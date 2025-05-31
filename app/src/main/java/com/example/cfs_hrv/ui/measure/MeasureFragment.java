package com.example.cfs_hrv.ui.measure;

import android.Manifest;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.camera.core.Camera;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.ImageAnalysis;
import androidx.camera.core.ImageProxy;
import androidx.camera.core.Preview;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.camera.view.PreviewView;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;

import com.example.cfs_hrv.ImageProcessing;
import com.example.cfs_hrv.R;
import com.example.cfs_hrv.databinding.FragmentHomeBinding;
import com.github.mikephil.charting.charts.LineChart;
import com.github.mikephil.charting.components.Description;
import com.github.mikephil.charting.components.XAxis;
import com.github.mikephil.charting.components.YAxis;
import com.github.mikephil.charting.data.Entry;
import com.github.mikephil.charting.data.LineData;
import com.github.mikephil.charting.data.LineDataSet;
import com.google.android.material.math.MathUtils;
import com.google.common.util.concurrent.ListenableFuture;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MeasureFragment extends Fragment {

    private FragmentHomeBinding binding;

    private TextView progress_text;
    private PreviewView previewView;
    private Button measureButton;
    private TextView pixelDataView;
    private Camera camera;
    private ExecutorService cameraExecutor;

    //Charting values
    private LineChart redColorChart;

    private List<Entry> redColorEntries = new ArrayList<>();

    //Permissions stuff
    private Handler mainHandler;
    public static final String[] REQUIRED_PERMISSIONS = new String[]{Manifest.permission.CAMERA};
    private static final int REQUEST_CODE_PERMISSIONS = 10;

    //Measure stuff
    private Long start_Time;
    private Long lastProcessedTime;

    boolean isTorchOn = false;

    //Sampling stuff
    private static final long SAMPLE_INTERVAL_MS = (long) 33.33333333; // Process frames every 100ms

    public View onCreateView(@NonNull LayoutInflater inflater,
                             ViewGroup container, Bundle savedInstanceState) {
        MeasureViewModel measureViewModel =
                new ViewModelProvider(this).get(MeasureViewModel.class);

        binding = FragmentHomeBinding.inflate(inflater, container, false);
        View root = binding.getRoot();
        /*
        final TextView textView = binding.textHome;
        measureViewModel.getText().observe(getViewLifecycleOwner(), textView::setText);
         */
        //All of our setup stuff for the measure view
        progress_text = binding.progressText;// .findViewById(R.id.progress_text);
        previewView = binding.previewView; //.findViewById(R.id.preview_view);
        measureButton = binding.measureButton; //.findViewById(R.id.torch_button);
        //pixelDataView = binding.findViewById(R.id.pixel_data_view);

        mainHandler = new Handler(Looper.getMainLooper());

        // Set up the torch toggle button
        //measureButton.setOnClickListener(v -> dataRecordButton());

        cameraExecutor = Executors.newSingleThreadExecutor();
        // Request camera permissions
        if (allPermissionsGranted()) {
            startCamera();
        } else {
            ActivityCompat.requestPermissions(
                    requireActivity(), REQUIRED_PERMISSIONS, REQUEST_CODE_PERMISSIONS);
        }

        redColorChart = binding.redColorChart;//view.findViewById(R.id.red_color_chart);

        //heartRateTextView = findViewById(R.id.heart_rate_text);
        //setupChart();

        return root;
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

    private int dataPointCount;
    private final int MAX_DATA_POINTS = 50; //So we don't chew up memory pointlessly
    private void updateRedColorChart(float avgValue) {
        // Calculate average red value from all sampled pixels
        updateGraph(avgValue);
        // Add new data point
        dataPointCount++;
        redColorEntries.add(new Entry(dataPointCount, avgValue));

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
                ProcessCameraProvider.getInstance(requireActivity());
        start_Time = System.currentTimeMillis();    //When we started our camera
        lastProcessedTime = System.currentTimeMillis();
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
                            double imageYValue = ImageProcessing.processImageFromYPlane(imageProxy);
                            //Need to start passing through all the bits and pieces to do the likes of updating our graph

                            updateRedColorChart((float)imageYValue);
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
                measureButton.setEnabled(camera.getCameraInfo().hasFlashUnit());
                if (camera.getCameraInfo().hasFlashUnit()) {
                    //toggleTorch(); //Turn the torch on to begin with
                    if (camera != null && camera.getCameraInfo().hasFlashUnit()) {
                        isTorchOn = !isTorchOn;
                        camera.getCameraControl().enableTorch(isTorchOn);
                    }
                }
            } catch (ExecutionException | InterruptedException e) {
                Toast.makeText(requireActivity(), "Error starting camera: " + e.getMessage(),
                        Toast.LENGTH_SHORT).show();
            }
        }, ContextCompat.getMainExecutor(requireActivity()));
    }

    //Graphing Values:
    private float frame_stable_max = 0;
    private float frame_stable_min = 255;

    private float FRAME_STABLE_LERP_SPEED = 3f; //How quickly will our frame stable positions adjust?

    private void updateGraph(float currentValue) {
        long currentTime = System.currentTimeMillis();
        boolean isPeak = false;

        //Handle the zooming in of our graph
        frame_stable_max = Math.max(frame_stable_max, currentValue);
        frame_stable_max = MathUtils.lerp(frame_stable_max, currentValue, FRAME_STABLE_LERP_SPEED/100);

        frame_stable_min = Math.min(frame_stable_min, currentValue);
        frame_stable_min = MathUtils.lerp(frame_stable_min, currentValue, FRAME_STABLE_LERP_SPEED/100);

        YAxis leftAxis = redColorChart.getAxisLeft();
        leftAxis.setAxisMinimum(frame_stable_min-2);  //0
        leftAxis.setAxisMaximum(frame_stable_max+2);  //255
        //Zooming function complete :)

    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_CODE_PERMISSIONS) {
            if (allPermissionsGranted()) {
                startCamera();
            } else {
                Toast.makeText(requireContext(), "Permissions not granted by the user.",
                        Toast.LENGTH_SHORT).show();
            }
        }
    }

    private boolean allPermissionsGranted() {
        for (String permission : REQUIRED_PERMISSIONS) {
            if (ContextCompat.checkSelfPermission(requireActivity(), permission) !=
                    PackageManager.PERMISSION_GRANTED) {
                return false;
            }
        }
        return true;
    }

    private void toggleTorch() {
        if (camera != null && camera.getCameraInfo().hasFlashUnit()) {
            isTorchOn = !isTorchOn;
            camera.getCameraControl().enableTorch(isTorchOn);
            measureButton.setText(isTorchOn ? "Turn Off Torch" : "Turn On Torch");
        }
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        if (cameraExecutor != null) {
            cameraExecutor.shutdown();
        }
        binding = null;
    }
}