package org.studyfuse.engine

import android.content.Context
import android.graphics.*
import com.tom_roush.pdfbox.cos.*
import com.tom_roush.pdfbox.io.MemoryUsageSetting
import com.tom_roush.pdfbox.multipdf.PDFCloneUtility
import com.tom_roush.pdfbox.pdmodel.*
import com.tom_roush.pdfbox.pdmodel.common.*
import com.tom_roush.pdfbox.pdmodel.graphics.form.PDFormXObject
import com.tom_roush.pdfbox.pdmodel.graphics.image.*
import com.tom_roush.pdfbox.rendering.PDFRenderer
import com.tom_roush.pdfbox.text.PDFTextStripper
import com.tom_roush.pdfbox.text.TextPosition
import com.tom_roush.pdfbox.util.Matrix as PdfMatrix
import java.io.File
import java.nio.charset.CodingErrorAction
import kotlinx.coroutines.*
import org.studyfuse.core.*
import org.studyfuse.data.Source
import kotlin.math.*

interface ConversionAdapter {
    val format:Format
    suspend fun convert(source:Source,destination:File,settings:ExportSettings,result:FileResult,
        progress:suspend (String)->Unit)
}

fun newPdf(scratch:File):PDDocument=PDDocument(MemoryUsageSetting.setupTempFileOnly().setTempDir(scratch))
fun loadPdf(file:File,scratch:File):PDDocument=PDDocument.load(file,MemoryUsageSetting.setupTempFileOnly().setTempDir(scratch))

