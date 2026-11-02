package org.studyfuse.learning

import android.app.ActivityManager
import android.app.ApplicationExitInfo
import android.content.Context
import android.os.Build
import android.os.Process
import org.json.JSONObject
import org.studyfuse.BuildConfig
import java.io.File

/** Local startup metadata only: never stores prompts, responses, document names or crash dumps. */
class AiDiagnostics(private val context:Context) {
    private val file=File(context.filesDir,"local-models/ai-runtime.json")
    fun snapshot():JSONObject=runCatching{readJson(file)}.getOrDefault(JSONObject())
    fun stage(stage:String,gpu:Boolean,image:Boolean,visualTokens:Int?=null) = synchronized(stateLock) {
        val memory=ActivityManager.MemoryInfo().also{context.getSystemService(ActivityManager::class.java).getMemoryInfo(it)}
        atomicJson(file,JSONObject().put("stage",stage).put("pid",Process.myPid()).put("at",System.currentTimeMillis())
            .put("phaseStartedElapsed",android.os.SystemClock.elapsedRealtime()).apply{if(visualTokens!=null)put("visualTokens",visualTokens)}
            .put("gpu",gpu).put("image",image).put("availableBytes",memory.availMem).put("lowMemory",memory.lowMemory))
    }
    fun generationProgress(characters:Int,started:Long,firstChunkMs:Long?)=synchronized(stateLock){
        val state=snapshot()
        if(state.optString("stage") in setOf("VISION_GENERATION","TEXT_GENERATION"))atomicJson(file,state.put("characters",characters).put("generationStartedElapsed",started).put("firstChunkMs",firstChunkMs ?: JSONObject.NULL))
    }
    fun completedTiming(timing:GenerationTiming)=synchronized(stateLock){atomicJson(file,snapshot().put("timing",timing.json()))}
    fun javaFailure(error:Throwable)=synchronized(stateLock){runCatching{atomicJson(file,snapshot().put("errorClass",error.javaClass.simpleName))}}
    fun fatal(error:Throwable)=synchronized(stateLock){runCatching{
        val state=snapshot().put("pid",Process.myPid()).put("fatalClass",error.javaClass.name)
        if(!state.has("at"))state.put("at",System.currentTimeMillis())
        val frames=error.stackTrace.asSequence().filter{frame->
            listOf("org.studyfuse.","com.google.ai.edge.litertlm.","kotlinx.coroutines.","kotlin.","java.","android.").any{frame.className.startsWith(it)} &&
                frame.className.matches(Regex("[A-Za-z0-9_.$]+")) && frame.methodName.matches(Regex("[A-Za-z0-9_$<>-]+"))
        }.take(12).map{"${it.className.take(180)}.${it.methodName.take(100)}:${it.lineNumber}"}.toList()
        state.put("fatalFrames",org.json.JSONArray(frames));atomicJson(file,state)
    }}
    fun summary():String {
        val state=snapshot();val exit=if(Build.VERSION.SDK_INT>=30 && state.optInt("pid",0)>0)runCatching{
            context.getSystemService(ActivityManager::class.java).getHistoricalProcessExitReasons(context.packageName,state.getInt("pid"),4)
                .firstOrNull{it.processName==context.packageName+":local_ai" && it.timestamp>=state.optLong("at",0)-1000}
        }.getOrNull()else null
        return buildString {
            appendLine("StudyFuse ${BuildConfig.VERSION_NAME} · LiteRT-LM 0.16.1")
            appendLine("${Build.MANUFACTURER} ${Build.MODEL} · Android ${Build.VERSION.RELEASE} / API ${Build.VERSION.SDK_INT}")
            appendLine("Aşama: ${state.optString("stage","Henüz yerel AI kaydı yok")}")
            if(state.has("gpu"))appendLine("Dil: ${if(state.getBoolean("gpu"))"GPU" else "CPU"} · Görüntü: ${if(state.optBoolean("image"))"GPU" else "yok"}")
            if(state.has("availableBytes"))appendLine("Son aşamada boş RAM: ${state.getLong("availableBytes")/(1024*1024)} MiB · düşük bellek: ${state.optBoolean("lowMemory")}")
            if(state.has("visualTokens"))appendLine("Görsel token bütçesi: ${state.getInt("visualTokens")}")
            state.optJSONObject("timing")?.let{appendLine("Son istek: model ${it.optLong("engineMs")} ms; ilk parça ${it.opt("firstChunkMs")} ms; üretim ${it.optLong("generationMs")} ms; ${it.optInt("characters")} karakter")}
            if(state.has("fatalClass")){appendLine("Yakalanmamış hata sınıfı: ${state.getString("fatalClass")}")
                val frames=state.optJSONArray("fatalFrames");if(frames!=null)for(i in 0 until frames.length())appendLine(frames.getString(i))}
            if(state.has("errorClass"))appendLine("Java hatası: ${state.getString("errorClass")}")
            if(exit!=null && Build.VERSION.SDK_INT>=30)appendLine("Android çıkış nedeni: ${exitReason(exit.reason)} · durum/sinyal: ${exit.status} · PSS: ${exit.pss} KiB")
            else appendLine(if(Build.VERSION.SDK_INT<30)"Android 10 süreç çıkış kaydı sağlamaz." else "Android henüz eşleşen süreç çıkış nedeni sağlamadı.")
            append("Bu kayıt belge, istem, model yanıtı veya çökme dökümü içermez.")
        }
    }
    companion object {
        private val stateLock=Any()
        fun exitReason(reason:Int)=when(reason){
            ApplicationExitInfo.REASON_LOW_MEMORY->"LOW_MEMORY (bellek nedeniyle kapatıldı)"
            ApplicationExitInfo.REASON_CRASH_NATIVE->"CRASH_NATIVE (yerel kütüphane çöktü)"
            ApplicationExitInfo.REASON_CRASH->"CRASH (uygulama hatası)"
            ApplicationExitInfo.REASON_SIGNALED->"SIGNALED (sinyalle kapatıldı)"
            ApplicationExitInfo.REASON_ANR->"ANR (yanıt vermedi)"
            ApplicationExitInfo.REASON_EXIT_SELF->"EXIT_SELF"
            ApplicationExitInfo.REASON_USER_REQUESTED->"USER_REQUESTED"
            else->"OTHER ($reason)"
        }
    }
}

/** A native process death is distinct from a model answer that failed the image probe. */
class AiProcessStopped(message:String):IllegalStateException(message)

suspend fun testSetupBackends(preferGpu:Boolean,onFallback:(String)->Unit,probe:suspend (Boolean)->Unit):Boolean {
    try{probe(preferGpu);return preferGpu}
    catch(e:kotlinx.coroutines.CancellationException){throw e}
    catch(first:Exception){
        if(!preferGpu)throw first
        onFallback(first.message.orEmpty().take(1400))
        try{probe(false);return false}
        catch(e:kotlinx.coroutines.CancellationException){throw e}
        catch(second:Exception){throw IllegalStateException("GPU ve CPU dil modeli denemeleri başarısız. Görüntü kodlayıcı GPU kullanır. İndirilen model korundu. Son hata: ${second.message.orEmpty().take(1800)}",second)}
    }
}
