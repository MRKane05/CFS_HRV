package com.example.cfs_hrv;

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
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;
import android.Manifest;

import com.google.common.util.concurrent.ListenableFuture;

import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.util.Arrays;
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


public class MainActivity extends AppCompatActivity {
    private static final int REQUEST_CODE_PERMISSIONS = 10;
    private static final String[] REQUIRED_PERMISSIONS = new String[]{Manifest.permission.CAMERA};
    private static final String TAG = "CameraTorch";

    // Sampling configuration
    private static final int SAMPLE_INTERVAL_MS = 100; // Process frames every 100ms
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
        setupChart();
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
                        // Only process frames at specified interval to maintain performance
                        if (currentTime - lastProcessedTime >= SAMPLE_INTERVAL_MS) {
                            processImage(imageProxy);
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

            } catch (ExecutionException | InterruptedException e) {
                Toast.makeText(this, "Error starting camera: " + e.getMessage(),
                        Toast.LENGTH_SHORT).show();
            }
        }, ContextCompat.getMainExecutor(this));
    }

    private void toggleTorch() {
        if (camera != null && camera.getCameraInfo().hasFlashUnit()) {
            isTorchOn = !isTorchOn;
            camera.getCameraControl().enableTorch(isTorchOn);
            torchButton.setText(isTorchOn ? "Turn Off Torch" : "Turn On Torch");
        }
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

    /**
     * Process the image frame to extract pixel data
     */
    private void processImage(ImageProxy imageProxy) {
        // Sample frame counter for debugging
        frameCount++;

        @OptIn(markerClass = ExperimentalGetImage.class) Image image = imageProxy.getImage();
        if (image == null) return;

        try {
            // Convert YUV image to bitmap for easier pixel access
            Bitmap bitmap = imageProxyToBitmap(imageProxy);
            if (bitmap == null) return;

            // Get image dimensions
            int width = bitmap.getWidth();
            int height = bitmap.getHeight();

            // Sample pixels in a grid pattern
            for (int x = 0; x < SAMPLE_WIDTH; x++) {
                for (int y = 0; y < SAMPLE_HEIGHT; y++) {
                    // Calculate sample positions
                    int sampleX = width * (x + 1) / (SAMPLE_WIDTH + 1);
                    int sampleY = height * (y + 1) / (SAMPLE_HEIGHT + 1);

                    // Get pixel color at sample position
                    int pixel = bitmap.getPixel(sampleX, sampleY);
                    pixelGrid[x][y] = pixel;
                }
            }

            // Update UI with some sample pixel data
            updatePixelDataDisplay();

            // Here you can do further processing with the pixelGrid data
            // such as analyzing color values, detecting patterns, etc.

            bitmap.recycle(); // Free the bitmap to avoid memory leaks
        } catch (Exception e) {
            Log.e(TAG, "Error processing image", e);
        }
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

    /**
     * Update the UI with pixel data
     */
    private void updatePixelDataDisplay() {
        mainHandler.post(() -> {
            StringBuilder sb = new StringBuilder();
            sb.append("Frame: ").append(frameCount).append("\n");
            //Take an average of our sampled pixels to look through for changes that we can detect
            if (SAMPLE_WIDTH > 0 && SAMPLE_HEIGHT > 0) {
                float pixel_R = 0;
                float pixel_G = 0;
                float pixel_B = 0;

                for (int x=0; x< SAMPLE_WIDTH; x++) {
                    for (int y = 0; y<SAMPLE_HEIGHT; y++) {
                        int pixelCol = pixelGrid[x][y];
                        pixel_R += Color.red(pixelCol);
                        pixel_G += Color.green(pixelCol);
                        pixel_B += Color.blue(pixelCol);
                    }
                }

                float sampleSize = (float)SAMPLE_WIDTH * (float)SAMPLE_HEIGHT;
                pixel_R /= sampleSize;
                pixel_G /= sampleSize;
                pixel_B /= sampleSize;

                sb.append("Average Color: (").append(pixel_R).append(",").append(pixel_G).append(",").append(pixel_B).append(")\n");
                //Our values seem to vary from 198 to 206 and our graph is spread up to 255 (for the moment)
                updateRedColorChart(pixel_R);
                /*
                // Display a sample of pixels (top-left, center, bottom-right)
                // Top-left pixel
                int topLeft = pixelGrid[0][0];
                int r = Color.red(topLeft);
                int g = Color.green(topLeft);
                int b = Color.blue(topLeft);
                sb.append("Top-Left: RGB(").append(r).append(",").append(g).append(",").append(b).append(")\n");

                // Center pixel (if exists)
                if (SAMPLE_WIDTH > 2 && SAMPLE_HEIGHT > 2) {
                    int centerX = SAMPLE_WIDTH / 2;
                    int centerY = SAMPLE_HEIGHT / 2;
                    int center = pixelGrid[centerX][centerY];
                    r = Color.red(center);
                    g = Color.green(center);
                    b = Color.blue(center);
                    sb.append("Center: RGB(").append(r).append(",").append(g).append(",").append(b).append(")\n");
                }

                // Bottom-right pixel
                int bottomRight = pixelGrid[SAMPLE_WIDTH-1][SAMPLE_HEIGHT-1];
                r = Color.red(bottomRight);
                g = Color.green(bottomRight);
                b = Color.blue(bottomRight);
                sb.append("Bottom-Right: RGB(").append(r).append(",").append(g).append(",").append(b).append(")");
                */
            }

            pixelDataView.setText(sb.toString());
        });
    }
}