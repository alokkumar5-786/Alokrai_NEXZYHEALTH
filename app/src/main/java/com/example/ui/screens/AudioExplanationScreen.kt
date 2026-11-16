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
import androidx.compose.material.icons.filled.Face
import androidx.compose.material.icons.filled.FastForward
import androidx.compose.material.icons.filled.FastRewind
import androidx.compose.material.icons.filled.Forward10
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Replay
import androidx.compose.material.icons.filled.Replay10
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
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
import com.example.ui.components.NexzyTopAppBar
import com.example.ui.theme.BrandMintBackground
import com.example.ui.theme.BrandMintSurface
import com.example.ui.theme.BrandNavyMuted
import com.example.ui.theme.BrandNavyText
import com.example.ui.theme.BrandTealPrimary
import com.example.ui.theme.SurfaceBorder
import com.example.ui.theme.VoiceWaveformBg

@Composable
fun AudioExplanationScreen(
  transcript: String,
  reportTitle: String = "Complete Blood Count (CBC) Summary",
  onNavigateBack: () -> Unit,
  modifier: Modifier = Modifier
) {
  var isPlaying by remember { mutableStateOf(false) }
  var currentPosition by remember { mutableFloatStateOf(42f) } // seconds
  val totalDuration = 145f // 2m 25s
  var currentSpeed by remember { mutableStateOf("1.0x") }
  var selectedLanguage by remember { mutableStateOf("English") }

  Column(
    modifier = modifier
      .fillMaxSize()
      .background(Color.White)
  ) {
    NexzyTopAppBar(
      title = "Audio Explanation",
      subtitle = "Spoken plain-language health summary",
      showBackButton = true,
      onBackClick = onNavigateBack
    )

    Column(
      modifier = Modifier
        .fillMaxSize()
        .verticalScroll(rememberScrollState())
        .padding(horizontal = 24.dp, vertical = 12.dp),
      horizontalAlignment = Alignment.CenterHorizontally
    ) {
      // Voice Narrator Avatar Card
      Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
          .size(90.dp)
          .background(BrandMintBackground, CircleShape)
          .border(3.dp, BrandTealPrimary, CircleShape)
      ) {
        Icon(
          imageVector = Icons.Default.Face,
          contentDescription = "Narrator Avatar",
          tint = BrandTealPrimary,
          modifier = Modifier.size(54.dp)
        )
      }

      Spacer(modifier = Modifier.height(14.dp))

      Text(
        text = "Dr. Ananya's Audio Explanation",
        style = MaterialTheme.typography.titleLarge,
        color = BrandNavyText,
        fontWeight = FontWeight.Bold,
        textAlign = TextAlign.Center
      )

      Text(
        text = "Warm, natural female narration • Simple reading level",
        style = MaterialTheme.typography.bodySmall,
        color = BrandNavyMuted,
        textAlign = TextAlign.Center
      )

      Spacer(modifier = Modifier.height(14.dp))

      // Language Switcher Row
      Row(
        modifier = Modifier
          .background(Color(0xFFF1F5F9), RoundedCornerShape(20.dp))
          .padding(horizontal = 4.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp)
      ) {
        listOf("English", "हिन्दी (Hindi)", "বাংলা (Bengali)").forEach { lang ->
          val isSelected = selectedLanguage.startsWith(lang.substring(0, 3))
          Box(
            modifier = Modifier
              .clip(RoundedCornerShape(16.dp))
              .background(if (isSelected) BrandTealPrimary else Color.Transparent)
              .clickable { selectedLanguage = lang }
              .padding(horizontal = 12.dp, vertical = 6.dp)
          ) {
            Text(
              text = lang,
              fontSize = 12.sp,
              fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
              color = if (isSelected) Color.White else BrandNavyMuted
            )
          }
        }
      }

      Spacer(modifier = Modifier.height(20.dp))

      // Audio Waveform Visualization Simulation
      Row(
        modifier = Modifier
          .fillMaxWidth()
          .height(48.dp)
          .background(VoiceWaveformBg, RoundedCornerShape(12.dp))
          .padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
      ) {
        val heights = listOf(14, 28, 40, 22, 34, 18, 42, 26, 38, 16, 30, 44, 20, 36, 12, 24, 38, 20, 32, 14, 26)
        heights.forEachIndexed { idx, h ->
          val barActive = (idx.toFloat() / heights.size) <= (currentPosition / totalDuration)
          Box(
            modifier = Modifier
              .width(6.dp)
              .height(h.dp)
              .background(
                if (barActive) BrandTealPrimary else BrandTealPrimary.copy(alpha = 0.25f),
                RoundedCornerShape(3.dp)
              )
          )
        }
      }

      Spacer(modifier = Modifier.height(10.dp))

      // Progress Slider
      Slider(
        value = currentPosition,
        onValueChange = { currentPosition = it },
        valueRange = 0f..totalDuration,
        colors = SliderDefaults.colors(
          thumbColor = BrandTealPrimary,
          activeTrackColor = BrandTealPrimary,
          inactiveTrackColor = Color(0xFFE2E8F0)
        ),
        modifier = Modifier
          .fillMaxWidth()
          .testTag("audio_scrubber_slider")
      )

      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
      ) {
        val curMins = (currentPosition / 60).toInt()
        val curSecs = (currentPosition % 60).toInt()
        val totalMins = (totalDuration / 60).toInt()
        val totalSecs = (totalDuration % 60).toInt()
        Text(
          text = String.format("%02d:%02d", curMins, curSecs),
          fontSize = 12.sp,
          color = BrandNavyMuted
        )
        Text(
          text = String.format("%02d:%02d", totalMins, totalSecs),
          fontSize = 12.sp,
          color = BrandNavyMuted
        )
      }

      Spacer(modifier = Modifier.height(16.dp))

      // Main Playback Controls
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically
      ) {
        // Speed selector
        Box(
          modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .background(Color(0xFFF1F5F9))
            .clickable {
              currentSpeed = when (currentSpeed) {
                "0.75x" -> "1.0x"
                "1.0x" -> "1.25x"
                "1.25x" -> "1.5x"
                else -> "0.75x"
              }
            }
            .padding(horizontal = 10.dp, vertical = 6.dp)
            .testTag("audio_speed_selector")
        ) {
          Text(text = currentSpeed, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = BrandNavyText)
        }

        // Rewind 10s
        IconButton(
          onClick = { currentPosition = (currentPosition - 10f).coerceAtLeast(0f) },
          modifier = Modifier.testTag("btn_audio_rewind_10")
        ) {
          Icon(Icons.Default.Replay10, contentDescription = "Rewind 10s", tint = BrandNavyText, modifier = Modifier.size(28.dp))
        }

        // Play/Pause Big Button
        Box(
          contentAlignment = Alignment.Center,
          modifier = Modifier
            .size(64.dp)
            .background(BrandTealPrimary, CircleShape)
            .clickable { isPlaying = !isPlaying }
            .testTag("btn_audio_play_pause_main")
        ) {
          Icon(
            imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
            contentDescription = if (isPlaying) "Pause" else "Play",
            tint = Color.White,
            modifier = Modifier.size(36.dp)
          )
        }

        // Forward 10s
        IconButton(
          onClick = { currentPosition = (currentPosition + 10f).coerceAtMost(totalDuration) },
          modifier = Modifier.testTag("btn_audio_forward_10")
        ) {
          Icon(Icons.Default.Forward10, contentDescription = "Forward 10s", tint = BrandNavyText, modifier = Modifier.size(28.dp))
        }

        // Replay from beginning
        IconButton(
          onClick = { currentPosition = 0f },
          modifier = Modifier.testTag("btn_audio_replay_all")
        ) {
          Icon(Icons.Default.Replay, contentDescription = "Replay All", tint = BrandNavyText, modifier = Modifier.size(26.dp))
        }
      }

      Spacer(modifier = Modifier.height(24.dp))

      // Live Spoken Transcript Card
      Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = BrandMintSurface),
        modifier = Modifier
          .fillMaxWidth()
          .border(1.dp, SurfaceBorder, RoundedCornerShape(16.dp))
      ) {
        Column(modifier = Modifier.padding(16.dp)) {
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
          ) {
            Text(
              text = "Read Along (Transcript)",
              fontWeight = FontWeight.Bold,
              fontSize = 14.sp,
              color = BrandNavyText
            )
            Icon(Icons.Default.VolumeUp, contentDescription = null, tint = BrandTealPrimary, modifier = Modifier.size(18.dp))
          }

          Spacer(modifier = Modifier.height(10.dp))

          Text(
            text = transcript,
            fontSize = 14.sp,
            lineHeight = 22.sp,
            color = BrandNavyText
          )
        }
      }

      Spacer(modifier = Modifier.height(24.dp))
    }
  }
}
