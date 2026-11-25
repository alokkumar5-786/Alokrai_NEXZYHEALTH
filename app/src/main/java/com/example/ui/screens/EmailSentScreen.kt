package com.example.ui.screens

import android.content.Intent
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.MailOutline
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.auth.EmailLoginViewModel
import com.example.ui.components.NexzyTopAppBar

@Composable
fun EmailSentScreen(
  viewModel: EmailLoginViewModel,
  onNavigateBackToEmail: () -> Unit,
  modifier: Modifier = Modifier
) {
  val uiState by viewModel.uiState.collectAsState()
  val context = LocalContext.current

  Column(
    modifier = modifier
      .fillMaxSize()
      .background(MaterialTheme.colorScheme.background)
  ) {
    NexzyTopAppBar(
      title = "Check Your Email",
      subtitle = "One-click authentication link sent",
      showBackButton = true,
      onBackClick = onNavigateBackToEmail
    )

    Column(
      modifier = Modifier
        .fillMaxSize()
        .navigationBarsPadding()
        .verticalScroll(rememberScrollState())
        .padding(horizontal = 24.dp, vertical = 20.dp),
      horizontalAlignment = Alignment.CenterHorizontally
    ) {
      Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
          .size(80.dp)
          .background(MaterialTheme.colorScheme.primaryContainer, CircleShape)
      ) {
        Icon(
          imageVector = Icons.Default.MailOutline,
          contentDescription = null,
          tint = MaterialTheme.colorScheme.primary,
          modifier = Modifier.size(44.dp)
        )
      }

      Spacer(modifier = Modifier.height(20.dp))

      Text(
        text = "Sign-In Link Sent!",
        style = MaterialTheme.typography.headlineSmall,
        color = MaterialTheme.colorScheme.onBackground,
        fontWeight = FontWeight.Bold,
        textAlign = TextAlign.Center
      )

      Spacer(modifier = Modifier.height(8.dp))

      Text(
        text = "We sent a secure, passwordless sign-in link to:",
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center
      )

      Spacer(modifier = Modifier.height(8.dp))

      Box(
        modifier = Modifier
          .background(MaterialTheme.colorScheme.primaryContainer, RoundedCornerShape(12.dp))
          .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(12.dp))
          .padding(horizontal = 16.dp, vertical = 8.dp)
      ) {
        Text(
          text = uiState.sentToEmail.ifBlank { uiState.email },
          fontWeight = FontWeight.Bold,
          fontSize = 15.sp,
          color = MaterialTheme.colorScheme.primary,
          textAlign = TextAlign.Center
        )
      }

      Spacer(modifier = Modifier.height(24.dp))

      Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        modifier = Modifier.fillMaxWidth()
      ) {
        Column(modifier = Modifier.padding(18.dp)) {
          Text(
            text = "How to sign in:",
            fontWeight = FontWeight.Bold,
            fontSize = 14.sp,
            color = MaterialTheme.colorScheme.onSurface
          )
          Spacer(modifier = Modifier.height(10.dp))
          InstructionStep("1", "Open your email inbox (such as Gmail or Outlook).")
          Spacer(modifier = Modifier.height(8.dp))
          InstructionStep("2", "Look for an email from NexzyHealth titled 'Sign in to NexzyHealth'.")
          Spacer(modifier = Modifier.height(8.dp))
          InstructionStep("3", "Tap the secure link in the email. NexzyHealth will open and verify you automatically.")
        }
      }

      Spacer(modifier = Modifier.height(24.dp))

      Button(
        onClick = {
          val emailIntent = Intent(Intent.ACTION_MAIN).apply {
            addCategory(Intent.CATEGORY_APP_EMAIL)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
          }
          try {
            context.startActivity(emailIntent)
          } catch (e: Exception) {
            val genericIntent = Intent(Intent.ACTION_VIEW).apply {
              data = android.net.Uri.parse("mailto:")
            }
            try {
              context.startActivity(genericIntent)
            } catch (_: Exception) {}
          }
        },
        colors = ButtonDefaults.buttonColors(
          containerColor = MaterialTheme.colorScheme.primary,
          contentColor = MaterialTheme.colorScheme.onPrimary
        ),
        shape = RoundedCornerShape(28.dp),
        modifier = Modifier
          .fillMaxWidth()
          .height(52.dp)
          .testTag("btn_open_email_app")
      ) {
        Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = null)
        Spacer(modifier = Modifier.width(8.dp))
        Text(
          text = "Open Email App",
          fontSize = 16.sp,
          fontWeight = FontWeight.Bold
        )
      }

      Spacer(modifier = Modifier.height(14.dp))

      OutlinedButton(
        onClick = { viewModel.resendSignInLink() },
        enabled = uiState.cooldownSeconds == 0 && !uiState.isLoading,
        shape = RoundedCornerShape(28.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        colors = ButtonDefaults.outlinedButtonColors(
          containerColor = MaterialTheme.colorScheme.surface,
          contentColor = MaterialTheme.colorScheme.primary,
          disabledContentColor = MaterialTheme.colorScheme.onSurfaceVariant
        ),
        modifier = Modifier
          .fillMaxWidth()
          .height(50.dp)
          .testTag("btn_resend_email_link")
      ) {
        if (uiState.isLoading) {
          CircularProgressIndicator(
            color = MaterialTheme.colorScheme.primary,
            strokeWidth = 2.dp,
            modifier = Modifier.size(20.dp)
          )
        } else {
          Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
          Spacer(modifier = Modifier.width(6.dp))
          Text(
            text = if (uiState.cooldownSeconds > 0) {
              "Resend Link in ${uiState.cooldownSeconds}s"
            } else {
              "Resend Sign-In Link"
            },
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold
          )
        }
      }

      if (uiState.errorMessage != null) {
        Spacer(modifier = Modifier.height(8.dp))
        Text(
          text = uiState.errorMessage!!,
          color = MaterialTheme.colorScheme.error,
          fontSize = 12.sp,
          textAlign = TextAlign.Center
        )
      }

      Spacer(modifier = Modifier.height(14.dp))

      OutlinedButton(
        onClick = onNavigateBackToEmail,
        shape = RoundedCornerShape(28.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        colors = ButtonDefaults.outlinedButtonColors(
          containerColor = MaterialTheme.colorScheme.surface,
          contentColor = MaterialTheme.colorScheme.onSurfaceVariant
        ),
        modifier = Modifier
          .fillMaxWidth()
          .height(48.dp)
          .testTag("btn_change_email")
      ) {
        Icon(
          Icons.AutoMirrored.Filled.ArrowBack,
          contentDescription = null,
          modifier = Modifier.size(16.dp)
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text("Use a different email address", fontSize = 13.sp)
      }

      Spacer(modifier = Modifier.height(20.dp))
    }
  }
}

@Composable
private fun InstructionStep(number: String, text: String) {
  Row(verticalAlignment = Alignment.Top) {
    Box(
      contentAlignment = Alignment.Center,
      modifier = Modifier
        .size(22.dp)
        .background(MaterialTheme.colorScheme.primary, CircleShape)
    ) {
      Text(
        text = number,
        color = MaterialTheme.colorScheme.onPrimary,
        fontSize = 11.sp,
        fontWeight = FontWeight.Bold
      )
    }
    Spacer(modifier = Modifier.width(10.dp))
    Text(
      text = text,
      color = MaterialTheme.colorScheme.onSurface,
      fontSize = 13.sp,
      lineHeight = 18.sp
    )
  }
}
