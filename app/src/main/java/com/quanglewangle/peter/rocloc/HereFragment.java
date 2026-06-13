package com.quanglewangle.peter.rocloc;

import android.Manifest;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.drawable.BitmapDrawable;
import android.location.Location;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;

import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationCallback;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationResult;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.location.Priority;
import com.quanglewangle.peter.rocloc.api.ApiService;
import com.quanglewangle.peter.rocloc.data.Site;
import com.quanglewangle.peter.rocloc.data.SiteCache;
import com.quanglewangle.peter.rocloc.terrain.TerrainEngine;
import com.quanglewangle.peter.rocloc.terrain.TerrainStore;

import org.osmdroid.config.Configuration;
import org.osmdroid.tileprovider.tilesource.TileSourceFactory;
import org.osmdroid.util.GeoPoint;
import org.osmdroid.views.MapView;
import org.osmdroid.views.overlay.Marker;
import org.osmdroid.views.overlay.Polyline;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

public class HereFragment extends Fragment {

    private static final double DOWNLOAD_RADIUS_KM = 40;

    private MapView mapView;
    private ProgressBar progressBar;
    private TextView statusText;
    private Button downloadBtn;
    private final ApiService api = new ApiService();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private ExecutorService bgExecutor;

    private FusedLocationProviderClient locationClient;
    private LocationCallback locationCallback;
    private Location hereLocation;
    private Marker hereMarker;
    private List<Site> allSites = new ArrayList<>();
    private final List<Polyline> losLines = new ArrayList<>();
    private final List<Marker> siteMarkers = new ArrayList<>();
    private boolean sitesLoaded = false;

    private TerrainStore terrainStore;
    private TerrainEngine terrainEngine;
    private double lastLoSLat = Double.NaN;
    private double lastLoSLon = Double.NaN;

    @Nullable @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        Configuration.getInstance().load(requireContext(),
                requireContext().getSharedPreferences("osmdroid", 0));
        Configuration.getInstance().setUserAgentValue(requireContext().getPackageName());
        java.io.File osmdroidBase = new java.io.File(requireContext().getCacheDir(), "osmdroid");
        Configuration.getInstance().setOsmdroidBasePath(osmdroidBase);
        Configuration.getInstance().setOsmdroidTileCache(new java.io.File(osmdroidBase, "tiles"));
        return inflater.inflate(R.layout.fragment_here, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        progressBar = view.findViewById(R.id.hereProgress);
        statusText  = view.findViewById(R.id.hereStatus);
        downloadBtn = view.findViewById(R.id.downloadTerrainBtn);
        mapView     = view.findViewById(R.id.hereMapView);
        mapView.setTileSource(TileSourceFactory.MAPNIK);
        mapView.setMultiTouchControls(true);
        mapView.setHorizontalMapRepetitionEnabled(false);
        mapView.setVerticalMapRepetitionEnabled(false);
        mapView.getController().setZoom(6.5);
        mapView.getController().setCenter(new GeoPoint(54.5, -3.0));

        bgExecutor    = Executors.newCachedThreadPool();
        terrainStore  = new TerrainStore(requireContext());
        terrainEngine = new TerrainEngine(terrainStore);

        updateDownloadBtn();
        downloadBtn.setOnClickListener(v -> downloadTerrain());

        locationClient = LocationServices.getFusedLocationProviderClient(requireActivity());
        statusText.setText(R.string.getting_location);
        startLocationUpdates();
        if (!sitesLoaded) loadSites();
    }

    @Override public void onResume() {
        super.onResume();
        if (mapView != null) mapView.onResume();
    }

    @Override public void onPause() {
        super.onPause();
        if (mapView != null) mapView.onPause();
        stopLocationUpdates();
    }

    @Override public void onDestroyView() {
        super.onDestroyView();
        bgExecutor.shutdownNow();
        mapView = null;
    }

    @Override public void onHiddenChanged(boolean hidden) {
        super.onHiddenChanged(hidden);
        if (!hidden && mapView != null) {
            mapView.invalidate();
            startLocationUpdates();
            for (Polyline p : losLines) mapView.getOverlays().remove(p);
            losLines.clear();
            if (sitesLoaded && !SiteCache.get().hasCachedData()) sitesLoaded = false;
            if (!sitesLoaded) {
                loadSites();
            } else if (hereLocation != null) {
                drawLoS();
            }
            mapView.invalidate();
        } else if (hidden) {
            stopLocationUpdates();
        }
    }

