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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.FactCheck
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Medication
import androidx.compose.material.icons.filled.QuestionAnswer
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.model.CategoryExplanation
import com.example.model.ExplainedCardiologyMetric
import com.example.model.ExplainedClinicalFact
import com.example.model.ExplainedLabItem
import com.example.model.ExplainedMedication
import com.example.model.ExplainedRadiologyFinding
import com.example.model.ExplainedRadiologyImpression
import com.example.model.ExplainedSpecialisedSection
import com.example.model.ExplanationConstants
import com.example.model.FieldProvenance
import com.example.model.FindingStatus
import com.example.model.MedicalTermDefinition
import com.example.model.PersistedMedicalReport
import com.example.model.ProvenanceAvailability
import com.example.model.ReportExplanationResult
import com.example.model.ReportExplanationState
import com.example.model.ReportPersistenceSyncStatus
import com.example.model.ReportSafetySummary
import com.example.model.SafetyBucket
import com.example.ui.components.NexzyTopAppBar
import com.example.ui.components.ReportRetentionBanner
import com.example.ui.components.SafetyCompactNoticeBanner
import com.example.ui.components.SafetySummaryCard
import com.example.ui.theme.BrandMintBackground
import com.example.ui.theme.BrandMintSurface
import com.example.ui.theme.BrandNavyMuted
import com.example.ui.theme.BrandNavyText
import com.example.ui.theme.BrandTealPrimary
import com.example.ui.theme.StatusAttentionAmber
import com.example.ui.theme.StatusAttentionHoney
import com.example.ui.theme.StatusNeutralBg
import com.example.ui.theme.StatusNeutralGrey
import com.example.ui.theme.SurfaceBorder
import com.example.ui.theme.SurfaceCard

/**
 * Dedicated Task 3D screen rendering category-specific plain-language medical explanations
 * strictly grounded in Task 3C extracted results.
 */
@Composable
fun ReportExplanationScreen(
  explanationState: ReportExplanationState,
  isConsentDialogVisible: Boolean,
  fileName: String?,
  patientName: String?,
  onConfirmConsent: () -> Unit,
  onDismissConsent: () -> Unit,
  onRequestExplanation: () -> Unit,
  onRetryClick: () -> Unit,
  onNavigateBack: () -> Unit,
  modifier: Modifier = Modifier,
  safetySummary: ReportSafetySummary? = null,
  persistedReport: PersistedMedicalReport? = null,
  persistenceSyncStatus: ReportPersistenceSyncStatus = ReportPersistenceSyncStatus.Idle,
  onKeepReportClick: (() -> Unit)? = null,
  onDeleteReportClick: (() -> Unit)? = null
) {
  val showCompactSafetyBanner = safetySummary == null ||
    safetySummary.hasCriticalOrUrgentFindings ||
    safetySummary.hasAbnormalOrPositiveFindings ||
    safetySummary.hasUncertainOrUnreadableFindings ||
    safetySummary.overallClassification == SafetyBucket.INCOMPLETE_ASSESSMENT

  Column(
    modifier = modifier
      .fillMaxSize()
      .background(MaterialTheme.colorScheme.background)
      .testTag("report_explanation_screen")
  ) {
    NexzyTopAppBar(
      title = "Plain-Language Explanation",
      subtitle = fileName ?: "Medical Report",
      showBackButton = true,
      onBackClick = onNavigateBack
    )

    when (explanationState) {
      is ReportExplanationState.Idle,
      is ReportExplanationState.WaitingForConsent -> {
        if (showCompactSafetyBanner) {
          SafetyCompactNoticeBanner(
            safetySummary = safetySummary,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)
          )
        }
        ExplanationConsentPromptState(
          isWaitingForConsent = explanationState is ReportExplanationState.WaitingForConsent,
          onRequestExplanation = onRequestExplanation,
          onNavigateBack = onNavigateBack,
          modifier = Modifier
            .weight(1f)
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(24.dp)
        )
      }

      is ReportExplanationState.Loading -> {
        if (showCompactSafetyBanner) {
          SafetyCompactNoticeBanner(
            safetySummary = safetySummary,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)
          )
        }
        ExplanationLoadingState(
          modifier = Modifier
            .weight(1f)
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(24.dp)
        )
      }

      is ReportExplanationState.Error -> {
        if (showCompactSafetyBanner) {
          SafetyCompactNoticeBanner(
            safetySummary = safetySummary,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)
          )
        }
        ExplanationErrorState(
          message = explanationState.message,
          isRetryable = explanationState.isRetryable,
          onRetryClick = onRetryClick,
          onNavigateBack = onNavigateBack,
          modifier = Modifier
            .weight(1f)
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(24.dp)
        )
      }

      is ReportExplanationState.Success -> {
        ExplanationSuccessContent(
          explanation = explanationState.explanation,
          safetySummary = safetySummary,
          fileName = fileName,
          patientName = patientName,
          onNavigateBack = onNavigateBack,
          persistedReport = persistedReport,
          persistenceSyncStatus = persistenceSyncStatus,
          onKeepReportClick = onKeepReportClick,
          onDeleteReportClick = onDeleteReportClick
        )
      }
    }
  }

  if (isConsentDialogVisible) {
    ExplanationAiConsentDialog(
      onConfirm = onConfirmConsent,
      onDismiss = onDismissConsent
    )
  }
}

