package com.example.cfs_hrv;

import androidx.annotation.NonNull;
import androidx.annotation.OptIn;
import androidx.annotation.RequiresApi;
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

import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.ImageFormat;
import android.graphics.Rect;
import android.graphics.YuvImage;
import android.media.Image;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;
import android.Manifest;

import com.github.mikephil.charting.components.LimitLine;
import com.google.android.material.math.MathUtils;
import com.google.common.util.concurrent.ListenableFuture;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.Locale;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

//Graph imports
import com.github.mikephil.charting.charts.LineChart;
import com.github.mikephil.charting.components.Description;
import com.github.mikephil.charting.components.XAxis;
import com.github.mikephil.charting.components.YAxis;
import com.github.mikephil.charting.data.Entry;
import com.github.mikephil.charting.data.LineData;
import com.github.mikephil.charting.data.LineDataSet;
import com.github.mikephil.charting.formatter.ValueFormatter;

import java.util.ArrayList;
import java.util.List;


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

public class MainActivity extends AppCompatActivity {
    private static final int REQUEST_CODE_PERMISSIONS = 10;
    private static final String[] REQUIRED_PERMISSIONS = new String[]{Manifest.permission.CAMERA};
    private static final String TAG = "CameraTorch";

    //Config for sample window
    private long sample_startTime = 0;
    private long sample_stopTime = 0;

    // Sampling configuration
    private static final float START_SAMPLING_DELAY = 1000; //3 second delay to begin with
    private static final long SAMPLE_INTERVAL_MS = (long) 33.33333333; // Process frames every 100ms
    private static final int SAMPLE_WIDTH = 10; // Sample width for grid
    private static final int SAMPLE_HEIGHT = 10; // Sample height for grid

    private PreviewView previewView;
    private Button torchButton;
    private TextView pixelDataView;
    private Camera camera;
    private ExecutorService cameraExecutor;
    private Handler mainHandler;
    private boolean isTorchOn = false;
    private int frameCount = 0;

    // Store pixel data
    private int[][] pixelGrid = new int[SAMPLE_WIDTH][SAMPLE_HEIGHT];
    private long lastProcessedTime = 0;

    //Graph fields
    private LineChart redColorChart;
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
    private TextView heartRateTextView;

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

    Long start_Time;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        previewView = findViewById(R.id.preview_view);
        torchButton = findViewById(R.id.torch_button);
        pixelDataView = findViewById(R.id.pixel_data_view);

        mainHandler = new Handler(Looper.getMainLooper());

        // Request camera permissions
        if (allPermissionsGranted()) {
            startCamera();
        } else {
            ActivityCompat.requestPermissions(
                    this, REQUIRED_PERMISSIONS, REQUEST_CODE_PERMISSIONS);
        }

        // Set up the torch toggle button
        torchButton.setOnClickListener(v -> toggleTorch());

        cameraExecutor = Executors.newSingleThreadExecutor();

        redColorChart = findViewById(R.id.red_color_chart);

        heartRateTextView = findViewById(R.id.heart_rate_text);
        setupChart();

