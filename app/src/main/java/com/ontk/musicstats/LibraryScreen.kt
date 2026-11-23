package com.ontk.musicstats

import android.Manifest
import android.content.Context
import android.content.Intent
import android.os.Build
import android.content.pm.PackageManager
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import kotlinx.coroutines.delay

private enum class Glyph { PLAY, PAUSE, NEXT, PREV }

private fun clock(ms: Long): String {
    val total = (ms / 1000).coerceAtLeast(0)
    return "%d:%02d".format(total / 60, total % 60)
}

@Composable
private fun TransportButton(glyph: Glyph, filled: Boolean = false, onClick: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    val fg = if (filled) cs.onPrimary else cs.onSurface
    Box(
        modifier = Modifier
            .size(46.dp)
            .clip(PillShape)
            .background(if (filled) cs.primary else Color.Transparent)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.size(18.dp)) {
            val w = size.width
            val h = size.height
            when (glyph) {
                Glyph.PLAY -> drawPath(
                    Path().apply {
                        moveTo(w * 0.15f, 0f); lineTo(w, h / 2f); lineTo(w * 0.15f, h); close()
                    }, fg
                )
                Glyph.PAUSE -> {
                    drawRect(fg, Offset(w * 0.1f, 0f), Size(w * 0.28f, h))
                    drawRect(fg, Offset(w * 0.62f, 0f), Size(w * 0.28f, h))
                }
                Glyph.NEXT -> {
                    drawPath(
                        Path().apply {
                            moveTo(0f, 0f); lineTo(w * 0.72f, h / 2f); lineTo(0f, h); close()
                        }, fg
                    )
                    drawRect(fg, Offset(w * 0.8f, 0f), Size(w * 0.2f, h))
                }
                Glyph.PREV -> {
                    drawPath(
                        Path().apply {
                            moveTo(w, 0f); lineTo(w * 0.28f, h / 2f); lineTo(w, h); close()
                        }, fg
                    )
                    drawRect(fg, Offset(0f, 0f), Size(w * 0.2f, h))
                }
            }
        }
    }
}

/** Нижняя панель встроенного плеера. */
@Composable
private fun PlayerBar() {
    val cs = MaterialTheme.colorScheme
    var position by remember { mutableStateOf(0L) }
    var seeking by remember { mutableStateOf(false) }
    var seekValue by remember { mutableFloatStateOf(0f) }

    LaunchedEffect(PlayerController.hasMedia) {
        while (true) {
            if (!seeking) position = PlayerController.positionMs()
            delay(500)
        }
    }

    val duration = PlayerController.durationMs
    val range = maxOf(1f, duration.toFloat())
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(cs.surface)
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    PlayerController.title.ifEmpty { "Без названия" },
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    PlayerController.artist,
                    style = MaterialTheme.typography.bodySmall,
                    color = cs.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            TransportButton(Glyph.PREV) { PlayerController.previous() }
            TransportButton(if (PlayerController.isPlaying) Glyph.PAUSE else Glyph.PLAY, filled = true) {
                PlayerController.toggle()
            }
            TransportButton(Glyph.NEXT) { PlayerController.next() }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                clock(if (seeking) seekValue.toLong() else position),
                fontFamily = FontFamily.Monospace,
                style = MaterialTheme.typography.labelSmall,
                color = cs.onSurfaceVariant
            )
            Slider(
                value = (if (seeking) seekValue else position.toFloat()).coerceIn(0f, range),
                onValueChange = {
                    seeking = true
                    seekValue = it
                },
                onValueChangeFinished = {
                    PlayerController.seekTo(seekValue.toLong())
                    position = seekValue.toLong()
                    seeking = false
                },
                valueRange = 0f..range,
                modifier = Modifier.weight(1f).padding(horizontal = 8.dp)
            )
            Text(
                clock(duration),
                fontFamily = FontFamily.Monospace,
                style = MaterialTheme.typography.labelSmall,
                color = cs.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun TrackRow(track: LibraryTrack, onClick: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
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
        }
        Spacer(modifier = Modifier.width(8.dp))
        if (track.plays > 0) {
            Text(
                "×${track.plays}",
                fontFamily = FontFamily.Monospace,
                style = MaterialTheme.typography.bodySmall,
                color = cs.primary
            )
            Spacer(modifier = Modifier.width(8.dp))
        }
        if (track.durationMs > 0) {
            Text(
                clock(track.durationMs),
                fontFamily = FontFamily.Monospace,
                style = MaterialTheme.typography.bodySmall,
                color = cs.onSurfaceVariant
            )
        }
    }
}

private fun needsNotificationPermission(context: Context): Boolean =
    Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED

