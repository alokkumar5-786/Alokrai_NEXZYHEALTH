package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.PlayCircleFilled
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.model.ReportHistoryItem
import com.example.ui.components.NexzyTopAppBar
import com.example.ui.components.StatusBadge
import com.example.ui.theme.MistBlue
import com.example.ui.theme.NexzyTheme
import com.example.ui.theme.PaleBlue
import com.example.ui.theme.TextOnDark

@Composable
fun HomeScreen(
  userName: String = "Priya",
  selectedLanguageName: String = "English",
  reportsAnalysedCount: Int = 14,
  freeCreditsRemaining: Int = 3,
  recentReports: List<ReportHistoryItem>,
  onScanReportClick: () -> Unit,
  onUploadReportClick: () -> Unit,
  onSampleExplanationClick: () -> Unit,
  onLanguageClick: () -> Unit,
  onHelpClick: () -> Unit,
  onViewAllReportsClick: () -> Unit,
  onReportItemClick: (ReportHistoryItem) -> Unit,
  onSubscriptionClick: () -> Unit,
  modifier: Modifier = Modifier,
  onExplainHealthPolicyClick: () -> Unit = {},
  onReportReaderGuideClick: () -> Unit = {}
) {
  val palette = NexzyTheme.colors

  Column(
    modifier = modifier
      .fillMaxSize()
      .background(MaterialTheme.colorScheme.background)
  ) {
    NexzyTopAppBar(
      title = "NexzyHealth",
      subtitle = "Understand Your Health Report. Simply.",
      selectedLanguageName = selectedLanguageName,
      onLanguageClick = onLanguageClick,
      onHelpClick = onHelpClick
    )

    LazyColumn(
      modifier = Modifier
        .fillMaxSize()
        .padding(horizontal = 18.dp),
      contentPadding = PaddingValues(top = 8.dp, bottom = 20.dp),
      verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
      item {
        // Greeting & Caregiver Context
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically
        ) {
          Column(modifier = Modifier.weight(1f)) {
            Text(
              text = "Hello, $userName \uD83D\uDC4B",
              style = MaterialTheme.typography.headlineSmall,
              color = MaterialTheme.colorScheme.onBackground,
              fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
              text = "Managing health reports for 3 family members",
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant
            )
          }

          Spacer(modifier = Modifier.width(8.dp))

          Box(
            modifier = Modifier
              .clip(RoundedCornerShape(12.dp))
              .background(MaterialTheme.colorScheme.primaryContainer)
              .clickable(onClick = onLanguageClick)
              .defaultMinSize(minHeight = 40.dp)
              .padding(horizontal = 12.dp, vertical = 8.dp),
            contentAlignment = Alignment.Center
          ) {
            Text(
              text = "\uD83C\uDF10 $selectedLanguageName",
              color = MaterialTheme.colorScheme.primary,
              fontWeight = FontWeight.Bold,
              fontSize = 12.sp
            )
          }
        }
      }

      // Counters Row
      item {
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
          CounterCard(
            title = "Reports Analysed",
            count = "$reportsAnalysedCount",
            subtitle = "Plain explanations created",
            icon = Icons.Default.Speed,
            badgeColor = MaterialTheme.colorScheme.primary,
            badgeBgColor = MaterialTheme.colorScheme.primaryContainer,
            bgColor = MaterialTheme.colorScheme.surface,
            borderColor = palette.dividerBorder,
            modifier = Modifier.weight(1f),
            testTag = "counter_reports_analysed"
          )
          CounterCard(
            title = "Free Remaining",
            count = "$freeCreditsRemaining",
            subtitle = "Tap to view plans",
            icon = Icons.Default.LockOpen,
            badgeColor = palette.statusAttention,
            badgeBgColor = palette.statusAttentionContainer,
            bgColor = MaterialTheme.colorScheme.surface,
            borderColor = palette.dividerBorder,
            modifier = Modifier.weight(1f),
            testTag = "counter_free_remaining",
            onClick = onSubscriptionClick
          )
        }
      }

      // Two Prominent Primary Actions: A. Scan My Report, B. Upload My Report
      item {
        Text(
          text = "Analyze A Medical Report",
          style = MaterialTheme.typography.titleMedium,
          color = MaterialTheme.colorScheme.onBackground,
          fontWeight = FontWeight.Bold
        )

        Spacer(modifier = Modifier.height(10.dp))

        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
          ActionBigButton(
            title = "Scan My Report",
            subtitle = "Take photo of paper lab sheet",
            icon = Icons.Default.CameraAlt,
            primaryColor = MaterialTheme.colorScheme.primary,
            iconContainerColor = MaterialTheme.colorScheme.primaryContainer,
            containerColor = MaterialTheme.colorScheme.surface,
            borderColor = palette.dividerBorder,
            modifier = Modifier.weight(1f),
            testTag = "btn_scan_my_report",
            onClick = onScanReportClick
          )

          ActionBigButton(
            title = "Upload My Report",
            subtitle = "Import PDF or photo from phone",
            icon = Icons.Default.FileUpload,
            primaryColor = MaterialTheme.colorScheme.primary,
            iconContainerColor = MaterialTheme.colorScheme.primaryContainer,
            containerColor = MaterialTheme.colorScheme.surface,
            borderColor = palette.dividerBorder,
            modifier = Modifier.weight(1f),
            testTag = "btn_upload_my_report",
            onClick = onUploadReportClick
          )
        }
      }

      // First-Time Guidance Card: Medical Report Reader Onboarding Carousel
      item {
        Card(
          shape = RoundedCornerShape(16.dp),
          colors = CardDefaults.cardColors(containerColor = palette.statusInfoContainer),
          border = BorderStroke(1.25.dp, palette.statusInfoBorder),
          modifier = Modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = 48.dp)
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onReportReaderGuideClick)
            .testTag("card_report_reader_guide")
        ) {
          Row(
            modifier = Modifier
              .fillMaxWidth()
              .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
          ) {
            Box(
              contentAlignment = Alignment.Center,
              modifier = Modifier
                .size(44.dp)
                .background(MaterialTheme.colorScheme.surface, CircleShape)
            ) {
              Icon(
                imageVector = Icons.AutoMirrored.Filled.MenuBook,
                contentDescription = stringResource(R.string.home_report_guide_cd),
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(22.dp)
              )
            }
            Spacer(modifier = Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
              Text(
                text = stringResource(R.string.home_report_guide_badge),
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.6.sp,
                color = MaterialTheme.colorScheme.primary
              )
              Spacer(modifier = Modifier.height(2.dp))
              Text(
                text = stringResource(R.string.home_report_guide_title),
                fontWeight = FontWeight.Bold,
                color = palette.statusInfoText,
                fontSize = 15.sp
              )
              Spacer(modifier = Modifier.height(2.dp))
              Text(
                text = stringResource(R.string.home_report_guide_subtitle),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 12.sp,
                lineHeight = 17.sp
              )
            }
            Spacer(modifier = Modifier.width(8.dp))
            Icon(
              imageVector = Icons.Default.ChevronRight,
              contentDescription = null,
              tint = MaterialTheme.colorScheme.primary
            )
          }
        }
      }

      // Explain My Health Policy Card
      item {
        Card(
          shape = RoundedCornerShape(16.dp),
          colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
          border = BorderStroke(1.25.dp, MaterialTheme.colorScheme.outlineVariant),
          modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onExplainHealthPolicyClick)
            .testTag("card_explain_health_policy")
        ) {
          Row(
            modifier = Modifier
              .fillMaxWidth()
              .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
          ) {
            Box(
              contentAlignment = Alignment.Center,
              modifier = Modifier
                .size(48.dp)
                .background(MaterialTheme.colorScheme.primaryContainer, CircleShape)
            ) {
              Icon(
                imageVector = Icons.Default.Shield,
                contentDescription = "Explain My Health Policy",
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(26.dp)
              )
            }
            Spacer(modifier = Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
              Text(
                text = "Explain My Health Policy",
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                fontSize = 16.sp
              )
              Spacer(modifier = Modifier.height(2.dp))
              Text(
                text = "Understand coverage, waiting periods & red flags in simple words",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 13.sp,
                lineHeight = 18.sp
              )
            }
            Icon(
              imageVector = Icons.Default.ChevronRight,
              contentDescription = "Open Explain My Health Policy",
              tint = MaterialTheme.colorScheme.primary
            )
          }
        }
      }

      // Try Sample Report Explanation Banner (Premium Midnight-to-Royal Blue Hero)
      item {
        Card(
          shape = RoundedCornerShape(16.dp),
          colors = CardDefaults.cardColors(containerColor = Color.Transparent),
          modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(palette.heroGradientBrush)
            .clickable(onClick = onSampleExplanationClick)
            .testTag("banner_sample_demo_report")
        ) {
          Row(
            modifier = Modifier
              .fillMaxWidth()
              .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
          ) {
            Box(
              contentAlignment = Alignment.Center,
              modifier = Modifier
                .size(44.dp)
                .background(palette.heroCardSurface, CircleShape)
            ) {
              Icon(
                imageVector = Icons.Default.PlayCircleFilled,
                contentDescription = null,
                tint = TextOnDark,
                modifier = Modifier.size(28.dp)
              )
            }
            Spacer(modifier = Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
              Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                  text = "DEMO REPORT",
                  fontSize = 11.sp,
                  fontWeight = FontWeight.Bold,
                  color = MistBlue
                )
                Spacer(modifier = Modifier.width(6.dp))
                Icon(
                  imageVector = Icons.Default.AutoAwesome,
                  contentDescription = null,
                  tint = MistBlue,
                  modifier = Modifier.size(13.dp)
                )
              }
              Text(
                text = "Complete Blood Count (CBC) Demo",
                fontWeight = FontWeight.Bold,
                color = TextOnDark,
                fontSize = 15.sp
              )
              Text(
                text = "Explore 12-yr-old simple explanations & audio voice",
                color = PaleBlue,
                fontSize = 12.sp
              )
            }
            Icon(
              imageVector = Icons.Default.ChevronRight,
              contentDescription = "Open Demo",
              tint = TextOnDark
            )
          }
        }
      }

      // Recent Reports Section
      item {
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically
        ) {
          Text(
            text = "Recent Reports",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onBackground,
            fontWeight = FontWeight.Bold
          )

          Text(
            text = "View All",
            color = MaterialTheme.colorScheme.primary,
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier
              .clip(RoundedCornerShape(8.dp))
              .clickable(onClick = onViewAllReportsClick)
              .padding(horizontal = 6.dp, vertical = 4.dp)
              .testTag("link_view_all_reports")
          )
        }
      }

      items(recentReports) { report ->
        RecentReportItemCard(
          report = report,
          onClick = { onReportItemClick(report) }
        )
      }
    }
  }
}

