package com.ontk.musicstats

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.util.Calendar
import kotlin.math.PI
import kotlin.math.ceil
import kotlin.math.roundToInt
import kotlin.math.sin

// ---------- что сейчас играет ----------

data class NowInfo(
    val artists: String,
    val title: String,
    val source: String,
    val playing: Boolean,
    val genre: String?,
    val vibe: Vibe
)

fun sourceName(pkg: String): String = when {
    pkg.contains("youtube.music") -> "YouTube Music"
    pkg.contains("youtube") -> "YouTube"
    pkg.contains("vkontakte") || pkg.contains(".vk.") || pkg.contains("musicvk") -> "VK"
    pkg.contains("telegram") -> "Telegram"
    pkg.contains("spotify") -> "Spotify"
    pkg.contains("yandex.music") -> "Яндекс Музыка"
    else -> pkg.substringAfterLast('.')
}

@Composable
fun rememberNowInfo(db: StatsDb): NowInfo? {
    val artist = NowPlaying.artist
    val title = NowPlaying.title
    val pkg = NowPlaying.pkg
    val playing = NowPlaying.playing
    val version = NowPlaying.version
    return remember(artist, title, pkg, playing, version) {
        val parsed = if (title.isEmpty()) null else TrackParser.parse(artist, title, pkg)
        if (parsed == null) {
            null
        } else {
            val main = parsed.artists.first()
            val genre = db.artistGenre(TrackParser.key(main))
            NowInfo(
                parsed.artists.joinToString(", "),
                parsed.title,
                sourceName(pkg),
                playing,
                genre,
                Vibes.forGenre(genre)
            )
        }
    }
}

/** Плавный эквалайзер: 5 плоских столбиков, анимация на частоте экрана. */
@Composable
fun EqualizerBars(playing: Boolean, color: Color, modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "eq")
    val phase = transition.animateFloat(
        initialValue = 0f,
        targetValue = (2 * PI).toFloat(),
        animationSpec = infiniteRepeatable(tween(2400, easing = LinearEasing), RepeatMode.Restart),
        label = "eqPhase"
    )
    val amplitude = animateFloatAsState(
        targetValue = if (playing) 1f else 0f,
        animationSpec = tween(350),
        label = "eqAmplitude"
    )
    Canvas(modifier = modifier.width(26.dp).height(16.dp)) {
        val bars = 5
        val gap = 2.dp.toPx()
        val barWidth = (size.width - gap * (bars - 1)) / bars
        // целые множители — анимация зацикливается без скачка
        val multipliers = intArrayOf(2, 3, 4, 3, 2)
        val offsets = floatArrayOf(0f, 1.3f, 2.6f, 3.9f, 5.2f)
        for (i in 0 until bars) {
            val wave = 0.5f + 0.5f * sin(phase.value * multipliers[i] + offsets[i])
            val barHeight = size.height * (0.16f + 0.84f * amplitude.value * wave)
            drawRect(
                color = color,
                topLeft = Offset(i * (barWidth + gap), size.height - barHeight),
                size = Size(barWidth, barHeight)
            )
        }
    }
}

@Composable
fun NowPlayingCard(info: NowInfo) {
    val cs = MaterialTheme.colorScheme
    val bg = if (info.playing) cs.onBackground else cs.surfaceVariant
    val fg = if (info.playing) cs.background else cs.onSurface
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(CardShape)
            .background(bg)
            .padding(20.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            MonoLabel(if (info.playing) "сейчас играет" else "пауза", color = fg.copy(alpha = 0.7f))
            Spacer(modifier = Modifier.width(12.dp))
            EqualizerBars(info.playing, if (info.playing) cs.primary else fg.copy(alpha = 0.5f))
            Spacer(modifier = Modifier.weight(1f))
            MonoLabel(info.source, color = fg.copy(alpha = 0.7f))
        }
        Spacer(modifier = Modifier.height(22.dp))
        Text(
            info.title,
            color = fg,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Black,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            info.artists,
            color = fg.copy(alpha = 0.8f),
            style = MaterialTheme.typography.titleMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        if (info.genre != null) {
            Spacer(modifier = Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                GenreIcon(info.genre, iconSize = 16.dp, tint = fg.copy(alpha = 0.7f))
                Spacer(modifier = Modifier.width(8.dp))
                MonoLabel(info.genre, color = fg.copy(alpha = 0.7f))
            }
        }
    }
}

