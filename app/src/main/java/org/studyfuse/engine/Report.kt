package org.studyfuse.engine

import org.json.JSONArray
import org.json.JSONObject
import org.studyfuse.data.Source
import java.io.File
import java.security.MessageDigest

data class PageMapping(val originalPage:Int,val outputPage:Int,val left:Double,val top:Double,
    val right:Double,val bottom:Double,val width:Double,val height:Double,val scale:Double=1.0)
data class FileResult(val source:Source,var status:String="OK",var originalPages:Int=0,var generatedPages:Int=0,
    var start:Int=0,var end:Int=0,val warnings:MutableList<String> = mutableListOf(),
    val mappings:MutableList<PageMapping> = mutableListOf(),var activeSha256:String="") {
    val ocr=mutableListOf<JSONObject>()
    fun json()=JSONObject().put("sourceId",source.id).put("fileName",source.name).put("section",source.section)
        .put("status",status).put("originalSha256",source.sha256).put("activeSha256",activeSha256)
        .put("replacement",source.replacement).put("originalPages",originalPages)
        .put("generatedContentPages",generatedPages).put("startPage",start).put("endPage",end)
        .put("warnings",JSONArray(warnings)).put("ocr",JSONArray(ocr)).put("coverage",JSONArray(mappings.map {
            JSONObject().put("originalPage",it.originalPage).put("outputPage",it.outputPage)
                .put("sourceBounds",JSONArray(listOf(it.left,it.top,it.right,it.bottom)))
                .put("sourceSize",JSONArray(listOf(it.width,it.height))).put("scale",it.scale)
        }))
}
data class Artifact(val file:File,val pages:Int,val start:Int,val end:Int) {
    fun json()=JSONObject().put("path",file.path).put("name",file.name).put("pages",pages)
        .put("startPage",start).put("endPage",end).put("bytes",file.length()).put("sha256",sha256(file))
}
fun sha256(file:File):String {
    val md=MessageDigest.getInstance("SHA-256")
    file.inputStream().use { input ->
        val buffer=ByteArray(65536)
        while(true) { val n=input.read(buffer); if(n<0) break; md.update(buffer,0,n) }
    }
    return md.digest().joinToString("") { "%02x".format(it) }
}
