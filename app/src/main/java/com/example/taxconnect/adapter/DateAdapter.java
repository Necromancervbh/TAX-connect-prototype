package com.example.taxconnect.adapter;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.taxconnect.R;
import com.google.android.material.card.MaterialCardView;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;

public class DateAdapter extends RecyclerView.Adapter<DateAdapter.DateViewHolder> {

    private final List<Calendar> dates;
    private int selectedPosition = 0; // Default first day selected
    private final OnDateSelectedListener listener;

    public interface OnDateSelectedListener {
        void onDateSelected(Calendar date);
    }

    public DateAdapter(List<Calendar> dates, OnDateSelectedListener listener) {
        this.dates = dates;
        this.listener = listener;
    }

    @NonNull
    @Override
    public DateViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_date, parent, false);
        return new DateViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull DateViewHolder holder, int position) {
        holder.bind(dates.get(position), position);
    }

    @Override
    public int getItemCount() {
        return dates.size();
    }

    class DateViewHolder extends RecyclerView.ViewHolder {
        TextView tvDay, tvDate;
        MaterialCardView cardDate;

        DateViewHolder(View itemView) {
            super(itemView);
            tvDay = itemView.findViewById(R.id.tvDay);
            tvDate = itemView.findViewById(R.id.tvDate);
            cardDate = itemView.findViewById(R.id.cardDate);
        }

        void bind(Calendar date, int position) {
            SimpleDateFormat dayFormat = new SimpleDateFormat("EEE", Locale.getDefault());
            SimpleDateFormat dateFormat = new SimpleDateFormat("dd", Locale.getDefault());

            tvDay.setText(dayFormat.format(date.getTime()));
            tvDate.setText(dateFormat.format(date.getTime()));

            if (selectedPosition == position) {
                cardDate.setCardBackgroundColor(itemView.getContext().getColor(R.color.primary));
                tvDay.setTextColor(itemView.getContext().getColor(android.R.color.white));
                tvDate.setTextColor(itemView.getContext().getColor(android.R.color.white));
                cardDate.setStrokeWidth(0);
            } else {
                cardDate.setCardBackgroundColor(itemView.getContext().getColor(android.R.color.white));
                tvDay.setTextColor(itemView.getContext().getColor(R.color.slate_500));
                tvDate.setTextColor(itemView.getContext().getColor(R.color.slate_900));
                cardDate.setStrokeColor(itemView.getContext().getColor(R.color.slate_200));
                cardDate.setStrokeWidth(2); // 1dp approx
            }

            itemView.setOnClickListener(v -> {
                int previous = selectedPosition;
                selectedPosition = position;
                notifyItemChanged(previous);
                notifyItemChanged(selectedPosition);
                listener.onDateSelected(date);
            });
        }
    }
}