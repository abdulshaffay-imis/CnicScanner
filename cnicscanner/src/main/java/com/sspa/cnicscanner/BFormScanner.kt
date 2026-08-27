package com.sspa.cnicscanner

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import androidx.exifinterface.media.ExifInterface
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.TextRecognizer
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import com.sspa.cnicscanner.CnicPatternExtractor
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Scans a NADRA B-Form (Bay Form / Child Registration Certificate) image
 * and extracts ONLY each child's own B-Form/CRC number — one per row, in
 * the same top-to-bottom order they appear on the physical form. The
 * guardian's CNIC and every other field (names, dates of birth, place of
 * birth, gender, etc.) are intentionally ignored.
 */
class BFormScanner(context: Context) {

    private val recognizer: TextRecognizer =
        TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)

    private val appContext = context.applicationContext

    sealed class BFormScanResult {
        /** [childNumbers] has one entry per child row, in form order. Size 1 for a single-child B-Form. */
        data class Success(val childNumbers: List<String>) : BFormScanResult()
        data class NotFound(val rawText: String) : BFormScanResult()
        data class Error(val throwable: Throwable) : BFormScanResult()
    }

    /** Scan from a content/file [Uri] (e.g. picked from gallery or camera). */
    suspend fun scan(uri: Uri): BFormScanResult {
        return try {
            val image = InputImage.fromFilePath(appContext, uri)
            scan(image)
        } catch (t: Throwable) {
            BFormScanResult.Error(t)
        }
    }

    /** Scan from an already-decoded [Bitmap]. [rotationDegrees] should reflect EXIF/camera rotation. */
    suspend fun scan(bitmap: Bitmap, rotationDegrees: Int = 0): BFormScanResult {
        return try {
            val image = InputImage.fromBitmap(bitmap, rotationDegrees)
            scan(image)
        } catch (t: Throwable) {
            BFormScanResult.Error(t)
        }
    }

    private suspend fun scan(image: InputImage): BFormScanResult {
        return try {
            val visionText = recognizeText(image)
            val fullText = visionText.text

            val childNumbers = CnicPatternExtractor.extractChildNumbers(fullText)
                .filter { CnicPatternExtractor.isValidCnicFormat(it) }

            if (childNumbers.isNotEmpty()) {
                BFormScanResult.Success(childNumbers)
            } else {
                BFormScanResult.NotFound(fullText)
            }
        } catch (t: Throwable) {
            BFormScanResult.Error(t)
        }
    }

    private suspend fun recognizeText(image: InputImage) =
        suspendCancellableCoroutine { continuation ->
            recognizer.process(image)
                .addOnSuccessListener { result ->
                    if (continuation.isActive) continuation.resume(result)
                }
                .addOnFailureListener { e ->
                    if (continuation.isActive) continuation.resumeWithException(e)
                }
        }

    fun close() {
        recognizer.close()
    }
}