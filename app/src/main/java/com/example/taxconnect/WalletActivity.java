package com.example.taxconnect;

import android.os.Bundle;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.biometric.BiometricPrompt;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.example.taxconnect.adapter.TransactionAdapter;
import com.example.taxconnect.databinding.ActivityWalletBinding;
import com.razorpay.PaymentResultListener;
import com.example.taxconnect.model.TransactionModel;
import com.google.firebase.auth.FirebaseAuth;
import com.example.taxconnect.services.PaymentManager;
import java.util.UUID;
import java.util.concurrent.Executor;

import com.google.android.material.chip.Chip;

import java.util.ArrayList;
import java.util.List;

import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;

public class WalletActivity extends AppCompatActivity implements PaymentResultListener {

    private ActivityWalletBinding binding;
    private TransactionAdapter adapter;
    private boolean isDepositMode = true;
    
    private DataRepository repository;
    private String currentUserId;
    private com.example.taxconnect.model.UserModel currentUserModel;
    private double currentBalance = 0.0;
    private PaymentManager paymentManager;
    private boolean isAuthenticated = false;

    // Bottom Sheet references (kept as fields to access in callbacks if needed)
    private BottomSheetDialog bottomSheetDialog;
    private TextInputEditText etAmount, etUpiId;
    private double pendingDepositAmount = 0.0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityWalletBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        
        // Hide content initially
        binding.getRoot().setVisibility(View.INVISIBLE);

        repository = DataRepository.getInstance();
        if (FirebaseAuth.getInstance().getCurrentUser() != null) {
            currentUserId = FirebaseAuth.getInstance().getCurrentUser().getUid();
        }
        paymentManager = new PaymentManager();

