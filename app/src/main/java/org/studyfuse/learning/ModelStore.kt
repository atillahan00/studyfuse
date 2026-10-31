package org.studyfuse.learning

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import android.util.AtomicFile
import kotlinx.coroutines.*
import org.json.JSONObject
import org.studyfuse.engine.sha256
import java.io.File
import java.security.MessageDigest

@Synchronized fun atomicJson(file:File,json:JSONObject) {
    file.parentFile!!.mkdirs();val atomic=AtomicFile(file);val out=atomic.startWrite()
    try {out.write(json.toString().toByteArray(Charsets.UTF_8));atomic.finishWrite(out)}catch(t:Throwable){atomic.failWrite(out);throw t}
}
data class LocalModel(val name:String,val file:File,val digest:String,val bytes:Long)
fun visionReady(context:Context,gpu:Boolean):Boolean=runCatching{
    val model=ModelStore(context).selected() ?: return false
    val test=readJson(File(context.filesDir,"local-models/vision-test.json"))
    test.getString("sha256")==model.digest && test.getBoolean("gpu")==gpu && test.getString("fingerprint")==android.os.Build.FINGERPRINT
}.getOrDefault(false)
class ModelStore(private val context:Context) {
    private val root=File(context.filesDir,"local-models").apply{mkdirs()}
    fun directory()=root
    fun candidate(artifact:org.studyfuse.core.ModelArtifact,file:File):LocalModel {
        check(file.length()==artifact.bytes && sha256(file)==artifact.sha256){"İndirilen model doğrulanamadı"}
        val dest=File(root,"${artifact.sha256}.litertlm")
        if(file!=dest){if(dest.exists())dest.delete();check(file.renameTo(dest)){"Model kurulamadı"}}
        return LocalModel(artifact.name,dest,artifact.sha256,artifact.bytes)
    }
    fun activate(model:LocalModel,gpu:Boolean){
        check(model.file.canonicalPath==File(root,"${model.digest}.litertlm").canonicalPath && sha256(model.file)==model.digest)
        atomicJson(File(root,"selected.json"),JSONObject().put("name",model.name).put("sha256",model.digest).put("bytes",model.bytes))
        atomicJson(File(root,"vision-test.json"),JSONObject().put("sha256",model.digest).put("gpu",gpu).put("fingerprint",android.os.Build.FINGERPRINT))
        root.listFiles()?.filter{it.extension=="litertlm" && it!=model.file}?.forEach{it.delete()}
    }
    fun selected():LocalModel? {
        val metadata=File(root,"selected.json");if(!metadata.exists())return null
        val j=JSONObject(AtomicFile(metadata).openRead().bufferedReader().use{it.readText()})
        val hash=j.getString("sha256");require(hash.matches(Regex("[0-9a-f]{64}")))
        val file=File(root,"$hash.litertlm");check(file.isFile && file.length()==j.getLong("bytes")){"Model eksik veya boyutu değişmiş; yeniden içe aktarın"}
        return LocalModel(j.getString("name"),file,hash,file.length())
    }
    suspend fun import(uri:Uri,onProgress:(Long)->Unit):LocalModel=withContext(Dispatchers.IO) {
        val name=context.contentResolver.query(uri,arrayOf(OpenableColumns.DISPLAY_NAME),null,null,null)?.use{if(it.moveToFirst())it.getString(0)else null} ?: "model.litertlm"
        require(name.endsWith(".litertlm",true)){"Görüntü destekli .litertlm modeli seçin; GGUF ve .task desteklenmez"}
        val temp=File(root,"import.part");val md=MessageDigest.getInstance("SHA-256")
        try {
            context.contentResolver.openInputStream(uri)?.use{input->temp.outputStream().use{out->
                val bytes=ByteArray(1024*1024);var total=0L
                while(true){currentCoroutineContext().ensureActive();val n=input.read(bytes);if(n<0)break
                    check(root.usableSpace>n+32L*1024*1024){"Model için yeterli boş alan yok"}
                    out.write(bytes,0,n);md.update(bytes,0,n);total+=n;onProgress(total)}
                out.fd.sync()
            }} ?: error("Model okunamadı")
            require(temp.length()>1024*1024){"Model dosyası eksik veya çok küçük"}
            val hash=md.digest().joinToString(""){"%02x".format(it)};val dest=File(root,"$hash.litertlm")
            if(dest.exists()){check(sha256(dest)==hash);temp.delete()}else check(temp.renameTo(dest)){"Model saklanamadı"}
            atomicJson(File(root,"selected.json"),JSONObject().put("name",name).put("sha256",hash).put("bytes",dest.length()))
            root.listFiles()?.filter{it.extension=="litertlm" && it!=dest}?.forEach{it.delete()}
            LocalModel(name,dest,hash,dest.length())
        }finally{temp.delete()}
    }
}
