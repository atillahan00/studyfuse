package org.studyfuse.ui

import android.graphics.*
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.*
import org.studyfuse.engine.ImageSource
import org.studyfuse.data.readPrefix
import java.io.File
import kotlin.math.*

private data class Frame(val bitmap:Bitmap,val pages:Int)
@Composable fun PreviewDialog(file:File,name:String,onDismiss:()->Unit) {
    var page by remember(file) { mutableIntStateOf(0) }
    var frame by remember { mutableStateOf<Frame?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var loading by remember { mutableStateOf(true) }
    var zoom by remember(file,page) { mutableFloatStateOf(1f) }
    var pan by remember(file,page) { mutableStateOf(Offset.Zero) }
    LaunchedEffect(file,page) {
        loading=true;error=null
        try {
            val next=withContext(Dispatchers.IO) {
                val pdf=file.inputStream().use { String(it.readPrefix(5),Charsets.US_ASCII)=="%PDF-" }
                if(pdf) ParcelFileDescriptor.open(file,ParcelFileDescriptor.MODE_READ_ONLY).use { fd ->
                    PdfRenderer(fd).use { renderer ->
                        renderer.openPage(page.coerceIn(0,renderer.pageCount-1)).use { p ->
                            val s=min(2f,1600f/max(p.width,p.height))
                            val bitmap=Bitmap.createBitmap(max(1,(p.width*s).roundToInt()),max(1,(p.height*s).roundToInt()),Bitmap.Config.ARGB_8888)
                            bitmap.eraseColor(android.graphics.Color.WHITE)
                            p.render(bitmap,null,null,PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                            Frame(bitmap,renderer.pageCount)
                        }
                    }
                } else ImageSource(file).use { image ->
                    val sample=max(1,ceil(max(image.width,image.height)/1800.0).toInt())
                    Frame(image.region(Rect(0,0,image.width,image.height),sample),1)
                }
            }
            frame=next
        } catch(e:CancellationException) { throw e }
        catch(e:Exception) { error=e.message ?: "Önizleme oluşturulamadı" }
        finally {loading=false}
    }
    // Compose's bitmap painter can retain the previous frame for a draw pass; recycle on disposal.
    DisposableEffect(frame) { val current=frame; onDispose { current?.bitmap?.recycle() } }
    Dialog(onDismissRequest=onDismiss,properties=DialogProperties(usePlatformDefaultWidth=false)) {
        Surface(Modifier.fillMaxWidth(.96f).fillMaxHeight(.96f),shape=MaterialTheme.shapes.large) {
            Column {
                Row(Modifier.fillMaxWidth().padding(12.dp),verticalAlignment=Alignment.CenterVertically) {
                    Text(name,Modifier.weight(1f),style=MaterialTheme.typography.titleMedium,maxLines=2)
                    IconButton(onClick=onDismiss) {Icon(Icons.Default.Close,"Önizlemeyi kapat")}
                }
                Box(Modifier.weight(1f).fillMaxWidth().background(MaterialTheme.colorScheme.surfaceVariant).clipToBounds()
                    .pointerInput(file,page) {detectTransformGestures {_,offset,scale,_ -> zoom=(zoom*scale).coerceIn(1f,8f);pan+=offset}},contentAlignment=Alignment.Center) {
                    if(error!=null) Text("Önizleme açılamadı: $error",Modifier.padding(24.dp))
                    else frame?.let { f -> Image(f.bitmap.asImageBitmap(),"Sayfa ${page+1}",contentScale=ContentScale.Fit,
                        modifier=Modifier.fillMaxSize().graphicsLayer { scaleX=zoom;scaleY=zoom;translationX=pan.x;translationY=pan.y }) }
                    if(loading) CircularProgressIndicator()
                }
                Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.Center) {
                    IconButton(onClick={page--},enabled=page>0 && !loading) {Icon(Icons.Default.NavigateBefore,"Önceki sayfa")}
                    Text("${page+1} / ${frame?.pages ?: "…"}")
                    IconButton(onClick={page++},enabled=page+1<(frame?.pages ?: 0) && !loading) {Icon(Icons.Default.NavigateNext,"Sonraki sayfa")}
                    IconButton(onClick={zoom=(zoom/1.5f).coerceAtLeast(1f)}) {Icon(Icons.Default.ZoomOut,"Uzaklaştır")}
                    IconButton(onClick={zoom=(zoom*1.5f).coerceAtMost(8f)}) {Icon(Icons.Default.ZoomIn,"Yakınlaştır")}
                    IconButton(onClick={zoom=1f;pan=Offset.Zero}) {Icon(Icons.Default.CenterFocusStrong,"Görünümü sıfırla")}
                }
                if((frame?.pages ?: 0)>1) Slider(value=page.toFloat(),onValueChange={page=it.roundToInt()},
                    valueRange=0f..((frame?.pages ?: 1)-1).toFloat(),enabled=!loading,modifier=Modifier.padding(horizontal=24.dp))
            }
        }
    }
}
