package org.studyfuse.ui

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.studyfuse.core.*
import org.studyfuse.data.*
import org.json.*
import java.io.File

@Composable
fun ExportPanel(project:Project,sources:List<Source>,model:StudyViewModel,enabled:Boolean,
    onPreview:(File)->Unit,onSave:(File)->Unit,onShare:(List<File>)->Unit,onSaveAll:(List<File>)->Unit,modifier:Modifier=Modifier) {
    val settings=remember(project.settings) { SettingsCodec.decode(project.settings) }
    val work by model.work.collectAsStateWithLifecycle()
    val active=work.firstOrNull { !it.state.isFinished }
    var reportVisible by remember { mutableStateOf(false) }
    var reportText by remember { mutableStateOf("") }
    val outputs=remember(project.output) { runCatching {
        val json=JSONArray(project.output ?: "[]"); (0 until json.length()).map {json.getJSONObject(it)}
    }.getOrDefault(emptyList()) }
    val external=sources.count { !Format.valueOf(it.format).native }
    Column(modifier.verticalScroll(rememberScrollState()).padding(vertical=12.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
        Text("Çalışma belgeni oluştur",style=MaterialTheme.typography.titleLarge,fontWeight=FontWeight.SemiBold)
        Text("Dışa aktarma modu",style=MaterialTheme.typography.labelLarge)
        Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
            FilterChip(selected=settings.mode==ExportMode.AI_STUDY,onClick={model.settings(settings.copy(mode=ExportMode.AI_STUDY))},enabled=enabled,label={Text("AI Çalışma")})
            FilterChip(selected=settings.mode==ExportMode.ARCHIVE,onClick={model.settings(settings.copy(mode=ExportMode.ARCHIVE))},enabled=enabled,label={Text("Arşiv kalitesi")})
        }
        Text(if(settings.mode==ExportMode.AI_STUDY) "İçindekiler, kaynak başlıkları ve sayfa numaraları. ChatGPT'ye paylaşmaya uygun."
            else "Kayıpsız görüntü sıkıştırması önerilir. Mevcut PDF'ler vektör olarak korunur. Sayfa düzeni ayrıca seçilir.",
            style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
        SettingMenu("Sayfa düzeni",when(settings.pageSize) {PageSize.AUTO->"Otomatik A4";PageSize.A4_PORTRAIT->"A4 dikey";PageSize.A4_LANDSCAPE->"A4 yatay";PageSize.ORIGINAL->"Orijinal boyut + numara kenarı"},enabled,
            listOf("Otomatik A4","A4 dikey","A4 yatay","Orijinal boyut + numara kenarı")) { index ->
            model.settings(settings.copy(pageSize=listOf(PageSize.AUTO,PageSize.A4_PORTRAIT,PageSize.A4_LANDSCAPE,PageSize.ORIGINAL)[index]))
        }
        if(settings.pageSize==PageSize.ORIGINAL) Text("Uzun sayfalar bölünmez. AI Çalışma için otomatik A4 önerilir.",color=MaterialTheme.colorScheme.error,style=MaterialTheme.typography.bodySmall)
        SettingMenu("Kenar boşluğu","${settings.margin.toInt()} pt",enabled,listOf("12 pt","24 pt","36 pt","48 pt")) {
            model.settings(settings.copy(margin=listOf(12.0,24.0,36.0,48.0)[it]))
        }
        SettingMenu("Bölme örtüşmesi","${settings.overlap.toInt()} pt",enabled,listOf("0 pt","12 pt","18 pt","24 pt","36 pt")) {
            model.settings(settings.copy(overlap=listOf(0.0,12.0,18.0,24.0,36.0)[it]))
        }
        SettingMenu("Yeni görüntü sayfaları",when(settings.compression) {Compression.LOSSLESS->"Kayıpsız (önerilen)";Compression.JPEG_95->"JPEG %95";Compression.JPEG_90->"JPEG %90"},enabled,
            listOf("Kayıpsız (önerilen)","JPEG %95","JPEG %90")) { model.settings(settings.copy(compression=Compression.entries[it])) }
        Text("PDF içindeki görüntüler yeniden sıkıştırılmaz. Görüntülerin piksel çözünürlüğü korunur; dosya büyük olabilir.",style=MaterialTheme.typography.bodySmall)
        Row(verticalAlignment=Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) { Text("Basılı metin OCR",fontWeight=FontWeight.Medium); Text("Almanca · Türkçe · İngilizce",style=MaterialTheme.typography.bodySmall) }
            Switch(settings.ocr,{model.settings(settings.copy(ocr=it))},enabled=enabled)
        }
        Text("El yazısı ve matematik tanıma desteklenmez. OCR açılırsa doğrulanmamış, aranabilir metin ekleri üretilir; orijinal sayfalar korunur. Kullanılabilir güven puanları JSON raporuna kaydedilir.",style=MaterialTheme.typography.bodySmall)
        SettingMenu("Cilt boyutu sınırı",if(settings.volumeLimitMb==0) "Bölme yok" else "${settings.volumeLimitMb} MB",enabled,
            listOf("Bölme yok","20 MB","50 MB","100 MB","200 MB")) {
            model.settings(settings.copy(volumeLimitMb=listOf(0,20,50,100,200)[it]))
        }
        Text("Kaynak toplamı: ${sizeLabel(sources.sumOf { it.bytes })}. Son boyut dönüştürmeden önce güvenilir biçimde tahmin edilemez. Tek bir sayfa cilt sınırını aşabilir.",style=MaterialTheme.typography.bodySmall)
        if(external>0) Surface(color=MaterialTheme.colorScheme.errorContainer,shape=MaterialTheme.shapes.medium) {
            Text("$external kaynak PDF dönüşümü bekliyor. Şimdi derlersen içerikleri yerine uyarı sayfası eklenir; sonuç EKSİK olur.",Modifier.padding(14.dp),style=MaterialTheme.typography.bodyMedium)
        }
        if(project.status in setOf("RUNNING","QUEUED")) {
            OutlinedCard { Column(Modifier.padding(16.dp),verticalArrangement=Arrangement.spacedBy(10.dp)) {
                Text("Belgeler işleniyor",fontWeight=FontWeight.SemiBold)
                val processed=active?.progress?.getInt("processed",0) ?: 0
                val total=active?.progress?.getInt("total",sources.size) ?: sources.size
                LinearProgressIndicator(progress={if(total>0) processed.toFloat()/total else 0f},modifier=Modifier.fillMaxWidth())
                Text(project.message,style=MaterialTheme.typography.bodySmall)
                Text("$processed / $total dosya · Süre içerik karmaşıklığına bağlıdır",style=MaterialTheme.typography.bodySmall)
                OutlinedButton(onClick={model.cancel()}) {Text("İşlemi iptal et")}
            } }
        } else Button(onClick={model.compile()},enabled=enabled && sources.isNotEmpty(),modifier=Modifier.fillMaxWidth().height(52.dp)) {
            Icon(Icons.Default.Merge,null); Spacer(Modifier.width(8.dp)); Text(if(outputs.isEmpty()) "PDF oluştur" else "Yeniden oluştur")
        }
        if(project.status in setOf("FAILED","CANCELLED")) Text(project.message,color=MaterialTheme.colorScheme.error)
        if(outputs.isNotEmpty()) {
            HorizontalDivider(Modifier.padding(vertical=8.dp))
            Text(if(project.status=="INCOMPLETE") "Eksik çıktı — raporu incele" else "Çalışma belgen hazır",style=MaterialTheme.typography.titleLarge)
            Text(project.message)
            val files=outputs.map {File(it.getString("path"))}
            for(o in outputs) {
                val f=File(o.getString("path"))
                OutlinedCard(Modifier.fillMaxWidth()) { Column(Modifier.padding(14.dp),verticalArrangement=Arrangement.spacedBy(6.dp)) {
                    Text(f.name,fontWeight=FontWeight.Medium)
                    Text("${o.getInt("pages")} sayfa · ${sizeLabel(o.getLong("bytes"))} · ${o.getInt("startPage")}–${o.getInt("endPage")}",style=MaterialTheme.typography.bodySmall)
                    if(settings.volumeLimitMb>0 && o.getLong("bytes")>settings.volumeLimitMb*1024L*1024L)
                        Text("Tek sayfa boyut sınırını aşıyor; okunabilirliği korumak için küçültülmedi.",color=MaterialTheme.colorScheme.error,style=MaterialTheme.typography.bodySmall)
                    Row { TextButton(onClick={onPreview(f)}){Text("Önizle")}; TextButton(onClick={onSave(f)}){Text("PDF kaydet")}; TextButton(onClick={onShare(listOf(f))}){Text("Paylaş")} }
                } }
            }
            if(files.size>1) {
                OutlinedButton(onClick={onSaveAll(files)},modifier=Modifier.fillMaxWidth()){Text("Tüm ciltleri klasöre kaydet")}
                OutlinedButton(onClick={onShare(files)},modifier=Modifier.fillMaxWidth()){Text("Tüm ciltleri paylaş")}
            }
            project.report?.let { path ->
                val report=File(path); val txt=File(report.parentFile,"StudyFuse-rapor.txt")
                TextButton(onClick={reportVisible=true}) { Icon(Icons.Default.FactCheck,null);Spacer(Modifier.width(6.dp));Text("Bütünlük raporunu incele") }
                Row {TextButton(onClick={onSave(report)}){Text("JSON raporu kaydet")};TextButton(onClick={onSave(txt)}){Text("Metin raporu kaydet")}}
                LaunchedEffect(path) { reportText=kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { runCatching {txt.readText()}.getOrDefault("Rapor okunamadı") } }
            }
            Text("ChatGPT'ye paylaştıktan sonra her sayfayı incelemesini iste. Yükleme ve analiz sınırları seçtiğin hizmete bağlıdır.",style=MaterialTheme.typography.bodySmall)
        }
        Spacer(Modifier.height(24.dp))
    }
    if(reportVisible) AlertDialog(onDismissRequest={reportVisible=false},title={Text("Bütünlük raporu")},
        text={Text(reportText,Modifier.heightIn(max=500.dp).verticalScroll(rememberScrollState()),style=MaterialTheme.typography.bodySmall)},
        confirmButton={TextButton(onClick={reportVisible=false}){Text("Kapat")}})
}

@Composable private fun SettingMenu(label:String,value:String,enabled:Boolean,values:List<String>,onSelect:(Int)->Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box(Modifier.fillMaxWidth()) {
        OutlinedButton(onClick={expanded=true},enabled=enabled,modifier=Modifier.fillMaxWidth(),shape=MaterialTheme.shapes.medium) {
            Column(Modifier.weight(1f),horizontalAlignment=Alignment.Start) {Text(label,style=MaterialTheme.typography.labelSmall);Text(value)}
            Icon(Icons.Default.ArrowDropDown,null)
        }
        DropdownMenu(expanded,onDismissRequest={expanded=false}) {values.forEachIndexed {i,s -> DropdownMenuItem(text={Text(s)},onClick={expanded=false;onSelect(i)})} }
    }
}
