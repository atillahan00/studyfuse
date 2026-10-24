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
        screenshot("tablet-exam-workshop.png")
        ui.onNodeWithText("Sınav hazırlığını oluştur / sürdür").performScrollTo().assertIsNotEnabled()
        ui.onNodeWithText("Notlar",useUnmergedTree=true).performClick()
        ui.onNodeWithText("Güncel çalışma maddesi yok.",substring=true).assertExists()
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
