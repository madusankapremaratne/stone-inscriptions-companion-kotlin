package org.sellipi.companion.domain.knowledge

import java.text.Normalizer
import java.util.Locale

/**
 * Script-aware normalisation for en/si/ta queries.
 *
 * Combining marks are kept for Sinhala and Tamil (they carry vowels and the virama) and
 * stripped only after Latin letters, so "Devānampiya" and "Devanampiya" match.
 */
object TextNormalizer {

    private const val ZWJ = '‍'
    private const val ZWNJ = '‌'
    private const val ZWSP = '​'

    private val EMAIL = Regex("""\S+@\S+""")
    private val DIGITS = Regex("""\d+""")
    private const val MAX_STORED_QUERY_LENGTH = 120

    // Romanisation variants seen for Sinhala names: w/v, aspirated consonants, doubled letters.
    private val LATIN_FOLDS = listOf("th" to "t", "dh" to "d", "bh" to "b", "kh" to "k", "ph" to "p", "sh" to "s", "w" to "v")

    private val EN_STOPWORDS = setOf(
        "a", "an", "and", "are", "about", "did", "do", "does", "for", "from", "how", "in", "is", "it",
        "me", "of", "on", "tell", "that", "the", "this", "to", "was", "were", "what", "when", "where",
        "which", "who", "whom", "why", "with", "his", "her", "its"
    )

    fun normalize(text: String): String {
        val decomposed = Normalizer.normalize(text, Normalizer.Form.NFD)
        val sb = StringBuilder(decomposed.length)
        var lastBase = ' '
        for (ch in decomposed) {
            when {
                ch == ZWJ || ch == ZWNJ || ch == ZWSP -> Unit
                isMark(ch) -> if (!isLatin(lastBase)) sb.append(ch)
                Character.isLetterOrDigit(ch) -> { sb.append(ch); lastBase = ch }
                else -> { sb.append(' '); lastBase = ' ' }
            }
        }
        return Normalizer.normalize(sb.toString(), Normalizer.Form.NFC)
            .lowercase(Locale.ROOT)
            .split(' ')
            .filter { it.isNotEmpty() }
            .joinToString(" ")
    }

    /**
     * Spacing- and romanisation-insensitive key: "Dewanam Piyathissa" and "Devanampiya Tissa"
     * both become "devanampiyatisa". Non-Latin text only loses its spaces.
     */
    fun looseKey(text: String): String {
        var key = normalize(text).replace(" ", "")
        for ((from, to) in LATIN_FOLDS) key = key.replace(from, to)
        val sb = StringBuilder(key.length)
        for (ch in key) {
            if (sb.isNotEmpty() && ch == sb.last() && ch in 'a'..'z') continue
            sb.append(ch)
        }
        return sb.toString()
    }

    fun tokens(text: String): List<String> =
        normalize(text).split(' ').filter { it.length >= 2 && it !in EN_STOPWORDS }

    /** What may be stored in a lesson: no emails, no digits (phone numbers, ages), bounded length. */
    fun sanitizeForStorage(query: String): String =
        normalize(DIGITS.replace(EMAIL.replace(query, " "), " ")).take(MAX_STORED_QUERY_LENGTH)

    private fun isMark(ch: Char): Boolean = when (Character.getType(ch).toByte()) {
        Character.NON_SPACING_MARK, Character.COMBINING_SPACING_MARK, Character.ENCLOSING_MARK -> true
        else -> false
    }

    private fun isLatin(ch: Char): Boolean = ch in 'A'..'Z' || ch in 'a'..'z' || ch.code in 0x00C0..0x024F || ch.code in 0x1E00..0x1EFF
}
