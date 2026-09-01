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
    private static final String MORNING_REMINDER_TIME_KEY = "morning_reminder_time";
    private static final int REMINDER_REQUEST_CODE = 100;
    private static final int MORNING_REMINDER_REQUEST_CODE = 101;
    private static final String TAG = "ReminderManager";
    private static final String PROMPT_SHOWN_KEY = "reminder_prompt_shown";
    private static final String MORNING_PROMPT_SHOWN_KEY = "morning_reminder_prompt_shown";
    private static final String SYMPTOMS_VISIT_COUNT_KEY = "symptoms_visit_count";
    private static final String SETUP_PENDING_KEY = "reminder_setup_pending";
    private static final String WELCOME_SHOWN_KEY = "welcome_notice_shownA";
    private static final String SYMPTOMS_NOTICE_SHOWN_KEY = "symptoms_notice_shown";
    private static final String POSITION_NOTICE_SHOWN_KEY = "position_notice_shown";
    private static final String EXPOSURE_MODE_KEY = "exposure_mode_int";
    private static final String EXPOSURE_STORED_INDEX_KEY = "exposure_stored_index";
    private static final String EXPOSURE_USER_INDEX_KEY = "exposure_user_index";

    public static final int EXPOSURE_MODE_AUTO = 0;
    public static final int EXPOSURE_MODE_REMEMBERED = 1;
    public static final int EXPOSURE_MODE_USER = 2;

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

    public static int getSymptomsVisitCount(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        return prefs.getInt(SYMPTOMS_VISIT_COUNT_KEY, 0);
    }

    public static void incrementSymptomsVisitCount(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        int count = prefs.getInt(SYMPTOMS_VISIT_COUNT_KEY, 0);
        prefs.edit().putInt(SYMPTOMS_VISIT_COUNT_KEY, count + 1).apply();
    }

    public static boolean hasMorningPromptBeenShown(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        return prefs.getBoolean(MORNING_PROMPT_SHOWN_KEY, false);
    }

    public static void setMorningPromptShown(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        prefs.edit().putBoolean(MORNING_PROMPT_SHOWN_KEY, true).apply();
    }

    public static int getExposureMode(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        // Default to AUTO (0)
        return prefs.getInt(EXPOSURE_MODE_KEY, EXPOSURE_MODE_AUTO);
    }

    public static void setExposureMode(Context context, int mode) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        prefs.edit().putInt(EXPOSURE_MODE_KEY, mode).apply();
    }

    public static int getStoredExposureIndex(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        return prefs.getInt(EXPOSURE_STORED_INDEX_KEY, 0);
    }

    public static void setStoredExposureIndex(Context context, int index) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        prefs.edit().putInt(EXPOSURE_STORED_INDEX_KEY, index).apply();
    }

    public static int getUserExposureIndex(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        return prefs.getInt(EXPOSURE_USER_INDEX_KEY, 0);
    }

    public static void setUserExposureIndex(Context context, int index) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        prefs.edit().putInt(EXPOSURE_USER_INDEX_KEY, index).apply();
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

        scheduleReminder(context, hourOfDay, minute, REMINDER_REQUEST_CODE);
    }

    public static void setMorningReminder(Context context, int hourOfDay, int minute) {
        Log.d(TAG, "setMorningReminder called for " + hourOfDay + ":" + String.format("%02d", minute));

        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        prefs.edit().putInt(MORNING_REMINDER_TIME_KEY, hourOfDay * 60 + minute).apply();

        scheduleReminder(context, hourOfDay, minute, MORNING_REMINDER_REQUEST_CODE);
    }

    public static void scheduleReminder(Context context, int hourOfDay, int minute, int requestCode) {
        Log.d(TAG, "scheduleReminder called for code " + requestCode);

        AlarmManager alarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        if (alarmManager == null) return;

        Intent intent = new Intent(context, ReminderBroadcastReceiver.class);
        String action = (requestCode == MORNING_REMINDER_REQUEST_CODE) ?
                "com.vitahot.ms_battery_nz.MORNING_REMINDER" : "com.vitahot.ms_battery_nz.DAILY_REMINDER";
        intent.setAction(action);

        PendingIntent pendingIntent = PendingIntent.getBroadcast(context, requestCode, intent,
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
            scheduleReminder(context, hour, minute, REMINDER_REQUEST_CODE);
        }

        int morningTime = prefs.getInt(MORNING_REMINDER_TIME_KEY, -1);
        if (morningTime != -1) {
            int hour = morningTime / 60;
            int minute = morningTime % 60;
            scheduleReminder(context, hour, minute, MORNING_REMINDER_REQUEST_CODE);
        }
    }

    public static boolean isReminderSet(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        return prefs.getInt(REMINDER_TIME_KEY, -1) != -1;
    }

    public static boolean isMorningReminderSet(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        return prefs.getInt(MORNING_REMINDER_TIME_KEY, -1) != -1;
    }

    public static boolean cancelReminder(Context context) {
        return cancelReminderByCode(context, REMINDER_REQUEST_CODE);
    }

    public static boolean cancelMorningReminder(Context context) {
        return cancelReminderByCode(context, MORNING_REMINDER_REQUEST_CODE);
    }

    private static boolean cancelReminderByCode(Context context, int requestCode) {
        AlarmManager alarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        if (alarmManager == null) return false;

        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        String key = (requestCode == MORNING_REMINDER_REQUEST_CODE) ? MORNING_REMINDER_TIME_KEY : REMINDER_TIME_KEY;
        boolean wasSet = prefs.getInt(key, -1) != -1;

        Intent intent = new Intent(context, ReminderBroadcastReceiver.class);
        String action = (requestCode == MORNING_REMINDER_REQUEST_CODE) ?
                "com.vitahot.ms_battery_nz.MORNING_REMINDER" : "com.vitahot.ms_battery_nz.DAILY_REMINDER";
        intent.setAction(action);

        PendingIntent pendingIntent = PendingIntent.getBroadcast(context, requestCode, intent,
                PendingIntent.FLAG_NO_CREATE | PendingIntent.FLAG_IMMUTABLE);

        if (pendingIntent != null) {
            alarmManager.cancel(pendingIntent);
            pendingIntent.cancel();
        }

        prefs.edit().remove(key).apply();
        Log.d(TAG, "Reminder cancelled for code " + requestCode + ". Was set: " + wasSet);
        return wasSet;
    }
}
