package com.example.domain.engine

import com.example.model.CategoryExplanation
import com.example.model.DocumentExtractionResult
import com.example.model.ExplainedCardiologyMetric
import com.example.model.ExplainedClinicalFact
import com.example.model.ExplainedLabItem
import com.example.model.ExplainedMedication
import com.example.model.ExplainedRadiologyFinding
import com.example.model.ExplainedRadiologyImpression
import com.example.model.ExplainedSpecialisedSection
import com.example.model.ExplanationConstants
import com.example.model.ExtractedMedication
import com.example.model.FindingStatus
import com.example.model.MedicalDocumentCategory
import com.example.model.MedicalTermDefinition
import com.example.model.ReportExplanationResult
import com.example.model.SupportedExplanationLanguage
import com.example.model.UniversalMedicalReport
import com.squareup.moshi.JsonAdapter
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import java.util.Locale

/**
 * Pure Kotlin parser and safety validator for Task 3D AI explanation payloads.
 *
 * Enforces strict grounding to Task 3C [DocumentExtractionResult]:
 * - AI responses are bound strictly by validated item indexes or allow-listed field keys.
 * - Missing, duplicate, unknown, or mismatched indexes/keys safely fall back to the original
 *   Task 3C item with no AI explanation attached.
 * - Original values, units, reference ranges, lab flags, dates, FindingStatus, and provenance
 *   are read directly from Task 3C models and can never be modified by AI output.
 * - Medication explanations are restricted to explicitly printed dosing schedules/instructions,
 *   block unexpanded ambiguous abbreviations, and filter unsafe medication advice across all
 *   AI-generated text fields while preserving legitimate doctor-consultation warnings.
 */
object MedicalExplanationParser {

  private val moshi: Moshi = Moshi.Builder().build()

  @Suppress("UNCHECKED_CAST")
  private val jsonObjectAdapter: JsonAdapter<Map<String, Any?>> = moshi.adapter(
    Types.newParameterizedType(Map::class.java, String::class.java, Any::class.java)
  )

  private val CARDIOLOGY_METRIC_SPECS = listOf(
    FieldSpec("heartRateBpm", "Heart Rate"),
    FieldSpec("rhythmInterpretation", "Rhythm"),
    FieldSpec("prIntervalMs", "PR Interval (ms)"),
    FieldSpec("qrsDurationMs", "QRS Duration (ms)"),
    FieldSpec("qtcIntervalMs", "QTc Interval (ms)"),
    FieldSpec("ejectionFractionPercent", "Ejection Fraction"),
    FieldSpec("wallMotionFindings", "Wall Motion")
  )

  private val SPECIALISED_SECTION_SPECS = listOf(
    FieldSpec("specimenSource", "Specimen Source"),
    FieldSpec("clinicalHistory", "Clinical History"),
    FieldSpec("grossDescription", "Gross Description"),
    FieldSpec("microscopicDescription", "Microscopic Description"),
    FieldSpec("gradingOrStaging", "Grading / Staging")
  )

  private data class FieldSpec(val key: String, val label: String)

  fun parseExplanationJsonSafe(
    jsonString: String,
    sourceExtraction: DocumentExtractionResult,
    language: String = ExplanationConstants.DEFAULT_EXPLANATION_LANGUAGE
  ): Result<ReportExplanationResult> = try {
    Result.success(parseExplanationJson(jsonString, sourceExtraction, language))
  } catch (e: Exception) {
    Result.failure(e)
  }

