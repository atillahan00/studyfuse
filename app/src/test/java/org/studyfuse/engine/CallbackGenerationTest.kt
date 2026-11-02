package org.studyfuse.engine

import com.google.ai.edge.litertlm.*
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.ProducerScope
import org.junit.Assert.*
import org.junit.Test
import org.studyfuse.learning.CallbackGeneration
import java.lang.reflect.Proxy

/** Runs the actual SDK completion class; no model, GPU or mocked SDK bytecode. */
class CallbackGenerationTest {
    @Test fun pinnedSdkFlowCompletionReproducesMissingCoroutineMethod(){
        val scope=Proxy.newProxyInstance(ProducerScope::class.java.classLoader,arrayOf(ProducerScope::class.java)){_,_,_->null}
        val type=Class.forName("com.google.ai.edge.litertlm.Conversation\$sendMessageAsync\$1\$1")
        val callback=type.declaredConstructors.single().apply{isAccessible=true}.newInstance(scope) as MessageCallback
        try{callback.onDone();fail("Pinned SDK completion unexpectedly changed; review the workaround")}
        catch(e:NoSuchMethodError){assertTrue(e.message.orEmpty().contains("close"))}
    }
    private fun message(text:String)=Message.model(Contents.of(Content.Text(text)))
    @Test fun appCompiledCompletionPreservesUnicodeChunks()=runBlocking {
        var cancels=0
        val answer=CallbackGeneration.run(start={c->c.onMessage(message("äöüß · "));c.onMessage(message("ğışç ∑x²"));c.onDone()},cancel={cancels++})
        assertEquals("äöüß · ğışç ∑x²",answer);assertEquals(0,cancels)
    }
    @Test fun failingTelemetryObserverCannotBreakGeneration()=runBlocking {
        var observed=0
        val answer=CallbackGeneration.run(start={it.onMessage(message("all text"));it.onDone()},cancel={fail("Cancelled success")},onCharacters={observed=it;throw java.io.IOException("telemetry disk error")})
        assertEquals("all text",answer);assertEquals(8,observed)
    }
    @Test fun callbackThreadCanFinishWithoutSdkFlowBridge()=runBlocking {
        var thread:Thread?=null
        val answer=CallbackGeneration.run(start={c->thread=Thread{c.onMessage(message("complete"));c.onDone()}.apply{start()}},cancel={})
        thread!!.join();assertEquals("complete",answer)
    }
    @Test fun callbackErrorsPropagateAndCancelOnce()=runBlocking {
        val failure=IllegalStateException("controlled SDK failure");var cancels=0
        try{CallbackGeneration.run(start={it.onError(failure)},cancel={cancels++});fail("Error swallowed")}
        catch(e:IllegalStateException){assertEquals(failure.javaClass,e.javaClass);assertEquals(failure.message,e.message)}
        assertEquals(1,cancels)
    }
    @Test fun synchronousRegistrationFailureStillCancels()=runBlocking {
        var cancels=0
        try{CallbackGeneration.run(start={throw IllegalArgumentException("registration failed")},cancel={cancels++});fail("Error swallowed")}
        catch(_:IllegalArgumentException){}
        assertEquals(1,cancels)
    }
    @Test fun timeoutCancelsAndIgnoresLateCallbacks()=runBlocking {
        lateinit var callback:MessageCallback;var cancels=0
        try{withTimeout(100){CallbackGeneration.run(start={callback=it},cancel={cancels++})};fail("Timeout swallowed")}
        catch(_:TimeoutCancellationException){}
        callback.onMessage(message("late"));callback.onDone();callback.onError(IllegalStateException("late error"))
        assertEquals(1,cancels)
    }
    @Test fun duplicateCompletionAndLateChunksDoNotChangeAnswer()=runBlocking {
        val answer=CallbackGeneration.run(start={c->c.onMessage(message("first"));c.onDone();c.onMessage(message("late"));c.onDone()},cancel={fail("Successful stream cancelled")})
        assertEquals("first",answer)
    }
    @Test fun oversizedOutputFailsExplicitlyInsteadOfReturningTruncatedText()=runBlocking {
        var cancels=0
        try{CallbackGeneration.run(4,start={it.onMessage(message("1234"));it.onMessage(message("5"));it.onDone()},cancel={cancels++});fail("Truncated answer accepted")}
        catch(e:IllegalStateException){assertTrue(e.message.orEmpty().contains("boyutu"))}
        assertEquals(1,cancels)
    }
}
