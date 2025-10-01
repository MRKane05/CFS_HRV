package com.example.cfs_hrv;
import android.content.Context;
import android.os.Environment;
import android.util.Log;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class HRVDataManager {
    private static final String TAG = "HRVDataManager";
    private static final String FILENAME = "hrv_data.json";

    private Context context;
    private List<HRVData> allData;
    private Gson gson;

    public HRVDataManager(Context context) {
        this.context = context;
        this.allData = new ArrayList<>();
        this.gson = new GsonBuilder()
                .setPrettyPrinting()
                .create();
        loadAllData();
    }

    /**
     * Add or update HRV data for today (one entry per day)
     */
    public void setTodaysHRVData(HRVData data) {
        String today = getCurrentDateString();

        // Remove existing entry for today if it exists
        allData.removeIf(existingData -> today.equals(existingData.getDate()));

        // Add the new data
        allData.add(data);
        saveAllData();
    }

    /**
     * Add or update HRV data for today with all parameters except fatigue level
     */
    public void setTodaysHRVData(double meanRR, double sdnn, double rmssd, double pnn50,
                                 double heartRate, int validBeats) {
        String today = getCurrentDateString();

        // Check if entry for today already exists
        HRVData existingData = getTodaysData();

        if (existingData != null) {
            // Update existing entry, keep current fatigue level
            existingData.setMeanRR(meanRR);
            existingData.setSdnn(sdnn);
            existingData.setRmssd(rmssd);
            existingData.setPnn50(pnn50);
            existingData.setHeartRate(heartRate);
            existingData.setValidBeats(validBeats);
            existingData.setTimestamp(System.currentTimeMillis());
        } else {
            // Create new entry with default fatigue level of 0
            HRVData newData = new HRVData(meanRR, sdnn, rmssd, pnn50, heartRate, validBeats, 0, 0);

            //Grab an estimate of fatigue to kick off with seeing as we're adding a new entry
            int fatigueEstimate = FatigueLevelPredictor.predictFatigueLevel(getAllData(), newData);
            newData.setFatigueLevel(fatigueEstimate);

            allData.add(newData);
        }

        saveAllData();
    }

    /**
     * Set fatigue level for today's entry
     */
    public boolean setTodaysFatigueLevel(int fatigueLevel) {
        HRVData todaysData = getTodaysData();

        if (todaysData == null) {
            // Create a new entry with default values and the specified fatigue level
            HRVData newData = new HRVData(0, 0, 0, 0, 0, 0, fatigueLevel, 0);
            allData.add(newData);
            saveAllData();
            return true;
        }

        todaysData.setFatigueLevel(fatigueLevel);
        saveAllData();
        return true;
    }

    /**
     * Set headache level for today's entry
     */
    public boolean setTodaysHeadacheLevel(int headacheLevel) {
        HRVData todaysData = getTodaysData();

        if (todaysData == null) {
            // Create a new entry with default values and the specified headache level
            HRVData newData = new HRVData(0, 0, 0, 0, 0, 0, 0, headacheLevel);
            allData.add(newData);
            saveAllData();
            return true;
        }

        todaysData.setHeadacheLevel(headacheLevel);
        saveAllData();
        return true;
    }

    /**
     * Get today's HRV data entry (single entry)
     */
    public HRVData getTodaysData() {
        String today = getCurrentDateString();

        for (HRVData data : allData) {
            if (today.equals(data.getDate())) {
                return data;
            }
        }

        return null;
    }

    /**
     * Get the most recent HRV data entry (same as getTodaysData for single entry per day)
     */
    public HRVData getLatestData() {
        return getTodaysData();
    }

    /**
     * Get specific value from today's data entry
     */
    public Double getTodaysValue(String parameter) {
        HRVData todaysData = getTodaysData();
        if (todaysData == null) return null;

        switch (parameter.toLowerCase()) {
            case "meanrr": return todaysData.getMeanRR();
            case "sdnn": return todaysData.getSdnn();
            case "rmssd": return todaysData.getRmssd();
            case "pnn50": return todaysData.getPnn50();
            case "heartrate": return todaysData.getHeartRate();
            case "validbeats": return (double) todaysData.getValidBeats();
            case "fatiguelevel": return (double) todaysData.getFatigueLevel();
            default: return null;
        }
    }

    /**
     * Modify today's data entry
     */
    public boolean modifyTodaysData(String parameter, double value) {
        HRVData todaysData = getTodaysData();
        if (todaysData == null) return false;

        switch (parameter.toLowerCase()) {
            case "meanrr":
                todaysData.setMeanRR(value);
                break;
            case "sdnn":
                todaysData.setSdnn(value);
                break;
            case "rmssd":
                todaysData.setRmssd(value);
                break;
            case "pnn50":
                todaysData.setPnn50(value);
                break;
            case "heartrate":
                todaysData.setHeartRate(value);
                break;
            case "validbeats":
                todaysData.setValidBeats((int) value);
                break;
            case "fatiguelevel":
                todaysData.setFatigueLevel((int) value);
                break;
            default:
                return false;
        }

        saveAllData();
        return true;
    }

    /**
     * Get average values for today (returns today's single entry)
     */
    public HRVData getTodaysAverages() {
        return getTodaysData();
    }

    /**
     * Load data for a specific date (returns single entry)
     */
    public HRVData getDataForDate(String dateString) {
        for (HRVData data : allData) {
            if (dateString.equals(data.getDate())) {
                return data;
            }
        }

        return null;
    }

    /**
     * Get count of entries for today (will be 0 or 1)
     */
    public int getTodaysEntryCount() {
        return getTodaysData() != null ? 1 : 0;
    }

    /**
     * Clear today's data entry
     */
    public void clearTodaysData() {
        String today = getCurrentDateString();
        allData.removeIf(data -> today.equals(data.getDate()));
        saveAllData();
    }

    /**
     * Get list of available data dates
     */
    public List<String> getAvailableDates() {
        List<String> dates = new ArrayList<>();

        for (HRVData data : allData) {
            String date = data.getDate();
            if (!dates.contains(date)) {
                dates.add(date);
            }
        }

        return dates;
    }

    /**
     * Get all data entries
     */
    public List<HRVData> getAllData() {
        return new ArrayList<>(allData);
    }

    /**
     * Get total number of entries
     */
    public int getTotalEntryCount() {
        return allData.size();
    }

    private String getCurrentDateString() {
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());
        return sdf.format(new Date());
    }

    private File getSaveFile() {
        //return new File(context.getFilesDir(), FILENAME);
        //return new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), FILENAME);
        return new File(context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS), FILENAME);
    }

    private void loadAllData() {
        File file = getSaveFile(); //new File(context.getFilesDir(), FILENAME);
        //File file = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS), FILENAME);

        if (!file.exists()) {
            return;
        }

        try (FileReader reader = new FileReader(file)) {
            TypeToken<List<HRVData>> token = new TypeToken<List<HRVData>>() {};
            List<HRVData> data = gson.fromJson(reader, token.getType());
            if (data != null) {
                allData = data;
            }
            Log.d(TAG, "Loaded " + allData.size() + " total entries from " + FILENAME);
        } catch (IOException e) {
            Log.e(TAG, "Error loading data", e);
        }
    }

    private void saveAllData() {
        File file = getSaveFile(); //new File(context.getFilesDir(), FILENAME);

        try (FileWriter writer = new FileWriter(file)) {
            gson.toJson(allData, writer);
            Log.d(TAG, "Saved " + allData.size() + " total entries to " + FILENAME);
        } catch (IOException e) {
            Log.e(TAG, "Error saving data", e);
        }
    }

    public void saveRawDataFile(String thisData) {
        File file = getSaveFile(); //new File(context.getFilesDir(), FILENAME);

        try (FileWriter writer = new FileWriter(file)) {
            //gson.toJson(allData, writer);
            writer.write(thisData);
            Log.d(TAG, "Saved string to " + FILENAME);
        } catch (IOException e) {
            Log.e(TAG, "Error saving data", e);
        }
    }
}