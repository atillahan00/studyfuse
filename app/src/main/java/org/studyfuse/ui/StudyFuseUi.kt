package org.studyfuse.ui

import android.webkit.MimeTypeMap
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.studyfuse.core.*
import org.studyfuse.data.*
import org.json.*
import java.io.File

private val Light=lightColorScheme(primary=Color(0xFF146F65),secondary=Color(0xFF496660),
    tertiary=Color(0xFF786345),background=Color(0xFFF6F8F6),surface=Color(0xFFFFFFFF),
    primaryContainer=Color(0xFFD5EFE7),onPrimaryContainer=Color(0xFF073D35))
private val Dark=darkColorScheme(primary=Color(0xFF8AD5C5),background=Color(0xFF111917),
    surface=Color(0xFF19221F),primaryContainer=Color(0xFF224F45))

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StudyFuseUi(model:StudyViewModel,onImport:()->Unit,onFolder:()->Unit,onReplace:(Source)->Unit,
    onOpen:(File,String)->Unit,onShare:(List<File>)->Unit,onSave:(File)->Unit,onSaveAll:(List<File>)->Unit,
    onRestore:()->Unit,onBulkReplace:()->Unit) {
    val mode by model.dark.collectAsStateWithLifecycle()
    val project by model.project.collectAsStateWithLifecycle()
    val selected by model.selected.collectAsStateWithLifecycle()
    val projects by model.projects.collectAsStateWithLifecycle()
    val sources by model.sources.collectAsStateWithLifecycle()
    val importing by model.importing.collectAsStateWithLifecycle()
    val importMessage by model.importMessage.collectAsStateWithLifecycle()
    val notice by model.notice.collectAsStateWithLifecycle()
    var preferences by remember { mutableStateOf(false) }
    var rename by remember { mutableStateOf(false) }
    var preview by remember { mutableStateOf<Pair<File,String>?>(null) }
    var editor by remember{mutableStateOf<Source?>(null)}
    var conversions by remember{mutableStateOf(false)}
    val replacements by model.replacements.collectAsStateWithLifecycle()
    val dark=if(mode==0) isSystemInDarkTheme() else mode==2
    MaterialTheme(colorScheme=if(dark) Dark else Light) {
        val busy=importing || project?.status in setOf("RUNNING","QUEUED")
        Scaffold(containerColor=MaterialTheme.colorScheme.background,topBar={
            TopAppBar(title={ Row(verticalAlignment=Alignment.CenterVertically) {
                Icon(Icons.AutoMirrored.Filled.MenuBook,null,tint=MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(10.dp)); Text("StudyFuse",fontWeight=FontWeight.Bold)
            } },navigationIcon={ if(selected!=null) IconButton(onClick={ model.select(null) },enabled=!importing) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack,"Projeler")
            } },actions={ IconButton(onClick={ preferences=true }) { Icon(Icons.Default.Settings,"Ayarlar") } })
        }) { padding ->
            BoxWithConstraints(Modifier.fillMaxSize().padding(padding)) {
                val tablet=maxWidth>=850.dp
                if(selected==null) Home(projects,importing,{model.create()},onImport,{ model.select(it) },{model.deleteProject(it)},onRestore)
                else if(project==null) Box(Modifier.fillMaxSize(),contentAlignment=Alignment.Center) { CircularProgressIndicator() }
                else {
                    val p=project!!
                    Column(Modifier.fillMaxSize().padding(horizontal=if(tablet) 28.dp else 16.dp)) {
                        Row(Modifier.fillMaxWidth().padding(vertical=12.dp),verticalAlignment=Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(p.name,style=MaterialTheme.typography.headlineSmall,fontWeight=FontWeight.Bold)
                                Text("${sources.size} kaynak · Belgelerin aygıtında kalır",style=MaterialTheme.typography.bodyMedium,
                                    color=MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            IconButton(onClick={rename=true},enabled=!busy) { Icon(Icons.Default.Edit,"Proje adını değiştir") }
                        }
                        FlowRow {
                            TextButton(onClick={model.backup(onSave)},enabled=!busy){Text("Proje yedeği kaydet")}
                            TextButton(onClick={conversions=true},enabled=!busy){Text("Office / OneNote hazırlığı")}
                        }
                        if(importing) {
                            LinearProgressIndicator(Modifier.fillMaxWidth())
                            Row(verticalAlignment=Alignment.CenterVertically) {
                                Text(importMessage,Modifier.weight(1f),style=MaterialTheme.typography.bodySmall)
                                TextButton(onClick={model.cancelImport()}) { Text("İptal") }
                            }
                        }
                        if(tablet) Row(Modifier.weight(1f),horizontalArrangement=Arrangement.spacedBy(24.dp)) {
                            SourcesPanel(sources,!busy,onImport,onFolder,onReplace,model,
                                { s -> val f=Format.valueOf(s.format); if(f==Format.PDF || f==Format.IMAGE) preview=File(s.activePath) to s.name
                                  else onOpen(File(s.originalPath),mime(s.name)) },onOpen,Modifier.weight(1.25f),onEditPagination={editor=it})
                            ExportPanel(p,sources,model,!busy,{ preview=it to it.name },onSave,onShare,onSaveAll,Modifier.weight(1f))
                        } else {
                            var tab by remember { mutableIntStateOf(0) }
                            PrimaryTabRow(selectedTabIndex=tab) {
                                Tab(selected=tab==0,onClick={tab=0},text={Text("Kaynaklar")})
                                Tab(selected=tab==1,onClick={tab=1},text={Text("Derle ve dışa aktar")})
                            }
                            if(tab==0) SourcesPanel(sources,!busy,onImport,onFolder,onReplace,model,
                                { s -> val f=Format.valueOf(s.format); if(f==Format.PDF || f==Format.IMAGE) preview=File(s.activePath) to s.name
                                  else onOpen(File(s.originalPath),mime(s.name)) },onOpen,Modifier.weight(1f),onEditPagination={editor=it})
                            else ExportPanel(p,sources,model,!busy,{preview=it to it.name},onSave,onShare,onSaveAll,Modifier.weight(1f))
                        }
                    }
                }
            }
        }
        if(preferences) AlertDialog(onDismissRequest={preferences=false},title={Text("Görünüm ve gizlilik")},
            text={ Column {
                listOf("Sistem","Açık","Koyu").forEachIndexed { i,t -> Row(Modifier.fillMaxWidth().clickable {model.theme(i)},verticalAlignment=Alignment.CenterVertically) {
                    RadioButton(mode==i,{model.theme(i)}); Text(t)
                } }
                Text("İnternet ve geniş depolama izni kullanılmaz. Dosyalar uygulamanın özel alanına kopyalanır. " +
                    "Yalnızca Paylaş düğmesine bastığında seçtiğin uygulamaya aktarılır. " +
                    "Projeyi silmek uygulamadaki kopyaları siler. Uygulamayı kaldırmadan önce PDF ve raporlarını kaydet.",
                    style=MaterialTheme.typography.bodySmall)
                if(project!=null)TextButton(onClick={model.clearCache()},enabled=!busy){Text("Dönüştürme önbelleğini temizle")}
            } },confirmButton={TextButton(onClick={preferences=false}){Text("Tamam")}})
        if(rename && project!=null) EditDialog("Proje adı",project!!.name,{rename=false}) {model.renameProject(it)}
        if(notice!=null) AlertDialog(onDismissRequest={model.notice.value=null},title={Text("İşlem bilgisi")},
            text={Text(notice!!,Modifier.heightIn(max=450.dp).verticalScroll(rememberScrollState()))},
            confirmButton={TextButton(onClick={model.notice.value=null}){Text("Tamam")}})
        preview?.let { (file,name) -> PreviewDialog(file,name,{preview=null}) }
        editor?.let{s->PaginationEditor(s,SettingsCodec.decode(project?.settings ?: "{}"),{editor=null}){model.pagination(s,it)}}
        if(conversions)ConversionWizard(sources,onReplace,onBulkReplace,{conversions=false})
        if(replacements.isNotEmpty())AlertDialog(onDismissRequest={model.replacements.value=emptyList()},title={Text("PDF eşleştirmelerini onayla")},
            text={Column{Text("PDF'lerin tüm içeriği kapsadığını önizleyerek denetleyin. Orijinaller ve sıraları korunur.");replacements.forEach{Text("${it.source.name} → ${it.name}",style=MaterialTheme.typography.bodySmall)}}},
            confirmButton={TextButton(onClick={model.applyReplacements()}){Text("PDF'leri yerine koy")}},dismissButton={TextButton(onClick={model.replacements.value=emptyList()}){Text("Vazgeç")}})
    }
}

