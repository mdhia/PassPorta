package org.shadowgrove.passporta

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
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

    /**
     * PDF staged by [ImportActivity], waiting to be shown in the preview screen. Hoisted here
     * (not in a ViewModel) because it comes from the *Intent*, not from app state, and must
     * survive exactly one navigation.
     */
    private var pendingPdfUri by mutableStateOf<Uri?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        pendingPdfUri = intent.pdfPreviewUri()

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
                PassPortaApp(
                    pendingPdfUri = pendingPdfUri,
                    onPdfUriConsumed = { pendingPdfUri = null },
                )
            }
        }
    }

    /**
     * Called when the activity already exists and is reused (e.g. `FLAG_ACTIVITY_CLEAR_TOP`
     * from [ImportActivity]) instead of being recreated.
     */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        intent.pdfPreviewUri()?.let { pendingPdfUri = it }
    }
}

