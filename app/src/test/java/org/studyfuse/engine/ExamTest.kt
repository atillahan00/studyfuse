package org.studyfuse.engine

import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
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
class ExamTest {
    private fun json(origin:String="document",evidence:String="Die Zelle enthält DNA.")=JSONObject().put("transcription","Die Zelle enthält DNA.").put("observations","Ein Pfeil zeigt zum Zellkern.")
        .put("uncertainties",JSONArray().put("Handschrift neben dem Pfeil unklar"))
        .put("items",JSONArray().put(JSONObject().put("kind","recall").put("question","Was enthält die Zelle?").put("answer","Die Zelle enthält DNA.").put("evidence",evidence).put("origin",origin)))
    private fun root()=File(ApplicationProvider.getApplicationContext<StudyFuseApp>().filesDir,"projects/test-${UUID.randomUUID()}").apply{mkdirs()}
    @Test fun exactPdfEvidenceIsTaggedWithoutClaimingSemanticVerification(){val result=ExamResponse.parse(json().toString(),KnowledgeMode.DOCUMENTS_ONLY,"Die Zelle enthält DNA.","key");assertEquals("pdf_text",result.getJSONArray("items").getJSONObject(0).getString("basis"));assertEquals("REVIEW",result.getString("status"))}
    @Test fun handwrittenTranscriptRemainsUnverified(){val result=ExamResponse.parse(json().toString(),KnowledgeMode.DOCUMENTS_ONLY,"","key");assertEquals("unverified_visual",result.getJSONArray("items").getJSONObject(0).getString("basis"));assertEquals(1,result.getJSONArray("uncertainties").length())}
    @Test fun unsupportedQuoteIsRejectedRatherThanPromotedToFact(){val result=ExamResponse.parse(json(evidence="DNA kommt nur in Tieren vor").toString(),KnowledgeMode.DOCUMENTS_ONLY,"","key");assertEquals(0,result.getJSONArray("items").length());assertEquals(1,result.getJSONArray("rejected").length())}
    @Test fun modelAdditionsCannotLeakIntoDocumentsOnly(){val raw=json("ai_added","").toString();assertEquals(0,ExamResponse.parse(raw,KnowledgeMode.DOCUMENTS_ONLY,"","k").getJSONArray("items").length());val addition=ExamResponse.parse(raw,KnowledgeMode.ENRICHED,"","k").getJSONArray("items").getJSONObject(0);assertEquals("model_knowledge",addition.getString("basis"))}
    @Test(expected=JSONException::class) fun truncatedModelOutputDoesNotBecomeNotes(){ExamResponse.parse("{\"transcription\":\"unfinished",KnowledgeMode.DOCUMENTS_ONLY,"","k")}
    @Test fun optionalExamFieldsAreNotRequiredAndDefaultIsDocumentsOnly(){val config=ExamConfig.decode(JSONObject());assertEquals(KnowledgeMode.DOCUMENTS_ONLY,config.mode);assertEquals("",config.focus);assertEquals("",config.examDate);config.validate()}
    @Test(expected=java.time.format.DateTimeParseException::class) fun invalidExamDateIsExplicit(){ExamConfig(examDate="2026-99-50").validate()}
    private fun report(file:File,stamp:String,sourceId:String)=JSONObject().put("settings",JSONObject().put("pageSize","AUTO")).put("createdUtc",stamp)
        .put("files",JSONArray().put(JSONObject().put("fileName","Biologie.pdf").put("section","Zelle").put("status","OK").put("sourceId",sourceId)
            .put("originalSha256","abc").put("activeSha256","abc").put("contentStart",3).put("visualPages",1).put("coverage",JSONArray()))).also{file.writeText(it.toString())}
    @Test fun checkpointSignatureSeparatesPoliciesAndSurvivesRestoreMetadataChanges(){val directory=root();val store=ExamStore(directory);val file=File(directory,"report.json");val m=File(directory,"m").apply{writeText("model")};val model=LocalModel("model",m,sha256(m),m.length())
        report(file,"before","source-old");val one=store.signature(file,model,ExamConfig());val enriched=store.signature(file,model,ExamConfig(mode=KnowledgeMode.ENRICHED));assertNotEquals(one,enriched)
        report(file,"after","source-restored");assertEquals(one,store.signature(file,model,ExamConfig()));assertNotEquals(one,store.signature(file,model,ExamConfig(focus="Kapitel 2")))
    }
    @Test fun reviewStateIsAtomicAndPersistsAcrossReopening(){val directory=root();val store=ExamStore(directory);store.updateReview("card"){it.put("approved",true).put("lapses",2).put("correction","ğ ü ş ı ö ç ä ß")};val restored=ExamStore(directory).review().getJSONObject("card");assertTrue(restored.getBoolean("approved"));assertEquals(2,restored.getInt("lapses"));assertEquals("ğ ü ş ı ö ç ä ß",restored.getString("correction"))}
    @Test fun exportHasSeparateAnswersUncertaintyAndSourceReferences(){val store=ExamStore(root());val unit=ExamResponse.parse(json().toString(),KnowledgeMode.DOCUMENTS_ONLY,"","k").put("page",7).put("region",0).put("sourceName","Zelle.pdf").put("section","Biologie")
        val session=JSONObject().put("config",ExamConfig().json()).put("model","test-double").put("files",JSONArray().put(JSONObject().put("fileName","Zelle.pdf").put("status","OK").put("visualPages",1))).put("total",1)
        val md=ExamExport.markdown(store,session,listOf(unit),JSONObject());assertTrue(md.indexOf("## Aktif hatırlama")<md.indexOf("## Ayrı yanıt anahtarı"));assertTrue(md.contains("PDF 7"));assertTrue(md.contains("İNCELENMEMİŞ"));assertTrue(md.contains("Handschrift"))
        val context=ApplicationProvider.getApplicationContext<StudyFuseApp>();val files=ExamExport.write(context,store,session,listOf(unit));assertTrue(files.all{it.length()>0});loadPdf(files.first(),store.root).use{assertTrue(it.numberOfPages>0)}
    }
    @Test fun malformedCheckpointAndTraversalAreRejected(){val store=ExamStore(root())
        try{store.unit("../../settings");fail("Traversal accepted")}catch(_:IllegalArgumentException){}
        try{validateUnit(JSONObject().put("key","a").put("status","REVIEW"),"a");fail("Malformed checkpoint accepted")}catch(_:Exception){}
    }
    @Test fun examPdfAppendsOriginalVectorPageWithoutRasterReplacement(){
        val context=ApplicationProvider.getApplicationContext<StudyFuseApp>();val store=ExamStore(root());val original=File(store.project,"original.pdf")
        newPdf(store.root).use{doc->val page=com.tom_roush.pdfbox.pdmodel.PDPage(com.tom_roush.pdfbox.pdmodel.common.PDRectangle.A4);doc.addPage(page)
            com.tom_roush.pdfbox.pdmodel.PDPageContentStream(doc,page).use{cs->PdfText(context,doc).draw(cs,"ORIGINAL_DIAGRAM_123",40f,740f);cs.addRect(40f,650f,120f,60f);cs.stroke()};doc.save(original)}
        val report=File(store.project,"report.json").apply{writeText(JSONObject().put("outputs",JSONArray().put(JSONObject().put("path",original.path))).toString())}
        val session=JSONObject().put("config",ExamConfig().json()).put("model","test-double").put("files",JSONArray()).put("total",0)
        val result=ExamExport.write(context,store,session,emptyList(),report).first()
        loadPdf(result,store.root).use{doc->assertTrue(doc.numberOfPages>=3)
            val last=doc.getPage(doc.numberOfPages-1);assertTrue(last.contents.bufferedReader().readText().contains(" re"))
            assertTrue(com.tom_roush.pdfbox.text.PDFTextStripper().getText(doc).contains("ORIGINAL_DIAGRAM_123"))
        }
    }
}
