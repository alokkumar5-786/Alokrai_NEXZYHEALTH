package com.example.model

import android.graphics.Bitmap

/**
 * Represents a document selected by the user from device storage.
 * Does not expose raw file contents in logs or state.
 */
data class SelectedDocument(
  val uriString: String,
  val fileName: String,
  val mimeType: String,
  val fileSizeBytes: Long,
  val formattedSize: String,
  val isPdf: Boolean = mimeType.equals("application/pdf", ignoreCase = true) || fileName.endsWith(".pdf", ignoreCase = true),
  val isImage: Boolean = mimeType.startsWith("image/", ignoreCase = true) || fileName.endsWith(".jpg", ignoreCase = true) || fileName.endsWith(".jpeg", ignoreCase = true) || fileName.endsWith(".png", ignoreCase = true)
)

sealed class DocumentSelectionStatus {
  object Idle : DocumentSelectionStatus()
  data class DocumentReady(val document: SelectedDocument) : DocumentSelectionStatus()
  data class InvalidDocument(val reason: String) : DocumentSelectionStatus()
}

/**
 * State representing asynchronous document preview rendering.
 */
sealed class DocumentPreviewState {
  object None : DocumentPreviewState()
  object Loading : DocumentPreviewState()
  data class Ready(
    val isPdf: Boolean,
    val pageCount: Int = 1,
    val currentPage: Int = 1,
    val pageInfo: String = if (isPdf) "Page $currentPage of $pageCount" else "Single Image",
    val bitmap: Bitmap? = null,
    val width: Int = 0,
    val height: Int = 0,
    val errorNotice: String? = null
  ) : DocumentPreviewState()
  data class Error(val message: String) : DocumentPreviewState()
}

/**
 * State representing the end-to-end medical document extraction workflow.
 */
sealed class DocumentExtractionState {
  object Idle : DocumentExtractionState()
  object WaitingForConsent : DocumentExtractionState()
  object InProgress : DocumentExtractionState()
  data class Success(val result: DocumentExtractionResult) : DocumentExtractionState()
  data class Unidentified(val result: DocumentExtractionResult, val reason: String) : DocumentExtractionState()
  data class Failure(val message: String, val isRetryable: Boolean = true) : DocumentExtractionState()
}

data class ReportUploadUiState(
  val selectedDocument: SelectedDocument? = null,
  val selectionStatus: DocumentSelectionStatus = DocumentSelectionStatus.Idle,
  val previewState: DocumentPreviewState = DocumentPreviewState.None,
  val isPreviewModalVisible: Boolean = false,
  val selectedFamilyMember: FamilyMember? = null,
  val selectedCategory: String = "Blood Test (CBC / Metabolic)",
  val errorMessage: String? = null,
  val isReadyForUpload: Boolean = false,
  val isConsentDialogVisible: Boolean = false,
  val hasGivenAiConsent: Boolean = false,
  val isExtracting: Boolean = false,
  val extractionResult: DocumentExtractionResult? = null,
  val extractionState: DocumentExtractionState = DocumentExtractionState.Idle
)

