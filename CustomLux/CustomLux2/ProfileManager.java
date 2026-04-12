package ca.brocku.customlux;

import android.content.Context;
import android.content.SharedPreferences;

public class ProfileManager {
    private static final String PREFS_NAME = "CustomLuxPref";
    private static final String KEY_OFFSET = "offsetValue";
    private static final String KEY_CURVE_ENABLED = "curveEnabled";
    private static final String KEY_SLIDER_PREFIX = "slider_";
    private static final String KEY_PER_APP_ENABLED = "perAppEnabled";
    private static final String KEY_APP_PREFIX = "app_";





    public static void saveOffsetValue(Context context, int offsetValue){
        SharedPreferences.Editor editor = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit();
        editor.putInt(KEY_OFFSET, offsetValue);
        editor.commit();
    }

    public static int getOffsetValue(Context context){
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).getInt(KEY_OFFSET, 0);
    }


    public static void saveCurveEnabled(Context context, boolean enabled) {
        SharedPreferences.Editor editor = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit();
        editor.putBoolean(KEY_CURVE_ENABLED, enabled);
        editor.commit();
    }

    public static boolean isCurveEnabled(Context context) {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).getBoolean(KEY_CURVE_ENABLED, false);
    }

    public static void saveSliderValue(Context context, int index, int value) {
        SharedPreferences.Editor editor = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit();
        editor.putInt(KEY_SLIDER_PREFIX + index, value);
        editor.commit();
    }

    public static int getSliderValue(Context context, int index) {
        int defaultValue = (index * 255) / 6;
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).getInt(KEY_SLIDER_PREFIX + index, defaultValue);
    }

    public static void savePerAppEnabled(Context context, boolean enabled) {
        SharedPreferences.Editor editor = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit();
        editor.putBoolean(KEY_PER_APP_ENABLED, enabled);
        editor.commit();
    }

    public static boolean isPerAppEnabled(Context context) {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).getBoolean(KEY_PER_APP_ENABLED, false);
    }

    public static void saveAppBrightness(Context context, String packageName, int brightness) {
        SharedPreferences.Editor editor = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit();
        editor.putInt(KEY_APP_PREFIX + packageName, brightness);
        editor.commit();

    }
    public static int getAppBrightness(Context context, String packageName) {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).getInt(KEY_APP_PREFIX + packageName, 128);
    }

}
