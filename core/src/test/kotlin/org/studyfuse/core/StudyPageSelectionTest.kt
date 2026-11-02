package org.studyfuse.core

import org.junit.Assert.*
import org.junit.Test

class StudyPageSelectionTest {
    @Test fun rangesMergeWithoutDuplicateRequests(){val s=StudyPageSelection.parse("3, 5-7,6,8");assertEquals(listOf(3..3,5..8),s.ranges);assertEquals(5L,s.countInside(1,9));assertTrue(7 in s);assertFalse(4 in s)}
    @Test fun largeRangeIsNotExpanded(){val s=StudyPageSelection.parse("1-2147483647");assertEquals(1,s.ranges.size);assertEquals(2147483647L,s.countInside(1,Int.MAX_VALUE));assertFalse(s.coveredBy(listOf(3..5)))}
    @Test fun selectedPagesMustBeCoveredByActualSourcePages(){val s=StudyPageSelection.parse("3-7");assertTrue(s.coveredBy(listOf(3..4,5..7)));assertFalse(s.coveredBy(listOf(3..4,6..7)));assertTrue(StudyPageSelection.parse("").coveredBy(emptyList()))}
    @Test fun malformedSelectionsFailExplicitly(){for(v in listOf("0","5-3","3,","-1","2-x","2147483648")){try{StudyPageSelection.parse(v);fail("Accepted $v")}catch(_:Exception){}}}
}
