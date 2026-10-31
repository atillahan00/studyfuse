package org.studyfuse.worker

import android.app.*
import android.content.*
import android.content.pm.ServiceInfo
import androidx.core.app.NotificationCompat
import androidx.work.*
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONObject
import org.studyfuse.MainActivity
import org.studyfuse.core.*
import org.studyfuse.learning.*
import java.io.*

class ModelSetupWorker(context:Context,params:WorkerParameters):CoroutineWorker(context,params) {
    override suspend fun doWork():Result=withContext(Dispatchers.IO){transferLock.withLock {
        val store=ModelStore(applicationContext);var locked=false
        try {
            val artifact=ModelCatalog.get(inputData.getString("modelId") ?: error("Model seçilmedi"));val gpu=inputData.getBoolean("gpu",true)
            setForeground(notification("Model indiriliyor; belgeler gönderilmez"))
            val completed=File(store.directory(),"${artifact.sha256}.litertlm")
            val part=if(completed.exists())completed else File(store.directory(),"${artifact.sha256}.part")
            check(store.directory().usableSpace>artifact.bytes-part.length()+64L*1024*1024){"Kurulum için yeterli boş alan yok"}
            val coroutine=currentCoroutineContext();var last=0L
            ModelDownloader().download(artifact,part,{coroutine.ensureActive()}){bytes->
                val now=System.currentTimeMillis()
                if(now-last>=750 || bytes==artifact.bytes){last=now
                    atomicJson(File(store.directory(),"setup.json"),JSONObject().put("status","DOWNLOADING").put("modelId",artifact.id).put("bytes",bytes).put("total",artifact.bytes))
                    setProgressAsync(workDataOf("bytes" to bytes,"total" to artifact.bytes,"stage" to "İndiriliyor / doğrulanıyor"))
                }
            }
            locked=ExamStore.execution.tryLock()
            if(!locked){state("WAITING_AI","İndirme tamamlandı; çalışan AI işlemi bekleniyor");return@withContext Result.retry()}
            state("TESTING","Model bütünlüğü ve görüntü girişi sınanıyor")
            val candidate=store.candidate(artifact,part)
            VisionProbe.run(applicationContext,candidate,gpu)
            store.activate(candidate,gpu)
            state("READY","Çevrimdışı AI hazır. El yazısı ve çözümler kaynakla incelenmelidir.")
            Result.success()
        }catch(e:CancellationException){withContext(NonCancellable){state("PAUSED","Kurulum duraklatıldı; tamamlanan baytlar korunuyor")};throw e}
        catch(e:Exception){val retry=e is IOException && !e.message.orEmpty().contains("SHA-256") && runAttemptCount<3
            state(if(retry)"RETRY" else "FAILED",e.message ?: "Kurulum tamamlanamadı")
            if(retry)Result.retry()else Result.failure(workDataOf("error" to (e.message ?: "Kurulum hatası")))
        }finally{if(locked)ExamStore.execution.unlock()}
    }}
    companion object {val transferLock=Mutex()}
    private fun state(status:String,message:String){
        val file=File(ModelStore(applicationContext).directory(),"setup.json")
        val j=runCatching{readJson(file)}.getOrDefault(JSONObject()).put("status",status).put("message",message)
        atomicJson(file,j);setProgressAsync(workDataOf("stage" to message,"bytes" to j.optLong("bytes",0),"total" to j.optLong("total",0)))
    }
    override suspend fun getForegroundInfo()=notification("Çevrimdışı AI kurulumu")
    private fun notification(text:String):ForegroundInfo {
        val channel="studyfuse_model_setup";applicationContext.getSystemService(NotificationManager::class.java).createNotificationChannel(NotificationChannel(channel,"AI model kurulumu",NotificationManager.IMPORTANCE_LOW))
        val intent=PendingIntent.getActivity(applicationContext,0,Intent(applicationContext,MainActivity::class.java),PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val n=NotificationCompat.Builder(applicationContext,channel).setSmallIcon(org.studyfuse.R.drawable.ic_studyfuse).setContentTitle("StudyFuse · AI kurulumu").setContentText(text).setContentIntent(intent).setOngoing(true)
            .addAction(0,"Duraklat",WorkManager.getInstance(applicationContext).createCancelPendingIntent(id)).build()
        return ForegroundInfo(id.hashCode(),n,ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
    }
}
