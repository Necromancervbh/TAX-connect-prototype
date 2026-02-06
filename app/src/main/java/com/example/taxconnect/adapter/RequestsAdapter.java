package com.example.taxconnect.adapter;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.example.taxconnect.R;
import com.example.taxconnect.model.BookingModel;
import com.example.taxconnect.model.ConversationModel;
import com.example.taxconnect.model.RequestItem;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class RequestsAdapter extends RecyclerView.Adapter<RequestsAdapter.RequestViewHolder> {

    private List<RequestItem> items = new ArrayList<>();
    private final OnRequestActionListener listener;

    public interface OnRequestActionListener {
        void onAccept(RequestItem item);
        void onReject(RequestItem item);
    }

    public RequestsAdapter(OnRequestActionListener listener) {
        this.listener = listener;
    }

    public void setItems(List<RequestItem> items) {
        this.items = items;
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public RequestViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_request, parent, false);
        return new RequestViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull RequestViewHolder holder, int position) {
        RequestItem item = items.get(position);
        holder.bind(item);
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    class RequestViewHolder extends RecyclerView.ViewHolder {
        ImageView ivProfile;
        TextView tvName, tvQuery;
        ImageButton btnAccept, btnReject;

        RequestViewHolder(View itemView) {
            super(itemView);
            ivProfile = itemView.findViewById(R.id.ivProfile);
            tvName = itemView.findViewById(R.id.tvName);
            tvQuery = itemView.findViewById(R.id.tvQuery);
            btnAccept = itemView.findViewById(R.id.btnAccept);
            btnReject = itemView.findViewById(R.id.btnReject);
        }

        void bind(RequestItem item) {
            if (item.getType() == RequestItem.TYPE_CONVERSATION) {
                ConversationModel request = (ConversationModel) item.getData();
                tvName.setText(request.getOtherUserName() != null ? request.getOtherUserName() : "Unknown User");
                tvQuery.setText(request.getLastMessage() != null ? request.getLastMessage() : "No query");

                Glide.with(itemView.getContext())
                        .load(request.getOtherUserProfileImage())
                        .placeholder(R.drawable.ic_person)
                        .error(R.drawable.ic_person)
                        .circleCrop()
                        .into(ivProfile);
            } else if (item.getType() == RequestItem.TYPE_BOOKING) {
                BookingModel booking = (BookingModel) item.getData();
                tvName.setText(booking.getUserName() != null ? booking.getUserName() : "Unknown User");
                
                SimpleDateFormat sdf = new SimpleDateFormat("EEE, dd MMM • hh:mm a", Locale.getDefault());
                String dateStr = sdf.format(new Date(booking.getAppointmentTimestamp()));
                tvQuery.setText("Booking Request: " + dateStr);

                // Use default icon for now as we don't have user image in BookingModel
                ivProfile.setImageResource(R.drawable.ic_person);
                // Optionally clear Glide if view is recycled
                Glide.with(itemView.getContext()).clear(ivProfile);
            }

            btnAccept.setOnClickListener(v -> {
                if (listener != null) listener.onAccept(item);
            });

            btnReject.setOnClickListener(v -> {
                if (listener != null) listener.onReject(item);
            });
        }
    }
}