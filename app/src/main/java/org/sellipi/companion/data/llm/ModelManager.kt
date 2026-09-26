package org.sellipi.companion.data.llm

import android.app.DownloadManager
import android.content.Context
import android.net.Uri
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.File

sealed interface ModelState {
    /** No mirror URL/hash in [ModelCatalog] yet. */
    data object NotConfigured : ModelState
    data class Ineligible(val reason: DeviceCapability.IneligibleReason, val ramGb: Double) : ModelState
    data object NotDownloaded : ModelState
    /** [waitingForWifi]: DownloadManager paused because only a metered network is available. */
    data class Downloading(val bytes: Long, val total: Long, val waitingForWifi: Boolean) : ModelState
    data class Verifying(val bytes: Long, val total: Long) : ModelState
    data class Ready(val file: File) : ModelState
    data class Failed(val reason: FailReason) : ModelState

    enum class FailReason { DOWNLOAD_FAILED, INSUFFICIENT_SPACE, SIZE_MISMATCH, HASH_MISMATCH, STORAGE_ERROR }
}

/**
 * Downloads the model with the system DownloadManager (Wi-Fi only, resumable, survives the app
 * being closed, shows its own progress notification), then verifies it against the pinned
 * SHA-256 before it is ever loaded.
 */
class ModelManager(
    private val context: Context,
    val spec: ModelSpec,
    private val capability: DeviceCapability,
    private val scope: CoroutineScope
) {
    private val prefs = context.getSharedPreferences("on_device_model", Context.MODE_PRIVATE)
    private val downloads = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
    private val modelFile = File(File(context.filesDir, "models"), spec.fileName)

    private val _state = MutableStateFlow<ModelState>(ModelState.NotDownloaded)
    val state: StateFlow<ModelState> = _state.asStateFlow()

    private var pollJob: Job? = null

    init {
        refresh()
    }

    fun refresh() {
        _state.value = when {
            !spec.isConfigured -> ModelState.NotConfigured
            !capability.eligible -> ModelState.Ineligible(capability.reason!!, capability.totalRamGb)
            isVerifiedOnDisk() -> ModelState.Ready(modelFile)
            prefs.getLong(KEY_DOWNLOAD_ID, -1) >= 0 -> ModelState.Downloading(0, spec.sizeBytes, false).also { poll() }
            else -> ModelState.NotDownloaded
        }
    }

    fun startDownload() {
        if (!spec.isConfigured || !capability.eligible) return
        if (_state.value is ModelState.Downloading || _state.value is ModelState.Verifying) return
        val usable = context.getExternalFilesDir(null)?.usableSpace ?: 0L
        // Room for the download plus the verified copy in internal storage.
        if (usable < spec.sizeBytes * 2) {
            _state.value = ModelState.Failed(ModelState.FailReason.INSUFFICIENT_SPACE)
            return
        }
        downloadTarget().delete()
        val request = DownloadManager.Request(Uri.parse(spec.url))
            .setTitle(spec.displayName)
            .setDescription("On-device AI for Sellipi")
            .setAllowedOverMetered(false)
            .setAllowedOverRoaming(false)
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE)
            .setDestinationInExternalFilesDir(context, DOWNLOAD_DIR, spec.fileName)
        prefs.edit().putLong(KEY_DOWNLOAD_ID, downloads.enqueue(request)).apply()
        _state.value = ModelState.Downloading(0, spec.sizeBytes, false)
        poll()
    }

    fun cancelDownload() {
        val id = prefs.getLong(KEY_DOWNLOAD_ID, -1)
        if (id >= 0) downloads.remove(id)
        pollJob?.cancel()
        prefs.edit().remove(KEY_DOWNLOAD_ID).apply()
        downloadTarget().delete()
        refresh()
    }

    fun deleteModel() {
        cancelDownload()
        modelFile.delete()
        prefs.edit().remove(KEY_VERIFIED).apply()
        refresh()
    }

    private fun poll() {
        if (pollJob?.isActive == true) return
        pollJob = scope.launch(Dispatchers.IO) {
            while (isActive) {
                val id = prefs.getLong(KEY_DOWNLOAD_ID, -1)
                if (id < 0) return@launch
                val status = query(id)
                when (status?.status) {
                    null -> { // Removed from the system (e.g. user cleared downloads)
                        prefs.edit().remove(KEY_DOWNLOAD_ID).apply()
                        _state.value = ModelState.NotDownloaded
                        return@launch
                    }
                    DownloadManager.STATUS_SUCCESSFUL -> {
                        verifyAndInstall()
                        // remove() also deletes the downloaded file, so only after it was moved or rejected.
                        downloads.remove(id)
                        prefs.edit().remove(KEY_DOWNLOAD_ID).apply()
                        return@launch
                    }
                    DownloadManager.STATUS_FAILED -> {
                        prefs.edit().remove(KEY_DOWNLOAD_ID).apply()
                        downloads.remove(id)
                        _state.value = ModelState.Failed(
                            if (status.reason == DownloadManager.ERROR_INSUFFICIENT_SPACE) ModelState.FailReason.INSUFFICIENT_SPACE
                            else ModelState.FailReason.DOWNLOAD_FAILED
                        )
                        return@launch
                    }
                    else -> _state.value = ModelState.Downloading(
                        bytes = status.bytes,
                        total = if (status.total > 0) status.total else spec.sizeBytes,
                        waitingForWifi = status.status == DownloadManager.STATUS_PAUSED &&
                            status.reason == DownloadManager.PAUSED_QUEUED_FOR_WIFI
                    )
                }
                delay(POLL_MS)
            }
        }
    }

    private data class Status(val status: Int, val reason: Int, val bytes: Long, val total: Long)

    private fun query(id: Long): Status? =
        downloads.query(DownloadManager.Query().setFilterById(id))?.use { c ->
            if (!c.moveToFirst()) return null
            Status(
                status = c.getInt(c.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS)),
                reason = c.getInt(c.getColumnIndexOrThrow(DownloadManager.COLUMN_REASON)),
                bytes = c.getLong(c.getColumnIndexOrThrow(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR)),
                total = c.getLong(c.getColumnIndexOrThrow(DownloadManager.COLUMN_TOTAL_SIZE_BYTES))
            )
        }

    private fun verifyAndInstall() {
        val downloaded = downloadTarget()
        try {
            if (downloaded.length() != spec.sizeBytes) {
                downloaded.delete()
                _state.value = ModelState.Failed(ModelState.FailReason.SIZE_MISMATCH)
                return
            }
            val hash = FileHashing.sha256(downloaded) { read ->
                _state.value = ModelState.Verifying(read, spec.sizeBytes)
            }
            if (!hash.equals(spec.sha256, ignoreCase = true)) {
                downloaded.delete()
                _state.value = ModelState.Failed(ModelState.FailReason.HASH_MISMATCH)
                return
            }
            FileHashing.moveReplacing(downloaded, modelFile)
            prefs.edit().putString(KEY_VERIFIED, marker(modelFile)).apply()
            _state.value = ModelState.Ready(modelFile)
        } catch (e: Exception) {
            downloaded.delete()
            _state.value = ModelState.Failed(ModelState.FailReason.STORAGE_ERROR)
        }
    }

    /** Hashing ~550 MB on every launch is slow, so a verified file is remembered by hash, size and mtime. */
    private fun isVerifiedOnDisk(): Boolean =
        modelFile.isFile && modelFile.length() == spec.sizeBytes && prefs.getString(KEY_VERIFIED, null) == marker(modelFile)

    private fun marker(file: File) = "${spec.sha256}:${file.length()}:${file.lastModified()}"

    private fun downloadTarget() = File(context.getExternalFilesDir(DOWNLOAD_DIR), spec.fileName)

    private companion object {
        const val KEY_DOWNLOAD_ID = "download_id"
        const val KEY_VERIFIED = "verified_marker"
        const val DOWNLOAD_DIR = "model-downloads"
        const val POLL_MS = 750L
    }
}
