package org.studyfuse.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.*
import kotlinx.coroutines.*
import org.json.*
import org.studyfuse.data.Source
import org.studyfuse.core.Format
import org.studyfuse.engine.PageMapping
import java.io.File

@Composable fun CompletenessDashboard(report:File,sources:List<Source>,onDismiss:()->Unit) {
    var data by remember{mutableStateOf<JSONObject?>(null)}
    var error by remember{mutableStateOf<String?>(null)}
    var inspect by remember{mutableStateOf<Pair<Source,PageMapping>?>(null)}
    var outputPreview by remember{mutableStateOf<Int?>(null)}
    LaunchedEffect(report){try{data=withContext(Dispatchers.IO){JSONObject(report.readText())}}catch(e:Exception){error=e.message}}
    val output=data?.optString("fullPdf")?.let(::File)
    Dialog(onDismissRequest=onDismiss,properties=DialogProperties(usePlatformDefaultWidth=false)) {
        Surface(Modifier.fillMaxWidth(.95f).fillMaxHeight(.95f),shape=MaterialTheme.shapes.large){Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment=Alignment.CenterVertically){Text("Görsel bütünlük denetimi",Modifier.weight(1f),style=MaterialTheme.typography.titleLarge);TextButton(onClick=onDismiss){Text("Kapat")}}
            error?.let{Text(it,color=MaterialTheme.colorScheme.error)}
            data?.let{r->
                val included=r.optBoolean("allSourcesIncluded",r.optBoolean("complete"))
                Text(if(!included)"Eksik kaynak var. Uyarı sayfaları kaynak içeriğini içermez." else if(r.optInt("reviewFiles")>0)"Tüm kaynaklar eklendi; bazıları görsel inceleme bekliyor." else "Tüm kaynaklar işlendi. Önemli çizimleri ve sayfa sınırlarını yine de karşılaştırın.")
                Text("${r.getInt("importedFiles")} kaynak · ${r.getInt("originalPages")} bilinen orijinal sayfa · ${r.getInt("generatedPages")} PDF sayfası",Modifier.padding(vertical=8.dp))
                val array=r.getJSONArray("files");val rows=(0 until array.length()).map{array.getJSONObject(it)}
                LazyColumn(verticalArrangement=Arrangement.spacedBy(10.dp)) {
                    items(rows){f->
                        val s=sources.firstOrNull{it.id==f.getString("sourceId")}
                        OutlinedCard(Modifier.fillMaxWidth()){Column(Modifier.padding(12.dp)) {
                            Text(f.getString("section"),style=MaterialTheme.typography.titleMedium)
                            Text(f.getString("fileName"),style=MaterialTheme.typography.bodySmall)
                            val status=when(f.getString("status")){"OK"->"İşlendi";"REVIEW"->"Görsel inceleme gerekli";"FALLBACK"->"PDF dönüşümü bekliyor";else->"İşlenemedi — içerik eksik"}
                            Text("$status · Orijinal: ${f.getInt("originalPages")} · Üretilen: ${f.getInt("generatedContentPages")}")
                            Text("PDF aralığı: ${f.getInt("startPage")}–${f.getInt("endPage")}")
                            val warnings=f.getJSONArray("warnings")
                            for(i in 0 until warnings.length())Text("• ${warnings.getString(i)}",style=MaterialTheme.typography.bodySmall,modifier=Modifier.padding(top=4.dp))
                            val maps=f.getJSONArray("coverage")
                            Row {
                                TextButton(onClick={outputPreview=f.optInt("contentStart",f.getInt("startPage"))-1},enabled=output!=null){Text("Çıktıya git")}
                            }
                            if(s!=null && Format.valueOf(s.format) in setOf(Format.PDF,Format.IMAGE) && output!=null) {
                                Text("Karşılaştırılacak çıktı bölümü:",style=MaterialTheme.typography.labelSmall)
                                LazyRow {
                                    items(maps.length()){i->
                                        val m=maps.getJSONObject(i);val b=m.getJSONArray("sourceBounds");val dims=m.getJSONArray("sourceSize")
                                        TextButton(onClick={inspect=s to PageMapping(m.getInt("originalPage"),m.getInt("outputPage"),b.getDouble(0),b.getDouble(1),b.getDouble(2),b.getDouble(3),dims.getDouble(0),dims.getDouble(1),m.getDouble("scale"))}) {
                                            Text("${m.getInt("originalPage")} → ${m.getInt("outputPage")}")
                                        }
                                    }
                                }
                            }
                        }}
                    }
                }
            }
        }}
    }
    inspect?.let{(s,m)->output?.let{ComparisonDialog(File(s.activePath),it,m.originalPage-1,m.outputPage-1,m,{inspect=null})}}
    outputPreview?.let{p->output?.let{PreviewDialog(it,it.name,{outputPreview=null},p)}}
}
