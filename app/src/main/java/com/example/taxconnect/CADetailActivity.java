package com.example.taxconnect;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.bumptech.glide.Glide;
import com.example.taxconnect.databinding.ActivityCaDetailBinding;
import com.example.taxconnect.model.ConversationModel;
import com.example.taxconnect.model.UserModel;
import com.example.taxconnect.model.BookingModel;
import com.example.taxconnect.model.CertificateModel;
import com.google.android.material.textfield.TextInputEditText;
import com.google.firebase.auth.FirebaseAuth;

import java.util.Calendar;
import com.example.taxconnect.adapter.DateAdapter;
import com.example.taxconnect.adapter.TimeSlotAdapter;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import java.util.ArrayList;
import java.util.List;
import java.util.Arrays;

public class CADetailActivity extends AppCompatActivity {

    private ActivityCaDetailBinding binding;
    private UserModel ca;
    private UserModel currentUser;
    private DataRepository repository;
    private String currentUserId;
    private String chatId;
    private ConversationModel currentConversation;
    private com.google.firebase.firestore.ListenerRegistration conversationListener;

    private com.example.taxconnect.adapter.ServiceAdapter serviceAdapter;
    private com.example.taxconnect.adapter.RatingAdapter ratingAdapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityCaDetailBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        repository = DataRepository.getInstance();
        currentUserId = FirebaseAuth.getInstance().getUid();
        
        setupRatingsRecyclerView();

        repository.fetchUser(currentUserId, new DataRepository.DataCallback<UserModel>() {
            @Override
            public void onSuccess(UserModel user) {
                currentUser = user;
                checkRatingEligibility();
            }
            @Override
            public void onError(String error) {
                // Ignore
            }
        });

        if (getIntent().hasExtra("CA_DATA")) {
            ca = (UserModel) getIntent().getSerializableExtra("CA_DATA");
            if (ca != null) {
                // Initialize button state
                binding.btnConnect.setText("Loading...");
                binding.btnConnect.setEnabled(false);

                chatId = repository.getChatId(currentUserId, ca.getUid());
                setupUI();
                setupServices();
                listenForConversationUpdates();
            } else {
                finish();
            }
        } else {
            finish();
        }
        
