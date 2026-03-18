package com.nitro.tvplayer.ui.login

import android.content.Intent
import android.content.pm.ActivityInfo
import android.os.Bundle
import android.view.View
import android.view.animation.AnimationUtils
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.nitro.tvplayer.R
import com.nitro.tvplayer.databinding.ActivityLoginBinding
import com.nitro.tvplayer.ui.home.HomeActivity
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@AndroidEntryPoint
class LoginActivity : AppCompatActivity() {

    private lateinit var binding: ActivityLoginBinding
    private val viewModel: LoginViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        binding = ActivityLoginBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setupUI()
        observeState()
    }

    private fun setupUI() {
        binding.btnLogin.setOnClickListener {
            val url  = binding.etServerUrl.text.toString().trim()
            val user = binding.etUsername.text.toString().trim()
            val pass = binding.etPassword.text.toString().trim()
            if (url.isEmpty() || user.isEmpty() || pass.isEmpty()) {
                showError("Please fill in all fields")
                return@setOnClickListener
            }
            binding.tvError.visibility = View.GONE
            viewModel.login(url, user, pass)
        }
    }

    private fun observeState() {
        lifecycleScope.launch {
            viewModel.loginState.collect { state ->
                when (state) {
                    is LoginState.Loading -> setLoading(true)
                    is LoginState.Success -> onSuccess()
                    is LoginState.Error   -> { setLoading(false); showError(state.message) }
                    else -> Unit
                }
            }
        }
    }

    private fun onSuccess() {
        binding.btnLogin.text = "✓ Connected!"
        binding.progressBar.visibility = View.GONE
        lifecycleScope.launch {
            delay(600)
            requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
            delay(500)
            startActivity(
                Intent(this@LoginActivity, HomeActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                }
            )
            overridePendingTransition(R.anim.slide_in_right, R.anim.fade_out)
            finish()
        }
    }

    private fun setLoading(show: Boolean) {
        binding.progressBar.visibility = if (show) View.VISIBLE else View.GONE
        binding.btnLogin.isEnabled = !show
        binding.btnLogin.text = if (show) "Connecting..." else "Sign In"
    }

    private fun showError(msg: String) {
        binding.tvError.text = msg
        binding.tvError.visibility = View.VISIBLE
        binding.cardLogin.startAnimation(AnimationUtils.loadAnimation(this, R.anim.shake))
    }
}