@Composable
private fun CounterCard(
  title: String,
  count: String,
  subtitle: String,
  icon: ImageVector,
  badgeColor: Color,
  badgeBgColor: Color,
  bgColor: Color,
  borderColor: Color,
  modifier: Modifier = Modifier,
  testTag: String,
  onClick: (() -> Unit)? = null
) {
  Card(
    shape = RoundedCornerShape(16.dp),
    colors = CardDefaults.cardColors(containerColor = bgColor),
    border = BorderStroke(1.dp, borderColor),
    modifier = modifier
      .clip(RoundedCornerShape(16.dp))
      .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
      .testTag(testTag)
  ) {
    Column(
      modifier = Modifier
        .fillMaxWidth()
        .padding(14.dp)
    ) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
      ) {
        Text(
          text = title,
          fontSize = 12.sp,
          fontWeight = FontWeight.Medium,
          color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Box(
          contentAlignment = Alignment.Center,
          modifier = Modifier
            .size(28.dp)
            .background(badgeBgColor, CircleShape)
        ) {
          Icon(
            imageVector = icon,
            contentDescription = null,
            tint = badgeColor,
            modifier = Modifier.size(16.dp)
          )
        }
      }
      Spacer(modifier = Modifier.height(6.dp))
      Text(
        text = count,
        fontSize = 24.sp,
        fontWeight = FontWeight.ExtraBold,
        color = MaterialTheme.colorScheme.onSurface
      )
      Spacer(modifier = Modifier.height(2.dp))
      Text(
        text = subtitle,
        fontSize = 11.sp,
        color = MaterialTheme.colorScheme.onSurfaceVariant
      )
    }
  }
}

