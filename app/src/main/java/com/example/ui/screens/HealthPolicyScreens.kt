package com.example.ui.screens

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.MockDataProvider
import com.example.model.HealthPolicyCopy
import com.example.model.HealthPolicyStep
import com.example.model.HealthPolicyStrings
import com.example.model.HealthPolicyUiState
import com.example.model.PolicyFileAttachment
import com.example.ui.components.NexzyTopAppBar
import com.example.ui.policy.AndroidPolicyFileReader
import com.example.ui.theme.NexzyTheme

/**
 * Coordinates the 4-screen "Explain My Health Policy" flow:
 * 1. Output Language Selection (reusing LanguageScreen)
 * 2. Policy Upload (PDF/Photos up to 3) or Text Paste + Privacy Notice
 * 3. Loading ("Reading your policy...")
 * 4. Expandable Policy Results + Share + Delete Now
 */
@Composable
fun HealthPolicyFlowScreen(
  uiState: HealthPolicyUiState,
  onSelectLanguage: (String) -> Unit,
  onConfirmLanguage: () -> Unit,
  onBackToLanguage: () -> Unit,
  onFilesPicked: (List<Uri>) -> Unit,
  onRemoveAttachment: (String) -> Unit,
  onPastedTextChanged: (String) -> Unit,
  onDismissError: () -> Unit,
  onAnalyzePolicy: () -> Unit,
  onCancelLoading: () -> Unit,
  onShareSummary: (String) -> Unit,
  onDeleteDocumentNow: () -> Unit,
  onExitToHome: () -> Unit,
  buildShareText: () -> String?,
  modifier: Modifier = Modifier
) {
  val copy = HealthPolicyCopy.forLanguage(uiState.selectedLanguageCode)

  BackHandler {
    when (uiState.currentStep) {
      HealthPolicyStep.LANGUAGE_SELECTION -> {
        onDeleteDocumentNow()
        onExitToHome()
      }
      HealthPolicyStep.UPLOAD_OR_PASTE -> onBackToLanguage()
      HealthPolicyStep.LOADING -> onCancelLoading()
      HealthPolicyStep.RESULT -> {
        onDeleteDocumentNow()
        onExitToHome()
      }
    }
  }

  when (uiState.currentStep) {
    HealthPolicyStep.LANGUAGE_SELECTION -> {
      val supportedLanguages = MockDataProvider.sampleLanguages.filter { it.isSupported }
      LanguageScreen(
        languages = supportedLanguages,
        selectedLanguageCode = uiState.selectedLanguageCode,
        onLanguageSelected = onSelectLanguage,
        onConfirm = onConfirmLanguage,
        onNavigateBack = {
          onDeleteDocumentNow()
          onExitToHome()
        },
        isOnboarding = false,
        titleText = copy.featureTitle,
        subtitleText = copy.languageScreenSubtitle,
        confirmButtonText = copy.languageConfirmButton,
        modifier = modifier
      )
    }

    HealthPolicyStep.UPLOAD_OR_PASTE -> {
      HealthPolicyUploadScreen(
        uiState = uiState,
        copy = copy,
        onBackClick = onBackToLanguage,
        onFilesPicked = onFilesPicked,
        onRemoveAttachment = onRemoveAttachment,
        onPastedTextChanged = onPastedTextChanged,
        onDismissError = onDismissError,
        onAnalyzePolicy = onAnalyzePolicy,
        modifier = modifier
      )
    }

    HealthPolicyStep.LOADING -> {
      HealthPolicyLoadingScreen(
        copy = copy,
        onCancelClick = onCancelLoading,
        modifier = modifier
      )
    }

    HealthPolicyStep.RESULT -> {
      val explanation = uiState.explanation
      if (explanation == null) {
        onBackToLanguage()
      } else {
        HealthPolicyResultScreen(
          explanation = explanation,
          copy = copy,
          onBackClick = {
            onDeleteDocumentNow()
            onExitToHome()
          },
          onShareClick = {
            val shareText = buildShareText()
            if (!shareText.isNullOrBlank()) {
              onShareSummary(shareText)
            }
          },
          onDeleteNowClick = {
            onDeleteDocumentNow()
            onExitToHome()
          },
          modifier = modifier
        )
      }
    }
  }
}