@Composable
private fun ExplanationSuccessContent(
  explanation: ReportExplanationResult,
  safetySummary: ReportSafetySummary?,
  fileName: String?,
  patientName: String?,
  onNavigateBack: () -> Unit,
  persistedReport: PersistedMedicalReport? = null,
  persistenceSyncStatus: ReportPersistenceSyncStatus = ReportPersistenceSyncStatus.Idle,
  onKeepReportClick: (() -> Unit)? = null,
  onDeleteReportClick: (() -> Unit)? = null
) {
  LazyColumn(
    modifier = Modifier
      .fillMaxSize()
      .padding(horizontal = 20.dp)
      .testTag("explanation_result_list"),
    verticalArrangement = Arrangement.spacedBy(12.dp)
  ) {
    item {
      Spacer(modifier = Modifier.height(6.dp))
      SafetyDisclaimerBanner(disclaimer = explanation.safetyDisclaimer)
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

    item {
      ExplanationSummaryCard(
        explanation = explanation,
        fileName = fileName,
        patientName = patientName
      )
    }

    if (explanation.uncertaintyNotes.isNotEmpty()) {
      item {
        UncertaintyNotesCard(notes = explanation.uncertaintyNotes)
      }
    }

    when (val details = explanation.categoryExplanation) {
      is CategoryExplanation.Laboratory -> {
        item {
          SectionInfoCard(title = "Laboratory Details") {
            PreservedValueRow("Facility", details.labFacilityName)
            PreservedValueRow("Collection Date", details.collectionDate)
            PreservedValueRow("Report Date", details.reportDate)
          }
        }
        if (details.items.isEmpty()) {
          item { EmptyNoticeCard("No laboratory parameters were extracted to explain.") }
        } else {
          itemsIndexed(details.items) { index, item ->
            ExplainedLabItemCard(index = index, item = item)
          }
        }
      }

      is CategoryExplanation.Radiology -> {
        item {
          SectionInfoCard(title = "Imaging Study Overview") {
            PreservedValueRow("Modality", details.modality)
            PreservedValueRow("Anatomical Region", details.anatomicalRegion)
            PreservedValueRow("Clinical Indication", details.clinicalIndication)
            PreservedValueRow("Technique", details.techniqueDescription)
            if (!details.studyOverviewExplanation.isNullOrBlank()) {
              Spacer(modifier = Modifier.height(8.dp))
              PlainLanguageCallout(text = details.studyOverviewExplanation)
            }
          }
        }
        if (details.findings.isNotEmpty()) {
          itemsIndexed(details.findings) { index, finding ->
            ExplainedRadiologyFindingCard(index = index, finding = finding)
          }
        }
        if (details.impressions.isNotEmpty()) {
          itemsIndexed(details.impressions) { index, impression ->
            ExplainedRadiologyImpressionCard(index = index, impression = impression)
          }
        }
        if (details.findings.isEmpty() && details.impressions.isEmpty()) {
          item { EmptyNoticeCard("No written radiology findings or impressions were extracted.") }
        }
      }

      is CategoryExplanation.Cardiology -> {
        val src = details.sourceReport
        item {
          SectionInfoCard(title = "Cardiology Report Overview") {
            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.SpaceBetween,
              verticalAlignment = Alignment.CenterVertically
            ) {
              Text("Reported Status", fontSize = 12.sp, color = BrandNavyMuted)
              ExplanationFindingStatusChip(src.findingStatus)
            }
            Spacer(modifier = Modifier.height(6.dp))
            PreservedValueRow("Modality", src.modality)
            if (!details.modalityExplanation.isNullOrBlank()) {
              Spacer(modifier = Modifier.height(8.dp))
              PlainLanguageCallout(text = details.modalityExplanation)
            }
            if (!details.uncertaintyNote.isNullOrBlank()) {
              Spacer(modifier = Modifier.height(8.dp))
              InlineUncertaintyBanner(note = details.uncertaintyNote)
            }
          }
        }
        if (details.metrics.isNotEmpty()) {
          itemsIndexed(details.metrics) { index, metric ->
            ExplainedCardiologyMetricCard(index = index, metric = metric)
          }
        }
        item {
          SectionInfoCard(title = "Cardiology Written Conclusion") {
            PreservedValueRow("Formal Conclusion", src.formalConclusion)
            if (!details.conclusionExplanation.isNullOrBlank()) {
              Spacer(modifier = Modifier.height(8.dp))
              PlainLanguageCallout(text = details.conclusionExplanation)
            }
            Spacer(modifier = Modifier.height(8.dp))
            ExplanationProvenanceBlock(
              provenance = src.fieldProvenance["formalConclusion"] ?: src.provenance,
              tag = "explain_cardio_conclusion_provenance"
            )
          }
        }
      }

      is CategoryExplanation.Specialised -> {
        val src = details.sourceReport
        item {
          SectionInfoCard(title = "Specialised Pathology Overview") {
            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.SpaceBetween,
              verticalAlignment = Alignment.CenterVertically
            ) {
              Text("Reported Status", fontSize = 12.sp, color = BrandNavyMuted)
              ExplanationFindingStatusChip(src.findingStatus)
            }
            Spacer(modifier = Modifier.height(6.dp))
            PreservedValueRow("Report Subtype", src.reportSubtype)
            if (!details.reportOverviewExplanation.isNullOrBlank()) {
              Spacer(modifier = Modifier.height(8.dp))
              PlainLanguageCallout(text = details.reportOverviewExplanation)
            }
            if (!details.uncertaintyNote.isNullOrBlank()) {
              Spacer(modifier = Modifier.height(8.dp))
              InlineUncertaintyBanner(note = details.uncertaintyNote)
            }
          }
        }
        if (details.sections.isNotEmpty()) {
          itemsIndexed(details.sections) { index, section ->
            ExplainedSpecialisedSectionCard(index = index, section = section)
          }
        }
        item {
          SectionInfoCard(title = "Pathology Diagnostic Conclusion") {
            PreservedValueRow("Diagnostic Conclusion", src.diagnosticConclusion)
            if (!details.conclusionExplanation.isNullOrBlank()) {
              Spacer(modifier = Modifier.height(8.dp))
              PlainLanguageCallout(text = details.conclusionExplanation)
            }
            Spacer(modifier = Modifier.height(8.dp))
            ExplanationProvenanceBlock(
              provenance = src.fieldProvenance["diagnosticConclusion"] ?: src.provenance,
              tag = "explain_specialised_conclusion_provenance"
            )
          }
        }
      }

      is CategoryExplanation.Clinical -> {
        item {
          SectionInfoCard(title = "Clinical Document Overview") {
            PreservedValueRow("Document Subtype", details.documentSubtype)
            PreservedValueRow("Facility / Clinician", details.facilityOrDoctorName)
            PreservedValueRow("Follow-Up Date", details.followUpDate)
            if (!details.documentOverviewExplanation.isNullOrBlank()) {
              Spacer(modifier = Modifier.height(8.dp))
              PlainLanguageCallout(text = details.documentOverviewExplanation)
            }
          }
        }
        if (details.diagnoses.isNotEmpty()) {
          item {
            ExplainedClinicalFactGroupCard(
              title = "Recorded Diagnoses",
              facts = details.diagnoses,
              tagPrefix = "explain_diagnosis"
            )
          }
        }
        if (details.procedures.isNotEmpty()) {
          item {
            ExplainedClinicalFactGroupCard(
              title = "Recorded Procedures",
              facts = details.procedures,
              tagPrefix = "explain_procedure"
            )
          }
        }
        if (details.medications.isNotEmpty()) {
          itemsIndexed(details.medications) { index, med ->
            ExplainedMedicationCard(index = index, med = med)
          }
        }
        if (details.allergies.isNotEmpty()) {
          item {
            ExplainedClinicalFactGroupCard(
              title = "Allergies Recorded",
              facts = details.allergies,
              tagPrefix = "explain_allergy"
            )
          }
        }
        if (details.dischargeInstructions.isNotEmpty()) {
          item {
            ExplainedClinicalFactGroupCard(
              title = "Care & Discharge Instructions",
              facts = details.dischargeInstructions,
              tagPrefix = "explain_instruction"
            )
          }
        }
      }
    }

    if (explanation.medicalTerms.isNotEmpty()) {
      item {
        MedicalTermsCard(terms = explanation.medicalTerms)
      }
    }

    if (explanation.questionsForDoctor.isNotEmpty()) {
      item {
        DoctorQuestionsCard(questions = explanation.questionsForDoctor)
      }
    }

    item {
      Spacer(modifier = Modifier.height(4.dp))
      OutlinedButton(
        onClick = onNavigateBack,
        shape = RoundedCornerShape(28.dp),
        modifier = Modifier
          .fillMaxWidth()
          .height(48.dp)
          .testTag("btn_back_to_extracted_data")
      ) {
        Text("Back to Extracted Medical Data", color = BrandNavyText, fontWeight = FontWeight.SemiBold)
      }
      Spacer(modifier = Modifier.height(24.dp))
    }
  }
}

