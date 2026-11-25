package com.example.model

import com.google.firebase.Timestamp
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

enum class RetentionDecision(val serializedValue: String) {
  PENDING_DECISION("PENDING_DECISION"),
  KEEP_SEVEN_DAYS("KEEP_SEVEN_DAYS"),
  DELETE_REQUESTED("DELETE_REQUESTED");

  companion object {
    fun fromSerialized(value: String?): RetentionDecision {
      return entries.firstOrNull { it.serializedValue == value } ?: PENDING_DECISION
    }
  }
}

enum class DeletionSyncState(val serializedValue: String) {
  NONE("NONE"),
  PENDING_OFFLINE_DELETION("PENDING_OFFLINE_DELETION"),
  DELETING("DELETING"),
  DELETED("DELETED");

  companion object {
    fun fromSerialized(value: String?): DeletionSyncState {
      return entries.firstOrNull { it.serializedValue == value } ?: NONE
    }
  }
}

enum class ReportProcessingStatus(val serializedValue: String) {
  EXTRACTED("EXTRACTED"),
  UNIDENTIFIED("UNIDENTIFIED"),
  EXPLAINED("EXPLAINED"),
  FAILED("FAILED");

  companion object {
    fun fromSerialized(value: String?): ReportProcessingStatus {
      return entries.firstOrNull { it.serializedValue == value } ?: EXTRACTED
    }
  }
}

/**
 * Enforces the approved 7-day retention window anchored strictly to the original
 * server upload timestamp ([uploadedAt]). Reopening, translating, or saving follow-up
 * explanations never extends or resets this deadline.
 */
object ReportRetentionPolicy {
  const val RETENTION_DAYS: Long = 7L
  const val SECONDS_PER_DAY: Long = 24L * 60L * 60L
  const val RETENTION_SECONDS: Long = RETENTION_DAYS * SECONDS_PER_DAY
  const val RETENTION_MILLIS: Long = RETENTION_SECONDS * 1000L

  fun calculateExpiresAtTimestamp(uploadedAt: Timestamp): Timestamp {
    return Timestamp(uploadedAt.seconds + RETENTION_SECONDS, uploadedAt.nanoseconds)
  }

  fun calculateExpiresAtMillis(uploadedAtMillis: Long): Long {
    return uploadedAtMillis + RETENTION_MILLIS
  }

  fun isExpired(expiresAtMillis: Long?, nowMillis: Long = System.currentTimeMillis()): Boolean {
    return expiresAtMillis != null && nowMillis >= expiresAtMillis
  }

  fun formatShortDate(epochMillis: Long): String {
    val formatter = SimpleDateFormat("dd MMM yyyy", Locale.getDefault())
    return formatter.format(Date(epochMillis))
  }

  fun formatExpiryDateTime(epochMillis: Long): String {
    val formatter = SimpleDateFormat("dd MMM yyyy, h:mm a", Locale.getDefault())
    return formatter.format(Date(epochMillis))
  }

  fun daysRemaining(expiresAtMillis: Long?, nowMillis: Long = System.currentTimeMillis()): Int {
    if (expiresAtMillis == null) return 0
    val remainingMillis = (expiresAtMillis - nowMillis).coerceAtLeast(0L)
    return ((remainingMillis + (SECONDS_PER_DAY * 1000L - 1L)) / (SECONDS_PER_DAY * 1000L)).toInt()
  }

  fun storageObjectPathFor(uid: String, reportId: String): String {
    return "users/$uid/reports/$reportId/original"
  }
}

data class PersistedMedicalReport(
  val reportId: String,
  val ownerUid: String,
  val title: String,
  val selectedCategory: String,
  val extractedCategory: MedicalDocumentCategory,
  val patientLabel: String,
  val facilityOrProvider: String,
  val documentMimeType: String,
  val documentByteSize: Long,
  val pageCount: Int,
  val storageObjectPath: String,
  val processingStatus: ReportProcessingStatus,
  val retentionDecision: RetentionDecision,
  val uploadedAtMillis: Long,
  val expiresAtMillis: Long?,
  val mainHighlight: String,
  val statusTag: TestStatus,
  val availableLanguages: List<String> = emptyList(),
  val updatedAtMillis: Long = uploadedAtMillis
) {
  fun toHistoryItem(): ReportHistoryItem {
    val expiryText = expiresAtMillis?.let { ReportRetentionPolicy.formatShortDate(it) }
    val retentionLabel = when {
      retentionDecision == RetentionDecision.DELETE_REQUESTED -> "Deletion in progress"
      expiresAtMillis != null -> "Expires $expiryText"
      else -> "7-day retention"
    }
    return ReportHistoryItem(
      id = reportId,
      title = title,
      date = ReportRetentionPolicy.formatShortDate(uploadedAtMillis),
      labName = facilityOrProvider,
      patientName = patientLabel,
      category = selectedCategory,
      mainHighlight = mainHighlight,
      status = statusTag,
      isDemo = false,
      expiresAtDisplay = expiryText,
      retentionStatusLabel = retentionLabel,
      isPendingDeletion = false,
      canDelete = true
    )
  }
}

data class PersistedReportDetail(
  val report: PersistedMedicalReport,
  val extractionResult: DocumentExtractionResult?,
  val explanationsByLanguage: Map<String, ReportExplanationResult> = emptyMap()
)

data class PendingDeletionItem(
  val reportId: String,
  val ownerUid: String,
  val title: String,
  val requestedAtMillis: Long,
  val attemptCount: Int,
  val status: String
)

sealed class DeletionOutcome {
  object DeletedRemotely : DeletionOutcome()
  data class QueuedOffline(val reason: String) : DeletionOutcome()
  data class Failed(val message: String) : DeletionOutcome()
}

sealed class ReportPersistenceSyncStatus {
  object Idle : ReportPersistenceSyncStatus()
  object Saving : ReportPersistenceSyncStatus()
  object SavedPendingChoice : ReportPersistenceSyncStatus()
  object KeptForSevenDays : ReportPersistenceSyncStatus()
  object Deleting : ReportPersistenceSyncStatus()
  data class QueuedOfflineDeletion(val message: String) : ReportPersistenceSyncStatus()
  object Deleted : ReportPersistenceSyncStatus()
  data class Error(val message: String) : ReportPersistenceSyncStatus()
}