class PdfAdapter(private val context:Context,private val scratch:File):ConversionAdapter {
    override val format=Format.PDF
    override suspend fun convert(source:Source,destination:File,settings:ExportSettings,result:FileResult,
        progress:suspend(String)->Unit) {
        loadPdf(File(source.activePath),scratch).use { input ->
            require(!input.isEncrypted) { "Şifreli PDF desteklenmiyor. Kaynak uygulamada şifresiz kopyasını dışa aktarın." }
            require(input.numberOfPages>0) { "PDF sayfa içermiyor" }
            result.originalPages=input.numberOfPages
            val textSizes=runCatching { textSizes(input) }.getOrElse {
                result.warnings+="Metin boyutu analizi yapılamadı; görsel sayfalama kullanıldı.";emptyMap()
            }
            val names=input.documentCatalog.cosObject.getDictionaryObject(COSName.NAMES) as? COSDictionary
            if(names?.getDictionaryObject(COSName.EMBEDDED_FILES)!=null || input.documentCatalog.cosObject.containsKey(COSName.getPDFName("AF"))) {
                result.status="REVIEW"; result.warnings+="PDF gömülü ekler içeriyor. Ekler görünür sayfalara dönüştürülmedi; orijinal dosyada korunuyor."
            }
            if(input.documentCatalog.ocProperties!=null) {
                result.status="REVIEW"; result.warnings+="PDF katmanlar içeriyor; katman görünürlük durumları bağımsız olarak doğrulanamadı. Orijinali denetleyin."
            }
            if(input.documentCatalog.acroForm!=null) {
                input.documentCatalog.acroForm.flatten()
                result.warnings+="Form alanları görsel olarak sabitlendi; imzalar ve etkileşimli alanlar çıktı için geçerli değildir."
            }
            newPdf(scratch).use { output ->
                val cloner=PDFCloneUtility(output)
                for(index in 0 until input.numberOfPages) {
                    currentCoroutineContext().ensureActive(); progress("PDF · ${index+1}/${input.numberOfPages}")
                    val page=input.getPage(index)
                    flattenAnnotations(input,page,result,index+1)
                    val media=page.mediaBox; val crop=page.cropBox
                    val box=PDRectangle(min(media.lowerLeftX,crop.lowerLeftX),min(media.lowerLeftY,crop.lowerLeftY),
                        max(media.upperRightX,crop.upperRightX)-min(media.lowerLeftX,crop.lowerLeftX),
                        max(media.upperRightY,crop.upperRightY)-min(media.lowerLeftY,crop.lowerLeftY))
                    if(media!=crop && (media.width!=crop.width || media.height!=crop.height || media.lowerLeftX!=crop.lowerLeftX || media.lowerLeftY!=crop.lowerLeftY))
                        result.warnings+="Sayfa ${index+1}: CropBox yerine MediaBox ile birleşimi korundu; gizli kenarlar görünebilir."
                    val unit=page.userUnit.toDouble()
                    require(unit.isFinite() && unit>0) { "Geçersiz PDF UserUnit" }
                    val rotation=((page.rotation%360)+360)%360
                    require(rotation%90==0) { "Desteklenmeyen sayfa dönüşü" }
                    val w=(if(rotation==90 || rotation==270) box.height else box.width)*unit
                    val h=(if(rotation==90 || rotation==270) box.width else box.height)*unit
                    val form=PDFormXObject(PDStream(output,page.contents,COSName.FLATE_DECODE)).apply {
                        bBox=box
                        resources=page.resources?.let { PDResources(cloner.cloneForNewDocument(it.cosObject) as COSDictionary) } ?: PDResources()
                        page.cosObject.getDictionaryObject(COSName.GROUP)?.let { cosObject.setItem(COSName.GROUP,cloner.cloneForNewDocument(it)) }
                    }
                    val llx=box.lowerLeftX; val lly=box.lowerLeftY; val bw=box.width; val bh=box.height; val u=unit.toFloat()
                    val display=when(rotation) {
                        90 -> PdfMatrix(0f,-u,u,0f,-lly*u,(llx+bw)*u)
                        180 -> PdfMatrix(-u,0f,0f,-u,(llx+bw)*u,(lly+bh)*u)
                        270 -> PdfMatrix(0f,u,-u,0f,(lly+bh)*u,-llx*u)
                        else -> PdfMatrix(u,0f,0f,u,-llx*u,-lly*u)
                    }
                    val rasterWarnings=PdfRisks.rasterWarnings(page)
                    if(rasterWarnings.isNotEmpty()) {result.status="REVIEW";result.warnings+=rasterWarnings.map{"Sayfa ${index+1}: $it"}}
                    val whitespace=runCatching { if(rasterWarnings.isEmpty()) findWhitespace(input,index,w,h) else emptyList() }.getOrElse {
                        result.warnings+="Sayfa ${index+1}: boşluk analizi yapılamadı; örtüşmeli geometrik bölme kullanıldı."; emptyList()
                    }
                    val textSize=textSizes[index]?.times(unit)
                    val minScale=if(textSize!=null && settings.pageSize!=PageSize.ORIGINAL) (8.0/textSize).coerceAtMost(4.0) else 0.0
                    val plan=Pagination.plan(w,h,settings,whitespace,minScale)
                    if(textSize!=null && plan.scale*textSize<8.0)
                        result.warnings+="Sayfa ${index+1}: küçük metin algılandı (${(textSize*plan.scale).toInt()} pt). Önizlemede yakınlaştırarak denetleyin."
                    if(plan.slices.size>1) result.warnings+="Sayfa ${index+1}: ${plan.slices.size} örtüşmeli bölüme ayrıldı. Bazı PDF metin çıkarıcıları kırpma sınırları dışındaki vektör metnini tekrarlayabilir; görsel sayfa sınırlarını esas alın."
                    check(Pagination.covers(w,h,plan.slices)) { "Sayfa kapsama denetimi başarısız" }
                    for(slice in plan.slices) {
                        currentCoroutineContext().ensureActive()
                        val target=PDPage(PDRectangle(plan.width.toFloat(),plan.height.toFloat())); output.addPage(target)
                        val scale=plan.scale.toFloat(); val m=settings.margin.toFloat()
                        PDPageContentStream(output,target).use { cs ->
                            cs.addRect(m,(plan.height-settings.margin-slice.height*plan.scale).toFloat(),
                                (slice.width*plan.scale).toFloat(),(slice.height*plan.scale).toFloat()); cs.clip()
                            cs.transform(PdfMatrix(scale,0f,0f,scale,(settings.margin-slice.left*plan.scale).toFloat(),
                                (plan.height-settings.margin+(slice.top-h)*plan.scale).toFloat()))
                            cs.transform(display); cs.drawForm(form)
                        }
                        result.mappings+=PageMapping(index+1,output.numberOfPages,slice.left,slice.top,slice.right,slice.bottom,w,h,plan.scale)
                    }
                }
                result.generatedPages=output.numberOfPages
                output.save(destination)
            }
        }
    }
    private fun textSizes(doc:PDDocument):Map<Int,Double> {
        val sizes=mutableMapOf<Int,MutableList<Float>>()
        val stripper=object:PDFTextStripper() {
            override fun processTextPosition(text:TextPosition) {
                if(text.unicode?.isNotBlank()!=true) return
                val list=sizes.getOrPut(currentPageNo-1) {mutableListOf()}
                if(list.size<10000 && text.fontSizeInPt>.1f) list+=text.fontSizeInPt
                // Do not retain glyphs in PDFTextStripper's page-sized text buffers.
            }
        }
        stripper.writeText(doc,object:java.io.Writer() {
            override fun write(cbuf:CharArray,off:Int,len:Int) {}
            override fun flush() {};override fun close() {}
        })
        return sizes.mapValues {(_,v)->v.sorted()[v.size/2].toDouble()}
    }
    private fun findWhitespace(doc:PDDocument,index:Int,w:Double,h:Double):List<Double> {
        // Bounded thumbnail analysis only; the exported document remains vector based.
        val page=doc.getPage(index)
        if(max(w,h)<1500) return emptyList()
        val scale=(min(1200.0/max(w,h),sqrt(1_000_000.0/(w*h)))*page.userUnit).toFloat()
        val bitmap=PDFRenderer(doc).renderImage(index,scale)
        return try {
            val rows=mutableListOf<Double>(); val pixels=IntArray(bitmap.width)
            for(y in 1 until bitmap.height-1) {
                bitmap.getPixels(pixels,0,bitmap.width,0,y,bitmap.width,1)
                if(pixels.count { min(Color.red(it),min(Color.green(it),Color.blue(it)))<236 }<=max(1,bitmap.width/300))
                    rows+=y*h/bitmap.height
            }
            rows
        } finally { bitmap.recycle() }
    }
    private fun flattenAnnotations(doc:PDDocument,page:PDPage,result:FileResult,index:Int) {
        val annotations=page.annotations.toList()
        if(annotations.isEmpty()) return
        PDPageContentStream(doc,page,PDPageContentStream.AppendMode.APPEND,true,true).use { cs ->
            for(a in annotations) {
                a.contents?.takeIf { it.isNotBlank() }?.let { result.warnings+="Sayfa $index, açıklama (${a.subtype}): $it" }
                if(a.isHidden || a.isInvisible || a.isNoView) {
                    result.warnings+="Sayfa $index: gizli açıklama orijinal dosyada korunuyor."; continue
                }
                var appearance=a.normalAppearanceStream
                if(appearance==null) { runCatching { a.constructAppearances(doc) }; appearance=a.normalAppearanceStream }
                if(appearance==null) {
                    if(a.subtype!="Link" && a.subtype!="Popup") error("Sayfa $index: ${a.subtype} açıklamasının görünümü yok; görsel kaybı önlemek için kaynak dönüştürülmedi")
                    result.warnings+="Sayfa $index: ${a.subtype} etkileşimi orijinal PDF'de bulunur."; continue
                }
                val r=a.rectangle ?: error("Açıklama konumu geçersiz")
                val b=appearance.bBox ?: error("Açıklama sınırları geçersiz")
                val at=appearance.matrix
                val points=listOf(at.transformPoint(b.lowerLeftX,b.lowerLeftY),at.transformPoint(b.upperRightX,b.lowerLeftY),
                    at.transformPoint(b.lowerLeftX,b.upperRightY),at.transformPoint(b.upperRightX,b.upperRightY))
                val minX=points.minOf { it.x }; val maxX=points.maxOf { it.x }
                val minY=points.minOf { it.y }; val maxY=points.maxOf { it.y }
                check(maxX>minX && maxY>minY) { "Açıklama dönüşümü geçersiz" }
                val sx=r.width/(maxX-minX); val sy=r.height/(maxY-minY)
                cs.saveGraphicsState(); cs.transform(PdfMatrix(sx,0f,0f,sy,r.lowerLeftX-minX*sx,r.lowerLeftY-minY*sy)); cs.drawForm(appearance); cs.restoreGraphicsState()
            }
        }
        page.annotations=emptyList()
    }
}

