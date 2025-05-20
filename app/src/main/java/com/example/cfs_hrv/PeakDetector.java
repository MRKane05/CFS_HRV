package com.example.cfs_hrv;
import java.util.ArrayList;
import java.util.List;

public class PeakDetector {
    public static List<Integer> detectPeaks(List<Double> data, double threshold, int minDistance) {
        List<Integer> peaks = new ArrayList<>();
        int lastPeak = -minDistance;

        for (int i = 1; i < data.size() - 1; i++) {
            double prev = data.get(i - 1);
            double curr = data.get(i);
            double next = data.get(i + 1);

            if (curr > threshold && curr > prev && curr > next) {
                if (i - lastPeak >= minDistance) {
                    peaks.add(i);
                    lastPeak = i;
                }
            }
        }
        return peaks;
    }
}
