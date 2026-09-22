package org.shadowgrove.passporta.ui.editor

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.exclude
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Colorize
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Grid4x4
import androidx.compose.material.icons.filled.GridOn
import androidx.compose.material.icons.filled.Numbers
import androidx.compose.material.icons.filled.QrCode2
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.ViewHeadline
import androidx.compose.material.icons.filled.ViewWeek
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FabPosition
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import org.shadowgrove.passporta.R
import org.shadowgrove.passporta.data.local.entity.BarcodeType
import org.shadowgrove.passporta.ui.icons.PassIcon
import org.shadowgrove.passporta.ui.icons.PassIconLibrary
import org.shadowgrove.passporta.ui.model.PassPalette
import java.io.File
import java.text.DateFormat
import java.util.Calendar
import java.util.Date
import java.util.TimeZone

/**
 * Form for reviewing and adjusting pass data before saving.
 *
 * Pre-filled from a scan (image/PDF), from an existing pass, or empty for manual creation. All
 * fields remain editable - the scan heuristic only provides suggestions.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PassEditorScreen(
    onBack: () -> Unit,
    onSaved: (String) -> Unit,
    viewModel: PassEditorViewModel,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        stringResource(
                            if (state.isEditingExisting) {
                                R.string.editor_title_edit
                            } else {
                                R.string.editor_title_new
                            },
                        ),
                    )
                },
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
        floatingActionButton = {
            // There's nothing to save yet while the analysis is running.
            if (!state.isScanning) {
                SaveFab(
                    enabled = state.canSave,
                    onClick = { viewModel.save(onSaved) },
                    // Same spacing as the content: otherwise the button would sit behind the
                    // keyboard and be unreachable while filling in the form.
                    modifier = Modifier.windowInsetsPadding(
                        WindowInsets.ime.exclude(WindowInsets.navigationBars),
                    ),
                )
            }
        },
        floatingActionButtonPosition = FabPosition.End,
    ) { innerPadding ->
        if (state.isScanning) {
            ScanningIndicator(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
            )
            return@Scaffold
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                // Space for the keyboard.
                //
                // Deliberately a layout modifier and not the detour via the scaffold's
                // `contentWindowInsets`: `windowInsetsPadding` re-reads the animated inset on
                // every measure pass and reliably falls back to zero when closed. A
                // once-computed `PaddingValues`, by contrast, could remain stuck at the value
                // of the opened keyboard - the content would then stay shrunk.
                //
                // `exclude` prevents double spacing: the navigation bar is already included in
                // `innerPadding`, and the keyboard covers it anyway.
                .windowInsetsPadding(WindowInsets.ime.exclude(WindowInsets.navigationBars))
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (state.scanFailed) {
                Text(
                    text = stringResource(R.string.editor_scan_unreadable),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                )
            } else if (state.scanFoundNoBarcode) {
                Text(
                    text = stringResource(R.string.editor_scan_no_barcode),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                )
            }

            OutlinedTextField(
                value = state.title,
                onValueChange = viewModel::updateTitle,
                label = { Text(stringResource(R.string.editor_field_title)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = state.subtitle,
                onValueChange = viewModel::updateSubtitle,
                label = { Text(stringResource(R.string.editor_field_subtitle)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = state.ownerName,
                onValueChange = viewModel::updateOwnerName,
                label = { Text(stringResource(R.string.editor_field_owner)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = state.identifier,
                onValueChange = viewModel::updateIdentifier,
                label = { Text(stringResource(R.string.editor_field_identifier)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            FolderSelector(
                folderName = state.folderName,
                suggestions = state.folderSuggestions,
                onValueChange = viewModel::updateFolderName,
            )
            OutlinedTextField(
                value = state.location,
                onValueChange = viewModel::updateLocation,
                label = { Text(stringResource(R.string.editor_field_location)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )

            BarcodeEditor(
                barcodes = state.barcodes,
                onDataChange = viewModel::updateBarcodeData,
                onTypeChange = viewModel::updateBarcodeType,
                onAltTextChange = viewModel::updateBarcodeAltText,
                onRemove = viewModel::removeBarcode,
                onAdd = viewModel::addBarcode,
            )

            DateRangeSelector(
                startDate = state.startDate,
                expirationDate = state.expirationDate,
                onSelectStart = viewModel::updateStartDate,
                onSelectExpiration = viewModel::updateExpirationDate,
            )

            LogoSelector(
                logoFile = state.logoFile,
                icon = state.icon,
                onPick = viewModel::pickLogo,
                onPickIcon = viewModel::pickIcon,
                onRemove = viewModel::removeLogo,
                onRemoveIcon = viewModel::removeIcon,
            )

            ColorSelector(
                selected = state.backgroundColor,
                onSelect = viewModel::updateBackgroundColor,
            )

            FieldEditor(
                fields = state.fields,
                onLabelChange = viewModel::updateFieldLabel,
                onValueChange = viewModel::updateFieldValue,
                onRemove = viewModel::removeField,
                onAdd = viewModel::addField,
            )

            if (state.suggestions.isNotEmpty()) {
                SuggestionList(
                    suggestions = state.suggestions,
                    onPick = viewModel::updateTitle,
                )
            }

            // Space for the FAB, so it doesn't cover the last field.
            Spacer(modifier = Modifier.height(80.dp))
        }
    }
}

/**
 * Round save button.
 *
 * Material 3 has no disabled FAB. As long as title or barcode are missing, it is therefore shown
 * dimmed and ignores clicks - but the button doesn't disappear, so it stays recognizable where
 * saving happens.
 */
