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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.FormatSize
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.components.NexzyTopAppBar
import com.example.ui.theme.BrandMintBackground
import com.example.ui.theme.BrandNavyMuted
import com.example.ui.theme.BrandNavyText
import com.example.ui.theme.BrandTealPrimary
import com.example.ui.theme.SurfaceBorder
import com.example.ui.theme.SurfaceCard

@Composable
fun SettingsScreen(
  selectedLanguage: String,
  onNavigateToLanguage: () -> Unit,
  onNavigateBack: () -> Unit,
  modifier: Modifier = Modifier
) {
  var selectedTextScale by remember { mutableFloatStateOf(1.0f) }
  var notifyOnReportReady by remember { mutableStateOf(true) }
  var notifyOnFreeCreditReset by remember { mutableStateOf(true) }
  var defaultAudioSpeed by remember { mutableStateOf("1.0x") }

  Column(
    modifier = modifier
      .fillMaxSize()
      .background(Color.White)
  ) {
    NexzyTopAppBar(
      title = "Settings",
      subtitle = "Accessibility & app configuration",
      showBackButton = true,
      onBackClick = onNavigateBack
    )

    Column(
      modifier = Modifier
        .fillMaxSize()
        .verticalScroll(rememberScrollState())
        .padding(horizontal = 20.dp, vertical = 12.dp),
      verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
      // Language Card
      Text(
        text = "Language & Reading",
        style = MaterialTheme.typography.titleMedium,
        color = BrandNavyText,
        fontWeight = FontWeight.Bold
      )

      Card(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = SurfaceCard),
        modifier = Modifier
          .fillMaxWidth()
          .clip(RoundedCornerShape(14.dp))
          .border(1.dp, SurfaceBorder, RoundedCornerShape(14.dp))
          .clickable(onClick = onNavigateToLanguage)
          .testTag("settings_language_tile")
      ) {
        Row(
          modifier = Modifier.padding(14.dp),
          verticalAlignment = Alignment.CenterVertically
        ) {
          Icon(Icons.Default.Language, contentDescription = null, tint = BrandTealPrimary)
          Spacer(modifier = Modifier.width(12.dp))
          Column(modifier = Modifier.weight(1f)) {
            Text("Selected Language", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = BrandNavyText)
            Text(selectedLanguage, fontSize = 12.sp, color = BrandNavyMuted)
          }
          Icon(Icons.Default.ChevronRight, contentDescription = null, tint = BrandNavyMuted)
        }
      }

      // Senior Accessibility: Font Sizing
      Card(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = SurfaceCard),
        modifier = Modifier.fillMaxWidth().border(1.dp, SurfaceBorder, RoundedCornerShape(14.dp))
      ) {
        Column(modifier = Modifier.padding(14.dp)) {
          Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.FormatSize, contentDescription = null, tint = BrandTealPrimary)
            Spacer(modifier = Modifier.width(12.dp))
            Column {
              Text("Senior & Caregiver Text Size", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = BrandNavyText)
              Text("Enhances readability for older family members", fontSize = 12.sp, color = BrandNavyMuted)
            }
          }

          Spacer(modifier = Modifier.height(12.dp))

          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
          ) {
            val sizes = listOf(1.0f to "Standard", 1.15f to "Large", 1.3f to "Extra Large")
            sizes.forEach { (scale, label) ->
              val isSelected = selectedTextScale == scale
              Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                  .weight(1f)
                  .clip(RoundedCornerShape(10.dp))
                  .background(if (isSelected) BrandTealPrimary else Color.White)
                  .border(1.dp, if (isSelected) BrandTealPrimary else SurfaceBorder, RoundedCornerShape(10.dp))
                  .clickable { selectedTextScale = scale }
                  .padding(vertical = 10.dp)
              ) {
                Text(
                  text = label,
                  fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                  fontSize = 12.sp,
                  color = if (isSelected) Color.White else BrandNavyText
                )
              }
            }
          }
        }
      }

      // Audio Speed Setting
      Card(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = SurfaceCard),
        modifier = Modifier.fillMaxWidth().border(1.dp, SurfaceBorder, RoundedCornerShape(14.dp))
      ) {
        Column(modifier = Modifier.padding(14.dp)) {
          Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.Speed, contentDescription = null, tint = BrandTealPrimary)
            Spacer(modifier = Modifier.width(12.dp))
            Column {
              Text("Default Voice Speed", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = BrandNavyText)
              Text("Preferred pacing for spoken summaries", fontSize = 12.sp, color = BrandNavyMuted)
            }
          }

          Spacer(modifier = Modifier.height(10.dp))

          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
          ) {
            listOf("0.75x (Slower)", "1.0x (Normal)", "1.25x (Faster)").forEach { speed ->
              val isSelected = defaultAudioSpeed.startsWith(speed.substring(0, 4))
              Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                  .weight(1f)
                  .clip(RoundedCornerShape(10.dp))
                  .background(if (isSelected) BrandMintBackground else Color.White)
                  .border(1.dp, if (isSelected) BrandTealPrimary else SurfaceBorder, RoundedCornerShape(10.dp))
                  .clickable { defaultAudioSpeed = speed.substring(0, 4) }
                  .padding(vertical = 8.dp)
              ) {
                Text(
                  text = speed,
                  fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                  fontSize = 11.sp,
                  color = if (isSelected) BrandTealPrimary else BrandNavyText,
                  maxLines = 1
                )
              }
            }
          }
        }
      }

      // Notifications
      Text(
        text = "Notifications",
        style = MaterialTheme.typography.titleMedium,
        color = BrandNavyText,
        fontWeight = FontWeight.Bold
      )

      Card(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = SurfaceCard),
        modifier = Modifier.fillMaxWidth().border(1.dp, SurfaceBorder, RoundedCornerShape(14.dp))
      ) {
        Column(modifier = Modifier.padding(14.dp)) {
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
          ) {
            Column(modifier = Modifier.weight(1f)) {
              Text("Report Ready Alerts", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = BrandNavyText)
              Text("Notify when simplified explanation is generated", fontSize = 12.sp, color = BrandNavyMuted)
            }
            Switch(
              checked = notifyOnReportReady,
              onCheckedChange = { notifyOnReportReady = it },
              colors = SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = BrandTealPrimary)
            )
          }

          Spacer(modifier = Modifier.height(12.dp))

          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
          ) {
            Column(modifier = Modifier.weight(1f)) {
              Text("Monthly Free Credit Alerts", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = BrandNavyText)
              Text("Reminder when 3 new free reports refresh", fontSize = 12.sp, color = BrandNavyMuted)
            }
            Switch(
              checked = notifyOnFreeCreditReset,
              onCheckedChange = { notifyOnFreeCreditReset = it },
              colors = SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = BrandTealPrimary)
            )
          }
        }
      }

      // App Information & Version
      Card(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFFF1F5F9)),
        modifier = Modifier.fillMaxWidth()
      ) {
        Row(
          modifier = Modifier.padding(14.dp),
          verticalAlignment = Alignment.CenterVertically
        ) {
          Icon(Icons.Default.Info, contentDescription = null, tint = BrandNavyMuted)
          Spacer(modifier = Modifier.width(12.dp))
          Column {
            Text("NexzyHealth for Android", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = BrandNavyText)
            Text("Version 1.0.0 (Day 1 Preview Build)", fontSize = 12.sp, color = BrandNavyMuted)
          }
        }
      }

      Spacer(modifier = Modifier.height(20.dp))
    }
  }
}
