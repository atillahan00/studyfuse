package org.studyfuse

import android.content.Context
import android.graphics.*
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.studyfuse.core.*
import org.studyfuse.data.Source
import org.studyfuse.engine.*
import java.io.File
import java.util.UUID
import org.json.*
import org.studyfuse.learning.*

/** Requires a real device/emulator; not executed by Robolectric. */
@RunWith(AndroidJUnit4::class)
class OnDeviceTest {
    private val context:Context=ApplicationProvider.getApplicationContext()
    private val root=File(context.cacheDir,"device-tests").apply {mkdirs()}
    private fun source(f:File)=Source(UUID.randomUUID().toString(),"device",f.name,f.name,0,f.path,f.path,f.length(),sha256(f),"IMAGE")
    @Test fun modelNetworkingIsGrantedAndOcrTelemetryJobsAreAbsent(){
        assertEquals(android.content.pm.PackageManager.PERMISSION_GRANTED,context.checkSelfPermission(android.Manifest.permission.INTERNET))
        for(name in listOf("com.google.android.datatransport.runtime.backends.TransportBackendDiscovery","com.google.android.datatransport.runtime.scheduling.jobscheduling.JobInfoSchedulerService")){
            try{context.packageManager.getServiceInfo(android.content.ComponentName(context,name),0);fail("OCR telemetry component still registered")}
            catch(_:android.content.pm.PackageManager.NameNotFoundException){}
        }
    }
    @Test fun bundledPrintedTextOcrWorksOffline()=runBlocking {
        val bitmap=Bitmap.createBitmap(1200,300,Bitmap.Config.ARGB_8888).apply {eraseColor(Color.WHITE)}
        val paint=Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color=Color.BLACK;textSize=80f;typeface=Typeface.createFromAsset(context.assets,"fonts/NotoSans-Regular.ttf")
        }
        Canvas(bitmap).drawText("Biologie Zelle 123",30f,150f,paint)
        try {OcrEngine().use {val result=it.read(bitmap);assertTrue(result.text.contains("Zelle"));assertTrue(result.lines.length()>0)}}
        finally {bitmap.recycle()}
    }
    @Test fun platformRendererHandlesLosslessImages()=runBlocking {
        val image=File(root,"native-red.png")
        val bitmap=Bitmap.createBitmap(200,300,Bitmap.Config.ARGB_8888).apply {eraseColor(Color.RED)}
        image.outputStream().use {bitmap.compress(Bitmap.CompressFormat.PNG,100,it)};bitmap.recycle()
        val s=source(image);val out=File(root,"native-out.pdf")
        ImageAdapter(root).convert(s,out,ExportSettings(),FileResult(s)){}
        android.os.ParcelFileDescriptor.open(out,android.os.ParcelFileDescriptor.MODE_READ_ONLY).use {fd ->
            android.graphics.pdf.PdfRenderer(fd).use {renderer -> renderer.openPage(0).use {page ->
                val b=Bitmap.createBitmap(page.width,page.height,Bitmap.Config.ARGB_8888)
                b.eraseColor(Color.WHITE);page.render(b,null,null,android.graphics.pdf.PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                assertEquals(Color.RED,b.getPixel(50,70));b.recycle()
            } }
        }
    }
    @Test fun longImageRegionDecodeKeepsBottomPixels()=runBlocking {
        com.tom_roush.pdfbox.android.PDFBoxResourceLoader.init(context)
        val file=File(root,"long.png")
        val bitmap=Bitmap.createBitmap(1000,18000,Bitmap.Config.ARGB_8888)
        bitmap.eraseColor(Color.WHITE)
        val canvas=Canvas(bitmap);val paint=Paint().apply{color=Color.RED}
        canvas.drawRect(0f,17900f,1000f,18000f,paint)
        file.outputStream().use{bitmap.compress(Bitmap.CompressFormat.PNG,100,it)};bitmap.recycle()
        ImageSource(file).use {image ->
            val bottom=image.region(Rect(0,17900,1000,18000))
            assertEquals(Color.RED,bottom.getPixel(500,50));bottom.recycle()
        }
        val viewport=org.studyfuse.ui.DocumentView.render(file,0,600,400,area=Slice(0.0,17900.0,1000.0,18000.0))
        assertEquals(Color.RED,viewport.getPixel(300,200));assertEquals(Color.WHITE,viewport.getPixel(300,10));viewport.recycle()
        val s=source(file);val out=File(root,"long-out.pdf");val result=FileResult(s)
        ImageAdapter(root).convert(s,out,ExportSettings(),result){}
        assertTrue(result.generatedPages>8)
        assertEquals(18000*ImageSource.POINTS_PER_PIXEL,result.mappings.last().bottom,.01)
    }
    /** Exercises native PDF/region processing and resume with a deterministic test double, NOT AI quality. */
    @Test fun examPipelineCoversFiveRegionsAndResumesAfterCancellation()=runBlocking {
        com.tom_roush.pdfbox.android.PDFBoxResourceLoader.init(context)
        val project=File(context.filesDir,"projects/device-exam-${UUID.randomUUID()}").apply{mkdirs()}
        try {
            val file=File(project,"Biologie.txt").apply{writeText("Die Zelle enthält DNA.\nğ ü ş ı ö ç ä ö ü ß")}
            val source=Source("exam-source","device",file.name,"Zelle",0,file.path,file.path,file.length(),sha256(file),"TEXT")
            val compilation=org.studyfuse.engine.Compiler(context,File(project,"exports/run").apply{mkdirs()}).compile(listOf(source),ExportSettings())
            val modelFile=File(project,"test-model").apply{writeText("test double, no real weights")}
            val model=LocalModel("deterministic test engine",modelFile,sha256(modelFile),modelFile.length());val store=ExamStore(project)
            var calls=0
            val response=JSONObject().put("transcription","Die Zelle enthält DNA.").put("observations","Test fixture")
                .put("uncertainties",JSONArray()).put("items",JSONArray().put(JSONObject().put("kind","recall").put("question","Was enthält die Zelle?")
                    .put("answer","DNA").put("evidence","Die Zelle enthält DNA.").put("origin","document"))).toString()
            val cancelled=ExamInference{_,_,_,_,image->assertTrue(image!!.length()>0);calls++;if(calls==2)throw kotlinx.coroutines.CancellationException("test cancellation");response}
            try{ExamCompiler(context,store,cancelled).compile(compilation.report,model,ExamConfig(detailed=true)){_,_,_->};fail("Cancellation ignored")}catch(_:kotlinx.coroutines.CancellationException){}
            assertEquals("CANCELLED",store.session()!!.getString("status"))
            var resumedCalls=0
            val resumed=ExamInference{_,_,_,_,image->assertTrue(image!!.length()>0);resumedCalls++;response}
            val result=ExamCompiler(context,store,resumed).compile(compilation.report,model,ExamConfig(detailed=true)){_,_,_->}
            assertEquals(5,result.getInt("total"));assertEquals(4,resumedCalls)
            val units=store.currentUnits(store.signature(compilation.report,model,ExamConfig(detailed=true)))
            assertEquals(5,units.size);assertEquals(setOf(0,1,2,3,4),units.map{it.getInt("region")}.toSet())
            assertTrue(units.all{it.getString("status")=="REVIEW"});assertFalse(store.review().keys().hasNext())
            var cachedCalls=0
            ExamCompiler(context,store,ExamInference{_,_,_,_,_->cachedCalls++;response}).compile(compilation.report,model,ExamConfig(detailed=true)){_,_,_->}
            assertEquals(0,cachedCalls)
        }finally{project.deleteRecursively()}
    }
    /** Native PDF parsing/rendering plus deterministic inference: validates work reuse, not neural speed. */
    @Test fun readingFirstTargetedDetailsAndBackendChangesReuseCompletedPages()=runBlocking {
        com.tom_roush.pdfbox.android.PDFBoxResourceLoader.init(context)
        val project=File(context.filesDir,"projects/perf-${UUID.randomUUID()}").apply{mkdirs()}
        try {
            val file=File(project,"Biologie.pdf")
            newPdf(project).use{doc->repeat(3){index->val page=com.tom_roush.pdfbox.pdmodel.PDPage(com.tom_roush.pdfbox.pdmodel.common.PDRectangle.A4);doc.addPage(page)
                com.tom_roush.pdfbox.pdmodel.PDPageContentStream(doc,page).use{PdfText(context,doc).draw(it,"Die Zelle enthält DNA. Seite ${index+1}",40f,740f)}};doc.save(file)}
            val before=sha256(file);val source=Source("perf-source","device",file.name,"Zelle",0,file.path,file.path,file.length(),before,"PDF")
            val compilation=org.studyfuse.engine.Compiler(context,File(project,"exports/run").apply{mkdirs()}).compile(listOf(source),ExportSettings())
            val first=readJson(compilation.report).getJSONArray("files").getJSONObject(0).getInt("contentStart")
            val weights=File(project,"test-model").apply{writeText("controlled inference fixture, not neural weights")};val model=LocalModel("fixture",weights,sha256(weights),weights.length())
            val store=ExamStore(project);var calls=0
            val engine=object:ExamInference {
                override suspend fun generate(m:LocalModel,gpu:Boolean,system:String,prompt:String,image:File?):String=error("Options must be passed")
                override suspend fun generate(m:LocalModel,gpu:Boolean,system:String,prompt:String,image:File?,options:GenerationOptions):String {
                    assertEquals(280,options.visualTokens);assertTrue(image!!.length()>0);assertTrue(system.contains("items as an empty array"));calls++
                    return JSONObject().put("transcription","").put("observations","deterministic diagram fixture").put("uncertainties",JSONArray()).put("items",JSONArray()).toString()
                }
            }
            val fast=ExamConfig(readingOnly=true,visualTokens=280,nativeTextFirst=false)
            val overview=ExamCompiler(context,store,engine).compile(compilation.report,model,fast){_,_,_->}
            assertEquals(3,calls);assertEquals(3,overview.getInt("total"))
            val overviewKeys=overview.getJSONArray("units").let{a->(0 until a.length()).map{a.getString(it)}}
            assertTrue(store.currentUnits(store.signature(compilation.report,model,fast)).all{it.getString("transcription").contains("Die Zelle enthält DNA.") && it.has("timings")})
            calls=0;val targeted=ExamCompiler(context,store,engine).compile(compilation.report,model,fast.copy(detailPages="${first+1}")){_,_,_->}
            assertEquals(4,calls);assertEquals(7,targeted.getInt("total"));assertEquals(3,targeted.getInt("reusedRegions"))
            assertTrue(overviewKeys.all{k->targeted.getJSONArray("units").toString().contains(k)})
            calls=0;val detailed=fast.copy(detailed=true)
            val all=ExamCompiler(context,store,engine).compile(compilation.report,model,detailed){_,_,_->}
            assertEquals(8,calls);assertEquals(15,all.getInt("total"));assertEquals(7,all.getInt("reusedRegions"))
            calls=0;val otherBackend=ExamCompiler(context,store,engine).compile(compilation.report,model,detailed.copy(gpu=false)){_,_,_->}
            assertEquals(0,calls);assertEquals(15,otherBackend.getInt("reusedRegions"));assertEquals(before,sha256(file))
        }finally{project.deleteRecursively()}
    }
    /** Native Android PDF parser/classifier: synthetic text bypasses inference; drawn ink does not. */
    @Test fun textFirstSkipsOnlyPlainPagesAndFullVisualOverrideStillWorks()=runBlocking {
        com.tom_roush.pdfbox.android.PDFBoxResourceLoader.init(context)
        val project=File(context.filesDir,"projects/native-${UUID.randomUUID()}").apply{mkdirs()}
        try {
            val input=File(project,"mixed.pdf")
            newPdf(project).use{doc->repeat(2){index->
                val p=com.tom_roush.pdfbox.pdmodel.PDPage(com.tom_roush.pdfbox.pdmodel.common.PDRectangle.A4);doc.addPage(p)
                com.tom_roush.pdfbox.pdmodel.PDPageContentStream(doc,p).use{cs->
                    PdfText(context,doc).draw(cs,"Die Zelle enthält DNA. äöüß ğışç",40f,740f)
                    if(index==1){cs.moveTo(30f,30f);cs.lineTo(80f,50f);cs.stroke()}
                }
            };doc.save(input)}
            val before=sha256(input)
            val s=Source("native-source","device",input.name,"Biologie",0,input.path,input.path,input.length(),before,"PDF")
            val out=org.studyfuse.engine.Compiler(context,File(project,"exports").apply{mkdirs()}).compile(listOf(s),ExportSettings())
            val weights=File(project,"model").apply{writeText("deterministic fixture, no neural weights")}
            val m=LocalModel("fixture",weights,sha256(weights),weights.length());val store=ExamStore(project);var calls=0
            val engine=ExamInference{_,_,_,_,image->assertTrue(image!!.length()>0);calls++;"""{"transcription":"","observations":"fixture","uncertainties":[],"items":[]}"""}
            val config=ExamConfig(readingOnly=true)
            val result=ExamCompiler(context,store,engine).compile(out.report,m,config){_,_,_->}
            assertEquals(1,calls);assertEquals(1,result.getInt("nativeTextRegions"));assertEquals(1,result.getInt("inferenceCalls"))
            val units=store.currentUnits(store.signature(out.report,m,config))
            assertEquals(listOf("native_pdf","visual_ai"),units.map{it.getString("readingMethod")})
            assertTrue(units.first().getString("transcription").contains("äöüß ğışç"))
            calls=0
            ExamCompiler(context,store,engine).compile(out.report,m,config.copy(nativeTextFirst=false)){_,_,_->}
            assertEquals(2,calls);assertEquals(before,sha256(input))
        }finally{project.deleteRecursively()}
    }
    /** Source-reference protocol and partial-card persistence; no neural weights or accuracy claim. */
    @Test fun coachSourceLabelsKeepValidCardsAndRestoreExactTurkishQuotes()=runBlocking {
        val project=File(context.filesDir,"projects/coach-${UUID.randomUUID()}").apply{mkdirs()}
        try {
            val tools=StudyTools(ExamStore(project));val text="Şiirde ölçü ve uyak incelenir. ğ ü ş ı ö ç ä ß"
            val p=EvidencePassage("source:0",text,"Şiir",3,"source.pdf",unverifiedVisualReading=false)
            val raw="""{"answer":"model summary","additional":"","uncertainties":[],"citations":[{"evidence":"unknown"}],"cards":[{"evidence":"E1","question":"Neler incelenir?","answer":"Ölçü ve uyak."},{"evidence":"E999","question":"bad","answer":"bad"}]}"""
            val result=tools.validate(raw,KnowledgeMode.DOCUMENTS_ONLY,listOf(p),CoachTask.CARDS)
            assertEquals(1,result.getJSONArray("cards").length());assertEquals(2,result.getJSONArray("rejected").length())
            assertEquals(text,result.getJSONArray("citations").getJSONObject(0).getString("quote"))
            val weights=File(project,"fixture").apply{writeText("not a neural model")};val m=LocalModel("fixture",weights,sha256(weights),weights.length())
            var calls=0
            val runner=CoachRunner(tools,ExamInference{_,_,_,_,_->calls++;raw})
            runner.cards("a".repeat(64),m,ExamConfig(),"all",listOf(p)){_,_,_->}
            assertEquals(1,calls);assertEquals(1,tools.cards("a".repeat(64),emptyList(),JSONObject()).size)
            assertEquals(0,tools.store.review().length())
            runner.cards("a".repeat(64),m,ExamConfig(),"all",listOf(p)){_,_,_->}
            assertEquals(1,calls)
        }finally{project.deleteRecursively()}
    }
    /** Actual pinned SDK completion bytecode, followed by the production bridge; no neural weights. */
    @Test fun sdkCompletionAbiCrashIsReproducedAndAppBridgeCompletes()=runBlocking {
        val scope=java.lang.reflect.Proxy.newProxyInstance(kotlinx.coroutines.channels.ProducerScope::class.java.classLoader,
            arrayOf(kotlinx.coroutines.channels.ProducerScope::class.java)){_,_,_->null}
        val type=Class.forName("com.google.ai.edge.litertlm.Conversation\$sendMessageAsync\$1\$1")
        val old=type.declaredConstructors.single().apply{isAccessible=true}.newInstance(scope) as com.google.ai.edge.litertlm.MessageCallback
        var reproduced=false
        try{old.onDone()}catch(e:NoSuchMethodError){reproduced=e.message.orEmpty().contains("close")}
        assertTrue("The pinned SDK Flow completion must expose the ABI mismatch",reproduced)
        val result=CallbackGeneration.run(start={callback->
            Thread{callback.onMessage(com.google.ai.edge.litertlm.Message.model(com.google.ai.edge.litertlm.Contents.of("äöüß ğışç ∑x²")));callback.onDone()}.start()
        },cancel={fail("Successful completion must not cancel")})
        assertEquals("äöüß ğışç ∑x²",result)
    }
    @Test fun nativeAiFailureIsReportedWithoutKillingUiProcess()=runBlocking {
        val missing=File(context.filesDir,"local-models/does-not-exist.litertlm")
        var error:Throwable?=null
        LocalAiClient(context).use{client->client.connect()
            try{kotlinx.coroutines.withTimeout(60_000){client.generate(LocalModel("missing",missing,"",0),false,"","test",null)}}catch(t:Throwable){error=t}
        }
        assertNotNull("An absent model must never produce a fake answer",error)
        assertFalse("Native failure must be surfaced before the test timeout",error is kotlinx.coroutines.TimeoutCancellationException)
        assertEquals("org.studyfuse",context.packageName)
        assertTrue(context.applicationContext is StudyFuseApp)
        val diagnostic=AiDiagnostics(context).snapshot()
        assertTrue(diagnostic.getString("stage") in setOf("CREATING_ENGINE","INITIALIZING_ENGINE","CLOSING_PREVIOUS_ENGINE"))
        assertTrue("Native startup was attempted in a separate process",diagnostic.getInt("pid")!=android.os.Process.myPid())
        assertFalse(diagnostic.has("prompt"));assertFalse(diagnostic.has("response"))
    }
}