@Composable
private fun SafetyDisclaimerBanner(disclaimer: String) {
  Card(
    shape = RoundedCornerShape(12.dp),
    colors = CardDefaults.cardColors(containerColor = StatusNeutralBg),
    modifier = Modifier
      .fillMaxWidth()
      .border(1.dp, Color(0xFFCBD5E1), RoundedCornerShape(12.dp))
      .testTag("explanation_safety_disclaimer")
  ) {
    Row(
      modifier = Modifier.padding(12.dp),
      verticalAlignment = Alignment.Top
    ) {
      Icon(
        imageVector = Icons.Default.Info,
        contentDescription = "Medical safety notice",
        tint = StatusNeutralGrey,
        modifier = Modifier.size(18.dp)
      )
      Spacer(modifier = Modifier.width(8.dp))
      Text(
        text = disclaimer,
        fontSize = 11.sp,
        lineHeight = 16.sp,
        color = StatusNeutralGrey
      )
    }
  }
}

@Composable
private fun ExplanationSummaryCard(
  explanation: ReportExplanationResult,
  fileName: String?,
  patientName: String?
) {
  Card(
    shape = RoundedCornerShape(16.dp),
    colors = CardDefaults.cardColors(containerColor = BrandMintSurface),
    modifier = Modifier
      .fillMaxWidth()
      .border(1.dp, BrandTealPrimary.copy(alpha = 0.35f), RoundedCornerShape(16.dp))
      .testTag("explanation_summary_card")
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
          text = explanation.category.displayName,
          fontWeight = FontWeight.Bold,
          fontSize = 15.sp,
          color = BrandNavyText
        )
      }
      Spacer(modifier = Modifier.height(6.dp))
      Text(
        text = "Document: ${fileName ?: "Uploaded File"} • Language: ${explanation.language}" +
          if (!patientName.isNullOrBlank()) " • Profile: $patientName" else "",
        fontSize = 12.sp,
        color = BrandNavyMuted
      )
      Spacer(modifier = Modifier.height(10.dp))
      Text(
        text = "Simple Summary",
        fontWeight = FontWeight.Bold,
        fontSize = 13.sp,
        color = BrandTealPrimary
      )
      Spacer(modifier = Modifier.height(4.dp))
      Text(
        text = explanation.conciseSummary,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        color = BrandNavyText
      )
    }
  }
}

