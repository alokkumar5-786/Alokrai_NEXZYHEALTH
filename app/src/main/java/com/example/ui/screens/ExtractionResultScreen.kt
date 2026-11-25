package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.FactCheck
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.AnatomicalFinding
import com.example.model.DocumentExtractionResult
import com.example.model.ExtractedCardiologyReport
import com.example.model.ExtractedClinicalDocument
import com.example.model.ExtractedClinicalFact
import com.example.model.ExtractedLabItem
import com.example.model.ExtractedMedication
import com.example.model.ExtractedRadiologyReport
import com.example.model.ExtractedSpecialisedReport
import com.example.model.FieldProvenance
import com.example.model.FindingStatus
import com.example.model.PersistedMedicalReport
import com.example.model.ProvenanceAvailability
import com.example.model.RadiologyImpression
import com.example.model.ReportPersistenceSyncStatus
import com.example.model.ReportSafetySummary
import com.example.model.SafetyFindingAlert
import com.example.model.UnavailableComparisonNotice
import com.example.model.UniversalMedicalReport
import com.example.ui.components.NexzyTopAppBar
import com.example.ui.components.ReportRetentionBanner
import com.example.ui.components.SafetyInlineItemNotice
import com.example.ui.components.SafetySummaryCard
import com.example.ui.theme.BrandMintBackground
import com.example.ui.theme.BrandMintSurface
import com.example.ui.theme.BrandNavyMuted
import com.example.ui.theme.BrandNavyText
import com.example.ui.theme.BrandTealPrimary
import com.example.ui.theme.SurfaceBorder
import com.example.ui.theme.SurfaceCard

/**
 * Displays the real structured medical extraction output produced by [MedicalDocumentExtractor].
 * Never substitutes demo/sample values and never fabricates missing fields or provenance.
 */
