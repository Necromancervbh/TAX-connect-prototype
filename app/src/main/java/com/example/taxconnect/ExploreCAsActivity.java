package com.example.taxconnect;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.text.Editable;
import android.text.TextWatcher;
import android.widget.ArrayAdapter;
import android.widget.Toast;
import android.widget.EditText;
import androidx.appcompat.app.AlertDialog;
import android.text.InputType;
import android.widget.LinearLayout;
import android.content.DialogInterface;
import android.graphics.Typeface;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.style.StyleSpan;
import com.google.android.material.chip.Chip;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.taxconnect.adapter.CAAdapter;
import com.example.taxconnect.databinding.ActivityExploreCasBinding;
import com.example.taxconnect.model.UserModel;
import com.google.firebase.firestore.DocumentSnapshot;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;
import java.util.LinkedHashSet;
import java.util.Set;

public class ExploreCAsActivity extends AppCompatActivity implements CAAdapter.OnConnectClickListener {

    private ActivityExploreCasBinding binding;
    private DataRepository repository;
    private CAAdapter adapter;
    private List<UserModel> fullCAList = new ArrayList<>();
    private double filterMinPrice = 0;
    private double filterMaxPrice = Double.MAX_VALUE;
    private String currentSearchQuery = "";
    private boolean filterVerifiedOnly = false;
    private android.content.SharedPreferences prefs;
    private static final String PREF_RECENT_SEARCHES = "recent_searches";
    private final android.os.Handler searchHandler = new android.os.Handler(android.os.Looper.getMainLooper());
    private final java.lang.Runnable searchRunnable = new java.lang.Runnable() {
        @Override
        public void run() {
            applySearch(currentSearchQuery);
        }
    };
    private java.util.Set<String> favoriteIds = new java.util.HashSet<>();
    private boolean favoritesOnly = false;
    private String currentUserCity = null;
    private DocumentSnapshot lastCaSnapshot = null;
    private boolean isLoadingCAs = false;
    private boolean hasMoreCAs = true;
    private static final int CA_PAGE_SIZE = 30;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityExploreCasBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        repository = DataRepository.getInstance();
        prefs = getSharedPreferences("explore_prefs", MODE_PRIVATE);
        
        setupUI();
        setupRecyclerView();
        setupSorting();
        setupSearch();
        renderRecentChips(loadRecentSearches());
        showLoading(true);
        fetchCAList();
        loadFavorites();
        loadCurrentUserCity();
        
