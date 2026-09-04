package com.sspa.cnicscanner.ocr

import com.sspa.cnicscanner.entities.CnicEntity

/**
 * Default regex-based implementation of [CnicOcrParser] for Pakistani CNIC cards.
 *
 * This is a reasonable starting point for testing the scanner end-to-end.
 * OCR text layout can vary by print batch / camera angle / lighting, so
 * treat these patterns as a baseline to tune against real scanned samples
 * rather than a guaranteed-correct parser.
 */
class CnicOcrParserImpl : CnicOcrParser {

    private val cnicNumberRegex = Regex("""\d{5}-\d{7}-\d{1}|\d{13}""")
    // dd.MM.yyyy / dd-MM-yyyy / dd/MM/yyyy
    private val dateRegex = Regex("""\d{2}[./-]\d{2}[./-]\d{4}""")

    override fun parse(ocrText: String, existing: CnicEntity?, isBackScan: Boolean): CnicEntity {
        val base = existing ?: CnicEntity()
        val lines = ocrText.lines().map { it.trim() }.filter { it.isNotEmpty() }

        return if (!isBackScan) {
            parseFront(lines, ocrText, base)
        } else {
            parseBack(lines, ocrText, base)
        }
    }

    private fun parseFront(lines: List<String>, fullText: String, base: CnicEntity): CnicEntity {
        val cnic = cnicNumberRegex.find(fullText)?.value.orEmpty()

        val name = extractValueAfterLabel(lines, listOf("name"), excludeLabels = listOf("father"))
        val fatherName = extractValueAfterLabel(lines, listOf("father name", "father"))
        val gender = extractGender(fullText)
        val country = extractValueAfterLabel(lines, listOf("country of stay"))

        val dates = dateRegex.findAll(fullText).map { it.value }.toList()
        // Front typically shows Date of Birth only; issue/expiry usually on back,
        // but some layouts show all three on front — take them in order of appearance.
        val dob = dates.getOrNull(0).orEmpty()

        val cardType = when {
            fullText.contains("smart", ignoreCase = true) -> "Smart National Identity Card"
            fullText.contains("national identity card", ignoreCase = true) -> "National Identity Card"
            else -> base.cardType
        }

        return base.copy(
            cnic = cnic.ifEmpty { base.cnic },
            cardType = cardType,
            name = name.ifEmpty { base.name },
            father_name = fatherName.ifEmpty { base.father_name },
            date_of_birth = dob.ifEmpty { base.date_of_birth },
            gender = gender.ifEmpty { base.gender },
            cnicHolderCountry = country.ifEmpty { base.cnicHolderCountry }
        )
    }

    private fun parseBack(lines: List<String>, fullText: String, base: CnicEntity): CnicEntity {
        val cnic = cnicNumberRegex.find(fullText)?.value.orEmpty()
        val dates = dateRegex.findAll(fullText).map { it.value }.toList()

        // Back typically lists Date of Issue then Date of Expiry, in that order
        val issueDate = extractValueAfterLabel(lines, listOf("date of issue")).ifEmpty { dates.getOrNull(0).orEmpty() }
        val expiryDate = extractValueAfterLabel(lines, listOf("date of expiry")).ifEmpty { dates.getOrNull(1).orEmpty() }

        return base.copy(
            cnic = cnic.ifEmpty { base.cnic },
            cnic_issue_date = issueDate.ifEmpty { base.cnic_issue_date },
            cnic_expiry = expiryDate.ifEmpty { base.cnic_expiry }
        )
    }

    /**
     * Looks for a line matching one of [labels] and returns the text after the
     * label (same line, after ':' or the label itself) or, if the line is just
     * the label, the following line's text.
     */
    private fun extractValueAfterLabel(
        lines: List<String>,
        labels: List<String>,
        excludeLabels: List<String> = emptyList()
    ): String {
        for ((index, line) in lines.withIndex()) {
            val lower = line.lowercase()
            if (excludeLabels.any { lower.contains(it) }) continue

            val matchedLabel = labels.firstOrNull { lower.contains(it) } ?: continue

            // Same-line value after a colon, e.g. "Name: JOHN DOE"
            val colonIndex = line.indexOf(':')
            if (colonIndex != -1 && colonIndex + 1 < line.length) {
                val value = line.substring(colonIndex + 1).trim()
                if (value.isNotEmpty()) return value
            }

            // Same-line value after the label text itself, e.g. "Name JOHN DOE"
            val labelIndex = lower.indexOf(matchedLabel)
            val afterLabel = line.substring(labelIndex + matchedLabel.length)
                .trim(':', ' ', '-')
            if (afterLabel.isNotEmpty() && !afterLabel.equals(matchedLabel, ignoreCase = true)) {
                return afterLabel
            }

            // Otherwise assume the value is on the next non-empty line
            if (index + 1 < lines.size) {
                val next = lines[index + 1].trim()
                if (next.isNotEmpty() && !looksLikeAnotherLabel(next)) {
                    return next
                }
            }
        }
        return ""
    }

    private fun looksLikeAnotherLabel(line: String): Boolean {
        val lower = line.lowercase()
        val knownLabels = listOf(
            "name", "father", "gender", "country of stay", "date of birth",
            "identity", "date of issue", "date of expiry"
        )
        return knownLabels.any { lower.contains(it) }
    }

    private fun extractGender(text: String): String {
        val lower = text.lowercase()
        return when {
            Regex("""\bmale\b""").containsMatchIn(lower) && !lower.contains("female") -> "Male"
            lower.contains("female") -> "Female"
            else -> ""
        }
    }
}