@Composable
private fun UncertaintyNotesCard(notes: List<String>) {
  Card(
    shape = RoundedCornerShape(14.dp),
    colors = CardDefaults.cardColors(containerColor = Color(0xFFEDE9FE)),
    modifier = Modifier
      .fillMaxWidth()
      .border(1.dp, Color(0xFFDDD6FE), RoundedCornerShape(14.dp))
      .testTag("explanation_uncertainty_card")
  ) {
    Column(modifier = Modifier.padding(14.dp)) {
      Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.Default.WarningAmber, contentDescription = null, tint = Color(0xFF6D28D9), modifier = Modifier.size(18.dp))
        Spacer(modifier = Modifier.width(8.dp))
        Text(
          text = "Missing or Uncertain Information",
          fontWeight = FontWeight.Bold,
          fontSize = 13.sp,
          color = Color(0xFF5B21B6)
        )
      }
      Spacer(modifier = Modifier.height(6.dp))
      notes.forEach { note ->
        Text(
          text = "• $note",
          fontSize = 12.sp,
          lineHeight = 17.sp,
          color = Color(0xFF4C1D95),
          modifier = Modifier.padding(vertical = 2.dp)
        )
      }
    }
  }
}

@Composable
private fun ExplainedLabItemCard(index: Int, item: ExplainedLabItem) {
  val src = item.sourceItem
  Card(
    shape = RoundedCornerShape(14.dp),
    colors = CardDefaults.cardColors(containerColor = SurfaceCard),
    modifier = Modifier
      .fillMaxWidth()
      .border(1.dp, SurfaceBorder, RoundedCornerShape(14.dp))
      .testTag("explain_lab_item_card_$index")
  ) {
    Column(modifier = Modifier.padding(14.dp)) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
      ) {
        Text(
          text = src.originalName ?: "Unnamed Parameter",
          fontWeight = FontWeight.Bold,
          fontSize = 15.sp,
          color = if (src.originalName != null) BrandNavyText else BrandNavyMuted,
          modifier = Modifier.weight(1f)
        )
        Spacer(modifier = Modifier.width(8.dp))
        ExplanationFindingStatusChip(src.findingStatus)
      }
      Spacer(modifier = Modifier.height(8.dp))
      val valueDisplay = when {
        src.originalValue != null && src.originalUnit != null -> "${src.originalValue} ${src.originalUnit}"
        src.originalValue != null -> src.originalValue
        else -> null
      }
      PreservedValueRow("Reported Value", valueDisplay)
      PreservedValueRow("Reference Range", src.referenceRange)
      if (src.laboratoryFlag != null) {
        PreservedValueRow("Lab Flag", src.laboratoryFlag)
      }

      if (!item.whatItMeasures.isNullOrBlank()) {
        Spacer(modifier = Modifier.height(8.dp))
        Text(
          text = "What this measures: ${item.whatItMeasures}",
          fontSize = 12.sp,
          color = BrandNavyMuted,
          lineHeight = 17.sp
        )
      }
      if (!item.uncertaintyNote.isNullOrBlank()) {
        Spacer(modifier = Modifier.height(8.dp))
        InlineUncertaintyBanner(note = item.uncertaintyNote)
      } else if (!item.plainLanguageExplanation.isNullOrBlank()) {
        Spacer(modifier = Modifier.height(8.dp))
        PlainLanguageCallout(text = item.plainLanguageExplanation)
      } else {
        Spacer(modifier = Modifier.height(8.dp))
        FallbackNoticeText("Plain-language explanation unavailable for this item; original value preserved above.")
      }

      Spacer(modifier = Modifier.height(8.dp))
      ExplanationProvenanceBlock(src.provenance, tag = "explain_lab_provenance_$index")
    }
  }
}

