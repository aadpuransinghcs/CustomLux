package com.example.customlux;

import android.app.Dialog;
import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.DialogFragment;

import java.util.List;

public class AppSelectionDialogFragment extends DialogFragment {

    public interface OnAppSelectedListener {
        void onAppSelected(String packageName, String appName);
    }

    private OnAppSelectedListener listener;

    @Override
    public void onAttach(@NonNull Context context) {
        super.onAttach(context);
        if (context instanceof OnAppSelectedListener) {
            listener = (OnAppSelectedListener) context;
        }
    }

    public void setOnAppSelectedListener(OnAppSelectedListener listener) {
        this.listener = listener;
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        ScrollView scrollView = new ScrollView(getContext());
        scrollView.setLayoutParams(new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        
        LinearLayout layout = new LinearLayout(getContext());
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setLayoutParams(new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        
        PackageManager pm = getContext().getPackageManager();
        List<ApplicationInfo> apps = pm.getInstalledApplications(PackageManager.GET_META_DATA);

        for (ApplicationInfo appInfo : apps) {
            if (pm.getLaunchIntentForPackage(appInfo.packageName) != null) {
                String label = appInfo.loadLabel(pm).toString();
                TextView tv = new TextView(getContext());
                tv.setText(label);
                tv.setPadding(48, 48, 48, 48);
                tv.setTextSize(18);
                tv.setBackgroundResource(android.R.drawable.list_selector_background);
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
        Dialog dialog = getDialog();
        if (dialog != null) {
            dialog.getWindow().setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
        }
    }
}
