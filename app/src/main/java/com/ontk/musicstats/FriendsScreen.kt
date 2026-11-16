package com.ontk.musicstats

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private fun toast(context: Context, text: String) =
    Toast.makeText(context, text, Toast.LENGTH_SHORT).show()

private fun shareCode(context: Context, text: String) {
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_TEXT, text)
    }
    context.startActivity(Intent.createChooser(intent, "Отправить профиль"))
}

private fun copyCode(context: Context, text: String) {
    val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    cm.setPrimaryClip(ClipData.newPlainText("Slukh", text))
    toast(context, "Профиль скопирован")
}

@Composable
fun FriendsScreen() {
    val context = LocalContext.current
    val db = remember { StatsDb.get(context) }
    var tick by remember { mutableIntStateOf(0) }
    var selectedId by rememberSaveable { mutableStateOf<String?>(null) }
    var periodIdx by rememberSaveable { mutableIntStateOf(Period.MONTH.ordinal) }
    var withTracks by rememberSaveable { mutableStateOf(true) }
    var input by remember { mutableStateOf("") }
    var message by remember { mutableStateOf<String?>(null) }
    val friends = remember(tick) { db.friends() }
    val matches = remember(friends) { friends.associate { it.id to Compare.run(db, it).percent } }
    val fmt = remember { SimpleDateFormat("dd.MM.yyyy", Locale.getDefault()) }

    val selected = friends.firstOrNull { it.id == selectedId }
    if (selected != null) {
        FriendDetail(selected, onBack = { selectedId = null }) {
            db.deleteFriend(selected.id)
            selectedId = null
            tick++
        }
        return
    }

    fun makeMessage(): String? {
        val name = AppSettings.profileName.trim()
        if (name.isEmpty()) {
            toast(context, "Сначала впиши свой ник")
            return null
        }
        val snap = Profile.build(db, Period.entries[periodIdx], name, AppSettings.profileId, withTracks)
        if (snap.plays == 0) {
            toast(context, "За этот период нет прослушиваний")
            return null
        }
        return FriendCodec.message(snap)
    }

    fun addFriend() {
        val snap = FriendCodec.decode(input)
        when {
            snap == null -> message = "Не нашёл код. Он начинается с SLUKH1:"
            snap.id == AppSettings.profileId -> message = "Это твой собственный код."
            else -> {
                db.saveFriend(snap)
                input = ""
                message = "Добавлен: ${snap.name}"
                tick++
            }
        }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Text(
                "Друзья",
                style = MaterialTheme.typography.headlineLarge,
                fontWeight = FontWeight.Black
            )
        }

        item {
            Panel {
                MonoLabel("мой профиль")
                Spacer(modifier = Modifier.height(10.dp))
                OutlinedTextField(
                    value = AppSettings.profileName,
                    onValueChange = { AppSettings.setProfileName(context, it) },
                    label = { Text("Твой ник") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(14.dp))
                MonoLabel("период для друга")
                Spacer(modifier = Modifier.height(8.dp))
                Pills(Period.entries.map { it.label }, periodIdx) { periodIdx = it }
                Spacer(modifier = Modifier.height(10.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Switch(checked = withTracks, onCheckedChange = { withTracks = it })
                    Spacer(modifier = Modifier.width(10.dp))
                    Text("Добавить топ треков")
                }
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    "Друг получит только итоги периода: исполнителей, жанры и часы. " +
                            "Время отдельных прослушиваний не передаётся.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = { makeMessage()?.let { shareCode(context, it) } },
                        shape = PillShape
                    ) { Text("Поделиться") }
                    OutlinedButton(
                        onClick = { makeMessage()?.let { copyCode(context, it) } },
                        shape = PillShape
                    ) { Text("Копировать") }
                }
            }
        }

        item {
            Panel {
                MonoLabel("добавить друга")
                Spacer(modifier = Modifier.height(10.dp))
                OutlinedTextField(
                    value = input,
                    onValueChange = {
                        input = it
                        message = null
                    },
                    label = { Text("Код друга (SLUKH1:…)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = {
                            val text = LastFmKey.readClipboard(context)
                            if (FriendCodec.decode(text) != null) {
                                input = text.orEmpty()
                                message = null
                            } else {
                                message = "В буфере обмена нет кода Slukh."
                            }
                        },
                        shape = PillShape
                    ) { Text("Вставить") }
                    Button(
                        onClick = { addFriend() },
                        enabled = input.isNotBlank(),
                        shape = PillShape
                    ) { Text("Добавить") }
                }
                message?.let {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(it, style = MaterialTheme.typography.bodySmall)
                }
            }
        }

        if (friends.isEmpty()) {
            item {
                Text(
                    "Пока никого. Отправь свой профиль другу или вставь его код сюда, " +
                            "и Slukh сравнит ваш вкус.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            item { MonoLabel("друзья") }
            items(friends, key = { it.id }) { f ->
                FriendRow(f, matches[f.id], fmt.format(Date(f.createdAt))) { selectedId = f.id }
            }
        }
    }
}

@Composable
private fun FriendRow(f: Snapshot, percent: Int?, date: String, onClick: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    Panel(modifier = Modifier.clip(CardShape).clickable(onClick = onClick)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            ArtistMark(f.name)
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    f.name,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    "${f.label} · $date",
                    style = MaterialTheme.typography.bodySmall,
                    color = cs.onSurfaceVariant
                )
            }
            if (percent != null) {
                Text(
                    "$percent%",
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Black,
                    style = MaterialTheme.typography.titleLarge,
                    color = cs.primary
                )
            }
        }
    }
}

