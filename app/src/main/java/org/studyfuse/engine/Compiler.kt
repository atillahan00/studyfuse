package org.studyfuse.engine

import android.content.Context
import com.tom_roush.pdfbox.cos.COSName
import com.tom_roush.pdfbox.multipdf.PDFMergerUtility
import com.tom_roush.pdfbox.pdmodel.*
import com.tom_roush.pdfbox.pdmodel.common.PDRectangle
import com.tom_roush.pdfbox.pdmodel.interactive.documentnavigation.destination.PDPageFitDestination
import com.tom_roush.pdfbox.pdmodel.interactive.documentnavigation.outline.PDDocumentOutline
import com.tom_roush.pdfbox.pdmodel.interactive.documentnavigation.outline.PDOutlineItem
import com.tom_roush.pdfbox.rendering.PDFRenderer
import kotlinx.coroutines.*
import org.json.*
import org.studyfuse.core.*
import org.studyfuse.data.Source
import org.studyfuse.data.readPrefix
import java.io.File
import kotlin.math.*

data class Compilation(val outputs:List<Artifact>,val report:File,val reportText:File,val complete:Boolean,val pages:Int)
data class ProcessingProgress(val file:String,val stage:String,val processed:Int,val total:Int)

class Compiler(private val context:Context,private val runDirectory:File) {
    private val scratch=File(runDirectory,"scratch").apply { mkdirs() }
    private val adapters:Map<Format,ConversionAdapter> = Format.entries.associateWith {
        when(it) {
            Format.PDF -> PdfAdapter(context,scratch)
            Format.IMAGE -> ImageAdapter(scratch)
            Format.TEXT -> TextAdapter(context,scratch)
            else -> ExternalPdfAdapter(it)
        }
    }
    private data class Prepared(val result:FileResult,val content:File,val divider:File?,val dividerPages:Int)

