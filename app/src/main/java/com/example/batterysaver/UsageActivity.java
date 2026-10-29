package com.example.batterysaver;

import android.app.AppOpsManager;
import android.app.usage.UsageStats;
import android.app.usage.UsageStatsManager;
import android.content.Intent;
import android.os.BatteryManager;
import android.os.Bundle;
import android.os.PowerManager;
import android.provider.Settings;
import android.net.Uri;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import com.google.android.material.progressindicator.LinearProgressIndicator;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public class UsageActivity extends AppCompatActivity {
    private LinearLayout appUsageList;
    private TextView usageHint;
    private Button grantAccess;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_usage);
        if (getSupportActionBar() != null) getSupportActionBar().setDisplayHomeAsUpEnabled(true);

        appUsageList = findViewById(R.id.appUsageList);
        usageHint = findViewById(R.id.usageHint);
        grantAccess = findViewById(R.id.grantUsageAccess);
        grantAccess.setOnClickListener(v -> startActivity(new Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)));

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
        ((TextView) findViewById(R.id.usageBatteryStatus)).setText(charging
                ? R.string.status_charging : R.string.status_discharging);
        PowerManager powerManager = (PowerManager) getSystemService(POWER_SERVICE);
        ((TextView) findViewById(R.id.saverState)).setText(getString(
                powerManager != null && powerManager.isPowerSaveMode()
                        ? R.string.saver_enabled : R.string.saver_disabled));
        findViewById(R.id.openBatterySettings).setOnClickListener(v ->
                startActivity(new Intent(Settings.ACTION_BATTERY_SAVER_SETTINGS)));
        findViewById(R.id.openBackgroundSettings).setOnClickListener(v -> {
            Intent details = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                    Uri.parse("package:" + getPackageName()));
            startActivity(details);
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        loadUsage();
    }

    private boolean hasUsageAccess() {
        AppOpsManager appOps = (AppOpsManager) getSystemService(APP_OPS_SERVICE);
        return appOps != null && appOps.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS,
                android.os.Process.myUid(), getPackageName()) == AppOpsManager.MODE_ALLOWED;
    }

    private void loadUsage() {
        boolean allowed = hasUsageAccess();
        grantAccess.setVisibility(allowed ? android.view.View.GONE : android.view.View.VISIBLE);
        appUsageList.removeAllViews();
        if (!allowed) {
            usageHint.setText(R.string.usage_access_required);
            return;
        }

        long end = System.currentTimeMillis();
        long start = end - 24L * 60 * 60 * 1000;
        UsageStatsManager manager = (UsageStatsManager) getSystemService(USAGE_STATS_SERVICE);
        List<UsageStats> stats = manager == null ? new ArrayList<>()
                : manager.queryUsageStats(UsageStatsManager.INTERVAL_DAILY, start, end);
        if (stats == null) stats = new ArrayList<>();
        stats.removeIf(s -> s.getTotalTimeInForeground() <= 0 || s.getPackageName().equals(getPackageName()));
        stats.sort(Comparator.comparingLong(UsageStats::getTotalTimeInForeground).reversed());

        if (stats.isEmpty()) {
            usageHint.setText(R.string.usage_no_data);
            return;
        }
        usageHint.setText(R.string.usage_last_day);
        long max = stats.get(0).getTotalTimeInForeground();
        int count = Math.min(stats.size(), 6);
        for (int i = 0; i < count; i++) {
            UsageStats item = stats.get(i);
            String label;
            try {
                label = getPackageManager().getApplicationLabel(
                        getPackageManager().getApplicationInfo(item.getPackageName(), 0)).toString();
            } catch (Exception e) {
                label = item.getPackageName();
            }
            addUsageRow(label, item.getTotalTimeInForeground(), max);
        }
    }

    private void addUsageRow(String label, long time, long max) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.VERTICAL);
        row.setPadding(0, dp(10), 0, dp(10));
        TextView title = new TextView(this);
        title.setText(getString(R.string.app_usage_format, label, formatDuration(time)));
        title.setTextSize(15);
        title.setTextColor(ContextCompat.getColor(this, com.google.android.material.R.color.material_on_surface_emphasis_medium));
        row.addView(title);
        LinearProgressIndicator bar = new LinearProgressIndicator(this);
        bar.setMax(100);
        bar.setProgressCompat((int) Math.max(1, time * 100 / Math.max(1, max)), false);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(6));
        params.topMargin = dp(8);
        row.addView(bar, params);
        appUsageList.addView(row);
    }

    private String formatDuration(long millis) {
        long minutes = millis / 60000;
        return minutes >= 60 ? getString(R.string.duration_hours_minutes, minutes / 60, minutes % 60)
                : getString(R.string.duration_minutes, minutes);
    }

    private int dp(int value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }

    @Override
    public boolean onSupportNavigateUp() {
        finish();
        return true;
    }
}
