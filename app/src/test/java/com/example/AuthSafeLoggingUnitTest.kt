package com.example

import android.app.Activity
import android.app.Application
import android.content.Context
import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import com.example.domain.repository.AuthRepository
import com.example.domain.repository.PhoneVerificationCallbacks
import com.example.domain.repository.UserRepository
import com.example.firebase.AppCheckHelper
import com.example.firebase.FirebaseManager
import com.example.model.UserProfile
import com.example.ui.auth.AuthState
import com.example.ui.auth.AuthViewModel
import com.example.ui.auth.EmailLinkViewModel
import com.example.ui.auth.PhoneVerificationViewModel
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.PhoneAuthCredential
import com.google.firebase.auth.PhoneAuthProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowLog

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class AuthSafeLoggingUnitTest {

  private lateinit var application: Application
  private val testDispatcher = StandardTestDispatcher()

  @Before
  fun setUp() {
    Dispatchers.setMain(testDispatcher)
    application = ApplicationProvider.getApplicationContext()
    if (FirebaseApp.getApps(application).isEmpty()) {
      FirebaseApp.initializeApp(application)
    }
    ShadowLog.clear()
  }

  @After
  fun tearDown() {
    val apps = FirebaseApp.getApps(application)
    if (apps.isNotEmpty()) {
      val persistenceKey = apps.first().persistenceKey
      application.getSharedPreferences(
        "com.google.firebase.appcheck.debug.store.$persistenceKey",
        Context.MODE_PRIVATE
      ).edit().clear().commit()
    }
    ShadowLog.clear()
    Dispatchers.resetMain()
  }

  private fun allLogMessages(): String =
    ShadowLog.getLogs().joinToString("\n") { "${it.tag}: ${it.msg} ${it.throwable ?: ""}" }

  @Test
  fun `email link viewmodel logs safe events without links oobCode email or exception details`() = runTest(testDispatcher) {
    val secretEmail = "patient.secret@example.com"
    val secretOob = "SECRET_OOB_CODE_987654"
    val secretToken = "SECRET_ID_TOKEN_ABCXYZ"
    val invalidLink = "https://invalid.example.com/?oobCode=$secretOob&email=$secretEmail"
    val validLink = "https://nexzy.page.link/auth?mode=signIn&oobCode=$secretOob"

    val fakeAuthRepo = FakeAuthRepository(
      validLinks = setOf(validLink),
      pendingEmail = null,
      signInWithEmailLinkResult = Result.failure(
        IllegalStateException("Failed for email=$secretEmail oobCode=$secretOob token=$secretToken")
      )
    )
    val vm = EmailLinkViewModel(application, fakeAuthRepo, FakeUserRepository())

    vm.processIncomingLink(invalidLink)
    vm.processIncomingLink(validLink)
    assertTrue(vm.uiState.value.requiresEmailInput)

    vm.onEmailInputChanged(secretEmail)
    vm.submitManualEmail()
    advanceUntilIdle()

    val logs = allLogMessages()
    assertTrue(logs.contains("Email-link validation failed."))
    assertTrue(logs.contains("Pending email missing from local storage; prompting user for email confirmation."))
    assertTrue(logs.contains("Email-link authentication failed: IllegalStateException"))

    assertFalse(logs.contains(secretEmail))
    assertFalse(logs.contains(secretOob))
    assertFalse(logs.contains(secretToken))
    assertFalse(logs.contains(invalidLink))
    assertFalse(logs.contains(validLink))
  }

  @Test
  fun `auth viewmodel signOut logs exception class only without sensitive message or uid`() = runTest(testDispatcher) {
    val secretUid = "SECRET_FIREBASE_UID_444"
    val secretRefreshToken = "SECRET_REFRESH_TOKEN_777"
    val fakeAuthRepo = FakeAuthRepository(
      signOutException = RuntimeException("uid=$secretUid refreshToken=$secretRefreshToken")
    )
    val vm = AuthViewModel(application, fakeAuthRepo, FakeUserRepository())
    advanceUntilIdle()

    ShadowLog.clear()
    vm.signOut()
    advanceUntilIdle()

    assertEquals(AuthState.Unauthenticated, vm.authState.value)
    val logs = allLogMessages()
    assertTrue(logs.contains("User sign-out failed: RuntimeException"))
    assertFalse(logs.contains(secretUid))
    assertFalse(logs.contains(secretRefreshToken))
  }

  @Test
  fun `phone verification viewmodel does not log phone numbers or otp inputs`() = runTest(testDispatcher) {
    val secretRawPhone = "9876543210"
    val secretFormattedPhone = "+919876543210"
    val secretOtp = "654321"
    val vm = PhoneVerificationViewModel(application, FakeAuthRepository(), FakeUserRepository())

    vm.onPhoneNumberChanged(secretRawPhone)
    assertEquals(secretFormattedPhone, vm.uiState.value.formattedPhoneNumber)
    assertTrue(vm.uiState.value.isValidPhoneNumber)

    vm.onOtpCodeChanged(secretOtp)
    assertEquals(secretOtp, vm.uiState.value.otpCode)
    assertTrue(vm.uiState.value.isValidOtp)

    vm.verifyManualOtp()
    advanceUntilIdle()

    val logs = allLogMessages()
    assertFalse(logs.contains(secretRawPhone))
    assertFalse(logs.contains(secretFormattedPhone))
    assertFalse(logs.contains(secretOtp))
  }

  @Test
  fun `app check and firebase manager do not log debug token projectId or appId`() {
    val secretDebugToken = "SECRET_APPCHECK_DEBUG_TOKEN_555"
    val intent = Intent().apply {
      putExtra("FIREBASE_APPCHECK_DEBUG_TOKEN", secretDebugToken)
    }

    AppCheckHelper.configureAppCheck(application, intent)
    val status = FirebaseManager.verifyInitialization(application)
    val app = FirebaseApp.getInstance()

    assertTrue(AppCheckHelper.isConfigured)
    assertTrue(status.isConfigured)

    val logs = allLogMessages()
    assertTrue(logs.contains("Firebase App Check configured using DebugAppCheckProviderFactory."))
    assertTrue(logs.contains("Firebase initialization succeeded."))
    assertFalse(logs.contains(secretDebugToken))

    app.options.projectId?.takeIf { it.isNotBlank() }?.let { projectId ->
      assertFalse(logs.contains(projectId))
    }
    if (app.options.applicationId.isNotBlank()) {
      assertFalse(logs.contains(app.options.applicationId))
    }
  }

  private class FakeAuthRepository(
    private val validLinks: Set<String> = emptySet(),
    private val pendingEmail: String? = null,
    private val signInWithEmailLinkResult: Result<FirebaseUser> =
      Result.failure(UnsupportedOperationException("unused")),
    private val signOutException: RuntimeException? = null
  ) : AuthRepository {
    override suspend fun sendEmailSignInLink(email: String): Result<Unit> = Result.success(Unit)
    override fun isSignInWithEmailLink(link: String): Boolean = link in validLinks
    override suspend fun signInWithEmailLink(email: String, link: String): Result<FirebaseUser> =
      signInWithEmailLinkResult

    override fun savePendingEmail(email: String) {}
    override fun getPendingEmail(): String? = pendingEmail
    override fun clearPendingEmail() {}
    override fun getCurrentUser(): FirebaseUser? = null
    override fun isUserAuthenticated(): Boolean = false
    override fun getAuthStateFlow(): Flow<FirebaseUser?> = flowOf(null)
    override fun signOut() {
      signOutException?.let { throw it }
    }

    override fun verifyPhoneNumber(
      activity: Activity,
      phoneNumber: String,
      resendToken: PhoneAuthProvider.ForceResendingToken?,
      callbacks: PhoneVerificationCallbacks
    ) {}

    override suspend fun linkPhoneCredential(credential: PhoneAuthCredential): Result<FirebaseUser> =
      Result.failure(UnsupportedOperationException("unused"))

    override fun createPhoneCredential(verificationId: String, smsCode: String): PhoneAuthCredential {
      throw UnsupportedOperationException("unused")
    }
  }

  private class FakeUserRepository : UserRepository {
    override suspend fun getUserProfile(uid: String): Result<UserProfile?> = Result.success(null)
    override suspend fun createOrUpdateUserProfile(profile: UserProfile): Result<Unit> = Result.success(Unit)
    override suspend fun updatePhoneVerification(uid: String, phoneNumber: String, isVerified: Boolean): Result<Unit> =
      Result.success(Unit)

    override suspend fun updatePreferredLanguage(uid: String, languageCode: String): Result<Unit> =
      Result.success(Unit)

    override fun getUserProfileFlow(uid: String): Flow<UserProfile?> = flowOf(null)
  }
}
