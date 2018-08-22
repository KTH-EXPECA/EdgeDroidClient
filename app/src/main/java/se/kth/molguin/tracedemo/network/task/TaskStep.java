package se.kth.molguin.tracedemo.network.task;

import android.util.Log;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.DataInputStream;
import java.io.IOException;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.ReentrantLock;

import se.kth.molguin.tracedemo.ApplicationStateUpdHandler;

import static java.lang.System.exit;

public class TaskStep {

    private static final String HEADER_NAME_KEY = "name";
    private static final String HEADER_INDEX_KEY = "index";
    private static final String HEADER_NFRAMES_KEY = "num_frames";
    private static final String HEADER_KEYFRAME_KEY = "key_frame";
    private static final String LOG_TAG = "TaskStep";

    //    private static final Object lock = new Object();
    private final ReentrantLock rlock;
    private final Timer pushTimer;
    private final TimerTask pushTask;
    private final LinkedBlockingQueue<byte[]> replay_buffer;
    private final int max_replay_count;
    private final int fps;
    private final DataInputStream trace_in;
    private final SynchronizedBuffer<byte[]> frame_buffer;
    private final AtomicBoolean running_flag;

    private int stepIndex;
    private String name;
    private int N_frames;
    private int key_frame;
    private int loaded_frames;
    private byte[] next_frame;
    private boolean replay;
    private int current_replay_count;


    public TaskStep(final DataInputStream trace_in, final SynchronizedBuffer<byte[]> frame_buffer,
                    int fps, int rewind_seconds, int max_replays) {

        this.running_flag = new AtomicBoolean(false);
        this.rlock = new ReentrantLock();
        this.frame_buffer = frame_buffer;
        this.loaded_frames = 0;

        this.fps = fps;
        this.trace_in = trace_in;

        int replay_capacity = fps * rewind_seconds;

        this.replay_buffer = new LinkedBlockingQueue<>(replay_capacity);
        this.max_replay_count = replay_capacity * max_replays;
        this.current_replay_count = 0;

        this.replay = false;

        try {
            // read header
            int header_len = trace_in.readInt();
            byte[] header_s = new byte[header_len];
            trace_in.read(header_s);

            JSONObject header = new JSONObject(new String(header_s, "utf-8"));

            this.stepIndex = header.getInt(HEADER_INDEX_KEY) - 1; // steps are 1-indexed in the trace
            this.name = header.getString(HEADER_NAME_KEY);
            this.N_frames = header.getInt(HEADER_NFRAMES_KEY);
            this.key_frame = header.getInt(HEADER_KEYFRAME_KEY);

            //this.log_tag = "Step" + stepIndex;

            // pre-load next frame
            this.preloadNextFrame();

        } catch (IOException | JSONException e) {
            // should never happen
            Log.e(LOG_TAG, "Exception!", e);
            exit(-1);
        }

        this.pushTimer = new Timer();
        this.pushTask = new TimerTask() {
            @Override
            public void run() {
                TaskStep.this.pushFrame();
            }
        };
    }

    private void preloadNextFrame() {
        if (this.replay) {
            this.current_replay_count++;

            if (this.current_replay_count > this.max_replay_count) {
                Log.w(LOG_TAG, "Too many replays! Shutting down...");
                Log.w(LOG_TAG, "Aborting on Step " + this.stepIndex);
                ApplicationStateUpdHandler.errorMsg(this.stepIndex, "Too many replays!");
                this.stop();
            }
            this.next_frame = this.replay_buffer.poll();
        } else {
            try {
                int frame_len = this.trace_in.readInt();
                this.next_frame = new byte[frame_len];

                this.trace_in.read(this.next_frame);
                this.loaded_frames++;

                // replay if we reach end of step
                if (this.loaded_frames >= this.N_frames) {
                    Log.i(LOG_TAG, "Replaying step " + this.stepIndex);
                    this.replay = true;
                }
            } catch (IOException e) {
                Log.e(LOG_TAG, "Exception!", e);
                exit(-1);
            }
        }
    }

    private void pushFrame() {
        // push new frames
        if (this.running_flag.get()) {
            this.rlock.lock();
            try {

                this.frame_buffer.push(this.next_frame);
                ApplicationStateUpdHandler.realTimeFrameMsg(this.next_frame);
                while (!this.replay_buffer.offer(this.next_frame))
                    this.replay_buffer.poll();
                this.preloadNextFrame();
            } finally {
                this.rlock.unlock();
            }
        }
    }


    public void stop() {
        //Log.i(log_tag, "Stopping...");
        this.running_flag.set(false);
        this.pushTask.cancel();
        this.pushTimer.cancel();


        try {
            this.trace_in.close();
        } catch (IOException e) {
            Log.e(LOG_TAG, "Error closing file.", e);
        }
    }

    public void start() {
        // schedule to push frames @ 15 FPS (period: 66.6666666 = 67 ms)
        if (!this.running_flag.getAndSet(true))
            //Log.i(log_tag, "Starting...");
            this.pushTimer.scheduleAtFixedRate(this.pushTask, 0, (long) Math.ceil(1000.0 / this.fps));
    }

    public int getStepIndex() {
        return stepIndex;
    }
}
