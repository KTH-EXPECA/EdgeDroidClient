package se.kth.molguin.tracedemo.network.control.experiment;

import android.support.annotation.NonNull;
import android.util.Log;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorCompletionService;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static java.lang.System.exit;

public class Sockets {

    private static final String LOG_TAG = "Sockets";
    public final int DEFAULT_SOCKET_TIMEOUT = 250;

    public final Socket video;
    public final Socket result;
    public final Socket control;

    public final int video_port;
    public final int result_port;
    public final int control_port;

    public final String server;

    public Sockets(@NonNull Config config) throws ExecutionException, InterruptedException {
        this.server = config.server;
        this.video_port = config.video_port;
        this.result_port = config.result_port;
        this.control_port = config.control_port;

        ExecutorService execs = Executors.newCachedThreadPool();

        Future<Socket> video_future = execs.submit(
                getConnectCallable(this.server, this.video_port, DEFAULT_SOCKET_TIMEOUT));

        Future<Socket> result_future = execs.submit(
                getConnectCallable(this.server, this.result_port, DEFAULT_SOCKET_TIMEOUT));

        Future<Socket> control_future = execs.submit(
                getConnectCallable(this.server, this.control_port, DEFAULT_SOCKET_TIMEOUT));

        this.video = video_future.get();
        this.result = result_future.get();
        this.control = control_future.get();

        execs.shutdownNow();
    }

    public void disconnect() throws IOException {
        Log.i(LOG_TAG, "Disconnecting sockets...");
        this.video.close();
        this.result.close();
        this.control.close();
    }

    private static Callable<Socket> getConnectCallable(final String addr,
                                                       final int port,
                                                       final int timeout_ms) {
        return new Callable<Socket>() {
            @Override
            public Socket call() throws IOException {
                return Sockets.prepareSocket(addr, port, timeout_ms);
            }
        };
    }

    private static Socket prepareSocket(String addr, int port, int timeout_ms) throws IOException {
        boolean connected = false;
        Socket socket = null;

        Log.i(LOG_TAG, String.format("Connecting to %s:%d", addr, port));
        while (!connected) {
            try {
                socket = new Socket();
                socket.setTcpNoDelay(true);
                socket.connect(new InetSocketAddress(addr, port), timeout_ms);
                connected = true;
            } catch (SocketTimeoutException e) {
                Log.i(LOG_TAG, "Could not connect, retrying...");
            }
        }
        Log.i(LOG_TAG, String.format("Connected to %s:%d", addr, port));
        return socket;
    }

}
