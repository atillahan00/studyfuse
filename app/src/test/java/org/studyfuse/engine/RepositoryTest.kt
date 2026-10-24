package org.studyfuse.engine

import androidx.core.content.FileProvider
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.*
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.studyfuse.StudyFuseApp
import org.studyfuse.data.*
import java.io.File
import java.util.UUID

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[34],application=StudyFuseApp::class)
class RepositoryTest {
    private lateinit var app:StudyFuseApp
    private lateinit var db:StudyDatabase
    private lateinit var repo:Repository
    @Before fun setup() {
        app=ApplicationProvider.getApplicationContext()
        org.robolectric.util.ReflectionHelpers.getStaticField<MutableMap<String,Any>>(FileProvider::class.java,"sCache").clear()
        db=Room.inMemoryDatabaseBuilder(app,StudyDatabase::class.java).build()
        repo=Repository(app,db.dao())
    }
    @After fun close() {db.close()}
    private fun file(name:String,text:String):File=File(app.filesDir,"projects/fixtures/${UUID.randomUUID()}/$name").apply {parentFile!!.mkdirs();writeText(text)}
    private fun uri(file:File)=FileProvider.getUriForFile(app,"org.studyfuse.files",file)
    @Test fun safImportIsDurableAndCanBeReordered()=runBlocking {
        val id=repo.create()
        val a=repo.import(id,uri(file("öğrenci.txt","äöüß ğüşıöç")))
        val b=repo.import(id,uri(file("second.txt","second")))
        assertEquals("äöüß ğüşıöç",File(a.originalPath).readText())
        assertEquals(a.sha256,sha256(File(a.originalPath)))
        db.dao().reorder(listOf(b,a))
        assertEquals(listOf(b.id,a.id),db.dao().snapshot(id).map {it.id})
    }
    @Test fun replacementKeepsOriginalAndSectionPosition()=runBlocking {
        val id=repo.create();val original=repo.import(id,uri(file("slides.pptx","not a converted presentation")))
        val replacement=repo.import(id,uri(file("slides.pdf","%PDF-1.7\nconversion must be validated")),original)
        assertEquals(original.originalPath,replacement.originalPath)
        assertEquals(original.section,replacement.section);assertEquals(original.position,replacement.position)
        assertNotEquals(original.activePath,replacement.activePath)
        assertEquals(replacement.activeSha256,sha256(File(replacement.activePath)))
        assertTrue(File(original.originalPath).exists());assertTrue(replacement.replacement)
    }
    @Test fun staleCancelledWorkerCannotOverwriteNewGeneration()=runBlocking {
        val id=repo.create();val p=db.dao().project(id)!!
        db.dao().updateProject(p.copy(workId="new-work",status="RUNNING"))
        db.dao().workStatus(id,"old-work","CANCELLED","stale")
        assertEquals("RUNNING",db.dao().project(id)!!.status)
        assertFalse(db.dao().publish(id,"old-work","DONE","[]","old-report","stale"))
        assertTrue(db.dao().publish(id,"new-work","DONE","[]","new-report","done"))
        assertEquals("new-report",db.dao().project(id)!!.report)
    }
    @Test fun emptyImportDoesNotCreatePhantomSource()=runBlocking {
        val id=repo.create()
        try {repo.import(id,uri(file("empty.pdf","")));fail("Empty file accepted")}
        catch(_:IllegalStateException) {}
        assertTrue(db.dao().snapshot(id).isEmpty())
        assertTrue(File(repo.directory(id),"originals").listFiles().isNullOrEmpty())
    }
    @Test fun failedProjectUpdateRollsBackSourceAndRemovesImportedCopy()=runBlocking {
        val id=repo.create()
        withContext(Dispatchers.IO) {
            db.openHelper.writableDatabase.execSQL("CREATE TRIGGER fail_project_update BEFORE UPDATE ON projects BEGIN SELECT RAISE(ABORT, 'simulated database failure'); END")
        }
        try {repo.import(id,uri(file("incoming.txt","complete source bytes")));fail("Database failure hidden")}
        catch(e:android.database.sqlite.SQLiteException) {assertTrue(e.message?.contains("simulated") ?: false)}
        assertTrue(db.dao().snapshot(id).isEmpty())
        assertTrue(File(repo.directory(id),"originals").listFiles().isNullOrEmpty())
    }
}
