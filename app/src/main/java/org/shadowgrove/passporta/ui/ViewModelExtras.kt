package org.shadowgrove.passporta.ui

import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.CreationExtras
import org.shadowgrove.passporta.PassPortaApplication

/**
 * Access to the app's service locator inside a `viewModelFactory`.
 *
 * Replaces a DI framework: PassPorta has few dependencies and is meant to stay small.
 */
internal fun CreationExtras.passPortaApplication(): PassPortaApplication {
    val application = requireNotNull(this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY]) {
        "ViewModel was created without an Application context"
    }
    return application as PassPortaApplication
}

