package se.kth.molguin.tracedemo.network.gabriel;

import java.io.DataInputStream;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import se.kth.molguin.tracedemo.Constants;
import se.kth.molguin.tracedemo.network.ResultInputThread;
import se.kth.molguin.tracedemo.network.VideoOutputThread;

import static java.lang.System.exit;

public class ConnectionManager {

    private static final int THREADS = 4;
    private static final Object lock = new Object();
    private static final Object last_frame_lock = new Object();
    private static ConnectionManager instance = null;
    //private DataInputStream video_trace;
    private DataInputStream[] step_traces;
    private Socket video_socket;
    private Socket result_socket;
    /* TODO: Audio and other sensors?
    private Socket audio_socket;
    private Socket acc_socket;
     */
    private Socket control_socket;
    private String addr;
    private ExecutorService execs;
    private TokenManager tkn;
    private VideoOutputThread video_out;
    private ResultInputThread result_in;
    private CMSTATE state;

    private int current_error_count;

    private VideoOutputThread.VideoFrame last_sent_frame;
    private boolean got_new_frame;

    private ConnectionManager() {
        this.addr = null;
        this.video_socket = null;
        this.result_socket = null;
        this.control_socket = null;
        this.tkn = TokenManager.getInstance();
        //this.video_trace = null;
        this.step_traces = null;

        this.video_out = null;
        this.result_in = null;

        this.changeStateAndNotify(CMSTATE.DISCONNECTED);
        this.execs = Executors.newFixedThreadPool(THREADS);

        this.last_sent_frame = null;
        this.got_new_frame = false;

        this.current_error_count = 0;
    }

    private static Socket prepareSocket(String addr, int port) throws IOException {
        Socket socket = new Socket();
        socket.setTcpNoDelay(true);
        socket.connect(new InetSocketAddress(addr, port));
        return socket;
    }

    public static ConnectionManager getInstance() {
        synchronized (lock) {
            if (instance == null)
                instance = new ConnectionManager();
            return instance;
        }
    }

    public static void shutDownAndDelete() throws IOException, InterruptedException {
        synchronized (lock) {
            if (instance != null) {
                instance.shutDown();
                instance = null;
            }
        }
    }

    public void shutDown() throws InterruptedException, IOException {

        synchronized (lock) {
            switch (this.state) {
                case DISCONNECTED:
                case DISCONNECTING:
                    return;
                default:
                    break;
            }
        }

        this.changeStateAndNotify(CMSTATE.DISCONNECTING);

        if (this.video_out != null)
            this.video_out.finish();
        if (this.result_in != null)
            this.result_in.stop();

        execs.awaitTermination(100, TimeUnit.MILLISECONDS);

        this.result_in = null;
        this.video_out = null;

        if (this.video_socket != null)
            video_socket.close();

        if (this.result_socket != null)
            result_socket.close();

        if (control_socket != null)
            control_socket.close();

        this.changeStateAndNotify(CMSTATE.DISCONNECTED);
    }

    private void changeStateAndNotify(CMSTATE new_state) {
        synchronized (lock) {
            if (this.state == new_state) return;
            this.state = new_state;
            lock.notifyAll();
        }
    }

    public void notifyStreamEnd() {
        this.changeStateAndNotify(CMSTATE.STREAMING_DONE);
    }

    public void waitForState(CMSTATE state) throws InterruptedException {
        synchronized (lock) {
            while (state != this.state) {
                lock.wait();
            }
        }
    }

    public void waitForStateChange() throws InterruptedException {
        synchronized (lock) {
            CMSTATE previous_state = this.state;
            while (previous_state == this.state) {
                lock.wait();
            }
        }
    }

