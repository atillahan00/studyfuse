package org.studyfuse.data

import org.json.JSONObject
import org.studyfuse.core.*

object SettingsCodec {
    fun encode(s:ExportSettings)=JSONObject().put("mode",s.mode.name).put("pageSize",s.pageSize.name)
        .put("margin",s.margin).put("overlap",s.overlap).put("compression",s.compression.name)
        .put("ocr",s.ocr).put("volumeLimitMb",s.volumeLimitMb).toString()
    fun decode(text:String):ExportSettings = runCatching {strictDecode(text)}.getOrDefault(ExportSettings())
    fun strictDecode(text:String):ExportSettings {
        val o=JSONObject(text)
        return ExportSettings(ExportMode.valueOf(o.optString("mode","AI_STUDY")),
            PageSize.valueOf(o.optString("pageSize","AUTO")),o.optDouble("margin",24.0),
            o.optDouble("overlap",18.0),Compression.valueOf(o.optString("compression","LOSSLESS")),
            o.optBoolean("ocr",false),o.optInt("volumeLimitMb",0))
    }
}
