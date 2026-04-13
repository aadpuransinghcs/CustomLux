package com.example.customlux;

import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.media.projection.MediaProjectionManager;
import android.os.Bundle;
import android.view.MenuItem;
import android.view.View;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;

import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.android.material.floatingactionbutton.FloatingActionButton;

/**
 * Main Activity that hosts the Dashboard and Curve Editor fragments.
 */
public class MainActivity extends AppCompatActivity implements AppSelectionDialogFragment.OnAppSelectedListener {

    private static final int REQUEST_MEDIA_PROJECTION = 1001;
    private DatabaseHelper dbHelper;
    private BottomNavigationView bottomNav;
    private FloatingActionButton fab;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        dbHelper = new DatabaseHelper(this);
        bottomNav = findViewById(R.id.bottom_navigation);
        fab = findViewById(R.id.fab_add_profile);

        // Handle navigation between fragments with unsaved changes check
        bottomNav.setOnNavigationItemSelectedListener(item -> {
            int id = item.getItemId();

            // Check if we are already on the selected fragment to prevent reloading
            if (bottomNav.getSelectedItemId() == id) {
                return false;
            }

            boolean isCurveDisabled = getSharedPreferences("CustomLuxPrefs", MODE_PRIVATE)
                    .getBoolean("disable_curve_editor", false);

            // Prevent entering the curve editor if it's disabled
            if (id == R.id.navigation_curve && isCurveDisabled) {
                Toast.makeText(this, "Please Re-enable Curve Editor", Toast.LENGTH_SHORT).show();
                return false; // Intercept click
            }

            Fragment currentFragment = getSupportFragmentManager().findFragmentById(R.id.fragment_container);

            if (currentFragment instanceof CurveEditorFragment && id == R.id.navigation_dashboard) {
                CurveEditorFragment curveFragment = (CurveEditorFragment) currentFragment;
                if (curveFragment.hasUnsavedChanges()) {
                    showUnsavedChangesDialog(curveFragment);
                    return false; 
                }
            }

            switchFragment(id);
            return true;
        });

        // Open app selection dialog
        fab.setOnClickListener(v -> {
            if (getSupportFragmentManager().findFragmentByTag("AppSelection") == null) {
                AppSelectionDialogFragment dialog = new AppSelectionDialogFragment();
                dialog.show(getSupportFragmentManager(), "AppSelection");
            }
        });

        if (savedInstanceState == null) {
            switchFragment(R.id.navigation_dashboard);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        updateCurveEditorMenuAppearance();
    }

    /**
     * Swaps the visible fragment based on navigation selection.
     */
    private void switchFragment(int itemId) {
        Fragment selectedFragment = null;
        if (itemId == R.id.navigation_dashboard) {
            selectedFragment = new DashboardFragment();
            fab.show();
        } else if (itemId == R.id.navigation_curve) {
            selectedFragment = new CurveEditorFragment();
            fab.hide();
        }

        if (selectedFragment != null) {
            getSupportFragmentManager().beginTransaction()
                    .replace(R.id.fragment_container, selectedFragment)
                    .commit();
        }
    }

    /**
     * Shows a confirmation dialog before discarding unsaved curve changes.
     */
    private void showUnsavedChangesDialog(CurveEditorFragment fragment) {
        new AlertDialog.Builder(this)
                .setTitle("Unsaved Changes")
                .setMessage("Would you like to save your changes to the brightness curve?")
                .setPositiveButton("Save", (dialog, which) -> {
                    fragment.saveChanges();
                    bottomNav.setSelectedItemId(R.id.navigation_dashboard);
                })
                .setNegativeButton("Discard", (dialog, which) -> {
                    fragment.discardChanges();
                    bottomNav.setSelectedItemId(R.id.navigation_dashboard);
                })
                .setNeutralButton("Cancel", null)
                .show();
    }

    public void updateCurveEditorMenuAppearance() {
        boolean isCurveDisabled = getSharedPreferences("CustomLuxPrefs", MODE_PRIVATE)
                .getBoolean("disable_curve_editor", false);

        // Find the actual VIEW of the menu item (not the MenuItem object)
        View curveItemView = findViewById(R.id.navigation_curve);

        if (curveItemView != null) {
            // 0.3f makes it look significantly grayed out
            curveItemView.setAlpha(isCurveDisabled ? 0.3f : 1.0f);
            // keep it enabled, just greyed out
            curveItemView.setEnabled(true);
        }
    }

    /**
     * Triggers the system media projection permission request.
     */
    public void requestMediaProjection() {
        MediaProjectionManager mpm = (MediaProjectionManager) getSystemService(Context.MEDIA_PROJECTION_SERVICE);
        startActivityForResult(mpm.createScreenCaptureIntent(), REQUEST_MEDIA_PROJECTION);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_MEDIA_PROJECTION) {
            if (resultCode == RESULT_OK && data != null) {
                BrightnessService.setProjectionIntent(data);
                // Refresh service to apply new projection intent
                if (getSharedPreferences("CustomLuxPrefs", MODE_PRIVATE).getBoolean("service_enabled", false)) {
                    Intent serviceIntent = new Intent(this, BrightnessService.class);
                    startService(serviceIntent);
                }
            } else {
                Toast.makeText(this, "Screen capture permission denied", Toast.LENGTH_SHORT).show();
                getSharedPreferences("CustomLuxPrefs", MODE_PRIVATE).edit().putBoolean("white_level_comp", false).apply();
            }
        }
    }

    @Override
    public void onAppSelected(String packageName, String appName) {
        dbHelper.addProfile(new AppProfile(packageName, appName, 0, true));
        Fragment fragment = getSupportFragmentManager().findFragmentById(R.id.fragment_container);
        if (fragment instanceof DashboardFragment) {
            ((DashboardFragment) fragment).refreshAppProfiles();
        }
    }
}
