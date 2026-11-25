package com.example.domain.engine

import com.example.model.DocumentExtractionResult
import com.example.model.EvidenceOrigin
import com.example.model.ExtractedCardiologyReport
import com.example.model.ExtractedClinicalDocument
import com.example.model.ExtractedLabItem
import com.example.model.ExtractedLaboratoryReport
import com.example.model.ExtractedRadiologyReport
import com.example.model.ExtractedSpecialisedReport
import com.example.model.FieldProvenance
import com.example.model.FindingStatus
import com.example.model.MedicalDocumentCategory
import com.example.model.RangeComparisonResult
import com.example.model.RangeComparisonStatus
import com.example.model.ReportSafetySummary
import com.example.model.SafetyActionGuidance
import com.example.model.SafetyBucket
import com.example.model.SafetyConstants
import com.example.model.SafetyFindingAlert
import com.example.model.UnavailableComparisonNotice
import com.example.model.UniversalMedicalReport
import com.example.model.ValidatedRuleReference
import java.util.Locale
import kotlin.math.abs

/**
 * Pure Kotlin deterministic safety evaluator for Task 3E.
 *
 * Operates exclusively on immutable Task 3C [DocumentExtractionResult] data and executable rules
 * from [SafetyRuleRegistry]. Never accepts AI-generated Task 3D explanation output, never
 * invents reference ranges or thresholds, and never performs implicit unit conversion.
 */
object SafetyFindingEvaluator {

  // ============================================================================
  // 1. CONSERVATIVE LABORATORY FLAG TOKENS (COMPLETE TOKENS/PHRASES ONLY)
  // ============================================================================

  private val CRITICAL_LAB_FLAGS = setOf(
    "CRITICAL", "CRIT", "CRITICAL HIGH", "CRITICAL LOW",
    "CRIT HIGH", "CRIT LOW", "CRIT H", "CRIT L",
    "PANIC", "PANIC HIGH", "PANIC LOW", "PANIC VALUE", "CRITICAL VALUE"
  )

  private val ORDINARY_ABNORMAL_LAB_FLAGS = setOf(
    "H", "L", "HIGH", "LOW", "ABNORMAL", "ABNORM", "ABN", "OUT OF RANGE", "OOR"
  )

  private val LEGEND_DEPENDENT_AMBIGUOUS_FLAGS = setOf(
    "A", "*", "**", "***", "HH", "LL", "!!", "CALL"
  )

  private val NEGATED_FLAG_REGEX = Regex(
    "(?i)\\b(?:NON[- ]?CRITICAL|NOT\\s+CRITICAL|NO\\s+CRITICAL|NON[- ]?PANIC|NOT\\s+PANIC|" +
      "NON[- ]?ABNORMAL|NOT\\s+ABNORMAL|NOT\\s+HIGH|NOT\\s+LOW|NOT\\s+OUT\\s+OF\\s+RANGE)\\b"
  )

  private enum class LabFlagClass {
    NONE,
    CRITICAL,
    ABNORMAL,
    STAT_PRIORITY,
    LEGEND_DEPENDENT_OR_UNKNOWN,
    NEGATED_NO_ALERT
  }

  // ============================================================================
  // 2. NARROW EXPLICIT URGENT HEADER & NEGATION / CONTEXT GUARDS
  // ============================================================================

  private val EXPLICIT_URGENT_HEADER_REGEX = Regex(
    "(?i)\\b(?:CRITICAL\\s+FINDING|CRITICAL\\s+RESULT|CRITICAL\\s+VALUE|CRITICAL\\s+ALERT|" +
      "PANIC\\s+VALUE|URGENT\\s+CRITICAL\\s+COMMUNICATION|URGENT\\s+ALERT|CRITICAL\\s+COMMUNICATION)\\s*(?::|-|\\b)"
  )

  private val EXPLICIT_ABNORMAL_LABEL_IN_TEXT_REGEX = Regex(
    "(?i)\\b(?:ABNORMAL\\s+ECG|ABNORMAL\\s+FINDING|ABNORMAL\\s+STUDY|ABNORMAL\\s+REPORT)\\b"
  )

  private val UNREADABLE_TEXT_MARKER_REGEX = Regex(
    "(?i)(?:\\[\\s*(?:ILLEGIBLE|UNREADABLE|UNCERTAIN|CROPPED|BLURRED)\\s*\\]|" +
      "\\b(?:ILLEGIBLE|UNREADABLE)\\b)"
  )

  private val NEGATION_CONTEXT_REGEX = Regex(
    "(?i)\\b(?:NO|NOT|WITHOUT|NEGATIVE\\s+FOR|NO\\s+EVIDENCE\\s+OF|ABSENT|FREE\\s+OF|" +
      "NON[- ]?CRITICAL|NON[- ]?URGENT|DENIES|DENIED)\\b"
  )

  private val UNCERTAINTY_OR_RULE_OUT_CONTEXT_REGEX = Regex(
    "(?i)\\b(?:RULE\\s+OUT|SUSPECTED|POSSIBLE|QUESTION\\s+OF|TO\\s+EXCLUDE|DIFFERENTIAL|" +
      "IF|UNLESS|IN\\s+CASE\\s+OF|SHOULD\\s+BE\\s+REPORTED)\\b|\\bR\\s*/\\s*O\\b"
  )

  private val HISTORICAL_CONTEXT_REGEX = Regex(
    "(?i)\\b(?:HISTORY\\s+OF|HX\\s+OF|PRIOR|PREVIOUS|PREVIOUSLY|RESOLVED|PAST)\\b"
  )

  // ============================================================================
  // 3. STRICT NUMERIC VALUE & REFERENCE RANGE GRAMMAR
  // ============================================================================

  private const val STRICT_NUM_PATTERN = "[+-]?(?:\\d{1,3}(?:,\\d{3})+(?:\\.\\d+)?|\\d+(?:\\.\\d+)?)"
  private const val UNIT_TOKEN_PATTERN = "[A-Za-zµμ%][A-Za-z0-9µμ/%^.*-]*"

  private val STRICT_VALUE_REGEX = Regex("^$STRICT_NUM_PATTERN$")

  private val CLOSED_INTERVAL_REGEX = Regex(
    "^($STRICT_NUM_PATTERN)\\s*(?:-|–|—|\\bto\\b)\\s*($STRICT_NUM_PATTERN)(?:\\s+($UNIT_TOKEN_PATTERN))?$",
    RegexOption.IGNORE_CASE
  )

  private val UPPER_BOUND_REGEX = Regex(
    "^(<=|≤|<|\\bup\\s+to\\b|\\bbelow\\b|\\bless\\s+than\\b)\\s*($STRICT_NUM_PATTERN)(?:\\s+($UNIT_TOKEN_PATTERN))?$",
    RegexOption.IGNORE_CASE
  )

  private val LOWER_BOUND_REGEX = Regex(
    "^(>=|≥|>|\\babove\\b|\\bgreater\\s+than\\b|\\bmore\\s+than\\b)\\s*($STRICT_NUM_PATTERN)(?:\\s+($UNIT_TOKEN_PATTERN))?$",
    RegexOption.IGNORE_CASE
  )

  private val UNSUPPORTED_RANGE_KEYWORD_REGEX = Regex(
    "(?i)\\b(?:MALE|FEMALE|MEN|WOMEN|M|F|BOY|GIRL|ADULT|ADULTS|CHILD|CHILDREN|PEDIATRIC|" +
      "NEONATE|NEWBORN|INFANT|TRIMESTER|PREGNANT|PREGNANCY|FOLLICULAR|LUTEAL|OVULATION|" +
      "MIDCYCLE|POSTMENOPAUSAL|PREMENOPAUSAL|AGE|YR|YRS|YEAR|YEARS|MONTH|MONTHS|DAY|DAYS|" +
      "WEEK|WEEKS|FASTING|RANDOM|POSTPRANDIAL|PP|DESIRABLE|OPTIMAL|BORDERLINE|ELEVATED|" +
      "HIGH|LOW|NORMAL|CRITICAL|NEGATIVE|POSITIVE|OR|AND)\\b"
  )

  // ============================================================================
  // PUBLIC EVALUATION ENTRY POINT
  // ============================================================================

  fun evaluate(
    extraction: DocumentExtractionResult,
    registry: SafetyRuleRegistry = SafetyRuleRegistry.productionDefault()
  ): ReportSafetySummary {
    val validatedRuleAlerts = registry.evaluateExecutableRules(extraction).mapIndexed { idx, match ->
      SafetyFindingAlert(
        id = "rule_${match.ruleId}_${match.ruleVersion}_$idx",
        itemKey = null,
        category = extraction.category,
        bucket = SafetyBucket.RULE_VALIDATED_ALERT,
        evidenceOrigin = EvidenceOrigin.CLINICALLY_VALIDATED_RULE,
        title = "Clinically Validated Alert (${match.ruleId} v${match.ruleVersion})",
        sourceLabel = match.sourceLabel,
        reportedValueSummary = match.matchedEvidenceSnippet,
        explanationText = match.summaryMessage,
        actionGuidance = when (match.targetSeverity) {
          SafetyRuleTargetSeverity.CRITICAL_ALERT -> SafetyActionGuidance.URGENT_PROVIDER_REVIEW
          SafetyRuleTargetSeverity.ABNORMAL_ALERT,
          SafetyRuleTargetSeverity.POSITIVE_ALERT -> SafetyActionGuidance.PROMPT_DOCTOR_REVIEW
        },
        provenance = match.provenance,
        ruleReference = ValidatedRuleReference(
          ruleId = match.ruleId,
          ruleVersion = match.ruleVersion,
          rulesetVersion = match.rulesetVersion,
          clinicalEvidenceRef = match.clinicalEvidenceRef
        )
      )
    }

    val accumulator = EvalAccumulator()
    accumulator.alerts.addAll(validatedRuleAlerts)

    when (val report = extraction.report) {
      is UniversalMedicalReport.Laboratory -> evaluateLaboratory(report.report, accumulator)
      is UniversalMedicalReport.Radiology -> evaluateRadiology(report.report, accumulator)
      is UniversalMedicalReport.Cardiology -> evaluateCardiology(report.report, accumulator)
      is UniversalMedicalReport.Specialised -> evaluateSpecialised(report.report, accumulator)
      is UniversalMedicalReport.Clinical -> evaluateClinical(report.report, accumulator)
      is UniversalMedicalReport.Unidentified -> evaluateUnidentified(report, accumulator)
    }

    return buildFinalSummary(
      extraction = extraction,
      acc = accumulator,
      registry = registry
    )
  }

