package com.example.customlux;

import android.app.AlertDialog;
import android.app.AppOpsManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.SwitchCompat;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;

import java.util.List;

public class DashboardFragment extends Fragment {

    private TextView luxDisplay;
    private TextView brightnessDisplay;
    private SwitchCompat serviceSwitch;
    private LinearLayout appProfilesContainer;
    private DatabaseHelper dbHelper;
    private SharedPreferences prefs;

    private BroadcastReceiver updateReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if ("lux_update".equals(intent.getAction())) {
                float lux = intent.getFloatExtra("lux_value", 0);
                int brightness = intent.getIntExtra("brightness_value", 0);
                if (luxDisplay != null) luxDisplay.setText("Ambient: " + (int)lux + " Lux");
                if (brightnessDisplay != null) brightnessDisplay.setText("Brightness: " + brightness + "%");
            }
        }
    };

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_dashboard, container, false);

        luxDisplay = view.findViewById(R.id.lux_display);
        brightnessDisplay = view.findViewById(R.id.brightness_display);
        serviceSwitch = view.findViewById(R.id.service_switch);
        appProfilesContainer = view.findViewById(R.id.app_profiles_container);

        dbHelper = new DatabaseHelper(getContext());
        prefs = getContext().getSharedPreferences("CustomLuxPrefs", Context.MODE_PRIVATE);

        serviceSwitch.setChecked(prefs.getBoolean("service_enabled", false));
        serviceSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (isChecked) {
                if (checkPermissions()) {
                    startService();
                } else {
                    serviceSwitch.setChecked(false);
                }
            } else {
                stopService();
            }
        });

        refreshAppProfiles();

        return view;
    }

    private void startService() {
        prefs.edit().putBoolean("service_enabled", true).apply();
        Intent intent = new Intent(getContext(), BrightnessService.class);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            getContext().startForegroundService(intent);
        } else {
            getContext().startService(intent);
        }
    }

    private void stopService() {
        prefs.edit().putBoolean("service_enabled", false).apply();
        Intent intent = new Intent(getContext(), BrightnessService.class);
        getContext().stopService(intent);
        if (luxDisplay != null) luxDisplay.setText("Ambient: -- Lux");
        if (brightnessDisplay != null) brightnessDisplay.setText("Brightness: --");
    }

    private boolean checkPermissions() {
        boolean writeSettings = Settings.System.canWrite(getContext());
        
        AppOpsManager appOps = (AppOpsManager) getContext().getSystemService(Context.APP_OPS_SERVICE);
        int mode = appOps.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS,
                android.os.Process.myUid(), getContext().getPackageName());
        boolean usageStats = (mode == AppOpsManager.MODE_ALLOWED);

        boolean notificationPerm = true;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            notificationPerm = ContextCompat.checkSelfPermission(getContext(), android.Manifest.permission.POST_NOTIFICATIONS) 
                    == android.content.pm.PackageManager.PERMISSION_GRANTED;
        }

        if (!writeSettings || !usageStats || !notificationPerm) {
            showPermissionDialog(writeSettings, usageStats, notificationPerm);
            return false;
        }
        return true;
    }

    private void showPermissionDialog(boolean writeSettings, boolean usageStats, boolean notificationPerm) {
        StringBuilder message = new StringBuilder("CustomLux requires the following permissions to function:\n");
        if (!writeSettings) message.append("\n- Modify System Settings (to change brightness)");
        if (!usageStats) message.append("\n- Usage Access (to detect foreground apps)");
        if (!notificationPerm) message.append("\n- Notifications (required for the background service)");

        new AlertDialog.Builder(getContext())
                .setTitle("Permissions Required")
                .setMessage(message.toString())
                .setPositiveButton("Go to Settings", (dialog, which) -> {
                    if (!writeSettings) {
                        Intent intent = new Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS);
                        intent.setData(Uri.parse("package:" + getContext().getPackageName()));
                        startActivity(intent);
                    } else if (!usageStats) {
                        Intent intent = new Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS);
                        startActivity(intent);
                    } else if (!notificationPerm) {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            requestPermissions(new String[]{android.Manifest.permission.POST_NOTIFICATIONS}, 101);
                        }
                    }
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    public void refreshAppProfiles() {
        if (appProfilesContainer == null) return;
        appProfilesContainer.removeAllViews();
        List<AppProfile> profiles = dbHelper.getAllProfiles();
        for (AppProfile profile : profiles) {
            View itemView = getLayoutInflater().inflate(R.layout.item_app_profile, appProfilesContainer, false);
            
            TextView nameText = itemView.findViewById(R.id.app_name);
            TextView brightnessLabel = itemView.findViewById(R.id.brightness_label);
            SeekBar seekBar = itemView.findViewById(R.id.brightness_seekbar);
            SwitchCompat appSwitch = itemView.findViewById(R.id.app_switch);
            ImageView deleteBtn = itemView.findViewById(R.id.btn_delete);

            nameText.setText(profile.getAppName());
            
            int offset = profile.getBrightnessOffset();
            brightnessLabel.setText((offset >= 0 ? "+" : "") + offset + "%");
            seekBar.setProgress(offset + 100);
            appSwitch.setChecked(profile.isEnabled());

            seekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
                @Override
                public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                    if (fromUser) {
                        int realOffset = progress - 100;
                        brightnessLabel.setText((realOffset >= 0 ? "+" : "") + realOffset + "%");
                        profile.setBrightnessOffset(realOffset);
                        dbHelper.updateProfile(profile);
                    }
                }
                @Override public void onStartTrackingTouch(SeekBar seekBar) {}
                @Override public void onStopTrackingTouch(SeekBar seekBar) {}
            });

            appSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> {
                profile.setEnabled(isChecked);
                dbHelper.updateProfile(profile);
            });

            deleteBtn.setOnClickListener(v -> {
                dbHelper.deleteProfile(profile.getPackageName());
                refreshAppProfiles();
            });

            appProfilesContainer.addView(itemView);
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        if (getContext() != null) {
            IntentFilter filter = new IntentFilter("lux_update");
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                getContext().registerReceiver(updateReceiver, filter, Context.RECEIVER_NOT_EXPORTED);
            } else {
                getContext().registerReceiver(updateReceiver, filter);
            }
        }
        refreshAppProfiles();
    }

    @Override
    public void onPause() {
        super.onPause();
        if (getContext() != null) {
            getContext().unregisterReceiver(updateReceiver);
        }
    }
}
