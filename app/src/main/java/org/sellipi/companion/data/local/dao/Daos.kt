package org.sellipi.companion.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow
import org.sellipi.companion.data.local.entity.CaptureSessionEntity
import org.sellipi.companion.data.local.entity.GlyphOccurrenceEntity
import org.sellipi.companion.data.local.entity.InscriptionEntity
import org.sellipi.companion.data.local.entity.LetterEntity
import org.sellipi.companion.data.local.entity.LetterFormEntity
import org.sellipi.companion.data.local.entity.PeriodEntity
import org.sellipi.companion.data.local.entity.SiteEntity
import org.sellipi.companion.data.local.entity.TranscriptionLineEntity

@Dao
interface SiteDao {
    @Query("SELECT * FROM sites ORDER BY name_en ASC")
    fun getAllSitesFlow(): Flow<List<SiteEntity>>

    @Query("SELECT * FROM sites WHERE id = :siteId")
    suspend fun getSiteById(siteId: String): SiteEntity?
}

@Dao
interface InscriptionDao {
    @Query("SELECT * FROM inscriptions WHERE site_id = :siteId ORDER BY date_range_start ASC")
    fun getInscriptionsBySiteFlow(siteId: String): Flow<List<InscriptionEntity>>

    @Query("SELECT * FROM inscriptions WHERE id = :inscriptionId")
    suspend fun getInscriptionById(inscriptionId: String): InscriptionEntity?

    @Query("SELECT * FROM inscriptions ORDER BY date_range_start ASC")
    fun getAllInscriptionsFlow(): Flow<List<InscriptionEntity>>
}

@Dao
interface PeriodDao {
    @Query("SELECT * FROM periods ORDER BY period_number ASC")
    fun getAllPeriodsFlow(): Flow<List<PeriodEntity>>

    @Query("SELECT * FROM periods WHERE id = :periodId")
    suspend fun getPeriodById(periodId: String): PeriodEntity?
}

@Dao
interface LetterDao {
    @Query("SELECT * FROM letters ORDER BY row_index ASC")
    fun getAllLettersFlow(): Flow<List<LetterEntity>>

    @Query("SELECT * FROM letters WHERE id = :letterId")
    suspend fun getLetterById(letterId: String): LetterEntity?
}

@Dao
interface LetterFormDao {
    @Query("SELECT * FROM letter_forms WHERE letter_id = :letterId ORDER BY grid_col ASC")
    fun getFormsForLetterFlow(letterId: String): Flow<List<LetterFormEntity>>

    @Query("SELECT * FROM letter_forms WHERE letter_id = :letterId AND period_id = :periodId")
    suspend fun getForm(letterId: String, periodId: String): LetterFormEntity?
}

@Dao
interface TranscriptionDao {
    @Query("SELECT * FROM transcription_lines WHERE inscription_id = :inscriptionId ORDER BY line_number ASC")
    fun getLinesForInscriptionFlow(inscriptionId: String): Flow<List<TranscriptionLineEntity>>

    @Query("SELECT * FROM glyph_occurrences WHERE transcription_line_id = :lineId ORDER BY position ASC")
    suspend fun getGlyphsForLine(lineId: String): List<GlyphOccurrenceEntity>

    @Query("SELECT * FROM glyph_occurrences WHERE transcription_line_id IN (SELECT id FROM transcription_lines WHERE inscription_id = :inscriptionId)")
    fun getAllGlyphsForInscriptionFlow(inscriptionId: String): Flow<List<GlyphOccurrenceEntity>>
}

@Dao
interface CaptureDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSession(session: CaptureSessionEntity)

    @Query("SELECT * FROM capture_sessions ORDER BY timestamp DESC")
    fun getAllSessionsFlow(): Flow<List<CaptureSessionEntity>>
}
