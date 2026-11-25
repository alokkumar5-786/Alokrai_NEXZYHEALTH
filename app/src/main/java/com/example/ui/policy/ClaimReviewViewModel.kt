package com.example.ui.policy

import android.app.Application
import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.domain.engine.AiModelConfig
import com.example.domain.engine.ClaimDocumentExtractionRecord
import com.example.domain.engine.ClaimDocumentExtractionStatus
import com.example.domain.engine.ClaimDocumentValidator
import com.example.domain.engine.ClaimPass2ComparisonOutcome
import com.example.domain.engine.ClaimPastedTextInput
import com.example.domain.engine.ClaimReviewExplainerContract
import com.example.domain.engine.FirebaseClaimReviewExplainer
import com.example.model.ClaimBucketType
import com.example.model.ClaimDocumentAttachment
import com.example.model.ClaimDocumentMetadata
import com.example.model.ClaimDocumentRole
import com.example.model.ClaimErrorType
import com.example.model.ClaimProcessingStage
import com.example.model.ClaimReviewCopy
import com.example.model.ClaimReviewUiState
import com.example.model.SupportedExplanationLanguage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.getAndUpdate
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.util.UUID
import java.util.concurrent.atomic.AtomicLong

sealed class ClaimFileReadOutcome {
  data class Success(
    val originalFileName: String,
    val declaredMimeType: String?,
    val bytes: ByteArray
  ) : ClaimFileReadOutcome()

  data class Error(
    val errorType: ClaimErrorType,
    val reason: String
  ) : ClaimFileReadOutcome()
}

interface ClaimFileReader {
  suspend fun readFile(uri: Uri): ClaimFileReadOutcome
}

/**
 * Ephemeral ContentResolver reader loading selected SAF/PhotoPicker documents strictly into
 * memory without writing copies to disk, Room, DataStore, or SharedPreferences.
 */
class AndroidClaimFileReader(private val context: Context) : ClaimFileReader {

  companion object {
    private const val BUFFER_SIZE = 16 * 1024
  }

  override suspend fun readFile(uri: Uri): ClaimFileReadOutcome = withContext(Dispatchers.IO) {
    try {
      val resolver = context.contentResolver
      val declaredMime = resolver.getType(uri)?.trim()?.takeIf { it.isNotEmpty() }
      val displayName = queryDisplayName(uri) ?: "claim_document"

      val declaredSize = querySizeBytes(uri)
      if (declaredSize != null && !AiModelConfig.isPayloadWithinSafeLimits(declaredSize)) {
        return@withContext ClaimFileReadOutcome.Error(
          errorType = ClaimErrorType.FILE_TOO_LARGE,
          reason = "Selected file exceeds the 14 MB safe payload limit."
        )
      }

      val bytes = resolver.openInputStream(uri)?.use { input ->
        val output = ByteArrayOutputStream()
        val buffer = ByteArray(BUFFER_SIZE)
        try {
          var total = 0L
          while (true) {
            currentCoroutineContext().ensureActive()
            val read = input.read(buffer)
            if (read <= 0) break
            total += read
            if (!AiModelConfig.isPayloadWithinSafeLimits(total)) {
              return@withContext ClaimFileReadOutcome.Error(
                errorType = ClaimErrorType.FILE_TOO_LARGE,
                reason = "Selected file exceeds the 14 MB safe payload limit."
              )
            }
            output.write(buffer, 0, read)
          }
          output.toByteArray()
        } finally {
          buffer.fill(0)
        }
      } ?: return@withContext ClaimFileReadOutcome.Error(
        errorType = ClaimErrorType.UNREADABLE,
        reason = "Could not open the selected document."
      )

      if (bytes.isEmpty()) {
        return@withContext ClaimFileReadOutcome.Error(
          errorType = ClaimErrorType.UNREADABLE,
          reason = "Selected document is empty."
        )
      }

      ClaimFileReadOutcome.Success(
        originalFileName = displayName,
        declaredMimeType = declaredMime,
        bytes = bytes
      )
    } catch (e: CancellationException) {
      throw e
    } catch (_: SecurityException) {
      ClaimFileReadOutcome.Error(ClaimErrorType.UNREADABLE, "Permission denied reading document.")
    } catch (_: IOException) {
      ClaimFileReadOutcome.Error(ClaimErrorType.UNREADABLE, "I/O error reading document.")
    } catch (_: OutOfMemoryError) {
      ClaimFileReadOutcome.Error(ClaimErrorType.FILE_TOO_LARGE, "Document exceeds available memory.")
    } catch (_: Exception) {
      ClaimFileReadOutcome.Error(ClaimErrorType.UNREADABLE, "Could not read selected document.")
    }
  }

