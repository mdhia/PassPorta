package org.shadowgrove.passporta.data.local.entity

/**
 * Section in which an additional field is displayed.
 *
 * The names follow Apple's `pass.json` structure, since it provides the finest granularity.
 * Google Wallet has no direct equivalent - there, `textModulesData` maps to [SECONDARY],
 * `infoModuleData` to [AUXILIARY] and `linksModuleData` to [BACK].
 *
 * [storageKey] is persisted, so a later rename of the constants doesn't invalidate the
 * database.
 */
enum class PassFieldSection(val storageKey: String) {
    HEADER("HEADER"),
    PRIMARY("PRIMARY"),
    SECONDARY("SECONDARY"),
    AUXILIARY("AUXILIARY"),

    /** Back of the pass - for Apple the detailed information (terms, notes, ...). */
    BACK("BACK");

    companion object {

        val DEFAULT: PassFieldSection = SECONDARY

        fun fromKey(rawKey: String?): PassFieldSection =
            entries.firstOrNull { it.storageKey.equals(rawKey, ignoreCase = true) } ?: DEFAULT
    }
}
