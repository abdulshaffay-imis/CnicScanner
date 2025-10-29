package com.sspa.cnicscanner.ocr

import com.sspa.cnicscanner.entities.CnicEntity

/**
 * Interface for parsing OCR text from CNIC images.
 * Implement this interface to provide custom parsing logic for extracting
 * CNIC information from recognized text.
 */
interface CnicOcrParser {
    /**
     * Parse OCR text and extract CNIC information
     * 
     * @param ocrText The raw text recognized from the CNIC image
     * @param existing Existing CNIC entity to merge data with (optional)
     * @param isBackScan True if scanning the back of the CNIC, false for front
     * @return CnicEntity with parsed information
     */
    fun parse(ocrText: String, existing: CnicEntity? = null, isBackScan: Boolean = false): CnicEntity
}
