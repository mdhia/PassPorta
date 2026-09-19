package org.shadowgrove.passporta.ui

import android.content.ActivityNotFoundException
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import org.shadowgrove.passporta.R
import org.shadowgrove.passporta.data.importer.CameraCapture
import org.shadowgrove.passporta.ui.detail.PassDetailScreen
import org.shadowgrove.passporta.ui.document.PassDocumentScreen
import org.shadowgrove.passporta.ui.editor.PassEditorScreen
import org.shadowgrove.passporta.ui.editor.PassEditorViewModel
import org.shadowgrove.passporta.ui.overview.PassOverviewScreen
import org.shadowgrove.passporta.ui.overview.PassOverviewViewModel
import org.shadowgrove.passporta.ui.pdfimport.PdfPreviewScreen
import org.shadowgrove.passporta.ui.settings.SettingsScreen

/** Starting destination: overview of all passes. */
@Serializable
data object OverviewRoute

/** Detail view of a pass. */
@Serializable
data class PassDetailRoute(val passId: String)

/** Display of a pass's preserved original document. */
@Serializable
data class PassDocumentRoute(val passId: String)

/**
 * Preview of a PDF handed to PassPorta by another app, before it is imported.
 *
 * [uri] is encoded for the same reason as [PassEditorRoute.sourceUri].
 */
@Serializable
data class PdfPreviewRoute(val uri: String)

/** App settings. */
@Serializable
data object SettingsRoute

/**
 * Form for creating or editing.
 *
 * [sourceUri] is the source of a scan (image/PDF), [passId] an existing pass. Both `null`
 * means: create manually.
 *
 * The URI is encoded upfront, so special characters from `content://` addresses don't break the
 * route; navigation encodes again and decodes once, leaving exactly one decoding here.
 */
@Serializable
data class PassEditorRoute(
    val sourceUri: String? = null,
    val passId: String? = null,
)

/**
 * MIME types for the document picker behind the FAB.
 *
 * Many file apps expose `.pkpass` with a generic type, so ZIP and binary stream are also
 * allowed - the parser itself detects whether a valid archive is present.
 */
private val PKPASS_MIME_TYPES = arrayOf(
    "application/vnd.apple.pkpass",
    "application/vnd.apple.pkpasses",
    "application/zip",
    "application/octet-stream",
)

/** Images and PDFs for offline extraction via ML Kit. */
private val SCAN_MIME_TYPES = arrayOf("image/*", "application/pdf")

/**
 * Navigation scaffold of the app.
 *
 * Uses type-safe routes (`@Serializable`), so destinations and arguments are checked by the
 * compiler.
 */