@Composable
fun ExtractionResultScreen(
  extractionResult: DocumentExtractionResult?,
  fileName: String?,
  patientName: String?,
  onUploadAnotherClick: () -> Unit,
  onNavigateBack: () -> Unit,
  modifier: Modifier = Modifier,
  onExplainReportClick: (() -> Unit)? = null,
  safetySummary: ReportSafetySummary? = null,
  persistedReport: PersistedMedicalReport? = null,
  persistenceSyncStatus: ReportPersistenceSyncStatus = ReportPersistenceSyncStatus.Idle,
  onKeepReportClick: (() -> Unit)? = null,
  onDeleteReportClick: (() -> Unit)? = null
) {
  Column(
    modifier = modifier
      .fillMaxSize()
      .background(Color.White)
      .testTag("extraction_result_screen")
  ) {
    NexzyTopAppBar(
      title = "Extracted Medical Data",
      subtitle = fileName ?: "Uploaded Document",
      showBackButton = true,
      onBackClick = onNavigateBack
    )

    if (extractionResult == null) {
      MissingExtractionState(
        onReturnToUpload = onUploadAnotherClick,
        modifier = Modifier
          .fillMaxSize()
          .padding(24.dp)
      )
      return
    }

    val alertsByItemKey = safetySummary?.alerts
      ?.filter { it.itemKey != null }
      ?.groupBy { it.itemKey!! }
      ?: emptyMap()
    val unavailableByItemKey = safetySummary?.unavailableComparisons
      ?.filter { it.itemKey != null }
      ?.associateBy { it.itemKey!! }
      ?: emptyMap()

    LazyColumn(
      modifier = Modifier
        .fillMaxSize()
        .padding(horizontal = 20.dp)
        .testTag("extraction_result_list"),
      verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
      item {
        Spacer(modifier = Modifier.height(6.dp))
        ExtractionSummaryHeader(
          result = extractionResult,
          fileName = fileName,
          patientName = patientName
        )
      }

      if (onKeepReportClick != null && onDeleteReportClick != null) {
        item {
          ReportRetentionBanner(
            report = persistedReport,
            syncStatus = persistenceSyncStatus,
            onKeepReportClick = onKeepReportClick,
            onDeleteReportClick = onDeleteReportClick
          )
        }
      }

      item {
        SafetySummaryCard(safetySummary = safetySummary)
      }

      when (val report = extractionResult.report) {
        is UniversalMedicalReport.Laboratory -> {
          item {
            SectionCard(title = "Laboratory Metadata") {
              LabeledValueRow("Facility", report.report.labFacilityName)
              LabeledValueRow("Collection Date", report.report.collectionDate)
              LabeledValueRow("Report Date", report.report.reportDate)
            }
          }
          if (report.report.items.isEmpty()) {
            item { EmptySectionNotice("No laboratory parameters were extracted from this document.") }
          } else {
            itemsIndexed(report.report.items) { index, item ->
              LabItemCard(
                index = index,
                item = item,
                safetyAlert = alertsByItemKey["lab:$index"]?.firstOrNull(),
                unavailableNotice = unavailableByItemKey["lab:$index"]
              )
            }
          }
        }

        is UniversalMedicalReport.Radiology -> {
          val rad: ExtractedRadiologyReport = report.report
          item {
            SectionCard(title = "Imaging Overview") {
              LabeledValueRow("Modality", rad.modality)
              LabeledValueRow("Anatomical Region", rad.anatomicalRegion)
              LabeledValueRow("Clinical Indication", rad.clinicalIndication)
              LabeledValueRow("Technique", rad.techniqueDescription)
            }
          }
          if (rad.anatomicalFindings.isNotEmpty()) {
            itemsIndexed(rad.anatomicalFindings) { index, finding ->
              RadiologyFindingCard(
                index = index,
                finding = finding,
                safetyAlert = alertsByItemKey["rad_finding:$index"]?.firstOrNull()
              )
            }
          }
          if (rad.formalImpressions.isNotEmpty()) {
            itemsIndexed(rad.formalImpressions) { index, impression ->
              RadiologyImpressionCard(
                index = index,
                impression = impression,
                safetyAlert = alertsByItemKey["rad_impression:$index"]?.firstOrNull()
              )
            }
          }
          if (rad.anatomicalFindings.isEmpty() && rad.formalImpressions.isEmpty()) {
            item { EmptySectionNotice("No imaging findings or formal impressions were extracted.") }
          }
        }

        is UniversalMedicalReport.Cardiology -> {
          val cardio: ExtractedCardiologyReport = report.report
          val cardioAlert = alertsByItemKey.entries
            .firstOrNull { it.key.startsWith("cardio:") }
            ?.value
            ?.firstOrNull()
          item {
            CardiologyReportCard(report = cardio, safetyAlert = cardioAlert)
          }
        }

        is UniversalMedicalReport.Specialised -> {
          val spec: ExtractedSpecialisedReport = report.report
          item {
            SpecialisedReportCard(
              report = spec,
              safetyAlert = alertsByItemKey["spec:diagnosticConclusion"]?.firstOrNull()
            )
          }
        }

        is UniversalMedicalReport.Clinical -> {
          val clinical: ExtractedClinicalDocument = report.report
          item {
            SectionCard(title = "Document Overview") {
              LabeledValueRow("Document Subtype", clinical.documentSubtype)
              LabeledValueRow("Facility / Clinician", clinical.facilityOrDoctorName)
              LabeledValueRow("Follow-Up Date", clinical.followUpDate)
            }
          }
          if (clinical.primaryDiagnoses.isNotEmpty()) {
            item {
              ClinicalFactGroupCard(
                title = "Primary Diagnoses",
                facts = clinical.primaryDiagnoses,
                tagPrefix = "clinical_diagnosis"
              )
            }
          }
          if (clinical.surgicalProcedures.isNotEmpty()) {
            item {
              ClinicalFactGroupCard(
                title = "Surgical / Clinical Procedures",
                facts = clinical.surgicalProcedures,
                tagPrefix = "clinical_procedure"
              )
            }
          }
          if (clinical.prescribedMedications.isNotEmpty()) {
            itemsIndexed(clinical.prescribedMedications) { index, med ->
              MedicationCard(index = index, med = med)
            }
          }
          if (clinical.allergiesRecorded.isNotEmpty()) {
            item {
              ClinicalFactGroupCard(
                title = "Allergies Recorded",
                facts = clinical.allergiesRecorded,
                tagPrefix = "clinical_allergy"
              )
            }
          }
          if (clinical.dischargeInstructions.isNotEmpty()) {
            item {
              ClinicalFactGroupCard(
                title = "Discharge Instructions",
                facts = clinical.dischargeInstructions,
                tagPrefix = "clinical_instruction"
              )
            }
          }
        }

        is UniversalMedicalReport.Unidentified -> {
          item {
            UnidentifiedReportCard(
              reason = report.reason,
              rawSnippet = report.rawSnippet
            )
          }
        }
      }

      item {
        Spacer(modifier = Modifier.height(6.dp))
        if (extractionResult.report !is UniversalMedicalReport.Unidentified && onExplainReportClick != null) {
          Button(
            onClick = onExplainReportClick,
            colors = ButtonDefaults.buttonColors(containerColor = BrandTealPrimary),
            shape = RoundedCornerShape(28.dp),
            modifier = Modifier
              .fillMaxWidth()
              .height(52.dp)
              .testTag("btn_explain_report")
          ) {
            Icon(Icons.Default.AutoAwesome, contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
            Text("Explain in Simple Language", fontWeight = FontWeight.Bold, color = Color.White)
          }
          Spacer(modifier = Modifier.height(10.dp))
        }
        Button(
          onClick = onUploadAnotherClick,
          colors = ButtonDefaults.buttonColors(containerColor = BrandTealPrimary),
          shape = RoundedCornerShape(28.dp),
          modifier = Modifier
            .fillMaxWidth()
            .height(52.dp)
            .testTag("btn_upload_another_report")
        ) {
          Icon(Icons.Default.CloudUpload, contentDescription = null)
          Spacer(modifier = Modifier.width(8.dp))
          Text("Upload Another Report", fontWeight = FontWeight.Bold, color = Color.White)
        }
        Spacer(modifier = Modifier.height(10.dp))
        OutlinedButton(
          onClick = onNavigateBack,
          shape = RoundedCornerShape(28.dp),
          modifier = Modifier
            .fillMaxWidth()
            .height(48.dp)
            .testTag("btn_back_home_from_extraction")
        ) {
          Text("Back", color = BrandNavyText, fontWeight = FontWeight.SemiBold)
        }
        Spacer(modifier = Modifier.height(24.dp))
      }
    }
  }
}

@Composable
private fun ExtractionSummaryHeader(
  result: DocumentExtractionResult,
  fileName: String?,
  patientName: String?
) {
  Card(
    shape = RoundedCornerShape(16.dp),
    colors = CardDefaults.cardColors(containerColor = BrandMintSurface),
    modifier = Modifier
      .fillMaxWidth()
      .border(1.dp, BrandTealPrimary.copy(alpha = 0.35f), RoundedCornerShape(16.dp))
      .testTag("extraction_result_header")
  ) {
    Column(modifier = Modifier.padding(16.dp)) {
      Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
          imageVector = Icons.AutoMirrored.Filled.FactCheck,
          contentDescription = null,
          tint = BrandTealPrimary,
          modifier = Modifier.size(22.dp)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
          text = result.category.displayName,
          fontWeight = FontWeight.Bold,
          fontSize = 15.sp,
          color = BrandNavyText,
          modifier = Modifier.testTag("extraction_category_badge")
        )
      }
      Spacer(modifier = Modifier.height(6.dp))
      Text(
        text = "Document: ${fileName ?: "Uploaded File"} • ${result.pageCount} page(s)",
        fontSize = 12.sp,
        color = BrandNavyMuted
      )
      if (!patientName.isNullOrBlank()) {
        Text(
          text = "Profile: $patientName",
          fontSize = 12.sp,
          color = BrandNavyMuted
        )
      }
      Spacer(modifier = Modifier.height(6.dp))
      Text(
        text = "Values shown below reflect exact source-reported entries and provenance. Unreported fields remain blank.",
        fontSize = 11.sp,
        color = BrandNavyMuted
      )
    }
  }
}

