package com.example.data.serialization

import com.example.model.AnatomicalFinding
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
import com.example.model.MedicalTermDefinition
import com.example.model.ProvenanceAvailability
import com.example.model.RadiologyImpression
import com.example.model.ReportExplanationResult
import com.example.model.UniversalMedicalReport
import org.json.JSONArray
import org.json.JSONObject

/**
 * Deterministic, reflection-free JSON serializer for [DocumentExtractionResult] and
 * [ReportExplanationResult] suitable for Firestore subdocuments and Room local cache.
 */
object ReportPayloadSerializer {
  private const val SCHEMA_VERSION = 1

  fun serializeExtraction(result: DocumentExtractionResult): String {
    val root = JSONObject()
    root.put("schemaVersion", SCHEMA_VERSION)
    root.put("category", result.category.name)
    root.put("pageCount", result.pageCount)
    root.put("isMultiPageBatch", result.isMultiPageBatch)
    root.put("extractionTimestamp", result.extractionTimestamp)

    val reportObj = JSONObject()
    when (val report = result.report) {
      is UniversalMedicalReport.Laboratory -> {
        reportObj.put("type", "LABORATORY")
        putNullableString(reportObj, "labFacilityName", report.report.labFacilityName)
        putNullableString(reportObj, "collectionDate", report.report.collectionDate)
        putNullableString(reportObj, "reportDate", report.report.reportDate)
        reportObj.put("items", JSONArray().apply {
          report.report.items.forEach { put(labItemToJson(it)) }
        })
      }

      is UniversalMedicalReport.Radiology -> {
        reportObj.put("type", "RADIOLOGY")
        putNullableString(reportObj, "modality", report.report.modality)
        putNullableString(reportObj, "anatomicalRegion", report.report.anatomicalRegion)
        putNullableString(reportObj, "clinicalIndication", report.report.clinicalIndication)
        putNullableString(reportObj, "techniqueDescription", report.report.techniqueDescription)
        reportObj.put("anatomicalFindings", JSONArray().apply {
          report.report.anatomicalFindings.forEach { put(anatomicalFindingToJson(it)) }
        })
        reportObj.put("formalImpressions", JSONArray().apply {
          report.report.formalImpressions.forEach { put(radiologyImpressionToJson(it)) }
        })
      }

      is UniversalMedicalReport.Cardiology -> {
        reportObj.put("type", "CARDIOLOGY")
        reportObj.put("data", cardiologyToJson(report.report))
      }

      is UniversalMedicalReport.Specialised -> {
        reportObj.put("type", "SPECIALISED")
        reportObj.put("data", specialisedToJson(report.report))
      }

      is UniversalMedicalReport.Clinical -> {
        reportObj.put("type", "CLINICAL")
        putNullableString(reportObj, "documentSubtype", report.report.documentSubtype)
        putNullableString(reportObj, "facilityOrDoctorName", report.report.facilityOrDoctorName)
        putNullableString(reportObj, "followUpDate", report.report.followUpDate)
        reportObj.put("primaryDiagnoses", clinicalFactListToJson(report.report.primaryDiagnoses))
        reportObj.put("surgicalProcedures", clinicalFactListToJson(report.report.surgicalProcedures))
        reportObj.put("prescribedMedications", JSONArray().apply {
          report.report.prescribedMedications.forEach { put(medicationToJson(it)) }
        })
        reportObj.put("allergiesRecorded", clinicalFactListToJson(report.report.allergiesRecorded))
        reportObj.put("dischargeInstructions", clinicalFactListToJson(report.report.dischargeInstructions))
      }

      is UniversalMedicalReport.Unidentified -> {
        reportObj.put("type", "UNIDENTIFIED")
        putNullableString(reportObj, "rawSnippet", report.rawSnippet)
        reportObj.put("reason", report.reason)
      }
    }

    root.put("report", reportObj)
    return root.toString()
  }

