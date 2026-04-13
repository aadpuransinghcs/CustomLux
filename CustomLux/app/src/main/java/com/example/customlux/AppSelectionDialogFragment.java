package com.example.customlux;

import android.app.Dialog;
import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.DialogFragment;

import java.util.List;

/**
 * Overlay dialog that allows users to select an installed app to create a brightness profile for.
 */
public class AppSelectionDialogFragment extends DialogFragment {

    /**
     * Interface to communicate the selected app back to the host activity.
     */
    public interface OnAppSelectedListener {
        void onAppSelected(String packageName, String appName);
    }

    private OnAppSelectedListener listener;

    @Override
    public void onAttach(@NonNull Context context) {
        super.onAttach(context);
        // Link the listener to the MainActivity
        if (context instanceof OnAppSelectedListener) {
            listener = (OnAppSelectedListener) context;
        }
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        Context context = getContext();
        if (context == null) return null;

        // Use a ScrollView to ensure the entire app list is accessible
        ScrollView scrollView = new ScrollView(context);
        scrollView.setLayoutParams(new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        
        LinearLayout layout = new LinearLayout(context);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setLayoutParams(new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        
        // Scan for all installed applications that have a launch intent
        PackageManager pm = context.getPackageManager();
        List<ApplicationInfo> apps = pm.getInstalledApplications(PackageManager.GET_META_DATA);

        for (ApplicationInfo appInfo : apps) {
            if (pm.getLaunchIntentForPackage(appInfo.packageName) != null) {
                String label = appInfo.loadLabel(pm).toString();
                
                // Create a simple text view for each app entry
                TextView tv = new TextView(context);
                tv.setText(label);
                tv.setPadding(48, 48, 48, 48);
                tv.setTextSize(18);
                tv.setBackgroundResource(android.R.drawable.list_selector_background);
                
                // Trigger selection and close dialog on click
                tv.setOnClickListener(v -> {
                    if (listener != null) {
                        listener.onAppSelected(appInfo.packageName, label);
                    }
                    dismiss();
                });
                layout.addView(tv);
            }
        }

        scrollView.addView(layout);
        return scrollView;
    }
    
    @Override
    public void onStart() {
        super.onStart();
        // Force the dialog to use full screen dimensions
        Dialog dialog = getDialog();
        if (dialog != null) {
            Window window = dialog.getWindow();
            if (window != null) {
                window.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
            }
        }
    }
}