  private fun queryDisplayName(uri: Uri): String? = try {
    context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
      ?.use { cursor ->
        if (cursor.moveToFirst()) {
          val idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
          if (idx >= 0) cursor.getString(idx) else null
        } else {
          null
        }
      }
  } catch (_: Exception) {
    null
  }

  private fun querySizeBytes(uri: Uri): Long? = try {
    context.contentResolver.query(uri, arrayOf(OpenableColumns.SIZE), null, null, null)
      ?.use { cursor ->
        if (cursor.moveToFirst()) {
          val idx = cursor.getColumnIndex(OpenableColumns.SIZE)
          if (idx >= 0 && !cursor.isNull(idx)) cursor.getLong(idx) else null
        } else {
          null
        }
      }
  } catch (_: Exception) {
    null
  }
}

/**
 * Stage 2 ViewModel for "Review My Rejected Health Claim".
 *
 * Security & Lifecycle Guarantees:
 * - Uploaded document bytes live strictly in transient in-memory [ClaimDocumentAttachment] objects.
 * - Requires explicit, non-persistent per-session privacy confirmation before any Gemini call starts.
 * - Enforces [ClaimDocumentValidator] before inference and runs sequential Pass 1 -> Pass 2 via
 *   [ClaimReviewExplainerContract].
 * - Never writes documents, quotes, or comparison results to Firestore, Room, DataStore, prefs, or disk.
 * - Never logs document content, filenames, insurer names, policy numbers, or quotations.
 * - Uses best-effort byte-buffer zeroing via [ClaimDocumentAttachment.wipe] on removal/reset/clear.
 *   (Note: zeroing owned ByteArrays is best-effort and cannot guarantee removal of all JVM/service copies.)
 */
