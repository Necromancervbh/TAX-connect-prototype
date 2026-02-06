package com.example.taxconnect;

import android.content.Intent;
import android.os.Bundle;
import android.view.MenuItem;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;
import android.Manifest;
import android.content.pm.PackageManager;
import android.os.Build;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.view.GravityCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.core.content.ContextCompat;

import com.bumptech.glide.Glide;
import com.example.taxconnect.adapter.CAAdapter;
import com.example.taxconnect.databinding.ActivityHomeBinding;
import com.example.taxconnect.model.UserModel;
import com.google.android.material.navigation.NavigationView;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.messaging.FirebaseMessaging;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class HomeActivity extends AppCompatActivity implements CAAdapter.OnConnectClickListener {

    private ActivityHomeBinding binding;
    private DataRepository repository;
    private CAAdapter adapter;
    private CAAdapter featuredAdapter;
    private List<UserModel> fullCAList = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityHomeBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        repository = DataRepository.getInstance();
        setupRecyclerView();
        setupDrawer();
        binding.progressTopRated.setVisibility(View.VISIBLE);
        fetchCAList();
        
        binding.ivProfile.setOnClickListener(v -> {
            startActivity(new Intent(HomeActivity.this, ProfileActivity.class));
        });

        binding.ivMyChats.setOnClickListener(v -> {
            startActivity(new Intent(HomeActivity.this, MyChatsActivity.class));
        });
        
        // Listener for View All
        binding.btnViewAll.setOnClickListener(v -> {
            startActivity(new Intent(HomeActivity.this, ExploreCAsActivity.class));
        });
        
        binding.etSearch.setOnEditorActionListener((textView, actionId, event) -> {
            if (actionId == android.view.inputmethod.EditorInfo.IME_ACTION_SEARCH) {
                String query = textView.getText().toString();
                Intent intent = new Intent(HomeActivity.this, ExploreCAsActivity.class);
                intent.putExtra("QUERY", query);
                startActivity(intent);
                return true;
            }
            return false;
        });
        
        binding.chipExplore.setOnClickListener(v -> {
            startActivity(new Intent(HomeActivity.this, ExploreCAsActivity.class));
        });
        
        binding.chipBookings.setOnClickListener(v -> {
            startActivity(new Intent(HomeActivity.this, MyBookingsActivity.class));
        });
        
        binding.chipWallet.setOnClickListener(v -> {
            startActivity(new Intent(HomeActivity.this, WalletActivity.class));
        });
        
        binding.chipSaved.setOnClickListener(v -> {
            Intent intent = new Intent(HomeActivity.this, ExploreCAsActivity.class);
            intent.putExtra("FAVORITES_ONLY", true);
            startActivity(intent);
        });
        
        askNotificationPermission();
        updateFcmToken();
    }

    private void updateFcmToken() {
        FirebaseMessaging.getInstance().getToken()
            .addOnCompleteListener(task -> {
                if (!task.isSuccessful()) {
                    return;
                }
                String token = task.getResult();
                if (token != null) {
                    repository.updateFcmToken(token);
                }
            });
    }

    private void askNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) ==
                    PackageManager.PERMISSION_GRANTED) {
                // Granted
            } else {
                requestPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS);
            }
        }
    }

    private final androidx.activity.result.ActivityResultLauncher<String> requestPermissionLauncher =
            registerForActivityResult(new androidx.activity.result.contract.ActivityResultContracts.RequestPermission(), isGranted -> {
                if (isGranted) {
                    // FCM SDK (and your app) can post notifications.
                } else {
                    // TODO: Inform user that that your app will not show notifications.
                }
            });

    private void fetchCAList() {
        repository.getCAList(new DataRepository.DataCallback<List<UserModel>>() {
            @Override
            public void onSuccess(List<UserModel> caList) {
                binding.progressTopRated.setVisibility(View.GONE);
                fullCAList.clear();
                fullCAList.addAll(caList);
                
                // Process lists using utility
                com.example.taxconnect.utils.ListUtils.SplitResult result = 
                    com.example.taxconnect.utils.ListUtils.processCALists(fullCAList);
                
                List<UserModel> topList = result.topList;
                List<UserModel> featuredList = result.featuredList;
                
                adapter.updateList(topList);
                featuredAdapter.updateList(featuredList);
                
                // Hide Featured section if empty
                if (featuredList.isEmpty()) {
                    binding.layoutFeatured.setVisibility(View.GONE);
                    binding.rvFeaturedCAs.setVisibility(View.GONE);
                } else {
                    binding.layoutFeatured.setVisibility(View.VISIBLE);
                    binding.rvFeaturedCAs.setVisibility(View.VISIBLE);
                }
                
                if (topList.isEmpty()) {
                    binding.tvEmptyState.setVisibility(View.VISIBLE);
                    binding.tvEmptyState.setText("No CAs available yet.");
                } else {
                    binding.tvEmptyState.setVisibility(View.GONE);
                }
            }

            @Override
            public void onError(String error) {
                binding.progressTopRated.setVisibility(View.GONE);
                Toast.makeText(HomeActivity.this, "Error fetching CAs: " + error, Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void setupRecyclerView() {
        // Setup Top Rated
        LinearLayoutManager lm = new LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false);
        binding.rvTopRated.setLayoutManager(lm);
        adapter = new CAAdapter(new ArrayList<>(), this, true);
        binding.rvTopRated.setAdapter(adapter);
        new androidx.recyclerview.widget.LinearSnapHelper().attachToRecyclerView(binding.rvTopRated);

        binding.rvTopRated.addOnScrollListener(new androidx.recyclerview.widget.RecyclerView.OnScrollListener() {
            @Override
            public void onScrolled(@NonNull androidx.recyclerview.widget.RecyclerView recyclerView, int dx, int dy) {
                super.onScrolled(recyclerView, dx, dy);
                applyCenterScale(recyclerView);
            }
        });

        binding.rvTopRated.post(() -> applyCenterScale(binding.rvTopRated));
        
        // Setup Featured
        LinearLayoutManager lmFeatured = new LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false);
        binding.rvFeaturedCAs.setLayoutManager(lmFeatured);
        featuredAdapter = new CAAdapter(new ArrayList<>(), this, true);
        binding.rvFeaturedCAs.setAdapter(featuredAdapter);
        new androidx.recyclerview.widget.LinearSnapHelper().attachToRecyclerView(binding.rvFeaturedCAs);
        
        binding.rvFeaturedCAs.addOnScrollListener(new androidx.recyclerview.widget.RecyclerView.OnScrollListener() {
            @Override
            public void onScrolled(@NonNull androidx.recyclerview.widget.RecyclerView recyclerView, int dx, int dy) {
                super.onScrolled(recyclerView, dx, dy);
                applyCenterScale(recyclerView);
            }
        });
        
        binding.rvFeaturedCAs.post(() -> applyCenterScale(binding.rvFeaturedCAs));
    }

    private void applyCenterScale(androidx.recyclerview.widget.RecyclerView recyclerView) {
        int centerX = recyclerView.getWidth() / 2;
        for (int i = 0; i < recyclerView.getChildCount(); i++) {
            View child = recyclerView.getChildAt(i);
            int childCenterX = (child.getLeft() + child.getRight()) / 2;
            int distance = Math.abs(centerX - childCenterX);
            float maxScale = 1.0f;
            float minScale = 0.9f;
            int maxDistance = recyclerView.getWidth() / 2;
            float scale = maxScale - (Math.min(distance, maxDistance) / (float) maxDistance) * (maxScale - minScale);
            child.setScaleX(scale);
            child.setScaleY(scale);
        }
    }

    private void setupDrawer() {
        binding.ivMenu.setOnClickListener(v -> binding.drawerLayout.openDrawer(GravityCompat.START));
        
        // Hide Requests item for Customers
        android.view.Menu menu = binding.navView.getMenu();
        MenuItem requestsItem = menu.findItem(R.id.nav_requests);
        if (requestsItem != null) {
            requestsItem.setVisible(false);
        }

        binding.navView.setNavigationItemSelectedListener(new NavigationView.OnNavigationItemSelectedListener() {
            @Override
            public boolean onNavigationItemSelected(@NonNull MenuItem item) {
                int id = item.getItemId();

                if (id == R.id.nav_home) {
                    // Already on Home
                } else if (id == R.id.nav_profile) {
                    startActivity(new Intent(HomeActivity.this, ProfileActivity.class));
                } else if (id == R.id.nav_chats) {
                    startActivity(new Intent(HomeActivity.this, MyChatsActivity.class));
                } else if (id == R.id.nav_wallet) {
                   startActivity(new Intent(HomeActivity.this, WalletActivity.class));
                } else if (id == R.id.nav_bookings) {
                   startActivity(new Intent(HomeActivity.this, MyBookingsActivity.class));
                } else if (id == R.id.nav_docs) {
                    startActivity(new Intent(HomeActivity.this, MyDocumentsActivity.class));
                } else if (id == R.id.nav_community) {
                    startActivity(new Intent(HomeActivity.this, CommunityActivity.class));
                } else if (id == R.id.nav_explore) {
                    startActivity(new Intent(HomeActivity.this, ExploreCAsActivity.class));
                } else if (id == R.id.nav_theme_light) {
                    com.example.taxconnect.utils.ThemeHelper.setTheme(HomeActivity.this, com.example.taxconnect.utils.ThemeHelper.THEME_LIGHT);
                } else if (id == R.id.nav_theme_dark) {
                    com.example.taxconnect.utils.ThemeHelper.setTheme(HomeActivity.this, com.example.taxconnect.utils.ThemeHelper.THEME_DARK);
                } else if (id == R.id.nav_theme_system) {
                    com.example.taxconnect.utils.ThemeHelper.setTheme(HomeActivity.this, com.example.taxconnect.utils.ThemeHelper.THEME_SYSTEM);
                } else if (id == R.id.nav_logout) {
                    FirebaseAuth.getInstance().signOut();
                    Intent intent = new Intent(HomeActivity.this, LoginActivity.class);
                    intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                    startActivity(intent);
                    finish();
                }

                binding.drawerLayout.closeDrawer(GravityCompat.START);
                return true;
            }
        });

        // Set current theme checked
        int currentTheme = com.example.taxconnect.utils.ThemeHelper.getSelectedTheme(this);
        if (currentTheme == com.example.taxconnect.utils.ThemeHelper.THEME_LIGHT) {
            menu.findItem(R.id.nav_theme_light).setChecked(true);
        } else if (currentTheme == com.example.taxconnect.utils.ThemeHelper.THEME_DARK) {
            menu.findItem(R.id.nav_theme_dark).setChecked(true);
        } else {
            menu.findItem(R.id.nav_theme_system).setChecked(true);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        updateNavHeader();
    }

    private void updateNavHeader() {
        View headerView = binding.navView.getHeaderView(0);
        TextView navName = headerView.findViewById(R.id.nav_header_name);
        TextView navEmail = headerView.findViewById(R.id.nav_header_email);
        ImageView navImage = headerView.findViewById(R.id.nav_header_image);

        String uid = FirebaseAuth.getInstance().getUid();
        if (uid != null) {
            repository.fetchUser(uid, new DataRepository.DataCallback<UserModel>() {
                @Override
                public void onSuccess(UserModel user) {
                    if (user != null) {
                        navName.setText(user.getName());
                        navEmail.setText(user.getEmail());
                        
                        Glide.with(HomeActivity.this)
                            .load(user.getProfileImageUrl())
                            .placeholder(android.R.drawable.ic_menu_gallery)
                            .error(android.R.drawable.ic_menu_gallery)
                            .circleCrop()
                            .into(navImage);

                        Glide.with(HomeActivity.this)
                            .load(user.getProfileImageUrl())
                            .placeholder(R.drawable.ic_launcher_foreground)
                            .error(R.drawable.ic_launcher_foreground)
                            .circleCrop()
                            .into(binding.ivProfile);
                    }
                }

                @Override
                public void onError(String error) {
                    // Ignore
                }
            });
        }
    }

    @Override
    public void onConnectClick(UserModel ca) {
        Intent intent = new Intent(HomeActivity.this, CADetailActivity.class);
        intent.putExtra("CA_DATA", ca);
        startActivity(intent);
    }
}