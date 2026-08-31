package com.vitahot.ms_battery_nz.ui.symptoms;

import android.app.AlarmManager;
import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.TextView;
import android.widget.TimePicker;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;

import com.vitahot.ms_battery_nz.FatigueLevelPredictor;
import com.vitahot.ms_battery_nz.ForestDataPoint;
import com.vitahot.ms_battery_nz.HRVBaselineAnalyzer;
import com.vitahot.ms_battery_nz.HRVData;
import com.vitahot.ms_battery_nz.HRVDataManager;
import com.vitahot.ms_battery_nz.ReminderManager;
import com.vitahot.ms_battery_nz.databinding.FragmentDashboardBinding;
import com.vitahot.ms_battery_nz.MessageDisplayManager;

import java.util.ArrayList;
import java.util.List;
import java.text.SimpleDateFormat;
import java.text.ParseException;
import java.util.Date;
import java.util.Locale;


public class SymptomsFragment extends Fragment {

    private FragmentDashboardBinding binding;

    HRVDataManager hrvData;


    EditText inputField; // = findViewById<EditText>(R.id.inputField)
    Button sendButton;// = findViewById<Button>(R.id.sendButton)
    int currentFatigueLevel = 0;
    int currentHeadacheLevel = 0;

    //Fatigue selection buttons
    // Radio button references
    private RadioButton rbtn_fatigueLevel0, rbtn_fatigueLevel1, rbtn_fatigueLevel2, rbtn_fatigueLevel3, rbtn_fatigueLevel4, rbtn_fatigueLevel5;
    //private RadioButton rbtn_HeadacheLevel0, rbtn_HeadacheLevel1, rbtn_HeadacheLevel2, rbtn_HeadacheLevel3, rbtn_HeadacheLevel4, rbtn_HeadacheLevel5;

    private ImageButton btn_daybackward, btn_dayforward;
    private TextView dayDisplayText;
    private int dayOffset;  //Offsets our current day to view other data
    private TextView textView;

    private RadioGroup fatigueGroup;//, headacheGroup;

    private TextView guideTextView;
    private MessageDisplayManager messageManager;

    private ActivityResultLauncher<String[]> permissionLauncher;
    private boolean settingMorningReminder = false;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        // Increment visit count for morning reminder prompt
        ReminderManager.incrementSymptomsVisitCount(requireContext());
        
