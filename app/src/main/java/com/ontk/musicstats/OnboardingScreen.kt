package com.ontk.musicstats

import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.NotificationManagerCompat
import kotlinx.coroutines.launch

@Composable
fun OnboardingScreen(onDone: () -> Unit) {
    val context = LocalContext.current
    val cs = MaterialTheme.colorScheme
    val pageCount = 4
    val pagerState = rememberPagerState(pageCount = { pageCount })
    val scope = rememberCoroutineScope()
    val hasAccess = remember(NowPlaying.resumeTick) {
        NotificationManagerCompat.getEnabledListenerPackages(context).contains(context.packageName)
    }
    val current = pagerState.currentPage
    val isLast = current == pageCount - 1

    Scaffold(containerColor = cs.background, contentColor = cs.onBackground) { inner ->
        Column(modifier = Modifier.padding(inner).fillMaxSize().imePadding()) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.End
            ) {
                TextButton(onClick = onDone) { Text("Пропустить") }
            }

            HorizontalPager(state = pagerState, modifier = Modifier.weight(1f)) { page ->
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 24.dp)
                ) {
                    when (page) {
                        0 -> PageIntro()
                        1 -> PageHow()
                        2 -> PageAccess(hasAccess) {
                            scope.launch { pagerState.animateScrollToPage(3) }
                        }
                        else -> PageGenres()
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth().padding(24.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Dots(current, pageCount)
                Spacer(modifier = Modifier.weight(1f))
                if (current > 0) {
                    TextButton(onClick = {
                        scope.launch { pagerState.animateScrollToPage(current - 1) }
                    }) { Text("Назад") }
                    Spacer(modifier = Modifier.width(8.dp))
                }
                Button(
                    onClick = {
                        if (isLast) onDone()
                        else scope.launch { pagerState.animateScrollToPage(current + 1) }
                    },
                    shape = PillShape
                ) { Text(if (isLast) "Начать" else "Далее") }
            }
        }
    }
}

@Composable
private fun Dots(current: Int, count: Int) {
    val cs = MaterialTheme.colorScheme
    Row(
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        repeat(count) { i ->
            Box(
                modifier = Modifier
                    .height(6.dp)
                    .width(if (i == current) 22.dp else 6.dp)
                    .background(if (i == current) cs.primary else cs.onBackground.copy(alpha = 0.2f))
            )
        }
    }
}

@Composable
private fun PageIntro() {
    Spacer(modifier = Modifier.height(24.dp))
    MonoLabel("slukh")
    Spacer(modifier = Modifier.height(16.dp))
    Text(
        "Музыка\nв цифрах.",
        fontSize = 60.sp,
        lineHeight = 62.sp,
        fontWeight = FontWeight.Black,
        letterSpacing = (-1).sp
    )
    Spacer(modifier = Modifier.height(24.dp))
    Text(
        "Приложение считает, что ты слушаешь, в любых плеерах: VK, YouTube Music, " +
                "Telegram, локальные файлы. Итоги по неделям, месяцам и годам, жанры, " +
                "настроение, истории и достижения.",
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
    Spacer(modifier = Modifier.height(16.dp))
    Text("Без аккаунтов и регистрации.", fontWeight = FontWeight.Bold)
}

@Composable
private fun PageHow() {
    Spacer(modifier = Modifier.height(24.dp))
    MonoLabel("как это работает")
    Spacer(modifier = Modifier.height(16.dp))
    Text(
        "Что делает приложение и чего не делает",
        style = MaterialTheme.typography.headlineMedium,
        fontWeight = FontWeight.Black
    )
    Spacer(modifier = Modifier.height(24.dp))
    HowRow("01", "Читает название трека, исполнителя и состояние плеера (играет или пауза).")
    HowRow("02", "Не читает тексты уведомлений и сообщения, не использует микрофон.")
    HowRow("03", "Хранит историю только на этом телефоне и никуда её не отправляет.")
    HowRow(
        "04",
        "В интернет уходят только имена исполнителей (в Last.fm), и только если ты подключишь жанры."
    )
}

@Composable
private fun HowRow(number: String, text: String) {
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp)) {
        Text(
            number,
            modifier = Modifier.width(40.dp),
            fontFamily = FontFamily.Monospace,
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.Black
        )
        Text(text, style = MaterialTheme.typography.bodyLarge)
    }
}

@Composable
private fun PageAccess(hasAccess: Boolean, onSkip: () -> Unit) {
    val context = LocalContext.current
    Spacer(modifier = Modifier.height(24.dp))
    MonoLabel("доступ к уведомлениям")
    Spacer(modifier = Modifier.height(16.dp))
    Text(
        "Нужно одно разрешение",
        style = MaterialTheme.typography.headlineMedium,
        fontWeight = FontWeight.Black
    )
    Spacer(modifier = Modifier.height(16.dp))
    Text(
        "Чтобы видеть, что сейчас играет, Android требует разрешение «Доступ к уведомлениям». " +
                "Slukh использует его только для одного: узнать название трека, исполнителя " +
                "и состояние плеера (играет или пауза).\n\n" +
                "Приложение не читает тексты уведомлений и сообщений, ничего не передаёт другим " +
                "приложениям и не отправляет историю на серверы. Отозвать доступ можно в любой " +
                "момент в настройках Android.",
        style = MaterialTheme.typography.bodyLarge
    )
    Spacer(modifier = Modifier.height(24.dp))
    if (hasAccess) {
        Text(
            "Доступ выдан ✓",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Black
        )
    } else {
        Button(
            onClick = {
                context.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
            },
            shape = PillShape
        ) { Text("Согласен, открыть настройки") }
        Spacer(modifier = Modifier.height(4.dp))
        TextButton(onClick = onSkip) { Text("Не сейчас") }
    }
}

@Composable
private fun PageGenres() {
    Spacer(modifier = Modifier.height(24.dp))
    MonoLabel("жанры")
    Spacer(modifier = Modifier.height(16.dp))
    Text(
        "Жанры — по желанию",
        style = MaterialTheme.typography.headlineMedium,
        fontWeight = FontWeight.Black
    )
    Spacer(modifier = Modifier.height(12.dp))
    Text(
        "Жанры и настроение берутся из тегов Last.fm. Для этого нужен твой личный " +
                "бесплатный ключ. Можно пропустить и подключить позже в настройках.",
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
    Spacer(modifier = Modifier.height(20.dp))
    KeySetupCard()
    Spacer(modifier = Modifier.height(24.dp))
}