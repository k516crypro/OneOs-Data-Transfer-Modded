package com.fahadali.oneos.transfer

import android.content.Context
import android.net.Uri
import android.provider.MediaStore
import com.fahadali.oneos.core.root.RootScript
import com.fahadali.oneos.session.BringAppsJob
import com.fahadali.oneos.session.RootVerbs
import com.fahadali.oneos.session.StageCheck
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.io.File
import java.security.MessageDigest

/**
 * One UI → OxygenOS 16 Guest file transfer.
 * Writes only under $STAGE/data/media/0/... via the pinned transfer-place verb.
 * Never calls remove/purge/begin. Host sources stay read-only.
 */
object TransferJob {
    private val _state = MutableStateFlow(TransferProgress())
    val state: StateFlow<TransferProgress> = _state.asStateFlow()

    private val _summary = MutableStateFlow(TransferSummary())
    val summary: StateFlow<TransferSummary> = _summary.asStateFlow()

    private const val STAGE = "/data/local/tmp/oneos"

    suspend fun scan(ctx: Context, category: TransferCategory): List<TransferFile> = withContext(Dispatchers.IO) {
        when (category) {
            TransferCategory.PHOTOS -> queryMedia(ctx, MediaStore.Images.Media.EXTERNAL_CONTENT_URI, category)
            TransferCategory.VIDEOS -> queryMedia(ctx, MediaStore.Video.Media.EXTERNAL_CONTENT_URI, category)
            else -> emptyList()
        }
    }

    private fun queryMedia(ctx: Context, uri: Uri, category: TransferCategory): List<TransferFile> {
        val list = mutableListOf<TransferFile>()
        val projection = arrayOf(
            MediaStore.MediaColumns._ID,
            MediaStore.MediaColumns.DISPLAY_NAME,
            MediaStore.MediaColumns.SIZE,
            MediaStore.MediaColumns.MIME_TYPE,
        )
        ctx.contentResolver.query(uri, projection, null, null, null)?.use { c ->
            val nameIdx = c.getColumnIndexOrThrow(MediaStore.MediaColumns.DISPLAY_NAME)
            val sizeIdx = c.getColumnIndexOrThrow(MediaStore.MediaColumns.SIZE)
            val mimeIdx = c.getColumnIndexOrThrow(MediaStore.MediaColumns.MIME_TYPE)
            val idIdx = c.getColumnIndexOrThrow(MediaStore.MediaColumns._ID)
            while (c.moveToNext()) {
                val name = c.getString(nameIdx) ?: continue
                val safe = TransferSanitizer.sanitizeName(name) ?: continue
                val id = c.getLong(idIdx)
                val contentUri = Uri.withAppendedPath(uri, id.toString())
                list += TransferFile(safe, contentUri.toString(), c.getLong(sizeIdx), c.getString(mimeIdx), category)
            }
        }
        return list
    }

