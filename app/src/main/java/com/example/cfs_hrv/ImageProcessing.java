package com.example.cfs_hrv;

import android.media.Image;
import android.util.Log;

import androidx.annotation.OptIn;
import androidx.camera.core.ExperimentalGetImage;
import androidx.camera.core.ImageProxy;

import java.nio.ByteBuffer;

public class ImageProcessing {

    public static double processImageFromYPlane(ImageProxy imageProxy) {
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

            return averageLuminance;
        } catch (Exception e) {
            Log.e("LUMINANCE", "Error reading Y plane", e);
        } finally {
            imageProxy.close(); // Very important: must close to avoid memory leak
        }
        return 0;
    }
}