  fun deserializeExtraction(json: String): DocumentExtractionResult? {
    return try {
      val root = JSONObject(json)
      val category = MedicalDocumentCategory.entries.firstOrNull {
        it.name == root.optString("category")
      } ?: MedicalDocumentCategory.UNCERTAIN
      val pageCount = root.optInt("pageCount", 1)
      val isMultiPageBatch = root.optBoolean("isMultiPageBatch", false)
      val extractionTimestamp = root.optLong("extractionTimestamp", 0L)
      val reportObj = root.getJSONObject("report")

      val report: UniversalMedicalReport = when (reportObj.optString("type")) {
        "LABORATORY" -> {
          val itemsArr = reportObj.optJSONArray("items") ?: JSONArray()
          val items = (0 until itemsArr.length()).map { idx ->
            jsonToLabItem(itemsArr.getJSONObject(idx))
          }
          UniversalMedicalReport.Laboratory(
            ExtractedLaboratoryReport(
              labFacilityName = optNullableString(reportObj, "labFacilityName"),
              collectionDate = optNullableString(reportObj, "collectionDate"),
              reportDate = optNullableString(reportObj, "reportDate"),
              items = items
            )
          )
        }

        "RADIOLOGY" -> {
          val findingsArr = reportObj.optJSONArray("anatomicalFindings") ?: JSONArray()
          val impressionsArr = reportObj.optJSONArray("formalImpressions") ?: JSONArray()
          UniversalMedicalReport.Radiology(
            ExtractedRadiologyReport(
              modality = optNullableString(reportObj, "modality"),
              anatomicalRegion = optNullableString(reportObj, "anatomicalRegion"),
              clinicalIndication = optNullableString(reportObj, "clinicalIndication"),
              techniqueDescription = optNullableString(reportObj, "techniqueDescription"),
              anatomicalFindings = (0 until findingsArr.length()).map {
                jsonToAnatomicalFinding(findingsArr.getJSONObject(it))
              },
              formalImpressions = (0 until impressionsArr.length()).map {
                jsonToRadiologyImpression(impressionsArr.getJSONObject(it))
              }
            )
          )
        }

        "CARDIOLOGY" -> {
          UniversalMedicalReport.Cardiology(jsonToCardiology(reportObj.getJSONObject("data")))
        }

        "SPECIALISED" -> {
          UniversalMedicalReport.Specialised(jsonToSpecialised(reportObj.getJSONObject("data")))
        }

        "CLINICAL" -> {
          val medsArr = reportObj.optJSONArray("prescribedMedications") ?: JSONArray()
          UniversalMedicalReport.Clinical(
            ExtractedClinicalDocument(
              documentSubtype = optNullableString(reportObj, "documentSubtype"),
              facilityOrDoctorName = optNullableString(reportObj, "facilityOrDoctorName"),
              primaryDiagnoses = jsonToClinicalFactList(reportObj.optJSONArray("primaryDiagnoses")),
              surgicalProcedures = jsonToClinicalFactList(reportObj.optJSONArray("surgicalProcedures")),
              prescribedMedications = (0 until medsArr.length()).map {
                jsonToMedication(medsArr.getJSONObject(it))
              },
              allergiesRecorded = jsonToClinicalFactList(reportObj.optJSONArray("allergiesRecorded")),
              dischargeInstructions = jsonToClinicalFactList(reportObj.optJSONArray("dischargeInstructions")),
              followUpDate = optNullableString(reportObj, "followUpDate")
            )
          )
        }

        else -> {
          UniversalMedicalReport.Unidentified(
            rawSnippet = optNullableString(reportObj, "rawSnippet"),
            reason = reportObj.optString("reason", "Unidentified medical document")
          )
        }
      }

      DocumentExtractionResult(
        category = category,
        pageCount = pageCount,
        isMultiPageBatch = isMultiPageBatch,
        report = report,
        extractionTimestamp = extractionTimestamp
      )
    } catch (_: Exception) {
      null
    }
  }

