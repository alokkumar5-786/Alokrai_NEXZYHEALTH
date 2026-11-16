package com.example.ui.auth

import android.app.Activity
import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.data.repository.FirebaseAuthRepository
import com.example.data.repository.FirestoreUserRepository
import com.example.domain.repository.AuthRepository
import com.example.domain.repository.PhoneVerificationCallbacks
import com.example.domain.repository.UserRepository
import com.google.firebase.FirebaseTooManyRequestsException
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException
import com.google.firebase.auth.FirebaseAuthUserCollisionException
import com.google.firebase.auth.PhoneAuthCredential
import com.google.firebase.auth.PhoneAuthProvider
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class CountryCodeOption(
  val code: String,
  val countryName: String,
  val dialCode: String,
  val flagEmoji: String
)

val SupportedCountryCodes = listOf(
  CountryCodeOption("IN", "India", "+91", "🇮🇳"),
  CountryCodeOption("US", "United States", "+1", "🇺🇸"),
  CountryCodeOption("GB", "United Kingdom", "+44", "🇬🇧"),
  CountryCodeOption("AE", "United Arab Emirates", "+971", "🇦🇪"),
  CountryCodeOption("SG", "Singapore", "+65", "🇸🇬"),
  CountryCodeOption("CA", "Canada", "+1", "🇨🇦"),
  CountryCodeOption("AU", "Australia", "+61", "🇦🇺")
)

sealed class PhoneVerificationStep {
  object EnterPhone : PhoneVerificationStep()
  data class EnterOtp(val formattedPhoneNumber: String) : PhoneVerificationStep()
  data class Success(val formattedPhoneNumber: String) : PhoneVerificationStep()
}

data class PhoneVerificationUiState(
  val step: PhoneVerificationStep = PhoneVerificationStep.EnterPhone,
  val selectedCountry: CountryCodeOption = SupportedCountryCodes[0], // Default: India (+91)
  val rawPhoneNumber: String = "",
  val isValidPhoneNumber: Boolean = false,
  val formattedPhoneNumber: String = "",
  val otpCode: String = "",
  val isValidOtp: Boolean = false,
  val verificationId: String? = null,
  val resendToken: PhoneAuthProvider.ForceResendingToken? = null,
  val isLoading: Boolean = false,
  val isAutoVerifying: Boolean = false,
  val cooldownSeconds: Int = 0,
  val errorMessage: String? = null,
  val statusMessage: String? = null
)

