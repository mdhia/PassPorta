package org.shadowgrove.passporta.ui.overview

import android.net.Uri
import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.shadowgrove.passporta.data.importer.PassAssetStore
import org.shadowgrove.passporta.data.importer.PassImportResult
import org.shadowgrove.passporta.data.importer.PassImporter
import org.shadowgrove.passporta.data.local.entity.PassEntity
import org.shadowgrove.passporta.data.local.entity.isExpired
import org.shadowgrove.passporta.data.repository.PassRepository
import org.shadowgrove.passporta.data.settings.SettingsStore
import org.shadowgrove.passporta.ui.model.FolderFilter
import org.shadowgrove.passporta.ui.model.PassUi
import org.shadowgrove.passporta.ui.model.toUi
import org.shadowgrove.passporta.ui.passPortaApplication

/**
 * A page of the overview - corresponds to an entry of the folder selection.
 *
 * The passes are deliberately kept per page and not just for the active folder: while swiping,
 * the neighboring page is already visible before the selection switches. With only one pass
 * list, the old content would briefly appear there and only change after release.
 */
@Immutable
data class FolderPage(
    val filter: FolderFilter,
    val passes: List<PassUi>,
) {

    val isEmpty: Boolean get() = passes.isEmpty()

    val isArchive: Boolean get() = filter == FolderFilter.Archive
}

@Immutable
data class OverviewUiState(

    /**
     * All selectable pages in swipe order: favorites (if any), then "All", then the folders,
     * finally the archive.
     *
     * The folder bar and the pager draw from the same list - a separate folder list in the
     * state could diverge from it.
     */
    val pages: List<FolderPage> = emptyList(),

    val selectedFolder: FolderFilter = FolderFilter.All,

    val isLoading: Boolean = true,

    /** Current search input; empty means: normal folder view. */
    val query: String = "",

    /** Search results - across folders, including expired passes. */
    val results: List<PassUi> = emptyList(),
) {

    /** Position of the active folder; -1 as long as nothing is loaded. */
    val selectedIndex: Int get() = pages.indexOfFirst { it.filter == selectedFolder }

    val isSearching: Boolean get() = query.isNotBlank()
}

/**
 * State of the overview: pages of the folder bar, filtered passes, and the import via the
 * document picker.
 *
 * The split into valid and expired passes deliberately happens in memory instead of via SQL: a
 * query with `expiration_date < :now` would freeze the point in time when the flow is created
 * and only re-evaluate on the next database write. At the usual scale of a few dozen passes,
 * filtering in Kotlin is negligible anyway.
 *
 * The entire state arises from a single data flow. There is deliberately no second observation
 * of the passes that corrects the filter afterwards: it would have to replicate the same rules
 * a second time and could diverge from them.
 */
