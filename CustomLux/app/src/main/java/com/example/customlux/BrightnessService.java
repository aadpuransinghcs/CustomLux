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
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Build;
import android.os.IBinder;
import android.provider.Settings;

import java.util.List;

public class BrightnessService extends Service implements SensorEventListener {

    private SensorManager sensorManager;
    private Sensor lightSensor;
    private DatabaseHelper dbHelper;
    private SharedPreferences prefs;

    private static final String CHANNEL_ID = "CustomLuxChannel";
    private final float MAX_LUX = 10000f;
    private final float MIN_LUX = 1f;

    @Override
    public void onCreate() {
        super.onCreate();
        sensorManager = (SensorManager) getSystemService(SENSOR_SERVICE);
        lightSensor = sensorManager.getDefaultSensor(Sensor.TYPE_LIGHT);
        dbHelper = new DatabaseHelper(this);
        prefs = getSharedPreferences("CustomLuxPrefs", MODE_PRIVATE);

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
        return START_STICKY;
    }

    @Override
    public void onSensorChanged(SensorEvent event) {
        if (event.sensor.getType() == Sensor.TYPE_LIGHT) {
            float lux = event.values[0];
            int brightness = calculateFinalBrightness(lux);
            applyBrightness(brightness);
            broadcastUpdate(lux, brightness);
        }
    }

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

        int offsetValue = (offsetPercentage * 255 / 100);
        int finalBrightness = baseBrightness + offsetValue;
        
        return Math.max(0, Math.min(finalBrightness, 255));
    }

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

    private String getForegroundApp() {
        UsageStatsManager usm = (UsageStatsManager) getSystemService(Context.USAGE_STATS_SERVICE);
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
        intent.putExtra("brightness_value", (int)(brightness * 100 / 255));
        sendBroadcast(intent);
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {}

    @Override
    public IBinder onBind(Intent intent) { return null; }

    @Override
    public void onDestroy() {
        sensorManager.unregisterListener(this);
        super.onDestroy();
    }
}