        permissionLauncher = registerForActivityResult(
                new ActivityResultContracts.RequestMultiplePermissions(),
                result -> {
                    boolean allGranted = true;
                    for (Boolean granted : result.values()) {
                        if (!granted) {
                            allGranted = false;
                            break;
                        }
                    }

                    if (allGranted) {
                        showTimePicker();
                    } else {
                        Toast.makeText(getContext(), "Permissions required to set reminders", Toast.LENGTH_SHORT).show();
                        ReminderManager.setSetupPending(requireContext(), false);
                        if (!settingMorningReminder) {
                            ReminderManager.setPromptShown(requireContext());
                        } else {
                            ReminderManager.setMorningPromptShown(requireContext());
                        }
                        showInformationPageMessage();
                    }
                });
    }

    public View onCreateView(@NonNull LayoutInflater inflater,
                             ViewGroup container, Bundle savedInstanceState) {
        SymptomsViewModel symptomsViewModel =
                new ViewModelProvider(this).get(SymptomsViewModel.class);

        binding = FragmentDashboardBinding.inflate(inflater, container, false);
        View root = binding.getRoot();

        hrvData = new HRVDataManager(getContext()); //This is a terrible way of doing things...

        textView = binding.predictionText;
        //symptomsViewModel.getText().observe(getViewLifecycleOwner(), textView::setText);

        //inputField = binding.inputField;
        //sendButton = binding.sendButton;
        btn_daybackward = binding.btnDayback;
        btn_dayforward = binding.btnDayforward;
        dayDisplayText = binding.txtDaytitle;
        guideTextView = binding.symptomsAreaText;
        messageManager = new MessageDisplayManager(guideTextView);
        messageManager.startStage(4);

        // Show symptoms notice if it's the first time
        if (!ReminderManager.hasSymptomsNoticeBeenShown(requireContext())) {
            showSymptomsNotice();
        } else if (ReminderManager.getSymptomsVisitCount(requireContext()) >= 2 
                && !ReminderManager.hasMorningPromptBeenShown(requireContext())) {
            showMorningReminderPrompt();
        }


        fatigueGroup = binding.fatigueRadioGroup;
        //headacheGroup = binding.headacheRadioGroup;

        // Initialize radio buttons
        initializeRadioButtons(root);

        // Set up click listeners
        setupRadioButtonListeners();

        // Load and display current fatigue level from dataset
        //Need to check if we've got data for today
        boolean hasDataForToday=hrvData.getTodaysData() != null;

        if (hasDataForToday) {
            loadCurrentFatigueLevel(hrvData.getTodaysData().getFatigueLevel());

            loadCurrentHeadacheLevel(hrvData.getTodaysData().getHeadacheLevel());

            String predictedFatigueLevel = MakePrediction();
            textView.setText(predictedFatigueLevel);
        } else {
            textView.setText("No data recorded for today");
        }

        //Change our text header so that we're displaying todays data as this is what we'll drop in on by default
        dayDisplayText.setText("Today");// + hrvData.getTodaysData().getDate().toString());

        btn_dayforward.setOnClickListener(v -> GotoNextDay());
        btn_daybackward.setOnClickListener(v -> GotoPreviousDay());
        return root;
    }

    @Override
    public void onPause() {
        super.onPause();
        // Just pause the message manager, don't unbind camera
        if (messageManager != null) {
            messageManager.stop();
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        if (messageManager != null) {   //resume our messages for this screen
            messageManager.startStage(4);
        }

        // Check if we were in the middle of a reminder setup and just returned
        if (ReminderManager.isSetupPending(requireContext())) {
            showReminderDialog();
        }
    }

    void GotoNextDay() {
        //We need to cycle our current day back and collect the data for it
        dayOffset += 1;
        if (dayOffset > 0) {    //Quick clamp so that we can't go into the future
            dayOffset = 0;
        }
        RefreshPageDate();
    }

    void GotoPreviousDay() {
        //We need to cycle our current day forward and collect the data for it
        dayOffset -= 1;
        RefreshPageDate();
    }

    private String convertDateFormat(String dateString) {
        try {
            // Parse the input format (yyyy-MM-dd)
            SimpleDateFormat inputFormat = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());
            Date date = inputFormat.parse(dateString);

            if (date == null) {
                return dateString;
            }

            // Format to output format (dd/MM/yyyy)
            SimpleDateFormat outputFormat = new SimpleDateFormat("dd/MM/yyyy", Locale.getDefault());
            return outputFormat.format(date);
        } catch (ParseException e) {
            e.printStackTrace();
            return dateString; // Return original string if parsing fails
        }
    }

    void RefreshPageDate() {
        HRVData sampleData = hrvData.getOffsetData(dayOffset);
        //We need to change our radio buttons to the radio buttons set on this date
        //We need to have the radio buttons take input from the user to set the date

        boolean hasDataForToday= sampleData != null;


        if (hasDataForToday) {
            if (dayOffset == 0) {
                dayDisplayText.setText("Today");
            } else{

                dayDisplayText.setText(convertDateFormat(sampleData.getDate()));
            }

            loadCurrentFatigueLevel(sampleData.getFatigueLevel());
            loadCurrentHeadacheLevel(sampleData.getHeadacheLevel());

            String predictedFatigueLevel = MakePrediction();
            textView.setText(predictedFatigueLevel);
        } else {
            dayDisplayText.setText("No data for this date");
        }
    }

    void getInputFieldValue() {
        String userInput = inputField.getText().toString();  //.text.toString(); // Get text from EditText
        float fatigueValue = Float.parseFloat(userInput);
        //round our float
        int fatigueInt = Math.round(fatigueValue);
        hrvData.setTodaysFatigueLevel(fatigueInt);

        //displayText.text = userInput // Set text in TextView
    }

    String MakePrediction() {
        //double fatiguePrediction = -1;
        if (hrvData ==null) {
            hrvData = new HRVDataManager(getContext()); //This is a terrible way of doing things...
        }

        List<ForestDataPoint> historicalData = new ArrayList<>();
        List<HRVData> allHRVData = hrvData.getAllData();

        String predictionString  = "";
        HRVData dataEntry = hrvData.getOffsetData(dayOffset);// hrvData.getTodaysData();// allHRVData.get(allHRVData.size()-1);    //Todays entry
        if (dataEntry == null) {
            return "No data avaliable";
        }

        if (allHRVData.size() < 3) {
            predictionString = "Need to gather " + (3 - allHRVData.size()) + " more days worth of data to make a predication\n";
            predictionString += "RMSSD: " +  String.format("%.2f",dataEntry.getRmssd()) + "\n";
            predictionString += "Heart Rate: " +  String.format("%.1f",dataEntry.getHeartRate()) + "\n";
            predictionString += "Valid Beats: " + dataEntry.getValidBeats() + "\n";
            return predictionString;
        }

        List<HRVData> historicHRV = new ArrayList<>();
        for (int i=0; i< allHRVData.size()-1; i++) {    //Grab all our data apart from todays entry for a test
            //double sdnn, double rmssd, double pnn50, double fatigueLevel
            HRVData hv_dataEntry = allHRVData.get(i);
            ForestDataPoint newForestPoint = new ForestDataPoint(hv_dataEntry.getSdnn(), hv_dataEntry.getRmssd(),
                    hv_dataEntry.getPnn50(), hv_dataEntry.getFatigueLevel());
            historicalData.add(newForestPoint);
            historicHRV.add(hv_dataEntry);
        }

        //RandomForest  fatigueModel = new RandomForest(30, 8, 3);
        //fatigueModel.train(historicalData);

        HRVBaselineAnalyzer baselineAnalyzer = new HRVBaselineAnalyzer(historicalData.size());
        baselineAnalyzer.updateBaseline(historicalData);

         //""HRV Score: " + FatigueLevelPredictor.getDailyScore(historicHRV, dataEntry) + "\n";
        predictionString += "Predicted Level: " + FatigueLevelPredictor.predictFatigueLevelRange(historicHRV, dataEntry) + "\n";

        //predictionString += "Trend Prediction: " + FatigueLevelPredictor.predictFatigueLevelRangeWithTrend(historicHRV, dataEntry, 7) + "\n";
// Get confidence level
        double confidence = FatigueLevelPredictor.getPredictionConfidence(historicHRV, FatigueLevelPredictor.predictFatigueLevel(historicHRV, dataEntry));
        predictionString += "Confidence: " + confidence + "%\n";
        predictionString += "\n";
        //Add in details from this reading:
        predictionString += "Measurements:\n";
        predictionString += "RMSSD: " +  String.format("%.2f",dataEntry.getRmssd()) + "\n";
        predictionString += "Heart Rate: " +  String.format("%.1f",dataEntry.getHeartRate()) + "\n";
        predictionString += "Valid Beats: " + dataEntry.getValidBeats() + "\n";

        return predictionString;
    }

    public String getRecommendation(boolean highFatigueRisk, double percentileRank, String riskLevel) {
        if (highFatigueRisk || riskLevel.equals("HIGH_DEVIATION")) {
            return "High risk measure. Pay close attention to difficulties today.";
        } else if (riskLevel.equals("MODERATE_DEVIATION")) {
            return "Moderate risk measure. Take it easy on yourself.";
        } else if (percentileRank > 75) {
            return "Slightly raised risk measure. Proceed carefully.";
        } else {
            return "Your risk measure is within normal ranges.";
        }
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }

    private void initializeRadioButtons(View view) {
        rbtn_fatigueLevel0 = binding.rbtnFatigueLevel0;
        rbtn_fatigueLevel1 = binding.rbtnFatigueLevel1;
        rbtn_fatigueLevel2 = binding.rbtnFatigueLevel2;
        rbtn_fatigueLevel3 = binding.rbtnFatigueLevel3;
        rbtn_fatigueLevel4 = binding.rbtnFatigueLevel4;
        rbtn_fatigueLevel5 = binding.rbtnFatigueLevel5;
/*
        rbtn_HeadacheLevel0 = binding.rbtnHeadacheLevel0;
        rbtn_HeadacheLevel1 = binding.rbtnHeadacheLevel1;
        rbtn_HeadacheLevel2 = binding.rbtnHeadacheLevel2;
        rbtn_HeadacheLevel3 = binding.rbtnHeadacheLevel3;
        rbtn_HeadacheLevel4 = binding.rbtnHeadacheLevel4;
        rbtn_HeadacheLevel5 = binding.rbtnHeadacheLevel5;

 */
    }

    private void setupRadioButtonListeners() {
        rbtn_fatigueLevel0.setOnClickListener(v -> onFatigueLevelSelected(0));
        rbtn_fatigueLevel1.setOnClickListener(v -> onFatigueLevelSelected(1));
        rbtn_fatigueLevel2.setOnClickListener(v -> onFatigueLevelSelected(2));
        rbtn_fatigueLevel3.setOnClickListener(v -> onFatigueLevelSelected(3));
        rbtn_fatigueLevel4.setOnClickListener(v -> onFatigueLevelSelected(4));
        rbtn_fatigueLevel5.setOnClickListener(v -> onFatigueLevelSelected(5));
        /*
        rbtn_HeadacheLevel0.setOnClickListener(v -> onHeadacheLevelSelected(0));
        rbtn_HeadacheLevel1.setOnClickListener(v -> onHeadacheLevelSelected(1));
        rbtn_HeadacheLevel2.setOnClickListener(v -> onHeadacheLevelSelected(2));
        rbtn_HeadacheLevel3.setOnClickListener(v -> onHeadacheLevelSelected(3));
        rbtn_HeadacheLevel4.setOnClickListener(v -> onHeadacheLevelSelected(4));
        rbtn_HeadacheLevel5.setOnClickListener(v -> onHeadacheLevelSelected(5));

         */
    }

    /**
     * Called when a radio button is selected
     * @param fatigueLevel The selected fatigue level (0-5)
     */
    private void onFatigueLevelSelected(int fatigueLevel) {
        // Store the current selection
        currentFatigueLevel = fatigueLevel;

        // Save to your dataset here
        hrvData.setFatigueLevel(fatigueLevel, dayOffset);

        // Check if we should prompt for reminder (only once)
        if (!ReminderManager.hasPromptBeenShown(requireContext())) {
            showReminderPrompt();
        }
    }

    private void showReminderPrompt() {
        new AlertDialog.Builder(getContext())
                .setTitle("Daily Reminder")
                .setMessage("Would you like to set a daily reminder to log your fatigue levels?")
                .setPositiveButton("Yes", (dialog, which) -> {
                    settingMorningReminder = false;
                    showReminderDialog();
                })
                .setNegativeButton("No", (dialog, which) -> {
                    ReminderManager.setPromptShown(requireContext());
                    showInformationPageMessage();
                })
                .setCancelable(false)
                .show();
    }

    private void showMorningReminderPrompt() {
        new AlertDialog.Builder(getContext())
                .setTitle("Morning Reminder")
                .setMessage("Would you like to set a morning reminder to record your HRV?")
                .setPositiveButton("Yes", (dialog, which) -> {
                    settingMorningReminder = true;
                    showReminderDialog();
                })
                .setNegativeButton("No", (dialog, which) -> {
                    ReminderManager.setMorningPromptShown(requireContext());
                    showInformationPageMessage();
                })
                .setCancelable(false)
                .show();
    }

    private void showInformationPageMessage() {
        String message = settingMorningReminder ? 
                "You can set or change your morning reminder at any time from the Information page." :
                "You can set or change your daily reminder at any time from the Information page.";
        
        new AlertDialog.Builder(getContext())
                .setTitle("Reminder Settings")
                .setMessage(message)
                .setPositiveButton("OK", null)
                .show();
    }

    private void showSymptomsNotice() {
        new AlertDialog.Builder(getContext())
                .setTitle("Setting Symptoms")
                .setMessage("Please select the level of fatigue you experienced today.\n\nThis is best set at the end of the day based on how you feel. Your fatigue level can be updated at any time, and the data you enter will be used to improve the accuracy of future predictions.")
                .setPositiveButton("OK", (dialog, which) -> {
                    ReminderManager.setSymptomsNoticeShown(requireContext());
                })
                .setCancelable(false)
                .show();
    }

    private void showReminderDialog() {
        List<String> permissionsToRequest = new ArrayList<>();

        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(requireContext(), android.Manifest.permission.POST_NOTIFICATIONS)
                    != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                permissionsToRequest.add(android.Manifest.permission.POST_NOTIFICATIONS);
            }
        }

        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
            AlarmManager alarmManager = (AlarmManager) requireContext().getSystemService(Context.ALARM_SERVICE);
            if (alarmManager != null && !alarmManager.canScheduleExactAlarms()) {
                ReminderManager.setSetupPending(requireContext(), true);
                Toast.makeText(getContext(), "Please allow exact alarms in settings for precise reminders", Toast.LENGTH_LONG).show();
                Intent intent = new Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM);
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
        try {
            TimePicker timePicker = new TimePicker(getContext());
            String title = settingMorningReminder ? "Set Morning Reminder" : "Set Daily Reminder";
            String message = settingMorningReminder ? "What time would you like to record your HRV each morning?" : "What time would you like to be reminded each day?";
            
            new AlertDialog.Builder(getContext())
                    .setTitle(title)
                    .setMessage(message)
                    .setView(timePicker)
                    .setPositiveButton("Set Reminder", (dialog, which) -> {
                        int hour = timePicker.getHour();
                        int minute = timePicker.getMinute();
                        
                        if (settingMorningReminder) {
                            ReminderManager.setMorningReminder(getContext(), hour, minute);
                            ReminderManager.setMorningPromptShown(requireContext());
                            Toast.makeText(getContext(), "✓ Morning reminder set", Toast.LENGTH_LONG).show();
                        } else {
                            ReminderManager.setDailyReminder(getContext(), hour, minute);
                            ReminderManager.setPromptShown(requireContext());
                            Toast.makeText(getContext(), "✓ Daily reminder set", Toast.LENGTH_LONG).show();
                        }
                        showInformationPageMessage();
                    })
                    .setNegativeButton("Cancel", (dialog, which) -> {
                        ReminderManager.setSetupPending(requireContext(), false);
                        if (settingMorningReminder) {
                            ReminderManager.setMorningPromptShown(requireContext());
                        } else {
                            ReminderManager.setPromptShown(requireContext());
                        }
                        showInformationPageMessage();
                    })
                    .setCancelable(false)
                    .show();
        } catch (Exception e) {
            Toast.makeText(getContext(), "Error: " + e.getMessage(), Toast.LENGTH_SHORT).show();
            if (settingMorningReminder) {
                ReminderManager.setMorningPromptShown(requireContext());
            } else {
                ReminderManager.setPromptShown(requireContext());
            }
        }
    }

    private void onHeadacheLevelSelected(int headacheLevel) {
        // Clear all radio buttons first
        //clearAllHeadacheRadioButtons();

        // Set the selected radio button
        //setRadioHeadacheButtonChecked(headacheLevel, true);

        // Store the current selection
        currentHeadacheLevel = headacheLevel;

        // Save to your dataset here
        //hrvData.setTodaysHeadacheLevel(headacheLevel);
        hrvData.setHeadacheLevel(headacheLevel, dayOffset);

    }

    /**
     * Load the current fatigue level from your dataset and update UI
     */
    private void loadCurrentFatigueLevel(int savedFatigueLevel) {
        // Replace this with your actual dataset retrieval method
        //int savedFatigueLevel = hrvData.getTodaysData().getFatigueLevel();

        if (savedFatigueLevel >= 0 && savedFatigueLevel <= 5) {
            currentFatigueLevel = savedFatigueLevel;
            clearAllRadioButtons();
            setRadioButtonChecked(savedFatigueLevel, true);
        }
    }


    private void loadCurrentHeadacheLevel(int savedHeadacheLevel) {
        // Replace this with your actual dataset retrieval method
        //int savedHeadacheLevel = hrvData.getTodaysData().getHeadacheLevel();

        if (savedHeadacheLevel >= 0 && savedHeadacheLevel <= 5) {
            currentHeadacheLevel = savedHeadacheLevel;
            clearAllHeadacheRadioButtons();
            setRadioHeadacheButtonChecked(savedHeadacheLevel, true);
        }
    }
    /**
     * Clear all radio button selections
     */
    private void clearAllRadioButtons() {
        fatigueGroup.clearCheck();
        /*
        rbtn_fatigueLevel0.setChecked(false);
        rbtn_fatigueLevel1.setChecked(false);
        rbtn_fatigueLevel2.setChecked(false);
        rbtn_fatigueLevel3.setChecked(false);
        rbtn_fatigueLevel4.setChecked(false);
        rbtn_fatigueLevel5.setChecked(false);
         */
    }

    private void clearAllHeadacheRadioButtons() {
        //headacheGroup.clearCheck();
        /*
        rbtn_HeadacheLevel0.setChecked(false);
        rbtn_HeadacheLevel1.setChecked(false);
        rbtn_HeadacheLevel2.setChecked(false);
        rbtn_HeadacheLevel3.setChecked(false);
        rbtn_HeadacheLevel4.setChecked(false);
        rbtn_HeadacheLevel5.setChecked(false);
         */
    }

    /**
     * Set a specific radio button's checked state
     * @param level The fatigue level (0-5)
     * @param checked Whether to check or uncheck
     */
    private void setRadioButtonChecked(int level, boolean checked) {
        switch (level) {
            case 0:
                rbtn_fatigueLevel0.setChecked(checked);
                break;
            case 1:
                rbtn_fatigueLevel1.setChecked(checked);
                break;
            case 2:
                rbtn_fatigueLevel2.setChecked(checked);
                break;
            case 3:
                rbtn_fatigueLevel3.setChecked(checked);
                break;
            case 4:
                rbtn_fatigueLevel4.setChecked(checked);
                break;
            case 5:
                rbtn_fatigueLevel5.setChecked(checked);
                break;
        }
    }

    private void setRadioHeadacheButtonChecked(int level, boolean checked) {
        /*
        rbtn_HeadacheLevel0.setChecked(level == 0);
        rbtn_HeadacheLevel1.setChecked(level == 1);
        rbtn_HeadacheLevel2.setChecked(level == 2);
        rbtn_HeadacheLevel3.setChecked(level == 3);
        rbtn_HeadacheLevel4.setChecked(level == 4);
        rbtn_HeadacheLevel5.setChecked(level == 5);

         */
    }
}