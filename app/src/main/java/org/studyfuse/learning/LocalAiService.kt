package org.studyfuse.learning

import android.app.Service
import android.content.*
import android.os.*
import com.google.ai.edge.litertlm.*
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import java.io.File
import java.util.concurrent.Executors

/** Native execution is confined to a private process; a native abort cannot kill the UI process. */
class LocalAiService:Service() {
    private val nativeDispatcher=Executors.newSingleThreadExecutor{task->Thread(task,"StudyFuse-local-AI")}.asCoroutineDispatcher()
    private val scope=CoroutineScope(SupervisorJob()+nativeDispatcher)
    private val queue=Channel<android.os.Message>(Channel.UNLIMITED)
    private var active:Job?=null
    private var engine:Engine?=null
    private var engineKey=""
    private val diagnostics by lazy { AiDiagnostics(this) }
    private val messenger=Messenger(object:Handler(Looper.getMainLooper()) {
        override fun handleMessage(msg:android.os.Message) {
            if(msg.what==2)active?.cancel() else if(msg.what==1)queue.trySend(android.os.Message.obtain(msg))
        }
    })
    override fun onCreate(){super.onCreate();scope.launch{for(request in queue){active=launch{respond(request)};active?.join()}}}
    override fun onBind(intent:Intent):IBinder=messenger.binder
    private suspend fun respond(request:android.os.Message) {
        val result=Bundle()
        try {
            val data=request.data;val path=data.getString("model") ?: error("Model yok")
            require(File(path).canonicalPath.startsWith(File(filesDir,"local-models").canonicalPath+"/"))
            val gpu=data.getBoolean("gpu",true);val imageInput=data.getString("image")!=null;val key="$path|$gpu"
            fun stage(name:String)=diagnostics.stage(name,gpu,imageInput)
            if(key!=engineKey || engine==null) {
                stage("CLOSING_PREVIOUS_ENGINE");engine?.close();engine=null;engineKey=""
                stage("CREATING_ENGINE")
                val e=Engine(EngineConfig(modelPath=path,backend=if(gpu)Backend.GPU()else Backend.CPU(threadCount=4),
                    visionBackend=Backend.GPU(),maxNumTokens=8192,maxNumImages=1,cacheDir=File(cacheDir,"litert-v2-${File(path).nameWithoutExtension.take(16)}-${if(gpu)"gpu" else "cpu"}").apply{mkdirs()}.path))
                try {stage("INITIALIZING_ENGINE");e.initialize();engine=e;engineKey=key}catch(t:Throwable){runCatching{e.close()};throw t}
            }
            stage("CREATING_CONVERSATION")
            val text=withTimeout(300_000){
                engine!!.createConversation(ConversationConfig(systemInstruction=Contents.of(data.getString("system") ?: ""),
                    samplerConfig=SamplerConfig(1,.9,.1),maxOutputToken=2200,thinkingConfig=ThinkingConfig(false),automaticToolCalling=false)).use{conversation->
                    val parts=mutableListOf<Content>()
                    data.getString("image")?.let{image->require(File(image).canonicalPath.startsWith(filesDir.canonicalPath+"/"));parts+=Content.ImageFile(image)}
                    parts+=Content.Text(data.getString("prompt") ?: "")
                    stage(if(imageInput)"VISION_GENERATION" else "TEXT_GENERATION")
                    CallbackGeneration.run(start={callback->conversation.sendMessageAsync(Contents.of(parts),callback)},
                        cancel={conversation.cancelProcess()})
                }
            }
            stage("COMPLETED")
            check(text.isNotBlank()){"Model boş yanıt verdi"};result.putString("text",text)
        }catch(t:Throwable){diagnostics.javaFailure(t);result.putString("error",if(t is CancellationException)"İşlem iptal edildi veya zaman aşımı" else "${t.javaClass.simpleName}: ${t.message}")}
        finally{runCatching{request.replyTo.send(android.os.Message.obtain().apply{what=1;arg1=request.arg1;data=result})}}
    }
    override fun onDestroy(){val job=active;scope.cancel();queue.close()
        CoroutineScope(nativeDispatcher).launch{try{job?.join();runCatching{engine?.close()};engine=null}finally{nativeDispatcher.close()}}
        super.onDestroy()
    }
}

fun interface ExamInference {
    suspend fun generate(model:LocalModel,gpu:Boolean,system:String,prompt:String,image:File?):String
}
class LocalAiClient(private val context:Context):AutoCloseable,ExamInference {
    private var remote:Messenger?=null
    private var bound=false
    private val ready=CompletableDeferred<Unit>()
    private var pending:CompletableDeferred<String>?=null
    private var nextId=0
    private val replies=Messenger(object:Handler(Looper.getMainLooper()){
        override fun handleMessage(message:android.os.Message){
            if(message.arg1!=nextId)return
            val error=message.data.getString("error")
            if(error!=null)pending?.completeExceptionally(IllegalStateException(error))else pending?.complete(message.data.getString("text") ?: "")
        }
    })
    private val connection=object:ServiceConnection {
        override fun onServiceConnected(name:ComponentName,binder:IBinder){remote=Messenger(binder);ready.complete(Unit)}
        override fun onServiceDisconnected(name:ComponentName){fail("Yerel AI işlemi durdu; model/bellek/aygıt uyumluluğunu kontrol edin")}
        override fun onBindingDied(name:ComponentName){fail("Yerel AI bağlantısı kesildi")}
        override fun onNullBinding(name:ComponentName){fail("Yerel AI başlatılamadı")}
    }
    private fun fail(message:String){val error=AiProcessStopped(message);ready.completeExceptionally(error);pending?.completeExceptionally(error);remote=null}
    suspend fun connect(){withContext(Dispatchers.Main){bound=context.bindService(Intent(context,LocalAiService::class.java),connection,Context.BIND_AUTO_CREATE or Context.BIND_IMPORTANT);check(bound){"Yerel AI servisi bağlanamadı"}};withTimeout(30_000){ready.await()}}
    override suspend fun generate(model:LocalModel,gpu:Boolean,system:String,prompt:String,image:File?):String {
        check(pending?.isActive!=true);val response=CompletableDeferred<String>();pending=response;nextId++
        val message=android.os.Message.obtain().apply{what=1;arg1=nextId;replyTo=replies;data=Bundle().apply{
            putString("model",model.file.path);putBoolean("gpu",gpu);putString("system",system);putString("prompt",prompt);putString("image",image?.path)}}
        try {checkNotNull(remote){"AI bağlantısı yok"}.send(message);return withTimeout(330_000){response.await()}}
        catch(e:Exception){
            if(e is AiProcessStopped || e is RemoteException){
                val details=withContext(Dispatchers.IO){delay(250);AiDiagnostics(context).summary()}
                throw IllegalStateException("Yerel AI süreci kapandı. İndirilen model silinmedi. CPU dil modeliyle yeniden sınayın; görüntü kodlayıcı yine GPU gerektirir.\n$details",e)
            }
            throw e
        }
        finally {if(!response.isCompleted)runCatching{remote?.send(android.os.Message.obtain().apply{what=2})};pending=null}
    }
    override fun close(){if(bound){context.unbindService(connection);bound=false};remote=null}
}
