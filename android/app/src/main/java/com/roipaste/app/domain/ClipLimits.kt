package com.roipaste.app.domain

/** Payload limits from the ROIPaste protocol specification. */
object ClipLimits {
    const val MAX_TEXT_BYTES: Int = 256 * 1024

    fun isWithinLimit(byteCount: Int): Boolean = byteCount in 0..MAX_TEXT_BYTES
}
