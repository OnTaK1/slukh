package com.ontk.musicstats

import androidx.activity.compose.BackHandler
import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Какие истории сейчас открыты: индекс периода или null. */
object Overlay {
    var storiesPeriod by mutableStateOf<Int?>(null)
    var playlistPeriod by mutableStateOf<Int?>(null)
}

private class Story(
    val label: String,
    val big: String,
    val caption: String,
    val genre: String? = null
)

private fun buildStories(r: Recap): List<Story> {
    val list = mutableListOf<Story>()
    if (r.totals.plays == 0) {
        list.add(Story("итоги ${r.period.inPhrase}", "тишина", "Послушай музыку: итоги появятся здесь."))
        return list
    }
    list.add(
        Story(
            "итоги ${r.period.inPhrase}",
            formatDuration(r.totals.totalMs),
            "музыки · ${r.totals.plays} прослушиваний, ${r.totals.uniqueTracks} треков, " +
                    "${r.totals.uniqueArtists} исполнителей"
        )
    )
    r.topArtists.firstOrNull()?.let { a ->
        list.add(
            Story(
                "главный исполнитель",
                a.name,
                "${a.count} прослушиваний" + (a.genre?.let { " · $it" } ?: "")
            )
        )
    }
    r.topTracks.firstOrNull()?.let { (artists, title, count) ->
        list.add(Story("главный трек", title, "$artists · ×$count"))
    }
    val genre = r.topGenres.firstOrNull { it.first != "Без жанра" }
    if (genre != null) {
        val vibe = Vibes.forGenre(genre.first)
        val subs = r.topSubgenres.take(3).joinToString(" · ") { it.first }
        list.add(
            Story(
                "главный жанр",
                genre.first,
                vibe.tagline + if (subs.isNotEmpty()) "\n$subs" else "",
                genre.first
            )
        )
    }
    r.archetype?.let { list.add(Story("твой тип слушателя", it.title, it.description)) }
    if (r.equivalents.isNotEmpty()) {
        list.add(
            Story(
                "за это время ты мог бы",
                r.equivalents.first(),
                r.equivalents.drop(1).take(2).joinToString("\n") { "· $it" }
            )
        )
    }
    return list
}

@Composable
private fun BigText(text: String, color: Color) {
    val size = when {
        text.length <= 8 -> 72.sp
        text.length <= 14 -> 56.sp
        text.length <= 24 -> 44.sp
        else -> 34.sp
    }
    Text(
        text,
        color = color,
        fontSize = size,
        lineHeight = size * 1.05f,
        fontWeight = FontWeight.Black,
        letterSpacing = (-1).sp,
        maxLines = 4,
        overflow = TextOverflow.Ellipsis
    )
}

@Composable
fun StoriesScreen(period: Period, onClose: () -> Unit) {
    val context = LocalContext.current
    val cs = MaterialTheme.colorScheme
    val db = remember { StatsDb.get(context) }
    val recap = remember(period) { RecapBuilder.build(db, period) }
    val stories = remember(recap) { buildStories(recap) }
    var index by remember { mutableIntStateOf(0) }
    BackHandler { onClose() }

    // фоны чередуются: акцент, инверсия, обычный
    val bgTarget = when (index % 3) {
        0 -> cs.primary
        1 -> cs.onBackground
        else -> cs.background
    }
    val fg = when (index % 3) {
        0 -> cs.onPrimary
        1 -> cs.background
        else -> cs.onBackground
    }
    val bg by animateColorAsState(bgTarget, tween(300), label = "storyBg")
    val isLast = index == stories.size - 1
    val noRipple = remember { MutableInteractionSource() }

    Box(modifier = Modifier.fillMaxSize().background(bg)) {
        // тап: левая половина — назад, правая — вперёд
        Row(modifier = Modifier.fillMaxSize()) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .clickable(interactionSource = noRipple, indication = null) {
                        if (index > 0) index--
                    }
            )
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .clickable(interactionSource = noRipple, indication = null) {
                        if (isLast) onClose() else index++
                    }
            )
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(horizontal = 24.dp, vertical = 12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                stories.indices.forEach { i ->
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(3.dp)
                            .background(fg.copy(alpha = if (i <= index) 1f else 0.25f))
                    )
                }
            }
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = onClose) { Text("✕ закрыть", color = fg) }
            }
            Spacer(modifier = Modifier.weight(1f))
            Crossfade(targetState = index, animationSpec = tween(250), label = "story") { i ->
                val s = stories[i.coerceIn(0, stories.size - 1)]
                Column {
                    MonoLabel(s.label, color = fg.copy(alpha = 0.7f))
                    Spacer(modifier = Modifier.height(16.dp))
                    if (s.genre != null) {
                        GenreIcon(s.genre, iconSize = 84.dp, tint = fg)
                        Spacer(modifier = Modifier.height(16.dp))
                    }
                    BigText(s.big, fg)
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        s.caption,
                        color = fg.copy(alpha = 0.85f),
                        style = MaterialTheme.typography.titleMedium
                    )
                }
            }
            Spacer(modifier = Modifier.weight(1f))
            if (isLast && recap.totals.plays > 0) {
                Button(
                    onClick = { ShareCard.share(context, recap, cs.primary.toArgb()) },
                    shape = PillShape,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = fg,
                        contentColor = bgTarget
                    )
                ) { Text("Поделиться итогами") }
            } else {
                Text(
                    "тап справа — дальше, слева — назад",
                    color = fg.copy(alpha = 0.5f),
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
    }
}