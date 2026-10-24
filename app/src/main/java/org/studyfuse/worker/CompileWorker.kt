package org.studyfuse.worker

import android.app.*
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import androidx.core.app.NotificationCompat
import androidx.work.*
import kotlinx.coroutines.*
import org.json.JSONArray
import org.studyfuse.*
import org.studyfuse.data.SettingsCodec
import org.studyfuse.engine.Compiler
import java.io.File

class CompileWorker(context:Context,params:WorkerParameters):CoroutineWorker(context,params) {
    override suspend fun doWork():Result {
        val app=applicationContext as StudyFuseApp
        val dao=app.database.dao()
        val projectId=inputData.getString("projectId") ?: return Result.failure()
        val project=dao.project(projectId) ?: return Result.failure()
        if(project.workId!=id.toString()) return Result.success()
        val run=File(app.repository.directory(projectId),"exports/$id").apply { mkdirs() }
        var successful=false
        try {
            setForeground(notification("Hazırlanıyor"))
            dao.workStatus(projectId,id.toString(),"RUNNING","Hazırlanıyor")
            var lastUpdate=0L
            val settings=SettingsCodec.decode(project.settings)
            val result=Compiler(applicationContext,run,File(app.repository.directory(projectId),"conversion-cache")).compile(dao.snapshot(projectId),settings) { p ->
                currentCoroutineContext().ensureActive()
                val now=System.currentTimeMillis()
                if(now-lastUpdate>300 || p.processed==p.total) {
                    lastUpdate=now
                    val message="${p.file} · ${p.stage}"
                    setProgress(workDataOf("file" to p.file,"stage" to p.stage,"processed" to p.processed,"total" to p.total))
                    dao.workStatus(projectId,id.toString(),"RUNNING",message)
                    setForeground(notification(message))
                }
            }
            val published=dao.publish(projectId,id.toString(),if(result.complete) "DONE" else if(result.allSourcesIncluded) "REVIEW" else "INCOMPLETE",
                JSONArray(result.outputs.map { it.json() }).toString(),result.report.path,
                "${result.pages} sayfa · ${if(result.complete) "Hazır" else if(result.allSourcesIncluded) "Görsel inceleme gerekli" else "Eksik kaynak var"}")
            if(!published) return Result.failure()
            successful=true
            // The last published generation remains; obsolete runs and intermediate files are removed.
            run.parentFile?.listFiles()?.filter { it.isDirectory && it!=run }?.forEach { it.deleteRecursively() }
            return Result.success()
        } catch(e:CancellationException) {
            withContext(NonCancellable) { dao.workStatus(projectId,id.toString(),"CANCELLED","İşlem iptal edildi. Orijinaller korundu; yeniden deneyebilirsiniz.") }
            throw e
        } catch(e:Exception) {
            dao.workStatus(projectId,id.toString(),"FAILED",e.message ?: "İşlem tamamlanamadı")
            return Result.failure(workDataOf("error" to (e.message ?: "Bilinmeyen hata")))
        } finally { if(!successful) run.deleteRecursively() }
    }
    override suspend fun getForegroundInfo()=notification("Hazırlanıyor")
    private fun notification(message:String):ForegroundInfo {
        val channel="studyfuse_processing"
        val manager=applicationContext.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(NotificationChannel(channel,"Belge işleme",NotificationManager.IMPORTANCE_LOW))
        val intent=PendingIntent.getActivity(applicationContext,0,Intent(applicationContext,MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val n=NotificationCompat.Builder(applicationContext,channel).setSmallIcon(org.studyfuse.R.drawable.ic_studyfuse)
            .setContentTitle("StudyFuse · Belgeler işleniyor").setContentText(message).setOngoing(true)
            .setContentIntent(intent).addAction(0,"İptal",WorkManager.getInstance(applicationContext).createCancelPendingIntent(id)).build()
        return ForegroundInfo(id.hashCode(),n,ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
    }
}
