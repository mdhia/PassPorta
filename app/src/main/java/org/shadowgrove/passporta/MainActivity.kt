package org.shadowgrove.passporta

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import org.shadowgrove.passporta.ui.PassPortaApp
import org.shadowgrove.passporta.ui.theme.PassPortaTheme

/**
 * The app's only screen activity. The entire UI is Jetpack Compose.
 *
 * Deliberately [AppCompatActivity] instead of a plain `ComponentActivity`: only AppCompatActivity
 * wraps its base context so that `AppCompatDelegate.setApplicationLocales()` can actually change
 * the displayed language on API 24-32 and automatically recreates the activity when it does.
 */
class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val settingsStore = (application as PassPortaApplication).settingsStore

        setContent {
            // Initial value directly from preferences: without it, the first frame would show
            // the default scheme and the theme would visibly switch right after.
            val settings by settingsStore.settings.collectAsState(initial = settingsStore.current)
            PassPortaTheme(
                themeColor = settings.themeColor,
                themeMode = settings.themeMode,
                dynamicColor = settings.useDynamicColor,
            ) {
                PassPortaApp()
            }
        }
    }
}

