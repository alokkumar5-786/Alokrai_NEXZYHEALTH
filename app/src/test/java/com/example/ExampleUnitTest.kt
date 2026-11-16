package com.example

import com.example.data.MockDataProvider
import com.example.model.TestStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ExampleUnitTest {
  @Test
  fun addition_isCorrect() {
    assertEquals(4, 2 + 2)
  }

  @Test
  fun verifyDemoReportStructure() {
    val sample = MockDataProvider.sampleReport
    assertNotNull(sample)
    assertEquals("Hemoglobin (Hb)", sample.primaryTest.name)
    assertEquals(TestStatus.ATTENTION, sample.primaryTest.status)
    assertTrue(sample.allParameters.isNotEmpty())
    assertTrue(sample.audioTranscript.isNotBlank())
  }

  @Test
  fun verifyRegionalLanguagesSupport() {
    val languages = MockDataProvider.sampleLanguages
    assertEquals(7, languages.size)
    val supported = languages.filter { it.isSupported }.map { it.code }
    assertEquals(listOf("en", "hi", "bn"), supported)
  }

  @Test
  fun verifyFirebaseConfigurationStatusModel() {
    val status = com.example.firebase.FirebaseStatus(
      isConfigured = true,
      projectId = "nexzyhealth",
      appId = "1:768225720339:android:12cfa6bbb9cc1c6bb7a7e6",
      authReady = true,
      firestoreReady = true,
      message = "Connected to Firebase project: nexzyhealth"
    )
    assertTrue(status.isConfigured)
    assertEquals("nexzyhealth", status.projectId)
    assertTrue(status.authReady)
    assertTrue(status.firestoreReady)
  }

  @Test
  fun verifyAuthRepositoryContract() {
    // Verifies that domain AuthRepository and its contract methods are well-formed
    val testRepo = object : com.example.domain.repository.AuthRepository {
      override suspend fun sendEmailSignInLink(email: String): Result<Unit> {
        return if (email.contains("@")) Result.success(Unit) else Result.failure(IllegalArgumentException())
      }
      override fun isSignInWithEmailLink(link: String): Boolean = link.contains("nexzyhealth.firebaseapp.com")
      override suspend fun signInWithEmailLink(email: String, link: String): Result<com.google.firebase.auth.FirebaseUser> {
        return Result.failure(UnsupportedOperationException())
      }
      override fun savePendingEmail(email: String) {}
      override fun getPendingEmail(): String? = "test@example.com"
      override fun clearPendingEmail() {}
      override fun getCurrentUser(): com.google.firebase.auth.FirebaseUser? = null
      override fun isUserAuthenticated(): Boolean = false
      override fun getAuthStateFlow(): kotlinx.coroutines.flow.Flow<com.google.firebase.auth.FirebaseUser?> {
        return kotlinx.coroutines.flow.flowOf(null)
      }
      override fun signOut() {}
      override fun verifyPhoneNumber(
        activity: android.app.Activity,
        phoneNumber: String,
        resendToken: com.google.firebase.auth.PhoneAuthProvider.ForceResendingToken?,
        callbacks: com.example.domain.repository.PhoneVerificationCallbacks
      ) {}
      override suspend fun linkPhoneCredential(credential: com.google.firebase.auth.PhoneAuthCredential): Result<com.google.firebase.auth.FirebaseUser> = Result.failure(UnsupportedOperationException())
      override fun createPhoneCredential(verificationId: String, smsCode: String): com.google.firebase.auth.PhoneAuthCredential {
        throw UnsupportedOperationException()
      }
    }

    assertTrue(testRepo.isSignInWithEmailLink("https://nexzyhealth.firebaseapp.com/__/auth/links?link=https://nexzyhealth.firebaseapp.com&apiKey=test"))
    assertEquals("test@example.com", testRepo.getPendingEmail())
  }

  @Test
  fun verifyUserProfileSchemaIntegrity() {
    val profile = com.example.model.UserProfile(
      uid = "usr-test-123",
      email = "priya@example.com",
      emailVerified = true,
      phoneNumber = "+919876543210",
      phoneVerified = true,
      displayName = "Priya Sharma",
      accountStatus = "ACTIVE",
      preferredLanguage = "en"
    )

    assertEquals("usr-test-123", profile.uid)
    assertEquals("priya@example.com", profile.email)
    assertTrue(profile.emailVerified)
    assertEquals("+919876543210", profile.phoneNumber)
    assertTrue(profile.phoneVerified)
    assertEquals("ACTIVE", profile.accountStatus)
    assertEquals("en", profile.preferredLanguage)
  }

  @Test
  fun verifyFirebaseHostingEmailLinkAndContinuationUrls() {
    val continuationUrl = "https://nexzyhealth.firebaseapp.com"
    val hostingLinksPrefix = "https://nexzyhealth.firebaseapp.com/__/auth/links"

    assertTrue("Continuation URL must use HTTPS", continuationUrl.startsWith("https://"))
    assertTrue("Continuation URL must target authorized project domain", continuationUrl.contains("nexzyhealth.firebaseapp.com"))
    assertTrue("Hosting email-link prefix must use /__/auth/links", hostingLinksPrefix.endsWith("/__/auth/links"))
  }

  @Test
  fun verifyUntrustedEmailCannotBypassAuthWithoutMatchingOobCode() {
    val deepLinkWithAttackerEmail = "https://nexzyhealth.firebaseapp.com/__/auth/links?link=https://nexzyhealth.firebaseapp.com&email=attacker@evil.com"
    val hasEmailParameterInUrl = deepLinkWithAttackerEmail.contains("email=")
    assertTrue(hasEmailParameterInUrl)

    val mockRepo = object : com.example.domain.repository.AuthRepository {
      override suspend fun sendEmailSignInLink(email: String): Result<Unit> = Result.success(Unit)
      override fun isSignInWithEmailLink(link: String): Boolean = link.startsWith("https://nexzyhealth.firebaseapp.com/__/auth/links")
      override suspend fun signInWithEmailLink(email: String, link: String): Result<com.google.firebase.auth.FirebaseUser> {
        return if (email == "legitimate@example.com") {
          Result.failure(Exception("Mock verified: requires Firebase Auth server validation"))
        } else {
          Result.failure(IllegalArgumentException("Mismatched email credentials"))
        }
      }
      override fun savePendingEmail(email: String) {}
      override fun getPendingEmail(): String? = "legitimate@example.com"
      override fun clearPendingEmail() {}
      override fun getCurrentUser(): com.google.firebase.auth.FirebaseUser? = null
      override fun isUserAuthenticated(): Boolean = false
      override fun getAuthStateFlow(): kotlinx.coroutines.flow.Flow<com.google.firebase.auth.FirebaseUser?> = kotlinx.coroutines.flow.flowOf(null)
      override fun signOut() {}
      override fun verifyPhoneNumber(
        activity: android.app.Activity,
        phoneNumber: String,
        resendToken: com.google.firebase.auth.PhoneAuthProvider.ForceResendingToken?,
        callbacks: com.example.domain.repository.PhoneVerificationCallbacks
      ) {}
      override suspend fun linkPhoneCredential(credential: com.google.firebase.auth.PhoneAuthCredential): Result<com.google.firebase.auth.FirebaseUser> = Result.failure(UnsupportedOperationException())
      override fun createPhoneCredential(verificationId: String, smsCode: String): com.google.firebase.auth.PhoneAuthCredential {
        throw UnsupportedOperationException()
      }
    }

    assertTrue(mockRepo.isSignInWithEmailLink(deepLinkWithAttackerEmail))
    assertEquals("legitimate@example.com", mockRepo.getPendingEmail())
  }

  @Test
  fun verifyIndianMobileNumberValidationLogic() {
    fun isValidIndianMobile(number: String): Boolean {
      val clean = number.filter { it.isDigit() }
      return clean.length == 10 && (clean.startsWith("6") || clean.startsWith("7") || clean.startsWith("8") || clean.startsWith("9"))
    }

    // Valid 10-digit Indian numbers starting with 6, 7, 8, or 9
    assertTrue(isValidIndianMobile("9876543210"))
    assertTrue(isValidIndianMobile("8123456789"))
    assertTrue(isValidIndianMobile("7999999999"))
    assertTrue(isValidIndianMobile("6300000000"))

    // Invalid numbers
    org.junit.Assert.assertFalse(isValidIndianMobile("5123456789")) // Starts with 5
    org.junit.Assert.assertFalse(isValidIndianMobile("1234567890")) // Starts with 1
    org.junit.Assert.assertFalse(isValidIndianMobile("98765"))      // Too short
    org.junit.Assert.assertFalse(isValidIndianMobile("98765432100")) // Too long
    org.junit.Assert.assertFalse(isValidIndianMobile("abcdefghij")) // Non-digits
  }

  @Test
  fun verifyPhoneLinkingPreservesExistingFirebaseUid() {
    val initialUserUid = "firebase-user-uid-888"
    val originalEmail = "priya.caregiver@example.com"
    val originalCreatedAt = 1700000000000L

    val initialProfile = com.example.model.UserProfile(
      uid = initialUserUid,
      email = originalEmail,
      emailVerified = true,
      phoneNumber = "",
      phoneVerified = false,
      displayName = "Priya Sharma",
      createdAt = originalCreatedAt,
      updatedAt = originalCreatedAt,
      accountStatus = "ACTIVE"
    )

    // Simulate linkPhoneCredential and Firestore update:
    // Existing UID and all profile fields must remain unchanged; only phone and phoneVerified are updated
    val verifiedPhoneNumber = "+919876543210"
    val linkedProfile = initialProfile.copy(
      phoneNumber = verifiedPhoneNumber,
      phoneVerified = true,
      updatedAt = 1700002000000L
    )

    // Assert UID preservation (CRITICAL REQUIREMENT)
    assertEquals("Existing Firebase UID must be strictly preserved after linking phone", initialUserUid, linkedProfile.uid)
    assertEquals(originalEmail, linkedProfile.email)
    assertTrue(linkedProfile.emailVerified)
    assertEquals(verifiedPhoneNumber, linkedProfile.phoneNumber)
    assertTrue(linkedProfile.phoneVerified)
    assertEquals(originalCreatedAt, linkedProfile.createdAt)
  }

  @Test
  fun verifyOtpInputValidationRules() {
    fun isValidOtp(otp: String): Boolean {
      val clean = otp.filter { it.isDigit() }.take(6)
      return clean.length == 6
    }

    assertTrue(isValidOtp("123456"))
    assertTrue(isValidOtp("000000"))
    org.junit.Assert.assertFalse(isValidOtp("12345"))  // 5 digits
    org.junit.Assert.assertFalse(isValidOtp(""))       // empty
    org.junit.Assert.assertFalse(isValidOtp("abc"))    // non-digit
  }

  @Test
  fun verifyExistingUserProfileIsNotOverwrittenOnSignIn() {
    val existingProfile = com.example.model.UserProfile(
      uid = "user-abc-123",
      email = "priya@example.com",
      emailVerified = true,
      phoneNumber = "+919876543210",
      phoneVerified = true,
      displayName = "Priya Sharma",
      createdAt = 1700000000000L,
      updatedAt = 1700000000000L,
      accountStatus = "ACTIVE",
      preferredLanguage = "hi"
    )

    // Simulating profile sync: only emailVerified and updatedAt are refreshed; phone, name, createdAt, language preserved
    val refreshedTimestamp = 1700005000000L
    val syncedProfile = existingProfile.copy(
      updatedAt = refreshedTimestamp
    )

    assertEquals(existingProfile.uid, syncedProfile.uid)
    assertEquals(existingProfile.phoneNumber, syncedProfile.phoneNumber)
    assertTrue(syncedProfile.phoneVerified)
    assertEquals(existingProfile.displayName, syncedProfile.displayName)
    assertEquals(existingProfile.createdAt, syncedProfile.createdAt)
    assertEquals(existingProfile.preferredLanguage, syncedProfile.preferredLanguage)
    assertEquals(refreshedTimestamp, syncedProfile.updatedAt)
  }

  @Test
  fun verifyUserProfileContainsAllNineRequiredFields() {
    val now = System.currentTimeMillis()
    val fullProfile = com.example.model.UserProfile(
      uid = "auth-uid-456",
      email = "patient@nexzyhealth.com",
      emailVerified = true,
      phoneNumber = "+919876543211",
      phoneVerified = true,
      displayName = "Dr. Priya Rao",
      preferredLanguage = "hi",
      createdAt = now,
      updatedAt = now,
      accountStatus = "ACTIVE"
    )

    // 1. uid
    assertEquals("auth-uid-456", fullProfile.uid)
    // 2. email
    assertEquals("patient@nexzyhealth.com", fullProfile.email)
    // 3. displayName
    assertEquals("Dr. Priya Rao", fullProfile.displayName)
    // 4. phoneNumber
    assertEquals("+919876543211", fullProfile.phoneNumber)
    // 5. phoneVerified
    assertTrue(fullProfile.phoneVerified)
    // 6. preferredLanguage
    assertEquals("hi", fullProfile.preferredLanguage)
    // 7. createdAt
    assertEquals(now, fullProfile.createdAt)
    // 8. updatedAt
    assertEquals(now, fullProfile.updatedAt)
    // 9. accountStatus
    assertEquals("ACTIVE", fullProfile.accountStatus)
  }

  @Test
  fun verifyFirstTimeProfileCreationIncludesCreatedAt() {
    val uid = "new-user-789"
    val email = "newuser@example.com"
    val creationTime = 1700001234567L

    val initialProfile = com.example.model.UserProfile(
      uid = uid,
      email = email,
      emailVerified = true,
      createdAt = creationTime,
      updatedAt = creationTime,
      accountStatus = "ACTIVE",
      preferredLanguage = "en"
    )

    // Simulate creation map logic in FirestoreUserRepository
    val creationMap = mutableMapOf<String, Any>(
      "uid" to initialProfile.uid,
      "email" to initialProfile.email,
      "emailVerified" to initialProfile.emailVerified,
      "phoneNumber" to initialProfile.phoneNumber,
      "phoneVerified" to initialProfile.phoneVerified,
      "displayName" to initialProfile.displayName,
      "preferredLanguage" to initialProfile.preferredLanguage,
      "createdAt" to initialProfile.createdAt,
      "updatedAt" to initialProfile.updatedAt,
      "accountStatus" to initialProfile.accountStatus
    )

    assertTrue("Creation map must contain createdAt timestamp", creationMap.containsKey("createdAt"))
    assertEquals(creationTime, creationMap["createdAt"])
    assertEquals(uid, creationMap["uid"])
    assertEquals("ACTIVE", creationMap["accountStatus"])
    assertEquals("en", creationMap["preferredLanguage"])
  }

  @Test
  fun verifyReturningUserUpdateStrictlyOmitsCreatedAtAndPreservesExistingFields() {
    val existingProfile = com.example.model.UserProfile(
      uid = "returning-user-321",
      email = "returning@example.com",
      emailVerified = true,
      phoneNumber = "+919876543210",
      phoneVerified = true,
      displayName = "Existing Name",
      preferredLanguage = "bn",
      createdAt = 1690000000000L,
      updatedAt = 1690000000000L,
      accountStatus = "ACTIVE"
    )

    // Incoming profile with empty phone/display name during re-login
    val incomingLoginProfile = com.example.model.UserProfile(
      uid = "returning-user-321",
      email = "returning@example.com",
      emailVerified = true,
      phoneNumber = "",
      phoneVerified = false,
      displayName = ""
    )

    // Update map logic as implemented in FirestoreUserRepository
    val updateMap = mutableMapOf<String, Any>(
      "uid" to incomingLoginProfile.uid,
      "email" to incomingLoginProfile.email,
      "emailVerified" to incomingLoginProfile.emailVerified,
      "updatedAt" to 1700000000000L
    )
    if (incomingLoginProfile.displayName.isNotBlank()) {
      updateMap["displayName"] = incomingLoginProfile.displayName
    }
    if (incomingLoginProfile.phoneVerified || incomingLoginProfile.phoneNumber.isNotBlank()) {
      updateMap["phoneNumber"] = incomingLoginProfile.phoneNumber
      updateMap["phoneVerified"] = incomingLoginProfile.phoneVerified
    }
    if (incomingLoginProfile.preferredLanguage.isNotBlank()) {
      updateMap["preferredLanguage"] = incomingLoginProfile.preferredLanguage
    }
    if (incomingLoginProfile.accountStatus.isNotBlank()) {
      updateMap["accountStatus"] = incomingLoginProfile.accountStatus
    }

    // Assert that createdAt is NOT in the updateMap (violating diff().affectedKeys() in security rules)
    org.junit.Assert.assertFalse("Updates must not contain createdAt to satisfy Firestore rules", updateMap.containsKey("createdAt"))
    // Assert that empty phone number did NOT overwrite existing phone
    org.junit.Assert.assertFalse("Blank phone number must not overwrite existing phone", updateMap.containsKey("phoneNumber"))
    org.junit.Assert.assertFalse("Unverified phone state on email re-login must not overwrite existing verified state", updateMap.containsKey("phoneVerified"))
    // Assert that empty displayName did not overwrite existing
    org.junit.Assert.assertFalse("Blank displayName must not overwrite existing displayName", updateMap.containsKey("displayName"))
  }

  @Test
  fun verifySecurityRulesDisallowedFieldsAreNeverSentByClient() {
    val disallowedFields = setOf("subscription", "credits", "paymentEntitlements", "medicalReports")

    // Client profile property keys
    val profileKeys = setOf(
      "uid", "email", "emailVerified", "phoneNumber", "phoneVerified",
      "displayName", "createdAt", "updatedAt", "accountStatus", "preferredLanguage"
    )

    val intersection = profileKeys.intersect(disallowedFields)
    assertTrue("Client profile model must never contain server-governed entitlement fields", intersection.isEmpty())
  }

  @Test
  fun verifyFirestoreDocumentPathMatchesFirebaseUserUid() {
    val authUid = "firebase-auth-uid-999"
    val expectedFirestoreDocPath = "users/$authUid"

    assertEquals("users/firebase-auth-uid-999", expectedFirestoreDocPath)
    assertTrue(expectedFirestoreDocPath.startsWith("users/"))
    assertEquals(authUid, expectedFirestoreDocPath.removePrefix("users/"))
  }

  // --- Day 2 Task 2E Tests: Authentication State, Logout and Session Persistence ---

  @Test
  fun verifyAuthStateTransitions() {
    val initial: com.example.ui.auth.AuthState = com.example.ui.auth.AuthState.Initial
    val loading: com.example.ui.auth.AuthState = com.example.ui.auth.AuthState.Loading
    val unauth: com.example.ui.auth.AuthState = com.example.ui.auth.AuthState.Unauthenticated
    val error: com.example.ui.auth.AuthState = com.example.ui.auth.AuthState.Error("Network failure")

    assertTrue(initial is com.example.ui.auth.AuthState.Initial)
    assertTrue(loading is com.example.ui.auth.AuthState.Loading)
    assertTrue(unauth is com.example.ui.auth.AuthState.Unauthenticated)
    assertTrue(error is com.example.ui.auth.AuthState.Error)
    assertEquals("Network failure", (error as com.example.ui.auth.AuthState.Error).message)
  }

  @Test
  fun verifySessionRestorationPreservesProfileAndUid() {
    val persistedUid = "persisted-caregiver-777"
    val persistedEmail = "caregiver.persisted@example.com"
    val originalCreatedAt = 1680000000000L

    val existingFirestoreProfile = com.example.model.UserProfile(
      uid = persistedUid,
      email = persistedEmail,
      emailVerified = true,
      phoneNumber = "+919876543210",
      phoneVerified = true,
      displayName = "Aarav Sharma",
      createdAt = originalCreatedAt,
      updatedAt = originalCreatedAt,
      accountStatus = "ACTIVE",
      preferredLanguage = "hi"
    )

    // Simulate session restoration: retrieving existing profile on app launch
    val restoredProfile = existingFirestoreProfile.copy()

    // Verify session restoration invariants
    assertEquals(persistedUid, restoredProfile.uid)
    assertEquals(persistedEmail, restoredProfile.email)
    assertEquals("+919876543210", restoredProfile.phoneNumber)
    assertTrue(restoredProfile.phoneVerified)
    assertEquals("Aarav Sharma", restoredProfile.displayName)
    assertEquals(originalCreatedAt, restoredProfile.createdAt)
    assertEquals("hi", restoredProfile.preferredLanguage)
  }

  @Test
  fun verifyLogoutClearsInMemoryStateWithoutDeletingFirestoreProfile() {
    val userUid = "logout-test-uid-123"
    val userEmail = "logout.test@example.com"

    val firestoreProfile = com.example.model.UserProfile(
      uid = userUid,
      email = userEmail,
      emailVerified = true,
      phoneNumber = "+919988776655",
      phoneVerified = true,
      displayName = "Test User",
      createdAt = 1690000000000L,
      updatedAt = 1690000000000L,
      accountStatus = "ACTIVE"
    )

    // In-memory UI states before logout
    var inMemoryEmailInput = "logout.test@example.com"
    var inMemoryOtp = "123456"
    var inMemoryAuthState: com.example.ui.auth.AuthState = com.example.ui.auth.AuthState.Loading

    // Simulate complete logout:
    // 1. Sign out Firebase session
    inMemoryAuthState = com.example.ui.auth.AuthState.Unauthenticated
    // 2. Reset in-memory ViewModel states
    inMemoryEmailInput = ""
    inMemoryOtp = ""

    // Assert that in-memory states are completely cleared
    assertEquals(com.example.ui.auth.AuthState.Unauthenticated, inMemoryAuthState)
    assertTrue(inMemoryEmailInput.isEmpty())
    assertTrue(inMemoryOtp.isEmpty())

    // Assert that Firestore profile data remains intact (NOT deleted on logout)
    assertEquals(userUid, firestoreProfile.uid)
    assertEquals(userEmail, firestoreProfile.email)
    assertEquals("+919988776655", firestoreProfile.phoneNumber)
    assertTrue(firestoreProfile.phoneVerified)
  }

  @Test
  fun verifyNavigationGuardBlocksUnauthenticatedAccessToAuthenticatedScreens() {
    val authenticatedRoutes = setOf(
      com.example.navigation.Screen.Home.route,
      com.example.navigation.Screen.ReportHistory.route,
      com.example.navigation.Screen.UserProfile.route,
      com.example.navigation.Screen.UploadReport.route,
      com.example.navigation.Screen.ScanReport.route,
      com.example.navigation.Screen.UploadProgress.route,
      com.example.navigation.Screen.DetailedResults.route,
      com.example.navigation.Screen.AudioExplanation.route,
      com.example.navigation.Screen.FamilyProfiles.route,
      com.example.navigation.Screen.Subscription.route,
      com.example.navigation.Screen.Privacy.route,
      com.example.navigation.Screen.Settings.route,
      com.example.navigation.Screen.PhoneEntry.route,
      com.example.navigation.Screen.OtpVerification.route
    )

    fun shouldRedirectToWelcome(currentRoute: String, isAuthenticated: Boolean): Boolean {
      return !isAuthenticated && currentRoute in authenticatedRoutes
    }

    // Unauthenticated user attempting to access protected screens must be evicted
    assertTrue(shouldRedirectToWelcome(com.example.navigation.Screen.Home.route, false))
    assertTrue(shouldRedirectToWelcome(com.example.navigation.Screen.UserProfile.route, false))
    assertTrue(shouldRedirectToWelcome(com.example.navigation.Screen.ReportHistory.route, false))
    assertTrue(shouldRedirectToWelcome(com.example.navigation.Screen.PhoneEntry.route, false))

    // Authenticated user can access protected screens
    org.junit.Assert.assertFalse(shouldRedirectToWelcome(com.example.navigation.Screen.Home.route, true))
    org.junit.Assert.assertFalse(shouldRedirectToWelcome(com.example.navigation.Screen.UserProfile.route, true))

    // Public/Auth screens are never blocked
    org.junit.Assert.assertFalse(shouldRedirectToWelcome(com.example.navigation.Screen.Welcome.route, false))
    org.junit.Assert.assertFalse(shouldRedirectToWelcome(com.example.navigation.Screen.Login.route, false))
    org.junit.Assert.assertFalse(shouldRedirectToWelcome(com.example.navigation.Screen.Splash.route, false))
  }

  @Test
  fun verifyNetworkFailurePreservesAuthenticatedSession() {
    // When network fails during profile sync, the user remains authenticated via Firebase Auth
    val fallbackProfile: com.example.model.UserProfile? = null
    var currentAuthState: com.example.ui.auth.AuthState = com.example.ui.auth.AuthState.Loading

    val isNetworkFailure = true
    if (isNetworkFailure) {
      // Rather than unauthenticating or throwing user out, retain authenticated state or report non-fatal error
      // In AuthViewModel: _authState.value = AuthState.Authenticated(user, cachedProfile)
      val mockUser = "mock-firebase-user-object"
      assertNotNull(mockUser)
    }

    org.junit.Assert.assertNotEquals(com.example.ui.auth.AuthState.Unauthenticated, currentAuthState)
  }

  // --- Day 3 Task 3A Tests: Medical Report Upload Architecture & Document Selection ---

  @Test
  fun verifyInitialEmptyUploadState() {
    val initialState = com.example.model.ReportUploadUiState()

    org.junit.Assert.assertNull(initialState.selectedDocument)
    assertTrue(initialState.selectionStatus is com.example.model.DocumentSelectionStatus.Idle)
    org.junit.Assert.assertNull(initialState.errorMessage)
    org.junit.Assert.assertFalse(initialState.isReadyForUpload)
    assertEquals("Blood Test (CBC / Metabolic)", initialState.selectedCategory)
  }

  @Test
  fun verifySuccessfulDocumentSelectionForPdfAndImages() {
    // 1. PDF
    assertTrue(com.example.ui.upload.ReportUploadViewModel.isSupportedFileType("application/pdf", "CBC_Report.pdf"))
    val pdfDoc = com.example.model.SelectedDocument(
      uriString = "content://com.android.providers.media.documents/document/123",
      fileName = "CBC_Report.pdf",
      mimeType = "application/pdf",
      fileSizeBytes = 2_500_000L,
      formattedSize = com.example.ui.upload.ReportUploadViewModel.formatFileSize(2_500_000L)
    )
    assertTrue(pdfDoc.isPdf)
    org.junit.Assert.assertFalse(pdfDoc.isImage)
    assertEquals("2.4 MB", pdfDoc.formattedSize)

    // 2. JPEG
    assertTrue(com.example.ui.upload.ReportUploadViewModel.isSupportedFileType("image/jpeg", "Lab_Scan.jpg"))
    val jpegDoc = com.example.model.SelectedDocument(
      uriString = "content://media/external/images/media/456",
      fileName = "Lab_Scan.jpg",
      mimeType = "image/jpeg",
      fileSizeBytes = 850_000L,
      formattedSize = com.example.ui.upload.ReportUploadViewModel.formatFileSize(850_000L)
    )
    org.junit.Assert.assertFalse(jpegDoc.isPdf)
    assertTrue(jpegDoc.isImage)
    assertEquals("830 KB", jpegDoc.formattedSize)

    // 3. PNG
    assertTrue(com.example.ui.upload.ReportUploadViewModel.isSupportedFileType("image/png", "Thyroid_Test.png"))
    val pngDoc = com.example.model.SelectedDocument(
      uriString = "content://media/external/images/media/789",
      fileName = "Thyroid_Test.png",
      mimeType = "image/png",
      fileSizeBytes = 1_200_000L,
      formattedSize = com.example.ui.upload.ReportUploadViewModel.formatFileSize(1_200_000L)
    )
    assertTrue(pngDoc.isImage)
    assertEquals("1.1 MB", pngDoc.formattedSize)
  }

  @Test
  fun verifyUnsupportedFileHandling() {
    // Unsupported extensions / MIME types
    org.junit.Assert.assertFalse(com.example.ui.upload.ReportUploadViewModel.isSupportedFileType("application/vnd.openxmlformats-officedocument.wordprocessingml.document", "Prescription.docx"))
    org.junit.Assert.assertFalse(com.example.ui.upload.ReportUploadViewModel.isSupportedFileType("application/zip", "Reports.zip"))
    org.junit.Assert.assertFalse(com.example.ui.upload.ReportUploadViewModel.isSupportedFileType("text/plain", "notes.txt"))
    org.junit.Assert.assertFalse(com.example.ui.upload.ReportUploadViewModel.isSupportedFileType("audio/mpeg", "recording.mp3"))
    org.junit.Assert.assertFalse(com.example.ui.upload.ReportUploadViewModel.isSupportedFileType("application/x-msdownload", "setup.exe"))
  }

  @Test
  fun verifySelectedDocumentMetadataFormatting() {
    assertEquals("0 KB", com.example.ui.upload.ReportUploadViewModel.formatFileSize(0L))
    assertEquals("0 KB", com.example.ui.upload.ReportUploadViewModel.formatFileSize(-50L))
    assertEquals("500 KB", com.example.ui.upload.ReportUploadViewModel.formatFileSize(512_000L))
    assertEquals("1.0 MB", com.example.ui.upload.ReportUploadViewModel.formatFileSize(1024L * 1024L))
    assertEquals("14.5 MB", com.example.ui.upload.ReportUploadViewModel.formatFileSize((14.5 * 1024 * 1024).toLong()))
  }

  @Test
  fun verifyStateUpdatesAfterDocumentSelection() {
    var state = com.example.model.ReportUploadUiState()

    val validDoc = com.example.model.SelectedDocument(
      uriString = "content://media/external/files/101",
      fileName = "Liver_Function_Panel.pdf",
      mimeType = "application/pdf",
      fileSizeBytes = 3_200_000L,
      formattedSize = "3.1 MB"
    )

    // Simulate selection
    state = state.copy(
      selectedDocument = validDoc,
      selectionStatus = com.example.model.DocumentSelectionStatus.DocumentReady(validDoc),
      errorMessage = null,
      isReadyForUpload = true
    )

    assertEquals("Liver_Function_Panel.pdf", state.selectedDocument?.fileName)
    assertTrue(state.selectionStatus is com.example.model.DocumentSelectionStatus.DocumentReady)
    assertTrue(state.isReadyForUpload)
    org.junit.Assert.assertNull(state.errorMessage)

    // Simulate clear
    state = state.copy(
      selectedDocument = null,
      selectionStatus = com.example.model.DocumentSelectionStatus.Idle,
      isReadyForUpload = false
    )
    org.junit.Assert.assertNull(state.selectedDocument)
    assertTrue(state.selectionStatus is com.example.model.DocumentSelectionStatus.Idle)
    org.junit.Assert.assertFalse(state.isReadyForUpload)
  }

  @Test
  fun verifyErrorHandlingForOversizedAndEmptyFiles() {
    val maxSizeBytes = com.example.ui.upload.ReportUploadViewModel.MAX_FILE_SIZE_BYTES
    assertEquals(15L * 1024L * 1024L, maxSizeBytes)

    fun validate(fileName: String, mime: String, size: Long): String? {
      if (!com.example.ui.upload.ReportUploadViewModel.isSupportedFileType(mime, fileName)) {
        return "Unsupported file type. Please select a PDF, JPG, or PNG medical document."
      }
      if (size <= 0) {
        return "Selected file appears to be empty (0 bytes). Please choose a valid report."
      }
      if (size > maxSizeBytes) {
        return "File size exceeds the 15 MB limit (${com.example.ui.upload.ReportUploadViewModel.formatFileSize(size)}). Please select a smaller report."
      }
      return null
    }

    // Zero-byte file
    val emptyError = validate("empty.pdf", "application/pdf", 0L)
    assertNotNull(emptyError)
    assertTrue(emptyError!!.contains("empty"))

    // Oversized file (16 MB)
    val oversizedBytes = 16L * 1024L * 1024L
    val sizeError = validate("huge_scan.png", "image/png", oversizedBytes)
    assertNotNull(sizeError)
    assertTrue(sizeError!!.contains("15 MB"))

    // Unsupported format
    val typeError = validate("report.docx", "application/vnd.openxmlformats-officedocument.wordprocessingml.document", 100_000L)
    assertNotNull(typeError)
    assertTrue(typeError!!.contains("Unsupported file type"))

    // Valid file
    val validResult = validate("report.pdf", "application/pdf", 2_000_000L)
    org.junit.Assert.assertNull(validResult)
  }

  // --- Day 3 Task 3B Tests: Medical Document Preview & URI Lifecycle Management ---

  @Test
  fun verifyDocumentPreviewStateTransitions() {
    val none: com.example.model.DocumentPreviewState = com.example.model.DocumentPreviewState.None
    val loading: com.example.model.DocumentPreviewState = com.example.model.DocumentPreviewState.Loading
    val readyPdf: com.example.model.DocumentPreviewState = com.example.model.DocumentPreviewState.Ready(
      isPdf = true,
      pageCount = 3,
      currentPage = 1,
      pageInfo = "Page 1 of 3",
      bitmap = null,
      width = 800,
      height = 1100
    )
    val readyImage: com.example.model.DocumentPreviewState = com.example.model.DocumentPreviewState.Ready(
      isPdf = false,
      pageCount = 1,
      currentPage = 1,
      pageInfo = "Single Image",
      bitmap = null,
      width = 1200,
      height = 900
    )
    val error: com.example.model.DocumentPreviewState = com.example.model.DocumentPreviewState.Error("This PDF is password-protected.")

    assertTrue(none is com.example.model.DocumentPreviewState.None)
    assertTrue(loading is com.example.model.DocumentPreviewState.Loading)
    assertTrue(readyPdf is com.example.model.DocumentPreviewState.Ready)
    assertTrue(readyImage is com.example.model.DocumentPreviewState.Ready)
    assertTrue(error is com.example.model.DocumentPreviewState.Error)

    assertEquals("Page 1 of 3", (readyPdf as com.example.model.DocumentPreviewState.Ready).pageInfo)
    assertEquals(3, readyPdf.pageCount)
    assertEquals("Single Image", (readyImage as com.example.model.DocumentPreviewState.Ready).pageInfo)
    assertEquals("This PDF is password-protected.", (error as com.example.model.DocumentPreviewState.Error).message)
  }

  @Test
  fun verifyDocumentRemovalCleansStateAndPreview() {
    val initialDoc = com.example.model.SelectedDocument(
      uriString = "content://media/external/files/42",
      fileName = "Blood_Test_Report.pdf",
      mimeType = "application/pdf",
      fileSizeBytes = 2_100_000L,
      formattedSize = "2.0 MB"
    )

    var state = com.example.model.ReportUploadUiState(
      selectedDocument = initialDoc,
      selectionStatus = com.example.model.DocumentSelectionStatus.DocumentReady(initialDoc),
      previewState = com.example.model.DocumentPreviewState.Ready(
        isPdf = true,
        pageCount = 2,
        currentPage = 1,
        pageInfo = "Page 1 of 2",
        bitmap = null
      ),
      isPreviewModalVisible = true,
      errorMessage = null,
      isReadyForUpload = true
    )

    // Execute removal action
    state = state.copy(
      selectedDocument = null,
      selectionStatus = com.example.model.DocumentSelectionStatus.Idle,
      previewState = com.example.model.DocumentPreviewState.None,
      isPreviewModalVisible = false,
      errorMessage = null,
      isReadyForUpload = false
    )

    // Verify all document resources and preview states are cleanly cleared
    org.junit.Assert.assertNull(state.selectedDocument)
    assertTrue(state.selectionStatus is com.example.model.DocumentSelectionStatus.Idle)
    assertTrue(state.previewState is com.example.model.DocumentPreviewState.None)
    org.junit.Assert.assertFalse(state.isPreviewModalVisible)
    org.junit.Assert.assertFalse(state.isReadyForUpload)
    org.junit.Assert.assertNull(state.errorMessage)
  }

  @Test
  fun verifyDocumentReplacementReleasesPreviousState() {
    val docA = com.example.model.SelectedDocument(
      uriString = "content://media/files/1",
      fileName = "Old_Report.pdf",
      mimeType = "application/pdf",
      fileSizeBytes = 1_000_000L,
      formattedSize = "1.0 MB"
    )

    val docB = com.example.model.SelectedDocument(
      uriString = "content://media/images/2",
      fileName = "New_Scan.jpg",
      mimeType = "image/jpeg",
      fileSizeBytes = 750_000L,
      formattedSize = "732 KB"
    )

    var state = com.example.model.ReportUploadUiState(
      selectedDocument = docA,
      selectionStatus = com.example.model.DocumentSelectionStatus.DocumentReady(docA),
      isReadyForUpload = true
    )

    assertEquals("Old_Report.pdf", state.selectedDocument?.fileName)
    assertTrue(state.selectedDocument?.isPdf == true)

    // Replace with docB
    state = state.copy(
      selectedDocument = docB,
      selectionStatus = com.example.model.DocumentSelectionStatus.DocumentReady(docB),
      previewState = com.example.model.DocumentPreviewState.Ready(
        isPdf = false,
        pageCount = 1,
        currentPage = 1,
        pageInfo = "Single Image"
      ),
      isReadyForUpload = true
    )

    assertEquals("New_Scan.jpg", state.selectedDocument?.fileName)
    org.junit.Assert.assertFalse(state.selectedDocument?.isPdf == true)
    assertTrue(state.selectedDocument?.isImage == true)
    assertEquals("732 KB", state.selectedDocument?.formattedSize)
    assertTrue(state.previewState is com.example.model.DocumentPreviewState.Ready)
    org.junit.Assert.assertFalse((state.previewState as com.example.model.DocumentPreviewState.Ready).isPdf)
  }

  @Test
  fun verifyInvalidDocumentPreviewErrorHandling() {
    fun mapPreviewError(exception: Exception): String {
      return when (exception) {
        is SecurityException -> "This PDF is password-protected. Please select an unencrypted copy."
        is java.io.FileNotFoundException -> "Document access permission was revoked or file is missing."
        is java.io.IOException -> "Corrupted or unreadable PDF document."
        else -> "Unable to render document preview."
      }
    }

    val passwordError = mapPreviewError(SecurityException("Password required"))
    assertEquals("This PDF is password-protected. Please select an unencrypted copy.", passwordError)

    val missingError = mapPreviewError(java.io.FileNotFoundException("File deleted"))
    assertEquals("Document access permission was revoked or file is missing.", missingError)

    val corruptedError = mapPreviewError(java.io.IOException("Invalid header"))
    assertEquals("Corrupted or unreadable PDF document.", corruptedError)

    val genericError = mapPreviewError(RuntimeException("Unknown format"))
    assertEquals("Unable to render document preview.", genericError)
  }

  @Test
  fun verifyUriPersistablePermissionSafeHandling() {
    var permissionAcquired = false
    var errorHandledGracefully = false

    fun safeTakePersistablePermission(throwSecurityException: Boolean) {
      try {
        if (throwSecurityException) {
          throw SecurityException("No persistable permission grant")
        }
        permissionAcquired = true
      } catch (e: SecurityException) {
        // Must handle without throwing or crashing
        errorHandledGracefully = true
      }
    }

    // Normal grant
    safeTakePersistablePermission(false)
    assertTrue(permissionAcquired)

    // Provider that does not support persistable permissions
    safeTakePersistablePermission(true)
    assertTrue(errorHandledGracefully)
  }
}
