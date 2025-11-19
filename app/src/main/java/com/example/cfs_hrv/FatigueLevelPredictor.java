package com.example.cfs_hrv;

import java.util.List;
import java.util.stream.Collectors;

public class FatigueLevelPredictor {

    /**
     * Predicts fatigue level based on RMSSD and heart rate using historical data patterns
     * @param historicalData List of previous HRVData measurements with known fatigue levels
     * @param newMeasurement New HRVData measurement (fatigue level will be predicted)
     * @return Predicted fatigue level (1-5 scale)
     */

    public static int predictFatigueLevel(List<HRVData> historicalData, HRVData newMeasurement) {
        if (historicalData == null || historicalData.isEmpty()) {
            return 3; // Default to moderate fatigue if no historical data
        }

        // Filter out any invalid historical data
        List<HRVData> validHistoricalData = historicalData.stream()
                .filter(data -> data.getFatigueLevel() >= 1 && data.getFatigueLevel() <= 5)
                .filter(data -> data.getRmssd() > 0 && data.getHeartRate() > 0)
                .collect(Collectors.toList());

        if (validHistoricalData.isEmpty()) {
            return 3;
        }

        // Calculate historical averages for each fatigue level
        double[] avgRmssdByFatigue = new double[6]; // Index 0 unused, 1-5 for fatigue levels
        //double[] avgHRByFatigue = new double[6];
        int[] countByFatigue = new int[6];

        for (HRVData data : validHistoricalData) {
            int fatigueLevel = data.getFatigueLevel();
            avgRmssdByFatigue[fatigueLevel] += data.getRmssd();
            //avgHRByFatigue[fatigueLevel] += data.getHeartRate();
            countByFatigue[fatigueLevel]++;
        }

        // Calculate averages
        for (int i = 1; i <= 5; i++) {
            if (countByFatigue[i] > 0) {
                avgRmssdByFatigue[i] /= countByFatigue[i];
                //avgHRByFatigue[i] /= countByFatigue[i];
            }
        }

        // Find the closest match based on RMSSD and HR patterns
        double bestScore = Double.MAX_VALUE;
        int predictedFatigue = 3;

        for (int fatigueLevel = 1; fatigueLevel <= 5; fatigueLevel++) {
            if (countByFatigue[fatigueLevel] == 0) continue;

            // Calculate weighted distance (RMSSD weighted more heavily based on observed correlation)
            double rmssdDiff = Math.abs(newMeasurement.getRmssd() - avgRmssdByFatigue[fatigueLevel]);
            //double hrDiff = Math.abs(newMeasurement.getHeartRate() - avgHRByFatigue[fatigueLevel]);

            // Weight RMSSD more heavily since it showed stronger correlation
            //Need to remove the hrDiff as we're not calculating brachycardia with this measure due to it being ineffective
            double score = rmssdDiff; //(rmssdDiff * 0.7) + (hrDiff * 0.3);

            if (score < bestScore) {
                bestScore = score;
                predictedFatigue = fatigueLevel;
            }
        }

        return predictedFatigue;
    }

    /**
     * Enhanced prediction that also considers recent trends
     * @param historicalData List of previous HRVData measurements
     * @param newMeasurement New measurement to predict
     * @param recentDaysToConsider Number of recent days to weight more heavily
     * @return Predicted fatigue level with trend consideration
     */
    public static int predictFatigueLevelWithTrend(List<HRVData> historicalData,
                                                   HRVData newMeasurement,
                                                   int recentDaysToConsider) {
        if (historicalData == null || historicalData.size() < 2) {
            return predictFatigueLevel(historicalData, newMeasurement);
        }

        // Get base prediction
        int basePrediction = predictFatigueLevel(historicalData, newMeasurement);

        // Calculate recent trend
        List<HRVData> recentData = historicalData.stream()
                .sorted((a, b) -> Long.compare(b.getTimestamp(), a.getTimestamp()))
                .limit(recentDaysToConsider)
                .collect(Collectors.toList());

        if (recentData.size() < 2) {
            return basePrediction;
        }

        // Calculate average fatigue of recent measurements
        double recentAvgFatigue = recentData.stream()
                .mapToInt(HRVData::getFatigueLevel)
                .average()
                .orElse(3.0);

        // Adjust prediction based on recent trend (slight bias toward recent patterns)
        double adjustedPrediction = (basePrediction * 0.8) + (recentAvgFatigue * 0.2);

        return Math.max(1, Math.min(5, (int) Math.round(adjustedPrediction)));
    }

