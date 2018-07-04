package se.kth.molguin.tracedemo.network.control.experiment;

import android.support.annotation.NonNull;
import android.util.Log;

import java.io.IOException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import se.kth.molguin.tracedemo.network.ResultInputThread;
import se.kth.molguin.tracedemo.network.VideoOutputThread;
import se.kth.molguin.tracedemo.network.control.ConnectionManager;
import se.kth.molguin.tracedemo.network.gabriel.TokenPool;
import se.kth.molguin.tracedemo.synchronization.NTPClient;

public class Run {
    private static final String LOG_TAG = "ExperimentRun";

    private int current_error_count;

    private final NTPClient ntp;
    private final Config config;
    private final ConnectionManager cm;

    private final ExecutorService execs;
    private final RunStats stats;
    private final Sockets sockets;

    private final TokenPool token_pool;

    // private final VideoOutputThread video_out;
    // private final ResultInputThread result_in;

    public Run(@NonNull Config config, @NonNull NTPClient ntpClient, @NonNull ConnectionManager cm)
            throws InterruptedException, ExecutionException, IOException {
        Log.i(LOG_TAG, "Initiating new Experiment Run");
        this.config = config;
        this.ntp = ntpClient;
        this.cm = cm;

        this.current_error_count = 0;

        this.execs = Executors.newCachedThreadPool();
        this.stats = new RunStats(this.ntp);

        this.token_pool = new TokenPool();
        this.sockets = new Sockets(this.config);


        this.execute(); // immediately start
    }

    private void execute() throws IOException, ExecutionException, InterruptedException {
        this.stats.init();
        this.cm.changeState(ConnectionManager.CMSTATE.INITEXPERIMENT);

        Log.i(LOG_TAG, "Starting stream...");


    }
}
