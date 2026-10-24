package org.studyfuse.data

import androidx.room.*
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.flow.Flow

@Entity(tableName="projects")
data class Project(
    @PrimaryKey val id: String,
    val name: String,
    val created: Long = System.currentTimeMillis(),
    val status: String = "READY",
    val settings: String = "{}",
    val output: String? = null,
    val report: String? = null,
    val message: String = "",
    val workId:String? = null
)
@Entity(tableName="sources", foreignKeys=[ForeignKey(entity=Project::class,
    parentColumns=["id"],childColumns=["projectId"],onDelete=ForeignKey.CASCADE)],indices=[Index("projectId")])
data class Source(
    @PrimaryKey val id: String,
    val projectId: String,
    val name: String,
    val section: String,
    val position: Int,
    val originalPath: String,
    val activePath: String,
    val bytes: Long,
    val sha256: String,
    val format: String,
    val replacement: Boolean = false,
    val activeSha256:String = sha256,
    @ColumnInfo(defaultValue="'{}'") val pagination:String = "{}",
    @ColumnInfo(defaultValue="0") val removedAt:Long = 0
)

@Dao
abstract class StudyDao {
    @Query("SELECT * FROM projects ORDER BY created DESC") abstract fun projects(): Flow<List<Project>>
    @Query("SELECT * FROM projects WHERE id=:id") abstract fun watchProject(id:String): Flow<Project?>
    @Query("SELECT * FROM projects WHERE id=:id") abstract suspend fun project(id:String): Project?
    @Query("SELECT * FROM sources WHERE projectId=:id AND removedAt=0 ORDER BY position,id") abstract fun sources(id:String): Flow<List<Source>>
    @Query("SELECT * FROM sources WHERE projectId=:id AND removedAt=0 ORDER BY position,id") abstract suspend fun snapshot(id:String): List<Source>
    @Query("SELECT * FROM sources WHERE projectId=:id AND removedAt>0 ORDER BY removedAt DESC") abstract suspend fun trash(id:String): List<Source>
    @Query("SELECT * FROM sources WHERE id=:id") abstract suspend fun source(id:String):Source?
    @Insert abstract suspend fun addProject(project:Project)
    @Update abstract suspend fun updateProject(project:Project)
    @Insert abstract suspend fun addSource(source:Source)
    @Update abstract suspend fun updateSource(source:Source)
    @Delete abstract suspend fun deleteSource(source:Source)
    @Delete abstract suspend fun deleteProject(project:Project)
    @Transaction open suspend fun restoreProject(project:Project,sources:List<Source>) {
        addProject(project);sources.forEach {addSource(it)}
    }
    @Transaction open suspend fun editSources(projectId:String,sources:List<Source>) {
        val p=project(projectId) ?: error("Proje bulunamadı")
        check(p.status !in setOf("RUNNING","QUEUED")) {"İşlem sürüyor"}
        sources.forEach {check(it.projectId==projectId);updateSource(it)}
        updateProject(p.copy(status="READY",output=null,report=null,message="",workId=null))
    }
    @Transaction open suspend fun commitImport(source:Source,replacing:Boolean) {
        val p=project(source.projectId) ?: error("Proje bulunamadı")
        check(p.status !in setOf("RUNNING","QUEUED")) {"İşlem sırasında kaynaklar değiştirilemez"}
        if(replacing) updateSource(source) else addSource(source)
        updateProject(p.copy(status="READY",output=null,report=null,message="",workId=null))
    }
    @Query("UPDATE projects SET status=:status, message=:message WHERE id=:id")
    abstract suspend fun status(id:String,status:String,message:String)
    @Query("UPDATE projects SET status=:status, message=:message WHERE id=:id AND workId=:workId")
    abstract suspend fun workStatus(id:String,workId:String,status:String,message:String)
    @Transaction open suspend fun publish(id:String,workId:String,status:String,output:String,report:String,message:String):Boolean {
        val p=project(id) ?: return false
        if(p.workId!=workId) return false
        updateProject(p.copy(status=status,output=output,report=report,message=message))
        return true
    }
    @Transaction open suspend fun reorder(items:List<Source>) {
        items.forEachIndexed { index,s -> updateSource(s.copy(position=index)) }
    }
}

@Database(entities=[Project::class,Source::class],version=2,exportSchema=false)
abstract class StudyDatabase:RoomDatabase() {
    abstract fun dao():StudyDao
    companion object {
        val MIGRATION_1_2=object:Migration(1,2) {
            override fun migrate(db:SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE sources ADD COLUMN pagination TEXT NOT NULL DEFAULT '{}'")
                db.execSQL("ALTER TABLE sources ADD COLUMN removedAt INTEGER NOT NULL DEFAULT 0")
            }
        }
    }
}
