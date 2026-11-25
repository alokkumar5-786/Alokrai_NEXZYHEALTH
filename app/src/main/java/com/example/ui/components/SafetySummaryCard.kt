package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.MedicalInformation
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.FieldProvenance
import com.example.model.ProvenanceAvailability
import com.example.model.ReportSafetySummary
import com.example.model.SafetyBucket
import com.example.model.SafetyFindingAlert
import com.example.model.UnavailableComparisonNotice
import com.example.ui.theme.NexzyExtendedColors
import com.example.ui.theme.NexzyTheme

private data class BucketVisualStyle(
  val containerBg: Color,
  val border: Color,
  val titleColor: Color,
  val bodyColor: Color,
  val badgeBg: Color,
  val badgeFg: Color,
  val icon: ImageVector
)

private fun styleForBucket(bucket: SafetyBucket, palette: NexzyExtendedColors): BucketVisualStyle = when (bucket) {
  SafetyBucket.EXTRACTED_CRITICAL_FLAG,
  SafetyBucket.AI_EXTRACTED_URGENT_WORDING -> BucketVisualStyle(
    containerBg = palette.statusAlertContainer,
    border = palette.statusAlertBorder,
    titleColor = palette.statusAlertText,
    bodyColor = palette.statusAlertText,
    badgeBg = palette.statusAlertContainer,
    badgeFg = palette.statusAlert,
    icon = Icons.Default.ErrorOutline
  )

  SafetyBucket.RULE_VALIDATED_ALERT,
  SafetyBucket.EXTRACTED_ABNORMAL_FINDING,
  SafetyBucket.OUTSIDE_EXTRACTED_REFERENCE_RANGE -> BucketVisualStyle(
    containerBg = palette.statusAttentionContainer,
    border = palette.statusAttentionBorder,
    titleColor = palette.statusAttentionText,
    bodyColor = palette.statusAttentionText,
    badgeBg = palette.statusAttentionContainer,
    badgeFg = palette.statusAttention,
    icon = Icons.Default.WarningAmber
  )

  SafetyBucket.EXTRACTED_POSITIVE_FINDING -> BucketVisualStyle(
    containerBg = palette.statusInfoContainer,
    border = palette.statusInfoBorder,
    titleColor = palette.statusInfoText,
    bodyColor = palette.statusInfoText,
    badgeBg = palette.statusInfoContainer,
    badgeFg = palette.statusInfoAccent,
    icon = Icons.Default.MedicalInformation
  )

  SafetyBucket.EXTRACTED_CARE_INSTRUCTION -> BucketVisualStyle(
    containerBg = palette.statusNeutralContainer,
    border = palette.statusNeutralBorder,
    titleColor = palette.statusNeutralText,
    bodyColor = palette.statusNeutral,
    badgeBg = palette.statusNeutralContainer,
    badgeFg = palette.statusNeutral,
    icon = Icons.Default.Info
  )

  SafetyBucket.UNCERTAIN_OR_UNREADABLE -> BucketVisualStyle(
    containerBg = palette.statusInfoContainer,
    border = palette.statusInfoBorder,
    titleColor = palette.statusInfoText,
    bodyColor = palette.statusNeutral,
    badgeBg = palette.statusInfoBorder.copy(alpha = 0.4f),
    badgeFg = palette.statusInfoText,
    icon = Icons.Default.WarningAmber
  )

  SafetyBucket.INCOMPLETE_ASSESSMENT -> BucketVisualStyle(
    containerBg = palette.statusNeutralContainer,
    border = palette.statusNeutralBorder,
    titleColor = palette.statusNeutralText,
    bodyColor = palette.statusNeutral,
    badgeBg = palette.statusNeutralContainer,
    badgeFg = palette.statusNeutral,
    icon = Icons.Default.Info
  )

  SafetyBucket.NO_ABNORMALITY_IDENTIFIED_IN_EXTRACTED_FINDINGS -> BucketVisualStyle(
    containerBg = palette.statusNormalContainer,
    border = palette.statusNormalBorder,
    titleColor = palette.statusNormalText,
    bodyColor = palette.statusNormalText,
    badgeBg = palette.statusNormalContainer,
    badgeFg = palette.statusNormal,
    icon = Icons.Default.CheckCircle
  )
}

