package com.vitahot.ms_battery_nz;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import androidx.core.app.NotificationCompat;

public class ReminderBroadcastReceiver extends BroadcastReceiver {

    private static final String CHANNEL_ID = "daily_reminder";
    private static final int NOTIFICATION_ID = 101;

    @Override
    public void onReceive(Context context, Intent intent) {
        String action = intent.getAction();
        android.util.Log.d("ReminderBroadcast", "onReceive triggered with action: " + action);

        if ("com.vitahot.ms_battery_nz.MORNING_REMINDER".equals(action)) {
            createMorningNotification(context);
        } else {
            createNotification(context);
        }

        // Reschedule for next day since we use non-repeating exact alarms for reliability
        ReminderManager.rescheduleReminder(context);
    }

    private void createMorningNotification(Context context) {
        Intent notificationIntent = new Intent(context, MainActivity.class);
        notificationIntent.putExtra("navigate_to", "measure");
        notificationIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);

        PendingIntent pendingIntent = PendingIntent.getActivity(context, 1, notificationIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        NotificationCompat.Builder builder = new NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
                .setContentTitle("Morning HRV Recording")
                .setContentText("Tap to record your HRV measurement for this morning.")
                .setAutoCancel(true)
                .setDefaults(NotificationCompat.DEFAULT_ALL)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setCategory(NotificationCompat.CATEGORY_REMINDER)
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                .setContentIntent(pendingIntent);

        NotificationManager manager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        if (manager == null) return;
        ensureChannel(manager);
        manager.notify(102, builder.build());
    }

    private void ensureChannel(NotificationManager manager) {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(CHANNEL_ID, "Daily Reminders",
                    NotificationManager.IMPORTANCE_HIGH);
            channel.setDescription("Reminder to log daily fatigue levels and record HRV");
            channel.enableLights(true);
            channel.enableVibration(true);
            channel.setLockscreenVisibility(android.app.Notification.VISIBILITY_PUBLIC);
            manager.createNotificationChannel(channel);
        }
    }

    private void createNotification(Context context) {
        // Intent to open app when notification is tapped
        Intent notificationIntent = new Intent(context, MainActivity.class);
        notificationIntent.putExtra("navigate_to", "symptoms");
        notificationIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);

        PendingIntent pendingIntent = PendingIntent.getActivity(context, 0, notificationIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        NotificationCompat.Builder builder = new NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
                .setContentTitle("Daily Fatigue Check")
                .setContentText("Tap to update your fatigue level for today.")
                .setAutoCancel(true)
                .setDefaults(NotificationCompat.DEFAULT_ALL)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setCategory(NotificationCompat.CATEGORY_REMINDER)
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                .setContentIntent(pendingIntent);

        NotificationManager manager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        if (manager == null) return;

        ensureChannel(manager);

        manager.notify(NOTIFICATION_ID, builder.build());
    }
}