@Composable
fun HealthPolicyUploadScreen(
  uiState: HealthPolicyUiState,
  copy: HealthPolicyStrings,
  onBackClick: () -> Unit,
  onFilesPicked: (List<Uri>) -> Unit,
  onRemoveAttachment: (String) -> Unit,
  onPastedTextChanged: (String) -> Unit,
  onDismissError: () -> Unit,
  onAnalyzePolicy: () -> Unit,
  modifier: Modifier = Modifier
) {
  val palette = NexzyTheme.colors

  val filePickerLauncher = rememberLauncherForActivityResult(
    contract = ActivityResultContracts.OpenMultipleDocuments()
  ) { uris: List<Uri> ->
    if (uris.isNotEmpty()) onFilesPicked(uris)
  }

  val photoPickerLauncher = rememberLauncherForActivityResult(
    contract = ActivityResultContracts.PickMultipleVisualMedia(
      maxItems = HealthPolicyCopy.MAX_POLICY_FILES
    )
  ) { uris: List<Uri> ->
    if (uris.isNotEmpty()) onFilesPicked(uris)
  }

  Column(
    modifier = modifier
      .fillMaxSize()
      .background(MaterialTheme.colorScheme.background)
      .navigationBarsPadding()
      .testTag("health_policy_upload_screen")
  ) {
    NexzyTopAppBar(
      title = copy.uploadScreenTitle,
      subtitle = copy.uploadScreenSubtitle,
      showBackButton = true,
      onBackClick = onBackClick
    )

    Column(
      modifier = Modifier
        .fillMaxSize()
        .verticalScroll(rememberScrollState())
        .padding(horizontal = 20.dp, vertical = 14.dp)
    ) {
      // Mandatory short privacy notice before upload
      Card(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier
          .fillMaxWidth()
          .testTag("policy_privacy_notice")
      ) {
        Row(
          modifier = Modifier.padding(14.dp),
          verticalAlignment = Alignment.CenterVertically
        ) {
          Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
              .size(40.dp)
              .background(MaterialTheme.colorScheme.surface, CircleShape)
          ) {
            Icon(
              imageVector = Icons.Default.Lock,
              contentDescription = null,
              tint = MaterialTheme.colorScheme.primary,
              modifier = Modifier.size(22.dp)
            )
          }
          Spacer(modifier = Modifier.width(12.dp))
          Text(
            text = copy.privacyNotice,
            fontSize = 15.sp,
            lineHeight = 22.sp,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onPrimaryContainer
          )
        }
      }

      Spacer(modifier = Modifier.height(16.dp))

      // Friendly Error Banner
      AnimatedVisibility(
        visible = !uiState.errorMessage.isNullOrBlank(),
        enter = fadeIn(),
        exit = fadeOut()
      ) {
        Card(
          shape = RoundedCornerShape(14.dp),
          colors = CardDefaults.cardColors(containerColor = palette.statusAlertContainer),
          border = BorderStroke(1.dp, palette.statusAlertBorder),
          modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 16.dp)
            .testTag("policy_error_banner")
        ) {
          Row(
            modifier = Modifier
              .fillMaxWidth()
              .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
          ) {
            Icon(
              imageVector = Icons.Default.ErrorOutline,
              contentDescription = null,
              tint = palette.statusAlert,
              modifier = Modifier.size(24.dp)
            )
            Spacer(modifier = Modifier.width(10.dp))
            Text(
              text = uiState.errorMessage.orEmpty(),
              color = palette.statusAlertText,
              fontSize = 15.sp,
              lineHeight = 21.sp,
              fontWeight = FontWeight.Medium,
              modifier = Modifier.weight(1f)
            )
            IconButton(
              onClick = onDismissError,
              modifier = Modifier.size(48.dp)
            ) {
              Icon(
                imageVector = Icons.Default.Clear,
                contentDescription = "Dismiss error",
                tint = palette.statusAlertText
              )
            }
          }
        }
      }

      // Section Header & File Counter
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
      ) {
        Text(
          text = copy.uploadSectionHeading,
          style = MaterialTheme.typography.titleLarge,
          color = MaterialTheme.colorScheme.onBackground,
          fontWeight = FontWeight.Bold
        )
        Text(
          text = String.format(copy.filesCountFormat, uiState.attachments.size),
          fontSize = 13.sp,
          fontWeight = FontWeight.SemiBold,
          color = MaterialTheme.colorScheme.primary,
          modifier = Modifier.testTag("policy_file_count")
        )
      }

      Spacer(modifier = Modifier.height(12.dp))

      val canAddMoreFiles = uiState.attachments.size < HealthPolicyCopy.MAX_POLICY_FILES

      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
      ) {
        OutlinedButton(
          onClick = {
            filePickerLauncher.launch(AndroidPolicyFileReader.SUPPORTED_MIME_TYPES)
          },
          enabled = canAddMoreFiles,
          shape = RoundedCornerShape(14.dp),
          border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
          colors = ButtonDefaults.outlinedButtonColors(
            containerColor = MaterialTheme.colorScheme.surface,
            contentColor = MaterialTheme.colorScheme.primary
          ),
          modifier = Modifier
            .weight(1f)
            .height(54.dp)
            .testTag("btn_upload_policy_files")
        ) {
          Icon(Icons.Default.FolderOpen, contentDescription = null, modifier = Modifier.size(20.dp))
          Spacer(modifier = Modifier.width(6.dp))
          Text(
            text = copy.uploadFilesButton,
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 2
          )
        }

        OutlinedButton(
          onClick = {
            photoPickerLauncher.launch(
              PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
            )
          },
          enabled = canAddMoreFiles,
          shape = RoundedCornerShape(14.dp),
          border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
          colors = ButtonDefaults.outlinedButtonColors(
            containerColor = MaterialTheme.colorScheme.surface,
            contentColor = MaterialTheme.colorScheme.primary
          ),
          modifier = Modifier
            .weight(1f)
            .height(54.dp)
            .testTag("btn_pick_policy_photos")
        ) {
          Icon(Icons.Default.PhotoLibrary, contentDescription = null, modifier = Modifier.size(20.dp))
          Spacer(modifier = Modifier.width(6.dp))
          Text(
            text = copy.pickPhotosButton,
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 2
          )
        }
      }

      if (uiState.attachments.isNotEmpty()) {
        Spacer(modifier = Modifier.height(14.dp))
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
          uiState.attachments.forEachIndexed { index, attachment ->
            PolicyAttachmentRow(
              attachment = attachment,
              index = index,
              onRemove = { onRemoveAttachment(attachment.id) }
            )
          }
        }
      }

      Spacer(modifier = Modifier.height(20.dp))

      // OR divider
      Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
      ) {
        HorizontalDivider(modifier = Modifier.weight(1f), color = palette.dividerBorder)
        Text(
          text = "  ${copy.pasteDividerLabel}  ",
          fontSize = 12.sp,
          fontWeight = FontWeight.Bold,
          color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        HorizontalDivider(modifier = Modifier.weight(1f), color = palette.dividerBorder)
      }

      Spacer(modifier = Modifier.height(16.dp))

      Text(
        text = copy.pasteInputLabel,
        fontSize = 16.sp,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.onBackground
      )

      Spacer(modifier = Modifier.height(8.dp))

      OutlinedTextField(
        value = uiState.pastedPolicyText,
        onValueChange = onPastedTextChanged,
        placeholder = {
          Text(
            text = copy.pasteInputPlaceholder,
            fontSize = 15.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f)
          )
        },
        textStyle = MaterialTheme.typography.bodyLarge.copy(
          fontSize = 16.sp,
          lineHeight = 23.sp,
          color = MaterialTheme.colorScheme.onSurface
        ),
        minLines = 5,
        maxLines = 10,
        shape = RoundedCornerShape(14.dp),
        colors = OutlinedTextFieldDefaults.colors(
          focusedBorderColor = MaterialTheme.colorScheme.primary,
          unfocusedBorderColor = palette.dividerBorder,
          focusedContainerColor = MaterialTheme.colorScheme.surface,
          unfocusedContainerColor = MaterialTheme.colorScheme.surface
        ),
        modifier = Modifier
          .fillMaxWidth()
          .testTag("input_policy_text")
      )

      Spacer(modifier = Modifier.height(24.dp))

      Button(
        onClick = onAnalyzePolicy,
        enabled = uiState.canAnalyze,
        colors = ButtonDefaults.buttonColors(
          containerColor = MaterialTheme.colorScheme.primary,
          contentColor = MaterialTheme.colorScheme.onPrimary,
          disabledContainerColor = palette.disabledContainer,
          disabledContentColor = palette.disabledContent
        ),
        shape = RoundedCornerShape(28.dp),
        modifier = Modifier
          .fillMaxWidth()
          .height(56.dp)
          .testTag("btn_explain_policy")
      ) {
        Icon(Icons.Default.Shield, contentDescription = null, modifier = Modifier.size(22.dp))
        Spacer(modifier = Modifier.width(10.dp))
        Text(
          text = copy.explainButtonText,
          fontSize = 17.sp,
          fontWeight = FontWeight.Bold
        )
      }

      Spacer(modifier = Modifier.height(20.dp))
    }
  }
}

