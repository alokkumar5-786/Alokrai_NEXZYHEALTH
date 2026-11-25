package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.auth.EmailLinkViewModel
import com.example.ui.components.NexzyTopAppBar
import com.example.ui.theme.NexzyTheme

@Composable
fun AuthLoadingScreen(
  viewModel: EmailLinkViewModel,
  onAuthSuccess: () -> Unit,
  onRequestNewLink: () -> Unit,
  modifier: Modifier = Modifier
) {
  val uiState by viewModel.uiState.collectAsState()
  val palette = NexzyTheme.colors

  LaunchedEffect(uiState.isSuccess) {
    if (uiState.isSuccess) {
      onAuthSuccess()
    }
  }

  Column(
    modifier = modifier
      .fillMaxSize()
      .background(MaterialTheme.colorScheme.background)
      .navigationBarsPadding()
  ) {
    NexzyTopAppBar(
      title = "Authenticating",
      subtitle = "Verifying secure sign-in link",
      showBackButton = false
    )

    Box(
      modifier = Modifier
        .fillMaxSize()
        .padding(24.dp),
      contentAlignment = Alignment.Center
    ) {
      when {
        uiState.isLoading -> {
          Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
          ) {
            Box(
              contentAlignment = Alignment.Center,
              modifier = Modifier
                .size(80.dp)
                .background(MaterialTheme.colorScheme.primaryContainer, CircleShape)
            ) {
              CircularProgressIndicator(
                color = MaterialTheme.colorScheme.primary,
                strokeWidth = 3.5.dp,
                modifier = Modifier.size(46.dp)
              )
            }

            Spacer(modifier = Modifier.height(24.dp))

            Text(
              text = "Verifying Sign-In Link...",
              style = MaterialTheme.typography.titleLarge,
              color = MaterialTheme.colorScheme.onBackground,
              fontWeight = FontWeight.Bold,
              textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
              text = "Connecting with Firebase and preparing your health dashboard.",
              style = MaterialTheme.typography.bodyMedium,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
              textAlign = TextAlign.Center,
              modifier = Modifier.padding(horizontal = 16.dp)
            )
          }
        }

        uiState.requiresEmailInput -> {
          Card(
            shape = RoundedCornerShape(18.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            modifier = Modifier
              .fillMaxWidth()
              .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(18.dp))
          ) {
            Column(
              modifier = Modifier.padding(20.dp),
              horizontalAlignment = Alignment.CenterHorizontally
            ) {
              Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                  .size(54.dp)
                  .background(MaterialTheme.colorScheme.primaryContainer, CircleShape)
              ) {
                Icon(
                  imageVector = Icons.Default.Email,
                  contentDescription = null,
                  tint = MaterialTheme.colorScheme.primary,
                  modifier = Modifier.size(28.dp)
                )
              }

              Spacer(modifier = Modifier.height(14.dp))

              Text(
                text = "Confirm Your Email",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
              )

              Spacer(modifier = Modifier.height(6.dp))

              Text(
                text = "Please enter the email address where you received the sign-in link to complete verification.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
              )

              Spacer(modifier = Modifier.height(18.dp))

              OutlinedTextField(
                value = uiState.emailInput,
                onValueChange = { viewModel.onEmailInputChanged(it) },
                label = { Text("Email Address") },
                placeholder = { Text("e.g. name@example.com") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                modifier = Modifier
                  .fillMaxWidth()
                  .testTag("input_confirm_email"),
                shape = RoundedCornerShape(12.dp),
                colors = OutlinedTextFieldDefaults.colors(
                  focusedBorderColor = MaterialTheme.colorScheme.primary,
                  unfocusedBorderColor = MaterialTheme.colorScheme.outline,
                  focusedLabelColor = MaterialTheme.colorScheme.primary
                )
              )

              if (uiState.errorMessage != null) {
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                  text = uiState.errorMessage!!,
                  color = MaterialTheme.colorScheme.error,
                  fontSize = 12.sp,
                  textAlign = TextAlign.Center
                )
              }

              Spacer(modifier = Modifier.height(18.dp))

              Button(
                onClick = { viewModel.submitManualEmail() },
                colors = ButtonDefaults.buttonColors(
                  containerColor = MaterialTheme.colorScheme.primary,
                  contentColor = MaterialTheme.colorScheme.onPrimary
                ),
                shape = RoundedCornerShape(24.dp),
                modifier = Modifier
                  .fillMaxWidth()
                  .height(48.dp)
                  .testTag("btn_confirm_manual_email")
              ) {
                Text(
                  text = "Confirm & Complete Sign-In",
                  fontWeight = FontWeight.Bold
                )
              }
            }
          }
        }

        uiState.errorMessage != null -> {
          Card(
            shape = RoundedCornerShape(18.dp),
            colors = CardDefaults.cardColors(containerColor = palette.statusAlertContainer),
            modifier = Modifier
              .fillMaxWidth()
              .border(1.dp, palette.statusAlertBorder, RoundedCornerShape(18.dp))
          ) {
            Column(
              modifier = Modifier.padding(24.dp),
              horizontalAlignment = Alignment.CenterHorizontally
            ) {
              Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                  .size(60.dp)
                  .background(MaterialTheme.colorScheme.surface, CircleShape)
              ) {
                Icon(
                  imageVector = Icons.Default.ErrorOutline,
                  contentDescription = null,
                  tint = palette.statusAlert,
                  modifier = Modifier.size(36.dp)
                )
              }

              Spacer(modifier = Modifier.height(16.dp))

              Text(
                text = "Verification Failed",
                style = MaterialTheme.typography.titleMedium,
                color = palette.statusAlertText,
                fontWeight = FontWeight.Bold
              )

              Spacer(modifier = Modifier.height(8.dp))

              Text(
                text = uiState.errorMessage!!,
                style = MaterialTheme.typography.bodyMedium,
                color = palette.statusAlertText,
                textAlign = TextAlign.Center
              )

              Spacer(modifier = Modifier.height(24.dp))

              Button(
                onClick = onRequestNewLink,
                colors = ButtonDefaults.buttonColors(
                  containerColor = MaterialTheme.colorScheme.primary,
                  contentColor = MaterialTheme.colorScheme.onPrimary
                ),
                shape = RoundedCornerShape(24.dp),
                modifier = Modifier
                  .fillMaxWidth()
                  .height(48.dp)
                  .testTag("btn_request_new_link")
              ) {
                Text(
                  text = "Request A New Sign-In Link",
                  fontWeight = FontWeight.Bold
                )
              }
            }
          }
        }

        uiState.isSuccess -> {
          Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
          ) {
            Box(
              contentAlignment = Alignment.Center,
              modifier = Modifier
                .size(72.dp)
                .background(palette.statusNormalContainer, CircleShape)
            ) {
              Icon(
                imageVector = Icons.Default.CheckCircle,
                contentDescription = null,
                tint = palette.statusNormal,
                modifier = Modifier.size(48.dp)
              )
            }

            Spacer(modifier = Modifier.height(18.dp))

            Text(
              text = "Sign-In Successful!",
              style = MaterialTheme.typography.titleLarge,
              color = MaterialTheme.colorScheme.onBackground,
              fontWeight = FontWeight.Bold
            )

            Spacer(modifier = Modifier.height(6.dp))

            Text(
              text = "Opening your health dashboard...",
              style = MaterialTheme.typography.bodyMedium,
              color = MaterialTheme.colorScheme.onSurfaceVariant
            )
          }
        }

        else -> {
          CircularProgressIndicator(
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(40.dp)
          )
        }
      }
    }
  }
}
