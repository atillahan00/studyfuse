package org.studyfuse.ui

import android.app.ActivityManager
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.*
import org.studyfuse.core.ModelCatalog
import org.studyfuse.learning.*
import java.io.File

@Composable fun AutomaticSetup(model:StudyViewModel,gpu:Boolean,enabled:Boolean=true) {
    val context=LocalContext.current
    val memory=remember{ActivityManager.MemoryInfo().also{context.getSystemService(ActivityManager::class.java).getMemoryInfo(it)}.totalMem}
    var choice by remember{mutableStateOf(if(memory>10L*1024*1024*1024)"gemma4-e4b" else "gemma4-e2b")}
    var wifiOnly by remember{mutableStateOf(true)}
    val work by model.setupWork.collectAsStateWithLifecycle();val active=work.firstOrNull{!it.state.isFinished}
    val revision by model.examRevision.collectAsStateWithLifecycle()
    var status by remember{mutableStateOf("")}
    LaunchedEffect(work,revision){status=withContext(Dispatchers.IO){runCatching{val j=readJson(File(ModelStore(context).directory(),"setup.json"));j.optString("message",j.optString("status"))}.getOrDefault("")}}
    val artifact=ModelCatalog.get(choice)
    OutlinedCard(Modifier.fillMaxWidth()){Column(Modifier.padding(14.dp),verticalArrangement=Arrangement.spacedBy(8.dp)){
        Text("Otomatik çevrimdışı AI kurulumu",style=MaterialTheme.typography.titleMedium)
        Text("Modeli uygulama indirir, SHA-256 ile doğrular ve görüntü girişini sınar. Yalnızca model dosyası indirilir; belgelerin gönderilmez. İlk kurulumdan sonra çalışma çevrimdışıdır.",style=MaterialTheme.typography.bodySmall)
        ModelCatalog.models.forEach{m->Row{RadioButton(choice==m.id,{choice=m.id},enabled=enabled && active==null);Column{Text(m.name);Text("${"%.2f".format(m.bytes/1e9)} GB indirme · ${m.memoryGb} GB RAM sınıfı · Apache 2.0",style=MaterialTheme.typography.bodySmall)}}}
        Text("Aygıt RAM'i: yaklaşık ${"%.1f".format(memory/1e9)} GB. Boyut ve sürücü uyumluluğu gerçek aygıtta sınanır; görüntü testi başarı garantisi değildir.",style=MaterialTheme.typography.bodySmall)
        Row{Switch(wifiOnly,{wifiOnly=it},enabled=active==null && enabled);Text("Yalnızca ölçümsüz ağ / Wi-Fi")}
        if(active!=null){val bytes=active.progress.getLong("bytes",0);val total=active.progress.getLong("total",artifact.bytes)
            LinearProgressIndicator(progress={if(total>0)bytes.toFloat()/total else 0f},modifier=Modifier.fillMaxWidth())
            Text("${sizeLabel(bytes)} / ${sizeLabel(total)} · ${active.progress.getString("stage") ?: "Ağ koşulları / kurulum bekleniyor"}",style=MaterialTheme.typography.bodySmall)
            TextButton(onClick={model.pauseSetup()}){Text("İndirmeyi duraklat")}
        }else Button(onClick={model.setupModel(choice,gpu,wifiOnly)},enabled=enabled){Text("Otomatik kur / sürdür")}
        if(active==null)TextButton(onClick={model.resetModelDownload(choice)},enabled=enabled){Text("Seçili indirme önbelleğini temizle")}
        if(status.isNotBlank())Text(status,style=MaterialTheme.typography.bodySmall)
        Text("Kurulum model ve GPU uyumsuzluğunu bildirebilir. Önceden çalışan model, yeni modelin testi geçene kadar korunur. Elle içe aktarma seçeneği de kullanılabilir.",style=MaterialTheme.typography.bodySmall)
    }}
}