    suspend fun compile(sources:List<Source>,settings:ExportSettings,
        progress:suspend(ProcessingProgress)->Unit = {}):Compilation = withContext(Dispatchers.IO) {
        require(sources.isNotEmpty()) { "Önce dosya ekleyin" }
        val prepared=mutableListOf<Prepared>()
        try {
            for((i,source) in sources.withIndex()) {
                ensureActive()
                val result=FileResult(source)
                val content=File(scratch,"${source.id}-content.pdf")
                progress(ProcessingProgress(source.name,"Doğrulama",i,sources.size))
                try {
                    val original=File(source.originalPath); val active=File(source.activePath)
                    check(sha256(original)==source.sha256) { "Orijinal dosya özeti değişti; bütünlük denetimi başarısız" }
                    result.activeSha256=sha256(active)
                    check(result.activeSha256==source.activeSha256) { "Kullanılan PDF / görüntü özeti değişti; bütünlük denetimi başarısız" }
                    if(source.replacement) result.warnings+="Kaynak uygulamadan alınan PDF kullanıldı. PDF'nin orijinalin tüm içeriğini kapsadığı kullanıcı tarafından denetlenmelidir."
                    val format=Format.detect(source.name,active.inputStream().use { it.readPrefix(32) })
                    adapters.getValue(format).convert(source,content,settings,result) { stage ->
                        ensureActive(); progress(ProcessingProgress(source.name,stage,i,sources.size))
                    }
                    val qualityWarnings=verify(content,result.generatedPages)
                    if(qualityWarnings.isNotEmpty()) {result.status="REVIEW";result.warnings+=qualityWarnings}
                    if(settings.ocr) {
                        try { addOcr(content,result) { stage ->
                            progress(ProcessingProgress(source.name,stage,i,sources.size))
                        } } catch(e:CancellationException) { throw e }
                        catch(e:Exception) { result.warnings+="OCR eklenemedi: ${e.message}. Orijinal görsel sayfalar korundu." }
                    }
                } catch(e:CancellationException) { throw e }
                catch(e:Exception) {
                    content.delete(); result.generatedPages=0; result.mappings.clear()
                    result.status=if(e is RequiresConversion) "FALLBACK" else "FAILED"
                    result.warnings+=(e.message ?: e.javaClass.simpleName)
                    result.generatedPages=metadataPdf(content,
                        "${if(result.status=="FALLBACK") "DÖNÜŞÜM GEREKİYOR" else "DOSYA İŞLENEMEDİ"}\n\n"+
                        "Kaynak: ${source.name}\nBölüm: ${source.section}\n\n${result.warnings.last()}\n\n"+
                        "Bu sayfa kaynak belgenin içeriğini içermez. Orijinal aygıtta korunuyor. " +
                        "Kaynak uygulamada PDF dışa aktarın; proje ekranındaki PDF ile değiştir işlemini kullanın. " +
                        "Bu derleme EKSİKTİR.")
                }
                if(result.warnings.isNotEmpty()) progress(ProcessingProgress(source.name,"${result.warnings.size} uyarı · ${result.status}",i,sources.size))
                var divider:File?=null; var dividerPages=0
                if(settings.mode==ExportMode.AI_STUDY) {
                    divider=File(scratch,"${source.id}-divider.pdf")
                    dividerPages=metadataPdf(divider,"${source.section}\n\nOrijinal kaynak: ${source.name}\n\n"+
                        "Durum: ${result.status}\nKaynak sayfa: ${if(result.originalPages==0) "Sayfalı değil / bilinmiyor" else result.originalPages}\n"+
                        "Üretilen içerik sayfası: ${result.generatedPages}\n\n"+
                        "Kaynak sırası: ${i+1}/${sources.size}\n"+
                        if(settings.ocr) "OCR metinleri doğrulanmamış eklerde yer alır. Görseller asıl kaynaktır." else "")
                }
                prepared+=Prepared(result,content,divider,dividerPages)
            }
            ensureActive()
            val complete=prepared.all { it.result.status=="OK" }
            val toc=File(scratch,"toc.pdf")
            var tocPages=0
            if(settings.mode==ExportMode.AI_STUDY) {
                tocPages=metadataPdf(toc,tocText(prepared,false))
            }
            var next=tocPages+1
            for(p in prepared) {
                p.result.start=next; p.result.end=next+p.dividerPages+p.result.generatedPages-1
                p.result.mappings.replaceAll { it.copy(outputPage=it.outputPage+next+p.dividerPages-1) }
                next=p.result.end+1
            }
            if(tocPages>0) check(metadataPdf(toc,tocText(prepared,true))==tocPages) { "İçindekiler sayfa sayısı kararlı değil" }
            val reportText=File(runDirectory,"StudyFuse-rapor.txt")
            var human=humanReport(prepared.map { it.result },complete,settings)
            val reportPdf=File(scratch,"report.pdf");val reportPages=metadataPdf(reportPdf,human)
            human=human.replace("[OUTPUT_PAGES]",(next-1+reportPages).toString())
            check(metadataPdf(reportPdf,human)==reportPages) {"Rapor sayfa sayısı kararlı değil"}
            reportText.writeText(human)
            val assembled=File(runDirectory,if(complete) "StudyFuse.pdf" else "StudyFuse-EKSIK.pdf")
            progress(ProcessingProgress("PDF","Birleştirme ve sayfa numaraları",sources.size,sources.size))
            newPdf(scratch).use { target ->
                val merger=PDFMergerUtility()
                suspend fun append(file:File) {
                    currentCoroutineContext().ensureActive()
                    loadPdf(file,scratch).use { merger.appendDocument(target,it) }
                }
                if(tocPages>0) append(toc)
                for(p in prepared) { p.divider?.let { append(it) }; append(p.content) }
                append(reportPdf)
                val outline=PDDocumentOutline(); target.documentCatalog.documentOutline=outline
                for(p in prepared) {
                    val item=PDOutlineItem(); item.title=p.result.source.section
                    val dest=PDPageFitDestination(); dest.page=target.getPage(p.result.start-1)
                    item.destination=dest; outline.addLast(item)
                }
                outline.openNode()
                val writer=PdfText(context,target)
                for(index in 0 until target.numberOfPages) {
                    ensureActive()
                    val page=target.getPage(index)
                    PDPageContentStream(target,page,PDPageContentStream.AppendMode.APPEND,true,true).use { cs ->
                        // Border reserved by every adapter, including ORIGINAL layout.
                        writer.draw(cs,"${index+1} / ${target.numberOfPages}",24f,8f,8f)
                    }
                }
                target.documentInformation.title="StudyFuse · ${if(complete) "Çalışma notları" else "EKSİK DERLEME"}"
                target.documentInformation.creator="StudyFuse"
                target.save(assembled)
            }
            val expected=next-1+pageCount(reportPdf)
            verify(assembled,expected)
            progress(ProcessingProgress("PDF","Bütünlük denetimi ve ciltler",sources.size,sources.size))
            val artifacts=if(settings.volumeLimitMb>0 && assembled.length()>settings.volumeLimitMb*1024L*1024L)
                splitVolumes(assembled,settings.volumeLimitMb*1024L*1024L) else listOf(Artifact(assembled,expected,1,expected))
            ensureActive()
            val json=JSONObject().put("schemaVersion",1).put("complete",complete).put("mode",settings.mode.name)
                .put("settings",JSONObject(org.studyfuse.data.SettingsCodec.encode(settings)))
                .put("importedFiles",sources.size).put("successfulFiles",prepared.count { it.result.status=="OK" })
                .put("failedFiles",prepared.count { it.result.status=="FAILED" })
                .put("fallbackFiles",prepared.count { it.result.status=="FALLBACK" })
                .put("reviewFiles",prepared.count { it.result.status=="REVIEW" })
                .put("originalPages",prepared.sumOf { it.result.originalPages }).put("generatedPages",expected)
                .put("pageCountIncludes","İçindekiler, bölüm başlıkları, doğrulanmamış OCR ve rapor sayfaları")
                .put("verification","SHA-256, tam geometrik kapsama, çıktı sayfa sayısı ve güvenli / desteklenen sayfaların sınırlı çözünürlükte işlenebilirliği. Kodlayıcı veya bellek nedeniyle raster denetimi atlanan kaynaklar REVIEW olarak raporlanır. Görsel eşdeğerliğin veya kaynak uygulama dışa aktarımının eksiksizliğinin kanıtı değildir.")
                .put("files",JSONArray(prepared.map { it.result.json() })).put("outputs",JSONArray(artifacts.map { it.json() }))
                .put("fullPdf",assembled.path).put("createdUtc",java.time.Instant.now().toString())
            val report=File(runDirectory,"StudyFuse-rapor.json"); report.writeText(json.toString(2))
            Compilation(artifacts,report,reportText,complete,expected)
        } finally { scratch.deleteRecursively() }
    }

