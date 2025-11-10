package com.sspa.cnicscanner.sample

import com.sspa.cnicscanner.entities.CnicEntity
import com.sspa.cnicscanner.ocr.CnicOcrParser

/**
 * Sample implementation of CnicOcrParser.
 * This is a reference implementation showing how to parse Pakistani CNIC data from OCR text.
 * 
 * Note: This is provided as an example. You should customize the parsing logic
 * based on your specific CNIC format and requirements.
 */
class SampleCnicOcrParser : CnicOcrParser {
    
    companion object {
        // CNIC number pattern: 12345-6789012-3
        private val CNIC_PATTERN = Regex("""(\d{5}-\d{7}-\d)""")
        
        // Date patterns: DD/MM/YYYY or DD-MM-YYYY
        private val DATE_PATTERN = Regex("""(\d{2}[/-]\d{2}[/-]\d{4})""")
        
        // Card type label
        private const val CARD_TYPE_LABEL = "National Identity Card"
        
        // Flexible pattern for "National Identity Card" to handle OCR errors
        // Handles: spaces, missing letters, character substitutions (l/I, 0/O, etc.)
        private val CARD_TYPE_PATTERN = Regex(
            """(?i)nat[io0]{0,2}na[l1i]{0,2}\s*[il1]{0,2}d[e3]{0,2}nt[il1]{0,2}ty\s*[c©]{0,2}ard""",
            RegexOption.IGNORE_CASE
        )
        
        // Common labels on CNIC
        private const val NAME_LABEL = "Name"
        private const val FATHER_NAME_LABEL = "Father"
        private const val DOB_LABEL = "Date of Birth"
        private const val ISSUE_DATE_LABEL = "Date of Issue"
        private const val EXPIRY_LABEL = "Date of Expiry"
        private const val GENDER_LABEL = "Sex"
        private const val COUNTRY_LABEL = "Country"
        
        // Address labels (on back)
        private const val PRESENT_ADDRESS_LABEL = "Present Address"
        private const val PERMANENT_ADDRESS_LABEL = "Permanent Address"
    }
    
    override fun parse(ocrText: String, existing: CnicEntity?, isBackScan: Boolean): CnicEntity {
        val entity = existing ?: CnicEntity()
        
        if (isBackScan) {
            // Parse back side (addresses)
            parseBackSide(ocrText, entity)
        } else {
            // Parse front side (main details)
            parseFrontSide(ocrText, entity)
        }
        
        return entity
    }
    
    private fun parseFrontSide(ocrText: String, entity: CnicEntity) {
        val lines = ocrText.lines().map { it.trim() }
        
        // Extract Card Type using flexible pattern matching
        // Remove all spaces from OCR text for better matching
        val cleanedText = ocrText.replace("\\s+".toRegex(), " ")
        
        // Try regex pattern first
        if (CARD_TYPE_PATTERN.containsMatchIn(cleanedText)) {
            entity.cardType = CARD_TYPE_LABEL
        } else {
            // Fallback: Use similarity matching for broken text
            val cardTypeFound = detectCardTypeWithSimilarity(cleanedText)
            if (cardTypeFound) {
                entity.cardType = CARD_TYPE_LABEL
            }
        }
        
        // Extract CNIC number
        CNIC_PATTERN.find(ocrText)?.let {
            entity.cnic = it.value
        }
        
        // Extract fields by looking for labels
        var i = 0
        while (i < lines.size) {
            val line = lines[i]
            
            when {
                // Name
                line.contains(NAME_LABEL, ignoreCase = true) -> {
                    entity.name = extractFieldValue(lines, i)
                }
                
                // Father's name
                line.contains(FATHER_NAME_LABEL, ignoreCase = true) -> {
                    entity.father_name = extractFieldValue(lines, i)
                }
                
                // Date of Birth
                line.contains(DOB_LABEL, ignoreCase = true) -> {
                    DATE_PATTERN.find(line)?.let {
                        entity.date_of_birth = it.value
                    } ?: run {
                        // Try next line
                        if (i + 1 < lines.size) {
                            DATE_PATTERN.find(lines[i + 1])?.let {
                                entity.date_of_birth = it.value
                            }
                        }
                    }
                }
                
                // Issue Date
                line.contains(ISSUE_DATE_LABEL, ignoreCase = true) -> {
                    DATE_PATTERN.find(line)?.let {
                        entity.cnic_issue_date = it.value
                    } ?: run {
                        if (i + 1 < lines.size) {
                            DATE_PATTERN.find(lines[i + 1])?.let {
                                entity.cnic_issue_date = it.value
                            }
                        }
                    }
                }
                
                // Expiry Date
                line.contains(EXPIRY_LABEL, ignoreCase = true) -> {
                    DATE_PATTERN.find(line)?.let {
                        entity.cnic_expiry = it.value
                    } ?: run {
                        if (i + 1 < lines.size) {
                            DATE_PATTERN.find(lines[i + 1])?.let {
                                entity.cnic_expiry = it.value
                            }
                        }
                    }
                }
                
                // Gender
                line.contains(GENDER_LABEL, ignoreCase = true) -> {
                    when {
                        line.contains("M", ignoreCase = true) || 
                        line.contains("Male", ignoreCase = true) -> entity.gender = "Male"
                        line.contains("F", ignoreCase = true) || 
                        line.contains("Female", ignoreCase = true) -> entity.gender = "Female"
                    }
                }
                
                // Country
                line.contains(COUNTRY_LABEL, ignoreCase = true) -> {
                    when {
                        line.contains("Pakistan", ignoreCase = true) -> entity.cnicHolderCountry = "Pakistan"
                        else -> {
                            // Try next line
                            if (i + 1 < lines.size && lines[i + 1].isNotBlank()) {
                                entity.cnicHolderCountry = lines[i + 1]
                            }
                        }
                    }
                }
            }
            
            i++
        }
    }
    
