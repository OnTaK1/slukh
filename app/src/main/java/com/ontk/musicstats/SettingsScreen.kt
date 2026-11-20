package com.ontk.musicstats

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

// Сетка своих цветов: белый, серый и 12 оттенков в трёх тонах
private val CUSTOM_SWATCHES: List<Int> = buildList {
    add(0xFFF5F5F5.toInt())
    add(0xFF9A9A9A.toInt())
    val tones = listOf(0.85f to 0.95f, 0.80f to 0.70f, 0.45f to 0.95f)
    for ((saturation, value) in tones) {
        for (hue in 0 until 360 step 30) {
            add(Color.hsv(hue.toFloat(), saturation, value).toArgb())
        }
    }
}

@Composable
private fun CardTitle(text: String) {
    MonoLabel(text)
    Spacer(modifier = Modifier.height(12.dp))
}

@Composable
private fun Hint(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
}

@Composable
private fun PreviewHalf(bg: Color, accent: Color, ink: Color, modifier: Modifier) {
    Box(
        modifier = modifier.fillMaxHeight().background(bg),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(modifier = Modifier.width(34.dp).height(10.dp).background(accent))
            Spacer(modifier = Modifier.height(5.dp))
            Box(modifier = Modifier.width(22.dp).height(3.dp).background(ink.copy(alpha = 0.6f)))
        }
    }
}

/** Карточка готовой палитры: слева тёмный вариант, справа светлый. */
@Composable
private fun PresetCard(
    palette: Palette,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val cs = MaterialTheme.colorScheme
    val shape = RoundedCornerShape(10.dp)
    Column(
        modifier = modifier
            .clip(shape)
            .border(if (selected) 2.dp else 1.dp, if (selected) cs.primary else cs.outline, shape)
            .clickable(onClick = onClick)
    ) {
        Row(modifier = Modifier.fillMaxWidth().height(64.dp)) {
            PreviewHalf(palette.darkBg, palette.accent, Color.White, Modifier.weight(1f))
            PreviewHalf(palette.lightBg, palette.accent, Color(0xFF0A0A0A), Modifier.weight(1f))
        }
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                palette.name,
                modifier = Modifier.weight(1f),
                fontWeight = if (selected) FontWeight.Black else FontWeight.SemiBold
            )
            if (selected) Text("✓", color = cs.primary, fontWeight = FontWeight.Black)
        }
    }
}

@Composable
private fun Swatch(argb: Int, selected: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    Box(
        modifier = modifier
            .aspectRatio(1f)
            .clip(CircleShape)
            .background(Color(argb))
            .border(
                if (selected) 3.dp else 1.dp,
                if (selected) cs.onBackground else cs.outline,
                CircleShape
            )
            .clickable(onClick = onClick)
    )
}

