package com.example.ui.screens

import android.graphics.Bitmap
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.model.DocumentPreviewState
import com.example.model.FamilyMember
import com.example.model.ReportUploadUiState
import com.example.model.SelectedDocument
import com.example.ui.components.NexzyTopAppBar
import com.example.ui.theme.BrandMintBackground
import com.example.ui.theme.BrandNavyMuted
import com.example.ui.theme.BrandNavyText
import com.example.ui.theme.BrandTealPrimary
import com.example.ui.theme.SurfaceBorder
import com.example.ui.theme.SurfaceCard
import com.example.ui.upload.ReportUploadViewModel

@Composable
fun ReportUploadScreen(
  viewModel: ReportUploadViewModel,
  familyMembers: List<FamilyMember>,
  onStartUploadAndExplain: (fileName: String, person: String) -> Unit,
  onNavigateToScan: () -> Unit,
  onNavigateBack: () -> Unit,
  modifier: Modifier = Modifier
) {
  val uiState by viewModel.uiState.collectAsState()
  var selectedMemberIndex by remember { mutableIntStateOf(0) }

  // Synchronize initial family member
  LaunchedEffect(familyMembers) {
    if (uiState.selectedFamilyMember == null && familyMembers.isNotEmpty()) {
      viewModel.onFamilyMemberSelected(familyMembers.first())
    }
  }

  // Native Android Document Picker (Activity Result API)
  val documentPickerLauncher = rememberLauncherForActivityResult(
    contract = ActivityResultContracts.OpenDocument()
  ) { uri: Uri? ->
    viewModel.onDocumentSelected(uri)
  }

  val reportTypes = listOf(
    "Blood Test (CBC / Metabolic)",
    "Lipid / Heart Profile",
    "Thyroid Profile",
    "Urine Routine / Kidney",
    "Liver Function (LFT)",
    "Other Health Test"
  )

  Column(
    modifier = modifier
      .fillMaxSize()
      .background(Color.White)
  ) {
    NexzyTopAppBar(
      title = "Upload Medical Report",
      subtitle = "Select digital PDF or image",
      showBackButton = true,
      onBackClick = onNavigateBack
    )

    Column(
      modifier = Modifier
        .fillMaxSize()
        .verticalScroll(rememberScrollState())
        .padding(horizontal = 20.dp, vertical = 14.dp)
    ) {
      // 1. Heading & Explanation
      Text(
        text = "Upload Your Medical Report",
        style = MaterialTheme.typography.titleLarge,
        color = BrandNavyText,
        fontWeight = FontWeight.ExtraBold
      )

      Spacer(modifier = Modifier.height(4.dp))

      Text(
        text = "Select your lab test, blood work, or pathology report. Supports PDF, JPG, JPEG, and PNG files up to 15 MB. Your data is stored securely with zero data sharing.",
        style = MaterialTheme.typography.bodyMedium,
        color = BrandNavyMuted,
        lineHeight = 19.sp
      )

      Spacer(modifier = Modifier.height(18.dp))

      // Error Banner
      AnimatedVisibility(
        visible = uiState.errorMessage != null,
        enter = fadeIn(),
        exit = fadeOut()
      ) {
        Card(
          shape = RoundedCornerShape(12.dp),
          colors = CardDefaults.cardColors(containerColor = Color(0xFFFEE2E2)),
          modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 14.dp)
            .border(1.dp, Color(0xFFFCA5A5), RoundedCornerShape(12.dp))
            .testTag("upload_error_banner")
        ) {
          Row(
            modifier = Modifier
              .fillMaxWidth()
              .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
          ) {
            Icon(
              imageVector = Icons.Default.ErrorOutline,
              contentDescription = "Error",
              tint = Color(0xFFDC2626),
              modifier = Modifier.size(24.dp)
            )
            Spacer(modifier = Modifier.width(10.dp))
            Text(
              text = uiState.errorMessage ?: "",
              color = Color(0xFF991B1B),
              fontSize = 13.sp,
              fontWeight = FontWeight.Medium,
              modifier = Modifier.weight(1f)
            )
            IconButton(
              onClick = { viewModel.clearError() },
              modifier = Modifier.size(28.dp)
            ) {
              Icon(
                imageVector = Icons.Default.Clear,
                contentDescription = "Dismiss error",
                tint = Color(0xFF991B1B),
                modifier = Modifier.size(16.dp)
              )
            }
          }
        }
      }

      // 2. Document Selection Area (Selected vs Empty State)
      if (uiState.selectedDocument != null) {
        SelectedDocumentCard(
          document = uiState.selectedDocument!!,
          previewState = uiState.previewState,
          onPreviewClick = { viewModel.setPreviewModalVisible(true) },
          onChangeClick = {
            documentPickerLauncher.launch(ReportUploadViewModel.SUPPORTED_MIME_TYPES)
          },
          onRemoveClick = { viewModel.removeSelectedDocument() }
        )
      } else {
        EmptyDocumentDropzone(
          onSelectFileClick = {
            documentPickerLauncher.launch(ReportUploadViewModel.SUPPORTED_MIME_TYPES)
          }
        )
      }

      Spacer(modifier = Modifier.height(12.dp))

      // 3. Selection Actions Row (Upload from Device & Take Photo)
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
      ) {
        OutlinedButton(
          onClick = {
            documentPickerLauncher.launch(ReportUploadViewModel.SUPPORTED_MIME_TYPES)
          },
          shape = RoundedCornerShape(14.dp),
          colors = ButtonDefaults.outlinedButtonColors(contentColor = BrandTealPrimary),
          modifier = Modifier
            .weight(1f)
            .height(48.dp)
            .testTag("btn_select_file")
        ) {
          Icon(Icons.Default.FolderOpen, contentDescription = null, modifier = Modifier.size(18.dp))
          Spacer(modifier = Modifier.width(6.dp))
          Text(
            text = if (uiState.selectedDocument != null) "Select Another" else "Select File",
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold
          )
        }

        OutlinedButton(
          onClick = onNavigateToScan,
          shape = RoundedCornerShape(14.dp),
          colors = ButtonDefaults.outlinedButtonColors(contentColor = BrandTealPrimary),
          modifier = Modifier
            .weight(1f)
            .height(48.dp)
            .testTag("btn_take_photo_scan")
        ) {
          Icon(Icons.Default.CameraAlt, contentDescription = null, modifier = Modifier.size(18.dp))
          Spacer(modifier = Modifier.width(6.dp))
          Text("Take Photo", fontSize = 13.sp, fontWeight = FontWeight.Bold)
        }
      }

      Spacer(modifier = Modifier.height(22.dp))

      // 4. Family Member Selector
      Text(
        text = "Whose report is this?",
        style = MaterialTheme.typography.titleMedium,
        color = BrandNavyText,
        fontWeight = FontWeight.Bold
      )

      Spacer(modifier = Modifier.height(10.dp))

      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
      ) {
        familyMembers.forEachIndexed { index, member ->
          val isSelected = index == selectedMemberIndex
          Card(
            modifier = Modifier
              .weight(1f)
              .clip(RoundedCornerShape(12.dp))
              .border(
                width = if (isSelected) 2.dp else 1.dp,
                color = if (isSelected) BrandTealPrimary else SurfaceBorder,
                shape = RoundedCornerShape(12.dp)
              )
              .clickable {
                selectedMemberIndex = index
                viewModel.onFamilyMemberSelected(member)
              }
              .testTag("upload_member_$index"),
            colors = CardDefaults.cardColors(
              containerColor = if (isSelected) BrandMintBackground else SurfaceCard
            ),
            shape = RoundedCornerShape(12.dp)
          ) {
            Column(
              modifier = Modifier.padding(10.dp),
              horizontalAlignment = Alignment.CenterHorizontally
            ) {
              Icon(
                imageVector = Icons.Default.Person,
                contentDescription = null,
                tint = if (isSelected) BrandTealPrimary else BrandNavyMuted,
                modifier = Modifier.size(20.dp)
              )
              Spacer(modifier = Modifier.height(4.dp))
              Text(
                text = member.name.substringBefore(" "),
                fontSize = 12.sp,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                color = BrandNavyText,
                maxLines = 1
              )
              Text(
                text = member.relation.substringBefore(" "),
                fontSize = 10.sp,
                color = BrandNavyMuted
              )
            }
          }
        }
      }

      Spacer(modifier = Modifier.height(20.dp))

      // 5. Report Category
      Text(
        text = "Report Category",
        style = MaterialTheme.typography.titleMedium,
        color = BrandNavyText,
        fontWeight = FontWeight.Bold
      )

      Spacer(modifier = Modifier.height(8.dp))

      Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        reportTypes.take(4).forEach { type ->
          val isSelected = type == uiState.selectedCategory
          Row(
            modifier = Modifier
              .fillMaxWidth()
              .clip(RoundedCornerShape(10.dp))
              .background(if (isSelected) BrandMintBackground else SurfaceCard)
              .clickable { viewModel.onCategorySelected(type) }
              .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
          ) {
            Icon(
              imageVector = if (isSelected) Icons.Default.Check else Icons.Default.Description,
              contentDescription = null,
              tint = if (isSelected) BrandTealPrimary else BrandNavyMuted,
              modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(10.dp))
            Text(
              text = type,
              fontSize = 13.sp,
              fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
              color = BrandNavyText
            )
          }
        }
      }

      Spacer(modifier = Modifier.height(28.dp))

      // 6. Action Button
      Button(
        onClick = {
          val activeDoc = uiState.selectedDocument
          val fileName = activeDoc?.fileName ?: "Medical_Report.pdf"
          val currentPerson = uiState.selectedFamilyMember?.name
            ?: familyMembers.getOrNull(selectedMemberIndex)?.name
            ?: "Self"
          viewModel.requestExtraction(
            onStarted = {
              onStartUploadAndExplain(fileName, currentPerson)
            }
          )
        },
        enabled = uiState.isReadyForUpload && !uiState.isExtracting,
        colors = ButtonDefaults.buttonColors(
          containerColor = BrandTealPrimary,
          disabledContainerColor = Color(0xFFD1D5DB)
        ),
        shape = RoundedCornerShape(28.dp),
        modifier = Modifier
          .fillMaxWidth()
          .height(52.dp)
          .testTag("btn_upload_and_analyze")
      ) {
        if (uiState.isExtracting) {
          CircularProgressIndicator(
            modifier = Modifier.size(22.dp),
            color = Color.White,
            strokeWidth = 2.dp
          )
          Spacer(modifier = Modifier.width(8.dp))
          Text(
            text = "Extracting Medical Data...",
            fontSize = 15.sp,
            fontWeight = FontWeight.Bold,
            color = Color.White
          )
        } else {
          Icon(Icons.Default.CloudUpload, contentDescription = null)
          Spacer(modifier = Modifier.width(8.dp))
          Text(
            text = if (uiState.isReadyForUpload) "Continue & Extract Report" else "Select a Document to Continue",
            fontSize = 15.sp,
            fontWeight = FontWeight.Bold,
            color = Color.White
          )
        }
      }

      Spacer(modifier = Modifier.height(20.dp))
    }
  }

  // Full Document Preview Modal
  if (uiState.isPreviewModalVisible && uiState.selectedDocument != null) {
    DocumentPreviewDialog(
      document = uiState.selectedDocument!!,
      previewState = uiState.previewState,
      onNextPage = { viewModel.navigatePdfPage(next = true) },
      onPrevPage = { viewModel.navigatePdfPage(next = false) },
      onDismiss = { viewModel.setPreviewModalVisible(false) }
    )
  }

  // Explicit AI Medical Processing Consent Dialog
  if (uiState.isConsentDialogVisible) {
    MedicalAiConsentDialog(
      onConfirm = {
        val activeDoc = uiState.selectedDocument
        val fileName = activeDoc?.fileName ?: "Medical_Report.pdf"
        val currentPerson = uiState.selectedFamilyMember?.name
          ?: familyMembers.getOrNull(selectedMemberIndex)?.name
          ?: "Self"
        viewModel.confirmConsentAndStartExtraction(
          onStarted = {
            onStartUploadAndExplain(fileName, currentPerson)
          }
        )
      },
      onDismiss = { viewModel.setConsentDialogVisible(false) }
    )
  }
}

