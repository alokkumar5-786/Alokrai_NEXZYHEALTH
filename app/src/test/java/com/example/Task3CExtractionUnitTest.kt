package com.example

import com.example.domain.engine.AiModelConfig
import com.example.domain.engine.MedicalDocumentParser
import com.example.model.FindingStatus
import com.example.model.MedicalDocumentCategory
import com.example.model.ProvenanceAvailability
import com.example.model.UniversalMedicalReport
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pure JVM Unit Tests for Day 3 Task 3C: Universal Medical Document Reading and Information Extraction.
 * Tests pure parsing logic via [MedicalDocumentParser] and [AiModelConfig] without Android framework objects.
 */
class Task3CExtractionUnitTest {

  @Test
  fun verifyAiModelConfigConstantsAndLimits() {
    assertEquals("gemini-3.8-flash", AiModelConfig.extractionModel)
    assertEquals("gemini-3.5-flash-lite", AiModelConfig.FAST_EXTRACTION_MODEL)
    assertEquals(14L * 1024L * 1024L, AiModelConfig.MAX_RAW_PAYLOAD_BYTES)
    assertEquals(20L * 1024L * 1024L, AiModelConfig.FIREBASE_AI_INLINE_LIMIT_BYTES)

    val raw10Mb = 10L * 1024L * 1024L
    val base64For10Mb = AiModelConfig.calculateBase64PayloadSize(raw10Mb)
    assertTrue(base64For10Mb > raw10Mb)
    assertTrue(AiModelConfig.isPayloadWithinSafeLimits(raw10Mb))

    // 14 MB unencoded: ~18.66 MB Base64, safe (< 20 MB)
    assertTrue(AiModelConfig.isPayloadWithinSafeLimits(14L * 1024L * 1024L))

    // 16 MB unencoded: exceeds 14 MB raw ceiling and 20 MB Base64 ceiling
    assertFalse(AiModelConfig.isPayloadWithinSafeLimits(16L * 1024L * 1024L))
  }

  @Test
  fun verifyLaboratoryReportJsonParsingAndProvenance() {
    val sampleLabJson = """
    {
      "category": "LABORATORY",
      "report": {
        "labFacilityName": "Apex Diagnostic Centre",
        "collectionDate": "2026-10-14",
        "reportDate": "2026-10-15",
        "items": [
          {
            "originalName": "Hemoglobin (Hb)",
            "originalValue": "9.2",
            "originalUnit": "g/dL",
            "referenceRange": "12.0 - 15.5",
            "laboratoryFlag": "L",
            "findingStatus": "ABNORMAL",
            "provenance": {
              "pageNumber": 1,
              "sourceSnippet": "Hemoglobin (Hb) 9.2 g/dL (12.0-15.5) L",
              "estimatedConfidence": 0.98
            }
          },
          {
            "originalName": "Total Leucocyte Count (TLC)",
            "originalValue": "7800",
            "originalUnit": "cells/mcL",
            "referenceRange": "4000 - 11000",
            "laboratoryFlag": null,
            "findingStatus": "NORMAL",
            "provenance": {
              "pageNumber": 1,
              "sourceSnippet": "Total Leucocyte Count (TLC) 7800 cells/mcL",
              "estimatedConfidence": 0.99
            }
          }
        ]
      }
    }
    """.trimIndent()

    val result = MedicalDocumentParser.parseExtractionJson(sampleLabJson, pageCount = 1, isMultiPage = false)
    assertEquals(MedicalDocumentCategory.LABORATORY, result.category)
    assertEquals(1, result.pageCount)
    assertFalse(result.isMultiPageBatch)

    val labReport = (result.report as UniversalMedicalReport.Laboratory).report
    assertEquals("Apex Diagnostic Centre", labReport.labFacilityName)
    assertEquals("2026-10-14", labReport.collectionDate)
    assertEquals(2, labReport.items.size)

    val hb = labReport.items[0]
    assertEquals("Hemoglobin (Hb)", hb.originalName)
    assertEquals("9.2", hb.originalValue)
    assertEquals("g/dL", hb.originalUnit)
    assertEquals("12.0 - 15.5", hb.referenceRange)
    assertEquals("L", hb.laboratoryFlag)
    assertEquals(FindingStatus.ABNORMAL, hb.findingStatus)
    assertEquals(1, hb.provenance.pageNumber)
    assertEquals("Hemoglobin (Hb) 9.2 g/dL (12.0-15.5) L", hb.provenance.sourceSnippet)
    assertEquals(0.98f, hb.provenance.estimatedExtractionConfidence!!, 0.01f)
    assertEquals(ProvenanceAvailability.AVAILABLE, hb.provenance.availability)

    val tlc = labReport.items[1]
    assertEquals("Total Leucocyte Count (TLC)", tlc.originalName)
    assertEquals("7800", tlc.originalValue)
    assertNull(tlc.laboratoryFlag)
    assertEquals(FindingStatus.NORMAL, tlc.findingStatus)
  }