  fun serializeExplanation(result: ReportExplanationResult): String {
    val root = JSONObject()
    root.put("schemaVersion", SCHEMA_VERSION)
    root.put("category", result.category.name)
    root.put("language", result.language)
    root.put("conciseSummary", result.conciseSummary)
    root.put("safetyDisclaimer", result.safetyDisclaimer)
    root.put("generatedTimestamp", result.generatedTimestamp)

    root.put("medicalTerms", JSONArray().apply {
      result.medicalTerms.forEach { term ->
        put(JSONObject().apply {
          put("term", term.term)
          put("simpleDefinition", term.simpleDefinition)
        })
      }
    })
    root.put("questionsForDoctor", JSONArray().apply {
      result.questionsForDoctor.forEach { put(it) }
    })
    root.put("uncertaintyNotes", JSONArray().apply {
      result.uncertaintyNotes.forEach { put(it) }
    })

    val catObj = JSONObject()
    when (val details = result.categoryExplanation) {
      is CategoryExplanation.Laboratory -> {
        catObj.put("type", "LABORATORY")
        putNullableString(catObj, "labFacilityName", details.labFacilityName)
        putNullableString(catObj, "collectionDate", details.collectionDate)
        putNullableString(catObj, "reportDate", details.reportDate)
        catObj.put("items", JSONArray().apply {
          details.items.forEach { item ->
            put(JSONObject().apply {
              put("sourceItem", labItemToJson(item.sourceItem))
              putNullableString(this, "whatItMeasures", item.whatItMeasures)
              putNullableString(this, "plainLanguageExplanation", item.plainLanguageExplanation)
              putNullableString(this, "uncertaintyNote", item.uncertaintyNote)
            })
          }
        })
      }

      is CategoryExplanation.Radiology -> {
        catObj.put("type", "RADIOLOGY")
        putNullableString(catObj, "modality", details.modality)
        putNullableString(catObj, "anatomicalRegion", details.anatomicalRegion)
        putNullableString(catObj, "clinicalIndication", details.clinicalIndication)
        putNullableString(catObj, "techniqueDescription", details.techniqueDescription)
        putNullableString(catObj, "studyOverviewExplanation", details.studyOverviewExplanation)
        catObj.put("findings", JSONArray().apply {
          details.findings.forEach { f ->
            put(JSONObject().apply {
              put("sourceFinding", anatomicalFindingToJson(f.sourceFinding))
              putNullableString(this, "plainLanguageExplanation", f.plainLanguageExplanation)
              putNullableString(this, "uncertaintyNote", f.uncertaintyNote)
            })
          }
        })
        catObj.put("impressions", JSONArray().apply {
          details.impressions.forEach { imp ->
            put(JSONObject().apply {
              put("sourceImpression", radiologyImpressionToJson(imp.sourceImpression))
              putNullableString(this, "plainLanguageExplanation", imp.plainLanguageExplanation)
              putNullableString(this, "uncertaintyNote", imp.uncertaintyNote)
            })
          }
        })
      }

      is CategoryExplanation.Cardiology -> {
        catObj.put("type", "CARDIOLOGY")
        catObj.put("sourceReport", cardiologyToJson(details.sourceReport))
        putNullableString(catObj, "modalityExplanation", details.modalityExplanation)
        putNullableString(catObj, "conclusionExplanation", details.conclusionExplanation)
        putNullableString(catObj, "uncertaintyNote", details.uncertaintyNote)
        catObj.put("metrics", JSONArray().apply {
          details.metrics.forEach { m ->
            put(JSONObject().apply {
              put("fieldKey", m.fieldKey)
              put("label", m.label)
              put("originalValue", m.originalValue)
              putNullableString(this, "plainLanguageExplanation", m.plainLanguageExplanation)
              put("provenance", provenanceToJson(m.provenance))
            })
          }
        })
      }

      is CategoryExplanation.Specialised -> {
        catObj.put("type", "SPECIALISED")
        catObj.put("sourceReport", specialisedToJson(details.sourceReport))
        putNullableString(catObj, "reportOverviewExplanation", details.reportOverviewExplanation)
        putNullableString(catObj, "conclusionExplanation", details.conclusionExplanation)
        putNullableString(catObj, "uncertaintyNote", details.uncertaintyNote)
        catObj.put("sections", JSONArray().apply {
          details.sections.forEach { s ->
            put(JSONObject().apply {
              put("fieldKey", s.fieldKey)
              put("sectionTitle", s.sectionTitle)
              put("originalText", s.originalText)
              putNullableString(this, "plainLanguageExplanation", s.plainLanguageExplanation)
              put("provenance", provenanceToJson(s.provenance))
            })
          }
        })
      }

      is CategoryExplanation.Clinical -> {
        catObj.put("type", "CLINICAL")
        putNullableString(catObj, "documentSubtype", details.documentSubtype)
        putNullableString(catObj, "facilityOrDoctorName", details.facilityOrDoctorName)
        putNullableString(catObj, "followUpDate", details.followUpDate)
        putNullableString(catObj, "documentOverviewExplanation", details.documentOverviewExplanation)
        catObj.put("diagnoses", explainedClinicalFactListToJson(details.diagnoses))
        catObj.put("procedures", explainedClinicalFactListToJson(details.procedures))
        catObj.put("medications", JSONArray().apply {
          details.medications.forEach { m ->
            put(JSONObject().apply {
              put("sourceMedication", medicationToJson(m.sourceMedication))
              putNullableString(this, "printedScheduleExplanation", m.printedScheduleExplanation)
              put("safetyNote", m.safetyNote)
            })
          }
        })
        catObj.put("allergies", explainedClinicalFactListToJson(details.allergies))
        catObj.put("dischargeInstructions", explainedClinicalFactListToJson(details.dischargeInstructions))
      }
    }

    root.put("categoryExplanation", catObj)
    return root.toString()
  }

