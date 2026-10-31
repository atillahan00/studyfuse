package org.studyfuse.core

import org.junit.Test
import org.junit.Assert.*
import java.io.*
import java.nio.file.Files
import java.security.MessageDigest
import java.util.concurrent.CancellationException

class ModelDownloadTest {
    private val content=ByteArray(8192){(it%251).toByte()}
    private fun artifact(bytes:ByteArray=content)=ModelArtifact("test","Test","https://example.com/model.litertlm",MessageDigest.getInstance("SHA-256").digest(bytes).joinToString(""){"%02x".format(it)},bytes.size.toLong(),8)
    private fun part()=Files.createTempDirectory("studyfuse-download").resolve("model.part").toFile()
    private fun response(code:Int,bytes:ByteArray,range:String?=null)=object:ModelResponse{
        override val status=code;override fun header(name:String)=if(name=="Content-Range")range else null
        override val body=ByteArrayInputStream(bytes);override fun close(){body.close()}
    }
    @Test fun interruptedDownloadResumesWithExactRangeAndDigest(){val file=part();var calls=0
        val download=ModelDownloader{_,offset->calls++;if(calls==1){assertEquals(0,offset);response(200,content.copyOfRange(0,2048))}else{assertEquals(2048,offset);response(206,content.copyOfRange(2048,8192),"bytes 2048-8191/8192")}}
        try{download.download(artifact(),file);fail("Truncation accepted")}catch(_:IOException){}
        assertEquals(2048,file.length());download.download(artifact(),file);assertArrayEquals(content,file.readBytes());file.parentFile.deleteRecursively()
    }
    @Test fun serverIgnoringRangeRestartsWithoutDuplicatingBytes(){val file=part().apply{writeBytes(content.copyOfRange(0,300))}
        ModelDownloader{_,offset->assertEquals(300,offset);response(200,content)}.download(artifact(),file)
        assertArrayEquals(content,file.readBytes());file.parentFile.deleteRecursively()
    }
    @Test fun mismatchedRangeCannotOverwritePreservedPrefix(){val file=part().apply{writeBytes(content.copyOfRange(0,300))}
        try{ModelDownloader{_,_->response(206,content,"bytes 0-8191/8192")}.download(artifact(),file);fail("Wrong range accepted")}catch(_:IllegalArgumentException){}
        assertEquals(300,file.length());file.parentFile.deleteRecursively()
    }
    @Test fun digestMismatchDeletesCorruptWeights(){val file=part();val bad=content.clone().apply{this[0]=99}
        try{ModelDownloader{_,_->response(200,bad)}.download(artifact(),file);fail("Corrupt model accepted")}catch(_:IOException){}
        assertFalse(file.exists());file.parentFile.deleteRecursively()
    }
    @Test fun completeVerifiedFileNeedsNoNetwork(){val file=part().apply{writeBytes(content)}
        ModelDownloader{_,_->throw AssertionError("Unexpected network access")}.download(artifact(),file)
        assertArrayEquals(content,file.readBytes());file.parentFile.deleteRecursively()
    }
    @Test fun cancellationKeepsPartialData(){val file=part().apply{writeBytes(content.copyOfRange(0,300))}
        try{ModelDownloader{_,_->throw AssertionError()}.download(artifact(),file,{throw CancellationException("pause")});fail()}catch(_:CancellationException){}
        assertEquals(300,file.length());file.parentFile.deleteRecursively()
    }
    @Test fun providerErrorKeepsPartialData(){val file=part().apply{writeBytes(content.copyOfRange(0,300))}
        try{ModelDownloader{_,_->response(403,byteArrayOf())}.download(artifact(),file);fail()}catch(_:IOException){}
        assertEquals(300,file.length());file.parentFile.deleteRecursively()
    }
    @Test fun httpsTransportRejectsCleartextBeforeOpeningSocket(){try{HttpsModelTransport().open("http://example.com/model",0);fail()}catch(_:IllegalArgumentException){}}
    @Test fun catalogUsesPinnedPublicVisionArtifacts(){assertEquals(2,ModelCatalog.models.size);for(m in ModelCatalog.models){assertTrue(m.url.matches(Regex("https://huggingface.co/litert-community/[^/]+/resolve/[0-9a-f]{40}/[^/]+\\.litertlm")));assertTrue(m.bytes>2_000_000_000);assertEquals(64,m.sha256.length)}}
}
