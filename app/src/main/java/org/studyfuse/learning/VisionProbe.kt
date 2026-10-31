package org.studyfuse.learning

import android.content.Context
import android.graphics.*
import java.io.File
import java.util.UUID

object VisionProbe {
    suspend fun run(context:Context,model:LocalModel,gpu:Boolean){
        val image=File(context.filesDir,"local-models/probe-${UUID.randomUUID()}.png")
        val token="SF"+UUID.randomUUID().toString().take(8).uppercase()
        val bitmap=Bitmap.createBitmap(1024,512,Bitmap.Config.ARGB_8888)
        try{val canvas=Canvas(bitmap);canvas.drawColor(Color.WHITE)
            canvas.drawText(token,70f,250f,Paint().apply{color=Color.BLACK;textSize=80f;isAntiAlias=true})
            image.outputStream().use{bitmap.compress(Bitmap.CompressFormat.PNG,100,it)}
        }finally{bitmap.recycle()}
        try {
            val answer=LocalAiClient(context).use{client->client.connect();client.generate(model,gpu,"Read the image literally.","Return only the exact alphanumeric code visible in the image.",image)}
            check(answer.filter{it.isLetterOrDigit()}.contains(token)){"Görüntü giriş testi geçmedi. Aygıt/model uyumluluğunu kontrol edin. Yanıt: $answer"}
        }finally{image.delete()}
    }
}
