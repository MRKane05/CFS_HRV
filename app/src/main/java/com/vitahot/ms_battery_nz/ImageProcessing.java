package com.vitahot.ms_battery_nz;

import android.media.Image;
import android.util.Log;

import androidx.annotation.OptIn;
import androidx.camera.core.ExperimentalGetImage;
import androidx.camera.core.ImageProxy;

import java.nio.ByteBuffer;

public class ImageProcessing {

    /**
     * Extracts pure Green channel average from YUV_420_888 ImageProxy.
     * Hemoglobin in blood has an optimal absorption peak in Green light (~530nm),
     * providing superior PPG signal-to-noise ratio compared to Y-luminance.
     */
    public static double processImageForGreenChannel(ImageProxy imageProxy) {
        @OptIn(markerClass = ExperimentalGetImage.class) Image image = imageProxy.getImage();
        if (image == null) {
            return 0.0;
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

            long totalGreen = 0;
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

                    // YUV to Green channel conversion: G = Y - 0.337633*U - 0.698001*V
                    int g = (int) (yVal - 0.337633 * uVal - 0.698001 * vVal);
                    totalGreen += Math.max(0, Math.min(255, g));
                    sampleCount++;
                }
            }

            return sampleCount > 0 ? (double) totalGreen / sampleCount : 0.0;
        } catch (Exception e) {
            Log.e("GREEN_CHANNEL", "Error processing Green channel", e);
            return 0.0;
        } finally {
            imageProxy.close();
        }
    }

    public static double processImageFromYPlane(ImageProxy imageProxy) {
        return processImageForGreenChannel(imageProxy);
    }
}