/**
 * Displays the Task 3E deterministic safety summary for an extracted report, or an explicit
 * "Safety assessment unavailable" card when [safetySummary] is null so missing state is never
 * silently hidden.
 */
@Composable
fun SafetySummaryCard(
  safetySummary: ReportSafetySummary?,
  modifier: Modifier = Modifier
) {
  if (safetySummary == null) {
    SafetyAssessmentUnavailableCard(modifier = modifier)
    return
  }

  val palette = NexzyTheme.colors
  val style = styleForBucket(safetySummary.overallClassification, palette)

  Card(
    shape = RoundedCornerShape(16.dp),
    colors = CardDefaults.cardColors(containerColor = style.containerBg),
    modifier = modifier
      .fillMaxWidth()
      .border(1.dp, style.border, RoundedCornerShape(16.dp))
      .testTag("safety_summary_card")
  ) {
    Column(modifier = Modifier.padding(16.dp)) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
      ) {
        Row(
          verticalAlignment = Alignment.CenterVertically,
          modifier = Modifier.weight(1f)
        ) {
          Icon(
            imageVector = style.icon,
            contentDescription = safetySummary.overallClassification.displayLabel,
            tint = style.titleColor,
            modifier = Modifier.size(22.dp)
          )
          Spacer(modifier = Modifier.width(8.dp))
          Text(
            text = safetySummary.overallHeadline,
            fontWeight = FontWeight.Bold,
            fontSize = 15.sp,
            color = style.titleColor,
            modifier = Modifier.testTag("safety_overall_headline")
          )
        }
      }

      Spacer(modifier = Modifier.height(6.dp))

      Box(
        modifier = Modifier
          .clip(RoundedCornerShape(8.dp))
          .background(MaterialTheme.colorScheme.surface)
          .border(1.dp, style.border, RoundedCornerShape(8.dp))
          .padding(horizontal = 8.dp, vertical = 4.dp)
          .testTag("safety_overall_bucket_chip")
      ) {
        Text(
          text = safetySummary.overallClassification.displayLabel,
          fontSize = 11.sp,
          fontWeight = FontWeight.Bold,
          color = style.badgeFg
        )
      }

      Spacer(modifier = Modifier.height(8.dp))

      Text(
        text = safetySummary.overallSummaryMessage,
        fontSize = 13.sp,
        lineHeight = 19.sp,
        color = style.bodyColor
      )

      Spacer(modifier = Modifier.height(8.dp))

      Text(
        text = "Next step: ${safetySummary.actionGuidance.summaryLabel}",
        fontSize = 12.sp,
        fontWeight = FontWeight.SemiBold,
        color = style.titleColor
      )

      if (safetySummary.alerts.isNotEmpty()) {
        Spacer(modifier = Modifier.height(12.dp))
        HorizontalDivider(color = style.border)
        Spacer(modifier = Modifier.height(10.dp))
        Text(
          text = "Safety Findings & Notices (${safetySummary.alerts.size})",
          fontWeight = FontWeight.Bold,
          fontSize = 13.sp,
          color = style.titleColor
        )
        Spacer(modifier = Modifier.height(8.dp))
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
          safetySummary.alerts.forEachIndexed { index, alert ->
            SafetyAlertItemRow(index = index, alert = alert)
          }
        }
      }

      if (safetySummary.unavailableComparisons.isNotEmpty()) {
        Spacer(modifier = Modifier.height(12.dp))
        HorizontalDivider(color = style.border)
        Spacer(modifier = Modifier.height(10.dp))
        UnavailableComparisonsSection(notices = safetySummary.unavailableComparisons)
      }

      if (safetySummary.careInstructions.isNotEmpty()) {
        Spacer(modifier = Modifier.height(12.dp))
        HorizontalDivider(color = style.border)
        Spacer(modifier = Modifier.height(10.dp))
        CareInstructionsSection(instructions = safetySummary.careInstructions)
      }

      Spacer(modifier = Modifier.height(12.dp))
      Box(
        modifier = Modifier
          .fillMaxWidth()
          .clip(RoundedCornerShape(10.dp))
          .background(MaterialTheme.colorScheme.surface)
          .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(10.dp))
          .padding(10.dp)
          .testTag("safety_scope_limitation_notice")
      ) {
        Row(verticalAlignment = Alignment.Top) {
          Icon(
            imageVector = Icons.Default.VerifiedUser,
            contentDescription = "Safety assessment scope limitation",
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(16.dp)
          )
          Spacer(modifier = Modifier.width(6.dp))
          Column {
            Text(
              text = safetySummary.scopeLimitationNotice,
              fontSize = 11.sp,
              lineHeight = 15.sp,
              color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
              text = "Ruleset v${safetySummary.rulesetVersion} • Active clinical rules: ${safetySummary.activeValidatedRuleCount}",
              fontSize = 10.sp,
              color = MaterialTheme.colorScheme.onSurfaceVariant
            )
          }
        }
      }
    }
  }
}

