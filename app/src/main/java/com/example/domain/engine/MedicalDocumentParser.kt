package com.example.domain.engine

import com.example.model.AnatomicalFinding
import com.example.model.DocumentExtractionResult
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
import com.example.model.UniversalMedicalReport
import com.squareup.moshi.JsonAdapter
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import java.util.Locale

/**
 * Pure Kotlin JSON and status parser for Universal Medical Document extraction.
 * Contains zero Android framework dependencies so parsing and status logic can be
 * verified deterministically on the JVM.
 *
 * Preserves source-reported text verbatim without fabricating missing medical values,
 * clinical conclusions, anatomical labels, page numbers, snippets, or confidence scores.
 */
object MedicalDocumentParser {

  private val moshi: Moshi = Moshi.Builder().build()

  @Suppress("UNCHECKED_CAST")
  private val jsonObjectAdapter: JsonAdapter<Map<String, Any?>> = moshi.adapter(
    Types.newParameterizedType(Map::class.java, String::class.java, Any::class.java)
  )

  private val NON_REACTIVE_REGEX = Regex(".*\\bNON[- ]?REACTIVE\\b.*")
  private val NOT_REACTIVE_REGEX = Regex(".*\\bNOT[- ]?REACTIVE\\b.*")
  private val NOT_DETECTED_REGEX = Regex(".*\\bNOT[- ]?DETECTED\\b.*")
  private val NOT_POSITIVE_REGEX = Regex(".*\\bNOT[- ]?POSITIVE\\b.*")
  private val NEGATIVE_REGEX = Regex(".*\\bNEGATIVE\\b.*")
  private val POSITIVE_REGEX = Regex(".*\\bPOSITIVE\\b.*")
  private val REACTIVE_REGEX = Regex(".*\\bREACTIVE\\b.*")
  private val ABNORMAL_REGEX = Regex(".*\\bABNORMAL\\b.*")
  private val NEGATED_NORMAL_REGEX = Regex(".*\\b(NOT|NON)[- ]?NORMAL\\b.*")
  private val NORMAL_REGEX = Regex(".*\\bNORMAL\\b.*")

  /**
   * Maps a source-reported finding status string to [FindingStatus].
   *
   * Rules:
   * - Missing (`null`) maps to [FindingStatus.NOT_MENTIONED].
   * - Blank/empty or unrecognized strings map to [FindingStatus.UNCERTAIN_OR_UNREADABLE], never [FindingStatus.NORMAL].
   * - Explicit negative phrases (`NON-REACTIVE`, `NOT REACTIVE`, `NOT POSITIVE`, `NEGATIVE`, etc.)
   *   are evaluated BEFORE `POSITIVE`/`REACTIVE`.
   * - Explicit `ABNORMAL` is evaluated BEFORE `NORMAL`.
   */
  fun parseFindingStatus(rawStatus: String?): FindingStatus {
    if (rawStatus == null) return FindingStatus.NOT_MENTIONED

    val normalized = rawStatus
      .trim()
      .uppercase(Locale.US)
      .replace('_', ' ')
      .replace(Regex("\\s+"), " ")

    if (normalized.isEmpty()) return FindingStatus.UNCERTAIN_OR_UNREADABLE

    return when {
      normalized == "NOT MENTIONED" ||
        normalized == "NOT-MENTIONED" ||
        normalized == "MISSING" -> FindingStatus.NOT_MENTIONED

      normalized == "UNCERTAIN OR UNREADABLE" ||
        normalized == "UNCERTAIN" ||
        normalized == "UNREADABLE" ||
        normalized.contains("UNREADABLE") ||
        normalized.contains("UNCERTAIN") ||
        normalized.contains("SMUDGED") ||
        normalized.contains("ILLEGIBLE") -> FindingStatus.UNCERTAIN_OR_UNREADABLE

      NON_REACTIVE_REGEX.matches(normalized) ||
        NOT_REACTIVE_REGEX.matches(normalized) ||
        NOT_DETECTED_REGEX.matches(normalized) ||
        NOT_POSITIVE_REGEX.matches(normalized) ||
        NEGATIVE_REGEX.matches(normalized) -> FindingStatus.NEGATIVE

      POSITIVE_REGEX.matches(normalized) ||
        REACTIVE_REGEX.matches(normalized) -> FindingStatus.POSITIVE

      ABNORMAL_REGEX.matches(normalized) -> FindingStatus.ABNORMAL

      !NEGATED_NORMAL_REGEX.matches(normalized) &&
        NORMAL_REGEX.matches(normalized) -> FindingStatus.NORMAL

      else -> FindingStatus.UNCERTAIN_OR_UNREADABLE
    }
  }

