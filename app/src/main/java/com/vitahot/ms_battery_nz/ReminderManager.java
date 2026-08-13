package com.vitahot.ms_battery_nz;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;
import android.util.Log;
import java.util.Calendar;

public class ReminderManager {

    private static final String PREFS_NAME = "reminder_prefs";
    private static final String REMINDER_TIME_KEY = "reminder_time";
    private static final int REMINDER_REQUEST_CODE = 100;
    private static final String TAG = "ReminderManager";

    public static void setDailyReminder(Context context, int hourOfDay, int minute) {
        Log.d(TAG, "setDailyReminder called for " + hourOfDay + ":" + String.format("%02d", minute));

        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        prefs.edit().putInt(REMINDER_TIME_KEY, hourOfDay * 60 + minute).apply();

        scheduleReminder(context, hourOfDay, minute);
    }

    public static void scheduleReminder(Context context, int hourOfDay, int minute) {
        Log.d(TAG, "scheduleReminder called");

        AlarmManager alarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        if (alarmManager == null) return;

        Intent intent = new Intent(context, ReminderBroadcastReceiver.class);
        intent.setAction("com.vitahot.ms_battery_nz.DAILY_REMINDER");

        PendingIntent pendingIntent = PendingIntent.getBroadcast(context, REMINDER_REQUEST_CODE, intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        Calendar calendar = Calendar.getInstance();
        calendar.set(Calendar.HOUR_OF_DAY, hourOfDay);
        calendar.set(Calendar.MINUTE, minute);
        calendar.set(Calendar.SECOND, 0);
        calendar.set(Calendar.MILLISECOND, 0);

        if (calendar.before(Calendar.getInstance())) {
            calendar.add(Calendar.DATE, 1);
        }

        long triggerTime = calendar.getTimeInMillis();
        Log.d(TAG, "Alarm will trigger at: " + calendar.getTime());

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                if (alarmManager.canScheduleExactAlarms()) {
                    alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerTime, pendingIntent);
                    Log.d(TAG, "Exact alarm scheduled (API 31+)");
                } else {
                    alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerTime, pendingIntent);
                    Log.d(TAG, "Inexact alarm scheduled (API 31+, no permission)");
                }
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerTime, pendingIntent);
                Log.d(TAG, "Exact alarm scheduled (API 23-30)");
            } else {
                alarmManager.setExact(AlarmManager.RTC_WAKEUP, triggerTime, pendingIntent);
                Log.d(TAG, "Exact alarm scheduled (Legacy)");
            }
        } catch (Exception e) {
            Log.e(TAG, "Error scheduling alarm", e);
        }
    }

    public static void rescheduleReminder(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        int time = prefs.getInt(REMINDER_TIME_KEY, -1);
        if (time != -1) {
            int hour = time / 60;
            int minute = time % 60;
            scheduleReminder(context, hour, minute);
        }
    }

    public static void cancelReminder(Context context) {
        AlarmManager alarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        if (alarmManager == null) return;
        
        Intent intent = new Intent(context, ReminderBroadcastReceiver.class);
        intent.setAction("com.vitahot.ms_battery_nz.DAILY_REMINDER");
        PendingIntent pendingIntent = PendingIntent.getBroadcast(context, REMINDER_REQUEST_CODE, intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        alarmManager.cancel(pendingIntent);
        Log.d(TAG, "Reminder cancelled");
    }
}