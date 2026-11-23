package com.ontk.musicstats

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

data class Suggestion(
    val trackKey: String,
    val artists: String,
    val title: String,
    val plays: Int,
    val packageName: String,
    /** Файл этого трека в твоей библиотеке, если есть. */
    val local: LibraryTrack?
)

/**
 * Подсказки про часто слушаемые треки. Ничего не скачиваем и не копируем из сервисов:
 * предлагаем включить файл из своей библиотеки, добавить в «Любимое», открыть трек в плеере
 * (чтобы пользователь сам сохранил его там, если у него есть подписка) или найти и поддержать
 * исполнителя в магазине.
 */
object Suggestions {
    const val WINDOW_DAYS = 14

    /** Меняется после действий пользователя, чтобы главный экран пересчитал подсказку. */
    var tick by mutableIntStateOf(0)

    fun compute(db: StatsDb, minPlays: Int, ownPackage: String): Suggestion? {
        val since = System.currentTimeMillis() - WINDOW_DAYS * DAY_MS
        val c = db.suggestionCandidates(since, minPlays, ownPackage, 5).firstOrNull() ?: return null
        return Suggestion(c.trackKey, c.artists, c.title, c.plays, c.packageName, db.findLocal(c.artists, c.title))
    }

    fun bandcampUrl(artists: String, title: String): String =
        "https://bandcamp.com/search?q=" + Uri.encode("${artists.substringBefore(',')} $title") + "&item_type=t"
}

private fun openUrl(context: Context, url: String) {
    try {
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
    } catch (_: Exception) {
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SuggestionCard(s: Suggestion) {
    val context = LocalContext.current
    val db = remember { StatsDb.get(context) }
    val cs = MaterialTheme.colorScheme

    Panel {
        MonoLabel("часто слушаешь")
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            s.title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Black
        )
        Text(s.artists, color = cs.onSurfaceVariant)
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            "${s.plays} раз за ${Suggestions.WINDOW_DAYS} дней" +
                    if (s.packageName.isNotEmpty()) " · ${sourceName(s.packageName)}" else "",
            style = MaterialTheme.typography.bodySmall,
            color = cs.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(12.dp))
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (s.local != null) {
                Button(
                    onClick = { PlayerController.playQueue(context, listOf(s.local)) },
                    shape = PillShape
                ) { Text("Включить из библиотеки") }
            } else if (s.packageName.isNotEmpty()) {
                Button(
                    onClick = { PlayerLauncher.play(context, s.artists, s.title, s.packageName) },
                    shape = PillShape
                ) { Text("Открыть в ${sourceName(s.packageName)}") }
            }
            OutlinedButton(
                onClick = {
                    db.addFavorite(s.trackKey, s.artists, s.title, s.packageName)
                    Suggestions.tick++
                },
                shape = PillShape
            ) { Text("В любимое") }
            OutlinedButton(
                onClick = { openUrl(context, Suggestions.bandcampUrl(s.artists, s.title)) },
                shape = PillShape
            ) { Text("Найти и поддержать") }
            TextButton(onClick = {
                db.dismissSuggestion(s.trackKey)
                Suggestions.tick++
            }) { Text("Не предлагать") }
        }
    }
}

/** Панель настроек: когда показывать подсказки. */
@Composable
fun SuggestionsPanel() {
    val context = LocalContext.current
    val cs = MaterialTheme.colorScheme
    val options = listOf(2, 3, 5)
    Panel {
        MonoLabel("подсказки")
        Spacer(modifier = Modifier.height(10.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Switch(
                checked = AppSettings.suggestionsEnabled,
                onCheckedChange = { AppSettings.setSuggestionsEnabled(context, it) }
            )
            Spacer(modifier = Modifier.width(10.dp))
            Text("Предлагать, что делать с любимыми треками")
        }
        Spacer(modifier = Modifier.height(10.dp))
        MonoLabel("после скольких прослушиваний")
        Spacer(modifier = Modifier.height(8.dp))
        Pills(listOf("2 раза", "3 раза", "5 раз"), options.indexOf(AppSettings.suggestMinPlays).coerceAtLeast(0)) {
            AppSettings.setSuggestMinPlays(context, options[it])
        }
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            "Подсказка появляется на главном экране. Сам Slukh ничего не скачивает из сервисов.",
            style = MaterialTheme.typography.bodySmall,
            color = cs.onSurfaceVariant
        )
    }
}