@Composable
fun SafetyAssessmentUnavailableCard(modifier: Modifier = Modifier) {
  val palette = NexzyTheme.colors
  Card(
    shape = RoundedCornerShape(14.dp),
    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    modifier = modifier
      .fillMaxWidth()
      .border(1.dp, palette.statusNeutralBorder, RoundedCornerShape(14.dp))
      .testTag("safety_assessment_unavailable_card")
  ) {
    Row(
      modifier = Modifier.padding(14.dp),
      verticalAlignment = Alignment.Top
    ) {
      Icon(
        imageVector = Icons.Default.Info,
        contentDescription = "Safety assessment unavailable",
        tint = palette.statusNeutral,
        modifier = Modifier.size(20.dp)
      )
      Spacer(modifier = Modifier.width(10.dp))
      Column {
        Text(
          text = "Safety Assessment Unavailable",
          fontWeight = FontWeight.Bold,
          fontSize = 14.sp,
          color = MaterialTheme.colorScheme.onSurface
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
          text = "An automated safety summary is unavailable for this view. Review all extracted values and your original report with your doctor.",
          fontSize = 12.sp,
          lineHeight = 17.sp,
          color = MaterialTheme.colorScheme.onSurfaceVariant
        )
      }
    }
  }
}

/**
 * Compact non-blocking banner shown on non-Success states of ReportExplanationScreen
 * (e.g., Idle, WaitingForConsent, Loading, Error) so critical/urgent, incomplete, or
 * assessment-unavailable notices remain visible regardless of AI explanation consent or network state.
 */
