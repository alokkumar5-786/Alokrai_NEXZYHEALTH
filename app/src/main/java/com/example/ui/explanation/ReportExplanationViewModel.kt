package com.example.ui.explanation

import android.app.Application
import android.content.Context
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.domain.engine.MedicalReportExplainer
import com.example.domain.engine.ReportExplainer
import com.example.firebase.FirebaseManager
import com.example.model.DocumentExtractionResult
import com.example.model.ExplanationConstants
import com.example.model.MedicalDocumentCategory
import com.example.model.ReportExplanationState
import com.example.model.UniversalMedicalReport
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * UID-scoped persistence contract for explicit user consent to send already-extracted
 * medical fields to Firebase AI Logic for plain-language explanations.
 */
interface ExplanationConsentStore {
  fun hasGrantedExplanationConsent(uid: String?): Boolean
  fun setExplanationConsentGranted(uid: String?, granted: Boolean)
  fun clearConsentForUser(uid: String?)
  fun clearAllConsents()
}

class SharedPreferencesExplanationConsentStore(context: Context) : ExplanationConsentStore {
  private val prefs = context.applicationContext.getSharedPreferences(
    "nexzy_ai_explanation_consent_prefs",
    Context.MODE_PRIVATE
  )

  init {
    clearLegacyUnscopedConsent()
  }

  override fun hasGrantedExplanationConsent(uid: String?): Boolean {
    clearLegacyUnscopedConsent()
    val safeUid = uid?.trim()?.takeIf { it.isNotEmpty() } ?: return false
    return prefs.getBoolean(keyForUid(safeUid), false)
  }

  override fun setExplanationConsentGranted(uid: String?, granted: Boolean) {
    clearLegacyUnscopedConsent()
    val safeUid = uid?.trim()?.takeIf { it.isNotEmpty() } ?: return
    if (granted) {
      prefs.edit().putBoolean(keyForUid(safeUid), true).apply()
    } else {
      prefs.edit().remove(keyForUid(safeUid)).apply()
    }
  }

  override fun clearConsentForUser(uid: String?) {
    clearLegacyUnscopedConsent()
    val safeUid = uid?.trim()?.takeIf { it.isNotEmpty() } ?: return
    prefs.edit().remove(keyForUid(safeUid)).apply()
  }

  override fun clearAllConsents() {
    prefs.edit().clear().apply()
  }

  private fun clearLegacyUnscopedConsent() {
    if (prefs.contains(LEGACY_KEY_EXPLANATION_CONSENT_GRANTED)) {
      prefs.edit().remove(LEGACY_KEY_EXPLANATION_CONSENT_GRANTED).apply()
    }
  }

  companion object {
    private const val LEGACY_KEY_EXPLANATION_CONSENT_GRANTED =
      "ai_plain_language_explanation_consent_v1"
    private const val KEY_PREFIX_EXPLANATION_CONSENT_UID =
      "ai_plain_language_explanation_consent_v2_"

    private fun keyForUid(uid: String): String = "$KEY_PREFIX_EXPLANATION_CONSENT_UID$uid"
  }
}

