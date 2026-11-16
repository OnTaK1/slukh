package com.ontk.musicstats

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt

@Composable
private fun SectionLabel(text: String) {
    MonoLabel(text)
    Spacer(modifier = Modifier.height(12.dp))
}

@Composable
private fun GenreBar(name: String, count: Int, total: Int) {
    val cs = MaterialTheme.colorScheme
    val fraction = if (total == 0) 0f else (count.toFloat() / total).coerceIn(0f, 1f)
    var shown by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { shown = true }
    val animated by animateFloatAsState(
        targetValue = if (shown) fraction else 0f,
        animationSpec = tween(800),
        label = "genreBar"
    )
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Row(modifier = Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                GenreIcon(name, iconSize = 16.dp, tint = cs.primary)
                Text(
                    name,
                    modifier = Modifier.padding(start = 8.dp),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Text(
                "${(fraction * 100).roundToInt()}%",
                fontFamily = FontFamily.Monospace,
                style = MaterialTheme.typography.bodySmall,
                color = cs.onSurfaceVariant
            )
        }
        Spacer(modifier = Modifier.height(6.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(6.dp)
                .background(cs.onBackground.copy(alpha = 0.08f))
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(animated.coerceAtLeast(0.02f))
                    .height(6.dp)
                    .background(cs.primary)
            )
        }
    }
}

@Composable
private fun ArtistRow(stat: ArtistStat, onClick: () -> Unit) {
    val subtitle = when {
        stat.genre == null -> "жанр не определён · нажми, чтобы задать"
        stat.subgenres.isNullOrBlank() -> "${stat.genre}"
        else -> "${stat.genre} · ${stat.subgenres}"
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        ArtistMark(stat.name)
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                stat.name,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            stat.count.toString(),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Black
        )
    }
}

