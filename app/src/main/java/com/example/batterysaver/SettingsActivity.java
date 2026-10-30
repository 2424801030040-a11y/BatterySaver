package com.example.batterysaver;

import android.Manifest;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.slider.Slider;
import com.google.android.material.switchmaterial.SwitchMaterial;

import androidx.core.content.ContextCompat;

public class SettingsActivity extends AppCompatActivity {

    @Override
    protected void onResume() {
        super.onResume();
        ModeBrightness.apply(this);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);

        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }

        SharedPreferences prefs = getSharedPreferences("prefs", MODE_PRIVATE);

        Slider slider = findViewById(R.id.sliderThreshold);
        TextView tv = findViewById(R.id.tvThreshold);
        SwitchMaterial swNotify = findViewById(R.id.swNotify);

        slider.setValueFrom(5);
        slider.setValueTo(50);
        slider.setStepSize(5);
        slider.setValue(prefs.getInt("threshold", 20));
        tv.setText(getString(R.string.threshold_format, (int) slider.getValue()));

        slider.addOnChangeListener((s, value, fromUser) -> {
            tv.setText(getString(R.string.threshold_format, (int) value));
            prefs.edit().putInt("threshold", (int) value).apply();
        });

        swNotify.setChecked(prefs.getBoolean("notify", true));
        swNotify.setOnCheckedChangeListener((btn, checked) ->
                prefs.edit().putBoolean("notify", checked).apply());

        Slider saverBrightness = findViewById(R.id.sliderSaverBrightness);
        TextView saverBrightnessLabel = findViewById(R.id.tvSaverBrightness);
        saverBrightness.setValue(prefs.getInt("saver_brightness", 55));
        updateSaverBrightnessLabel(saverBrightnessLabel, (int) saverBrightness.getValue());
        saverBrightness.addOnChangeListener((changedSlider, value, fromUser) -> {
            int percent = (int) value;
            updateSaverBrightnessLabel(saverBrightnessLabel, percent);
            prefs.edit().putInt("saver_brightness", percent).apply();
            ModeBrightness.apply(this);
        });

        Slider ultraBrightness = findViewById(R.id.sliderUltraBrightness);
        TextView ultraBrightnessLabel = findViewById(R.id.tvUltraBrightness);
        ultraBrightness.setValue(prefs.getInt("ultra_brightness", 25));
        updateUltraBrightnessLabel(ultraBrightnessLabel, (int) ultraBrightness.getValue());
        ultraBrightness.addOnChangeListener((changedSlider, value, fromUser) -> {
            int percent = (int) value;
            updateUltraBrightnessLabel(ultraBrightnessLabel, percent);
            prefs.edit().putInt("ultra_brightness", percent).apply();
            ModeBrightness.apply(this);
        });

        SwitchMaterial autoSaver = findViewById(R.id.swAutoSaver);
        Slider autoThreshold = findViewById(R.id.sliderAutoSaverThreshold);
        TextView autoThresholdLabel = findViewById(R.id.tvAutoSaverThreshold);
        autoThreshold.setValue(prefs.getInt("auto_saver_threshold", 20));
        updateAutoThresholdLabel(autoThresholdLabel, (int) autoThreshold.getValue());
        autoSaver.setChecked(prefs.getBoolean("auto_saver", false));
        autoThreshold.setEnabled(autoSaver.isChecked());
        autoSaver.setOnCheckedChangeListener((button, checked) -> {
            prefs.edit().putBoolean("auto_saver", checked).apply();
            autoThreshold.setEnabled(checked);
        });
        autoThreshold.addOnChangeListener((changedSlider, value, fromUser) -> {
            int percent = (int) value;
            updateAutoThresholdLabel(autoThresholdLabel, percent);
            prefs.edit().putInt("auto_saver_threshold", percent).apply();
        });

        SwitchMaterial thermalAlerts = findViewById(R.id.swThermalAlerts);
        Slider thermalThreshold = findViewById(R.id.sliderThermalThreshold);
        TextView thermalLabel = findViewById(R.id.tvThermalThreshold);
        thermalThreshold.setValue(prefs.getInt("thermal_threshold", 42));
        updateThermalLabel(thermalLabel, (int) thermalThreshold.getValue());
        thermalAlerts.setChecked(prefs.getBoolean("thermal_alerts", false));
        thermalThreshold.setEnabled(thermalAlerts.isChecked());
        thermalAlerts.setOnCheckedChangeListener((button, checked) -> {
            prefs.edit().putBoolean("thermal_alerts", checked)
                    .putBoolean("thermal_alert_sent", false).apply();
            thermalThreshold.setEnabled(checked);
            if (checked) requestNotificationPermission();
        });
        thermalThreshold.addOnChangeListener((changedSlider, value, fromUser) -> {
            int degrees = (int) value;
            updateThermalLabel(thermalLabel, degrees);
            prefs.edit().putInt("thermal_threshold", degrees)
                    .putBoolean("thermal_alert_sent", false).apply();
        });

        SwitchMaterial chargeAlert = findViewById(R.id.swChargeAlert);
        Slider chargeTarget = findViewById(R.id.sliderChargeTarget);
        TextView chargeTargetLabel = findViewById(R.id.tvChargeTarget);
        chargeTarget.setValue(prefs.getInt("charge_target", 80));
        updateChargeTargetLabel(chargeTargetLabel, (int) chargeTarget.getValue());
        chargeAlert.setChecked(prefs.getBoolean("charge_alert", false));
        chargeTarget.setEnabled(chargeAlert.isChecked());
        chargeAlert.setOnCheckedChangeListener((button, checked) -> {
            prefs.edit().putBoolean("charge_alert", checked)
                    .putBoolean("charge_alert_sent", false).apply();
            chargeTarget.setEnabled(checked);
            if (checked) requestNotificationPermission();
        });
        chargeTarget.addOnChangeListener((changedSlider, value, fromUser) -> {
            int percent = (int) value;
            updateChargeTargetLabel(chargeTargetLabel, percent);
            prefs.edit().putInt("charge_target", percent)
                    .putBoolean("charge_alert_sent", false).apply();
        });
    }

    private void updateSaverBrightnessLabel(TextView view, int value) {
        view.setText(getString(R.string.saver_brightness_format, value));
    }

    private void updateUltraBrightnessLabel(TextView view, int value) {
        view.setText(getString(R.string.ultra_brightness_format, value));
    }

    private void updateAutoThresholdLabel(TextView view, int value) {
        view.setText(getString(R.string.auto_saver_threshold_format, value));
    }

    private void updateThermalLabel(TextView view, int value) {
        view.setText(getString(R.string.thermal_threshold_format, value));
    }

    private void updateChargeTargetLabel(TextView view, int value) {
        view.setText(getString(R.string.charge_target_format, value));
    }

    private void requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(this,
                Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, 901);
        }
    }

    @Override
    public boolean onSupportNavigateUp() {
        finish();
        return true;
    }
}
