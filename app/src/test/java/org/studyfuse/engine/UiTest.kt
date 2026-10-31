package org.studyfuse.engine

import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import org.junit.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.*
import org.studyfuse.*
import java.io.File
import kotlinx.coroutines.runBlocking
import org.json.*
import org.studyfuse.learning.*

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[34],qualifiers="w1280dp-h800dp-land-mdpi",application=StudyFuseApp::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class UiTest {
    @get:Rule val ui=createAndroidComposeRule<MainActivity>()
    @Test fun homeAndThemeControlsWorkInTurkish() {
        ui.onNodeWithText("Yeni proje").assertExists()
        ui.onNodeWithText("Dosya içe aktar").assertExists()
        screenshot("tablet-home.png")
        ui.onNodeWithContentDescription("Ayarlar").performClick()
        ui.onNodeWithText("Koyu").performClick()
        ui.onNodeWithText("Tamam").performClick()
        ui.onNodeWithText("Yeni proje").assertExists()
    }
    @Test fun newProjectAndRenameAreFunctional() {
        ui.onNodeWithText("Yeni proje").performClick()
        ui.waitUntil(15000) {ui.onAllNodesWithText("Proje yedeği kaydet").fetchSemanticsNodes().isNotEmpty()}
        ui.onNodeWithText("Dosyalar").assertExists()
        ui.onNodeWithText("Klasör").assertExists()
        ui.onNodeWithText("PDF oluştur").assertIsNotEnabled()
        ui.onNodeWithContentDescription("Proje adını değiştir").performClick()
        ui.onNode(hasSetTextAction()).performTextReplacement("Biyoloji — Hücre")
        ui.onNodeWithText("Kaydet").performClick()
        ui.waitUntil(10000) {ui.onAllNodesWithText("Biyoloji — Hücre").fetchSemanticsNodes().isNotEmpty()}
        screenshot("tablet-project.png")
    }
    @Test fun bulkRemovalCanBeUndoneAndBackupRestoreIsVisible() {
        ui.onNodeWithText("Yedek geri yükle").assertExists()
        ui.onNodeWithText("Yeni proje").performClick()
        ui.waitUntil(15000){ui.onAllNodesWithText("Proje yedeği kaydet").fetchSemanticsNodes().isNotEmpty()}
        lateinit var model:org.studyfuse.ui.StudyViewModel
        ui.runOnIdle{model=org.robolectric.util.ReflectionHelpers.callInstanceMethod(ui.activity,"getModel")}
        val input=File(ui.activity.filesDir,"projects/ui-input/Not 2.txt").apply{parentFile!!.mkdirs();writeText("ğ ü ş ı ö ç")}
        org.robolectric.util.ReflectionHelpers.getStaticField<MutableMap<String,Any>>(androidx.core.content.FileProvider::class.java,"sCache").clear()
        val uri=androidx.core.content.FileProvider.getUriForFile(ui.activity,"org.studyfuse.files",input)
        ui.runOnUiThread{model.importUris(listOf(uri))}
        ui.waitUntil(15000){ui.onAllNodesWithText("Not 2.txt").fetchSemanticsNodes().isNotEmpty()}
        ui.onNodeWithText("Toplu seç").performClick()
        ui.onNodeWithContentDescription("Seç: Not 2.txt").performClick()
        ui.onNodeWithText("Seçilenleri kaldır").performClick()
        ui.waitUntil(10000){ui.onAllNodesWithText("Notlarını buraya ekle").fetchSemanticsNodes().isNotEmpty()}
        ui.onNodeWithText("Kaldırmayı geri al").performClick()
        ui.waitUntil(10000){ui.onAllNodesWithText("Not 2.txt").fetchSemanticsNodes().isNotEmpty()}
        ui.onNodeWithText("Notlarını buraya ekle").assertDoesNotExist()
        ui.onNodeWithText("Proje yedeği kaydet").assertExists()
    }
    @Test fun localExamWorkshopRequiresRealModelAndDefaultsToDocumentsOnly() {
        ui.onNodeWithText("Yeni proje").performClick()
        ui.waitUntil(15000){ui.onAllNodesWithText("Sınav atölyesi · Yerel AI").fetchSemanticsNodes().isNotEmpty()}
        ui.onNodeWithText("Sınav atölyesi · Yerel AI").performClick()
        ui.waitUntil(10000){ui.onAllNodesWithText("Yalnızca belgelerim").fetchSemanticsNodes().isNotEmpty()}
        ui.onNodeWithText("Model içe aktar").assertExists()
        ui.onNodeWithText("Otomatik çevrimdışı AI kurulumu").assertExists()
        screenshot("tablet-exam-workshop.png")
        ui.onNodeWithText("Sınav hazırlığını oluştur / sürdür").performScrollTo().assertIsNotEnabled()
        ui.onNodeWithText("Notlar",useUnmergedTree=true).performClick()
        ui.onNodeWithText("Güncel çalışma maddesi yok.",substring=true).assertExists()
    }
    @Test fun learningHubGuidesStudentsToRealSourcesInsteadOfFakeAnswers(){
        ui.onNodeWithText("Yeni proje").performClick()
        ui.waitUntil(15000){ui.onAllNodesWithText("AI öğrenme merkezi").fetchSemanticsNodes().isNotEmpty()}
        ui.onNodeWithText("AI öğrenme merkezi").performClick()
        ui.waitUntil(10000){ui.onAllNodesWithText("Kurulum ve sınav atölyesi").fetchSemanticsNodes().isNotEmpty()}
        ui.onNodeWithText("Koç").assertExists();ui.onNodeWithText("Deneme").assertExists();ui.onNodeWithText("Plan").assertExists()
        screenshot("tablet-study-hub.png")
    }
    /** Prepared deterministic study fixture: exercises UI/answer persistence, not model quality. */
    @Test fun timedMockHidesAnswersUntilSubmissionAndStoresSelfAssessment(){
        ui.onNodeWithText("Yeni proje").performClick()
        ui.waitUntil(15000){ui.onAllNodesWithText("AI öğrenme merkezi").fetchSemanticsNodes().isNotEmpty()}
        lateinit var model:org.studyfuse.ui.StudyViewModel
        ui.runOnIdle{model=org.robolectric.util.ReflectionHelpers.callInstanceMethod(ui.activity,"getModel")}
        val id=model.selected.value!!;val dir=model.repo.directory(id);val store=ExamStore(dir)
        val input=File(dir,"Biologie.txt").apply{writeText("Die Zelle enthält DNA.")}
        val source=org.studyfuse.data.Source(java.util.UUID.randomUUID().toString(),id,input.name,"Biologie",0,input.path,input.path,input.length(),sha256(input),"TEXT")
        runBlocking{
            model.repo.dao.addSource(source)
            val compiled=Compiler(ui.activity,File(dir,"exports/ui").apply{mkdirs()}).compile(listOf(source),org.studyfuse.core.ExportSettings())
            val weights=File(model.repo.context.filesDir,"local-models/fixture.part").apply{parentFile!!.mkdirs();writeText("TEST ONLY; not model weights")}
            val hash=sha256(weights);val fake=File(weights.parentFile,"$hash.litertlm");weights.renameTo(fake)
            atomicJson(File(fake.parentFile,"selected.json"),JSONObject().put("name","UI fixture, no inference").put("sha256",hash).put("bytes",fake.length()))
            val local=LocalModel("UI fixture, no inference",fake,hash,fake.length());val key=digest("ui prepared study reading")
            val raw=JSONObject().put("transcription","Die Zelle enthält DNA.").put("observations","").put("uncertainties",JSONArray())
                .put("items",JSONArray().put(JSONObject().put("kind","recall").put("question","Was enthält die Zelle?").put("answer","DNA").put("evidence","Die Zelle enthält DNA.").put("origin","document")))
            val unit=ExamResponse.parse(raw.toString(),org.studyfuse.core.KnowledgeMode.DOCUMENTS_ONLY,"Die Zelle enthält DNA.",key)
                .put("key",key).put("sourceId",source.id).put("sourceName",input.name).put("section","Biologie").put("page",3).put("region",0).put("bounds",JSONArray(listOf(0,0,1,1)))
            atomicJson(store.unit(key),unit)
            val signature=store.signature(compiled.report,local,ExamConfig())
            store.saveSession(JSONObject().put("signature",signature).put("units",JSONArray().put(key)))
            val itemId=unit.getJSONArray("items").getJSONObject(0).getString("id");store.updateReview(itemId){it.put("approved",true)}
            val p=model.repo.dao.project(id)!!;model.repo.dao.updateProject(p.copy(status="DONE",report=compiled.report.path,output=JSONArray(compiled.outputs.map{it.json()}).toString()))
        }
        ui.onNodeWithText("AI öğrenme merkezi").performClick()
        try{ui.waitUntil(10000){ui.onAllNodesWithText("Tüm konular").fetchSemanticsNodes().isNotEmpty()}}catch(t:Throwable){
            val local=ModelStore(model.repo.context).selected();val p=model.project.value
            val diagnostics="selected=${model.selected.value}; expected=$id; project=${p?.id}; report=${p?.report}; appFiles=${model.repo.context.filesDir}; activityFiles=${ui.activity.filesDir}; model=${local?.digest}; stored=${store.session()?.optString("signature")}; computed=${if(local!=null && p?.report!=null)store.signature(File(p.report),local,store.config())else null}"
            File("build/test-artifacts/mock-ui-tree.txt").writeText(diagnostics+"\n"+ui.onAllNodes(isRoot()).onLast().printToString());screenshot("mock-ui-debug.png");throw t
        }
        ui.onNodeWithText("Deneme",useUnmergedTree=true).performClick()
        ui.onNodeWithText("Yeni deneme başlat").performScrollTo().performClick()
        ui.waitUntil(10000){ui.onAllNodesWithText("Denemeyi teslim et").fetchSemanticsNodes().isNotEmpty()}
        ui.onNodeWithText("Yanıt anahtarı: DNA").assertDoesNotExist()
        ui.onNode(hasSetTextAction() and hasText("Yanıtım")).performScrollTo().performTextInput("DNA")
        ui.onNodeWithText("Denemeyi teslim et").performScrollTo().performClick()
        ui.waitUntil(10000){ui.onAllNodesWithText("Yanıt anahtarı: DNA").fetchSemanticsNodes().isNotEmpty()}
        // Expiry may reveal the key while asynchronous submission is still saving.
        // Wait for the enabled assessment control, not merely visible answer text.
        ui.waitUntil(10000){ui.onAllNodes(hasText("0 / 2") and isEnabled()).fetchSemanticsNodes().isNotEmpty()}
        ui.onNodeWithText("0 / 2").performScrollTo().assertIsEnabled().performClick()
        ui.waitUntil(10000){StudyTools(store).data("mock.json").getJSONObject("marks").length()==1}
        org.junit.Assert.assertEquals("DNA",StudyTools(store).data("mock.json").getJSONObject("answers").getString(StudyTools(store).data("mock.json").getJSONArray("cards").getJSONObject(0).getString("id")))
        screenshot("tablet-mock-exam.png")
    }
    @After fun captureFailedMockDiagnostics() {
        File("build/test-artifacts").mkdirs()
        runCatching { File("build/test-artifacts/mock-final-ui-tree.txt").writeText(ui.onAllNodes(isRoot()).onLast().printToString()) }
    }
    private fun screenshot(name:String) {
        ui.waitForIdle()
        lateinit var image:android.graphics.Bitmap
        ui.runOnUiThread {
            val view=android.view.inspector.WindowInspector.getGlobalWindowViews().lastOrNull() ?: ui.activity.window.decorView
            image=android.graphics.Bitmap.createBitmap(view.width,view.height,android.graphics.Bitmap.Config.ARGB_8888)
            view.draw(android.graphics.Canvas(image))
        }
        val file=File("build/test-artifacts/$name").apply {parentFile!!.mkdirs()}
        file.outputStream().use {image.compress(android.graphics.Bitmap.CompressFormat.PNG,100,it)}
    }
}
