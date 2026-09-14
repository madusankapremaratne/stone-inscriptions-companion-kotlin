package org.sellipi.companion.data.repository

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import org.sellipi.companion.data.local.database.SellipiDatabase
import org.sellipi.companion.data.local.entity.CaptureSessionEntity
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

class SiteRepositoryImpl(private val db: SellipiDatabase) : SiteRepository {
    override fun getSites(): Flow<List<Site>> = db.siteDao().getAllSitesFlow().map { list ->
        list.map { e ->
            Site(e.id, e.nameSi, e.nameTa, e.nameEn, e.latitude, e.longitude, e.geofenceRadiusM, e.permitReference)
        }
    }

    override suspend fun getSiteById(siteId: String): Site? = db.siteDao().getSiteById(siteId)?.let { e ->
        Site(e.id, e.nameSi, e.nameTa, e.nameEn, e.latitude, e.longitude, e.geofenceRadiusM, e.permitReference)
    }
}

class InscriptionRepositoryImpl(private val db: SellipiDatabase) : InscriptionRepository {
    override fun getInscriptionsForSite(siteId: String): Flow<List<Inscription>> =
        db.inscriptionDao().getInscriptionsBySiteFlow(siteId).map { list ->
            list.map { e ->
                Inscription(
                    e.id, e.siteId, e.nameSi, e.nameTa, e.nameEn,
                    e.dateRangeStart, e.dateRangeEnd, e.datingBasis,
                    e.primaryPeriodId, e.referencePhoto, e.arcoreTargetScore,
                    e.alignmentStrategy, e.sourceCitation
                )
            }
        }

    override fun getAllInscriptions(): Flow<List<Inscription>> =
        db.inscriptionDao().getAllInscriptionsFlow().map { list ->
            list.map { e ->
                Inscription(
                    e.id, e.siteId, e.nameSi, e.nameTa, e.nameEn,
                    e.dateRangeStart, e.dateRangeEnd, e.datingBasis,
                    e.primaryPeriodId, e.referencePhoto, e.arcoreTargetScore,
                    e.alignmentStrategy, e.sourceCitation
                )
            }
        }

    override suspend fun getInscriptionById(id: String): Inscription? =
        db.inscriptionDao().getInscriptionById(id)?.let { e ->
            Inscription(
                e.id, e.siteId, e.nameSi, e.nameTa, e.nameEn,
                e.dateRangeStart, e.dateRangeEnd, e.datingBasis,
                e.primaryPeriodId, e.referencePhoto, e.arcoreTargetScore,
                e.alignmentStrategy, e.sourceCitation
            )
        }

    override fun getTranscriptionLines(inscriptionId: String): Flow<List<TranscriptionLine>> =
        db.transcriptionDao().getLinesForInscriptionFlow(inscriptionId).map { lines ->
            lines.map { l ->
                TranscriptionLine(
                    l.id, l.inscriptionId, l.lineNumber,
                    l.textOriginal, l.textModernSinhala,
                    l.translationSi, l.translationTa, l.translationEn
                )
            }
        }

    override fun getGlyphsForInscription(inscriptionId: String): Flow<List<GlyphOccurrence>> =
        db.transcriptionDao().getAllGlyphsForInscriptionFlow(inscriptionId).map { glyphs ->
            glyphs.map { g ->
                GlyphOccurrence(
                    g.id, g.transcriptionLineId, g.position,
                    g.letterId, g.bboxX, g.bboxY, g.bboxW, g.bboxH
                )
            }
        }
}

class EvolutionRepositoryImpl(private val db: SellipiDatabase) : EvolutionRepository {
    override fun getAllLetters(): Flow<List<Letter>> = db.letterDao().getAllLettersFlow().map { list ->
        list.map { e -> Letter(e.id, e.rowIndex, e.modernSinhalaCodepoint, e.romanisation, e.letterName, e.glyphImagePath) }
    }

    override suspend fun getLetterById(letterId: String): Letter? = db.letterDao().getLetterById(letterId)?.let { e ->
        Letter(e.id, e.rowIndex, e.modernSinhalaCodepoint, e.romanisation, e.letterName, e.glyphImagePath)
    }

    override fun getAllPeriods(): Flow<List<Period>> = db.periodDao().getAllPeriodsFlow().map { list ->
        list.map { e -> Period(e.id, e.periodNumber, e.labelSi, e.labelTa, e.labelEn, e.yearStart, e.yearEnd, e.chartColumnRefs) }
    }

    override fun getFormsForLetter(letterId: String): Flow<List<LetterForm>> =
        db.letterFormDao().getFormsForLetterFlow(letterId).map { list ->
            list.map { e ->
                LetterForm(
                    e.id, e.letterId, e.periodId, e.gridRow, e.gridCol,
                    e.vectorPath, e.imageAssetPath,
                    isAttested = e.isAttested == 1,
                    isReconstructed = e.isReconstructed == 1,
                    e.sourceInscriptionRef, e.provenance
                )
            }
        }
}

class ResearcherCaptureRepositoryImpl(private val db: SellipiDatabase) : ResearcherCaptureRepository {
    override suspend fun saveCaptureSession(session: CaptureSession) {
        db.captureDao().insertSession(
            CaptureSessionEntity(
                session.id, session.inscriptionId, session.deviceModel,
                session.timestamp, session.gpsLat, session.gpsLon,
                session.gpsAccuracy, session.poseMatrix, session.scaleMmPerPx,
                session.imagePaths.joinToString(";"), session.permitReference
            )
        )
    }

    override fun getAllSessions(): Flow<List<CaptureSession>> = db.captureDao().getAllSessionsFlow().map { list ->
        list.map { e ->
            CaptureSession(
                e.id, e.inscriptionId, e.deviceModel, e.timestamp,
                e.gpsLat, e.gpsLon, e.gpsAccuracy, e.poseMatrix,
                e.scaleMmPerPx, e.imagePaths.split(";").filter { it.isNotBlank() },
                e.permitReference
            )
        }
    }
}
