package com.example.customlux;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.app.usage.UsageEvents;
import android.app.usage.UsageStats;
import android.app.usage.UsageStatsManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.graphics.PixelFormat;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.hardware.display.DisplayManager;
import android.hardware.display.VirtualDisplay;
import android.media.Image;
import android.media.ImageReader;
import android.media.projection.MediaProjection;
import android.media.projection.MediaProjectionManager;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.provider.Settings;
import android.util.DisplayMetrics;
import android.view.WindowManager;

import java.nio.ByteBuffer;
import java.util.List;

/**
 * Foreground service that monitors ambient light and adjusts screen brightness.
 * Also handles High Brightness Mode (HBM) handover and White-Level Compensation.
 */
public class BrightnessService extends Service implements SensorEventListener {

    private SensorManager sensorManager;
    private Sensor lightSensor;
    private DatabaseHelper dbHelper;
    private SharedPreferences prefs;

    private static final String CHANNEL_ID = "CustomLuxChannel";
    private final float MAX_LUX = 10000f;
    private final float MIN_LUX = 1f;
    private final float HBM_THRESHOLD = 10000f;

    private int originalBrightnessMode = -1;
    private boolean isHbmActive = false;
    
    // Manual base tracking to avoid feedback loops when curve editor is disabled
    private int manualBaseBrightness = 128;
    private int lastAppliedBrightness = -1;
    private int lastBaseBrightness = 128;
    private float lastKnownLux = -1;