  @Test
  fun verifyRadiologyReportJsonParsingAndImpressions() {
    val sampleRadiologyJson = """
    {
      "category": "RADIOLOGY",
      "report": {
        "modality": "Chest X-Ray PA View",
        "anatomicalRegion": "Thorax / Chest",
        "clinicalIndication": "Persistent cough and fever",
        "techniqueDescription": "Standard posteroanterior projection",
        "anatomicalFindings": [
          {
            "anatomicalSite": "Lungs",
            "observationText": "Bilateral lung fields clear. No focal consolidation or effusion.",
            "findingStatus": "NEGATIVE",
            "provenance": {
              "pageNumber": 1,
              "sourceSnippet": "Bilateral lung fields clear",
              "estimatedConfidence": 0.96
            }
          }
        ],
        "formalImpressions": [
          {
            "impressionText": "Normal chest radiograph with no acute cardiopulmonary process.",
            "findingStatus": "NEGATIVE",
            "provenance": {
              "pageNumber": 1,
              "sourceSnippet": "IMPRESSION: Normal chest radiograph",
              "estimatedConfidence": 0.97
            }
          }
        ]
      }
    }
    """.trimIndent()

    val result = MedicalDocumentParser.parseExtractionJson(sampleRadiologyJson, pageCount = 1, isMultiPage = false)
    assertEquals(MedicalDocumentCategory.RADIOLOGY, result.category)

    val radReport = (result.report as UniversalMedicalReport.Radiology).report
    assertEquals("Chest X-Ray PA View", radReport.modality)
    assertEquals("Thorax / Chest", radReport.anatomicalRegion)
    assertEquals(1, radReport.anatomicalFindings.size)
    assertEquals(FindingStatus.NEGATIVE, radReport.anatomicalFindings[0].findingStatus)
    assertEquals(1, radReport.formalImpressions.size)
    assertEquals("Normal chest radiograph with no acute cardiopulmonary process.", radReport.formalImpressions[0].impressionText)
    assertEquals(FindingStatus.NEGATIVE, radReport.formalImpressions[0].findingStatus)
  }

  @Test
  fun verifyCardiologyReportJsonParsing() {
    val sampleCardioJson = """
    {
      "category": "CARDIOLOGY",
      "report": {
        "modality": "12-Lead Electrocardiogram (ECG)",
        "heartRateBpm": "74",
        "rhythmInterpretation": "Normal Sinus Rhythm",
        "prIntervalMs": "160",
        "qrsDurationMs": "88",
        "qtcIntervalMs": "415",
        "formalConclusion": "Normal ECG without acute ST-T wave changes.",
        "findingStatus": "NORMAL",
        "provenance": {
          "pageNumber": 1,
          "sourceSnippet": "Vent. rate 74 BPM, Normal sinus rhythm",
          "estimatedConfidence": 0.94
        }
      }
    }
    """.trimIndent()

    val result = MedicalDocumentParser.parseExtractionJson(sampleCardioJson, pageCount = 1, isMultiPage = false)
    assertEquals(MedicalDocumentCategory.CARDIOLOGY, result.category)

    val cardioReport = (result.report as UniversalMedicalReport.Cardiology).report
    assertEquals("74", cardioReport.heartRateBpm)
    assertEquals("Normal Sinus Rhythm", cardioReport.rhythmInterpretation)
    assertEquals("160", cardioReport.prIntervalMs)
    assertEquals("88", cardioReport.qrsDurationMs)
    assertEquals("415", cardioReport.qtcIntervalMs)
    assertEquals(FindingStatus.NORMAL, cardioReport.findingStatus)
    assertEquals(1, cardioReport.provenance.pageNumber)
  }

