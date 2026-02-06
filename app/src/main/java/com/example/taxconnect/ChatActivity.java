package com.example.taxconnect;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;

import androidx.appcompat.app.AlertDialog;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.example.taxconnect.adapter.MessageAdapter;
import com.example.taxconnect.databinding.ActivityChatBinding;
import com.example.taxconnect.model.MessageModel;
import com.example.taxconnect.services.CloudinaryHelper;
import com.example.taxconnect.services.PaymentManager;
import com.google.android.material.textfield.TextInputEditText;
import com.google.firebase.auth.FirebaseAuth;
import com.razorpay.PaymentResultListener;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.List;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;

import com.example.taxconnect.adapter.DateAdapter;
import com.example.taxconnect.adapter.TimeSlotAdapter;
import com.example.taxconnect.model.BookingModel;
import com.google.android.material.bottomsheet.BottomSheetDialog;

public class ChatActivity extends AppCompatActivity implements MessageAdapter.OnProposalActionListener, MessageAdapter.OnCallActionListener, PaymentResultListener {

    private static final int PERMISSION_REQ_ID = 22;
    private static final String[] REQUESTED_PERMISSIONS = {
            Manifest.permission.RECORD_AUDIO,
            Manifest.permission.CAMERA
    };

    private ActivityChatBinding binding;
    private DataRepository repository;
    private String currentUserId;
    private String receiverId;
    private MessageAdapter adapter;
    private String chatId;
    private PaymentManager paymentManager;
    private MessageModel currentProposal;
    private com.example.taxconnect.model.UserModel currentUser;
    private com.example.taxconnect.model.UserModel receiverUser;
    private String currentConversationState;
    private com.example.taxconnect.model.ConversationModel currentConversation;
    // Removed manual Button declarations as we will use binding

    private Uri photoUri;
    private com.google.firebase.firestore.ListenerRegistration messagesListener;
    private com.google.firebase.firestore.ListenerRegistration conversationListener;

    private final ActivityResultLauncher<String> pickImageLauncher = registerForActivityResult(
            new ActivityResultContracts.GetContent(),
            uri -> {
                if (uri != null) {
                    uploadImage(uri);
                }
            }
    );

    private final ActivityResultLauncher<String> pickDocumentLauncher = registerForActivityResult(
            new ActivityResultContracts.GetContent(),
            uri -> {
                if (uri != null) {
                    uploadDocument(uri);
                }
            }
    );

    private final ActivityResultLauncher<Uri> takePictureLauncher = registerForActivityResult(
            new ActivityResultContracts.TakePicture(),
            success -> {
                if (success && photoUri != null) {
                    uploadImage(photoUri);
                }
            }
    );

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // Prevent screenshots and screen recording for security
        getWindow().setFlags(android.view.WindowManager.LayoutParams.FLAG_SECURE, android.view.WindowManager.LayoutParams.FLAG_SECURE);

        binding = ActivityChatBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        repository = DataRepository.getInstance();
        paymentManager = new PaymentManager();
        currentUserId = FirebaseAuth.getInstance().getUid();

        // Retrieve receiver ID from intent
        receiverId = getIntent().getStringExtra("RECEIVER_ID");
        String receiverName = getIntent().getStringExtra("RECEIVER_NAME");
        boolean isOnline = getIntent().getBooleanExtra("IS_ONLINE", true); 
        
