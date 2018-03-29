package se.kth.molguin.tracedemo.network;

import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.Socket;
import java.util.Locale;

import se.kth.molguin.tracedemo.Const;

public class VideoOutputThread extends SocketOutputThread {

    private byte[] last_frame;

    public VideoOutputThread(Socket socket, DataInputStream trace_in) throws IOException {
        super(socket, trace_in);
        last_frame = new byte[]{0};
    }

    byte[] getLastFrame() {
        return last_frame;
    }

    @Override
    protected TracePacket prepareData() throws IOException, InterruptedException {
        long dt = trace_in.readInt();
        int id = trace_in.readInt();
        int size = trace_in.readInt();

        byte[] frame = new byte[size];
        trace_in.read(frame);

        byte[] header = String.format(Locale.ENGLISH, Const.VIDEO_HEADER_FMT, id).getBytes();

        // pack everything
        try (
                ByteArrayOutputStream baos = new ByteArrayOutputStream();
                DataOutputStream dos = new DataOutputStream(baos);
        ) {
            dos.writeInt(header.length);
            dos.write(header);
            dos.writeInt(frame.length);
            dos.write(frame);

            last_frame = frame;
            return new TracePacket(dt, baos.toByteArray());
        }
    }
}
