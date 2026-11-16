package com.ontk.musicstats

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.Random
import java.util.TreeSet
import kotlin.math.abs

private val MONTH_NAMES = listOf(
    "январь", "февраль", "март", "апрель", "май", "июнь",
    "июль", "август", "сентябрь", "октябрь", "ноябрь", "декабрь"
)

enum class Period(val label: String, private val fixedDays: Int, private val phrase: String) {
    WEEK("7 дней", 7, "за 7 дней"),
    MONTH("30 дней", 30, "за 30 дней"),
    YEAR("Год", 365, "за год"),
    THIS_MONTH("Этот месяц", 0, ""),
    LAST_MONTH("Прошлый месяц", 0, "");

    /** Календарный месяц (с 1-го числа), а не «последние N дней». */
    val isCalendar: Boolean get() = fixedDays == 0

    private val monthOffset: Int get() = if (this == LAST_MONTH) -1 else 0

    private fun monthStart(now: Long, offset: Int): Long =
        Calendar.getInstance().apply {
            timeInMillis = now
            set(Calendar.DAY_OF_MONTH, 1)
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
            add(Calendar.MONTH, offset)
        }.timeInMillis

    fun since(now: Long = System.currentTimeMillis()): Long =
        if (isCalendar) monthStart(now, monthOffset) else now - fixedDays * DAY_MS

    /** Верхняя граница (не включая). Для скользящих периодов её нет. */
    fun until(now: Long = System.currentTimeMillis()): Long =
        if (isCalendar) monthStart(now, monthOffset + 1) else Long.MAX_VALUE

    /** Начало предыдущего такого же периода. */
    fun prevSince(now: Long = System.currentTimeMillis()): Long =
        if (isCalendar) monthStart(now, monthOffset - 1) else since(now) - fixedDays * DAY_MS

    /** Сколько дней в периоде (для этого месяца: сколько уже прошло). */
    fun days(now: Long = System.currentTimeMillis()): Int {
        if (!isCalendar) return fixedDays
        val cal = Calendar.getInstance().apply { timeInMillis = since(now) }
        return if (this == THIS_MONTH) {
            Calendar.getInstance().apply { timeInMillis = now }.get(Calendar.DAY_OF_MONTH)
        } else {
            cal.getActualMaximum(Calendar.DAY_OF_MONTH)
        }
    }

    private fun monthName(now: Long): String {
        val cal = Calendar.getInstance().apply { timeInMillis = since(now) }
        return MONTH_NAMES[cal.get(Calendar.MONTH)]
    }

    private fun year(now: Long): Int =
        Calendar.getInstance().apply { timeInMillis = since(now) }.get(Calendar.YEAR)

    /** «за 7 дней», «за октябрь 2026». */
    val inPhrase: String
        get() {
            if (!isCalendar) return phrase
            val now = System.currentTimeMillis()
            return "за ${monthName(now)} ${year(now)}"
        }

    /** Крупный заголовок карточки: «Итоги» или название месяца. */
    val cardTitle: String
        get() = if (isCalendar) monthName(System.currentTimeMillis()).replaceFirstChar { it.uppercase() }
        else "Итоги"
}

data class PeriodTotals(
    val plays: Int,
    val totalMs: Long,
    val uniqueTracks: Int,
    val uniqueArtists: Int
)

data class Archetype(val title: String, val description: String)

/** Факты бывают трёх видов: число, шкала из двух частей и фраза. */
sealed class Fact {
    abstract fun sentence(): String

    data class Stat(val value: String, val caption: String) : Fact() {
        override fun sentence() = "$value — $caption"
    }

    data class Split(
        val label: String,
        val leftName: String,
        val leftPercent: Int,
        val rightName: String,
        val comment: String
    ) : Fact() {
        val rightPercent: Int get() = 100 - leftPercent
        override fun sentence() = comment
    }

    data class Line(val label: String, val text: String) : Fact() {
        override fun sentence() = text
    }
}

