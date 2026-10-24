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
                for((i,uri) in uris.withIndex()) {
                    importMessage.value="Dosya kopyalanıyor · ${i+1}/${uris.size}"
                    try { repo.import(id,uri,replace) }
                    catch(e:CancellationException) { throw e }
                    catch(e:Exception) { errors+="${uri.lastPathSegment}: ${e.message}" }
                }
                if(errors.isNotEmpty()) notice.value="İçe aktarılamayan dosyalar:\n"+errors.joinToString("\n")
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
    fun renameSource(s:Source,name:String)=mutate { id -> dao.updateSource(s.copy(section=name.ifBlank { s.name })); repo.invalidate(id) }
    fun remove(s:Source)=mutate { id ->
        dao.deleteSource(s); File(s.originalPath).delete()
        if(s.activePath!=s.originalPath) File(s.activePath).delete()
        repo.invalidate(id)
    }
    fun reorder(items:List<Source>)=mutate { id -> dao.reorder(items); repo.invalidate(id) }
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