@Composable
private fun PolicyAttachmentRow(
  attachment: PolicyFileAttachment,
  index: Int,
  onRemove: () -> Unit
) {
  val palette = NexzyTheme.colors

  Card(
    shape = RoundedCornerShape(12.dp),
    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    modifier = Modifier
      .fillMaxWidth()
      .testTag("policy_attachment_$index")
  ) {
    Row(
      modifier = Modifier
        .fillMaxWidth()
        .padding(horizontal = 12.dp, vertical = 10.dp),
      verticalAlignment = Alignment.CenterVertically
    ) {
      Icon(
        imageVector = if (attachment.isPdf) Icons.Default.PictureAsPdf else Icons.Default.Image,
        contentDescription = null,
        tint = MaterialTheme.colorScheme.primary,
        modifier = Modifier.size(26.dp)
      )
      Spacer(modifier = Modifier.width(12.dp))
      Column(modifier = Modifier.weight(1f)) {
        Text(
          text = attachment.fileName,
          fontSize = 15.sp,
          fontWeight = FontWeight.SemiBold,
          color = MaterialTheme.colorScheme.onSurface,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis
        )
        Text(
          text = "${if (attachment.isPdf) "PDF" else "Photo"} \u2022 ${attachment.formattedSize}",
          fontSize = 13.sp,
          color = MaterialTheme.colorScheme.onSurfaceVariant
        )
      }
      IconButton(
        onClick = onRemove,
        modifier = Modifier
          .size(48.dp)
          .testTag("btn_remove_policy_file_$index")
      ) {
        Icon(
          imageVector = Icons.Default.DeleteOutline,
          contentDescription = "Remove file",
          tint = palette.statusAlert
        )
      }
    }
  }
}