  fun parseExplanationJson(
    jsonString: String,
    sourceExtraction: DocumentExtractionResult,
    language: String = ExplanationConstants.DEFAULT_EXPLANATION_LANGUAGE
  ): ReportExplanationResult {
    if (sourceExtraction.report is UniversalMedicalReport.Unidentified ||
      sourceExtraction.category == MedicalDocumentCategory.UNCERTAIN
    ) {
      throw IllegalArgumentException("Unidentified or non-medical documents cannot be explained.")
    }

    val cleanJson = jsonString.trim()
      .removePrefix("```json")
      .removePrefix("```")
      .removeSuffix("```")
      .trim()

    if (cleanJson.isEmpty()) {
      throw IllegalArgumentException("Model returned empty explanation JSON.")
    }

    val resolvedLanguageEnum: SupportedExplanationLanguage =
      LocalizedSafetyResources.resolveLanguage(language)
    val resolvedLanguageName = resolvedLanguageEnum.canonicalName
    val safetyStrings = LocalizedSafetyResources.stringsFor(resolvedLanguageEnum)

    val root = jsonObjectAdapter.fromJson(cleanJson)
      ?: throw IllegalArgumentException("Model returned invalid explanation JSON.")

    val medAbbreviationContexts: List<MedicationAbbreviationContext> =
      (sourceExtraction.report as? UniversalMedicalReport.Clinical)
        ?.report
        ?.prescribedMedications
        ?.map { med ->
          MedicationAbbreviationContext(
            med = med,
            unexpandedRules = unexpandedAbbreviationRulesForMedication(med, resolvedLanguageEnum)
          )
        }
        ?: emptyList()

    val uncertaintyNotes = LinkedHashSet<String>()
    root.optStringList("uncertaintyNotes").forEach { note ->
      val safeNote = sanitizeNarrativeOrNull(note, medAbbreviationContexts, resolvedLanguageEnum)
      if (safeNote != null) {
        uncertaintyNotes.add(safeNote)
      }
    }

    val categoryExplanation: CategoryExplanation = when (val report = sourceExtraction.report) {
      is UniversalMedicalReport.Laboratory -> {
        val rawItems = root.optObjectList("items")
        val validatedMap = buildValidatedIndexMap(
          entries = rawItems,
          indexKeys = listOf("itemIndex", "index"),
          sourceSize = report.report.items.size,
          expectedNameForIndex = { idx -> report.report.items[idx].originalName },
          echoedNameKeys = listOf("originalName", "name")
        )
        val explainedItems = report.report.items.mapIndexed { idx, src ->
          val aiEntry = validatedMap[idx]
          when (src.findingStatus) {
            FindingStatus.UNCERTAIN_OR_UNREADABLE -> {
              uncertaintyNotes.add(
                "${src.originalName ?: safetyStrings.itemLabel(idx + 1)}: ${safetyStrings.uncertainStatusNote}"
              )
              ExplainedLabItem(
                sourceItem = src,
                whatItMeasures = null,
                plainLanguageExplanation = null,
                uncertaintyNote = safetyStrings.uncertainStatusNote
              )
            }
            FindingStatus.NOT_MENTIONED -> {
              val hasPrintableValue = !src.originalValue.isNullOrBlank()
              uncertaintyNotes.add(
                "${src.originalName ?: safetyStrings.itemLabel(idx + 1)}: ${safetyStrings.notMentionedStatusNote}"
              )
              ExplainedLabItem(
                sourceItem = src,
                whatItMeasures = sanitizeExplanationOrNull(
                  text = aiEntry?.optNonBlankString("whatItMeasures"),
                  language = resolvedLanguageEnum
                ),
                plainLanguageExplanation = if (hasPrintableValue) {
                  sanitizeExplanationOrNull(
                    text = aiEntry?.optNonBlankString("plainLanguageExplanation"),
                    language = resolvedLanguageEnum
                  )
                } else {
                  null
                },
                uncertaintyNote = safetyStrings.notMentionedStatusNote
              )
            }
            else -> {
              ExplainedLabItem(
                sourceItem = src,
                whatItMeasures = sanitizeExplanationOrNull(
                  text = aiEntry?.optNonBlankString("whatItMeasures"),
                  language = resolvedLanguageEnum
                ),
                plainLanguageExplanation = sanitizeExplanationOrNull(
                  text = aiEntry?.optNonBlankString("plainLanguageExplanation"),
                  language = resolvedLanguageEnum
                ),
                uncertaintyNote = null
              )
            }
          }
        }
        CategoryExplanation.Laboratory(
          labFacilityName = report.report.labFacilityName,
          collectionDate = report.report.collectionDate,
          reportDate = report.report.reportDate,
          items = explainedItems
        )
      }

      is UniversalMedicalReport.Radiology -> {
        val rad = report.report
        val findingMap = buildValidatedIndexMap(
          entries = root.optObjectList("findings"),
          indexKeys = listOf("findingIndex", "itemIndex", "index"),
          sourceSize = rad.anatomicalFindings.size,
          expectedNameForIndex = { idx -> rad.anatomicalFindings[idx].anatomicalSite },
          echoedNameKeys = listOf("anatomicalSite")
        )
        val impressionMap = buildValidatedIndexMap(
          entries = root.optObjectList("impressions"),
          indexKeys = listOf("impressionIndex", "itemIndex", "index"),
          sourceSize = rad.formalImpressions.size,
          expectedNameForIndex = { idx -> rad.formalImpressions[idx].impressionText },
          echoedNameKeys = listOf("impressionText")
        )

        val explainedFindings = rad.anatomicalFindings.mapIndexed { idx, finding ->
          val aiEntry = findingMap[idx]
          when (finding.findingStatus) {
            FindingStatus.UNCERTAIN_OR_UNREADABLE -> {
              uncertaintyNotes.add(
                "${finding.anatomicalSite ?: safetyStrings.findingLabel(idx + 1)}: ${safetyStrings.uncertainStatusNote}"
              )
              ExplainedRadiologyFinding(
                sourceFinding = finding,
                plainLanguageExplanation = null,
                uncertaintyNote = safetyStrings.uncertainStatusNote
              )
            }
            FindingStatus.NOT_MENTIONED -> {
              uncertaintyNotes.add(
                "${finding.anatomicalSite ?: safetyStrings.findingLabel(idx + 1)}: ${safetyStrings.notMentionedStatusNote}"
              )
              ExplainedRadiologyFinding(
                sourceFinding = finding,
                plainLanguageExplanation = if (!finding.observationText.isNullOrBlank()) {
                  sanitizeExplanationOrNull(
                    text = aiEntry?.optNonBlankString("plainLanguageExplanation"),
                    language = resolvedLanguageEnum
                  )
                } else {
                  null
                },
                uncertaintyNote = safetyStrings.notMentionedStatusNote
              )
            }
            else -> ExplainedRadiologyFinding(
              sourceFinding = finding,
              plainLanguageExplanation = sanitizeExplanationOrNull(
                text = aiEntry?.optNonBlankString("plainLanguageExplanation"),
                language = resolvedLanguageEnum
              ),
              uncertaintyNote = null
            )
          }
        }

        val explainedImpressions = rad.formalImpressions.mapIndexed { idx, imp ->
          val aiEntry = impressionMap[idx]
          when (imp.findingStatus) {
            FindingStatus.UNCERTAIN_OR_UNREADABLE -> {
              uncertaintyNotes.add("${safetyStrings.impressionLabel(idx + 1)}: ${safetyStrings.uncertainStatusNote}")
              ExplainedRadiologyImpression(
                sourceImpression = imp,
                plainLanguageExplanation = null,
                uncertaintyNote = safetyStrings.uncertainStatusNote
              )
            }
            FindingStatus.NOT_MENTIONED -> {
              uncertaintyNotes.add("${safetyStrings.impressionLabel(idx + 1)}: ${safetyStrings.notMentionedStatusNote}")
              ExplainedRadiologyImpression(
                sourceImpression = imp,
                plainLanguageExplanation = if (!imp.impressionText.isNullOrBlank()) {
                  sanitizeExplanationOrNull(
                    text = aiEntry?.optNonBlankString("plainLanguageExplanation"),
                    language = resolvedLanguageEnum
                  )
                } else {
                  null
                },
                uncertaintyNote = safetyStrings.notMentionedStatusNote
              )
            }
            else -> ExplainedRadiologyImpression(
              sourceImpression = imp,
              plainLanguageExplanation = sanitizeExplanationOrNull(
                text = aiEntry?.optNonBlankString("plainLanguageExplanation"),
                language = resolvedLanguageEnum
              ),
              uncertaintyNote = null
            )
          }
        }

        val hasStudyContext = listOf(
          rad.modality,
          rad.anatomicalRegion,
          rad.clinicalIndication,
          rad.techniqueDescription
        ).any { !it.isNullOrBlank() }

        CategoryExplanation.Radiology(
          modality = rad.modality,
          anatomicalRegion = rad.anatomicalRegion,
          clinicalIndication = rad.clinicalIndication,
          techniqueDescription = rad.techniqueDescription,
          studyOverviewExplanation = if (hasStudyContext) {
            sanitizeExplanationOrNull(
              text = root.optNonBlankString("studyOverviewExplanation"),
              language = resolvedLanguageEnum
            )
          } else {
            null
          },
          findings = explainedFindings,
          impressions = explainedImpressions
        )
      }

      is UniversalMedicalReport.Cardiology -> {
        val cardio = report.report
        val sourceValuesByKey = mapOf(
          "heartRateBpm" to cardio.heartRateBpm,
          "rhythmInterpretation" to cardio.rhythmInterpretation,
          "prIntervalMs" to cardio.prIntervalMs,
          "qrsDurationMs" to cardio.qrsDurationMs,
          "qtcIntervalMs" to cardio.qtcIntervalMs,
          "ejectionFractionPercent" to cardio.ejectionFractionPercent,
          "wallMotionFindings" to cardio.wallMotionFindings,
          "formalConclusion" to cardio.formalConclusion
        )
        val fieldMap = buildValidatedFieldKeyMap(
          entries = root.optObjectList("metrics"),
          allowedKeys = sourceValuesByKey.keys,
          expectedValueForKey = { key -> sourceValuesByKey[key] },
          echoedValueKeys = listOf("originalValue")
        )

        val isUncertainReport = cardio.findingStatus == FindingStatus.UNCERTAIN_OR_UNREADABLE
        if (isUncertainReport) {
          uncertaintyNotes.add("${safetyStrings.cardiologyReportLabel}: ${safetyStrings.uncertainStatusNote}")
        } else if (cardio.findingStatus == FindingStatus.NOT_MENTIONED) {
          uncertaintyNotes.add("${safetyStrings.cardiologyReportLabel}: ${safetyStrings.notMentionedStatusNote}")
        }

        val explainedMetrics = CARDIOLOGY_METRIC_SPECS.mapNotNull { spec ->
          val sourceVal = sourceValuesByKey[spec.key]?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
          val aiEntry = if (isUncertainReport) null else fieldMap[spec.key]
          ExplainedCardiologyMetric(
            fieldKey = spec.key,
            label = safetyStrings.cardiologyMetricLabel(spec.key, spec.label),
            originalValue = sourceVal,
            plainLanguageExplanation = sanitizeExplanationOrNull(
              text = aiEntry?.optNonBlankString("plainLanguageExplanation"),
              language = resolvedLanguageEnum
            ),
            provenance = cardio.fieldProvenance[spec.key] ?: cardio.provenance
          )
        }

        // Strict field-key pathway only: never substitute unvalidated root conclusionExplanation
        val conclusionText = when {
          isUncertainReport -> null
          cardio.formalConclusion.isNullOrBlank() -> null
          else -> sanitizeExplanationOrNull(
            text = fieldMap["formalConclusion"]?.optNonBlankString("plainLanguageExplanation"),
            language = resolvedLanguageEnum
          )
        }

        val cardioUncertainty = when {
          isUncertainReport -> safetyStrings.uncertainStatusNote
          cardio.formalConclusion.isNullOrBlank() -> safetyStrings.cardiologyConclusionNotReported
          cardio.findingStatus == FindingStatus.NOT_MENTIONED -> safetyStrings.notMentionedStatusNote
          else -> null
        }
        if (cardioUncertainty != null) {
          uncertaintyNotes.add(cardioUncertainty)
        }

        CategoryExplanation.Cardiology(
          sourceReport = cardio,
          modalityExplanation = if (!cardio.modality.isNullOrBlank() && !isUncertainReport) {
            sanitizeExplanationOrNull(
              text = root.optNonBlankString("modalityExplanation"),
              language = resolvedLanguageEnum
            )
          } else {
            null
          },
          metrics = explainedMetrics,
          conclusionExplanation = conclusionText,
          uncertaintyNote = cardioUncertainty
        )
      }

      is UniversalMedicalReport.Specialised -> {
        val spec = report.report
        val sourceValuesByKey = mapOf(
          "specimenSource" to spec.specimenSource,
          "clinicalHistory" to spec.clinicalHistory,
          "grossDescription" to spec.grossDescription,
          "microscopicDescription" to spec.microscopicDescription,
          "gradingOrStaging" to spec.gradingOrStaging,
          "diagnosticConclusion" to spec.diagnosticConclusion
        )
        val fieldMap = buildValidatedFieldKeyMap(
          entries = root.optObjectList("sections"),
          allowedKeys = sourceValuesByKey.keys,
          expectedValueForKey = { key -> sourceValuesByKey[key] },
          echoedValueKeys = listOf("originalText", "originalValue")
        )

        val isUncertainReport = spec.findingStatus == FindingStatus.UNCERTAIN_OR_UNREADABLE
        if (isUncertainReport) {
          uncertaintyNotes.add("${safetyStrings.specialisedReportLabel}: ${safetyStrings.uncertainStatusNote}")
        } else if (spec.findingStatus == FindingStatus.NOT_MENTIONED) {
          uncertaintyNotes.add("${safetyStrings.specialisedReportLabel}: ${safetyStrings.notMentionedStatusNote}")
        }

        val explainedSections = SPECIALISED_SECTION_SPECS.mapNotNull { sectionSpec ->
          val sourceText = sourceValuesByKey[sectionSpec.key]?.takeIf { it.isNotBlank() }
            ?: return@mapNotNull null
          val aiEntry = if (isUncertainReport) null else fieldMap[sectionSpec.key]
          ExplainedSpecialisedSection(
            fieldKey = sectionSpec.key,
            sectionTitle = safetyStrings.specialisedSectionLabel(sectionSpec.key, sectionSpec.label),
            originalText = sourceText,
            plainLanguageExplanation = sanitizeExplanationOrNull(
              text = aiEntry?.optNonBlankString("plainLanguageExplanation"),
              language = resolvedLanguageEnum
            ),
            provenance = spec.fieldProvenance[sectionSpec.key] ?: spec.provenance
          )
        }

        // Strict field-key pathway only: never substitute unvalidated root conclusionExplanation
        val conclusionText = when {
          isUncertainReport -> null
          spec.diagnosticConclusion.isNullOrBlank() -> null
          else -> sanitizeExplanationOrNull(
            text = fieldMap["diagnosticConclusion"]?.optNonBlankString("plainLanguageExplanation"),
            language = resolvedLanguageEnum
          )
        }

        val specUncertainty = when {
          isUncertainReport -> safetyStrings.uncertainStatusNote
          spec.diagnosticConclusion.isNullOrBlank() -> safetyStrings.specialisedConclusionNotReported
          spec.findingStatus == FindingStatus.NOT_MENTIONED -> safetyStrings.notMentionedStatusNote
          else -> null
        }
        if (specUncertainty != null) {
          uncertaintyNotes.add(specUncertainty)
        }

        CategoryExplanation.Specialised(
          sourceReport = spec,
          reportOverviewExplanation = if (!spec.reportSubtype.isNullOrBlank() && !isUncertainReport) {
            sanitizeExplanationOrNull(
              text = root.optNonBlankString("reportOverviewExplanation"),
              language = resolvedLanguageEnum
            )
          } else {
            null
          },
          sections = explainedSections,
          conclusionExplanation = conclusionText,
          uncertaintyNote = specUncertainty
        )
      }

      is UniversalMedicalReport.Clinical -> {
        val clinical = report.report
        val diagnosesMap = buildValidatedIndexMap(
          entries = root.optObjectList("diagnoses"),
          indexKeys = listOf("diagnosisIndex", "itemIndex", "index"),
          sourceSize = clinical.primaryDiagnoses.size,
          expectedNameForIndex = { idx -> clinical.primaryDiagnoses[idx].text },
          echoedNameKeys = listOf("originalText", "text")
        )
        val proceduresMap = buildValidatedIndexMap(
          entries = root.optObjectList("procedures"),
          indexKeys = listOf("procedureIndex", "itemIndex", "index"),
          sourceSize = clinical.surgicalProcedures.size,
          expectedNameForIndex = { idx -> clinical.surgicalProcedures[idx].text },
          echoedNameKeys = listOf("originalText", "text")
        )
        val medicationsMap = buildValidatedIndexMap(
          entries = root.optObjectList("medications"),
          indexKeys = listOf("medicationIndex", "itemIndex", "index"),
          sourceSize = clinical.prescribedMedications.size,
          expectedNameForIndex = { idx -> clinical.prescribedMedications[idx].drugName },
          echoedNameKeys = listOf("drugName", "originalName")
        )
        val allergiesMap = buildValidatedIndexMap(
          entries = root.optObjectList("allergies"),
          indexKeys = listOf("allergyIndex", "itemIndex", "index"),
          sourceSize = clinical.allergiesRecorded.size,
          expectedNameForIndex = { idx -> clinical.allergiesRecorded[idx].text },
          echoedNameKeys = listOf("originalText", "text")
        )
        val instructionsMap = buildValidatedIndexMap(
          entries = root.optObjectList("dischargeInstructions"),
          indexKeys = listOf("instructionIndex", "itemIndex", "index"),
          sourceSize = clinical.dischargeInstructions.size,
          expectedNameForIndex = { idx -> clinical.dischargeInstructions[idx].text },
          echoedNameKeys = listOf("originalText", "text")
        )

        val explainedMeds = clinical.prescribedMedications.mapIndexed { idx, med ->
          val hasPrintedSchedule = listOf(
            med.dosage,
            med.frequency,
            med.duration,
            med.instructions
          ).any { !it.isNullOrBlank() }

          val rawScheduleExplanation = if (hasPrintedSchedule) {
            medicationsMap[idx]?.optNonBlankString("printedScheduleExplanation")
              ?: medicationsMap[idx]?.optNonBlankString("scheduleExplanation")
          } else {
            null
          }

          val perMedUnexpanded = medAbbreviationContexts.getOrNull(idx)?.unexpandedRules
            ?: unexpandedAbbreviationRulesForMedication(med, resolvedLanguageEnum)
          val safeScheduleExplanation = sanitizeExplanationOrNull(
            text = rawScheduleExplanation,
            unexpandedRules = perMedUnexpanded,
            language = resolvedLanguageEnum
          )

          ExplainedMedication(
            sourceMedication = med,
            printedScheduleExplanation = safeScheduleExplanation,
            safetyNote = safetyStrings.medicationSafetyNote
          )
        }

        CategoryExplanation.Clinical(
          documentSubtype = clinical.documentSubtype,
          facilityOrDoctorName = clinical.facilityOrDoctorName,
          followUpDate = clinical.followUpDate,
          documentOverviewExplanation = sanitizeNarrativeOrNull(
            text = root.optNonBlankString("documentOverviewExplanation"),
            contexts = medAbbreviationContexts,
            language = resolvedLanguageEnum
          ),
          diagnoses = clinical.primaryDiagnoses.mapIndexed { idx, fact ->
            ExplainedClinicalFact(
              sourceFact = fact,
              plainLanguageExplanation = sanitizeNarrativeOrNull(
                text = diagnosesMap[idx]?.optNonBlankString("plainLanguageExplanation"),
                contexts = medAbbreviationContexts,
                language = resolvedLanguageEnum
              )
            )
          },
          procedures = clinical.surgicalProcedures.mapIndexed { idx, fact ->
            ExplainedClinicalFact(
              sourceFact = fact,
              plainLanguageExplanation = sanitizeNarrativeOrNull(
                text = proceduresMap[idx]?.optNonBlankString("plainLanguageExplanation"),
                contexts = medAbbreviationContexts,
                language = resolvedLanguageEnum
              )
            )
          },
          medications = explainedMeds,
          allergies = clinical.allergiesRecorded.mapIndexed { idx, fact ->
            ExplainedClinicalFact(
              sourceFact = fact,
              plainLanguageExplanation = sanitizeNarrativeOrNull(
                text = allergiesMap[idx]?.optNonBlankString("plainLanguageExplanation"),
                contexts = medAbbreviationContexts,
                language = resolvedLanguageEnum
              )
            )
          },
          dischargeInstructions = clinical.dischargeInstructions.mapIndexed { idx, fact ->
            ExplainedClinicalFact(
              sourceFact = fact,
              plainLanguageExplanation = sanitizeNarrativeOrNull(
                text = instructionsMap[idx]?.optNonBlankString("plainLanguageExplanation"),
                contexts = medAbbreviationContexts,
                language = resolvedLanguageEnum
              )
            )
          }
        )
      }

      is UniversalMedicalReport.Unidentified -> {
        throw IllegalArgumentException("Unidentified documents cannot be explained.")
      }
    }

    val conciseSummary = sanitizeNarrativeOrNull(
      text = root.optNonBlankString("conciseSummary"),
      contexts = medAbbreviationContexts,
      language = resolvedLanguageEnum
    ) ?: safetyStrings.safeFallbackSummary

    val medicalTerms = root.optObjectList("medicalTerms").mapNotNull { termObj ->
      val term = termObj.optNonBlankString("term") ?: return@mapNotNull null
      val definition = termObj.optNonBlankString("simpleDefinition")
        ?: termObj.optNonBlankString("definition")
        ?: return@mapNotNull null
      if (LocalizedSafetyResources.containsUnsafeMedicationAdvice(term, resolvedLanguageEnum) ||
        LocalizedSafetyResources.containsUnsafeMedicationAdvice(definition, resolvedLanguageEnum)
      ) {
        return@mapNotNull null
      }
      val combinedTermText = "$term $definition"
      val applicableRules = relevantUnexpandedRulesForNarrative(
        text = combinedTermText,
        contexts = medAbbreviationContexts,
        language = resolvedLanguageEnum
      )
      if (hasUnsafeAbbreviationExpansion(combinedTermText, applicableRules, resolvedLanguageEnum)) {
        return@mapNotNull null
      }
      MedicalTermDefinition(term = term, simpleDefinition = definition)
    }

    val questionsForDoctor = root.optStringList("questionsForDoctor").filter { question ->
      sanitizeNarrativeOrNull(question, medAbbreviationContexts, resolvedLanguageEnum) != null
    }

    return ReportExplanationResult(
      category = sourceExtraction.category,
      language = resolvedLanguageName,
      conciseSummary = conciseSummary,
      categoryExplanation = categoryExplanation,
      medicalTerms = medicalTerms,
      questionsForDoctor = questionsForDoctor,
      uncertaintyNotes = uncertaintyNotes.toList(),
      safetyDisclaimer = safetyStrings.medicalExplanationDisclaimer
    )
  }

