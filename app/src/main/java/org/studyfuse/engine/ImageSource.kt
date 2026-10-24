package org.studyfuse.engine

import android.graphics.*
import androidx.exifinterface.media.ExifInterface
import java.io.File
import kotlin.math.*

/** Region decoding keeps long screenshots out of a single full-height bitmap. */
class ImageSource(private val file:File):AutoCloseable {
    private val bounds=BitmapFactory.Options().apply { inJustDecodeBounds=true }
    private val orientation=runCatching { ExifInterface(file).getAttributeInt(ExifInterface.TAG_ORIENTATION,1) }.getOrDefault(1)
    private val decoder:BitmapRegionDecoder?
    val rawWidth:Int
    val rawHeight:Int
    val width:Int
    val height:Int
    private val transform=Matrix()
    init {
        BitmapFactory.decodeFile(file.path,bounds)
        rawWidth=bounds.outWidth; rawHeight=bounds.outHeight
        require(rawWidth>0 && rawHeight>0) { "Android bu görüntüyü çözemedi. PDF olarak dışa aktarın." }
        width=if(orientation in 5..8) rawHeight else rawWidth
        height=if(orientation in 5..8) rawWidth else rawHeight
        val values=when(orientation) {
            2 -> floatArrayOf(-1f,0f,rawWidth.toFloat(),0f,1f,0f,0f,0f,1f)
            3 -> floatArrayOf(-1f,0f,rawWidth.toFloat(),0f,-1f,rawHeight.toFloat(),0f,0f,1f)
            4 -> floatArrayOf(1f,0f,0f,0f,-1f,rawHeight.toFloat(),0f,0f,1f)
            5 -> floatArrayOf(0f,1f,0f,1f,0f,0f,0f,0f,1f)
            6 -> floatArrayOf(0f,-1f,rawHeight.toFloat(),1f,0f,0f,0f,0f,1f)
            7 -> floatArrayOf(0f,-1f,rawHeight.toFloat(),-1f,0f,rawWidth.toFloat(),0f,0f,1f)
            8 -> floatArrayOf(0f,1f,0f,-1f,0f,rawWidth.toFloat(),0f,0f,1f)
            else -> floatArrayOf(1f,0f,0f,0f,1f,0f,0f,0f,1f)
        }
        transform.setValues(values)
        @Suppress("DEPRECATION")
        decoder=runCatching { BitmapRegionDecoder.newInstance(file.path,false) }.getOrNull()
    }
    fun region(display:Rect,sample:Int=1):Bitmap {
        val inverse=Matrix(); check(transform.invert(inverse))
        val mapped=RectF(display); inverse.mapRect(mapped)
        val rect=Rect(floor(mapped.left.toDouble()).toInt().coerceAtLeast(0),floor(mapped.top.toDouble()).toInt().coerceAtLeast(0),
            ceil(mapped.right.toDouble()).toInt().coerceAtMost(rawWidth),ceil(mapped.bottom.toDouble()).toInt().coerceAtMost(rawHeight))
        val maxBytes=Runtime.getRuntime().maxMemory()/4
        check(rect.width().toLong()*rect.height()*4/(sample.toLong()*sample)<maxBytes) {
            "Bu görüntü bölgesi aygıtın güvenli bellek bütçesini aşıyor. A4 sayfalama seçin veya kaynak uygulamada bölün."
        }
        val options=BitmapFactory.Options().apply { inSampleSize=sample; inPreferredConfig=Bitmap.Config.ARGB_8888 }
        // BitmapFactory is the more broadly supported path for small images (including HEIC).
        // Region decoding is reserved for large canvases where a full bitmap would be wasteful.
        val small=rawWidth.toLong()*rawHeight*4<min(32L*1024*1024,maxBytes/2)
        val raw=if(decoder!=null && !small) decoder.decodeRegion(rect,options) else {
            check(rawWidth.toLong()*rawHeight*4/(sample.toLong()*sample)<maxBytes) {
                "Bu biçimde bölgesel çözümleme yok; görüntü bellek bütçesini aşıyor. PDF olarak dönüştürün."
            }
            val full=BitmapFactory.decodeFile(file.path,options) ?: error("Görüntü okunamadı")
            val r=Bitmap.createBitmap(full,rect.left/sample,rect.top/sample,
                min(ceil(rect.width().toDouble()/sample).toInt(),full.width-rect.left/sample),
                min(ceil(rect.height().toDouble()/sample).toInt(),full.height-rect.top/sample))
            if(r!==full) full.recycle(); r
        } ?: error("Görüntü bölgesi okunamadı")
        val local=Matrix(transform).apply {
            val v=FloatArray(9); getValues(v); v[2]=0f; v[5]=0f; setValues(v)
        }
        val result=Bitmap.createBitmap(raw,0,0,raw.width,raw.height,local,false)
        if(result!==raw) raw.recycle()
        return result
    }
    fun whitespaceRows():List<Double> {
        val sample=max(1,ceil(max(width,height)/1800.0).toInt())
        val thumb=region(Rect(0,0,width,height),sample)
        return try {
            val rows=mutableListOf<Double>(); val pixels=IntArray(thumb.width)
            for(y in 1 until thumb.height-1) {
                thumb.getPixels(pixels,0,thumb.width,0,y,thumb.width,1)
                val ink=pixels.count { Color.alpha(it)>30 && min(Color.red(it),min(Color.green(it),Color.blue(it)))<236 }
                if(ink<=max(1,thumb.width/300)) rows+=y.toDouble()*height/thumb.height*POINTS_PER_PIXEL
            }
            rows
        } finally { thumb.recycle() }
    }
    override fun close() { decoder?.recycle() }
    companion object { const val POINTS_PER_PIXEL=72.0/150.0 }
}
