package org.studyfuse.engine

import android.app.ApplicationExitInfo
import android.content.Context
import android.os.Build
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.*
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.studyfuse.learning.*
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[34])
class AiStartupTest {
    private val context:Context get()=ApplicationProvider.getApplicationContext()
    @Test fun gpuFailureRetriesCpuExactlyOnceAndKeepsDownloadedBytes()=runBlocking {
        val file=File(context.filesDir,"downloaded-fixture").apply{writeText("downloaded bytes")};val before=file.readBytes()
        val calls=mutableListOf<Boolean>();var firstFailure=""
        val result=testSetupBackends(true,{firstFailure=it}){gpu->calls+=gpu;assertArrayEquals(before,file.readBytes());if(gpu)throw AiProcessStopped("GPU stopped")}
        assertFalse(result);assertEquals(listOf(true,false),calls);assertEquals("GPU stopped",firstFailure);assertArrayEquals(before,file.readBytes())
    }
    @Test fun cancellationNeverStartsAnotherNativeAttempt()=runBlocking {
        val calls=mutableListOf<Boolean>()
        try{testSetupBackends(true,{fail("No fallback on cancellation")}){calls+=it;throw CancellationException("paused")};fail("Cancellation swallowed")}
        catch(_:CancellationException){}
        assertEquals(listOf(true),calls)
    }
    @Test fun cpuCompatibilityFailureIsNotDeclaredReady()=runBlocking {
        val calls=mutableListOf<Boolean>()
        try{testSetupBackends(false,{fail("CPU has no hidden GPU retry")}){calls+=it;throw IllegalStateException("vision unavailable")};fail("Failed probe accepted")}
        catch(e:IllegalStateException){assertEquals("vision unavailable",e.message)}
        assertEquals(listOf(false),calls)
    }
    @Test fun twoFailedBackendsReportFailureWithoutUnboundedRetries()=runBlocking {
        val calls=mutableListOf<Boolean>()
        try{testSetupBackends(true,{}){calls+=it;throw IllegalStateException("native error")};fail("Failed probes accepted")}
        catch(e:IllegalStateException){assertTrue(e.message!!.contains("İndirilen model korundu"))}
        assertEquals(listOf(true,false),calls)
    }
    @Test fun diagnosticsContainOnlyStartupMetadataNotErrorText() {
        val d=AiDiagnostics(context);d.stage("INITIALIZING_ENGINE",false,true)
        d.javaFailure(IllegalStateException("PRIVATE TEACHER DOCUMENT AND PROMPT"))
        assertEquals(setOf("stage","pid","at","gpu","image","availableBytes","lowMemory","errorClass"),d.snapshot().keys().asSequence().toSet())
        assertFalse(d.snapshot().toString().contains("PRIVATE"));assertFalse(d.summary().contains("PRIVATE"))
        assertTrue(d.summary().contains("INITIALIZING_ENGINE"));assertTrue(d.summary().contains("CPU"))
        assertTrue(AiDiagnostics.exitReason(ApplicationExitInfo.REASON_LOW_MEMORY).contains("LOW_MEMORY"))
        assertTrue(AiDiagnostics.exitReason(ApplicationExitInfo.REASON_CRASH_NATIVE).contains("CRASH_NATIVE"))
    }
    @Test fun uncaughtFailureKeepsOnlyBoundedCodeFrames() {
        val d=AiDiagnostics(context);d.stage("VISION_GENERATION",false,true)
        val failure=NoSuchMethodError("PRIVATE NOTE CONTENT").apply{stackTrace=arrayOf(
            StackTraceElement("com.google.ai.edge.litertlm.Conversation", "onDone", "PRIVATE DOCUMENT PATH", 452),
            StackTraceElement("private.teacher.notes", "secret", "PRIVATE DOCUMENT PATH", 1),
            *Array(20){StackTraceElement("org.studyfuse.learning.LocalAiService","respond","PRIVATE DOCUMENT PATH",42)})}
        d.fatal(failure)
        val raw=d.snapshot().toString();assertFalse(raw.contains("PRIVATE"));assertFalse(raw.contains("private.teacher"))
        assertEquals(12,d.snapshot().getJSONArray("fatalFrames").length())
        assertTrue(d.summary().contains("java.lang.NoSuchMethodError"));assertTrue(d.summary().contains("Conversation.onDone:452"))
    }
    @Test @Config(sdk=[29]) fun androidTenDiagnosticsDoNotRequireExitInfoApi() {
        val d=AiDiagnostics(context);d.stage("CREATING_ENGINE",false,true)
        assertTrue(d.summary().contains("Android 10 süreç çıkış kaydı sağlamaz"))
    }
    @Test fun successfulBackendIsBoundToModelAndDevice() {
        val root=ModelStore(context).directory();val f=File(root,"candidate").apply{writeText("fixture weights")};val hash=sha256(f)
        val weights=File(root,"$hash.litertlm");assertTrue(f.renameTo(weights))
        val store=ModelStore(context);store.activate(LocalModel("fixture",weights,hash,weights.length()),false)
        assertEquals(false,store.testedGpu());assertTrue(visionReady(context,false));assertFalse(visionReady(context,true))
        val test=File(root,"vision-test.json");atomicJson(test,readJson(test).put("fingerprint","different-device"))
        assertNull(store.testedGpu());assertFalse(visionReady(context,false))
    }
}
