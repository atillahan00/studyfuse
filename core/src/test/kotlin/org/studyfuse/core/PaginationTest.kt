package org.studyfuse.core

import org.junit.Assert.*
import org.junit.Test
import kotlin.random.Random

class PaginationTest {
    @Test fun tallOneNoteHasCompleteCoverage() {
        val p = Pagination.plan(1000.0,18000.0, ExportSettings())
        assertTrue(p.slices.size > 10)
        assertTrue(Pagination.covers(1000.0,18000.0,p.slices))
        assertEquals(18000.0, p.slices.last().bottom, 1e-6)
        p.slices.forEach { assertTrue(it.height*p.scale <= p.height-48.0+1e-5) }
    }
    @Test fun naturalWhitespaceNeverOverflows() {
        val p = Pagination.plan(595.0,3000.0,ExportSettings(),listOf(700.0,1400.0,2100.0))
        assertEquals(700.0,p.slices.first().bottom,0.0)
        assertTrue(Pagination.covers(595.0,3000.0,p.slices))
    }
    @Test fun normalPageIsOneSlice() {
        val p=Pagination.plan(595.0,842.0, ExportSettings())
        // Full-width A4 fits one page, including a small downscale for reserved border.
        assertEquals(1,p.slices.size)
    }
    @Test fun wideCanvasTilesHorizontally() {
        val p=Pagination.plan(8000.0,18000.0,ExportSettings())
        assertTrue(p.slices.any { it.left > 0 })
        assertTrue(Pagination.covers(8000.0,18000.0,p.slices))
    }
    @Test fun originalRetainsDimensionsAndBorder() {
        val p=Pagination.plan(1000.0,18000.0, ExportSettings(pageSize=PageSize.ORIGINAL))
        assertEquals(1,p.slices.size); assertEquals(18048.0,p.height,0.0)
    }
    @Test fun randomizedCoverageAndBounds() {
        val rng=Random(923)
        repeat(1000) {
            val w=rng.nextDouble(20.0,10000.0); val h=rng.nextDouble(20.0,20000.0)
            val p=Pagination.plan(w,h,ExportSettings(margin=rng.nextDouble(12.0,72.0)))
            assertTrue(Pagination.covers(w,h,p.slices))
            p.slices.forEach { assertTrue(it.width*p.scale <= p.width-24.0+1e-5) }
        }
    }
    @Test fun coverageRejectsMissingStrip() {
        assertFalse(Pagination.covers(100.0,100.0,listOf(Slice(0.0,0.0,100.0,49.0), Slice(0.0,50.0,100.0,100.0))))
    }
    @Test fun overlapAndOrdering() {
        val p=Pagination.plan(1000.0,18000.0,ExportSettings())
        p.slices.zipWithNext().forEach { (a,b)-> assertTrue(b.top<a.bottom); assertTrue(b.top>a.top) }
    }
    @Test fun rejectsInvalidDimensions() {
        assertThrows(IllegalArgumentException::class.java) { Pagination.plan(Double.NaN,12.0,ExportSettings()) }
    }
    @Test fun pathologicalCanvasesFailBeforeUnboundedAllocation() {
        assertThrows(IllegalArgumentException::class.java) {
            Pagination.plan(1e15,1e15,ExportSettings())
        }
    }
    @Test fun smallTextSelectsLandscapeBeforeHorizontalTiling() {
        val p=Pagination.plan(1000.0,18000.0,ExportSettings(),minimumScale=8.0/12)
        assertTrue(p.width>p.height)
        assertTrue(p.scale*12>=8)
        assertTrue(Pagination.covers(1000.0,18000.0,p.slices))
        assertTrue(p.slices.all {it.left==0.0})
    }
    @Test fun knownFormatsAndUnicodeNames() {
        assertEquals(Format.PDF,Format.detect("öğrenci.bin","%PDF-1.7".toByteArray()))
        assertEquals(Format.SLIDES,Format.detect("Biologie.PPTX",byteArrayOf()))
        assertFalse(Format.ONENOTE.native)
        assertEquals(Format.TEXT,Format.detect("ğüşıöç_äöüß.md",byteArrayOf()))
    }
}