  private class EvalAccumulator {
    val alerts = mutableListOf<SafetyFindingAlert>()
    val careInstructions = mutableListOf<SafetyFindingAlert>()
    val unavailableComparisons = mutableListOf<UnavailableComparisonNotice>()
    var evaluatedItemsCount: Int = 0
    var uncertainItemsCount: Int = 0
    var conflictsCount: Int = 0
    var unavailableComparisonsCount: Int = 0
    var requiresIncompleteAssessment: Boolean = false
    var incompleteReason: String? = null
  }

  // ============================================================================
  // 1. LABORATORY EVALUATION
  // ============================================================================

  private fun evaluateLaboratory(lab: ExtractedLaboratoryReport, acc: EvalAccumulator) {
    if (lab.items.isEmpty()) {
      acc.requiresIncompleteAssessment = true
      acc.incompleteReason = "No laboratory parameters were extracted from this document."
      return
    }

    lab.items.forEachIndexed { idx, item ->
      val itemKey = "lab:$idx"
      val label = item.originalName ?: "Laboratory Parameter #${idx + 1}"
      val valueSummary = formatLabValueSummary(item)
      val flagClass = classifyLabFlag(item.laboratoryFlag)
      val comparison = comparePrintedRange(item)

      if (comparison.status == RangeComparisonStatus.UNAVAILABLE) {
        acc.unavailableComparisonsCount += 1
        acc.unavailableComparisons.add(
          UnavailableComparisonNotice(
            itemKey = itemKey,
            itemLabel = label,
            reason = comparison.unavailableReason
              ?: "Automated range comparison could not be performed safely.",
            provenance = item.provenance
          )
        )
      }

      val isNormalOrNegativeStatus =
        item.findingStatus == FindingStatus.NORMAL || item.findingStatus == FindingStatus.NEGATIVE
      val hasCriticalOrAbnormalFlag =
        flagClass == LabFlagClass.CRITICAL || flagClass == LabFlagClass.ABNORMAL

      var itemConflictReason: String? = null
      if (isNormalOrNegativeStatus && (hasCriticalOrAbnormalFlag || comparison.status == RangeComparisonStatus.OUTSIDE_RANGE)) {
        acc.conflictsCount += 1
        itemConflictReason = when {
          flagClass == LabFlagClass.CRITICAL ->
            "Conflict in extracted fields: status is ${item.findingStatus.name}, but extracted lab flag is '${item.laboratoryFlag}'."
          flagClass == LabFlagClass.ABNORMAL ->
            "Conflict in extracted fields: status is ${item.findingStatus.name}, but extracted lab flag is '${item.laboratoryFlag}'."
          else ->
            "Conflict in extracted fields: status is ${item.findingStatus.name}, but extracted value falls outside the extracted reference range."
        }
      }

      when {
        flagClass == LabFlagClass.CRITICAL -> {
          acc.evaluatedItemsCount += 1
          acc.alerts.add(
            SafetyFindingAlert(
              id = "lab_crit_$idx",
              itemKey = itemKey,
              category = MedicalDocumentCategory.LABORATORY,
              bucket = SafetyBucket.EXTRACTED_CRITICAL_FLAG,
              evidenceOrigin = EvidenceOrigin.AI_EXTRACTED_LAB_FLAG,
              title = "Critical Lab Flag Extracted ('${item.laboratoryFlag}') — Verify Original Report",
              sourceLabel = label,
              reportedValueSummary = valueSummary,
              explanationText =
                "The AI-extracted laboratory flag '${item.laboratoryFlag}' indicates a critical or panic marking. " +
                  "Verify the original report and urgently contact the reporting doctor, clinic, or laboratory. " +
                  "If you have severe or worsening symptoms, seek immediate emergency care without waiting.",
              actionGuidance = SafetyActionGuidance.URGENT_PROVIDER_REVIEW,
              provenance = item.provenance,
              rangeComparisonStatus = comparison.status,
              comparisonUnavailableReason = comparison.unavailableReason,
              conflictReason = itemConflictReason
            )
          )
        }

        flagClass == LabFlagClass.ABNORMAL -> {
          acc.evaluatedItemsCount += 1
          acc.alerts.add(
            SafetyFindingAlert(
              id = "lab_flag_abn_$idx",
              itemKey = itemKey,
              category = MedicalDocumentCategory.LABORATORY,
              bucket = SafetyBucket.EXTRACTED_ABNORMAL_FINDING,
              evidenceOrigin = EvidenceOrigin.AI_EXTRACTED_LAB_FLAG,
              title = "Abnormal Lab Flag Extracted ('${item.laboratoryFlag}')",
              sourceLabel = label,
              reportedValueSummary = valueSummary,
              explanationText =
                "The AI-extracted laboratory flag '${item.laboratoryFlag}' indicates an out-of-range or abnormal entry. " +
                  "Review the original report and discuss this result with your doctor.",
              actionGuidance = SafetyActionGuidance.PROMPT_DOCTOR_REVIEW,
              provenance = item.provenance,
              rangeComparisonStatus = comparison.status,
              comparisonUnavailableReason = comparison.unavailableReason,
              conflictReason = itemConflictReason
            )
          )
        }

        item.findingStatus == FindingStatus.ABNORMAL -> {
          acc.evaluatedItemsCount += 1
          val legendExtra = legendCautionText(flagClass, item.laboratoryFlag)
          acc.alerts.add(
            SafetyFindingAlert(
              id = "lab_status_abn_$idx",
              itemKey = itemKey,
              category = MedicalDocumentCategory.LABORATORY,
              bucket = SafetyBucket.EXTRACTED_ABNORMAL_FINDING,
              evidenceOrigin = EvidenceOrigin.AI_EXTRACTED_FINDING_STATUS,
              title = "Extracted Status: Abnormal",
              sourceLabel = label,
              reportedValueSummary = valueSummary,
              explanationText =
                "Categorized as abnormal from the AI-extracted report entry. " +
                  (if (legendExtra != null) "$legendExtra " else "") +
                  "Verify on your original report and discuss with your doctor.",
              actionGuidance = SafetyActionGuidance.PROMPT_DOCTOR_REVIEW,
              provenance = item.provenance,
              rangeComparisonStatus = comparison.status,
              comparisonUnavailableReason = comparison.unavailableReason
            )
          )
        }

        item.findingStatus == FindingStatus.POSITIVE -> {
          acc.evaluatedItemsCount += 1
          val legendExtra = legendCautionText(flagClass, item.laboratoryFlag)
          acc.alerts.add(
            SafetyFindingAlert(
              id = "lab_pos_$idx",
              itemKey = itemKey,
              category = MedicalDocumentCategory.LABORATORY,
              bucket = SafetyBucket.EXTRACTED_POSITIVE_FINDING,
              evidenceOrigin = EvidenceOrigin.AI_EXTRACTED_FINDING_STATUS,
              title = "Extracted Status: Positive (As Extracted)",
              sourceLabel = label,
              reportedValueSummary = valueSummary,
              explanationText =
                "Categorized as positive in the AI-extracted report entry. A positive test requires clinical interpretation " +
                  (if (legendExtra != null) "($legendExtra) " else "") +
                  "— confirm on your original report and review with your doctor.",
              actionGuidance = SafetyActionGuidance.PROMPT_DOCTOR_REVIEW,
              provenance = item.provenance,
              rangeComparisonStatus = comparison.status,
              comparisonUnavailableReason = comparison.unavailableReason
            )
          )
        }

        comparison.status == RangeComparisonStatus.OUTSIDE_RANGE -> {
          acc.evaluatedItemsCount += 1
          acc.alerts.add(
            SafetyFindingAlert(
              id = "lab_outside_range_$idx",
              itemKey = itemKey,
              category = MedicalDocumentCategory.LABORATORY,
              bucket = SafetyBucket.OUTSIDE_EXTRACTED_REFERENCE_RANGE,
              evidenceOrigin = EvidenceOrigin.EXTRACTED_RANGE_NUMERIC_COMPARISON,
              title = "Outside Extracted Reference Range",
              sourceLabel = label,
              reportedValueSummary = valueSummary,
              explanationText =
                "Deterministic numeric comparison shows the extracted value falls outside the extracted reference range " +
                  "(${comparison.detailNote ?: "matching units verified"}). No abnormal lab flag was printed in the extracted flag field; " +
                  "verify on your original report and review with your doctor.",
              actionGuidance = SafetyActionGuidance.PROMPT_DOCTOR_REVIEW,
              provenance = item.provenance,
              rangeComparisonStatus = RangeComparisonStatus.OUTSIDE_RANGE,
              conflictReason = itemConflictReason
            )
          )
        }

        flagClass == LabFlagClass.LEGEND_DEPENDENT_OR_UNKNOWN || flagClass == LabFlagClass.STAT_PRIORITY -> {
          acc.uncertainItemsCount += 1
          val isStat = flagClass == LabFlagClass.STAT_PRIORITY
          acc.alerts.add(
            SafetyFindingAlert(
              id = "lab_flag_uncertain_$idx",
              itemKey = itemKey,
              category = MedicalDocumentCategory.LABORATORY,
              bucket = SafetyBucket.UNCERTAIN_OR_UNREADABLE,
              evidenceOrigin = EvidenceOrigin.MISSING_OR_CONFLICTING_EXTRACTED_DATA,
              title = if (isStat) {
                "Extracted Flag 'STAT' — Priority/Legend Verification Needed"
              } else {
                "Legend-Dependent Lab Flag Extracted ('${item.laboratoryFlag}')"
              },
              sourceLabel = label,
              reportedValueSummary = valueSummary,
              explanationText = if (isStat) {
                "The extracted token 'STAT' generally refers to test turnaround priority rather than a critical result, " +
                  "and cannot be confirmed as normal without reviewing the original report."
              } else {
                "The laboratory printed '${item.laboratoryFlag}'. Its meaning depends on the laboratory's flag legend " +
                  "(for example footnote, analyzer tier, abnormal, or critical marking). Check the original report legend promptly with your doctor."
              },
              actionGuidance = SafetyActionGuidance.VERIFY_WITH_ORIGINAL_REPORT,
              provenance = item.provenance,
              rangeComparisonStatus = comparison.status,
              comparisonUnavailableReason = comparison.unavailableReason
            )
          )
        }

        item.findingStatus == FindingStatus.UNCERTAIN_OR_UNREADABLE ||
          item.findingStatus == FindingStatus.NOT_MENTIONED ||
          item.originalValue.isNullOrBlank() -> {
          acc.uncertainItemsCount += 1
          acc.alerts.add(
            SafetyFindingAlert(
              id = "lab_uncertain_$idx",
              itemKey = itemKey,
              category = MedicalDocumentCategory.LABORATORY,
              bucket = SafetyBucket.UNCERTAIN_OR_UNREADABLE,
              evidenceOrigin = EvidenceOrigin.MISSING_OR_CONFLICTING_EXTRACTED_DATA,
              title = when {
                item.originalValue.isNullOrBlank() -> "Missing Extracted Value"
                item.findingStatus == FindingStatus.NOT_MENTIONED -> "Finding Status Not Mentioned"
                else -> "Uncertain or Unreadable Entry"
              },
              sourceLabel = label,
              reportedValueSummary = valueSummary,
              explanationText = comparison.unavailableReason
                ?: "This entry is missing a value, missing a finding status, or was marked uncertain/unreadable. Do not assume it is normal; check the original report.",
              actionGuidance = SafetyActionGuidance.VERIFY_WITH_ORIGINAL_REPORT,
              provenance = item.provenance,
              rangeComparisonStatus = comparison.status,
              comparisonUnavailableReason = comparison.unavailableReason
            )
          )
        }

        comparison.status == RangeComparisonStatus.UNAVAILABLE && isUnitVerificationFailure(comparison.unavailableReason) -> {
          acc.uncertainItemsCount += 1
          acc.alerts.add(
            SafetyFindingAlert(
              id = "lab_unit_unverified_$idx",
              itemKey = itemKey,
              category = MedicalDocumentCategory.LABORATORY,
              bucket = SafetyBucket.UNCERTAIN_OR_UNREADABLE,
              evidenceOrigin = EvidenceOrigin.MISSING_OR_CONFLICTING_EXTRACTED_DATA,
              title = "Unit Could Not Be Verified for Range Comparison",
              sourceLabel = label,
              reportedValueSummary = valueSummary,
              explanationText =
                "${comparison.unavailableReason} Automated verification could not confirm normality; review the original report with your doctor.",
              actionGuidance = SafetyActionGuidance.VERIFY_WITH_ORIGINAL_REPORT,
              provenance = item.provenance,
              rangeComparisonStatus = RangeComparisonStatus.UNAVAILABLE,
              comparisonUnavailableReason = comparison.unavailableReason
            )
          )
        }

        else -> {
          // Extracted NORMAL or NEGATIVE without conflict, without ambiguous flag, and without unit verification failure.
          // If comparison.status == UNAVAILABLE (e.g., malformed/multi-tier range), unavailableComparisonsCount > 0 ensures
          // overall summary resolves to INCOMPLETE_ASSESSMENT rather than unqualified NO_ABNORMALITY_IDENTIFIED_IN_EXTRACTED_FINDINGS.
          acc.evaluatedItemsCount += 1
        }
      }
    }
  }

