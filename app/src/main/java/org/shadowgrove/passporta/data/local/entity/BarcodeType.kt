package org.shadowgrove.passporta.data.local.entity

/**
 * Supported barcode formats.
 *
 * [storageKey] is persisted in the database. This keeps existing records readable even if the
 * enum constants are later renamed or reordered.
 */
enum class BarcodeType(val storageKey: String) {
    QR("QR"),
    AZTEC("AZTEC"),
    PDF417("PDF417"),
    CODE128("CODE128"),

    /**
     * Interleaved 2 of 5 - a pure digit sequence of even length.
     *
     * Common on shipping labels, warehouse and pharmacy labels, and as ITF-14 on outer
     * packaging. Odd-length digit sequences cannot be represented in this format; ZXing
     * rejects them instead of silently padding them.
     */
    ITF("ITF");

    companion object {

        val DEFAULT: BarcodeType = QR

        /**
         * Robust mapping of an arbitrary identifier (e.g. from a `pass.json` or from ML Kit)
         * to a [BarcodeType]. Returns `null` if no format was recognized.
         */
        fun fromKeyOrNull(rawKey: String?): BarcodeType? {
            if (rawKey.isNullOrBlank()) return null
            val normalized = rawKey.uppercase().filter { it.isLetterOrDigit() }
            return entries.firstOrNull { normalized.contains(it.storageKey) }
                ?: when {
                    normalized.contains("QRCODE") -> QR
                    normalized.contains("CODE39") -> CODE128
                    // Google's Wallet API and many label printers write "2OF5" or
                    // "INTERLEAVED25" instead of "ITF".
                    normalized.contains("2OF5") || normalized.contains("INTERLEAVED") -> ITF
                    else -> null
                }
        }

        /** Like [fromKeyOrNull], but falls back to [DEFAULT]. */
        fun fromKey(rawKey: String?): BarcodeType = fromKeyOrNull(rawKey) ?: DEFAULT
    }
}
