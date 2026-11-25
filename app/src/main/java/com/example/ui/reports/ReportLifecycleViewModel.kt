package com.example.ui.reports

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.data.repository.FirebaseReportRepository
import com.example.domain.repository.ReportRepository
import com.example.model.DeletionOutcome
import com.example.model.DocumentExtractionResult
import com.example.model.PendingDeletionItem
import com.example.model.PersistedMedicalReport
import com.example.model.PersistedReportDetail
import com.example.model.ReportExplanationResult
import com.example.model.ReportPersistenceSyncStatus
import com.example.model.ReportRetentionPolicy
import com.example.model.RetentionDecision
import com.example.model.SelectedDocument
import com.example.model.SupportedExplanationLanguage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ActivePersistedReportSession(
  val report: PersistedMedicalReport? = null,
  val extractionResult: DocumentExtractionResult? = null,
  val explanationResult: ReportExplanationResult? = null,
  val isOpenedFromHistory: Boolean = false,
  val syncStatus: ReportPersistenceSyncStatus = ReportPersistenceSyncStatus.Idle
) {
  val requiresRetentionDecision: Boolean
    get() = report != null &&
      report.retentionDecision == RetentionDecision.PENDING_DECISION &&
      syncStatus !is ReportPersistenceSyncStatus.Deleting &&
      syncStatus !is ReportPersistenceSyncStatus.Deleted
}

data class ReportLifecycleUiState(
  val activeUid: String? = null,
  val reports: List<PersistedMedicalReport> = emptyList(),
  val pendingDeletions: List<PendingDeletionItem> = emptyList(),
  val activeSession: ActivePersistedReportSession = ActivePersistedReportSession(),
  val reopenedDetail: PersistedReportDetail? = null,
  val isLoadingDetail: Boolean = false,
  val isExitRetentionPromptVisible: Boolean = false,
  val statusBannerMessage: String? = null
)

