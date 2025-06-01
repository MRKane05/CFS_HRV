package com.example.cfs_hrv;
import java.util.*;

// Class to analyze how far current HRV deviates from normal baseline
public class HRVBaselineAnalyzer {

    // Baseline statistics for each HRV metric
    public static class BaselineStats {
        public double mean;
        public double standardDeviation;
        public double median;
        public double percentile25;
        public double percentile75;

        public BaselineStats(double mean, double std, double median, double p25, double p75) {
            this.mean = mean;
            this.standardDeviation = std;
            this.median = median;
            this.percentile25 = p25;
            this.percentile75 = p75;
        }

        @Override
        public String toString() {
            return String.format("Mean: %.2f, Std: %.2f, Median: %.2f", mean, standardDeviation, median);
        }
    }

    // Result of baseline deviation analysis
    public static class DeviationResult {
        public double sdnnDeviation;
        public double rmssdDeviation;
        public double pnn50Deviation;
        public double compositeDeviation;
        public String riskLevel;
        public String interpretation;

        public DeviationResult(double sdnn, double rmssd, double pnn50, double composite, String risk, String interp) {
            this.sdnnDeviation = sdnn;
            this.rmssdDeviation = rmssd;
            this.pnn50Deviation = pnn50;
            this.compositeDeviation = composite;
            this.riskLevel = risk;
            this.interpretation = interp;
        }
    }

    private BaselineStats sdnnBaseline;
    private BaselineStats rmssdBaseline;
    private BaselineStats pnn50Baseline;
    private List<ForestDataPoint> historicalData;
    private int baselineDays;

    public HRVBaselineAnalyzer(int baselineDays) {
        this.baselineDays = baselineDays;
        this.historicalData = new ArrayList<>();
    }

    // Update baseline with historical data
    public void updateBaseline(List<ForestDataPoint> data) {
        if (data.isEmpty()) {
            throw new IllegalArgumentException("Cannot create baseline from empty data");
        }

        this.historicalData = new ArrayList<>(data);

        // Use most recent data for baseline (or all data if less than baselineDays)
        List<ForestDataPoint> baselineData = data;
        if (data.size() > baselineDays) {
            baselineData = data.subList(data.size() - baselineDays, data.size());
        }

        // Calculate baseline statistics for each metric
        this.sdnnBaseline = calculateBaselineStats(baselineData, "sdnn");
        this.rmssdBaseline = calculateBaselineStats(baselineData, "rmssd");
        this.pnn50Baseline = calculateBaselineStats(baselineData, "pnn50");
    }

    private BaselineStats calculateBaselineStats(List<ForestDataPoint> data, String metric) {
        List<Double> values = new ArrayList<>();

        for (ForestDataPoint point : data) {
            switch (metric) {
                case "sdnn":
                    values.add(point.sdnn);
                    break;
                case "rmssd":
                    values.add(point.rmssd);
                    break;
                case "pnn50":
                    values.add(point.pnn50);
                    break;
            }
        }

        Collections.sort(values);

        double mean = values.stream().mapToDouble(Double::doubleValue).average().orElse(0);
        double variance = values.stream()
                .mapToDouble(val -> Math.pow(val - mean, 2))
                .average().orElse(0);
        double std = Math.sqrt(variance);

        double median = getPercentile(values, 50);
        double p25 = getPercentile(values, 25);
        double p75 = getPercentile(values, 75);

        return new BaselineStats(mean, std, median, p25, p75);
    }

    private double getPercentile(List<Double> sortedValues, double percentile) {
        if (sortedValues.isEmpty()) return 0;

        double index = (percentile / 100.0) * (sortedValues.size() - 1);
        int lowerIndex = (int) Math.floor(index);
        int upperIndex = (int) Math.ceil(index);

        if (lowerIndex == upperIndex) {
            return sortedValues.get(lowerIndex);
        }

        double weight = index - lowerIndex;
        return sortedValues.get(lowerIndex) * (1 - weight) + sortedValues.get(upperIndex) * weight;
    }

    // Analyze how far current HRV deviates from baseline
    public DeviationResult analyzeDeviation(double currentSDNN, double currentRMSSD, double currentPNN50) {
        if (sdnnBaseline == null || rmssdBaseline == null || pnn50Baseline == null) {
            throw new IllegalStateException("Baseline not established. Call updateBaseline() first.");
        }

        // Calculate z-scores (standard deviations from mean)
        double sdnnZScore = (currentSDNN - sdnnBaseline.mean) / sdnnBaseline.standardDeviation;
        double rmssdZScore = (currentRMSSD - rmssdBaseline.mean) / rmssdBaseline.standardDeviation;
        double pnn50ZScore = (currentPNN50 - pnn50Baseline.mean) / pnn50Baseline.standardDeviation;

        // Calculate composite deviation (weighted average of absolute z-scores)
        // RMSSD often most sensitive to autonomic changes, so weight it higher
        double compositeDeviation = (Math.abs(sdnnZScore) * 0.3 +
                Math.abs(rmssdZScore) * 0.5 +
                Math.abs(pnn50ZScore) * 0.2);

        // Determine risk level and interpretation
        String riskLevel = determineRiskLevel(compositeDeviation);
        String interpretation = generateInterpretation(sdnnZScore, rmssdZScore, pnn50ZScore, compositeDeviation);

        return new DeviationResult(sdnnZScore, rmssdZScore, pnn50ZScore, compositeDeviation, riskLevel, interpretation);
    }

