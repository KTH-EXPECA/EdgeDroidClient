package se.kth.molguin.tracedemo.network.task;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.DataInputStream;
import java.io.FileInputStream;
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
    private FrameCircularLinkedList frames;
    private int N_frames;

    public TaskStep(final String trace_path, VideoOutputThread outputThread) {
        this.outputThread = outputThread;
        this.stepIndex = -1;
        this.name = null;
        this.N_frames = 0;

        // load file in the background in a one-time thread
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    synchronized (lock) {
                        DataInputStream input = new DataInputStream(new FileInputStream(trace_path));
                        // read header
                        int header_len = input.readInt();
                        byte[] header_s = new byte[header_len];
                        input.read(header_s);

                        JSONObject header = new JSONObject(new String(header_s, "utf-8"));
                        TaskStep.this.stepIndex = header.getInt(HEADER_INDEX_KEY);
                        TaskStep.this.name = header.getString(HEADER_NAME_KEY);
                        TaskStep.this.N_frames = header.getInt(HEADER_NFRAMES_KEY);

                        TaskStep.this.frames = new FrameCircularLinkedList();

                        for (int i = 0; i < TaskStep.this.N_frames; i++) {
                            // read all the frames into memory
                            int frame_len = input.readInt();
                            byte[] frame_data = new byte[frame_len];
                            input.read(frame_data);

                            TaskStep.this.frames.put(frame_data);
                        }

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

    public void start() {
        synchronized (lock) {
            while (this.stepIndex == -1) {
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

    public void stop() {
        synchronized (lock) {
            this.pushTask.cancel();
        }
    }

    public void rewind(int seconds) {
        synchronized (lock) {
            this.frames.rewind(Constants.FPS * seconds);
        }
    }

    private void pushFrame() {
        // TODO: implement pushing current frame to VideoOutputThread
    }

}