@Composable
fun SafetyCompactNoticeBanner(
  safetySummary: ReportSafetySummary?,
  modifier: Modifier = Modifier
) {
  val palette = NexzyTheme.colors
  if (safetySummary == null) {
    Card(
      shape = RoundedCornerShape(12.dp),
      colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
      modifier = modifier
        .fillMaxWidth()
        .border(1.dp, palette.statusNeutralBorder, RoundedCornerShape(12.dp))
        .testTag("safety_compact_notice_banner")
    ) {
      Row(
        modifier = Modifier.padding(12.dp),
        verticalAlignment = Alignment.Top
      ) {
        Icon(
          imageVector = Icons.Default.Info,
          contentDescription = "Safety assessment unavailable",
          tint = palette.statusNeutral,
          modifier = Modifier.size(18.dp)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Column {
          Text(
            text = "Safety Assessment Unavailable",
            fontWeight = FontWeight.Bold,
            fontSize = 13.sp,
            color = MaterialTheme.colorScheme.onSurface
          )
          Spacer(modifier = Modifier.height(2.dp))
          Text(
            text = "Automated safety summary is unavailable. Review your original report with your doctor.",
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
          )
        }
      }
    }
    return
  }

  val style = styleForBucket(safetySummary.overallClassification, palette)
  Card(
    shape = RoundedCornerShape(12.dp),
    colors = CardDefaults.cardColors(containerColor = style.containerBg),
    modifier = modifier
      .fillMaxWidth()
      .border(1.dp, style.border, RoundedCornerShape(12.dp))
      .testTag("safety_compact_notice_banner")
  ) {
    Row(
      modifier = Modifier.padding(12.dp),
      verticalAlignment = Alignment.Top
    ) {
      Icon(
        imageVector = style.icon,
        contentDescription = safetySummary.overallClassification.displayLabel,
        tint = style.titleColor,
        modifier = Modifier.size(18.dp)
      )
      Spacer(modifier = Modifier.width(8.dp))
      Column {
        Text(
          text = safetySummary.overallHeadline,
          fontWeight = FontWeight.Bold,
          fontSize = 13.sp,
          color = style.titleColor
        )
        Spacer(modifier = Modifier.height(2.dp))
        Text(
          text = if (safetySummary.overallClassification == SafetyBucket.INCOMPLETE_ASSESSMENT) {
            "${safetySummary.actionGuidance.summaryLabel} • Automated safety check incomplete."
          } else {
            safetySummary.actionGuidance.summaryLabel
          },
          fontSize = 12.sp,
          color = style.bodyColor
        )
      }
    }
  }
}

/**
 * Inline badge rendered on individual extracted item cards when Task 3E detected a finding
 * notice or unavailable numeric comparison.
 */
@Composable
fun SafetyInlineItemNotice(
  alert: SafetyFindingAlert?,
  unavailableNotice: UnavailableComparisonNotice?,
  modifier: Modifier = Modifier
) {
  if (alert == null && unavailableNotice == null) return

  val palette = NexzyTheme.colors
  Column(
    modifier = modifier.fillMaxWidth(),
    verticalArrangement = Arrangement.spacedBy(6.dp)
  ) {
    if (alert != null) {
      val style = styleForBucket(alert.bucket, palette)
      Box(
        modifier = Modifier
          .fillMaxWidth()
          .clip(RoundedCornerShape(8.dp))
          .background(style.containerBg)
          .border(1.dp, style.border, RoundedCornerShape(8.dp))
          .padding(8.dp)
      ) {
        Column {
          Text(
            text = "${alert.bucket.displayLabel} • ${alert.evidenceOrigin.displayLabel}",
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            color = style.titleColor
          )
          if (!alert.conflictReason.isNullOrBlank()) {
            Spacer(modifier = Modifier.height(2.dp))
            Text(
              text = alert.conflictReason,
              fontSize = 11.sp,
              color = style.bodyColor
            )
          }
        }
      }
    }

    if (unavailableNotice != null) {
      Box(
        modifier = Modifier
          .fillMaxWidth()
          .clip(RoundedCornerShape(8.dp))
          .background(palette.statusInfoContainer)
          .border(1.dp, palette.statusInfoBorder, RoundedCornerShape(8.dp))
          .padding(8.dp)
      ) {
        Text(
          text = "Range comparison unavailable: ${unavailableNotice.reason}",
          fontSize = 11.sp,
          color = palette.statusInfoText,
          fontWeight = FontWeight.Medium
        )
      }
    }
  }
}

@Composable
private fun SafetyAlertItemRow(index: Int, alert: SafetyFindingAlert) {
  val palette = NexzyTheme.colors
  val style = styleForBucket(alert.bucket, palette)
  Box(
    modifier = Modifier
      .fillMaxWidth()
      .clip(RoundedCornerShape(12.dp))
      .background(MaterialTheme.colorScheme.surface)
      .border(1.dp, style.border, RoundedCornerShape(12.dp))
      .padding(12.dp)
      .testTag("safety_alert_item_$index")
  ) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
      ) {
        Text(
          text = alert.sourceLabel,
          fontWeight = FontWeight.Bold,
          fontSize = 13.sp,
          color = MaterialTheme.colorScheme.onSurface,
          modifier = Modifier.weight(1f)
        )
        Spacer(modifier = Modifier.width(6.dp))
        Box(
          modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(style.badgeBg)
            .padding(horizontal = 6.dp, vertical = 2.dp)
        ) {
          Text(
            text = alert.bucket.displayLabel,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            color = style.badgeFg
          )
        }
      }

      Text(
        text = alert.title,
        fontWeight = FontWeight.SemiBold,
        fontSize = 12.sp,
        color = style.titleColor
      )

      Text(
        text = "Evidence origin: ${alert.evidenceOrigin.displayLabel}",
        fontSize = 11.sp,
        color = MaterialTheme.colorScheme.onSurfaceVariant
      )

      if (!alert.reportedValueSummary.isNullOrBlank()) {
        Text(
          text = "Extracted entry: ${alert.reportedValueSummary}",
          fontSize = 12.sp,
          fontWeight = FontWeight.Medium,
          color = MaterialTheme.colorScheme.onSurface
        )
      }

      Text(
        text = alert.explanationText,
        fontSize = 12.sp,
        lineHeight = 17.sp,
        color = MaterialTheme.colorScheme.onSurfaceVariant
      )

      if (!alert.conflictReason.isNullOrBlank()) {
        Text(
          text = alert.conflictReason,
          fontSize = 11.sp,
          fontWeight = FontWeight.SemiBold,
          color = style.titleColor
        )
      }

      if (alert.ruleReference != null) {
        Text(
          text = "Rule: ${alert.ruleReference.ruleId} v${alert.ruleReference.ruleVersion} (${alert.ruleReference.clinicalEvidenceRef})",
          fontSize = 11.sp,
          color = MaterialTheme.colorScheme.onSurfaceVariant
        )
      }

      SafetyProvenanceLine(provenance = alert.provenance)
    }
  }
}

