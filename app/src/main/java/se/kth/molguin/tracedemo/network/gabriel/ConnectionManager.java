package se.kth.molguin.tracedemo.network.gabriel;

import org.apache.commons.math3.stat.descriptive.DescriptiveStatistics;
import org.apache.commons.math3.stat.descriptive.SummaryStatistics;

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
import se.kth.molguin.tracedemo.network.VideoFrame;
import se.kth.molguin.tracedemo.network.VideoOutputThread;

import static java.lang.System.exit;

public class ConnectionManager {

    private static final int THREADS = 4;
    private static final int STAT_WINDOW_SZ = 15;

    private static final Object lock = new Object();
    private static final Object last_frame_lock = new Object();
    private static final Object stat_lock = new Object();

    private static ConnectionManager instance = null;
    // Statistics
    DescriptiveStatistics rolling_rtt_stats;
    SummaryStatistics total_rtt_stats;
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
    //private boolean got_new_frame;
    private int current_error_count;
    private VideoFrame last_sent_frame;

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
        //this.got_new_frame = false;

        this.current_error_count = 0;

        this.total_rtt_stats = new SummaryStatistics();
        this.rolling_rtt_stats = new DescriptiveStatistics(STAT_WINDOW_SZ);
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

    private void changeStateAndNotify(CMSTATE new_state) {
        synchronized (lock) {
            if (this.state == new_state) return;
            this.state = new_state;
            lock.notifyAll();
        }
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

    public VideoFrame getLastFrame() throws InterruptedException {
        synchronized (last_frame_lock) {
            // removed because frame update is now at a fixed rate:
            //while (!this.got_new_frame)
            //    last_frame_lock.wait();

            //this.got_new_frame = false;
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

    public void notifySuccessForFrame(VideoFrame frame) {
        synchronized (stat_lock) {
            this.current_error_count = 0;
            registerStats(frame);
        }

        this.video_out.nextStep();
    }

    private void registerStats(VideoFrame in_frame) {
        synchronized (stat_lock) {
            if (in_frame.getId() == this.last_sent_frame.getId()) {
                long rtt = in_frame.getTimestamp() - this.last_sent_frame.getTimestamp();
                this.rolling_rtt_stats.addValue(rtt);
                this.total_rtt_stats.addValue(rtt);
            }
        }
    }

    public void notifyMistakeForFrame(VideoFrame frame) {

        int errors;
        synchronized (stat_lock) {
            this.current_error_count++;
            errors = this.current_error_count;
            registerStats(frame);
        }

        if (errors >= Constants.MIN_MISTAKE_COUNT)
            this.video_out.rewind();
    }

    public void notifyNoResultForFrame(VideoFrame frame) {
        registerStats(frame);
    }

    public void notifySentFrame(VideoFrame frame) {
        synchronized (last_frame_lock) {
            this.last_sent_frame = frame;
            //this.got_new_frame = true;
            //last_frame_lock.notifyAll();
        }
    }

    public double getRollingRTT() {
        synchronized (stat_lock) {
            return rolling_rtt_stats.getMean();
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
