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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Face
import androidx.compose.material.icons.filled.Forward10
import androidx.compose.material.icons.filled.HelpOutline
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.QuestionAnswer
import androidx.compose.material.icons.filled.Replay
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.SampleReportData
import com.example.ui.components.NexzyTopAppBar
import com.example.ui.components.StatusBadge
import com.example.ui.theme.BrandMintBackground
import com.example.ui.theme.BrandMintSurface
import com.example.ui.theme.BrandNavyMuted
import com.example.ui.theme.BrandNavyText
import com.example.ui.theme.BrandTealPrimary
import com.example.ui.theme.StatusAttentionAmber
import com.example.ui.theme.StatusAttentionHoney
import com.example.ui.theme.SurfaceBorder
import com.example.ui.theme.SurfaceCard

@Composable
fun SampleExplanationScreen(
  reportData: SampleReportData,
  onNavigateToDetailedResults: () -> Unit,
  onNavigateToAudioScreen: () -> Unit,
  onNavigateBack: () -> Unit,
  modifier: Modifier = Modifier
) {
  var isAudioPlayingMock by remember { mutableStateOf(false) }

  Column(
    modifier = modifier
      .fillMaxSize()
      .background(Color.White)
  ) {
    NexzyTopAppBar(
      title = "Report Explanation",
      subtitle = "Demystified for everyday clarity",
      showBackButton = true,
      onBackClick = onNavigateBack
    )

    Column(
      modifier = Modifier
        .fillMaxSize()
        .verticalScroll(rememberScrollState())
        .padding(horizontal = 20.dp, vertical = 10.dp)
    ) {
      // Prominent DEMO DATA Watermark Banner
      Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFFF1F5F9)),
        modifier = Modifier
          .fillMaxWidth()
          .border(1.dp, Color(0xFFCBD5E1), RoundedCornerShape(12.dp))
          .testTag("banner_demo_data_notice")
      ) {
        Row(
          modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
          verticalAlignment = Alignment.CenterVertically
        ) {
          Icon(
            imageVector = Icons.Default.WarningAmber,
            contentDescription = null,
            tint = Color(0xFF475569),
            modifier = Modifier.size(20.dp)
          )
          Spacer(modifier = Modifier.width(10.dp))
          Column {
            Text(
              text = "DEMO DATA • FOR DEMONSTRATION PURPOSES ONLY",
              fontWeight = FontWeight.Bold,
              fontSize = 11.sp,
              color = Color(0xFF1E293B)
            )
            Text(
              text = "Fictional values illustrate how NexzyHealth translates complex lab sheets.",
              fontSize = 11.sp,
              color = Color(0xFF64748B)
            )
          }
        }
      }

      Spacer(modifier = Modifier.height(14.dp))

      // Report Header Card
      Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = BrandMintSurface),
        modifier = Modifier.fillMaxWidth()
      ) {
        Column(modifier = Modifier.padding(16.dp)) {
          Text(
            text = reportData.reportName,
            style = MaterialTheme.typography.titleMedium,
            color = BrandNavyText,
            fontWeight = FontWeight.Bold
          )
          Spacer(modifier = Modifier.height(4.dp))
          Text(
            text = "📅 Date: ${reportData.date} • 🏥 Lab: ${reportData.labName}",
            fontSize = 12.sp,
            color = BrandNavyMuted
          )
          Spacer(modifier = Modifier.height(2.dp))
          Text(
            text = "👤 Patient: ${reportData.patientName}",
            fontSize = 12.sp,
            color = BrandNavyMuted
          )
        }
      }

      Spacer(modifier = Modifier.height(16.dp))

      // Featured Test Parameter: Hemoglobin (Hb)
      val test = reportData.primaryTest
      Text(
        text = "Key Test Highlight",
        style = MaterialTheme.typography.titleMedium,
        color = BrandNavyText,
        fontWeight = FontWeight.Bold
      )

      Spacer(modifier = Modifier.height(8.dp))

      Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = SurfaceCard),
        modifier = Modifier
          .fillMaxWidth()
          .border(1.dp, SurfaceBorder, RoundedCornerShape(18.dp))
      ) {
        Column(modifier = Modifier.padding(18.dp)) {
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
          ) {
            Column {
              Text(
                text = test.name,
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp,
                color = BrandNavyText
              )
              Text(
                text = "Clinical Code: ${test.code}",
                fontSize = 12.sp,
                color = BrandNavyMuted
              )
            }
            StatusBadge(status = test.status)
          }

          Spacer(modifier = Modifier.height(16.dp))

          // Original Test Value & Lab Range Table
          Row(
            modifier = Modifier
              .fillMaxWidth()
              .background(Color.White, RoundedCornerShape(12.dp))
              .border(1.dp, SurfaceBorder, RoundedCornerShape(12.dp))
              .padding(14.dp),
            horizontalArrangement = Arrangement.SpaceAround
          ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
              Text(text = "Your Value", fontSize = 11.sp, color = BrandNavyMuted)
              Spacer(modifier = Modifier.height(2.dp))
              Text(
                text = "${test.value} ${test.unit}",
                fontWeight = FontWeight.ExtraBold,
                fontSize = 17.sp,
                color = StatusAttentionAmber
              )
            }

            Box(
              modifier = Modifier
                .width(1.dp)
                .height(36.dp)
                .background(SurfaceBorder)
            )

            Column(horizontalAlignment = Alignment.CenterHorizontally) {
              Text(text = "Lab Reference Range", fontSize = 11.sp, color = BrandNavyMuted)
              Spacer(modifier = Modifier.height(2.dp))
              Text(
                text = test.referenceRange,
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp,
                color = BrandNavyText
              )
            }
          }

          Spacer(modifier = Modifier.height(16.dp))

          // 12-Year-Old Simple Analogy Explanation Box
          Box(
            modifier = Modifier
              .fillMaxWidth()
              .background(BrandMintBackground, RoundedCornerShape(14.dp))
              .padding(14.dp)
          ) {
            Column {
              Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                  text = test.simpleMetaphor,
                  fontWeight = FontWeight.Bold,
                  fontSize = 13.sp,
                  color = BrandTealPrimary
                )
              }
              Spacer(modifier = Modifier.height(6.dp))
              Text(
                text = test.simpleExplanation,
                fontSize = 14.sp,
                lineHeight = 21.sp,
                color = BrandNavyText
              )
            }
          }
        }
      }

      Spacer(modifier = Modifier.height(18.dp))

      // "What should I ask my doctor?" Section
      Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFFFEF3C7)),
        modifier = Modifier
          .fillMaxWidth()
          .border(1.dp, Color(0xFFFDE68A), RoundedCornerShape(16.dp))
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
              text = "What Should I Ask My Doctor?",
              fontWeight = FontWeight.Bold,
              fontSize = 15.sp,
              color = Color(0xFF92400E)
            )
          }

          Spacer(modifier = Modifier.height(10.dp))

          Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            QuestionBullet("Should I take a gentle iron supplement, or adjust my daily diet with lentils and greens?")
            QuestionBullet("Could my afternoon tiredness or low energy be connected to this 11.2 score?")
            QuestionBullet("When do you recommend we re-test my blood to see if numbers have improved?")
          }
        }
      }

      Spacer(modifier = Modifier.height(18.dp))

      // Audio Explanation Panel with Female Voice Icon & Mock Playback Controls
      Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = BrandMintSurface),
        modifier = Modifier
          .fillMaxWidth()
          .border(1.5.dp, BrandTealPrimary.copy(alpha = 0.4f), RoundedCornerShape(18.dp))
          .testTag("audio_explanation_panel")
      ) {
        Column(modifier = Modifier.padding(16.dp)) {
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
          ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
              Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                  .size(44.dp)
                  .background(BrandTealPrimary, CircleShape)
              ) {
                Icon(
                  imageVector = Icons.Default.Face,
                  contentDescription = "Narrator",
                  tint = Color.White,
                  modifier = Modifier.size(26.dp)
                )
              }
              Spacer(modifier = Modifier.width(12.dp))
              Column {
                Text(
                  text = "Listen: Dr. Ananya's Voice",
                  fontWeight = FontWeight.Bold,
                  fontSize = 15.sp,
                  color = BrandNavyText
                )
                Text(
                  text = "Warm female narration • 2 min 25 sec",
                  fontSize = 12.sp,
                  color = BrandNavyMuted
                )
              }
            }

            Box(
              modifier = Modifier
                .clip(RoundedCornerShape(8.dp))
                .background(BrandMintBackground)
                .clickable(onClick = onNavigateToAudioScreen)
                .padding(horizontal = 8.dp, vertical = 4.dp)
            ) {
              Text(
                text = "Full Player ↗",
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = BrandTealPrimary
              )
            }
          }

          Spacer(modifier = Modifier.height(14.dp))

          // Playback Controls Row
          Row(
            modifier = Modifier
              .fillMaxWidth()
              .background(Color.White, RoundedCornerShape(12.dp))
              .padding(horizontal = 14.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
          ) {
            IconButton(
              onClick = { isAudioPlayingMock = false },
              modifier = Modifier.testTag("btn_audio_mock_replay")
            ) {
              Icon(Icons.Default.Replay, contentDescription = "Replay", tint = BrandNavyText)
            }

            IconButton(
              onClick = { isAudioPlayingMock = !isAudioPlayingMock },
              modifier = Modifier
                .size(48.dp)
                .background(BrandTealPrimary, CircleShape)
                .testTag("btn_audio_mock_play_pause")
            ) {
              Icon(
                imageVector = if (isAudioPlayingMock) Icons.Default.Pause else Icons.Default.PlayArrow,
                contentDescription = if (isAudioPlayingMock) "Pause" else "Play",
                tint = Color.White,
                modifier = Modifier.size(28.dp)
              )
            }

            IconButton(
              onClick = { },
              modifier = Modifier.testTag("btn_audio_mock_forward")
            ) {
              Icon(Icons.Default.Forward10, contentDescription = "Forward 10s", tint = BrandNavyText)
            }

            Box(
              modifier = Modifier
                .background(Color(0xFFF1F5F9), RoundedCornerShape(6.dp))
                .padding(horizontal = 8.dp, vertical = 4.dp)
            ) {
              Text(text = "1.0x", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = BrandNavyText)
            }
          }
        }
      }

      Spacer(modifier = Modifier.height(20.dp))

      // Button: Detailed Results
      Button(
        onClick = onNavigateToDetailedResults,
        colors = ButtonDefaults.buttonColors(containerColor = BrandTealPrimary),
        shape = RoundedCornerShape(28.dp),
        modifier = Modifier
          .fillMaxWidth()
          .height(52.dp)
          .testTag("btn_view_detailed_test_results")
      ) {
        Text(
          text = "View All 6 Test Parameters",
          fontSize = 15.sp,
          fontWeight = FontWeight.Bold,
          color = Color.White
        )
        Spacer(modifier = Modifier.width(6.dp))
        Icon(Icons.Default.ChevronRight, contentDescription = null)
      }

      Spacer(modifier = Modifier.height(10.dp))

      // Action row: Share & Download
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
      ) {
        OutlinedButton(
          onClick = { },
          shape = RoundedCornerShape(24.dp),
          modifier = Modifier
            .weight(1f)
            .height(48.dp)
            .testTag("btn_download_summary")
        ) {
          Icon(Icons.Default.Download, contentDescription = null, tint = BrandTealPrimary, modifier = Modifier.size(18.dp))
          Spacer(modifier = Modifier.width(6.dp))
          Text("Save PDF", color = BrandNavyText, fontSize = 13.sp)
        }

        OutlinedButton(
          onClick = { },
          shape = RoundedCornerShape(24.dp),
          modifier = Modifier
            .weight(1f)
            .height(48.dp)
            .testTag("btn_share_summary")
        ) {
          Icon(Icons.Default.Share, contentDescription = null, tint = BrandTealPrimary, modifier = Modifier.size(18.dp))
          Spacer(modifier = Modifier.width(6.dp))
          Text("Share", color = BrandNavyText, fontSize = 13.sp)
        }
      }

      Spacer(modifier = Modifier.height(24.dp))
    }
  }
}

@Composable
private fun QuestionBullet(text: String) {
  Row(
    modifier = Modifier.fillMaxWidth(),
    verticalAlignment = Alignment.Top
  ) {
    Text(
      text = "•",
      color = Color(0xFFB45309),
      fontWeight = FontWeight.Bold,
      fontSize = 16.sp,
      modifier = Modifier.padding(end = 8.dp)
    )
    Text(
      text = text,
      color = Color(0xFF78350F),
      fontSize = 13.sp,
      lineHeight = 18.sp
    )
  }
}
