package org.sellipi.companion.data.llm

/**
 * A downloadable on-device model. The file is fetched from [url] (your ungated mirror of the
 * vendor's file) and used only if its SHA-256 equals [sha256], the hash of the ORIGINAL file
 * published by Google on Hugging Face. That makes the mirror trust-free: a modified or wrong
 * file is rejected. See docs/slm-lessons-architecture.md §8.3 for the mirror steps.
 */
data class ModelSpec(
    val id: String,
    val displayName: String,
    val fileName: String,
    val url: String,
    val sha256: String,
    val sizeBytes: Long,
    val vendorPage: String,
    val licenseName: String,
    val licenseUrl: String,
    val prohibitedUseUrl: String
) {
    val isConfigured: Boolean
        get() = url.startsWith("https://") && sha256.matches(Regex("[0-9a-f]{64}")) && sizeBytes > 0
}

object ModelCatalog {

    /**
     * FILL THESE IN after mirroring (docs §8.3):
     *  - url:       https://huggingface.co/<you>/<repo>/resolve/main/<fileName>
     *  - sha256:    the SHA-256 shown on Google's file page (lowercase hex), NOT computed from your copy
     *  - sizeBytes: the exact byte size shown on Google's file page
     * Until then the app reports the model as not configured and answers without it.
     */
    val GEMMA3_1B = ModelSpec(
        id = "gemma3-1b-it-q4-ekv4096",
        displayName = "Gemma 3 1B (int4)",
        fileName = "Gemma3-1B-IT_multi-prefill-seq_q4_ekv4096.litertlm",
        url = "",
        sha256 = "",
        sizeBytes = 0L,
        vendorPage = "https://huggingface.co/litert-community/Gemma3-1B-IT",
        licenseName = "Gemma Terms of Use",
        licenseUrl = "https://ai.google.dev/gemma/terms",
        prohibitedUseUrl = "https://ai.google.dev/gemma/prohibited_use_policy"
    )

    val current: ModelSpec = GEMMA3_1B
}