@Composable
fun MiniPlayer(info: NowInfo) {
    val cs = MaterialTheme.colorScheme
    val bg = if (info.playing) cs.primary else cs.surfaceVariant
    val fg = if (info.playing) cs.onPrimary else cs.onSurface
    Row(
        modifier = Modifier
            .padding(horizontal = 12.dp, vertical = 6.dp)
            .fillMaxWidth()
            .clip(CardShape)
            .background(bg)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        EqualizerBars(info.playing, fg)
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                info.title,
                color = fg,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                info.artists,
                color = fg.copy(alpha = 0.75f),
                style = MaterialTheme.typography.bodySmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

// ---------- слово-настроение ----------

private fun contrast(a: Color, b: Color): Float {
    val l1 = maxOf(a.luminance(), b.luminance())
    val l2 = minOf(a.luminance(), b.luminance())
    return (l1 + 0.05f) / (l2 + 0.05f)
}

@Composable
fun MoodHero(mood: Mood?, topGenre: String?) {
    val cs = MaterialTheme.colorScheme
    if (mood == null) {
        Column(modifier = Modifier.fillMaxWidth()) {
            MonoLabel("настроение")
            Spacer(modifier = Modifier.height(10.dp))
            Text(
                "тишина",
                style = MaterialTheme.typography.displaySmall,
                fontWeight = FontWeight.Light,
                color = cs.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                "Включи что-нибудь, и я скажу, какой у тебя вайб.",
                color = cs.onSurfaceVariant
            )
        }
        return
    }

    // акцентным слово красится только если оно читается на фоне
    val wordColor = when (mood.level) {
        4 -> if (contrast(cs.primary, cs.background) >= 3f) cs.primary else cs.onBackground
        3, 2 -> cs.onBackground
        else -> cs.onSurfaceVariant
    }
    val wordSize = when (mood.level) {
        4 -> 60.sp
        3 -> 56.sp
        2 -> 48.sp
        else -> 52.sp
    }
    val wordWeight = when (mood.level) {
        4 -> FontWeight.Black
        3 -> FontWeight.ExtraBold
        2 -> FontWeight.SemiBold
        1 -> FontWeight.Light
        else -> FontWeight.ExtraLight
    }

    val transition = rememberInfiniteTransition(label = "mood")
    val jitter = transition.animateFloat(
        -1f, 1f,
        infiniteRepeatable(tween(215, easing = LinearEasing), RepeatMode.Reverse),
        label = "jitter"
    )
    val breath = transition.animateFloat(
        0.35f, 1f,
        infiniteRepeatable(
            tween(if (mood.level == 0) 5000 else 3200, easing = LinearEasing),
            RepeatMode.Reverse
        ),
        label = "breath"
    )

    var shown by remember { mutableStateOf(false) }
    LaunchedEffect(mood.energy) { shown = true }
    val fill = animateFloatAsState(
        targetValue = if (shown) mood.energy / 100f else 0f,
        animationSpec = tween(900),
        label = "energy"
    )
    val track = cs.onBackground.copy(alpha = 0.12f)
    val tickColor = cs.primary

    Column(modifier = Modifier.fillMaxWidth()) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(cs.outline)
        )
        Spacer(modifier = Modifier.height(14.dp))
        MonoLabel("настроение сейчас")
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            mood.word,
            color = wordColor,
            fontSize = wordSize,
            fontWeight = wordWeight,
            lineHeight = wordSize * 1.05f,
            letterSpacing = if (mood.level >= 3) (-1).sp else 0.sp,
            maxLines = 2,
            modifier = Modifier.graphicsLayer {
                if (mood.level == 4) {
                    translationX = jitter.value * 2.dp.toPx()
                    translationY = -jitter.value * 1.dp.toPx()
                }
                if (mood.level <= 1) alpha = breath.value
            }
        )
        Spacer(modifier = Modifier.height(16.dp))
        // шкала энергии: 32 деления
        Canvas(modifier = Modifier.fillMaxWidth().height(14.dp)) {
            val ticks = 32
            val gap = 3.dp.toPx()
            val tickWidth = (size.width - gap * (ticks - 1)) / ticks
            val filled = (fill.value * ticks).roundToInt()
            for (i in 0 until ticks) {
                drawRect(
                    color = if (i < filled) tickColor else track,
                    topLeft = Offset(i * (tickWidth + gap), 0f),
                    size = Size(tickWidth, size.height)
                )
            }
        }
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            "// энергия ${mood.energy}% · ${topGenre ?: "жанр не определён"}",
            color = cs.onSurfaceVariant,
            fontFamily = FontFamily.Monospace,
            fontSize = 12.sp
        )
    }
}

