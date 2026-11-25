package com.example.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.data.MockDataProvider
import android.app.Application
import androidx.compose.ui.platform.LocalContext
import com.example.domain.engine.SafetyFindingEvaluator
import com.example.model.ExplanationConstants
import com.example.model.FamilyMember
import com.example.model.SupportedExplanationLanguage
import com.example.model.UserProfile
import com.example.navigation.Screen
import com.example.ui.auth.AuthState
import com.example.ui.auth.AuthViewModel
import com.example.ui.auth.EmailLinkViewModel
import com.example.ui.auth.EmailLoginViewModel
import com.example.ui.auth.LanguageUpdateState
import com.example.ui.auth.PhoneVerificationViewModel
import java.util.Locale
import com.example.ui.components.NexzyBottomBar
import com.example.ui.explanation.ReportExplanationViewModel
import com.example.ui.screens.AudioExplanationScreen
import com.example.ui.screens.AuthLoadingScreen
import com.example.ui.screens.DetailedResultsScreen
import com.example.ui.screens.EmailLoginScreen
import com.example.ui.screens.EmailSentScreen
import com.example.ui.screens.ExtractionResultScreen
import com.example.ui.screens.FamilyProfilesScreen
import com.example.ui.screens.HelpSupportScreen
import com.example.ui.screens.HomeScreen
import com.example.ui.screens.LanguageScreen
import com.example.ui.screens.OtpVerificationScreen
import com.example.ui.screens.PhoneEntryScreen
import com.example.ui.screens.PrivacyScreen
import com.example.ui.screens.ReportExplanationScreen
import com.example.ui.screens.ReportHistoryScreen
import com.example.ui.screens.SampleExplanationScreen
import com.example.ui.screens.ScanScreen
import com.example.ui.screens.SettingsScreen
import com.example.ui.screens.SplashScreen
import com.example.ui.screens.ReportUploadScreen
import com.example.ui.screens.SubscriptionScreen
import com.example.ui.screens.UploadProgressScreen
import android.content.Intent
import android.widget.Toast
import com.example.model.DocumentExtractionState
import com.example.model.HealthPolicyCopy
import com.example.model.ReportExplanationState
import com.example.ui.components.ReportExitRetentionDialog
import com.example.ui.policy.HealthPolicyViewModel
import com.example.ui.reports.ReportLifecycleViewModel
import com.example.ui.screens.HealthPolicyFlowScreen
import com.example.ui.screens.ReportReaderOnboardingScreen
import com.example.ui.screens.UploadScreen
import com.example.ui.screens.UserProfileScreen
import com.example.ui.screens.WelcomeScreen
import com.example.ui.upload.ReportUploadViewModel

