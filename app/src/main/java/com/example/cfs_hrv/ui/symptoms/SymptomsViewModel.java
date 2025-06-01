package com.example.cfs_hrv.ui.symptoms;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

public class SymptomsViewModel extends ViewModel {

    private final MutableLiveData<String> mText;

    public SymptomsViewModel() {
        mText = new MutableLiveData<>();
       // mText.setValue("This is dashboard fragment");
    }

    public LiveData<String> getText() {
        return mText;
    }
}