    /**
     * Provides confidence level for the prediction
     * @param historicalData Historical data used for prediction
     * @param predictedLevel The predicted fatigue level
     * @return Confidence percentage (0-100)
     */
    public static double getPredictionConfidence(List<HRVData> historicalData, int predictedLevel) {
        if (historicalData == null || historicalData.isEmpty()) {
            return 50.0; // Low confidence with no data
        }

        // Count how many historical data points match this fatigue level
        long matchingCount = historicalData.stream()
                .filter(data -> data.getFatigueLevel() == predictedLevel)
                .count();

        // Confidence based on sample size and data consistency
        double baseConfidence = Math.min(90.0, 30.0 + (matchingCount * 10.0));

        // Reduce confidence if we have very few total measurements
        if (historicalData.size() < 5) {
            baseConfidence *= 0.7;
        }

        return Math.max(25.0, baseConfidence);
    }

    public static double getDailyScore(List<HRVData> historicalData, HRVData newMeasurement) {

        if (historicalData == null || historicalData.isEmpty()) {
            return 3; // Default to moderate fatigue if no historical data
        }

        // Filter out any invalid historical data
        List<HRVData> validHistoricalData = historicalData.stream()
                .filter(data -> data.getFatigueLevel() >= 1 && data.getFatigueLevel() <= 5)
                .filter(data -> data.getRmssd() > 0 && data.getHeartRate() > 0)
                .collect(Collectors.toList());

        if (validHistoricalData.isEmpty()) {
            return 0;
        }

        double averageRMSSD = 0;
        double averageHR = 0;
        for (HRVData data : validHistoricalData) {
            averageRMSSD += data.getRmssd();
            averageHR += data.getHeartRate();
        }

        averageRMSSD /= validHistoricalData.size();
        averageHR /= validHistoricalData.size();

        return  ((newMeasurement.getRmssd() / averageRMSSD) * 0.7) + ((newMeasurement.getHeartRate() / averageHR) * 0.3);
    }

        //Range string approach
    /**
     * Predicts fatigue level based on RMSSD and heart rate using historical data patterns
     * @param historicalData List of previous HRVData measurements with known fatigue levels
     * @param newMeasurement New HRVData measurement (fatigue level will be predicted)
     * @return Predicted fatigue level as string (single value or range like "2-3")
     */
    public static String predictFatigueLevelRange(List<HRVData> historicalData, HRVData newMeasurement) {
        if (historicalData == null || historicalData.isEmpty()) {
            return "No Data Avaliable"; // Default to moderate range if no historical data
        }

        // Filter out any invalid historical data
        List<HRVData> validHistoricalData = historicalData.stream()
                .filter(data -> data.getFatigueLevel() >= 1 && data.getFatigueLevel() <= 5)
                .filter(data -> data.getRmssd() > 0 && data.getHeartRate() > 0)
                .collect(Collectors.toList());

        if (validHistoricalData.isEmpty()) {
            return "No Data Avaliable";
        }

        // Calculate historical averages for each fatigue level
        double[] avgRmssdByFatigue = new double[6]; // Index 0 unused, 1-5 for fatigue levels
        //double[] avgHRByFatigue = new double[6];
        int[] countByFatigue = new int[6];

        for (HRVData data : validHistoricalData) {
            int fatigueLevel = data.getFatigueLevel();
            avgRmssdByFatigue[fatigueLevel] += data.getRmssd();
            //avgHRByFatigue[fatigueLevel] += data.getHeartRate();
            countByFatigue[fatigueLevel]++;
        }

        // Calculate averages
        for (int i = 1; i <= 5; i++) {
            if (countByFatigue[i] > 0) {
                avgRmssdByFatigue[i] /= countByFatigue[i];
                //avgHRByFatigue[i] /= countByFatigue[i];
            }
        }

        // Find the closest matches and determine confidence
        double[] scores = new double[6]; // Index 0 unused, 1-5 for fatigue levels
        for (int i = 1; i <= 5; i++) {
            scores[i] = Double.MAX_VALUE;
        }

        for (int fatigueLevel = 1; fatigueLevel <= 5; fatigueLevel++) {
            if (countByFatigue[fatigueLevel] == 0) continue;

            // Calculate weighted distance (RMSSD weighted more heavily based on observed correlation)
            double rmssdDiff = Math.abs(newMeasurement.getRmssd() - avgRmssdByFatigue[fatigueLevel]);
            //double hrDiff = Math.abs(newMeasurement.getHeartRate() - avgHRByFatigue[fatigueLevel]);

            // Weight RMSSD more heavily since it showed stronger correlation
            // From further reading we shouldn't consider the bradycardia symptoms in this reading, so lets drop the hrDiff
            scores[fatigueLevel] = rmssdDiff; //(rmssdDiff * 0.7) + (hrDiff * 0.3);
        }

        // Find best match and check for close alternatives
        int bestMatch = 1;
        double bestScore = scores[1];
        for (int i = 2; i <= 5; i++) {
            if (scores[i] < bestScore) {
                bestScore = scores[i];
                bestMatch = i;
            }
        }

        // Check if there are other close matches (within 15% of best score)
        double threshold = bestScore * 1.15;
        int closestLower = bestMatch;
        int closestHigher = bestMatch;

        // Find closest lower value
        for (int i = bestMatch - 1; i >= 1; i--) {
            if (scores[i] <= threshold) {
                closestLower = i;
            } else {
                break;
            }
        }

        // Find closest higher value
        for (int i = bestMatch + 1; i <= 5; i++) {
            if (scores[i] <= threshold) {
                closestHigher = i;
            } else {
                break;
            }
        }

        // Determine if we should return a range or single value
        if (closestLower == closestHigher) {
            // High confidence - single value
            return String.valueOf(bestMatch);
        } else if (closestHigher - closestLower == 1) {
            // Adjacent values - return range
            return closestLower + "-" + closestHigher;
        } else {
            // Multiple close matches - return range around best match
            int rangeStart = Math.max(1, bestMatch - 1);
            int rangeEnd = Math.min(5, bestMatch + 1);
            return rangeStart + "-" + rangeEnd;
        }
    }

