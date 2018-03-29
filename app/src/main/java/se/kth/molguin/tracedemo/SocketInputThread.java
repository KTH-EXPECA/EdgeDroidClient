package se.kth.molguin.tracedemo;

import android.util.Log;

import java.io.DataInputStream;
import java.io.IOException;
import java.net.Socket;

import static java.lang.System.exit;

/**
 * Created by molguin on 2018-02-16.
 */

public class SocketInputThread implements Runnable {

    private DataInputStream socket_in;
    private boolean running;
    private int read;
    StatCollector statCollector;

    public SocketInputThread(Socket socket, StatCollector statCollector) throws IOException {
        this.socket_in = new DataInputStream(socket.getInputStream());
        this.running = true;
        this.read = 0;
        this.statCollector = statCollector;
    }

    public int getRead() {
        return read;
    }

    public void stop() {
        synchronized (this) {
            running = false;
        }
    }

    @Override
    public void run() {
        int seq = 0;
        int incoming_size = 0;
        byte[] incoming;

        try {
            // first, read the two integers detailing the simulation parameters
            socket_in.readInt();
            socket_in.readInt();
            read += 8;

            while (true) {
                synchronized (this) {
                    if (!running) return;
                }


                if (socket_in.available() > 12) {
                    seq = socket_in.readInt();
                    socket_in.readInt(); // discard the token_id
                    incoming_size = socket_in.readInt();
                    incoming = new byte[incoming_size];

                    while (socket_in.available() < incoming_size)
                        Thread.sleep(0, 500000); // 0.5 milliseconds

                    socket_in.read(incoming);
                    statCollector.recordRecvTime(seq);

                    read += 12 + incoming_size;
                } else
                    Thread.sleep(2);
            }

        } catch (IOException e) {
            e.printStackTrace();
            return;
        } catch (InterruptedException e) {
            e.printStackTrace();
            exit(-1);
        }
        catch (NegativeArraySizeException | OutOfMemoryError e)
        {
            e.printStackTrace();
            Log.e("TraceDemo", String.format("seq: %d | size: %d ", seq, incoming_size));
            throw e;
        }
    }
}
