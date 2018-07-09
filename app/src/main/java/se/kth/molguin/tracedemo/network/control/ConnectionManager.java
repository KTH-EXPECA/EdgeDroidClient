package se.kth.molguin.tracedemo.network.control;

import android.content.Context;
import android.util.Log;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.lang.ref.WeakReference;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import se.kth.molguin.tracedemo.MainActivity;
import se.kth.molguin.tracedemo.network.ResultInputThread;
import se.kth.molguin.tracedemo.network.VideoFrame;
import se.kth.molguin.tracedemo.network.VideoOutputThread;
import se.kth.molguin.tracedemo.network.control.experiment.Config;
import se.kth.molguin.tracedemo.network.control.experiment.RunStats;
import se.kth.molguin.tracedemo.network.gabriel.TokenPool;
import se.kth.molguin.tracedemo.synchronization.NTPClient;

import static java.lang.System.exit;

public class ConnectionManagerDeprecated {

    private static final int THREADS = 4;
    private static final int SOCKET_TIMEOUT = 250;

    private static final String LOG_TAG = "ConnectionManager";

    private static final ReadWriteLock instance_lock = new ReentrantReadWriteLock();

    private static ConnectionManager instance = null;

    private CMSTATE state;

    private final Config config;
    private int run_count;

    private ControlClient controlClient;
    private NTPClient ntpSyncer;

    private ConnectionManagerDeprecated(MainActivity mAct) {
        this.config = null;

        /* context! */
        this.app_context = mAct.getApplicationContext();
        this.mAct = new WeakReference<>(mAct);

        this.last_sent_frame = null;
        this.current_error_count = 0;
        this.run_count = 0;
        this.run_stats = null;

        this.ntpSyncer = null;

        // listen to control
        this.changeState(CMSTATE.WAITINGFORCONTROL);
        this.controlClient = new ControlClient(this.app_context, this);
    }

    public FileInputStream getFileInputFromAppContext(String path) throws FileNotFoundException {
        return this.app_context.openFileInput(path);
    }

    public void runExperiment() throws IOException, ConnectionManagerException {

        this.state_lock.writeLock().lock();
        try {
            this.run_count++;
            this.current_error_count = 0;

            Log.i(LOG_TAG, String.format("Executing experiment, run number %d", this.run_count));

            // first, sync clocks!
            //if (this.ntpSyncer == null)
            //     this.ntpSyncer = new NTPClient(ProtocolConst.SERVER);
            // else
            //    this.ntpSyncer.syncTime();

            if (this.ntpSyncer == null)
                throw new ConnectionManagerException(EXCEPTIONSTATE.NTPNOTSYNCED);

            this.backend_execs = Executors.newFixedThreadPool(THREADS);
            this.run_stats = new RunStats(this.ntpSyncer);
            this.run_stats.init();
        } finally {
            this.state_lock.writeLock().unlock();
        }

        MainActivity mAct;
        this.state_lock.readLock().lock();
        try {
            mAct = this.mAct.get();
        } finally {
            this.state_lock.readLock().unlock();
        }

        if (mAct != null)
            mAct.updateRunStatus(this.run_count);

        this.connectAndStream();
    }

    public void syncNTP() throws ConnectionManagerException {
        this.state_lock.writeLock().lock();
        if (this.config == null)
            throw new ConnectionManagerException(EXCEPTIONSTATE.NOCONFIG);
        this.changeState(CMSTATE.NTPSYNC);
        try {
            if (this.ntpSyncer == null)
                this.ntpSyncer = new NTPClient(this.config.ntp_host);
            else
                this.ntpSyncer.syncTime();
        } catch (SocketException | UnknownHostException e) {
            Log.e(LOG_TAG, "Exception!", e);
            exit(-1);
        } finally {
            this.state_lock.writeLock().unlock();
        }
    }

    public static void shutdownAndDelete() {
        instance_lock.writeLock().lock();
        try {
            if (instance != null) {
                instance.forceShutDown();
                instance = null;
            }
        } finally {
            instance_lock.writeLock().unlock();
        }
    }

    public static ConnectionManager reset(MainActivity act) {
        Log.i(LOG_TAG, "Resetting");
        shutdownAndDelete();
        return init(act);
    }