data class Recap(
    val period: Period,
    val totals: PeriodTotals,
    val topArtists: List<ArtistStat>,
    val topTracks: List<Triple<String, String, Int>>,
    val topGenres: List<Pair<String, Int>>,
    val topSubgenres: List<Pair<String, Int>>,
    val facts: List<Fact>,
    val phrases: List<String>,
    val equivalents: List<String>,
    val archetype: Archetype?
)

object RecapBuilder {

    private class TimeStats(
        val byHour: IntArray,
        val byWeekday: IntArray,
        val activeDays: Int,
        val longestStreak: Int
    )

    private class Equivalent(val minutes: Double, val category: String, val make: (Int) -> String)

    private fun plural(n: Int, one: String, few: String, many: String): String {
        val m100 = n % 100
        val m10 = n % 10
        return when {
            m100 in 11..14 -> many
            m10 == 1 -> one
            m10 in 2..4 -> few
            else -> many
        }
    }

    private fun mmss(sec: Long): String = "%d:%02d".format(sec / 60, sec % 60)

    // За сколько минут делается «дело» и как о нём сказать при n повторах
    private val EQUIVALENTS = listOf(
        Equivalent(120.0, "кино") { n -> "посмотреть $n ${plural(n, "фильм", "фильма", "фильмов")}" },
        Equivalent(150.0, "кино") { n -> "сходить в кино $n ${plural(n, "раз", "раза", "раз")}" },
        Equivalent(22.0, "сериалы") { n -> "посмотреть $n ${plural(n, "серию", "серии", "серий")} ситкома" },
        Equivalent(24.0, "сериалы") { n -> "посмотреть $n ${plural(n, "серию", "серии", "серий")} аниме" },
        Equivalent(480.0, "сон") { n -> "проспать $n ${plural(n, "ночь", "ночи", "ночей")}" },
        Equivalent(20.0, "сон") { n -> "вздремнуть $n ${plural(n, "раз", "раза", "раз")} по 20 минут" },
        Equivalent(360.0, "книги") { n -> "прочитать $n ${plural(n, "книгу", "книги", "книг")}" },
        Equivalent(20.0, "книги") { n -> "прочитать $n ${plural(n, "главу", "главы", "глав")} романа" },
        Equivalent(12.0, "прогулка") { n -> "пройти пешком $n км" },
        Equivalent(4.0, "прогулка") { n -> "проехать на велосипеде $n км" },
        Equivalent(270.0, "спорт") { n -> "пробежать $n ${plural(n, "марафон", "марафона", "марафонов")}" },
        Equivalent(60.0, "спорт") { n -> "пробежать $n ${plural(n, "раз", "раза", "раз")} по 10 км" },
        Equivalent(45.0, "спорт") { n -> "провести $n ${plural(n, "тренировку", "тренировки", "тренировок")}" },
        Equivalent(90.0, "спорт") { n ->
            "сыграть $n ${plural(n, "футбольный матч", "футбольных матча", "футбольных матчей")}"
        },
        Equivalent(2.0, "спорт") { n ->
            "простоять в планке $n ${plural(n, "подход", "подхода", "подходов")} по 2 минуты"
        },
        Equivalent(15.0, "еда") { n -> "неспешно выпить $n ${plural(n, "чашку", "чашки", "чашек")} кофе" },
        Equivalent(5.0, "еда") { n -> "заварить $n ${plural(n, "чашку", "чашки", "чашек")} чая" },
        Equivalent(10.0, "еда") { n -> "сварить $n ${plural(n, "порцию", "порции", "порций")} пасты" },
        Equivalent(20.0, "еда") { n -> "сварить $n ${plural(n, "порцию", "порции", "порций")} пельменей" },
        Equivalent(45.0, "еда") { n -> "испечь $n ${plural(n, "пиццу", "пиццы", "пицц")} с нуля" },
        Equivalent(120.0, "еда") { n -> "сварить $n ${plural(n, "кастрюлю", "кастрюли", "кастрюль")} борща" },
        Equivalent(60.0, "быт") { n -> "запустить $n ${plural(n, "стирку", "стирки", "стирок")} и дождаться конца" },
        Equivalent(10.0, "быт") { n -> "принять $n ${plural(n, "душ", "душа", "душей")}" },
        Equivalent(2.0, "быт") { n -> "почистить зубы $n ${plural(n, "раз", "раза", "раз")}" },
        Equivalent(20.0, "быт") { n -> "помыть посуду $n ${plural(n, "раз", "раза", "раз")}" },
        Equivalent(30.0, "игры") { n -> "сыграть $n ${plural(n, "партию", "партии", "партий")} в шахматы" },
        Equivalent(180.0, "игры") { n -> "собрать $n ${plural(n, "пазл", "пазла", "пазлов")} на 1000 деталей" },
        Equivalent(15.0, "игры") { n -> "сыграть $n ${plural(n, "партию", "партии", "партий")} в дурака" },
        Equivalent(90.0, "учёба") { n -> "отсидеть $n ${plural(n, "пару", "пары", "пар")} в университете" }
    )

