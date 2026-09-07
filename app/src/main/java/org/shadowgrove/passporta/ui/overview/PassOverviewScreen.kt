package org.shadowgrove.passporta.ui.overview

import android.content.ActivityNotFoundException
import android.content.Intent
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.InsertDriveFile
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Cake
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Draw
import androidx.compose.material.icons.filled.DocumentScanner
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FabPosition
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarVisuals
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.launch
import org.shadowgrove.passporta.R
import org.shadowgrove.passporta.ui.components.rememberDrawablePainter
import org.shadowgrove.passporta.ui.model.FolderFilter
import org.shadowgrove.passporta.ui.model.PassUi

/** Minimum width of a card; determines the number of columns on large displays. */
private val MinCardWidth = 200.dp

/** Even on narrow devices, at least two cards should stand side by side. */
private const val MIN_COLUMNS = 2

private val GridSpacing = 12.dp

/** Space below the last row of cards, so the FAB doesn't cover anything. */
private val GridBottomPadding = 96.dp

/** Dimensions of the folder bar below the header. */
private val ChipRowHorizontalPadding = 16.dp
private val ChipSpacing = 8.dp
private val ChipRowBottomPadding = 8.dp

/**
 * Overview of all passes.
 *
 * Header: app icon on the left, current folder in the center, below it the folders as
 * scrollable chips. Content: adaptive grid, swipeable sideways to change folder. Footer: FAB at
 * the bottom right with the four ways to create a pass.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PassOverviewScreen(
    onPassClick: (String) -> Unit,
    onImportPkPass: () -> Unit,
    onScanSource: () -> Unit,
    onCapturePhoto: () -> Unit,
    onCreateManually: () -> Unit,
    onOpenSettings: () -> Unit,
    snackbarHostState: SnackbarHostState,
    modifier: Modifier = Modifier,
    viewModel: PassOverviewViewModel = viewModel(factory = PassOverviewViewModel.Factory),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var showAddSheet by remember { mutableStateOf(false) }
    var showSearch by rememberSaveable { mutableStateOf(false) }

    var logoTaps by remember { mutableIntStateOf(0) }
    var cakeRevealed by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val cakeMessage = stringResource(R.string.easter_egg_cake_message)

    val onLogoClick: () -> Unit = onLogoClick@{
        logoTaps++
        if (logoTaps < 3) return@onLogoClick
        logoTaps = 0

        if (!cakeRevealed) {
            cakeRevealed = true
            coroutineScope.launch {
                snackbarHostState.showSnackbar(CakeSnackbarVisuals(cakeMessage))
            }
        } else {
            cakeRevealed = false
            try {
                context.startActivity(
                    Intent(Intent.ACTION_VIEW, "https://youtu.be/6ug6Bbc6diA?t=36".toUri()),
                )
            } catch (_: ActivityNotFoundException) {
                // Skip
            }
        }
    }

    val pages = state.pages
    val selectedIndex = state.selectedIndex.coerceAtLeast(0)
    val pagerState = rememberPagerState(
        initialPage = selectedIndex,
        pageCount = { pages.size },
    )

    // Transfer the menu selection to the pager.
    LaunchedEffect(selectedIndex, pages.size) {
        if (pages.isNotEmpty() && pagerState.currentPage != selectedIndex) {
            pagerState.animateScrollToPage(selectedIndex)
        }
    }

    // And vice versa: a completed swipe reports the new folder back. Deliberately
    // `settledPage` instead of `currentPage` - otherwise half a swipe gesture would already
    // switch the filter, and an aborted swipe would change the selection.
    //
    // The effect depends only on the pager, not on the pages: if every database change
    // restarted it, `snapshotFlow` would immediately report the current page - overwriting a
    // selection just made in the menu whose animation is still running.
    val currentPages by rememberUpdatedState(pages)
    LaunchedEffect(pagerState) {
        snapshotFlow { pagerState.settledPage }.collect { page ->
            currentPages.getOrNull(page)?.filter?.let(viewModel::selectFolder)
        }
    }

    Scaffold(
        modifier = modifier,
        topBar = {
            if (state.isSearching || showSearch) {
                SearchBar(
                    query = state.query,
                    onQueryChange = viewModel::updateQuery,
                    onClose = {
                        showSearch = false
                        viewModel.clearQuery()
                    },
                )
            } else {
                // Header and folder bar together form the `topBar` area, so the scaffold keeps
                // them clear of the content together.
                Column(modifier = Modifier.background(MaterialTheme.colorScheme.surface)) {
                    CenterAlignedTopAppBar(
                        navigationIcon = { AppIcon(onClick = onLogoClick) },
                        title = { FolderTitle(selectedFolder = state.selectedFolder) },
                        actions = {
                            IconButton(onClick = { showSearch = true }) {
                                Icon(
                                    imageVector = Icons.Default.Search,
                                    contentDescription = stringResource(R.string.overview_search),
                                )
                            }
                            IconButton(onClick = onOpenSettings) {
                                Icon(
                                    imageVector = Icons.Default.Settings,
                                    contentDescription = stringResource(R.string.settings_title),
                                )
                            }
                        },
                    )
                    FolderChips(
                        pages = pages,
                        selectedIndex = state.selectedIndex,
                        onSelect = viewModel::selectFolder,
                    )
                }
            }
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { showAddSheet = true }) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = stringResource(R.string.overview_add_pass),
                )
            }
        },
        floatingActionButtonPosition = FabPosition.End,
        snackbarHost = {
            SnackbarHost(snackbarHostState) { data ->
                val visuals = data.visuals
                if (visuals is CakeSnackbarVisuals) {
                    Snackbar {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(imageVector = Icons.Default.Cake, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(visuals.message)
                        }
                    }
                } else {
                    Snackbar(data)
                }
            }
        },
    ) { innerPadding ->
        // As long as nothing is loaded, the area stays empty: a loading indicator would only
        // flash briefly for a local database with few images.
        if (state.isLoading) return@Scaffold

        val contentModifier = Modifier
            .fillMaxSize()
            .padding(innerPadding)

        // During a search, the folder split takes a back seat: search spans all passes, and
        // swiping between folders would only be confusing here.
        if (state.isSearching) {
            if (state.results.isEmpty()) {
                NoSearchResults(query = state.query, modifier = contentModifier)
            } else {
                PassGrid(
                    passes = state.results,
                    onPassClick = onPassClick,
                    modifier = contentModifier,
                )
            }
            return@Scaffold
        }

        HorizontalPager(
            state = pagerState,
            modifier = contentModifier,
        ) { index ->
            val page = pages.getOrNull(index) ?: return@HorizontalPager

            if (page.isEmpty) {
                EmptyState(
                    isArchive = page.isArchive,
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                PassGrid(passes = page.passes, onPassClick = onPassClick)
            }
        }
    }

    if (showAddSheet) {
        AddPassSheet(
            onDismiss = { showAddSheet = false },
            onImportPkPass = {
                showAddSheet = false
                onImportPkPass()
            },
            onCapturePhoto = {
                showAddSheet = false
                onCapturePhoto()
            },
            onScanSource = {
                showAddSheet = false
                onScanSource()
            },
            onCreateManually = {
                showAddSheet = false
                onCreateManually()
            },
        )
    }
}

/** Adaptive grid of the passes of a folder. */
@Composable
private fun PassGrid(
    passes: List<PassUi>,
    onPassClick: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyVerticalGrid(
        columns = remember { AdaptiveGridCells(MinCardWidth, MIN_COLUMNS) },
        contentPadding = PaddingValues(
            start = GridSpacing,
            end = GridSpacing,
            top = GridSpacing,
            bottom = GridBottomPadding,
        ),
        horizontalArrangement = Arrangement.spacedBy(GridSpacing),
        verticalArrangement = Arrangement.spacedBy(GridSpacing),
        modifier = modifier.fillMaxSize(),
    ) {
        items(items = passes, key = { it.id }) { pass ->
            PassCard(pass = pass, onClick = { onPassClick(pass.id) })
        }
    }
}