    private suspend fun addOcr(content:File,result:FileResult,progress:suspend(String)->Unit) {
        val ocrPdf=File(scratch,"${result.source.id}-ocr.pdf")
        val merged=File(scratch,"${result.source.id}-with-ocr.pdf")
        result.warnings+="OCR yalnızca basılı Latin metin içindir (Almanca, Türkçe, İngilizce). El yazısı ve denklemler desteklenmez. Kullanılabilir satır / sözcük güven puanları JSON raporundadır; doğruluk garantisi değildir. OCR ekleri doğrulanmamıştır."
        loadPdf(content,scratch).use { original ->
            newPdf(scratch).use { transcript ->
                val writer=PdfText(context,transcript)
                OcrEngine().use { ocr ->
                    for(i in 0 until original.numberOfPages) {
                        currentCoroutineContext().ensureActive(); progress("Basılı metin OCR · ${i+1}/${original.numberOfPages}")
                        val page=original.getPage(i); val box=page.cropBox
                        if(PdfRisks.rasterWarnings(page).isNotEmpty()) {
                            result.warnings+="OCR sayfa ${i+1}: kodlayıcı / bellek güvenliği nedeniyle uygulanmadı; orijinal görsel korunuyor."
                            continue
                        }
                        val scale=min(2.1f,1800f/max(box.width,box.height))
                        val bitmap=PDFRenderer(original).renderImage(i,scale)
                        try {
                            val recognized=ocr.read(bitmap);val text=recognized.text
                            result.ocr+=JSONObject().put("contentPage",i+1).put("confidence",JSONObject.NULL)
                                .put("confidenceScope","Motor sayfa düzeyinde güven puanı sağlamaz; kullanılabilir ham satır / sözcük puanları lines içindedir.")
                                .put("lines",recognized.lines).put("imageWidth",bitmap.width).put("imageHeight",bitmap.height)
                                .put("verified",false).put("engine","ML Kit bundled Latin 16.0.1").put("text",text)
                            if(text.isNotBlank()) {
                                val safe=writer.metadata(text)
                                writer.pages("DOĞRULANMAMIŞ OCR — İçerik sayfası ${i+1}\n"+
                                    "El yazısı / denklem tanıma değildir. Orijinal görseli esas alın.\n\n$safe")
                                if(safe!=text) result.warnings+="OCR sayfa ${i+1}: desteklenmeyen yazı karakterleri PDF ekinde U+ koduyla gösterildi; ham metin JSON raporundadır."
                            }
                        } catch(e:CancellationException) { throw e }
                        catch(e:Exception) { result.warnings+="OCR sayfa ${i+1} başarısız: ${e.message}; görsel sayfa korundu." }
                        finally { bitmap.recycle() }
                    }
                }
                if(transcript.numberOfPages==0) return
                transcript.save(ocrPdf)
            }
        }
        newPdf(scratch).use { doc ->
            val merger=PDFMergerUtility()
            loadPdf(content,scratch).use { merger.appendDocument(doc,it) }
            loadPdf(ocrPdf,scratch).use { merger.appendDocument(doc,it) }
            result.generatedPages=doc.numberOfPages; doc.save(merged)
        }
        check(content.delete() && merged.renameTo(content)) { "OCR ekleri kaydedilemedi" }
    }

