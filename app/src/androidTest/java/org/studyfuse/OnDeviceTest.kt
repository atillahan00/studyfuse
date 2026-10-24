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
}