    private fun parseBackSide(ocrText: String, entity: CnicEntity) {
        val lines = ocrText.lines().map { it.trim() }
        
        var i = 0
        while (i < lines.size) {
            val line = lines[i]
            
            when {
                // Present Address
                line.contains(PRESENT_ADDRESS_LABEL, ignoreCase = true) -> {
                    entity.present_address = extractAddress(lines, i + 1)
                }
                
                // Permanent Address
                line.contains(PERMANENT_ADDRESS_LABEL, ignoreCase = true) -> {
                    entity.permanent_address = extractAddress(lines, i + 1)
                }
            }
            
            i++
        }
    }
    
    /**
     * Extract field value from current or next line
     */
    private fun extractFieldValue(lines: List<String>, currentIndex: Int): String {
        val currentLine = lines[currentIndex]
        
        // Check if value is on the same line after the label
        val parts = currentLine.split(":", limit = 2)
        if (parts.size == 2 && parts[1].trim().isNotEmpty()) {
            return parts[1].trim()
        }
        
        // Otherwise, take the next line
        if (currentIndex + 1 < lines.size) {
            val nextLine = lines[currentIndex + 1].trim()
            if (nextLine.isNotEmpty()) {
                return nextLine
            }
        }
        
        return ""
    }
    
    /**
     * Extract multi-line address
     */
    private fun extractAddress(lines: List<String>, startIndex: Int): String {
        val addressLines = mutableListOf<String>()
        var i = startIndex
        
        // Collect lines until we hit another label or empty line pattern
        while (i < lines.size && addressLines.size < 4) {
            val line = lines[i].trim()
            
            // Stop if we hit another major label
            if (line.contains(PRESENT_ADDRESS_LABEL, ignoreCase = true) ||
                line.contains(PERMANENT_ADDRESS_LABEL, ignoreCase = true)) {
                break
            }
            
            if (line.isNotEmpty()) {
                addressLines.add(line)
            } else if (addressLines.isNotEmpty()) {
                // Empty line after we've started collecting
                break
            }
            
            i++
        }
        
        return addressLines.joinToString(", ")
    }
    
    /**
     * Detect "National Identity Card" with similarity matching
     * Handles OCR errors like: "Nationa Ldenity ard", "National ldentity Card", etc.
     */
    private fun detectCardTypeWithSimilarity(text: String): Boolean {
        // Key words that should appear in some form
        val keywords = listOf("nation", "identity", "card")
        val normalizedText = text.lowercase().replace("\\s+".toRegex(), "")
        
        // Check if at least 2 out of 3 keywords are present (with typo tolerance)
        var matchCount = 0
        
        // Check for "nation" variants (nat, natio, nationa, national)
        if (normalizedText.contains(Regex("nat[io0]{0,4}na[l1]{0,2}"))) {
            matchCount++
        }
        
        // Check for "identity" variants (identit, identity, ldentity, etc.)
        if (normalizedText.contains(Regex("[il1]{0,2}d[e3]{0,2}nt[il1]{0,2}ty"))) {
            matchCount++
        }
        
        // Check for "card" variants (card, ard, cad, etc.)
        if (normalizedText.contains(Regex("[c©]{0,1}[ao0]{0,1}r{0,1}d"))) {
            matchCount++
        }
        
        // If at least 2 keywords match, consider it valid
        return matchCount >= 2
    }
}
