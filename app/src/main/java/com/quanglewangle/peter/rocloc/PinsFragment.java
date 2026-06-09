package com.quanglewangle.peter.rocloc;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.quanglewangle.peter.rocloc.api.ApiService;
import com.quanglewangle.peter.rocloc.data.Site;

import java.util.ArrayList;
import java.util.List;

public class PinsFragment extends Fragment {

    private SiteAdapter adapter;
    private final List<Site> pinList = new ArrayList<>();
    private final ApiService api = new ApiService();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    private ProgressBar progressBar;
    private TextView emptyText;

    @Nullable @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_list, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        progressBar = view.findViewById(R.id.progressBar);
        emptyText   = view.findViewById(R.id.emptyText);

        RecyclerView rv = view.findViewById(R.id.recyclerView);
        adapter = new SiteAdapter(pinList, site -> showOnMap(site));
        rv.setLayoutManager(new LinearLayoutManager(requireContext()));
        rv.setAdapter(adapter);

        load();
    }

    @Override public void onResume() { super.onResume(); load(); }

    private void load() {
        progressBar.setVisibility(View.VISIBLE);
        emptyText.setVisibility(View.GONE);
        api.getSites(new ApiService.SitesCallback() {
            @Override public void onResult(List<Site> sites) {
                mainHandler.post(() -> {
                    pinList.clear();
                    for (Site s : sites) { if (s.isPin()) pinList.add(s); }
                    adapter.notifyDataSetChanged();
                    progressBar.setVisibility(View.GONE);
                    if (pinList.isEmpty()) {
                        emptyText.setText(R.string.no_pins);
                        emptyText.setVisibility(View.VISIBLE);
                    }
                });
            }
            @Override public void onError(String error) {
                mainHandler.post(() -> {
                    progressBar.setVisibility(View.GONE);
                    emptyText.setText(error);
                    emptyText.setVisibility(View.VISIBLE);
                });
            }
        });
    }

    private void showOnMap(Site site) {
        if (getActivity() instanceof MainActivity) {
            ((MainActivity) getActivity()).showSiteOnMap(site);
        }
    }
}