@Composable
fun PassPortaApp(
    modifier: Modifier = Modifier,
    pendingPdfUri: Uri? = null,
    onPdfUriConsumed: () -> Unit = {},
) {
    val navController = rememberNavController()
    val snackbarHostState = remember { SnackbarHostState() }

    // A PDF handed in from ImportActivity is a one-shot deep link: navigate to its preview once,
    // then let the caller clear it so a later recomposition doesn't navigate again.
    LaunchedEffect(pendingPdfUri) {
        pendingPdfUri?.let { uri ->
            navController.navigate(PdfPreviewRoute(uri = Uri.encode(uri.toString())))
            onPdfUriConsumed()
        }
    }

    NavHost(
        navController = navController,
        startDestination = OverviewRoute,
        modifier = modifier,
    ) {
        composable<OverviewRoute> {
            val viewModel: PassOverviewViewModel =
                viewModel(factory = PassOverviewViewModel.Factory)

            val context = LocalContext.current
            val scope = rememberCoroutineScope()
            val resources = LocalResources.current

            LaunchedEffect(viewModel, resources) {
                viewModel.importResults.collect { result ->
                    snackbarHostState.showSnackbar(
                        message = result.toUserMessage(resources),
                        duration = SnackbarDuration.Long,
                    )
                }
            }

            val pickPkPass = rememberLauncherForActivityResult(
                ActivityResultContracts.OpenDocument(),
            ) { uri -> uri?.let(viewModel::importFrom) }

            val pickScanSource = rememberLauncherForActivityResult(
                ActivityResultContracts.OpenDocument(),
            ) { uri ->
                uri?.let {
                    navController.navigate(PassEditorRoute(sourceUri = Uri.encode(it.toString())))
                }
            }

            // Target of the camera capture. Survives configuration changes, so the result can
            // still be attributed even after a rotation during capture.
            var captureTarget by rememberSaveable { mutableStateOf<String?>(null) }

            val takePhoto = rememberLauncherForActivityResult(
                ActivityResultContracts.TakePicture(),
            ) { success ->
                val target = captureTarget?.let(Uri::parse)
                captureTarget = null
                when {
                    !success || target == null -> Unit
                    // Without this check, an ignored capture would lead to an empty form.
                    !CameraCapture.hasContent(context, target) -> scope.launch {
                        snackbarHostState.showSnackbar(
                            resources.getString(R.string.camera_capture_failed),
                        )
                    }

                    else -> navController.navigate(
                        PassEditorRoute(sourceUri = Uri.encode(target.toString())),
                    )
                }
            }

            PassOverviewScreen(
                onPassClick = { passId -> navController.navigate(PassDetailRoute(passId)) },
                onImportPkPass = { pickPkPass.launch(PKPASS_MIME_TYPES) },
                onScanSource = { pickScanSource.launch(SCAN_MIME_TYPES) },
                onCapturePhoto = {
                    val uri = CameraCapture.createTargetUri(context)
                    if (uri == null) {
                        scope.launch {
                            snackbarHostState.showSnackbar(
                                resources.getString(R.string.camera_unavailable),
                            )
                        }
                    } else {
                        captureTarget = uri.toString()
                        try {
                            takePhoto.launch(uri)
                        } catch (_: ActivityNotFoundException) {
                            captureTarget = null
                            scope.launch {
                                snackbarHostState.showSnackbar(
                                    resources.getString(R.string.camera_unavailable),
                                )
                            }
                        }
                    }
                },
                onCreateManually = { navController.navigate(PassEditorRoute()) },
                onOpenSettings = { navController.navigate(SettingsRoute) },
                snackbarHostState = snackbarHostState,
                viewModel = viewModel,
            )
        }

        composable<SettingsRoute> {
            SettingsScreen(onBack = { navController.popBackStack() })
        }

        composable<PassDetailRoute> { backStackEntry ->
            val route = backStackEntry.toRoute<PassDetailRoute>()
            PassDetailScreen(
                passId = route.passId,
                onBack = { navController.popBackStack() },
                onEdit = { passId -> navController.navigate(PassEditorRoute(passId = passId)) },
                onOpenOriginal = { passId -> navController.navigate(PassDocumentRoute(passId)) },
            )
        }

        composable<PassDocumentRoute> { backStackEntry ->
            val route = backStackEntry.toRoute<PassDocumentRoute>()
            PassDocumentScreen(
                passId = route.passId,
                onBack = { navController.popBackStack() },
            )
        }

        composable<PdfPreviewRoute> { backStackEntry ->
            val route = backStackEntry.toRoute<PdfPreviewRoute>()
            PdfPreviewScreen(
                uri = Uri.parse(Uri.decode(route.uri)),
                onBack = { navController.popBackStack() },
                onImport = { navController.navigate(PassEditorRoute(sourceUri = route.uri)) },
            )
        }

        composable<PassEditorRoute> { backStackEntry ->
            val route = backStackEntry.toRoute<PassEditorRoute>()
            val sourceUri = route.sourceUri?.let { Uri.parse(Uri.decode(it)) }

            PassEditorScreen(
                onBack = { navController.popBackStack() },
                onSaved = { passId ->
                    // The form itself doesn't belong on the back stack.
                    navController.popBackStack()
                    // When editing, the detail view is already underneath - a second entry
                    // would show the same pass again when going back.
                    if (route.passId == null) {
                        navController.navigate(PassDetailRoute(passId))
                    }
                },
                viewModel = viewModel(
                    factory = PassEditorViewModel.factory(
                        passId = route.passId,
                        sourceUri = sourceUri,
                    ),
                ),
            )
        }
    }
}