@Composable
private fun LabItemCard(
  index: Int,
  item: ExtractedLabItem,
  safetyAlert: SafetyFindingAlert? = null,
  unavailableNotice: UnavailableComparisonNotice? = null
) {
  Card(
    shape = RoundedCornerShape(14.dp),
    colors = CardDefaults.cardColors(containerColor = SurfaceCard),
    modifier = Modifier
      .fillMaxWidth()
      .border(1.dp, SurfaceBorder, RoundedCornerShape(14.dp))
      .testTag("lab_item_card_$index")
  ) {
    Column(modifier = Modifier.padding(14.dp)) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
      ) {
        Text(
          text = item.originalName ?: "Unnamed Parameter",
          fontWeight = FontWeight.Bold,
          fontSize = 15.sp,
          color = if (item.originalName != null) BrandNavyText else BrandNavyMuted,
          fontStyle = if (item.originalName != null) FontStyle.Normal else FontStyle.Italic,
          modifier = Modifier.weight(1f)
        )
        Spacer(modifier = Modifier.width(8.dp))
        FindingStatusChip(item.findingStatus)
      }

      Spacer(modifier = Modifier.height(8.dp))

      val valueDisplay = when {
        item.originalValue != null && item.originalUnit != null -> "${item.originalValue} ${item.originalUnit}"
        item.originalValue != null -> item.originalValue
        else -> null
      }
      LabeledValueRow("Reported Value", valueDisplay)
      LabeledValueRow("Reference Range", item.referenceRange)
      if (item.laboratoryFlag != null) {
        LabeledValueRow("Lab Flag", item.laboratoryFlag)
      }

      if (safetyAlert != null || unavailableNotice != null) {
        Spacer(modifier = Modifier.height(8.dp))
        SafetyInlineItemNotice(alert = safetyAlert, unavailableNotice = unavailableNotice)
      }

      Spacer(modifier = Modifier.height(8.dp))
      ProvenanceBlock(item.provenance, tag = "lab_item_provenance_$index")
    }
  }
}

