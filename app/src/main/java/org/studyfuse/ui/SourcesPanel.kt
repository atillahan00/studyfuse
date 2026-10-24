package org.studyfuse.ui

import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import org.studyfuse.core.Format
import org.studyfuse.data.Source
import java.io.File

@Composable
fun SourcesPanel(sources:List<Source>,enabled:Boolean,onImport:()->Unit,onFolder:()->Unit,
    onReplace:(Source)->Unit,model:StudyViewModel,onPreview:(Source)->Unit,onOpen:(File,String)->Unit,modifier:Modifier=Modifier) {
    var rename by remember { mutableStateOf<Source?>(null) }
    var help by remember { mutableStateOf<Source?>(null) }
    val order=remember { mutableStateListOf<Source>() }
    var dragging by remember { mutableStateOf<String?>(null) }
    var distance by remember { mutableFloatStateOf(0f) }
    val heights=remember { mutableStateMapOf<String,Int>() }
    LaunchedEffect(sources,dragging) { if(dragging==null) { order.clear(); order.addAll(sources) } }
    Column(modifier.padding(vertical=12.dp)) {
        Row(horizontalArrangement=Arrangement.spacedBy(10.dp)) {
            FilledTonalButton(onClick=onImport,enabled=enabled) { Icon(Icons.Default.Add,null); Spacer(Modifier.width(6.dp)); Text("Dosyalar") }
            OutlinedButton(onClick=onFolder,enabled=enabled) { Icon(Icons.Default.CreateNewFolder,null); Spacer(Modifier.width(6.dp)); Text("Klasör") }
        }
        Text("Sıralamak için tutup sürükle. Menüdeki oklarla da taşıyabilirsin.",style=MaterialTheme.typography.bodySmall,
            modifier=Modifier.padding(vertical=12.dp),color=MaterialTheme.colorScheme.onSurfaceVariant)
        if(order.isEmpty()) Box(Modifier.weight(1f).fillMaxWidth(),contentAlignment=Alignment.Center) {
            Column(horizontalAlignment=Alignment.CenterHorizontally) {
                Icon(Icons.Default.NoteAdd,null,Modifier.size(56.dp),tint=MaterialTheme.colorScheme.primary)
                Spacer(Modifier.height(16.dp)); Text("Notlarını buraya ekle",style=MaterialTheme.typography.titleLarge)
                Text("PDF · Görüntü · UTF-8 metin",Modifier.padding(top=6.dp))
            }
        } else LazyColumn(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(8.dp),contentPadding=PaddingValues(bottom=24.dp)) {
            items(order,key={it.id}) { source ->
                var expanded by remember { mutableStateOf(false) }
                val format=Format.valueOf(source.format)
                val external=!format.native
                OutlinedCard(Modifier.fillMaxWidth().zIndex(if(dragging==source.id) 1f else 0f)
                    .onSizeChanged { heights[source.id]=it.height }
                    .pointerInput(source.id,enabled) {
                        if(enabled) detectDragGesturesAfterLongPress(
                            onDragStart={dragging=source.id;distance=0f},
                            onDragEnd={model.reorder(order.toList());dragging=null;distance=0f},
                            onDragCancel={dragging=null;distance=0f},
                            onDrag={change,delta ->
                                change.consume(); distance+=delta.y
                                val index=order.indexOfFirst { it.id==source.id }
                                val height=(heights[source.id] ?: 120)+8
                                if(index>=0 && distance>height*.65 && index<order.lastIndex) {
                                    val moved=order.removeAt(index);order.add(index+1,moved);distance-=height
                                } else if(index>0 && distance< -height*.65) {
                                    val moved=order.removeAt(index);order.add(index-1,moved);distance+=height
                                }
                            }
                        )
                    }.semantics { contentDescription="${order.indexOf(source)+1}. ${source.section}" },
                    colors=CardDefaults.outlinedCardColors(containerColor=if(dragging==source.id) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface)) {
                    Row(Modifier.padding(start=10.dp,top=12.dp,bottom=12.dp,end=4.dp),verticalAlignment=Alignment.CenterVertically) {
                        Icon(Icons.Default.DragIndicator,"Sürükleyerek sırala",tint=MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.width(8.dp))
                        Column(Modifier.weight(1f)) {
                            Text(source.section,fontWeight=FontWeight.SemiBold)
                            Text(source.name,style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
                            Text("${order.indexOf(source)+1} · ${format.name} · ${sizeLabel(source.bytes)}"+
                                if(source.replacement) " · PDF kopyası" else "",style=MaterialTheme.typography.labelSmall)
                            if(external) TextButton(onClick={help=source},contentPadding=PaddingValues(0.dp)) {
                                Icon(Icons.Default.WarningAmber,null,Modifier.size(16.dp)); Spacer(Modifier.width(4.dp)); Text("PDF dönüşümü gerekiyor")
                            }
                        }
                        IconButton(onClick={onPreview(source)},enabled=enabled) {Icon(Icons.Default.Visibility,"Önizle ${source.name}")}
                        Box {
                            IconButton(onClick={expanded=true},enabled=enabled) {Icon(Icons.Default.MoreVert,"Kaynak işlemleri")}
                            DropdownMenu(expanded=expanded,onDismissRequest={expanded=false}) {
                                DropdownMenuItem(text={Text("Bölüm adını değiştir")},onClick={expanded=false;rename=source})
                                DropdownMenuItem(text={Text("Yukarı taşı")},enabled=order.indexOf(source)>0,onClick={
                                    val i=order.indexOf(source);val copy=order.toMutableList();copy.add(i-1,copy.removeAt(i));model.reorder(copy);expanded=false
                                })
                                DropdownMenuItem(text={Text("Aşağı taşı")},enabled=order.indexOf(source)<order.lastIndex,onClick={
                                    val i=order.indexOf(source);val copy=order.toMutableList();copy.add(i+1,copy.removeAt(i));model.reorder(copy);expanded=false
                                })
                                DropdownMenuItem(text={Text("PDF ile değiştir")},onClick={expanded=false;onReplace(source)})
                                DropdownMenuItem(text={Text("Orijinali başka uygulamada aç")},onClick={expanded=false;onOpen(File(source.originalPath),mime(source.name))})
                                DropdownMenuItem(text={Text("Projeden kaldır")},onClick={expanded=false;model.remove(source)})
                            }
                        }
                    }
                }
            }
        }
    }
    rename?.let { s -> EditDialog("Bölüm adı",s.section,{rename=null}) {model.renameSource(s,it)} }
    help?.let { s -> AlertDialog(onDismissRequest={help=null},title={Text("${Format.valueOf(s.format).name} dönüştürme")},
        text={Text(Format.valueOf(s.format).explanation+".\n\n"+
            "Kaynak uygulamada tüm sayfaları / slaytları PDF olarak dışa aktar. Sonra bu kaynağın menüsünden PDF ile değiştir seçeneğini kullan. " +
            "Orijinal dosya, bölüm adı ve sırası korunur. Yerine koyduğun PDF'nin eksiksizliğini önizlemede denetle. " +
            "Dönüştürmeden derlersen uyarı sayfası eklenir ve çıktı EKSİK olarak işaretlenir.")},
        confirmButton={TextButton(onClick={help=null;onReplace(s)},enabled=enabled){Text("PDF seç")}},
        dismissButton={TextButton(onClick={help=null}){Text("Tamam")}}) }
}
