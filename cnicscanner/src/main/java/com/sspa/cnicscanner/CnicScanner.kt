package com.sspa.cnicscanner

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import android.net.Uri
import android.provider.MediaStore
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.exifinterface.media.ExifInterface
import java.io.File
import java.io.FileOutputStream
import android.graphics.Bitmap
import android.graphics.Matrix
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContract
import androidx.activity.result.contract.ActivityResultContracts
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.documentscanner.GmsDocumentScannerOptions
import com.google.mlkit.vision.documentscanner.GmsDocumentScanning
import com.google.mlkit.vision.documentscanner.GmsDocumentScanningResult
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import com.sspa.cnicscanner.core.ImageSource
import com.sspa.cnicscanner.entities.CnicEntity
import com.sspa.cnicscanner.ocr.CnicOcrParser
import kotlinx.coroutines.suspendCancellableCoroutine
import java.util.UUID
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Thrown when the scanned image's OCR text does not match the expected
 * CNIC layout (e.g. it's a B-Form, NICOP, POC card, or an unrelated document).
 */
class InvalidCnicDocumentException(val side: String) :
    Exception("Scanned image does not appear to be a valid CNIC ($side)")

/**
 * Validates OCR text against CNIC-specific markers before any data is extracted.
 *
 * Uses fuzzy (edit-distance) word matching rather than literal string/regex
 * matching, since real OCR output on CNIC cards is often heavily garbled
 * ("National dentity Card", "ISAMIc REPURIC OF PAsoSTAN", etc.) — a purely
 * literal check breaks on every new misread pattern, whereas checking
 * "is there a word close enough to 'identity'?" tolerates dropped letters,
 * substituted letters, and similar OCR noise generically.
 *
 * FRONT: requires words similar to "national", "identity", and "card" all
 * present (in any order/position), and is rejected outright if it contains
 * a marker for a different NADRA document type (B-Form, NICOP, POC, etc.).
 *
 * BACK: requires the "Registrar General of Pakistan" issuer line (fuzzy —
 * at least 2 of the 3 words "registrar"/"general"/"pakistan", or "nadra"
 * paired with one of them). A bare 13-digit number is deliberately NOT
 * treated as sufficient on its own: other NADRA documents (e.g. a Form-B /
 * child registration certificate) also carry a 13-digit registration
 * number, so number-only matching would false-positive on them. The back
 * is also rejected outright on the same B-Form/NICOP/POC markers as the
 * front.
 */
private object CnicValidator {
    private val wordRegex = Regex("""[A-Za-z]+""")

    // Presence of any of these means this is a different NADRA document type,
    // not a CNIC — reject even if a CNIC-format number is also present.
    private val exclusionKeywords = listOf(
        "form b", "form-b", "family registration certificate",
        "bay form", "b-form", "child registration",
        "registration no", "registration number",
        "guardian", "juvenile card", "poc card",
        "nicop", "smart national identity card for overseas"
    )

    /** Classic Levenshtein edit distance between two strings. */
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

    /** True if any word in [words] is within [maxDistance] edits of [target]. */
    private fun hasWordSimilarTo(words: List<String>, target: String, maxDistance: Int): Boolean =
        words.any { editDistance(it, target) <= maxDistance }

    fun isValidFront(text: String): Boolean {
        val lower = text.lowercase()
        if (exclusionKeywords.any { lower.contains(it) }) return false

        val tokens = wordsOf(text)
        val hasNational = hasWordSimilarTo(tokens, "national", 2)
        val hasIdentity = hasWordSimilarTo(tokens, "identity", 3)
        val hasCard = hasWordSimilarTo(tokens, "card", 1)

        return hasNational && hasIdentity && hasCard
    }

    fun isValidBack(text: String): Boolean {
        val lower = text.lowercase()
        // Same exclusion list as the front: a Form-B / NICOP / POC card is not
        // a CNIC back, no matter what number or issuer text OCR picks up on it.
        if (exclusionKeywords.any { lower.contains(it) }) return false

        val tokens = wordsOf(text)

        // "Registrar General of Pakistan" appears above the signature on every
        // CNIC back. OCR frequently garbles it heavily ("Regisirar", "Genera1",
        // "Pak1stan", "0f"), so match loosely per-word — but require at least
        // 2 of the 3 words (or "nadra" plus one of them) so a document that
        // merely mentions "Pakistan" or "NADRA" in passing (e.g. a Form-B,
        // which is also NADRA-issued) doesn't slip through on a single word.
        val hasRegistrar = hasWordSimilarTo(tokens, "registrar", 2)
        val hasGeneral = hasWordSimilarTo(tokens, "general", 2)
        val hasPakistan = hasWordSimilarTo(tokens, "pakistan", 2)
        val hasNadra = hasWordSimilarTo(tokens, "nadra", 1)

        val coreMatches = listOf(hasRegistrar, hasGeneral, hasPakistan).count { it }
        val hasIssuerMarker = coreMatches >= 2 || (hasNadra && coreMatches >= 1)

        // Deliberately NOT accepting a bare 13-digit number as sufficient here —
        // other NADRA documents (Form-B, etc.) also carry a 13-digit number, so
        // that alone doesn't distinguish a CNIC back. The issuer marker is
        // mandatory; extractCnicNumber() is only used post-validation to help
        // fill the field.
        return hasIssuerMarker
    }

    /**
     * Finds a 13-digit CNIC number in [text], tolerant of common OCR digit
     * misreads (O/o -> 0, I/l/i -> 1, S/s -> 5, B/b -> 8, Z/z -> 2, G/g -> 6)
     * and of dashes/spaces/dots being dropped, doubled, or misplaced —
     * which happens often on the back because the number is printed
     * directly over the busy QR code pattern.
     *
     * Returns the number formatted as "XXXXX-XXXXXXX-X", or null if no
     * 13-digit run is found.
     */
    fun extractCnicNumber(text: String): String? {
        val cleaned = text
            .replace(Regex("[Oo]"), "0")
            .replace(Regex("[Ili]"), "1")
            .replace(Regex("[Ss]"), "5")
            .replace(Regex("[Bb]"), "8")
            .replace(Regex("[Zz]"), "2")
            .replace(Regex("[Gg]"), "6")

        // 5-7-1 grouping, with optional/garbled separators between groups.
        val pattern = Regex("""(\d{5})[\s\-.]?(\d{7})[\s\-.]?(\d{1})""")
        val match = pattern.find(cleaned) ?: return null
        return "${match.groupValues[1]}-${match.groupValues[2]}-${match.groupValues[3]}"
    }
}

/**
 * Main CNIC Scanner class for capturing and parsing Pakistani CNIC cards.
 *
 * This class provides functionality to:
 * - Scan CNIC from camera, gallery, or document scanner
 * - Perform OCR on CNIC images
 * - Validate that the scanned document is actually a CNIC (not a B-Form,
 *   NICOP, POC card, or unrelated document) before extracting any data —
 *   for both the front (identity-card markers) and the back (issuer
 *   markers or a valid 13-digit number)
 * - Extract structured data from CNIC text
 *
 * @param context Android context
 * @param activity Activity instance (must be ComponentActivity)
 * @param ocrParser Implementation of CnicOcrParser for text parsing
 *
 * Usage:
 * ```kotlin
 * val scanner = CnicScanner(context, activity, myOcrParser)
 * try {
 *     val cnicData = scanner.scanImage(ImageSource.CAMERA, isBackScan = false)
 *     // proceed with cnicData
 * } catch (e: InvalidCnicDocumentException) {
 *     // show "That doesn't look like a CNIC — please scan the ${e.side} of your card"
 * } catch (e: Exception) {
 *     // generic scan/OCR failure
 * }
 * ```
 */
class CnicScanner(
    private val context: Context,
    private val activity: Activity,
    private val ocrParser: CnicOcrParser
) {
    companion object {
        private const val TAG = "CnicScanner"
    }

    private val textRecognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
    private var cnicDetails = CnicEntity()
    private var isBackScan: Boolean = false

    /**
     * Scan a CNIC image from the specified source
     *
     * @param imageSource Source to capture image from (CAMERA, GALLERY, or DOCUMENT_SCANNER)
     * @param isBackScan Set to true when scanning the back of CNIC, false for front
     * @return CnicEntity with parsed CNIC information
     * @throws IllegalStateException if activity is not a ComponentActivity
     * @throws InvalidCnicDocumentException if the scanned document does not look like a CNIC
     */
    suspend fun scanImage(imageSource: ImageSource, isBackScan: Boolean = false): CnicEntity {
        this.isBackScan = isBackScan

        // Reset scanner state when starting a new scan
        if (!isBackScan) {
            // For front scan, reset everything
            cnicDetails = CnicEntity()
        } else {
            // For back scan, only reset text data but keep front image
            cnicDetails = cnicDetails.copy(
                cnic = "",
                cardType = "",
                name = "",
                father_name = "",
                date_of_birth = "",
                cnic_issue_date = "",
                cnic_expiry = "",
                gender = "",
                cnicHolderCountry = "",
                cnic_back = null // Reset back image
                // Keep cnic_front from previous scan
            )
        }

        return when (imageSource) {
            ImageSource.CAMERA -> scanFromCamera()
            ImageSource.GALLERY -> scanFromGallery()
            ImageSource.DOCUMENT_SCANNER -> scanFromDocumentScanner()
        }
    }

    /**
     * Process an already-obtained image Uri directly, bypassing the built-in
     * camera/gallery/document-scanner launchers. Useful for test harnesses or
     * callers that already have their own picker (e.g. ActivityResultContracts.GetContent()).
     *
     * @param uri Content or file Uri pointing at the image to scan
     * @param isBackScan Set to true when scanning the back of CNIC, false for front
     * @return CnicEntity with parsed CNIC information
     * @throws InvalidCnicDocumentException if the image does not look like a CNIC
     */
    suspend fun scanFromUri(uri: Uri, isBackScan: Boolean = false): CnicEntity {
        this.isBackScan = isBackScan

        if (!isBackScan) {
            cnicDetails = CnicEntity()
        } else {
            cnicDetails = cnicDetails.copy(
                cnic = "",
                cardType = "",
                name = "",
                father_name = "",
                date_of_birth = "",
                cnic_issue_date = "",
                cnic_expiry = "",
                gender = "",
                cnicHolderCountry = "",
                cnic_back = null
            )
        }

        return suspendCancellableCoroutine { continuation ->
            processCapturedImage(uri) { result ->
                if (!continuation.isActive) return@processCapturedImage
                result.fold(
                    onSuccess = { continuation.resume(it) },
                    onFailure = { continuation.resumeWithException(it) }
                )
            }
        }
    }

    /**
     * Registers a launcher with a unique key that auto-unregisters after delivering the result.
     * Also returns an `onCancel` cleanup you should call if the coroutine is cancelled.
     */
    private fun <I, O> ComponentActivity.registerOneShot(
        contract: ActivityResultContract<I, O>,
        onResult: (O) -> Unit
    ): Pair<ActivityResultLauncher<I>, () -> Unit> {
        val key = "one_shot_${UUID.randomUUID()}"
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

    private suspend fun scanFromCamera(): CnicEntity =
        suspendCancellableCoroutine { continuation ->
            // Use ML Kit Document Scanner for better document capture
            val options = GmsDocumentScannerOptions.Builder()
                .setGalleryImportAllowed(false) // Only camera capture for this function
                .setResultFormats(
                    GmsDocumentScannerOptions.RESULT_FORMAT_JPEG,
                    GmsDocumentScannerOptions.RESULT_FORMAT_PDF
                )
                .setPageLimit(1) // Single page for CNIC
                .setScannerMode(GmsDocumentScannerOptions.CAPTURE_MODE_MANUAL)
                .build()

            val scanner = GmsDocumentScanning.getClient(options)
            val activity = (activity as? ComponentActivity)
                ?: run {
                    continuation.resumeWithException(IllegalStateException("Activity not available"))
                    return@suspendCancellableCoroutine
                }

            scanner.getStartScanIntent(activity)
                .addOnSuccessListener { intentSender ->
                    val (launcher, onCancel) = activity.registerOneShot(
                        ActivityResultContracts.StartIntentSenderForResult()
                    ) { result ->
                        if (!continuation.isActive) return@registerOneShot
                        if (result.resultCode == Activity.RESULT_OK) {
                            val scanningResult = GmsDocumentScanningResult.fromActivityResultIntent(result.data)
                            val pages = scanningResult?.pages
                            if (!pages.isNullOrEmpty()) {
                                val uri = pages[0].imageUri
                                processCapturedImage(uri) { result ->
                                    result.fold(
                                        onSuccess = { continuation.resume(it) },
                                        onFailure = { continuation.resumeWithException(it) }
                                    )
                                }
                            } else {
                                continuation.resume(cnicDetails)
                            }
                        } else {
                            continuation.resume(cnicDetails)
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

    private suspend fun scanFromGallery(): CnicEntity =
        suspendCancellableCoroutine { continuation ->
            val pickIntent = Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI)

            val activity = (activity as? ComponentActivity)
                ?: run {
                    continuation.resumeWithException(IllegalStateException("Activity not available"))
                    return@suspendCancellableCoroutine
                }

            val (launcher, onCancel) = activity.registerOneShot(
                ActivityResultContracts.StartActivityForResult()
            ) { result ->
                if (!continuation.isActive) return@registerOneShot
                if (result.resultCode == Activity.RESULT_OK) {
                    val uri = result.data?.data
                    if (uri != null) {
                        processCapturedImage(uri) { result ->
                            result.fold(
                                onSuccess = { continuation.resume(it) },
                                onFailure = { continuation.resumeWithException(it) }
                            )
                        }
                    } else {
                        continuation.resume(cnicDetails)
                    }
                } else {
                    continuation.resume(cnicDetails)
                }
            }

            continuation.invokeOnCancellation { onCancel() }
            launcher.launch(pickIntent)
        }

    private suspend fun scanFromDocumentScanner(): CnicEntity =
        suspendCancellableCoroutine { continuation ->
            val options = GmsDocumentScannerOptions.Builder()
                .setGalleryImportAllowed(true)
                .setResultFormats(
                    GmsDocumentScannerOptions.RESULT_FORMAT_JPEG,
                    GmsDocumentScannerOptions.RESULT_FORMAT_PDF
                )
                .setPageLimit(2)
                .setScannerMode(GmsDocumentScannerOptions.CAPTURE_MODE_MANUAL)
                .build()

            val scanner = GmsDocumentScanning.getClient(options)
            val activity = (activity as? ComponentActivity)
                ?: run {
                    continuation.resumeWithException(IllegalStateException("Activity not available"))
                    return@suspendCancellableCoroutine
                }

            scanner.getStartScanIntent(activity)
                .addOnSuccessListener { intentSender ->
                    val (launcher, onCancel) = activity.registerOneShot(
                        ActivityResultContracts.StartIntentSenderForResult()
                    ) { result ->
                        if (!continuation.isActive) return@registerOneShot
                        if (result.resultCode == Activity.RESULT_OK) {
                            val scanningResult = GmsDocumentScanningResult.fromActivityResultIntent(result.data)
                            val pages = scanningResult?.pages
                            if (!pages.isNullOrEmpty()) {
                                val uri = pages[0].imageUri
                                processCapturedImage(uri) { result ->
                                    result.fold(
                                        onSuccess = { continuation.resume(it) },
                                        onFailure = { continuation.resumeWithException(it) }
                                    )
                                }
                            } else {
                                continuation.resume(cnicDetails)
                            }
                        } else {
                            continuation.resume(cnicDetails)
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

    /**
     * Decodes, corrects orientation, and caches the captured image, then runs OCR + validation.
     * The cnic_front/cnic_back URI is only written into cnicDetails after validation succeeds,
     * so a rejected (non-CNIC) image never leaves a dangling image reference behind.
     */
    private fun processCapturedImage(imageUri: Uri, callback: (Result<CnicEntity>) -> Unit) {
        try {
            val inputStream = context.contentResolver.openInputStream(imageUri)
            var bitmap = BitmapFactory.decodeStream(inputStream)
            inputStream?.close()

            if (bitmap == null) {
                Log.e(TAG, "Failed to decode bitmap from URI: $imageUri")
                callback(Result.failure(IllegalStateException("Could not decode image")))
                return
            }

            // Correct orientation based on EXIF tag (camera/gallery images are often
            // stored in sensor orientation with a rotation flag, not pre-rotated pixels)
            val rotationDegrees = getExifRotationDegrees(imageUri)
            bitmap = rotateBitmapIfNeeded(bitmap, rotationDegrees)

            // Save the corrected bitmap so OCR *and* whatever displays cnic_front/cnic_back
            // later both see the same upright image, instead of the original (possibly rotated) URI
            val correctedUri = saveBitmapToCache(bitmap, if (isBackScan) "cnic_back" else "cnic_front")

            // For the back, boost contrast before feeding ML Kit: the CNIC number is
            // printed directly over the QR code's busy black/white pattern, and the
            // extra contrast helps the recognizer separate ink from QR noise. This is
            // ONLY used for OCR input — the cached/displayed image above stays natural.
            val ocrBitmap = if (isBackScan) enhanceForOcr(bitmap) else bitmap
            val inputImage = InputImage.fromBitmap(ocrBitmap, 0)

            Log.d(TAG, "Processing image from URI: $imageUri (corrected rotation: ${rotationDegrees}°)")

            performOCR(inputImage) { result ->
                result.onSuccess {
                    // Only attach the image reference once we know it's a valid CNIC page
                    if (isBackScan) {
                        cnicDetails.cnic_back = correctedUri.toString()
                    } else {
                        cnicDetails.cnic_front = correctedUri.toString()
                    }
                }
                callback(result)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error processing image: ${e.message}")
            callback(Result.failure(e))
        }
    }

    /**
     * Reads the EXIF orientation tag and converts it to degrees of rotation needed
     * to display the image upright.
     */
    private fun getExifRotationDegrees(uri: Uri): Int {
        return try {
            context.contentResolver.openInputStream(uri)?.use { stream ->
                val exif = ExifInterface(stream)
                when (exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) {
                    ExifInterface.ORIENTATION_ROTATE_90 -> 90
                    ExifInterface.ORIENTATION_ROTATE_180 -> 180
                    ExifInterface.ORIENTATION_ROTATE_270 -> 270
                    else -> 0
                }
            } ?: 0
        } catch (e: Exception) {
            Log.e(TAG, "Failed reading EXIF orientation: ${e.message}")
            0
        }
    }

    private fun rotateBitmapIfNeeded(bitmap: Bitmap, degrees: Int): Bitmap {
        if (degrees == 0) return bitmap
        val matrix = Matrix().apply { postRotate(degrees.toFloat()) }
        val rotated = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
        if (rotated != bitmap) bitmap.recycle()
        return rotated
    }

    /**
     * Boosts contrast (and pulls brightness down slightly) so text printed over
     * a busy background — specifically the CNIC number sitting on top of the
     * back's QR code — reads more cleanly to ML Kit's text recognizer. Returns
     * a new bitmap; the source bitmap is left untouched since it's also used
     * for the cached/displayed image.
     */
    private fun enhanceForOcr(bitmap: Bitmap): Bitmap {
        val enhanced = Bitmap.createBitmap(bitmap.width, bitmap.height, Bitmap.Config.ARGB_8888)
        val contrast = 1.6f
        val brightness = -40f
        val colorMatrix = ColorMatrix(
            floatArrayOf(
                contrast, 0f, 0f, 0f, brightness,
                0f, contrast, 0f, 0f, brightness,
                0f, 0f, contrast, 0f, brightness,
                0f, 0f, 0f, 1f, 0f
            )
        )
        val paint = Paint().apply { colorFilter = ColorMatrixColorFilter(colorMatrix) }
        Canvas(enhanced).drawBitmap(bitmap, 0f, 0f, paint)
        return enhanced
    }

    private fun saveBitmapToCache(bitmap: Bitmap, prefix: String): Uri {
        val file = File(context.cacheDir, "${prefix}_${System.currentTimeMillis()}.jpg")
        FileOutputStream(file).use { out ->
            bitmap.compress(Bitmap.CompressFormat.JPEG, 90, out)
        }
        return Uri.fromFile(file)
    }

    /**
     * Runs OCR, validates the recognized text against CNIC-specific markers,
     * and only merges extracted fields into cnicDetails if validation passes.
     * If the document doesn't look like a CNIC, callback receives a failure
     * with InvalidCnicDocumentException and cnicDetails is left untouched.
     */
    private fun performOCR(inputImage: InputImage, callback: (Result<CnicEntity>) -> Unit) {
        textRecognizer.process(inputImage)
            .addOnSuccessListener { recognizedText ->
                val ocrResult = recognizedText.text
                Log.d(TAG, "OCR Result: $ocrResult")

                val side = if (isBackScan) "back" else "front"

                // Both sides are guarded, but on different markers: the front on
                // the "National Identity Card" wording, the back on the
                // "Registrar General of Pakistan" issuer line. Neither side
                // accepts a bare number match — some other NADRA documents
                // (e.g. Form-B) also carry a 13-digit number, so the number alone
                // is never treated as proof of being a CNIC.
                val isValid = if (isBackScan) {
                    CnicValidator.isValidBack(ocrResult)
                } else {
                    CnicValidator.isValidFront(ocrResult)
                }

                if (!isValid) {
                    Log.w(TAG, "Rejected non-CNIC document ($side). OCR text: $ocrResult")
                    callback(Result.failure(InvalidCnicDocumentException(side)))
                    return@addOnSuccessListener
                }

                val extractedEntity = ocrParser.parse(
                    ocrText = ocrResult,
                    existing = cnicDetails,
                    isBackScan = isBackScan
                )

                // Fallback for the CNIC number specifically: the parser's own regex
                // may be too strict for the OCR noise typical of text printed over
                // the QR code on the back. If the parser came back empty, try the
                // noise-tolerant extractor here before giving up on the field.
                val fallbackCnic = if (extractedEntity.cnic.isEmpty()) {
                    CnicValidator.extractCnicNumber(ocrResult)
                } else {
                    null
                }

                cnicDetails = cnicDetails.copy(
                    cnic = extractedEntity.cnic.ifEmpty { fallbackCnic ?: cnicDetails.cnic },
                    cardType = if (extractedEntity.cardType.isNotEmpty()) extractedEntity.cardType else cnicDetails.cardType,
                    name = if (extractedEntity.name.isNotEmpty()) extractedEntity.name else cnicDetails.name,
                    father_name = if (extractedEntity.father_name.isNotEmpty()) extractedEntity.father_name else cnicDetails.father_name,
                    date_of_birth = if (extractedEntity.date_of_birth.isNotEmpty()) extractedEntity.date_of_birth else cnicDetails.date_of_birth,
                    cnic_issue_date = if (extractedEntity.cnic_issue_date.isNotEmpty()) extractedEntity.cnic_issue_date else cnicDetails.cnic_issue_date,
                    cnic_expiry = if (extractedEntity.cnic_expiry.isNotEmpty()) extractedEntity.cnic_expiry else cnicDetails.cnic_expiry,
                    gender = if (extractedEntity.gender.isNotEmpty()) extractedEntity.gender else cnicDetails.gender,
                    cnicHolderCountry = if (extractedEntity.cnicHolderCountry.isNotEmpty()) extractedEntity.cnicHolderCountry else cnicDetails.cnicHolderCountry,
                    cnic_front = cnicDetails.cnic_front,
                    cnic_back = cnicDetails.cnic_back
                )

                Log.d(TAG, "Final CNIC Details with preserved images: $cnicDetails")
                callback(Result.success(cnicDetails))
            }
            .addOnFailureListener { exception ->
                Log.e(TAG, "OCR failed: ${exception.message}")
                callback(Result.failure(exception))
            }
    }
}