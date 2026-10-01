package com.hrithikvish.nanobot

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.hrithikvish.nanobot.ui.chat.ChatScreen
import com.hrithikvish.nanobot.ui.theme.NanoBotTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            NanoBotTheme {
                ChatScreen(
                    instrumentationError = intent.getStringExtra(EXTRA_INSTRUMENTATION_ERROR),
                )
            }
        }
    }

    companion object {
        const val EXTRA_INSTRUMENTATION_ERROR = "EXTRA_INSTRUMENTATION_ERROR"
    }
}
