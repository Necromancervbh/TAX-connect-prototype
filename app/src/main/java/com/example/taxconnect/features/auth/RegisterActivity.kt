package com.example.taxconnect.features.auth

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.text.TextUtils
import android.util.Patterns
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.ImageView
import android.view.LayoutInflater
import androidx.activity.result.contract.ActivityResultContracts
import com.example.taxconnect.R
import com.example.taxconnect.core.base.BaseActivity
import com.example.taxconnect.data.repositories.DataRepository
import com.example.taxconnect.data.repositories.DataRepository.DataCallback
import com.example.taxconnect.databinding.ActivityRegisterBinding
import com.example.taxconnect.data.models.UserModel
import com.example.taxconnect.data.services.CloudinaryHelper
import com.example.taxconnect.core.network.NetworkUtils
import com.example.taxconnect.features.dashboard.CADashboardActivity
import com.example.taxconnect.features.home.HomeActivity

class RegisterActivity : BaseActivity<ActivityRegisterBinding>() {

    override val bindingInflater: (LayoutInflater) -> ActivityRegisterBinding = ActivityRegisterBinding::inflate
    private lateinit var repository: DataRepository
    private var role = "CUSTOMER" // Default

    private var selectedProfileImageUri: Uri? = null
    private var selectedIntroVideoUri: Uri? = null
    private var uploadedProfileImageUrl: String? = null
    private var uploadedIntroVideoUrl: String? = null

