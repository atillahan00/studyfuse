package org.studyfuse.core

import java.time.LocalDate
import java.util.ArrayDeque
import kotlin.math.*

enum class KnowledgeMode { DOCUMENTS_ONLY, ENRICHED }
enum class RecallGrade { AGAIN, HARD, GOOD }
data class RecallProgress(val stage:Int=0,val dueDay:Long=0,val attempts:Int=0,val lapses:Int=0)

/** Transparent expanding intervals; a practical heuristic, not a validated memory predictor. */
object RecallSchedule {
    private val intervals=listOf(1L,3L,7L,14L,30L,60L)
    fun assess(old:RecallProgress,grade:RecallGrade,today:LocalDate,exam:LocalDate?=null):RecallProgress {
        require(old.stage in 0..6 && old.attempts>=0 && old.lapses>=0)
        val stage=when(grade){RecallGrade.AGAIN->0;RecallGrade.HARD->(old.stage-1).coerceAtLeast(0);RecallGrade.GOOD->(old.stage+1).coerceAtMost(6)}
        val days=when(grade){RecallGrade.AGAIN->0L;RecallGrade.HARD->1L;RecallGrade.GOOD->intervals[stage-1]}
        val due=today.plusDays(days).toEpochDay().let{if(exam!=null && !exam.isBefore(today))minOf(it,exam.toEpochDay())else it}
        return RecallProgress(stage,due,old.attempts+1,old.lapses+if(grade==RecallGrade.AGAIN)1 else 0)
    }
    fun <T> interleave(items:List<T>,topic:(T)->String):List<T> {
        val groups=linkedMapOf<String,ArrayDeque<T>>()
        items.forEach{groups.getOrPut(topic(it)){ArrayDeque()}.add(it)}
        return buildList{while(groups.isNotEmpty()){val iterator=groups.entries.iterator();while(iterator.hasNext()){
            val queue=iterator.next().value;add(queue.removeFirst());if(queue.isEmpty())iterator.remove()
        }}}
    }
}

object EvidenceRules {
    fun normalize(s:String)=s.replace(Regex("\\s+")," ").trim()
    fun contains(text:String,quote:String):Boolean = normalize(quote).let{it.length>=8 && normalize(text).contains(it)}
    fun permitted(mode:KnowledgeMode,origin:String)=origin=="document" || (mode==KnowledgeMode.ENRICHED && origin=="ai_added")
}

/** Normalized rectangles allow Android rendering without keeping whole images in memory. */
data class StudyRegion(val left:Double,val top:Double,val right:Double,val bottom:Double)
object VisionRegions {
    fun plan(detailed:Boolean):List<StudyRegion> = listOf(StudyRegion(0.0,0.0,1.0,1.0)) +
        if(detailed)listOf(StudyRegion(0.0,0.0,.55,.55),StudyRegion(.45,0.0,1.0,.55),
            StudyRegion(0.0,.45,.55,1.0),StudyRegion(.45,.45,1.0,1.0))else emptyList()
}