  // ============================================================================
  // 2. RADIOLOGY EVALUATION
  // ============================================================================

  private fun evaluateRadiology(rad: ExtractedRadiologyReport, acc: EvalAccumulator) {
    if (rad.anatomicalFindings.isEmpty() && rad.formalImpressions.isEmpty()) {
      acc.requiresIncompleteAssessment = true
      acc.incompleteReason = "No written radiology findings or formal impressions were extracted."
      return
    }
    if (rad.formalImpressions.isEmpty()) {
      acc.requiresIncompleteAssessment = true
      acc.incompleteReason = "Formal radiology impression was not extracted; assessment is incomplete."
    }

    rad.anatomicalFindings.forEachIndexed { idx, finding ->
      evaluateRadiologyEntry(
        id = "rad_finding_$idx",
        itemKey = "rad_finding:$idx",
        label = finding.anatomicalSite ?: "Anatomical Finding #${idx + 1}",
        text = finding.observationText,
        status = finding.findingStatus,
        provenance = finding.provenance,
        acc = acc
      )
    }

    rad.formalImpressions.forEachIndexed { idx, imp ->
      evaluateRadiologyEntry(
        id = "rad_imp_$idx",
        itemKey = "rad_impression:$idx",
        label = "Formal Impression #${idx + 1}",
        text = imp.impressionText,
        status = imp.findingStatus,
        provenance = imp.provenance,
        acc = acc
      )
    }
  }

  private fun evaluateRadiologyEntry(
    id: String,
    itemKey: String,
    label: String,
    text: String?,
    status: FindingStatus,
    provenance: FieldProvenance,
    acc: EvalAccumulator
  ) {
    val cleanText = text?.trim()?.takeIf { it.isNotEmpty() }
    val hasUrgentHeader = cleanText != null && containsQualifiedUrgentHeader(cleanText)
    val hasUnreadableMarker = cleanText != null && UNREADABLE_TEXT_MARKER_REGEX.containsMatchIn(cleanText)
    val isNormalOrNegative = status == FindingStatus.NORMAL || status == FindingStatus.NEGATIVE

    var conflictReason: String? = null
    if (isNormalOrNegative && hasUrgentHeader) {
      acc.conflictsCount += 1
      conflictReason =
        "Conflict in extracted radiology entry: status is ${status.name}, but extracted text contains an urgent communication header."
    }

    when {
      hasUrgentHeader -> {
        acc.evaluatedItemsCount += 1
        acc.alerts.add(
          SafetyFindingAlert(
            id = id,
            itemKey = itemKey,
            category = MedicalDocumentCategory.RADIOLOGY,
            bucket = SafetyBucket.AI_EXTRACTED_URGENT_WORDING,
            evidenceOrigin = EvidenceOrigin.AI_EXTRACTED_NARRATIVE_TEXT,
            title = SafetyConstants.URGENT_WORDING_TITLE,
            sourceLabel = label,
            reportedValueSummary = cleanText,
            explanationText = SafetyConstants.URGENT_WORDING_BODY,
            actionGuidance = SafetyActionGuidance.URGENT_PROVIDER_REVIEW,
            provenance = provenance,
            conflictReason = conflictReason
          )
        )
        if (hasUnreadableMarker) {
          recordAdditionalIllegibleEvidence(
            id = "${id}_unreadable",
            itemKey = itemKey,
            category = MedicalDocumentCategory.RADIOLOGY,
            sourceLabel = label,
            reportedText = cleanText,
            provenance = provenance,
            acc = acc
          )
        }
      }

      status == FindingStatus.ABNORMAL -> {
        acc.evaluatedItemsCount += 1
        acc.alerts.add(
          SafetyFindingAlert(
            id = id,
            itemKey = itemKey,
            category = MedicalDocumentCategory.RADIOLOGY,
            bucket = SafetyBucket.EXTRACTED_ABNORMAL_FINDING,
            evidenceOrigin = EvidenceOrigin.AI_EXTRACTED_FINDING_STATUS,
            title = "Extracted Status: Abnormal Finding",
            sourceLabel = label,
            reportedValueSummary = cleanText,
            explanationText =
              "Categorized as abnormal from the AI-extracted radiology text. Verify the written report with your doctor.",
            actionGuidance = SafetyActionGuidance.PROMPT_DOCTOR_REVIEW,
            provenance = provenance
          )
        )
        if (hasUnreadableMarker) {
          recordAdditionalIllegibleEvidence(
            id = "${id}_unreadable",
            itemKey = itemKey,
            category = MedicalDocumentCategory.RADIOLOGY,
            sourceLabel = label,
            reportedText = cleanText,
            provenance = provenance,
            acc = acc
          )
        }
      }

      status == FindingStatus.POSITIVE -> {
        acc.evaluatedItemsCount += 1
        acc.alerts.add(
          SafetyFindingAlert(
            id = id,
            itemKey = itemKey,
            category = MedicalDocumentCategory.RADIOLOGY,
            bucket = SafetyBucket.EXTRACTED_POSITIVE_FINDING,
            evidenceOrigin = EvidenceOrigin.AI_EXTRACTED_FINDING_STATUS,
            title = "Extracted Status: Positive Finding",
            sourceLabel = label,
            reportedValueSummary = cleanText,
            explanationText =
              "Categorized as a positive observation in the AI-extracted radiology report. Verify on your original report with your doctor.",
            actionGuidance = SafetyActionGuidance.PROMPT_DOCTOR_REVIEW,
            provenance = provenance
          )
        )
        if (hasUnreadableMarker) {
          recordAdditionalIllegibleEvidence(
            id = "${id}_unreadable",
            itemKey = itemKey,
            category = MedicalDocumentCategory.RADIOLOGY,
            sourceLabel = label,
            reportedText = cleanText,
            provenance = provenance,
            acc = acc
          )
        }
      }

      status == FindingStatus.UNCERTAIN_OR_UNREADABLE ||
        status == FindingStatus.NOT_MENTIONED ||
        cleanText == null ||
        hasUnreadableMarker -> {
        acc.uncertainItemsCount += 1
        acc.alerts.add(
          SafetyFindingAlert(
            id = id,
            itemKey = itemKey,
            category = MedicalDocumentCategory.RADIOLOGY,
            bucket = SafetyBucket.UNCERTAIN_OR_UNREADABLE,
            evidenceOrigin = EvidenceOrigin.MISSING_OR_CONFLICTING_EXTRACTED_DATA,
            title = if (cleanText == null) "Missing Extracted Radiology Text" else "Uncertain or Unmentioned Radiology Finding",
            sourceLabel = label,
            reportedValueSummary = cleanText,
            explanationText =
              "This radiology entry is missing text, omitted a finding status, or was marked uncertain/unreadable. Check the original report with your doctor.",
            actionGuidance = SafetyActionGuidance.VERIFY_WITH_ORIGINAL_REPORT,
            provenance = provenance
          )
        )
      }

      else -> {
        acc.evaluatedItemsCount += 1
      }
    }
  }

