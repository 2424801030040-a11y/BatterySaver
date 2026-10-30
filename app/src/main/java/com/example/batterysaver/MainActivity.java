package com.example.batterysaver;

import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.os.BatteryManager;
import android.os.Bundle;
import android.provider.Settings;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AppCompatDelegate;

import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.android.material.chip.ChipGroup;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.progressindicator.CircularProgressIndicator;
import com.google.android.material.snackbar.Snackbar;

import java.util.List;

public class MainActivity extends AppCompatActivity {

    private CircularProgressIndicator progress;
    private TextView tvLevel, tvInfo, tvMode, tvModeLabel, tvEstimate, tvThermalAlert;
    private BottomNavigationView bottomNav;
    private ChipGroup modeChips;
    private ObjectAnimator pulse;
    private SharedPreferences prefs;
    private boolean suppressModeCallback;
    private int lastBatteryPct = -1;
    private boolean lastCharging;

    private int lastLevel = -1;
    private String lastStatus;
    private int lastTemperatureTenths = -1, lastVoltageMv = -1;
    private String lastPowerSource;
    private long lastDetailedSampleTime;
    private long lastEstimateRefreshTime;
    private int lastEstimatePct = -1;
    private boolean lastEstimateCharging;
    private boolean lowAlertShown = false;

