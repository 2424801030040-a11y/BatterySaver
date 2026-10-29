package com.example.batterysaver;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/** Simple in-memory store for battery readings (replace with Room for persistence). */
public final class BatteryLog {

    public static class Entry {
        public final long time;
        public final int level;
        public final String status;

        public Entry(long time, int level, String status) {
            this.time = time;
            this.level = level;
            this.status = status;
        }

        @Override
        public String toString() {
            String t = new SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(new Date(time));
            return t + "  –  " + level + "%  –  " + status;
        }
    }

    private static final List<Entry> ENTRIES = new ArrayList<>();

    private BatteryLog() {}

    public static synchronized void add(int level, String status) {
        ENTRIES.add(new Entry(System.currentTimeMillis(), level, status));
    }

    public static synchronized List<Entry> snapshot() {
        return new ArrayList<>(ENTRIES);
    }

    public static synchronized void remove(Entry e) {
        ENTRIES.remove(e);
    }
}