class PassOverviewViewModel(
    private val repository: PassRepository,
    private val importer: PassImporter,
    private val assetStore: PassAssetStore,
    settingsStore: SettingsStore,
) : ViewModel() {

    /**
     * Deliberate user choice; `null` means "none made yet".
     *
     * The starting folder is only determined once the passes are loaded: if there are
     * favorites, it's the favorites page, otherwise "All". With a fixed starting value, the two
     * couldn't be distinguished - a later correction would make the overview visibly jump.
     */
    private val selectedFolder = MutableStateFlow<FolderFilter?>(null)

    private val query = MutableStateFlow("")

    /** One-off messages (snackbar) - deliberately not part of the state. */
    private val importResultChannel = Channel<PassImportResult>(Channel.BUFFERED)
    val importResults: Flow<PassImportResult> = importResultChannel.receiveAsFlow()

    val uiState: StateFlow<OverviewUiState> = combine(
        repository.observePasses(),
        selectedFolder,
        query,
        settingsStore.settings,
    ) { entities, filter, searchQuery, settings ->
        val now = System.currentTimeMillis()
        val (archived, active) = entities.partition { it.isExpired(now) }

        // Setting "show expired in folders": the archive still exists, the expired passes
        // additionally show up in their original folder.
        val inFolders = if (settings.showExpiredInFolders) entities else active

        // Favorites follow the same rule as folders: an expired favorite belongs in the
        // archive, not on the start page - unless the user explicitly wants it otherwise.
        val favorites = inFolders.filter { it.isFavorite }

        // Only the names, not the full folder summary: the chips show no counters, and the
        // passes per folder are already directly on the pages anyway.
        val folderNames = inFolders
            .map { it.folderName }
            .distinct()
            .sortedBy { it.lowercase() }

        val pages = buildList {
            // Favorites come first: they are the starting folder, so everything else moves
            // back by one page.
            if (favorites.isNotEmpty()) {
                add(FolderPage(FolderFilter.Favorites, favorites.toUiPasses(now)))
            }
            add(FolderPage(FolderFilter.All, inFolders.toUiPasses(now)))
            folderNames.forEach { name ->
                add(
                    FolderPage(
                        filter = FolderFilter.Named(name),
                        passes = inFolders.filter { it.folderName == name }.toUiPasses(now),
                    ),
                )
            }
            // The archive is deliberately at the end - expired passes are the special case.
            if (archived.isNotEmpty()) {
                add(FolderPage(FolderFilter.Archive, archived.toUiPasses(now)))
            }
        }

        // The chosen filter can lose its target - the last pass of a folder gets deleted, the
        // archive empties, the last favorite expires. Instead of tracking that in several
        // places, it's checked here against the actually built pages: this way no filter can
        // survive without a page. The default is always present, because the favorites page
        // arises under exactly the same condition as `defaultFilter`.
        val fallback = defaultFilter(hasFavorites = favorites.isNotEmpty())
        val requested = filter ?: fallback
        val selected = if (pages.any { it.filter == requested }) requested else fallback

        OverviewUiState(
            pages = pages,
            selectedFolder = selected,
            isLoading = false,
            query = searchQuery,
            // The search deliberately spans all passes including the archive: someone searching
            // specifically wants to find the pass - regardless of which folder it's in or
            // whether it has already expired.
            results = if (searchQuery.isBlank()) {
                emptyList()
            } else {
                entities.filter { it.matches(searchQuery) }.toUiPasses(now)
            },
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
        initialValue = OverviewUiState(),
    )

    fun selectFolder(filter: FolderFilter) {
        selectedFolder.value = filter
    }

    fun updateQuery(value: String) {
        query.value = value
    }

    fun clearQuery() {
        query.value = ""
    }

    /**
     * Free-text search over the visible fields of a pass.
     *
     * The barcode payload deliberately stays out: it's often a long string with random digit
     * sequences and would produce a flood of false positives for short inputs.
     */
    private fun PassEntity.matches(query: String): Boolean {
        val needle = query.trim().lowercase()
        if (needle.isEmpty()) return false

        return listOfNotNull(title, subtitle, ownerName, identifier, folderName, location)
            .any { it.lowercase().contains(needle) }
    }

    /** Imports a file selected by the user (document picker behind the FAB). */
    fun importFrom(uri: Uri) {
        viewModelScope.launch {
            importResultChannel.send(importer.importFromUri(uri))
        }
    }

    /** Starting page without an own choice: favorites, as soon as there are any. */
    private fun defaultFilter(hasFavorites: Boolean): FolderFilter =
        if (hasFavorites) FolderFilter.Favorites else FolderFilter.All


    private fun List<PassEntity>.toUiPasses(now: Long): List<PassUi> =
        map { it.toUi(assetStore, now = now) }
            .sortedWith(compareBy({ it.folderName.lowercase() }, { it.title.lowercase() }))

    companion object {

        private const val STOP_TIMEOUT_MILLIS = 5_000L

        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = passPortaApplication()
                PassOverviewViewModel(
                    repository = app.passRepository,
                    importer = app.passImporter,
                    assetStore = app.passAssetStore,
                    settingsStore = app.settingsStore,
                )
            }
        }
    }
}









