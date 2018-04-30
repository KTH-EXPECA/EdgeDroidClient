package se.kth.molguin.tracedemo.network.gabriel;

import android.annotation.SuppressLint;
import android.content.Context;
import android.util.Log;

import com.android.volley.Request;
import com.android.volley.RequestQueue;
import com.android.volley.Response;
import com.android.volley.VolleyError;
import com.android.volley.toolbox.Volley;
import com.instacart.library.truetime.TrueTime;
import com.instacart.library.truetime.TrueTimeRx;

import org.apache.commons.math3.stat.descriptive.DescriptiveStatistics;
import org.apache.commons.math3.stat.descriptive.SummaryStatistics;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.lang.ref.WeakReference;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.util.Calendar;
import java.util.Date;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import io.reactivex.functions.Action;
import io.reactivex.functions.Consumer;
import io.reactivex.schedulers.Schedulers;
import se.kth.molguin.tracedemo.Constants;
import se.kth.molguin.tracedemo.MainActivity;
import se.kth.molguin.tracedemo.StatBackendConstants;
import se.kth.molguin.tracedemo.network.InputStreamVolleyRequest;
import se.kth.molguin.tracedemo.network.ResultInputThread;
import se.kth.molguin.tracedemo.network.VideoFrame;
import se.kth.molguin.tracedemo.network.VideoOutputThread;

import static java.lang.System.exit;

public class ConnectionManager {

    private static final int THREADS = 4;
    private static final int STAT_WINDOW_SZ = 15;
    private static final int SOCKET_TIMEOUT = 250;

    private static final String LOG_TAG = "ConnectionManager";

    private static final Object lock = new Object();
    private static final Object last_frame_lock = new Object();
    private static final Object stat_lock = new Object();
    private static final Object stream_lock = new Object();

    private static ConnectionManager instance = null;
    // Statistics
    private DescriptiveStatistics[] rolling_rtt_stats;
    private SummaryStatistics[] total_rtt_stats;
    //private DataInputStream video_trace;
    private Socket video_socket;
    private Socket result_socket;
    private Socket exp_control_socket;
    /* TODO: Audio and other sensors?
    private Socket audio_socket;
    private Socket acc_socket;
     */
    private Socket control_socket;
    //private String addr;
    private ExecutorService backend_execs;
    private ExecutorService main_exec;
    private TokenManager tkn;
    private VideoOutputThread video_out;
    private ResultInputThread result_in;
    private CMSTATE state;
    //private boolean got_new_frame;
    private int current_error_count;
    private VideoFrame last_sent_frame;

    private Context app_context;
    private WeakReference<MainActivity> mAct;
    private boolean time_synced;

    private ExperimentConfig config;

    private Date[] task_inits;
    private Date[] task_ends;
    private boolean[] task_status;

    private int run_index;

    private ConnectionManager() {

        synchronized (lock) {
            this.state = CMSTATE.WARMUP;
        }

        this.backend_execs = Executors.newFixedThreadPool(THREADS);
        this.main_exec = Executors.newSingleThreadExecutor();
        this.config = null;
        //this.addr = null;
        this.video_socket = null;
        this.result_socket = null;
        this.control_socket = null;
        this.exp_control_socket = null;
        this.tkn = TokenManager.getInstance();
        this.video_out = null;
        this.result_in = null;
        this.app_context = null;
        this.mAct = null;
        this.last_sent_frame = null;
        this.current_error_count = 0;
        this.time_synced = false;
        this.total_rtt_stats = null;
        this.rolling_rtt_stats = null;

        this.task_inits = null;
        this.task_ends = null;
        this.task_status = null;
        this.run_index = -1;

        Runnable experiment_run = new Runnable() {
            @Override
            public void run() {
                ConnectionManager.this.executeExperiment();
            }
        };
        this.main_exec.execute(experiment_run);
    }

    public static void shutdownAndDelete() {
        synchronized (lock) {
            if (instance != null) {
                instance.forceShutDown();
                instance = null;
            }
        }
    }

    public static ConnectionManager reset(MainActivity act) {
        synchronized (lock) {
            shutdownAndDelete();
            return init(act);
        }
    }