    private final BroadcastReceiver settingsReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if ("settings_updated".equals(intent.getAction())) {
                refreshBrightness();
            }
        }
    };

    // Persistent state for foreground app detection to avoid "losing" the app after a few seconds
    private String currentForegroundApp = "";
    private long lastAppCheckTime = 0;

    // MediaProjection for White-Level Compensation
    private MediaProjection mediaProjection;
    private VirtualDisplay virtualDisplay;
    private ImageReader imageReader;
    private final Handler projectionHandler = new Handler(Looper.getMainLooper());
    private static Intent projectionIntent;
    private float currentApl = 0.0f;
    private boolean isProjectionRunning = false;

    /**
     * Receives projection intent from MainActivity after user permission.
     */
    public static void setProjectionIntent(Intent intent) {
        projectionIntent = intent;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        sensorManager = (SensorManager) getSystemService(SENSOR_SERVICE);
        lightSensor = sensorManager.getDefaultSensor(Sensor.TYPE_LIGHT);
        dbHelper = new DatabaseHelper(this);
        prefs = getSharedPreferences("CustomLuxPrefs", MODE_PRIVATE);

        // Listen for setting changes from the UI
        IntentFilter filter = new IntentFilter("settings_updated");
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(settingsReceiver, filter, Context.RECEIVER_NOT_EXPORTED);
        } else {
            registerReceiver(settingsReceiver, filter);
        }

        // Initial base brightness from current system level
        manualBaseBrightness = Settings.System.getInt(getContentResolver(), Settings.System.SCREEN_BRIGHTNESS, 128);
        lastBaseBrightness = manualBaseBrightness;

        // Control system adaptive settings
        saveOriginalBrightnessMode();
        disableSystemAdaptiveBrightness();

        createNotificationChannel();

        Notification.Builder builder;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            builder = new Notification.Builder(this, CHANNEL_ID);
        } else {
            builder = new Notification.Builder(this);
        }

        builder.setContentTitle("CustomLux")
                .setContentText("Monitoring ambient light...")
                .setSmallIcon(android.R.drawable.ic_menu_compass)
                .setOngoing(true);

        startForeground(777, builder.build());
    }

    /**
     * Records the user's current brightness mode to restore it when service stops.
     */
    private void saveOriginalBrightnessMode() {
        originalBrightnessMode = Settings.System.getInt(getContentResolver(), Settings.System.SCREEN_BRIGHTNESS_MODE, 0);
    }

    /**
     * Restores the system's original brightness mode.
     */
    private void restoreOriginalBrightnessMode() {
        if (originalBrightnessMode != -1) {
            Settings.System.putInt(getContentResolver(), Settings.System.SCREEN_BRIGHTNESS_MODE, originalBrightnessMode);
        }
    }

    /**
     * Disables system adaptive brightness while CustomLux is active.
     */
    private void disableSystemAdaptiveBrightness() {
        Settings.System.putInt(getContentResolver(), Settings.System.SCREEN_BRIGHTNESS_MODE, 0); // 0 = Manual
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    "CustomLux Service Channel",
                    NotificationManager.IMPORTANCE_LOW
            );
            channel.setDescription("Background brightness monitoring");
            NotificationManager manager = getSystemService(NotificationManager.class);
            if (manager != null) {
                manager.createNotificationChannel(channel);
            }
        }
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (lightSensor != null) {
            sensorManager.registerListener(this, lightSensor, SensorManager.SENSOR_DELAY_NORMAL);
        }
        checkProjectionStatus();
        return START_STICKY;
    }

    // Toggle screen content analysis based on settings
    private void checkProjectionStatus() {
        boolean whiteLevelEnabled = prefs.getBoolean("white_level_comp", false);
        if (whiteLevelEnabled && !isProjectionRunning && projectionIntent != null) {
            startProjection();
        } else if (!whiteLevelEnabled && isProjectionRunning) {
            stopProjection();
        }
    }

    /**
     * Initializes MediaProjection to start capturing screen content.
     */
    private void startProjection() {
        MediaProjectionManager mpm = (MediaProjectionManager) getSystemService(MEDIA_PROJECTION_SERVICE);
        mediaProjection = mpm.getMediaProjection(-1, projectionIntent);
        if (mediaProjection != null) {
            isProjectionRunning = true;
            setupVirtualDisplay();
            scheduleAplCalculation();
        }
    }

    /**
     * Creates a tiny virtual display for efficient APL analysis.
     */
    private void setupVirtualDisplay() {
        DisplayMetrics metrics = new DisplayMetrics();
        WindowManager wm = (WindowManager) getSystemService(WINDOW_SERVICE);
        if (wm != null) {
            wm.getDefaultDisplay().getRealMetrics(metrics);
            // Downsample screen to 32x32 for efficient analysis
            imageReader = ImageReader.newInstance(32, 32, PixelFormat.RGBA_8888, 2);
            virtualDisplay = mediaProjection.createVirtualDisplay("APL_Capture",
                    32, 32, metrics.densityDpi,
                    DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                    imageReader.getSurface(), null, null);
        }
    }

    /**
     * Periodically schedules the next APL calculation.
     */
    private void scheduleAplCalculation() {
        if (!isProjectionRunning) return;
        projectionHandler.postDelayed(() -> {
            calculateApl();
            scheduleAplCalculation();
        }, 1000); // 1s interval to save battery
    }

    /**
     * Calculates screen brightness (APL) using Perceived Luminance formula.
     */
    private void calculateApl() {
        if (imageReader == null) return;
        Image image = imageReader.acquireLatestImage();
        if (image == null) return;

        Image.Plane[] planes = image.getPlanes();
        ByteBuffer buffer = planes[0].getBuffer();
        int pixelStride = planes[0].getPixelStride();
        int rowStride = planes[0].getRowStride();
        int width = image.getWidth();
        int height = image.getHeight();

        double totalLuminance = 0;
        int pixelCount = 0;

        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int offset = y * rowStride + x * pixelStride;
                int r = buffer.get(offset) & 0xFF;
                int g = buffer.get(offset + 1) & 0xFF;
                int b = buffer.get(offset + 2) & 0xFF;

                // Perceived Luminance: L = 0.299R + 0.587G + 0.114B
                double l = (0.299 * r + 0.587 * g + 0.114 * b);
                totalLuminance += l;
                pixelCount++;
            }
        }

        if (pixelCount > 0) {
            currentApl = (float) (totalLuminance / pixelCount / 255.0);
        }
        image.close();
    }

    /**
     * Stops screen content analysis and releases resources.
     */
    private void stopProjection() {
        isProjectionRunning = false;
        if (virtualDisplay != null) virtualDisplay.release();
        if (imageReader != null) imageReader.close();
        if (mediaProjection != null) mediaProjection.stop();
        virtualDisplay = null;
        imageReader = null;
        mediaProjection = null;
    }

    @Override
    public void onSensorChanged(SensorEvent event) {
        if (event.sensor.getType() == Sensor.TYPE_LIGHT) {
            float lux = event.values[0];
            lastKnownLux = lux;

            // Check for High Brightness Mode handover threshold
            boolean hbmEnabled = prefs.getBoolean("hbm_handover", false);
            if (hbmEnabled) {
                handleHbmHandover(lux);
            } else if (isHbmActive) {
                disableHbmMode();
            }

            // Only apply CustomLux curve if HBM isn't taking over
            if (!isHbmActive) {
                int brightness = calculateFinalBrightness(lux);
                applyBrightness(brightness);
                broadcastUpdate(lux, brightness);
            } else {
                broadcastUpdate(lux, -1);
            }
            checkProjectionStatus();
        }
    }

    /**
     * Switches control between CustomLux and system adaptive brightness based on lux.
     */
    private void handleHbmHandover(float lux) {
        if (lux > HBM_THRESHOLD && !isHbmActive) {
            enableHbmMode();
        } else if (lux <= HBM_THRESHOLD && isHbmActive) {
            disableHbmMode();
        }
    }

    /**
     * Activates system adaptive brightness for direct sunlight visibility.
     */
    private void enableHbmMode() {
        isHbmActive = true;
        Settings.System.putInt(getContentResolver(), Settings.System.SCREEN_BRIGHTNESS_MODE, 1);
    }

    /**
     * Deactivates system adaptive brightness to return control to CustomLux.
     */
    private void disableHbmMode() {
        isHbmActive = false;
        Settings.System.putInt(getContentResolver(), Settings.System.SCREEN_BRIGHTNESS_MODE, 0);
    }

    /**
     * Calculates brightness combining base curve, app offset, and APL compensation.
     */
    private int calculateFinalBrightness(float lux) {
        boolean curveDisabled = prefs.getBoolean("disable_curve_editor", false);
        
        String currentApp = getForegroundApp();
        AppProfile profile = dbHelper.getProfile(currentApp);
        int activeOffsetPercentage = (profile != null && profile.isEnabled()) ? profile.getBrightnessOffset() : 0;

        int baseBrightness;
        if (curveDisabled) {
            int currentSystem = Settings.System.getInt(getContentResolver(),
                    Settings.System.SCREEN_BRIGHTNESS, 128);

            // Detect manual user adjustment (e.g., from system slider)
            // We compare against our last applied value to see if the user moved the slider
            if (lastAppliedBrightness != -1 && Math.abs(currentSystem - lastAppliedBrightness) > 2) {
                // If they moved it, we update our internal base to match their choice (removing our offset)
                int scaleOffset = (activeOffsetPercentage * 255 / 100);
                manualBaseBrightness = Math.max(0, Math.min(currentSystem - scaleOffset, 255));
            }
            baseBrightness = manualBaseBrightness;
        } else {
            baseBrightness = getCurveBrightness(lux);
        }

        // Track the base brightness (without app-specific offsets) for reverting on stop
        lastBaseBrightness = baseBrightness;

        int finalBrightnessValue = baseBrightness;
        
        // Apply % offset to base (Convert % to 0-255 scale)
        int scaleOffset = (activeOffsetPercentage * 255 / 100);
        finalBrightnessValue += scaleOffset;

        // Smart dimming for white content (APL)
        if (lux < 50 && prefs.getBoolean("white_level_comp", false)) {
            float reductionFactor = 1.0f - (currentApl * 0.4f);
            finalBrightnessValue = (int) (finalBrightnessValue * reductionFactor);
        }

        return Math.max(0, Math.min(finalBrightnessValue, 255));
    }

    /**
     * Map lux to brightness using the user-defined curve points.
     */
    private int getCurveBrightness(float lux) {
        String data = prefs.getString("curve_points_data", "");
        if (data.isEmpty()) {
            return (int) (Math.min(lux, MAX_LUX) / MAX_LUX * 255);
        }

        String[] parts = data.split(",");
        int numPoints = parts.length;
        int[] points = new int[numPoints];
        for (int i = 0; i < numPoints; i++) {
            points[i] = Integer.parseInt(parts[i]);
        }

        // Map current lux to its position on the logarithmic curve
        double minLog = Math.log10(MIN_LUX);
        double maxLog = Math.log10(MAX_LUX);
        double currentLog = Math.log10(Math.max(MIN_LUX, Math.min(lux, MAX_LUX)));

        float position = (float) ((currentLog - minLog) / (maxLog - minLog) * (numPoints - 1));
        int index = (int) position;
        float fraction = position - index;

        if (index >= numPoints - 1) return (points[numPoints - 1] * 255 / 100);

        // Linear interpolation between the two nearest curve points
        int b1 = points[index];
        int b2 = points[index + 1];
        float interpolated = b1 + (b2 - b1) * fraction;

        return (int) (interpolated * 255 / 100);
    }

    /**
     * Identify currently active app using UsageStats API with persistence to handle idle states.
     */
    private String getForegroundApp() {
        UsageStatsManager usm = (UsageStatsManager) getSystemService(Context.USAGE_STATS_SERVICE);
        if (usm == null) return currentForegroundApp;

        long currentTime = System.currentTimeMillis();
        // Limit frequency of system calls to once per second
        if (currentTime - lastAppCheckTime < 1000 && !currentForegroundApp.isEmpty()) {
            return currentForegroundApp;
        }
        lastAppCheckTime = currentTime;

        // Look back 1 minute to find the most recent foreground transition
        UsageEvents events = usm.queryEvents(currentTime - 60000, currentTime);
        UsageEvents.Event event = new UsageEvents.Event();
        String detectedApp = "";

        while (events.hasNextEvent()) {
            events.getNextEvent(event);
            if (event.getEventType() == UsageEvents.Event.MOVE_TO_FOREGROUND) {
                detectedApp = event.getPackageName();
            }
        }

        if (detectedApp.isEmpty()) {
            // Fallback: check last used app if no transition events found in last minute
            List<UsageStats> stats = usm.queryUsageStats(UsageStatsManager.INTERVAL_DAILY, currentTime - 60000, currentTime);
            if (stats != null && !stats.isEmpty()) {
                UsageStats mostRecent = null;
                for (UsageStats s : stats) {
                    if (mostRecent == null || s.getLastTimeUsed() > mostRecent.getLastTimeUsed()) {
                        mostRecent = s;
                    }
                }
                if (mostRecent != null) {
                    detectedApp = mostRecent.getPackageName();
                }
            }
        }

        if (!detectedApp.isEmpty()) {
            currentForegroundApp = detectedApp;
        }
        return currentForegroundApp;
    }

    /**
     * Applies the calculated brightness value to the system settings.
     */
    private void applyBrightness(int value) {
        Settings.System.putInt(getContentResolver(), Settings.System.SCREEN_BRIGHTNESS, value);
        lastAppliedBrightness = value;
    }

    private void refreshBrightness() {
        if (!isHbmActive && lastKnownLux != -1) {
            int brightness = calculateFinalBrightness(lastKnownLux);
            applyBrightness(brightness);
            broadcastUpdate(lastKnownLux, brightness);
        }
    }

    /**
     * Broadcasts current lux and brightness values to the dashboard UI.
     */
    private void broadcastUpdate(float lux, int brightness) {
        Intent intent = new Intent("lux_update");
        intent.putExtra("lux_value", lux);
        if (brightness != -1) {
            intent.putExtra("brightness_value", brightness * 100 / 255);
        } else {
            intent.putExtra("brightness_value", -1); // -1 signals HBM active
        }
        sendBroadcast(intent);
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {}

    @Override
    public IBinder onBind(Intent intent) { return null; }

    @Override
    public void onDestroy() {
        stopProjection();
        sensorManager.unregisterListener(this);
        unregisterReceiver(settingsReceiver);
        
        // Revert to base brightness (remove app-specific offset) before stopping to prevent restart loops
        applyBrightness(lastBaseBrightness);

        restoreOriginalBrightnessMode(); // Restore system settings on exit
        super.onDestroy();
    }
}
