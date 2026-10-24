package org.studyfuse.core

import org.junit.Test
import org.junit.Assert.*
import java.time.LocalDate

class LearningTest {
    private val today=LocalDate.of(2026,10,24)
    @Test fun successfulRecallExpandsIntervals(){var p=RecallProgress();for(days in listOf(1L,3L,7L,14L,30L,60L,60L)){p=RecallSchedule.assess(p,RecallGrade.GOOD,today);assertEquals(today.plusDays(days).toEpochDay(),p.dueDay)};assertEquals(7,p.attempts)}
    @Test fun lapseResetsStageAndReturnsToday(){val p=RecallSchedule.assess(RecallProgress(5,0,10,2),RecallGrade.AGAIN,today);assertEquals(0,p.stage);assertEquals(today.toEpochDay(),p.dueDay);assertEquals(3,p.lapses)}
    @Test fun hardAnswerShortensNextInterval(){val p=RecallSchedule.assess(RecallProgress(4),RecallGrade.HARD,today);assertEquals(3,p.stage);assertEquals(today.plusDays(1).toEpochDay(),p.dueDay)}
    @Test fun optionalExamBoundsFutureReviewButPastDateDoesNotScheduleInPast(){assertEquals(today.plusDays(2).toEpochDay(),RecallSchedule.assess(RecallProgress(3),RecallGrade.GOOD,today,today.plusDays(2)).dueDay);assertTrue(RecallSchedule.assess(RecallProgress(),RecallGrade.GOOD,today,today.minusDays(1)).dueDay>today.toEpochDay())}
    @Test fun interleavePreservesAllItemsAndAlternatesTopics(){val items=listOf("A1","A2","A3","B1","B2","C1");assertEquals(listOf("A1","B1","C1","A2","B2","A3"),RecallSchedule.interleave(items){it.take(1)})}
    @Test fun documentsOnlyCannotAcceptModelKnowledge(){assertTrue(EvidenceRules.permitted(KnowledgeMode.DOCUMENTS_ONLY,"document"));assertFalse(EvidenceRules.permitted(KnowledgeMode.DOCUMENTS_ONLY,"ai_added"));assertFalse(EvidenceRules.permitted(KnowledgeMode.ENRICHED,"verified_web"));assertTrue(EvidenceRules.permitted(KnowledgeMode.ENRICHED,"ai_added"))}
    @Test fun evidenceRequiresActualContiguousWords(){assertTrue(EvidenceRules.contains("Die Zelle\n enthält DNA.","Zelle enthält DNA."));assertFalse(EvidenceRules.contains("Die Zelle enthält DNA.","enthält keine DNA"));assertFalse(EvidenceRules.contains("DNA","DNA"))}
    @Test fun detailedRegionsCoverWholePageAndOverlap(){val regions=VisionRegions.plan(true);assertEquals(5,regions.size);for(x in 0..100)for(y in 0..100)assertTrue(regions.drop(1).any{x/100.0 in it.left..it.right && y/100.0 in it.top..it.bottom});assertTrue(regions[1].right>regions[2].left);assertEquals(1,VisionRegions.plan(false).size)}
}
