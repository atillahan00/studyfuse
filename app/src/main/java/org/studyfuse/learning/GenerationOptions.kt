package org.studyfuse.learning

import org.json.JSONObject

data class GenerationOptions(val visualTokens:Int?=null,val maxOutputTokens:Int=2200) {
    fun validate(){require(visualTokens==null || visualTokens in setOf(280,560,1120)){"Geçersiz görsel token bütçesi"};require(maxOutputTokens in 64..2200){"Geçersiz yanıt bütçesi"}}
}

/** Numeric telemetry only; no prompt, image, document title or response text. */
data class GenerationTiming(val engineMs:Long,val firstChunkMs:Long?,val generationMs:Long,val characters:Int) {
    fun json()=JSONObject().put("engineMs",engineMs).put("firstChunkMs",firstChunkMs ?: JSONObject.NULL)
        .put("generationMs",generationMs).put("characters",characters)
}