  @Test
  fun verifySpecialisedReportJsonParsing() {
    val sampleSpecialisedJson = """
    {
      "category": "SPECIALISED",
      "report": {
        "reportSubtype": "Histopathology Biopsy Report",
        "specimenSource": "Skin punch biopsy, left forearm",
        "grossDescription": "Single punch biopsy measuring 4mm in diameter.",
        "microscopicDescription": "Benign intradermal nevus with no cytological atypia.",
        "diagnosticConclusion": "Benign Intradermal Nevus. Margins clear.",
        "findingStatus": "NEGATIVE",
        "provenance": {
          "pageNumber": 2,
          "sourceSnippet": "DIAGNOSIS: Benign Intradermal Nevus",
          "estimatedConfidence": 0.92
        }
      }
    }
    """.trimIndent()

    val result = MedicalDocumentParser.parseExtractionJson(sampleSpecialisedJson, pageCount = 2, isMultiPage = true)
    assertEquals(MedicalDocumentCategory.SPECIALISED, result.category)
    assertEquals(2, result.pageCount)
    assertTrue(result.isMultiPageBatch)

    val specReport = (result.report as UniversalMedicalReport.Specialised).report
    assertEquals("Histopathology Biopsy Report", specReport.reportSubtype)
    assertEquals("Skin punch biopsy, left forearm", specReport.specimenSource)
    assertEquals("Benign Intradermal Nevus. Margins clear.", specReport.diagnosticConclusion)
    assertEquals(FindingStatus.NEGATIVE, specReport.findingStatus)
    assertEquals(2, specReport.provenance.pageNumber)
  }

  @Test
  fun verifyClinicalDocumentJsonParsingWithMedications() {
    val sampleClinicalJson = """
    {
      "category": "CLINICAL",
      "report": {
        "documentSubtype": "Discharge Summary",
        "facilityOrDoctorName": "City General Hospital - Dr. S. Mehta",
        "primaryDiagnoses": ["Acute Gastroenteritis", "Mild Dehydration"],
        "prescribedMedications": [
          {
            "drugName": "Oral Rehydration Salts (ORS)",
            "dosage": "1 packet in 1L water",
            "frequency": "As needed",
            "duration": "3 days",
            "provenance": {
              "pageNumber": 1,
              "sourceSnippet": "ORS 1 pkt in 1L water PRN x 3d",
              "estimatedConfidence": 0.91
            }
          }
        ],
        "allergiesRecorded": ["Penicillin"],
        "dischargeInstructions": ["Maintain oral hydration", "Review if fever persists"],
        "followUpDate": "2026-11-01"
      }
    }
    """.trimIndent()

    val result = MedicalDocumentParser.parseExtractionJson(sampleClinicalJson, pageCount = 1, isMultiPage = false)
    assertEquals(MedicalDocumentCategory.CLINICAL, result.category)

    val clinicalDoc = (result.report as UniversalMedicalReport.Clinical).report
    assertEquals("Discharge Summary", clinicalDoc.documentSubtype)
    assertEquals(2, clinicalDoc.primaryDiagnoses.size)
    assertEquals("Acute Gastroenteritis", clinicalDoc.primaryDiagnoses[0].text)
    assertFalse(clinicalDoc.primaryDiagnoses[0].provenance.isAvailable)
    assertEquals(1, clinicalDoc.prescribedMedications.size)
    val med = clinicalDoc.prescribedMedications[0]
    assertEquals("Oral Rehydration Salts (ORS)", med.drugName)
    assertEquals("1 packet in 1L water", med.dosage)
    assertEquals(listOf("Penicillin"), clinicalDoc.allergiesRecorded.map { it.text })
    assertEquals("2026-11-01", clinicalDoc.followUpDate)
  }

  @Test
  fun verifyUncertainCategoryHandling() {
    val unclassifiedJson = """
    {
      "category": "UNKNOWN",
      "report": {
        "reason": "Document does not appear to be a medical report (utility bill).",
        "rawSnippet": "Electricity Bill - Account #12345"
      }
    }
    """.trimIndent()

    val result = MedicalDocumentParser.parseExtractionJson(unclassifiedJson, pageCount = 1, isMultiPage = false)
    assertEquals(MedicalDocumentCategory.UNCERTAIN, result.category)
    assertTrue(result.report is UniversalMedicalReport.Unidentified)
    val unidentified = result.report as UniversalMedicalReport.Unidentified
    assertTrue(unidentified.reason.contains("utility bill") || unidentified.reason.contains("Could not classify"))
  }

