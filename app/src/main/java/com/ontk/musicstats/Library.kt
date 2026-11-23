package com.ontk.musicstats

import android.Manifest
import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.content.pm.PackageManager
import android.database.sqlite.SQLiteDatabase
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.DocumentsContract
import android.provider.MediaStore
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import java.util.concurrent.Executors

// ---------- модели ----------

data class LibraryTrack(
    val uri: String,
    val title: String,
    val artists: String,
    val artistKeys: String,
    val album: String,
    val durationMs: Long,
    val trackKey: String,
    /** Сколько раз этот трек засчитан в статистике (по любым плеерам). */
    val plays: Int = 0
)

data class Favorite(
    val trackKey: String,
    val artists: String,
    val title: String,
    val packageName: String
)

data class SuggestionCandidate(
    val trackKey: String,
    val artists: String,
    val title: String,
    val plays: Int,
    val packageName: String
)

/** Файл, найденный сканером, до разбора названия. */
data class ScannedFile(
    val uri: String,
    val title: String,
    val artist: String,
    val album: String,
    val durationMs: Long
)

enum class LibrarySort { ALL, FREQUENT, RECENT }

// ---------- таблицы ----------

/** Вызывается из StatsDb при создании базы и при обновлении до версии 7. */
fun createLibraryTables(db: SQLiteDatabase) {
    db.execSQL(
        "CREATE TABLE IF NOT EXISTS library_tracks (" +
                "uri TEXT PRIMARY KEY, title TEXT NOT NULL, artists TEXT NOT NULL, " +
                "artist_keys TEXT NOT NULL, album TEXT, duration_ms INTEGER NOT NULL, " +
                "source TEXT NOT NULL, track_key TEXT NOT NULL, title_key TEXT NOT NULL, " +
                "search TEXT NOT NULL, added_at INTEGER NOT NULL)"
    )
    db.execSQL("CREATE INDEX IF NOT EXISTS idx_lib_key ON library_tracks(track_key)")
    db.execSQL("CREATE INDEX IF NOT EXISTS idx_lib_title ON library_tracks(title_key)")
    db.execSQL(
        "CREATE TABLE IF NOT EXISTS favorites (" +
                "track_key TEXT PRIMARY KEY, artists TEXT NOT NULL, title TEXT NOT NULL, " +
                "package TEXT NOT NULL, added_at INTEGER NOT NULL)"
    )
    db.execSQL(
        "CREATE TABLE IF NOT EXISTS dismissed_suggestions (" +
                "track_key TEXT PRIMARY KEY, dismissed_at INTEGER NOT NULL)"
    )
}

// ---------- запросы к библиотеке ----------

fun StatsDb.libraryCount(): Int =
    readableDatabase.rawQuery("SELECT COUNT(DISTINCT track_key) FROM library_tracks", null)
        .use { if (it.moveToFirst()) it.getInt(0) else 0 }

/**
 * Треки библиотеки. Поиск идёт по заранее приведённому к нижнему регистру полю:
 * стандартный LIKE в SQLite не различает регистр только у латиницы, а не у кириллицы.
 * Один и тот же трек из разных источников показывается один раз.
 */
fun StatsDb.libraryTracks(query: String, sort: LibrarySort, limit: Int = 1000): List<LibraryTrack> {
    val args = ArrayList<String>()
    var where = ""
    if (query.isNotBlank()) {
        where = "WHERE l.search LIKE ?"
        args.add("%${query.trim().lowercase()}%")
    }
    val outerWhere = if (sort == LibrarySort.FREQUENT) "WHERE plays > 0" else ""
    val order = when (sort) {
        LibrarySort.ALL -> "artists COLLATE NOCASE, title COLLATE NOCASE"
        LibrarySort.FREQUENT -> "plays DESC, artists COLLATE NOCASE"
        LibrarySort.RECENT -> "added_at DESC"
    }
    val sql = "SELECT uri, title, artists, artist_keys, album, duration_ms, track_key, plays FROM (" +
            "SELECT MIN(l.uri) AS uri, l.title AS title, l.artists AS artists, " +
            "l.artist_keys AS artist_keys, l.album AS album, l.duration_ms AS duration_ms, " +
            "l.track_key AS track_key, MAX(l.added_at) AS added_at, " +
            "(SELECT COUNT(*) FROM plays p WHERE p.track_key = l.track_key) AS plays " +
            "FROM library_tracks l $where GROUP BY l.track_key) $outerWhere " +
            "ORDER BY $order LIMIT $limit"
    return readableDatabase.rawQuery(sql, args.toTypedArray()).use { c ->
        val list = mutableListOf<LibraryTrack>()
        while (c.moveToNext()) {
            list.add(
                LibraryTrack(
                    c.getString(0), c.getString(1), c.getString(2), c.getString(3),
                    c.getString(4).orEmpty(), c.getLong(5), c.getString(6), c.getInt(7)
                )
            )
        }
        list
    }
}

