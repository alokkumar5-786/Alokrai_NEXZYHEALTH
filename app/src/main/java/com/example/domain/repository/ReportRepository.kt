package com.example.domain.repository

import com.example.model.DeletionOutcome
import com.example.model.DocumentExtractionResult
import com.example.model.PendingDeletionItem
import com.example.model.PersistedMedicalReport
import com.example.model.PersistedReportDetail
import com.example.model.ReportExplanationResult
import com.example.model.ReportProcessingStatus
import com.example.model.RetentionDecision
import com.example.model.SelectedDocument
import kotlinx.coroutines.flow.Flow

interface ReportRepository {
  fun observeReports(uid: String): Flow<List<PersistedMedicalReport>>

  fun observePendingDeletions(uid: String): Flow<List<PendingDeletionItem>>

  suspend fun createProcessedReport(
    uid: String,
    selectedDocument: SelectedDocument?,
    fileBytes: ByteArray? = null,
    patientLabel: String,
    selectedCategory: String,
    extractionResult: DocumentExtractionResult,
    initialExplanation: ReportExplanationResult? = null
  ): Result<PersistedMedicalReport>

  suspend fun saveReportExplanation(
    uid: String,
    reportId: String,
    explanation: ReportExplanationResult
  ): Result<PersistedMedicalReport>

  suspend fun updateRetentionDecision(
    uid: String,
    reportId: String,
    decision: RetentionDecision
  ): Result<PersistedMedicalReport>

  suspend fun getReportById(
    uid: String,
    reportId: String
  ): Result<PersistedReportDetail?>

  suspend fun fetchAllReports(uid: String): Result<List<PersistedMedicalReport>>

  suspend fun updateProcessingStatus(
    uid: String,
    reportId: String,
    status: ReportProcessingStatus
  ): Result<Unit>

  suspend fun deleteReport(
    uid: String,
    reportId: String,
    localUriToRelease: String? = null
  ): DeletionOutcome

  suspend fun reconcilePendingDeletions(uid: String): Int

  suspend fun purgeExpiredReports(uid: String): Int

  suspend fun clearLocalUserCache(uid: String)
}
