package com.quanglewangle.peter.rocloc;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewGroup;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.quanglewangle.peter.rocloc.api.ApiService;
import com.quanglewangle.peter.rocloc.data.Site;
import com.quanglewangle.peter.rocloc.data.SiteCache;

import java.util.ArrayList;
import java.util.List;

public class PinsFragment extends Fragment {

    private SiteAdapter adapter;
    private final List<Site> pinList = new ArrayList<>();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    private ProgressBar progressBar;
    private TextView emptyText;
    private SwipeRefreshLayout swipeRefresh;

    @Nullable @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_list, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        progressBar  = view.findViewById(R.id.progressBar);
        emptyText    = view.findViewById(R.id.emptyText);
        swipeRefresh = view.findViewById(R.id.swipeRefresh);
        swipeRefresh.setOnRefreshListener(() -> {
            SiteCache.get().invalidate();
            load();
        });

        RecyclerView rv = view.findViewById(R.id.recyclerView);
        adapter = new SiteAdapter(pinList, site -> showOnMap(site));
        rv.setLayoutManager(new LinearLayoutManager(requireContext()));
        rv.setAdapter(adapter);
        addLongPressDetector(rv, pinList);
    }

    @Override public void onResume() { super.onResume(); if (pinList.isEmpty()) load(); }

    private void load() {
        progressBar.setVisibility(View.VISIBLE);
        emptyText.setVisibility(View.GONE);
        SiteCache.get().getSites(new ApiService.SitesCallback() {
            @Override public void onResult(List<Site> sites) {
                mainHandler.post(() -> {
                    pinList.clear();
                    for (Site s : sites) { if (s.isPin()) pinList.add(s); }
                    java.util.Collections.sort(pinList, (a, b) -> {
                        String na = a.name != null ? a.name : a.displayCallsign();
                        String nb = b.name != null ? b.name : b.displayCallsign();
                        return na.compareToIgnoreCase(nb);
                    });
                    adapter.notifyDataSetChanged();
                    progressBar.setVisibility(View.GONE);
                    swipeRefresh.setRefreshing(false);
                    if (pinList.isEmpty()) {
                        emptyText.setText(R.string.no_pins);
                        emptyText.setVisibility(View.VISIBLE);
                    }
                });
            }
            @Override public void onError(String error) {
                mainHandler.post(() -> {
                    progressBar.setVisibility(View.GONE);
                    swipeRefresh.setRefreshing(false);
                    emptyText.setText(error);
                    emptyText.setVisibility(View.VISIBLE);
                });
            }
        });
    }

    private void addLongPressDetector(RecyclerView rv, List<Site> list) {
        Handler h = new Handler(Looper.getMainLooper());
        Runnable[] pending = {null};
        rv.addOnItemTouchListener(new RecyclerView.SimpleOnItemTouchListener() {
            @Override public boolean onInterceptTouchEvent(@NonNull RecyclerView r, @NonNull MotionEvent e) {
                if (e.getActionMasked() == MotionEvent.ACTION_DOWN) {
                    View child = rv.findChildViewUnder(e.getX(), e.getY());
                    if (child != null) {
                        int pos = rv.getChildAdapterPosition(child);
                        if (pos >= 0 && pos < list.size()) {
                            Site site = list.get(pos);
                            if (pending[0] != null) h.removeCallbacks(pending[0]);
                            pending[0] = () -> { pending[0] = null; SiteAdapter.showDetail(child, site); };
                            h.postDelayed(pending[0], ViewConfiguration.getLongPressTimeout());
                        }
                    }
                } else if (e.getActionMasked() == MotionEvent.ACTION_UP
                        || e.getActionMasked() == MotionEvent.ACTION_CANCEL) {
                    if (pending[0] != null) { h.removeCallbacks(pending[0]); pending[0] = null; }
                }
                return false;
            }
        });
    }

    private void showOnMap(Site site) {
        if (getActivity() instanceof MainActivity) {
            ((MainActivity) getActivity()).showSiteOnMap(site);
        }
    }
}