@Composable
private fun ExplainedRadiologyFindingCard(index: Int, finding: ExplainedRadiologyFinding) {
  val src = finding.sourceFinding
  Card(
    shape = RoundedCornerShape(14.dp),
    colors = CardDefaults.cardColors(containerColor = SurfaceCard),
    modifier = Modifier
      .fillMaxWidth()
      .border(1.dp, SurfaceBorder, RoundedCornerShape(14.dp))
      .testTag("explain_rad_finding_card_$index")
  ) {
    Column(modifier = Modifier.padding(14.dp)) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
      ) {
        Text(
          text = src.anatomicalSite ?: "Site not reported",
          fontWeight = FontWeight.Bold,
          fontSize = 14.sp,
          color = BrandNavyText,
          modifier = Modifier.weight(1f)
        )
        ExplanationFindingStatusChip(src.findingStatus)
      }
      Spacer(modifier = Modifier.height(6.dp))
      Text(
        text = src.observationText ?: "Observation text not reported",
        fontSize = 13.sp,
        color = if (src.observationText != null) BrandNavyText else BrandNavyMuted,
        fontStyle = if (src.observationText != null) FontStyle.Normal else FontStyle.Italic
      )
      if (!finding.uncertaintyNote.isNullOrBlank()) {
        Spacer(modifier = Modifier.height(8.dp))
        InlineUncertaintyBanner(note = finding.uncertaintyNote)
      } else if (!finding.plainLanguageExplanation.isNullOrBlank()) {
        Spacer(modifier = Modifier.height(8.dp))
        PlainLanguageCallout(text = finding.plainLanguageExplanation)
      } else {
        Spacer(modifier = Modifier.height(8.dp))
        FallbackNoticeText("Plain-language explanation unavailable for this finding; original observation preserved above.")
      }
      Spacer(modifier = Modifier.height(8.dp))
      ExplanationProvenanceBlock(src.provenance, tag = "explain_rad_finding_provenance_$index")
    }
  }
}

@Composable
private fun ExplainedRadiologyImpressionCard(index: Int, impression: ExplainedRadiologyImpression) {
  val src = impression.sourceImpression
  Card(
    shape = RoundedCornerShape(14.dp),
    colors = CardDefaults.cardColors(containerColor = BrandMintSurface),
    modifier = Modifier
      .fillMaxWidth()
      .border(1.dp, SurfaceBorder, RoundedCornerShape(14.dp))
      .testTag("explain_rad_impression_card_$index")
  ) {
    Column(modifier = Modifier.padding(14.dp)) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
      ) {
        Text("Formal Impression", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = BrandTealPrimary)
        ExplanationFindingStatusChip(src.findingStatus)
      }
      Spacer(modifier = Modifier.height(6.dp))
      Text(
        text = src.impressionText ?: "Impression text not reported",
        fontSize = 13.sp,
        color = if (src.impressionText != null) BrandNavyText else BrandNavyMuted,
        fontStyle = if (src.impressionText != null) FontStyle.Normal else FontStyle.Italic
      )
      if (!impression.uncertaintyNote.isNullOrBlank()) {
        Spacer(modifier = Modifier.height(8.dp))
        InlineUncertaintyBanner(note = impression.uncertaintyNote)
      } else if (!impression.plainLanguageExplanation.isNullOrBlank()) {
        Spacer(modifier = Modifier.height(8.dp))
        PlainLanguageCallout(text = impression.plainLanguageExplanation)
      }
      Spacer(modifier = Modifier.height(8.dp))
      ExplanationProvenanceBlock(src.provenance, tag = "explain_rad_impression_provenance_$index")
    }
  }
}

@Composable
private fun ExplainedCardiologyMetricCard(index: Int, metric: ExplainedCardiologyMetric) {
  Card(
    shape = RoundedCornerShape(14.dp),
    colors = CardDefaults.cardColors(containerColor = SurfaceCard),
    modifier = Modifier
      .fillMaxWidth()
      .border(1.dp, SurfaceBorder, RoundedCornerShape(14.dp))
      .testTag("explain_cardio_metric_$index")
  ) {
    Column(modifier = Modifier.padding(14.dp)) {
      PreservedValueRow(metric.label, metric.originalValue)
      if (!metric.plainLanguageExplanation.isNullOrBlank()) {
        Spacer(modifier = Modifier.height(8.dp))
        PlainLanguageCallout(text = metric.plainLanguageExplanation)
      } else {
        Spacer(modifier = Modifier.height(8.dp))
        FallbackNoticeText("Plain-language explanation unavailable for this measurement.")
      }
      Spacer(modifier = Modifier.height(8.dp))
      ExplanationProvenanceBlock(metric.provenance, tag = "explain_cardio_provenance_$index")
    }
  }
}

@Composable
private fun ExplainedSpecialisedSectionCard(index: Int, section: ExplainedSpecialisedSection) {
  Card(
    shape = RoundedCornerShape(14.dp),
    colors = CardDefaults.cardColors(containerColor = SurfaceCard),
    modifier = Modifier
      .fillMaxWidth()
      .border(1.dp, SurfaceBorder, RoundedCornerShape(14.dp))
      .testTag("explain_specialised_section_$index")
  ) {
    Column(modifier = Modifier.padding(14.dp)) {
      Text(
        text = section.sectionTitle,
        fontWeight = FontWeight.Bold,
        fontSize = 14.sp,
        color = BrandTealPrimary
      )
      Spacer(modifier = Modifier.height(4.dp))
      Text(
        text = section.originalText,
        fontSize = 13.sp,
        color = BrandNavyText
      )
      if (!section.plainLanguageExplanation.isNullOrBlank()) {
        Spacer(modifier = Modifier.height(8.dp))
        PlainLanguageCallout(text = section.plainLanguageExplanation)
      } else {
        Spacer(modifier = Modifier.height(8.dp))
        FallbackNoticeText("Plain-language explanation unavailable for this section; verbatim text preserved above.")
      }
      Spacer(modifier = Modifier.height(8.dp))
      ExplanationProvenanceBlock(section.provenance, tag = "explain_specialised_provenance_$index")
    }
  }
}