    // ── Location ─────────────────────────────────────────────────────────────

    private void startLocationUpdates() {
        if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) {
            if (statusText != null) statusText.setText("Location permission needed");
            return;
        }
        if (locationCallback != null) return;

        locationCallback = new LocationCallback() {
            @Override public void onLocationResult(@NonNull LocationResult result) {
                Location loc = result.getLastLocation();
                if (loc == null) return;
                boolean first = (hereLocation == null);
                hereLocation = loc;
                mainHandler.post(() -> onLocationUpdate(loc, first));
            }
        };

        LocationRequest req = new LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 10000)
                .setMinUpdateDistanceMeters(100).build();
        locationClient.requestLocationUpdates(req, locationCallback, Looper.getMainLooper());
        locationClient.getLastLocation().addOnSuccessListener(loc -> {
            if (loc != null && hereLocation == null) {
                hereLocation = loc;
                onLocationUpdate(loc, true);
            }
        });
    }

    private void stopLocationUpdates() {
        if (locationCallback != null) {
            locationClient.removeLocationUpdates(locationCallback);
            locationCallback = null;
        }
    }

    private void onLocationUpdate(Location loc, boolean first) {
        GeoPoint gp = new GeoPoint(loc.getLatitude(), loc.getLongitude());
        if (hereMarker == null) {
            hereMarker = dotMarker(Color.rgb(59, 130, 246));
            hereMarker.setTitle("Here");
            mapView.getOverlays().add(0, hereMarker);
        }
        hereMarker.setPosition(gp);
        if (first) {
            mapView.getController().animateTo(gp);
            mapView.getController().setZoom(13.0);
            statusText.setVisibility(View.GONE);
            if (sitesLoaded) drawLoS();
        } else if (terrainEngine.hasTiles() && sitesLoaded && !Double.isNaN(lastLoSLat)) {
            float[] dist = new float[1];
            Location.distanceBetween(lastLoSLat, lastLoSLon, loc.getLatitude(), loc.getLongitude(), dist);
            if (dist[0] >= 50f) drawLoS();
        }
        mapView.invalidate();
    }

    // ── Sites ────────────────────────────────────────────────────────────────

    private void loadSites() {
        progressBar.setVisibility(View.VISIBLE);
        SiteCache.get().getSites(new ApiService.SitesCallback() {
            @Override public void onResult(List<Site> sites) {
                mainHandler.post(() -> {
                    allSites = sites;
                    sitesLoaded = true;
                    for (Marker old : siteMarkers) mapView.getOverlays().remove(old);
                    siteMarkers.clear();
                    for (Site s : allSites) {
                        if (s.lat == 0 && s.lon == 0) continue;
                        Marker m = dotMarker(s.isPin() ? Color.rgb(34, 197, 94) : Color.rgb(239, 107, 34));
                        m.setPosition(new GeoPoint(s.lat, s.lon));
                        m.setTitle(s.displayCallsign());
                        m.setSnippet(s.isPin() ? "Pin" : safe(s.name));
                        mapView.getOverlays().add(m);
                        siteMarkers.add(m);
                    }
                    progressBar.setVisibility(View.GONE);
                    if (hereLocation != null) drawLoS();
                    mapView.invalidate();
                });
            }
            @Override public void onError(String error) {
                mainHandler.post(() -> {
                    progressBar.setVisibility(View.GONE);
                    if (statusText != null) {
                        statusText.setText("Could not load sites: " + error);
                        statusText.setVisibility(View.VISIBLE);
                    }
                });
            }
        });
    }

    // ── LoS ──────────────────────────────────────────────────────────────────

    private void drawLoS() {
        for (Polyline p : losLines) mapView.getOverlays().remove(p);
        losLines.clear();

        double lat1 = hereLocation.getLatitude(), lon1 = hereLocation.getLongitude();
        lastLoSLat = lat1;
        lastLoSLon = lon1;

        double losMaxM = SettingsFragment.getLosMaxKm(requireContext()) * 1000.0;
        String filterSite = SettingsFragment.getFilterSite(requireContext());
        List<Site> targets = new ArrayList<>();
        for (Site s : allSites) {
            if (s.lat == 0 && s.lon == 0) continue;
            if (!filterSite.isEmpty()) {
                // Single-site mode: include only the selected site, no range limit
                if (filterSite.equals(s.displayCallsign())) targets.add(s);
            } else {
                if (TerrainEngine.haversineM(lat1, lon1, s.lat, s.lon) <= losMaxM) targets.add(s);
            }
        }
        if (targets.isEmpty() || hereLocation == null) return;

        if (terrainEngine.hasTiles()) {
            drawLoSLocal(targets, lat1, lon1);
        } else {
            drawLoSRemote(targets, lat1, lon1);
        }
    }

    private void drawLoSLocal(List<Site> targets, double lat1, double lon1) {
        progressBar.setVisibility(View.VISIBLE);
        double elev1 = terrainEngine.elevationAt(lat1, lon1);
        double h1 = (Double.isNaN(elev1) ? 0 : elev1) + SettingsFragment.getAntennaHeightM(requireContext());

        bgExecutor.execute(() -> {
            for (Site s : targets) {
                double elev2 = terrainEngine.elevationAt(s.lat, s.lon);
                double h2 = (Double.isNaN(elev2) ? 0 : elev2) + s.qnf;
                TerrainEngine.LoSResult r = terrainEngine.losCheck(lat1, lon1, h1, s.lat, s.lon, h2);
                mainHandler.post(() -> {
                    if (mapView == null) return;
                    GeoPoint from = new GeoPoint(lat1, lon1);
                    GeoPoint to   = new GeoPoint(s.lat, s.lon);
                    if (r.clear) {
                        addLine(from, to, Color.rgb(34, 197, 94), 0.85f);
                    } else if (r.obsLat != 0 || r.obsLon != 0) {
                        GeoPoint obs = new GeoPoint(r.obsLat, r.obsLon);
                        addLine(from, obs, Color.rgb(239, 68, 68), 0.85f);
                        addLine(obs, to, Color.DKGRAY, 0.35f);
                    }
                    mapView.invalidate();
                });
            }
            mainHandler.post(() -> { if (mapView != null) progressBar.setVisibility(View.GONE); });
        });
    }

    private void drawLoSRemote(List<Site> targets, double lat1, double lon1) {
        progressBar.setVisibility(View.VISIBLE);
        AtomicInteger remaining = new AtomicInteger(targets.size());
        for (Site s : targets) {
            api.losCheck(lat1, lon1, s.lat, s.lon, SettingsFragment.getAntennaHeightM(requireContext()), s.qnf,
                    new ApiService.LoSCallback() {
                        @Override public void onResult(ApiService.LoSResult r) {
                            mainHandler.post(() -> {
                                if (mapView == null) return;
                                GeoPoint from = new GeoPoint(lat1, lon1);
                                GeoPoint to   = new GeoPoint(s.lat, s.lon);
                                if (r.clear) {
                                    addLine(from, to, Color.rgb(34, 197, 94), 0.85f);
                                } else if (r.obsLat != 0 || r.obsLon != 0) {
                                    GeoPoint obs = new GeoPoint(r.obsLat, r.obsLon);
                                    addLine(from, obs, Color.rgb(239, 68, 68), 0.85f);
                                    addLine(obs, to, Color.DKGRAY, 0.35f);
                                }
                                mapView.invalidate();
                                if (remaining.decrementAndGet() == 0)
                                    progressBar.setVisibility(View.GONE);
                            });
                        }
                        @Override public void onError(String error) {
                            if (remaining.decrementAndGet() == 0)
                                mainHandler.post(() -> { if (mapView != null) progressBar.setVisibility(View.GONE); });
                        }
                    });
        }
    }

    // ── Terrain download ──────────────────────────────────────────────────────

    private void downloadTerrain() {
        if (hereLocation == null) {
            statusText.setText("Waiting for GPS fix before download");
            statusText.setVisibility(View.VISIBLE);
            return;
        }
        terrainStore.clearTiles();
        downloadBtn.setEnabled(false);
        statusText.setText("Fetching tile list…");
        statusText.setVisibility(View.VISIBLE);
        progressBar.setVisibility(View.VISIBLE);

        api.getTerrainTileList(hereLocation.getLatitude(), hereLocation.getLongitude(),
                DOWNLOAD_RADIUS_KM, new ApiService.TileListCallback() {
                    @Override public void onResult(List<String> codes) {
                        List<String> needed = new ArrayList<>();
                        for (String c : codes) if (!terrainStore.hasTile(c)) needed.add(c);
                        mainHandler.post(() -> {
                            if (needed.isEmpty()) {
                                statusText.setText("Terrain already downloaded ✓");
                                progressBar.setVisibility(View.GONE);
                                downloadBtn.setEnabled(true);
                                updateDownloadBtn();
                                return;
                            }
                            downloadTiles(needed);
                        });
                    }
                    @Override public void onError(String error) {
                        mainHandler.post(() -> {
                            statusText.setText("Could not fetch tile list: " + error);
                            progressBar.setVisibility(View.GONE);
                            downloadBtn.setEnabled(true);
                        });
                    }
                });
    }

    private void downloadTiles(List<String> codes) {
        int total = codes.size();
        AtomicInteger done = new AtomicInteger(0);
        AtomicInteger failed = new AtomicInteger(0);

        for (String code : codes) {
            api.getTerrainTile(code, new ApiService.TileDataCallback() {
                @Override public void onResult(byte[] data) {
                    try {
                        terrainStore.saveTile(code, data);
                    } catch (IOException e) {
                        failed.incrementAndGet();
                    }
                    int n = done.incrementAndGet();
                    mainHandler.post(() -> {
                        statusText.setText("Downloaded " + n + " / " + total + " tiles");
                        if (n == total) onDownloadComplete(failed.get());
                    });
                }
                @Override public void onError(String error) {
                    failed.incrementAndGet();
                    int n = done.incrementAndGet();
                    mainHandler.post(() -> {
                        statusText.setText("Downloaded " + n + " / " + total + " tiles");
                        if (n == total) onDownloadComplete(failed.get());
                    });
                }
            });
        }
    }

    private void onDownloadComplete(int failed) {
        progressBar.setVisibility(View.GONE);
        int count = terrainStore.tileCount();
        long mb = terrainStore.totalSizeBytes() / (1024 * 1024);
        String msg = count + " tiles on device (" + mb + " MB)";
        if (failed > 0) msg += " — " + failed + " failed";
        statusText.setText(msg);
        mainHandler.postDelayed(() -> statusText.setVisibility(View.GONE), 4000);
        updateDownloadBtn();
        downloadBtn.setEnabled(true);
        if (hereLocation != null && sitesLoaded) drawLoS();
    }

    private void updateDownloadBtn() {
        if (downloadBtn == null) return;
        downloadBtn.setVisibility(View.VISIBLE);
        if (terrainStore.tileCount() > 0) {
            int count = terrainStore.tileCount();
            long mb = terrainStore.totalSizeBytes() / (1024 * 1024);
            downloadBtn.setText(count + " tiles (" + mb + " MB) — tap to re-download");
        } else {
            downloadBtn.setText("Tap to download terrain for offline LoS");
        }
    }

    // ── Drawing helpers ───────────────────────────────────────────────────────

    private void addLine(GeoPoint a, GeoPoint b, int color, float alpha) {
        Polyline line = new Polyline();
        List<GeoPoint> pts = new ArrayList<>();
        pts.add(a); pts.add(b);
        line.setPoints(pts);
        line.getOutlinePaint().setColor(color);
        line.getOutlinePaint().setAlpha((int) (alpha * 255));
        line.getOutlinePaint().setStrokeWidth(3f);
        mapView.getOverlays().add(line);
        losLines.add(line);
    }

    private Marker dotMarker(int color) {
        float dp = getResources().getDisplayMetrics().density;
        int px = (int) (12 * dp);
        Bitmap bmp = Bitmap.createBitmap(px, px, Bitmap.Config.ARGB_8888);
        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        paint.setColor(color);
        new Canvas(bmp).drawCircle(px / 2f, px / 2f, px / 2f, paint);
        Marker m = new Marker(mapView);
        m.setIcon(new BitmapDrawable(getResources(), bmp));
        m.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER);
        return m;
    }

    private static String safe(String s) { return s == null ? "" : s; }
}
