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

  private const val SAFE_FALLBACK_SUMMARY =
    "Plain-language summary is limited for this document. Please review the preserved original extracted entries below with your doctor."

  private val UNCERTAIN_STATUS_NOTE =
    "Marked uncertain or unreadable in the source document; please check the original report with your doctor."

  private val NOT_MENTIONED_STATUS_NOTE =
    "Finding status was not mentioned in the source document."

  private data class AbbreviationRule(
    val codeRegex: Regex,
    val expansionRegex: Regex
  )

  private val AMBIGUOUS_ABBREVIATION_RULES = listOf(
    AbbreviationRule(
      codeRegex = Regex("(?<![A-Za-z0-9])O\\.?D\\.?(?![A-Za-z0-9])", RegexOption.IGNORE_CASE),
      expansionRegex = Regex(
        "\\b(once\\s+(?:a\\s+)?daily|once\\s+(?:per\\s+|a\\s+)?day|one\\s+time\\s+(?:a\\s+|per\\s+)?day|every\\s+day|right\\s+eye|oculus\\s+dexter)\\b",
        RegexOption.IGNORE_CASE
      )
    ),
    AbbreviationRule(
      codeRegex = Regex("(?<![A-Za-z0-9])Q\\.?D\\.?(?![A-Za-z0-9])", RegexOption.IGNORE_CASE),
      expansionRegex = Regex(
        "\\b(once\\s+(?:a\\s+)?daily|once\\s+(?:per\\s+|a\\s+)?day|one\\s+time\\s+(?:a\\s+|per\\s+)?day|every\\s+day|each\\s+day)\\b",
        RegexOption.IGNORE_CASE
      )
    ),
    AbbreviationRule(
      codeRegex = Regex("(?<![A-Za-z0-9])(?:B\\.?D\\.?|B\\.?I\\.?D\\.?)(?![A-Za-z0-9])", RegexOption.IGNORE_CASE),
      expansionRegex = Regex(
        "\\b(twice\\s+(?:a\\s+)?daily|twice\\s+(?:per\\s+|a\\s+)?day|two\\s+times\\s+(?:a\\s+|per\\s+)?day|2\\s+times\\s+(?:a\\s+)?day|every\\s+12\\s+hours)\\b",
        RegexOption.IGNORE_CASE
      )
    ),
    AbbreviationRule(
      codeRegex = Regex("(?<![A-Za-z0-9])(?:T\\.?I\\.?D\\.?|T\\.?D\\.?S\\.?)(?![A-Za-z0-9])", RegexOption.IGNORE_CASE),
      expansionRegex = Regex(
        "\\b(three\\s+times\\s+(?:a\\s+)?daily|three\\s+times\\s+(?:per\\s+|a\\s+)?day|thrice\\s+(?:a\\s+)?day|thrice\\s+daily|3\\s+times\\s+(?:a\\s+)?day|every\\s+8\\s+hours)\\b",
        RegexOption.IGNORE_CASE
      )
    ),
    AbbreviationRule(
      codeRegex = Regex("(?<![A-Za-z0-9])Q\\.?I\\.?D\\.?(?![A-Za-z0-9])", RegexOption.IGNORE_CASE),
      expansionRegex = Regex(
        "\\b(four\\s+times\\s+(?:a\\s+)?daily|four\\s+times\\s+(?:per\\s+|a\\s+)?day|4\\s+times\\s+(?:a\\s+)?day|every\\s+6\\s+hours)\\b",
        RegexOption.IGNORE_CASE
      )
    ),
    AbbreviationRule(
      codeRegex = Regex("(?<![A-Za-z0-9])(?:P\\.?R\\.?N\\.?|S\\.?O\\.?S\\.?)(?![A-Za-z0-9])", RegexOption.IGNORE_CASE),
      expansionRegex = Regex(
        "\\b(as\\s+needed|when\\s+needed|if\\s+needed|as\\s+required|when\\s+required|only\\s+when|when\\s+symptoms)\\b",
        RegexOption.IGNORE_CASE
      )
    ),
    AbbreviationRule(
      codeRegex = Regex("(?<![A-Za-z0-9])H\\.?S\\.?(?![A-Za-z0-9])", RegexOption.IGNORE_CASE),
      expansionRegex = Regex(
        "\\b(at\\s+bedtime|before\\s+(?:bed|sleep|sleeping)|bedtime|every\\s+night|at\\s+night|half[- ]strength)\\b",
        RegexOption.IGNORE_CASE
      )
    ),
    AbbreviationRule(
      codeRegex = Regex("(?<![A-Za-z0-9])Q\\.?O\\.?D\\.?(?![A-Za-z0-9])", RegexOption.IGNORE_CASE),
      expansionRegex = Regex("\\b(every\\s+other\\s+day|alternate\\s+days?)\\b", RegexOption.IGNORE_CASE)
    ),
    AbbreviationRule(
      codeRegex = Regex("(?<![A-Za-z0-9])A\\.?C\\.?(?![A-Za-z0-9])", RegexOption.IGNORE_CASE),
      expansionRegex = Regex("\\b(before\\s+(?:meals?|eating|food)|on\\s+an\\s+empty\\s+stomach)\\b", RegexOption.IGNORE_CASE)
    ),
    AbbreviationRule(
      codeRegex = Regex("(?<![A-Za-z0-9])P\\.?C\\.?(?![A-Za-z0-9])", RegexOption.IGNORE_CASE),
      expansionRegex = Regex("\\b(after\\s+(?:meals?|eating|food))\\b", RegexOption.IGNORE_CASE)
    ),
    AbbreviationRule(
      codeRegex = Regex("(?<![A-Za-z0-9])(?:O\\.?S\\.?|O\\.?U\\.?)(?![A-Za-z0-9])", RegexOption.IGNORE_CASE),
      expansionRegex = Regex("\\b(left\\s+eye|oculus\\s+sinister|both\\s+eyes|each\\s+eye|oculus\\s+uterque)\\b", RegexOption.IGNORE_CASE)
    )
  )

  private val PROTECTIVE_MEDICATION_WARNING_REGEX = Regex(
    "\\b(?:do\\s+not|don't|never|should\\s+not|must\\s+not)\\s+(?:ever\\s+)?" +
      "(?:stop(?:\\s+taking|\\s+using)?|discontinue|halt(?:\\s+taking|\\s+using)?|cease|quit|pause|hold|skip|omit|" +
      "change|alter|adjust|modify|increase|decrease|reduce|double|taper(?:\\s+off)?|start|restart)" +
      "[^.!?;\\n]*?\\b(?:without\\s+(?:first\\s+)?(?:asking|consulting|speaking\\s+(?:to|with)|talking\\s+(?:to|with)|checking\\s+with)\\s+(?:a\\s+|your\\s+)?(?:doctor|physician|prescriber|pharmacist|clinician|healthcare\\s+provider)|" +
      "unless\\s+(?:told|directed|advised|instructed)\\s+by\\s+(?:a\\s+|your\\s+)?(?:doctor|physician|prescriber|pharmacist|clinician|healthcare\\s+provider))",
    RegexOption.IGNORE_CASE
  )

  private val CONSULT_BEFORE_CHANGE_WARNING_REGEX = Regex(
    "\\b(?:always\\s+)?(?:ask|consult|check\\s+with|speak\\s+(?:to|with)|talk\\s+(?:to|with))\\s+" +
      "(?:a\\s+|your\\s+)?(?:doctor|physician|prescriber|pharmacist|clinician|healthcare\\s+provider)\\s+" +
      "before\\s+(?:stopping|discontinuing|halting|changing|altering|adjusting|increasing|decreasing|reducing|tapering|starting|omitting|skipping)\\b[^.!?;\\n]*",
    RegexOption.IGNORE_CASE
  )

  private val UNSAFE_MEDICATION_ADVICE_REGEX = Regex(
    "\\b(" +
      // Stopping / halting / pausing / omitting / starting directives
      "stop\\s+taking|stop\\s+using|stop\\s+this\\s+(?:med|medicine|medication|drug|pill|tablet|capsule)|" +
      "discontinue(?:\\s+(?:taking|using|this))?|halt\\s+(?:taking|using|this)|cease\\s+(?:taking|using)|quit\\s+(?:taking|using)|" +
      "pause\\s+(?:taking|using|this)|omit\\s+(?:a|an|one|the|your)\\s+(?:dose|pill|tablet|capsule)|" +
      "skip\\s+(?:a|an|one|the|your)\\s+(?:dose|pill|tablet|capsule)|start\\s+taking|begin\\s+taking|" +
      // Tapering / changing / adjusting / doubling / pill-count modifications
      "taper\\s*(?:off|down)?|wean\\s*(?:off|yourself)|" +
      "increase\\s+(?:the\\s+|your\\s+)?(?:dose|dosage|frequency|duration|amount)|" +
      "decrease\\s+(?:the\\s+|your\\s+)?(?:dose|dosage|frequency|duration|amount)|" +
      "reduce\\s+(?:the\\s+|your\\s+)?(?:dose|dosage|frequency|duration|amount)|" +
      "lower\\s+(?:the\\s+|your\\s+)?(?:dose|dosage)|raise\\s+(?:the\\s+|your\\s+)?(?:dose|dosage)|" +
      "double\\s+(?:the\\s+|your\\s+|up\\s+(?:on\\s+)?(?:the\\s+|your\\s+)?)?(?:dose|dosage|pills?|tablets?|capsules?)?|" +
      "halve\\s+(?:the\\s+|your\\s+)?(?:dose|pill|tablet)|cut\\s+(?:the\\s+|your\\s+)?(?:dose|pill|tablet)\\s+in\\s+half|" +
      "split\\s+(?:the\\s+|your\\s+)?(?:dose|pill|tablet)|" +
      "take\\s+(?:two|2|three|3|four|4|extra|double|half|additional|more|fewer)\\s+(?:pills?|tablets?|capsules?|doses?)|" +
      "take\\s+instead|switch\\s+(?:this\\s+|your\\s+(?:medicine|medication|drug)\\s+)?to\\b|" +
      "change\\s+(?:the\\s+|your\\s+)?(?:dose|dosage|frequency|duration|schedule)|" +
      "adjust\\s+(?:the\\s+|your\\s+)?(?:dose|dosage|frequency|duration|schedule)|" +
      "modify\\s+(?:the\\s+|your\\s+)?(?:dose|dosage|frequency|duration|schedule)|" +
      // Prescribing recommendations
      "you\\s+should\\s+(?:take|start|stop|switch|increase|decrease|adjust|taper)|" +
      "we\\s+recommend\\s+(?:taking|starting|stopping|changing)|" +
      "i\\s+recommend\\s+(?:taking|starting|stopping|changing)|" +
      "consider\\s+(?:taking|starting|stopping|switching)|" +
      "you\\s+need\\s+to\\s+(?:take|start|stop|increase|decrease)|" +
      // Unsupported medication purpose / indication (condition-agnostic)
      "indicated\\s+(?:for|to\\s+treat|in\\s+the\\s+treatment\\s+of)|" +
      "prescribed\\s+(?:for|to\\s+(?:treat|manage|control|cure|relieve|lower|reduce|prevent|ease|address|help)|because)|" +
      "used\\s+(?:to\\s+(?:treat|manage|control|cure|relieve|lower|reduce|prevent|ease|address|help)|for\\s+(?!your\\s+(?:review|record|records|reference|visit|appointment|clarity|information)|measuring|checking|recording|evaluating|assessing|examining|imaging|viewing|detecting|screening|identifying|observing|monitoring|displaying|tracking|logging|informational|diagnostic|reference|\\d)[a-z]+)|" +
      "meant\\s+(?:for|to\\s+(?:treat|manage|control|cure|relieve|lower|reduce|prevent))|" +
      "intended\\s+(?:for|to\\s+(?:treat|manage|control|cure|relieve|lower|reduce|prevent))|" +
      "given\\s+(?:for|to\\s+(?:treat|manage|control|cure|relieve|lower|reduce|prevent))|" +
      "taken\\s+(?:for|to\\s+(?:treat|manage|control|cure|relieve|lower|reduce|prevent))|" +
      "(?:to|for)\\s+(?:treat|treating|cure|curing|relieve|relieving|alleviate|alleviating)\\s+[a-z]+|" +
      "(?:treats|cures|relieves|alleviates)\\s+[a-z]+|" +
      "(?:helps|designed|formulated|works)\\s+(?:to\\s+)?(?:treat|manage|control|cure|relieve|lower|reduce|prevent)\\s+[a-z]+|" +
      "(?:this\\s+)?(?:medication|medicine|drug|tablet|capsule|pill)\\s+(?:that|which)\\s+(?:treats|manages|controls|lowers|reduces|prevents|relieves|cures)|" +
      "(?:this\\s+)?(?:medication|medicine|drug|tablet|capsule|pill)\\s+(?:is|are)\\s+(?:primarily\\s+)?for\\s+(?!\\d|oral|external|topical|informational|your\\s+(?:review|record|records|reference|visit|appointment|clarity|information))[a-z]+|" +
      "(?:take|taking)\\s+(?:this\\s+|the\\s+|your\\s+)?(?:medication|medicine|drug|pill|tablet|capsule|it)\\s+(?:for|to\\s+(?:treat|manage|control|cure|relieve|lower|reduce|prevent))\\s+(?!\\d|a\\s+(?:day|week|month|year)|one\\s+|two\\s+|three\\s+|four\\s+|five\\s+|six\\s+|seven\\s+|breakfast|lunch|dinner|meals?|bedtime|your\\s+(?:next|doctor|review|records?))[a-z]+" +
      ")\\b",
    RegexOption.IGNORE_CASE
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

    val root = jsonObjectAdapter.fromJson(cleanJson)
      ?: throw IllegalArgumentException("Model returned invalid explanation JSON.")

    val medAbbreviationContexts: List<MedicationAbbreviationContext> =
      (sourceExtraction.report as? UniversalMedicalReport.Clinical)
        ?.report
        ?.prescribedMedications
        ?.map { med ->
          MedicationAbbreviationContext(
            med = med,
            unexpandedRules = unexpandedAbbreviationRulesForMedication(med)
          )
        }
        ?: emptyList()

    val uncertaintyNotes = LinkedHashSet<String>()
    root.optStringList("uncertaintyNotes").forEach { note ->
      val safeNote = sanitizeNarrativeOrNull(note, medAbbreviationContexts)
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
                "${src.originalName ?: "Item ${idx + 1}"}: $UNCERTAIN_STATUS_NOTE"
              )
              ExplainedLabItem(
                sourceItem = src,
                whatItMeasures = null,
                plainLanguageExplanation = null,
                uncertaintyNote = UNCERTAIN_STATUS_NOTE
              )
            }
            FindingStatus.NOT_MENTIONED -> {
              val hasPrintableValue = !src.originalValue.isNullOrBlank()
              uncertaintyNotes.add(
                "${src.originalName ?: "Item ${idx + 1}"}: $NOT_MENTIONED_STATUS_NOTE"
              )
              ExplainedLabItem(
                sourceItem = src,
                whatItMeasures = sanitizeExplanationOrNull(aiEntry?.optNonBlankString("whatItMeasures")),
                plainLanguageExplanation = if (hasPrintableValue) {
                  sanitizeExplanationOrNull(aiEntry?.optNonBlankString("plainLanguageExplanation"))
                } else {
                  null
                },
                uncertaintyNote = NOT_MENTIONED_STATUS_NOTE
              )
            }
            else -> {
              ExplainedLabItem(
                sourceItem = src,
                whatItMeasures = sanitizeExplanationOrNull(aiEntry?.optNonBlankString("whatItMeasures")),
                plainLanguageExplanation = sanitizeExplanationOrNull(
                  aiEntry?.optNonBlankString("plainLanguageExplanation")
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
                "${finding.anatomicalSite ?: "Finding ${idx + 1}"}: $UNCERTAIN_STATUS_NOTE"
              )
              ExplainedRadiologyFinding(
                sourceFinding = finding,
                plainLanguageExplanation = null,
                uncertaintyNote = UNCERTAIN_STATUS_NOTE
              )
            }
            FindingStatus.NOT_MENTIONED -> {
              uncertaintyNotes.add(
                "${finding.anatomicalSite ?: "Finding ${idx + 1}"}: $NOT_MENTIONED_STATUS_NOTE"
              )
              ExplainedRadiologyFinding(
                sourceFinding = finding,
                plainLanguageExplanation = if (!finding.observationText.isNullOrBlank()) {
                  sanitizeExplanationOrNull(aiEntry?.optNonBlankString("plainLanguageExplanation"))
                } else {
                  null
                },
                uncertaintyNote = NOT_MENTIONED_STATUS_NOTE
              )
            }
            else -> ExplainedRadiologyFinding(
              sourceFinding = finding,
              plainLanguageExplanation = sanitizeExplanationOrNull(
                aiEntry?.optNonBlankString("plainLanguageExplanation")
              ),
              uncertaintyNote = null
            )
          }
        }

        val explainedImpressions = rad.formalImpressions.mapIndexed { idx, imp ->
          val aiEntry = impressionMap[idx]
          when (imp.findingStatus) {
            FindingStatus.UNCERTAIN_OR_UNREADABLE -> {
              uncertaintyNotes.add("Impression ${idx + 1}: $UNCERTAIN_STATUS_NOTE")
              ExplainedRadiologyImpression(
                sourceImpression = imp,
                plainLanguageExplanation = null,
                uncertaintyNote = UNCERTAIN_STATUS_NOTE
              )
            }
            FindingStatus.NOT_MENTIONED -> {
              uncertaintyNotes.add("Impression ${idx + 1}: $NOT_MENTIONED_STATUS_NOTE")
              ExplainedRadiologyImpression(
                sourceImpression = imp,
                plainLanguageExplanation = if (!imp.impressionText.isNullOrBlank()) {
                  sanitizeExplanationOrNull(aiEntry?.optNonBlankString("plainLanguageExplanation"))
                } else {
                  null
                },
                uncertaintyNote = NOT_MENTIONED_STATUS_NOTE
              )
            }
            else -> ExplainedRadiologyImpression(
              sourceImpression = imp,
              plainLanguageExplanation = sanitizeExplanationOrNull(
                aiEntry?.optNonBlankString("plainLanguageExplanation")
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
            sanitizeExplanationOrNull(root.optNonBlankString("studyOverviewExplanation"))
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
          uncertaintyNotes.add("Cardiology report: $UNCERTAIN_STATUS_NOTE")
        } else if (cardio.findingStatus == FindingStatus.NOT_MENTIONED) {
          uncertaintyNotes.add("Cardiology report: $NOT_MENTIONED_STATUS_NOTE")
        }

        val explainedMetrics = CARDIOLOGY_METRIC_SPECS.mapNotNull { spec ->
          val sourceVal = sourceValuesByKey[spec.key]?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
          val aiEntry = if (isUncertainReport) null else fieldMap[spec.key]
          ExplainedCardiologyMetric(
            fieldKey = spec.key,
            label = spec.label,
            originalValue = sourceVal,
            plainLanguageExplanation = sanitizeExplanationOrNull(
              aiEntry?.optNonBlankString("plainLanguageExplanation")
            ),
            provenance = cardio.fieldProvenance[spec.key] ?: cardio.provenance
          )
        }

        // Strict field-key pathway only: never substitute unvalidated root conclusionExplanation
        val conclusionText = when {
          isUncertainReport -> null
          cardio.formalConclusion.isNullOrBlank() -> null
          else -> sanitizeExplanationOrNull(
            fieldMap["formalConclusion"]?.optNonBlankString("plainLanguageExplanation")
          )
        }

        val cardioUncertainty = when {
          isUncertainReport -> UNCERTAIN_STATUS_NOTE
          cardio.formalConclusion.isNullOrBlank() ->
            "Formal cardiology conclusion was not reported in the extracted document."
          cardio.findingStatus == FindingStatus.NOT_MENTIONED -> NOT_MENTIONED_STATUS_NOTE
          else -> null
        }
        if (cardioUncertainty != null) {
          uncertaintyNotes.add(cardioUncertainty)
        }

        CategoryExplanation.Cardiology(
          sourceReport = cardio,
          modalityExplanation = if (!cardio.modality.isNullOrBlank() && !isUncertainReport) {
            sanitizeExplanationOrNull(root.optNonBlankString("modalityExplanation"))
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
          uncertaintyNotes.add("Specialised pathology report: $UNCERTAIN_STATUS_NOTE")
        } else if (spec.findingStatus == FindingStatus.NOT_MENTIONED) {
          uncertaintyNotes.add("Specialised pathology report: $NOT_MENTIONED_STATUS_NOTE")
        }

        val explainedSections = SPECIALISED_SECTION_SPECS.mapNotNull { sectionSpec ->
          val sourceText = sourceValuesByKey[sectionSpec.key]?.takeIf { it.isNotBlank() }
            ?: return@mapNotNull null
          val aiEntry = if (isUncertainReport) null else fieldMap[sectionSpec.key]
          ExplainedSpecialisedSection(
            fieldKey = sectionSpec.key,
            sectionTitle = sectionSpec.label,
            originalText = sourceText,
            plainLanguageExplanation = sanitizeExplanationOrNull(
              aiEntry?.optNonBlankString("plainLanguageExplanation")
            ),
            provenance = spec.fieldProvenance[sectionSpec.key] ?: spec.provenance
          )
        }

        // Strict field-key pathway only: never substitute unvalidated root conclusionExplanation
        val conclusionText = when {
          isUncertainReport -> null
          spec.diagnosticConclusion.isNullOrBlank() -> null
          else -> sanitizeExplanationOrNull(
            fieldMap["diagnosticConclusion"]?.optNonBlankString("plainLanguageExplanation")
          )
        }

        val specUncertainty = when {
          isUncertainReport -> UNCERTAIN_STATUS_NOTE
          spec.diagnosticConclusion.isNullOrBlank() ->
            "Pathologist diagnostic conclusion was not reported in the extracted document."
          spec.findingStatus == FindingStatus.NOT_MENTIONED -> NOT_MENTIONED_STATUS_NOTE
          else -> null
        }
        if (specUncertainty != null) {
          uncertaintyNotes.add(specUncertainty)
        }

        CategoryExplanation.Specialised(
          sourceReport = spec,
          reportOverviewExplanation = if (!spec.reportSubtype.isNullOrBlank() && !isUncertainReport) {
            sanitizeExplanationOrNull(root.optNonBlankString("reportOverviewExplanation"))
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
            ?: unexpandedAbbreviationRulesForMedication(med)
          val safeScheduleExplanation = sanitizeExplanationOrNull(
            text = rawScheduleExplanation,
            unexpandedRules = perMedUnexpanded
          )

          ExplainedMedication(
            sourceMedication = med,
            printedScheduleExplanation = safeScheduleExplanation,
            safetyNote = ExplanationConstants.MEDICATION_SAFETY_NOTE
          )
        }

        CategoryExplanation.Clinical(
          documentSubtype = clinical.documentSubtype,
          facilityOrDoctorName = clinical.facilityOrDoctorName,
          followUpDate = clinical.followUpDate,
          documentOverviewExplanation = sanitizeNarrativeOrNull(
            text = root.optNonBlankString("documentOverviewExplanation"),
            contexts = medAbbreviationContexts
          ),
          diagnoses = clinical.primaryDiagnoses.mapIndexed { idx, fact ->
            ExplainedClinicalFact(
              sourceFact = fact,
              plainLanguageExplanation = sanitizeNarrativeOrNull(
                text = diagnosesMap[idx]?.optNonBlankString("plainLanguageExplanation"),
                contexts = medAbbreviationContexts
              )
            )
          },
          procedures = clinical.surgicalProcedures.mapIndexed { idx, fact ->
            ExplainedClinicalFact(
              sourceFact = fact,
              plainLanguageExplanation = sanitizeNarrativeOrNull(
                text = proceduresMap[idx]?.optNonBlankString("plainLanguageExplanation"),
                contexts = medAbbreviationContexts
              )
            )
          },
          medications = explainedMeds,
          allergies = clinical.allergiesRecorded.mapIndexed { idx, fact ->
            ExplainedClinicalFact(
              sourceFact = fact,
              plainLanguageExplanation = sanitizeNarrativeOrNull(
                text = allergiesMap[idx]?.optNonBlankString("plainLanguageExplanation"),
                contexts = medAbbreviationContexts
              )
            )
          },
          dischargeInstructions = clinical.dischargeInstructions.mapIndexed { idx, fact ->
            ExplainedClinicalFact(
              sourceFact = fact,
              plainLanguageExplanation = sanitizeNarrativeOrNull(
                text = instructionsMap[idx]?.optNonBlankString("plainLanguageExplanation"),
                contexts = medAbbreviationContexts
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
      contexts = medAbbreviationContexts
    ) ?: SAFE_FALLBACK_SUMMARY

    val medicalTerms = root.optObjectList("medicalTerms").mapNotNull { termObj ->
      val term = termObj.optNonBlankString("term") ?: return@mapNotNull null
      val definition = termObj.optNonBlankString("simpleDefinition")
        ?: termObj.optNonBlankString("definition")
        ?: return@mapNotNull null
      if (containsUnsafeMedicationAdvice(term) || containsUnsafeMedicationAdvice(definition)) {
        return@mapNotNull null
      }
      val combinedTermText = "$term $definition"
      val applicableRules = relevantUnexpandedRulesForNarrative(combinedTermText, medAbbreviationContexts)
      if (hasUnsafeAbbreviationExpansion(combinedTermText, applicableRules)) {
        return@mapNotNull null
      }
      MedicalTermDefinition(term = term, simpleDefinition = definition)
    }

    val questionsForDoctor = root.optStringList("questionsForDoctor").filter { question ->
      sanitizeNarrativeOrNull(question, medAbbreviationContexts) != null
    }

    return ReportExplanationResult(
      category = sourceExtraction.category,
      language = ExplanationConstants.resolveSafeExplanationLanguage(language),
      conciseSummary = conciseSummary,
      categoryExplanation = categoryExplanation,
      medicalTerms = medicalTerms,
      questionsForDoctor = questionsForDoctor,
      uncertaintyNotes = uncertaintyNotes.toList(),
      safetyDisclaimer = ExplanationConstants.MEDICAL_EXPLANATION_DISCLAIMER
    )
  }

  private data class MedicationAbbreviationContext(
    val med: ExtractedMedication,
    val unexpandedRules: List<AbbreviationRule>
  )

  private val MEDICATION_REFERENCE_REGEX = Regex(
    "\\b(meds?|medicine|medicines|medication|medications|drug|drugs|prescription|prescriptions|prescribed|pill|pills|tablet|tablets|capsule|capsules|dose|doses|dosing|dosage|regimen|drop|drops|eye\\s+drops?|inhaler|syrup|ointment)\\b",
    RegexOption.IGNORE_CASE
  )

  private val DRUG_GENERIC_FORM_TOKENS = setOf(
    "tab", "tabs", "tablet", "tablets", "cap", "caps", "capsule", "capsules",
    "syp", "syrup", "inj", "injection", "drop", "drops", "oint", "ointment",
    "cream", "gel", "sol", "solution", "susp", "suspension", "mg", "mcg", "ml", "gm"
  )

  private fun textMentionsDrugName(text: String, drugName: String?): Boolean {
    val cleanDrug = drugName?.trim()?.takeIf { it.isNotEmpty() } ?: return false
    val escapedFull = Regex.escape(cleanDrug)
    if (Regex("(?<![A-Za-z0-9])$escapedFull(?![A-Za-z0-9])", RegexOption.IGNORE_CASE).containsMatchIn(text)) {
      return true
    }
    val tokens = cleanDrug.split(Regex("[^A-Za-z0-9]+"))
      .map { it.trim() }
      .filter { token ->
        token.length >= 4 &&
          token.any { it.isLetter() } &&
          token.lowercase(Locale.US) !in DRUG_GENERIC_FORM_TOKENS
      }
    return tokens.any { token ->
      Regex("(?<![A-Za-z0-9])${Regex.escape(token)}(?![A-Za-z0-9])", RegexOption.IGNORE_CASE)
        .containsMatchIn(text)
    }
  }

  private fun relevantUnexpandedRulesForNarrative(
    text: String,
    contexts: List<MedicationAbbreviationContext>
  ): List<AbbreviationRule> {
    if (text.isBlank() || contexts.isEmpty()) return emptyList()
    val contextsWithUnexpanded = contexts.filter { it.unexpandedRules.isNotEmpty() }
    if (contextsWithUnexpanded.isEmpty()) return emptyList()

    val rules = LinkedHashSet<AbbreviationRule>()

    // 1. Apply only unexpanded rules belonging to medications explicitly referenced in this narrative
    val mentionedContexts = contexts.filter { textMentionsDrugName(text, it.med.drugName) }
    mentionedContexts.forEach { rules.addAll(it.unexpandedRules) }

    // 2. Apply any unexpanded rule whose abbreviation code itself appears in the text
    val allUnexpandedRules = contextsWithUnexpanded.flatMap { it.unexpandedRules }.distinct()
    allUnexpandedRules.filterTo(rules) { rule -> rule.codeRegex.containsMatchIn(text) }

    // 3. Apply unexpanded rules when a clause with generic medication/dosing wording also contains an expansion
    if (mentionedContexts.isEmpty() && MEDICATION_REFERENCE_REGEX.containsMatchIn(text)) {
      val clauses = text.split(Regex("[.!?;,\\n]|\\band\\b", RegexOption.IGNORE_CASE))
      clauses.forEach { clause ->
        if (MEDICATION_REFERENCE_REGEX.containsMatchIn(clause)) {
          allUnexpandedRules.filterTo(rules) { rule -> rule.expansionRegex.containsMatchIn(clause) }
        }
      }
    }

    return rules.toList()
  }

  private fun sanitizeNarrativeOrNull(
    text: String?,
    contexts: List<MedicationAbbreviationContext>
  ): String? {
    val clean = text?.trim()?.takeIf { it.isNotEmpty() } ?: return null
    if (containsUnsafeMedicationAdvice(clean)) return null
    val applicableRules = relevantUnexpandedRulesForNarrative(clean, contexts)
    if (hasUnsafeAbbreviationExpansion(clean, applicableRules)) return null
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

  private fun unexpandedAbbreviationRulesForMedication(med: ExtractedMedication): List<AbbreviationRule> {
    val sourceText = medicationSourceText(med)
    if (sourceText.isBlank()) return emptyList()
    return AMBIGUOUS_ABBREVIATION_RULES.filter { rule ->
      rule.codeRegex.containsMatchIn(sourceText) && !rule.expansionRegex.containsMatchIn(sourceText)
    }
  }

  private fun hasUnsafeAbbreviationExpansion(
    text: String,
    unexpandedRules: List<AbbreviationRule>
  ): Boolean {
    if (unexpandedRules.isEmpty()) return false
    return unexpandedRules.any { rule -> rule.expansionRegex.containsMatchIn(text) }
  }

  internal fun containsUnsafeMedicationAdvice(text: String): Boolean {
    if (text.isBlank()) return false
    val withoutProtectiveWarnings = text
      .replace(PROTECTIVE_MEDICATION_WARNING_REGEX, " ")
      .replace(CONSULT_BEFORE_CHANGE_WARNING_REGEX, " ")
    return UNSAFE_MEDICATION_ADVICE_REGEX.containsMatchIn(withoutProtectiveWarnings)
  }

  private fun sanitizeExplanationOrNull(
    text: String?,
    unexpandedRules: List<AbbreviationRule> = emptyList()
  ): String? {
    val clean = text?.trim()?.takeIf { it.isNotEmpty() } ?: return null
    if (containsUnsafeMedicationAdvice(clean)) return null
    if (hasUnsafeAbbreviationExpansion(clean, unexpandedRules)) return null
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
    val normExpected = expected.lowercase(Locale.US).replace(Regex("\\s+"), " ").trim()
    val normEchoed = echoed.lowercase(Locale.US).replace(Regex("\\s+"), " ").trim()
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
