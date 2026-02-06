package com.example.taxconnect;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.example.taxconnect.adapter.BookingAdapter;
import com.example.taxconnect.databinding.ActivityMyBookingsBinding;
import com.example.taxconnect.model.BookingModel;
import com.google.firebase.auth.FirebaseAuth;

import java.util.List;
import java.util.ArrayList;
import com.google.android.material.tabs.TabLayout;

public class MyBookingsActivity extends AppCompatActivity {

    private ActivityMyBookingsBinding binding;
    private BookingAdapter adapter;
    private DataRepository repository;
    private List<BookingModel> allBookings = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityMyBookingsBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        repository = DataRepository.getInstance();

        binding.toolbar.setNavigationIcon(R.drawable.ic_arrow_back);
        binding.toolbar.setNavigationOnClickListener(v -> finish());

        setupTabs();
        setupRecyclerView();
        loadBookings();
    }

    private void setupTabs() {
        binding.tabLayout.addTab(binding.tabLayout.newTab().setText("Pending"));
        binding.tabLayout.addTab(binding.tabLayout.newTab().setText("Accepted"));
        binding.tabLayout.addTab(binding.tabLayout.newTab().setText("Refused"));

        binding.tabLayout.addOnTabSelectedListener(new TabLayout.OnTabSelectedListener() {
            @Override
            public void onTabSelected(TabLayout.Tab tab) {
                filterBookings();
            }

            @Override
            public void onTabUnselected(TabLayout.Tab tab) {}

            @Override
            public void onTabReselected(TabLayout.Tab tab) {}
        });
    }

    private void filterBookings() {
        int position = binding.tabLayout.getSelectedTabPosition();
        List<BookingModel> filteredList = new ArrayList<>();
        String targetStatus = "PENDING";
        
        if (position == 1) targetStatus = "ACCEPTED";
        else if (position == 2) targetStatus = "REJECTED";

        for (BookingModel booking : allBookings) {
            if (targetStatus.equals(booking.getStatus())) {
                filteredList.add(booking);
            } else if (position == 1 && ("CONFIRMED".equals(booking.getStatus()) || "COMPLETED".equals(booking.getStatus()))) {
                // Group confirmed/completed with accepted
                filteredList.add(booking);
            } else if (position == 2 && "CANCELLED".equals(booking.getStatus())) {
                // Group cancelled with refused
                filteredList.add(booking);
            }
        }
        
        adapter.setBookings(filteredList);
        
        if (filteredList.isEmpty()) {
            binding.rvBookings.setVisibility(View.GONE);
            binding.tvNoBookings.setVisibility(View.VISIBLE);
            binding.tvNoBookings.setText("No " + targetStatus.toLowerCase() + " bookings");
        } else {
            binding.rvBookings.setVisibility(View.VISIBLE);
            binding.tvNoBookings.setVisibility(View.GONE);
        }
    }

    private void setupRecyclerView() {
        // Initial setup, will be updated after user role fetch
        adapter = new BookingAdapter(null, false); 
        binding.rvBookings.setLayoutManager(new LinearLayoutManager(this));
        binding.rvBookings.setAdapter(adapter);
    }

    private void loadBookings() {
        String uid = FirebaseAuth.getInstance().getUid();
        if (uid == null) {
            finish();
            return;
        }

        binding.progressBar.setVisibility(View.VISIBLE);

        repository.fetchUser(uid, new DataRepository.DataCallback<com.example.taxconnect.model.UserModel>() {
            @Override
            public void onSuccess(com.example.taxconnect.model.UserModel user) {
                if (user != null) {
                    boolean isCa = "CA".equals(user.getRole());
                    updateAdapter(isCa);
                    
                    if (isCa) {
                        repository.getBookingsForCA(uid, bookingCallback);
                    } else {
                        repository.getBookingsForUser(uid, bookingCallback);
                    }
                } else {
                    binding.progressBar.setVisibility(View.GONE);
                }
            }
            
            @Override
            public void onError(String error) {
                binding.progressBar.setVisibility(View.GONE);
                Toast.makeText(MyBookingsActivity.this, "Failed to fetch user", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void updateAdapter(boolean isCa) {
        BookingAdapter.OnBookingActionListener listener = new BookingAdapter.OnBookingActionListener() {
            @Override
            public void onAccept(BookingModel booking) {
                if (isCa) {
                    updateBookingStatus(booking, "ACCEPTED");
                }
            }

            @Override
            public void onReject(BookingModel booking) {
                if (isCa) {
                    updateBookingStatus(booking, "REJECTED");
                }
            }

            @Override
            public void onBookingClick(BookingModel booking) {
                String receiverId = isCa ? booking.getUserId() : booking.getCaId();
                String receiverName = isCa ? booking.getUserName() : booking.getCaName();

                if (receiverId != null) {
                    Intent intent = new Intent(MyBookingsActivity.this, ChatActivity.class);
                    intent.putExtra("RECEIVER_ID", receiverId);
                    intent.putExtra("RECEIVER_NAME", receiverName);
                    startActivity(intent);
                } else {
                    Toast.makeText(MyBookingsActivity.this, "User information missing", Toast.LENGTH_SHORT).show();
                }
            }

            @Override
            public void onMilestonesClick(BookingModel booking) {
                Intent intent = new Intent(MyBookingsActivity.this, MilestonesActivity.class);
                intent.putExtra("bookingId", booking.getId());
                startActivity(intent);
            }
        };
        
        adapter = new BookingAdapter(listener, isCa);
        binding.rvBookings.setAdapter(adapter);
    }
    
    private void updateBookingStatus(BookingModel booking, String status) {
        repository.updateBookingStatus(booking.getId(), status, new DataRepository.DataCallback<Void>() {
            @Override
            public void onSuccess(Void data) {
                if ("ACCEPTED".equals(status)) {
                    repository.incrementClientCount(booking.getCaId(), booking.getUserId());
                }
                Toast.makeText(MyBookingsActivity.this, "Booking " + status, Toast.LENGTH_SHORT).show();
                // Update local list
                for (BookingModel b : allBookings) {
                    if (b.getId().equals(booking.getId())) {
                        b.setStatus(status);
                        break;
                    }
                }
                filterBookings();
            }
            @Override
            public void onError(String error) {
                Toast.makeText(MyBookingsActivity.this, "Failed to update: " + error, Toast.LENGTH_SHORT).show();
            }
        });
    }

    private final DataRepository.DataCallback<List<BookingModel>> bookingCallback = new DataRepository.DataCallback<List<BookingModel>>() {
        @Override
        public void onSuccess(List<BookingModel> bookings) {
            binding.progressBar.setVisibility(View.GONE);
            if (bookings != null) {
                allBookings = bookings;
                filterBookings();
            } else {
                allBookings.clear();
                filterBookings();
            }
        }

        @Override
        public void onError(String error) {
            binding.progressBar.setVisibility(View.GONE);
            Toast.makeText(MyBookingsActivity.this, "Failed to load bookings: " + error, Toast.LENGTH_SHORT).show();
        }
    };
}