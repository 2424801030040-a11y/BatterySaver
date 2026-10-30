package com.example.batterysaver;

import android.content.Intent;
import android.content.IntentFilter;
import android.os.BatteryManager;
import android.os.Bundle;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class BatteryAnalyticsActivity extends AppCompatActivity {
    private static final long DAY_MS = 24L * 60 * 60 * 1000;

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        setContentView(R.layout.activity_battery_analytics);
        if (getSupportActionBar() != null) getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        BatteryLog.initialize(getApplicationContext());
        renderReport();
    }

    @Override protected void onResume() {
        super.onResume();
        ModeBrightness.apply(this);
    }

    private void renderReport() {
        long cutoff = System.currentTimeMillis() - 7 * DAY_MS;
        List<BatteryLog.Entry> recent = new ArrayList<>();
        for (BatteryLog.Entry entry : BatteryLog.snapshot()) if (entry.time >= cutoff) recent.add(entry);

        double dischargeDrop = 0, dischargeHours = 0;
        double chargeGain = 0, chargeHours = 0;
        double maxTemp = -1;
        int hotSamples = 0, sessions = 0;
        String charging = getString(R.string.status_charging);
        String discharging = getString(R.string.status_discharging);

        for (int i = 0; i < recent.size(); i++) {
            BatteryLog.Entry current = recent.get(i);
            if (current.temperatureTenths > 0) {
                double temperature = current.temperatureTenths / 10.0;
                maxTemp = Math.max(maxTemp, temperature);
                if (temperature >= 45.0) hotSamples++;
            }
            if (charging.equals(current.status)
                    && (i == 0 || !charging.equals(recent.get(i - 1).status))) sessions++;
            if (i == 0) continue;

            BatteryLog.Entry previous = recent.get(i - 1);
            long elapsedMinutes = (current.time - previous.time) / 60000;
            if (elapsedMinutes < 5 || elapsedMinutes > 6 * 60) continue;
            double elapsedHours = elapsedMinutes / 60.0;
            if (discharging.equals(previous.status) && discharging.equals(current.status)
                    && current.level < previous.level) {
                dischargeDrop += previous.level - current.level;
                dischargeHours += elapsedHours;
            } else if (charging.equals(previous.status) && charging.equals(current.status)
                    && current.level > previous.level) {
                chargeGain += current.level - previous.level;
                chargeHours += elapsedHours;
            }
        }

        ((TextView) findViewById(R.id.dischargeRate)).setText(dischargeHours > 0
                ? String.format(Locale.getDefault(), "%.1f%% mỗi giờ", dischargeDrop / dischargeHours)
                : getString(R.string.metric_no_value));
        ((TextView) findViewById(R.id.chargeRate)).setText(chargeHours > 0
                ? String.format(Locale.getDefault(), "%.1f%% mỗi giờ", chargeGain / chargeHours)
                : getString(R.string.metric_no_value));
        ((TextView) findViewById(R.id.equivalentCycles)).setText(dischargeDrop > 0
                ? getString(R.string.metric_cycles_value, dischargeDrop / 100.0)
                : getString(R.string.metric_no_value));
        ((TextView) findViewById(R.id.maxTemperature)).setText(maxTemp >= 0
                ? getString(R.string.metric_temperature_value, maxTemp)
                : getString(R.string.metric_no_value));
        ((TextView) findViewById(R.id.highTemperatureSamples)).setText(
                getString(R.string.metric_samples_value, hotSamples));
        ((TextView) findViewById(R.id.chargingSessions)).setText(
                getString(R.string.metric_sessions_value, sessions));
        ((TextView) findViewById(R.id.sampleCount)).setText(
                getString(R.string.metric_samples_value, recent.size()));

        TextView hint = findViewById(R.id.reportHint);
        hint.setText(dischargeHours == 0 || chargeHours == 0
                ? getString(R.string.battery_report_insufficient)
                : getString(R.string.battery_report_note));
        renderChargeCoach(chargeHours > 0 ? chargeGain / chargeHours : 0);
        renderDrainInsight(recent, cutoff, System.currentTimeMillis(), discharging);
    }

    private void renderChargeCoach(double chargePercentPerHour) {
        Intent battery = registerReceiver(null, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
        int level = 0;
        boolean chargingNow = false;
        if (battery != null) {
            int raw = battery.getIntExtra(BatteryManager.EXTRA_LEVEL, 0);
            int scale = battery.getIntExtra(BatteryManager.EXTRA_SCALE, 100);
            level = scale > 0 ? raw * 100 / scale : raw;
            int status = battery.getIntExtra(BatteryManager.EXTRA_STATUS, -1);
            int plug = battery.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0);
            chargingNow = status == BatteryManager.BATTERY_STATUS_CHARGING
                    || (status == BatteryManager.BATTERY_STATUS_FULL && plug != 0);
        }
        TextView status = findViewById(R.id.chargeCoachStatus);
        TextView to80 = findViewById(R.id.chargeTo80);
        TextView to100 = findViewById(R.id.chargeTo100);
        if (!chargingNow) {
            status.setText(R.string.charge_not_active);
            to80.setText(getString(R.string.charge_to_80, getString(R.string.metric_no_value)));
            to100.setText(getString(R.string.charge_to_100, getString(R.string.metric_no_value)));
            return;
        }
        status.setText(getString(R.string.charge_current_level, level));
        if (chargePercentPerHour <= 0) {
            to80.setText(getString(R.string.charge_to_80, getString(R.string.charge_rate_learning)));
            to100.setText(getString(R.string.charge_to_100, getString(R.string.charge_rate_learning)));
            return;
        }
        to80.setText(getString(R.string.charge_to_80, formatEta(Math.max(0, 80 - level), chargePercentPerHour)));
        to100.setText(getString(R.string.charge_to_100, formatEta(Math.max(0, 100 - level), chargePercentPerHour)));
    }

    private String formatEta(int remainingPercent, double percentPerHour) {
        long minutes = Math.round(remainingPercent * 60.0 / percentPerHour);
        if (minutes == 0) return getString(R.string.charge_target_reached);
        return getString(R.string.charge_eta_format, minutes / 60, minutes % 60);
    }

    private void renderDrainInsight(List<BatteryLog.Entry> entries, long weekStart, long now,
                                    String dischargingStatus) {
        long recentStart = now - 2L * 60 * 60 * 1000;
        Rate recentRate = calculateRate(entries, recentStart, now, dischargingStatus, false);
        Rate baselineRate = calculateRate(entries, weekStart, recentStart, dischargingStatus, false);
        TextView insight = findViewById(R.id.drainInsight);
        if (recentRate.samples < 2 || baselineRate.samples < 2 || baselineRate.value <= 0) {
            insight.setText(R.string.drain_insight_learning);
            return;
        }
        if (recentRate.value >= baselineRate.value * 1.5) {
            int increase = Math.round((float) ((recentRate.value / baselineRate.value - 1) * 100));
            insight.setText(getString(R.string.drain_insight_high, recentRate.value, increase));
            insight.setTextColor(0xffb3261e);
        } else {
            insight.setText(getString(R.string.drain_insight_normal, recentRate.value, baselineRate.value));
        }
    }

    private Rate calculateRate(List<BatteryLog.Entry> entries, long start, long end,
                               String status, boolean chargingRate) {
        double change = 0, hours = 0;
        int samples = 0;
        for (int i = 1; i < entries.size(); i++) {
            BatteryLog.Entry previous = entries.get(i - 1);
            BatteryLog.Entry current = entries.get(i);
            if (previous.time < start || current.time > end
                    || !status.equals(previous.status) || !status.equals(current.status)) continue;
            long elapsed = (current.time - previous.time) / 60000;
            if (elapsed < 5 || elapsed > 6 * 60) continue;
            int delta = chargingRate ? current.level - previous.level : previous.level - current.level;
            if (delta <= 0) continue;
            change += delta;
            hours += elapsed / 60.0;
            samples++;
        }
        return new Rate(hours > 0 ? change / hours : 0, samples);
    }

    private static final class Rate {
        final double value;
        final int samples;
        Rate(double value, int samples) { this.value = value; this.samples = samples; }
    }

    @Override public boolean onSupportNavigateUp() { finish(); return true; }
}
