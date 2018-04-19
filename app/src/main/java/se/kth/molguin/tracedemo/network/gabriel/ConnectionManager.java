package se.kth.molguin.tracedemo.network.gabriel;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.SharedPreferences;
import android.net.Uri;
import android.util.Log;

import com.android.volley.RequestQueue;
import com.android.volley.Response;
import com.android.volley.VolleyError;
import com.android.volley.toolbox.JsonObjectRequest;
import com.android.volley.toolbox.Volley;
import com.instacart.library.truetime.TrueTime;
import com.instacart.library.truetime.TrueTimeRx;

import org.apache.commons.math3.stat.descriptive.DescriptiveStatistics;
import org.apache.commons.math3.stat.descriptive.SummaryStatistics;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
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
import se.kth.molguin.tracedemo.StatBackendConstants;
import se.kth.molguin.tracedemo.network.ResultInputThread;
import se.kth.molguin.tracedemo.network.VideoFrame;
import se.kth.molguin.tracedemo.network.VideoOutputThread;

import static java.lang.System.exit;

public class ConnectionManager {

    private static final int THREADS = 4;
    private static final int STAT_WINDOW_SZ = 15;
    private static final String LOG_TAG = "ConnectionManager";

    private static final Object lock = new Object();
    private static final Object last_frame_lock = new Object();
    private static final Object stat_lock = new Object();

    private static ConnectionManager instance = null;
    // Statistics
    private DescriptiveStatistics rolling_rtt_stats;
    private SummaryStatistics total_rtt_stats;
    //private DataInputStream video_trace;
    private Uri[] step_traces;
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

    private Context app_context;
    private boolean force_ntp_sync;
    private boolean time_synced;

    private Date task_start;
    private Date task_end;
    private boolean task_success;

    // it sometimes happens that the backend jumps back and fro between error and correct
    // states. After a number of bounces we should just give up.
    // reset this value at each state transition
    //private int error_bounces;

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

        this.app_context = null;

        this.changeStateAndNotify(CMSTATE.DISCONNECTED);
        this.execs = Executors.newFixedThreadPool(THREADS);

        this.last_sent_frame = null;
        //this.got_new_frame = false;

        this.current_error_count = 0;
        this.force_ntp_sync = false;
        this.time_synced = false;
        //this.error_bounces = 0;

        this.task_start = null;
        this.task_end = null;
        this.task_success = false;