  fun deserializeExplanation(json: String): ReportExplanationResult? {
    return try {
      val root = JSONObject(json)
      val category = MedicalDocumentCategory.entries.firstOrNull {
        it.name == root.optString("category")
      } ?: MedicalDocumentCategory.LABORATORY
      val language = root.optString("language", ExplanationConstants.DEFAULT_EXPLANATION_LANGUAGE)
      val conciseSummary = root.optString("conciseSummary", "")
      val safetyDisclaimer = root.optString(
        "safetyDisclaimer",
        ExplanationConstants.MEDICAL_EXPLANATION_DISCLAIMER
      )
      val generatedTimestamp = root.optLong("generatedTimestamp", 0L)

      val termsArr = root.optJSONArray("medicalTerms") ?: JSONArray()
      val terms = (0 until termsArr.length()).map { idx ->
        val obj = termsArr.getJSONObject(idx)
        MedicalTermDefinition(
          term = obj.optString("term"),
          simpleDefinition = obj.optString("simpleDefinition")
        )
      }

      val questionsArr = root.optJSONArray("questionsForDoctor") ?: JSONArray()
      val questions = (0 until questionsArr.length()).map { questionsArr.optString(it) }

      val uncertaintyArr = root.optJSONArray("uncertaintyNotes") ?: JSONArray()
      val uncertaintyNotes = (0 until uncertaintyArr.length()).map { uncertaintyArr.optString(it) }

      val catObj = root.getJSONObject("categoryExplanation")
      val categoryExplanation: CategoryExplanation = when (catObj.optString("type")) {
        "LABORATORY" -> {
          val itemsArr = catObj.optJSONArray("items") ?: JSONArray()
          CategoryExplanation.Laboratory(
            labFacilityName = optNullableString(catObj, "labFacilityName"),
            collectionDate = optNullableString(catObj, "collectionDate"),
            reportDate = optNullableString(catObj, "reportDate"),
            items = (0 until itemsArr.length()).map { idx ->
              val itemObj = itemsArr.getJSONObject(idx)
              ExplainedLabItem(
                sourceItem = jsonToLabItem(itemObj.getJSONObject("sourceItem")),
                whatItMeasures = optNullableString(itemObj, "whatItMeasures"),
                plainLanguageExplanation = optNullableString(itemObj, "plainLanguageExplanation"),
                uncertaintyNote = optNullableString(itemObj, "uncertaintyNote")
              )
            }
          )
        }

        "RADIOLOGY" -> {
          val findingsArr = catObj.optJSONArray("findings") ?: JSONArray()
          val impressionsArr = catObj.optJSONArray("impressions") ?: JSONArray()
          CategoryExplanation.Radiology(
            modality = optNullableString(catObj, "modality"),
            anatomicalRegion = optNullableString(catObj, "anatomicalRegion"),
            clinicalIndication = optNullableString(catObj, "clinicalIndication"),
            techniqueDescription = optNullableString(catObj, "techniqueDescription"),
            studyOverviewExplanation = optNullableString(catObj, "studyOverviewExplanation"),
            findings = (0 until findingsArr.length()).map { idx ->
              val fObj = findingsArr.getJSONObject(idx)
              ExplainedRadiologyFinding(
                sourceFinding = jsonToAnatomicalFinding(fObj.getJSONObject("sourceFinding")),
                plainLanguageExplanation = optNullableString(fObj, "plainLanguageExplanation"),
                uncertaintyNote = optNullableString(fObj, "uncertaintyNote")
              )
            },
            impressions = (0 until impressionsArr.length()).map { idx ->
              val iObj = impressionsArr.getJSONObject(idx)
              ExplainedRadiologyImpression(
                sourceImpression = jsonToRadiologyImpression(iObj.getJSONObject("sourceImpression")),
                plainLanguageExplanation = optNullableString(iObj, "plainLanguageExplanation"),
                uncertaintyNote = optNullableString(iObj, "uncertaintyNote")
              )
            }
          )
        }

        "CARDIOLOGY" -> {
          val metricsArr = catObj.optJSONArray("metrics") ?: JSONArray()
          CategoryExplanation.Cardiology(
            sourceReport = jsonToCardiology(catObj.getJSONObject("sourceReport")),
            modalityExplanation = optNullableString(catObj, "modalityExplanation"),
            metrics = (0 until metricsArr.length()).map { idx ->
              val mObj = metricsArr.getJSONObject(idx)
              ExplainedCardiologyMetric(
                fieldKey = mObj.optString("fieldKey"),
                label = mObj.optString("label"),
                originalValue = mObj.optString("originalValue"),
                plainLanguageExplanation = optNullableString(mObj, "plainLanguageExplanation"),
                provenance = jsonToProvenance(mObj.optJSONObject("provenance"))
              )
            },
            conclusionExplanation = optNullableString(catObj, "conclusionExplanation"),
            uncertaintyNote = optNullableString(catObj, "uncertaintyNote")
          )
        }

        "SPECIALISED" -> {
          val secArr = catObj.optJSONArray("sections") ?: JSONArray()
          CategoryExplanation.Specialised(
            sourceReport = jsonToSpecialised(catObj.getJSONObject("sourceReport")),
            reportOverviewExplanation = optNullableString(catObj, "reportOverviewExplanation"),
            sections = (0 until secArr.length()).map { idx ->
              val sObj = secArr.getJSONObject(idx)
              ExplainedSpecialisedSection(
                fieldKey = sObj.optString("fieldKey"),
                sectionTitle = sObj.optString("sectionTitle"),
                originalText = sObj.optString("originalText"),
                plainLanguageExplanation = optNullableString(sObj, "plainLanguageExplanation"),
                provenance = jsonToProvenance(sObj.optJSONObject("provenance"))
              )
            },
            conclusionExplanation = optNullableString(catObj, "conclusionExplanation"),
            uncertaintyNote = optNullableString(catObj, "uncertaintyNote")
          )
        }

        else -> {
          val medsArr = catObj.optJSONArray("medications") ?: JSONArray()
          CategoryExplanation.Clinical(
            documentSubtype = optNullableString(catObj, "documentSubtype"),
            facilityOrDoctorName = optNullableString(catObj, "facilityOrDoctorName"),
            followUpDate = optNullableString(catObj, "followUpDate"),
            documentOverviewExplanation = optNullableString(catObj, "documentOverviewExplanation"),
            diagnoses = jsonToExplainedClinicalFactList(catObj.optJSONArray("diagnoses")),
            procedures = jsonToExplainedClinicalFactList(catObj.optJSONArray("procedures")),
            medications = (0 until medsArr.length()).map { idx ->
              val mObj = medsArr.getJSONObject(idx)
              ExplainedMedication(
                sourceMedication = jsonToMedication(mObj.getJSONObject("sourceMedication")),
                printedScheduleExplanation = optNullableString(mObj, "printedScheduleExplanation"),
                safetyNote = mObj.optString("safetyNote", ExplanationConstants.MEDICATION_SAFETY_NOTE)
              )
            },
            allergies = jsonToExplainedClinicalFactList(catObj.optJSONArray("allergies")),
            dischargeInstructions = jsonToExplainedClinicalFactList(catObj.optJSONArray("dischargeInstructions"))
          )
        }
      }

      ReportExplanationResult(
        category = category,
        language = language,
        conciseSummary = conciseSummary,
        categoryExplanation = categoryExplanation,
        medicalTerms = terms,
        questionsForDoctor = questions,
        uncertaintyNotes = uncertaintyNotes,
        safetyDisclaimer = safetyDisclaimer,
        generatedTimestamp = generatedTimestamp
      )
    } catch (_: Exception) {
      null
    }
  }

