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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.components.NexzyTopAppBar
import com.example.ui.theme.BrandMintBackground
import com.example.ui.theme.BrandMintSurface
import com.example.ui.theme.BrandNavyMuted
import com.example.ui.theme.BrandNavyText
import com.example.ui.theme.BrandTealPrimary
import com.example.ui.theme.SurfaceBorder
import com.example.ui.theme.SurfaceCard

@Composable
fun PrivacyScreen(
  onNavigateBack: () -> Unit,
  onAccountDeleted: () -> Unit,
  modifier: Modifier = Modifier
) {
  var storeOnDeviceOnly by remember { mutableStateOf(false) }
  var autoDelete30Days by remember { mutableStateOf(false) }
  var showDeleteDialog by remember { mutableStateOf(false) }
  var exportFeedback by remember { mutableStateOf<String?>(null) }

  Column(
    modifier = modifier
      .fillMaxSize()
      .background(Color.White)
  ) {
    NexzyTopAppBar(
      title = "Privacy & Data Deletion",
      subtitle = "Zero-selling pledge & total user control",
      showBackButton = true,
      onBackClick = onNavigateBack
    )

    Column(
      modifier = Modifier
        .fillMaxSize()
        .verticalScroll(rememberScrollState())
        .padding(horizontal = 20.dp, vertical = 12.dp)
    ) {
      // Privacy Pledge Card
      Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = BrandMintSurface),
        modifier = Modifier
          .fillMaxWidth()
          .border(1.dp, BrandTealPrimary.copy(alpha = 0.3f), RoundedCornerShape(16.dp))
      ) {
        Column(modifier = Modifier.padding(16.dp)) {
          Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
              contentAlignment = Alignment.Center,
              modifier = Modifier
                .size(38.dp)
                .background(BrandMintBackground, CircleShape)
            ) {
              Icon(
                imageVector = Icons.Default.Shield,
                contentDescription = null,
                tint = BrandTealPrimary,
                modifier = Modifier.size(22.dp)
              )
            }
            Spacer(modifier = Modifier.width(12.dp))
            Text(
              text = "Our Strict Privacy Pledge",
              fontWeight = FontWeight.Bold,
              fontSize = 16.sp,
              color = BrandNavyText
            )
          }
          Spacer(modifier = Modifier.height(10.dp))
          Text(
            text = "• We NEVER sell or share your medical data with insurance brokers, pharmaceutical advertisers, or employers.\n• All laboratory files are encrypted with industry-standard 256-bit encryption.\n• You maintain absolute ownership and can permanently erase your entire history anytime.",
            fontSize = 13.sp,
            lineHeight = 20.sp,
            color = BrandNavyText
          )
        }
      }

      Spacer(modifier = Modifier.height(20.dp))

      Text(
        text = "Data Retention Controls",
        style = MaterialTheme.typography.titleMedium,
        color = BrandNavyText,
        fontWeight = FontWeight.Bold
      )

      Spacer(modifier = Modifier.height(10.dp))

      Card(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = SurfaceCard),
        modifier = Modifier.fillMaxWidth().border(1.dp, SurfaceBorder, RoundedCornerShape(14.dp))
      ) {
        Column(modifier = Modifier.padding(14.dp)) {
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
          ) {
            Column(modifier = Modifier.weight(1f)) {
              Text(
                text = "Store Reports on Device Only",
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp,
                color = BrandNavyText
              )
              Text(
                text = "Prevents any cloud backup; reports stay strictly on this phone.",
                fontSize = 12.sp,
                color = BrandNavyMuted
              )
            }
            Switch(
              checked = storeOnDeviceOnly,
              onCheckedChange = { storeOnDeviceOnly = it },
              colors = SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = BrandTealPrimary)
            )
          }

          Spacer(modifier = Modifier.height(14.dp))

          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
          ) {
            Column(modifier = Modifier.weight(1f)) {
              Text(
                text = "Auto-Delete After 30 Days",
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp,
                color = BrandNavyText
              )
              Text(
                text = "Automatically purges old medical uploads after one month.",
                fontSize = 12.sp,
                color = BrandNavyMuted
              )
            }
            Switch(
              checked = autoDelete30Days,
              onCheckedChange = { autoDelete30Days = it },
              colors = SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = BrandTealPrimary)
            )
          }
        }
      }

      Spacer(modifier = Modifier.height(20.dp))

      Text(
        text = "Export Health History",
        style = MaterialTheme.typography.titleMedium,
        color = BrandNavyText,
        fontWeight = FontWeight.Bold
      )

      Spacer(modifier = Modifier.height(8.dp))

      OutlinedButton(
        onClick = { exportFeedback = "ZIP archive of all 14 reports prepared for download." },
        shape = RoundedCornerShape(24.dp),
        modifier = Modifier
          .fillMaxWidth()
          .height(48.dp)
          .testTag("btn_export_health_data")
      ) {
        Icon(Icons.Default.Download, contentDescription = null, tint = BrandTealPrimary)
        Spacer(modifier = Modifier.width(8.dp))
        Text("Download Complete Health Archive (ZIP)", color = BrandNavyText, fontSize = 13.sp)
      }

      if (exportFeedback != null) {
        Spacer(modifier = Modifier.height(6.dp))
        Text(
          text = exportFeedback!!,
          color = BrandTealPrimary,
          fontSize = 12.sp,
          fontWeight = FontWeight.Medium
        )
      }

      Spacer(modifier = Modifier.height(28.dp))

      // Danger Zone: Data Deletion
      Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFFFEF2F2)),
        modifier = Modifier
          .fillMaxWidth()
          .border(1.dp, Color(0xFFFECACA), RoundedCornerShape(16.dp))
      ) {
        Column(modifier = Modifier.padding(16.dp)) {
          Text(
            text = "Danger Zone",
            fontWeight = FontWeight.Bold,
            fontSize = 14.sp,
            color = Color(0xFF991B1B)
          )
          Spacer(modifier = Modifier.height(4.dp))
          Text(
            text = "Permanently delete all your uploaded medical documents, explanations, audio recordings, and family profiles. This action is irreversible.",
            fontSize = 12.sp,
            lineHeight = 17.sp,
            color = Color(0xFF7F1D1D)
          )
          Spacer(modifier = Modifier.height(14.dp))
          Button(
            onClick = { showDeleteDialog = true },
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFDC2626)),
            shape = RoundedCornerShape(24.dp),
            modifier = Modifier
              .fillMaxWidth()
              .height(48.dp)
              .testTag("btn_delete_all_data")
          ) {
            Icon(Icons.Default.DeleteForever, contentDescription = null, tint = Color.White)
            Spacer(modifier = Modifier.width(8.dp))
            Text("Delete All My Reports & Data", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
          }
        }
      }

      Spacer(modifier = Modifier.height(24.dp))
    }
  }

  if (showDeleteDialog) {
    AlertDialog(
      onDismissRequest = { showDeleteDialog = false },
      title = {
        Text("Permanently Delete All Health Data?", fontWeight = FontWeight.Bold, color = Color(0xFF991B1B))
      },
      text = {
        Text(
          "Are you sure? This will immediately wipe all 14 analyzed medical reports and 3 family member profiles. You cannot undo this action.",
          color = BrandNavyText,
          fontSize = 13.sp
        )
      },
      confirmButton = {
        Button(
          onClick = {
            showDeleteDialog = false
            onAccountDeleted()
          },
          colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFDC2626))
        ) {
          Text("Yes, Delete Everything")
        }
      },
      dismissButton = {
        OutlinedButton(onClick = { showDeleteDialog = false }) {
          Text("Cancel", color = BrandNavyMuted)
        }
      }
    )
  }
}