  @Test
  fun verifyFindingStatusMappingExhaustiveness() {
    val labJson = """
    {
      "category": "LABORATORY",
      "report": {
        "items": [
          {
            "originalName": "Normal Test",
            "originalValue": "5.0",
            "findingStatus": "NORMAL"
          },
          {
            "originalName": "Abnormal Test",
            "originalValue": "15.0",
            "findingStatus": "ABNORMAL"
          },
          {
            "originalName": "Qualitative Positive",
            "originalValue": "Detected",
            "findingStatus": "POSITIVE"
          },
          {
            "originalName": "Qualitative Negative",
            "originalValue": "Non-Reactive",
            "findingStatus": "NEGATIVE"
          },
          {
            "originalName": "Smudged Text",
            "originalValue": "---",
            "findingStatus": "UNCERTAIN_OR_UNREADABLE"
          },
          {
            "originalName": "Omitted Param",
            "originalValue": "N/A",
            "findingStatus": "NOT_MENTIONED"
          }
        ]
      }
    }
    """.trimIndent()

    val result = MedicalDocumentParser.parseExtractionJson(labJson, pageCount = 1, isMultiPage = false)
    val items = (result.report as UniversalMedicalReport.Laboratory).report.items
    assertEquals(6, items.size)
    assertEquals(FindingStatus.NORMAL, items[0].findingStatus)
    assertEquals(FindingStatus.ABNORMAL, items[1].findingStatus)
    assertEquals(FindingStatus.POSITIVE, items[2].findingStatus)
    assertEquals(FindingStatus.NEGATIVE, items[3].findingStatus)
    assertEquals(FindingStatus.UNCERTAIN_OR_UNREADABLE, items[4].findingStatus)
    assertEquals(FindingStatus.NOT_MENTIONED, items[5].findingStatus)
  }

  // --- Targeted Regression Tests for FindingStatus Parsing ---

  @Test
  fun verifyNonReactiveMapsToNegativeBeforePositiveOrReactive() {
    assertEquals(FindingStatus.NEGATIVE, MedicalDocumentParser.parseFindingStatus("NON-REACTIVE"))
    assertEquals(FindingStatus.NEGATIVE, MedicalDocumentParser.parseFindingStatus("Non-Reactive"))
    assertEquals(FindingStatus.NEGATIVE, MedicalDocumentParser.parseFindingStatus("  non-reactive  "))
    assertEquals(FindingStatus.NEGATIVE, MedicalDocumentParser.parseFindingStatus("NON REACTIVE"))
    assertEquals(FindingStatus.NEGATIVE, MedicalDocumentParser.parseFindingStatus("non_reactive"))
    assertEquals(FindingStatus.NEGATIVE, MedicalDocumentParser.parseFindingStatus("nonreactive"))
    assertEquals(FindingStatus.NEGATIVE, MedicalDocumentParser.parseFindingStatus("NOT REACTIVE"))
    assertEquals(FindingStatus.NEGATIVE, MedicalDocumentParser.parseFindingStatus("not_detected"))
  }

  @Test
  fun verifyReactiveAndPositiveMapToPositive() {
    assertEquals(FindingStatus.POSITIVE, MedicalDocumentParser.parseFindingStatus("REACTIVE"))
    assertEquals(FindingStatus.POSITIVE, MedicalDocumentParser.parseFindingStatus("  reactive\t"))
    assertEquals(FindingStatus.POSITIVE, MedicalDocumentParser.parseFindingStatus("Reactive"))
    assertEquals(FindingStatus.POSITIVE, MedicalDocumentParser.parseFindingStatus("POSITIVE"))
    assertEquals(FindingStatus.POSITIVE, MedicalDocumentParser.parseFindingStatus("  positive  "))
    assertEquals(FindingStatus.POSITIVE, MedicalDocumentParser.parseFindingStatus("Positive"))
  }

  @Test
  fun verifyNegativeMapsToNegative() {
    assertEquals(FindingStatus.NEGATIVE, MedicalDocumentParser.parseFindingStatus("NEGATIVE"))
    assertEquals(FindingStatus.NEGATIVE, MedicalDocumentParser.parseFindingStatus(" negative "))
    assertEquals(FindingStatus.NEGATIVE, MedicalDocumentParser.parseFindingStatus("Negative"))
  }

  @Test
  fun verifyNormalAndAbnormalRemainDistinctWithCaseAndWhitespace() {
    assertEquals(FindingStatus.NORMAL, MedicalDocumentParser.parseFindingStatus("NORMAL"))
    assertEquals(FindingStatus.NORMAL, MedicalDocumentParser.parseFindingStatus("  normal \n"))
    assertEquals(FindingStatus.NORMAL, MedicalDocumentParser.parseFindingStatus("Normal"))

    assertEquals(FindingStatus.ABNORMAL, MedicalDocumentParser.parseFindingStatus("ABNORMAL"))
    assertEquals(FindingStatus.ABNORMAL, MedicalDocumentParser.parseFindingStatus("  abnormal "))
    assertEquals(FindingStatus.ABNORMAL, MedicalDocumentParser.parseFindingStatus("Abnormal"))
  }