    fun build(db: StatsDb, period: Period): Recap {
        val now = System.currentTimeMillis()
        val since = period.since(now)
        val until = period.until(now)
        val prevSince = period.prevSince(now)
        val totals = db.totals(since, until)
        val artists = db.topArtists(since, 8, until)
        val tracks = db.topTracks(since, 5, until)
        val genres = db.topGenres(since, 6, until)
        val subs = db.topSubgenres(since, 5, until)
        val points = db.playPoints(since, until)
        val ts = timeStats(points.map { it.playedAt })
        val firstPlay = db.firstPlayAt()
        // «новые исполнители» и «динамика» имеют смысл, только если данные есть до начала периода
        val newArtists =
            if (firstPlay != null && firstPlay < since) db.newArtistsCount(since, until) else 0
        val prevMs = if (firstPlay != null && firstPlay < prevSince + DAY_MS) {
            db.totals(prevSince, since).totalMs
        } else {
            0L
        }
        val facts = facts(period, totals, artists, tracks, genres, ts, points, newArtists, prevMs)
        return Recap(
            period = period,
            totals = totals,
            topArtists = artists,
            topTracks = tracks,
            topGenres = genres,
            topSubgenres = subs,
            facts = facts,
            phrases = facts.map { it.sentence() },
            equivalents = equivalents(period, totals.totalMs),
            archetype = archetype(totals, artists, genres, ts)
        )
    }

    private fun timeStats(times: List<Long>): TimeStats {
        val cal = Calendar.getInstance()
        val byHour = IntArray(24)
        val byWeekday = IntArray(8)
        val days = TreeSet<Long>()
        for (t in times) {
            cal.timeInMillis = t
            byHour[cal.get(Calendar.HOUR_OF_DAY)]++
            byWeekday[cal.get(Calendar.DAY_OF_WEEK)]++
            val offset = (cal.get(Calendar.ZONE_OFFSET) + cal.get(Calendar.DST_OFFSET)).toLong()
            days.add((t + offset) / DAY_MS)
        }
        var longest = 0
        var run = 0
        var prev = Long.MIN_VALUE
        for (d in days) {
            run = if (prev != Long.MIN_VALUE && d == prev + 1) run + 1 else 1
            if (run > longest) longest = run
            prev = d
        }
        return TimeStats(byHour, byWeekday, days.size, longest)
    }

    /** Четыре сравнения из разных категорий; набор стабилен в течение дня. */
    private fun equivalents(period: Period, totalMs: Long): List<String> {
        val minutes = totalMs / 60_000.0
        val cal = Calendar.getInstance()
        val rnd = Random(
            cal.get(Calendar.YEAR) * 1000L + cal.get(Calendar.DAY_OF_YEAR) +
                    period.ordinal * 100_000L
        )
        val used = HashSet<String>()
        val result = mutableListOf<String>()
        for (eq in EQUIVALENTS.shuffled(rnd)) {
            val n = (minutes / eq.minutes).toInt()
            if (n !in 1..99 || eq.category in used) continue
            used.add(eq.category)
            result.add(eq.make(n))
            if (result.size == 4) break
        }
        return result
    }