    public void startStreaming() throws IOException, ConnectionManagerException {
        if (this.step_traces == null)
            throw new ConnectionManagerException(EXCEPTIONSTATE.NOTRACE);

        synchronized (lock) {
            switch (this.state) {
                case STREAMING:
                    throw new ConnectionManagerException(EXCEPTIONSTATE.ALREADYSTREAMING);
                case CONNECTING:
                case DISCONNECTED:
                case DISCONNECTING:
                    throw new ConnectionManagerException(EXCEPTIONSTATE.NOTCONNECTED);
                default:
                    break;
            }
        }

        this.video_out = new VideoOutputThread(video_socket, step_traces);
        this.result_in = new ResultInputThread(result_socket, tkn);

        execs.execute(video_out);
        execs.execute(result_in);

        this.changeStateAndNotify(CMSTATE.STREAMING);
    }

    public void initConnections() throws ConnectionManagerException {
        if (this.addr == null) throw new ConnectionManagerException(EXCEPTIONSTATE.NOADDRESS);

        synchronized (lock) {
            if (this.state != CMSTATE.DISCONNECTED)
                throw new ConnectionManagerException(EXCEPTIONSTATE.ALREADYCONNECTED);
        }

        this.changeStateAndNotify(CMSTATE.CONNECTING);
        final CountDownLatch latch = new CountDownLatch(3); // TODO: Fix magic number

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

        // additional thread to make this method asynchronous and still be able to wait for
        // all three connections to execute before changing state.
//        execs.execute(new Runnable() {
//            @Override
//            public void run() {
//
//            }
//        });

        execs.execute(vt);
        execs.execute(rt);
        execs.execute(ct);

        try {
            latch.await();
        } catch (InterruptedException e) {
            e.printStackTrace();
            exit(-1);
        }
        this.changeStateAndNotify(CMSTATE.CONNECTED);
    }

    public CMSTATE getState() {
        synchronized (lock) {
            return this.state;
        }
    }

    public void setTrace(DataInputStream[] steps) throws ConnectionManagerException {
        synchronized (lock) {
            if (this.state != CMSTATE.DISCONNECTED)
                throw new ConnectionManagerException(EXCEPTIONSTATE.ALREADYCONNECTED);
        }

        this.step_traces = steps;
    }

    public VideoOutputThread.VideoFrame getLastFrame() throws InterruptedException {
        synchronized (last_frame_lock) {
            while (!this.got_new_frame)
                last_frame_lock.wait();

            return this.last_sent_frame;
        }
    }

    public void setAddr(String addr) throws ConnectionManagerException {
        synchronized (lock) {
            if (this.state != CMSTATE.DISCONNECTED)
                throw new ConnectionManagerException(EXCEPTIONSTATE.ALREADYCONNECTED);
        }

        this.addr = addr;
    }

    public void notifySuccessForFrame(int frame_id) {
        // TODO: more?
        // probably register statistics here in the future
        this.current_error_count = 0;
        this.video_out.nextStep();
    }

    public void notifyMistakeForFrame(int frame_id) {
        // TODO: more?
        // only rewind after a minimum number of mistakes

        this.current_error_count++;
        if (this.current_error_count >= Constants.MIN_MISTAKE_COUNT)
            this.video_out.rewind();
    }

    public void notifySentFrame(VideoOutputThread.VideoFrame frame) {
        synchronized (last_frame_lock) {
            this.last_sent_frame = frame;
            this.got_new_frame = true;
            last_frame_lock.notifyAll();
        }
    }

    public enum EXCEPTIONSTATE {
        ALREADYCONNECTED,
        NOTCONNECTED,
        ALREADYSTREAMING,
        NOTRACE,
        NOADDRESS,
        INVALIDTRACEDIR
    }

    public enum CMSTATE {
        DISCONNECTED,
        CONNECTING,
        CONNECTED,
        STREAMING,
        STREAMING_DONE,
        DISCONNECTING
    }

    public class ConnectionManagerException extends Exception {

        private EXCEPTIONSTATE state;
        private String CMExceptMsg;

        ConnectionManagerException(EXCEPTIONSTATE state) {
            super("ConnectionManager Exception");
            this.state = state;
            switch (state) {
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
        public String getMessage() {
            return super.getMessage() + ": " + this.CMExceptMsg;
        }
    }
}
