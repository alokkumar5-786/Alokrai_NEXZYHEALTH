package com.example.domain.engine

import com.example.model.DocumentExtractionResult
import com.example.model.FieldProvenance
import com.example.model.MedicalDocumentCategory
import java.util.Locale

/**
 * Governance lifecycle state for deterministic clinical safety rules.
 *
 * Fail-closed policy: only [CLINICALLY_VALIDATED] rules with explicit clinical sign-off
 * metadata and [SafetyRuleDefinition.isClinicallyApprovedForProduction] == true can execute.
 */
enum class RuleValidationStatus {
  /** Reviewed, benchmarked, and explicitly signed off for production execution. */
  CLINICALLY_VALIDATED,

  /** Registered candidate awaiting licensed clinical governance review; blocked in production. */
  PENDING_CLINICAL_VALIDATION,

  /** Explicitly deactivated or retired; blocked from execution. */
  DISABLED
}

/**
 * Target alert classification emitted when a clinically validated rule matches.
 */
enum class SafetyRuleTargetSeverity {
  CRITICAL_ALERT,
  ABNORMAL_ALERT,
  POSITIVE_ALERT
}

/**
 * Deterministic match output produced exclusively by a clinically validated [SafetyRuleDefinition]
 * operating on immutable Task 3C [DocumentExtractionResult] data.
 */
data class SafetyRuleMatch(
  val ruleId: String,
  val ruleVersion: Int,
  val rulesetVersion: String,
  val category: MedicalDocumentCategory,
  val targetSeverity: SafetyRuleTargetSeverity,
  val clinicalEvidenceRef: String,
  val sourceLabel: String,
  val matchedEvidenceSnippet: String,
  val summaryMessage: String,
  val provenance: FieldProvenance,
  val matchedUnit: String? = null
)

/**
 * Pure Kotlin evaluation contract for a validated clinical safety rule.
 * Receives ONLY Task 3C [DocumentExtractionResult] — never AI-generated Task 3D explanations.
 */
fun interface SafetyRulePredicate {
  fun evaluate(
    extraction: DocumentExtractionResult,
    rule: SafetyRuleDefinition,
    rulesetVersion: String
  ): List<SafetyRuleMatch>
}

/**
 * Versioned, auditable definition of a clinical safety rule.
 *
 * Defaults fail closed ([RuleValidationStatus.PENDING_CLINICAL_VALIDATION],
 * [isClinicallyApprovedForProduction] = false, [clinicalReviewerSignOff] = null) so an
 * unreviewed rule can never accidentally become executable through default arguments.
 */
data class SafetyRuleDefinition(
  val ruleId: String,
  val ruleVersion: Int,
  val title: String,
  val category: MedicalDocumentCategory,
  val targetSeverity: SafetyRuleTargetSeverity,
  val validationStatus: RuleValidationStatus = RuleValidationStatus.PENDING_CLINICAL_VALIDATION,
  val isClinicallyApprovedForProduction: Boolean = false,
  val clinicalEvidenceRef: String,
  val clinicalReviewerSignOff: String? = null,
  val signOffDateIso: String? = null,
  val requiresExactUnitMatch: Boolean = false,
  val applicableUnits: Set<String> = emptySet(),
  val predicate: SafetyRulePredicate? = null
) {
  /**
   * Strict production-grade execution gate.
   * All governance, unit-safety, and predicate requirements must hold simultaneously.
   */
  fun isExecutable(): Boolean {
    if (ruleId.isBlank() || ruleVersion <= 0 || clinicalEvidenceRef.isBlank()) return false
    if (validationStatus != RuleValidationStatus.CLINICALLY_VALIDATED) return false
    if (!isClinicallyApprovedForProduction) return false
    if (clinicalReviewerSignOff.isNullOrBlank() || signOffDateIso.isNullOrBlank()) return false
    if (requiresExactUnitMatch && applicableUnits.none { it.isNotBlank() }) return false
    return predicate != null
  }

  internal fun isUnitCompatible(unit: String?): Boolean {
    if (!requiresExactUnitMatch) return true
    val normalizedCandidate = normalizeUnitToken(unit) ?: return false
    val normalizedAllowed = applicableUnits.mapNotNull { normalizeUnitToken(it) }
    return normalizedCandidate in normalizedAllowed
  }

  companion object {
    internal fun normalizeUnitToken(unit: String?): String? =
      unit?.trim()?.replace(Regex("\\s+"), "")?.lowercase(Locale.US)?.takeIf { it.isNotEmpty() }
  }
}

/**
 * Versioned, testable clinical safety rule registry for Task 3E.
 *
 * Release 1 (`productionDefault()`) activates ZERO unvalidated clinical thresholds or broad
 * clinical phrase rules; all candidate rules remain [RuleValidationStatus.PENDING_CLINICAL_VALIDATION]
 * until licensed clinical sign-off is provided.
 */
