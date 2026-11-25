package com.example.ui.screens

import android.app.Activity
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Sensors
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.auth.PhoneVerificationStep
import com.example.ui.auth.PhoneVerificationViewModel
import com.example.ui.components.NexzyTopAppBar
import com.example.ui.theme.NexzyTheme

@Composable
fun OtpVerificationScreen(
  viewModel: PhoneVerificationViewModel,
  onVerificationSuccess: (String) -> Unit,
  onChangeNumberClick: () -> Unit,
  modifier: Modifier = Modifier
) {
  val uiState by viewModel.uiState.collectAsState()
  val context = LocalContext.current
  val activity = context as? Activity
  val palette = NexzyTheme.colors

  LaunchedEffect(uiState.step) {
    if (uiState.step is PhoneVerificationStep.Success) {
      val verifiedPhone = (uiState.step as PhoneVerificationStep.Success).formattedPhoneNumber
      onVerificationSuccess(verifiedPhone)
    }
  }

  Column(
    modifier = modifier
      .fillMaxSize()
      .background(MaterialTheme.colorScheme.background)
  ) {
    NexzyTopAppBar(
      title = "Enter Verification Code",
      subtitle = "SMS OTP sent to your phone",
      showBackButton = true,
      onBackClick = onChangeNumberClick
    )

    Column(
      modifier = Modifier
        .fillMaxSize()
        .navigationBarsPadding()
        .verticalScroll(rememberScrollState())
        .padding(horizontal = 24.dp, vertical = 16.dp),
      horizontalAlignment = Alignment.CenterHorizontally
    ) {
      Spacer(modifier = Modifier.height(12.dp))

      Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
          .size(72.dp)
          .clip(CircleShape)
          .background(MaterialTheme.colorScheme.primaryContainer)
          .border(2.dp, MaterialTheme.colorScheme.outlineVariant, CircleShape)
      ) {
        Icon(
          imageVector = Icons.Default.Key,
          contentDescription = null,
          tint = MaterialTheme.colorScheme.primary,
          modifier = Modifier.size(36.dp)
        )
      }

      Spacer(modifier = Modifier.height(18.dp))

      Text(
        text = "Enter 6-Digit Code",
        fontSize = 22.sp,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.onBackground,
        textAlign = TextAlign.Center
      )

      Spacer(modifier = Modifier.height(8.dp))

      Text(
        text = "We sent a 6-digit verification code to:",
        fontSize = 14.sp,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center
      )

      Spacer(modifier = Modifier.height(4.dp))

      Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
      ) {
        Text(
          text = uiState.formattedPhoneNumber.ifBlank { "+91 ••••• •••••" },
          fontSize = 16.sp,
          fontWeight = FontWeight.Bold,
          color = MaterialTheme.colorScheme.onBackground
        )
        Spacer(modifier = Modifier.width(6.dp))
        IconButton(
          onClick = onChangeNumberClick,
          modifier = Modifier.testTag("change_phone_number_btn")
        ) {
          Icon(
            imageVector = Icons.Default.Edit,
            contentDescription = "Edit Mobile Number",
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(18.dp)
          )
        }
      }

      Spacer(modifier = Modifier.height(16.dp))

      Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier.fillMaxWidth()
      ) {
        Row(
          modifier = Modifier.padding(12.dp),
          verticalAlignment = Alignment.CenterVertically
        ) {
          if (uiState.isAutoVerifying) {
            CircularProgressIndicator(
              modifier = Modifier.size(18.dp),
              color = MaterialTheme.colorScheme.primary,
              strokeWidth = 2.dp
            )
          } else {
            Icon(
              imageVector = Icons.Default.Sensors,
              contentDescription = null,
              tint = MaterialTheme.colorScheme.primary,
              modifier = Modifier.size(18.dp)
            )
          }
          Spacer(modifier = Modifier.width(10.dp))
          Text(
            text = if (uiState.isAutoVerifying) {
              "SMS code auto-detected! Linking mobile to account..."
            } else {
              "Automatic SMS verification active. We'll auto-read the code when received."
            },
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onPrimaryContainer,
            lineHeight = 16.sp
          )
        }
      }

      Spacer(modifier = Modifier.height(24.dp))

      OutlinedTextField(
        value = uiState.otpCode,
        onValueChange = { viewModel.onOtpCodeChanged(it) },
        placeholder = {
          Text(
            text = "• • • • • •",
            modifier = Modifier.fillMaxWidth(),
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f),
            fontSize = 26.sp,
            letterSpacing = 8.sp
          )
        },
        textStyle = TextStyle(
          textAlign = TextAlign.Center,
          fontSize = 26.sp,
          fontWeight = FontWeight.Bold,
          letterSpacing = 8.sp,
          color = MaterialTheme.colorScheme.onSurface,
          fontFamily = FontFamily.Monospace
        ),
        singleLine = true,
        keyboardOptions = KeyboardOptions(
          keyboardType = KeyboardType.NumberPassword,
          imeAction = ImeAction.Done
        ),
        keyboardActions = KeyboardActions(
          onDone = {
            if (uiState.isValidOtp && !uiState.isLoading) {
              viewModel.verifyManualOtp()
            }
          }
        ),
        shape = RoundedCornerShape(16.dp),
        colors = OutlinedTextFieldDefaults.colors(
          focusedBorderColor = MaterialTheme.colorScheme.primary,
          unfocusedBorderColor = MaterialTheme.colorScheme.outline,
          cursorColor = MaterialTheme.colorScheme.primary,
          focusedContainerColor = MaterialTheme.colorScheme.surface,
          unfocusedContainerColor = MaterialTheme.colorScheme.surface
        ),
        modifier = Modifier
          .fillMaxWidth()
          .height(68.dp)
          .testTag("otp_input")
      )

      Spacer(modifier = Modifier.height(14.dp))

      if (uiState.errorMessage != null) {
        Card(
          shape = RoundedCornerShape(12.dp),
          colors = CardDefaults.cardColors(containerColor = palette.statusAlertContainer),
          border = BorderStroke(1.dp, palette.statusAlertBorder),
          modifier = Modifier
            .fillMaxWidth()
            .testTag("otp_error_banner")
        ) {
          Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
          ) {
            Icon(
              imageVector = Icons.Default.ErrorOutline,
              contentDescription = null,
              tint = palette.statusAlert,
              modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(10.dp))
            Text(
              text = uiState.errorMessage ?: "",
              fontSize = 13.sp,
              color = palette.statusAlertText,
              lineHeight = 18.sp
            )
          }
        }
        Spacer(modifier = Modifier.height(14.dp))
      }

      Button(
        onClick = { viewModel.verifyManualOtp() },
        enabled = uiState.isValidOtp && !uiState.isLoading,
        shape = RoundedCornerShape(28.dp),
        colors = ButtonDefaults.buttonColors(
          containerColor = MaterialTheme.colorScheme.primary,
          contentColor = MaterialTheme.colorScheme.onPrimary,
          disabledContainerColor = palette.disabledContainer,
          disabledContentColor = palette.disabledContent
        ),
        modifier = Modifier
          .fillMaxWidth()
          .height(52.dp)
          .testTag("verify_otp_btn")
      ) {
        if (uiState.isLoading) {
          CircularProgressIndicator(
            modifier = Modifier.size(22.dp),
            color = MaterialTheme.colorScheme.onPrimary,
            strokeWidth = 2.5.dp
          )
          Spacer(modifier = Modifier.width(10.dp))
          Text(
            text = uiState.statusMessage ?: "Verifying...",
            fontSize = 15.sp,
            fontWeight = FontWeight.Bold
          )
        } else {
          Icon(
            imageVector = Icons.Default.CheckCircle,
            contentDescription = null,
            modifier = Modifier.size(18.dp)
          )
          Spacer(modifier = Modifier.width(8.dp))
          Text(
            text = "Verify & Link Mobile",
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold
          )
        }
      }

      Spacer(modifier = Modifier.height(20.dp))

      Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
        modifier = Modifier.fillMaxWidth()
      ) {
        Text(
          text = "Didn't receive the SMS? ",
          fontSize = 13.sp,
          color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        if (uiState.cooldownSeconds > 0) {
          Text(
            text = "Resend in ${uiState.cooldownSeconds}s",
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurfaceVariant
          )
        } else {
          TextButton(
            onClick = {
              if (activity != null) {
                viewModel.resendVerificationCode(activity)
              }
            },
            enabled = !uiState.isLoading,
            modifier = Modifier.testTag("resend_otp_btn")
          ) {
            Icon(
              imageVector = Icons.Default.Refresh,
              contentDescription = null,
              tint = MaterialTheme.colorScheme.primary,
              modifier = Modifier.size(16.dp)
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(
              text = "Resend Code",
              fontSize = 13.sp,
              fontWeight = FontWeight.Bold,
              color = MaterialTheme.colorScheme.primary
            )
          }
        }
      }

      Spacer(modifier = Modifier.height(16.dp))

      OutlinedButton(
        onClick = onChangeNumberClick,
        shape = RoundedCornerShape(28.dp),
        colors = ButtonDefaults.outlinedButtonColors(
          containerColor = MaterialTheme.colorScheme.surface,
          contentColor = MaterialTheme.colorScheme.onSurfaceVariant
        ),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        modifier = Modifier
          .fillMaxWidth()
          .height(48.dp)
      ) {
        Text(
          text = "Change Mobile Number",
          fontSize = 14.sp,
          fontWeight = FontWeight.Medium
        )
      }

      Spacer(modifier = Modifier.height(24.dp))
    }
  }
}
