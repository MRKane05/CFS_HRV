package com.example.cfs_hrv;

import java.util.ArrayList;
import java.util.List;

// Android Integration Example
public class HRVTrackingActivity {
    private HRVBaselineAnalyzer baselineAnalyzer;
    private RandomForest fatigueModel; // From previous implementation

    public void initializeAnalysis() {
        // Initialize with 30-day baseline
        baselineAnalyzer = new HRVBaselineAnalyzer(30);

        // Load historical data and establish baseline
        List<ForestDataPoint> historicalData = loadHistoricalDataFromDB();
        baselineAnalyzer.updateBaseline(historicalData);

        // Also initialize fatigue prediction model
        fatigueModel = new RandomForest(50, 10, 5);
        fatigueModel.train(historicalData);
    }

    // Comprehensive analysis of today's HRV measurement
    public DailyHRVAnalysis analyzeTodaysHRV(double sdnn, double rmssd, double pnn50) {
        // 1. Baseline deviation analysis
        HRVBaselineAnalyzer.DeviationResult deviation = baselineAnalyzer.analyzeDeviation(sdnn, rmssd, pnn50);

        // 2. Fatigue prediction
        double predictedFatigue = fatigueModel.predict(sdnn, rmssd, pnn50);

        // 3. Percentile ranking
        double percentileRank = baselineAnalyzer.getPercentileRank(sdnn, rmssd, pnn50);

        // 4. Risk assessment
        boolean highFatigueRisk = baselineAnalyzer.isHighFatigueRisk(sdnn, rmssd, pnn50);

        return new DailyHRVAnalysis(deviation, predictedFatigue, percentileRank, highFatigueRisk);
    }

    // Add new measurement to update baseline and model
    public void recordEndOfDayData(double sdnn, double rmssd, double pnn50, double actualFatigue) {
        ForestDataPoint newData = new ForestDataPoint(sdnn, rmssd, pnn50, actualFatigue);

        // Update baseline (rolling window)
        baselineAnalyzer.addForestDataPoint(newData);

        // Save to database
        saveToDatabase(newData);

        // Periodically retrain fatigue model (e.g., weekly)
        if (shouldRetrainModel()) {
            List<ForestDataPoint> updatedData = loadHistoricalDataFromDB();
            fatigueModel.train(updatedData);
        }
    }

    // Result class for comprehensive analysis
    public static class DailyHRVAnalysis {
        public HRVBaselineAnalyzer.DeviationResult baselineDeviation;
        public double predictedFatigue;
        public double percentileRank;
        public boolean highFatigueRisk;

        public DailyHRVAnalysis(HRVBaselineAnalyzer.DeviationResult deviation,
                                double fatigue, double percentile, boolean risk) {
            this.baselineDeviation = deviation;
            this.predictedFatigue = fatigue;
            this.percentileRank = percentile;
            this.highFatigueRisk = risk;
        }

        public String getRecommendation() {
            if (highFatigueRisk || baselineDeviation.riskLevel.equals("HIGH_DEVIATION")) {
                return "Consider rest or very light activity today. Your HRV suggests your body may need recovery.";
            } else if (baselineDeviation.riskLevel.equals("MODERATE_DEVIATION")) {
                return "Moderate training recommended. Listen to your body and adjust intensity as needed.";
            } else if (percentileRank > 75) {
                return "Great recovery! You're in good shape for normal or higher intensity training.";
            } else {
                return "Normal training intensity is appropriate based on your baseline.";
            }
        }
    }

    private List<ForestDataPoint> loadHistoricalDataFromDB() {
        // Implementation depends on your database structure
        return new ArrayList<>();
    }

    private void saveToDatabase(ForestDataPoint data) {
        // Save to your SQLite database
    }

    private boolean shouldRetrainModel() {
        // Return true if it's been a week since last retraining, etc.
        return false;
    }
}