class ReportExplanationViewModel @JvmOverloads constructor(
  application: Application,
  private val explainer: ReportExplainer = MedicalReportExplainer(),
  private val consentStore: ExplanationConsentStore =
    SharedPreferencesExplanationConsentStore(application),
  private val currentUidProvider: () -> String? = {
    try {
      if (FirebaseManager.isInitialized) FirebaseManager.auth.currentUser?.uid else null
    } catch (_: Exception) {
      null
    }
  }
) : AndroidViewModel(application) {

  companion object {
    private const val TAG = "NexzyExplanationVM"

    fun provideFactory(application: Application): ViewModelProvider.Factory =
      object : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
          return ReportExplanationViewModel(application) as T
        }
      }
  }

  private var syncedExternalUid: String? = null
  private var boundUserUid: String? = normalizeUid(currentUidProvider())

  private val _uiState = MutableStateFlow<ReportExplanationState>(ReportExplanationState.Idle)
  val uiState: StateFlow<ReportExplanationState> = _uiState.asStateFlow()

  private val _hasExplanationConsent =
    MutableStateFlow(boundUserUid?.let { consentStore.hasGrantedExplanationConsent(it) } ?: false)
  val hasExplanationConsent: StateFlow<Boolean> = _hasExplanationConsent.asStateFlow()

  private val _isConsentDialogVisible = MutableStateFlow(false)
  val isConsentDialogVisible: StateFlow<Boolean> = _isConsentDialogVisible.asStateFlow()

  private var activeJob: Job? = null
  private var boundExtractionResult: DocumentExtractionResult? = null
  private var boundLanguage: String? = null

  private fun normalizeUid(uid: String?): String? = uid?.trim()?.takeIf { it.isNotEmpty() }

  private fun resolveAndSyncActiveUid(): String? {
    val activeUid = normalizeUid(currentUidProvider()) ?: syncedExternalUid
    if (activeUid != boundUserUid) {
      clear()
      boundUserUid = activeUid
      _hasExplanationConsent.value =
        activeUid?.let { consentStore.hasGrantedExplanationConsent(it) } ?: false
    }
    return activeUid
  }

  /**
   * Synchronizes in-memory explanation state and consent status with the active authenticated UID.
   * Switching users or signing out immediately resets prior in-memory explanation state.
   */
  fun syncUserSession(uid: String?) {
    val normalized = normalizeUid(uid)
    syncedExternalUid = normalized
    if (normalized != boundUserUid) {
      clear()
      boundUserUid = normalized
    }
    _hasExplanationConsent.value =
      normalized?.let { consentStore.hasGrantedExplanationConsent(it) } ?: false
  }

  /**
   * Synchronizes cached explanation state with the currently active extraction result.
   * Whenever the user removes or switches documents, stale explanation state is cleared.
   */
  fun syncForExtractionResult(extractionResult: DocumentExtractionResult?) {
    if (extractionResult == null) {
      if (boundExtractionResult != null || _uiState.value !is ReportExplanationState.Idle) {
        clear()
      }
      return
    }
    if (boundExtractionResult !== extractionResult) {
      activeJob?.cancel()
      activeJob = null
      boundExtractionResult = extractionResult
      boundLanguage = null
      _isConsentDialogVisible.value = false
      _uiState.value = ReportExplanationState.Idle
    }
  }

  fun requestExplanation(
    extractionResult: DocumentExtractionResult?,
    language: String = ExplanationConstants.DEFAULT_EXPLANATION_LANGUAGE
  ) {
    val activeUid = resolveAndSyncActiveUid()
    syncForExtractionResult(extractionResult)

    if (extractionResult == null) {
      _uiState.value = ReportExplanationState.Error(
        message = "No extracted medical report is available to explain.",
        isRetryable = false
      )
      return
    }

    if (extractionResult.report is UniversalMedicalReport.Unidentified ||
      extractionResult.category == MedicalDocumentCategory.UNCERTAIN
    ) {
      _uiState.value = ReportExplanationState.Error(
        message = "Unidentified or non-medical documents cannot be explained.",
        isRetryable = false
      )
      return
    }

    if (activeUid == null) {
      _hasExplanationConsent.value = false
      _isConsentDialogVisible.value = false
      _uiState.value = ReportExplanationState.Error(
        message = "Please sign in to generate plain-language explanations.",
        isRetryable = false
      )
      return
    }

    val safeLanguage = ExplanationConstants.resolveSafeExplanationLanguage(language)
    val consentGranted = consentStore.hasGrantedExplanationConsent(activeUid)
    if (!consentGranted) {
      _hasExplanationConsent.value = false
      _isConsentDialogVisible.value = true
      _uiState.value = ReportExplanationState.WaitingForConsent
      return
    }

    _hasExplanationConsent.value = true
    _isConsentDialogVisible.value = false

    val current = _uiState.value
    if (current is ReportExplanationState.Success &&
      boundExtractionResult === extractionResult &&
      boundLanguage == safeLanguage
    ) {
      return
    }

    activeJob?.cancel()
    boundExtractionResult = extractionResult
    boundLanguage = safeLanguage
    _uiState.value = ReportExplanationState.Loading

    activeJob = viewModelScope.launch {
      try {
        val result = explainer.explainReport(extractionResult, safeLanguage)
        result.fold(
          onSuccess = { explanation ->
            if (boundExtractionResult === extractionResult && boundUserUid == activeUid) {
              _uiState.value = ReportExplanationState.Success(explanation)
            }
          },
          onFailure = { error ->
            Log.e(TAG, "Explanation request failed: ${error.javaClass.simpleName}")
            if (boundExtractionResult === extractionResult && boundUserUid == activeUid) {
              _uiState.value = ReportExplanationState.Error(
                message = error.localizedMessage
                  ?: "Could not generate a plain-language explanation right now.",
                isRetryable = true
              )
            }
          }
        )
      } catch (e: CancellationException) {
        throw e
      } catch (e: Exception) {
        Log.e(TAG, "Unexpected explanation failure: ${e.javaClass.simpleName}")
        if (boundExtractionResult === extractionResult && boundUserUid == activeUid) {
          _uiState.value = ReportExplanationState.Error(
            message = e.localizedMessage
              ?: "Unexpected error while generating plain-language explanation.",
            isRetryable = true
          )
        }
      }
    }
  }

  fun confirmConsentAndExplain(
    extractionResult: DocumentExtractionResult?,
    language: String = ExplanationConstants.DEFAULT_EXPLANATION_LANGUAGE
  ) {
    val activeUid = resolveAndSyncActiveUid()
    if (activeUid == null) {
      _hasExplanationConsent.value = false
      _isConsentDialogVisible.value = false
      _uiState.value = ReportExplanationState.Error(
        message = "Please sign in to authorize AI report explanations.",
        isRetryable = false
      )
      return
    }
    consentStore.setExplanationConsentGranted(activeUid, true)
    _hasExplanationConsent.value = true
    _isConsentDialogVisible.value = false
    requestExplanation(
      extractionResult = extractionResult,
      language = ExplanationConstants.resolveSafeExplanationLanguage(language)
    )
  }

  fun dismissConsentDialog() {
    _isConsentDialogVisible.value = false
    if (_uiState.value is ReportExplanationState.WaitingForConsent) {
      _uiState.value = ReportExplanationState.Idle
    }
  }

  fun retryExplanation(
    extractionResult: DocumentExtractionResult?,
    language: String = ExplanationConstants.DEFAULT_EXPLANATION_LANGUAGE
  ) {
    if (boundExtractionResult === extractionResult && _uiState.value is ReportExplanationState.Success) {
      _uiState.value = ReportExplanationState.Idle
    }
    requestExplanation(
      extractionResult = extractionResult,
      language = ExplanationConstants.resolveSafeExplanationLanguage(language)
    )
  }

  /**
   * Clears in-memory explanation state and invalidates stored consent for the departing user
   * on sign-out or account deletion so neither the same session nor another user can reuse it.
   */
  fun clearSessionAndRevokeConsent(uid: String? = null) {
    val uidsToRevoke = listOfNotNull(
      normalizeUid(uid),
      boundUserUid,
      normalizeUid(currentUidProvider()),
      syncedExternalUid
    ).distinct()

    uidsToRevoke.forEach { targetUid ->
      consentStore.clearConsentForUser(targetUid)
    }
    _hasExplanationConsent.value = false
    boundUserUid = null
    syncedExternalUid = null
    clear()
  }

  fun clear() {
    activeJob?.cancel()
    activeJob = null
    boundExtractionResult = null
    boundLanguage = null
    _isConsentDialogVisible.value = false
    _uiState.value = ReportExplanationState.Idle
  }

  override fun onCleared() {
    super.onCleared()
    activeJob?.cancel()
  }
}
