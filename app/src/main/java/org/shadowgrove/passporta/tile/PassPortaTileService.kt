package org.shadowgrove.passporta.tile

import android.app.PendingIntent
import android.content.Intent
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import org.shadowgrove.passporta.MainActivity

/**
 * Quick Settings tile that simply opens PassPorta. The tile has no on/off state of its own -
 * it is a shortcut, always shown as inactive, and every tap launches [MainActivity].
 *
 * From Android 14 (UPSIDE_DOWN_CAKE) onward, [TileService.startActivityAndCollapse] with a plain
 * [Intent] is deprecated/blocked for security reasons; a [PendingIntent] has to be used instead.
 * Both paths are handled here so the tile keeps working across all supported API levels.
 */
class PassPortaTileService : TileService() {

    override fun onStartListening() {
        super.onStartListening()
        qsTile?.apply {
            state = Tile.STATE_INACTIVE
            updateTile()
        }
    }

    override fun onClick() {
        super.onClick()

        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            val pendingIntent = PendingIntent.getActivity(
                this,
                0,
                intent,
                PendingIntent.FLAG_IMMUTABLE,
            )
            startActivityAndCollapse(pendingIntent)
        } else {
            @Suppress("DEPRECATION", "StartActivityAndCollapseDeprecated")
            startActivityAndCollapse(intent)
        }
    }
}