    private fun archetype(
        totals: PeriodTotals,
        artists: List<ArtistStat>,
        genres: List<Pair<String, Int>>,
        ts: TimeStats
    ): Archetype? {
        if (totals.plays < 10) return null
        val n = totals.plays.toDouble()
        val night = (ts.byHour[22] + ts.byHour[23] + (0..4).sumOf { ts.byHour[it] }) / n
        val morning = (5..10).sumOf { ts.byHour[it] } / n
        val topShare = (artists.firstOrNull()?.count ?: 0) / n
        val wideGenres = genres.filter { it.first != "Без жанра" }.count { it.second / n >= 0.10 }
        val explorer = totals.uniqueArtists / n
        return when {
            night >= 0.35 -> Archetype(
                "Ночная сова",
                "Больше трети музыки играет, когда нормальные люди уже спят."
            )
            topShare >= 0.35 -> Archetype(
                "Верный фанат",
                "Один исполнитель занимает большую часть твоего времени. Это называется преданность."
            )
            explorer >= 0.6 -> Archetype(
                "Исследователь",
                "Ты постоянно открываешь новых исполнителей и редко повторяешься."
            )
            wideGenres >= 4 -> Archetype(
                "Жанровый хамелеон",
                "От тяжёлого к нежному за пару треков: границы жанров тебе не мешают."
            )
            morning >= 0.30 -> Archetype(
                "Утренний заряд",
                "Твой день начинается с музыки, и это видно по статистике."
            )
            else -> Archetype(
                "Ровный ритм",
                "Ты слушаешь стабильно и без крайностей: музыка просто часть дня."
            )
        }
    }

