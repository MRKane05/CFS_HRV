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
    private static final String PROMPT_SHOWN_KEY = "reminder_prompt_shown";
    private static final String SETUP_PENDING_KEY = "reminder_setup_pending";
    private static final String WELCOME_SHOWN_KEY = "welcome_notice_shown";
    private static final String SYMPTOMS_NOTICE_SHOWN_KEY = "symptoms_notice_shown";
    private static final String POSITION_NOTICE_SHOWN_KEY = "position_notice_shown";
    private static final String EXPOSURE_AUTO_KEY = "exposure_auto_mode";
    private static final String EXPOSURE_STORED_INDEX_KEY = "exposure_stored_index";

    public static boolean hasPromptBeenShown(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        return prefs.getBoolean(PROMPT_SHOWN_KEY, false);
    }

    public static void setPromptShown(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        prefs.edit().putBoolean(PROMPT_SHOWN_KEY, true).apply();
    }

    public static boolean hasWelcomeNoticeBeenShown(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        return prefs.getBoolean(WELCOME_SHOWN_KEY, false);
    }

    public static void setWelcomeNoticeShown(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        prefs.edit().putBoolean(WELCOME_SHOWN_KEY, true).apply();
    }

    public static boolean hasSymptomsNoticeBeenShown(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        return prefs.getBoolean(SYMPTOMS_NOTICE_SHOWN_KEY, false);
    }

    public static void setSymptomsNoticeShown(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        prefs.edit().putBoolean(SYMPTOMS_NOTICE_SHOWN_KEY, true).apply();
    }

    public static boolean hasPositionNoticeBeenShown(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        return prefs.getBoolean(POSITION_NOTICE_SHOWN_KEY, false);
    }

    public static void setPositionNoticeShown(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        prefs.edit().putBoolean(POSITION_NOTICE_SHOWN_KEY, true).apply();
    }

    public static boolean isExposureAutoMode(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        return prefs.getBoolean(EXPOSURE_AUTO_KEY, true);
    }

    public static void setExposureAutoMode(Context context, boolean auto) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        prefs.edit().putBoolean(EXPOSURE_AUTO_KEY, auto).apply();
    }

    public static int getStoredExposureIndex(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        return prefs.getInt(EXPOSURE_STORED_INDEX_KEY, 0);
    }

    public static void setStoredExposureIndex(Context context, int index) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        prefs.edit().putInt(EXPOSURE_STORED_INDEX_KEY, index).apply();
    }

    public static void setSetupPending(Context context, boolean pending) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        prefs.edit().putBoolean(SETUP_PENDING_KEY, pending).apply();
    }

    public static boolean isSetupPending(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        return prefs.getBoolean(SETUP_PENDING_KEY, false);
    }

    public static void setDailyReminder(Context context, int hourOfDay, int minute) {
        Log.d(TAG, "setDailyReminder called for " + hourOfDay + ":" + String.format("%02d", minute));

        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        prefs.edit().putInt(REMINDER_TIME_KEY, hourOfDay * 60 + minute).apply();
        prefs.edit().putBoolean(SETUP_PENDING_KEY, false).apply(); // Clear pending on success

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

    public static boolean isReminderSet(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        return prefs.getInt(REMINDER_TIME_KEY, -1) != -1;
    }

    public static boolean cancelReminder(Context context) {
        AlarmManager alarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        if (alarmManager == null) return false;

        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        boolean wasSet = prefs.getInt(REMINDER_TIME_KEY, -1) != -1;

        Intent intent = new Intent(context, ReminderBroadcastReceiver.class);
        intent.setAction("com.vitahot.ms_battery_nz.DAILY_REMINDER");
        PendingIntent pendingIntent = PendingIntent.getBroadcast(context, REMINDER_REQUEST_CODE, intent,
                PendingIntent.FLAG_NO_CREATE | PendingIntent.FLAG_IMMUTABLE);

        if (pendingIntent != null) {
            alarmManager.cancel(pendingIntent);
            pendingIntent.cancel();
        }

        prefs.edit().remove(REMINDER_TIME_KEY).apply();
        Log.d(TAG, "Reminder cancelled. Was set: " + wasSet);
        return wasSet;
    }
}