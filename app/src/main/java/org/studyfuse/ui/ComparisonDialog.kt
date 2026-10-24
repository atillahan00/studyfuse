package org.studyfuse.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.*
import org.studyfuse.core.Slice
import org.studyfuse.engine.*
import java.io.File

@Composable fun ComparisonDialog(source:File,output:File,originalPage:Int,outputPage:Int,mapping:PageMapping?,onDismiss:()->Unit) {
    var useRegion by remember{mutableStateOf(mapping!=null)}
    val factor=if(DocumentView.isPdf(source))1.0 else 1/ImageSource.POINTS_PER_PIXEL
    val area=if(useRegion)mapping?.let{Slice(it.left*factor,it.top*factor,it.right*factor,it.bottom*factor)} else null
    Dialog(onDismissRequest=onDismiss,properties=DialogProperties(usePlatformDefaultWidth=false)) {
        Surface(Modifier.fillMaxWidth(.98f).fillMaxHeight(.96f),shape=MaterialTheme.shapes.large){Column {
            Row(Modifier.padding(8.dp),verticalAlignment=Alignment.CenterVertically){Text("Kaynak ve çıktı",Modifier.weight(1f),style=MaterialTheme.typography.titleLarge)
                if(mapping!=null)TextButton(onClick={useRegion=!useRegion}){Text(if(useRegion)"Kaynağın tamamı" else "Eşleşen bölge")}
                TextButton(onClick=onDismiss){Text("Kapat")}}
            Text("İki görünüm ayrı yakınlaştırılabilir. Kırpma alanı farklı PDF'lerde kaynağın tamamını da denetleyin.",Modifier.padding(horizontal=12.dp),style=MaterialTheme.typography.bodySmall)
            Row(Modifier.weight(1f)) {
                Column(Modifier.weight(1f)){Text("Orijinal · ${originalPage+1}. sayfa",Modifier.padding(8.dp));DocumentPane(source,originalPage,Modifier.weight(1f),area)}
                VerticalDivider()
                Column(Modifier.weight(1f)){Text("Çıktı · ${outputPage+1}. sayfa",Modifier.padding(8.dp));DocumentPane(output,outputPage,Modifier.weight(1f))}
            }
        }}
    }
}
