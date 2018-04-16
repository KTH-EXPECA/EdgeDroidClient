package se.kth.molguin.tracedemo.network;

import android.content.ContentResolver;
import android.net.Uri;
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

import se.kth.molguin.tracedemo.network.gabriel.ConnectionManager;
import se.kth.molguin.tracedemo.network.gabriel.ProtocolConst;
import se.kth.molguin.tracedemo.network.gabriel.TokenManager;
import se.kth.molguin.tracedemo.task.TaskStep;

import static java.lang.System.exit;

public class VideoOutputThread implements Runnable {

    private static final Object loadlock = new Object();

    private static final Object framelock = new Object();
    private static final Object runlock = new Object();
    private static String LOG_TAG = "VideoOutputThread";
    Timer timer;

    private byte[] next_frame;
    private boolean running;
    private int frame_counter;
    private int current_step_idx;
    private TaskStep previous_step;

    private DataOutputStream socket_out;

    public int getCurrentStepIndex() {
        synchronized (runlock) {
            return current_step_idx;
        }
    }

    private TaskStep current_step;
    private TaskStep next_step;
    private Uri[] step_files;
    private ContentResolver contentResolver;

    public VideoOutputThread(Socket socket, Uri[] steps) throws IOException {
        this.frame_counter = 0;
        this.socket_out = new DataOutputStream(socket.getOutputStream());

        this.step_files = steps;
        this.current_step_idx = 0;

        this.current_step = null;
        this.next_step = null;
        this.previous_step = null;
        this.timer = new Timer();

        try {
            this.contentResolver = ConnectionManager
                    .getInstance()
                    .getContext()
                    .getContentResolver();

            if (this.contentResolver == null)
                throw new VideoOutputThreadException(EXCEPTIONSTATE.NULLCONTENTRESOLVER);

            this.goToStep(this.current_step_idx);
        } catch (VideoOutputThreadException | ConnectionManager.ConnectionManagerException e) {
            e.printStackTrace();
            exit(-1);
        }
    }

    public void goToStep(final int step_idx) throws VideoOutputThreadException {
        Log.i(LOG_TAG, "Moving to step " + step_idx);
        synchronized (runlock) {
            if (this.current_step != null)
                this.current_step.stop();

            if (step_idx == this.step_files.length) {
                // done with the task, finish
                Log.i(LOG_TAG, "Success.");
                this.finish();
                return;
            } else if (step_idx < 0 || step_idx > this.step_files.length)
                throw new VideoOutputThreadException(EXCEPTIONSTATE.INVALIDSTEPINDEX);


            if (this.current_step_idx != step_idx) {
                synchronized (loadlock) {
                    if (this.current_step_idx + 1 == step_idx) {
                        Log.i(LOG_TAG, "New step is next step.");
                        this.current_step = this.next_step;

                        // prepare next and previous
                        this.next_step = null;
                        if (this.previous_step != null) this.previous_step.stop();
                        this.previous_step = null;

                    } else if (this.current_step_idx - 1 == step_idx) {
                        Log.i(LOG_TAG, "New step is previous step.");

                        this.current_step = this.previous_step;

                        this.previous_step = null;
                        if (this.next_step != null) this.next_step.stop();
                        this.next_step = null;
                    } else {
                        Log.i(LOG_TAG, "New step is other step.");
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

                // load previous and next step:
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
            Log.i(LOG_TAG, "Starting new step.");
            this.current_step.start();
        }
    }

    private void preLoadSteps() {
        this.timer.schedule(new TimerTask() {
            @Override
            public void run() {
                synchronized (VideoOutputThread.loadlock) {

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
        synchronized (runlock) {
            this.running = false;
            if (this.current_step != null)
                this.current_step.stop();
        }

        synchronized (framelock) {
            framelock.notifyAll();
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
        synchronized (loadlock) {
            return new DataInputStream(this.contentResolver.openInputStream(this.step_files[index]));
        }
    }

    public int getNumSteps() {
        return this.step_files.length;
    }

    public void pushFrame(byte[] frame) {
        synchronized (framelock) {
            this.next_frame = frame;
            framelock.notifyAll();
        }
    }

    public boolean isOnLastStep() {
        synchronized (runlock) {
            return this.current_step_idx + 1 >= this.step_files.length;
        }
    }


    @Override
    public void run() {

        synchronized (runlock) {
            this.running = true;
            this.current_step.start();
        }

        TokenManager tk = TokenManager.getInstance();

        byte[] frame_to_send;
        int frame_id;

        while (true) {
            synchronized (runlock) {
                if (!this.running) break;
            }

            // first, need to get a token
            try {
                tk.getToken();
            } catch (InterruptedException e) {
                break;
            }

            // now we have a token and can try to send stuff
            synchronized (framelock) {
                while (this.next_frame == null) {
                    // re-check that we're actually running
                    // wait can hang for a long while, so we need to do this
                    synchronized (runlock) {
                        if (!this.running) break;
                    }

                    try {
                        framelock.wait();
                    } catch (InterruptedException e) {
                        break;
                    }
                }

                this.frame_counter += 1;
                frame_id = this.frame_counter;
                frame_to_send = this.next_frame;
                this.next_frame = null;
            }

            synchronized (runlock) {
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
                ConnectionManager.getInstance()
                        .notifySentFrame(new VideoFrame(frame_id, frame_to_send, System.currentTimeMillis()));

            } catch (IOException e) {
                e.printStackTrace();
                exit(-1);
            }
        }

        synchronized (runlock) {
            if (this.running) this.finish();
        }

        ConnectionManager.getInstance().notifyStreamEnd();
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