        this.total_rtt_stats = new SummaryStatistics();
        this.rolling_rtt_stats = new DescriptiveStatistics(STAT_WINDOW_SZ);
    }


    public void initConnections() throws ConnectionManagerException {
        synchronized (lock) {
            if (this.addr == null) throw new ConnectionManagerException(EXCEPTIONSTATE.NOADDRESS);
            if (this.state != CMSTATE.DISCONNECTED)
                throw new ConnectionManagerException(EXCEPTIONSTATE.ALREADYCONNECTED);
        }

        this.synchronizeTime();
        synchronized (lock) {
            while (!this.time_synced) {
                try {
                    lock.wait();
                } catch (InterruptedException e) {
                    e.printStackTrace();
                    return;
                }
            }
        }


        this.changeStateAndNotify(CMSTATE.CONNECTING);
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

        Log.i(LOG_TAG, "Connected.");
        this.changeStateAndNotify(CMSTATE.CONNECTED);
    }


    public Context getContext() throws ConnectionManagerException {
        if (this.app_context == null)
            throw new ConnectionManagerException(EXCEPTIONSTATE.NOCONTEXT);

        return this.app_context;
    }

    public void setContext(Context context) {
        this.app_context = context;
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
                instance.uploadResultsAndShutDown();
                instance = null;
            }
        }
    }

    public void uploadResultsAndShutDown() throws InterruptedException, IOException {

        Log.i(LOG_TAG, "Shutting down.");
        synchronized (lock) {
            switch (this.state) {
                case DISCONNECTED:
                case DISCONNECTING:
                case UPLOADINGRESULTS:
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

        try {
            this.uploadResults();
        } catch (ConnectionManagerException e) {
            e.printStackTrace();
            exit(-1);
        }
    }

    private void uploadResults() throws ConnectionManagerException {
        Context context;
        synchronized (lock) {
            if (this.app_context == null)
                throw new ConnectionManagerException(EXCEPTIONSTATE.NOCONTEXT);
            context = this.app_context;
            this.changeStateAndNotify(CMSTATE.UPLOADINGRESULTS);
        }

        Log.i(LOG_TAG, "Experiment done (or cancelled), uploading results.");

        final SharedPreferences preferences = context
                .getSharedPreferences(Constants.PREFS_NAME, Context.MODE_PRIVATE);
        int client_id = preferences.getInt(Constants.PREFS_CLIENTID, -1);

        // build the json data
        JSONObject payload = new JSONObject();
        try {
            Log.i(LOG_TAG, "Building JSON body");
            // build the json inside a try block
            if (client_id != -1)
                payload.put(StatBackendConstants.FIELD_CLIENTID, client_id);

            payload.put(StatBackendConstants.FIELD_TASKNAME, Constants.TASKNAME);

            Calendar c = Calendar.getInstance();

            c.setTime(this.task_start);
            long task_start_timestamp = c.getTimeInMillis();

            c.setTime(this.task_end);
            long task_end_timestamp = c.getTimeInMillis();

            payload.put(StatBackendConstants.FIELD_TASKBEGIN, task_start_timestamp);
            payload.put(StatBackendConstants.FIELD_TASKEND, task_end_timestamp);

            payload.put(StatBackendConstants.FIELD_TASKSUCCESS, this.task_success);
            JSONArray frames = new JSONArray(); // empty for now
            payload.put(StatBackendConstants.FIELD_FRAMELIST, frames);
        } catch (JSONException e) {
            e.printStackTrace();
            exit(-1);
        }

        Log.i(LOG_TAG, "Sending JSON data...");
        RequestQueue q = Volley.newRequestQueue(context);
        String statUrl =
                "http://" +
                        this.addr + ":"
                        + StatBackendConstants.STATSERVERPORT
                        + StatBackendConstants.STATSERVERENDPOINT;

        JsonObjectRequest req = new JsonObjectRequest(statUrl, payload, new Response.Listener<JSONObject>() {
            @Override
            public void onResponse(JSONObject response) {
                try {
                    int new_client_id = response.getInt(StatBackendConstants.FIELD_CLIENTID);
                    preferences.edit().putInt(Constants.PREFS_CLIENTID, new_client_id).apply();
                    ConnectionManager.getInstance().changeStateAndNotify(CMSTATE.DISCONNECTED);
                    Log.i(LOG_TAG, "Shut down.");
                } catch (JSONException e) {
                    Log.e(LOG_TAG, "Malformed response from StatServer?");
                }
            }
        }, new Response.ErrorListener() {
            @Override
            public void onErrorResponse(VolleyError error) {
                Log.i(LOG_TAG, "Error when sending data!");
                ConnectionManager.getInstance().changeStateAndNotify(CMSTATE.DISCONNECTED);
                Log.i(LOG_TAG, "Shut down.");
            }
        });
        q.add(req);
    }

    public void endStream(boolean task_completed) {
        Log.i(LOG_TAG, "Stream ends");
        this.task_end = TrueTime.now();
        this.task_success = task_completed;
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

        Log.i(LOG_TAG, "Starting stream.");
        this.video_out = new VideoOutputThread(video_socket, step_traces);
        this.result_in = new ResultInputThread(result_socket, tkn);

        // record task init
        this.task_start = TrueTime.now();
        this.task_success = false;
        this.total_rtt_stats.clear();
        this.rolling_rtt_stats.clear();

        execs.execute(video_out);
        execs.execute(result_in);

        this.changeStateAndNotify(CMSTATE.STREAMING);
    }

    @SuppressLint("CheckResult")
    private void synchronizeTime() throws ConnectionManagerException {
        synchronized (lock) {
            if (this.addr == null)
                throw new ConnectionManagerException(EXCEPTIONSTATE.NOADDRESS);
            if (this.app_context == null)
                throw new ConnectionManagerException(EXCEPTIONSTATE.NOCONTEXT);

            if (this.force_ntp_sync)
                TrueTimeRx.clearCachedInfo(this.app_context);

            this.changeStateAndNotify(CMSTATE.NTPSYNC);

            if (this.force_ntp_sync || !TrueTimeRx.isInitialized()) {
                this.force_ntp_sync = false;
                Log.i(LOG_TAG, "Synchronizing time with " + this.addr);

                TrueTimeRx.build()
                        .withSharedPreferences(this.app_context)
                        .withRootDispersionMax(Constants.MAX_NTP_DISPERSION)
                        .withLoggingEnabled(true) // this doesn't bother us since it runs before the actual streaming
                        .initializeRx(this.addr)
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
                                            ConnectionManager.lock.notifyAll();
                                        }
                                    }
                                });
            } else {
                Log.i(LOG_TAG, "Clock already in sync");
                synchronized (lock) {
                    this.time_synced = true;
                    lock.notifyAll();
                }
            }
        }
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

    public void setTrace(Uri[] steps) throws ConnectionManagerException {
        synchronized (lock) {
            if (this.state != CMSTATE.DISCONNECTED)
                throw new ConnectionManagerException(EXCEPTIONSTATE.ALREADYCONNECTED);
        }

        this.step_traces = steps;
    }

    public VideoFrame getLastFrame() {
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

    public void forceNTPSync(boolean force) {
        synchronized (lock) {
            this.time_synced = false;
            this.force_ntp_sync = force;
        }
    }

    public void notifySuccessForFrame(VideoFrame frame, int step_index) {
        Log.i(LOG_TAG, "Got success message for frame " + frame.getId());
        registerStats(frame);
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
        synchronized (stat_lock) {
            if (in_frame.getId() == this.last_sent_frame.getId()) {
                long rtt = in_frame.getTimestamp() - this.last_sent_frame.getTimestamp();
                this.rolling_rtt_stats.addValue(rtt);
                this.total_rtt_stats.addValue(rtt);
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
        INVALIDTRACEDIR,
        TASKNOTCOMPLETED,
        NOCONTEXT
    }

    public enum CMSTATE {
        DISCONNECTED,
        NTPSYNC,
        CONNECTING,
        CONNECTED,
        STREAMING,
        STREAMING_DONE,
        DISCONNECTING,
        UPLOADINGRESULTS
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
                case TASKNOTCOMPLETED:
                    this.CMExceptMsg = "Received unexpected task finish message!";
                    break;
                case INVALIDTRACEDIR:
                    this.CMExceptMsg = "Provided trace directory is not valid!";
                    break;
                case NOCONTEXT:
                    this.CMExceptMsg = "No application Context provided!";
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