@Composable
private fun FriendDetail(f: Snapshot, onBack: () -> Unit, onDelete: () -> Unit) {
    val context = LocalContext.current
    val cs = MaterialTheme.colorScheme
    val db = remember { StatsDb.get(context) }
    val cmp = remember(f) { Compare.run(db, f) }
    val target = remember { AppSettings.playlistPlayer.ifEmpty { db.mostUsedPackage() } }
    BackHandler(onBack = onBack)

    fun play(artists: String, title: String) {
        if (!PlayerLauncher.play(context, artists, title, target)) {
            toast(context, "Не нашёл приложение, чтобы включить")
        }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Column {
                TextButton(onClick = onBack) { Text("← назад") }
                MonoLabel("сравнение · ${f.label}")
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    f.name,
                    style = MaterialTheme.typography.headlineLarge,
                    fontWeight = FontWeight.Black
                )
            }
        }

        item {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = CardShape,
                color = cs.primary,
                contentColor = cs.onPrimary
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    MonoLabel("совпадение вкусов", color = cs.onPrimary.copy(alpha = 0.8f))
                    Spacer(modifier = Modifier.height(8.dp))
                    if (cmp.mineEmpty) {
                        Text("В этот период у тебя нет прослушиваний, сравнивать не с чем.")
                    } else {
                        Text(
                            "${cmp.percent}%",
                            fontSize = 72.sp,
                            fontWeight = FontWeight.Black
                        )
                        Text(
                            Compare.word(cmp.percent),
                            style = MaterialTheme.typography.titleMedium
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            "исполнители ${cmp.artistPct}% · жанры ${cmp.genrePct}%",
                            fontFamily = FontFamily.Monospace,
                            style = MaterialTheme.typography.bodySmall,
                            color = cs.onPrimary.copy(alpha = 0.85f)
                        )
                    }
                }
            }
        }

        item {
            Panel {
                MonoLabel("часов музыки за период")
                Spacer(modifier = Modifier.height(10.dp))
                Row(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.weight(1f)) {
                        MonoLabel("ты")
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            formatDuration(cmp.mineMs),
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Black
                        )
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        MonoLabel(f.name)
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            formatDuration(f.totalMs),
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Black
                        )
                    }
                }
            }
        }

        if (cmp.common.isNotEmpty()) {
            item {
                Panel {
                    MonoLabel("общие исполнители · ты / ${f.name}")
                    Spacer(modifier = Modifier.height(8.dp))
                    cmp.common.forEach { (name, mine, theirs) ->
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 5.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                name,
                                modifier = Modifier.weight(1f),
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                "×$mine / ×$theirs",
                                fontFamily = FontFamily.Monospace,
                                style = MaterialTheme.typography.bodySmall,
                                color = cs.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }

        if (cmp.commonGenres.isNotEmpty()) {
            item {
                Panel {
                    MonoLabel("общие жанры")
                    Spacer(modifier = Modifier.height(10.dp))
                    Row(
                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        cmp.commonGenres.forEach { GenreChip(it) }
                    }
                }
            }
        }

        if (cmp.fresh.isNotEmpty()) {
            item {
                Panel {
                    MonoLabel("новое для тебя")
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        "Исполнители из топа друга, которых ты ещё не слушал.",
                        style = MaterialTheme.typography.bodySmall,
                        color = cs.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    cmp.fresh.forEach { artist ->
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 5.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                artist,
                                modifier = Modifier.weight(1f),
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            PlayButton { play(artist, "") }
                        }
                    }
                }
            }
        }

        if (f.topTracks.isNotEmpty()) {
            item {
                Panel {
                    MonoLabel("топ треков ${f.name}")
                    Spacer(modifier = Modifier.height(8.dp))
                    f.topTracks.take(5).forEach { (artists, title, count) ->
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 5.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    title,
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    "$artists · ×$count",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = cs.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            Spacer(modifier = Modifier.width(10.dp))
                            PlayButton { play(artists, title) }
                        }
                    }
                }
            }
        }

        item {
            Column {
                Text(
                    "Чтобы обновить данные, попроси друга прислать новый код: он заменит старый.",
                    style = MaterialTheme.typography.bodySmall,
                    color = cs.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(12.dp))
                OutlinedButton(
                    onClick = onDelete,
                    shape = PillShape,
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = cs.error)
                ) { Text("Удалить друга") }
            }
        }
    }
}