package org.studyfuse.core

import org.junit.Test
import org.junit.Assert.*

class StudyPlanningTest {
    @Test fun searchFindsGermanTurkishAndExcludesUnrelatedPages(){val passages=listOf(EvidencePassage("a","Mitochondrien liefern Energie.","Zelle",3,"bio.pdf"),EvidencePassage("b","IŞIK ve ışık kırılması.","Fizik",8,"fizik.pdf"),EvidencePassage("c","Wahlen im Parlament.","Politik",12,"politik.pdf"))
        assertEquals("a",StudySearch.retrieve(passages,"Mitochondrien").first().id);assertEquals("b",StudySearch.retrieve(passages,"ışık").first().id)
        assertTrue(StudySearch.retrieve(passages,"unbekannteswort").isEmpty())
    }
    @Test fun retrievalRespectsContextBudgetWithoutClippingPassages(){val passages=(1..10).map{EvidencePassage("$it","Zelle "+"a".repeat(80),"Biologie",it,"bio.pdf")}
        val result=StudySearch.retrieve(passages,"Zelle",6,200);assertTrue(result.sumOf{it.text.length}<=200);assertTrue(result.all{it.text.length==86})
        val sameNames=passages.mapIndexed{i,p->p.copy(source="notes.pdf",sourceId=if(i<8)"teacher" else "book")}
        assertEquals(setOf("teacher","book"),StudySearch.retrieve(sameNames,"Zelle",2,200,true).map{it.sourceId}.toSet())
    }
    @Test fun plannerPrioritizesDueWorkWithinAvailableTime(){val plan=StudyPlanner.daily(listOf("A","B","C"),mapOf("B" to 10),mapOf("A" to 3),15)
        assertEquals("B",plan.first().topic);assertEquals(5,plan.sumOf{it.questions});assertEquals(0,StudyPlanner.remainingSeconds(100,200));assertEquals(1,StudyPlanner.remainingSeconds(2000,1000))
    }
}