@Composable
private fun ExplainedClinicalFactGroupCard(
  title: String,
  facts: List<ExplainedClinicalFact>,
  tagPrefix: String
) {
  SectionInfoCard(title = title) {
    facts.forEachIndexed { idx, item ->
      if (idx > 0) {
        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp), color = SurfaceBorder)
      }
      Text(
        text = item.sourceFact.text,
        fontSize = 13.sp,
        fontWeight = FontWeight.SemiBold,
        color = BrandNavyText
      )
      if (!item.plainLanguageExplanation.isNullOrBlank()) {
        Spacer(modifier = Modifier.height(6.dp))
        PlainLanguageCallout(text = item.plainLanguageExplanation)
      }
      Spacer(modifier = Modifier.height(6.dp))
      ExplanationProvenanceBlock(item.sourceFact.provenance, tag = "${tagPrefix}_provenance_$idx")
    }
  }
}

@Composable
private fun ExplainedMedicationCard(index: Int, med: ExplainedMedication) {
  val src = med.sourceMedication
  Card(
    shape = RoundedCornerShape(14.dp),
    colors = CardDefaults.cardColors(containerColor = SurfaceCard),
    modifier = Modifier
      .fillMaxWidth()
      .border(1.dp, SurfaceBorder, RoundedCornerShape(14.dp))
      .testTag("explain_medication_card_$index")
  ) {
    Column(modifier = Modifier.padding(14.dp)) {
      Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.Default.Medication, contentDescription = null, tint = BrandTealPrimary, modifier = Modifier.size(18.dp))
        Spacer(modifier = Modifier.width(6.dp))
        Text(
          text = src.drugName ?: "Medication name not reported",
          fontWeight = FontWeight.Bold,
          fontSize = 15.sp,
          color = if (src.drugName != null) BrandNavyText else BrandNavyMuted
        )
      }
      Spacer(modifier = Modifier.height(6.dp))
      PreservedValueRow("Dosage", src.dosage)
      PreservedValueRow("Frequency", src.frequency)
      PreservedValueRow("Duration", src.duration)
      PreservedValueRow("Instructions", src.instructions)

      if (!med.printedScheduleExplanation.isNullOrBlank()) {
        Spacer(modifier = Modifier.height(8.dp))
        PlainLanguageCallout(text = med.printedScheduleExplanation)
      } else {
        Spacer(modifier = Modifier.height(8.dp))
        FallbackNoticeText("Printed schedule details are shown above.")
      }
      Spacer(modifier = Modifier.height(6.dp))
      Text(
        text = med.safetyNote,
        fontSize = 11.sp,
        color = BrandNavyMuted,
        fontStyle = FontStyle.Italic
      )
      Spacer(modifier = Modifier.height(8.dp))
      ExplanationProvenanceBlock(src.provenance, tag = "explain_medication_provenance_$index")
    }
  }
}

@Composable
private fun MedicalTermsCard(terms: List<MedicalTermDefinition>) {
  SectionInfoCard(title = "Medical Terms Made Simple", tag = "explanation_terms_card") {
    terms.forEachIndexed { idx, term ->
      if (idx > 0) {
        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp), color = SurfaceBorder)
      }
      Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.AutoMirrored.Filled.MenuBook, contentDescription = null, tint = BrandTealPrimary, modifier = Modifier.size(16.dp))
        Spacer(modifier = Modifier.width(6.dp))
        Text(
          text = term.term,
          fontWeight = FontWeight.Bold,
          fontSize = 13.sp,
          color = BrandNavyText
        )
      }
      Spacer(modifier = Modifier.height(3.dp))
      Text(
        text = term.simpleDefinition,
        fontSize = 12.sp,
        lineHeight = 18.sp,
        color = BrandNavyMuted
      )
    }
  }
}

@Composable
private fun DoctorQuestionsCard(questions: List<String>) {
  Card(
    shape = RoundedCornerShape(16.dp),
    colors = CardDefaults.cardColors(containerColor = StatusAttentionHoney),
    modifier = Modifier
      .fillMaxWidth()
      .border(1.dp, Color(0xFFFDE68A), RoundedCornerShape(16.dp))
      .testTag("explanation_doctor_questions_card")
  ) {
    Column(modifier = Modifier.padding(16.dp)) {
      Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
          imageVector = Icons.Default.QuestionAnswer,
          contentDescription = null,
          tint = Color(0xFFB45309),
          modifier = Modifier.size(20.dp)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
          text = "Questions to Ask Your Doctor",
          fontWeight = FontWeight.Bold,
          fontSize = 15.sp,
          color = Color(0xFF92400E)
        )
      }
      Spacer(modifier = Modifier.height(10.dp))
      questions.forEach { q ->
        Row(
          modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp),
          verticalAlignment = Alignment.Top
        ) {
          Text(
            text = "•",
            color = StatusAttentionAmber,
            fontWeight = FontWeight.Bold,
            fontSize = 15.sp,
            modifier = Modifier.padding(end = 8.dp)
          )
          Text(
            text = q,
            color = Color(0xFF78350F),
            fontSize = 13.sp,
            lineHeight = 18.sp
          )
        }
      }
    }
  }
}

