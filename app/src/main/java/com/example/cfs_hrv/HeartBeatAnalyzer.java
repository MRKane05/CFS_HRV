package com.example.cfs_hrv;

import java.util.*;

public class HeartBeatAnalyzer {

    // Represents a fitted cubic polynomial: y = a*x^3 + b*x^2 + c*x + d
    static class Cubic {
        double a, b, c, d;

        // Returns the derivative at a given x
        double derivative(double x) {
            return 3 * a * x * x + 2 * b * x + c;
        }

        // Returns the second derivative (for checking curvature)
        double secondDerivative(double x) {
            return 6 * a * x + 2 * b;
        }

        // Returns value at x
        double evaluate(double x) {
            return a * x * x * x + b * x * x + c * x + d;
        }
    }

    public static double findSteepestDrop(List<Double> data, int startIndex, int window) {
        int n = window + 1;
        double[] x = new double[n];
        double[] y = new double[n];

        for (int i = 0; i < n; i++) {
            x[i] = i;  // relative index
            y[i] = data.get(startIndex + i);
        }

        Cubic poly = fitCubic(x, y);

        // Derivative of cubic is quadratic: f'(x) = 3ax^2 + 2bx + c
        // We sample derivative at multiple points in the interval to find steepest slope
        double minSlope = Double.POSITIVE_INFINITY;
        double bestX = 0.0;

        for (double xi = 0.0; xi <= n - 1; xi += 0.01) {
            double slope = poly.derivative(xi);
            if (slope < minSlope) {
                minSlope = slope;
                bestX = xi;
            }
        }

        // Return floating-point index relative to full signal
        return startIndex + bestX;
    }

    // Least squares cubic fit
    private static Cubic fitCubic(double[] x, double[] y) {
        int n = x.length;

        double sumX0 = n;
        double sumX1 = 0, sumX2 = 0, sumX3 = 0, sumX4 = 0, sumX5 = 0, sumX6 = 0;
        double sumY = 0, sumYX1 = 0, sumYX2 = 0, sumYX3 = 0;

        for (int i = 0; i < n; i++) {
            double xi = x[i];
            double yi = y[i];

            double xi2 = xi * xi;
            double xi3 = xi2 * xi;
            double xi4 = xi3 * xi;
            double xi5 = xi4 * xi;
            double xi6 = xi5 * xi;

            sumX1 += xi;
            sumX2 += xi2;
            sumX3 += xi3;
            sumX4 += xi4;
            sumX5 += xi5;
            sumX6 += xi6;

            sumY += yi;
            sumYX1 += yi * xi;
            sumYX2 += yi * xi2;
            sumYX3 += yi * xi3;
        }

        // Solve linear system A * coeffs = B for [d, c, b, a]
        double[][] A = {
                {sumX0, sumX1, sumX2, sumX3},
                {sumX1, sumX2, sumX3, sumX4},
                {sumX2, sumX3, sumX4, sumX5},
                {sumX3, sumX4, sumX5, sumX6}
        };

        double[] B = {sumY, sumYX1, sumYX2, sumYX3};

        double[] coeffs = gaussianElimination(A, B);
        Cubic poly = new Cubic();
        poly.d = coeffs[0];
        poly.c = coeffs[1];
        poly.b = coeffs[2];
        poly.a = coeffs[3];
        return poly;
    }

    // Solves Ax = B using Gaussian elimination
    private static double[] gaussianElimination(double[][] A, double[] B) {
        int n = B.length;
        for (int i = 0; i < n; i++) {
            // Pivot
            int maxRow = i;
            for (int k = i + 1; k < n; k++)
                if (Math.abs(A[k][i]) > Math.abs(A[maxRow][i]))
                    maxRow = k;

            // Swap rows
            double[] tmp = A[i]; A[i] = A[maxRow]; A[maxRow] = tmp;
            double t = B[i]; B[i] = B[maxRow]; B[maxRow] = t;

            // Eliminate
            for (int k = i + 1; k < n; k++) {
                double factor = A[k][i] / A[i][i];
                for (int j = i; j < n; j++)
                    A[k][j] -= factor * A[i][j];
                B[k] -= factor * B[i];
            }
        }

        // Back substitution
        double[] x = new double[n];
        for (int i = n - 1; i >= 0; i--) {
            x[i] = B[i];
            for (int j = i + 1; j < n; j++)
                x[i] -= A[i][j] * x[j];
            x[i] /= A[i][i];
        }

        return x;
    }

    // Example usage
    public static void main(String[] args) {
        List<Double> data = Arrays.asList(
                0.2, 0.3, 0.35, 0.33, 0.31, 0.25, 0.1, -0.1, -0.2, -0.25
        );

        int start = 2;  // index of peak
        int end = 9;    // just past trough

        double decimalBeatTime = findSteepestDrop(data, start, end);
        System.out.printf("Estimated beat time: %.3f (between samples)\n", decimalBeatTime);
    }

    public static double getHeartbeatIndex(List<Double> data, int start, int end) {
        return findSteepestDrop(data, start, end);
    }
}

