package com.sspa.cnicscanner

import android.app.Activity
import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import androidx.activity.ComponentActivity
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContract
import androidx.activity.result.contract.ActivityResultContracts
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.documentscanner.GmsDocumentScannerOptions
import com.google.mlkit.vision.documentscanner.GmsDocumentScanning
import com.google.mlkit.vision.documentscanner.GmsDocumentScanningResult
import com.google.mlkit.vision.text.Text
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.TextRecognizer
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import com.sspa.cnicscanner.CnicPatternExtractor
import kotlinx.coroutines.suspendCancellableCoroutine
import java.util.UUID
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.math.abs

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
 *
 * UNCHANGED from the previous version — this guardrail stays exactly as it was.
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
 * Finds each table row's date of birth from ML Kit's *line-level bounding
 * boxes* (not the flattened OCR string), and pairs it with the child in the
 * same row purely by vertical (top-to-bottom) position.
 *
 * Why bounding boxes and not a second pass over the flat text: the "date of
 * birth" column sits directly next to (same row as) the child/father/mother
 * ID-number columns, but a form also has an unrelated date printed near the
 * signature block at the bottom ("تاریخ اجراء" / issue date). A plain
 * "find all YYYY-MM-DD patterns in the text" pass would pick that up too and
 * throw off the row alignment. So a text line only counts as a "data row"
 * if, in addition to a date pattern, it ALSO sits in a line group that
 * contains a CNIC-shaped number — which the issue-date/signature line does
 * not. That keeps the row count matched to the number of children.
 */
private object BFormRowExtractor {

    private data class LineInfo(val text: String, val centerY: Int, val height: Int)

    // Loose CNIC-shape check (5-7-1 digits, tolerant of missing/garbled
    // separators) — used ONLY to decide "is this row a data row", not to
    // extract the number itself. Digit-only OCR misreads are normalized
    // first (same substitutions as the CNIC-side extractor).
    private val cnicShapeRegex = Regex("""\d{5}[\s\-.]?\d{7}[\s\-.]?\d""")

    // Two accepted date shapes: ISO (as printed on the form, YYYY-MM-DD) and
    // DD-MM-YYYY as a fallback in case a differently formatted B-Form is scanned.
    private val isoDateRegex = Regex("""(\d{4})[\s\-/.](\d{1,2})[\s\-/.](\d{1,2})""")
    private val dmyDateRegex = Regex("""(\d{1,2})[\s\-/.](\d{1,2})[\s\-/.](\d{4})""")

    private fun cleanDigits(text: String): String = text
        .replace(Regex("[Oo]"), "0")
        .replace(Regex("[Ili]"), "1")
        .replace(Regex("[Ss]"), "5")
        .replace(Regex("[Bb]"), "8")
        .replace(Regex("[Zz]"), "2")
        .replace(Regex("[Gg]"), "6")

    private fun looksLikeDataRow(rowText: String): Boolean =
        cnicShapeRegex.containsMatchIn(cleanDigits(rowText))

    private fun extractDate(rowText: String): String? {
        val cleaned = cleanDigits(rowText)

        isoDateRegex.find(cleaned)?.let { m ->
            val year = m.groupValues[1].toIntOrNull() ?: return@let
            val month = m.groupValues[2].toIntOrNull() ?: return@let
            val day = m.groupValues[3].toIntOrNull() ?: return@let
            if (year in 1900..2100 && month in 1..12 && day in 1..31) {
                return "%04d-%02d-%02d".format(year, month, day)
            }
        }
        dmyDateRegex.find(cleaned)?.let { m ->
            val day = m.groupValues[1].toIntOrNull() ?: return@let
            val month = m.groupValues[2].toIntOrNull() ?: return@let
            val year = m.groupValues[3].toIntOrNull() ?: return@let
            if (year in 1900..2100 && month in 1..12 && day in 1..31) {
                return "%04d-%02d-%02d".format(year, month, day)
            }
        }
        return null
    }

    private fun collectLines(visionText: Text): List<LineInfo> =
        visionText.textBlocks.flatMap { block ->
            block.lines.mapNotNull { line ->
                val box = line.boundingBox ?: return@mapNotNull null
                LineInfo(line.text, (box.top + box.bottom) / 2, box.height())
            }
        }.sortedBy { it.centerY }

    /**
     * Groups OCR lines into physical table rows using vertical proximity,
     * then returns one date-of-birth per row (in top-to-bottom order) for
     * every row that also looks like a data row (see [looksLikeDataRow]).
     * Rows without a usable date are skipped rather than emitting a null,
     * since [pairWithChildren] tolerates a shorter dates list (see its doc).
     */
    fun extractDatesOfBirthInRowOrder(visionText: Text): List<String> {
        val lines = collectLines(visionText)
        if (lines.isEmpty()) return emptyList()

        val medianHeight = lines.map { it.height }.sorted()[lines.size / 2].coerceAtLeast(1)
        val rowGapThreshold = (medianHeight * 0.7).toInt().coerceAtLeast(1)

        val rows = mutableListOf<MutableList<LineInfo>>()
        for (line in lines) {
            val lastRow = rows.lastOrNull()
            val lastRowCenterY = lastRow?.let { r -> r.sumOf { it.centerY } / r.size }
            if (lastRow != null && lastRowCenterY != null && abs(line.centerY - lastRowCenterY) <= rowGapThreshold) {
                lastRow.add(line)
            } else {
                rows.add(mutableListOf(line))
            }
        }

        return rows.mapNotNull { row ->
            val rowText = row.joinToString(" ") { it.text }
            if (!looksLikeDataRow(rowText)) return@mapNotNull null
            extractDate(rowText)
        }
    }