  private data class MedicationAbbreviationContext(
    val med: ExtractedMedication,
    val unexpandedRules: List<LocalizedSafetyResources.AbbreviationRuleSpec>
  )

  private val DRUG_GENERIC_FORM_TOKENS = setOf(
    "tab", "tabs", "tablet", "tablets", "cap", "caps", "capsule", "capsules",
    "syp", "syrup", "inj", "injection", "drop", "drops", "oint", "ointment",
    "cream", "gel", "sol", "solution", "susp", "suspension", "mg", "mcg", "ml", "gm"
  )

  private fun textMentionsDrugName(text: String, drugName: String?): Boolean {
    val cleanDrug = drugName?.trim()?.takeIf { it.isNotEmpty() }
      ?.let { LocalizedSafetyResources.normalizeForMatching(it) }
      ?: return false
    val normalizedText = LocalizedSafetyResources.normalizeForMatching(text)
    val escapedFull = Regex.escape(cleanDrug)
    if (
      Regex(
        "${LocalizedSafetyResources.B_START}$escapedFull${LocalizedSafetyResources.B_END}",
        RegexOption.IGNORE_CASE
      ).containsMatchIn(normalizedText)
    ) {
      return true
    }
    val tokens = cleanDrug.split(Regex("[^${LocalizedSafetyResources.UNICODE_WORD_CLASS}]+"))
      .map { it.trim() }
      .filter { token ->
        token.length >= 4 &&
          token.any { it.isLetter() } &&
          token.lowercase(Locale.ROOT) !in DRUG_GENERIC_FORM_TOKENS
      }
    return tokens.any { token ->
      Regex(
        "${LocalizedSafetyResources.B_START}${Regex.escape(token)}${LocalizedSafetyResources.B_END}",
        RegexOption.IGNORE_CASE
      ).containsMatchIn(normalizedText)
    }
  }