// ---------- карта тепла ----------

private fun buildHeatGrid(times: List<Long>): Array<IntArray> {
    val grid = Array(7) { IntArray(24) }
    val cal = Calendar.getInstance()
    for (t in times) {
        cal.timeInMillis = t
        val day = (cal.get(Calendar.DAY_OF_WEEK) + 5) % 7 // пн = 0 … вс = 6
        grid[day][cal.get(Calendar.HOUR_OF_DAY)]++
    }
    return grid
}

@Composable
fun HeatMap(times: List<Long>) {
    val cs = MaterialTheme.colorScheme
    val grid = remember(times) { buildHeatGrid(times) }
    val max = grid.maxOf { row -> row.maxOrNull() ?: 0 }
    val dayShort = listOf("пн", "вт", "ср", "чт", "пт", "сб", "вс")
    val dayIn = listOf(
        "в понедельник", "во вторник", "в среду", "в четверг",
        "в пятницу", "в субботу", "в воскресенье"
    )
    val track = cs.onBackground.copy(alpha = 0.07f)
    val accent = cs.primary
    val alphas = floatArrayOf(0f, 0.28f, 0.52f, 0.76f, 1f)

    var peakDay = 0
    var peakHour = 0
    var peak = -1
    for (d in 0..6) {
        for (h in 0..23) {
            if (grid[d][h] > peak) {
                peak = grid[d][h]
                peakDay = d
                peakHour = h
            }
        }
    }

    Row {
        Column {
            dayShort.forEach { label ->
                Box(
                    modifier = Modifier.height(16.dp).width(24.dp),
                    contentAlignment = Alignment.CenterStart
                ) {
                    Text(
                        label,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 10.sp,
                        color = cs.onSurfaceVariant
                    )
                }
            }
        }
        Canvas(modifier = Modifier.fillMaxWidth().height(110.dp)) {
            val gap = 2.dp.toPx()
            val cellW = (size.width - gap * 23) / 24f
            val cellH = (size.height + gap) / 7f - gap
            for (d in 0..6) {
                for (h in 0..23) {
                    val count = grid[d][h]
                    val level = if (max == 0) 0 else ceil(count * 4f / max).toInt()
                    val color = if (level == 0) track else accent.copy(alpha = alphas[level])
                    drawRect(
                        color = color,
                        topLeft = Offset(h * (cellW + gap), d * (cellH + gap)),
                        size = Size(cellW, cellH)
                    )
                }
            }
        }
    }
    Row(
        modifier = Modifier.fillMaxWidth().padding(start = 24.dp, top = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        listOf("0", "6", "12", "18", "23").forEach {
            Text(
                it,
                fontFamily = FontFamily.Monospace,
                fontSize = 10.sp,
                color = cs.onSurfaceVariant
            )
        }
    }
    if (max > 0) {
        Spacer(modifier = Modifier.height(10.dp))
        Text(
            "Горячая точка: ${dayIn[peakDay]}, ${"%02d".format(peakHour)}:00",
            style = MaterialTheme.typography.bodyMedium
        )
    }
}