@Composable
private fun ActionBigButton(
  title: String,
  subtitle: String,
  icon: ImageVector,
  primaryColor: Color,
  iconContainerColor: Color,
  containerColor: Color,
  borderColor: Color,
  modifier: Modifier = Modifier,
  testTag: String,
  onClick: () -> Unit
) {
  Card(
    shape = RoundedCornerShape(18.dp),
    colors = CardDefaults.cardColors(containerColor = containerColor),
    border = BorderStroke(1.dp, borderColor),
    modifier = modifier
      .height(140.dp)
      .clip(RoundedCornerShape(18.dp))
      .clickable(onClick = onClick)
      .testTag(testTag)
  ) {
    Column(
      modifier = Modifier
        .fillMaxSize()
        .padding(14.dp),
      verticalArrangement = Arrangement.SpaceBetween
    ) {
      Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
          .size(44.dp)
          .background(iconContainerColor, CircleShape)
      ) {
        Icon(
          imageVector = icon,
          contentDescription = null,
          tint = primaryColor,
          modifier = Modifier.size(24.dp)
        )
      }

      Column {
        Text(
          text = title,
          fontWeight = FontWeight.Bold,
          fontSize = 15.sp,
          color = MaterialTheme.colorScheme.onSurface
        )
        Spacer(modifier = Modifier.height(2.dp))
        Text(
          text = subtitle,
          fontSize = 11.sp,
          lineHeight = 15.sp,
          color = MaterialTheme.colorScheme.onSurfaceVariant
        )
      }
    }
  }
}