  /**
   * Safe entry point that catches malformed/empty JSON or schema mismatches into [Result.failure]
   * instead of throwing uncaught exceptions.
   */
  fun parseExtractionJsonSafe(
    jsonString: String,
    pageCount: Int,
    isMultiPage: Boolean
  ): Result<DocumentExtractionResult> = try {
    Result.success(parseExtractionJson(jsonString, pageCount, isMultiPage))
  } catch (e: Exception) {
    Result.failure(e)
  }

  /**
   * Deserializes JSON output from the extraction model into typed domain schemas.
   */
  fun parseExtractionJson(
    jsonString: String,
    pageCount: Int,
    isMultiPage: Boolean
  ): DocumentExtractionResult {
    val cleanJson = jsonString.trim()
      .removePrefix("```json")
      .removePrefix("```")
      .removeSuffix("```")
      .trim()

    if (cleanJson.isEmpty()) {
      throw IllegalArgumentException("Model returned empty or invalid JSON object.")
    }

    val root = jsonObjectAdapter.fromJson(cleanJson)
      ?: throw IllegalArgumentException("Model returned empty or invalid JSON object.")

    val categoryStr = (root.optNonBlankString("category") ?: "UNCERTAIN").uppercase(Locale.US)
    val category = when {
      categoryStr.contains("LAB") -> MedicalDocumentCategory.LABORATORY
      categoryStr.contains("RADIO") -> MedicalDocumentCategory.RADIOLOGY
      categoryStr.contains("CARDIO") -> MedicalDocumentCategory.CARDIOLOGY
      categoryStr.contains("SPEC") -> MedicalDocumentCategory.SPECIALISED
      categoryStr.contains("CLINIC") || categoryStr.contains("PRESCRIPTION") || categoryStr.contains("DISCHARGE") -> MedicalDocumentCategory.CLINICAL
      else -> MedicalDocumentCategory.UNCERTAIN
    }

    val reportObj = root.optMap("report") ?: root

    val universalReport: UniversalMedicalReport = when (category) {
      MedicalDocumentCategory.LABORATORY -> {
        val labFacility = reportObj.optNonBlankString("labFacilityName")
        val collectionDate = reportObj.optNonBlankString("collectionDate")
        val reportDate = reportObj.optNonBlankString("reportDate")
        val itemsList = reportObj.optObjectList("items")
        val labItems = itemsList.map { itemObj ->
          ExtractedLabItem(
            originalName = itemObj.optNonBlankString("originalName"),
            originalValue = itemObj.optNonBlankString("originalValue"),
            originalUnit = itemObj.optNonBlankString("originalUnit"),
            referenceRange = itemObj.optNonBlankString("referenceRange"),
            laboratoryFlag = itemObj.optNonBlankString("laboratoryFlag"),
            findingStatus = parseFindingStatus(itemObj.optString("findingStatus")),
            provenance = parseProvenance(itemObj.optMap("provenance"))
          )
        }
        UniversalMedicalReport.Laboratory(
          ExtractedLaboratoryReport(labFacility, collectionDate, reportDate, labItems)
        )
      }

      MedicalDocumentCategory.RADIOLOGY -> {
        val modality = reportObj.optNonBlankString("modality")
        val region = reportObj.optNonBlankString("anatomicalRegion")
        val indication = reportObj.optNonBlankString("clinicalIndication")
        val technique = reportObj.optNonBlankString("techniqueDescription")

        val findings = reportObj.optObjectList("anatomicalFindings").map { fObj ->
          AnatomicalFinding(
            anatomicalSite = fObj.optNonBlankString("anatomicalSite"),
            observationText = fObj.optNonBlankString("observationText"),
            findingStatus = parseFindingStatus(fObj.optString("findingStatus")),
            provenance = parseProvenance(fObj.optMap("provenance"))
          )
        }

        val impressions = reportObj.optObjectList("formalImpressions").map { impObj ->
          RadiologyImpression(
            impressionText = impObj.optNonBlankString("impressionText"),
            findingStatus = parseFindingStatus(impObj.optString("findingStatus")),
            provenance = parseProvenance(impObj.optMap("provenance"))
          )
        }

        UniversalMedicalReport.Radiology(
          ExtractedRadiologyReport(modality, region, indication, technique, findings, impressions)
        )
      }

      MedicalDocumentCategory.CARDIOLOGY -> {
        val modality = reportObj.optNonBlankString("modality")
        val hr = reportObj.optNonBlankString("heartRateBpm")
        val rhythm = reportObj.optNonBlankString("rhythmInterpretation")
        val pr = reportObj.optNonBlankString("prIntervalMs")
        val qrs = reportObj.optNonBlankString("qrsDurationMs")
        val qtc = reportObj.optNonBlankString("qtcIntervalMs")
        val ef = reportObj.optNonBlankString("ejectionFractionPercent")
        val wall = reportObj.optNonBlankString("wallMotionFindings")
        val conclusion = reportObj.optNonBlankString("formalConclusion")
        val status = parseFindingStatus(reportObj.optString("findingStatus"))
        val provenance = parseProvenance(reportObj.optMap("provenance"))
        val fieldProvenance = parseFieldProvenanceMap(reportObj.optMap("fieldProvenance"))

        UniversalMedicalReport.Cardiology(
          ExtractedCardiologyReport(
            modality = modality,
            heartRateBpm = hr,
            rhythmInterpretation = rhythm,
            prIntervalMs = pr,
            qrsDurationMs = qrs,
            qtcIntervalMs = qtc,
            ejectionFractionPercent = ef,
            wallMotionFindings = wall,
            formalConclusion = conclusion,
            findingStatus = status,
            provenance = provenance,
            fieldProvenance = fieldProvenance
          )
        )
      }

      MedicalDocumentCategory.SPECIALISED -> {
        val subtype = reportObj.optNonBlankString("reportSubtype")
        val source = reportObj.optNonBlankString("specimenSource")
        val history = reportObj.optNonBlankString("clinicalHistory")
        val gross = reportObj.optNonBlankString("grossDescription")
        val micro = reportObj.optNonBlankString("microscopicDescription")
        val staging = reportObj.optNonBlankString("gradingOrStaging")
        val conclusion = reportObj.optNonBlankString("diagnosticConclusion")
        val status = parseFindingStatus(reportObj.optString("findingStatus"))
        val provenance = parseProvenance(reportObj.optMap("provenance"))
        val fieldProvenance = parseFieldProvenanceMap(reportObj.optMap("fieldProvenance"))

        UniversalMedicalReport.Specialised(
          ExtractedSpecialisedReport(
            reportSubtype = subtype,
            specimenSource = source,
            clinicalHistory = history,
            grossDescription = gross,
            microscopicDescription = micro,
            gradingOrStaging = staging,
            diagnosticConclusion = conclusion,
            findingStatus = status,
            provenance = provenance,
            fieldProvenance = fieldProvenance
          )
        )
      }

      MedicalDocumentCategory.CLINICAL -> {
        val subtype = reportObj.optNonBlankString("documentSubtype")
        val facility = reportObj.optNonBlankString("facilityOrDoctorName")
        val followUp = reportObj.optNonBlankString("followUpDate")

        val diagnoses = reportObj.optClinicalFactList("primaryDiagnoses")
        val procedures = reportObj.optClinicalFactList("surgicalProcedures")
        val allergies = reportObj.optClinicalFactList("allergiesRecorded")
        val instructions = reportObj.optClinicalFactList("dischargeInstructions")

        val medications = reportObj.optObjectList("prescribedMedications").map { mObj ->
          ExtractedMedication(
            drugName = mObj.optNonBlankString("drugName"),
            dosage = mObj.optNonBlankString("dosage"),
            frequency = mObj.optNonBlankString("frequency"),
            duration = mObj.optNonBlankString("duration"),
            instructions = mObj.optNonBlankString("instructions"),
            provenance = parseProvenance(mObj.optMap("provenance"))
          )
        }

        UniversalMedicalReport.Clinical(
          ExtractedClinicalDocument(
            documentSubtype = subtype,
            facilityOrDoctorName = facility,
            primaryDiagnoses = diagnoses,
            surgicalProcedures = procedures,
            prescribedMedications = medications,
            allergiesRecorded = allergies,
            dischargeInstructions = instructions,
            followUpDate = followUp
          )
        )
      }

      MedicalDocumentCategory.UNCERTAIN -> {
        UniversalMedicalReport.Unidentified(
          rawSnippet = reportObj.optNonBlankString("rawSnippet"),
          reason = reportObj.optNonBlankString("reason")
            ?: "Could not classify document into supported medical categories."
        )
      }
    }

    return DocumentExtractionResult(
      category = category,
      pageCount = pageCount,
      isMultiPageBatch = isMultiPage,
      report = universalReport
    )
  }

