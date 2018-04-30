package se.kth.molguin.tracedemo.network;

import android.content.Context;
import android.util.Log;

import com.instacart.library.truetime.TrueTimeRx;

import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.Socket;
import java.util.Locale;
import java.util.Timer;
import java.util.TimerTask;

import se.kth.molguin.tracedemo.Constants;
import se.kth.molguin.tracedemo.network.gabriel.ConnectionManager;
import se.kth.molguin.tracedemo.network.gabriel.ProtocolConst;
import se.kth.molguin.tracedemo.network.gabriel.TokenManager;
import se.kth.molguin.tracedemo.task.TaskStep;

import static java.lang.System.exit;

public class VideoOutputThread implements Runnable {

    private static final Object load_lock = new Object();

    private static final Object frame_lock = new Object();
    private static final Object run_lock = new Object();
    private Timer timer;

    private byte[] next_frame;
    private boolean running;
    private int frame_counter;
    private int current_step_idx;
    private int num_steps;
    private TaskStep previous_step;
    private Context app_context;

    private DataOutputStream socket_out;

    public int getCurrentStepIndex() {
        synchronized (run_lock) {
            return current_step_idx;
        }
    }

    private TaskStep current_step;
    private TaskStep next_step;
    //private ContentResolver contentResolver;

    private boolean task_success;

    public VideoOutputThread(Socket socket, int num_steps, Context app_context) throws IOException {
        this.frame_counter = 0;
        this.socket_out = new DataOutputStream(socket.getOutputStream());

        this.current_step_idx = 0;
        this.num_steps = num_steps;
        this.app_context = app_context;

        this.current_step = null;
        this.next_step = null;
        this.previous_step = null;
        this.timer = new Timer();
        this.task_success = false;

        try {
            this.goToStep(this.current_step_idx);
        } catch (VideoOutputThreadException e) {
            e.printStackTrace();
            exit(-1);
        }
    }

