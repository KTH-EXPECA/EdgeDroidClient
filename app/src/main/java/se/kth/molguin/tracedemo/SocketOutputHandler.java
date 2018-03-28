package se.kth.molguin.tracedemo;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.Socket;

import static java.lang.System.exit;

/**
 * Created by molguin on 2018-02-16.
 */

public class SocketOutputHandler implements Runnable {

    protected DataOutputStream socket_out;
    protected DataInputStream trace_in;
    protected int sent;
    protected StatCollector statCollector;

    private boolean running;
    private long last_sent_t;

    public SocketOutputHandler(Socket socket, DataInputStream trace_in, StatCollector statCollector) throws IOException {
        this.socket_out = new DataOutputStream(socket.getOutputStream());
        this.trace_in = trace_in;
        this.running = true;
        this.sent = 0;
        this.statCollector = statCollector;
        this.last_sent_t = System.currentTimeMillis();
    }

    public int getSent() {
        return sent;
    }

    public void stop() {
        synchronized (this) {
            running = false;
        }
    }

    protected void waitForDeltaT(long dt) throws InterruptedException {
        // wait for the specified time
        dt = dt - (System.currentTimeMillis() - this.last_sent_t);
        if (dt < 0)
            return;
        Thread.sleep(dt);
    }

    protected void sendData() throws IOException, InterruptedException {
        long dt = trace_in.readInt();
        int seq = trace_in.readInt();
        int size = trace_in.readInt();
        byte[] data = new byte[size];
        trace_in.read(data);

        waitForDeltaT(dt);

        //send data
        statCollector.recordSentTime(seq);
        socket_out.write(data);
        sent += size;
    }

    @Override
    public void run() {
        long dt;
        int seq;
        int size;
        byte[] data;

        long loop_start_t;

        try {
            while (trace_in.available() > 0) {

                loop_start_t = System.currentTimeMillis();

                synchronized (this) {
                    if (!running) break;
                }

                sendData();

//                dt = trace_in.readInt();
//                seq = trace_in.readInt();
//                size = trace_in.readInt();
//                data = new byte[size];
//                trace_in.read(data);
//
//                // wait the specified time
//                dt = dt - (System.currentTimeMillis() - loop_start_t);
//                if (dt < 0)
//                    dt = 0;
//                Thread.sleep(dt);
//
//                //send data
//                statCollector.recordSentTime(seq);
//                socket_out.write(data);
//                sent += size;
            }

            socket_out.close();
        } catch (IOException e) {
            e.printStackTrace();
            return;
        } catch (InterruptedException e) {
            e.printStackTrace();
            exit(-1);
        }



    }
}