/**
 * Privacy & Security consent dialog before sending document bytes to Firebase AI Logic.
 */
@Composable
private fun MedicalAiConsentDialog(
  onConfirm: () -> Unit,
  onDismiss: () -> Unit
) {
  Dialog(
    onDismissRequest = onDismiss,
    properties = DialogProperties(dismissOnBackPress = true, dismissOnClickOutside = false)
  ) {
    Card(
      shape = RoundedCornerShape(20.dp),
      colors = CardDefaults.cardColors(containerColor = Color.White),
      modifier = Modifier
        .fillMaxWidth(0.95f)
        .testTag("dialog_ai_consent")
    ) {
      Column(
        modifier = Modifier
          .fillMaxWidth()
          .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
      ) {
        Box(
          contentAlignment = Alignment.Center,
          modifier = Modifier
            .size(56.dp)
            .background(BrandMintBackground, CircleShape)
        ) {
          Icon(
            imageVector = Icons.Default.Lock,
            contentDescription = null,
            tint = BrandTealPrimary,
            modifier = Modifier.size(30.dp)
          )
        }

        Spacer(modifier = Modifier.height(16.dp))

        Text(
          text = "Medical Privacy & AI Consent",
          fontSize = 18.sp,
          fontWeight = FontWeight.Bold,
          color = BrandNavyText,
          textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(10.dp))

        Text(
          text = "To extract information from your medical report, NexzyHealth securely transmits document contents over encrypted TLS to Firebase AI Logic.\n\n• Your data is processed ephemerally and never used to train public foundation models.\n• No public file hosting or third-party advertising tracking is performed.\n• Only verified app sessions protected by App Check can request extraction.",
          fontSize = 13.sp,
          color = BrandNavyMuted,
          lineHeight = 18.sp,
          textAlign = TextAlign.Start
        )

        Spacer(modifier = Modifier.height(20.dp))

        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
          OutlinedButton(
            onClick = onDismiss,
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier
              .weight(1f)
              .height(48.dp)
              .testTag("btn_consent_cancel")
          ) {
            Text("Cancel", color = BrandNavyMuted)
          }

          Button(
            onClick = onConfirm,
            colors = ButtonDefaults.buttonColors(containerColor = BrandTealPrimary),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier
              .weight(1f)
              .height(48.dp)
              .testTag("btn_consent_confirm")
          ) {
            Text("I Agree & Extract", color = Color.White, fontWeight = FontWeight.Bold)
          }
        }
      }
    }
  }
}

