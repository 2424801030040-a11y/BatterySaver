package com.example.batterysaver;

import android.Manifest;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Build;

import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;
import androidx.core.content.ContextCompat;

final class BatteryNotifications {
    private static final String CHANNEL_ID = "battery_safety";

    private BatteryNotifications() {}

    static boolean post(Context context, int id, String title, String message) {
        if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(context,
                Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return false;
        NotificationManagerCompat manager = NotificationManagerCompat.from(context);
        if (!manager.areNotificationsEnabled()) return false;
        if (Build.VERSION.SDK_INT >= 26) {
            NotificationManager system = context.getSystemService(NotificationManager.class);
            if (system != null && system.getNotificationChannel(CHANNEL_ID) == null) {
                system.createNotificationChannel(new NotificationChannel(CHANNEL_ID,
                        context.getString(R.string.notification_channel_name), NotificationManager.IMPORTANCE_DEFAULT));
            }
        }
        try {
            manager.notify(id, new NotificationCompat.Builder(context, CHANNEL_ID)
                    .setSmallIcon(android.R.drawable.ic_dialog_info)
                    .setContentTitle(title)
                    .setContentText(message)
                    .setStyle(new NotificationCompat.BigTextStyle().bigText(message))
                    .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                    .setAutoCancel(true)
                    .build());
            return true;
        } catch (SecurityException e) {
            return false;
        }
    }
}
