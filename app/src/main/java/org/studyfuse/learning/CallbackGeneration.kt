package org.studyfuse.learning

import com.google.ai.edge.litertlm.Message
import com.google.ai.edge.litertlm.MessageCallback
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

/**
 * Keep completion in app-compiled code. The 0.16.1 SDK Flow overload calls an absent
 * SendChannel.close$default on completion with our packaged coroutines runtime.
 * The SDK callback overload does not use that bridge. No model response is dropped.
 */
object CallbackGeneration {
    suspend fun run(maxCharacters:Int=90_000,start:(MessageCallback)->Unit,cancel:()->Unit,onCharacters:(Int)->Unit={}):String {
        require(maxCharacters>0)
        val result=CompletableDeferred<String>();val output=StringBuilder();val lock=Any()
        var completedNormally=false
        val callback=object:MessageCallback {
            override fun onMessage(message:Message){val count=synchronized(lock){
                if(!result.isCompleted){
                    try{val chunk=message.toString()
                        check(chunk.length<=maxCharacters-output.length){"Model yanıtı güvenli boyutu aştı"}
                        output.append(chunk)
                    }catch(t:Throwable){result.completeExceptionally(t)}
                };if(result.isCompleted)null else output.length
            };if(count!=null)runCatching{onCharacters(count)}}
            override fun onDone(){synchronized(lock){if(!result.isCompleted)result.complete(output.toString())}}
            override fun onError(throwable:Throwable){synchronized(lock){if(!result.isCompleted)result.completeExceptionally(throwable)}}
        }
        try{
            currentCoroutineContext().ensureActive();start(callback)
            val answer=result.await();completedNormally=true;return answer
        }finally{
            if(!completedNormally){synchronized(lock){result.cancel()};runCatching{cancel()}}
        }
    }
}
