package com.example.ui.auth

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.data.repository.FirebaseAuthRepository
import com.example.domain.repository.AuthRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class EmailLoginUiState(
  val email: String = "",
  val isValidEmail: Boolean = false,
  val isLoading: Boolean = false,
  val isLinkSent: Boolean = false,
  val sentToEmail: String = "",
  val errorMessage: String? = null,
  val cooldownSeconds: Int = 0
)

class EmailLoginViewModel @JvmOverloads constructor(
  application: Application,
  private val authRepository: AuthRepository = FirebaseAuthRepository(application)
) : AndroidViewModel(application) {

  companion object {
    fun provideFactory(
      application: Application,
      authRepository: AuthRepository = FirebaseAuthRepository(application)
    ): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
      @Suppress("UNCHECKED_CAST")
      override fun <T : ViewModel> create(modelClass: Class<T>): T {
        return EmailLoginViewModel(application, authRepository) as T
      }
    }
  }

  private val _uiState = MutableStateFlow(EmailLoginUiState())
  val uiState: StateFlow<EmailLoginUiState> = _uiState.asStateFlow()

  private var timerJob: Job? = null

  fun onEmailChanged(newEmail: String) {
    val clean = newEmail.trim()
    val isValid = android.util.Patterns.EMAIL_ADDRESS.matcher(clean).matches()
    _uiState.update {
      it.copy(
        email = newEmail,
        isValidEmail = isValid,
        errorMessage = null
      )
    }
  }

  fun sendSignInLink() {
    val currentEmail = _uiState.value.email.trim()
    if (!_uiState.value.isValidEmail || currentEmail.isEmpty()) {
      _uiState.update { it.copy(errorMessage = "Please enter a valid email address.") }
      return
    }

    viewModelScope.launch {
      _uiState.update { it.copy(isLoading = true, errorMessage = null) }
      val result = authRepository.sendEmailSignInLink(currentEmail)
      result.fold(
        onSuccess = {
          _uiState.update {
            it.copy(
              isLoading = false,
              isLinkSent = true,
              sentToEmail = currentEmail,
              errorMessage = null
            )
          }
          startCooldownTimer()
        },
        onFailure = { error ->
          _uiState.update {
            it.copy(
              isLoading = false,
              errorMessage = error.localizedMessage ?: "Failed to send sign-in link. Please verify your internet connection."
            )
          }
        }
      )
    }
  }

  fun resendSignInLink() {
    if (_uiState.value.cooldownSeconds > 0 || _uiState.value.isLoading) return
    val targetEmail = _uiState.value.sentToEmail.ifBlank { _uiState.value.email.trim() }
    if (targetEmail.isEmpty()) return

    viewModelScope.launch {
      _uiState.update { it.copy(isLoading = true, errorMessage = null) }
      val result = authRepository.sendEmailSignInLink(targetEmail)
      result.fold(
        onSuccess = {
          _uiState.update {
            it.copy(
              isLoading = false,
              errorMessage = null
            )
          }
          startCooldownTimer()
        },
        onFailure = { error ->
          _uiState.update {
            it.copy(
              isLoading = false,
              errorMessage = error.localizedMessage ?: "Failed to resend sign-in link."
            )
          }
        }
      )
    }
  }

  private fun startCooldownTimer(seconds: Int = 60) {
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

  fun reset() {
    timerJob?.cancel()
    _uiState.value = EmailLoginUiState()
  }

  override fun onCleared() {
    super.onCleared()
    timerJob?.cancel()
  }
}
