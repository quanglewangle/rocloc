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
import com.quanglewangle.peter.rocloc.data.Repository;
import com.quanglewangle.peter.rocloc.databinding.ActivityDetailBinding;
import com.quanglewangle.peter.rocloc.location.HereCalculator;

public class DetailActivity extends AppCompatActivity {

    public static final String EXTRA_CALLSIGN  = "callsign";
    public static final String EXTRA_HERE_ONLY = "here_only";
    private static final int   LOC_REQ         = 1001;

    private ActivityDetailBinding binding;
    private Repository repository;
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

        mainHandler   = new Handler(Looper.getMainLooper());
        repository    = new Repository(this);
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

        if (target != null) {
            if (target.lat != 0 || target.lon != 0) {
                double dist    = HereCalculator.distanceKm(myLat, myLon, target.lat, target.lon);
                double bearing = HereCalculator.bearingDeg(myLat, myLon, target.lat, target.lon);
                String compass = HereCalculator.compassPoint(bearing);

                sb.append("\nTo ").append(target.callsign).append(" (").append(safe(target.grid)).append(")\n");
                sb.append(String.format("  Distance : %.1f km%s\n",
                        dist, dist < 1 ? " (" + (int)(dist * 1000) + " m)" : ""));
                sb.append(String.format("  Bearing  : %.1f° T  (%s)\n", bearing, compass));

                // Geometric radio horizon (no terrain data — informational only)
                double h1 = 10.0; // assume 10 m antenna height
                double h2 = 10.0;
                double horizon = HereCalculator.radioHorizonKm(h1) + HereCalculator.radioHorizonKm(h2);
                sb.append(String.format("  Horizon  : ~%.0f km @ 10 m antennas\n", horizon));
                sb.append(dist <= horizon ? "  LoS likely (terrain not checked)\n"
                                          : "  Beyond radio horizon\n");
            } else {
                sb.append("\nNo coordinates stored for ").append(target.callsign)
                  .append("\n(server did not return lat/lon)");
            }
        }

        binding.hereSection.setText(sb.toString());
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
