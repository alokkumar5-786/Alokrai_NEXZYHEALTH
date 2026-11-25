package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.LocalHospital
import androidx.compose.material.icons.filled.QuestionAnswer
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Shield
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.HealthPolicyExplanation
import com.example.model.HealthPolicyStrings
import com.example.model.PolicyRedFlag
import com.example.model.RedFlagSeverity
import com.example.ui.components.NexzyTopAppBar
import com.example.ui.theme.NexzyTheme
import com.example.ui.theme.TextOnDark

@Composable
fun HealthPolicyResultScreen(
  explanation: HealthPolicyExplanation,
  copy: HealthPolicyStrings,
  onBackClick: () -> Unit,
  onShareClick: () -> Unit,
  onDeleteNowClick: () -> Unit,
  modifier: Modifier = Modifier
) {
  val palette = NexzyTheme.colors

  Column(
    modifier = modifier
      .fillMaxSize()
      .background(MaterialTheme.colorScheme.background)
      .navigationBarsPadding()
      .testTag("health_policy_result_screen")
  ) {
    NexzyTopAppBar(
      title = copy.featureTitle,
      subtitle = explanation.policyBasics.planName,
      showBackButton = true,
      onBackClick = onBackClick
    )

    LazyColumn(
      modifier = Modifier
        .fillMaxSize()
        .padding(horizontal = 18.dp)
        .testTag("health_policy_result_list"),
      contentPadding = PaddingValues(top = 10.dp, bottom = 28.dp),
      verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
      // 1. Mandatory Disclaimer at TOP
      item {
        PolicyDisclaimerCard(
          text = copy.mandatoryDisclaimer,
          testTag = "policy_disclaimer_top"
        )
      }

      // 2. Key Facts Card at Top
      item {
        KeyFactsCard(
          explanation = explanation,
          copy = copy
        )
      }

      // 3. Highlighted Red Flags Card
      item {
        RedFlagsHighlightCard(
          redFlags = explanation.redFlags,
          copy = copy
        )
      }

      // 4. Expandable Card: Simple Summary (expanded by default)
      item {
        ExpandablePolicySectionCard(
          title = copy.simpleSummaryTitle,
          icon = Icons.Default.Description,
          accentColor = MaterialTheme.colorScheme.primary,
          initiallyExpanded = true,
          testTag = "card_expand_summary"
        ) {
          Text(
            text = explanation.simpleSummary,
            fontSize = 16.sp,
            lineHeight = 25.sp,
            color = MaterialTheme.colorScheme.onSurface
          )
        }
      }

      // 5. Expandable Card: What is covered
      item {
        ExpandablePolicySectionCard(
          title = copy.coveredTitle,
          icon = Icons.Default.CheckCircle,
          accentColor = palette.statusNormal,
          initiallyExpanded = false,
          testTag = "card_expand_covered"
        ) {
          if (explanation.whatIsCovered.isEmpty()) {
            EmptySectionText(copy.notMentionedFallback)
          } else {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
              explanation.whatIsCovered.forEach { item ->
                Row(verticalAlignment = Alignment.Top) {
                  Icon(
                    imageVector = Icons.Default.CheckCircle,
                    contentDescription = "Covered",
                    tint = palette.statusNormal,
                    modifier = Modifier
                      .padding(top = 2.dp)
                      .size(20.dp)
                  )
                  Spacer(modifier = Modifier.width(10.dp))
                  Text(
                    text = item,
                    fontSize = 16.sp,
                    lineHeight = 23.sp,
                    color = MaterialTheme.colorScheme.onSurface
                  )
                }
              }
            }
          }
        }
      }

      // 6. Expandable Card: What is not covered
      item {
        ExpandablePolicySectionCard(
          title = copy.notCoveredTitle,
          icon = Icons.Default.Cancel,
          accentColor = palette.statusAlert,
          initiallyExpanded = false,
          testTag = "card_expand_not_covered"
        ) {
          if (explanation.whatIsNotCovered.isEmpty()) {
            EmptySectionText(copy.notMentionedFallback)
          } else {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
              explanation.whatIsNotCovered.forEach { item ->
                Row(verticalAlignment = Alignment.Top) {
                  Icon(
                    imageVector = Icons.Default.Cancel,
                    contentDescription = "Not covered",
                    tint = palette.statusAlert,
                    modifier = Modifier
                      .padding(top = 2.dp)
                      .size(20.dp)
                  )
                  Spacer(modifier = Modifier.width(10.dp))
                  Text(
                    text = item,
                    fontSize = 16.sp,
                    lineHeight = 23.sp,
                    color = MaterialTheme.colorScheme.onSurface
                  )
                }
              }
            }
          }
        }
      }

      // 7. Expandable Card: Waiting periods
      item {
        ExpandablePolicySectionCard(
          title = copy.waitingPeriodsTitle,
          icon = Icons.Default.Schedule,
          accentColor = palette.statusAttention,
          initiallyExpanded = false,
          testTag = "card_expand_waiting_periods"
        ) {
          if (explanation.waitingPeriods.isEmpty()) {
            EmptySectionText(copy.notMentionedFallback)
          } else {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
              explanation.waitingPeriods.forEachIndexed { idx, wp ->
                if (idx > 0) HorizontalDivider(color = palette.dividerBorder)
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                  Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                  ) {
                    Text(
                      text = wp.item,
                      fontSize = 16.sp,
                      fontWeight = FontWeight.Bold,
                      color = MaterialTheme.colorScheme.onSurface,
                      modifier = Modifier.weight(1f)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Box(
                      modifier = Modifier
                        .background(palette.statusAttentionContainer, RoundedCornerShape(8.dp))
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                      Text(
                        text = wp.duration,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = palette.statusAttentionText
                      )
                    }
                  }
                  Text(
                    text = wp.meaningInSimpleWords,
                    fontSize = 15.sp,
                    lineHeight = 22.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                  )
                }
              }
            }
          }
        }
      }

      // 8. Expandable Card: Money you pay
      item {
        ExpandablePolicySectionCard(
          title = copy.moneyYouPayTitle,
          icon = Icons.Default.AccountBalanceWallet,
          accentColor = MaterialTheme.colorScheme.onSurface,
          initiallyExpanded = false,
          testTag = "card_expand_money_you_pay"
        ) {
          Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            PolicyDetailRow(copy.coPaymentLabel, explanation.moneyYouPay.coPayment)
            PolicyDetailRow(copy.deductibleLabel, explanation.moneyYouPay.deductible)
            PolicyDetailRow(copy.roomRentLabel, explanation.moneyYouPay.roomRentLimit)
            PolicyDetailRow(copy.subLimitsLabel, explanation.moneyYouPay.subLimits)
            PolicyDetailRow(copy.nonPayableLabel, explanation.moneyYouPay.nonPayableItemsNote)
          }
        }
      }

      // 9. Expandable Card: How to claim
      item {
        ExpandablePolicySectionCard(
          title = copy.howToClaimTitle,
          icon = Icons.Default.LocalHospital,
          accentColor = MaterialTheme.colorScheme.primary,
          initiallyExpanded = false,
          testTag = "card_expand_how_to_claim"
        ) {
          Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            ClaimStepsBlock(
              label = copy.cashlessStepsLabel,
              steps = explanation.howToClaim.cashlessSteps,
              fallback = copy.notMentionedFallback
            )
            HorizontalDivider(color = palette.dividerBorder)
            ClaimStepsBlock(
              label = copy.reimbursementStepsLabel,
              steps = explanation.howToClaim.reimbursementSteps,
              fallback = copy.notMentionedFallback
            )
            HorizontalDivider(color = palette.dividerBorder)
            ClaimStepsBlock(
              label = copy.documentsNeededLabel,
              steps = explanation.howToClaim.documentsNeeded,
              fallback = copy.notMentionedFallback
            )
            HorizontalDivider(color = palette.dividerBorder)
            PolicyDetailRow(copy.timeLimitsLabel, explanation.howToClaim.timeLimits)
          }
        }
      }

      // 10. Expandable Card: Questions to ask insurer
      item {
        ExpandablePolicySectionCard(
          title = copy.questionsTitle,
          icon = Icons.Default.QuestionAnswer,
          accentColor = MaterialTheme.colorScheme.primary,
          initiallyExpanded = false,
          testTag = "card_expand_questions"
        ) {
          if (explanation.questionsToAskInsurer.isEmpty()) {
            EmptySectionText(copy.notMentionedFallback)
          } else {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
              explanation.questionsToAskInsurer.forEachIndexed { index, q ->
                Text(
                  text = "${index + 1}. $q",
                  fontSize = 16.sp,
                  lineHeight = 23.sp,
                  color = MaterialTheme.colorScheme.onSurface
                )
              }
            }
          }
        }
      }

      // 11. Expandable Card: Plain-language glossary
      item {
        ExpandablePolicySectionCard(
          title = copy.glossaryTitle,
          icon = Icons.AutoMirrored.Filled.MenuBook,
          accentColor = MaterialTheme.colorScheme.primary,
          initiallyExpanded = false,
          testTag = "card_expand_glossary"
        ) {
          if (explanation.plainLanguageGlossary.isEmpty()) {
            EmptySectionText(copy.notMentionedFallback)
          } else {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
              explanation.plainLanguageGlossary.forEachIndexed { idx, entry ->
                if (idx > 0) HorizontalDivider(color = palette.dividerBorder)
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                  Text(
                    text = entry.term,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                  )
                  Text(
                    text = entry.meaning,
                    fontSize = 15.sp,
                    lineHeight = 22.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                  )
                }
              }
            }
          }
        }
      }

      // 12. Mandatory Disclaimer at BOTTOM
      item {
        PolicyDisclaimerCard(
          text = copy.mandatoryDisclaimer,
          testTag = "policy_disclaimer_bottom"
        )
      }

      // 13. Action Buttons: Share summary & Delete my document now
      item {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
          Button(
            onClick = onShareClick,
            colors = ButtonDefaults.buttonColors(
              containerColor = MaterialTheme.colorScheme.primary,
              contentColor = MaterialTheme.colorScheme.onPrimary
            ),
            shape = RoundedCornerShape(28.dp),
            modifier = Modifier
              .fillMaxWidth()
              .height(54.dp)
              .testTag("btn_share_policy_summary")
          ) {
            Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(20.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text(
              text = copy.shareSummaryButton,
              fontSize = 17.sp,
              fontWeight = FontWeight.Bold
            )
          }

          OutlinedButton(
            onClick = onDeleteNowClick,
            border = BorderStroke(1.5.dp, palette.statusAlert),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = palette.statusAlert),
            shape = RoundedCornerShape(28.dp),
            modifier = Modifier
              .fillMaxWidth()
              .height(54.dp)
              .testTag("btn_delete_policy_now")
          ) {
            Icon(Icons.Default.DeleteForever, contentDescription = null, modifier = Modifier.size(22.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text(
              text = copy.deleteNowButton,
              fontSize = 17.sp,
              fontWeight = FontWeight.Bold
            )
          }
        }
      }
    }
  }
}

