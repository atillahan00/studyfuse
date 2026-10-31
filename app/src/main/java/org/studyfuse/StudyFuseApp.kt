package org.studyfuse

import android.app.Application
import androidx.room.Room
import org.studyfuse.data.*
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader

class StudyFuseApp:Application(),androidx.work.Configuration.Provider {
    override val workManagerConfiguration:androidx.work.Configuration
        get()=androidx.work.Configuration.Builder().build()
    val database by lazy { Room.databaseBuilder(this,StudyDatabase::class.java,"studyfuse.db").addMigrations(StudyDatabase.MIGRATION_1_2).build() }
    val repository by lazy { Repository(this,database.dao()) }
    override fun onCreate() {
        super.onCreate()
        if(Application.getProcessName()==packageName+":local_ai"){
            val previous=Thread.getDefaultUncaughtExceptionHandler()
            Thread.setDefaultUncaughtExceptionHandler{thread,error->
                runCatching{org.studyfuse.learning.AiDiagnostics(this).fatal(error)}
                if(previous!=null)previous.uncaughtException(thread,error)else android.os.Process.killProcess(android.os.Process.myPid())
            }
        }
        PDFBoxResourceLoader.init(this)
        // Only abandoned partial imports are removed here. WorkManager owns processing scratch space.
        filesDir.resolve("projects").walkTopDown().filter { it.isFile && it.extension=="part" &&
            System.currentTimeMillis()-it.lastModified()>24*60*60*1000L }.forEach { it.delete() }
    }
}
