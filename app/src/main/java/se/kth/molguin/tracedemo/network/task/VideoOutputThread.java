package se.kth.molguin.tracedemo.network.task;

import android.support.annotation.NonNull;
import android.util.Log;

import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.Socket;
import java.util.Locale;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.ReentrantLock;

import se.kth.molguin.tracedemo.ModelState;
import se.kth.molguin.tracedemo.network.control.ControlConst;
import se.kth.molguin.tracedemo.network.control.experiment.run.Run;
import se.kth.molguin.tracedemo.network.control.experiment.run.RunStats;
import se.kth.molguin.tracedemo.network.gabriel.ProtocolConst;
import se.kth.molguin.tracedemo.network.gabriel.TokenPool;

import static java.lang.System.exit;

//import com.instacart.library.truetime.TrueTimeRx;

public class VideoOutputThread implements Runnable {

    private final static String LOG_TAG = "VideoOutput";

    private final Timer timer;

    private final ReentrantLock running_lock;
    private final ReentrantLock loading_lock;
    private final SynchronizedBuffer<byte[]> frame_buffer;

    private final AtomicBoolean running_flag;
    private final DataOutputStream socket_out;
    private final int num_steps;
    private final int fps;
    private final int rewind_seconds;
    private final int max_replays;

    private final TokenPool tokenPool;
    private final RunStats stats;
    private final ModelState modelState;
    private final Run run;

    // mutable state:
    private int frame_counter;
    private int current_step_idx;

    private TaskStep previous_step;
    private TaskStep current_step;
    private TaskStep next_step;
    //private ContentResolver contentResolver;

    private boolean task_success;

    public VideoOutputThread(int num_steps, int fps, int rewind_seconds, int max_replays,
                             @NonNull Run run, @NonNull RunStats stats, @NonNull ModelState modelState,
                             @NonNull Socket socket, @NonNull TokenPool tokenPool)
            throws IOException {
        this.frame_counter = 0;
        this.socket_out = new DataOutputStream(socket.getOutputStream());

        this.fps = fps;
        this.rewind_seconds = rewind_seconds;
        this.max_replays = max_replays;

        this.current_step_idx = 0;

        this.run = run;
        this.num_steps = num_steps;
        this.modelState = modelState;

        this.current_step = null;
        this.next_step = null;
        this.previous_step = null;
        this.timer = new Timer();
        this.task_success = false;

        this.stats = stats;

        this.frame_buffer = new SynchronizedBuffer<>();

        this.running_lock = new ReentrantLock();
        this.loading_lock = new ReentrantLock();

        this.tokenPool = tokenPool;

        this.running_flag = new AtomicBoolean(false);

        this.goToStep(this.current_step_idx);
    }

    public void goToStep(final int step_idx) {
        Log.i("VideoOutputThread", "Moving to step " + step_idx + " from step " + this.current_step_idx);

        this.running_lock.lock();
        try {
//        synchronized (run_lock) {
            if (step_idx == this.num_steps) {
                // done with the task, finish
                if (this.current_step != null)
                    this.current_step.stop();

                Log.i("VideoOutputThread", "Success!");
                this.task_success = true;
                this.finish();
                return;
            } else if (step_idx < 0 || step_idx > this.num_steps)
                throw new VideoOutputThreadException(EXCEPTIONSTATE.INVALIDSTEPINDEX);


            if (this.current_step_idx != step_idx) {
                if (this.current_step != null)
                    this.current_step.stop();

                this.loading_lock.lock();
                try {
                    if (this.current_step_idx + 1 == step_idx) {
                        //Log.i(LOG_TAG, "New step is next step.");
                        this.current_step = this.next_step;

                        // prepare next and previous
                        this.next_step = null;
                        if (this.previous_step != null) this.previous_step.stop();
                        this.previous_step = null;

                    } else if (this.current_step_idx - 1 == step_idx) {
                        //Log.i(LOG_TAG, "New step is previous step.");

                        this.current_step = this.previous_step;

                        this.previous_step = null;
                        if (this.next_step != null) this.next_step.stop();
                        this.next_step = null;
                    } else {
                        //Log.i(LOG_TAG, "New step is other step.");
                        this.current_step = new TaskStep(
                                this.getDataInputStreamForStep(step_idx),
                                this.frame_buffer, this.fps, this.rewind_seconds, this.max_replays);

                        if (this.next_step != null) this.next_step.stop();
                        if (this.previous_step != null) this.previous_step.stop();
                        this.next_step = null;
                        this.previous_step = null;

                    }
                } finally {
                    this.loading_lock.unlock();
                }

                this.current_step_idx = step_idx;

            } else if (this.current_step == null) {
                this.current_step = new TaskStep(
                        this.getDataInputStreamForStep(this.current_step_idx),
                        this.frame_buffer, this.fps, this.rewind_seconds, this.max_replays);
            }
        } catch (FileNotFoundException | VideoOutputThreadException e) {
            Log.e(LOG_TAG, "Exception!", e);
            exit(-1);
        } finally {
            this.running_lock.unlock();
        }

        this.preLoadSteps();

        if (this.running_flag.get()) {
            //Log.i(LOG_TAG, "Starting new step.");
            this.current_step.start();
        }
    }

