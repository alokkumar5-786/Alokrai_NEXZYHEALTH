package com.example.ui.auth

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.data.repository.FirebaseAuthRepository
import com.example.data.repository.FirestoreUserRepository
import com.example.domain.repository.AuthRepository
import com.example.domain.repository.UserRepository
import com.example.model.UserProfile
import com.google.firebase.auth.FirebaseUser
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class EmailLinkUiState(
  val isLoading: Boolean = false,
  val isSuccess: Boolean = false,
  val requiresEmailInput: Boolean = false,
  val emailInput: String = "",
  val pendingLink: String? = null,
  val errorMessage: String? = null,
  val authenticatedUser: FirebaseUser? = null,
  val userProfile: UserProfile? = null
)

class EmailLinkViewModel @JvmOverloads constructor(
  application: Application,
  private val authRepository: AuthRepository = FirebaseAuthRepository(application),
  private val userRepository: UserRepository = FirestoreUserRepository()
) : AndroidViewModel(application) {

  companion object {
    private const val TAG = "EmailLinkVM"

    fun provideFactory(
      application: Application,
      authRepository: AuthRepository = FirebaseAuthRepository(application),
      userRepository: UserRepository = FirestoreUserRepository()
    ): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
      @Suppress("UNCHECKED_CAST")
      override fun <T : ViewModel> create(modelClass: Class<T>): T {
        return EmailLinkViewModel(application, authRepository, userRepository) as T
      }
    }
  }

  private val _uiState = MutableStateFlow(EmailLinkUiState())
  val uiState: StateFlow<EmailLinkUiState> = _uiState.asStateFlow()

  fun onEmailInputChanged(email: String) {
    _uiState.update { it.copy(emailInput = email, errorMessage = null) }
  }

  /**
   * Processes an incoming deep link from email.
   * If a pending email was previously saved on this device, it completes sign-in automatically.
   * If the pending email is absent (e.g., opened on a different device), prompts user to confirm their email.
   */
  fun processIncomingLink(link: String) {
    if (_uiState.value.isLoading || (_uiState.value.isSuccess && _uiState.value.pendingLink == link)) {
      Log.d(TAG, "Email-link sign-in already in progress or completed.")
      return
    }

    if (!authRepository.isSignInWithEmailLink(link)) {
      Log.d(TAG, "Email-link validation failed.")
      return
    }

    val pendingEmail = authRepository.getPendingEmail()
    if (pendingEmail.isNullOrBlank()) {
      Log.w(TAG, "Pending email missing from local storage; prompting user for email confirmation.")
      _uiState.update {
        it.copy(
          requiresEmailInput = true,
          pendingLink = link,
          isLoading = false,
          errorMessage = null
        )
      }
    } else {
      authenticateWithEmailAndLink(pendingEmail, link)
    }
  }

  /**
   * Completes authentication when user manually enters their email after link redirection.
   */
  fun submitManualEmail() {
    val email = _uiState.value.emailInput.trim()
    val link = _uiState.value.pendingLink

    if (email.isEmpty() || !android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
      _uiState.update { it.copy(errorMessage = "Please enter a valid email address.") }
      return
    }

    if (link.isNullOrBlank()) {
      _uiState.update { it.copy(errorMessage = "Sign-in link is missing. Please request a new link.") }
      return
    }

    authenticateWithEmailAndLink(email, link)
  }

  private fun authenticateWithEmailAndLink(email: String, link: String) {
    viewModelScope.launch {
      _uiState.update {
        it.copy(
          isLoading = true,
          errorMessage = null,
          requiresEmailInput = false
        )
      }

      val result = authRepository.signInWithEmailLink(email, link)
      result.fold(
        onSuccess = { user ->
          Log.i(TAG, "Email-link authentication succeeded.")
          // Ensure Firestore profile exists or is updated
          val profile = syncUserProfile(user, email)
          _uiState.update {
            it.copy(
              isLoading = false,
              isSuccess = true,
              authenticatedUser = user,
              userProfile = profile,
              errorMessage = null
            )
          }
        },
        onFailure = { error ->
          Log.e(TAG, "Email-link authentication failed: ${error.javaClass.simpleName}")
          _uiState.update {
            it.copy(
              isLoading = false,
              isSuccess = false,
              errorMessage = error.localizedMessage ?: "Failed to verify email link. It may have expired or already been used."
            )
          }
        }
      )
    }
  }

  private suspend fun syncUserProfile(user: FirebaseUser, fallbackEmail: String): UserProfile {
    val existingProfileResult = userRepository.getUserProfile(user.uid)
    val existing = existingProfileResult.getOrNull()

    val profile = if (existing != null) {
      existing.copy(
        email = user.email ?: fallbackEmail,
        emailVerified = true,
        displayName = if (existing.displayName.isNotBlank()) existing.displayName else (user.displayName ?: ""),
        updatedAt = System.currentTimeMillis()
      )
    } else {
      UserProfile(
        uid = user.uid,
        email = user.email ?: fallbackEmail,
        emailVerified = true,
        phoneNumber = "",
        phoneVerified = false,
        displayName = user.displayName ?: fallbackEmail.substringBefore("@").replaceFirstChar { it.uppercase() },
        createdAt = System.currentTimeMillis(),
        updatedAt = System.currentTimeMillis(),
        accountStatus = "ACTIVE",
        preferredLanguage = "en",
        preferredLanguageSelected = false
      )
    }

    userRepository.createOrUpdateUserProfile(profile)
    return profile
  }

  fun clearError() {
    _uiState.update { it.copy(errorMessage = null) }
  }

  fun reset() {
    _uiState.value = EmailLinkUiState()
  }
}
