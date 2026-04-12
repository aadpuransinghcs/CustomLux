package ca.brocku.customlux;

import android.app.AppOpsManager;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.IBinder;
import android.os.Messenger;
import android.os.Process;
import android.provider.Settings;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import java.util.List;

public class MainActivity extends AppCompatActivity {
    
    private Switch serviceSwitch;
    private Switch curveSwitch;
    private Switch perAppSwitch;
    private Messenger messenger;
    private boolean mBound;
    private TextView luxValueDisplay;
    private TextView brightnessValueDisplay;
    private SeekBar[] sliders = new SeekBar[7];
    private LinearLayout appListContainer;


//Testing




    private BroadcastReceiver luxReceiver = new BroadcastReceiver(){
        @Override
        public void onReceive(Context context, Intent intent) {
            if (intent.getAction().equals("lux_update")) {
                float luxValue = intent.getFloatExtra("lux_value", 0);
                int brightnessValue = intent.getIntExtra("brightness_value", 0);
              //  if (luxValueDisplay != null) {
                    luxValueDisplay.setText("Lux Value: " + luxValue);
             //   }
             //   if (brightnessValueDisplay != null) {
                    brightnessValueDisplay.setText("Brightness Value: " + brightnessValue);
               // }
             //   if (luxValue > 30000 && serviceSwitch != null && serviceSwitch.isChecked()) { // High Brightness mode at 30000 lux
            //        serviceSwitch.setChecked(false);
            //        toggleService(serviceSwitch);
            //    }
            }

        }

    };

    private ServiceConnection servConn = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName componentName, IBinder iBinder) {
            messenger = new Messenger(iBinder);
            mBound = true;
            Toast.makeText(MainActivity.this, "Service Connected", Toast.LENGTH_SHORT).show();
        }
        @Override
        public void onServiceDisconnected(ComponentName componentName) {
            messenger = null;
            mBound = false;

        }
    };
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        serviceSwitch = (Switch) findViewById(R.id.service_toggle);
        luxValueDisplay = (TextView) findViewById(R.id.lux_value);
        brightnessValueDisplay = (TextView) findViewById(R.id.brightness_value);
        curveSwitch = (Switch) findViewById(R.id.curve_toggle);
        perAppSwitch = (Switch) findViewById(R.id.per_app_toggle);
        appListContainer = (LinearLayout) findViewById(R.id.app_list);


        if (perAppSwitch != null) {
            perAppSwitch.setChecked(ProfileManager.isPerAppEnabled(this));
        }

        if (serviceSwitch != null && savedInstanceState != null) {
            serviceSwitch.setChecked(savedInstanceState.getBoolean("switch_state", false));
        }


        if (curveSwitch != null){
            curveSwitch.setChecked(ProfileManager.isCurveEnabled(this));
        }


        setupCurveSliders();
        populateAppList();

        checkPermission();


    }



    private void setupCurveSliders() {
        for (int i = 0; i < 7; i++) {

            int id = getResources().getIdentifier("slider" + i, "id", getPackageName());
            sliders[i] = (SeekBar) findViewById(id);

            if (sliders[i] != null) {
                sliders[i].setProgress(ProfileManager.getSliderValue(this, i));

                final int finali = i;
                sliders[i].setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
                    @Override
                    public void onStartTrackingTouch(SeekBar seekBar) {

                    }

                    @Override
                    public void onStopTrackingTouch(SeekBar seekBar) {

                    }


                    @Override
                    public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                        if (fromUser) {
                            ProfileManager.saveSliderValue(MainActivity.this, finali, progress);
                        }

                    }

                });

            }
        }
    }

    private void populateAppList() {
        if (appListContainer == null) return;
        PackageManager pm = getPackageManager();
        List<ApplicationInfo> apps = pm.getInstalledApplications(PackageManager.GET_META_DATA);

        for (ApplicationInfo appInfo : apps) {

            if (pm.getLaunchIntentForPackage(appInfo.packageName) != null) {
                String label = appInfo.loadLabel(pm).toString();
                final String pkgName = appInfo.packageName;

                TextView nameLabel = new TextView(this);
                nameLabel.setText(label);
                nameLabel.setPadding(0, 30, 0, 10);
                nameLabel.setTextSize(12);


                SeekBar brSlider = new SeekBar(this);
                brSlider.setMax(255);
                brSlider.setProgress(ProfileManager.getAppBrightness(this, pkgName));
                brSlider.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
                    @Override
                    public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                        if (fromUser) {
                            ProfileManager.saveAppBrightness(MainActivity.this, pkgName, progress);
                        }
                    }
                    @Override public void onStartTrackingTouch(SeekBar seekBar) {}
                    @Override public void onStopTrackingTouch(SeekBar seekBar) {}
                });

                appListContainer.addView(nameLabel);
                appListContainer.addView(brSlider);
            }
        }
    }

    public void togglePerApp(View view) {
        if(perAppSwitch!=null) {
            ProfileManager.savePerAppEnabled(this, perAppSwitch.isChecked());
        }

    }


    public void toggleCurve(View view) {

        if(curveSwitch!=null) {
        ProfileManager.saveCurveEnabled(this, curveSwitch.isChecked());

        }

    }


    private void checkPermission() {
        if(!Settings.System.canWrite(this)){
            Intent intent = new Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS);
            intent.setData(Uri.parse("package:" + getPackageName()));
            Toast.makeText(this, "change settings permission", Toast.LENGTH_SHORT).show();
            startActivity(intent);

        }

        AppOpsManager appOps = (AppOpsManager) getSystemService(Context.APP_OPS_SERVICE);
        int mode = appOps.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), getPackageName());
        if (mode != AppOpsManager.MODE_ALLOWED) {
            startActivity(new Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS));
            Toast.makeText(this, "access for app list", Toast.LENGTH_LONG).show();
        }
    }

    @Override
    protected void onStart(){
        super.onStart();
        registerReceiver(luxReceiver, new IntentFilter("lux_update"));

    }

    @Override
    protected void onStop(){
        super.onStop();
        unregisterReceiver(luxReceiver);
    }



    // start stop service
    public void toggleService(View view) {
        Intent intent = new Intent(this, BrightnessService.class);
        if (serviceSwitch.isChecked()) { // start service
            startService(intent);
            bindService(intent, servConn, BIND_AUTO_CREATE);
        } else {
            if (mBound) { // stop service
                unbindService(servConn);
                mBound = false;
            }
            stopService(intent);

                luxValueDisplay.setText("Lux Value: Disconnected");


                brightnessValueDisplay.setText("Brightness Value: Disconnected");


            Toast.makeText(MainActivity.this, "Service Disconnected", Toast.LENGTH_SHORT).show();
        }

    }

    @Override
    public void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putBoolean("switch_state", serviceSwitch.isChecked());
    }


}