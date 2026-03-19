package com.nitro.tvplayer.ui.login

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nitro.tvplayer.data.repository.AuthRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class LoginViewModel @Inject constructor(
    private val authRepository: AuthRepository
) : ViewModel() {

    private val _loginState = MutableStateFlow<LoginState>(LoginState.Idle)
    val loginState: StateFlow<LoginState> = _loginState

    fun login(serverUrl: String, username: String, password: String) {
        if (_loginState.value is LoginState.Loading) return
        viewModelScope.launch {
            _loginState.value = LoginState.Loading
            try {
                val cleanUrl = formatServerUrl(serverUrl)
                val result   = authRepository.authenticate(cleanUrl, username, password)
                _loginState.value = result.fold(
                    onSuccess = { LoginState.Success(it) },
                    onFailure = { LoginState.Error(it.message ?: "Authentication failed") }
                )
            } catch (e: Exception) {
                _loginState.value = LoginState.Error(
                    when {
                        e.message?.contains("timeout", true) == true          -> "Connection timed out"
                        e.message?.contains("unable to resolve", true) == true -> "Server not found"
                        e.message?.contains("failed to connect", true) == true -> "Cannot connect to server"
                        else -> e.message ?: "Unknown error"
                    }
                )
            }
        }
    }

    private fun formatServerUrl(url: String): String {
        var clean = url.trim()
        if (!clean.startsWith("http://") && !clean.startsWith("https://")) {
            clean = "http://$clean"
        }
        if (clean.endsWith("/")) clean = clean.dropLast(1)
        return clean
    }
}
