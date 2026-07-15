package com.sspa.cnicscanner

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import android.net.Uri
import android.provider.MediaStore
import android.util.Log
import androidx.activity.ComponentActivity
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
 * Main CNIC Scanner class for capturing and parsing Pakistani CNIC cards.
 * 
 * This class provides functionality to:
 * - Scan CNIC from camera, gallery, or document scanner
 * - Perform OCR on CNIC images
 * - Extract structured data from CNIC text
 * 
 * @param context Android context
 * @param activity Activity instance (must be ComponentActivity)
 * @param ocrParser Implementation of CnicOcrParser for text parsing
 * 
 * Usage:
 * ```kotlin
 * val scanner = CnicScanner(context, activity, myOcrParser)
 * val cnicData = scanner.scanImage(ImageSource.CAMERA, isBackScan = false)
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
                                processCapturedImage(uri) { cnicResult -> continuation.resume(cnicResult) }
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
                        processCapturedImage(uri) { cnicResult -> continuation.resume(cnicResult) }
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
                                processCapturedImage(uri) { cnicResult -> continuation.resume(cnicResult) }
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

    private fun processCapturedImage(imageUri: Uri, callback: (CnicEntity) -> Unit) {
        try {
            val inputStream = context.contentResolver.openInputStream(imageUri)
            val bitmap = BitmapFactory.decodeStream(inputStream)
            if (bitmap == null) {
                Log.e(TAG, "Failed to decode bitmap from URI: $imageUri")
                callback(cnicDetails)
                return
            }
            val inputImage = InputImage.fromBitmap(bitmap, 0)

            Log.d(TAG, "Processing image from URI: $imageUri")

            // Store image to proper field based on scan side
            if (isBackScan) {
                cnicDetails.cnic_back = imageUri.toString()
            } else {
                cnicDetails.cnic_front = imageUri.toString()
            }

            performOCR(inputImage, callback)
            inputStream?.close()
        } catch (e: Exception) {
            Log.e(TAG, "Error processing image: ${e.message}")
            callback(cnicDetails)
        }
    }

    private fun performOCR(inputImage: InputImage, callback: (CnicEntity) -> Unit) {
        textRecognizer.process(inputImage)
            .addOnSuccessListener { recognizedText ->
                val ocrResult = recognizedText.text
                Log.d(TAG, "OCR Result: $ocrResult")
                val extractedEntity = ocrParser.parse(
                    ocrText = ocrResult,
                    existing = cnicDetails,
                    isBackScan = isBackScan
                )
                cnicDetails = cnicDetails.copy(
                    cnic = if (extractedEntity.cnic.isNotEmpty()) extractedEntity.cnic else cnicDetails.cnic,
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
                callback(cnicDetails)
            }
            .addOnFailureListener { exception ->
                Log.e(TAG, "OCR failed: ${exception.message}")
                callback(cnicDetails)
            }
    }
}
