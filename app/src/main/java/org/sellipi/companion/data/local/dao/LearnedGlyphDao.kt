package org.sellipi.companion.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow
import org.sellipi.companion.data.local.entity.LearnedGlyphEntity

@Dao
interface LearnedGlyphDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertLearnedGlyph(glyph: LearnedGlyphEntity)

    @Query("SELECT * FROM learned_glyphs ORDER BY timestamp DESC")
    fun getAllLearnedGlyphsFlow(): Flow<List<LearnedGlyphEntity>>

    @Query("SELECT * FROM learned_glyphs WHERE letter_id = :letterId")
    suspend fun getLearnedGlyphsForLetter(letterId: String): List<LearnedGlyphEntity>

    @Query("SELECT COUNT(*) FROM learned_glyphs")
    suspend fun getLearnedCount(): Int
}
