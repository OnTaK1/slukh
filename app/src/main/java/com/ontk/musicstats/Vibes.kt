package com.ontk.musicstats

data class Vibe(val glyph: String, val tagline: String)

object Vibes {
    val NEUTRAL = Vibe("·", "твой звук")

    private val VIBES = mapOf(
        "Рэп / хип-хоп" to Vibe("▲", "ритм и слова"),
        "Поп" to Vibe("●", "мелодии, которые застревают"),
        "Рок" to Vibe("✕", "громко и живо"),
        "Инди / альтернатива" to Vibe("◇", "тихо и по-своему"),
        "Метал" to Vibe("◆", "тяжело и мощно"),
        "Панк / хардкор" to Vibe("✚", "быстро и без правил"),
        "Электроника" to Vibe("■", "пульс и свет"),
        "R&B / соул" to Vibe("≈", "тепло и плавно"),
        "Джаз / блюз" to Vibe("♪", "полуночный клуб"),
        "Классика" to Vibe("○", "без спешки"),
        "Фолк / кантри" to Vibe("△", "дорога и костёр"),
        "Шансон / авторская песня" to Vibe("☆", "истории под гитару"),
        "Регги / ска" to Vibe("□", "лёгкий шаг"),
        "Латино" to Vibe("★", "жара и танцы"),
        "Саундтрек" to Vibe("▶", "кино в наушниках")
    )

    /** Все стандартные жанры приложения. */
    fun allGenres(): List<String> = VIBES.keys.toList()

    fun forGenre(genre: String?): Vibe {
        if (genre.isNullOrBlank() || genre == "Без жанра") return NEUTRAL
        return VIBES[genre] ?: Vibe("·", "свой путь")
    }
}

/** level: 0 (спокойно) … 4 (драйв); energy: 0..100 */
data class Mood(val level: Int, val word: String, val energy: Int)

object Moods {
    private val ENERGY = mapOf(
        "Метал" to 0.95,
        "Панк / хардкор" to 0.95,
        "Рок" to 0.8,
        "Рэп / хип-хоп" to 0.75,
        "Электроника" to 0.7,
        "Латино" to 0.65,
        "Поп" to 0.55,
        "Регги / ска" to 0.4,
        "R&B / соул" to 0.4,
        "Инди / альтернатива" to 0.35,
        "Фолк / кантри" to 0.3,
        "Шансон / авторская песня" to 0.3,
        "Саундтрек" to 0.3,
        "Джаз / блюз" to 0.25,
        "Классика" to 0.15
    )

    private val WORDS = listOf(
        listOf("спокойствие", "почти шёпот", "дышим"),
        listOf("тихо", "неспешно", "мягко"),
        listOf("ровно", "в потоке", "обычный день"),
        listOf("ГРОМКО", "БЫСТРО", "ГРЯСЬЬЬ"),
        listOf("ДРАЙВ", "НА МАКСИМУМ", "БЕЗ ТОРМОЗОВ")
    )

    /** genres: пары (жанр, вес) по последним прослушиваниям. null — данных нет. */
    fun compute(genres: List<Pair<String, Int>>, hour: Int, dayOfYear: Int): Mood? {
        var sum = 0.0
        var weight = 0
        for ((genre, count) in genres) {
            val e = ENERGY[genre] ?: continue
            sum += e * count
            weight += count
        }
        if (weight == 0) return null
        var x = sum / weight
        if (hour in 0..5) x -= 0.08
        x = x.coerceIn(0.0, 1.0)
        val level = when {
            x >= 0.8 -> 4
            x >= 0.62 -> 3
            x >= 0.42 -> 2
            x >= 0.27 -> 1
            else -> 0
        }
        val words = WORDS[level]
        return Mood(level, words[(dayOfYear + level) % words.size], (x * 100).toInt())
    }
}