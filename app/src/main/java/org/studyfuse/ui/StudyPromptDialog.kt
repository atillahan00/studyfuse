package org.studyfuse.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.*
import java.io.File

@Composable fun StudyPromptDialog(file:File,onSave:(File)->Unit,onDismiss:()->Unit) {
    val context=LocalContext.current
    var text by remember{mutableStateOf("")}
    var error by remember{mutableStateOf<String?>(null)}
    val scope=rememberCoroutineScope()
    LaunchedEffect(file){try{text=withContext(Dispatchers.IO){file.readText()}}catch(e:Exception){error=e.message}}
    AlertDialog(onDismissRequest=onDismiss,title={Text("Almanca çalışma isteği")},text={Column{
        Text("Sınav konunu veya istediğin soru türlerini ekleyebilirsin. Bu metin yerel bir şablondur; bir yapay zekâ hizmetine bağlanılmaz.",style=MaterialTheme.typography.bodySmall)
        OutlinedTextField(text,{text=it},modifier=Modifier.fillMaxWidth().heightIn(min=200.dp,max=400.dp),label={Text("ChatGPT'ye verilecek istek")})
        error?.let{Text(it,color=MaterialTheme.colorScheme.error)}
    }},confirmButton={TextButton(onClick={
        (context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(ClipData.newPlainText("StudyFuse",text))
        onDismiss()
    },enabled=text.isNotBlank()){Text("Kopyala")}},dismissButton={Row {
        TextButton(onClick={scope.launch{try{withContext(Dispatchers.IO){file.writeText(text)};onSave(file)}catch(e:Exception){error=e.message}}},enabled=text.isNotBlank()){Text("Dosyaya kaydet")}
        TextButton(onClick=onDismiss){Text("Kapat")}
    }})
}