    /**
     * Enhanced prediction that also considers recent trends
     * @param historicalData List of previous HRVData measurements
     * @param newMeasurement New measurement to predict
     * @param recentDaysToConsider Number of recent days to weight more heavily
     * @return Predicted fatigue level as string with trend consideration
     */
    /**
     * Enhanced prediction that also considers recent trends
     * @param historicalData List of previous HRVData measurements
     * @param newMeasurement New measurement to predict
     * @param recentDaysToConsider Number of recent days to weight more heavily
     * @return Predicted fatigue level as string with trend consideration
     */
    public static String predictFatigueLevelRangeWithTrend(List<HRVData> historicalData,
                                                      HRVData newMeasurement,
                                                      int recentDaysToConsider) {
        if (historicalData == null || historicalData.size() < 5) {
            return predictFatigueLevelRange(historicalData, newMeasurement);
        }

        // Get base prediction
        String basePrediction = predictFatigueLevelRange(historicalData, newMeasurement);

        // If base prediction is already a range, trust it
        if (basePrediction.contains("No")) {
            return basePrediction;
        }

        // Calculate recent trend
        List<HRVData> recentData = historicalData.stream()
                .sorted((a, b) -> Long.compare(b.getTimestamp(), a.getTimestamp()))
                .limit(recentDaysToConsider)
                .collect(Collectors.toList());

        if (recentData.size() < 2) {
            return basePrediction;
        }

        // Calculate average fatigue of recent measurements
        double recentAvgFatigue = recentData.stream()
                .mapToInt(HRVData::getFatigueLevel)
                .average()
                .orElse(3.0);

        // Parse base prediction (could be single value or range)
        int baseLow, baseHigh;
        if (basePrediction.contains("-")) {
            String[] parts = basePrediction.split("-");
            baseLow = Integer.parseInt(parts[0]);
            baseHigh = Integer.parseInt(parts[1]);
        } else {
            baseLow = baseHigh = Integer.parseInt(basePrediction);
        }

        // Calculate recent trend
        int trendInt = (int) Math.round(recentAvgFatigue);

        // Check if recent trend differs from base prediction range
        boolean trendOutsideBase = trendInt < baseLow || trendInt > baseHigh;
        double trendDifference = Math.min(Math.abs(recentAvgFatigue - baseLow),
                Math.abs(recentAvgFatigue - baseHigh));

        if (trendOutsideBase && trendDifference > 0.6) {
            // Significant trend difference - expand range to include trend
            int finalLow = Math.max(1, Math.min(baseLow, trendInt));
            int finalHigh = Math.min(5, Math.max(baseHigh, trendInt));

            if (finalLow == finalHigh) {
                return String.valueOf(finalLow);
            } else {
                return finalLow + "-" + finalHigh;
            }
        } else if (trendOutsideBase && trendDifference > 0.3) {
            // Moderate trend difference - expand range slightly
            int finalLow = Math.max(1, Math.min(baseLow, baseLow - 1));
            int finalHigh = Math.min(5, Math.max(baseHigh, baseHigh + 1));

            if (finalLow == finalHigh) {
                return String.valueOf(finalLow);
            } else {
                return finalLow + "-" + finalHigh;
            }
        }

        return basePrediction;
    }

    /**
     * Provides confidence level for the prediction
     * @param historicalData Historical data used for prediction
     * @param predictedLevel The predicted fatigue level
     * @return Confidence percentage (0-100)
     */
    public static double getPredictionStringConfidence(List<HRVData> historicalData, int predictedLevel) {
        if (historicalData == null || historicalData.isEmpty()) {
            return 50.0; // Low confidence with no data
        }

        // Count how many historical data points match this fatigue level
        long matchingCount = historicalData.stream()
                .filter(data -> data.getFatigueLevel() == predictedLevel)
                .count();

        // Confidence based on sample size and data consistency
        double baseConfidence = Math.min(90.0, 30.0 + (matchingCount * 10.0));

        // Reduce confidence if we have very few total measurements
        if (historicalData.size() < 5) {
            baseConfidence *= 0.7;
        }

        return Math.max(25.0, baseConfidence);
    }
}