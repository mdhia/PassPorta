package org.shadowgrove.passporta.ui.settings

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import org.shadowgrove.passporta.R
import org.shadowgrove.passporta.data.settings.AppThemeColor
import org.shadowgrove.passporta.data.settings.AppThemeMode
import org.shadowgrove.passporta.ui.model.PassPalette
import org.shadowgrove.passporta.ui.toUserMessage

/** Size of a color swatch in the color picker. */
private val SwatchSize = 44.dp

/** Size of the checkmark in the selected color swatch. */
private val SwatchCheckSize = 20.dp

/** Suggested file name for an exported backup; the user can still change it in the picker. */
private const val BACKUP_FILE_NAME = "passporta-backup.zip"

/**
 * MIME types accepted when picking a backup to import.
 *
 * Many file apps hand out `.zip` files with a generic type, so `application/octet-stream` is
 * accepted too - [org.shadowgrove.passporta.data.backup.BackupManager] itself detects whether the
 * archive is actually a valid backup.
 */
private val BACKUP_MIME_TYPES = arrayOf("application/zip", "application/octet-stream")

/**
 * App settings.
 *
 * Deliberately without a save button: every change applies immediately and is instantly
 * visible - for color and appearance, that is exactly the best feedback.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: SettingsViewModel = viewModel(factory = SettingsViewModel.Factory),
) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()

    // Not part of `settings`: the language is governed by AppCompatDelegate, not by our own
    // SharedPreferences store (see [AppLanguage]). A locale change recreates the activity almost
    // immediately, so plain `remember` is enough to reflect the choice until then.
    var language by remember { mutableStateOf(AppLanguage.current()) }

    val resources = LocalResources.current
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(viewModel, resources) {
        viewModel.backupResults.collect { result ->
            snackbarHostState.showSnackbar(result.toUserMessage(resources))
        }
    }

    val exportBackupLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/zip"),
    ) { uri -> uri?.let(viewModel::exportBackup) }

    val importBackupLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri -> uri?.let(viewModel::importBackup) }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.detail_back),
                        )
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(bottom = 24.dp),
        ) {
            SectionTitle(stringResource(R.string.settings_section_language))

            Column(modifier = Modifier.selectableGroup()) {
                AppLanguage.entries.forEach { option ->
                    ThemeModeOption(
                        label = stringResource(option.labelRes),
                        selected = language == option,
                        onSelect = {
                            language = option
                            viewModel.setLanguage(option)
                        },
                    )
                }
            }

            HorizontalDivider()
            SectionTitle(stringResource(R.string.settings_section_appearance))

            ColorPicker(
                selected = settings.themeColor,
                enabled = !settings.useDynamicColor,
                onSelect = viewModel::setThemeColor,
            )

            ListItem(
                headlineContent = { Text(stringResource(R.string.settings_dynamic_color)) },
                supportingContent = {
                    Text(stringResource(R.string.settings_dynamic_color_hint))
                },
                trailingContent = {
                    Switch(
                        checked = settings.useDynamicColor,
                        onCheckedChange = viewModel::setUseDynamicColor,
                    )
                },
                modifier = Modifier.clickable {
                    viewModel.setUseDynamicColor(!settings.useDynamicColor)
                },
            )

            SectionTitle(stringResource(R.string.settings_section_mode))

            Column(modifier = Modifier.selectableGroup()) {
                ThemeModeOption(
                    label = stringResource(R.string.settings_mode_system),
                    selected = settings.themeMode == AppThemeMode.SYSTEM,
                    onSelect = { viewModel.setThemeMode(AppThemeMode.SYSTEM) },
                )
                ThemeModeOption(
                    label = stringResource(R.string.settings_mode_light),
                    selected = settings.themeMode == AppThemeMode.LIGHT,
                    onSelect = { viewModel.setThemeMode(AppThemeMode.LIGHT) },
                )
                ThemeModeOption(
                    label = stringResource(R.string.settings_mode_dark),
                    selected = settings.themeMode == AppThemeMode.DARK,
                    onSelect = { viewModel.setThemeMode(AppThemeMode.DARK) },
                )
            }

            HorizontalDivider()
            SectionTitle(stringResource(R.string.settings_section_behaviour))

            ListItem(
                headlineContent = { Text(stringResource(R.string.settings_fullscreen)) },
                supportingContent = { Text(stringResource(R.string.settings_fullscreen_hint)) },
                trailingContent = {
                    Switch(
                        checked = settings.openBarcodeFullscreen,
                        onCheckedChange = viewModel::setOpenBarcodeFullscreen,
                    )
                },
                modifier = Modifier.clickable {
                    viewModel.setOpenBarcodeFullscreen(!settings.openBarcodeFullscreen)
                },
            )

            ListItem(
                headlineContent = { Text(stringResource(R.string.settings_expired_in_folders)) },
                supportingContent = {
                    Text(stringResource(R.string.settings_expired_in_folders_hint))
                },
                trailingContent = {
                    Switch(
                        checked = settings.showExpiredInFolders,
                        onCheckedChange = viewModel::setShowExpiredInFolders,
                    )
                },
                modifier = Modifier.clickable {
                    viewModel.setShowExpiredInFolders(!settings.showExpiredInFolders)
                },
            )

            ListItem(
                headlineContent = { Text(stringResource(R.string.settings_upcoming_in_folders)) },
                supportingContent = {
                    Text(stringResource(R.string.settings_upcoming_in_folders_hint))
                },
                trailingContent = {
                    Switch(
                        checked = settings.showUpcomingInFolders,
                        onCheckedChange = viewModel::setShowUpcomingInFolders,
                    )
                },
                modifier = Modifier.clickable {
                    viewModel.setShowUpcomingInFolders(!settings.showUpcomingInFolders)
                },
            )

            HorizontalDivider()
            SectionTitle(stringResource(R.string.settings_section_backup))

            ListItem(
                headlineContent = { Text(stringResource(R.string.settings_backup_export)) },
                supportingContent = { Text(stringResource(R.string.settings_backup_export_hint)) },
                modifier = Modifier.clickable {
                    exportBackupLauncher.launch(BACKUP_FILE_NAME)
                },
            )

            ListItem(
                headlineContent = { Text(stringResource(R.string.settings_backup_import)) },
                supportingContent = { Text(stringResource(R.string.settings_backup_import_hint)) },
                modifier = Modifier.clickable {
                    importBackupLauncher.launch(BACKUP_MIME_TYPES)
                },
            )

            HorizontalDivider()
            SectionTitle(stringResource(R.string.settings_section_about))

            ListItem(
                headlineContent = { Text(stringResource(R.string.settings_offline)) },
                supportingContent = { Text(stringResource(R.string.settings_offline_hint)) },
            )
        }
    }
}

@Composable
private fun SectionTitle(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = modifier.padding(start = 16.dp, end = 16.dp, top = 20.dp, bottom = 4.dp),
    )
}

@Composable
private fun ThemeModeOption(
    label: String,
    selected: Boolean,
    onSelect: () -> Unit,
) {
    ListItem(
        headlineContent = { Text(label) },
        leadingContent = {
            // The button itself gets no onClick: the whole row is selectable, a second click
            // target in it would only be confusing for accessibility.
            RadioButton(selected = selected, onClick = null)
        },
        modifier = Modifier.clickable(role = Role.RadioButton, onClick = onSelect),
    )
}

/** Color selection as a row of round swatches. */
@Composable
private fun ColorPicker(
    selected: AppThemeColor,
    enabled: Boolean,
    onSelect: (AppThemeColor) -> Unit,
    modifier: Modifier = Modifier,
) {
    FlowRow(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        AppThemeColor.entries.forEach { option ->
            ColorSwatch(
                color = option.previewColor(),
                selected = option == selected,
                enabled = enabled,
                contentDescription = option.label(),
                onClick = { onSelect(option) },
            )
        }
    }
}