    public void pushStateToActivity() {
        synchronized (lock) {
            MainActivity mAct = this.mAct.get();
            if (mAct != null) {
                switch (this.state) {
                    case WARMUP:
                        break;
                    case WAITINGFORCONTROL:
                        mAct.stateConnectingControl();
                        break;
                    case CONFIGURING:
                        mAct.stateConfig();
                        break;
                    case FETCHINGTRACE:
                        mAct.stateFetchTraces();
                        break;
                    case WAITFOREXPERIMENTSTART:
                        mAct.stateWaitForStart();
                        break;
                    case NTPSYNC:
                        mAct.stateNTPSync();
                        break;
                    case CONNECTING:
                        mAct.stateConnecting();
                        break;
                    case CONNECTED:
                        mAct.stateConnected();
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
                    case WAITINGFORUPLOAD:
                        mAct.stateConnectingControl();
                        break;
                    case UPLOADINGRESULTS:
                        mAct.stateUploading();
                        break;
                    case DISCONNECTED:
                        mAct.stateDisconnected();
                        break;
                }
            }
        }
    }

    private void changeState(CMSTATE new_state) {
        synchronized (lock) {
            this.state = new_state;
            this.pushStateToActivity();
        }
    }

    private void connectToControl() {
        this.changeState(CMSTATE.WAITINGFORCONTROL);

        Log.i(LOG_TAG, "Connecting to experiment control...");
        this.exp_control_socket = prepareSocket(StatBackendConstants.EXP_CONTROL_ADDRESS,
                StatBackendConstants.EXP_CONTROL_PORT,
                SOCKET_TIMEOUT);
        Log.i(LOG_TAG, "Connected.");
    }

    private void executeExperiment() {
        try {
            // Execute experiment in order
            this.connectToControl();
            this.getRemoteExperimentConfig();
            this.prepareTraces();
            this.initConnections();
            this.notifyControl();
            this.waitForExperimentStart();

            try {
                this.exp_control_socket.close();
                this.exp_control_socket = null;
            } catch (IOException e) {
                e.printStackTrace();
                exit(-1);
            }

            int runs;
            synchronized (lock) {
                if (this.config == null)
                    throw new ConnectionManagerException(EXCEPTIONSTATE.NOCONFIG);
                runs = config.runs;
            }

            for (int i = 0; i < runs; i++) {
                Log.i(LOG_TAG, String.format("Executing run %d of %d", i + 1, runs));

                synchronized (lock) {
                    this.run_index = i;
                    MainActivity mAct = this.mAct.get();
                    if (mAct != null)
                        mAct.updateRunStatus(i + 1, runs);
                }

                if (i != 0)
                    this.initConnections();

                synchronized (stream_lock) {
                    this.startStreaming();
                    while (this.state == CMSTATE.STREAMING)
                        stream_lock.wait();
                }
                // done streaming, disconnect!
                this.disconnectBackend();
            }

            // reconnect to Control to upload stats
            this.connectToControl();
            // wait for control to request stats and send them
            this.uploadResults();

            // disconnect
            try {
                this.exp_control_socket.close();
                this.exp_control_socket = null;
            } catch (IOException e) {
                e.printStackTrace();
                exit(-1);
            }

            this.changeState(CMSTATE.DISCONNECTED);

        } catch (ConnectionManagerException | IOException e) {
            e.printStackTrace();
            exit(-1);
        } catch (InterruptedException ignored) {
        }
    }

    private void getRemoteExperimentConfig() throws ConnectionManagerException {
        if (this.exp_control_socket == null)
            throw new ConnectionManagerException(EXCEPTIONSTATE.NOTCONNECTED);

        this.changeState(CMSTATE.CONFIGURING);

        Log.i(LOG_TAG, "Fetching experiment configuration...");

        try {
            DataInputStream in_data = new DataInputStream(this.exp_control_socket.getInputStream());
            int config_len = in_data.readInt();
            byte[] config_b = new byte[config_len];

            int readSize = 0;
            while (readSize < config_len) {
                int ret = in_data.read(config_b, readSize, config_len - readSize);
                if (ret <= 0) {
                    throw new IOException();
                }
                readSize += ret;
            }

            JSONObject config = new JSONObject(new String(config_b, "UTF-8"));
            synchronized (lock) {
                this.config = new ExperimentConfig(config);
                this.task_status = new boolean[this.config.runs];
                this.task_inits = new Date[this.config.runs];
                this.task_ends = new Date[this.config.runs];
                this.total_rtt_stats = new SummaryStatistics[this.config.runs];
                this.rolling_rtt_stats = new DescriptiveStatistics[this.config.runs];
            }
        } catch (IOException | JSONException e) {
            Log.e(LOG_TAG, "Error when fetching config from control server.");
            e.printStackTrace();
            exit(-1);
        }
    }

