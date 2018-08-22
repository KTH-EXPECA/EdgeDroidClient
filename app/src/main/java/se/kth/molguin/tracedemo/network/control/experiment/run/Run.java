package se.kth.molguin.tracedemo.network.control.experiment.run;

import android.arch.lifecycle.MutableLiveData;
import android.support.annotation.NonNull;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import se.kth.molguin.tracedemo.IntegratedAsyncLog;
import se.kth.molguin.tracedemo.network.control.experiment.Config;
import se.kth.molguin.tracedemo.network.control.experiment.Sockets;
import se.kth.molguin.tracedemo.network.gabriel.TokenPool;
import se.kth.molguin.tracedemo.network.task.ResultInputThread;
import se.kth.molguin.tracedemo.network.task.VideoOutputThread;
import se.kth.molguin.tracedemo.synchronization.INTPSync;

public class Run {
    private static final String LOG_TAG = "ExperimentRun";

    private int current_error_count;

    private final ReadWriteLock state_locks;

    private final INTPSync ntp;
    private final Config config;

    private final ExecutorService execs;
    private final RunStats stats;
    private final Sockets sockets;

    private final VideoOutputThread video_out;
    private final ResultInputThread result_in;

    private final IntegratedAsyncLog log;
    private final MutableLiveData<byte[]> rtframe_feed;
    private final MutableLiveData<byte[]> sentframe_feed;

    private boolean is_shutdown;

    public static final class RunException extends Exception {
        RunException(String msg) {
            super(msg);
        }
    }

    public Run(@NonNull final Config config,
               @NonNull final INTPSync ntp,
               @NonNull final IntegratedAsyncLog log,
               @NonNull final MutableLiveData<byte[]> rtframe_feed,
               @NonNull final MutableLiveData<byte[]> sentframe_feed)
            throws InterruptedException, ExecutionException, IOException {

        this.state_locks = new ReentrantReadWriteLock();

        this.log = log;
        this.rtframe_feed = rtframe_feed;
        this.sentframe_feed = sentframe_feed;

        this.log.i(LOG_TAG, "Initiating new Experiment Run");
        this.config = config;
        this.ntp = ntp;

        this.current_error_count = 0;

        this.execs = Executors.newCachedThreadPool();
        this.stats = new RunStats(this.ntp);

        TokenPool token_pool = new TokenPool();
        this.sockets = new Sockets(this.config);

        // FIXME APP CONTEXT
        this.video_out = new VideoOutputThread(this.config.num_steps, this.config.fps,
                this.config.rewind_seconds, this.config.max_replays, this, this.stats,
                this.cm.getAppContext(), this.sockets.video, token_pool);
        this.result_in = new ResultInputThread(this, this.stats, this.sockets.result, token_pool);
        this.is_shutdown = false;
    }

    private void checkRunning() throws RunException {
        if (this.is_shutdown) throw new RunException("Run has already been shutdown!");
    }

    public void execute() throws RunException {
        this.checkRunning();

        this.stats.init();
        this.log.i(LOG_TAG, "Initializing experiment!");
        this.log.i(LOG_TAG, "Starting stream...");
        this.execs.execute(this.video_out);
        this.execs.execute(this.result_in);
        this.log.i(LOG_TAG, "Started streaming...");

        // FIXME - wait for finish
    }

    public void finish() throws InterruptedException, IOException, RunException {
        this.checkRunning();
        this.log.i(LOG_TAG, "Experiment run ends");
        this.log.i(LOG_TAG, "Disconnecting from CA backend...");
        this.video_out.finish();
        this.result_in.stop();

        if (!this.execs.awaitTermination(100, TimeUnit.MILLISECONDS)) // FIXME: magic number
            this.execs.shutdownNow();

        this.sockets.disconnect();
        this.log.i(LOG_TAG, "Disconnected from CA backend");
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
            this.log.w(LOG_TAG, "Received error from backend, current count: " + this.current_error_count);
        } finally {
            this.state_locks.writeLock().unlock();
        }
    }
}
