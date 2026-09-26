package org.sellipi.companion.data.llm

import java.io.File
import java.security.MessageDigest

/** Plain-JVM helpers for model files (unit tested without Android). */
object FileHashing {

    fun sha256(file: File, onProgress: (bytesRead: Long) -> Unit = {}): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val buffer = ByteArray(1 shl 20)
        var total = 0L
        file.inputStream().use { input ->
            while (true) {
                val n = input.read(buffer)
                if (n < 0) break
                digest.update(buffer, 0, n)
                total += n
                onProgress(total)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    /**
     * Moves [from] to [to], copying when they are on different storage volumes (DownloadManager
     * writes to external app storage; the model lives in internal storage). Never leaves a
     * partial file at [to].
     */
    fun moveReplacing(from: File, to: File) {
        to.parentFile?.mkdirs()
        val tmp = File(to.parentFile, to.name + ".tmp")
        tmp.delete()
        if (!from.renameTo(tmp)) {
            from.copyTo(tmp, overwrite = true)
            from.delete()
        }
        to.delete()
        check(tmp.renameTo(to)) { "Could not move model into place" }
    }
}