@Composable
private fun SaveFab(
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    FloatingActionButton(
        onClick = { if (enabled) onClick() },
        containerColor = if (enabled) {
            MaterialTheme.colorScheme.primaryContainer
        } else {
            MaterialTheme.colorScheme.surfaceVariant
        },
        contentColor = if (enabled) {
            MaterialTheme.colorScheme.onPrimaryContainer
        } else {
            MaterialTheme.colorScheme.onSurfaceVariant
        },
        modifier = modifier,
    ) {
        Icon(
            imageVector = Icons.Default.Check,
            contentDescription = stringResource(R.string.editor_save),
        )
    }
}

@Composable
private fun ScanningIndicator(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        CircularProgressIndicator()
        Text(
            text = stringResource(R.string.editor_scanning),
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(top = 16.dp),
        )
    }
}

/**
 * All barcodes of a pass, each editable in a compact card with type and value side by side.
 */
@Composable
private fun BarcodeEditor(
    barcodes: List<EditableBarcode>,
    onDataChange: (String, String) -> Unit,
    onTypeChange: (String, BarcodeType) -> Unit,
    onAltTextChange: (String, String) -> Unit,
    onRemove: (String) -> Unit,
    onAdd: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(
            text = stringResource(R.string.editor_barcodes_title),
            style = MaterialTheme.typography.labelLarge,
        )

        barcodes.forEachIndexed { index, barcode ->
            BarcodeRow(
                index = index,
                barcode = barcode,
                showRemove = barcodes.size > 1,
                onDataChange = { onDataChange(barcode.id, it) },
                onTypeChange = { onTypeChange(barcode.id, it) },
                onAltTextChange = { onAltTextChange(barcode.id, it) },
                onRemove = { onRemove(barcode.id) },
            )
        }

        TextButton(onClick = onAdd) {
            Icon(imageVector = Icons.Default.Add, contentDescription = null)
            Text(
                text = stringResource(R.string.editor_barcode_add),
                modifier = Modifier.padding(start = 8.dp),
            )
        }
    }
}

