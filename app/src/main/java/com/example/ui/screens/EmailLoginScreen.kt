package com.example.ui.screens

import androidx.compose.foundation.background
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
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.MarkEmailRead
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.auth.EmailLoginViewModel
import com.example.ui.components.NexzyTopAppBar
import com.example.ui.theme.BrandMintBackground
import com.example.ui.theme.BrandNavyMuted
import com.example.ui.theme.BrandNavyText
import com.example.ui.theme.BrandTealPrimary
import com.example.ui.theme.SurfaceBorder

@Composable
fun EmailLoginScreen(
  viewModel: EmailLoginViewModel,
  onNavigateToEmailSent: (sentEmail: String) -> Unit,
  onNavigateBack: () -> Unit,
  modifier: Modifier = Modifier
) {
  val uiState by viewModel.uiState.collectAsState()

  LaunchedEffect(uiState.isLinkSent) {
    if (uiState.isLinkSent) {
      onNavigateToEmailSent(uiState.sentToEmail)
    }
  }

  Column(
    modifier = modifier
      .fillMaxSize()
      .background(Color.White)
  ) {
    NexzyTopAppBar(
      title = "Sign In",
      subtitle = "Passwordless email verification",
      showBackButton = true,
      onBackClick = onNavigateBack
    )

    Column(
      modifier = Modifier
        .fillMaxSize()
        .verticalScroll(rememberScrollState())
        .padding(horizontal = 24.dp, vertical = 20.dp),
      horizontalAlignment = Alignment.CenterHorizontally
    ) {
      Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
          .size(64.dp)
          .background(BrandMintBackground, CircleShape)
      ) {
        Icon(
          imageVector = Icons.Default.MarkEmailRead,
          contentDescription = null,
          tint = BrandTealPrimary,
          modifier = Modifier.size(36.dp)
        )
      }

      Spacer(modifier = Modifier.height(16.dp))

      Text(
        text = "Passwordless Email Login",
        style = MaterialTheme.typography.headlineSmall,
        color = BrandNavyText,
        fontWeight = FontWeight.Bold,
        textAlign = TextAlign.Center
      )

      Spacer(modifier = Modifier.height(8.dp))

      Text(
        text = "Enter your email address to receive a secure sign-in link. No password required.",
        style = MaterialTheme.typography.bodyMedium,
        color = BrandNavyMuted,
        textAlign = TextAlign.Center
      )

      Spacer(modifier = Modifier.height(28.dp))

      OutlinedTextField(
        value = uiState.email,
        onValueChange = { viewModel.onEmailChanged(it) },
        label = { Text("Your Email Address") },
        placeholder = { Text("e.g. priya.caregiver@example.com") },
        leadingIcon = {
          Icon(Icons.Default.Email, contentDescription = null, tint = BrandTealPrimary)
        },
        isError = uiState.errorMessage != null,
        keyboardOptions = KeyboardOptions(
          keyboardType = KeyboardType.Email,
          imeAction = ImeAction.Done
        ),
        keyboardActions = KeyboardActions(
          onDone = { viewModel.sendSignInLink() }
        ),
        singleLine = true,
        modifier = Modifier
          .fillMaxWidth()
          .testTag("input_email_address"),
        shape = RoundedCornerShape(12.dp),
        colors = OutlinedTextFieldDefaults.colors(
          focusedBorderColor = BrandTealPrimary,
          unfocusedBorderColor = SurfaceBorder
        )
      )

      if (uiState.errorMessage != null) {
        Spacer(modifier = Modifier.height(6.dp))
        Text(
          text = uiState.errorMessage!!,
          color = MaterialTheme.colorScheme.error,
          fontSize = 12.sp,
          modifier = Modifier
            .fillMaxWidth()
            .testTag("email_error_text")
        )
      }

      Spacer(modifier = Modifier.height(24.dp))

      Button(
        onClick = { viewModel.sendSignInLink() },
        enabled = !uiState.isLoading && uiState.isValidEmail,
        colors = ButtonDefaults.buttonColors(
          containerColor = BrandTealPrimary,
          disabledContainerColor = BrandTealPrimary.copy(alpha = 0.5f)
        ),
        shape = RoundedCornerShape(28.dp),
        modifier = Modifier
          .fillMaxWidth()
          .height(52.dp)
          .testTag("btn_send_signin_link")
      ) {
        if (uiState.isLoading) {
          CircularProgressIndicator(
            color = Color.White,
            strokeWidth = 2.5.dp,
            modifier = Modifier.size(22.dp)
          )
        } else {
          Text(
            text = "Send Sign-In Link",
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold,
            color = Color.White
          )
        }
      }

      Spacer(modifier = Modifier.height(36.dp))

      Row(
        modifier = Modifier
          .fillMaxWidth()
          .background(BrandMintBackground, RoundedCornerShape(12.dp))
          .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically
      ) {
        Icon(
          imageVector = Icons.Default.Shield,
          contentDescription = null,
          tint = BrandTealPrimary,
          modifier = Modifier.size(24.dp)
        )
        Spacer(modifier = Modifier.width(10.dp))
        Text(
          text = "Passwordless login protects your medical privacy from credential stuffing and leaked passwords.",
          style = MaterialTheme.typography.bodySmall,
          color = BrandNavyText,
          fontSize = 12.sp,
          lineHeight = 16.sp
        )
      }
    }
  }
}