    public void pushStateToActivity() {
        this.state_lock.readLock().lock();
        try {
            MainActivity mAct = this.mAct.get();
            if (mAct != null) {
                switch (this.state) {
                    case WAITINGFORCONTROL:
                        mAct.stateConnectingControl();
                        break;
                    case CONFIGURING:
                        mAct.stateConfig();
                        break;
                    case FETCHINGTRACE:
                        mAct.stateFetchTraces();
                        break;
                    case NTPSYNC:
                        mAct.stateNTPSync();
                        break;
                    case INITEXPERIMENT:
                        mAct.stateInitExperiment();
                        break;
                    case STREAMING:
                        mAct.stateStreaming();
                        break;
                    case STREAMING_DONE:
                        mAct.stateStreamingEnd();
                        break;
                    case DISCONNECTING:
                        mAct.stateDisconnecting();
                        break;
                    case UPLOADINGRESULTS:
                        mAct.stateUploading();
                        break;
                    case DISCONNECTED:
                        mAct.stateDisconnected();
                        break;
                    case LISTENINGCONTROL:
                        mAct.stateListeningControl();
                        break;
                }
            }
        } finally {
            this.state_lock.readLock().unlock();
        }
    }

    public void changeState(CMSTATE new_state) {
        this.state_lock.writeLock().lock();
        try {
            if (this.state != new_state) {
                Log.i(LOG_TAG, "Changing state to " + new_state.name());
                this.state = new_state;
                this.pushStateToActivity();
            }
        } finally {
            this.state_lock.writeLock().unlock();
        }
    }

    public void setConfig(Config config) {
        this.state_lock.writeLock().lock();
        try {
            this.config = config;
        } finally {
            this.state_lock.writeLock().unlock();
        }
    }