  @Test
  fun verifyMissingEmptyAndUnknownStatuses() {
    // Missing -> NOT_MENTIONED
    assertEquals(FindingStatus.NOT_MENTIONED, MedicalDocumentParser.parseFindingStatus(null))
    assertEquals(FindingStatus.NOT_MENTIONED, MedicalDocumentParser.parseFindingStatus("NOT_MENTIONED"))
    assertEquals(FindingStatus.NOT_MENTIONED, MedicalDocumentParser.parseFindingStatus("  not mentioned "))
    assertEquals(FindingStatus.NOT_MENTIONED, MedicalDocumentParser.parseFindingStatus("MISSING"))

    // Empty / whitespace -> UNCERTAIN_OR_UNREADABLE
    assertEquals(FindingStatus.UNCERTAIN_OR_UNREADABLE, MedicalDocumentParser.parseFindingStatus(""))
    assertEquals(FindingStatus.UNCERTAIN_OR_UNREADABLE, MedicalDocumentParser.parseFindingStatus("   \t\n  "))

    // Unknown / unrecognized -> UNCERTAIN_OR_UNREADABLE (never NORMAL)
    assertEquals(FindingStatus.UNCERTAIN_OR_UNREADABLE, MedicalDocumentParser.parseFindingStatus("UNKNOWN"))
    assertEquals(FindingStatus.UNCERTAIN_OR_UNREADABLE, MedicalDocumentParser.parseFindingStatus("BORDERLINE_XYZ"))
    assertEquals(FindingStatus.UNCERTAIN_OR_UNREADABLE, MedicalDocumentParser.parseFindingStatus("INDETERMINATE"))
    assertEquals(FindingStatus.UNCERTAIN_OR_UNREADABLE, MedicalDocumentParser.parseFindingStatus("NOT NORMAL"))
    assertEquals(FindingStatus.UNCERTAIN_OR_UNREADABLE, MedicalDocumentParser.parseFindingStatus("12.5"))
  }

  @Test
  fun verifyMissingStatusInJsonMapsToNotMentionedWithoutNumericInferenceAcrossCategories() {
    // Numeric lab value without findingStatus must NOT infer NORMAL or ABNORMAL
    val labMissingStatusJson = """
    {
      "category": "LABORATORY",
      "report": {
        "items": [
          {
            "originalName": "Hemoglobin (Hb)",
            "originalValue": "9.2",
            "originalUnit": "g/dL",
            "referenceRange": "12.0 - 15.5"
          },
          {
            "originalName": "HIV 1 & 2",
            "originalValue": "Non-Reactive",
            "findingStatus": ""
          },
          {
            "originalName": "HBsAg",
            "originalValue": "Non-Reactive",
            "findingStatus": "NON-REACTIVE"
          },
          {
            "originalName": "CRP",
            "originalValue": "24.0",
            "findingStatus": "INDETERMINATE"
          }
        ]
      }
    }
    """.trimIndent()

    val labResult = MedicalDocumentParser.parseExtractionJson(labMissingStatusJson, pageCount = 1, isMultiPage = false)
    val items = (labResult.report as UniversalMedicalReport.Laboratory).report.items
    assertEquals(4, items.size)
    // Missing status -> NOT_MENTIONED, numeric originalValue preserved verbatim
    assertEquals("9.2", items[0].originalValue)
    assertEquals(FindingStatus.NOT_MENTIONED, items[0].findingStatus)
    // Empty status -> UNCERTAIN_OR_UNREADABLE, originalValue preserved verbatim
    assertEquals("Non-Reactive", items[1].originalValue)
    assertEquals(FindingStatus.UNCERTAIN_OR_UNREADABLE, items[1].findingStatus)
    // NON-REACTIVE -> NEGATIVE
    assertEquals(FindingStatus.NEGATIVE, items[2].findingStatus)
    // Unknown status -> UNCERTAIN_OR_UNREADABLE
    assertEquals(FindingStatus.UNCERTAIN_OR_UNREADABLE, items[3].findingStatus)

    // Radiology, Cardiology, Specialised missing findingStatus -> NOT_MENTIONED (never NORMAL/NEGATIVE)
    val radMissingJson = """
    {
      "category": "RADIOLOGY",
      "report": {
        "anatomicalFindings": [{"anatomicalSite": "Lungs", "observationText": "Clear"}],
        "formalImpressions": [{"impressionText": "No acute disease"}]
      }
    }
    """.trimIndent()
    val rad = (MedicalDocumentParser.parseExtractionJson(radMissingJson, 1, false).report as UniversalMedicalReport.Radiology).report
    assertEquals(FindingStatus.NOT_MENTIONED, rad.anatomicalFindings[0].findingStatus)
    assertEquals(FindingStatus.NOT_MENTIONED, rad.formalImpressions[0].findingStatus)

    val cardioMissingJson = """{"category": "CARDIOLOGY", "report": {"heartRateBpm": "72", "formalConclusion": "Sinus rhythm"}}"""
    val cardio = (MedicalDocumentParser.parseExtractionJson(cardioMissingJson, 1, false).report as UniversalMedicalReport.Cardiology).report
    assertEquals(FindingStatus.NOT_MENTIONED, cardio.findingStatus)

    val specMissingJson = """{"category": "SPECIALISED", "report": {"diagnosticConclusion": "Benign"}}"""
    val spec = (MedicalDocumentParser.parseExtractionJson(specMissingJson, 1, false).report as UniversalMedicalReport.Specialised).report
    assertEquals(FindingStatus.NOT_MENTIONED, spec.findingStatus)
  }

