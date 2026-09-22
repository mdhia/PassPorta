package org.shadowgrove.passporta.data.settings

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.Immutable
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.conflate
import org.shadowgrove.passporta.data.local.entity.PassEntity

private const val DEFAULT_ROLLING_BACKUP_COUNT = 5

/** Color mood of the frame surface. Passes always keep their own colors. */
enum class AppThemeColor(val key: String) {
    INDIGO("indigo"),
    TEAL("teal"),
    GREEN("green"),
    AMBER("amber"),
    ORANGE("orange"),
    RED("red"),
    VIOLET("violet"),
    SLATE("slate"),
    ;

    companion object {
        val DEFAULT = INDIGO

        fun fromKey(key: String?): AppThemeColor =
            entries.firstOrNull { it.key == key } ?: DEFAULT
    }
}

/** Choice between light and dark appearance. */
enum class AppThemeMode(val key: String) {
    SYSTEM("system"),
    LIGHT("light"),
    DARK("dark"),
    ;

    companion object {
        val DEFAULT = SYSTEM

        fun fromKey(key: String?): AppThemeMode =
            entries.firstOrNull { it.key == key } ?: DEFAULT
    }
}

@Immutable
data class AppSettings(
    val themeColor: AppThemeColor = AppThemeColor.DEFAULT,
    val themeMode: AppThemeMode = AppThemeMode.DEFAULT,

    /**
     * System colors from Android 12 onwards (Material You).
     *
     * Off by default, so the color chosen here is actually visible - otherwise the system
     * scheme would always override it on newer devices.
     */
    val useDynamicColor: Boolean = false,

    /** Opens the detail view directly in the full-screen barcode. */
    val openBarcodeFullscreen: Boolean = false,

    /** Also shows expired passes in their original folder. */
    val showExpiredInFolders: Boolean = false,

    /** Also shows passes with a future start date in "All passes" and their original folder. */
    val showUpcomingInFolders: Boolean = false,

    /** Pre-filled owner name for newly created passes. */
    val defaultOwnerName: String = "",

    /** Pre-filled folder for newly created passes. */
    val defaultFolderName: String = PassEntity.DEFAULT_FOLDER,

    /** SAF tree URI for automatic backups; `null` disables automatic backups. */
    val automaticBackupFolderUri: String? = null,

    /** Number of automatic backup archives retained, always between 1 and 7. */
    val rollingBackupCount: Int = DEFAULT_ROLLING_BACKUP_COUNT,
)

/**
 * Persistence of app settings.
 *
 * Deliberately `SharedPreferences` instead of DataStore: this is about a handful of switches
 * that must already be fixed by the first frame (the theme depends on it). An extra dependency
 * would not be justified for that.
 */
class SettingsStore(context: Context) {

    private val preferences: SharedPreferences =
        context.applicationContext.getSharedPreferences(FILE_NAME, Context.MODE_PRIVATE)

    /** Current state without waiting - for the first frame after startup. */
    val current: AppSettings get() = read()

    /**
     * Changes as a data stream.
     *
     * `conflate`, because when writing multiple values only the final state matters; an
     * intermediate state would only trigger an extra recomposition.
     */
    val settings: Flow<AppSettings> = callbackFlow {
        trySend(read())

        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, _ ->
            trySend(read())
        }
        preferences.registerOnSharedPreferenceChangeListener(listener)

        awaitClose { preferences.unregisterOnSharedPreferenceChangeListener(listener) }
    }.conflate()

    fun setThemeColor(value: AppThemeColor) = preferences.edit { putString(KEY_COLOR, value.key) }

    fun setThemeMode(value: AppThemeMode) = preferences.edit { putString(KEY_MODE, value.key) }

    fun setUseDynamicColor(value: Boolean) = preferences.edit { putBoolean(KEY_DYNAMIC, value) }

    fun setOpenBarcodeFullscreen(value: Boolean) =
        preferences.edit { putBoolean(KEY_FULLSCREEN, value) }

    fun setShowExpiredInFolders(value: Boolean) =
        preferences.edit { putBoolean(KEY_EXPIRED_IN_FOLDERS, value) }

    fun setShowUpcomingInFolders(value: Boolean) =
        preferences.edit { putBoolean(KEY_UPCOMING_IN_FOLDERS, value) }

    fun setDefaultOwnerName(value: String) =
        preferences.edit { putString(KEY_DEFAULT_OWNER_NAME, value) }

    fun setDefaultFolderName(value: String) =
        preferences.edit { putString(KEY_DEFAULT_FOLDER_NAME, value) }

    fun setAutomaticBackupFolderUri(value: String?) =
        preferences.edit { putString(KEY_AUTOMATIC_BACKUP_FOLDER_URI, value) }

    fun setRollingBackupCount(value: Int) =
        preferences.edit { putInt(KEY_ROLLING_BACKUP_COUNT, value.coerceIn(1, 7)) }

    private fun read() = AppSettings(
        themeColor = AppThemeColor.fromKey(preferences.getString(KEY_COLOR, null)),
        themeMode = AppThemeMode.fromKey(preferences.getString(KEY_MODE, null)),
        useDynamicColor = preferences.getBoolean(KEY_DYNAMIC, false),
        openBarcodeFullscreen = preferences.getBoolean(KEY_FULLSCREEN, false),
        showExpiredInFolders = preferences.getBoolean(KEY_EXPIRED_IN_FOLDERS, false),
        showUpcomingInFolders = preferences.getBoolean(KEY_UPCOMING_IN_FOLDERS, false),
        defaultOwnerName = preferences.getString(KEY_DEFAULT_OWNER_NAME, null).orEmpty(),
        defaultFolderName = preferences.getString(KEY_DEFAULT_FOLDER_NAME, null)
            ?: PassEntity.DEFAULT_FOLDER,
        automaticBackupFolderUri = preferences.getString(KEY_AUTOMATIC_BACKUP_FOLDER_URI, null),
        rollingBackupCount = preferences.getInt(
            KEY_ROLLING_BACKUP_COUNT,
            DEFAULT_ROLLING_BACKUP_COUNT,
        ).coerceIn(1, 7),
    )

    /**
     * Writes asynchronously.
     *
     * `apply` instead of `commit`: the call comes from the UI, and the change is reported back
     * via the listener anyway - nobody needs to wait for the write to disk.
     */
    private inline fun SharedPreferences.edit(block: SharedPreferences.Editor.() -> Unit) {
        edit().apply(block).apply()
    }

    private companion object {
        const val FILE_NAME = "passporta_settings"

        const val KEY_COLOR = "theme_color"
        const val KEY_MODE = "theme_mode"
        const val KEY_DYNAMIC = "dynamic_color"
        const val KEY_FULLSCREEN = "barcode_fullscreen"
        const val KEY_EXPIRED_IN_FOLDERS = "expired_in_folders"
        const val KEY_UPCOMING_IN_FOLDERS = "upcoming_in_folders"
        const val KEY_DEFAULT_OWNER_NAME = "default_owner_name"
        const val KEY_DEFAULT_FOLDER_NAME = "default_folder_name"
        const val KEY_AUTOMATIC_BACKUP_FOLDER_URI = "automatic_backup_folder_uri"
        const val KEY_ROLLING_BACKUP_COUNT = "rolling_backup_count"
    }
}
