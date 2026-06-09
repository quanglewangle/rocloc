package com.quanglewangle.peter.rocloc;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.android.material.snackbar.Snackbar;
import com.quanglewangle.peter.rocloc.data.OperatorEntity;
import com.quanglewangle.peter.rocloc.data.Repository;

import android.widget.Button;
import android.widget.EditText;
import android.widget.ProgressBar;

import java.util.ArrayList;
import java.util.List;

public class SearchFragment extends Fragment {

    private Repository repository;
    private OperatorListAdapter adapter;
    private final List<OperatorEntity> recentList = new ArrayList<>();
    private Handler mainHandler;

    private EditText callsignInput;
    private Button searchButton;
    private ProgressBar progressBar;

    @Nullable @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_search, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        mainHandler = new Handler(Looper.getMainLooper());
        repository  = new Repository(requireContext());

        callsignInput = view.findViewById(R.id.callsignInput);
        searchButton  = view.findViewById(R.id.searchButton);
        progressBar   = view.findViewById(R.id.progressBar);

        RecyclerView rv = view.findViewById(R.id.recyclerView);
        adapter = new OperatorListAdapter(recentList, entity -> openDetail(entity.callsign));
        rv.setLayoutManager(new LinearLayoutManager(requireContext()));
        rv.setAdapter(adapter);

        searchButton.setOnClickListener(v -> performSearch());
        callsignInput.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_SEARCH) { performSearch(); return true; }
            return false;
        });

        FloatingActionButton fab = view.findViewById(R.id.fabHere);
        fab.setOnClickListener(v -> {
            Intent i = new Intent(requireContext(), DetailActivity.class);
            i.putExtra(DetailActivity.EXTRA_HERE_ONLY, true);
            startActivity(i);
        });

        loadRecent();
    }

    @Override public void onResume() {
        super.onResume();
        loadRecent();
    }

    private void performSearch() {
        String input = callsignInput.getText().toString().trim().toUpperCase();
        if (input.isEmpty()) return;
        progressBar.setVisibility(View.VISIBLE);
        repository.lookup(input, false, new Repository.LookupCallback() {
            @Override public void onResult(OperatorEntity entity, boolean fromCache) {
                progressBar.setVisibility(View.GONE);
                openDetail(entity.callsign);
            }
            @Override public void onError(String error) {
                progressBar.setVisibility(View.GONE);
                Snackbar.make(requireView(), "Not found: " + input, Snackbar.LENGTH_LONG).show();
            }
        }, mainHandler);
    }

    private void openDetail(String callsign) {
        Intent i = new Intent(requireContext(), DetailActivity.class);
        i.putExtra(DetailActivity.EXTRA_CALLSIGN, callsign);
        startActivity(i);
    }

    private void loadRecent() {
        if (repository == null) return;
        repository.getRecent(mainHandler, list -> {
            recentList.clear();
            recentList.addAll(list);
            if (adapter != null) adapter.notifyDataSetChanged();
        });
    }
}
