package com.example.taxconnect;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;

import com.bumptech.glide.Glide;
import com.example.taxconnect.databinding.ActivityProfileBinding;
import com.example.taxconnect.model.UserModel;
import com.example.taxconnect.model.CertificateModel;
import com.example.taxconnect.services.CloudinaryHelper;
import com.google.firebase.auth.FirebaseAuth;
import android.widget.TextView;
import android.view.LayoutInflater;
import android.view.ViewGroup;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import androidx.recyclerview.widget.LinearLayoutManager;

import java.util.ArrayList;
import java.util.List;
import android.database.Cursor;
import android.provider.OpenableColumns;

public class ProfileActivity extends AppCompatActivity {

    private ActivityProfileBinding binding;
    private DataRepository repository;
    private String currentUserId;
    private UserModel currentUser;
    
    private Uri selectedProfileImageUri;
    private Uri selectedIntroVideoUri;
    private CertificateAdapter certificateAdapter;

    private final ActivityResultLauncher<String> pickCertificateLauncher = registerForActivityResult(
            new ActivityResultContracts.GetContent(),
            uri -> {
                if (uri != null) {
                    uploadCertificate(uri);
                }
            }
    );

    private final ActivityResultLauncher<String> pickImageLauncher = registerForActivityResult(
            new ActivityResultContracts.GetContent(),
            uri -> {
                if (uri != null) {
                    selectedProfileImageUri = uri;
                    binding.ivProfileImage.setImageURI(uri);
                }
            }
    );

    private final ActivityResultLauncher<String> pickVideoLauncher = registerForActivityResult(
            new ActivityResultContracts.GetContent(),
            uri -> {
                if (uri != null) {
                    selectedIntroVideoUri = uri;
                    // Show a placeholder or thumbnail
                    binding.ivVideoPreview.setImageResource(android.R.drawable.ic_media_play);
                    binding.btnChangeVideo.setText("Video Selected");
                    Toast.makeText(this, "Video selected. Click 'Save Changes' to upload.", Toast.LENGTH_SHORT).show();
                }
            }
    );

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityProfileBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        repository = DataRepository.getInstance();
        currentUserId = FirebaseAuth.getInstance().getUid();

