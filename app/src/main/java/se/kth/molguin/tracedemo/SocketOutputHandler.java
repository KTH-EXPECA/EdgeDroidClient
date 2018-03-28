package se.kth.molguin.tracedemo;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.Socket;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import static java.lang.System.exit;

/**
 * Created by molguin on 2018-02-16.
 */

public class SocketOutputHandler implements Runnable {

    private DataOutputStream socket_out;
    private DataInputStream trace_in;
    private boolean running;
    private int sent;
    private StatCollector statCollector;

    public SocketOutputHandler(Socket socket, DataInputStream trace_in, StatCollector statCollector) throws IOException {
        this.socket_out = new DataOutputStream(socket.getOutputStream());
        this.trace_in = trace_in;
        this.running = true;
        this.sent = 0;
        this.statCollector = statCollector;
    }

    public int getSent() {
        return sent;
    }

    public void stop() {
        synchronized (this) {
            running = false;
        }
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


                dt = trace_in.readInt();
                seq = trace_in.readInt();
                size = trace_in.readInt();
                data = new byte[size];
                trace_in.read(data);

                // wait the specified time
                dt = dt - (System.currentTimeMillis() - loop_start_t);
                if (dt < 0)
                    dt = 0;
                Thread.sleep(dt);

                //send data
                statCollector.recordSentTime(seq);
                socket_out.write(data);
                sent += size;
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
