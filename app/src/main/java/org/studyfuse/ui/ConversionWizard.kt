package org.studyfuse.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.studyfuse.core.Format
import org.studyfuse.data.Source

@Composable fun ConversionWizard(sources:List<Source>,onReplace:(Source)->Unit,onBulk:()->Unit,onDismiss:()->Unit) {
    val pending=sources.filter{!Format.valueOf(it.format).native}
    AlertDialog(onDismissRequest=onDismiss,title={Text("Office / OneNote PDF hazırlığı")},text={LazyColumn {
        item{Text("Kaynak uygulamada tüm sayfaları, slaytları veya çalışma sayfalarını PDF olarak dışa aktarın. PDF'yi aynı temel dosya adıyla kaydederseniz toplu eşleştirebilirsiniz. Orijinal dosyalar ve sıraları korunur.")}
        item{Text("Denetim listesi: son sayfa ve sağ kenar; diyagramlar ve formüller; konuşmacı notları; gizli slayt/sayfalar ve baskı alanları. Animasyon ve ek dosyalar statik PDF'de korunmayabilir.",Modifier.padding(vertical=12.dp),style=MaterialTheme.typography.bodySmall)}
        items(pending){s->OutlinedCard(Modifier.fillMaxWidth().padding(vertical=4.dp)){Column(Modifier.padding(8.dp)){
            Text(s.name);Text(Format.valueOf(s.format).explanation,style=MaterialTheme.typography.bodySmall)
            TextButton(onClick={onReplace(s);onDismiss()}){Text("Bu kaynak için PDF seç")}
        }}}
        if(pending.isEmpty())item{Text("PDF dönüşümü bekleyen kaynak yok.")}
    }},confirmButton={TextButton(onClick={onBulk();onDismiss()},enabled=pending.isNotEmpty()){Text("PDF'leri toplu eşleştir")}},dismissButton={TextButton(onClick=onDismiss){Text("Kapat")}})
}