@Composable
private fun SectionInfoCard(
  title: String,
  tag: String? = null,
  content: @Composable () -> Unit
) {
  val baseModifier = Modifier
    .fillMaxWidth()
    .border(1.dp, SurfaceBorder, RoundedCornerShape(14.dp))
  val finalModifier = if (tag != null) baseModifier.testTag(tag) else baseModifier

  Card(
    shape = RoundedCornerShape(14.dp),
    colors = CardDefaults.cardColors(containerColor = SurfaceCard),
    modifier = finalModifier
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
private fun PreservedValueRow(label: String, value: String?) {
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
private fun PlainLanguageCallout(text: String) {
  Box(
    modifier = Modifier
      .fillMaxWidth()
      .clip(RoundedCornerShape(10.dp))
      .background(BrandMintBackground)
      .padding(10.dp)
  ) {
    Text(
      text = text,
      fontSize = 13.sp,
      lineHeight = 19.sp,
      color = BrandNavyText
    )
  }
}

@Composable
private fun InlineUncertaintyBanner(note: String) {
  Box(
    modifier = Modifier
      .fillMaxWidth()
      .clip(RoundedCornerShape(10.dp))
      .background(Color(0xFFEDE9FE))
      .padding(10.dp)
  ) {
    Text(
      text = note,
      fontSize = 12.sp,
      lineHeight = 17.sp,
      color = Color(0xFF5B21B6),
      fontWeight = FontWeight.Medium
    )
  }
}

@Composable
private fun FallbackNoticeText(message: String) {
  Text(
    text = message,
    fontSize = 12.sp,
    fontStyle = FontStyle.Italic,
    color = BrandNavyMuted
  )
}

@Composable
private fun ExplanationFindingStatusChip(status: FindingStatus) {
  val (bg, fg, label) = when (status) {
    FindingStatus.NORMAL -> Triple(BrandMintBackground, BrandTealPrimary, "NORMAL")
    FindingStatus.ABNORMAL -> Triple(StatusAttentionHoney, Color(0xFFB45309), "ABNORMAL")
    FindingStatus.POSITIVE -> Triple(Color(0xFFFEE2E2), Color(0xFFDC2626), "POSITIVE")
    FindingStatus.NEGATIVE -> Triple(BrandMintBackground, BrandTealPrimary, "NEGATIVE")
    FindingStatus.NOT_MENTIONED -> Triple(StatusNeutralBg, Color(0xFF64748B), "NOT MENTIONED")
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
private fun ExplanationProvenanceBlock(provenance: FieldProvenance, tag: String) {
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
        }
      }
    }
  }
}

@Composable
private fun EmptyNoticeCard(message: String) {
  SectionInfoCard(title = "Notice") {
    Text(text = message, fontSize = 13.sp, color = BrandNavyMuted)
  }
}

@Composable
private fun ExplanationLoadingState(modifier: Modifier = Modifier) {
  Column(
    modifier = modifier.testTag("explanation_loading"),
    verticalArrangement = Arrangement.Center,
    horizontalAlignment = Alignment.CenterHorizontally
  ) {
    CircularProgressIndicator(color = BrandTealPrimary)
    Spacer(modifier = Modifier.height(16.dp))
    Text(
      text = "Generating Plain-Language Explanation...",
      fontWeight = FontWeight.Bold,
      fontSize = 17.sp,
      color = BrandNavyText,
      textAlign = TextAlign.Center
    )
    Spacer(modifier = Modifier.height(6.dp))
    Text(
      text = "Translating your extracted medical findings into clear everyday terms while keeping original values intact.",
      fontSize = 13.sp,
      color = BrandNavyMuted,
      textAlign = TextAlign.Center
    )
  }
}

@Composable
private fun ExplanationConsentPromptState(
  isWaitingForConsent: Boolean,
  onRequestExplanation: () -> Unit,
  onNavigateBack: () -> Unit,
  modifier: Modifier = Modifier
) {
  Column(
    modifier = modifier.testTag("explanation_waiting_consent"),
    verticalArrangement = Arrangement.Center,
    horizontalAlignment = Alignment.CenterHorizontally
  ) {
    Box(
      contentAlignment = Alignment.Center,
      modifier = Modifier
        .size(64.dp)
        .background(BrandMintBackground, CircleShape)
    ) {
      Icon(Icons.Default.AutoAwesome, contentDescription = null, tint = BrandTealPrimary, modifier = Modifier.size(32.dp))
    }
    Spacer(modifier = Modifier.height(16.dp))
    Text(
      text = if (isWaitingForConsent) "Consent Required for AI Explanation" else "Ready to Explain Report",
      fontWeight = FontWeight.Bold,
      fontSize = 18.sp,
      color = BrandNavyText,
      textAlign = TextAlign.Center
    )
    Spacer(modifier = Modifier.height(8.dp))
    Text(
      text = "NexzyHealth needs your permission to send the extracted fields from this report to Firebase AI Logic to generate plain-language explanations (currently provided in English only).",
      fontSize = 13.sp,
      color = BrandNavyMuted,
      textAlign = TextAlign.Center
    )
    Spacer(modifier = Modifier.height(20.dp))
    Button(
      onClick = onRequestExplanation,
      colors = ButtonDefaults.buttonColors(containerColor = BrandTealPrimary),
      shape = RoundedCornerShape(28.dp),
      modifier = Modifier
        .fillMaxWidth()
        .height(48.dp)
        .testTag("btn_request_explanation")
    ) {
      Text("Review Consent & Explain", color = Color.White, fontWeight = FontWeight.Bold)
    }
    Spacer(modifier = Modifier.height(10.dp))
    OutlinedButton(
      onClick = onNavigateBack,
      shape = RoundedCornerShape(28.dp),
      modifier = Modifier
        .fillMaxWidth()
        .height(48.dp)
    ) {
      Text("Back to Extracted Data", color = BrandNavyText)
    }
  }
}

@Composable
private fun ExplanationErrorState(
  message: String,
  isRetryable: Boolean,
  onRetryClick: () -> Unit,
  onNavigateBack: () -> Unit,
  modifier: Modifier = Modifier
) {
  Column(
    modifier = modifier.testTag("explanation_error_card"),
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
      text = "Could Not Generate Explanation",
      fontWeight = FontWeight.Bold,
      fontSize = 18.sp,
      color = BrandNavyText,
      textAlign = TextAlign.Center
    )
    Spacer(modifier = Modifier.height(8.dp))
    Text(
      text = message,
      fontSize = 13.sp,
      color = Color(0xFF7F1D1D),
      textAlign = TextAlign.Center
    )
    Spacer(modifier = Modifier.height(20.dp))
    if (isRetryable) {
      Button(
        onClick = onRetryClick,
        colors = ButtonDefaults.buttonColors(containerColor = BrandTealPrimary),
        shape = RoundedCornerShape(28.dp),
        modifier = Modifier
          .fillMaxWidth()
          .height(48.dp)
          .testTag("btn_retry_explanation")
      ) {
        Icon(Icons.Default.Refresh, contentDescription = null)
        Spacer(modifier = Modifier.width(8.dp))
        Text("Retry Explanation", color = Color.White, fontWeight = FontWeight.Bold)
      }
      Spacer(modifier = Modifier.height(10.dp))
    }
    OutlinedButton(
      onClick = onNavigateBack,
      shape = RoundedCornerShape(28.dp),
      modifier = Modifier
        .fillMaxWidth()
        .height(48.dp)
        .testTag("btn_back_from_explanation_error")
    ) {
      Text("Back to Extracted Data", color = BrandNavyText)
    }
  }
}

