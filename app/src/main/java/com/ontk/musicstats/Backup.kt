package com.ontk.musicstats

import android.content.Context
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.provider.DocumentsContract
import android.util.JsonReader
import android.util.JsonToken
import android.util.JsonWriter
import android.util.Log
import org.json.JSONObject
import java.io.File
import java.io.InputStream
import java.io.InputStreamReader
import java.io.OutputStream
import java.io.OutputStreamWriter
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream

data class ImportResult(
    val plays: Int,
    val skipped: Int,
    val genres: Int,
    val friends: Int,
    val profileId: String?,
    val profileName: String?
)

private fun JsonReader.stringOrNull(): String? =
    if (peek() == JsonToken.NULL) {
        nextNull()
        null
    } else nextString()

private class PlayIn {
    val artists = mutableListOf<String>()
    var title = ""
    var key = ""
    var album = ""
    var duration = 0L
    var pkg = ""
    var at = 0L
    var ms = 0L
}

/**
 * Резервная копия: один файл (сжатый JSON), который не зависит от подписи приложения
 * и переживает переустановку. В копию не попадает ключ Last.fm.
 * Восстановление объединяет данные с текущими и не создаёт дубликатов.
 */
object Backup {
    private const val TAG = "Slukh"
    private const val FORMAT = "slukh-backup"
    private const val VERSION = 1
    const val FILE_NAME = "slukh-backup.slukh"
    private const val AUTO_INTERVAL_MS = 24L * 60 * 60 * 1000

    private val executor = Executors.newSingleThreadExecutor()
    private val autoRunning = AtomicBoolean(false)
    private val main = Handler(Looper.getMainLooper())

    // ---------- экспорт ----------

    private fun export(context: Context, out: OutputStream) {
        val db = StatsDb.get(context)
        val rdb = db.readableDatabase

        val artistsByPlay = HashMap<Long, MutableList<String>>()
        rdb.rawQuery("SELECT play_id, artist FROM play_artists ORDER BY rowid", null).use { c ->
            while (c.moveToNext()) {
                artistsByPlay.getOrPut(c.getLong(0)) { mutableListOf() }.add(c.getString(1))
            }
        }

        val w = JsonWriter(OutputStreamWriter(GZIPOutputStream(out), Charsets.UTF_8))
        w.beginObject()
        w.name("format").value(FORMAT)
        w.name("v").value(VERSION.toLong())
        w.name("exportedAt").value(System.currentTimeMillis())

        w.name("profile").beginObject()
        w.name("id").value(AppSettings.profileId)
        w.name("name").value(AppSettings.profileName)
        w.endObject()

        w.name("plays").beginArray()
        rdb.rawQuery(
            "SELECT id, title, track_key, album, duration_sec, package, played_at, played_ms " +
                    "FROM plays ORDER BY played_at", null
        ).use { c ->
            while (c.moveToNext()) {
                w.beginObject()
                w.name("a").beginArray()
                artistsByPlay[c.getLong(0)]?.forEach { w.value(it) }
                w.endArray()
                w.name("t").value(c.getString(1))
                w.name("k").value(c.getString(2))
                w.name("al").value(c.getString(3))
                w.name("d").value(c.getLong(4))
                w.name("p").value(c.getString(5))
                w.name("at").value(c.getLong(6))
                w.name("ms").value(c.getLong(7))
                w.endObject()
            }
        }
        w.endArray()

        w.name("genres").beginArray()
        rdb.rawQuery(
            "SELECT artist_key, artist, genre, subgenres, source FROM artist_genres", null
        ).use { c ->
            while (c.moveToNext()) {
                w.beginObject()
                w.name("k").value(c.getString(0))
                w.name("a").value(c.getString(1))
                w.name("g").value(c.getString(2))
                w.name("s").value(c.getString(3))
                w.name("src").value(c.getString(4))
                w.endObject()
            }
        }
        w.endArray()

        w.name("apps").beginArray()
        rdb.rawQuery("SELECT package, enabled FROM apps", null).use { c ->
            while (c.moveToNext()) {
                w.beginObject()
                w.name("p").value(c.getString(0))
                w.name("e").value(c.getLong(1))
                w.endObject()
            }
        }
        w.endArray()

        w.name("friends").beginArray()
        rdb.rawQuery("SELECT data FROM friends", null).use { c ->
            while (c.moveToNext()) w.value(c.getString(0))
        }
        w.endArray()

        w.endObject()
        w.close()
    }

    /** Сначала пишем во временный файл: если что-то пойдёт не так, старая копия не пострадает. */
    private fun exportToTemp(context: Context): File {
        val f = File.createTempFile("backup", ".slukh", context.cacheDir)
        f.outputStream().use { export(context, it) }
        return f
    }

    private fun copyFileTo(file: File, out: OutputStream) {
        out.use { o -> file.inputStream().use { it.copyTo(o) } }
    }

