package com.example

import android.app.Application
import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.local.NexzyLocalDatabase
import com.example.data.repository.FirebaseReportRepository
import com.example.data.repository.RemoteReportDataSource
import com.example.data.repository.RemoteReportPayload
import com.example.data.serialization.ReportPayloadSerializer
import com.example.model.AnatomicalFinding
import com.example.model.CategoryExplanation
import com.example.model.DeletionOutcome
import com.example.model.DocumentExtractionResult
import com.example.model.ExplainedCardiologyMetric
import com.example.model.ExplainedClinicalFact
import com.example.model.ExplainedLabItem
import com.example.model.ExplainedMedication
import com.example.model.ExplainedRadiologyFinding
import com.example.model.ExplainedRadiologyImpression
import com.example.model.ExplainedSpecialisedSection
import com.example.model.ExtractedCardiologyReport
import com.example.model.ExtractedClinicalDocument
import com.example.model.ExtractedClinicalFact
import com.example.model.ExtractedLabItem
import com.example.model.ExtractedLaboratoryReport
import com.example.model.ExtractedMedication
import com.example.model.ExtractedRadiologyReport
import com.example.model.ExtractedSpecialisedReport
import com.example.model.FieldProvenance
import com.example.model.FindingStatus
import com.example.model.MedicalDocumentCategory
import com.example.model.MedicalTermDefinition
import com.example.model.PersistedMedicalReport
import com.example.model.ProvenanceAvailability
import com.example.model.RadiologyImpression
import com.example.model.ReportExplanationResult
import com.example.model.ReportProcessingStatus
import com.example.model.ReportRetentionPolicy
import com.example.model.RetentionDecision
import com.example.model.SelectedDocument
import com.example.model.UniversalMedicalReport
import com.example.ui.reports.ReportLifecycleViewModel
import com.google.firebase.Timestamp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.IOException

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ReportPersistenceLifecycleUnitTest {

  private lateinit var context: Context
  private lateinit var database: NexzyLocalDatabase

  @OptIn(ExperimentalCoroutinesApi::class)
  private val testDispatcher = StandardTestDispatcher()

  @OptIn(ExperimentalCoroutinesApi::class)
  @Before
  fun setup() {
    Dispatchers.setMain(testDispatcher)
    context = ApplicationProvider.getApplicationContext()
    database = Room.inMemoryDatabaseBuilder(context, NexzyLocalDatabase::class.java)
      .allowMainThreadQueries()
      .build()
  }

  @OptIn(ExperimentalCoroutinesApi::class)
  @After
  fun teardown() {
    database.close()
    Dispatchers.resetMain()
  }

  @Test
  fun `seven day expiry is anchored strictly to uploadedAt and never resets on reopen or explanation save`() = runBlocking {
    val fixedUploadSeconds = 1_800_000_000L
    val fakeRemote = FakeRemoteReportDataSource(fixedUploadSeconds = fixedUploadSeconds)
    val repo = FirebaseReportRepository(
      context = context,
      localDao = database.reportLocalDao(),
      remoteDataSource = fakeRemote,
      scheduleSyncWorker = {}
    )

    val extraction = sampleLabExtraction()
    val created = repo.createProcessedReport(
      uid = "uid-alice",
      selectedDocument = SelectedDocument(
        uriString = "content://docs/report.pdf",
        fileName = "cbc.pdf",
        mimeType = "application/pdf",
        fileSizeBytes = 2048L,
        formattedSize = "2 KB"
      ),
      fileBytes = byteArrayOf(1, 2, 3, 4),
      patientLabel = "Self",
      selectedCategory = "Blood Test",
      extractionResult = extraction
    ).getOrThrow()

    val expectedExpiresMillis = (fixedUploadSeconds + ReportRetentionPolicy.RETENTION_SECONDS) * 1000L
    assertEquals(fixedUploadSeconds * 1000L, created.uploadedAtMillis)
    assertEquals(expectedExpiresMillis, created.expiresAtMillis)
    assertEquals(RetentionDecision.PENDING_DECISION, created.retentionDecision)

    // Keep for 7 days -> uploadedAt & expiresAt must stay identical
    val kept = repo.updateRetentionDecision("uid-alice", created.reportId, RetentionDecision.KEEP_SEVEN_DAYS)
      .getOrThrow()
    assertEquals(RetentionDecision.KEEP_SEVEN_DAYS, kept.retentionDecision)
    assertEquals(expectedExpiresMillis, kept.expiresAtMillis)

    // Advance mock clock, reopen report, and save an explanation -> expiresAt must STILL match original upload + 7 days
    fakeRemote.currentTimeSeconds = fixedUploadSeconds + (3L * 86_400L)
    val reopened = repo.getReportById("uid-alice", created.reportId).getOrThrow()
    assertNotNull(reopened)
    assertEquals(expectedExpiresMillis, reopened!!.report.expiresAtMillis)

    val explained = repo.saveReportExplanation(
      uid = "uid-alice",
      reportId = created.reportId,
      explanation = sampleLabExplanation(extraction)
    ).getOrThrow()
    assertEquals(expectedExpiresMillis, explained.expiresAtMillis)
    assertEquals(ReportProcessingStatus.EXPLAINED, explained.processingStatus)
    assertTrue(explained.availableLanguages.contains("en"))
  }

  @Test
  fun `offline deletion hides report immediately queues sync and never resurrects from stale snapshot`() = runBlocking {
    val fakeRemote = FakeRemoteReportDataSource(fixedUploadSeconds = System.currentTimeMillis() / 1000L)
    var workerScheduledCount = 0
    val repo = FirebaseReportRepository(
      context = context,
      localDao = database.reportLocalDao(),
      remoteDataSource = fakeRemote,
      scheduleSyncWorker = { workerScheduledCount += 1 }
    )

    val created = repo.createProcessedReport(
      uid = "uid-bob",
      selectedDocument = null,
      fileBytes = byteArrayOf(9, 8, 7),
      patientLabel = "Father",
      selectedCategory = "Blood Test",
      extractionResult = sampleLabExtraction()
    ).getOrThrow()

    repo.updateRetentionDecision("uid-bob", created.reportId, RetentionDecision.KEEP_SEVEN_DAYS).getOrThrow()
    assertEquals(1, repo.observeReports("uid-bob").first().size)

    // Simulate going offline when deleting
    fakeRemote.failDeletes = true
    val outcome = repo.deleteReport("uid-bob", created.reportId)
    assertTrue(outcome is DeletionOutcome.QueuedOffline)
    assertEquals(1, workerScheduledCount)

    // Report disappears immediately from local visible list, and pending deletion item is visible
    assertTrue(repo.observeReports("uid-bob").first().isEmpty())
    val pending = repo.observePendingDeletions("uid-bob").first()
    assertEquals(1, pending.size)
    assertEquals(created.reportId, pending.first().reportId)

    // Remote snapshot arrives before reconciliation completes -> report must NOT resurrect
    fakeRemote.emitSnapshot("uid-bob")
    delay(50)
    assertTrue(repo.observeReports("uid-bob").first().isEmpty())
    assertNull(repo.getReportById("uid-bob", created.reportId).getOrThrow())

    // Come back online and reconcile queued deletions
    fakeRemote.failDeletes = false
    val remaining = repo.reconcilePendingDeletions("uid-bob")
    assertEquals(0, remaining)
    assertTrue(repo.observePendingDeletions("uid-bob").first().isEmpty())
    assertEquals(1, fakeRemote.deletedReportIds.size)
    assertEquals(created.reportId, fakeRemote.deletedReportIds.first())
  }

  @Test
  fun `serializer round trips all six extraction categories and explanation models losslessly`() {
    val samples = listOf(
      sampleLabExtraction(),
      DocumentExtractionResult(
        category = MedicalDocumentCategory.RADIOLOGY,
        pageCount = 2,
        isMultiPageBatch = true,
        report = UniversalMedicalReport.Radiology(
          ExtractedRadiologyReport(
            modality = "CT Chest",
            anatomicalRegion = "Thorax",
            clinicalIndication = "Persistent cough",
            techniqueDescription = "Non-contrast spiral CT",
            anatomicalFindings = listOf(
              AnatomicalFinding(
                anatomicalSite = "Pleural space",
                observationText = "No pleural effusion",
                findingStatus = FindingStatus.NEGATIVE,
                provenance = FieldProvenance(1, "No effusion seen", 0.91f, ProvenanceAvailability.AVAILABLE)
              )
            ),
            formalImpressions = listOf(
              RadiologyImpression(
                impressionText = "Clear lungs",
                findingStatus = FindingStatus.NORMAL,
                provenance = FieldProvenance.unavailable()
              )
            )
          )
        ),
        extractionTimestamp = 123456L
      ),
      DocumentExtractionResult(
        category = MedicalDocumentCategory.CARDIOLOGY,
        pageCount = 1,
        isMultiPageBatch = false,
        report = UniversalMedicalReport.Cardiology(
          ExtractedCardiologyReport(
            modality = "12-Lead ECG",
            heartRateBpm = "74 bpm",
            rhythmInterpretation = "Normal sinus rhythm",
            prIntervalMs = "158 ms",
            qrsDurationMs = "88 ms",
            qtcIntervalMs = "412 ms",
            ejectionFractionPercent = null,
            wallMotionFindings = null,
            formalConclusion = "Normal resting ECG",
            findingStatus = FindingStatus.NORMAL,
            provenance = FieldProvenance(1, "Normal sinus rhythm", 0.95f, ProvenanceAvailability.AVAILABLE),
            fieldProvenance = mapOf("heartRateBpm" to FieldProvenance(1, "HR 74 bpm", 0.94f, ProvenanceAvailability.AVAILABLE))
          )
        ),
        extractionTimestamp = 234567L
      ),
      DocumentExtractionResult(
        category = MedicalDocumentCategory.SPECIALISED,
        pageCount = 3,
        isMultiPageBatch = true,
        report = UniversalMedicalReport.Specialised(
          ExtractedSpecialisedReport(
            reportSubtype = "Histopathology Biopsy",
            specimenSource = "Gastric antrum",
            clinicalHistory = "Dyspepsia",
            grossDescription = "Two tan tissue fragments",
            microscopicDescription = "Mild chronic inflammation",
            gradingOrStaging = null,
            diagnosticConclusion = "Mild chronic gastritis",
            findingStatus = FindingStatus.ABNORMAL,
            provenance = FieldProvenance(2, "Mild chronic gastritis", 0.89f, ProvenanceAvailability.AVAILABLE)
          )
        ),
        extractionTimestamp = 345678L
      ),
      DocumentExtractionResult(
        category = MedicalDocumentCategory.CLINICAL,
        pageCount = 1,
        isMultiPageBatch = false,
        report = UniversalMedicalReport.Clinical(
          ExtractedClinicalDocument(
            documentSubtype = "Outpatient Prescription",
            facilityOrDoctorName = "City Care Clinic",
            primaryDiagnoses = listOf(ExtractedClinicalFact("Type 2 Diabetes")),
            surgicalProcedures = emptyList(),
            prescribedMedications = listOf(
              ExtractedMedication(
                drugName = "Metformin",
                dosage = "500 mg",
                frequency = "Twice daily",
                duration = "30 days",
                instructions = "After meals",
                provenance = FieldProvenance(1, "Metformin 500mg BD", 0.92f, ProvenanceAvailability.AVAILABLE)
              )
            ),
            allergiesRecorded = listOf(ExtractedClinicalFact("Penicillin")),
            dischargeInstructions = listOf(ExtractedClinicalFact("Follow up in 1 month")),
            followUpDate = "10 Dec 2026"
          )
        ),
        extractionTimestamp = 456789L
      ),
      DocumentExtractionResult(
        category = MedicalDocumentCategory.UNCERTAIN,
        pageCount = 1,
        isMultiPageBatch = false,
        report = UniversalMedicalReport.Unidentified("blurry text", "Low legibility"),
        extractionTimestamp = 567890L
      )
    )

    samples.forEach { original ->
      val json = ReportPayloadSerializer.serializeExtraction(original)
      val decoded = ReportPayloadSerializer.deserializeExtraction(json)
      assertEquals(original, decoded)
    }

    val labExp = sampleLabExplanation(samples.first())
    val expJson = ReportPayloadSerializer.serializeExplanation(labExp)
    assertEquals(labExp, ReportPayloadSerializer.deserializeExplanation(expJson))

    val cardioExp = ReportExplanationResult(
      category = MedicalDocumentCategory.CARDIOLOGY,
      language = "Hindi",
      conciseSummary = "Normal ECG summary",
      categoryExplanation = CategoryExplanation.Cardiology(
        sourceReport = (samples[2].report as UniversalMedicalReport.Cardiology).report,
        modalityExplanation = "Measures heart electrical activity",
        metrics = listOf(
          ExplainedCardiologyMetric(
            fieldKey = "heartRateBpm",
            label = "Heart Rate",
            originalValue = "74 bpm",
            plainLanguageExplanation = "Steady heart rate",
            provenance = FieldProvenance(1, "HR 74 bpm", 0.94f, ProvenanceAvailability.AVAILABLE)
          )
        ),
        conclusionExplanation = "Within usual resting rhythm",
        uncertaintyNote = null
      ),
      medicalTerms = listOf(MedicalTermDefinition("Sinus rhythm", "Normal heartbeat pattern")),
      questionsForDoctor = listOf("Should I repeat ECG next year?"),
      uncertaintyNotes = emptyList(),
      generatedTimestamp = 99999L
    )
    assertEquals(cardioExp, ReportPayloadSerializer.deserializeExplanation(ReportPayloadSerializer.serializeExplanation(cardioExp)))
  }

  @OptIn(ExperimentalCoroutinesApi::class)
  @Test
  fun `leaving without choosing Keep defaults to deletion in ReportLifecycleViewModel`() = runBlocking {
    val fakeRemote = FakeRemoteReportDataSource(fixedUploadSeconds = System.currentTimeMillis() / 1000L)
    val repo = FirebaseReportRepository(
      context = context,
      localDao = database.reportLocalDao(),
      remoteDataSource = fakeRemote,
      scheduleSyncWorker = {}
    )
    val vm = ReportLifecycleViewModel(
      application = context as Application,
      repository = repo
    )
    vm.syncUserSession("uid-carol")
    advanceAndWaitUntil { vm.uiState.value.activeUid == "uid-carol" }

    vm.onExtractionCompleted(
      uid = "uid-carol",
      selectedDocument = SelectedDocument(
        uriString = "content://docs/temp.pdf",
        fileName = "temp.pdf",
        mimeType = "application/pdf",
        fileSizeBytes = 1024L,
        formattedSize = "1 KB"
      ),
      patientLabel = "Self",
      selectedCategory = "Blood Test",
      extractionResult = sampleLabExtraction(),
      fileBytes = byteArrayOf(1, 2, 3)
    )
    advanceAndWaitUntil { vm.uiState.value.activeSession.report != null }

    val createdId = vm.uiState.value.activeSession.report?.reportId
    assertNotNull(createdId)
    assertTrue(vm.uiState.value.activeSession.requiresRetentionDecision)

    var immediateExitAllowed = false
    vm.requestExitReportSession { immediateExitAllowed = true }
    assertFalse(immediateExitAllowed)
    assertTrue(vm.uiState.value.isExitRetentionPromptVisible)

    var leftAfterDefaultDelete = false
    vm.dismissExitPromptAndDeleteByDefault { leftAfterDefaultDelete = true }
    advanceAndWaitUntil { leftAfterDefaultDelete }

    assertTrue(leftAfterDefaultDelete)
    assertFalse(vm.uiState.value.isExitRetentionPromptVisible)
    assertTrue(fakeRemote.deletedReportIds.contains(createdId))
    assertNull(vm.uiState.value.activeSession.report)
  }

  @OptIn(ExperimentalCoroutinesApi::class)
  private suspend fun advanceAndWaitUntil(timeoutMs: Long = 5_000L, predicate: () -> Boolean) {
    val deadline = System.currentTimeMillis() + timeoutMs
    while (!predicate() && System.currentTimeMillis() < deadline) {
      testDispatcher.scheduler.advanceUntilIdle()
      if (!predicate()) {
        delay(20)
      }
    }
    testDispatcher.scheduler.advanceUntilIdle()
  }

  private fun sampleLabExtraction(): DocumentExtractionResult {
    return DocumentExtractionResult(
      category = MedicalDocumentCategory.LABORATORY,
      pageCount = 1,
      isMultiPageBatch = false,
      report = UniversalMedicalReport.Laboratory(
        ExtractedLaboratoryReport(
          labFacilityName = "Apex Diagnostics",
          collectionDate = "24 Nov 2026",
          reportDate = "25 Nov 2026",
          items = listOf(
            ExtractedLabItem(
              originalName = "Hemoglobin",
              originalValue = "11.4",
              originalUnit = "g/dL",
              referenceRange = "12.0 - 15.5",
              laboratoryFlag = "L",
              findingStatus = FindingStatus.ABNORMAL,
              provenance = FieldProvenance(1, "Hemoglobin 11.4 g/dL L", 0.96f, ProvenanceAvailability.AVAILABLE)
            )
          )
        )
      ),
      extractionTimestamp = 10000L
    )
  }

  private fun sampleLabExplanation(extraction: DocumentExtractionResult): ReportExplanationResult {
    val lab = (extraction.report as UniversalMedicalReport.Laboratory).report
    return ReportExplanationResult(
      category = MedicalDocumentCategory.LABORATORY,
      language = "English",
      conciseSummary = "Your hemoglobin is slightly below the printed range.",
      categoryExplanation = CategoryExplanation.Laboratory(
        labFacilityName = lab.labFacilityName,
        collectionDate = lab.collectionDate,
        reportDate = lab.reportDate,
        items = listOf(
          ExplainedLabItem(
            sourceItem = lab.items.first(),
            whatItMeasures = "Oxygen-carrying protein in red blood cells.",
            plainLanguageExplanation = "Slightly below the lab's printed 12.0–15.5 g/dL range.",
            uncertaintyNote = null
          )
        )
      ),
      medicalTerms = listOf(MedicalTermDefinition("Hemoglobin", "Protein carrying oxygen in blood")),
      questionsForDoctor = listOf("Should I repeat this CBC test?"),
      uncertaintyNotes = emptyList(),
      generatedTimestamp = 20000L
    )
  }
}

