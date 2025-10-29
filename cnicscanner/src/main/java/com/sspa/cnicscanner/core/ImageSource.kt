package com.sspa.cnicscanner.core

/**
 * Enum representing different sources from which CNIC images can be captured
 */
enum class ImageSource {
    /** Capture image directly from camera */
    CAMERA,
    
    /** Select image from device gallery */
    GALLERY,
    
    /** Use ML Kit Document Scanner for enhanced capture */
    DOCUMENT_SCANNER
}
