package se.kth.molguin.edgedroid.network.control.experiment;

import android.support.annotation.NonNull;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

public class Sockets implements AutoCloseable {
    private final static int DEFAULT_SOCKET_TIMEOUT = 250;

    public final Socket video;
    public final Socket result;
    public final Socket control;

    public Sockets(@NonNull Config config) throws ExecutionException, InterruptedException {

        ExecutorService execs = Executors.newCachedThreadPool();

        Future<Socket> video_future = execs.submit(
                getConnectCallable(config.server, config.video_port, DEFAULT_SOCKET_TIMEOUT));

        Future<Socket> result_future = execs.submit(
                getConnectCallable(config.server, config.result_port, DEFAULT_SOCKET_TIMEOUT));

        Future<Socket> control_future = execs.submit(
                getConnectCallable(config.server, config.control_port, DEFAULT_SOCKET_TIMEOUT));

        // TODO: Fix exceptions to more descriptive ones

        this.video = video_future.get();
        this.result = result_future.get();
        this.control = control_future.get();

        execs.shutdownNow();
    }

    @Override
    public void close() throws IOException {
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
        while (!connected) {
            try {
                socket = new Socket();
                socket.setTcpNoDelay(true);
                socket.connect(new InetSocketAddress(addr, port), timeout_ms);
                connected = true;
            } catch (SocketTimeoutException ignored) {
            }
        }
        return socket;
    }
}
