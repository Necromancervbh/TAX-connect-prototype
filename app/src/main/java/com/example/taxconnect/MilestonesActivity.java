package com.example.taxconnect;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.taxconnect.adapter.MilestoneAdapter;
import com.example.taxconnect.model.MilestoneModel;
import com.example.taxconnect.model.UserModel;
import com.google.android.material.floatingactionbutton.ExtendedFloatingActionButton;
import com.google.android.material.textfield.TextInputEditText;
import com.google.firebase.Timestamp;
import com.google.firebase.auth.FirebaseAuth;

import java.util.List;
import java.util.UUID;

public class MilestonesActivity extends AppCompatActivity {

    private RecyclerView rvMilestones;
    private MilestoneAdapter adapter;
    private ExtendedFloatingActionButton fabAddMilestone;
    private DataRepository repository;
    private String bookingId;
    private boolean isCa = false;

    private android.widget.TextView tvProgressPercent;
    private android.widget.ProgressBar progressBar;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_milestones);

        bookingId = getIntent().getStringExtra("bookingId");
        if (bookingId == null) {
            finish();
            return;
        }

        repository = DataRepository.getInstance();
        rvMilestones = findViewById(R.id.rvMilestones);
        fabAddMilestone = findViewById(R.id.fabAddMilestone);
        tvProgressPercent = findViewById(R.id.tvProgressPercent);
        progressBar = findViewById(R.id.progressBar);

        findViewById(R.id.toolbar).setOnClickListener(v -> finish());

        checkUserRole();
    }

    private void checkUserRole() {
        String uid = FirebaseAuth.getInstance().getUid();
        if (uid == null) return;

        repository.fetchUser(uid, new DataRepository.DataCallback<UserModel>() {
            @Override
            public void onSuccess(UserModel user) {
                if (user != null) {
                    isCa = "CA".equals(user.getRole());
                    setupUI();
                }
            }

            @Override
            public void onError(String error) {
                // Handle error
            }
        });
    }

    private void setupUI() {
        if (isCa) {
            fabAddMilestone.setVisibility(View.VISIBLE);
            fabAddMilestone.setOnClickListener(v -> showAddMilestoneDialog());
        } else {
            fabAddMilestone.setVisibility(View.GONE);
        }

        adapter = new MilestoneAdapter(isCa, milestone -> {
            if (isCa) {
                toggleMilestoneStatus(milestone);
            }
        });
        rvMilestones.setLayoutManager(new LinearLayoutManager(this));
        rvMilestones.setAdapter(adapter);

        loadMilestones();
    }

    private void loadMilestones() {
        repository.getMilestones(bookingId, new DataRepository.DataCallback<List<MilestoneModel>>() {
            @Override
            public void onSuccess(List<MilestoneModel> data) {
                adapter.setMilestones(data);
                updateProgress(data);
            }

            @Override
            public void onError(String error) {
                Toast.makeText(MilestonesActivity.this, "Failed to load milestones", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void updateProgress(List<MilestoneModel> data) {
        if (data == null || data.isEmpty()) {
            progressBar.setProgress(0);
            tvProgressPercent.setText("0%");
            return;
        }
        
        int completed = 0;
        for (MilestoneModel m : data) {
            if ("COMPLETED".equals(m.getStatus())) {
                completed++;
            }
        }
        
        int progress = (int) ((completed / (float) data.size()) * 100);
        progressBar.setProgress(progress);
        tvProgressPercent.setText(progress + "%");
    }

    private void toggleMilestoneStatus(MilestoneModel milestone) {
        String currentStatus = milestone.getStatus();
        String newStatus;
        if ("PENDING".equals(currentStatus)) {
            newStatus = "IN_PROGRESS";
        } else if ("IN_PROGRESS".equals(currentStatus)) {
            newStatus = "COMPLETED";
        } else {
            newStatus = "PENDING"; // Cycle back
        }

        repository.updateMilestoneStatus(bookingId, milestone.getId(), newStatus, new DataRepository.DataCallback<Void>() {
            @Override
            public void onSuccess(Void data) {
                milestone.setStatus(newStatus);
                adapter.notifyDataSetChanged();
                // We need to reload or manually recalculate progress. 
                // Since adapter has the reference, we can re-iterate.
                // But simplest is to just call loadMilestones() again or calculate from adapter list.
                // Better: iterate adapter list.
                // However, we can just fetch list again for safety or assume local list is updated.
                // Let's assume local list is updated since we updated the object reference.
                // But we don't have easy access to the full list unless we expose it or keep a local copy in Activity.
                // Actually, loadMilestones passed 'data' to adapter. 'milestone' is a reference to an object in that list.
                // So if we have a reference to the list, we can recalc.
                // Let's just reload for consistency or keep a local list.
                loadMilestones(); 
            }

            @Override
            public void onError(String error) {
                Toast.makeText(MilestonesActivity.this, "Failed to update status", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void showAddMilestoneDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        View view = LayoutInflater.from(this).inflate(R.layout.dialog_add_milestone, null);
        builder.setView(view);
        AlertDialog dialog = builder.create();

        TextInputEditText etTitle = view.findViewById(R.id.etTitle);
        TextInputEditText etDescription = view.findViewById(R.id.etDescription);
        Button btnAdd = view.findViewById(R.id.btnAdd);
        Button btnCancel = view.findViewById(R.id.btnCancel);

        btnAdd.setOnClickListener(v -> {
            String title = etTitle.getText().toString().trim();
            String desc = etDescription.getText().toString().trim();

            if (title.isEmpty()) return;

            addMilestone(title, desc);
            dialog.dismiss();
        });

        btnCancel.setOnClickListener(v -> dialog.dismiss());
        dialog.show();
    }

    private void addMilestone(String title, String description) {
        MilestoneModel milestone = new MilestoneModel();
        milestone.setId(UUID.randomUUID().toString());
        milestone.setBookingId(bookingId);
        milestone.setTitle(title);
        milestone.setDescription(description);
        milestone.setStatus("PENDING");
        milestone.setTimestamp(Timestamp.now());

        repository.addMilestone(bookingId, milestone, new DataRepository.DataCallback<Void>() {
            @Override
            public void onSuccess(Void data) {
                Toast.makeText(MilestonesActivity.this, "Milestone Added", Toast.LENGTH_SHORT).show();
                loadMilestones();
            }

            @Override
            public void onError(String error) {
                Toast.makeText(MilestonesActivity.this, "Failed to add milestone", Toast.LENGTH_SHORT).show();
            }
        });
    }
}
