package org.studyfuse.data

import java.io.*
import java.util.UUID
import java.util.zip.*
import kotlinx.coroutines.*
import org.json.*
import org.studyfuse.engine.sha256

/** Portable originals/settings archive. Entry names and sizes are strictly validated before publication. */
class ProjectArchive(private val repo:Repository) {
    suspend fun export(id:String,dest:OutputStream)=withContext(Dispatchers.IO) {
        val project=repo.dao.project(id) ?: error("Proje bulunamadı")
        check(project.status !in setOf("RUNNING","QUEUED")) {"Önce işlemin bitmesini bekleyin"}
        val sources=repo.dao.snapshot(id)+repo.dao.trash(id)
        val rows=sources.mapIndexed {i,s ->
            check(sha256(File(s.originalPath))==s.sha256) {"Orijinal bütünlüğü bozulmuş: ${s.name}"}
            check(sha256(File(s.activePath))==s.activeSha256) {"Kopya bütünlüğü bozulmuş: ${s.name}"}
            JSONObject().put("name",s.name).put("section",s.section).put("position",s.position).put("removedAt",s.removedAt)
                .put("format",s.format).put("replacement",s.replacement).put("pagination",s.pagination)
                .put("original",JSONObject().put("entry","files/$i/original").put("sha256",s.sha256).put("bytes",File(s.originalPath).length()))
                .put("active",JSONObject().put("entry",if(s.activePath==s.originalPath) "files/$i/original" else "files/$i/active")
                    .put("sha256",s.activeSha256).put("bytes",File(s.activePath).length()))
        }
        val manifest=JSONObject().put("schemaVersion",1).put("name",project.name).put("settings",project.settings).put("sources",JSONArray(rows))
        ZipOutputStream(BufferedOutputStream(dest)).use {zip ->
            zip.putNextEntry(ZipEntry("manifest.json"));zip.write(manifest.toString(2).toByteArray(Charsets.UTF_8));zip.closeEntry()
            for((i,s) in sources.withIndex()) {
                currentCoroutineContext().ensureActive()
                for((entry,file) in listOf("files/$i/original" to File(s.originalPath))+
                    if(s.originalPath==s.activePath) emptyList() else listOf("files/$i/active" to File(s.activePath))) {
                    zip.putNextEntry(ZipEntry(entry));file.inputStream().use {input -> copyChecked(input,zip)};zip.closeEntry()
                }
            }
        }
    }
    suspend fun restore(input:InputStream):String=withContext(Dispatchers.IO) {
        val id=UUID.randomUUID().toString();val dir=repo.directory(id)
        try {
            ZipInputStream(BufferedInputStream(input)).use {zip ->
                require(zip.nextEntry?.name=="manifest.json") {"StudyFuse yedek bildirimi bulunamadı"}
                val raw=ByteArrayOutputStream();copyChecked(zip,raw,8L*1024*1024)
                val manifest=JSONObject(raw.toString("UTF-8"));require(manifest.getInt("schemaVersion")==1) {"Desteklenmeyen yedek sürümü"}
                SettingsCodec.strictDecode(manifest.getString("settings"))
                val rows=manifest.getJSONArray("sources");val expected=mutableMapOf<String,JSONObject>()
                val targets=mutableMapOf<String,File>()
                for(i in 0 until rows.length()) {
                    val r=rows.getJSONObject(i);PageCuts.validate(r.optString("pagination","{}"))
                    org.studyfuse.core.Format.valueOf(r.getString("format"))
                    require(r.optInt("position",i)>=0 && r.optLong("removedAt",0)>=0) {"Geçersiz kaynak sırası"}
                    for(key in listOf("original","active")) {
                        val file=r.getJSONObject(key);val entry=file.getString("entry")
                        require(entry=="files/$i/original" || (key=="active" && entry=="files/$i/active")) {"Geçersiz arşiv yolu"}
                        require(file.getLong("bytes")>=0 && file.getString("sha256").matches(Regex("[0-9a-f]{64}"))) {"Geçersiz dosya bildirimi"}
                        expected[entry]?.let {require(it.toString()==file.toString()) {"Çelişen arşiv kaydı"}}
                        expected[entry]=file
                    }
                }
                require(expected.values.sumOf{it.getLong("bytes")}>=0) {"Geçersiz toplam boyut"}
                var next=zip.nextEntry
                while(next!=null) {
                    currentCoroutineContext().ensureActive()
                    val name=next.name;val info=expected[name] ?: error("Bildirilmeyen arşiv öğesi: $name")
                    require(!next.isDirectory && name !in targets) {"Tekrarlanan arşiv öğesi"}
                    check(info.getLong("bytes")<dir.usableSpace-16*1024*1024) {"Yedek için yeterli boş alan yok"}
                    val file=File(dir,"originals/${UUID.randomUUID()}");file.parentFile!!.mkdirs()
                    file.outputStream().use {copyChecked(zip,it,info.getLong("bytes"))}
                    require(file.length()==info.getLong("bytes") && sha256(file)==info.getString("sha256")) {"Yedek bütünlüğü doğrulanamadı: $name"}
                    targets[name]=file;next=zip.nextEntry
                }
                require(targets.keys==expected.keys) {"Yedekte eksik dosyalar var"}
                val sources=(0 until rows.length()).map {i ->
                    val r=rows.getJSONObject(i);val original=r.getJSONObject("original");val active=r.getJSONObject("active")
                    Source(UUID.randomUUID().toString(),id,r.getString("name"),r.getString("section"),r.optInt("position",i),
                        targets.getValue(original.getString("entry")).path,targets.getValue(active.getString("entry")).path,
                        original.getLong("bytes"),original.getString("sha256"),r.getString("format"),r.getBoolean("replacement"),
                        active.getString("sha256"),r.optString("pagination","{}"),r.optLong("removedAt",0))
                }
                repo.dao.restoreProject(Project(id,manifest.getString("name"),settings=manifest.getString("settings")),sources)
            }
            id
        } catch(e:Exception) {dir.deleteRecursively();throw e}
    }
    private suspend fun copyChecked(input:InputStream,out:OutputStream,limit:Long=Long.MAX_VALUE) {
        val buffer=ByteArray(65536);var total=0L
        while(true) {currentCoroutineContext().ensureActive();val n=input.read(buffer);if(n<0)break
            total+=n;require(total<=limit) {"Arşiv öğesi izin verilen boyutu aşıyor"};out.write(buffer,0,n)}
    }
}
