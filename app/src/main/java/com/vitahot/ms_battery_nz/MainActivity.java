package com.vitahot.ms_battery_nz;

import androidx.annotation.NonNull;
import androidx.annotation.OptIn;
import androidx.appcompat.app.AppCompatActivity;
import androidx.camera.core.Camera;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.ExperimentalGetImage;
import androidx.camera.core.ImageAnalysis;
import androidx.camera.core.ImageProxy;
import androidx.camera.core.Preview;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.camera.view.PreviewView;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;

import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.ImageFormat;
import android.graphics.Rect;
import android.graphics.YuvImage;
import android.media.Image;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.MenuItem;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;
import android.Manifest;

import com.vitahot.ms_battery_nz.ui.measure.MeasureFragment;
import com.vitahot.ms_battery_nz.ui.results.ResultsFragment;
import com.vitahot.ms_battery_nz.ui.symptoms.SymptomsFragment;
import com.github.mikephil.charting.components.LimitLine;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.android.material.math.MathUtils;
import com.google.android.material.navigation.NavigationBarView;
import com.google.common.util.concurrent.ListenableFuture;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Collections;
import java.util.Locale;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;

//Graph imports
import com.github.mikephil.charting.charts.LineChart;
import com.github.mikephil.charting.components.Description;
import com.github.mikephil.charting.components.XAxis;
import com.github.mikephil.charting.components.YAxis;
import com.github.mikephil.charting.data.Entry;
import com.github.mikephil.charting.data.LineData;
import com.github.mikephil.charting.data.LineDataSet;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executors;


//Data type for peak points
class PeakPoint {

    public float pointEstimate = 0f;
    public int pointIndex = 0;
    public boolean bHasBeenEvaluated = false;
    public int evaluationIndex = 0; //At what point can we evaluate this trigged point?
    public Long timestamp = 0L;
    public Long intervalForward = 0L;
    public Long intervalBackward = 0L;

    public boolean validPoint = true;

    public boolean forwardValid = true;

    public boolean backwardValid = true;


}

class HRVResult {
    public double rmssd;
    public double confidence;

    public HRVResult(double rmssd, double confidence) {
        this.rmssd = rmssd;
        this.confidence = confidence;
    }
}

//public class MainActivity extends AppCompatActivity {
public class MainActivity extends AppCompatActivity implements MeasureFragment.MeasureListener {
    private static final int REQUEST_CODE_PERMISSIONS = 10;
    public static final String[] REQUIRED_PERMISSIONS = new String[]{Manifest.permission.CAMERA};
    private static final String TAG = "CameraTorch";

    //Config for sample window
    public long sample_startTime = 0;
    private long sample_stopTime = 0;

    // Sampling configuration
    private static final float START_SAMPLING_DELAY = 1000; //3 second delay to begin with
    private static final long SAMPLE_INTERVAL_MS = (long) 33.33333333; // Process frames every 100ms
    private static final int SAMPLE_WIDTH = 10; // Sample width for grid
    private static final int SAMPLE_HEIGHT = 10; // Sample height for grid

    //private TextView progress_text;
    public PreviewView previewView;
    private Button torchButton;
    private TextView pixelDataView;
    public Camera camera;
    private ExecutorService cameraExecutor;
    public Handler mainHandler;
    private boolean isTorchOn = false;
    private int frameCount = 0;

    // Store pixel data
    private int[][] pixelGrid = new int[SAMPLE_WIDTH][SAMPLE_HEIGHT];
    private long lastProcessedTime = 0;

    //Graph fields
    public LineChart redColorChart;
    private List<Entry> redColorEntries = new ArrayList<>();
    private static final int MAX_DATA_POINTS = 50; // Maximum number of data points to show
    private int dataPointCount = 0;

    //Heart rate measure fields
    private List<Long> peakTimestamps = new ArrayList<>();

    private List<Double> recordedPoints = new ArrayList<>();

    private List<PeakPoint> allPeakPoints = new ArrayList<>();

    private List<PeakPoint> allTroughPoints = new ArrayList<>();
    private static final int PEAK_DETECTION_WINDOW = 10;
    private static final int MIN_PEAK_DISTANCE_MS = 300; // Minimum 300ms between peaks (max 200 BPM)

    private float[] recentValues = new float[PEAK_DETECTION_WINDOW];
    private int valueIndex = 0;
    private long lastPeakTime = 0;
    public TextView heartRateTextView;
    public ProgressBar progressBar;

    //Hand written function to look for peaks by judging drop-offs in data
    private static float PEAK_MIN_DROPOFF_VALUE = 1.5f; //How much we expect the red value to drop by (at least) after a peak

    private Long lastDropoffPeakTimestamp;
    private List<Long> dropoffPeakTimestamps = new ArrayList<>();
    private float dropoffMaxValue = 1f;

    //Details for detecting troughs
    private boolean bHasHadPeak = false;    //We can only record a trough after a peak
    private static float TROUGH_MIN_GAIN_VALUE = 1.25f; //How much we expect the red value to drop by (at least) after a peak

    private Long lastTroughTimestamp;
    private List<Long> troughsTimestamps = new ArrayList<>();

