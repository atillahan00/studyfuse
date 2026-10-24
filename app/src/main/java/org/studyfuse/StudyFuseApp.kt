package org.studyfuse

import android.app.Application
import androidx.room.Room
import org.studyfuse.data.*
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader

class StudyFuseApp:Application() {
    val database by lazy { Room.databaseBuilder(this,StudyDatabase::class.java,"studyfuse.db").build() }
    val repository by lazy { Repository(this,database.dao()) }
    override fun onCreate() {
        super.onCreate()
        PDFBoxResourceLoader.init(this)
        // Only abandoned partial imports are removed here. WorkManager owns processing scratch space.
        filesDir.resolve("projects").walkTopDown().filter { it.isFile && it.extension=="part" &&
            System.currentTimeMillis()-it.lastModified()>24*60*60*1000L }.forEach { it.delete() }
    }
}
