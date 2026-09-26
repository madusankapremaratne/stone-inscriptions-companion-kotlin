package org.sellipi.companion.data.lessons

import android.content.Context
import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import kotlinx.coroutines.flow.Flow

@Entity(
    tableName = "lessons",
    indices = [Index("type"), Index("synced_at")]
)
data class LessonEntity(
    @PrimaryKey val id: String,
    val type: String,
    @ColumnInfo(name = "inscription_id") val inscriptionId: String?,
    @ColumnInfo(name = "pack_version") val packVersion: Int,
    @ColumnInfo(name = "app_version") val appVersion: String,
    @ColumnInfo(name = "payload_schema_version") val payloadSchemaVersion: Int,
    @ColumnInfo(name = "payload_json") val payloadJson: String,
    val trust: String,
    @ColumnInfo(name = "created_at") val createdAt: Long,
    @ColumnInfo(name = "synced_at") val syncedAt: Long?
)

@Dao
interface LessonDao {
    @Insert
    suspend fun insert(lesson: LessonEntity)

    @Query("SELECT * FROM lessons ORDER BY created_at ASC")
    suspend fun getAll(): List<LessonEntity>

    @Query("SELECT COUNT(*) FROM lessons")
    fun observeCount(): Flow<Int>
}

/**
 * Kept separate from [org.sellipi.companion.data.local.database.SellipiDatabase] on purpose:
 * that database is re-seeded from assets with destructive migration on every content update,
 * which would wipe lessons. This one must only ever change through real migrations.
 */
@Database(entities = [LessonEntity::class], version = 1, exportSchema = true)
abstract class LessonsDatabase : RoomDatabase() {
    abstract fun lessonDao(): LessonDao

    companion object {
        @Volatile
        private var INSTANCE: LessonsDatabase? = null

        fun getInstance(context: Context): LessonsDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    LessonsDatabase::class.java,
                    "sellipi_lessons.db"
                ).build().also { INSTANCE = it }
            }
        }
    }
}
