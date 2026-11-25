package com.example

import android.app.Activity
import android.app.Application
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.test.core.app.ApplicationProvider
import com.example.data.MockDataProvider
import com.example.data.repository.FirestoreUserRepository
import com.example.domain.engine.LocalizedSafetyResources
import com.example.domain.engine.MedicalExplanationParser
import com.example.domain.repository.AuthRepository
import com.example.domain.repository.PhoneVerificationCallbacks
import com.example.domain.repository.UserRepository
import com.example.model.AnatomicalFinding
import com.example.model.CategoryExplanation
import com.example.model.DocumentExtractionResult
import com.example.model.ExplanationConstants
import com.example.model.ExtractedCardiologyReport
import com.example.model.ExtractedClinicalDocument
import com.example.model.ExtractedClinicalFact
import com.example.model.ExtractedLabItem
import com.example.model.ExtractedLaboratoryReport
import com.example.model.ExtractedMedication
import com.example.model.ExtractedRadiologyReport
import com.example.model.ExtractedSpecialisedReport
import com.example.model.FieldProvenance
import com.example.model.FindingStatus
import com.example.model.MedicalDocumentCategory
import com.example.model.ProvenanceAvailability
import com.example.model.RadiologyImpression
import com.example.model.SupportedExplanationLanguage
import com.example.model.UniversalMedicalReport
import com.example.model.UserProfile
import com.example.navigation.Screen
import com.example.ui.PostLoginLanguageRouter
import com.example.ui.auth.AuthState
import com.example.ui.auth.AuthViewModel
import com.example.ui.auth.LanguageUpdateState
import com.example.ui.explanation.SharedPreferencesExplanationConsentStore
import com.example.ui.resolveExplanationLanguage
import com.example.ui.screens.LanguageScreen
import com.example.ui.screens.WelcomeScreen
import com.example.ui.theme.MyApplicationTheme
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.PhoneAuthCredential
import com.google.firebase.auth.PhoneAuthProvider
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Task 3F Unit & Robolectric Tests:
 * - Stage 1: Four-Language Launch Registry & Safe Resolution
 * - Stage 2: Post-Login Language Selection, Backward-Compatible Profile Persistence & Settings Sync
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class Task3FMultilingualExplanationUnitTest {

  @get:Rule
  val composeTestRule = createComposeRule()

  private val testDispatcher = UnconfinedTestDispatcher()

  @Before
  fun setUp() {
    Dispatchers.setMain(testDispatcher)
  }

  @After
  fun tearDown() {
    Dispatchers.resetMain()
  }

  private class FakeAuthRepository(
    private val currentUserValue: FirebaseUser? = null
  ) : AuthRepository {
    override suspend fun sendEmailSignInLink(email: String): Result<Unit> = Result.success(Unit)
    override fun isSignInWithEmailLink(link: String): Boolean = false
    override suspend fun signInWithEmailLink(email: String, link: String): Result<FirebaseUser> =
      Result.failure(UnsupportedOperationException())
    override fun savePendingEmail(email: String) {}
    override fun getPendingEmail(): String? = null
    override fun clearPendingEmail() {}
    override fun getCurrentUser(): FirebaseUser? = currentUserValue
    override fun isUserAuthenticated(): Boolean = currentUserValue != null
    override fun getAuthStateFlow(): Flow<FirebaseUser?> = flowOf(currentUserValue)
    override fun signOut() {}
    override fun verifyPhoneNumber(
      activity: Activity,
      phoneNumber: String,
      resendToken: PhoneAuthProvider.ForceResendingToken?,
      callbacks: PhoneVerificationCallbacks
    ) {}
    override suspend fun linkPhoneCredential(credential: PhoneAuthCredential): Result<FirebaseUser> =
      Result.failure(UnsupportedOperationException())
    override fun createPhoneCredential(verificationId: String, smsCode: String): PhoneAuthCredential {
      throw UnsupportedOperationException()
    }
  }

  private class FakeUserRepository(
    var shouldFailLanguageUpdate: Boolean = false,
    var failureException: Throwable = IOException("Simulated Firestore network outage")
  ) : UserRepository {
    val profilesByUid: MutableMap<String, UserProfile> = mutableMapOf()
    val updatedLanguageCalls: MutableList<Pair<String, String>> = mutableListOf()

    override suspend fun getUserProfile(uid: String): Result<UserProfile?> =
      Result.success(profilesByUid[uid])

    override suspend fun createOrUpdateUserProfile(profile: UserProfile): Result<Unit> {
      profilesByUid[profile.uid] = profile
      return Result.success(Unit)
    }

    override suspend fun updatePhoneVerification(
      uid: String,
      phoneNumber: String,
      isVerified: Boolean
    ): Result<Unit> {
      val current = profilesByUid[uid]
      if (current != null) {
        profilesByUid[uid] = current.copy(phoneNumber = phoneNumber, phoneVerified = isVerified)
      }
      return Result.success(Unit)
    }

    override suspend fun updatePreferredLanguage(uid: String, languageCode: String): Result<Unit> {
      val validated = FirestoreUserRepository.validateSupportedLanguageCode(languageCode)
        ?: return Result.failure(IllegalArgumentException("Unsupported language code: $languageCode"))
      if (shouldFailLanguageUpdate) {
        return Result.failure(failureException)
      }
      updatedLanguageCalls.add(uid to validated)
      val existing = profilesByUid[uid]
      if (existing != null) {
        profilesByUid[uid] = existing.copy(
          preferredLanguage = validated,
          preferredLanguageSelected = true,
          updatedAt = existing.updatedAt + 1L
        )
      }
      return Result.success(Unit)
    }

    override fun getUserProfileFlow(uid: String): Flow<UserProfile?> = flowOf(profilesByUid[uid])
  }

  // ============================================================================
  // STAGE 1 TESTS (PRESERVED)
  // ============================================================================

  @Test
  fun approvedLanguageCodesAndDisplayNamesResolveToCanonicalNames() {
    assertEquals("English", ExplanationConstants.resolveSafeExplanationLanguage("en"))
    assertEquals("English", ExplanationConstants.resolveSafeExplanationLanguage("English"))

    assertEquals("Hindi", ExplanationConstants.resolveSafeExplanationLanguage("hi"))
    assertEquals("Hindi", ExplanationConstants.resolveSafeExplanationLanguage("Hindi"))

    assertEquals("Bengali", ExplanationConstants.resolveSafeExplanationLanguage("bn"))
    assertEquals("Bengali", ExplanationConstants.resolveSafeExplanationLanguage("Bengali"))

    assertEquals("Marathi", ExplanationConstants.resolveSafeExplanationLanguage("mr"))
    assertEquals("Marathi", ExplanationConstants.resolveSafeExplanationLanguage("Marathi"))

    // Verify NexzyApp helper stays aligned with ExplanationConstants
    assertEquals("Hindi", resolveExplanationLanguage("hi"))
    assertEquals("Bengali", resolveExplanationLanguage("bn"))
    assertEquals("Marathi", resolveExplanationLanguage("mr"))
    assertEquals("English", resolveExplanationLanguage("en"))
  }

  @Test
  fun mixedCaseAndWhitespaceNormalizeSafely() {
    assertEquals("English", ExplanationConstants.resolveSafeExplanationLanguage("  EN  "))
    assertEquals("English", ExplanationConstants.resolveSafeExplanationLanguage("\teNgLiSh\n"))

    assertEquals("Hindi", ExplanationConstants.resolveSafeExplanationLanguage("  Hi "))
    assertEquals("Hindi", ExplanationConstants.resolveSafeExplanationLanguage(" hInDi\t"))

    assertEquals("Bengali", ExplanationConstants.resolveSafeExplanationLanguage("\tbN  "))
    assertEquals("Bengali", ExplanationConstants.resolveSafeExplanationLanguage("  BENGALI "))

    assertEquals("Marathi", ExplanationConstants.resolveSafeExplanationLanguage(" Mr "))
    assertEquals("Marathi", ExplanationConstants.resolveSafeExplanationLanguage(" mArAtHi  "))
  }

  @Test
  fun unsupportedPlannedBlankNullAndUnknownLanguagesFallbackToEnglish() {
    // Planned Indian languages must fall back to English, never cross-mapped to another Indian language
    assertEquals("English", ExplanationConstants.resolveSafeExplanationLanguage("ta"))
    assertEquals("English", ExplanationConstants.resolveSafeExplanationLanguage("TA"))
    assertEquals("English", ExplanationConstants.resolveSafeExplanationLanguage("Tamil"))
    assertEquals("English", ExplanationConstants.resolveSafeExplanationLanguage("pa"))
    assertEquals("English", ExplanationConstants.resolveSafeExplanationLanguage("Punjabi"))
    assertEquals("English", ExplanationConstants.resolveSafeExplanationLanguage("gu"))
    assertEquals("English", ExplanationConstants.resolveSafeExplanationLanguage("Gujarati"))

    // Null, empty, whitespace, and arbitrary invalid strings must fall back to English
    assertEquals("English", ExplanationConstants.resolveSafeExplanationLanguage(null))
    assertEquals("English", ExplanationConstants.resolveSafeExplanationLanguage(""))
    assertEquals("English", ExplanationConstants.resolveSafeExplanationLanguage("   \t\n "))
    assertEquals("English", ExplanationConstants.resolveSafeExplanationLanguage("unknown"))
    assertEquals("English", ExplanationConstants.resolveSafeExplanationLanguage("fr"))
    assertEquals("English", ExplanationConstants.resolveSafeExplanationLanguage("es"))
  }

  @Test
  fun launchLanguageRegistryContainsExactlyFourApprovedLanguages() {
    assertEquals(
      setOf("en", "hi", "bn", "mr"),
      ExplanationConstants.SUPPORTED_LANGUAGE_CODES
    )
    assertEquals(
      listOf("English", "Hindi", "Bengali", "Marathi"),
      ExplanationConstants.SUPPORTED_LANGUAGE_NAMES
    )
    assertEquals(4, SupportedExplanationLanguage.launchLanguages.size)
  }

  @Test
  fun sampleLanguagesMarksExactlyFourApprovedLaunchLanguagesSupported() {
    val languagesByCode = MockDataProvider.sampleLanguages.associateBy { it.code }

    val supportedCodes = MockDataProvider.sampleLanguages
      .filter { it.isSupported }
      .map { it.code }
      .toSet()

    assertEquals(setOf("en", "hi", "bn", "mr"), supportedCodes)

    val marathi = languagesByCode.getValue("mr")
    assertTrue(marathi.isSupported)
    assertFalse(marathi.subtitle.contains("Planned release", ignoreCase = true))
    assertTrue(marathi.subtitle.contains("Available now"))

    // Planned languages remain explicitly unsupported
    listOf("ta", "pa", "gu").forEach { plannedCode ->
      val option = languagesByCode.getValue(plannedCode)
      assertFalse("Planned language $plannedCode must stay unsupported", option.isSupported)
    }

    // Starter Free feature wording matches the 4 launch languages
    val starterPlan = MockDataProvider.subscriptionPlans.first { it.id == "free" }
    assertTrue(
      starterPlan.features.any {
        it.contains("English") && it.contains("Hindi") && it.contains("Bengali") && it.contains("Marathi")
      }
    )
  }

  // ============================================================================
  // STAGE 2 TESTS: POST-LOGIN LANGUAGE SELECTION & PROFILE PERSISTENCE
  // ============================================================================

  @Test
  fun stage2_newUserWithoutConfirmedPreferenceRoutesToLanguageSelectionAfterLogin() {
    val brandNewProfile = UserProfile(
      uid = "new-uid-1",
      email = "new@example.com",
      emailVerified = true,
      phoneVerified = false,
      preferredLanguage = "en",
      preferredLanguageSelected = false
    )

    // Default "en" must NOT be treated as an explicit user choice
    assertFalse(PostLoginLanguageRouter.hasCompletedLanguageSelection(brandNewProfile))
    assertTrue(PostLoginLanguageRouter.requiresPostLoginLanguageSelection(brandNewProfile))
    assertEquals(
      Screen.LanguageSelection.createRoute(onboarding = true),
      PostLoginLanguageRouter.resolveInitialAuthenticatedDestination(brandNewProfile)
    )

    // Even if phone is already verified, missing language confirmation must still show onboarding
    val phoneVerifiedNoLangProfile = brandNewProfile.copy(phoneVerified = true)
    assertEquals(
      Screen.LanguageSelection.createRoute(onboarding = true),
      PostLoginLanguageRouter.resolveInitialAuthenticatedDestination(phoneVerifiedNoLangProfile)
    )

    // After onboarding language choice is confirmed, route depends on phone verification
    val confirmedUnverifiedPhone = brandNewProfile.copy(preferredLanguageSelected = true)
    assertEquals(
      Screen.PhoneEntry.route,
      PostLoginLanguageRouter.resolvePostLanguageOnboardingDestination(confirmedUnverifiedPhone)
    )
    assertEquals(
      Screen.Home.route,
      PostLoginLanguageRouter.resolvePostLanguageOnboardingDestination(confirmedUnverifiedPhone.copy(phoneVerified = true))
    )
  }

  @Test
  fun stage2_explicitEnglishChoicePersistsAndPreventsFutureOnboarding() = runBlocking {
    val app = ApplicationProvider.getApplicationContext<Application>()
    val fakeUserRepo = FakeUserRepository()
    val originalCreatedAt = 1700000001234L
    val initialProfile = UserProfile(
      uid = "uid-en-1",
      email = "en@example.com",
      emailVerified = true,
      createdAt = originalCreatedAt,
      updatedAt = originalCreatedAt,
      preferredLanguage = "en",
      preferredLanguageSelected = false
    )
    fakeUserRepo.profilesByUid[initialProfile.uid] = initialProfile
    val viewModel = AuthViewModel(app, FakeAuthRepository(), fakeUserRepo)

    val (updated, state) = viewModel.persistPreferredLanguageForUser(
      uid = initialProfile.uid,
      languageCode = "en",
      existingProfile = initialProfile
    )

    assertTrue(state is LanguageUpdateState.Success)
    assertEquals("en", (state as LanguageUpdateState.Success).languageCode)
    assertNotNull(updated)
    assertEquals("en", updated!!.preferredLanguage)
    assertTrue(updated.preferredLanguageSelected)
    assertEquals("en", fakeUserRepo.profilesByUid[initialProfile.uid]?.preferredLanguage)
    assertTrue(fakeUserRepo.profilesByUid[initialProfile.uid]?.preferredLanguageSelected == true)

    // Returning login with explicit "en" must NOT re-open mandatory onboarding
    assertFalse(PostLoginLanguageRouter.requiresPostLoginLanguageSelection(updated))
    assertEquals(
      Screen.Home.route,
      PostLoginLanguageRouter.resolveInitialAuthenticatedDestination(updated.copy(phoneVerified = true))
    )
    assertEquals(
      Screen.PhoneEntry.route,
      PostLoginLanguageRouter.resolveInitialAuthenticatedDestination(updated.copy(phoneVerified = false))
    )
  }

  @Test
  fun stage2_choosingHindiBengaliOrMarathiPersistsAndUpdatesExplanationLanguage() = runBlocking {
    val app = ApplicationProvider.getApplicationContext<Application>()
    val fakeUserRepo = FakeUserRepository()
    val viewModel = AuthViewModel(app, FakeAuthRepository(), fakeUserRepo)

    val choices = listOf(
      "hi" to "Hindi",
      "bn" to "Bengali",
      "mr" to "Marathi"
    )

    choices.forEach { (code, expectedDisplayName) ->
      val uid = "uid-$code"
      val initialProfile = UserProfile(
        uid = uid,
        email = "$code@example.com",
        emailVerified = true,
        phoneVerified = true,
        preferredLanguage = "en",
        preferredLanguageSelected = false
      )
      fakeUserRepo.profilesByUid[uid] = initialProfile

      val (updated, state) = viewModel.persistPreferredLanguageForUser(
        uid = uid,
        languageCode = code,
        existingProfile = initialProfile
      )

      assertEquals(LanguageUpdateState.Success(code), state)
      assertNotNull(updated)
      assertEquals(code, updated!!.preferredLanguage)
      assertTrue(updated.preferredLanguageSelected)
      assertEquals(expectedDisplayName, resolveExplanationLanguage(updated.preferredLanguage))
      assertEquals(code, fakeUserRepo.profilesByUid[uid]?.preferredLanguage)
      assertTrue(fakeUserRepo.profilesByUid[uid]?.preferredLanguageSelected == true)
    }
  }

  @Test
  fun stage2_legacyNonEnglishProfileWithoutFlagIsPreservedAndNotForcedThroughOnboarding() {
    listOf("hi", "bn", "mr").forEach { legacyCode ->
      // 1. Firestore document missing preferredLanguageSelected field altogether
      val deserialized = FirestoreUserRepository.profileFromDocumentFields(
        uid = "legacy-$legacyCode",
        storedUid = "legacy-$legacyCode",
        email = "legacy@example.com",
        emailVerified = true,
        phoneNumber = "+919876543210",
        phoneVerified = true,
        displayName = "Legacy Caregiver",
        createdAt = 1680000000000L,
        updatedAt = 1690000000000L,
        accountStatus = "ACTIVE",
        preferredLanguage = legacyCode,
        preferredLanguageSelected = null
      )

      assertEquals(legacyCode, deserialized.preferredLanguage)
      assertTrue(
        "Legacy $legacyCode profile without flag must be treated as explicitly selected",
        deserialized.preferredLanguageSelected
      )
      assertFalse(PostLoginLanguageRouter.requiresPostLoginLanguageSelection(deserialized))
      assertEquals(
        Screen.Home.route,
        PostLoginLanguageRouter.resolveInitialAuthenticatedDestination(deserialized)
      )

      // 2. Profile merge on subsequent login must NOT overwrite legacy non-English preference back to "en"
      val defaultLoginPayload = UserProfile(
        uid = deserialized.uid,
        email = deserialized.email,
        emailVerified = true,
        preferredLanguage = "en",
        preferredLanguageSelected = false
      )
      val updateMap = FirestoreUserRepository.buildUpdateProfileMap(
        profile = defaultLoginPayload,
        existingPreferredLanguage = legacyCode,
        existingPreferredLanguageSelected = null
      )
      assertFalse(
        "Re-login merge must not overwrite legacy $legacyCode with default 'en'",
        updateMap.containsKey("preferredLanguage")
      )
    }

    // Legacy English profile without flag MUST still be offered language selection
    val legacyEnglishWithoutFlag = FirestoreUserRepository.profileFromDocumentFields(
      uid = "legacy-en",
      storedUid = "legacy-en",
      email = "legacyen@example.com",
      emailVerified = true,
      phoneNumber = "",
      phoneVerified = false,
      displayName = "Legacy English",
      createdAt = 1680000000000L,
      updatedAt = 1690000000000L,
      accountStatus = "ACTIVE",
      preferredLanguage = "en",
      preferredLanguageSelected = null
    )
    assertEquals("en", legacyEnglishWithoutFlag.preferredLanguage)
    assertFalse(legacyEnglishWithoutFlag.preferredLanguageSelected)
    assertTrue(PostLoginLanguageRouter.requiresPostLoginLanguageSelection(legacyEnglishWithoutFlag))
  }

  @Test
  fun stage2_returningUserRestoresSavedLanguageOnStartupAndLogin() {
    val returningMarathi = UserProfile(
      uid = "returning-mr",
      email = "mr@example.com",
      emailVerified = true,
      phoneVerified = true,
      preferredLanguage = "mr",
      preferredLanguageSelected = true
    )

    assertEquals("Marathi", resolveExplanationLanguage(returningMarathi.preferredLanguage))
    assertEquals(
      Screen.Home.route,
      PostLoginLanguageRouter.resolveInitialAuthenticatedDestination(returningMarathi)
    )
    assertEquals(
      Screen.Home.route,
      PostLoginLanguageRouter.resolveSplashAuthenticatedDestination(
        // Verify Splash routing when Authenticated profile has completed selection
        AuthState.Loading // Also verify Loading falls back safely without trapping in onboarding
      )
    )
  }

  @Test
  fun stage2_updatingLanguageLaterFromSettingsPersistsAndUpdatesActiveProfile() = runBlocking {
    val app = ApplicationProvider.getApplicationContext<Application>()
    val fakeUserRepo = FakeUserRepository()
    val viewModel = AuthViewModel(app, FakeAuthRepository(), fakeUserRepo)

    val initialProfile = UserProfile(
      uid = "uid-settings-1",
      email = "settings@example.com",
      emailVerified = true,
      phoneNumber = "+919876543210",
      phoneVerified = true,
      displayName = "Priya",
      createdAt = 1700000000000L,
      updatedAt = 1700000000000L,
      preferredLanguage = "en",
      preferredLanguageSelected = true
    )
    fakeUserRepo.profilesByUid[initialProfile.uid] = initialProfile

    val (afterHindi, state1) = viewModel.persistPreferredLanguageForUser(
      uid = initialProfile.uid,
      languageCode = "hi",
      existingProfile = initialProfile
    )
    assertEquals(LanguageUpdateState.Success("hi"), state1)
    assertEquals("Hindi", resolveExplanationLanguage(afterHindi?.preferredLanguage))

    val (afterBengali, state2) = viewModel.persistPreferredLanguageForUser(
      uid = initialProfile.uid,
      languageCode = "bn",
      existingProfile = afterHindi
    )
    assertEquals(LanguageUpdateState.Success("bn"), state2)
    assertEquals("Bengali", resolveExplanationLanguage(afterBengali?.preferredLanguage))
    assertEquals("bn", fakeUserRepo.profilesByUid[initialProfile.uid]?.preferredLanguage)
    assertEquals(
      listOf("uid-settings-1" to "hi", "uid-settings-1" to "bn"),
      fakeUserRepo.updatedLanguageCalls
    )
  }

  @Test
  fun stage2_invalidAndUnsupportedLanguageCodesAreRejectedWithoutWriting() = runBlocking {
    val app = ApplicationProvider.getApplicationContext<Application>()
    val fakeUserRepo = FakeUserRepository()
    val firestoreRepo = FirestoreUserRepository(firestoreInstance = null)
    val viewModel = AuthViewModel(app, FakeAuthRepository(), fakeUserRepo)

    val existing = UserProfile(
      uid = "uid-valid-1",
      preferredLanguage = "hi",
      preferredLanguageSelected = true
    )
    fakeUserRepo.profilesByUid[existing.uid] = existing

    // Blank UID rejected
    assertTrue(firestoreRepo.updatePreferredLanguage("", "hi").isFailure)
    assertTrue(firestoreRepo.updatePreferredLanguage("   ", "en").isFailure)

    // Unsupported/planned/blank/garbage codes rejected without Firestore call
    listOf("ta", "pa", "gu", "fr", "", "   ", "unknown", "English").forEach { badCode ->
      assertTrue("Repository must reject unsupported code: $badCode", firestoreRepo.updatePreferredLanguage("uid-valid-1", badCode).isFailure)
      val (profileAfter, vmState) = viewModel.persistPreferredLanguageForUser(
        uid = existing.uid,
        languageCode = badCode,
        existingProfile = existing
      )
      assertNull(profileAfter)
      assertTrue("ViewModel must emit Error for bad code: $badCode", vmState is LanguageUpdateState.Error)
    }

    assertTrue(fakeUserRepo.updatedLanguageCalls.isEmpty())
    assertEquals("hi", fakeUserRepo.profilesByUid[existing.uid]?.preferredLanguage)
  }

  @Test
  fun stage2_firestoreWriteFailureRetainsPriorProfileAndDoesNotAdvance() = runBlocking {
    val app = ApplicationProvider.getApplicationContext<Application>()
    val fakeUserRepo = FakeUserRepository(shouldFailLanguageUpdate = true)
    val existingProfile = UserProfile(
      uid = "uid-fail-1",
      email = "fail@example.com",
      phoneVerified = false,
      preferredLanguage = "en",
      preferredLanguageSelected = false
    )
    fakeUserRepo.profilesByUid[existingProfile.uid] = existingProfile
    val viewModel = AuthViewModel(app, FakeAuthRepository(), fakeUserRepo)

    val (updatedProfile, vmState) = viewModel.persistPreferredLanguageForUser(
      uid = existingProfile.uid,
      languageCode = "mr",
      existingProfile = existingProfile
    )

    assertNull("Profile must not be mutated on failure", updatedProfile)
    assertTrue(vmState is LanguageUpdateState.Error)
    assertTrue((vmState as LanguageUpdateState.Error).message.contains("network", ignoreCase = true))
    assertEquals("en", fakeUserRepo.profilesByUid[existingProfile.uid]?.preferredLanguage)
    assertFalse(fakeUserRepo.profilesByUid[existingProfile.uid]?.preferredLanguageSelected == true)
    assertTrue(PostLoginLanguageRouter.requiresPostLoginLanguageSelection(fakeUserRepo.profilesByUid[existingProfile.uid]))
  }

  @Test
  fun stage2_languageUpdatePreservesCreatedAtPhoneVerificationAndServerManagedFields() {
    val createdAt = 1675000000000L
    val languageUpdatePayload = FirestoreUserRepository.buildPreferredLanguageUpdateMap(
      uid = "uid-preserve-1",
      normalizedLanguageCode = "mr",
      nowMillis = 1710000000000L
    )

    assertEquals("uid-preserve-1", languageUpdatePayload["uid"])
    assertEquals("mr", languageUpdatePayload["preferredLanguage"])
    assertEquals(true, languageUpdatePayload["preferredLanguageSelected"])
    assertTrue(languageUpdatePayload.containsKey("updatedAt"))

    val protectedAndUnrelatedKeys = setOf(
      "createdAt",
      "accountStatus",
      "phoneNumber",
      "phoneVerified",
      "subscription",
      "credits",
      "paymentEntitlements",
      "medicalReports"
    )
    assertTrue(
      "Language update map must not overwrite protected/unrelated fields",
      languageUpdatePayload.keys.intersect(protectedAndUnrelatedKeys).isEmpty()
    )

    // Profile merge preserving createdAt & phoneVerified
    val existingCreatedAtUpdate = FirestoreUserRepository.buildUpdateProfileMap(
      profile = UserProfile(
        uid = "uid-preserve-1",
        email = "p@example.com",
        emailVerified = true,
        phoneNumber = "+919876543210",
        phoneVerified = true,
        displayName = "Priya",
        createdAt = createdAt,
        preferredLanguage = "mr",
        preferredLanguageSelected = true
      ),
      existingPreferredLanguage = "hi",
      existingPreferredLanguageSelected = true
    )
    assertFalse(existingCreatedAtUpdate.containsKey("createdAt"))
    assertFalse(existingCreatedAtUpdate.containsKey("accountStatus"))
    assertEquals("mr", existingCreatedAtUpdate["preferredLanguage"])
    assertEquals(true, existingCreatedAtUpdate["preferredLanguageSelected"])
    assertEquals("+919876543210", existingCreatedAtUpdate["phoneNumber"])
    assertEquals(true, existingCreatedAtUpdate["phoneVerified"])
  }

  @Test
  fun stage2_uidScopedConsentAndLanguageUiOnboardingGuardsRemainIntact() {
    // 1. Verify UID-scoped explanation consent isolation remains strictly intact
    val app = ApplicationProvider.getApplicationContext<Application>()
    val consentStore = SharedPreferencesExplanationConsentStore(app)
    consentStore.clearAllConsents()

    consentStore.setExplanationConsentGranted("uid-alpha", true)
    assertTrue(consentStore.hasGrantedExplanationConsent("uid-alpha"))
    assertFalse(consentStore.hasGrantedExplanationConsent("uid-beta"))
    assertFalse(consentStore.hasGrantedExplanationConsent(null))
    assertFalse(consentStore.hasGrantedExplanationConsent(""))

    consentStore.clearConsentForUser("uid-alpha")
    assertFalse(consentStore.hasGrantedExplanationConsent("uid-alpha"))

    // 2. Verify LanguageScreen UI in onboarding mode: hides Back button, blocks unsupported selection,
    // shows error banner and disables button when saving
    var selectedCode = "en"
    var confirmedCount = 0
    composeTestRule.setContent {
      MyApplicationTheme {
        LanguageScreen(
          languages = MockDataProvider.sampleLanguages,
          selectedLanguageCode = selectedCode,
          onLanguageSelected = { selectedCode = it },
          onConfirm = { confirmedCount++ },
          onNavigateBack = {},
          isOnboarding = true,
          isSaving = false,
          errorMessage = "Network error while saving"
        )
      }
    }

    composeTestRule.onNodeWithContentDescription("Back").assertDoesNotExist()
    composeTestRule.onNodeWithTag("language_error_banner").assertIsDisplayed()
    composeTestRule.onNodeWithText("First release supports English, Hindi, Bengali, and Marathi. Other regional languages are in progress.").assertIsDisplayed()

    // Selecting planned Tamil must not update selection; selecting Marathi must succeed
    composeTestRule.onNodeWithTag("language_list").performScrollToNode(hasTestTag("lang_card_ta"))
    composeTestRule.onNodeWithTag("lang_card_ta").performClick()
    assertEquals("en", selectedCode)
    composeTestRule.onNodeWithTag("language_list").performScrollToNode(hasTestTag("lang_card_mr"))
    composeTestRule.onNodeWithTag("lang_card_mr").performClick()
    assertEquals("mr", selectedCode)

    composeTestRule.onNodeWithTag("btn_confirm_language").assertIsEnabled().performClick()
    assertEquals(1, confirmedCount)
  }

  @Test
  fun stage2_welcomeScreenListsAllFourSupportedLanguagesAndSavingStateDisablesConfirm() {
    composeTestRule.setContent {
      MyApplicationTheme {
        WelcomeScreen(
          onGetStarted = {},
          onSignInClick = {}
        )
      }
    }
    composeTestRule.onNodeWithText(
      "Explanations available in English, Hindi, Bengali, and Marathi, with more languages coming soon."
    ).performScrollTo().assertIsDisplayed()
  }

  @Test
  fun stage2_languageScreenDisablesConfirmButtonWhileSaving() {
    composeTestRule.setContent {
      MyApplicationTheme {
        LanguageScreen(
          languages = MockDataProvider.sampleLanguages,
          selectedLanguageCode = "hi",
          onLanguageSelected = {},
          onConfirm = {},
          onNavigateBack = {},
          isOnboarding = false,
          isSaving = true,
          errorMessage = null
        )
      }
    }
    composeTestRule.onNodeWithTag("btn_confirm_language").assertIsNotEnabled()
    composeTestRule.onNodeWithText("Saving Preference...").assertIsDisplayed()
  }

  // ============================================================================
  // STAGE 3B TESTS: MULTILINGUAL MEDICAL SAFETY RESOURCES & PARSER PROTECTION
  // ============================================================================

  private fun clinicalDocWithMedications(
    medications: List<ExtractedMedication>,
    diagnoses: List<ExtractedClinicalFact> = listOf(
      ExtractedClinicalFact(
        "Essential hypertension",
        FieldProvenance(1, "Dx: Essential hypertension", 0.93f, ProvenanceAvailability.AVAILABLE)
      )
    ),
    dischargeInstructions: List<ExtractedClinicalFact> = listOf(
      ExtractedClinicalFact(
        "Track nocturnal cough and meal timing",
        FieldProvenance(1, "Track nocturnal cough & meal timing", 0.90f, ProvenanceAvailability.AVAILABLE)
      )
    )
  ): DocumentExtractionResult = DocumentExtractionResult(
    category = MedicalDocumentCategory.CLINICAL,
    pageCount = 1,
    isMultiPageBatch = false,
    report = UniversalMedicalReport.Clinical(
      ExtractedClinicalDocument(
        documentSubtype = "Outpatient Prescription",
        facilityOrDoctorName = "Dr. A. Mehta",
        primaryDiagnoses = diagnoses,
        surgicalProcedures = emptyList(),
        prescribedMedications = medications,
        allergiesRecorded = emptyList(),
        dischargeInstructions = dischargeInstructions,
        followUpDate = "2026-12-20"
      )
    )
  )

  private fun amlodipineStandardDoc(): DocumentExtractionResult = clinicalDocWithMedications(
    listOf(
      ExtractedMedication(
        drugName = "Amlodipine",
        dosage = "5 mg",
        frequency = "Once daily",
        duration = "30 days",
        instructions = "Take after breakfast",
        provenance = FieldProvenance(1, "Tab Amlodipine 5 mg once daily x 30d", 0.95f, ProvenanceAvailability.AVAILABLE)
      )
    )
  )

  @Test
  fun stage3b_fourLanguageResourceResolutionPreservesEnglishAndUnsupportedFallback() {
    val en = LocalizedSafetyResources.stringsFor("en")
    val hi = LocalizedSafetyResources.stringsFor("hi")
    val bn = LocalizedSafetyResources.stringsFor("bn")
    val mr = LocalizedSafetyResources.stringsFor("mr")
    val unsupported = LocalizedSafetyResources.stringsFor("ta")

    assertEquals(SupportedExplanationLanguage.ENGLISH, en.language)
    assertEquals(ExplanationConstants.MEDICAL_EXPLANATION_DISCLAIMER, en.medicalExplanationDisclaimer)
    assertEquals(ExplanationConstants.MEDICATION_SAFETY_NOTE, en.medicationSafetyNote)
    assertEquals(en, unsupported)

    listOf(hi, bn, mr).forEach { pack ->
      assertTrue(pack.safeFallbackSummary.isNotBlank())
      assertTrue(pack.uncertainStatusNote.isNotBlank())
      assertTrue(pack.notMentionedStatusNote.isNotBlank())
      assertTrue(pack.cardiologyConclusionNotReported.isNotBlank())
      assertTrue(pack.specialisedConclusionNotReported.isNotBlank())
      assertTrue(pack.medicalExplanationDisclaimer.isNotBlank())
      assertTrue(pack.medicationSafetyNote.isNotBlank())
      assertTrue(pack.medicalExplanationDisclaimer != en.medicalExplanationDisclaimer)
      assertTrue(pack.medicationSafetyNote != en.medicationSafetyNote)
      assertTrue(pack.safeFallbackSummary != en.safeFallbackSummary)
    }

    // Verify canonical name and blank/unknown resolution
    assertEquals(hi, LocalizedSafetyResources.stringsFor("Hindi"))
    assertEquals(bn, LocalizedSafetyResources.stringsFor("  BN  "))
    assertEquals(mr, LocalizedSafetyResources.stringsFor("Marathi"))
    assertEquals(en, LocalizedSafetyResources.stringsFor(null))
    assertEquals(en, LocalizedSafetyResources.stringsFor("pa"))
    assertEquals(en, LocalizedSafetyResources.stringsFor("gu"))
  }

  @Test
  fun stage3b_unicodeNormalizationAndIndicBoundariesWorkInParsedOutput() {
    val medDoc = clinicalDocWithMedications(
      listOf(
        ExtractedMedication(
          drugName = "Amlodipine",
          dosage = "5 mg",
          frequency = "HS",
          duration = "10 days",
          instructions = null,
          provenance = FieldProvenance(1, "Tab Amlodipine 5 mg HS", 0.92f, ProvenanceAvailability.AVAILABLE)
        )
      )
    )

    // 1. Decomposed NFD Devanagari ("बढ़ा" with ढ+\u093c) & combining matras ("रोकें।")
    val nfdDevanagariIncrease = "खुराक ब\u0922\u093cा दें।" // "खुराक बढ़ा दें।" using NFD ढ + nukta
    val hiNfdJson = """
      {
        "conciseSummary": "$nfdDevanagariIncrease",
        "medications": [
          {"medicationIndex": 0, "drugName": "Amlodipine", "printedScheduleExplanation": "Amlodipine 5 mg HS।"}
        ]
      }
    """.trimIndent()
    val hiParsed = MedicalExplanationParser.parseExplanationJsonSafe(hiNfdJson, medDoc, language = "hi").getOrThrow()
    assertEquals(LocalizedSafetyResources.stringsFor("hi").safeFallbackSummary, hiParsed.conciseSummary)

    // 2. Decomposed NFD Bengali o-kar (\u09C7\u09BE instead of \u09CB) inside "ডোজ বন্ধ করুন" & "রোজ"
    val nfdBengaliDose = "ড\u09C7\u09BEজ দ্বিগুণ করুন।" // "ডোজ দ্বিগুণ করুন।" using NFD ে + া
    val bnNfdJson = """
      {
        "conciseSummary": "$nfdBengaliDose",
        "medications": [
          {"medicationIndex": 0, "drugName": "Amlodipine", "printedScheduleExplanation": "Amlodipine 5 mg HS।"}
        ]
      }
    """.trimIndent()
    val bnParsed = MedicalExplanationParser.parseExplanationJsonSafe(bnNfdJson, medDoc, language = "bn").getOrThrow()
    assertEquals(LocalizedSafetyResources.stringsFor("bn").safeFallbackSummary, bnParsed.conciseSummary)

    // 3. Mixed-script Marathi drug suffix "Amlodipine-ची" must still bind to Amlodipine's unexpanded HS rule
    val mrMixedBoundaryJson = """
      {
        "conciseSummary": "Amlodipine-ची गोळी रात्री झोपताना घ्यायची आहे.",
        "medications": [
          {"medicationIndex": 0, "drugName": "Amlodipine", "printedScheduleExplanation": "5 mg HS."}
        ]
      }
    """.trimIndent()
    val mrParsed = MedicalExplanationParser.parseExplanationJsonSafe(mrMixedBoundaryJson, medDoc, language = "mr").getOrThrow()
    assertEquals(LocalizedSafetyResources.stringsFor("mr").safeFallbackSummary, mrParsed.conciseSummary)
  }

  @Test
  fun stage3b_hindiBengaliMarathiUnsafeStoppingStartingAndDosageChangesAreRejected() {
    val doc = amlodipineStandardDoc()

    // Hindi: stopping, starting, dosage increase/double, Indic numeral ২/२ extra pills, unsupported indication
    val hiUnsafeJson = """
      {
        "conciseSummary": "आप यह दवा बंद कर दें।",
        "documentOverviewExplanation": "नई दवा लेना शुरू कर दें।",
        "diagnoses": [
          {"diagnosisIndex": 0, "plainLanguageExplanation": "ब्लड प्रेशर के लिए खुराक दोगुनी करें।"}
        ],
        "medications": [
          {"medicationIndex": 0, "drugName": "Amlodipine", "printedScheduleExplanation": "रोज़ २ गोलियां लें।"}
        ],
        "dischargeInstructions": [
          {"instructionIndex": 0, "plainLanguageExplanation": "यह दवा सीने के दर्द के इलाज के लिए दी गई है।"}
        ],
        "medicalTerms": [
          {"term": "Amlodipine", "simpleDefinition": "आपको खुराक कम करनी चाहिए।"},
          {"term": "उच्च रक्तचाप", "simpleDefinition": "धमनियों में रक्तचाप सामान्य से अधिक होना।"}
        ],
        "questionsForDoctor": [
          "क्या मैं अपनी दवा बंद कर सकता हूँ या यह खुराक घटा दें?",
          "अगली जांच पर कौन से रिकॉर्ड साथ लाने चाहिए?"
        ]
      }
    """.trimIndent()
    val hiParsed = MedicalExplanationParser.parseExplanationJsonSafe(hiUnsafeJson, doc, "hi").getOrThrow()
    val hiClin = hiParsed.categoryExplanation as CategoryExplanation.Clinical
    assertEquals(LocalizedSafetyResources.stringsFor("hi").safeFallbackSummary, hiParsed.conciseSummary)
    assertNull(hiClin.documentOverviewExplanation)
    assertNull(hiClin.diagnoses[0].plainLanguageExplanation)
    assertNull(hiClin.medications[0].printedScheduleExplanation)
    assertNull(hiClin.dischargeInstructions[0].plainLanguageExplanation)
    assertTrue(hiParsed.medicalTerms.none { it.term == "Amlodipine" })
    assertTrue(hiParsed.medicalTerms.any { it.term == "उच्च रक्तचाप" })
    assertEquals(listOf("अगली जांच पर कौन से रिकॉर्ड साथ लाने चाहिए?"), hiParsed.questionsForDoctor)

    // Bengali: stopping, doubling dose, Indic numeral ২ pills, prescribing advice, unsupported indication
    val bnUnsafeJson = """
      {
        "conciseSummary": "এই ওষুধ খাওয়া বন্ধ করুন।",
        "documentOverviewExplanation": "নতুন ওষুধ খাওয়া শুরু করুন।",
        "diagnoses": [
          {"diagnosisIndex": 0, "plainLanguageExplanation": "ডোজ দ্বিগুণ করুন এবং মাত্রা বাড়ান।"}
        ],
        "medications": [
          {"medicationIndex": 0, "drugName": "Amlodipine", "printedScheduleExplanation": "প্রতিদিন ২টি বড়ি খান।"}
        ],
        "dischargeInstructions": [
          {"instructionIndex": 0, "plainLanguageExplanation": "এই ওষুধ বুক ব্যথা চিকিৎসার জন্য দেওয়া হয়েছে।"}
        ],
        "medicalTerms": [
          {"term": "Amlodipine", "simpleDefinition": "আপনার ডোজ কমানো উচিত।"},
          {"term": "উচ্চ রক্তচাপ", "simpleDefinition": "রক্তনালীতে স্বাভাবিকের চেয়ে বেশি চাপ।"}
        ],
        "questionsForDoctor": [
          "আমি কি বড়ি অর্ধেক করুন বা ওষুধ বন্ধ করুন?",
          "পরবর্তী ভিজিটে কী কী রিপোর্ট আনতে হবে?"
        ]
      }
    """.trimIndent()
    val bnParsed = MedicalExplanationParser.parseExplanationJsonSafe(bnUnsafeJson, doc, "bn").getOrThrow()
    val bnClin = bnParsed.categoryExplanation as CategoryExplanation.Clinical
    assertEquals(LocalizedSafetyResources.stringsFor("bn").safeFallbackSummary, bnParsed.conciseSummary)
    assertNull(bnClin.documentOverviewExplanation)
    assertNull(bnClin.diagnoses[0].plainLanguageExplanation)
    assertNull(bnClin.medications[0].printedScheduleExplanation)
    assertNull(bnClin.dischargeInstructions[0].plainLanguageExplanation)
    assertTrue(bnParsed.medicalTerms.none { it.term == "Amlodipine" })
    assertTrue(bnParsed.medicalTerms.any { it.term == "উচ্চ রক্তচাপ" })
    assertEquals(listOf("পরবর্তী ভিজিটে কী কী রিপোর্ট আনতে হবে?"), bnParsed.questionsForDoctor)

    // Marathi: stopping, halving/doubling dose, Devanagari २ pills, prescribing advice, therapeutic claim
    val mrUnsafeJson = """
      {
        "conciseSummary": "हे औषध घेणे बंद करा.",
        "documentOverviewExplanation": "नवीन औषध घेणे सुरू करा.",
        "diagnoses": [
          {"diagnosisIndex": 0, "plainLanguageExplanation": "रक्तदाबासाठी डोस दुप्पट करा."}
        ],
        "medications": [
          {"medicationIndex": 0, "drugName": "Amlodipine", "printedScheduleExplanation": "दररोज २ गोळ्या घ्या."}
        ],
        "dischargeInstructions": [
          {"instructionIndex": 0, "plainLanguageExplanation": "हे औषध छातीतील दुखणे उपचारासाठी दिले आहे."}
        ],
        "medicalTerms": [
          {"term": "Amlodipine", "simpleDefinition": "तुम्ही डोस कमी करावे."},
          {"term": "उच्च रक्तदाब", "simpleDefinition": "रक्तवाहिन्यांमध्ये दाब नेहमीपेक्षा जास्त असणे."}
        ],
        "questionsForDoctor": [
          "मी गोळी अर्धी करा किंवा औषध बंद करा का?",
          "पुढील तपासणीवेळी कोणते अहवाल सोबत आणावेत?"
        ]
      }
    """.trimIndent()
    val mrParsed = MedicalExplanationParser.parseExplanationJsonSafe(mrUnsafeJson, doc, "mr").getOrThrow()
    val mrClin = mrParsed.categoryExplanation as CategoryExplanation.Clinical
    assertEquals(LocalizedSafetyResources.stringsFor("mr").safeFallbackSummary, mrParsed.conciseSummary)
    assertNull(mrClin.documentOverviewExplanation)
    assertNull(mrClin.diagnoses[0].plainLanguageExplanation)
    assertNull(mrClin.medications[0].printedScheduleExplanation)
    assertNull(mrClin.dischargeInstructions[0].plainLanguageExplanation)
    assertTrue(mrParsed.medicalTerms.none { it.term == "Amlodipine" })
    assertTrue(mrParsed.medicalTerms.any { it.term == "उच्च रक्तदाब" })
    assertEquals(listOf("पुढील तपासणीवेळी कोणते अहवाल सोबत आणावेत?"), mrParsed.questionsForDoctor)
  }

  @Test
  fun stage3b_codeSwitchedEnglishInIndicOutputIsStillRejected() {
    val doc = amlodipineStandardDoc()

    // Hindi with pure English unsafe phrase AND SOV code-switched phrase ("dose double करें")
    val hiMixed = MedicalExplanationParser.parseExplanationJsonSafe(
      """
        {
          "conciseSummary": "रिपोर्ट में Amlodipine 5 mg दर्ज है; कृपया stop taking करें।",
          "medications": [
            {"medicationIndex": 0, "drugName": "Amlodipine", "printedScheduleExplanation": "Amlodipine की dose double करें।"}
          ]
        }
      """.trimIndent(),
      doc,
      "hi"
    ).getOrThrow()
    assertEquals(LocalizedSafetyResources.stringsFor("hi").safeFallbackSummary, hiMixed.conciseSummary)
    assertNull((hiMixed.categoryExplanation as CategoryExplanation.Clinical).medications[0].printedScheduleExplanation)

    // Bengali with English unsafe phrase
    val bnMixed = MedicalExplanationParser.parseExplanationJsonSafe(
      """
        {
          "conciseSummary": "Amlodipine লেখা আছে তবে আজই double the dose দিন।",
          "medications": [
            {"medicationIndex": 0, "drugName": "Amlodipine", "printedScheduleExplanation": "নিজের ইচ্ছায় ডোজ increase করুন।"}
          ]
        }
      """.trimIndent(),
      doc,
      "bn"
    ).getOrThrow()
    assertEquals(LocalizedSafetyResources.stringsFor("bn").safeFallbackSummary, bnMixed.conciseSummary)
    assertNull((bnMixed.categoryExplanation as CategoryExplanation.Clinical).medications[0].printedScheduleExplanation)

    // Marathi with SOV code-switch ("tablet बंद करा") + English phrase
    val mrMixed = MedicalExplanationParser.parseExplanationJsonSafe(
      """
        {
          "conciseSummary": "तुम्ही स्वतःहून Amlodipine tablet बंद करा.",
          "medications": [
            {"medicationIndex": 0, "drugName": "Amlodipine", "printedScheduleExplanation": "You can double the dose जर दबाव जास्त असेल."}
          ]
        }
      """.trimIndent(),
      doc,
      "mr"
    ).getOrThrow()
    assertEquals(LocalizedSafetyResources.stringsFor("mr").safeFallbackSummary, mrMixed.conciseSummary)
    assertNull((mrMixed.categoryExplanation as CategoryExplanation.Clinical).medications[0].printedScheduleExplanation)
  }

  @Test
  fun stage3b_protectiveWarningsArePreservedWhileDandaSeparatedUnsafeSentencesAreRejected() {
    val doc = amlodipineStandardDoc()

    // 1. Legitimate protective warnings must survive cleanly in Hindi, Bengali, and Marathi
    val hiSafeJson = """
      {
        "conciseSummary": "पर्चे में Amlodipine 5 mg दर्ज है। डॉक्टर से पूछे बिना दवा बंद न करें।",
        "medications": [
          {
            "medicationIndex": 0,
            "drugName": "Amlodipine",
            "printedScheduleExplanation": "नाश्ते के बाद दिन में एक बार 5 mg 30 दिनों के लिए लिखा है। दवा बंद करने से पहले हमेशा अपने डॉक्टर से सलाह लें।"
          }
        ]
      }
    """.trimIndent()
    val hiSafe = MedicalExplanationParser.parseExplanationJsonSafe(hiSafeJson, doc, "hi").getOrThrow()
    assertTrue(hiSafe.conciseSummary.contains("डॉक्टर से पूछे बिना दवा बंद न करें।"))
    assertEquals(
      "नाश्ते के बाद दिन में एक बार 5 mg 30 दिनों के लिए लिखा है। दवा बंद करने से पहले हमेशा अपने डॉक्टर से सलाह लें।",
      (hiSafe.categoryExplanation as CategoryExplanation.Clinical).medications[0].printedScheduleExplanation
    )

    val bnSafeJson = """
      {
        "conciseSummary": "প্রেসক্রিপশনে Amlodipine 5 mg উল্লেখ আছে। ডাক্তারের পরামর্শ ছাড়া ওষুধ বন্ধ করবেন না।",
        "medications": [
          {
            "medicationIndex": 0,
            "drugName": "Amlodipine",
            "printedScheduleExplanation": "নাস্তার পর দিনে একবার ৫ মিগ্রা ৩০ দিনের জন্য লেখা আছে। ওষুধ বন্ধ করার আগে ডাক্তারের পরামর্শ নিন।"
          }
        ]
      }
    """.trimIndent()
    val bnSafe = MedicalExplanationParser.parseExplanationJsonSafe(bnSafeJson, doc, "bn").getOrThrow()
    assertTrue(bnSafe.conciseSummary.contains("ডাক্তারের পরামর্শ ছাড়া ওষুধ বন্ধ করবেন না।"))
    assertNotNull((bnSafe.categoryExplanation as CategoryExplanation.Clinical).medications[0].printedScheduleExplanation)

    val mrSafeJson = """
      {
        "conciseSummary": "प्रिस्क्रिप्शनमध्ये Amlodipine 5 mg नोंदवले आहे। डॉक्टरांचा सल्ला घेतल्याशिवाय औषध बंद करू नका.",
        "medications": [
          {
            "medicationIndex": 0,
            "drugName": "Amlodipine",
            "printedScheduleExplanation": "नाश्त्यानंतर दिवसातून एकदा ५ मिग्रॅ ३० दिवसांसाठी लिहिले आहे. औषध बंद करण्यापूर्वी डॉक्टरांचा सल्ला घ्या."
          }
        ]
      }
    """.trimIndent()
    val mrSafe = MedicalExplanationParser.parseExplanationJsonSafe(mrSafeJson, doc, "mr").getOrThrow()
    assertTrue(mrSafe.conciseSummary.contains("डॉक्टरांचा सल्ला घेतल्याशिवाय औषध बंद करू नका."))
    assertNotNull((mrSafe.categoryExplanation as CategoryExplanation.Clinical).medications[0].printedScheduleExplanation)

    // 2. Sentence-boundary isolation across । and ॥ : protective sentence 1 MUST NOT hide unsafe sentence 2!
    val hiDoubleDandaLeak = """
      {
        "conciseSummary": "डॉक्टर से पूछे बिना दवा बंद न करें॥ खुराक दोगुनी करें।"
      }
    """.trimIndent()
    val hiLeakParsed = MedicalExplanationParser.parseExplanationJsonSafe(hiDoubleDandaLeak, doc, "hi").getOrThrow()
    assertEquals(LocalizedSafetyResources.stringsFor("hi").safeFallbackSummary, hiLeakParsed.conciseSummary)

    val bnDandaLeak = """
      {
        "conciseSummary": "ডাক্তারের পরামর্শ ছাড়া ওষুধ বন্ধ করবেন না। ডোজ দ্বিগুণ করুন।"
      }
    """.trimIndent()
    val bnLeakParsed = MedicalExplanationParser.parseExplanationJsonSafe(bnDandaLeak, doc, "bn").getOrThrow()
    assertEquals(LocalizedSafetyResources.stringsFor("bn").safeFallbackSummary, bnLeakParsed.conciseSummary)

    val mrDandaLeak = """
      {
        "conciseSummary": "डॉक्टरांचा सल्ला घेतल्याशिवाय औषध बंद करू नका। डोस दुप्पट करा।"
      }
    """.trimIndent()
    val mrLeakParsed = MedicalExplanationParser.parseExplanationJsonSafe(mrDandaLeak, doc, "mr").getOrThrow()
    assertEquals(LocalizedSafetyResources.stringsFor("mr").safeFallbackSummary, mrLeakParsed.conciseSummary)
  }

  @Test
  fun stage3b_allAmbiguousAbbreviationsBlockedWhenUnexpandedAcrossAllLanguages() {
    val unexpandedMeds = listOf(
      ExtractedMedication("MedOD", "10 mg", "OD", "5d", null, FieldProvenance(1, "MedOD 10 mg OD", 0.9f, ProvenanceAvailability.AVAILABLE)),
      ExtractedMedication("MedQD", "10 mg", "QD", "5d", null, FieldProvenance(1, "MedQD 10 mg QD", 0.9f, ProvenanceAvailability.AVAILABLE)),
      ExtractedMedication("MedBD", "10 mg", "BD", "5d", null, FieldProvenance(1, "MedBD 10 mg BD", 0.9f, ProvenanceAvailability.AVAILABLE)),
      ExtractedMedication("MedBID", "10 mg", "BID", "5d", null, FieldProvenance(1, "MedBID 10 mg BID", 0.9f, ProvenanceAvailability.AVAILABLE)),
      ExtractedMedication("MedTID", "10 mg", "TID", "5d", null, FieldProvenance(1, "MedTID 10 mg TID", 0.9f, ProvenanceAvailability.AVAILABLE)),
      ExtractedMedication("MedTDS", "10 mg", "TDS", "5d", null, FieldProvenance(1, "MedTDS 10 mg TDS", 0.9f, ProvenanceAvailability.AVAILABLE)),
      ExtractedMedication("MedQID", "10 mg", "QID", "5d", null, FieldProvenance(1, "MedQID 10 mg QID", 0.9f, ProvenanceAvailability.AVAILABLE)),
      ExtractedMedication("MedPRN", "10 mg", "PRN", "5d", null, FieldProvenance(1, "MedPRN 10 mg PRN", 0.9f, ProvenanceAvailability.AVAILABLE)),
      ExtractedMedication("MedSOS", "10 mg", "SOS", "5d", null, FieldProvenance(1, "MedSOS 10 mg SOS", 0.9f, ProvenanceAvailability.AVAILABLE)),
      ExtractedMedication("MedHS", "10 mg", "HS", "5d", null, FieldProvenance(1, "MedHS 10 mg HS", 0.9f, ProvenanceAvailability.AVAILABLE)),
      ExtractedMedication("MedQOD", "10 mg", "QOD", "5d", null, FieldProvenance(1, "MedQOD 10 mg QOD", 0.9f, ProvenanceAvailability.AVAILABLE)),
      ExtractedMedication("MedAC", "10 mg", "AC", "5d", null, FieldProvenance(1, "MedAC 10 mg AC", 0.9f, ProvenanceAvailability.AVAILABLE)),
      ExtractedMedication("MedPC", "10 mg", "PC", "5d", null, FieldProvenance(1, "MedPC 10 mg PC", 0.9f, ProvenanceAvailability.AVAILABLE)),
      ExtractedMedication("MedOS", "1 drop", "OS", "5d", null, FieldProvenance(1, "MedOS 1 drop OS", 0.9f, ProvenanceAvailability.AVAILABLE)),
      ExtractedMedication("MedOU", "1 drop", "OU", "5d", null, FieldProvenance(1, "MedOU 1 drop OU", 0.9f, ProvenanceAvailability.AVAILABLE))
    )
    val doc = clinicalDocWithMedications(unexpandedMeds)

    val languageExpansions = listOf(
      "en" to listOf(
        "Take once daily.",
        "Take once per day.",
        "Take twice daily.",
        "Take two times a day.",
        "Take three times daily.",
        "Take thrice a day.",
        "Take four times daily.",
        "Take as needed.",
        "Take only when symptoms appear.",
        "Take at bedtime.",
        "Take every other day.",
        "Take before meals.",
        "Take after meals.",
        "Apply in left eye.",
        "Apply in both eyes."
      ),
      "hi" to listOf(
        "10 mg दिन में एक बार लें।",
        "10 mg रोज़ एक बार लें।",
        "10 mg दिन में दो बार लें।",
        "10 mg सुबह-शाम लें।",
        "10 mg दिन में तीन बार लें।",
        "10 mg हर 8 घंटे में लें।",
        "10 mg दिन में चार बार लें।",
        "10 mg आवश्यकता पड़ने पर लें।",
        "10 mg ज़रूरत होने पर लें।",
        "10 mg सोते समय लें।",
        "10 mg एक दिन छोड़कर लें।",
        "10 mg भोजन से पहले लें।",
        "10 mg भोजन के बाद लें।",
        "1 बूंद बाईं आंख में डालें।",
        "1 बूंद दोनों आंखों में डालें।"
      ),
      "bn" to listOf(
        "১০ মিগ্রা দিনে একবার।",
        "১০ মিগ্রা প্রতিদিন একবার।",
        "১০ মিগ্রা দিনে দুবার।",
        "১০ মিগ্রা সকাল-সন্ধ্যা।",
        "১০ মিগ্রা দিনে তিনবার।",
        "১০ মিগ্রা প্রতি ৮ ঘণ্টা অন্তর।",
        "১০ মিগ্রা দিনে চারবার।",
        "১০ মিগ্রা প্রয়োজন হলে।",
        "১০ মিগ্রা দরকার হলে।",
        "১০ মিগ্রা ঘুমানোর আগে।",
        "১০ মিগ্রা একদিন পরপর।",
        "১০ মিগ্রা খাবারের আগে।",
        "১০ মিগ্রা খাবারের পরে।",
        "১ ফোঁটা বাম চোখে।",
        "১ ফোঁটা দুই চোখে।"
      ),
      "mr" to listOf(
        "१० मिग्रॅ दिवसातून एकदा।",
        "१० मिग्रॅ दररोज एकदा।",
        "१० मिग्रॅ दिवसातून दोनदा।",
        "१० मिग्रॅ सकाळ-संध्याकाळ।",
        "१० मिग्रॅ दिवसातून तीनदा।",
        "१० मिग्रॅ दर ८ तासांनी।",
        "१० मिग्रॅ दिवसातून चारदा।",
        "१० मिग्रॅ गरज पडल्यास।",
        "१० मिग्रॅ आवश्यकता असल्यास।",
        "१० मिग्रॅ झोपण्यापूर्वी।",
        "१० मिग्रॅ एक दिवसाआड।",
        "१० मिग्रॅ जेवणापूर्वी।",
        "१० मिग्रॅ जेवणानंतर।",
        "१ थेंब डाव्या डोळ्यात।",
        "१ थेंब दोन्ही डोळ्यांत।"
      )
    )

    languageExpansions.forEach { (lang, expansions) ->
      val medsJson = expansions.mapIndexed { idx, text ->
        """{"medicationIndex": $idx, "drugName": "${unexpandedMeds[idx].drugName}", "printedScheduleExplanation": "$text"}"""
      }.joinToString(",")
      val json = """{"conciseSummary": "Summary in $lang.", "medications": [$medsJson]}"""
      val parsed = MedicalExplanationParser.parseExplanationJsonSafe(json, doc, lang).getOrThrow()
      val clin = parsed.categoryExplanation as CategoryExplanation.Clinical
      clin.medications.forEachIndexed { idx, med ->
        assertNull(
          "Lang=$lang med=${med.sourceMedication.drugName} (${med.sourceMedication.frequency}) must reject unexpanded expansion",
          med.printedScheduleExplanation
        )
        // Verbatim printed code stays preserved cleanly
        assertEquals(unexpandedMeds[idx].frequency, med.sourceMedication.frequency)
      }
    }

    // Cross-language English expansion leak when language=hi must ALSO be rejected
    val englishLeakInHindi = MedicalExplanationParser.parseExplanationJsonSafe(
      """
        {
          "conciseSummary": "MedBD once twice daily",
          "medications": [
            {"medicationIndex": 2, "drugName": "MedBD", "printedScheduleExplanation": "MedBD 10 mg twice daily."}
          ]
        }
      """.trimIndent(),
      doc,
      "hi"
    ).getOrThrow()
    assertNull((englishLeakInHindi.categoryExplanation as CategoryExplanation.Clinical).medications[2].printedScheduleExplanation)
  }

  @Test
  fun stage3b_sourceExpandedAuthorizesTargetLanguageAndEnforcesPerMedicationIsolation() {
    val isolationDoc = clinicalDocWithMedications(
      listOf(
        // Med 0: QD explicitly expanded in English source -> target-language once-daily explanation allowed ONLY for Med 0
        ExtractedMedication(
          drugName = "Atorvastatin",
          dosage = "10 mg",
          frequency = "QD (once daily)",
          duration = "30 days",
          instructions = null,
          provenance = FieldProvenance(1, "Tab Atorvastatin 10 mg QD (once daily)", 0.94f, ProvenanceAvailability.AVAILABLE)
        ),
        // Med 1: QD unexpanded -> Med 0 must NOT authorize Med 1
        ExtractedMedication(
          drugName = "Lisinopril",
          dosage = "5 mg",
          frequency = "QD",
          duration = "30 days",
          instructions = null,
          provenance = FieldProvenance(1, "Tab Lisinopril 5 mg QD", 0.93f, ProvenanceAvailability.AVAILABLE)
        )
      )
    )

    val payloads = listOf(
      "hi" to Triple("10 mg दिन में एक बार (QD) 30 दिनों के लिए दर्ज है।", "Atorvastatin: 10 mg दिन में एक बार 30 दिनों के लिए दर्ज है।", "5 mg दिन में एक बार 30 दिनों के लिए।"),
      "bn" to Triple("১০ মিগ্রা দিনে একবার (QD) ৩০ দিনের জন্য উল্লেখ আছে।", "Atorvastatin: ১০ মিগ্রা দিনে একবার ৩০ দিনের জন্য উল্লেখ আছে।", "৫ মিগ্রা দিনে একবার ৩০ দিনের জন্য।"),
      "mr" to Triple("१० मिग्रॅ दिवसातून एकदा (QD) ३० दिवसांसाठी नोंदवले आहे।", "Atorvastatin: १० मिग्रॅ दिवसातून एकदा ३० दिवसांसाठी नोंदवले आहे।", "५ मिग्रॅ दिवसातून एकदा ३० दिवसांसाठी।")
    )

    payloads.forEach { (lang, triple) ->
      val (med0Allowed, safeSummary, med1Blocked) = triple
      val json = """
        {
          "conciseSummary": "$safeSummary",
          "medications": [
            {"medicationIndex": 0, "drugName": "Atorvastatin", "printedScheduleExplanation": "$med0Allowed"},
            {"medicationIndex": 1, "drugName": "Lisinopril", "printedScheduleExplanation": "$med1Blocked"}
          ]
        }
      """.trimIndent()
      val parsed = MedicalExplanationParser.parseExplanationJsonSafe(json, isolationDoc, lang).getOrThrow()
      val clin = parsed.categoryExplanation as CategoryExplanation.Clinical
      assertEquals("Source-expanded QD must be allowed for Med 0 in $lang", med0Allowed, clin.medications[0].printedScheduleExplanation)
      assertNull("Unexpanded QD on Med 1 must stay blocked in $lang", clin.medications[1].printedScheduleExplanation)
      assertEquals(safeSummary, parsed.conciseSummary)
    }
  }

  @Test
  fun stage3b_ophthalmicAndHalfStrengthAmbiguitiesRespectSourceEvidenceWithoutGlobalBlocking() {
    val ophthalmicAndHsDoc = clinicalDocWithMedications(
      listOf(
        // Med 0: OD unexpanded -> neither right eye nor once daily allowed
        ExtractedMedication("EyeDropA", "1 drop", "OD", "7 days", null, FieldProvenance(1, "EyeDropA 1 drop OD", 0.91f, ProvenanceAvailability.AVAILABLE)),
        // Med 1: OD explicitly expanded as right eye in source -> right-eye explanation allowed in Hindi/Bengali/Marathi!
        ExtractedMedication("EyeDropB", "1 drop", "OD (right eye)", "7 days", null, FieldProvenance(1, "EyeDropB 1 drop OD (right eye)", 0.95f, ProvenanceAvailability.AVAILABLE)),
        // Med 2: HS unexpanded -> bedtime AND half-strength expansions both blocked
        ExtractedMedication("DrugHS", "5 mg", "HS", "14 days", null, FieldProvenance(1, "DrugHS 5 mg HS", 0.90f, ProvenanceAvailability.AVAILABLE)),
        // Med 3: HS explicitly half-strength -> half-strength allowed
        ExtractedMedication("DrugHalf", "5 mg", "HS (half-strength)", "14 days", null, FieldProvenance(1, "DrugHalf 5 mg HS (half-strength)", 0.94f, ProvenanceAvailability.AVAILABLE))
      )
    )

    val hiJson = """
      {
        "conciseSummary": "रिपोर्ट में दाहिनी आंख और बाईं आंख की जांच का उल्लेख भी है।",
        "medications": [
          {"medicationIndex": 0, "drugName": "EyeDropA", "printedScheduleExplanation": "दाहिनी आंख में 1 बूंद डालें।"},
          {"medicationIndex": 1, "drugName": "EyeDropB", "printedScheduleExplanation": "दाहिनी आंख में 1 बूंद 7 दिनों तक लिखा है।"},
          {"medicationIndex": 2, "drugName": "DrugHS", "printedScheduleExplanation": "5 mg आधी ताकत में लिखा है।"},
          {"medicationIndex": 3, "drugName": "DrugHalf", "printedScheduleExplanation": "5 mg आधी ताकत (HS) 14 दिनों के लिए लिखा है।"}
        ]
      }
    """.trimIndent()
    val hiParsed = MedicalExplanationParser.parseExplanationJsonSafe(hiJson, ophthalmicAndHsDoc, "hi").getOrThrow()
    val hiClin = hiParsed.categoryExplanation as CategoryExplanation.Clinical
    // Ophthalmic words in general narrative are NOT globally blocked when not tied to an unexpanded drug
    assertEquals("रिपोर्ट में दाहिनी आंख और बाईं आंख की जांच का उल्लेख भी है।", hiParsed.conciseSummary)
    assertNull(hiClin.medications[0].printedScheduleExplanation)
    assertNotNull(hiClin.medications[1].printedScheduleExplanation)
    assertNull(hiClin.medications[2].printedScheduleExplanation)
    assertNotNull(hiClin.medications[3].printedScheduleExplanation)

    // Non-medication Radiology report mentioning eyes must remain preserved across hi, bn, mr
    val orbitRad = DocumentExtractionResult(
      category = MedicalDocumentCategory.RADIOLOGY,
      pageCount = 1,
      isMultiPageBatch = false,
      report = UniversalMedicalReport.Radiology(
        ExtractedRadiologyReport(
          modality = "MRI",
          anatomicalRegion = "Orbits",
          clinicalIndication = "Vision check",
          techniqueDescription = "Orbital MRI",
          anatomicalFindings = listOf(
            AnatomicalFinding("Right eye", "Normal globe", FindingStatus.NORMAL, FieldProvenance(1, "Right eye normal", 0.95f, ProvenanceAvailability.AVAILABLE))
          ),
          formalImpressions = listOf(
            RadiologyImpression("Normal orbits.", FindingStatus.NORMAL, FieldProvenance(1, "Normal orbits", 0.96f, ProvenanceAvailability.AVAILABLE))
          )
        )
      )
    )
    listOf(
      "hi" to "दाहिनी आंख की संरचना सामान्य है।",
      "bn" to "ডান চোখ স্বাভাবিক দেখানো হয়েছে।",
      "mr" to "उजवा डोळा सामान्य असल्याचे नोंदवले आहे."
    ).forEach { (lang, eyeFindingText) ->
      val radParsed = MedicalExplanationParser.parseExplanationJsonSafe(
        """{"conciseSummary": "$eyeFindingText", "findings": [{"findingIndex": 0, "anatomicalSite": "Right eye", "plainLanguageExplanation": "$eyeFindingText"}]}""",
        orbitRad,
        lang
      ).getOrThrow()
      assertEquals(eyeFindingText, radParsed.conciseSummary)
      assertEquals(
        eyeFindingText,
        (radParsed.categoryExplanation as CategoryExplanation.Radiology).findings[0].plainLanguageExplanation
      )
    }
  }

  @Test
  fun stage3b_unrelatedNightMealAndHarmlessPurposePhrasesArePreservedInAllLanguages() {
    val docWithHsAndAc = clinicalDocWithMedications(
      medications = listOf(
        ExtractedMedication("Zolpidem", "5 mg", "HS", "7 days", null, FieldProvenance(1, "Tab Zolpidem 5 mg HS", 0.91f, ProvenanceAvailability.AVAILABLE)),
        ExtractedMedication("Pantoprazole", "40 mg", "AC", "14 days", null, FieldProvenance(1, "Tab Pantoprazole 40 mg AC", 0.92f, ProvenanceAvailability.AVAILABLE))
      ),
      diagnoses = listOf(
        ExtractedClinicalFact("Nocturnal cough and pre-meal epigastric discomfort", FieldProvenance(1, "Cough at night; discomfort before meals", 0.92f, ProvenanceAvailability.AVAILABLE))
      ),
      dischargeInstructions = listOf(
        ExtractedClinicalFact("Monitor night-time cough and record meals", FieldProvenance(1, "Monitor night cough", 0.90f, ProvenanceAvailability.AVAILABLE))
      )
    )

    // Harmless clinical narratives about night cough, meal symptoms, and benign "के लिए"/"জন্য"/"साठी" info sentences MUST survive
    val cases = listOf(
      Triple(
        "hi",
        "यह सरल विवरण आपकी जानकारी के लिए है। रात में खांसी और दो दवाओं की सूची दर्ज है।",
        Pair("रात में खांसी के लक्षण और भोजन से पहले पेट दर्द दर्ज है।", "यह निर्देश आपकी समीक्षा के लिए है कि रात में खांसी पर ध्यान दें।")
      ),
      Triple(
        "bn",
        "এই সহজ বিবরণটি আপনার তথ্যের জন্য। রাতে কাশি এবং দুটি ওষুধের তালিকা উল্লেখ আছে।",
        Pair("রাতে কাশি এবং খাবারের আগে পেটের অস্বস্তি উল্লেখ আছে।", "আপনার পর্যালোচনার জন্য রাতে কাশির লক্ষণ লক্ষ্য রাখুন।")
      ),
      Triple(
        "mr",
        "हा सोपा तपशील तुमच्या माहितीसाठी आहे. रात्री खोकला आणि दोन औषधांची यादी नोंदवली आहे.",
        Pair("रात्री खोकला आणि जेवणापूर्वी अस्वस्थता नोंदवली आहे.", "तुमच्या संदर्भासाठी रात्री होणाऱ्या खोकल्याची नोंद ठेवा.")
      )
    )

    cases.forEach { (lang, summary, pair) ->
      val (diagText, instrText) = pair
      val json = """
        {
          "conciseSummary": "$summary",
          "documentOverviewExplanation": "$diagText",
          "diagnoses": [{"diagnosisIndex": 0, "plainLanguageExplanation": "$diagText"}],
          "medications": [
            {"medicationIndex": 0, "drugName": "Zolpidem", "printedScheduleExplanation": "5 mg HS 7 days।"},
            {"medicationIndex": 1, "drugName": "Pantoprazole", "printedScheduleExplanation": "40 mg AC 14 days।"}
          ],
          "dischargeInstructions": [{"instructionIndex": 0, "plainLanguageExplanation": "$instrText"}]
        }
      """.trimIndent()
      val parsed = MedicalExplanationParser.parseExplanationJsonSafe(json, docWithHsAndAc, lang).getOrThrow()
      val clin = parsed.categoryExplanation as CategoryExplanation.Clinical
      assertEquals("Harmless summary with night/purpose in $lang must be preserved", summary, parsed.conciseSummary)
      assertEquals(diagText, clin.documentOverviewExplanation)
      assertEquals(diagText, clin.diagnoses[0].plainLanguageExplanation)
      assertEquals(instrText, clin.dischargeInstructions[0].plainLanguageExplanation)
    }
  }

  @Test
  fun stage3b_localizedFallbackUncertaintyMissingConclusionsDisclaimerAndImmutableTask3CFacts() {
    val uncertainLab = DocumentExtractionResult(
      category = MedicalDocumentCategory.LABORATORY,
      pageCount = 1,
      isMultiPageBatch = false,
      report = UniversalMedicalReport.Laboratory(
        ExtractedLaboratoryReport(
          labFacilityName = "City Diagnostics",
          collectionDate = "2026-11-12",
          reportDate = "2026-11-13",
          items = listOf(
            ExtractedLabItem(
              originalName = "Hemoglobin",
              originalValue = "10.8",
              originalUnit = "g/dL",
              referenceRange = "12.0 - 15.5",
              laboratoryFlag = "L",
              findingStatus = FindingStatus.ABNORMAL,
              provenance = FieldProvenance(1, "Hemoglobin 10.8 g/dL (12.0 - 15.5) L", 0.96f, ProvenanceAvailability.AVAILABLE)
            ),
            ExtractedLabItem(
              originalName = null,
              originalValue = "blurred",
              originalUnit = null,
              referenceRange = null,
              laboratoryFlag = null,
              findingStatus = FindingStatus.UNCERTAIN_OR_UNREADABLE,
              provenance = FieldProvenance(1, "blurred text", 0.30f, ProvenanceAvailability.AVAILABLE)
            )
          )
        )
      )
    )

    val cardioMissingConclusion = DocumentExtractionResult(
      category = MedicalDocumentCategory.CARDIOLOGY,
      pageCount = 1,
      isMultiPageBatch = false,
      report = UniversalMedicalReport.Cardiology(
        ExtractedCardiologyReport(
          modality = "12-Lead ECG",
          heartRateBpm = "74 bpm",
          rhythmInterpretation = null,
          prIntervalMs = null,
          qrsDurationMs = null,
          qtcIntervalMs = null,
          ejectionFractionPercent = null,
          wallMotionFindings = null,
          formalConclusion = null,
          findingStatus = FindingStatus.NOT_MENTIONED,
          provenance = FieldProvenance(1, "HR 74 bpm", 0.91f, ProvenanceAvailability.AVAILABLE)
        )
      )
    )

    val specialisedMissingConclusion = DocumentExtractionResult(
      category = MedicalDocumentCategory.SPECIALISED,
      pageCount = 1,
      isMultiPageBatch = false,
      report = UniversalMedicalReport.Specialised(
        ExtractedSpecialisedReport(
          reportSubtype = "Biopsy",
          specimenSource = "Left thyroid nodule",
          clinicalHistory = null,
          grossDescription = null,
          microscopicDescription = null,
          gradingOrStaging = null,
          diagnosticConclusion = null,
          findingStatus = FindingStatus.UNCERTAIN_OR_UNREADABLE,
          provenance = FieldProvenance(1, "Specimen: Left thyroid nodule", 0.72f, ProvenanceAvailability.AVAILABLE)
        )
      )
    )

    listOf("hi" to "Hindi", "bn" to "Bengali", "mr" to "Marathi").forEach { (code, canonical) ->
      val expectedStrings = LocalizedSafetyResources.stringsFor(code)

      // 1. Lab: fallback summary + uncertain note + localized disclaimer + untouched Task 3C facts
      val labParsed = MedicalExplanationParser.parseExplanationJsonSafe(
        """{"conciseSummary": ""}""",
        uncertainLab,
        language = code
      ).getOrThrow()
      assertEquals(canonical, labParsed.language)
      assertEquals(expectedStrings.safeFallbackSummary, labParsed.conciseSummary)
      assertEquals(expectedStrings.medicalExplanationDisclaimer, labParsed.safetyDisclaimer)
      val labItems = (labParsed.categoryExplanation as CategoryExplanation.Laboratory).items
      assertEquals("10.8", labItems[0].sourceItem.originalValue)
      assertEquals("g/dL", labItems[0].sourceItem.originalUnit)
      assertEquals("12.0 - 15.5", labItems[0].sourceItem.referenceRange)
      assertEquals("L", labItems[0].sourceItem.laboratoryFlag)
      assertEquals(FindingStatus.UNCERTAIN_OR_UNREADABLE, labItems[1].sourceItem.findingStatus)
      assertEquals(expectedStrings.uncertainStatusNote, labItems[1].uncertaintyNote)
      assertTrue(
        labParsed.uncertaintyNotes.contains("${expectedStrings.itemLabel(2)}: ${expectedStrings.uncertainStatusNote}")
      )

      // 2. Cardiology missing conclusion + NOT_MENTIONED prefix
      val cardioParsed = MedicalExplanationParser.parseExplanationJsonSafe(
        """{"conciseSummary": "ECG 74 bpm."}""",
        cardioMissingConclusion,
        language = code
      ).getOrThrow()
      val cardioCat = cardioParsed.categoryExplanation as CategoryExplanation.Cardiology
      assertEquals(expectedStrings.cardiologyConclusionNotReported, cardioCat.uncertaintyNote)
      assertTrue(
        cardioParsed.uncertaintyNotes.contains("${expectedStrings.cardiologyReportLabel}: ${expectedStrings.notMentionedStatusNote}")
      )
      assertTrue(cardioParsed.uncertaintyNotes.contains(expectedStrings.cardiologyConclusionNotReported))
      assertEquals(expectedStrings.cardiologyMetricLabel("heartRateBpm", "Heart Rate"), cardioCat.metrics[0].label)

      // 2b. Specialised uncertain/missing conclusion + localized section title + immutable originalText
      val specParsed = MedicalExplanationParser.parseExplanationJsonSafe(
        """{"conciseSummary": "Biopsy summary."}""",
        specialisedMissingConclusion,
        language = code
      ).getOrThrow()
      val specCat = specParsed.categoryExplanation as CategoryExplanation.Specialised
      assertEquals(expectedStrings.uncertainStatusNote, specCat.uncertaintyNote)
      assertTrue(
        specParsed.uncertaintyNotes.contains("${expectedStrings.specialisedReportLabel}: ${expectedStrings.uncertainStatusNote}")
      )
      assertEquals(expectedStrings.specialisedSectionLabel("specimenSource", "Specimen Source"), specCat.sections[0].sectionTitle)
      assertEquals("Left thyroid nodule", specCat.sections[0].originalText)

      // 3. Clinical safetyNote localization + immutable Task 3C medication fields
      val clinParsed = MedicalExplanationParser.parseExplanationJsonSafe(
        """{"conciseSummary": "Summary."}""",
        amlodipineStandardDoc(),
        language = code
      ).getOrThrow()
      val clinMed = (clinParsed.categoryExplanation as CategoryExplanation.Clinical).medications[0]
      assertEquals(expectedStrings.medicationSafetyNote, clinMed.safetyNote)
      assertEquals("Amlodipine", clinMed.sourceMedication.drugName)
      assertEquals("5 mg", clinMed.sourceMedication.dosage)
      assertEquals("Once daily", clinMed.sourceMedication.frequency)
      assertEquals("30 days", clinMed.sourceMedication.duration)
      assertEquals("Take after breakfast", clinMed.sourceMedication.instructions)
      assertEquals("Tab Amlodipine 5 mg once daily x 30d", clinMed.sourceMedication.provenance.sourceSnippet)
    }

    // 4. Unsupported language ("ta") safely falls back to English disclaimer, fallback summary, and safety rules
    val taFallback = MedicalExplanationParser.parseExplanationJsonSafe(
      """{"conciseSummary": "You can stop taking this medicine immediately."}""",
      amlodipineStandardDoc(),
      language = "ta"
    ).getOrThrow()
    val enStrings = LocalizedSafetyResources.stringsFor("en")
    assertEquals("English", taFallback.language)
    assertEquals(enStrings.safeFallbackSummary, taFallback.conciseSummary)
    assertEquals(ExplanationConstants.MEDICAL_EXPLANATION_DISCLAIMER, taFallback.safetyDisclaimer)
    assertEquals(
      ExplanationConstants.MEDICATION_SAFETY_NOTE,
      (taFallback.categoryExplanation as CategoryExplanation.Clinical).medications[0].safetyNote
    )
  }
}