class ReportLifecycleViewModel(
  application: Application,
  private val repository: ReportRepository = FirebaseReportRepository.createDefault(application)
) : AndroidViewModel(application) {

  companion object {
    private const val TAG = "NexzyLifecycleVM"

    fun provideFactory(application: Application): ViewModelProvider.Factory =
      object : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
          return ReportLifecycleViewModel(application) as T
        }
      }
  }

  private val _uiState = MutableStateFlow(ReportLifecycleUiState())
  val uiState: StateFlow<ReportLifecycleUiState> = _uiState.asStateFlow()

  private var reportsObserverJob: Job? = null
  private var pendingDeletionsObserverJob: Job? = null
  private var lastSavedExtractionRef: DocumentExtractionResult? = null
  private var lastSavedExplanationRef: ReportExplanationResult? = null

  fun syncUserSession(uid: String?) {
    val cleanUid = uid?.trim()?.takeIf { it.isNotEmpty() }
    val currentUid = _uiState.value.activeUid
    if (cleanUid == currentUid) return

    // If the departing user leaves an undecided report, enforce default-to-deletion immediately.
    val previousPendingReport = _uiState.value.activeSession.report
      ?.takeIf { it.retentionDecision == RetentionDecision.PENDING_DECISION }
    if (currentUid != null && previousPendingReport != null) {
      viewModelScope.launch {
        repository.deleteReport(currentUid, previousPendingReport.reportId)
      }
    }
    if (currentUid != null) {
      viewModelScope.launch {
        repository.clearLocalUserCache(currentUid)
      }
    }

    reportsObserverJob?.cancel()
    pendingDeletionsObserverJob?.cancel()
    lastSavedExtractionRef = null
    lastSavedExplanationRef = null

    if (cleanUid == null) {
      _uiState.value = ReportLifecycleUiState()
      return
    }

    _uiState.value = ReportLifecycleUiState(activeUid = cleanUid)

    reportsObserverJob = viewModelScope.launch {
      repository.observeReports(cleanUid).collect { list ->
        _uiState.update { state ->
          val refreshedActive = state.activeSession.report?.let { active ->
            list.firstOrNull { it.reportId == active.reportId } ?: active
          }
          state.copy(
            reports = list,
            activeSession = state.activeSession.copy(report = refreshedActive)
          )
        }
      }
    }

    pendingDeletionsObserverJob = viewModelScope.launch {
      repository.observePendingDeletions(cleanUid).collect { pending ->
        _uiState.update { it.copy(pendingDeletions = pending) }
      }
    }

    viewModelScope.launch {
      try {
        repository.reconcilePendingDeletions(cleanUid)
        repository.purgeExpiredReports(cleanUid)
        val synced = repository.fetchAllReports(cleanUid).getOrNull().orEmpty()
        // Any previously abandoned report still in PENDING_DECISION after app restart defaults to deletion.
        val activeId = _uiState.value.activeSession.report?.reportId
        synced
          .filter { it.retentionDecision == RetentionDecision.PENDING_DECISION && it.reportId != activeId }
          .forEach { abandoned ->
            repository.deleteReport(cleanUid, abandoned.reportId)
          }
      } catch (e: CancellationException) {
        throw e
      } catch (e: Exception) {
        Log.w(TAG, "Initial report reconciliation skipped: ${e.javaClass.simpleName}")
      }
    }
  }

  fun onExtractionCompleted(
    uid: String?,
    selectedDocument: SelectedDocument?,
    patientLabel: String,
    selectedCategory: String,
    extractionResult: DocumentExtractionResult,
    fileBytes: ByteArray? = null
  ) {
    val cleanUid = uid?.trim()?.takeIf { it.isNotEmpty() } ?: return
    if (lastSavedExtractionRef === extractionResult && _uiState.value.activeSession.report != null) {
      return
    }

    lastSavedExtractionRef = extractionResult
    lastSavedExplanationRef = null
    _uiState.update {
      it.copy(
        isExitRetentionPromptVisible = false,
        statusBannerMessage = null,
        activeSession = ActivePersistedReportSession(
          report = null,
          extractionResult = extractionResult,
          explanationResult = null,
          isOpenedFromHistory = false,
          syncStatus = ReportPersistenceSyncStatus.Saving
        )
      )
    }

    viewModelScope.launch {
      val result = repository.createProcessedReport(
        uid = cleanUid,
        selectedDocument = selectedDocument,
        fileBytes = fileBytes,
        patientLabel = patientLabel,
        selectedCategory = selectedCategory,
        extractionResult = extractionResult,
        initialExplanation = null
      )

      result.fold(
        onSuccess = { persisted ->
          _uiState.update { state ->
            state.copy(
              activeSession = state.activeSession.copy(
                report = persisted,
                syncStatus = ReportPersistenceSyncStatus.SavedPendingChoice
              )
            )
          }
        },
        onFailure = { err ->
          Log.w(TAG, "Report persistence deferred or failed: ${err.javaClass.simpleName}")
          _uiState.update { state ->
            state.copy(
              activeSession = state.activeSession.copy(
                syncStatus = ReportPersistenceSyncStatus.Error(
                  "Could not save report to your cloud account. Check connection and try again."
                )
              )
            )
          }
        }
      )
    }
  }

  fun onExplanationCompleted(
    uid: String?,
    explanationResult: ReportExplanationResult
  ) {
    val cleanUid = uid?.trim()?.takeIf { it.isNotEmpty() } ?: return
    val activeReport = _uiState.value.activeSession.report ?: return
    if (lastSavedExplanationRef === explanationResult) return

    lastSavedExplanationRef = explanationResult
    viewModelScope.launch {
      val saved = repository.saveReportExplanation(
        uid = cleanUid,
        reportId = activeReport.reportId,
        explanation = explanationResult
      )
      saved.fold(
        onSuccess = { updated ->
          _uiState.update { state ->
            state.copy(
              activeSession = state.activeSession.copy(
                report = updated,
                explanationResult = explanationResult
              )
            )
          }
        },
        onFailure = { err ->
          Log.w(TAG, "Explanation save skipped or deferred: ${err.javaClass.simpleName}")
        }
      )
    }
  }

  fun keepActiveReport() {
    val cleanUid = _uiState.value.activeUid ?: return
    val activeReport = _uiState.value.activeSession.report ?: return

    viewModelScope.launch {
      val result = repository.updateRetentionDecision(
        uid = cleanUid,
        reportId = activeReport.reportId,
        decision = RetentionDecision.KEEP_SEVEN_DAYS
      )
      result.fold(
        onSuccess = { updated ->
          val expiryText = updated.expiresAtMillis?.let { ReportRetentionPolicy.formatShortDate(it) }
          _uiState.update { state ->
            state.copy(
              isExitRetentionPromptVisible = false,
              statusBannerMessage = if (expiryText != null) {
                "Report kept securely until $expiryText."
              } else {
                "Report kept for 7 days from upload."
              },
              activeSession = state.activeSession.copy(
                report = updated,
                syncStatus = ReportPersistenceSyncStatus.KeptForSevenDays
              )
            )
          }
        },
        onFailure = { err ->
          Log.w(TAG, "Keep report update deferred: ${err.javaClass.simpleName}")
          _uiState.update { state ->
            val updatedLocal = state.activeSession.report?.copy(
              retentionDecision = RetentionDecision.KEEP_SEVEN_DAYS
            )
            state.copy(
              isExitRetentionPromptVisible = false,
              statusBannerMessage = "Saved preference locally; will sync when connected.",
              activeSession = state.activeSession.copy(
                report = updatedLocal,
                syncStatus = ReportPersistenceSyncStatus.KeptForSevenDays
              )
            )
          }
        }
      )
    }
  }

  fun deleteActiveReport(
    localUriToRelease: String? = null,
    onCompleted: () -> Unit = {}
  ) {
    val cleanUid = _uiState.value.activeUid
    val activeReport = _uiState.value.activeSession.report
    if (cleanUid.isNullOrBlank() || activeReport == null) {
      _uiState.update {
        it.copy(
          isExitRetentionPromptVisible = false,
          activeSession = ActivePersistedReportSession()
        )
      }
      onCompleted()
      return
    }

    _uiState.update {
      it.copy(
        isExitRetentionPromptVisible = false,
        activeSession = it.activeSession.copy(syncStatus = ReportPersistenceSyncStatus.Deleting)
      )
    }

    viewModelScope.launch {
      val outcome = repository.deleteReport(
        uid = cleanUid,
        reportId = activeReport.reportId,
        localUriToRelease = localUriToRelease
      )
      applyDeletionOutcome(outcome, clearActiveSession = true)
      onCompleted()
    }
  }

  fun deleteReportById(
    reportId: String,
    localUriToRelease: String? = null
  ) {
    val cleanUid = _uiState.value.activeUid ?: return
    val isDeletingActive = _uiState.value.activeSession.report?.reportId == reportId

    viewModelScope.launch {
      val outcome = repository.deleteReport(
        uid = cleanUid,
        reportId = reportId,
        localUriToRelease = localUriToRelease
      )
      applyDeletionOutcome(outcome, clearActiveSession = isDeletingActive)
    }
  }

  /**
   * Handles leaving a report session.
   * - If the user already chose Keep (7 Days) or no persisted report exists, allows immediate exit.
   * - If the user has not chosen Keep yet ([RetentionDecision.PENDING_DECISION]), displays the
   *   Keep/Delete confirmation dialog (which defaults to deletion if dismissed without keeping).
   */
  fun requestExitReportSession(onImmediateLeaveAllowed: () -> Unit) {
    if (_uiState.value.activeSession.requiresRetentionDecision) {
      _uiState.update { it.copy(isExitRetentionPromptVisible = true) }
    } else {
      onImmediateLeaveAllowed()
    }
  }

  fun dismissExitPromptAndDeleteByDefault(
    localUriToRelease: String? = null,
    onLeaveAfterDelete: () -> Unit
  ) {
    deleteActiveReport(localUriToRelease = localUriToRelease, onCompleted = onLeaveAfterDelete)
  }

  fun openPersistedReport(
    reportId: String,
    preferredLanguageCode: String,
    onLoaded: (PersistedReportDetail) -> Unit,
    onUnavailable: (String) -> Unit
  ) {
    val cleanUid = _uiState.value.activeUid
    if (cleanUid.isNullOrBlank()) {
      onUnavailable("Please sign in to open saved reports.")
      return
    }

    _uiState.update { it.copy(isLoadingDetail = true) }

    viewModelScope.launch {
      val result = repository.getReportById(cleanUid, reportId)
      result.fold(
        onSuccess = { detail ->
          if (detail == null || detail.extractionResult == null) {
            _uiState.update { it.copy(isLoadingDetail = false) }
            onUnavailable("This report is no longer available or has expired after 7 days.")
          } else {
            val langKey = SupportedExplanationLanguage.fromCodeOrName(preferredLanguageCode)?.code
            val matchedExplanation = langKey?.let { detail.explanationsByLanguage[it] }
              ?: detail.explanationsByLanguage.values.firstOrNull()

            lastSavedExtractionRef = detail.extractionResult
            lastSavedExplanationRef = matchedExplanation

            _uiState.update { state ->
              state.copy(
                isLoadingDetail = false,
                isExitRetentionPromptVisible = false,
                reopenedDetail = detail,
                activeSession = ActivePersistedReportSession(
                  report = detail.report,
                  extractionResult = detail.extractionResult,
                  explanationResult = matchedExplanation,
                  isOpenedFromHistory = true,
                  syncStatus = if (detail.report.retentionDecision == RetentionDecision.KEEP_SEVEN_DAYS) {
                    ReportPersistenceSyncStatus.KeptForSevenDays
                  } else {
                    ReportPersistenceSyncStatus.SavedPendingChoice
                  }
                )
              )
            }
            onLoaded(detail)
          }
        },
        onFailure = { err ->
          Log.w(TAG, "Unable to open persisted report: ${err.javaClass.simpleName}")
          _uiState.update { it.copy(isLoadingDetail = false) }
          onUnavailable("Could not load this saved report right now.")
        }
      )
    }
  }

  fun retryPendingDeletions() {
    val cleanUid = _uiState.value.activeUid ?: return
    viewModelScope.launch {
      val remaining = repository.reconcilePendingDeletions(cleanUid)
      _uiState.update { state ->
        state.copy(
          statusBannerMessage = if (remaining == 0) {
            "Pending report deletions completed."
          } else {
            "$remaining report deletion(s) still queued — waiting for internet connection."
          }
        )
      }
    }
  }

  fun clearActiveSessionState() {
    lastSavedExtractionRef = null
    lastSavedExplanationRef = null
    _uiState.update {
      it.copy(
        isExitRetentionPromptVisible = false,
        reopenedDetail = null,
        activeSession = ActivePersistedReportSession()
      )
    }
  }

  fun dismissStatusBanner() {
    _uiState.update { it.copy(statusBannerMessage = null) }
  }

  private fun applyDeletionOutcome(
    outcome: DeletionOutcome,
    clearActiveSession: Boolean
  ) {
    _uiState.update { state ->
      val nextSession = if (clearActiveSession) {
        lastSavedExtractionRef = null
        lastSavedExplanationRef = null
        ActivePersistedReportSession(syncStatus = ReportPersistenceSyncStatus.Deleted)
      } else {
        state.activeSession
      }
      val msg = when (outcome) {
        is DeletionOutcome.DeletedRemotely -> "Medical report deleted permanently."
        is DeletionOutcome.QueuedOffline -> outcome.reason
        is DeletionOutcome.Failed -> outcome.message
      }
      state.copy(
        isExitRetentionPromptVisible = false,
        statusBannerMessage = msg,
        activeSession = nextSession
      )
    }
  }
}
