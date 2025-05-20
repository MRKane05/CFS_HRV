package com.example.cfs_hrv;
import java.util.ArrayList;
import java.util.List;

public class MatchedFilter {
    public static List<Double> correlate(List<Double> signal, List<Double> template) {
        int signalLen = signal.size();
        int templateLen = template.size();
        int halfTemplate = templateLen / 2;

        List<Double> result = new ArrayList<>(signalLen);

        for (int i = 0; i < signalLen; i++) {
            double sum = 0;
            for (int j = 0; j < templateLen; j++) {
                int k = i + j - halfTemplate;
                if (k >= 0 && k < signalLen) {
                    sum += signal.get(k) * template.get(j);
                }
            }
            result.add(sum);
        }
        return result;
    }

    public static List<Double> generateSimplePPGTemplate(int length) {
        List<Double> template = new ArrayList<>(length);
        for (int i = 0; i < length; i++) {
            double x = (i - length / 2.0) / (length / 2.0);
            template.add(Math.exp(-x * x * 5)); // Gaussian-shaped pulse
        }
        return template;
    }
}
