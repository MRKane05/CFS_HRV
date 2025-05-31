package com.example.cfs_hrv;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

// Simple Decision Tree Node
class TreeNode {
    int featureIndex = -1;
    double threshold = 0;
    double prediction = 0;
    TreeNode left = null;
    TreeNode right = null;
    boolean isLeaf = false;

    public TreeNode(double prediction) {
        this.prediction = prediction;
        this.isLeaf = true;
    }

    public TreeNode(int featureIndex, double threshold) {
        this.featureIndex = featureIndex;
        this.threshold = threshold;
    }
}

// Decision Tree class
class DecisionTree {
    private TreeNode root;
    private Random random;
    private int maxDepth;
    private int minSamples;

    public DecisionTree(int maxDepth, int minSamples, Random random) {
        this.maxDepth = maxDepth;
        this.minSamples = minSamples;
        this.random = random;
    }

    public void train(List<ForestDataPoint> data) {
        root = buildTree(data, 0);
    }

    private TreeNode buildTree(List<ForestDataPoint> data, int depth) {
        if (data.size() <= minSamples || depth >= maxDepth) {
            return new TreeNode(calculateMean(data));
        }

        // Find best split
        BestSplit bestSplit = findBestSplit(data);
        if (bestSplit == null) {
            return new TreeNode(calculateMean(data));
        }

        TreeNode node = new TreeNode(bestSplit.featureIndex, bestSplit.threshold);

        List<ForestDataPoint> leftData = new ArrayList<>();
        List<ForestDataPoint> rightData = new ArrayList<>();

        for (ForestDataPoint point : data) {
            if (point.getFeatures()[bestSplit.featureIndex] <= bestSplit.threshold) {
                leftData.add(point);
            } else {
                rightData.add(point);
            }
        }

        node.left = buildTree(leftData, depth + 1);
        node.right = buildTree(rightData, depth + 1);

        return node;
    }

    private BestSplit findBestSplit(List<ForestDataPoint> data) {
        BestSplit bestSplit = null;
        double bestScore = Double.MAX_VALUE;

        // Try random subset of features (for Random Forest)
        int[] features = {0, 1, 2}; // indices for sdnn, rmssd, pnn50
        shuffleArray(features);
        int numFeatures = Math.max(1, (int) Math.sqrt(features.length));

        for (int i = 0; i < numFeatures; i++) {
            int featureIndex = features[i];

            // Get unique values for this feature
            Set<Double> uniqueValues = new HashSet<>();
            for (ForestDataPoint point : data) {
                uniqueValues.add(point.getFeatures()[featureIndex]);
            }

            for (double threshold : uniqueValues) {
                double score = calculateSplitScore(data, featureIndex, threshold);
                if (score < bestScore) {
                    bestScore = score;
                    bestSplit = new BestSplit(featureIndex, threshold, score);
                }
            }
        }

        return bestSplit;
    }

    private double calculateSplitScore(List<ForestDataPoint> data, int featureIndex, double threshold) {
        List<Double> leftTargets = new ArrayList<>();
        List<Double> rightTargets = new ArrayList<>();

        for (ForestDataPoint point : data) {
            if (point.getFeatures()[featureIndex] <= threshold) {
                leftTargets.add(point.fatigueLevel);
            } else {
                rightTargets.add(point.fatigueLevel);
            }
        }

        if (leftTargets.isEmpty() || rightTargets.isEmpty()) {
            return Double.MAX_VALUE;
        }

        double leftVariance = calculateVariance(leftTargets);
        double rightVariance = calculateVariance(rightTargets);

        double weightedVariance = (leftTargets.size() * leftVariance + rightTargets.size() * rightVariance) / data.size();
        return weightedVariance;
    }

    private double calculateVariance(List<Double> values) {
        if (values.isEmpty()) return 0;

        double mean = values.stream().mapToDouble(Double::doubleValue).average().orElse(0);
        double variance = values.stream()
                .mapToDouble(val -> Math.pow(val - mean, 2))
                .average().orElse(0);
        return variance;
    }

    private double calculateMean(List<ForestDataPoint> data) {
        return data.stream().mapToDouble(point -> point.fatigueLevel).average().orElse(0);
    }

    public double predict(double[] features) {
        return predictRecursive(root, features);
    }

    private double predictRecursive(TreeNode node, double[] features) {
        if (node.isLeaf) {
            return node.prediction;
        }

        if (features[node.featureIndex] <= node.threshold) {
            return predictRecursive(node.left, features);
        } else {
            return predictRecursive(node.right, features);
        }
    }

    private void shuffleArray(int[] array) {
        for (int i = array.length - 1; i > 0; i--) {
            int index = random.nextInt(i + 1);
            int temp = array[index];
            array[index] = array[i];
            array[i] = temp;
        }
    }

    private static class BestSplit {
        int featureIndex;
        double threshold;
        double score;

        BestSplit(int featureIndex, double threshold, double score) {
            this.featureIndex = featureIndex;
            this.threshold = threshold;
            this.score = score;
        }
    }
}

// Random Forest implementation
public class RandomForest {
    private List<DecisionTree> trees;
    private Random random;
    private int numTrees;
    private int maxDepth;
    private int minSamples;

    public RandomForest(int numTrees, int maxDepth, int minSamples) {
        this.numTrees = numTrees;
        this.maxDepth = maxDepth;
        this.minSamples = minSamples;
        this.random = new Random();
        this.trees = new ArrayList<>();
    }

    public void train(List<ForestDataPoint> trainingData) {
        trees.clear();

        for (int i = 0; i < numTrees; i++) {
            // Bootstrap sampling
            List<ForestDataPoint> bootstrapSample = createBootstrapSample(trainingData);

            DecisionTree tree = new DecisionTree(maxDepth, minSamples, new Random(random.nextLong()));
            tree.train(bootstrapSample);
            trees.add(tree);
        }
    }

    private List<ForestDataPoint> createBootstrapSample(List<ForestDataPoint> data) {
        List<ForestDataPoint> sample = new ArrayList<>();
        for (int i = 0; i < data.size(); i++) {
            int randomIndex = random.nextInt(data.size());
            sample.add(data.get(randomIndex));
        }
        return sample;
    }

    public double predict(double sdnn, double rmssd, double pnn50) {
        double[] features = {sdnn, rmssd, pnn50};
        return predict(features);
    }

    public double predict(double[] features) {
        double sum = 0;
        for (DecisionTree tree : trees) {
            sum += tree.predict(features);
        }
        return sum / trees.size();
    }

    // Calculate feature importance
    public double[] getFeatureImportance() {
        // Simplified feature importance calculation
        // In practice, this would be more sophisticated
        return new double[]{0.33, 0.33, 0.34}; // SDNN, RMSSD, PNN50
    }

    // Method to save model (you'd implement serialization)
    public void saveModel(String filePath) {
        // Implement model serialization here
        // You could use JSON or binary serialization
    }

    // Method to load model
    public void loadModel(String filePath) {
        // Implement model deserialization here
    }
}