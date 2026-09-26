package org.sellipi.companion.domain.lessons

import kotlinx.coroutines.flow.Flow

interface LessonRepository {
    /** Fire-and-forget: must outlive the calling ViewModel (sessions end in onCleared). */
    fun record(payload: LessonPayload, inscriptionId: String?)
    suspend fun getAll(): List<Lesson>
    fun observeCount(): Flow<Int>
}

/** Writes an export file and returns a user-visible path. */
fun interface LessonExportSink {
    suspend fun write(fileName: String, content: String): String
}

data class LessonExportResult(val path: String, val lessonCount: Int)

class ExportLessonsUseCase(
    private val repo: LessonRepository,
    private val sink: LessonExportSink,
    private val appVersion: String,
    private val clock: () -> Long = System::currentTimeMillis
) {
    suspend operator fun invoke(): LessonExportResult {
        val lessons = repo.getAll()
        val now = clock()
        val content = LessonCodec.exportDocument(lessons, exportedAt = now, appVersion = appVersion)
        val path = sink.write("sellipi_lessons_$now.json", content)
        return LessonExportResult(path, lessons.size)
    }
}

/** Builds the lesson for a manual pick from the Home list; [visibleIds] is the list as shown. */
fun identificationRecoveryOf(
    chosenId: String,
    visibleIds: List<String>,
    searchQuery: String,
    selectedSiteId: String?,
    locationAvailable: Boolean,
    msToSelect: Long
): IdentificationRecovery {
    val method = when {
        searchQuery.isNotBlank() -> SelectionMethod.SEARCH
        selectedSiteId != null -> SelectionMethod.SITE_FILTER
        else -> SelectionMethod.NEARBY_LIST
    }
    return IdentificationRecovery(
        selectionMethod = method,
        rankInList = visibleIds.indexOf(chosenId),
        listSize = visibleIds.size,
        searchQueryLength = searchQuery.trim().length,
        locationAvailable = locationAvailable,
        msToSelect = msToSelect.coerceAtLeast(0L)
    )
}
