package org.studyfuse.data

import org.json.*

object PageCuts {
    fun read(json:String,page:Int):List<Double> {
        val array=JSONObject(json).optJSONArray(page.toString()) ?: return emptyList()
        return (0 until array.length()).map{array.getDouble(it)}
    }
    fun write(json:String,page:Int,cuts:List<Double>):String = JSONObject(json).apply {
        if(cuts.isEmpty()) remove(page.toString()) else put(page.toString(),JSONArray(cuts.distinct().sorted()))
    }.toString()
    fun validate(json:String) {
        val o=JSONObject(json)
        o.keys().forEach {key -> require(key.toInt()>0);val a=o.getJSONArray(key)
            require((0 until a.length()).all{a.getDouble(it).isFinite() && a.getDouble(it)>0})
        }
    }
}
