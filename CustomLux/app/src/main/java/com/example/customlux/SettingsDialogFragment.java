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

public class SettingsDialogFragment extends DialogFragment {

    private SharedPreferences prefs;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.dialog_settings, container, false);

        prefs = getContext().getSharedPreferences("CustomLuxPrefs", Context.MODE_PRIVATE);

        SwitchCompat switchWhiteLevel = view.findViewById(R.id.switch_white_level);
        SwitchCompat switchHbm = view.findViewById(R.id.switch_hbm);
        Button btnClose = view.findViewById(R.id.btn_close_settings);

        switchWhiteLevel.setChecked(prefs.getBoolean("white_level_comp", false));
        switchHbm.setChecked(prefs.getBoolean("hbm_handover", false));

        switchWhiteLevel.setOnCheckedChangeListener((buttonView, isChecked) -> {
            prefs.edit().putBoolean("white_level_comp", isChecked).apply();
            if (isChecked && getActivity() instanceof MainActivity) {
                ((MainActivity) getActivity()).requestMediaProjection();
            }
        });

        switchHbm.setOnCheckedChangeListener((buttonView, isChecked) -> 
                prefs.edit().putBoolean("hbm_handover", isChecked).apply());

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