  private fun relevantUnexpandedRulesForNarrative(
    text: String,
    contexts: List<MedicationAbbreviationContext>,
    language: SupportedExplanationLanguage
  ): List<LocalizedSafetyResources.AbbreviationRuleSpec> {
    if (text.isBlank() || contexts.isEmpty()) return emptyList()
    val contextsWithUnexpanded = contexts.filter { it.unexpandedRules.isNotEmpty() }
    if (contextsWithUnexpanded.isEmpty()) return emptyList()

    val normalizedText = LocalizedSafetyResources.normalizeForMatching(text)
    val rules = LinkedHashSet<LocalizedSafetyResources.AbbreviationRuleSpec>()

    // 1. Apply only unexpanded rules belonging to medications explicitly referenced in this narrative
    val mentionedContexts = contexts.filter { textMentionsDrugName(normalizedText, it.med.drugName) }
    mentionedContexts.forEach { rules.addAll(it.unexpandedRules) }

    // 2. Apply any unexpanded rule whose abbreviation code itself appears in the text
    val allUnexpandedRules = contextsWithUnexpanded.flatMap { it.unexpandedRules }.distinct()
    allUnexpandedRules.filterTo(rules) { rule -> rule.codeRegex.containsMatchIn(normalizedText) }

    // 3. Apply unexpanded rules when a clause with generic medication/dosing wording also contains an expansion
    if (mentionedContexts.isEmpty() &&
      LocalizedSafetyResources.containsMedicationReference(normalizedText, language)
    ) {
      val clauses = LocalizedSafetyResources.splitClausesForNarrative(normalizedText, language)
      clauses.forEach { clause ->
        if (LocalizedSafetyResources.containsMedicationReference(clause, language)) {
          allUnexpandedRules.filterTo(rules) { rule ->
            rule.expansionRegexesFor(language).any { expRegex -> expRegex.containsMatchIn(clause) }
          }
        }
      }
    }

    return rules.toList()
  }

