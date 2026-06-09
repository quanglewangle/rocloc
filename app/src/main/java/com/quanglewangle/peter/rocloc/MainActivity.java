package com.quanglewangle.peter.rocloc;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.inputmethod.EditorInfo;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.google.android.material.snackbar.Snackbar;
import com.quanglewangle.peter.rocloc.data.OperatorEntity;
import com.quanglewangle.peter.rocloc.data.Repository;
import com.quanglewangle.peter.rocloc.databinding.ActivityMainBinding;

import java.util.ArrayList;
import java.util.List;

public class MainActivity extends AppCompatActivity {

    private ActivityMainBinding binding;
    private Repository repository;
    private OperatorListAdapter adapter;
    private final List<OperatorEntity> recentList = new ArrayList<>();
    private Handler mainHandler;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        setSupportActionBar(binding.toolbar);

        mainHandler = new Handler(Looper.getMainLooper());
        repository = new Repository(this);

        adapter = new OperatorListAdapter(recentList, entity -> openDetail(entity.callsign));
        binding.recyclerView.setLayoutManager(new LinearLayoutManager(this));
        binding.recyclerView.setAdapter(adapter);

        binding.searchButton.setOnClickListener(v -> performSearch());
        binding.callsignInput.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                performSearch();
                return true;
            }
            return false;
        });

        // HERE FAB — shows current GPS position with no target callsign
        binding.fabHere.setOnClickListener(v -> {
            Intent i = new Intent(this, DetailActivity.class);
            i.putExtra(DetailActivity.EXTRA_HERE_ONLY, true);
            startActivity(i);
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        loadRecent();
    }

    private void performSearch() {
        String input = binding.callsignInput.getText().toString().trim().toUpperCase();
        if (input.isEmpty()) return;

        binding.progressBar.setVisibility(android.view.View.VISIBLE);

        repository.lookup(input, false, new Repository.LookupCallback() {
            @Override
            public void onResult(OperatorEntity entity, boolean fromCache) {
                binding.progressBar.setVisibility(android.view.View.GONE);
                openDetail(entity.callsign);
            }

            @Override
            public void onError(String error) {
                binding.progressBar.setVisibility(android.view.View.GONE);
                Snackbar.make(binding.getRoot(), "Not found: " + input, Snackbar.LENGTH_LONG).show();
            }
        }, mainHandler);
    }

    private void openDetail(String callsign) {
        Intent i = new Intent(this, DetailActivity.class);
        i.putExtra(DetailActivity.EXTRA_CALLSIGN, callsign);
        startActivity(i);
    }

    private void loadRecent() {
        repository.getRecent(mainHandler, list -> {
            recentList.clear();
            recentList.addAll(list);
            adapter.notifyDataSetChanged();
        });
    }
}
