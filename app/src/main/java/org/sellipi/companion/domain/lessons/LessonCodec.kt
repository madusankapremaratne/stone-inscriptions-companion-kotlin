package org.sellipi.companion.domain.lessons

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.util.UUID

object LessonCodec {
    /** Bump when any payload's fields change meaning; stored per lesson. */
    const val PAYLOAD_SCHEMA_VERSION = 1
    const val EXPORT_SCHEMA_VERSION = 1

    private val json = Json {
        encodeDefaults = true
        classDiscriminator = "kind"
    }

    fun encode(payload: LessonPayload): String =
        json.encodeToString(LessonPayload.serializer(), payload)

    fun decode(payloadJson: String): LessonPayload =
        json.decodeFromString(LessonPayload.serializer(), payloadJson)

    /** Self-describing export bundle; payloads are embedded as JSON objects, not strings. */
    fun exportDocument(lessons: List<Lesson>, exportedAt: Long, appVersion: String): String {
        val doc = buildJsonObject {
            put("exportSchemaVersion", EXPORT_SCHEMA_VERSION)
            put("exportedAt", exportedAt)
            put("appVersion", appVersion)
            put("lessonCount", lessons.size)
            put("lessons", JsonArray(lessons.map { lesson ->
                buildJsonObject {
                    put("id", lesson.id)
                    put("type", lesson.type.name)
                    put("inscriptionId", lesson.inscriptionId?.let(::JsonPrimitive) ?: JsonNull)
                    put("packVersion", lesson.packVersion)
                    put("appVersion", lesson.appVersion)
                    put("payloadSchemaVersion", lesson.payloadSchemaVersion)
                    put("trust", lesson.trust.name)
                    put("createdAt", lesson.createdAt)
                    put("payload", json.parseToJsonElement(lesson.payloadJson))
                }
            }))
        }
        return json.encodeToString(kotlinx.serialization.json.JsonObject.serializer(), doc)
    }
}

class LessonFactory(
    private val packVersion: Int,
    private val appVersion: String,
    private val clock: () -> Long = System::currentTimeMillis,
    private val newId: () -> String = { UUID.randomUUID().toString() }
) {
    fun create(payload: LessonPayload, inscriptionId: String?): Lesson = Lesson(
        id = newId(),
        type = payload.type,
        inscriptionId = inscriptionId,
        packVersion = packVersion,
        appVersion = appVersion,
        payloadSchemaVersion = LessonCodec.PAYLOAD_SCHEMA_VERSION,
        payloadJson = LessonCodec.encode(payload),
        trust = LessonTrust.OBSERVED,
        createdAt = clock(),
        syncedAt = null
    )
}