@Composable
private fun PolicyDisclaimerCard(
  text: String,
  testTag: String
) {
  val palette = NexzyTheme.colors

  Card(
    shape = RoundedCornerShape(14.dp),
    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    border = BorderStroke(1.dp, palette.dividerBorder),
    modifier = Modifier
      .fillMaxWidth()
      .testTag(testTag)
  ) {
    Row(
      modifier = Modifier.padding(14.dp),
      verticalAlignment = Alignment.Top
    ) {
      Icon(
        imageVector = Icons.Default.Info,
        contentDescription = null,
        tint = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier
          .padding(top = 2.dp)
          .size(20.dp)
      )
      Spacer(modifier = Modifier.width(10.dp))
      Text(
        text = text,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        color = MaterialTheme.colorScheme.onSurface
      )
    }
  }
}

@Composable
private fun KeyFactsCard(
  explanation: HealthPolicyExplanation,
  copy: HealthPolicyStrings
) {
  val basics = explanation.policyBasics
  Card(
    shape = RoundedCornerShape(18.dp),
    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
    border = BorderStroke(1.25.dp, MaterialTheme.colorScheme.outlineVariant),
    modifier = Modifier
      .fillMaxWidth()
      .testTag("card_policy_key_facts")
  ) {
    Column(
      modifier = Modifier
        .fillMaxWidth()
        .padding(16.dp),
      verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
      Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
          contentAlignment = Alignment.Center,
          modifier = Modifier
            .size(38.dp)
            .background(MaterialTheme.colorScheme.surface, CircleShape)
        ) {
          Icon(
            imageVector = Icons.Default.Shield,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(22.dp)
          )
        }
        Spacer(modifier = Modifier.width(10.dp))
        Text(
          text = copy.keyFactsTitle,
          fontSize = 19.sp,
          fontWeight = FontWeight.Bold,
          color = MaterialTheme.colorScheme.onPrimaryContainer
        )
      }

      HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
      ) {
        KeyFactHighlightTile(
          label = copy.sumInsuredLabel,
          value = basics.sumInsured,
          modifier = Modifier.weight(1f)
        )
        KeyFactHighlightTile(
          label = copy.premiumLabel,
          value = basics.premium,
          modifier = Modifier.weight(1f)
        )
      }

      PolicyDetailRow(copy.policyPeriodLabel, basics.policyPeriod)
      PolicyDetailRow(copy.membersCoveredLabel, basics.membersCovered)
      PolicyDetailRow(copy.insurerLabel, basics.insurerName)
      PolicyDetailRow(copy.planLabel, basics.planName)
      PolicyDetailRow(copy.policyTypeLabel, basics.policyType)
    }
  }
}

