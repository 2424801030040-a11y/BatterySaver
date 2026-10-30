package com.example.batterysaver;

import android.app.AppOpsManager;
import android.app.usage.UsageEvents;
import android.app.usage.UsageStatsManager;
import android.content.Intent;
import android.os.BatteryManager;
import android.os.Bundle;
import android.os.PowerManager;
import android.provider.Settings;
import android.net.Uri;
import android.view.View;
import android.widget.ImageView;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import com.google.android.material.progressindicator.LinearProgressIndicator;

import java.text.DateFormatSymbols;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class UsageActivity extends AppCompatActivity {
    private LinearLayout appUsageList;
    private TextView usageHint, weekComparisonHint;
    private Button grantAccess;
    private UsageChartView weekdayChart, weekChart, appChart;
    private static final long DAY = 24L * 60 * 60 * 1000;

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        setContentView(R.layout.activity_usage);
        if (getSupportActionBar() != null) getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        appUsageList = findViewById(R.id.appUsageList);
        usageHint = findViewById(R.id.usageHint);
        weekComparisonHint = findViewById(R.id.weekComparisonHint);
        grantAccess = findViewById(R.id.grantUsageAccess);
        weekdayChart = findViewById(R.id.weekdayChart);
        weekChart = findViewById(R.id.weekChart);
        appChart = findViewById(R.id.appChart);
        grantAccess.setOnClickListener(v -> startActivity(new Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)));
        showDeviceBattery();
        findViewById(R.id.openBatterySettings).setOnClickListener(v -> openBatteryUsage());
        findViewById(R.id.openBackgroundSettings).setOnClickListener(v -> {
            Intent details = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                    Uri.parse("package:" + getPackageName()));
            startActivity(details);
        });
    }

    @Override protected void onResume() {
        super.onResume();
        ModeBrightness.apply(this);
        loadUsage();
    }

    private void showDeviceBattery() {
        Intent battery = registerReceiver(null, new android.content.IntentFilter(Intent.ACTION_BATTERY_CHANGED));
        int level = 0;
        boolean charging = false;
        if (battery != null) {
            int raw = battery.getIntExtra(BatteryManager.EXTRA_LEVEL, 0);
            int scale = battery.getIntExtra(BatteryManager.EXTRA_SCALE, 100);
            level = scale > 0 ? raw * 100 / scale : raw;
            int status = battery.getIntExtra(BatteryManager.EXTRA_STATUS, -1);
            charging = status == BatteryManager.BATTERY_STATUS_CHARGING || status == BatteryManager.BATTERY_STATUS_FULL;
        }
        ((TextView) findViewById(R.id.usageBatteryLevel)).setText(getString(R.string.percent_value, level));
        ((TextView) findViewById(R.id.usageBatteryStatus)).setText(charging ? R.string.status_charging : R.string.status_discharging);
        PowerManager pm = (PowerManager) getSystemService(POWER_SERVICE);
        ((TextView) findViewById(R.id.saverState)).setText(getString(pm != null && pm.isPowerSaveMode()
                ? R.string.saver_enabled : R.string.saver_disabled));
    }

    private boolean hasUsageAccess() {
        AppOpsManager ops = (AppOpsManager) getSystemService(APP_OPS_SERVICE);
        return ops != null && ops.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS,
                android.os.Process.myUid(), getPackageName()) == AppOpsManager.MODE_ALLOWED;
    }

    private void loadUsage() {
        boolean allowed = hasUsageAccess();
        grantAccess.setVisibility(allowed ? View.GONE : View.VISIBLE);
        appUsageList.removeAllViews();
        if (!allowed) {
            usageHint.setText(R.string.usage_access_required);
            clearCharts();
            return;
        }
        Calendar today = atMidnight(Calendar.getInstance());
        Calendar queryStart = (Calendar) today.clone();
        queryStart.add(Calendar.DAY_OF_YEAR, -14);
        long currentWeekStart = weekStart(today).getTimeInMillis();
        UsageStatsManager manager = (UsageStatsManager) getSystemService(USAGE_STATS_SERVICE);
        Map<Long, Long> daily = new HashMap<>();
        Map<String, Long> currentWeekApps = new HashMap<>();
        Map<String, Long> previousWeekApps = new HashMap<>();
        if (manager != null) {
            UsageEvents events = manager.queryEvents(queryStart.getTimeInMillis(), System.currentTimeMillis());
            UsageEvents.Event event = new UsageEvents.Event();
            String foregroundPackage = null;
            long foregroundStart = 0;
            while (events != null && events.hasNextEvent()) {
                events.getNextEvent(event);
                if (event.getEventType() == UsageEvents.Event.MOVE_TO_FOREGROUND) {
                    if (foregroundPackage != null) addForegroundInterval(daily, currentWeekApps, previousWeekApps,
                            foregroundPackage, foregroundStart, event.getTimeStamp(), currentWeekStart);
                    foregroundPackage = event.getPackageName();
                    foregroundStart = event.getTimeStamp();
                } else if (event.getEventType() == UsageEvents.Event.MOVE_TO_BACKGROUND
                        && event.getPackageName().equals(foregroundPackage)) {
                    addForegroundInterval(daily, currentWeekApps, previousWeekApps, foregroundPackage,
                            foregroundStart, event.getTimeStamp(), currentWeekStart);
                    foregroundPackage = null;
                }
            }
            if (foregroundPackage != null) addForegroundInterval(daily, currentWeekApps, previousWeekApps,
                    foregroundPackage, foregroundStart, System.currentTimeMillis(), currentWeekStart);
        }
        renderDailyChart(today, daily);
        renderWeekChart(today, daily);
        renderApps(currentWeekApps, previousWeekApps);
    }

    private void addForegroundInterval(Map<Long, Long> daily, Map<String, Long> currentWeekApps,
                                       Map<String, Long> previousWeekApps, String packageName,
                                       long start, long end, long currentWeekStart) {
        if (packageName == null || packageName.equals(getPackageName()) || end <= start) return;
        long cursor = start;
        while (cursor < end) {
            Calendar day = Calendar.getInstance();
            day.setTimeInMillis(cursor);
            long dayStart = atMidnight(day).getTimeInMillis();
            long sliceEnd = Math.min(end, dayStart + DAY);
            long duration = sliceEnd - cursor;
            daily.merge(dayStart, duration, Long::sum);
            if (dayStart >= currentWeekStart) currentWeekApps.merge(packageName, duration, Long::sum);
            else previousWeekApps.merge(packageName, duration, Long::sum);
            cursor = sliceEnd;
        }
    }

    private void renderDailyChart(Calendar today, Map<Long, Long> daily) {
        String[] labels = new String[7];
        float[] values = new float[7];
        Calendar date = (Calendar) today.clone();
        date.add(Calendar.DAY_OF_YEAR, -6);
        DateFormatSymbols symbols = new DateFormatSymbols();
        for (int i = 0; i < 7; i++) {
            labels[i] = symbols.getShortWeekdays()[date.get(Calendar.DAY_OF_WEEK)];
            long millis = daily.getOrDefault(date.getTimeInMillis(), 0L);
            values[i] = millis / 3600000f;
            date.add(Calendar.DAY_OF_YEAR, 1);
        }
        weekdayChart.setData(labels, values, getString(R.string.chart_hours));
    }

    private void renderWeekChart(Calendar today, Map<Long, Long> daily) {
        Calendar monday = weekStart(today);
        Calendar priorMonday = (Calendar) monday.clone();
        priorMonday.add(Calendar.DAY_OF_YEAR, -7);
        float[] current = new float[7], previous = new float[7];
        String[] weekdays = new DateFormatSymbols().getShortWeekdays();
        String[] labels = {weekdays[Calendar.MONDAY], weekdays[Calendar.TUESDAY], weekdays[Calendar.WEDNESDAY],
                weekdays[Calendar.THURSDAY], weekdays[Calendar.FRIDAY], weekdays[Calendar.SATURDAY], weekdays[Calendar.SUNDAY]};
        long currentTotal = 0, previousTotal = 0;
        for (int i = 0; i < 7; i++) {
            Calendar d = (Calendar) monday.clone(); d.add(Calendar.DAY_OF_YEAR, i);
            long a = daily.getOrDefault(d.getTimeInMillis(), 0L);
            d = (Calendar) priorMonday.clone(); d.add(Calendar.DAY_OF_YEAR, i);
            long b = daily.getOrDefault(d.getTimeInMillis(), 0L);
            current[i] = a / 3600000f; previous[i] = b / 3600000f;
            currentTotal += a; previousTotal += b;
        }
        weekChart.setComparison(labels, current, previous, getString(R.string.this_week), getString(R.string.previous_week));
        weekComparisonHint.setText(getString(R.string.week_totals, formatDuration(currentTotal), formatDuration(previousTotal)));
        if (currentTotal == 0 && previousTotal == 0) {
            usageHint.setText(R.string.usage_no_data);
        } else if (previousTotal == 0) {
            usageHint.setText(getString(R.string.week_usage_no_previous, formatDuration(currentTotal)));
        } else if (currentTotal == previousTotal) {
            usageHint.setText(R.string.week_usage_unchanged);
        } else {
            long difference = Math.abs(currentTotal - previousTotal);
            int percent = Math.round(difference * 100f / previousTotal);
            usageHint.setText(getString(currentTotal > previousTotal
                            ? R.string.week_usage_increased : R.string.week_usage_decreased,
                    percent, formatDuration(difference)));
        }
    }

    private Calendar weekStart(Calendar day) {
        Calendar monday = (Calendar) day.clone();
        int offset = (monday.get(Calendar.DAY_OF_WEEK) + 5) % 7;
        monday.add(Calendar.DAY_OF_YEAR, -offset);
        return monday;
    }

    private void renderApps(Map<String, Long> currentWeek, Map<String, Long> previousWeek) {
        List<String> packages = new ArrayList<>(currentWeek.keySet());
        for (String packageName : previousWeek.keySet()) if (!packages.contains(packageName)) packages.add(packageName);
        packages.sort((a, b) -> Long.compare(currentWeek.getOrDefault(b, 0L), currentWeek.getOrDefault(a, 0L)));
        int count = Math.min(5, packages.size());
        String[] labels = new String[count];
        float[] current = new float[count], previous = new float[count];
        long max = count == 0 ? 0 : currentWeek.getOrDefault(packages.get(0), 0L);
        for (int i = 0; i < count; i++) {
            String packageName = packages.get(i);
            long currentTime = currentWeek.getOrDefault(packageName, 0L);
            long previousTime = previousWeek.getOrDefault(packageName, 0L);
            String label = appLabel(packageName);
            labels[i] = label.length() > 10 ? label.substring(0, 9) + "…" : label;
            current[i] = currentTime / 3600000f;
            previous[i] = previousTime / 3600000f;
            addUsageRow(packageName, label, currentTime, previousTime, max);
        }
        appChart.setComparison(labels, current, previous, getString(R.string.this_week), getString(R.string.previous_week));
        if (packages.isEmpty()) appUsageList.addView(emptyLabel(getString(R.string.usage_no_data)));
    }

    private String appLabel(String packageName) {
        try { return getPackageManager().getApplicationLabel(
                getPackageManager().getApplicationInfo(packageName, 0)).toString(); }
        catch (Exception e) { return packageName; }
    }

    private void addUsageRow(String packageName, String label, long currentTime, long previousTime, long max) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.VERTICAL);
        row.setPadding(0, dp(10), 0, dp(10));
        LinearLayout heading = new LinearLayout(this);
        heading.setOrientation(LinearLayout.HORIZONTAL);
        heading.setGravity(android.view.Gravity.CENTER_VERTICAL);
        ImageView icon = new ImageView(this);
        icon.setContentDescription(label);
        try {
            icon.setImageDrawable(getPackageManager().getApplicationIcon(packageName));
        } catch (Exception ignored) {
            icon.setImageResource(android.R.drawable.sym_def_app_icon);
        }
        heading.addView(icon, new LinearLayout.LayoutParams(dp(28), dp(28)));
        TextView title = new TextView(this);
        title.setText(getString(R.string.app_week_usage_format, label,
                formatDuration(currentTime), formatDuration(previousTime)));
        title.setTextSize(15);
        title.setTextColor(ContextCompat.getColor(this,
                com.google.android.material.R.color.material_on_surface_emphasis_medium));
        LinearLayout.LayoutParams titleParams = new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        titleParams.leftMargin = dp(10);
        heading.addView(title, titleParams);
        row.addView(heading);
        LinearProgressIndicator bar = new LinearProgressIndicator(this);
        bar.setMax(100);
        bar.setProgressCompat((int) (currentTime * 100 / Math.max(1, max)), false);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, dp(6));
        params.topMargin = dp(8);
        row.addView(bar, params);
        appUsageList.addView(row);
    }


    private TextView emptyLabel(String text) {
        TextView view = new TextView(this); view.setText(text); view.setTextColor(0xff777777); return view;
    }
    private void clearCharts() {
        weekdayChart.setData(new String[0], new float[0], "");
        weekChart.setData(new String[0], new float[0], "");
        appChart.setData(new String[0], new float[0], "");
    }
    private Calendar atMidnight(Calendar c) {
        c.set(Calendar.HOUR_OF_DAY, 0); c.set(Calendar.MINUTE, 0);
        c.set(Calendar.SECOND, 0); c.set(Calendar.MILLISECOND, 0); return c;
    }
    private String formatDuration(long millis) {
        long minutes = millis / 60000;
        return minutes >= 60 ? getString(R.string.duration_hours_minutes, minutes / 60, minutes % 60)
                : getString(R.string.duration_minutes, minutes);
    }
    private int dp(int value) { return (int) (value * getResources().getDisplayMetrics().density + .5f); }
    private void openBatteryUsage() {
        try { startActivity(new Intent("android.intent.action.POWER_USAGE_SUMMARY")); }
        catch (Exception e) { startActivity(new Intent(Settings.ACTION_BATTERY_SAVER_SETTINGS)); }
    }
    @Override public boolean onSupportNavigateUp() { finish(); return true; }
}
