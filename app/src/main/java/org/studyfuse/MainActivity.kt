package org.studyfuse

import android.content.*
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.core.content.FileProvider
import androidx.documentfile.provider.DocumentFile
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.*
import org.studyfuse.data.Source
import org.studyfuse.ui.*
import java.io.File

class MainActivity:ComponentActivity() {
    private val model:StudyViewModel by viewModels()
    private var replace:String?=null
    private var pendingSave:File?=null
    private var pendingVolumes:List<File> = emptyList()
    private val picker=registerForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { model.importUris(it) }
    private val localModelPicker=registerForActivityResult(ActivityResultContracts.OpenDocument()){uri->if(uri!=null)model.importLocalModel(uri)}
    private val restorePicker=registerForActivityResult(ActivityResultContracts.OpenDocument()){uri->if(uri!=null)model.restore(uri)}
    private val bulkPdfPicker=registerForActivityResult(ActivityResultContracts.OpenMultipleDocuments()){model.prepareReplacements(it)}
    private val replacement=registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        val sourceId=replace; replace=null
        if(uri!=null && sourceId!=null) lifecycleScope.launch {
            val source=model.repo.dao.source(sourceId)
            if(source==null) message("Değiştirilecek kaynak bulunamadı")
            else {model.select(source.projectId);model.importUris(listOf(uri),source)}
        }
    }
    private val folder=registerForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if(uri!=null) model.importFolder(uri)
    }
    private val pdfSaver=registerForActivityResult(ActivityResultContracts.CreateDocument("application/pdf")) { uri -> saveTo(uri) }
    private val reportSaver=registerForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri -> saveTo(uri) }
    private val textSaver=registerForActivityResult(ActivityResultContracts.CreateDocument("text/plain")) { uri -> saveTo(uri) }
    private val zipSaver=registerForActivityResult(ActivityResultContracts.CreateDocument("application/zip")){uri->saveTo(uri)}
    private val volumeSaver=registerForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        val files=pendingVolumes; pendingVolumes=emptyList()
        if(uri!=null) lifecycleScope.launch {
            runCatching {
                withContext(Dispatchers.IO) {
                    val root=DocumentFile.fromTreeUri(this@MainActivity,uri) ?: error("Klasör açılamadı")
                    for(file in files) {
                        val dest=root.createFile("application/pdf",file.name) ?: error("${file.name} oluşturulamadı")
                        try { copy(file,dest.uri) } catch(e:Exception) { dest.delete(); throw e }
                    }
                }
            }.onSuccess { message("${files.size} PDF kaydedildi") }.onFailure { message("Kaydetme tamamlanamadı: ${it.message}") }
        }
    }
    override fun onCreate(savedInstanceState:Bundle?) {
        super.onCreate(savedInstanceState); enableEdgeToEdge()
        if(savedInstanceState!=null) {
            pendingSave=savedInstanceState.getString("save")?.let(::File)
            pendingVolumes=savedInstanceState.getStringArrayList("volumes")?.map(::File) ?: emptyList()
            replace=savedInstanceState.getString("replace")
            if(model.selected.value==null) model.select(savedInstanceState.getString("selected"))
        }
        setContent { StudyFuseUi(model,
            onImport={ picker.launch(arrayOf("*/*")) },onFolder={ folder.launch(null) },
            onReplace={ replace=it.id; replacement.launch(arrayOf("application/pdf")) },
            onOpen={ file,mime -> open(file,mime) },onShare={ share(it) },
            onSave={ file -> pendingSave=file
                when(file.extension) { "pdf" -> pdfSaver.launch(file.name); "json" -> reportSaver.launch(file.name); "zip"->zipSaver.launch(file.name);else -> textSaver.launch(file.name) }
            },onSaveAll={ pendingVolumes=it; volumeSaver.launch(null) },
            onRestore={restorePicker.launch(arrayOf("application/zip","application/octet-stream"))},
            onBulkReplace={bulkPdfPicker.launch(arrayOf("application/pdf"))},onImportModel={localModelPicker.launch(arrayOf("*/*"))}) }
        if(savedInstanceState==null) handleShare(intent)
    }
    override fun onSaveInstanceState(outState:Bundle) {
        outState.putString("save",pendingSave?.path)
        outState.putStringArrayList("volumes",ArrayList(pendingVolumes.map { it.path }))
        outState.putString("replace",replace)
        outState.putString("selected",model.selected.value)
        super.onSaveInstanceState(outState)
    }
    override fun onNewIntent(intent:Intent) { super.onNewIntent(intent); setIntent(intent); handleShare(intent) }
    @Suppress("DEPRECATION") private fun handleShare(intent:Intent?) {
        val uris=when(intent?.action) {
            Intent.ACTION_SEND -> listOfNotNull(intent.getParcelableExtra<Uri>(Intent.EXTRA_STREAM))
            Intent.ACTION_SEND_MULTIPLE -> intent.getParcelableArrayListExtra<Uri>(Intent.EXTRA_STREAM)?.toList() ?: emptyList()
            else -> emptyList()
        }.filter { it.scheme=="content" }
        if(uris.isNotEmpty()) {
            if(model.project.value?.status in setOf("RUNNING","QUEUED") || model.importing.value)
                message("İşlem sürüyor. Paylaşımı işlem bittikten sonra tekrar gönderin.")
            else model.importUris(uris)
        } else if(intent?.action in setOf(Intent.ACTION_SEND,Intent.ACTION_SEND_MULTIPLE)) {
            message("Dosya eki bulunamadı. OneNote bağlantısı yerine PDF veya görüntü paylaşın.")
        }
    }
    private fun saveTo(uri:Uri?) {
        val file=pendingSave; pendingSave=null
        if(uri==null || file==null) return
        lifecycleScope.launch {
            runCatching { withContext(Dispatchers.IO) { copy(file,uri) } }
                .onSuccess { message("${file.name} kaydedildi") }.onFailure {
                    runCatching { contentResolver.delete(uri,null,null) }; message("Kaydedilemedi: ${it.message}")
                }
        }
    }
    private fun copy(file:File,uri:Uri) {
        contentResolver.openOutputStream(uri,"wt")?.use { out -> file.inputStream().use { it.copyTo(out,65536) } }
            ?: error("Hedef dosya açılamadı")
    }
    private fun contentUri(file:File):Uri {
        val s=model.sources.value.firstOrNull {it.originalPath==file.path || it.activePath==file.path}
        val name=if(s==null) file.name else if(s.originalPath==file.path) s.name else s.name.substringBeforeLast('.')+".pdf"
        return FileProvider.getUriForFile(this,"$packageName.files",file,name)
    }
    private fun open(file:File,mime:String) {
        runCatching { startActivity(Intent(Intent.ACTION_VIEW).setDataAndType(contentUri(file),mime)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)) }.onFailure { message("Bu dosyayı açabilecek uygulama yok. Kaynak uygulamadan PDF dışa aktarın.") }
    }
    private fun share(files:List<File>) {
        if(files.isEmpty()) return
        runCatching {
            val uris=ArrayList(files.map(::contentUri))
            val i=Intent(if(uris.size==1) Intent.ACTION_SEND else Intent.ACTION_SEND_MULTIPLE).apply {
                type=if(files.all{it.extension=="pdf"})"application/pdf" else if(files.size==1)when(files.first().extension){"md"->"text/markdown";"txt"->"text/plain";"json"->"application/json";"zip"->"application/zip";else->"application/octet-stream"} else "*/*"
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                if(uris.size==1) putExtra(Intent.EXTRA_STREAM,uris.first()) else putParcelableArrayListExtra(Intent.EXTRA_STREAM,uris)
                clipData=ClipData.newRawUri("StudyFuse",uris.first()).also { c -> uris.drop(1).forEach { c.addItem(ClipData.Item(it)) } }
            }
            startActivity(Intent.createChooser(i,"StudyFuse dosyalarını paylaş"))
        }.onFailure { message("Paylaşılamadı: ${it.message}") }
    }
    private fun message(text:String) { Toast.makeText(this,text,Toast.LENGTH_LONG).show() }
}
