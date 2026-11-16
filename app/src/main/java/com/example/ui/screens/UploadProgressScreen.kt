package com.example.ui.screens

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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DocumentScanner
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.HourglassTop
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.DocumentExtractionResult
import com.example.model.DocumentExtractionState
import com.example.ui.theme.BrandMintBackground
import com.example.ui.theme.BrandMintSurface
import com.example.ui.theme.BrandNavyMuted
import com.example.ui.theme.BrandNavyText
import com.example.ui.theme.BrandTealPrimary

@Composable
fun UploadProgressScreen(
  fileName: String,
  extractionState: DocumentExtractionState,
  onExtractionSucceeded: (DocumentExtractionResult) -> Unit,
  onInspectUnidentified: () -> Unit,
  onRetryClick: () -> Unit,
  onCancelClick: () -> Unit,
  modifier: Modifier = Modifier
) {
  // Navigate to the real ExtractionResultScreen ONLY when extraction actually succeeds
  LaunchedEffect(extractionState) {
    if (extractionState is DocumentExtractionState.Success) {
      onExtractionSucceeded(extractionState.result)
    }
  }

  Column(
    modifier = modifier
      .fillMaxSize()
      .background(Color.White)
      .padding(24.dp)
      .testTag("upload_progress_screen"),
    horizontalAlignment = Alignment.CenterHorizontally,
    verticalArrangement = Arrangement.SpaceBetween
  ) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
      Spacer(modifier = Modifier.height(28.dp))

      val (iconBg, iconTint, headerIcon) = when (extractionState) {
        is DocumentExtractionState.Success -> Triple(BrandMintBackground, BrandTealPrimary, Icons.Default.CheckCircle)
        is DocumentExtractionState.Unidentified -> Triple(Color(0xFFFEF3C7), Color(0xFFB45309), Icons.Default.WarningAmber)
        is DocumentExtractionState.Failure -> Triple(Color(0xFFFEE2E2), Color(0xFFDC2626), Icons.Default.ErrorOutline)
        else -> Triple(BrandMintBackground, BrandTealPrimary, Icons.Default.DocumentScanner)
      }

      Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
          .size(72.dp)
          .background(iconBg, CircleShape)
      ) {
        Icon(
          imageVector = headerIcon,
          contentDescription = null,
          tint = iconTint,
          modifier = Modifier.size(40.dp)
        )
      }

      Spacer(modifier = Modifier.height(20.dp))

      val headline = when (extractionState) {
        is DocumentExtractionState.Idle,
        is DocumentExtractionState.WaitingForConsent -> "Preparing Extraction"
        is DocumentExtractionState.InProgress -> "Reading & Extracting Medical Data"
        is DocumentExtractionState.Success -> "Medical Extraction Complete"
        is DocumentExtractionState.Unidentified -> "Unidentified Document"
        is DocumentExtractionState.Failure -> "Extraction Failed"
      }

      Text(
        text = headline,
        style = MaterialTheme.typography.headlineSmall,
        color = BrandNavyText,
        fontWeight = FontWeight.Bold,
        textAlign = TextAlign.Center,
        modifier = Modifier.testTag("progress_headline")
      )

      Spacer(modifier = Modifier.height(6.dp))

      Text(
        text = fileName,
        style = MaterialTheme.typography.bodyMedium,
        color = BrandNavyMuted,
        fontWeight = FontWeight.Medium
      )

      Spacer(modifier = Modifier.height(24.dp))

      when (extractionState) {
        is DocumentExtractionState.InProgress -> {
          LinearProgressIndicator(
            modifier = Modifier
              .fillMaxWidth()
              .height(10.dp)
              .clip(RoundedCornerShape(5.dp))
              .testTag("progress_bar_active"),
            color = BrandTealPrimary,
            trackColor = BrandMintBackground,
            strokeCap = StrokeCap.Round
          )
        }
        is DocumentExtractionState.Success -> {
          LinearProgressIndicator(
            progress = { 1f },
            modifier = Modifier
              .fillMaxWidth()
              .height(10.dp)
              .clip(RoundedCornerShape(5.dp)),
            color = BrandTealPrimary,
            trackColor = BrandMintBackground,
            strokeCap = StrokeCap.Round
          )
        }
        is DocumentExtractionState.Idle,
        is DocumentExtractionState.WaitingForConsent -> {
          LinearProgressIndicator(
            progress = { 0.15f },
            modifier = Modifier
              .fillMaxWidth()
              .height(10.dp)
              .clip(RoundedCornerShape(5.dp)),
            color = BrandTealPrimary,
            trackColor = BrandMintBackground,
            strokeCap = StrokeCap.Round
          )
        }
        is DocumentExtractionState.Failure,
        is DocumentExtractionState.Unidentified -> Unit
      }

      Spacer(modifier = Modifier.height(24.dp))

      when (extractionState) {
        is DocumentExtractionState.Failure -> {
          Card(
            shape = RoundedCornerShape(14.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFFFEE2E2)),
            modifier = Modifier
              .fillMaxWidth()
              .border(1.dp, Color(0xFFFCA5A5), RoundedCornerShape(14.dp))
              .testTag("progress_error_card")
          ) {
            Column(modifier = Modifier.padding(16.dp)) {
              Text(
                text = "Could not extract data from this document",
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp,
                color = Color(0xFF991B1B)
              )
              Spacer(modifier = Modifier.height(6.dp))
              Text(
                text = extractionState.message,
                fontSize = 13.sp,
                color = Color(0xFF7F1D1D)
              )
            }
          }
        }

        is DocumentExtractionState.Unidentified -> {
          Card(
            shape = RoundedCornerShape(14.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFFFEF3C7)),
            modifier = Modifier
              .fillMaxWidth()
              .border(1.dp, Color(0xFFFDE68A), RoundedCornerShape(14.dp))
              .testTag("progress_unidentified_card")
          ) {
            Column(modifier = Modifier.padding(16.dp)) {
              Text(
                text = "Document could not be classified as a supported medical report",
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp,
                color = Color(0xFF92400E)
              )
              Spacer(modifier = Modifier.height(6.dp))
              Text(
                text = extractionState.reason,
                fontSize = 13.sp,
                color = Color(0xFF78350F)
              )
            }
          }
        }

        else -> {
          val isDone = extractionState is DocumentExtractionState.Success
          val isRunning = extractionState is DocumentExtractionState.InProgress

          Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(12.dp)
          ) {
            StepProgressRow(
              icon = Icons.Default.Verified,
              title = "Step 1: Consent & Document Validation",
              status = StepStatus.DONE
            )
            StepProgressRow(
              icon = Icons.Default.DocumentScanner,
              title = "Step 2: Medical Document Classification & Reading",
              status = when {
                isDone -> StepStatus.DONE
                isRunning -> StepStatus.IN_PROGRESS
                else -> StepStatus.PENDING
              }
            )
            StepProgressRow(
              icon = Icons.Default.HourglassTop,
              title = "Step 3: Extracting Values, Status & Page Provenance",
              status = when {
                isDone -> StepStatus.DONE
                isRunning -> StepStatus.IN_PROGRESS
                else -> StepStatus.PENDING
              }
            )
          }
        }
      }
    }

    // Bottom Actions
    Column(
      modifier = Modifier.fillMaxWidth(),
      horizontalAlignment = Alignment.CenterHorizontally
    ) {
      when (extractionState) {
        is DocumentExtractionState.Success -> {
          Button(
            onClick = { onExtractionSucceeded(extractionState.result) },
            colors = ButtonDefaults.buttonColors(containerColor = BrandTealPrimary),
            shape = RoundedCornerShape(28.dp),
            modifier = Modifier
              .fillMaxWidth()
              .height(52.dp)
              .testTag("btn_view_extracted_result")
          ) {
            Icon(Icons.Default.AutoAwesome, contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
            Text(
              text = "View Extracted Report",
              fontSize = 16.sp,
              fontWeight = FontWeight.Bold,
              color = Color.White
            )
          }
        }

        is DocumentExtractionState.Unidentified -> {
          Button(
            onClick = onInspectUnidentified,
            colors = ButtonDefaults.buttonColors(containerColor = BrandTealPrimary),
            shape = RoundedCornerShape(28.dp),
            modifier = Modifier
              .fillMaxWidth()
              .height(52.dp)
              .testTag("btn_view_unidentified_details")
          ) {
            Text(
              text = "View Details",
              fontSize = 15.sp,
              fontWeight = FontWeight.Bold,
              color = Color.White
            )
          }
          Spacer(modifier = Modifier.height(10.dp))
          OutlinedButton(
            onClick = onCancelClick,
            shape = RoundedCornerShape(28.dp),
            modifier = Modifier
              .fillMaxWidth()
              .height(48.dp)
              .testTag("btn_choose_another_from_unidentified")
          ) {
            Text("Choose Another Document", color = BrandNavyText)
          }
        }

        is DocumentExtractionState.Failure -> {
          if (extractionState.isRetryable) {
            Button(
              onClick = onRetryClick,
              colors = ButtonDefaults.buttonColors(containerColor = BrandTealPrimary),
              shape = RoundedCornerShape(28.dp),
              modifier = Modifier
                .fillMaxWidth()
                .height(52.dp)
                .testTag("btn_retry_extraction")
            ) {
              Icon(Icons.Default.Refresh, contentDescription = null)
              Spacer(modifier = Modifier.width(8.dp))
              Text(
                text = "Retry Extraction",
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White
              )
            }
            Spacer(modifier = Modifier.height(10.dp))
          }
          OutlinedButton(
            onClick = onCancelClick,
            shape = RoundedCornerShape(28.dp),
            modifier = Modifier
              .fillMaxWidth()
              .height(48.dp)
              .testTag("btn_back_from_failure")
          ) {
            Text("Choose Another Document", color = BrandNavyText)
          }
        }

        else -> {
          OutlinedButton(
            onClick = onCancelClick,
            shape = RoundedCornerShape(28.dp),
            modifier = Modifier
              .fillMaxWidth()
              .height(48.dp)
              .testTag("btn_cancel_progress")
          ) {
            Text("Cancel Analysis", color = BrandNavyMuted)
          }
        }
      }

      Spacer(modifier = Modifier.height(16.dp))
    }
  }
}

