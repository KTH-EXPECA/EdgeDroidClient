package se.kth.molguin.tracedemo;

import android.app.Application;
import android.arch.lifecycle.AndroidViewModel;
import android.arch.lifecycle.LiveData;
import android.arch.lifecycle.MutableLiveData;
import android.util.Log;

import se.kth.molguin.tracedemo.network.control.ControlClient;

import static java.lang.System.exit;

public class AppViewModel extends AndroidViewModel {
    /*
    Interface layer between MainActivity and application logic.
     */
    private final MutableLiveData<String> latest_log_msg;
    private final MutableLiveData<ShutdownMessage> latest_shutdownmessage;

    private final ControlClient client;

    public AppViewModel(Application app) {
        // initialize everything here

        super(app);
        this.latest_log_msg = new MutableLiveData<>();
        this.latest_shutdownmessage = new MutableLiveData<>();

        this.client = new ControlClient(app.getApplicationContext());
        this.client.init();
    }

    public LiveData<byte[]> getRealTimeFrameFeed() {
        return this.client.getRealTimeFrameFeed();
    }

    public LiveData<byte[]> getSentFrameFeed() {
        return this.client.getSentFrameFeed();
    }

    public LiveData<String> getLatestLogMsg() {
        return this.latest_log_msg;
    }

    public LiveData<ShutdownMessage> getShutdownMessage() {
        return latest_shutdownmessage;
    }

    @Override
    protected void onCleared() {
        try {
            this.client.cancel();
        } catch (Exception e) {
            Log.e("ViewModel", "Exception", e);
            exit(-1);
        }
    }
}
