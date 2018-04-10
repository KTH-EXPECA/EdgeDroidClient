package se.kth.molguin.tracedemo.network;

import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.Socket;
import java.util.Locale;
import java.util.Queue;
import java.util.concurrent.LinkedBlockingQueue;

import se.kth.molguin.tracedemo.Constants;
import se.kth.molguin.tracedemo.network.gabriel.ConnectionManager;
import se.kth.molguin.tracedemo.network.gabriel.ProtocolConst;
import se.kth.molguin.tracedemo.network.gabriel.TokenManager;
import se.kth.molguin.tracedemo.task.TaskStep;

import static java.lang.System.exit;

public class VideoOutputThread implements Runnable {

    private static final Object framelock = new Object();
    private static final Object runlock = new Object();

    private byte[] next_frame;
    private boolean running;
    private int frame_counter;

    private DataOutputStream socket_out;
    private TaskStep current_step;
    private TaskStep next_step;
    private Queue<DataInputStream> steps;
    private boolean rewinded;

    public VideoOutputThread(Socket socket, DataInputStream[] steps) throws IOException {
        this.frame_counter = 0;
        this.rewinded = false;
        this.socket_out = new DataOutputStream(socket.getOutputStream());

        this.steps = new LinkedBlockingQueue<DataInputStream>(steps.length);
        for (DataInputStream step : steps)
            this.steps.offer(step);

        DataInputStream current_step_f = this.steps.poll();
        DataInputStream next_step_f = this.steps.poll();
        this.current_step = null;
        this.next_step = null;

        if (current_step_f != null)
            this.current_step = new TaskStep(current_step_f, this);
        if (next_step_f != null)
            this.next_step = new TaskStep(next_step_f, this);
    }

    public void nextStep() {
        synchronized (runlock) {
            if (rewinded) {
                // don't continue if we had to rewind
                rewinded = false;
            } else if (current_step != null) {
                if (this.next_step == null) {
                    this.finish();
                    this.current_step = null;
                } else {
                    this.current_step.stop();
                    this.current_step = this.next_step;
                    this.current_step.start();

                    DataInputStream next_step_f = this.steps.poll();
                    if (next_step_f != null)
                        this.next_step = new TaskStep(next_step_f, this);
                    else
                        this.next_step = null;
                }
            }
        }
    }

    /**
     * Rewinds current step a fixed number of seconds in case of error.
     */
    public void rewind() {
        synchronized (runlock) {
            if (current_step != null)
                current_step.rewind(Constants.REWIND_SECONDS);
            this.rewinded = true;
        }
    }

    public void pushFrame(byte[] frame) {
        synchronized (framelock) {
            this.next_frame = frame;
            framelock.notifyAll();
        }
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
            } catch (IOException e) {
                e.printStackTrace();
                exit(-1);
            }

            ConnectionManager.getInstance().notifySentFrame(new VideoFrame(frame_id, frame_to_send));
        }

        synchronized (runlock) {
            if (this.running) this.finish();
        }

        ConnectionManager.getInstance().notifyStreamEnd();
    }

    public class VideoFrame {
        int id;
        byte[] frame_data;

        VideoFrame(int id, byte[] data) {
            this.id = id;
            this.frame_data = data;
        }

        public int getId() {
            return id;
        }

        public byte[] getFrameData() {
            return frame_data;
        }
    }
}
