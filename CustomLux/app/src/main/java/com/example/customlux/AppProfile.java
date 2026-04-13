package com.example.customlux;

/**
 * Model class for per-app brightness settings.
 */
public class AppProfile {
    private final String packageName;
    private final String appName;
    private int brightnessOffset; // Percentage offset (-100 to 100)
    private boolean isEnabled;

    public AppProfile(String packageName, String appName, int brightnessOffset, boolean isEnabled) {
        this.packageName = packageName;
        this.appName = appName;
        this.brightnessOffset = brightnessOffset;
        this.isEnabled = isEnabled;
    }

    // Getters
    public String getPackageName() { return packageName; }
    public String getAppName() { return appName; }
    public int getBrightnessOffset() { return brightnessOffset; }
    public boolean isEnabled() { return isEnabled; }

    // Setters
    public void setBrightnessOffset(int brightnessOffset) { this.brightnessOffset = brightnessOffset; }
    public void setEnabled(boolean enabled) { isEnabled = enabled; }
}