  // --- Critical Fix 2: Medical Data Integrity & Provenance Regression Tests ---

  @Test
  fun verifyMissingLabOriginalValueRemainsNullNotFabricated() {
    val json = """
    {
      "category": "LABORATORY",
      "report": {
        "items": [
          {
            "originalName": "Serum Ferritin"
          },
          {
            "originalValue": null
          }
        ]
      }
    }
    """.trimIndent()

    val result = MedicalDocumentParser.parseExtractionJson(json, pageCount = 1, isMultiPage = false)
    val items = (result.report as UniversalMedicalReport.Laboratory).report.items
    assertEquals(2, items.size)

    assertEquals("Serum Ferritin", items[0].originalName)
    assertNull(items[0].originalValue)
    assertNull(items[0].originalUnit)
    assertNull(items[0].referenceRange)
    assertEquals(FindingStatus.NOT_MENTIONED, items[0].findingStatus)

    assertNull(items[1].originalName)
    assertNull(items[1].originalValue)
    assertEquals(FindingStatus.NOT_MENTIONED, items[1].findingStatus)
  }

  @Test
  fun verifyMissingCardiologyFormalConclusionRemainsNullNotFabricated() {
    val json = """
    {
      "category": "CARDIOLOGY",
      "report": {
        "heartRateBpm": "76"
      }
    }
    """.trimIndent()

    val result = MedicalDocumentParser.parseExtractionJson(json, pageCount = 1, isMultiPage = false)
    val cardio = (result.report as UniversalMedicalReport.Cardiology).report
    assertNull(cardio.modality)
    assertEquals("76", cardio.heartRateBpm)
    assertNull(cardio.formalConclusion)
    assertEquals(FindingStatus.NOT_MENTIONED, cardio.findingStatus)
  }

  @Test
  fun verifyMissingSpecialisedDiagnosticConclusionRemainsNullNotFabricated() {
    val json = """
    {
      "category": "SPECIALISED",
      "report": {
        "clinicalHistory": "Routine screening biopsy"
      }
    }
    """.trimIndent()

    val result = MedicalDocumentParser.parseExtractionJson(json, pageCount = 1, isMultiPage = false)
    val spec = (result.report as UniversalMedicalReport.Specialised).report
    assertNull(spec.reportSubtype)
    assertNull(spec.specimenSource)
    assertEquals("Routine screening biopsy", spec.clinicalHistory)
    assertNull(spec.diagnosticConclusion)
    assertEquals(FindingStatus.NOT_MENTIONED, spec.findingStatus)
  }

  @Test
  fun verifyMissingProvenanceMarkedUnavailableWithoutFabrication() {
    val labJson = """
    {
      "category": "LABORATORY",
      "report": {
        "items": [
          {
            "originalName": "Sodium",
            "originalValue": "140",
            "findingStatus": "NORMAL"
          }
        ]
      }
    }
    """.trimIndent()
    val labItem = (MedicalDocumentParser.parseExtractionJson(labJson, 1, false).report as UniversalMedicalReport.Laboratory).report.items[0]
    assertEquals(ProvenanceAvailability.UNAVAILABLE, labItem.provenance.availability)
    assertFalse(labItem.provenance.isAvailable)
    assertNull(labItem.provenance.pageNumber)
    assertNull(labItem.provenance.sourceSnippet)
    assertNull(labItem.provenance.estimatedExtractionConfidence)

    val radJson = """
    {
      "category": "RADIOLOGY",
      "report": {
        "anatomicalFindings": [{"observationText": "Mild degenerative changes"}],
        "formalImpressions": [{"impressionText": "No acute fracture"}]
      }
    }
    """.trimIndent()
    val rad = (MedicalDocumentParser.parseExtractionJson(radJson, 1, false).report as UniversalMedicalReport.Radiology).report
    assertNull(rad.modality)
    assertNull(rad.anatomicalRegion)
    assertNull(rad.anatomicalFindings[0].anatomicalSite)
    assertEquals(ProvenanceAvailability.UNAVAILABLE, rad.anatomicalFindings[0].provenance.availability)
    assertEquals(ProvenanceAvailability.UNAVAILABLE, rad.formalImpressions[0].provenance.availability)
  }

