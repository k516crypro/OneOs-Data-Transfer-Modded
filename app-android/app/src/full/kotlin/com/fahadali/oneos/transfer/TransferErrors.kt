package com.fahadali.oneos.transfer

import android.content.Context
import com.fahadali.oneos.R

/**
 * Maps technical errors to user-facing Turkish messages.
 * Technical details stay in logs; the UI never shows raw exceptions.
 */
object TransferErrors {
    fun userMessage(ctx: Context, technical: String?): String {
        if (technical.isNullOrBlank()) return ctx.getString(R.string.err_unexpected)
        val t = technical.lowercase()
        return when {
            "cancel" in t -> ctx.getString(R.string.transfer_cancelled)
            "permission" in t || "denied" in t -> ctx.getString(R.string.err_permission)
            "storage" in t || "space" in t || "no space" in t -> ctx.getString(R.string.err_storage)
            "not found" in t || "enoent" in t -> ctx.getString(R.string.err_not_found)
            "sha" in t || "checksum" in t -> ctx.getString(R.string.transfer_sha_fail)
            "unsafe" in t || "traversal" in t -> ctx.getString(R.string.transfer_unsafe_name)
            "stage" in t || "not ready" in t -> ctx.getString(R.string.transfer_no_stage)
            "open" in t || "uri" in t -> ctx.getString(R.string.transfer_open_failed)
            "root" in t || "refused" in t -> ctx.getString(R.string.transfer_root_refused)
            else -> ctx.getString(R.string.err_unexpected)
        }
    }
}
