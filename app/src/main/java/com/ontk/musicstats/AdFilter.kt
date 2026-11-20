package com.ontk.musicstats

/**
 * Реклама в бесплатных плеерах приходит как обычная «музыка»: у Spotify исполнитель
 * «Advertisement • 1 of 2», а вместо названия просто «Spotify». Такие записи не считаются.
 */
object AdFilter {
    // Слово «реклама» на разных языках: так плеер подписывает исполнителя рекламной вставки
    private val WORDS = setOf(
        "advertisement", "advertising", "sponsored",
        "реклама", "рекламное сообщение", "рекламная пауза",
        "reklam", "werbung", "anzeige", "publicité", "publicidad", "anuncio",
        "pubblicità", "publicidade", "anúncio"
    )

    // «Advertisement • 1 of 2» → «advertisement»
    private val SUFFIX = Regex("""\s*[•·|:–—-].*$""")

    private fun head(artist: String): String = artist.lowercase().replace(SUFFIX, "").trim()

    fun isAd(artist: String, title: String, pkg: String, mediaId: String? = null): Boolean {
        if (mediaId != null && mediaId.contains("spotify:ad:", ignoreCase = true)) return true
        if (head(artist) in WORDS) return true
        if (artist.isBlank() && title.trim().lowercase() in WORDS) return true
        if (pkg.contains("spotify") && title.trim().equals("Spotify", ignoreCase = true)) return true
        return false
    }
}

/**
 * Удаляет из уже записанной истории прослушивания, похожие на рекламу.
 * Возвращает, сколько прослушиваний удалено.
 */
fun StatsDb.purgeAds(): Int {
    val ids = mutableListOf<Long>()
    readableDatabase.rawQuery("SELECT id, artists, title, package FROM plays", null).use { c ->
        while (c.moveToNext()) {
            if (AdFilter.isAd(c.getString(1), c.getString(2), c.getString(3))) ids.add(c.getLong(0))
        }
    }
    val adGenreKeys = mutableListOf<String>()
    readableDatabase.rawQuery("SELECT artist_key, artist FROM artist_genres", null).use { c ->
        while (c.moveToNext()) {
            if (AdFilter.isAd(c.getString(1), "", "")) adGenreKeys.add(c.getString(0))
        }
    }
    if (ids.isEmpty() && adGenreKeys.isEmpty()) return 0

    val db = writableDatabase
    db.beginTransaction()
    try {
        ids.chunked(500).forEach { chunk ->
            val list = chunk.joinToString(",")
            db.execSQL("DELETE FROM play_artists WHERE play_id IN ($list)")
            db.execSQL("DELETE FROM plays WHERE id IN ($list)")
        }
        adGenreKeys.forEach { key -> db.delete("artist_genres", "artist_key = ?", arrayOf(key)) }
        db.setTransactionSuccessful()
    } finally {
        db.endTransaction()
    }
    return ids.size
}