  @Test
  fun verifyMissingPageNumberRemainsNullWithoutDefaultingToOne() {
    val json = """
    {
      "category": "LABORATORY",
      "report": {
        "items": [
          {
            "originalName": "Potassium",
            "originalValue": "4.2",
            "findingStatus": "NORMAL",
            "provenance": {
              "sourceSnippet": "Potassium 4.2 mEq/L",
              "estimatedConfidence": 0.91
            }
          },
          {
            "originalName": "Chloride",
            "originalValue": "102",
            "provenance": {
              "pageNumber": 0,
              "sourceSnippet": "Chloride 102"
            }
          }
        ]
      }
    }
    """.trimIndent()

    val items = (MedicalDocumentParser.parseExtractionJson(json, 2, true).report as UniversalMedicalReport.Laboratory).report.items
    assertNull(items[0].provenance.pageNumber)
    assertEquals("Potassium 4.2 mEq/L", items[0].provenance.sourceSnippet)
    assertEquals(0.91f, items[0].provenance.estimatedExtractionConfidence!!, 0.001f)
    assertEquals(ProvenanceAvailability.PARTIALLY_AVAILABLE, items[0].provenance.availability)

    // Invalid pageNumber (<= 0) is also rejected rather than fabricated as 1
    assertNull(items[1].provenance.pageNumber)
    assertEquals(ProvenanceAvailability.PARTIALLY_AVAILABLE, items[1].provenance.availability)
  }

  @Test
  fun verifyMissingConfidenceRemainsNullWithoutDefaultingToNinetyFive() {
    val json = """
    {
      "category": "LABORATORY",
      "report": {
        "items": [
          {
            "originalName": "Fasting Glucose",
            "originalValue": "110",
            "findingStatus": "ABNORMAL",
            "provenance": {
              "pageNumber": 2,
              "sourceSnippet": "Fasting Glucose 110 mg/dL"
            }
          }
        ]
      }
    }
    """.trimIndent()

    val item = (MedicalDocumentParser.parseExtractionJson(json, 2, true).report as UniversalMedicalReport.Laboratory).report.items[0]
    assertEquals(2, item.provenance.pageNumber)
    assertEquals("Fasting Glucose 110 mg/dL", item.provenance.sourceSnippet)
    assertNull(item.provenance.estimatedExtractionConfidence)
    assertEquals(ProvenanceAvailability.PARTIALLY_AVAILABLE, item.provenance.availability)
  }

  @Test
  fun verifyExistingValidProvenanceRemainsUnchanged() {
    val json = """
    {
      "category": "LABORATORY",
      "report": {
        "items": [
          {
            "originalName": "Serum Creatinine",
            "originalValue": "1.1",
            "originalUnit": "mg/dL",
            "findingStatus": "NORMAL",
            "provenance": {
              "pageNumber": 3,
              "sourceSnippet": "Serum Creatinine   1.1 mg/dL",
              "estimatedConfidence": 0.87
            }
          }
        ]
      }
    }
    """.trimIndent()

    val item = (MedicalDocumentParser.parseExtractionJson(json, 3, true).report as UniversalMedicalReport.Laboratory).report.items[0]
    assertEquals(ProvenanceAvailability.AVAILABLE, item.provenance.availability)
    assertTrue(item.provenance.isAvailable)
    assertEquals(3, item.provenance.pageNumber)
    assertEquals("Serum Creatinine   1.1 mg/dL", item.provenance.sourceSnippet)
    assertEquals(0.87f, item.provenance.estimatedExtractionConfidence!!, 0.001f)
  }