  private fun sanitizeNarrativeOrNull(
    text: String?,
    contexts: List<MedicationAbbreviationContext>,
    language: SupportedExplanationLanguage
  ): String? {
    val clean = text?.trim()?.takeIf { it.isNotEmpty() } ?: return null
    if (LocalizedSafetyResources.containsUnsafeMedicationAdvice(clean, language)) return null
    val applicableRules = relevantUnexpandedRulesForNarrative(clean, contexts, language)
    if (hasUnsafeAbbreviationExpansion(clean, applicableRules, language)) return null
    return clean
  }

  private fun medicationSourceText(med: ExtractedMedication): String =
    listOfNotNull(
      med.dosage,
      med.frequency,
      med.duration,
      med.instructions,
      med.provenance.sourceSnippet
    ).joinToString(" ")

  private fun unexpandedAbbreviationRulesForMedication(
    med: ExtractedMedication,
    language: SupportedExplanationLanguage
  ): List<LocalizedSafetyResources.AbbreviationRuleSpec> {
    val sourceText = LocalizedSafetyResources.normalizeForMatching(medicationSourceText(med))
    if (sourceText.isBlank()) return emptyList()
    return LocalizedSafetyResources.AMBIGUOUS_ABBREVIATION_RULES.filter { rule ->
      rule.codeRegex.containsMatchIn(sourceText) &&
        rule.expansionRegexesFor(language).none { expRegex -> expRegex.containsMatchIn(sourceText) }
    }
  }

