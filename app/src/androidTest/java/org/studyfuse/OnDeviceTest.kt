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
            try{ExamCompiler(context,store,cancelled).compile(compilation.report,model,ExamConfig()){_,_,_->};fail("Cancellation ignored")}catch(_:kotlinx.coroutines.CancellationException){}
            assertEquals("CANCELLED",store.session()!!.getString("status"))
            var resumedCalls=0
            val resumed=ExamInference{_,_,_,_,image->assertTrue(image!!.length()>0);resumedCalls++;response}
            val result=ExamCompiler(context,store,resumed).compile(compilation.report,model,ExamConfig()){_,_,_->}
            assertEquals(5,result.getInt("total"));assertEquals(4,resumedCalls)
            val units=store.currentUnits(store.signature(compilation.report,model,ExamConfig()))
            assertEquals(5,units.size);assertEquals(setOf(0,1,2,3,4),units.map{it.getInt("region")}.toSet())
            assertTrue(units.all{it.getString("status")=="REVIEW"});assertFalse(store.review().keys().hasNext())
            var cachedCalls=0
            ExamCompiler(context,store,ExamInference{_,_,_,_,_->cachedCalls++;response}).compile(compilation.report,model,ExamConfig()){_,_,_->}
            assertEquals(0,cachedCalls)
        }finally{project.deleteRecursively()}
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
    }
}