class ImageAdapter(private val scratch:File):ConversionAdapter {
    override val format=Format.IMAGE
    override suspend fun convert(source:Source,destination:File,settings:ExportSettings,result:FileResult,
        progress:suspend(String)->Unit) {
        ImageSource(File(source.activePath)).use { image ->
            result.originalPages=1
            val factor=ImageSource.POINTS_PER_PIXEL
            val w=image.width*factor; val h=image.height*factor
            val whitespace=runCatching { image.whitespaceRows() }.getOrElse {
                result.warnings+="Boşluk analizi yapılamadı; örtüşmeli bölme kullanıldı."; emptyList()
            }
            val plan=Pagination.plan(w,h,settings,whitespace)
            check(Pagination.covers(w,h,plan.slices))
            newPdf(scratch).use { output ->
                for((i,slice) in plan.slices.withIndex()) {
                    currentCoroutineContext().ensureActive(); progress("Görüntü · ${i+1}/${plan.slices.size}")
                    val pixels=Rect(floor(slice.left/factor).toInt(),floor(slice.top/factor).toInt(),
                        ceil(slice.right/factor).toInt().coerceAtMost(image.width),ceil(slice.bottom/factor).toInt().coerceAtMost(image.height))
                    val bitmap=image.region(pixels)
                    try {
                        val xobject=when(settings.compression) {
                            Compression.LOSSLESS -> LosslessFactory.createFromImage(output,bitmap)
                            Compression.JPEG_95 -> JPEGFactory.createFromImage(output,whiteBitmap(bitmap),.95f)
                            Compression.JPEG_90 -> JPEGFactory.createFromImage(output,whiteBitmap(bitmap),.90f)
                        }
                        val page=PDPage(PDRectangle(plan.width.toFloat(),plan.height.toFloat())); output.addPage(page)
                        val s=plan.scale*factor
                        PDPageContentStream(output,page).use { cs ->
                            // Integer pixel rounding adds at most one pixel of overlap; no pixel is discarded.
                            cs.drawImage(xobject,settings.margin.toFloat(),
                                (plan.height-settings.margin-bitmap.height*s).toFloat(),(bitmap.width*s).toFloat(),(bitmap.height*s).toFloat())
                        }
                        result.mappings+=PageMapping(1,output.numberOfPages,slice.left,slice.top,slice.right,slice.bottom,w,h,plan.scale)
                    } finally { bitmap.recycle() }
                }
                result.generatedPages=output.numberOfPages; output.save(destination)
            }
            if(settings.compression!=Compression.LOSSLESS) result.warnings+="JPEG sıkıştırması seçildi; ince çizgileri dışa aktarmadan önce denetleyin."
        }
    }
    private fun whiteBitmap(bitmap:Bitmap):Bitmap {
        // White is drawn behind transparent pixels in place to avoid holding two full-resolution regions.
        if(bitmap.isMutable) { Canvas(bitmap).drawColor(Color.WHITE,PorterDuff.Mode.DST_OVER); return bitmap }
        return bitmap // Android JPEG encoding uses a solid background; lossless is the default.
    }
}

