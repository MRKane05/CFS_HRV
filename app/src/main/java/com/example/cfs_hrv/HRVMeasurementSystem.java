package com.example.cfs_hrv;

import java.util.*;
import java.util.stream.Collectors;

public class HRVMeasurementSystem {

    public static class DataPoint {
        public double value;
        public long timestamp; // milliseconds

        public DataPoint(double value, long timestamp) {
            this.value = value;
            this.timestamp = timestamp;
        }
    }

    public static class HRVMetrics {
        public double meanRR;      // Mean R-R interval (ms)
        public double sdnn;        // Standard deviation of R-R intervals
        public double rmssd;       // Root mean square of successive differences
        public double pnn50;       // Percentage of successive R-R intervals differing by >50ms
        public double heartRate;   // Average heart rate (BPM)
        public int validBeats;     // Number of valid beats detected

        @Override
        public String toString() {
            return String.format("HR: %.1f BPM, SDNN: %.1f ms, RMSSD: %.1f ms, pNN50: %.1f%%, Valid beats: %d",
                    heartRate, sdnn, rmssd, pnn50, validBeats);
        }
    }

    /**
     * Main HRV analysis function
     */
    public static HRVMetrics analyzeHRV(List<DataPoint> rawData, double samplingRate) {
        // Step 1: Preprocess the signal
        List<DataPoint> filteredData = preprocessSignal(rawData, samplingRate);

        // Step 2: Detect R-R intervals (peak-to-peak or trough-to-trough)
        List<Long> rrIntervals = detectRRIntervals(filteredData, samplingRate);

        // Step 3: Clean and validate R-R intervals
        List<Long> cleanRRIntervals = cleanRRIntervals(rrIntervals);

        // Step 4: Calculate HRV metrics
        return calculateHRVMetrics(cleanRRIntervals);
    }

    /**
     * Preprocess the PPG signal with filtering and normalization
     */
    private static List<DataPoint> preprocessSignal(List<DataPoint> data, double samplingRate) {
        if (data.size() < 10) return data;

        // Apply moving average filter to reduce noise
        List<DataPoint> smoothed = applyMovingAverage(data, 5);

        // Apply bandpass filter (0.5-4 Hz for heart rate)
        List<DataPoint> filtered = applyBandpassFilter(smoothed, samplingRate, 0.5, 4.0);

        // Normalize the signal
        return normalizeSignal(filtered);
    }

    /**
     * Simple moving average filter
     */
    private static List<DataPoint> applyMovingAverage(List<DataPoint> data, int windowSize) {
        List<DataPoint> result = new ArrayList<>();

        for (int i = 0; i < data.size(); i++) {
            int start = Math.max(0, i - windowSize / 2);
            int end = Math.min(data.size(), i + windowSize / 2 + 1);

            double sum = 0;
            for (int j = start; j < end; j++) {
                sum += data.get(j).value;
            }

            result.add(new DataPoint(sum / (end - start), data.get(i).timestamp));
        }

        return result;
    }

    /**
     * Simple IIR bandpass filter implementation
     */
    private static List<DataPoint> applyBandpassFilter(List<DataPoint> data, double samplingRate,
                                                       double lowCutoff, double highCutoff) {
        // Simplified Butterworth filter coefficients (would be better to use proper DSP library)
        double nyquist = samplingRate / 2.0;
        double lowNorm = lowCutoff / nyquist;
        double highNorm = highCutoff / nyquist;

        // For simplicity, using a basic high-pass then low-pass approach
        List<DataPoint> highPassed = applyHighPassFilter(data, lowNorm);
        return applyLowPassFilter(highPassed, highNorm);
    }

    private static List<DataPoint> applyHighPassFilter(List<DataPoint> data, double cutoff) {
        List<DataPoint> result = new ArrayList<>();
        double alpha = cutoff;
        double prevInput = data.get(0).value;
        double prevOutput = 0;

        for (DataPoint point : data) {
            double output = alpha * (prevOutput + point.value - prevInput);
            result.add(new DataPoint(output, point.timestamp));
            prevInput = point.value;
            prevOutput = output;
        }

        return result;
    }

