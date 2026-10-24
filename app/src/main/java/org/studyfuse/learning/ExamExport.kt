package org.studyfuse.learning

import android.content.Context
import com.tom_roush.pdfbox.pdmodel.PDDocument
import org.json.JSONObject
import org.studyfuse.core.KnowledgeMode
import org.studyfuse.engine.PdfText
import java.io.File

object ExamExport {
    fun markdown(store:ExamStore,session:JSONObject,units:List<JSONObject>,review:JSONObject):String=buildString {
        val config=ExamConfig.decode(session.getJSONObject("config"))
        appendLine("# StudyFuse — Sınav hazırlığı / Prüfungsvorbereitung")
        appendLine("Kaynak modu: ${config.mode} · Dil: ${config.language} · Model: ${session.getString("model")}")
        appendLine("AI taslağıdır. Alıntı eşleşmesi doğruluğu/çıkarımı kanıtlamaz. Görsel transkripsiyonlar, formüller ve çözümler kaynak PDF ile kontrol edilmelidir. Ek bağlam isteğe bağlıdır; gerçek sınav kapsamı bilinmeyebilir.\n")
        appendLine("## Kapsam ve belirsizlikler")
        for(source in session.getJSONArray("files").objects())appendLine("- ${source.getString("fileName")}: ${source.getString("status")}, ${source.getInt("visualPages")} görsel sayfa")
        appendLine("- Bölgeler: ${units.size}/${session.getInt("total")}; başarısız: ${units.count{it.optString("status")=="FAILED"}}. İşlenmiş bölge bütün işaretlerin anlaşıldığı anlamına gelmez.\n")
        appendLine("## Nasıl çalışmalı?")
        appendLine("Önce konu notlarını ve kaynak görselleri incele. Ardından belgeyi kapatıp soruları yanıtla, yanıt anahtarıyla karşılaştır ve hatanın nedenini yaz. Kaynaktaki çözülmüş örnekleri açıklayarak incele, sonra çözüm adımlarını gizleyerek dene. Başlangıç öğrenmesinden sonra konuları karıştır. Başarılı geri çağırmalarda 1, 3, 7, 14, 30, 60 günlük aralıklar önerilir; bu pratik bir kuraldır, kişisel hafıza tahmini değildir.\n")
        appendLine("## Kaynaklı konu notları")
        for(unit in units) {
            appendLine("### ${unit.getString("section")} · PDF ${unit.getInt("page")} · bölge ${unit.getInt("region")}")
            appendLine("Kaynak: ${unit.getString("sourceName")}${if(unit.optBoolean("reference"))" · EK BAŞVURU" else ""}")
            if(unit.optString("status")=="FAILED"){appendLine("**OKUNAMADI:** ${unit.optString("error")}\n");continue}
            appendLine("Görsel okumadan kalan belirsizlikler: ${unit.getJSONArray("uncertainties")}.")
            for(item in unit.getJSONArray("items").objects().filter{it.getString("kind")=="note"})appendItem(item,review,true)
            if(unit.getJSONArray("items").length()==0)appendLine("Bu bölge için çalışma maddesi üretilemedi; kaynak görseli inceleyin.")
            appendLine("Reddedilen maddeler: ${unit.getJSONArray("rejected").length()}\n")
        }
        appendLine("## Aktif hatırlama — yanıtları açmadan dene")
        var number=0;val answers=StringBuilder()
        for(unit in units)for(item in unit.optJSONArray("items")?.objects().orEmpty().filter{it.getString("kind")!="note"}) {
            number++;val label=label(item,review)
            appendLine("$number. ${item.getString("question")} [PDF ${unit.getInt("page")}, ${unit.getString("sourceName")}; $label]")
            answers.appendLine("### $number · PDF ${unit.getInt("page")} · $label\n${item.getString("answer")}\nKanıt: ${item.getString("evidence")}\n")
        }
        appendLine("\n## Ayrı yanıt anahtarı\n$answers")
        appendLine("## Hata defteri ve tekrar planı — öğrencinin değerlendirmesi")
        for(unit in units)for(item in unit.optJSONArray("items")?.objects().orEmpty())review.optJSONObject(item.getString("id"))?.let{r->
            if(r.optInt("attempts",0)>0)appendLine("- ${item.getString("question")} [Kaynak PDF ${unit.getInt("page")}]: ${r.optInt("attempts")} deneme, ${r.optInt("lapses")} tekrar hatası. Sonraki gün: ${java.time.LocalDate.ofEpochDay(r.optLong("dueDay",0))}. Düzeltme: ${r.optString("correction")}")
        }
        appendLine("## Görsel okuma kayıtları — doğrulanmamış transkripsiyon")
        for(unit in units.filter{it.optString("status")!="FAILED"})appendLine("### PDF ${unit.getInt("page")} / bölge ${unit.getInt("region")}\n${unit.getString("transcription")}\nGörsel ilişkiler: ${unit.getString("observations")}\n")
    }
    private fun label(item:JSONObject,review:JSONObject)=
        (if(item.getString("origin")=="ai_added")"MODEL EK BİLGİSİ — kaynak atfı yok" else "KAYNAK · ${item.getString("basis")}")+" · "+
            if(review.optJSONObject(item.getString("id"))?.optBoolean("approved")==true)"ÖĞRENCİ İNCELEDİ" else "İNCELENMEMİŞ"
    private fun StringBuilder.appendItem(item:JSONObject,review:JSONObject,answer:Boolean){
        appendLine("**${item.getString("question")}** [${label(item,review)}]")
        if(answer)appendLine(item.getString("answer"));appendLine("Kanıt alıntısı: ${item.getString("evidence")}\n")
    }
    fun write(context:Context,store:ExamStore,session:JSONObject,units:List<JSONObject>,report:File?=null):List<File> {
        val text=markdown(store,session,units,store.review());val md=File(store.root,"StudyFuse-sinav-notlari.md");md.writeText(text)
        val pdf=File(store.root,"StudyFuse-sinav-notlari.pdf");val temp=File(store.root,"export.part")
        try {PDDocument().use{doc->val writer=PdfText(context,doc)
            // Explicit codepoint labels preserve unsupported symbols instead of silently dropping them.
            writer.pages(writer.metadata(text),"StudyFuse · Sınav hazırlığı")
            if(report!=null) {
                writer.pages("Sonraki sayfalar kaynak derlemesinin görsel ekidir. Çalışma notlarındaki PDF numaraları bu kaynak derlemesinin basılı sayfa numaralarıdır; bu dosyanın başındaki not sayfaları numaralara dahil değildir. Ek başvuru olarak işaretlenen orijinaller de kaynak arşivinde bulunabilir; bu, yalnızca belgelerim modunda onlardan not üretildiği anlamına gelmez.","Kaynak PDF · Görsel ek")
                for(output in readJson(report).getJSONArray("outputs").objects()) {
                    val original=File(output.getString("path"));check(original.canonicalPath.startsWith(store.project.canonicalPath+"/"))
                    org.studyfuse.engine.loadPdf(original,store.root).use{source->com.tom_roush.pdfbox.multipdf.PDFMergerUtility().appendDocument(doc,source)}
                }
            }
            doc.save(temp)
        };check(temp.renameTo(pdf)){"Sınav PDF'si kaydedilemedi"}}finally{temp.delete()}
        return listOf(pdf,md)
    }
}
