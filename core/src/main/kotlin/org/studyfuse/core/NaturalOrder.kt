package org.studyfuse.core

object NaturalOrder:Comparator<String> {
    override fun compare(a:String,b:String):Int {
        val aa=Regex("[0-9]+|[^0-9]+").findAll(a.lowercase(java.util.Locale.ROOT)).map{it.value}.toList()
        val bb=Regex("[0-9]+|[^0-9]+").findAll(b.lowercase(java.util.Locale.ROOT)).map{it.value}.toList()
        for(i in 0 until minOf(aa.size,bb.size)) {
            val x=aa[i];val y=bb[i]
            val c=if(x[0].isDigit() && y[0].isDigit()) {
                val xx=x.trimStart('0').ifEmpty{"0"};val yy=y.trimStart('0').ifEmpty{"0"}
                xx.length.compareTo(yy.length).takeIf{it!=0} ?: xx.compareTo(yy)
            } else x.compareTo(y)
            if(c!=0)return c
        }
        return aa.size.compareTo(bb.size).takeIf{it!=0} ?: a.compareTo(b)
    }
}
