package org.studyfuse.engine

import androidx.test.core.app.ApplicationProvider
import org.json.*
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.studyfuse.StudyFuseApp
import org.studyfuse.core.*
import org.studyfuse.learning.*
import java.io.File
import java.util.UUID

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[34],application=StudyFuseApp::class)
class StudyToolsTest {
    private fun tools()=StudyTools(ExamStore(File(ApplicationProvider.getApplicationContext<StudyFuseApp>().filesDir,"projects/tools-${UUID.randomUUID()}").apply{mkdirs()}))
    private val passage=EvidencePassage("source","Die Zelle enthält DNA.","Biologie",3,"Zelle.pdf")
    private val signature="a".repeat(64)
    private fun response(extra:String="",id:String="source",quote:String=passage.text)=JSONObject().put("answer","Die Zelle enthält DNA.")
        .put("additional",extra).put("uncertainties",JSONArray()).put("citations",JSONArray().put(JSONObject().put("id",id).put("quote",quote))).put("cards",JSONArray())
    private fun card(id:String="b".repeat(64),section:String="Biologie")=JSONObject().put("id",id).put("kind","recall").put("origin","document").put("question","Was enthält die Zelle?")
        .put("answer","DNA").put("evidence",passage.text).put("section",section).put("page",3).put("sourceName","Zelle.pdf")
    @Test fun longVisualReadingsAreIndexedWithoutDroppingTextOrBreakingUnicode(){val text=("Zelle ğ ü ş ı ö ç ä ß 🧬 ").repeat(300)
        val unit=JSONObject().put("key","b".repeat(64)).put("status","REVIEW").put("transcription",text).put("observations","").put("section","Biologie").put("page",3).put("sourceName","Zelle.pdf")
        val chunks=tools().passages(listOf(unit));assertTrue(chunks.size>1);assertEquals(text+"\n",chunks.joinToString(""){it.text});assertTrue(chunks.all{it.text.length<=2000})
    }
    @Test fun sourceCitedCoachResultIsAcceptedButStillADraft(){val t=tools();val result=t.validate(response().toString(),KnowledgeMode.DOCUMENTS_ONLY,listOf(passage),CoachTask.QUESTION);assertEquals(1,result.getJSONArray("citations").length())}
    @Test fun translatedSearchKeywordsRetrieveGermanEvidenceWithoutBecomingAnAnswer(){val t=tools();val terms=t.searchKeywords("{\"terms\":[\"Zelle\",\"hücre\",\"cell\"]}")
        assertEquals("source",StudySearch.retrieve(listOf(passage),"cell $terms").single().id)
        try{t.searchKeywords("{\"terms\":[]}");fail()}catch(_:IllegalArgumentException){}
    }
    @Test(expected=IllegalArgumentException::class) fun externalKnowledgeIsRejectedInDocumentsOnly(){tools().validate(response("Additional claim").toString(),KnowledgeMode.DOCUMENTS_ONLY,listOf(passage),CoachTask.QUESTION)}
    @Test(expected=IllegalStateException::class) fun unknownCitationsCannotBecomeAnswers(){tools().validate(response(id="invented").toString(),KnowledgeMode.DOCUMENTS_ONLY,listOf(passage),CoachTask.QUESTION)}
    @Test(expected=IllegalArgumentException::class) fun unsupportedEvidenceCannotBecomeAnswers(){tools().validate(response(quote="Die Zelle enthält keine DNA").toString(),KnowledgeMode.DOCUMENTS_ONLY,listOf(passage),CoachTask.QUESTION)}
    @Test fun enrichedKnowledgeIsSeparatedFromSourceAnswer(){val result=tools().validate(response("Model background").toString(),KnowledgeMode.ENRICHED,listOf(passage),CoachTask.SIMPLE);assertEquals("Model background",result.getString("additional"))}
    @Test fun generatedCardsAreStoredWithoutAutomaticApproval(){val t=tools();val r=response().put("cards",JSONArray().put(JSONObject().put("id","source").put("question","Was enthält die Zelle?").put("answer","DNA").put("quote",passage.text)))
        val valid=t.validate(r.toString(),KnowledgeMode.DOCUMENTS_ONLY,listOf(passage),CoachTask.CARDS);t.saveCoach(signature,CoachTask.CARDS,"Zelle",valid,listOf(passage))
        val cards=t.cards(signature,emptyList(),JSONObject());assertEquals(1,cards.size);assertEquals(0,t.store.review().length());assertTrue(t.cards("c".repeat(64),emptyList(),JSONObject()).isEmpty())
    }
    @Test fun timedMockSurvivesReopeningAndRejectsLateAnswers(){val t=tools();t.createMock(signature,listOf(card()),1,1,1000)
        t.answerMock(signature,"b".repeat(64),"DNA",2000)
        val restored=StudyTools(ExamStore(t.store.project));assertEquals("DNA",restored.data("mock.json").getJSONObject("answers").getString("b".repeat(64)))
        try{restored.answerMock(signature,"b".repeat(64),"late",61_001);fail("Late answer accepted")}catch(_:IllegalArgumentException){}
        restored.submitMock(signature);val file=restored.exportMock(restored.data("mock.json"));assertTrue(file.readText().contains("# Ayrı yanıt anahtarı"))
    }
    @Test fun mockCannotExportAnswerKeyBeforeSubmission(){val t=tools();val m=t.createMock(signature,listOf(card()),1,1,1000)
        try{t.exportMock(m);fail("Answer key leaked")}catch(_:IllegalArgumentException){}
    }
    @Test fun mockMarkingUpdatesReviewOnceAndRejectsDuplicateGrade(){val t=tools();val id="b".repeat(64);t.createMock(signature,listOf(card()),1,1,1000);t.submitMock(signature);t.markMock(signature,id,0)
        assertEquals(1,t.store.review().getJSONObject(id).getInt("lapses"))
        try{t.markMock(signature,id,2);fail("Duplicate grade accepted")}catch(_:IllegalArgumentException){}
        assertEquals(1,t.store.review().getJSONObject(id).getInt("attempts"))
    }
    @Test fun clozeRequiresAnExactSourceTermAndPreservesProvenance(){val t=tools();t.cloze(signature,card(),"Zelle");val generated=t.cards(signature,emptyList(),JSONObject()).single()
        assertTrue(generated.getString("question").contains("[…]"));assertEquals("Zelle",generated.getString("answer"));assertEquals(3,generated.getInt("page"))
        try{t.cloze(signature,card(),"unknown");fail()}catch(_:IllegalArgumentException){}
    }
    @Test fun ankiExportEscapesFieldsInsteadOfCreatingExtraColumns(){val t=tools();val file=t.exportCards(signature,listOf(card().put("question","A\tB\n<term>")))
        val row=file.readLines().last();assertEquals(1,row.count{it=='\t'});assertTrue(row.contains("&lt;term&gt;"));assertTrue(row.contains("<br>"))
    }
    @Test fun malformedSavedDeckIsRejectedBeforeUiRendering(){val t=tools();atomicJson(t.file("cards.json"),JSONObject().put("cards",JSONArray().put(JSONObject().put("id","bad"))))
        try{t.cards(signature,emptyList(),JSONObject());fail("Malformed deck accepted")}catch(_:Exception){}
    }
}
