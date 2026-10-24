package org.studyfuse.worker

import android.app.*
import android.content.*
import android.content.pm.ServiceInfo
import androidx.core.app.NotificationCompat
import androidx.work.*
import kotlinx.coroutines.*
import org.studyfuse.*
import org.studyfuse.learning.*
import java.io.File

class ExamWorker(context:Context,params:WorkerParameters):CoroutineWorker(context,params) {
    override suspend fun doWork():Result {
        val app=applicationContext as StudyFuseApp;val dao=app.database.dao()
        val projectId=inputData.getString("projectId") ?: return Result.failure()
        val project=dao.project(projectId) ?: return Result.failure()
        if(project.workId!=id.toString())return Result.success()
        var locked=false
        try {
            locked=ExamStore.execution.tryLock();check(locked){"Başka bir yerel AI/model işlemi sürüyor"}
            setForeground(notification("Yerel model hazırlanıyor"))
            val model=ModelStore(app).selected() ?: error("Önce görüntü destekli bir .litertlm modeli içe aktarın")
            val store=ExamStore(app.repository.directory(projectId));val report=File(project.report ?: error("Önce PDF derleyin"))
            val config=ExamConfig.decode(readJson(File(store.root,"requests/$id.json")))
            check(visionReady(app,config.gpu)){"Seçili model/aygıt için görüntü giriş testi gerekli"}
            dao.workStatus(projectId,id.toString(),"RUNNING","Yerel AI · model doğrulanıyor")
            LocalAiClient(app).use{client->
                client.connect()
                ExamCompiler(app,store,client).compile(report,model,config){done,total,message->
                    setProgress(workDataOf("processed" to done,"total" to total,"message" to message))
                    dao.workStatus(projectId,id.toString(),"RUNNING","Yerel AI · $done/$total · $message")
                    setForeground(notification(message))
                }
            }
            dao.workStatus(projectId,id.toString(),inputData.getString("previousStatus") ?: "REVIEW","Sınav taslağı hazır; atölyede inceleyin")
            return Result.success()
        }catch(e:CancellationException){withContext(NonCancellable){dao.workStatus(projectId,id.toString(),inputData.getString("previousStatus") ?: "REVIEW","AI iptal edildi; tamamlanan bölgeler yeniden kullanılabilir")};throw e}
        catch(e:Exception){dao.workStatus(projectId,id.toString(),inputData.getString("previousStatus") ?: "REVIEW","AI tamamlanamadı: ${e.message}");return Result.failure(workDataOf("error" to (e.message ?: "Yerel AI hatası")))}
        finally{File(app.repository.directory(projectId),"exam/requests/$id.json").delete();if(locked)ExamStore.execution.unlock()}
    }
    override suspend fun getForegroundInfo()=notification("Yerel sınav hazırlığı")
    private fun notification(text:String):ForegroundInfo {
        val manager=applicationContext.getSystemService(NotificationManager::class.java);val channel="studyfuse_exam"
        manager.createNotificationChannel(NotificationChannel(channel,"Yerel sınav hazırlığı",NotificationManager.IMPORTANCE_LOW))
        val intent=PendingIntent.getActivity(applicationContext,0,Intent(applicationContext,MainActivity::class.java),PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val n=NotificationCompat.Builder(applicationContext,channel).setSmallIcon(org.studyfuse.R.drawable.ic_studyfuse).setContentTitle("StudyFuse · Yerel AI")
            .setContentText(text).setContentIntent(intent).setOngoing(true).addAction(0,"İptal",WorkManager.getInstance(applicationContext).createCancelPendingIntent(id)).build()
        return ForegroundInfo(id.hashCode(),n,ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
    }
}