    private String determineRiskLevel(double compositeDeviation) {
        if (compositeDeviation <= 1.0) {
            return "NORMAL";
        } else if (compositeDeviation <= 1.5) {
            return "MILD_DEVIATION";
        } else if (compositeDeviation <= 2.0) {
            return "MODERATE_DEVIATION";
        } else {
            return "HIGH_DEVIATION";
        }
    }

    private String generateInterpretation(double sdnnZ, double rmssdZ, double pnn50Z, double composite) {
        StringBuilder interpretation = new StringBuilder();

        // Overall assessment
        if (composite <= 1.0) {
            interpretation.append("HRV is within normal baseline range. ");
        } else if (composite <= 1.5) {
            interpretation.append("HRV shows mild deviation from baseline. ");
        } else if (composite <= 2.0) {
            interpretation.append("HRV shows moderate deviation from baseline. Consider lighter training. ");
        } else {
            interpretation.append("HRV shows significant deviation from baseline. Consider rest or very light activity. ");
        }

        // Specific metric insights
        List<String> lowMetrics = new ArrayList<>();
        List<String> highMetrics = new ArrayList<>();

        if (sdnnZ < -1.5) lowMetrics.add("SDNN");
        if (rmssdZ < -1.5) lowMetrics.add("RMSSD");
        if (pnn50Z < -1.5) lowMetrics.add("PNN50");

        if (sdnnZ > 1.5) highMetrics.add("SDNN");
        if (rmssdZ > 1.5) highMetrics.add("RMSSD");
        if (pnn50Z > 1.5) highMetrics.add("PNN50");

        if (!lowMetrics.isEmpty()) {
            interpretation.append("Lower than normal: ").append(String.join(", ", lowMetrics)).append(". ");
        }
        if (!highMetrics.isEmpty()) {
            interpretation.append("Higher than normal: ").append(String.join(", ", highMetrics)).append(". ");
        }

        return interpretation.toString().trim();
    }

    // Get percentile rank of current measurement
    public double getPercentileRank(double currentSDNN, double currentRMSSD, double currentPNN50) {
        if (historicalData.isEmpty()) return 50.0; // Default to median if no data

        // Calculate composite HRV score for current measurement
        double currentComposite = calculateCompositeHRV(currentSDNN, currentRMSSD, currentPNN50);

        // Calculate composite scores for all historical data
        List<Double> historicalComposites = new ArrayList<>();
        for (ForestDataPoint point : historicalData) {
            historicalComposites.add(calculateCompositeHRV(point.sdnn, point.rmssd, point.pnn50));
        }

        Collections.sort(historicalComposites);

        // Find percentile rank
        int countBelow = 0;
        for (double historical : historicalComposites) {
            if (historical < currentComposite) {
                countBelow++;
            }
        }

        return (double) countBelow / historicalComposites.size() * 100.0;
    }

    private double calculateCompositeHRV(double sdnn, double rmssd, double pnn50) {
        // Normalize each metric by its baseline and create composite
        if (sdnnBaseline == null) return 0;

        double sdnnNorm = sdnn / sdnnBaseline.mean;
        double rmssdNorm = rmssd / rmssdBaseline.mean;
        double pnn50Norm = pnn50 / pnn50Baseline.mean;

        // Weighted composite (RMSSD weighted higher as it's more sensitive)
        return sdnnNorm * 0.3 + rmssdNorm * 0.5 + pnn50Norm * 0.2;
    }

    // Check if current measurement suggests high fatigue risk
    public boolean isHighFatigueRisk(double currentSDNN, double currentRMSSD, double currentPNN50) {
        DeviationResult deviation = analyzeDeviation(currentSDNN, currentRMSSD, currentPNN50);

        // High risk if significant deviation OR if HRV is well below baseline
        return deviation.compositeDeviation > 2.0 ||
                (deviation.sdnnDeviation > 1.5 && deviation.rmssdDeviation > 1.5);
    }

    // Get baseline information for display
    public String getBaselineInfo() {
        if (sdnnBaseline == null) return "Baseline not established";

        return String.format("Baseline (%d days):\nSDNN: %s\nRMSSD: %s\nPNN50: %s",
                baselineDays,
                sdnnBaseline.toString(),
                rmssdBaseline.toString(),
                pnn50Baseline.toString());
    }

    // Update baseline with new data point (rolling window)
    public void addForestDataPoint(ForestDataPoint newData) {
        historicalData.add(newData);

        // Keep only the most recent baselineDays worth of data
        if (historicalData.size() > baselineDays * 2) { // Keep extra for stability
            historicalData = historicalData.subList(historicalData.size() - baselineDays, historicalData.size());
        }

        // Recalculate baseline periodically (e.g., every 7 days)
        if (historicalData.size() % 7 == 0) {
            updateBaseline(historicalData);
        }
    }
}