    private void preLoadSteps() {
        this.timer.schedule(new TimerTask() {
            @Override
            public void run() {
                VideoOutputThread.this.loading_lock.lock();
                try {

                    final int current_step_idx = VideoOutputThread.this.current_step_idx;
                    final int next_step_idx = current_step_idx + 1;
                    final int previous_step_idx = current_step_idx - 1;
                    final int num_steps = VideoOutputThread.this.getNumSteps();

                    TaskStep next_step = VideoOutputThread.this.next_step;
                    TaskStep previous_step = VideoOutputThread.this.previous_step;

                    if (next_step != null && next_step.getStepIndex() != next_step_idx) {
                        next_step.stop();
                        next_step = null;
                    }

                    if (next_step == null && next_step_idx < num_steps)
                        VideoOutputThread.this.next_step =
                                new TaskStep(VideoOutputThread.this.getDataInputStreamForStep(next_step_idx),
                                        VideoOutputThread.this.frame_buffer,
                                        VideoOutputThread.this.fps,
                                        VideoOutputThread.this.rewind_seconds,
                                        VideoOutputThread.this.max_replays);

                    if (previous_step != null && previous_step.getStepIndex() != previous_step_idx) {
                        previous_step.stop();
                        previous_step = null;
                    }

                    if (previous_step == null && previous_step_idx >= 0)
                        VideoOutputThread.this.previous_step =
                                new TaskStep(VideoOutputThread.this.getDataInputStreamForStep(previous_step_idx),
                                        VideoOutputThread.this.frame_buffer,
                                        VideoOutputThread.this.fps,
                                        VideoOutputThread.this.rewind_seconds,
                                        VideoOutputThread.this.max_replays
                                );

                } catch (FileNotFoundException e) {
                    Log.e(VideoOutputThread.LOG_TAG, "Exception!", e);
                    exit(-1);
                } finally {
                    VideoOutputThread.this.loading_lock.unlock();
                }
            }
        }, 0);
    }

    public void finish() {
        this.running_lock.lock();
        this.running_flag.set(false);
        try {
            if (this.current_step != null)
                this.current_step.stop();
        } finally {
            this.running_lock.unlock();
        }

        this.timer.cancel();

        if (this.next_step != null) this.next_step.stop();
        if (this.previous_step != null) this.previous_step.stop();

        this.current_step_idx = -1;
        this.current_step = null;
        this.next_step = null;
        this.previous_step = null;
    }

    private DataInputStream getDataInputStreamForStep(int index) throws FileNotFoundException {
        this.loading_lock.lock();
        try {
            return new DataInputStream(this.modelState.getAppContext().openFileInput(
                    ControlConst.STEP_PREFIX + (index + 1) + ControlConst.STEP_SUFFIX
            ));
        } finally {
            this.loading_lock.unlock();
        }
    }

    private int getNumSteps() {
        return this.num_steps;
    }


    @Override
    public void run() {
        this.running_flag.set(true);
        this.running_lock.lock();
        try {
            this.current_step.start();
        } finally {
            this.running_lock.unlock();
        }

        byte[] frame_data;

        while (this.running_flag.get()) {
            try {
                // get a token
                this.tokenPool.getToken();
                // got a token
                // now get a frame to send
                frame_data = this.frame_buffer.pop();
                this.frame_counter++;
                this.sendFrame(this.frame_counter, frame_data);

            } catch (InterruptedException e) {
                return;
            }
        }

        if (this.running_flag.get()) this.finish();

        try {
            this.stats.finish(this.task_success);
            this.run.finish();
        } catch (InterruptedException ignored) {
        } catch (IOException e) {
            Log.e(LOG_TAG, "Error when shutting down?");
        } catch (Run.RunException e) {
            Log.e(LOG_TAG, "Tried to shutdown Run twice from VideoOutputThread?");
            exit(-1);
        }
    }

    private void sendFrame(int id, byte[] data) {
        byte[] header = String.format(Locale.ENGLISH, ProtocolConst.VIDEO_HEADER_FMT, id).getBytes();

        try (// use auxiliary output streams to write everything out at once
             ByteArrayOutputStream baos = new ByteArrayOutputStream();
             DataOutputStream daos = new DataOutputStream(baos)
        ) {
            daos.writeInt(header.length);
            daos.write(header);
            daos.writeInt(data.length);
            daos.write(data);

            byte[] out_data = baos.toByteArray();
            this.socket_out.write(out_data); // send!
            this.socket_out.flush();

            this.stats.registerSentFrame(id);
            this.modelState.postSentFrame(data);
        } catch (IOException e) {
            Log.e(LOG_TAG, "IOException while sending data:", e);
            exit(-1);
        }
    }

    private enum EXCEPTIONSTATE {
        NULLCONTENTRESOLVER,
        INVALIDSTEPINDEX
    }

    public static class VideoOutputThreadException extends Exception {
        String msg;

        VideoOutputThreadException(EXCEPTIONSTATE state) {
            super();

            switch (state) {
                case NULLCONTENTRESOLVER:
                    this.msg = "ContentResolver is null!";
                    break;
                case INVALIDSTEPINDEX:
                    this.msg = "Invalid step index!";
                    break;
                default:
                    this.msg = "???";
                    break;
            }
        }

        @Override
        public String getMessage() {
            return super.getMessage() + ": " + this.msg;
        }
    }

    public int getCurrentStepIndex() {
        this.running_lock.lock();
        try {
            return current_step_idx;
        } finally {
            this.running_lock.unlock();
        }

//        synchronized (run_lock) {
//            return current_step_idx;
//        }
    }
}
