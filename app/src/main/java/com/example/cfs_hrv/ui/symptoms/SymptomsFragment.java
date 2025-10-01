package com.example.cfs_hrv.ui.symptoms;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.RadioButton;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;

import com.example.cfs_hrv.FatigueLevelPredictor;
import com.example.cfs_hrv.ForestDataPoint;
import com.example.cfs_hrv.HRVBaselineAnalyzer;
import com.example.cfs_hrv.HRVData;
import com.example.cfs_hrv.HRVDataManager;
import com.example.cfs_hrv.databinding.FragmentDashboardBinding;

import java.util.ArrayList;
import java.util.List;


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
    private RadioButton rbtn_HeadacheLevel0, rbtn_HeadacheLevel1, rbtn_HeadacheLevel2, rbtn_HeadacheLevel3, rbtn_HeadacheLevel4, rbtn_HeadacheLevel5;
    public View onCreateView(@NonNull LayoutInflater inflater,
                             ViewGroup container, Bundle savedInstanceState) {
        SymptomsViewModel symptomsViewModel =
                new ViewModelProvider(this).get(SymptomsViewModel.class);

        binding = FragmentDashboardBinding.inflate(inflater, container, false);
        View root = binding.getRoot();

        hrvData = new HRVDataManager(getContext()); //This is a terrible way of doing things...

        final TextView textView = binding.predictionText;
        //symptomsViewModel.getText().observe(getViewLifecycleOwner(), textView::setText);

        //inputField = binding.inputField;
        //sendButton = binding.sendButton;


            // Initialize radio buttons
        initializeRadioButtons(root);

        // Set up click listeners
        setupRadioButtonListeners();

        // Load and display current fatigue level from dataset
        //Need to check if we've got data for today
        boolean hasDataForToday=hrvData.getTodaysData() != null;

        if (hasDataForToday) {
            loadCurrentFatigueLevel();

            loadCurrentHeadacheLevel();

            String predictedFatigueLevel = MakePrediction();
            textView.setText(predictedFatigueLevel);
        } else {
            textView.setText("No data recorded for today");
        }


        return root;
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

        if (allHRVData.size() < 3) {
            return "Need to gather " + (3 - allHRVData.size()) + " more days worth of data\nto make a predication";
        }

        List<HRVData> historicHRV = new ArrayList<>();
        for (int i=0; i< allHRVData.size()-1; i++) {    //Grab all our data apart from todays entry for a test
            //double sdnn, double rmssd, double pnn50, double fatigueLevel
            HRVData dataEntry = allHRVData.get(i);
            ForestDataPoint newForestPoint = new ForestDataPoint(dataEntry.getSdnn(), dataEntry.getRmssd(),
                    dataEntry.getPnn50(), dataEntry.getFatigueLevel());
            historicalData.add(newForestPoint);
            historicHRV.add(dataEntry);
        }

        //RandomForest  fatigueModel = new RandomForest(30, 8, 3);
        //fatigueModel.train(historicalData);

        HRVBaselineAnalyzer baselineAnalyzer = new HRVBaselineAnalyzer(historicalData.size());
        baselineAnalyzer.updateBaseline(historicalData);

        HRVData dataEntry = hrvData.getTodaysData();// allHRVData.get(allHRVData.size()-1);    //Todays entry
        //(double sdnn, double rmssd, double pnn50) {
        //fatiguePrediction = fatigueModel.predict(dataEntry.getSdnn(), dataEntry.getRmssd(), dataEntry.getPnn50());

        // 3. Percentile ranking
        //double percentileRank = baselineAnalyzer.getPercentileRank(dataEntry.getSdnn(), dataEntry.getRmssd(), dataEntry.getPnn50());

        // 4. Risk assessment
        //boolean highFatigueRisk = baselineAnalyzer.isHighFatigueRisk(dataEntry.getSdnn(), dataEntry.getRmssd(), dataEntry.getPnn50());

        //HRVBaselineAnalyzer.DeviationResult deviation = baselineAnalyzer.analyzeDeviation(dataEntry.getSdnn(), dataEntry.getRmssd(), dataEntry.getPnn50());

        //HRVBaselineAnalyzer.DeviationResult riskLevel = baselineAnalyzer.analyzeDeviation(dataEntry.getSdnn(), dataEntry.getRmssd(), dataEntry.getPnn50());

        //String reccomendation = getRecommendation(highFatigueRisk, percentileRank, riskLevel.riskLevel);

        //textView.setText("Predicted Level: " + (float)predictedFatigueLevel);
        /*
        String predictionString = "Predicted Level: " + (float)fatiguePrediction;
        predictionString += "\n";
        predictionString += reccomendation;
        predictionString += "\n\n";*/

        //String predictionString = "Todays Fatigue Level: " + dataEntry.getFatigueLevel() + "\n";
        //predictionString += "Todays Headache Level: " + dataEntry.getHeadacheLevel() + "\n";

        String predictionString  = "Predicted Level: " + FatigueLevelPredictor.predictFatigueLevelRange(historicHRV, dataEntry) + "\n";

        predictionString += "Trend Prediction: " + FatigueLevelPredictor.predictFatigueLevelRangeWithTrend(historicHRV, dataEntry, 7) + "\n";
// Get confidence level
        double confidence = FatigueLevelPredictor.getPredictionConfidence(historicHRV, FatigueLevelPredictor.predictFatigueLevel(historicHRV, dataEntry));
        predictionString += "Confidence: " + confidence + "%\n";
/*
        predictionString += "\n\n";
        predictionString += "SDNN Dev: " + deviation.sdnnDeviation + "\n";
        predictionString += "RMSS Dev: " + deviation.rmssdDeviation + "\n";
        predictionString += "PNN50 Dev: " + deviation.pnn50Deviation + "\n";
        predictionString += "Composite Dev: " + deviation.compositeDeviation + "\n";
*/
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

        rbtn_HeadacheLevel0 = binding.rbtnHeadacheLevel0;
        rbtn_HeadacheLevel1 = binding.rbtnHeadacheLevel1;
        rbtn_HeadacheLevel2 = binding.rbtnHeadacheLevel2;
        rbtn_HeadacheLevel3 = binding.rbtnHeadacheLevel3;
        rbtn_HeadacheLevel4 = binding.rbtnHeadacheLevel4;
        rbtn_HeadacheLevel5 = binding.rbtnHeadacheLevel5;
    }

    private void setupRadioButtonListeners() {
        rbtn_fatigueLevel0.setOnClickListener(v -> onFatigueLevelSelected(0));
        rbtn_fatigueLevel1.setOnClickListener(v -> onFatigueLevelSelected(1));
        rbtn_fatigueLevel2.setOnClickListener(v -> onFatigueLevelSelected(2));
        rbtn_fatigueLevel3.setOnClickListener(v -> onFatigueLevelSelected(3));
        rbtn_fatigueLevel4.setOnClickListener(v -> onFatigueLevelSelected(4));
        rbtn_fatigueLevel5.setOnClickListener(v -> onFatigueLevelSelected(5));

        rbtn_HeadacheLevel0.setOnClickListener(v -> onHeadacheLevelSelected(0));
        rbtn_HeadacheLevel1.setOnClickListener(v -> onHeadacheLevelSelected(1));
        rbtn_HeadacheLevel2.setOnClickListener(v -> onHeadacheLevelSelected(2));
        rbtn_HeadacheLevel3.setOnClickListener(v -> onHeadacheLevelSelected(3));
        rbtn_HeadacheLevel4.setOnClickListener(v -> onHeadacheLevelSelected(4));
        rbtn_HeadacheLevel5.setOnClickListener(v -> onHeadacheLevelSelected(5));
    }

    /**
     * Called when a radio button is selected
     * @param fatigueLevel The selected fatigue level (0-5)
     */
    private void onFatigueLevelSelected(int fatigueLevel) {
        // Clear all radio buttons first
        //clearAllRadioButtons();

        // Set the selected radio button
        //setRadioButtonChecked(fatigueLevel, true);

        // Store the current selection
        currentFatigueLevel = fatigueLevel;

        // Save to your dataset here
        hrvData.setTodaysFatigueLevel(fatigueLevel);
    }

    private void onHeadacheLevelSelected(int headacheLevel) {
        // Clear all radio buttons first
        //clearAllHeadacheRadioButtons();

        // Set the selected radio button
        //setRadioHeadacheButtonChecked(headacheLevel, true);

        // Store the current selection
        currentHeadacheLevel = headacheLevel;

        // Save to your dataset here
        hrvData.setTodaysHeadacheLevel(headacheLevel);
    }

    /**
     * Load the current fatigue level from your dataset and update UI
     */
    private void loadCurrentFatigueLevel() {
        // Replace this with your actual dataset retrieval method
        int savedFatigueLevel = hrvData.getTodaysData().getFatigueLevel();

        if (savedFatigueLevel >= 0 && savedFatigueLevel <= 5) {
            currentFatigueLevel = savedFatigueLevel;
            clearAllRadioButtons();
            setRadioButtonChecked(savedFatigueLevel, true);
        }
    }


    private void loadCurrentHeadacheLevel() {
        // Replace this with your actual dataset retrieval method
        int savedHeadacheLevel = hrvData.getTodaysData().getHeadacheLevel();

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
        rbtn_fatigueLevel0.setChecked(false);
        rbtn_fatigueLevel1.setChecked(false);
        rbtn_fatigueLevel2.setChecked(false);
        rbtn_fatigueLevel3.setChecked(false);
        rbtn_fatigueLevel4.setChecked(false);
        rbtn_fatigueLevel5.setChecked(false);
    }

    private void clearAllHeadacheRadioButtons() {
        rbtn_HeadacheLevel0.setChecked(false);
        rbtn_HeadacheLevel1.setChecked(false);
        rbtn_HeadacheLevel2.setChecked(false);
        rbtn_HeadacheLevel3.setChecked(false);
        rbtn_HeadacheLevel4.setChecked(false);
        rbtn_HeadacheLevel5.setChecked(false);
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
        rbtn_HeadacheLevel0.setChecked(level == 0);
        rbtn_HeadacheLevel1.setChecked(level == 1);
        rbtn_HeadacheLevel2.setChecked(level == 2);
        rbtn_HeadacheLevel3.setChecked(level == 3);
        rbtn_HeadacheLevel4.setChecked(level == 4);
        rbtn_HeadacheLevel5.setChecked(level == 5);
        /*
        switch (level) {
            case 0:
                rbtn_HeadacheLevel0.setChecked(checked);
                break;
            case 1:
                rbtn_HeadacheLevel1.setChecked(checked);
                break;
            case 2:
                rbtn_HeadacheLevel2.setChecked(checked);
                break;
            case 3:
                rbtn_HeadacheLevel3.setChecked(checked);
                break;
            case 4:
                rbtn_HeadacheLevel4.setChecked(checked);
                break;
            case 5:
                rbtn_HeadacheLevel5.setChecked(checked);
                break;
        }
         */
    }
}