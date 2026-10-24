package org.studyfuse.core

import kotlin.math.*

enum class PageSize { A4_PORTRAIT, A4_LANDSCAPE, ORIGINAL, AUTO }
enum class ExportMode { ARCHIVE, AI_STUDY }
enum class Compression { LOSSLESS, JPEG_95, JPEG_90 }

data class ExportSettings(
    val mode: ExportMode = ExportMode.AI_STUDY,
    val pageSize: PageSize = PageSize.AUTO,
    val margin: Double = 24.0,
    val overlap: Double = 18.0,
    val compression: Compression = Compression.LOSSLESS,
    val ocr: Boolean = false,
    val volumeLimitMb: Int = 0
) {
    init {
        require(margin.isFinite() && margin in 12.0..72.0)
        require(overlap.isFinite() && overlap in 0.0..72.0)
        require(volumeLimitMb >= 0)
    }
}

/** All coordinates refer to the displayed source, with (0,0) at its top left. */
data class Slice(val left: Double, val top: Double, val right: Double, val bottom: Double) {
    val width get() = right - left
    val height get() = bottom - top
}
data class Layout(val width: Double, val height: Double, val scale: Double, val slices: List<Slice>)

object Pagination {
    fun plan(width: Double, height: Double, settings: ExportSettings,
             whitespaceRows: List<Double> = emptyList(), minimumScale:Double=0.0,
             manualCuts:List<Double> = emptyList()): Layout {
        require(width.isFinite() && height.isFinite() && width > 0 && height > 0)
        val m = settings.margin
        if (settings.pageSize == PageSize.ORIGINAL) {
            // Extra border reserves space for page numbers without overlaying source content.
            return Layout(width + 2*m, height + 2*m, 1.0, listOf(Slice(0.0, 0.0, width, height)))
        }
        val landscape = settings.pageSize == PageSize.A4_LANDSCAPE ||
            (settings.pageSize == PageSize.AUTO && (width / height > 1.2 ||
                (width<=1000.0 && minimumScale*width>595.2756-2*m)))
        val pw = if (landscape) 841.8898 else 595.2756
        val ph = if (landscape) 595.2756 else 841.8898
        val usableW = pw - 2*m
        val usableH = ph - 2*m
        // Retain the source's physical scale where possible. Oversized canvases tile in BOTH axes.
        require(minimumScale.isFinite() && minimumScale>=0)
        val scale = max(minimumScale, min(1.0, usableW / min(width, 1000.0)))
        val tileW = usableW / scale
        val tileH = usableH / scale
        val overlap = min(settings.overlap / scale, min(tileW, tileH) / 5.0)
        // Reject pathological canvas dimensions before materializing an unbounded slice list.
        // This budget depends on available heap, not the number of imported files.
        val estimatedSlices=ceil(width/(tileW*.5))*ceil(height/(tileH*.5))
        val safeSlices=(Runtime.getRuntime().maxMemory()/4096L).coerceAtMost(1_000_000L)
        require(estimatedSlices.isFinite() && estimatedSlices+manualCuts.size*ceil(width/tileW)*2<=safeSlices) {
            "Tuval, aygıtın güvenli sayfalama belleğini aşıyor. Kaynak uygulamada daha küçük PDF bölümlerine ayırın."
        }
        val xs = intervals(width, tileW, overlap)
        require(manualCuts.all {it.isFinite() && it>0 && it<height}) {"Bölme noktaları sayfa içinde olmalıdır"}
        val boundaries=listOf(0.0)+manualCuts.distinct().sorted()+height
        val ys=if(manualCuts.isEmpty()) intervals(height,tileH,overlap,whitespaceRows) else
            boundaries.zipWithNext().flatMap { (a,b) ->
                val from=max(0.0,a-overlap);val to=b
                intervals(to-from,tileH,overlap,whitespaceRows.map{it-from}).map{it.first+from to it.second+from}
            }
        val slices = ys.flatMap { y -> xs.map { x -> Slice(x.first, y.first, x.second, y.second) } }
        return Layout(pw, ph, scale, slices)
    }

    private fun intervals(length: Double, extent: Double, overlap: Double,
                          whitespace: List<Double> = emptyList()): List<Pair<Double, Double>> {
        val result = mutableListOf<Pair<Double, Double>>()
        var start = 0.0
        while (start < length - 1e-7) {
            val ideal = if(start+extent>=length-1e-7) length else start+extent
            var end = ideal
            if (ideal < length) {
                // Split earlier only: a later split could make the page exceed its printable bounds.
                val candidate = whitespace.filter { it in (start + extent * .78)..ideal }.maxOrNull()
                if (candidate != null) end = candidate
            }
            result += start to end
            if (end >= length) break
            start = max(start + extent * .5, end - overlap)
        }
        return result
    }

    /** A geometric proof of coverage, independent of the rendering implementation. */
    fun covers(width: Double, height: Double, slices: List<Slice>): Boolean {
        if (slices.isEmpty() || slices.any { it.left < -1e-5 || it.top < -1e-5 ||
                it.right > width + 1e-5 || it.bottom > height + 1e-5 || it.width <= 0 || it.height <= 0 }) return false
        val ys = (listOf(0.0, height) + slices.flatMap { listOf(it.top, it.bottom) }).distinct().sorted()
        return ys.zipWithNext().all { (a,b) ->
            val mid = (a + b)/2
            val spans = slices.filter { mid >= it.top && mid <= it.bottom }.sortedBy { it.left }
            var edge = 0.0
            for (s in spans) {
                if (s.left > edge + 1e-5) return@all false
                edge = max(edge, s.right)
            }
            edge >= width - 1e-5
        }
    }
}
