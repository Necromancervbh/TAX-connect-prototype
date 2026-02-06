package com.example.taxconnect.adapter;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.taxconnect.R;
import com.example.taxconnect.model.RatingModel;

import java.util.ArrayList;
import java.util.List;

public class RatingAdapter extends RecyclerView.Adapter<RatingAdapter.RatingViewHolder> {

    private List<RatingModel> ratings = new ArrayList<>();

    public void setRatings(List<RatingModel> ratings) {
        this.ratings = ratings;
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public RatingViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_rating, parent, false);
        return new RatingViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull RatingViewHolder holder, int position) {
        RatingModel rating = ratings.get(position);
        holder.bind(rating);
    }

    @Override
    public int getItemCount() {
        return ratings.size();
    }

    static class RatingViewHolder extends RecyclerView.ViewHolder {
        TextView tvUserName, tvRatingValue, tvReview;

        public RatingViewHolder(@NonNull View itemView) {
            super(itemView);
            tvUserName = itemView.findViewById(R.id.tvUserName);
            tvRatingValue = itemView.findViewById(R.id.tvRatingValue);
            tvReview = itemView.findViewById(R.id.tvReview);
        }

        public void bind(RatingModel rating) {
            tvUserName.setText(rating.getUserName() != null ? rating.getUserName() : "Anonymous");
            tvRatingValue.setText(String.valueOf(rating.getRating()));
            tvReview.setText(rating.getReview() != null ? rating.getReview() : "");
        }
    }
}