  // ============================================================================
  // 3. CARDIOLOGY EVALUATION
  // ============================================================================

  private fun evaluateCardiology(cardio: ExtractedCardiologyReport, acc: EvalAccumulator) {
    val conclusionProv = cardio.fieldProvenance["formalConclusion"] ?: cardio.provenance
    val conclusionText = cardio.formalConclusion?.trim()?.takeIf { it.isNotEmpty() }

    val urgentFieldEntry = listOf(
      Triple("formalConclusion", "Formal Conclusion", conclusionText),
      Triple("rhythmInterpretation", "Rhythm Interpretation", cardio.rhythmInterpretation?.trim()?.takeIf { it.isNotEmpty() }),
      Triple("wallMotionFindings", "Wall Motion Findings", cardio.wallMotionFindings?.trim()?.takeIf { it.isNotEmpty() })
    ).firstOrNull { (_, _, text) -> text != null && containsQualifiedUrgentHeader(text) }

    val unreadableCardioFields = listOf(
      Triple("formalConclusion", "Formal Conclusion", conclusionText),
      Triple("rhythmInterpretation", "Rhythm Interpretation", cardio.rhythmInterpretation?.trim()?.takeIf { it.isNotEmpty() }),
      Triple("wallMotionFindings", "Wall Motion Findings", cardio.wallMotionFindings?.trim()?.takeIf { it.isNotEmpty() }),
      Triple("heartRateBpm", "Heart Rate", cardio.heartRateBpm?.trim()?.takeIf { it.isNotEmpty() }),
      Triple("prIntervalMs", "PR Interval", cardio.prIntervalMs?.trim()?.takeIf { it.isNotEmpty() }),
      Triple("qrsDurationMs", "QRS Duration", cardio.qrsDurationMs?.trim()?.takeIf { it.isNotEmpty() }),
      Triple("qtcIntervalMs", "QTc Interval", cardio.qtcIntervalMs?.trim()?.takeIf { it.isNotEmpty() }),
      Triple("ejectionFractionPercent", "Ejection Fraction", cardio.ejectionFractionPercent?.trim()?.takeIf { it.isNotEmpty() })
    ).filter { (_, _, text) -> text != null && UNREADABLE_TEXT_MARKER_REGEX.containsMatchIn(text) }

    val hasExplicitAbnormalConclusion = conclusionText != null &&
      containsQualifiedPattern(conclusionText, EXPLICIT_ABNORMAL_LABEL_IN_TEXT_REGEX)
    val isNormalOrNegative = cardio.findingStatus == FindingStatus.NORMAL ||
      cardio.findingStatus == FindingStatus.NEGATIVE

    var conflictReason: String? = null
    if (isNormalOrNegative && (urgentFieldEntry != null || hasExplicitAbnormalConclusion)) {
      acc.conflictsCount += 1
      conflictReason =
        "Conflict in extracted cardiology report: status is ${cardio.findingStatus.name}, but extracted text contains urgent or abnormal wording."
    }

    when {
      urgentFieldEntry != null -> {
        val (fieldKey, label, text) = urgentFieldEntry
        acc.evaluatedItemsCount += 1
        acc.alerts.add(
          SafetyFindingAlert(
            id = "cardio_urgent_$fieldKey",
            itemKey = "cardio:$fieldKey",
            category = MedicalDocumentCategory.CARDIOLOGY,
            bucket = SafetyBucket.AI_EXTRACTED_URGENT_WORDING,
            evidenceOrigin = EvidenceOrigin.AI_EXTRACTED_NARRATIVE_TEXT,
            title = SafetyConstants.URGENT_WORDING_TITLE,
            sourceLabel = label,
            reportedValueSummary = text,
            explanationText = SafetyConstants.URGENT_WORDING_BODY,
            actionGuidance = SafetyActionGuidance.URGENT_PROVIDER_REVIEW,
            provenance = cardio.fieldProvenance[fieldKey] ?: cardio.provenance,
            conflictReason = conflictReason
          )
        )
        unreadableCardioFields.forEach { (unreadableKey, unreadableLabel, unreadableText) ->
          recordAdditionalIllegibleEvidence(
            id = "cardio_unreadable_$unreadableKey",
            itemKey = "cardio:$unreadableKey",
            category = MedicalDocumentCategory.CARDIOLOGY,
            sourceLabel = unreadableLabel,
            reportedText = unreadableText,
            provenance = cardio.fieldProvenance[unreadableKey] ?: cardio.provenance,
            acc = acc
          )
        }
      }

      hasExplicitAbnormalConclusion && isNormalOrNegative -> {
        acc.uncertainItemsCount += 1
        acc.alerts.add(
          SafetyFindingAlert(
            id = "cardio_conflict",
            itemKey = "cardio:formalConclusion",
            category = MedicalDocumentCategory.CARDIOLOGY,
            bucket = SafetyBucket.UNCERTAIN_OR_UNREADABLE,
            evidenceOrigin = EvidenceOrigin.MISSING_OR_CONFLICTING_EXTRACTED_DATA,
            title = "Conflicting Cardiology Status & Conclusion",
            sourceLabel = "Formal Conclusion",
            reportedValueSummary = conclusionText,
            explanationText =
              "$conflictReason Verify the original cardiology report with your doctor.",
            actionGuidance = SafetyActionGuidance.VERIFY_WITH_ORIGINAL_REPORT,
            provenance = conclusionProv,
            conflictReason = conflictReason
          )
        )
        unreadableCardioFields
          .filter { (k, _, _) -> k != "formalConclusion" }
          .forEach { (unreadableKey, unreadableLabel, unreadableText) ->
            recordAdditionalIllegibleEvidence(
              id = "cardio_unreadable_$unreadableKey",
              itemKey = "cardio:$unreadableKey",
              category = MedicalDocumentCategory.CARDIOLOGY,
              sourceLabel = unreadableLabel,
              reportedText = unreadableText,
              provenance = cardio.fieldProvenance[unreadableKey] ?: cardio.provenance,
              acc = acc
            )
          }
      }

      cardio.findingStatus == FindingStatus.ABNORMAL -> {
        acc.evaluatedItemsCount += 1
        acc.alerts.add(
          SafetyFindingAlert(
            id = "cardio_abn",
            itemKey = "cardio:formalConclusion",
            category = MedicalDocumentCategory.CARDIOLOGY,
            bucket = SafetyBucket.EXTRACTED_ABNORMAL_FINDING,
            evidenceOrigin = EvidenceOrigin.AI_EXTRACTED_FINDING_STATUS,
            title = "Extracted Status: Abnormal Cardiology Report",
            sourceLabel = "Formal Conclusion",
            reportedValueSummary = conclusionText,
            explanationText =
              "Categorized as abnormal from the AI-extracted cardiology report. Review the original written report with your doctor.",
            actionGuidance = SafetyActionGuidance.PROMPT_DOCTOR_REVIEW,
            provenance = conclusionProv
          )
        )
        unreadableCardioFields.forEach { (unreadableKey, unreadableLabel, unreadableText) ->
          recordAdditionalIllegibleEvidence(
            id = "cardio_unreadable_$unreadableKey",
            itemKey = "cardio:$unreadableKey",
            category = MedicalDocumentCategory.CARDIOLOGY,
            sourceLabel = unreadableLabel,
            reportedText = unreadableText,
            provenance = cardio.fieldProvenance[unreadableKey] ?: cardio.provenance,
            acc = acc
          )
        }
      }

      cardio.findingStatus == FindingStatus.POSITIVE -> {
        acc.evaluatedItemsCount += 1
        acc.alerts.add(
          SafetyFindingAlert(
            id = "cardio_pos",
            itemKey = "cardio:formalConclusion",
            category = MedicalDocumentCategory.CARDIOLOGY,
            bucket = SafetyBucket.EXTRACTED_POSITIVE_FINDING,
            evidenceOrigin = EvidenceOrigin.AI_EXTRACTED_FINDING_STATUS,
            title = "Extracted Status: Positive Cardiology Finding",
            sourceLabel = "Formal Conclusion",
            reportedValueSummary = conclusionText,
            explanationText =
              "Categorized as a positive finding in the AI-extracted cardiology report. Verify on your original report with your doctor.",
            actionGuidance = SafetyActionGuidance.PROMPT_DOCTOR_REVIEW,
            provenance = conclusionProv
          )
        )
        unreadableCardioFields.forEach { (unreadableKey, unreadableLabel, unreadableText) ->
          recordAdditionalIllegibleEvidence(
            id = "cardio_unreadable_$unreadableKey",
            itemKey = "cardio:$unreadableKey",
            category = MedicalDocumentCategory.CARDIOLOGY,
            sourceLabel = unreadableLabel,
            reportedText = unreadableText,
            provenance = cardio.fieldProvenance[unreadableKey] ?: cardio.provenance,
            acc = acc
          )
        }
      }

      cardio.findingStatus == FindingStatus.UNCERTAIN_OR_UNREADABLE ||
        unreadableCardioFields.isNotEmpty() -> {
        acc.uncertainItemsCount += 1
        val firstUnreadable = unreadableCardioFields.firstOrNull()
        acc.alerts.add(
          SafetyFindingAlert(
            id = "cardio_uncertain",
            itemKey = "cardio:${firstUnreadable?.first ?: "formalConclusion"}",
            category = MedicalDocumentCategory.CARDIOLOGY,
            bucket = SafetyBucket.UNCERTAIN_OR_UNREADABLE,
            evidenceOrigin = EvidenceOrigin.MISSING_OR_CONFLICTING_EXTRACTED_DATA,
            title = "Uncertain or Unreadable Cardiology Report",
            sourceLabel = firstUnreadable?.second ?: "Cardiology Report",
            reportedValueSummary = firstUnreadable?.third ?: conclusionText,
            explanationText =
              "The extracted cardiology report was marked uncertain or unreadable. Check the original report with your doctor.",
            actionGuidance = SafetyActionGuidance.VERIFY_WITH_ORIGINAL_REPORT,
            provenance = firstUnreadable?.let { cardio.fieldProvenance[it.first] } ?: conclusionProv
          )
        )
      }

      cardio.findingStatus == FindingStatus.NOT_MENTIONED || conclusionText == null -> {
        acc.requiresIncompleteAssessment = true
        acc.incompleteReason = if (conclusionText == null) {
          "Formal cardiology conclusion was not extracted; individual measurements cannot be verified without a written conclusion."
        } else {
          "Cardiology finding status was not mentioned in the extracted report."
        }
      }

      else -> {
        acc.evaluatedItemsCount += 1
      }
    }
  }