        authenticateUser();
    }

    private void authenticateUser() {
        Executor executor = ContextCompat.getMainExecutor(this);
        BiometricPrompt biometricPrompt = new BiometricPrompt(WalletActivity.this,
                executor, new BiometricPrompt.AuthenticationCallback() {
            @Override
            public void onAuthenticationError(int errorCode, @NonNull CharSequence errString) {
                super.onAuthenticationError(errorCode, errString);
                Toast.makeText(getApplicationContext(),
                        "Authentication error: " + errString, Toast.LENGTH_SHORT).show();
                finish(); // Close activity if auth fails
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
                .setTitle("Wallet Security")
                .setSubtitle("Log in using your biometric credential")
                .setNegativeButtonText("Cancel")
                .build();

        biometricPrompt.authenticate(promptInfo);
    }

    private void initializeApp() {
        setupToolbar();
        setupRecyclerView();
        setupActionButtons();
        loadData();
    }

    private void setupToolbar() {
        binding.toolbarWallet.setNavigationOnClickListener(v -> finish());
    }

    private void setupRecyclerView() {
        adapter = new TransactionAdapter();
        binding.rvTransactions.setLayoutManager(new LinearLayoutManager(this));
        binding.rvTransactions.setAdapter(adapter);
    }

    private void setupActionButtons() {
        binding.btnDeposit.setOnClickListener(v -> {
            isDepositMode = true;
            showBottomSheet(true);
        });

        binding.btnWithdraw.setOnClickListener(v -> {
            isDepositMode = false;
            showBottomSheet(false);
        });
    }

    private void showBottomSheet(boolean isDeposit) {
        bottomSheetDialog = new BottomSheetDialog(this);
        View sheetView = getLayoutInflater().inflate(R.layout.layout_wallet_action_bottom_sheet, null);
        bottomSheetDialog.setContentView(sheetView);

        TextView tvTitle = sheetView.findViewById(R.id.tvSheetTitle);
        TextInputLayout tilUpiId = sheetView.findViewById(R.id.tilUpiId);
        etAmount = sheetView.findViewById(R.id.etAmount);
        etUpiId = sheetView.findViewById(R.id.etUpiId);
        com.google.android.material.chip.ChipGroup chipGroup = sheetView.findViewById(R.id.chipGroupQuickAmounts);
        com.google.android.material.button.MaterialButton btnProceed = sheetView.findViewById(R.id.btnProceed);

        if (isDeposit) {
            tvTitle.setText("Add Money to Wallet");
            tilUpiId.setVisibility(View.GONE);
            btnProceed.setText("Add Money");
        } else {
            tvTitle.setText("Withdraw to Bank");
            tilUpiId.setVisibility(View.VISIBLE);
            btnProceed.setText("Withdraw Money");
        }

        chipGroup.setOnCheckedStateChangeListener((group, checkedIds) -> {
            if (!checkedIds.isEmpty()) {
                com.google.android.material.chip.Chip chip = group.findViewById(checkedIds.get(0));
                String text = chip.getText().toString();
                String amount = text.replaceAll("[^0-9.]", "").trim();
                etAmount.setText(amount);
                etAmount.setSelection(amount.length());
            }
        });

        btnProceed.setOnClickListener(v -> {
            String amountStr = etAmount.getText().toString();
            if (amountStr.isEmpty()) {
                etAmount.setError("Enter amount");
                return;
            }

            double amount = Double.parseDouble(amountStr);
            if (amount <= 0) {
                 etAmount.setError("Invalid amount");
                 return;
            }

            if (isDeposit) {
                // Deposit Logic
                pendingDepositAmount = amount;
                bottomSheetDialog.dismiss();
                paymentManager.startPayment(this, amountStr, "user@example.com", "9999999999", "Wallet Deposit");
            } else {
                // Withdraw Logic
                String upiId = etUpiId.getText().toString();
                if (upiId.isEmpty()) {
                    etUpiId.setError("Enter UPI ID");
                    return;
                }
                
                if (amount > currentBalance) {
                    Toast.makeText(this, "Insufficient Balance", Toast.LENGTH_SHORT).show();
                    return;
                }

                bottomSheetDialog.dismiss();
                performWithdrawal(amount, upiId);
            }
        });

        bottomSheetDialog.show();
    }

    private void performWithdrawal(double amount, String upiId) {
        // 1. Create Transaction
        TransactionModel transaction = new TransactionModel(
                UUID.randomUUID().toString(),
                currentUserId,
                "BANK",
                "Bank Transfer",
                "Withdrawal to " + upiId,
                -amount, // Negative for withdrawal
                "SUCCESS" // Assuming instant success for simulation
        );

        repository.createTransaction(transaction, new DataRepository.DataCallback<Void>() {
            @Override
            public void onSuccess(Void data) {
                // 2. Update Balance
                repository.updateWalletBalance(currentUserId, -amount, new DataRepository.DataCallback<Double>() {
                    @Override
                    public void onSuccess(Double newBalance) {
                        currentBalance = newBalance;
                        binding.tvWalletBalance.setText(String.format("₹ %.2f", newBalance));
                        Toast.makeText(WalletActivity.this, "Withdrawal Successful", Toast.LENGTH_SHORT).show();
                        loadData(); // Refresh list
                    }

                    @Override
                    public void onError(String error) {
                        Toast.makeText(WalletActivity.this, "Balance update failed", Toast.LENGTH_SHORT).show();
                    }
                });
            }

            @Override
            public void onError(String error) {
                Toast.makeText(WalletActivity.this, "Transaction failed", Toast.LENGTH_SHORT).show();
            }
        });
    }

    @Override
    public void onPaymentSuccess(String s) {
        if (pendingDepositAmount <= 0) return;

        TransactionModel transaction = new TransactionModel(
                UUID.randomUUID().toString(),
                currentUserId,
                "WALLET",
                "Wallet Top-up",
                "Deposit via Razorpay",
                pendingDepositAmount,
                "SUCCESS"
        );

        repository.createTransaction(transaction, new DataRepository.DataCallback<Void>() {
            @Override
            public void onSuccess(Void data) {
                repository.updateWalletBalance(currentUserId, pendingDepositAmount, new DataRepository.DataCallback<Double>() {
                    @Override
                    public void onSuccess(Double newBalance) {
                        currentBalance = newBalance;
                        binding.tvWalletBalance.setText(String.format("₹ %.2f", newBalance));
                        Toast.makeText(WalletActivity.this, "Deposit Successful", Toast.LENGTH_SHORT).show();
                        loadData();
                        pendingDepositAmount = 0.0; // Reset
                    }

                    @Override
                    public void onError(String error) {
                        Toast.makeText(WalletActivity.this, "Balance update failed: " + error, Toast.LENGTH_SHORT).show();
                    }
                });
            }

            @Override
            public void onError(String error) {
                Toast.makeText(WalletActivity.this, "Transaction failed: " + error, Toast.LENGTH_SHORT).show();
            }
        });
    }


    @Override
    public void onPaymentError(int i, String s) {
        Toast.makeText(this, "Payment Failed: " + s, Toast.LENGTH_SHORT).show();
    }

    private void loadData() {
        if (currentUserId == null) return;
        
        // Fetch User Details
        repository.fetchUser(currentUserId, new DataRepository.DataCallback<com.example.taxconnect.model.UserModel>() {
            @Override
            public void onSuccess(com.example.taxconnect.model.UserModel user) {
                currentUserModel = user;
            }

            @Override
            public void onError(String error) {
                // Log error
            }
        });

        repository.getWalletBalance(currentUserId, new DataRepository.DataCallback<Double>() {
            @Override
            public void onSuccess(Double balance) {
                currentBalance = balance;
                binding.tvWalletBalance.setText(String.format("₹ %.2f", balance));
            }

            @Override
            public void onError(String error) {
                // Handle error or use cached data
            }
        });
        
        repository.getTransactions(currentUserId, new DataRepository.DataCallback<List<TransactionModel>>() {
            @Override
            public void onSuccess(List<TransactionModel> transactions) {
                adapter.setTransactions(transactions);
                if (transactions.isEmpty()) {
                    binding.tvEmptyState.setVisibility(View.VISIBLE);
                    binding.rvTransactions.setVisibility(View.GONE);
                } else {
                    binding.tvEmptyState.setVisibility(View.GONE);
                    binding.rvTransactions.setVisibility(View.VISIBLE);
                }
            }
            
            @Override
            public void onError(String error) {
                // Handle error
                binding.tvEmptyState.setVisibility(View.VISIBLE);
                binding.tvEmptyState.setText("Failed to load transactions");
            }
        });
    }
}
