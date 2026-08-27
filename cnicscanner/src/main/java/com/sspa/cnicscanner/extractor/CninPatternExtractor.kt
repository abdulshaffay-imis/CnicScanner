package com.sspa.cnicscanner

/**
 * Shared utility for extracting Pakistani CNIC-format numbers (NNNNN-NNNNNNN-N)
 * from raw OCR text. Used by both the CNIC front/back scanner and the
 * B-Form scanner, since both documents contain numbers in this exact format.
 */
object CnicPatternExtractor {

    // Matches 37103-2873560-5 style numbers. Tolerant of OCR noise:
    // - allows a space instead of a dash (common ML Kit misread)
    // - allows the dashes to be dropped entirely (13 digits in a row)
    private val CNIC_REGEX = Regex(
        """(\d{5})[\s-]?(\d{7})[\s-]?(\d{1})(?!\d)"""
    )

    /** A single CNIC-format match found in the OCR text, normalized to NNNNN-NNNNNNN-N. */
    data class CnicMatch(
        val formatted: String,
        val rawText: String,
        val occurrenceCount: Int,
        val firstOccurrenceIndex: Int
    )

    /**
     * Finds every CNIC-format number in [text], normalizes each to the
     * standard dashed format, and returns them ranked by how many times
     * they occur (most frequent first). Ties keep the order they first
     * appeared in [text].
     *
     * On a B-Form, the guardian's CNIC typically repeats (it appears once
     * per child row plus the header), while each child's own B-Form/CRC
     * number appears only once. This frequency split is what
     * [extractPrimaryCnic] and [extractChildNumbers] key off of.
     */
    fun extractAll(text: String): List<CnicMatch> {
        val matches = CNIC_REGEX.findAll(text)
            .mapIndexed { index, m ->
                val formatted = "${m.groupValues[1]}-${m.groupValues[2]}-${m.groupValues[3]}"
                Triple(formatted, m.value, index)
            }
            .toList()

        if (matches.isEmpty()) return emptyList()

        return matches
            .groupBy { it.first }
            .map { (formatted, group) ->
                CnicMatch(
                    formatted = formatted,
                    rawText = group.first().second,
                    occurrenceCount = group.size,
                    firstOccurrenceIndex = group.minOf { it.third }
                )
            }
            .sortedByDescending { it.occurrenceCount }
    }

    /**
     * Returns the single most likely guardian/applicant CNIC in [text], or
     * null if none was found.
     *
     * On a plain CNIC card there is only one candidate, so this just
     * returns it. On a B-Form, multiple CNIC-format numbers exist
     * (guardian's CNIC + each child's B-Form number) — this returns the
     * one that occurs most often, which in practice is the guardian's
     * CNIC since it repeats across rows.
     */
    fun extractPrimaryCnic(text: String): String? {
        return extractAll(text).firstOrNull()?.formatted
    }

    /**
     * Returns each child's own B-Form/CRC number from a B-Form, in the
     * same top-to-bottom row order they appear on the physical form.
     *
     * Unlike the guardian's CNIC (which repeats once per row), each
     * child's number appears exactly once in the OCR text — so this
     * filters [extractAll] down to occurrenceCount == 1. If a form only
     * has one child, the guardian's CNIC still repeats at least twice
     * (header + the one row), so the split holds.
     */
    fun extractChildNumbers(text: String): List<String> {
        return extractAll(text)
            .filter { it.occurrenceCount == 1 }
            .sortedBy { it.firstOccurrenceIndex }
            .map { it.formatted }
    }

    /** True if [candidate] is a syntactically valid CNIC-format number. */
    fun isValidCnicFormat(candidate: String): Boolean {
        return Regex("""^\d{5}-\d{7}-\d$""").matches(candidate)
    }
}