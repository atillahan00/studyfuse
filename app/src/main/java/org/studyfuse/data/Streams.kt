package org.studyfuse.data

import java.io.InputStream

/** InputStream.readNBytes is unavailable on Android 10–12. */
fun InputStream.readPrefix(length:Int):ByteArray {
    val buffer=ByteArray(length);var count=0
    while(count<length) {
        val n=read(buffer,count,length-count)
        if(n<0) break
        if(n==0) {val b=read();if(b<0) break;buffer[count++]=b.toByte()} else count+=n
    }
    return buffer.copyOf(count)
}
