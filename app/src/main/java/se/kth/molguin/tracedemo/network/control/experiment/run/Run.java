package se.kth.molguin.tracedemo.network.control.experiment.run;

import android.arch.lifecycle.MutableLiveData;
import android.content.Context;
import android.support.annotation.NonNull;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import se.kth.molguin.tracedemo.IntegratedAsyncLog;
import se.kth.molguin.tracedemo.network.control.experiment.Config;
import se.kth.molguin.tracedemo.network.control.experiment.Sockets;
import se.kth.molguin.tracedemo.network.gabriel.TokenPool;
import se.kth.molguin.tracedemo.network.task.ResultInputThread;
import se.kth.molguin.tracedemo.network.task.VideoOutputThread;
import se.kth.molguin.tracedemo.synchronization.INTPSync;

public class Run {
    private static final String LOG_TAG = "ExperimentRun";

    private final ExecutorService execs;
    private final RunStats stats;
    private final Sockets sockets;

    private final VideoOutputThread video_out;
    private final ResultInputThread result_in;

    private final IntegratedAsyncLog log;

    public Run(@NonNull final Config config,
               @NonNull final INTPSync ntp,
               @NonNull final Context appContext,
               @NonNull final IntegratedAsyncLog log,
               @NonNull final MutableLiveData<byte[]> rtframe_feed,
               @NonNull final MutableLiveData<byte[]> sentframe_feed)
            throws InterruptedException, ExecutionException, IOException {

        this.log = log;
        this.log.i(LOG_TAG, "Initiating new Experiment Run");
        this.execs = Executors.newCachedThreadPool();
        this.stats = new RunStats(ntp);

        TokenPool token_pool = new TokenPool(this.log);
        this.sockets = new Sockets(config);

        this.video_out = new VideoOutputThread(
                config.num_steps, config.fps, config.rewind_seconds, config.max_replays,
                appContext, this.stats, this.sockets.video, token_pool,
                sentframe_feed, rtframe_feed, log);
        this.result_in = new ResultInputThread(
                this.video_out, this.stats, this.sockets.result, token_pool, this.log);
    }

    public void execute() throws InterruptedException, IOException {

        this.stats.init();
        this.log.i(LOG_TAG, "Initializing experiment!");
        this.execs.execute(this.result_in);

        // run
        this.video_out.run();

        // done!
        // finish up here

        this.result_in.stop();
        this.log.i(LOG_TAG, "Experiment run ends");
        this.log.i(LOG_TAG, "Disconnecting from CA backend...");

        if (!this.execs.awaitTermination(100, TimeUnit.MILLISECONDS)) // FIXME: magic number
            this.execs.shutdownNow();

        this.sockets.disconnect();
        this.log.i(LOG_TAG, "Disconnected from CA backend");
    }

    public JSONObject getRunStats() throws RunStats.RunStatsException, JSONException {
        return this.stats.toJSON();
    }

    public boolean succeeded() {
        return this.stats.succeeded();
    }
}
