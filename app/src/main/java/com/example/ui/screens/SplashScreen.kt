package com.example.ui.screens

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.ui.theme.BrandMintBackground
import com.example.ui.theme.BrandNavyMuted
import com.example.ui.theme.BrandNavyText
import com.example.ui.theme.BrandTealPrimary

@Composable
fun SplashScreen(
  isAuthenticated: Boolean = false,
  onNavigateToNext: () -> Unit,
  modifier: Modifier = Modifier
) {
  val scale = remember { Animatable(0.85f) }
  val alpha = remember { Animatable(0f) }

  LaunchedEffect(Unit) {
    scale.animateTo(
      targetValue = 1f,
      animationSpec = tween(durationMillis = 800, easing = FastOutSlowInEasing)
    )
    alpha.animateTo(
      targetValue = 1f,
      animationSpec = tween(durationMillis = 600)
    )
  }

  Box(
    modifier = modifier
      .fillMaxSize()
      .background(Color.White)
      .padding(24.dp)
  ) {
    Column(
      modifier = Modifier
        .fillMaxSize()
        .scale(scale.value)
        .alpha(alpha.value),
      verticalArrangement = Arrangement.Center,
      horizontalAlignment = Alignment.CenterHorizontally
    ) {
      Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
          .size(120.dp)
          .clip(CircleShape)
          .background(BrandMintBackground)
      ) {
        Image(
          painter = painterResource(id = R.drawable.nexzyhealth_icon_1794245550079),
          contentDescription = "NexzyHealth Logo",
          modifier = Modifier
            .size(90.dp)
            .clip(RoundedCornerShape(20.dp))
        )
      }

      Spacer(modifier = Modifier.height(24.dp))

      Text(
        text = "NexzyHealth",
        style = MaterialTheme.typography.headlineLarge,
        color = BrandTealPrimary,
        fontWeight = FontWeight.ExtraBold,
        letterSpacing = (-0.5).sp
      )

      Spacer(modifier = Modifier.height(8.dp))

      Text(
        text = "Understand Your Health Report. Simply.",
        style = MaterialTheme.typography.titleMedium,
        color = BrandNavyText,
        fontWeight = FontWeight.SemiBold,
        textAlign = TextAlign.Center
      )

      Spacer(modifier = Modifier.height(12.dp))

      Text(
        text = "Demystifying complex laboratory numbers into plain, everyday language for you and your family.",
        style = MaterialTheme.typography.bodyMedium,
        color = BrandNavyMuted,
        textAlign = TextAlign.Center,
        modifier = Modifier.padding(horizontal = 20.dp)
      )

      Spacer(modifier = Modifier.height(48.dp))

      Button(
        onClick = onNavigateToNext,
        colors = ButtonDefaults.buttonColors(containerColor = BrandTealPrimary),
        shape = RoundedCornerShape(28.dp),
        modifier = Modifier
          .fillMaxWidth(0.75f)
          .height(52.dp)
          .testTag("splash_continue_button")
      ) {
        Text(
          text = if (isAuthenticated) "Continue to Dashboard" else "Get Started",
          fontSize = 16.sp,
          fontWeight = FontWeight.Bold,
          color = Color.White
        )
      }
    }
  }
}
