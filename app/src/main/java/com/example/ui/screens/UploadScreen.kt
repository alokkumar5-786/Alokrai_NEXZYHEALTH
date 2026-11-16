package com.example.ui.screens

import android.app.Application
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.model.FamilyMember
import com.example.ui.upload.ReportUploadViewModel

/**
 * Backward compatibility wrapper delegating to [ReportUploadScreen].
 */
@Composable
fun UploadScreen(
  familyMembers: List<FamilyMember>,
  onStartUploadAndExplain: (fileName: String, person: String) -> Unit,
  onNavigateToScan: () -> Unit,
  onNavigateBack: () -> Unit,
  modifier: Modifier = Modifier
) {
  val context = LocalContext.current
  val application = context.applicationContext as Application
  val uploadViewModel: ReportUploadViewModel = viewModel(
    factory = ReportUploadViewModel.provideFactory(application)
  )

  ReportUploadScreen(
    viewModel = uploadViewModel,
    familyMembers = familyMembers,
    onStartUploadAndExplain = onStartUploadAndExplain,
    onNavigateToScan = onNavigateToScan,
    onNavigateBack = onNavigateBack,
    modifier = modifier
  )
}
