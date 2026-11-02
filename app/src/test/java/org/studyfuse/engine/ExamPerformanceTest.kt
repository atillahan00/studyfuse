package org.studyfuse.engine

import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import org.json.*
import org.junit.Assert.*
import org.junit.Test
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
class ExamPerformanceTest {
    private fun report()=JSONObject().put("files",JSONArray().put(JSONObject().put("originalSha256","a").put("status","OK").put("contentStart",3).put("visualPages",3)))
    @Test fun workloadIsOnePassOrTargetedOrAllCloseups(){
        assertEquals(3,ExamWorkload.plan(report(),ExamConfig()).calls)
        assertEquals(7,ExamWorkload.plan(report(),ExamConfig(detailPages="4")).calls)
        assertEquals(15,ExamWorkload.plan(report(),ExamConfig(detailed=true)).calls)
        try{ExamWorkload.plan(report(),ExamConfig(detailPages="1-4"));fail("TOC pages silently accepted")}catch(_:IllegalArgumentException){}
    }
    @Test fun legacySettingsRemainCompatibleAndNewOptionsRoundTrip(){
        val old=JSONObject("""{"mode":"DOCUMENTS_ONLY","language":"Deutsch","focus":"","examDate":"","detailed":true,"gpu":true,"references":[]}""")
        assertEquals(canonical(old),canonical(ExamConfig.decode(old).json()))
        val fast=ExamConfig(readingOnly=true,visualTokens=280,detailPages="3,5-7")
        assertEquals(fast,ExamConfig.decode(fast.json()))
        try{GenerationOptions(42).validate();fail("Bad budget accepted")}catch(_:IllegalArgumentException){}
    }
    @Test fun nativeTextIsRetainedWithoutAskingModelToRepeatIt(){
        val text="Die Zelle enthält DNA. ğ ü ş ı ö ç ä ß ∑x²"
        val raw=JSONObject().put("transcription","").put("observations","[VISUAL: arrow to nucleus]").put("uncertainties",JSONArray()).put("items",JSONArray())
        val parsed=ExamResponse.parse(raw.toString(),KnowledgeMode.DOCUMENTS_ONLY,text,"key")
        assertEquals(text,parsed.getString("transcription"));assertTrue(ExamPrompts.system(ExamConfig(readingOnly=true)).contains("items as an empty array"))
        raw.put("transcription","Handschrift: DNA → RNA")
        assertTrue(ExamResponse.parse(raw.toString(),KnowledgeMode.DOCUMENTS_ONLY,text,"key").getString("transcription").contains("DNA → RNA"))
    }
    @Test fun legacyDetailedOverviewIsReusedWithStableReviewedCardIds()=runBlocking {
        val context=ApplicationProvider.getApplicationContext<StudyFuseApp>();val dir=File(context.filesDir,"projects/perf-${UUID.randomUUID()}").apply{mkdirs()}
        val input=File(dir,"Zelle.txt").apply{writeText("Die Zelle enthält DNA.")}
        val source=org.studyfuse.data.Source("source","p",input.name,"Biologie",0,input.path,input.path,input.length(),sha256(input),"TEXT")
        val out=Compiler(context,File(dir,"exports").apply{mkdirs()}).compile(listOf(source),ExportSettings())
        val store=ExamStore(dir);val weights=File(dir,"m").apply{writeText("fixture only")};val model=LocalModel("fixture",weights,sha256(weights),weights.length())
        val page=readJson(out.report).getJSONArray("files").getJSONObject(0).getInt("contentStart")
        val key=digest("${store.signature(out.report,model,ExamConfig(detailed=true))}|$page|0")
        val raw=JSONObject("""{"transcription":"Die Zelle enthält DNA.","observations":"","uncertainties":[],"items":[{"kind":"recall","question":"Was enthält die Zelle?","answer":"DNA","evidence":"Die Zelle enthält DNA.","origin":"document"}]}""")
        val unit=ExamResponse.parse(raw.toString(),KnowledgeMode.DOCUMENTS_ONLY,"Die Zelle enthält DNA.",key)
            .put("key",key).put("page",page).put("region",0).put("sourceId",source.id).put("sourceName",source.name).put("section",source.section).put("bounds",JSONArray(listOf(0,0,1,1)))
        atomicJson(store.unit(key),unit);val card=unit.getJSONArray("items").getJSONObject(0).getString("id");store.updateReview(card){it.put("approved",true)}
        val result=ExamCompiler(context,store,ExamInference{_,_,_,_,_->error("Cached overview was regenerated")}).compile(out.report,model,ExamConfig()){_,_,_->}
        assertEquals(1,result.getInt("reusedRegions"));assertEquals(key,result.getJSONArray("units").getString(0));assertTrue(store.review().getJSONObject(card).getBoolean("approved"))
    }
}