  /**
   * Parses provenance metadata without fabricating page numbers, snippets, or confidence values.
   */
  fun parseProvenance(provenanceObj: Map<String, Any?>?): FieldProvenance {
    if (provenanceObj == null) {
      return FieldProvenance.unavailable()
    }

    val pageNumber = provenanceObj.optPositiveInt("pageNumber")
    val sourceSnippet = provenanceObj.optNonBlankString("sourceSnippet")
    val confidence = (provenanceObj.optFloat("estimatedConfidence")
      ?: provenanceObj.optFloat("estimatedExtractionConfidence"))
      ?.takeIf { it.isFinite() }

    val availability = when {
      pageNumber == null && sourceSnippet == null && confidence == null ->
        ProvenanceAvailability.UNAVAILABLE

      pageNumber != null && sourceSnippet != null && confidence != null ->
        ProvenanceAvailability.AVAILABLE

      else -> ProvenanceAvailability.PARTIALLY_AVAILABLE
    }

    return FieldProvenance(
      pageNumber = pageNumber,
      sourceSnippet = sourceSnippet,
      estimatedExtractionConfidence = confidence,
      availability = availability
    )
  }

  private fun parseFieldProvenanceMap(mapObj: Map<String, Any?>?): Map<String, FieldProvenance> {
    if (mapObj == null) return emptyMap()
    val result = LinkedHashMap<String, FieldProvenance>()
    for ((fieldKey, rawVal) in mapObj) {
      val provMap = (rawVal as? Map<*, *>)?.toStringKeyMap() ?: continue
      result[fieldKey] = parseProvenance(provMap)
    }
    return result
  }

