package org.sellipi.companion.ui.ondeviceai

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import kotlinx.coroutines.flow.StateFlow
import org.sellipi.companion.data.llm.DeviceCapability
import org.sellipi.companion.data.llm.LlmProvider
import org.sellipi.companion.data.llm.ModelSpec
import org.sellipi.companion.data.llm.ModelState

class OnDeviceAiViewModel(private val provider: LlmProvider) : ViewModel() {

    val spec: ModelSpec = provider.modelManager.spec
    val capability: DeviceCapability = provider.capability
    val state: StateFlow<ModelState> = provider.modelManager.state
    val useGpu: StateFlow<Boolean> = provider.useGpu

    fun download() = provider.modelManager.startDownload()
    fun cancel() = provider.modelManager.cancelDownload()
    fun delete() = provider.deleteModel()
    fun setUseGpu(enabled: Boolean) = provider.setUseGpu(enabled)
    fun refresh() = provider.modelManager.refresh()

    class Factory(private val provider: LlmProvider) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T = OnDeviceAiViewModel(provider) as T
    }
}
