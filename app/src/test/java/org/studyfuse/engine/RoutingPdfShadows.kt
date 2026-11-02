package org.studyfuse.engine

import android.graphics.Bitmap
import android.graphics.Matrix
import android.graphics.Rect
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import org.robolectric.annotation.Implementation
import org.robolectric.annotation.Implements
import org.robolectric.shadow.api.Shadow

/** JVM route tests use deterministic empty raster output. Actual PDF rendering is covered by
 * OnDeviceTest; this is not a pixel-fidelity test or a neural performance measurement. */
@Implements(PdfRenderer::class)
class RoutingPdfRendererShadow {
    @Implementation fun __constructor__(fd:ParcelFileDescriptor){}
    @Implementation fun openPage(index:Int):PdfRenderer.Page=Shadow.newInstanceOf(PdfRenderer.Page::class.java)
    @Implementation fun close(){}
}
@Implements(PdfRenderer.Page::class)
class RoutingPdfPageShadow {
    @Implementation fun getWidth()=595
    @Implementation fun getHeight()=842
    @Implementation fun render(bitmap:Bitmap,clip:Rect?,matrix:Matrix?,mode:Int){}
    @Implementation fun close(){}
}
