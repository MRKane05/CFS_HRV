package com.vitahot.ms_battery_nz.ui.measure;

import android.Manifest;
import android.app.AlertDialog;
import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ProgressBar;
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

import com.vitahot.ms_battery_nz.HRVDataManager;
import com.vitahot.ms_battery_nz.HRVMeasurementSystem;
import com.vitahot.ms_battery_nz.ImageProcessing;
import com.vitahot.ms_battery_nz.MainActivity;
import com.vitahot.ms_battery_nz.MessageDisplayManager;
import com.vitahot.ms_battery_nz.R;
import com.vitahot.ms_battery_nz.ReminderManager;
import com.vitahot.ms_battery_nz.databinding.FragmentHomeBinding;
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
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MeasureFragment extends Fragment {

    private FragmentHomeBinding binding;

    //private TextView progress_text;
    private ProgressBar progressBar;

    private TextView heartRateTextView;
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
    private Long start_Time = 0l;
    private Long lastProcessedTime = 0l;

    public List<HRVMeasurementSystem.DataPoint> dataPointList = new ArrayList<>();

    boolean isTorchOn = false;

    private MessageDisplayManager messageManager;

    //Sampling stuff
    private static final long SAMPLE_INTERVAL_MS = (long) 33.33333333; // Process frames every 100ms

    // Interface for communicating with the host activity
    public interface MeasureListener {
        void onDataRecordButtonPressed();
    }

    private MeasureListener listener;

    @Override
    public void onAttach(@NonNull Context context) {
        super.onAttach(context);
        // Get the listener from the activity
        if (context instanceof MeasureListener) {
            listener = (MeasureListener) context;
        } else {
            throw new RuntimeException(context + " must implement MeasureListener");
        }
    }



    public View onCreateView(@NonNull LayoutInflater inflater,
                             ViewGroup container, Bundle savedInstanceState) {
        binding = FragmentHomeBinding.inflate(inflater, container, false);
        View root = binding.getRoot();

        // Initialize UI elements
        progressBar = binding.progressBar;
        previewView = binding.previewView;
        measureButton = binding.measureButton;
        redColorChart = binding.redColorChart;
        heartRateTextView = binding.heartRateText;

        MainActivity mainActivity = (MainActivity) getActivity();
        if (mainActivity != null) {
            mainActivity.previewView = previewView;
            mainActivity.redColorChart = redColorChart;
            mainActivity.heartRateTextView = heartRateTextView;
            mainActivity.progressBar = progressBar;
            mainHandler = mainActivity.mainHandler;
        }

        /*
        // Setup button click listener
        measureButton.setOnClickListener(v -> {
            if (listener != null) {
                listener.onDataRecordButtonPressed();
            }
        });*/

        measureButton.setOnClickListener(v -> {
            dataRecordButton();  // Call directly, not through listener
        });

        // Initialize message manager
        messageManager = new MessageDisplayManager(heartRateTextView);

        // Show welcome notice if it's the first time
        if (!ReminderManager.hasWelcomeNoticeBeenShown(requireContext())) {
            showWelcomeNotice();
        } else {
            messageManager.startStage(1);
        }

        // Setup chart
        setupChart();

        return root;
    }

    private void showWelcomeNotice() {
        new AlertDialog.Builder(getContext())
                .setTitle("Welcome to MS Battery")
                .setMessage("To begin: start recording your Heart Rate Variability (HRV) data, please press the button below.\n\nIt's best to record this data before you even get out of bed each morning.")
                .setPositiveButton("Got it", (dialog, which) -> {
                    ReminderManager.setWelcomeNoticeShown(requireContext());
                    if (messageManager != null) {
                        messageManager.startStage(1);
                    }
                })
                .setCancelable(false)
                .show();
    }

    @Override
    public void onPause() {
        super.onPause();
        // Just pause the message manager, don't unbind camera
        if (messageManager != null) {
            messageManager.stop();
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        MainActivity mainActivity = (MainActivity) getActivity();
        if (mainActivity != null) {
            camera = mainActivity.camera;
            mainHandler = mainActivity.mainHandler;

            // Pass UI elements
            mainActivity.redColorChart = redColorChart;
            mainActivity.heartRateTextView = heartRateTextView;
            mainActivity.progressBar = progressBar;
            mainActivity.previewView = previewView;

            // Rebind preview to the new surface
            if (previewView != null && mainActivity.preview != null && camera != null) {
                try {
                    // Clear old binding and rebind to new surface
                    mainActivity.preview.setSurfaceProvider(null);
                    mainActivity.preview.setSurfaceProvider(previewView.getSurfaceProvider());
                } catch (Exception e) {
                    android.util.Log.w("MeasureFragment", "Could not rebind preview", e);
                }
            }

            // Reset button state when returning
            sampleButtonState = 0;
            mainActivity.stopMeasurement();
        }
    }

    int sampleButtonState = 0;  //This will change dependin gon what we're doing
    boolean doingDataSample = false;

    Long sample_startTime;

    public void dataRecordButton() {
        if (getActivity() == null) {
            return;
        }
        MainActivity mainActivity = (MainActivity) getActivity();
        switch (sampleButtonState) {
            case 0:
                if (measureButton != null) {
                    getActivity().runOnUiThread(new Runnable() {
                        public void run(){
                            measureButton.setText("Get a Good Signal\nTap to Record");
                        }
                    });
                }
                if (messageManager != null) {
                    messageManager.startStage(2);
                }
                sampleButtonState = 1;
                setTorch(true);
                break;

            case 1:
                setTorch(true);

                if (mainActivity != null) {
                    mainActivity.doingDataSample = true;
                    mainActivity.sample_startTime = System.currentTimeMillis();
                }
                doingDataSample = true;
                sample_startTime = System.currentTimeMillis();
                if (messageManager != null) {
                    messageManager.startStage(3);
                }
                //MainActivity mainActivity = (MainActivity) getActivity();

                mainActivity.dataPointList.clear();
                if (measureButton != null) {
                    getActivity().runOnUiThread(new Runnable() {
                        public void run(){
                            measureButton.setText("Doing Data Sample\nTap to Finish");
                        }
                    });
                }
                sampleButtonState = 2;
                break;

            case 2:
                if (measureButton != null) {
                    getActivity().runOnUiThread(new Runnable() {
                        public void run() {
                            measureButton.setText("Finished Data Recording\nTap to Repeat");
                        }
                    });
                }

                if (mainActivity != null) {
                    mainActivity.doingDataSample = false;
                }
                doingDataSample = false;
                if (messageManager != null) {
                    messageManager.release();
                }

                //MainActivity mainActivity = (MainActivity) getActivity();
                HRVMeasurementSystem.HRVMetrics results =
                        HRVMeasurementSystem.analyzeHRV(mainActivity.dataPointList, 30);
                /*
                HRVDataManager hrvManager = new HRVDataManager(getContext());
                hrvManager.setTodaysHRVData(results.meanRR, results.sdnn, results.rmssd, results.pnn50,
                        results.heartRate, results.validBeats);*/

                saveResultsToDatabase();

                setTorch(false);
                sampleButtonState = 0;

                if (heartRateTextView != null && getActivity() != null) {
                    getActivity().runOnUiThread(new Runnable() {
                        public void run() {
                            if (heartRateTextView != null) {
                                heartRateTextView.setText(results.toString());
                                getActivity().findViewById(R.id.navigation_symptoms).performClick();
                            }
                        }
                    });
                }
                break;
        }
    }

    public  void saveResultsToDatabase() {
        MainActivity mainActivity = (MainActivity) getActivity();
        HRVMeasurementSystem.HRVMetrics results =
                HRVMeasurementSystem.analyzeHRV(mainActivity.dataPointList, 30);

        HRVDataManager hrvManager = new HRVDataManager(getContext());
        hrvManager.setTodaysHRVData(results.meanRR, results.sdnn, results.rmssd, results.pnn50,
                results.heartRate, results.validBeats);

        if (heartRateTextView != null) {
            heartRateTextView.setText(results.toString());
        }
    }

    private void setupChart() {
        redColorChart.setDrawGridBackground(false);
        redColorChart.setDrawBorders(false);
        redColorChart.setAutoScaleMinMaxEnabled(true);
        redColorChart.setTouchEnabled(false);
        redColorChart.setDragEnabled(false);
        redColorChart.setScaleEnabled(true);
        redColorChart.setPinchZoom(true);
        redColorChart.getLegend().setEnabled(false);

        // Chart description
        Description description = new Description();
        description.setText("PPG Waveform");
        description.setTextSize(12f);
        description.setTextColor(Color.WHITE);
        redColorChart.setDescription(description);

        // X-axis setup
        XAxis xAxis = redColorChart.getXAxis();
        xAxis.setPosition(XAxis.XAxisPosition.BOTTOM);
        xAxis.setDrawGridLines(false);
        xAxis.setLabelCount(0);
        //xAxis.setLabelCount(5, false);

        // Y-axis setup
        YAxis leftAxis = redColorChart.getAxisLeft();
        leftAxis.setAxisMinimum(0f);  //0
        leftAxis.setAxisMaximum(255f);  //255
        leftAxis.setDrawGridLines(false);

        // Disable right axis
        redColorChart.getAxisRight().setEnabled(false);
        redColorChart.getAxisLeft().setEnabled(false);
        redColorChart.getXAxis().setEnabled(false);

        redColorChart.invalidate();

        // Make sure to create initial dataset
        LineDataSet dataSet = new LineDataSet(new ArrayList<>(), "");
        dataSet.setColor(Color.LTGRAY);
        dataSet.setDrawCircles(false);
        //dataSet.setCircleRadius(0f);
        dataSet.setDrawCircleHole(false);
        dataSet.setDrawValues(false);
        dataSet.setValueTextSize(0f);
        dataSet.setLineWidth(2f);
        dataSet.setMode(LineDataSet.Mode.LINEAR);


        LineData lineData = new LineData(dataSet);
        lineData.setDrawValues(false);

        redColorChart.setData(lineData);
        redColorChart.invalidate();
    }

    private int dataPointCount;
    private final int MAX_DATA_POINTS = 50; //So we don't chew up memory pointlessly

    protected Long MEASURE_TIME_DURATION = 120000L; //2 minutes

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
        // Disable axis after setting range
        leftAxis.setEnabled(false);
        leftAxis.setDrawGridLines(false);
        leftAxis.setDrawLabels(false);
        leftAxis.setDrawAxisLine(false);
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
            //measureButton.setText(isTorchOn ? "Turn Off Torch" : "Turn On Torch");
        }
    }


    private void setTorch(boolean isOn) {
        MainActivity mainActivity = (MainActivity) getActivity();
        if (mainActivity != null && mainActivity.camera != null &&
                mainActivity.camera.getCameraInfo().hasFlashUnit()) {
            isTorchOn = isOn;
            mainActivity.camera.getCameraControl().enableTorch(isTorchOn);
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