    private final BroadcastReceiver batteryReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context c, Intent i) {
            int level = i.getIntExtra(BatteryManager.EXTRA_LEVEL, 0);
            int scale = i.getIntExtra(BatteryManager.EXTRA_SCALE, 100);
            int pct = level * 100 / scale;
            int status = i.getIntExtra(BatteryManager.EXTRA_STATUS, -1);
            int temperatureTenths = i.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0);
            float temp = temperatureTenths / 10f;
            int volt = i.getIntExtra(BatteryManager.EXTRA_VOLTAGE, 0);
            int plug = i.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0);

            boolean charging = status == BatteryManager.BATTERY_STATUS_CHARGING
                    || (status == BatteryManager.BATTERY_STATUS_FULL && plug != 0);
            lastBatteryPct = pct;
            lastCharging = charging;

            progress.setProgressCompat(pct, true);
            tvLevel.setText(pct + "%");

            String source;
            if (plug == BatteryManager.BATTERY_PLUGGED_USB) source = getString(R.string.source_usb);
            else if (plug == BatteryManager.BATTERY_PLUGGED_AC) source = getString(R.string.source_ac);
            else if (plug == BatteryManager.BATTERY_PLUGGED_WIRELESS) source = getString(R.string.source_wireless);
            else source = getString(R.string.source_battery);

            tvInfo.setText(getString(R.string.battery_info_format,
                    getString(charging ? R.string.status_charging : R.string.status_discharging),
                    source, temp, volt));

            updateAutomaticSaver(pct, charging);

            togglePulse(charging);

            // Log each new level
            String batteryStatus = getString(charging
                    ? R.string.status_charging : R.string.status_discharging);
            long now = System.currentTimeMillis();
            boolean sensorChanged = lastTemperatureTenths < 0
                    || Math.abs(temperatureTenths - lastTemperatureTenths) >= 10
                    || Math.abs(volt - lastVoltageMv) >= 50
                    || !source.equals(lastPowerSource);
            boolean periodicSampleDue = now - lastDetailedSampleTime >= 15L * 60 * 1000;
            if (pct != lastLevel || !batteryStatus.equals(lastStatus) || sensorChanged || periodicSampleDue) {
                BatteryLog.add(pct, batteryStatus, temperatureTenths, volt, source);
                lastLevel = pct;
                lastStatus = batteryStatus;
                lastTemperatureTenths = temperatureTenths;
                lastVoltageMv = volt;
                lastPowerSource = source;
                lastDetailedSampleTime = now;
            }
            updateSafetyAlerts(temp, pct, charging);
            if (pct != lastEstimatePct || charging != lastEstimateCharging
                    || now - lastEstimateRefreshTime >= 5L * 60 * 1000) {
                updateRemainingEstimate(pct, charging);
                lastEstimatePct = pct;
                lastEstimateCharging = charging;
                lastEstimateRefreshTime = now;
            }

            // Low-battery alert (once per drop below threshold)
            int threshold = prefs.getInt("threshold", 20);
            boolean notify = prefs.getBoolean("notify", true);
            if (!charging && pct <= threshold) {
                if (notify && !lowAlertShown) {
                    Toast.makeText(MainActivity.this,
                            getString(R.string.low_battery_toast, pct), Toast.LENGTH_LONG).show();
                    lowAlertShown = true;
                }
            } else {
                lowAlertShown = false;
            }
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        prefs = getSharedPreferences("prefs", MODE_PRIVATE);
        BatteryLog.initialize(getApplicationContext());
        if (!prefs.contains("manual_power_mode")) {
            prefs.edit().putString("manual_power_mode", prefs.getString("power_mode", "normal")).apply();
        }

        progress = findViewById(R.id.progressBattery);
        tvLevel = findViewById(R.id.tvLevel);
        tvInfo = findViewById(R.id.tvInfo);
        tvMode = findViewById(R.id.tvModeDescription);
        tvModeLabel = findViewById(R.id.tvMode);
        tvEstimate = findViewById(R.id.tvEstimate);
        tvThermalAlert = findViewById(R.id.tvThermalAlert);
        bottomNav = findViewById(R.id.bottomNav);

        // ---- Power modes (event handling) ----
        modeChips = findViewById(R.id.chipModes);
        modeChips.setOnCheckedStateChangeListener((group, ids) -> {
            if (suppressModeCallback) return;
            if (ids.isEmpty()) return;
            int id = ids.get(0);
            String msg;
            String selectedMode;
            if (id == R.id.chipSaver) {
                tvModeLabel.setText(R.string.mode_saver);
                msg = getString(R.string.mode_saver_description);
                selectedMode = "saver";
            } else if (id == R.id.chipUltra) {
                tvModeLabel.setText(R.string.mode_ultra);
                msg = getString(R.string.mode_ultra_description);
                selectedMode = "ultra";
            } else {
                tvModeLabel.setText(R.string.mode_normal);
                msg = getString(R.string.mode_normal_description);
                selectedMode = "normal";
            }
            prefs.edit().putString("manual_power_mode", selectedMode)
                    .putString("power_mode", selectedMode).apply();
            ModeBrightness.apply(this);
            tvMode.setText(msg);
            Snackbar.make(group, msg, Snackbar.LENGTH_SHORT).show();
            if (lastBatteryPct >= 0) updateAutomaticSaver(lastBatteryPct, lastCharging);
        });
        String savedMode = prefs.getString("power_mode", "normal");
        suppressModeCallback = true;
        modeChips.check("saver".equals(savedMode) ? R.id.chipSaver
                : "ultra".equals(savedMode) ? R.id.chipUltra : R.id.chipNormal);
        suppressModeCallback = false;
        ModeBrightness.apply(this);

        // ---- Shortcuts (implicit Intents) ----
        findViewById(R.id.btnDisplay).setOnClickListener(v ->
                startActivity(new Intent(Settings.ACTION_DISPLAY_SETTINGS)));
        findViewById(R.id.btnUsage).setOnClickListener(v ->
                startActivity(new Intent(this, UsageActivity.class)));
        findViewById(R.id.btnBatteryReport).setOnClickListener(v ->
                startActivity(new Intent(this, BatteryAnalyticsActivity.class)));

        // ---- Bottom navigation (explicit Intents) ----
        bottomNav.setOnItemSelectedListener(item -> {
            int id = item.getItemId();
            if (id == R.id.nav_history) {
                startActivity(new Intent(this, HistoryActivity.class));
                overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
            } else if (id == R.id.nav_settings) {
                startActivity(new Intent(this, SettingsActivity.class));
                overridePendingTransition(android.R.anim.slide_in_left, android.R.anim.fade_out);
            }
            return true;
        });
    }

    @Override
    protected void onStart() {
        super.onStart();
        registerReceiver(batteryReceiver, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
    }

    @Override
    protected void onResume() {
        super.onResume();
        ModeBrightness.apply(this);
        bottomNav.getMenu().findItem(R.id.nav_dashboard).setChecked(true);
    }

    @Override
    protected void onStop() {
        super.onStop();
        unregisterReceiver(batteryReceiver);
    }

    private void togglePulse(boolean charging) {
        if (charging && pulse == null) {
            pulse = ObjectAnimator.ofFloat(tvLevel, "alpha", 1f, 0.4f);
            pulse.setDuration(900);
            pulse.setRepeatMode(ValueAnimator.REVERSE);
            pulse.setRepeatCount(ValueAnimator.INFINITE);
            pulse.start();
        } else if (!charging && pulse != null) {
            pulse.cancel();
            pulse = null;
            tvLevel.setAlpha(1f);
        }
    }

    private void updateRemainingEstimate(int currentLevel, boolean charging) {
        if (charging) {
            tvEstimate.setText(R.string.estimate_charging);
            return;
        }
        List<BatteryLog.Entry> entries = BatteryLog.snapshot();
        long now = System.currentTimeMillis();
        BatteryLog.Entry latest = null;
        BatteryLog.Entry older = null;
        String discharging = getString(R.string.status_discharging);
        for (int i = entries.size() - 1; i >= 0; i--) {
            BatteryLog.Entry entry = entries.get(i);
            if (getString(R.string.status_charging).equals(entry.status)) break;
            if (!discharging.equals(entry.status) || now - entry.time > 12L * 60 * 60 * 1000) continue;
            if (latest == null) latest = entry;
            else if (entry.level >= latest.level + 2 && latest.time - entry.time >= 20L * 60 * 1000) {
                older = entry;
            }
        }
        if (latest == null || older == null) {
            tvEstimate.setText(R.string.estimate_learning);
            return;
        }
        int drop = older.level - latest.level;
        long elapsedMinutes = (latest.time - older.time) / 60000;
        if (drop <= 0 || elapsedMinutes <= 0) {
            tvEstimate.setText(R.string.estimate_learning);
            return;
        }
        long remainingMinutes = Math.round(currentLevel * (double) elapsedMinutes / drop);
        tvEstimate.setText(getString(R.string.estimate_remaining, remainingMinutes / 60,
                remainingMinutes % 60));
    }

    private void updateSafetyAlerts(float temperature, int batteryPercent, boolean charging) {
        int thermalThreshold = prefs.getInt("thermal_threshold", 42);
        boolean tooHot = temperature >= thermalThreshold;
        tvThermalAlert.setVisibility(tooHot ? View.VISIBLE : View.GONE);
        if (tooHot) tvThermalAlert.setText(getString(R.string.dashboard_thermal_warning, temperature));
        if (temperature > 0 && temperature <= thermalThreshold - 3) {
            prefs.edit().putBoolean("thermal_alert_sent", false).apply();
        } else if (tooHot && prefs.getBoolean("thermal_alerts", false)
                && !prefs.getBoolean("thermal_alert_sent", false)) {
            boolean posted = BatteryNotifications.post(this, 4101,
                    getString(R.string.notification_thermal_title),
                    getString(R.string.notification_thermal_message, temperature));
            if (posted) prefs.edit().putBoolean("thermal_alert_sent", true).apply();
        }

        if (!charging) {
            prefs.edit().putBoolean("charge_alert_sent", false).apply();
        } else if (prefs.getBoolean("charge_alert", false)
                && batteryPercent >= prefs.getInt("charge_target", 80)
                && !prefs.getBoolean("charge_alert_sent", false)) {
            boolean posted = BatteryNotifications.post(this, 4102,
                    getString(R.string.notification_charge_title),
                    getString(R.string.notification_charge_message, batteryPercent));
            if (posted) prefs.edit().putBoolean("charge_alert_sent", true).apply();
        }
    }

    private void updateAutomaticSaver(int batteryPercent, boolean charging) {
        boolean autoSaver = prefs.getBoolean("auto_saver", false)
                && !charging && batteryPercent <= prefs.getInt("auto_saver_threshold", 20);
        String targetMode = autoSaver ? "saver" : prefs.getString("manual_power_mode",
                prefs.getString("power_mode", "normal"));
        String currentMode = prefs.getString("power_mode", "normal");
        if (!targetMode.equals(currentMode)) prefs.edit().putString("power_mode", targetMode).apply();
        suppressModeCallback = true;
        modeChips.check("saver".equals(targetMode) ? R.id.chipSaver
                : "ultra".equals(targetMode) ? R.id.chipUltra : R.id.chipNormal);
        suppressModeCallback = false;
        if ("saver".equals(targetMode)) {
            tvModeLabel.setText(R.string.mode_saver);
            tvMode.setText(autoSaver ? R.string.mode_auto_saver_active : R.string.mode_saver_description);
        } else if ("ultra".equals(targetMode)) {
            tvModeLabel.setText(R.string.mode_ultra);
            tvMode.setText(R.string.mode_ultra_description);
        } else {
            tvModeLabel.setText(R.string.mode_normal);
            tvMode.setText(R.string.mode_normal_description);
        }
        ModeBrightness.apply(this);
    }

    // ---------------- Options Menu + Submenu ----------------
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.main_menu, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();
        if (id == R.id.action_settings) {
            startActivity(new Intent(this, SettingsActivity.class));
        } else if (id == R.id.theme_light) {
            AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO);
        } else if (id == R.id.theme_dark) {
            AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES);
        } else if (id == R.id.theme_system) {
            AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM);
        } else if (id == R.id.action_about) {
            new MaterialAlertDialogBuilder(this)
                    .setTitle(R.string.app_name)
                    .setMessage(R.string.about_message)
                    .setPositiveButton(R.string.button_ok, null)
                    .show();
        }
        return super.onOptionsItemSelected(item);
    }
}
