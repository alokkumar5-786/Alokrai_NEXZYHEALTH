package com.example.navigation

sealed class Screen(val route: String, val title: String) {
  object Splash : Screen("splash", "NexzyHealth")
  object Welcome : Screen("welcome", "Welcome")
  object Login : Screen("login", "Sign In")
  object EmailSent : Screen("email_sent", "Check Your Email")
  object AuthLoading : Screen("auth_loading", "Verifying Sign-In")
  object PhoneEntry : Screen("phone_entry", "Link Mobile Number")
  object OtpVerification : Screen("otp_verification", "Verify OTP")
  object LanguageSelection : Screen("language_selection", "Select Language") {
    const val routePattern: String = "language_selection?onboarding={onboarding}"
    fun createRoute(onboarding: Boolean = false): String = "language_selection?onboarding=$onboarding"
  }
  object Home : Screen("home", "Home Dashboard")
  object ReportReaderOnboarding : Screen("report_reader_onboarding", "How Report Reader Works")
  object UploadReport : Screen("upload_report", "Upload Report")
  object ScanReport : Screen("scan_report", "Scan Report")
  object UploadProgress : Screen("upload_progress", "Analysing Report")
  object HealthPolicy : Screen("health_policy", "Explain My Health Policy")
  object ExtractionResult : Screen("extraction_result", "Extracted Medical Data")
  object ReportExplanation : Screen("report_explanation", "Plain-Language Explanation")
  object SampleExplanation : Screen("sample_explanation", "Sample Report Explanation")
  object DetailedResults : Screen("detailed_results", "Detailed Test Results")
  object AudioExplanation : Screen("audio_explanation", "Audio Narration")
  object ReportHistory : Screen("report_history", "My Reports")
  object UserProfile : Screen("user_profile", "My Profile")
  object FamilyProfiles : Screen("family_profiles", "Family Profiles")
  object Subscription : Screen("subscription", "Subscription & Credits")
  object Privacy : Screen("privacy", "Privacy & Data Deletion")
  object HelpSupport : Screen("help_support", "Help & Support")
  object Settings : Screen("settings", "Settings")
}
