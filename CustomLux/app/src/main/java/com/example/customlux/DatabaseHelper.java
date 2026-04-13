package com.example.customlux;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import java.util.ArrayList;
import java.util.List;

/**
 * Manages the SQLite database for storing per-app brightness profiles.
 */
public class DatabaseHelper extends SQLiteOpenHelper {
    private static final String DATABASE_NAME = "CustomLux.db";
    private static final int DATABASE_VERSION = 1;

    public static final String TABLE_PROFILES = "app_profiles";
    public static final String COLUMN_PACKAGE = "package_name";
    public static final String COLUMN_APP_NAME = "app_name";
    public static final String COLUMN_OFFSET = "brightness_offset";
    public static final String COLUMN_ENABLED = "is_enabled";

    private static final String TABLE_CREATE =
            "CREATE TABLE " + TABLE_PROFILES + " (" +
                    "_id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                    COLUMN_PACKAGE + " TEXT UNIQUE, " +
                    COLUMN_APP_NAME + " TEXT, " +
                    COLUMN_OFFSET + " INTEGER, " +
                    COLUMN_ENABLED + " INTEGER);";

    public DatabaseHelper(Context context) {
        super(context, DATABASE_NAME, null, DATABASE_VERSION);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        db.execSQL(TABLE_CREATE);
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        db.execSQL("DROP TABLE IF EXISTS " + TABLE_PROFILES);
        onCreate(db);
    }

    /**
     * Adds or updates an app profile in the database.
     */
    public void addProfile(AppProfile profile) {
        SQLiteDatabase db = this.getWritableDatabase();
        ContentValues values = new ContentValues();
        values.put(COLUMN_PACKAGE, profile.getPackageName());
        values.put(COLUMN_APP_NAME, profile.getAppName());
        values.put(COLUMN_OFFSET, profile.getBrightnessOffset());
        values.put(COLUMN_ENABLED, profile.isEnabled() ? 1 : 0);
        db.insertWithOnConflict(TABLE_PROFILES, null, values, SQLiteDatabase.CONFLICT_REPLACE);
        db.close();
    }

    /**
     * Retrieves a single profile by package name.
     */
    public AppProfile getProfile(String packageName) {
        if (packageName == null || packageName.isEmpty()) return null;
        SQLiteDatabase db = this.getReadableDatabase();
        Cursor cursor = db.query(TABLE_PROFILES, null, COLUMN_PACKAGE + " = ?", 
                new String[]{packageName}, null, null, null);
        
        AppProfile profile = null;
        if (cursor != null && cursor.moveToFirst()) {
            profile = new AppProfile(
                    cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_PACKAGE)),
                    cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_APP_NAME)),
                    cursor.getInt(cursor.getColumnIndexOrThrow(COLUMN_OFFSET)),
                    cursor.getInt(cursor.getColumnIndexOrThrow(COLUMN_ENABLED)) == 1
            );
            cursor.close();
        }
        db.close();
        return profile;
    }

    /**
     * Retrieves all app profiles from the database.
     */
    public List<AppProfile> getAllProfiles() {
        List<AppProfile> profileList = new ArrayList<>();
        SQLiteDatabase db = this.getReadableDatabase();
        Cursor cursor = db.query(TABLE_PROFILES, null, null, null, null, null, null);

        if (cursor.moveToFirst()) {
            do {
                AppProfile profile = new AppProfile(
                        cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_PACKAGE)),
                        cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_APP_NAME)),
                        cursor.getInt(cursor.getColumnIndexOrThrow(COLUMN_OFFSET)),
                        cursor.getInt(cursor.getColumnIndexOrThrow(COLUMN_ENABLED)) == 1
                );
                profileList.add(profile);
            } while (cursor.moveToNext());
        }
        cursor.close();
        db.close();
        return profileList;
    }

    /**
     * Updates an existing profile's offset and enabled state.
     */
    public void updateProfile(AppProfile profile) {
        SQLiteDatabase db = this.getWritableDatabase();
        ContentValues values = new ContentValues();
        values.put(COLUMN_OFFSET, profile.getBrightnessOffset());
        values.put(COLUMN_ENABLED, profile.isEnabled() ? 1 : 0);
        db.update(TABLE_PROFILES, values, COLUMN_PACKAGE + " = ?", new String[]{profile.getPackageName()});
        db.close();
    }

    /**
     * Removes an app profile from the database.
     */
    public void deleteProfile(String packageName) {
        SQLiteDatabase db = this.getWritableDatabase();
        db.delete(TABLE_PROFILES, COLUMN_PACKAGE + " = ?", new String[]{packageName});
        db.close();
    }
}
