package se.kth.molguin.tracedemo;

import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.Socket;
import java.util.Locale;

public class VideoOutputHandler extends SocketOutputHandler {

    public VideoOutputHandler(Socket socket, DataInputStream trace_in, StatCollector statCollector) throws IOException {
        super(socket, trace_in, statCollector);
    }

    @Override
    protected void sendData() throws IOException, InterruptedException {
        long dt = trace_in.readInt();
        int id = trace_in.readInt();
        int size = trace_in.readInt();

        byte[] frame = new byte[size];
        trace_in.read(frame);

        byte[] header = String.format(Locale.ENGLISH, Const.VIDEO_HEADER_FMT, id).getBytes();

        // pack everything and send
        try (
                ByteArrayOutputStream baos = new ByteArrayOutputStream();
                DataOutputStream dos = new DataOutputStream(baos);
        ) {
            dos.writeInt(header.length);
            dos.write(header);
            dos.writeInt(frame.length);
            dos.write(frame);

            waitForDeltaT(dt);

            //send data
            // statCollector.recordSentTime(seq);
            socket_out.write(baos.toByteArray());
            socket_out.flush();
        }

        sent += size;
    }
}
