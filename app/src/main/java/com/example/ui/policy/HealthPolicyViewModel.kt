package com.example.ui.policy

import android.app.Application
import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.domain.engine.AiModelConfig
import com.example.domain.engine.FirebaseHealthPolicyExplainer
import com.example.domain.engine.HealthPolicyExplainerContract
import com.example.domain.engine.HealthPolicyParser
import com.example.domain.engine.PolicyAnalysisOutcome
import com.example.model.HealthPolicyCopy
import com.example.model.HealthPolicyStep
import com.example.model.HealthPolicyUiState
import com.example.model.PolicyErrorType
import com.example.model.PolicyFileAttachment
import com.example.model.SupportedExplanationLanguage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.util.UUID

sealed class FileReadOutcome {
  data class Success(val attachment: PolicyFileAttachment) : FileReadOutcome()
  data class Error(val errorType: PolicyErrorType) : FileReadOutcome()
}

interface PolicyFileReader {
  suspend fun readFile(uri: Uri): FileReadOutcome
}

class AndroidPolicyFileReader(private val context: Context) : PolicyFileReader {

  companion object {
    val SUPPORTED_MIME_TYPES = arrayOf(
      "application/pdf",
      "image/jpeg",
      "image/png",
      "image/webp"
    )
    private const val BUFFER_SIZE = 16 * 1024
  }

  override suspend fun readFile(uri: Uri): FileReadOutcome = withContext(Dispatchers.IO) {
    try {
      val contentResolver = context.contentResolver
      val rawMime = contentResolver.getType(uri)?.lowercase().orEmpty()
      val resolvedName = queryDisplayName(uri) ?: "policy_document"
      val inferredMime = inferMimeType(rawMime, resolvedName)
        ?: return@withContext FileReadOutcome.Error(PolicyErrorType.UNREADABLE)

      val declaredSize = querySizeBytes(uri)
      if (declaredSize != null && declaredSize > AiModelConfig.MAX_RAW_PAYLOAD_BYTES) {
        return@withContext FileReadOutcome.Error(PolicyErrorType.FILE_TOO_LARGE)
      }

      val bytes = contentResolver.openInputStream(uri)?.use { input ->
        val output = ByteArrayOutputStream()
        val buffer = ByteArray(BUFFER_SIZE)
        var total = 0L
        while (true) {
          val read = input.read(buffer)
          if (read <= 0) break
          total += read
          if (total > AiModelConfig.MAX_RAW_PAYLOAD_BYTES) {
            return@withContext FileReadOutcome.Error(PolicyErrorType.FILE_TOO_LARGE)
          }
          output.write(buffer, 0, read)
        }
        output.toByteArray()
      } ?: return@withContext FileReadOutcome.Error(PolicyErrorType.UNREADABLE)

      if (bytes.isEmpty()) {
        return@withContext FileReadOutcome.Error(PolicyErrorType.UNREADABLE)
      }

      FileReadOutcome.Success(
        PolicyFileAttachment(
          id = UUID.randomUUID().toString(),
          fileName = resolvedName,
          mimeType = inferredMime,
          byteCount = bytes.size.toLong(),
          bytes = bytes
        )
      )
    } catch (_: SecurityException) {
      FileReadOutcome.Error(PolicyErrorType.UNREADABLE)
    } catch (_: IOException) {
      FileReadOutcome.Error(PolicyErrorType.UNREADABLE)
    } catch (_: OutOfMemoryError) {
      FileReadOutcome.Error(PolicyErrorType.FILE_TOO_LARGE)
    } catch (_: Exception) {
      FileReadOutcome.Error(PolicyErrorType.UNREADABLE)
    }
  }

  private fun queryDisplayName(uri: Uri): String? {
    return try {
      context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
        ?.use { cursor ->
          if (cursor.moveToFirst()) {
            val idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (idx >= 0) cursor.getString(idx) else null
          } else null
        }
    } catch (_: Exception) {
      null
    }
  }

  private fun querySizeBytes(uri: Uri): Long? {
    return try {
      context.contentResolver.query(uri, arrayOf(OpenableColumns.SIZE), null, null, null)
        ?.use { cursor ->
          if (cursor.moveToFirst()) {
            val idx = cursor.getColumnIndex(OpenableColumns.SIZE)
            if (idx >= 0 && !cursor.isNull(idx)) cursor.getLong(idx) else null
          } else null
        }
    } catch (_: Exception) {
      null
    }
  }

