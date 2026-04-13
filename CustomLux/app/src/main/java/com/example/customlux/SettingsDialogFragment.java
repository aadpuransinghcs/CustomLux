package com.example.customlux;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.SwitchCompat;
import androidx.fragment.app.DialogFragment;

/**
 * Dialog fragment for managing advanced app settings like HBM and White-Level compensation.
 */
public class SettingsDialogFragment extends DialogFragment {

    private SharedPreferences prefs;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.dialog_settings, container, false);

        prefs = getContext().getSharedPreferences("CustomLuxPrefs", Context.MODE_PRIVATE);

        SwitchCompat switchWhiteLevel = view.findViewById(R.id.switch_white_level);
        SwitchCompat switchHbm = view.findViewById(R.id.switch_hbm);
        SwitchCompat switchDisableCurve = view.findViewById(R.id.switch_disable_curve);
        Button btnClose = view.findViewById(R.id.btn_close_settings);

        // Load current states
        switchWhiteLevel.setChecked(prefs.getBoolean("white_level_comp", false));
        switchHbm.setChecked(prefs.getBoolean("hbm_handover", false));
        switchDisableCurve.setChecked(prefs.getBoolean("disable_curve_editor", false));

        // Toggle Smart White-Level Compensation
        switchWhiteLevel.setOnCheckedChangeListener((buttonView, isChecked) -> {
            prefs.edit().putBoolean("white_level_comp", isChecked).apply();
            if (isChecked && getActivity() instanceof MainActivity) {
                ((MainActivity) getActivity()).requestMediaProjection();
            }
        });

        // Toggle HBM Handover
        switchHbm.setOnCheckedChangeListener((buttonView, isChecked) -> 
                prefs.edit().putBoolean("hbm_handover", isChecked).apply());

        // Toggle Disable Curve Editor
        switchDisableCurve.setOnCheckedChangeListener((buttonView, isChecked) -> {
            prefs.edit().putBoolean("disable_curve_editor", isChecked).apply();
            // Notify MainActivity to update bottom navigation state
            if (getActivity() instanceof MainActivity) {
                ((MainActivity) getActivity()).updateCurveEditorMenuAppearance();
            }
        });

        btnClose.setOnClickListener(v -> dismiss());

        return view;
    }

    @Override
    public void onStart() {
        super.onStart();
        if (getDialog() != null && getDialog().getWindow() != null) {
            getDialog().getWindow().setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        }
    }
}
