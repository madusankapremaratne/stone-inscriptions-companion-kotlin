package org.sellipi.companion.domain.usecase

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import org.sellipi.companion.domain.model.CaptureSession
import org.sellipi.companion.domain.model.GeoPoint
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
    /** Re-sorts whenever either the sites or the user's location change. */
    operator fun invoke(userLocation: Flow<GeoPoint?>): Flow<List<Site>> =
        combine(siteRepo.getSites(), userLocation) { sites, location ->
            if (location == null) {
                sites
            } else {
                sites.map { site ->
                    site.copy(
                        distanceFromUserM = haversineMeters(
                            location.latitude, location.longitude, site.latitude, site.longitude
                        ).toFloat()
                    )
                }.sortedBy { it.distanceFromUserM }
            }
        }
}

/**
 * Nearest site first; within a site (or when distance is unknown) keeps the incoming
 * chronological order. Stable, so inscriptions at unknown-distance sites go last.
 */
fun orderByProximity(inscriptions: List<Inscription>, sites: List<Site>): List<Inscription> {
    val distanceBySite = sites.associate { it.id to it.distanceFromUserM }
    if (distanceBySite.values.all { it == null }) return inscriptions
    return inscriptions.sortedBy { distanceBySite[it.siteId] ?: Float.MAX_VALUE }
}

/** Great-circle distance; accurate to well under 1% at site scales, which is ample for ranking. */
internal fun haversineMeters(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
    val earthRadiusM = 6_371_008.8
    val dLat = Math.toRadians(lat2 - lat1)
    val dLon = Math.toRadians(lon2 - lon1)
    val a = Math.sin(dLat / 2).let { it * it } +
        Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2)) * Math.sin(dLon / 2).let { it * it }
    return 2 * earthRadiusM * Math.asin(Math.sqrt(a).coerceAtMost(1.0))
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
