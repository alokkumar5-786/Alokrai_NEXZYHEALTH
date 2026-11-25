package com.example.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface ReportLocalDao {

  @Query(
    """
    SELECT * FROM cached_medical_reports
    WHERE ownerUid = :uid
      AND reportId NOT IN (
        SELECT reportId FROM pending_report_deletions WHERE ownerUid = :uid
      )
    ORDER BY uploadedAtMillis DESC
    """
  )
  fun observeVisibleReportsForUser(uid: String): Flow<List<CachedMedicalReportEntity>>

  @Query(
    """
    SELECT * FROM cached_medical_reports
    WHERE ownerUid = :uid
      AND reportId NOT IN (
        SELECT reportId FROM pending_report_deletions WHERE ownerUid = :uid
      )
    ORDER BY uploadedAtMillis DESC
    """
  )
  suspend fun getVisibleReportsForUser(uid: String): List<CachedMedicalReportEntity>

  @Query("SELECT * FROM cached_medical_reports WHERE ownerUid = :uid AND reportId = :reportId LIMIT 1")
  suspend fun getReportById(uid: String, reportId: String): CachedMedicalReportEntity?

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun upsertReport(report: CachedMedicalReportEntity)

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun upsertReports(reports: List<CachedMedicalReportEntity>)

  @Query("DELETE FROM cached_medical_reports WHERE ownerUid = :uid AND reportId = :reportId")
  suspend fun deleteCachedReport(uid: String, reportId: String)

  @Query("DELETE FROM cached_medical_reports WHERE ownerUid = :uid")
  suspend fun clearCachedReportsForUser(uid: String)

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun upsertExplanation(explanation: CachedReportExplanationEntity)

  @Query("SELECT * FROM cached_report_explanations WHERE ownerUid = :uid AND reportId = :reportId")
  suspend fun getExplanationsForReport(uid: String, reportId: String): List<CachedReportExplanationEntity>

  @Query("DELETE FROM cached_report_explanations WHERE ownerUid = :uid AND reportId = :reportId")
  suspend fun deleteCachedExplanationsForReport(uid: String, reportId: String)

  @Query("DELETE FROM cached_report_explanations WHERE ownerUid = :uid")
  suspend fun clearCachedExplanationsForUser(uid: String)

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun enqueuePendingDeletion(item: PendingReportDeletionEntity)

  @Query("SELECT * FROM pending_report_deletions WHERE ownerUid = :uid ORDER BY requestedAtMillis ASC")
  fun observePendingDeletionsForUser(uid: String): Flow<List<PendingReportDeletionEntity>>

  @Query("SELECT * FROM pending_report_deletions WHERE ownerUid = :uid ORDER BY requestedAtMillis ASC")
  suspend fun getPendingDeletionsForUser(uid: String): List<PendingReportDeletionEntity>

  @Query("SELECT reportId FROM pending_report_deletions WHERE ownerUid = :uid")
  suspend fun getPendingDeletionIdsForUser(uid: String): List<String>

  @Query("SELECT EXISTS(SELECT 1 FROM pending_report_deletions WHERE ownerUid = :uid AND reportId = :reportId)")
  suspend fun isPendingDeletion(uid: String, reportId: String): Boolean

  @Query("DELETE FROM pending_report_deletions WHERE ownerUid = :uid AND reportId = :reportId")
  suspend fun removePendingDeletion(uid: String, reportId: String)

  @Query(
    """
    UPDATE pending_report_deletions
    SET attemptCount = attemptCount + 1,
        lastAttemptAtMillis = :attemptTimeMillis,
        status = :status
    WHERE ownerUid = :uid AND reportId = :reportId
    """
  )
  suspend fun markDeletionAttemptFailed(
    uid: String,
    reportId: String,
    attemptTimeMillis: Long,
    status: String = PendingReportDeletionEntity.STATUS_RETRYING
  )

  @Query(
    """
    UPDATE cached_medical_reports
    SET retentionDecision = :decision,
        updatedAtMillis = :updatedAtMillis
    WHERE ownerUid = :uid AND reportId = :reportId
    """
  )
  suspend fun updateCachedRetentionDecision(
    uid: String,
    reportId: String,
    decision: String,
    updatedAtMillis: Long
  )

  @Query(
    """
    UPDATE cached_medical_reports
    SET processingStatus = :status,
        updatedAtMillis = :updatedAtMillis
    WHERE ownerUid = :uid AND reportId = :reportId
    """
  )
  suspend fun updateCachedProcessingStatus(
    uid: String,
    reportId: String,
    status: String,
    updatedAtMillis: Long
  )
}