/**
 * Ищет в библиотеке файл, соответствующий треку из статистики: совпадает название
 * и хотя бы один исполнитель.
 */
fun StatsDb.findLocal(artists: String, title: String): LibraryTrack? {
    val wanted = TrackParser.splitArtists(artists).map { TrackParser.key(it) }.toSet()
    val titleKey = TrackParser.key(title)
    if (titleKey.isEmpty()) return null
    return readableDatabase.rawQuery(
        "SELECT uri, title, artists, artist_keys, album, duration_ms, track_key " +
                "FROM library_tracks WHERE title_key = ? LIMIT 20", arrayOf(titleKey)
    ).use { c ->
        var found: LibraryTrack? = null
        while (found == null && c.moveToNext()) {
            val keys = c.getString(3).split(',')
            if (keys.any { it in wanted }) {
                found = LibraryTrack(
                    c.getString(0), c.getString(1), c.getString(2), c.getString(3),
                    c.getString(4).orEmpty(), c.getLong(5), c.getString(6)
                )
            }
        }
        found
    }
}

/** Заменяет все записи источника свежим списком, сохраняя дату добавления. */
fun StatsDb.replaceLibrarySource(source: String, files: List<ScannedFile>): Int {
    val db = writableDatabase
    val known = HashMap<String, Long>()
    db.rawQuery("SELECT uri, added_at FROM library_tracks WHERE source = ?", arrayOf(source)).use { c ->
        while (c.moveToNext()) known[c.getString(0)] = c.getLong(1)
    }
    var inserted = 0
    val now = System.currentTimeMillis()
    db.beginTransaction()
    try {
        db.delete("library_tracks", "source = ?", arrayOf(source))
        for (f in files) {
            if (f.durationMs in 1 until 30_000) continue // слишком короткие записи не музыка
            val parsed = TrackParser.parse(f.artist, f.title, "") ?: continue
            val keys = parsed.artists.map { TrackParser.key(it) }.sorted()
            val titleKey = TrackParser.key(parsed.title)
            val values = ContentValues().apply {
                put("uri", f.uri)
                put("title", parsed.title)
                put("artists", parsed.artists.joinToString(", "))
                put("artist_keys", keys.joinToString(","))
                put("album", f.album)
                put("duration_ms", f.durationMs)
                put("source", source)
                put("track_key", keys.joinToString(",") + "|" + titleKey)
                put("title_key", titleKey)
                put("search", (parsed.title + " " + parsed.artists.joinToString(" ")).lowercase())
                put("added_at", known[f.uri] ?: now)
            }
            if (db.insertWithOnConflict("library_tracks", null, values, SQLiteDatabase.CONFLICT_REPLACE) != -1L) {
                inserted++
            }
        }
        db.setTransactionSuccessful()
    } finally {
        db.endTransaction()
    }
    return inserted
}

fun StatsDb.removeLibrarySource(source: String) {
    writableDatabase.delete("library_tracks", "source = ?", arrayOf(source))
}

// ---------- любимое и подсказки ----------

fun StatsDb.addFavorite(trackKey: String, artists: String, title: String, packageName: String) {
    val values = ContentValues().apply {
        put("track_key", trackKey)
        put("artists", artists)
        put("title", title)
        put("package", packageName)
        put("added_at", System.currentTimeMillis())
    }
    writableDatabase.insertWithOnConflict("favorites", null, values, SQLiteDatabase.CONFLICT_REPLACE)
}

fun StatsDb.removeFavorite(trackKey: String) {
    writableDatabase.delete("favorites", "track_key = ?", arrayOf(trackKey))
}

