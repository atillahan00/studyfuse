package org.studyfuse.ui

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.*
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.*
import org.studyfuse.core.*
import org.studyfuse.data.Project
import org.studyfuse.learning.*
import java.io.File
import java.time.LocalDate

private data class HubSnapshot(val config:ExamConfig,val signature:String?,val units:List<JSONObject>,val review:JSONObject,
    val cards:List<JSONObject>,val history:List<JSONObject>,val mock:JSONObject?)

@OptIn(ExperimentalMaterial3Api::class)
@Composable fun StudyHub(project:Project,model:StudyViewModel,onSave:(File)->Unit,onPrepare:()->Unit,onDismiss:()->Unit) {
    val scope=rememberCoroutineScope();val store=remember(project.id){ExamStore(model.repo.directory(project.id))};val tools=remember(store){StudyTools(store)}
    var config by remember{mutableStateOf(ExamConfig())};var signature by remember{mutableStateOf<String?>(null)}
    var units by remember{mutableStateOf<List<JSONObject>>(emptyList())};var review by remember{mutableStateOf(JSONObject())}
    var cards by remember{mutableStateOf<List<JSONObject>>(emptyList())};var history by remember{mutableStateOf<List<JSONObject>>(emptyList())}
    var mock by remember{mutableStateOf<JSONObject?>(null)};var error by remember{mutableStateOf<String?>(null)};var refresh by remember{mutableIntStateOf(0)}
    var tab by remember{mutableIntStateOf(0)};var topic by remember{mutableStateOf("")};var extra by remember{mutableStateOf(false)}
    var preview by remember{mutableStateOf<Pair<File,Int>?>(null)}
    val persistence=remember{Mutex()};var saving by remember{mutableIntStateOf(0)}
    val workers by model.examWork.collectAsStateWithLifecycle();val revision by model.examRevision.collectAsStateWithLifecycle()
    val busy=project.status in setOf("RUNNING","QUEUED")
    val workerError=workers.firstOrNull{it.id.toString()==project.workId}?.outputData?.getString("error")
    LaunchedEffect(project.report,workers,revision,refresh){
        try{
            val state=withContext(Dispatchers.IO){val cfg=store.config();val local=ModelStore(model.repo.context).selected()
                val sig=if(local!=null && project.report!=null)store.signature(File(project.report),local,cfg)else null
                val readings=sig?.let{store.currentUnits(it)} ?: emptyList();val reviews=store.review()
                HubSnapshot(cfg,sig,readings,reviews,sig?.let{tools.cards(it,readings,reviews)} ?: emptyList(),
                    tools.data("coach.json").optJSONArray("entries")?.objects().orEmpty().filter{it.getString("signature")==sig},tools.data("mock.json").takeIf{it.optString("signature")==sig})
            }
            config=state.config;signature=state.signature;units=state.units;review=state.review;cards=state.cards;history=state.history;mock=state.mock
        }catch(e:CancellationException){throw e}catch(e:Exception){error=e.message;signature=null;units=emptyList();cards=emptyList();history=emptyList();mock=null}
    }
    fun action(reload:Boolean=true,block:()->Unit){saving++;scope.launch{try{persistence.withLock{withContext(Dispatchers.IO){block()}};if(reload)refresh++}catch(e:Exception){error=e.message}finally{saving--}}}
    fun openSource(page:Int){scope.launch{try{preview=withContext(Dispatchers.IO){val row=readJson(File(project.report!!)).getJSONArray("outputs").objects().first{page in it.getInt("startPage")..it.getInt("endPage")};File(row.getString("path")) to (page-row.getInt("startPage"))}}catch(e:Exception){error=e.message}}}
    val scoped=cards.filter{(topic.isBlank() || it.getString("section")==topic) && (extra || (it.getString("origin")=="document" && !it.optBoolean("reference")))}
    val approved=scoped.filter{review.optJSONObject(it.getString("id"))?.optBoolean("approved")==true}
    Dialog(onDismissRequest={if(saving==0)onDismiss()},properties=DialogProperties(usePlatformDefaultWidth=false)){
        Surface(Modifier.fillMaxWidth(.98f).fillMaxHeight(.97f),shape=MaterialTheme.shapes.large){Column{
            Row(Modifier.padding(12.dp),verticalAlignment=Alignment.CenterVertically){Text("AI öğrenme merkezi",Modifier.weight(1f),style=MaterialTheme.typography.titleLarge);if(saving>0)Text("Kaydediliyor…");TextButton(onClick=onDismiss,enabled=saving==0){Text("Kapat")}}
            Text("${if(config.mode==KnowledgeMode.DOCUMENTS_ONLY)"Yalnızca belgelerim" else "Ek kaynaklarla öğren"} · ${config.language} · AI taslakları kaynakla kontrol edilmelidir",Modifier.padding(horizontal=16.dp),style=MaterialTheme.typography.bodySmall)
            PrimaryTabRow(selectedTabIndex=tab){listOf("Koç","Kartlar","Deneme","Plan").forEachIndexed{i,t->Tab(tab==i,{tab=i},text={Text(t)})}}
            if(error!=null || workerError!=null)Text(error ?: workerError!!,Modifier.padding(12.dp),color=MaterialTheme.colorScheme.error)
            if(busy)Row(verticalAlignment=Alignment.CenterVertically){LinearProgressIndicator(Modifier.weight(1f));TextButton(onClick={model.cancelExam()}){Text("İptal")}}
            if(units.isEmpty())Column(Modifier.padding(16.dp)){Text("Önce kaynak PDF'yi derleyip Sınav atölyesi'nde görsel okumayı oluşturun. Bu merkez o kaynak kayıtlarını kullanır.");Button(onClick=onPrepare){Text("Kurulum ve sınav atölyesi")}}
            else{
                LazyRow(Modifier.padding(horizontal=12.dp),horizontalArrangement=Arrangement.spacedBy(6.dp)){
                    item{FilterChip(topic.isBlank(),{topic=""},label={Text("Tüm konular")})}
                    items(units.map{it.getString("section")}.distinct()){section->FilterChip(topic==section,{topic=section},label={Text(section)})}
                }
                if(config.mode==KnowledgeMode.ENRICHED && tab!=0)Row(verticalAlignment=Alignment.CenterVertically){Switch(extra,{extra=it});Text("Ek bağlam sorularını dahil et")}
                when(tab){
                    0->{var task by remember{mutableStateOf(CoachTask.QUESTION)};var query by remember{mutableStateOf("")};var attempt by remember{mutableStateOf("")}
                        LazyColumn(Modifier.weight(1f).padding(16.dp),verticalArrangement=Arrangement.spacedBy(10.dp)){
                            item{Text("Kaynaklı çalışma koçu",style=MaterialTheme.typography.titleMedium);Text("Her istek ilgili kaynak alıntılarıyla yanıtlanır. Bu, tüm belgelerin eksiksiz anlaşıldığı veya yanıtın doğrulandığı anlamına gelmez. Bir konu/terim yazın; belirsiz el yazısını kaynak görselinde kontrol edin.",style=MaterialTheme.typography.bodySmall)
                                FlowRow(horizontalArrangement=Arrangement.spacedBy(6.dp)){CoachTask.entries.forEach{t->FilterChip(task==t,{task=t},enabled=!busy,label={Text(t.title)})}}
                                OutlinedTextField(query,{query=it.take(1000)},label={Text("Konu veya sorun (en fazla 1000 karakter)")},modifier=Modifier.fillMaxWidth(),enabled=!busy)
                                if(task==CoachTask.FEEDBACK)OutlinedTextField(attempt,{attempt=it.take(3000)},label={Text("Önce kendi yanıtını yaz")},modifier=Modifier.fillMaxWidth(),enabled=!busy)
                                Button(onClick={model.askCoach(task,query,attempt,topic,signature!!)},enabled=!busy && signature!=null && query.isNotBlank() && (task!=CoachTask.FEEDBACK || attempt.isNotBlank())){Text("Yerel AI ile çalış")}
                            }
                            items(history.asReversed()){entry->val result=entry.getJSONObject("result");OutlinedCard(Modifier.fillMaxWidth()){Column(Modifier.padding(12.dp),verticalArrangement=Arrangement.spacedBy(8.dp)){
                                Text(entry.getString("query"),style=MaterialTheme.typography.titleMedium)
                                Text(result.getString("answer"));if(result.getString("additional").isNotBlank()){Text("MODEL EK BİLGİSİ · belge kanıtı değildir",color=MaterialTheme.colorScheme.primary);Text(result.getString("additional"))}
                                Text("Belirsizlikler: ${result.getJSONArray("uncertainties")}",style=MaterialTheme.typography.bodySmall)
                                result.getJSONArray("citations").objects().forEach{cite->val source=entry.getJSONArray("passages").objects().first{it.getString("id")==cite.getString("id")}
                                    TextButton(onClick={openSource(source.getInt("page"))}){Text("${source.getString("source")} · PDF ${source.getInt("page")}: ${cite.getString("quote")}")}}
                                if(result.getJSONArray("cards").length()>0)Text("${result.getJSONArray("cards").length()} yeni kart Kartlar bölümüne eklendi; inceleme bekliyor.")
                            }}}
                        }
                    }
                    1->{var index by remember{mutableIntStateOf(0)};val card=scoped.getOrNull(index.coerceIn(0,(scoped.size-1).coerceAtLeast(0)))
                        var revealed by remember(card?.optString("id")){mutableStateOf(false)};var cloze by remember(card?.optString("id")){mutableStateOf("")}
                        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(16.dp),verticalArrangement=Arrangement.spacedBy(10.dp)){
                            Text("${scoped.size} kart · ${approved.size} öğrenci tarafından incelendi")
                            if(card==null)Text("Henüz kart yok. Koç → Yeni çalışma kartları ile kaynaklı kartlar üretin.")else{
                                Row{TextButton(onClick={index--},enabled=index>0){Text("Önceki")};Text("${index+1}/${scoped.size}");TextButton(onClick={index++},enabled=index<scoped.lastIndex){Text("Sonraki")}}
                                Text(card.getString("question"),style=MaterialTheme.typography.headlineSmall)
                                Button(onClick={revealed=!revealed}){Text(if(revealed)"Yanıtı gizle" else "Yanıtı göster")}
                                if(revealed){Text(card.getString("answer"));Text("Kanıt: ${card.getString("evidence")}")
                                    TextButton(onClick={openSource(card.getInt("page"))}){Text("${card.getString("sourceName")} · PDF ${card.getInt("page")}")}
                                    Row(verticalAlignment=Alignment.CenterVertically){Checkbox(review.optJSONObject(card.getString("id"))?.optBoolean("approved")==true,{value->action{store.updateReview(card.getString("id")){it.put("approved",value)}}},enabled=!busy);Text("Kaynağı ve yanıtı inceledim")}
                                    OutlinedTextField(cloze,{cloze=it},label={Text("Alıntıda gizlenecek terim")});TextButton(onClick={action{tools.cloze(signature!!,card,cloze)}},enabled=!busy && cloze.isNotBlank()){Text("Boşluk doldurma kartı oluştur")}
                                }
                            }
                            TextButton(onClick={scope.launch{try{val file=withContext(Dispatchers.IO){tools.exportCards(signature!!,approved)};onSave(file)}catch(e:Exception){error=e.message}}},enabled=approved.isNotEmpty() && !busy){Text("İncelenmiş kartları Anki TSV kaydet")}
                            Text("Kartlar AI taslağıdır. Kaynağı incelemeden ezberleme. Boşluk doldurma kartları alıntıdan oluşturulur; otomatik anlam/doğruluk puanı verilmez.",style=MaterialTheme.typography.bodySmall)
                        }
                    }
                    2->{var count by remember(approved.size){mutableStateOf(minOf(10,approved.size).toString())};var minutes by remember{mutableStateOf("20")};var tick by remember{mutableLongStateOf(System.currentTimeMillis())}
                        LaunchedEffect(mock?.optString("id")){while(true){tick=System.currentTimeMillis();delay(1000)}}
                        val current=mock;val ended=current!=null && (current.getBoolean("submitted") || tick>=current.getLong("deadline"))
                        LaunchedEffect(ended,current?.optString("id")){if(ended && current!=null && !current.getBoolean("submitted"))action{tools.submitMock(signature!!)}}
                        LazyColumn(Modifier.weight(1f).padding(16.dp),verticalArrangement=Arrangement.spacedBy(12.dp)){
                            item{Text("Kaynaklı süreli deneme",style=MaterialTheme.typography.titleMedium);Text("Sorular incelenmiş AI çalışma kartlarından seçilir. Gerçek öğretmen sınavı tahmini değildir. Yanıt anahtarı teslimden veya süre bitiminden sonra açılır.",style=MaterialTheme.typography.bodySmall)
                                Row{OutlinedTextField(count,{count=it.filter(Char::isDigit)},label={Text("Soru sayısı / ${approved.size}")},modifier=Modifier.weight(1f));OutlinedTextField(minutes,{minutes=it.filter(Char::isDigit)},label={Text("Dakika")},modifier=Modifier.weight(1f))}
                                Button(onClick={action{tools.createMock(signature!!,approved.sortedByDescending{review.optJSONObject(it.getString("id"))?.optInt("lapses",0) ?:0},count.toInt(),minutes.toInt())}},enabled=!busy && (count.toIntOrNull() ?:0) in 1..approved.size && (minutes.toIntOrNull() ?:0) in 1..240 && (current==null || current.getBoolean("submitted"))){Text("Yeni deneme başlat")}
                                if(current!=null){Text(if(ended)"Deneme tamamlandı · kendi yanıtlarını kaynakla değerlendir" else "Kalan: ${StudyPlanner.remainingSeconds(current.getLong("deadline"),tick)} saniye")
                                    if(!ended)TextButton(onClick={action{tools.submitMock(signature!!)}}){Text("Denemeyi teslim et")}
                                }
                            }
                            if(current!=null)items(current.getJSONArray("cards").objects(),key={it.getString("id")}){c->
                                val id=c.getString("id");var answer by remember(current.getString("id"),id){mutableStateOf(current.getJSONObject("answers").optString(id))}
                                OutlinedCard{Column(Modifier.padding(12.dp),verticalArrangement=Arrangement.spacedBy(8.dp)){
                                    Text(c.getString("question"),style=MaterialTheme.typography.titleMedium)
                                    OutlinedTextField(answer,{value->answer=value;val typedAt=System.currentTimeMillis();action(false){tools.answerMock(signature!!,id,value,typedAt)}},label={Text("Yanıtım")},enabled=!ended && !busy,modifier=Modifier.fillMaxWidth())
                                    if(ended){Text("Yanıt anahtarı: ${c.getString("answer")}");Text("Kanıt: ${c.getString("evidence")}");TextButton(onClick={openSource(c.getInt("page"))}){Text("Kaynağı aç · PDF ${c.getInt("page")}")}
                                        Text("Öz değerlendirme: 0 eksik, 1 kısmi, 2 tamam. Resmi not değildir.",style=MaterialTheme.typography.bodySmall)
                                        Row{(0..2).forEach{mark->TextButton(onClick={action{tools.markMock(signature!!,id,mark)}},enabled=!current.getJSONObject("marks").has(id) && current.getBoolean("submitted") && !busy){Text("$mark / 2")}}}
                                    }
                                }}
                            }
                            if(current!=null && current.getBoolean("submitted"))item{
                                Text("Öz puan toplamı: ${current.getJSONObject("marks").keys().asSequence().sumOf{current.getJSONObject("marks").getInt(it)}} / ${current.getJSONArray("cards").length()*2}. İşaretlenmemiş sorular olabilir; bu bir sınav başarı tahmini değildir.")
                                TextButton(onClick={scope.launch{try{val file=withContext(Dispatchers.IO){tools.exportMock(current)};onSave(file)}catch(e:Exception){error=e.message}}}){Text("Deneme ve yanıtlarımı kaydet")}
                            }
                        }
                    }
                    else->{var minutes by remember{mutableStateOf("30")};val today=LocalDate.now();val due=approved.filter{(review.optJSONObject(it.getString("id"))?.optLong("dueDay",0) ?:0)<=today.toEpochDay()}.groupingBy{it.getString("section")}.eachCount()
                        val lapses=approved.groupBy{it.getString("section")}.mapValues{(_,rows)->rows.sumOf{review.optJSONObject(it.getString("id"))?.optInt("lapses",0) ?:0}}
                        val plan=StudyPlanner.daily(units.map{it.getString("section")},due,lapses,(minutes.toIntOrNull() ?:30).coerceIn(5,240))
                        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(16.dp),verticalArrangement=Arrangement.spacedBy(12.dp)){
                            Text("Bugünün çalışma planı · $today",style=MaterialTheme.typography.titleMedium)
                            OutlinedTextField(minutes,{minutes=it.filter(Char::isDigit)},label={Text("Günlük süre: 5–240 dakika")})
                            Text("${units.count{it.optString("status")=="FAILED"}} okunamayan bölge · ${cards.size-approved.size} kapsam/ek bağlam veya inceleme dışında kart. Önce eksikleri ve belirsizlikleri kontrol et.")
                            if(config.examDate.isNotBlank())Text("İsteğe bağlı sınav tarihi: ${config.examDate}; öğretmenin gerçek soru dağılımı bilinmiyor.")
                            plan.forEach{task->OutlinedCard(Modifier.fillMaxWidth()){Column(Modifier.padding(12.dp)){Text(task.topic,style=MaterialTheme.typography.titleMedium);Text("${task.questions} geri çağırma/uygulama adımı · ${task.reason}");Text("Notu kapat → açıkla/çiz/çöz → kaynakla karşılaştır → hatayı düzelt.",style=MaterialTheme.typography.bodySmall)}}}
                            Text("Aralıklı tekrar ve aktif hatırlama desteklenir. Öğrenilmiş konuları karıştır; yeni konuyu önce açıklama ve çözülmüş örnekle öğren. Süre tahmini bir planlama kuralıdır, kişisel hafıza veya not tahmini değildir.",style=MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
        }}
    }
    preview?.let{(file,page)->PreviewDialog(file,"Kaynak PDF",{preview=null},page)}
}
