package com.fahadali.oneos.transfer

import android.os.Bundle
import android.widget.Button
import android.widget.CheckBox
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.ScrollView
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.fahadali.oneos.R
import kotlinx.coroutines.launch

/**
 * OneOS Veri Aktarımı — Fotoğraflar / Videolar / Belgeler / İndirilenler.
 */
class DataTransferActivity : AppCompatActivity() {
    private lateinit var status: TextView
    private lateinit var progress: ProgressBar
    private lateinit var summary: TextView
    private val checks = mutableMapOf<TransferCategory, CheckBox>()
    private val selectedFiles = mutableListOf<TransferFile>()
    private var policy = DuplicatePolicy.SKIP

    private val pickDocs = registerForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        uris?.forEach { uri ->
            val name = uri.lastPathSegment ?: "belge"
            val safe = TransferSanitizer.sanitizeName(name) ?: return@forEach
            selectedFiles += TransferFile(safe, uri.toString(), 0L, null, TransferCategory.DOCUMENTS)
        }
        updateStatus()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        title = getString(R.string.transfer_title)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(32, 32, 32, 32)
        }
        status = TextView(this).apply {
            text = getString(R.string.transfer_source) + "\n" + getString(R.string.transfer_dest)
        }
        progress = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply {
            isIndeterminate = false
            max = 100
        }
        summary = TextView(this)
        root.addView(status)
        root.addView(progress)
        root.addView(summary)

        listOf(
            TransferCategory.PHOTOS,
            TransferCategory.VIDEOS,
            TransferCategory.DOCUMENTS,
            TransferCategory.DOWNLOADS,
            TransferCategory.CONTACTS,
            TransferCategory.APPLICATIONS,
        ).forEach { cat ->
            val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
            val cb = CheckBox(this).apply { text = cat.label }
            checks[cat] = cb
            val pick = Button(this).apply {
                text = getString(R.string.transfer_pick)
                setOnClickListener { onPick(cat) }
            }
            row.addView(cb)
            row.addView(pick)
            root.addView(row)
        }

        val policyGroup = RadioGroup(this)
        val rename = RadioButton(this).apply { text = "Yeniden adlandır"; tag = DuplicatePolicy.RENAME }
        val skip = RadioButton(this).apply { text = "Atla"; tag = DuplicatePolicy.SKIP; isChecked = true }
        val replace = RadioButton(this).apply {
            text = "Değiştir (güvenli değil, atlanır)"
            tag = DuplicatePolicy.REPLACE
            isEnabled = false
        }
        policyGroup.addView(rename)
        policyGroup.addView(skip)
        policyGroup.addView(replace)
        policyGroup.setOnCheckedChangeListener { group, id ->
            policy = group.findViewById<RadioButton>(id)?.tag as? DuplicatePolicy ?: DuplicatePolicy.SKIP
        }
        root.addView(policyGroup)

        val start = Button(this).apply {
            text = getString(R.string.transfer_start)
            setOnClickListener { doStart() }
        }
        val cancel = Button(this).apply {
            text = getString(R.string.transfer_cancel)
            setOnClickListener { TransferJob.cancel() }
        }
        root.addView(start)
        root.addView(cancel)

        setContentView(ScrollView(this).apply { addView(root) })

        lifecycleScope.launch {
            TransferJob.state.collect { p ->
                status.text = buildString {
                    appendLine(getString(R.string.transfer_source) + " → " + getString(R.string.transfer_dest))
                    appendLine(p.message.ifEmpty { getString(R.string.transfer_ready) })
                    if (p.filesTotal > 0) appendLine("Dosya: ${p.filesDone}/${p.filesTotal}")
                    if (p.error != null) appendLine(TransferErrors.userMessage(this@DataTransferActivity, p.error))
                }
                if (p.total > 0) progress.progress = ((p.current * 100) / p.total).toInt()
            }
        }
        lifecycleScope.launch {
            TransferJob.summary.collect { s ->
                summary.text = getString(R.string.transfer_summary, s.itemCount, s.failed.size) + "\n" + GuestMediaIndex.userMessage +
                    "  Atlanan: ${s.skipped.size}  Yeniden adlandırılan: ${s.renamed.size}"
            }
        }
    }

    private fun onPick(cat: TransferCategory) {
        when (cat) {
            TransferCategory.CONTACTS -> {
                if (checkSelfPermission(android.Manifest.permission.READ_CONTACTS) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                    requestPermissions(arrayOf(android.Manifest.permission.READ_CONTACTS), 1001)
                    return
                }
                selectedFiles.removeAll { it.category == TransferCategory.CONTACTS }
                selectedFiles += TransferFile("contacts.vcf", "contacts", 0L, "text/vcard", TransferCategory.CONTACTS)
                updateStatus()
            }
            TransferCategory.APPLICATIONS -> lifecycleScope.launch {
                // List launchable user apps the same way Bring Apps does, via PackageManager.
                val pm = packageManager
                val intent = android.content.Intent(android.content.Intent.ACTION_MAIN).addCategory(android.content.Intent.CATEGORY_LAUNCHER)
                val seen = mutableSetOf<String>()
                selectedFiles.removeAll { it.category == TransferCategory.APPLICATIONS }
                for (ri in pm.queryIntentActivities(intent, 0)) {
                    val ai = ri.activityInfo?.applicationInfo ?: continue
                    val pkg = ai.packageName
                    if (!seen.add(pkg)) continue
                    if (ai.uid < android.os.Process.FIRST_APPLICATION_UID) continue
                    val system = (ai.flags and (android.content.pm.ApplicationInfo.FLAG_SYSTEM or android.content.pm.ApplicationInfo.FLAG_UPDATED_SYSTEM_APP)) != 0
                    if (!com.fahadali.oneos.core.model.BringApps.eligible(pkg, system, launchable = true)) continue
                    val label = runCatching { ai.loadLabel(pm).toString() }.getOrDefault(pkg)
                    selectedFiles += TransferFile(label, pkg, 0L, null, TransferCategory.APPLICATIONS)
                }
                updateStatus()
            }
            TransferCategory.DOCUMENTS, TransferCategory.DOWNLOADS -> pickDocs.launch(arrayOf("*/*"))
            else -> lifecycleScope.launch {
                val list = TransferJob.scan(this@DataTransferActivity, cat)
                selectedFiles.removeAll { it.category == cat }
                selectedFiles.addAll(list.take(100))
                updateStatus()
            }
        }
    }

    private fun updateStatus() {
        val bytes = selectedFiles.sumOf { it.size }
        status.text = "Seçilen: ${selectedFiles.size} dosya (~${bytes / 1024} KB)"
    }

    private fun doStart() {
        val cats = checks.filterValues { it.isChecked }.keys
        val files = selectedFiles.filter { it.category in cats }
        lifecycleScope.launch {
            TransferJob.start(this@DataTransferActivity, TransferSelection(cats, files, policy))
        }
    }
}