    private fun metadataPdf(file:File,text:String):Int = newPdf(scratch).use { doc ->
        val writer=PdfText(context,doc); writer.pages(writer.metadata(text)); doc.save(file); doc.numberOfPages
    }
    private fun tocText(items:List<Prepared>,ranges:Boolean):String = buildString {
        append("STUDYFUSE — İÇİNDEKİLER\n\n")
        append("Her kaynak ayrı bir bölüm olarak sıralanmıştır. EKSİK veya inceleme gerektiren kaynaklar raporda gösterilir.\n\n")
        for((i,p) in items.withIndex()) {
            append("${i+1}. ${p.result.source.section}\nKaynak: ${p.result.source.name}\n")
            append(if(ranges) "Sayfalar: ${p.result.start}–${p.result.end}\n\n" else "Sayfalar: 000000–000000\n\n")
        }
    }
    private fun humanReport(items:List<FileResult>,complete:Boolean,s:ExportSettings)=buildString {
        append("STUDYFUSE — BÜTÜNLÜK RAPORU\n\n${if(complete) "İŞLEME TAMAMLANDI" else "EKSİK / İNCELEME GEREKLİ"}\n")
        append("İçe aktarılan: ${items.size}\nBaşarılı: ${items.count { it.status=="OK" }}\n")
        append("Başarısız: ${items.count { it.status=="FAILED" }}\nDış dönüşüm gereken: ${items.count { it.status=="FALLBACK" }}\n")
        append("İnceleme gereken: ${items.count { it.status=="REVIEW" }}\nOrijinal sayfa (bilinen): ${items.sumOf { it.originalPages }}\n")
        append("Üretilen kaynak sayfaları: ${items.sumOf { it.generatedPages }}\nMod: ${s.mode}\n\n")
        append("Toplam PDF sayfası (başlıklar ve rapor dahil): [OUTPUT_PAGES]\n\n")
        append("Denetimler: orijinal SHA-256, sayfa kapsama sınırları, sıra, çıktı sayfa sayısı ve tüm çıktı sayfalarının sınırlı çözünürlükte işlenmesi. " +
            "Bu denetimler her el yazısı işaretinin görsel eşdeğerliğini kanıtlamaz. Kaynak uygulamada önceden kesilmiş içerik geri getirilemez. " +
            "Orijinaller aygıtta tutulur. PDF dışındaki gömülü dosyalar, bağlantılar, medya, katmanlar ve imzalar için orijinali kullanın.\n\n")
        for(r in items) {
            append("${r.source.name}\nBölüm: ${r.source.section}\nDurum: ${r.status}\nSayfalar: ${r.start}–${r.end}\n")
            r.warnings.forEach { append("UYARI: $it\n") }; append("\n")
        }
    }
    private fun pageCount(file:File)=loadPdf(file,scratch).use { it.numberOfPages }
    private suspend fun verify(file:File,expected:Int):List<String> {
        val warnings=mutableListOf<String>()
        loadPdf(file,scratch).use { doc ->
            check(doc.numberOfPages==expected) { "Beklenen sayfa sayısı: $expected; bulunan: ${doc.numberOfPages}" }
            val renderer=PDFRenderer(doc)
            for(i in 0 until doc.numberOfPages) {
                currentCoroutineContext().ensureActive()
                val page=doc.getPage(i); val box=page.cropBox
                check(box.width.isFinite() && box.height.isFinite() && box.width>0 && box.height>0)
                val risks=PdfRisks.rasterWarnings(page)
                if(risks.isNotEmpty()) {warnings+=risks.map{"Sayfa ${i+1}: $it"};continue}
                val scale=min(.25f,256f/max(box.width,box.height))
                renderer.renderImage(i,scale).recycle()
            }
        }
        return warnings
    }
    private suspend fun splitVolumes(full:File,limit:Long):List<Artifact> {
        val outputs=mutableListOf<Artifact>()
        loadPdf(full,scratch).use { input ->
            suspend fun saveRange(start:Int,end:Int) {
                currentCoroutineContext().ensureActive()
                val candidate=File(scratch,"volume-$start-$end.pdf")
                newPdf(scratch).use { doc ->
                    val merger=PDFMergerUtility()
                    val cloner=com.tom_roush.pdfbox.multipdf.PDFCloneUtility(doc)
                    for(i in start..end) {
                        val source=input.getPage(i)
                        val page=doc.importPage(source)
                        page.resources=PDResources(cloner.cloneForNewDocument(source.resources.cosObject) as com.tom_roush.pdfbox.cos.COSDictionary)
                    }
                    doc.save(candidate)
                }
                if(candidate.length()>limit && end>start) {
                    candidate.delete(); val mid=(start+end)/2; saveRange(start,mid); saveRange(mid+1,end)
                } else {
                    val dest=File(runDirectory,"${full.nameWithoutExtension}-Cilt-%03d.pdf".format(outputs.size+1))
                    check(candidate.renameTo(dest))
                    verify(dest,end-start+1)
                    // A single unsplittable page may exceed the size limit; recorded in the volume manifest/UI.
                    outputs+=Artifact(dest,end-start+1,start+1,end+1)
                }
            }
            saveRange(0,input.numberOfPages-1)
        }
        check(outputs.sumOf { it.pages }==pageCount(full)) { "Cilt kapsamı doğrulanamadı" }
        return outputs
    }
}
