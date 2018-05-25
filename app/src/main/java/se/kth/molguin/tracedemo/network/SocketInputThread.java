package se.kth.molguin.tracedemo.network;

import java.io.DataInputStream;
import java.io.IOException;
import java.net.Socket;
import java.net.SocketException;
import java.util.concurrent.locks.ReentrantLock;

import static java.lang.System.exit;

/**
 * Created by molguin on 2018-02-16.
 */

public abstract class SocketInputThread implements Runnable {

    //private DataInputStream socket_in;
    private ReentrantLock lock;
    private Socket socket;
    private boolean running;
    private int read;
    //StatCollector statCollector;

    SocketInputThread(Socket socket) throws IOException {
        //this.socket_in = new DataInputStream(socket.getInputStream());
        this.lock = new ReentrantLock();
        this.socket = socket;
        this.running = true;
        this.read = 0;
        //this.statCollector = statCollector;
    }

    public int getRead() {
        return read;
    }

    public void stop() {
        this.lock.lock();
        try {
            running = false;
        } finally {
            this.lock.unlock();
        }
    }

    protected abstract int processIncoming(DataInputStream socket_in) throws IOException, InterruptedException;

    @Override
    public void run() {
        try (DataInputStream socket_in = new DataInputStream(socket.getInputStream())) {
            while (true) {
                this.lock.lock();
                try {
                    if (!running) return;
                } finally {
                    this.lock.unlock();
                }

                read += processIncoming(socket_in);
            }

        } catch (SocketException ignored) {
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
