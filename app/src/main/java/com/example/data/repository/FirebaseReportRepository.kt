package com.example.data.repository

import android.content.Context
import android.net.Uri
import android.util.Log
import com.example.data.local.CachedMedicalReportEntity
import com.example.data.local.CachedReportExplanationEntity
import com.example.data.local.NexzyLocalDatabase
import com.example.data.local.PendingReportDeletionEntity
import com.example.data.local.ReportLocalDao
import com.example.data.serialization.ReportPayloadSerializer
import com.example.data.worker.PendingDeletionSyncWorker
import com.example.domain.engine.SafetyFindingEvaluator
import com.example.domain.repository.ReportRepository
import com.example.firebase.FirebaseManager
import com.example.model.DeletionOutcome
import com.example.model.DocumentExtractionResult
import com.example.model.MedicalDocumentCategory
import com.example.model.PendingDeletionItem
import com.example.model.PersistedMedicalReport
import com.example.model.PersistedReportDetail
import com.example.model.ReportExplanationResult
import com.example.model.ReportProcessingStatus
import com.example.model.ReportRetentionPolicy
import com.example.model.RetentionDecision
import com.example.model.SafetyBucket
import com.example.model.SelectedDocument
import com.example.model.SupportedExplanationLanguage
import com.example.model.TestStatus
import com.example.model.UniversalMedicalReport
import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.MetadataChanges
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.Source
import com.google.firebase.storage.FirebaseStorage
import com.google.firebase.storage.StorageException
import com.google.firebase.storage.StorageMetadata
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.IOException
import java.util.Locale

data class RemoteReportPayload(
  val report: PersistedMedicalReport,
  val extractionJson: String?,
  val explanationsJsonByLang: Map<String, String> = emptyMap()
)

/**
 * Remote persistence boundary separating Firebase Firestore + Storage operations from
 * local Room caching and offline deletion queue management.
 */
interface RemoteReportDataSource {
  fun observeUserReports(uid: String): Flow<List<PersistedMedicalReport>>

  suspend fun createReport(
    uid: String,
    draft: PersistedMedicalReport,
    fileBytes: ByteArray,
    extractionJson: String,
    initialLanguageCode: String?,
    initialExplanationJson: String?
  ): PersistedMedicalReport

  suspend fun saveExplanation(
    uid: String,
    reportId: String,
    languageCode: String,
    explanationJson: String
  ): PersistedMedicalReport

  suspend fun updateRetentionDecision(
    uid: String,
    reportId: String,
    decision: RetentionDecision
  ): PersistedMedicalReport

  suspend fun updateProcessingStatus(
    uid: String,
    reportId: String,
    status: ReportProcessingStatus
  )

  suspend fun fetchAllReports(uid: String): List<PersistedMedicalReport>

  suspend fun fetchReportDetail(uid: String, reportId: String): RemoteReportPayload?

  suspend fun deleteReportCascade(uid: String, reportId: String, storageObjectPath: String)
}

