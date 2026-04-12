package ca.brocku.customlux;

import android.annotation.SuppressLint;
import android.app.Notification;
import android.app.Service;
import android.app.usage.UsageEvents;
import android.app.usage.UsageStatsManager;
import android.content.Context;
import android.content.Intent;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.os.Messenger;
import android.provider.Settings;
import android.widget.Toast;

public class BrightnessService extends Service implements SensorEventListener {

    private SensorManager sensorManager;
    private Sensor lightSensor;

    class InBoundHandler extends Handler {

        @Override
        public void handleMessage(Message msg) {
                super.handleMessage(msg);
        }
    }

    final Messenger myMessenger = new Messenger(new InBoundHandler());


    @Override
    public void onCreate() {
        super.onCreate();
        sensorManager = (SensorManager) getSystemService(SENSOR_SERVICE);
        lightSensor = sensorManager.getDefaultSensor(Sensor.TYPE_LIGHT);

        Notification.Builder builder = new Notification.Builder(this)
                .setContentTitle("CustomLux")
                .setContentText("Service is running")
                .setSmallIcon(android.R.drawable.ic_menu_compass)
                .setWhen(System.currentTimeMillis());

        startForeground(777, builder.build());

    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
    if (lightSensor!=null) {
        sensorManager.registerListener(this, lightSensor, SensorManager.SENSOR_DELAY_NORMAL);

    }
    return START_STICKY;


    }

    @Override
    public IBinder onBind(Intent intent) {
        return myMessenger.getBinder();

    }

    public void onSensorChanged(SensorEvent event) {
        if (event.sensor.getType() == Sensor.TYPE_LIGHT) {
            float lux = event.values[0];
            int brightness = calculateBrightness(lux);

            applyBrightnessCurve(brightness);
            updateLuxDisplay(lux, brightness);
        }
    }

    private int calculateBrightness(float lux) {

        if (ProfileManager.isPerAppEnabled(this)) {
            String currentApp = getForegroundApp();

           if (currentApp != null && !currentApp.isEmpty()) {
                int appBrightness = ProfileManager.getAppBrightness(this, currentApp);
                return appBrightness;
            }else {
               return ProfileManager.getAppBrightness(this, getPackageName());
           }
        }

        if (ProfileManager.isCurveEnabled(this)) {
            int bucketindex = (int) (lux / 5714);
            if (bucketindex < 0) bucketindex = 0;
            if (bucketindex > 6) bucketindex = 6;
            return ProfileManager.getSliderValue(this, bucketindex);
        }

        return (int) (lux / 40000 * 255);
    }

    private String getForegroundApp() {
        UsageStatsManager usm = (UsageStatsManager) getSystemService(Context.USAGE_STATS_SERVICE);
        long time = System.currentTimeMillis();

        UsageEvents events = usm.queryEvents(time - 10000, time);
        UsageEvents.Event event = new UsageEvents.Event();
        String lastPackage = "";

        while (events.hasNextEvent()) {
            events.getNextEvent(event);
            if (event.getEventType() == UsageEvents.Event.MOVE_TO_FOREGROUND) {
                lastPackage = event.getPackageName();
            }
        }
        return lastPackage.isEmpty() ? getPackageName() : lastPackage;
    }

    private void updateLuxDisplay(float lux, int brightness) {
        Intent intent = new Intent("lux_update");
        intent.putExtra("lux_value", lux);
        intent.putExtra("brightness_value", brightness);
        sendBroadcast(intent);
    }

    private void applyBrightnessCurve(int brightnessValue) {
    // android system brightness is 8 bit


        Settings.System.putInt(getContentResolver(), Settings.System.SCREEN_BRIGHTNESS, brightnessValue);
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {
    }

    @Override
    public void onDestroy() {
        sensorManager.unregisterListener(this);
        super.onDestroy();
    }






}




