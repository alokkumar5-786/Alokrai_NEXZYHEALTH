package com.example.ui.upload

import android.app.Application
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.provider.OpenableColumns
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.domain.engine.AiModelConfig
import com.example.domain.engine.DocumentExtractor
import com.example.domain.engine.MedicalDocumentExtractor
import com.example.model.DocumentExtractionResult
import com.example.model.DocumentExtractionState
import com.example.model.DocumentPreviewState
import com.example.model.DocumentSelectionStatus
import com.example.model.FamilyMember
import com.example.model.ReportUploadUiState
import com.example.model.SelectedDocument
import com.example.model.UniversalMedicalReport
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.IOException
import java.util.Locale
import kotlin.math.max

/**
 * Persistence abstraction for user AI medical-processing consent.
 */
interface AiConsentStore {
  fun hasGrantedAiConsent(): Boolean
  fun setAiConsentGranted(granted: Boolean)
}

class SharedPreferencesAiConsentStore(context: Context) : AiConsentStore {
  private val prefs = context.applicationContext.getSharedPreferences(
    "nexzy_ai_consent_prefs",
    Context.MODE_PRIVATE
  )

  override fun hasGrantedAiConsent(): Boolean = prefs.getBoolean(KEY_AI_CONSENT_GRANTED, false)

  override fun setAiConsentGranted(granted: Boolean) {
    prefs.edit().putBoolean(KEY_AI_CONSENT_GRANTED, granted).apply()
  }

  companion object {
    private const val KEY_AI_CONSENT_GRANTED = "ai_medical_processing_consent_granted_v1"
  }
}

/**
 * Abstraction for reading document bytes/bitmaps from device storage URIs.
 */
interface DocumentContentReader {
  suspend fun readPdfBytes(uriString: String): Result<ByteArray>
  suspend fun decodeBitmap(uriString: String): Result<Bitmap>
}

class AndroidDocumentContentReader(private val context: Context) : DocumentContentReader {
  override suspend fun readPdfBytes(uriString: String): Result<ByteArray> = withContext(Dispatchers.IO) {
    try {
      val uri = Uri.parse(uriString)
      val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
      if (bytes != null) {
        Result.success(bytes)
      } else {
        Result.failure(IOException("Could not read PDF bytes from device storage."))
      }
    } catch (e: Exception) {
      Result.failure(e)
    }
  }

  override suspend fun decodeBitmap(uriString: String): Result<Bitmap> = withContext(Dispatchers.IO) {
    try {
      val uri = Uri.parse(uriString)
      val bitmap = context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it) }
      if (bitmap != null) {
        Result.success(bitmap)
      } else {
        Result.failure(IOException("Unable to decode medical image for extraction."))
      }
    } catch (e: Exception) {
      Result.failure(e)
    }
  }
}

/**
 * Abstraction for rendering memory-safe document previews (PDF pages or images).
 */
interface DocumentPreviewLoader {
  suspend fun loadPreview(uri: Uri, isPdf: Boolean, pageIndex: Int): DocumentPreviewState
}

class AndroidDocumentPreviewLoader(private val context: Context) : DocumentPreviewLoader {
  companion object {
    private const val TAG = "NexzyPreviewLoader"
    private const val MAX_PREVIEW_DIMENSION = 1280
  }

  override suspend fun loadPreview(uri: Uri, isPdf: Boolean, pageIndex: Int): DocumentPreviewState =
    withContext(Dispatchers.IO) {
      if (isPdf) {
        renderPdfPreview(context, uri, pageIndex)
      } else {
        renderImagePreview(context, uri)
      }
    }

