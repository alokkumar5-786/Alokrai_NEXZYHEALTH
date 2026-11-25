package com.example

import android.app.Application
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.test.core.app.ApplicationProvider
import com.example.domain.engine.AiModelConfig
import com.example.domain.engine.MedicalExplanationParser
import com.example.domain.engine.ReportExplainer
import com.example.model.AnatomicalFinding
import com.example.model.CategoryExplanation
import com.example.model.DocumentExtractionResult
import com.example.model.ExplanationConstants
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
import com.example.model.ProvenanceAvailability
import com.example.model.RadiologyImpression
import com.example.model.ReportExplanationResult
import com.example.model.ReportExplanationState
import com.example.model.UniversalMedicalReport
import com.example.model.UserProfile
import com.example.ui.explanation.ExplanationConsentStore
import com.example.ui.explanation.ReportExplanationViewModel
import com.example.ui.explanation.SharedPreferencesExplanationConsentStore
import com.example.ui.resolveExplanationLanguage
import com.example.ui.screens.ExtractionResultScreen
import com.example.ui.screens.ReportExplanationScreen
import com.example.ui.theme.MyApplicationTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.IOException

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class Task3DExplanationUnitTest {

  @get:Rule
  val composeTestRule = createComposeRule()

  private val testDispatcher = UnconfinedTestDispatcher()

  @Before
  fun setUp() {
    Dispatchers.setMain(testDispatcher)
  }

  @After
  fun tearDown() {
    Dispatchers.resetMain()
  }

  private class InMemoryExplanationConsentStore(
    initialUid: String? = null
  ) : ExplanationConsentStore {
    private val grantedUids = mutableSetOf<String>().apply {
      initialUid?.trim()?.takeIf { it.isNotEmpty() }?.let { add(it) }
    }

    override fun hasGrantedExplanationConsent(uid: String?): Boolean {
      val safeUid = uid?.trim()?.takeIf { it.isNotEmpty() } ?: return false
      return safeUid in grantedUids
    }

    override fun setExplanationConsentGranted(uid: String?, granted: Boolean) {
      val safeUid = uid?.trim()?.takeIf { it.isNotEmpty() } ?: return
      if (granted) grantedUids.add(safeUid) else grantedUids.remove(safeUid)
    }

    override fun clearConsentForUser(uid: String?) {
      val safeUid = uid?.trim()?.takeIf { it.isNotEmpty() } ?: return
      grantedUids.remove(safeUid)
    }

    override fun clearAllConsents() {
      grantedUids.clear()
    }
  }

  private class FakeReportExplainer(
    private val handler: suspend (callIndex: Int, extraction: DocumentExtractionResult, language: String) -> Result<ReportExplanationResult>
  ) : ReportExplainer {
    var callCount: Int = 0
      private set
    val observedLanguages: MutableList<String> = mutableListOf()

    override suspend fun explainReport(
      extractionResult: DocumentExtractionResult,
      language: String
    ): Result<ReportExplanationResult> {
      callCount += 1
      observedLanguages.add(language)
      return handler(callCount, extractionResult, language)
    }
  }

  private fun createViewModel(
    consentStore: ExplanationConsentStore,
    explainer: ReportExplainer,
    uidProvider: () -> String? = { "test-user-uid" }
  ): ReportExplanationViewModel {
    val app = ApplicationProvider.getApplicationContext<Application>()
    return ReportExplanationViewModel(
      application = app,
      explainer = explainer,
      consentStore = consentStore,
      currentUidProvider = uidProvider
    )
  }

  private fun sampleLabExtraction(): DocumentExtractionResult = DocumentExtractionResult(
    category = MedicalDocumentCategory.LABORATORY,
    pageCount = 2,
    isMultiPageBatch = true,
    report = UniversalMedicalReport.Laboratory(
      ExtractedLaboratoryReport(
        labFacilityName = "Metro Endocrine Lab",
        collectionDate = "2026-11-10",
        reportDate = "2026-11-11",
        items = listOf(
          ExtractedLabItem(
            originalName = "Hemoglobin",
            originalValue = "10.8",
            originalUnit = "g/dL",
            referenceRange = "12.0 - 15.5",
            laboratoryFlag = "L",
            findingStatus = FindingStatus.ABNORMAL,
            provenance = FieldProvenance(
              pageNumber = 1,
              sourceSnippet = "Hemoglobin 10.8 g/dL (12.0 - 15.5) L",
              estimatedExtractionConfidence = 0.96f,
              availability = ProvenanceAvailability.AVAILABLE
            )
          ),
          ExtractedLabItem(
            originalName = "HBsAg",
            originalValue = "Non-Reactive",
            originalUnit = null,
            referenceRange = "Non-Reactive",
            laboratoryFlag = null,
            findingStatus = FindingStatus.NEGATIVE,
            provenance = FieldProvenance(
              pageNumber = 2,
              sourceSnippet = "HBsAg: Non-Reactive",
              estimatedExtractionConfidence = 0.92f,
              availability = ProvenanceAvailability.AVAILABLE
            )
          )
        )
      )
    )
  )

  private fun sampleRadiologyExtraction(): DocumentExtractionResult = DocumentExtractionResult(
    category = MedicalDocumentCategory.RADIOLOGY,
    pageCount = 1,
    isMultiPageBatch = false,
    report = UniversalMedicalReport.Radiology(
      ExtractedRadiologyReport(
        modality = "X-Ray",
        anatomicalRegion = "Chest PA",
        clinicalIndication = "Persistent cough",
        techniqueDescription = "Posteroanterior upright chest radiograph",
        anatomicalFindings = listOf(
          AnatomicalFinding(
            anatomicalSite = "Lung parenchyma",
            observationText = "Clear bilaterally without focal consolidation.",
            findingStatus = FindingStatus.NEGATIVE,
            provenance = FieldProvenance(1, "Lung parenchyma: Clear", 0.95f, ProvenanceAvailability.AVAILABLE)
          )
        ),
        formalImpressions = listOf(
          RadiologyImpression(
            impressionText = "No acute cardiopulmonary abnormality.",
            findingStatus = FindingStatus.NEGATIVE,
            provenance = FieldProvenance(1, "No acute cardiopulmonary abnormality.", 0.97f, ProvenanceAvailability.AVAILABLE)
          )
        )
      )
    )
  )

  private fun sampleCardiologyExtraction(): DocumentExtractionResult = DocumentExtractionResult(
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
        qtcIntervalMs = null,
        ejectionFractionPercent = null,
        wallMotionFindings = null,
        formalConclusion = "Normal sinus rhythm without acute ischemic changes.",
        findingStatus = FindingStatus.NORMAL,
        provenance = FieldProvenance(1, "Conclusion: Normal sinus rhythm", 0.94f, ProvenanceAvailability.AVAILABLE),
        fieldProvenance = mapOf(
          "heartRateBpm" to FieldProvenance(1, "HR: 74 bpm", 0.98f, ProvenanceAvailability.AVAILABLE)
        )
      )
    )
  )

  private fun sampleSpecialisedExtraction(): DocumentExtractionResult = DocumentExtractionResult(
    category = MedicalDocumentCategory.SPECIALISED,
    pageCount = 2,
    isMultiPageBatch = true,
    report = UniversalMedicalReport.Specialised(
      ExtractedSpecialisedReport(
        reportSubtype = "Core Needle Biopsy",
        specimenSource = "Left thyroid nodule",
        clinicalHistory = "Palpable nodule",
        grossDescription = "Two tan tissue cores measuring 1.2 cm",
        microscopicDescription = "Follicular cells arranged in uniform macrofollicles",
        gradingOrStaging = null,
        diagnosticConclusion = "Benign follicular nodule (Bethesda Category II).",
        findingStatus = FindingStatus.NEGATIVE,
        provenance = FieldProvenance(2, "Benign follicular nodule", 0.91f, ProvenanceAvailability.AVAILABLE),
        fieldProvenance = mapOf(
          "specimenSource" to FieldProvenance(1, "Specimen: Left thyroid nodule", 0.95f, ProvenanceAvailability.AVAILABLE)
        )
      )
    )
  )

  private fun sampleClinicalExtraction(): DocumentExtractionResult = DocumentExtractionResult(
    category = MedicalDocumentCategory.CLINICAL,
    pageCount = 1,
    isMultiPageBatch = false,
    report = UniversalMedicalReport.Clinical(
      ExtractedClinicalDocument(
        documentSubtype = "Outpatient Prescription",
        facilityOrDoctorName = "Dr. A. Mehta, MD",
        primaryDiagnoses = listOf(
          ExtractedClinicalFact("Essential hypertension", FieldProvenance(1, "Dx: Essential hypertension", 0.93f, ProvenanceAvailability.AVAILABLE))
        ),
        surgicalProcedures = emptyList(),
        prescribedMedications = listOf(
          ExtractedMedication(
            drugName = "Amlodipine",
            dosage = "5 mg",
            frequency = "Once daily",
            duration = "30 days",
            instructions = "Take after breakfast",
            provenance = FieldProvenance(1, "Tab Amlodipine 5 mg OD x 30 days after breakfast", 0.94f, ProvenanceAvailability.AVAILABLE)
          ),
          ExtractedMedication(
            drugName = "Vitamin B12",
            dosage = null,
            frequency = null,
            duration = null,
            instructions = null,
            provenance = FieldProvenance.unavailable()
          )
        ),
        allergiesRecorded = listOf(
          ExtractedClinicalFact("Penicillin", FieldProvenance(1, "Allergy: Penicillin", 0.96f, ProvenanceAvailability.AVAILABLE))
        ),
        dischargeInstructions = listOf(
          ExtractedClinicalFact("Monitor home blood pressure log twice weekly", FieldProvenance(1, "Monitor home BP", 0.90f, ProvenanceAvailability.AVAILABLE))
        ),
        followUpDate = "2026-12-10"
      )
    )
  )

  @Test
  fun verifyModelConfigDefaultAndFiveCategoryExplanationParsing() {
    assertEquals("gemini-3.8-flash", AiModelConfig.explanationModel)

    // 1. LABORATORY
    val labJson = """
      {
        "conciseSummary": "This lab report shows Hemoglobin is below the printed reference range and HBsAg is negative.",
        "uncertaintyNotes": [],
        "medicalTerms": [{"term": "Hemoglobin", "simpleDefinition": "A protein in red blood cells that carries oxygen."}],
        "questionsForDoctor": ["Could we review what might explain my Hemoglobin of 10.8 g/dL?"],
        "items": [
          {"itemIndex": 0, "originalName": "Hemoglobin", "whatItMeasures": "Oxygen-carrying protein in blood.", "plainLanguageExplanation": "Your printed value of 10.8 g/dL is flagged L below the lab range of 12.0 - 15.5."},
          {"itemIndex": 1, "originalName": "HBsAg", "whatItMeasures": "Surface marker checked for Hepatitis B.", "plainLanguageExplanation": "The report records this marker as Non-Reactive (negative)."}
        ]
      }
    """.trimIndent()
    val labParsed = MedicalExplanationParser.parseExplanationJsonSafe(labJson, sampleLabExtraction()).getOrThrow()
    val labCat = labParsed.categoryExplanation as CategoryExplanation.Laboratory
    assertEquals(MedicalDocumentCategory.LABORATORY, labParsed.category)
    assertEquals(ExplanationConstants.DEFAULT_EXPLANATION_LANGUAGE, labParsed.language)
    assertEquals("10.8", labCat.items[0].sourceItem.originalValue)
    assertEquals("g/dL", labCat.items[0].sourceItem.originalUnit)
    assertEquals("L", labCat.items[0].sourceItem.laboratoryFlag)
    assertEquals(1, labCat.items[0].sourceItem.provenance.pageNumber)
    assertNotNull(labCat.items[0].plainLanguageExplanation)
    assertEquals(ExplanationConstants.MEDICAL_EXPLANATION_DISCLAIMER, labParsed.safetyDisclaimer)

    // 2. RADIOLOGY
    val radJson = """
      {
        "conciseSummary": "Written chest X-ray report states clear lungs with no acute abnormality.",
        "studyOverviewExplanation": "This chest X-ray was ordered to check a persistent cough.",
        "findings": [{"findingIndex": 0, "anatomicalSite": "Lung parenchyma", "plainLanguageExplanation": "The lung tissue appeared clear on the written report."}],
        "impressions": [{"impressionIndex": 0, "plainLanguageExplanation": "The radiologist noted no sudden heart or lung issue."}],
        "medicalTerms": [{"term": "Parenchyma", "simpleDefinition": "The main working tissue of the lung."}],
        "questionsForDoctor": ["Should we check other causes for my cough?"]
      }
    """.trimIndent()
    val radParsed = MedicalExplanationParser.parseExplanationJsonSafe(radJson, sampleRadiologyExtraction()).getOrThrow()
    val radCat = radParsed.categoryExplanation as CategoryExplanation.Radiology
    assertEquals("X-Ray", radCat.modality)
    assertEquals("Clear bilaterally without focal consolidation.", radCat.findings[0].sourceFinding.observationText)
    assertEquals(1, radCat.impressions[0].sourceImpression.provenance.pageNumber)

    // 3. CARDIOLOGY
    val cardioJson = """
      {
        "conciseSummary": "Written ECG report records a resting heart rate of 74 bpm in normal sinus rhythm.",
        "modalityExplanation": "A 12-lead ECG records electrical signals of the heartbeat.",
        "metrics": [
          {"fieldKey": "heartRateBpm", "originalValue": "74 bpm", "plainLanguageExplanation": "Your printed heart rate is 74 beats per minute."},
          {"fieldKey": "prIntervalMs", "originalValue": "158 ms", "plainLanguageExplanation": "Measures electrical travel time from upper to lower heart chambers."},
          {"fieldKey": "formalConclusion", "originalValue": "Normal sinus rhythm without acute ischemic changes.", "plainLanguageExplanation": "The written summary notes a normal sinus rhythm."},
          {"fieldKey": "ejectionFractionPercent", "originalValue": "60%", "plainLanguageExplanation": "Should be ignored because original was null"}
        ],
        "medicalTerms": [{"term": "PR Interval", "simpleDefinition": "Timing of the heart's upper electrical signal."}],
        "questionsForDoctor": ["Do I need any routine repeat ECG?"]
      }
    """.trimIndent()
    val cardioParsed = MedicalExplanationParser.parseExplanationJsonSafe(cardioJson, sampleCardiologyExtraction()).getOrThrow()
    val cardioCat = cardioParsed.categoryExplanation as CategoryExplanation.Cardiology
    // Only non-null metrics (heartRateBpm, rhythmInterpretation, prIntervalMs, qrsDurationMs) must exist — never null ejectionFractionPercent
    assertEquals(4, cardioCat.metrics.size)
    assertTrue(cardioCat.metrics.none { it.fieldKey == "ejectionFractionPercent" })
    assertEquals("74 bpm", cardioCat.metrics.first { it.fieldKey == "heartRateBpm" }.originalValue)
    assertEquals("HR: 74 bpm", cardioCat.metrics.first { it.fieldKey == "heartRateBpm" }.provenance.sourceSnippet)
    assertNotNull(cardioCat.conclusionExplanation)

    // 4. SPECIALISED
    val specJson = """
      {
        "conciseSummary": "The written biopsy report describes a benign follicular thyroid nodule.",
        "reportOverviewExplanation": "A core needle biopsy examines a small sample of tissue under a microscope.",
        "sections": [
          {"fieldKey": "specimenSource", "plainLanguageExplanation": "Sample was taken from the left thyroid nodule."},
          {"fieldKey": "diagnosticConclusion", "plainLanguageExplanation": "The written conclusion states benign follicular nodule."}
        ],
        "medicalTerms": [{"term": "Benign", "simpleDefinition": "Non-cancerous."}],
        "questionsForDoctor": ["How often should this thyroid nodule be monitored?"]
      }
    """.trimIndent()
    val specParsed = MedicalExplanationParser.parseExplanationJsonSafe(specJson, sampleSpecialisedExtraction()).getOrThrow()
    val specCat = specParsed.categoryExplanation as CategoryExplanation.Specialised
    assertTrue(specCat.sections.none { it.fieldKey == "gradingOrStaging" })
    assertEquals("Left thyroid nodule", specCat.sections.first { it.fieldKey == "specimenSource" }.originalText)
    assertEquals(1, specCat.sections.first { it.fieldKey == "specimenSource" }.provenance.pageNumber)
    assertNotNull(specCat.conclusionExplanation)

    // 5. CLINICAL
    val clinJson = """
      {
        "conciseSummary": "This outpatient prescription records essential hypertension and lists Amlodipine and Vitamin B12.",
        "documentOverviewExplanation": "An outpatient prescription summarizes recorded diagnoses and prescribed items.",
        "diagnoses": [{"diagnosisIndex": 0, "plainLanguageExplanation": "Recorded high blood pressure."}],
        "medications": [
          {"medicationIndex": 0, "drugName": "Amlodipine", "printedScheduleExplanation": "Printed as 5 mg once daily for 30 days after breakfast."},
          {"medicationIndex": 1, "drugName": "Vitamin B12", "printedScheduleExplanation": "Invented schedule should be dropped"}
        ],
        "allergies": [{"allergyIndex": 0, "plainLanguageExplanation": "Penicillin is listed as an allergy on this note."}],
        "dischargeInstructions": [{"instructionIndex": 0, "plainLanguageExplanation": "Keep a blood pressure log twice a week."}],
        "medicalTerms": [],
        "questionsForDoctor": ["How should I bring my home blood pressure log to the next visit?"]
      }
    """.trimIndent()
    val clinParsed = MedicalExplanationParser.parseExplanationJsonSafe(clinJson, sampleClinicalExtraction()).getOrThrow()
    val clinCat = clinParsed.categoryExplanation as CategoryExplanation.Clinical
    assertEquals("5 mg", clinCat.medications[0].sourceMedication.dosage)
    assertEquals("Once daily", clinCat.medications[0].sourceMedication.frequency)
    assertEquals("30 days", clinCat.medications[0].sourceMedication.duration)
    assertNotNull(clinCat.medications[0].printedScheduleExplanation)
    // Second medication had null dosage/frequency/duration/instructions -> must NOT retain AI-invented schedule
    assertNull(clinCat.medications[1].printedScheduleExplanation)
  }

  @Test
  fun verifyOriginalValuesPreservedAndInvalidIndexesOrKeysFallBackSafely() {
    val labExtraction = sampleLabExtraction()
    val jsonWithBadIndexesAndAlteredValues = """
      {
        "conciseSummary": "Summary grounded in extracted report.",
        "items": [
          {"itemIndex": 0, "originalName": "Hemoglobin", "originalValue": "999.0", "whatItMeasures": "Oxygen protein", "plainLanguageExplanation": "Duplicate should disqualify index 0."},
          {"itemIndex": 0, "originalName": "Hemoglobin", "whatItMeasures": "Oxygen protein duplicate", "plainLanguageExplanation": "Duplicate entry."},
          {"itemIndex": 1, "originalName": "Completely Different Test", "plainLanguageExplanation": "Mismatched name should disqualify index 1."},
          {"itemIndex": 99, "originalName": "Fake Parameter", "plainLanguageExplanation": "Out of range should be ignored."},
          {"itemIndex": -1, "originalName": "Negative Index", "plainLanguageExplanation": "Negative index should be ignored."}
        ]
      }
    """.trimIndent()

    val parsed = MedicalExplanationParser.parseExplanationJsonSafe(jsonWithBadIndexesAndAlteredValues, labExtraction).getOrThrow()
    val labCat = parsed.categoryExplanation as CategoryExplanation.Laboratory

    // Count must match exact Task 3C item count (no fabricated extra items)
    assertEquals(2, labCat.items.size)
    // Original values and provenance strictly preserved even when AI echoed 999.0
    assertEquals("10.8", labCat.items[0].sourceItem.originalValue)
    assertEquals("g/dL", labCat.items[0].sourceItem.originalUnit)
    assertEquals("12.0 - 15.5", labCat.items[0].sourceItem.referenceRange)
    assertEquals(1, labCat.items[0].sourceItem.provenance.pageNumber)
    // Duplicate index 0 and mismatched name index 1 both safely fall back to null explanation
    assertNull(labCat.items[0].plainLanguageExplanation)
    assertNull(labCat.items[1].plainLanguageExplanation)

    // Cardiology duplicate/unknown/mismatched field keys
    val cardioBadJson = """
      {
        "conciseSummary": "Cardiology summary.",
        "metrics": [
          {"fieldKey": "heartRateBpm", "originalValue": "140 bpm", "plainLanguageExplanation": "Mismatched value must be rejected."},
          {"fieldKey": "prIntervalMs", "originalValue": "158 ms", "plainLanguageExplanation": "First"},
          {"fieldKey": "prIntervalMs", "originalValue": "158 ms", "plainLanguageExplanation": "Duplicate key"},
          {"fieldKey": "unknownMetric", "originalValue": "123", "plainLanguageExplanation": "Unknown key ignored"}
        ]
      }
    """.trimIndent()
    val cardioParsed = MedicalExplanationParser.parseExplanationJsonSafe(cardioBadJson, sampleCardiologyExtraction()).getOrThrow()
    val cardioCat = cardioParsed.categoryExplanation as CategoryExplanation.Cardiology
    val hrMetric = cardioCat.metrics.first { it.fieldKey == "heartRateBpm" }
    val prMetric = cardioCat.metrics.first { it.fieldKey == "prIntervalMs" }
    assertEquals("74 bpm", hrMetric.originalValue)
    assertNull(hrMetric.plainLanguageExplanation)
    assertNull(prMetric.plainLanguageExplanation)
  }

  @Test
  fun verifyUncertainAndNotMentionedFindingsNeverBecomeConfirmed() {
    val uncertainLab = DocumentExtractionResult(
      category = MedicalDocumentCategory.LABORATORY,
      pageCount = 1,
      isMultiPageBatch = false,
      report = UniversalMedicalReport.Laboratory(
        ExtractedLaboratoryReport(
          labFacilityName = null,
          collectionDate = null,
          reportDate = null,
          items = listOf(
            ExtractedLabItem(
              originalName = "Serum Ferritin",
              originalValue = "illegible",
              originalUnit = null,
              referenceRange = null,
              laboratoryFlag = null,
              findingStatus = FindingStatus.UNCERTAIN_OR_UNREADABLE,
              provenance = FieldProvenance(1, "Ferritin [smudged]", 0.35f, ProvenanceAvailability.AVAILABLE)
            ),
            ExtractedLabItem(
              originalName = "Vitamin D",
              originalValue = null,
              originalUnit = null,
              referenceRange = null,
              laboratoryFlag = null,
              findingStatus = FindingStatus.NOT_MENTIONED,
              provenance = FieldProvenance.unavailable()
            )
          )
        )
      )
    )

    val aiAttemptingConfirmation = """
      {
        "conciseSummary": "Some items in this report could not be read clearly.",
        "items": [
          {"itemIndex": 0, "originalName": "Serum Ferritin", "whatItMeasures": "Iron stores", "plainLanguageExplanation": "Your ferritin is completely normal!"},
          {"itemIndex": 1, "originalName": "Vitamin D", "whatItMeasures": "Vitamin D level", "plainLanguageExplanation": "Your Vitamin D looks fine."}
        ]
      }
    """.trimIndent()

    val parsed = MedicalExplanationParser.parseExplanationJsonSafe(aiAttemptingConfirmation, uncertainLab).getOrThrow()
    val items = (parsed.categoryExplanation as CategoryExplanation.Laboratory).items

    assertEquals(FindingStatus.UNCERTAIN_OR_UNREADABLE, items[0].sourceItem.findingStatus)
    assertNull(items[0].plainLanguageExplanation)
    assertNotNull(items[0].uncertaintyNote)

    assertEquals(FindingStatus.NOT_MENTIONED, items[1].sourceItem.findingStatus)
    assertNull(items[1].plainLanguageExplanation)
    assertNotNull(items[1].uncertaintyNote)
    assertTrue(parsed.uncertaintyNotes.size >= 2)
  }

  @Test
  fun verifyMedicationSafetyRejectsPrescribingAndDosageChangeAdvice() {
    val clinical = sampleClinicalExtraction()
    val unsafeMedJson = """
      {
        "conciseSummary": "Prescription summary.",
        "medications": [
          {
            "medicationIndex": 0,
            "drugName": "Amlodipine",
            "printedScheduleExplanation": "You can stop taking this or double the dose if blood pressure stays high; prescribed to treat chest pain."
          }
        ]
      }
    """.trimIndent()

    val parsed = MedicalExplanationParser.parseExplanationJsonSafe(unsafeMedJson, clinical).getOrThrow()
    val clinCat = parsed.categoryExplanation as CategoryExplanation.Clinical

    // Unsafe recommendation must be dropped; printed fields & safety note preserved
    assertNull(clinCat.medications[0].printedScheduleExplanation)
    assertEquals("5 mg", clinCat.medications[0].sourceMedication.dosage)
    assertEquals("Once daily", clinCat.medications[0].sourceMedication.frequency)
    assertEquals(ExplanationConstants.MEDICATION_SAFETY_NOTE, clinCat.medications[0].safetyNote)
  }

  @Test
  fun verifyUnidentifiedRejectionMalformedJsonConsentAndErrorHandling() {
    // 1. Malformed & empty JSON fail safely
    assertTrue(MedicalExplanationParser.parseExplanationJsonSafe("", sampleLabExtraction()).isFailure)
    assertTrue(MedicalExplanationParser.parseExplanationJsonSafe("{not_json", sampleLabExtraction()).isFailure)

    // 2. Unidentified report rejected by parser & ViewModel without invoking explainer
    val unidentified = DocumentExtractionResult(
      category = MedicalDocumentCategory.UNCERTAIN,
      pageCount = 1,
      isMultiPageBatch = false,
      report = UniversalMedicalReport.Unidentified("Grocery receipt", "Not a medical report")
    )
    assertTrue(MedicalExplanationParser.parseExplanationJsonSafe("{\"conciseSummary\":\"x\"}", unidentified).isFailure)

    val consentGrantedStore = InMemoryExplanationConsentStore(initialUid = "test-user-uid")
    val explainer = FakeReportExplainer { _, _, _ ->
      Result.failure(AssertionError("Explainer must not be called for Unidentified documents"))
    }
    val vmUnidentified = createViewModel(consentGrantedStore, explainer)
    vmUnidentified.requestExplanation(unidentified)
    assertEquals(0, explainer.callCount)
    assertTrue(vmUnidentified.uiState.value is ReportExplanationState.Error)
    assertFalse((vmUnidentified.uiState.value as ReportExplanationState.Error).isRetryable)

    // 3. Consent enforcement: unconsented request NEVER invokes explainer
    val noConsentStore = InMemoryExplanationConsentStore(initialUid = null)
    val lab = sampleLabExtraction()
    val validParsed = MedicalExplanationParser.parseExplanationJson(
      "{\"conciseSummary\":\"Hemoglobin is 10.8 g/dL.\"}",
      lab
    )
    val gatedExplainer = FakeReportExplainer { _, _, _ -> Result.success(validParsed) }
    val vmConsent = createViewModel(noConsentStore, gatedExplainer)

    vmConsent.requestExplanation(lab)
    assertEquals(0, gatedExplainer.callCount)
    assertTrue(vmConsent.uiState.value is ReportExplanationState.WaitingForConsent)
    assertTrue(vmConsent.isConsentDialogVisible.value)

    // Dismissing consent returns to Idle without calling explainer
    vmConsent.dismissConsentDialog()
    assertEquals(0, gatedExplainer.callCount)
    assertTrue(vmConsent.uiState.value is ReportExplanationState.Idle)
    assertFalse(vmConsent.isConsentDialogVisible.value)

    // Confirming consent stores consent for the active UID and runs explainer
    vmConsent.confirmConsentAndExplain(lab)
    assertTrue(noConsentStore.hasGrantedExplanationConsent("test-user-uid"))
    assertEquals(1, gatedExplainer.callCount)
    assertTrue(vmConsent.uiState.value is ReportExplanationState.Success)

    // 4. Retryable timeout/network failure and recovery + document change / sign-out reset
    val flakyExplainer = FakeReportExplainer { callIndex, _, _ ->
      if (callIndex == 1) {
        Result.failure(IOException("Request timed out after 60s"))
      } else {
        Result.success(validParsed)
      }
    }
    val vmRetry = createViewModel(InMemoryExplanationConsentStore(initialUid = "test-user-uid"), flakyExplainer)
    vmRetry.requestExplanation(lab)
    val errorState = vmRetry.uiState.value as ReportExplanationState.Error
    assertTrue(errorState.isRetryable)
    assertTrue(errorState.message.contains("timed out"))

    vmRetry.retryExplanation(lab)
    assertEquals(2, flakyExplainer.callCount)
    assertTrue(vmRetry.uiState.value is ReportExplanationState.Success)

    // Switching to another document resets prior explanation state cleanly
    vmRetry.syncForExtractionResult(sampleRadiologyExtraction())
    assertTrue(vmRetry.uiState.value is ReportExplanationState.Idle)

    // Clearing (e.g. remove document) resets state cleanly
    vmRetry.requestExplanation(sampleRadiologyExtraction())
    assertTrue(vmRetry.uiState.value is ReportExplanationState.Success)
    vmRetry.clear()
    assertTrue(vmRetry.uiState.value is ReportExplanationState.Idle)
  }

  @Test
  fun verifyTimeoutExceptionDoesNotCrashParserOrExplainerContract() {
    val consent = InMemoryExplanationConsentStore(initialUid = "test-user-uid")
    val timeoutExplainer = FakeReportExplainer { _, _, _ ->
      Result.failure(Exception("Explanation generation timed out.", mockTimeoutCancellationException()))
    }
    val vm = createViewModel(consent, timeoutExplainer)
    vm.requestExplanation(sampleLabExtraction())
    val state = vm.uiState.value
    assertTrue(state is ReportExplanationState.Error)
    assertTrue((state as ReportExplanationState.Error).isRetryable)
  }

  private fun mockTimeoutCancellationException(): Throwable? =
    try {
      kotlinx.coroutines.runBlocking {
        kotlinx.coroutines.withTimeout(1L) {
          kotlinx.coroutines.delay(50L)
        }
      }
      null
    } catch (e: TimeoutCancellationException) {
      e
    }

  @Test
  fun verifyExtractionResultScreenShowsExplainButtonAndTriggersNavigation() {
    val lab = sampleLabExtraction()
    var explainClicked = false

    composeTestRule.setContent {
      MyApplicationTheme {
        ExtractionResultScreen(
          extractionResult = lab,
          fileName = "CBC_2026.pdf",
          patientName = "Priya Sharma",
          onUploadAnotherClick = {},
          onNavigateBack = {},
          onExplainReportClick = { explainClicked = true }
        )
      }
    }
    composeTestRule.waitForIdle()
    val extractionList = composeTestRule.onNodeWithTag("extraction_result_list")
    extractionList.performScrollToNode(hasText("Explain in Simple Language"))
    composeTestRule.onNodeWithTag("btn_explain_report").assertIsDisplayed().performClick()
    assertTrue(explainClicked)
  }

  @Test
  fun verifyReportExplanationScreenDisplaysDisclaimerPreservedValuesAndProvenance() {
    val lab = sampleLabExtraction()
    val explanation = MedicalExplanationParser.parseExplanationJson(
      """
        {
          "conciseSummary": "Your Hemoglobin is 10.8 g/dL, which is flagged L below 12.0 - 15.5.",
          "medicalTerms": [{"term": "Hemoglobin", "simpleDefinition": "Oxygen-carrying protein in red blood cells."}],
          "questionsForDoctor": ["What follow-up test would help check my Hemoglobin?"],
          "items": [
            {"itemIndex": 0, "originalName": "Hemoglobin", "whatItMeasures": "Red blood cell oxygen carrier.", "plainLanguageExplanation": "Printed below the reference range."}
          ]
        }
      """.trimIndent(),
      lab
    )

    composeTestRule.setContent {
      MyApplicationTheme {
        ReportExplanationScreen(
          explanationState = ReportExplanationState.Success(explanation),
          isConsentDialogVisible = false,
          fileName = "CBC_2026.pdf",
          patientName = "Priya Sharma",
          onConfirmConsent = {},
          onDismissConsent = {},
          onRequestExplanation = {},
          onRetryClick = {},
          onNavigateBack = {}
        )
      }
    }
    composeTestRule.waitForIdle()

    composeTestRule.onNodeWithTag("explanation_safety_disclaimer").assertIsDisplayed()
    composeTestRule.onNodeWithText("not medically verified", substring = true, ignoreCase = true).assertIsDisplayed()

    val explainList = composeTestRule.onNodeWithTag("explanation_result_list")
    explainList.performScrollToNode(hasText("10.8 g/dL"))
    composeTestRule.onNodeWithText("10.8 g/dL").assertIsDisplayed()
    composeTestRule.onNodeWithText("12.0 - 15.5").assertIsDisplayed()

    explainList.performScrollToNode(hasText("Page 1 • AI extraction confidence: 96%"))
    composeTestRule.onNodeWithText("Page 1 • AI extraction confidence: 96%").assertIsDisplayed()

    explainList.performScrollToNode(hasText("Questions to Ask Your Doctor"))
    composeTestRule.onNodeWithText("What follow-up test would help check my Hemoglobin?").assertIsDisplayed()
  }

  @Test
  fun verifyReportExplanationConsentDialogRequiresExplicitConfirmation() {
    var confirmed = false
    composeTestRule.setContent {
      MyApplicationTheme {
        ReportExplanationScreen(
          explanationState = ReportExplanationState.WaitingForConsent,
          isConsentDialogVisible = true,
          fileName = "CBC_2026.pdf",
          patientName = "Priya Sharma",
          onConfirmConsent = { confirmed = true },
          onDismissConsent = {},
          onRequestExplanation = {},
          onRetryClick = {},
          onNavigateBack = {}
        )
      }
    }
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag("dialog_explanation_consent").assertIsDisplayed()
    composeTestRule.onNodeWithText("not medically verified", substring = true, ignoreCase = true).assertIsDisplayed()
    composeTestRule.onNodeWithTag("btn_explanation_consent_confirm").assertIsDisplayed().performClick()
    assertTrue(confirmed)
  }

  @Test
  fun fix1_consentScopedToUidAndDoesNotLeakAcrossLogoutUserSwitchOrAccountDeletion() {
    val app = ApplicationProvider.getApplicationContext<Application>()
    // Seed a legacy global unscoped key to verify migration purges it and never grants consent
    app.getSharedPreferences("nexzy_ai_explanation_consent_prefs", android.content.Context.MODE_PRIVATE)
      .edit()
      .putBoolean("ai_plain_language_explanation_consent_v1", true)
      .commit()

    val consentStore = SharedPreferencesExplanationConsentStore(app)
    consentStore.clearAllConsents()

    val lab = sampleLabExtraction()
    val validParsed = MedicalExplanationParser.parseExplanationJson(
      "{\"conciseSummary\":\"Hemoglobin is 10.8 g/dL.\"}",
      lab
    )
    val explainer = FakeReportExplainer { _, _, _ -> Result.success(validParsed) }
    var activeUid: String? = "uid_user_a"

    val vmSession1 = createViewModel(consentStore, explainer, uidProvider = { activeUid })
    // User A must be prompted initially (legacy global key must not grant consent)
    vmSession1.requestExplanation(lab)
    assertEquals(0, explainer.callCount)
    assertTrue(vmSession1.uiState.value is ReportExplanationState.WaitingForConsent)

    // User A grants consent
    vmSession1.confirmConsentAndExplain(lab)
    assertEquals(1, explainer.callCount)
    assertTrue(consentStore.hasGrantedExplanationConsent("uid_user_a"))
    assertFalse(consentStore.hasGrantedExplanationConsent("uid_user_b"))

    // Ordinary app restart for the same authenticated User A preserves User A's consent
    val vmRestartSameUser = createViewModel(consentStore, explainer, uidProvider = { activeUid })
    vmRestartSameUser.requestExplanation(lab)
    assertEquals(2, explainer.callCount)
    assertTrue(vmRestartSameUser.uiState.value is ReportExplanationState.Success)

    // User A logs out -> in-memory state cleared & User A's stored consent invalidated
    vmRestartSameUser.clearSessionAndRevokeConsent("uid_user_a")
    assertFalse(consentStore.hasGrantedExplanationConsent("uid_user_a"))
    assertTrue(vmRestartSameUser.uiState.value is ReportExplanationState.Idle)

    // User B signs in on the same device -> must be asked for consent
    activeUid = "uid_user_b"
    vmRestartSameUser.syncUserSession("uid_user_b")
    vmRestartSameUser.requestExplanation(lab)
    assertEquals(2, explainer.callCount)
    assertTrue(vmRestartSameUser.uiState.value is ReportExplanationState.WaitingForConsent)
    assertTrue(vmRestartSameUser.isConsentDialogVisible.value)

    // Declining consent for User B prevents Firebase AI call
    vmRestartSameUser.dismissConsentDialog()
    assertEquals(2, explainer.callCount)
    assertTrue(vmRestartSameUser.uiState.value is ReportExplanationState.Idle)

    // User B grants consent, then deletes account -> User B's consent is invalidated and cannot be reused
    vmRestartSameUser.confirmConsentAndExplain(lab)
    assertEquals(3, explainer.callCount)
    assertTrue(consentStore.hasGrantedExplanationConsent("uid_user_b"))

    vmRestartSameUser.clearSessionAndRevokeConsent("uid_user_b")
    assertFalse(consentStore.hasGrantedExplanationConsent("uid_user_b"))

    // Attempting to reuse old consent for deleted account UID fails and prompts again
    val vmPostDeletion = createViewModel(consentStore, explainer, uidProvider = { "uid_user_b" })
    vmPostDeletion.requestExplanation(lab)
    assertEquals(3, explainer.callCount)
    assertTrue(vmPostDeletion.uiState.value is ReportExplanationState.WaitingForConsent)

    // Unauthenticated session (uid == null) never persists consent or calls explainer
    val vmNoUser = createViewModel(consentStore, explainer, uidProvider = { null })
    vmNoUser.requestExplanation(lab)
    vmNoUser.confirmConsentAndExplain(lab)
    assertEquals(3, explainer.callCount)
    assertTrue(vmNoUser.uiState.value is ReportExplanationState.Error)
  }

  @Test
  fun fix2_cardiologyAndSpecialisedConclusionsRejectBypassAndRootFallback() {
    val cardio = sampleCardiologyExtraction()
    val cardioReport = (cardio.report as UniversalMedicalReport.Cardiology).report

    // Cardiology 1: Valid conclusion entry via validated fieldKey
    val cardioValid = MedicalExplanationParser.parseExplanationJsonSafe(
      """
        {
          "conciseSummary": "ECG summary.",
          "metrics": [
            {"fieldKey": "formalConclusion", "originalValue": "Normal sinus rhythm without acute ischemic changes.", "plainLanguageExplanation": "Validated ECG conclusion explanation."}
          ]
        }
      """.trimIndent(),
      cardio
    ).getOrThrow().categoryExplanation as CategoryExplanation.Cardiology
    assertEquals("Validated ECG conclusion explanation.", cardioValid.conclusionExplanation)
    assertEquals(cardioReport.formalConclusion, cardioValid.sourceReport.formalConclusion)

    // Cardiology 2: Duplicate conclusion key with root conclusionExplanation present -> must remain null
    val cardioDuplicate = MedicalExplanationParser.parseExplanationJsonSafe(
      """
        {
          "conciseSummary": "ECG summary.",
          "metrics": [
            {"fieldKey": "formalConclusion", "originalValue": "Normal sinus rhythm without acute ischemic changes.", "plainLanguageExplanation": "First"},
            {"fieldKey": "formalConclusion", "originalValue": "Normal sinus rhythm without acute ischemic changes.", "plainLanguageExplanation": "Duplicate"}
          ],
          "conclusionExplanation": "Unvalidated root fallback must NEVER be used"
        }
      """.trimIndent(),
      cardio
    ).getOrThrow().categoryExplanation as CategoryExplanation.Cardiology
    assertNull(cardioDuplicate.conclusionExplanation)
    assertEquals(cardioReport.formalConclusion, cardioDuplicate.sourceReport.formalConclusion)
    assertEquals(1, cardioDuplicate.sourceReport.provenance.pageNumber)

    // Cardiology 3: Mismatched originalValue with root fallback present -> must remain null
    val cardioMismatch = MedicalExplanationParser.parseExplanationJsonSafe(
      """
        {
          "conciseSummary": "ECG summary.",
          "metrics": [
            {"fieldKey": "formalConclusion", "originalValue": "Atrial fibrillation", "plainLanguageExplanation": "Wrong condition explanation"}
          ],
          "conclusionExplanation": "Unvalidated root fallback must NEVER be used"
        }
      """.trimIndent(),
      cardio
    ).getOrThrow().categoryExplanation as CategoryExplanation.Cardiology
    assertNull(cardioMismatch.conclusionExplanation)
    assertEquals("Normal sinus rhythm without acute ischemic changes.", cardioMismatch.sourceReport.formalConclusion)

    // Cardiology 4: Missing conclusion entry with root fallback present -> must remain null
    val cardioMissing = MedicalExplanationParser.parseExplanationJsonSafe(
      """
        {
          "conciseSummary": "ECG summary.",
          "metrics": [
            {"fieldKey": "heartRateBpm", "originalValue": "74 bpm", "plainLanguageExplanation": "Heart rate is 74 beats per minute."}
          ],
          "conclusionExplanation": "Unvalidated root fallback must NEVER be used"
        }
      """.trimIndent(),
      cardio
    ).getOrThrow().categoryExplanation as CategoryExplanation.Cardiology
    assertNull(cardioMissing.conclusionExplanation)

    // Specialised
    val specialised = sampleSpecialisedExtraction()
    val specialisedReport = (specialised.report as UniversalMedicalReport.Specialised).report

    // Specialised 1: Valid conclusion entry
    val specValid = MedicalExplanationParser.parseExplanationJsonSafe(
      """
        {
          "conciseSummary": "Biopsy summary.",
          "sections": [
            {"fieldKey": "diagnosticConclusion", "originalText": "Benign follicular nodule (Bethesda Category II).", "plainLanguageExplanation": "Validated pathology conclusion explanation."}
          ]
        }
      """.trimIndent(),
      specialised
    ).getOrThrow().categoryExplanation as CategoryExplanation.Specialised
    assertEquals("Validated pathology conclusion explanation.", specValid.conclusionExplanation)
    assertEquals(specialisedReport.diagnosticConclusion, specValid.sourceReport.diagnosticConclusion)

    // Specialised 2: Duplicate conclusion key with root fallback -> must remain null
    val specDuplicate = MedicalExplanationParser.parseExplanationJsonSafe(
      """
        {
          "conciseSummary": "Biopsy summary.",
          "sections": [
            {"fieldKey": "diagnosticConclusion", "originalText": "Benign follicular nodule (Bethesda Category II).", "plainLanguageExplanation": "First"},
            {"fieldKey": "diagnosticConclusion", "originalText": "Benign follicular nodule (Bethesda Category II).", "plainLanguageExplanation": "Duplicate"}
          ],
          "conclusionExplanation": "Unvalidated root fallback must NEVER be used"
        }
      """.trimIndent(),
      specialised
    ).getOrThrow().categoryExplanation as CategoryExplanation.Specialised
    assertNull(specDuplicate.conclusionExplanation)
    assertEquals(specialisedReport.diagnosticConclusion, specDuplicate.sourceReport.diagnosticConclusion)
    assertEquals(2, specDuplicate.sourceReport.provenance.pageNumber)

    // Specialised 3: Mismatched originalText with root fallback -> must remain null
    val specMismatch = MedicalExplanationParser.parseExplanationJsonSafe(
      """
        {
          "conciseSummary": "Biopsy summary.",
          "sections": [
            {"fieldKey": "diagnosticConclusion", "originalText": "Malignant carcinoma", "plainLanguageExplanation": "Fabricated malignancy explanation"}
          ],
          "conclusionExplanation": "Unvalidated root fallback must NEVER be used"
        }
      """.trimIndent(),
      specialised
    ).getOrThrow().categoryExplanation as CategoryExplanation.Specialised
    assertNull(specMismatch.conclusionExplanation)
    assertEquals("Benign follicular nodule (Bethesda Category II).", specMismatch.sourceReport.diagnosticConclusion)

    // Specialised 4: Missing conclusion entry with root fallback -> must remain null
    val specMissing = MedicalExplanationParser.parseExplanationJsonSafe(
      """
        {
          "conciseSummary": "Biopsy summary.",
          "sections": [
            {"fieldKey": "specimenSource", "originalText": "Left thyroid nodule", "plainLanguageExplanation": "Sample taken from left thyroid."}
          ],
          "conclusionExplanation": "Unvalidated root fallback must NEVER be used"
        }
      """.trimIndent(),
      specialised
    ).getOrThrow().categoryExplanation as CategoryExplanation.Specialised
    assertNull(specMissing.conclusionExplanation)
  }

  @Test
  fun fix3_ambiguousMedicationAbbreviationsCannotBeGuessedWithoutExplicitSourceMeaning() {
    val abbreviationClinical = DocumentExtractionResult(
      category = MedicalDocumentCategory.CLINICAL,
      pageCount = 1,
      isMultiPageBatch = false,
      report = UniversalMedicalReport.Clinical(
        ExtractedClinicalDocument(
          documentSubtype = "Outpatient Prescription",
          facilityOrDoctorName = "Dr. R. Nair",
          primaryDiagnoses = emptyList(),
          surgicalProcedures = emptyList(),
          prescribedMedications = listOf(
            ExtractedMedication("DrugA", "10 mg", "OD", "14 days", null, FieldProvenance(1, "Tab DrugA 10 mg OD x 14d", 0.9f, ProvenanceAvailability.AVAILABLE)),
            ExtractedMedication("DrugB", "500 mg", "BD", "5 days", null, FieldProvenance(1, "Tab DrugB 500 mg BD x 5d", 0.9f, ProvenanceAvailability.AVAILABLE)),
            ExtractedMedication("DrugC", "250 mg", "TID", "7 days", null, FieldProvenance(1, "Tab DrugC 250 mg TID x 7d", 0.9f, ProvenanceAvailability.AVAILABLE)),
            ExtractedMedication("DrugD", "400 mg", "PRN", null, null, FieldProvenance(1, "Tab DrugD 400 mg PRN", 0.9f, ProvenanceAvailability.AVAILABLE)),
            ExtractedMedication("DrugE", "5 mg", "HS", "10 days", null, FieldProvenance(1, "Tab DrugE 5 mg HS x 10d", 0.9f, ProvenanceAvailability.AVAILABLE)),
            ExtractedMedication("DrugF", "20 mg", "OD (once daily)", "30 days", null, FieldProvenance(1, "Tab DrugF 20 mg OD (once daily)", 0.95f, ProvenanceAvailability.AVAILABLE))
          ),
          allergiesRecorded = emptyList(),
          dischargeInstructions = emptyList(),
          followUpDate = null
        )
      )
    )

    val aiExpandingAbbreviations = """
      {
        "conciseSummary": "Prescription lists DrugA once daily and DrugB twice daily.",
        "medicalTerms": [
          {"term": "OD", "simpleDefinition": "Means once daily."},
          {"term": "Prescription", "simpleDefinition": "A written medication order from a clinician."}
        ],
        "medications": [
          {"medicationIndex": 0, "drugName": "DrugA", "printedScheduleExplanation": "Take 10 mg once daily for 14 days."},
          {"medicationIndex": 1, "drugName": "DrugB", "printedScheduleExplanation": "Take 500 mg twice daily for 5 days."},
          {"medicationIndex": 2, "drugName": "DrugC", "printedScheduleExplanation": "Take 250 mg three times daily for 7 days."},
          {"medicationIndex": 3, "drugName": "DrugD", "printedScheduleExplanation": "Take 400 mg as needed."},
          {"medicationIndex": 4, "drugName": "DrugE", "printedScheduleExplanation": "Take 5 mg at bedtime for 10 days."},
          {"medicationIndex": 5, "drugName": "DrugF", "printedScheduleExplanation": "Printed as 20 mg OD (once daily) for 30 days."}
        ]
      }
    """.trimIndent()

    val parsed = MedicalExplanationParser.parseExplanationJsonSafe(aiExpandingAbbreviations, abbreviationClinical).getOrThrow()
    val clin = parsed.categoryExplanation as CategoryExplanation.Clinical

    // All 5 unexpanded abbreviations (OD, BD, TID, PRN, HS) must have unsafe expansions rejected
    assertNull(clin.medications[0].printedScheduleExplanation)
    assertNull(clin.medications[1].printedScheduleExplanation)
    assertNull(clin.medications[2].printedScheduleExplanation)
    assertNull(clin.medications[3].printedScheduleExplanation)
    assertNull(clin.medications[4].printedScheduleExplanation)
    // Explicitly expanded in source ("OD (once daily)") is preserved
    assertNotNull(clin.medications[5].printedScheduleExplanation)

    // Original printed Task 3C medication fields remain completely unchanged
    assertEquals("OD", clin.medications[0].sourceMedication.frequency)
    assertEquals("BD", clin.medications[1].sourceMedication.frequency)
    assertEquals("TID", clin.medications[2].sourceMedication.frequency)
    assertEquals("PRN", clin.medications[3].sourceMedication.frequency)
    assertEquals("HS", clin.medications[4].sourceMedication.frequency)

    // Unexpanded abbreviation definition ("OD": "Means once daily") is filtered out while benign term stays
    assertTrue(parsed.medicalTerms.none { it.term == "OD" })
    assertTrue(parsed.medicalTerms.any { it.term == "Prescription" })

    // Preserving abbreviation verbatim without guessing is allowed
    val verbatimJson = """
      {
        "conciseSummary": "Medication list printed as extracted.",
        "medications": [
          {"medicationIndex": 0, "drugName": "DrugA", "printedScheduleExplanation": "Printed as 10 mg with abbreviation OD for 14 days."}
        ]
      }
    """.trimIndent()
    val verbatimParsed = MedicalExplanationParser.parseExplanationJsonSafe(verbatimJson, abbreviationClinical).getOrThrow()
    val verbatimClin = verbatimParsed.categoryExplanation as CategoryExplanation.Clinical
    assertEquals("Printed as 10 mg with abbreviation OD for 14 days.", verbatimClin.medications[0].printedScheduleExplanation)
  }

  @Test
  fun fix4_medicationSafetyFilteringAcrossAllFieldsPreservesSafeWarningsAndRejectsUnsafeAdvice() {
    val clinical = sampleClinicalExtraction()

    // 1. Unsafe paraphrases ("halt taking", "taper off", "omit a tablet", "take two pills", "indicated for",
    //    unsupported purpose with arbitrary condition, plus unsafe advice in summary, overview, diagnoses,
    //    discharge instructions, medicalTerms, and questionsForDoctor)
    val unsafeAcrossFieldsJson = """
      {
        "conciseSummary": "You can halt taking Amlodipine or increase the dose if symptoms continue.",
        "documentOverviewExplanation": "Notes indicate you should taper off this prescription next week.",
        "diagnoses": [
          {"diagnosisIndex": 0, "plainLanguageExplanation": "Recorded high blood pressure; take two pills if readings rise."}
        ],
        "medications": [
          {"medicationIndex": 0, "drugName": "Amlodipine", "printedScheduleExplanation": "You may omit a tablet or use this as indicated for migraines."}
        ],
        "dischargeInstructions": [
          {"instructionIndex": 0, "plainLanguageExplanation": "This drug is used to treat rheumatoid arthritis and gout."}
        ],
        "medicalTerms": [
          {"term": "Amlodipine", "simpleDefinition": "You should taper off gradually or take two pills at night."},
          {"term": "Hypertension", "simpleDefinition": "Persistently elevated blood pressure in the arteries."}
        ],
        "questionsForDoctor": [
          "Should I halt taking Amlodipine or omit a tablet before my appointment?",
          "Can I take two pills instead of one if my blood pressure is elevated?",
          "How should I bring my home blood pressure log to the next visit?"
        ]
      }
    """.trimIndent()

    val unsafeParsed = MedicalExplanationParser.parseExplanationJsonSafe(unsafeAcrossFieldsJson, clinical).getOrThrow()
    val unsafeClin = unsafeParsed.categoryExplanation as CategoryExplanation.Clinical

    // Unsafe summary & overview rejected without altering Task 3C facts
    assertFalse(unsafeParsed.conciseSummary.contains("halt taking", ignoreCase = true))
    assertNull(unsafeClin.documentOverviewExplanation)
    assertNull(unsafeClin.diagnoses[0].plainLanguageExplanation)
    assertNull(unsafeClin.medications[0].printedScheduleExplanation)
    assertNull(unsafeClin.dischargeInstructions[0].plainLanguageExplanation)
    assertTrue(unsafeParsed.medicalTerms.none { it.term == "Amlodipine" })
    assertTrue(unsafeParsed.medicalTerms.any { it.term == "Hypertension" })
    assertEquals(listOf("How should I bring my home blood pressure log to the next visit?"), unsafeParsed.questionsForDoctor)

    // Original Task 3C medication instructions remain completely untouched
    assertEquals("Take after breakfast", unsafeClin.medications[0].sourceMedication.instructions)
    assertEquals("5 mg", unsafeClin.medications[0].sourceMedication.dosage)

    // 2. Specific paraphrase & unsupported-purpose unit checks + safe warning preservation
    assertTrue(MedicalExplanationParser.containsUnsafeMedicationAdvice("Please halt taking this medicine immediately."))
    assertTrue(MedicalExplanationParser.containsUnsafeMedicationAdvice("You should taper off slowly over 2 weeks."))
    assertTrue(MedicalExplanationParser.containsUnsafeMedicationAdvice("You can omit a tablet in the evening."))
    assertTrue(MedicalExplanationParser.containsUnsafeMedicationAdvice("Take two pills before lunch."))
    assertTrue(MedicalExplanationParser.containsUnsafeMedicationAdvice("This medicine is indicated for psoriasis."))
    assertTrue(MedicalExplanationParser.containsUnsafeMedicationAdvice("It is used for lowering uric acid in gout."))

    // Safe warning MUST be preserved, along with harmless educational explanation
    val safeWarningJson = """
      {
        "conciseSummary": "This prescription lists Amlodipine 5 mg once daily for 30 days. Always consult your doctor before stopping or changing your medication.",
        "medications": [
          {
            "medicationIndex": 0,
            "drugName": "Amlodipine",
            "printedScheduleExplanation": "Printed as 5 mg once daily for 30 days after breakfast. Do not stop taking this medicine without consulting your doctor."
          }
        ],
        "medicalTerms": [
          {"term": "Essential Hypertension", "simpleDefinition": "High blood pressure with no single identifiable secondary cause."}
        ],
        "questionsForDoctor": [
          "Should I review this schedule at my follow-up visit?"
        ]
      }
    """.trimIndent()

    val safeParsed = MedicalExplanationParser.parseExplanationJsonSafe(safeWarningJson, clinical).getOrThrow()
    val safeClin = safeParsed.categoryExplanation as CategoryExplanation.Clinical
    assertEquals(
      "Printed as 5 mg once daily for 30 days after breakfast. Do not stop taking this medicine without consulting your doctor.",
      safeClin.medications[0].printedScheduleExplanation
    )
    assertTrue(safeParsed.conciseSummary.contains("Always consult your doctor before stopping"))
    assertEquals(1, safeParsed.medicalTerms.size)
    assertEquals(1, safeParsed.questionsForDoctor.size)
  }

  @Test
  fun fix1_hardening_approvedFourLanguagesResolveSafelyWithoutOverwritingUserProfile() {
    val hindiProfile = UserProfile(
      uid = "uid_lang_hi",
      email = "priya@example.com",
      displayName = "Priya",
      preferredLanguage = "hi"
    )
    val bengaliProfile = hindiProfile.copy(uid = "uid_lang_bn", preferredLanguage = "bn")
    val marathiProfile = hindiProfile.copy(uid = "uid_lang_mr", preferredLanguage = "mr")

    // Resolver supports the four approved launch languages and falls back to English for null/blank/unsupported codes
    assertEquals("English", resolveExplanationLanguage(null))
    assertEquals("English", resolveExplanationLanguage(""))
    assertEquals("English", resolveExplanationLanguage("en"))
    assertEquals("Hindi", resolveExplanationLanguage(hindiProfile.preferredLanguage))
    assertEquals("Hindi", resolveExplanationLanguage("Hindi"))
    assertEquals("Bengali", resolveExplanationLanguage(bengaliProfile.preferredLanguage))
    assertEquals("Bengali", resolveExplanationLanguage("Bengali"))
    assertEquals("Marathi", resolveExplanationLanguage(marathiProfile.preferredLanguage))
    assertEquals("Marathi", resolveExplanationLanguage("Marathi"))
    assertEquals("English", resolveExplanationLanguage("ta"))
    assertEquals("English", resolveExplanationLanguage("pa"))
    assertEquals("English", resolveExplanationLanguage("gu"))
    assertEquals("English", resolveExplanationLanguage("unknown"))

    // Original UserProfile language fields remain preserved (never deleted or overwritten)
    assertEquals("hi", hindiProfile.preferredLanguage)
    assertEquals("bn", bengaliProfile.preferredLanguage)
    assertEquals("mr", marathiProfile.preferredLanguage)

    // ViewModel and Parser safely resolve approved languages and fall back unsupported languages to English
    val lab = sampleLabExtraction()
    val explainer = FakeReportExplainer { _, extraction, lang ->
      val parsed = MedicalExplanationParser.parseExplanationJson(
        "{\"conciseSummary\":\"Hemoglobin summary in $lang.\"}",
        extraction,
        language = lang
      )
      Result.success(parsed)
    }
    val consentStore = InMemoryExplanationConsentStore()
    val vm = createViewModel(consentStore, explainer, uidProvider = { hindiProfile.uid })

    vm.requestExplanation(lab, language = "Hindi")
    assertTrue(vm.uiState.value is ReportExplanationState.WaitingForConsent)

    vm.confirmConsentAndExplain(lab, language = "Hindi")
    assertEquals(listOf("Hindi"), explainer.observedLanguages)
    val firstResult = (vm.uiState.value as ReportExplanationState.Success).explanation
    assertEquals("Hindi", firstResult.language)

    vm.retryExplanation(lab, language = "Bengali")
    assertEquals(listOf("Hindi", "Bengali"), explainer.observedLanguages)
    val retryResult = (vm.uiState.value as ReportExplanationState.Success).explanation
    assertEquals("Bengali", retryResult.language)

    vm.retryExplanation(lab, language = "ta")
    assertEquals(listOf("Hindi", "Bengali", "English"), explainer.observedLanguages)
    val fallbackResult = (vm.uiState.value as ReportExplanationState.Success).explanation
    assertEquals("English", fallbackResult.language)

    // Parser resolves supported languages cleanly and enforces English for unsupported languages
    val directParsed = MedicalExplanationParser.parseExplanationJsonSafe(
      "{\"conciseSummary\":\"Summary.\"}",
      lab,
      language = "Marathi"
    ).getOrThrow()
    assertEquals("Marathi", directParsed.language)

    val directUnsupported = MedicalExplanationParser.parseExplanationJsonSafe(
      "{\"conciseSummary\":\"Summary.\"}",
      lab,
      language = "gu"
    ).getOrThrow()
    assertEquals("English", directUnsupported.language)

    // Original Task 3C medical values, units, reference ranges, and provenance remain untranslated & untouched
    val labItems = (retryResult.categoryExplanation as CategoryExplanation.Laboratory).items
    assertEquals("10.8", labItems[0].sourceItem.originalValue)
    assertEquals("g/dL", labItems[0].sourceItem.originalUnit)
    assertEquals("12.0 - 15.5", labItems[0].sourceItem.referenceRange)
    assertEquals(1, labItems[0].sourceItem.provenance.pageNumber)
    assertEquals("Hemoglobin 10.8 g/dL (12.0 - 15.5) L", labItems[0].sourceItem.provenance.sourceSnippet)

    // Current Stage 1 UI consent dialog still reflects Stage 1 scope before Stage 2+ UI localization
    composeTestRule.setContent {
      MyApplicationTheme {
        ReportExplanationScreen(
          explanationState = ReportExplanationState.WaitingForConsent,
          isConsentDialogVisible = true,
          fileName = "CBC_2026.pdf",
          patientName = "Priya Sharma",
          onConfirmConsent = {},
          onDismissConsent = {},
          onRequestExplanation = {},
          onRetryClick = {},
          onNavigateBack = {}
        )
      }
    }
    composeTestRule.waitForIdle()
    composeTestRule.onAllNodesWithText("English only", substring = true, ignoreCase = true)
      .onFirst()
      .assertIsDisplayed()
  }

  @Test
  fun fix2_hardening_qdRulePerMedicationIsolationAndUnrelatedNightNarrativesPreserved() {
    val clinicalWithQdAndHs = DocumentExtractionResult(
      category = MedicalDocumentCategory.CLINICAL,
      pageCount = 1,
      isMultiPageBatch = false,
      report = UniversalMedicalReport.Clinical(
        ExtractedClinicalDocument(
          documentSubtype = "Outpatient Consultation",
          facilityOrDoctorName = "Dr. S. Kulkarni",
          primaryDiagnoses = listOf(
            ExtractedClinicalFact(
              "Nocturnal cough worsening at night",
              FieldProvenance(1, "Dx: Cough worse at night", 0.92f, ProvenanceAvailability.AVAILABLE)
            )
          ),
          surgicalProcedures = emptyList(),
          prescribedMedications = listOf(
            // Med 0: QD expanded in its own source -> allowed for Med 0
            ExtractedMedication(
              drugName = "Atorvastatin",
              dosage = "10 mg",
              frequency = "QD (once daily)",
              duration = "30 days",
              instructions = null,
              provenance = FieldProvenance(1, "Tab Atorvastatin 10 mg QD (once daily)", 0.94f, ProvenanceAvailability.AVAILABLE)
            ),
            // Med 1: QD unexpanded -> Med 0 must NOT authorize QD expansion for Med 1
            ExtractedMedication(
              drugName = "Lisinopril",
              dosage = "5 mg",
              frequency = "QD",
              duration = "30 days",
              instructions = null,
              provenance = FieldProvenance(1, "Tab Lisinopril 5 mg QD", 0.93f, ProvenanceAvailability.AVAILABLE)
            ),
            // Med 2: HS unexpanded -> must block bedtime/night expansions referring to Zolpidem,
            // yet NOT suppress unrelated clinical explanations about night events
            ExtractedMedication(
              drugName = "Zolpidem",
              dosage = "5 mg",
              frequency = "HS",
              duration = "7 days",
              instructions = null,
              provenance = FieldProvenance(1, "Tab Zolpidem 5 mg HS", 0.91f, ProvenanceAvailability.AVAILABLE)
            )
          ),
          allergiesRecorded = emptyList(),
          dischargeInstructions = listOf(
            ExtractedClinicalFact(
              "Track breathing discomfort occurring at night",
              FieldProvenance(1, "Track breathing at night", 0.90f, ProvenanceAvailability.AVAILABLE)
            ),
            ExtractedClinicalFact(
              "Confirm Zolpidem timing with pharmacist",
              FieldProvenance(1, "Confirm Zolpidem timing", 0.89f, ProvenanceAvailability.AVAILABLE)
            )
          ),
          followUpDate = "2026-12-01"
        )
      )
    )

    val aiJson = """
      {
        "conciseSummary": "Zolpidem is listed to be taken at bedtime for sleep.",
        "documentOverviewExplanation": "Notes nocturnal cough symptoms occurring at night and lists three medications.",
        "diagnoses": [
          {"diagnosisIndex": 0, "plainLanguageExplanation": "Recorded cough flare-ups happening at night."}
        ],
        "medications": [
          {"medicationIndex": 0, "drugName": "Atorvastatin", "printedScheduleExplanation": "Printed as 10 mg once daily for 30 days."},
          {"medicationIndex": 1, "drugName": "Lisinopril", "printedScheduleExplanation": "Take 5 mg once daily for 30 days."},
          {"medicationIndex": 2, "drugName": "Zolpidem", "printedScheduleExplanation": "Take 5 mg at bedtime for 7 days."}
        ],
        "dischargeInstructions": [
          {"instructionIndex": 0, "plainLanguageExplanation": "Keep a log if breathing trouble occurs at night."},
          {"instructionIndex": 1, "plainLanguageExplanation": "Take Zolpidem before sleep as part of your evening routine."}
        ]
      }
    """.trimIndent()

    val parsed = MedicalExplanationParser.parseExplanationJsonSafe(aiJson, clinicalWithQdAndHs).getOrThrow()
    val clin = parsed.categoryExplanation as CategoryExplanation.Clinical

    // 1. Med 0 (Atorvastatin) had explicit QD (once daily) -> allowed
    assertEquals("Printed as 10 mg once daily for 30 days.", clin.medications[0].printedScheduleExplanation)

    // 2. Med 1 (Lisinopril) had unexpanded QD -> Med 0 cannot authorize it -> rejected
    assertNull(clin.medications[1].printedScheduleExplanation)

    // 3. Med 2 (Zolpidem) had unexpanded HS -> rejected
    assertNull(clin.medications[2].printedScheduleExplanation)

    // 4. Narrative fields actually referring to Zolpidem ("conciseSummary", dischargeInstructions[1])
    //    cannot bypass HS restriction
    assertFalse(parsed.conciseSummary.contains("at bedtime", ignoreCase = true))
    assertNull(clin.dischargeInstructions[1].plainLanguageExplanation)

    // 5. Unrelated clinical explanations about an event occurring at night ARE preserved!
    assertEquals(
      "Notes nocturnal cough symptoms occurring at night and lists three medications.",
      clin.documentOverviewExplanation
    )
    assertEquals(
      "Recorded cough flare-ups happening at night.",
      clin.diagnoses[0].plainLanguageExplanation
    )
    assertEquals(
      "Keep a log if breathing trouble occurs at night.",
      clin.dischargeInstructions[0].plainLanguageExplanation
    )

    // 6. Summary referring ONLY to Atorvastatin's explicitly expanded schedule is preserved
    val summaryOnlySafeMedJson = """
      {
        "conciseSummary": "Atorvastatin is printed as once daily for 30 days.",
        "medications": [
          {"medicationIndex": 1, "drugName": "Lisinopril", "printedScheduleExplanation": "Printed as 5 mg QD for 30 days."}
        ]
      }
    """.trimIndent()
    val safeMedParsed = MedicalExplanationParser.parseExplanationJsonSafe(
      summaryOnlySafeMedJson,
      clinicalWithQdAndHs
    ).getOrThrow()
    assertEquals("Atorvastatin is printed as once daily for 30 days.", safeMedParsed.conciseSummary)
    val safeClin = safeMedParsed.categoryExplanation as CategoryExplanation.Clinical
    assertEquals("Printed as 5 mg QD for 30 days.", safeClin.medications[1].printedScheduleExplanation)

    // 7. Original Task 3C medication instructions/frequencies/provenance remain untouched
    assertEquals("QD (once daily)", clin.medications[0].sourceMedication.frequency)
    assertEquals("QD", clin.medications[1].sourceMedication.frequency)
    assertEquals("HS", clin.medications[2].sourceMedication.frequency)
    assertEquals("Tab Lisinopril 5 mg QD", clin.medications[1].sourceMedication.provenance.sourceSnippet)
  }

  @Test
  fun fix3_hardening_uncertaintyNotesEnforceAbbreviationSafetyWhilePreservingSafeNotes() {
    val clinicalWithHs = DocumentExtractionResult(
      category = MedicalDocumentCategory.CLINICAL,
      pageCount = 1,
      isMultiPageBatch = false,
      report = UniversalMedicalReport.Clinical(
        ExtractedClinicalDocument(
          documentSubtype = "Outpatient Prescription",
          facilityOrDoctorName = "Dr. M. Rao",
          primaryDiagnoses = emptyList(),
          surgicalProcedures = emptyList(),
          prescribedMedications = listOf(
            ExtractedMedication(
              drugName = "Lorazepam",
              dosage = "1 mg",
              frequency = "HS",
              duration = "5 days",
              instructions = null,
              provenance = FieldProvenance(1, "Tab Lorazepam 1 mg HS x 5d", 0.90f, ProvenanceAvailability.AVAILABLE)
            )
          ),
          allergiesRecorded = emptyList(),
          dischargeInstructions = emptyList(),
          followUpDate = null
        )
      )
    )

    val aiWithUncertaintyNotes = """
      {
        "conciseSummary": "Prescription lists Lorazepam 1 mg with frequency HS for 5 days.",
        "uncertaintyNotes": [
          "Lorazepam schedule printed as HS likely means at bedtime.",
          "Timing abbreviation HS for Lorazepam could not be confirmed verbatim without prescriber clarification.",
          "Handwritten follow-up date near bottom of page was smudged and blood pressure readings at night were blurred."
        ],
        "medications": [
          {"medicationIndex": 0, "drugName": "Lorazepam", "printedScheduleExplanation": "Printed as 1 mg with abbreviation HS for 5 days."}
        ]
      }
    """.trimIndent()

    val parsed = MedicalExplanationParser.parseExplanationJsonSafe(aiWithUncertaintyNotes, clinicalWithHs).getOrThrow()

    // 1. Uncertainty note that incorrectly expands HS ("at bedtime") must be rejected
    assertTrue(parsed.uncertaintyNotes.none { it.contains("at bedtime", ignoreCase = true) })

    // 2. Uncertainty note preserving the original abbreviation HS is retained
    assertTrue(
      parsed.uncertaintyNotes.contains(
        "Timing abbreviation HS for Lorazepam could not be confirmed verbatim without prescriber clarification."
      )
    )

    // 3. Safe uncertainty note unrelated to medication (even mentioning "at night") is retained
    assertTrue(
      parsed.uncertaintyNotes.contains(
        "Handwritten follow-up date near bottom of page was smudged and blood pressure readings at night were blurred."
      )
    )
    assertEquals(2, parsed.uncertaintyNotes.size)

    // 4. Original source medical facts and provenance remain untouched
    val med = (parsed.categoryExplanation as CategoryExplanation.Clinical).medications[0].sourceMedication
    assertEquals("Lorazepam", med.drugName)
    assertEquals("HS", med.frequency)
    assertEquals("Tab Lorazepam 1 mg HS x 5d", med.provenance.sourceSnippet)
  }
}