/**
 * Empty state dropzone when no file has been selected yet.
 */
@Composable
private fun EmptyDocumentDropzone(
  onSelectFileClick: () -> Unit
) {
  Card(
    shape = RoundedCornerShape(18.dp),
    colors = CardDefaults.cardColors(containerColor = SurfaceCard),
    modifier = Modifier
      .fillMaxWidth()
      .border(
        width = 1.5.dp,
        color = SurfaceBorder,
        shape = RoundedCornerShape(18.dp)
      )
      .clickable(onClick = onSelectFileClick)
      .testTag("upload_dropzone")
  ) {
    Column(
      modifier = Modifier
        .fillMaxWidth()
        .padding(24.dp),
      horizontalAlignment = Alignment.CenterHorizontally
    ) {
      Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
          .size(60.dp)
          .background(BrandMintBackground, CircleShape)
      ) {
        Icon(
          imageVector = Icons.Default.CloudUpload,
          contentDescription = null,
          tint = BrandTealPrimary,
          modifier = Modifier.size(34.dp)
        )
      }

      Spacer(modifier = Modifier.height(14.dp))

      Text(
        text = "Tap to choose lab report PDF or Image",
        fontWeight = FontWeight.Bold,
        fontSize = 15.sp,
        color = BrandNavyText
      )

      Spacer(modifier = Modifier.height(4.dp))

      Text(
        text = "Supports PDF, JPG, JPEG, and PNG (up to 15 MB)",
        fontSize = 12.sp,
        color = BrandNavyMuted,
        textAlign = TextAlign.Center
      )

      Spacer(modifier = Modifier.height(14.dp))

      Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
      ) {
        SupportedFormatChip("PDF")
        SupportedFormatChip("JPG")
        SupportedFormatChip("PNG")
      }
    }
  }
}