/** One barcode: number/remove header, then type and value in the same row, plus a caption. */
@Composable
private fun BarcodeRow(
    index: Int,
    barcode: EditableBarcode,
    showRemove: Boolean,
    onDataChange: (String) -> Unit,
    onTypeChange: (BarcodeType) -> Unit,
    onAltTextChange: (String) -> Unit,
    onRemove: () -> Unit,
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, MaterialTheme.shapes.medium)
            .padding(12.dp),
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(R.string.editor_barcode_number, index + 1),
                style = MaterialTheme.typography.labelMedium,
                modifier = Modifier.weight(1f),
            )
            if (showRemove) {
                IconButton(onClick = onRemove) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = stringResource(R.string.editor_barcode_remove),
                    )
                }
            }
        }

        // Type and value share a row: together they fully describe the barcode, and this way
        // both fit above the fold even with several barcodes.
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth(),
        ) {
            OutlinedTextField(
                value = barcode.data,
                onValueChange = onDataChange,
                label = { Text(stringResource(R.string.editor_field_barcode_data)) },
                singleLine = true,
                modifier = Modifier.weight(1.4f),
            )
            CompactBarcodeTypeSelector(
                selected = barcode.type,
                onSelect = onTypeChange,
                modifier = Modifier.weight(1f),
            )
        }

        OutlinedTextField(
            value = barcode.altText,
            onValueChange = onAltTextChange,
            label = { Text(stringResource(R.string.editor_field_barcode_alt_text)) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

/** Narrower variant of the barcode type dropdown, meant to sit next to the value field. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CompactBarcodeTypeSelector(
    selected: BarcodeType,
    onSelect: (BarcodeType) -> Unit,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = it },
        modifier = modifier,
    ) {
        OutlinedTextField(
            value = selected.storageKey,
            onValueChange = {},
            readOnly = true,
            label = { Text(stringResource(R.string.editor_field_barcode_type)) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
            modifier = Modifier
                .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable)
                .fillMaxWidth(),
        )

        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            BarcodeType.entries.forEach { type ->
                DropdownMenuItem(
                    text = { Text(type.storageKey) },
                    leadingIcon = { BarcodeTypeIcon(type) },
                    onClick = {
                        onSelect(type)
                        expanded = false
                    },
                )
            }
        }
    }
}

/**
 * Symbol representing the respective format.
 *
 * The icons reflect the structure, not the name: area patterns for the 2D formats, stacked
 * lines for PDF417, vertical bars for CODE128. ITF gets the digit symbol - that this format
 * only encodes an even number of digits is exactly the information needed when choosing it.
 */
@Composable
private fun BarcodeTypeIcon(type: BarcodeType) {
    Icon(
        imageVector = when (type) {
            BarcodeType.QR -> Icons.Default.QrCode2
            BarcodeType.DATA_MATRIX -> Icons.Default.Grid4x4
            BarcodeType.AZTEC -> Icons.Default.GridOn
            BarcodeType.PDF417 -> Icons.Default.ViewHeadline
            BarcodeType.CODE128 -> Icons.Default.ViewWeek
            BarcodeType.ITF -> Icons.Default.Numbers
            BarcodeType.UPC, BarcodeType.EAN -> Icons.Default.Numbers
        },
        contentDescription = null,
    )
}

@Composable
private fun ColorSelector(
    selected: Int,
    onSelect: (Int) -> Unit,
) {
    var showCustomPicker by remember { mutableStateOf(false) }
    // Any color not among the fixed swatches is by definition a custom, freely picked one.
    val isCustomSelected = selected !in PassColorSwatches

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = stringResource(R.string.editor_field_color),
            style = MaterialTheme.typography.labelLarge,
        )
        Row(
            modifier = Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            // First in the row: opens the free color picker. Placed before the fixed swatches
            // so it's reachable without scrolling, regardless of how many swatches follow.
            CustomColorSwatch(
                color = if (isCustomSelected) selected else null,
                selected = isCustomSelected,
                onClick = { showCustomPicker = true },
            )

            PassColorSwatches.forEach { color ->
                val palette = PassPalette.from(color)
                val selectedSwatch = color == selected
                Box(
                    modifier = Modifier
                        .size(ColorSwatchSize)
                        .clip(CircleShape)
                        .background(palette.background)
                        .border(
                            width = if (selectedSwatch) 3.dp else 1.dp,
                            color = if (selectedSwatch) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.outlineVariant
                            },
                            shape = CircleShape,
                        )
                        .clickable { onSelect(color) },
                    contentAlignment = Alignment.Center,
                ) {
                    if (selectedSwatch) {
                        // The checkmark carries the field's contrast color: a fixed white
                        // checkmark would fade away on light colors like yellow.
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = stringResource(R.string.editor_color_selected),
                            tint = palette.content,
                            modifier = Modifier.size(ColorSwatchCheckSize),
                        )
                    }
                }
            }
        }
    }

    if (showCustomPicker) {
        ColorPickerDialog(
            initial = if (isCustomSelected) selected else PassColorSwatches.first(),
            onConfirm = {
                onSelect(it)
                showCustomPicker = false
            },
            onDismiss = { showCustomPicker = false },
        )
    }
}

