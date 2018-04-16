package se.kth.molguin.tracedemo.task;

import android.util.Log;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.DataInputStream;
import java.io.IOException;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.LinkedBlockingQueue;

import se.kth.molguin.tracedemo.Constants;
import se.kth.molguin.tracedemo.network.VideoOutputThread;

import static java.lang.System.exit;

public class TaskStep {

    private static final String HEADER_NAME_KEY = "name";
    private static final String HEADER_INDEX_KEY = "index";
    private static final String HEADER_NFRAMES_KEY = "num_frames";
    private static final String HEADER_KEYFRAME_KEY = "key_frame";

    private static final Object lock = new Object();

    private String log_tag;
    private VideoOutputThread outputThread;
    private Timer pushTimer;
    private TimerTask pushTask;
    private int stepIndex;
    private String name;

    private int N_frames;
    private int key_frame;
    private int loaded_frames;

    private LinkedBlockingQueue<byte[]> replay_buffer;
    private byte[] next_frame;
    private boolean replay;
    private boolean running;

    private DataInputStream trace_in;

    public TaskStep(final DataInputStream trace_in, VideoOutputThread outputThread) {
        this.outputThread = outputThread;
        this.loaded_frames = 0;

        this.trace_in = trace_in;

        this.replay_buffer = new LinkedBlockingQueue<>(Constants.FPS * Constants.REWIND_SECONDS);
        this.replay = false;
        this.running = false;

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

            this.log_tag = "STEP" + stepIndex;

            // pre-load next frame
            this.preloadNextFrame();

        } catch (IOException | JSONException e) {
            // should never happen
            e.printStackTrace();
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
        if (this.replay)
            this.next_frame = this.replay_buffer.poll();
        else {
            try {
                int frame_len = this.trace_in.readInt();
                this.next_frame = new byte[frame_len];

                this.trace_in.read(this.next_frame);
                this.loaded_frames++;

                // replay if we reach end of step
                if (this.loaded_frames >= this.N_frames)
                    this.replay = true;
            } catch (IOException e) {
                e.printStackTrace();
                exit(-1);
            }
        }
    }

    private void pushFrame() {
        // push new frames
        synchronized (lock) {
            if (!this.running) {
                return;
            }

            this.outputThread.pushFrame(this.next_frame);
            while (!this.replay_buffer.offer(this.next_frame))
                this.replay_buffer.poll();
            this.preloadNextFrame();
        }
    }


    public void stop() {
        Log.i(log_tag, "Stopping...");
        this.pushTask.cancel();
        this.pushTimer.cancel();

        synchronized (lock) {
            this.running = false;
            try {
                this.trace_in.close();
            } catch (IOException e) {
                e.printStackTrace();
                exit(-1);
            }
        }
    }

    public void start() {
        // schedule to push frames @ 15 FPS (period: 66.6666666 = 67 ms)
        synchronized (lock) {
            Log.i(log_tag, "Starting...");
            this.running = true;
        }
        this.pushTimer.scheduleAtFixedRate(this.pushTask, 0, (long) Math.ceil(1000.0 / Constants.FPS));
    }

    public int getStepIndex() {
        return stepIndex;
    }
}