  private fun labItemToJson(item: ExtractedLabItem): JSONObject = JSONObject().apply {
    putNullableString(this, "originalName", item.originalName)
    putNullableString(this, "originalValue", item.originalValue)
    putNullableString(this, "originalUnit", item.originalUnit)
    putNullableString(this, "referenceRange", item.referenceRange)
    putNullableString(this, "laboratoryFlag", item.laboratoryFlag)
    put("findingStatus", item.findingStatus.name)
    put("provenance", provenanceToJson(item.provenance))
  }

  private fun jsonToLabItem(obj: JSONObject): ExtractedLabItem = ExtractedLabItem(
    originalName = optNullableString(obj, "originalName"),
    originalValue = optNullableString(obj, "originalValue"),
    originalUnit = optNullableString(obj, "originalUnit"),
    referenceRange = optNullableString(obj, "referenceRange"),
    laboratoryFlag = optNullableString(obj, "laboratoryFlag"),
    findingStatus = parseFindingStatus(obj.optString("findingStatus")),
    provenance = jsonToProvenance(obj.optJSONObject("provenance"))
  )

  private fun anatomicalFindingToJson(f: AnatomicalFinding): JSONObject = JSONObject().apply {
    putNullableString(this, "anatomicalSite", f.anatomicalSite)
    putNullableString(this, "observationText", f.observationText)
    put("findingStatus", f.findingStatus.name)
    put("provenance", provenanceToJson(f.provenance))
  }

