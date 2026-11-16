package com.ontk.musicstats

import android.content.ContentValues
import android.database.sqlite.SQLiteDatabase
import android.util.Base64
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream
import kotlin.math.roundToInt

/**
 * Снимок музыкального профиля за период. Это всё, что уходит к другу: итоги,
 * без времени отдельных прослушиваний.
 */
data class Snapshot(
    val id: String,
    val name: String,
    val label: String,
    val from: Long,
    val until: Long,
    val createdAt: Long,
    val totalMs: Long,
    val plays: Int,
    val tracks: Int,
    val artists: Int,
    val topArtists: List<Pair<String, Int>>,
    val topGenres: List<Pair<String, Int>>,
    val topTracks: List<Triple<String, String, Int>>
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("v", 1)
        put("id", id)
        put("n", name)
        put("l", label)
        put("f", from)
        put("u", until)
        put("c", createdAt)
        put("tm", totalMs)
        put("pl", plays)
        put("tr", tracks)
        put("ar", artists)
        put("a", JSONArray().apply {
            topArtists.forEach { put(JSONArray().put(it.first).put(it.second)) }
        })
        put("g", JSONArray().apply {
            topGenres.forEach { put(JSONArray().put(it.first).put(it.second)) }
        })
        put("t", JSONArray().apply {
            topTracks.forEach { put(JSONArray().put(it.first).put(it.second).put(it.third)) }
        })
    }

    companion object {
        private fun pairs(arr: JSONArray?, max: Int): List<Pair<String, Int>> {
            if (arr == null) return emptyList()
            val out = mutableListOf<Pair<String, Int>>()
            for (i in 0 until minOf(arr.length(), max)) {
                val e = arr.optJSONArray(i) ?: continue
                val name = e.optString(0).trim().take(80)
                if (name.isEmpty()) continue
                out.add(name to e.optInt(1).coerceAtLeast(0))
            }
            return out
        }

        private fun triples(arr: JSONArray?, max: Int): List<Triple<String, String, Int>> {
            if (arr == null) return emptyList()
            val out = mutableListOf<Triple<String, String, Int>>()
            for (i in 0 until minOf(arr.length(), max)) {
                val e = arr.optJSONArray(i) ?: continue
                val artists = e.optString(0).trim().take(120)
                val title = e.optString(1).trim().take(120)
                if (artists.isEmpty() || title.isEmpty()) continue
                out.add(Triple(artists, title, e.optInt(2).coerceAtLeast(0)))
            }
            return out
        }

        /** Данные пришли от другого человека: проверяем и обрезаем всё, что можно. */
        fun fromJson(o: JSONObject): Snapshot? {
            if (o.optInt("v") != 1) return null
            val id = o.optString("id").trim().take(32)
            val from = o.optLong("f")
            val until = o.optLong("u")
            if (id.isEmpty() || until <= from) return null
            return Snapshot(
                id = id,
                name = o.optString("n").trim().take(24).ifEmpty { "Друг" },
                label = o.optString("l").trim().take(40),
                from = from,
                until = until,
                createdAt = o.optLong("c"),
                totalMs = o.optLong("tm").coerceAtLeast(0),
                plays = o.optInt("pl").coerceAtLeast(0),
                tracks = o.optInt("tr").coerceAtLeast(0),
                artists = o.optInt("ar").coerceAtLeast(0),
                topArtists = pairs(o.optJSONArray("a"), 30),
                topGenres = pairs(o.optJSONArray("g"), 15),
                topTracks = triples(o.optJSONArray("t"), 15)
            )
        }
    }
}

object Profile {
    fun build(db: StatsDb, period: Period, name: String, id: String, withTracks: Boolean): Snapshot {
        val now = System.currentTimeMillis()
        return buildRange(
            db, period.since(now), minOf(period.until(now), now),
            name, id, period.inPhrase, withTracks
        )
    }

    fun buildRange(
        db: StatsDb,
        from: Long,
        until: Long,
        name: String,
        id: String,
        label: String,
        withTracks: Boolean,
        topN: Int = 20
    ): Snapshot {
        val totals = db.totals(from, until)
        return Snapshot(
            id = id,
            name = name.trim().take(24),
            label = label,
            from = from,
            until = until,
            createdAt = System.currentTimeMillis(),
            totalMs = totals.totalMs,
            plays = totals.plays,
            tracks = totals.uniqueTracks,
            artists = totals.uniqueArtists,
            topArtists = db.topArtists(from, topN, until).map { it.name to it.count },
            topGenres = db.topGenres(from, 9, until).filter { it.first != "Без жанра" }.take(8),
            topTracks = if (withTracks) {
                db.topTracks(from, 10, until).map { Triple(it.first, it.second, it.third) }
            } else emptyList()
        )
    }
}

/** Код для пересылки: «SLUKH1:» и сжатый снимок. Его можно вставить в любое сообщение. */
object FriendCodec {
    private const val PREFIX = "SLUKH1:"
    private val CODE = Regex("SLUKH1:([A-Za-z0-9_-]{20,20000})")
    private const val FLAGS = Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING

    fun encode(s: Snapshot): String {
        val bos = ByteArrayOutputStream()
        GZIPOutputStream(bos).use { it.write(s.toJson().toString().toByteArray(Charsets.UTF_8)) }
        return PREFIX + Base64.encodeToString(bos.toByteArray(), FLAGS)
    }

    /** Текст сообщения, которое уйдёт другу. */
    fun message(s: Snapshot): String {
        val top = s.topArtists.take(3).joinToString(", ") { it.first }
        return buildString {
            append("Мой музыкальный профиль в Slukh · ").append(s.label).append('\n')
            if (top.isNotEmpty()) append("Топ: ").append(top).append('\n')
            append("Чтобы сравнить вкусы: Slukh → Друзья → вставить код.\n\n")
            append(encode(s))
        }
    }

