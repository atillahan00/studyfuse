package org.studyfuse.engine

import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.*
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
class CoachReliabilityTest {
    private val root=File(ApplicationProvider.getApplicationContext<StudyFuseApp>().filesDir,"projects/coach-${UUID.randomUUID()}").apply{mkdirs()}
    private val store=ExamStore(root);private val tools=StudyTools(store)
    private val signature="a".repeat(64)
    private val text="Şiirde ölçü ve uyak incelenir. ä ö ü ß ğ ü ş ı ö ç. Olumsuzluğu silme: içermez."
    private val passage=EvidencePassage("c".repeat(64)+":0",text,"Şiir",3,"source.pdf",unverifiedVisualReading=false)
    @After fun clean(){root.deleteRecursively()}
    private fun model():LocalModel {val f=File(root,"model").apply{writeText("deterministic test double")};return LocalModel("fixture",f,sha256(f),f.length())}
    private fun card(evidence:String="E1")=JSONObject().put("evidence",evidence).put("question","Neler incelenir?").put("answer","Ölçü ve uyak.")
    private fun response(cards:JSONArray=JSONArray().put(card()))=JSONObject().put("answer","Model overview is not trusted as a card summary")
        .put("additional","").put("uncertainties",JSONArray()).put("citations",JSONArray().put(JSONObject().put("evidence","E1"))).put("cards",cards)
    private fun responseForPrompt(prompt:String):String {
        val cards=JSONObject(prompt).getJSONArray("sourceExcerpts").objects().map{source->card(source.getJSONArray("evidence").getJSONObject(0).getString("id"))}
        return response(JSONArray(cards)).toString()
    }
    @Test fun appOwnedEvidenceAttachesLiteralUnicodeSourceAndRetainsReviewRequirement(){
        val r=tools.validate(response().toString(),KnowledgeMode.DOCUMENTS_ONLY,listOf(passage),CoachTask.CARDS)
        val c=r.getJSONArray("cards").getJSONObject(0)
        assertEquals(text,c.getString("quote"));assertEquals(passage.id,c.getString("id"))
        assertFalse(r.getString("answer").contains("Model overview"))
        tools.saveCoach(signature,CoachTask.CARDS,"all",r,listOf(passage))
        assertEquals(text,tools.cards(signature,emptyList(),JSONObject()).single().getString("evidence"))
        assertEquals(0,store.review().length())
    }
    @Test fun promptReferencesPreserveEveryCharacterWithoutDuplicatingQuotes(){
        val long=passage.copy(text=("Şiir dizesi 🧬 ve olumsuzluk içermez.\n").repeat(80))
        val context=CoachEvidence(listOf(long))
        assertEquals(long.text,context.spans.joinToString(""){it.text});assertTrue(context.spans.all{it.text.lastOrNull()?.isHighSurrogate()!=true})
        val prompt=JSONObject(tools.prompt("all","",listOf(passage))).getJSONArray("sourceExcerpts").getJSONObject(0)
        assertEquals("P1",prompt.getString("id"));assertFalse(prompt.getBoolean("unverifiedVisualReading"))
        assertEquals(text,prompt.getJSONArray("evidence").getJSONObject(0).getString("text"))
        val cardSystem=tools.system(ExamConfig(),CoachTask.CARDS)
        assertFalse(cardSystem.contains("exact contiguous source excerpt"));assertTrue(cardSystem.contains("\"answer\":\"\",\"citations\":[]"))
        assertTrue(tools.system(ExamConfig(),CoachTask.QUESTION).contains("\"cards\":[]"))
        val conventional=tools.validate(response(JSONArray().put(JSONObject().put("id","E1").put("quote","model translation is replaced by the original").put("question","q").put("answer","a"))).toString(),KnowledgeMode.DOCUMENTS_ONLY,listOf(passage),CoachTask.CARDS)
        assertEquals(text,conventional.getJSONArray("cards").getJSONObject(0).getString("quote"))
    }
    @Test fun badSummaryCitationAndBadCardDoNotDiscardValidCards(){
        val raw=response(JSONArray().put(card()).put(card("E999")).put("malformed card"))
            .put("citations",JSONArray().put(JSONObject().put("id","invented").put("quote","paraphrased Turkish quote")))
        val r=tools.validate(raw.toString(),KnowledgeMode.ENRICHED,listOf(passage),CoachTask.CARDS)
        assertEquals(1,r.getJSONArray("cards").length());assertEquals(3,r.getJSONArray("rejected").length())
        assertEquals(1,r.getJSONArray("citations").length());assertEquals(text,r.getJSONArray("citations").getJSONObject(0).getString("quote"))
    }
    @Test fun retypedParaphrasesNegationAndInventedIdsRemainRejected(){
        val bad=JSONObject().put("id",passage.id).put("quote","Şiirde ölçü ve uyak incelenmez.").put("question","q").put("answer","bad")
        val r=tools.validate(response(JSONArray().put(bad)).toString(),KnowledgeMode.DOCUMENTS_ONLY,listOf(passage),CoachTask.CARDS)
        assertEquals(0,r.getJSONArray("cards").length());assertEquals(1,r.getJSONArray("rejected").length())
        val answer=response(JSONArray()).put("citations",JSONArray().put(JSONObject().put("evidence","E999")))
        try{tools.validate(answer.toString(),KnowledgeMode.DOCUMENTS_ONLY,listOf(passage),CoachTask.QUESTION);fail("Invented evidence became an answer")}catch(_:IllegalStateException){}
    }
    @Test fun emptyEvidenceCanProduceAnHonestInsufficiencyMessage(){
        val raw=response(JSONArray()).put("answer","").put("citations",JSONArray()).put("uncertainties",JSONArray().put("Bu konuda kaynak yok"))
        val r=tools.validate(raw.toString(),KnowledgeMode.DOCUMENTS_ONLY,emptyList(),CoachTask.QUESTION)
        assertEquals(0,r.getJSONArray("citations").length());assertTrue(r.getString("answer").contains("yeterli kanıt yok"))
    }
    @Test fun nativePassagesDoNotTreatAppStatusTextAsEducationalEvidence(){
        val unit=JSONObject().put("key","c".repeat(64)).put("status","REVIEW").put("transcription",text).put("observations","PDF metni doğrudan alındı; AI yorumu yapılmadı")
            .put("readingMethod","native_pdf").put("section","Şiir").put("page",3).put("sourceName","source.pdf")
        val p=tools.passages(listOf(unit)).single();assertEquals(text,p.text);assertFalse(p.unverifiedVisualReading)
    }
    @Test fun sourceWidePlanIncludesHundredPagesAndNeverTruncatesPassages(){
        val corpus=(1..100).map{passage.copy(id="p$it",page=it,text=("Kaynak $it ").repeat(100))}
        val plan=CoachBatches.plan(corpus)
        assertEquals(corpus,plan.flatten());assertTrue(plan.size>6)
        assertTrue(plan.all{it.size<=4 && it.sumOf{p->p.text.length}<=6000})
    }
    @Test fun oneBoundedRepairRecoversInvalidReferencesWithoutRepeatingGoodCards()=runBlocking {
        var calls=0
        val inference=ExamInference{_,_,system,_,image->assertNull(image);calls++;if(calls==1)response(JSONArray().put(card("bad"))).toString()else{assertTrue(system.contains("REPAIR"));response().toString()}}
        val r=CoachRunner(tools,inference).generate(model(),ExamConfig(),CoachTask.CARDS,"all","",listOf(passage))
        assertEquals(2,calls);assertEquals(1,r.getJSONArray("cards").length());assertTrue(r.getBoolean("repairAttempted"))
    }
    @Test fun partialCardsAreReturnedWithoutAnUnnecessaryRepair()=runBlocking {
        var calls=0
        val inference=ExamInference{_,_,_,_,_->calls++;response(JSONArray().put(card()).put(card("bad"))).toString()}
        val r=CoachRunner(tools,inference).generate(model(),ExamConfig(),CoachTask.CARDS,"all","",listOf(passage))
        assertEquals(1,calls);assertEquals(1,r.getJSONArray("cards").length());assertEquals(1,r.getJSONArray("rejected").length())
    }
    @Test fun batchCancellationResumesAndKeepsCardsWithoutApprovingOrDuplicatingThem()=runBlocking {
        val corpus=(1..9).map{passage.copy(id="p$it",page=it)};var calls=0
        val inference=ExamInference{_,_,_,prompt,_->calls++;if(calls==2)throw CancellationException("fixture");responseForPrompt(prompt)}
        val m=model()
        try{CoachRunner(tools,inference).cards(signature,m,ExamConfig(),"all",corpus){_,_,_->};fail("Cancellation ignored")}catch(_:CancellationException){}
        assertEquals(4,tools.cards(signature,emptyList(),JSONObject()).size)
        assertEquals("CANCELLED",readJson(tools.file("coach-run.json")).getString("status"))
        calls=0
        val resumed=CoachRunner(tools,ExamInference{_,_,_,prompt,_->calls++;responseForPrompt(prompt)}).cards(signature,m,ExamConfig(),"all",corpus){_,_,_->}
        assertEquals(2,calls);assertEquals(1,resumed.getInt("reusedBatches"));assertEquals(3,resumed.getInt("processedBatches"))
        assertEquals(9,tools.cards(signature,emptyList(),JSONObject()).size);assertEquals(0,store.review().length())
        assertEquals(9,resumed.getInt("passagesWithCards"));assertEquals(0,resumed.getJSONArray("passagesWithoutCards").length())
    }
    @Test fun failedBatchDoesNotHideLaterCardsAndChangingSourceInvalidatesItsCheckpoint()=runBlocking {
        val corpus=(1..5).map{passage.copy(id="p$it",page=it)};var calls=0
        val m=model();val engine=ExamInference{_,_,_,_,_->calls++;if(calls<=2)"not JSON" else response().toString()}
        val r=CoachRunner(tools,engine).cards(signature,m,ExamConfig(),"all",corpus){_,_,_->}
        assertEquals(1,r.getInt("failedBatches"));assertEquals("PARTIAL",r.getString("status"));assertEquals(1,tools.cards(signature,emptyList(),JSONObject()).size)
        calls=0
        val changed=corpus.map{it.copy(text=it.text+" Yeni eklenen bilgi.")}
        val second=CoachRunner(tools,ExamInference{_,_,_,_,_->calls++;response().toString()}).cards(signature,m,ExamConfig(),"all",changed){_,_,_->}
        assertEquals(2,calls);assertEquals(0,second.getInt("reusedBatches"))
    }
    @Test fun resumeTargetsOnlyPassagesWithoutCardsAndDoesNotClaimFullFactCoverage()=runBlocking {
        val corpus=(1..4).map{passage.copy(id="p$it",page=it)};val m=model()
        val first=CoachRunner(tools,ExamInference{_,_,_,_,_->response().toString()}).cards(signature,m,ExamConfig(),"all",corpus){_,_,_->}
        assertEquals("PARTIAL",first.getString("status"));assertEquals(1,first.getInt("passagesWithCards"))
        var contextPages=emptyList<Int>()
        val resumed=CoachRunner(tools,ExamInference{_,_,_,prompt,_->contextPages=JSONObject(prompt).getJSONArray("sourceExcerpts").objects().map{it.getInt("page")};responseForPrompt(prompt)})
            .cards(signature,m,ExamConfig(),"all",corpus){_,_,_->}
        assertEquals(listOf(2,3,4),contextPages);assertEquals(4,resumed.getInt("passagesWithCards"));assertEquals(4,tools.cards(signature,emptyList(),JSONObject()).size)
        assertEquals(1,tools.data("coach.json").getJSONArray("entries").length())
    }
    @Test fun corruptCachedQuoteIsRegeneratedInsteadOfBeingTrusted()=runBlocking {
        val m=model();val corpus=listOf(passage);val engine=ExamInference{_,_,_,_,_->response().toString()}
        CoachRunner(tools,engine).cards(signature,m,ExamConfig(),"all",corpus){_,_,_->}
        val data=tools.data("coach.json");data.getJSONArray("entries").getJSONObject(0).getJSONObject("result").getJSONArray("cards").getJSONObject(0).put("quote","Invented source quote")
        atomicJson(tools.file("coach.json"),data);var calls=0
        val result=CoachRunner(tools,ExamInference{_,_,_,_,_->calls++;response().toString()}).cards(signature,m,ExamConfig(),"all",corpus){_,_,_->}
        assertEquals(1,calls);assertEquals(0,result.getInt("reusedBatches"))
    }
    @Test fun replayingCheckpointRepairsAnInterruptedDeckWriteWithoutDuplicatingHistory(){
        val r=tools.validate(response().toString(),KnowledgeMode.DOCUMENTS_ONLY,listOf(passage),CoachTask.CARDS)
        tools.saveCoach(signature,CoachTask.CARDS,"all",r,listOf(passage),"batch")
        tools.file("cards.json").delete()
        tools.saveCoach(signature,CoachTask.CARDS,"all",r,listOf(passage),"batch")
        assertEquals(1,tools.data("coach.json").getJSONArray("entries").length())
        assertEquals(1,tools.cards(signature,emptyList(),JSONObject()).size)
    }
}
