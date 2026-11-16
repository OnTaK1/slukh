package com.ontk.musicstats

data class ParsedTrack(val artists: List<String>, val title: String)

object TrackParser {

    // Голосовые и видеосообщения — не музыка. Если что-то ещё просачивается, добавь сюда.
    private val NOT_MUSIC_MARKERS = listOf(
        "голосовое сообщение", "видеосообщение", "видео сообщение", "видео-сообщение",
        "voice message", "voice note", "video message"
    )

    // Слова-мусор в скобках названия: (Official Video), [Lyrics], (Премьера клипа) и т.п.
    private val NOISE_LONG = listOf(
        "official", "lyric", "audio", "video", "clip", "remaster", "explicit",
        "клип", "премьера", "текст", "караоке"
    )
    private val NOISE_SHORT = setOf("hd", "hq", "4k", "mv", "m/v")

    // Названия, которые нельзя резать на несколько исполнителей
    private val NO_SPLIT = setOf(
        "earth, wind & fire", "tyler, the creator", "simon & garfunkel", "hall & oates"
    )

    // Если после " - " идёт одно из этих слов, это версия трека, а не его название
    private val VERSION_FIRST_WORDS = setOf(
        "remaster", "remastered", "live", "remix", "version", "edit", "acoustic",
        "instrumental", "demo", "radio", "slowed", "sped", "cover", "from",
        "original", "extended", "bonus", "deluxe", "single", "album"
    )

    private val ARTIST_SPLIT = Regex(
        """\s*(?:,|;|&|\s[xх]\s|\sfeat\.?\s|\sft\.?\s|\svs\.?\s)\s*""",
        RegexOption.IGNORE_CASE
    )
    private val FEAT_BRACKET = Regex(
        """[(\[]\s*(?:feat\.?|ft\.?|featuring|при участии|при уч\.?|с уч\.?)\s+([^)\]]+)[)\]]""",
        RegexOption.IGNORE_CASE
    )
    private val FEAT_TAIL = Regex("""\s(?:feat\.?|ft\.?)\s+(.+)$""", RegexOption.IGNORE_CASE)
    private val BRACKET = Regex("""[(\[][^)\]]*[)\]]""")
    private val TAIL_NOISE = Regex(
        """\s[-–—]\s*(?:official|lyric|audio|video|клип|премьера)[^-–—]*$""",
        RegexOption.IGNORE_CASE
    )
    private val DASH = Regex("""\s[-–—]\s""")
    private val SPACES = Regex("""\s+""")

    /** Ключ для сравнения: без регистра, знаков препинания, звёздочек; ё = е. */
    fun key(s: String): String =
        s.lowercase().replace('ё', 'е').filter { it.isLetterOrDigit() }

    private fun clean(s: String): String =
        s.replace("\u200b", "").replace(SPACES, " ").trim()

    private fun isNotMusic(artist: String, title: String): Boolean {
        val a = artist.lowercase()
        val t = title.lowercase().trim()
        return NOT_MUSIC_MARKERS.any { a.contains(it) || t == it }
    }

    fun splitArtists(raw: String): List<String> {
        val text = clean(raw)
        if (text.isEmpty()) return emptyList()
        if (text.lowercase() in NO_SPLIT) return listOf(text)
        return text.split(ARTIST_SPLIT)
            .map { clean(it) }
            .filter { it.isNotEmpty() }
            .distinctBy { key(it) }
    }

    private fun stripNoise(title: String): String {
        var t = BRACKET.replace(title) { m ->
            val inner = m.value.trim('(', ')', '[', ']').trim().lowercase()
            if (NOISE_LONG.any { inner.contains(it) } || inner in NOISE_SHORT) " " else m.value
        }
        t = TAIL_NOISE.replace(t, "")
        t = t.substringBefore(" | ")
        return clean(t)
    }

    private fun splitArtistFromTitle(title: String): Pair<String, String>? {
        val m = DASH.find(title) ?: return null
        val left = clean(title.substring(0, m.range.first))
        val right = clean(title.substring(m.range.last + 1))
        if (left.isEmpty() || right.isEmpty()) return null
        val firstWord = right.lowercase().substringBefore(' ')
        if (firstWord in VERSION_FIRST_WORDS) return null
        return left to right
    }

    private fun extractFeat(title: String): Pair<String, List<String>> {
        val feats = mutableListOf<String>()
        FEAT_BRACKET.findAll(title).forEach { feats += splitArtists(it.groupValues[1]) }
        var t = FEAT_BRACKET.replace(title, " ")
        val tail = FEAT_TAIL.find(t)
        if (tail != null) {
            feats += splitArtists(tail.groupValues[1])
            t = t.substring(0, tail.range.first)
        }
        return clean(t) to feats
    }

    /** Возвращает null, если это не музыка (голосовое, видеосообщение и т.п.). */
    fun parse(rawArtist: String, rawTitle: String, packageName: String): ParsedTrack? {
        if (isNotMusic(rawArtist, rawTitle)) return null

        var title = stripNoise(clean(rawTitle))
        var artists = splitArtists(rawArtist)

        // YouTube: «трек» часто видео вида «Артист - Песня», а исполнитель = название канала
        if (packageName.contains("youtube")) {
            splitArtistFromTitle(title)?.let { (left, right) ->
                artists = splitArtists(left)
                title = right
            }
        }

        val (titleWithoutFeat, feats) = extractFeat(title)
        title = titleWithoutFeat
        artists = (artists + feats).distinctBy { key(it) }

        if (title.isEmpty()) return null
        if (artists.isEmpty()) artists = listOf("Неизвестный исполнитель")
        return ParsedTrack(artists, title)
    }
}