  private fun renderPdfPreview(context: Context, uri: Uri, pageIndex: Int): DocumentPreviewState {
    var pfd: ParcelFileDescriptor? = null
    var renderer: PdfRenderer? = null
    var page: PdfRenderer.Page? = null

    return try {
      pfd = context.contentResolver.openFileDescriptor(uri, "r")
      if (pfd == null) {
        return DocumentPreviewState.Error("Document access permission was revoked or file is missing.")
      }

      renderer = PdfRenderer(pfd)
      val totalPages = renderer.pageCount
      if (totalPages <= 0) {
        return DocumentPreviewState.Error("PDF contains no readable pages.")
      }

      val targetIndex = pageIndex.coerceIn(0, totalPages - 1)
      page = renderer.openPage(targetIndex)

      val pageWidth = page.width
      val pageHeight = page.height
      val scaleFactor = minOf(
        MAX_PREVIEW_DIMENSION.toFloat() / max(pageWidth, 1),
        MAX_PREVIEW_DIMENSION.toFloat() / max(pageHeight, 1),
        2.0f
      )
      val destWidth = (pageWidth * scaleFactor).toInt().coerceAtLeast(1)
      val destHeight = (pageHeight * scaleFactor).toInt().coerceAtLeast(1)

      val bitmap = Bitmap.createBitmap(destWidth, destHeight, Bitmap.Config.ARGB_8888)
      page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)

      DocumentPreviewState.Ready(
        isPdf = true,
        pageCount = totalPages,
        currentPage = targetIndex + 1,
        pageInfo = "Page ${targetIndex + 1} of $totalPages",
        bitmap = bitmap,
        width = destWidth,
        height = destHeight
      )
    } catch (e: SecurityException) {
      Log.w(TAG, "PDF is password-protected or encrypted: ${e.javaClass.simpleName}")
      DocumentPreviewState.Error("This PDF is password-protected. Please select an unencrypted copy.")
    } catch (e: IOException) {
      Log.w(TAG, "Corrupted PDF file: ${e.javaClass.simpleName}")
      DocumentPreviewState.Error("Corrupted or unreadable PDF document.")
    } catch (e: OutOfMemoryError) {
      Log.e(TAG, "Out of memory rendering PDF preview")
      DocumentPreviewState.Error("Document is too large to render preview on this device.")
    } catch (e: Exception) {
      Log.e(TAG, "Unexpected error rendering PDF preview: ${e.javaClass.simpleName}")
      DocumentPreviewState.Error("Unable to render document preview (${e.localizedMessage ?: "Unknown error"}).")
    } finally {
      try { page?.close() } catch (ignored: Exception) {}
      try { renderer?.close() } catch (ignored: Exception) {}
      try { pfd?.close() } catch (ignored: Exception) {}
    }
  }

  private fun renderImagePreview(context: Context, uri: Uri): DocumentPreviewState {
    return try {
      val boundsOptions = BitmapFactory.Options().apply {
        inJustDecodeBounds = true
      }
      context.contentResolver.openInputStream(uri)?.use { stream ->
        BitmapFactory.decodeStream(stream, null, boundsOptions)
      }

      val origWidth = boundsOptions.outWidth
      val origHeight = boundsOptions.outHeight
      if (origWidth <= 0 || origHeight <= 0) {
        return DocumentPreviewState.Error("Invalid or corrupted image file.")
      }

      var inSampleSize = 1
      while (origWidth / inSampleSize > MAX_PREVIEW_DIMENSION || origHeight / inSampleSize > MAX_PREVIEW_DIMENSION) {
        inSampleSize *= 2
      }

      val decodeOptions = BitmapFactory.Options().apply {
        this.inSampleSize = inSampleSize
        inPreferredConfig = Bitmap.Config.ARGB_8888
      }

      val sampledBitmap = context.contentResolver.openInputStream(uri)?.use { stream ->
        BitmapFactory.decodeStream(stream, null, decodeOptions)
      }

      if (sampledBitmap != null) {
        DocumentPreviewState.Ready(
          isPdf = false,
          pageCount = 1,
          currentPage = 1,
          pageInfo = "Single Image (${origWidth}x${origHeight})",
          bitmap = sampledBitmap,
          width = sampledBitmap.width,
          height = sampledBitmap.height
        )
      } else {
        DocumentPreviewState.Error("Unable to decode image preview.")
      }
    } catch (e: SecurityException) {
      Log.w(TAG, "SecurityException reading image URI: ${e.javaClass.simpleName}")
      DocumentPreviewState.Error("Permission to access image was revoked.")
    } catch (e: OutOfMemoryError) {
      Log.e(TAG, "OutOfMemory decoding image preview")
      DocumentPreviewState.Error("Image resolution is too high to generate a preview.")
    } catch (e: Exception) {
      Log.e(TAG, "Error decoding image preview: ${e.javaClass.simpleName}")
      DocumentPreviewState.Error("Could not render image preview.")
    }
  }
}

