package com.example.taxconnect.adapter;

import android.view.LayoutInflater;
import android.view.ViewGroup;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Button;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.example.taxconnect.R;
import com.example.taxconnect.model.UserModel;
import android.content.Intent;
import com.example.taxconnect.CADetailActivity;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.HashSet;

public class CAAdapter extends RecyclerView.Adapter<CAAdapter.CAViewHolder> {

    private List<UserModel> caList = new ArrayList<>();
    private final OnConnectClickListener listener;
    private final android.content.Context context;
    private final boolean compactMode;
    private OnSaveClickListener saveListener;
    private Set<String> favoriteIds = new HashSet<>();

    public interface OnConnectClickListener {
        void onConnectClick(UserModel ca);
    }
    public interface OnSaveClickListener {
        void onSaveClick(UserModel ca);
    }

    public CAAdapter(List<UserModel> list, android.content.Context context) {
        this(list, context, (context instanceof OnConnectClickListener) ? (OnConnectClickListener) context : null, false);
    }

    public CAAdapter(List<UserModel> list, android.content.Context context, boolean compactMode) {
        this(list, context, (context instanceof OnConnectClickListener) ? (OnConnectClickListener) context : null, compactMode);
    }

    public CAAdapter(List<UserModel> list, android.content.Context context, OnConnectClickListener listener) {
        this(list, context, listener, false);
    }

    public CAAdapter(List<UserModel> list, android.content.Context context, OnConnectClickListener listener, boolean compactMode) {
        this.caList = list;
        this.context = context;
        this.listener = listener;
        this.compactMode = compactMode;
    }

    public void updateList(List<UserModel> list) {
        this.caList = list;
        notifyDataSetChanged();
    }

    public List<UserModel> getCurrentList() {
        return this.caList;
    }
    
    public void setOnSaveClickListener(OnSaveClickListener l) {
        this.saveListener = l;
    }
    
    public void setFavorites(Set<String> ids) {
        this.favoriteIds = ids != null ? new HashSet<>(ids) : new HashSet<>();
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public CAViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        int layoutId = compactMode ? R.layout.item_ca_compact : R.layout.item_ca;
        View view = LayoutInflater.from(parent.getContext()).inflate(layoutId, parent, false);
        return new CAViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull CAViewHolder holder, int position) {
        UserModel ca = caList.get(position);
        holder.bind(ca);
    }

    @Override
    public int getItemCount() {
        return caList.size();
    }

    class CAViewHolder extends RecyclerView.ViewHolder {
        ImageView ivProfileImage;
        ImageView ivVerifiedBadge;
        TextView tvCAName;
        TextView tvSpecialization;
        TextView tvExperience;
        TextView tvStatus;
        TextView tvCity;
        TextView tvRating;
        TextView tvMinCharges;
        Button btnConnect;
        Button btnSave;

        public CAViewHolder(View itemView) {
            super(itemView);
            ivProfileImage = itemView.findViewById(R.id.ivProfileImage);
            ivVerifiedBadge = itemView.findViewById(R.id.ivVerifiedBadge);
            tvCAName = itemView.findViewById(R.id.tvCAName);
            tvSpecialization = itemView.findViewById(R.id.tvSpecialization);
            tvExperience = itemView.findViewById(R.id.tvExperience);
            tvStatus = itemView.findViewById(R.id.tvStatus);
            tvCity = itemView.findViewById(R.id.tvCity);
            tvRating = itemView.findViewById(R.id.tvRating);
            tvMinCharges = itemView.findViewById(R.id.tvMinCharges);
            btnConnect = itemView.findViewById(R.id.btnConnect);
            btnSave = itemView.findViewById(R.id.btnSave);
        }

        public void bind(UserModel ca) {
            if (tvCAName != null) tvCAName.setText(ca.getName());
            if (ivVerifiedBadge != null) ivVerifiedBadge.setVisibility(ca.isVerified() ? View.VISIBLE : View.GONE);
            if (tvSpecialization != null) tvSpecialization.setText("Specialization: " + ca.getSpecialization());
            if (tvExperience != null) tvExperience.setText("Experience: " + ca.getExperience() + " years");
            if (tvStatus != null) {
                tvStatus.setText(ca.isOnline() ? "● Online" : "● Offline");
                int color = ca.isOnline() ? context.getColor(R.color.emerald_600) : context.getColor(R.color.slate_500);
                tvStatus.setTextColor(color);
            }
            if (tvCity != null) tvCity.setText(ca.getCity() != null ? ca.getCity() : "City: N/A");
            if (tvRating != null) tvRating.setText(String.format(java.util.Locale.getDefault(), "★ %.1f (%d)", ca.getRating(), ca.getRatingCount()));
            if (tvMinCharges != null) tvMinCharges.setText(ca.getMinCharges() != null ? "Starts at ₹ " + ca.getMinCharges() : "Charges: N/A");

            if (ivProfileImage != null) {
                if (ca.getProfileImageUrl() != null && !ca.getProfileImageUrl().isEmpty()) {
                    Glide.with(context)
                            .load(ca.getProfileImageUrl())
                            .placeholder(android.R.drawable.ic_menu_gallery)
                            .error(android.R.drawable.ic_menu_report_image)
                            .centerCrop()
                            .into(ivProfileImage);
                } else {
                    ivProfileImage.setImageResource(android.R.drawable.ic_menu_gallery);
                }
            }

            if (btnConnect != null) {
                btnConnect.setOnClickListener(v -> {
                    if (listener != null) {
                        listener.onConnectClick(ca);
                    }
                });
            }
            
            if (btnSave != null) {
                boolean isFav = favoriteIds.contains(ca.getUid());
                btnSave.setText(isFav ? "Saved" : "Save");
                btnSave.setOnClickListener(v -> {
                    if (saveListener != null) {
                        saveListener.onSaveClick(ca);
                    }
                });
            }

            itemView.setOnClickListener(v -> {
                Intent intent = new Intent(context, CADetailActivity.class);
                intent.putExtra("CA_DATA", ca);
                context.startActivity(intent);
            });
        }
    }
}
