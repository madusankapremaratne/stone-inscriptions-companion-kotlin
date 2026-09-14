package org.sellipi.companion.domain.repository

import kotlinx.coroutines.flow.Flow
import org.sellipi.companion.domain.model.CaptureSession
import org.sellipi.companion.domain.model.GlyphOccurrence
import org.sellipi.companion.domain.model.Inscription
import org.sellipi.companion.domain.model.Letter
import org.sellipi.companion.domain.model.LetterForm
import org.sellipi.companion.domain.model.Period
import org.sellipi.companion.domain.model.Site
import org.sellipi.companion.domain.model.TranscriptionLine

interface SiteRepository {
    fun getSites(): Flow<List<Site>>
    suspend fun getSiteById(siteId: String): Site?
}

interface InscriptionRepository {
    fun getInscriptionsForSite(siteId: String): Flow<List<Inscription>>
    fun getAllInscriptions(): Flow<List<Inscription>>
    suspend fun getInscriptionById(id: String): Inscription?
    fun getTranscriptionLines(inscriptionId: String): Flow<List<TranscriptionLine>>
    fun getGlyphsForInscription(inscriptionId: String): Flow<List<GlyphOccurrence>>
}

interface EvolutionRepository {
    fun getAllLetters(): Flow<List<Letter>>
    suspend fun getLetterById(letterId: String): Letter?
    fun getAllPeriods(): Flow<List<Period>>
    fun getFormsForLetter(letterId: String): Flow<List<LetterForm>>
}

interface ResearcherCaptureRepository {
    suspend fun saveCaptureSession(session: CaptureSession)
    fun getAllSessions(): Flow<List<CaptureSession>>
}
