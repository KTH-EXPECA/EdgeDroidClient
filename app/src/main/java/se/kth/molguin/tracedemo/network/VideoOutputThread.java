package se.kth.molguin.tracedemo.network;

import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.Socket;
import java.util.Locale;

import se.kth.molguin.tracedemo.network.gabriel.ConnectionManager;
import se.kth.molguin.tracedemo.network.gabriel.ProtocolConst;
import se.kth.molguin.tracedemo.network.gabriel.TokenManager;

public class VideoOutputThread extends SocketOutputThread {

    private TokenManager tkman;
    private static final Object lock = new Object();
    private int last_notified_frame_id;
    private VideoFrame last_sent_frame;

    public VideoOutputThread(Socket socket, DataInputStream trace_in, TokenManager tkman) throws IOException {
        super(socket, trace_in);
        this.last_sent_frame = null;
        this.tkman = tkman;
    }

    public VideoFrame getLastFrame() throws InterruptedException {
        synchronized (lock) {
            while (last_sent_frame == null || last_notified_frame_id == last_sent_frame.id) {
                lock.wait();
            }

            VideoFrame frame = new VideoFrame(last_sent_frame);
            last_notified_frame_id = last_sent_frame.id;
            return frame;
        }
    }

    @Override
    protected void postPacketSend() {
        synchronized (lock) {
            lock.notify();
        }
    }

    @Override
    protected void postSend() {
        ConnectionManager.getInstance().notifyStreamEnd();
    }

    @Override
    protected TracePacket prepareData() throws IOException, InterruptedException {
        long dt = trace_in.readInt();
        int id = trace_in.readInt();
        int size = trace_in.readInt();

        byte[] frame = new byte[size];
        trace_in.read(frame);

        byte[] header = String.format(Locale.ENGLISH, ProtocolConst.VIDEO_HEADER_FMT, id).getBytes();

        // pack everything
        try (
                ByteArrayOutputStream baos = new ByteArrayOutputStream();
                DataOutputStream dos = new DataOutputStream(baos);
        ) {
            dos.writeInt(header.length);
            dos.write(header);
            dos.writeInt(frame.length);
            dos.write(frame);

            last_sent_frame = new VideoFrame(id, frame);

            // block until token is available
            tkman.getToken();
            // got a token yay

            return new TracePacket(dt, baos.toByteArray());
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
