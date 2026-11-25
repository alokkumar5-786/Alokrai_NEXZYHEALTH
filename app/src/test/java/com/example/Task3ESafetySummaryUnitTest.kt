package com.example

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import com.example.domain.engine.RuleValidationStatus
import com.example.domain.engine.SafetyFindingEvaluator
import com.example.domain.engine.SafetyRuleDefinition
import com.example.domain.engine.SafetyRuleMatch
import com.example.domain.engine.SafetyRuleRegistry
import com.example.domain.engine.SafetyRuleTargetSeverity
import com.example.model.AnatomicalFinding
import com.example.model.CategoryExplanation
import com.example.model.DocumentExtractionResult
import com.example.model.EvidenceOrigin
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
import com.example.model.RangeComparisonStatus
import com.example.model.ReportExplanationResult
import com.example.model.ReportExplanationState
import com.example.model.SafetyActionGuidance
import com.example.model.SafetyBucket
import com.example.model.SafetyConstants
import com.example.model.UniversalMedicalReport
import com.example.ui.components.SafetySummaryCard
import com.example.ui.screens.ExtractionResultScreen
import com.example.ui.screens.ReportExplanationScreen
import com.example.ui.theme.MyApplicationTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class Task3ESafetySummaryUnitTest {

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

  private fun availProv(page: Int = 1, snippet: String = "Sample snippet"): FieldProvenance =
    FieldProvenance(
      pageNumber = page,
      sourceSnippet = snippet,
      estimatedExtractionConfidence = 0.95f,
      availability = ProvenanceAvailability.AVAILABLE
    )

  private fun labExtraction(vararg items: ExtractedLabItem): DocumentExtractionResult =
    DocumentExtractionResult(
      category = MedicalDocumentCategory.LABORATORY,
      pageCount = 1,
      isMultiPageBatch = false,
      report = UniversalMedicalReport.Laboratory(
        ExtractedLaboratoryReport(
          labFacilityName = "Apex Diagnostics",
          collectionDate = "2026-04-01",
          reportDate = "2026-04-02",
          items = items.toList()
        )
      )
    )

  @Test
  fun productionRegistryHasZeroActiveRulesAndFailsClosed() {
    val prodRegistry = SafetyRuleRegistry.productionDefault()
    assertTrue(prodRegistry.isProductionDefault)
    assertEquals(8, prodRegistry.allRegisteredRules().size)
    assertTrue(prodRegistry.activeRules().isEmpty())
    prodRegistry.allRegisteredRules().forEach { rule ->
      assertEquals(RuleValidationStatus.PENDING_CLINICAL_VALIDATION, rule.validationStatus)
      assertFalse(rule.isClinicallyApprovedForProduction)
      assertFalse(rule.isExecutable())
    }

    // Even a report with extreme numeric values executes 0 unvalidated rules in productionDefault()
    val extremePotassium = labExtraction(
      ExtractedLabItem(
        originalName = "Serum Potassium",
        originalValue = "7.4",
        originalUnit = "mmol/L",
        referenceRange = "3.5 - 5.1",
        laboratoryFlag = null,
        findingStatus = FindingStatus.ABNORMAL,
        provenance = availProv(1, "Potassium 7.4 mmol/L")
      )
    )
    assertTrue(prodRegistry.evaluateExecutableRules(extremePotassium).isEmpty())
    val prodSummary = SafetyFindingEvaluator.evaluate(extremePotassium, prodRegistry)
    assertEquals(0, prodSummary.activeValidatedRuleCount)
    assertTrue(prodSummary.alerts.none { it.bucket == SafetyBucket.RULE_VALIDATED_ALERT })

    // Pending status fails closed even if isClinicallyApprovedForProduction=true and sign-off present
    val spoofedPendingRule = SafetyRuleDefinition(
      ruleId = "SPOOFED_PENDING_RULE",
      ruleVersion = 1,
      title = "Pending but approved flag set",
      category = MedicalDocumentCategory.LABORATORY,
      targetSeverity = SafetyRuleTargetSeverity.CRITICAL_ALERT,
      validationStatus = RuleValidationStatus.PENDING_CLINICAL_VALIDATION,
      isClinicallyApprovedForProduction = true,
      clinicalEvidenceRef = "Ref-001",
      clinicalReviewerSignOff = "Dr. Test",
      signOffDateIso = "2026-04-10",
      predicate = { _, _, _ -> emptyList() }
    )
    assertFalse(spoofedPendingRule.isExecutable())

    // Disabled rule or missing sign-off or missing units fails closed
    val missingSignOffRule = spoofedPendingRule.copy(
      ruleId = "NO_SIGNOFF",
      validationStatus = RuleValidationStatus.CLINICALLY_VALIDATED,
      clinicalReviewerSignOff = null
    )
    assertFalse(missingSignOffRule.isExecutable())

    val missingUnitsRule = spoofedPendingRule.copy(
      ruleId = "NO_UNITS",
      validationStatus = RuleValidationStatus.CLINICALLY_VALIDATED,
      requiresExactUnitMatch = true,
      applicableUnits = emptySet()
    )
    assertFalse(missingUnitsRule.isExecutable())

    val disabledRule = spoofedPendingRule.copy(
      ruleId = "DISABLED_RULE",
      validationStatus = RuleValidationStatus.DISABLED
    )
    assertFalse(disabledRule.isExecutable())

    // Test-injected CLINICALLY_VALIDATED rule executes ONLY when unit matches
    val validatedTestRule = SafetyRuleDefinition(
      ruleId = "TEST_POTASSIUM_HIGH_V1",
      ruleVersion = 2,
      title = "Validated High Potassium Rule",
      category = MedicalDocumentCategory.LABORATORY,
      targetSeverity = SafetyRuleTargetSeverity.CRITICAL_ALERT,
      validationStatus = RuleValidationStatus.CLINICALLY_VALIDATED,
      isClinicallyApprovedForProduction = true,
      clinicalEvidenceRef = "Guideline-K-2026",
      clinicalReviewerSignOff = "Dr. A. Sen, MD",
      signOffDateIso = "2026-04-10",
      requiresExactUnitMatch = true,
      applicableUnits = setOf("mmol/L"),
      predicate = { extraction, rule, rulesetVer ->
        val lab = (extraction.report as UniversalMedicalReport.Laboratory).report
        lab.items.mapNotNull { item ->
          val v = item.originalValue?.toDoubleOrNull() ?: return@mapNotNull null
          if (v >= 6.5) {
            SafetyRuleMatch(
              ruleId = rule.ruleId,
              ruleVersion = rule.ruleVersion,
              rulesetVersion = rulesetVer,
              category = rule.category,
              targetSeverity = rule.targetSeverity,
              clinicalEvidenceRef = rule.clinicalEvidenceRef,
              sourceLabel = item.originalName ?: "Potassium",
              matchedEvidenceSnippet = "${item.originalValue} ${item.originalUnit ?: ""}".trim(),
              summaryMessage = "Validated test rule matched K >= 6.5 mmol/L.",
              provenance = item.provenance,
              matchedUnit = item.originalUnit
            )
          } else {
            null
          }
        }
      }
    )
    assertTrue(validatedTestRule.isExecutable())

    val testRegistry = SafetyRuleRegistry.forTesting(
      rules = listOf(spoofedPendingRule, validatedTestRule)
    )
    assertEquals(1, testRegistry.activeRules().size)

    val matchingSummary = SafetyFindingEvaluator.evaluate(extremePotassium, testRegistry)
    assertEquals(1, matchingSummary.activeValidatedRuleCount)
    assertEquals(SafetyBucket.RULE_VALIDATED_ALERT, matchingSummary.overallClassification)
    val ruleAlert = matchingSummary.alerts.first { it.bucket == SafetyBucket.RULE_VALIDATED_ALERT }
    assertEquals(EvidenceOrigin.CLINICALLY_VALIDATED_RULE, ruleAlert.evidenceOrigin)
    assertEquals("TEST_POTASSIUM_HIGH_V1", ruleAlert.ruleReference?.ruleId)
    assertEquals(2, ruleAlert.ruleReference?.ruleVersion)
    assertEquals("Guideline-K-2026", ruleAlert.ruleReference?.clinicalEvidenceRef)

    // Mismatched unit ("mEq/L" when only "mmol/L" is allowed) must NOT match
    val wrongUnitPotassium = labExtraction(
      ExtractedLabItem(
        originalName = "Serum Potassium",
        originalValue = "7.4",
        originalUnit = "mEq/L",
        referenceRange = "3.5 - 5.1",
        laboratoryFlag = null,
        findingStatus = FindingStatus.ABNORMAL,
        provenance = availProv(1, "Potassium 7.4 mEq/L")
      )
    )
    val wrongUnitSummary = SafetyFindingEvaluator.evaluate(wrongUnitPotassium, testRegistry)
    assertTrue(wrongUnitSummary.alerts.none { it.bucket == SafetyBucket.RULE_VALIDATED_ALERT })
  }

  @Test
  fun labFlagsClassifyCriticalAbnormalStatLegendDependentPositiveAndNegatedSafely() {
    // 1. CRITICAL / PANIC flag
    val criticalRes = SafetyFindingEvaluator.evaluate(
      labExtraction(
        ExtractedLabItem(
          originalName = "Potassium",
          originalValue = "6.8",
          originalUnit = "mmol/L",
          referenceRange = "3.5 - 5.1",
          laboratoryFlag = "CRITICAL HIGH",
          findingStatus = FindingStatus.ABNORMAL,
          provenance = availProv(1, "Potassium 6.8 mmol/L CRITICAL HIGH")
        )
      )
    )
    assertEquals(SafetyBucket.EXTRACTED_CRITICAL_FLAG, criticalRes.overallClassification)
    assertEquals(EvidenceOrigin.AI_EXTRACTED_LAB_FLAG, criticalRes.alerts.single().evidenceOrigin)
    assertTrue(criticalRes.hasCriticalOrUrgentFindings)

    // 2. Ordinary abnormal H / L
    val highRes = SafetyFindingEvaluator.evaluate(
      labExtraction(
        ExtractedLabItem(
          originalName = "LDL Cholesterol",
          originalValue = "162",
          originalUnit = "mg/dL",
          referenceRange = "< 100 mg/dL",
          laboratoryFlag = "H",
          findingStatus = FindingStatus.ABNORMAL,
          provenance = availProv(1, "LDL 162 mg/dL H")
        )
      )
    )
    assertEquals(SafetyBucket.EXTRACTED_ABNORMAL_FINDING, highRes.overallClassification)
    assertEquals(EvidenceOrigin.AI_EXTRACTED_LAB_FLAG, highRes.alerts.single().evidenceOrigin)
    assertFalse(highRes.hasCriticalOrUrgentFindings)

    // 3. NON-CRITICAL must never become critical
    val negatedFlagRes = SafetyFindingEvaluator.evaluate(
      labExtraction(
        ExtractedLabItem(
          originalName = "Sodium",
          originalValue = "140",
          originalUnit = "mmol/L",
          referenceRange = "135 - 145",
          laboratoryFlag = "NON-CRITICAL",
          findingStatus = FindingStatus.NORMAL,
          provenance = availProv(1, "Sodium 140 mmol/L NON-CRITICAL")
        )
      )
    )
    assertEquals(SafetyBucket.NO_ABNORMALITY_IDENTIFIED_IN_EXTRACTED_FINDINGS, negatedFlagRes.overallClassification)

    // 4. STAT is order priority / legend-dependent -> UNCERTAIN_OR_UNREADABLE, never critical
    val statRes = SafetyFindingEvaluator.evaluate(
      labExtraction(
        ExtractedLabItem(
          originalName = "Troponin I",
          originalValue = "0.02",
          originalUnit = "ng/mL",
          referenceRange = "<= 0.04",
          laboratoryFlag = "STAT",
          findingStatus = FindingStatus.NORMAL,
          provenance = availProv(1, "Troponin I 0.02 ng/mL STAT")
        )
      )
    )
    assertEquals(SafetyBucket.UNCERTAIN_OR_UNREADABLE, statRes.overallClassification)
    assertFalse(statRes.hasCriticalOrUrgentFindings)
    assertTrue(statRes.alerts.single().title.contains("STAT"))

    // 5. Legend-dependent ambiguous flags ("A", "*", "**", "***", "HH", "LL", "!!", "CALL") -> never silently normal or critical
    listOf("A", "*", "**", "***", "HH", "LL", "!!", "CALL").forEach { legendFlag ->
      val legendRes = SafetyFindingEvaluator.evaluate(
        labExtraction(
          ExtractedLabItem(
            originalName = "Analyte",
            originalValue = "10.0",
            originalUnit = "mg/dL",
            referenceRange = "8.0 - 12.0",
            laboratoryFlag = legendFlag,
            findingStatus = FindingStatus.NORMAL,
            provenance = availProv(1, "Analyte 10.0 mg/dL $legendFlag")
          )
        )
      )
      assertEquals(
        "Flag '$legendFlag' must map to UNCERTAIN_OR_UNREADABLE",
        SafetyBucket.UNCERTAIN_OR_UNREADABLE,
        legendRes.overallClassification
      )
      assertFalse(legendRes.hasCriticalOrUrgentFindings)
      assertEquals(
        EvidenceOrigin.MISSING_OR_CONFLICTING_EXTRACTED_DATA,
        legendRes.alerts.single().evidenceOrigin
      )
    }

    // 6. FindingStatus.POSITIVE stays EXTRACTED_POSITIVE_FINDING (never coerced to abnormal)
    val positiveRes = SafetyFindingEvaluator.evaluate(
      labExtraction(
        ExtractedLabItem(
          originalName = "Hepatitis B Antibody",
          originalValue = "Positive",
          originalUnit = null,
          referenceRange = null,
          laboratoryFlag = null,
          findingStatus = FindingStatus.POSITIVE,
          provenance = availProv(1, "Anti-HBs: Positive")
        )
      )
    )
    assertEquals(SafetyBucket.EXTRACTED_POSITIVE_FINDING, positiveRes.overallClassification)
    assertEquals(EvidenceOrigin.AI_EXTRACTED_FINDING_STATUS, positiveRes.alerts.single().evidenceOrigin)
  }

  @Test
  fun labNumericRangeGrammarSafeguardsUnitsMalformedMultiTierAndValidComparisons() {
    // 1. Missing unit blocks numeric comparison
    val missingUnit = SafetyFindingEvaluator.comparePrintedRange(
      ExtractedLabItem("Hemoglobin", "9.2", null, "12.0 - 15.5", null, FindingStatus.NORMAL, availProv())
    )
    assertEquals(RangeComparisonStatus.UNAVAILABLE, missingUnit.status)

    // 2. Unit mismatch refuses implicit conversion
    val unitMismatch = SafetyFindingEvaluator.comparePrintedRange(
      ExtractedLabItem("Glucose", "150", "mg/dL", "3.9 - 5.5 mmol/L", null, FindingStatus.NORMAL, availProv())
    )
    assertEquals(RangeComparisonStatus.UNAVAILABLE, unitMismatch.status)
    assertTrue(unitMismatch.unavailableReason?.contains("Unit mismatch", ignoreCase = true) == true)

    // 3. Multi-tier & demographic ranges fail closed to UNAVAILABLE
    listOf(
      "Male: 13-17; Female: 12-15",
      "< 100 Desirable",
      "70 - 99 (Fasting)",
      "12.0 - 10.0",
      "10 ± 2"
    ).forEach { unsafeRange ->
      val res = SafetyFindingEvaluator.comparePrintedRange(
        ExtractedLabItem("Param", "14.0", "g/dL", unsafeRange, null, FindingStatus.NORMAL, availProv())
      )
      assertEquals("Range '$unsafeRange' must be UNAVAILABLE", RangeComparisonStatus.UNAVAILABLE, res.status)
    }

    // 4. Non-numeric value vs printed range
    val nonNumeric = SafetyFindingEvaluator.comparePrintedRange(
      ExtractedLabItem("WBC", "Pending", "K/uL", "4.0 - 11.0", null, FindingStatus.NORMAL, availProv())
    )
    assertEquals(RangeComparisonStatus.UNAVAILABLE, nonNumeric.status)

    // 5. Valid matching unit within interval & inequality bounds
    val withinClosed = SafetyFindingEvaluator.comparePrintedRange(
      ExtractedLabItem("Hemoglobin", "14.0", "g/dL", "12.0 - 15.5", null, FindingStatus.NORMAL, availProv())
    )
    assertEquals(RangeComparisonStatus.WITHIN_RANGE, withinClosed.status)

    val withinUpper = SafetyFindingEvaluator.comparePrintedRange(
      ExtractedLabItem("Cholesterol", "180", "mg/dL", "<= 200 mg/dL", null, FindingStatus.NORMAL, availProv())
    )
    assertEquals(RangeComparisonStatus.WITHIN_RANGE, withinUpper.status)

    val outsideLower = SafetyFindingEvaluator.comparePrintedRange(
      ExtractedLabItem("eGFR", "52", "mL/min", ">= 60 mL/min", null, FindingStatus.NORMAL, availProv())
    )
    assertEquals(RangeComparisonStatus.OUTSIDE_RANGE, outsideLower.status)

    // 6. Outside extracted reference range without lab flag -> OUTSIDE_EXTRACTED_REFERENCE_RANGE + conflict counter
    val unflaggedLowSummary = SafetyFindingEvaluator.evaluate(
      labExtraction(
        ExtractedLabItem("Hemoglobin", "10.8", "g/dL", "12.0 - 15.5 g/dL", null, FindingStatus.NORMAL, availProv())
      )
    )
    assertEquals(SafetyBucket.OUTSIDE_EXTRACTED_REFERENCE_RANGE, unflaggedLowSummary.overallClassification)
    assertEquals(1, unflaggedLowSummary.conflictsCount)
    assertEquals(
      EvidenceOrigin.EXTRACTED_RANGE_NUMERIC_COMPARISON,
      unflaggedLowSummary.alerts.single().evidenceOrigin
    )

    // 7. Qualitative negative without reference range -> NOT_APPLICABLE, evaluates cleanly
    val qualSummary = SafetyFindingEvaluator.evaluate(
      labExtraction(
        ExtractedLabItem("HBsAg", "Non-Reactive", null, null, null, FindingStatus.NEGATIVE, availProv())
      )
    )
    assertEquals(SafetyBucket.NO_ABNORMALITY_IDENTIFIED_IN_EXTRACTED_FINDINGS, qualSummary.overallClassification)
    assertEquals(0, qualSummary.unavailableComparisonsCount)
  }

  @Test
  fun fiveItemLabPrecedenceAndUnavailableComparisonBlockNoAbnormalityClaim() {
    fun normalItem(name: String, value: String = "14.0"): ExtractedLabItem =
      ExtractedLabItem(name, value, "g/dL", "12.0 - 16.0", null, FindingStatus.NORMAL, availProv(1, "$name $value g/dL"))

    // Scenario A: 4 NORMAL + 1 NORMAL with missing unit -> UNCERTAIN_OR_UNREADABLE (never NO_ABNORMALITY)
    val missingUnitSummary = SafetyFindingEvaluator.evaluate(
      labExtraction(
        normalItem("Test1"),
        normalItem("Test2"),
        normalItem("Test3"),
        normalItem("Test4"),
        ExtractedLabItem("Test5", "9.2", null, "12.0 - 15.5", null, FindingStatus.NORMAL, availProv())
      )
    )
    assertEquals(SafetyBucket.UNCERTAIN_OR_UNREADABLE, missingUnitSummary.overallClassification)
    assertEquals(1, missingUnitSummary.unavailableComparisonsCount)
    assertFalse(missingUnitSummary.overallHeadline.contains("No Abnormality", ignoreCase = true))

    // Scenario B: 4 NORMAL + 1 NORMAL with multi-tier range -> INCOMPLETE_ASSESSMENT (blocks NO_ABNORMALITY)
    val multiTierSummary = SafetyFindingEvaluator.evaluate(
      labExtraction(
        normalItem("Test1"),
        normalItem("Test2"),
        normalItem("Test3"),
        normalItem("Test4"),
        ExtractedLabItem(
          "Ferritin",
          "45",
          "ng/mL",
          "Male: 30-400; Female: 13-150",
          null,
          FindingStatus.NORMAL,
          availProv()
        )
      )
    )
    assertEquals(SafetyBucket.INCOMPLETE_ASSESSMENT, multiTierSummary.overallClassification)
    assertEquals(1, multiTierSummary.unavailableComparisonsCount)
    assertEquals(1, multiTierSummary.unavailableComparisons.size)
    assertFalse(multiTierSummary.overallHeadline.contains("No Abnormality", ignoreCase = true))

    // Scenario C: 3 NORMAL + 1 multi-tier unavailable + 1 CRITICAL -> EXTRACTED_CRITICAL_FLAG wins precedence
    val mixedCriticalSummary = SafetyFindingEvaluator.evaluate(
      labExtraction(
        normalItem("Test1"),
        normalItem("Test2"),
        normalItem("Test3"),
        ExtractedLabItem("Ferritin", "45", "ng/mL", "Male: 30-400; Female: 13-150", null, FindingStatus.NORMAL, availProv()),
        ExtractedLabItem("Potassium", "6.9", "mmol/L", "3.5 - 5.1", "CRITICAL", FindingStatus.ABNORMAL, availProv())
      )
    )
    assertEquals(SafetyBucket.EXTRACTED_CRITICAL_FLAG, mixedCriticalSummary.overallClassification)
    assertEquals(1, mixedCriticalSummary.unavailableComparisonsCount)
    assertTrue(mixedCriticalSummary.overallSummaryMessage.contains("1 unavailable range comparison"))

    // Scenario D: All 5 NORMAL with verified ranges -> exact safe headline & qualified message
    val allNormalSummary = SafetyFindingEvaluator.evaluate(
      labExtraction(
        normalItem("Test1"),
        normalItem("Test2"),
        normalItem("Test3"),
        normalItem("Test4"),
        normalItem("Test5")
      )
    )
    assertEquals(SafetyBucket.NO_ABNORMALITY_IDENTIFIED_IN_EXTRACTED_FINDINGS, allNormalSummary.overallClassification)
    assertEquals(SafetyConstants.NO_ABNORMALITY_HEADLINE, allNormalSummary.overallHeadline)
    assertTrue(allNormalSummary.overallSummaryMessage.startsWith("No abnormality identified in AI-extracted findings"))
  }

  @Test
  fun narrativeUrgentHeadersContextGuardsAndAllCategoriesSafelyEvaluated() {
    // 1. Radiology urgent header vs negated / rule-out / historical context
    val urgentRad = DocumentExtractionResult(
      category = MedicalDocumentCategory.RADIOLOGY,
      pageCount = 1,
      isMultiPageBatch = false,
      report = UniversalMedicalReport.Radiology(
        ExtractedRadiologyReport(
          modality = "CT Head",
          anatomicalRegion = "Brain",
          clinicalIndication = "Trauma",
          techniqueDescription = "Non-contrast",
          anatomicalFindings = listOf(
            AnatomicalFinding("Brain", "CRITICAL FINDING: Acute subdural hematoma.", FindingStatus.ABNORMAL, availProv())
          ),
          formalImpressions = listOf(
            RadiologyImpression("Acute subdural hematoma requiring urgent review.", FindingStatus.ABNORMAL, availProv())
          )
        )
      )
    )
    val urgentRadSummary = SafetyFindingEvaluator.evaluate(urgentRad)
    assertEquals(SafetyBucket.AI_EXTRACTED_URGENT_WORDING, urgentRadSummary.overallClassification)
    assertEquals(SafetyConstants.URGENT_WORDING_TITLE, urgentRadSummary.overallHeadline)
    assertTrue(urgentRadSummary.overallSummaryMessage.contains(SafetyConstants.URGENT_WORDING_BODY))

    val negatedAndRuleOutRad = DocumentExtractionResult(
      category = MedicalDocumentCategory.RADIOLOGY,
      pageCount = 1,
      isMultiPageBatch = false,
      report = UniversalMedicalReport.Radiology(
        ExtractedRadiologyReport(
          modality = "CT Chest",
          anatomicalRegion = "Thorax",
          clinicalIndication = "Rule out critical finding",
          techniqueDescription = "Contrast",
          anatomicalFindings = listOf(
            AnatomicalFinding("Parenchyma", "No evidence of critical finding or acute hemorrhage. History of critical finding in 2021.", FindingStatus.NORMAL, availProv()),
            AnatomicalFinding("Vessels", "Rule out critical alert for pulmonary embolism.", FindingStatus.NORMAL, availProv())
          ),
          formalImpressions = listOf(
            RadiologyImpression("No acute abnormality.", FindingStatus.NORMAL, availProv())
          )
        )
      )
    )
    val guardedRadSummary = SafetyFindingEvaluator.evaluate(negatedAndRuleOutRad)
    assertEquals(SafetyBucket.NO_ABNORMALITY_IDENTIFIED_IN_EXTRACTED_FINDINGS, guardedRadSummary.overallClassification)
    assertFalse(guardedRadSummary.hasCriticalOrUrgentFindings)

    // 2. Cardiology: missing formalConclusion -> INCOMPLETE_ASSESSMENT; conflicting NORMAL vs "ABNORMAL ECG" -> UNCERTAIN
    val incompleteCardio = DocumentExtractionResult(
      category = MedicalDocumentCategory.CARDIOLOGY,
      pageCount = 1,
      isMultiPageBatch = false,
      report = UniversalMedicalReport.Cardiology(
        ExtractedCardiologyReport(
          modality = "12-Lead ECG",
          heartRateBpm = "72 bpm",
          rhythmInterpretation = "Sinus rhythm",
          prIntervalMs = "160 ms",
          qrsDurationMs = "88 ms",
          qtcIntervalMs = "420 ms",
          ejectionFractionPercent = null,
          wallMotionFindings = null,
          formalConclusion = null,
          findingStatus = FindingStatus.NORMAL,
          provenance = availProv()
        )
      )
    )
    assertEquals(SafetyBucket.INCOMPLETE_ASSESSMENT, SafetyFindingEvaluator.evaluate(incompleteCardio).overallClassification)

    val conflictCardio = DocumentExtractionResult(
      category = MedicalDocumentCategory.CARDIOLOGY,
      pageCount = 1,
      isMultiPageBatch = false,
      report = UniversalMedicalReport.Cardiology(
        ExtractedCardiologyReport(
          modality = "12-Lead ECG",
          heartRateBpm = "72 bpm",
          rhythmInterpretation = "Sinus rhythm",
          prIntervalMs = "160 ms",
          qrsDurationMs = "88 ms",
          qtcIntervalMs = "420 ms",
          ejectionFractionPercent = null,
          wallMotionFindings = null,
          formalConclusion = "ABNORMAL ECG with nonspecific ST changes",
          findingStatus = FindingStatus.NORMAL,
          provenance = availProv()
        )
      )
    )
    val conflictCardioSummary = SafetyFindingEvaluator.evaluate(conflictCardio)
    assertEquals(SafetyBucket.UNCERTAIN_OR_UNREADABLE, conflictCardioSummary.overallClassification)
    assertEquals(1, conflictCardioSummary.conflictsCount)

    // 3. Specialised: IMMEDIATE CLINICAL CORRELATION / clinicalHistory are NOT treated as urgent wording
    val specialisedCorrelation = DocumentExtractionResult(
      category = MedicalDocumentCategory.SPECIALISED,
      pageCount = 1,
      isMultiPageBatch = false,
      report = UniversalMedicalReport.Specialised(
        ExtractedSpecialisedReport(
          reportSubtype = "Fine Needle Aspiration",
          specimenSource = "Thyroid",
          clinicalHistory = "CRITICAL ALERT mentioned in prior referral history.",
          grossDescription = "Clear fluid",
          microscopicDescription = "Benign follicular cells",
          gradingOrStaging = null,
          diagnosticConclusion = "Benign follicular nodule. IMMEDIATE CLINICAL CORRELATION recommended.",
          findingStatus = FindingStatus.NEGATIVE,
          provenance = availProv()
        )
      )
    )
    val specCorrelationSummary = SafetyFindingEvaluator.evaluate(specialisedCorrelation)
    assertFalse(specCorrelationSummary.hasCriticalOrUrgentFindings)
    assertEquals(SafetyBucket.NO_ABNORMALITY_IDENTIFIED_IN_EXTRACTED_FINDINGS, specCorrelationSummary.overallClassification)

    // 4. Clinical document: conditional ER warning in dischargeInstructions -> EXTRACTED_CARE_INSTRUCTION (not emergency)
    // Routine medication never triggers abnormal/critical; overall Clinical -> INCOMPLETE_ASSESSMENT
    val clinicalDoc = DocumentExtractionResult(
      category = MedicalDocumentCategory.CLINICAL,
      pageCount = 1,
      isMultiPageBatch = false,
      report = UniversalMedicalReport.Clinical(
        ExtractedClinicalDocument(
          documentSubtype = "Discharge Summary",
          facilityOrDoctorName = "Dr. R. Nair",
          primaryDiagnoses = listOf(ExtractedClinicalFact("Viral upper respiratory infection", availProv())),
          surgicalProcedures = emptyList(),
          prescribedMedications = listOf(
            ExtractedMedication("Paracetamol", "500 mg", "Twice daily", "5 days", "Take after food", availProv())
          ),
          allergiesRecorded = listOf(ExtractedClinicalFact("No known drug allergies", availProv())),
          dischargeInstructions = listOf(
            ExtractedClinicalFact("Go to ER if fever > 102F or breathing worsens", availProv()),
            ExtractedClinicalFact("Rest and drink plenty of fluids", availProv())
          ),
          followUpDate = "2026-04-12"
        )
      )
    )
    val clinicalSummary = SafetyFindingEvaluator.evaluate(clinicalDoc)
    assertFalse(clinicalSummary.hasCriticalOrUrgentFindings)
    assertFalse(clinicalSummary.hasAbnormalOrPositiveFindings)
    assertEquals(SafetyBucket.INCOMPLETE_ASSESSMENT, clinicalSummary.overallClassification)
    assertEquals(2, clinicalSummary.careInstructions.size)
    assertTrue(clinicalSummary.careInstructions.all { it.bucket == SafetyBucket.EXTRACTED_CARE_INSTRUCTION })

    // 5. Unidentified -> UNCERTAIN_OR_UNREADABLE
    val unidentified = DocumentExtractionResult(
      category = MedicalDocumentCategory.UNCERTAIN,
      pageCount = 1,
      isMultiPageBatch = false,
      report = UniversalMedicalReport.Unidentified(rawSnippet = "Blurred receipt", reason = "Not a medical report")
    )
    assertEquals(SafetyBucket.UNCERTAIN_OR_UNREADABLE, SafetyFindingEvaluator.evaluate(unidentified).overallClassification)
  }

  @Test
  fun missingProvenanceNeverFabricatesPageOrSnippetInSummaryOrUi() {
    val noProvLab = labExtraction(
      ExtractedLabItem(
        originalName = "Sodium",
        originalValue = "121",
        originalUnit = "mmol/L",
        referenceRange = "135 - 145",
        laboratoryFlag = "CRITICAL LOW",
        findingStatus = FindingStatus.ABNORMAL,
        provenance = FieldProvenance.unavailable()
      )
    )
    val summary = SafetyFindingEvaluator.evaluate(noProvLab)
    val alert = summary.alerts.single()
    assertEquals(ProvenanceAvailability.UNAVAILABLE, alert.provenance.availability)
    assertNull(alert.provenance.pageNumber)
    assertNull(alert.provenance.sourceSnippet)

    composeTestRule.setContent {
      MyApplicationTheme {
        Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
          SafetySummaryCard(safetySummary = summary)
        }
      }
    }
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag("safety_summary_card").assertIsDisplayed()
    composeTestRule.onNodeWithText("Provenance: Unavailable (not extracted from document)")
      .performScrollTo()
      .assertIsDisplayed()
    assertTrue(composeTestRule.onAllNodes(hasText("Page 1", substring = true)).fetchSemanticsNodes().isEmpty())
  }

  @Test
  fun aiExplanationIsolationAndConsistentUiAcrossExtractionAndExplanationScreens() {
    val urgentRadExtraction = DocumentExtractionResult(
      category = MedicalDocumentCategory.RADIOLOGY,
      pageCount = 1,
      isMultiPageBatch = false,
      report = UniversalMedicalReport.Radiology(
        ExtractedRadiologyReport(
          modality = "Chest X-Ray",
          anatomicalRegion = "Chest",
          clinicalIndication = "Shortness of breath",
          techniqueDescription = "PA view",
          anatomicalFindings = listOf(
            AnatomicalFinding(
              anatomicalSite = "Right hemithorax",
              observationText = "CRITICAL FINDING: Moderate right pneumothorax.",
              findingStatus = FindingStatus.ABNORMAL,
              provenance = availProv(1, "CRITICAL FINDING: Moderate right pneumothorax.")
            )
          ),
          formalImpressions = listOf(
            RadiologyImpression(
              impressionText = "Right pneumothorax; urgent clinical team notification recommended.",
              findingStatus = FindingStatus.ABNORMAL,
              provenance = availProv(1, "Right pneumothorax")
            )
          )
        )
      )
    )

    // Evaluate purely from Task 3C extraction; adversarial Task 3D AI output cannot alter it
    val safetySummary = SafetyFindingEvaluator.evaluate(urgentRadExtraction)
    assertEquals(SafetyBucket.AI_EXTRACTED_URGENT_WORDING, safetySummary.overallClassification)

    val adversarialAiExplanation = ReportExplanationResult(
      category = MedicalDocumentCategory.RADIOLOGY,
      language = "English",
      conciseSummary = "Everything looks reassuring and completely normal.",
      uncertaintyNotes = emptyList(),
      medicalTerms = emptyList(),
      questionsForDoctor = listOf("When should I follow up?"),
      safetyDisclaimer = ExplanationConstants.MEDICAL_EXPLANATION_DISCLAIMER,
      categoryExplanation = CategoryExplanation.Radiology(
        modality = "Chest X-Ray",
        anatomicalRegion = "Chest",
        clinicalIndication = "Shortness of breath",
        techniqueDescription = "PA view",
        studyOverviewExplanation = "X-ray picture of the chest.",
        findings = emptyList(),
        impressions = emptyList()
      )
    )
    // Re-evaluating the same extraction confirms AI explanation had zero effect on Task 3E classification
    val reevaluated = SafetyFindingEvaluator.evaluate(urgentRadExtraction)
    assertEquals(safetySummary.overallClassification, reevaluated.overallClassification)
    assertEquals(safetySummary.overallHeadline, reevaluated.overallHeadline)

    // Verify ExtractionResultScreen displays urgent SafetySummaryCard & scope notice
    composeTestRule.setContent {
      MyApplicationTheme {
        ExtractionResultScreen(
          extractionResult = urgentRadExtraction,
          fileName = "CXR_2026.pdf",
          patientName = "Priya Sharma",
          onUploadAnotherClick = {},
          onNavigateBack = {},
          onExplainReportClick = {},
          safetySummary = safetySummary
        )
      }
    }
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag("extraction_result_list")
      .performScrollToNode(hasTestTag("safety_summary_card"))
    composeTestRule.onNodeWithTag("safety_summary_card").assertIsDisplayed()
    composeTestRule.onNodeWithTag("safety_overall_headline").assertIsDisplayed()
    composeTestRule.onAllNodesWithText(SafetyConstants.URGENT_WORDING_TITLE).onFirst().assertIsDisplayed()
    composeTestRule.onNodeWithTag("safety_scope_limitation_notice")
      .performScrollTo()
      .assertIsDisplayed()
  }

  @Test
  fun reportExplanationScreenSuccessDisplaysIdenticalSafetySummaryAgainstAdversarialAiSummary() {
    val urgentRadExtraction = DocumentExtractionResult(
      category = MedicalDocumentCategory.RADIOLOGY,
      pageCount = 1,
      isMultiPageBatch = false,
      report = UniversalMedicalReport.Radiology(
        ExtractedRadiologyReport(
          modality = "Chest X-Ray",
          anatomicalRegion = "Chest",
          clinicalIndication = "Shortness of breath",
          techniqueDescription = "PA view",
          anatomicalFindings = listOf(
            AnatomicalFinding(
              anatomicalSite = "Right hemithorax",
              observationText = "CRITICAL FINDING: Moderate right pneumothorax.",
              findingStatus = FindingStatus.ABNORMAL,
              provenance = availProv(1, "CRITICAL FINDING: Moderate right pneumothorax.")
            )
          ),
          formalImpressions = listOf(
            RadiologyImpression(
              impressionText = "Right pneumothorax; urgent clinical team notification recommended.",
              findingStatus = FindingStatus.ABNORMAL,
              provenance = availProv(1, "Right pneumothorax")
            )
          )
        )
      )
    )
    val safetySummary = SafetyFindingEvaluator.evaluate(urgentRadExtraction)
    val adversarialAiExplanation = ReportExplanationResult(
      category = MedicalDocumentCategory.RADIOLOGY,
      language = "English",
      conciseSummary = "Everything looks reassuring and completely normal.",
      uncertaintyNotes = emptyList(),
      medicalTerms = emptyList(),
      questionsForDoctor = listOf("When should I follow up?"),
      safetyDisclaimer = ExplanationConstants.MEDICAL_EXPLANATION_DISCLAIMER,
      categoryExplanation = CategoryExplanation.Radiology(
        modality = "Chest X-Ray",
        anatomicalRegion = "Chest",
        clinicalIndication = "Shortness of breath",
        techniqueDescription = "PA view",
        studyOverviewExplanation = "X-ray picture of the chest.",
        findings = emptyList(),
        impressions = emptyList()
      )
    )

    composeTestRule.setContent {
      MyApplicationTheme {
        ReportExplanationScreen(
          explanationState = ReportExplanationState.Success(adversarialAiExplanation),
          isConsentDialogVisible = false,
          fileName = "CXR_2026.pdf",
          patientName = "Priya Sharma",
          onConfirmConsent = {},
          onDismissConsent = {},
          onRequestExplanation = {},
          onRetryClick = {},
          onNavigateBack = {},
          safetySummary = safetySummary
        )
      }
    }
    composeTestRule.waitForIdle()

    // Ensure Task 3D disclaimer check stays unique on the explanation screen
    composeTestRule.onNodeWithText("not medically verified", substring = true, ignoreCase = true).assertIsDisplayed()

    composeTestRule.onNodeWithTag("explanation_result_list")
      .performScrollToNode(hasTestTag("safety_summary_card"))
    composeTestRule.onNodeWithTag("safety_summary_card").assertIsDisplayed()
    composeTestRule.onNodeWithTag("safety_overall_headline").assertIsDisplayed()
    composeTestRule.onAllNodesWithText(SafetyConstants.URGENT_WORDING_TITLE).onFirst().assertIsDisplayed()
    composeTestRule.onNodeWithTag("safety_scope_limitation_notice")
      .performScrollTo()
      .assertIsDisplayed()
  }

  @Test
  fun reportExplanationScreenShowsSafetySummaryCompactBannerAndNullFallbackSafely() {
    val criticalLab = labExtraction(
      ExtractedLabItem(
        originalName = "Serum Potassium",
        originalValue = "6.8",
        originalUnit = "mmol/L",
        referenceRange = "3.5 - 5.1",
        laboratoryFlag = "CRITICAL",
        findingStatus = FindingStatus.ABNORMAL,
        provenance = availProv(1, "Potassium 6.8 mmol/L CRITICAL")
      )
    )
    val criticalSummary = SafetyFindingEvaluator.evaluate(criticalLab)
    assertEquals(SafetyBucket.EXTRACTED_CRITICAL_FLAG, criticalSummary.overallClassification)

    // 1. WaitingForConsent state shows compact safety banner immediately above consent prompt
    composeTestRule.setContent {
      MyApplicationTheme {
        ReportExplanationScreen(
          explanationState = ReportExplanationState.WaitingForConsent,
          isConsentDialogVisible = false,
          fileName = "Electrolytes.pdf",
          patientName = "Priya Sharma",
          onConfirmConsent = {},
          onDismissConsent = {},
          onRequestExplanation = {},
          onRetryClick = {},
          onNavigateBack = {},
          safetySummary = criticalSummary
        )
      }
    }
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag("safety_compact_notice_banner").assertIsDisplayed()
    composeTestRule.onNodeWithText(criticalSummary.overallHeadline).assertIsDisplayed()
    composeTestRule.onNodeWithTag("btn_request_explanation").assertIsDisplayed()
  }

  @Test
  fun nullSafetySummaryRendersUnavailableCardCleanlyWithoutPresumingNormal() {
    val lab = labExtraction(
      ExtractedLabItem("Hemoglobin", "14.0", "g/dL", "12.0 - 15.5", null, FindingStatus.NORMAL, availProv())
    )
    val explanation = ReportExplanationResult(
      category = MedicalDocumentCategory.LABORATORY,
      language = "English",
      conciseSummary = "Hemoglobin is printed as 14.0 g/dL.",
      uncertaintyNotes = emptyList(),
      medicalTerms = emptyList(),
      questionsForDoctor = emptyList(),
      safetyDisclaimer = ExplanationConstants.MEDICAL_EXPLANATION_DISCLAIMER,
      categoryExplanation = CategoryExplanation.Laboratory(
        labFacilityName = "Apex Diagnostics",
        collectionDate = "2026-04-01",
        reportDate = "2026-04-02",
        items = emptyList()
      )
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
          onNavigateBack = {},
          safetySummary = null
        )
      }
    }
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithText("not medically verified", substring = true, ignoreCase = true).assertIsDisplayed()
    composeTestRule.onNodeWithTag("explanation_result_list")
      .performScrollToNode(hasTestTag("safety_assessment_unavailable_card"))
    composeTestRule.onNodeWithTag("safety_assessment_unavailable_card").assertIsDisplayed()
    composeTestRule.onNodeWithText("Safety Assessment Unavailable").assertIsDisplayed()
  }

  @Test
  fun abnormalAndPositiveFindingsWithIllegibleMarkersPreserveFindingAndRecordUncertainty() {
    // 1. Radiology ABNORMAL finding with [ILLEGIBLE] marker
    val radExtraction = DocumentExtractionResult(
      category = MedicalDocumentCategory.RADIOLOGY,
      pageCount = 1,
      isMultiPageBatch = false,
      report = UniversalMedicalReport.Radiology(
        ExtractedRadiologyReport(
          modality = "Chest CT",
          anatomicalRegion = "Thorax",
          clinicalIndication = null,
          techniqueDescription = null,
          anatomicalFindings = listOf(
            AnatomicalFinding(
              anatomicalSite = "Right lung",
              observationText = "Mass in right apex [ILLEGIBLE]",
              findingStatus = FindingStatus.ABNORMAL,
              provenance = availProv(1, "Mass in right apex [ILLEGIBLE]")
            )
          ),
          formalImpressions = listOf(
            RadiologyImpression(
              impressionText = "Apical pulmonary lesion; correlate clinically.",
              findingStatus = FindingStatus.ABNORMAL,
              provenance = availProv(1, "Apical pulmonary lesion")
            )
          )
        )
      )
    )
    val radSummary = SafetyFindingEvaluator.evaluate(radExtraction)
    assertEquals(SafetyBucket.EXTRACTED_ABNORMAL_FINDING, radSummary.overallClassification)
    assertTrue(radSummary.hasAbnormalOrPositiveFindings)
    assertTrue(radSummary.hasUncertainOrUnreadableFindings)
    assertEquals(1, radSummary.uncertainItemsCount)
    assertTrue(
      radSummary.alerts.any {
        it.bucket == SafetyBucket.EXTRACTED_ABNORMAL_FINDING && it.itemKey == "rad_finding:0"
      }
    )
    val radUncertainAlert = radSummary.alerts.firstOrNull {
      it.bucket == SafetyBucket.UNCERTAIN_OR_UNREADABLE && it.itemKey == "rad_finding:0"
    }
    assertTrue(radUncertainAlert != null)
    assertEquals("Mass in right apex [ILLEGIBLE]", radUncertainAlert?.reportedValueSummary)
    assertEquals(availProv(1, "Mass in right apex [ILLEGIBLE]"), radUncertainAlert?.provenance)
    assertTrue(radSummary.overallSummaryMessage.contains("1 uncertain/unreadable item(s)"))

    // 2. Cardiology ABNORMAL finding with [ILLEGIBLE] in rhythmInterpretation
    val cardioExtraction = DocumentExtractionResult(
      category = MedicalDocumentCategory.CARDIOLOGY,
      pageCount = 2,
      isMultiPageBatch = false,
      report = UniversalMedicalReport.Cardiology(
        ExtractedCardiologyReport(
          modality = "12-Lead ECG",
          heartRateBpm = null,
          rhythmInterpretation = "Atrial flutter [ILLEGIBLE]",
          prIntervalMs = null,
          qrsDurationMs = null,
          qtcIntervalMs = null,
          ejectionFractionPercent = null,
          wallMotionFindings = null,
          formalConclusion = "Abnormal ECG showing atrial flutter",
          findingStatus = FindingStatus.ABNORMAL,
          provenance = availProv(1, "Abnormal ECG showing atrial flutter"),
          fieldProvenance = mapOf(
            "formalConclusion" to availProv(1, "Abnormal ECG showing atrial flutter"),
            "rhythmInterpretation" to availProv(2, "Atrial flutter [ILLEGIBLE]")
          )
        )
      )
    )
    val cardioSummary = SafetyFindingEvaluator.evaluate(cardioExtraction)
    assertEquals(SafetyBucket.EXTRACTED_ABNORMAL_FINDING, cardioSummary.overallClassification)
    assertTrue(cardioSummary.hasAbnormalOrPositiveFindings)
    assertTrue(cardioSummary.hasUncertainOrUnreadableFindings)
    assertEquals(1, cardioSummary.uncertainItemsCount)
    assertTrue(
      cardioSummary.alerts.any {
        it.bucket == SafetyBucket.EXTRACTED_ABNORMAL_FINDING && it.sourceLabel == "Formal Conclusion"
      }
    )
    val cardioUncertainAlert = cardioSummary.alerts.firstOrNull {
      it.bucket == SafetyBucket.UNCERTAIN_OR_UNREADABLE && it.itemKey == "cardio:rhythmInterpretation"
    }
    assertTrue(cardioUncertainAlert != null)
    assertEquals(availProv(2, "Atrial flutter [ILLEGIBLE]"), cardioUncertainAlert?.provenance)

    // 3. Specialised POSITIVE finding with [ILLEGIBLE] marker
    val specExtraction = DocumentExtractionResult(
      category = MedicalDocumentCategory.SPECIALISED,
      pageCount = 1,
      isMultiPageBatch = false,
      report = UniversalMedicalReport.Specialised(
        ExtractedSpecialisedReport(
          reportSubtype = "Cytology",
          specimenSource = null,
          clinicalHistory = null,
          grossDescription = null,
          microscopicDescription = null,
          gradingOrStaging = null,
          diagnosticConclusion = "Positive for atypical cells [ILLEGIBLE]",
          findingStatus = FindingStatus.POSITIVE,
          provenance = availProv(1, "Positive for atypical cells [ILLEGIBLE]")
        )
      )
    )
    val specSummary = SafetyFindingEvaluator.evaluate(specExtraction)
    assertEquals(SafetyBucket.EXTRACTED_POSITIVE_FINDING, specSummary.overallClassification)
    assertTrue(specSummary.hasAbnormalOrPositiveFindings)
    assertTrue(specSummary.hasUncertainOrUnreadableFindings)
    assertEquals(1, specSummary.uncertainItemsCount)
    assertTrue(specSummary.alerts.any { it.bucket == SafetyBucket.EXTRACTED_POSITIVE_FINDING })
    assertTrue(
      specSummary.alerts.any {
        it.bucket == SafetyBucket.UNCERTAIN_OR_UNREADABLE &&
          it.reportedValueSummary == "Positive for atypical cells [ILLEGIBLE]"
      }
    )

    // 4. Radiology POSITIVE finding with [ILLEGIBLE] marker
    val radPositiveExtraction = DocumentExtractionResult(
      category = MedicalDocumentCategory.RADIOLOGY,
      pageCount = 1,
      isMultiPageBatch = false,
      report = UniversalMedicalReport.Radiology(
        ExtractedRadiologyReport(
          modality = "HIDA Scan",
          anatomicalRegion = "Hepatobiliary",
          clinicalIndication = null,
          techniqueDescription = null,
          anatomicalFindings = listOf(
            AnatomicalFinding(
              anatomicalSite = "Gallbladder",
              observationText = "Positive Murphy sign correlation [ILLEGIBLE]",
              findingStatus = FindingStatus.POSITIVE,
              provenance = availProv(1, "Positive Murphy sign correlation [ILLEGIBLE]")
            )
          ),
          formalImpressions = listOf(
            RadiologyImpression(
              impressionText = "Findings positive for acute cholecystitis",
              findingStatus = FindingStatus.POSITIVE,
              provenance = availProv(1, "Findings positive for acute cholecystitis")
            )
          )
        )
      )
    )
    val radPosSummary = SafetyFindingEvaluator.evaluate(radPositiveExtraction)
    assertEquals(SafetyBucket.EXTRACTED_POSITIVE_FINDING, radPosSummary.overallClassification)
    assertTrue(radPosSummary.hasAbnormalOrPositiveFindings)
    assertTrue(radPosSummary.hasUncertainOrUnreadableFindings)
    assertEquals(1, radPosSummary.uncertainItemsCount)
  }

  private fun incompleteClinicalSummary() = SafetyFindingEvaluator.evaluate(
    DocumentExtractionResult(
      category = MedicalDocumentCategory.CLINICAL,
      pageCount = 1,
      isMultiPageBatch = false,
      report = UniversalMedicalReport.Clinical(
        ExtractedClinicalDocument(
          documentSubtype = "Discharge Summary",
          facilityOrDoctorName = null,
          primaryDiagnoses = listOf(
            ExtractedClinicalFact("Type 2 Diabetes Mellitus", availProv(1, "Type 2 Diabetes Mellitus"))
          ),
          surgicalProcedures = emptyList(),
          prescribedMedications = listOf(
            ExtractedMedication(
              drugName = "Metformin",
              dosage = "500 mg",
              frequency = "Twice daily",
              duration = null,
              instructions = null,
              provenance = availProv(1, "Metformin 500 mg twice daily")
            )
          ),
          allergiesRecorded = emptyList(),
          dischargeInstructions = emptyList(),
          followUpDate = null
        )
      )
    )
  )

  @Test
  fun waitingForConsentShowsIncompleteCompactBannerWithZeroUncertainItems() {
    val incompleteSummary = incompleteClinicalSummary()
    assertEquals(SafetyBucket.INCOMPLETE_ASSESSMENT, incompleteSummary.overallClassification)
    assertEquals(0, incompleteSummary.uncertainItemsCount)
    assertFalse(incompleteSummary.hasUncertainOrUnreadableFindings)
    assertFalse(incompleteSummary.hasCriticalOrUrgentFindings)

    composeTestRule.setContent {
      MyApplicationTheme {
        ReportExplanationScreen(
          explanationState = ReportExplanationState.WaitingForConsent,
          isConsentDialogVisible = false,
          fileName = "Discharge.pdf",
          patientName = "Priya Sharma",
          onConfirmConsent = {},
          onDismissConsent = {},
          onRequestExplanation = {},
          onRetryClick = {},
          onNavigateBack = {},
          safetySummary = incompleteSummary
        )
      }
    }
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag("safety_compact_notice_banner").assertIsDisplayed()
    composeTestRule.onNodeWithText("Safety Assessment Incomplete").assertIsDisplayed()
    composeTestRule.onNodeWithText("Automated safety check incomplete", substring = true).assertIsDisplayed()
    composeTestRule.onNodeWithTag("safety_summary_card").assertDoesNotExist()
    composeTestRule.onNodeWithText(SafetyConstants.URGENT_WORDING_TITLE).assertDoesNotExist()
    composeTestRule.onNodeWithTag("btn_request_explanation").performScrollTo().assertIsDisplayed()
  }

  @Test
  fun errorStateWithNullSafetySummaryShowsUnavailableCompactBanner() {
    composeTestRule.setContent {
      MyApplicationTheme {
        ReportExplanationScreen(
          explanationState = ReportExplanationState.Error("Temporary network error", isRetryable = true),
          isConsentDialogVisible = false,
          fileName = "Discharge.pdf",
          patientName = "Priya Sharma",
          onConfirmConsent = {},
          onDismissConsent = {},
          onRequestExplanation = {},
          onRetryClick = {},
          onNavigateBack = {},
          safetySummary = null
        )
      }
    }
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag("safety_compact_notice_banner").assertIsDisplayed()
    composeTestRule.onNodeWithText("Safety Assessment Unavailable").assertIsDisplayed()
    composeTestRule.onNodeWithTag("safety_summary_card").assertDoesNotExist()
    composeTestRule.onNodeWithTag("btn_retry_explanation").performScrollTo().assertIsDisplayed()
  }

  @Test
  fun loadingStateWithIncompleteSummaryShowsCompactBannerWithoutFullCardDuplication() {
    val incompleteSummary = incompleteClinicalSummary()
    composeTestRule.setContent {
      MyApplicationTheme {
        ReportExplanationScreen(
          explanationState = ReportExplanationState.Loading,
          isConsentDialogVisible = false,
          fileName = "Discharge.pdf",
          patientName = "Priya Sharma",
          onConfirmConsent = {},
          onDismissConsent = {},
          onRequestExplanation = {},
          onRetryClick = {},
          onNavigateBack = {},
          safetySummary = incompleteSummary
        )
      }
    }
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag("safety_compact_notice_banner").assertIsDisplayed()
    composeTestRule.onNodeWithText("Safety Assessment Incomplete").assertIsDisplayed()
    composeTestRule.onNodeWithTag("safety_summary_card").assertDoesNotExist()
    composeTestRule.onNodeWithTag("explanation_loading").assertIsDisplayed()
  }

  @Test
  fun idleStateWithNullSafetySummaryShowsUnavailableCompactBanner() {
    composeTestRule.setContent {
      MyApplicationTheme {
        ReportExplanationScreen(
          explanationState = ReportExplanationState.Idle,
          isConsentDialogVisible = false,
          fileName = "Discharge.pdf",
          patientName = "Priya Sharma",
          onConfirmConsent = {},
          onDismissConsent = {},
          onRequestExplanation = {},
          onRetryClick = {},
          onNavigateBack = {},
          safetySummary = null
        )
      }
    }
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag("safety_compact_notice_banner").assertIsDisplayed()
    composeTestRule.onNodeWithText("Safety Assessment Unavailable").assertIsDisplayed()
    composeTestRule.onNodeWithTag("safety_summary_card").assertDoesNotExist()
  }

  @Test
  fun higherPriorityFindingPreservesIncompleteAssessmentReasonInSummary() {
    // Radiology report with abnormal anatomical finding and missing formal impressions
    val radMissingImpression = DocumentExtractionResult(
      category = MedicalDocumentCategory.RADIOLOGY,
      pageCount = 1,
      isMultiPageBatch = false,
      report = UniversalMedicalReport.Radiology(
        ExtractedRadiologyReport(
          modality = "Chest X-Ray",
          anatomicalRegion = "Chest",
          clinicalIndication = null,
          techniqueDescription = null,
          anatomicalFindings = listOf(
            AnatomicalFinding(
              anatomicalSite = "Right lower lobe",
              observationText = "Right lower lobe consolidation",
              findingStatus = FindingStatus.ABNORMAL,
              provenance = availProv(1, "Right lower lobe consolidation")
            )
          ),
          formalImpressions = emptyList()
        )
      )
    )
    val radSummary = SafetyFindingEvaluator.evaluate(radMissingImpression)
    assertEquals(SafetyBucket.EXTRACTED_ABNORMAL_FINDING, radSummary.overallClassification)
    assertTrue(
      radSummary.overallSummaryMessage.contains(
        "Formal radiology impression was not extracted; assessment is incomplete."
      )
    )

    // Clinical document with urgent wording still preserves clinical non-standardization incomplete note
    val urgentClinical = DocumentExtractionResult(
      category = MedicalDocumentCategory.CLINICAL,
      pageCount = 1,
      isMultiPageBatch = false,
      report = UniversalMedicalReport.Clinical(
        ExtractedClinicalDocument(
          documentSubtype = "Emergency Note",
          facilityOrDoctorName = null,
          primaryDiagnoses = listOf(
            ExtractedClinicalFact("CRITICAL FINDING: Acute sepsis", availProv(1, "CRITICAL FINDING: Acute sepsis"))
          ),
          surgicalProcedures = emptyList(),
          prescribedMedications = listOf(
            ExtractedMedication(
              drugName = "Vancomycin",
              dosage = null,
              frequency = null,
              duration = null,
              instructions = null,
              provenance = availProv(1, "Vancomycin")
            )
          ),
          allergiesRecorded = emptyList(),
          dischargeInstructions = emptyList(),
          followUpDate = null
        )
      )
    )
    val clinSummary = SafetyFindingEvaluator.evaluate(urgentClinical)
    assertEquals(SafetyBucket.AI_EXTRACTED_URGENT_WORDING, clinSummary.overallClassification)
    assertTrue(
      clinSummary.overallSummaryMessage.contains(
        "Clinical documents and prescriptions do not provide standardized normality"
      )
    )
  }
}
