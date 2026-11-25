package com.example.data.local

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.example.model.MedicalDocumentCategory
import com.example.model.PersistedMedicalReport
import com.example.model.ReportProcessingStatus
import com.example.model.RetentionDecision
import com.example.model.TestStatus

@Entity(
  tableName = "pending_report_deletions",
  indices = [Index(value = ["ownerUid"])]
)
data class PendingReportDeletionEntity(
  @PrimaryKey
  val reportId: String,
  val ownerUid: String,
  val title: String,
  val storageObjectPath: String,
  val requestedAtMillis: Long,
  val lastAttemptAtMillis: Long = 0L,
  val attemptCount: Int = 0,
  val status: String = STATUS_QUEUED
) {
  companion object {
    const val STATUS_QUEUED = "QUEUED"
    const val STATUS_RETRYING = "RETRYING"
  }
}

@Entity(
  tableName = "cached_medical_reports",
  indices = [Index(value = ["ownerUid", "uploadedAtMillis"])]
)
data class CachedMedicalReportEntity(
  @PrimaryKey
  val reportId: String,
  val ownerUid: String,
  val title: String,
  val selectedCategory: String,
  val extractedCategory: String,
  val patientLabel: String,
  val facilityOrProvider: String,
  val documentMimeType: String,
  val documentByteSize: Long,
  val pageCount: Int,
  val storageObjectPath: String,
  val processingStatus: String,
  val retentionDecision: String,
  val uploadedAtMillis: Long,
  val expiresAtMillis: Long?,
  val mainHighlight: String,
  val statusTag: String,
  val availableLanguagesCsv: String,
  val extractionJson: String?,
  val updatedAtMillis: Long
) {
  fun toDomain(): PersistedMedicalReport {
    val categoryEnum = MedicalDocumentCategory.entries.firstOrNull {
      it.name == extractedCategory
    } ?: MedicalDocumentCategory.UNCERTAIN
    val testStatus = TestStatus.entries.firstOrNull {
      it.name == statusTag
    } ?: TestStatus.INFORMATIONAL
    val langs = availableLanguagesCsv
      .split(',')
      .map { it.trim() }
      .filter { it.isNotEmpty() }

    return PersistedMedicalReport(
      reportId = reportId,
      ownerUid = ownerUid,
      title = title,
      selectedCategory = selectedCategory,
      extractedCategory = categoryEnum,
      patientLabel = patientLabel,
      facilityOrProvider = facilityOrProvider,
      documentMimeType = documentMimeType,
      documentByteSize = documentByteSize,
      pageCount = pageCount,
      storageObjectPath = storageObjectPath,
      processingStatus = ReportProcessingStatus.fromSerialized(processingStatus),
      retentionDecision = RetentionDecision.fromSerialized(retentionDecision),
      uploadedAtMillis = uploadedAtMillis,
      expiresAtMillis = expiresAtMillis,
      mainHighlight = mainHighlight,
      statusTag = testStatus,
      availableLanguages = langs,
      updatedAtMillis = updatedAtMillis
    )
  }

  companion object {
    fun fromDomain(
      report: PersistedMedicalReport,
      extractionJson: String?
    ): CachedMedicalReportEntity {
      return CachedMedicalReportEntity(
        reportId = report.reportId,
        ownerUid = report.ownerUid,
        title = report.title,
        selectedCategory = report.selectedCategory,
        extractedCategory = report.extractedCategory.name,
        patientLabel = report.patientLabel,
        facilityOrProvider = report.facilityOrProvider,
        documentMimeType = report.documentMimeType,
        documentByteSize = report.documentByteSize,
        pageCount = report.pageCount,
        storageObjectPath = report.storageObjectPath,
        processingStatus = report.processingStatus.serializedValue,
        retentionDecision = report.retentionDecision.serializedValue,
        uploadedAtMillis = report.uploadedAtMillis,
        expiresAtMillis = report.expiresAtMillis,
        mainHighlight = report.mainHighlight,
        statusTag = report.statusTag.name,
        availableLanguagesCsv = report.availableLanguages.joinToString(","),
        extractionJson = extractionJson,
        updatedAtMillis = report.updatedAtMillis
      )
    }
  }
}

@Entity(
  tableName = "cached_report_explanations",
  primaryKeys = ["reportId", "languageCode"],
  indices = [Index(value = ["ownerUid", "reportId"])]
)
data class CachedReportExplanationEntity(
  val reportId: String,
  val languageCode: String,
  val ownerUid: String,
  val expiresAtMillis: Long?,
  val explanationJson: String,
  val updatedAtMillis: Long
)
