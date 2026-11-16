package com.ontk.musicstats

data class RecentPlay(
    val artists: String,
    val title: String,
    val packageName: String,
    val playedAt: Long,
    val genre: String?
)

fun StatsDb.recentPlaysWithGenre(limit: Int): List<RecentPlay> =
    readableDatabase.rawQuery(
        "SELECT p.artists, p.title, p.package, p.played_at, " +
                "(SELECT g.genre FROM play_artists pa " +
                "JOIN artist_genres g ON g.artist_key = pa.artist_key " +
                "WHERE pa.play_id = p.id AND g.genre IS NOT NULL LIMIT 1) " +
                "FROM plays p ORDER BY p.played_at DESC LIMIT $limit", null
    ).use { c ->
        val list = mutableListOf<RecentPlay>()
        while (c.moveToNext()) {
            list.add(
                RecentPlay(
                    c.getString(0), c.getString(1), c.getString(2),
                    c.getLong(3), c.getString(4)
                )
            )
        }
        list
    }

/** Самые частые жанры среди последних [playsLimit] прослушиваний. */
fun StatsDb.recentGenres(playsLimit: Int, top: Int): List<Pair<String, Int>> =
    readableDatabase.rawQuery(
        "SELECT g.genre, COUNT(*) AS c FROM " +
                "(SELECT id FROM plays ORDER BY played_at DESC LIMIT $playsLimit) r " +
                "JOIN play_artists pa ON pa.play_id = r.id " +
                "JOIN artist_genres g ON g.artist_key = pa.artist_key " +
                "WHERE g.genre IS NOT NULL GROUP BY g.genre ORDER BY c DESC LIMIT $top", null
    ).use { c ->
        val list = mutableListOf<Pair<String, Int>>()
        while (c.moveToNext()) list.add(c.getString(0) to c.getInt(1))
        list
    }

fun StatsDb.artistGenre(artistKey: String): String? =
    readableDatabase.rawQuery(
        "SELECT genre FROM artist_genres WHERE artist_key = ?", arrayOf(artistKey)
    ).use { if (it.moveToFirst()) it.getString(0) else null }
data class PlayPoint(
    val playedAt: Long,
    val playedMs: Long,
    val durationSec: Long,
    val trackKey: String,
    val artists: String,
    val title: String
)

fun StatsDb.playPoints(sinceMs: Long, untilMs: Long = Long.MAX_VALUE): List<PlayPoint> =
    readableDatabase.rawQuery(
        "SELECT played_at, played_ms, duration_sec, track_key, artists, title " +
                "FROM plays WHERE played_at >= $sinceMs AND played_at < $untilMs ORDER BY played_at", null
    ).use { c ->
        val list = mutableListOf<PlayPoint>()
        while (c.moveToNext()) {
            list.add(
                PlayPoint(
                    c.getLong(0), c.getLong(1), c.getLong(2),
                    c.getString(3), c.getString(4), c.getString(5)
                )
            )
        }
        list
    }

/** Время самого первого прослушивания в базе (null, если база пуста). */
fun StatsDb.firstPlayAt(): Long? =
    readableDatabase.rawQuery("SELECT MIN(played_at) FROM plays", null).use { c ->
        if (c.moveToFirst() && !c.isNull(0)) c.getLong(0) else null
    }

/** Сколько исполнителей впервые зазвучали после sinceMs. */
fun StatsDb.newArtistsCount(sinceMs: Long, untilMs: Long = Long.MAX_VALUE): Int =
    readableDatabase.rawQuery(
        "SELECT COUNT(*) FROM (SELECT pa.artist_key FROM play_artists pa " +
                "JOIN plays p ON p.id = pa.play_id GROUP BY pa.artist_key " +
                "HAVING MIN(p.played_at) >= $sinceMs AND MIN(p.played_at) < $untilMs)", null
    ).use { c -> if (c.moveToFirst()) c.getInt(0) else 0 }

/** Жанры, которые уже есть в базе, самые частые первыми. */
fun StatsDb.knownGenres(): List<String> =
    readableDatabase.rawQuery(
        "SELECT genre, COUNT(*) AS c FROM artist_genres " +
                "WHERE genre IS NOT NULL AND genre <> '' GROUP BY genre ORDER BY c DESC", null
    ).use { c ->
        val list = mutableListOf<String>()
        while (c.moveToNext()) list.add(c.getString(0))
        list
    }

/** Поджанры, которые уже есть в базе, самые частые первыми. */
fun StatsDb.knownSubgenres(): List<String> {
    val counts = HashMap<String, Int>()
    readableDatabase.rawQuery(
        "SELECT subgenres FROM artist_genres WHERE subgenres IS NOT NULL AND subgenres <> ''", null
    ).use { c ->
        while (c.moveToNext()) {
            c.getString(0).split(",")
                .map { it.trim() }
                .filter { it.isNotEmpty() }
                .forEach { counts[it] = (counts[it] ?: 0) + 1 }
        }
    }
    return counts.entries.sortedByDescending { it.value }.map { it.key }
}

data class PlaylistTrack(
    val artists: String,
    val title: String,
    val count: Int,
    val durationSec: Long,
    val packageName: String
)

/** Самые частые треки периода; packageName: приложение, где трек слушали чаще всего. */
fun StatsDb.playlistTracks(sinceMs: Long, untilMs: Long, limit: Int): List<PlaylistTrack> =
    readableDatabase.rawQuery(
        "SELECT MIN(p.artists), MIN(p.title), COUNT(*) AS c, MAX(p.duration_sec), " +
                "(SELECT p2.package FROM plays p2 WHERE p2.track_key = p.track_key " +
                "AND p2.played_at >= $sinceMs AND p2.played_at < $untilMs " +
                "GROUP BY p2.package ORDER BY COUNT(*) DESC LIMIT 1) " +
                "FROM plays p WHERE p.played_at >= $sinceMs AND p.played_at < $untilMs " +
                "GROUP BY p.track_key ORDER BY c DESC, MAX(p.played_at) DESC LIMIT $limit", null
    ).use { c ->
        val list = mutableListOf<PlaylistTrack>()
        while (c.moveToNext()) {
            list.add(
                PlaylistTrack(
                    c.getString(0), c.getString(1), c.getInt(2), c.getLong(3),
                    c.getString(4).orEmpty()
                )
            )
        }
        list
    }