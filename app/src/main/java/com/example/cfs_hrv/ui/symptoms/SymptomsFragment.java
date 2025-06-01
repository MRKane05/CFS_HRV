package com.example.cfs_hrv.ui.symptoms;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;

import com.example.cfs_hrv.ForestDataPoint;
import com.example.cfs_hrv.HRVBaselineAnalyzer;
import com.example.cfs_hrv.HRVData;
import com.example.cfs_hrv.HRVDataManager;
import com.example.cfs_hrv.HRVMeasurementSystem;
import com.example.cfs_hrv.HRVTrackingActivity;
import com.example.cfs_hrv.RandomForest;
import com.example.cfs_hrv.databinding.FragmentDashboardBinding;

import java.util.ArrayList;
import java.util.List;

public class SymptomsFragment extends Fragment {

    private FragmentDashboardBinding binding;

    HRVDataManager hrvData;

    EditText inputField; // = findViewById<EditText>(R.id.inputField)
    Button sendButton;// = findViewById<Button>(R.id.sendButton)

    public View onCreateView(@NonNull LayoutInflater inflater,
                             ViewGroup container, Bundle savedInstanceState) {
        SymptomsViewModel symptomsViewModel =
                new ViewModelProvider(this).get(SymptomsViewModel.class);

        binding = FragmentDashboardBinding.inflate(inflater, container, false);
        View root = binding.getRoot();

        final TextView textView = binding.predictionText;
        //symptomsViewModel.getText().observe(getViewLifecycleOwner(), textView::setText);

        inputField = binding.inputField;
        sendButton = binding.sendButton;

        // When the button is clicked
        sendButton.setOnClickListener (
            v -> getInputFieldValue()
        );


        String predictedFatigueLevel = MakePrediction();
        textView.setText(predictedFatigueLevel);


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
        double fatiguePrediction = -1;
        hrvData = new HRVDataManager(getContext()); //This is a terrible way of doing things...

        List<ForestDataPoint> historicalData = new ArrayList<>();
        List<HRVData> allHRVData = hrvData.getAllData();

        for (int i=0; i< allHRVData.size()-1; i++) {    //Grab all our data apart from todays entry for a test
            //double sdnn, double rmssd, double pnn50, double fatigueLevel
            HRVData dataEntry = allHRVData.get(i);
            ForestDataPoint newForestPoint = new ForestDataPoint(dataEntry.getSdnn(), dataEntry.getRmssd(),
                    dataEntry.getPnn50(), dataEntry.getFatigueLevel());
            historicalData.add(newForestPoint);
        }

        RandomForest  fatigueModel = new RandomForest(30, 8, 3);
        fatigueModel.train(historicalData);

        HRVBaselineAnalyzer baselineAnalyzer = new HRVBaselineAnalyzer(historicalData.size());
        baselineAnalyzer.updateBaseline(historicalData);

        HRVData dataEntry = allHRVData.get(allHRVData.size()-1);    //Todays entry
        //(double sdnn, double rmssd, double pnn50) {
        fatiguePrediction = fatigueModel.predict(dataEntry.getSdnn(), dataEntry.getRmssd(), dataEntry.getPnn50());

        // 3. Percentile ranking
        double percentileRank = baselineAnalyzer.getPercentileRank(dataEntry.getSdnn(), dataEntry.getRmssd(), dataEntry.getPnn50());

        // 4. Risk assessment
        boolean highFatigueRisk = baselineAnalyzer.isHighFatigueRisk(dataEntry.getSdnn(), dataEntry.getRmssd(), dataEntry.getPnn50());

        HRVBaselineAnalyzer.DeviationResult riskLevel = baselineAnalyzer.analyzeDeviation(dataEntry.getSdnn(), dataEntry.getRmssd(), dataEntry.getPnn50());

        String reccomendation = getRecommendation(highFatigueRisk, percentileRank, riskLevel.riskLevel);

        //textView.setText("Predicted Level: " + (float)predictedFatigueLevel);
        String predictionString = "Predicted Level: " + (float)fatiguePrediction;
        predictionString += "\n";
        predictionString += reccomendation;
        return predictionString;
        //return fatiguePrediction;
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
}