        if (receiverId == null) {
            Toast.makeText(this, "Error: No user to chat with", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        if (receiverName != null) {
            binding.toolbar.setTitle(receiverName);
        }
        
        binding.toolbar.setNavigationOnClickListener(v -> finish());

        chatId = repository.getChatId(currentUserId, receiverId);

        setupRecyclerView();
        adapter.setOnCallActionListener(this);
        listenForMessages();
        listenForConversationUpdates();

        binding.btnSend.setOnClickListener(v -> sendMessage());
        binding.btnCreateProposal.setOnClickListener(v -> showProposalDialog());
        binding.btnRequestDocs.setOnClickListener(v -> requestDocs());
        binding.btnCompleteJob.setOnClickListener(v -> completeJob());
        binding.btnLeaveFeedback.setOnClickListener(v -> showFeedbackDialog());
        binding.btnBookAppointment.setOnClickListener(v -> showBookingBottomSheet());

        if (isOnline) {
            // Logic handled in updateUIBasedOnRoleAndState
        } else {
            binding.btnVideoCall.setAlpha(0.5f);
            binding.btnVideoCall.setOnClickListener(v -> 
                Toast.makeText(this, "User is Offline. Video call unavailable.", Toast.LENGTH_SHORT).show()
            );
        }
        
        binding.btnAttach.setOnClickListener(v -> showAttachmentOptions());

        fetchCurrentUser();
        fetchReceiverUser();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (messagesListener != null) {
            messagesListener.remove();
            messagesListener = null;
        }
        if (conversationListener != null) {
            conversationListener.remove();
            conversationListener = null;
        }
    }

    private void fetchReceiverUser() {
        repository.fetchUser(receiverId, new DataRepository.DataCallback<com.example.taxconnect.model.UserModel>() {
            @Override
            public void onSuccess(com.example.taxconnect.model.UserModel user) {
                receiverUser = user;
                checkBlockingStatus();
            }

            @Override
            public void onError(String error) {
                // Log.e("ChatActivity", "Failed to fetch receiver user: " + error);
            }
        });
    }

    private void checkBlockingStatus() {
        if (currentUser == null || receiverUser == null) return;

        boolean isBlockedByMe = currentUser.getBlockedUsers() != null && currentUser.getBlockedUsers().contains(receiverId);
        boolean isBlockedByOther = receiverUser.getBlockedUsers() != null && receiverUser.getBlockedUsers().contains(currentUserId);

        if (isBlockedByMe) {
            binding.layoutInput.setVisibility(View.GONE);
            binding.actionContainer.setVisibility(View.GONE); // Also hide actions
            Toast.makeText(this, "You have blocked this user.", Toast.LENGTH_LONG).show();
        } else if (isBlockedByOther) {
            binding.layoutInput.setVisibility(View.GONE);
            binding.actionContainer.setVisibility(View.GONE);
            Toast.makeText(this, "You cannot reply to this conversation.", Toast.LENGTH_LONG).show();
        } else {
            binding.layoutInput.setVisibility(View.VISIBLE);
            binding.actionContainer.setVisibility(View.VISIBLE);
        }
    }

    private void requestDocs() {
        String text = "Please upload the required documents so we can proceed.";
        MessageModel message = new MessageModel(
                currentUserId,
                receiverId,
                chatId,
                text,
                System.currentTimeMillis(),
                "TEXT"
        );

        repository.sendMessage(message, new DataRepository.DataCallback<Void>() {
            @Override
            public void onSuccess(Void data) {
                Toast.makeText(ChatActivity.this, "Document Request Sent", Toast.LENGTH_SHORT).show();
            }

            @Override
            public void onError(String error) {
                Toast.makeText(ChatActivity.this, "Failed to send request", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void fetchCurrentUser() {
        repository.fetchUser(currentUserId, new DataRepository.DataCallback<com.example.taxconnect.model.UserModel>() {
            @Override
            public void onSuccess(com.example.taxconnect.model.UserModel user) {
                currentUser = user;
                updateUIBasedOnRoleAndState();
            }

            @Override
            public void onError(String error) {
                Toast.makeText(ChatActivity.this, "Failed to fetch user role", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void updateUIBasedOnRoleAndState() {
        if (currentUser == null) return;
        // Check state first to update hint
        if (com.example.taxconnect.model.ConversationModel.STATE_REQUESTED.equals(currentConversationState)) {
             if ("CA".equals(currentUser.getRole())) {
                 binding.etMessage.setHint("Request Pending...");
             } else {
                 binding.etMessage.setHint("Waiting for CA to accept...");
             }
        } else if (com.example.taxconnect.model.ConversationModel.STATE_REFUSED.equals(currentConversationState)) {
             binding.etMessage.setHint("Request Refused");
        } else {
             binding.etMessage.setHint("Type a message...");
        }

        if ("CA".equals(currentUser.getRole())) {
            // CA Logic
            binding.btnVideoCall.setImageResource(R.drawable.ic_video_call);
            binding.btnVideoCall.setOnClickListener(v -> startVideoCall());
            binding.btnVideoCall.setVisibility(View.VISIBLE);

            // Toggle Video Call Permission Button
            binding.btnToggleVideoCall.setVisibility(View.VISIBLE);
            if (currentConversation != null && currentConversation.isVideoCallAllowed()) {
                binding.btnToggleVideoCall.setText("Disable Video Calls");
                binding.btnToggleVideoCall.setOnClickListener(v -> toggleVideoCallPermission(false));
            } else {
                binding.btnToggleVideoCall.setText("Enable Video Calls");
                binding.btnToggleVideoCall.setOnClickListener(v -> toggleVideoCallPermission(true));
            }
            
            binding.btnCustomerVideoCall.setVisibility(View.GONE); // Hide customer button for CA
            
            if (com.example.taxconnect.model.ConversationModel.STATE_DOCS_REQUEST.equals(currentConversationState)) {
                binding.btnRequestDocs.setVisibility(View.VISIBLE);
                binding.btnCompleteJob.setVisibility(View.VISIBLE);
            } else if (com.example.taxconnect.model.ConversationModel.STATE_ADVANCE_PAYMENT.equals(currentConversationState)) {
                 binding.btnCompleteJob.setVisibility(View.GONE);
                 binding.btnRequestDocs.setVisibility(View.GONE);
            } else {
                binding.btnCompleteJob.setVisibility(View.GONE);
                binding.btnRequestDocs.setVisibility(View.GONE);
            }
            
            binding.btnLeaveFeedback.setVisibility(View.GONE);
            binding.btnBookAppointment.setVisibility(View.GONE);
            
            // Only show create proposal in early stages
            if (com.example.taxconnect.model.ConversationModel.STATE_DISCUSSION.equals(currentConversationState) || 
                com.example.taxconnect.model.ConversationModel.STATE_PRICE_NEGOTIATION.equals(currentConversationState)) {
                binding.btnCreateProposal.setVisibility(View.VISIBLE);
            } else {
                binding.btnCreateProposal.setVisibility(View.GONE);
            }

        } else {
            // Customer Logic
            binding.btnVideoCall.setVisibility(View.GONE); // Hide toolbar button for customer
            binding.btnToggleVideoCall.setVisibility(View.GONE);

            // Customer Video Call Button
            binding.btnCustomerVideoCall.setVisibility(View.VISIBLE);
            if (currentConversation != null && currentConversation.isVideoCallAllowed()) {
                binding.btnCustomerVideoCall.setAlpha(1.0f);
                binding.btnCustomerVideoCall.setOnClickListener(v -> startVideoCall());
            } else {
                binding.btnCustomerVideoCall.setAlpha(0.5f);
                binding.btnCustomerVideoCall.setOnClickListener(v -> 
                    Toast.makeText(this, "Video calls are not enabled by the CA.", Toast.LENGTH_SHORT).show()
                );
            }
            
            binding.btnCompleteJob.setVisibility(View.GONE);
            binding.btnCreateProposal.setVisibility(View.GONE);
            binding.btnRequestDocs.setVisibility(View.GONE);
            binding.btnBookAppointment.setVisibility(View.VISIBLE);

            if (com.example.taxconnect.model.ConversationModel.STATE_COMPLETED.equals(currentConversationState)) {
                binding.btnLeaveFeedback.setVisibility(View.VISIBLE);
            } else {
                binding.btnLeaveFeedback.setVisibility(View.GONE);
            }
        }
    }

    private void toggleVideoCallPermission(boolean allow) {
        com.example.taxconnect.repository.ConversationRepository.getInstance().updateVideoCallPermission(chatId, allow, new DataRepository.DataCallback<Void>() {
            @Override
            public void onSuccess(Void data) {
                String status = allow ? "Enabled" : "Disabled";
                Toast.makeText(ChatActivity.this, "Video Calls " + status, Toast.LENGTH_SHORT).show();
            }

            @Override
            public void onError(String error) {
                Toast.makeText(ChatActivity.this, "Failed to update permission: " + error, Toast.LENGTH_SHORT).show();
            }
        });
    }


    private void completeJob() {
        new AlertDialog.Builder(this)
                .setTitle("Complete Job")
                .setMessage("Are you sure you want to mark this service as completed?")
                .setPositiveButton("Yes", (dialog, which) -> {
                    repository.updateConversationState(chatId, com.example.taxconnect.model.ConversationModel.STATE_COMPLETED, new DataRepository.DataCallback<Void>() {
                        @Override
                        public void onSuccess(Void data) {
                            Toast.makeText(ChatActivity.this, "Job Marked as Completed", Toast.LENGTH_SHORT).show();
                        }

                        @Override
                        public void onError(String error) {
                            Toast.makeText(ChatActivity.this, "Error: " + error, Toast.LENGTH_SHORT).show();
                        }
                    });
                })
                .setNegativeButton("No", null)
                .show();
    }

    private void showFeedbackDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        View view = LayoutInflater.from(this).inflate(R.layout.dialog_feedback, null);
        builder.setView(view);
        AlertDialog dialog = builder.create();

        android.widget.RatingBar ratingBar = view.findViewById(R.id.ratingBar);
        TextInputEditText etComment = view.findViewById(R.id.etComment);
        Button btnSubmit = view.findViewById(R.id.btnSubmitFeedback);
        Button btnCancel = view.findViewById(R.id.btnCancel);

        btnSubmit.setOnClickListener(v -> {
            float rating = ratingBar.getRating();
            String comment = etComment.getText().toString();

            if (rating == 0) {
                Toast.makeText(this, "Please provide a rating", Toast.LENGTH_SHORT).show();
                return;
            }

            com.example.taxconnect.model.ReviewModel review = new com.example.taxconnect.model.ReviewModel(
                    java.util.UUID.randomUUID().toString(),
                    receiverId, // CA ID
                    currentUserId, // Customer ID
                    currentUser.getName(),
                    rating,
                    comment,
                    System.currentTimeMillis()
            );

            repository.submitReview(review, new DataRepository.DataCallback<Void>() {
                @Override
                public void onSuccess(Void data) {
                    Toast.makeText(ChatActivity.this, "Feedback Submitted", Toast.LENGTH_SHORT).show();
                    dialog.dismiss();
                    binding.btnLeaveFeedback.setVisibility(View.GONE); // Hide after submission
                }

                @Override
                public void onError(String error) {
                    Toast.makeText(ChatActivity.this, "Failed to submit: " + error, Toast.LENGTH_SHORT).show();
                }
            });
        });

        btnCancel.setOnClickListener(v -> dialog.dismiss());
        dialog.show();
    }

    @Override
    public void onRequestAccept(MessageModel message) {
        // Update message status to ACCEPTED
        message.setProposalStatus("ACCEPTED");
        message.setMessage("Call Request Accepted");
        repository.updateMessage(message, new DataRepository.DataCallback<Void>() {
            @Override
            public void onSuccess(Void data) {
                // Start call immediately
                startVideoCall();
            }

            @Override
            public void onError(String error) {
                Toast.makeText(ChatActivity.this, "Error accepting request", Toast.LENGTH_SHORT).show();
            }
        });
    }

    @Override
    public void onRequestReject(MessageModel message) {
        // Update message status to REJECTED
        message.setProposalStatus("REJECTED");
        message.setMessage("Call Request Declined");
        repository.updateMessage(message, new DataRepository.DataCallback<Void>() {
            @Override
            public void onSuccess(Void data) {
                // Done
            }

            @Override
            public void onError(String error) {
                Toast.makeText(ChatActivity.this, "Error rejecting request", Toast.LENGTH_SHORT).show();
            }
        });
    }

    @Override
    public void onJoinCall(MessageModel message) {
        // Join the call
        Intent intent = new Intent(ChatActivity.this, VideoCallActivity.class);
        intent.putExtra("CHANNEL_NAME", message.getChatId());
        startActivity(intent);
    }
    
    @Override
    public boolean onCreateOptionsMenu(android.view.Menu menu) {
        getMenuInflater().inflate(R.menu.chat_menu, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull android.view.MenuItem item) {
        if (item.getItemId() == R.id.action_block) {
            new AlertDialog.Builder(this)
                    .setTitle("Block User")
                    .setMessage("Are you sure you want to block this user?")
                    .setPositiveButton("Block", (dialog, which) -> blockUser())
                    .setNegativeButton("Cancel", null)
                    .show();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private void blockUser() {
        repository.blockUser(currentUserId, receiverId, new DataRepository.DataCallback<Void>() {
            @Override
            public void onSuccess(Void data) {
                Toast.makeText(ChatActivity.this, "User blocked", Toast.LENGTH_SHORT).show();
                finish();
            }

            @Override
            public void onError(String error) {
                Toast.makeText(ChatActivity.this, "Failed to block user: " + error, Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void startVideoCall() {
        if (checkSelfPermission(REQUESTED_PERMISSIONS[0], PERMISSION_REQ_ID) &&
                checkSelfPermission(REQUESTED_PERMISSIONS[1], PERMISSION_REQ_ID)) {
            
            // Reset call status to ACTIVE to avoid immediate rejection
            com.example.taxconnect.repository.ConversationRepository.getInstance().updateCallStatus(chatId, "ACTIVE", null);

            // Send a call started message to notify the other user
            MessageModel callMsg = new MessageModel(
                    currentUserId,
                    receiverId,
                    chatId,
                    "📞 Video Call Started",
                    System.currentTimeMillis(),
                    "CALL"
            );
            com.example.taxconnect.repository.ConversationRepository.getInstance().sendMessage(callMsg, new DataRepository.DataCallback<Void>() {
                @Override
                public void onSuccess(Void data) {
                    // Message sent, now start activity
                    Intent intent = new Intent(ChatActivity.this, VideoCallActivity.class);
                    intent.putExtra("CHANNEL_NAME", chatId);
                    startActivity(intent);
                }

                @Override
                public void onError(String error) {
                    // Start anyway even if message fails (local issue)
                    Intent intent = new Intent(ChatActivity.this, VideoCallActivity.class);
                    intent.putExtra("CHANNEL_NAME", chatId);
                    startActivity(intent);
                }
            });
        }
    }
    
    private boolean checkSelfPermission(String permission, int requestCode) {
        if (ContextCompat.checkSelfPermission(this, permission) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, REQUESTED_PERMISSIONS, requestCode);
            return false;
        }
        return true;
    }

    private void showAttachmentOptions() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Choose Action");
        String[] options = {"Pick Image", "Take Photo", "Attach Document", "Attach from Vault"};
        builder.setItems(options, (dialog, which) -> {
            if (which == 0) {
                pickImageLauncher.launch("image/*");
            } else if (which == 1) {
                if (checkSelfPermission(Manifest.permission.CAMERA, PERMISSION_REQ_ID)) {
                    launchCamera();
                }
            } else if (which == 2) {
                pickDocumentLauncher.launch("application/*");
            } else if (which == 3) {
                openVaultPicker();
            }
        });
        builder.show();
    }

    private void openVaultPicker() {
        repository.getDocuments(currentUserId, new DataRepository.DataCallback<List<com.example.taxconnect.model.DocumentModel>>() {
            @Override
            public void onSuccess(List<com.example.taxconnect.model.DocumentModel> data) {
                if (data.isEmpty()) {
                    Toast.makeText(ChatActivity.this, "Vault is empty", Toast.LENGTH_SHORT).show();
                    return;
                }
                
                String[] docNames = new String[data.size()];
                for (int i = 0; i < data.size(); i++) {
                    docNames[i] = data.get(i).getName();
                }
                
                new AlertDialog.Builder(ChatActivity.this)
                    .setTitle("Select from Vault")
                    .setItems(docNames, (dialog, which) -> {
                        com.example.taxconnect.model.DocumentModel selected = data.get(which);
                        sendDocumentMessage(selected.getUrl(), selected.getName());
                    })
                    .show();
            }

            @Override
            public void onError(String error) {
                Toast.makeText(ChatActivity.this, "Failed to load vault", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void sendDocumentMessage(String url, String name) {
        MessageModel message = new MessageModel(
                currentUserId,
                receiverId,
                chatId,
                name != null ? name : "Shared Document",
                System.currentTimeMillis(),
                "DOCUMENT"
        );
        message.setImageUrl(url);

        repository.sendMessage(message, new DataRepository.DataCallback<Void>() {
            @Override
            public void onSuccess(Void data) {
                // Sent
            }

            @Override
            public void onError(String error) {
                Toast.makeText(ChatActivity.this, "Failed to share document", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void showBookingBottomSheet() {
        BottomSheetDialog bottomSheetDialog = new BottomSheetDialog(this);
        View view = getLayoutInflater().inflate(R.layout.layout_booking_bottom_sheet, null);
        bottomSheetDialog.setContentView(view);

        androidx.recyclerview.widget.RecyclerView rvDates = view.findViewById(R.id.rvDates);
        androidx.recyclerview.widget.RecyclerView rvTimeSlots = view.findViewById(R.id.rvTimeSlots);
        Button btnConfirm = view.findViewById(R.id.btnConfirmBooking);
        com.google.android.material.textfield.TextInputEditText etMessage = view.findViewById(R.id.etBookingMessage);

        // Setup Dates
        List<Calendar> dates = new ArrayList<>();
        Calendar calendar = Calendar.getInstance();
        for (int i = 0; i < 14; i++) {
            Calendar date = (Calendar) calendar.clone();
            dates.add(date);
            calendar.add(Calendar.DAY_OF_YEAR, 1);
        }
        
        final Calendar[] selectedDate = {dates.get(0)};
        final String[] selectedTime = {null};

        DateAdapter dateAdapter = new DateAdapter(dates, date -> {
            selectedDate[0] = date;
            checkBookingReadiness(selectedDate[0], selectedTime[0], btnConfirm);
        });
        rvDates.setAdapter(dateAdapter);

        // Setup Time Slots
        List<String> timeSlots = Arrays.asList(
            "10:00 AM", "11:00 AM", "12:00 PM", 
            "02:00 PM", "03:00 PM", "04:00 PM", "05:00 PM", "06:00 PM"
        );
        
        TimeSlotAdapter timeSlotAdapter = new TimeSlotAdapter(timeSlots, time -> {
            selectedTime[0] = time;
            checkBookingReadiness(selectedDate[0], selectedTime[0], btnConfirm);
        });
        rvTimeSlots.setAdapter(timeSlotAdapter);

        btnConfirm.setOnClickListener(v -> {
            if (selectedDate[0] != null && selectedTime[0] != null) {
                Calendar bookingTime = (Calendar) selectedDate[0].clone();
                try {
                    java.util.Date timeDate = new java.text.SimpleDateFormat("hh:mm a", java.util.Locale.getDefault()).parse(selectedTime[0]);
                    Calendar timeCal = Calendar.getInstance();
                    timeCal.setTime(timeDate);
                    bookingTime.set(Calendar.HOUR_OF_DAY, timeCal.get(Calendar.HOUR_OF_DAY));
                    bookingTime.set(Calendar.MINUTE, timeCal.get(Calendar.MINUTE));
                    bookingTime.set(Calendar.SECOND, 0);
                    bookingTime.set(Calendar.MILLISECOND, 0);
                    
                    String message = etMessage.getText().toString();
                    bottomSheetDialog.dismiss();
                    confirmBooking(bookingTime.getTimeInMillis(), message);
                } catch (Exception e) {
                    e.printStackTrace();
                    Toast.makeText(this, "Error parsing time", Toast.LENGTH_SHORT).show();
                }
            }
        });

        bottomSheetDialog.show();
    }

    private void checkBookingReadiness(Calendar date, String time, Button btn) {
        boolean enabled = date != null && time != null;
        btn.setEnabled(enabled);
        btn.setAlpha(enabled ? 1.0f : 0.5f);
        if (enabled) {
            btn.setBackgroundTintList(getColorStateList(R.color.primary));
        }
    }

    private void confirmBooking(long timestamp, String message) {
        new AlertDialog.Builder(this)
                .setTitle("Confirm Booking")
                .setMessage("Book a slot for " + new java.text.SimpleDateFormat("dd MMM yyyy, hh:mm a").format(new java.util.Date(timestamp)) + "?")
                .setPositiveButton("Confirm", (dialog, which) -> {
                    saveBooking(timestamp, message);
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void saveBooking(long timestamp, String message) {
        BookingModel booking = new BookingModel();
        booking.setCaId(receiverId);
        booking.setUserId(currentUserId);
        booking.setUserName(currentUser != null ? currentUser.getName() : "User");
        booking.setCaName(receiverUser != null ? receiverUser.getName() : getIntent().getStringExtra("RECEIVER_NAME"));
        booking.setAppointmentTimestamp(timestamp);
        booking.setStatus("PENDING");
        booking.setMessage(message);
        
        repository.saveBooking(booking, new DataRepository.DataCallback<Void>() {
            @Override
            public void onSuccess(Void data) {
                Toast.makeText(ChatActivity.this, "Booking Requested Successfully!", Toast.LENGTH_SHORT).show();
                
                // Optionally send a message
                String dateStr = new java.text.SimpleDateFormat("dd MMM yyyy, hh:mm a").format(new java.util.Date(timestamp));
                String msgText = "I have requested a booking for " + dateStr;
                MessageModel message = new MessageModel(
                        currentUserId,
                        receiverId,
                        chatId,
                        msgText,
                        System.currentTimeMillis(),
                        "TEXT"
                );
                
                repository.sendMessage(message, new DataRepository.DataCallback<Void>() {
                     @Override
                     public void onSuccess(Void data) {}
                     @Override
                     public void onError(String error) {}
                });
            }

            @Override
            public void onError(String error) {
                Toast.makeText(ChatActivity.this, "Failed to book: " + error, Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void launchCamera() {
        try {
            File photoFile = createImageFile();
            if (photoFile != null) {
                photoUri = androidx.core.content.FileProvider.getUriForFile(this, getPackageName() + ".provider", photoFile);
                takePictureLauncher.launch(photoUri);
            }
        } catch (Exception e) {
            e.printStackTrace();
            Toast.makeText(this, "Error starting camera", Toast.LENGTH_SHORT).show();
        }
    }

    private File createImageFile() throws java.io.IOException {
        String timeStamp = new java.text.SimpleDateFormat("yyyyMMdd_HHmmss").format(new java.util.Date());
        String imageFileName = "JPEG_" + timeStamp + "_";
        File storageDir = getCacheDir();
        return File.createTempFile(imageFileName, ".jpg", storageDir);
    }

    private void uploadDocument(Uri uri) {
        Toast.makeText(this, "Uploading Document...", Toast.LENGTH_SHORT).show();
        
        try {
            // Get original file name
            String fileName = "Document";
            android.database.Cursor cursor = getContentResolver().query(uri, null, null, null, null);
            if (cursor != null && cursor.moveToFirst()) {
                int nameIndex = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME);
                if (nameIndex != -1) {
                    fileName = cursor.getString(nameIndex);
                }
                cursor.close();
            }

            String mimeType = getContentResolver().getType(uri);
            String extension = ".pdf"; // default
            if (mimeType != null) {
                extension = "." + android.webkit.MimeTypeMap.getSingleton().getExtensionFromMimeType(mimeType);
            }
            
            File tempFile = File.createTempFile("doc_upload", extension, getCacheDir());
            InputStream inputStream = getContentResolver().openInputStream(uri);
            FileOutputStream outputStream = new FileOutputStream(tempFile);
            byte[] buffer = new byte[1024];
            int length;
            while ((length = inputStream.read(buffer)) > 0) {
                outputStream.write(buffer, 0, length);
            }
            outputStream.close();
            inputStream.close();
            
            String finalMimeType = mimeType;
            String finalFileName = fileName;
            
            CloudinaryHelper.uploadImage(tempFile.getAbsolutePath(), new CloudinaryHelper.ImageUploadCallback() {
                @Override
                public void onSuccess(String url) {
                    runOnUiThread(() -> sendAttachmentMessage(url, finalMimeType, finalFileName));
                }

                @Override
                public void onError(String error) {
                    runOnUiThread(() -> Toast.makeText(ChatActivity.this, "Upload failed: " + error, Toast.LENGTH_SHORT).show());
                }
            });
            
        } catch (Exception e) {
            e.printStackTrace();
            Toast.makeText(this, "Error preparing file", Toast.LENGTH_SHORT).show();
        }
    }

    private void uploadImage(Uri uri) {
        Toast.makeText(this, "Uploading...", Toast.LENGTH_SHORT).show();
        
        try {
            String mimeType = getContentResolver().getType(uri);
            String extension = ".jpg"; // default
            if (mimeType != null) {
                extension = "." + android.webkit.MimeTypeMap.getSingleton().getExtensionFromMimeType(mimeType);
            }
            
            File tempFile = File.createTempFile("upload", extension, getCacheDir());
            InputStream inputStream = getContentResolver().openInputStream(uri);
            FileOutputStream outputStream = new FileOutputStream(tempFile);
            byte[] buffer = new byte[1024];
            int length;
            while ((length = inputStream.read(buffer)) > 0) {
                outputStream.write(buffer, 0, length);
            }
            outputStream.close();
            inputStream.close();
            
            String finalMimeType = mimeType;
            CloudinaryHelper.uploadImage(tempFile.getAbsolutePath(), new CloudinaryHelper.ImageUploadCallback() {
                @Override
                public void onSuccess(String url) {
                    runOnUiThread(() -> sendAttachmentMessage(url, finalMimeType, null));
                }

                @Override
                public void onError(String error) {
                    runOnUiThread(() -> Toast.makeText(ChatActivity.this, "Upload failed: " + error, Toast.LENGTH_SHORT).show());
                }
            });
            
        } catch (Exception e) {
            e.printStackTrace();
            Toast.makeText(this, "Error preparing file", Toast.LENGTH_SHORT).show();
        }
    }

    private void sendAttachmentMessage(String url, String mimeType, String fileName) {
        long timestamp = System.currentTimeMillis();
        String type = "IMAGE";
        String messageText = "Attachment";

        if (mimeType != null && !mimeType.startsWith("image/")) {
            type = "DOCUMENT";
            if (fileName != null) {
                messageText = fileName;
            }
        }
        
        MessageModel message = new MessageModel(currentUserId, receiverId, chatId, messageText, timestamp, type);
        message.setImageUrl(url);
        
        repository.sendMessage(message, new DataRepository.DataCallback<Void>() {
            @Override
            public void onSuccess(Void data) {
                binding.etMessage.setText("");
                
                // If this is a document upload during DOCS_REQUEST state, maybe we should update status?
                // For now, let's just send it.
            }

            @Override
            public void onError(String error) {
                Toast.makeText(ChatActivity.this, "Failed to send attachment", Toast.LENGTH_SHORT).show();
            }
        });
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == PERMISSION_REQ_ID) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                startVideoCall();
            } else {
                Toast.makeText(this, "Permissions required for video call", Toast.LENGTH_SHORT).show();
            }
        }
    }

    private void setupRecyclerView() {
        adapter = new MessageAdapter(this);
        LinearLayoutManager layoutManager = new LinearLayoutManager(this);
        layoutManager.setStackFromEnd(true);
        binding.rvChatMessages.setLayoutManager(layoutManager);
        binding.rvChatMessages.setAdapter(adapter);
        binding.tvEmptyState.setVisibility(View.VISIBLE);
    }
    
    private void showProposalDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        View view = LayoutInflater.from(this).inflate(R.layout.dialog_create_proposal, null);
        builder.setView(view);

        AlertDialog dialog = builder.create();

        TextInputEditText etDescription = view.findViewById(R.id.etDescription);
        TextInputEditText etAmount = view.findViewById(R.id.etAmount);
        Button btnSend = view.findViewById(R.id.btnSendProposal);
        Button btnCancel = view.findViewById(R.id.btnCancel);

        btnSend.setOnClickListener(v -> {
            String description = etDescription.getText().toString().trim();
            String amount = etAmount.getText().toString().trim();

            if (description.isEmpty() || amount.isEmpty()) {
                Toast.makeText(this, "Please fill all fields", Toast.LENGTH_SHORT).show();
                return;
            }

            sendProposal(description, amount);
            dialog.dismiss();
        });

        btnCancel.setOnClickListener(v -> dialog.dismiss());

        dialog.show();
    }
    
    private void sendProposal(String description, String amount) {
        MessageModel proposal = new MessageModel(
                currentUserId,
                receiverId,
                chatId,
                "Sent a proposal: " + description,
                System.currentTimeMillis(),
                "PROPOSAL",
                description,
                amount,
                "PENDING"
        );
        
        repository.sendMessage(proposal, new DataRepository.DataCallback<Void>() {
            @Override
            public void onSuccess(Void data) {
                Toast.makeText(ChatActivity.this, "Proposal Sent", Toast.LENGTH_SHORT).show();
                // Update workflow state
                repository.updateConversationState(chatId, com.example.taxconnect.model.ConversationModel.STATE_PRICE_AGREEMENT, new DataRepository.DataCallback<Void>() {
                    @Override
                    public void onSuccess(Void data) {
                        // State updated
                    }

                    @Override
                    public void onError(String error) {
                        // Ignore error
                    }
                });
            }

            @Override
            public void onError(String error) {
                Toast.makeText(ChatActivity.this, "Failed to send proposal: " + error, Toast.LENGTH_SHORT).show();
            }
        });
    }

    @Override
    public void onAccept(MessageModel proposal) {
        currentProposal = proposal;
        
        String email = "test@example.com";
        String contact = "9999999999";
        
        if (currentUser != null) {
            if (currentUser.getEmail() != null && !currentUser.getEmail().isEmpty()) {
                email = currentUser.getEmail();
            }
            // Add contact logic if available in future
        }

        // Trigger Payment
        paymentManager.startPayment(this, proposal.getProposalAmount(), email, contact, proposal.getProposalDescription());
    }

    @Override
    public void onPaymentSuccess(String razorpayPaymentID) {
        if (currentProposal != null) {
            repository.updateProposalStatus(currentProposal.getChatId(), currentProposal.getId(), "ACCEPTED", new DataRepository.DataCallback<Void>() {
                @Override
                public void onSuccess(Void data) {
                    Toast.makeText(ChatActivity.this, "Payment Successful. Proposal Accepted.", Toast.LENGTH_SHORT).show();
                    
                    double amount = Double.parseDouble(currentProposal.getProposalAmount());
                    
                    // 1. Record Transaction for Client (Expense)
                    // Note: Since payment is via Gateway, we don't deduct from wallet balance,
                    // but we record it in history with "GATEWAY" status or similar to indicate source.
                    // Or "WALLET" if we treat it as generic. Let's use "Payment".
                    
                    com.example.taxconnect.model.TransactionModel clientTxn = new com.example.taxconnect.model.TransactionModel(
                            java.util.UUID.randomUUID().toString(),
                            currentUserId, // Client ID
                            receiverId, // CA ID
                            currentUser != null ? currentUser.getName() : "Client",
                            "Paid for: " + currentProposal.getProposalDescription(),
                            -amount, // Negative for expense
                            "SUCCESS"
                    );
                    
                    repository.createTransaction(clientTxn, new DataRepository.DataCallback<Void>() {
                        @Override
                        public void onSuccess(Void data) {
                            // Client transaction recorded
                        }
                        @Override
                        public void onError(String error) {}
                    });
                    
                    // CA Transaction (Income)
                    com.example.taxconnect.model.TransactionModel caTxn = new com.example.taxconnect.model.TransactionModel(
                            java.util.UUID.randomUUID().toString(),
                            receiverId, // CA ID
                            currentUserId, // Client ID
                            currentUser != null ? currentUser.getName() : "Client",
                            "Payment for: " + currentProposal.getProposalDescription(),
                            amount,
                            "SUCCESS"
                    );
                    
                    repository.createTransaction(caTxn, new DataRepository.DataCallback<Void>() {
                        @Override
                        public void onSuccess(Void data) {
                            // Update CA Balance
                            repository.updateWalletBalance(receiverId, amount, new DataRepository.DataCallback<Double>() {
                                @Override
                                public void onSuccess(Double newBalance) {
                                    // Done
                                }
                                @Override
                                public void onError(String error) {}
                            });
                        }
                        @Override
                        public void onError(String error) {}
                    });

                    // Update workflow state to Document Request
                    repository.updateConversationState(chatId, com.example.taxconnect.model.ConversationModel.STATE_DOCS_REQUEST, new DataRepository.DataCallback<Void>() {
                        @Override
                        public void onSuccess(Void data) {
                             // State updated
                        }
                        @Override
                        public void onError(String error) {}
                    });
                }

                @Override
                public void onError(String error) {
                    Toast.makeText(ChatActivity.this, "Payment success but status update failed: " + error, Toast.LENGTH_SHORT).show();
                }
            });
        }
    }

    @Override
    public void onPaymentError(int code, String response) {
        Toast.makeText(this, "Payment Failed: " + response, Toast.LENGTH_SHORT).show();
    }

    @Override
    public void onReject(MessageModel proposal) {
        repository.updateProposalStatus(proposal.getChatId(), proposal.getId(), "REJECTED", new DataRepository.DataCallback<Void>() {
            @Override
            public void onSuccess(Void data) {
                Toast.makeText(ChatActivity.this, "Proposal Rejected", Toast.LENGTH_SHORT).show();
            }

            @Override
            public void onError(String error) {
                Toast.makeText(ChatActivity.this, "Error rejecting proposal: " + error, Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void listenForMessages() {
        if (messagesListener != null) {
            messagesListener.remove();
        }
        messagesListener = com.example.taxconnect.repository.ConversationRepository.getInstance().listenForRecentMessages(chatId, 50, new DataRepository.DataCallback<List<MessageModel>>() {
            @Override
            public void onSuccess(List<MessageModel> data) {
                adapter.setMessages(data);
                binding.tvEmptyState.setVisibility(data == null || data.isEmpty() ? View.VISIBLE : View.GONE);
                if (!data.isEmpty()) {
                    binding.rvChatMessages.smoothScrollToPosition(data.size() - 1);
                }
            }

            @Override
            public void onError(String error) {
                Toast.makeText(ChatActivity.this, "Error loading messages: " + error, Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void listenForConversationUpdates() {
        if (conversationListener != null) {
            conversationListener.remove();
        }
        conversationListener = com.example.taxconnect.repository.ConversationRepository.getInstance().listenToConversation(chatId, new DataRepository.DataCallback<com.example.taxconnect.model.ConversationModel>() {
            @Override
            public void onSuccess(com.example.taxconnect.model.ConversationModel data) {
                if (data != null && data.getWorkflowState() != null) {
                    currentConversation = data;
                    currentConversationState = data.getWorkflowState();
                    binding.tvWorkflowStatus.setText("Status: " + currentConversationState);
                    
                    // Optional: Color coding
                    int colorRes = R.color.primary;
                    if (com.example.taxconnect.model.ConversationModel.STATE_COMPLETED.equals(currentConversationState)) {
                        colorRes = R.color.emerald_600;
                    } else if (com.example.taxconnect.model.ConversationModel.STATE_ADVANCE_PAYMENT.equals(currentConversationState)) {
                        colorRes = R.color.secondary;
                    }
                    binding.tvWorkflowStatus.setTextColor(ContextCompat.getColor(ChatActivity.this, colorRes));
                    
                    // Refresh UI Actions based on new state
                    updateUIBasedOnRoleAndState();
                }
            }

            @Override
            public void onError(String error) {
                // Log.e("ChatActivity", "Error listening to conversation: " + error);
            }
        });
    }

    private void sendMessage() {
        String text = binding.etMessage.getText().toString().trim();
        if (text.isEmpty()) return;

        if (currentConversationState == null || com.example.taxconnect.model.ConversationModel.STATE_REFUSED.equals(currentConversationState)) {
             repository.sendRequest(currentUserId, receiverId, text, new DataRepository.DataCallback<Void>() {
                 @Override
                 public void onSuccess(Void data) {
                     binding.etMessage.setText("");
                     Toast.makeText(ChatActivity.this, "Request Sent", Toast.LENGTH_SHORT).show();
                 }

                 @Override
                 public void onError(String error) {
                     Toast.makeText(ChatActivity.this, "Failed to send request: " + error, Toast.LENGTH_SHORT).show();
                 }
             });
             return;
        }

        MessageModel message = new MessageModel(
                currentUserId,
                receiverId,
                chatId,
                text,
                System.currentTimeMillis(),
                "TEXT"
        );

        com.example.taxconnect.repository.ConversationRepository.getInstance().sendMessage(message, new DataRepository.DataCallback<Void>() {
            @Override
            public void onSuccess(Void data) {
                binding.etMessage.setText("");
                // Toast.makeText(ChatActivity.this, "Sent", Toast.LENGTH_SHORT).show();
            }

            @Override
            public void onError(String error) {
                Toast.makeText(ChatActivity.this, "Failed to send: " + error, Toast.LENGTH_SHORT).show();
            }
        });
    }
}
