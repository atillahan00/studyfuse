package org.studyfuse.ui

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.*
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.*
import org.json.*
import org.studyfuse.core.*
import org.studyfuse.data.*
import org.studyfuse.learning.*
import java.io.File
import java.time.LocalDate

private data class PracticeItem(val item:JSONObject,val unit:JSONObject)

@OptIn(ExperimentalMaterial3Api::class)
@Composable fun ExamWorkshop(project:Project,sources:List<Source>,model:StudyViewModel,onImportModel:()->Unit,
    onSave:(File)->Unit,onShare:(List<File>)->Unit,onDismiss:()->Unit) {
    val context=LocalContext.current;val scope=rememberCoroutineScope()
    val store=remember(project.id){ExamStore(model.repo.directory(project.id))}
    var config by remember(project.id){mutableStateOf(ExamConfig())}
    var loaded by remember{mutableStateOf(false)}
    var tab by remember{mutableIntStateOf(0)}
    var error by remember{mutableStateOf<String?>(null)}
    var selectedModel by remember{mutableStateOf<LocalModel?>(null)}
    var session by remember{mutableStateOf<JSONObject?>(null)}
    var units by remember{mutableStateOf<List<JSONObject>>(emptyList())}
    var review by remember{mutableStateOf(JSONObject())}
    var ready by remember{mutableStateOf(false)}
    var refresh by remember{mutableIntStateOf(0)}
    var signature by remember{mutableStateOf<String?>(null)}
    var unitIndex by remember{mutableIntStateOf(0)}
    var preview by remember{mutableStateOf<Pair<File,Int>?>(null)}
    var exporting by remember{mutableStateOf(false)}
    var edited by remember{mutableStateOf<JSONObject?>(null)}
    val revision by model.examRevision.collectAsStateWithLifecycle()
    val workers by model.examWork.collectAsStateWithLifecycle()
    val modelBusy by model.modelBusy.collectAsStateWithLifecycle()
    val modelMessage by model.modelMessage.collectAsStateWithLifecycle()
    val active=workers.firstOrNull{!it.state.isFinished}
    val workerError=workers.firstOrNull{it.id.toString()==project.workId}?.outputData?.getString("error")
    val busy=project.status in setOf("QUEUED","RUNNING") || modelBusy || exporting
    LaunchedEffect(project.id){try{config=withContext(Dispatchers.IO){store.config()};loaded=true}catch(e:Exception){error=e.message}}
    LaunchedEffect(loaded,config,revision,workers,refresh,project.report) {
        if(!loaded)return@LaunchedEffect
        try {
            withContext(Dispatchers.IO){
                selectedModel=ModelStore(context).selected();ready=visionReady(context,config.gpu)
                signature=project.report?.let{path->selectedModel?.let{local->store.signature(File(path),local,config)}}
                session=store.session()?.takeIf{it.optString("signature")==signature}
                units=signature?.let{store.currentUnits(it)} ?: emptyList();review=store.review()
            }
        }catch(e:Exception){error=e.message;session=null;units=emptyList()}
    }
    fun sourcePreview(unit:JSONObject){
        scope.launch {try {
            val result=withContext(Dispatchers.IO){val report=readJson(File(project.report ?: error("PDF derlemesi gerekli")))
                val page=unit.getInt("page");val out=report.getJSONArray("outputs").objects().first{page in it.getInt("startPage")..it.getInt("endPage")}
                File(out.getString("path")) to (page-out.getInt("startPage"))}
            preview=result
        }catch(e:Exception){error=e.message}}
    }
    fun update(id:String,change:(JSONObject)->Unit){scope.launch{try{withContext(Dispatchers.IO){store.updateReview(id,change)};refresh++}catch(e:Exception){error=e.message}}}
    fun effective(item:JSONObject)=JSONObject(item.toString()).apply{review.optJSONObject(item.getString("id"))?.let{r->
        if(r.has("question"))put("question",r.getString("question"));if(r.has("answer"))put("answer",r.getString("answer"))}}
    fun export(share:Boolean){val snapshot=session ?: return
        exporting=true;scope.launch{try{
            val files=withContext(Dispatchers.IO){
                val corrected=units.map{u->JSONObject(u.toString()).apply{put("items",JSONArray(u.optJSONArray("items")?.objects().orEmpty().map(::effective)))}}
                ExamExport.write(context,store,snapshot,corrected,project.report?.let(::File))
            }
            if(share){val originals=project.output?.let{JSONArray(it).objects().map{row->File(row.getString("path"))}}.orEmpty();onShare(originals+files)}else onSave(files.first())
        }catch(e:Exception){error=e.message}finally{exporting=false}}
    }
    Dialog(onDismissRequest=onDismiss,properties=DialogProperties(usePlatformDefaultWidth=false)) {
        Surface(Modifier.fillMaxWidth(.98f).fillMaxHeight(.97f),shape=MaterialTheme.shapes.large){Column {
            Row(Modifier.fillMaxWidth().padding(12.dp),verticalAlignment=Alignment.CenterVertically){
                Text("Sınav atölyesi · Yerel AI",Modifier.weight(1f),style=MaterialTheme.typography.titleLarge)
                TextButton(onClick=onDismiss){Text("Kapat")}
            }
            PrimaryTabRow(selectedTabIndex=tab){listOf("Hazırla","Notlar","Hatırla","Kapsam").forEachIndexed{i,label->Tab(selected=tab==i,onClick={tab=i},text={Text(label)})}}
            if(error!=null)Row(Modifier.padding(10.dp)){Text(error!!,Modifier.weight(1f),color=MaterialTheme.colorScheme.error);TextButton(onClick={error=null}){Text("Kapat")}}
            if(workerError!=null)Text("AI tamamlanamadı: $workerError",Modifier.padding(12.dp),color=MaterialTheme.colorScheme.error)
            if(active!=null){Column(Modifier.padding(12.dp)){
                val done=active.progress.getInt("processed",0);val total=active.progress.getInt("total",0)
                LinearProgressIndicator(progress={if(total>0)done.toFloat()/total else 0f},modifier=Modifier.fillMaxWidth())
                Text("$done/$total · ${active.progress.getString("message") ?: project.message}",style=MaterialTheme.typography.bodySmall)
                TextButton(onClick={model.cancelExam()}){Text("AI işlemini iptal et")}
            }}
            when(tab) {
                0->Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(16.dp),verticalArrangement=Arrangement.spacedBy(12.dp)){
                    Text("Belgelerin tek başına yeterli. Sınav konusu ve tarihi isteğe bağlıdır. El yazısı/grafik yorumları ve AI çözümleri kontrol edilmeden güvenilir kabul edilmez.")
                    Text("1 · Yerel görüntü modeli",style=MaterialTheme.typography.titleMedium)
                    Text(selectedModel?.let{"${it.name} · ${sizeLabel(it.bytes)} · SHA-256 ${it.digest.take(16)}…"} ?: "Henüz model yok. Model APK'ya dahil değildir.")
                    Text("MagicPad 2 için başlangıç adayı: görüntü destekli Gemma 4 E4B .litertlm; daha hafif seçenek E2B. GGUF/.task dosyaları desteklenmez. Aygıtın GPU sürücüsü ayrıca sınanmalıdır.",style=MaterialTheme.typography.bodySmall)
                    FlowRow {
                        OutlinedButton(onClick=onImportModel,enabled=!busy){Text("Model içe aktar")}
                        TextButton(onClick={runCatching{context.startActivity(Intent(Intent.ACTION_VIEW,Uri.parse("https://huggingface.co/litert-community/gemma-4-E4B-it-litert-lm")))}.onFailure{error="Tarayıcı açılamadı"}}){Text("Model indirme sayfası")}
                    }
                    Row(verticalAlignment=Alignment.CenterVertically){Switch(config.gpu,{config=config.copy(gpu=it)},enabled=!busy);Text("Dil modeli GPU hızlandırması")}
                    Text("Kapalıyken dil modeli CPU'da çalışır; görüntü kodlayıcı GPU gerektirir. NPU hızlandırması vaat edilmez. İlk indirme/kopyalama sonrası tüm analiz çevrimdışıdır.",style=MaterialTheme.typography.bodySmall)
                    OutlinedButton(onClick={model.testLocalModel(config.gpu)},enabled=selectedModel!=null && !busy){Text("Görüntü girişini sına")}
                    if(modelMessage.isNotBlank())Text(modelMessage,style=MaterialTheme.typography.bodySmall)
                    if(modelBusy)TextButton(onClick={model.cancelModelSetup()}){Text("Model işlemini iptal et")}
                    Text(if(ready)"Görüntü testi geçti; gerçek el yazısı doğruluğu ayrıca incelenmeli." else "Analizden önce seçili model/aygıt görüntü testini geçmeli.",style=MaterialTheme.typography.bodySmall)
                    HorizontalDivider();Text("2 · Kaynak politikası",style=MaterialTheme.typography.titleMedium)
                    Row(verticalAlignment=Alignment.CenterVertically){
                        Switch(config.mode==KnowledgeMode.ENRICHED,{config=config.copy(mode=if(it)KnowledgeMode.ENRICHED else KnowledgeMode.DOCUMENTS_ONLY)},enabled=!busy)
                        Text(if(config.mode==KnowledgeMode.DOCUMENTS_ONLY)"Yalnızca belgelerim" else "Ek kaynaklarla öğren")
                    }
                    Text(if(config.mode==KnowledgeMode.DOCUMENTS_ONLY)"Yalnızca ana belgelerde desteklenen içerik istenir. Ek bilgiler reddedilir; kanıt alıntıları denetlenir. Bu, AI hatalarını tamamen engellemez."
                        else "Ek başvuru belgeleri ve modelin önceden öğrendiği bilgiler kullanılabilir. Model ekleri ayrı etiketlenir; web araştırması yapılmaz.",style=MaterialTheme.typography.bodySmall)
                    Text("Ek başvuru kaynakları (ana ders belgesi olarak kullanılmaz):",style=MaterialTheme.typography.labelLarge)
                    sources.forEach{s->Row(verticalAlignment=Alignment.CenterVertically){Checkbox(s.sha256 in config.references,{checked->config=config.copy(references=if(checked)config.references+s.sha256 else config.references-s.sha256)},enabled=!busy);Text(s.name,style=MaterialTheme.typography.bodySmall)}}
                    Text("Aynı baytları içeren kopyalar aynı kaynak rolünü paylaşır.",style=MaterialTheme.typography.bodySmall)
                    FlowRow{listOf("Deutsch","Türkçe","English").forEach{lang->FilterChip(config.language==lang,{config=config.copy(language=lang)},enabled=!busy,label={Text(lang)})}}
                    OutlinedTextField(config.focus,{config=config.copy(focus=it)},label={Text("Sınav kapsamı / öğretmen beklentisi (isteğe bağlı)")},enabled=!busy,modifier=Modifier.fillMaxWidth())
                    OutlinedTextField(config.examDate,{config=config.copy(examDate=it)},label={Text("Sınav tarihi: YYYY-MM-DD (isteğe bağlı)")},enabled=!busy,singleLine=true)
                    Row(verticalAlignment=Alignment.CenterVertically){Switch(config.detailed,{config=config.copy(detailed=it)},enabled=!busy);Text("Ayrıntılı görsel okuma: tam sayfa + 4 örtüşen bölge")}
                    Text("Açıkken küçük yazılar için daha fazla inceleme yapılır; işlem daha uzun sürer. Büyük partiler ısı/batarya nedeniyle yavaşlayabilir. İptalde tamamlanan bölgeler korunur.",style=MaterialTheme.typography.bodySmall)
                    TextButton(onClick={scope.launch{try{withContext(Dispatchers.IO){store.saveConfig(config)};refresh++}catch(e:Exception){error="Tercihler kaydedilemedi: ${e.message}"}}},enabled=!busy){Text("Tercihleri kaydet")}
                    Button(onClick={try{config.validate();model.startExam(config)}catch(e:Exception){error="Ayarları kontrol edin: ${e.message}"}},enabled=project.report!=null && ready && !busy,modifier=Modifier.fillMaxWidth()){Text("Sınav hazırlığını oluştur / sürdür")}
                    if(project.report==null)Text("Önce ana ekranda PDF oluşturun. Desteklenmeyen Office/OneNote kaynakları için PDF yerine koyun.")
                    if(session==null)Text("Bu model, kaynak politikası ve ayarlar için güncel taslak yok. Ayar değişikliğinde eski içerik gösterilmez.",style=MaterialTheme.typography.bodySmall)
                }
                1->{
                    if(units.isEmpty())EmptyExam(Modifier.weight(1f))else{
                        val unit=units[unitIndex.coerceIn(0,units.lastIndex)]
                        LazyColumn(Modifier.weight(1f).padding(16.dp),verticalArrangement=Arrangement.spacedBy(10.dp)){
                            item{Row(verticalAlignment=Alignment.CenterVertically){TextButton(onClick={unitIndex--},enabled=unitIndex>0){Text("Önceki")};Text("${unitIndex+1}/${units.size}");TextButton(onClick={unitIndex++},enabled=unitIndex<units.lastIndex){Text("Sonraki")}}}
                            item{Text("${unit.getString("section")} · PDF ${unit.getInt("page")} · bölge ${unit.getInt("region")}",style=MaterialTheme.typography.titleMedium)
                                Text(unit.getString("sourceName")+if(unit.optBoolean("reference"))" · EK BAŞVURU" else "")
                                OutlinedButton(onClick={sourcePreview(unit)}){Text("Kaynak sayfayı incele")}
                            }
                            if(unit.optString("status")=="FAILED")item{Text("Okunamadı: ${unit.optString("error")}",color=MaterialTheme.colorScheme.error)}
                            else {
                                item{project.report?.let{StudyEvidencePane(File(it),unit)}}
                                item{Text("Görsel transkripsiyon (AI; doğrulanmamış)",style=MaterialTheme.typography.labelLarge);SelectionContainerText(unit.getString("transcription"));Text(unit.getString("observations"));Text("Belirsizlikler: ${unit.getJSONArray("uncertainties")}")}
                                items(unit.getJSONArray("items").objects(),key={it.getString("id")}){original->val item=effective(original);val id=item.getString("id");val state=review.optJSONObject(id)
                                    OutlinedCard(Modifier.fillMaxWidth()){Column(Modifier.padding(12.dp),verticalArrangement=Arrangement.spacedBy(8.dp)){
                                        Text(if(item.getString("origin")=="ai_added")"MODEL EK BİLGİSİ · kaynak atfı yok" else "KAYNAK · ${item.getString("basis")}",style=MaterialTheme.typography.labelSmall)
                                        Text("${item.getString("kind")} · ${item.getString("question")}",style=MaterialTheme.typography.titleSmall)
                                        SelectionContainerText(item.getString("answer"));Text("Kanıt: ${item.getString("evidence")}",style=MaterialTheme.typography.bodySmall)
                                        if(state?.has("answer")==true)Text("Öğrenci tarafından düzenlendi",style=MaterialTheme.typography.labelSmall)
                                        Row(verticalAlignment=Alignment.CenterVertically){Checkbox(state?.optBoolean("approved")==true,{checked->update(id){it.put("approved",checked)}},enabled=!busy);Text("Kaynağı ve yanıtı inceledim",style=MaterialTheme.typography.bodySmall)}
                                        TextButton(onClick={edited=item},enabled=!busy){Text("Soru / yanıtı düzelt")}
                                    }}
                                }
                                item{Text("Reddedilen maddeler: ${unit.getJSONArray("rejected").length()}. ${unit.getJSONArray("rejected").objects().joinToString {it.getString("reason")}}",style=MaterialTheme.typography.bodySmall)}
                            }
                            item{Text("İncelenmemiş maddeler tekrar oturumuna alınmaz. Notlar ve çözümler AI taslağıdır; kaynak alıntısı mantıksal doğruluğu kanıtlamaz.",style=MaterialTheme.typography.bodySmall)
                                FlowRow{TextButton(onClick={export(false)},enabled=!busy){Text("Sınav notlarını PDF kaydet")};TextButton(onClick={export(true)},enabled=!busy){Text("Kaynak PDF ve çalışma paketini paylaş")}}
                                TextButton(onClick={scope.launch{try{val corrected=units.map{u->JSONObject(u.toString()).apply{put("items",JSONArray(u.optJSONArray("items")?.objects().orEmpty().map(::effective)))}}
                                    val files=withContext(Dispatchers.IO){ExamExport.write(context,store,session!!,corrected,project.report?.let(::File))};onSave(files.last())}catch(e:Exception){error=e.message}}},enabled=!busy){Text("Markdown kaydet")}
                            }
                        }
                    }
                }
                2->{
                    var includeExtras by remember{mutableStateOf(false)}
                    val today=LocalDate.now()
                    val all=units.flatMap{unit->unit.optJSONArray("items")?.objects().orEmpty().map{PracticeItem(effective(it),unit)}}
                        .filter{it.item.getString("kind")!="note" && review.optJSONObject(it.item.getString("id"))?.optBoolean("approved")==true}
                        .distinctBy{it.unit.getString("sourceName")+it.unit.getInt("page")+EvidenceRules.normalize(it.item.getString("question"))+EvidenceRules.normalize(it.item.getString("answer"))}
                    val due=RecallSchedule.interleave(all.filter{(includeExtras || (it.item.getString("origin")=="document" && !it.unit.optBoolean("reference"))) &&
                        (review.optJSONObject(it.item.getString("id"))?.optLong("dueDay",0) ?:0)<=today.toEpochDay()}
                        .sortedBy{review.optJSONObject(it.item.getString("id"))?.optLong("dueDay",0) ?:0}){it.unit.getString("section")}
                    val current=due.firstOrNull()
                    var attempt by remember(current?.item?.optString("id")){mutableStateOf("")}
                    var revealed by remember(current?.item?.optString("id")){mutableStateOf(false)}
                    var correction by remember(current?.item?.optString("id")){mutableStateOf("")}
                    Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(16.dp),verticalArrangement=Arrangement.spacedBy(12.dp)){
                        Text("Aktif hatırlama · karışık konular · aralıklı tekrar",style=MaterialTheme.typography.titleMedium)
                        Text("Yanıtı görmeden dene, sonra kaynakla karşılaştır. Değerlendirme sana aittir; otomatik doğru/yanlış puanı veya sınava hazır olma garantisi verilmez.")
                        if(config.mode==KnowledgeMode.ENRICHED)Row(verticalAlignment=Alignment.CenterVertically){Switch(includeExtras,{includeExtras=it});Text("Ek bilgi sorularını dahil et")}
                        Text("Bugün ${due.size} tekrar · ${all.size} incelenmiş soru")
                        if(current==null)Text("Bugün için soru yok. Önce Notlar bölümündeki kaynak/yanıtları inceleyip onaylayın.")else{
                            Text("${current.unit.getString("section")} · PDF ${current.unit.getInt("page")}",style=MaterialTheme.typography.labelLarge)
                            Text(current.item.getString("question"),style=MaterialTheme.typography.titleLarge)
                            OutlinedTextField(attempt,{attempt=it},label={Text("Kaynağı kapat; yanıtını yaz")},modifier=Modifier.fillMaxWidth())
                            Button(onClick={revealed=true},enabled=attempt.isNotBlank()){Text("Yanıtı aç")}
                            if(revealed){SelectionContainerText(current.item.getString("answer"));Text("Kanıt: ${current.item.getString("evidence")}")
                                TextButton(onClick={sourcePreview(current.unit)}){Text("Kaynakla karşılaştır")}
                                OutlinedTextField(correction,{correction=it},label={Text("Hatanın nedeni / düzeltme (isteğe bağlı)")},modifier=Modifier.fillMaxWidth())
                                FlowRow{listOf("Tekrar" to RecallGrade.AGAIN,"Zor" to RecallGrade.HARD,"Hatırladım" to RecallGrade.GOOD).forEach{(label,grade)->OutlinedButton(onClick={
                                    update(current.item.getString("id")){j->
                                        val p=RecallSchedule.assess(RecallProgress(j.optInt("stage",0),j.optLong("dueDay",0),j.optInt("attempts",0),j.optInt("lapses",0)),grade,today,runCatching{LocalDate.parse(config.examDate)}.getOrNull())
                                        j.put("stage",p.stage).put("dueDay",p.dueDay).put("attempts",p.attempts).put("lapses",p.lapses).put("lastAttempt",attempt)
                                        if(correction.isNotBlank())j.put("correction",correction)
                                        val history=j.optJSONArray("history") ?: JSONArray();history.put(JSONObject().put("date",today.toString()).put("grade",grade.name).put("attempt",attempt).put("correction",correction));j.put("history",history)
                                    };attempt="";revealed=false;correction=""
                                },enabled=!busy){Text(label)}}}
                            }
                        }
                        HorizontalDivider();Text("Hata defteri",style=MaterialTheme.typography.titleMedium)
                        all.filter{(review.optJSONObject(it.item.getString("id"))?.optInt("lapses",0) ?:0)>0}.forEach{card->
                            val state=review.getJSONObject(card.item.getString("id"));Text("${card.item.getString("question")} · ${state.optInt("lapses")} tekrar hatası\n${state.optString("correction")}\nSonraki tekrar: ${LocalDate.ofEpochDay(state.optLong("dueDay",today.toEpochDay()))}",style=MaterialTheme.typography.bodySmall)
                        }
                    }
                }
                else->LazyColumn(Modifier.weight(1f).padding(16.dp),verticalArrangement=Arrangement.spacedBy(10.dp)){
                    item{Text("Kapsam kaydı",style=MaterialTheme.typography.titleLarge);Text("${units.size}/${session?.optInt("total",0) ?:0} bölge kaydı · ${units.count{it.optString("status")=="FAILED"}} başarısız. Bölge işlendi demek her çizginin doğru anlaşıldığı anlamına gelmez.")}
                    if(session==null)item{Text("Güncel çalışma taslağı yok; önce Hazırla bölümünden oluşturun.")}
                    session?.getJSONArray("files")?.objects()?.forEach{row->item{Text("${row.getString("fileName")} · ${row.getString("status")} · ${row.getInt("visualPages")} kaynak görsel sayfa",color=if(row.getString("status") in setOf("FAILED","FALLBACK"))MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface)}}
                    items(units){u->OutlinedCard(onClick={unitIndex=units.indexOf(u);tab=1},modifier=Modifier.fillMaxWidth()){Column(Modifier.padding(10.dp)){
                        Text("${u.getString("sourceName")} · PDF ${u.getInt("page")} · bölge ${u.getInt("region")} · ${u.optString("status")}")
                        Text(if(u.optString("status")=="FAILED")u.optString("error") else "Belirsizlik: ${u.getJSONArray("uncertainties")} · Reddedilen: ${u.getJSONArray("rejected").length()}",style=MaterialTheme.typography.bodySmall)
                    }}}
                }
            }
        }}
    }
    preview?.let{(file,page)->PreviewDialog(file,"Kaynak PDF · ${page+1}. sayfa",{preview=null},page)}
    edited?.let{item->var question by remember(item){mutableStateOf(item.getString("question"))};var answer by remember(item){mutableStateOf(item.getString("answer"))}
        AlertDialog(onDismissRequest={edited=null},title={Text("Soru / yanıtı düzelt")},text={Column{
            Text("Düzenleme onayı kaldırır ve tekrar aralığını sıfırlar. Kaynak alıntısı korunur; yeni yanıtı kaynakla yeniden kontrol edin.",style=MaterialTheme.typography.bodySmall)
            OutlinedTextField(question,{question=it},label={Text("Soru / başlık")});OutlinedTextField(answer,{answer=it},label={Text("Yanıt")},modifier=Modifier.heightIn(max=300.dp))
        }},confirmButton={TextButton(onClick={update(item.getString("id")){it.put("question",question).put("answer",answer).put("approved",false).put("stage",0).put("dueDay",0)};edited=null},enabled=question.isNotBlank() && answer.isNotBlank()){Text("Kaydet")}},dismissButton={TextButton(onClick={edited=null}){Text("Vazgeç")}})
    }
}
@Composable private fun EmptyExam(modifier:Modifier)=Box(modifier.fillMaxWidth().padding(24.dp)){Text("Güncel çalışma maddesi yok. Hazırla bölümünden modelini sınayıp belgelerini analiz et. Eksik/okunamayan sayfalar Kapsam bölümünde görünür.")}
@Composable private fun SelectionContainerText(text:String){androidx.compose.foundation.text.selection.SelectionContainer{Text(text)}}