@Composable
private fun RadiologyFindingCard(
  index: Int,
  finding: AnatomicalFinding,
  safetyAlert: SafetyFindingAlert? = null
) {
  Card(
    shape = RoundedCornerShape(14.dp),
    colors = CardDefaults.cardColors(containerColor = SurfaceCard),
    modifier = Modifier
      .fillMaxWidth()
      .border(1.dp, SurfaceBorder, RoundedCornerShape(14.dp))
  ) {
    Column(modifier = Modifier.padding(14.dp)) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
      ) {
        Text(
          text = finding.anatomicalSite ?: "Site not reported",
          fontWeight = FontWeight.Bold,
          fontSize = 14.sp,
          color = if (finding.anatomicalSite != null) BrandNavyText else BrandNavyMuted,
          fontStyle = if (finding.anatomicalSite != null) FontStyle.Normal else FontStyle.Italic,
          modifier = Modifier.weight(1f)
        )
        FindingStatusChip(finding.findingStatus)
      }
      Spacer(modifier = Modifier.height(6.dp))
      NullableTextValue(finding.observationText, missingLabel = "Observation text not reported")
      if (safetyAlert != null) {
        Spacer(modifier = Modifier.height(8.dp))
        SafetyInlineItemNotice(alert = safetyAlert, unavailableNotice = null)
      }
      Spacer(modifier = Modifier.height(8.dp))
      ProvenanceBlock(finding.provenance, tag = "rad_finding_provenance_$index")
    }
  }
}

@Composable
private fun RadiologyImpressionCard(
  index: Int,
  impression: RadiologyImpression,
  safetyAlert: SafetyFindingAlert? = null
) {
  Card(
    shape = RoundedCornerShape(14.dp),
    colors = CardDefaults.cardColors(containerColor = BrandMintSurface),
    modifier = Modifier
      .fillMaxWidth()
      .border(1.dp, SurfaceBorder, RoundedCornerShape(14.dp))
  ) {
    Column(modifier = Modifier.padding(14.dp)) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
      ) {
        Text("Formal Impression", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = BrandTealPrimary)
        FindingStatusChip(impression.findingStatus)
      }
      Spacer(modifier = Modifier.height(6.dp))
      NullableTextValue(impression.impressionText, missingLabel = "Impression text not reported")
      if (safetyAlert != null) {
        Spacer(modifier = Modifier.height(8.dp))
        SafetyInlineItemNotice(alert = safetyAlert, unavailableNotice = null)
      }
      Spacer(modifier = Modifier.height(8.dp))
      ProvenanceBlock(impression.provenance, tag = "rad_impression_provenance_$index")
    }
  }
}