class FirebaseRemoteReportDataSource(
  private val firestore: FirebaseFirestore,
  private val storage: FirebaseStorage
) : RemoteReportDataSource {

  companion object {
    private const val TAG = "NexzyRemoteReports"
    private const val COLLECTION_USERS = "users"
    private const val COLLECTION_REPORTS = "reports"
    private const val SUBCOLLECTION_EXTRACTED = "extracted"
    private const val SUBCOLLECTION_EXPLANATIONS = "explanations"
    private const val SUBCOLLECTION_QA = "qa"
    private const val DOC_EXTRACTED_CURRENT = "current"
  }

  private fun userReportsRef(uid: String) =
    firestore.collection(COLLECTION_USERS).document(uid).collection(COLLECTION_REPORTS)

  override fun observeUserReports(uid: String): Flow<List<PersistedMedicalReport>> = callbackFlow {
    val registration = userReportsRef(uid)
      .orderBy("uploadedAt", Query.Direction.DESCENDING)
      .addSnapshotListener(MetadataChanges.INCLUDE) { snapshot, error ->
        if (error != null) {
          Log.w(TAG, "Remote report snapshot listener error: ${error.javaClass.simpleName}")
          return@addSnapshotListener
        }
        if (snapshot != null && !snapshot.metadata.isFromCache) {
          val parsed = snapshot.documents.mapNotNull { parseReportDocument(it, uid) }
          trySend(parsed)
        }
      }
    awaitClose { registration.remove() }
  }

  override suspend fun createReport(
    uid: String,
    draft: PersistedMedicalReport,
    fileBytes: ByteArray,
    extractionJson: String,
    initialLanguageCode: String?,
    initialExplanationJson: String?
  ): PersistedMedicalReport {
    val reportDocRef = userReportsRef(uid).document()
    val reportId = reportDocRef.id
    val storageObjectPath = ReportRetentionPolicy.storageObjectPathFor(uid, reportId)

    val initialDoc = hashMapOf<String, Any?>(
      "reportId" to reportId,
      "ownerUid" to uid,
      "title" to draft.title,
      "selectedCategory" to draft.selectedCategory,
      "extractedCategory" to draft.extractedCategory.name,
      "patientLabel" to draft.patientLabel,
      "facilityOrProvider" to draft.facilityOrProvider,
      "documentMimeType" to draft.documentMimeType,
      "documentByteSize" to draft.documentByteSize,
      "pageCount" to draft.pageCount,
      "storageObjectPath" to storageObjectPath,
      "processingStatus" to draft.processingStatus.serializedValue,
      "retentionDecision" to RetentionDecision.PENDING_DECISION.serializedValue,
      "mainHighlight" to draft.mainHighlight,
      "statusTag" to draft.statusTag.name,
      "availableLanguages" to emptyList<String>(),
      "createdAt" to FieldValue.serverTimestamp(),
      "uploadedAt" to FieldValue.serverTimestamp(),
      "updatedAt" to FieldValue.serverTimestamp(),
      "expiresAt" to null
    )

    // Step 1: Create parent metadata document with server timestamps
    reportDocRef.set(initialDoc).awaitTask()

    // Step 2: Upload raw binary to private Storage path; rollback parent if upload fails
    try {
      val metadata = StorageMetadata.Builder()
        .setContentType(draft.documentMimeType)
        .setCustomMetadata("ownerUid", uid)
        .setCustomMetadata("reportId", reportId)
        .build()

      storage.reference.child(storageObjectPath)
        .putBytes(fileBytes, metadata)
        .awaitTask()
    } catch (e: Exception) {
      Log.w(TAG, "Storage binary upload failed; cleaning up metadata: ${e.javaClass.simpleName}")
      try {
        reportDocRef.delete().awaitTask()
      } catch (_: Exception) {
      }
      throw e
    }

    // Step 3: Resolve server-authoritative uploadedAt and compute immutable 7-day expiresAt once
    val serverSnapshot = try {
      reportDocRef.get(Source.SERVER).awaitTask()
    } catch (_: Exception) {
      reportDocRef.get(Source.DEFAULT).awaitTask()
    }
    val uploadedTimestamp = serverSnapshot.getTimestamp("uploadedAt") ?: Timestamp.now()
    val canonicalExpiresAt = ReportRetentionPolicy.calculateExpiresAtTimestamp(uploadedTimestamp)

    val languages = if (!initialLanguageCode.isNullOrBlank() && !initialExplanationJson.isNullOrBlank()) {
      listOf(initialLanguageCode)
    } else {
      emptyList()
    }
    val finalStatus = if (languages.isNotEmpty()) {
      ReportProcessingStatus.EXPLAINED
    } else {
      draft.processingStatus
    }

    reportDocRef.update(
      mapOf(
        "expiresAt" to canonicalExpiresAt,
        "processingStatus" to finalStatus.serializedValue,
        "availableLanguages" to languages,
        "updatedAt" to FieldValue.serverTimestamp()
      )
    ).awaitTask()

    // Step 4: Save structured extraction subdocument inheriting parent's expiresAt
    reportDocRef.collection(SUBCOLLECTION_EXTRACTED)
      .document(DOC_EXTRACTED_CURRENT)
      .set(
        mapOf(
          "ownerUid" to uid,
          "reportId" to reportId,
          "payloadJson" to extractionJson,
          "expiresAt" to canonicalExpiresAt,
          "updatedAt" to FieldValue.serverTimestamp()
        )
      )
      .awaitTask()

    // Step 5: Save optional initial explanation inheriting parent's exact expiresAt
    if (!initialLanguageCode.isNullOrBlank() && !initialExplanationJson.isNullOrBlank()) {
      reportDocRef.collection(SUBCOLLECTION_EXPLANATIONS)
        .document(initialLanguageCode)
        .set(
          mapOf(
            "ownerUid" to uid,
            "reportId" to reportId,
            "languageCode" to initialLanguageCode,
            "payloadJson" to initialExplanationJson,
            "expiresAt" to canonicalExpiresAt,
            "updatedAt" to FieldValue.serverTimestamp()
          )
        )
        .awaitTask()
    }

    val uploadedMillis = uploadedTimestamp.toDate().time
    val expiresMillis = canonicalExpiresAt.toDate().time
    return draft.copy(
      reportId = reportId,
      ownerUid = uid,
      storageObjectPath = storageObjectPath,
      processingStatus = finalStatus,
      retentionDecision = RetentionDecision.PENDING_DECISION,
      uploadedAtMillis = uploadedMillis,
      expiresAtMillis = expiresMillis,
      availableLanguages = languages,
      updatedAtMillis = uploadedMillis
    )
  }

  override suspend fun saveExplanation(
    uid: String,
    reportId: String,
    languageCode: String,
    explanationJson: String
  ): PersistedMedicalReport {
    val reportDocRef = userReportsRef(uid).document(reportId)
    val parentSnap = try {
      reportDocRef.get(Source.SERVER).awaitTask()
    } catch (_: Exception) {
      reportDocRef.get(Source.DEFAULT).awaitTask()
    }
    if (!parentSnap.exists()) {
      throw IllegalStateException("Parent medical report no longer exists.")
    }

    val parentReport = parseReportDocument(parentSnap, uid)
      ?: throw IllegalStateException("Parent medical report metadata is invalid.")
    val parentExpiresAt = parentSnap.getTimestamp("expiresAt")
      ?: ReportRetentionPolicy.calculateExpiresAtTimestamp(
        parentSnap.getTimestamp("uploadedAt") ?: Timestamp.now()
      )

    if (ReportRetentionPolicy.isExpired(parentExpiresAt.toDate().time)) {
      throw IllegalStateException("Report retention period has already expired.")
    }

    reportDocRef.collection(SUBCOLLECTION_EXPLANATIONS)
      .document(languageCode)
      .set(
        mapOf(
          "ownerUid" to uid,
          "reportId" to reportId,
          "languageCode" to languageCode,
          "payloadJson" to explanationJson,
          "expiresAt" to parentExpiresAt,
          "updatedAt" to FieldValue.serverTimestamp()
        )
      )
      .awaitTask()

    val updatedLangs = (parentReport.availableLanguages + languageCode).distinct()
    reportDocRef.update(
      mapOf(
        "processingStatus" to ReportProcessingStatus.EXPLAINED.serializedValue,
        "availableLanguages" to updatedLangs,
        "updatedAt" to FieldValue.serverTimestamp()
      )
    ).awaitTask()

    return parentReport.copy(
      processingStatus = ReportProcessingStatus.EXPLAINED,
      availableLanguages = updatedLangs,
      updatedAtMillis = System.currentTimeMillis()
    )
  }

  override suspend fun updateRetentionDecision(
    uid: String,
    reportId: String,
    decision: RetentionDecision
  ): PersistedMedicalReport {
    val reportDocRef = userReportsRef(uid).document(reportId)
    // Intentionally never modifies uploadedAt or expiresAt
    reportDocRef.update(
      mapOf(
        "retentionDecision" to decision.serializedValue,
        "updatedAt" to FieldValue.serverTimestamp()
      )
    ).awaitTask()

    val updatedSnap = reportDocRef.get(Source.DEFAULT).awaitTask()
    return parseReportDocument(updatedSnap, uid)
      ?: throw IllegalStateException("Updated report record could not be parsed.")
  }

  override suspend fun updateProcessingStatus(
    uid: String,
    reportId: String,
    status: ReportProcessingStatus
  ) {
    userReportsRef(uid).document(reportId)
      .update(
        mapOf(
          "processingStatus" to status.serializedValue,
          "updatedAt" to FieldValue.serverTimestamp()
        )
      )
      .awaitTask()
  }

  override suspend fun fetchAllReports(uid: String): List<PersistedMedicalReport> {
    val querySnapshot = userReportsRef(uid)
      .orderBy("uploadedAt", Query.Direction.DESCENDING)
      .get(Source.SERVER)
      .awaitTask()
    return querySnapshot.documents.mapNotNull { parseReportDocument(it, uid) }
  }

  override suspend fun fetchReportDetail(uid: String, reportId: String): RemoteReportPayload? {
    val reportDocRef = userReportsRef(uid).document(reportId)
    val parentSnap = try {
      reportDocRef.get(Source.SERVER).awaitTask()
    } catch (_: Exception) {
      reportDocRef.get(Source.DEFAULT).awaitTask()
    }
    if (!parentSnap.exists()) return null

    val parsed = parseReportDocument(parentSnap, uid) ?: return null
    val extractedSnap = reportDocRef.collection(SUBCOLLECTION_EXTRACTED)
      .document(DOC_EXTRACTED_CURRENT)
      .get(Source.DEFAULT)
      .awaitTask()
    val extractionJson = if (extractedSnap.exists()) {
      extractedSnap.getString("payloadJson")
    } else {
      null
    }

    val explanationsSnap = reportDocRef.collection(SUBCOLLECTION_EXPLANATIONS)
      .get(Source.DEFAULT)
      .awaitTask()
    val explanationsMap = linkedMapOf<String, String>()
    explanationsSnap.documents.forEach { doc ->
      val langCode = doc.getString("languageCode") ?: doc.id
      val payload = doc.getString("payloadJson")
      if (!payload.isNullOrBlank()) {
        explanationsMap[langCode] = payload
      }
    }

    return RemoteReportPayload(
      report = parsed,
      extractionJson = extractionJson,
      explanationsJsonByLang = explanationsMap
    )
  }

  override suspend fun deleteReportCascade(
    uid: String,
    reportId: String,
    storageObjectPath: String
  ) {
    val reportDocRef = userReportsRef(uid).document(reportId)

    // 1. Delete subcollection documents first (idempotent)
    deleteSubcollectionDocuments(reportDocRef.collection(SUBCOLLECTION_EXPLANATIONS))
    deleteSubcollectionDocuments(reportDocRef.collection(SUBCOLLECTION_EXTRACTED))
    deleteSubcollectionDocuments(reportDocRef.collection(SUBCOLLECTION_QA))

    // 2. Delete original Storage binary (treat object-not-found as idempotent success)
    val resolvedPath = storageObjectPath.ifBlank { ReportRetentionPolicy.storageObjectPathFor(uid, reportId) }
    try {
      storage.reference.child(resolvedPath).delete().awaitTask()
    } catch (e: StorageException) {
      if (e.errorCode != StorageException.ERROR_OBJECT_NOT_FOUND) {
        throw e
      }
    }

    // 3. Delete parent report metadata document
    try {
      reportDocRef.delete().awaitTask()
    } catch (e: FirebaseFirestoreException) {
      if (e.code != FirebaseFirestoreException.Code.NOT_FOUND) {
        throw e
      }
    }
  }

  private suspend fun deleteSubcollectionDocuments(
    collectionRef: com.google.firebase.firestore.CollectionReference
  ) {
    val snapshot = collectionRef.get(Source.DEFAULT).awaitTask()
    for (doc in snapshot.documents) {
      doc.reference.delete().awaitTask()
    }
  }

  private fun parseReportDocument(doc: DocumentSnapshot, expectedUid: String): PersistedMedicalReport? {
    val ownerUid = doc.getString("ownerUid") ?: return null
    if (ownerUid != expectedUid) return null

    val reportId = doc.getString("reportId") ?: doc.id
    val uploadedTs = doc.getTimestamp("uploadedAt") ?: doc.getTimestamp("createdAt")
    val expiresTs = doc.getTimestamp("expiresAt")
    val updatedTs = doc.getTimestamp("updatedAt") ?: uploadedTs
    val uploadedMillis = uploadedTs?.toDate()?.time ?: System.currentTimeMillis()
    val expiresMillis = expiresTs?.toDate()?.time
    val updatedMillis = updatedTs?.toDate()?.time ?: uploadedMillis

    val categoryEnum = MedicalDocumentCategory.entries.firstOrNull {
      it.name == doc.getString("extractedCategory")
    } ?: MedicalDocumentCategory.UNCERTAIN

    val statusTag = TestStatus.entries.firstOrNull {
      it.name == doc.getString("statusTag")
    } ?: TestStatus.INFORMATIONAL

    val rawLangs = doc.get("availableLanguages") as? List<*>
    val languages = rawLangs?.mapNotNull { it as? String }?.filter { it.isNotBlank() } ?: emptyList()

    return PersistedMedicalReport(
      reportId = reportId,
      ownerUid = ownerUid,
      title = doc.getString("title") ?: "Medical Report",
      selectedCategory = doc.getString("selectedCategory") ?: "Medical Report",
      extractedCategory = categoryEnum,
      patientLabel = doc.getString("patientLabel") ?: "Self",
      facilityOrProvider = doc.getString("facilityOrProvider") ?: "Uploaded Document",
      documentMimeType = doc.getString("documentMimeType") ?: "application/pdf",
      documentByteSize = doc.getLong("documentByteSize") ?: 0L,
      pageCount = (doc.getLong("pageCount") ?: 1L).toInt(),
      storageObjectPath = doc.getString("storageObjectPath")
        ?: ReportRetentionPolicy.storageObjectPathFor(ownerUid, reportId),
      processingStatus = ReportProcessingStatus.fromSerialized(doc.getString("processingStatus")),
      retentionDecision = RetentionDecision.fromSerialized(doc.getString("retentionDecision")),
      uploadedAtMillis = uploadedMillis,
      expiresAtMillis = expiresMillis,
      mainHighlight = doc.getString("mainHighlight") ?: "Processed medical report",
      statusTag = statusTag,
      availableLanguages = languages,
      updatedAtMillis = updatedMillis
    )
  }
}