fun StatsDb.favorites(): List<Favorite> =
    readableDatabase.rawQuery(
        "SELECT track_key, artists, title, package FROM favorites ORDER BY added_at DESC", null
    ).use { c ->
        val list = mutableListOf<Favorite>()
        while (c.moveToNext()) {
            list.add(Favorite(c.getString(0), c.getString(1), c.getString(2), c.getString(3)))
        }
        list
    }

fun StatsDb.dismissSuggestion(trackKey: String) {
    val values = ContentValues().apply {
        put("track_key", trackKey)
        put("dismissed_at", System.currentTimeMillis())
    }
    writableDatabase.insertWithOnConflict(
        "dismissed_suggestions", null, values, SQLiteDatabase.CONFLICT_REPLACE
    )
}

/**
 * Треки, которые прозвучали не меньше [minPlays] раз с момента [sinceMs],
 * но ещё не в любимом и не отклонены. Прослушивания в самом Slukh не считаются:
 * такой трек уже лежит у тебя файлом. Числа вставлены в запрос напрямую:
 * SQLite сравнивает число с текстовым параметром неверно.
 */
fun StatsDb.suggestionCandidates(
    sinceMs: Long,
    minPlays: Int,
    ownPackage: String,
    limit: Int
): List<SuggestionCandidate> =
    readableDatabase.rawQuery(
        "SELECT p.track_key, MIN(p.artists), MIN(p.title), COUNT(*) AS c, " +
                "(SELECT p2.package FROM plays p2 WHERE p2.track_key = p.track_key " +
                "GROUP BY p2.package ORDER BY COUNT(*) DESC LIMIT 1) " +
                "FROM plays p WHERE p.played_at >= $sinceMs AND p.package <> ? " +
                "AND p.track_key NOT IN (SELECT track_key FROM favorites) " +
                "AND p.track_key NOT IN (SELECT track_key FROM dismissed_suggestions) " +
                "GROUP BY p.track_key HAVING c >= $minPlays " +
                "ORDER BY c DESC, MAX(p.played_at) DESC LIMIT $limit",
        arrayOf(ownPackage)
    ).use { c ->
        val list = mutableListOf<SuggestionCandidate>()
        while (c.moveToNext()) {
            list.add(
                SuggestionCandidate(
                    c.getString(0), c.getString(1), c.getString(2), c.getInt(3), c.getString(4).orEmpty()
                )
            )
        }
        list
    }

// ---------- сканер ----------

/**
 * Ищет музыку: на устройстве (MediaStore) и в папках, выбранных пользователем
 * (в том числе из облака через системный выбор папки).
 */
object LibraryScanner {
    private const val TAG = "Slukh"
    private val executor = Executors.newSingleThreadExecutor()
    private val main = Handler(Looper.getMainLooper())
    private val AUDIO_EXT = setOf("mp3", "flac", "m4a", "aac", "ogg", "opus", "wav", "wma", "alac")

    var scanning by mutableStateOf(false)
        private set
    var found by mutableIntStateOf(0)
        private set
    var message by mutableStateOf<String?>(null)
        private set

    fun audioPermission(): String =
        if (Build.VERSION.SDK_INT >= 33) Manifest.permission.READ_MEDIA_AUDIO
        else Manifest.permission.READ_EXTERNAL_STORAGE

    fun hasPermission(context: Context): Boolean =
        ContextCompat.checkSelfPermission(context, audioPermission()) == PackageManager.PERMISSION_GRANTED

    fun scan(context: Context) {
        if (scanning) return
        val app = context.applicationContext
        val trees = AppSettings.libraryTrees
        scanning = true
        found = 0
        message = null
        executor.execute {
            var total = 0
            var text = ""
            try {
                val db = StatsDb.get(app)
                if (hasPermission(app)) total += scanMediaStore(app, db)
                for (tree in trees) {
                    total += try {
                        scanTree(app, db, Uri.parse(tree))
                    } catch (e: Exception) {
                        Log.d(TAG, "Не удалось просканировать папку: $e")
                        0
                    }
                }
                text = if (!hasPermission(app) && trees.isEmpty()) {
                    "Нет доступа к файлам. Разреши доступ или добавь папку."
                } else {
                    "Готово. Найдено треков: $total"
                }
            } catch (e: Exception) {
                Log.d(TAG, "Ошибка сканирования: $e")
                text = "Не удалось просканировать музыку."
            }
            main.post {
                scanning = false
                message = text
                NowPlaying.version++
            }
        }
    }