  private fun jsonToAnatomicalFinding(obj: JSONObject): AnatomicalFinding = AnatomicalFinding(
    anatomicalSite = optNullableString(obj, "anatomicalSite"),
    observationText = optNullableString(obj, "observationText"),
    findingStatus = parseFindingStatus(obj.optString("findingStatus")),
    provenance = jsonToProvenance(obj.optJSONObject("provenance"))
  )

  private fun radiologyImpressionToJson(imp: RadiologyImpression): JSONObject = JSONObject().apply {
    putNullableString(this, "impressionText", imp.impressionText)
    put("findingStatus", imp.findingStatus.name)
    put("provenance", provenanceToJson(imp.provenance))
  }

  private fun jsonToRadiologyImpression(obj: JSONObject): RadiologyImpression = RadiologyImpression(
    impressionText = optNullableString(obj, "impressionText"),
    findingStatus = parseFindingStatus(obj.optString("findingStatus")),
    provenance = jsonToProvenance(obj.optJSONObject("provenance"))
  )

  private fun cardiologyToJson(report: ExtractedCardiologyReport): JSONObject = JSONObject().apply {
    putNullableString(this, "modality", report.modality)
    putNullableString(this, "heartRateBpm", report.heartRateBpm)
    putNullableString(this, "rhythmInterpretation", report.rhythmInterpretation)
    putNullableString(this, "prIntervalMs", report.prIntervalMs)
    putNullableString(this, "qrsDurationMs", report.qrsDurationMs)
    putNullableString(this, "qtcIntervalMs", report.qtcIntervalMs)
    putNullableString(this, "ejectionFractionPercent", report.ejectionFractionPercent)
    putNullableString(this, "wallMotionFindings", report.wallMotionFindings)
    putNullableString(this, "formalConclusion", report.formalConclusion)
    put("findingStatus", report.findingStatus.name)
    put("provenance", provenanceToJson(report.provenance))
    put("fieldProvenance", fieldProvenanceMapToJson(report.fieldProvenance))
  }