    /** Сохраняет копию в выбранный пользователем файл. */
    fun exportToUri(context: Context, uri: Uri, onDone: (Boolean) -> Unit) {
        val app = context.applicationContext
        executor.execute {
            val ok = try {
                val tmp = exportToTemp(app)
                try {
                    val out = app.contentResolver.openOutputStream(uri, "wt")
                        ?: error("не удалось открыть файл")
                    copyFileTo(tmp, out)
                } finally {
                    tmp.delete()
                }
                true
            } catch (e: Exception) {
                Log.d(TAG, "Ошибка сохранения копии: $e")
                false
            }
            main.post { onDone(ok) }
        }
    }

    // ---------- автокопия в выбранную папку ----------

    private fun openAutoStream(context: Context, tree: Uri): OutputStream? {
        val resolver = context.contentResolver
        val parentId = DocumentsContract.getTreeDocumentId(tree)
        val parent = DocumentsContract.buildDocumentUriUsingTree(tree, parentId)
        val children = DocumentsContract.buildChildDocumentsUriUsingTree(tree, parentId)
        var existing: Uri? = null
        resolver.query(
            children,
            arrayOf(
                DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                DocumentsContract.Document.COLUMN_DISPLAY_NAME
            ),
            null, null, null
        )?.use { c ->
            while (c.moveToNext()) {
                if (c.getString(1).orEmpty().startsWith("slukh-backup")) {
                    existing = DocumentsContract.buildDocumentUriUsingTree(tree, c.getString(0))
                    break
                }
            }
        }
        val target = existing
            ?: DocumentsContract.createDocument(resolver, parent, "application/octet-stream", FILE_NAME)
            ?: return null
        return resolver.openOutputStream(target, "wt")
    }

    private fun writeAuto(context: Context, tree: Uri): Boolean {
        val tmp = exportToTemp(context)
        try {
            val out = openAutoStream(context, tree) ?: return false
            copyFileTo(tmp, out)
            return true
        } finally {
            tmp.delete()
        }
    }

    /** Сделать копию в папку автокопии прямо сейчас. */
    fun backupNow(context: Context, onDone: (Boolean) -> Unit) {
        val app = context.applicationContext
        val tree = AppSettings.autoBackupUri
        if (tree.isEmpty()) {
            onDone(false)
            return
        }
        executor.execute {
            val ok = try {
                writeAuto(app, Uri.parse(tree))
            } catch (e: Exception) {
                Log.d(TAG, "Ошибка автокопии: $e")
                false
            }
            main.post {
                if (ok) AppSettings.setLastBackupAt(app, System.currentTimeMillis())
                onDone(ok)
            }
        }
    }

    /** Раз в сутки обновляет копию в папке автокопии. Дешёвая проверка, можно вызывать часто. */
    fun autoBackupIfNeeded(context: Context) {
        val tree = AppSettings.autoBackupUri
        if (tree.isEmpty()) return
        if (System.currentTimeMillis() - AppSettings.lastBackupAt < AUTO_INTERVAL_MS) return
        if (!autoRunning.compareAndSet(false, true)) return
        val app = context.applicationContext
        executor.execute {
            try {
                if (writeAuto(app, Uri.parse(tree))) {
                    main.post { AppSettings.setLastBackupAt(app, System.currentTimeMillis()) }
                }
            } catch (e: Exception) {
                Log.d(TAG, "Ошибка автокопии: $e")
            } finally {
                autoRunning.set(false)
            }
        }
    }

    // ---------- импорт ----------

    private fun readPlay(r: JsonReader): PlayIn {
        val p = PlayIn()
        r.beginObject()
        while (r.hasNext()) {
            when (r.nextName()) {
                "a" -> {
                    r.beginArray()
                    while (r.hasNext()) {
                        val name = r.stringOrNull().orEmpty().trim().take(200)
                        if (name.isNotEmpty()) p.artists.add(name)
                    }
                    r.endArray()
                }
                "t" -> p.title = r.stringOrNull().orEmpty().trim().take(300)
                "k" -> p.key = r.stringOrNull().orEmpty().take(600)
                "al" -> p.album = r.stringOrNull().orEmpty().take(300)
                "d" -> p.duration = r.nextLong()
                "p" -> p.pkg = r.stringOrNull().orEmpty().take(200)
                "at" -> p.at = r.nextLong()
                "ms" -> p.ms = r.nextLong()
                else -> r.skipValue()
            }
        }
        r.endObject()
        return p
    }

