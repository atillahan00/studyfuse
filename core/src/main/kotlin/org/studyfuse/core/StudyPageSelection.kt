package org.studyfuse.core

/** Inclusive printed-PDF page ranges, stored without expanding large selections. */
class StudyPageSelection private constructor(val ranges:List<IntRange>) {
    operator fun contains(page:Int)=ranges.any{page in it}
    fun countInside(first:Int,last:Int):Long=ranges.sumOf{r->(minOf(last,r.last).toLong()-maxOf(first,r.first)+1).coerceAtLeast(0)}
    fun coveredBy(allowed:List<IntRange>):Boolean=ranges.all{wanted->
        var next=wanted.first.toLong()
        for(r in allowed.sortedBy{it.first}){if(r.last<next)continue;if(r.first>next)break;next=maxOf(next,r.last.toLong()+1);if(next>wanted.last)break}
        next>wanted.last
    }
    companion object {
        fun parse(value:String):StudyPageSelection {
            require(value.length<=2000){"Sayfa seçimi en fazla 2000 karakter olmalı"}
            if(value.isBlank())return StudyPageSelection(emptyList())
            val sorted=value.split(',').map{part->
                val m=Regex("\\s*([0-9]+)\\s*(?:-\\s*([0-9]+)\\s*)?").matchEntire(part) ?: error("Sayfaları 3,5-7 biçiminde yazın")
                val first=m.groupValues[1].toIntOrNull() ?: error("Geçersiz sayfa numarası")
                val last=m.groupValues[2].ifBlank{m.groupValues[1]}.toIntOrNull() ?: error("Geçersiz sayfa numarası")
                require(first>0 && last>=first){"Sayfa aralığı pozitif ve artan olmalı"};first..last
            }.sortedBy{it.first}
            val merged=mutableListOf<IntRange>()
            for(r in sorted){val old=merged.lastOrNull();if(old!=null && r.first.toLong()<=old.last.toLong()+1)merged[merged.lastIndex]=old.first..maxOf(old.last,r.last)else merged+=r}
            return StudyPageSelection(merged)
        }
    }
}
