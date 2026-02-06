package com.example.taxconnect;

import android.os.Bundle;
import android.view.View;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.taxconnect.adapter.TransactionAdapter;
import com.example.taxconnect.model.TransactionModel;
import com.example.taxconnect.model.UserModel;
import com.google.firebase.auth.FirebaseAuth;

import java.util.List;

public class OrderHistoryActivity extends AppCompatActivity {

    private RecyclerView recyclerView;
    private TransactionAdapter adapter;
    private ProgressBar progressBar;
    private TextView tvEmpty;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_order_history);

        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setDisplayShowHomeEnabled(true);
        }
        toolbar.setNavigationOnClickListener(v -> onBackPressed());

        recyclerView = findViewById(R.id.recyclerView);
        progressBar = findViewById(R.id.progressBar);
        tvEmpty = findViewById(R.id.tvEmpty);

        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        adapter = new TransactionAdapter();
        recyclerView.setAdapter(adapter);

        loadTransactions();
    }

    private void loadTransactions() {
        String uid = FirebaseAuth.getInstance().getUid();
        if (uid == null) return;

        progressBar.setVisibility(View.VISIBLE);

        // First we need to know if the user is a CA or Customer to call the right method
        DataRepository.getInstance().fetchUser(uid, new DataRepository.DataCallback<UserModel>() {
            @Override
            public void onSuccess(UserModel user) {
                if (user == null) {
                    progressBar.setVisibility(View.GONE);
                    return;
                }

                if ("CA".equals(user.getRole())) {
                    fetchCaTransactions(uid);
                } else {
                    fetchCustomerTransactions(uid);
                }
            }

            @Override
            public void onError(String error) {
                progressBar.setVisibility(View.GONE);
                Toast.makeText(OrderHistoryActivity.this, "Error loading profile: " + error, Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void fetchCustomerTransactions(String uid) {
        DataRepository.getInstance().getUserTransactions(uid, new DataRepository.DataCallback<List<TransactionModel>>() {
            @Override
            public void onSuccess(List<TransactionModel> data) {
                progressBar.setVisibility(View.GONE);
                if (data == null || data.isEmpty()) {
                    tvEmpty.setVisibility(View.VISIBLE);
                } else {
                    tvEmpty.setVisibility(View.GONE);
                    adapter.setTransactions(data);
                }
            }

            @Override
            public void onError(String error) {
                progressBar.setVisibility(View.GONE);
                Toast.makeText(OrderHistoryActivity.this, "Error: " + error, Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void fetchCaTransactions(String uid) {
        DataRepository.getInstance().getCaTransactions(uid, new DataRepository.DataCallback<List<TransactionModel>>() {
            @Override
            public void onSuccess(List<TransactionModel> data) {
                progressBar.setVisibility(View.GONE);
                if (data == null || data.isEmpty()) {
                    tvEmpty.setVisibility(View.VISIBLE);
                } else {
                    tvEmpty.setVisibility(View.GONE);
                    adapter.setTransactions(data);
                }
            }

            @Override
            public void onError(String error) {
                progressBar.setVisibility(View.GONE);
                Toast.makeText(OrderHistoryActivity.this, "Error: " + error, Toast.LENGTH_SHORT).show();
            }
        });
    }
}