        setupListeners();
        loadUserProfile();
    }

    private void setupListeners() {
        binding.btnBack.setOnClickListener(v -> finish());

        binding.btnOrderHistory.setOnClickListener(v -> {
            startActivity(new Intent(this, OrderHistoryActivity.class));
        });
        
        binding.btnChangePhoto.setOnClickListener(v -> pickImageLauncher.launch("image/*"));
        binding.tvChangePhoto.setOnClickListener(v -> pickImageLauncher.launch("image/*"));
        
        binding.btnChangeVideo.setOnClickListener(v -> pickVideoLauncher.launch("video/*"));
        
        binding.btnViewPublicProfile.setOnClickListener(v -> {
            if (currentUser != null) {
                Intent intent = new Intent(this, CADetailActivity.class);
                intent.putExtra("CA_DATA", currentUser);
                startActivity(intent);
            }
        });

        binding.btnUpdate.setOnClickListener(v -> {
            if (currentUser != null) {
                updateProfile();
            }
        });
        
        // Add Service Button Logic
        binding.btnAddService.setOnClickListener(v -> showAddServiceDialog(null));
        
        binding.btnUploadCertificate.setOnClickListener(v -> pickCertificateLauncher.launch("*/*"));
        
        binding.btnRequestVerification.setOnClickListener(v -> requestVerification());

        setupServiceList();
        setupCertificatesList();
    }

    private void setupCertificatesList() {
        certificateAdapter = new CertificateAdapter(new ArrayList<>(), certificate -> {
            // Delete certificate
            if (currentUser != null && currentUser.getCertificates() != null) {
                currentUser.getCertificates().remove(certificate);
                updateProfileNoToast(); // Save changes
                certificateAdapter.setCertificates(currentUser.getCertificates());
            }
        });
        binding.rvCertificates.setLayoutManager(new LinearLayoutManager(this));
        binding.rvCertificates.setAdapter(certificateAdapter);
    }

    private void setupServiceList() {
        com.example.taxconnect.adapter.ServiceAdapter serviceAdapter = new com.example.taxconnect.adapter.ServiceAdapter(service -> {
            showAddServiceDialog(service);
        });
        serviceAdapter.setEditable(true);
        serviceAdapter.setOnServiceDeleteListener(service -> {
            new androidx.appcompat.app.AlertDialog.Builder(this)
                .setTitle("Delete Service")
                .setMessage("Are you sure you want to delete " + service.getTitle() + "?")
                .setPositiveButton("Delete", (dialog, which) -> {
                    repository.deleteService(service.getId(), new DataRepository.DataCallback<Void>() {
                        @Override
                        public void onSuccess(Void data) {
                            Toast.makeText(ProfileActivity.this, "Service Deleted", Toast.LENGTH_SHORT).show();
                            loadServices();
                        }

                        @Override
                        public void onError(String error) {
                            Toast.makeText(ProfileActivity.this, "Failed to delete service: " + error, Toast.LENGTH_SHORT).show();
                        }
                    });
                })
                .setNegativeButton("Cancel", null)
                .show();
        });
        binding.rvServices.setLayoutManager(new androidx.recyclerview.widget.LinearLayoutManager(this));
        binding.rvServices.setAdapter(serviceAdapter);
        this.serviceAdapter = serviceAdapter;
    }

    private com.example.taxconnect.adapter.ServiceAdapter serviceAdapter;

    private void showAddServiceDialog(com.example.taxconnect.model.ServiceModel serviceToEdit) {
        androidx.appcompat.app.AlertDialog.Builder builder = new androidx.appcompat.app.AlertDialog.Builder(this);
        View dialogView = getLayoutInflater().inflate(R.layout.dialog_add_service, null);
        builder.setView(dialogView);
        
        com.google.android.material.textfield.TextInputEditText etTitle = dialogView.findViewById(R.id.etServiceTitle);
        com.google.android.material.textfield.TextInputEditText etDesc = dialogView.findViewById(R.id.etServiceDesc);
        com.google.android.material.textfield.TextInputEditText etPrice = dialogView.findViewById(R.id.etServicePrice);
        com.google.android.material.textfield.TextInputEditText etTime = dialogView.findViewById(R.id.etServiceTime);
        
        if (serviceToEdit != null) {
            etTitle.setText(serviceToEdit.getTitle());
            etDesc.setText(serviceToEdit.getDescription());
            etPrice.setText(serviceToEdit.getPrice());
            etTime.setText(serviceToEdit.getEstimatedTime());
            builder.setTitle("Edit Service");
        } else {
            builder.setTitle("Add Service");
        }

        builder.setPositiveButton(serviceToEdit != null ? "Update" : "Add", (dialog, which) -> {
            String title = etTitle.getText().toString();
            String desc = etDesc.getText().toString();
            String price = etPrice.getText().toString();
            String time = etTime.getText().toString();
            
            if (title.isEmpty() || price.isEmpty()) {
                Toast.makeText(this, "Title and Price are required", Toast.LENGTH_SHORT).show();
                return;
            }
            
            String id = serviceToEdit != null ? serviceToEdit.getId() : java.util.UUID.randomUUID().toString();
            
            com.example.taxconnect.model.ServiceModel service = new com.example.taxconnect.model.ServiceModel(
                    id,
                    currentUserId,
                    title,
                    desc,
                    price,
                    time
            );
            
            repository.saveService(service, new DataRepository.DataCallback<Void>() {
                @Override
                public void onSuccess(Void data) {
                    Toast.makeText(ProfileActivity.this, serviceToEdit != null ? "Service Updated" : "Service Added", Toast.LENGTH_SHORT).show();
                    loadServices();
                }

                @Override
                public void onError(String error) {
                    Toast.makeText(ProfileActivity.this, "Failed to save service", Toast.LENGTH_SHORT).show();
                }
            });
        });
        builder.setNegativeButton("Cancel", null);
        builder.show();
    }

    private void loadUserProfile() {
        repository.fetchUser(currentUserId, new DataRepository.DataCallback<UserModel>() {
            @Override
            public void onSuccess(UserModel user) {
                currentUser = user;
                populateFields(user);
            }

            @Override
            public void onError(String error) {
                Toast.makeText(ProfileActivity.this, "Error loading profile: " + error, Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void populateFields(UserModel user) {
        binding.etName.setText(user.getName());
        binding.etEmail.setText(user.getEmail());
        binding.etPhoneNumber.setText(user.getPhoneNumber());
        binding.etCity.setText(user.getCity());
        binding.etBio.setText(user.getBio());
        
        // Load Profile Image
        if (user.getProfileImageUrl() != null && !user.getProfileImageUrl().isEmpty()) {
            Glide.with(this)
                .load(user.getProfileImageUrl())
                .placeholder(android.R.drawable.ic_menu_gallery)
                .into(binding.ivProfileImage);
        }

        if ("CA".equals(user.getRole())) {
            binding.layoutCAFields.setVisibility(View.VISIBLE);
            binding.btnViewPublicProfile.setVisibility(View.VISIBLE);
            binding.etCaNumber.setText(user.getCaNumber());
            binding.etExperience.setText(user.getExperience());
            binding.etSpecialization.setText(user.getSpecialization());
            binding.etMinCharges.setText(user.getMinCharges());
            
            updateVerificationStatusUI(user);
            
            loadServices();
            // Note: Video preview is static for now unless we load a thumbnail

            if (user.getCertificates() != null && !user.getCertificates().isEmpty()) {
                binding.rvCertificates.setVisibility(View.VISIBLE);
                certificateAdapter.setCertificates(user.getCertificates());
            } else {
                binding.rvCertificates.setVisibility(View.GONE);
            }
        } else {
            binding.layoutCAFields.setVisibility(View.GONE);
            binding.btnViewPublicProfile.setVisibility(View.GONE);
        }
    }

    private void updateVerificationStatusUI(UserModel user) {
        if (user.isVerified()) {
            binding.ivVerificationStatus.setColorFilter(getColor(R.color.primary));
            binding.tvVerificationStatus.setText("Verified CA");
            binding.tvVerificationStatus.setTextColor(getColor(R.color.primary));
            binding.btnRequestVerification.setVisibility(View.GONE);
        } else if (user.isVerificationRequested()) {
            binding.ivVerificationStatus.setColorFilter(getColor(R.color.orange_500)); // Assuming orange_500 exists or use android.R.color.holo_orange_dark
            binding.tvVerificationStatus.setText("Verification Pending");
            binding.tvVerificationStatus.setTextColor(getColor(R.color.orange_500));
            binding.btnRequestVerification.setVisibility(View.GONE);
        } else {
            binding.ivVerificationStatus.setColorFilter(getColor(R.color.slate_500));
            binding.tvVerificationStatus.setText("Not Verified");
            binding.tvVerificationStatus.setTextColor(getColor(R.color.slate_700));
            binding.btnRequestVerification.setVisibility(View.VISIBLE);
        }
    }

    private void requestVerification() {
        if (currentUser == null) return;
        
        currentUser.setVerificationRequested(true);
        updateVerificationStatusUI(currentUser);
        
        repository.updateUser(currentUser, new DataRepository.DataCallback<Void>() {
            @Override
            public void onSuccess(Void data) {
                Toast.makeText(ProfileActivity.this, "Verification Request Submitted", Toast.LENGTH_SHORT).show();
            }

            @Override
            public void onError(String error) {
                currentUser.setVerificationRequested(false);
                updateVerificationStatusUI(currentUser);
                Toast.makeText(ProfileActivity.this, "Request Failed: " + error, Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void loadServices() {
        repository.getServices(currentUserId, new DataRepository.DataCallback<java.util.List<com.example.taxconnect.model.ServiceModel>>() {
            @Override
            public void onSuccess(java.util.List<com.example.taxconnect.model.ServiceModel> data) {
                if (serviceAdapter != null) {
                    serviceAdapter.setServices(data);
                }
            }

            @Override
            public void onError(String error) {
                // ignore
            }
        });
    }

    private void updateProfile() {
        String name = binding.etName.getText().toString().trim();
        String city = binding.etCity.getText().toString().trim();
        String bio = binding.etBio.getText().toString().trim();

        if (name.isEmpty()) {
            binding.etName.setError("Name is required");
            return;
        }

        currentUser.setName(name);
        currentUser.setCity(city);
        currentUser.setBio(bio);

        if ("CA".equals(currentUser.getRole())) {
            String caNumber = binding.etCaNumber.getText().toString().trim();
            String experienceStr = binding.etExperience.getText().toString().trim();
            String specialization = binding.etSpecialization.getText().toString().trim();
            String minChargesStr = binding.etMinCharges.getText().toString().trim();

            if (caNumber.isEmpty()) {
                binding.etCaNumber.setError("Required");
                return;
            }
            if (experienceStr.isEmpty()) {
                binding.etExperience.setError("Required");
                return;
            }

            currentUser.setCaNumber(caNumber);
            currentUser.setExperience(experienceStr);
            currentUser.setSpecialization(specialization);
            currentUser.setMinCharges(minChargesStr);
        }
        
        binding.btnUpdate.setEnabled(false);
        binding.btnUpdate.setText("Updating...");
        
        // Handle Media Uploads
        if (selectedProfileImageUri != null) {
            CloudinaryHelper.uploadMedia(this, selectedProfileImageUri, new CloudinaryHelper.ImageUploadCallback() {
                @Override
                public void onSuccess(String url) {
                    currentUser.setProfileImageUrl(url);
                    uploadVideoAndSave();
                }

                @Override
                public void onError(String error) {
                    runOnUiThread(() -> {
                        binding.btnUpdate.setEnabled(true);
                        binding.btnUpdate.setText("Save Changes");
                        Toast.makeText(ProfileActivity.this, "Image Upload Failed: " + error, Toast.LENGTH_SHORT).show();
                    });
                }
            });
        } else {
            uploadVideoAndSave();
        }
    }
    
    private void uploadVideoAndSave() {
        if (selectedIntroVideoUri != null) {
            CloudinaryHelper.uploadMedia(this, selectedIntroVideoUri, new CloudinaryHelper.ImageUploadCallback() {
                @Override
                public void onSuccess(String url) {
                    currentUser.setIntroVideoUrl(url);
                    saveUserToFirestore();
                }

                @Override
                public void onError(String error) {
                    runOnUiThread(() -> {
                        binding.btnUpdate.setEnabled(true);
                        binding.btnUpdate.setText("Save Changes");
                        Toast.makeText(ProfileActivity.this, "Video Upload Failed: " + error, Toast.LENGTH_SHORT).show();
                    });
                }
            });
        } else {
            saveUserToFirestore();
        }
    }

    private void saveUserToFirestore() {
        repository.updateUser(currentUser, new DataRepository.DataCallback<Void>() {
            @Override
            public void onSuccess(Void data) {
                binding.btnUpdate.setEnabled(true);
                binding.btnUpdate.setText("Save Changes");
                Toast.makeText(ProfileActivity.this, "Profile Updated Successfully", Toast.LENGTH_SHORT).show();
                finish();
            }

            @Override
            public void onError(String error) {
                binding.btnUpdate.setEnabled(true);
                binding.btnUpdate.setText("Save Changes");
                Toast.makeText(ProfileActivity.this, "Update Failed: " + error, Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void uploadCertificate(Uri uri) {
        String fileName = getFileName(uri);
        Toast.makeText(this, "Uploading Certificate...", Toast.LENGTH_SHORT).show();
        
        CloudinaryHelper.uploadMedia(this, uri, new CloudinaryHelper.ImageUploadCallback() {
            @Override
            public void onSuccess(String url) {
                runOnUiThread(() -> {
                    CertificateModel cert = new CertificateModel(
                            java.util.UUID.randomUUID().toString(),
                            fileName,
                            url,
                            fileName.toLowerCase().endsWith(".pdf") ? "pdf" : "image",
                            System.currentTimeMillis()
                    );
                    
                    if (currentUser.getCertificates() == null) {
                        currentUser.setCertificates(new ArrayList<>());
                    }
                    currentUser.getCertificates().add(cert);
                    
                    updateProfileNoToast();
                    certificateAdapter.setCertificates(currentUser.getCertificates());
                    binding.rvCertificates.setVisibility(View.VISIBLE);
                    Toast.makeText(ProfileActivity.this, "Certificate Uploaded", Toast.LENGTH_SHORT).show();
                });
            }

            @Override
            public void onError(String error) {
                runOnUiThread(() -> {
                    Toast.makeText(ProfileActivity.this, "Upload Failed: " + error, Toast.LENGTH_SHORT).show();
                });
            }
        });
    }
    
    private void updateProfileNoToast() {
        repository.updateUser(currentUser, new DataRepository.DataCallback<Void>() {
            @Override
            public void onSuccess(Void data) {
                // Silent success
            }
            @Override
            public void onError(String error) {
                Toast.makeText(ProfileActivity.this, "Failed to save changes: " + error, Toast.LENGTH_SHORT).show();
            }
        });
    }
    
    private String getFileName(Uri uri) {
        String result = null;
        if (uri.getScheme().equals("content")) {
            try (Cursor cursor = getContentResolver().query(uri, null, null, null, null)) {
                if (cursor != null && cursor.moveToFirst()) {
                    int index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                    if (index != -1) {
                        result = cursor.getString(index);
                    }
                }
            }
        }
        if (result == null) {
            result = uri.getPath();
            int cut = result.lastIndexOf('/');
            if (cut != -1) {
                result = result.substring(cut + 1);
            }
        }
        return result;
    }

    private static class CertificateAdapter extends RecyclerView.Adapter<CertificateAdapter.ViewHolder> {
        private List<CertificateModel> certificates;
        private final OnDeleteListener onDeleteListener;

        public interface OnDeleteListener {
            void onDelete(CertificateModel certificate);
        }

        public CertificateAdapter(List<CertificateModel> certificates, OnDeleteListener onDeleteListener) {
            this.certificates = certificates;
            this.onDeleteListener = onDeleteListener;
        }

        public void setCertificates(List<CertificateModel> certificates) {
            this.certificates = certificates;
            notifyDataSetChanged();
        }

        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_certificate_edit, parent, false);
            return new ViewHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            CertificateModel cert = certificates.get(position);
            holder.tvName.setText(cert.getName());
            holder.btnDelete.setOnClickListener(v -> onDeleteListener.onDelete(cert));
        }

        @Override
        public int getItemCount() {
            return certificates == null ? 0 : certificates.size();
        }

        static class ViewHolder extends RecyclerView.ViewHolder {
            TextView tvName;
            android.widget.ImageView btnDelete;

            public ViewHolder(@NonNull View itemView) {
                super(itemView);
                tvName = itemView.findViewById(R.id.tvName);
                btnDelete = itemView.findViewById(R.id.btnDelete);
            }
        }
    }
}
