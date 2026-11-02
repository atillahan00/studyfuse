package org.studyfuse.engine

import android.graphics.Path
import android.graphics.PointF
import com.tom_roush.pdfbox.contentstream.PDFGraphicsStreamEngine
import com.tom_roush.pdfbox.contentstream.operator.Operator
import com.tom_roush.pdfbox.cos.*
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.font.PDFont
import com.tom_roush.pdfbox.pdmodel.font.PDType3Font
import com.tom_roush.pdfbox.pdmodel.graphics.image.PDImage
import com.tom_roush.pdfbox.util.Matrix
import com.tom_roush.pdfbox.util.Vector
import java.io.IOException

data class NativePageAssessment(val visualReasons:Set<String>) {
    fun canReadDirectly(text:String)=visualReasons.isEmpty() && text.count{!it.isWhitespace()}>=8 &&
        text.none{it=='\uFFFD' || Character.getType(it)==Character.PRIVATE_USE.toInt() || (it.isISOControl() && it !in "\n\r\t")}
}

/** Conservative paint inspection, not OCR or proof of semantic accuracy.
 * Visits nested forms and inline images without decoding pixels. Any painted path (including
 * underlines), image, annotation, unusual text or unknown operation retains visual inference.
 * Clip-only paths inserted by pagination are not drawings. We deliberately inspect the whole
 * underlying form, even outside its clip: false positives are safer than missing a diagram.
 */
object NativePageReading {
    fun inspect(page:PDPage):NativePageAssessment {
        val reasons=linkedSetOf<String>()
        try {
            if(page.annotations.isNotEmpty())reasons+="annotation"
            val inspector=object:PDFGraphicsStreamEngine(page) {
                private var point=PointF();private var start=PointF();private var operations=0
                override fun appendRectangle(p0:PointF,p1:PointF,p2:PointF,p3:PointF){point=p0;start=p0}
                override fun drawImage(image:PDImage){reasons+="image"}
                override fun clip(rule:Path.FillType){}
                override fun moveTo(x:Float,y:Float){point=PointF(x,y);start=point}
                override fun lineTo(x:Float,y:Float){point=PointF(x,y)}
                override fun curveTo(x1:Float,y1:Float,x2:Float,y2:Float,x3:Float,y3:Float){point=PointF(x3,y3)}
                override fun getCurrentPoint()=point
                override fun closePath(){point=start}
                override fun endPath(){}
                override fun strokePath(){reasons+="vector"}
                override fun fillPath(rule:Path.FillType){reasons+="vector"}
                override fun fillAndStrokePath(rule:Path.FillType){reasons+="vector"}
                override fun shadingFill(name:COSName){reasons+="shading"}
                override fun showGlyph(matrix:Matrix,font:PDFont,code:Int,displacement:Vector) {
                    val unicode=font.toUnicode(code)
                    if(font is PDType3Font || unicode.isNullOrEmpty() || unicode.contains('\uFFFD') ||
                        unicode.any{Character.getType(it)==Character.PRIVATE_USE.toInt()} ||
                        graphicsState.textState.renderingMode.intValue()!=0 ||
                        graphicsState.nonStrokingColor.colorSpace.name=="Pattern")reasons+="unusual_text"
                }
                override fun processOperator(operator:Operator,operands:MutableList<COSBase>) {
                    if(++operations>1_000_000)throw IOException("Inspection budget exceeded")
                    super.processOperator(operator,operands)
                    if(operator.name=="gs" && graphicsState.softMask!=null)reasons+="soft_mask"
                }
                override fun unsupportedOperator(operator:Operator,operands:MutableList<COSBase>){reasons+="unknown_operation"}
                override fun operatorException(operator:Operator,operands:MutableList<COSBase>,error:IOException){throw error}
            }
            inspector.processPage(page)
        }catch(_:Exception){reasons+="inspection_failed"}
        return NativePageAssessment(reasons)
    }
}
