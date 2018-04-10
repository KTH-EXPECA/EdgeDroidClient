package se.kth.molguin.tracedemo.task;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.DataInputStream;
import java.io.IOException;
import java.util.Timer;
import java.util.TimerTask;

import se.kth.molguin.tracedemo.Constants;
import se.kth.molguin.tracedemo.network.VideoOutputThread;

import static java.lang.System.exit;

public class TaskStep {

    // TODO: Implement way of marking KEY frames.

    private static final String HEADER_NAME_KEY = "name";
    private static final String HEADER_INDEX_KEY = "index";
    private static final String HEADER_NFRAMES_KEY = "num_frames";
    private static final Object lock = new Object();
    private VideoOutputThread outputThread;
    private Timer pushTimer;
    private TimerTask pushTask;
    private int stepIndex;
    private String name;
    private byte[][] frames;
    private int next_frame_idx;
    private int N_frames;
    private boolean rewinded;
    private int rewind_frame;
    private boolean running;

    public TaskStep(final DataInputStream trace_in, VideoOutputThread outputThread) {
        this.outputThread = outputThread;
        this.stepIndex = -1;
        this.name = null;
        this.N_frames = 0;
        this.frames = null;
        this.running = false;
        this.rewinded = false;
        this.rewind_frame = -1;

        // load file in the background in a one-time thread
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    synchronized (lock) {
                        // read header
                        int header_len = trace_in.readInt();
                        byte[] header_s = new byte[header_len];
                        trace_in.read(header_s);

                        JSONObject header = new JSONObject(new String(header_s, "utf-8"));
                        TaskStep.this.stepIndex = header.getInt(HEADER_INDEX_KEY);
                        TaskStep.this.name = header.getString(HEADER_NAME_KEY);
                        TaskStep.this.N_frames = header.getInt(HEADER_NFRAMES_KEY);

                        TaskStep.this.next_frame_idx = 0;
                        TaskStep.this.frames = new byte[N_frames][];

                        for (int i = 0; i < TaskStep.this.N_frames; i++) {
                            // read all the frames into memory
                            int frame_len = trace_in.readInt();
                            TaskStep.this.frames[i] = new byte[frame_len];
                            trace_in.read(TaskStep.this.frames[i]);
                        }

                        TaskStep.this.running = true;
                        lock.notifyAll();
                    }
                } catch (IOException | JSONException e) {
                    // should never happen
                    e.printStackTrace();
                    exit(-1);
                }
            }
        }).start();

        this.pushTimer = new Timer();
        this.pushTask = new TimerTask() {
            @Override
            public void run() {
                TaskStep.this.pushFrame();
            }
        };
    }

    private void pushFrame() {
        synchronized (lock) {
            this.outputThread.pushFrame(this.frames[next_frame_idx]);
            this.next_frame_idx++;

            if (this.next_frame_idx >= this.rewind_frame)
                this.rewinded = false;

            if (this.next_frame_idx >= this.N_frames) {
                this.rewinded = false;
                this.rewind(Constants.REWIND_SECONDS);
            }
        }
    }

    public void rewind(int seconds) {
        synchronized (lock) {
            if (!this.rewinded) {
                this.rewind_frame = this.next_frame_idx;
                this.next_frame_idx = this.next_frame_idx - (seconds * Constants.FPS);
                if (this.next_frame_idx < 0)
                    this.next_frame_idx = 0;
                this.rewinded = true;
            }
        }
    }

    public void stop() {
        synchronized (lock) {
            this.pushTask.cancel();
        }
    }

    public void start() {
        synchronized (lock) {
            while (!this.running) {
                try {
                    lock.wait();
                } catch (InterruptedException e) {
                    return;
                }
            }
        }

        // schedule to push frames @ 15 FPS (period: 66.6666666 = 67 ms)
        this.pushTimer.scheduleAtFixedRate(this.pushTask, 0, (long) Math.ceil(1000.0 / Constants.FPS));
    }

}
