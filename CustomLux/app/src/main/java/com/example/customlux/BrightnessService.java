package com.example.customlux;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.app.usage.UsageEvents;
import android.app.usage.UsageStatsManager;
import android.content.Context;
import android.content.Intent;
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
import android.util.Log;
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
    private final float HBM_THRESHOLD = 20000f;

    private int originalBrightnessMode = -1;
    private boolean isHbmActive = false;

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

    private void saveOriginalBrightnessMode() {
        try {
            originalBrightnessMode = Settings.System.getInt(getContentResolver(), Settings.System.SCREEN_BRIGHTNESS_MODE);
        } catch (Settings.SettingNotFoundException e) {
            originalBrightnessMode = 0;
        }
    }

    private void restoreOriginalBrightnessMode() {
        if (originalBrightnessMode != -1) {
            try {
                Settings.System.putInt(getContentResolver(), Settings.System.SCREEN_BRIGHTNESS_MODE, originalBrightnessMode);
            } catch (Exception e) {
                Log.e("BrightnessService", "Restore failed", e);
            }
        }
    }

    private void disableSystemAdaptiveBrightness() {
        try {
            Settings.System.putInt(getContentResolver(), Settings.System.SCREEN_BRIGHTNESS_MODE, 0); // Manual
        } catch (Exception e) {
        }
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

    private void startProjection() {
        MediaProjectionManager mpm = (MediaProjectionManager) getSystemService(MEDIA_PROJECTION_SERVICE);
        mediaProjection = mpm.getMediaProjection(-1, projectionIntent);
        if (mediaProjection != null) {
            isProjectionRunning = true;
            setupVirtualDisplay();
            scheduleAplCalculation();
        }
    }

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
        Image image = null;
        try {
            image = imageReader.acquireLatestImage();
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
        } catch (Exception e) {
        } finally {
            if (image != null) image.close();
        }
    }

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
            
            boolean hbmEnabled = prefs.getBoolean("hbm_handover", false);
            if (hbmEnabled) {
                handleHbmHandover(lux);
            } else if (isHbmActive) {
                disableHbmMode();
            }

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

    // Restore system adaptive brightness in direct sunlight
    private void handleHbmHandover(float lux) {
        if (lux > HBM_THRESHOLD && !isHbmActive) {
            enableHbmMode();
        } else if (lux <= HBM_THRESHOLD && isHbmActive) {
            disableHbmMode();
        }
    }

    private void enableHbmMode() {
        isHbmActive = true;
        try {
            Settings.System.putInt(getContentResolver(), Settings.System.SCREEN_BRIGHTNESS_MODE, 1);
        } catch (Exception e) {}
    }

    private void disableHbmMode() {
        isHbmActive = false;
        try {
            Settings.System.putInt(getContentResolver(), Settings.System.SCREEN_BRIGHTNESS_MODE, 0);
        } catch (Exception e) {}
    }

    /**
     * Calculates brightness combining base curve, app offset, and APL compensation.
     */
    private int calculateFinalBrightness(float lux) {
        int baseBrightness = getCurveBrightness(lux);
        String currentApp = getForegroundApp();
        int offsetPercentage = 0;
        
        List<AppProfile> profiles = dbHelper.getAllProfiles();
        for (AppProfile profile : profiles) {
            if (profile.getPackageName().equals(currentApp) && profile.isEnabled()) {
                offsetPercentage = profile.getBrightnessOffset();
                break;
            }
        }

        int finalBrightnessValue = baseBrightness + (offsetPercentage * 255 / 100);

        // Smart dimming for white content in dark environments
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

        double minLog = Math.log10(MIN_LUX);
        double maxLog = Math.log10(MAX_LUX);
        double currentLog = Math.log10(Math.max(MIN_LUX, Math.min(lux, MAX_LUX)));
        
        float position = (float) ((currentLog - minLog) / (maxLog - minLog) * (numPoints - 1));
        int index = (int) position;
        float fraction = position - index;

        if (index >= numPoints - 1) return (points[numPoints - 1] * 255 / 100);
        
        int b1 = points[index];
        int b2 = points[index + 1];
        float interpolated = b1 + (b2 - b1) * fraction;
        
        return (int) (interpolated * 255 / 100);
    }

    /**
     * Identify currently active app using UsageStats API.
     */
    private String getForegroundApp() {
        UsageStatsManager usm = (UsageStatsManager) getSystemService(Context.USAGE_STATS_SERVICE);
        if (usm == null) return "";
        long time = System.currentTimeMillis();
        UsageEvents events = usm.queryEvents(time - 5000, time);
        UsageEvents.Event event = new UsageEvents.Event();
        String lastPackage = "";
        while (events.hasNextEvent()) {
            events.getNextEvent(event);
            if (event.getEventType() == UsageEvents.Event.MOVE_TO_FOREGROUND) {
                lastPackage = event.getPackageName();
            }
        }
        return lastPackage;
    }

    private void applyBrightness(int value) {
        try {
            Settings.System.putInt(getContentResolver(), Settings.System.SCREEN_BRIGHTNESS, value);
        } catch (Exception e) {
        }
    }

    private void broadcastUpdate(float lux, int brightness) {
        Intent intent = new Intent("lux_update");
        intent.putExtra("lux_value", lux);
        if (brightness != -1) {
            intent.putExtra("brightness_value", (int)(brightness * 100 / 255));
        } else {
            intent.putExtra("brightness_value", -1);
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
        restoreOriginalBrightnessMode();
        super.onDestroy();
    }
}
