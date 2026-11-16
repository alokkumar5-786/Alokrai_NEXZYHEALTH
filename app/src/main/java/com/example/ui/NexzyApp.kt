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
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.data.MockDataProvider
import android.app.Application
import androidx.compose.ui.platform.LocalContext
import com.example.model.ExplanationConstants
import com.example.model.FamilyMember
import com.example.navigation.Screen
import com.example.ui.auth.AuthState
import com.example.ui.auth.AuthViewModel
import com.example.ui.auth.EmailLinkViewModel
import com.example.ui.auth.EmailLoginViewModel
import com.example.ui.auth.PhoneVerificationViewModel
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

  val uploadStateSnapshot by reportUploadViewModel.uiState.collectAsState()
  LaunchedEffect(uploadStateSnapshot.extractionResult) {
    reportExplanationViewModel.syncForExtractionResult(uploadStateSnapshot.extractionResult)
  }

  val authState by authViewModel.authState.collectAsState()
  val isUserAuthenticated = authState is AuthState.Authenticated || authViewModel.currentUser != null
  val currentUserUid = (authState as? AuthState.Authenticated)?.user?.uid ?: authViewModel.currentUser?.uid

  LaunchedEffect(currentUserUid) {
    reportExplanationViewModel.syncUserSession(currentUserUid)
  }

  // Guard: when authState transitions to Unauthenticated, automatically evict from authenticated screens
  LaunchedEffect(authState) {
    if (authState is AuthState.Unauthenticated) {
      val authenticatedRoutes = setOf(
        Screen.Home.route,
        Screen.ReportHistory.route,
        Screen.UserProfile.route,
        Screen.UploadReport.route,
        Screen.ScanReport.route,
        Screen.UploadProgress.route,
        Screen.ExtractionResult.route,
        Screen.ReportExplanation.route,
        Screen.DetailedResults.route,
        Screen.AudioExplanation.route,
        Screen.FamilyProfiles.route,
        Screen.Subscription.route,
        Screen.Privacy.route,
        Screen.Settings.route,
        Screen.PhoneEntry.route,
        Screen.OtpVerification.route
      )
      if (currentRoute in authenticatedRoutes) {
        emailLoginViewModel.reset()
        emailLinkViewModel.reset()
        phoneVerificationViewModel.resetAll()
        reportExplanationViewModel.clearSessionAndRevokeConsent(currentUserUid)
        reportUploadViewModel.removeSelectedDocument()
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

  // Production explanation language comes from authenticated UserProfile.preferredLanguage (default English),
  // never from MockDataProvider.sampleLanguages.
  val authenticatedProfile = (authState as? AuthState.Authenticated)?.profile
  val explanationLanguage = remember(authenticatedProfile?.preferredLanguage) {
    resolveExplanationLanguage(authenticatedProfile?.preferredLanguage)
  }

  // Caregiver App State
  var selectedLanguageCode by remember { mutableStateOf("en") }
  val currentLanguageName = remember(selectedLanguageCode) {
    MockDataProvider.sampleLanguages.firstOrNull { it.code == selectedLanguageCode }?.englishName ?: "English"
  }
  var freeCredits by remember { mutableIntStateOf(3) }
  var reportsAnalysed by remember { mutableIntStateOf(14) }
  val familyMembersList = remember { mutableStateListOf(*MockDataProvider.sampleFamilyMembers.toTypedArray()) }
  var activeUploadFileName by remember { mutableStateOf("CBC_Report_ApexLab.pdf") }

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
                navController.navigate(Screen.Home.route) {
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
              authViewModel.checkSession()
              val profile = emailLinkViewModel.uiState.value.userProfile
              if (profile?.phoneVerified == true) {
                navController.navigate(Screen.Home.route) {
                  popUpTo(Screen.Welcome.route) { inclusive = true }
                }
              } else {
                navController.navigate(Screen.PhoneEntry.route) {
                  popUpTo(Screen.Welcome.route) { inclusive = true }
                }
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

        // 6. Language Selection
        composable(Screen.LanguageSelection.route) {
          LanguageScreen(
            languages = MockDataProvider.sampleLanguages,
            selectedLanguageCode = selectedLanguageCode,
            onLanguageSelected = { selectedLanguageCode = it },
            onConfirm = {
              if (navController.previousBackStackEntry != null) {
                navController.popBackStack()
              } else {
                navController.navigate(Screen.Home.route) {
                  popUpTo(Screen.Splash.route) { inclusive = true }
                }
              }
            },
            onNavigateBack = { navController.popBackStack() }
          )
        }

        // 7. Home Dashboard
        composable(Screen.Home.route) {
          HomeScreen(
            userName = authViewModel.currentUser?.email?.substringBefore("@")?.replaceFirstChar { it.uppercase() } ?: "Priya",
            selectedLanguageName = currentLanguageName,
            reportsAnalysedCount = reportsAnalysed,
            freeCreditsRemaining = freeCredits,
            recentReports = MockDataProvider.sampleHistory,
            onScanReportClick = { navController.navigate(Screen.ScanReport.route) },
            onUploadReportClick = { navController.navigate(Screen.UploadReport.route) },
            onSampleExplanationClick = { navController.navigate(Screen.SampleExplanation.route) },
            onLanguageClick = { navController.navigate(Screen.LanguageSelection.route) },
            onHelpClick = { navController.navigate(Screen.HelpSupport.route) },
            onViewAllReportsClick = { navController.navigate(Screen.ReportHistory.route) },
            onReportItemClick = { report ->
              navController.navigate(Screen.SampleExplanation.route)
            },
            onSubscriptionClick = { navController.navigate(Screen.Subscription.route) }
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
              reportsAnalysed += 1
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
          ExtractionResultScreen(
            extractionResult = uploadState.extractionResult,
            fileName = uploadState.selectedDocument?.fileName,
            patientName = uploadState.selectedFamilyMember?.name,
            onUploadAnotherClick = {
              reportExplanationViewModel.clear()
              reportUploadViewModel.removeSelectedDocument()
              navController.navigate(Screen.UploadReport.route) {
                popUpTo(Screen.Home.route)
              }
            },
            onNavigateBack = { navController.popBackStack() },
            onExplainReportClick = {
              reportExplanationViewModel.requestExplanation(
                extractionResult = uploadState.extractionResult,
                language = explanationLanguage
              )
              navController.navigate(Screen.ReportExplanation.route) {
                launchSingleTop = true
              }
            }
          )
        }

        // 10c. Category-Specific Plain-Language Explanation Screen (Task 3D)
        composable(Screen.ReportExplanation.route) {
          val uploadState by reportUploadViewModel.uiState.collectAsState()
          val explanationState by reportExplanationViewModel.uiState.collectAsState()
          val isConsentDialogVisible by reportExplanationViewModel.isConsentDialogVisible.collectAsState()
          ReportExplanationScreen(
            explanationState = explanationState,
            isConsentDialogVisible = isConsentDialogVisible,
            fileName = uploadState.selectedDocument?.fileName,
            patientName = uploadState.selectedFamilyMember?.name,
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
            onNavigateBack = { navController.popBackStack() }
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
            reports = MockDataProvider.sampleHistory,
            onReportClick = { report -> navController.navigate(Screen.SampleExplanation.route) },
            onNavigateBack = { navController.popBackStack() }
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
            reportsAnalysed = reportsAnalysed,
            freeCredits = freeCredits,
            onNavigateToFamilyProfiles = { navController.navigate(Screen.FamilyProfiles.route) },
            onNavigateToSubscription = { navController.navigate(Screen.Subscription.route) },
            onNavigateToPrivacy = { navController.navigate(Screen.Privacy.route) },
            onNavigateToHelp = { navController.navigate(Screen.HelpSupport.route) },
            onNavigateToSettings = { navController.navigate(Screen.Settings.route) },
            onNavigateToLanguage = { navController.navigate(Screen.LanguageSelection.route) },
            onNavigateToPhoneVerification = { navController.navigate(Screen.PhoneEntry.route) },
            onLogoutClick = {
              reportExplanationViewModel.clearSessionAndRevokeConsent(currentUserUid)
              authViewModel.signOut()
              emailLoginViewModel.reset()
              emailLinkViewModel.reset()
              phoneVerificationViewModel.resetAll()
              reportUploadViewModel.removeSelectedDocument()
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
              authViewModel.signOut()
              emailLoginViewModel.reset()
              emailLinkViewModel.reset()
              phoneVerificationViewModel.resetAll()
              reportUploadViewModel.removeSelectedDocument()
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
            onNavigateToLanguage = { navController.navigate(Screen.LanguageSelection.route) },
            onNavigateBack = { navController.popBackStack() }
          )
        }
      }
    }
  }
}

internal fun resolveExplanationLanguage(preferredLanguageCode: String?): String =
  ExplanationConstants.resolveSafeExplanationLanguage(preferredLanguageCode)
