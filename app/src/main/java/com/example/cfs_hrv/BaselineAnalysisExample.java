package com.example.cfs_hrv;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class BaselineAnalysisExample {

    public static void main(String[] args) {
        demonstrateBaselineAnalysis();
    }

    public static void demonstrateBaselineAnalysis() {
        // Create sample historical data
        List<ForestDataPoint> historicalData = createHistoricalData();

        // Initialize baseline analyzer with 30-day baseline
        HRVBaselineAnalyzer analyzer = new HRVBaselineAnalyzer(30);

        // Establish baseline from historical data
        analyzer.updateBaseline(historicalData);

        System.out.println("=== BASELINE ESTABLISHED ===");
        System.out.println(analyzer.getBaselineInfo());
        System.out.println();

        // Analyze today's measurement
        analyzeCurrentMeasurement(analyzer, 45.2, 38.1, 15.3, "Normal day");
        analyzeCurrentMeasurement(analyzer, 32.8, 22.4, 8.1, "Potentially stressful day");
        analyzeCurrentMeasurement(analyzer, 55.6, 48.9, 25.7, "Well-recovered day");
        analyzeCurrentMeasurement(analyzer, 28.1, 18.2, 4.2, "High deviation day");
    }

    private static void analyzeCurrentMeasurement(HRVBaselineAnalyzer analyzer,
                                                  double sdnn, double rmssd, double pnn50,
                                                  String scenario) {
        System.out.println("=== " + scenario.toUpperCase() + " ===");
        System.out.println(String.format("Today's HRV: SDNN=%.1f, RMSSD=%.1f, PNN50=%.1f",
                sdnn, rmssd, pnn50));

        // Get deviation analysis
        HRVBaselineAnalyzer.DeviationResult deviation = analyzer.analyzeDeviation(sdnn, rmssd, pnn50);

        System.out.println("Deviation Analysis:");
        System.out.println(String.format("  SDNN Z-Score: %.2f", deviation.sdnnDeviation));
        System.out.println(String.format("  RMSSD Z-Score: %.2f", deviation.rmssdDeviation));
        System.out.println(String.format("  PNN50 Z-Score: %.2f", deviation.pnn50Deviation));
        System.out.println(String.format("  Composite Deviation: %.2f", deviation.compositeDeviation));
        System.out.println(String.format("  Risk Level: %s", deviation.riskLevel));

        // Get percentile rank
        double percentile = analyzer.getPercentileRank(sdnn, rmssd, pnn50);
        System.out.println(String.format("  Percentile Rank: %.1f%%", percentile));

        // High fatigue risk check
        boolean highRisk = analyzer.isHighFatigueRisk(sdnn, rmssd, pnn50);
        System.out.println(String.format("  High Fatigue Risk: %s", highRisk ? "YES" : "NO"));

        System.out.println("Interpretation: " + deviation.interpretation);
        System.out.println();
    }

    private static List<ForestDataPoint> createHistoricalData() {
        List<ForestDataPoint> data = new ArrayList<>();
        Random rand = new Random(42);

        // Create 60 days of historical data with realistic patterns
        for (int day = 0; day < 60; day++) {
            // Base HRV values with some weekly patterns
            double weekFactor = 1.0 + 0.1 * Math.sin(2 * Math.PI * day / 7.0); // Weekly cycle
            double trendFactor = 1.0 + 0.05 * Math.sin(2 * Math.PI * day / 30.0); // Monthly trend

            double baseSDNN = 42.0 * weekFactor * trendFactor;
            double baseRMSSD = 35.0 * weekFactor * trendFactor;
            double basePNN50 = 18.0 * weekFactor * trendFactor;

            // Add individual variation
            double sdnn = Math.max(15, baseSDNN + rand.nextGaussian() * 8);
            double rmssd = Math.max(10, baseRMSSD + rand.nextGaussian() * 7);
            double pnn50 = Math.max(2, basePNN50 + rand.nextGaussian() * 5);

            // Calculate corresponding fatigue (lower HRV = higher fatigue)
            double baseFatigue = 5.0;
            double hrvFatigueEffect = (50 - sdnn) * 0.05 + (40 - rmssd) * 0.08 + (20 - pnn50) * 0.1;
            double fatigue = Math.max(1, Math.min(10, baseFatigue + hrvFatigueEffect + rand.nextGaussian() * 0.8));

            data.add(new ForestDataPoint(sdnn, rmssd, pnn50, fatigue));
        }

        return data;
    }
}