  // ============================================================================
  // 4. SPECIALISED EVALUATION (BIOPSY / HISTOPATHOLOGY / ONCOLOGY)
  // ============================================================================

  private fun evaluateSpecialised(spec: ExtractedSpecialisedReport, acc: EvalAccumulator) {
    val conclusionProv = spec.fieldProvenance["diagnosticConclusion"] ?: spec.provenance
    val conclusionText = spec.diagnosticConclusion?.trim()?.takeIf { it.isNotEmpty() }

    // Evaluate diagnosticConclusion ONLY for narrow urgent headers (never clinicalHistory or general staging/correlation)
    val hasUrgentHeader = conclusionText != null && containsQualifiedUrgentHeader(conclusionText)
    val isNormalOrNegative = spec.findingStatus == FindingStatus.NORMAL ||
      spec.findingStatus == FindingStatus.NEGATIVE

    val unreadableSpecFields = listOf(
      Triple("diagnosticConclusion", "Diagnostic Conclusion", conclusionText),
      Triple("gradingOrStaging", "Grading / Staging", spec.gradingOrStaging?.trim()?.takeIf { it.isNotEmpty() }),
      Triple("microscopicDescription", "Microscopic Description", spec.microscopicDescription?.trim()?.takeIf { it.isNotEmpty() }),
      Triple("grossDescription", "Gross Description", spec.grossDescription?.trim()?.takeIf { it.isNotEmpty() }),
      Triple("specimenSource", "Specimen Source", spec.specimenSource?.trim()?.takeIf { it.isNotEmpty() })
    ).filter { (_, _, text) -> text != null && UNREADABLE_TEXT_MARKER_REGEX.containsMatchIn(text) }

    var conflictReason: String? = null
    if (isNormalOrNegative && hasUrgentHeader) {
      acc.conflictsCount += 1
      conflictReason =
        "Conflict in extracted pathology report: status is ${spec.findingStatus.name}, but diagnostic conclusion contains an urgent communication header."
    }

    when {
      hasUrgentHeader -> {
        acc.evaluatedItemsCount += 1
        acc.alerts.add(
          SafetyFindingAlert(
            id = "spec_urgent",
            itemKey = "spec:diagnosticConclusion",
            category = MedicalDocumentCategory.SPECIALISED,
            bucket = SafetyBucket.AI_EXTRACTED_URGENT_WORDING,
            evidenceOrigin = EvidenceOrigin.AI_EXTRACTED_NARRATIVE_TEXT,
            title = SafetyConstants.URGENT_WORDING_TITLE,
            sourceLabel = "Diagnostic Conclusion",
            reportedValueSummary = conclusionText,
            explanationText = SafetyConstants.URGENT_WORDING_BODY,
            actionGuidance = SafetyActionGuidance.URGENT_PROVIDER_REVIEW,
            provenance = conclusionProv,
            conflictReason = conflictReason
          )
        )
        unreadableSpecFields.forEach { (unreadableKey, unreadableLabel, unreadableText) ->
          recordAdditionalIllegibleEvidence(
            id = "spec_unreadable_$unreadableKey",
            itemKey = "spec:$unreadableKey",
            category = MedicalDocumentCategory.SPECIALISED,
            sourceLabel = unreadableLabel,
            reportedText = unreadableText,
            provenance = spec.fieldProvenance[unreadableKey] ?: spec.provenance,
            acc = acc
          )
        }
      }

      spec.findingStatus == FindingStatus.ABNORMAL -> {
        acc.evaluatedItemsCount += 1
        acc.alerts.add(
          SafetyFindingAlert(
            id = "spec_abn",
            itemKey = "spec:diagnosticConclusion",
            category = MedicalDocumentCategory.SPECIALISED,
            bucket = SafetyBucket.EXTRACTED_ABNORMAL_FINDING,
            evidenceOrigin = EvidenceOrigin.AI_EXTRACTED_FINDING_STATUS,
            title = "Extracted Status: Abnormal Pathology Finding",
            sourceLabel = "Diagnostic Conclusion",
            reportedValueSummary = conclusionText,
            explanationText =
              "Categorized as abnormal from the AI-extracted pathology report. Review the original written conclusion with your specialist or doctor.",
            actionGuidance = SafetyActionGuidance.PROMPT_DOCTOR_REVIEW,
            provenance = conclusionProv
          )
        )
        unreadableSpecFields.forEach { (unreadableKey, unreadableLabel, unreadableText) ->
          recordAdditionalIllegibleEvidence(
            id = "spec_unreadable_$unreadableKey",
            itemKey = "spec:$unreadableKey",
            category = MedicalDocumentCategory.SPECIALISED,
            sourceLabel = unreadableLabel,
            reportedText = unreadableText,
            provenance = spec.fieldProvenance[unreadableKey] ?: spec.provenance,
            acc = acc
          )
        }
      }

      spec.findingStatus == FindingStatus.POSITIVE -> {
        acc.evaluatedItemsCount += 1
        acc.alerts.add(
          SafetyFindingAlert(
            id = "spec_pos",
            itemKey = "spec:diagnosticConclusion",
            category = MedicalDocumentCategory.SPECIALISED,
            bucket = SafetyBucket.EXTRACTED_POSITIVE_FINDING,
            evidenceOrigin = EvidenceOrigin.AI_EXTRACTED_FINDING_STATUS,
            title = "Extracted Status: Positive Pathology Finding",
            sourceLabel = "Diagnostic Conclusion",
            reportedValueSummary = conclusionText,
            explanationText =
              "Categorized as positive in the AI-extracted pathology report. Discuss the full written report directly with your doctor.",
            actionGuidance = SafetyActionGuidance.PROMPT_DOCTOR_REVIEW,
            provenance = conclusionProv
          )
        )
        unreadableSpecFields.forEach { (unreadableKey, unreadableLabel, unreadableText) ->
          recordAdditionalIllegibleEvidence(
            id = "spec_unreadable_$unreadableKey",
            itemKey = "spec:$unreadableKey",
            category = MedicalDocumentCategory.SPECIALISED,
            sourceLabel = unreadableLabel,
            reportedText = unreadableText,
            provenance = spec.fieldProvenance[unreadableKey] ?: spec.provenance,
            acc = acc
          )
        }
      }

      spec.findingStatus == FindingStatus.UNCERTAIN_OR_UNREADABLE ||
        unreadableSpecFields.isNotEmpty() -> {
        acc.uncertainItemsCount += 1
        val firstUnreadable = unreadableSpecFields.firstOrNull()
        acc.alerts.add(
          SafetyFindingAlert(
            id = "spec_uncertain",
            itemKey = "spec:${firstUnreadable?.first ?: "diagnosticConclusion"}",
            category = MedicalDocumentCategory.SPECIALISED,
            bucket = SafetyBucket.UNCERTAIN_OR_UNREADABLE,
            evidenceOrigin = EvidenceOrigin.MISSING_OR_CONFLICTING_EXTRACTED_DATA,
            title = "Uncertain or Unreadable Pathology Report",
            sourceLabel = firstUnreadable?.second ?: "Specialised Pathology Report",
            reportedValueSummary = firstUnreadable?.third ?: conclusionText,
            explanationText =
              "This pathology entry was marked uncertain or unreadable. Review the original report with your specialist.",
            actionGuidance = SafetyActionGuidance.VERIFY_WITH_ORIGINAL_REPORT,
            provenance = firstUnreadable?.let { spec.fieldProvenance[it.first] } ?: conclusionProv
          )
        )
      }

      spec.findingStatus == FindingStatus.NOT_MENTIONED || conclusionText == null -> {
        acc.requiresIncompleteAssessment = true
        acc.incompleteReason = if (conclusionText == null) {
          "Pathologist diagnostic conclusion was not extracted from this report."
        } else {
          "Pathology finding status was not mentioned in the extracted report."
        }
      }

      else -> {
        acc.evaluatedItemsCount += 1
      }
    }
  }

  private fun recordAdditionalIllegibleEvidence(
    id: String,
    itemKey: String,
    category: MedicalDocumentCategory,
    sourceLabel: String,
    reportedText: String?,
    provenance: FieldProvenance,
    acc: EvalAccumulator
  ) {
    acc.uncertainItemsCount += 1
    acc.alerts.add(
      SafetyFindingAlert(
        id = id,
        itemKey = itemKey,
        category = category,
        bucket = SafetyBucket.UNCERTAIN_OR_UNREADABLE,
        evidenceOrigin = EvidenceOrigin.MISSING_OR_CONFLICTING_EXTRACTED_DATA,
        title = "Illegible or Uncertain Marker in Extracted Text",
        sourceLabel = sourceLabel,
        reportedValueSummary = reportedText,
        explanationText =
          "Part of this extracted entry contains an illegible or uncertain marker alongside the reported finding. Do not guess missing details; verify the original report with your doctor.",
        actionGuidance = SafetyActionGuidance.VERIFY_WITH_ORIGINAL_REPORT,
        provenance = provenance
      )
    )
  }

  // ============================================================================
  // 5. CLINICAL DOCUMENTS (PRESCRIPTIONS / DISCHARGE / NOTES)
  // ============================================================================

