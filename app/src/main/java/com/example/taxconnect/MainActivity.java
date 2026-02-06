package com.example.taxconnect;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.example.taxconnect.databinding.ActivityMainBinding;
import com.example.taxconnect.model.UserModel;
import com.google.firebase.auth.FirebaseAuth;

public class MainActivity extends AppCompatActivity {

    private ActivityMainBinding binding;
    private DataRepository repository;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        // Check if user is already logged in
        if (FirebaseAuth.getInstance().getCurrentUser() != null) {
            checkUserRoleAndRedirect();
            return;
        }

        initializeUI();
    }

    private void initializeUI() {
        // Initialize ViewBinding
        binding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        // Initialize Repository
        repository = DataRepository.getInstance();

        setupListeners();
    }

    private void checkUserRoleAndRedirect() {
        String uid = FirebaseAuth.getInstance().getCurrentUser().getUid();
        DataRepository.getInstance().fetchUser(uid, new DataRepository.DataCallback<UserModel>() {
            @Override
            public void onSuccess(UserModel user) {
                if ("CA".equalsIgnoreCase(user.getRole())) {
                    startActivity(new Intent(MainActivity.this, CADashboardActivity.class));
                } else {
                    startActivity(new Intent(MainActivity.this, HomeActivity.class));
                }
                finish();
            }

            @Override
            public void onError(String error) {
                // If error (e.g. user deleted from DB but still in Auth), sign out and show UI
                Toast.makeText(MainActivity.this, "Session invalid: " + error, Toast.LENGTH_SHORT).show();
                FirebaseAuth.getInstance().signOut();
                initializeUI();
            }
        });
    }

    private void setupListeners() {
        binding.btnCustomer.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startRegisterActivity("CUSTOMER");
            }
        });

        binding.btnCA.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startRegisterActivity("CA");
            }
        });
        
        binding.tvLoginLink.setOnClickListener(v -> {
            startActivity(new Intent(MainActivity.this, LoginActivity.class));
        });
    }

    private void startRegisterActivity(String role) {
        Intent intent = new Intent(this, RegisterActivity.class);
        intent.putExtra("ROLE", role);
        startActivity(intent);
    }
}
