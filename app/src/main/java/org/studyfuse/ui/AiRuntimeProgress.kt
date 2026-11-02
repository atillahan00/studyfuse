package org.studyfuse.ui

import android.os.SystemClock
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.*
import org.json.JSONObject
import org.studyfuse.learning.AiDiagnostics

/** Read numeric telemetry independently; do not reload all study JSON on every timer tick. */
@Composable fun AiRuntimeProgress(active:Boolean) {
    val context=LocalContext.current;var snapshot by remember{mutableStateOf(JSONObject())};var now by remember{mutableLongStateOf(0)}
    LaunchedEffect(active){while(active){snapshot=withContext(Dispatchers.IO){AiDiagnostics(context).snapshot()};now=SystemClock.elapsedRealtime();delay(2000)}}
    if(active && snapshot.has("stage")){
        val stage=when(snapshot.optString("stage")){
            "CREATING_ENGINE","INITIALIZING_ENGINE"->"Model yükleniyor"
            "CREATING_CONVERSATION"->"İstek hazırlanıyor"
            "VISION_GENERATION","TEXT_GENERATION"->if(snapshot.optInt("characters",0)>0)"Yanıt yazılıyor" else "Girdi işleniyor; ilk parça bekleniyor"
            "COMPLETED"->"Son istek tamamlandı"
            else->"Yerel AI hazırlanıyor"
        }
        val start=snapshot.optLong("generationStartedElapsed",snapshot.optLong("phaseStartedElapsed",now))
        val elapsed=((now-start).coerceAtLeast(0)/1000)
        Text("$stage · ${if(snapshot.optBoolean("gpu"))"GPU" else "CPU"} · $elapsed sn · ${snapshot.optInt("characters",snapshot.optJSONObject("timing")?.optInt("characters") ?:0)} karakter",style=MaterialTheme.typography.bodySmall)
        Text("Süre ve karakter sayısı ölçümdür; karakterler token veya doğruluk puanı değildir.",style=MaterialTheme.typography.bodySmall)
    }
}