private class FakeRemoteReportDataSource(
  private val fixedUploadSeconds: Long,
  var currentTimeSeconds: Long = fixedUploadSeconds
) : RemoteReportDataSource {
  private val records = linkedMapOf<String, MutableMap<String, PersistedMedicalReport>>()
  private val extractions = linkedMapOf<String, String>()
  private val explanations = linkedMapOf<String, MutableMap<String, String>>()
  private val flowByUid = linkedMapOf<String, MutableStateFlow<List<PersistedMedicalReport>>>()
  private var nextSeq = 1

  var failDeletes: Boolean = false
  val deletedReportIds = mutableListOf<String>()

  private fun uidFlow(uid: String): MutableStateFlow<List<PersistedMedicalReport>> =
    flowByUid.getOrPut(uid) { MutableStateFlow(emptyList()) }

  fun emitSnapshot(uid: String) {
    uidFlow(uid).value = records[uid]?.values?.toList().orEmpty()
  }

  override fun observeUserReports(uid: String): Flow<List<PersistedMedicalReport>> =
    uidFlow(uid).map { it }

  override suspend fun createReport(
    uid: String,
    draft: PersistedMedicalReport,
    fileBytes: ByteArray,
    extractionJson: String,
    initialLanguageCode: String?,
    initialExplanationJson: String?
  ): PersistedMedicalReport {
    val reportId = "rep_${nextSeq++}"
    val expiresTs = ReportRetentionPolicy.calculateExpiresAtTimestamp(Timestamp(fixedUploadSeconds, 0))
    val created = draft.copy(
      reportId = reportId,
      ownerUid = uid,
      storageObjectPath = ReportRetentionPolicy.storageObjectPathFor(uid, reportId),
      uploadedAtMillis = fixedUploadSeconds * 1000L,
      expiresAtMillis = expiresTs.toDate().time,
      updatedAtMillis = fixedUploadSeconds * 1000L
    )
    records.getOrPut(uid) { linkedMapOf() }[reportId] = created
    extractions["$uid/$reportId"] = extractionJson
    if (initialLanguageCode != null && initialExplanationJson != null) {
      explanations.getOrPut("$uid/$reportId") { linkedMapOf() }[initialLanguageCode] = initialExplanationJson
    }
    emitSnapshot(uid)
    return created
  }

  override suspend fun saveExplanation(
    uid: String,
    reportId: String,
    languageCode: String,
    explanationJson: String
  ): PersistedMedicalReport {
    val existing = records[uid]?.get(reportId) ?: error("Report missing")
    explanations.getOrPut("$uid/$reportId") { linkedMapOf() }[languageCode] = explanationJson
    val updated = existing.copy(
      processingStatus = ReportProcessingStatus.EXPLAINED,
      availableLanguages = (existing.availableLanguages + languageCode).distinct(),
      updatedAtMillis = currentTimeSeconds * 1000L
    )
    records[uid]!![reportId] = updated
    emitSnapshot(uid)
    return updated
  }

  override suspend fun updateRetentionDecision(
    uid: String,
    reportId: String,
    decision: RetentionDecision
  ): PersistedMedicalReport {
    val existing = records[uid]?.get(reportId) ?: error("Report missing")
    val updated = existing.copy(
      retentionDecision = decision,
      updatedAtMillis = currentTimeSeconds * 1000L
    )
    records[uid]!![reportId] = updated
    emitSnapshot(uid)
    return updated
  }

  override suspend fun updateProcessingStatus(
    uid: String,
    reportId: String,
    status: ReportProcessingStatus
  ) {
    val existing = records[uid]?.get(reportId) ?: return
    records[uid]!![reportId] = existing.copy(processingStatus = status)
    emitSnapshot(uid)
  }

  override suspend fun fetchAllReports(uid: String): List<PersistedMedicalReport> {
    return records[uid]?.values?.toList().orEmpty()
  }

  override suspend fun fetchReportDetail(uid: String, reportId: String): RemoteReportPayload? {
    val report = records[uid]?.get(reportId) ?: return null
    return RemoteReportPayload(
      report = report,
      extractionJson = extractions["$uid/$reportId"],
      explanationsJsonByLang = explanations["$uid/$reportId"]?.toMap().orEmpty()
    )
  }

  override suspend fun deleteReportCascade(
    uid: String,
    reportId: String,
    storageObjectPath: String
  ) {
    if (failDeletes) {
      throw IOException("Simulated offline network error")
    }
    deletedReportIds.add(reportId)
    records[uid]?.remove(reportId)
    extractions.remove("$uid/$reportId")
    explanations.remove("$uid/$reportId")
    emitSnapshot(uid)
  }
}
