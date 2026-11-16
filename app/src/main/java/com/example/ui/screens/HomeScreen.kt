package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.PlayCircleFilled
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.ReportHistoryItem
import com.example.ui.components.NexzyTopAppBar
import com.example.ui.components.StatusBadge
import com.example.ui.theme.BrandMintBackground
import com.example.ui.theme.BrandMintSurface
import com.example.ui.theme.BrandNavyMuted
import com.example.ui.theme.BrandNavyText
import com.example.ui.theme.BrandTealDark
import com.example.ui.theme.BrandTealLight
import com.example.ui.theme.BrandTealPrimary
import com.example.ui.theme.StatusAttentionAmber
import com.example.ui.theme.StatusAttentionHoney
import com.example.ui.theme.SurfaceBorder
import com.example.ui.theme.SurfaceCard

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
  modifier: Modifier = Modifier
) {
  Column(
    modifier = modifier
      .fillMaxSize()
      .background(Color.White)
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
      verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
      item {
        Spacer(modifier = Modifier.height(4.dp))
        // Greeting & Caregiver Context
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically
        ) {
          Column {
            Text(
              text = "Hello, $userName 👋",
              style = MaterialTheme.typography.headlineSmall,
              color = BrandNavyText,
              fontWeight = FontWeight.Bold
            )
            Text(
              text = "Managing health reports for 3 family members",
              style = MaterialTheme.typography.bodySmall,
              color = BrandNavyMuted
            )
          }

          Box(
            modifier = Modifier
              .clip(RoundedCornerShape(12.dp))
              .background(BrandMintBackground)
              .clickable(onClick = onLanguageClick)
              .padding(horizontal = 10.dp, vertical = 6.dp)
          ) {
            Text(
              text = "🌐 $selectedLanguageName",
              color = BrandTealPrimary,
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
            badgeColor = BrandTealPrimary,
            bgColor = BrandMintSurface,
            modifier = Modifier.weight(1f),
            testTag = "counter_reports_analysed"
          )
          CounterCard(
            title = "Free Remaining",
            count = "$freeCreditsRemaining",
            subtitle = "Tap to view plans",
            icon = Icons.Default.LockOpen,
            badgeColor = StatusAttentionAmber,
            bgColor = StatusAttentionHoney.copy(alpha = 0.5f),
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
          color = BrandNavyText,
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
            primaryColor = BrandTealPrimary,
            containerColor = BrandMintBackground,
            modifier = Modifier.weight(1f),
            testTag = "btn_scan_my_report",
            onClick = onScanReportClick
          )

          ActionBigButton(
            title = "Upload My Report",
            subtitle = "Import PDF or photo from phone",
            icon = Icons.Default.FileUpload,
            primaryColor = BrandNavyText,
            containerColor = Color(0xFFF1F5F9),
            modifier = Modifier.weight(1f),
            testTag = "btn_upload_my_report",
            onClick = onUploadReportClick
          )
        }
      }

      // Try Sample Report Explanation Banner
      item {
        Card(
          shape = RoundedCornerShape(16.dp),
          colors = CardDefaults.cardColors(containerColor = BrandTealPrimary),
          modifier = Modifier
            .fillMaxWidth()
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
                .background(Color.White.copy(alpha = 0.2f), CircleShape)
            ) {
              Icon(
                imageVector = Icons.Default.PlayCircleFilled,
                contentDescription = null,
                tint = Color.White,
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
                  color = BrandMintBackground
                )
                Spacer(modifier = Modifier.width(6.dp))
                Icon(
                  imageVector = Icons.Default.AutoAwesome,
                  contentDescription = null,
                  tint = BrandMintBackground,
                  modifier = Modifier.size(13.dp)
                )
              }
              Text(
                text = "Complete Blood Count (CBC) Demo",
                fontWeight = FontWeight.Bold,
                color = Color.White,
                fontSize = 15.sp
              )
              Text(
                text = "Explore 12-yr-old simple explanations & audio voice",
                color = Color.White.copy(alpha = 0.85f),
                fontSize = 12.sp
              )
            }
            Icon(
              imageVector = Icons.Default.ChevronRight,
              contentDescription = "Open Demo",
              tint = Color.White
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
            color = BrandNavyText,
            fontWeight = FontWeight.Bold
          )

          Text(
            text = "View All",
            color = BrandTealPrimary,
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier
              .clickable(onClick = onViewAllReportsClick)
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

      item {
        Spacer(modifier = Modifier.height(16.dp))
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
  bgColor: Color,
  modifier: Modifier = Modifier,
  testTag: String,
  onClick: (() -> Unit)? = null
) {
  Card(
    shape = RoundedCornerShape(16.dp),
    colors = CardDefaults.cardColors(containerColor = bgColor),
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
          color = BrandNavyMuted
        )
        Icon(
          imageVector = icon,
          contentDescription = null,
          tint = badgeColor,
          modifier = Modifier.size(18.dp)
        )
      }
      Spacer(modifier = Modifier.height(6.dp))
      Text(
        text = count,
        fontSize = 24.sp,
        fontWeight = FontWeight.ExtraBold,
        color = BrandNavyText
      )
      Spacer(modifier = Modifier.height(2.dp))
      Text(
        text = subtitle,
        fontSize = 11.sp,
        color = BrandNavyMuted
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
  containerColor: Color,
  modifier: Modifier = Modifier,
  testTag: String,
  onClick: () -> Unit
) {
  Card(
    shape = RoundedCornerShape(18.dp),
    colors = CardDefaults.cardColors(containerColor = containerColor),
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
          .size(42.dp)
          .background(Color.White, CircleShape)
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
          color = BrandNavyText
        )
        Spacer(modifier = Modifier.height(2.dp))
        Text(
          text = subtitle,
          fontSize = 11.sp,
          lineHeight = 15.sp,
          color = BrandNavyMuted
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
  Card(
    shape = RoundedCornerShape(14.dp),
    colors = CardDefaults.cardColors(containerColor = SurfaceCard),
    modifier = Modifier
      .fillMaxWidth()
      .clip(RoundedCornerShape(14.dp))
      .border(1.dp, SurfaceBorder, RoundedCornerShape(14.dp))
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
          .background(BrandMintBackground, CircleShape)
      ) {
        Icon(
          imageVector = Icons.Default.Description,
          contentDescription = null,
          tint = BrandTealPrimary,
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
            color = BrandNavyText,
            modifier = Modifier.weight(1f)
          )
          if (report.isDemo) {
            Box(
              modifier = Modifier
                .background(Color(0xFFE0E7FF), RoundedCornerShape(6.dp))
                .padding(horizontal = 6.dp, vertical = 2.dp)
            ) {
              Text(
                text = "DEMO",
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF3730A3)
              )
            }
          }
        }
        Spacer(modifier = Modifier.height(3.dp))
        Text(
          text = "${report.patientName} • ${report.date}",
          fontSize = 12.sp,
          color = BrandNavyMuted
        )
        Spacer(modifier = Modifier.height(6.dp))
        StatusBadge(status = report.status, customLabel = report.mainHighlight)
      }
      Spacer(modifier = Modifier.width(6.dp))
      Icon(
        imageVector = Icons.Default.ChevronRight,
        contentDescription = "Details",
        tint = BrandNavyMuted
      )
    }
  }
}
