package se.kth.molguin.tracedemo.network.control.experiment;

import android.support.annotation.NonNull;

import se.kth.molguin.tracedemo.synchronization.INTPSync;

public class Experiment {

    private static final String LOG_TAG = "Experiment";

    private final Config config;
    private final INTPSync ntp;

    private int run_count;

    public Experiment(@NonNull Config config, INTPSync ntp) {
        this.config = config;
        this.ntp = ntp;
        this.run_count = 0;
    }


}