    private fun gunzip(raw: ByteArray, limit: Int = 256 * 1024): String {
        GZIPInputStream(ByteArrayInputStream(raw)).use { zin ->
            val out = ByteArrayOutputStream()
            val buf = ByteArray(8192)
            while (true) {
                val n = zin.read(buf)
                if (n < 0) break
                out.write(buf, 0, n)
                require(out.size() <= limit) { "слишком большой код" }
            }
            return out.toString("UTF-8")
        }
    }

    fun decode(text: String?): Snapshot? {
        val m = CODE.find(text ?: return null) ?: return null
        return try {
            val raw = Base64.decode(m.groupValues[1], FLAGS)
            Snapshot.fromJson(JSONObject(gunzip(raw)))
        } catch (e: Exception) {
            null
        }
    }
}

// ---- хранение друзей ----

fun StatsDb.saveFriend(s: Snapshot) {
    val values = ContentValues().apply {
        put("id", s.id)
        put("name", s.name)
        put("data", s.toJson().toString())
        put("updated_at", System.currentTimeMillis())
    }
    writableDatabase.insertWithOnConflict("friends", null, values, SQLiteDatabase.CONFLICT_REPLACE)
}

fun StatsDb.friends(): List<Snapshot> =
    readableDatabase.rawQuery("SELECT data FROM friends ORDER BY updated_at DESC", null).use { c ->
        val list = mutableListOf<Snapshot>()
        while (c.moveToNext()) {
            runCatching { Snapshot.fromJson(JSONObject(c.getString(0))) }.getOrNull()?.let { list.add(it) }
        }
        list
    }

fun StatsDb.deleteFriend(id: String) {
    writableDatabase.delete("friends", "id = ?", arrayOf(id))
}

/** Сколько раз за всё время звучал исполнитель (0: ты его никогда не слушал). */
fun StatsDb.artistTotalPlays(artistKey: String): Int =
    readableDatabase.rawQuery(
        "SELECT COUNT(*) FROM play_artists WHERE artist_key = ?", arrayOf(artistKey)
    ).use { if (it.moveToFirst()) it.getInt(0) else 0 }

/** Приложение, в котором слушают чаще всего. */
fun StatsDb.mostUsedPackage(): String =
    readableDatabase.rawQuery(
        "SELECT package FROM plays GROUP BY package ORDER BY COUNT(*) DESC LIMIT 1", null
    ).use { if (it.moveToFirst()) it.getString(0) else "" }

// ---- сравнение ----

data class Comparison(
    val mineEmpty: Boolean,
    val percent: Int,
    val artistPct: Int,
    val genrePct: Int,
    val mineMs: Long,
    /** исполнитель, сколько раз у тебя, сколько у друга */
    val common: List<Triple<String, Int, Int>>,
    val commonGenres: List<String>,
    /** исполнители из топа друга, которых ты ни разу не слушал */
    val fresh: List<String>
)

object Compare {
    /** Доля совпадения двух топов: сумма меньших долей по общим элементам. */
    private fun overlap(a: List<Pair<String, Int>>, b: List<Pair<String, Int>>): Int {
        val sa = a.sumOf { it.second }.toDouble()
        val sb = b.sumOf { it.second }.toDouble()
        if (sa == 0.0 || sb == 0.0) return 0
        val shareB = HashMap<String, Double>()
        b.forEach { (name, c) -> shareB[TrackParser.key(name)] = c / sb }
        var sum = 0.0
        for ((name, c) in a) {
            val other = shareB[TrackParser.key(name)] ?: continue
            sum += minOf(c / sa, other)
        }
        return (sum * 100).roundToInt()
    }

    fun word(percent: Int): String = when {
        percent >= 70 -> "музыкальные близнецы"
        percent >= 50 -> "очень похожий вкус"
        percent >= 30 -> "есть о чём поговорить"
        percent >= 15 -> "пересечения редки"
        else -> "разные вселенные"
    }

    /** Сравнивает по тому же окну времени, за которое друг собрал снимок. */
    fun run(db: StatsDb, friend: Snapshot): Comparison {
        val mine = Profile.buildRange(db, friend.from, friend.until, "", "", "", false)
        if (mine.plays == 0) {
            return Comparison(true, 0, 0, 0, 0, emptyList(), emptyList(), emptyList())
        }
        val myAll = HashMap<String, Int>()
        db.topArtists(friend.from, 300, friend.until).forEach { myAll[TrackParser.key(it.name)] = it.count }

        val artistPct = overlap(friend.topArtists, mine.topArtists)
        val genrePct = overlap(friend.topGenres, mine.topGenres)
        val percent = ((artistPct + genrePct) / 2.0).roundToInt()

        val common = friend.topArtists
            .mapNotNull { (name, c) ->
                val m = myAll[TrackParser.key(name)] ?: return@mapNotNull null
                Triple(name, m, c)
            }
            .sortedByDescending { minOf(it.second, it.third) }
            .take(8)

        val myGenres = mine.topGenres.map { TrackParser.key(it.first) }.toSet()
        val commonGenres = friend.topGenres.map { it.first }
            .filter { TrackParser.key(it) in myGenres }
            .take(6)

        val fresh = friend.topArtists.map { it.first }
            .filter { db.artistTotalPlays(TrackParser.key(it)) == 0 }
            .take(6)

        return Comparison(false, percent, artistPct, genrePct, mine.totalMs, common, commonGenres, fresh)
    }
}