@Composable
fun NexzyApp(
  incomingEmailLink: String? = null,
  onEmailLinkConsumed: () -> Unit = {}
) {
  val navController = rememberNavController()
  val navBackStackEntry by navController.currentBackStackEntryAsState()
  val currentRoute = navBackStackEntry?.destination?.route ?: Screen.Splash.route

  val context = LocalContext.current
  val application = context.applicationContext as Application

  // Authentication ViewModels with explicit Factories
  val authViewModel: AuthViewModel = viewModel(
    factory = AuthViewModel.provideFactory(application)
  )
  val emailLoginViewModel: EmailLoginViewModel = viewModel(
    factory = EmailLoginViewModel.provideFactory(application)
  )
  val emailLinkViewModel: EmailLinkViewModel = viewModel(
    factory = EmailLinkViewModel.provideFactory(application)
  )
  val phoneVerificationViewModel: PhoneVerificationViewModel = viewModel(
    factory = PhoneVerificationViewModel.provideFactory(application)
  )
  val reportUploadViewModel: ReportUploadViewModel = viewModel(
    factory = ReportUploadViewModel.provideFactory(application)
  )
  val reportExplanationViewModel: ReportExplanationViewModel = viewModel(
    factory = ReportExplanationViewModel.provideFactory(application)
  )
  val healthPolicyViewModel: HealthPolicyViewModel = viewModel(
    factory = HealthPolicyViewModel.provideFactory(application)
  )
  val reportLifecycleViewModel: ReportLifecycleViewModel = viewModel(
    factory = ReportLifecycleViewModel.provideFactory(application)
  )

  val uploadStateSnapshot by reportUploadViewModel.uiState.collectAsState()
  val explanationStateSnapshot by reportExplanationViewModel.uiState.collectAsState()
  val lifecycleState by reportLifecycleViewModel.uiState.collectAsState()

  val currentSafetySummary = remember(uploadStateSnapshot.extractionResult) {
    uploadStateSnapshot.extractionResult?.let { SafetyFindingEvaluator.evaluate(it) }
  }
  LaunchedEffect(uploadStateSnapshot.extractionResult) {
    reportExplanationViewModel.syncForExtractionResult(uploadStateSnapshot.extractionResult)
  }

  val authState by authViewModel.authState.collectAsState()
  val isUserAuthenticated = authState is AuthState.Authenticated || authViewModel.currentUser != null
  val currentUserUid = (authState as? AuthState.Authenticated)?.user?.uid ?: authViewModel.currentUser?.uid

  LaunchedEffect(currentUserUid) {
    reportExplanationViewModel.syncUserSession(currentUserUid)
    reportLifecycleViewModel.syncUserSession(currentUserUid)
  }

  LaunchedEffect(uploadStateSnapshot.extractionResult, uploadStateSnapshot.selectedDocument) {
    val extraction = uploadStateSnapshot.extractionResult
    val activeDoc = uploadStateSnapshot.selectedDocument
    if (extraction != null && activeDoc != null && !currentUserUid.isNullOrBlank()) {
      reportLifecycleViewModel.onExtractionCompleted(
        uid = currentUserUid,
        selectedDocument = activeDoc,
        patientLabel = uploadStateSnapshot.selectedFamilyMember?.name ?: "Self",
        selectedCategory = uploadStateSnapshot.selectedCategory,
        extractionResult = extraction
      )
    }
  }

  LaunchedEffect(explanationStateSnapshot) {
    val successExplanation = (explanationStateSnapshot as? ReportExplanationState.Success)?.explanation
    if (successExplanation != null && !currentUserUid.isNullOrBlank()) {
      reportLifecycleViewModel.onExplanationCompleted(
        uid = currentUserUid,
        explanationResult = successExplanation
      )
    }
  }

  LaunchedEffect(lifecycleState.statusBannerMessage) {
    val bannerMsg = lifecycleState.statusBannerMessage
    if (!bannerMsg.isNullOrBlank()) {
      Toast.makeText(context, bannerMsg, Toast.LENGTH_SHORT).show()
      reportLifecycleViewModel.dismissStatusBanner()
    }
  }

  // Guard: when authState transitions to Unauthenticated, automatically evict from authenticated screens
  LaunchedEffect(authState) {
    if (authState is AuthState.Unauthenticated) {
      val authenticatedRoutes = setOf(
        Screen.Home.route,
        Screen.ReportReaderOnboarding.route,
        Screen.ReportHistory.route,
        Screen.UserProfile.route,
        Screen.UploadReport.route,
        Screen.ScanReport.route,
        Screen.UploadProgress.route,
        Screen.HealthPolicy.route,
        Screen.ExtractionResult.route,
        Screen.ReportExplanation.route,
        Screen.DetailedResults.route,
        Screen.AudioExplanation.route,
        Screen.FamilyProfiles.route,
        Screen.Subscription.route,
        Screen.Privacy.route,
        Screen.Settings.route,
        Screen.PhoneEntry.route,
        Screen.OtpVerification.route,
        Screen.LanguageSelection.route
      )
      if (currentRoute in authenticatedRoutes || currentRoute.startsWith(Screen.LanguageSelection.route)) {
        emailLoginViewModel.reset()
        emailLinkViewModel.reset()
        phoneVerificationViewModel.resetAll()
        reportExplanationViewModel.clearSessionAndRevokeConsent(currentUserUid)
        reportLifecycleViewModel.syncUserSession(null)
        reportUploadViewModel.removeSelectedDocument()
        healthPolicyViewModel.deleteDocumentAndReset()
        navController.navigate(Screen.Welcome.route) {
          popUpTo(0) { inclusive = true }
        }
      }
    }
  }

  // Handle incoming deep link from email
  LaunchedEffect(incomingEmailLink) {
    if (!incomingEmailLink.isNullOrBlank()) {
      navController.navigate(Screen.AuthLoading.route)
      emailLinkViewModel.processIncomingLink(incomingEmailLink)
      onEmailLinkConsumed()
    }
  }

  // Single source of truth: authenticated UserProfile.preferredLanguage (default English),
  // never MockDataProvider or transient local state.
  val authenticatedProfile = (authState as? AuthState.Authenticated)?.profile
  val activeLanguageCode = remember(authenticatedProfile?.preferredLanguage) {
    SupportedExplanationLanguage.fromCodeOrName(authenticatedProfile?.preferredLanguage)?.code ?: "en"
  }
  val explanationLanguage = remember(authenticatedProfile?.preferredLanguage) {
    resolveExplanationLanguage(authenticatedProfile?.preferredLanguage)
  }
  val currentLanguageName = explanationLanguage
  val languageUpdateState by authViewModel.languageUpdateState.collectAsState()
  var freeCredits by remember { mutableIntStateOf(3) }
  val persistedHistoryItems = remember(lifecycleState.reports) {
    lifecycleState.reports.map { it.toHistoryItem() }
  }
  val reportsAnalysedCount = persistedHistoryItems.size
  val familyMembersList = remember { mutableStateListOf(*MockDataProvider.sampleFamilyMembers.toTypedArray()) }
  var activeUploadFileName by remember { mutableStateOf("Medical_Report") }
  var pendingExitAction by remember { mutableStateOf("back") }

  val bottomBarScreens = listOf(
    Screen.Home.route,
    Screen.ReportHistory.route,
    Screen.UserProfile.route
  )
  val showBottomBar = currentRoute in bottomBarScreens

  Scaffold(
    modifier = Modifier.fillMaxSize(),
    contentWindowInsets = WindowInsets.navigationBars,
    bottomBar = {
      if (showBottomBar) {
        NexzyBottomBar(
          currentRoute = currentRoute,
          onNavigateToRoute = { route ->
            if (route != currentRoute) {
              navController.navigate(route) {
                popUpTo(Screen.Home.route) { saveState = true }
                launchSingleTop = true
                restoreState = true
              }
            }
          }
        )
      }
    }
  ) { innerPadding ->
    Box(
      modifier = Modifier
        .fillMaxSize()
        .padding(innerPadding)
    ) {
      NavHost(
        navController = navController,
        startDestination = Screen.Splash.route,
        enterTransition = { fadeIn(animationSpec = tween(250)) },
        exitTransition = { fadeOut(animationSpec = tween(200)) }
      ) {
        // 1. Splash
        composable(Screen.Splash.route) {
          SplashScreen(
            isAuthenticated = isUserAuthenticated,
            onNavigateToNext = {
              if (isUserAuthenticated) {
                val nextRoute = PostLoginLanguageRouter.resolveSplashAuthenticatedDestination(authState)
                navController.navigate(nextRoute) {
                  popUpTo(Screen.Splash.route) { inclusive = true }
                }
              } else {
                navController.navigate(Screen.Welcome.route) {
                  popUpTo(Screen.Splash.route) { inclusive = true }
                }
              }
            }
          )
        }

        // 2. Welcome
        composable(Screen.Welcome.route) {
          WelcomeScreen(
            onGetStarted = { navController.navigate(Screen.SampleExplanation.route) },
            onSignInClick = { navController.navigate(Screen.Login.route) }
          )
        }

        // 3. Email Login (Passwordless)
        composable(Screen.Login.route) {
          EmailLoginScreen(
            viewModel = emailLoginViewModel,
            onNavigateToEmailSent = { sentEmail ->
              navController.navigate(Screen.EmailSent.route)
            },
            onNavigateBack = { navController.popBackStack() }
          )
        }

        // 4. Email Sent Screen
        composable(Screen.EmailSent.route) {
          EmailSentScreen(
            viewModel = emailLoginViewModel,
            onNavigateBackToEmail = {
              emailLoginViewModel.reset()
              navController.popBackStack()
            }
          )
        }

        // 5. Auth Loading Screen (Verifying Incoming Email Link)
        composable(Screen.AuthLoading.route) {
          AuthLoadingScreen(
            viewModel = emailLinkViewModel,
            onAuthSuccess = {
              emailLoginViewModel.reset()
              val syncedUser = emailLinkViewModel.uiState.value.authenticatedUser
              val syncedProfile = emailLinkViewModel.uiState.value.userProfile
              authViewModel.syncAuthenticatedSession(syncedUser, syncedProfile)
              val nextRoute = PostLoginLanguageRouter.resolveInitialAuthenticatedDestination(syncedProfile)
              navController.navigate(nextRoute) {
                popUpTo(0) { inclusive = true }
              }
            },
            onRequestNewLink = {
              navController.navigate(Screen.Login.route) {
                popUpTo(Screen.Welcome.route)
              }
            }
          )
        }

        // 5b. Phone Entry Screen (Day 2C)
        composable(Screen.PhoneEntry.route) {
          PhoneEntryScreen(
            viewModel = phoneVerificationViewModel,
            onNavigateToOtp = {
              navController.navigate(Screen.OtpVerification.route)
            },
            onSkipClick = {
              navController.navigate(Screen.Home.route) {
                popUpTo(Screen.PhoneEntry.route) { inclusive = true }
              }
            },
            onNavigateBack = {
              if (navController.previousBackStackEntry != null) {
                navController.popBackStack()
              } else {
                navController.navigate(Screen.Home.route) {
                  popUpTo(Screen.PhoneEntry.route) { inclusive = true }
                }
              }
            }
          )
        }

        // 5c. OTP Verification Screen (Day 2C)
        composable(Screen.OtpVerification.route) {
          OtpVerificationScreen(
            viewModel = phoneVerificationViewModel,
            onVerificationSuccess = { verifiedPhone ->
              authViewModel.checkSession()
              navController.navigate(Screen.Home.route) {
                popUpTo(Screen.PhoneEntry.route) { inclusive = true }
              }
            },
            onChangeNumberClick = {
              phoneVerificationViewModel.resetToPhoneEntry()
              navController.popBackStack()
            }
          )
        }

        // 6. Language Selection (Post-login onboarding & Settings/Profile/Home edits)
        composable(
          route = Screen.LanguageSelection.routePattern,
          arguments = listOf(
            navArgument("onboarding") {
              type = NavType.BoolType
              defaultValue = false
            }
          )
        ) { backStackEntry ->
          val isOnboarding = backStackEntry.arguments?.getBoolean("onboarding") ?: false
          var draftLanguageCode by remember(activeLanguageCode) {
            mutableStateOf(activeLanguageCode)
          }

          LaunchedEffect(Unit) {
            authViewModel.resetLanguageUpdateState()
          }

          LaunchedEffect(languageUpdateState) {
            if (languageUpdateState is LanguageUpdateState.Success) {
              authViewModel.resetLanguageUpdateState()
              if (isOnboarding) {
                val nextRoute = PostLoginLanguageRouter.resolvePostLanguageOnboardingDestination(authenticatedProfile)
                navController.navigate(nextRoute) {
                  popUpTo(0) { inclusive = true }
                }
              } else if (navController.previousBackStackEntry != null) {
                navController.popBackStack()
              } else {
                navController.navigate(Screen.Home.route) {
                  popUpTo(0) { inclusive = true }
                }
              }
            }
          }

          LanguageScreen(
            languages = MockDataProvider.sampleLanguages,
            selectedLanguageCode = draftLanguageCode,
            onLanguageSelected = { selectedCode ->
              draftLanguageCode = selectedCode
              if (languageUpdateState is LanguageUpdateState.Error) {
                authViewModel.resetLanguageUpdateState()
              }
            },
            onConfirm = {
              authViewModel.updatePreferredLanguage(draftLanguageCode)
            },
            onNavigateBack = {
              if (!isOnboarding && languageUpdateState !is LanguageUpdateState.Saving) {
                authViewModel.resetLanguageUpdateState()
                navController.popBackStack()
              }
            },
            isOnboarding = isOnboarding,
            isSaving = languageUpdateState is LanguageUpdateState.Saving,
            errorMessage = (languageUpdateState as? LanguageUpdateState.Error)?.message
          )
        }

        // 7. Home Dashboard
        composable(Screen.Home.route) {
          HomeScreen(
            userName = authViewModel.currentUser?.email?.substringBefore("@")?.replaceFirstChar { it.uppercase() } ?: "Priya",
            selectedLanguageName = currentLanguageName,
            reportsAnalysedCount = reportsAnalysedCount,
            freeCreditsRemaining = freeCredits,
            recentReports = persistedHistoryItems.take(3),
            onScanReportClick = { navController.navigate(Screen.ScanReport.route) },
            onUploadReportClick = { navController.navigate(Screen.UploadReport.route) },
            onSampleExplanationClick = { navController.navigate(Screen.SampleExplanation.route) },
            onLanguageClick = { navController.navigate(Screen.LanguageSelection.createRoute(onboarding = false)) },
            onHelpClick = { navController.navigate(Screen.HelpSupport.route) },
            onViewAllReportsClick = { navController.navigate(Screen.ReportHistory.route) },
            onReportItemClick = { report ->
              if (report.isDemo) {
                navController.navigate(Screen.SampleExplanation.route)
              } else {
                reportLifecycleViewModel.openPersistedReport(
                  reportId = report.id,
                  preferredLanguageCode = activeLanguageCode,
                  onLoaded = { detail ->
                    val extraction = detail.extractionResult ?: return@openPersistedReport
                    val matchedExp = detail.explanationsByLanguage[activeLanguageCode]
                      ?: detail.explanationsByLanguage.values.firstOrNull()
                    reportUploadViewModel.loadPersistedExtractionForViewing(
                      extractionResult = extraction,
                      category = detail.report.selectedCategory
                    )
                    reportExplanationViewModel.loadPersistedExplanationForViewing(
                      extractionResult = extraction,
                      explanation = matchedExp
                    )
                    navController.navigate(Screen.ExtractionResult.route) {
                      launchSingleTop = true
                    }
                  },
                  onUnavailable = { message ->
                    Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
                  }
                )
              }
            },
            onSubscriptionClick = { navController.navigate(Screen.Subscription.route) },
            onExplainHealthPolicyClick = {
              healthPolicyViewModel.startFlow(activeLanguageCode)
              navController.navigate(Screen.HealthPolicy.route) {
                launchSingleTop = true
              }
            },
            onReportReaderGuideClick = {
              navController.navigate(Screen.ReportReaderOnboarding.route) {
                launchSingleTop = true
              }
            },
            pendingDeletionCount = lifecycleState.pendingDeletions.size,
            onRetryPendingDeletionsClick = { reportLifecycleViewModel.retryPendingDeletions() },
            onDeleteReportClick = { report -> reportLifecycleViewModel.deleteReportById(report.id) }
          )
        }

        // 7a. Medical Report Reader Onboarding Carousel (Accessible from Home)
        composable(Screen.ReportReaderOnboarding.route) {
          ReportReaderOnboardingScreen(
            onNavigateBack = { navController.popBackStack() },
            onStartUploadClick = {
              navController.navigate(Screen.UploadReport.route) {
                popUpTo(Screen.ReportReaderOnboarding.route) { inclusive = true }
                launchSingleTop = true
              }
            },
            onStartScanClick = {
              navController.navigate(Screen.ScanReport.route) {
                popUpTo(Screen.ReportReaderOnboarding.route) { inclusive = true }
                launchSingleTop = true
              }
            },
            onTryDemoClick = {
              navController.navigate(Screen.SampleExplanation.route) {
                popUpTo(Screen.ReportReaderOnboarding.route) { inclusive = true }
                launchSingleTop = true
              }
            }
          )
        }

        // 7b. Explain My Health Policy (Ephemeral 4-step flow)
        composable(Screen.HealthPolicy.route) {
          val policyState by healthPolicyViewModel.uiState.collectAsState()
          HealthPolicyFlowScreen(
            uiState = policyState,
            onSelectLanguage = { code -> healthPolicyViewModel.selectLanguage(code) },
            onConfirmLanguage = { healthPolicyViewModel.confirmLanguageAndProceed() },
            onBackToLanguage = { healthPolicyViewModel.goBackToLanguageStep() },
            onFilesPicked = { uris -> healthPolicyViewModel.onFilesSelected(uris) },
            onRemoveAttachment = { id -> healthPolicyViewModel.removeAttachment(id) },
            onPastedTextChanged = { text -> healthPolicyViewModel.onPastedTextChanged(text) },
            onDismissError = { healthPolicyViewModel.clearError() },
            onAnalyzePolicy = { healthPolicyViewModel.analyzePolicy() },
            onCancelLoading = { healthPolicyViewModel.cancelAnalysis() },
            onShareSummary = { summaryText ->
              val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_SUBJECT, "NexzyHealth — Explain My Health Policy")
                putExtra(Intent.EXTRA_TEXT, summaryText)
              }
              context.startActivity(
                Intent.createChooser(shareIntent, "Share summary")
              )
            },
            onDeleteDocumentNow = {
              val deletedMsg = HealthPolicyCopy.forLanguage(policyState.selectedLanguageCode).deletedToastMessage
              healthPolicyViewModel.deleteDocumentAndReset()
              Toast.makeText(context, deletedMsg, Toast.LENGTH_SHORT).show()
            },
            onExitToHome = {
              healthPolicyViewModel.deleteDocumentAndReset()
              navController.popBackStack(Screen.Home.route, inclusive = false)
            },
            buildShareText = { healthPolicyViewModel.buildShareSummary() }
          )
        }

        // 8. Upload Medical Report
        composable(Screen.UploadReport.route) {
          ReportUploadScreen(
            viewModel = reportUploadViewModel,
            familyMembers = familyMembersList,
            onStartUploadAndExplain = { fileName, _ ->
              activeUploadFileName = fileName
              navController.navigate(Screen.UploadProgress.route) {
                launchSingleTop = true
              }
            },
            onNavigateToScan = { navController.navigate(Screen.ScanReport.route) },
            onNavigateBack = { navController.popBackStack() }
          )
        }

        // 9. Scan Medical Report
        composable(Screen.ScanReport.route) {
          ScanScreen(
            onCaptureCompleted = { scannedUri ->
              if (scannedUri != null) {
                reportUploadViewModel.onDocumentSelected(scannedUri)
                navController.navigate(Screen.UploadReport.route) {
                  popUpTo(Screen.ScanReport.route) { inclusive = true }
                }
              }
            },
            onNavigateBack = { navController.popBackStack() },
            onPickFromGallery = { navController.navigate(Screen.UploadReport.route) }
          )
        }

        // 10. Upload Progress (Real Extraction State)
        composable(Screen.UploadProgress.route) {
          val uploadState by reportUploadViewModel.uiState.collectAsState()
          BackHandler {
            reportUploadViewModel.cancelActiveExtraction()
            navController.popBackStack()
          }
          UploadProgressScreen(
            fileName = uploadState.selectedDocument?.fileName ?: activeUploadFileName,
            extractionState = uploadState.extractionState,
            onExtractionSucceeded = {
              navController.navigate(Screen.ExtractionResult.route) {
                popUpTo(Screen.UploadProgress.route) { inclusive = true }
                launchSingleTop = true
              }
            },
            onInspectUnidentified = {
              navController.navigate(Screen.ExtractionResult.route) {
                popUpTo(Screen.UploadProgress.route) { inclusive = true }
                launchSingleTop = true
              }
            },
            onRetryClick = { reportUploadViewModel.retryExtraction() },
            onCancelClick = {
              reportUploadViewModel.cancelActiveExtraction()
              navController.popBackStack()
            }
          )
        }

        // 10b. Real Extracted Medical Data Result Screen
        composable(Screen.ExtractionResult.route) {
          val uploadState by reportUploadViewModel.uiState.collectAsState()
          val performLeaveAction: (String) -> Unit = { action ->
            reportExplanationViewModel.clear()
            reportUploadViewModel.removeSelectedDocument()
            reportLifecycleViewModel.clearActiveSessionState()
            if (action == "upload_another") {
              navController.navigate(Screen.UploadReport.route) {
                popUpTo(Screen.Home.route)
              }
            } else {
              navController.popBackStack()
            }
          }

          BackHandler {
            pendingExitAction = "back"
            reportLifecycleViewModel.requestExitReportSession {
              performLeaveAction("back")
            }
          }

          ExtractionResultScreen(
            extractionResult = uploadState.extractionResult,
            fileName = lifecycleState.activeSession.report?.title ?: uploadState.selectedDocument?.fileName,
            patientName = lifecycleState.activeSession.report?.patientLabel ?: uploadState.selectedFamilyMember?.name,
            onUploadAnotherClick = {
              pendingExitAction = "upload_another"
              reportLifecycleViewModel.requestExitReportSession {
                performLeaveAction("upload_another")
              }
            },
            onNavigateBack = {
              pendingExitAction = "back"
              reportLifecycleViewModel.requestExitReportSession {
                performLeaveAction("back")
              }
            },
            onExplainReportClick = {
              val loadedExplanation = lifecycleState.activeSession.explanationResult
              if (loadedExplanation != null) {
                reportExplanationViewModel.loadPersistedExplanationForViewing(
                  extractionResult = uploadState.extractionResult,
                  explanation = loadedExplanation
                )
              } else {
                reportExplanationViewModel.requestExplanation(
                  extractionResult = uploadState.extractionResult,
                  language = explanationLanguage
                )
              }
              navController.navigate(Screen.ReportExplanation.route) {
                launchSingleTop = true
              }
            },
            safetySummary = currentSafetySummary,
            persistedReport = lifecycleState.activeSession.report,
            persistenceSyncStatus = lifecycleState.activeSession.syncStatus,
            onKeepReportClick = { reportLifecycleViewModel.keepActiveReport() },
            onDeleteReportClick = {
              reportLifecycleViewModel.deleteActiveReport(
                localUriToRelease = uploadState.selectedDocument?.uriString
              ) {
                performLeaveAction("back")
              }
            }
          )

          if (lifecycleState.isExitRetentionPromptVisible) {
            ReportExitRetentionDialog(
              expiresAtMillis = lifecycleState.activeSession.report?.expiresAtMillis,
              onKeepAndExit = {
                reportLifecycleViewModel.keepActiveReport()
                performLeaveAction(pendingExitAction)
              },
              onDeleteAndLeave = {
                reportLifecycleViewModel.dismissExitPromptAndDeleteByDefault(
                  localUriToRelease = uploadState.selectedDocument?.uriString
                ) {
                  performLeaveAction(pendingExitAction)
                }
              }
            )
          }
        }

        // 10c. Category-Specific Plain-Language Explanation Screen (Task 3D)
        composable(Screen.ReportExplanation.route) {
          val uploadState by reportUploadViewModel.uiState.collectAsState()
          val explanationState by reportExplanationViewModel.uiState.collectAsState()
          val isConsentDialogVisible by reportExplanationViewModel.isConsentDialogVisible.collectAsState()
          ReportExplanationScreen(
            explanationState = explanationState,
            isConsentDialogVisible = isConsentDialogVisible,
            fileName = lifecycleState.activeSession.report?.title ?: uploadState.selectedDocument?.fileName,
            patientName = lifecycleState.activeSession.report?.patientLabel ?: uploadState.selectedFamilyMember?.name,
            onConfirmConsent = {
              reportExplanationViewModel.confirmConsentAndExplain(
                extractionResult = uploadState.extractionResult,
                language = explanationLanguage
              )
            },
            onDismissConsent = {
              reportExplanationViewModel.dismissConsentDialog()
            },
            onRequestExplanation = {
              reportExplanationViewModel.requestExplanation(
                extractionResult = uploadState.extractionResult,
                language = explanationLanguage
              )
            },
            onRetryClick = {
              reportExplanationViewModel.retryExplanation(
                extractionResult = uploadState.extractionResult,
                language = explanationLanguage
              )
            },
            onNavigateBack = { navController.popBackStack() },
            safetySummary = currentSafetySummary,
            persistedReport = lifecycleState.activeSession.report,
            persistenceSyncStatus = lifecycleState.activeSession.syncStatus,
            onKeepReportClick = { reportLifecycleViewModel.keepActiveReport() },
            onDeleteReportClick = {
              reportLifecycleViewModel.deleteActiveReport(
                localUriToRelease = uploadState.selectedDocument?.uriString
              ) {
                reportExplanationViewModel.clear()
                reportUploadViewModel.removeSelectedDocument()
                reportLifecycleViewModel.clearActiveSessionState()
                navController.popBackStack(Screen.Home.route, inclusive = false)
              }
            }
          )
        }

        // 11. Sample Report Explanation (Explicit Demo Showcase Only)
        composable(Screen.SampleExplanation.route) {
          SampleExplanationScreen(
            reportData = MockDataProvider.sampleReport,
            onNavigateToDetailedResults = { navController.navigate(Screen.DetailedResults.route) },
            onNavigateToAudioScreen = { navController.navigate(Screen.AudioExplanation.route) },
            onNavigateBack = { navController.popBackStack() }
          )
        }

        // 12. Detailed Test Results
        composable(Screen.DetailedResults.route) {
          DetailedResultsScreen(
            parameters = MockDataProvider.sampleParametersList,
            onNavigateBack = { navController.popBackStack() }
          )
        }

        // 13. Audio Explanation
        composable(Screen.AudioExplanation.route) {
          AudioExplanationScreen(
            transcript = MockDataProvider.sampleReport.audioTranscript,
            onNavigateBack = { navController.popBackStack() }
          )
        }

        // 14. Report History
        composable(Screen.ReportHistory.route) {
          ReportHistoryScreen(
            reports = persistedHistoryItems,
            onReportClick = { report ->
              if (report.isDemo) {
                navController.navigate(Screen.SampleExplanation.route)
              } else {
                reportLifecycleViewModel.openPersistedReport(
                  reportId = report.id,
                  preferredLanguageCode = activeLanguageCode,
                  onLoaded = { detail ->
                    val extraction = detail.extractionResult ?: return@openPersistedReport
                    val matchedExp = detail.explanationsByLanguage[activeLanguageCode]
                      ?: detail.explanationsByLanguage.values.firstOrNull()
                    reportUploadViewModel.loadPersistedExtractionForViewing(
                      extractionResult = extraction,
                      category = detail.report.selectedCategory
                    )
                    reportExplanationViewModel.loadPersistedExplanationForViewing(
                      extractionResult = extraction,
                      explanation = matchedExp
                    )
                    navController.navigate(Screen.ExtractionResult.route) {
                      launchSingleTop = true
                    }
                  },
                  onUnavailable = { message ->
                    Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
                  }
                )
              }
            },
            onNavigateBack = { navController.popBackStack() },
            pendingDeletionCount = lifecycleState.pendingDeletions.size,
            onRetryPendingDeletionsClick = { reportLifecycleViewModel.retryPendingDeletions() },
            onDeleteReportClick = { report -> reportLifecycleViewModel.deleteReportById(report.id) }
          )
        }

        // 15. User Profile
        composable(Screen.UserProfile.route) {
          val authState by authViewModel.authState.collectAsState()
          val profile = (authState as? AuthState.Authenticated)?.profile
          val activeEmail = profile?.email?.ifBlank { null }
            ?: authViewModel.currentUser?.email
            ?: "priya.caregiver@example.com"
          val activeDisplayName = profile?.displayName?.ifBlank { null }
            ?: authViewModel.currentUser?.displayName?.ifBlank { null }
            ?: activeEmail.substringBefore("@").replaceFirstChar { it.uppercase() }

          UserProfileScreen(
            userName = activeDisplayName,
            userEmail = activeEmail,
            userPhone = profile?.phoneNumber ?: "",
            isPhoneVerified = profile?.phoneVerified ?: false,
            selectedLanguage = currentLanguageName,
            reportsAnalysed = reportsAnalysedCount,
            freeCredits = freeCredits,
            onNavigateToFamilyProfiles = { navController.navigate(Screen.FamilyProfiles.route) },
            onNavigateToSubscription = { navController.navigate(Screen.Subscription.route) },
            onNavigateToPrivacy = { navController.navigate(Screen.Privacy.route) },
            onNavigateToHelp = { navController.navigate(Screen.HelpSupport.route) },
            onNavigateToSettings = { navController.navigate(Screen.Settings.route) },
            onNavigateToLanguage = { navController.navigate(Screen.LanguageSelection.createRoute(onboarding = false)) },
            onNavigateToPhoneVerification = { navController.navigate(Screen.PhoneEntry.route) },
            onLogoutClick = {
              reportExplanationViewModel.clearSessionAndRevokeConsent(currentUserUid)
              reportLifecycleViewModel.syncUserSession(null)
              authViewModel.signOut()
              emailLoginViewModel.reset()
              emailLinkViewModel.reset()
              phoneVerificationViewModel.resetAll()
              reportUploadViewModel.removeSelectedDocument()
              healthPolicyViewModel.deleteDocumentAndReset()
              navController.navigate(Screen.Welcome.route) {
                popUpTo(0) { inclusive = true }
              }
            }
          )
        }

        // 16. Family Profiles
        composable(Screen.FamilyProfiles.route) {
          FamilyProfilesScreen(
            familyMembers = familyMembersList,
            onAddMember = { member -> familyMembersList.add(member) },
            onNavigateBack = { navController.popBackStack() }
          )
        }

        // 17. Subscription & Free-Credit
        composable(Screen.Subscription.route) {
          SubscriptionScreen(
            freeCreditsRemaining = freeCredits,
            plans = MockDataProvider.subscriptionPlans,
            onNavigateBack = { navController.popBackStack() }
          )
        }

        // 18. Privacy & Data Deletion
        composable(Screen.Privacy.route) {
          PrivacyScreen(
            onNavigateBack = { navController.popBackStack() },
            onAccountDeleted = {
              reportExplanationViewModel.clearSessionAndRevokeConsent(currentUserUid)
              reportLifecycleViewModel.syncUserSession(null)
              authViewModel.signOut()
              emailLoginViewModel.reset()
              emailLinkViewModel.reset()
              phoneVerificationViewModel.resetAll()
              reportUploadViewModel.removeSelectedDocument()
              healthPolicyViewModel.deleteDocumentAndReset()
              navController.navigate(Screen.Welcome.route) {
                popUpTo(0) { inclusive = true }
              }
            }
          )
        }

        // 19. Help & Support
        composable(Screen.HelpSupport.route) {
          HelpSupportScreen(
            faqs = MockDataProvider.sampleFaqs,
            onNavigateBack = { navController.popBackStack() }
          )
        }

        // 20. Settings
        composable(Screen.Settings.route) {
          SettingsScreen(
            selectedLanguage = currentLanguageName,
            onNavigateToLanguage = { navController.navigate(Screen.LanguageSelection.createRoute(onboarding = false)) },
            onNavigateBack = { navController.popBackStack() }
          )
        }
      }
    }
  }
}

