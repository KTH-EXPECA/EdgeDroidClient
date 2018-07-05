package se.kth.molguin.tracedemo.network.control.experiment;

import android.support.annotation.NonNull;
import android.util.Log;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import se.kth.molguin.tracedemo.network.ResultInputThread;
import se.kth.molguin.tracedemo.network.VideoOutputThread;
import se.kth.molguin.tracedemo.network.control.ConnectionManager;
import se.kth.molguin.tracedemo.network.gabriel.TokenPool;
import se.kth.molguin.tracedemo.synchronization.NTPClient;

public class Run {
    private static final String LOG_TAG = "ExperimentRun";

    private int current_error_count;

    private final ReadWriteLock state_locks;

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
        RunException(String msg) {
            super(msg);
        }
    }

    public Run(@NonNull Config config, @NonNull NTPClient ntpSyncer, @NonNull ConnectionManager cm)
            throws InterruptedException, ExecutionException, IOException, RunException {
        this.state_locks = new ReentrantReadWriteLock();
        Log.i(LOG_TAG, "Initiating new Experiment Run");
        this.config = config;
        this.ntp = ntpSyncer;
        this.cm = cm;

        this.current_error_count = 0;

        this.execs = Executors.newCachedThreadPool();
        this.stats = new RunStats(this.ntp);

        TokenPool token_pool = new TokenPool();
        this.sockets = new Sockets(this.config);

        this.video_out = new VideoOutputThread(this.config.num_steps, this.config.fps,
                this.config.rewind_seconds, this.config.max_replays, this, this.stats,
                this.cm.getAppContext(), this.sockets.video, token_pool);
        this.result_in = new ResultInputThread(this, this.stats, this.sockets.result, token_pool);
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
        Log.i(LOG_TAG, "Experiment run ends");

        this.cm.changeState(ConnectionManager.CMSTATE.DISCONNECTING);
        Log.i(LOG_TAG, "Disconnecting from CA backend...");
        this.video_out.finish();
        this.result_in.stop();

        if (!this.execs.awaitTermination(100, TimeUnit.MILLISECONDS)) // FIXME: magic number
            this.execs.shutdownNow();

        this.sockets.disconnect();
        Log.i(LOG_TAG, "Disconnected from CA backend");
        this.is_shutdown = true;

        // TODO: notify ControlClient
    }

    public JSONObject getRunStats() throws RunStats.RunStatsException, JSONException {
        return this.stats.toJSON();
    }

    public void stepUpdate(int step) {
        // change steps if needed
        if (step != this.video_out.getCurrentStepIndex()) {
            this.state_locks.writeLock().lock();
            try {
                this.current_error_count = 0; // reset error count
            } finally {
                this.state_locks.writeLock().unlock();
            }

            this.video_out.goToStep(step);
        }
    }

    public void incrementErrorCount() {
        // TODO: Actually do something with the error count
        this.state_locks.writeLock();
        try {
            this.current_error_count++;
            Log.w(LOG_TAG, "Received error from backend, current count: " + this.current_error_count);
        } finally {
            this.state_locks.writeLock().unlock();
        }
    }

    // TODO: method for getting frame previews

    public Status getCurrentRunStatus() {
        return new Status(this.stats.getRollingRTT(),
                this.video_out.getLastSentFrame(),
                this.video_out.getLastPushedFrame());
    }

    public static class Status {
        public final double rtt;
        public final byte[] last_sent_frame;
        public final byte[] last_pushed_frame;

        public Status(double rtt, byte[] last_sent_frame, byte[] last_pushed_frame) {
            this.rtt = rtt;
            this.last_sent_frame = last_sent_frame;
            this.last_pushed_frame = last_pushed_frame;
        }
    }
}
