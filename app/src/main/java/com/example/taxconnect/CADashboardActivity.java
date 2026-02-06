package com.example.taxconnect;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Toast;
import android.view.MenuItem;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.CompoundButton;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.view.GravityCompat;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.example.taxconnect.databinding.ActivityCaDashboardBinding;
import com.example.taxconnect.model.ConversationModel;
import com.example.taxconnect.model.UserModel;
import com.bumptech.glide.Glide;
import com.google.android.material.navigation.NavigationView;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.messaging.FirebaseMessaging;

import java.util.ArrayList;
import java.util.List;
import android.content.pm.PackageManager;
import android.Manifest;
import android.os.Build;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

public class CADashboardActivity extends AppCompatActivity {

    private ActivityCaDashboardBinding binding;
    private DataRepository repository;
    private com.example.taxconnect.adapter.BookingAdapter bookingAdapter;
    private String currentUserId;
    private List<com.example.taxconnect.model.BookingModel> bookings = new ArrayList<>();

    private final CompoundButton.OnCheckedChangeListener onlineStatusListener = new CompoundButton.OnCheckedChangeListener() {
        @Override
        public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
            updateOnlineStatusUI(isChecked);
            repository.updateUserStatus(currentUserId, isChecked, new DataRepository.DataCallback<Void>() {
                @Override
                public void onSuccess(Void data) {
                    // Updated
                }

                @Override
                public void onError(String error) {
                    Toast.makeText(CADashboardActivity.this, "Failed to update status", Toast.LENGTH_SHORT).show();
                    // Revert if failed
                    buttonView.setOnCheckedChangeListener(null);
                    buttonView.setChecked(!isChecked);
                    updateOnlineStatusUI(!isChecked);
                    buttonView.setOnCheckedChangeListener(onlineStatusListener);
                }
            });
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityCaDashboardBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        repository = DataRepository.getInstance();
        currentUserId = FirebaseAuth.getInstance().getUid();

        setupRecyclerView();
        loadDashboardData();
        setupDrawer();
        loadBookings();
        
        binding.ivMyChats.setOnClickListener(v -> {
            startActivity(new Intent(CADashboardActivity.this, MyChatsActivity.class));
        });

        binding.ivProfile.setOnClickListener(v -> {
            startActivity(new Intent(CADashboardActivity.this, ProfileActivity.class));
        });

        binding.cardRevenue.setOnClickListener(v -> {
            startActivity(new Intent(CADashboardActivity.this, BalanceSheetActivity.class));
        });

        binding.cardActiveClients.setOnClickListener(v -> {
            startActivity(new Intent(CADashboardActivity.this, MyChatsActivity.class));
        });

        binding.cardPendingRequests.setOnClickListener(v -> {
            startActivity(new Intent(CADashboardActivity.this, RequestsActivity.class));
        });

        binding.btnViewAllRequests.setOnClickListener(v -> {
            startActivity(new Intent(CADashboardActivity.this, RequestsActivity.class));
        });

        binding.cardPendingBookings.setOnClickListener(v -> {
            startActivity(new Intent(CADashboardActivity.this, MyBookingsActivity.class));
        });

        binding.btnViewAllBookings.setOnClickListener(v -> {
            startActivity(new Intent(CADashboardActivity.this, MyBookingsActivity.class));
        });

        binding.cardExplore.setOnClickListener(v -> {
            startActivity(new Intent(CADashboardActivity.this, ExploreCAsActivity.class));
        });

        binding.switchOnline.setOnCheckedChangeListener(onlineStatusListener);
        
        askNotificationPermission();
        updateFcmToken();
    }

    private void updateFcmToken() {
        FirebaseMessaging.getInstance().getToken()
            .addOnCompleteListener(task -> {
                if (!task.isSuccessful()) {
                    return;
                }
                String token = task.getResult();
                if (repository != null) {
                    repository.updateFcmToken(token);
                }
            });
    }

