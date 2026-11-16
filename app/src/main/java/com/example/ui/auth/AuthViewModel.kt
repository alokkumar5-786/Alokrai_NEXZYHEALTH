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
import com.example.model.UserProfile
import com.google.firebase.auth.FirebaseUser
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

  fun refreshProfile() {
    val user = authRepository.getCurrentUser()
    if (user != null) {
      viewModelScope.launch {
        loadUserProfile(user)
      }
    }
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
            preferredLanguage = "en"
          )
          userRepository.createOrUpdateUserProfile(fallbackProfile)
          profile = fallbackProfile
        }
        _authState.value = AuthState.Authenticated(user, profile)
      } else {
        val error = profileResult.exceptionOrNull()
        android.util.Log.w("AuthViewModel", "Failed to retrieve user profile: ${error?.message}")
        // Preserve authenticated state on network/transient failure with existing or fallback profile
        val currentProfile = (_authState.value as? AuthState.Authenticated)?.profile
        _authState.value = AuthState.Authenticated(user, currentProfile)
      }
    } catch (e: Exception) {
      android.util.Log.e("AuthViewModel", "Exception loading user profile: ${e.message}", e)
      val currentProfile = (_authState.value as? AuthState.Authenticated)?.profile
      _authState.value = AuthState.Authenticated(user, currentProfile)
    }
  }

  fun signOut() {
    viewModelScope.launch {
      _authState.value = AuthState.Loading
      try {
        authRepository.signOut()
      } catch (e: Exception) {
        android.util.Log.e("AuthViewModel", "Error during signOut: ${e.message}", e)
      } finally {
        _authState.value = AuthState.Unauthenticated
      }
    }
  }
}