/**
 * Header in search mode.
 *
 * Fully replaces the folder selection instead of appearing additionally below it: during a
 * search the active folder is meaningless, and the list gets the full height.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SearchBar(
    query: String,
    onQueryChange: (String) -> Unit,
    onClose: () -> Unit,
) {
    val focusRequester = remember { FocusRequester() }

    // The keyboard should appear without an extra tap - the user just deliberately opened the
    // search.
    LaunchedEffect(Unit) { focusRequester.requestFocus() }

    TopAppBar(
        navigationIcon = {
            IconButton(onClick = onClose) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = stringResource(R.string.detail_back),
                )
            }
        },
        title = {
            TextField(
                value = query,
                onValueChange = onQueryChange,
                placeholder = { Text(stringResource(R.string.overview_search_hint)) },
                singleLine = true,
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = Color.Transparent,
                    unfocusedContainerColor = Color.Transparent,
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent,
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(focusRequester),
            )
        },
        actions = {
            if (query.isNotEmpty()) {
                IconButton(onClick = { onQueryChange("") }) {
                    Icon(
                        imageVector = Icons.Default.Clear,
                        contentDescription = stringResource(R.string.overview_search_clear),
                    )
                }
            }
        },
    )
}

@Composable
private fun NoSearchResults(query: String, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = stringResource(R.string.overview_search_empty_title),
            style = MaterialTheme.typography.titleMedium,
        )
        Text(
            text = stringResource(R.string.overview_search_empty_message, query),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp),
        )
    }
}

/** Source selection when adding. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddPassSheet(
    onDismiss: () -> Unit,
    onImportPkPass: () -> Unit,
    onCapturePhoto: () -> Unit,
    onScanSource: () -> Unit,
    onCreateManually: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(modifier = Modifier.padding(bottom = 32.dp)) {
            ListItem(
                headlineContent = { Text(stringResource(R.string.add_pkpass_title)) },
                supportingContent = { Text(stringResource(R.string.add_pkpass_subtitle)) },
                leadingContent = { Icon(Icons.AutoMirrored.Filled.InsertDriveFile, contentDescription = null) },
                modifier = Modifier.clickable(onClick = onImportPkPass),
            )
            ListItem(
                headlineContent = { Text(stringResource(R.string.add_camera_title)) },
                supportingContent = { Text(stringResource(R.string.add_camera_subtitle)) },
                leadingContent = { Icon(Icons.Default.PhotoCamera, contentDescription = null) },
                modifier = Modifier.clickable(onClick = onCapturePhoto),
            )
            ListItem(
                headlineContent = { Text(stringResource(R.string.add_scan_title)) },
                supportingContent = { Text(stringResource(R.string.add_scan_subtitle)) },
                leadingContent = { Icon(Icons.Default.DocumentScanner, contentDescription = null) },
                modifier = Modifier.clickable(onClick = onScanSource),
            )
            ListItem(
                headlineContent = { Text(stringResource(R.string.add_manual_title)) },
                supportingContent = { Text(stringResource(R.string.add_manual_subtitle)) },
                leadingContent = { Icon(Icons.Default.Draw, contentDescription = null) },
                modifier = Modifier.clickable(onClick = onCreateManually),
            )
        }
    }
}

/**
 * Like [GridCells.Adaptive], but enforces a minimum column count.
 *
 * `GridCells.Adaptive` alone would fall back to one column on very narrow displays - but the
 * overview should always stay two-column.
 */
