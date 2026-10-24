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
        ui.waitUntil(15000) {ui.onAllNodesWithText("Yeni çalışma").fetchSemanticsNodes().isNotEmpty()}
        ui.onNodeWithText("Dosyalar").assertExists()
        ui.onNodeWithText("Klasör").assertExists()
        ui.onNodeWithText("PDF oluştur").assertIsNotEnabled()
        ui.onNodeWithContentDescription("Proje adını değiştir").performClick()
        ui.onNode(hasSetTextAction()).performTextReplacement("Biyoloji — Hücre")
        ui.onNodeWithText("Kaydet").performClick()
        ui.waitUntil(10000) {ui.onAllNodesWithText("Biyoloji — Hücre").fetchSemanticsNodes().isNotEmpty()}
        screenshot("tablet-project.png")
    }
    private fun screenshot(name:String) {
        ui.waitForIdle()
        lateinit var image:android.graphics.Bitmap
        ui.runOnUiThread {
            val view=ui.activity.window.decorView
            image=android.graphics.Bitmap.createBitmap(view.width,view.height,android.graphics.Bitmap.Config.ARGB_8888)
            view.draw(android.graphics.Canvas(image))
        }
        val file=File("build/test-artifacts/$name").apply {parentFile!!.mkdirs()}
        file.outputStream().use {image.compress(android.graphics.Bitmap.CompressFormat.PNG,100,it)}
    }
}
