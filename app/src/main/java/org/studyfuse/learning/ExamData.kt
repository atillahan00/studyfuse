package org.studyfuse.learning

import android.util.AtomicFile
import kotlinx.coroutines.sync.Mutex
import org.json.*
import org.studyfuse.core.*
import org.studyfuse.engine.sha256
import java.io.File
import java.security.MessageDigest
import java.time.LocalDate

data class ExamConfig(val mode:KnowledgeMode=KnowledgeMode.DOCUMENTS_ONLY,val language:String="Deutsch",val focus:String="",
    val examDate:String="",val detailed:Boolean=true,val gpu:Boolean=true,val references:Set<String> = emptySet()) {
    fun json()=JSONObject().put("mode",mode.name).put("language",language).put("focus",focus).put("examDate",examDate)
        .put("detailed",detailed).put("gpu",gpu).put("references",JSONArray(references.sorted()))
    fun validate(){require(language in listOf("Deutsch","Türkçe","English"));require(focus.length<=2000){"Sınav bağlamı en fazla 2000 karakter olmalı"};require(references.all{it.matches(Regex("[0-9a-f]{64}"))});if(examDate.isNotBlank())LocalDate.parse(examDate)}
    companion object {fun decode(j:JSONObject)=ExamConfig(KnowledgeMode.valueOf(j.optString("mode","DOCUMENTS_ONLY")),j.optString("language","Deutsch"),
        j.optString("focus"),j.optString("examDate"),j.optBoolean("detailed",true),j.optBoolean("gpu",true),
        j.optJSONArray("references")?.let{a->(0 until a.length()).map{a.getString(it)}.toSet()} ?: emptySet()).also{it.validate()}}
}
fun digest(text:String)=MessageDigest.getInstance("SHA-256").digest(text.toByteArray()).joinToString(""){"%02x".format(it)}
fun readJson(file:File):JSONObject=JSONObject(AtomicFile(file).openRead().bufferedReader().use{it.readText()})
fun JSONArray.objects()=(0 until length()).map{getJSONObject(it)}
fun canonical(value:Any?):String=when(value){
    is JSONObject->value.keys().asSequence().sorted().joinToString(",","{","}"){JSONObject.quote(it)+":"+canonical(value.get(it))}
    is JSONArray->(0 until value.length()).joinToString(",","[","]"){canonical(value.get(it))}
    is String->JSONObject.quote(value)
    null,JSONObject.NULL->"null"
    else->value.toString()
}

class ExamStore(val project:File) {
    val root=File(project,"exam").apply{mkdirs()}
    val configFile=File(root,"settings.json")
    fun config()=if(configFile.exists())ExamConfig.decode(readJson(configFile))else ExamConfig()
    fun saveConfig(config:ExamConfig){config.validate();atomicJson(configFile,config.json())}
    fun signature(report:File,model:LocalModel,config:ExamConfig):String {
        val json=readJson(report)
        val snapshot=JSONObject().put("settings",json.getJSONObject("settings")).put("files",JSONArray(json.getJSONArray("files").objects().map{row->
            JSONObject().apply{for(key in listOf("fileName","section","status","originalSha256","activeSha256","contentStart","visualPages","coverage"))put(key,row.get(key))}
        }))
        return digest("exam-v1|${canonical(snapshot)}|${model.digest}|${canonical(config.json())}")
    }
    fun unit(key:String)=File(root,"units/$key.json")
    fun session():JSONObject?=File(root,"session.json").let{if(it.exists())readJson(it)else null}
    fun saveSession(j:JSONObject)=atomicJson(File(root,"session.json"),j)
    fun review():JSONObject=File(root,"review.json").let{if(it.exists())readJson(it)else JSONObject()}
    @Synchronized fun updateReview(id:String,change:(JSONObject)->Unit){val all=review();val item=all.optJSONObject(id) ?: JSONObject();change(item);all.put(id,item);atomicJson(File(root,"review.json"),all)}
    fun currentUnits(signature:String):List<JSONObject> {
        val session=session() ?: return emptyList();if(session.optString("signature")!=signature)return emptyList()
        return session.getJSONArray("units").let{a->(0 until a.length()).mapNotNull{index->val f=unit(a.getString(index));if(f.exists())readJson(f)else null}}
    }
    companion object {val execution=Mutex()}
}

object ExamResponse {
    private val kinds=setOf("note","recall","self_explanation","worked_example","application","diagram")
    /** Structural evidence checks are not semantic verification or a handwriting accuracy guarantee. */
    fun parse(raw:String,mode:KnowledgeMode,pdfText:String,key:String):JSONObject {
        val trimmed=raw.trim().removePrefix("```json").removePrefix("```").removeSuffix("```").trim()
        val j=JSONObject(trimmed);val transcript=j.getString("transcription");j.getString("observations")
        val uncertainties=j.getJSONArray("uncertainties");for(i in 0 until uncertainties.length())uncertainties.getString(i)
        val items=j.getJSONArray("items");val accepted=JSONArray();val rejected=JSONArray()
        for(i in 0 until items.length()) {
            val item=items.getJSONObject(i);val origin=item.getString("origin");val quote=item.getString("evidence")
            val reason=when {
                item.getString("kind") !in kinds->"Bilinmeyen çalışma türü"
                !EvidenceRules.permitted(mode,origin)->"Seçilen kaynak moduna aykırı ek bilgi"
                item.getString("question").isBlank() || item.getString("answer").isBlank()->"Boş soru veya yanıt"
                origin=="document" && !EvidenceRules.contains(pdfText,quote) && !EvidenceRules.contains(transcript,quote)->"Kanıt alıntısı kaynak metninde veya görsel transkripsiyonda bulunamadı"
                else->null
            }
            if(reason!=null)rejected.put(JSONObject().put("reason",reason).put("item",item))else {
                item.put("id",digest("$key|$i|$item"));item.put("basis",if(origin=="ai_added")"model_knowledge" else if(EvidenceRules.contains(pdfText,quote))"pdf_text" else "unverified_visual")
                accepted.put(item)
            }
        }
        return JSONObject().put("transcription",transcript).put("observations",j.getString("observations")).put("uncertainties",uncertainties)
            .put("items",accepted).put("rejected",rejected).put("status","REVIEW")
    }
}
