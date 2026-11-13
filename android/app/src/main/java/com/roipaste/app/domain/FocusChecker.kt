package com.roipaste.app.domain

fun interface FocusChecker {
    fun canReadClipboard(): Boolean
}

class MutableFocusChecker : FocusChecker {
    @Volatile
    var resumed: Boolean = false

    @Volatile
    var windowFocused: Boolean = false

    override fun canReadClipboard(): Boolean = resumed && windowFocused
}