class TextAdapter(private val context:Context,private val scratch:File):ConversionAdapter {
    override val format=Format.TEXT
    override suspend fun convert(source:Source,destination:File,settings:ExportSettings,result:FileResult,
        progress:suspend(String)->Unit) {
        val decoder=Charsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT)
        newPdf(scratch).use { output ->
            val writer=PdfText(context,output)
            val lines=mutableListOf<String>()
            // Bounded reader: text is streamed in codepoint-safe chunks, never read as one whole file.
            java.io.InputStreamReader(File(source.activePath).inputStream(),decoder).buffered().use { reader ->
                val chunk=CharArray(2048); var pending=""
                while(true) {
                    currentCoroutineContext().ensureActive()
                    val n=reader.read(chunk); if(n<0) break
                    var text=pending+String(chunk,0,n); pending=""
                    if(text.lastOrNull()?.isHighSurrogate()==true) { pending=text.takeLast(1); text=text.dropLast(1) }
                    if(output.numberOfPages==0 && lines.isEmpty()) text=text.removePrefix("\uFEFF")
                    val parts=writer.wrap(text,520f)
                    // Retain the last visual line so chunk boundaries never insert artificial line breaks.
                    if(lines.isNotEmpty()) {
                        val previous=lines.removeAt(lines.lastIndex)
                        lines+=writer.wrap(previous+(parts.firstOrNull() ?: ""),520f)
                        lines+=parts.drop(1)
                    } else lines+=parts
                    while(lines.size>49) { writer.page(lines.take(49)); repeat(49) { lines.removeAt(0) }; progress("Metin · ${output.numberOfPages} sayfa") }
                }
                if(pending.isNotEmpty()) error("Geçersiz Unicode metni")
            }
            writer.page(lines.ifEmpty { listOf("") }); result.generatedPages=output.numberOfPages
            result.originalPages=0 // Plain text has no original page count.
            result.warnings+="UTF-8 kaynak metni yeniden akıtıldı; Markdown işaretleri ve CSV hücre ayırıcıları olduğu gibi gösterilir."
            output.save(destination)
        }
    }
}

/** Dedicated, explicit external conversion adapters; never return a fictitious success. */
class ExternalPdfAdapter(override val format:Format):ConversionAdapter {
    override suspend fun convert(source:Source,destination:File,settings:ExportSettings,result:FileResult,
        progress:suspend(String)->Unit):Unit = throw RequiresConversion(format.explanation)
}
class RequiresConversion(message:String):Exception(message)
