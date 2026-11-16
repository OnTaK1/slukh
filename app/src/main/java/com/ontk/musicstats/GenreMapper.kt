package com.ontk.musicstats

data class GenreGuess(val genre: String, val subgenres: List<String>)

object GenreMapper {

    private const val RAP = "Рэп / хип-хоп"
    private const val POP = "Поп"
    private const val ROCK = "Рок"
    private const val INDIE = "Инди / альтернатива"
    private const val METAL = "Метал"
    private const val PUNK = "Панк / хардкор"
    private const val ELECTRONIC = "Электроника"
    private const val RNB = "R&B / соул"
    private const val JAZZ = "Джаз / блюз"
    private const val CLASSICAL = "Классика"
    private const val FOLK = "Фолк / кантри"
    private const val CHANSON = "Шансон / авторская песня"
    private const val REGGAE = "Регги / ска"
    private const val LATIN = "Латино"
    private const val SOUNDTRACK = "Саундтрек"

    private const val MIN_TAG_COUNT = 10

    private class Rule(val genre: String, val sub: String?, val keywords: List<String>)

    private fun r(genre: String, sub: String?, vararg keywords: String) =
        Rule(genre, sub, keywords.toList())

    // Побеждает первое подходящее правило, поэтому частные правила стоят выше общих.
    // Ключевые слова сопоставляются как целые слова, без ё, в нижнем регистре.
    private val RULES = listOf(
        // --- рэп ---
        r(RAP, "эмо-рэп", "emo rap"),
        r(RAP, "клауд-рэп", "cloud rap"),
        r(RAP, "дрилл", "drill"),
        r(RAP, "трэп", "trap"),
        r(RAP, "русский рэп", "russian rap", "russian hip hop", "русский рэп", "русский хип хоп"),
        r(RAP, "андерграунд", "underground hip hop", "underground rap"),
        r(RAP, "бумбэп", "boom bap"),
        r(ELECTRONIC, "лоу-фай", "lo fi", "lofi"),
        r(ELECTRONIC, "фонк", "phonk"),
        r(RAP, null, "rap", "hip hop", "hiphop", "рэп", "хип хоп"),

        // --- метал ---
        r(METAL, "металкор", "metalcore"),
        r(METAL, "дэткор", "deathcore"),
        r(METAL, "дэт-метал", "death metal"),
        r(METAL, "блэк-метал", "black metal"),
        r(METAL, "ню-метал", "nu metal"),
        r(METAL, "фолк-метал", "folk metal"),
        r(METAL, "хеви-метал", "heavy metal"),
        r(METAL, "пауэр-метал", "power metal"),
        r(METAL, "трэш-метал", "thrash metal", "thrash"),
        r(METAL, "дум-метал", "doom metal"),
        r(METAL, null, "metal", "метал"),

        // --- гибриды с попом ---
        r(PUNK, "поп-панк", "pop punk"),
        r(ROCK, "поп-рок", "pop rock"),
        r(POP, "k-pop", "k pop", "kpop"),
        r(POP, "j-pop", "j pop", "jpop"),
        r(POP, "синти-поп", "synthpop", "synth pop"),
        r(POP, "электропоп", "electropop"),
        r(POP, "хайперпоп", "hyperpop"),
        r(POP, "танцевальный поп", "dance pop"),
        r(POP, "русский поп", "russian pop", "русский поп", "эстрада", "попса"),

        // --- инди / альтернатива ---
        r(INDIE, "дрим-поп", "dream pop"),
        r(INDIE, "инди-поп", "indie pop"),
        r(INDIE, "инди-рок", "indie rock"),
        r(INDIE, "шугейз", "shoegaze"),
        r(INDIE, "альтернативный рок", "alternative rock", "alt rock"),
        r(INDIE, null, "indie", "alternative", "инди", "альтернатива"),

        // --- электроника ---
        r(ELECTRONIC, "хардбасс", "hardbass"),
        r(ELECTRONIC, "драм-н-бейс", "drum and bass", "drum n bass", "dnb"),
        r(ELECTRONIC, "дабстеп", "dubstep"),
        r(ELECTRONIC, "транс", "trance"),
        r(ELECTRONIC, "техно", "techno"),
        r(ELECTRONIC, "хаус", "house"),
        r(ELECTRONIC, "синтвейв", "synthwave"),
        r(ELECTRONIC, "вейпорвейв", "vaporwave"),
        r(ELECTRONIC, "эмбиент", "ambient"),
        r(ELECTRONIC, "евродэнс", "eurodance"),
        r(ELECTRONIC, "хардстайл", "hardstyle"),
        r(
            ELECTRONIC, null,
            "electronic", "electronica", "electro", "edm", "idm", "downtempo",
            "chillout", "breakbeat", "gabber", "электроника"
        ),

        // --- R&B / соул ---
        r(RNB, "фанк", "funk"),
        r(RNB, "нео-соул", "neo soul"),
        r(RNB, null, "r&b", "rnb", "rhythm and blues", "soul", "соул"),

        // --- джаз / блюз ---
        r(JAZZ, "блюз", "blues", "блюз"),
        r(JAZZ, null, "jazz", "джаз"),

        // --- классика ---
        r(CLASSICAL, null, "classical", "neoclassical", "orchestral", "opera", "baroque", "классика"),

        // --- регги / ска ---
        r(REGGAE, "ска", "ska"),
        r(REGGAE, null, "reggae", "dancehall", "dub", "регги"),

        // --- латино ---
        r(LATIN, null, "latin", "latino", "reggaeton", "salsa", "bachata", "реггетон"),

        // --- шансон / бард ---
        r(CHANSON, "авторская песня", "bard", "бард", "авторская песня"),
        r(CHANSON, null, "chanson", "шансон", "blatnaya"),

        // --- фолк ---
        r(FOLK, null, "folk", "country", "bluegrass", "singer songwriter", "фолк", "кантри"),

        // --- панк ---
        r(PUNK, "пост-панк", "post punk"),
        r(PUNK, "хардкор", "hardcore"),
        r(PUNK, "эмо", "emo"),
        r(PUNK, null, "punk", "панк"),

        // --- рок ---
        r(ROCK, "хард-рок", "hard rock"),
        r(ROCK, "пост-рок", "post rock"),
        r(ROCK, "русский рок", "russian rock", "русский рок"),
        r(ROCK, "прогрессивный рок", "progressive rock", "prog rock"),
        r(ROCK, "классический рок", "classic rock"),
        r(ROCK, null, "rock", "рок"),

        // --- поп (общее) ---
        r(POP, null, "pop", "поп"),

        // --- саундтреки ---
        r(SOUNDTRACK, null, "soundtrack", "score", "ost", "саундтрек")
    )

