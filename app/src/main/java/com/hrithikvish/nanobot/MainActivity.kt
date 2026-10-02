package com.hrithikvish.nanobot

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.hrithikvish.nanobot.ui.appfunctions.AppFunctionsScreen
import com.hrithikvish.nanobot.ui.chat.ChatScreen
import com.hrithikvish.nanobot.ui.theme.NanoBotTheme
import dagger.hilt.android.AndroidEntryPoint

object Destinations {
    const val CHAT = "chat"
    const val APP_FUNCTIONS = "app_functions"
}

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            NanoBotTheme {
                val navController = rememberNavController()
                NavHost(
                    navController = navController,
                    startDestination = Destinations.CHAT,
                    enterTransition = { slideInHorizontally { width -> width } + fadeIn() },
                    exitTransition = { slideOutHorizontally { width -> -width / 3 } + fadeOut() },
                    popEnterTransition = { slideInHorizontally { width -> -width / 3 } + fadeIn() },
                    popExitTransition = { slideOutHorizontally { width -> width } + fadeOut() },
                ) {
                    composable(Destinations.CHAT) {
                        ChatScreen(
                            instrumentationError = intent.getStringExtra(EXTRA_INSTRUMENTATION_ERROR),
                            onNavigateToAppFunctions = {
                                navController.navigate(Destinations.APP_FUNCTIONS)
                            },
                        )
                    }
                    composable(Destinations.APP_FUNCTIONS) {
                        AppFunctionsScreen(
                            onNavigateBack = {
                                navController.popBackStack()
                            },
                        )
                    }
                }
            }
        }
    }

    companion object {
        const val EXTRA_INSTRUMENTATION_ERROR = "EXTRA_INSTRUMENTATION_ERROR"
    }
}