/**
 * Coordinates UID-owned Firestore/Storage report persistence, immutable 7-day retention,
 * Room local caching, and offline deletion queuing + WorkManager reconciliation.
 */
class FirebaseReportRepository(
  private val context: Context,
  private val localDao: ReportLocalDao,
  private val remoteDataSource: RemoteReportDataSource,
  private val scheduleSyncWorker: (Context) -> Unit = { ctx -> PendingDeletionSyncWorker.enqueue(ctx) }
) : ReportRepository {

  companion object {
    private const val TAG = "NexzyReportRepo"
    private const val MAX_STORAGE_BYTES = 15L * 1024L * 1024L

    fun createDefault(context: Context): FirebaseReportRepository {
      val appContext = context.applicationContext
      val db = NexzyLocalDatabase.getInstance(appContext)
      val remote = FirebaseRemoteReportDataSource(
        firestore = FirebaseManager.firestore,
        storage = FirebaseManager.storage
      )
      return FirebaseReportRepository(
        context = appContext,
        localDao = db.reportLocalDao(),
        remoteDataSource = remote
      )
    }
  }

  private val syncScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

  override fun observeReports(uid: String): Flow<List<PersistedMedicalReport>> {
    val cleanUid = uid.trim()
    val localFlow = localDao.observeVisibleReportsForUser(cleanUid).map { entities ->
      val now = System.currentTimeMillis()
      entities
        .asSequence()
        .filter { !ReportRetentionPolicy.isExpired(it.expiresAtMillis, now) }
        .filter { it.retentionDecision != RetentionDecision.DELETE_REQUESTED.serializedValue }
        .map { it.toDomain() }
        .toList()
    }

    val remoteSyncFlow = callbackFlow<Unit> {
      val syncJob = syncScope.launch {
        try {
          remoteDataSource.observeUserReports(cleanUid).collect { remoteReports ->
            reconcileRemoteSnapshotIntoCache(cleanUid, remoteReports)
          }
        } catch (e: CancellationException) {
          throw e
        } catch (e: Exception) {
          Log.w(TAG, "Remote report observation stopped: ${e.javaClass.simpleName}")
        }
      }
      awaitClose { syncJob.cancel() }
    }

    return combine(localFlow, remoteSyncFlow.startWithUnit()) { visibleLocal, _ ->
      visibleLocal
    }
  }

  private fun Flow<Unit>.startWithUnit(): Flow<Unit> = kotlinx.coroutines.flow.flow {
    emit(Unit)
    collect { emit(it) }
  }

  override fun observePendingDeletions(uid: String): Flow<List<PendingDeletionItem>> {
    val cleanUid = uid.trim()
    return localDao.observePendingDeletionsForUser(cleanUid).map { list ->
      list.map {
        PendingDeletionItem(
          reportId = it.reportId,
          ownerUid = it.ownerUid,
          title = it.title,
          requestedAtMillis = it.requestedAtMillis,
          attemptCount = it.attemptCount,
          status = it.status
        )
      }
    }
  }

  override suspend fun createProcessedReport(
    uid: String,
    selectedDocument: SelectedDocument?,
    fileBytes: ByteArray?,
    patientLabel: String,
    selectedCategory: String,
    extractionResult: DocumentExtractionResult,
    initialExplanation: ReportExplanationResult?
  ): Result<PersistedMedicalReport> = withContext(Dispatchers.IO) {
    val cleanUid = uid.trim()
    if (cleanUid.isEmpty()) {
      return@withContext Result.failure(IllegalStateException("Authenticated user required."))
    }

    return@withContext try {
      val resolvedBytes = fileBytes ?: readDocumentBytes(selectedDocument)
      if (resolvedBytes.isEmpty()) {
        return@withContext Result.failure(
          IOException("Could not read medical report file bytes for secure storage.")
        )
      }
      if (resolvedBytes.size.toLong() > MAX_STORAGE_BYTES) {
        return@withContext Result.failure(
          IllegalArgumentException("Report file exceeds 15 MB storage limit.")
        )
      }

      val mimeType = resolveMimeType(selectedDocument)
      val draft = buildReportDraft(
        uid = cleanUid,
        mimeType = mimeType,
        byteSize = resolvedBytes.size.toLong(),
        patientLabel = patientLabel,
        selectedCategory = selectedCategory,
        extractionResult = extractionResult
      )
      val extractionJson = ReportPayloadSerializer.serializeExtraction(extractionResult)
      val initialLangCode = initialExplanation?.let {
        SupportedExplanationLanguage.fromCodeOrName(it.language)?.code ?: "en"
      }
      val initialExplanationJson = initialExplanation?.let {
        ReportPayloadSerializer.serializeExplanation(it)
      }

      val persisted = remoteDataSource.createReport(
        uid = cleanUid,
        draft = draft,
        fileBytes = resolvedBytes,
        extractionJson = extractionJson,
        initialLanguageCode = initialLangCode,
        initialExplanationJson = initialExplanationJson
      )

      if (!localDao.isPendingDeletion(cleanUid, persisted.reportId)) {
        localDao.upsertReport(CachedMedicalReportEntity.fromDomain(persisted, extractionJson))
        if (initialLangCode != null && initialExplanationJson != null) {
          localDao.upsertExplanation(
            CachedReportExplanationEntity(
              reportId = persisted.reportId,
              languageCode = initialLangCode,
              ownerUid = cleanUid,
              expiresAtMillis = persisted.expiresAtMillis,
              explanationJson = initialExplanationJson,
              updatedAtMillis = persisted.updatedAtMillis
            )
          )
        }
      }

      Log.i(TAG, "Medical report persisted successfully with server 7-day retention.")
      Result.success(persisted)
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      Log.e(TAG, "Failed to persist processed medical report: ${e.javaClass.simpleName}")
      Result.failure(e)
    }
  }

  override suspend fun saveReportExplanation(
    uid: String,
    reportId: String,
    explanation: ReportExplanationResult
  ): Result<PersistedMedicalReport> = withContext(Dispatchers.IO) {
    val cleanUid = uid.trim()
    if (cleanUid.isEmpty() || reportId.isBlank()) {
      return@withContext Result.failure(IllegalArgumentException("Invalid user or report ID."))
    }

    if (localDao.isPendingDeletion(cleanUid, reportId)) {
      return@withContext Result.failure(IllegalStateException("Report is queued for deletion."))
    }

    return@withContext try {
      val langCode = SupportedExplanationLanguage.fromCodeOrName(explanation.language)?.code ?: "en"
      val explanationJson = ReportPayloadSerializer.serializeExplanation(explanation)
      val updatedReport = remoteDataSource.saveExplanation(
        uid = cleanUid,
        reportId = reportId,
        languageCode = langCode,
        explanationJson = explanationJson
      )

      val existingCached = localDao.getReportById(cleanUid, reportId)
      localDao.upsertReport(
        CachedMedicalReportEntity.fromDomain(
          report = updatedReport,
          extractionJson = existingCached?.extractionJson
        )
      )
      localDao.upsertExplanation(
        CachedReportExplanationEntity(
          reportId = reportId,
          languageCode = langCode,
          ownerUid = cleanUid,
          expiresAtMillis = updatedReport.expiresAtMillis,
          explanationJson = explanationJson,
          updatedAtMillis = updatedReport.updatedAtMillis
        )
      )

      Log.i(TAG, "Report explanation saved inheriting parent expiry.")
      Result.success(updatedReport)
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      Log.e(TAG, "Failed to save report explanation: ${e.javaClass.simpleName}")
      Result.failure(e)
    }
  }

  override suspend fun updateRetentionDecision(
    uid: String,
    reportId: String,
    decision: RetentionDecision
  ): Result<PersistedMedicalReport> = withContext(Dispatchers.IO) {
    val cleanUid = uid.trim()
    if (cleanUid.isEmpty() || reportId.isBlank()) {
      return@withContext Result.failure(IllegalArgumentException("Invalid user or report ID."))
    }

    return@withContext try {
      val updated = remoteDataSource.updateRetentionDecision(cleanUid, reportId, decision)
      if (!localDao.isPendingDeletion(cleanUid, reportId)) {
        val existingCached = localDao.getReportById(cleanUid, reportId)
        localDao.upsertReport(
          CachedMedicalReportEntity.fromDomain(
            report = updated,
            extractionJson = existingCached?.extractionJson
          )
        )
      }
      Log.i(TAG, "Updated report retention decision.")
      Result.success(updated)
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      // Update local cache decision so offline UI remains coherent, while returning failure if remote failed
      localDao.updateCachedRetentionDecision(
        uid = cleanUid,
        reportId = reportId,
        decision = decision.serializedValue,
        updatedAtMillis = System.currentTimeMillis()
      )
      Log.w(TAG, "Failed to update remote retention decision: ${e.javaClass.simpleName}")
      Result.failure(e)
    }
  }

  override suspend fun getReportById(
    uid: String,
    reportId: String
  ): Result<PersistedReportDetail?> = withContext(Dispatchers.IO) {
    val cleanUid = uid.trim()
    if (cleanUid.isEmpty() || reportId.isBlank()) {
      return@withContext Result.success(null)
    }

    if (localDao.isPendingDeletion(cleanUid, reportId)) {
      return@withContext Result.success(null)
    }

    return@withContext try {
      val remoteDetail = try {
        remoteDataSource.fetchReportDetail(cleanUid, reportId)
      } catch (e: Exception) {
        Log.w(TAG, "Remote fetchReportDetail unavailable; reading local cache: ${e.javaClass.simpleName}")
        null
      }

      if (remoteDetail != null) {
        val report = remoteDetail.report
        if (ReportRetentionPolicy.isExpired(report.expiresAtMillis)) {
          return@withContext Result.success(null)
        }
        val extraction = remoteDetail.extractionJson?.let {
          ReportPayloadSerializer.deserializeExtraction(it)
        }
        val explanations = linkedMapOf<String, ReportExplanationResult>()
        remoteDetail.explanationsJsonByLang.forEach { (lang, json) ->
          ReportPayloadSerializer.deserializeExplanation(json)?.let { parsed ->
            explanations[lang] = parsed
          }
        }

        if (!localDao.isPendingDeletion(cleanUid, reportId)) {
          localDao.upsertReport(
            CachedMedicalReportEntity.fromDomain(
              report = report,
              extractionJson = remoteDetail.extractionJson
            )
          )
          remoteDetail.explanationsJsonByLang.forEach { (lang, json) ->
            localDao.upsertExplanation(
              CachedReportExplanationEntity(
                reportId = reportId,
                languageCode = lang,
                ownerUid = cleanUid,
                expiresAtMillis = report.expiresAtMillis,
                explanationJson = json,
                updatedAtMillis = report.updatedAtMillis
              )
            )
          }
        }

        return@withContext Result.success(
          PersistedReportDetail(
            report = report,
            extractionResult = extraction,
            explanationsByLanguage = explanations
          )
        )
      }

      // Offline cache fallback (never modifies expiresAt)
      val cached = localDao.getReportById(cleanUid, reportId) ?: return@withContext Result.success(null)
      if (ReportRetentionPolicy.isExpired(cached.expiresAtMillis)) {
        return@withContext Result.success(null)
      }
      val domain = cached.toDomain()
      val extraction = cached.extractionJson?.let { ReportPayloadSerializer.deserializeExtraction(it) }
      val cachedExplanations = localDao.getExplanationsForReport(cleanUid, reportId)
      val explanationMap = linkedMapOf<String, ReportExplanationResult>()
      cachedExplanations.forEach { entity ->
        ReportPayloadSerializer.deserializeExplanation(entity.explanationJson)?.let { parsed ->
          explanationMap[entity.languageCode] = parsed
        }
      }
      Result.success(
        PersistedReportDetail(
          report = domain,
          extractionResult = extraction,
          explanationsByLanguage = explanationMap
        )
      )
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      Log.e(TAG, "Failed loading persisted report detail: ${e.javaClass.simpleName}")
      Result.failure(e)
    }
  }

  override suspend fun fetchAllReports(uid: String): Result<List<PersistedMedicalReport>> =
    withContext(Dispatchers.IO) {
      val cleanUid = uid.trim()
      if (cleanUid.isEmpty()) return@withContext Result.success(emptyList())

      return@withContext try {
        val remoteReports = remoteDataSource.fetchAllReports(cleanUid)
        reconcileRemoteSnapshotIntoCache(cleanUid, remoteReports)
        val pendingIds = localDao.getPendingDeletionIdsForUser(cleanUid).toSet()
        val now = System.currentTimeMillis()
        val active = remoteReports.filter { report ->
          report.reportId !in pendingIds &&
            report.retentionDecision != RetentionDecision.DELETE_REQUESTED &&
            !ReportRetentionPolicy.isExpired(report.expiresAtMillis, now)
        }
        Result.success(active)
      } catch (e: CancellationException) {
        throw e
      } catch (e: Exception) {
        Log.w(TAG, "Remote fetchAllReports failed; serving non-deleted local cache: ${e.javaClass.simpleName}")
        val now = System.currentTimeMillis()
        val cached = localDao.getVisibleReportsForUser(cleanUid)
          .filter { !ReportRetentionPolicy.isExpired(it.expiresAtMillis, now) }
          .map { it.toDomain() }
        Result.success(cached)
      }
    }

  override suspend fun updateProcessingStatus(
    uid: String,
    reportId: String,
    status: ReportProcessingStatus
  ): Result<Unit> = withContext(Dispatchers.IO) {
    val cleanUid = uid.trim()
    return@withContext try {
      remoteDataSource.updateProcessingStatus(cleanUid, reportId, status)
      localDao.updateCachedProcessingStatus(
        uid = cleanUid,
        reportId = reportId,
        status = status.serializedValue,
        updatedAtMillis = System.currentTimeMillis()
      )
      Result.success(Unit)
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      Log.w(TAG, "Failed to update processing status: ${e.javaClass.simpleName}")
      Result.failure(e)
    }
  }

  override suspend fun deleteReport(
    uid: String,
    reportId: String,
    localUriToRelease: String?
  ): DeletionOutcome = withContext(Dispatchers.IO) {
    val cleanUid = uid.trim()
    if (cleanUid.isEmpty() || reportId.isBlank()) {
      return@withContext DeletionOutcome.Failed("Invalid account or report reference.")
    }

    val cached = localDao.getReportById(cleanUid, reportId)
    val storagePath = cached?.storageObjectPath
      ?: ReportRetentionPolicy.storageObjectPathFor(cleanUid, reportId)
    val safeLabel = cached?.title ?: "Medical Report"

    // Step 1: Immediately record tombstone in Room outbox & wipe local cached records + temp previews
    localDao.enqueuePendingDeletion(
      PendingReportDeletionEntity(
        reportId = reportId,
        ownerUid = cleanUid,
        title = safeLabel,
        storageObjectPath = storagePath,
        requestedAtMillis = System.currentTimeMillis(),
        lastAttemptAtMillis = 0L,
        attemptCount = 0,
        status = PendingReportDeletionEntity.STATUS_QUEUED
      )
    )
    localDao.deleteCachedReport(cleanUid, reportId)
    localDao.deleteCachedExplanationsForReport(cleanUid, reportId)
    purgeLocalTempArtifacts(localUriToRelease)

    // Step 2: Attempt immediate remote cascade deletion; if offline, queue for WorkManager retry
    return@withContext try {
      remoteDataSource.deleteReportCascade(
        uid = cleanUid,
        reportId = reportId,
        storageObjectPath = storagePath
      )
      localDao.removePendingDeletion(cleanUid, reportId)
      Log.i(TAG, "Report permanently deleted from Firestore and Storage.")
      DeletionOutcome.DeletedRemotely
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      Log.w(TAG, "Remote report deletion deferred to offline queue: ${e.javaClass.simpleName}")
      localDao.markDeletionAttemptFailed(
        uid = cleanUid,
        reportId = reportId,
        attemptTimeMillis = System.currentTimeMillis()
      )
      scheduleSyncWorker(context)
      DeletionOutcome.QueuedOffline("Pending deletion — will finish removing from cloud when online.")
    }
  }

  override suspend fun reconcilePendingDeletions(uid: String): Int = withContext(Dispatchers.IO) {
    val cleanUid = uid.trim()
    if (cleanUid.isEmpty()) return@withContext 0

    val pendingItems = localDao.getPendingDeletionsForUser(cleanUid)
    if (pendingItems.isEmpty()) return@withContext 0

    var remaining = 0
    for (item in pendingItems) {
      try {
        remoteDataSource.deleteReportCascade(
          uid = cleanUid,
          reportId = item.reportId,
          storageObjectPath = item.storageObjectPath
        )
        localDao.removePendingDeletion(cleanUid, item.reportId)
        localDao.deleteCachedReport(cleanUid, item.reportId)
        localDao.deleteCachedExplanationsForReport(cleanUid, item.reportId)
      } catch (e: CancellationException) {
        throw e
      } catch (e: Exception) {
        remaining += 1
        localDao.markDeletionAttemptFailed(
          uid = cleanUid,
          reportId = item.reportId,
          attemptTimeMillis = System.currentTimeMillis()
        )
        Log.w(TAG, "Pending offline deletion retry deferred: ${e.javaClass.simpleName}")
      }
    }
    remaining
  }

  override suspend fun purgeExpiredReports(uid: String): Int = withContext(Dispatchers.IO) {
    val cleanUid = uid.trim()
    if (cleanUid.isEmpty()) return@withContext 0

    val now = System.currentTimeMillis()
    var purged = 0
    val localCandidates = localDao.getVisibleReportsForUser(cleanUid)
    for (entity in localCandidates) {
      if (ReportRetentionPolicy.isExpired(entity.expiresAtMillis, now)) {
        deleteReport(cleanUid, entity.reportId)
        purged += 1
      }
    }

    try {
      val remoteCandidates = remoteDataSource.fetchAllReports(cleanUid)
      for (report in remoteCandidates) {
        if (ReportRetentionPolicy.isExpired(report.expiresAtMillis, now)) {
          deleteReport(cleanUid, report.reportId)
          purged += 1
        }
      }
    } catch (e: Exception) {
      Log.w(TAG, "Remote expiry sweep skipped while offline: ${e.javaClass.simpleName}")
    }

    purged
  }

  override suspend fun clearLocalUserCache(uid: String) {
    val cleanUid = uid.trim()
    if (cleanUid.isEmpty()) return
    withContext(Dispatchers.IO) {
      localDao.clearCachedReportsForUser(cleanUid)
      localDao.clearCachedExplanationsForUser(cleanUid)
      purgeLocalTempArtifacts(null)
    }
  }

  private suspend fun reconcileRemoteSnapshotIntoCache(
    uid: String,
    remoteReports: List<PersistedMedicalReport>
  ) {
    val pendingIds = localDao.getPendingDeletionIdsForUser(uid).toSet()
    val now = System.currentTimeMillis()

    val validRemote = remoteReports.filter { report ->
      report.reportId !in pendingIds &&
        report.retentionDecision != RetentionDecision.DELETE_REQUESTED &&
        !ReportRetentionPolicy.isExpired(report.expiresAtMillis, now)
    }

    val existingCachedMap = localDao.getVisibleReportsForUser(uid)
      .associateBy { it.reportId }

    val entitiesToUpsert = validRemote.map { report ->
      val preservedJson = existingCachedMap[report.reportId]?.extractionJson
      CachedMedicalReportEntity.fromDomain(report, preservedJson)
    }
    if (entitiesToUpsert.isNotEmpty()) {
      localDao.upsertReports(entitiesToUpsert)
    }

    val remoteIds = remoteReports.map { it.reportId }.toSet()
    existingCachedMap.keys
      .filter { it !in remoteIds || it in pendingIds }
      .forEach { staleId ->
        localDao.deleteCachedReport(uid, staleId)
        localDao.deleteCachedExplanationsForReport(uid, staleId)
      }
  }

  private fun readDocumentBytes(selectedDocument: SelectedDocument?): ByteArray {
    if (selectedDocument == null) return ByteArray(0)
    val uri = try {
      Uri.parse(selectedDocument.uriString)
    } catch (_: Exception) {
      return ByteArray(0)
    }
    return try {
      context.contentResolver.openInputStream(uri)?.use { stream ->
        stream.readBytes()
      } ?: ByteArray(0)
    } catch (e: Exception) {
      Log.w(TAG, "Failed reading document stream for upload: ${e.javaClass.simpleName}")
      ByteArray(0)
    }
  }

  private fun purgeLocalTempArtifacts(localUriToRelease: String?) {
    try {
      context.cacheDir?.listFiles()
        ?.filter { file ->
          file.isFile && (
            file.name.startsWith("nexzy_preview_") ||
              file.name.startsWith("nexzy_scan_") ||
              file.name.startsWith("report_temp_")
            )
        }
        ?.forEach { file -> file.delete() }
    } catch (_: Exception) {
    }

    if (!localUriToRelease.isNullOrBlank()) {
      try {
        val uri = Uri.parse(localUriToRelease)
        context.contentResolver.releasePersistableUriPermission(
          uri,
          android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION
        )
      } catch (_: Exception) {
      }
    }
  }

  private fun resolveMimeType(selectedDocument: SelectedDocument?): String {
    val raw = selectedDocument?.mimeType?.lowercase(Locale.ROOT) ?: ""
    return when {
      raw.startsWith("application/pdf") -> "application/pdf"
      raw.startsWith("image/png") -> "image/png"
      raw.startsWith("image/jpeg") || raw.startsWith("image/jpg") -> "image/jpeg"
      selectedDocument?.isPdf == true -> "application/pdf"
      else -> "image/jpeg"
    }
  }

  private fun buildReportDraft(
    uid: String,
    mimeType: String,
    byteSize: Long,
    patientLabel: String,
    selectedCategory: String,
    extractionResult: DocumentExtractionResult
  ): PersistedMedicalReport {
    val safety = SafetyFindingEvaluator.evaluate(extractionResult)
    val statusTag = when (safety.overallClassification) {
      SafetyBucket.EXTRACTED_CRITICAL_FLAG,
      SafetyBucket.AI_EXTRACTED_URGENT_WORDING,
      SafetyBucket.RULE_VALIDATED_ALERT,
      SafetyBucket.EXTRACTED_POSITIVE_FINDING -> TestStatus.REVIEW_NEEDED
      SafetyBucket.EXTRACTED_ABNORMAL_FINDING,
      SafetyBucket.OUTSIDE_EXTRACTED_REFERENCE_RANGE -> TestStatus.ATTENTION
      SafetyBucket.NO_ABNORMALITY_IDENTIFIED_IN_EXTRACTED_FINDINGS -> TestStatus.NORMAL
      else -> TestStatus.INFORMATIONAL
    }

    val (title, facility, highlight) = when (val report = extractionResult.report) {
      is UniversalMedicalReport.Laboratory -> {
        val count = report.report.items.size
        val labTitle = selectedCategory.takeIf { it.isNotBlank() } ?: "Laboratory Report"
        val labName = report.report.labFacilityName?.takeIf { it.isNotBlank() } ?: "Laboratory Report"
        val summary = if (safety.alerts.isNotEmpty()) {
          "${safety.alerts.size} flagged item(s) • $count extracted parameter(s)"
        } else {
          "$count laboratory parameter(s) extracted"
        }
        Triple(labTitle, labName, summary)
      }

      is UniversalMedicalReport.Radiology -> {
        val modality = report.report.modality?.takeIf { it.isNotBlank() } ?: "Radiology Scan"
        val region = report.report.anatomicalRegion?.takeIf { it.isNotBlank() }
        val radTitle = if (region != null) "$modality — $region" else modality
        Triple(radTitle, "Imaging Report", safety.overallHeadline)
      }

      is UniversalMedicalReport.Cardiology -> {
        val modality = report.report.modality?.takeIf { it.isNotBlank() } ?: "Cardiology Report"
        Triple(modality, "Cardiology Report", safety.overallHeadline)
      }

      is UniversalMedicalReport.Specialised -> {
        val subtype = report.report.reportSubtype?.takeIf { it.isNotBlank() } ?: "Specialised Pathology"
        Triple(subtype, "Pathology Report", safety.overallHeadline)
      }

      is UniversalMedicalReport.Clinical -> {
        val subtype = report.report.documentSubtype?.takeIf { it.isNotBlank() } ?: "Clinical Document"
        val clinician = report.report.facilityOrDoctorName?.takeIf { it.isNotBlank() } ?: "Clinical Care"
        Triple(subtype, clinician, safety.overallHeadline)
      }

      is UniversalMedicalReport.Unidentified -> {
        Triple(
          "Unidentified Document",
          "Medical Upload",
          "Document saved; findings could not be automatically classified"
        )
      }
    }

    val processingStatus = if (extractionResult.report is UniversalMedicalReport.Unidentified) {
      ReportProcessingStatus.UNIDENTIFIED
    } else {
      ReportProcessingStatus.EXTRACTED
    }

    val now = System.currentTimeMillis()
    return PersistedMedicalReport(
      reportId = "",
      ownerUid = uid,
      title = title,
      selectedCategory = selectedCategory.ifBlank { extractionResult.category.displayName },
      extractedCategory = extractionResult.category,
      patientLabel = patientLabel.ifBlank { "Self" },
      facilityOrProvider = facility,
      documentMimeType = mimeType,
      documentByteSize = byteSize,
      pageCount = extractionResult.pageCount.coerceAtLeast(1),
      storageObjectPath = "",
      processingStatus = processingStatus,
      retentionDecision = RetentionDecision.PENDING_DECISION,
      uploadedAtMillis = now,
      expiresAtMillis = ReportRetentionPolicy.calculateExpiresAtMillis(now),
      mainHighlight = highlight,
      statusTag = statusTag,
      availableLanguages = emptyList(),
      updatedAtMillis = now
    )
  }
}