/**
 * Round button opening the free color picker.
 *
 * Shows a rainbow ring as long as no custom color is active - a neutral placeholder would look
 * like just another (unexplained) swatch, while the rainbow immediately signals "pick any
 * color". Once a custom color is chosen, the button adopts it directly, exactly like the fixed
 * swatches.
 */
@Composable
private fun CustomColorSwatch(
    color: Int?,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val rainbowBrush = remember {
        Brush.sweepGradient(
            listOf(
                Color.Red,
                Color.Yellow,
                Color.Green,
                Color.Cyan,
                Color.Blue,
                Color.Magenta,
                Color.Red,
            ),
        )
    }

    Box(
        modifier = Modifier
            .size(ColorSwatchSize)
            .clip(CircleShape)
            .then(
                if (color != null) {
                    Modifier.background(Color(color))
                } else {
                    Modifier.background(rainbowBrush)
                },
            )
            .border(
                width = if (selected) 3.dp else 1.dp,
                color = if (selected) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.outlineVariant
                },
                shape = CircleShape,
            )
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        if (selected && color != null) {
            Icon(
                imageVector = Icons.Default.Check,
                contentDescription = stringResource(R.string.editor_color_selected),
                tint = PassPalette.from(color).content,
                modifier = Modifier.size(ColorSwatchCheckSize),
            )
        } else {
            Icon(
                imageVector = Icons.Default.Colorize,
                contentDescription = stringResource(R.string.editor_color_custom),
                tint = Color.White,
                modifier = Modifier.size(ColorSwatchCheckSize),
            )
        }
    }
}

/**
 * Free color selection via RGB sliders.
 */
@Composable
private fun ColorPickerDialog(
    initial: Int,
    onConfirm: (Int) -> Unit,
    onDismiss: () -> Unit,
) {
    var red by remember { mutableFloatStateOf(((initial shr 16) and 0xFF).toFloat()) }
    var green by remember { mutableFloatStateOf(((initial shr 8) and 0xFF).toFloat()) }
    var blue by remember { mutableFloatStateOf((initial and 0xFF).toFloat()) }

    val currentColor = remember(red, green, blue) {
        (0xFF shl 24) or
            (red.toInt() and 0xFF shl 16) or
            (green.toInt() and 0xFF shl 8) or
            (blue.toInt() and 0xFF)
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.editor_color_picker_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(ColorPreviewHeight)
                        .clip(MaterialTheme.shapes.medium)
                        .background(Color(currentColor))
                        .border(1.dp, MaterialTheme.colorScheme.outlineVariant, MaterialTheme.shapes.medium),
                )

                ColorSlider(
                    label = stringResource(R.string.editor_color_red, red.toInt()),
                    value = red,
                    valueRange = 0f..255f,
                    onValueChange = { red = it },
                )
                ColorSlider(
                    label = stringResource(R.string.editor_color_green, green.toInt()),
                    value = green,
                    valueRange = 0f..255f,
                    onValueChange = { green = it },
                )
                ColorSlider(
                    label = stringResource(R.string.editor_color_blue, blue.toInt()),
                    value = blue,
                    valueRange = 0f..255f,
                    onValueChange = { blue = it },
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(currentColor) }) {
                Text(stringResource(android.R.string.ok))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(android.R.string.cancel))
            }
        },
    )
}

@Composable
private fun ColorSlider(
    label: String,
    value: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    onValueChange: (Float) -> Unit,
) {
    Column {
        Text(text = label, style = MaterialTheme.typography.labelMedium)
        Slider(value = value, onValueChange = onValueChange, valueRange = valueRange)
    }
}


