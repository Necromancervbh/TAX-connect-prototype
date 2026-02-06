package com.example.taxconnect.adapter;

import android.graphics.Color;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.taxconnect.R;
import com.example.taxconnect.model.MilestoneModel;

import java.util.ArrayList;
import java.util.List;

public class MilestoneAdapter extends RecyclerView.Adapter<MilestoneAdapter.MilestoneViewHolder> {

    private List<MilestoneModel> milestones = new ArrayList<>();
    private final OnMilestoneClickListener listener;
    private final boolean isCa; // Only CA can edit

    public interface OnMilestoneClickListener {
        void onMilestoneClick(MilestoneModel milestone);
    }

    public MilestoneAdapter(boolean isCa, OnMilestoneClickListener listener) {
        this.isCa = isCa;
        this.listener = listener;
    }

    public void setMilestones(List<MilestoneModel> milestones) {
        this.milestones = milestones;
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public MilestoneViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_milestone, parent, false);
        return new MilestoneViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull MilestoneViewHolder holder, int position) {
        MilestoneModel milestone = milestones.get(position);
        holder.bind(milestone, isCa, listener);
    }

    @Override
    public int getItemCount() {
        return milestones.size();
    }

    static class MilestoneViewHolder extends RecyclerView.ViewHolder {
        CheckBox cbStatus;
        TextView tvTitle, tvStatus, tvDescription;

        public MilestoneViewHolder(@NonNull View itemView) {
            super(itemView);
            cbStatus = itemView.findViewById(R.id.cbStatus);
            tvTitle = itemView.findViewById(R.id.tvTitle);
            tvStatus = itemView.findViewById(R.id.tvStatus);
            tvDescription = itemView.findViewById(R.id.tvDescription);
        }

        public void bind(MilestoneModel milestone, boolean isCa, OnMilestoneClickListener listener) {
            tvTitle.setText(milestone.getTitle());
            tvDescription.setText(milestone.getDescription());
            
            // Format Status text
            String status = milestone.getStatus();
            if (status == null) status = "PENDING";
            tvStatus.setText(status.replace("_", " "));

            boolean isCompleted = "COMPLETED".equals(status);
            cbStatus.setChecked(isCompleted);

            if (isCompleted) {
                tvStatus.setTextColor(Color.parseColor("#4CAF50")); // Green
                tvStatus.setBackgroundResource(R.drawable.bg_tag_green_light);
            } else if ("IN_PROGRESS".equals(status)) {
                tvStatus.setTextColor(Color.parseColor("#FF9800")); // Orange
                tvStatus.setBackgroundResource(R.drawable.bg_tag_orange_light);
            } else {
                tvStatus.setTextColor(Color.GRAY);
                tvStatus.setBackgroundResource(R.drawable.bg_tag_light);
            }

            itemView.setOnClickListener(v -> {
                if (isCa) {
                    listener.onMilestoneClick(milestone);
                }
            });
        }
    }
}
