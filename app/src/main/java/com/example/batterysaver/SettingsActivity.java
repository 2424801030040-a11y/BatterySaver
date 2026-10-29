package com.example.batterysaver;

import android.content.SharedPreferences;
import android.os.Bundle;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.slider.Slider;
import com.google.android.material.switchmaterial.SwitchMaterial;

public class SettingsActivity extends AppCompatActivity {

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
    }

    @Override
    public boolean onSupportNavigateUp() {
        finish();
        return true;
    }
}
