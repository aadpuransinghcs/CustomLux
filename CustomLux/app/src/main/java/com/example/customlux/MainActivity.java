package com.example.customlux;

import android.app.AppOpsManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.provider.Settings;
import android.widget.Button;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.SwitchCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

public class MainActivity extends AppCompatActivity {

    private SharedPreferences prefs;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        SwitchCompat serviceSwitch = findViewById(R.id.service_switch);
        Button btnCurveEditor = findViewById(R.id.btn_curve_editor);

        //Use Preferences to remember if the service was left on
        prefs = getSharedPreferences("CustomLuxPrefs", MODE_PRIVATE);
        boolean isRunning = prefs.getBoolean("service_enabled", false);
        serviceSwitch.setChecked(isRunning);

        serviceSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> {
            prefs.edit().putBoolean("service_enabled", isChecked).apply();
            if (isChecked) {
                // Future Step: Start Foreground Service
                Toast.makeText(this, "Service Started", Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(this, "Service Stopped", Toast.LENGTH_SHORT).show();
            }
        });

        // Intent to move to another Activity (The Curve Editor)
        btnCurveEditor.setOnClickListener(v -> {
            Intent intent = new Intent(MainActivity.this, CurveEditorActivity.class);
            startActivity(intent);
        });
    }

    private void checkUsageStatsPermission() {
        AppOpsManager appOps = (AppOpsManager) getSystemService(Context.APP_OPS_SERVICE);
        int mode = appOps.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS,
                android.os.Process.myUid(), getPackageName());

        if (mode != AppOpsManager.MODE_ALLOWED) {
            // If not allowed, send the user to the Settings page
            // Using an Implicit Intent
            Intent intent = new Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS);
            startActivity(intent);
            Toast.makeText(this, "Please enable Usage Access for CustomLux", Toast.LENGTH_LONG).show();
        }
    }
}