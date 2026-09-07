package org.shadowgrove.passporta.data.local.entity

/**
 * Origin of a pass. Useful for later phases (re-import, debugging, UI hints).
 */
enum class PassSource(val storageKey: String) {

    /** Import of a `.pkpass` file (phase 2). */
    PKPASS("PKPASS"),

    /** Intercepted "Add to Google Wallet" link or JWT (phase 2). */
    WALLET_LINK("WALLET_LINK"),

    /** Offline extraction from an image or PDF via ML Kit (phase 5). */
    SCAN("SCAN"),

    /** Manually created by the user. */
    MANUAL("MANUAL");

    companion object {

        val DEFAULT: PassSource = MANUAL

        fun fromKey(rawKey: String?): PassSource =
            entries.firstOrNull { it.storageKey.equals(rawKey, ignoreCase = true) } ?: DEFAULT
    }
}
