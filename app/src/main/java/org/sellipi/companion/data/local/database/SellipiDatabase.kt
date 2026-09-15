package org.sellipi.companion.data.local.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import org.sellipi.companion.data.local.dao.CaptureDao
import org.sellipi.companion.data.local.dao.InscriptionDao
import org.sellipi.companion.data.local.dao.LetterDao
import org.sellipi.companion.data.local.dao.LetterFormDao
import org.sellipi.companion.data.local.dao.PeriodDao
import org.sellipi.companion.data.local.dao.SiteDao
import org.sellipi.companion.data.local.dao.TranscriptionDao
import org.sellipi.companion.data.local.entity.CaptureSessionEntity
import org.sellipi.companion.data.local.entity.GlyphOccurrenceEntity
import org.sellipi.companion.data.local.entity.InscriptionEntity
import org.sellipi.companion.data.local.entity.LetterEntity
import org.sellipi.companion.data.local.entity.LetterFormEntity
import org.sellipi.companion.data.local.entity.PeriodEntity
import org.sellipi.companion.data.local.entity.SiteEntity
import org.sellipi.companion.data.local.entity.TranscriptionLineEntity

@Database(
    entities = [
        SiteEntity::class,
        PeriodEntity::class,
        LetterEntity::class,
        LetterFormEntity::class,
        InscriptionEntity::class,
        TranscriptionLineEntity::class,
        GlyphOccurrenceEntity::class,
        CaptureSessionEntity::class,
        org.sellipi.companion.data.local.entity.LearnedGlyphEntity::class
    ],
    version = 1,
    exportSchema = false
)
abstract class SellipiDatabase : RoomDatabase() {
    abstract fun siteDao(): SiteDao
    abstract fun inscriptionDao(): InscriptionDao
    abstract fun periodDao(): PeriodDao
    abstract fun letterDao(): LetterDao
    abstract fun letterFormDao(): LetterFormDao
    abstract fun transcriptionDao(): TranscriptionDao
    abstract fun captureDao(): CaptureDao
    abstract fun learnedGlyphDao(): org.sellipi.companion.data.local.dao.LearnedGlyphDao

    companion object {
        @Volatile
        private var INSTANCE: SellipiDatabase? = null

        fun getInstance(context: Context): SellipiDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    SellipiDatabase::class.java,
                    "sellipi.db"
                )
                .createFromAsset("databases/sellipi.db")
                .fallbackToDestructiveMigration()
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
