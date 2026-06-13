package com.quanglewangle.peter.rocloc.data;

import android.os.Handler;
import android.os.Looper;

import com.quanglewangle.peter.rocloc.api.ApiService;

import java.util.ArrayList;
import java.util.List;

public class SiteCache {

    private static final SiteCache INSTANCE = new SiteCache();
    public static SiteCache get() { return INSTANCE; }

    private final ApiService api = new ApiService();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private List<Site> cached;
    private boolean fetching = false;
    private final List<ApiService.SitesCallback> pending = new ArrayList<>();

    private SiteCache() {}

    public void getSites(ApiService.SitesCallback callback) {
        if (cached != null) {
            callback.onResult(new ArrayList<>(cached));
            return;
        }
        pending.add(callback);
        if (fetching) return;
        fetching = true;
        api.getSites(new ApiService.SitesCallback() {
            @Override public void onResult(List<Site> sites) {
                mainHandler.post(() -> {
                    cached = sites;
                    fetching = false;
                    List<ApiService.SitesCallback> toNotify = new ArrayList<>(pending);
                    pending.clear();
                    for (ApiService.SitesCallback cb : toNotify) cb.onResult(new ArrayList<>(sites));
                });
            }
            @Override public void onError(String error) {
                mainHandler.post(() -> {
                    fetching = false;
                    List<ApiService.SitesCallback> toNotify = new ArrayList<>(pending);
                    pending.clear();
                    for (ApiService.SitesCallback cb : toNotify) cb.onError(error);
                });
            }
        });
    }

    public void invalidate() { cached = null; }

    public boolean hasCachedData() { return cached != null; }
}