@Composable
private fun CardiologyReportCard(
  report: ExtractedCardiologyReport,
  safetyAlert: SafetyFindingAlert? = null
) {
  SectionCard(title = "Cardiology Findings") {
    Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.SpaceBetween,
      verticalAlignment = Alignment.CenterVertically
    ) {
      Text("Finding Status", fontSize = 12.sp, color = BrandNavyMuted)
      FindingStatusChip(report.findingStatus)
    }
    Spacer(modifier = Modifier.height(6.dp))
    LabeledValueRow("Modality", report.modality)
    LabeledValueRow("Heart Rate", report.heartRateBpm)
    LabeledValueRow("Rhythm", report.rhythmInterpretation)
    LabeledValueRow("PR Interval (ms)", report.prIntervalMs)
    LabeledValueRow("QRS Duration (ms)", report.qrsDurationMs)
    LabeledValueRow("QTc Interval (ms)", report.qtcIntervalMs)
    LabeledValueRow("Ejection Fraction", report.ejectionFractionPercent)
    LabeledValueRow("Wall Motion", report.wallMotionFindings)
    LabeledValueRow("Formal Conclusion", report.formalConclusion)
    if (safetyAlert != null) {
      Spacer(modifier = Modifier.height(8.dp))
      SafetyInlineItemNotice(alert = safetyAlert, unavailableNotice = null)
    }
    Spacer(modifier = Modifier.height(8.dp))
    ProvenanceBlock(report.provenance, tag = "cardio_provenance")
  }
}

@Composable
private fun SpecialisedReportCard(
  report: ExtractedSpecialisedReport,
  safetyAlert: SafetyFindingAlert? = null
) {
  SectionCard(title = "Specialised Pathology Findings") {
    Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.SpaceBetween,
      verticalAlignment = Alignment.CenterVertically
    ) {
      Text("Finding Status", fontSize = 12.sp, color = BrandNavyMuted)
      FindingStatusChip(report.findingStatus)
    }
    Spacer(modifier = Modifier.height(6.dp))
    LabeledValueRow("Report Subtype", report.reportSubtype)
    LabeledValueRow("Specimen Source", report.specimenSource)
    LabeledValueRow("Clinical History", report.clinicalHistory)
    LabeledValueRow("Gross Description", report.grossDescription)
    LabeledValueRow("Microscopic Description", report.microscopicDescription)
    LabeledValueRow("Grading / Staging", report.gradingOrStaging)
    LabeledValueRow("Diagnostic Conclusion", report.diagnosticConclusion)
    if (safetyAlert != null) {
      Spacer(modifier = Modifier.height(8.dp))
      SafetyInlineItemNotice(alert = safetyAlert, unavailableNotice = null)
    }
    Spacer(modifier = Modifier.height(8.dp))
    ProvenanceBlock(report.provenance, tag = "specialised_provenance")
  }
}

@Composable
private fun ClinicalFactGroupCard(
  title: String,
  facts: List<ExtractedClinicalFact>,
  tagPrefix: String
) {
  SectionCard(title = title) {
    facts.forEachIndexed { idx, fact ->
      if (idx > 0) {
        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp), color = SurfaceBorder)
      }
      Text(
        text = fact.text,
        fontSize = 13.sp,
        fontWeight = FontWeight.Medium,
        color = BrandNavyText
      )
      Spacer(modifier = Modifier.height(4.dp))
      ProvenanceBlock(fact.provenance, tag = "${tagPrefix}_provenance_$idx")
    }
  }
}

@Composable
private fun MedicationCard(index: Int, med: ExtractedMedication) {
  Card(
    shape = RoundedCornerShape(14.dp),
    colors = CardDefaults.cardColors(containerColor = SurfaceCard),
    modifier = Modifier
      .fillMaxWidth()
      .border(1.dp, SurfaceBorder, RoundedCornerShape(14.dp))
  ) {
    Column(modifier = Modifier.padding(14.dp)) {
      Text(
        text = med.drugName ?: "Medication name not reported",
        fontWeight = FontWeight.Bold,
        fontSize = 15.sp,
        color = if (med.drugName != null) BrandNavyText else BrandNavyMuted,
        fontStyle = if (med.drugName != null) FontStyle.Normal else FontStyle.Italic
      )
      Spacer(modifier = Modifier.height(6.dp))
      LabeledValueRow("Dosage", med.dosage)
      LabeledValueRow("Frequency", med.frequency)
      LabeledValueRow("Duration", med.duration)
      LabeledValueRow("Instructions", med.instructions)
      Spacer(modifier = Modifier.height(8.dp))
      ProvenanceBlock(med.provenance, tag = "medication_provenance_$index")
    }
  }
}

