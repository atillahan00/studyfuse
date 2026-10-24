package org.studyfuse.core

enum class Format(val extensions: Set<String>, val native: Boolean, val explanation: String) {
    PDF(setOf("pdf"), true, "Vektör PDF; metin ve çizimler korunur"),
    IMAGE(setOf("png","jpg","jpeg","webp","heic","heif","bmp"), true, "Android görüntü çözücüsü; destek aygıta bağlıdır"),
    TEXT(setOf("txt","md","markdown","csv"), true, "UTF-8 metin; Markdown ve CSV kaynak metni olarak korunur"),
    WORD(setOf("doc","docx","rtf","odt"), false, "Word veya LibreOffice ile PDF dışa aktarın"),
    SLIDES(setOf("ppt","pptx","odp"), false, "PowerPoint veya LibreOffice ile PDF dışa aktarın"),
    SHEET(setOf("xls","xlsx","ods"), false, "Tüm çalışma sayfalarını ve baskı alanlarını denetleyerek PDF dışa aktarın"),
    ONENOTE(setOf("one","onepkg","onetoc2"), false, "OneNote masaüstü uygulamasından PDF veya tam çözünürlüklü görüntü dışa aktarın"),
    HTML(setOf("html","htm","mhtml","mht"), false, "Tarayıcıda çevrimdışı açıp tüm kaynaklarla PDF yazdırın"),
    SVG(setOf("svg"), false, "Vektör destekli bir uygulamadan PDF dışa aktarın"),
    EPUB(setOf("epub"), false, "E-kitap okuyucudan PDF dışa aktarın; DRM kısıtlamaları geçerlidir"),
    TIFF(setOf("tif","tiff"), false, "Çok sayfalı TIFF dosyasının tüm karelerini PDF olarak dışa aktarın"),
    UNKNOWN(emptySet(), false, "Kaynak uygulamadan PDF dışa aktarın");
    companion object {
        fun detect(name: String, header: ByteArray): Format {
            if (header.take(5).toByteArray().contentEquals("%PDF-".toByteArray())) return PDF
            val ext = name.substringAfterLast('.', "").lowercase(java.util.Locale.ROOT)
            val byExt = entries.firstOrNull { ext in it.extensions } ?: UNKNOWN
            if (header.size >= 8 && header.take(8).toByteArray().contentEquals(byteArrayOf(-119,80,78,71,13,10,26,10))) return IMAGE
            if (header.size >= 3 && header[0] == (-1).toByte() && header[1] == (-40).toByte()) return IMAGE
            if (header.size >= 12 && String(header,0,4,Charsets.US_ASCII) == "RIFF" &&
                String(header,8,4,Charsets.US_ASCII) == "WEBP") return IMAGE
            return byExt
        }
    }
}
