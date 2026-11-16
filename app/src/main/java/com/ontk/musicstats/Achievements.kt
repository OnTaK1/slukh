package com.ontk.musicstats

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import java.util.Calendar
import java.util.TreeSet

data class Achievement(
    val title: String,
    val description: String,
    val unlocked: Boolean,
    val progress: String
)

object Achievements {

    private fun achievement(
        title: String,
        description: String,
        value: Long,
        goal: Long,
        unit: String = ""
    ) = Achievement(title, description, value >= goal, "${minOf(value, goal)}/$goal$unit")

    fun build(db: StatsDb): List<Achievement> {
        val totals = db.totals(0L)
        val points = db.playPoints(0L)
        val genres = db.topGenres(0L, 100).count { it.first != "Без жанра" }
        val topTrackCount = db.topTracks(0L, 1).firstOrNull()?.third ?: 0

        val cal = Calendar.getInstance()
        var night = 0
        var morning = 0
        val days = TreeSet<Long>()
        val dayMs = HashMap<Long, Long>()
        for (p in points) {
            cal.timeInMillis = p.playedAt
            val hour = cal.get(Calendar.HOUR_OF_DAY)
            if (hour >= 22 || hour < 5) night++
            if (hour in 5..9) morning++
            val offset = (cal.get(Calendar.ZONE_OFFSET) + cal.get(Calendar.DST_OFFSET)).toLong()
            val day = (p.playedAt + offset) / DAY_MS
            days.add(day)
            dayMs[day] = (dayMs[day] ?: 0L) + p.playedMs
        }

        var streak = 0
        var run = 0
        var prev = Long.MIN_VALUE
        for (d in days) {
            run = if (prev != Long.MIN_VALUE && d == prev + 1) run + 1 else 1
            if (run > streak) streak = run
            prev = d
        }
        val bestDayMinutes = (dayMs.values.maxOrNull() ?: 0L) / 60_000
        val hours = totals.totalMs / 3_600_000

        return listOf(
            achievement("Первый шаг", "Первое засчитанное прослушивание", totals.plays.toLong(), 1),
            achievement("Сотка", "100 прослушиваний", totals.plays.toLong(), 100),
            achievement("Тысячник", "1000 прослушиваний", totals.plays.toLong(), 1000),
            achievement("Сутки музыки", "24 часа музыки суммарно", hours, 24, " ч"),
            achievement("Неделя без тишины", "7 дней подряд с музыкой", streak.toLong(), 7),
            achievement("Месяц ритма", "30 дней подряд с музыкой", streak.toLong(), 30),
            achievement("Коллекционер", "50 разных исполнителей", totals.uniqueArtists.toLong(), 50),
            achievement("Всеядный", "8 разных жанров", genres.toLong(), 8),
            achievement("Ночная смена", "20 прослушиваний ночью (22–5)", night.toLong(), 20),
            achievement("Ранняя пташка", "20 прослушиваний утром (5–10)", morning.toLong(), 20),
            achievement("Один на повторе", "Один трек 10 раз", topTrackCount.toLong(), 10),
            achievement("Марафонец", "4 часа музыки за один день", bestDayMinutes, 240, " мин")
        )
    }
}

@Composable
fun AchievementsPanel(list: List<Achievement>) {
    val cs = MaterialTheme.colorScheme
    var expanded by remember { mutableStateOf(false) }
    val unlocked = list.count { it.unlocked }
    val shown = if (expanded) list else list.sortedByDescending { it.unlocked }.take(4)

    Panel {
        Row(verticalAlignment = Alignment.CenterVertically) {
            MonoLabel("достижения", modifier = Modifier.weight(1f))
            Text(
                "$unlocked/${list.size}",
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold
            )
        }
        Spacer(modifier = Modifier.height(12.dp))
        shown.forEach { a ->
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(14.dp)
                        .background(if (a.unlocked) cs.primary else Color.Transparent)
                        .border(1.5.dp, if (a.unlocked) cs.primary else cs.outline)
                )
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        a.title,
                        fontWeight = if (a.unlocked) FontWeight.Bold else FontWeight.Medium,
                        color = if (a.unlocked) cs.onSurface else cs.onSurfaceVariant
                    )
                    Text(
                        a.description,
                        style = MaterialTheme.typography.bodySmall,
                        color = cs.onSurfaceVariant
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    a.progress,
                    fontFamily = FontFamily.Monospace,
                    style = MaterialTheme.typography.bodySmall,
                    color = cs.onSurfaceVariant
                )
            }
        }
        TextButton(onClick = { expanded = !expanded }) {
            Text(if (expanded) "Свернуть" else "Показать все")
        }
    }
}