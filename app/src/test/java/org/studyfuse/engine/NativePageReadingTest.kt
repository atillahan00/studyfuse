package org.studyfuse.engine

import android.graphics.Bitmap
import androidx.test.core.app.ApplicationProvider
import com.tom_roush.pdfbox.pdmodel.*
import com.tom_roush.pdfbox.pdmodel.common.PDRectangle
import com.tom_roush.pdfbox.pdmodel.graphics.image.LosslessFactory
import com.tom_roush.pdfbox.pdmodel.graphics.state.RenderingMode
import com.tom_roush.pdfbox.pdmodel.interactive.annotation.PDAnnotationText
import kotlinx.coroutines.*
import org.json.*
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.*
import org.studyfuse.StudyFuseApp
import org.studyfuse.core.*
import org.studyfuse.data.Source
import org.studyfuse.learning.*
import java.io.File
import java.util.UUID

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[34],application=StudyFuseApp::class,shadows=[RoutingPdfRendererShadow::class,RoutingPdfPageShadow::class])
class NativePageReadingTest {
    private val context=ApplicationProvider.getApplicationContext<StudyFuseApp>()
    private val root=File(context.filesDir,"projects/native-${UUID.randomUUID()}").apply{mkdirs()}
    private val sentence="Die Zelle enthält DNA. ä ö ü ß ğ ü ş ı ö ç ∑x²"
    @After fun cleanup(){root.deleteRecursively()}
    private fun page(doc:PDDocument,extra:(PDPageContentStream)->Unit={}):PDPage {
        val p=PDPage(PDRectangle.A4);doc.addPage(p)
        PDPageContentStream(doc,p).use{cs->PdfText(context,doc).draw(cs,sentence,40f,740f);extra(cs)}
        return p
    }
    private fun fixture(extra:(PDDocument,PDPage)->Unit={_,_->}):File {
        val file=File(root,"source.pdf")
        newPdf(root).use{doc->val p=page(doc);extra(doc,p);doc.save(file)}
        return file
    }
    private suspend fun compile(file:File):Compilation {
        val s=Source("source","p",file.name,"Biologie",0,file.path,file.path,file.length(),sha256(file),"PDF")
        return Compiler(context,File(root,"exports").apply{mkdirs()}).compile(listOf(s),ExportSettings())
    }
    private fun model():LocalModel {val f=File(root,"model").apply{writeText("test double")};return LocalModel("test",f,sha256(f),f.length())}
    @Test fun plainUnicodeAndClipOnlyPathsCanUseNativeText(){
        val file=fixture{doc,p->PDPageContentStream(doc,p,PDPageContentStream.AppendMode.APPEND,true).use{it.addRect(0f,0f,595f,842f);it.clip()}}
        loadPdf(file,root).use{doc->
            val text=PageText.extract(doc,0)
            assertTrue(text.contains("ä ö ü ß ğ ü ş ı ö ç"));assertTrue(NativePageReading.inspect(doc.getPage(0)).canReadDirectly(text))
        }
    }
    @Test fun selectableTextNeverHidesAnImageOrVectorDrawing(){
        newPdf(root).use{doc->
            val bitmap=Bitmap.createBitmap(2,2,Bitmap.Config.ARGB_8888)
            val image=try{LosslessFactory.createFromImage(doc,bitmap)}finally{bitmap.recycle()}
            val withImage=page(doc){it.drawImage(image,40f,40f,20f,20f)}
            assertFalse(NativePageReading.inspect(withImage).canReadDirectly(sentence))
            val withVector=page(doc){it.moveTo(30f,30f);it.curveTo(40f,45f,50f,20f,60f,30f);it.stroke()}
            assertFalse(NativePageReading.inspect(withVector).canReadDirectly(sentence))
            val withFill=page(doc){it.addRect(40f,40f,50f,50f);it.fill()}
            assertFalse(NativePageReading.inspect(withFill).canReadDirectly(sentence))
        }
    }
    @Test fun annotationsInvisibleTextAndUnknownOperatorsRetainVisualReading(){
        newPdf(root).use{doc->
            val annotation=page(doc);annotation.annotations.add(PDAnnotationText())
            assertFalse(NativePageReading.inspect(annotation).canReadDirectly(sentence))
            val hidden=PDPage(PDRectangle.A4);doc.addPage(hidden)
            PDPageContentStream(doc,hidden).use{it.setRenderingMode(RenderingMode.NEITHER);PdfText(context,doc).draw(it,sentence,40f,740f)}
            assertFalse(NativePageReading.inspect(hidden).canReadDirectly(sentence))
            val unknown=page(doc)
            @Suppress("DEPRECATION")
            PDPageContentStream(doc,unknown,PDPageContentStream.AppendMode.APPEND,true).use{it.appendRawCommands("\nMysteryPaint\n")}
            assertFalse(NativePageReading.inspect(unknown).canReadDirectly(sentence))
        }
        assertFalse(NativePageAssessment(emptySet()).canReadDirectly(""))
        assertFalse(NativePageAssessment(emptySet()).canReadDirectly("Broken \uFFFD text"))
    }
    @Test fun compiledNestedFormsReadDirectlyWithoutInvokingTheModelAndResume()=runBlocking {
        val input=fixture();val before=sha256(input);val out=compile(input);val store=ExamStore(root);val model=model()
        val engine=ExamInference{_,_,_,_,_->error("Plain text must not render or invoke AI")}
        val config=ExamConfig(readingOnly=true,visualTokens=280)
        val result=ExamCompiler(context,store,engine).compile(out.report,model,config){_,_,_->}
        assertEquals(1,result.getInt("nativeTextRegions"));assertEquals(0,result.getInt("inferenceCalls"))
        val unit=store.currentUnits(store.signature(out.report,model,config)).single()
        assertEquals("native_pdf",unit.getString("readingMethod"));assertEquals(0,unit.getJSONArray("items").length())
        assertTrue(unit.getString("transcription").contains("ä ö ü ß ğ ü ş ı ö ç"))
        assertEquals(0,unit.getJSONObject("timings").getLong("inferenceMs"));assertEquals(before,sha256(input))
        val resumed=ExamCompiler(context,store,engine).compile(out.report,model,config){_,_,_->}
        assertEquals(1,resumed.getInt("reusedRegions"));assertEquals(1,resumed.getInt("nativeTextRegions"));assertEquals(0,resumed.getInt("inferenceCalls"))
    }
    @Test fun nestedFormGraphicsRequireVisualRequestAndAreLabelled()=runBlocking {
        val input=fixture{doc,p->PDPageContentStream(doc,p,PDPageContentStream.AppendMode.APPEND,true).use{it.addRect(10f,10f,30f,30f);it.fill()}}
        val out=compile(input);val store=ExamStore(root);val model=model();var calls=0
        val engine=ExamInference{_,_,_,_,image->assertTrue(image!!.length()>0);calls++;"""{"transcription":"","observations":"diagram fixture","uncertainties":[],"items":[]}"""}
        val config=ExamConfig(readingOnly=true)
        val result=ExamCompiler(context,store,engine).compile(out.report,model,config){_,_,_->}
        assertEquals(store.currentUnits(store.signature(out.report,model,config)).toString(),1,calls);assertEquals(0,result.getInt("nativeTextRegions"));assertEquals(1,result.getInt("inferenceCalls"))
        val unit=store.currentUnits(store.signature(out.report,model,config)).single()
        assertEquals("visual_ai",unit.getString("readingMethod"));assertTrue(unit.getJSONArray("visualReasons").toString().contains("vector"))
    }
    @Test fun explicitVisualOverrideAndCloseupsNeverReuseDirectReading()=runBlocking {
        val out=compile(fixture());val store=ExamStore(root);val model=model();var calls=0
        val engine=ExamInference{_,_,_,_,_->calls++;"""{"transcription":"","observations":"","uncertainties":[],"items":[]}"""}
        val config=ExamConfig(readingOnly=true)
        ExamCompiler(context,store,engine).compile(out.report,model,config){_,_,_->}
        val detailed=ExamCompiler(context,store,engine).compile(out.report,model,config.copy(detailed=true)){_,_,_->}
        assertEquals(store.currentUnits(store.signature(out.report,model,config.copy(detailed=true))).toString(),5,calls);assertEquals(0,detailed.getInt("nativeTextRegions"))
        calls=0
        ExamCompiler(context,store,engine).compile(out.report,model,config.copy(nativeTextFirst=false)){_,_,_->}
        assertEquals(1,calls)
        assertFalse(ExamConfig.decode(config.copy(nativeTextFirst=false).json()).nativeTextFirst)
    }
    @Test fun cancellationStillCheckpointsDirectReadings()=runBlocking {
        val input=File(root,"multi.pdf")
        newPdf(root).use{doc->repeat(3){page(doc)};doc.save(input)}
        val out=compile(input);val store=ExamStore(root);val model=model();val config=ExamConfig(readingOnly=true)
        val engine=ExamInference{_,_,_,_,_->error("No AI for plain pages")}
        try{ExamCompiler(context,store,engine).compile(out.report,model,config){done,_,_->if(done==1)throw CancellationException("fixture")};fail("Cancellation ignored")}catch(_:CancellationException){}
        assertEquals("CANCELLED",store.session()!!.getString("status"))
        val resumed=ExamCompiler(context,store,engine).compile(out.report,model,config){_,_,_->}
        assertEquals(1,resumed.getInt("reusedRegions"));assertEquals(3,resumed.getInt("nativeTextRegions"))
    }
}