  private fun inferMimeType(mime: String, name: String): String? {
    if (mime in SUPPORTED_MIME_TYPES) return mime
    if (mime == "image/jpg") return "image/jpeg"
    val lower = name.lowercase()
    return when {
      lower.endsWith(".pdf") -> "application/pdf"
      lower.endsWith(".jpg") || lower.endsWith(".jpeg") -> "image/jpeg"
      lower.endsWith(".png") -> "image/png"
      lower.endsWith(".webp") -> "image/webp"
      mime.startsWith("image/") -> "image/jpeg"
      else -> null
    }
  }
}

/**
 * ViewModel for "Explain My Health Policy".
 * Holds all policy files, pasted text, and explanations exclusively in memory and purges
 * them immediately upon explicit deletion, navigation exit, or ViewModel clearance.
 */
class HealthPolicyViewModel @JvmOverloads constructor(
  application: Application,
  private val explainer: HealthPolicyExplainerContract = FirebaseHealthPolicyExplainer(),
  private val fileReader: PolicyFileReader = AndroidPolicyFileReader(application)
) : AndroidViewModel(application) {

  private val _uiState = MutableStateFlow(HealthPolicyUiState())
  val uiState: StateFlow<HealthPolicyUiState> = _uiState.asStateFlow()

  private var analysisJob: Job? = null

  fun startFlow(preferredLanguageCodeOrName: String?) {
    analysisJob?.cancel()
    analysisJob = null
    wipeAttachmentsInMemory()
    val safeCode = SupportedExplanationLanguage.fromCodeOrName(preferredLanguageCodeOrName)?.code
      ?: SupportedExplanationLanguage.ENGLISH.code
    _uiState.value = HealthPolicyUiState(
      currentStep = HealthPolicyStep.LANGUAGE_SELECTION,
      selectedLanguageCode = safeCode
    )
  }

  fun selectLanguage(languageCodeOrName: String) {
    val safeCode = SupportedExplanationLanguage.fromCodeOrName(languageCodeOrName)?.code
      ?: SupportedExplanationLanguage.ENGLISH.code
    _uiState.update { state ->
      state.copy(selectedLanguageCode = safeCode, errorType = null, errorMessage = null)
    }
  }

  fun confirmLanguageAndProceed() {
    _uiState.update { state ->
      state.copy(
        currentStep = HealthPolicyStep.UPLOAD_OR_PASTE,
        errorType = null,
        errorMessage = null
      )
    }
  }

  fun goBackToLanguageStep() {
    analysisJob?.cancel()
    analysisJob = null
    _uiState.update { state ->
      state.copy(
        currentStep = HealthPolicyStep.LANGUAGE_SELECTION,
        errorType = null,
        errorMessage = null
      )
    }
  }

  fun onFilesSelected(uris: List<Uri>) {
    if (uris.isEmpty()) return
    val currentState = _uiState.value
    val copy = HealthPolicyCopy.forLanguage(currentState.selectedLanguageCode)
    val remainingSlots = HealthPolicyCopy.MAX_POLICY_FILES - currentState.attachments.size

    if (remainingSlots <= 0) {
      _uiState.update {
        it.copy(
          errorType = PolicyErrorType.TOO_MANY_FILES,
          errorMessage = copy.errorTooManyFiles
        )
      }
      return
    }

    val truncatedNotice = uris.size > remainingSlots
    val urisToRead = uris.take(remainingSlots)

    viewModelScope.launch {
      val newlyAdded = mutableListOf<PolicyFileAttachment>()
      var firstError: PolicyErrorType? = null

      for (uri in urisToRead) {
        when (val outcome = fileReader.readFile(uri)) {
          is FileReadOutcome.Success -> newlyAdded.add(outcome.attachment)
          is FileReadOutcome.Error -> {
            if (firstError == null) firstError = outcome.errorType
          }
        }
      }

      _uiState.update { state ->
        val candidateList = state.attachments + newlyAdded
        val totalBytes = candidateList.sumOf { it.byteCount }
        if (newlyAdded.isNotEmpty() && !AiModelConfig.isPayloadWithinSafeLimits(totalBytes)) {
          newlyAdded.forEach { it.wipe() }
          state.copy(
            errorType = PolicyErrorType.FILE_TOO_LARGE,
            errorMessage = copy.errorTooLarge
          )
        } else if (firstError != null && newlyAdded.isEmpty()) {
          state.copy(
            errorType = firstError,
            errorMessage = resolveErrorMessage(firstError, state.selectedLanguageCode)
          )
        } else {
          val warningType = when {
            firstError != null -> firstError
            truncatedNotice -> PolicyErrorType.TOO_MANY_FILES
            else -> null
          }
          state.copy(
            attachments = candidateList,
            errorType = warningType,
            errorMessage = warningType?.let { resolveErrorMessage(it, state.selectedLanguageCode) }
          )
        }
      }
    }
  }

  fun removeAttachment(attachmentId: String) {
    _uiState.update { state ->
      val target = state.attachments.firstOrNull { it.id == attachmentId }
      target?.wipe()
      state.copy(
        attachments = state.attachments.filterNot { it.id == attachmentId },
        errorType = null,
        errorMessage = null
      )
    }
  }

  fun onPastedTextChanged(text: String) {
    _uiState.update { state ->
      state.copy(
        pastedPolicyText = text,
        errorType = null,
        errorMessage = null
      )
    }
  }

  fun clearError() {
    _uiState.update { it.copy(errorType = null, errorMessage = null) }
  }

  fun analyzePolicy() {
    val snapshot = _uiState.value
    if (!snapshot.canAnalyze) return

    if (snapshot.attachments.isNotEmpty() &&
      !AiModelConfig.isPayloadWithinSafeLimits(snapshot.totalAttachmentBytes)
    ) {
      val msg = resolveErrorMessage(PolicyErrorType.FILE_TOO_LARGE, snapshot.selectedLanguageCode)
      _uiState.update {
        it.copy(
          errorType = PolicyErrorType.FILE_TOO_LARGE,
          errorMessage = msg
        )
      }
      return
    }

    analysisJob?.cancel()
    _uiState.update {
      it.copy(
        currentStep = HealthPolicyStep.LOADING,
        errorType = null,
        errorMessage = null
      )
    }

    analysisJob = viewModelScope.launch {
      try {
        when (
          val outcome = explainer.explainPolicy(
            attachments = snapshot.attachments,
            pastedText = snapshot.pastedPolicyText,
            languageCode = snapshot.selectedLanguageCode
          )
        ) {
          is PolicyAnalysisOutcome.Success -> {
            _uiState.update {
              it.copy(
                currentStep = HealthPolicyStep.RESULT,
                explanation = outcome.explanation,
                errorType = null,
                errorMessage = null
              )
            }
          }

          is PolicyAnalysisOutcome.Error -> {
            _uiState.update { state ->
              state.copy(
                currentStep = HealthPolicyStep.UPLOAD_OR_PASTE,
                errorType = outcome.errorType,
                errorMessage = resolveErrorMessage(outcome.errorType, state.selectedLanguageCode)
              )
            }
          }
        }
      } catch (e: CancellationException) {
        throw e
      } catch (_: Exception) {
        _uiState.update { state ->
          state.copy(
            currentStep = HealthPolicyStep.UPLOAD_OR_PASTE,
            errorType = PolicyErrorType.GENERIC,
            errorMessage = resolveErrorMessage(PolicyErrorType.GENERIC, state.selectedLanguageCode)
          )
        }
      }
    }
  }

  fun cancelAnalysis() {
    analysisJob?.cancel()
    analysisJob = null
    _uiState.update { state ->
      if (state.currentStep == HealthPolicyStep.LOADING) {
        state.copy(currentStep = HealthPolicyStep.UPLOAD_OR_PASTE)
      } else {
        state
      }
    }
  }

  fun buildShareSummary(): String? {
    val explanation = _uiState.value.explanation ?: return null
    return HealthPolicyParser.buildShareSummaryText(explanation)
  }

  /**
   * Explicitly wipes all uploaded policy bytes, pasted text, and AI results from memory.
   */
  fun deleteDocumentAndReset() {
    analysisJob?.cancel()
    analysisJob = null
    wipeAttachmentsInMemory()
    _uiState.value = HealthPolicyUiState()
  }

  private fun wipeAttachmentsInMemory() {
    _uiState.value.attachments.forEach { it.wipe() }
  }

  private fun resolveErrorMessage(type: PolicyErrorType, languageCode: String): String {
    val copy = HealthPolicyCopy.forLanguage(languageCode)
    return when (type) {
      PolicyErrorType.NOT_A_HEALTH_POLICY -> copy.errorNotAPolicy
      PolicyErrorType.UNREADABLE -> copy.errorUnreadable
      PolicyErrorType.NO_INTERNET -> copy.errorNoInternet
      PolicyErrorType.FILE_TOO_LARGE -> copy.errorTooLarge
      PolicyErrorType.TOO_MANY_FILES -> copy.errorTooManyFiles
      PolicyErrorType.TIMEOUT -> copy.errorTimeout
      PolicyErrorType.GENERIC -> copy.errorGeneric
    }
  }

  override fun onCleared() {
    deleteDocumentAndReset()
    super.onCleared()
  }

  companion object {
    fun provideFactory(application: Application): ViewModelProvider.Factory =
      object : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
          return HealthPolicyViewModel(application) as T
        }
      }
  }
}