  @Test
  fun verifyClinicalExtractedFieldsRetainIndividualProvenance() {
    val json = """
    {
      "category": "CLINICAL",
      "report": {
        "primaryDiagnoses": [
          {
            "text": "Type 2 Diabetes Mellitus",
            "provenance": {
              "pageNumber": 1,
              "sourceSnippet": "Dx: Type 2 Diabetes Mellitus",
              "estimatedConfidence": 0.94
            }
          }
        ],
        "surgicalProcedures": [
          {
            "text": "Laparoscopic Cholecystectomy",
            "provenance": {
              "pageNumber": 2,
              "sourceSnippet": "Procedure: Laparoscopic Cholecystectomy",
              "estimatedConfidence": 0.89
            }
          }
        ],
        "allergiesRecorded": [
          {
            "text": "Sulfa drugs",
            "provenance": {
              "pageNumber": 1,
              "sourceSnippet": "Allergies: Sulfa drugs",
              "estimatedConfidence": 0.96
            }
          }
        ],
        "dischargeInstructions": [
          {
            "text": "Follow low-sodium diet",
            "provenance": {
              "pageNumber": 3,
              "sourceSnippet": "Advice: Follow low-sodium diet",
              "estimatedConfidence": 0.92
            }
          }
        ],
        "prescribedMedications": [
          {
            "dosage": "500 mg"
          }
        ]
      }
    }
    """.trimIndent()

    val result = MedicalDocumentParser.parseExtractionJson(json, 3, true)
    val clinical = (result.report as UniversalMedicalReport.Clinical).report
    assertNull(clinical.documentSubtype)

    assertEquals(1, clinical.primaryDiagnoses.size)
    assertEquals("Type 2 Diabetes Mellitus", clinical.primaryDiagnoses[0].text)
    assertEquals(1, clinical.primaryDiagnoses[0].provenance.pageNumber)
    assertEquals("Dx: Type 2 Diabetes Mellitus", clinical.primaryDiagnoses[0].provenance.sourceSnippet)
    assertEquals(0.94f, clinical.primaryDiagnoses[0].provenance.estimatedExtractionConfidence!!, 0.001f)

    assertEquals(1, clinical.surgicalProcedures.size)
    assertEquals("Laparoscopic Cholecystectomy", clinical.surgicalProcedures[0].text)
    assertEquals(2, clinical.surgicalProcedures[0].provenance.pageNumber)
    assertEquals("Procedure: Laparoscopic Cholecystectomy", clinical.surgicalProcedures[0].provenance.sourceSnippet)

    assertEquals(1, clinical.allergiesRecorded.size)
    assertEquals("Sulfa drugs", clinical.allergiesRecorded[0].text)
    assertEquals(1, clinical.allergiesRecorded[0].provenance.pageNumber)

    assertEquals(1, clinical.dischargeInstructions.size)
    assertEquals("Follow low-sodium diet", clinical.dischargeInstructions[0].text)
    assertEquals(3, clinical.dischargeInstructions[0].provenance.pageNumber)
    assertEquals(0.92f, clinical.dischargeInstructions[0].provenance.estimatedExtractionConfidence!!, 0.001f)

    // Medication omitting drugName and provenance preserves null & unavailable provenance
    assertNull(clinical.prescribedMedications[0].drugName)
    assertEquals("500 mg", clinical.prescribedMedications[0].dosage)
    assertEquals(ProvenanceAvailability.UNAVAILABLE, clinical.prescribedMedications[0].provenance.availability)
  }

  @Test
  fun verifyNotPositiveMapsToNegative() {
    assertEquals(FindingStatus.NEGATIVE, MedicalDocumentParser.parseFindingStatus("NOT POSITIVE"))
    assertEquals(FindingStatus.NEGATIVE, MedicalDocumentParser.parseFindingStatus("not-positive"))
    assertEquals(FindingStatus.NEGATIVE, MedicalDocumentParser.parseFindingStatus("  Not Positive  "))

    val json = """
    {
      "category": "LABORATORY",
      "report": {
        "items": [
          {
            "originalName": "SARS-CoV-2 Antigen",
            "originalValue": "Not Positive",
            "findingStatus": "NOT POSITIVE"
          }
        ]
      }
    }
    """.trimIndent()
    val item = (MedicalDocumentParser.parseExtractionJson(json, 1, false).report as UniversalMedicalReport.Laboratory).report.items[0]
    assertEquals(FindingStatus.NEGATIVE, item.findingStatus)
    assertEquals("Not Positive", item.originalValue)
  }

  @Test
  fun verifyMalformedJsonProducesSafeExtractionFailureWithoutCrash() {
    val malformedPayloads = listOf(
      "{ invalid json",
      "",
      "   \n\t ",
      "not json at all",
      "[1, 2, 3]"
    )

    for (payload in malformedPayloads) {
      val result = MedicalDocumentParser.parseExtractionJsonSafe(payload, pageCount = 1, isMultiPage = false)
      assertTrue("Expected failure for payload: '$payload'", result.isFailure)
      assertNotNull(result.exceptionOrNull())
    }
  }
}