    private void askNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) !=
                    PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.POST_NOTIFICATIONS}, 101);
            }
        }
    }

    private void setupDrawer() {
        binding.ivMenu.setOnClickListener(v -> binding.drawerLayout.openDrawer(GravityCompat.START));

        binding.navView.setNavigationItemSelectedListener(new NavigationView.OnNavigationItemSelectedListener() {
            @Override
            public boolean onNavigationItemSelected(@NonNull MenuItem item) {
                int id = item.getItemId();

                if (id == R.id.nav_home) {
                    // Already on Dashboard
                } else if (id == R.id.nav_profile) {
                    startActivity(new Intent(CADashboardActivity.this, ProfileActivity.class));
                } else if (id == R.id.nav_chats) {
                     startActivity(new Intent(CADashboardActivity.this, MyChatsActivity.class));
                } else if (id == R.id.nav_requests) {
                     startActivity(new Intent(CADashboardActivity.this, RequestsActivity.class));
                } else if (id == R.id.nav_explore) {
                     startActivity(new Intent(CADashboardActivity.this, ExploreCAsActivity.class));
                } else if (id == R.id.nav_wallet) {
                   startActivity(new Intent(CADashboardActivity.this, WalletActivity.class));
                } else if (id == R.id.nav_bookings) {
                   startActivity(new Intent(CADashboardActivity.this, MyBookingsActivity.class));
                } else if (id == R.id.nav_docs) {
                   startActivity(new Intent(CADashboardActivity.this, MyDocumentsActivity.class));
                } else if (id == R.id.nav_community) {
                   startActivity(new Intent(CADashboardActivity.this, CommunityActivity.class));
                } else if (id == R.id.nav_theme_light) {
                    com.example.taxconnect.utils.ThemeHelper.setTheme(CADashboardActivity.this, com.example.taxconnect.utils.ThemeHelper.THEME_LIGHT);
                } else if (id == R.id.nav_theme_dark) {
                    com.example.taxconnect.utils.ThemeHelper.setTheme(CADashboardActivity.this, com.example.taxconnect.utils.ThemeHelper.THEME_DARK);
                } else if (id == R.id.nav_theme_system) {
                    com.example.taxconnect.utils.ThemeHelper.setTheme(CADashboardActivity.this, com.example.taxconnect.utils.ThemeHelper.THEME_SYSTEM);
                } else if (id == R.id.nav_logout) {
                    FirebaseAuth.getInstance().signOut();
                    Intent intent = new Intent(CADashboardActivity.this, LoginActivity.class);
                    intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                    startActivity(intent);
                    finish();
                }

                binding.drawerLayout.closeDrawer(GravityCompat.START);
                return true;
            }
        });

        // Set current theme checked
        android.view.Menu menu = binding.navView.getMenu();
        int currentTheme = com.example.taxconnect.utils.ThemeHelper.getSelectedTheme(this);
        if (currentTheme == com.example.taxconnect.utils.ThemeHelper.THEME_LIGHT) {
            menu.findItem(R.id.nav_theme_light).setChecked(true);
        } else if (currentTheme == com.example.taxconnect.utils.ThemeHelper.THEME_DARK) {
            menu.findItem(R.id.nav_theme_dark).setChecked(true);
        } else {
            menu.findItem(R.id.nav_theme_system).setChecked(true);
        }

        // Update Header Info
        updateNavHeader();
    }

    private void updateNavHeader() {
        View headerView = binding.navView.getHeaderView(0);
        TextView navName = headerView.findViewById(R.id.nav_header_name);
        TextView navEmail = headerView.findViewById(R.id.nav_header_email);
        ImageView navImage = headerView.findViewById(R.id.nav_header_image);

        repository.fetchUser(currentUserId, new DataRepository.DataCallback<UserModel>() {
            @Override
            public void onSuccess(UserModel user) {
                if (user != null) {
                    navName.setText(user.getName());
                    navEmail.setText(user.getEmail());

                    // Load Profile Image into Navigation Header
                    Glide.with(CADashboardActivity.this)
                        .load(user.getProfileImageUrl())
                        .placeholder(android.R.drawable.ic_menu_gallery)
                        .error(android.R.drawable.ic_menu_gallery)
                        .circleCrop()
                        .into(navImage);

                    // Load Profile Image into Dashboard Header
                    Glide.with(CADashboardActivity.this)
                        .load(user.getProfileImageUrl())
                        .placeholder(R.drawable.ic_launcher_foreground)
                        .error(R.drawable.ic_launcher_foreground)
                        .circleCrop()
                        .into(binding.ivProfile);

                    // Set initial switch state
                    binding.switchOnline.setOnCheckedChangeListener(null);
                    binding.switchOnline.setChecked(user.isOnline());
                    updateOnlineStatusUI(user.isOnline());
                    binding.switchOnline.setOnCheckedChangeListener(onlineStatusListener);
                }
            }

            @Override
            public void onError(String error) {
                // Ignore
            }
        });
    }

    private void updateOnlineStatusUI(boolean isOnline) {
        if (isOnline) {
            binding.switchOnline.setText("Online");
            binding.switchOnline.setTextColor(getColor(R.color.emerald_600));
        } else {
            binding.switchOnline.setText("Offline");
            binding.switchOnline.setTextColor(getColor(R.color.slate_500));
        }
    }

    private void setupRecyclerView() {
        bookingAdapter = new com.example.taxconnect.adapter.BookingAdapter(new com.example.taxconnect.adapter.BookingAdapter.OnBookingActionListener() {
            @Override
            public void onAccept(com.example.taxconnect.model.BookingModel booking) {
                updateBookingStatus(booking, "ACCEPTED");
            }

            @Override
            public void onReject(com.example.taxconnect.model.BookingModel booking) {
                updateBookingStatus(booking, "REJECTED");
            }

            @Override
            public void onBookingClick(com.example.taxconnect.model.BookingModel booking) {
                // Open booking details or chat
                // For now, maybe just show a toast or open chat if accepted
                if ("ACCEPTED".equals(booking.getStatus())) {
                    Intent intent = new Intent(CADashboardActivity.this, ChatActivity.class);
                    intent.putExtra("bookingId", booking.getId());
                    intent.putExtra("userId", booking.getUserId());
                    intent.putExtra("userName", booking.getUserName());
                    startActivity(intent);
                }
            }

            @Override
            public void onMilestonesClick(com.example.taxconnect.model.BookingModel booking) {
                Intent intent = new Intent(CADashboardActivity.this, MilestonesActivity.class);
                intent.putExtra("bookingId", booking.getId());
                startActivity(intent);
            }
        }, true); // isCaView = true
        binding.rvBookings.setLayoutManager(new LinearLayoutManager(this));
        binding.rvBookings.setAdapter(bookingAdapter);
    }

    private void updateBookingStatus(com.example.taxconnect.model.BookingModel booking, String status) {
        repository.updateBookingStatus(booking.getId(), status, new DataRepository.DataCallback<Void>() {
            @Override
            public void onSuccess(Void data) {
                if ("ACCEPTED".equals(status)) {
                    repository.incrementClientCount(booking.getCaId(), booking.getUserId());
                }
                Toast.makeText(CADashboardActivity.this, "Booking " + status, Toast.LENGTH_SHORT).show();
                loadBookings(); // Refresh list
            }

            @Override
            public void onError(String error) {
                Toast.makeText(CADashboardActivity.this, "Failed to update: " + error, Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void loadDashboardData() {
        // Load Revenue Stats
        repository.getRevenueStats(currentUserId, new DataRepository.DataCallback<Double>() {
            @Override
            public void onSuccess(Double revenue) {
                binding.tvRevenue.setText("₹" + revenue);
            }

            @Override
            public void onError(String error) {
                Toast.makeText(CADashboardActivity.this, "Error loading revenue: " + error, Toast.LENGTH_SHORT).show();
            }
        });

        // Load Client Count (from conversations)
        repository.getConversations(currentUserId, new DataRepository.DataCallback<List<ConversationModel>>() {
            @Override
            public void onSuccess(List<ConversationModel> conversations) {
                binding.tvClientCount.setText(String.valueOf(conversations.size()));
            }

            @Override
            public void onError(String error) {
                // Ignore for stats
            }
        });

        // Load Pending Requests Count
        repository.getRequests(currentUserId, new DataRepository.DataCallback<List<ConversationModel>>() {
            @Override
            public void onSuccess(List<ConversationModel> data) {
                if (data != null) {
                    binding.tvPendingRequestsCount.setText(data.size() + " New Requests");
                    if (!data.isEmpty()) {
                        binding.tvPendingRequestsCount.setTextColor(getResources().getColor(android.R.color.holo_red_dark));
                    }
                }
            }

            @Override
            public void onError(String error) {
                binding.tvPendingRequestsCount.setText("0 New Requests");
            }
        });
    }

    private void loadBookings() {
        repository.getBookingsForCA(currentUserId, new DataRepository.DataCallback<List<com.example.taxconnect.model.BookingModel>>() {
            @Override
            public void onSuccess(List<com.example.taxconnect.model.BookingModel> data) {
                List<com.example.taxconnect.model.BookingModel> acceptedBookings = new ArrayList<>();
                int pendingCount = 0;

                if (data != null) {
                    for (com.example.taxconnect.model.BookingModel booking : data) {
                        if ("ACCEPTED".equalsIgnoreCase(booking.getStatus())) {
                            acceptedBookings.add(booking);
                        } else if ("PENDING".equalsIgnoreCase(booking.getStatus())) {
                            pendingCount++;
                        }
                    }
                }

                // Update Pending Bookings Card
                binding.tvPendingBookingsCount.setText(pendingCount + " New Bookings");
                if (pendingCount > 0) {
                    binding.tvPendingBookingsCount.setTextColor(getResources().getColor(android.R.color.holo_red_dark));
                } else {
                    binding.tvPendingBookingsCount.setTextColor(getResources().getColor(R.color.text_muted));
                }

                // Update Upcoming Bookings List
                if (acceptedBookings.isEmpty()) {
                    binding.tvBookingsTitle.setText("Upcoming Bookings");
                    binding.nsvEmptyState.setVisibility(View.VISIBLE);
                    binding.rvBookings.setVisibility(View.GONE);
                } else {
                    bookings = acceptedBookings;
                    binding.tvBookingsTitle.setText("Upcoming Bookings (" + bookings.size() + ")");
                    binding.nsvEmptyState.setVisibility(View.GONE);
                    binding.rvBookings.setVisibility(View.VISIBLE);
                    bookingAdapter.setBookings(bookings);
                }
            }

            @Override
            public void onError(String error) {
                binding.tvPendingBookingsCount.setText("0 New Bookings");
                binding.tvBookingsTitle.setText("Upcoming Bookings");
                binding.tvEmptyState.setText("Error loading bookings");
                binding.nsvEmptyState.setVisibility(View.VISIBLE);
                binding.rvBookings.setVisibility(View.GONE);
                Toast.makeText(CADashboardActivity.this, "Error: " + error, Toast.LENGTH_SHORT).show();
            }
        });
    }
}
