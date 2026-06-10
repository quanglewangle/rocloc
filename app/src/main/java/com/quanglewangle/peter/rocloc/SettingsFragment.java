package com.quanglewangle.peter.rocloc;

import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.preference.PreferenceManager;

import com.google.android.material.slider.Slider;

public class SettingsFragment extends Fragment {

    public static final String PREF_ANTENNA_HEIGHT_M = "pref_antenna_height_m";

    public static float getAntennaHeightM(android.content.Context ctx) {
        return PreferenceManager.getDefaultSharedPreferences(ctx).getFloat(PREF_ANTENNA_HEIGHT_M, 2f);
    }

    @Nullable @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_settings, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(requireContext());

        Slider antennaSlider  = view.findViewById(R.id.antennaHeightSlider);
        TextView antennaLabel = view.findViewById(R.id.antennaHeightLabel);

        antennaSlider.setValue(prefs.getFloat(PREF_ANTENNA_HEIGHT_M, 2f));
        updateLabel(antennaLabel, (int) antennaSlider.getValue());

        antennaSlider.addOnChangeListener((slider, value, fromUser) -> {
            if (fromUser) {
                prefs.edit().putFloat(PREF_ANTENNA_HEIGHT_M, value).apply();
                updateLabel(antennaLabel, (int) value);
            }
        });
    }

    private void updateLabel(TextView tv, int value) {
        tv.setText(value + " m");
    }
}
