package com.example.taxconnect;

import android.content.Intent;
import android.os.Bundle;
import android.net.Uri;
import android.text.TextUtils;
import android.view.View;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;

import com.example.taxconnect.databinding.ActivityRegisterBinding;
import com.example.taxconnect.model.UserModel;
import com.example.taxconnect.services.CloudinaryHelper;

public class RegisterActivity extends AppCompatActivity {

    private ActivityRegisterBinding binding;
    private DataRepository repository;
    private String role = "CUSTOMER"; // Default
    
    private Uri selectedProfileImageUri;
    private Uri selectedIntroVideoUri;
    private String uploadedProfileImageUrl;
    private String uploadedIntroVideoUrl;

    private final ActivityResultLauncher<String> pickImageLauncher = registerForActivityResult(
            new ActivityResultContracts.GetContent(),
            uri -> {
                if (uri != null) {
                    selectedProfileImageUri = uri;
                    binding.ivProfilePreview.setImageURI(uri);
                    binding.btnUploadProfilePic.setText("Photo Selected");
                }
            }
    );

    private final ActivityResultLauncher<String> pickVideoLauncher = registerForActivityResult(
            new ActivityResultContracts.GetContent(),
            uri -> {
                if (uri != null) {
                    selectedIntroVideoUri = uri;
                    binding.ivVideoPreview.setVisibility(View.VISIBLE);
                    binding.tvVideoPlaceholder.setVisibility(View.GONE);
                    // Use Glide or just set a placeholder for video, or try to load thumbnail
                    // For now, just show the icon or text
                    binding.tvVideoPlaceholder.setText("Video Selected");
                    binding.tvVideoPlaceholder.setVisibility(View.VISIBLE);
                    binding.btnUploadIntroVideo.setText("Video Selected");
                }
            }
    );

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityRegisterBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        
        repository = DataRepository.getInstance();
        
        // Handle Intent Extra
        if (getIntent().hasExtra("ROLE")) {
            String intentRole = getIntent().getStringExtra("ROLE");
            if ("CA".equals(intentRole)) {
                binding.rbCA.setChecked(true);
                role = "CA";
                binding.layoutCAFields.setVisibility(View.VISIBLE);
                binding.layoutCustomerFields.setVisibility(View.GONE);
            } else {
                binding.rbCustomer.setChecked(true);
                role = "CUSTOMER";
                binding.layoutCAFields.setVisibility(View.GONE);
                binding.layoutCustomerFields.setVisibility(View.VISIBLE);
            }
        } else {
             // Default state is Customer checked
             binding.layoutCAFields.setVisibility(View.GONE);
             binding.layoutCustomerFields.setVisibility(View.VISIBLE);
        }
        
