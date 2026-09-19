package org.shadowgrove.passporta.ui.editor

import android.net.Uri
import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.shadowgrove.passporta.data.importer.LocalDocumentSource
import org.shadowgrove.passporta.data.importer.PassAssetStore
import org.shadowgrove.passporta.data.local.entity.BarcodeType
import org.shadowgrove.passporta.data.local.entity.PassEntity
import org.shadowgrove.passporta.data.local.entity.PassFieldEntity
import org.shadowgrove.passporta.data.local.entity.PassSource
import org.shadowgrove.passporta.data.repository.PassRepository
import org.shadowgrove.passporta.data.scanner.PassScanner
import org.shadowgrove.passporta.data.settings.SettingsStore
import org.shadowgrove.passporta.ui.icons.PassIcon
import org.shadowgrove.passporta.ui.icons.PassIconLibrary
import org.shadowgrove.passporta.ui.passPortaApplication
import java.io.File
import java.util.UUID

/** Selection of card colors for manually created passes. */
val PassColorSwatches: List<Int> = listOf(
    0xFF37474F.toInt(), // Blue-gray
    0xFF5A3CC8.toInt(), // Violet
    0xFF1A73E8.toInt(), // Blue
    0xFF00796B.toInt(), // Teal
    0xFF2E7D32.toInt(), // Green
    0xFFF9A825.toInt(), // Yellow
    0xFFE65100.toInt(), // Orange
    0xFFC62828.toInt(), // Red
    0xFFAD1457.toInt(), // Magenta
    0xFFECEFF1.toInt(), // Light gray
)

/**
 * An editable label-value row.
 *
 * [id] stays stable across changes, so Compose correctly maps the text fields when a row is
 * deleted.
 */
@Immutable
data class EditableField(
    val id: String = UUID.randomUUID().toString(),
    val label: String = "",
    val value: String = "",
)

@Immutable
data class PassEditorUiState(
    val title: String = "",
    val subtitle: String = "",
    val ownerName: String = "",
    val identifier: String = "",
    val folderName: String = PassEntity.DEFAULT_FOLDER,
    val barcodeData: String = "",
    val barcodeType: BarcodeType = BarcodeType.DEFAULT,
    val backgroundColor: Int = PassEntity.DEFAULT_BACKGROUND_COLOR,

    /** Additional fields as label-value pairs. */
    val fields: List<EditableField> = emptyList(),

    val location: String = "",

    /** `null` = valid indefinitely. */
    val expirationDate: Long? = null,

    /** `null` = valid from the start. */
    val startDate: Long? = null,

    /** Chosen logo - already saved locally, so the preview shows immediately. */
    val logoFile: File? = null,

    /** Chosen symbol from the icon library; applies when no logo is set. */
    val icon: PassIcon? = null,

    /** Already existing folders - suggestions for the folder field. */
    val folderSuggestions: List<String> = emptyList(),

    /** Lines recognized by the scan - tappable as suggestions in the form. */
    val suggestions: List<String> = emptyList(),

    val isScanning: Boolean = false,
    val isEditingExisting: Boolean = false,

    /** Set when a scan ran but found no barcode. */
    val scanFoundNoBarcode: Boolean = false,

    /**
     * Set when the source itself could not be evaluated.
     *
     * Deliberately separate from [scanFoundNoBarcode]: for an empty camera file, a hint about
     * missing barcodes doesn't help - the capture needs to be repeated there.
     */
    val scanFailed: Boolean = false,
) {

    /** Without a barcode and title, the pass makes no sense. */
    val canSave: Boolean
        get() = barcodeData.isNotBlank() && title.isNotBlank() && !isScanning
}

/**
 * Form for creating and editing a pass.
 *
 * Three starting points: empty (manual), pre-filled from a scan ([sourceUri]), or populated
 * from an existing pass ([passId]).
 */