  private fun jsonToCardiology(obj: JSONObject): ExtractedCardiologyReport = ExtractedCardiologyReport(
    modality = optNullableString(obj, "modality"),
    heartRateBpm = optNullableString(obj, "heartRateBpm"),
    rhythmInterpretation = optNullableString(obj, "rhythmInterpretation"),
    prIntervalMs = optNullableString(obj, "prIntervalMs"),
    qrsDurationMs = optNullableString(obj, "qrsDurationMs"),
    qtcIntervalMs = optNullableString(obj, "qtcIntervalMs"),
    ejectionFractionPercent = optNullableString(obj, "ejectionFractionPercent"),
    wallMotionFindings = optNullableString(obj, "wallMotionFindings"),
    formalConclusion = optNullableString(obj, "formalConclusion"),
    findingStatus = parseFindingStatus(obj.optString("findingStatus")),
    provenance = jsonToProvenance(obj.optJSONObject("provenance")),
    fieldProvenance = jsonToFieldProvenanceMap(obj.optJSONObject("fieldProvenance"))
  )

  private fun specialisedToJson(report: ExtractedSpecialisedReport): JSONObject = JSONObject().apply {
    putNullableString(this, "reportSubtype", report.reportSubtype)
    putNullableString(this, "specimenSource", report.specimenSource)
    putNullableString(this, "clinicalHistory", report.clinicalHistory)
    putNullableString(this, "grossDescription", report.grossDescription)
    putNullableString(this, "microscopicDescription", report.microscopicDescription)
    putNullableString(this, "gradingOrStaging", report.gradingOrStaging)
    putNullableString(this, "diagnosticConclusion", report.diagnosticConclusion)
    put("findingStatus", report.findingStatus.name)
    put("provenance", provenanceToJson(report.provenance))
    put("fieldProvenance", fieldProvenanceMapToJson(report.fieldProvenance))
  }

  private fun jsonToSpecialised(obj: JSONObject): ExtractedSpecialisedReport = ExtractedSpecialisedReport(
    reportSubtype = optNullableString(obj, "reportSubtype"),
    specimenSource = optNullableString(obj, "specimenSource"),
    clinicalHistory = optNullableString(obj, "clinicalHistory"),
    grossDescription = optNullableString(obj, "grossDescription"),
    microscopicDescription = optNullableString(obj, "microscopicDescription"),
    gradingOrStaging = optNullableString(obj, "gradingOrStaging"),
    diagnosticConclusion = optNullableString(obj, "diagnosticConclusion"),
    findingStatus = parseFindingStatus(obj.optString("findingStatus")),
    provenance = jsonToProvenance(obj.optJSONObject("provenance")),
    fieldProvenance = jsonToFieldProvenanceMap(obj.optJSONObject("fieldProvenance"))
  )

  private fun clinicalFactListToJson(facts: List<ExtractedClinicalFact>): JSONArray = JSONArray().apply {
    facts.forEach { fact ->
      put(JSONObject().apply {
        put("text", fact.text)
        put("provenance", provenanceToJson(fact.provenance))
      })
    }
  }

  private fun jsonToClinicalFactList(arr: JSONArray?): List<ExtractedClinicalFact> {
    if (arr == null) return emptyList()
    return (0 until arr.length()).map { idx ->
      val obj = arr.getJSONObject(idx)
      ExtractedClinicalFact(
        text = obj.optString("text"),
        provenance = jsonToProvenance(obj.optJSONObject("provenance"))
      )
    }
  }

  private fun explainedClinicalFactListToJson(facts: List<ExplainedClinicalFact>): JSONArray = JSONArray().apply {
    facts.forEach { fact ->
      put(JSONObject().apply {
        put("sourceFact", JSONObject().apply {
          put("text", fact.sourceFact.text)
          put("provenance", provenanceToJson(fact.sourceFact.provenance))
        })
        putNullableString(this, "plainLanguageExplanation", fact.plainLanguageExplanation)
      })
    }
  }