@Composable private fun Home(projects:List<Project>,importing:Boolean,onCreate:()->Unit,onImport:()->Unit,
    onSelect:(String)->Unit,onDelete:(Project)->Unit,onRestore:()->Unit) {
    var deletion by remember { mutableStateOf<Project?>(null) }
    LazyColumn(Modifier.fillMaxSize().widthIn(max=1100.dp).padding(horizontal=24.dp),verticalArrangement=Arrangement.spacedBy(16.dp)) {
        item { Spacer(Modifier.height(20.dp)); Text("Dağınık notlar.\nTek bir çalışma kaynağı.",style=MaterialTheme.typography.headlineLarge,fontWeight=FontWeight.Bold) }
        item { Text("PDF'lerini, el yazısı ekran görüntülerini ve uzun OneNote çıktıları­nı düzenle; okunabilir bir belgede bir araya getir.",
            style=MaterialTheme.typography.bodyLarge,color=MaterialTheme.colorScheme.onSurfaceVariant) }
        item { Card(colors=CardDefaults.cardColors(containerColor=MaterialTheme.colorScheme.primaryContainer),shape=RoundedCornerShape(24.dp)) {
            Column(Modifier.fillMaxWidth().padding(24.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
                Icon(Icons.Default.AutoStories,null,Modifier.size(36.dp)); Text("Bir sonraki sınava hazırla",style=MaterialTheme.typography.titleLarge)
                Text("Sırayı sen belirle. Orijinaller korunsun. Her kaynak raporlansın.")
                FlowRow(horizontalArrangement=Arrangement.spacedBy(10.dp)) {
                    Button(onClick=onCreate,enabled=!importing) {Icon(Icons.Default.Add,null); Spacer(Modifier.width(8.dp));Text("Yeni proje")}
                    OutlinedButton(onClick=onImport,enabled=!importing) {Text("Dosya içe aktar")}
                    OutlinedButton(onClick=onRestore,enabled=!importing){Text("Yedek geri yükle")}
                }
            }
        } }
        item { Text("Son projeler",style=MaterialTheme.typography.titleLarge,fontWeight=FontWeight.SemiBold) }
        if(projects.isEmpty()) item { Text("Henüz proje yok. Dosyalarını seçerek başlayabilirsin.",Modifier.padding(vertical=20.dp)) }
        items(projects,key={it.id}) { p ->
            OutlinedCard(onClick={onSelect(p.id)},enabled=!importing,modifier=Modifier.fillMaxWidth()) {
                Row(Modifier.padding(18.dp),verticalAlignment=Alignment.CenterVertically) {
                    Icon(Icons.Default.Folder,null,tint=MaterialTheme.colorScheme.primary)
                    Column(Modifier.weight(1f).padding(horizontal=16.dp)) {
                        Text(p.name,fontWeight=FontWeight.SemiBold); Text(statusLabel(p.status),style=MaterialTheme.typography.bodySmall)
                    }
                    IconButton(onClick={deletion=p},enabled=!importing && p.status !in setOf("RUNNING","QUEUED")) {Icon(Icons.Default.DeleteOutline,"Projeyi sil")}
                }
            }
        }
        item { Spacer(Modifier.height(24.dp)) }
    }
    deletion?.let { p -> AlertDialog(onDismissRequest={deletion=null},title={Text("Projeyi sil?")},
        text={Text("${p.name} projesinin uygulamadaki orijinalleri ve çıktıları silinir. Başka yere kaydettiğin dosyalar etkilenmez.")},
        confirmButton={TextButton(onClick={onDelete(p);deletion=null}) {Text("Sil")}},dismissButton={TextButton(onClick={deletion=null}) {Text("Vazgeç")}}) }
}

@Composable fun EditDialog(title:String,value:String,onDismiss:()->Unit,onSave:(String)->Unit) {
    var text by remember { mutableStateOf(value) }
    AlertDialog(onDismissRequest=onDismiss,title={Text(title)},text={OutlinedTextField(value=text,onValueChange={text=it},singleLine=true,label={Text("Ad")})},
        confirmButton={TextButton(onClick={onSave(text);onDismiss()}){Text("Kaydet")}},dismissButton={TextButton(onClick=onDismiss){Text("Vazgeç")}})
}
fun mime(name:String):String=MimeTypeMap.getSingleton().getMimeTypeFromExtension(name.substringAfterLast('.').lowercase(java.util.Locale.ROOT)) ?: "application/octet-stream"
fun statusLabel(status:String)=when(status) {
    "READY"->"Düzenlemeye hazır"; "RUNNING"->"İşleniyor"; "QUEUED"->"Sırada"; "DONE"->"PDF hazır"
    "REVIEW"->"Kaynaklar eklendi · görsel inceleme gerekli"; "INCOMPLETE"->"Eksik kaynak var"; "FAILED"->"İşlem başarısız"; "CANCELLED"->"İptal edildi"; else->status
}
fun sizeLabel(bytes:Long)=if(bytes<1024*1024) "%.0f KB".format(bytes/1024.0) else "%.1f MB".format(bytes/1048576.0)