/** Recognized text lines as tappable suggestions for the title. */
@Composable
private fun SuggestionList(
    suggestions: List<String>,
    onPick: (String) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = stringResource(R.string.editor_suggestions),
            style = MaterialTheme.typography.labelLarge,
        )
        Row(
            modifier = Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            suggestions.take(MAX_SUGGESTIONS).forEach { suggestion ->
                AssistChip(
                    onClick = { onPick(suggestion) },
                    label = { Text(suggestion, maxLines = 1) },
                )
            }
        }
    }
}

/**
 * Optional start and expiration date.
 *
 * Both fields share the section title "Date": from the user's point of view they are two ends
 * of the same span, not two unrelated settings. A pass with a start date in the future gets its
 * own "Upcoming" category in the overview until that date passes - see
 * `PassOverviewViewModel`.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DateRangeSelector(
    startDate: Long?,
    expirationDate: Long?,
    onSelectStart: (Long?) -> Unit,
    onSelectExpiration: (Long?) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = stringResource(R.string.editor_field_date),
            style = MaterialTheme.typography.labelLarge,
        )
        DateRow(
            label = stringResource(R.string.editor_date_start),
            date = startDate,
            onSelect = onSelectStart,
            // The start date marks the beginning of the day, not its end - unlike the
            // expiration date it must not carry the end-of-day offset, otherwise a pass
            // starting "today" would incorrectly count as upcoming for the whole day.
            endOfDayOffset = false,
            isSelectableDate = { utcTimeMillis ->
                expirationDate == null || localStartOfDayMillis(utcTimeMillis) <= expirationDate
            },
        )
        DateRow(
            label = stringResource(R.string.editor_date_end),
            date = expirationDate,
            onSelect = onSelectExpiration,
            isSelectableDate = { utcTimeMillis ->
                // `startDate` is already the local start of its day, so it's directly
                // comparable with the candidate day's local start.
                startDate == null || localStartOfDayMillis(utcTimeMillis) >= startDate
            },
        )
    }
}

/** One row of [DateRangeSelector]: a labeled button opening the picker, plus a clear button. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DateRow(
    label: String,
    date: Long?,
    onSelect: (Long?) -> Unit,
    // Restricts the picker so the start/end invariant (end must not be before start) can't
    // even be violated in the first place, instead of merely rejecting it afterwards.
    isSelectableDate: (Long) -> Boolean = { true },
    // Whether the stored value should be pushed to the end of the picked day (used for the
    // expiration date, which should cover its whole day) or kept at its start (used for the
    // start date, which should already count as valid from the beginning of that day).
    endOfDayOffset: Boolean = true,
) {
    var showPicker by remember { mutableStateOf(false) }

    val formatted = date?.let {
        remember(it) { DateFormat.getDateInstance(DateFormat.LONG).format(Date(it)) }
    } ?: stringResource(R.string.editor_date_none)

    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.width(DateRowLabelWidth),
        )
        OutlinedButton(onClick = { showPicker = true }) { Text(formatted) }
        if (date != null) {
            TextButton(onClick = { onSelect(null) }) {
                Text(stringResource(R.string.editor_expiration_clear))
            }
        }
    }

    if (showPicker) {
        val pickerState = rememberDatePickerState(
            // `date` is stored as a local start/end-of-day value (see below), but the picker
            // expects UTC midnight of the calendar day - so it has to be converted back for
            // the initial selection to highlight the correct day.
            initialSelectedDateMillis = date?.let(::utcMidnightMillisOfLocalDay),
            selectableDates = remember(isSelectableDate) {
                object : SelectableDates {
                    override fun isSelectableDate(utcTimeMillis: Long): Boolean =
                        isSelectableDate.invoke(utcTimeMillis)
                }
            },
        )
        DatePickerDialog(
            onDismissRequest = { showPicker = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        // The picker returns midnight UTC of the chosen calendar day. That
                        // day is then re-expressed in the *local* timezone: for the
                        // expiration date, a pass is valid for the whole chosen day, so it's
                        // pushed to that day's local end; the start date, by contrast, should
                        // already count as valid from that day's beginning, so it keeps the
                        // local start. Using the local timezone (instead of just adding a
                        // fixed UTC offset) is what keeps the displayed day - which is
                        // formatted using the local timezone - in sync with the picked day,
                        // regardless of the device's UTC offset.
                        val selected = pickerState.selectedDateMillis?.let {
                            if (endOfDayOffset) localEndOfDayMillis(it) else localStartOfDayMillis(it)
                        }
                        onSelect(selected)
                        showPicker = false
                    },
                ) {
                    Text(stringResource(android.R.string.ok))
                }
            },
            dismissButton = {
                TextButton(onClick = { showPicker = false }) {
                    Text(stringResource(android.R.string.cancel))
                }
            },
        ) {
            DatePicker(state = pickerState)
        }
    }
}

/** Year/month/day of [millis] in the device's default timezone. */
private fun localDateParts(millis: Long): IntArray {
    val calendar = Calendar.getInstance().apply { timeInMillis = millis }
    return intArrayOf(
        calendar.get(Calendar.YEAR),
        calendar.get(Calendar.MONTH),
        calendar.get(Calendar.DAY_OF_MONTH),
    )
}