    private val pickImageLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) {
            selectedProfileImageUri = uri
            binding.ivProfilePreview.setImageURI(uri)
            binding.btnUploadProfilePic.text = getString(R.string.photo_selected)
        }
    }

    private val pickVideoLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) {
            selectedIntroVideoUri = uri
            binding.ivVideoPreview.visibility = View.VISIBLE
            binding.tvVideoPlaceholder.visibility = View.GONE
            binding.tvVideoPlaceholder.text = getString(R.string.video_selected)
            binding.tvVideoPlaceholder.visibility = View.VISIBLE
            binding.btnUploadIntroVideo.text = getString(R.string.video_selected)
        }
    }

    override fun initViews() {
        repository = DataRepository.getInstance()

        if (intent.hasExtra("ROLE")) {
            val intentRole = intent.getStringExtra("ROLE")
            if ("CA" == intentRole) {
                binding.rbCA.isChecked = true
                role = "CA"
                binding.layoutCAFields.visibility = View.VISIBLE
                binding.layoutCustomerFields.visibility = View.GONE
            } else {
                binding.rbCustomer.isChecked = true
                role = "CUSTOMER"
                binding.layoutCAFields.visibility = View.GONE
                binding.layoutCustomerFields.visibility = View.VISIBLE
            }
        } else {
            binding.layoutCAFields.visibility = View.GONE
            binding.layoutCustomerFields.visibility = View.VISIBLE
        }
    }

    override fun observeViewModel() {
        // No ViewModel to observe
    }

    override fun setupListeners() {
        binding.cardClient.setOnClickListener { selectRole("CUSTOMER") }
        binding.cardCA.setOnClickListener { selectRole("CA") }

        binding.btnRegister.setOnClickListener { registerUser() }

        binding.tvLoginLink.setOnClickListener {
            startActivity(Intent(this@RegisterActivity, LoginActivity::class.java))
            finish()
        }

        binding.btnUploadProfilePic.setOnClickListener { pickImageLauncher.launch("image/*") }
        binding.btnUploadIntroVideo.setOnClickListener { pickVideoLauncher.launch("video/*") }
    }

    private fun selectRole(selectedRole: String) {
        this.role = selectedRole
        if ("CA" == selectedRole) {
            binding.rbCA.isChecked = true
            binding.rbCustomer.isChecked = false
            binding.cardCA.strokeColor = getColor(R.color.primary)
            binding.cardClient.strokeColor = getColor(R.color.text_muted)

            ((binding.cardCA.getChildAt(0) as LinearLayout).getChildAt(1) as TextView).setTextColor(getColor(R.color.primary))
            ((binding.cardCA.getChildAt(0) as LinearLayout).getChildAt(0) as ImageView).setColorFilter(getColor(R.color.primary))

            ((binding.cardClient.getChildAt(0) as LinearLayout).getChildAt(1) as TextView).setTextColor(getColor(R.color.text_muted))
            ((binding.cardClient.getChildAt(0) as LinearLayout).getChildAt(0) as ImageView).setColorFilter(getColor(R.color.text_muted))

            binding.layoutCAFields.visibility = View.VISIBLE
            binding.layoutCustomerFields.visibility = View.GONE
        } else {
            binding.rbCustomer.isChecked = true
            binding.rbCA.isChecked = false
            binding.cardClient.strokeColor = getColor(R.color.primary)
            binding.cardCA.strokeColor = getColor(R.color.text_muted)

            ((binding.cardClient.getChildAt(0) as LinearLayout).getChildAt(1) as TextView).setTextColor(getColor(R.color.primary))
            ((binding.cardClient.getChildAt(0) as LinearLayout).getChildAt(0) as ImageView).setColorFilter(getColor(R.color.primary))

            ((binding.cardCA.getChildAt(0) as LinearLayout).getChildAt(1) as TextView).setTextColor(getColor(R.color.text_muted))
            ((binding.cardCA.getChildAt(0) as LinearLayout).getChildAt(0) as ImageView).setColorFilter(getColor(R.color.text_muted))

            binding.layoutCAFields.visibility = View.GONE
            binding.layoutCustomerFields.visibility = View.VISIBLE
        }
    }

    private fun registerUser() {
        if (!NetworkUtils.isNetworkAvailable(this)) {
            showToast(getString(R.string.no_internet))
            return
        }

        val name = binding.etName.text.toString().trim()
        val email = binding.etEmail.text.toString().trim()
        val city = binding.etCity.text.toString().trim()
        val password = binding.etPassword.text.toString().trim()

        if (TextUtils.isEmpty(name)) {
            binding.etName.error = getString(R.string.name_required)
            binding.etName.requestFocus()
            return
        }

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

        if (TextUtils.isEmpty(city)) {
            binding.etCity.error = getString(R.string.city_required)
            binding.etCity.requestFocus()
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

        val user = UserModel(null, email, name, role)
        user.city = city

        if ("CA" == role) {
            val caNumber = binding.etCaNumber.text.toString().trim()
            val experience = binding.etExperience.text.toString().trim()
            val specialization = binding.etSpecialization.text.toString().trim()
            val minCharges = binding.etMinCharges.text.toString().trim()

            if (TextUtils.isEmpty(caNumber)) {
                binding.etCaNumber.error = getString(R.string.ca_number_required)
                binding.etCaNumber.requestFocus()
                return
            }
            if (caNumber.length < 6) {
                binding.etCaNumber.error = getString(R.string.ca_number_min_length)
                binding.etCaNumber.requestFocus()
                return
            }
            if (TextUtils.isEmpty(experience)) {
                binding.etExperience.error = getString(R.string.experience_required)
                binding.etExperience.requestFocus()
                return
            }
            if (TextUtils.isEmpty(specialization)) {
                binding.etSpecialization.error = getString(R.string.specialization_required)
                binding.etSpecialization.requestFocus()
                return
            }
            if (TextUtils.isEmpty(minCharges)) {
                binding.etMinCharges.error = getString(R.string.min_charges_required)
                binding.etMinCharges.requestFocus()
                return
            }

            if (selectedProfileImageUri == null) {
                showToast(getString(R.string.select_profile_pic))
                binding.btnUploadProfilePic.requestFocus()
                return
            }
            if (selectedIntroVideoUri == null) {
                showToast(getString(R.string.select_intro_video))
                binding.btnUploadIntroVideo.requestFocus()
                return
            }

            user.caNumber = caNumber
            user.experience = experience
            user.specialization = specialization
            user.minCharges = minCharges

            uploadMediaAndRegister(email, password, user)
        } else {
            performRegistration(email, password, user)
        }
    }

    private fun uploadMediaAndRegister(email: String, password: String, user: UserModel) {
        binding.btnRegister.isEnabled = false
        binding.btnRegister.text = getString(R.string.uploading_media)
        binding.progressBarMedia.visibility = View.VISIBLE

        CloudinaryHelper.uploadMedia(this, selectedProfileImageUri!!, object : CloudinaryHelper.ImageUploadCallback {
            override fun onSuccess(data: String?) {
                uploadedProfileImageUrl = data ?: ""
                user.profileImageUrl = uploadedProfileImageUrl

                CloudinaryHelper.uploadMedia(this@RegisterActivity, selectedIntroVideoUri!!, object : CloudinaryHelper.ImageUploadCallback {
                    override fun onSuccess(data: String?) {
                        uploadedIntroVideoUrl = data ?: ""
                        user.introVideoUrl = uploadedIntroVideoUrl

                        runOnUiThread {
                            binding.progressBarMedia.visibility = View.GONE
                            performRegistration(email, password, user)
                        }
                    }

                    override fun onError(error: String?) {
                        runOnUiThread {
                            binding.btnRegister.isEnabled = true
                            binding.btnRegister.text = getString(R.string.create_account)
                            binding.progressBarMedia.visibility = View.GONE
                            showToast(getString(R.string.video_upload_failed, error))
                        }
                    }
                })
            }

            override fun onError(error: String?) {
                runOnUiThread {
                    binding.btnRegister.isEnabled = true
                    binding.btnRegister.text = getString(R.string.create_account)
                    binding.progressBarMedia.visibility = View.GONE
                    showToast(getString(R.string.image_upload_failed, error))
                }
            }
        })
    }

    private fun performRegistration(email: String, password: String, user: UserModel) {
        binding.btnRegister.isEnabled = false
        binding.btnRegister.text = getString(R.string.registering)

        repository.registerUser(email, password, user, object : DataCallback<Void?> {
            override fun onSuccess(data: Void?) {
                showToast(getString(R.string.registration_successful))
                navigateToDashboard()
            }

            override fun onError(error: String?) {
                binding.btnRegister.isEnabled = true
                binding.btnRegister.text = getString(R.string.create_account)
                showToast(getString(R.string.error_with_message, error))
            }
        })
    }

    private fun navigateToDashboard() {
        val intent = if ("CA" == role) {
            Intent(this, CADashboardActivity::class.java)
        } else {
            Intent(this, HomeActivity::class.java)
        }
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        startActivity(intent)
    }
}