    private List<PeakPoint> TroughPeakPoints = new ArrayList<>();
    private List<PeakPoint> troughPonts = new ArrayList<>();    //The list for doing our curve analysis approach
    private float troughMinValue = 255f;

    //Handlers for frame stable settings
    private float FRAME_STABLE_DURATION = 1;    //How many seconds do we need frame stable to consider the data good?
    private float frame_max = 0;

    private float frame_stable_max = 0;
    private float frame_min = 255;

    private float frame_stable_min = 255;

    private float FRAME_STABLE_THRESHOLD = 1.5f; //How much can we shift and still be considered to be frame stable?

    private float FRAME_STABLE_LERP_SPEED = 3f; //How quickly will our frame stable positions adjust?

    private float MAX_HEART_PAUSE = 1000; //Maximum time between beats - anything that exceeds this will be dropped from the dataset

    private float MIN_HEART_PAUSE = 500f; //Anything below this value will be thrown out (120bpm)

    public boolean isCameraReady = false;
    BottomNavigationView bottomNavigationView;

    protected Long MEASURE_TIME_DURATION = 120000L; //2 minutes

    public Preview preview;  // Add this as a class variable

    Long start_Time;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        try {
            // Initialize handlers and executors (needed for camera)
            mainHandler = new Handler(Looper.getMainLooper());
            cameraExecutor = Executors.newSingleThreadExecutor();

            // Setup bottom navigation
            bottomNavigationView = findViewById(R.id.bottom_nav);
            bottomNavigationView.setOnItemSelectedListener(new NavigationBarView.OnItemSelectedListener() {
                Fragment selected_fragment = null;

                public boolean onNavigationItemSelected(@NonNull MenuItem menuItem) {
                    int id = menuItem.getItemId();
                    if (id == R.id.navigation_measure) {
                        selected_fragment = new MeasureFragment();
                    } else if (id == R.id.navigation_symptoms) {
                        selected_fragment = new SymptomsFragment();
                    } else if (id == R.id.navigation_results) {
                        selected_fragment = new ResultsFragment();  //This is our info screen with things like the privacy policy
                    }

                    if (selected_fragment != null) {
                        getSupportFragmentManager().beginTransaction()
                                .replace(R.id.fragment_container, selected_fragment)
                                .commit();
                    }
                    return true;
                }
            });

            // Initialize first fragment if this is a fresh start
            if (savedInstanceState == null) {
                getSupportFragmentManager().beginTransaction()
                        .replace(R.id.fragment_container, new MeasureFragment())
                        .commit();
            }

            // Check if opened from notification and if so go to our symptoms page
            handleIntent(getIntent());

            // Start measuring time for sampling window
            start_Time = System.currentTimeMillis();

            // Request camera permissions
            // Request camera permissions
            if (allPermissionsGranted()) {
                // Delay slightly to let activity fully initialize
                mainHandler.postDelayed(() -> startCamera(), 500);
            } else {
                ActivityCompat.requestPermissions(this, REQUIRED_PERMISSIONS, REQUEST_CODE_PERMISSIONS);
            }
        } catch (Exception e) {
            Log.e("MainActivity", "Error in onCreate: " + e.getMessage(), e);
            Toast.makeText(this, "Error initializing app", Toast.LENGTH_SHORT).show();
        }
    }
    public void onDataRecordButtonPressed() {
        dataRecordButton();
    }

    public void setupChart() {
        // Chart styling
        redColorChart.setDrawGridBackground(false);
        redColorChart.setDrawBorders(true);
        redColorChart.setAutoScaleMinMaxEnabled(true);
        redColorChart.setTouchEnabled(false);
        redColorChart.setDragEnabled(false);
        redColorChart.setScaleEnabled(false);
        redColorChart.setPinchZoom(false);

        // Chart description
        Description description = new Description();
        description.setText("Red Color Values Over Time");
        description.setTextSize(12f);
        description.setTextColor(Color.WHITE);
        redColorChart.setDescription(description);

        // X-axis setup
        XAxis xAxis = redColorChart.getXAxis();
        xAxis.setPosition(XAxis.XAxisPosition.BOTTOM);
        xAxis.setDrawGridLines(false);
        xAxis.setLabelCount(5, true);

        // Y-axis setup
        YAxis leftAxis = redColorChart.getAxisLeft();
        leftAxis.setAxisMinimum(180f);  //0
        leftAxis.setAxisMaximum(210f);  //255
        leftAxis.setDrawGridLines(true);

        // Disable right axis
        redColorChart.getAxisRight().setEnabled(false);

        // Initialize empty data
        LineDataSet dataSet = new LineDataSet(redColorEntries, "Red Color Value");
        dataSet.setColor(Color.LTGRAY);
        dataSet.setDrawCircles(false);
        dataSet.setDrawValues(false);
        dataSet.setLineWidth(2f);
        dataSet.setMode(LineDataSet.Mode.CUBIC_BEZIER);

        LineData lineData = new LineData(dataSet);
        redColorChart.setData(lineData);
        redColorChart.invalidate();
    }

    private void updateRedColorChart(float avgRed) {
        // Calculate average red value from all sampled pixels
        float graphValue = 255-avgRed;
        // Add new data point
        dataPointCount++;
        redColorEntries.add(new Entry(dataPointCount, graphValue));

        // Remove old data points if we exceed max
        if (redColorEntries.size() > MAX_DATA_POINTS) {
            redColorEntries.remove(0);
        }
        //Log.d("UpdateChart", "updateRedColorChart called, doingDataSample=" + doingDataSample);

        // Update chart on UI thread
        mainHandler.post(() -> {
            if (doingDataSample && sample_startTime > 0) {
                long elapsedTime = System.currentTimeMillis() - sample_startTime;
                int completePercentage = (int) ((elapsedTime * 100) / MEASURE_TIME_DURATION);
                if (completePercentage > 100) completePercentage = 100;
                //Log.d("ProgressBar", "Is null? " + (progressBar == null) + " | Current progress: " + completePercentage);
                if (progressBar != null) {
                    progressBar.setProgress(completePercentage);
                }
                //our code to call the next page when the line is complete
                if (completePercentage >= 100 && doingDataSample) {
                    // Get the fragment and call dataRecordButton
                    Fragment fragment = getSupportFragmentManager().findFragmentById(R.id.fragment_container);
                    if (fragment instanceof MeasureFragment) {
                        MeasureFragment measureFragment = (MeasureFragment) fragment;
                        measureFragment.saveResultsToDatabase();  // Call the new method
                        measureFragment.dataRecordButton();  // This handles navigation
                    }
                }
            } else {
                progressBar.setProgress(0);
            }

            LineDataSet dataSet;

            if (redColorChart.getData() != null && redColorChart.getData().getDataSetCount() > 0) {
                dataSet = (LineDataSet) redColorChart.getData().getDataSetByIndex(0);
                dataSet.setValues(new ArrayList<>(redColorEntries));
                redColorChart.getData().notifyDataChanged();
                redColorChart.notifyDataSetChanged();
                handleGraphZooming(graphValue);
            }

            redColorChart.invalidate();
        });
    }

    public void stopMeasurement() {
        progressBar.setProgress(0);
        doingDataSample = false;
        sample_startTime = 0;
    }

    private void startCamera() {
        ListenableFuture<ProcessCameraProvider> cameraProviderFuture =
                ProcessCameraProvider.getInstance(this);

        cameraProviderFuture.addListener(() -> {
            try {
                ProcessCameraProvider cameraProvider = cameraProviderFuture.get();

                // Preview
                preview = new Preview.Builder().build();
                if (previewView != null) {
                    preview.setSurfaceProvider(previewView.getSurfaceProvider());
                    // Preview - now a member variable
                    if (previewView != null) {
                        preview.setSurfaceProvider(previewView.getSurfaceProvider());
                    }
                }

                // Image analysis use case
                ImageAnalysis imageAnalysis = new ImageAnalysis.Builder()
                        .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                        .build();

                imageAnalysis.setAnalyzer(cameraExecutor, new ImageAnalysis.Analyzer() {
                    @Override
                    public void analyze(@NonNull ImageProxy imageProxy) {
                        long currentTime = System.currentTimeMillis();
                        long start_delay = currentTime - start_Time;

                        // Only process frames at specified interval to maintain performance
                        if (currentTime - lastProcessedTime >= SAMPLE_INTERVAL_MS && start_delay > 500L) {
                            if (isCameraReady) {
                                processImageFromYPlane(imageProxy);
                            }
                            lastProcessedTime = currentTime;
                        }
                        imageProxy.close(); // Important: must close the imageProxy
                    }
                });

                // Select back camera
                CameraSelector cameraSelector = new CameraSelector.Builder()
                        .requireLensFacing(CameraSelector.LENS_FACING_BACK)
                        .build();

                // Unbind any bound use cases before rebinding
                cameraProvider.unbindAll();

                // Bind use cases to camera
                camera = cameraProvider.bindToLifecycle(
                        this, cameraSelector, preview, imageAnalysis);

                Log.d(TAG, "Camera started successfully");

                // SIGNAL THAT CAMERA IS READY - set this AFTER binding
                isCameraReady = true;

            } catch (ExecutionException | InterruptedException e) {
                Toast.makeText(this, "Error starting camera: " + e.getMessage(),
                        Toast.LENGTH_SHORT).show();
                isCameraReady = false;
            }
        }, ContextCompat.getMainExecutor(this));
    }

    List<Double> pulseTemplate;

    {
        List<Double> doubles = new ArrayList<>();
        doubles.add(81.029929);
        doubles.add(80.854643);
        doubles.add(80.362786);
        doubles.add(79.773357);
        doubles.add(79.377643);
        doubles.add(79.202071);
        doubles.add(79.248357);
        doubles.add(79.399214);
        doubles.add(79.5775);
        doubles.add(79.691714);
        doubles.add(79.760786);
        doubles.add(79.745429);
        pulseTemplate = new ArrayList<>(doubles);
    }

    int recordingStartIndex = 0;
    public boolean doingDataSample = false;

    public void dataRecordButton() {
        //Setup a user controlled sample window for ease of function
        if (!doingDataSample) {
            recordingStartIndex =  dataPointCount;
            doingDataSample = true;
            sample_startTime = System.currentTimeMillis();
            //torchButton.setText("Doing Data Sample");
        } else {
            //torchButton.setText("Doing Data Analysis");
            doingDataSample = false;
            sample_stopTime = System.currentTimeMillis();
            HRVMeasurementSystem.HRVMetrics results =
                    HRVMeasurementSystem.analyzeHRV(dataPointList, 30);

            if (heartRateTextView != null) {
                heartRateTextView.setText(results.toString());
            }
            exportPeakPointsToCSV(this, HRVMeasurementSystem.troughs, "ClaudeHeartPeaks.txt");
            camera.getCameraControl().enableTorch(false);   //Disable our torch
            //peaks
            //Finally we need to display our results
        }
    }

    private void toggleTorch() {
        if (camera != null && camera.getCameraInfo().hasFlashUnit()) {
            isTorchOn = !isTorchOn;
            camera.getCameraControl().enableTorch(isTorchOn);
            //torchButton.setText(isTorchOn ? "Turn Off Torch" : "Turn On Torch");
        }
    }

    public static List<Integer> processPPG(List<Double> rawPPG) {
        List<Double> smoothed = SavitzkyGolayFilter.smooth(rawPPG, 5, 2);
        List<Double> template = MatchedFilter.generateSimplePPGTemplate(15);
        List<Double> matched = MatchedFilter.correlate(smoothed, template);

        double mean = matched.stream().mapToDouble(Double::doubleValue).average().orElse(0);
        double stdDev = Math.sqrt(
                matched.stream().mapToDouble(v -> (v - mean) * (v - mean)).sum() / matched.size()
        );

        List<Integer> detectedPeaks = PeakDetector.detectPeaks(matched, mean + stdDev * 0.5, 30);

        return PeakDetector.detectPeaks(matched, mean + stdDev * 0.5, 30);
    }

    private boolean allPermissionsGranted() {
        for (String permission : REQUIRED_PERMISSIONS) {
            if (ContextCompat.checkSelfPermission(this, permission) !=
                    PackageManager.PERMISSION_GRANTED) {
                return false;
            }
        }
        return true;
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_CODE_PERMISSIONS) {
            if (allPermissionsGranted()) {
                // Delay to let activity resume properly after permission dialog
                mainHandler.postDelayed(() -> startCamera(), 500);
            } else {
                Toast.makeText(this, "Permissions not granted by the user.",
                        Toast.LENGTH_SHORT).show();
                finish();
            }
        }
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        handleIntent(intent);
    }

    private void handleIntent(Intent intent) {
        if (intent != null && "symptoms".equals(intent.getStringExtra("navigate_to"))) {
            if (bottomNavigationView != null) {
                bottomNavigationView.setSelectedItemId(R.id.navigation_symptoms);
            }
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        cameraExecutor.shutdown();
    }

    /**
     * Provides access to the pixel grid for external use
     * This can be used by other components that need to process the pixel data
     */
    public int[][] getPixelGrid() {
        return pixelGrid;
    }

    /**
     * Method to process the pixel data in your desired way
     * This is just a sample implementation
     */
    public void processPixelData() {
        // Example processing logic
        int redSum = 0, greenSum = 0, blueSum = 0;
        int totalPixels = SAMPLE_WIDTH * SAMPLE_HEIGHT;

        for (int x = 0; x < SAMPLE_WIDTH; x++) {
            for (int y = 0; y < SAMPLE_HEIGHT; y++) {
                int pixel = pixelGrid[x][y];
                redSum += Color.red(pixel);
                greenSum += Color.green(pixel);
                blueSum += Color.blue(pixel);
            }
        }

        // Calculate average colors
        final int avgRed = redSum / totalPixels;
        final int avgGreen = greenSum / totalPixels;
        final int avgBlue = blueSum / totalPixels;

        // Use the results in your application
        mainHandler.post(() -> {
            // Update UI or trigger actions based on pixel analysis
            Log.d(TAG, "Average color: RGB(" + avgRed + "," + avgGreen + "," + avgBlue + ")");
            // You could trigger actions based on color values here
        });
    }

    public List<HRVMeasurementSystem.DataPoint> dataPointList = new ArrayList<>();

    private boolean isOptimizingExposure = false;
    private int minExposure = 0, maxExposure = 0;
    private int currentExposureIndex = 0;
    private double bestVariance = -1;
    private int bestExposureIndex = 0;
    private List<Double> exposureSamples = new ArrayList<>();
    private static final int SAMPLES_PER_EXPOSURE = 8; // Faster sweep
    private int exposureSettlingFrames = 0;
    private static final int SETTLING_DELAY_FRAMES = 4; // Faster settling

    public void startExposureOptimization() {
        if (camera == null) return;
        mainHandler.post(() -> {
            try {
                androidx.camera.core.ExposureState state = camera.getCameraInfo().getExposureState();
                if (!state.isExposureCompensationSupported()) {
                    Log.d(TAG, "Exposure compensation not supported");
                    return;
                }

                minExposure = state.getExposureCompensationRange().getLower();
                maxExposure = state.getExposureCompensationRange().getUpper();
                
                // Start from middle-low to avoid starting in saturation
                currentExposureIndex = minExposure;
                bestVariance = -10000; // Large negative for scoring
                bestExposureIndex = 0;
                exposureSamples.clear();
                isOptimizingExposure = true;
                exposureSettlingFrames = SETTLING_DELAY_FRAMES;
                
                camera.getCameraControl().setExposureCompensationIndex(currentExposureIndex);
                Log.d(TAG, "Exposure sweep started: [" + minExposure + " to " + maxExposure + "]");
            } catch (Exception e) {
                Log.e(TAG, "Sweep start error", e);
            }
        });
    }

    public void stopExposureOptimization(boolean lockAtBest) {
        isOptimizingExposure = false;
        if (camera != null && lockAtBest && bestVariance > -1000) {
            camera.getCameraControl().setExposureCompensationIndex(bestExposureIndex);
            Log.d(TAG, "Locked exposure at best level: " + bestExposureIndex);
        } else if (camera != null && !lockAtBest) {
            camera.getCameraControl().setExposureCompensationIndex(0);
        }
    }

    private void runExposureStep(double avgRed, double variance) {
        if (!isOptimizingExposure) return;

        if (exposureSettlingFrames > 0) {
            exposureSettlingFrames--;
            return;
        }

        exposureSamples.add(variance);
        
        if (exposureSamples.size() >= SAMPLES_PER_EXPOSURE) {
            Collections.sort(exposureSamples);
            double medianVariance = exposureSamples.get(exposureSamples.size() / 2);

            // Odinaev et al. (2023) "Sweet Spot": 
            // 1. Target range 175-210 (clipping starts at 235-240 on many sensors).
            // 2. Prioritize signal shape (variance) within that range.
            
            double target = 195.0;
            double distance = Math.abs(avgRed - target);
            
            // Primary goal: Variance. Secondary goal: Proximity to 195.
            double score = medianVariance - (distance * 0.8);
            
            // Hard penalties for non-viable signals
            if (avgRed > 230) score -= 5000; // Saturated - peaks are clipped
            if (avgRed < 60) score -= 5000;  // Too dark - quantization noise dominates

            Log.d(TAG, "Sweep Index " + currentExposureIndex + " | Red: " + String.format("%.0f", avgRed) + " | Var: " + String.format("%.2f", medianVariance) + " | Score: " + String.format("%.2f", score));

            if (score > bestVariance) {
                bestVariance = score;
                bestExposureIndex = currentExposureIndex;
            }

            exposureSamples.clear();
            currentExposureIndex += 2; // Move in steps of 2 for speed (~5s total sweep)

            if (currentExposureIndex > maxExposure) {
                isOptimizingExposure = false;
                camera.getCameraControl().setExposureCompensationIndex(bestExposureIndex);
                mainHandler.post(() -> {
                    Toast.makeText(this, "Optimal HRV Exposure Locked", Toast.LENGTH_SHORT).show();
                    Log.d(TAG, "Sweep finished. Best Level: " + bestExposureIndex);
                });
            } else {
                camera.getCameraControl().setExposureCompensationIndex(currentExposureIndex);
                exposureSettlingFrames = SETTLING_DELAY_FRAMES;
            }
        }
    }

    private double dcG_fallback = 0;

    private float processImageFromYPlane(ImageProxy imageProxy) {

        @OptIn(markerClass = ExperimentalGetImage.class) Image image = imageProxy.getImage();
        if (image == null) {
            return 0;
        }

        try {
            Image.Plane yPlane = image.getPlanes()[0];
            Image.Plane uPlane = image.getPlanes()[1];
            Image.Plane vPlane = image.getPlanes()[2];

            ByteBuffer yBuf = yPlane.getBuffer();
            ByteBuffer uBuf = uPlane.getBuffer();
            ByteBuffer vBuf = vPlane.getBuffer();

            int width = image.getWidth();
            int height = image.getHeight();
            int yRowStride = yPlane.getRowStride();
            int uvRowStride = uPlane.getRowStride();
            int uvPixelStride = uPlane.getPixelStride();

            long totalR = 0, totalG = 0, totalB = 0;
            int sampleCount = 0;

            int step = 20; 
            for (int y = 0; y < height; y += step) {
                for (int x = 0; x < width; x += step) {
                    int yIdx = y * yRowStride + x;
                    int uvIdx = (y / 2) * uvRowStride + (x / 2) * uvPixelStride;

                    if (yIdx >= yBuf.capacity() || uvIdx >= uBuf.capacity() || uvIdx >= vBuf.capacity()) continue;

                    int yVal = yBuf.get(yIdx) & 0xFF;
                    int uVal = (uBuf.get(uvIdx) & 0xFF) - 128;
                    int vVal = (vBuf.get(uvIdx) & 0xFF) - 128;

                    // YUV to RGB (R, G, B are all used for the combined PPG signal)
                    int r = (int) (yVal + 1.370705 * vVal);
                    int g = (int) (yVal - 0.337633 * uVal - 0.698001 * vVal);
                    int b = (int) (yVal + 1.732446 * uVal);

                    totalR += Math.max(0, Math.min(255, r));
                    totalG += Math.max(0, Math.min(255, g));
                    totalB += Math.max(0, Math.min(255, b));
                    sampleCount++;
                }
            }

            if (sampleCount == 0) return 0;

            double avgR = (double) totalR / sampleCount;
            double avgG = (double) totalG / sampleCount;
            double avgB = (double) totalB / sampleCount;

            if (isOptimizingExposure) {
                // Exposure optimization anchored on average brightness (Rec. 601)
                double brightness = (0.299 * avgR) + (0.587 * avgG) + (0.114 * avgB);
                
                exposureSamples.add(avgR); // Still use Red for variance as it's the cleanest
                if (exposureSamples.size() >= SAMPLES_PER_EXPOSURE) {
                    double sum = 0;
                    for (double s : exposureSamples) sum += s;
                    double meanR = sum / exposureSamples.size();
                    double sumSq = 0;
                    for (double s : exposureSamples) sumSq += (s - meanR) * (s - meanR);
                    double varR = sumSq / exposureSamples.size();
                    runExposureStep(brightness, varR);
                    exposureSamples.clear();
                }
            }

            // Signal Combination as per "OpenPPG" research: 
            // Arithmetic average of all three channels (R, G, B).
            // This provides a baseline intensity representing the total reflected brightness.
            float ppgSignal = (float) ((avgR + avgG + avgB) / 3.0);
            
            // Note: The "Inverse" relationship (G increases as R decreases) mentioned in research
            // refers to the AC pulsatile components being in anti-phase. 
            // OpenPPG uses the simple average for the "Main PPG" waveform.

            // Re-center for the graph (0-255 range)
            dcG_fallback = (dcG_fallback == 0) ? ppgSignal : (dcG_fallback * 0.98 + ppgSignal * 0.02);
            float centeredSignal = (float) (ppgSignal - dcG_fallback + 128.0);

            updateRedColorChart(centeredSignal);

            if (doingDataSample) {
                dataPointList.add(new HRVMeasurementSystem.DataPoint((double)centeredSignal, System.currentTimeMillis()));
            }

            return centeredSignal;
        } catch (Exception e) {
            Log.e("SIGNAL_EXTRACTION", "Error extracting PPG signal", e);
        } finally {
            imageProxy.close();
        }
        return 0;
    }


    /**
     * Convert ImageProxy to Bitmap for pixel processing
     */
    private Bitmap imageProxyToBitmap(ImageProxy imageProxy) {
        @OptIn(markerClass = ExperimentalGetImage.class) Image image = imageProxy.getImage();
        if (image == null) return null;

        try {
            Image.Plane[] planes = image.getPlanes();
            ByteBuffer yBuffer = planes[0].getBuffer();
            ByteBuffer uBuffer = planes[1].getBuffer();
            ByteBuffer vBuffer = planes[2].getBuffer();

            int ySize = yBuffer.remaining();
            int uSize = uBuffer.remaining();
            int vSize = vBuffer.remaining();

            byte[] nv21 = new byte[ySize + uSize + vSize];

            // U and V are swapped
            yBuffer.get(nv21, 0, ySize);
            vBuffer.get(nv21, ySize, vSize);
            uBuffer.get(nv21, ySize + vSize, uSize);

            YuvImage yuvImage = new YuvImage(nv21, ImageFormat.NV21,
                    image.getWidth(), image.getHeight(), null);
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            yuvImage.compressToJpeg(
                    new Rect(0, 0, image.getWidth(), image.getHeight()), 100, out);
            byte[] imageBytes = out.toByteArray();

            return Bitmap.createBitmap(
                    android.graphics.BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.length));
        } catch (Exception e) {
            Log.e(TAG, "Error converting image to bitmap", e);
            return null;
        }
    }

    private void addMarkerLine(float peakIndex, int markerColor, String label) {
        LimitLine limit = new LimitLine(peakIndex, label);
        limit.setLineColor(markerColor);
        limit.setLineWidth(2f);
        limit.enableDashedLine(10f, 10f, 0f);
        limit.setLabelPosition(LimitLine.LimitLabelPosition.RIGHT_TOP);

        XAxis bottomAxis = redColorChart.getXAxis();
        bottomAxis.addLimitLine(limit);
    }

    private int lastPeakPoint = 0;

    private void handleGraphZooming(float currentRedValue) {
        //Handle the zooming in of our graph
        frame_stable_max = Math.max(frame_stable_max, currentRedValue);
        frame_stable_max = MathUtils.lerp(frame_stable_max, currentRedValue, FRAME_STABLE_LERP_SPEED/100);

        frame_stable_min = Math.min(frame_stable_min, currentRedValue);
        frame_stable_min = MathUtils.lerp(frame_stable_min, currentRedValue, FRAME_STABLE_LERP_SPEED/100);

        YAxis leftAxis = redColorChart.getAxisLeft();
        leftAxis.setAxisMinimum(frame_stable_min-2);  //0
        leftAxis.setAxisMaximum(frame_stable_max+2);  //255
        //Zooming function complete :)
    }

    private void detectPeaks(float currentRedValue) {
        long currentTime = System.currentTimeMillis();
        boolean isPeak = false;

        //Handle the zooming in of our graph
        frame_stable_max = Math.max(frame_stable_max, currentRedValue);
        frame_stable_max = MathUtils.lerp(frame_stable_max, currentRedValue, FRAME_STABLE_LERP_SPEED/100);

        frame_stable_min = Math.min(frame_stable_min, currentRedValue);
        frame_stable_min = MathUtils.lerp(frame_stable_min, currentRedValue, FRAME_STABLE_LERP_SPEED/100);

        YAxis leftAxis = redColorChart.getAxisLeft();
        leftAxis.setAxisMinimum(frame_stable_min-2);  //0
        leftAxis.setAxisMaximum(frame_stable_max+2);  //255
        //Zooming function complete :)

        if (currentRedValue > dropoffMaxValue) {
            dropoffMaxValue = currentRedValue;
            lastPeakPoint = dataPointCount; //Keep a ticker on where our peak should be
            lastDropoffPeakTimestamp = System.currentTimeMillis();
        } else {
            if (dropoffMaxValue - currentRedValue > PEAK_MIN_DROPOFF_VALUE && !bHasHadPeak) {   //we've got a dropoff happening
                isPeak = true; //log our position
                bHasHadPeak = true;
            }
        }

        // If it's a peak and enough time has passed since last peak
        if (isPeak && (currentTime - lastDropoffPeakTimestamp) > MIN_PEAK_DISTANCE_MS) {
            dropoffMaxValue = 0;

            peakTimestamps.add(currentTime);

            PeakPoint newPeakPoint = new PeakPoint();
            newPeakPoint.timestamp = currentTime;
            if (doingDataSample) {
                allPeakPoints.add(newPeakPoint);
            }

            lastDropoffPeakTimestamp = currentTime;
            addMarkerLine(lastPeakPoint, Color.YELLOW, "Peak");
            // Keep only recent peaks (last 10)
            if (peakTimestamps.size() > 10) {
                peakTimestamps.remove(0);
            }

            // Calculate heart rate if we have at least 2 peaks
            if (peakTimestamps.size() >= 2) {
                //calculateHeartRate();
            }
        }
    }

    private void displayCalculatedTroughIntervalVariance() {
        int discardedPoints = 0;
        int validPoints = 0;
        Long averageValue = 0L;
        for (PeakPoint p : allTroughPoints) {
            if (p.validPoint) {
                averageValue += p.intervalForward;
                validPoints ++;
            } else {
                discardedPoints ++;
            }
        }

        averageValue /= Long.valueOf(validPoints);

        HRVResult HRV = calculateHRV();
        Long finalAverageValue = averageValue;
        int finalDiscardedPoints = discardedPoints;
        int finalValidPoints = validPoints;
        mainHandler.post(() -> {
            //heartRateTextView.setText(String.format("Calculated interval: %d BPM", finalAverageValue));
            heartRateTextView.setText(String.format(
                    "Calculated interval: %d ms  discarded points: %d  valid points: %d Calculated HRV RMSSD: %.2f  confidence: %.1f%%",
                    finalAverageValue, finalDiscardedPoints, finalValidPoints, HRV.rmssd, HRV.confidence
            ));
        });
    }
    private HRVResult calculateHRV() {
        List<Long> intervals = new ArrayList<>();

        int validCount = 0;
        for (PeakPoint point : allTroughPoints) {
            //if (point.validPoint && point.intervalForward > 0) {
            if (true) {
                intervals.add(point.intervalForward); // in milliseconds
                validCount++;
            }
        }

        int totalCount = allTroughPoints.size();
        if (intervals.size() < 2) {
            return new HRVResult(0, 0); // Not enough data
        }

        // Calculate the root mean square of successive differences (RMSSD)
        double sumSqDiff = 0;
        for (int i = 0; i < intervals.size() - 1; i++) {
            double diff = intervals.get(i) - intervals.get(i + 1); // in ms
            sumSqDiff += diff * diff;
        }

        double rmssd = Math.sqrt(sumSqDiff / (intervals.size() - 1)); // result in ms

        // Confidence is percentage of valid points out of all points
        double confidence = (totalCount > 0) ? (validCount * 100.0 / totalCount) : 0;

        return new HRVResult(rmssd, confidence);
    }

    private void prepareHRData() {
        //Populate our data for forward and backward
        for (int i=1; i<allPeakPoints.size()-1; i++) {
            if (allPeakPoints.get(i).intervalForward == 0L) {
                allPeakPoints.get(i).intervalForward = allPeakPoints.get(i+1).timestamp - allPeakPoints.get(i).timestamp;
            }
            if (allPeakPoints.get(i).intervalBackward == 0L) {
                allPeakPoints.get(i).intervalBackward = allPeakPoints.get(i).timestamp - allPeakPoints.get(i-1).timestamp;
            }
        }

        //Go through and sort out our troughs
        for (int i=1; i<allTroughPoints.size()-1; i++) {
            if (allTroughPoints.get(i).intervalForward == 0L) {
                allTroughPoints.get(i).intervalForward = allTroughPoints.get(i+1).timestamp - allTroughPoints.get(i).timestamp;
            }
            if (allTroughPoints.get(i).intervalBackward == 0L) {
                allTroughPoints.get(i).intervalBackward = allTroughPoints.get(i).timestamp - allTroughPoints.get(i-1).timestamp;
            }
        }
    }

    private void filterOutliersFromTroughPoints() {
        if (allTroughPoints == null || allTroughPoints.size() < 3) return; // Need enough data to analyze

        List<Long> intervals = new ArrayList<>();

        // Collect both forward and backward intervals from all points
        for (PeakPoint p : allTroughPoints) {
            intervals.add(p.intervalForward);
            intervals.add(p.intervalBackward);
        }

        // Calculate mean and standard deviation
        double mean = intervals.stream().mapToDouble(Long::doubleValue).average().orElse(0);
        double stddev = Math.sqrt(intervals.stream()
                .mapToDouble(val -> Math.pow(val - mean, 2))
                .average().orElse(0));

        // Define Z-score threshold for outlier detection
        double zThreshold = 2.5;
        //double zThreshold = 1.5; //A much tighter outlier control. We should  have a skewed population anyway

        // Flag invalid points
        //for (PeakPoint p : allPeakPoints) {
        for (int i=0; i<allTroughPoints.size(); i++) {
            PeakPoint p = allTroughPoints.get(i);
            double zBack = stddev > 0 ? Math.abs((p.intervalBackward - mean) / stddev) : 0;
            double zFwd = stddev > 0 ? Math.abs((p.intervalForward - mean) / stddev) : 0;

            //p.validPoint = zBack <= zThreshold && zFwd <= zThreshold;
            if (zBack <= zThreshold && zFwd <= zThreshold && p.validPoint && p.intervalForward > MIN_HEART_PAUSE && p.intervalForward < MAX_HEART_PAUSE) {
                //This is fine, this point is valid and should remain unchanged
            } else {
                p.validPoint = false;
                //Invalidate point behind and point forward (if avaliable)
                if (i > 0) {
                    allTroughPoints.get((i-1)).forwardValid = false;
                }
                /*
                if (i<allPeakPoints.size()-2) {
                    allPeakPoints.get((i+1)).backwardValid = false;
                }*/
            }

            /*
            if (p.intervalForward > MAX_HEART_PAUSE && p.validPoint && p.intervalForward > MIN_HEART_PAUSE) {    //A threshold to see if this point should be excluded from the dataset also
                p.validPoint = false;
            }*/
        }
    }

    private int lastTroughPoint = 0;
    private void detectTroughs(float currentRedValue) {
        long currentTime = System.currentTimeMillis();
        boolean isTrough = false;

        if (currentRedValue < troughMinValue) {
            troughMinValue = currentRedValue;
            lastTroughPoint = dataPointCount; //Keep a ticker on where our trough should be
            lastTroughTimestamp = System.currentTimeMillis();
        } else {
            if (currentRedValue - troughMinValue > TROUGH_MIN_GAIN_VALUE && bHasHadPeak) {   //we've got a dropoff happening
                isTrough = true; //log our position
                bHasHadPeak = false;    //Flip our toggle to have the system now look for a peak
            }
        }

        // If it's a peak and enough time has passed since last peak
        if (isTrough && (currentTime - lastTroughTimestamp) > MIN_PEAK_DISTANCE_MS) {
            troughMinValue = 4096;

            //Keep track of our Trough points to assess usefulness
            PeakPoint newTroughPoint = new PeakPoint();
            newTroughPoint.timestamp = currentTime;
            if (doingDataSample) {
                allTroughPoints.add(newTroughPoint);
                //Use this as a measuring tool. It'll need to have time included in it, but for the moment!
                float measureProgress = (float)allTroughPoints.size()/200f; //Attempt to get 200 heartbeats
                int barFill = (int)(measureProgress * 100f);
                if (barFill > 100) { barFill = 100; }
                
                //progress_text.setText("Progress: " + barFill + "%");
            }

            troughsTimestamps.add(currentTime);
            lastTroughTimestamp = currentTime;
            addMarkerLine(lastTroughPoint, Color.BLUE, "Trough");   //this errs towards false positives
            // Keep only recent peaks (last 10)
            if (troughsTimestamps.size() > 10) {
                troughsTimestamps.remove(0);
            }
/*
            //Keep a list of all of our trough points
            PeakPoint newTroughStamp = new PeakPoint();
            newTroughStamp.timestamp = System.currentTimeMillis();
            newTroughStamp.pointIndex = recordedPoints.size();

            troughPonts.add(newTroughStamp);

            if (allTroughPoints.size() %20 == 0) {
                prepareHRData();
                displayCalculatedTroughIntervalVariance();
            }
            */
        }
    }

    private void exportPeakPointsToCSV(Context context, List<Integer> peakPoints, String filename) {
        File exportDir = context.getExternalFilesDir(null); // App-specific external storage
        if (exportDir == null) {
            Log.e("CSV_EXPORT", "External storage not available.");
            return;
        }

        File file = new File(exportDir, filename);

        try (FileWriter writer = new FileWriter(file)) {
            writer.write("recorded,peak\n"); // CSV Header
            for (int i=0; i<recordedPoints.size(); i++) {
                writer.write(String.format(Locale.US, "%f,%d\n",
                        recordedPoints.get(i),
                        peakPoints.contains(i) ? 80: 76));
                        //peakPoints.size() > i ? peakPoints.get(i) : -1));
            }

            Log.i("CSV_EXPORT", "Exported to: " + file.getAbsolutePath());
        } catch (IOException e) {
            Log.e("CSV_EXPORT", "Error writing CSV", e);
        }
    }
}