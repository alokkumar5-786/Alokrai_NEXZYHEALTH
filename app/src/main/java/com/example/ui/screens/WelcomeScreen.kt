package com.example.ui.screens

import androidx.compose.foundation.background
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
import androidx.compose.material.icons.filled.AutoStories
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.HealthAndSafety
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material.icons.filled.VolumeUp
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.BrandMintBackground
import com.example.ui.theme.BrandMintSurface
import com.example.ui.theme.BrandNavyMuted
import com.example.ui.theme.BrandNavyText
import com.example.ui.theme.BrandTealPrimary

@Composable
fun WelcomeScreen(
  onGetStarted: () -> Unit,
  onSignInClick: () -> Unit,
  modifier: Modifier = Modifier
) {
  Column(
    modifier = modifier
      .fillMaxSize()
      .background(Color.White)
      .verticalScroll(rememberScrollState())
      .padding(horizontal = 24.dp, vertical = 28.dp),
    horizontalAlignment = Alignment.CenterHorizontally
  ) {
    Spacer(modifier = Modifier.height(16.dp))

    Box(
      contentAlignment = Alignment.Center,
      modifier = Modifier
        .size(64.dp)
        .background(BrandMintBackground, CircleShape)
    ) {
      Icon(
        imageVector = Icons.Default.HealthAndSafety,
        contentDescription = null,
        tint = BrandTealPrimary,
        modifier = Modifier.size(36.dp)
      )
    }

    Spacer(modifier = Modifier.height(16.dp))

    Text(
      text = "Understand Your Health Reports Better",
      style = MaterialTheme.typography.titleLarge,
      color = BrandNavyText,
      fontWeight = FontWeight.Bold,
      textAlign = TextAlign.Center
    )

    Spacer(modifier = Modifier.height(8.dp))

    Text(
      text = "Health reports made clear as daylight for adult patients and family caregivers.",
      style = MaterialTheme.typography.bodyLarge,
      color = BrandNavyMuted,
      textAlign = TextAlign.Center
    )

    Spacer(modifier = Modifier.height(28.dp))

    FeaturePill(
      icon = Icons.Default.AutoStories,
      title = "Clear 12-Year-Old Reading Level",
      desc = "No scary clinical jargon. Everyday analogies that explain what test results actually mean."
    )

    Spacer(modifier = Modifier.height(14.dp))

    FeaturePill(
      icon = Icons.Default.CameraAlt,
      title = "Scan Paper or Upload PDF",
      desc = "Use your camera on paper lab printouts or import digital lab PDFs directly."
    )

    Spacer(modifier = Modifier.height(14.dp))

    FeaturePill(
      icon = Icons.Default.VolumeUp,
      title = "Warm Audio Voice Explanations",
      desc = "Listen to friendly spoken summaries with playback controls so you can relax and listen."
    )

    Spacer(modifier = Modifier.height(14.dp))

    FeaturePill(
      icon = Icons.Default.Translate,
      title = "Indian Regional Languages",
      desc = "Explanations available in English, Hindi, and Bengali, with more languages coming soon."
    )

    Spacer(modifier = Modifier.height(36.dp))

    Button(
      onClick = onSignInClick,
      colors = ButtonDefaults.buttonColors(containerColor = BrandTealPrimary),
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
        color = Color.White
      )
    }

    Spacer(modifier = Modifier.height(12.dp))

    OutlinedButton(
      onClick = onGetStarted,
      shape = RoundedCornerShape(28.dp),
      colors = ButtonDefaults.outlinedButtonColors(contentColor = BrandTealPrimary),
      modifier = Modifier
        .fillMaxWidth()
        .height(52.dp)
        .testTag("welcome_explore_demo_btn")
    ) {
      Text(
        text = "Explore Sample Demo Report",
        fontSize = 15.sp,
        fontWeight = FontWeight.SemiBold
      )
    }

    Spacer(modifier = Modifier.height(16.dp))
  }
}

@Composable
private fun FeaturePill(
  icon: ImageVector,
  title: String,
  desc: String
) {
  Card(
    colors = CardDefaults.cardColors(containerColor = BrandMintSurface),
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
          .background(BrandMintBackground, CircleShape)
      ) {
        Icon(
          imageVector = icon,
          contentDescription = null,
          tint = BrandTealPrimary,
          modifier = Modifier.size(22.dp)
        )
      }
      Spacer(modifier = Modifier.width(14.dp))
      Column {
        Text(
          text = title,
          fontWeight = FontWeight.Bold,
          color = BrandNavyText,
          fontSize = 15.sp
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
          text = desc,
          color = BrandNavyMuted,
          fontSize = 13.sp,
          lineHeight = 18.sp
        )
      }
    }
  }
}
