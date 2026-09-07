package org.shadowgrove.passporta.data.scanner

import com.google.android.gms.tasks.Task
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Bridge from the GMS `Task` API to Kotlin coroutines.
 *
 * Deliberately hand-written instead of using `kotlinx-coroutines-play-services`: that artifact
 * must exactly match the transitively resolved `kotlinx-coroutines-core` version. For this one
 * function, that would be an unnecessary version coupling.
 */
suspend fun <T> Task<T>.await(): T = suspendCancellableCoroutine { continuation ->
    addOnCompleteListener { task ->
        val error = task.exception
        when {
            error != null -> continuation.resumeWithException(error)
            task.isCanceled -> continuation.cancel()
            else -> {
                @Suppress("UNCHECKED_CAST")
                continuation.resume(task.result as T)
            }
        }
    }
}
