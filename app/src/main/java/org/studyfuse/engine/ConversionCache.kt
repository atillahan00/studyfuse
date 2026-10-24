package org.studyfuse.engine

import java.io.File
import java.security.MessageDigest
import java.util.UUID
import org.json.*
import org.studyfuse.core.ExportSettings
import org.studyfuse.data.*

/** Committed, verified per-source checkpoints survive cancellation and process death. */
class ConversionCache(private val root:File) {
    fun key(source:Source,settings:ExportSettings):String {
        val options=JSONObject(SettingsCodec.encode(settings)).apply {remove("mode");remove("volumeLimitMb")}
        return MessageDigest.getInstance("SHA-256").digest(("studyfuse-converter-2|${source.id}|${source.activeSha256}|${source.format}|${source.pagination}|$options").toByteArray())
            .joinToString(""){"%02x".format(it)}
    }
    fun restore(key:String,to:File,r:FileResult):Boolean {
        val dir=File(root,key)
        return try {
            val metadata=JSONObject(File(dir,"result.json").readText());val file=File(dir,"content.pdf")
            check(sha256(file)==metadata.getString("pdfSha256"))
            val o=metadata.getJSONObject("result")
            r.status=o.getString("status");r.originalPages=o.getInt("originalPages");r.generatedPages=o.getInt("generatedContentPages")
            r.visualPages=o.getInt("visualPages")
            val warnings=o.getJSONArray("warnings");for(i in 0 until warnings.length()) r.warnings+=warnings.getString(i)
            val mapping=o.getJSONArray("coverage")
            for(i in 0 until mapping.length()) {
                val m=mapping.getJSONObject(i);val b=m.getJSONArray("sourceBounds");val s=m.getJSONArray("sourceSize")
                r.mappings+=PageMapping(m.getInt("originalPage"),m.getInt("outputPage"),b.getDouble(0),b.getDouble(1),b.getDouble(2),b.getDouble(3),s.getDouble(0),s.getDouble(1),m.getDouble("scale"))
            }
            val ocr=o.getJSONArray("ocr");for(i in 0 until ocr.length())r.ocr+=ocr.getJSONObject(i)
            file.copyTo(to,true);r.cached=true;true
        } catch(_:Exception) {dir.deleteRecursively();r.mappings.clear();r.ocr.clear();r.warnings.clear();r.status="OK";false}
    }
    fun save(key:String,file:File,r:FileResult) {
        root.mkdirs();val staging=File(root,"${UUID.randomUUID()}.part").apply{mkdirs()}
        try {
            file.copyTo(File(staging,"content.pdf"))
            File(staging,"result.json").writeText(JSONObject().put("pdfSha256",sha256(file)).put("result",r.json()).toString())
            val target=File(root,key)
            if(!staging.renameTo(target)) staging.deleteRecursively()
        } finally {staging.deleteRecursively()}
    }
}