  private fun hasUnsafeAbbreviationExpansion(
    text: String,
    unexpandedRules: List<LocalizedSafetyResources.AbbreviationRuleSpec>,
    language: SupportedExplanationLanguage
  ): Boolean {
    if (unexpandedRules.isEmpty()) return false
    val normalizedText = LocalizedSafetyResources.normalizeForMatching(text)
    return unexpandedRules.any { rule ->
      rule.expansionRegexesFor(language).any { expRegex -> expRegex.containsMatchIn(normalizedText) }
    }
  }

  internal fun containsUnsafeMedicationAdvice(
    text: String,
    language: String = ExplanationConstants.DEFAULT_EXPLANATION_LANGUAGE
  ): Boolean = LocalizedSafetyResources.containsUnsafeMedicationAdvice(
    text = text,
    language = LocalizedSafetyResources.resolveLanguage(language)
  )

  private fun sanitizeExplanationOrNull(
    text: String?,
    unexpandedRules: List<LocalizedSafetyResources.AbbreviationRuleSpec> = emptyList(),
    language: SupportedExplanationLanguage = SupportedExplanationLanguage.ENGLISH
  ): String? {
    val clean = text?.trim()?.takeIf { it.isNotEmpty() } ?: return null
    if (LocalizedSafetyResources.containsUnsafeMedicationAdvice(clean, language)) return null
    if (hasUnsafeAbbreviationExpansion(clean, unexpandedRules, language)) return null
    return clean
  }