private data class AdaptiveGridCells(
    private val minSize: Dp,
    private val minColumns: Int,
) : GridCells {

    override fun Density.calculateCrossAxisCellSizes(
        availableSize: Int,
        spacing: Int,
    ): List<Int> {
        val fitting = (availableSize + spacing) / (minSize.roundToPx() + spacing)
        val columns = maxOf(minColumns, fitting)

        // Distribute remaining pixels evenly across the leading columns - otherwise a gap
        // remains on the right.
        val usable = availableSize - spacing * (columns - 1)
        val cellSize = usable / columns
        val remainder = usable % columns
        return List(columns) { index -> cellSize + if (index < remainder) 1 else 0 }
    }
}

/** Size of the app icon in the header. */
private val AppIconSize = 32.dp

@Composable
private fun AppIcon(onClick: () -> Unit = {}, modifier: Modifier = Modifier) {
    // Launcher icons are adaptive icons from API 26 and cannot be loaded via painterResource.
    val painter = rememberDrawablePainter(R.mipmap.ic_launcher_round, AppIconSize)

    val iconModifier = modifier
        .padding(start = 12.dp)
        .size(AppIconSize)
        .clip(CircleShape)
        .clickable(onClick = onClick)

    if (painter != null) {
        Image(
            painter = painter,
            contentDescription = stringResource(R.string.overview_app_icon),
            // Adaptive icons draw beyond the visible area - the mask sits here.
            modifier = iconModifier,
        )
    } else {
        // Reserve space so the centered title doesn't jump.
        Spacer(modifier = iconModifier)
    }
}