@Composable
private fun UnidentifiedReportCard(reason: String, rawSnippet: String?) {
  Card(
    shape = RoundedCornerShape(14.dp),
    colors = CardDefaults.cardColors(containerColor = Color(0xFFFEF3C7)),
    modifier = Modifier
      .fillMaxWidth()
      .border(1.dp, Color(0xFFFDE68A), RoundedCornerShape(14.dp))
      .testTag("extraction_unidentified_card")
  ) {
    Column(modifier = Modifier.padding(16.dp)) {
      Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.Default.WarningAmber, contentDescription = null, tint = Color(0xFFB45309))
        Spacer(modifier = Modifier.width(8.dp))
        Text(
          text = "Unsupported or Unidentified Document",
          fontWeight = FontWeight.Bold,
          fontSize = 15.sp,
          color = Color(0xFF92400E)
        )
      }
      Spacer(modifier = Modifier.height(8.dp))
      Text(text = reason, fontSize = 13.sp, color = Color(0xFF78350F))
      if (!rawSnippet.isNullOrBlank()) {
        Spacer(modifier = Modifier.height(8.dp))
        Text(
          text = "Detected excerpt: $rawSnippet",
          fontSize = 12.sp,
          color = Color(0xFF92400E)
        )
      }
    }
  }
}

@Composable
private fun SectionCard(
  title: String,
  content: @Composable () -> Unit
) {
  Card(
    shape = RoundedCornerShape(14.dp),
    colors = CardDefaults.cardColors(containerColor = SurfaceCard),
    modifier = Modifier
      .fillMaxWidth()
      .border(1.dp, SurfaceBorder, RoundedCornerShape(14.dp))
  ) {
    Column(modifier = Modifier.padding(14.dp)) {
      Text(
        text = title,
        fontWeight = FontWeight.Bold,
        fontSize = 14.sp,
        color = BrandTealPrimary
      )
      Spacer(modifier = Modifier.height(8.dp))
      content()
    }
  }
}

@Composable
private fun LabeledValueRow(label: String, value: String?) {
  Row(
    modifier = Modifier
      .fillMaxWidth()
      .padding(vertical = 2.dp),
    horizontalArrangement = Arrangement.SpaceBetween,
    verticalAlignment = Alignment.Top
  ) {
    Text(
      text = label,
      fontSize = 12.sp,
      color = BrandNavyMuted,
      modifier = Modifier.width(132.dp)
    )
    Text(
      text = value ?: "Not reported in document",
      fontSize = 12.sp,
      fontWeight = if (value != null) FontWeight.SemiBold else FontWeight.Normal,
      fontStyle = if (value != null) FontStyle.Normal else FontStyle.Italic,
      color = if (value != null) BrandNavyText else BrandNavyMuted,
      textAlign = TextAlign.End,
      modifier = Modifier.weight(1f)
    )
  }
}

@Composable
private fun NullableTextValue(value: String?, missingLabel: String) {
  Text(
    text = value ?: missingLabel,
    fontSize = 13.sp,
    color = if (value != null) BrandNavyText else BrandNavyMuted,
    fontStyle = if (value != null) FontStyle.Normal else FontStyle.Italic
  )
}

@Composable
private fun FindingStatusChip(status: FindingStatus) {
  val (bg, fg, label) = when (status) {
    FindingStatus.NORMAL -> Triple(BrandMintBackground, BrandTealPrimary, "NORMAL")
    FindingStatus.ABNORMAL -> Triple(Color(0xFFFEF3C7), Color(0xFFB45309), "ABNORMAL")
    FindingStatus.POSITIVE -> Triple(Color(0xFFFEE2E2), Color(0xFFDC2626), "POSITIVE")
    FindingStatus.NEGATIVE -> Triple(BrandMintBackground, BrandTealPrimary, "NEGATIVE")
    FindingStatus.NOT_MENTIONED -> Triple(Color(0xFFF1F5F9), Color(0xFF64748B), "NOT MENTIONED")
    FindingStatus.UNCERTAIN_OR_UNREADABLE -> Triple(Color(0xFFEDE9FE), Color(0xFF6D28D9), "UNCERTAIN / UNREADABLE")
  }
  Box(
    modifier = Modifier
      .clip(RoundedCornerShape(8.dp))
      .background(bg)
      .padding(horizontal = 8.dp, vertical = 4.dp)
  ) {
    Text(text = label, fontSize = 10.sp, fontWeight = FontWeight.Bold, color = fg)
  }
}

