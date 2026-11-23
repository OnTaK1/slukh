package com.ontk.musicstats

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.imePadding

@Composable
private fun NavIcon(kind: Int, color: Color) {
    Canvas(modifier = Modifier.size(24.dp)) {
        val w = size.width
        val h = size.height
        val stroke = w * 0.09f
        when (kind) {
            0 -> { // главная: кольцо с точкой
                drawCircle(color, radius = w * 0.42f, style = Stroke(width = stroke))
                drawCircle(color, radius = w * 0.16f)
            }
            1 -> { // итоги: столбики
                val bw = w * 0.16f
                drawRoundRect(color, Offset(w * 0.14f, h * 0.5f), Size(bw, h * 0.36f), CornerRadius(bw / 2))
                drawRoundRect(color, Offset(w * 0.42f, h * 0.2f), Size(bw, h * 0.66f), CornerRadius(bw / 2))
                drawRoundRect(color, Offset(w * 0.70f, h * 0.36f), Size(bw, h * 0.5f), CornerRadius(bw / 2))
            }
            4 -> { // музыка: нота
                drawCircle(color, radius = w * 0.17f, center = Offset(w * 0.34f, h * 0.74f))
                drawLine(color, Offset(w * 0.5f, h * 0.74f), Offset(w * 0.5f, h * 0.18f), strokeWidth = stroke, cap = StrokeCap.Round)
                drawLine(color, Offset(w * 0.5f, h * 0.18f), Offset(w * 0.82f, h * 0.3f), strokeWidth = stroke, cap = StrokeCap.Round)
            }
            3 -> { // друзья: два пересекающихся кольца
                val r = w * 0.27f
                drawCircle(color, radius = r, center = Offset(w * 0.36f, h * 0.5f), style = Stroke(width = stroke))
                drawCircle(color, radius = r, center = Offset(w * 0.64f, h * 0.5f), style = Stroke(width = stroke))
            }
            else -> { // настройки: ползунки
                for (i in 0..2) {
                    val y = h * (0.25f + 0.25f * i)
                    drawLine(
                        color,
                        Offset(w * 0.12f, y),
                        Offset(w * 0.88f, y),
                        strokeWidth = stroke,
                        cap = StrokeCap.Round
                    )
                    drawCircle(
                        color,
                        radius = w * 0.1f,
                        center = Offset(w * (0.3f + 0.2f * ((i * 2) % 3)), y)
                    )
                }
            }
        }
    }
}

@Composable
private fun RowScope.NavItem(selected: Boolean, label: String, kind: Int, onClick: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    NavigationBarItem(
        selected = selected,
        onClick = onClick,
        icon = { NavIcon(kind, if (selected) cs.onPrimary else cs.onSurfaceVariant) },
        label = { Text(label) },
        colors = NavigationBarItemDefaults.colors(
            selectedIconColor = cs.onPrimary,
            selectedTextColor = cs.onBackground,
            unselectedIconColor = cs.onSurfaceVariant,
            unselectedTextColor = cs.onSurfaceVariant,
            indicatorColor = cs.primary
        )
    )
}

@Composable
fun MainScreen() {
    val context = LocalContext.current
    val cs = MaterialTheme.colorScheme
    val db = remember { StatsDb.get(context) }
    var tab by rememberSaveable { mutableIntStateOf(0) }
    val now = rememberNowInfo(db)

    LaunchedEffect(Unit) {
        GenreResolver.requestMissing(context)
        Maintenance.onAppStart(context)
    }

    // приветствие для новых пользователей
    if (!AppSettings.onboarded) {
        OnboardingScreen { AppSettings.setOnboarded(context, true) }
        return
    }

    // истории итогов поверх всего
    val storiesIndex = Overlay.storiesPeriod
    if (storiesIndex != null) {
        StoriesScreen(Period.entries[storiesIndex]) { Overlay.storiesPeriod = null }
        return
    }

    // плейлист периода
    val playlistIndex = Overlay.playlistPeriod
    if (playlistIndex != null) {
        PlaylistScreen(Period.entries[playlistIndex]) { Overlay.playlistPeriod = null }
        return
    }

    Scaffold(
        containerColor = cs.background,
        contentColor = cs.onBackground,
        bottomBar = {
            Column {
                if (tab != 0 && tab != 4 && now != null) MiniPlayer(now)
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(1.dp)
                        .background(cs.outline)
                )
                NavigationBar(
                    containerColor = cs.background,
                    contentColor = cs.onBackground,
                    tonalElevation = 0.dp
                ) {
                    NavItem(tab == 0, "Главная", 0) { tab = 0 }
                    NavItem(tab == 1, "Итоги", 1) { tab = 1 }
                    NavItem(tab == 4, "Музыка", 4) { tab = 4 }
                    NavItem(tab == 3, "Друзья", 3) { tab = 3 }
                    NavItem(tab == 2, "Настройки", 2) { tab = 2 }
                }
            }
        }
    ) { inner ->
        Box(modifier = Modifier.padding(inner).fillMaxSize().imePadding()) {
            when (tab) {
                0 -> HomeScreen()
                1 -> RecapScreen()
                3 -> FriendsScreen()
                4 -> LibraryScreen()
                else -> SettingsScreen()
            }
        }
    }
}