/** Year/month/day of the UTC calendar day represented by [utcMidnightMillis]. */
private fun utcDateParts(utcMidnightMillis: Long): IntArray {
    val calendar = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply {
        timeInMillis = utcMidnightMillis
    }
    return intArrayOf(
        calendar.get(Calendar.YEAR),
        calendar.get(Calendar.MONTH),
        calendar.get(Calendar.DAY_OF_MONTH),
    )
}

/**
 * Start of the local calendar day (00:00:00.000, default timezone) that the picker's UTC
 * midnight [utcMidnightMillis] represents.
 */
private fun localStartOfDayMillis(utcMidnightMillis: Long): Long {
    val (year, month, day) = utcDateParts(utcMidnightMillis).let { Triple(it[0], it[1], it[2]) }
    return Calendar.getInstance().apply {
        clear()
        set(year, month, day, 0, 0, 0)
    }.timeInMillis
}

/**
 * End of the local calendar day (23:59:59.999, default timezone) that the picker's UTC
 * midnight [utcMidnightMillis] represents.
 */
private fun localEndOfDayMillis(utcMidnightMillis: Long): Long {
    val (year, month, day) = utcDateParts(utcMidnightMillis).let { Triple(it[0], it[1], it[2]) }
    return Calendar.getInstance().apply {
        clear()
        set(year, month, day, 23, 59, 59)
        set(Calendar.MILLISECOND, 999)
    }.timeInMillis
}

/**
 * UTC midnight of the calendar day that [localMillis] falls on in the default timezone - the
 * inverse of [localStartOfDayMillis]/[localEndOfDayMillis], needed to feed a stored local value
 * back into the picker.
 */
private fun utcMidnightMillisOfLocalDay(localMillis: Long): Long {
    val (year, month, day) = localDateParts(localMillis).let { Triple(it[0], it[1], it[2]) }
    return Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply {
        clear()
        set(year, month, day, 0, 0, 0)
    }.timeInMillis
}