    private void prepareTraces() throws ConnectionManagerException {
        this.changeState(CMSTATE.FETCHINGTRACE);

        String trace_url;
        int steps;
        synchronized (lock) {
            if (this.config == null) throw new ConnectionManagerException(EXCEPTIONSTATE.NOCONFIG);
            trace_url = this.config.trace_url;
            steps = this.config.steps;
        }

        final File appDir = this.app_context.getFilesDir();
        for (File f : appDir.listFiles())
            if (!f.isDirectory())
                f.delete();

        final CountDownLatch latch = new CountDownLatch(steps);
        RequestQueue requestQueue;
        synchronized (lock) {
            requestQueue = Volley.newRequestQueue(this.app_context);
        }

        for (int i = 0; i < steps; i++) {
            // fetch all the steps using Volley

            final String stepFilename = Constants.STEP_PREFIX + (i + 1) + Constants.STEP_SUFFIX;
            final String stepUrl = trace_url + stepFilename;
            final int index = i;

            InputStreamVolleyRequest req =
                    new InputStreamVolleyRequest(Request.Method.GET, stepUrl,
                            new Response.Listener<byte[]>() {
                                @Override
                                public void onResponse(byte[] response) {
                                    try {
                                        if (response != null) {
                                            Log.i(LOG_TAG, "Got trace " + stepFilename);
                                            FileOutputStream file_out = ConnectionManager.this.app_context.openFileOutput(stepFilename, Context.MODE_PRIVATE);
                                            file_out.write(response);
                                            file_out.close();
                                            latch.countDown();

                                            // TODO: notify UI
                                        }
                                    } catch (IOException e) {
                                        e.printStackTrace();
                                        exit(-1);
                                    }
                                }
                            },
                            new Response.ErrorListener() {
                                @Override
                                public void onErrorResponse(VolleyError error) {
                                    Log.e(LOG_TAG, "Could not fetch " + stepUrl);
                                    error.printStackTrace();
                                    // TODO: Notify on UI
                                    exit(-1); // for now
                                }
                            }, null);

            requestQueue.add(req);
        }

        try {
            latch.await();
        } catch (InterruptedException e) {
            e.printStackTrace();
        } finally {
            requestQueue.stop();
        }
    }

    private void waitForExperimentStart() throws ConnectionManagerException {
        if (this.exp_control_socket == null)
            throw new ConnectionManagerException(EXCEPTIONSTATE.NOTCONNECTED);

        this.changeState(CMSTATE.WAITFOREXPERIMENTSTART);

        try {
            DataInputStream in_data = new DataInputStream(this.exp_control_socket.getInputStream());
            if (in_data.readInt() != StatBackendConstants.EXP_CONTROL_SUCCESS)
                throw new ConnectionManagerException(EXCEPTIONSTATE.CONTROLERROR);
        } catch (IOException e) {
            e.printStackTrace();
            exit(-1);
        }
    }

    private void notifyControl() throws ConnectionManagerException {
        // notify control
        if (this.exp_control_socket == null)
            throw new ConnectionManagerException(EXCEPTIONSTATE.NOTCONNECTED);

        try {
            DataOutputStream outputStream = new DataOutputStream(this.exp_control_socket.getOutputStream());
            outputStream.writeInt(StatBackendConstants.EXP_CONTROL_SUCCESS);
            outputStream.flush();
        } catch (IOException e) {
            e.printStackTrace();
            exit(-1);
        }
    }

    private static Socket prepareSocket(String addr, int port, int timeout_ms) {
        Socket socket = new Socket();
        boolean connected = false;

        while (!connected) {
            try {
                socket.setTcpNoDelay(true);
                socket.connect(new InetSocketAddress(addr, port), timeout_ms);
                connected = true;
            } catch (SocketTimeoutException ignored) {
            } catch (IOException e) {
                e.printStackTrace();
                exit(-1);
            }
        }
        return socket;
    }