    private fun facts(
        period: Period,
        totals: PeriodTotals,
        artists: List<ArtistStat>,
        tracks: List<Triple<String, String, Int>>,
        genres: List<Pair<String, Int>>,
        ts: TimeStats,
        points: List<PlayPoint>,
        newArtists: Int,
        prevMs: Long
    ): List<Fact> {
        if (totals.plays == 0) return emptyList()
        val plays = totals.plays
        val enough = plays >= 10
        val cal = Calendar.getInstance()
        // набор формулировок и фактов меняется каждый день
        val rnd = Random(
            cal.get(Calendar.YEAR) * 1000L + cal.get(Calendar.DAY_OF_YEAR) +
                    period.ordinal * 7919L
        )

        fun pick(vararg variants: String): String = variants[rnd.nextInt(variants.size)]

        val stats = mutableListOf<Fact>()
        val splits = mutableListOf<Fact>()
        val lines = mutableListOf<Fact>()

        val peakHour = ts.byHour.indices.maxByOrNull { ts.byHour[it] } ?: 0
        val hh = "%02d:00".format(peakHour)
        val top = artists.firstOrNull()

        // ---------- числа ----------
        if (ts.longestStreak >= 2) {
            stats.add(
                Fact.Stat(
                    "${ts.longestStreak}",
                    plural(ts.longestStreak, "день подряд", "дня подряд", "дней подряд") + " с музыкой"
                )
            )
        }
        if (plays >= 5) stats.add(Fact.Stat("${ts.activeDays}/${period.days()}", "дней с музыкой"))
        if (newArtists > 0) {
            stats.add(
                Fact.Stat(
                    "$newArtists",
                    plural(newArtists, "новый исполнитель", "новых исполнителя", "новых исполнителей")
                )
            )
        }
        if (enough) stats.add(Fact.Stat(hh, "любимый час прослушиваний"))
        if (plays >= 15) {
            stats.add(Fact.Stat("${(plays - totals.uniqueTracks) * 100 / plays}%", "прослушиваний — повторы"))
        }
        if (ts.activeDays >= 2) {
            stats.add(
                Fact.Stat(formatDuration(totals.totalMs / ts.activeDays), "музыки в день, когда она играла")
            )
        }
        if (prevMs > 0) {
            val diff = ((totals.totalMs - prevMs) * 100 / prevMs).toInt()
            stats.add(
                Fact.Stat(
                    (if (diff >= 0) "+" else "−") + "${abs(diff)}%",
                    "к прошлому периоду по времени"
                )
            )
        }
        val longest = points.filter { it.durationSec > 0 }.maxByOrNull { it.durationSec }
        if (longest != null) {
            stats.add(Fact.Stat(mmss(longest.durationSec), "самый длинный трек: ${longest.title}"))
        }

        // ---------- шкалы ----------
        if (enough && points.isNotEmpty()) {
            val weekend = points.count {
                cal.timeInMillis = it.playedAt
                val d = cal.get(Calendar.DAY_OF_WEEK)
                d == Calendar.SATURDAY || d == Calendar.SUNDAY
            }
            val weekendPct = weekend * 100 / points.size
            splits.add(
                Fact.Split(
                    "будни и выходные", "будни", 100 - weekendPct, "выходные",
                    pick(
                        "На выходные приходится $weekendPct% прослушиваний.",
                        "Выходные дают $weekendPct% музыки, будни — остальное.",
                        "Ты слушаешь на выходных на $weekendPct%."
                    )
                )
            )

            val night = points.count {
                cal.timeInMillis = it.playedAt
                val h = cal.get(Calendar.HOUR_OF_DAY)
                h >= 22 || h < 5
            }
            val nightPct = night * 100 / points.size
            splits.add(
                Fact.Split(
                    "день и ночь", "день", 100 - nightPct, "ночь",
                    pick(
                        "Ночью (с 22 до 5) играет $nightPct% музыки.",
                        "$nightPct% прослушиваний — ночные.",
                        "Ночная смена музыки: $nightPct%."
                    )
                )
            )
        }
        if (top != null && enough) {
            val share = top.count * 100 / plays
            if (share in 1..99) {
                splits.add(
                    Fact.Split(
                        "главный исполнитель", top.name, share, "остальные",
                        pick(
                            "${top.name} — $share% всех прослушиваний.",
                            "Один против всех: ${top.name} забирает $share%."
                        )
                    )
                )
            }
        }

        // ---------- фразы ----------
        if (top != null) {
            val share = top.count * 100 / plays
            lines.add(
                Fact.Line(
                    "исполнитель",
                    when {
                        share >= 50 -> pick(
                            "Больше половины прослушиваний — ${top.name}. Это любовь или одержимость?",
                            "${top.name} занимает больше половины твоего времени. Остальные просто в гостях."
                        )
                        share >= 30 -> pick(
                            "Почти каждое третье прослушивание — ${top.name}.",
                            "${top.name} стабильно держит первое место: $share% всех прослушиваний."
                        )
                        share >= 15 -> pick(
                            "${top.name} — твой главный исполнитель: $share% всех прослушиваний.",
                            "Если бы у периода был саундтрек, его пел бы ${top.name}."
                        )
                        else -> pick(
                            "Ты слушаешь разнообразно: ни один исполнитель не набрал и 15%.",
                            "Никакой монополии: любимчиков много, и никто не доминирует."
                        )
                    }
                )
            )
        }

        val realGenres = genres.filter { it.first != "Без жанра" }
        val topGenre = realGenres.firstOrNull()
        if (topGenre != null) {
            val share = topGenre.second * 100 / plays
            val many = realGenres.count { it.second * 100 / plays >= 10 }
            lines.add(
                Fact.Line(
                    "жанр",
                    when {
                        share >= 70 -> pick(
                            "Ты верен жанру «${topGenre.first}»: $share% прослушиваний.",
                            "«${topGenre.first}» — это почти всё, что у тебя играет ($share%)."
                        )
                        many >= 4 -> pick(
                            "Музыкальный всеядный: $many жанров занимают заметную часть твоего времени.",
                            "$many жанров делят твои уши почти поровну."
                        )
                        else -> pick(
                            "Главный жанр периода — «${topGenre.first}».",
                            "Если коротко, этот период прошёл под «${topGenre.first}»."
                        )
                    }
                )
            )
        }

        val topTrack = tracks.firstOrNull()
        if (topTrack != null && topTrack.third >= 5) {
            val times = "${topTrack.third} ${plural(topTrack.third, "раз", "раза", "раз")}"
            lines.add(
                Fact.Line(
                    "гимн",
                    pick(
                        "«${topTrack.second}» ты включал $times. Похоже на гимн периода.",
                        "$times подряд, не подряд — но «${topTrack.second}» явно зацепил.",
                        "Трек «${topTrack.second}» прозвучал $times: рекорд повторов."
                    )
                )
            )
        }

        if (enough) {
            lines.add(
                Fact.Line(
                    "время суток",
                    when (peakHour) {
                        in 0..4 -> pick(
                            "Ночной слушатель: пик около $hh. Сон подождёт.",
                            "Самое громкое время — $hh. Соседи, простите."
                        )
                        in 5..9 -> pick(
                            "Ранняя пташка: больше всего музыки около $hh.",
                            "День начинается с музыки: пик около $hh."
                        )
                        in 10..16 -> pick(
                            "Дневной режим: пик прослушиваний около $hh.",
                            "Рабочие часы с наушниками: пик около $hh."
                        )
                        else -> pick(
                            "Вечерний режим: пик прослушиваний около $hh.",
                            "К $hh музыка включается на полную."
                        )
                    }
                )
            )

            val dayNames = arrayOf(
                "", "в воскресенье", "в понедельник", "во вторник", "в среду",
                "в четверг", "в пятницу", "в субботу"
            )
            val peakDay = (1..7).maxByOrNull { ts.byWeekday[it] } ?: 1
            lines.add(
                Fact.Line(
                    "день недели",
                    pick(
                        "Чаще всего музыка играет у тебя ${dayNames[peakDay]}.",
                        "Любимый день для музыки — ${dayNames[peakDay].removePrefix("в ").removePrefix("во ")}."
                    )
                )
            )
        }

        // рекордный день по времени
        val dayMs = HashMap<Long, Long>()
        val dayTs = HashMap<Long, Long>()
        for (p in points) {
            cal.timeInMillis = p.playedAt
            val offset = (cal.get(Calendar.ZONE_OFFSET) + cal.get(Calendar.DST_OFFSET)).toLong()
            val key = (p.playedAt + offset) / DAY_MS
            dayMs[key] = (dayMs[key] ?: 0L) + p.playedMs
            if (!dayTs.containsKey(key)) dayTs[key] = p.playedAt
        }
        val bestDay = dayMs.maxByOrNull { it.value }
        if (bestDay != null && bestDay.value > 0 && ts.activeDays >= 3) {
            val label = SimpleDateFormat("EEEE, d MMMM", Locale.forLanguageTag("ru"))
                .format(Date(dayTs[bestDay.key] ?: 0L))
            val time = formatDuration(bestDay.value)
            lines.add(
                Fact.Line(
                    "рекорд",
                    pick(
                        "Самый музыкальный день — $label: $time.",
                        "$label: $time с наушниками. Рекорд периода.",
                        "Больше всего музыки было в день «$label» — $time."
                    )
                )
            )
        }

        val shortest = points.filter { it.durationSec >= 30 }.minByOrNull { it.durationSec }
        if (shortest != null && shortest.trackKey != longest?.trackKey) {
            lines.add(
                Fact.Line(
                    "рекордсмен",
                    pick(
                        "Самый короткий трек — «${shortest.title}», всего ${mmss(shortest.durationSec)}.",
                        "«${shortest.title}» пролетел за ${mmss(shortest.durationSec)}. Блиц."
                    )
                )
            )
        }

        stats.shuffle(rnd)
        splits.shuffle(rnd)
        lines.shuffle(rnd)
        return stats.take(4) + splits.take(2) + lines.take(3)
    }
}