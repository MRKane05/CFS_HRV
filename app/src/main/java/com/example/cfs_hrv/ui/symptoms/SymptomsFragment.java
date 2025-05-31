package com.example.cfs_hrv.ui.symptoms;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;

import com.example.cfs_hrv.ForestDataPoint;
import com.example.cfs_hrv.HRVData;
import com.example.cfs_hrv.HRVDataManager;
import com.example.cfs_hrv.HRVMeasurementSystem;
import com.example.cfs_hrv.RandomForest;
import com.example.cfs_hrv.databinding.FragmentDashboardBinding;

import java.util.ArrayList;
import java.util.List;

public class SymptomsFragment extends Fragment {

    private FragmentDashboardBinding binding;

    public View onCreateView(@NonNull LayoutInflater inflater,
                             ViewGroup container, Bundle savedInstanceState) {
        SymptomsViewModel symptomsViewModel =
                new ViewModelProvider(this).get(SymptomsViewModel.class);

        binding = FragmentDashboardBinding.inflate(inflater, container, false);
        View root = binding.getRoot();

        final TextView textView = binding.textDashboard;
        symptomsViewModel.getText().observe(getViewLifecycleOwner(), textView::setText);



        double predictedFatigueLevel = MakePrediction();



        return root;
    }

    double MakePrediction() {
        double fatiguePrediction = -1;
        HRVDataManager hrvData = new HRVDataManager(getContext()); //This is a terrible way of doing things...

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

        HRVData dataEntry = allHRVData.get(allHRVData.size()-1);
        //(double sdnn, double rmssd, double pnn50) {
        fatiguePrediction = fatigueModel.predict(dataEntry.getSdnn(), dataEntry.getRmssd(), dataEntry.getPnn50());
        return fatiguePrediction;
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}