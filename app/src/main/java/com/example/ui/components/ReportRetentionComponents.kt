package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BookmarkAdded
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.model.PersistedMedicalReport
import com.example.model.ReportPersistenceSyncStatus
import com.example.model.ReportRetentionPolicy
import com.example.model.RetentionDecision
import com.example.ui.theme.BrandMintBackground
import com.example.ui.theme.BrandMintSurface
import com.example.ui.theme.BrandNavyMuted
import com.example.ui.theme.BrandNavyText
import com.example.ui.theme.BrandTealPrimary
import com.example.ui.theme.StatusAttentionHoney

@Composable
fun ReportRetentionBanner(
  report: PersistedMedicalReport?,
  syncStatus: ReportPersistenceSyncStatus,
  onKeepReportClick: () -> Unit,
  onDeleteReportClick: () -> Unit,
  modifier: Modifier = Modifier
) {
  when {
    syncStatus is ReportPersistenceSyncStatus.Saving -> {
      Card(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = BrandMintSurface),
        border = BorderStroke(1.dp, BrandTealPrimary.copy(alpha = 0.3f)),
        modifier = modifier
          .fillMaxWidth()
          .testTag("card_report_saving")
      ) {
        Row(
          modifier = Modifier.padding(14.dp),
          verticalAlignment = Alignment.CenterVertically
        ) {
          CircularProgressIndicator(
            modifier = Modifier.size(18.dp),
            strokeWidth = 2.dp,
            color = BrandTealPrimary
          )
          Spacer(modifier = Modifier.width(12.dp))
          Text(
            text = stringResource(R.string.report_retention_saving),
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
            color = BrandNavyText
          )
        }
      }
    }

    syncStatus is ReportPersistenceSyncStatus.Deleting -> {
      Card(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFFFEF2F2)),
        border = BorderStroke(1.dp, Color(0xFFFECACA)),
        modifier = modifier
          .fillMaxWidth()
          .testTag("card_report_deleting")
      ) {
        Row(
          modifier = Modifier.padding(14.dp),
          verticalAlignment = Alignment.CenterVertically
        ) {
          CircularProgressIndicator(
            modifier = Modifier.size(18.dp),
            strokeWidth = 2.dp,
            color = Color(0xFFDC2626)
          )
          Spacer(modifier = Modifier.width(12.dp))
          Text(
            text = "Removing report and original file...",
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
            color = Color(0xFF991B1B)
          )
        }
      }
    }

    syncStatus is ReportPersistenceSyncStatus.Error -> {
      Card(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFFFEF3C7)),
        border = BorderStroke(1.dp, Color(0xFFFDE68A)),
        modifier = modifier
          .fillMaxWidth()
          .testTag("card_report_save_error")
      ) {
        Row(
          modifier = Modifier.padding(14.dp),
          verticalAlignment = Alignment.CenterVertically
        ) {
          Icon(
            imageVector = Icons.Default.ErrorOutline,
            contentDescription = null,
            tint = Color(0xFFB45309),
            modifier = Modifier.size(20.dp)
          )
          Spacer(modifier = Modifier.width(10.dp))
          Text(
            text = syncStatus.message,
            fontSize = 12.sp,
            color = Color(0xFF78350F),
            lineHeight = 17.sp
          )
        }
      }
    }

    report != null && report.retentionDecision == RetentionDecision.PENDING_DECISION -> {
      val expiryText = report.expiresAtMillis?.let { ReportRetentionPolicy.formatShortDate(it) }
      Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = StatusAttentionHoney),
        border = BorderStroke(1.dp, Color(0xFFFCD34D)),
        modifier = modifier
          .fillMaxWidth()
          .testTag("card_retention_decision")
      ) {
        Column(modifier = Modifier.padding(14.dp)) {
          Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
              imageVector = Icons.Default.Schedule,
              contentDescription = null,
              tint = Color(0xFFB45309),
              modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
              text = stringResource(R.string.report_retention_prompt_title),
              fontWeight = FontWeight.Bold,
              fontSize = 14.sp,
              color = Color(0xFF92400E)
            )
          }
          Spacer(modifier = Modifier.height(6.dp))
          Text(
            text = stringResource(R.string.report_retention_prompt_body) +
              if (expiryText != null) " Expiry if kept: $expiryText." else "",
            fontSize = 12.sp,
            lineHeight = 17.sp,
            color = Color(0xFF78350F)
          )
          Spacer(modifier = Modifier.height(12.dp))
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
          ) {
            Button(
              onClick = onKeepReportClick,
              colors = ButtonDefaults.buttonColors(containerColor = BrandTealPrimary),
              shape = RoundedCornerShape(12.dp),
              modifier = Modifier
                .weight(1f)
                .defaultMinSize(minHeight = 48.dp)
                .testTag("btn_keep_report_7_days")
            ) {
              Icon(
                imageVector = Icons.Default.BookmarkAdded,
                contentDescription = null,
                modifier = Modifier.size(16.dp)
              )
              Spacer(modifier = Modifier.width(6.dp))
              Text(
                text = stringResource(R.string.report_retention_keep_cta),
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White
              )
            }
            OutlinedButton(
              onClick = onDeleteReportClick,
              border = BorderStroke(1.dp, Color(0xFFDC2626)),
              shape = RoundedCornerShape(12.dp),
              modifier = Modifier
                .weight(1f)
                .defaultMinSize(minHeight = 48.dp)
                .testTag("btn_delete_report_now")
            ) {
              Icon(
                imageVector = Icons.Default.DeleteOutline,
                contentDescription = null,
                tint = Color(0xFFDC2626),
                modifier = Modifier.size(16.dp)
              )
              Spacer(modifier = Modifier.width(6.dp))
              Text(
                text = stringResource(R.string.report_retention_delete_cta),
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFFDC2626)
              )
            }
          }
        }
      }
    }

    report != null && report.retentionDecision == RetentionDecision.KEEP_SEVEN_DAYS -> {
      val expiryText = report.expiresAtMillis?.let {
        ReportRetentionPolicy.formatShortDate(it)
      } ?: "7 days after upload"
      Card(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = BrandMintBackground),
        border = BorderStroke(1.dp, BrandTealPrimary.copy(alpha = 0.35f)),
        modifier = modifier
          .fillMaxWidth()
          .testTag("card_retention_kept")
      ) {
        Row(
          modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 10.dp),
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.SpaceBetween
        ) {
          Row(
            modifier = Modifier.weight(1f),
            verticalAlignment = Alignment.CenterVertically
          ) {
            Icon(
              imageVector = Icons.Default.CloudDone,
              contentDescription = null,
              tint = BrandTealPrimary,
              modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
              text = stringResource(R.string.report_retention_kept_banner, expiryText),
              fontSize = 12.sp,
              fontWeight = FontWeight.SemiBold,
              color = BrandNavyText
            )
          }
          TextButton(
            onClick = onDeleteReportClick,
            modifier = Modifier
              .defaultMinSize(minHeight = 48.dp)
              .testTag("btn_delete_kept_report")
          ) {
            Text(
              text = "Delete",
              color = Color(0xFFDC2626),
              fontWeight = FontWeight.Bold,
              fontSize = 12.sp
            )
          }
        }
      }
    }
  }
}

