package se.kth.molguin.tracedemo.network;

import android.util.Log;

import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.Socket;
import java.util.Locale;
import java.util.Queue;
import java.util.concurrent.LinkedBlockingQueue;

import se.kth.molguin.tracedemo.network.gabriel.ConnectionManager;
import se.kth.molguin.tracedemo.network.gabriel.ProtocolConst;
import se.kth.molguin.tracedemo.network.gabriel.TokenManager;
import se.kth.molguin.tracedemo.network.task.TaskStep;

import static java.lang.System.exit;

public class VideoOutputThread implements Runnable {

    private static final Object lastsentlock = new Object();
    private static final Object framelock = new Object();
    private static final Object runlock = new Object();

    private int last_notified_frame_id;
    private VideoFrame last_sent_frame;
    private byte[] next_frame;
    private boolean running;
    private int frame_counter;

    private DataOutputStream socket_out;
    private TaskStep current_step;
    private TaskStep next_step;
    private Queue<DataInputStream> steps;

    public VideoOutputThread(Socket socket, DataInputStream[] steps) throws IOException {
        this.frame_counter = 0;
        this.socket_out = new DataOutputStream(socket.getOutputStream());
        this.last_sent_frame = null;

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

    public VideoFrame getLastSentFrame() throws InterruptedException {
        synchronized (lastsentlock) {
            while (last_sent_frame == null || last_notified_frame_id == last_sent_frame.id) {
                lastsentlock.wait();
            }

            VideoFrame frame = new VideoFrame(last_sent_frame);
            last_notified_frame_id = last_sent_frame.id;
            return frame;
        }
    }

    public void nextStep() {
        synchronized (runlock) {
            if (current_step != null) {
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

            Log.w("V", "Try to get token");

            // first, need to get a token
            try {
                tk.getToken();
            } catch (InterruptedException e) {
                break;
            }

            Log.w("V", "Got token");

            // now we have a token and can try to send stuff
            synchronized (framelock) {
                while (this.next_frame == null) {
                    // re-check that we're actually running
                    // wait can hang for a long while, so we need to do this
                    Log.w("V", "Waiting for frame feed");
                    synchronized (runlock) {
                        if (!this.running) break;
                    }

                    try {
                        framelock.wait();
                    } catch (InterruptedException e) {
                        break;
                    }

                    Log.w("V", "Got frame");
                }

                this.frame_counter += 1;
                frame_id = this.frame_counter;
                frame_to_send = this.next_frame;
                this.next_frame = null;
            }

            synchronized (runlock) {
                if (!this.running || frame_to_send == null) break;
            }

            Log.w("V", "Preparing to send");
            byte[] header = String.format(Locale.ENGLISH,
                    ProtocolConst.VIDEO_HEADER_FMT,
                    frame_id).getBytes();
            try {
                Log.e("V", new String(header, "utf-8"));
            } catch (UnsupportedEncodingException e) {
                e.printStackTrace();
            }

            // use auxiliary output streams to write everything out at once
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            DataOutputStream daos = new DataOutputStream(baos);
            try {
                daos.write(header.length);
                daos.write(header);
                daos.write(frame_to_send.length);
                daos.write(frame_to_send);

                byte[] out_data = baos.toByteArray();
                this.socket_out.write(out_data); // send!
                this.socket_out.flush();
                Log.w("V", "Sent " + out_data.length + " bytes");
            } catch (IOException e) {
                e.printStackTrace();
                exit(-1);
            }

            synchronized (lastsentlock) {
                // update last sent frame
                Log.w("V", "Update preview.");
                this.last_sent_frame = new VideoFrame(frame_id, frame_to_send);
                lastsentlock.notifyAll();
            }
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

        VideoFrame(VideoFrame v) {
            this.id = v.id;
            this.frame_data = v.frame_data;
        }

        public int getId() {
            return id;
        }

        public byte[] getFrameData() {
            return frame_data;
        }
    }
}
