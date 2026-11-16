package com.fahadali.oneos.transfer

import android.content.Context
import android.provider.ContactsContract
import java.io.ByteArrayOutputStream

/**
 * Exports One UI contacts as a vCard 3.0 file.
 * Host ContactsProvider is read-only. The vCard is then placed in guest storage.
 */
object ContactsExporter {
    fun exportVcf(ctx: Context): ByteArray {
        val out = ByteArrayOutputStream()
        val cr = ctx.contentResolver
        cr.query(
            ContactsContract.Contacts.CONTENT_URI,
            arrayOf(ContactsContract.Contacts._ID, ContactsContract.Contacts.DISPLAY_NAME, ContactsContract.Contacts.HAS_PHONE_NUMBER),
            null, null, ContactsContract.Contacts.DISPLAY_NAME,
        )?.use { c ->
            val idIdx = c.getColumnIndexOrThrow(ContactsContract.Contacts._ID)
            val nameIdx = c.getColumnIndexOrThrow(ContactsContract.Contacts.DISPLAY_NAME)
            val phoneIdx = c.getColumnIndexOrThrow(ContactsContract.Contacts.HAS_PHONE_NUMBER)
            while (c.moveToNext()) {
                val id = c.getString(idIdx)
                val name = c.getString(nameIdx) ?: continue
                val hasPhone = c.getInt(phoneIdx) > 0
                val phones = if (hasPhone) phonesOf(ctx, id) else emptyList()
                val emails = emailsOf(ctx, id)
                out.write(vcfEntry(name, phones, emails).toByteArray())
            }
        }
        return out.toByteArray()
    }

    fun count(ctx: Context): Int {
        crQueryCount(ctx)
        return crQueryCount(ctx)
    }

    private fun crQueryCount(ctx: Context): Int {
        var n = 0
        ctx.contentResolver.query(
            ContactsContract.Contacts.CONTENT_URI,
            arrayOf(ContactsContract.Contacts._ID),
            null, null, null,
        )?.use { n = it.count }
        return n
    }

    private fun phonesOf(ctx: Context, id: String): List<String> {
        val list = mutableListOf<String>()
        ctx.contentResolver.query(
            ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
            arrayOf(ContactsContract.CommonDataKinds.Phone.NUMBER),
            "${ContactsContract.CommonDataKinds.Phone.CONTACT_ID}=?",
            arrayOf(id), null,
        )?.use { c ->
            val idx = c.getColumnIndexOrThrow(ContactsContract.CommonDataKinds.Phone.NUMBER)
            while (c.moveToNext()) c.getString(idx)?.let { list += it }
        }
        return list
    }

    private fun emailsOf(ctx: Context, id: String): List<String> {
        val list = mutableListOf<String>()
        ctx.contentResolver.query(
            ContactsContract.CommonDataKinds.Email.CONTENT_URI,
            arrayOf(ContactsContract.CommonDataKinds.Email.ADDRESS),
            "${ContactsContract.CommonDataKinds.Email.CONTACT_ID}=?",
            arrayOf(id), null,
        )?.use { c ->
            val idx = c.getColumnIndexOrThrow(ContactsContract.CommonDataKinds.Email.ADDRESS)
            while (c.moveToNext()) c.getString(idx)?.let { list += it }
        }
        return list
    }

    private fun vcfEntry(name: String, phones: List<String>, emails: List<String>): String = buildString {
        append("BEGIN:VCARD\r\n")
        append("VERSION:3.0\r\n")
        append("FN:").append(escape(name)).append("\r\n")
        append("N:").append(escape(name)).append(";;;;\r\n")
        phones.forEach { append("TEL;TYPE=CELL:").append(escape(it)).append("\r\n") }
        emails.forEach { append("EMAIL:").append(escape(it)).append("\r\n") }
        append("END:VCARD\r\n")
    }

    private fun escape(s: String) = s.replace("\\", "\\\\").replace(";", "\\;").replace(",", "\\,").replace("\n", "\\n")
}
