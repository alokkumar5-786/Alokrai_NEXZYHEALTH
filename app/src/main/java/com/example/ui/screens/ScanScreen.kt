package com.example.ui.screens

import android.app.Activity
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DocumentScanner
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.PictureAsPdf
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.components.NexzyTopAppBar
import com.example.ui.theme.BrandMintBackground
import com.example.ui.theme.BrandNavyMuted
import com.example.ui.theme.BrandNavyText
import com.example.ui.theme.BrandTealPrimary
import com.example.ui.theme.SurfaceBorder
import com.example.ui.theme.SurfaceCard
import com.example.ui.upload.DocumentScannerHelper
import com.google.mlkit.vision.documentscanner.GmsDocumentScanningResult

@Composable
fun ScanScreen(
  onCaptureCompleted: (Uri?) -> Unit,
  onNavigateBack: () -> Unit,
  onPickFromGallery: () -> Unit,
  modifier: Modifier = Modifier
) {
  val context = LocalContext.current
  val activity = context as? Activity

  var isLaunchingScanner by remember { mutableStateOf(false) }
  var scannerErrorMessage by remember { mutableStateOf<String?>(null) }

  // Official Google ML Kit Document Scanner launcher
  val scannerLauncher = rememberLauncherForActivityResult(
    contract = ActivityResultContracts.StartIntentSenderForResult()
  ) { result ->
    isLaunchingScanner = false
    if (result.resultCode == Activity.RESULT_OK) {
      val scanningResult = GmsDocumentScanningResult.fromActivityResultIntent(result.data)
      val scannedUri = scanningResult?.pdf?.uri ?: scanningResult?.pages?.firstOrNull()?.imageUri
      if (scannedUri != null) {
        onCaptureCompleted(scannedUri)
      } else {
        scannerErrorMessage = "No document output was generated."
      }
    } else if (result.resultCode == Activity.RESULT_CANCELED) {
      // User cancelled scan session
    } else {
      scannerErrorMessage = "Document scanning was cancelled or failed."
    }
  }

  fun startScanner() {
    if (activity != null) {
      isLaunchingScanner = true
      scannerErrorMessage = null
      DocumentScannerHelper.launchScanner(
        activity = activity,
        onIntentSenderReady = { request ->
          scannerLauncher.launch(request)
        },
        onError = { error ->
          isLaunchingScanner = false
          scannerErrorMessage = error
        }
      )
    } else {
      scannerErrorMessage = "Cannot launch scanner without an active screen context."
    }
  }

  Column(
    modifier = modifier
      .fillMaxSize()
      .background(Color.White)
  ) {
    NexzyTopAppBar(
      title = "Scan Medical Report",
      subtitle = "Google ML Kit Document Scanner",
      showBackButton = true,
      onBackClick = onNavigateBack
    )

    Column(
      modifier = Modifier
        .fillMaxSize()
        .padding(24.dp),
      horizontalAlignment = Alignment.CenterHorizontally,
      verticalArrangement = Arrangement.Center
    ) {
      // Scanner Feature Illustration Card
      Card(
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = BrandMintBackground.copy(alpha = 0.5f)),
        modifier = Modifier
          .fillMaxWidth()
          .border(1.5.dp, BrandTealPrimary, RoundedCornerShape(24.dp))
          .testTag("scan_intro_card")
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
              .size(72.dp)
              .background(BrandMintBackground, CircleShape)
          ) {
            Icon(
              imageVector = Icons.Default.DocumentScanner,
              contentDescription = null,
              tint = BrandTealPrimary,
              modifier = Modifier.size(40.dp)
            )
          }

          Spacer(modifier = Modifier.height(16.dp))

          Text(
            text = "Official Document Scanner",
            fontWeight = FontWeight.ExtraBold,
            fontSize = 18.sp,
            color = BrandNavyText,
            textAlign = TextAlign.Center
          )

          Spacer(modifier = Modifier.height(6.dp))

          Text(
            text = "Powered by Google Play services ML Kit Document Scanner with automatic edge-detection, perspective correction, shadow removal, and multi-page PDF generation.",
            fontSize = 13.sp,
            color = BrandNavyMuted,
            textAlign = TextAlign.Center,
            lineHeight = 18.sp
          )

          Spacer(modifier = Modifier.height(18.dp))

          // Feature list
          Column(
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth()
          ) {
            ScannerFeatureRow(text = "Automatic edge detection and auto-cropping")
            ScannerFeatureRow(text = "Multi-page scan support (up to 20 pages)")
            ScannerFeatureRow(text = "Consolidated high-clarity PDF output")
          }
        }
      }

      // Scanner Error Alert if any
      if (scannerErrorMessage != null) {
        Spacer(modifier = Modifier.height(16.dp))
        Card(
          shape = RoundedCornerShape(12.dp),
          colors = CardDefaults.cardColors(containerColor = Color(0xFFFEE2E2)),
          modifier = Modifier.fillMaxWidth()
        ) {
          Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
          ) {
            Icon(Icons.Default.Info, contentDescription = null, tint = Color(0xFFDC2626), modifier = Modifier.size(20.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text(
              text = scannerErrorMessage!!,
              fontSize = 12.sp,
              color = Color(0xFF991B1B)
            )
          }
        }
      }

      Spacer(modifier = Modifier.height(32.dp))

      // Primary Start Scanner Button
      Button(
        onClick = { startScanner() },
        enabled = !isLaunchingScanner,
        colors = ButtonDefaults.buttonColors(containerColor = BrandTealPrimary),
        shape = RoundedCornerShape(28.dp),
        modifier = Modifier
          .fillMaxWidth()
          .height(52.dp)
          .testTag("scan_shutter_button")
      ) {
        if (isLaunchingScanner) {
          CircularProgressIndicator(
            modifier = Modifier.size(20.dp),
            color = Color.White,
            strokeWidth = 2.dp
          )
          Spacer(modifier = Modifier.width(8.dp))
          Text("Starting Document Scanner...", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = Color.White)
        } else {
          Icon(Icons.Default.DocumentScanner, contentDescription = null)
          Spacer(modifier = Modifier.width(8.dp))
          Text("Start Document Scanner", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = Color.White)
        }
      }

      Spacer(modifier = Modifier.height(12.dp))

      // Fallback: Pick existing file
      OutlinedButton(
        onClick = onPickFromGallery,
        shape = RoundedCornerShape(28.dp),
        colors = ButtonDefaults.outlinedButtonColors(contentColor = BrandTealPrimary),
        modifier = Modifier
          .fillMaxWidth()
          .height(48.dp)
          .testTag("scan_gallery_button")
      ) {
        Icon(Icons.Default.FolderOpen, contentDescription = null, modifier = Modifier.size(18.dp))
        Spacer(modifier = Modifier.width(6.dp))
        Text("Select Stored PDF or Image Instead", fontSize = 13.sp, fontWeight = FontWeight.Bold)
      }
    }
  }
}

@Composable
private fun ScannerFeatureRow(text: String) {
  Row(
    verticalAlignment = Alignment.CenterVertically,
    modifier = Modifier.fillMaxWidth()
  ) {
    Icon(
      imageVector = Icons.Default.CheckCircle,
      contentDescription = null,
      tint = BrandTealPrimary,
      modifier = Modifier.size(16.dp)
    )
    Spacer(modifier = Modifier.width(8.dp))
    Text(
      text = text,
      fontSize = 12.sp,
      fontWeight = FontWeight.Medium,
      color = BrandNavyText
    )
  }
}