    private fun importFrom(context: Context, input: InputStream): ImportResult {
        val db = StatsDb.get(context)
        val wdb = db.writableDatabase

        val existing = HashSet<String>()
        wdb.rawQuery("SELECT played_at, track_key FROM plays", null).use { c ->
            while (c.moveToNext()) existing.add("${c.getLong(0)}|${c.getString(1)}")
        }

        var plays = 0
        var skipped = 0
        var genres = 0
        var friends = 0
        var profileId: String? = null
        var profileName: String? = null
        var formatOk = false

        val reader = try {
            JsonReader(InputStreamReader(GZIPInputStream(input), Charsets.UTF_8))
        } catch (e: java.io.IOException) {
            throw IllegalArgumentException("Это не файл копии Slukh")
        }

        wdb.beginTransaction()
        try {
            reader.use { r ->
                r.beginObject()
                while (r.hasNext()) {
                    when (r.nextName()) {
                        "format" -> formatOk = r.nextString() == FORMAT
                        "v" -> require(r.nextInt() <= VERSION) { "Файл создан более новой версией Slukh" }
                        "profile" -> {
                            r.beginObject()
                            while (r.hasNext()) {
                                when (r.nextName()) {
                                    "id" -> profileId = r.stringOrNull()?.take(32)
                                    "name" -> profileName = r.stringOrNull()?.take(24)
                                    else -> r.skipValue()
                                }
                            }
                            r.endObject()
                        }
                        "plays" -> {
                            require(formatOk) { "Это не файл копии Slukh" }
                            r.beginArray()
                            while (r.hasNext()) {
                                val p = readPlay(r)
                                if (p.artists.isEmpty() || p.title.isEmpty() || p.at <= 0L) continue
                                val key = p.key.ifEmpty {
                                    p.artists.map { TrackParser.key(it) }.sorted().joinToString(",") +
                                            "|" + TrackParser.key(p.title)
                                }
                                if (!existing.add("${p.at}|$key")) {
                                    skipped++
                                    continue
                                }
                                db.insertPlay(
                                    p.artists, p.title, p.album, p.duration, p.pkg, p.at, p.ms
                                )
                                plays++
                            }
                            r.endArray()
                        }
                        "genres" -> {
                            require(formatOk) { "Это не файл копии Slukh" }
                            r.beginArray()
                            while (r.hasNext()) {
                                var key = ""
                                var artist = ""
                                var genre: String? = null
                                var subs: String? = null
                                var source = "none"
                                r.beginObject()
                                while (r.hasNext()) {
                                    when (r.nextName()) {
                                        "k" -> key = r.stringOrNull().orEmpty()
                                        "a" -> artist = r.stringOrNull().orEmpty()
                                        "g" -> genre = r.stringOrNull()
                                        "s" -> subs = r.stringOrNull()
                                        "src" -> source = r.stringOrNull().orEmpty()
                                        else -> r.skipValue()
                                    }
                                }
                                r.endObject()
                                if (key.isEmpty()) continue
                                val current = db.genreSource(key)
                                // жанр, заданный вручную, важнее автоматического
                                if (current == null || (source == "manual" && current != "manual")) {
                                    db.saveGenre(key, artist, genre, subs, source)
                                    genres++
                                }
                            }
                            r.endArray()
                        }
                        "apps" -> {
                            r.beginArray()
                            while (r.hasNext()) {
                                var pkg = ""
                                var enabled = true
                                r.beginObject()
                                while (r.hasNext()) {
                                    when (r.nextName()) {
                                        "p" -> pkg = r.stringOrNull().orEmpty()
                                        "e" -> enabled = r.nextLong() == 1L
                                        else -> r.skipValue()
                                    }
                                }
                                r.endObject()
                                if (pkg.isNotEmpty()) db.registerApp(pkg, enabled)
                            }
                            r.endArray()
                        }
                        "friends" -> {
                            val known = db.friends().associateBy { it.id }
                            r.beginArray()
                            while (r.hasNext()) {
                                val raw = r.stringOrNull()
                                val snap = raw?.let {
                                    runCatching { Snapshot.fromJson(JSONObject(it)) }.getOrNull()
                                }
                                if (snap != null) {
                                    val old = known[snap.id]
                                    if (old == null || snap.createdAt > old.createdAt) {
                                        db.saveFriend(snap)
                                        friends++
                                    }
                                }
                            }
                            r.endArray()
                        }
                        else -> r.skipValue()
                    }
                }
                r.endObject()
            }
            require(formatOk) { "Это не файл копии Slukh" }
            wdb.setTransactionSuccessful()
        } finally {
            wdb.endTransaction()
        }
        return ImportResult(plays, skipped, genres, friends, profileId, profileName)
    }

    /** Восстанавливает данные из файла копии. Результат придёт в главном потоке. */
    fun restoreFromUri(context: Context, uri: Uri, onDone: (Result<ImportResult>) -> Unit) {
        val app = context.applicationContext
        executor.execute {
            val result = runCatching {
                val input = app.contentResolver.openInputStream(uri)
                    ?: throw IllegalArgumentException("Не удалось открыть файл")
                input.use { importFrom(app, it) }
            }
            main.post {
                result.getOrNull()?.let { r ->
                    val id = r.profileId
                    if (!id.isNullOrBlank() && id != AppSettings.profileId) {
                        AppSettings.setProfileId(app, id)
                    }
                    val name = r.profileName
                    if (AppSettings.profileName.isBlank() && !name.isNullOrBlank()) {
                        AppSettings.setProfileName(app, name)
                    }
                    NowPlaying.version++
                    GenreResolver.requestMissing(app)
                }
                onDone(result)
            }
        }
    }
}