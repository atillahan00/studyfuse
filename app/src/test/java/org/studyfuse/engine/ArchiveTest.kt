package org.studyfuse.engine

import androidx.core.content.FileProvider
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import org.json.*
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.studyfuse.StudyFuseApp
import org.studyfuse.data.*
import java.io.*
import java.util.UUID
import java.util.zip.*

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[34],application=StudyFuseApp::class)
class ArchiveTest {
    private lateinit var app:StudyFuseApp
    private lateinit var db:StudyDatabase
    private lateinit var repo:Repository
    @Before fun setup(){app=ApplicationProvider.getApplicationContext();org.robolectric.util.ReflectionHelpers.getStaticField<MutableMap<String,Any>>(FileProvider::class.java,"sCache").clear();db=Room.inMemoryDatabaseBuilder(app,StudyDatabase::class.java).build();repo=Repository(app,db.dao())}
    @After fun close(){db.close()}
    private fun file(name:String,text:String)=File(app.filesDir,"projects/input/${UUID.randomUUID()}/$name").apply{parentFile!!.mkdirs();writeText(text)}
    private fun uri(f:File)=FileProvider.getUriForFile(app,"org.studyfuse.files",f)
    @Test fun roundTripKeepsOriginalReplacementOrderCutsAndSettings()=runBlocking {
        val id=repo.create();val word=repo.import(id,uri(file("Zelle.docx","original Office bytes")))
        val replacement=repo.import(id,uri(file("Zelle.pdf","%PDF-1.7 exported PDF bytes")),word).copy(pagination="{\"1\":[700.0]}")
        db.dao().updateSource(replacement)
        val txt=repo.import(id,uri(file("Türkçe.txt","ğ ü ş ı ö ç ä ö ü ß")))
        db.dao().reorder(listOf(txt,replacement));val p=db.dao().project(id)!!
        db.dao().updateProject(p.copy(name="Biyoloji",settings="{\"mode\":\"ARCHIVE\"}"))
        val bytes=ByteArrayOutputStream();ProjectArchive(repo).export(id,bytes)
        val restored=ProjectArchive(repo).restore(ByteArrayInputStream(bytes.toByteArray()))
        assertNotEquals(id,restored);assertEquals("Biyoloji",db.dao().project(restored)!!.name)
        assertEquals(p.copy(settings="{\"mode\":\"ARCHIVE\"}").settings,db.dao().project(restored)!!.settings)
        val rows=db.dao().snapshot(restored);assertEquals(listOf("Türkçe.txt","Zelle.docx"),rows.map{it.name})
        assertEquals("{\"1\":[700.0]}",rows[1].pagination);assertTrue(rows[1].replacement)
        assertEquals("original Office bytes",File(rows[1].originalPath).readText())
        assertEquals("%PDF-1.7 exported PDF bytes",File(rows[1].activePath).readText())
        rows.forEach{assertEquals(it.sha256,sha256(File(it.originalPath)));assertEquals(it.activeSha256,sha256(File(it.activePath)))}
    }
    @Test fun zipTraversalAndUndeclaredFilesAreRejectedAtomically()=runBlocking {
        val manifest=JSONObject().put("schemaVersion",1).put("name","malicious").put("settings","{}").put("sources",JSONArray())
        val data=ByteArrayOutputStream();ZipOutputStream(data).use{z->z.putNextEntry(ZipEntry("manifest.json"));z.write(manifest.toString().toByteArray());z.closeEntry();z.putNextEntry(ZipEntry("../../escape.txt"));z.write("bad".toByteArray());z.closeEntry()}
        try{ProjectArchive(repo).restore(ByteArrayInputStream(data.toByteArray()));fail("Traversal accepted")}catch(_:IllegalStateException){}
        assertTrue(db.dao().projects().first().isEmpty());assertFalse(File(app.filesDir,"escape.txt").exists())
    }
    @Test fun changedSourceBytesPreventMisleadingBackup()=runBlocking {
        val id=repo.create();val s=repo.import(id,uri(file("notes.txt","original")));File(s.originalPath).writeText("changed")
        try{ProjectArchive(repo).export(id,ByteArrayOutputStream());fail("Corrupt source backed up")}catch(_:IllegalStateException){}
    }
    @Test fun removedSourcesCanBeRestoredWithoutDeletingTheirBytes()=runBlocking {
        val id=repo.create();val s=repo.import(id,uri(file("notes.txt","keep these bytes")))
        db.dao().editSources(id,listOf(s.copy(removedAt=123)))
        assertTrue(db.dao().snapshot(id).isEmpty());assertTrue(File(s.originalPath).exists())
        db.dao().editSources(id,db.dao().trash(id).map{it.copy(removedAt=0)})
        assertEquals(s.id,db.dao().snapshot(id).single().id)
    }
    @Test fun backupRetainsRemovedSourceBytesAndUndoState()=runBlocking {
        val id=repo.create();val s=repo.import(id,uri(file("removed.txt","retain in backup")))
        db.dao().editSources(id,listOf(s.copy(removedAt=123)))
        val out=ByteArrayOutputStream();ProjectArchive(repo).export(id,out)
        val copy=ProjectArchive(repo).restore(ByteArrayInputStream(out.toByteArray()))
        assertTrue(db.dao().snapshot(copy).isEmpty());val item=db.dao().trash(copy).single()
        assertEquals(123L,item.removedAt);assertEquals("retain in backup",File(item.originalPath).readText())
    }
    @Test fun truncatedBackupDoesNotPublishAPartialProject()=runBlocking {
        val id=repo.create();repo.import(id,uri(file("notes.txt","must survive")))
        val out=ByteArrayOutputStream();ProjectArchive(repo).export(id,out)
        val bytes=out.toByteArray().copyOf(35)
        try{ProjectArchive(repo).restore(ByteArrayInputStream(bytes));fail("Truncated backup accepted")}catch(_:Exception){}
        assertEquals(1,db.dao().projects().first().size)
    }
    @Test fun backupRetainsExamSettingsNotesAndReviewButExcludesModelAndScratch()=runBlocking {
        val id=repo.create();repo.import(id,uri(file("notes.txt","Die Zelle enthält DNA.")))
        val store=org.studyfuse.learning.ExamStore(repo.directory(id));store.saveConfig(org.studyfuse.learning.ExamConfig(mode=org.studyfuse.core.KnowledgeMode.ENRICHED))
        store.updateReview("card"){it.put("approved",true).put("lapses",2).put("dueDay",10)}
        val key="a".repeat(64);org.studyfuse.learning.atomicJson(store.unit(key),JSONObject().put("key",key).put("transcription","Die Zelle enthält DNA."))
        File(store.root,"scratch/image.png").apply{parentFile!!.mkdirs();writeText("temporary")}
        val out=ByteArrayOutputStream();ProjectArchive(repo).export(id,out)
        val copy=ProjectArchive(repo).restore(ByteArrayInputStream(out.toByteArray()));val restored=org.studyfuse.learning.ExamStore(repo.directory(copy))
        assertEquals(org.studyfuse.core.KnowledgeMode.ENRICHED,restored.config().mode);assertTrue(restored.review().getJSONObject("card").getBoolean("approved"))
        assertEquals("Die Zelle enthält DNA.",org.studyfuse.learning.readJson(restored.unit(key)).getString("transcription"));assertFalse(File(restored.root,"scratch").exists())
    }
}