class ReportUploadViewModel @JvmOverloads constructor(
  application: Application,
  private val extractor: DocumentExtractor = MedicalDocumentExtractor(application),
  private val consentStore: AiConsentStore = SharedPreferencesAiConsentStore(application),
  private val contentReader: DocumentContentReader = AndroidDocumentContentReader(application),
  private val previewLoader: DocumentPreviewLoader = AndroidDocumentPreviewLoader(application)
) : AndroidViewModel(application) {

  companion object {
    private const val TAG = "NexzyUploadVM"
    const val MAX_FILE_SIZE_BYTES = 15L * 1024L * 1024L // 15 MB limit
    private const val MAX_PREVIEW_DIMENSION = 1280

    val SUPPORTED_MIME_TYPES = arrayOf(
      "application/pdf",
      "image/jpeg",
      "image/png"
    )

    fun provideFactory(
      application: Application
    ): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
      @Suppress("UNCHECKED_CAST")
      override fun <T : ViewModel> create(modelClass: Class<T>): T {
        return ReportUploadViewModel(application) as T
      }
    }

    /**
     * Formats bytes into human-readable MB or KB strings.
     */
    fun formatFileSize(bytes: Long): String {
      if (bytes <= 0) return "0 KB"
      val kb = bytes / 1024.0
      val mb = kb / 1024.0
      return when {
        mb >= 1.0 -> String.format(Locale.US, "%.1f MB", mb)
        else -> String.format(Locale.US, "%d KB", kb.toLong().coerceAtLeast(1L))
      }
    }

    /**
     * Validates whether the given MIME type and file name correspond to supported medical report formats.
     */
    fun isSupportedFileType(mimeType: String?, fileName: String?): Boolean {
      val cleanMime = mimeType?.lowercase() ?: ""
      val cleanName = fileName?.lowercase() ?: ""

      val validMimes = setOf("application/pdf", "image/jpeg", "image/jpg", "image/png")
      val validExtensions = listOf(".pdf", ".jpg", ".jpeg", ".png")

      val mimeMatches = validMimes.any { cleanMime.startsWith(it) }
      val extMatches = validExtensions.any { cleanName.endsWith(it) }

      return mimeMatches || extMatches
    }
  }

  private val _uiState = MutableStateFlow(
    ReportUploadUiState(hasGivenAiConsent = consentStore.hasGrantedAiConsent())
  )
  val uiState: StateFlow<ReportUploadUiState> = _uiState.asStateFlow()

  // Tracks currently held persistable URI for permission management
  private var heldPersistableUri: Uri? = null
  private var activeExtractionJob: Job? = null

  /**
   * Processes a content [Uri] returned from Android's Activity Result Document picker.
   * Manages persistable URI permissions, extracts metadata via ContentResolver,
   * and initiates asynchronous preview generation.
   */
  fun onDocumentSelected(uri: Uri?) {
    if (uri == null) {
      Log.d(TAG, "Document selection cancelled by user")
      return
    }

    val context = getApplication<Application>()

    // Release previously held URI permission when replacing selection
    releaseHeldUriPermission(context)

    // Acquire persistable read permission for the newly selected URI
    takePersistableUriPermission(context, uri)

    val resolved = resolveUriMetadata(context, uri)
    if (resolved != null) {
      applyDocument(resolved)
      loadDocumentPreview(uri, resolved.isPdf)
    } else {
      val errorMsg = "Could not read document information. Please choose another file."
      _uiState.update {
        it.copy(
          selectedDocument = null,
          selectionStatus = DocumentSelectionStatus.InvalidDocument(errorMsg),
          previewState = DocumentPreviewState.Error(errorMsg),
          errorMessage = errorMsg,
          isReadyForUpload = false
        )
      }
    }
  }

  /**
   * Applies validated document metadata into the UI state.
   */
  fun onDocumentMetadataProvided(
    fileName: String,
    mimeType: String,
    sizeBytes: Long,
    uriString: String
  ) {
    val validationError = validateDocumentMetadata(fileName, mimeType, sizeBytes)
    if (validationError != null) {
      _uiState.update {
        it.copy(
          selectedDocument = null,
          selectionStatus = DocumentSelectionStatus.InvalidDocument(validationError),
          previewState = DocumentPreviewState.Error(validationError),
          errorMessage = validationError,
          isReadyForUpload = false
        )
      }
      return
    }

    val document = SelectedDocument(
      uriString = uriString,
      fileName = fileName,
      mimeType = mimeType,
      fileSizeBytes = sizeBytes,
      formattedSize = formatFileSize(sizeBytes)
    )

    applyDocument(document)
    try {
      val uri = Uri.parse(uriString)
      loadDocumentPreview(uri, document.isPdf)
    } catch (e: Exception) {
      Log.w(TAG, "Could not parse URI for preview: ${e.javaClass.simpleName}")
    }
  }

  private fun applyDocument(document: SelectedDocument) {
    activeExtractionJob?.cancel()
    activeExtractionJob = null
    recycleCurrentPreviewBitmap()
    val consented = _uiState.value.hasGivenAiConsent || consentStore.hasGrantedAiConsent()
    _uiState.update {
      it.copy(
        selectedDocument = document,
        selectionStatus = DocumentSelectionStatus.DocumentReady(document),
        errorMessage = null,
        isReadyForUpload = true,
        isExtracting = false,
        hasGivenAiConsent = consented,
        extractionResult = null,
        extractionState = DocumentExtractionState.Idle
      )
    }
    Log.i(TAG, "Document applied: mime=${document.mimeType}, size=${document.formattedSize}")
  }

  /**
   * Asynchronously loads document preview with memory-safe bitmap rendering.
   */
  fun loadDocumentPreview(uri: Uri, isPdf: Boolean, pageIndex: Int = 0) {
    viewModelScope.launch {
      _uiState.update { it.copy(previewState = DocumentPreviewState.Loading) }

      val previewResult = previewLoader.loadPreview(uri, isPdf, pageIndex)

      _uiState.update { current ->
        current.copy(previewState = previewResult)
      }
    }
  }

  /**
   * Navigates to a specific page in a multi-page PDF document.
   */
  fun navigatePdfPage(next: Boolean) {
    val currentState = _uiState.value
    val readyPreview = currentState.previewState as? DocumentPreviewState.Ready ?: return
    if (!readyPreview.isPdf) return

    val totalPages = readyPreview.pageCount
    val currentPage = readyPreview.currentPage
    val targetPage = if (next) currentPage + 1 else currentPage - 1

    if (targetPage in 1..totalPages) {
      val uriString = currentState.selectedDocument?.uriString ?: return
      try {
        val uri = Uri.parse(uriString)
        loadDocumentPreview(uri, isPdf = true, pageIndex = targetPage - 1)
      } catch (e: Exception) {
        Log.w(TAG, "Could not navigate PDF page: ${e.javaClass.simpleName}")
      }
    }
  }

  /**
   * Toggles full-screen document preview modal visibility.
   */
  fun setPreviewModalVisible(visible: Boolean) {
    _uiState.update { it.copy(isPreviewModalVisible = visible) }
  }

  /**
   * Permanently removes the currently selected document and releases held URI permissions.
   */
  fun removeSelectedDocument() {
    activeExtractionJob?.cancel()
    activeExtractionJob = null
    val context = getApplication<Application>()
    releaseHeldUriPermission(context)
    recycleCurrentPreviewBitmap()
    val consented = _uiState.value.hasGivenAiConsent || consentStore.hasGrantedAiConsent()

    _uiState.update {
      it.copy(
        selectedDocument = null,
        selectionStatus = DocumentSelectionStatus.Idle,
        previewState = DocumentPreviewState.None,
        isPreviewModalVisible = false,
        errorMessage = null,
        isReadyForUpload = false,
        isConsentDialogVisible = false,
        hasGivenAiConsent = consented,
        isExtracting = false,
        extractionResult = null,
        extractionState = DocumentExtractionState.Idle
      )
    }
    Log.i(TAG, "Document removed and preview resources released")
  }

  /**
   * Action to select another document cleanly replacing any previous selection.
   */
  fun selectAnotherDocument() {
    removeSelectedDocument()
  }

  fun onFamilyMemberSelected(member: FamilyMember) {
    _uiState.update { it.copy(selectedFamilyMember = member) }
  }

  fun onCategorySelected(category: String) {
    _uiState.update { it.copy(selectedCategory = category) }
  }

  fun setConsentDialogVisible(visible: Boolean) {
    _uiState.update { current ->
      val updatedExtractionState = if (!visible && current.extractionState is DocumentExtractionState.WaitingForConsent) {
        DocumentExtractionState.Idle
      } else {
        current.extractionState
      }
      current.copy(
        isConsentDialogVisible = visible,
        extractionState = updatedExtractionState
      )
    }
  }

  /**
   * Entry point when user initiates extraction on a selected document:
   * - Validates document readiness, preview health, and 14 MB / 20 MB Base64 ceiling.
   * - Requests consent if never granted.
   * - Immediately starts real extraction if valid consent is already stored.
   */
  fun requestExtraction(
    onStarted: () -> Unit = {},
    onSuccess: (DocumentExtractionResult) -> Unit = {}
  ) {
    val validationFailure = validateReadyForExtraction()
    if (validationFailure != null) {
      _uiState.update {
        it.copy(
          errorMessage = validationFailure,
          extractionState = DocumentExtractionState.Failure(validationFailure, isRetryable = false)
        )
      }
      return
    }

    val hasConsent = _uiState.value.hasGivenAiConsent || consentStore.hasGrantedAiConsent()
    if (!hasConsent) {
      _uiState.update {
        it.copy(
          hasGivenAiConsent = false,
          isConsentDialogVisible = true,
          errorMessage = null,
          extractionState = DocumentExtractionState.WaitingForConsent
        )
      }
      return
    }

    _uiState.update { it.copy(hasGivenAiConsent = true, isConsentDialogVisible = false) }
    startDocumentExtraction(onSuccess = onSuccess, onStarted = onStarted)
  }

  fun confirmConsentAndStartExtraction(
    onStarted: () -> Unit = {},
    onSuccess: (DocumentExtractionResult) -> Unit = {}
  ) {
    consentStore.setAiConsentGranted(true)
    _uiState.update { it.copy(isConsentDialogVisible = false, hasGivenAiConsent = true) }
    startDocumentExtraction(onSuccess = onSuccess, onStarted = onStarted)
  }

  /**
   * Initiates extraction using [DocumentExtractor] with strict consent, preview, and payload guards.
   */
  fun startDocumentExtraction(
    onSuccess: (DocumentExtractionResult) -> Unit = {},
    onStarted: () -> Unit = {}
  ) {
    val hasConsent = _uiState.value.hasGivenAiConsent || consentStore.hasGrantedAiConsent()
    if (!hasConsent) {
      _uiState.update {
        it.copy(
          hasGivenAiConsent = false,
          isConsentDialogVisible = true,
          isExtracting = false,
          extractionState = DocumentExtractionState.WaitingForConsent
        )
      }
      return
    }

    val validationFailure = validateReadyForExtraction()
    if (validationFailure != null) {
      _uiState.update {
        it.copy(
          isExtracting = false,
          errorMessage = validationFailure,
          extractionState = DocumentExtractionState.Failure(validationFailure, isRetryable = false)
        )
      }
      return
    }

    val currentDoc = _uiState.value.selectedDocument ?: return

    activeExtractionJob?.cancel()
    _uiState.update {
      it.copy(
        hasGivenAiConsent = true,
        isConsentDialogVisible = false,
        isExtracting = true,
        errorMessage = null,
        extractionResult = null,
        extractionState = DocumentExtractionState.InProgress
      )
    }
    onStarted()

    activeExtractionJob = viewModelScope.launch {
      try {
        val result = if (currentDoc.isPdf) {
          val bytesResult = contentReader.readPdfBytes(currentDoc.uriString)
          bytesResult.fold(
            onSuccess = { bytes ->
              if (bytes.isEmpty()) {
                Result.failure(IOException("Selected PDF file is empty or unreadable."))
              } else if (!AiModelConfig.isPayloadWithinSafeLimits(bytes.size.toLong())) {
                val base64Mb = AiModelConfig.calculateBase64PayloadSize(bytes.size.toLong()) / (1024 * 1024)
                Result.failure(
                  IllegalArgumentException(
                    "Document exceeds the 20 MB AI request limit ($base64Mb MB Base64). Please select a document under 14 MB."
                  )
                )
              } else {
                val preview = _uiState.value.previewState as? DocumentPreviewState.Ready
                val pageCount = preview?.pageCount ?: 1
                extractor.extractFromPdfBytes(bytes, pageCount)
              }
            },
            onFailure = { readError -> Result.failure(readError) }
          )
        } else {
          // Image document extraction
          val previewBitmap = (_uiState.value.previewState as? DocumentPreviewState.Ready)
            ?.bitmap
            ?.takeIf { !it.isRecycled }
          if (previewBitmap != null) {
            extractor.extractFromBitmaps(listOf(previewBitmap))
          } else {
            val decoded = contentReader.decodeBitmap(currentDoc.uriString)
            decoded.fold(
              onSuccess = { bmp -> extractor.extractFromBitmaps(listOf(bmp)) },
              onFailure = { decodeError -> Result.failure(decodeError) }
            )
          }
        }

        result.fold(
          onSuccess = { extractionResult ->
            val nextExtractionState = if (extractionResult.report is UniversalMedicalReport.Unidentified) {
              DocumentExtractionState.Unidentified(
                result = extractionResult,
                reason = extractionResult.report.reason
              )
            } else {
              DocumentExtractionState.Success(extractionResult)
            }

            _uiState.update {
              it.copy(
                isExtracting = false,
                extractionResult = extractionResult,
                extractionState = nextExtractionState,
                errorMessage = null
              )
            }

            if (nextExtractionState is DocumentExtractionState.Success) {
              onSuccess(extractionResult)
            }
          },
          onFailure = { error ->
            val msg = error.localizedMessage ?: "Failed to extract information from document."
            _uiState.update {
              it.copy(
                isExtracting = false,
                extractionResult = null,
                errorMessage = msg,
                extractionState = DocumentExtractionState.Failure(msg, isRetryable = true)
              )
            }
          }
        )
      } catch (e: CancellationException) {
        throw e
      } catch (e: Exception) {
        val msg = e.localizedMessage ?: "Unexpected error during document extraction."
        _uiState.update {
          it.copy(
            isExtracting = false,
            extractionResult = null,
            errorMessage = msg,
            extractionState = DocumentExtractionState.Failure(msg, isRetryable = true)
          )
        }
      }
    }
  }

  /**
   * Retries extraction on the currently selected document without discarding selection or consent.
   */
  fun retryExtraction(
    onStarted: () -> Unit = {},
    onSuccess: (DocumentExtractionResult) -> Unit = {}
  ) {
    requestExtraction(onStarted = onStarted, onSuccess = onSuccess)
  }

  /**
   * Cancels an in-flight extraction cleanly and returns workflow state to Idle.
   */
  fun cancelActiveExtraction() {
    activeExtractionJob?.cancel()
    activeExtractionJob = null
    _uiState.update {
      it.copy(
        isExtracting = false,
        extractionState = DocumentExtractionState.Idle,
        errorMessage = null
      )
    }
  }

  private fun validateReadyForExtraction(): String? {
    val state = _uiState.value
    val currentDoc = state.selectedDocument
      ?: return "Please select a medical document to extract."

    val preview = state.previewState
    if (preview is DocumentPreviewState.Error) {
      return preview.message
    }

    if (!AiModelConfig.isPayloadWithinSafeLimits(currentDoc.fileSizeBytes)) {
      val base64Mb = AiModelConfig.calculateBase64PayloadSize(currentDoc.fileSizeBytes) / (1024 * 1024)
      return "Document exceeds the 20 MB AI request limit ($base64Mb MB Base64). Please select a document under 14 MB."
    }

    return null
  }

  fun clearError() {
    _uiState.update { current ->
      val nextExtraction = if (current.extractionState is DocumentExtractionState.Failure) {
        DocumentExtractionState.Idle
      } else {
        current.extractionState
      }
      current.copy(errorMessage = null, extractionState = nextExtraction)
    }
  }

  /**
   * Validates document metadata without accessing file contents.
   */
  fun validateDocumentMetadata(
    fileName: String,
    mimeType: String,
    sizeBytes: Long
  ): String? {
    if (!isSupportedFileType(mimeType, fileName)) {
      return "Unsupported file type. Please select a PDF, JPG, or PNG medical document."
    }

    if (sizeBytes <= 0) {
      return "Selected file appears to be empty (0 bytes). Please choose a valid report."
    }

    if (sizeBytes > MAX_FILE_SIZE_BYTES) {
      return "File size exceeds the 15 MB limit (${formatFileSize(sizeBytes)}). Please select a smaller report."
    }

    return null
  }

  private fun takePersistableUriPermission(context: Context, uri: Uri) {
    try {
      context.contentResolver.takePersistableUriPermission(
        uri,
        Intent.FLAG_GRANT_READ_URI_PERMISSION
      )
      heldPersistableUri = uri
      Log.d(TAG, "Acquired persistable URI read permission")
    } catch (e: SecurityException) {
      Log.d(TAG, "Persistable permission not supported or not granted by source: ${e.javaClass.simpleName}")
      heldPersistableUri = uri
    } catch (e: Exception) {
      Log.w(TAG, "Failed to take persistable URI permission: ${e.javaClass.simpleName}")
    }
  }

  private fun releaseHeldUriPermission(context: Context) {
    val uri = heldPersistableUri ?: return
    try {
      context.contentResolver.releasePersistableUriPermission(
        uri,
        Intent.FLAG_GRANT_READ_URI_PERMISSION
      )
      Log.d(TAG, "Released persistable URI permission")
    } catch (e: SecurityException) {
      Log.d(TAG, "SecurityException releasing persistable URI permission: ${e.javaClass.simpleName}")
    } catch (e: Exception) {
      Log.w(TAG, "Error releasing persistable URI permission: ${e.javaClass.simpleName}")
    } finally {
      heldPersistableUri = null
    }
  }

  private fun recycleCurrentPreviewBitmap() {
    val current = _uiState.value.previewState
    if (current is DocumentPreviewState.Ready && current.bitmap != null) {
      if (!current.bitmap.isRecycled) {
        current.bitmap.recycle()
      }
    }
  }

  private fun resolveUriMetadata(context: Context, uri: Uri): SelectedDocument? {
    var displayName: String? = null
    var sizeBytes: Long = -1L

    try {
      context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
        if (cursor.moveToFirst()) {
          val nameIdx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
          if (nameIdx != -1 && !cursor.isNull(nameIdx)) {
            displayName = cursor.getString(nameIdx)
          }

          val sizeIdx = cursor.getColumnIndex(OpenableColumns.SIZE)
          if (sizeIdx != -1 && !cursor.isNull(sizeIdx)) {
            sizeBytes = cursor.getLong(sizeIdx)
          }
        }
      }
    } catch (e: Exception) {
      Log.w(TAG, "ContentResolver query failed: ${e.javaClass.simpleName}")
    }

    if (displayName.isNullOrBlank()) {
      displayName = uri.lastPathSegment?.substringAfterLast('/') ?: "medical_report"
    }

    if (sizeBytes <= 0) {
      try {
        context.contentResolver.openInputStream(uri)?.use { stream ->
          sizeBytes = stream.available().toLong()
        }
      } catch (e: Exception) {
        Log.w(TAG, "Could not open stream for size check: ${e.javaClass.simpleName}")
      }
    }

    val mimeType = context.contentResolver.getType(uri) ?: when {
      displayName!!.endsWith(".pdf", ignoreCase = true) -> "application/pdf"
      displayName!!.endsWith(".jpg", ignoreCase = true) || displayName!!.endsWith(".jpeg", ignoreCase = true) -> "image/jpeg"
      displayName!!.endsWith(".png", ignoreCase = true) -> "image/png"
      else -> "application/octet-stream"
    }

    val validationError = validateDocumentMetadata(displayName!!, mimeType, sizeBytes)
    if (validationError != null) {
      _uiState.update {
        it.copy(
          selectedDocument = null,
          selectionStatus = DocumentSelectionStatus.InvalidDocument(validationError),
          previewState = DocumentPreviewState.Error(validationError),
          errorMessage = validationError,
          isReadyForUpload = false
        )
      }
      return null
    }

    return SelectedDocument(
      uriString = uri.toString(),
      fileName = displayName!!,
      mimeType = mimeType,
      fileSizeBytes = sizeBytes,
      formattedSize = formatFileSize(sizeBytes)
    )
  }

  override fun onCleared() {
    super.onCleared()
    activeExtractionJob?.cancel()
    val context = getApplication<Application>()
    releaseHeldUriPermission(context)
    recycleCurrentPreviewBitmap()
  }

  // Helper testing hooks to verify UI state without device hardware
  fun setPreviewStateForTesting(state: DocumentPreviewState) {
    _uiState.update { it.copy(previewState = state) }
  }

  fun setPersistableUriForTesting(uri: Uri?) {
    heldPersistableUri = uri
  }

  fun getHeldPersistableUriForTesting(): Uri? = heldPersistableUri
}