@Composable
fun HealthPolicyLoadingScreen(
  copy: HealthPolicyStrings,
  onCancelClick: () -> Unit,
  modifier: Modifier = Modifier
) {
  Column(
    modifier = modifier
      .fillMaxSize()
      .background(MaterialTheme.colorScheme.background)
      .navigationBarsPadding()
      .padding(24.dp)
      .testTag("health_policy_loading_screen"),
    horizontalAlignment = Alignment.CenterHorizontally,
    verticalArrangement = Arrangement.SpaceBetween
  ) {
    Spacer(modifier = Modifier.height(24.dp))

    Column(
      horizontalAlignment = Alignment.CenterHorizontally,
      modifier = Modifier.fillMaxWidth()
    ) {
      Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
          .size(84.dp)
          .background(MaterialTheme.colorScheme.primaryContainer, CircleShape)
      ) {
        CircularProgressIndicator(
          modifier = Modifier.size(56.dp),
          color = MaterialTheme.colorScheme.primary,
          strokeWidth = 4.dp
        )
        Icon(
          imageVector = Icons.Default.Shield,
          contentDescription = null,
          tint = MaterialTheme.colorScheme.primary,
          modifier = Modifier.size(26.dp)
        )
      }

      Spacer(modifier = Modifier.height(24.dp))

      Text(
        text = copy.readingPolicyTitle,
        fontSize = 22.sp,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.onBackground,
        textAlign = TextAlign.Center,
        modifier = Modifier.testTag("policy_loading_title")
      )

      Spacer(modifier = Modifier.height(10.dp))

      Text(
        text = copy.readingPolicySubtitle,
        fontSize = 16.sp,
        lineHeight = 23.sp,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center
      )

      Spacer(modifier = Modifier.height(24.dp))

      LinearProgressIndicator(
        modifier = Modifier
          .fillMaxWidth()
          .height(10.dp)
          .clip(RoundedCornerShape(5.dp))
          .testTag("policy_loading_bar"),
        color = MaterialTheme.colorScheme.primary,
        trackColor = MaterialTheme.colorScheme.primaryContainer,
        strokeCap = StrokeCap.Round
      )
    }

    OutlinedButton(
      onClick = onCancelClick,
      border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
      colors = ButtonDefaults.outlinedButtonColors(
        containerColor = MaterialTheme.colorScheme.surface,
        contentColor = MaterialTheme.colorScheme.onSurface
      ),
      shape = RoundedCornerShape(28.dp),
      modifier = Modifier
        .fillMaxWidth()
        .height(52.dp)
        .testTag("btn_cancel_policy_loading")
    ) {
      Text(
        text = copy.cancelButtonText,
        fontSize = 16.sp,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.onSurface
      )
    }
  }
}