    /**
     * Pairs each child's number with the date of birth from the same table
     * row, both already in top-to-bottom order. If OCR missed a date on some
     * row (dates list shorter than numbers list), the trailing children are
     * paired with a null date rather than misaligning the rest of the list —
     * this can only under-fill from the end, never swap two children's DOBs.
     */
    fun pairWithChildren(childNumbers: List<String>, datesOfBirth: List<String>): List<BFormScanner.ChildRecord> =
        childNumbers.mapIndexed { index, number ->
            BFormScanner.ChildRecord(cnic = number, dateOfBirth = datesOfBirth.getOrNull(index))
        }
}

/**
 * Scans a NADRA B-Form (Bay Form / Child Registration Certificate) image
 * and extracts each child's own B-Form/CRC number together with that
 * child's date of birth, one entry per row, in the same top-to-bottom order
 * they appear on the physical form. The guardian's CNIC and all other
 * fields (names, place of birth, gender, etc.) are intentionally ignored.
 *
 * Before any extraction happens, the OCR text is checked for the "CRC No"
 * label that appears in the top-left of every genuine B-Form. If it's not
 * found, the scan is rejected as [BFormScanResult.Invalid] rather than
 * silently proceeding to pull a number off some other document that just
 * happens to contain a CNIC-shaped digit sequence. This guardrail is
 * unchanged from before.
 *
 * Capture now supports the ML Kit Document Scanner (auto-detect + auto-
 * capture), the same way [CnicScanner] does, in addition to the existing
 * uri/bitmap entry points for callers with their own picker.
 *
 * @param context Android context
 * @param activity Activity instance (must be ComponentActivity) — required
 *   for [scanUsingDocumentScanner]; the uri/bitmap-based scan() overloads
 *   don't need it.
 */
