package org.studyfuse.engine

import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.text.PDFTextStripper
import com.tom_roush.pdfbox.text.TextPosition
import org.studyfuse.core.Slice
import kotlin.math.*

/** Filters by DISPLAYED bounds. PDFTextStripper otherwise ignores form clipping. */
object PageText {
    fun extract(doc:PDDocument,page:Int,bounds:Slice?=null,mapping:PageMapping?=null,seen:MutableSet<String>?=null):String {
        val box=doc.getPage(page).cropBox
        val limit=bounds ?: Slice(0.0,0.0,box.width.toDouble(),box.height.toDouble())
        val stripper=object:PDFTextStripper() {
            override fun processTextPosition(text:TextPosition) {
                val x=text.x.toDouble();val y=text.y.toDouble()
                // A glyph center must lie inside the actual visual clip. Preserve edge glyphs in the PDF itself.
                val cx=x+text.width/2;val cy=y-text.height/2
                if(cx<limit.left-.05 || cx>limit.right+.05 || cy<limit.top-.05 || cy>limit.bottom+.05) return
                if(mapping!=null && seen!=null) {
                    val sx=mapping.left+(x-limit.left)/mapping.scale
                    val sy=mapping.top+(y-limit.top)/mapping.scale
                    val key="${mapping.originalPage}:${round(sx*10).toLong()}:${round(sy*10).toLong()}:${text.unicode}"
                    if(!seen.add(key)) return
                }
                super.processTextPosition(text)
            }
        }
        stripper.startPage=page+1;stripper.endPage=page+1;stripper.sortByPosition=true
        return stripper.getText(doc).trim()
    }
    private fun fold(text:String)=text.lowercase(java.util.Locale.ROOT).replace("\u0307","").replace('ı','i').replace("ß","ss")
    fun matches(text:String,query:String):Boolean = fold(text).contains(fold(query))
}
