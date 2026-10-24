package org.studyfuse.ui

import android.app.Application
import android.net.Uri
import androidx.lifecycle.*
import androidx.work.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import org.studyfuse.StudyFuseApp
import org.studyfuse.core.*
import org.studyfuse.data.*
import org.studyfuse.worker.CompileWorker
import java.io.File

@OptIn(ExperimentalCoroutinesApi::class)
data class Replacement(val source:Source,val uri:Uri,val name:String)

@OptIn(ExperimentalCoroutinesApi::class)
class StudyViewModel(application:Application):AndroidViewModel(application) {
    private val app=application as StudyFuseApp
    val repo=app.repository
    private val dao=repo.dao
    val selected=MutableStateFlow<String?>(null)
    val projects=dao.projects().stateIn(viewModelScope,SharingStarted.WhileSubscribed(5000),emptyList())
    val project=selected.flatMapLatest { id -> if(id==null) flowOf(null) else dao.watchProject(id) }
        .stateIn(viewModelScope,SharingStarted.WhileSubscribed(5000),null)
    val sources=selected.flatMapLatest { id -> if(id==null) flowOf(emptyList()) else dao.sources(id) }
        .stateIn(viewModelScope,SharingStarted.WhileSubscribed(5000),emptyList())
    val work=selected.flatMapLatest { id -> if(id==null) flowOf(emptyList()) else
        WorkManager.getInstance(app).getWorkInfosForUniqueWorkFlow("compile-$id") }
        .stateIn(viewModelScope,SharingStarted.WhileSubscribed(5000),emptyList())
    val importing=MutableStateFlow(false)
    val importMessage=MutableStateFlow("")
    val notice=MutableStateFlow<String?>(null)
    val replacements=MutableStateFlow<List<Replacement>>(emptyList())
    private var importJob:Job?=null
    val dark=MutableStateFlow(app.getSharedPreferences("ui",0).getInt("theme",0))
    fun theme(mode:Int) { dark.value=mode; app.getSharedPreferences("ui",0).edit().putInt("theme",mode).apply() }
    fun select(id:String?) { if(!importing.value) selected.value=id }
    fun create()=viewModelScope.launch { if(!importing.value) selected.value=repo.create() }
    fun importUris(uris:List<Uri>,replace:Source?=null) {
        if(importing.value || uris.isEmpty()) return
        importing.value=true
        importJob=viewModelScope.launch(Dispatchers.IO) {
            try {
                val id=selected.value ?: repo.create().also { selected.value=it }
                val errors=mutableListOf<String>()
                val duplicates=mutableListOf<String>()
                for((i,uri) in uris.withIndex()) {
                    importMessage.value="Dosya kopyalanıyor · ${i+1}/${uris.size}"
                    try {
                        val added=repo.import(id,uri,replace)
                        if(replace==null && dao.snapshot(id).any{it.id!=added.id && it.sha256==added.sha256}) duplicates+=added.name
                    }
                    catch(e:CancellationException) { throw e }
                    catch(e:Exception) { errors+="${uri.lastPathSegment}: ${e.message}" }
                }
                if(errors.isNotEmpty() || duplicates.isNotEmpty()) notice.value=
                    (if(errors.isNotEmpty()) "İçe aktarılamayan dosyalar:\n"+errors.joinToString("\n") else "")+
                    (if(duplicates.isNotEmpty()) "\nAynı dosyadan birden fazla kopya var; hiçbir dosya otomatik silinmedi:\n"+duplicates.joinToString("\n") else "")
            } catch(e:CancellationException) { notice.value="İçe aktarma iptal edildi. Daha önce tamamlanan dosyalar korunuyor." }
            catch(e:Exception) { notice.value=e.message }
            finally { importing.value=false; importMessage.value="" }
        }
    }
    fun importFolder(uri:Uri) {
        if(importing.value) return
        importing.value=true
        importJob=viewModelScope.launch(Dispatchers.IO) {
            try {
                val id=selected.value ?: repo.create().also { selected.value=it }
                val errors=repo.folder(id,uri) { importMessage.value=it }
                if(errors.isNotEmpty()) notice.value="Klasör uyarıları:\n"+errors.joinToString("\n")
            } catch(e:CancellationException) { notice.value="Klasör içe aktarma iptal edildi." }
            catch(e:Exception) { notice.value=e.message }
            finally { importing.value=false; importMessage.value="" }
        }
    }
    fun cancelImport() { importJob?.cancel() }
    fun renameProject(name:String)=mutate { id -> dao.project(id)?.let { dao.updateProject(it.copy(name=name.ifBlank { "Yeni çalışma" })) } }
    fun renameSource(s:Source,name:String)=mutate { id -> dao.editSources(id,listOf(s.copy(section=name.ifBlank { s.name }))) }
    fun remove(s:Source)=mutate { id ->
        dao.editSources(id,listOf(s.copy(removedAt=System.currentTimeMillis())))
    }
    fun reorder(items:List<Source>)=mutate { id -> dao.editSources(id,items.mapIndexed{i,s->s.copy(position=i)}) }
    fun undoRemoval()=mutate {id ->
        val trash=dao.trash(id);val stamp=trash.firstOrNull()?.removedAt
        if(stamp!=null) {
            val active=dao.snapshot(id).toMutableList()
            trash.filter{it.removedAt==stamp}.sortedBy{it.position}.forEach{active.add(it.position.coerceIn(0,active.size),it.copy(removedAt=0))}
            dao.editSources(id,active.mapIndexed{i,s->s.copy(position=i)})
        }
        else notice.value="Geri alınacak kaldırma yok"
    }
    fun bulkRemove(ids:Set<String>)=mutate {id ->
        val time=System.currentTimeMillis();dao.editSources(id,dao.snapshot(id).filter{it.id in ids}.map{it.copy(removedAt=time)})
    }
    fun bulkRename(ids:Set<String>,prefix:String)=mutate {id ->
        dao.editSources(id,dao.snapshot(id).filter{it.id in ids}.mapIndexed{i,s->s.copy(section="${prefix.ifBlank{"Bölüm"}} ${i+1} · ${s.name.substringBeforeLast('.')}")})
    }
    fun naturalSort()=mutate {id ->dao.editSources(id,dao.snapshot(id).sortedWith {a,b->NaturalOrder.compare(a.name,b.name)}.mapIndexed{i,s->s.copy(position=i)})}
    fun pagination(source:Source,json:String)=mutate {id ->PageCuts.validate(json);dao.editSources(id,listOf(source.copy(pagination=json)))}
    fun clearCache()=mutate {id ->withContext(Dispatchers.IO){File(repo.directory(id),"conversion-cache").deleteRecursively()};notice.value="Dönüştürme önbelleği temizlendi; orijinaller korundu."}
    fun restore(uri:Uri) {
        if(importing.value)return
        importing.value=true
        importJob=viewModelScope.launch(Dispatchers.IO) {
            try {app.contentResolver.openInputStream(uri)?.use {selected.value=ProjectArchive(repo).restore(it)} ?: error("Yedek açılamadı")}
            catch(e:Exception){notice.value="Yedek geri yüklenemedi: ${e.message}"}
            finally{importing.value=false}
        }
    }
    fun backup(onReady:(File)->Unit) {
        val id=selected.value ?: return
        if(importing.value || project.value?.status in setOf("RUNNING","QUEUED"))return
        importing.value=true
        importJob=viewModelScope.launch {
            try {
                val file=withContext(Dispatchers.IO) {val f=File(repo.directory(id),"StudyFuse-yedek.zip");
                    try {f.outputStream().use{ProjectArchive(repo).export(id,it)};f} catch(e:Exception){f.delete();throw e}}
                onReady(file)
            } catch(e:Exception){notice.value=e.message}
            finally{importing.value=false}
        }
    }
    fun prepareReplacements(uris:List<Uri>)=viewModelScope.launch(Dispatchers.IO) {
        val id=selected.value ?: return@launch
        val waiting=dao.snapshot(id).filter{!Format.valueOf(it.format).native}
        val pairs=mutableListOf<Replacement>();val unmatched=mutableListOf<String>()
        fun stem(s:String)=s.substringBeforeLast('.').lowercase(java.util.Locale.ROOT)
        for(uri in uris) {
            val name=app.contentResolver.query(uri,arrayOf(android.provider.OpenableColumns.DISPLAY_NAME),null,null,null)?.use{if(it.moveToFirst())it.getString(0)else null} ?: "PDF"
            val candidates=waiting.filter{stem(it.name)==stem(name) && pairs.none{p->p.source.id==it.id}}
            if(candidates.size==1)pairs+=Replacement(candidates.single(),uri,name) else unmatched+=name
        }
        replacements.value=pairs
        if(unmatched.isNotEmpty())notice.value="Bu PDF'ler benzersiz bir kaynak adıyla eşleşmedi; kaynak menüsünden tek tek seçin:\n"+unmatched.joinToString("\n")
    }
    fun applyReplacements() {
        val pending=replacements.value;replacements.value=emptyList();if(pending.isEmpty() || importing.value)return
        importing.value=true
        importJob=viewModelScope.launch(Dispatchers.IO) {
            val errors=mutableListOf<String>()
            try {for((s,u) in pending){currentCoroutineContext().ensureActive();try{repo.import(s.projectId,u,s)}catch(e:CancellationException){throw e}catch(e:Exception){errors+="${s.name}: ${e.message}"}}}
            finally{importing.value=false;if(errors.isNotEmpty())notice.value=errors.joinToString("\n")}
        }
    }
    fun settings(s:ExportSettings)=mutate { id -> dao.project(id)?.let {
        dao.updateProject(it.copy(settings=SettingsCodec.encode(s),output=null,report=null,status="READY",message="",workId=null))
    } }
    private fun mutate(action:suspend(String)->Unit)=viewModelScope.launch {
        val id=selected.value ?: return@launch
        val p=dao.project(id) ?: return@launch
        if(importing.value || p.status in setOf("RUNNING","QUEUED")) return@launch
        try { action(id) } catch(e:Exception) { notice.value=e.message }
    }
    fun compile()=viewModelScope.launch {
        val id=selected.value ?: return@launch
        val p=dao.project(id) ?: return@launch
        if(importing.value || p.status in setOf("RUNNING","QUEUED") || dao.snapshot(id).isEmpty()) return@launch
        val request=OneTimeWorkRequestBuilder<CompileWorker>().setInputData(workDataOf("projectId" to id))
            .addTag("project-$id").build()
        dao.updateProject(p.copy(status="QUEUED",message="İşlem sıraya alındı",workId=request.id.toString()))
        try {
            withContext(Dispatchers.IO) {
                WorkManager.getInstance(app).enqueueUniqueWork("compile-$id",ExistingWorkPolicy.REPLACE,request).result.get()
            }
        } catch(e:Exception) { dao.workStatus(id,request.id.toString(),"FAILED",e.message ?: "İşlem sıraya alınamadı") }
    }
    fun cancel() {
        val id=selected.value ?: return
        val workId=project.value?.workId ?: return
        WorkManager.getInstance(app).cancelUniqueWork("compile-$id")
        viewModelScope.launch { dao.workStatus(id,workId,"CANCELLED","İşlem iptal edildi") }
    }
    fun deleteProject(p:Project)=viewModelScope.launch(Dispatchers.IO) {
        if(importing.value || p.status in setOf("RUNNING","QUEUED")) return@launch
        dao.deleteProject(p); repo.directory(p.id).deleteRecursively()
        if(selected.value==p.id) selected.value=null
    }
}