class PhoneVerificationViewModel @JvmOverloads constructor(
  application: Application,
  private val authRepository: AuthRepository = FirebaseAuthRepository(application),
  private val userRepository: UserRepository = FirestoreUserRepository()
) : AndroidViewModel(application) {

  companion object {
    private const val TAG = "NexzyPhoneVM"
    private const val COOLDOWN_DURATION = 60

    fun provideFactory(
      application: Application,
      authRepository: AuthRepository = FirebaseAuthRepository(application),
      userRepository: UserRepository = FirestoreUserRepository()
    ): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
      @Suppress("UNCHECKED_CAST")
      override fun <T : ViewModel> create(modelClass: Class<T>): T {
        return PhoneVerificationViewModel(application, authRepository, userRepository) as T
      }
    }
  }

  private val _uiState = MutableStateFlow(PhoneVerificationUiState())
  val uiState: StateFlow<PhoneVerificationUiState> = _uiState.asStateFlow()

  private var timerJob: Job? = null

  fun onCountrySelected(country: CountryCodeOption) {
    _uiState.update { current ->
      val isValid = validatePhoneNumber(current.rawPhoneNumber, country.dialCode)
      current.copy(
        selectedCountry = country,
        isValidPhoneNumber = isValid,
        formattedPhoneNumber = formatE164(current.rawPhoneNumber, country.dialCode),
        errorMessage = null
      )
    }
  }

  fun onPhoneNumberChanged(newRawNumber: String) {
    // Only allow digits
    val clean = newRawNumber.filter { it.isDigit() }
    _uiState.update { current ->
      val isValid = validatePhoneNumber(clean, current.selectedCountry.dialCode)
      current.copy(
        rawPhoneNumber = clean,
        isValidPhoneNumber = isValid,
        formattedPhoneNumber = formatE164(clean, current.selectedCountry.dialCode),
        errorMessage = null
      )
    }
  }

  fun onOtpCodeChanged(newOtp: String) {
    // 6-digit maximum numeric input
    val clean = newOtp.filter { it.isDigit() }.take(6)
    _uiState.update { current ->
      current.copy(
        otpCode = clean,
        isValidOtp = clean.length == 6,
        errorMessage = null
      )
    }
  }

  /**
   * Initiates sending an SMS verification OTP to the user's mobile number.
   * Leverages Android SMS Retriever for automatic detection where available.
   */
  fun sendVerificationCode(activity: Activity) {
    val currentState = _uiState.value
    if (!currentState.isValidPhoneNumber) {
      _uiState.update { it.copy(errorMessage = getValidationErrorMessage(currentState.selectedCountry.dialCode)) }
      return
    }

    val currentUser = authRepository.getCurrentUser()
    if (currentUser == null) {
      _uiState.update {
        it.copy(errorMessage = "Please complete email authentication before linking a phone number.")
      }
      return
    }

    val targetPhone = currentState.formattedPhoneNumber
    _uiState.update {
      it.copy(
        isLoading = true,
        errorMessage = null,
        statusMessage = "Sending verification code..."
      )
    }

    authRepository.verifyPhoneNumber(
      activity = activity,
      phoneNumber = targetPhone,
      resendToken = null,
      callbacks = object : PhoneVerificationCallbacks {
        override fun onVerificationCompleted(credential: PhoneAuthCredential) {
          Log.i(TAG, "Automatic verification triggered via SMS Retriever")
          handleAutoVerification(credential, targetPhone)
        }

        override fun onVerificationFailed(exception: Exception) {
          Log.e(TAG, "Phone verification failed: ${exception.message}", exception)
          val friendlyMessage = mapFirebasePhoneException(exception)
          _uiState.update {
            it.copy(
              isLoading = false,
              isAutoVerifying = false,
              errorMessage = friendlyMessage,
              statusMessage = null
            )
          }
        }

        override fun onCodeSent(
          verificationId: String,
          resendToken: PhoneAuthProvider.ForceResendingToken
        ) {
          Log.i(TAG, "SMS OTP sent successfully")
          _uiState.update {
            it.copy(
              step = PhoneVerificationStep.EnterOtp(targetPhone),
              verificationId = verificationId,
              resendToken = resendToken,
              isLoading = false,
              errorMessage = null,
              statusMessage = "Verification code sent to $targetPhone"
            )
          }
          startCooldownTimer()
        }

        override fun onCodeAutoRetrievalTimeOut(verificationId: String) {
          Log.d(TAG, "SMS auto retrieval timed out; manual OTP entry active")
          _uiState.update {
            it.copy(
              verificationId = verificationId,
              statusMessage = "Please enter the 6-digit code received via SMS."
            )
          }
        }
      }
    )
  }

  /**
   * Resends verification code with cooldown protection.
   */
  fun resendVerificationCode(activity: Activity) {
    val currentState = _uiState.value
    if (currentState.cooldownSeconds > 0 || currentState.isLoading) return
    val targetPhone = currentState.formattedPhoneNumber
    val resendToken = currentState.resendToken

    _uiState.update {
      it.copy(
        isLoading = true,
        errorMessage = null,
        statusMessage = "Resending verification code..."
      )
    }

    authRepository.verifyPhoneNumber(
      activity = activity,
      phoneNumber = targetPhone,
      resendToken = resendToken,
      callbacks = object : PhoneVerificationCallbacks {
        override fun onVerificationCompleted(credential: PhoneAuthCredential) {
          Log.i(TAG, "Automatic verification completed on resend")
          handleAutoVerification(credential, targetPhone)
        }

        override fun onVerificationFailed(exception: Exception) {
          Log.e(TAG, "Resend verification failed: ${exception.message}", exception)
          _uiState.update {
            it.copy(
              isLoading = false,
              isAutoVerifying = false,
              errorMessage = mapFirebasePhoneException(exception),
              statusMessage = null
            )
          }
        }

        override fun onCodeSent(
          verificationId: String,
          token: PhoneAuthProvider.ForceResendingToken
        ) {
          Log.i(TAG, "Resent SMS OTP successfully")
          _uiState.update {
            it.copy(
              verificationId = verificationId,
              resendToken = token,
              isLoading = false,
              errorMessage = null,
              statusMessage = "New code sent to $targetPhone"
            )
          }
          startCooldownTimer()
        }

        override fun onCodeAutoRetrievalTimeOut(verificationId: String) {
          _uiState.update { it.copy(verificationId = verificationId) }
        }
      }
    )
  }

  /**
   * Manually verifies the 6-digit OTP code entered by the user.
   */
  fun verifyManualOtp() {
    val currentState = _uiState.value
    val verificationId = currentState.verificationId
    val otpCode = currentState.otpCode.trim()

    if (verificationId.isNullOrBlank()) {
      _uiState.update { it.copy(errorMessage = "Verification session expired. Please request a new code.") }
      return
    }

    if (otpCode.length != 6) {
      _uiState.update { it.copy(errorMessage = "Please enter the complete 6-digit verification code.") }
      return
    }

    val credential = authRepository.createPhoneCredential(verificationId, otpCode)
    linkCredentialToAccount(credential, currentState.formattedPhoneNumber)
  }

  /**
   * Handles automatic verification callback from Google Play Services SMS Retriever.
   */
  private fun handleAutoVerification(credential: PhoneAuthCredential, phoneNumber: String) {
    _uiState.update {
      it.copy(
        isAutoVerifying = true,
        isLoading = true,
        statusMessage = "Auto-detected SMS code. Linking phone..."
      )
    }
    linkCredentialToAccount(credential, phoneNumber)
  }

  /**
   * Links the verified PhoneAuthCredential to the existing email-authenticated Firebase account.
   * Preserves the existing user's Firebase UID and synchronizes Firestore users/{uid}.
   */
  private fun linkCredentialToAccount(credential: PhoneAuthCredential, phoneNumber: String) {
    val currentUser = authRepository.getCurrentUser()
    if (currentUser == null) {
      _uiState.update {
        it.copy(
          isLoading = false,
          isAutoVerifying = false,
          errorMessage = "No active user session. Please sign in with email first."
        )
      }
      return
    }

    viewModelScope.launch {
      _uiState.update {
        it.copy(isLoading = true, errorMessage = null)
      }

      val linkResult = authRepository.linkPhoneCredential(credential)
      linkResult.fold(
        onSuccess = { linkedUser ->
          Log.i(TAG, "Phone successfully linked. Existing UID verified: ${linkedUser.uid}")
          // Update Firestore user document with verified phone details
          val firestoreResult = userRepository.updatePhoneVerification(
            uid = linkedUser.uid,
            phoneNumber = phoneNumber,
            isVerified = true
          )

          if (firestoreResult.isSuccess) {
            Log.i(TAG, "Firestore phone verification record updated")
          } else {
            Log.w(TAG, "Firestore update returned failure: ${firestoreResult.exceptionOrNull()?.message}")
          }

          _uiState.update {
            it.copy(
              step = PhoneVerificationStep.Success(phoneNumber),
              isLoading = false,
              isAutoVerifying = false,
              errorMessage = null,
              statusMessage = "Phone number verified successfully!"
            )
          }
        },
        onFailure = { error ->
          Log.e(TAG, "Account linking error: ${error.message}", error)
          val friendly = when (error) {
            is FirebaseAuthUserCollisionException ->
              "This mobile number is already linked to another NexzyHealth account."
            is FirebaseAuthInvalidCredentialsException ->
              "The entered OTP code is incorrect or expired. Please check and try again."
            else -> error.localizedMessage ?: "Verification failed. Please try again."
          }
          _uiState.update {
            it.copy(
              isLoading = false,
              isAutoVerifying = false,
              errorMessage = friendly,
              statusMessage = null
            )
          }
        }
      )
    }
  }

  private fun startCooldownTimer(seconds: Int = COOLDOWN_DURATION) {
    timerJob?.cancel()
    timerJob = viewModelScope.launch {
      var remaining = seconds
      while (remaining > 0) {
        _uiState.update { it.copy(cooldownSeconds = remaining) }
        delay(1000L)
        remaining -= 1
      }
      _uiState.update { it.copy(cooldownSeconds = 0) }
    }
  }

  fun resetToPhoneEntry() {
    timerJob?.cancel()
    _uiState.update {
      it.copy(
        step = PhoneVerificationStep.EnterPhone,
        otpCode = "",
        isValidOtp = false,
        verificationId = null,
        resendToken = null,
        isLoading = false,
        isAutoVerifying = false,
        errorMessage = null,
        statusMessage = null
      )
    }
  }

  fun resetAll() {
    timerJob?.cancel()
    _uiState.value = PhoneVerificationUiState()
  }

  override fun onCleared() {
    super.onCleared()
    timerJob?.cancel()
  }

  private fun validatePhoneNumber(rawNumber: String, dialCode: String): Boolean {
    val clean = rawNumber.filter { it.isDigit() }
    return if (dialCode == "+91") {
      // Indian mobile numbers: exactly 10 digits starting with 6, 7, 8, or 9
      clean.length == 10 && (clean.startsWith("6") || clean.startsWith("7") || clean.startsWith("8") || clean.startsWith("9"))
    } else {
      clean.length in 7..15
    }
  }

  private fun formatE164(rawNumber: String, dialCode: String): String {
    val clean = rawNumber.filter { it.isDigit() }
    return if (clean.isEmpty()) "" else "$dialCode$clean"
  }

  private fun getValidationErrorMessage(dialCode: String): String {
    return if (dialCode == "+91") {
      "Please enter a valid 10-digit Indian mobile number starting with 6, 7, 8, or 9."
    } else {
      "Please enter a valid phone number with 7 to 15 digits."
    }
  }

  private fun mapFirebasePhoneException(e: Exception): String {
    return when (e) {
      is FirebaseAuthInvalidCredentialsException ->
        "Invalid phone number format. Please check the country code and digits."
      is FirebaseTooManyRequestsException ->
        "Too many SMS verification attempts. Please wait a few minutes before trying again."
      is FirebaseAuthUserCollisionException ->
        "This mobile number is already linked to another account."
      else -> e.localizedMessage ?: "Failed to send verification SMS. Please check your network connection."
    }
  }
}
