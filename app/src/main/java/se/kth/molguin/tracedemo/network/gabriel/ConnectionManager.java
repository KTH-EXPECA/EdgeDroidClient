package se.kth.molguin.tracedemo.network.gabriel;

import java.io.DataInputStream;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import se.kth.molguin.tracedemo.network.ResultInputThread;
import se.kth.molguin.tracedemo.network.VideoOutputThread;

import static java.lang.System.exit;

public class ConnectionManager {
    private static final int THREADS = 3;
    private Socket video_socket;
    private Socket result_socket;

    /* TODO: Audio and other sensors?
    private Socket audio_socket;
    private Socket acc_socket;
     */
    private Socket control_socket;
    private String addr;
    private boolean connected;
    private ExecutorService execs;
    private TokenManager tkn;

    private DataInputStream video_trace;
    private VideoOutputThread video_out;
    private ResultInputThread result_in;

    public ConnectionManager(String addr, DataInputStream video_trace, TokenManager tkn) {
        this.addr = addr;
        this.video_socket = null;
        this.result_socket = null;
        this.control_socket = null;
        this.connected = false;
        this.tkn = tkn;
        this.video_trace = video_trace;

        video_out = null;
        result_in = null;

        this.execs = Executors.newFixedThreadPool(THREADS);
    }

    public ConnectionManager(DataInputStream video_trace, TokenManager tkn) {
        this(ProtocolConst.SERVER, video_trace, tkn);
    }

    private static Socket prepareSocket(String addr, int port) throws IOException {
        Socket socket = new Socket();
        socket.setTcpNoDelay(true);
        socket.connect(new InetSocketAddress(addr, port));
        return socket;
    }

    public void initConnections() {

        final CountDownLatch latch = new CountDownLatch(THREADS);

        // video
        Runnable vt = new Runnable() {
            @Override
            public void run() {
                if (video_socket != null) {
                    try {
                        video_socket.close();
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }

                try {
                    video_socket = ConnectionManager.prepareSocket(addr, ProtocolConst.VIDEO_STREAM_PORT);
                    latch.countDown();
                } catch (IOException e) {
                    e.printStackTrace();
                    exit(-1);
                }
            }
        };

        // results
        Runnable rt = new Runnable() {
            @Override
            public void run() {
                if (result_socket != null) {
                    try {
                        result_socket.close();
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }

                try {
                    result_socket = ConnectionManager.prepareSocket(addr, ProtocolConst.RESULT_RECEIVING_PORT);
                    latch.countDown();
                } catch (IOException e) {
                    e.printStackTrace();
                    exit(-1);
                }
            }
        };


        // control
        Runnable ct = new Runnable() {
            @Override
            public void run() {
                if (control_socket != null) {
                    try {
                        control_socket.close();
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }

                try {
                    control_socket = ConnectionManager.prepareSocket(addr, ProtocolConst.CONTROL_PORT);
                    latch.countDown();
                } catch (IOException e) {
                    e.printStackTrace();
                    exit(-1);
                }
            }
        };

        execs.execute(vt);
        execs.execute(rt);
        execs.execute(ct);

        try {
            latch.await();
        } catch (InterruptedException e) {
            e.printStackTrace();
            exit(-1);
        }

        connected = true;
    }

    void startStreaming() throws IOException, InterruptedException {
        if (!connected) throw new SocketException("Sockets not connected.");
        if (this.video_out != null) {
            this.video_out.stop();
            this.video_out = null;
        }
        if (this.result_in != null) {
            this.result_in.stop();
            this.result_in = null;
        }

        execs.awaitTermination(100, TimeUnit.MILLISECONDS);

        this.video_out = new VideoOutputThread(video_socket, video_trace, tkn);
        this.result_in = new ResultInputThread(result_socket, tkn);

        execs.execute(video_out);
        execs.execute(result_in);
    }

    void shutDown() throws InterruptedException, IOException {
        if (this.video_out != null) {
            this.video_out.stop();
            this.video_out = null;
        }
        if (this.result_in != null) {
            this.result_in.stop();
            this.result_in = null;
        }

        execs.awaitTermination(100, TimeUnit.MILLISECONDS);
        video_socket.close();
        result_socket.close();
        control_socket.close();
    }

}