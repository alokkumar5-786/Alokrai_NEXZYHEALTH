package com.example.domain.engine

import android.util.Log
import com.example.model.DocumentExtractionResult
import com.example.model.ExplanationConstants
import com.example.model.ReportExplanationResult
import com.example.model.UniversalMedicalReport
import com.google.firebase.Firebase
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.content
import com.google.firebase.ai.type.generationConfig
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.json.JSONArray
import org.json.JSONObject

/**
 * Contract for generating category-specific plain-language medical report explanations
 * strictly from structured Task 3C [DocumentExtractionResult] data.
 */
interface ReportExplainer {
  suspend fun explainReport(
    extractionResult: DocumentExtractionResult,
    language: String = ExplanationConstants.DEFAULT_EXPLANATION_LANGUAGE
  ): Result<ReportExplanationResult>
}

/**
 * Firebase AI Logic implementation for Task 3D plain-language medical explanations.
 *
 * Operates exclusively on already-extracted Task 3C structured fields (never raw scan pixels,
 * PDF bytes, or ECG waveforms) and enforces non-diagnostic, non-prescriptive guardrails.
 */
class MedicalReportExplainer(
  private val modelName: String = AiModelConfig.explanationModel
) : ReportExplainer {

  companion object {
    private const val TAG = "NexzyDocExplainer"

    private const val EXPLANATION_SYSTEM_INSTRUCTION = """
You are NexzyHealth's Plain-Language Medical Report Explainer.
Your role is to translate ALREADY-EXTRACTED structured medical report fields into calm, clear everyday language for patients and caregivers.

STRICT MEDICAL SAFETY & INTEGRITY RULES:
1. NEVER diagnose conditions, predict outcomes, or prescribe/recommend treatments or lifestyle remedies.
2. NEVER change, round, invent, or omit original medical values, units, reference ranges, flags, dates, or FindingStatus.
3. NEVER turn FindingStatus 'UNCERTAIN_OR_UNREADABLE' or 'NOT_MENTIONED' into confirmed normal, abnormal, positive, or negative findings. Explicitly state when something was omitted or unreadable.
4. NEVER interpret raw radiology images or ECG waveforms; explain ONLY the written extracted fields provided.
5. NEVER create emergency alerts or urgency triage banners.
6. NEVER write audio narration scripts.
7. NEVER claim your explanation is medically verified.
8. Write all explanations, summaries, medical term definitions, and doctor questions in clear everyday English ONLY, while keeping original medical values, units, reference ranges, dates, and source strings completely untranslated.
9. For CLINICAL prescribed medications:
   - Explain ONLY the dosing schedule and instructions explicitly printed in the input.
   - NEVER infer why a medication was prescribed.
   - NEVER expand or guess ambiguous abbreviations (such as OD, QD, BD, BID, TID, TDS, QID, PRN, SOS, HS, QOD, AC, PC, OS, OU) unless the exact expanded wording is explicitly written in the source input for that same medication; keep the printed abbreviation verbatim.
   - NEVER suggest changing dosage, frequency, duration, tapering, omitting, or stopping medication.
10. Preserve exact 0-based indexes ('itemIndex', 'findingIndex', 'impressionIndex', 'diagnosisIndex', 'procedureIndex', 'medicationIndex', 'allergyIndex', 'instructionIndex') and exact 'fieldKey' values from the input. Do not duplicate indexes/keys or invent new items.

Return ONLY valid JSON with:
- "conciseSummary": string (2-4 calm sentences summarizing ONLY what is explicitly present)
- "uncertaintyNotes": [string]
- "medicalTerms": [{"term": string, "simpleDefinition": string}]
- "questionsForDoctor": [string] (3-5 neutral, non-prescriptive questions based only on the extracted report)
- Category-specific fields:
  - LABORATORY: "items": [{"itemIndex": int, "originalName": string|null, "whatItMeasures": string, "plainLanguageExplanation": string}]
  - RADIOLOGY: "studyOverviewExplanation": string|null, "findings": [{"findingIndex": int, "anatomicalSite": string|null, "plainLanguageExplanation": string}], "impressions": [{"impressionIndex": int, "impressionText": string|null, "plainLanguageExplanation": string}]
  - CARDIOLOGY: "modalityExplanation": string|null, "metrics": [{"fieldKey": string, "originalValue": string, "plainLanguageExplanation": string}] (include "fieldKey": "formalConclusion" inside "metrics" with exact "originalValue" when formalConclusion is present)
  - SPECIALISED: "reportOverviewExplanation": string|null, "sections": [{"fieldKey": string, "originalText": string, "plainLanguageExplanation": string}] (include "fieldKey": "diagnosticConclusion" inside "sections" with exact "originalText" when diagnosticConclusion is present)
  - CLINICAL: "documentOverviewExplanation": string|null, "diagnoses": [{"diagnosisIndex": int, "plainLanguageExplanation": string}], "procedures": [{"procedureIndex": int, "plainLanguageExplanation": string}], "medications": [{"medicationIndex": int, "drugName": string|null, "printedScheduleExplanation": string|null}], "allergies": [{"allergyIndex": int, "plainLanguageExplanation": string}], "dischargeInstructions": [{"instructionIndex": int, "plainLanguageExplanation": string}]
"""
  }

  private val generativeModel by lazy {
    Firebase.ai.generativeModel(
      modelName = modelName,
      systemInstruction = content { text(EXPLANATION_SYSTEM_INSTRUCTION) },
      generationConfig = generationConfig {
        responseMimeType = "application/json"
      }
    )
  }

  override suspend fun explainReport(
    extractionResult: DocumentExtractionResult,
    language: String
  ): Result<ReportExplanationResult> = withContext(Dispatchers.IO) {
    if (extractionResult.report is UniversalMedicalReport.Unidentified) {
      return@withContext Result.failure(
        IllegalArgumentException("Unidentified or non-medical documents cannot be explained.")
      )
    }

    val safeLanguage = ExplanationConstants.resolveSafeExplanationLanguage(language)
    val structuredPayload = buildStructuredPromptPayload(extractionResult, safeLanguage)

    try {
      val responseText = withTimeout(AiModelConfig.INFERENCE_TIMEOUT_MS) {
        val response = generativeModel.generateContent(
          content {
            text(structuredPayload)
          }
        )
        response.text ?: throw IllegalStateException("Model returned an empty explanation response.")
      }

      MedicalExplanationParser.parseExplanationJsonSafe(
        jsonString = responseText,
        sourceExtraction = extractionResult,
        language = safeLanguage
      )
    } catch (e: TimeoutCancellationException) {
      Log.e(TAG, "Explanation generation timed out after ${AiModelConfig.INFERENCE_TIMEOUT_MS}ms")
      Result.failure(
        Exception("Explanation generation timed out. Please check your connection and retry.", e)
      )
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      // Never log medical payload contents; log only exception class for diagnostics
      Log.e(TAG, "Explanation generation failed: ${e.javaClass.simpleName}")
      Result.failure(e)
    }
  }

  internal fun buildStructuredPromptPayload(
    extractionResult: DocumentExtractionResult,
    language: String
  ): String {
    val root = JSONObject()
    root.put("targetLanguage", language)
    root.put("category", extractionResult.category.name)
    root.put("pageCount", extractionResult.pageCount)

    when (val report = extractionResult.report) {
      is UniversalMedicalReport.Laboratory -> {
        val lab = report.report
        lab.labFacilityName?.let { root.put("labFacilityName", it) }
        lab.collectionDate?.let { root.put("collectionDate", it) }
        lab.reportDate?.let { root.put("reportDate", it) }
        val itemsArr = JSONArray()
        lab.items.forEachIndexed { idx, item ->
          val itemObj = JSONObject()
          itemObj.put("itemIndex", idx)
          item.originalName?.let { itemObj.put("originalName", it) }
          item.originalValue?.let { itemObj.put("originalValue", it) }
          item.originalUnit?.let { itemObj.put("originalUnit", it) }
          item.referenceRange?.let { itemObj.put("referenceRange", it) }
          item.laboratoryFlag?.let { itemObj.put("laboratoryFlag", it) }
          itemObj.put("findingStatus", item.findingStatus.name)
          item.provenance.pageNumber?.let { itemObj.put("pageNumber", it) }
          itemsArr.put(itemObj)
        }
        root.put("items", itemsArr)
      }

      is UniversalMedicalReport.Radiology -> {
        val rad = report.report
        rad.modality?.let { root.put("modality", it) }
        rad.anatomicalRegion?.let { root.put("anatomicalRegion", it) }
        rad.clinicalIndication?.let { root.put("clinicalIndication", it) }
        rad.techniqueDescription?.let { root.put("techniqueDescription", it) }
        val findingsArr = JSONArray()
        rad.anatomicalFindings.forEachIndexed { idx, finding ->
          val fObj = JSONObject()
          fObj.put("findingIndex", idx)
          finding.anatomicalSite?.let { fObj.put("anatomicalSite", it) }
          finding.observationText?.let { fObj.put("observationText", it) }
          fObj.put("findingStatus", finding.findingStatus.name)
          finding.provenance.pageNumber?.let { fObj.put("pageNumber", it) }
          findingsArr.put(fObj)
        }
        root.put("findings", findingsArr)

        val impressionsArr = JSONArray()
        rad.formalImpressions.forEachIndexed { idx, imp ->
          val iObj = JSONObject()
          iObj.put("impressionIndex", idx)
          imp.impressionText?.let { iObj.put("impressionText", it) }
          iObj.put("findingStatus", imp.findingStatus.name)
          imp.provenance.pageNumber?.let { iObj.put("pageNumber", it) }
          impressionsArr.put(iObj)
        }
        root.put("impressions", impressionsArr)
      }

      is UniversalMedicalReport.Cardiology -> {
        val cardio = report.report
        cardio.modality?.let { root.put("modality", it) }
        root.put("findingStatus", cardio.findingStatus.name)
        val metricsArr = JSONArray()
        listOf(
          "heartRateBpm" to cardio.heartRateBpm,
          "rhythmInterpretation" to cardio.rhythmInterpretation,
          "prIntervalMs" to cardio.prIntervalMs,
          "qrsDurationMs" to cardio.qrsDurationMs,
          "qtcIntervalMs" to cardio.qtcIntervalMs,
          "ejectionFractionPercent" to cardio.ejectionFractionPercent,
          "wallMotionFindings" to cardio.wallMotionFindings,
          "formalConclusion" to cardio.formalConclusion
        ).forEach { (key, value) ->
          if (!value.isNullOrBlank()) {
            metricsArr.put(
              JSONObject()
                .put("fieldKey", key)
                .put("originalValue", value)
            )
          }
        }
        root.put("metrics", metricsArr)
      }

      is UniversalMedicalReport.Specialised -> {
        val spec = report.report
        spec.reportSubtype?.let { root.put("reportSubtype", it) }
        root.put("findingStatus", spec.findingStatus.name)
        val sectionsArr = JSONArray()
        listOf(
          "specimenSource" to spec.specimenSource,
          "clinicalHistory" to spec.clinicalHistory,
          "grossDescription" to spec.grossDescription,
          "microscopicDescription" to spec.microscopicDescription,
          "gradingOrStaging" to spec.gradingOrStaging,
          "diagnosticConclusion" to spec.diagnosticConclusion
        ).forEach { (key, value) ->
          if (!value.isNullOrBlank()) {
            sectionsArr.put(
              JSONObject()
                .put("fieldKey", key)
                .put("originalText", value)
            )
          }
        }
        root.put("sections", sectionsArr)
      }

      is UniversalMedicalReport.Clinical -> {
        val clinical = report.report
        clinical.documentSubtype?.let { root.put("documentSubtype", it) }
        clinical.facilityOrDoctorName?.let { root.put("facilityOrDoctorName", it) }
        clinical.followUpDate?.let { root.put("followUpDate", it) }

        fun putFactArray(jsonKey: String, indexKey: String, list: List<com.example.model.ExtractedClinicalFact>) {
          val arr = JSONArray()
          list.forEachIndexed { idx, fact ->
            arr.put(
              JSONObject()
                .put(indexKey, idx)
                .put("text", fact.text)
            )
          }
          root.put(jsonKey, arr)
        }

        putFactArray("diagnoses", "diagnosisIndex", clinical.primaryDiagnoses)
        putFactArray("procedures", "procedureIndex", clinical.surgicalProcedures)
        putFactArray("allergies", "allergyIndex", clinical.allergiesRecorded)
        putFactArray("dischargeInstructions", "instructionIndex", clinical.dischargeInstructions)

        val medsArr = JSONArray()
        clinical.prescribedMedications.forEachIndexed { idx, med ->
          val mObj = JSONObject().put("medicationIndex", idx)
          med.drugName?.let { mObj.put("drugName", it) }
          med.dosage?.let { mObj.put("dosage", it) }
          med.frequency?.let { mObj.put("frequency", it) }
          med.duration?.let { mObj.put("duration", it) }
          med.instructions?.let { mObj.put("instructions", it) }
          med.provenance.sourceSnippet?.let { mObj.put("sourceSnippet", it) }
          medsArr.put(mObj)
        }
        root.put("medications", medsArr)
      }

      is UniversalMedicalReport.Unidentified -> Unit
    }

    return "Generate a category-specific plain-language medical explanation strictly for the extracted report JSON below.\n$root"
  }
}