    /** Поджанры, которые автоматика умеет определять для данного жанра. */
    fun subgenresFor(genre: String): List<String> =
        RULES.filter { it.genre == genre }.mapNotNull { it.sub }.distinct()

    private val SPACES = Regex(" +")

    private fun normalize(tag: String): String =
        buildString {
            for (ch in tag.lowercase().replace('ё', 'е')) {
                append(if (ch.isLetterOrDigit() || ch == '&') ch else ' ')
            }
        }.trim().replace(SPACES, " ")

    private fun findRule(tag: String): Rule? {
        val padded = " ${normalize(tag)} "
        return RULES.firstOrNull { rule -> rule.keywords.any { padded.contains(" $it ") } }
    }

    /** tags: пары (название тега, вес 0..100) от Last.fm. null — жанр определить не удалось. */
    fun guess(tags: List<Pair<String, Int>>): GenreGuess? {
        val genreScore = linkedMapOf<String, Int>()
        val subScore = mutableMapOf<Pair<String, String>, Int>()
        for ((name, count) in tags) {
            if (count < MIN_TAG_COUNT) continue
            val rule = findRule(name) ?: continue
            genreScore[rule.genre] = (genreScore[rule.genre] ?: 0) + count
            val sub = rule.sub
            if (sub != null) {
                val k = rule.genre to sub
                subScore[k] = (subScore[k] ?: 0) + count
            }
        }
        val best = genreScore.maxByOrNull { it.value } ?: return null
        val subs = subScore.entries
            .filter { it.key.first == best.key }
            .sortedByDescending { it.value }
            .take(3)
            .map { it.key.second }
        return GenreGuess(best.key, subs)
    }
}