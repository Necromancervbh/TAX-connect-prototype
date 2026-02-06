package com.example.taxconnect;

import android.os.Bundle;
import android.view.View;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.biometric.BiometricPrompt;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.example.taxconnect.adapter.TransactionAdapter;
import com.example.taxconnect.databinding.ActivityBalanceSheetBinding;
import com.example.taxconnect.model.TransactionModel;
import com.google.firebase.auth.FirebaseAuth;

import java.util.List;
import java.util.Locale;
import java.util.concurrent.Executor;

public class BalanceSheetActivity extends AppCompatActivity {

    private ActivityBalanceSheetBinding binding;
    private DataRepository repository;
    private String currentUserId;
    private TransactionAdapter adapter;
    private boolean isAuthenticated = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityBalanceSheetBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        // Hide content initially
        binding.getRoot().setVisibility(View.INVISIBLE);

        repository = DataRepository.getInstance();
        currentUserId = FirebaseAuth.getInstance().getUid();

        authenticateUser();
    }

    private void authenticateUser() {
        Executor executor = ContextCompat.getMainExecutor(this);
        BiometricPrompt biometricPrompt = new BiometricPrompt(BalanceSheetActivity.this,
                executor, new BiometricPrompt.AuthenticationCallback() {
            @Override
            public void onAuthenticationError(int errorCode, @NonNull CharSequence errString) {
                super.onAuthenticationError(errorCode, errString);
                Toast.makeText(getApplicationContext(),
                        "Authentication error: " + errString, Toast.LENGTH_SHORT).show();
                finish();
            }

            @Override
            public void onAuthenticationSucceeded(@NonNull BiometricPrompt.AuthenticationResult result) {
                super.onAuthenticationSucceeded(result);
                isAuthenticated = true;
                binding.getRoot().setVisibility(View.VISIBLE);
                initializeApp();
            }

            @Override
            public void onAuthenticationFailed() {
                super.onAuthenticationFailed();
                Toast.makeText(getApplicationContext(), "Authentication failed",
                        Toast.LENGTH_SHORT).show();
            }
        });

        BiometricPrompt.PromptInfo promptInfo = new BiometricPrompt.PromptInfo.Builder()
                .setTitle("Secure Access")
                .setSubtitle("Confirm your identity to view financial data")
                .setNegativeButtonText("Cancel")
                .build();

        biometricPrompt.authenticate(promptInfo);
    }

    private void initializeApp() {
        binding.toolbar.setNavigationOnClickListener(v -> finish());
        setupRecyclerView();
        loadData();
    }

    private void setupRecyclerView() {
        adapter = new TransactionAdapter();
        binding.rvTransactions.setLayoutManager(new LinearLayoutManager(this));
        binding.rvTransactions.setAdapter(adapter);
    }

    private void loadData() {
        // Load Revenue Stats
        repository.getRevenueStats(currentUserId, new DataRepository.DataCallback<Double>() {
            @Override
            public void onSuccess(Double totalRevenue) {
                binding.tvTotalEarnings.setText(String.format(Locale.getDefault(), "₹ %.2f", totalRevenue));
            }

            @Override
            public void onError(String error) {
                Toast.makeText(BalanceSheetActivity.this, "Error loading revenue", Toast.LENGTH_SHORT).show();
            }
        });

        // Load Transactions
        repository.getCaTransactions(currentUserId, new DataRepository.DataCallback<List<TransactionModel>>() {
            @Override
            public void onSuccess(List<TransactionModel> transactions) {
                adapter.setTransactions(transactions);
                
                // Calculate unique customers (simple approximation based on transaction userIds)
                // In a real app, you'd query unique user IDs or maintain a counter
                long uniqueCustomers = transactions.stream()
                        .map(TransactionModel::getUserId)
                        .distinct()
                        .count();
                binding.tvTotalCustomers.setText(String.valueOf(uniqueCustomers));
                
                // Count pending requests (if any transaction is pending? or from proposals?)
                // The requirement says "what is pending from whom". 
                // This might refer to pending proposals in conversations, but let's check transactions for now.
                // Or maybe we should query conversations with PENDING payment state? 
                // For now, let's just count transactions with status "PENDING" if any.
                long pendingCount = transactions.stream()
                        .filter(t -> "PENDING".equalsIgnoreCase(t.getStatus()))
                        .count();
                binding.tvPendingCount.setText(pendingCount + " Requests");
            }

            @Override
            public void onError(String error) {
                Toast.makeText(BalanceSheetActivity.this, "Error loading transactions", Toast.LENGTH_SHORT).show();
            }
        });
    }
}
