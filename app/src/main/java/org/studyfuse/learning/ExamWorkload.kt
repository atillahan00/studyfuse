package org.studyfuse.learning

import org.json.JSONObject
import org.studyfuse.core.*

data class ExamWorkload(val pages:Int,val calls:Int) {
    companion object {
        fun plan(report:JSONObject,config:ExamConfig):ExamWorkload {
            config.validate();val selection=StudyPageSelection.parse(config.detailPages)
            val rows=report.getJSONArray("files").objects().filter{
                (config.mode==KnowledgeMode.ENRICHED || it.getString("originalSha256") !in config.references) && it.optString("status") !in setOf("FAILED","FALLBACK")}
            val ranges=rows.filter{it.getInt("visualPages")>0}.map{val start=it.getInt("contentStart");val end=start.toLong()+it.getInt("visualPages")-1
                require(start>0 && end<=Int.MAX_VALUE);start..end.toInt()}
            if(!config.detailed)require(selection.coveredBy(ranges)){"Yakın plan seçimi, işlenen kaynaklarda olmayan PDF sayfaları içeriyor"}
            val pages=ranges.sumOf{it.last.toLong()-it.first+1}
            val details=if(config.detailed)pages else ranges.sumOf{selection.countInside(it.first,it.last)}
            val calls=pages+details*4;require(calls<=Int.MAX_VALUE){"İşlem sayısı desteklenen ilerleme aralığını aşıyor"}
            return ExamWorkload(pages.toInt(),calls.toInt())
        }
    }
}