    suspend fun start(ctx: Context, selection: TransferSelection) = withContext(Dispatchers.IO) {
        val stage = StageCheck.state.result
        if (stage == null || !stage.ours) {
            _state.value = TransferProgress(message = "Guest sistemi henüz hazır değil.", error = "Stage hazır değil")
            return@withContext
        }
        val files = selection.files.filter { it.category.guestRelative.isNotEmpty() }
        if (files.isEmpty()) {
            _state.value = TransferProgress(message = "Aktarılacak dosya seçilmedi.", error = "Dosya yok")
            return@withContext
        }

        val totalBytes = files.sumOf { it.size }
        // Guest free space from the read-only transfer-space verb (stage media filesystem).
        val free = guestFreeBytes(ctx)
        val margin = 100L * 1024 * 1024 // 100 MiB safety margin
        if (free != null && totalBytes + margin > free) {
            _state.value = TransferProgress(
                message = "Yetersiz depolama alanı. Guest'te yeterli boş alan yok.",
                error = "Yetersiz depolama alanı",
            )
            return@withContext
        }
        if (free == null) {
            _state.value = TransferProgress(message = "Guest boş alanı okunamadı; aktarım devam ediyor (doğrulanmamış).")
        }

        var doneBytes = 0L
        var doneCount = 0
        val failed = mutableMapOf<String, String>()
        val succeeded = mutableListOf<String>()
        val skipped = mutableListOf<String>()
        val renamed = mutableListOf<String>()
        val usedNames = mutableSetOf<String>()
        _state.value = TransferProgress(total = totalBytes, filesTotal = files.size, message = "Aktarım başlatılıyor")

        // Contacts: export vCard and place in guest Documents.
        if (TransferCategory.CONTACTS in selection.categories) {
            runCatching {
                val vcf = ContactsExporter.exportVcf(ctx)
                if (vcf.isEmpty()) {
                    failed["contacts.vcf"] = "Kişi bulunamadı veya izin yok"
                } else {
                    val sha = TransferSanitizer.sha256(vcf)
                    val temp = File(ctx.cacheDir, "oneos-contacts.vcf")
                    temp.writeBytes(vcf)
                    val reply = RootVerbs.run(
                        ctx,
                        RootScript.Verb.TRANSFER_PLACE,
                        listOf("documents", "contacts.vcf", vcf.size.toString(), sha),
                        feed = RootShellFeed(temp),
                    )
                    temp.delete()
                    if (reply.ok) {
                        succeeded += "contacts.vcf (${vcf.size} bayt) → Documents/OneOS Transfer"
                        doneCount++
                    } else {
                        failed["contacts.vcf"] = reply.problem ?: "Root reddetti"
                    }
                }
            }.onFailure { failed["contacts.vcf"] = it.message ?: "Kişi aktarımı başarısız" }
        }

        for (f in files) {
            if (_state.value.error == "İptal edildi") break
            var safe = TransferSanitizer.sanitizeName(f.displayName)
            if (safe == null) {
                failed[f.displayName] = "Güvenli olmayan dosya adı"
                continue
            }
            if (safe in usedNames) {
                when (selection.duplicatePolicy) {
                    DuplicatePolicy.SKIP -> {
                        skipped += safe
                        continue
                    }
                    DuplicatePolicy.RENAME -> {
                        val unique = TransferSanitizer.uniqueName(safe, usedNames)
                        renamed += "$safe → $unique"
                        safe = unique
                    }
                    DuplicatePolicy.REPLACE -> {
                        // Atomic replace is not implemented in the root verb (it refuses overwrite).
                        // Until a safe replace verb exists we skip and report.
                        skipped += safe
                        failed[safe] = "Değiştirme henüz güvenli değil; atlandı"
                        continue
                    }
                }
            }
            usedNames += safe

            val uri = Uri.parse(f.sourceUri)
            val temp = File(ctx.cacheDir, "oneos-transfer-${System.nanoTime()}")
            try {
                val (size, sha) = streamAndHash(ctx, uri, temp)
                if (size != f.size && f.size > 0) {
                    failed[safe] = "Boyut uyuşmazlığı"
                    continue
                }
                val catWord = when (f.category) {
                    TransferCategory.PHOTOS -> "photos"
                    TransferCategory.VIDEOS -> "videos"
                    TransferCategory.DOCUMENTS -> "documents"
                    TransferCategory.DOWNLOADS -> "downloads"
                    else -> continue
                }
                val reply = RootVerbs.run(
                    ctx,
                    RootScript.Verb.TRANSFER_PLACE,
                    listOf(catWord, safe, size.toString(), sha),
                    feed = RootShellFeed(temp),
                )
                if (reply.ok) {
                    succeeded += safe
                    doneBytes += size
                    doneCount++
                    _state.value = TransferProgress(
                        category = f.category,
                        current = doneBytes,
                        total = totalBytes,
                        filesDone = doneCount,
                        filesTotal = files.size,
                        message = "Aktarıldı: $safe",
                    )
                } else {
                    failed[safe] = reply.problem ?: "Root reddetti"
                }
            } catch (e: Exception) {
                failed[safe] = e.message ?: e.javaClass.simpleName
            } finally {
                temp.delete()
            }
        }

        // App data: reuse existing Bring Apps (APK + /data via bring-collect).
        if (TransferCategory.APPLICATIONS in selection.categories) {
            val pkgs = selection.files.filter { it.category == TransferCategory.APPLICATIONS }.map { it.sourceUri }
            if (pkgs.isEmpty()) {
                failed["apps"] = "Uygulama seçilmedi"
            } else {
                BringAppsJob.tick(ctx, pkgs)
                BringAppsJob.collect(ctx, pkgs)
                succeeded += "apps:${pkgs.size} (Bring Apps başlatıldı)"
            }
        }

        // Guest-side scan + contacts import. Host MediaStore is not used.
        val index = runCatching {
            RootVerbs.run(ctx, RootScript.Verb.TRANSFER_INDEX, emptyList())
        }.getOrNull()
        if (index?.ok == true && index.reply.ok?.contains("done") == true) {
            GuestMediaIndex.markSuccess()
        } else {
            GuestMediaIndex.requestIndex()
        }
        _summary.value = TransferSummary(succeeded, failed, skipped, renamed, doneBytes, doneCount)
        val msg = buildString {
            appendLine("Aktarım tamamlandı")
            appendLine("Başarılı: ${succeeded.size}")
            appendLine("Atlanan: ${skipped.size}")
            appendLine("Yeniden adlandırılan: ${renamed.size}")
            appendLine("Başarısız: ${failed.size}")
            append(GuestMediaIndex.userMessage)
        }
        _state.value = TransferProgress(
            current = doneBytes,
            total = totalBytes,
            filesDone = doneCount,
            filesTotal = files.size,
            message = msg,
            completed = true,
            error = if (failed.isEmpty()) null else failed.values.firstOrNull(),
        )
    }

    private fun streamAndHash(ctx: Context, uri: Uri, dest: File): Pair<Long, String> {
        val md = MessageDigest.getInstance("SHA-256")
        var total = 0L
        ctx.contentResolver.openInputStream(uri)?.use { input ->
            dest.outputStream().use { output ->
                val buf = ByteArray(64 * 1024)
                while (true) {
                    val n = input.read(buf)
                    if (n < 0) break
                    output.write(buf, 0, n)
                    md.update(buf, 0, n)
                    total += n
                }
            }
        } ?: error("Dosya açılamadı")
        val sha = md.digest().joinToString("") { "%02x".format(it) }
        return total to sha
    }


    private suspend fun guestFreeBytes(ctx: Context): Long? = withContext(Dispatchers.IO) {
        runCatching {
            val reply = RootVerbs.run(ctx, RootScript.Verb.TRANSFER_SPACE, emptyList())
            if (!reply.ok) return@runCatching null
            val ok = reply.reply.ok ?: return@runCatching null
            ok.removePrefix("transfer-space ").trim().toLongOrNull()
        }.getOrNull()
    }
    fun cancel() {
        RootVerbs.cancel()
        _state.value = _state.value.copy(message = "Aktarım iptal edildi", completed = true, error = "İptal edildi")
    }

    fun reset() {
        _state.value = TransferProgress()
        _summary.value = TransferSummary()
    }
}

private class RootShellFeed(private val file: File) : com.fahadali.oneos.core.root.RootShell.Feed {
    override fun write(out: java.io.OutputStream) {
        file.inputStream().use { it.copyTo(out) }
    }
}
