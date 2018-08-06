package se.kth.molguin.tracedemo;

import android.app.Application;
import android.arch.lifecycle.AndroidViewModel;
import android.arch.lifecycle.LiveData;
import android.arch.lifecycle.MutableLiveData;

import se.kth.molguin.tracedemo.network.control.ControlClient;

public class AppViewModel extends AndroidViewModel {
    /*
    Interface layer between MainActivity and application logic.
     */

    private MutableLiveData<byte[]> latest_realtime_frame;
    private MutableLiveData<byte[]> latest_sent_frame;

    private ControlClient controlClient;

    public AppViewModel(Application app) {
        // initialize everything here

        super(app);
        this.latest_realtime_frame = new MutableLiveData<>();
        this.latest_sent_frame = new MutableLiveData<>();
    }

    public LiveData<byte[]> getLatestRealTimeFrame() {
        return this.latest_realtime_frame;
    }

    public LiveData<byte[]> getLatestSentFrame() {
        return latest_sent_frame;
    }
}