  /**
   * Maps AI entries by validated 0-based index.
   * Rejects out-of-bounds, negative, non-integer, duplicate, or label-mismatched entries so they
   * safely fall back to `null` (original item without explanation).
   */
  private fun buildValidatedIndexMap(
    entries: List<Map<String, Any?>>,
    indexKeys: List<String>,
    sourceSize: Int,
    expectedNameForIndex: (Int) -> String?,
    echoedNameKeys: List<String>
  ): Map<Int, Map<String, Any?>> {
    if (sourceSize <= 0 || entries.isEmpty()) return emptyMap()
    val counts = HashMap<Int, Int>()
    val candidates = HashMap<Int, Map<String, Any?>>()

    for (entry in entries) {
      val idx = indexKeys.firstNotNullOfOrNull { key -> entry.optValidIndex(key) } ?: continue
      if (idx !in 0 until sourceSize) continue
      counts[idx] = (counts[idx] ?: 0) + 1
      candidates.putIfAbsent(idx, entry)
    }

    val validated = HashMap<Int, Map<String, Any?>>()
    for ((idx, count) in counts) {
      if (count != 1) continue // Duplicate index -> safe fallback with no explanation
      val entry = candidates[idx] ?: continue
      val expectedName = expectedNameForIndex(idx)?.trim()?.takeIf { it.isNotEmpty() }
      val echoedName = echoedNameKeys.firstNotNullOfOrNull { key -> entry.optNonBlankString(key) }?.trim()
      if (expectedName != null && echoedName != null && !namesMatch(expectedName, echoedName)) {
        continue // Mismatched identifier -> safe fallback with no explanation
      }
      validated[idx] = entry
    }
    return validated
  }