class BFormScanner(
    private val context: Context,
    private val activity: Activity
) {
    private val recognizer: TextRecognizer =
        TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)

    private val appContext = context.applicationContext

    /** One child row: their own CRC/B-Form number and their date of birth (null if OCR couldn't find it). */
    data class ChildRecord(val cnic: String, val dateOfBirth: String?)

    sealed class BFormScanResult {
        /** [children] has one entry per child row, in form order. Size 1 for a single-child B-Form. */
        data class Success(val children: List<ChildRecord>) : BFormScanResult()
        /** The "CRC No" marker was found, but no valid child number could be extracted from the text. */
        data class NotFound(val rawText: String) : BFormScanResult()
        /** The "CRC No" marker was NOT found — this document doesn't look like a B-Form. */
        data class Invalid(val rawText: String, val reason: String = "Not a valid B-Form") : BFormScanResult()
        data class Error(val throwable: Throwable) : BFormScanResult()
    }

    /**
     * Registers a launcher with a unique key that auto-unregisters after delivering the result.
     * Also returns an `onCancel` cleanup you should call if the coroutine is cancelled.
     * (Same helper as CnicScanner's — duplicated locally since the original is private to that class.)
     */
    private fun <I, O> ComponentActivity.registerOneShot(
        contract: ActivityResultContract<I, O>,
        onResult: (O) -> Unit
    ): Pair<ActivityResultLauncher<I>, () -> Unit> {
        val key = "bform_one_shot_${UUID.randomUUID()}"
        var launcherRef: ActivityResultLauncher<I>? = null
        val launcher = activityResultRegistry.register(key, contract) { result ->
            try {
                onResult(result)
            } finally {
                launcherRef?.unregister()
            }
        }
        launcherRef = launcher
        val onCancel = { launcherRef.unregister() }
        return launcher to onCancel
    }

    /**
     * Launches the ML Kit document scanner (auto-detects the document edges
     * and auto-captures, same as [CnicScanner.scanFromCamera]/
     * [CnicScanner.scanFromDocumentScanner]), OCRs the resulting page(s),
     * and runs the same CRC guardrail + child/DOB extraction as [scan].
     *
     * @param pageLimit B-Forms are almost always a single page, but this is
     *   left slightly above 1 in case a family with many children spans an
     *   addendum page. Pages are OCR'd and their rows concatenated in the
     *   order the scanner returns them.
     * @param allowGalleryImport whether the scanner UI also lets the user
     *   import an existing image instead of capturing live.
     * @param onPagesCaptured optional callback fired with the captured page
     *   URI(s) as soon as the scan completes, before OCR runs — use this if
     *   you want to show a preview of what was captured (e.g.
     *   `imagePreview.setImageURI(uris.first())`) without waiting on OCR.
     */
    suspend fun scanUsingDocumentScanner(
        pageLimit: Int = 2,
        allowGalleryImport: Boolean = true,
        onPagesCaptured: (List<Uri>) -> Unit = {}
    ): BFormScanResult = suspendCancellableCoroutine { continuation ->
        val activity = (activity as? ComponentActivity)
            ?: run {
                continuation.resumeWithException(IllegalStateException("Activity not available"))
                return@suspendCancellableCoroutine
            }

        val options = GmsDocumentScannerOptions.Builder()
            .setGalleryImportAllowed(allowGalleryImport)
            .setResultFormats(
                GmsDocumentScannerOptions.RESULT_FORMAT_JPEG,
                GmsDocumentScannerOptions.RESULT_FORMAT_PDF
            )
            .setPageLimit(pageLimit)
            .setScannerMode(GmsDocumentScannerOptions.CAPTURE_MODE_MANUAL)
            .build()

        val scanner = GmsDocumentScanning.getClient(options)

        scanner.getStartScanIntent(activity)
            .addOnSuccessListener { intentSender ->
                val (launcher, onCancel) = activity.registerOneShot(
                    ActivityResultContracts.StartIntentSenderForResult()
                ) { result ->
                    if (!continuation.isActive) return@registerOneShot
                    if (result.resultCode == Activity.RESULT_OK) {
                        val scanningResult = GmsDocumentScanningResult.fromActivityResultIntent(result.data)
                        val pages = scanningResult?.pages
                        if (pages.isNullOrEmpty()) {
                            continuation.resumeWithException(IllegalStateException("No pages captured"))
                            return@registerOneShot
                        }
                        val pageUris = pages.map { it.imageUri }
                        onPagesCaptured(pageUris)
                        processPages(pageUris) { scanResult ->
                            if (continuation.isActive) continuation.resume(scanResult)
                        }
                    } else {
                        continuation.resumeWithException(IllegalStateException("Scan cancelled"))
                    }
                }

                continuation.invokeOnCancellation { onCancel() }

                launcher.launch(
                    androidx.activity.result.IntentSenderRequest.Builder(intentSender).build()
                )
            }
            .addOnFailureListener { e ->
                if (continuation.isActive) continuation.resumeWithException(e)
            }
    }

    /** Scan from a content/file [Uri] (e.g. picked from gallery or camera). */
    suspend fun scan(uri: Uri): BFormScanResult {
        return try {
            val image = InputImage.fromFilePath(appContext, uri)
            scanSinglePage(image)
        } catch (t: Throwable) {
            BFormScanResult.Error(t)
        }
    }

    /** Scan from an already-decoded [Bitmap]. [rotationDegrees] should reflect EXIF/camera rotation. */
    suspend fun scan(bitmap: Bitmap, rotationDegrees: Int = 0): BFormScanResult {
        return try {
            val image = InputImage.fromBitmap(bitmap, rotationDegrees)
            scanSinglePage(image)
        } catch (t: Throwable) {
            BFormScanResult.Error(t)
        }
    }

    private suspend fun scanSinglePage(image: InputImage): BFormScanResult {
        return try {
            val visionText = recognizeText(image)
            buildResult(listOf(visionText))
        } catch (t: Throwable) {
            BFormScanResult.Error(t)
        }
    }

    /** Runs OCR on each page URI from the document scanner, in order, then builds one combined result. */
    private fun processPages(pageUris: List<Uri>, callback: (BFormScanResult) -> Unit) {
        val pageTexts = mutableListOf<Text>()

        fun processNext(index: Int) {
            if (index >= pageUris.size) {
                callback(
                    try {
                        buildResult(pageTexts)
                    } catch (t: Throwable) {
                        BFormScanResult.Error(t)
                    }
                )
                return
            }
            val image = try {
                InputImage.fromFilePath(appContext, pageUris[index])
            } catch (t: Throwable) {
                callback(BFormScanResult.Error(t))
                return
            }
            recognizer.process(image)
                .addOnSuccessListener { text ->
                    pageTexts.add(text)
                    processNext(index + 1)
                }
                .addOnFailureListener { e -> callback(BFormScanResult.Error(e)) }
        }

        processNext(0)
    }

    /** Combines CRC guardrail + number extraction + row-mapped DOB extraction across one or more pages. */
    private fun buildResult(pages: List<Text>): BFormScanResult {
        val fullText = pages.joinToString("\n") { it.text }

        if (!BFormValidator.hasCrcMarker(fullText)) {
            return BFormScanResult.Invalid(fullText)
        }

        val childNumbers = CnicPatternExtractor.extractChildNumbers(fullText)
            .filter { CnicPatternExtractor.isValidCnicFormat(it) }

        if (childNumbers.isEmpty()) {
            return BFormScanResult.NotFound(fullText)
        }

        val datesOfBirth = pages.flatMap { BFormRowExtractor.extractDatesOfBirthInRowOrder(it) }
        val children = BFormRowExtractor.pairWithChildren(childNumbers, datesOfBirth)

        return BFormScanResult.Success(children)
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