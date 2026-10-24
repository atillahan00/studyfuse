package org.studyfuse.data

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.documentfile.provider.DocumentFile
import java.io.File
import java.security.MessageDigest
import java.util.UUID
import kotlinx.coroutines.*
import org.studyfuse.core.Format
import org.studyfuse.core.NaturalOrder

class Repository(val context:Context,val dao:StudyDao) {
    fun directory(id:String)=File(context.filesDir,"projects/$id").apply { mkdirs() }
    suspend fun create():String {
        val id=UUID.randomUUID().toString()
        dao.addProject(Project(id,"Yeni çalışma"))
        return id
    }
    suspend fun import(projectId:String,uri:Uri,replace:Source?=null):Source = withContext(Dispatchers.IO) {
        val project=dao.project(projectId) ?: error("Proje bulunamadı")
        check(project.status !in setOf("QUEUED","RUNNING")) { "İşlem sırasında kaynaklar değiştirilemez" }
        val name=context.contentResolver.query(uri,arrayOf(OpenableColumns.DISPLAY_NAME),null,null,null)?.use {
            if(it.moveToFirst()) it.getString(0) else null
        } ?: "Paylaşılan dosya"
        val id=UUID.randomUUID().toString()
        val dir=File(directory(projectId),"originals").apply { mkdirs() }
        val tmp=File(dir,"$id.part")
        val dest=File(dir,id)
        val digest=MessageDigest.getInstance("SHA-256")
        try {
            context.contentResolver.openInputStream(uri)?.use { input ->
                tmp.outputStream().use { output ->
                    val buffer=ByteArray(64*1024)
                    while(true) {
                        ensureActive()
                        val n=input.read(buffer); if(n<0) break
                        output.write(buffer,0,n); digest.update(buffer,0,n)
                    }
                    output.fd.sync()
                }
            } ?: error("Dosya okunamadı")
            check(tmp.length()>0) { "Dosya boş" }
            val head=tmp.inputStream().use { it.readPrefix(32) }
            val format=Format.detect(name,head)
            if(replace!=null) check(format==Format.PDF) { "Yerine koymak için PDF seçin" }
            check(tmp.renameTo(dest)) { "Dosya saklanamadı" }
            val hash=digest.digest().joinToString("") { "%02x".format(it) }
            val source=if(replace==null) Source(id,projectId,name,name.substringBeforeLast('.',name),
                (dao.snapshot(projectId).maxOfOrNull { it.position } ?: -1)+1,
                dest.path,dest.path,dest.length(),hash,format.name)
            else replace.copy(activePath=dest.path,format=Format.PDF.name,replacement=true,activeSha256=hash)
            dao.commitImport(source,replace!=null)
            if(replace!=null && replace.activePath!=replace.originalPath) runCatching {File(replace.activePath).delete()}
            source
        } catch(e:Exception) { tmp.delete(); if(replace==null || dest.path!=replace.originalPath) dest.delete(); throw e }
    }
    suspend fun folder(projectId:String,uri:Uri,onProgress:(String)->Unit):List<String> {
        val failures=mutableListOf<String>()
        val queue=ArrayDeque<DocumentFile>()
        queue.add(DocumentFile.fromTreeUri(context,uri) ?: error("Klasör açılamadı"))
        val visited=mutableSetOf<String>()
        while(queue.isNotEmpty()) {
            currentCoroutineContext().ensureActive()
            val next=queue.removeFirst()
            if(!visited.add(next.uri.toString())) continue
            if(next.isDirectory) {
                try { check(next.canRead()) {"Klasör okuma izni yok"};queue.addAll(next.listFiles().sortedWith {a,b->NaturalOrder.compare(a.name ?: "",b.name ?: "")}) }
                catch(e:Exception) { failures+="${next.name}: ${e.message}" }
            } else if(next.isFile) {
                onProgress(next.name ?: "Dosya")
                try { import(projectId,next.uri) }
                catch(e:CancellationException) { throw e }
                catch(e:Exception) { failures+="${next.name}: ${e.message}" }
            } else failures+="${next.name}: Okunamayan klasör öğesi"
        }
        return failures
    }
    suspend fun invalidate(id:String) {
        dao.project(id)?.let { dao.updateProject(it.copy(status="READY",output=null,report=null,message="",workId=null)) }
    }
}
