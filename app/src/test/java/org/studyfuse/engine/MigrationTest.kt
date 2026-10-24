package org.studyfuse.engine

import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.studyfuse.StudyFuseApp
import org.studyfuse.data.StudyDatabase
import java.util.UUID

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[34],application=StudyFuseApp::class)
class MigrationTest {
    @Test fun versionOneProjectsMigrateWithoutDataLoss()=runBlocking {
        val app=ApplicationProvider.getApplicationContext<StudyFuseApp>();val name="migration-${UUID.randomUUID()}.db"
        val file=app.getDatabasePath(name);file.parentFile!!.mkdirs()
        SQLiteDatabase.openOrCreateDatabase(file,null).use{db->
            db.execSQL("CREATE TABLE projects (id TEXT NOT NULL PRIMARY KEY,name TEXT NOT NULL,created INTEGER NOT NULL,status TEXT NOT NULL,settings TEXT NOT NULL,output TEXT,report TEXT,message TEXT NOT NULL,workId TEXT)")
            db.execSQL("CREATE TABLE sources (id TEXT NOT NULL PRIMARY KEY,projectId TEXT NOT NULL,name TEXT NOT NULL,section TEXT NOT NULL,position INTEGER NOT NULL,originalPath TEXT NOT NULL,activePath TEXT NOT NULL,bytes INTEGER NOT NULL,sha256 TEXT NOT NULL,format TEXT NOT NULL,replacement INTEGER NOT NULL,activeSha256 TEXT NOT NULL,FOREIGN KEY(projectId) REFERENCES projects(id) ON UPDATE NO ACTION ON DELETE CASCADE)")
            db.execSQL("CREATE INDEX index_sources_projectId ON sources(projectId)")
            db.execSQL("INSERT INTO projects VALUES ('project','Existing project',1,'READY','{}',NULL,NULL,'',NULL)")
            db.execSQL("INSERT INTO sources VALUES ('source','project','old.pdf','Biologie',0,'original','original',123,'hash','PDF',0,'hash')")
            db.version=1
        }
        val db=Room.databaseBuilder(app,StudyDatabase::class.java,name).addMigrations(StudyDatabase.MIGRATION_1_2).build()
        try{assertEquals("Existing project",db.dao().project("project")!!.name);val s=db.dao().snapshot("project").single();assertEquals("hash",s.sha256);assertEquals("{}",s.pagination);assertEquals(0L,s.removedAt)}
        finally{db.close();app.deleteDatabase(name)}
    }
}
