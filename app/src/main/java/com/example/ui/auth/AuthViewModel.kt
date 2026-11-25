package com.example.ui.auth

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.data.repository.FirebaseAuthRepository
import com.example.data.repository.FirestoreUserRepository
import com.example.domain.repository.AuthRepository
import com.example.domain.repository.UserRepository
import com.example.model.ExplanationConstants
import com.example.model.UserProfile
import com.google.firebase.auth.FirebaseUser
import java.util.Locale
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed class AuthState {
  object Initial : AuthState()
  object Loading : AuthState()
  data class Authenticated(val user: FirebaseUser, val profile: UserProfile?) : AuthState()
  object Unauthenticated : AuthState()
  data class Error(val message: String) : AuthState()
}

sealed class LanguageUpdateState {
  object Idle : LanguageUpdateState()
  object Saving : LanguageUpdateState()
  data class Success(val languageCode: String) : LanguageUpdateState()
  data class Error(val message: String) : LanguageUpdateState()
}

class AuthViewModel @JvmOverloads constructor(
  application: Application,
  private val authRepository: AuthRepository = FirebaseAuthRepository(application),
  private val userRepository: UserRepository = FirestoreUserRepository()
) : AndroidViewModel(application) {

  companion object {
    fun provideFactory(
      application: Application,
      authRepository: AuthRepository = FirebaseAuthRepository(application),
      userRepository: UserRepository = FirestoreUserRepository()
    ): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
      @Suppress("UNCHECKED_CAST")
      override fun <T : ViewModel> create(modelClass: Class<T>): T {
        return AuthViewModel(application, authRepository, userRepository) as T
      }
    }
  }

  private val _authState = MutableStateFlow<AuthState>(AuthState.Initial)
  val authState: StateFlow<AuthState> = _authState.asStateFlow()

  private val _languageUpdateState = MutableStateFlow<LanguageUpdateState>(LanguageUpdateState.Idle)
  val languageUpdateState: StateFlow<LanguageUpdateState> = _languageUpdateState.asStateFlow()

  val currentUser: FirebaseUser?
    get() = authRepository.getCurrentUser()

  init {
    observeAuthState()
  }

  fun checkSession() {
    viewModelScope.launch {
      _authState.value = AuthState.Loading
      val user = authRepository.getCurrentUser()
      if (user != null) {
        loadUserProfile(user)
      } else {
        _authState.value = AuthState.Unauthenticated
      }
    }
  }

  fun syncAuthenticatedSession(user: FirebaseUser?, profile: UserProfile?) {
    if (user != null) {
      _authState.value = AuthState.Authenticated(user, profile)
    } else {
      checkSession()
    }
  }

  fun refreshProfile() {
    val user = authRepository.getCurrentUser()
    if (user != null) {
      viewModelScope.launch {
        loadUserProfile(user)
      }
    }
  }

  fun resetLanguageUpdateState() {
    _languageUpdateState.value = LanguageUpdateState.Idle
  }

  fun updatePreferredLanguage(languageCode: String) {
    val currentAuth = _authState.value as? AuthState.Authenticated
    val user = currentAuth?.user ?: authRepository.getCurrentUser()
    if (user == null) {
      _languageUpdateState.value = LanguageUpdateState.Error(
        "Please sign in again to save your language preference."
      )
      return
    }

    val normalizedCode = languageCode.trim().lowercase(Locale.ROOT)
    if (normalizedCode !in ExplanationConstants.SUPPORTED_LANGUAGE_CODES) {
      _languageUpdateState.value = LanguageUpdateState.Error(
        "Selected language is not supported yet. Please choose English, Hindi, Bengali, or Marathi."
      )
      return
    }

    viewModelScope.launch {
      _languageUpdateState.value = LanguageUpdateState.Saving
      val existingProfile = (_authState.value as? AuthState.Authenticated)?.profile ?: currentAuth?.profile
      val (updatedProfile, nextState) = persistPreferredLanguageForUser(
        uid = user.uid,
        languageCode = normalizedCode,
        existingProfile = existingProfile,
        email = user.email ?: "",
        emailVerified = user.isEmailVerified,
        phoneNumber = user.phoneNumber ?: "",
        phoneVerified = !user.phoneNumber.isNullOrBlank(),
        displayName = user.displayName ?: (user.email?.substringBefore("@")?.replaceFirstChar { it.uppercase() } ?: "User"),
        createdAtFallback = user.metadata?.creationTimestamp ?: System.currentTimeMillis()
      )
      if (updatedProfile != null) {
        _authState.value = AuthState.Authenticated(user, updatedProfile)
      }
      _languageUpdateState.value = nextState
    }
  }

  internal suspend fun persistPreferredLanguageForUser(
    uid: String,
    languageCode: String,
    existingProfile: UserProfile?,
    email: String = existingProfile?.email ?: "",
    emailVerified: Boolean = existingProfile?.emailVerified ?: true,
    phoneNumber: String = existingProfile?.phoneNumber ?: "",
    phoneVerified: Boolean = existingProfile?.phoneVerified ?: false,
    displayName: String = existingProfile?.displayName ?: "",
    createdAtFallback: Long = System.currentTimeMillis()
  ): Pair<UserProfile?, LanguageUpdateState> {
    val normalizedCode = languageCode.trim().lowercase(Locale.ROOT)
    if (uid.isBlank()) {
      return null to LanguageUpdateState.Error("Please sign in again to save your language preference.")
    }
    if (normalizedCode !in ExplanationConstants.SUPPORTED_LANGUAGE_CODES) {
      return null to LanguageUpdateState.Error(
        "Selected language is not supported yet. Please choose English, Hindi, Bengali, or Marathi."
      )
    }

    val result = userRepository.updatePreferredLanguage(uid, normalizedCode)
    return result.fold(
      onSuccess = {
        val now = System.currentTimeMillis()
        val updatedProfile = existingProfile?.copy(
          preferredLanguage = normalizedCode,
          preferredLanguageSelected = true,
          updatedAt = now
        ) ?: UserProfile(
          uid = uid,
          email = email,
          emailVerified = emailVerified,
          phoneNumber = phoneNumber,
          phoneVerified = phoneVerified,
          displayName = displayName.ifBlank { "User" },
          createdAt = createdAtFallback,
          updatedAt = now,
          accountStatus = "ACTIVE",
          preferredLanguage = normalizedCode,
          preferredLanguageSelected = true
        )
        updatedProfile to LanguageUpdateState.Success(normalizedCode)
      },
      onFailure = { error ->
        val message = error.localizedMessage?.takeIf { it.isNotBlank() }
          ?: "Unable to save your language preference. Please check your connection and try again."
        null to LanguageUpdateState.Error(message)
      }
    )
  }

  private fun observeAuthState() {
    viewModelScope.launch {
      // Transition to Loading on startup during session lookup
      if (_authState.value is AuthState.Initial) {
        _authState.value = AuthState.Loading
      }
      authRepository.getAuthStateFlow().collect { user ->
        if (user != null) {
          loadUserProfile(user)
        } else {
          _authState.value = AuthState.Unauthenticated
          _languageUpdateState.value = LanguageUpdateState.Idle
        }
      }
    }
  }

  private suspend fun loadUserProfile(user: FirebaseUser) {
    try {
      val profileResult = userRepository.getUserProfile(user.uid)
      if (profileResult.isSuccess) {
        var profile = profileResult.getOrNull()
        if (profile == null) {
          // Self-heal initial profile only if document does not exist yet
          val fallbackProfile = UserProfile(
            uid = user.uid,
            email = user.email ?: "",
            emailVerified = user.isEmailVerified,
            phoneNumber = user.phoneNumber ?: "",
            phoneVerified = !user.phoneNumber.isNullOrBlank(),
            displayName = user.displayName ?: (user.email?.substringBefore("@")?.replaceFirstChar { it.uppercase() } ?: "User"),
            createdAt = user.metadata?.creationTimestamp ?: System.currentTimeMillis(),
            updatedAt = System.currentTimeMillis(),
            accountStatus = "ACTIVE",
            preferredLanguage = "en",
            preferredLanguageSelected = false
          )
          userRepository.createOrUpdateUserProfile(fallbackProfile)
          profile = fallbackProfile
        }
        _authState.value = AuthState.Authenticated(user, profile)
      } else {
        val error = profileResult.exceptionOrNull()
        android.util.Log.w("AuthViewModel", "User profile retrieval failed: ${error?.javaClass?.simpleName}")
        // Preserve authenticated state on network/transient failure with existing or fallback profile
        val currentProfile = (_authState.value as? AuthState.Authenticated)?.profile
        _authState.value = AuthState.Authenticated(user, currentProfile)
      }
    } catch (e: Exception) {
      android.util.Log.e("AuthViewModel", "User profile load failed: ${e.javaClass.simpleName}")
      val currentProfile = (_authState.value as? AuthState.Authenticated)?.profile
      _authState.value = AuthState.Authenticated(user, currentProfile)
    }
  }

  fun signOut() {
    viewModelScope.launch {
      _authState.value = AuthState.Loading
      _languageUpdateState.value = LanguageUpdateState.Idle
      try {
        authRepository.signOut()
      } catch (e: Exception) {
        android.util.Log.e("AuthViewModel", "User sign-out failed: ${e.javaClass.simpleName}")
      } finally {
        _authState.value = AuthState.Unauthenticated
      }
    }
  }
}
