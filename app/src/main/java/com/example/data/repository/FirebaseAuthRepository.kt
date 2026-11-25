package com.example.data.repository

import android.app.Activity
import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import com.example.domain.repository.AuthRepository
import com.example.domain.repository.PhoneVerificationCallbacks
import com.example.firebase.FirebaseManager
import com.google.android.gms.tasks.Task
import com.google.firebase.FirebaseException
import com.google.firebase.auth.ActionCodeSettings
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthActionCodeException
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException
import com.google.firebase.auth.FirebaseAuthUserCollisionException
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.PhoneAuthCredential
import com.google.firebase.auth.PhoneAuthOptions
import com.google.firebase.auth.PhoneAuthProvider
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

class FirebaseAuthRepository(
  private val context: Context,
  firebaseAuthInstance: FirebaseAuth? = null
) : AuthRepository {

  private val firebaseAuth: FirebaseAuth by lazy {
    FirebaseManager.ensureInitialized(context)
    firebaseAuthInstance ?: FirebaseManager.auth
  }

  companion object {
    private const val TAG = "NexzyAuthRepo"
    private const val PREFS_NAME = "nexzy_auth_prefs"
    private const val KEY_PENDING_EMAIL = "pending_email_for_signin"
    // Authorized continuation URL (where user lands if opened in desktop browser or app not installed)
    private const val CONTINUATION_URL = "https://nexzyhealth.firebaseapp.com"
    // Firebase Hosting domain handling email links on path /__/auth/links
    private const val HOSTING_LINK_DOMAIN = "nexzyhealth.firebaseapp.com"
    private const val PACKAGE_NAME = "com.aistudio.nexzyhealth.kznvh"
  }

  private val sharedPreferences: SharedPreferences by lazy {
    context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
  }

  private val actionCodeSettings: ActionCodeSettings by lazy {
    ActionCodeSettings.newBuilder()
      .setUrl(CONTINUATION_URL)
      .setHandleCodeInApp(true)
      .setAndroidPackageName(
        PACKAGE_NAME,
        /* installIfNotAvailable = */ true,
        /* minimumVersion = */ "1"
      )
      .setLinkDomain(HOSTING_LINK_DOMAIN)
      .build()
  }

  override suspend fun sendEmailSignInLink(email: String): Result<Unit> = withContext(Dispatchers.IO) {
    val cleanEmail = email.trim()
    if (!android.util.Patterns.EMAIL_ADDRESS.matcher(cleanEmail).matches()) {
      return@withContext Result.failure(IllegalArgumentException("Please enter a valid email address."))
    }

    try {
      Log.i(TAG, "Sending passwordless sign-in link started.")
      firebaseAuth.sendSignInLinkToEmail(cleanEmail, actionCodeSettings).awaitTask()
      savePendingEmail(cleanEmail)
      Log.i(TAG, "Passwordless sign-in link sent.")
      Result.success(Unit)
    } catch (e: Exception) {
      Log.e(TAG, "Sending passwordless sign-in link failed: ${e.javaClass.simpleName}")
      val userMessage = when (e) {
        is FirebaseAuthInvalidCredentialsException -> "The email address is improperly formatted."
        else -> e.localizedMessage ?: "Failed to send email verification link. Please check your network connection."
      }
      Result.failure(Exception(userMessage, e))
    }
  }

  override fun isSignInWithEmailLink(link: String): Boolean {
    val isLink = firebaseAuth.isSignInWithEmailLink(link)
    Log.d(TAG, "Email-link validation checked: valid=$isLink")
    return isLink
  }

  override suspend fun signInWithEmailLink(email: String, link: String): Result<FirebaseUser> = withContext(Dispatchers.IO) {
    val cleanEmail = email.trim()
    if (cleanEmail.isEmpty()) {
      return@withContext Result.failure(IllegalArgumentException("Email cannot be empty."))
    }
    if (!isSignInWithEmailLink(link)) {
      return@withContext Result.failure(IllegalArgumentException("The provided link is not a valid Firebase sign-in link."))
    }

    try {
      Log.i(TAG, "Email-link authentication started.")
      val authResult = firebaseAuth.signInWithEmailLink(cleanEmail, link).awaitTask()
      val user = authResult.user
      if (user != null) {
        clearPendingEmail()
        Log.i(TAG, "Email-link authentication succeeded.")
        Result.success(user)
      } else {
        Result.failure(IllegalStateException("Authentication succeeded but no user object was returned."))
      }
    } catch (e: Exception) {
      Log.e(TAG, "Email-link authentication failed: ${e.javaClass.simpleName}")
      val userMessage = when (e) {
        is FirebaseAuthActionCodeException -> "This sign-in link has expired or has already been used. Please request a new link."
        is FirebaseAuthInvalidCredentialsException -> "Invalid authentication credentials or mismatched email."
        else -> e.localizedMessage ?: "Sign-in failed. Please request a new link and try again."
      }
      Result.failure(Exception(userMessage, e))
    }
  }

  override fun savePendingEmail(email: String) {
    sharedPreferences.edit().putString(KEY_PENDING_EMAIL, email.trim()).apply()
  }

  override fun getPendingEmail(): String? {
    return sharedPreferences.getString(KEY_PENDING_EMAIL, null)
  }

  override fun clearPendingEmail() {
    sharedPreferences.edit().remove(KEY_PENDING_EMAIL).apply()
  }

  override fun getCurrentUser(): FirebaseUser? {
    return firebaseAuth.currentUser
  }

  override fun isUserAuthenticated(): Boolean {
    return firebaseAuth.currentUser != null
  }

  override fun getAuthStateFlow(): Flow<FirebaseUser?> = callbackFlow {
    val listener = FirebaseAuth.AuthStateListener { auth ->
      trySend(auth.currentUser)
    }
    firebaseAuth.addAuthStateListener(listener)
    awaitClose {
      firebaseAuth.removeAuthStateListener(listener)
    }
  }

  override fun signOut() {
    try {
      Log.i(TAG, "User sign-out started.")
      firebaseAuth.signOut()
      clearPendingEmail()
    } catch (e: Exception) {
      Log.e(TAG, "User sign-out failed: ${e.javaClass.simpleName}")
    }
  }

  override fun verifyPhoneNumber(
    activity: Activity,
    phoneNumber: String,
    resendToken: PhoneAuthProvider.ForceResendingToken?,
    callbacks: PhoneVerificationCallbacks
  ) {
    Log.i(TAG, "Phone verification started.")

    val optionsBuilder = PhoneAuthOptions.newBuilder(firebaseAuth)
      .setPhoneNumber(phoneNumber)
      .setTimeout(60L, TimeUnit.SECONDS)
      .setActivity(activity)
      .setCallbacks(object : PhoneAuthProvider.OnVerificationStateChangedCallbacks() {
        override fun onVerificationCompleted(credential: PhoneAuthCredential) {
          Log.i(TAG, "Automatic verification completed via SMS Retriever")
          callbacks.onVerificationCompleted(credential)
        }

        override fun onVerificationFailed(e: FirebaseException) {
          Log.e(TAG, "Phone verification failed: ${e.javaClass.simpleName}")
          callbacks.onVerificationFailed(e)
        }

        override fun onCodeSent(verificationId: String, token: PhoneAuthProvider.ForceResendingToken) {
          Log.i(TAG, "SMS OTP dispatched successfully")
          callbacks.onCodeSent(verificationId, token)
        }

        override fun onCodeAutoRetrievalTimeOut(verificationId: String) {
          Log.d(TAG, "SMS auto-retrieval timed out.")
          callbacks.onCodeAutoRetrievalTimeOut(verificationId)
        }
      })

    if (resendToken != null) {
      optionsBuilder.setForceResendingToken(resendToken)
    }

    PhoneAuthProvider.verifyPhoneNumber(optionsBuilder.build())
  }

  override suspend fun linkPhoneCredential(credential: PhoneAuthCredential): Result<FirebaseUser> = withContext(Dispatchers.IO) {
    val currentUser = firebaseAuth.currentUser
      ?: return@withContext Result.failure(IllegalStateException("No authenticated email session. Please sign in with email first."))

    try {
      Log.i(TAG, "Phone credential linking started.")
      val authResult = currentUser.linkWithCredential(credential).awaitTask()
      val user = authResult.user ?: currentUser
      Log.i(TAG, "Phone credential linking succeeded.")
      Result.success(user)
    } catch (e: Exception) {
      Log.e(TAG, "Phone credential linking failed: ${e.javaClass.simpleName}")
      val userMessage = when (e) {
        is FirebaseAuthUserCollisionException ->
          "This mobile number is already linked to another NexzyHealth account."
        is FirebaseAuthInvalidCredentialsException ->
          "The entered OTP verification code is invalid or has expired."
        else -> e.localizedMessage ?: "Phone verification failed. Please try again."
      }
      Result.failure(Exception(userMessage, e))
    }
  }

  override fun createPhoneCredential(
    verificationId: String,
    smsCode: String
  ): PhoneAuthCredential {
    return PhoneAuthProvider.getCredential(verificationId, smsCode.trim())
  }
}

/**
 * Awaits completion of a Play Services [Task] without blocking, handling coroutine cancellation.
 */
suspend fun <T> Task<T>.awaitTask(): T = suspendCancellableCoroutine { continuation ->
  addOnSuccessListener { result ->
    continuation.resume(result)
  }
  addOnFailureListener { exception ->
    continuation.resumeWithException(exception)
  }
}