        start_Time = System.currentTimeMillis();
    }

    private void setupChart() {
        // Chart styling
        redColorChart.setDrawGridBackground(false);
        redColorChart.setDrawBorders(true);
        redColorChart.setAutoScaleMinMaxEnabled(true);
        redColorChart.setTouchEnabled(true);
        redColorChart.setDragEnabled(true);
        redColorChart.setScaleEnabled(true);
        redColorChart.setPinchZoom(true);

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
        dataSet.setColor(Color.RED);
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

        // Add new data point
        dataPointCount++;
        redColorEntries.add(new Entry(dataPointCount, avgRed));

        // Remove old data points if we exceed max
        if (redColorEntries.size() > MAX_DATA_POINTS) {
            redColorEntries.remove(0);
        }

        // Update chart on UI thread
        mainHandler.post(() -> {
            LineDataSet dataSet;

            if (redColorChart.getData() != null &&
                    redColorChart.getData().getDataSetCount() > 0) {
                // Update existing dataset
                dataSet = (LineDataSet) redColorChart.getData().getDataSetByIndex(0);
                dataSet.setValues(redColorEntries);
                redColorChart.getData().notifyDataChanged();
                redColorChart.notifyDataSetChanged();
            } else {
                // Create new dataset
                dataSet = new LineDataSet(redColorEntries, "Red Color Value");
                dataSet.setColor(Color.RED);
                dataSet.setDrawCircles(false);
                dataSet.setDrawValues(false);
                dataSet.setLineWidth(2f);
                dataSet.setMode(LineDataSet.Mode.CUBIC_BEZIER);

                LineData data = new LineData(dataSet);
                redColorChart.setData(data);
            }

            redColorChart.invalidate();
        });
    }

    private void startCamera() {
        ListenableFuture<ProcessCameraProvider> cameraProviderFuture =
                ProcessCameraProvider.getInstance(this);

        cameraProviderFuture.addListener(() -> {
            try {
                // Used to bind the lifecycle of cameras to the lifecycle owner
                ProcessCameraProvider cameraProvider = cameraProviderFuture.get();

                // Preview
                Preview preview = new Preview.Builder().build();
                preview.setSurfaceProvider(previewView.getSurfaceProvider());

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
                        if (currentTime - lastProcessedTime >= SAMPLE_INTERVAL_MS && start_delay > 500L) {// && currentTime > start_Time + START_SAMPLING_DELAY) {
                        //if (start_delay > 500L) {   //Unthrottled data gathering
                            //processImage(imageProxy);
                            processImageFromYPlane(imageProxy);
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

                // Update torch button state based on flashlight availability
                torchButton.setEnabled(camera.getCameraInfo().hasFlashUnit());
                if (camera.getCameraInfo().hasFlashUnit()) {
                    //toggleTorch(); //Turn the torch on to begin with
                    if (camera != null && camera.getCameraInfo().hasFlashUnit()) {
                        isTorchOn = !isTorchOn;
                        camera.getCameraControl().enableTorch(isTorchOn);
                        torchButton.setText(isTorchOn ? "Turn Off Torch" : "Turn On Torch");
                    }
                }

            } catch (ExecutionException | InterruptedException e) {
                Toast.makeText(this, "Error starting camera: " + e.getMessage(),
                        Toast.LENGTH_SHORT).show();
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
    boolean doingDataSample = false;
    private void toggleTorch() {
        /*
        if (camera != null && camera.getCameraInfo().hasFlashUnit()) {
            isTorchOn = !isTorchOn;
            camera.getCameraControl().enableTorch(isTorchOn);
            torchButton.setText(isTorchOn ? "Turn Off Torch" : "Turn On Torch");
        }
        */
        //Setup a user controlled sample window for ease of function
        if (!doingDataSample) {
            recordingStartIndex =  dataPointCount;
            doingDataSample = true;
            sample_startTime = System.currentTimeMillis();
            torchButton.setText("Doing Data Sample");
        } else {
            torchButton.setText("Doing Data Analysis");
            doingDataSample = false;
            sample_stopTime = System.currentTimeMillis();
            HRVMeasurementSystem.HRVMetrics results =
                    HRVMeasurementSystem.analyzeHRV(dataPointList, 30);

            heartRateTextView.setText(results.toString());
            exportPeakPointsToCSV(this, HRVMeasurementSystem.HBPeaks, "ClaudeHeartPeaks.txt");
            //peaks
            //Finally we need to display our results
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
                startCamera();
            } else {
                Toast.makeText(this, "Permissions not granted by the user.",
                        Toast.LENGTH_SHORT).show();
                finish();
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

    private float processImageFromYPlane(ImageProxy imageProxy) {
        @OptIn(markerClass = ExperimentalGetImage.class) Image image = imageProxy.getImage();
        if (image == null) {
            return 0;
        }

        try {
            Image.Plane yPlane = image.getPlanes()[0]; // Y plane is always at index 0

            ByteBuffer buffer = yPlane.getBuffer();
            int width = image.getWidth();
            int height = image.getHeight();
            int rowStride = yPlane.getRowStride();

            // Sum the Y values across a sampled region (for performance)
            long totalY = 0;
            int sampleCount = 0;

            // Use a small grid sampling instead of every pixel to keep performance reasonable
            int stepDivisor = 20;
            int stepX = width / stepDivisor;
            int stepY = height / stepDivisor;

            for (int y = 0; y < height; y += stepY) {
                for (int x = 0; x < width; x += stepX) {
                    int index = y * rowStride + x;
                    if (index < buffer.capacity()) {
                        int luminance = buffer.get(index) & 0xFF; // Convert unsigned byte to int
                        totalY += luminance;
                        sampleCount++;
                    }
                }
            }

            double averageLuminance = sampleCount > 0 ? (double) totalY / sampleCount : 0f;

            updateRedColorChart((float)averageLuminance);

            if (doingDataSample) {
                recordedPoints.add(averageLuminance);

                HRVMeasurementSystem.DataPoint newDataPoint = new HRVMeasurementSystem.DataPoint(averageLuminance, System.currentTimeMillis());
                dataPointList.add(newDataPoint);
            }



            //detectPeaks(pixel_R);
            detectPeaks((float)averageLuminance);
            detectTroughs((float)averageLuminance);

            //Log.d("LUMINANCE", "Average Y value: " + averageLuminance);

            // TODO: Use `averageLuminance` as your signal for heartbeat detection
            return (float)averageLuminance;
        } catch (Exception e) {
            Log.e("LUMINANCE", "Error reading Y plane", e);
        } finally {
            imageProxy.close(); // Very important: must close to avoid memory leak
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
            writer.write("recorded,filtered,peak\n"); // CSV Header
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