class PassEditorViewModel(
    private val repository: PassRepository,
    private val scanner: PassScanner,
    private val assetStore: PassAssetStore,
    private val documentSource: LocalDocumentSource,
    settingsStore: SettingsStore,
    private val passId: String?,
    sourceUri: Uri?,
) : ViewModel() {

    /**
     * Id of the edited pass - already assigned here even for new passes.
     *
     * Logo and original document are stored under this id. Without an id fixed in advance, an
     * uploaded logo could only be attributed after saving.
     */
    private val draftId: String = passId ?: UUID.randomUUID().toString()

    /** Origin for newly created passes: a scan if there's a source, otherwise manual entry. */
    private val newPassSource = if (sourceUri != null) PassSource.SCAN else PassSource.MANUAL

    /** Values without their own input field; they come from import or scan and are preserved. */
    private var logoPath: String? = null
    private var iconKey: String? = null
    private var originalFilePath: String? = null
    private var originalFileName: String? = null
    private var originalMimeType: String? = null
    private var barcodeEcc: String? = null
    private var barcodeEncoding: String? = null
    private var latitude: Double? = null
    private var longitude: Double? = null

    /**
     * Defaults for a genuinely new pass - `null` when editing an existing one, so they're
     * never applied on top of already-loaded values.
     */
    private val defaultOwnerName = settingsStore.current.defaultOwnerName
    private val defaultFolderName = settingsStore.current.defaultFolderName

    private val state = MutableStateFlow(
        PassEditorUiState(
            isScanning = sourceUri != null,
            isEditingExisting = passId != null,
            // Only for brand-new passes: an edited or scanned pass either already has its own
            // values or fills them in via [scan] right after.
            ownerName = if (passId == null) defaultOwnerName else "",
            folderName = if (passId == null) {
                defaultFolderName
            } else {
                PassEntity.DEFAULT_FOLDER
            },
        ),
    )
    val uiState: StateFlow<PassEditorUiState> = state.asStateFlow()

    init {
        // Existing folders as suggestions - this avoids duplicates caused by typos.
        viewModelScope.launch {
            repository.observeFolders().collect { folders ->
                state.update { it.copy(folderSuggestions = folders.map { f -> f.folderName }) }
            }
        }

        when {
            passId != null -> loadExisting(passId)
            sourceUri != null -> scan(sourceUri)
        }
    }

    private fun loadExisting(id: String) {
        viewModelScope.launch {
            val pass = repository.getPass(id) ?: return@launch
            val fields = repository.getFields(id)

            logoPath = pass.logoPath
            iconKey = pass.iconKey
            originalFilePath = pass.originalFilePath
            originalFileName = pass.originalFileName
            originalMimeType = pass.originalMimeType
            barcodeEcc = pass.barcodeEcc
            barcodeEncoding = pass.barcodeEncoding
            latitude = pass.locationLatitude
            longitude = pass.locationLongitude

            state.update {
                it.copy(
                    title = pass.title,
                    subtitle = pass.subtitle.orEmpty(),
                    ownerName = pass.ownerName,
                    identifier = pass.identifier.orEmpty(),
                    folderName = pass.folderName,
                    barcodeData = pass.barcodeData,
                    barcodeType = pass.barcodeType,
                    backgroundColor = pass.backgroundColor,
                    location = pass.location.orEmpty(),
                    expirationDate = pass.expirationDate,
                    startDate = pass.startDate,
                    logoFile = assetStore.resolve(pass.logoPath),
                    icon = PassIconLibrary.byKey(pass.iconKey),
                    fields = fields.map { field ->
                        EditableField(label = field.label.orEmpty(), value = field.value)
                    },
                )
            }
        }
    }

    private fun scan(uri: Uri) {
        viewModelScope.launch {
            val result = scanner.scan(uri)
            barcodeEcc = result.barcodeEcc

            // The scanned source is preserved, so it remains reachable later via "view original
            // document".
            withContext(Dispatchers.IO) {
                val bytes = documentSource.readBytes(uri)
                val name = documentSource.displayName(uri)
                if (bytes != null) {
                    originalFilePath = assetStore.saveOriginal(draftId, bytes, name)
                    originalFileName = name
                    originalMimeType = documentSource.mimeType(uri)
                }
            }

            // Derive a matching symbol from the recognized text - a train ticket thus gets a
            // train symbol without any effort. Deliberately across all recognized lines and not
            // just the title: the telling word often sits in the fine print ("Fernverkehr",
            // "Gate").
            val suggestedIcon = PassIconLibrary.suggestFor(
                result.title,
                result.identifier,
                *result.textLines.toTypedArray(),
                *result.fields.map { "${it.label} ${it.value}" }.toTypedArray(),
            )
            iconKey = suggestedIcon?.key

            state.update { current ->
                current.copy(
                    title = result.title.orEmpty(),
                    // A configured default takes priority over whatever the scan recognized -
                    // if the user has set up their own name as the default, passes almost
                    // always belong to them anyway, so a differently spelled or incomplete
                    // scan result shouldn't override that deliberate choice.
                    ownerName = defaultOwnerName.ifBlank { result.ownerName.orEmpty() },
                    identifier = result.identifier.orEmpty(),
                    barcodeData = result.barcodeData.orEmpty(),
                    barcodeType = result.barcodeType ?: current.barcodeType,
                    icon = suggestedIcon,
                    // Adopt recognized label-value pairs directly; the user can correct or
                    // delete them in the form.
                    fields = result.fields.map { field ->
                        EditableField(label = field.label, value = field.value)
                    },
                    suggestions = result.textLines,
                    isScanning = false,
                    scanFoundNoBarcode = !result.hasBarcode && !result.sourceUnreadable,
                    scanFailed = result.sourceUnreadable,
                )
            }
        }
    }

    fun updateTitle(value: String) = state.update { it.copy(title = value) }
    fun updateSubtitle(value: String) = state.update { it.copy(subtitle = value) }
    fun updateOwnerName(value: String) = state.update { it.copy(ownerName = value) }
    fun updateIdentifier(value: String) = state.update { it.copy(identifier = value) }
    fun updateFolderName(value: String) = state.update { it.copy(folderName = value) }
    fun updateBarcodeData(value: String) = state.update { it.copy(barcodeData = value) }
    fun updateBarcodeType(value: BarcodeType) = state.update { it.copy(barcodeType = value) }
    fun updateBackgroundColor(value: Int) = state.update { it.copy(backgroundColor = value) }
    fun updateLocation(value: String) = state.update { it.copy(location = value) }

    /**
     * Sets the expiration date - ignored if it would fall before [PassEditorUiState.startDate].
     *
     * The end of the validity span can never lie before its start; enforcing that here, at the
     * single source of truth, keeps the invariant valid regardless of which UI path changes the
     * date (the date picker additionally disables the invalid days so this rarely triggers).
     */
    fun updateExpirationDate(value: Long?) = state.update { current ->
        if (value != null && current.startDate != null && value < current.startDate) {
            current
        } else {
            current.copy(expirationDate = value)
        }
    }

    /**
     * Sets the start date - ignored if it would fall after [PassEditorUiState.expirationDate].
     *
     * Mirrors [updateExpirationDate]: the start of the validity span can never lie after its
     * end.
     */
    fun updateStartDate(value: Long?) = state.update { current ->
        if (value != null && current.expirationDate != null && value > current.expirationDate) {
            current
        } else {
            current.copy(startDate = value)
        }
    }

    // --- Additional fields ---

    fun addField() = state.update { it.copy(fields = it.fields + EditableField()) }

    fun updateFieldLabel(id: String, label: String) = updateField(id) { it.copy(label = label) }

    fun updateFieldValue(id: String, value: String) = updateField(id) { it.copy(value = value) }

    fun removeField(id: String) = state.update { current ->
        current.copy(fields = current.fields.filterNot { it.id == id })
    }

    private fun updateField(id: String, transform: (EditableField) -> EditableField) {
        state.update { current ->
            current.copy(
                fields = current.fields.map { field ->
                    if (field.id == id) transform(field) else field
                },
            )
        }
    }

    // --- Logo and symbol ---

    /**
     * Adopts an image chosen by the user as the logo.
     *
     * An uploaded image replaces a previously chosen symbol: saving both at once would only be
     * seemingly flexible - only one can be displayed anyway.
     */
    fun pickLogo(uri: Uri) {
        viewModelScope.launch {
            val saved = withContext(Dispatchers.IO) {
                val bitmap = documentSource.decodeImage(uri) ?: return@withContext null
                val path = assetStore.saveLogo(draftId, bitmap)
                bitmap.recycle()
                path
            } ?: return@launch

            logoPath = saved
            iconKey = null
            state.update { it.copy(logoFile = assetStore.resolve(saved), icon = null) }
        }
    }

    /** Adopts a symbol from the icon library and removes an uploaded logo. */
    fun pickIcon(icon: PassIcon) {
        assetStore.delete(logoPath)
        logoPath = null
        iconKey = icon.key
        state.update { it.copy(logoFile = null, icon = icon) }
    }

    fun removeIcon() {
        iconKey = null
        state.update { it.copy(icon = null) }
    }

    fun removeLogo() {
        assetStore.delete(logoPath)
        logoPath = null
        state.update { it.copy(logoFile = null) }
    }

    /** Saves the pass and reports the id back via [onSaved]. */
    fun save(onSaved: (String) -> Unit) {
        val current = state.value
        if (!current.canSave) return

        viewModelScope.launch {
            val existing = passId?.let { repository.getPass(it) }
            val id = repository.saveWithFields(
                pass = PassEntity(
                    id = existing?.id ?: draftId,
                    folderName = current.folderName,
                    // The form doesn't know about the star; it's set in the detail view.
                    isFavorite = existing?.isFavorite == true,
                    title = current.title,
                    subtitle = current.subtitle.takeIf { it.isNotBlank() },
                    // Without a recognized name, the title is the best available value.
                    ownerName = current.ownerName.ifBlank { current.title },
                    identifier = current.identifier.takeIf { it.isNotBlank() },
                    barcodeData = current.barcodeData,
                    barcodeType = current.barcodeType,
                    barcodeAltText = existing?.barcodeAltText,
                    barcodeEcc = barcodeEcc,
                    barcodeEncoding = barcodeEncoding,
                    backgroundColor = current.backgroundColor,
                    logoPath = logoPath,
                    iconKey = iconKey,
                    heroImagePath = existing?.heroImagePath,
                    originalFilePath = originalFilePath,
                    originalFileName = originalFileName,
                    originalMimeType = originalMimeType,
                    expirationDate = current.expirationDate,
                    startDate = current.startDate,
                    location = current.location.takeIf { it.isNotBlank() },
                    locationLatitude = latitude,
                    locationLongitude = longitude,
                    source = existing?.source ?: newPassSource,
                    createdAt = existing?.createdAt ?: System.currentTimeMillis(),
                ),
                fields = current.fields.mapIndexed { index, field ->
                    PassFieldEntity(
                        passId = draftId,
                        label = field.label.takeIf { it.isNotBlank() },
                        value = field.value,
                        position = index,
                    )
                },
            )
            onSaved(id)
        }
    }


    companion object {

        fun factory(passId: String?, sourceUri: Uri?): ViewModelProvider.Factory =
            viewModelFactory {
                initializer {
                    val app = passPortaApplication()
                    PassEditorViewModel(
                        repository = app.passRepository,
                        scanner = app.passScanner,
                        assetStore = app.passAssetStore,
                        documentSource = app.documentSource,
                        settingsStore = app.settingsStore,
                        passId = passId,
                        sourceUri = sourceUri,
                    )
                }
            }
    }
}



