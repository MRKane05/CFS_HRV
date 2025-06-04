package com.example.cfs_hrv;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class HRVData {
    private double meanRR;
    private double sdnn;
    private double rmssd;
    private double pnn50;
    private double heartRate;
    private int validBeats;
    private int fatigueLevel;

    private int headacheLevel;
    private long timestamp;
    private String date;
    private String time;

    public HRVData() {
        this.timestamp = System.currentTimeMillis();
        updateDateTimeStrings();
    }

    public HRVData(double meanRR, double sdnn, double rmssd, double pnn50,
                   double heartRate, int validBeats, int fatigueLevel, int headacheLevel) {
        this.meanRR = meanRR;
        this.sdnn = sdnn;
        this.rmssd = rmssd;
        this.pnn50 = pnn50;
        this.heartRate = heartRate;
        this.validBeats = validBeats;
        this.fatigueLevel = fatigueLevel;
        this.headacheLevel = headacheLevel;
        this.timestamp = System.currentTimeMillis();
        updateDateTimeStrings();
    }

    private void updateDateTimeStrings() {
        Date dateObj = new Date(timestamp);
        SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());
        SimpleDateFormat timeFormat = new SimpleDateFormat("HH:mm:ss", Locale.getDefault());
        this.date = dateFormat.format(dateObj);
        this.time = timeFormat.format(dateObj);
    }

    // Getters
    public double getMeanRR() { return meanRR; }
    public double getSdnn() { return sdnn; }
    public double getRmssd() { return rmssd; }
    public double getPnn50() { return pnn50; }
    public double getHeartRate() { return heartRate; }
    public int getValidBeats() { return validBeats; }
    public int getFatigueLevel() { return fatigueLevel; }

    public int getHeadacheLevel() { return headacheLevel; }
    public long getTimestamp() { return timestamp; }
    public String getDate() { return date; }
    public String getTime() { return time; }

    // Setters
    public void setMeanRR(double meanRR) { this.meanRR = meanRR; }
    public void setSdnn(double sdnn) { this.sdnn = sdnn; }
    public void setRmssd(double rmssd) { this.rmssd = rmssd; }
    public void setPnn50(double pnn50) { this.pnn50 = pnn50; }
    public void setHeartRate(double heartRate) { this.heartRate = heartRate; }
    public void setValidBeats(int validBeats) { this.validBeats = validBeats; }
    public void setFatigueLevel(int fatigueLevel) { this.fatigueLevel = fatigueLevel; }

    public void setHeadacheLevel(int headacheLevel) { this.headacheLevel= headacheLevel; }
    public void setTimestamp(long timestamp) {
        this.timestamp = timestamp;
        updateDateTimeStrings();
    }

    public String toJson() {
        Gson gson = new GsonBuilder()
                .setPrettyPrinting()
                .create();
        return gson.toJson(this);
    }

    public static HRVData fromJson(String json) {
        Gson gson = new Gson();
        return gson.fromJson(json, HRVData.class);
    }
}