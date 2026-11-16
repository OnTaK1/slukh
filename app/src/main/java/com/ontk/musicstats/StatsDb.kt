package com.ontk.musicstats

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

data class PlayRow(
    val artists: String,
    val title: String,
    val packageName: String,
    val playedAt: Long
)

data class AppRow(val packageName: String, val enabled: Boolean)

data class ArtistStat(
    val name: String,
    val count: Int,
    val genre: String?,
    val subgenres: String?
)

class StatsDb private constructor(context: Context) :
    SQLiteOpenHelper(context, "stats.db", null, 6) {

    override fun onCreate(db: SQLiteDatabase) {
        createPlayTables(db)
        db.execSQL("CREATE TABLE apps (package TEXT PRIMARY KEY, enabled INTEGER NOT NULL)")
        createGenreTable(db)
        createFriendsTable(db)
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        if (oldVersion < 2) {
            db.execSQL("DROP TABLE IF EXISTS play_artists")
            db.execSQL("DROP TABLE IF EXISTS plays")
            createPlayTables(db) // уже со столбцом played_ms
        } else if (oldVersion < 5) {
            db.execSQL("ALTER TABLE plays ADD COLUMN played_ms INTEGER NOT NULL DEFAULT 0")
            db.execSQL("UPDATE plays SET played_ms = duration_sec * 1000")
        }
        if (oldVersion < 3) {
            createGenreTable(db) // уже со столбцом subgenres
        } else if (oldVersion < 4) {
            db.execSQL("ALTER TABLE artist_genres ADD COLUMN subgenres TEXT")
            db.execSQL("DELETE FROM artist_genres WHERE source <> 'manual'")
        }
        if (oldVersion < 6) {
            createFriendsTable(db)
        }
    }

    private fun createPlayTables(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE plays (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                artists TEXT NOT NULL,
                title TEXT NOT NULL,
                track_key TEXT NOT NULL,
                album TEXT,
                duration_sec INTEGER NOT NULL,
                package TEXT NOT NULL,
                played_at INTEGER NOT NULL,
                played_ms INTEGER NOT NULL DEFAULT 0
            )
            """.trimIndent()
        )
        db.execSQL("CREATE INDEX idx_plays_time ON plays(played_at)")
        db.execSQL("CREATE INDEX idx_plays_track ON plays(track_key)")
        db.execSQL(
            "CREATE TABLE play_artists (" +
                    "play_id INTEGER NOT NULL, artist TEXT NOT NULL, artist_key TEXT NOT NULL)"
        )
        db.execSQL("CREATE INDEX idx_pa_play ON play_artists(play_id)")
        db.execSQL("CREATE INDEX idx_pa_key ON play_artists(artist_key)")
    }

    private fun createFriendsTable(db: SQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS friends (" +
                    "id TEXT PRIMARY KEY, name TEXT NOT NULL, data TEXT NOT NULL, " +
                    "updated_at INTEGER NOT NULL)"
        )
    }

    private fun createGenreTable(db: SQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE artist_genres (" +
                    "artist_key TEXT PRIMARY KEY, artist TEXT NOT NULL, genre TEXT, " +
                    "subgenres TEXT, source TEXT NOT NULL, updated_at INTEGER NOT NULL)"
        )
    }

    /** Возвращает id новой записи (нужен, чтобы позже уточнить проигранное время). */
    fun insertPlay(
        artists: List<String>,
        title: String,
        album: String,
        durationSec: Long,
        packageName: String,
        playedAt: Long,
        playedMs: Long
    ): Long {
        val trackKey = artists.map { TrackParser.key(it) }.sorted().joinToString(",") +
                "|" + TrackParser.key(title)
        val db = writableDatabase
        var playId = -1L
        db.beginTransaction()
        try {
            val values = ContentValues().apply {
                put("artists", artists.joinToString(", "))
                put("title", title)
                put("track_key", trackKey)
                put("album", album)
                put("duration_sec", durationSec)
                put("package", packageName)
                put("played_at", playedAt)
                put("played_ms", playedMs)
            }
            playId = db.insert("plays", null, values)
            artists.forEach { artist ->
                val av = ContentValues().apply {
                    put("play_id", playId)
                    put("artist", artist)
                    put("artist_key", TrackParser.key(artist))
                }
                db.insert("play_artists", null, av)
            }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
        return playId
    }

    fun updatePlayedMs(playId: Long, playedMs: Long) {
        val values = ContentValues().apply { put("played_ms", playedMs) }
        writableDatabase.update("plays", values, "id = ?", arrayOf(playId.toString()))
    }

    fun clearPlays() {
        val db = writableDatabase
        db.beginTransaction()
        try {
            db.execSQL("DELETE FROM play_artists")
            db.execSQL("DELETE FROM plays")
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    /** Возвращает true, если приложение видим впервые. */
    fun registerApp(packageName: String, enabledByDefault: Boolean): Boolean {
        val values = ContentValues().apply {
            put("package", packageName)
            put("enabled", if (enabledByDefault) 1 else 0)
        }
        val id = writableDatabase.insertWithOnConflict(
            "apps", null, values, SQLiteDatabase.CONFLICT_IGNORE
        )
        return id != -1L
    }

    fun isAppEnabled(packageName: String): Boolean =
        readableDatabase.rawQuery(
            "SELECT enabled FROM apps WHERE package = ?", arrayOf(packageName)
        ).use { it.moveToFirst() && it.getInt(0) == 1 }

    fun setAppEnabled(packageName: String, enabled: Boolean) {
        val values = ContentValues().apply { put("enabled", if (enabled) 1 else 0) }
        writableDatabase.update("apps", values, "package = ?", arrayOf(packageName))
    }

    fun getApps(): List<AppRow> =
        readableDatabase.rawQuery(
            "SELECT package, enabled FROM apps ORDER BY package", null
        ).use { c ->
            val list = mutableListOf<AppRow>()
            while (c.moveToNext()) list.add(AppRow(c.getString(0), c.getInt(1) == 1))
            list
        }

    fun recentPlays(limit: Int): List<PlayRow> =
        readableDatabase.rawQuery(
            "SELECT artists, title, package, played_at FROM plays " +
                    "ORDER BY played_at DESC LIMIT $limit", null
        ).use { c ->
            val list = mutableListOf<PlayRow>()
            while (c.moveToNext()) {
                list.add(PlayRow(c.getString(0), c.getString(1), c.getString(2), c.getLong(3)))
            }
            list
        }

    // ---- итоги за период ----

    fun totals(sinceMs: Long, untilMs: Long = Long.MAX_VALUE): PeriodTotals {
        var plays = 0
        var totalMs = 0L
        var tracks = 0
        readableDatabase.rawQuery(
            "SELECT COUNT(*), COALESCE(SUM(played_ms), 0), COUNT(DISTINCT track_key) " +
                    "FROM plays WHERE played_at >= $sinceMs AND played_at < $untilMs", null
        ).use { c ->
            if (c.moveToFirst()) {
                plays = c.getInt(0)
                totalMs = c.getLong(1)
                tracks = c.getInt(2)
            }
        }
        var artists = 0
        readableDatabase.rawQuery(
            "SELECT COUNT(DISTINCT pa.artist_key) FROM play_artists pa " +
                    "JOIN plays p ON p.id = pa.play_id WHERE p.played_at >= $sinceMs AND p.played_at < $untilMs", null
        ).use { c ->
            if (c.moveToFirst()) artists = c.getInt(0)
        }
        return PeriodTotals(plays, totalMs, tracks, artists)
    }

    fun playTimes(sinceMs: Long, untilMs: Long = Long.MAX_VALUE): List<Long> =
        readableDatabase.rawQuery(
            "SELECT played_at FROM plays WHERE played_at >= $sinceMs AND played_at < $untilMs", null
        ).use { c ->
            val list = ArrayList<Long>()
            while (c.moveToNext()) list.add(c.getLong(0))
            list
        }

    /** Каждый исполнитель трека получает по одному прослушиванию. */
    fun topArtists(sinceMs: Long, limit: Int, untilMs: Long = Long.MAX_VALUE): List<ArtistStat> =
        readableDatabase.rawQuery(
            "SELECT MIN(pa.artist), COUNT(*) AS c, MAX(g.genre), MAX(g.subgenres) " +
                    "FROM play_artists pa JOIN plays p ON p.id = pa.play_id " +
                    "LEFT JOIN artist_genres g ON g.artist_key = pa.artist_key " +
                    "WHERE p.played_at >= $sinceMs AND p.played_at < $untilMs " +
                    "GROUP BY pa.artist_key ORDER BY c DESC LIMIT $limit", null
        ).use { c ->
            val list = mutableListOf<ArtistStat>()
            while (c.moveToNext()) {
                list.add(ArtistStat(c.getString(0), c.getInt(1), c.getString(2), c.getString(3)))
            }
            list
        }

    fun topTracks(sinceMs: Long, limit: Int, untilMs: Long = Long.MAX_VALUE): List<Triple<String, String, Int>> =
        readableDatabase.rawQuery(
            "SELECT MIN(artists), MIN(title), COUNT(*) AS c FROM plays " +
                    "WHERE played_at >= $sinceMs AND played_at < $untilMs " +
                    "GROUP BY track_key ORDER BY c DESC LIMIT $limit", null
        ).use { c ->
            val list = mutableListOf<Triple<String, String, Int>>()
            while (c.moveToNext()) list.add(Triple(c.getString(0), c.getString(1), c.getInt(2)))
            list
        }

    /** Каждое прослушивание считается один раз в каждый из жанров его исполнителей. */
    fun topGenres(sinceMs: Long, limit: Int, untilMs: Long = Long.MAX_VALUE): List<Pair<String, Int>> =
        readableDatabase.rawQuery(
            "SELECT genre, COUNT(DISTINCT play_id) AS c FROM (" +
                    "SELECT g.genre AS genre, p.id AS play_id FROM plays p " +
                    "JOIN play_artists pa ON pa.play_id = p.id " +
                    "JOIN artist_genres g ON g.artist_key = pa.artist_key " +
                    "WHERE p.played_at >= $sinceMs AND p.played_at < $untilMs AND g.genre IS NOT NULL " +
                    "UNION ALL " +
                    "SELECT 'Без жанра', p.id FROM plays p " +
                    "WHERE p.played_at >= $sinceMs AND p.played_at < $untilMs AND NOT EXISTS (" +
                    "SELECT 1 FROM play_artists pa " +
                    "JOIN artist_genres g ON g.artist_key = pa.artist_key " +
                    "WHERE pa.play_id = p.id AND g.genre IS NOT NULL)" +
                    ") GROUP BY genre ORDER BY c DESC LIMIT $limit", null
        ).use { c ->
            val list = mutableListOf<Pair<String, Int>>()
            while (c.moveToNext()) list.add(c.getString(0) to c.getInt(1))
            list
        }

    fun topSubgenres(sinceMs: Long, limit: Int, untilMs: Long = Long.MAX_VALUE): List<Pair<String, Int>> {
        val counts = HashMap<String, Int>()
        val seen = HashSet<String>()
        readableDatabase.rawQuery(
            "SELECT DISTINCT p.id, g.subgenres FROM plays p " +
                    "JOIN play_artists pa ON pa.play_id = p.id " +
                    "JOIN artist_genres g ON g.artist_key = pa.artist_key " +
                    "WHERE p.played_at >= $sinceMs AND p.played_at < $untilMs " +
                    "AND g.subgenres IS NOT NULL AND g.subgenres <> ''",
            null
        ).use { c ->
            while (c.moveToNext()) {
                val playId = c.getLong(0)
                c.getString(1).split(",")
                    .map { it.trim() }
                    .filter { it.isNotEmpty() }
                    .forEach { sub ->
                        if (seen.add("$playId|$sub")) counts[sub] = (counts[sub] ?: 0) + 1
                    }
            }
        }
        return counts.entries
            .sortedByDescending { it.value }
            .take(limit)
            .map { it.key to it.value }
    }

    // ---- жанры исполнителей ----

    /** Исполнители, для которых ещё не искали жанр (или «не найден» устарело). */
    fun artistsNeedingGenre(retryBeforeMs: Long, limit: Int): List<Pair<String, String>> =
        readableDatabase.rawQuery(
            "SELECT pa.artist_key, MIN(pa.artist) FROM play_artists pa " +
                    "LEFT JOIN artist_genres g ON g.artist_key = pa.artist_key " +
                    "WHERE pa.artist_key <> '' AND pa.artist_key <> '$UNKNOWN_KEY' AND " +
                    "(g.artist_key IS NULL OR (g.source = 'none' AND g.updated_at < $retryBeforeMs)) " +
                    "GROUP BY pa.artist_key LIMIT $limit", null
        ).use { c ->
            val list = mutableListOf<Pair<String, String>>()
            while (c.moveToNext()) list.add(c.getString(0) to c.getString(1))
            list
        }

    fun genreSource(artistKey: String): String? =
        readableDatabase.rawQuery(
            "SELECT source FROM artist_genres WHERE artist_key = ?", arrayOf(artistKey)
        ).use { if (it.moveToFirst()) it.getString(0) else null }

    /** Автоматический результат не перезаписывает жанр, заданный вручную. */
    fun saveGenre(
        artistKey: String,
        artist: String,
        genre: String?,
        subgenres: String?,
        source: String
    ) {
        if (source != "manual" && genreSource(artistKey) == "manual") return
        val values = ContentValues().apply {
            put("artist_key", artistKey)
            put("artist", artist)
            put("genre", genre)
            put("subgenres", subgenres)
            put("source", source)
            put("updated_at", System.currentTimeMillis())
        }
        writableDatabase.insertWithOnConflict(
            "artist_genres", null, values, SQLiteDatabase.CONFLICT_REPLACE
        )
    }

    fun setManualGenre(artistKey: String, artist: String, genre: String, subgenres: String? = null) {
        saveGenre(artistKey, artist, genre.trim(), subgenres?.trim()?.ifEmpty { null }, "manual")
    }

    /** Удаляет запись, чтобы жанр определился заново автоматически. */
    fun clearGenre(artistKey: String) {
        writableDatabase.delete("artist_genres", "artist_key = ?", arrayOf(artistKey))
    }

    companion object {
        private val UNKNOWN_KEY = TrackParser.key("Неизвестный исполнитель")

        @Volatile
        private var instance: StatsDb? = null

        fun get(context: Context): StatsDb =
            instance ?: synchronized(this) {
                instance ?: StatsDb(context.applicationContext).also { instance = it }
            }
    }
}