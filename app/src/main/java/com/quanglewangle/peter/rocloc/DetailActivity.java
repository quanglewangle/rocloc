package com.quanglewangle.peter.rocloc;

import android.Manifest;
import android.content.pm.PackageManager;
import android.location.Location;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.format.DateFormat;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;

import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationServices;
import com.google.android.material.snackbar.Snackbar;
import com.quanglewangle.peter.rocloc.data.OperatorEntity;
import com.quanglewangle.peter.rocloc.api.ApiService;
import com.quanglewangle.peter.rocloc.data.Repository;
import com.quanglewangle.peter.rocloc.databinding.ActivityDetailBinding;
import com.quanglewangle.peter.rocloc.location.HereCalculator;

public class DetailActivity extends AppCompatActivity {

    public static final String EXTRA_CALLSIGN  = "callsign";
    public static final String EXTRA_HERE_ONLY = "here_only";
    private static final int   LOC_REQ         = 1001;

    private ActivityDetailBinding binding;
    private Repository repository;
    private ApiService apiService;
    private FusedLocationProviderClient locationClient;
    private Handler mainHandler;
    private OperatorEntity currentEntity;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityDetailBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        setSupportActionBar(binding.toolbar);
        if (getSupportActionBar() != null) getSupportActionBar().setDisplayHomeAsUpEnabled(true);

        mainHandler    = new Handler(Looper.getMainLooper());
        repository     = new Repository(this);
        apiService     = new ApiService();
        locationClient = LocationServices.getFusedLocationProviderClient(this);

        String callsign = getIntent().getStringExtra(EXTRA_CALLSIGN);
        boolean hereOnly = getIntent().getBooleanExtra(EXTRA_HERE_ONLY, false);

        if (hereOnly) {
            setTitle("HERE");
            binding.callsignText.setText("—");
            binding.nameText.setText("My current position");
            showHereInfo(null);
        } else if (callsign != null) {
            loadOperator(callsign, false);
        }

