package com.ontk.musicstats

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

private const val PLAYLIST_SIZE = 30

private val PLAYER_LABELS = listOf("Где слушал", "YouTube Music", "Spotify", "Яндекс Музыка", "VK")
private val PLAYER_PKGS = listOf(
    "", PlayerLauncher.YT_MUSIC, PlayerLauncher.SPOTIFY, PlayerLauncher.YANDEX, "com.vkontakte.android"
)

private fun playlistText(period: Period, tracks: List<PlaylistTrack>): String =
    buildString {
        append("Slukh — топ треков ").append(period.inPhrase).append('\n')
        tracks.forEachIndexed { i, t ->
            append(i + 1).append(". ").append(t.artists).append(" — ").append(t.title).append('\n')
        }
    }.trimEnd()

private fun shareText(context: Context, text: String) {
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_TEXT, text)
    }
    context.startActivity(Intent.createChooser(intent, "Поделиться списком"))
}

private fun copyText(context: Context, text: String) {
    val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    cm.setPrimaryClip(ClipData.newPlainText("Slukh", text))
    Toast.makeText(context, "Список скопирован", Toast.LENGTH_SHORT).show()
}

@Composable
fun PlayButton(onClick: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    Box(
        modifier = Modifier
            .size(42.dp)
            .clip(PillShape)
            .background(cs.primary)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.size(16.dp)) {
            val path = Path().apply {
                moveTo(size.width * 0.15f, 0f)
                lineTo(size.width, size.height / 2f)
                lineTo(size.width * 0.15f, size.height)
                close()
            }
            drawPath(path, cs.onPrimary)
        }
    }
}

@Composable
private fun TrackRow(index: Int, track: PlaylistTrack, onPlay: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            "${index + 1}",
            modifier = Modifier.width(34.dp),
            fontFamily = FontFamily.Monospace,
            style = MaterialTheme.typography.bodySmall,
            color = cs.onSurfaceVariant
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                track.title,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                track.artists,
                style = MaterialTheme.typography.bodySmall,
                color = cs.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                "${sourceName(track.packageName)} · ×${track.count}",
                fontFamily = FontFamily.Monospace,
                style = MaterialTheme.typography.labelSmall,
                color = cs.onSurfaceVariant
            )
        }
        Spacer(modifier = Modifier.width(10.dp))
        PlayButton(onPlay)
    }
}

@Composable
fun PlaylistScreen(period: Period, onClose: () -> Unit) {
    val context = LocalContext.current
    val cs = MaterialTheme.colorScheme
    val db = remember { StatsDb.get(context) }
    val tracks = remember(period) {
        db.playlistTracks(period.since(), period.until(), PLAYLIST_SIZE)
    }
    val totalMs = tracks.sumOf { it.durationSec } * 1000
    val text = remember(tracks) { playlistText(period, tracks) }
    BackHandler { onClose() }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = cs.background,
        contentColor = cs.onBackground
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(horizontal = 20.dp)
        ) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Start) {
                TextButton(onClick = onClose) { Text("← назад") }
            }
            MonoLabel("плейлист · ${period.inPhrase}")
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                "Топ треков",
                style = MaterialTheme.typography.headlineLarge,
                fontWeight = FontWeight.Black
            )
            Spacer(modifier = Modifier.height(4.dp))
            if (tracks.isNotEmpty()) {
                Text(
                    "${tracks.size} треков · ${formatDuration(totalMs)}",
                    color = cs.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { shareText(context, text) }, shape = PillShape) {
                        Text("Поделиться списком")
                    }
                    OutlinedButton(onClick = { copyText(context, text) }, shape = PillShape) {
                        Text("Копировать")
                    }
                }
                Spacer(modifier = Modifier.height(14.dp))
                MonoLabel("включать в")
                Spacer(modifier = Modifier.height(6.dp))
                Pills(
                    PLAYER_LABELS,
                    PLAYER_PKGS.indexOf(AppSettings.playlistPlayer).coerceAtLeast(0)
                ) { AppSettings.setPlaylistPlayer(context, PLAYER_PKGS[it]) }
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    "Кнопка справа открывает трек в выбранном плеере.",
                    style = MaterialTheme.typography.bodySmall,
                    color = cs.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(8.dp))
                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    itemsIndexed(tracks) { i, t ->
                        TrackRow(i, t) {
                            val target = AppSettings.playlistPlayer.ifEmpty { t.packageName }
                            val ok = PlayerLauncher.play(context, t.artists, t.title, target)
                            if (!ok) {
                                Toast.makeText(
                                    context,
                                    "Не нашёл приложение, чтобы включить трек",
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                        }
                    }
                }
            } else {
                Spacer(modifier = Modifier.height(24.dp))
                Text(
                    "За этот период прослушиваний нет. Послушай музыку: плейлист появится здесь.",
                    color = cs.onSurfaceVariant
                )
            }
        }
    }
}