@Composable
fun ReportExitRetentionDialog(
  expiresAtMillis: Long?,
  onKeepAndExit: () -> Unit,
  onDeleteAndLeave: () -> Unit
) {
  val expiryLine = expiresAtMillis?.let {
    "\n\nKept reports stay encrypted and auto-delete on ${ReportRetentionPolicy.formatShortDate(it)}."
  } ?: ""

  AlertDialog(
    onDismissRequest = onDeleteAndLeave,
    title = {
      Text(
        text = stringResource(R.string.report_retention_prompt_title),
        fontWeight = FontWeight.Bold,
        color = BrandNavyText
      )
    },
    text = {
      Text(
        text = stringResource(R.string.report_retention_prompt_body) + expiryLine,
        fontSize = 13.sp,
        lineHeight = 19.sp,
        color = BrandNavyMuted
      )
    },
    confirmButton = {
      Button(
        onClick = onKeepAndExit,
        colors = ButtonDefaults.buttonColors(containerColor = BrandTealPrimary),
        modifier = Modifier
          .defaultMinSize(minHeight = 48.dp)
          .testTag("btn_dialog_keep_report")
      ) {
        Text(
          text = stringResource(R.string.report_retention_keep_cta),
          color = Color.White,
          fontWeight = FontWeight.Bold
        )
      }
    },
    dismissButton = {
      OutlinedButton(
        onClick = onDeleteAndLeave,
        border = BorderStroke(1.dp, Color(0xFFDC2626)),
        modifier = Modifier
          .defaultMinSize(minHeight = 48.dp)
          .testTag("btn_dialog_delete_leave")
      ) {
        Text(
          text = stringResource(R.string.report_retention_delete_leave_cta),
          color = Color(0xFFDC2626),
          fontWeight = FontWeight.Bold
        )
      }
    }
  )
}

@Composable
fun PendingOfflineDeletionBanner(
  pendingCount: Int,
  onRetryClick: (() -> Unit)? = null,
  modifier: Modifier = Modifier
) {
  if (pendingCount <= 0) return

  Card(
    shape = RoundedCornerShape(14.dp),
    colors = CardDefaults.cardColors(containerColor = Color(0xFFFEF3C7)),
    border = BorderStroke(1.dp, Color(0xFFFCD34D)),
    modifier = modifier
      .fillMaxWidth()
      .testTag("banner_pending_offline_deletions")
  ) {
    Row(
      modifier = Modifier
        .fillMaxWidth()
        .padding(horizontal = 14.dp, vertical = 12.dp),
      verticalAlignment = Alignment.CenterVertically
    ) {
      Icon(
        imageVector = Icons.Default.CloudOff,
        contentDescription = stringResource(R.string.report_pending_deletion_title),
        tint = Color(0xFFB45309),
        modifier = Modifier.size(20.dp)
      )
      Spacer(modifier = Modifier.width(10.dp))
      Column(modifier = Modifier.weight(1f)) {
        Text(
          text = stringResource(R.string.report_pending_deletion_title),
          fontWeight = FontWeight.Bold,
          fontSize = 13.sp,
          color = Color(0xFF92400E)
        )
        Spacer(modifier = Modifier.height(2.dp))
        Text(
          text = stringResource(R.string.report_pending_deletion_subtitle, pendingCount),
          fontSize = 11.sp,
          lineHeight = 16.sp,
          color = Color(0xFF78350F)
        )
      }
      if (onRetryClick != null) {
        TextButton(
          onClick = onRetryClick,
          modifier = Modifier
            .defaultMinSize(minHeight = 48.dp)
            .testTag("btn_retry_pending_deletions")
        ) {
          Text(
            text = "Sync",
            color = Color(0xFFB45309),
            fontWeight = FontWeight.Bold,
            fontSize = 12.sp
          )
        }
      }
    }
  }
}