    private fun report(count: Int) {
        main.post { found = count }
    }

    private fun scanMediaStore(context: Context, db: StatsDb): Int {
        val collection = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
        val projection = arrayOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.ARTIST,
            MediaStore.Audio.Media.ALBUM,
            MediaStore.Audio.Media.DURATION
        )
        val selection = "${MediaStore.Audio.Media.IS_MUSIC} != 0 AND ${MediaStore.Audio.Media.DURATION} >= 30000"
        val files = ArrayList<ScannedFile>()
        context.contentResolver.query(collection, projection, selection, null, null)?.use { c ->
            while (c.moveToNext()) {
                val uri = ContentUris.withAppendedId(collection, c.getLong(0)).toString()
                files.add(
                    ScannedFile(
                        uri,
                        c.getString(1).orEmpty(),
                        c.getString(2).orEmpty().replace("<unknown>", ""),
                        c.getString(3).orEmpty().replace("<unknown>", ""),
                        c.getLong(4)
                    )
                )
                if (files.size % 100 == 0) report(files.size)
            }
        }
        val inserted = db.replaceLibrarySource("mediastore", files)
        report(inserted)
        return inserted
    }

    private fun scanTree(context: Context, db: StatsDb, tree: Uri): Int {
        val files = ArrayList<ScannedFile>()
        walk(context, tree, DocumentsContract.getTreeDocumentId(tree), files, 0)
        return db.replaceLibrarySource("tree:$tree", files)
    }

    private fun walk(context: Context, tree: Uri, docId: String, out: MutableList<ScannedFile>, depth: Int) {
        if (depth > 8) return
        val children = DocumentsContract.buildChildDocumentsUriUsingTree(tree, docId)
        val columns = arrayOf(
            DocumentsContract.Document.COLUMN_DOCUMENT_ID,
            DocumentsContract.Document.COLUMN_DISPLAY_NAME,
            DocumentsContract.Document.COLUMN_MIME_TYPE
        )
        val folders = mutableListOf<String>()
        context.contentResolver.query(children, columns, null, null, null)?.use { c ->
            while (c.moveToNext()) {
                val id = c.getString(0)
                val name = c.getString(1).orEmpty()
                val mime = c.getString(2).orEmpty()
                if (mime == DocumentsContract.Document.MIME_TYPE_DIR) {
                    folders.add(id)
                } else if (isAudio(name, mime)) {
                    out.add(readFile(context, DocumentsContract.buildDocumentUriUsingTree(tree, id), name))
                    if (out.size % 20 == 0) report(out.size)
                }
            }
        }
        folders.forEach { walk(context, tree, it, out, depth + 1) }
    }

    private fun isAudio(name: String, mime: String): Boolean {
        val ext = name.substringAfterLast('.', "").lowercase()
        if (ext in setOf("m3u", "m3u8", "pls")) return false
        return mime.startsWith("audio/") || ext in AUDIO_EXT
    }

    /** Теги файла; если их нет, берём имя файла вида «Исполнитель - Название». */
    private fun readFile(context: Context, uri: Uri, fileName: String): ScannedFile {
        var title = ""
        var artist = ""
        var album = ""
        var duration = 0L
        val retriever = MediaMetadataRetriever()
        try {
            retriever.setDataSource(context, uri)
            title = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE).orEmpty()
            artist = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST)
                ?: retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUMARTIST).orEmpty()
            album = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUM).orEmpty()
            duration = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                ?.toLongOrNull() ?: 0L
        } catch (e: Exception) {
            Log.d(TAG, "Не удалось прочитать теги: $fileName")
        } finally {
            try {
                retriever.release()
            } catch (_: Exception) {
            }
        }
        if (title.isBlank()) {
            val base = fileName.substringBeforeLast('.')
            val dash = base.indexOf(" - ")
            if (dash > 0) {
                if (artist.isBlank()) artist = base.substring(0, dash).trim()
                title = base.substring(dash + 3).trim()
            } else {
                title = base
            }
        }
        return ScannedFile(uri.toString(), title, artist, album, duration)
    }
}