package se.kth.molguin.tracedemo.network.gabriel;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

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


    public ConnectionManager(String addr, TokenManager tkn) {
        this.addr = addr;
        this.video_socket = null;
        this.result_socket = null;
        this.control_socket = null;
        this.connected = false;
        this.tkn = tkn;

        this.execs = Executors.newFixedThreadPool(THREADS);
    }

    public ConnectionManager(TokenManager tkn) {
        this(ProtocolConst.SERVER, tkn);
    }

    private static Socket prepareSocket(String addr, int port) throws IOException {
        Socket socket = new Socket();
        socket.setTcpNoDelay(true);
        socket.connect(new InetSocketAddress(addr, port));
        return socket;
    }

    public void initConnections() {

        // video
        Thread vt = new Thread(new Runnable() {
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
                } catch (IOException e) {
                    e.printStackTrace();
                    exit(-1);
                }
            }
        });

        // results
        Thread rt = new Thread(new Runnable() {
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
                } catch (IOException e) {
                    e.printStackTrace();
                    exit(-1);
                }
            }
        });


        // control
        Thread ct = new Thread(new Runnable() {
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
                } catch (IOException e) {
                    e.printStackTrace();
                    exit(-1);
                }
            }
        });

        vt.start();
        rt.start();
        ct.start();

        try {
            vt.join();
            rt.join();
            ct.join();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

        connected = true;
    }
}