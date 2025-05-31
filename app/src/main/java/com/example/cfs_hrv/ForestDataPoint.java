package com.example.cfs_hrv;

// Data point class to hold your HRV data and fatigue target
public class ForestDataPoint {
    public double sdnn;
    public double rmssd;
    public double pnn50;
    public double fatigueLevel; // target variable to predict

    public ForestDataPoint(double sdnn, double rmssd, double pnn50, double fatigueLevel) {
        this.sdnn = sdnn;
        this.rmssd = rmssd;
        this.pnn50 = pnn50;
        this.fatigueLevel = fatigueLevel;
    }

    public double[] getFeatures() {
        return new double[]{sdnn, rmssd, pnn50}; // Only HRV features for prediction
    }
}