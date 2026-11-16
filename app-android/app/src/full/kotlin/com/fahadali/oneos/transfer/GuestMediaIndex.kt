package com.fahadali.oneos.transfer

/**
 * Guest MediaStore indexing status.
 *
 * IMPORTANT: Host MediaScannerConnection does NOT affect the OxygenOS 16 Guest Gallery.
 * A real index requires running a media scan inside the guest container namespace
 * (via oneos_genv.sh + nsenter). That path is not exposed as a safe, pinned root verb
 * today. This class reports the honest status without faking success.
 */
object GuestMediaIndex {
    enum class Status { PENDING, RUNNING, SUCCESS, FAILED }

    /** Current status. Stays PENDING until a verified guest-side scan verb exists. */
    var status: Status = Status.PENDING
        private set

    val userMessage: String
        get() = when (status) {
            Status.PENDING -> "Galeri/Kişiler: OxygenOS 16 açılınca tamamlanacak"
            Status.RUNNING -> "Galeri/Kişiler: Devam ediyor"
            Status.SUCCESS -> "Galeri ve kişiler güncellendi"
            Status.FAILED -> "Galeri/Kişiler güncellenemedi (dosyalar yazıldı)"
        }

    fun requestIndex(): Status {
        status = Status.PENDING
        return status
    }

    fun markSuccess(): Status {
        status = Status.SUCCESS
        return status
    }
}
