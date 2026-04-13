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

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.SwitchCompat;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;

import java.util.List;

/**
 * Main dashboard view showing live sensor data and the list of per-app brightness profiles.
 */
public class DashboardFragment extends Fragment {

    private TextView luxDisplay;
    private TextView brightnessDisplay;
    private SwitchCompat serviceSwitch;
    private LinearLayout appProfilesContainer;
    private DatabaseHelper dbHelper;
    private SharedPreferences prefs;

    /**
     * Listens for brightness and lux updates from the background service.
     */
    private final BroadcastReceiver updateReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if ("lux_update".equals(intent.getAction())) {
                // If service was just disabled, ignore late incoming broadcasts to prevent UI flicker
                if (prefs != null && !prefs.getBoolean("service_enabled", false)) {
                    return;
                }

                float lux = intent.getFloatExtra("lux_value", 0);
                int brightness = intent.getIntExtra("brightness_value", 0);
                
                // Update live readout labels
                if (luxDisplay != null) {
                    luxDisplay.setText(getString(R.string.lux_value_label, (int) lux));
                }
                if (brightnessDisplay != null) {
                    if (brightness == -1) {
                        brightnessDisplay.setText(getString(R.string.brightness_default));
                    } else {
                        brightnessDisplay.setText(getString(R.string.brightness_value_label, brightness));
                    }
                }
            }
        }
    };

    /**
     * Handles the runtime notification permission request result.
     */
    private final ActivityResultLauncher<String> requestPermissionLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestPermission(), isGranted -> {
                if (isGranted) {
                    checkPermissions();
                }
            });

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_dashboard, container, false);

        // Bind UI components
        luxDisplay = view.findViewById(R.id.lux_display);
        brightnessDisplay = view.findViewById(R.id.brightness_display);
        serviceSwitch = view.findViewById(R.id.service_switch);
        appProfilesContainer = view.findViewById(R.id.app_profiles_container);
        ImageView btnSettings = view.findViewById(R.id.btn_settings);

        Context context = getContext();
        if (context != null) {
            dbHelper = new DatabaseHelper(context);
            prefs = context.getSharedPreferences("CustomLuxPrefs", Context.MODE_PRIVATE);

            // Sync switch state with saved preference
            boolean isEnabled = prefs.getBoolean("service_enabled", false);
            serviceSwitch.setChecked(isEnabled);
            
            // Ensure labels start as default if service is currently off
            if (!isEnabled) {
                if (luxDisplay != null) luxDisplay.setText(getString(R.string.ambient_default));
                if (brightnessDisplay != null) brightnessDisplay.setText(getString(R.string.brightness_default));
            }

            serviceSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> {
                if (isChecked) {
                    if (checkPermissions()) {
                        startService();
                    } else {
                        serviceSwitch.setChecked(false); // Revert if permissions missing
                    }
                } else {
                    stopService();
                }
            });
        }

        // Open advanced settings overlay
        btnSettings.setOnClickListener(v -> {
            SettingsDialogFragment dialog = new SettingsDialogFragment();
            dialog.show(getChildFragmentManager(), "SettingsDialog");
        });

        refreshAppProfiles();

        return view;
    }

    /**
     * Starts the background monitoring service.
     */
    private void startService() {
        Context context = getContext();
        if (context == null || prefs == null) return;

        prefs.edit().putBoolean("service_enabled", true).apply();
        Intent intent = new Intent(context, BrightnessService.class);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.startForegroundService(intent);
        } else {
            context.startService(intent);
        }
    }

    /**
     * Stops the background monitoring service and resets labels.
     */
    private void stopService() {
        Context context = getContext();
        if (context == null || prefs == null) return;

        prefs.edit().putBoolean("service_enabled", false).apply();
        Intent intent = new Intent(context, BrightnessService.class);
        context.stopService(intent);
        if (luxDisplay != null) luxDisplay.setText(getString(R.string.ambient_default));
        if (brightnessDisplay != null) brightnessDisplay.setText(getString(R.string.brightness_default));
    }

    /**
     * Verifies all mandatory system permissions are granted.
     */
    private boolean checkPermissions() {
        Context context = getContext();
        if (context == null) return false;

        boolean writeSettings = Settings.System.canWrite(context);
        
        AppOpsManager appOps = (AppOpsManager) context.getSystemService(Context.APP_OPS_SERVICE);
        boolean usageStats = false;
        if (appOps != null) {
            int mode = appOps.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS,
                    android.os.Process.myUid(), context.getPackageName());
            usageStats = (mode == AppOpsManager.MODE_ALLOWED);
        }

        boolean notificationPerm = true;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            notificationPerm = ContextCompat.checkSelfPermission(context, android.Manifest.permission.POST_NOTIFICATIONS) 
                    == android.content.pm.PackageManager.PERMISSION_GRANTED;
        }

        if (!writeSettings || !usageStats || !notificationPerm) {
            showPermissionDialog(writeSettings, usageStats, notificationPerm);
            return false;
        }
        return true;
    }

    /**
     * Shows a detailed alert explaining why specific permissions are needed.
     */
    private void showPermissionDialog(boolean writeSettings, boolean usageStats, boolean notificationPerm) {
        Context context = getContext();
        if (context == null) return;

        StringBuilder message = new StringBuilder("CustomLux requires the following permissions to function:\n");
        if (!writeSettings) message.append("\n- Modify System Settings (to change brightness)");
        if (!usageStats) message.append("\n- Usage Access (to detect foreground apps)");
        if (!notificationPerm) message.append("\n- Notifications (required for the background service)");

        new AlertDialog.Builder(context)
                .setTitle("Permissions Required")
                .setMessage(message.toString())
                .setPositiveButton("Go to Settings", (dialog, which) -> {
                    if (!writeSettings) {
                        Intent intent = new Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS);
                        intent.setData(Uri.parse("package:" + context.getPackageName()));
                        startActivity(intent);
                    } else if (!usageStats) {
                        Intent intent = new Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS);
                        startActivity(intent);
                    } else if (!notificationPerm) {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            requestPermissionLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS);
                        }
                    }
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    /**
     * Renders the dynamic list of app profiles from the database.
     */
    public void refreshAppProfiles() {
        if (appProfilesContainer == null || dbHelper == null) return;
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
            
            // Format offset label (e.g. +10%)
            int offset = profile.getBrightnessOffset();
            String label = (offset >= 0 ? "+" : "") + offset + "%";
            brightnessLabel.setText(label);
            seekBar.setProgress(offset + 100);
            appSwitch.setChecked(profile.isEnabled());

            // Handle brightness offset slider interaction
            seekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
                @Override
                public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                    if (fromUser) {
                        int realOffset = progress - 100;
                        String l = (realOffset >= 0 ? "+" : "") + realOffset + "%";
                        brightnessLabel.setText(l);
                        profile.setBrightnessOffset(realOffset);
                        dbHelper.updateProfile(profile);
                        
                        // Notify the service that a setting has changed
                        Context context = getContext();
                        if (context != null) {
                            context.sendBroadcast(new Intent("settings_updated"));
                        }
                    }
                }
                @Override public void onStartTrackingTouch(SeekBar seekBar) {}
                @Override public void onStopTrackingTouch(SeekBar seekBar) {}
            });

            // Toggle individual app profile status
            appSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> {
                profile.setEnabled(isChecked);
                dbHelper.updateProfile(profile);
                
                // Notify the service that a setting has changed
                Context context = getContext();
                if (context != null) {
                    context.sendBroadcast(new Intent("settings_updated"));
                }
            });

            // Remove profile from list and database
            deleteBtn.setOnClickListener(v -> {
                dbHelper.deleteProfile(profile.getPackageName());
                refreshAppProfiles();
                
                // Notify the service that a setting has changed
                Context context = getContext();
                if (context != null) {
                    context.sendBroadcast(new Intent("settings_updated"));
                }
            });

            appProfilesContainer.addView(itemView);
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        Context context = getContext();
        if (context != null) {
            IntentFilter filter = new IntentFilter("lux_update");
            // Register receiver with appropriate export flag for Android 14+
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                context.registerReceiver(updateReceiver, filter, Context.RECEIVER_NOT_EXPORTED);
            } else {
                context.registerReceiver(updateReceiver, filter);
            }
        }
        refreshAppProfiles();
    }

    @Override
    public void onPause() {
        super.onPause();
        Context context = getContext();
        if (context != null) {
            context.unregisterReceiver(updateReceiver);
        }
    }
}