    private static List<DataPoint> applyLowPassFilter(List<DataPoint> data, double cutoff) {
        List<DataPoint> result = new ArrayList<>();
        double alpha = cutoff;
        double prevOutput = data.get(0).value;

        for (DataPoint point : data) {
            double output = alpha * point.value + (1 - alpha) * prevOutput;
            result.add(new DataPoint(output, point.timestamp));
            prevOutput = output;
        }

        return result;
    }

    /**
     * Normalize signal to zero mean and unit variance
     */
    private static List<DataPoint> normalizeSignal(List<DataPoint> data) {
        double mean = data.stream().mapToDouble(p -> p.value).average().orElse(0);
        double variance = data.stream().mapToDouble(p -> Math.pow(p.value - mean, 2)).average().orElse(1);
        double std = Math.sqrt(variance);

        return data.stream()
                .map(p -> new DataPoint((p.value - mean) / std, p.timestamp))
                .collect(Collectors.toList());
    }

    /**
     * Detect R-R intervals using adaptive trough detection (recommended for PPG)
     */
    public static List<Integer> troughs= new ArrayList<>();
    private static List<Long> detectRRIntervals(List<DataPoint> data, double samplingRate) {
        troughs = findAdaptiveTroughs(data, samplingRate);
        List<Long> rrIntervals = new ArrayList<>();

        for (int i = 1; i < troughs.size(); i++) {
            int prevTrough = troughs.get(i - 1);
            int currentTrough = troughs.get(i);
            long interval = data.get(currentTrough).timestamp - data.get(prevTrough).timestamp;
            rrIntervals.add(interval);
        }

        return rrIntervals;
    }

    /**
     * Adaptive trough detection with dynamic thresholds (better for PPG signals)
     */
    private static List<Integer> findAdaptiveTroughs(List<DataPoint> data, double samplingRate) {
        List<Integer> troughs = new ArrayList<>();

        // Calculate adaptive parameters
        int minDistance = (int) (0.4 * samplingRate); // Minimum 0.4s between beats (150 BPM max)
        int windowSize = (int) (2.0 * samplingRate);  // 2-second window for threshold adaptation

        for (int i = windowSize; i < data.size() - windowSize; i++) {
            // Calculate local statistics
            double localMean = 0;
            double localStd = 0;

            for (int j = i - windowSize/2; j < i + windowSize/2; j++) {
                localMean += data.get(j).value;
            }
            localMean /= windowSize;

            for (int j = i - windowSize/2; j < i + windowSize/2; j++) {
                localStd += Math.pow(data.get(j).value - localMean, 2);
            }
            localStd = Math.sqrt(localStd / windowSize);

            // Adaptive threshold for troughs (below mean)
            double threshold = localMean - 0.2 * localStd; // Look for values below mean

            // Check if current point is a trough
            if (data.get(i).value < threshold && isTroughCandidate(data, i, minDistance)) {
                // Ensure minimum distance from last trough
                if (troughs.isEmpty() || i - troughs.get(troughs.size() - 1) >= minDistance) {
                    troughs.add(i);
                }
            }
        }

        return troughs;
    }

    /**
     * Check if a point is a local minimum (trough)
     */
    private static boolean isTroughCandidate(List<DataPoint> data, int index, int minDistance) {
        int searchRadius = Math.min(minDistance / 6, 5); // Small search radius for local minimum

        for (int i = Math.max(0, index - searchRadius);
             i <= Math.min(data.size() - 1, index + searchRadius); i++) {
            if (i != index && data.get(i).value <= data.get(index).value) {
                return false;
            }
        }
        return true;
    }

