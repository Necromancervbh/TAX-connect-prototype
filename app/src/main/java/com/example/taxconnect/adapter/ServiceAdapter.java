package com.example.taxconnect.adapter;

import android.view.LayoutInflater;
import android.view.ViewGroup;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import com.example.taxconnect.databinding.ItemServiceBinding;
import com.example.taxconnect.model.ServiceModel;
import java.util.ArrayList;
import java.util.List;

public class ServiceAdapter extends RecyclerView.Adapter<ServiceAdapter.ServiceViewHolder> {

    private List<ServiceModel> services = new ArrayList<>();
    private final OnServiceClickListener listener;
    private OnServiceDeleteListener deleteListener;
    private boolean isEditable = false;

    public interface OnServiceClickListener {
        void onServiceClick(ServiceModel service);
    }

    public interface OnServiceDeleteListener {
        void onServiceDelete(ServiceModel service);
    }

    public ServiceAdapter(OnServiceClickListener listener) {
        this.listener = listener;
    }

    public void setOnServiceDeleteListener(OnServiceDeleteListener listener) {
        this.deleteListener = listener;
    }

    public void setEditable(boolean editable) {
        this.isEditable = editable;
        notifyDataSetChanged();
    }

    public void setServices(List<ServiceModel> services) {
        this.services = services;
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public ServiceViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        ItemServiceBinding binding = ItemServiceBinding.inflate(LayoutInflater.from(parent.getContext()), parent, false);
        return new ServiceViewHolder(binding);
    }

    @Override
    public void onBindViewHolder(@NonNull ServiceViewHolder holder, int position) {
        holder.bind(services.get(position));
    }

    @Override
    public int getItemCount() {
        return services.size();
    }

    class ServiceViewHolder extends RecyclerView.ViewHolder {
        private final ItemServiceBinding binding;

        public ServiceViewHolder(ItemServiceBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }

        public void bind(ServiceModel service) {
            binding.tvTitle.setText(service.getTitle());
            binding.tvDesc.setText(service.getDescription());
            binding.tvPrice.setText("\u20B9 " + service.getPrice());
            binding.tvTime.setText("Time: " + service.getEstimatedTime());
            
            if (isEditable) {
                binding.btnBuy.setText("Edit");
                binding.btnDelete.setVisibility(android.view.View.VISIBLE);
            } else {
                binding.btnBuy.setText("Buy Now");
                binding.btnDelete.setVisibility(android.view.View.GONE);
            }

            binding.btnBuy.setOnClickListener(v -> {
                if (listener != null) listener.onServiceClick(service);
            });

            binding.btnDelete.setOnClickListener(v -> {
                if (deleteListener != null) deleteListener.onServiceDelete(service);
            });
        }
    }
}