@Composable
fun LibraryScreen() {
    val context = LocalContext.current
    val cs = MaterialTheme.colorScheme
    val db = remember { StatsDb.get(context) }
    val version = NowPlaying.version
    var tick by remember { mutableIntStateOf(0) }
    var query by rememberSaveable { mutableStateOf("") }
    var mode by rememberSaveable { mutableIntStateOf(0) } // 0 все, 1 часто, 2 недавно, 3 любимое
    var message by remember { mutableStateOf<String?>(null) }

    val count = remember(version, tick) { db.libraryCount() }
    val tracks = remember(version, tick, query, mode) {
        if (mode == 3) emptyList() else {
            db.libraryTracks(
                query,
                when (mode) {
                    1 -> LibrarySort.FREQUENT
                    2 -> LibrarySort.RECENT
                    else -> LibrarySort.ALL
                }
            )
        }
    }
    val favs = remember(version, tick, mode) { if (mode == 3) db.favorites() else emptyList() }

    LaunchedEffect(Unit) { PlayerController.connect(context) }

    val notificationLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    val audioLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) LibraryScanner.scan(context) else message = "Без доступа к файлам нельзя найти музыку на телефоне."
    }
    val treeLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) {
            try {
                context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
                AppSettings.addLibraryTree(context, uri.toString())
                LibraryScanner.scan(context)
            } catch (e: SecurityException) {
                message = "Не удалось получить доступ к папке."
            }
        }
    }

    fun play(list: List<LibraryTrack>, index: Int) {
        if (needsNotificationPermission(context)) {
            notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
        PlayerController.playQueue(context, list, index)
    }

    fun scanDevice() {
        if (LibraryScanner.hasPermission(context)) LibraryScanner.scan(context)
        else audioLauncher.launch(LibraryScanner.audioPermission())
    }

    Column(modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Text(
                    "Музыка",
                    style = MaterialTheme.typography.headlineLarge,
                    fontWeight = FontWeight.Black
                )
            }

            if (count == 0 && !LibraryScanner.scanning) {
                item {
                    Panel {
                        MonoLabel("своя музыка")
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            "Здесь играют твои файлы",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Black
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            "Добавь музыку с телефона или папку (в том числе из облака, например " +
                                    "Google Диска). Slukh ничего не копирует: он просто проигрывает файлы, " +
                                    "а прослушивания попадают в статистику.",
                            style = MaterialTheme.typography.bodySmall,
                            color = cs.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(onClick = { scanDevice() }, shape = PillShape) { Text("Музыка на телефоне") }
                            OutlinedButton(onClick = { treeLauncher.launch(null) }, shape = PillShape) {
                                Text("Папка")
                            }
                        }
                    }
                }
            } else {
                item {
                    OutlinedTextField(
                        value = query,
                        onValueChange = { query = it },
                        label = { Text("Поиск по библиотеке") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                item {
                    Pills(listOf("Все", "Часто", "Недавно", "Любимое"), mode) { mode = it }
                }
                if (mode != 3 && tracks.isNotEmpty()) {
                    item {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                            Button(onClick = { play(tracks, 0) }, shape = PillShape) { Text("Играть") }
                            OutlinedButton(onClick = { play(tracks.shuffled(), 0) }, shape = PillShape) {
                                Text("Перемешать")
                            }
                            Text(
                                "${tracks.size}",
                                fontFamily = FontFamily.Monospace,
                                color = cs.onSurfaceVariant
                            )
                        }
                    }
                }
                if (mode == 3) {
                    if (favs.isEmpty()) {
                        item {
                            Text(
                                "Пока пусто. Добавляй треки в любимое из подсказок на главном экране.",
                                color = cs.onSurfaceVariant
                            )
                        }
                    }
                    itemsIndexed(favs) { _, fav ->
                        val local = remember(fav.trackKey, version) { db.findLocal(fav.artists, fav.title) }
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    fav.title,
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    fav.artists + if (local != null) " · есть файл" else "",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = cs.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            PlayButton {
                                if (local != null) {
                                    play(listOf(local), 0)
                                } else if (!PlayerLauncher.play(context, fav.artists, fav.title, fav.packageName)) {
                                    Toast.makeText(context, "Не нашёл приложение, чтобы включить", Toast.LENGTH_SHORT).show()
                                }
                            }
                            TextButton(onClick = {
                                db.removeFavorite(fav.trackKey)
                                tick++
                            }) { Text("Убрать") }
                        }
                    }
                } else {
                    itemsIndexed(tracks) { index, track ->
                        TrackRow(track) { play(tracks, index) }
                    }
                }
            }

            item {
                Panel {
                    MonoLabel("источники")
                    Spacer(modifier = Modifier.height(10.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = { scanDevice() }, shape = PillShape, enabled = !LibraryScanner.scanning) {
                            Text("Обновить")
                        }
                        OutlinedButton(onClick = { treeLauncher.launch(null) }, shape = PillShape) {
                            Text("Добавить папку")
                        }
                    }
                    if (LibraryScanner.scanning) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            "Сканирую… найдено ${LibraryScanner.found}",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                    (LibraryScanner.message ?: message)?.let {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(it, style = MaterialTheme.typography.bodySmall)
                    }
                    AppSettings.libraryTrees.forEach { tree ->
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                Uri.parse(tree).lastPathSegment.orEmpty(),
                                modifier = Modifier.weight(1f),
                                style = MaterialTheme.typography.bodySmall,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            TextButton(onClick = {
                                db.removeLibrarySource("tree:$tree")
                                AppSettings.removeLibraryTree(context, tree)
                                tick++
                            }) { Text("Убрать") }
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        "Файлы из облака читаются по мере воспроизведения, поэтому первое сканирование " +
                                "большой облачной папки может быть долгим.",
                        style = MaterialTheme.typography.bodySmall,
                        color = cs.onSurfaceVariant
                    )
                }
            }
        }
        if (PlayerController.hasMedia) PlayerBar()
    }
}