  private fun evaluateClinical(clinical: ExtractedClinicalDocument, acc: EvalAccumulator) {
    val hasAnyContent = clinical.primaryDiagnoses.isNotEmpty() ||
      clinical.surgicalProcedures.isNotEmpty() ||
      clinical.prescribedMedications.isNotEmpty() ||
      clinical.allergiesRecorded.isNotEmpty() ||
      clinical.dischargeInstructions.isNotEmpty() ||
      !clinical.documentSubtype.isNullOrBlank() ||
      !clinical.facilityOrDoctorName.isNullOrBlank()

    if (!hasAnyContent) {
      acc.uncertainItemsCount += 1
      acc.alerts.add(
        SafetyFindingAlert(
          id = "clin_empty",
          category = MedicalDocumentCategory.CLINICAL,
          bucket = SafetyBucket.UNCERTAIN_OR_UNREADABLE,
          evidenceOrigin = EvidenceOrigin.MISSING_OR_CONFLICTING_EXTRACTED_DATA,
          title = "No Clinical Content Extracted",
          sourceLabel = "Clinical Document",
          explanationText =
            "No diagnoses, medications, allergies, or instructions could be extracted from this clinical document.",
          actionGuidance = SafetyActionGuidance.VERIFY_WITH_ORIGINAL_REPORT,
          provenance = FieldProvenance.unavailable()
        )
      )
      return
    }

    // Discharge instructions -> Extracted Care Instructions (never an active emergency alert)
    clinical.dischargeInstructions.forEachIndexed { idx, inst ->
      val text = inst.text.trim()
      if (text.isNotEmpty()) {
        if (UNREADABLE_TEXT_MARKER_REGEX.containsMatchIn(text)) {
          acc.uncertainItemsCount += 1
          acc.alerts.add(
            SafetyFindingAlert(
              id = "clin_inst_unreadable_$idx",
              itemKey = "clinical_instruction:$idx",
              category = MedicalDocumentCategory.CLINICAL,
              bucket = SafetyBucket.UNCERTAIN_OR_UNREADABLE,
              evidenceOrigin = EvidenceOrigin.MISSING_OR_CONFLICTING_EXTRACTED_DATA,
              title = "Uncertain or Illegible Care Instruction",
              sourceLabel = "Discharge Instruction #${idx + 1}",
              reportedValueSummary = text,
              explanationText =
                "Part of this extracted care instruction may be illegible or uncertain. Verify on your original document.",
              actionGuidance = SafetyActionGuidance.VERIFY_WITH_ORIGINAL_REPORT,
              provenance = inst.provenance
            )
          )
        } else {
          acc.careInstructions.add(
            SafetyFindingAlert(
              id = "clin_inst_$idx",
              itemKey = "clinical_instruction:$idx",
              category = MedicalDocumentCategory.CLINICAL,
              bucket = SafetyBucket.EXTRACTED_CARE_INSTRUCTION,
              evidenceOrigin = EvidenceOrigin.AI_EXTRACTED_NARRATIVE_TEXT,
              title = "Extracted Care Instruction",
              sourceLabel = "Discharge / Care Instruction #${idx + 1}",
              reportedValueSummary = text,
              explanationText =
                "Printed care guidance extracted from your clinical document. Follow these instructions only as directed on your original record with your doctor.",
              actionGuidance = SafetyActionGuidance.FOLLOW_SOURCE_CARE_INSTRUCTIONS,
              provenance = inst.provenance
            )
          )
        }
      }
    }

    // Diagnoses: only narrow explicit non-conditional urgent headers or explicit illegible markers
    clinical.primaryDiagnoses.forEachIndexed { idx, diag ->
      val text = diag.text.trim()
      if (text.isNotEmpty()) {
        if (containsQualifiedUrgentHeader(text)) {
          acc.alerts.add(
            SafetyFindingAlert(
              id = "clin_diag_urgent_$idx",
              itemKey = "clinical_diag:$idx",
              category = MedicalDocumentCategory.CLINICAL,
              bucket = SafetyBucket.AI_EXTRACTED_URGENT_WORDING,
              evidenceOrigin = EvidenceOrigin.AI_EXTRACTED_NARRATIVE_TEXT,
              title = SafetyConstants.URGENT_WORDING_TITLE,
              sourceLabel = "Primary Diagnosis #${idx + 1}",
              reportedValueSummary = text,
              explanationText = SafetyConstants.URGENT_WORDING_BODY,
              actionGuidance = SafetyActionGuidance.URGENT_PROVIDER_REVIEW,
              provenance = diag.provenance
            )
          )
        } else if (UNREADABLE_TEXT_MARKER_REGEX.containsMatchIn(text)) {
          acc.uncertainItemsCount += 1
          acc.alerts.add(
            SafetyFindingAlert(
              id = "clin_diag_unreadable_$idx",
              itemKey = "clinical_diag:$idx",
              category = MedicalDocumentCategory.CLINICAL,
              bucket = SafetyBucket.UNCERTAIN_OR_UNREADABLE,
              evidenceOrigin = EvidenceOrigin.MISSING_OR_CONFLICTING_EXTRACTED_DATA,
              title = "Uncertain or Illegible Clinical Diagnosis",
              sourceLabel = "Primary Diagnosis #${idx + 1}",
              reportedValueSummary = text,
              explanationText =
                "This diagnosis entry appears illegible or uncertain. Verify it directly on the original prescription or clinical note.",
              actionGuidance = SafetyActionGuidance.VERIFY_WITH_ORIGINAL_REPORT,
              provenance = diag.provenance
            )
          )
        }
      }
    }

    // Check medications and allergies for explicit illegible markers only — NEVER flag routine meds as abnormal
    clinical.prescribedMedications.forEachIndexed { idx, med ->
      val fields = listOfNotNull(med.drugName, med.dosage, med.frequency, med.duration, med.instructions)
      if (fields.any { UNREADABLE_TEXT_MARKER_REGEX.containsMatchIn(it) }) {
        acc.uncertainItemsCount += 1
        acc.alerts.add(
          SafetyFindingAlert(
            id = "clin_med_unreadable_$idx",
            itemKey = "clinical_med:$idx",
            category = MedicalDocumentCategory.CLINICAL,
            bucket = SafetyBucket.UNCERTAIN_OR_UNREADABLE,
            evidenceOrigin = EvidenceOrigin.MISSING_OR_CONFLICTING_EXTRACTED_DATA,
            title = "Uncertain or Illegible Medication Entry",
            sourceLabel = med.drugName ?: "Medication #${idx + 1}",
            reportedValueSummary = fields.joinToString(" • ").takeIf { it.isNotBlank() },
            explanationText =
              "Part of this medication entry was marked illegible or uncertain. Do not guess dosing; verify the original prescription with your doctor or pharmacist.",
            actionGuidance = SafetyActionGuidance.VERIFY_WITH_ORIGINAL_REPORT,
            provenance = med.provenance
          )
        )
      }
    }

    clinical.allergiesRecorded.forEachIndexed { idx, allergy ->
      if (UNREADABLE_TEXT_MARKER_REGEX.containsMatchIn(allergy.text)) {
        acc.uncertainItemsCount += 1
        acc.alerts.add(
          SafetyFindingAlert(
            id = "clin_allergy_unreadable_$idx",
            itemKey = "clinical_allergy:$idx",
            category = MedicalDocumentCategory.CLINICAL,
            bucket = SafetyBucket.UNCERTAIN_OR_UNREADABLE,
            evidenceOrigin = EvidenceOrigin.MISSING_OR_CONFLICTING_EXTRACTED_DATA,
            title = "Uncertain or Illegible Allergy Entry",
            sourceLabel = "Allergy #${idx + 1}",
            reportedValueSummary = allergy.text,
            explanationText =
              "This recorded allergy entry was marked illegible or uncertain. Verify it with your doctor.",
            actionGuidance = SafetyActionGuidance.VERIFY_WITH_ORIGINAL_REPORT,
            provenance = allergy.provenance
          )
        )
      }
    }

    // Clinical documents lack standardized FindingStatus fields -> never emit NO_ABNORMALITY_IDENTIFIED_IN_EXTRACTED_FINDINGS
    acc.requiresIncompleteAssessment = true
    acc.incompleteReason =
      "Clinical documents and prescriptions do not provide standardized normality or abnormality statuses across entries. Review diagnoses, medications, allergies, and care instructions with your doctor."
  }

  // ============================================================================
  // 6. UNIDENTIFIED DOCUMENTS
  // ============================================================================

  private fun evaluateUnidentified(
    unidentified: UniversalMedicalReport.Unidentified,
    acc: EvalAccumulator
  ) {
    acc.uncertainItemsCount += 1
    acc.alerts.add(
      SafetyFindingAlert(
        id = "unidentified_doc",
        category = MedicalDocumentCategory.UNCERTAIN,
        bucket = SafetyBucket.UNCERTAIN_OR_UNREADABLE,
        evidenceOrigin = EvidenceOrigin.MISSING_OR_CONFLICTING_EXTRACTED_DATA,
        title = "Unidentified Document — Safety Assessment Unavailable",
        sourceLabel = "Document Classification",
        reportedValueSummary = unidentified.rawSnippet,
        explanationText =
          "Could not classify this file as a supported medical report (${unidentified.reason}). Safety assessment cannot be performed.",
        actionGuidance = SafetyActionGuidance.VERIFY_WITH_ORIGINAL_REPORT,
        provenance = FieldProvenance.unavailable()
      )
    )
  }

  // ============================================================================
  // FINAL SUMMARY BUILDER (STRICT PRECEDENCE)
  // ============================================================================

