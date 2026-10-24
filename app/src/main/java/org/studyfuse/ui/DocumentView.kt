package org.studyfuse.ui

import android.graphics.*
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.*
import kotlinx.coroutines.*
import org.studyfuse.core.Slice
import org.studyfuse.data.readPrefix
import org.studyfuse.engine.ImageSource
import java.io.File
import kotlin.math.*

object DocumentView {
    fun isPdf(file:File)=file.inputStream().use {String(it.readPrefix(5),Charsets.US_ASCII)=="%PDF-"}
    fun count(file:File):Int = if(isPdf(file)) ParcelFileDescriptor.open(file,ParcelFileDescriptor.MODE_READ_ONLY).use {fd->PdfRenderer(fd).use{it.pageCount}} else 1
    /** Render only the viewport; zoom never allocates a full-height long-page bitmap. */
    fun render(file:File,page:Int,width:Int,height:Int,zoom:Float=1f,pan:Offset=Offset.Zero,area:Slice?=null):Bitmap {
        require(width>0 && height>0)
        val bitmap=Bitmap.createBitmap(width,height,Bitmap.Config.ARGB_8888)
        bitmap.eraseColor(Color.WHITE)
        try {
            if(isPdf(file)) ParcelFileDescriptor.open(file,ParcelFileDescriptor.MODE_READ_ONLY).use {fd->
                PdfRenderer(fd).use{renderer->renderer.openPage(page.coerceIn(0,renderer.pageCount-1)).use{p->
                    val a=area ?: Slice(0.0,0.0,p.width.toDouble(),p.height.toDouble())
                    val scale=min(width/a.width,height/a.height).toFloat()*zoom
                    val tx=(width-a.width*scale).toFloat()/2+pan.x-a.left.toFloat()*scale
                    val ty=(height-a.height*scale).toFloat()/2+pan.y-a.top.toFloat()*scale
                    val matrix=Matrix().apply{setScale(scale,scale);postTranslate(tx,ty)}
                    val clip=Rect(floor(tx+a.left*scale).toInt().coerceIn(0,width),floor(ty+a.top*scale).toInt().coerceIn(0,height),
                        ceil(tx+a.right*scale).toInt().coerceIn(0,width),ceil(ty+a.bottom*scale).toInt().coerceIn(0,height))
                    if(clip.width()>0 && clip.height()>0)p.render(bitmap,clip,matrix,PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                }}
            } else ImageSource(file).use {image->
                val a=area ?: Slice(0.0,0.0,image.width.toDouble(),image.height.toDouble())
                val scale=min(width/a.width,height/a.height).toFloat()*zoom
                val tx=(width-a.width*scale).toFloat()/2+pan.x-a.left.toFloat()*scale
                val ty=(height-a.height*scale).toFloat()/2+pan.y-a.top.toFloat()*scale
                val rect=Rect(floor(-tx/scale.toDouble()).toInt().coerceIn(0,image.width),floor(-ty/scale.toDouble()).toInt().coerceIn(0,image.height),
                    ceil((width-tx)/scale.toDouble()).toInt().coerceIn(0,image.width),ceil((height-ty)/scale.toDouble()).toInt().coerceIn(0,image.height))
                if(rect.width()>0 && rect.height()>0) {
                    val part=image.region(rect,max(1,floor(1/scale.toDouble()).toInt()))
                    try {Canvas(bitmap).apply{clipRect((tx+a.left*scale).toFloat(),(ty+a.top*scale).toFloat(),(tx+a.right*scale).toFloat(),(ty+a.bottom*scale).toFloat());drawBitmap(part,null,RectF(tx+rect.left*scale,ty+rect.top*scale,tx+rect.right*scale,ty+rect.bottom*scale),Paint(Paint.FILTER_BITMAP_FLAG))}}
                    finally{part.recycle()}
                }
            }
            return bitmap
        } catch(e:Exception){bitmap.recycle();throw e}
    }
}

@Composable fun DocumentPane(file:File,page:Int,modifier:Modifier=Modifier,area:Slice?=null) {
    var size by remember {mutableStateOf(IntSize(400,500))}
    var zoom by remember(file,page,area){mutableFloatStateOf(1f)}
    var pan by remember(file,page,area){mutableStateOf(Offset.Zero)}
    var bitmap by remember{mutableStateOf<Bitmap?>(null)}
    var error by remember{mutableStateOf<String?>(null)}
    var rendering by remember{mutableStateOf(false)}
    LaunchedEffect(file,page,size,zoom,pan,area) {
        rendering=true;error=null
        try {
            delay(35)
            val next=withContext(Dispatchers.IO){DocumentView.render(file,page,size.width,size.height,zoom,pan,area)}
            bitmap=next
        }catch(e:CancellationException){throw e}catch(e:Exception){error=e.message}finally{rendering=false}
    }
    // Compose/RenderThread may still hold the previous frame; let Android release it after use.
    Column(modifier) {
        Box(Modifier.weight(1f).fillMaxWidth().onSizeChanged{if(it.width>0 && it.height>0)size=it}
            .pointerInput(file,page){detectTransformGestures{_,move,scale,_->zoom=(zoom*scale).coerceIn(1f,32f);pan+=move}},contentAlignment=Alignment.Center) {
            bitmap?.let{Image(it.asImageBitmap(),"Sayfa ${page+1}",Modifier.fillMaxSize())}
            error?.let{Text("Önizleme: $it",Modifier.padding(12.dp))}
            if(rendering && bitmap==null)CircularProgressIndicator()
        }
        Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.Center,modifier=Modifier.fillMaxWidth()) {
            TextButton(onClick={zoom=(zoom/1.5f).coerceAtLeast(1f)}){Text("−")}
            Text("${(zoom*100).toInt()}%",style=MaterialTheme.typography.labelSmall)
            TextButton(onClick={zoom=(zoom*1.5f).coerceAtMost(32f)}){Text("+")}
            TextButton(onClick={zoom=1f;pan=Offset.Zero}){Text("Sıfırla")}
        }
    }
}
