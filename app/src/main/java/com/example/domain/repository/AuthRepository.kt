package com.example.domain.repository

import com.google.firebase.auth.FirebaseUser
import kotlinx.coroutines.flow.Flow

/**
 * Domain-level contract encapsulating authentication logic,
 * focusing on Firebase passwordless email-link sign-in, session state, and persistence.
 */
interface AuthRepository {

  /**
   * Sends a secure, passwordless sign-in link to the provided email address.
   *
   * @param email The target email address.
   * @return [Result.success] if the link was dispatched, [Result.failure] with diagnostic details otherwise.
   */
  suspend fun sendEmailSignInLink(email: String): Result<Unit>

  /**
   * Validates whether an incoming deep link or URI corresponds to a valid Firebase email sign-in link.
   *
   * @param link The incoming URL string.
   */
  fun isSignInWithEmailLink(link: String): Boolean

  /**
   * Completes passwordless authentication using the verified email and the incoming sign-in link.
   *
   * @param email The email address used to request the link.
   * @param link The incoming authentication link.
   * @return [Result.success] containing the authenticated [FirebaseUser], or [Result.failure].
   */
  suspend fun signInWithEmailLink(email: String, link: String): Result<FirebaseUser>

  /**
   * Persists the email address locally so it can be automatically retrieved
   * when the user returns to the app via the email link.
   */
  fun savePendingEmail(email: String)

  /**
   * Retrieves the locally saved pending email address.
   */
  fun getPendingEmail(): String?

  /**
   * Clears the pending email after successful verification or cancellation.
   */
  fun clearPendingEmail()

  /**
   * Returns the currently authenticated [FirebaseUser], or null if unauthenticated.
   */
  fun getCurrentUser(): FirebaseUser?

  /**
   * Returns true if a user session is actively authenticated and valid.
   */
  fun isUserAuthenticated(): Boolean

  /**
   * Reactive stream emitting the current [FirebaseUser] whenever authentication state changes.
   */
  fun getAuthStateFlow(): Flow<FirebaseUser?>

  /**
   * Logs out the current user session and clears cached session data.
   */
  fun signOut()

  /**
   * Initiates Firebase Phone Verification for the specified E.164 phone number.
   * Leverages Android SMS Retriever for automatic OTP detection without SMS permissions.
   *
   * @param activity Hosting Activity required for Play Services phone verification.
   * @param phoneNumber Destination phone number in E.164 format (+91XXXXXXXXXX).
   * @param resendToken Optional force resending token for cooldown re-requests.
   * @param callbacks Callback handler for verification lifecycle events.
   */
  fun verifyPhoneNumber(
    activity: android.app.Activity,
    phoneNumber: String,
    resendToken: com.google.firebase.auth.PhoneAuthProvider.ForceResendingToken? = null,
    callbacks: PhoneVerificationCallbacks
  )

  /**
   * Links a verified PhoneAuthCredential to the currently authenticated email account.
   * Preserves the existing Firebase UID.
   *
   * @param credential The verified phone authentication credential.
   * @return [Result.success] containing the user with linked phone, or [Result.failure].
   */
  suspend fun linkPhoneCredential(credential: com.google.firebase.auth.PhoneAuthCredential): Result<FirebaseUser>

  /**
   * Creates a PhoneAuthCredential from a verification ID and SMS OTP code.
   */
  fun createPhoneCredential(
    verificationId: String,
    smsCode: String
  ): com.google.firebase.auth.PhoneAuthCredential
}

/**
 * Callbacks for Firebase Phone Verification lifecycle events.
 */
interface PhoneVerificationCallbacks {
  fun onVerificationCompleted(credential: com.google.firebase.auth.PhoneAuthCredential)
  fun onVerificationFailed(exception: Exception)
  fun onCodeSent(
    verificationId: String,
    resendToken: com.google.firebase.auth.PhoneAuthProvider.ForceResendingToken
  )
  fun onCodeAutoRetrievalTimeOut(verificationId: String)
}
