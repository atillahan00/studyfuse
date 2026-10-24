package org.studyfuse.engine

import android.content.Context
import java.io.File
import kotlinx.coroutines.*
import org.json.*
import org.studyfuse.core.Slice

class AiCompanion(private val context:Context,private val scratch:File) {
    suspend fun write(directory:File,sections:List<Pair<FileResult,File>>,margin:Double,totalPages:Int):Unit=withContext(Dispatchers.IO) {
        val index=JSONArray()
        File(directory,"StudyFuse-notlar.md").bufferedWriter().use {out ->
            out.appendLine("# StudyFuse — Sayfa sınırlarına göre çalışma metni")
            out.appendLine("Görsel PDF asıl kaynaktır. Bu eşlikçi metin yalnızca görünür sınırlar içindeki erişilebilir metni içerir. El yazısı ve çizimler için PDF'yi inceleyin. OCR doğrulanmamıştır.\n")
            for((r,file) in sections) {
                currentCoroutineContext().ensureActive()
                out.appendLine("## ${r.source.section.replace('\n',' ')}\nKaynak: ${r.source.name.replace('\n',' ')}\nDurum: ${r.status}\n")
                if(r.status in setOf("FAILED","FALLBACK")) {out.appendLine("**Bu kaynak dönüştürülemedi; PDF'de yalnızca uyarı sayfası bulunur.**\n");continue}
                val seen=mutableSetOf<String>()
                loadPdf(file,scratch).use {doc ->
                    for(page in 0 until r.visualPages) {
                        currentCoroutineContext().ensureActive()
                        val mapping=r.mappings.firstOrNull {it.outputPage==r.contentStart+page}
                        val bounds=mapping?.let {Slice(margin,margin,margin+(it.right-it.left)*it.scale,margin+(it.bottom-it.top)*it.scale)}
                        val text=try {PageText.extract(doc,page,bounds,mapping,seen)} catch(e:Exception) {
                            r.warnings+="Eşlikçi metin çıkarılamadı, içerik sayfası ${page+1}: ${e.message}";""
                        }
                        val global=r.contentStart+page
                        out.appendLine("### PDF sayfa $global · Kaynak sayfa ${mapping?.originalPage ?: page+1}")
                        out.appendLine(if(text.isBlank()) "[Erişilebilir metin yok veya örtüşmede zaten verildi; görsel sayfayı inceleyin.]\n" else "$text\n")
                        index.put(JSONObject().put("page",global).put("sourceId",r.source.id).put("sourcePage",mapping?.originalPage ?: page+1)
                            .put("section",r.source.section).put("kind","source").put("text",text))
                    }
                }
                for(o in r.ocr) {
                    val text=o.optString("text");val page=r.contentStart+o.getInt("contentPage")-1
                    out.appendLine("### DOĞRULANMAMIŞ OCR · PDF sayfa $page\n$text\n")
                    index.put(JSONObject().put("page",page).put("sourceId",r.source.id).put("section",r.source.section).put("kind","unverified_ocr").put("text",text))
                }
            }
        }
        File(directory,"StudyFuse-index.json").writeText(index.toString())
        File(directory,"StudyFuse-prompt-de.txt").writeText(prompt(totalPages,sections.map{it.first},""))
    }
    companion object {
        fun prompt(totalPages:Int,sections:List<FileResult>,focus:String):String=buildString {
            append("Analysiere das beigefügte StudyFuse-Dokument mit $totalPages PDF-Seiten vollständig.\n")
            append("Berücksichtige jede Quellseite einschließlich Diagrammen, Tabellen, Formeln und handschriftlichen Anmerkungen. Die visuelle PDF ist maßgeblich. Die Markdown-Datei enthält ergänzenden Text; OCR ist ausdrücklich ungeprüft.\n\n")
            if(focus.isNotBlank())append("Prüfungsschwerpunkt: $focus\n\n")
            append("Erstelle eine strukturierte Prüfungsvorbereitung: Erklärungen, Fachbegriffe, Zusammenhänge, typische Fehler, Übungsaufgaben und begründete Lösungen. Verweise auf PDF-Seiten. Kennzeichne unleserliche, fehlende oder unsichere Inhalte; erfinde sie nicht. Trenne Quellinhalt und zusätzliche Erklärungen.\n\nQuellen:\n")
            sections.forEach {append("- ${it.source.section}: ${it.source.name}, Seiten ${it.start}–${it.end}, Status ${it.status}\n")}
            if(sections.any{it.status in setOf("FAILED","FALLBACK")})append("\nACHTUNG: Nicht alle Quellen konnten konvertiert werden. Eine Warnseite ist kein Ersatz für den fehlenden Inhalt.\n")
        }
    }
}