@Composable
fun RecentReportItemCard(
  report: ReportHistoryItem,
  onClick: () -> Unit
) {
  val palette = NexzyTheme.colors

  Card(
    shape = RoundedCornerShape(14.dp),
    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    border = BorderStroke(1.dp, palette.dividerBorder),
    modifier = Modifier
      .fillMaxWidth()
      .clip(RoundedCornerShape(14.dp))
      .clickable(onClick = onClick)
      .testTag("report_card_${report.id}")
  ) {
    Row(
      modifier = Modifier
        .fillMaxWidth()
        .padding(14.dp),
      verticalAlignment = Alignment.CenterVertically
    ) {
      Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
          .size(40.dp)
          .background(MaterialTheme.colorScheme.primaryContainer, CircleShape)
      ) {
        Icon(
          imageVector = Icons.Default.Description,
          contentDescription = null,
          tint = MaterialTheme.colorScheme.primary,
          modifier = Modifier.size(22.dp)
        )
      }
      Spacer(modifier = Modifier.width(12.dp))
      Column(modifier = Modifier.weight(1f)) {
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically
        ) {
          Text(
            text = report.title,
            fontWeight = FontWeight.Bold,
            fontSize = 14.sp,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f)
          )
          if (report.isDemo) {
            Box(
              modifier = Modifier
                .background(palette.statusInfoContainer, RoundedCornerShape(6.dp))
                .padding(horizontal = 6.dp, vertical = 2.dp)
            ) {
              Text(
                text = "DEMO",
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                color = palette.statusInfoText
              )
            }
          }
        }
        Spacer(modifier = Modifier.height(3.dp))
        Text(
          text = "${report.patientName} \u2022 ${report.date}",
          fontSize = 12.sp,
          color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(6.dp))
        StatusBadge(status = report.status, customLabel = report.mainHighlight)
      }
      Spacer(modifier = Modifier.width(6.dp))
      Icon(
        imageVector = Icons.Default.ChevronRight,
        contentDescription = "Details",
        tint = MaterialTheme.colorScheme.onSurfaceVariant
      )
    }
  }
}