@Composable
private fun KeyFactHighlightTile(
  label: String,
  value: String,
  modifier: Modifier = Modifier
) {
  val palette = NexzyTheme.colors

  Card(
    shape = RoundedCornerShape(12.dp),
    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    border = BorderStroke(1.dp, palette.dividerBorder),
    modifier = modifier
  ) {
    Column(modifier = Modifier.padding(12.dp)) {
      Text(
        text = label,
        fontSize = 13.sp,
        fontWeight = FontWeight.Medium,
        color = MaterialTheme.colorScheme.onSurfaceVariant
      )
      Spacer(modifier = Modifier.height(4.dp))
      Text(
        text = value,
        fontSize = 17.sp,
        fontWeight = FontWeight.ExtraBold,
        color = MaterialTheme.colorScheme.onSurface
      )
    }
  }
}

@Composable
private fun RedFlagsHighlightCard(
  redFlags: List<PolicyRedFlag>,
  copy: HealthPolicyStrings
) {
  val palette = NexzyTheme.colors

  Card(
    shape = RoundedCornerShape(18.dp),
    colors = CardDefaults.cardColors(containerColor = palette.statusAttentionContainer),
    border = BorderStroke(1.25.dp, palette.statusAttentionBorder),
    modifier = Modifier
      .fillMaxWidth()
      .testTag("card_policy_red_flags")
  ) {
    Column(
      modifier = Modifier
        .fillMaxWidth()
        .padding(16.dp),
      verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
      Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
          imageVector = Icons.Default.Flag,
          contentDescription = null,
          tint = palette.statusAlert,
          modifier = Modifier.size(24.dp)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
          text = copy.redFlagsTitle,
          fontSize = 19.sp,
          fontWeight = FontWeight.Bold,
          color = MaterialTheme.colorScheme.onSurface
        )
      }

      if (redFlags.isEmpty()) {
        Text(
          text = copy.noRedFlagsMessage,
          fontSize = 15.sp,
          lineHeight = 22.sp,
          color = MaterialTheme.colorScheme.onSurface
        )
      } else {
        redFlags.forEachIndexed { index, flag ->
          RedFlagItemCard(flag = flag, index = index, copy = copy)
        }
      }
    }
  }
}