    public void goToStep(final int step_idx) throws VideoOutputThreadException {
        Log.i("VideoOutputThread", "Moving to step " + step_idx + " from step " + this.current_step_idx);
        synchronized (run_lock) {
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

                synchronized (load_lock) {
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
                        try {
                            this.current_step = new TaskStep(this.getDataInputStreamForStep(step_idx), this);
                        } catch (FileNotFoundException e) {
                            e.printStackTrace();
                            exit(-1);
                        }

                        if (this.next_step != null) this.next_step.stop();
                        if (this.previous_step != null) this.previous_step.stop();
                        this.next_step = null;
                        this.previous_step = null;

                    }
                }

                this.current_step_idx = step_idx;

            } else if (this.current_step == null) {
                try {
                    this.current_step = new TaskStep(this.getDataInputStreamForStep(this.current_step_idx), this);
                } catch (FileNotFoundException e) {
                    e.printStackTrace();
                    exit(-1);
                }
            }
        }

        this.preLoadSteps();

        if (this.running) {
            //Log.i(LOG_TAG, "Starting new step.");
            this.current_step.start();
        }
    }

    private void preLoadSteps() {
        this.timer.schedule(new TimerTask() {
            @Override
            public void run() {
                synchronized (VideoOutputThread.load_lock) {

                    final int current_step_idx = VideoOutputThread.this.current_step_idx;
                    final int next_step_idx = current_step_idx + 1;
                    final int previous_step_idx = current_step_idx - 1;
                    final int num_steps = VideoOutputThread.this.getNumSteps();

                    TaskStep next_step = VideoOutputThread.this.next_step;
                    TaskStep previous_step = VideoOutputThread.this.previous_step;

                    try {
                        if (next_step != null && next_step.getStepIndex() != next_step_idx) {
                            next_step.stop();
                            next_step = null;
                        }

                        if (next_step == null && next_step_idx < num_steps)
                            VideoOutputThread.this.next_step =
                                    new TaskStep(VideoOutputThread.this.getDataInputStreamForStep(next_step_idx), VideoOutputThread.this);

                        if (previous_step != null && previous_step.getStepIndex() != previous_step_idx) {
                            previous_step.stop();
                            previous_step = null;
                        }

                        if (previous_step == null && previous_step_idx >= 0)
                            VideoOutputThread.this.previous_step =
                                    new TaskStep(VideoOutputThread.this.getDataInputStreamForStep(previous_step_idx), VideoOutputThread.this);


                    } catch (FileNotFoundException e) {
                        e.printStackTrace();
                        exit(-1);
                    }
                }
            }
        }, 0);
    }

    public void finish() {
        synchronized (run_lock) {
            this.running = false;
            if (this.current_step != null)
                this.current_step.stop();
        }

        synchronized (frame_lock) {
            frame_lock.notifyAll();
        }

        TokenManager.getInstance().putToken(); // in case the system is waiting for a token

        if (this.next_step != null) this.next_step.stop();
        if (this.previous_step != null) this.previous_step.stop();

        this.current_step_idx = -1;
        this.current_step = null;
        this.next_step = null;
        this.previous_step = null;
    }

    private DataInputStream getDataInputStreamForStep(int index) throws FileNotFoundException {
        synchronized (load_lock) {
            return new DataInputStream(this.app_context.openFileInput(
                    Constants.STEP_PREFIX + (index + 1) + Constants.STEP_SUFFIX
            ));
        }
    }

    private int getNumSteps() {
        return this.num_steps;
    }

    public void pushFrame(byte[] frame) {
        synchronized (frame_lock) {
            this.next_frame = frame;
            frame_lock.notifyAll();
        }
    }

    @Override
    public void run() {

        synchronized (run_lock) {
            this.running = true;
            this.current_step.start();
        }

        TokenManager tk = TokenManager.getInstance();

        byte[] frame_to_send;
        int frame_id;

        while (true) {
            synchronized (run_lock) {
                if (!this.running) break;
            }

            // first, need to get a token
            try {
                tk.getToken();
            } catch (InterruptedException e) {
                break;
            }

            // now we have a token and can try to send stuff
            synchronized (frame_lock) {
                while (this.next_frame == null) {
                    // re-check that we're actually running
                    // wait can hang for a long while, so we need to do this
                    synchronized (run_lock) {
                        if (!this.running) break;
                    }

                    try {
                        frame_lock.wait();
                    } catch (InterruptedException e) {
                        break;
                    }
                }

                this.frame_counter += 1;
                frame_id = this.frame_counter;
                frame_to_send = this.next_frame;
                this.next_frame = null;
            }

            synchronized (run_lock) {
                if (!this.running || frame_to_send == null) break;
            }

            byte[] header = String.format(Locale.ENGLISH,
                    ProtocolConst.VIDEO_HEADER_FMT,
                    frame_id).getBytes();


            try (// use auxiliary output streams to write everything out at once
                 ByteArrayOutputStream baos = new ByteArrayOutputStream();
                 DataOutputStream daos = new DataOutputStream(baos)
            ) {
                daos.writeInt(header.length);
                daos.write(header);
                daos.writeInt(frame_to_send.length);
                daos.write(frame_to_send);

                byte[] out_data = baos.toByteArray();
                this.socket_out.write(out_data); // send!
                this.socket_out.flush();
                try {
                    ConnectionManager.getInstance()
                            .notifySentFrame(new VideoFrame(frame_id, frame_to_send, TrueTimeRx.now()));
                } catch (ConnectionManager.ConnectionManagerException e) {
                    break;
                }

            } catch (IOException e) {
                e.printStackTrace();
                exit(-1);
            }
        }

        synchronized (run_lock) {
            if (this.running) this.finish();
        }

        try {
            ConnectionManager.getInstance().notifyEndStream(this.task_success);
        } catch (ConnectionManager.ConnectionManagerException ignored) {
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
}
