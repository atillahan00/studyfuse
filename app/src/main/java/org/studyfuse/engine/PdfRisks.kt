package org.studyfuse.engine

import com.tom_roush.pdfbox.cos.*
import com.tom_roush.pdfbox.pdmodel.PDPage
import java.util.Collections
import java.util.IdentityHashMap

/** Inspect encoded resources without invoking unsupported or oversized image decoders. */
object PdfRisks {
    fun rasterWarnings(page:PDPage):List<String> {
        val warnings=linkedSetOf<String>()
        val seen=Collections.newSetFromMap(IdentityHashMap<COSDictionary,Boolean>())
        val budget=Runtime.getRuntime().maxMemory()/24
        fun visit(dict:COSDictionary?) {
            if(dict==null || !seen.add(dict)) return
            if(dict.getNameAsString(COSName.SUBTYPE)=="Image") {
                val filter=dict.getDictionaryObject(COSName.FILTER)
                val names=when(filter) {is COSName ->listOf(filter.name);is COSArray ->(0 until filter.size()).mapNotNull{(filter.getObject(it) as? COSName)?.name};else->emptyList()}
                if(names.any{it=="JPXDecode" || it=="JBIG2Decode"})
                    warnings+="JPX/JBIG2 görüntü kodlaması yerel doğrulayıcıda desteklenmiyor. Kodlanmış orijinal görüntü PDF'de korunur; Android/harici PDF önizlemesinde denetleyin."
                if(dict.getInt(COSName.WIDTH).toLong()*dict.getInt(COSName.HEIGHT)>budget)
                    warnings+="Gömülü görüntü güvenli raster doğrulama bütçesini aşıyor. Çözünürlüğü değiştirilmeden PDF'de korundu; görsel doğrulama gerekli."
                visit(dict.getDictionaryObject(COSName.SMASK) as? COSDictionary)
            }
            val resources=dict.getDictionaryObject(COSName.RESOURCES) as? COSDictionary
            if(resources!=null) visit(resources)
            for(key in listOf(COSName.XOBJECT,COSName.PATTERN)) {
                val objects=dict.getDictionaryObject(key) as? COSDictionary ?: continue
                for(name in objects.keySet()) visit(objects.getDictionaryObject(name) as? COSDictionary)
            }
        }
        visit(page.resources?.cosObject)
        return warnings.toList()
    }
}