internal object PostLoginLanguageRouter {
  private val LEGACY_EXPLICIT_NON_ENGLISH_CODES = setOf("hi", "bn", "mr")

  fun hasCompletedLanguageSelection(profile: UserProfile?): Boolean {
    if (profile == null) return false
    if (profile.preferredLanguageSelected) return true
    val normalized = profile.preferredLanguage.trim().lowercase(Locale.ROOT)
    return normalized in LEGACY_EXPLICIT_NON_ENGLISH_CODES
  }

  fun requiresPostLoginLanguageSelection(profile: UserProfile?): Boolean =
    !hasCompletedLanguageSelection(profile)

  fun resolveInitialAuthenticatedDestination(profile: UserProfile?): String {
    return if (requiresPostLoginLanguageSelection(profile)) {
      Screen.LanguageSelection.createRoute(onboarding = true)
    } else if (profile?.phoneVerified == true) {
      Screen.Home.route
    } else {
      Screen.PhoneEntry.route
    }
  }

  fun resolvePostLanguageOnboardingDestination(profile: UserProfile?): String {
    return if (profile?.phoneVerified == true) {
      Screen.Home.route
    } else {
      Screen.PhoneEntry.route
    }
  }

  fun resolveSplashAuthenticatedDestination(authState: AuthState): String {
    val profile = (authState as? AuthState.Authenticated)?.profile
    return if (profile != null && requiresPostLoginLanguageSelection(profile)) {
      Screen.LanguageSelection.createRoute(onboarding = true)
    } else {
      Screen.Home.route
    }
  }
}

internal fun resolveExplanationLanguage(preferredLanguageCode: String?): String =
  ExplanationConstants.resolveSafeExplanationLanguage(preferredLanguageCode)
