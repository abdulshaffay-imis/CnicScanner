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
 * Validates that OCR'd text actually came from a NADRA B-Form (Bay Form /
 * Child Registration Certificate) before any numbers are extracted from it.
 *
 * The distinguishing marker is the "CRC No:" label printed in the top-left
 * of every genuine B-Form (CRC = Child Registration Certificate). Without
 * this check, any document containing a CNIC-shaped number — including a
 * plain CNIC card, since the guardian's own CNIC on a B-Form is in that
 * same format — would be accepted as a B-Form.
 *
 * Uses the same fuzzy edit-distance word matching as the CNIC front/back
 * guardrails, since OCR commonly garbles short acronyms like "CRC"
 * ("GRC", "CBC", "0RC", "CRG").
 */
private object BFormValidator {
    private val wordRegex = Regex("""[A-Za-z0-9]+""")

    private fun editDistance(a: String, b: String): Int {
        if (a.isEmpty()) return b.length
        if (b.isEmpty()) return a.length
        val dp = Array(a.length + 1) { IntArray(b.length + 1) }
        for (i in 0..a.length) dp[i][0] = i
        for (j in 0..b.length) dp[0][j] = j
        for (i in 1..a.length) {
            for (j in 1..b.length) {
                val cost = if (a[i - 1] == b[j - 1]) 0 else 1
                dp[i][j] = minOf(
                    dp[i - 1][j] + 1,      // deletion
                    dp[i][j - 1] + 1,      // insertion
                    dp[i - 1][j - 1] + cost // substitution
                )
            }
        }
        return dp[a.length][b.length]
    }

    private fun wordsOf(text: String): List<String> =
        wordRegex.findAll(text).map { it.value.lowercase() }.toList()

    /**
     * True if [text] contains the "CRC No" label near the top-left of the form.
     *
     * "CRC" is checked as a near-exact 3-character token (max 1 edit) — it's
     * short enough that a looser threshold would start matching unrelated
     * words. "No" is deliberately NOT fuzzy-matched on its own (a 2-letter
     * word is within 1 edit of dozens of unrelated words and would make the
     * check meaningless); instead we also accept the label with no space
     * between "CRC" and "No"/"Number"/"#" ("CRCNo", "CRC#"), which is a common
     * OCR/layout artifact when the label and value sit close together.
     */
    fun hasCrcMarker(text: String): Boolean {
        val words = wordsOf(text)
        val hasCrcToken = words.any { editDistance(it, "crc") <= 1 }
        if (hasCrcToken) return true

        // Fallback for cases where OCR fused the label into one run, e.g.
        // "CRCNo:", "CRC#4521...", "C.R.C No".
        return Regex("""(?i)\bC\.?\s?R\.?\s?C\.?\s*(No\.?|Number|#)""").containsMatchIn(text)
    }
}

/**
 * Scans a NADRA B-Form (Bay Form / Child Registration Certificate) image
 * and extracts ONLY each child's own B-Form/CRC number — one per row, in
 * the same top-to-bottom order they appear on the physical form. The
 * guardian's CNIC and every other field (names, dates of birth, place of
 * birth, gender, etc.) are intentionally ignored.
 *
 * Before any extraction happens, the OCR text is checked for the "CRC No"
 * label that appears in the top-left of every genuine B-Form. If it's not
 * found, the scan is rejected as [BFormScanResult.Invalid] rather than
 * silently proceeding to pull a number off some other document that just
 * happens to contain a CNIC-shaped digit sequence.
 */
class BFormScanner(context: Context) {

    private val recognizer: TextRecognizer =
        TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)

    private val appContext = context.applicationContext

    sealed class BFormScanResult {
        /** [childNumbers] has one entry per child row, in form order. Size 1 for a single-child B-Form. */
        data class Success(val childNumbers: List<String>) : BFormScanResult()
        /** The "CRC No" marker was found, but no valid child number could be extracted from the text. */
        data class NotFound(val rawText: String) : BFormScanResult()
        /** The "CRC No" marker was NOT found — this document doesn't look like a B-Form. */
        data class Invalid(val rawText: String, val reason: String = "Not a valid B-Form") : BFormScanResult()
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

            if (!BFormValidator.hasCrcMarker(fullText)) {
                return BFormScanResult.Invalid(fullText)
            }

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