package com.example.taxconnect.features.ca

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.taxconnect.R
import com.example.taxconnect.data.models.RatingModel
import java.util.*

class RatingAdapter : RecyclerView.Adapter<RatingAdapter.RatingViewHolder>() {

    private var ratings: List<RatingModel> = ArrayList()

    fun setRatings(ratings: List<RatingModel>) {
        this.ratings = ratings
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RatingViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_rating, parent, false)
        return RatingViewHolder(view)
    }

    override fun onBindViewHolder(holder: RatingViewHolder, position: Int) {
        holder.bind(ratings[position])
    }

    override fun getItemCount(): Int = ratings.size

    class RatingViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val tvUserName: TextView = itemView.findViewById(R.id.tvUserName)
        private val tvRatingValue: TextView = itemView.findViewById(R.id.tvRatingValue)
        private val tvReview: TextView = itemView.findViewById(R.id.tvReview)

        fun bind(rating: RatingModel) {
            tvUserName.text = rating.userName ?: "Anonymous"
            tvRatingValue.text = String.format(Locale.getDefault(), "%.1f", rating.rating)
            tvReview.text = rating.review ?: ""
            tvReview.visibility = if (rating.review.isNullOrEmpty()) View.GONE else View.VISIBLE
        }
    }
}
