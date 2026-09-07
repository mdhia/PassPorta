package org.shadowgrove.passporta

import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import org.shadowgrove.passporta.ui.toUserMessage

/**
 * Invisible gateway for incoming passes.
 *
 * Intercepts `.pkpass` files (VIEW/SEND) as well as "Add to Google Wallet" links, imports them
 * fully offline and briefly reports the result back.
 *
 * [AppCompatActivity] instead of a plain `ComponentActivity` so the toast text respects a
 * language chosen in the settings, not just the device's system language.
 */
class ImportActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Don't re-import on configuration changes.
        if (savedInstanceState != null) {
            finish()
            return
        }

        lifecycleScope.launch {
            val application = application as PassPortaApplication
            val result = application.passImporter.importFromIntent(intent)
            Toast.makeText(
                this@ImportActivity,
                result.toUserMessage(resources),
                Toast.LENGTH_LONG,
            ).show()
            finish()
        }
    }
}