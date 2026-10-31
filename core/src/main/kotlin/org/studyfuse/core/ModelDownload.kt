package org.studyfuse.core

import java.io.*
import java.net.*
import java.security.MessageDigest

data class ModelArtifact(val id:String,val name:String,val url:String,val sha256:String,val bytes:Long,val memoryGb:Int) {
    init {require(id.matches(Regex("[a-z0-9-]+")));require(sha256.matches(Regex("[0-9a-f]{64}")));require(bytes>0)}
}
/** Public Apache-2.0 mobile artifacts; publisher commit, exact size and SHA are pinned. */
object ModelCatalog {
    val models=listOf(
        ModelArtifact("gemma4-e2b","Gemma 4 E2B · Daha küçük","https://huggingface.co/litert-community/gemma-4-E2B-it-litert-lm/resolve/6e5c4f1e395deb959c494953478fa5cec4b8008f/gemma-4-E2B-it.litertlm",
            "181938105e0eefd105961417e8da75903eacda102c4fce9ce90f50b97139a63c",2588147712L,8),
        ModelArtifact("gemma4-e4b","Gemma 4 E4B · Daha güçlü aday","https://huggingface.co/litert-community/gemma-4-E4B-it-litert-lm/resolve/28299f30ee4d43294517a4ac93abd6163412f07f/gemma-4-E4B-it.litertlm",
            "0b2a8980ce155fd97673d8e820b4d29d9c7d99b8fa6806f425d969b145bd52e0",3659530240L,12))
    fun get(id:String)=models.single{it.id==id}
}
interface ModelResponse:Closeable {val status:Int;fun header(name:String):String?;val body:InputStream}
fun interface ModelTransport {fun open(url:String,offset:Long):ModelResponse}
class HttpsModelTransport:ModelTransport {
    override fun open(url:String,offset:Long):ModelResponse {
        var current=URI(url)
        repeat(8){
            require(current.scheme=="https" && !current.host.isNullOrBlank() && current.userInfo==null){"Yalnızca HTTPS model indirmesi desteklenir"}
            val c=current.toURL().openConnection() as HttpURLConnection
            c.instanceFollowRedirects=false;c.connectTimeout=30_000;c.readTimeout=30_000
            c.setRequestProperty("Accept-Encoding","identity");c.setRequestProperty("User-Agent","StudyFuse-model-setup/1.3")
            if(offset>0)c.setRequestProperty("Range","bytes=$offset-")
            try {
                val status=c.responseCode
                if(status in listOf(301,302,303,307,308)){val target=c.getHeaderField("Location") ?: error("İndirme yönlendirmesi eksik");current=current.resolve(target);c.disconnect()}
                else return object:ModelResponse {
                    override val status=status
                    override fun header(name:String)=c.getHeaderField(name)
                    override val body:InputStream get()=c.inputStream
                    override fun close(){c.disconnect()}
                }
            }catch(t:Throwable){c.disconnect();throw t}
        }
        error("Çok fazla model yönlendirmesi")
    }
}
class ModelDownloader(private val transport:ModelTransport=HttpsModelTransport()) {
    /** Pauses preserve bytes. A changed/corrupt payload is discarded, never selected as a model. */
    fun download(model:ModelArtifact,part:File,check:()->Unit={},progress:(Long)->Unit={}):File {
        part.parentFile!!.mkdirs()
        if(part.length()>model.bytes)part.delete()
        var offset=part.length();check();progress(offset)
        if(offset<model.bytes)transport.open(model.url,offset).use{response->
            if(response.status !in setOf(200,206))throw IOException("Model sunucusu HTTP ${response.status}; internet/erişim durumunu kontrol edin")
            if(response.status==206){
                val range=Regex("bytes (\\d+)-(\\d+)/(\\d+)").matchEntire(response.header("Content-Range") ?: "") ?: throw IOException("Geçersiz devam yanıtı")
                val (start,end,total)=range.destructured
                require(start.toLong()==offset && end.toLong()>=offset && end.toLong()<model.bytes && total.toLong()==model.bytes){"Model aralık/boyut bilgisi değişmiş"}
            }else if(offset>0){offset=0}
            response.body.use{input->FileOutputStream(part,offset>0).use{out->
                val buffer=ByteArray(1024*1024)
                while(true){check();val n=input.read(buffer);if(n<0)break
                    require(offset+n<=model.bytes){"Model bildirilen boyutu aşıyor"}
                    if(part.parentFile!!.usableSpace<n+64L*1024*1024)throw IOException("Model için yeterli boş alan yok")
                    out.write(buffer,0,n);offset+=n;progress(offset)
                };out.fd.sync()
            }}
        }
        if(part.length()!=model.bytes)throw IOException("İndirme yarıda kaldı; tamamlanan ${part.length()} bayttan sürdürülebilir")
        val hash=MessageDigest.getInstance("SHA-256")
        part.inputStream().use{input->val buffer=ByteArray(1024*1024);while(true){check();val n=input.read(buffer);if(n<0)break;hash.update(buffer,0,n)}}
        if(hash.digest().joinToString(""){"%02x".format(it)}!=model.sha256){part.delete();throw IOException("Model SHA-256 doğrulaması başarısız; bozuk indirme silindi")}
        return part
    }
}
