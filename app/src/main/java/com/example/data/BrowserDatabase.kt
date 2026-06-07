package com.example.data

import android.content.Context
import androidx.room.*
import kotlinx.coroutines.flow.Flow

// --- Entities ---

@Entity(tableName = "bookmarks")
data class Bookmark(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val title: String,
    val url: String,
    val timestamp: Long = System.currentTimeMillis()
)

@Entity(tableName = "history")
data class History(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val title: String,
    val url: String,
    val timestamp: Long = System.currentTimeMillis()
)

@Entity(tableName = "custom_scripts")
data class CustomScript(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val name: String,
    val description: String,
    val scriptCode: String,
    val isEnabled: Boolean = true,
    val isBuiltIn: Boolean = false
)

@Entity(tableName = "study_notes")
data class StudyNote(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val title: String,
    val content: String,
    val url: String,
    val timestamp: Long = System.currentTimeMillis()
)

// --- DAOs ---

@Dao
interface BrowserDao {
    // Bookmarks
    @Query("SELECT * FROM bookmarks ORDER BY timestamp DESC")
    fun getAllBookmarks(): Flow<List<Bookmark>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBookmark(bookmark: Bookmark)

    @Delete
    suspend fun deleteBookmark(bookmark: Bookmark)

    @Query("SELECT EXISTS(SELECT 1 FROM bookmarks WHERE url = :url LIMIT 1)")
    suspend fun isBookmarked(url: String): Boolean

    @Query("DELETE FROM bookmarks WHERE url = :url")
    suspend fun deleteBookmarkByUrl(url: String)

    // History
    @Query("SELECT * FROM history ORDER BY timestamp DESC")
    fun getAllHistory(): Flow<List<History>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertHistory(history: History)

    @Delete
    suspend fun deleteHistory(history: History)

    @Query("DELETE FROM history")
    suspend fun clearAllHistory()

    // Custom Scripts / Add-ons / Extensions
    @Query("SELECT * FROM custom_scripts")
    fun getAllScripts(): Flow<List<CustomScript>>

    @Query("SELECT * FROM custom_scripts WHERE isEnabled = 1")
    suspend fun getActiveScripts(): List<CustomScript>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertScript(script: CustomScript)

    @Delete
    suspend fun deleteScript(script: CustomScript)

    @Query("UPDATE custom_scripts SET isEnabled = :enabled WHERE id = :id")
    suspend fun toggleScriptState(id: Int, enabled: Boolean)

    // Study Notes
    @Query("SELECT * FROM study_notes ORDER BY timestamp DESC")
    fun getAllStudyNotes(): Flow<List<StudyNote>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertStudyNote(note: StudyNote)

    @Delete
    suspend fun deleteStudyNote(note: StudyNote)

    @Query("DELETE FROM study_notes")
    suspend fun clearAllStudyNotes()
}

// --- Database ---

@Database(entities = [Bookmark::class, History::class, CustomScript::class, StudyNote::class], version = 2, exportSchema = false)
abstract class BrowserDatabase : RoomDatabase() {
    abstract fun browserDao(): BrowserDao

    companion object {
        @Volatile
        private var INSTANCE: BrowserDatabase? = null

        fun getDatabase(context: Context): BrowserDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    BrowserDatabase::class.java,
                    "sarie_browser_db"
                )
                .fallbackToDestructiveMigration()
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}

// --- Repository ---

class BrowserRepository(private val dao: BrowserDao) {
    val allBookmarks: Flow<List<Bookmark>> = dao.getAllBookmarks()
    val allHistory: Flow<List<History>> = dao.getAllHistory()
    val allScripts: Flow<List<CustomScript>> = dao.getAllScripts()
    val allStudyNotes: Flow<List<StudyNote>> = dao.getAllStudyNotes()

    // Bookmarks API
    suspend fun addBookmark(title: String, url: String) {
        dao.insertBookmark(Bookmark(title = title, url = url))
    }

    suspend fun deleteBookmark(bookmark: Bookmark) {
        dao.deleteBookmark(bookmark)
    }

    suspend fun deleteBookmarkByUrl(url: String) {
        dao.deleteBookmarkByUrl(url)
    }

    suspend fun isBookmarked(url: String): Boolean {
        return dao.isBookmarked(url)
    }

    // History API
    suspend fun addHistory(title: String, url: String) {
        dao.insertHistory(History(title = title, url = url))
    }

    suspend fun deleteHistory(history: History) {
        dao.deleteHistory(history)
    }

    suspend fun clearHistory() {
        dao.clearAllHistory()
    }

    // Custom Scripts / Extensions API
    suspend fun addScript(name: String, description: String, scriptCode: String, isEnabled: Boolean = true) {
        dao.insertScript(CustomScript(name = name, description = description, scriptCode = scriptCode, isEnabled = isEnabled, isBuiltIn = false))
    }

    suspend fun insertRawScript(script: CustomScript) {
        dao.insertScript(script)
    }

    suspend fun deleteScript(script: CustomScript) {
        if (!script.isBuiltIn) {
            dao.deleteScript(script)
        }
    }

    suspend fun toggleScript(id: Int, enabled: Boolean) {
        dao.toggleScriptState(id, enabled)
    }

    suspend fun getActiveScripts(): List<CustomScript> {
        return dao.getActiveScripts()
    }

    // Study Notes API
    suspend fun addStudyNote(title: String, content: String, url: String) {
        dao.insertStudyNote(StudyNote(title = title, content = content, url = url))
    }

    suspend fun deleteStudyNote(note: StudyNote) {
        dao.deleteStudyNote(note)
    }

    suspend fun clearAllStudyNotes() {
        dao.clearAllStudyNotes()
    }
}
