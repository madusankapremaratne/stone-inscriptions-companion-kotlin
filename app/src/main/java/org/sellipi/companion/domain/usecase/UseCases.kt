package org.sellipi.companion.domain.usecase

import android.location.Location
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import org.sellipi.companion.domain.model.CaptureSession
import org.sellipi.companion.domain.model.GlyphOccurrence
import org.sellipi.companion.domain.model.Inscription
import org.sellipi.companion.domain.model.Letter
import org.sellipi.companion.domain.model.LetterForm
import org.sellipi.companion.domain.model.Period
import org.sellipi.companion.domain.model.Site
import org.sellipi.companion.domain.model.TranscriptionLine
import org.sellipi.companion.domain.repository.EvolutionRepository
import org.sellipi.companion.domain.repository.InscriptionRepository
import org.sellipi.companion.domain.repository.ResearcherCaptureRepository
import org.sellipi.companion.domain.repository.SiteRepository

class GetNearbySitesUseCase(private val siteRepo: SiteRepository) {
    operator fun invoke(userLat: Double?, userLon: Double?): Flow<List<Site>> =
        siteRepo.getSites().map { sites ->
            if (userLat == null || userLon == null) {
                sites
            } else {
                sites.map { site ->
                    val results = FloatArray(1)
                    Location.distanceBetween(userLat, userLon, site.latitude, site.longitude, results)
                    site.copy(distanceFromUserM = results[0])
                }.sortedBy { it.distanceFromUserM ?: Float.MAX_VALUE }
            }
        }
}

class GetInscriptionDetailsUseCase(
    private val inscriptionRepo: InscriptionRepository,
    private val evolutionRepo: EvolutionRepository
) {
    suspend fun getInscription(id: String): Inscription? = inscriptionRepo.getInscriptionById(id)

    fun getTranscriptionWithGlyphs(inscriptionId: String): Flow<List<TranscriptionLine>> {
        return combine(
            inscriptionRepo.getTranscriptionLines(inscriptionId),
            inscriptionRepo.getGlyphsForInscription(inscriptionId),
            evolutionRepo.getAllLetters()
        ) { lines, glyphs, letters ->
            val letterMap = letters.associateBy { it.id }
            val glyphsByLine = glyphs.groupBy { it.transcriptionLineId }
            lines.map { line ->
                val lineGlyphs = (glyphsByLine[line.id] ?: emptyList()).map { g ->
                    g.copy(letter = letterMap[g.letterId])
                }.sortedBy { it.position }
                line.copy(glyphs = lineGlyphs)
            }
        }
    }
}

data class LetterEvolutionData(
    val letter: Letter,
    val timeline: List<LetterForm>,
    val attestedCount: Int,
    val gapCount: Int
)

class GetLetterEvolutionUseCase(private val evolutionRepo: EvolutionRepository) {
    fun getEvolutionForLetter(letterId: String): Flow<LetterEvolutionData?> {
        return combine(
            evolutionRepo.getAllLetters(),
            evolutionRepo.getAllPeriods(),
            evolutionRepo.getFormsForLetter(letterId)
        ) { letters, periods, forms ->
            val letter = letters.find { it.id == letterId } ?: return@combine null
            val periodMap = periods.associateBy { it.id }
            
            // Build 18-period timeline with attached periods
            val timeline = periods.map { period ->
                val existingForm = forms.find { it.periodId == period.id }
                existingForm?.copy(period = period) ?: LetterForm(
                    id = "LF_${letter.id}_${period.id}",
                    letterId = letter.id,
                    periodId = period.id,
                    gridRow = letter.rowIndex,
                    gridCol = period.periodNumber,
                    vectorPath = null,
                    imageAssetPath = null,
                    isAttested = false,
                    isReconstructed = false,
                    sourceInscriptionRef = null,
                    provenance = "unattested_gap",
                    period = period
                )
            }.sortedBy { it.gridCol }

            val attested = timeline.count { it.isAttested }
            val gaps = timeline.count { !it.isAttested }

            LetterEvolutionData(
                letter = letter,
                timeline = timeline,
                attestedCount = attested,
                gapCount = gaps
            )
        }
    }
}

class SaveResearcherCaptureUseCase(private val captureRepo: ResearcherCaptureRepository) {
    suspend operator fun invoke(session: CaptureSession) {
        captureRepo.saveCaptureSession(session)
    }
}
