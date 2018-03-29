package se.kth.molguin.tracedemo.network;

import java.io.DataInputStream;
import java.io.IOException;
import java.net.Socket;

import static java.lang.System.exit;

/**
 * Created by molguin on 2018-02-16.
 */

public abstract class SocketInputThread implements Runnable {

    //private DataInputStream socket_in;
    private Socket socket;
    private boolean running;
    private int read;
    //StatCollector statCollector;

    SocketInputThread(Socket socket) throws IOException {
        //this.socket_in = new DataInputStream(socket.getInputStream());
        this.socket = socket;
        this.running = true;
        this.read = 0;
        //this.statCollector = statCollector;
    }

    public int getRead() {
        return read;
    }

    public void stop() {
        synchronized (this) {
            running = false;
        }
    }

    protected abstract int processIncoming(DataInputStream socket_in) throws IOException, InterruptedException;

    @Override
    public void run() {
        try (DataInputStream socket_in = new DataInputStream(socket.getInputStream())) {
            while (true) {
                synchronized (this) {
                    if (!running) return;
                }

                read += processIncoming(socket_in);
            }

        } catch (IOException e) {
            e.printStackTrace();
        } catch (InterruptedException e) {
            e.printStackTrace();
            exit(-1);
        } finally {
            this.stop();
        }
    }
}
