package org.sellipi.companion.data.llm

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.sellipi.companion.domain.llm.LlmEngine

/**
 * App-wide access to the on-device model. [current] returns an engine only when the model is
 * downloaded, verified, allowed on this phone and the phone is not overheating; otherwise the
 * Ask flow silently answers without it.
 */
class LlmProvider private constructor(private val context: Context) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val prefs = context.getSharedPreferences("on_device_model", Context.MODE_PRIVATE)

    val capability: DeviceCapability = DeviceCapability.check(context)
    val modelManager = ModelManager(context, ModelCatalog.current, capability, scope)

    private val _useGpu = MutableStateFlow(prefs.getBoolean(KEY_USE_GPU, false))
    /** Off by default: a GPU driver fault can crash natively on some phones; CPU is the safe floor. */
    val useGpu: StateFlow<Boolean> = _useGpu.asStateFlow()

    private var engine: LiteRtLmEngine? = null
    private var enginePath: String? = null

    @Synchronized
    fun current(): LlmEngine? {
        val ready = modelManager.state.value as? ModelState.Ready ?: return null
        if (!DeviceCapability.thermalAllowsGeneration(context)) return null
        val path = ready.file.absolutePath
        engine?.takeIf { enginePath == path }?.let { return it }
        release()
        return LiteRtLmEngine(path, context.cacheDir.absolutePath, _useGpu.value).also {
            engine = it
            enginePath = path
        }
    }

    /** Loads the model in the background so the first question does not pay the load time. */
    fun warmUp() {
        val e = current() as? LiteRtLmEngine ?: return
        scope.launch { runCatching { e.warmUp() } }
    }

    fun setUseGpu(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_USE_GPU, enabled).apply()
        _useGpu.value = enabled
        release()
    }

    fun deleteModel() {
        release()
        modelManager.deleteModel()
    }

    @Synchronized
    fun release() {
        engine?.close()
        engine = null
        enginePath = null
    }

    companion object {
        private const val KEY_USE_GPU = "use_gpu"

        @Volatile
        private var INSTANCE: LlmProvider? = null

        fun getInstance(context: Context): LlmProvider =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: LlmProvider(context.applicationContext).also { INSTANCE = it }
            }
    }
}
