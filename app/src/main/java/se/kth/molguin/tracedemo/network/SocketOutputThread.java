package se.kth.molguin.tracedemo.network;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.Socket;

import static java.lang.System.exit;

/**
 * Created by molguin on 2018-02-16.
 */

public abstract class SocketOutputThread implements Runnable {

    //protected DataOutputStream socket_out;
    DataInputStream trace_in;
    //protected StatCollector statCollector;

    private int sent;
    private boolean running;
    private long last_sent_t;
    private Socket socket;

    SocketOutputThread(Socket socket, DataInputStream trace_in) throws IOException {
        this.socket = socket;
        //this.socket_out = new DataOutputStream(socket.getOutputStream());
        this.trace_in = trace_in;
        this.running = true;
        this.sent = 0;
        //this.statCollector = statCollector;
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

    private void waitForDeltaT(long dt) throws InterruptedException {
        // wait for the specified time
        dt = dt - (System.currentTimeMillis() - this.last_sent_t);
        if (dt < 0)
            return;
        Thread.sleep(dt);
    }

    protected abstract TracePacket prepareData() throws IOException, InterruptedException;
    //{
    //long dt = trace_in.readInt();
    //int seq = trace_in.readInt();
    //int size = trace_in.readInt();
    //byte[] data = new byte[size];
    //trace_in.read(data);

    //waitForDeltaT(dt);

        //send data
    //statCollector.recordSentTime(seq);
    //socket_out.write(data);
    //return data;
    //}

    // called after sending a packet, in case the user wants to do something
    protected void postSend() {
    }

    ;

    @Override
    public void run() {
        TracePacket p;
        try (DataOutputStream socket_out = new DataOutputStream(socket.getOutputStream())) {
            while (trace_in.available() > 0) {

                synchronized (this) {
                    if (!running) break;
                }

                p = prepareData();

                waitForDeltaT(p.delta_t);
                socket_out.write(p.data);
                socket_out.flush();
                last_sent_t = System.currentTimeMillis();
                sent += p.data.length;
                postSend();

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
        } catch (IOException e) {
            // socket is closed, usually
            e.printStackTrace();
        } catch (InterruptedException e) {
            e.printStackTrace();
            exit(-1);
        } finally {
            this.stop();
        }
    }

    public class TracePacket {
        long delta_t;
        byte[] data;

        TracePacket(long dt, byte[] data) {
            this.delta_t = dt;
            this.data = data;
        }
    }
}
