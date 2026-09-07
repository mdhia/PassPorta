package org.shadowgrove.passporta.ui.detail

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Place
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import org.shadowgrove.passporta.R
import org.shadowgrove.passporta.data.local.entity.BarcodeType
import org.shadowgrove.passporta.ui.components.BarcodeImage
import org.shadowgrove.passporta.ui.components.BarcodeZoomDialog
import org.shadowgrove.passporta.ui.components.PassFieldList
import org.shadowgrove.passporta.ui.components.PassLogo
import org.shadowgrove.passporta.ui.components.PassLogoSizeOnDetail
import org.shadowgrove.passporta.ui.components.copyOnLongPress
import org.shadowgrove.passporta.ui.model.PassUi
import java.text.DateFormat
import java.util.Date

/** Horizontal margin around the content. */
private val ContentHorizontalPadding = 24.dp

/**
 * Size of the code in the detail view.
 *
 * Deliberately compact: the full-screen zoom is used for scanning, here the code shouldn't
 * crowd out the other information. 1D formats need more width to stay readable.
 */
private val MaxBarcodeWidth2D: Dp = 190.dp
private val MaxBarcodeWidth1D: Dp = 300.dp

/** Border width of the button to the original document. */
private val OutlineWidth: Dp = 1.dp

/**
 * Detail view of a pass: logo, card number, owner, additional fields and the barcode.
 *
 * The screen adopts the pass's color - the text color comes from the contrast logic.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PassDetailScreen(
    passId: String,
    onBack: () -> Unit,
    onEdit: (String) -> Unit,
    onOpenOriginal: (String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: PassDetailViewModel = viewModel(
        key = passId,
        factory = PassDetailViewModel.factory(passId),
    ),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val pass = state.pass

    // After deletion (or if the id no longer exists), go back to the overview.
    LaunchedEffect(state.isLoading, pass) {
        if (!state.isLoading && pass == null) onBack()
    }

    var showDeleteDialog by remember { mutableStateOf(false) }
    var showZoom by remember { mutableStateOf(false) }

    // Setting "open barcode immediately": applies once, as soon as the pass is loaded.
    // Deliberately tied to the pass id and not to `Unit` - otherwise the effect would stay
    // dormant after closing the full screen when the user switches to a different pass.
    val openFullscreen = state.openBarcodeFullscreen
    LaunchedEffect(passId, pass != null, openFullscreen) {
        if (openFullscreen && pass != null) showZoom = true
    }

    Scaffold(
        modifier = modifier,
        containerColor = pass?.palette?.background ?: MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = {},
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent,
                    navigationIconContentColor = pass?.palette?.content
                        ?: MaterialTheme.colorScheme.onBackground,
                    actionIconContentColor = pass?.palette?.content
                        ?: MaterialTheme.colorScheme.onBackground,
                ),
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.detail_back),
                        )
                    }
                },
                actions = {
                    if (pass != null) {
                        IconButton(onClick = viewModel::toggleFavorite) {
                            Icon(
                                imageVector = if (pass.isFavorite) {
                                    Icons.Default.Favorite
                                } else {
                                    Icons.Default.FavoriteBorder
                                },
                                contentDescription = stringResource(
                                    if (pass.isFavorite) {
                                        R.string.detail_favorite_remove
                                    } else {
                                        R.string.detail_favorite_add
                                    },
                                ),
                            )
                        }
                        IconButton(onClick = { onEdit(pass.id) }) {
                            Icon(
                                imageVector = Icons.Default.Edit,
                                contentDescription = stringResource(R.string.detail_edit),
                            )
                        }
                        IconButton(onClick = { showDeleteDialog = true }) {
                            Icon(
                                imageVector = Icons.Default.Delete,
                                contentDescription = stringResource(R.string.detail_delete),
                            )
                        }
                    }
                },
            )
        },
    ) { innerPadding ->
        if (pass != null) {
            PassDetailContent(
                pass = pass,
                onBarcodeClick = { showZoom = true },
                onOpenOriginal = { onOpenOriginal(pass.id) },
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
            )
        }
    }

    if (showZoom && pass != null) {
        BarcodeZoomDialog(pass = pass, onDismiss = { showZoom = false })
    }

    if (showDeleteDialog && pass != null) {
        DeleteConfirmationDialog(
            passTitle = pass.title,
            onConfirm = {
                showDeleteDialog = false
                viewModel.delete()
            },
            onDismiss = { showDeleteDialog = false },
        )
    }
}

@Composable
private fun PassDetailContent(
    pass: PassUi,
    onBarcodeClick: () -> Unit,
    onOpenOriginal: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .verticalScroll(rememberScrollState())
            .padding(horizontal = ContentHorizontalPadding),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        PassLogo(pass = pass, size = PassLogoSizeOnDetail)

        if (pass.isUpcoming) {
            UpcomingBadge(pass = pass, modifier = Modifier.padding(top = 12.dp))
        } else if (pass.isExpired) {
            ExpiredBadge(pass = pass, modifier = Modifier.padding(top = 12.dp))
        }

        if (pass.identifier != null) {
            Text(
                text = pass.identifier,
                style = MaterialTheme.typography.labelLarge,
                color = pass.palette.secondaryContent,
                textAlign = TextAlign.Center,
                // The card number is the most frequently used value - long press copies it.
                modifier = Modifier
                    .padding(top = 20.dp)
                    .copyOnLongPress(value = pass.identifier),
            )
        }

        Text(
            text = pass.ownerName,
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            color = pass.palette.content,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 6.dp),
        )

        if (pass.subtitle != null) {
            Text(
                text = pass.subtitle,
                style = MaterialTheme.typography.bodyLarge,
                color = pass.palette.secondaryContent,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 8.dp),
            )
        }

        BarcodePanel(
            pass = pass,
            onClick = onBarcodeClick,
            modifier = Modifier.padding(top = 28.dp),
        )

        MetaRows(pass = pass, modifier = Modifier.padding(top = 16.dp))

        PassFieldList(
            fields = pass.fields,
            palette = pass.palette,
            modifier = Modifier.padding(top = 16.dp),
        )

        if (pass.originalFile != null) {
            OutlinedButton(
                onClick = onOpenOriginal,
                // Without these colors, Material would draw the button in the theme's primary
                // color - on a colored pass it would then be barely readable.
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = pass.palette.content,
                ),
                border = BorderStroke(
                    width = OutlineWidth,
                    color = pass.palette.secondaryContent,
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 20.dp),
            ) {
                Text(stringResource(R.string.detail_open_original))
            }
        }

        Spacer(modifier = Modifier.height(32.dp))
    }
}

/** Location and expiration date - both optional. */
@Composable
private fun MetaRows(pass: PassUi, modifier: Modifier = Modifier) {
    if (!pass.hasLocation && pass.expirationDate == null && pass.startDate == null) return

    val context = LocalContext.current

    Column(modifier = modifier.fillMaxWidth()) {
        if (pass.hasLocation) {
            MetaRow(
                icon = Icons.Default.Place,
                iconDescription = stringResource(R.string.detail_location),
                text = pass.location ?: formatCoordinates(pass).orEmpty(),
                pass = pass,
                onClick = { openInMaps(context, pass) },
            )
        }

        // Combined into a single line - "valid from X to Y" reads as one span, not as two
        // unrelated facts stacked on top of each other.
        DateRangeRow(pass = pass)
    }
}

