package org.studyfuse.learning

import org.json.*
import org.studyfuse.core.*

/** The app owns the literal quote. The model selects a short label rather than retyping it.
 * A resolved reference proves location/existence, never that the generated claim follows from it. */
class CoachEvidence(private val passages:List<EvidencePassage>) {
    data class Span(val label:String,val passage:EvidencePassage,val text:String)
    init {require(passages.map{it.id}.distinct().size==passages.size){"Tekrarlanan kaynak kimliği"}}
    val spans=buildList {
        passages.forEach { p ->
            var offset=0
            while(offset<p.text.length) {
                var end=minOf(offset+480,p.text.length)
                if(end<p.text.length) {
                    val boundary=p.text.lastIndexOf('\n',end-1).takeIf{it>offset+160}
                        ?: p.text.lastIndexOf(' ',end-1).takeIf{it>offset+160}
                    if(boundary!=null)end=boundary+1
                    if(p.text.length-end<8)end=p.text.length
                    if(end<p.text.length && Character.isHighSurrogate(p.text[end-1]) && Character.isLowSurrogate(p.text[end]))end--
                }
                val text=p.text.substring(offset,end)
                if(text.isNotBlank())add(Span("E${size+1}",p,text))
                offset=end
            }
        }
    }
    fun excerpts()=JSONArray(passages.mapIndexed { index,p -> JSONObject().put("id","P${index+1}")
        .put("source",p.source).put("page",p.page).put("reference",p.reference)
        .put("unverifiedVisualReading",p.unverifiedVisualReading)
        .put("evidence",JSONArray(spans.filter{it.passage.id==p.id}.map{JSONObject().put("id",it.label).put("text",it.text)})) })
    fun resolve(item:JSONObject):JSONObject {
        val label=item.optString("evidence").ifBlank{
            val id=item.optString("id")
            if(spans.any{it.label==id} && passages.none{it.id==id})id else ""
        }
        if(label.isNotBlank()) {
            val span=spans.singleOrNull{it.label==label} ?: error("Kaynak etiketi listede yok")
            require(EvidenceRules.normalize(span.text).length>=8){"Kaynak parçası kanıt için çok kısa"}
            return JSONObject().put("id",span.passage.id).put("quote",span.text)
        }
        // Compatibility with an older/manual model that still emits literal quotes. No fuzzy match.
        val id=item.getString("id")
        val p=passages.firstOrNull{it.id==id} ?: passages.getOrNull(id.removePrefix("P").toIntOrNull()?.minus(1) ?: -1)?.takeIf{id.startsWith("P")}
            ?: error("Bilinmeyen kaynak atfı")
        val quote=item.getString("quote")
        require(EvidenceRules.contains(p.text,quote)){"Alıntı kaynakta aynen bulunmuyor"}
        return JSONObject().put("id",p.id).put("quote",quote)
    }
}
