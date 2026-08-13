package com.vitahot.ms_battery_nz.ui.results;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TimePicker;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;

import com.vitahot.ms_battery_nz.HRVDataManager;
import com.vitahot.ms_battery_nz.R;
import com.vitahot.ms_battery_nz.ReminderManager;

import java.util.ArrayList;
import java.util.List;

public class ResultsFragment extends Fragment {

    private HRVDataManager hrvManager;
    private ActivityResultLauncher<String[]> permissionLauncher;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Initialize the permission launcher
        permissionLauncher = registerForActivityResult(
                new ActivityResultContracts.RequestMultiplePermissions(),
                result -> {
                    android.util.Log.d("ReminderDialog", "Permission results:");
                    for (String permission : result.keySet()) {
                        Boolean granted = result.get(permission);
                        android.util.Log.d("ReminderDialog", permission + ": " + granted);
                    }

                    boolean allGranted = true;
                    for (Boolean granted : result.values()) {
                        if (!granted) {
                            allGranted = false;
                            break;
                        }
                    }

                    android.util.Log.d("ReminderDialog", "All granted: " + allGranted);

                    if (allGranted) {
                        android.util.Log.d("ReminderDialog", "All permissions granted");
                        showTimePicker();
                    } else {
                        android.util.Log.d("ReminderDialog", "Some permissions denied");
                        Toast.makeText(getContext(), "Permissions required to set reminders", Toast.LENGTH_SHORT).show();
                    }
                });
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_notifications, container, false);

        hrvManager = new HRVDataManager(getContext());

        // Privacy Policy Button
        Button privacyButton = view.findViewById(R.id.privacy_policy_button);
        privacyButton.setOnClickListener(v -> openPrivacyPolicy());

        // Delete Data Button
        Button deleteButton = view.findViewById(R.id.delete_data_button);
        deleteButton.setOnClickListener(v -> deleteUserData());

        // Reminder Button
        Button reminderButton = view.findViewById(R.id.reminder_button);
        reminderButton.setOnClickListener(v -> showReminderDialog());

        // Cancel Reminder Button
        Button cancelReminderButton = view.findViewById(R.id.cancel_reminder_button);
        cancelReminderButton.setOnClickListener(v -> cancelReminder());

        return view;
    }

    private void cancelReminder() {
        boolean removed = ReminderManager.cancelReminder(requireContext());
        if (removed) {
            Toast.makeText(getContext(), "Daily reminder has been removed", Toast.LENGTH_SHORT).show();
        } else {
            Toast.makeText(getContext(), "There was no reminder to remove", Toast.LENGTH_SHORT).show();
        }
    }

    private void openPrivacyPolicy() {
        String docUrl = "https://docs.google.com/document/d/YOUR_DOC_ID/edit?usp=sharing";
        Intent intent = new Intent(Intent.ACTION_VIEW);
        intent.setData(Uri.parse(docUrl));
        startActivity(intent);
    }

    private void deleteUserData() {
        if (getContext() == null) {
            Toast.makeText(getActivity(), "Context is null", Toast.LENGTH_SHORT).show();
            return;
        }

        if (hrvManager == null) {
            hrvManager = new HRVDataManager(getContext());
        }

        android.util.Log.d("DeleteData", "Attempting to delete data...");
        boolean deleted = hrvManager.deleteAllData();

        android.util.Log.d("DeleteData", "Delete result: " + deleted);

        if (deleted) {
            Toast.makeText(getContext(), "All data has been deleted", Toast.LENGTH_SHORT).show();
        } else {
            Toast.makeText(getContext(), "No data to delete", Toast.LENGTH_SHORT).show();
        }
    }

    private void showReminderDialog() {
        android.util.Log.d("ReminderDialog", "showReminderDialog called");

        List<String> permissionsToRequest = new ArrayList<>();

        // Check POST_NOTIFICATIONS (Android 13+)
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(requireContext(), android.Manifest.permission.POST_NOTIFICATIONS)
                    != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                permissionsToRequest.add(android.Manifest.permission.POST_NOTIFICATIONS);
            }
        }

        // For SCHEDULE_EXACT_ALARM on Android 12+, we check if we can schedule exact alarms.
        // If not, we should ideally guide the user to settings, but for simplicity in this flow,
        // we'll proceed and ReminderManager will handle the fallback or we can add the check here.
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
            android.app.AlarmManager alarmManager = (android.app.AlarmManager) requireContext().getSystemService(android.content.Context.ALARM_SERVICE);
            if (!alarmManager.canScheduleExactAlarms()) {
                Toast.makeText(getContext(), "Please allow exact alarms in settings for precise reminders", Toast.LENGTH_LONG).show();
                Intent intent = new Intent(android.provider.Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM);
                intent.setData(Uri.parse("package:" + requireContext().getPackageName()));
                startActivity(intent);
                return;
            }
        }

        if (permissionsToRequest.isEmpty()) {
            showTimePicker();
        } else {
            permissionLauncher.launch(permissionsToRequest.toArray(new String[0]));
        }
    }

    private void showTimePicker() {
        android.util.Log.d("ReminderDialog", "showTimePicker called");

        try {
            TimePicker timePicker = new TimePicker(getContext());

            new android.app.AlertDialog.Builder(getContext())
                    .setTitle("Set Daily Reminder")
                    .setMessage("What time would you like to be reminded each day?")
                    .setView(timePicker)
                    .setPositiveButton("Set Reminder", (dialog, which) -> {
                        int hour = timePicker.getHour();
                        int minute = timePicker.getMinute();
                        android.util.Log.d("ReminderDialog", "Setting reminder for " + hour + ":" + String.format("%02d", minute));
                        ReminderManager.setDailyReminder(getContext(), hour, minute);
                        Toast.makeText(getContext(), "✓ Daily reminder set for " + hour + ":" +
                                String.format("%02d", minute), Toast.LENGTH_LONG).show();
                    })
                    .setNegativeButton("Cancel", null)
                    .show();

            android.util.Log.d("ReminderDialog", "Dialog shown");
        } catch (Exception e) {
            android.util.Log.e("ReminderDialog", "Error showing time picker", e);
            Toast.makeText(getContext(), "Error: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }
}