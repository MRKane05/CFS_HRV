package com.example.cfs_hrv;
import java.util.ArrayList;
import java.util.List;

public class SavitzkyGolayFilter {
    public static List<Double> smooth(List<Double> y, int windowSize, int polynomialOrder) {
        if (windowSize % 2 == 0 || windowSize < 3)
            throw new IllegalArgumentException("Window size must be odd and >= 3");

        int halfWindow = windowSize / 2;
        int n = y.size();
        List<Double> result = new ArrayList<>(n);

        double[] coeffs = generateCoefficients(windowSize, polynomialOrder);

        for (int i = 0; i < n; i++) {
            double sum = 0;
            for (int j = -halfWindow; j <= halfWindow; j++) {
                int k = i + j;
                if (k < 0) k = 0;
                if (k >= n) k = n - 1;
                sum += coeffs[j + halfWindow] * y.get(k);
            }
            result.add(sum);
        }
        return result;
    }

    private static double[] generateCoefficients(int windowSize, int polyOrder) {
        // Hardcoded for common SG(5,2) only
        if (windowSize == 5 && polyOrder == 2) {
            return new double[]{-3.0 / 35, 12.0 / 35, 17.0 / 35, 12.0 / 35, -3.0 / 35};
        }
        throw new UnsupportedOperationException("Only SG(5,2) supported.");
    }
}