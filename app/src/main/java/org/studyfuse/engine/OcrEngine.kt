package org.studyfuse.engine

import android.graphics.Bitmap
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.tasks.await
import org.json.JSONArray
import org.json.JSONObject

data class OcrResult(val text:String,val lines:JSONArray)

/** Bundled Latin PRINTED text model. No handwriting or mathematical recognition claim. */
class OcrEngine:AutoCloseable {
    private val client=TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
    suspend fun read(bitmap:Bitmap):OcrResult {
        val result=client.process(InputImage.fromBitmap(bitmap,0)).await()
        fun confidence(value:Float):Any = if(value.isFinite() && value in 0f..1f) value else JSONObject.NULL
        val lines=result.textBlocks.flatMap { it.lines }.map { line ->
            JSONObject().put("text",line.text).put("confidence",confidence(line.confidence))
                .put("angle",line.angle).put("language",line.recognizedLanguage)
                .put("bounds",line.boundingBox?.let {JSONArray(listOf(it.left,it.top,it.right,it.bottom))} ?: JSONObject.NULL)
                .put("elements",JSONArray(line.elements.map { element ->
                    JSONObject().put("text",element.text).put("confidence",confidence(element.confidence))
                }))
        }
        return OcrResult(result.text,JSONArray(lines))
    }
    override fun close()=client.close()
}