  private fun jsonToExplainedClinicalFactList(arr: JSONArray?): List<ExplainedClinicalFact> {
    if (arr == null) return emptyList()
    return (0 until arr.length()).map { idx ->
      val obj = arr.getJSONObject(idx)
      val srcObj = obj.getJSONObject("sourceFact")
      ExplainedClinicalFact(
        sourceFact = ExtractedClinicalFact(
          text = srcObj.optString("text"),
          provenance = jsonToProvenance(srcObj.optJSONObject("provenance"))
        ),
        plainLanguageExplanation = optNullableString(obj, "plainLanguageExplanation")
      )
    }
  }

  private fun medicationToJson(med: ExtractedMedication): JSONObject = JSONObject().apply {
    putNullableString(this, "drugName", med.drugName)
    putNullableString(this, "dosage", med.dosage)
    putNullableString(this, "frequency", med.frequency)
    putNullableString(this, "duration", med.duration)
    putNullableString(this, "instructions", med.instructions)
    put("provenance", provenanceToJson(med.provenance))
  }

  private fun jsonToMedication(obj: JSONObject): ExtractedMedication = ExtractedMedication(
    drugName = optNullableString(obj, "drugName"),
    dosage = optNullableString(obj, "dosage"),
    frequency = optNullableString(obj, "frequency"),
    duration = optNullableString(obj, "duration"),
    instructions = optNullableString(obj, "instructions"),
    provenance = jsonToProvenance(obj.optJSONObject("provenance"))
  )

  private fun provenanceToJson(p: FieldProvenance): JSONObject = JSONObject().apply {
    if (p.pageNumber != null) put("pageNumber", p.pageNumber) else put("pageNumber", JSONObject.NULL)
    putNullableString(this, "sourceSnippet", p.sourceSnippet)
    if (p.estimatedExtractionConfidence != null) {
      put("estimatedExtractionConfidence", p.estimatedExtractionConfidence.toDouble())
    } else {
      put("estimatedExtractionConfidence", JSONObject.NULL)
    }
    put("availability", p.availability.name)
  }

  private fun jsonToProvenance(obj: JSONObject?): FieldProvenance {
    if (obj == null) return FieldProvenance.unavailable()
    val pageNumber = if (obj.isNull("pageNumber")) null else obj.optInt("pageNumber")
    val sourceSnippet = optNullableString(obj, "sourceSnippet")
    val confidence = if (obj.isNull("estimatedExtractionConfidence")) {
      null
    } else {
      obj.optDouble("estimatedExtractionConfidence").toFloat()
    }
    val availability = ProvenanceAvailability.entries.firstOrNull {
      it.name == obj.optString("availability")
    } ?: ProvenanceAvailability.UNAVAILABLE
    return FieldProvenance(
      pageNumber = pageNumber,
      sourceSnippet = sourceSnippet,
      estimatedExtractionConfidence = confidence,
      availability = availability
    )
  }

  private fun fieldProvenanceMapToJson(map: Map<String, FieldProvenance>): JSONObject = JSONObject().apply {
    map.forEach { (k, v) -> put(k, provenanceToJson(v)) }
  }

  private fun jsonToFieldProvenanceMap(obj: JSONObject?): Map<String, FieldProvenance> {
    if (obj == null) return emptyMap()
    val out = linkedMapOf<String, FieldProvenance>()
    val keys = obj.keys()
    while (keys.hasNext()) {
      val key = keys.next()
      out[key] = jsonToProvenance(obj.optJSONObject(key))
    }
    return out
  }

  private fun parseFindingStatus(name: String?): FindingStatus {
    return FindingStatus.entries.firstOrNull { it.name == name } ?: FindingStatus.NOT_MENTIONED
  }

  private fun putNullableString(obj: JSONObject, key: String, value: String?) {
    if (value == null) obj.put(key, JSONObject.NULL) else obj.put(key, value)
  }

  private fun optNullableString(obj: JSONObject, key: String): String? {
    if (!obj.has(key) || obj.isNull(key)) return null
    return obj.optString(key)
  }
}