/**
 * Card displaying metadata and inline preview thumbnail of the selected document.
 */
@Composable
private fun SelectedDocumentCard(
  document: SelectedDocument,
  previewState: DocumentPreviewState,
  onPreviewClick: () -> Unit,
  onChangeClick: () -> Unit,
  onRemoveClick: () -> Unit
) {
  Card(
    shape = RoundedCornerShape(18.dp),
    colors = CardDefaults.cardColors(containerColor = BrandMintBackground.copy(alpha = 0.45f)),
    modifier = Modifier
      .fillMaxWidth()
      .border(1.5.dp, BrandTealPrimary, RoundedCornerShape(18.dp))
      .testTag("selected_document_card")
  ) {
    Column(
      modifier = Modifier
        .fillMaxWidth()
        .padding(16.dp)
    ) {
      // Document metadata header
      Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
      ) {
        Box(
          contentAlignment = Alignment.Center,
          modifier = Modifier
            .size(46.dp)
            .background(BrandMintBackground, RoundedCornerShape(12.dp))
        ) {
          Icon(
            imageVector = if (document.isPdf) Icons.Default.PictureAsPdf else Icons.Default.Image,
            contentDescription = null,
            tint = BrandTealPrimary,
            modifier = Modifier.size(26.dp)
          )
        }

        Spacer(modifier = Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {
          Text(
            text = document.fileName,
            fontWeight = FontWeight.Bold,
            fontSize = 15.sp,
            color = BrandNavyText,
            maxLines = 1
          )

          Spacer(modifier = Modifier.height(2.dp))

          Text(
            text = "${if (document.isPdf) "PDF Document" else "Image File"} • ${document.formattedSize}",
            fontSize = 12.sp,
            color = BrandNavyMuted
          )
        }

        IconButton(
          onClick = onRemoveClick,
          modifier = Modifier.testTag("btn_remove_document")
        ) {
          Icon(
            imageVector = Icons.Default.DeleteOutline,
            contentDescription = "Remove document",
            tint = Color(0xFFDC2626)
          )
        }
      }

      Spacer(modifier = Modifier.height(12.dp))

      // Thumbnail Preview Area
      Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
          .fillMaxWidth()
          .height(120.dp)
          .clip(RoundedCornerShape(12.dp))
          .background(Color.White)
          .border(1.dp, SurfaceBorder, RoundedCornerShape(12.dp))
          .clickable(onClick = onPreviewClick)
          .testTag("document_preview_thumbnail")
      ) {
        when (previewState) {
          is DocumentPreviewState.Loading -> {
            Row(
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.Center
            ) {
              CircularProgressIndicator(
                modifier = Modifier.size(20.dp),
                color = BrandTealPrimary,
                strokeWidth = 2.dp
              )
              Spacer(modifier = Modifier.width(8.dp))
              Text(
                text = "Rendering preview...",
                fontSize = 12.sp,
                color = BrandNavyMuted
              )
            }
          }
          is DocumentPreviewState.Ready -> {
            if (previewState.bitmap != null) {
              Image(
                bitmap = previewState.bitmap.asImageBitmap(),
                contentDescription = "Document thumbnail",
                contentScale = ContentScale.Fit,
                modifier = Modifier
                  .fillMaxSize()
                  .padding(4.dp)
              )
            } else {
              Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(
                  imageVector = if (document.isPdf) Icons.Default.PictureAsPdf else Icons.Default.Image,
                  contentDescription = null,
                  tint = BrandTealPrimary,
                  modifier = Modifier.size(36.dp)
                )
                Text("Tap to view document", fontSize = 11.sp, color = BrandNavyMuted)
              }
            }
          }
          is DocumentPreviewState.Error -> {
            Column(
              horizontalAlignment = Alignment.CenterHorizontally,
              modifier = Modifier.padding(8.dp)
            ) {
              Icon(
                imageVector = Icons.Default.ErrorOutline,
                contentDescription = null,
                tint = Color(0xFFDC2626),
                modifier = Modifier.size(24.dp)
              )
              Spacer(modifier = Modifier.height(4.dp))
              Text(
                text = previewState.message,
                fontSize = 11.sp,
                color = Color(0xFF991B1B),
                textAlign = TextAlign.Center,
                maxLines = 2
              )
            }
          }
          DocumentPreviewState.None -> {
            Text("Preview unavailable", fontSize = 12.sp, color = BrandNavyMuted)
          }
        }
      }

      Spacer(modifier = Modifier.height(12.dp))

      // Action Row: Preview Document, Change, Remove
      Row(
        modifier = Modifier
          .fillMaxWidth()
          .background(Color.White, RoundedCornerShape(10.dp))
          .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
      ) {
        Row(
          verticalAlignment = Alignment.CenterVertically,
          modifier = Modifier
            .clickable(onClick = onPreviewClick)
            .testTag("btn_preview_document")
        ) {
          Icon(
            imageVector = Icons.Default.Visibility,
            contentDescription = null,
            tint = BrandTealPrimary,
            modifier = Modifier.size(16.dp)
          )
          Spacer(modifier = Modifier.width(6.dp))
          Text(
            text = "Preview Document",
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            color = BrandTealPrimary
          )
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
          Text(
            text = "Change",
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            color = BrandNavyText,
            modifier = Modifier
              .clickable(onClick = onChangeClick)
              .testTag("btn_change_document")
          )
          Spacer(modifier = Modifier.width(12.dp))
          Text(
            text = "Remove",
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            color = Color(0xFFDC2626),
            modifier = Modifier
              .clickable(onClick = onRemoveClick)
              .testTag("btn_remove_document_text")
          )
        }
      }
    }
  }
}