  private fun buildFinalSummary(
    extraction: DocumentExtractionResult,
    acc: EvalAccumulator,
    registry: SafetyRuleRegistry
  ): ReportSafetySummary {
    val activeRuleCount = registry.activeRulesForCategory(extraction.category).size

    val bucketOrder = listOf(
      SafetyBucket.EXTRACTED_CRITICAL_FLAG,
      SafetyBucket.AI_EXTRACTED_URGENT_WORDING,
      SafetyBucket.RULE_VALIDATED_ALERT,
      SafetyBucket.EXTRACTED_ABNORMAL_FINDING,
      SafetyBucket.OUTSIDE_EXTRACTED_REFERENCE_RANGE,
      SafetyBucket.EXTRACTED_POSITIVE_FINDING,
      SafetyBucket.UNCERTAIN_OR_UNREADABLE
    )

    val sortedAlerts = acc.alerts.sortedBy { alert ->
      bucketOrder.indexOf(alert.bucket).let { if (it == -1) Int.MAX_VALUE else it }
    }

    val hasCriticalFlag = sortedAlerts.any { it.bucket == SafetyBucket.EXTRACTED_CRITICAL_FLAG }
    val hasUrgentWording = sortedAlerts.any { it.bucket == SafetyBucket.AI_EXTRACTED_URGENT_WORDING }
    val hasRuleAlert = sortedAlerts.any { it.bucket == SafetyBucket.RULE_VALIDATED_ALERT }
    val hasAbnormal = sortedAlerts.any {
      it.bucket == SafetyBucket.EXTRACTED_ABNORMAL_FINDING ||
        it.bucket == SafetyBucket.OUTSIDE_EXTRACTED_REFERENCE_RANGE
    }
    val hasPositive = sortedAlerts.any { it.bucket == SafetyBucket.EXTRACTED_POSITIVE_FINDING }
    val hasUncertain = acc.uncertainItemsCount > 0 ||
      sortedAlerts.any { it.bucket == SafetyBucket.UNCERTAIN_OR_UNREADABLE }

    val overallBucket = when {
      hasCriticalFlag -> SafetyBucket.EXTRACTED_CRITICAL_FLAG
      hasUrgentWording -> SafetyBucket.AI_EXTRACTED_URGENT_WORDING
      hasRuleAlert -> SafetyBucket.RULE_VALIDATED_ALERT
      sortedAlerts.any { it.bucket == SafetyBucket.EXTRACTED_ABNORMAL_FINDING } ->
        SafetyBucket.EXTRACTED_ABNORMAL_FINDING
      sortedAlerts.any { it.bucket == SafetyBucket.OUTSIDE_EXTRACTED_REFERENCE_RANGE } ->
        SafetyBucket.OUTSIDE_EXTRACTED_REFERENCE_RANGE
      hasPositive -> SafetyBucket.EXTRACTED_POSITIVE_FINDING
      hasUncertain -> SafetyBucket.UNCERTAIN_OR_UNREADABLE
      acc.requiresIncompleteAssessment ||
        acc.unavailableComparisonsCount > 0 ||
        acc.conflictsCount > 0 ||
        acc.evaluatedItemsCount == 0 -> SafetyBucket.INCOMPLETE_ASSESSMENT
      else -> SafetyBucket.NO_ABNORMALITY_IDENTIFIED_IN_EXTRACTED_FINDINGS
    }

    val (headline, message, guidance) = when (overallBucket) {
      SafetyBucket.EXTRACTED_CRITICAL_FLAG -> Triple(
        "Critical Lab Flag Extracted — Verify Original Report",
        "One or more AI-extracted laboratory flags indicate a critical or panic marking. Verify the original report and urgently contact the reporting doctor, clinic, or laboratory. If you are having severe or worsening symptoms, seek immediate emergency medical care without waiting." +
          uncertaintySuffix(acc),
        SafetyActionGuidance.URGENT_PROVIDER_REVIEW
      )

      SafetyBucket.AI_EXTRACTED_URGENT_WORDING -> Triple(
        SafetyConstants.URGENT_WORDING_TITLE,
        SafetyConstants.URGENT_WORDING_BODY + uncertaintySuffix(acc),
        SafetyActionGuidance.URGENT_PROVIDER_REVIEW
      )

      SafetyBucket.RULE_VALIDATED_ALERT -> Triple(
        "Clinically Validated Safety Rule Triggered",
        "A clinically validated deterministic rule matched one or more extracted entries. Verify on your original report and discuss promptly with your doctor." +
          uncertaintySuffix(acc),
        SafetyActionGuidance.PROMPT_DOCTOR_REVIEW
      )

      SafetyBucket.EXTRACTED_ABNORMAL_FINDING -> Triple(
        "Abnormal Finding in Extracted Report",
        "One or more findings were marked abnormal by an AI-extracted flag or extracted finding status. Review your original report and discuss with your doctor." +
          uncertaintySuffix(acc),
        SafetyActionGuidance.PROMPT_DOCTOR_REVIEW
      )

      SafetyBucket.OUTSIDE_EXTRACTED_REFERENCE_RANGE -> Triple(
        "Value Outside Extracted Reference Range",
        "One or more numeric values fall outside the extracted reference range shown on the same row. Verify the printed numbers and units on your original report with your doctor." +
          uncertaintySuffix(acc),
        SafetyActionGuidance.PROMPT_DOCTOR_REVIEW
      )

      SafetyBucket.EXTRACTED_POSITIVE_FINDING -> Triple(
        "Positive Finding in Extracted Report",
        "One or more extracted findings are classified as positive. A positive result requires clinical context — review your original report with your doctor." +
          uncertaintySuffix(acc),
        SafetyActionGuidance.PROMPT_DOCTOR_REVIEW
      )

      SafetyBucket.UNCERTAIN_OR_UNREADABLE -> Triple(
        "Uncertain, Missing, or Unverified Findings",
        "Some extracted findings are missing values/units, unreadable, legend-dependent, or could not be safely verified. Do not assume these findings are normal — review the original report with your doctor." +
          uncertaintySuffix(acc, includeUncertainCount = false),
        SafetyActionGuidance.VERIFY_WITH_ORIGINAL_REPORT
      )

      SafetyBucket.INCOMPLETE_ASSESSMENT -> Triple(
        "Safety Assessment Incomplete",
        buildIncompleteMessage(acc),
        SafetyActionGuidance.VERIFY_WITH_ORIGINAL_REPORT
      )

      SafetyBucket.NO_ABNORMALITY_IDENTIFIED_IN_EXTRACTED_FINDINGS -> Triple(
        SafetyConstants.NO_ABNORMALITY_HEADLINE,
        SafetyConstants.NO_ABNORMALITY_MESSAGE,
        SafetyActionGuidance.ROUTINE_DOCTOR_DISCUSSION
      )

      SafetyBucket.EXTRACTED_CARE_INSTRUCTION -> Triple(
        "Safety Assessment Incomplete",
        buildIncompleteMessage(acc),
        SafetyActionGuidance.FOLLOW_SOURCE_CARE_INSTRUCTIONS
      )
    }

    return ReportSafetySummary(
      category = extraction.category,
      overallClassification = overallBucket,
      overallHeadline = headline,
      overallSummaryMessage = message,
      actionGuidance = guidance,
      alerts = sortedAlerts,
      careInstructions = acc.careInstructions.toList(),
      unavailableComparisons = acc.unavailableComparisons.toList(),
      evaluatedItemsCount = acc.evaluatedItemsCount,
      uncertainItemsCount = acc.uncertainItemsCount,
      conflictsCount = acc.conflictsCount,
      unavailableComparisonsCount = acc.unavailableComparisonsCount,
      hasCriticalOrUrgentFindings = hasCriticalFlag || hasUrgentWording,
      hasAbnormalOrPositiveFindings = hasAbnormal || hasPositive || hasRuleAlert,
      hasUncertainOrUnreadableFindings = hasUncertain,
      rulesetVersion = registry.rulesetVersion,
      activeValidatedRuleCount = activeRuleCount,
      scopeLimitationNotice = SafetyConstants.SCOPE_LIMITATION_NOTICE
    )
  }

  private fun uncertaintySuffix(
    acc: EvalAccumulator,
    includeUncertainCount: Boolean = true
  ): String {
    val parts = mutableListOf<String>()
    if (includeUncertainCount && acc.uncertainItemsCount > 0) {
      parts.add("${acc.uncertainItemsCount} uncertain/unreadable item(s)")
    }
    if (acc.unavailableComparisonsCount > 0) {
      parts.add("${acc.unavailableComparisonsCount} unavailable range comparison(s)")
    }
    if (acc.conflictsCount > 0) {
      parts.add("${acc.conflictsCount} conflicting entry/entries")
    }
    val countsSuffix = if (parts.isEmpty()) "" else " Also note: ${parts.joinToString(", ")}."
    val incompleteNote = acc.incompleteReason
      ?.takeIf { acc.requiresIncompleteAssessment && it.isNotBlank() }
      ?.let { " Incomplete assessment note: $it" }
      ?: if (acc.requiresIncompleteAssessment) {
        " Incomplete assessment note: Some expected report sections were missing or not standardized."
      } else {
        ""
      }
    return countsSuffix + incompleteNote
  }

  private fun buildIncompleteMessage(acc: EvalAccumulator): String {
    if (acc.unavailableComparisonsCount > 0) {
      return "No abnormality was identified in the other evaluated fields, but safety verification is incomplete because " +
        "${acc.unavailableComparisonsCount} printed reference range comparison(s) could not be performed automatically. " +
        "Review all entries on your original report with your doctor."
    }
    return acc.incompleteReason
      ?: "This report does not contain sufficient evaluated status information to complete an automated safety check. Review the original report with your doctor."
  }

  // ============================================================================
  // HELPER FUNCTIONS: LAB FLAGS, NUMERIC GRAMMAR, AND CONTEXT GUARDS
  // ============================================================================

  private fun classifyLabFlag(rawFlag: String?): LabFlagClass {
    val trimmed = rawFlag?.trim()?.takeIf { it.isNotEmpty() } ?: return LabFlagClass.NONE
    val normalized = trimmed
      .uppercase(Locale.US)
      .replace(Regex("\\s+"), " ")
      .trim()

    if (NEGATED_FLAG_REGEX.containsMatchIn(normalized)) {
      return LabFlagClass.NEGATED_NO_ALERT
    }
    if (normalized in CRITICAL_LAB_FLAGS) {
      return LabFlagClass.CRITICAL
    }
    if (normalized in ORDINARY_ABNORMAL_LAB_FLAGS) {
      return LabFlagClass.ABNORMAL
    }
    if (normalized == "STAT") {
      return LabFlagClass.STAT_PRIORITY
    }
    if (normalized in LEGEND_DEPENDENT_AMBIGUOUS_FLAGS) {
      return LabFlagClass.LEGEND_DEPENDENT_OR_UNKNOWN
    }
    return LabFlagClass.LEGEND_DEPENDENT_OR_UNKNOWN
  }