        binding.refreshFab.setOnClickListener(v -> {
            if (currentEntity != null) loadOperator(currentEntity.callsign, true);
        });
    }

    private void loadOperator(String callsign, boolean forceRefresh) {
        binding.progressBar.setVisibility(android.view.View.VISIBLE);

        repository.lookup(callsign, forceRefresh, new Repository.LookupCallback() {
            @Override
            public void onResult(OperatorEntity entity, boolean fromCache) {
                currentEntity = entity;
                binding.progressBar.setVisibility(android.view.View.GONE);
                displayEntity(entity, fromCache);
                showHereInfo(entity);
            }

            @Override
            public void onError(String error) {
                binding.progressBar.setVisibility(android.view.View.GONE);
                Snackbar.make(binding.getRoot(), "Error: " + error, Snackbar.LENGTH_LONG).show();
            }
        }, mainHandler);
    }

    private void displayEntity(OperatorEntity e, boolean fromCache) {
        setTitle(e.callsign);
        binding.callsignText.setText(e.callsign);
        binding.nameText.setText(safe(e.name));

        // addressText → city / state
        StringBuilder location = new StringBuilder();
        if (!safe(e.city).isEmpty())  location.append(e.city);
        if (!safe(e.state).isEmpty()) { if (location.length() > 0) location.append(", "); location.append(e.state); }
        binding.addressText.setText(location.toString());

        // postcodeText → country
        binding.postcodeText.setText(safe(e.country));

        // licenseText → altitude
        if (e.altm > 0) {
            binding.licenseText.setText(String.format("Altitude: %.0f m ASL", e.altm));
        } else {
            binding.licenseText.setText("");
        }

        binding.gridText.setText("Grid: " + safe(e.grid));
        String syncInfo = fromCache
                ? "Cached: " + DateFormat.format("dd MMM yyyy HH:mm", e.lastSynced)
                : "Live from server";
        binding.syncText.setText(syncInfo);
    }

    private void showHereInfo(OperatorEntity target) {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, LOC_REQ);
            return;
        }

        locationClient.getLastLocation().addOnSuccessListener(location -> {
            if (location != null) {
                renderHere(location, target);
            } else {
                binding.hereSection.setText("GPS: no fix yet — open Maps to warm up the receiver");
            }
        }).addOnFailureListener(e ->
            binding.hereSection.setText("GPS unavailable: " + e.getMessage())
        );
    }

    private void renderHere(Location me, OperatorEntity target) {
        double myLat = me.getLatitude();
        double myLon = me.getLongitude();
        String myGrid = HereCalculator.maidenhead(myLat, myLon);

        StringBuilder sb = new StringBuilder();
        sb.append(String.format("My position\n  %s\n  %.4f°N  %.4f°E\n  Accuracy: ±%.0f m\n",
                myGrid, myLat, myLon, me.getAccuracy()));

        if (target == null || (target.lat == 0 && target.lon == 0)) {
            if (target != null) {
                sb.append("\nNo coordinates for ").append(target.callsign);
            }
            binding.hereSection.setText(sb.toString());
            return;
        }

        double dist    = HereCalculator.distanceKm(myLat, myLon, target.lat, target.lon);
        double bearing = HereCalculator.bearingDeg(myLat, myLon, target.lat, target.lon);
        String compass = HereCalculator.compassPoint(bearing);

        sb.append("\nTo ").append(target.callsign).append(" (").append(safe(target.grid)).append(")\n");
        sb.append(String.format("  Distance : %.1f km%s\n",
                dist, dist < 1 ? " (" + (int)(dist * 1000) + " m)" : ""));
        sb.append(String.format("  Bearing  : %.1f° T  (%s)\n", bearing, compass));
        sb.append("  LoS      : checking terrain…\n");
        binding.hereSection.setText(sb.toString());

        final double antH = 10.0;
        apiService.losCheck(myLat, myLon, target.lat, target.lon, antH, antH,
                new ApiService.LoSCallback() {
            @Override public void onResult(ApiService.LoSResult r) {
                String losLine;
                if (r.clear) {
                    losLine = String.format("  LoS      : CLEAR (terrain OK)\n"
                            + "  My elev  : %.0f m ASL\n"
                            + "  Target   : %.0f m ASL\n", r.myElev, r.targetElev);
                } else {
                    losLine = String.format("  LoS      : BLOCKED\n"
                            + "  My elev  : %.0f m ASL\n"
                            + "  Target   : %.0f m ASL\n"
                            + "  Obstruction near %.4f°N %.4f°E\n",
                            r.myElev, r.targetElev, r.obsLat, r.obsLon);
                }
                String updated = sb.toString().replace("  LoS      : checking terrain…\n", losLine);
                mainHandler.post(() -> binding.hereSection.setText(updated));
            }
            @Override public void onError(String error) {
                // Fall back to geometric horizon if terrain unavailable
                double horizon = HereCalculator.radioHorizonKm(antH) * 2;
                String fallback = String.format("  Horizon  : ~%.0f km (geometric, no terrain data)\n"
                        + "  (%s)\n", horizon, error);
                String updated = sb.toString().replace("  LoS      : checking terrain…\n", fallback);
                mainHandler.post(() -> binding.hereSection.setText(updated));
            }
        });
    }

    @Override
    public void onRequestPermissionsResult(int req, @NonNull String[] perms, @NonNull int[] grants) {
        super.onRequestPermissionsResult(req, perms, grants);
        if (req == LOC_REQ && grants.length > 0 && grants[0] == PackageManager.PERMISSION_GRANTED) {
            showHereInfo(currentEntity);
        } else {
            binding.hereSection.setText("Location permission denied — HERE feature unavailable");
        }
    }

    @Override
    public boolean onSupportNavigateUp() {
        onBackPressed();
        return true;
    }

    private static String safe(String s) { return s == null ? "" : s; }

}
