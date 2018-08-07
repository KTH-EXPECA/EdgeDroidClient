package se.kth.molguin.tracedemo;

import android.app.Application;
import android.arch.lifecycle.AndroidViewModel;
import android.arch.lifecycle.LiveData;
import android.arch.lifecycle.MutableLiveData;

public class AppViewModel extends AndroidViewModel {
    /*
    Interface layer between MainActivity and application logic.
     */

    private final MutableLiveData<byte[]> latest_realtime_frame;
    private final MutableLiveData<byte[]> latest_sent_frame;
    private final MutableLiveData<String> latest_log_msg;
    private final MutableLiveData<AppStateMsg> latest_appstatemsg;

    private StateManager state;

    public AppViewModel(Application app) {
        // initialize everything here

        super(app);
        this.latest_realtime_frame = new MutableLiveData<>();
        this.latest_sent_frame = new MutableLiveData<>();
        this.latest_log_msg = new MutableLiveData<>();
        this.latest_appstatemsg = new MutableLiveData<>();

    }

    public LiveData<byte[]> getLatestRealTimeFrame() {
        return this.latest_realtime_frame;
    }

    public LiveData<byte[]> getLatestSentFrame() {
        return this.latest_sent_frame;
    }

    public LiveData<String> getLatestLogMsg()
    {
        return this.latest_log_msg;
    }

    public LiveData<AppStateMsg> getLatestAppStateMsg() {
        return latest_appstatemsg;
    }
}