private enum class StepStatus { PENDING, IN_PROGRESS, DONE }

@Composable
private fun StepProgressRow(
  icon: ImageVector,
  title: String,
  status: StepStatus
) {
  val (bgColor, tintColor, statusLabel) = when (status) {
    StepStatus.DONE -> Triple(BrandMintBackground, BrandTealPrimary, "Completed")
    StepStatus.IN_PROGRESS -> Triple(Color(0xFFFEF3C7), Color(0xFFD97706), "Processing...")
    StepStatus.PENDING -> Triple(Color(0xFFF1F5F9), Color(0xFF94A3B8), "Waiting")
  }

  Card(
    shape = RoundedCornerShape(12.dp),
    colors = CardDefaults.cardColors(containerColor = BrandMintSurface),
    modifier = Modifier.fillMaxWidth()
  ) {
    Row(
      modifier = Modifier.padding(14.dp),
      verticalAlignment = Alignment.CenterVertically
    ) {
      Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
          .size(34.dp)
          .background(bgColor, CircleShape)
      ) {
        Icon(
          imageVector = if (status == StepStatus.DONE) Icons.Default.CheckCircle else icon,
          contentDescription = null,
          tint = tintColor,
          modifier = Modifier.size(18.dp)
        )
      }
      Spacer(modifier = Modifier.width(12.dp))
      Text(
        text = title,
        fontWeight = if (status == StepStatus.IN_PROGRESS) FontWeight.Bold else FontWeight.Medium,
        fontSize = 13.sp,
        color = if (status == StepStatus.PENDING) Color(0xFF64748B) else BrandNavyText,
        modifier = Modifier.weight(1f)
      )
      Text(
        text = statusLabel,
        fontSize = 11.sp,
        fontWeight = FontWeight.SemiBold,
        color = tintColor
      )
    }
  }
}
