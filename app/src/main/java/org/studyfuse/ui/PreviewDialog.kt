package org.studyfuse.ui

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.*
import kotlinx.coroutines.*
import org.json.*
import org.studyfuse.engine.*
import java.io.File

private data class SearchHit(val page:Int,val text:String,val kind:String)
@Composable fun PreviewDialog(file:File,name:String,onDismiss:()->Unit,initialPage:Int=0) {
    var page by remember(file){mutableIntStateOf(initialPage)}
    var total by remember(file){mutableIntStateOf(1)}
    var query by remember{mutableStateOf("")}
    var jump by remember(file){mutableStateOf((initialPage+1).toString())}
    var grid by remember{mutableStateOf(false)}
    var hits by remember{mutableStateOf<List<SearchHit>>(emptyList())}
    var searching by remember{mutableStateOf(false)}
    var searchError by remember{mutableStateOf<String?>(null)}
    var searchId by remember{mutableIntStateOf(0)}
    var sections by remember(file){mutableStateOf<List<Pair<String,Int>>>(emptyList())}
    var showSections by remember{mutableStateOf(false)}
    LaunchedEffect(file){withContext(Dispatchers.IO){runCatching{DocumentView.count(file)}.onSuccess{total=it.coerceAtLeast(1)}}}
    LaunchedEffect(file,total){
        withContext(Dispatchers.IO){
            runCatching {
                val r=JSONObject(File(file.parentFile,"StudyFuse-rapor.json").readText());val files=r.getJSONArray("files")
                val outputs=r.getJSONArray("outputs");val start=(0 until outputs.length()).map{outputs.getJSONObject(it)}.firstOrNull{it.getString("path")==file.path}?.optInt("startPage",1) ?: 1
                sections=(0 until files.length()).map{files.getJSONObject(it)}.map{it.getString("section") to (it.getInt("contentStart")-start)}.filter{it.second in 0 until total}
            }
        }
    }
    LaunchedEffect(searchId) {
        if(searchId==0 || query.isBlank())return@LaunchedEffect
        searching=true;searchError=null
        try {
            val term=query
            hits=withContext(Dispatchers.IO){
                val index=File(file.parentFile,"StudyFuse-index.json")
                if(index.exists()) {
                    val r=JSONObject(File(file.parentFile,"StudyFuse-rapor.json").readText());val outputs=r.getJSONArray("outputs")
                    val start=(0 until outputs.length()).map{outputs.getJSONObject(it)}.firstOrNull{it.getString("path")==file.path}?.optInt("startPage",1) ?: 1
                    val rows=JSONArray(index.readText())
                    (0 until rows.length()).map{rows.getJSONObject(it)}.filter{it.getInt("page")-start in 0 until total && PageText.matches(it.optString("text")+"\n"+it.optString("section"),term)}
                        .map{SearchHit(it.getInt("page")-start,it.optString("text").take(220),if(it.optString("kind")=="unverified_ocr") "Doğrulanmamış OCR" else "PDF metni")}
                } else if(DocumentView.isPdf(file)) loadPdf(file,file.parentFile!!).use {doc->
                    val result=mutableListOf<SearchHit>()
                    for(i in 0 until doc.numberOfPages){currentCoroutineContext().ensureActive();val text=PageText.extract(doc,i)
                        if(PageText.matches(text,term))result+=SearchHit(i,text.take(220),"PDF metni")}
                    result
                } else emptyList()
            }
        }catch(e:CancellationException){throw e}catch(e:Exception){searchError=e.message}finally{searching=false}
    }
    Dialog(onDismissRequest=onDismiss,properties=DialogProperties(usePlatformDefaultWidth=false)) {
        Surface(Modifier.fillMaxWidth(.97f).fillMaxHeight(.97f),shape=MaterialTheme.shapes.large) {
            Column {
                Row(Modifier.padding(8.dp),verticalAlignment=Alignment.CenterVertically) {
                    Text(name,Modifier.weight(1f),style=MaterialTheme.typography.titleMedium,maxLines=1)
                    TextButton(onClick={grid=!grid}){Text(if(grid)"Sayfa" else "Küçük resimler")}
                    if(sections.isNotEmpty())TextButton(onClick={showSections=true}){Text("Bölümler")}
                    TextButton(onClick=onDismiss){Text("Kapat")}
                }
                Row(Modifier.padding(horizontal=12.dp),verticalAlignment=Alignment.CenterVertically) {
                    OutlinedTextField(query,{query=it},label={Text("Belgede ara")},singleLine=true,modifier=Modifier.weight(1f))
                    TextButton(onClick={searchId++},enabled=query.isNotBlank() && !searching){Text(if(searching)"Aranıyor…" else "Ara")}
                }
                searchError?.let{Text(it,Modifier.padding(8.dp),color=MaterialTheme.colorScheme.error)}
                if(searchId>0 && !searching && hits.isEmpty())Text("Sonuç bulunamadı. Görüntü/el yazısı metni için OCR gerekebilir; el yazısı tanıma desteklenmez.",Modifier.padding(8.dp),style=MaterialTheme.typography.bodySmall)
                if(hits.isNotEmpty())LazyRow(Modifier.heightIn(max=100.dp)) {
                    items(hits){h->OutlinedCard(onClick={page=h.page;jump=(page+1).toString();grid=false},modifier=Modifier.width(240.dp).padding(4.dp)){
                        Text("${h.page+1}. sayfa · ${h.kind}",Modifier.padding(6.dp),style=MaterialTheme.typography.labelSmall)
                        Text(h.text,Modifier.padding(horizontal=6.dp),maxLines=2,style=MaterialTheme.typography.bodySmall)
                    }}
                }
                if(grid)LazyVerticalGrid(GridCells.Adaptive(150.dp),Modifier.weight(1f)) {
                    items(total){i->Column(Modifier.height(210.dp).padding(6.dp).clickable{page=i;jump=(i+1).toString();grid=false}) {
                        Thumbnail(file,i,Modifier.weight(1f).fillMaxWidth());Text("${i+1}. sayfa")
                    }}
                } else DocumentPane(file,page,Modifier.weight(1f).fillMaxWidth())
                Row(Modifier.fillMaxWidth().padding(horizontal=8.dp),horizontalArrangement=Arrangement.Center,verticalAlignment=Alignment.CenterVertically) {
                    TextButton(onClick={page--;jump=(page+1).toString()},enabled=page>0){Text("Önceki")}
                    OutlinedTextField(jump,{jump=it.filter{c->c.isDigit()}},label={Text("Sayfa / $total")},singleLine=true,modifier=Modifier.width(120.dp))
                    TextButton(onClick={page=(jump.toIntOrNull() ?:1).coerceIn(1,total)-1;jump=(page+1).toString()}){Text("Git")}
                    TextButton(onClick={page++;jump=(page+1).toString()},enabled=page+1<total){Text("Sonraki")}
                }
            }
        }
    }
    if(showSections)AlertDialog(onDismissRequest={showSections=false},title={Text("Bölümler")},text={LazyColumn{items(sections){(title,p)->TextButton(onClick={page=p;jump=(p+1).toString();showSections=false}){Text("${p+1} · $title")}}}},confirmButton={TextButton(onClick={showSections=false}){Text("Kapat")}})
}
@Composable private fun Thumbnail(file:File,page:Int,modifier:Modifier) {
    var bitmap by remember(file,page){mutableStateOf<android.graphics.Bitmap?>(null)}
    LaunchedEffect(file,page){bitmap=withContext(Dispatchers.IO){runCatching{DocumentView.render(file,page,180,220)}.getOrNull()}}
    Box(modifier,contentAlignment=Alignment.Center){bitmap?.let{Image(it.asImageBitmap(),"${page+1}. sayfa",Modifier.fillMaxSize())} ?: Text("Önizleme")}
}