@Composable
private fun ColorSwatch(
    color: Color,
    selected: Boolean,
    enabled: Boolean,
    contentDescription: String,
    onClick: () -> Unit,
) {
    val border = if (selected) {
        MaterialTheme.colorScheme.onSurface
    } else {
        MaterialTheme.colorScheme.outlineVariant
    }

    Box(
        modifier = Modifier
            .size(SwatchSize)
            .clip(CircleShape)
            .background(if (enabled) color else color.copy(alpha = 0.35f))
            .border(width = if (selected) 3.dp else 1.dp, color = border, shape = CircleShape)
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        if (selected) {
            Icon(
                imageVector = Icons.Default.Check,
                contentDescription = contentDescription,
                // Contrast color instead of a fixed white: on amber, a white checkmark would
                // be barely visible. Same rule as for the pass colors.
                tint = PassPalette.from(color.toArgb()).content,
                modifier = Modifier.size(SwatchCheckSize),
            )
        }
    }
}

/** Vivid version of the base color - only for the preview in the selection. */
private fun AppThemeColor.previewColor(): Color = when (this) {

    AppThemeColor.INDIGO -> Color(0xFF3F51B5)
    AppThemeColor.TEAL -> Color(0xFF00796B)
    AppThemeColor.GREEN -> Color(0xFF2E7D32)
    AppThemeColor.AMBER -> Color(0xFFF9A825)
    AppThemeColor.ORANGE -> Color(0xFFE65100)
    AppThemeColor.RED -> Color(0xFFC62828)
    AppThemeColor.VIOLET -> Color(0xFF6A1B9A)
    AppThemeColor.SLATE -> Color(0xFF37474F)
}

@Composable
private fun AppThemeColor.label(): String = stringResource(
    when (this) {
        AppThemeColor.INDIGO -> R.string.settings_color_indigo
        AppThemeColor.TEAL -> R.string.settings_color_teal
        AppThemeColor.GREEN -> R.string.settings_color_green
        AppThemeColor.AMBER -> R.string.settings_color_amber
        AppThemeColor.ORANGE -> R.string.settings_color_orange
        AppThemeColor.RED -> R.string.settings_color_red
        AppThemeColor.VIOLET -> R.string.settings_color_violet
        AppThemeColor.SLATE -> R.string.settings_color_slate
    },
)

/**
 * Label for a selectable display language.
 *
 * Kept next to the enum's usage instead of inside [AppLanguage] so the ViewModel layer stays free
 * of resource ids; the `when` is exhaustive, so a newly added language cannot be forgotten here.
 */
@get:StringRes
private val AppLanguage.labelRes: Int
    get() = when (this) {
        AppLanguage.SYSTEM -> R.string.settings_language_system
        AppLanguage.GERMAN -> R.string.settings_language_german
        AppLanguage.ENGLISH -> R.string.settings_language_english
    }