class SafetyRuleRegistry private constructor(
  val rulesetVersion: String,
  val lastReviewedBy: String,
  val isProductionDefault: Boolean,
  private val registeredRules: List<SafetyRuleDefinition>
) {

  init {
    val distinctIds = registeredRules.map { it.ruleId }.toSet()
    require(distinctIds.size == registeredRules.size) {
      "SafetyRuleRegistry contains duplicate ruleId entries."
    }
  }

  /**
   * Returns all registered rules (including pending and disabled candidates) for governance audit.
   */
  fun allRegisteredRules(): List<SafetyRuleDefinition> = registeredRules.toList()

  /**
   * Returns ONLY rules that pass the strict [SafetyRuleDefinition.isExecutable] governance gate.
   * In Release 1 [productionDefault], this list is guaranteed empty.
   */
  fun activeRules(): List<SafetyRuleDefinition> =
    registeredRules.filter { it.isExecutable() }

  /**
   * Returns executable rules scoped to a single [MedicalDocumentCategory].
   */
  fun activeRulesForCategory(category: MedicalDocumentCategory): List<SafetyRuleDefinition> =
    activeRules().filter { it.category == category }

  /**
   * Executes ONLY clinically validated rules for [extraction.category].
   * Any rule failing [SafetyRuleDefinition.isExecutable] or failing exact unit compatibility
   * (when [SafetyRuleDefinition.requiresExactUnitMatch] is true) is strictly ignored/filtered.
   */
  fun evaluateExecutableRules(extraction: DocumentExtractionResult): List<SafetyRuleMatch> {
    val executableForCategory = activeRulesForCategory(extraction.category)
    if (executableForCategory.isEmpty()) return emptyList()

    val matches = mutableListOf<SafetyRuleMatch>()
    for (rule in executableForCategory) {
      val predicate = rule.predicate ?: continue
      val rawMatches = predicate.evaluate(extraction, rule, rulesetVersion)
      for (match in rawMatches) {
        if (rule.requiresExactUnitMatch && !rule.isUnitCompatible(match.matchedUnit)) {
          continue
        }
        // Stamp canonical metadata from the validated registry rule so predicates cannot spoof IDs
        matches.add(
          match.copy(
            ruleId = rule.ruleId,
            ruleVersion = rule.ruleVersion,
            rulesetVersion = rulesetVersion,
            category = rule.category,
            targetSeverity = rule.targetSeverity,
            clinicalEvidenceRef = rule.clinicalEvidenceRef
          )
        )
      }
    }
    return matches
  }

  companion object {
    const val RELEASE_1_RULESET_VERSION = "1.0.0-release1"
    const val PENDING_CLINICAL_REVIEW_NOTICE = "Pending Clinical Governance Sign-Off"

    /**
     * Release 1 production registry.
     *
     * Contains zero active clinical thresholds or broad clinical phrase rules; every candidate
     * rule is explicitly registered as [RuleValidationStatus.PENDING_CLINICAL_VALIDATION] and
     * blocked by [SafetyRuleDefinition.isExecutable].
     */
    fun productionDefault(): SafetyRuleRegistry = SafetyRuleRegistry(
      rulesetVersion = RELEASE_1_RULESET_VERSION,
      lastReviewedBy = PENDING_CLINICAL_REVIEW_NOTICE,
      isProductionDefault = true,
      registeredRules = listOf(
        SafetyRuleDefinition(
          ruleId = "LAB_CRIT_POTASSIUM_V1",
          ruleVersion = 1,
          title = "Critical Serum Potassium Threshold",
          category = MedicalDocumentCategory.LABORATORY,
          targetSeverity = SafetyRuleTargetSeverity.CRITICAL_ALERT,
          validationStatus = RuleValidationStatus.PENDING_CLINICAL_VALIDATION,
          isClinicallyApprovedForProduction = false,
          clinicalEvidenceRef = "Pending clinical governance review (no generic thresholds active in Release 1)",
          requiresExactUnitMatch = true,
          applicableUnits = setOf("mmol/L", "mEq/L")
        ),
        SafetyRuleDefinition(
          ruleId = "LAB_CRIT_GLUCOSE_V1",
          ruleVersion = 1,
          title = "Critical Blood Glucose Threshold",
          category = MedicalDocumentCategory.LABORATORY,
          targetSeverity = SafetyRuleTargetSeverity.CRITICAL_ALERT,
          validationStatus = RuleValidationStatus.PENDING_CLINICAL_VALIDATION,
          isClinicallyApprovedForProduction = false,
          clinicalEvidenceRef = "Pending clinical governance review (no generic thresholds active in Release 1)",
          requiresExactUnitMatch = true,
          applicableUnits = setOf("mg/dL", "mmol/L")
        ),
        SafetyRuleDefinition(
          ruleId = "LAB_CRIT_HEMOGLOBIN_V1",
          ruleVersion = 1,
          title = "Critical Hemoglobin Threshold",
          category = MedicalDocumentCategory.LABORATORY,
          targetSeverity = SafetyRuleTargetSeverity.CRITICAL_ALERT,
          validationStatus = RuleValidationStatus.PENDING_CLINICAL_VALIDATION,
          isClinicallyApprovedForProduction = false,
          clinicalEvidenceRef = "Pending clinical governance review (no generic thresholds active in Release 1)",
          requiresExactUnitMatch = true,
          applicableUnits = setOf("g/dL", "g/L")
        ),
        SafetyRuleDefinition(
          ruleId = "RAD_CRIT_PHRASE_PNEUMOTHORAX_V1",
          ruleVersion = 1,
          title = "Radiology Acute Pneumothorax Phrase Rule",
          category = MedicalDocumentCategory.RADIOLOGY,
          targetSeverity = SafetyRuleTargetSeverity.CRITICAL_ALERT,
          validationStatus = RuleValidationStatus.PENDING_CLINICAL_VALIDATION,
          isClinicallyApprovedForProduction = false,
          clinicalEvidenceRef = "Pending clinical governance review (broad phrase rules disabled in Release 1)"
        ),
        SafetyRuleDefinition(
          ruleId = "RAD_CRIT_PHRASE_INTRACRANIAL_HEMORRHAGE_V1",
          ruleVersion = 1,
          title = "Radiology Intracranial Hemorrhage Phrase Rule",
          category = MedicalDocumentCategory.RADIOLOGY,
          targetSeverity = SafetyRuleTargetSeverity.CRITICAL_ALERT,
          validationStatus = RuleValidationStatus.PENDING_CLINICAL_VALIDATION,
          isClinicallyApprovedForProduction = false,
          clinicalEvidenceRef = "Pending clinical governance review (broad phrase rules disabled in Release 1)"
        ),
        SafetyRuleDefinition(
          ruleId = "CARDIO_CRIT_QTC_PROLONGATION_V1",
          ruleVersion = 1,
          title = "Cardiology Marked QTc Prolongation Threshold",
          category = MedicalDocumentCategory.CARDIOLOGY,
          targetSeverity = SafetyRuleTargetSeverity.CRITICAL_ALERT,
          validationStatus = RuleValidationStatus.PENDING_CLINICAL_VALIDATION,
          isClinicallyApprovedForProduction = false,
          clinicalEvidenceRef = "Pending clinical governance review (interval thresholds disabled in Release 1)",
          requiresExactUnitMatch = true,
          applicableUnits = setOf("ms")
        ),
        SafetyRuleDefinition(
          ruleId = "SPEC_CRIT_MALIGNANCY_ALERT_V1",
          ruleVersion = 1,
          title = "Specialised Pathology Unexpected Malignancy Rule",
          category = MedicalDocumentCategory.SPECIALISED,
          targetSeverity = SafetyRuleTargetSeverity.CRITICAL_ALERT,
          validationStatus = RuleValidationStatus.PENDING_CLINICAL_VALIDATION,
          isClinicallyApprovedForProduction = false,
          clinicalEvidenceRef = "Pending clinical governance review (broad pathology phrase rules disabled in Release 1)"
        ),
        SafetyRuleDefinition(
          ruleId = "CLIN_ALLERGY_MED_CONFLICT_V1",
          ruleVersion = 1,
          title = "Clinical Medication vs Recorded Allergen Conflict Rule",
          category = MedicalDocumentCategory.CLINICAL,
          targetSeverity = SafetyRuleTargetSeverity.CRITICAL_ALERT,
          validationStatus = RuleValidationStatus.PENDING_CLINICAL_VALIDATION,
          isClinicallyApprovedForProduction = false,
          clinicalEvidenceRef = "Pending clinical governance review (drug-allergen matching disabled in Release 1)"
        )
      )
    )

    /**
     * Test-only factory allowing unit tests to inject [CLINICALLY_VALIDATED] test rules
     * alongside unvalidated rules to verify the registry execution gate end-to-end.
     */
    fun forTesting(
      rules: List<SafetyRuleDefinition>,
      rulesetVersion: String = "1.0.0-test",
      lastReviewedBy: String = "Unit Test Verification Harness"
    ): SafetyRuleRegistry = SafetyRuleRegistry(
      rulesetVersion = rulesetVersion,
      lastReviewedBy = lastReviewedBy,
      isProductionDefault = false,
      registeredRules = rules
    )
  }
}
