package com.example.batterysaver;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/** Persistent local history of battery level changes. */
public final class BatteryLog {
    public static class Entry {
        public final long id;
        public final long time;
        public final int level;
        public final String status;
        public final int temperatureTenths;
        public final int voltageMv;
        public final String source;

        Entry(long id, long time, int level, String status, int temperatureTenths, int voltageMv, String source) {
            this.id = id;
            this.time = time;
            this.level = level;
            this.status = status;
            this.temperatureTenths = temperatureTenths;
            this.voltageMv = voltageMv;
            this.source = source;
        }

        @Override public String toString() {
            String timestamp = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
                    .format(new Date(time));
            String detail = temperatureTenths > 0
                    ? String.format(Locale.getDefault(), "  –  %.1f °C  –  %d mV", temperatureTenths / 10f, voltageMv)
                    : "";
            return timestamp + "  –  " + level + "%  –  " + status + detail;
        }
    }

    private static Store store;
    private BatteryLog() {}

    public static synchronized void initialize(Context context) {
        if (store == null) store = new Store(context.getApplicationContext());
    }

    public static synchronized void add(int level, String status, int temperatureTenths,
                                        int voltageMv, String source) {
        if (store == null) return;
        ContentValues values = new ContentValues();
        values.put("time", System.currentTimeMillis());
        values.put("level", level);
        values.put("status", status);
        values.put("temp_tenths", temperatureTenths);
        values.put("voltage_mv", voltageMv);
        values.put("source", source);
        SQLiteDatabase db = store.getWritableDatabase();
        db.insert("battery_entries", null, values);
        db.execSQL("DELETE FROM battery_entries WHERE _id NOT IN "
                + "(SELECT _id FROM battery_entries ORDER BY time DESC LIMIT 3000)");
    }

    public static synchronized List<Entry> snapshot() {
        List<Entry> entries = new ArrayList<>();
        if (store == null) return entries;
        try (Cursor cursor = store.getReadableDatabase().query("battery_entries",
                new String[]{"_id", "time", "level", "status", "temp_tenths", "voltage_mv", "source"},
                null, null, null, null, "time ASC")) {
            while (cursor.moveToNext()) {
                entries.add(new Entry(cursor.getLong(0), cursor.getLong(1),
                        cursor.getInt(2), cursor.getString(3), cursor.getInt(4), cursor.getInt(5), cursor.getString(6)));
            }
        }
        return entries;
    }

    public static synchronized void remove(Entry entry) {
        if (store != null) store.getWritableDatabase().delete("battery_entries", "_id=?",
                new String[]{String.valueOf(entry.id)});
    }

    private static final class Store extends SQLiteOpenHelper {
        Store(Context context) { super(context, "battery_history.db", null, 2); }
        @Override public void onCreate(SQLiteDatabase db) {
            db.execSQL("CREATE TABLE battery_entries (_id INTEGER PRIMARY KEY AUTOINCREMENT, "
                    + "time INTEGER NOT NULL, level INTEGER NOT NULL, status TEXT NOT NULL, "
                    + "temp_tenths INTEGER NOT NULL DEFAULT 0, voltage_mv INTEGER NOT NULL DEFAULT 0, "
                    + "source TEXT NOT NULL DEFAULT '')");
        }
        @Override public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
            if (oldVersion < 2) {
                db.execSQL("ALTER TABLE battery_entries ADD COLUMN temp_tenths INTEGER NOT NULL DEFAULT 0");
                db.execSQL("ALTER TABLE battery_entries ADD COLUMN voltage_mv INTEGER NOT NULL DEFAULT 0");
                db.execSQL("ALTER TABLE battery_entries ADD COLUMN source TEXT NOT NULL DEFAULT ''");
            }
        }
    }
}
