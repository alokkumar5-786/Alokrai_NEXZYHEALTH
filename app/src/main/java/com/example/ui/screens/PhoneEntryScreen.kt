package com.example.ui.screens

import android.app.Activity
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Sms
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.auth.PhoneVerificationStep
import com.example.ui.auth.PhoneVerificationViewModel
import com.example.ui.auth.SupportedCountryCodes
import com.example.ui.components.NexzyTopAppBar
import com.example.ui.theme.NexzyTheme

@Composable
fun PhoneEntryScreen(
  viewModel: PhoneVerificationViewModel,
  onNavigateToOtp: () -> Unit,
  onSkipClick: () -> Unit,
  onNavigateBack: () -> Unit,
  modifier: Modifier = Modifier
) {
  val uiState by viewModel.uiState.collectAsState()
  val context = LocalContext.current
  val activity = context as? Activity
  val palette = NexzyTheme.colors

  var countryDropdownExpanded by remember { mutableStateOf(false) }

  LaunchedEffect(uiState.step) {
    if (uiState.step is PhoneVerificationStep.EnterOtp) {
      onNavigateToOtp()
    }
  }

  Column(
    modifier = modifier
      .fillMaxSize()
      .background(MaterialTheme.colorScheme.background)
  ) {
    NexzyTopAppBar(
      title = "Verify Mobile Number",
      subtitle = "Secure your NexzyHealth account",
      showBackButton = true,
      onBackClick = onNavigateBack
    )

    Column(
      modifier = Modifier
        .fillMaxSize()
        .navigationBarsPadding()
        .verticalScroll(rememberScrollState())
        .padding(horizontal = 24.dp, vertical = 16.dp),
      horizontalAlignment = Alignment.CenterHorizontally
    ) {
      Spacer(modifier = Modifier.height(8.dp))

      Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
          .size(72.dp)
          .clip(CircleShape)
          .background(MaterialTheme.colorScheme.primaryContainer)
          .border(2.dp, MaterialTheme.colorScheme.outlineVariant, CircleShape)
      ) {
        Icon(
          imageVector = Icons.Default.PhoneAndroid,
          contentDescription = null,
          tint = MaterialTheme.colorScheme.primary,
          modifier = Modifier.size(36.dp)
        )
      }

      Spacer(modifier = Modifier.height(18.dp))

      Text(
        text = "Add Your Mobile Number",
        fontSize = 22.sp,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.onBackground,
        textAlign = TextAlign.Center
      )

      Spacer(modifier = Modifier.height(8.dp))

      Text(
        text = "We will send an SMS with a 6-digit verification code. This links your verified mobile to your existing account.",
        fontSize = 14.sp,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
        lineHeight = 20.sp,
        modifier = Modifier.padding(horizontal = 8.dp)
      )

      Spacer(modifier = Modifier.height(24.dp))

      Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
      ) {
        Box {
          Card(
            shape = RoundedCornerShape(14.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
            modifier = Modifier
              .clickable { countryDropdownExpanded = true }
              .testTag("country_code_selector")
          ) {
            Row(
              modifier = Modifier.padding(horizontal = 12.dp, vertical = 15.dp),
              verticalAlignment = Alignment.CenterVertically
            ) {
              Text(
                text = "${uiState.selectedCountry.flagEmoji} ${uiState.selectedCountry.dialCode}",
                fontWeight = FontWeight.SemiBold,
                fontSize = 15.sp,
                color = MaterialTheme.colorScheme.onSurface
              )
              Icon(
                imageVector = Icons.Default.ArrowDropDown,
                contentDescription = "Select Country Code",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp)
              )
            }
          }

          DropdownMenu(
            expanded = countryDropdownExpanded,
            onDismissRequest = { countryDropdownExpanded = false }
          ) {
            SupportedCountryCodes.forEach { option ->
              DropdownMenuItem(
                text = {
                  Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(text = option.flagEmoji, fontSize = 18.sp)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                      text = "${option.countryName} (${option.dialCode})",
                      fontSize = 14.sp,
                      color = MaterialTheme.colorScheme.onSurface,
                      fontWeight = if (option == uiState.selectedCountry) FontWeight.Bold else FontWeight.Normal
                    )
                  }
                },
                onClick = {
                  viewModel.onCountrySelected(option)
                  countryDropdownExpanded = false
                }
              )
            }
          }
        }

        Spacer(modifier = Modifier.width(10.dp))

        OutlinedTextField(
          value = uiState.rawPhoneNumber,
          onValueChange = { viewModel.onPhoneNumberChanged(it) },
          label = { Text("Mobile Number") },
          placeholder = {
            Text(
              if (uiState.selectedCountry.dialCode == "+91") "9876543210" else "Phone number"
            )
          },
          singleLine = true,
          keyboardOptions = KeyboardOptions(
            keyboardType = KeyboardType.Phone,
            imeAction = ImeAction.Done
          ),
          keyboardActions = KeyboardActions(
            onDone = {
              if (uiState.isValidPhoneNumber && activity != null) {
                viewModel.sendVerificationCode(activity)
              }
            }
          ),
          shape = RoundedCornerShape(14.dp),
          colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = MaterialTheme.colorScheme.primary,
            unfocusedBorderColor = MaterialTheme.colorScheme.outline,
            focusedContainerColor = MaterialTheme.colorScheme.surface,
            unfocusedContainerColor = MaterialTheme.colorScheme.surface,
            focusedLabelColor = MaterialTheme.colorScheme.primary,
            cursorColor = MaterialTheme.colorScheme.primary
          ),
          modifier = Modifier
            .weight(1f)
            .testTag("phone_number_input")
        )
      }

      if (uiState.selectedCountry.dialCode == "+91") {
        Text(
          text = "Enter 10-digit Indian mobile number (e.g., 98765 43210)",
          fontSize = 12.sp,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
          modifier = Modifier
            .fillMaxWidth()
            .padding(start = 4.dp, top = 6.dp)
        )
      }

      Spacer(modifier = Modifier.height(16.dp))

      if (uiState.errorMessage != null) {
        Card(
          shape = RoundedCornerShape(12.dp),
          colors = CardDefaults.cardColors(containerColor = palette.statusAlertContainer),
          border = BorderStroke(1.dp, palette.statusAlertBorder),
          modifier = Modifier
            .fillMaxWidth()
            .testTag("phone_error_banner")
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
        Spacer(modifier = Modifier.height(16.dp))
      }

      Card(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier.fillMaxWidth()
      ) {
        Row(
          modifier = Modifier.padding(14.dp),
          verticalAlignment = Alignment.Top
        ) {
          Icon(
            imageVector = Icons.Default.Security,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier
              .size(20.dp)
              .padding(top = 2.dp)
          )
          Spacer(modifier = Modifier.width(10.dp))
          Column {
            Text(
              text = "Privacy Protected",
              fontWeight = FontWeight.Bold,
              fontSize = 13.sp,
              color = MaterialTheme.colorScheme.onPrimaryContainer
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
              text = "Your phone number is only used for two-factor account recovery and report delivery notifications. We never share or sell your data.",
              fontSize = 12.sp,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
              lineHeight = 17.sp
            )
          }
        }
      }

      Spacer(modifier = Modifier.height(28.dp))

      Button(
        onClick = {
          if (activity != null) {
            viewModel.sendVerificationCode(activity)
          }
        },
        enabled = uiState.isValidPhoneNumber && !uiState.isLoading,
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
          .testTag("send_otp_btn")
      ) {
        if (uiState.isLoading) {
          CircularProgressIndicator(
            modifier = Modifier.size(22.dp),
            color = MaterialTheme.colorScheme.onPrimary,
            strokeWidth = 2.5.dp
          )
          Spacer(modifier = Modifier.width(10.dp))
          Text(
            text = uiState.statusMessage ?: "Sending Code...",
            fontSize = 15.sp,
            fontWeight = FontWeight.Bold
          )
        } else {
          Icon(
            imageVector = Icons.Default.Sms,
            contentDescription = null,
            modifier = Modifier.size(18.dp)
          )
          Spacer(modifier = Modifier.width(8.dp))
          Text(
            text = "Send Verification Code",
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold
          )
        }
      }

      Spacer(modifier = Modifier.height(14.dp))

      OutlinedButton(
        onClick = onSkipClick,
        shape = RoundedCornerShape(28.dp),
        colors = ButtonDefaults.outlinedButtonColors(
          containerColor = MaterialTheme.colorScheme.surface,
          contentColor = MaterialTheme.colorScheme.onSurfaceVariant
        ),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        modifier = Modifier
          .fillMaxWidth()
          .height(48.dp)
          .testTag("skip_phone_verification_btn")
      ) {
        Text(
          text = "Skip for Now",
          fontSize = 14.sp,
          fontWeight = FontWeight.Medium
        )
      }

      Spacer(modifier = Modifier.height(24.dp))
    }
  }
}
