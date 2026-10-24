package org.studyfuse.engine

import android.content.Context
import com.tom_roush.pdfbox.pdmodel.*
import com.tom_roush.pdfbox.pdmodel.common.PDRectangle
import com.tom_roush.pdfbox.pdmodel.font.PDType0Font
import java.io.File

/** Embedded Unicode fonts, codepoint-safe wrapping, and no silently dropped glyphs. */
class PdfText(context:Context,private val document:PDDocument) {
    private val regular=context.assets.open("fonts/NotoSans-Regular.ttf").use { PDType0Font.load(document,it) }
    private val math=context.assets.open("fonts/NotoSansMath-Regular.ttf").use { PDType0Font.load(document,it) }
    private val fonts=mutableMapOf<String,PDType0Font>()
    private val widths=mutableMapOf<String,Float>()
    private fun font(s:String):PDType0Font = fonts.getOrPut(s) { when {
        runCatching { regular.encode(s) }.isSuccess -> regular
        runCatching { math.encode(s) }.isSuccess -> math
        else -> error("PDF yazı tipinde bulunmayan karakter: U+${s.codePointAt(0).toString(16).uppercase()}; kaynak dosya korunuyor")
    } }
    fun metadata(text:String):String = buildString {
        for(cp in text.codePoints().toArray()) {
            val s=String(Character.toChars(cp))
            if(cp==10 || cp==13 || cp==9 || runCatching { font(s) }.isSuccess) append(s)
            else append("[U+${cp.toString(16).uppercase()}]")
        }
    }
    fun width(text:String,size:Float):Float {
        var w=0f
        text.codePoints().toArray().forEach { cp -> val s=String(Character.toChars(cp)); w+=widths.getOrPut(s) {font(s).getStringWidth(s)/1000f}*size }
        return w
    }
    fun draw(cs:PDPageContentStream,text:String,x:Float,y:Float,size:Float=10f) {
        var offset=x;var current:PDType0Font?=null;val run=StringBuilder()
        fun flush() {
            val f=current ?: return
            if(run.isEmpty()) return
            val value=run.toString()
            cs.beginText();cs.setFont(f,size);cs.newLineAtOffset(offset,y);cs.showText(value);cs.endText()
            offset+=f.getStringWidth(value)/1000f*size;run.clear()
        }
        for(cp in text.codePoints().toArray()) {
            val s=String(Character.toChars(cp)); val f=font(s)
            if(current!==f) {flush();current=f}
            run.append(s)
        }
        flush()
    }
    fun wrap(text:String,maxWidth:Float,size:Float=10f):List<String> {
        val lines=mutableListOf<String>(); var current=StringBuilder(); var w=0f
        for(cp in text.codePoints().toArray()) {
            if(cp==13) continue
            if(cp==10) { lines+=current.toString(); current=StringBuilder(); w=0f; continue }
            // Tabs have no fixed visual width; explicitly render four spaces. Other controls fail.
            val s=if(cp==9) "    " else String(Character.toChars(cp))
            val sw=width(s,size)
            if(w+sw>maxWidth && current.isNotEmpty()) { lines+=current.toString(); current=StringBuilder(); w=0f }
            current.append(s); w+=sw
        }
        lines+=current.toString()
        return lines
    }
    fun page(lines:List<String>,title:String?=null):PDPage {
        val page=PDPage(PDRectangle.A4); document.addPage(page)
        PDPageContentStream(document,page).use { cs ->
            var y=790f
            if(title!=null) { draw(cs,title,36f,y,15f); y-=28f }
            for(line in lines) { draw(cs,line,36f,y,10f); y-=15f }
        }
        return page
    }
    fun pages(text:String,title:String?=null):List<PDPage> {
        val lines=wrap(text,520f)
        return lines.chunked(if(title==null) 49 else 47).mapIndexed { i,chunk -> page(chunk,if(i==0) title else null) }
    }
}