@Composable
fun RecapScreen() {
    val context = LocalContext.current
    val cs = MaterialTheme.colorScheme
    val db = remember { StatsDb.get(context) }
    var periodIndex by rememberSaveable { mutableIntStateOf(0) }
    val period = Period.entries[periodIndex]
    var editArtist by remember { mutableStateOf<ArtistStat?>(null) }
    var genreInput by remember { mutableStateOf("") }
    val version = NowPlaying.version
    val recap = remember(version, periodIndex) { RecapBuilder.build(db, period) }
    val times = remember(version, periodIndex) {
        db.playTimes(period.since(), period.until())

    }
    val topGenre = recap.topGenres.firstOrNull { it.first != "Без жанра" }?.first
    val accent = cs.primary.toArgb()

    editArtist?.let { stat ->
        val genres = remember(stat) { (db.knownGenres() + Vibes.allGenres()).distinct() }
        val subs = remember(stat) { db.knownSubgenres() }
        GenrePickerDialog(
            artistName = stat.name,
            initialGenre = stat.genre.orEmpty(),
            initialSubgenres = stat.subgenres.orEmpty(),
            genres = genres,
            subgenres = subs,
            onSave = { genre, sub ->
                val key = TrackParser.key(stat.name)
                if (genre.isBlank()) {
                    db.clearGenre(key)
                    GenreResolver.requestMissing(context)
                } else {
                    db.setManualGenre(key, stat.name, genre, sub)
                }
                NowPlaying.version++
                editArtist = null
            },
            onDismiss = { editArtist = null }
        )
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Text(
                "Итоги",
                style = MaterialTheme.typography.headlineLarge,
                fontWeight = FontWeight.Black
            )
        }
        item {
            Pills(Period.entries.map { it.label }, periodIndex) { periodIndex = it }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { Overlay.storiesPeriod = periodIndex }, shape = PillShape) {
                    Text("Смотреть как истории")
                }
                OutlinedButton(onClick = { Overlay.playlistPeriod = periodIndex }, shape = PillShape) {
                    Text("Плейлист")
                }
            }
        }

        if (recap.totals.plays == 0) {
            item {
                Panel {
                    Text(
                        "Здесь пока пусто",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        "Послушай музыку: итоги появятся, как только наберётся первое прослушивание.",
                        color = cs.onSurfaceVariant
                    )
                }
            }
        } else {
            // единственный большой красный блок
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(CardShape)
                        .background(cs.primary)
                        .padding(22.dp)
                ) {
                    MonoLabel("я слушал музыку ${period.inPhrase}", color = cs.onPrimary.copy(alpha = 0.8f))
                    Spacer(modifier = Modifier.height(14.dp))
                    Text(
                        formatDuration(recap.totals.totalMs),
                        style = MaterialTheme.typography.displayMedium,
                        fontWeight = FontWeight.Black,
                        color = cs.onPrimary
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        "${recap.totals.plays} прослушиваний · " +
                                "${recap.totals.uniqueTracks} треков · " +
                                "${recap.totals.uniqueArtists} исполнителей",
                        color = cs.onPrimary.copy(alpha = 0.9f)
                    )
                    if (topGenre != null) {
                        val vibe = Vibes.forGenre(topGenre)
                        Spacer(modifier = Modifier.height(14.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            GenreIcon(topGenre, iconSize = 16.dp, tint = cs.onPrimary.copy(alpha = 0.8f))
                            Spacer(modifier = Modifier.width(8.dp))
                            MonoLabel("$topGenre — ${vibe.tagline}", color = cs.onPrimary.copy(alpha = 0.8f))
                        }
                    }
                }
            }

            recap.archetype?.let { a ->
                item {
                    Panel {
                        SectionLabel("твой тип слушателя")
                        Text(
                            a.title,
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Black
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(a.description, color = cs.onSurfaceVariant)
                    }
                }
            }

            if (recap.equivalents.isNotEmpty()) {
                item {
                    Panel {
                        SectionLabel("за это время ты мог бы")
                        recap.equivalents.forEach { line ->
                            Text(
                                "— $line",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.padding(vertical = 4.dp)
                            )
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            "Варианты обновляются каждый день.",
                            style = MaterialTheme.typography.bodySmall,
                            color = cs.onSurfaceVariant
                        )
                    }
                }
            }

            item {
                Panel {
                    SectionLabel("карта тепла: когда играет музыка")
                    HeatMap(times)
                }
            }

            if (recap.facts.isNotEmpty()) {
                item { FactsSection(recap.facts) }
            }

            item {
                Panel {
                    SectionLabel("жанры")
                    recap.topGenres.forEach { (genre, count) ->
                        GenreBar(genre, count, recap.totals.plays)
                    }
                    if (recap.topSubgenres.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            "Поджанры: " + recap.topSubgenres.joinToString(" · ") { it.first },
                            style = MaterialTheme.typography.bodySmall,
                            color = cs.onSurfaceVariant
                        )
                    }
                }
            }

            item {
                Panel {
                    SectionLabel("топ исполнителей")
                    recap.topArtists.forEach { stat ->
                        ArtistRow(stat) {
                            editArtist = stat
                            genreInput = stat.genre.orEmpty()
                        }
                    }
                }
            }

            item {
                Panel {
                    SectionLabel("топ треков")
                    recap.topTracks.forEachIndexed { i, (artists, title, count) ->
                        if (i > 0) Spacer(modifier = Modifier.height(10.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                "${i + 1}",
                                modifier = Modifier.width(28.dp),
                                fontFamily = FontFamily.Monospace,
                                color = cs.onSurfaceVariant,
                                fontWeight = FontWeight.Bold
                            )
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    title,
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    artists,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = cs.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("×$count", fontWeight = FontWeight.Black)
                        }
                    }
                }
            }

            item {
                var cardStyle by remember { mutableStateOf(CardStyle.POSTER) }
                val card = remember(recap, accent, cardStyle) { ShareCard.render(recap, accent, cardStyle) }
                Panel {
                    SectionLabel("карточка для соцсетей")
                    Pills(CardStyle.entries.map { it.label }, cardStyle.ordinal) {
                        cardStyle = CardStyle.entries[it]
                    }
                    Spacer(modifier = Modifier.height(14.dp))
                    Image(
                        bitmap = card.asImageBitmap(),
                        contentDescription = "Карточка итогов",
                        modifier = Modifier
                            .fillMaxWidth(0.85f)
                            .clip(RoundedCornerShape(6.dp)),
                        contentScale = ContentScale.FillWidth
                    )
                    Spacer(modifier = Modifier.height(14.dp))
                    Button(
                        onClick = { ShareCard.share(context, recap, accent, cardStyle) },
                        shape = PillShape
                    ) {
                        Text("Поделиться")
                    }
                }
            }
        }
    }
}