@Composable private fun StudyEvidencePane(report:File,unit:JSONObject) {
    var source by remember(unit.optString("key")){mutableStateOf<Triple<File,Int,Slice>?>(null)}
    var failure by remember(unit.optString("key")){mutableStateOf<String?>(null)}
    LaunchedEffect(report,unit.optString("key")){
        try {source=withContext(Dispatchers.IO){
            val page=unit.getInt("page");val row=readJson(report).getJSONArray("outputs").objects().first{page in it.getInt("startPage")..it.getInt("endPage")}
            val file=File(row.getString("path"));val local=page-row.getInt("startPage");val bounds=unit.getJSONArray("bounds")
            org.studyfuse.engine.loadPdf(file,report.parentFile!!).use{doc->val box=doc.getPage(local).cropBox
                Triple(file,local,Slice(bounds.getDouble(0)*box.width,bounds.getDouble(1)*box.height,bounds.getDouble(2)*box.width,bounds.getDouble(3)*box.height))}
        }}catch(e:Exception){failure=e.message}
    }
    source?.let{(file,page,area)->DocumentPane(file,page,Modifier.fillMaxWidth().height(360.dp),area)}
    failure?.let{Text("Kaynak bölgesi açılamadı: $it",color=MaterialTheme.colorScheme.error)}
}