class ClaimReviewViewModel @JvmOverloads constructor(
  application: Application,
  private val explainer: ClaimReviewExplainerContract = FirebaseClaimReviewExplainer(),
  private val fileReader: ClaimFileReader = AndroidClaimFileReader(application),
  private val pdfPageCounter: ClaimDocumentValidator.PdfPageCounter = ClaimDocumentValidator.DefaultPdfPageCounter
) : AndroidViewModel(application) {

  companion object {
    private const val TAG = "NexzyClaimReviewVM"

    fun provideFactory(application: Application): ViewModelProvider.Factory =
      object : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
          return ClaimReviewViewModel(application) as T
        }
      }
  }

  private val _uiState = MutableStateFlow(ClaimReviewUiState())
  val uiState: StateFlow<ClaimReviewUiState> = _uiState.asStateFlow()

  // Strictly in-memory, non-persistent session privacy acceptance
  private val _hasAcceptedPrivacyNotice = MutableStateFlow(false)
  val hasAcceptedPrivacyNotice: StateFlow<Boolean> = _hasAcceptedPrivacyNotice.asStateFlow()

  private var analysisJob: Job? = null
  private var activeRunId: Long = 0L
  private val sessionGeneration = AtomicLong(0L)
  private val pendingFileSelectionJobsLock = Any()
  private val pendingFileSelectionJobs = mutableSetOf<Job>()

  fun startFlow(preferredLanguageCodeOrName: String?) {
    val safeCode = SupportedExplanationLanguage.fromCodeOrName(preferredLanguageCodeOrName)?.code
      ?: SupportedExplanationLanguage.ENGLISH.code
    invalidateAndResetSessionState {
      ClaimReviewUiState(
        processingStage = ClaimProcessingStage.IDLE,
        selectedLanguageCode = safeCode
      )
    }
  }

  fun selectLanguage(languageCodeOrName: String) {
    cancelInFlightForInputChange()
    val safeCode = SupportedExplanationLanguage.fromCodeOrName(languageCodeOrName)?.code
      ?: SupportedExplanationLanguage.ENGLISH.code
    _uiState.update { state ->
      state.copy(
        selectedLanguageCode = safeCode,
        errorType = null,
        errorMessage = null,
        comparisonResult = null,
        processingStage = ClaimProcessingStage.IDLE
      )
    }
  }

  /**
   * Explicitly records per-session, non-persistent user acceptance that submitted documents will be
   * processed transiently by Google/Firebase AI services. Never persisted across sessions.
   */
  fun confirmPrivacyNotice() {
    _hasAcceptedPrivacyNotice.value = true
    _uiState.update { state ->
      if (state.processingStage == ClaimProcessingStage.ERROR && state.errorType == null) {
        state.copy(
          processingStage = ClaimProcessingStage.IDLE,
          errorMessage = null
        )
      } else {
        state
      }
    }
  }

  fun revokePrivacyNotice() {
    _hasAcceptedPrivacyNotice.value = false
  }

  fun onFilesSelected(
    bucket: ClaimBucketType,
    role: ClaimDocumentRole,
    uris: List<Uri>
  ) {
    if (uris.isEmpty()) return
    cancelInFlightForInputChange()

    val captureGeneration: Long
    val job: Job
    synchronized(pendingFileSelectionJobsLock) {
      captureGeneration = sessionGeneration.get()
      job = viewModelScope.launch(start = CoroutineStart.LAZY) {
        val accumulatedNew = mutableListOf<ClaimDocumentAttachment>()
        var committed = false
        try {
          var firstErrorType: ClaimErrorType? = null

          for (uri in uris) {
            currentCoroutineContext().ensureActive()
            if (captureGeneration != sessionGeneration.get()) return@launch

            when (val readOutcome = fileReader.readFile(uri)) {
              is ClaimFileReadOutcome.Success -> {
                if (captureGeneration != sessionGeneration.get() || !currentCoroutineContext().isActive) {
                  readOutcome.bytes.fill(0)
                  return@launch
                }
                val currentCombined = _uiState.value.attachments + accumulatedNew
                when (
                  val validation = ClaimDocumentValidator.validateCandidateFile(
                    bucket = bucket,
                    role = role,
                    fileName = readOutcome.originalFileName,
                    declaredMimeType = readOutcome.declaredMimeType,
                    bytes = readOutcome.bytes,
                    existingAttachments = currentCombined,
                    pdfPageCounter = pdfPageCounter
                  )
                ) {
                  is ClaimDocumentValidator.FileValidationOutcome.Valid -> {
                    accumulatedNew.add(validation.attachment)
                  }
                  is ClaimDocumentValidator.FileValidationOutcome.Invalid -> {
                    readOutcome.bytes.fill(0)
                    if (firstErrorType == null) firstErrorType = validation.errorType
                  }
                }
              }
              is ClaimFileReadOutcome.Error -> {
                if (captureGeneration != sessionGeneration.get() || !currentCoroutineContext().isActive) {
                  return@launch
                }
                if (firstErrorType == null) firstErrorType = readOutcome.errorType
              }
            }
          }

          currentCoroutineContext().ensureActive()

          synchronized(pendingFileSelectionJobsLock) {
            if (captureGeneration == sessionGeneration.get() && isActive) {
              _uiState.update { state ->
                val nextAttachments = state.attachments + accumulatedNew
                val err = firstErrorType
                state.copy(
                  attachments = nextAttachments,
                  comparisonResult = null,
                  processingStage = if (err != null && accumulatedNew.isEmpty()) {
                    ClaimProcessingStage.ERROR
                  } else {
                    ClaimProcessingStage.IDLE
                  },
                  errorType = err,
                  errorMessage = err?.let { resolveErrorMessage(it, state.selectedLanguageCode) }
                )
              }
              committed = true
            }
          }
        } finally {
          if (!committed || captureGeneration != sessionGeneration.get()) {
            accumulatedNew.forEach { it.wipe() }
            accumulatedNew.clear()
          }
        }
      }
      pendingFileSelectionJobs.add(job)
    }
    job.invokeOnCompletion {
      synchronized(pendingFileSelectionJobsLock) {
        pendingFileSelectionJobs.remove(job)
      }
    }
    job.start()
  }

  /**
   * Validates and attaches an in-memory document candidate directly through [ClaimDocumentValidator].
   */
  fun addCandidateDocument(
    bucket: ClaimBucketType,
    role: ClaimDocumentRole,
    fileName: String,
    declaredMimeType: String?,
    bytes: ByteArray,
    documentId: String = UUID.randomUUID().toString()
  ): Boolean {
    cancelInFlightForInputChange()
    val state = _uiState.value
    val validation = ClaimDocumentValidator.validateCandidateFile(
      bucket = bucket,
      role = role,
      fileName = fileName,
      declaredMimeType = declaredMimeType,
      bytes = bytes,
      existingAttachments = state.attachments,
      pdfPageCounter = pdfPageCounter,
      documentId = documentId
    )
    return when (validation) {
      is ClaimDocumentValidator.FileValidationOutcome.Valid -> {
        _uiState.update { current ->
          current.copy(
            attachments = current.attachments + validation.attachment,
            comparisonResult = null,
            processingStage = ClaimProcessingStage.IDLE,
            errorType = null,
            errorMessage = null
          )
        }
        true
      }
      is ClaimDocumentValidator.FileValidationOutcome.Invalid -> {
        _uiState.update { current ->
          current.copy(
            comparisonResult = null,
            processingStage = ClaimProcessingStage.ERROR,
            errorType = validation.errorType,
            errorMessage = resolveErrorMessage(validation.errorType, current.selectedLanguageCode)
          )
        }
        false
      }
    }
  }

  fun removeAttachment(documentId: String) {
    cancelInFlightForInputChange()
    _uiState.update { state ->
      val target = state.attachments.firstOrNull { it.documentId == documentId }
      target?.wipe()
      state.copy(
        attachments = state.attachments.filterNot { it.documentId == documentId },
        comparisonResult = null,
        processingStage = ClaimProcessingStage.IDLE,
        errorType = null,
        errorMessage = null
      )
    }
  }

  fun onPastedRejectionLetterTextChanged(text: String) {
    cancelInFlightForInputChange()
    _uiState.update { state ->
      state.copy(
        pastedRejectionLetterText = text,
        comparisonResult = null,
        processingStage = ClaimProcessingStage.IDLE,
        errorType = null,
        errorMessage = null
      )
    }
  }

  fun onPastedPolicyTextChanged(text: String) {
    cancelInFlightForInputChange()
    _uiState.update { state ->
      state.copy(
        pastedPolicyText = text,
        comparisonResult = null,
        processingStage = ClaimProcessingStage.IDLE,
        errorType = null,
        errorMessage = null
      )
    }
  }

  fun clearError() {
    _uiState.update { state ->
      state.copy(
        processingStage = if (state.processingStage == ClaimProcessingStage.ERROR) {
          ClaimProcessingStage.IDLE
        } else {
          state.processingStage
        },
        errorType = null,
        errorMessage = null
      )
    }
  }

  fun analyzeClaim() {
    val snapshot = _uiState.value

    // Gate 1: Non-persistent per-session privacy notice must be explicitly accepted before ANY AI request
    if (!_hasAcceptedPrivacyNotice.value) {
      _uiState.update { state ->
        state.copy(
          processingStage = ClaimProcessingStage.ERROR,
          comparisonResult = null,
          errorType = null,
          errorMessage = privacyNoticeRequiredMessage(state.selectedLanguageCode)
        )
      }
      return
    }

    val runId = ++activeRunId
    analysisJob?.cancel()

    _uiState.update { state ->
      state.copy(
        processingStage = ClaimProcessingStage.VALIDATING_DOCUMENTS,
        comparisonResult = null,
        errorType = null,
        errorMessage = null
      )
    }

    analysisJob = viewModelScope.launch {
      try {
        val safeLanguageCode = SupportedExplanationLanguage
          .fromCodeOrName(snapshot.selectedLanguageCode)?.code
          ?: SupportedExplanationLanguage.ENGLISH.code

        val validation = ClaimDocumentValidator.validateSubmission(
          attachments = snapshot.attachments,
          pastedRejectionLetterText = snapshot.pastedRejectionLetterText,
          pastedPolicyText = snapshot.pastedPolicyText
        )

        if (validation is ClaimDocumentValidator.SubmissionValidationOutcome.Invalid) {
          val isMissingRejection = validation.errorType == ClaimErrorType.NOT_A_REJECTION_LETTER &&
            !snapshot.hasRejectionLetterInput
          publishErrorIfActive(
            runId = runId,
            errorType = validation.errorType,
            languageCode = safeLanguageCode,
            isMissingRejection = isMissingRejection
          )
          return@launch
        }

        val rejectionAttachments = snapshot.attachments.filter { it.bucket == ClaimBucketType.REJECTION_LETTER }
        val policyAttachments = snapshot.attachments.filter { it.bucket == ClaimBucketType.POLICY_DOCS }
        val supportingAttachments = snapshot.attachments.filter { it.bucket == ClaimBucketType.SUPPORTING_CLAIM_DOCS }

        val pastedRejectionInput = snapshot.pastedRejectionLetterText
          .takeIf { it.isNotBlank() }
          ?.let { text ->
            ClaimPastedTextInput(
              documentId = UUID.randomUUID().toString(),
              bucket = ClaimBucketType.REJECTION_LETTER,
              role = ClaimDocumentRole.REJECTION_OR_SETTLEMENT_LETTER,
              originalFileName = "Pasted Rejection Letter",
              text = text
            )
          }

        val pastedPolicyInput = snapshot.pastedPolicyText
          .takeIf { it.isNotBlank() }
          ?.let { text ->
            ClaimPastedTextInput(
              documentId = UUID.randomUUID().toString(),
              bucket = ClaimBucketType.POLICY_DOCS,
              role = ClaimDocumentRole.BASE_POLICY_WORDING,
              originalFileName = "Pasted Policy Text",
              text = text
            )
          }

        val submittedMetadata = buildList<ClaimDocumentMetadata> {
          rejectionAttachments.forEach { add(it.toMetadata()) }
          pastedRejectionInput?.let { add(it.toMetadata()) }
          policyAttachments.forEach { add(it.toMetadata()) }
          pastedPolicyInput?.let { add(it.toMetadata()) }
          supportingAttachments.forEach { add(it.toMetadata()) }
        }

        // Only raw user-pasted text counts as deterministic non-AI text; uploaded PDFs/images NEVER have
        // Pass 1 AI extractions promoted into verifiedExtractedTextsByDocumentId.
        val mutableVerifiedPastedTexts = buildMap {
          pastedRejectionInput?.let { put(it.documentId, it.text) }
          pastedPolicyInput?.let { put(it.documentId, it.text) }
        }.toMutableMap()

        val pass1Records = mutableListOf<ClaimDocumentExtractionRecord>()
        val unreadablePolicyRecords = mutableListOf<ClaimDocumentExtractionRecord>()
        var hasReadablePolicyInput = false

        // Pass 1A: Mandatory Rejection Letter Extraction (sequential)
        if (!setStageIfActive(runId, ClaimProcessingStage.EXTRACTING_REJECTION_LETTER)) return@launch

        for (attachment in rejectionAttachments) {
          currentCoroutineContext().ensureActive()
          val record = explainer.extractDocument(attachment, safeLanguageCode)
          if (runId != activeRunId) return@launch
          pass1Records.add(record)
          if (record.status == ClaimDocumentExtractionStatus.FAILED) {
            publishErrorIfActive(
              runId = runId,
              errorType = record.errorType ?: ClaimErrorType.UNREADABLE,
              languageCode = safeLanguageCode
            )
            return@launch
          }
        }
        if (pastedRejectionInput != null) {
          currentCoroutineContext().ensureActive()
          val record = explainer.extractPastedText(pastedRejectionInput, safeLanguageCode)
          if (runId != activeRunId) return@launch
          pass1Records.add(record)
          if (record.status == ClaimDocumentExtractionStatus.FAILED) {
            publishErrorIfActive(
              runId = runId,
              errorType = record.errorType ?: ClaimErrorType.UNREADABLE,
              languageCode = safeLanguageCode
            )
            return@launch
          }
        }

        // Pass 1B: Policy Documents Extraction (sequential, optional bucket;
        // UNREADABLE records are preserved for limited INSUFFICIENT_INFORMATION comparison)
        if (policyAttachments.isNotEmpty() || pastedPolicyInput != null) {
          if (!setStageIfActive(runId, ClaimProcessingStage.EXTRACTING_POLICY_DOCUMENTS)) return@launch

          for (attachment in policyAttachments) {
            currentCoroutineContext().ensureActive()
            val record = explainer.extractDocument(attachment, safeLanguageCode)
            if (runId != activeRunId) return@launch
            pass1Records.add(record)
            if (record.status == ClaimDocumentExtractionStatus.FAILED) {
              val errType = record.errorType ?: ClaimErrorType.UNREADABLE
              if (errType == ClaimErrorType.UNREADABLE) {
                unreadablePolicyRecords.add(record)
              } else {
                publishErrorIfActive(
                  runId = runId,
                  errorType = errType,
                  languageCode = safeLanguageCode
                )
                return@launch
              }
            } else {
              hasReadablePolicyInput = true
            }
          }
          if (pastedPolicyInput != null) {
            currentCoroutineContext().ensureActive()
            val record = explainer.extractPastedText(pastedPolicyInput, safeLanguageCode)
            if (runId != activeRunId) return@launch
            pass1Records.add(record)
            if (record.status == ClaimDocumentExtractionStatus.FAILED) {
              val errType = record.errorType ?: ClaimErrorType.UNREADABLE
              if (errType == ClaimErrorType.UNREADABLE) {
                mutableVerifiedPastedTexts.remove(pastedPolicyInput.documentId)
                unreadablePolicyRecords.add(record)
              } else {
                publishErrorIfActive(
                  runId = runId,
                  errorType = errType,
                  languageCode = safeLanguageCode
                )
                return@launch
              }
            } else {
              hasReadablePolicyInput = true
            }
          }
        }

        // Pass 1C: Supporting Claim Documents Extraction (sequential, optional bucket)
        if (supportingAttachments.isNotEmpty()) {
          if (!setStageIfActive(runId, ClaimProcessingStage.EXTRACTING_SUPPORTING_DOCUMENTS)) return@launch

          for (attachment in supportingAttachments) {
            currentCoroutineContext().ensureActive()
            val record = explainer.extractDocument(attachment, safeLanguageCode)
            if (runId != activeRunId) return@launch
            pass1Records.add(record)
            if (record.status == ClaimDocumentExtractionStatus.FAILED) {
              publishErrorIfActive(
                runId = runId,
                errorType = record.errorType ?: ClaimErrorType.UNREADABLE,
                languageCode = safeLanguageCode
              )
              return@launch
            }
          }
        }

        // Pass 2: Structured Evidence Comparison (receives compact Pass 1 records, NEVER binary ByteArrays)
        if (!setStageIfActive(runId, ClaimProcessingStage.COMPARING_EVIDENCE)) return@launch
        currentCoroutineContext().ensureActive()

        when (
          val comparisonOutcome = explainer.compareExtractedDocuments(
            pass1Records = pass1Records.toList(),
            submittedDocuments = submittedMetadata,
            languageCode = safeLanguageCode,
            verifiedExtractedTextsByDocumentId = mutableVerifiedPastedTexts
          )
        ) {
          is ClaimPass2ComparisonOutcome.Success -> {
            if (runId == activeRunId) {
              val copy = ClaimReviewCopy.forLanguage(safeLanguageCode)
              val hasUnreadablePolicy = unreadablePolicyRecords.isNotEmpty()
              val hardenedResult = if (!hasUnreadablePolicy) {
                comparisonOutcome.result
              } else {
                val rawResult = comparisonOutcome.result
                val unreadableEntries = unreadablePolicyRecords.map { rec ->
                  val detail = rec.errorMessage?.trim()?.takeIf { it.isNotEmpty() }
                    ?: copy.unreadableDocumentMessage
                  "${rec.metadata.originalFileName}: $detail"
                }
                val downgraded = rawResult.outcomeDowngradedBySafetyGuard ||
                  rawResult.comparisonOutcome != com.example.model.ComparisonOutcome.INSUFFICIENT_INFORMATION
                val baseRationale = if (downgraded) {
                  copy.insufficientInformationExplanation
                } else {
                  rawResult.outcomeRationale
                }
                val combinedRationale = if (baseRationale.contains(copy.unreadableDocumentMessage)) {
                  baseRationale
                } else {
                  "$baseRationale (${copy.unreadableDocumentMessage}: ${unreadableEntries.joinToString("; ")})"
                }
                rawResult.copy(
                  comparisonOutcome = com.example.model.ComparisonOutcome.INSUFFICIENT_INFORMATION,
                  outcomeDowngradedBySafetyGuard = true,
                  hasPolicyDocumentsProvided = hasReadablePolicyInput,
                  outcomeRationale = combinedRationale,
                  missingEvidenceItems = (rawResult.missingEvidenceItems + unreadableEntries).distinct()
                )
              }

              _uiState.update { state ->
                state.copy(
                  processingStage = ClaimProcessingStage.COMPLETED,
                  comparisonResult = hardenedResult,
                  errorType = if (hasUnreadablePolicy) ClaimErrorType.UNREADABLE else null,
                  errorMessage = if (hasUnreadablePolicy) copy.unreadableDocumentMessage else null
                )
              }
            }
          }
          is ClaimPass2ComparisonOutcome.Error -> {
            publishErrorIfActive(
              runId = runId,
              errorType = comparisonOutcome.errorType,
              languageCode = safeLanguageCode
            )
          }
        }
      } catch (e: TimeoutCancellationException) {
        Log.e(TAG, "Claim review timed out")
        publishErrorIfActive(
          runId = runId,
          errorType = ClaimErrorType.TIMEOUT,
          languageCode = _uiState.value.selectedLanguageCode
        )
      } catch (e: CancellationException) {
        // Cooperatively propagate cancellation; never convert to normal failure
        throw e
      } catch (e: Exception) {
        // Never log filenames, document bytes, insurer names, policy numbers, or quotes
        Log.e(TAG, "Claim review failed: ${e.javaClass.simpleName}")
        val mapped = FirebaseClaimReviewExplainer.classifyException(e)
        publishErrorIfActive(
          runId = runId,
          errorType = mapped,
          languageCode = _uiState.value.selectedLanguageCode
        )
      }
    }
  }

  fun retryAnalysis() {
    analyzeClaim()
  }

  /**
   * Cancels an active analysis run and clears partial AI state while preserving
   * uploaded attachments and pasted text for retry.
   */
  fun cancelAnalysis() {
    activeRunId++
    analysisJob?.cancel()
    analysisJob = null
    _uiState.update { state ->
      state.copy(
        processingStage = ClaimProcessingStage.IDLE,
        comparisonResult = null,
        errorType = null,
        errorMessage = null
      )
    }
  }

  /**
   * User-triggered delete action clearing all owned attachment byte buffers, pasted text,
   * analysis results, and per-session privacy acceptance.
   */
  fun deleteDocumentAndReset() {
    invalidateAndResetSessionState { ClaimReviewUiState() }
  }

  /**
   * Explicit session cleanup method for application/authentication lifecycle integration
   * (e.g., sign-out or account switching).
   */
  fun clearSession() {
    deleteDocumentAndReset()
  }

  override fun onCleared() {
    clearSession()
    super.onCleared()
  }

  private fun invalidateAndResetSessionState(nextStateFactory: () -> ClaimReviewUiState) {
    val jobsToCancel: List<Job>
    val previousAttachments: List<ClaimDocumentAttachment>
    synchronized(pendingFileSelectionJobsLock) {
      sessionGeneration.incrementAndGet()
      jobsToCancel = pendingFileSelectionJobs.toList()
      pendingFileSelectionJobs.clear()
      previousAttachments = _uiState.getAndUpdate { nextStateFactory() }.attachments
      _hasAcceptedPrivacyNotice.value = false
    }
    jobsToCancel.forEach { it.cancel() }
    activeRunId++
    analysisJob?.cancel()
    analysisJob = null
    previousAttachments.forEach { it.wipe() }
  }

  private fun cancelInFlightForInputChange() {
    if (analysisJob != null) {
      activeRunId++
      analysisJob?.cancel()
      analysisJob = null
    }
  }

  private fun setStageIfActive(runId: Long, stage: ClaimProcessingStage): Boolean {
    if (runId != activeRunId) return false
    _uiState.update { it.copy(processingStage = stage) }
    return true
  }

  private fun publishErrorIfActive(
    runId: Long,
    errorType: ClaimErrorType,
    languageCode: String,
    isMissingRejection: Boolean = false
  ) {
    if (runId != activeRunId) return
    _uiState.update { state ->
      state.copy(
        processingStage = ClaimProcessingStage.ERROR,
        comparisonResult = null,
        errorType = errorType,
        errorMessage = resolveErrorMessage(errorType, languageCode, isMissingRejection)
      )
    }
  }


  private fun resolveErrorMessage(
    errorType: ClaimErrorType,
    languageCode: String,
    isMissingRejection: Boolean = false
  ): String {
    val copy = ClaimReviewCopy.forLanguage(languageCode)
    return when (errorType) {
      ClaimErrorType.NOT_A_REJECTION_LETTER ->
        if (isMissingRejection) copy.missingRejectionLetterMessage else copy.notARejectionLetterMessage
      ClaimErrorType.UNREADABLE -> copy.unreadableDocumentMessage
      ClaimErrorType.FILE_TOO_LARGE -> copy.fileTooLargeMessage
      ClaimErrorType.INVALID_FILE_TYPE -> copy.invalidFileMessage
      ClaimErrorType.NO_INTERNET -> copy.noInternetMessage
      ClaimErrorType.TIMEOUT -> copy.timeoutMessage
      ClaimErrorType.INVALID_RESPONSE -> copy.invalidResponseMessage
      ClaimErrorType.INTERNAL_ERROR -> copy.internalErrorMessage
    }
  }

  private fun privacyNoticeRequiredMessage(languageCode: String): String {
    return when (SupportedExplanationLanguage.fromCodeOrName(languageCode)) {
      SupportedExplanationLanguage.HINDI ->
        "विश्लेषण शुरू करने से पहले कृपया AI दस्तावेज़-प्रोसेसिंग सूचना स्वीकार करें।"
      SupportedExplanationLanguage.BENGALI ->
        "বিশ্লেষণ শুরু করার আগে অনুগ্রহ করে AI নথি-প্রক্রিয়াকরণ বিজ্ঞপ্তিটি গ্রহণ করুন।"
      SupportedExplanationLanguage.MARATHI ->
        "विश्लेषण सुरू करण्यापूर्वी कृपया AI दस्तऐवज-प्रक्रिया सूचना स्वीकारा."
      else ->
        "Please confirm the AI document-processing notice before starting claim comparison."
    }
  }
}