    private void initConnections() throws ConnectionManagerException {
        synchronized (lock) {
            if (this.config == null) throw new ConnectionManagerException(EXCEPTIONSTATE.NOCONFIG);
        }

        this.synchronizeTime();

        this.changeState(CMSTATE.CONNECTING);
        final CountDownLatch latch = new CountDownLatch(3); // TODO: Fix magic number

        Log.i(LOG_TAG, "Connecting...");
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

                video_socket = ConnectionManager.prepareSocket(ProtocolConst.SERVER, config.video_port, SOCKET_TIMEOUT);
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
                        e.printStackTrace();
                    }
                }

                result_socket = ConnectionManager.prepareSocket(ProtocolConst.SERVER, config.result_port, SOCKET_TIMEOUT);
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
                        e.printStackTrace();
                    }
                }

                control_socket = ConnectionManager.prepareSocket(ProtocolConst.SERVER, config.control_port, SOCKET_TIMEOUT);
                latch.countDown();
            }
        };

        backend_execs.execute(vt);
        backend_execs.execute(rt);
        backend_execs.execute(ct);

        try {
            latch.await();
        } catch (InterruptedException e) {
            e.printStackTrace();
            exit(-1);
        }

        Log.i(LOG_TAG, "Connected.");

        this.changeState(CMSTATE.CONNECTED);
    }

    public static ConnectionManager init(MainActivity act) {
        synchronized (lock) {
            if (instance != null) {
                instance.mAct = new WeakReference<>(act);
                return instance;
            }

            instance = new ConnectionManager();
            instance.app_context = act.getApplicationContext();
            instance.mAct = new WeakReference<>(act);
            return instance;
        }
    }

    private void startStreaming() throws IOException {
        Log.i(LOG_TAG, "Starting stream.");
        this.video_out = new VideoOutputThread(video_socket, this.config.steps, this.app_context);
        this.result_in = new ResultInputThread(result_socket, tkn);

        synchronized (lock) {
            this.task_inits[run_index] = TrueTime.now();
            this.total_rtt_stats[run_index] = new SummaryStatistics();
            this.rolling_rtt_stats[run_index] = new DescriptiveStatistics(STAT_WINDOW_SZ);
        }

        backend_execs.execute(video_out);
        backend_execs.execute(result_in);

        this.changeState(CMSTATE.STREAMING);
    }

    public static ConnectionManager getInstance() throws ConnectionManagerException {
        synchronized (lock) {
            if (instance == null) {
                //instance = new ConnectionManager();
                throw new ConnectionManagerException(EXCEPTIONSTATE.UNITIALIZED);
            }
            return instance;
        }
    }

    private void disconnectBackend() throws InterruptedException, IOException {
        this.changeState(CMSTATE.DISCONNECTING);
        Log.i(LOG_TAG, "Disconnecting from CA backend.");
        if (this.video_out != null)
            this.video_out.finish();
        if (this.result_in != null)
            this.result_in.stop();

        backend_execs.awaitTermination(100, TimeUnit.MILLISECONDS);

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

    private void uploadResults() throws ConnectionManagerException {
        ExperimentConfig config;
        synchronized (lock) {
            if (this.app_context == null)
                throw new ConnectionManagerException(EXCEPTIONSTATE.NOCONTEXT);

            if (this.control_socket == null)
                throw new ConnectionManagerException(EXCEPTIONSTATE.NOTCONNECTED);
            config = this.config;
            this.state = CMSTATE.UPLOADINGRESULTS;
        }

        Log.i(LOG_TAG, "Waiting to upload results to Control server.");
        // build the json data
        JSONObject payload = new JSONObject();
        try {
            Log.i(LOG_TAG, "Building JSON body");
            // build the json inside a try block

            payload.put(StatBackendConstants.FIELD_CLIENTID, config.client_id);
            payload.put(StatBackendConstants.FIELD_TASKNAME, config.experiment_id);

            Calendar c = Calendar.getInstance();

            int runs;
            synchronized (lock) {
                runs = this.config.runs;
            }

            JSONArray start_timestamps = new JSONArray();
            JSONArray end_timestamps = new JSONArray();
            JSONArray task_success = new JSONArray();

            for (int i = 0; i < runs; i++) {
                c.setTime(this.task_inits[i]);
                start_timestamps.put(c.getTimeInMillis());

                c.setTime(this.task_ends[i]);
                end_timestamps.put(c.getTimeInMillis());

                task_success.put(this.task_status[i]);
            }


            payload.put(StatBackendConstants.FIELD_TASKBEGIN, start_timestamps);
            payload.put(StatBackendConstants.FIELD_TASKEND, end_timestamps);

            payload.put(StatBackendConstants.FIELD_TASKSUCCESS, task_success);
            JSONArray frames = new JSONArray(); // empty for now TODO: SEND FRAMES
            payload.put(StatBackendConstants.FIELD_FRAMELIST, frames);
        } catch (JSONException e) {
            e.printStackTrace();
            exit(-1);
        }

        try {

            DataInputStream socket_in = new DataInputStream(this.exp_control_socket.getInputStream());
            while (socket_in.readInt() != StatBackendConstants.EXP_CONTROL_GETSTATS) continue;

            Log.i(LOG_TAG, "Sending JSON data...");
            byte[] payload_b = payload.toString().getBytes("UTF-8");
            DataOutputStream outStream = new DataOutputStream(this.exp_control_socket.getOutputStream());
            outStream.writeInt(payload_b.length);
            outStream.write(payload_b);
        } catch (IOException e) {
            e.printStackTrace();
            exit(-1);
        }
        Log.i(LOG_TAG, "Sent stats to Control.");
    }

    private void forceShutDown() {
        try {
            Log.w(LOG_TAG, "Forcing shut down now!");
            this.main_exec.shutdownNow();

            if (instance.exp_control_socket != null) {
                instance.exp_control_socket.close();
                instance.exp_control_socket = null;

                this.disconnectBackend();
                this.changeState(CMSTATE.DISCONNECTED);
            }
        } catch (InterruptedException ignored) {
        } catch (IOException e) {
            e.printStackTrace();
            exit(-1);
        }
    }

    @SuppressLint("CheckResult")
    private void synchronizeTime() throws ConnectionManagerException {
        synchronized (lock) {
            //if (this.addr == null)
            //    throw new ConnectionManagerException(EXCEPTIONSTATE.NOADDRESS);
            if (this.app_context == null)
                throw new ConnectionManagerException(EXCEPTIONSTATE.NOCONTEXT);
            this.changeState(CMSTATE.NTPSYNC);
        }

        Log.i(LOG_TAG, "Synchronizing time with " + ProtocolConst.SERVER);
        final Object time_lock = new Object();
        TrueTimeRx.build()
                .withRootDispersionMax(Constants.MAX_NTP_DISPERSION)
                .withLoggingEnabled(true) // this doesn't bother us since it runs before the actual streaming
                .initializeRx(ProtocolConst.SERVER)
                .subscribeOn(Schedulers.io())
                .subscribe(
                        new Consumer<Date>() {
                            @Override
                            public void accept(Date date) {
                                Log.i(ConnectionManager.LOG_TAG, "Got date: " + date.toString());
                            }
                        },
                        new Consumer<Throwable>() {
                            @Override
                            public void accept(Throwable throwable) {
                                Log.e(ConnectionManager.LOG_TAG, "Could not initialize NTP!");
                                exit(-1);
                            }
                        },
                        new Action() {
                            @Override
                            public void run() {
                                Log.i(ConnectionManager.LOG_TAG, "Synchronized time with server!");
                                synchronized (ConnectionManager.lock) {
                                    ConnectionManager.this.time_synced = true;
                                    synchronized (time_lock) {
                                        time_lock.notifyAll();
                                    }
                                }
                            }
                        });


        synchronized (time_lock) {
            while (!this.time_synced) {
                try {
                    time_lock.wait();
                } catch (InterruptedException ignored) {
                }
            }
        }

    }

    public void notifyEndStream(boolean task_completed) {
        synchronized (stream_lock) {

            synchronized (lock) {
                this.task_ends[this.run_index] = TrueTime.now();
                this.task_status[this.run_index] = task_completed;
            }

            Log.i(LOG_TAG, "Stream ends");
            this.changeState(CMSTATE.STREAMING_DONE);
            stream_lock.notifyAll();
        }

    }

    public CMSTATE getState() {
        synchronized (lock) {
            return this.state;
        }
    }

    public VideoFrame getLastFrame() {
        synchronized (last_frame_lock) {
            return this.last_sent_frame;
        }
    }

    public void notifySuccessForFrame(VideoFrame frame, int step_index) {
        Log.i(LOG_TAG, "Got success message for frame " + frame.getId());
        this.registerStats(frame);
        try {
            if (step_index != this.video_out.getCurrentStepIndex())
                synchronized (stat_lock) {
                    this.current_error_count = 0; // reset error count if we change step
                }

            this.video_out.goToStep(step_index);
        } catch (VideoOutputThread.VideoOutputThreadException e) {
            e.printStackTrace();
            exit(-1);
        }
    }

    private void registerStats(VideoFrame in_frame) {
        int run;
        synchronized (lock) {
            run = this.run_index;
        }

        synchronized (stat_lock) {
            if (in_frame.getId() == this.last_sent_frame.getId()) {
                long rtt = in_frame.getTimestamp() - this.last_sent_frame.getTimestamp();
                this.rolling_rtt_stats[run].addValue(rtt);
                this.total_rtt_stats[run].addValue(rtt);
            }
        }
    }

    public void notifyMistakeForFrame(VideoFrame frame) {
        // TODO: maybe keep count of errors?
        Log.i(LOG_TAG, "Got error message for frame " + frame.getId());
        synchronized (stat_lock) {
            this.current_error_count++;
            Log.i(LOG_TAG, "Current error count: " + this.current_error_count);
        }
        registerStats(frame);
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
        int run;
        synchronized (lock) {
            run = run_index;
        }
        synchronized (stat_lock) {
            return rolling_rtt_stats[run].getMean();
        }
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
        NOCONTEXT,
        NOCONFIG,
        INVALIDSTATE
    }

    public enum CMSTATE {
        WARMUP,
        WAITINGFORCONTROL,
        CONFIGURING,
        FETCHINGTRACE,
        WAITFOREXPERIMENTSTART,
        NTPSYNC,
        CONNECTING,
        CONNECTED,
        STREAMING,
        STREAMING_DONE,
        DISCONNECTING,
        WAITINGFORUPLOAD,
        UPLOADINGRESULTS,
        DISCONNECTED
    }

    public static class ExperimentConfig {
        String experiment_id;
        int client_id;
        int runs;
        int steps;
        String trace_url;

        int video_port;
        int control_port;
        int result_port;

        public ExperimentConfig(JSONObject json) throws JSONException {
            this.experiment_id = json.getString(Constants.EXPCONFIG_ID);
            this.client_id = json.getInt(Constants.EXPCONFIG_CLIENTIDX);
            this.runs = json.getInt(Constants.EXPCONFIG_RUNS);
            this.steps = json.getInt(Constants.EXPCONFIG_STEPS);
            this.trace_url = json.getString(Constants.EXPCONFIG_TRACE);

            JSONObject ports = json.getJSONObject(Constants.EXPCONFIG_PORTS);
            this.video_port = ports.getInt(Constants.EXPPORTS_VIDEO);
            this.control_port = ports.getInt(Constants.EXPPORTS_CONTROL);
            this.result_port = ports.getInt(Constants.EXPPORTS_RESULT);
        }

        public JSONObject toJSON() throws JSONException {
            JSONObject ports = new JSONObject();
            ports.put(Constants.EXPPORTS_VIDEO, this.video_port);
            ports.put(Constants.EXPPORTS_CONTROL, this.control_port);
            ports.put(Constants.EXPPORTS_RESULT, this.result_port);

            JSONObject config = new JSONObject();
            config.put(Constants.EXPCONFIG_ID, this.experiment_id);
            config.put(Constants.EXPCONFIG_CLIENTIDX, this.client_id);
            config.put(Constants.EXPCONFIG_RUNS, this.runs);
            config.put(Constants.EXPCONFIG_STEPS, this.steps);
            config.put(Constants.EXPCONFIG_TRACE, this.trace_url);
            config.put(Constants.EXPCONFIG_PORTS, ports);

            return config;
        }
    }

    public static class ConnectionManagerException extends Exception {

        private EXCEPTIONSTATE state;
        private String CMExceptMsg;

        ConnectionManagerException(EXCEPTIONSTATE state) {
            super("ConnectionManager Exception");
            this.state = state;
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
                case NOCONTEXT:
                    this.CMExceptMsg = "No application Context provided!";
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