/** Single combined line for start and/or expiration date - never two separate rows. */
@Composable
private fun DateRangeRow(pass: PassUi, modifier: Modifier = Modifier) {
    // The start date is only worth mentioning while it still lies in the future; once passed,
    // it adds no information beyond what the expiration date already says.
    val showStart = pass.startDate != null && pass.isUpcoming
    val showEnd = pass.expirationDate != null
    if (!showStart && !showEnd) return

    val text = when {
        showStart && showEnd -> stringResource(
            R.string.detail_valid_range,
            formatDate(pass.startDate!!),
            formatDate(pass.expirationDate!!),
        )

        showStart -> stringResource(R.string.detail_valid_from, formatDate(pass.startDate!!))

        else -> stringResource(
            if (pass.isExpired) R.string.detail_expired_on else R.string.detail_valid_until,
            formatDate(pass.expirationDate!!),
        )
    }

    MetaRow(
        icon = Icons.Default.DateRange,
        iconDescription = null,
        text = text,
        pass = pass,
        onClick = null,
    )
}

@Composable
private fun formatDate(millis: Long): String = remember(millis) {
    DateFormat.getDateInstance(DateFormat.LONG).format(Date(millis))
}

@Composable
private fun MetaRow(
    icon: ImageVector,
    iconDescription: String?,
    text: String,
    pass: PassUi,
    onClick: (() -> Unit)?,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .let { base -> if (onClick != null) base.clickable(onClick = onClick) else base }
            .padding(vertical = 8.dp),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = iconDescription,
            tint = pass.palette.secondaryContent,
        )
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = pass.palette.secondaryContent,
        )
    }
}

