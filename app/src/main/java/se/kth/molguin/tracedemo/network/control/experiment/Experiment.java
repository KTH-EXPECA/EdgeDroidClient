package se.kth.molguin.tracedemo.network.control.experiment;

import android.support.annotation.NonNull;

import java.net.SocketException;
import java.net.UnknownHostException;

import se.kth.molguin.tracedemo.synchronization.NTPClient;

public class Experiment {

    private static final String LOG_TAG = "Experiment";

    private final Config config;
    private final NTPClient ntp;

    private int run_count;
    private Run current_run;

    public Experiment(@NonNull Config config, @NonNull NTPClient ntp) {
        this.config = config;
        this.ntp = ntp;
        this.run_count = 0;

    }


}
