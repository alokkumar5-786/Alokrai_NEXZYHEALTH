package com.example.model

/**
 * High-level medical document classification categories.
 */
enum class MedicalDocumentCategory(val displayName: String) {
  LABORATORY("Laboratory / Pathology Report"),
  RADIOLOGY("Radiology / Diagnostic Imaging Report"),
  CARDIOLOGY("Cardiology / Electrophysiology Report"),
  SPECIALISED("Specialised / Histopathology / Oncology Report"),
  CLINICAL("Clinical Document / Prescription / Discharge Summary"),
  UNCERTAIN("Unidentified Medical Document")
}

/**
 * Rigorous clinical finding classification separating normal values,
 * qualitative positives/negatives, missing fields, and unreadable text.
 */
enum class FindingStatus {
  NORMAL,                 // Value falls within laboratory reference interval
  ABNORMAL,               // Value falls outside laboratory reference interval
  POSITIVE,               // Qualitative positive (e.g., pathogen detected, acute fracture identified)
  NEGATIVE,               // Qualitative negative (e.g., "No acute fracture", "HBsAg non-reactive")
  NOT_MENTIONED,          // Field/finding not evaluated or reported in the document
  UNCERTAIN_OR_UNREADABLE // Value is cropped, smudged, handwriting illegible, or ambiguous
}

/**
 * Explicit indicator describing whether provenance metadata was extracted from the source payload.
 */
enum class ProvenanceAvailability {
  AVAILABLE,           // Page number, verbatim source snippet, and AI-estimated confidence all provided
  PARTIALLY_AVAILABLE, // Some provenance fields provided, others omitted by extraction
  UNAVAILABLE          // Provenance metadata omitted or completely absent from extraction output
}

/**
 * Field-level provenance attached to an extracted medical fact.
 *
 * [estimatedExtractionConfidence] is an AI-estimated extraction confidence indicator (0.0 to 1.0),
 * NOT a clinically calibrated probability.
 *
 * Omitted provenance fields remain null and explicitly marked via [availability] so page numbers,
 * snippets, and confidence scores are never fabricated.
 */
data class FieldProvenance(
  val pageNumber: Int? = null,
  val sourceSnippet: String? = null,
  val estimatedExtractionConfidence: Float? = null,
  val availability: ProvenanceAvailability = ProvenanceAvailability.UNAVAILABLE
) {
  val isAvailable: Boolean
    get() = availability != ProvenanceAvailability.UNAVAILABLE

  companion object {
    fun unavailable(): FieldProvenance = FieldProvenance(
      pageNumber = null,
      sourceSnippet = null,
      estimatedExtractionConfidence = null,
      availability = ProvenanceAvailability.UNAVAILABLE
    )
  }
}

// ============================================================================
// 1. LABORATORY EXTRACTION SCHEMA
// ============================================================================

data class ExtractedLabItem(
  val originalName: String?,              // Test or analyte name as printed (e.g., "Hemoglobin") or null if missing
  val originalValue: String?,             // Value or qualitative result as printed (e.g., "11.2", "Negative") or null if missing
  val originalUnit: String?,              // Unit as printed (e.g., "g/dL", "mmol/L") or null
  val referenceRange: String?,            // Reference interval as printed (e.g., "13.0 - 17.0") or null
  val laboratoryFlag: String?,            // Lab-printed flag (e.g., "H", "L", "*", "CRITICAL") or null
  val findingStatus: FindingStatus,       // ABNORMAL, NORMAL, POSITIVE, NEGATIVE, etc.
  val provenance: FieldProvenance
)

data class ExtractedLaboratoryReport(
  val labFacilityName: String?,
  val collectionDate: String?,
  val reportDate: String?,
  val items: List<ExtractedLabItem>
)

// ============================================================================
// 2. RADIOLOGY EXTRACTION SCHEMA (Written Consultation Reports Only)
// ============================================================================

data class AnatomicalFinding(
  val anatomicalSite: String?,            // e.g., "Lung parenchyma", "Pleural space" or null if missing
  val observationText: String?,           // Verbatim finding observation or null if missing
  val findingStatus: FindingStatus,       // NEGATIVE ("Clear"), POSITIVE ("Effusion detected"), etc.
  val provenance: FieldProvenance
)

data class RadiologyImpression(
  val impressionText: String?,            // e.g., "1. No acute cardiopulmonary abnormality." or null if missing
  val findingStatus: FindingStatus,
  val provenance: FieldProvenance
)

