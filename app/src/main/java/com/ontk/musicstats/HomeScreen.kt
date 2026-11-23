package com.ontk.musicstats

import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.app.NotificationManagerCompat
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

@Composable
fun HomeScreen() {
    val context = LocalContext.current
    val db = remember { StatsDb.get(context) }
    val version = NowPlaying.version
    val now = rememberNowInfo(db)
    val hasAccess = remember(NowPlaying.resumeTick) {
        NotificationManagerCompat.getEnabledListenerPackages(context).contains(context.packageName)
    }
    val today = remember(version) { db.totals(startOfToday()) }
    val weekGenres = remember(version) {
        db.topGenres(System.currentTimeMillis() - 7 * DAY_MS, 5)
            .filter { it.first != "Без жанра" }
    }
    val recentGenres = remember(version) { db.recentGenres(40, 5) }
    val recent = remember(version) { db.recentPlaysWithGenre(8) }
    val fmt = remember { SimpleDateFormat("dd.MM HH:mm", Locale.getDefault()) }
    val achievements = remember(version) { Achievements.build(db) }
    val mood = remember(recentGenres, now) {
        val list = recentGenres.toMutableList()
        val g = now?.genre
        if (now != null && now.playing && g != null) list.add(g to 4)
        val c = Calendar.getInstance()
        Moods.compute(list, c.get(Calendar.HOUR_OF_DAY), c.get(Calendar.DAY_OF_YEAR))
    }
    val topGenre = recentGenres.firstOrNull()?.first
    val suggestion = remember(version, Suggestions.tick, AppSettings.suggestionsEnabled, AppSettings.suggestMinPlays) {
        if (AppSettings.suggestionsEnabled) {
            Suggestions.compute(db, AppSettings.suggestMinPlays, context.packageName)
        } else null
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Text(
                "Что играет?",
                style = MaterialTheme.typography.headlineLarge,
                fontWeight = FontWeight.Black
            )
        }

        if (UpdateChecker.visible() != null) {
            item { UpdateBanner() }
        }

        if (suggestion != null) {
            item { SuggestionCard(suggestion) }
        }

        if (!hasAccess) {
            item {
                Panel {
                    Text(
                        "Нужен доступ к уведомлениям",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        "Так приложение узнаёт, что сейчас играет в плеерах. " +
                                "Музыку оно только считает и ничего в ней не меняет.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Button(
                        onClick = {
                            context.startActivity(
                                Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
                            )
                        },
                        shape = PillShape
                    ) {
                        Text("Выдать доступ")
                    }
                }
            }
        }

        if (AppSettings.lastfmKey.isBlank()) {
            item { KeySetupCard() }
        }

        item { MoodHero(mood, topGenre) }

        item {
            if (now != null) {
                NowPlayingCard(now)
            } else {
                Panel {
                    MonoLabel("сейчас")
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        "Тишина. Включи что-нибудь.",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                StatTile("сегодня", formatDuration(today.totalMs), Modifier.weight(1f))
                StatTile("треков", today.plays.toString(), Modifier.weight(1f))
                StatTile("артистов", today.uniqueArtists.toString(), Modifier.weight(1f))
            }
        }

        if (weekGenres.isNotEmpty()) {
            item { VibePanel(weekGenres) }
        }

        if (recent.isNotEmpty()) {
            item { AchievementsPanel(achievements) }
            item { MonoLabel("недавно") }
            item {
                Panel {
                    recent.forEachIndexed { i, play ->
                        if (i > 0) Spacer(modifier = Modifier.height(14.dp))
                        RecentRow(play, fmt)
                    }
                }
            }
        }
    }
}

@Composable
private fun StatTile(label: String, value: String, modifier: Modifier = Modifier) {
    Panel(modifier = modifier, padding = 14.dp) {
        MonoLabel(label)
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            value,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Black,
            maxLines = 1
        )
    }
}

@Composable
private fun VibePanel(genres: List<Pair<String, Int>>) {
    val top = genres.first()
    val vibe = Vibes.forGenre(top.first)
    Panel {
        MonoLabel("вайб недели")
        Spacer(modifier = Modifier.height(10.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            GenreIcon(top.first, iconSize = 30.dp, tint = MaterialTheme.colorScheme.primary)
            Spacer(modifier = Modifier.width(10.dp))
            Text(
                top.first,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Black
            )
        }
        Text(vibe.tagline, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (genres.size > 1) {
            Spacer(modifier = Modifier.height(14.dp))
            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                genres.drop(1).forEach { (genre, _) -> GenreChip(genre) }
            }
        }
    }
}

@Composable
private fun RecentRow(play: RecentPlay, fmt: SimpleDateFormat) {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        GenreIcon(play.genre, iconSize = 20.dp, tint = MaterialTheme.colorScheme.primary)
        Spacer(modifier = Modifier.width(8.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                play.title,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                play.artists,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            fmt.format(Date(play.playedAt)),
            fontFamily = FontFamily.Monospace,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}