/**
 * Folder field with suggestions from the already existing categories.
 *
 * Deliberately a freely editable field with a dropdown and not a plain selection list: the
 * first pass of a new category must be creatable without the category already existing. The
 * dropdown prevents the most common mistake - a duplicate due to a differing spelling.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FolderSelector(
    folderName: String,
    suggestions: List<String>,
    onValueChange: (String) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }

    // While typing, show only matching suggestions; an exact match would just repeat the
    // input.
    val visible = remember(folderName, suggestions) {
        suggestions.filter {
            it.contains(folderName.trim(), ignoreCase = true) && !it.equals(folderName, true)
        }
    }

    ExposedDropdownMenuBox(
        expanded = expanded && visible.isNotEmpty(),
        onExpandedChange = { expanded = it },
    ) {
        OutlinedTextField(
            value = folderName,
            onValueChange = {
                onValueChange(it)
                expanded = true
            },
            label = { Text(stringResource(R.string.editor_field_folder)) },
            singleLine = true,
            trailingIcon = {
                if (suggestions.isNotEmpty()) {
                    ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded)
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                // `MenuAnchorType.PrimaryEditable` keeps the field editable - a
                // `PrimaryNotEditable` would make every tap just open the menu.
                .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryEditable),
        )

        ExposedDropdownMenu(expanded = expanded && visible.isNotEmpty(), onDismissRequest = { expanded = false }) {
            visible.forEach { suggestion ->
                DropdownMenuItem(
                    text = { Text(suggestion) },
                    onClick = {
                        onValueChange(suggestion)
                        expanded = false
                    },
                )
            }
        }
    }
}

/** Logo upload and symbol selection with a round preview. */
@Composable
private fun LogoSelector(
    logoFile: File?,
    icon: PassIcon?,
    onPick: (Uri) -> Unit,
    onPickIcon: (PassIcon) -> Unit,
    onRemove: () -> Unit,
    onRemoveIcon: () -> Unit,
) {
    val picker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri -> uri?.let(onPick) }

    var showIconPicker by remember { mutableStateOf(false) }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = stringResource(R.string.editor_field_logo),
            style = MaterialTheme.typography.labelLarge,
        )
        Row(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center,
            ) {
                when {
                    logoFile != null -> AsyncImage(
                        model = logoFile,
                        contentDescription = stringResource(R.string.editor_field_logo),
                        contentScale = ContentScale.Fit,
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(6.dp),
                    )

                    icon != null -> Icon(
                        imageVector = icon.image,
                        contentDescription = icon.label,
                        modifier = Modifier.size(28.dp),
                    )
                }
            }

            // "Remove" sits below the two selection buttons, not next to them: in one row
            // they'd barely be readable as a trio on narrow devices, and the destructive
            // command should stand apart from the two equal-ranking alternatives anyway.
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { picker.launch(LOGO_MIME_TYPES) }) {
                        Text(stringResource(R.string.editor_logo_choose))
                    }
                    OutlinedButton(onClick = { showIconPicker = true }) {
                        Text(stringResource(R.string.editor_logo_icon))
                    }
                }

                if (logoFile != null) {
                    TextButton(onClick = onRemove) {
                        Text(stringResource(R.string.editor_logo_remove))
                    }
                } else if (icon != null) {
                    TextButton(onClick = onRemoveIcon) {
                        Text(stringResource(R.string.editor_logo_remove))
                    }
                }
            }
        }
    }

    if (showIconPicker) {
        IconPickerDialog(
            selected = icon,
            onSelect = {
                onPickIcon(it)
                showIconPicker = false
            },
            onDismiss = { showIconPicker = false },
        )
    }
}

/**
 * Searchable symbol selection.
 *
 * The list is deliberately curated (see [PassIconLibrary]) - a search among thirty matching
 * symbols reaches the goal faster than among three thousand mostly unsuitable ones.
 */
