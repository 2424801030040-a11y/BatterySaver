package com.example.batterysaver;

import android.app.Activity;
import android.view.WindowManager;

/** Applies the selected battery mode to this app's window without changing system brightness. */
final class ModeBrightness {
    private ModeBrightness() {}

    static void apply(Activity activity) {
        String mode = activity.getSharedPreferences("prefs", Activity.MODE_PRIVATE)
                .getString("power_mode", "normal");
        android.content.SharedPreferences prefs = activity.getSharedPreferences("prefs", Activity.MODE_PRIVATE);
        float brightness;
        if ("saver".equals(mode)) brightness = prefs.getInt("saver_brightness", 55) / 100f;
        else if ("ultra".equals(mode)) brightness = prefs.getInt("ultra_brightness", 25) / 100f;
        else brightness = WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE;

        WindowManager.LayoutParams attributes = activity.getWindow().getAttributes();
        if (attributes.screenBrightness != brightness) {
            attributes.screenBrightness = brightness;
            activity.getWindow().setAttributes(attributes);
        }
    }
}
