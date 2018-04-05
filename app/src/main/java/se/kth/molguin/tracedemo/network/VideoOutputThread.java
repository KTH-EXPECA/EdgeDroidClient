package se.kth.molguin.tracedemo.network;

import java.io.DataInputStream;
import java.io.IOException;
import java.net.Socket;

import se.kth.molguin.tracedemo.network.gabriel.ConnectionManager;
import se.kth.molguin.tracedemo.network.gabriel.TokenManager;

public class VideoOutputThread implements Runnable {

    private static final Object lock = new Object();
    private int last_notified_frame_id;
    private VideoFrame last_sent_frame;
    private byte[] next_frame;
    private boolean running;

    public VideoOutputThread(Socket socket, DataInputStream trace_in) throws IOException {
        this.last_sent_frame = null;
    }

    public VideoFrame getLastSentFrame() throws InterruptedException {
        synchronized (lock) {
            while (last_sent_frame == null || last_notified_frame_id == last_sent_frame.id) {
                lock.wait();
            }

            VideoFrame frame = new VideoFrame(last_sent_frame);
            last_notified_frame_id = last_sent_frame.id;
            return frame;
        }
    }

    protected void postPacketSend() {
        synchronized (lock) {
            lock.notify();
        }
    }

    protected void postSend() {
        ConnectionManager.getInstance().notifyStreamEnd();
    }


    @Override
    public void run() {
        TokenManager tk = TokenManager.getInstance();

        while (true) {
            synchronized (lock) {
                if (!running) break;
                if (next_frame == null) continue;
            }
        }


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
