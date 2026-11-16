package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.HelpOutline
import androidx.compose.material.icons.filled.Language
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.BrandMintBackground
import com.example.ui.theme.BrandNavyMuted
import com.example.ui.theme.BrandNavyText
import com.example.ui.theme.BrandTealPrimary

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NexzyTopAppBar(
  title: String,
  showBackButton: Boolean = false,
  onBackClick: () -> Unit = {},
  selectedLanguageName: String? = null,
  onLanguageClick: (() -> Unit)? = null,
  onHelpClick: (() -> Unit)? = null,
  subtitle: String? = null,
  modifier: Modifier = Modifier
) {
  TopAppBar(
    modifier = modifier,
    title = {
      Row(verticalAlignment = Alignment.CenterVertically) {
        androidx.compose.foundation.layout.Column {
          Text(
            text = title,
            style = MaterialTheme.typography.titleLarge,
            color = BrandNavyText,
            fontWeight = FontWeight.Bold
          )
          if (!subtitle.isNullOrBlank()) {
            Text(
              text = subtitle,
              style = MaterialTheme.typography.bodySmall,
              color = BrandNavyMuted
            )
          }
        }
      }
    },
    navigationIcon = {
      if (showBackButton) {
        IconButton(
          onClick = onBackClick,
          modifier = Modifier.testTag("top_bar_back_button")
        ) {
          Icon(
            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
            contentDescription = "Navigate back",
            tint = BrandTealPrimary
          )
        }
      }
    },
    actions = {
      if (selectedLanguageName != null && onLanguageClick != null) {
        Row(
          verticalAlignment = Alignment.CenterVertically,
          modifier = Modifier
            .padding(end = 4.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(BrandMintBackground)
            .clickable(onClick = onLanguageClick)
            .padding(horizontal = 8.dp, vertical = 6.dp)
            .testTag("quick_language_switch")
        ) {
          Icon(
            imageVector = Icons.Default.Language,
            contentDescription = "Switch Language",
            tint = BrandTealPrimary,
            modifier = Modifier.size(16.dp)
          )
          Spacer(modifier = Modifier.width(4.dp))
          Text(
            text = selectedLanguageName,
            color = BrandTealPrimary,
            fontWeight = FontWeight.SemiBold,
            fontSize = 12.sp
          )
        }
      }

      if (onHelpClick != null) {
        IconButton(
          onClick = onHelpClick,
          modifier = Modifier.testTag("top_bar_help_button")
        ) {
          Icon(
            imageVector = Icons.Default.HelpOutline,
            contentDescription = "Help & Support",
            tint = BrandNavyMuted
          )
        }
      }
    },
    colors = TopAppBarDefaults.topAppBarColors(
      containerColor = Color.White,
      titleContentColor = BrandNavyText
    )
  )
}