/**
 * Explicit consent dialog specifically covering AI-generated plain-language explanations
 * from already-extracted medical fields.
 */
@Composable
private fun ExplanationAiConsentDialog(
  onConfirm: () -> Unit,
  onDismiss: () -> Unit
) {
  Dialog(
    onDismissRequest = onDismiss,
    properties = DialogProperties(dismissOnBackPress = true, dismissOnClickOutside = false)
  ) {
    Card(
      shape = RoundedCornerShape(20.dp),
      colors = CardDefaults.cardColors(containerColor = Color.White),
      modifier = Modifier
        .fillMaxWidth(0.95f)
        .testTag("dialog_explanation_consent")
    ) {
      Column(
        modifier = Modifier
          .fillMaxWidth()
          .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
      ) {
        Box(
          contentAlignment = Alignment.Center,
          modifier = Modifier
            .size(56.dp)
            .background(BrandMintBackground, CircleShape)
        ) {
          Icon(
            imageVector = Icons.Default.Lock,
            contentDescription = null,
            tint = BrandTealPrimary,
            modifier = Modifier.size(30.dp)
          )
        }
        Spacer(modifier = Modifier.height(16.dp))
        Text(
          text = "Consent for AI Report Explanation",
          fontSize = 18.sp,
          fontWeight = FontWeight.Bold,
          color = BrandNavyText,
          textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(10.dp))
        Text(
          text = "To generate plain-language explanations, medical term definitions, and doctor questions, NexzyHealth sends the structured medical text extracted from this report over encrypted TLS to Firebase AI Logic.\n\n• Explanations are AI-generated in English only and are not medically verified.\n• They do not diagnose conditions or replace your doctor.\n• Data is processed ephemerally and never used to train public models.",
          fontSize = 13.sp,
          color = BrandNavyMuted,
          lineHeight = 18.sp,
          textAlign = TextAlign.Start
        )
        Spacer(modifier = Modifier.height(20.dp))
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
          OutlinedButton(
            onClick = onDismiss,
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier
              .weight(1f)
              .height(48.dp)
              .testTag("btn_explanation_consent_cancel")
          ) {
            Text("Cancel", color = BrandNavyMuted)
          }
          Button(
            onClick = onConfirm,
            colors = ButtonDefaults.buttonColors(containerColor = BrandTealPrimary),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier
              .weight(1f)
              .height(48.dp)
              .testTag("btn_explanation_consent_confirm")
          ) {
            Text("I Agree & Explain", color = Color.White, fontWeight = FontWeight.Bold)
          }
        }
      }
    }
  }
}
