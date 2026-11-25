package com.example.model

/**
 * Attribution describing how a Task 3E safety finding or notice was derived.
 *
 * Never claims independent verification against original PDF/image bytes when an item
 * originates from Task 3C AI extraction.
 */
enum class EvidenceOrigin(val displayLabel: String) {
  AI_EXTRACTED_LAB_FLAG("AI-extracted lab flag (verify original report)"),
  AI_EXTRACTED_FINDING_STATUS("AI-extracted finding status (verify original report)"),
  AI_EXTRACTED_NARRATIVE_TEXT("AI-extracted narrative text (verify original report)"),
  EXTRACTED_RANGE_NUMERIC_COMPARISON("Deterministic comparison vs extracted reference range"),
  CLINICALLY_VALIDATED_RULE("Clinically validated safety rule"),
  MISSING_OR_CONFLICTING_EXTRACTED_DATA("Missing, ambiguous, or conflicting extracted data")
}

/**
 * Categorized Task 3E safety outcome buckets preserving exact evidence semantics.
 */
enum class SafetyBucket(val displayLabel: String) {
  EXTRACTED_CRITICAL_FLAG("Extracted Critical Lab Flag"),
  AI_EXTRACTED_URGENT_WORDING("Urgent Wording Extracted by AI"),
  RULE_VALIDATED_ALERT("Clinically Validated Rule Alert"),
  EXTRACTED_ABNORMAL_FINDING("Extracted Abnormal Finding"),
  OUTSIDE_EXTRACTED_REFERENCE_RANGE("Outside Extracted Reference Range"),
  EXTRACTED_POSITIVE_FINDING("Extracted Positive Finding"),
  EXTRACTED_CARE_INSTRUCTION("Extracted Care Instructions"),
  UNCERTAIN_OR_UNREADABLE("Uncertain, Missing, or Unreadable"),
  INCOMPLETE_ASSESSMENT("Safety Assessment Incomplete"),
  NO_ABNORMALITY_IDENTIFIED_IN_EXTRACTED_FINDINGS("No Abnormality Identified in Extracted Findings")
}

/**
 * Deterministic numeric range check status for laboratory entries.
 */
enum class RangeComparisonStatus {
  WITHIN_RANGE,
  OUTSIDE_RANGE,
  UNAVAILABLE,
  NOT_APPLICABLE
}

data class RangeComparisonResult(
  val status: RangeComparisonStatus,
  val normalizedUnit: String? = null,
  val unavailableReason: String? = null,
  val detailNote: String? = null
)

enum class SafetyActionGuidance(val summaryLabel: String) {
  URGENT_PROVIDER_REVIEW("Contact reporting doctor or clinic urgently"),
  PROMPT_DOCTOR_REVIEW("Discuss promptly with your doctor"),
  VERIFY_WITH_ORIGINAL_REPORT("Verify against original report with your doctor"),
  FOLLOW_SOURCE_CARE_INSTRUCTIONS("Review printed care instructions with your doctor"),
  ROUTINE_DOCTOR_DISCUSSION("Review original report with your doctor")
}

data class ValidatedRuleReference(
  val ruleId: String,
  val ruleVersion: Int,
  val rulesetVersion: String,
  val clinicalEvidenceRef: String
)

data class SafetyFindingAlert(
  val id: String,
  val itemKey: String? = null,
  val category: MedicalDocumentCategory,
  val bucket: SafetyBucket,
  val evidenceOrigin: EvidenceOrigin,
  val title: String,
  val sourceLabel: String,
  val reportedValueSummary: String? = null,
  val explanationText: String,
  val actionGuidance: SafetyActionGuidance,
  val provenance: FieldProvenance,
  val ruleReference: ValidatedRuleReference? = null,
  val rangeComparisonStatus: RangeComparisonStatus = RangeComparisonStatus.NOT_APPLICABLE,
  val comparisonUnavailableReason: String? = null,
  val conflictReason: String? = null
)

data class UnavailableComparisonNotice(
  val itemKey: String? = null,
  val itemLabel: String,
  val reason: String,
  val provenance: FieldProvenance
)

data class ReportSafetySummary(
  val category: MedicalDocumentCategory,
  val overallClassification: SafetyBucket,
  val overallHeadline: String,
  val overallSummaryMessage: String,
  val actionGuidance: SafetyActionGuidance,
  val alerts: List<SafetyFindingAlert> = emptyList(),
  val careInstructions: List<SafetyFindingAlert> = emptyList(),
  val unavailableComparisons: List<UnavailableComparisonNotice> = emptyList(),
  val evaluatedItemsCount: Int = 0,
  val uncertainItemsCount: Int = 0,
  val conflictsCount: Int = 0,
  val unavailableComparisonsCount: Int = 0,
  val hasCriticalOrUrgentFindings: Boolean = false,
  val hasAbnormalOrPositiveFindings: Boolean = false,
  val hasUncertainOrUnreadableFindings: Boolean = false,
  val rulesetVersion: String,
  val activeValidatedRuleCount: Int = 0,
  val scopeLimitationNotice: String = SafetyConstants.SCOPE_LIMITATION_NOTICE
)

object SafetyConstants {
  const val ENGINE_VERSION = "1.0.0-release1"

  const val URGENT_WORDING_TITLE =
    "Urgent Wording Extracted by AI — Verify Original Report"

  const val URGENT_WORDING_BODY =
    "Potentially urgent or critical wording was extracted by AI from the report text below. " +
      "Automated extraction cannot independently verify the original document. Check your " +
      "original PDF or printed report and contact your doctor or reporting clinic urgently. " +
      "If you are experiencing severe or worsening symptoms, seek immediate emergency medical " +
      "care or contact local emergency services without waiting."

  const val SCOPE_LIMITATION_NOTICE =
    "NexzyHealth checks only AI-extracted report information and applicable clinically " +
      "validated rules. Extracted flags, statuses, and snippets are not independently verified " +
      "against the original PDF or image. This assessment is not a medical diagnosis, does not " +
      "recommend medication changes, and cannot detect every critical medical condition."

  const val NO_ABNORMALITY_HEADLINE =
    "No Abnormality Identified in Extracted Findings"

  const val NO_ABNORMALITY_MESSAGE =
    "No abnormality identified in AI-extracted findings across the evaluated fields. Because " +
      "automated extraction may miss details or lack full clinical context, this does not " +
      "confirm your full report or health is normal — always review the original report with your doctor."
}