data class ExtractedRadiologyReport(
  val modality: String?,                  // e.g., "X-Ray", "CT", "MRI", "Ultrasound" or null if missing
  val anatomicalRegion: String?,          // e.g., "Chest PA", "Brain Non-Contrast" or null if missing
  val clinicalIndication: String?,
  val techniqueDescription: String?,
  val anatomicalFindings: List<AnatomicalFinding>,
  val formalImpressions: List<RadiologyImpression>
)

// ============================================================================
// 3. CARDIOLOGY EXTRACTION SCHEMA (Written Reports Only)
// ============================================================================

data class ExtractedCardiologyReport(
  val modality: String?,                  // e.g., "12-Lead Electrocardiogram (ECG)" or null if missing
  val heartRateBpm: String?,              // e.g., "72 bpm"
  val rhythmInterpretation: String?,      // e.g., "Normal sinus rhythm"
  val prIntervalMs: String?,              // e.g., "160 ms"
  val qrsDurationMs: String?,             // e.g., "88 ms"
  val qtcIntervalMs: String?,             // e.g., "420 ms"
  val ejectionFractionPercent: String?,   // e.g., "60-65%"
  val wallMotionFindings: String?,
  val formalConclusion: String?,          // Verbatim conclusion or null if missing
  val findingStatus: FindingStatus,
  val provenance: FieldProvenance,
  val fieldProvenance: Map<String, FieldProvenance> = emptyMap()
)

// ============================================================================
// 4. SPECIALISED EXTRACTION SCHEMA (Biopsy, Histopathology, Oncology)
// ============================================================================

data class ExtractedSpecialisedReport(
  val reportSubtype: String?,             // e.g., "Core Needle Biopsy" or null if missing
  val specimenSource: String?,            // e.g., "Right breast lesion" or null if missing
  val clinicalHistory: String?,
  val grossDescription: String?,
  val microscopicDescription: String?,
  val gradingOrStaging: String?,
  val diagnosticConclusion: String?,      // Verbatim pathologist conclusion or null if missing
  val findingStatus: FindingStatus,
  val provenance: FieldProvenance,
  val fieldProvenance: Map<String, FieldProvenance> = emptyMap()
)

// ============================================================================
// 5. CLINICAL DOCUMENT EXTRACTION SCHEMA (Prescriptions, Discharge, Notes)
// ============================================================================

data class ExtractedClinicalFact(
  val text: String,
  val provenance: FieldProvenance = FieldProvenance.unavailable()
)

data class ExtractedMedication(
  val drugName: String?,                  // e.g., "Metformin" or null if missing
  val dosage: String?,                    // e.g., "500 mg"
  val frequency: String?,                 // e.g., "Twice daily with meals"
  val duration: String?,                  // e.g., "30 days"
  val instructions: String?,
  val provenance: FieldProvenance
)

data class ExtractedClinicalDocument(
  val documentSubtype: String?,           // e.g., "Discharge Summary", "Outpatient Prescription" or null if missing
  val facilityOrDoctorName: String?,
  val primaryDiagnoses: List<ExtractedClinicalFact>,
  val surgicalProcedures: List<ExtractedClinicalFact>,
  val prescribedMedications: List<ExtractedMedication>,
  val allergiesRecorded: List<ExtractedClinicalFact>,
  val dischargeInstructions: List<ExtractedClinicalFact>,
  val followUpDate: String?
)

// ============================================================================
// UNIFIED UNIVERSAL EXTRACTION RESULT
// ============================================================================

sealed class UniversalMedicalReport {
  data class Laboratory(val report: ExtractedLaboratoryReport) : UniversalMedicalReport()
  data class Radiology(val report: ExtractedRadiologyReport) : UniversalMedicalReport()
  data class Cardiology(val report: ExtractedCardiologyReport) : UniversalMedicalReport()
  data class Specialised(val report: ExtractedSpecialisedReport) : UniversalMedicalReport()
  data class Clinical(val report: ExtractedClinicalDocument) : UniversalMedicalReport()
  data class Unidentified(val rawSnippet: String?, val reason: String) : UniversalMedicalReport()
}

data class DocumentExtractionResult(
  val category: MedicalDocumentCategory,
  val pageCount: Int,
  val isMultiPageBatch: Boolean,
  val report: UniversalMedicalReport,
  val extractionTimestamp: Long = System.currentTimeMillis()
)