@Composable
private fun RedFlagItemCard(
  flag: PolicyRedFlag,
  index: Int,
  copy: HealthPolicyStrings
) {
  val palette = NexzyTheme.colors

  val (bgColor, badgeColor, textColor, label) = when (flag.severity) {
    RedFlagSeverity.HIGH -> Quadruple(
      palette.statusAlertContainer,
      palette.statusAlert,
      palette.statusAlertText,
      copy.severityHigh
    )
    RedFlagSeverity.MEDIUM -> Quadruple(
      palette.statusAttentionContainer,
      palette.statusAttention,
      palette.statusAttentionText,
      copy.severityMedium
    )
    RedFlagSeverity.LOW -> Quadruple(
      palette.statusInfoContainer,
      palette.statusInfoAccent,
      palette.statusInfoText,
      copy.severityLow
    )
  }

  Card(
    shape = RoundedCornerShape(12.dp),
    colors = CardDefaults.cardColors(containerColor = bgColor),
    border = BorderStroke(1.dp, badgeColor.copy(alpha = 0.55f)),
    modifier = Modifier
      .fillMaxWidth()
      .testTag("red_flag_${flag.severity.wireValue}_$index")
  ) {
    Column(
      modifier = Modifier.padding(12.dp),
      verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
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
            imageVector = Icons.Default.WarningAmber,
            contentDescription = label,
            tint = badgeColor,
            modifier = Modifier.size(20.dp)
          )
          Spacer(modifier = Modifier.width(6.dp))
          Text(
            text = flag.title,
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold,
            color = textColor
          )
        }
        Spacer(modifier = Modifier.width(8.dp))
        Box(
          modifier = Modifier
            .background(badgeColor, RoundedCornerShape(8.dp))
            .padding(horizontal = 8.dp, vertical = 3.dp)
        ) {
          Text(
            text = label,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            color = TextOnDark
          )
        }
      }
      Text(
        text = flag.simpleExplanation,
        fontSize = 15.sp,
        lineHeight = 22.sp,
        color = textColor
      )
    }
  }
}