        String initialQuery = getIntent().getStringExtra("QUERY");
        if (initialQuery != null && !initialQuery.isEmpty()) {
            binding.etSearch.setText(initialQuery);
            binding.etSearch.setSelection(initialQuery.length());
            saveRecentSearch(initialQuery);
            renderRecentChips(loadRecentSearches());
        }
        favoritesOnly = getIntent().getBooleanExtra("FAVORITES_ONLY", false);
    }

    private void setupUI() {
        binding.ivBack.setOnClickListener(v -> finish());
        binding.btnFilter.setOnClickListener(v -> showPriceFilterDialog());
        binding.chipVerified.setOnCheckedChangeListener((buttonView, isChecked) -> {
            filterVerifiedOnly = isChecked;
            filterList(currentSearchQuery);
        });
    }

    private void setupRecyclerView() {
        adapter = new CAAdapter(new ArrayList<>(), this, this, false);
        binding.rvCAList.setLayoutManager(new LinearLayoutManager(this));
        binding.rvCAList.setAdapter(adapter);
        binding.rvCAList.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrolled(RecyclerView recyclerView, int dx, int dy) {
                super.onScrolled(recyclerView, dx, dy);
                if (dy <= 0) return;
                LinearLayoutManager layoutManager = (LinearLayoutManager) recyclerView.getLayoutManager();
                if (layoutManager == null) return;
                int lastVisible = layoutManager.findLastVisibleItemPosition();
                int total = adapter.getItemCount();
                if (!isLoadingCAs && hasMoreCAs && lastVisible >= total - 5) {
                    loadMoreCAs(false);
                }
            }
        });
        adapter.setOnSaveClickListener(ca -> {
            String uid = com.google.firebase.auth.FirebaseAuth.getInstance().getUid();
            if (uid == null) return;
            repository.toggleFavorite(uid, ca.getUid(), new DataRepository.DataCallback<Boolean>() {
                @Override
                public void onSuccess(Boolean isFav) {
                    if (isFav) {
                        favoriteIds.add(ca.getUid());
                    } else {
                        favoriteIds.remove(ca.getUid());
                    }
                    adapter.setFavorites(favoriteIds);
                }
                @Override
                public void onError(String error) {}
            });
        });
    }

    private void showPriceFilterDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Filter by Price Range");

        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(32, 16, 32, 16);

        final EditText inputMin = new EditText(this);
        inputMin.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
        inputMin.setHint("Min Price (₹)");
        if (filterMinPrice > 0) inputMin.setText(String.valueOf(filterMinPrice));
        layout.addView(inputMin);

        final EditText inputMax = new EditText(this);
        inputMax.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
        inputMax.setHint("Max Price (₹)");
        if (filterMaxPrice < Double.MAX_VALUE) inputMax.setText(String.valueOf(filterMaxPrice));
        layout.addView(inputMax);

        builder.setView(layout);

        builder.setPositiveButton("Apply", (dialog, which) -> {
            String minStr = inputMin.getText().toString();
            String maxStr = inputMax.getText().toString();

            try {
                filterMinPrice = minStr.isEmpty() ? 0 : Double.parseDouble(minStr);
            } catch (NumberFormatException e) {
                filterMinPrice = 0;
            }

            try {
                filterMaxPrice = maxStr.isEmpty() ? Double.MAX_VALUE : Double.parseDouble(maxStr);
            } catch (NumberFormatException e) {
                filterMaxPrice = Double.MAX_VALUE;
            }

            // Re-apply filters
            filterList(currentSearchQuery);
        });

        builder.setNegativeButton("Clear", (dialog, which) -> {
            filterMinPrice = 0;
            filterMaxPrice = Double.MAX_VALUE;
            filterList(currentSearchQuery);
            dialog.cancel();
        });
        
        builder.setNeutralButton("Cancel", (dialog, which) -> dialog.cancel());

        builder.show();
    }

    private void setupSorting() {
        String[] sortOptions = {"Rating (High to Low)", "Experience (High to Low)", "Charges (Low to High)"};
        ArrayAdapter<String> sortAdapter = new ArrayAdapter<>(this, R.layout.item_spinner, sortOptions);
        sortAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        binding.spinnerSort.setAdapter(sortAdapter);

        binding.spinnerSort.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(android.widget.AdapterView<?> parent, View view, int position, long id) {
                sortList(position);
            }

            @Override
            public void onNothingSelected(android.widget.AdapterView<?> parent) {}
        });
    }

    private void setupSearch() {
        binding.etSearch.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                currentSearchQuery = s.toString();
                debounceSearch();
            }

            @Override
            public void afterTextChanged(Editable s) {}
        });
    }

    private void fetchCAList() {
        fullCAList.clear();
        lastCaSnapshot = null;
        hasMoreCAs = true;
        loadMoreCAs(true);
    }

    private void loadMoreCAs(boolean initialLoad) {
        if (isLoadingCAs || !hasMoreCAs) return;
        isLoadingCAs = true;
        if (initialLoad) {
            showLoading(true);
        }

        repository.getCaPage(CA_PAGE_SIZE, lastCaSnapshot, new DataRepository.DataCallback<DataRepository.PageResult<UserModel>>() {
            @Override
            public void onSuccess(DataRepository.PageResult<UserModel> data) {
                if (initialLoad) {
                    showLoading(false);
                }
                if (data == null || data.getItems() == null) {
                     binding.tvEmptyState.setVisibility(View.VISIBLE);
                     binding.rvCAList.setVisibility(View.GONE);
                     binding.tvRegisteredCount.setText("0 Registered CAs");
                    isLoadingCAs = false;
                     return;
                }
                
                List<UserModel> newItems = data.getItems();
                if (newItems.isEmpty()) {
                    hasMoreCAs = false;
                } else {
                    fullCAList.addAll(newItems);
                    lastCaSnapshot = data.getLastSnapshot();
                }
                
                String countText = String.valueOf(fullCAList.size());
                String fullText = countText + " Registered CAs";
                SpannableString spannable = new SpannableString(fullText);
                spannable.setSpan(new StyleSpan(Typeface.BOLD), 0, countText.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                binding.tvRegisteredCount.setText(spannable);
                
                if (fullCAList.isEmpty()) {
                    binding.tvEmptyState.setVisibility(View.VISIBLE);
                    binding.rvCAList.setVisibility(View.GONE);
                } else {
                    binding.tvEmptyState.setVisibility(View.GONE);
                    binding.rvCAList.setVisibility(View.VISIBLE);
                    sortList(binding.spinnerSort.getSelectedItemPosition());
                    adapter.notifyDataSetChanged();
                }
                isLoadingCAs = false;
            }

            @Override
            public void onError(String error) {
                if (initialLoad) {
                    showLoading(false);
                }
                Toast.makeText(ExploreCAsActivity.this, "Error fetching CAs: " + error, Toast.LENGTH_SHORT).show();
                binding.tvRegisteredCount.setText("0 Registered CAs");
                binding.tvEmptyState.setVisibility(View.VISIBLE);
                binding.rvCAList.setVisibility(View.GONE);
                isLoadingCAs = false;
            }
        });
    }

    private void filterList(String query) {
        currentSearchQuery = query;
        List<UserModel> filtered = fullCAList.stream()
                .filter(ca -> {
                    boolean matchesQuery = query.isEmpty() ||
                            (ca.getName() != null && ca.getName().toLowerCase().contains(query.toLowerCase())) ||
                            (ca.getSpecialization() != null && ca.getSpecialization().toLowerCase().contains(query.toLowerCase())) ||
                            (ca.getCity() != null && ca.getCity().toLowerCase().contains(query.toLowerCase()));
                    
                    boolean matchesPrice = true;
                    if (ca.getMinCharges() != null) {
                        try {
                            String num = ca.getMinCharges().replaceAll("[^0-9.]", "");
                            if (!num.isEmpty()) {
                                double price = Double.parseDouble(num);
                                matchesPrice = price >= filterMinPrice && price <= filterMaxPrice;
                            }
                        } catch (NumberFormatException e) {
                            // Ignore
                        }
                    }
                    boolean matchesVerified = !filterVerifiedOnly || ca.isVerified();
                    return matchesQuery && matchesPrice && matchesVerified;
                })
                .collect(Collectors.toList());

        if (filtered.isEmpty()) {
             binding.tvEmptyState.setVisibility(View.VISIBLE);
             binding.rvCAList.setVisibility(View.GONE);
        } else {
             binding.tvEmptyState.setVisibility(View.GONE);
             binding.rvCAList.setVisibility(View.VISIBLE);
        }
        adapter.updateList(filtered);
    }
    
    private void sortList(int position) {
        if (fullCAList == null) return;
        
        switch (position) {
            case 0: // Rating (High to Low)
                Collections.sort(fullCAList, (o1, o2) -> Double.compare(o2.getRating(), o1.getRating()));
                break;
            case 1: // Experience (High to Low)
                Collections.sort(fullCAList, (o1, o2) -> {
                    double e1 = parseExperience(o1.getExperience());
                    double e2 = parseExperience(o2.getExperience());
                    return Double.compare(e2, e1);
                });
                break;
            case 2: // Charges (Low to High)
                Collections.sort(fullCAList, (o1, o2) -> {
                    double c1 = parseCharges(o1.getMinCharges());
                    double c2 = parseCharges(o2.getMinCharges());
                    return Double.compare(c1, c2);
                });
                break;
        }
        filterList(currentSearchQuery);
    }

    private double parseExperience(String exp) {
        if (exp == null) return 0;
        try {
            return Double.parseDouble(exp.replaceAll("[^0-9.]", ""));
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private double parseCharges(String charge) {
        if (charge == null) return Double.MAX_VALUE;
        try {
            return Double.parseDouble(charge.replaceAll("[^0-9.]", ""));
        } catch (NumberFormatException e) {
            return Double.MAX_VALUE;
        }
    }

    private void debounceSearch() {
        searchHandler.removeCallbacks(searchRunnable);
        searchHandler.postDelayed(searchRunnable, 300);
    }

    private void applySearch(String query) {
        filterList(query);
        if (query != null && query.trim().length() >= 2) {
            saveRecentSearch(query.trim());
            renderRecentChips(loadRecentSearches());
        }
    }

    private List<String> loadRecentSearches() {
        String csv = prefs.getString(PREF_RECENT_SEARCHES, "");
        List<String> result = new ArrayList<>();
        if (csv == null || csv.isEmpty()) return result;
        for (String s : csv.split("\\|")) {
            if (!s.trim().isEmpty()) result.add(s.trim());
        }
        return result;
    }

    private void saveRecentSearch(String query) {
        List<String> current = loadRecentSearches();
        Set<String> set = new LinkedHashSet<>(current);
        // Move to front: remove then add
        set.remove(query);
        set.add(query);
        // Keep last 5 (preserve insertion order, we will trim if size > 5)
        List<String> ordered = new ArrayList<>(set);
        if (ordered.size() > 5) {
            ordered = ordered.subList(ordered.size() - 5, ordered.size());
        }
        String csv = String.join("|", ordered);
        prefs.edit().putString(PREF_RECENT_SEARCHES, csv).apply();
    }

    private void clearRecentSearches() {
        prefs.edit().remove(PREF_RECENT_SEARCHES).apply();
        renderRecentChips(new ArrayList<>());
    }

    private void renderRecentChips(List<String> items) {
        binding.chipGroupRecent.removeAllViews();
        binding.hsRecentSearches.setVisibility(items.isEmpty() ? View.GONE : View.VISIBLE);
        for (String label : items) {
            Chip chip = new Chip(this);
            chip.setText(label);
            chip.setCheckable(false);
            chip.setClickable(true);
            chip.setOnClickListener(v -> {
                binding.etSearch.setText(label);
                binding.etSearch.setSelection(label.length());
                applySearch(label);
            });
            binding.chipGroupRecent.addView(chip);
        }
        if (!items.isEmpty()) {
            Chip clearChip = new Chip(this);
            clearChip.setText("Clear");
            clearChip.setCheckable(false);
            clearChip.setClickable(true);
            clearChip.setOnClickListener(v -> clearRecentSearches());
            binding.chipGroupRecent.addView(clearChip);
        }
    }

    private void showLoading(boolean show) {
        binding.progressBar.setVisibility(show ? View.VISIBLE : View.GONE);
        binding.rvCAList.setVisibility(show ? View.GONE : View.VISIBLE);
        binding.tvEmptyState.setVisibility(View.GONE);
    }

    private void loadFavorites() {
        String uid = com.google.firebase.auth.FirebaseAuth.getInstance().getUid();
        if (uid == null) return;
        repository.getFavoriteCaIds(uid, new DataRepository.DataCallback<List<String>>() {
            @Override
            public void onSuccess(List<String> ids) {
                favoriteIds.clear();
                favoriteIds.addAll(ids);
                adapter.setFavorites(favoriteIds);
            }
            @Override
            public void onError(String error) {}
        });
    }

    private void loadCurrentUserCity() {
        String uid = com.google.firebase.auth.FirebaseAuth.getInstance().getUid();
        if (uid == null) return;
        repository.fetchUser(uid, new DataRepository.DataCallback<UserModel>() {
            @Override
            public void onSuccess(UserModel user) {
                if (user != null) {
                    currentUserCity = user.getCity();
                }
            }
            @Override
            public void onError(String error) {}
        });
    }

    @Override
    public void onConnectClick(UserModel ca) {
        Intent intent = new Intent(ExploreCAsActivity.this, CADetailActivity.class);
        intent.putExtra("CA_DATA", ca);
        startActivity(intent);
    }
}
