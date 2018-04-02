package se.kth.molguin.tracedemo.network.gabriel;

import java.io.DataInputStream;
import java.io.IOException;
import java.net.ConnectException;
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

    public enum EXCEPTIONSTATE {
        ALREADYCONNECTED,
        NOTCONNECTED,
        ALREADYSTREAMING,
        NOTRACE,
        NOADDRESS
    }

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
    private boolean streaming;
    private ExecutorService execs;
    private TokenManager tkn;

    private DataInputStream video_trace;
    private VideoOutputThread video_out;
    private ResultInputThread result_in;

    private static ConnectionManager instance = null;

    public static ConnectionManager getInstance() {
        if(instance == null)
            instance = new ConnectionManager();
        return instance;
    }

    private ConnectionManager() {
        this.addr = null;
        this.video_socket = null;
        this.result_socket = null;
        this.control_socket = null;
        this.connected = false;
        this.streaming = false;
        this.tkn = TokenManager.getInstance();
        this.video_trace = null;

        video_out = null;
        result_in = null;

        this.execs = Executors.newFixedThreadPool(THREADS);
    }

    private static Socket prepareSocket(String addr, int port) throws IOException {
        Socket socket = new Socket();
        socket.setTcpNoDelay(true);
        socket.connect(new InetSocketAddress(addr, port));
        return socket;
    }

    public void initConnections() throws ConnectionManagerException {
        if (addr == null) throw new ConnectionManagerException(EXCEPTIONSTATE.NOADDRESS);
        if (connected) throw  new ConnectionManagerException(EXCEPTIONSTATE.ALREADYCONNECTED);
        if (streaming) throw new ConnectionManagerException(EXCEPTIONSTATE.ALREADYSTREAMING);

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

    public void startStreaming() throws IOException, InterruptedException, ConnectionManagerException {
        if (video_trace == null) throw new ConnectionManagerException(EXCEPTIONSTATE.NOTRACE);
        if (streaming) throw new ConnectionManagerException(EXCEPTIONSTATE.ALREADYSTREAMING);
        //if (tkn == null) throw new ConnectionManagerException("No tokenmanager set!");
        if (!connected) throw new SocketException("Sockets not connected.");

        if (this.video_out != null)
            this.video_out.stop();
        if (this.result_in != null)
            this.result_in.stop();

        execs.awaitTermination(100, TimeUnit.MILLISECONDS);

        this.video_out = new VideoOutputThread(video_socket, video_trace, tkn);
        this.result_in = new ResultInputThread(result_socket, tkn);

        execs.execute(video_out);
        execs.execute(result_in);
        this.streaming = true;
    }

    public void shutDown() throws InterruptedException, IOException {
        if (this.video_out != null)
            this.video_out.stop();
        if (this.result_in != null)
            this.result_in.stop();

        execs.awaitTermination(100, TimeUnit.MILLISECONDS);

        this.result_in = null;
        this.video_out = null;
        video_socket.close();
        result_socket.close();
        control_socket.close();

        this.streaming = false;
        this.connected = false;
    }

    public boolean isConnected()
    {
        return connected;
    }

    public boolean isStreaming() {
        return streaming;
    }

    public void setTrace(DataInputStream video_trace) throws ConnectionManagerException {
        if (connected) throw new ConnectionManagerException(EXCEPTIONSTATE.ALREADYCONNECTED);
        if (streaming) throw new ConnectionManagerException(EXCEPTIONSTATE.ALREADYSTREAMING);

        this.video_trace = video_trace;
    }

    public void setAddr(String addr) throws ConnectionManagerException {
        if (connected) throw new ConnectionManagerException(EXCEPTIONSTATE.ALREADYCONNECTED);
        if (streaming) throw new ConnectionManagerException(EXCEPTIONSTATE.ALREADYSTREAMING);

        this.addr = addr;
    }

    public class ConnectionManagerException extends Exception {

        private EXCEPTIONSTATE state;
        private String CMExceptMsg;

        ConnectionManagerException(EXCEPTIONSTATE state) {
            super("ConnectionManager Exception");
            this.state = state;
            switch (state){
                case NOTRACE:
                    this.CMExceptMsg = "No trace set!";
                    break;
                case NOADDRESS:
                    this.CMExceptMsg = "No address set!";
                    break;
                case ALREADYCONNECTED:
                    this.CMExceptMsg = "Already connected!";
                    break;
                case ALREADYSTREAMING:
                    this.CMExceptMsg = "Already streaming!";
                    break;
                case NOTCONNECTED:
                    this.CMExceptMsg = "Not connected to a Gabriel server!";
                    break;
                default:
                    this.CMExceptMsg = "";
                    break;
            }
        }

        public EXCEPTIONSTATE getState() {
            return state;
        }

        @Override
        public String getMessage()
        {
            return super.getMessage() + ": " + this.CMExceptMsg;
        }
    }
}