        setupListeners();
    }
    
    private void setupListeners() {
        // Setup Card Selection Listeners
        binding.cardClient.setOnClickListener(v -> selectRole("CUSTOMER"));
        binding.cardCA.setOnClickListener(v -> selectRole("CA"));

        binding.btnRegister.setOnClickListener(v -> registerUser());
        
        binding.tvLoginLink.setOnClickListener(v -> {
            startActivity(new Intent(RegisterActivity.this, LoginActivity.class));
            finish();
        });
        
        binding.btnUploadProfilePic.setOnClickListener(v -> pickImageLauncher.launch("image/*"));
        binding.btnUploadIntroVideo.setOnClickListener(v -> pickVideoLauncher.launch("video/*"));
    }

    private void selectRole(String selectedRole) {
        this.role = selectedRole;
        if ("CA".equals(selectedRole)) {
            binding.rbCA.setChecked(true);
            binding.rbCustomer.setChecked(false);
            binding.cardCA.setStrokeColor(getColor(R.color.primary));
            binding.cardClient.setStrokeColor(getColor(R.color.text_muted));
            
            // Update Text Colors
            ((android.widget.TextView)((android.widget.LinearLayout)binding.cardCA.getChildAt(0)).getChildAt(1)).setTextColor(getColor(R.color.primary));
            ((android.widget.ImageView)((android.widget.LinearLayout)binding.cardCA.getChildAt(0)).getChildAt(0)).setColorFilter(getColor(R.color.primary));
            
            ((android.widget.TextView)((android.widget.LinearLayout)binding.cardClient.getChildAt(0)).getChildAt(1)).setTextColor(getColor(R.color.text_muted));
            ((android.widget.ImageView)((android.widget.LinearLayout)binding.cardClient.getChildAt(0)).getChildAt(0)).setColorFilter(getColor(R.color.text_muted));

            binding.layoutCAFields.setVisibility(View.VISIBLE);
            binding.layoutCustomerFields.setVisibility(View.GONE);
        } else {
            binding.rbCustomer.setChecked(true);
            binding.rbCA.setChecked(false);
            binding.cardClient.setStrokeColor(getColor(R.color.primary));
            binding.cardCA.setStrokeColor(getColor(R.color.text_muted));
            
            // Update Text Colors
            ((android.widget.TextView)((android.widget.LinearLayout)binding.cardClient.getChildAt(0)).getChildAt(1)).setTextColor(getColor(R.color.primary));
            ((android.widget.ImageView)((android.widget.LinearLayout)binding.cardClient.getChildAt(0)).getChildAt(0)).setColorFilter(getColor(R.color.primary));
            
            ((android.widget.TextView)((android.widget.LinearLayout)binding.cardCA.getChildAt(0)).getChildAt(1)).setTextColor(getColor(R.color.text_muted));
            ((android.widget.ImageView)((android.widget.LinearLayout)binding.cardCA.getChildAt(0)).getChildAt(0)).setColorFilter(getColor(R.color.text_muted));

            binding.layoutCAFields.setVisibility(View.GONE);
            binding.layoutCustomerFields.setVisibility(View.VISIBLE);
        }
    }
    
    private void registerUser() {
        String name = binding.etName.getText().toString().trim();
        String email = binding.etEmail.getText().toString().trim();
        String city = binding.etCity.getText().toString().trim();
        String password = binding.etPassword.getText().toString().trim();
        
        if (TextUtils.isEmpty(name) || TextUtils.isEmpty(email) || TextUtils.isEmpty(city) || TextUtils.isEmpty(password)) {
            Toast.makeText(this, "Please fill all basic fields including City", Toast.LENGTH_SHORT).show();
            return;
        }
        
        UserModel user = new UserModel(null, email, name, role);
        user.setCity(city);
        
        if ("CA".equals(role)) {
            String caNumber = binding.etCaNumber.getText().toString().trim();
            String experience = binding.etExperience.getText().toString().trim();
            String specialization = binding.etSpecialization.getText().toString().trim();
            String minCharges = binding.etMinCharges.getText().toString().trim();
            
            if (TextUtils.isEmpty(caNumber) || TextUtils.isEmpty(experience) || 
                TextUtils.isEmpty(specialization) || TextUtils.isEmpty(minCharges)) {
                Toast.makeText(this, "Please fill all CA fields", Toast.LENGTH_SHORT).show();
                return;
            }

            if (selectedProfileImageUri == null || selectedIntroVideoUri == null) {
                Toast.makeText(this, "Please select both profile picture and intro video", Toast.LENGTH_SHORT).show();
                return;
            }
            
            user.setCaNumber(caNumber);
            user.setExperience(experience);
            user.setSpecialization(specialization);
            user.setMinCharges(minCharges);
            
            uploadMediaAndRegister(email, password, user);
        } else {
            performRegistration(email, password, user);
        }
    }

    private void uploadMediaAndRegister(String email, String password, UserModel user) {
        binding.btnRegister.setEnabled(false);
        binding.btnRegister.setText("Uploading Media...");
        binding.progressBarMedia.setVisibility(View.VISIBLE);

        CloudinaryHelper.uploadMedia(this, selectedProfileImageUri, new CloudinaryHelper.ImageUploadCallback() {
            @Override
            public void onSuccess(String url) {
                uploadedProfileImageUrl = url;
                user.setProfileImageUrl(uploadedProfileImageUrl);
                
                CloudinaryHelper.uploadMedia(RegisterActivity.this, selectedIntroVideoUri, new CloudinaryHelper.ImageUploadCallback() {
                    @Override
                    public void onSuccess(String videoUrl) {
                        uploadedIntroVideoUrl = videoUrl;
                        user.setIntroVideoUrl(uploadedIntroVideoUrl);
                        
                        runOnUiThread(() -> {
                            binding.progressBarMedia.setVisibility(View.GONE);
                            performRegistration(email, password, user);
                        });
                    }

                    @Override
                    public void onError(String error) {
                        runOnUiThread(() -> {
                            binding.btnRegister.setEnabled(true);
                            binding.btnRegister.setText("Create Account");
                            binding.progressBarMedia.setVisibility(View.GONE);
                            Toast.makeText(RegisterActivity.this, "Video Upload Failed: " + error, Toast.LENGTH_SHORT).show();
                        });
                    }
                });
            }

            @Override
            public void onError(String error) {
                runOnUiThread(() -> {
                    binding.btnRegister.setEnabled(true);
                    binding.btnRegister.setText("Create Account");
                    binding.progressBarMedia.setVisibility(View.GONE);
                    Toast.makeText(RegisterActivity.this, "Image Upload Failed: " + error, Toast.LENGTH_SHORT).show();
                });
            }
        });
    }

    private void performRegistration(String email, String password, UserModel user) {
        binding.btnRegister.setEnabled(false);
        binding.btnRegister.setText("Registering...");
        
        repository.registerUser(email, password, user, new DataRepository.DataCallback<Void>() {
            @Override
            public void onSuccess(Void data) {
                Toast.makeText(RegisterActivity.this, "Registration Successful!", Toast.LENGTH_SHORT).show();
                navigateToDashboard();
            }

            @Override
            public void onError(String error) {
                binding.btnRegister.setEnabled(true);
                binding.btnRegister.setText("Create Account");
                Toast.makeText(RegisterActivity.this, "Error: " + error, Toast.LENGTH_LONG).show();
            }
        });
    }
    
    private void navigateToDashboard() {
        Intent intent;
        if ("CA".equals(role)) {
            intent = new Intent(this, CADashboardActivity.class);
        } else {
            intent = new Intent(this, HomeActivity.class);
        }
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
    }
}