@Composable
private fun UnavailableComparisonsSection(notices: List<UnavailableComparisonNotice>) {
  val palette = NexzyTheme.colors
  Column(
    modifier = Modifier
      .fillMaxWidth()
      .testTag("safety_unavailable_comparisons_section"),
    verticalArrangement = Arrangement.spacedBy(6.dp)
  ) {
    Text(
      text = "Unavailable Numeric Range Comparisons (${notices.size})",
      fontWeight = FontWeight.Bold,
      fontSize = 13.sp,
      color = palette.statusInfoText
    )
    notices.forEachIndexed { idx, notice ->
      Box(
        modifier = Modifier
          .fillMaxWidth()
          .clip(RoundedCornerShape(10.dp))
          .background(MaterialTheme.colorScheme.surface)
          .border(1.dp, palette.statusInfoBorder, RoundedCornerShape(10.dp))
          .padding(10.dp)
          .testTag("safety_unavailable_comparison_$idx")
      ) {
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
          Text(
            text = notice.itemLabel,
            fontWeight = FontWeight.Bold,
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurface
          )
          Text(
            text = notice.reason,
            fontSize = 11.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
          )
          SafetyProvenanceLine(provenance = notice.provenance)
        }
      }
    }
  }
}

@Composable
private fun CareInstructionsSection(instructions: List<SafetyFindingAlert>) {
  Column(
    modifier = Modifier
      .fillMaxWidth()
      .testTag("safety_care_instructions_section"),
    verticalArrangement = Arrangement.spacedBy(6.dp)
  ) {
    Text(
      text = "Extracted Care Instructions (${instructions.size})",
      fontWeight = FontWeight.Bold,
      fontSize = 13.sp,
      color = MaterialTheme.colorScheme.onSurface
    )
    Text(
      text = "These care instructions were extracted from your clinical document. Follow them only as directed on your original record.",
      fontSize = 11.sp,
      color = MaterialTheme.colorScheme.onSurfaceVariant
    )
    instructions.forEachIndexed { idx, inst ->
      Box(
        modifier = Modifier
          .fillMaxWidth()
          .clip(RoundedCornerShape(10.dp))
          .background(MaterialTheme.colorScheme.surface)
          .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(10.dp))
          .padding(10.dp)
          .testTag("safety_care_instruction_$idx")
      ) {
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
          Text(
            text = inst.reportedValueSummary ?: inst.sourceLabel,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurface
          )
          Text(
            text = "Evidence origin: ${inst.evidenceOrigin.displayLabel}",
            fontSize = 10.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
          )
          SafetyProvenanceLine(provenance = inst.provenance)
        }
      }
    }
  }
}

@Composable
private fun SafetyProvenanceLine(provenance: FieldProvenance) {
  if (provenance.availability == ProvenanceAvailability.UNAVAILABLE) {
    Text(
      text = "Provenance: Unavailable (not extracted from document)",
      fontSize = 11.sp,
      fontStyle = FontStyle.Italic,
      color = MaterialTheme.colorScheme.onSurfaceVariant
    )
  } else {
    val pageText = provenance.pageNumber?.let { "Page $it" } ?: "Page unavailable"
    val snippetText = provenance.sourceSnippet?.let { "Snippet (verify original report): \"$it\"" }
      ?: "Reference snippet unavailable"
    Text(
      text = "$pageText • $snippetText",
      fontSize = 11.sp,
      color = MaterialTheme.colorScheme.onSurfaceVariant
    )
  }
}
