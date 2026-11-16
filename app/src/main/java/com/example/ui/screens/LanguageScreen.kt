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
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.LanguageOption
import com.example.ui.components.NexzyTopAppBar
import com.example.ui.theme.BrandMintBackground
import com.example.ui.theme.BrandNavyMuted
import com.example.ui.theme.BrandNavyText
import com.example.ui.theme.BrandTealPrimary
import com.example.ui.theme.SurfaceBorder
import com.example.ui.theme.SurfaceCard

@Composable
fun LanguageScreen(
  languages: List<LanguageOption>,
  selectedLanguageCode: String,
  onLanguageSelected: (String) -> Unit,
  onConfirm: () -> Unit,
  onNavigateBack: () -> Unit,
  modifier: Modifier = Modifier
) {
  Column(
    modifier = modifier
      .fillMaxSize()
      .background(Color.White)
  ) {
    NexzyTopAppBar(
      title = "Select Language",
      showBackButton = true,
      onBackClick = onNavigateBack,
      subtitle = "Report explanations & audio narration"
    )

    Column(
      modifier = Modifier
        .fillMaxSize()
        .padding(horizontal = 20.dp, vertical = 12.dp)
    ) {
      Text(
        text = "Which language do you prefer?",
        style = MaterialTheme.typography.titleLarge,
        color = BrandNavyText,
        fontWeight = FontWeight.Bold
      )

      Spacer(modifier = Modifier.height(4.dp))

      Text(
        text = "First release supports English, Hindi and Bengali. Other regional languages are in progress.",
        style = MaterialTheme.typography.bodyMedium,
        color = BrandNavyMuted
      )

      Spacer(modifier = Modifier.height(16.dp))

      LazyColumn(
        modifier = Modifier
          .weight(1f)
          .fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp)
      ) {
        items(languages) { lang ->
          LanguageOptionCard(
            language = lang,
            isSelected = lang.code == selectedLanguageCode,
            onSelect = {
              if (lang.isSupported) {
                onLanguageSelected(lang.code)
              }
            }
          )
        }
      }

      Spacer(modifier = Modifier.height(12.dp))

      Button(
        onClick = onConfirm,
        colors = ButtonDefaults.buttonColors(containerColor = BrandTealPrimary),
        shape = RoundedCornerShape(28.dp),
        modifier = Modifier
          .fillMaxWidth()
          .height(52.dp)
          .testTag("btn_confirm_language")
      ) {
        Text(
          text = "Continue with Selected Language",
          fontSize = 16.sp,
          fontWeight = FontWeight.Bold,
          color = Color.White
        )
      }

      Spacer(modifier = Modifier.height(8.dp))
    }
  }
}

@Composable
private fun LanguageOptionCard(
  language: LanguageOption,
  isSelected: Boolean,
  onSelect: () -> Unit
) {
  val borderColor = if (isSelected) BrandTealPrimary else SurfaceBorder
  val bgColor = if (isSelected) BrandMintBackground else if (language.isSupported) Color.White else SurfaceCard

  Card(
    modifier = Modifier
      .fillMaxWidth()
      .clip(RoundedCornerShape(16.dp))
      .border(
        width = if (isSelected) 2.dp else 1.dp,
        color = borderColor,
        shape = RoundedCornerShape(16.dp)
      )
      .clickable(enabled = language.isSupported, onClick = onSelect)
      .testTag("lang_card_${language.code}"),
    colors = CardDefaults.cardColors(containerColor = bgColor),
    shape = RoundedCornerShape(16.dp)
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
          .size(42.dp)
          .background(
            if (isSelected) BrandTealPrimary else Color(0xFFE2E8F0),
            CircleShape
          )
      ) {
        if (isSelected) {
          Icon(
            imageVector = Icons.Default.Check,
            contentDescription = "Selected",
            tint = Color.White,
            modifier = Modifier.size(22.dp)
          )
        } else {
          Text(
            text = language.code.uppercase(),
            fontWeight = FontWeight.Bold,
            color = BrandNavyMuted,
            fontSize = 13.sp
          )
        }
      }

      Spacer(modifier = Modifier.width(14.dp))

      Column(modifier = Modifier.weight(1f)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
          Text(
            text = language.nativeName,
            fontWeight = FontWeight.Bold,
            fontSize = 17.sp,
            color = BrandNavyText
          )
          Spacer(modifier = Modifier.width(8.dp))
          Text(
            text = "(${language.englishName})",
            fontSize = 14.sp,
            color = BrandNavyMuted
          )
        }
        Spacer(modifier = Modifier.height(2.dp))
        Text(
          text = language.subtitle,
          fontSize = 12.sp,
          color = if (language.isSupported) BrandTealPrimary else Color(0xFF94A3B8)
        )
      }

      if (!language.isSupported) {
        Box(
          modifier = Modifier
            .background(Color(0xFFF1F5F9), RoundedCornerShape(8.dp))
            .padding(horizontal = 8.dp, vertical = 4.dp)
        ) {
          Text(
            text = "Planned",
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
            color = Color(0xFF64748B)
          )
        }
      } else if (isSelected) {
        Box(
          modifier = Modifier
            .background(BrandTealPrimary, RoundedCornerShape(8.dp))
            .padding(horizontal = 8.dp, vertical = 4.dp)
        ) {
          Text(
            text = "Active",
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            color = Color.White
          )
        }
      }
    }
  }
}