/** Visuals for the cake easter egg snackbar - rendered with a cake icon instead of plain text. */
private data class CakeSnackbarVisuals(override val message: String) : SnackbarVisuals {
    override val actionLabel: String? = null
    override val withDismissAction: Boolean = false
    override val duration: SnackbarDuration = SnackbarDuration.Short
}

/** Title of the header: the currently visible folder. Changed via the chips below. */
@Composable
private fun FolderTitle(selectedFolder: FolderFilter, modifier: Modifier = Modifier) {
    Text(
        text = selectedFolder.label(),
        style = MaterialTheme.typography.titleLarge,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = modifier,
    )
}

/**
 * Folder bar as horizontally scrollable chips.
 *
 * The chips come directly from the pager's pages and not from a separate list: this way order
 * and selection cannot drift apart when a folder is added or removed.
 */
@Composable
private fun FolderChips(
    pages: List<FolderPage>,
    selectedIndex: Int,
    onSelect: (FolderFilter) -> Unit,
    modifier: Modifier = Modifier,
) {
    // With only one page, there'd be nothing to choose - a single chip would be purely
    // decorative.
    if (pages.size <= 1) return

    val listState = rememberLazyListState()

    // While swiping, the bar moves along, otherwise the active chip would run out of view.
    LaunchedEffect(selectedIndex) {
        if (selectedIndex >= 0) listState.animateScrollToItem(selectedIndex)
    }

    LazyRow(
        state = listState,
        contentPadding = PaddingValues(horizontal = ChipRowHorizontalPadding),
        horizontalArrangement = Arrangement.spacedBy(ChipSpacing),
        modifier = modifier
            .fillMaxWidth()
            .padding(bottom = ChipRowBottomPadding),
    ) {
        // The key must be a simple type - `FolderFilter` itself can't be saved. `toString()`
        // is unique per filter, including for named folders.
        itemsIndexed(pages, key = { _, page -> page.filter.toString() }) { index, page ->
            val icon = page.filter.leadingIcon()
            FilterChip(
                selected = index == selectedIndex,
                onClick = { onSelect(page.filter) },
                label = {
                    Text(
                        text = page.filter.label(),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
                // Deliberately if/else instead of `icon?.let { { ... } }`: with nested lambdas,
                // type inference easily loses the `@Composable` marker otherwise.
                leadingIcon = if (icon != null) {
                    {
                        Icon(
                            imageVector = icon,
                            contentDescription = null,
                            modifier = Modifier.size(FilterChipDefaults.IconSize),
                        )
                    }
                } else {
                    null
                },
            )
        }
    }
}

/** Display name of a filter; named folders carry their own name. */
@Composable
private fun FolderFilter.label(): String = when (this) {
    FolderFilter.Favorites -> stringResource(R.string.overview_favorites)
    FolderFilter.All -> stringResource(R.string.overview_all_folders)
    FolderFilter.Archive -> stringResource(R.string.overview_archive)
    is FolderFilter.Named -> name
}

/**
 * Only favorites get a symbol.
 *
 * Deliberately an icon from the core set: the folder bar is the most-used area of the app and
 * shouldn't depend on the optional icon library.
 */
private fun FolderFilter.leadingIcon(): ImageVector? =
    if (this == FolderFilter.Favorites) Icons.Default.Favorite else null

@Composable
private fun EmptyState(isArchive: Boolean, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = stringResource(
                if (isArchive) R.string.overview_archive_empty_title else R.string.overview_empty_title,
            ),
            style = MaterialTheme.typography.titleMedium,
        )
        Text(
            text = stringResource(
                if (isArchive) {
                    R.string.overview_archive_empty_message
                } else {
                    R.string.overview_empty_message
                },
            ),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp),
        )
    }
}










