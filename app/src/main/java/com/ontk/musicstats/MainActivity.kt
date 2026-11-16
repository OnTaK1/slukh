package com.ontk.musicstats

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        AppSettings.load(this)
        setContent {
            AppTheme { MainScreen() }
        }
    }

    override fun onResume() {
        super.onResume()
        NowPlaying.resumeTick++
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        // читать буфер обмена можно только когда окно в фокусе
        if (hasFocus) KeySetup.checkClipboard(this)
    }
}