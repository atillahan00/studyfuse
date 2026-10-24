package org.studyfuse.learning

import android.app.Service
import android.content.*
import android.os.*
import com.google.ai.edge.litertlm.*
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.collect
import java.io.File

/** Native execution is confined to a private process; a native abort cannot kill the UI process. */
class LocalAiService:Service() {
    private val scope=CoroutineScope(SupervisorJob()+Dispatchers.Default)
    private val queue=Channel<android.os.Message>(Channel.UNLIMITED)
    private var active:Job?=null
    private var engine:Engine?=null
    private var engineKey=""
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
            val gpu=data.getBoolean("gpu",true);val key="$path|$gpu"
            if(key!=engineKey || engine==null) {
                engine?.close();engine=null;engineKey=""
                val e=Engine(EngineConfig(modelPath=path,backend=if(gpu)Backend.GPU()else Backend.CPU(threadCount=4),
                    visionBackend=Backend.GPU(),maxNumTokens=8192,maxNumImages=1,cacheDir=File(cacheDir,"litert").apply{mkdirs()}.path))
                try {e.initialize();engine=e;engineKey=key}catch(t:Throwable){runCatching{e.close()};throw t}
            }
            val text=withTimeout(300_000){
                engine!!.createConversation(ConversationConfig(systemInstruction=Contents.of(data.getString("system") ?: ""),
                    samplerConfig=SamplerConfig(1,.9,.1),maxOutputToken=2200,thinkingConfig=ThinkingConfig(false),automaticToolCalling=false)).use{conversation->
                    val parts=mutableListOf<Content>()
                    data.getString("image")?.let{image->require(File(image).canonicalPath.startsWith(filesDir.canonicalPath+"/"));parts+=Content.ImageFile(image)}
                    parts+=Content.Text(data.getString("prompt") ?: "")
                    val output=StringBuilder()
                    try {conversation.sendMessageAsync(Contents.of(parts)).collect{message->
                        output.append(message.toString());check(output.length<=90_000){"Model yanıtı güvenli boyutu aştı"}
                    }}finally{if(!currentCoroutineContext().isActive)conversation.cancelProcess()}
                    output.toString()
                }
            }
            check(text.isNotBlank()){"Model boş yanıt verdi"};result.putString("text",text)
        }catch(t:Throwable){result.putString("error",if(t is CancellationException)"İşlem iptal edildi veya zaman aşımı" else "${t.javaClass.simpleName}: ${t.message}")}
        finally{runCatching{request.replyTo.send(android.os.Message.obtain().apply{what=1;arg1=request.arg1;data=result})}}
    }
    override fun onDestroy(){val job=active;scope.cancel();queue.close()
        CoroutineScope(Dispatchers.Default).launch{job?.join();runCatching{engine?.close()};engine=null}
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
    private fun fail(message:String){val error=IllegalStateException(message);ready.completeExceptionally(error);pending?.completeExceptionally(error);remote=null}
    suspend fun connect(){withContext(Dispatchers.Main){bound=context.bindService(Intent(context,LocalAiService::class.java),connection,Context.BIND_AUTO_CREATE);check(bound){"Yerel AI servisi bağlanamadı"}};withTimeout(30_000){ready.await()}}
    override suspend fun generate(model:LocalModel,gpu:Boolean,system:String,prompt:String,image:File?):String {
        check(pending?.isActive!=true);val response=CompletableDeferred<String>();pending=response;nextId++
        val message=android.os.Message.obtain().apply{what=1;arg1=nextId;replyTo=replies;data=Bundle().apply{
            putString("model",model.file.path);putBoolean("gpu",gpu);putString("system",system);putString("prompt",prompt);putString("image",image?.path)}}
        try {checkNotNull(remote){"AI bağlantısı yok"}.send(message);return withTimeout(330_000){response.await()}}
        finally {if(!response.isCompleted)runCatching{remote?.send(android.os.Message.obtain().apply{what=2})};pending=null}
    }
    override fun close(){if(bound){context.unbindService(connection);bound=false};remote=null}
}
