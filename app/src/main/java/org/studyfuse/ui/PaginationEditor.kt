package org.studyfuse.ui

import android.content.Context
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.*
import kotlinx.coroutines.*
import org.studyfuse.core.*
import org.studyfuse.data.*
import org.studyfuse.engine.*
import java.io.File
import java.util.UUID

private data class LayoutPreview(val file:File,val result:FileResult)
@Composable fun PaginationEditor(source:Source,settings:ExportSettings,onDismiss:()->Unit,onSave:(String)->Unit) {
    val context=LocalContext.current
    val dir=remember{File(context.cacheDir,"pagination-${UUID.randomUUID()}").apply{mkdirs()}}
    var draft by remember{mutableStateOf(source.pagination)}
    var request by remember{mutableIntStateOf(0)}
    var preview by remember{mutableStateOf<LayoutPreview?>(null)}
    var renderedDraft by remember{mutableStateOf<String?>(null)}
    var loading by remember{mutableStateOf(true)}
    var error by remember{mutableStateOf<String?>(null)}
    var inputPage by remember{mutableIntStateOf(1)}
    var cutText by remember{mutableStateOf("")}
    var selectedCut by remember{mutableIntStateOf(0)}
    var outputPage by remember{mutableIntStateOf(0)}
    var compare by remember{mutableStateOf(false)}
    var adjacent by remember{mutableStateOf(true)}
    var pageMenu by remember{mutableStateOf(false)}
    LaunchedEffect(request) {
        loading=true;error=null
        try {
            require(settings.pageSize!=PageSize.ORIGINAL){"Önce A4 sayfa düzenini seçin; orijinal boyut bölünmez."}
            val json=draft
            val next=withContext(Dispatchers.IO){
                val s=source.copy(pagination=json);val out=File(dir,"preview-$request.pdf");val r=FileResult(s)
                when(Format.valueOf(source.format)) {
                    Format.PDF->PdfAdapter(context,dir).convert(s,out,settings,r){}
                    Format.IMAGE->ImageAdapter(dir).convert(s,out,settings,r){}
                    else->error("Bölme düzenleyicisi PDF ve görüntüler içindir")
                };LayoutPreview(out,r)
            }
            preview=next;renderedDraft=json;outputPage=outputPage.coerceIn(0,next.result.generatedPages-1)
        }catch(e:CancellationException){throw e}catch(e:Exception){error=e.message}finally{loading=false}
    }
    LaunchedEffect(inputPage,preview) {
        val maps=preview?.result?.mappings?.filter{it.originalPage==inputPage} ?: emptyList()
        val custom=PageCuts.read(draft,inputPage)
        val proposed=maps.map{it.bottom}.distinct().sorted().dropLast(1)
        cutText=(if(custom.isEmpty())proposed else custom).joinToString(", "){"%.1f".format(java.util.Locale.ROOT,it)}
        outputPage=maps.firstOrNull()?.outputPage?.minus(1) ?: 0;selectedCut=0
    }
    DisposableEffect(Unit){onDispose{dir.deleteRecursively()}}
    fun change(text:String) {
        cutText=text
        val parsed=text.split(',',';','\n').map{it.trim()}.filter{it.isNotEmpty()}.mapNotNull{it.toDoubleOrNull()}
        if((text.isBlank() || parsed.size==text.split(',',';','\n').count{it.trim().isNotEmpty()}) && parsed.all{it.isFinite() && it>0}) {
            draft=PageCuts.write(draft,inputPage,parsed);error=null
        } else {error="Noktaları virgülle ayırın; ondalık için nokta kullanın.";renderedDraft=null}
    }
    Dialog(onDismissRequest=onDismiss,properties=DialogProperties(usePlatformDefaultWidth=false)) {
        Surface(Modifier.fillMaxWidth(.97f).fillMaxHeight(.96f),shape=MaterialTheme.shapes.large){Row {
            Column(Modifier.widthIn(max=380.dp).weight(.8f).fillMaxHeight().verticalScroll(rememberScrollState()).padding(16.dp),verticalArrangement=Arrangement.spacedBy(10.dp)) {
                Text("Sayfa bölmelerini düzenle",style=MaterialTheme.typography.titleLarge)
                Text(source.name,style=MaterialTheme.typography.bodySmall)
                Text("Tüm kaynak korunur. Noktalar sayfanın üstünden PDF noktası (pt) olarak ölçülür. Aralık fazla büyükse okunabilirliği korumak için ek sayfalar üretilir.",style=MaterialTheme.typography.bodySmall)
                Box {OutlinedButton(onClick={pageMenu=true},enabled=!loading){Text("Kaynak sayfa: $inputPage")}
                    DropdownMenu(pageMenu,{pageMenu=false}) {
                        (preview?.result?.mappings?.map{it.originalPage}?.distinct() ?: listOf(1)).forEach{p->DropdownMenuItem(text={Text("Sayfa $p")},onClick={inputPage=p;pageMenu=false})}
                    }}
                OutlinedTextField(cutText,{change(it)},label={Text("Bölme noktaları: 700, 1400…")},minLines=2,maxLines=5,enabled=!loading)
                val maps=preview?.result?.mappings?.filter{it.originalPage==inputPage} ?: emptyList()
                val height=maps.firstOrNull()?.height ?: 0.0
                val cuts=cutText.split(',',';','\n').mapNotNull{it.trim().toDoubleOrNull()}
                if(cuts.isNotEmpty() && height>1) {
                    Text("${selectedCut.coerceAtMost(cuts.lastIndex)+1}. bölmeyi taşı")
                    Slider(cuts[selectedCut.coerceAtMost(cuts.lastIndex)].toFloat().coerceIn(.1f,(height-.1).toFloat()),{v->
                        val edited=cuts.toMutableList();edited[selectedCut.coerceAtMost(edited.lastIndex)]=v.toDouble();change(edited.sorted().joinToString(", "){"%.1f".format(java.util.Locale.ROOT,it)})
                    },valueRange=.1f..(height-.1).toFloat(),enabled=!loading)
                    Row {TextButton(onClick={selectedCut=(selectedCut-1).coerceAtLeast(0)}){Text("Önceki bölme")};TextButton(onClick={selectedCut=(selectedCut+1).coerceAtMost(cuts.lastIndex)}){Text("Sonraki bölme")}}
                }
                Text("Kenar: ${settings.margin.toInt()} pt · Örtüşme: ${settings.overlap.toInt()} pt. Bu ayarlar proje dışa aktarma panelinden değiştirilebilir.",style=MaterialTheme.typography.bodySmall)
                error?.let{Text(it,color=MaterialTheme.colorScheme.error)}
                if(loading)LinearProgressIndicator(Modifier.fillMaxWidth())
                Button(onClick={request++},enabled=!loading && error?.startsWith("Noktaları")!=true){Text("Bölmeleri önizle")}
                TextButton(onClick={draft=PageCuts.write(draft,inputPage,emptyList());request++},enabled=!loading){Text("Bu sayfada otomatiğe dön")}
                TextButton(onClick={draft="{}";request++},enabled=!loading){Text("Tüm özel bölmeleri sıfırla")}
                Button(onClick={onSave(draft);onDismiss()},enabled=!loading && error==null && renderedDraft==draft){Text("Bölmeleri kaydet")}
                TextButton(onClick=onDismiss){Text("Vazgeç")}
            }
            VerticalDivider()
            Column(Modifier.weight(1.4f).fillMaxHeight()) {
                preview?.let{p->
                    Text("Önizleme: ${outputPage+1}/${p.result.generatedPages}",Modifier.padding(8.dp))
                    if(renderedDraft!=draft)Text("Değişiklikleri görmek için bölmeleri yeniden önizleyin.",Modifier.padding(8.dp),style=MaterialTheme.typography.bodySmall)
                    Row(Modifier.weight(1f).fillMaxWidth()) {
                        Column(Modifier.weight(1f).fillMaxHeight()){Text("${outputPage+1}. çıktı sayfası",Modifier.padding(6.dp));DocumentPane(p.file,outputPage,Modifier.weight(1f).fillMaxWidth())}
                        if(adjacent && outputPage+1<p.result.generatedPages) {
                            VerticalDivider()
                            Column(Modifier.weight(1f).fillMaxHeight()){Text("${outputPage+2}. çıktı sayfası",Modifier.padding(6.dp));DocumentPane(p.file,outputPage+1,Modifier.weight(1f).fillMaxWidth())}
                        }
                    }
                    Row {
                        TextButton(onClick={outputPage--},enabled=outputPage>0){Text("Önceki")}
                        TextButton(onClick={outputPage++},enabled=outputPage+1<p.result.generatedPages){Text("Sonraki")}
                        TextButton(onClick={compare=true}){Text("Kaynakla karşılaştır")}
                    }
                    TextButton(onClick={adjacent=!adjacent}){Text(if(adjacent)"Tek sayfa göster" else "Komşu sayfayı göster")}
                }
            }
        }}
    }
    if(compare)preview?.let{p->val map=p.result.mappings.firstOrNull{it.outputPage==outputPage+1}
        ComparisonDialog(File(source.activePath),p.file,(map?.originalPage ?:1)-1,outputPage,map,{compare=false})}
}
