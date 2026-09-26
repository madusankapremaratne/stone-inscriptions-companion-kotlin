package org.sellipi.companion

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Test
import org.sellipi.companion.domain.lessons.AlignmentEndReason
import org.sellipi.companion.domain.lessons.AlignmentMode
import org.sellipi.companion.domain.lessons.AlignmentOutcome
import org.sellipi.companion.domain.lessons.ExportLessonsUseCase
import org.sellipi.companion.domain.lessons.Lesson
import org.sellipi.companion.domain.lessons.LessonCodec
import org.sellipi.companion.domain.lessons.LessonFactory
import org.sellipi.companion.domain.lessons.LessonPayload
import org.sellipi.companion.domain.lessons.LessonRepository
import org.sellipi.companion.domain.lessons.LessonTrust
import org.sellipi.companion.domain.lessons.LessonType
import org.sellipi.companion.domain.lessons.SelectionMethod
import org.sellipi.companion.domain.lessons.identificationRecoveryOf

class LessonCodecTest {

    private val outcome = AlignmentOutcome(
        mode = AlignmentMode.OVERLAY,
        success = true,
        timeToAlignMs = 1200L,
        sessionDurationMs = 9000L,
        glyphTapHits = 3,
        glyphTapMisses = 1,
        endReason = AlignmentEndReason.EXITED,
        localHour = 10
    )

    private val factory = LessonFactory(packVersion = 1, appVersion = "1.0.0", clock = { 42L }, newId = { "id-1" })

    @Test
    fun payloadRoundTripsWithDiscriminator() {
        val encoded = LessonCodec.encode(outcome)
        assertEquals("alignment_outcome", Json.parseToJsonElement(encoded).jsonObject["kind"]!!.jsonPrimitive.content)
        assertEquals(outcome, LessonCodec.decode(encoded))
    }

    @Test
    fun factoryStampsMetadata() {
        val lesson = factory.create(outcome, "INSC_ANP_01")
        assertEquals(LessonType.ALIGNMENT_OUTCOME, lesson.type)
        assertEquals(LessonTrust.OBSERVED, lesson.trust)
        assertEquals(42L, lesson.createdAt)
        assertEquals(LessonCodec.PAYLOAD_SCHEMA_VERSION, lesson.payloadSchemaVersion)
    }

    @Test
    fun exportEmbedsPayloadAsObject() = runTest {
        val repo = object : LessonRepository {
            override fun record(payload: LessonPayload, inscriptionId: String?) = Unit
            override suspend fun getAll(): List<Lesson> = listOf(factory.create(outcome, null))
            override fun observeCount(): Flow<Int> = flowOf(1)
        }
        var written = ""
        val result = ExportLessonsUseCase(repo, { name, content -> written = content; "/exports/$name" }, "1.0.0", clock = { 7L })()

        assertEquals("/exports/sellipi_lessons_7.json", result.path)
        assertEquals(1, result.lessonCount)
        val doc = Json.parseToJsonElement(written).jsonObject
        assertEquals(1, doc["exportSchemaVersion"]!!.jsonPrimitive.int)
        val payload = doc["lessons"]!!.jsonArray.single().jsonObject["payload"]!!.jsonObject
        assertEquals(3, payload["glyphTapHits"]!!.jsonPrimitive.int)
    }

    @Test
    fun identificationMethodAndRank() {
        val ids = listOf("A", "B", "C")
        assertEquals(SelectionMethod.SEARCH, identificationRecoveryOf("C", ids, " mihin ", "S1", true, 10).selectionMethod)
        assertEquals(SelectionMethod.SITE_FILTER, identificationRecoveryOf("C", ids, "", "S1", true, 10).selectionMethod)

        val lesson = identificationRecoveryOf("B", ids, "", null, false, -5)
        assertEquals(SelectionMethod.NEARBY_LIST, lesson.selectionMethod)
        assertEquals(1, lesson.rankInList)
        assertEquals(3, lesson.listSize)
        assertEquals(0L, lesson.msToSelect)
        assertEquals(5, identificationRecoveryOf("A", ids, " mihin ", null, true, 0).searchQueryLength)
    }
}
