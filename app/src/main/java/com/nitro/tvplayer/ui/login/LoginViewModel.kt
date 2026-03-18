package com.nitro.tvplayer.ui.login

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nitro.tvplayer.data.repository.AuthRepository
import com.nitro.tvplayer.utils.formatServerUrl
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
            val cleanUrl = serverUrl.formatServerUrl()
            val result = authRepository.authenticate(cleanUrl, username, password)
            _loginState.value = result.fold(
                onSuccess = { LoginState.Success(it) },
                onFailure = { LoginState.Error(it.message ?: "Login failed") }
            )
        }
    }

    fun resetState() { _loginState.value = LoginState.Idle }
}
