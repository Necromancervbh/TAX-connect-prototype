package com.example.taxconnect.adapter;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.taxconnect.R;
import com.example.taxconnect.databinding.ItemBookingBinding;
import com.example.taxconnect.model.BookingModel;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class BookingAdapter extends RecyclerView.Adapter<BookingAdapter.BookingViewHolder> {

    private List<BookingModel> bookings = new ArrayList<>();
    private final OnBookingActionListener listener;
    private final boolean isCaView;

    public interface OnBookingActionListener {
        void onAccept(BookingModel booking);
        void onReject(BookingModel booking);
        void onBookingClick(BookingModel booking);
        void onMilestonesClick(BookingModel booking);
    }

    public BookingAdapter(OnBookingActionListener listener, boolean isCaView) {
        this.listener = listener;
        this.isCaView = isCaView;
    }

    public void setBookings(List<BookingModel> bookings) {
        this.bookings = bookings;
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public BookingViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        ItemBookingBinding binding = ItemBookingBinding.inflate(
                LayoutInflater.from(parent.getContext()), parent, false);
        return new BookingViewHolder(binding);
    }

    @Override
    public void onBindViewHolder(@NonNull BookingViewHolder holder, int position) {
        holder.bind(bookings.get(position));
    }

    @Override
    public int getItemCount() {
        return bookings.size();
    }

    class BookingViewHolder extends RecyclerView.ViewHolder {
        private final ItemBookingBinding binding;

        public BookingViewHolder(@NonNull ItemBookingBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }

        public void bind(BookingModel booking) {
            if (isCaView) {
                binding.tvName.setText(booking.getUserName() != null ? booking.getUserName() : "User");
                binding.tvRoleLabel.setText("Client");
                binding.ivAvatar.setImageResource(R.drawable.ic_person);
            } else {
                binding.tvName.setText(booking.getCaName() != null ? booking.getCaName() : "CA");
                binding.tvRoleLabel.setText("Chartered Accountant");
                binding.ivAvatar.setImageResource(R.drawable.ic_work); // Differentiate CA
            }
            
            SimpleDateFormat sdf = new SimpleDateFormat("EEE, dd MMM yyyy • hh:mm a", Locale.getDefault());
            binding.tvDateTime.setText(sdf.format(new Date(booking.getAppointmentTimestamp())));
            
            if (booking.getMessage() != null && !booking.getMessage().trim().isEmpty()) {
                binding.tvMessage.setText(booking.getMessage());
                binding.tvMessage.setVisibility(View.VISIBLE);
            } else {
                binding.tvMessage.setVisibility(View.GONE);
            }

            long currentTime = System.currentTimeMillis();
            boolean isExpired = booking.getAppointmentTimestamp() < currentTime;
            String status = booking.getStatus();

            // Check for expiration first
            if (isExpired && (status.equals("PENDING") || status.equals("ACCEPTED") || status.equals("CONFIRMED"))) {
                binding.tvStatus.setText("EXPIRED");
                binding.tvStatus.setBackgroundResource(R.drawable.bg_status_rejected); // Use rejected style for expired
                binding.layoutActions.setVisibility(View.GONE);
            } else {
                binding.tvStatus.setText(status);
                switch (status) {
                    case "PENDING":
                        binding.tvStatus.setBackgroundResource(R.drawable.bg_status_pending);
                        if (isCaView) {
                            binding.layoutActions.setVisibility(View.VISIBLE);
                        } else {
                            binding.layoutActions.setVisibility(View.GONE);
                        }
                        break;
                    case "ACCEPTED":
                        // Display "ACTIVE" for future accepted bookings
                        binding.tvStatus.setText("ACTIVE");
                        binding.tvStatus.setBackgroundResource(R.drawable.bg_status_accepted);
                        binding.layoutActions.setVisibility(View.GONE);
                        break;
                    case "REJECTED":
                        binding.tvStatus.setBackgroundResource(R.drawable.bg_status_rejected);
                        binding.layoutActions.setVisibility(View.GONE);
                        break;
                    default:
                        binding.layoutActions.setVisibility(View.GONE);
                        binding.tvStatus.setBackgroundResource(R.drawable.bg_status_accepted); // Default fallback
                        break;
                }
            }

            binding.btnAccept.setOnClickListener(v -> {
                if (listener != null) listener.onAccept(booking);
            });

            binding.btnReject.setOnClickListener(v -> {
                if (listener != null) listener.onReject(booking);
            });

            if ("ACCEPTED".equals(status) || "COMPLETED".equals(status) || "CONFIRMED".equals(status)) {
                binding.btnMilestones.setVisibility(View.VISIBLE);
                binding.btnMilestones.setOnClickListener(v -> {
                    if (listener != null) listener.onMilestonesClick(booking);
                });
            } else {
                binding.btnMilestones.setVisibility(View.GONE);
            }

            binding.getRoot().setOnClickListener(v -> {
                if (listener != null) listener.onBookingClick(booking);
            });
        }
    }
}
