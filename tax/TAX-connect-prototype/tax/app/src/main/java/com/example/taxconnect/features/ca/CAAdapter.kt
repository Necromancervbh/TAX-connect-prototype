package com.example.taxconnect.features.ca

import android.content.Context
import android.content.Intent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import com.google.android.material.chip.Chip
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.example.taxconnect.features.ca.CADetailActivity
import com.example.taxconnect.R
import com.example.taxconnect.data.models.UserModel
import java.util.Locale
import androidx.lifecycle.findViewTreeLifecycleOwner
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import com.example.taxconnect.data.repositories.UserRepository

class CAAdapter(
    private var caList: List<UserModel>,
    private val context: Context,
    private val listener: OnRequestAssistanceClickListener? = null,
    private val compactMode: Boolean = false
) : RecyclerView.Adapter<CAAdapter.CAViewHolder>() {

    private var saveListener: OnSaveClickListener? = null
    private var favoriteIds: Set<String> = HashSet()

    interface OnRequestAssistanceClickListener {
        fun onRequestAssistanceClick(ca: UserModel)
    }

    interface OnSaveClickListener {
        fun onSaveClick(ca: UserModel)
    }

    constructor(list: List<UserModel>, context: Context) : this(
        list,
        context,
        if (context is OnRequestAssistanceClickListener) context else null,
        false
    )

    constructor(list: List<UserModel>, context: Context, compactMode: Boolean) : this(
        list,
        context,
        if (context is OnRequestAssistanceClickListener) context else null,
        compactMode
    )

    fun updateList(list: List<UserModel>) {
        this.caList = list
        notifyDataSetChanged()
    }

    fun getCurrentList(): List<UserModel> {
        return this.caList
    }

    fun setOnSaveClickListener(l: OnSaveClickListener) {
        this.saveListener = l
    }

    fun setFavorites(ids: Set<String>?) {
        this.favoriteIds = ids?.toHashSet() ?: HashSet()
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): CAViewHolder {
        try {
            val layoutId = if (compactMode) R.layout.item_ca_compact else R.layout.item_ca
            val view = LayoutInflater.from(parent.context).inflate(layoutId, parent, false)
            return CAViewHolder(view)
        } catch (e: Exception) {
            android.util.Log.e("CAAdapter", "Error inflating view", e)
            // Return a safe dummy view to prevent crash
            val dummyView = View(parent.context)
            dummyView.layoutParams = ViewGroup.LayoutParams(0, 0)
            dummyView.visibility = View.GONE
            return CAViewHolder(dummyView)
        }
    }

    override fun onBindViewHolder(holder: CAViewHolder, position: Int) {
        try {
            val ca = caList[position]
            holder.bind(ca)
        } catch (e: Exception) {
             android.util.Log.e("CAAdapter", "Error binding view", e)
        }
    }

    override fun onViewRecycled(holder: CAViewHolder) {
        super.onViewRecycled(holder)
        holder.cancelObservation()
    }

    override fun getItemCount(): Int {
        return caList.size
    }

    inner class CAViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val ivProfileImage: ImageView? = itemView.findViewById(R.id.ivProfileImage)
        private val ivVerifiedBadge: ImageView? = itemView.findViewById(R.id.ivVerifiedBadge)
        private val tvCAName: TextView? = itemView.findViewById(R.id.tvCAName)
        private val tvSpecialization: TextView? = itemView.findViewById(R.id.tvSpecialization)
        private val tvExperience: TextView? = itemView.findViewById(R.id.tvExperience)
        private val tvStatus: TextView? = itemView.findViewById(R.id.tvStatus)
        private val tvCity: TextView? = itemView.findViewById(R.id.tvCity)
        private val tvRating: TextView? = itemView.findViewById(R.id.tvRating)
        private val tvMinCharges: TextView? = itemView.findViewById(R.id.tvMinCharges)
        private val tvVerifiedTag: TextView? = itemView.findViewById(R.id.tvVerifiedTag)
        private val tvClientCount: TextView? = itemView.findViewById(R.id.tvClientCount)
        private val chipStatus: Chip? = itemView.findViewById(R.id.chipStatus)
        private val btnConnect: Button? = itemView.findViewById(R.id.btnConnect)
        private val btnSave: Button? = itemView.findViewById(R.id.btnSave)
        private var observerJob: Job? = null

        fun cancelObservation() {
            observerJob?.cancel()
        }

        fun bind(ca: UserModel) {
            tvCAName?.text = ca.name
            ivVerifiedBadge?.visibility = if (ca.isVerified) View.VISIBLE else View.GONE
            tvSpecialization?.text = ca.specialization
            tvExperience?.text = "${ca.experience} years"
            
            val uid = ca.uid
            observerJob?.cancel()
            if (uid != null) {
                observerJob = itemView.findViewTreeLifecycleOwner()?.lifecycleScope?.launch {
                    UserRepository.getInstance().observeUser(uid).collect { user ->
                        updateOnlineStatusUI(user.isOnline)
                    }
                }
            } else {
                updateOnlineStatusUI(ca.isOnline)
            }
            
            tvCity?.text = ca.city ?: "N/A"
            tvRating?.text = String.format(Locale.getDefault(), "★ %.1f (%d)", ca.rating, ca.ratingCount)
            
            tvMinCharges?.let {
                val minCharges = ca.minCharges
                if (!minCharges.isNullOrEmpty() && minCharges.trim().isNotEmpty()) {
                    it.text = "Starts at ₹ $minCharges"
                } else {
                    it.text = itemView.context.getString(R.string.pricing_on_request)
                }
            }
            
            tvVerifiedTag?.visibility = if (ca.isVerified) View.VISIBLE else View.GONE
            
            tvClientCount?.let {
                val count = ca.clientCount
                if (count > 0) {
                    it.text = "$count clients"
                    it.visibility = View.VISIBLE
                } else {
                    it.visibility = View.GONE
                }
            }
            


            ivProfileImage?.let {
                if (!ca.profileImageUrl.isNullOrEmpty()) {
                    Glide.with(context)
                        .load(ca.profileImageUrl)
                        .placeholder(android.R.drawable.ic_menu_gallery)
                        .error(android.R.drawable.ic_menu_report_image)
                        .centerCrop()
                        .into(it)
                } else {
                    it.setImageResource(android.R.drawable.ic_menu_gallery)
                }
            }

            btnConnect?.setOnClickListener {
                listener?.onRequestAssistanceClick(ca)
            }
            
            btnSave?.let {
                val isFav = favoriteIds.contains(ca.uid)
                it.text = if (isFav) "Saved" else "Save"
                it.setOnClickListener {
                    saveListener?.onSaveClick(ca)
                }
            }

            itemView.setOnClickListener {
                val intent = Intent(context, CADetailActivity::class.java)
                intent.putExtra("CA_DATA", ca)
                context.startActivity(intent)
            }
        }
        
        private fun updateOnlineStatusUI(isOnline: Boolean) {
            tvStatus?.let {
                it.text = if (isOnline) "\u25CF Online" else "\u25CF Offline"
                val color = if (isOnline) context.getColor(R.color.emerald_600) else context.getColor(R.color.slate_500)
                it.setTextColor(color)
            }

            chipStatus?.let {
                it.text = if (isOnline) "Online" else "Offline"
                val color = if (isOnline) context.getColor(R.color.emerald_600) else context.getColor(R.color.slate_500)
                it.setTextColor(color)
                it.setChipIconTintResource(if (isOnline) R.color.emerald_600 else R.color.slate_500)
                it.setChipIconResource(if (isOnline) R.drawable.ic_check_material3 else R.drawable.ic_close)
            }
        }
    }
}
