package com.example.taxconnect.adapter;

import android.graphics.Color;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.taxconnect.R;
import com.example.taxconnect.model.TransactionModel;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class TransactionAdapter extends RecyclerView.Adapter<TransactionAdapter.TransactionViewHolder> {

    private List<TransactionModel> transactions = new ArrayList<>();

    public void setTransactions(List<TransactionModel> transactions) {
        this.transactions = transactions;
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public TransactionViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_transaction, parent, false);
        return new TransactionViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull TransactionViewHolder holder, int position) {
        TransactionModel transaction = transactions.get(position);
        holder.bind(transaction);
    }

    @Override
    public int getItemCount() {
        return transactions.size();
    }

    static class TransactionViewHolder extends RecyclerView.ViewHolder {
        TextView tvDescription, tvDate, tvAmount, tvStatus;

        public TransactionViewHolder(@NonNull View itemView) {
            super(itemView);
            tvDescription = itemView.findViewById(R.id.tvDescription);
            tvDate = itemView.findViewById(R.id.tvDate);
            tvAmount = itemView.findViewById(R.id.tvAmount);
            tvStatus = itemView.findViewById(R.id.tvStatus);
        }

        public void bind(TransactionModel transaction) {
            tvDescription.setText(transaction.getDescription());
            tvAmount.setText(String.format(Locale.getDefault(), "₹ %.2f", transaction.getAmount()));
            tvStatus.setText(transaction.getStatus());

            if (transaction.getTimestamp() != null) {
                SimpleDateFormat sdf = new SimpleDateFormat("MMM dd, yyyy HH:mm", Locale.getDefault());
                tvDate.setText(sdf.format(transaction.getTimestamp().toDate()));
            } else {
                tvDate.setText("");
            }

            if ("SUCCESS".equalsIgnoreCase(transaction.getStatus())) {
                tvStatus.setBackgroundResource(R.drawable.bg_status_success);
            } else {
                tvStatus.setBackgroundColor(Color.GRAY);
            }
        }
    }
}
