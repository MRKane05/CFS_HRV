package com.example.cfs_hrv.ui.results;

import android.content.Context;
import android.os.Bundle;
import android.os.Environment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;

import com.example.cfs_hrv.databinding.FragmentNotificationsBinding;

public class ResultsFragment extends Fragment {
    private static final String FILENAME = "hrv_data.json";
    private Button exportDataButton;
    private Button importDataButton;
    private FragmentNotificationsBinding binding;

    private Context context;

    private TextView textView;

    public View onCreateView(@NonNull LayoutInflater inflater,
                             ViewGroup container, Bundle savedInstanceState) {
        ResultsViewModel resultsViewModel =
                new ViewModelProvider(this).get(ResultsViewModel.class);

        this.context = context;
        binding = FragmentNotificationsBinding.inflate(inflater, container, false);
        View root = binding.getRoot();

        exportDataButton = binding.btnCpytodocs;
        importDataButton = binding.btnCpyfromdocs;

        exportDataButton.setOnClickListener(v -> CopyDataToDocuments());
        importDataButton.setOnClickListener(v -> CopyDataFromDocuments());

        textView = binding.textNotifications;
        //resultsViewModel.getText().observe(getViewLifecycleOwner(), textView::setText);




        return root;
    }

    public void CopyDataFromDocuments() {
        new Thread(new Runnable() {
            @Override
            public void run() {
                File fileSource = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), FILENAME);

                if(fileSource.exists()) {
                    File fileTarget = new File(context.getFilesDir(), FILENAME);

                    try {
                        FileInputStream inStream = new FileInputStream(fileSource);
                        FileOutputStream outStream = new FileOutputStream(fileTarget);

                        byte[] buffer = new byte[1024];
                        int length;
                        while ((length = inStream.read(buffer)) > 0) {
                            outStream.write(buffer, 0, length);
                        }

                        inStream.close();
                        outStream.close();

                        // Update UI on main thread (Fragment version)
                        getActivity().runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                textView.setText(FILENAME + " successfully copied from Documents");
                            }
                        });

                    } catch (IOException e) {
                        e.printStackTrace();
                        getActivity().runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                textView.setText("Error copying file: " + e.getMessage());
                            }
                        });
                    }

                } else {
                    // Update UI on main thread (Fragment version)
                    getActivity().runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            textView.setText(FILENAME + " not found in Documents");
                        }
                    });
                }
            }
        }).start();
    }

    public void CopyDataToDocuments() {
        File fileSource = new File(context.getFilesDir(), FILENAME);
        if(fileSource.exists()) {
            File fileTarget = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), FILENAME);

            try {
                // Use streams to copy the file (works on all API levels)
                FileInputStream inStream = new FileInputStream(fileSource);
                FileOutputStream outStream = new FileOutputStream(fileTarget);

                byte[] buffer = new byte[1024];
                int length;
                while ((length = inStream.read(buffer)) > 0) {
                    outStream.write(buffer, 0, length);
                }

                inStream.close();
                outStream.close();

                // Do something after successful copy
                textView.setText(FILENAME + " successfully copied to Documents");
            } catch (IOException e) {
                e.printStackTrace();
                // Handle the error appropriately
            }

        } else {
            // Handle file not found
            textView.setText(FILENAME + " not found in CFS app");
        }
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}