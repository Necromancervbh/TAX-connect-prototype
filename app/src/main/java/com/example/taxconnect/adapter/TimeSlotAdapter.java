package com.example.taxconnect.adapter;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.taxconnect.R;

import java.util.List;

public class TimeSlotAdapter extends RecyclerView.Adapter<TimeSlotAdapter.TimeSlotViewHolder> {

    private final List<String> timeSlots;
    private int selectedPosition = -1;
    private final OnTimeSelectedListener listener;

    public interface OnTimeSelectedListener {
        void onTimeSelected(String timeSlot);
    }

    public TimeSlotAdapter(List<String> timeSlots, OnTimeSelectedListener listener) {
        this.timeSlots = timeSlots;
        this.listener = listener;
    }

    @NonNull
    @Override
    public TimeSlotViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_time_slot, parent, false);
        return new TimeSlotViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull TimeSlotViewHolder holder, int position) {
        holder.bind(timeSlots.get(position), position);
    }

    @Override
    public int getItemCount() {
        return timeSlots.size();
    }

    class TimeSlotViewHolder extends RecyclerView.ViewHolder {
        TextView tvTimeSlot;

        TimeSlotViewHolder(View itemView) {
            super(itemView);
            tvTimeSlot = itemView.findViewById(R.id.tvTimeSlot);
        }

        void bind(String timeSlot, int position) {
            tvTimeSlot.setText(timeSlot);

            if (selectedPosition == position) {
                tvTimeSlot.setBackgroundTintList(itemView.getContext().getColorStateList(R.color.primary));
                tvTimeSlot.setTextColor(itemView.getContext().getColor(android.R.color.white));
            } else {
                tvTimeSlot.setBackgroundTintList(itemView.getContext().getColorStateList(R.color.slate_100));
                tvTimeSlot.setTextColor(itemView.getContext().getColor(R.color.slate_700));
            }

            itemView.setOnClickListener(v -> {
                int previous = selectedPosition;
                selectedPosition = position;
                notifyItemChanged(previous);
                notifyItemChanged(selectedPosition);
                listener.onTimeSelected(timeSlot);
            });
        }
    }
}