@Composable
private fun IconPickerDialog(
    selected: PassIcon?,
    onSelect: (PassIcon) -> Unit,
    onDismiss: () -> Unit,
) {
    var query by remember { mutableStateOf("") }
    val results = remember(query) { PassIconLibrary.search(query) }
    // Only the unfiltered, full list separates curated from the library's remaining symbols -
    // once the user searches, everything is ranked together by relevance (see
    // `PassIconLibrary.search`), so a rigid split would no longer make sense there.
    val showSections = query.isBlank()
    val extraIcons = PassIconLibrary.extra

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.editor_icon_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    placeholder = { Text(stringResource(R.string.editor_icon_search)) },
                    leadingIcon = {
                        Icon(imageVector = Icons.Default.Search, contentDescription = null)
                    },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )

                if (results.isEmpty()) {
                    Text(
                        text = stringResource(R.string.editor_icon_none, query),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    LazyVerticalGrid(
                        columns = GridCells.Adaptive(72.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        // Fixed height: in a dialog, a list has no natural upper limit; without
                        // it, the grid would blow up the screen.
                        modifier = Modifier.height(280.dp),
                    ) {
                        if (showSections) {
                            items(items = PassIconLibrary.curated, key = { it.key }) { option ->
                                IconOption(
                                    icon = option,
                                    selected = option.key == selected?.key,
                                    onClick = { onSelect(option) },
                                )
                            }
                            if (extraIcons.isNotEmpty()) {
                                item(span = { GridItemSpan(maxLineSpan) }) {
                                    IconSectionDivider()
                                }
                                items(items = extraIcons, key = { it.key }) { option ->
                                    IconOption(
                                        icon = option,
                                        selected = option.key == selected?.key,
                                        onClick = { onSelect(option) },
                                    )
                                }
                            }
                        } else {
                            items(items = results, key = { it.key }) { option ->
                                IconOption(
                                    icon = option,
                                    selected = option.key == selected?.key,
                                    onClick = { onSelect(option) },
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(android.R.string.cancel))
            }
        },
    )
}

/** Separates the curated icons from the icon library's remaining symbols (see [IconPickerDialog]). */
@Composable
private fun IconSectionDivider() {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.padding(vertical = 4.dp),
    ) {
        HorizontalDivider(modifier = Modifier.weight(1f))
        Text(
            text = stringResource(R.string.editor_icon_more),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        HorizontalDivider(modifier = Modifier.weight(1f))
    }
}

@Composable
private fun IconOption(
    icon: PassIcon,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val background = if (selected) {
        MaterialTheme.colorScheme.primaryContainer
    } else {
        MaterialTheme.colorScheme.surfaceVariant
    }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
        modifier = Modifier
            .clip(MaterialTheme.shapes.medium)
            .clickable(onClick = onClick)
            .padding(vertical = 6.dp),
    ) {
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(CircleShape)
                .background(background),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = icon.image,
                contentDescription = icon.label,
                modifier = Modifier.size(24.dp),
            )
        }
        Text(
            text = icon.label,
            style = MaterialTheme.typography.labelSmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
        )
    }
}

/**
 * Additional fields as label-value pairs.
 *
 * Deliberately a simple list without drag-and-drop: the order results from creation, which is
 * entirely sufficient for the typical two to five fields.
 */
@Composable
private fun FieldEditor(
    fields: List<EditableField>,
    onLabelChange: (String, String) -> Unit,
    onValueChange: (String, String) -> Unit,
    onRemove: (String) -> Unit,
    onAdd: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = stringResource(R.string.editor_fields_title),
            style = MaterialTheme.typography.labelLarge,
        )

        fields.forEach { field ->
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth(),
            ) {
                OutlinedTextField(
                    value = field.label,
                    onValueChange = { onLabelChange(field.id, it) },
                    label = { Text(stringResource(R.string.editor_field_label)) },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                )
                OutlinedTextField(
                    value = field.value,
                    onValueChange = { onValueChange(field.id, it) },
                    label = { Text(stringResource(R.string.editor_field_value)) },
                    singleLine = true,
                    modifier = Modifier.weight(1.4f),
                )
                IconButton(onClick = { onRemove(field.id) }) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = stringResource(R.string.editor_field_remove),
                    )
                }
            }
        }

        TextButton(onClick = onAdd) {
            Icon(imageVector = Icons.Default.Add, contentDescription = null)
            Text(
                text = stringResource(R.string.editor_field_add),
                modifier = Modifier.padding(start = 8.dp),
            )
        }
    }
}

private const val MAX_SUGGESTIONS = 12

/** Dimensions of the color swatches in the card color selection. */
private val ColorSwatchSize = 40.dp
private val ColorSwatchCheckSize = 20.dp

/** Preview bar height in the custom color picker dialog. */
private val ColorPreviewHeight = 56.dp

/** Fixed width for the "Start"/"End" labels, so both date buttons line up. */
private val DateRowLabelWidth = 44.dp

/** Image types for the logo upload. */
private val LOGO_MIME_TYPES = arrayOf("image/*")



