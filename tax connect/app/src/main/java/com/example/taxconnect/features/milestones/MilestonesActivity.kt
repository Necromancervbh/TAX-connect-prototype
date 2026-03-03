package com.example.taxconnect.features.milestones

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.widget.Button
import androidx.appcompat.app.AlertDialog
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.taxconnect.R
import com.example.taxconnect.data.repositories.DataRepository
import com.example.taxconnect.data.repositories.DataRepository.DataCallback
import com.example.taxconnect.features.milestones.MilestoneAdapter
import com.example.taxconnect.databinding.ActivityMilestonesBinding
import com.example.taxconnect.data.models.MilestoneModel
import com.example.taxconnect.data.models.UserModel
import com.example.taxconnect.core.base.BaseActivity
import com.google.android.material.textfield.TextInputEditText
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import java.util.UUID

class MilestonesActivity : BaseActivity<ActivityMilestonesBinding>() {
    override val bindingInflater: (LayoutInflater) -> ActivityMilestonesBinding = ActivityMilestonesBinding::inflate
    
    private lateinit var adapter: MilestoneAdapter
    private lateinit var repository: DataRepository
    private var bookingId: String? = null
    private var isCa = false

    override fun initViews() {
        bookingId = intent.getStringExtra("bookingId")
        if (bookingId == null) {
            finish()
            return
        }

        repository = DataRepository.getInstance()

        checkUserRole()
    }

    override fun setupListeners() {
        binding.toolbar.setOnClickListener { finish() }
    }

    override fun observeViewModel() {
        // No ViewModel to observe yet
    }

    private fun checkUserRole() {
        val uid = FirebaseAuth.getInstance().uid ?: return

        repository.fetchUser(uid, object : DataCallback<UserModel> {
            override fun onSuccess(data: UserModel?) {
                if (data != null) {
                    isCa = "CA" == data.role
                    setupUI()
                }
            }

            override fun onError(error: String?) {
                // Handle error
            }
        })
    }

    private fun setupUI() {
        if (isCa) {
            binding.fabAddMilestone.visibility = View.VISIBLE
            binding.fabAddMilestone.setOnClickListener { showAddMilestoneDialog() }
        } else {
            binding.fabAddMilestone.visibility = View.GONE
        }

        adapter = MilestoneAdapter(isCa) { milestone ->
            if (isCa) {
                toggleMilestoneStatus(milestone)
            }
        }
        binding.rvMilestones.layoutManager = LinearLayoutManager(this)
        binding.rvMilestones.adapter = adapter

        loadMilestones()
    }

    private fun loadMilestones() {
        val bid = bookingId ?: return
        repository.getMilestones(bid, object : DataCallback<List<MilestoneModel>> {
            override fun onSuccess(data: List<MilestoneModel>?) {
                if (data != null) {
                    adapter.setMilestones(data)
                    updateProgress(data)
                }
            }

            override fun onError(error: String?) {
                showToast(getString(R.string.failed_to_load_milestones))
            }
        })
    }

    private fun updateProgress(data: List<MilestoneModel>?) {
        if (data.isNullOrEmpty()) {
            binding.progressBar.progress = 0
            binding.tvProgressPercent.text = "0%"
            return
        }

        var completed = 0
        for (m in data) {
            if ("COMPLETED" == m.status) {
                completed++
            }
        }

        val progress = (completed / data.size.toFloat() * 100).toInt()
        binding.progressBar.progress = progress
        binding.tvProgressPercent.text = "$progress%"
    }

    private fun toggleMilestoneStatus(milestone: MilestoneModel) {
        val bid = bookingId ?: return
        val mid = milestone.id ?: return
        val currentStatus = milestone.status
        val newStatus = when (currentStatus) {
            "PENDING" -> "IN_PROGRESS"
            "IN_PROGRESS" -> "COMPLETED"
            else -> "PENDING"
        }

        repository.updateMilestoneStatus(
            bid,
            mid,
            newStatus,
            object : DataCallback<Void?> {
                override fun onSuccess(data: Void?) {
                    milestone.status = newStatus
                    adapter.notifyDataSetChanged()
                    loadMilestones()
                }

                override fun onError(error: String?) {
                    showToast(getString(R.string.failed_to_update_status))
                }
            })
    }

    private fun showAddMilestoneDialog() {
        val builder = AlertDialog.Builder(this)
        val view = LayoutInflater.from(this).inflate(R.layout.dialog_add_milestone, null)
        builder.setView(view)
        val dialog = builder.create()

        val etTitle = view.findViewById<TextInputEditText>(R.id.etTitle)
        val etDescription = view.findViewById<TextInputEditText>(R.id.etDescription)
        val btnAdd = view.findViewById<Button>(R.id.btnAdd)
        val btnCancel = view.findViewById<Button>(R.id.btnCancel)

        btnAdd.setOnClickListener {
            val title = etTitle.text.toString().trim()
            val desc = etDescription.text.toString().trim()

            if (title.isEmpty()) {
                etTitle.error = getString(R.string.milestone_title_required)
                etTitle.requestFocus()
                return@setOnClickListener
            }

            addMilestone(title, desc)
            dialog.dismiss()
        }

        btnCancel.setOnClickListener { dialog.dismiss() }
        dialog.show()
    }

    private fun addMilestone(title: String, description: String) {
        val bid = bookingId ?: return
        val milestone = MilestoneModel()
        milestone.id = UUID.randomUUID().toString()
        milestone.bookingId = bid
        milestone.title = title
        milestone.description = description
        milestone.status = "PENDING"
        milestone.timestamp = Timestamp.now()

        repository.addMilestone(bid, milestone, object : DataCallback<Void?> {
            override fun onSuccess(data: Void?) {
                showToast("Milestone Added")
                loadMilestones()
            }

            override fun onError(error: String?) {
                showToast("Failed to add milestone")
            }
        })
    }
}
