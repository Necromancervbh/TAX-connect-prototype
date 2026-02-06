package com.example.taxconnect;

import android.os.Bundle;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.example.taxconnect.adapter.RequestsAdapter;
import com.example.taxconnect.databinding.ActivityRequestsBinding;
import com.example.taxconnect.model.BookingModel;
import com.example.taxconnect.model.ConversationModel;
import com.example.taxconnect.model.RequestItem;
import com.google.firebase.auth.FirebaseAuth;

import java.util.ArrayList;
import java.util.List;

public class RequestsActivity extends AppCompatActivity implements RequestsAdapter.OnRequestActionListener {

    private ActivityRequestsBinding binding;
    private DataRepository repository;
    private RequestsAdapter adapter;
    private String currentUserId;
    private List<RequestItem> allRequests = new ArrayList<>();
    private List<ConversationModel> conversationRequests = new ArrayList<>();
    private List<BookingModel> bookingRequests = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityRequestsBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        repository = DataRepository.getInstance();
        currentUserId = FirebaseAuth.getInstance().getUid();

        binding.toolbar.setNavigationOnClickListener(v -> finish());
        binding.toolbar.setTitle("Requests");

        setupRecyclerView();
        fetchRequests();
    }

    private void setupRecyclerView() {
        adapter = new RequestsAdapter(this);
        binding.rvRequests.setLayoutManager(new LinearLayoutManager(this));
        binding.rvRequests.setAdapter(adapter);
    }

    private void fetchRequests() {
        // Fetch Messaging Requests
        repository.getRequests(currentUserId, new DataRepository.DataCallback<List<ConversationModel>>() {
            @Override
            public void onSuccess(List<ConversationModel> data) {
                conversationRequests = data;
                updateMergedList();
            }

            @Override
            public void onError(String error) {
                Toast.makeText(RequestsActivity.this, "Error fetching requests: " + error, Toast.LENGTH_SHORT).show();
            }
        });
        
        // Fetch Booking Requests
        repository.getBookingsForCA(currentUserId, new DataRepository.DataCallback<List<BookingModel>>() {
            @Override
            public void onSuccess(List<BookingModel> data) {
                bookingRequests.clear();
                for (BookingModel model : data) {
                    if ("PENDING".equals(model.getStatus())) {
                        bookingRequests.add(model);
                    }
                }
                updateMergedList();
            }

            @Override
            public void onError(String error) {
                // Ignore error or log it, as user might not be a CA or have no bookings
            }
        });
    }

    private void updateMergedList() {
        allRequests.clear();
        for (ConversationModel model : conversationRequests) {
            allRequests.add(new RequestItem(RequestItem.TYPE_CONVERSATION, model));
        }
        for (BookingModel model : bookingRequests) {
            allRequests.add(new RequestItem(RequestItem.TYPE_BOOKING, model));
        }
        updateAdapter();
    }

    private void updateAdapter() {
        if (allRequests.isEmpty()) {
            // Optional: Show empty view
        }
        adapter.setItems(allRequests);
    }

    @Override
    public void onAccept(RequestItem item) {
        if (item.getType() == RequestItem.TYPE_CONVERSATION) {
            ConversationModel request = (ConversationModel) item.getData();
            repository.acceptRequest(request, new DataRepository.DataCallback<Void>() {
                @Override
                public void onSuccess(Void data) {
                    Toast.makeText(RequestsActivity.this, "Request Accepted", Toast.LENGTH_SHORT).show();
                    allRequests.remove(item);
                    updateAdapter();
                }

                @Override
                public void onError(String error) {
                    Toast.makeText(RequestsActivity.this, "Failed to accept: " + error, Toast.LENGTH_SHORT).show();
                }
            });
        } else if (item.getType() == RequestItem.TYPE_BOOKING) {
            BookingModel booking = (BookingModel) item.getData();
            repository.updateBookingStatus(booking.getId(), "ACCEPTED", new DataRepository.DataCallback<Void>() {
                @Override
                public void onSuccess(Void data) {
                    Toast.makeText(RequestsActivity.this, "Booking Accepted", Toast.LENGTH_SHORT).show();
                    repository.incrementClientCount(booking.getCaId(), booking.getUserId()); // Increment client count
                    allRequests.remove(item);
                    updateAdapter();
                }
                @Override
                public void onError(String error) {
                    Toast.makeText(RequestsActivity.this, "Failed to accept booking: " + error, Toast.LENGTH_SHORT).show();
                }
            });
        }
    }

    @Override
    public void onReject(RequestItem item) {
        if (item.getType() == RequestItem.TYPE_CONVERSATION) {
            ConversationModel request = (ConversationModel) item.getData();
            repository.updateConversationState(request.getConversationId(), ConversationModel.STATE_REFUSED, new DataRepository.DataCallback<Void>() {
                @Override
                public void onSuccess(Void data) {
                    Toast.makeText(RequestsActivity.this, "Request Refused", Toast.LENGTH_SHORT).show();
                    allRequests.remove(item);
                    updateAdapter();
                }

                @Override
                public void onError(String error) {
                    Toast.makeText(RequestsActivity.this, "Failed to refuse: " + error, Toast.LENGTH_SHORT).show();
                }
            });
        } else if (item.getType() == RequestItem.TYPE_BOOKING) {
            BookingModel booking = (BookingModel) item.getData();
            repository.updateBookingStatus(booking.getId(), "REJECTED", new DataRepository.DataCallback<Void>() {
                @Override
                public void onSuccess(Void data) {
                    Toast.makeText(RequestsActivity.this, "Booking Rejected", Toast.LENGTH_SHORT).show();
                    allRequests.remove(item);
                    updateAdapter();
                }
                @Override
                public void onError(String error) {
                    Toast.makeText(RequestsActivity.this, "Failed to reject booking: " + error, Toast.LENGTH_SHORT).show();
                }
            });
        }
    }
}