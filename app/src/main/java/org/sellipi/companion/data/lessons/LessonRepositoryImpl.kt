package org.sellipi.companion.data.lessons

import android.content.Context
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.sellipi.companion.domain.lessons.Lesson
import org.sellipi.companion.domain.lessons.LessonExportSink
import org.sellipi.companion.domain.lessons.LessonFactory
import org.sellipi.companion.domain.lessons.LessonPayload
import org.sellipi.companion.domain.lessons.LessonRepository
import org.sellipi.companion.domain.lessons.LessonTrust
import org.sellipi.companion.domain.lessons.LessonType
import java.io.File

class LessonRepositoryImpl(
    private val dao: LessonDao,
    private val factory: LessonFactory,
    // Outlives ViewModels so lessons emitted from onCleared() still get written.
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
) : LessonRepository {

    override fun record(payload: LessonPayload, inscriptionId: String?) {
        val lesson = factory.create(payload, inscriptionId)
        scope.launch {
            // Telemetry must never crash the visitor experience.
            runCatching { dao.insert(lesson.toEntity()) }
                .onFailure { Log.w("Lessons", "Failed to record ${lesson.type}", it) }
        }
    }

    override suspend fun getAll(): List<Lesson> = dao.getAll().map { it.toDomain() }

    override fun observeCount(): Flow<Int> = dao.observeCount()

    companion object {
        @Volatile
        private var INSTANCE: LessonRepositoryImpl? = null

        fun getInstance(context: Context, packVersion: Int): LessonRepositoryImpl {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: LessonRepositoryImpl(
                    dao = LessonsDatabase.getInstance(context).lessonDao(),
                    factory = LessonFactory(packVersion = packVersion, appVersion = appVersionOf(context))
                ).also { INSTANCE = it }
            }
        }
    }
}

fun appVersionOf(context: Context): String = runCatching {
    context.packageManager.getPackageInfo(context.packageName, 0).versionName
}.getOrNull() ?: "unknown"

/** Writes to app-specific external storage: no permission needed, reachable via USB / file manager. */
class FileLessonExportSink(private val context: Context) : LessonExportSink {
    override suspend fun write(fileName: String, content: String): String = withContext(Dispatchers.IO) {
        val dir = context.getExternalFilesDir("exports") ?: File(context.filesDir, "exports")
        dir.mkdirs()
        val file = File(dir, fileName)
        file.writeText(content)
        file.absolutePath
    }
}

private fun Lesson.toEntity() = LessonEntity(
    id = id,
    type = type.name,
    inscriptionId = inscriptionId,
    packVersion = packVersion,
    appVersion = appVersion,
    payloadSchemaVersion = payloadSchemaVersion,
    payloadJson = payloadJson,
    trust = trust.name,
    createdAt = createdAt,
    syncedAt = syncedAt
)

private fun LessonEntity.toDomain() = Lesson(
    id = id,
    type = LessonType.valueOf(type),
    inscriptionId = inscriptionId,
    packVersion = packVersion,
    appVersion = appVersion,
    payloadSchemaVersion = payloadSchemaVersion,
    payloadJson = payloadJson,
    trust = LessonTrust.valueOf(trust),
    createdAt = createdAt,
    syncedAt = syncedAt
)
