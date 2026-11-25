package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.AutoStories
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.HealthAndSafety
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.MidnightNavy
import com.example.ui.theme.MistBlue
import com.example.ui.theme.NexzyTheme
import com.example.ui.theme.PaleBlue
import com.example.ui.theme.RoyalBlue
import com.example.ui.theme.TextOnDark

@Composable
fun WelcomeScreen(
  onGetStarted: () -> Unit,
  onSignInClick: () -> Unit,
  modifier: Modifier = Modifier
) {
  val palette = NexzyTheme.colors

  Box(
    modifier = modifier
      .fillMaxSize()
      .background(palette.verticalHeroBrush)
  ) {
    Column(
      modifier = Modifier
        .fillMaxSize()
        .safeDrawingPadding()
        .verticalScroll(rememberScrollState())
        .padding(horizontal = 24.dp, vertical = 28.dp),
      horizontalAlignment = Alignment.CenterHorizontally
    ) {
      Spacer(modifier = Modifier.height(12.dp))

      Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
          .size(68.dp)
          .background(TextOnDark, CircleShape)
          .border(2.dp, MistBlue, CircleShape)
      ) {
        Icon(
          imageVector = Icons.Default.HealthAndSafety,
          contentDescription = null,
          tint = RoyalBlue,
          modifier = Modifier.size(36.dp)
        )
      }

      Spacer(modifier = Modifier.height(18.dp))

      Text(
        text = "Understand Your Health Reports Better",
        style = MaterialTheme.typography.headlineSmall,
        color = TextOnDark,
        fontWeight = FontWeight.Bold,
        textAlign = TextAlign.Center
      )

      Spacer(modifier = Modifier.height(8.dp))

      Text(
        text = "Health reports made clear as daylight for adult patients and family caregivers.",
        style = MaterialTheme.typography.bodyLarge,
        color = MistBlue,
        textAlign = TextAlign.Center
      )

      Spacer(modifier = Modifier.height(28.dp))

      FeaturePill(
        icon = Icons.Default.AutoStories,
        title = "Clear 12-Year-Old Reading Level",
        desc = "No scary clinical jargon. Everyday analogies that explain what test results actually mean."
      )

      Spacer(modifier = Modifier.height(12.dp))

      FeaturePill(
        icon = Icons.Default.CameraAlt,
        title = "Scan Paper or Upload PDF",
        desc = "Use your camera on paper lab printouts or import digital lab PDFs directly."
      )

      Spacer(modifier = Modifier.height(12.dp))

      FeaturePill(
        icon = Icons.AutoMirrored.Filled.VolumeUp,
        title = "Warm Audio Voice Explanations",
        desc = "Listen to friendly spoken summaries with playback controls so you can relax and listen."
      )

      Spacer(modifier = Modifier.height(12.dp))

      FeaturePill(
        icon = Icons.Default.Translate,
        title = "Indian Regional Languages",
        desc = "Explanations available in English, Hindi, Bengali, and Marathi, with more languages coming soon."
      )

      Spacer(modifier = Modifier.height(32.dp))

      Button(
        onClick = onSignInClick,
        colors = ButtonDefaults.buttonColors(
          containerColor = TextOnDark,
          contentColor = MidnightNavy
        ),
        shape = RoundedCornerShape(28.dp),
        modifier = Modifier
          .fillMaxWidth()
          .height(52.dp)
          .testTag("welcome_continue_with_email_btn")
      ) {
        Text(
          text = "Continue with Email",
          fontSize = 16.sp,
          fontWeight = FontWeight.Bold,
          color = MidnightNavy
        )
      }

      Spacer(modifier = Modifier.height(12.dp))

      OutlinedButton(
        onClick = onGetStarted,
        shape = RoundedCornerShape(28.dp),
        border = BorderStroke(1.5.dp, MistBlue),
        colors = ButtonDefaults.outlinedButtonColors(contentColor = TextOnDark),
        modifier = Modifier
          .fillMaxWidth()
          .height(52.dp)
          .testTag("welcome_explore_demo_btn")
      ) {
        Text(
          text = "Explore Sample Demo Report",
          fontSize = 15.sp,
          fontWeight = FontWeight.SemiBold,
          color = TextOnDark
        )
      }

      Spacer(modifier = Modifier.height(16.dp))
    }
  }
}

@Composable
private fun FeaturePill(
  icon: ImageVector,
  title: String,
  desc: String
) {
  val palette = NexzyTheme.colors

  Card(
    colors = CardDefaults.cardColors(containerColor = palette.heroCardSurface),
    border = BorderStroke(1.dp, palette.heroCardBorder),
    shape = RoundedCornerShape(16.dp),
    modifier = Modifier.fillMaxWidth()
  ) {
    Row(
      modifier = Modifier.padding(16.dp),
      verticalAlignment = Alignment.Top
    ) {
      Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
          .size(40.dp)
          .background(PaleBlue, CircleShape)
      ) {
        Icon(
          imageVector = icon,
          contentDescription = null,
          tint = RoyalBlue,
          modifier = Modifier.size(22.dp)
        )
      }
      Spacer(modifier = Modifier.width(14.dp))
      Column {
        Text(
          text = title,
          fontWeight = FontWeight.Bold,
          color = TextOnDark,
          fontSize = 15.sp
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
          text = desc,
          color = MistBlue,
          fontSize = 13.sp,
          lineHeight = 18.sp
        )
      }
    }
  }
}