@Composable
fun SettingsScreen() {
    val context = LocalContext.current
    val cs = MaterialTheme.colorScheme
    val db = remember { StatsDb.get(context) }
    var tick by remember { mutableIntStateOf(0) }
    var confirmClear by remember { mutableStateOf(false) }
    val version = NowPlaying.version
    val apps = remember(version, tick) { db.getApps() }

    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            title = { Text("Удалить всю историю?") },
            text = {
                Text(
                    "Будут удалены все прослушивания и итоги. Вернуть их будет нельзя. " +
                            "Жанры исполнителей сохранятся."
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    db.clearPlays()
                    NowPlaying.version++
                    confirmClear = false
                }) { Text("Удалить историю") }
            },
            dismissButton = {
                TextButton(onClick = { confirmClear = false }) { Text("Отмена") }
            }
        )
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Text(
                "Настройки",
                style = MaterialTheme.typography.headlineLarge,
                fontWeight = FontWeight.Black
            )
        }

        item { KeySetupCard() }

        item { BackupPanel() }
        item { UpdatePanel() }
        item {
            Panel {
                CardTitle("тема")
                Pills(listOf("Авто", "Светлая", "Тёмная"), AppSettings.themeMode) {
                    AppSettings.setTheme(context, it)
                }
                Spacer(modifier = Modifier.height(10.dp))
                Hint("Авто — как на устройстве.")
            }
        }

        item {
            Panel {
                CardTitle("цветовая схема")
                PALETTES.chunked(2).forEach { pair ->
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(bottom = 10.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        pair.forEach { p ->
                            PresetCard(p, AppSettings.paletteId == p.id, Modifier.weight(1f)) {
                                AppSettings.setPalette(context, p.id)
                            }
                        }
                    }
                }
                Spacer(modifier = Modifier.height(6.dp))
                MonoLabel("свой цвет акцента")
                Spacer(modifier = Modifier.height(10.dp))
                CUSTOM_SWATCHES.chunked(8).forEach { row ->
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        row.forEach { argb ->
                            Swatch(
                                argb = argb,
                                selected = AppSettings.paletteId == 4 &&
                                        AppSettings.customColor == argb,
                                modifier = Modifier.weight(1f)
                            ) {
                                AppSettings.setCustomColor(context, argb)
                            }
                        }
                        repeat(8 - row.size) { Spacer(modifier = Modifier.weight(1f)) }
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
                Hint("Нажми на цвет, чтобы использовать его как акцент.")
            }
        }

        item {
            Panel {
                CardTitle("шрифт")
                APP_FONTS.forEach { f ->
                    val selected = f.id == AppSettings.fontId
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .clickable { AppSettings.setFont(context, f.id) }
                            .padding(vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "${f.label} — Музыка играет",
                            style = MaterialTheme.typography.titleMedium.copy(fontFamily = f.family),
                            modifier = Modifier.weight(1f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        if (selected) {
                            Text(
                                "✓",
                                style = MaterialTheme.typography.titleMedium,
                                color = cs.primary
                            )
                        }
                    }
                }
            }
        }

        item {
            Panel {
                CardTitle("что считать музыкой")
                if (apps.isEmpty()) {
                    Hint("Список появится после первого воспроизведения.")
                }
                apps.forEach { app ->
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(sourceName(app.packageName), fontWeight = FontWeight.SemiBold)
                            Hint(app.packageName)
                        }
                        Switch(
                            checked = app.enabled,
                            onCheckedChange = {
                                db.setAppEnabled(app.packageName, it)
                                tick++
                            }
                        )
                    }
                }
            }
        }

        item {
            Panel {
                CardTitle("о приложении")
                Text("Как считаются прослушивания", fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(2.dp))
                Hint(
                    "Трек засчитывается, когда сыграна половина или 4 минуты (что наступит раньше). " +
                            "Паузы не учитываются. Голосовые сообщения, видео и записи короче 30 секунд " +
                            "или длиннее 15 минут не считаются музыкой."
                )
                Spacer(modifier = Modifier.height(14.dp))
                Text("Конфиденциальность", fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(2.dp))
                Hint(
                    "История прослушиваний хранится только на этом устройстве. " +
                            "Для определения жанра в Last.fm отправляется лишь имя исполнителя: " +
                            "названия треков и история не передаются."
                )
                Spacer(modifier = Modifier.height(14.dp))
                Text("Источник жанров", fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(2.dp))
                Hint("Жанры определяются по пользовательским тегам Last.fm.")
                Spacer(modifier = Modifier.height(12.dp))
                Button(
                    onClick = {
                        context.startActivity(
                            Intent(Intent.ACTION_VIEW, Uri.parse("https://www.last.fm"))
                        )
                    },
                    shape = PillShape
                ) {
                    Text("Открыть Last.fm")
                }
            }
        }

        item {
            Panel {
                CardTitle("данные")
                Hint("Всё хранится только на этом устройстве.")
                Spacer(modifier = Modifier.height(12.dp))
                OutlinedButton(
                    onClick = { confirmClear = true },
                    shape = PillShape,
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = cs.error)
                ) {
                    Text("Удалить всю историю")
                }
            }
        }
    }
}