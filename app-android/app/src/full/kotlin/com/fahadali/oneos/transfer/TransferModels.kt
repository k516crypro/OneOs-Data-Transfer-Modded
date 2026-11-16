package com.fahadali.oneos.transfer

import java.security.MessageDigest
import java.util.Locale

enum class TransferCategory(val label: String, val description: String, val guestRelative: String) {
    PHOTOS("Fotoğraflar", "Kamera ve galeri fotoğrafları", "Pictures/OneOS Transfer"),
    VIDEOS("Videolar", "Kamera ve galeri videoları", "Movies/OneOS Transfer"),
    DOCUMENTS("Belgeler", "Belgeler ve dosyalar", "Documents/OneOS Transfer"),
    DOWNLOADS("İndirilenler", "İndirilenler klasörü", "Download/OneOS Transfer"),
    CONTACTS("Kişiler", "Kişiler (sonraki faz)", ""),
    CALL_HISTORY("Arama Geçmişi", "Arama kaydı (sonraki faz)", ""),
    APPLICATIONS("Uygulamalar", "Bring Apps (sonraki faz)", ""),
}

data class TransferFile(
    val displayName: String,
    val sourceUri: String,
    val size: Long,
    val mime: String?,
    val category: TransferCategory,
    val sha256: String? = null,
)

enum class DuplicatePolicy { RENAME, SKIP, REPLACE }

data class TransferSelection(
    val categories: Set<TransferCategory> = emptySet(),
    val files: List<TransferFile> = emptyList(),
    val duplicatePolicy: DuplicatePolicy = DuplicatePolicy.SKIP,
)

data class TransferProgress(
    val category: TransferCategory? = null,
    val current: Long = 0,
    val total: Long = 0,
    val filesDone: Int = 0,
    val filesTotal: Int = 0,
    val message: String = "",
    val completed: Boolean = false,
    val error: String? = null,
)

data class TransferSummary(
    val succeeded: List<String> = emptyList(),
    val failed: Map<String, String> = emptyMap(),
    val skipped: List<String> = emptyList(),
    val renamed: List<String> = emptyList(),
    val totalBytes: Long = 0,
    val itemCount: Int = 0,
)

object TransferSanitizer {
    private val SAFE_NAME = Regex("^[A-Za-z0-9._\\- ()]+$")

    fun sanitizeName(name: String): String? {
        val base = name.substringAfterLast('/').substringAfterLast('\\')
        if (base.isBlank() || base == "." || base == ".." || base.contains('\u0000')) return null
        if (!SAFE_NAME.matches(base)) return null
        if (base.length > 180) return null
        return base
    }

    /** photo.jpg → photo (1).jpg when base is taken. */
    fun uniqueName(base: String, existing: Set<String>): String {
        if (base !in existing) return base
        val dot = base.lastIndexOf('.')
        val stem = if (dot > 0) base.substring(0, dot) else base
        val ext = if (dot > 0) base.substring(dot) else ""
        var i = 1
        while (true) {
            val candidate = "$stem ($i)$ext"
            if (candidate !in existing) return candidate
            i++
        }
    }

    fun sha256(bytes: ByteArray): String {
        val md = MessageDigest.getInstance("SHA-256")
        return md.digest(bytes).joinToString("") { "%02x".format(it) }.lowercase(Locale.US)
    }
}
