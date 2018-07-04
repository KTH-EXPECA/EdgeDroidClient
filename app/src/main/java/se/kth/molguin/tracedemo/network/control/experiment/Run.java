package se.kth.molguin.tracedemo.network.control.experiment;

import android.support.annotation.NonNull;
import android.util.Log;

import java.io.IOException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

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

    private final VideoOutputThread video_out;
    private final ResultInputThread result_in;

    private boolean is_shutdown;

    public static final class RunException extends Exception {
        public RunException(String msg) {
            super(msg);
        }
    }

    public Run(@NonNull Config config, @NonNull NTPClient ntpClient, @NonNull ConnectionManager cm)
            throws InterruptedException, ExecutionException, IOException, RunException {
        Log.i(LOG_TAG, "Initiating new Experiment Run");
        this.config = config;
        this.ntp = ntpClient;
        this.cm = cm;

        this.current_error_count = 0;

        this.execs = Executors.newCachedThreadPool();
        this.stats = new RunStats(this.ntp);

        TokenPool token_pool = new TokenPool();
        this.sockets = new Sockets(this.config);

        this.video_out = new VideoOutputThread(
                this.sockets.video, this.config.num_steps,
                this.config.fps, this.config.rewind_seconds,
                this.config.max_replays, this.cm,
                this.ntp, token_pool);
        this.result_in = new ResultInputThread(this.sockets.result, this.ntp, token_pool);
        this.is_shutdown = false;

        this.execute(); // immediately start
    }

    private void checkRunning() throws RunException {
        if (this.is_shutdown) throw new RunException("Run has already been shutdown!");
    }

    private void execute() throws RunException {
        this.checkRunning();

        this.stats.init();
        this.cm.changeState(ConnectionManager.CMSTATE.INITEXPERIMENT);

        Log.i(LOG_TAG, "Starting stream...");
        this.execs.execute(this.video_out);
        this.execs.execute(this.result_in);
        this.cm.changeState(ConnectionManager.CMSTATE.STREAMING);
    }

    public void finish() throws InterruptedException, IOException, RunException {
        this.checkRunning();

        this.cm.changeState(ConnectionManager.CMSTATE.DISCONNECTING);
        Log.i(LOG_TAG, "Disconnecting from CA backend...");
        this.video_out.finish();
        this.result_in.stop();

        if (!this.execs.awaitTermination(100, TimeUnit.MILLISECONDS)) // FIXME: magic number
            this.execs.shutdownNow();

        this.sockets.disconnect();
        Log.i(LOG_TAG, "Disconnected from CA backend");
        this.is_shutdown = true;
    }
}
