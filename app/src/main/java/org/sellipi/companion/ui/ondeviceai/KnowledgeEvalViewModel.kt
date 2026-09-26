package org.sellipi.companion.ui.ondeviceai

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.sellipi.companion.data.llm.LlmProvider
import org.sellipi.companion.domain.knowledge.KnowledgeRepository
import org.sellipi.companion.domain.lessons.LessonExportSink
import org.sellipi.companion.domain.llm.EvalQuestionSet
import org.sellipi.companion.domain.llm.EvalReport
import org.sellipi.companion.domain.llm.KnowledgeEvaluator

data class EvalUiState(
    val running: Boolean = false,
    val done: Int = 0,
    val total: Int = 0,
    val report: EvalReport? = null,
    val exportedPath: String? = null,
    val error: String? = null
)

/**
 * Debug-only: runs data/knowledge/eval/q_dev.json (bundled into debug assets) through the full
 * cascade on this phone. Running once with and once without the model gives the thesis
 * comparison on identical questions and cards.
 */
class KnowledgeEvalViewModel(
    private val appContext: Context,
    private val knowledgeRepo: KnowledgeRepository,
    private val llmProvider: LlmProvider,
    private val exportSink: LessonExportSink
) : ViewModel() {

    private val _state = MutableStateFlow(EvalUiState())
    val state: StateFlow<EvalUiState> = _state.asStateFlow()

    fun run(withModel: Boolean) {
        if (_state.value.running) return
        _state.value = EvalUiState(running = true)
        viewModelScope.launch {
            try {
                val questions = withContext(Dispatchers.IO) {
                    val json = appContext.assets.open(QUESTION_ASSET).bufferedReader().use { it.readText() }
                    KnowledgeEvaluator.json.decodeFromString(EvalQuestionSet.serializer(), json).questions
                }
                val llm = if (withModel) llmProvider.current() else null
                if (withModel && llm == null) {
                    _state.value = EvalUiState(error = "Model not ready (download it first, or the phone is too hot).")
                    return@launch
                }
                _state.update { it.copy(total = questions.size) }
                val report = KnowledgeEvaluator(knowledgeRepo.loadPack(), includeDrafts = true, llm = llm)
                    .run(questions) { done, total -> _state.update { it.copy(done = done, total = total) } }
                val path = exportSink.write(
                    "sellipi_eval_${if (withModel) report.backend.lowercase() else "nomodel"}_${System.currentTimeMillis()}.json",
                    KnowledgeEvaluator.json.encodeToString(EvalReport.serializer(), report)
                )
                _state.update { it.copy(running = false, report = report, exportedPath = path) }
            } catch (e: Exception) {
                _state.value = EvalUiState(error = e.message ?: e.javaClass.simpleName)
            }
        }
    }

    class Factory(
        private val appContext: Context,
        private val knowledgeRepo: KnowledgeRepository,
        private val llmProvider: LlmProvider,
        private val exportSink: LessonExportSink
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            KnowledgeEvalViewModel(appContext, knowledgeRepo, llmProvider, exportSink) as T
    }

    companion object {
        const val QUESTION_ASSET = "q_dev.json"
    }
}
