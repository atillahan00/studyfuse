package org.studyfuse.core

import java.text.Normalizer
import java.util.Locale
import kotlin.math.*

data class EvidencePassage(val id:String,val text:String,val section:String,val page:Int,val source:String,val reference:Boolean=false,val sourceId:String=source)
object StudySearch {
    fun tokens(text:String):List<String> = Regex("[\\p{L}\\p{N}]{2,}").findAll(Normalizer.normalize(text.lowercase(Locale.ROOT).replace('ı','i').replace("ß","ss"),Normalizer.Form.NFD).replace(Regex("\\p{M}"),""))
        .map{it.value}.filter{it !in setOf("the","and","der","die","das","und","bir","ile","what","was","wie","neden","explain","erklare")}.toList()
    /** Lexical retrieval, not a claim of full-document semantic understanding. */
    fun retrieve(passages:List<EvidencePassage>,query:String,limit:Int=6,budget:Int=11000,diverse:Boolean=false):List<EvidencePassage> {
        val terms=tokens(query).toSet();if(terms.isEmpty())return emptyList()
        val average=passages.map{tokens(it.text).size}.average().let{if(it.isNaN())1.0 else max(it,1.0)}
        val documentTerms=passages.map{tokens(it.text)}
        val frequency=terms.associateWith{word->documentTerms.count{word in it}}
        val ranked=passages.mapIndexed{i,p->val words=documentTerms[i];val counts=words.groupingBy{it}.eachCount()
            val score=terms.sumOf{term->val tf=(counts[term] ?:0).toDouble();val df=frequency.getValue(term)
                ln(1+(passages.size-df+.5)/(df+.5))*tf*2.2/(tf+1.2*(.25+.75*words.size/average))}+
                tokens(p.section).count{it in terms}*.5
            p to score
        }.filter{it.second>0}.sortedByDescending{it.second}
        var used=0
        val ordered=ranked.map{it.first}.distinctBy{it.id}.let{if(diverse)RecallSchedule.interleave(it){p->p.sourceId}else it}
        return ordered.filter{p->if(p.text.length>budget-used)false else{used+=p.text.length;true}}.take(limit)
    }
}
data class StudyTask(val topic:String,val questions:Int,val reason:String)
object StudyPlanner {
    fun daily(topics:List<String>,due:Map<String,Int>,lapses:Map<String,Int>,minutes:Int):List<StudyTask> {
        require(minutes in 5..240)
        var remaining=max(1,minutes/3)
        return topics.distinct().sortedWith(compareByDescending<String>{due[it] ?:0}.thenByDescending{lapses[it] ?:0}).mapNotNull{topic->
            if(remaining<=0)null else {val count=minOf(max(1,due[topic] ?:0),remaining);remaining-=count
                StudyTask(topic,count,if((lapses[topic] ?:0)>0)"Önceki hatalar ve kaynakla karşılaştırma" else if((due[topic] ?:0)>0)"Bugün zamanı gelen geri çağırma" else "Kaynağı kapatıp açıkla; bir örnek çöz")}
        }
    }
    fun remainingSeconds(deadline:Long,now:Long)=((deadline-now).coerceAtLeast(0)+999)/1000
}