    private static Socket prepareSocket(String addr, int port, int timeout_ms) {
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
            } catch (IOException e) {
                Log.e(LOG_TAG, "Exception!", e);
                exit(-1);
            }
        }
        Log.i(LOG_TAG, String.format("Connected to %s:%d", addr, port));
        return socket;
    }

    private void setMainActivity(MainActivity mAct) {
        this.state_lock.writeLock().lock();
        try {
            this.mAct = new WeakReference<>(mAct);
            this.app_context = mAct.getApplicationContext();
        } finally {
            this.state_lock.writeLock().unlock();
        }
    }

    public static ConnectionManager init(MainActivity act) {
        instance_lock.writeLock().lock();
        try {
            if (instance != null) {
                instance.setMainActivity(act);
                return instance;
            }

            instance = new ConnectionManager(act);
            return instance;
        } finally {
            instance_lock.writeLock().unlock();
        }
    }

    private void connectAndStream() throws ConnectionManagerException, IOException {
        this.state_lock.writeLock().lock();
        try {
            if (this.config == null) throw new ConnectionManagerException(EXCEPTIONSTATE.NOCONFIG);
            if (this.backend_execs == null || this.backend_execs.isShutdown())
                this.backend_execs = Executors.newFixedThreadPool(THREADS);

            this.changeState(CMSTATE.INITEXPERIMENT);
            final CountDownLatch latch = new CountDownLatch(3); // FIXME: magic number

            Log.i(LOG_TAG, "Connecting...");
            // video
            Runnable vt = new Runnable() {
                @Override
                public void run() {
                    if (video_socket != null) {
                        try {
                            video_socket.close();
                        } catch (IOException e) {
                            Log.e(LOG_TAG, "Exception!", e);
                        }
                    }

                    video_socket = ConnectionManager.prepareSocket(ControlConst.SERVER, config.video_port, SOCKET_TIMEOUT);
                    latch.countDown();
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
                            Log.e(LOG_TAG, "Exception!", e);
                        }
                    }

                    result_socket = ConnectionManager.prepareSocket(ControlConst.SERVER, config.result_port, SOCKET_TIMEOUT);
                    latch.countDown();
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
                            Log.e(LOG_TAG, "Exception!", e);
                        }
                    }

                    control_socket = ConnectionManager.prepareSocket(ControlConst.SERVER, config.control_port, SOCKET_TIMEOUT);
                    latch.countDown();
                }
            };

            this.backend_execs.execute(vt);
            this.backend_execs.execute(rt);
            this.backend_execs.execute(ct);

            try {
                latch.await();
            } catch (InterruptedException e) {
                return;
            }

            Log.i(LOG_TAG, "Connected.");
            // connected, now start streaming
            Log.i(LOG_TAG, "Starting stream.");
            TokenPool tokenPool = new TokenPool();
            this.video_out = new VideoOutputThread(
                    this.video_socket, this.config.num_steps,
                    this.config.fps, this.config.rewind_seconds,
                    this.config.max_replays, this,
                    this.ntpSyncer, tokenPool);
            this.result_in = new ResultInputThread(this.result_socket, this.ntpSyncer, tokenPool);

            this.backend_execs.execute(video_out);
            this.backend_execs.execute(result_in);

            this.changeState(CMSTATE.STREAMING);

        } finally {
            this.state_lock.writeLock().unlock();
        }
    }

    public static ConnectionManager getInstance() throws ConnectionManagerException {
        instance_lock.readLock().lock();
        try {
            if (instance == null)
                throw new ConnectionManagerException(EXCEPTIONSTATE.UNITIALIZED);
            return instance;
        } finally {
            instance_lock.readLock().unlock();
        }
    }

    private void disconnectBackend() throws InterruptedException, IOException {
        this.changeState(CMSTATE.DISCONNECTING);
        Log.i(LOG_TAG, "Disconnecting from CA backend.");
        if (this.video_out != null)
            this.video_out.finish();
        if (this.result_in != null)
            this.result_in.stop();

        if (!backend_execs.awaitTermination(100, TimeUnit.MILLISECONDS))
            backend_execs.shutdownNow();

        this.result_in = null;
        this.video_out = null;

        if (this.video_socket != null)
            video_socket.close();

        if (this.result_socket != null)
            result_socket.close();

        if (control_socket != null)
            control_socket.close();

        Log.i(LOG_TAG, "Disconnected from CA backend.");
    }

    public JSONObject getResults() {
        this.changeState(CMSTATE.UPLOADINGRESULTS);

        // build the json data
        JSONObject payload = new JSONObject();
        this.state_lock.readLock().lock();
        this.stats_lock.readLock().lock();

        try {
            Log.i(LOG_TAG, "Building JSON body");
            // build the json inside a try block

            payload.put(ControlConst.Stats.FIELD_CLIENTID, this.config.client_id);
            payload.put(ControlConst.Stats.FIELD_TASKNAME, this.config.experiment_id);

            // ports, for result analysis
            JSONObject ports = new JSONObject();
            ports.put(ControlConst.EXPPORTS_VIDEO, this.config.video_port);
            ports.put(ControlConst.EXPPORTS_RESULT, this.config.result_port);
            ports.put(ControlConst.EXPPORTS_CONTROL, this.config.control_port);
            payload.put(ControlConst.Stats.FIELD_PORTS, ports);

            //JSONArray run_results = new JSONArray();
            //run_results.put(this.run_stats.toJSON());

            payload.put(ControlConst.Stats.FIELD_RUNRESULTS, this.run_stats.toJSON());
        } catch (JSONException e) {
            Log.e(LOG_TAG, "Exception!", e);
            exit(-1);
        } finally {
            this.state_lock.readLock().unlock();
            this.stats_lock.readLock().unlock();
        }

        return payload;
    }

    public void triggerAppShutDown() {
        this.state_lock.writeLock().lock();
        try {
            MainActivity mAct = this.mAct.get();
            if (mAct != null)
                // mAct.finishAndRemoveTask();
                mAct.stateFinished(this.run_count);
            else
                System.exit(0);
        } finally {
            this.state_lock.writeLock().unlock();
        }
    }

    public void forceShutDown() {
        this.state_lock.writeLock().lock();
        try {
            Log.w(LOG_TAG, "Forcing shut down now!");

            this.disconnectBackend();
            this.changeState(CMSTATE.DISCONNECTED);

            if (this.ntpSyncer != null)
                this.ntpSyncer.close();

            if (this.controlClient != null)
                this.controlClient.close();

            //this.controlClient.close();
        } catch (InterruptedException ignored) {
        } catch (Exception e) {
            Log.e(LOG_TAG, "Exception!", e);
            exit(-1);
        } finally {
            this.state_lock.writeLock().unlock();
        }
    }

    public void notifyEndStream(boolean task_completed) {

        this.stats_lock.writeLock().lock();
        try {
            this.run_stats.finish();
            this.run_stats.setSuccess(task_completed);
        } finally {
            this.stats_lock.writeLock().unlock();
        }

        Log.i(LOG_TAG, "Stream ends");
        this.changeState(CMSTATE.STREAMING_DONE);

        try {
            this.disconnectBackend();
        } catch (InterruptedException e) {
            Log.e(LOG_TAG, "Exception!", e);
            return;
        } catch (IOException e) {
            Log.e(LOG_TAG, "Exception!", e);
            exit(-1);
        }
        this.state_lock.writeLock().lock();
        try {
            //if (this.controlClient != null)
            //    this.controlClient.close();
            //this.controlClient = new ControlClient(this.app_context, this);
            this.controlClient.notifyExperimentFinish();
        } finally {
            this.state_lock.writeLock().unlock();
        }

    }

    public CMSTATE getState() {
        this.state_lock.readLock().lock();
        try {
            return this.state;
        } finally {
            this.state_lock.readLock().unlock();
        }
    }

    public VideoFrame getLastFrame() {
        this.stats_lock.readLock().lock();
        try {
            return this.last_sent_frame;
        } finally {
            this.stats_lock.readLock().unlock();
        }
    }

    public void notifySuccessForFrame(VideoFrame frame, int step_index) {
        Log.i(LOG_TAG, "Got success message for frame " + frame.getId());
        this.registerStats(frame, true);
        try {
            if (step_index != this.video_out.getCurrentStepIndex()) {
                this.stats_lock.writeLock().lock();
                try {
                    this.current_error_count = 0; // reset error count if we change step
                } finally {
                    this.stats_lock.writeLock().unlock();
                }
            }

            this.video_out.goToStep(step_index);
        } catch (VideoOutputThread.VideoOutputThreadException e) {
            Log.e(LOG_TAG, "Exception!", e);
            exit(-1);
        }
    }

    private void registerStats(VideoFrame in_frame, boolean feedback) {
        // TODO maybe differentiate between different types of feedback (success/error?)

        this.stats_lock.writeLock().lock();
        try {
            if (in_frame.getId() == this.last_sent_frame.getId()) {
                this.run_stats.registerFrame(in_frame.getId(),
                        this.last_sent_frame.getTimestamp(),
                        in_frame.getTimestamp(), feedback);
            }
        } finally {
            this.stats_lock.writeLock().unlock();
        }
    }

    public void notifyMistakeForFrame(VideoFrame frame) {
        Log.i(LOG_TAG, "Got error message for frame " + frame.getId());
        this.stats_lock.writeLock().lock();
        try {
            this.current_error_count++;
            Log.i(LOG_TAG, "Current error count: " + this.current_error_count);
        } finally {
            this.stats_lock.writeLock().unlock();
        }
        registerStats(frame, true);
    }

    public void notifyNoResultForFrame(VideoFrame frame) {
        registerStats(frame, false);
    }

    public void notifySentFrame(VideoFrame frame) {
        this.stats_lock.writeLock().lock();
        try {
            this.last_sent_frame = frame;
            MainActivity act = this.mAct.get();
            if (act != null)
                act.pushSentFrameAndStatsToPreview(frame, this.run_stats.getRollingRTT());

        } finally {
            this.stats_lock.writeLock().unlock();
        }
    }

    public void notifyPushFrame(byte[] raw_frame) {
        this.stats_lock.readLock().lock();
        try {
            MainActivity act = this.mAct.get();
            if (act != null)
                act.pushNewFrameToPreview(raw_frame);
        } finally {
            stats_lock.readLock().unlock();
        }
    }

    public void notifyStepError(int step, String error) {
        this.stats_lock.writeLock().lock();
        try {
            this.run_stats.finish();
            this.run_stats.setSuccess(false);
        } finally {
            this.stats_lock.writeLock().unlock();
        }

        Log.e(LOG_TAG, "Stream ends due to error!");
        this.state_lock.writeLock().lock();
        try {
            MainActivity mAct = this.mAct.get();
            if (mAct != null)
                mAct.stateStepError(step, error);
            else
                System.exit(0);
        } finally {
            this.state_lock.writeLock().unlock();
        }
    }

    public Context getAppContext() {
        return app_context;
    }

    public enum EXCEPTIONSTATE {
        CONTROLERROR,
        UNITIALIZED,
        ERRORCREATINGTRACE,
        ALREADYCONNECTED,
        NOTCONNECTED,
        ALREADYSTREAMING,
        NOTRACE,
        NOADDRESS,
        INVALIDTRACEDIR,
        TASKNOTCOMPLETED,
        NOCONFIG,
        INVALIDSTATE,
        NTPNOTSYNCED
    }

    public enum CMSTATE {
        WAITINGFORCONTROL,
        LISTENINGCONTROL,
        CONFIGURING,
        FETCHINGTRACE,
        NTPSYNC,
        INITEXPERIMENT,
        STREAMING,
        STREAMING_DONE,
        DISCONNECTING,
        UPLOADINGRESULTS,
        DISCONNECTED
    }

    public static class ConnectionManagerException extends Exception {

        private String CMExceptMsg;

        ConnectionManagerException(EXCEPTIONSTATE state) {
            super("ConnectionManager Exception");
            switch (state) {
                case UNITIALIZED:
                    this.CMExceptMsg = "ConnectionManager has not been initialized!";
                    break;
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
                case TASKNOTCOMPLETED:
                    this.CMExceptMsg = "Received unexpected task finish message!";
                    break;
                case INVALIDTRACEDIR:
                    this.CMExceptMsg = "Provided trace directory is not valid!";
                    break;
                case NOCONFIG:
                    this.CMExceptMsg = "No configuration for the experiment!";
                    break;
                case INVALIDSTATE:
                    this.CMExceptMsg = "Invalid ConnectionManager state!";
                    break;
                case ERRORCREATINGTRACE:
                    this.CMExceptMsg = "Error when downloading trace!";
                    break;
                case CONTROLERROR:
                    this.CMExceptMsg = "Control Server error!";
                    break;
                case NTPNOTSYNCED:
                    this.CMExceptMsg = "NTP client not initialized!";
                    break;
                default:
                    this.CMExceptMsg = "";
                    break;
            }
        }

        @Override
        public String getMessage() {
            return super.getMessage() + ": " + this.CMExceptMsg;
        }
    }
}
