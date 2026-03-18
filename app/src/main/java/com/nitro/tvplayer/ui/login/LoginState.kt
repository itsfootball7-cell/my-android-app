package com.nitro.tvplayer.ui.login

import com.nitro.tvplayer.data.model.UserInfo

sealed class LoginState {
    object Idle    : LoginState()
    object Loading : LoginState()
    data class Success(val userInfo: UserInfo) : LoginState()
    data class Error(val message: String)      : LoginState()
}