  /**
   * Maps AI entries by validated allow-listed field key.
   * Rejects unknown, duplicate, or value-mismatched keys so they safely fall back to `null`.
   */
  private fun buildValidatedFieldKeyMap(
    entries: List<Map<String, Any?>>,
    allowedKeys: Set<String>,
    expectedValueForKey: (String) -> String?,
    echoedValueKeys: List<String>
  ): Map<String, Map<String, Any?>> {
    if (entries.isEmpty()) return emptyMap()
    val counts = HashMap<String, Int>()
    val candidates = HashMap<String, Map<String, Any?>>()

    for (entry in entries) {
      val key = entry.optNonBlankString("fieldKey") ?: entry.optNonBlankString("key") ?: continue
      if (key !in allowedKeys) continue
      counts[key] = (counts[key] ?: 0) + 1
      candidates.putIfAbsent(key, entry)
    }

    val validated = HashMap<String, Map<String, Any?>>()
    for ((key, count) in counts) {
      if (count != 1) continue // Duplicate key -> safe fallback
      val entry = candidates[key] ?: continue
      val expectedValue = expectedValueForKey(key)?.trim()?.takeIf { it.isNotEmpty() }
      val echoedValue = echoedValueKeys.firstNotNullOfOrNull { vKey -> entry.optNonBlankString(vKey) }?.trim()
      if (expectedValue != null && echoedValue != null && !namesMatch(expectedValue, echoedValue)) {
        continue // Mismatched original value -> safe fallback
      }
      validated[key] = entry
    }
    return validated
  }

  private fun namesMatch(expected: String, echoed: String): Boolean {
    val normExpected = LocalizedSafetyResources.normalizeForMatching(expected)
      .lowercase(Locale.ROOT)
      .replace(Regex("\\s+"), " ")
      .trim()
    val normEchoed = LocalizedSafetyResources.normalizeForMatching(echoed)
      .lowercase(Locale.ROOT)
      .replace(Regex("\\s+"), " ")
      .trim()
    return normExpected == normEchoed
  }

  private fun Map<String, Any?>.optValidIndex(key: String): Int? {
    val raw = this[key] ?: return null
    val value = when (raw) {
      is Int -> raw
      is Long -> raw.toInt()
      is Double -> if (raw.isFinite() && raw % 1.0 == 0.0) raw.toInt() else null
      is Number -> raw.toInt()
      is String -> raw.trim().toIntOrNull()
      else -> null
    }
    return value?.takeIf { it >= 0 }
  }

  @Suppress("UNCHECKED_CAST")
  private fun Map<String, Any?>.optObjectList(key: String): List<Map<String, Any?>> {
    val rawList = this[key] as? List<*> ?: return emptyList()
    return rawList.mapNotNull { it as? Map<String, Any?> }
  }

  private fun Map<String, Any?>.optStringList(key: String): List<String> {
    val rawList = this[key] as? List<*> ?: return emptyList()
    return rawList.mapNotNull { (it as? String)?.trim()?.takeIf { str -> str.isNotEmpty() } }
  }

  private fun Map<String, Any?>.optNonBlankString(key: String): String? {
    val raw = this[key] as? String ?: return null
    return raw.trim().takeIf { it.isNotEmpty() }
  }
}
