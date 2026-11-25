package com.example.domain.engine

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import com.example.model.DocumentExtractionResult
import com.example.model.FindingStatus
import com.google.firebase.Firebase
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.content
import com.google.firebase.ai.type.generationConfig
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout

/**
 * Contract for universal medical document classification and structured data extraction.
 */
interface DocumentExtractor {
  suspend fun extractFromPdfBytes(
    pdfBytes: ByteArray,
    pageCount: Int
  ): Result<DocumentExtractionResult>

  suspend fun extractFromBitmaps(
    pageBitmaps: List<Bitmap>
  ): Result<DocumentExtractionResult>
}

/**
 * Universal Medical Document Reading & Information Extraction Engine.
 * Extracts structured medical data preserving original printed values, units, reference intervals,
 * flags, and field-level page provenance without clinical diagnostics or threshold evaluation.
 */
class MedicalDocumentExtractor(
  private val context: Context,
  private val modelName: String = AiModelConfig.extractionModel
) : DocumentExtractor {

  companion object {
    private const val TAG = "NexzyDocExtractor"

    // System prompt enforcing strict fact extraction, zero hallucination, and field-level provenance
    private const val EXTRACTION_SYSTEM_INSTRUCTION = """
You are NexzyHealth's Universal Medical Document Information Extractor.
Your task is to classify and extract information from the provided medical document image(s) or PDF bytes.

STRICT EXTRACTION RULES:
1. Classify the document into exactly one category:
   - "LABORATORY": blood/urine/stool/serology/microbiology lab reports
   - "RADIOLOGY": written X-Ray, CT, MRI, Ultrasound, PET/nuclear imaging consultation reports
   - "CARDIOLOGY": written ECG, Echocardiogram, Holter, TMT/stress test reports
   - "SPECIALISED": biopsy, histopathology, cytology, oncology, genetic/molecular reports
   - "CLINICAL": outpatient prescriptions, discharge summaries, consultation/OPD notes
   - "UNCERTAIN": non-medical documents, illegible files, or raw scan/ECG images lacking a written report (never invent findings from raw waveforms or radiology pixels).
2. Extract fields EXACTLY as printed on the document. Do not invent missing values, conclusions, anatomical labels, reference ranges, or units; set omitted fields to null.
3. If an analyte/parameter has a printed abnormal flag (e.g., 'H', 'L', 'HIGH', 'LOW', '*', 'CRITICAL'), preserve it in 'laboratoryFlag'. If none is printed, set it to null.
4. For every extracted item, finding, medication, or clinical fact (including entries in 'primaryDiagnoses', 'surgicalProcedures', 'allergiesRecorded', and 'dischargeInstructions' as {"text": ..., "provenance": ...}), provide:
   - 'pageNumber': Integer page where it was found (1-indexed), or null if unknown.
   - 'sourceSnippet': Verbatim text snippet from the document proving the extraction, or null if unavailable.
   - 'estimatedConfidence': AI-estimated extraction confidence Float between 0.0 and 1.0, or null if unavailable.
5. Distinguish finding status:
   - 'NORMAL': explicitly reported within normal reference range.
   - 'ABNORMAL': explicitly reported outside reference range or flagged H/L.
   - 'POSITIVE': qualitative positive (e.g., bacteria detected, fracture found, lesion present).
   - 'NEGATIVE': qualitative negative (e.g., "no acute fracture", "non-reactive", "negative").
   - 'NOT_MENTIONED': field was omitted or not tested.
   - 'UNCERTAIN_OR_UNREADABLE': text is smudged, cut off, or handwriting is illegible.
6. Return ONLY a valid JSON object matching {"category": "<CATEGORY>", "report": <CATEGORY_OBJECT>} with exact keys:
- Provenance shape (<PROVENANCE>): {"pageNumber": int|null, "sourceSnippet": string|null, "estimatedConfidence": float|null}
- LABORATORY report: {"labFacilityName": string|null, "collectionDate": string|null, "reportDate": string|null, "items": [{"originalName": string|null, "originalValue": string|null, "originalUnit": string|null, "referenceRange": string|null, "laboratoryFlag": string|null, "findingStatus": string, "provenance": <PROVENANCE>}]}
- RADIOLOGY report: {"modality": string|null, "anatomicalRegion": string|null, "clinicalIndication": string|null, "techniqueDescription": string|null, "anatomicalFindings": [{"anatomicalSite": string|null, "observationText": string|null, "findingStatus": string, "provenance": <PROVENANCE>}], "formalImpressions": [{"impressionText": string|null, "findingStatus": string, "provenance": <PROVENANCE>}]}
- CARDIOLOGY report: {"modality": string|null, "heartRateBpm": string|null, "rhythmInterpretation": string|null, "prIntervalMs": string|null, "qrsDurationMs": string|null, "qtcIntervalMs": string|null, "ejectionFractionPercent": string|null, "wallMotionFindings": string|null, "formalConclusion": string|null, "findingStatus": string, "provenance": <PROVENANCE>}
- SPECIALISED report: {"reportSubtype": string|null, "specimenSource": string|null, "clinicalHistory": string|null, "grossDescription": string|null, "microscopicDescription": string|null, "gradingOrStaging": string|null, "diagnosticConclusion": string|null, "findingStatus": string, "provenance": <PROVENANCE>}
- CLINICAL report: {"documentSubtype": string|null, "facilityOrDoctorName": string|null, "followUpDate": string|null, "primaryDiagnoses": [{"text": string, "provenance": <PROVENANCE>}], "surgicalProcedures": [{"text": string, "provenance": <PROVENANCE>}], "prescribedMedications": [{"drugName": string|null, "dosage": string|null, "frequency": string|null, "duration": string|null, "instructions": string|null, "provenance": <PROVENANCE>}], "allergiesRecorded": [{"text": string, "provenance": <PROVENANCE>}], "dischargeInstructions": [{"text": string, "provenance": <PROVENANCE>}]}
- UNCERTAIN report: {"reason": string, "rawSnippet": string|null}
"""
  }

  // Lazy initialization ensures Firebase.ai is evaluated on demand rather than at constructor time
  private val generativeModel by lazy {
    Firebase.ai.generativeModel(
      modelName = modelName,
      systemInstruction = content { text(EXTRACTION_SYSTEM_INSTRUCTION) },
      generationConfig = generationConfig {
        responseMimeType = "application/json"
      }
    )
  }

  /**
   * Extracts structured information from a digital PDF byte stream.
   * Pre-validates request payload size against the 20 MB Base64 inline limit.
   */
  override suspend fun extractFromPdfBytes(
    pdfBytes: ByteArray,
    pageCount: Int
  ): Result<DocumentExtractionResult> = withContext(Dispatchers.IO) {
    if (!AiModelConfig.isPayloadWithinSafeLimits(pdfBytes.size.toLong())) {
      val base64Mb = AiModelConfig.calculateBase64PayloadSize(pdfBytes.size.toLong()) / (1024 * 1024)
      return@withContext Result.failure(
        IllegalArgumentException(
          "Document payload exceeds the 20 MB AI request limit ($base64Mb MB Base64 encoded). Please select a document under 14 MB."
        )
      )
    }

    try {
      val extractionPrompt = "Extract all structured medical findings and classify this document. Document total pages: $pageCount."
      val responseText = withTimeout(AiModelConfig.INFERENCE_TIMEOUT_MS) {
        val response = generativeModel.generateContent(
          content {
            inlineData(pdfBytes, "application/pdf")
            text(extractionPrompt)
          }
        )
        response.text ?: throw IllegalStateException("Model returned an empty extraction payload.")
      }

      parseExtractionJsonSafe(responseText, pageCount, isMultiPage = pageCount > 1)
    } catch (e: TimeoutCancellationException) {
      Log.e(TAG, "Extraction timed out after ${AiModelConfig.INFERENCE_TIMEOUT_MS}ms")
      Result.failure(Exception("Document extraction timed out. Please check your connection and retry.", e))
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      Log.e(TAG, "Document extraction failed: ${e.javaClass.simpleName}")
      Result.failure(e)
    }
  }

  /**
   * Extracts structured information from one or more rendered page bitmaps (for scanned PDFs or photos).
   * Automatically batches pages to stay within payload limits and coroutine timeouts.
   */
  override suspend fun extractFromBitmaps(
    pageBitmaps: List<Bitmap>
  ): Result<DocumentExtractionResult> = withContext(Dispatchers.IO) {
    if (pageBitmaps.isEmpty()) {
      return@withContext Result.failure(IllegalArgumentException("No document pages provided for extraction."))
    }

    try {
      // Step 1: Validate aggregate estimated payload size
      var totalEstimatedBytes = 0L
      for (bmp in pageBitmaps) {
        totalEstimatedBytes += (bmp.width * bmp.height * 2L) // Approx JPEG/compressed stream size
      }

      if (!AiModelConfig.isPayloadWithinSafeLimits(totalEstimatedBytes)) {
        return@withContext Result.failure(
          IllegalArgumentException("Document images exceed safe AI payload limits. Please choose a smaller document.")
        )
      }

      val promptText = "Extract structured clinical findings from these ${pageBitmaps.size} document page(s). Classify and preserve all numbers, units, reference intervals, and page citations."

      val responseText = withTimeout(AiModelConfig.INFERENCE_TIMEOUT_MS) {
        val response = generativeModel.generateContent(
          content {
            text(promptText)
            pageBitmaps.forEachIndexed { index, bitmap ->
              text("--- Page ${index + 1} ---")
              image(bitmap)
            }
          }
        )
        response.text ?: throw IllegalStateException("Model returned an empty extraction payload.")
      }

      parseExtractionJsonSafe(responseText, pageBitmaps.size, isMultiPage = pageBitmaps.size > 1)
    } catch (e: TimeoutCancellationException) {
      Log.e(TAG, "Extraction timed out after ${AiModelConfig.INFERENCE_TIMEOUT_MS}ms")
      Result.failure(Exception("Document extraction timed out. Please check your connection and retry.", e))
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      Log.e(TAG, "Bitmap extraction failed: ${e.javaClass.simpleName}")
      Result.failure(e)
    }
  }

  /**
   * Safely deserializes JSON output into typed domain schemas via [MedicalDocumentParser],
   * returning [Result.failure] on malformed payloads instead of throwing.
   */
  fun parseExtractionJsonSafe(
    jsonString: String,
    pageCount: Int,
    isMultiPage: Boolean
  ): Result<DocumentExtractionResult> =
    MedicalDocumentParser.parseExtractionJsonSafe(jsonString, pageCount, isMultiPage)

  /**
   * Deserializes JSON output from the model into typed domain schemas via [MedicalDocumentParser].
   */
  fun parseExtractionJson(
    jsonString: String,
    pageCount: Int,
    isMultiPage: Boolean
  ): DocumentExtractionResult =
    MedicalDocumentParser.parseExtractionJson(jsonString, pageCount, isMultiPage)

  /**
   * Maps a source-reported finding status string to [FindingStatus] via [MedicalDocumentParser].
   */
  fun parseFindingStatus(statusStr: String?): FindingStatus =
    MedicalDocumentParser.parseFindingStatus(statusStr)
}