    /**
     * Clean R-R intervals by removing outliers and artifacts
     */
    private static List<Long> cleanRRIntervals(List<Long> rrIntervals) {
        if (rrIntervals.size() < 3) return rrIntervals;

        List<Long> cleaned = new ArrayList<>();

        // Calculate initial statistics
        double mean = rrIntervals.stream().mapToLong(Long::longValue).average().orElse(0);
        double std = Math.sqrt(rrIntervals.stream()
                .mapToDouble(rr -> Math.pow(rr - mean, 2))
                .average().orElse(0));

        // Remove outliers (beyond 3 standard deviations)
        for (Long rr : rrIntervals) {
            if (Math.abs(rr - mean) <= 3 * std && rr >= 300 && rr <= 2000) { // 30-200 BPM range
                cleaned.add(rr);
            }
        }

        // Additional cleaning: remove intervals that differ too much from neighbors
        List<Long> finalCleaned = new ArrayList<>();
        if (!cleaned.isEmpty()) {
            finalCleaned.add(cleaned.get(0));

            for (int i = 1; i < cleaned.size() - 1; i++) {
                long current = cleaned.get(i);
                long prev = cleaned.get(i - 1);
                long next = cleaned.get(i + 1);

                // Check if current interval is reasonable compared to neighbors
                double avgNeighbor = (prev + next) / 2.0;
                if (Math.abs(current - avgNeighbor) < 0.5 * avgNeighbor) {
                    finalCleaned.add(current);
                }
            }

            if (cleaned.size() > 1) {
                finalCleaned.add(cleaned.get(cleaned.size() - 1));
            }
        }

        return finalCleaned;
    }

    /**
     * Calculate HRV metrics from clean R-R intervals
     */
    private static HRVMetrics calculateHRVMetrics(List<Long> rrIntervals) {
        HRVMetrics metrics = new HRVMetrics();

        if (rrIntervals.isEmpty()) {
            return metrics;
        }

        metrics.validBeats = rrIntervals.size();

        // Mean R-R interval
        metrics.meanRR = rrIntervals.stream().mapToLong(Long::longValue).average().orElse(0);

        // Heart rate (BPM)
        metrics.heartRate = 60000.0 / metrics.meanRR; // 60000 ms = 1 minute

        // SDNN (Standard Deviation of R-R intervals)
        double variance = rrIntervals.stream()
                .mapToDouble(rr -> Math.pow(rr - metrics.meanRR, 2))
                .average().orElse(0);
        metrics.sdnn = Math.sqrt(variance);

        // RMSSD (Root Mean Square of Successive Differences)
        if (rrIntervals.size() > 1) {
            double sumSquaredDiffs = 0;
            int pnn50Count = 0;

            for (int i = 1; i < rrIntervals.size(); i++) {
                double diff = rrIntervals.get(i) - rrIntervals.get(i - 1);
                sumSquaredDiffs += diff * diff;

                // Count for pNN50
                if (Math.abs(diff) > 50) {
                    pnn50Count++;
                }
            }

            metrics.rmssd = Math.sqrt(sumSquaredDiffs / (rrIntervals.size() - 1));
            metrics.pnn50 = (double) pnn50Count / (rrIntervals.size() - 1) * 100;
        }

        return metrics;
    }

    /**
     * Example usage and testing
     */
    /*
    public static void main(String[] args) {
        // Example usage
        List<DataPoint> sampleData = generateSampleData();
        HRVMetrics results = analyzeHRV(sampleData, 30.0); // Assuming 30 FPS camera
        System.out.println(results);
    }*/

    private static List<DataPoint> generateSampleData() {
        // Generate sample PPG-like data for testing
        List<DataPoint> data = new ArrayList<>();
        long startTime = System.currentTimeMillis();

        for (int i = 0; i < 1000; i++) {
            // Simulate PPG signal with noise
            double t = i / 30.0; // 30 FPS
            double heartbeat = Math.sin(2 * Math.PI * 1.2 * t); // ~72 BPM
            double noise = (Math.random() - 0.5) * 0.2;
            double value = 76.5 + heartbeat + noise; // Centered around your range

            data.add(new DataPoint(value, startTime + (long)(t * 1000)));
        }

        return data;
    }
}