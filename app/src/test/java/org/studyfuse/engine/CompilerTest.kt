package org.studyfuse.engine

import android.content.Context
import android.graphics.*
import androidx.test.core.app.ApplicationProvider
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.cos.COSName
import com.tom_roush.pdfbox.pdmodel.*
import com.tom_roush.pdfbox.pdmodel.common.PDRectangle
import com.tom_roush.pdfbox.rendering.PDFRenderer
import com.tom_roush.pdfbox.text.PDFTextStripper
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
import java.io.File
import java.util.UUID
import kotlin.math.*

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[34],application=StudyFuseApp::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class CompilerTest {
    private lateinit var context:Context
    private lateinit var root:File
    @Before fun setup() {
        context=ApplicationProvider.getApplicationContext()
        PDFBoxResourceLoader.init(context)
        root=File(context.cacheDir,UUID.randomUUID().toString()).apply {mkdirs()}
    }
    @After fun cleanup() {root.deleteRecursively()}
    private fun source(file:File,name:String=file.name,position:Int=0)=Source(UUID.randomUUID().toString(),"test",name,
        name.substringBeforeLast('.'),position,file.path,file.path,file.length(),sha256(file),
        Format.detect(name,file.inputStream().use {it.readNBytes(32)}).name)
    private fun pdf(name:String,text:String,color:Int?=null):File {
        val file=File(root,name)
        newPdf(root).use {doc ->
            val p=PDPage(PDRectangle(300f,400f));doc.addPage(p)
            val writer=PdfText(context,doc)
            PDPageContentStream(doc,p).use {cs ->
                if(color!=null) {cs.setNonStrokingColor(Color.red(color),Color.green(color),Color.blue(color));cs.addRect(20f,20f,260f,300f);cs.fill();cs.setNonStrokingColor(0,0,0)}
                writer.draw(cs,text,24f,360f)
            }
            doc.save(file)
        };return file
    }
    private suspend fun compile(sources:List<Source>,settings:ExportSettings=ExportSettings(mode=ExportMode.ARCHIVE))=
        Compiler(context,File(root,"run").apply {mkdirs()}).compile(sources,settings)
    private fun text(file:File)=loadPdf(file,root).use {PDFTextStripper().getText(it)}
    private fun report(c:Compilation)=JSONObject(c.report.readText())

    @Test fun standardPdfMergingOrderingAndPageNumbers()=runBlocking {
        val a=source(pdf("a.pdf","SOURCE_A"));val b=source(pdf("b.pdf","SOURCE_B"))
        val c=compile(listOf(b,a))
        assertTrue(c.complete)
        val r=report(c);assertEquals(2,r.getInt("successfulFiles"))
        val output=c.outputs.single().file
        loadPdf(output,root).use {doc ->
            val stripper=PDFTextStripper().apply {startPage=1;endPage=1}
            assertTrue(stripper.getText(doc).contains("SOURCE_B"))
            stripper.startPage=2;stripper.endPage=2
            assertTrue(stripper.getText(doc).contains("SOURCE_A"))
            assertTrue(stripper.getText(doc).contains("2 / ${doc.numberOfPages}"))
        }
        assertEquals(sha256(output),r.getJSONArray("outputs").getJSONObject(0).getString("sha256"))
    }
    @Test fun mixedImageAndPdfPreservesPixels()=runBlocking {
        val image=File(root,"red.png")
        Bitmap.createBitmap(200,300,Bitmap.Config.ARGB_8888).apply {eraseColor(Color.RED)}.let {bitmap ->
            image.outputStream().use {bitmap.compress(Bitmap.CompressFormat.PNG,100,it)};bitmap.recycle()
        }
        ImageSource(image).use { img -> val b=img.region(Rect(0,0,200,300));assertEquals("Source decode",Color.RED,b.getPixel(50,70));b.recycle() }
        val c=compile(listOf(source(image),source(pdf("blue.pdf","BLUE",Color.BLUE))))
        assertTrue(c.report.readText(),c.complete)
        File("build/test-artifacts").apply{mkdirs()}.let {c.outputs.single().file.copyTo(File(it,"mixed.pdf"),true)}
        loadPdf(c.outputs.single().file,root).use {doc ->
            val renderer=PDFRenderer(doc)
            val imageX=doc.getPage(0).resources.xObjectNames.map { doc.getPage(0).resources.getXObject(it) }
                .filterIsInstance<com.tom_roush.pdfbox.pdmodel.graphics.image.PDImageXObject>().first()
            // Assert encoded PDF color samples. Robolectric's native Skia buffer byte order differs
            // from Android in PDFBox's copyPixelsFromBuffer path; independent MuPDF checks this PDF.
            val rgb=imageX.createInputStream().use {it.readNBytes(3)}
            assertArrayEquals(byteArrayOf(-1,0,0),rgb)
            val b=renderer.renderImage(1);assertEquals(Color.BLUE,b.getPixel(160,200));b.recycle()
        }
        assertEquals(2,report(c).getInt("successfulFiles"))
    }
    @Test fun tallHandwrittenFixturePreservesEveryBandAndVectorContent()=runBlocking {
        val input=File(root,"onenote-long.pdf")
        javaClass.getResourceAsStream("/tall-handwritten-style.pdf")!!.use { stream -> input.outputStream().use {stream.copyTo(it)} }
        val markers=JSONArray(javaClass.getResourceAsStream("/tall-markers.json")!!.bufferedReader().readText())
        val c=compile(listOf(source(input)))
        assertTrue(c.complete)
        val artifactDir=File("build/test-artifacts").apply {mkdirs()}
        c.outputs.single().file.copyTo(File(artifactDir,"tall-output.pdf"),true)
        c.report.copyTo(File(artifactDir,"tall-report.json"),true)
        val mappings=report(c).getJSONArray("files").getJSONObject(0).getJSONArray("coverage")
        assertTrue(mappings.length()>10)
        val slices=(0 until mappings.length()).map { val a=mappings.getJSONObject(it).getJSONArray("sourceBounds");Slice(a.getDouble(0),a.getDouble(1),a.getDouble(2),a.getDouble(3)) }
        assertTrue(Pagination.covers(1000.0,18000.0,slices))
        loadPdf(c.outputs.single().file,root).use {doc ->
            val renderer=PDFRenderer(doc)
            val bitmaps=mutableMapOf<Int,Bitmap>()
            try {
                for(i in 0 until markers.length()) {
                    val mark=markers.getJSONObject(i);val x=mark.getDouble("x");val y=mark.getDouble("y")
                    val mi=slices.indexOfFirst {x>it.left+2 && x<it.right-2 && y>it.top+2 && y<it.bottom-2}
                    assertTrue("Marker $i omitted",mi>=0)
                    val s=slices[mi];val bitmap=bitmaps.getOrPut(mi){renderer.renderImage(mi)}
                    val scale=mappings.getJSONObject(mi).getDouble("scale")
                    val actual=bitmap.getPixel((24+(x-s.left)*scale).roundToInt(),(24+(y-s.top)*scale).roundToInt())
                    val rgb=mark.getJSONArray("rgb")
                    assertTrue("Marker $i red",abs(Color.red(actual)-rgb.getInt(0))<12)
                    assertTrue("Marker $i green",abs(Color.green(actual)-rgb.getInt(1))<12)
                    assertTrue("Marker $i blue",abs(Color.blue(actual)-rgb.getInt(2))<12)
                }
            } finally {bitmaps.values.forEach {it.recycle()}}
            for(i in slices.indices) {
                val page=doc.getPage(i)
                val forms=page.resources.xObjectNames.map {page.resources.getXObject(it)}
                assertTrue(forms.any {it is com.tom_roush.pdfbox.pdmodel.graphics.form.PDFormXObject})
            }
        }
        assertTrue(text(c.outputs.single().file).contains("200 Biologie"))
    }
    @Test fun unicodeGermanTurkishAndMathAreSearchable()=runBlocking {
        val input=File(root,"deutsch-türkçe.txt").apply {writeText("ä ö ü ß\nğ ü ş ı ö ç\n∑ ∫ √ ∞ α β\n")}
        val c=compile(listOf(source(input)))
        assertTrue(c.complete)
        val out=text(c.outputs.single().file)
        listOf("ä ö ü ß","ğ ü ş ı ö ç","∑ ∫ √ ∞ α β").forEach {assertTrue("Missing $it in $out",out.contains(it))}
    }
    @Test fun corruptionDoesNotSilentlySkipAndOtherFilesContinue()=runBlocking {
        val bad=File(root,"corrupt.pdf").apply {writeText("%PDF-1.7 BROKEN")}
        val c=compile(listOf(source(bad),source(pdf("ok.pdf","SURVIVES"))))
        assertFalse(c.complete)
        assertEquals(1,report(c).getInt("failedFiles"));assertEquals(1,report(c).getInt("successfulFiles"))
        val t=text(c.outputs.single().file);assertTrue(t.contains("DOSYA İŞLENEMEDİ"));assertTrue(t.contains("SURVIVES"))
    }
    @Test fun officeFallbackIsExplicitAndOriginalUnchanged()=runBlocking {
        val file=File(root,"slides.pptx").apply {writeBytes(byteArrayOf(80,75,3,4,1,2,3))}
        val hash=sha256(file);val c=compile(listOf(source(file)))
        assertFalse(c.complete);assertEquals(1,report(c).getInt("fallbackFiles"))
        assertEquals(hash,sha256(file));assertTrue(text(c.outputs.single().file).contains("DÖNÜŞÜM GEREKİYOR"))
    }
    @Test fun sourceDigestDetectsModification()=runBlocking {
        val file=pdf("a.pdf","BEFORE");val s=source(file);file.appendText("changed")
        val c=compile(listOf(s));assertFalse(c.complete)
        assertTrue(report(c).getJSONArray("files").getJSONObject(0).getJSONArray("warnings").toString().contains("özeti değişti"))
    }
    @Test fun cancellationLeavesOriginalsAndCleansScratch()=runBlocking {
        val a=source(pdf("a.pdf","A"));val b=source(pdf("b.pdf","B"))
        val run=File(root,"cancel").apply {mkdirs()}
        try {
            Compiler(context,run).compile(listOf(a,b),ExportSettings()) {
                if(it.processed==1) throw CancellationException("test cancellation")
            }
            fail("Cancellation was swallowed")
        } catch(_:CancellationException) { }
        assertTrue(File(a.originalPath).exists());assertTrue(File(b.originalPath).exists())
        assertFalse(File(run,"scratch").exists());assertFalse(File(run,"StudyFuse.pdf").exists())
    }
    @Test fun hundredFileBatchHasNoArtificialLimit()=runBlocking {
        val file=pdf("sample.pdf","BATCH")
        val batch=(0 until 105).map {source(file,"$it.pdf",it)}
        val c=compile(batch);assertTrue(c.complete)
        val r=report(c);assertEquals(105,r.getInt("successfulFiles"));assertEquals(105,r.getInt("originalPages"))
        val files=r.getJSONArray("files")
        for(i in batch.indices) assertEquals(i+1,files.getJSONObject(i).getInt("startPage"))
    }
    @Test fun aiModeTocAndSourceRangesAgree()=runBlocking {
        val c=compile(listOf(source(pdf("one.pdf","FIRST")),source(pdf("two.pdf","SECOND"))),ExportSettings())
        val r=report(c);val f=r.getJSONArray("files")
        assertEquals(2,f.getJSONObject(0).getInt("startPage"));assertEquals(3,f.getJSONObject(0).getInt("endPage"))
        assertEquals(4,f.getJSONObject(1).getInt("startPage"));assertEquals(5,f.getJSONObject(1).getInt("endPage"))
        val t=text(c.outputs.single().file);assertTrue(t.contains("İÇİNDEKİLER"));assertTrue(t.contains("Sayfalar: 2–3"))
    }
    @Test fun rotationsAndNonzeroOriginDoNotStretchOrClip()=runBlocking {
        for(rotation in listOf(0,90,180,270)) {
            val file=File(root,"rot-$rotation.pdf")
            newPdf(root).use {doc ->
                val page=PDPage(PDRectangle(40f,80f,200f,300f));page.rotation=rotation;doc.addPage(page)
                PDPageContentStream(doc,page).use {cs ->
                    cs.setNonStrokingColor(255,0,0);cs.addRect(40f,80f,100f,150f);cs.fill()
                    cs.setNonStrokingColor(0,0,255);cs.addRect(140f,230f,100f,150f);cs.fill()
                };doc.save(file)
            }
            val dest=File(root,"rot-out-$rotation.pdf");val s=source(file);val result=FileResult(s)
            PdfAdapter(context,root).convert(s,dest,ExportSettings(pageSize=PageSize.ORIGINAL),result){}
            loadPdf(file,root).use {original -> loadPdf(dest,root).use {out ->
                val a=PDFRenderer(original).renderImage(0);val b=PDFRenderer(out).renderImage(0)
                try {
                    assertEquals(a.width+48,b.width);assertEquals(a.height+48,b.height)
                    for(y in 10 until a.height step 20) for(x in 10 until a.width step 20)
                        assertEquals("rotation $rotation ($x,$y)",a.getPixel(x,y),b.getPixel(x+24,y+24))
                } finally {a.recycle();b.recycle()}
            } }
        }
    }
    @Test fun textMalformedUtf8IsReportedInsteadOfReplacementCharacters()=runBlocking {
        val file=File(root,"invalid.txt").apply {writeBytes(byteArrayOf(65,-61,40))}
        val c=compile(listOf(source(file)));assertFalse(c.complete);assertEquals(1,report(c).getInt("failedFiles"))
    }
    @Test fun splittingUsesActualSizesAndKeepsAllPageNumbers()=runBlocking {
        val image=File(root,"noise.png")
        val pixels=IntArray(600*600);val rng=java.util.Random(52)
        for(i in pixels.indices) pixels[i]=0xff000000.toInt() or rng.nextInt(0x1000000)
        val bitmap=Bitmap.createBitmap(pixels,600,600,Bitmap.Config.ARGB_8888)
        image.outputStream().use {bitmap.compress(Bitmap.CompressFormat.PNG,100,it)};bitmap.recycle()
        val c=compile(listOf(source(image,"noise-1.png"),source(image,"noise-2.png")),ExportSettings(mode=ExportMode.ARCHIVE,volumeLimitMb=1))
        assertTrue(c.outputs.size>1);assertEquals(c.pages,c.outputs.sumOf {it.pages})
        var next=1
        for(volume in c.outputs) {
            assertEquals(next,volume.start);next=volume.end+1
            if(volume.file.length()>1024*1024) assertEquals(1,volume.pages)
            assertTrue(text(volume.file).contains("${volume.start} / ${c.pages}"))
        }
        assertEquals(c.pages+1,next)
    }
    @Test fun inkAnnotationIsFlattenedAndStillVisible()=runBlocking {
        val input=File(root,"ink.pdf")
        newPdf(root).use {doc ->
            val page=PDPage(PDRectangle(200f,200f));doc.addPage(page)
            val ink=com.tom_roush.pdfbox.pdmodel.interactive.annotation.PDAnnotationMarkup().apply {
                cosObject.setName(COSName.SUBTYPE,"Ink")
            }
            ink.rectangle=PDRectangle(20f,20f,160f,160f)
            ink.inkList=arrayOf(floatArrayOf(30f,30f,170f,170f))
            ink.color=com.tom_roush.pdfbox.pdmodel.graphics.color.PDColor(floatArrayOf(1f,0f,0f),com.tom_roush.pdfbox.pdmodel.graphics.color.PDDeviceRGB.INSTANCE)
            ink.borderStyle=com.tom_roush.pdfbox.pdmodel.interactive.annotation.PDBorderStyleDictionary().apply {width=4f}
            page.annotations=listOf(ink);doc.save(input)
        }
        val output=File(root,"ink-normalized.pdf");val s=source(input);val result=FileResult(s)
        PdfAdapter(context,root).convert(s,output,ExportSettings(),result){}
        loadPdf(output,root).use {doc ->
            val bitmap=PDFRenderer(doc).renderImage(0)
            var red=0
            for(y in 24 until 220) for(x in 24 until 220) {val p=bitmap.getPixel(x,y);if(Color.red(p)>200 && Color.green(p)<50) red++}
            assertTrue("Ink missing",red>200);bitmap.recycle()
        }
    }
    @Test fun unsupportedJpxDecoderIsFlaggedAndEncodedImageRetained()=runBlocking {
        val input=File(root,"jpx.pdf")
        val bytes=javaClass.getResourceAsStream("/jpx-red.jp2")!!.readBytes()
        newPdf(root).use {doc ->
            val image=doc.document.createCOSStream().apply {
                setItem(COSName.TYPE,COSName.XOBJECT);setItem(COSName.SUBTYPE,COSName.IMAGE)
                setInt(COSName.WIDTH,64);setInt(COSName.HEIGHT,64);setInt(COSName.BITS_PER_COMPONENT,8)
                setItem(COSName.COLORSPACE,COSName.DEVICERGB);setItem(COSName.FILTER,COSName.JPX_DECODE)
                createRawOutputStream().use {it.write(bytes)}
            }
            val page=PDPage(PDRectangle(200f,200f));doc.addPage(page)
            page.resources=PDResources().apply {
                cosObject.setItem(COSName.XOBJECT,com.tom_roush.pdfbox.cos.COSDictionary().apply {setItem(COSName.getPDFName("JPX"),image)})
            }
            PDPageContentStream(doc,page).use {it.appendRawCommands("q 160 0 0 160 20 20 cm /JPX Do Q\n")}
            doc.save(input)
        }
        val c=compile(listOf(source(input)));assertFalse(c.complete)
        assertEquals("REVIEW",report(c).getJSONArray("files").getJSONObject(0).getString("status"))
        val dir=File("build/test-artifacts").apply{mkdirs()};c.outputs.single().file.copyTo(File(dir,"jpx-output.pdf"),true)
        loadPdf(c.outputs.single().file,root).use {doc ->
            val form=doc.getPage(0).resources.xObjectNames.map {doc.getPage(0).resources.getXObject(it)}
                .filterIsInstance<com.tom_roush.pdfbox.pdmodel.graphics.form.PDFormXObject>().first()
            val images=form.resources.cosObject.getDictionaryObject(COSName.XOBJECT) as com.tom_roush.pdfbox.cos.COSDictionary
            val stream=images.getDictionaryObject(COSName.getPDFName("JPX")) as com.tom_roush.pdfbox.cos.COSStream
            assertArrayEquals(bytes,stream.createRawInputStream().use {it.readBytes()})
        }
    }
}
