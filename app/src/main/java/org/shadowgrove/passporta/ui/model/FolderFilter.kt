package org.shadowgrove.passporta.ui.model

import androidx.compose.runtime.Immutable

/**
 * Selection in the overview's header row.
 *
 * Deliberately its own type instead of a nullable string: the archive is not a real folder, but
 * derives from the expiration date. With a magic string, a user folder named "Archiv" could no
 * longer be distinguished from it.
 */
@Immutable
sealed interface FolderFilter {

    /**
     * Passes marked as favorite - always the first entry once there are any.
     *
     * Like the archive, not a real folder: a pass stays in its folder and additionally appears
     * here.
     */
    data object Favorites : FolderFilter

    /** All still-valid passes. */
    data object All : FolderFilter

    data class Named(val name: String) : FolderFilter

    /** Expired passes - always the last entry of the selection. */
    data object Archive : FolderFilter
}


