package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.ExplanationConstants
import com.example.model.LanguageOption
import com.example.ui.components.NexzyTopAppBar
import com.example.ui.theme.NexzyTheme

@Composable
fun LanguageScreen(
  languages: List<LanguageOption>,
  selectedLanguageCode: String,
  onLanguageSelected: (String) -> Unit,
  onConfirm: () -> Unit,
  onNavigateBack: () -> Unit,
  modifier: Modifier = Modifier,
  isOnboarding: Boolean = false,
  isSaving: Boolean = false,
  errorMessage: String? = null,
  titleText: String? = null,
  subtitleText: String? = null,
  confirmButtonText: String? = null
) {
  BackHandler(enabled = isOnboarding) {
    // Language selection immediately after login requires an explicit confirmed choice.
  }

  val palette = NexzyTheme.colors

  Column(
    modifier = modifier
      .fillMaxSize()
      .background(MaterialTheme.colorScheme.background)
  ) {
    NexzyTopAppBar(
      title = titleText ?: "Select Language",
      showBackButton = !isOnboarding,
      onBackClick = onNavigateBack,
      subtitle = subtitleText ?: if (isOnboarding) {
        "Choose your preferred explanation language"
      } else {
        "Report explanations & audio narration"
      }
    )

    Column(
      modifier = Modifier
        .fillMaxSize()
        .navigationBarsPadding()
        .padding(horizontal = 20.dp, vertical = 12.dp)
    ) {
      Text(
        text = "Which language do you prefer?",
        style = MaterialTheme.typography.titleLarge,
        color = MaterialTheme.colorScheme.onBackground,
        fontWeight = FontWeight.Bold
      )

      Spacer(modifier = Modifier.height(4.dp))

      Text(
        text = "First release supports English, Hindi, Bengali, and Marathi. Other regional languages are in progress.",
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant
      )

      Spacer(modifier = Modifier.height(16.dp))

      LazyColumn(
        modifier = Modifier
          .weight(1f)
          .fillMaxWidth()
          .testTag("language_list"),
        verticalArrangement = Arrangement.spacedBy(10.dp)
      ) {
        items(languages) { lang ->
          LanguageOptionCard(
            language = lang,
            isSelected = lang.code == selectedLanguageCode,
            enabled = lang.isSupported && !isSaving,
            onSelect = {
              if (lang.isSupported && !isSaving) {
                onLanguageSelected(lang.code)
              }
            }
          )
        }
      }

      if (!errorMessage.isNullOrBlank()) {
        Spacer(modifier = Modifier.height(10.dp))
        Card(
          shape = RoundedCornerShape(12.dp),
          colors = CardDefaults.cardColors(containerColor = palette.statusAlertContainer),
          border = BorderStroke(1.dp, palette.statusAlertBorder),
          modifier = Modifier
            .fillMaxWidth()
            .testTag("language_error_banner")
        ) {
          Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
          ) {
            Icon(
              imageVector = Icons.Default.ErrorOutline,
              contentDescription = "Language save error",
              tint = palette.statusAlert,
              modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(10.dp))
            Text(
              text = errorMessage,
              fontSize = 13.sp,
              color = palette.statusAlertText,
              lineHeight = 18.sp
            )
          }
        }
      }

      Spacer(modifier = Modifier.height(12.dp))

      val canConfirm = !isSaving && selectedLanguageCode.trim().lowercase() in ExplanationConstants.SUPPORTED_LANGUAGE_CODES
      Button(
        onClick = onConfirm,
        enabled = canConfirm,
        colors = ButtonDefaults.buttonColors(
          containerColor = MaterialTheme.colorScheme.primary,
          contentColor = MaterialTheme.colorScheme.onPrimary,
          disabledContainerColor = palette.disabledContainer,
          disabledContentColor = palette.disabledContent
        ),
        shape = RoundedCornerShape(28.dp),
        modifier = Modifier
          .fillMaxWidth()
          .height(52.dp)
          .testTag("btn_confirm_language")
      ) {
        if (isSaving) {
          CircularProgressIndicator(
            modifier = Modifier.size(20.dp),
            color = MaterialTheme.colorScheme.onPrimary,
            strokeWidth = 2.5.dp
          )
          Spacer(modifier = Modifier.width(10.dp))
          Text(
            text = "Saving Preference...",
            fontSize = 15.sp,
            fontWeight = FontWeight.Bold
          )
        } else {
          Text(
            text = confirmButtonText
              ?: if (isOnboarding) "Continue with Selected Language" else "Save Language Preference",
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold
          )
        }
      }

      Spacer(modifier = Modifier.height(8.dp))
    }
  }
}

@Composable
private fun LanguageOptionCard(
  language: LanguageOption,
  isSelected: Boolean,
  enabled: Boolean = language.isSupported,
  onSelect: () -> Unit
) {
  val borderColor = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
  val bgColor = when {
    isSelected -> MaterialTheme.colorScheme.primaryContainer
    language.isSupported -> MaterialTheme.colorScheme.surface
    else -> MaterialTheme.colorScheme.background
  }

  Card(
    modifier = Modifier
      .fillMaxWidth()
      .clip(RoundedCornerShape(16.dp))
      .border(
        width = if (isSelected) 2.dp else 1.dp,
        color = borderColor,
        shape = RoundedCornerShape(16.dp)
      )
      .clickable(enabled = enabled, onClick = onSelect)
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
            if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
            CircleShape
          )
      ) {
        if (isSelected) {
          Icon(
            imageVector = Icons.Default.Check,
            contentDescription = "Selected",
            tint = MaterialTheme.colorScheme.onPrimary,
            modifier = Modifier.size(22.dp)
          )
        } else {
          Text(
            text = language.code.uppercase(),
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
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
            color = MaterialTheme.colorScheme.onSurface
          )
          Spacer(modifier = Modifier.width(8.dp))
          Text(
            text = "(${language.englishName})",
            fontSize = 14.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
          )
        }
        Spacer(modifier = Modifier.height(2.dp))
        Text(
          text = language.subtitle,
          fontSize = 12.sp,
          color = if (language.isSupported) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
        )
      }

      if (!language.isSupported) {
        Box(
          modifier = Modifier
            .background(MaterialTheme.colorScheme.outline.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
            .padding(horizontal = 8.dp, vertical = 4.dp)
        ) {
          Text(
            text = "Planned",
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurfaceVariant
          )
        }
      } else if (isSelected) {
        Box(
          modifier = Modifier
            .background(MaterialTheme.colorScheme.primary, RoundedCornerShape(8.dp))
            .padding(horizontal = 8.dp, vertical = 4.dp)
        ) {
          Text(
            text = "Active",
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onPrimary
          )
        }
      }
    }
  }
}