  private fun Map<*, *>.toStringKeyMap(): Map<String, Any?> {
    val result = LinkedHashMap<String, Any?>()
    for ((k, v) in this) {
      val keyStr = k as? String ?: continue
      result[keyStr] = v
    }
    return result
  }

  @Suppress("UNCHECKED_CAST")
  private fun Map<String, Any?>.optMap(key: String): Map<String, Any?>? {
    return this[key] as? Map<String, Any?>
  }

  @Suppress("UNCHECKED_CAST")
  private fun Map<String, Any?>.optObjectList(key: String): List<Map<String, Any?>> {
    val rawList = this[key] as? List<*> ?: return emptyList()
    return rawList.mapNotNull { it as? Map<String, Any?> }
  }

  private fun Map<String, Any?>.optClinicalFactList(key: String): List<ExtractedClinicalFact> {
    val rawList = this[key] as? List<*> ?: return emptyList()
    return rawList.mapNotNull { entry ->
      when (entry) {
        is Map<*, *> -> {
          val entryMap = entry.toStringKeyMap()
          val text = entryMap.optNonBlankString("text")
            ?: entryMap.optNonBlankString("value")
            ?: entryMap.optNonBlankString("name")
            ?: entryMap.optNonBlankString("description")
            ?: return@mapNotNull null
          ExtractedClinicalFact(
            text = text,
            provenance = parseProvenance(entryMap.optMap("provenance"))
          )
        }

        is String -> {
          val text = entry.takeIf { it.isNotBlank() } ?: return@mapNotNull null
          ExtractedClinicalFact(
            text = text,
            provenance = FieldProvenance.unavailable()
          )
        }

        else -> null
      }
    }
  }

  private fun Map<String, Any?>.optString(key: String): String? {
    val raw = this[key] ?: return null
    return when (raw) {
      is String -> raw
      is Double -> if (raw.isFinite() && raw % 1.0 == 0.0 && raw in Long.MIN_VALUE.toDouble()..Long.MAX_VALUE.toDouble()) {
        raw.toLong().toString()
      } else {
        raw.toString()
      }
      is Float -> if (raw.isFinite() && raw % 1.0f == 0.0f) {
        raw.toLong().toString()
      } else {
        raw.toString()
      }
      else -> raw.toString()
    }
  }

  private fun Map<String, Any?>.optNonBlankString(key: String): String? {
    val raw = optString(key) ?: return null
    return raw.takeIf { it.isNotBlank() }
  }

  private fun Map<String, Any?>.optPositiveInt(key: String): Int? {
    val raw = this[key] ?: return null
    val value = when (raw) {
      is Int -> raw
      is Long -> raw.toInt()
      is Double -> if (raw.isFinite() && raw % 1.0 == 0.0) raw.toInt() else null
      is Number -> raw.toInt()
      else -> raw.toString().trim().toIntOrNull()
    }
    return value?.takeIf { it > 0 }
  }

  private fun Map<String, Any?>.optFloat(key: String): Float? {
    val raw = this[key] ?: return null
    return when (raw) {
      is Number -> raw.toFloat()
      else -> raw.toString().trim().toFloatOrNull()
    }
  }
}
