package com.quanglewangle.peter.rocloc;

import android.Manifest;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.location.Location;
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

import org.osmdroid.config.Configuration;
import org.osmdroid.tileprovider.tilesource.TileSourceFactory;
import org.osmdroid.util.GeoPoint;
import org.osmdroid.views.MapView;
import org.osmdroid.views.overlay.Marker;
import org.osmdroid.views.overlay.Polyline;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

public class HereFragment extends Fragment {

    private MapView mapView;
    private ProgressBar progressBar;
    private TextView statusText;
    private final ApiService api = new ApiService();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    private FusedLocationProviderClient locationClient;
    private LocationCallback locationCallback;
    private Location hereLocation;
    private Marker hereMarker;
    private List<Site> allSites = new ArrayList<>();
    private final List<Polyline> losLines = new ArrayList<>();
    private boolean sitesLoaded = false;

    @Nullable @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        Configuration.getInstance().load(requireContext(),
                requireContext().getSharedPreferences("osmdroid", 0));
        Configuration.getInstance().setUserAgentValue(requireContext().getPackageName());
        return inflater.inflate(R.layout.fragment_here, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        progressBar = view.findViewById(R.id.hereProgress);
        statusText  = view.findViewById(R.id.hereStatus);
        mapView     = view.findViewById(R.id.hereMapView);
        mapView.setTileSource(TileSourceFactory.MAPNIK);
        mapView.setMultiTouchControls(true);
        mapView.getController().setZoom(6.5);
        mapView.getController().setCenter(new GeoPoint(54.5, -3.0));

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

    @Override public void onHiddenChanged(boolean hidden) {
        super.onHiddenChanged(hidden);
        if (!hidden && mapView != null) {
            mapView.invalidate();
            startLocationUpdates();
        } else if (hidden) {
            stopLocationUpdates();
        }
    }

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
                .setMinUpdateDistanceMeters(100)
                .build();
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
            hereMarker = new Marker(mapView);
            hereMarker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER);
            hereMarker.setTitle("Here");
            hereMarker.getIcon().setTint(Color.rgb(59, 130, 246));
            mapView.getOverlays().add(0, hereMarker);
        }
        hereMarker.setPosition(gp);

        if (first) {
            mapView.getController().animateTo(gp);
            mapView.getController().setZoom(10.0);
            statusText.setVisibility(View.GONE);
            if (sitesLoaded) drawLoS();
        }
        mapView.invalidate();
    }

    private void loadSites() {
        progressBar.setVisibility(View.VISIBLE);
        api.getSites(new ApiService.SitesCallback() {
            @Override public void onResult(List<Site> sites) {
                mainHandler.post(() -> {
                    allSites = sites;
                    sitesLoaded = true;
                    for (Site s : allSites) {
                        if (s.lat == 0 && s.lon == 0) continue;
                        Marker m = new Marker(mapView);
                        m.setPosition(new GeoPoint(s.lat, s.lon));
                        m.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM);
                        m.setTitle(s.displayCallsign());
                        m.setSnippet(s.isPin() ? "Pin" : safe(s.name));
                        if (s.isPin()) m.getIcon().setTint(Color.rgb(34, 197, 94));
                        mapView.getOverlays().add(m);
                    }
                    progressBar.setVisibility(View.GONE);
                    if (hereLocation != null) drawLoS();
                    mapView.invalidate();
                });
            }
            @Override public void onError(String error) {
                mainHandler.post(() -> progressBar.setVisibility(View.GONE));
            }
        });
    }

    private void drawLoS() {
        for (Polyline p : losLines) mapView.getOverlays().remove(p);
        losLines.clear();

        List<Site> targets = new ArrayList<>();
        for (Site s : allSites) {
            if (s.lat != 0 || s.lon != 0) targets.add(s);
        }
        if (targets.isEmpty() || hereLocation == null) return;

        progressBar.setVisibility(View.VISIBLE);
        AtomicInteger remaining = new AtomicInteger(targets.size());
        double lat1 = hereLocation.getLatitude();
        double lon1 = hereLocation.getLongitude();

        for (Site s : targets) {
            api.losCheck(lat1, lon1, s.lat, s.lon, 2.0, s.qnf,
                    new ApiService.LoSCallback() {
                        @Override public void onResult(ApiService.LoSResult r) {
                            mainHandler.post(() -> {
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
                                mainHandler.post(() -> progressBar.setVisibility(View.GONE));
                        }
                    });
        }
    }

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

    private static String safe(String s) { return s == null ? "" : s; }
}
