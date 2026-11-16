package com.fahadali.oneos.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TransferSanitizerTest {
    @Test
    fun rejectsTraversal() {
        assertNull(com.fahadali.oneos.transfer.TransferSanitizer.sanitizeName("../etc/passwd"))
        assertNull(com.fahadali.oneos.transfer.TransferSanitizer.sanitizeName(".."))
        assertNull(com.fahadali.oneos.transfer.TransferSanitizer.sanitizeName("a/b"))
    }

    @Test
    fun acceptsSafeName() {
        assertEquals("photo.jpg", com.fahadali.oneos.transfer.TransferSanitizer.sanitizeName("photo.jpg"))
        assertEquals("My File (1).mp4", com.fahadali.oneos.transfer.TransferSanitizer.sanitizeName("My File (1).mp4"))
    }

    @Test
    fun sha256Stable() {
        val h = com.fahadali.oneos.transfer.TransferSanitizer.sha256("hello".toByteArray())
        assertEquals(64, h.length)
        assertEquals(h, com.fahadali.oneos.transfer.TransferSanitizer.sha256("hello".toByteArray()))
    }
}
