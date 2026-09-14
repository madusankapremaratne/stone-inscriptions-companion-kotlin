package org.sellipi.companion

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.sellipi.companion.domain.model.Letter
import org.sellipi.companion.domain.model.LetterForm
import org.sellipi.companion.domain.model.Period
import org.sellipi.companion.domain.repository.EvolutionRepository
import org.sellipi.companion.domain.usecase.GetLetterEvolutionUseCase

class GetLetterEvolutionUseCaseTest {

    private val fakeRepo = object : EvolutionRepository {
        val testLetter = Letter("L01", 1, "අ", "a", "Akaraya", null)
        val testPeriods = listOf(
            Period("P01", 1, "ක්‍රි.පූ. 3 සියවස", "கி.மு. 3", "3rd c. BCE", -300, -200, "Col 1"),
            Period("P02", 2, "ක්‍රි.පූ. 2 සියවස", "கி.மு. 2", "2nd c. BCE", -200, -100, "Col 2")
        )
        val testForms = listOf(
            LetterForm("LF_L01_P01", "L01", "P01", 1, 1, null, "img1.png", isAttested = true, isReconstructed = false, "Ref1", "authentic")
        )

        override fun getAllLetters() = flowOf(listOf(testLetter))
        override suspend fun getLetterById(letterId: String) = if (letterId == "L01") testLetter else null
        override fun getAllPeriods() = flowOf(testPeriods)
        override fun getFormsForLetter(letterId: String) = flowOf(testForms)
    }

    private val useCase = GetLetterEvolutionUseCase(fakeRepo)

    @Test
    fun testEvolutionTimelineWithAttestedAndGap() = runTest {
        val data = useCase.getEvolutionForLetter("L01").first()
        assertNotNull(data)
        assertEquals("අ", data!!.letter.modernSinhalaCodepoint)
        assertEquals(2, data.timeline.size)
        
        // P01 is attested
        assertTrue(data.timeline[0].isAttested)
        assertEquals("P01", data.timeline[0].periodId)

        // P02 is an unattested gap
        assertFalse(data.timeline[1].isAttested)
        assertEquals("P02", data.timeline[1].periodId)

        assertEquals(1, data.attestedCount)
        assertEquals(1, data.gapCount)
    }
}
