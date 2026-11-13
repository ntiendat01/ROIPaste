package com.roipaste.app.presentation

import com.roipaste.app.data.clipboard.ClipboardRead
import com.roipaste.app.domain.LocalClipError
import com.roipaste.app.domain.TransferError
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ErrorMappingTest {
    @Test
    fun mapsLocalAndTransferErrors() {
        assertEquals(Banner.ClipboardEmpty, ErrorMapping.bannerFor(LocalClipError.Empty, sending = true))
        assertEquals(Banner.FocusRequired, ErrorMapping.bannerFor(LocalClipError.NotFocused, sending = true))
        assertEquals(Banner.Unauthenticated, ErrorMapping.bannerFor(TransferError.Unauthenticated, sending = true))
        assertEquals(Banner.Disconnected, ErrorMapping.bannerFor(TransferError.Disconnected, sending = false))
        assertEquals(Banner.SendFailed, ErrorMapping.bannerFor(TransferError.InvalidResponse, sending = true))
        assertEquals(Banner.FetchFailed, ErrorMapping.bannerFor(TransferError.InvalidResponse, sending = false))
    }

    @Test
    fun mapsClipboardReadToBanner() {
        assertNull(ErrorMapping.fromClipboard(ClipboardRead.Text("ok")))
        assertEquals(Banner.ClipboardEmpty, ErrorMapping.fromClipboard(ClipboardRead.Empty))
        assertEquals(Banner.FocusRequired, ErrorMapping.fromClipboard(ClipboardRead.NotFocused))
        assertEquals(Banner.Unsupported, ErrorMapping.fromClipboard(ClipboardRead.Unsupported))
    }
}
