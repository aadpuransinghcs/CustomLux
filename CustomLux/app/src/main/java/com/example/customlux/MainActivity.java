package com.example.customlux;

import android.app.AlertDialog;
import android.os.Bundle;

import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;

import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.android.material.floatingactionbutton.FloatingActionButton;

public class MainActivity extends AppCompatActivity implements AppSelectionDialogFragment.OnAppSelectedListener {

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

        bottomNav.setOnNavigationItemSelectedListener(item -> {
            int id = item.getItemId();
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

        fab.setOnClickListener(v -> {
            // Fix: Prevent opening multiple dialogs if clicked quickly
            if (getSupportFragmentManager().findFragmentByTag("AppSelection") == null) {
                AppSelectionDialogFragment dialog = new AppSelectionDialogFragment();
                dialog.show(getSupportFragmentManager(), "AppSelection");
            }
        });

        if (savedInstanceState == null) {
            switchFragment(R.id.navigation_dashboard);
        }
    }

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

    @Override
    public void onAppSelected(String packageName, String appName) {
        dbHelper.addProfile(new AppProfile(packageName, appName, 0, true));
        Fragment fragment = getSupportFragmentManager().findFragmentById(R.id.fragment_container);
        if (fragment instanceof DashboardFragment) {
            ((DashboardFragment) fragment).refreshAppProfiles();
        }
    }
}
