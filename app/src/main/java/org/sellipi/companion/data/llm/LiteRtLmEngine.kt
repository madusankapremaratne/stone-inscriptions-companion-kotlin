package org.sellipi.companion.data.llm

import android.util.Log
import com.google.ai.edge.litertlm.Backend
import com.google.ai.edge.litertlm.Contents
import com.google.ai.edge.litertlm.ConversationConfig
import com.google.ai.edge.litertlm.Engine
import com.google.ai.edge.litertlm.EngineConfig
import com.google.ai.edge.litertlm.SamplerConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.sellipi.companion.domain.llm.LlmEngine

/**
 * Gemma via LiteRT-LM. One engine per process (it holds ~1 GB), created lazily on the first
 * question; calls are serialised because a single engine runs one conversation at a time.
 *
 * All LiteRT-LM API use is confined to this file, so a library API change is a one-file fix.
 */
class LiteRtLmEngine(
    private val modelPath: String,
    private val cacheDir: String,
    private val preferGpu: Boolean
) : LlmEngine, AutoCloseable {

    private val mutex = Mutex()
    private var engine: Engine? = null
    private var activeBackend = if (preferGpu) "GPU" else "CPU"
    @Volatile private var closeRequested = false

    override val backendName: String get() = activeBackend

    override suspend fun generate(systemInstruction: String, prompt: String): String = mutex.withLock {
        try {
            runGeneration(systemInstruction, prompt)
        } finally {
            if (closeRequested) closeNow()
        }
    }

    private suspend fun runGeneration(systemInstruction: String, prompt: String): String =
        withContext(Dispatchers.Default) {
            val conversationConfig = ConversationConfig(
                systemInstruction = Contents.of(systemInstruction),
                // Near-greedy decoding: grounded answers should be repeatable, not creative.
                samplerConfig = SamplerConfig(topK = 1, topP = 1.0, temperature = 0.1)
            )
            ensureEngine().createConversation(conversationConfig).use { conversation ->
                val out = StringBuilder()
                // Each emitted Message renders as its text (as in LiteRT-LM's own example).
                conversation.sendMessageAsync(prompt).collect { out.append(it.toString()) }
                out.toString()
            }
        }

    /** Loads the model now (seconds on a mid-range phone) so the first question is not slower. */
    suspend fun warmUp() = mutex.withLock {
        try {
            withContext(Dispatchers.Default) { ensureEngine() }
        } finally {
            if (closeRequested) closeNow()
        }
    }

    private fun ensureEngine(): Engine {
        engine?.let { return it }
        val created = if (preferGpu) {
            // GPU support varies by phone; fall back to CPU if the GPU backend fails to load.
            runCatching { load(Backend.GPU()) }
                .onFailure { Log.w(TAG, "GPU backend unavailable, using CPU", it) }
                .getOrNull()
                ?: load(Backend.CPU()).also { activeBackend = "CPU" }
        } else {
            load(Backend.CPU())
        }
        engine = created
        return created
    }

    private fun load(backend: Backend): Engine =
        Engine(EngineConfig(modelPath = modelPath, backend = backend, cacheDir = cacheDir)).also { it.initialize() }

    /**
     * Closing native memory mid-generation would crash the process, so if a call is running
     * the close is deferred until it finishes.
     */
    override fun close() {
        if (mutex.tryLock()) {
            try { closeNow() } finally { mutex.unlock() }
        } else {
            closeRequested = true
        }
    }

    private fun closeNow() {
        runCatching { engine?.close() }
        engine = null
        closeRequested = false
    }

    private companion object {
        const val TAG = "LiteRtLmEngine"
    }
}