@Composable
private fun ExpiredBadge(pass: PassUi, modifier: Modifier = Modifier) {
    Text(
        text = stringResource(R.string.detail_expired_badge),
        style = MaterialTheme.typography.labelLarge,
        color = pass.palette.background,
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(pass.palette.content)
            .padding(horizontal = 10.dp, vertical = 4.dp),
    )
}

@Composable
private fun UpcomingBadge(pass: PassUi, modifier: Modifier = Modifier) {
    Text(
        text = stringResource(R.string.detail_upcoming_badge),
        style = MaterialTheme.typography.labelLarge,
        color = pass.palette.background,
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(pass.palette.content)
            .padding(horizontal = 10.dp, vertical = 4.dp),
    )
}

/**
 * Barcode on a white background.
 *
 * Scanners expect dark modules on a light background—so the spot color must not
 * show through here. Tapping opens zoom mode at full brightness.
 */
@Composable
private fun BarcodePanel(
    pass: PassUi,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(Color.White)
            .clickable(onClick = onClick),
    ) {
        val maxAllowed = when (pass.barcodeType) {
            BarcodeType.QR, BarcodeType.AZTEC -> MaxBarcodeWidth2D
            BarcodeType.PDF417, BarcodeType.CODE128, BarcodeType.ITF -> MaxBarcodeWidth1D
        }
        val barcodeWidth = minOf(maxWidth - 32.dp, maxAllowed)

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            BarcodeImage(
                pass = pass,
                width = barcodeWidth,
                accessibilityLabel = stringResource(R.string.detail_barcode, pass.title),
            )

            val caption = pass.barcodeAltText ?: pass.identifier
            if (caption != null) {
                Text(
                    text = caption,
                    style = MaterialTheme.typography.labelMedium,
                    color = Color.Black,
                    textAlign = TextAlign.Center,
                )
            }

            Text(
                text = stringResource(R.string.zoom_hint),
                style = MaterialTheme.typography.labelSmall,
                color = Color.Gray,
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
private fun DeleteConfirmationDialog(
    passTitle: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.detail_delete)) },
        text = { Text(passTitle) },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(stringResource(R.string.detail_delete))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(android.R.string.cancel))
            }
        },
    )
}

private fun formatCoordinates(pass: PassUi): String? {
    val latitude = pass.locationLatitude ?: return null
    val longitude = pass.locationLongitude ?: return null
    return "%.5f, %.5f".format(latitude, longitude)
}

/**
 * Opens the location in a maps app.
 *
 * The `geo:` scheme is a pure display intent: PassPorta needs neither a permission nor network
 * access for it - whether the target app goes online is entirely up to the user.
 */
private fun openInMaps(context: Context, pass: PassUi) {
    val latitude = pass.locationLatitude
    val longitude = pass.locationLongitude
    val query = pass.location?.takeIf { it.isNotBlank() }

    val uri = when {
        latitude != null && longitude != null && query != null ->
            "geo:$latitude,$longitude?q=${Uri.encode(query)}".toUri()

        latitude != null && longitude != null -> "geo:$latitude,$longitude".toUri()
        query != null -> "geo:0,0?q=${Uri.encode(query)}".toUri()
        else -> return
    }

    try {
        context.startActivity(Intent(Intent.ACTION_VIEW, uri))
    } catch (_: ActivityNotFoundException) {
        Toast.makeText(context, R.string.detail_no_maps_app, Toast.LENGTH_SHORT).show()
    }
}