@Composable
private fun ExpandablePolicySectionCard(
  title: String,
  icon: ImageVector,
  accentColor: Color,
  initiallyExpanded: Boolean,
  testTag: String,
  content: @Composable ColumnScope.() -> Unit
) {
  var expanded by rememberSaveable(testTag) { mutableStateOf(initiallyExpanded) }
  val palette = NexzyTheme.colors

  Card(
    shape = RoundedCornerShape(16.dp),
    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    border = BorderStroke(1.dp, palette.dividerBorder),
    modifier = Modifier
      .fillMaxWidth()
      .testTag(testTag)
  ) {
    Column(modifier = Modifier.fillMaxWidth()) {
      Row(
        modifier = Modifier
          .fillMaxWidth()
          .defaultMinSize(minHeight = 56.dp)
          .clip(RoundedCornerShape(16.dp))
          .clickable { expanded = !expanded }
          .padding(horizontal = 16.dp, vertical = 14.dp)
          .testTag("${testTag}_header"),
        verticalAlignment = Alignment.CenterVertically
      ) {
        Icon(
          imageVector = icon,
          contentDescription = null,
          tint = accentColor,
          modifier = Modifier.size(24.dp)
        )
        Spacer(modifier = Modifier.width(12.dp))
        Text(
          text = title,
          fontSize = 18.sp,
          fontWeight = FontWeight.Bold,
          color = MaterialTheme.colorScheme.onSurface,
          modifier = Modifier.weight(1f)
        )
        Icon(
          imageVector = if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
          contentDescription = if (expanded) "Collapse" else "Expand",
          tint = MaterialTheme.colorScheme.onSurfaceVariant,
          modifier = Modifier.size(26.dp)
        )
      }

      AnimatedVisibility(visible = expanded) {
        Column(
          modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface)
            .padding(horizontal = 16.dp, vertical = 14.dp)
        ) {
          content()
        }
      }
    }
  }
}

@Composable
private fun PolicyDetailRow(
  label: String,
  value: String
) {
  Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
    Text(
      text = label,
      fontSize = 14.sp,
      fontWeight = FontWeight.Bold,
      color = MaterialTheme.colorScheme.onSurfaceVariant
    )
    Text(
      text = value,
      fontSize = 16.sp,
      lineHeight = 22.sp,
      color = MaterialTheme.colorScheme.onSurface
    )
  }
}

@Composable
private fun ClaimStepsBlock(
  label: String,
  steps: List<String>,
  fallback: String
) {
  Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
    Text(
      text = label,
      fontSize = 16.sp,
      fontWeight = FontWeight.Bold,
      color = MaterialTheme.colorScheme.onSurface
    )
    if (steps.isEmpty()) {
      EmptySectionText(fallback)
    } else {
      steps.forEachIndexed { idx, step ->
        Text(
          text = "${idx + 1}. $step",
          fontSize = 15.sp,
          lineHeight = 22.sp,
          color = MaterialTheme.colorScheme.onSurface
        )
      }
    }
  }
}

@Composable
private fun EmptySectionText(text: String) {
  Text(
    text = text,
    fontSize = 15.sp,
    color = MaterialTheme.colorScheme.onSurfaceVariant
  )
}

private data class Quadruple<A, B, C, D>(
  val first: A,
  val second: B,
  val third: C,
  val fourth: D
)
