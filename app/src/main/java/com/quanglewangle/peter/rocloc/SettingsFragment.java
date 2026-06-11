package com.quanglewangle.peter.rocloc;

import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Spinner;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.preference.PreferenceManager;

import com.google.android.material.slider.Slider;
import com.quanglewangle.peter.rocloc.api.ApiService;
import com.quanglewangle.peter.rocloc.data.Site;

import java.util.ArrayList;
import java.util.List;

public class SettingsFragment extends Fragment {

    public static final String PREF_LOS_MAX_KM       = "pref_los_max_km";
    public static final String PREF_ANTENNA_HEIGHT_M  = "pref_antenna_height_m";
    public static final String PREF_FILTER_SITE       = "pref_filter_site";

    public static int getLosMaxKm(android.content.Context ctx) {
        return PreferenceManager.getDefaultSharedPreferences(ctx).getInt(PREF_LOS_MAX_KM, 50);
    }

    public static float getAntennaHeightM(android.content.Context ctx) {
        return PreferenceManager.getDefaultSharedPreferences(ctx).getFloat(PREF_ANTENNA_HEIGHT_M, 2f);
    }

    public static String getFilterSite(android.content.Context ctx) {
        return PreferenceManager.getDefaultSharedPreferences(ctx).getString(PREF_FILTER_SITE, "");
    }

    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    @Nullable @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_settings, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(requireContext());

        Slider losSlider      = view.findViewById(R.id.losRangeSlider);
        TextView losLabel     = view.findViewById(R.id.losRangeLabel);
        Slider antennaSlider  = view.findViewById(R.id.antennaHeightSlider);
        TextView antennaLabel = view.findViewById(R.id.antennaHeightLabel);
        Spinner siteSpinner   = view.findViewById(R.id.siteFilterSpinner);

        losSlider.setValue(prefs.getInt(PREF_LOS_MAX_KM, 50));
        antennaSlider.setValue(prefs.getFloat(PREF_ANTENNA_HEIGHT_M, 2f));
        losLabel.setText(prefs.getInt(PREF_LOS_MAX_KM, 50) + " km");
        antennaLabel.setText((int) prefs.getFloat(PREF_ANTENNA_HEIGHT_M, 2f) + " m");

        losSlider.addOnChangeListener((slider, value, fromUser) -> {
            if (fromUser) {
                prefs.edit().putInt(PREF_LOS_MAX_KM, (int) value).apply();
                losLabel.setText((int) value + " km");
            }
        });

        antennaSlider.addOnChangeListener((slider, value, fromUser) -> {
            if (fromUser) {
                prefs.edit().putFloat(PREF_ANTENNA_HEIGHT_M, value).apply();
                antennaLabel.setText((int) value + " m");
            }
        });

        // Site filter spinner
        List<String> labels = new ArrayList<>();
        List<String> values = new ArrayList<>();
        labels.add("All sites");
        values.add("");
        String savedFilter = prefs.getString(PREF_FILTER_SITE, "");

        ArrayAdapter<String> adapter = new ArrayAdapter<>(requireContext(),
                android.R.layout.simple_spinner_item, labels);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        siteSpinner.setAdapter(adapter);

        new ApiService().getSites(new ApiService.SitesCallback() {
            @Override public void onResult(List<Site> sites) {
                mainHandler.post(() -> {
                    if (getContext() == null) return;
                    for (Site s : sites) {
                        if (s.lat == 0 && s.lon == 0) continue;
                        String display = s.displayCallsign();
                        String label = display;
                        if (s.name != null && !s.name.isEmpty() && !s.name.equals(s.call_sign)) {
                            label = label + " - " + s.name;
                        }
                        labels.add(label);
                        values.add(display);
                    }
                    adapter.notifyDataSetChanged();
                    int sel = values.indexOf(savedFilter);
                    if (sel >= 0) siteSpinner.setSelection(sel);
                    siteSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
                        @Override public void onItemSelected(AdapterView<?> parent, View v, int pos, long id) {
                            prefs.edit().putString(PREF_FILTER_SITE, values.get(pos)).apply();
                        }
                        @Override public void onNothingSelected(AdapterView<?> parent) {}
                    });
                });
            }
            @Override public void onError(String error) { /* spinner stays as "All sites" */ }
        });
    }
}
