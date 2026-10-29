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
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AppCompatDelegate;

import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.android.material.chip.ChipGroup;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.progressindicator.CircularProgressIndicator;
import com.google.android.material.snackbar.Snackbar;

import java.util.Locale;

public class MainActivity extends AppCompatActivity {

    private CircularProgressIndicator progress;
    private TextView tvLevel, tvInfo, tvMode, tvModeLabel;
    private BottomNavigationView bottomNav;
    private ObjectAnimator pulse;
    private SharedPreferences prefs;

    private int lastLevel = -1;
    private boolean lowAlertShown = false;

    private final BroadcastReceiver batteryReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context c, Intent i) {
            int level = i.getIntExtra(BatteryManager.EXTRA_LEVEL, 0);
            int scale = i.getIntExtra(BatteryManager.EXTRA_SCALE, 100);
            int pct = level * 100 / scale;
            int status = i.getIntExtra(BatteryManager.EXTRA_STATUS, -1);
            float temp = i.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0) / 10f;
            int volt = i.getIntExtra(BatteryManager.EXTRA_VOLTAGE, 0);
            int plug = i.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0);

            boolean charging = status == BatteryManager.BATTERY_STATUS_CHARGING
                    || status == BatteryManager.BATTERY_STATUS_FULL;

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

            togglePulse(charging);

            // Log each new level
            if (pct != lastLevel) {
                BatteryLog.add(pct, getString(charging
                        ? R.string.status_charging : R.string.status_discharging));
                lastLevel = pct;
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

        progress = findViewById(R.id.progressBattery);
        tvLevel = findViewById(R.id.tvLevel);
        tvInfo = findViewById(R.id.tvInfo);
        tvMode = findViewById(R.id.tvModeDescription);
        tvModeLabel = findViewById(R.id.tvMode);
        bottomNav = findViewById(R.id.bottomNav);

        // ---- Power modes (event handling) ----
        ChipGroup modes = findViewById(R.id.chipModes);
        modes.setOnCheckedStateChangeListener((group, ids) -> {
            if (ids.isEmpty()) return;
            int id = ids.get(0);
            String msg;
            if (id == R.id.chipSaver) {
                tvModeLabel.setText(R.string.mode_saver);
                msg = getString(R.string.mode_saver_description);
            } else if (id == R.id.chipUltra) {
                tvModeLabel.setText(R.string.mode_ultra);
                msg = getString(R.string.mode_ultra_description);
            } else {
                tvModeLabel.setText(R.string.mode_normal);
                msg = getString(R.string.mode_normal_description);
            }
            tvMode.setText(msg);
            Snackbar.make(group, msg, Snackbar.LENGTH_SHORT).show();
        });

        // ---- Shortcuts (implicit Intents) ----
        findViewById(R.id.btnDisplay).setOnClickListener(v ->
                startActivity(new Intent(Settings.ACTION_DISPLAY_SETTINGS)));
        findViewById(R.id.btnUsage).setOnClickListener(v ->
                startActivity(new Intent(this, UsageActivity.class)));

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
