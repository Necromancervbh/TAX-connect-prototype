package com.example.taxconnect.features.auth

import android.content.Intent
import android.os.Bundle
import android.text.TextUtils
import android.util.Patterns
import android.view.LayoutInflater
import com.example.taxconnect.R
import com.example.taxconnect.core.base.BaseActivity
import com.example.taxconnect.data.repositories.DataRepository
import com.example.taxconnect.data.repositories.DataRepository.DataCallback
import com.example.taxconnect.databinding.ActivityLoginBinding
import com.example.taxconnect.data.models.UserModel
import com.example.taxconnect.features.dashboard.CADashboardActivity
import com.example.taxconnect.features.home.HomeActivity
import com.example.taxconnect.features.home.MainActivity
import com.example.taxconnect.core.network.NetworkUtils

class LoginActivity : BaseActivity<ActivityLoginBinding>() {

    override val bindingInflater: (LayoutInflater) -> ActivityLoginBinding = ActivityLoginBinding::inflate
    private lateinit var repository: DataRepository

    override fun initViews() {
        repository = DataRepository.getInstance()
    }

    override fun observeViewModel() {
        // No ViewModel to observe
    }

    override fun setupListeners() {
        binding.btnLogin.setOnClickListener { loginUser() }

        binding.tvRegisterLink.setOnClickListener {
            val intent = Intent(this@LoginActivity, MainActivity::class.java)
            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
            startActivity(intent)
        }
    }

    private fun loginUser() {
        if (!NetworkUtils.isNetworkAvailable(this)) {
            showToast(getString(R.string.no_internet))
            return
        }

        val email = binding.etEmail.text.toString().trim()
        val password = binding.etPassword.text.toString().trim()

        if (TextUtils.isEmpty(email)) {
            binding.etEmail.error = getString(R.string.email_required)
            binding.etEmail.requestFocus()
            return
        }

        if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            binding.etEmail.error = getString(R.string.invalid_email)
            binding.etEmail.requestFocus()
            return
        }

        if (TextUtils.isEmpty(password)) {
            binding.etPassword.error = getString(R.string.password_required)
            binding.etPassword.requestFocus()
            return
        }

        if (password.length < 6) {
            binding.etPassword.error = getString(R.string.password_min_length)
            binding.etPassword.requestFocus()
            return
        }

        binding.btnLogin.isEnabled = false
        binding.btnLogin.text = getString(R.string.logging_in)

        repository.loginUser(email, password, object : DataCallback<UserModel> {
            override fun onSuccess(data: UserModel?) {
                showToast(getString(R.string.login_successful))
                if (data != null) {
                    navigateToDashboard(data.role)
                }
            }

            override fun onError(error: String?) {
                binding.btnLogin.isEnabled = true
                binding.btnLogin.text = getString(R.string.login)
                showToast(error ?: "Login failed")
            }
        })
    }

    private fun navigateToDashboard(role: String?) {
        val intent = if ("CA".equals(role, ignoreCase = true)) {
            Intent(this, CADashboardActivity::class.java)
        } else {
            Intent(this, HomeActivity::class.java)
        }
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        startActivity(intent)
    }
}