        binding.toolbar.setNavigationOnClickListener(v -> finish());
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (conversationListener != null) {
            conversationListener.remove();
            conversationListener = null;
        }
    }

    private void setupServices() {
        serviceAdapter = new com.example.taxconnect.adapter.ServiceAdapter(service -> {
            // Handle Buy Service
            showBuyServiceDialog(service);
        });
        binding.rvServices.setLayoutManager(new androidx.recyclerview.widget.LinearLayoutManager(this));
        binding.rvServices.setAdapter(serviceAdapter);

        repository.getServices(ca.getUid(), new DataRepository.DataCallback<java.util.List<com.example.taxconnect.model.ServiceModel>>() {
            @Override
            public void onSuccess(java.util.List<com.example.taxconnect.model.ServiceModel> data) {
                serviceAdapter.setServices(data);
                if (data.isEmpty()) {
                    binding.rvServices.setVisibility(View.GONE);
                } else {
                    binding.rvServices.setVisibility(View.VISIBLE);
                }
            }

            @Override
            public void onError(String error) {
                // Ignore error, just hide section
                binding.rvServices.setVisibility(View.GONE);
            }
        });
    }

    private void showBuyServiceDialog(com.example.taxconnect.model.ServiceModel service) {
        new AlertDialog.Builder(this)
                .setTitle("Buy Service: " + service.getTitle())
                .setMessage("Price: \u20B9 " + service.getPrice() + "\nTime: " + service.getEstimatedTime() + "\n\nProceed to payment?")
                .setPositiveButton("Pay & Request", (dialog, which) -> {
                    initiatePayment(service);
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void initiatePayment(com.example.taxconnect.model.ServiceModel service) {
        // Here we would integrate Razorpay
        // For now, simulate success
        
        // Create conversation request directly with payment info
        ConversationModel request = new ConversationModel();
        request.setConversationId(chatId);
        request.setParticipantIds(java.util.Arrays.asList(currentUserId, ca.getUid()));
        request.setWorkflowState(ConversationModel.STATE_REQUESTED);
        request.setLastMessage("Service Request: " + service.getTitle());
        request.setLastMessageTimestamp(System.currentTimeMillis());
        
        // We might want to store more details about the service request in the conversation
        // For now, just a text message
        
        repository.createConversation(request, new DataRepository.DataCallback<Void>() {
            @Override
            public void onSuccess(Void data) {
                // Send initial message
                com.example.taxconnect.model.MessageModel msg = new com.example.taxconnect.model.MessageModel(
                        currentUserId, ca.getUid(), chatId, 
                        "I have purchased your service: " + service.getTitle(), 
                        System.currentTimeMillis(), "TEXT"
                );
                repository.sendMessage(msg, new DataRepository.DataCallback<Void>() {
                    @Override
                    public void onSuccess(Void data) {
                         Toast.makeText(CADetailActivity.this, "Service Requested Successfully!", Toast.LENGTH_SHORT).show();
                    }
                    @Override
                    public void onError(String error) {
                         // Still show success as conversation was created, message failure is minor
                         Toast.makeText(CADetailActivity.this, "Service Requested (Message Pending)", Toast.LENGTH_SHORT).show();
                    }
                });
            }

            @Override
            public void onError(String error) {
                Toast.makeText(CADetailActivity.this, "Failed to request service", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void setupUI() {
        if (ca == null) return;

        // Fetch latest CA data to ensure stats are up-to-date
        repository.fetchUser(ca.getUid(), new DataRepository.DataCallback<UserModel>() {
            @Override
            public void onSuccess(UserModel updatedCa) {
                if (updatedCa != null) {
                    ca = updatedCa;
                    updateStatsUI();
                    setupCertificates();
                }
            }
            @Override
            public void onError(String error) {
                // Fallback to existing data
                updateStatsUI();
                setupCertificates();
            }
        });
    }

    private void updateStatsUI() {
        binding.tvName.setText(ca.getName());
        binding.tvSpecialization.setText("Specialization: " + ca.getSpecialization());
        binding.tvExperience.setText(ca.getExperience() + " Years");
        binding.tvExperienceStat.setText(ca.getExperience() + "+");
        binding.tvClientStat.setText(ca.getClientCount() + "+");
        binding.tvRatingStat.setText(String.format(java.util.Locale.getDefault(), "%.1f", ca.getRating()));
        binding.tvBio.setText(ca.getBio() != null ? ca.getBio() : "No bio available.");
        
        // Bind Pricing
        binding.tvMinCharges.setText(ca.getMinCharges() != null && !ca.getMinCharges().isEmpty() ? "₹ " + ca.getMinCharges() : "Contact for Price");

        // Bind Rating
        binding.tvRating.setText(String.format(java.util.Locale.getDefault(), "%.1f (%d Reviews)", ca.getRating(), ca.getRatingCount()));
        loadRatings();

        // Verified Badge
        if (ca.isVerified()) {
            binding.ivVerifiedBadge.setVisibility(View.VISIBLE);
        } else {
            binding.ivVerifiedBadge.setVisibility(View.GONE);
        }

        // Load Profile Image
        if (ca.getProfileImageUrl() != null && !ca.getProfileImageUrl().isEmpty()) {
            Glide.with(this)
                    .load(ca.getProfileImageUrl())
                    .placeholder(R.drawable.ic_person)
                    .error(R.drawable.ic_person)
                    .into(binding.ivProfile);
        } else {
            binding.ivProfile.setImageResource(R.drawable.ic_person);
        }

        // Setup Intro Video Button
        if (ca.getIntroVideoUrl() != null && !ca.getIntroVideoUrl().isEmpty()) {
            binding.btnWatchIntro.setVisibility(View.VISIBLE);
            binding.btnWatchIntro.setOnClickListener(v -> {
                showVideoBottomSheet(ca.getIntroVideoUrl());
            });
        } else {
            binding.btnWatchIntro.setVisibility(View.GONE);
        }
    }

    private void listenForConversationUpdates() {
        if (conversationListener != null) {
            conversationListener.remove();
        }
        conversationListener = com.example.taxconnect.repository.ConversationRepository.getInstance().listenToConversation(chatId, new DataRepository.DataCallback<ConversationModel>() {
            @Override
            public void onSuccess(ConversationModel conversation) {
                currentConversation = conversation;
                updateConnectButton();
            }

            @Override
            public void onError(String error) {
                // If error, assume no conversation or network issue. 
                // If we treat it as null, user can try to request again.
                currentConversation = null;
                updateConnectButton();
            }
        });
    }

    private void updateConnectButton() {
        binding.btnConnect.setEnabled(true);
        
        if (currentConversation == null || 
            ConversationModel.STATE_REFUSED.equals(currentConversation.getWorkflowState())) {
            
            binding.btnConnect.setText("Request Assistance");
            binding.btnConnect.setOnClickListener(v -> showRequestDialog());
            
        } else if (ConversationModel.STATE_REQUESTED.equals(currentConversation.getWorkflowState())) {
            
            binding.btnConnect.setText("Request Pending");
            binding.btnConnect.setOnClickListener(v -> {
                Toast.makeText(this, "Your request is pending approval from the CA.", Toast.LENGTH_SHORT).show();
            });
            
        } else {
            // Discussion, Accepted, etc.
            binding.btnConnect.setText("Message");
            binding.btnConnect.setOnClickListener(v -> {
                Intent intent = new Intent(this, ChatActivity.class);
                intent.putExtra("RECEIVER_ID", ca.getUid());
                intent.putExtra("RECEIVER_NAME", ca.getName());
                startActivity(intent);
            });
        }
    }

    private void showRequestDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        View view = LayoutInflater.from(this).inflate(R.layout.dialog_send_request, null);
        builder.setView(view);
        AlertDialog dialog = builder.create();

        TextInputEditText etMessage = view.findViewById(R.id.etRequestMessage);
        Button btnSend = view.findViewById(R.id.btnSendRequest);
        Button btnCancel = view.findViewById(R.id.btnCancelRequest);

        btnSend.setOnClickListener(v -> {
            String message = etMessage.getText().toString().trim();
            if (message.isEmpty()) {
                Toast.makeText(this, "Please enter your query", Toast.LENGTH_SHORT).show();
                return;
            }

            sendRequest(message, dialog);
        });

        btnCancel.setOnClickListener(v -> dialog.dismiss());
        dialog.show();
    }

    private void sendRequest(String message, AlertDialog dialog) {
        repository.sendRequest(currentUserId, ca.getUid(), message, new DataRepository.DataCallback<Void>() {
            @Override
            public void onSuccess(Void data) {
                Toast.makeText(CADetailActivity.this, "Request Sent Successfully", Toast.LENGTH_SHORT).show();
                dialog.dismiss();
                // Button update will happen automatically via listener
            }

            @Override
            public void onError(String error) {
                Toast.makeText(CADetailActivity.this, "Failed to send request: " + error, Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void showVideoBottomSheet(String videoUrl) {
        if (videoUrl == null || videoUrl.isEmpty()) return;

        com.google.android.material.bottomsheet.BottomSheetDialog bottomSheetDialog = 
            new com.google.android.material.bottomsheet.BottomSheetDialog(this);
        
        View view = getLayoutInflater().inflate(R.layout.layout_video_bottom_sheet, null);
        bottomSheetDialog.setContentView(view);

        androidx.media3.ui.PlayerView playerView = view.findViewById(R.id.playerView);
        View btnClose = view.findViewById(R.id.btnClose);

        androidx.media3.exoplayer.ExoPlayer player = new androidx.media3.exoplayer.ExoPlayer.Builder(this).build();
        playerView.setPlayer(player);
        
        // Prepare the player
        androidx.media3.common.MediaItem mediaItem = androidx.media3.common.MediaItem.fromUri(Uri.parse(videoUrl));
        player.setMediaItem(mediaItem);
        player.prepare();
        player.play();

        bottomSheetDialog.setOnDismissListener(dialog -> {
            player.stop();
            player.release();
        });

        btnClose.setOnClickListener(v -> bottomSheetDialog.dismiss());

        bottomSheetDialog.show();
    }

    private boolean isUserEligibleToRate = false;

    private void setupRatingsRecyclerView() {
        ratingAdapter = new com.example.taxconnect.adapter.RatingAdapter();
        binding.rvRatings.setLayoutManager(new androidx.recyclerview.widget.LinearLayoutManager(this));
        binding.rvRatings.setAdapter(ratingAdapter);
        
        binding.btnRateCa.setOnClickListener(v -> {
            if (currentUser != null && !"CUSTOMER".equals(currentUser.getRole())) {
                Toast.makeText(this, "Only customers can rate CAs.", Toast.LENGTH_SHORT).show();
                return;
            }
            if (!isUserEligibleToRate) {
                Toast.makeText(this, "You can only review CAs after a completed service.", Toast.LENGTH_SHORT).show();
                return;
            }
            showRateDialog();
        });
    }

    private void loadRatings() {
        if (ca == null) return;
        repository.getRatings(ca.getUid(), new DataRepository.DataCallback<List<com.example.taxconnect.model.RatingModel>>() {
            @Override
            public void onSuccess(List<com.example.taxconnect.model.RatingModel> data) {
                ratingAdapter.setRatings(data);
                // Update header count if needed, though binding does it on setupUI
            }

            @Override
            public void onError(String error) {
                // Log or ignore
            }
        });
    }

    private void checkRatingEligibility() {
        if (ca == null || currentUser == null) return;
        
        // Only customers can rate
        if (!"CUSTOMER".equals(currentUser.getRole())) {
            isUserEligibleToRate = false;
            return;
        }

        repository.checkRatingEligibility(currentUserId, ca.getUid(), new DataRepository.DataCallback<Boolean>() {
            @Override
            public void onSuccess(Boolean isEligible) {
                isUserEligibleToRate = isEligible;
            }

            @Override
            public void onError(String error) {
                isUserEligibleToRate = false;
            }
        });
    }

    private void showRateDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        View view = LayoutInflater.from(this).inflate(R.layout.dialog_rate_ca, null);
        builder.setView(view);
        AlertDialog dialog = builder.create();

        android.widget.RatingBar ratingBar = view.findViewById(R.id.ratingBar);
        TextInputEditText etReview = view.findViewById(R.id.etReview);
        Button btnSubmit = view.findViewById(R.id.btnSubmit);
        Button btnCancel = view.findViewById(R.id.btnCancel);

        btnSubmit.setOnClickListener(v -> {
            float rating = ratingBar.getRating();
            String review = etReview.getText().toString().trim();

            if (rating == 0) {
                Toast.makeText(this, "Please select a rating", Toast.LENGTH_SHORT).show();
                return;
            }

            if (!review.isEmpty()) {
                String[] words = review.split("\\s+");
                if (words.length > 10) {
                    Toast.makeText(this, "Review must be at most 10 words", Toast.LENGTH_SHORT).show();
                    return;
                }
            }

            com.example.taxconnect.model.RatingModel ratingModel = new com.example.taxconnect.model.RatingModel(
                    currentUserId,
                    currentUser.getName(),
                    ca.getUid(),
                    rating,
                    review,
                    System.currentTimeMillis()
            );

            repository.addRating(ratingModel, new DataRepository.DataCallback<Void>() {
                @Override
                public void onSuccess(Void data) {
                    Toast.makeText(CADetailActivity.this, "Rating Submitted!", Toast.LENGTH_SHORT).show();
                    dialog.dismiss();
                    loadRatings();
                    refreshCAData(); // Update top stats
                }

                @Override
                public void onError(String error) {
                    Toast.makeText(CADetailActivity.this, "Failed to submit rating: " + error, Toast.LENGTH_SHORT).show();
                }
            });
        });

        btnCancel.setOnClickListener(v -> dialog.dismiss());
        dialog.show();
    }
    
    private void refreshCAData() {
        repository.fetchUser(ca.getUid(), new DataRepository.DataCallback<UserModel>() {
            @Override
            public void onSuccess(UserModel updatedCa) {
                ca = updatedCa;
                setupUI(); // Refresh UI with new stats
            }
            @Override
            public void onError(String error) {}
        });
    }

    private void setupCertificates() {
        if (ca.getCertificates() != null && !ca.getCertificates().isEmpty()) {
            binding.tvCertificatesTitle.setVisibility(View.VISIBLE);
            binding.rvCertificates.setVisibility(View.VISIBLE);
            
            CertificateViewerAdapter adapter = new CertificateViewerAdapter(ca.getCertificates(), certificate -> {
                Intent intent = new Intent(CADetailActivity.this, SecureDocViewerActivity.class);
                intent.putExtra("DOC_URL", certificate.getUrl());
                intent.putExtra("DOC_TYPE", certificate.getType());
                intent.putExtra("DOC_TITLE", certificate.getName());
                startActivity(intent);
            });
            binding.rvCertificates.setLayoutManager(new androidx.recyclerview.widget.LinearLayoutManager(this));
            binding.rvCertificates.setAdapter(adapter);
        } else {
            binding.tvCertificatesTitle.setVisibility(View.GONE);
            binding.rvCertificates.setVisibility(View.GONE);
        }
    }
    
    private static class CertificateViewerAdapter extends androidx.recyclerview.widget.RecyclerView.Adapter<CertificateViewerAdapter.ViewHolder> {
        private final List<CertificateModel> certificates;
        private final OnCertificateClickListener listener;

        public interface OnCertificateClickListener {
            void onCertificateClick(CertificateModel certificate);
        }

        public CertificateViewerAdapter(List<CertificateModel> certificates, OnCertificateClickListener listener) {
            this.certificates = certificates;
            this.listener = listener;
        }

        @androidx.annotation.NonNull
        @Override
        public ViewHolder onCreateViewHolder(@androidx.annotation.NonNull android.view.ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_certificate_public, parent, false);
            return new ViewHolder(view);
        }

        @Override
        public void onBindViewHolder(@androidx.annotation.NonNull ViewHolder holder, int position) {
            CertificateModel cert = certificates.get(position);
            holder.tvName.setText(cert.getName());
            holder.itemView.setOnClickListener(v -> listener.onCertificateClick(cert));
        }

        @Override
        public int getItemCount() {
            return certificates.size();
        }

        static class ViewHolder extends androidx.recyclerview.widget.RecyclerView.ViewHolder {
            android.widget.TextView tvName;

            public ViewHolder(@androidx.annotation.NonNull View itemView) {
                super(itemView);
                tvName = itemView.findViewById(R.id.tvName);
            }
        }
    }
}