  private fun legendCautionText(flagClass: LabFlagClass, rawFlag: String?): String? = when (flagClass) {
    LabFlagClass.STAT_PRIORITY ->
      "Extracted flag 'STAT' usually indicates order priority rather than result severity."
    LabFlagClass.LEGEND_DEPENDENT_OR_UNKNOWN ->
      "Extracted flag '$rawFlag' is legend-dependent; verify its meaning on your laboratory report."
    else -> null
  }

  internal fun comparePrintedRange(item: ExtractedLabItem): RangeComparisonResult {
    val rawRange = item.referenceRange?.trim()?.takeIf { it.isNotEmpty() }
      ?: return RangeComparisonResult(status = RangeComparisonStatus.NOT_APPLICABLE)

    val rawVal = item.originalValue?.trim()?.takeIf { it.isNotEmpty() }
      ?: return RangeComparisonResult(
        status = RangeComparisonStatus.UNAVAILABLE,
        unavailableReason = "Result value is missing; numeric range comparison is unavailable."
      )

    val parsedVal = parseStrictNumber(rawVal)
      ?: return RangeComparisonResult(
        status = RangeComparisonStatus.UNAVAILABLE,
        unavailableReason = "Result value '$rawVal' is not strictly numeric; numeric range comparison is unavailable."
      )

    val rawUnit = item.originalUnit?.trim()?.takeIf { it.isNotEmpty() }
      ?: return RangeComparisonResult(
        status = RangeComparisonStatus.UNAVAILABLE,
        unavailableReason = "Unit compatibility cannot be established because the result unit is missing."
      )

    val normalizedItemUnit = normalizeUnit(rawUnit)
      ?: return RangeComparisonResult(
        status = RangeComparisonStatus.UNAVAILABLE,
        unavailableReason = "Result unit could not be normalized for range comparison."
      )

    // Reject multi-tier separators and demographic/timing keywords before parsing
    if (rawRange.contains(';') || rawRange.contains('|') || rawRange.contains('(') || rawRange.contains(')') ||
      rawRange.contains('±') || UNSUPPORTED_RANGE_KEYWORD_REGEX.containsMatchIn(rawRange)
    ) {
      return RangeComparisonResult(
        status = RangeComparisonStatus.UNAVAILABLE,
        normalizedUnit = normalizedItemUnit,
        unavailableReason = "Extracted reference range '$rawRange' is multi-tier, demographic-specific, or unsupported for automated comparison."
      )
    }

    val closedMatch = CLOSED_INTERVAL_REGEX.matchEntire(rawRange)
    if (closedMatch != null) {
      val min = parseStrictNumber(closedMatch.groupValues[1])
      val max = parseStrictNumber(closedMatch.groupValues[2])
      val rangeUnit = closedMatch.groupValues[3].takeIf { it.isNotBlank() }

      val unitCheck = verifyRangeUnit(rawUnit, normalizedItemUnit, rangeUnit)
      if (unitCheck != null) return unitCheck

      if (min == null || max == null || !min.isFinite() || !max.isFinite() || min >= max) {
        return RangeComparisonResult(
          status = RangeComparisonStatus.UNAVAILABLE,
          normalizedUnit = normalizedItemUnit,
          unavailableReason = "Extracted reference range bounds '$rawRange' are invalid or non-increasing."
        )
      }

      val isOutside = parsedVal < min || parsedVal > max
      return RangeComparisonResult(
        status = if (isOutside) RangeComparisonStatus.OUTSIDE_RANGE else RangeComparisonStatus.WITHIN_RANGE,
        normalizedUnit = normalizedItemUnit,
        detailNote = "$rawVal $rawUnit vs $rawRange"
      )
    }

    val upperMatch = UPPER_BOUND_REGEX.matchEntire(rawRange)
    if (upperMatch != null) {
      val op = upperMatch.groupValues[1].trim().lowercase(Locale.US)
      val max = parseStrictNumber(upperMatch.groupValues[2])
      val rangeUnit = upperMatch.groupValues[3].takeIf { it.isNotBlank() }

      val unitCheck = verifyRangeUnit(rawUnit, normalizedItemUnit, rangeUnit)
      if (unitCheck != null) return unitCheck

      if (max == null || !max.isFinite()) {
        return RangeComparisonResult(
          status = RangeComparisonStatus.UNAVAILABLE,
          normalizedUnit = normalizedItemUnit,
          unavailableReason = "Extracted upper-bound range '$rawRange' is malformed."
        )
      }

      val isInclusive = op == "<=" || op == "≤" || op.startsWith("up")
      val isOutside = if (isInclusive) parsedVal > max else parsedVal >= max
      return RangeComparisonResult(
        status = if (isOutside) RangeComparisonStatus.OUTSIDE_RANGE else RangeComparisonStatus.WITHIN_RANGE,
        normalizedUnit = normalizedItemUnit,
        detailNote = "$rawVal $rawUnit vs $rawRange"
      )
    }

    val lowerMatch = LOWER_BOUND_REGEX.matchEntire(rawRange)
    if (lowerMatch != null) {
      val op = lowerMatch.groupValues[1].trim().lowercase(Locale.US)
      val min = parseStrictNumber(lowerMatch.groupValues[2])
      val rangeUnit = lowerMatch.groupValues[3].takeIf { it.isNotBlank() }

      val unitCheck = verifyRangeUnit(rawUnit, normalizedItemUnit, rangeUnit)
      if (unitCheck != null) return unitCheck

      if (min == null || !min.isFinite()) {
        return RangeComparisonResult(
          status = RangeComparisonStatus.UNAVAILABLE,
          normalizedUnit = normalizedItemUnit,
          unavailableReason = "Extracted lower-bound range '$rawRange' is malformed."
        )
      }

      val isInclusive = op == ">=" || op == "≥"
      val isOutside = if (isInclusive) parsedVal < min else parsedVal <= min
      return RangeComparisonResult(
        status = if (isOutside) RangeComparisonStatus.OUTSIDE_RANGE else RangeComparisonStatus.WITHIN_RANGE,
        normalizedUnit = normalizedItemUnit,
        detailNote = "$rawVal $rawUnit vs $rawRange"
      )
    }

    return RangeComparisonResult(
      status = RangeComparisonStatus.UNAVAILABLE,
      normalizedUnit = normalizedItemUnit,
      unavailableReason = "Extracted reference range '$rawRange' does not match supported single-interval grammar."
    )
  }

  private fun verifyRangeUnit(
    rawItemUnit: String,
    normalizedItemUnit: String,
    rawRangeUnit: String?
  ): RangeComparisonResult? {
    if (rawRangeUnit == null) return null // Range omits inline unit; same-row non-blank originalUnit is present
    val normalizedRangeUnit = normalizeUnit(rawRangeUnit)
      ?: return RangeComparisonResult(
        status = RangeComparisonStatus.UNAVAILABLE,
        normalizedUnit = normalizedItemUnit,
        unavailableReason = "Reference range unit '$rawRangeUnit' could not be verified."
      )
    if (normalizedItemUnit != normalizedRangeUnit) {
      return RangeComparisonResult(
        status = RangeComparisonStatus.UNAVAILABLE,
        normalizedUnit = normalizedItemUnit,
        unavailableReason =
          "Unit mismatch between result unit '$rawItemUnit' and reference range unit '$rawRangeUnit'; implicit unit conversion is not performed."
      )
    }
    return null
  }

  private fun parseStrictNumber(raw: String): Double? {
    val clean = raw.trim()
    if (!STRICT_VALUE_REGEX.matches(clean)) return null
    val parsed = clean.replace(",", "").toDoubleOrNull() ?: return null
    return parsed.takeIf { it.isFinite() && abs(it) < 1e15 }
  }

  private fun normalizeUnit(raw: String?): String? =
    raw?.trim()?.replace(Regex("\\s+"), "")?.lowercase(Locale.US)?.takeIf { it.isNotEmpty() }

  private fun isUnitVerificationFailure(reason: String?): Boolean {
    if (reason == null) return false
    val lower = reason.lowercase(Locale.US)
    return lower.contains("unit mismatch") ||
      lower.contains("result unit is missing") ||
      lower.contains("unit could not be")
  }

  internal fun containsQualifiedUrgentHeader(text: String): Boolean =
    containsQualifiedPattern(text, EXPLICIT_URGENT_HEADER_REGEX)

  private fun containsQualifiedPattern(text: String, pattern: Regex): Boolean {
    if (text.isBlank()) return false
    val clauses = text.split(Regex("[.!?;\\n]+"))
    for (clause in clauses) {
      val cleanClause = clause.trim()
      if (cleanClause.isEmpty()) continue
      val match = pattern.find(cleanClause) ?: continue
      val prefixAndWindow = cleanClause.substring(0, match.range.last + 1)
      if (!NEGATION_CONTEXT_REGEX.containsMatchIn(prefixAndWindow) &&
        !UNCERTAINTY_OR_RULE_OUT_CONTEXT_REGEX.containsMatchIn(cleanClause) &&
        !HISTORICAL_CONTEXT_REGEX.containsMatchIn(prefixAndWindow)
      ) {
        return true
      }
    }
    return false
  }

  private fun formatLabValueSummary(item: ExtractedLabItem): String? {
    val valuePart = when {
      !item.originalValue.isNullOrBlank() && !item.originalUnit.isNullOrBlank() ->
        "${item.originalValue} ${item.originalUnit}"
      !item.originalValue.isNullOrBlank() -> item.originalValue
      else -> null
    }
    val rangePart = item.referenceRange?.takeIf { it.isNotBlank() }?.let { "Ref: $it" }
    val flagPart = item.laboratoryFlag?.takeIf { it.isNotBlank() }?.let { "Flag: $it" }
    return listOfNotNull(valuePart, rangePart, flagPart).joinToString(" • ").takeIf { it.isNotEmpty() }
  }
}
