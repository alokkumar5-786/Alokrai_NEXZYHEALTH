package com.example.ui.screens

import android.app.Activity
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Lock
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
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.auth.CountryCodeOption
import com.example.ui.auth.PhoneVerificationStep
import com.example.ui.auth.PhoneVerificationViewModel
import com.example.ui.auth.SupportedCountryCodes
import com.example.ui.components.NexzyTopAppBar
import com.example.ui.theme.BrandMintBackground
import com.example.ui.theme.BrandMintSurface
import com.example.ui.theme.BrandNavyMuted
import com.example.ui.theme.BrandNavyText
import com.example.ui.theme.BrandTealPrimary
import com.example.ui.theme.SurfaceBorder
import com.example.ui.theme.SurfaceCard

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

  var countryDropdownExpanded by remember { mutableStateOf(false) }

  // Navigate to OTP screen when code is dispatched
  LaunchedEffect(uiState.step) {
    if (uiState.step is PhoneVerificationStep.EnterOtp) {
      onNavigateToOtp()
    }
  }

  Column(
    modifier = modifier
      .fillMaxSize()
      .background(Color.White)
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
        .verticalScroll(rememberScrollState())
        .padding(horizontal = 24.dp, vertical = 16.dp),
      horizontalAlignment = Alignment.CenterHorizontally
    ) {
      Spacer(modifier = Modifier.height(8.dp))

      // Icon Illustration
      Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
          .size(72.dp)
          .clip(CircleShape)
          .background(BrandMintBackground)
          .border(2.dp, BrandTealPrimary.copy(alpha = 0.3f), CircleShape)
      ) {
        Icon(
          imageVector = Icons.Default.PhoneAndroid,
          contentDescription = null,
          tint = BrandTealPrimary,
          modifier = Modifier.size(36.dp)
        )
      }

      Spacer(modifier = Modifier.height(18.dp))

      Text(
        text = "Add Your Mobile Number",
        fontSize = 22.sp,
        fontWeight = FontWeight.Bold,
        color = BrandNavyText,
        textAlign = TextAlign.Center
      )

      Spacer(modifier = Modifier.height(8.dp))

      Text(
        text = "We will send an SMS with a 6-digit verification code. This links your verified mobile to your existing account.",
        fontSize = 14.sp,
        color = BrandNavyMuted,
        textAlign = TextAlign.Center,
        lineHeight = 20.sp,
        modifier = Modifier.padding(horizontal = 8.dp)
      )

      Spacer(modifier = Modifier.height(24.dp))

      // Phone Input Row with Country Selector
      Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
      ) {
        // Country Code Selector Button
        Box {
          Card(
            shape = RoundedCornerShape(14.dp),
            colors = CardDefaults.cardColors(containerColor = BrandMintSurface),
            border = androidx.compose.foundation.BorderStroke(1.dp, SurfaceBorder),
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
                color = BrandNavyText
              )
              Icon(
                imageVector = Icons.Default.ArrowDropDown,
                contentDescription = "Select Country Code",
                tint = BrandNavyMuted,
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
                      color = BrandNavyText,
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

        // Phone Number Input Field
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
            focusedBorderColor = BrandTealPrimary,
            unfocusedBorderColor = SurfaceBorder,
            focusedLabelColor = BrandTealPrimary,
            cursorColor = BrandTealPrimary
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
          color = BrandNavyMuted,
          modifier = Modifier
            .fillMaxWidth()
            .padding(start = 4.dp, top = 6.dp)
        )
      }

      Spacer(modifier = Modifier.height(16.dp))

      // Error Banner
      if (uiState.errorMessage != null) {
        Card(
          shape = RoundedCornerShape(12.dp),
          colors = CardDefaults.cardColors(containerColor = Color(0xFFFFF1F2)),
          border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFFECDD3)),
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
              tint = Color(0xFFE11D48),
              modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(10.dp))
            Text(
              text = uiState.errorMessage ?: "",
              fontSize = 13.sp,
              color = Color(0xFF9F1239),
              lineHeight = 18.sp
            )
          }
        }
        Spacer(modifier = Modifier.height(16.dp))
      }

      // Security / Privacy Notice Card
      Card(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = BrandMintSurface),
        border = androidx.compose.foundation.BorderStroke(1.dp, SurfaceBorder),
        modifier = Modifier.fillMaxWidth()
      ) {
        Row(
          modifier = Modifier.padding(14.dp),
          verticalAlignment = Alignment.Top
        ) {
          Icon(
            imageVector = Icons.Default.Security,
            contentDescription = null,
            tint = BrandTealPrimary,
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
              color = BrandNavyText
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
              text = "Your phone number is only used for two-factor account recovery and report delivery notifications. We never share or sell your data.",
              fontSize = 12.sp,
              color = BrandNavyMuted,
              lineHeight = 17.sp
            )
          }
        }
      }

      Spacer(modifier = Modifier.height(28.dp))

      // Send OTP Action Button
      Button(
        onClick = {
          if (activity != null) {
            viewModel.sendVerificationCode(activity)
          }
        },
        enabled = uiState.isValidPhoneNumber && !uiState.isLoading,
        shape = RoundedCornerShape(28.dp),
        colors = ButtonDefaults.buttonColors(
          containerColor = BrandTealPrimary,
          disabledContainerColor = BrandTealPrimary.copy(alpha = 0.4f)
        ),
        modifier = Modifier
          .fillMaxWidth()
          .height(52.dp)
          .testTag("send_otp_btn")
      ) {
        if (uiState.isLoading) {
          CircularProgressIndicator(
            modifier = Modifier.size(22.dp),
            color = Color.White,
            strokeWidth = 2.5.dp
          )
          Spacer(modifier = Modifier.width(10.dp))
          Text(
            text = uiState.statusMessage ?: "Sending Code...",
            fontSize = 15.sp,
            fontWeight = FontWeight.Bold,
            color = Color.White
          )
        } else {
          Icon(
            imageVector = Icons.Default.Sms,
            contentDescription = null,
            tint = Color.White,
            modifier = Modifier.size(18.dp)
          )
          Spacer(modifier = Modifier.width(8.dp))
          Text(
            text = "Send Verification Code",
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold,
            color = Color.White
          )
        }
      }

      Spacer(modifier = Modifier.height(14.dp))

      // Skip for Now Option
      OutlinedButton(
        onClick = onSkipClick,
        shape = RoundedCornerShape(28.dp),
        colors = ButtonDefaults.outlinedButtonColors(contentColor = BrandNavyMuted),
        border = androidx.compose.foundation.BorderStroke(1.dp, SurfaceBorder),
        modifier = Modifier
          .fillMaxWidth()
          .height(48.dp)
          .testTag("skip_phone_verification_btn")
      ) {
        Text(
          text = "Skip for Now",
          fontSize = 14.sp,
          fontWeight = FontWeight.Medium,
          color = BrandNavyMuted
        )
      }

      Spacer(modifier = Modifier.height(24.dp))
    }
  }
}