@Composable
private fun ProvenanceBlock(provenance: FieldProvenance, tag: String) {
  Box(
    modifier = Modifier
      .fillMaxWidth()
      .clip(RoundedCornerShape(8.dp))
      .background(Color.White)
      .border(1.dp, SurfaceBorder, RoundedCornerShape(8.dp))
      .padding(8.dp)
      .testTag(tag)
  ) {
    if (provenance.availability == ProvenanceAvailability.UNAVAILABLE) {
      Text(
        text = "Provenance: Unavailable (not provided in document extraction)",
        fontSize = 11.sp,
        fontStyle = FontStyle.Italic,
        color = BrandNavyMuted
      )
    } else {
      Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        val pagePart = provenance.pageNumber?.let { "Page $it" } ?: "Page unavailable"
        val confPart = provenance.estimatedExtractionConfidence?.let {
          "AI extraction confidence: ${(it * 100).toInt()}%"
        } ?: "AI confidence unavailable"
        Text(
          text = "$pagePart • $confPart",
          fontSize = 11.sp,
          fontWeight = FontWeight.SemiBold,
          color = BrandNavyMuted
        )
        if (provenance.sourceSnippet != null) {
          Text(
            text = "Source: \"${provenance.sourceSnippet}\"",
            fontSize = 11.sp,
            color = BrandNavyText
          )
        } else {
          Text(
            text = "Source snippet unavailable",
            fontSize = 11.sp,
            fontStyle = FontStyle.Italic,
            color = BrandNavyMuted
          )
        }
      }
    }
  }
}

@Composable
private fun EmptySectionNotice(message: String) {
  Card(
    shape = RoundedCornerShape(12.dp),
    colors = CardDefaults.cardColors(containerColor = SurfaceCard),
    modifier = Modifier
      .fillMaxWidth()
      .border(1.dp, SurfaceBorder, RoundedCornerShape(12.dp))
  ) {
    Row(
      modifier = Modifier.padding(14.dp),
      verticalAlignment = Alignment.CenterVertically
    ) {
      Icon(Icons.Default.Description, contentDescription = null, tint = BrandNavyMuted)
      Spacer(modifier = Modifier.width(10.dp))
      Text(text = message, fontSize = 13.sp, color = BrandNavyMuted)
    }
  }
}

@Composable
private fun MissingExtractionState(
  onReturnToUpload: () -> Unit,
  modifier: Modifier = Modifier
) {
  Column(
    modifier = modifier.testTag("extraction_missing_state"),
    verticalArrangement = Arrangement.Center,
    horizontalAlignment = Alignment.CenterHorizontally
  ) {
    Icon(
      imageVector = Icons.Default.ErrorOutline,
      contentDescription = null,
      tint = Color(0xFFDC2626),
      modifier = Modifier.size(48.dp)
    )
    Spacer(modifier = Modifier.height(12.dp))
    Text(
      text = "No Extracted Result Available",
      fontWeight = FontWeight.Bold,
      fontSize = 18.sp,
      color = BrandNavyText,
      textAlign = TextAlign.Center
    )
    Spacer(modifier = Modifier.height(6.dp))
    Text(
      text = "Please upload a medical report first so NexzyHealth can extract its real findings.",
      fontSize = 13.sp,
      color = BrandNavyMuted,
      textAlign = TextAlign.Center
    )
    Spacer(modifier = Modifier.height(20.dp))
    Button(
      onClick = onReturnToUpload,
      colors = ButtonDefaults.buttonColors(containerColor = BrandTealPrimary),
      shape = RoundedCornerShape(28.dp),
      modifier = Modifier
        .fillMaxWidth()
        .height(48.dp)
        .testTag("btn_missing_result_upload")
    ) {
      Text("Select a Document", color = Color.White, fontWeight = FontWeight.Bold)
    }
  }
}