/**
 * Full-screen / expanded preview dialog displaying rendered PDF or image.
 */
@Composable
private fun DocumentPreviewDialog(
  document: SelectedDocument,
  previewState: DocumentPreviewState,
  onNextPage: () -> Unit,
  onPrevPage: () -> Unit,
  onDismiss: () -> Unit
) {
  var scale by remember { mutableFloatStateOf(1f) }
  var offsetX by remember { mutableFloatStateOf(0f) }
  var offsetY by remember { mutableFloatStateOf(0f) }

  Dialog(
    onDismissRequest = onDismiss,
    properties = DialogProperties(usePlatformDefaultWidth = false)
  ) {
    Card(
      shape = RoundedCornerShape(16.dp),
      colors = CardDefaults.cardColors(containerColor = Color.White),
      modifier = Modifier
        .fillMaxWidth(0.95f)
        .fillMaxHeight(0.85f)
        .testTag("document_preview_dialog")
    ) {
      Column(modifier = Modifier.fillMaxSize()) {
        // Header
        Row(
          modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.SpaceBetween
        ) {
          Column(modifier = Modifier.weight(1f)) {
            Text(
              text = document.fileName,
              fontWeight = FontWeight.Bold,
              fontSize = 15.sp,
              color = BrandNavyText,
              maxLines = 1
            )
            Text(
              text = if (document.isPdf) "PDF Document Preview" else "Image Document Preview",
              fontSize = 11.sp,
              color = BrandNavyMuted
            )
          }

          IconButton(
            onClick = onDismiss,
            modifier = Modifier.testTag("btn_close_preview")
          ) {
            Icon(Icons.Default.Close, contentDescription = "Close preview")
          }
        }

        Box(
          modifier = Modifier
            .fillMaxWidth()
            .height(1.dp)
            .background(SurfaceBorder)
        )

        // Content Display
        Box(
          contentAlignment = Alignment.Center,
          modifier = Modifier
            .weight(1f)
            .fillMaxWidth()
            .background(Color(0xFFF8FAFC))
            .pointerInput(Unit) {
              detectTransformGestures { _, pan, zoom, _ ->
                scale = (scale * zoom).coerceIn(1f, 3.5f)
                if (scale > 1f) {
                  offsetX += pan.x
                  offsetY += pan.y
                } else {
                  offsetX = 0f
                  offsetY = 0f
                }
              }
            }
        ) {
          when (previewState) {
            is DocumentPreviewState.Loading -> {
              Column(horizontalAlignment = Alignment.CenterHorizontally) {
                CircularProgressIndicator(color = BrandTealPrimary)
                Spacer(modifier = Modifier.height(12.dp))
                Text("Rendering document preview...", color = BrandNavyMuted, fontSize = 13.sp)
              }
            }
            is DocumentPreviewState.Ready -> {
              if (previewState.bitmap != null) {
                Image(
                  bitmap = previewState.bitmap.asImageBitmap(),
                  contentDescription = "Document full preview",
                  contentScale = ContentScale.Fit,
                  modifier = Modifier
                    .fillMaxSize()
                    .padding(12.dp)
                    .graphicsLayer(
                      scaleX = scale,
                      scaleY = scale,
                      translationX = offsetX,
                      translationY = offsetY
                    )
                )
              } else {
                Text("Preview bitmap not available", color = BrandNavyMuted)
              }
            }
            is DocumentPreviewState.Error -> {
              Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(24.dp)
              ) {
                Icon(
                  imageVector = Icons.Default.Lock,
                  contentDescription = null,
                  tint = Color(0xFFDC2626),
                  modifier = Modifier.size(42.dp)
                )
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                  text = "Preview Error",
                  fontWeight = FontWeight.Bold,
                  fontSize = 16.sp,
                  color = BrandNavyText
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                  text = previewState.message,
                  fontSize = 13.sp,
                  color = Color(0xFF991B1B),
                  textAlign = TextAlign.Center
                )
              }
            }
            DocumentPreviewState.None -> {
              Text("No document preview", color = BrandNavyMuted)
            }
          }
        }

        // Multi-page navigation footer for PDF
        if (previewState is DocumentPreviewState.Ready && previewState.isPdf) {
          Row(
            modifier = Modifier
              .fillMaxWidth()
              .background(Color.White)
              .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
          ) {
            OutlinedButton(
              onClick = onPrevPage,
              enabled = previewState.currentPage > 1,
              shape = RoundedCornerShape(8.dp),
              modifier = Modifier.testTag("btn_prev_page")
            ) {
              Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null, modifier = Modifier.size(16.dp))
              Spacer(modifier = Modifier.width(4.dp))
              Text("Previous", fontSize = 12.sp)
            }

            Text(
              text = previewState.pageInfo,
              fontSize = 12.sp,
              fontWeight = FontWeight.SemiBold,
              color = BrandNavyText
            )

            OutlinedButton(
              onClick = onNextPage,
              enabled = previewState.currentPage < previewState.pageCount,
              shape = RoundedCornerShape(8.dp),
              modifier = Modifier.testTag("btn_next_page")
            ) {
              Text("Next", fontSize = 12.sp)
              Spacer(modifier = Modifier.width(4.dp))
              Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null, modifier = Modifier.size(16.dp))
            }
          }
        }
      }
    }
  }
}

@Composable
private fun SupportedFormatChip(label: String) {
  Box(
    modifier = Modifier
      .clip(RoundedCornerShape(6.dp))
      .background(Color.White)
      .border(1.dp, SurfaceBorder, RoundedCornerShape(6.dp))
      .padding(horizontal = 8.dp, vertical = 3.dp)
  ) {
    Text(
      text = label,
      fontSize = 11.sp,
      fontWeight = FontWeight.Bold,
      color = BrandNavyText
    )
  }
}
