package com.example.customlux;

public class AppProfile {
    private String packageName;
    private String appName;
    private int brightnessOffset; // 0-100 percentage
    private boolean isEnabled;

    public AppProfile(String packageName, String appName, int brightnessOffset, boolean isEnabled) {
        this.packageName = packageName;
        this.appName = appName;
        this.brightnessOffset = brightnessOffset;
        this.isEnabled = isEnabled;
    }

    public String getPackageName() { return packageName; }
    public String getAppName() { return appName; }
    public int getBrightnessOffset() { return brightnessOffset; }
    public boolean isEnabled() { return isEnabled; }

    public void setBrightnessOffset(int brightnessOffset) { this.brightnessOffset = brightnessOffset; }
    public void setEnabled(boolean enabled) { isEnabled = enabled; }
}
