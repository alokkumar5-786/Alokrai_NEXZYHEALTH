package com.example.ui.upload

import android.app.Activity
import android.util.Log
import androidx.activity.result.IntentSenderRequest
import com.google.mlkit.vision.documentscanner.GmsDocumentScanner
import com.google.mlkit.vision.documentscanner.GmsDocumentScannerOptions
import com.google.mlkit.vision.documentscanner.GmsDocumentScanning

/**
 * Encapsulates official Google ML Kit Document Scanner client initialization,
 * multi-page configuration (up to 20 pages), and intent launch preparation.
 */
object DocumentScannerHelper {
  private const val TAG = "NexzyScannerHelper"

  /**
   * Maximum number of pages per multi-page medical test report scan.
   */
  const val SCANNER_PAGE_LIMIT = 20

  /**
   * Configures official ML Kit Document Scanner options:
   * - FULL mode: automatic edge detection, perspective correction, enhancement filters, and review.
   * - Multi-page scanning up to [SCANNER_PAGE_LIMIT].
   * - Both PDF and JPEG outputs enabled (PDF preferred for multi-page).
   * - Gallery import enabled for existing lab report photos.
   */
  fun createScannerOptions(): GmsDocumentScannerOptions {
    return GmsDocumentScannerOptions.Builder()
      .setGalleryImportAllowed(true)
      .setPageLimit(SCANNER_PAGE_LIMIT)
      .setResultFormats(
        GmsDocumentScannerOptions.RESULT_FORMAT_PDF,
        GmsDocumentScannerOptions.RESULT_FORMAT_JPEG
      )
      .setScannerMode(GmsDocumentScannerOptions.SCANNER_MODE_FULL)
      .build()
  }

  /**
   * Creates a [GmsDocumentScanner] client instance.
   */
  fun getScannerClient(options: GmsDocumentScannerOptions = createScannerOptions()): GmsDocumentScanner {
    return GmsDocumentScanning.getClient(options)
  }

  /**
   * Launches the ML Kit document scanner via [IntentSenderRequest] for use with
   * [androidx.activity.result.contract.ActivityResultContracts.StartIntentSenderForResult].
   */
  fun launchScanner(
    activity: Activity,
    scanner: GmsDocumentScanner = getScannerClient(),
    onIntentSenderReady: (IntentSenderRequest) -> Unit,
    onError: (String) -> Unit
  ) {
    scanner.getStartScanIntent(activity)
      .addOnSuccessListener { intentSender ->
        Log.i(TAG, "ML Kit Document Scanner IntentSender ready")
        val request = IntentSenderRequest.Builder(intentSender).build()
        onIntentSenderReady(request)
      }
      .addOnFailureListener { exception ->
        Log.e(TAG, "Failed to start document scanner: ${exception.message}", exception)
        val userFriendlyMessage = when {
          exception.message?.contains("Google Play Services", ignoreCase = true) == true ||
          exception.message?.contains("Play Services", ignoreCase = true) == true ->
            "Google Play services document scanner module is initializing or unavailable. Please try again in a moment or select an existing document file."
          else ->
            exception.localizedMessage ?: "Could not open document scanner. Please ensure Google Play services is updated."
        }
        onError(userFriendlyMessage)
      }
  }
}
