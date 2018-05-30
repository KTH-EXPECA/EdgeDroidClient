package se.kth.molguin.tracedemo.network.control;

import android.content.Context;
import android.util.Log;

import com.android.volley.Request;
import com.android.volley.RequestQueue;
import com.android.volley.Response;
import com.android.volley.VolleyError;
import com.android.volley.toolbox.Volley;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.ConnectException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.locks.ReentrantLock;

import se.kth.molguin.tracedemo.network.InputStreamVolleyRequest;
import se.kth.molguin.tracedemo.network.gabriel.ConnectionManager;
import se.kth.molguin.tracedemo.network.gabriel.Experiment;
import se.kth.molguin.tracedemo.network.gabriel.ProtocolConst;

import static java.lang.System.exit;
import static se.kth.molguin.tracedemo.network.control.ControlConst.CMD_FETCH_TRACES;
import static se.kth.molguin.tracedemo.network.control.ControlConst.CMD_NTP_SYNC;
import static se.kth.molguin.tracedemo.network.control.ControlConst.CMD_PULL_STATS;
import static se.kth.molguin.tracedemo.network.control.ControlConst.CMD_PUSH_CONFIG;
import static se.kth.molguin.tracedemo.network.control.ControlConst.CMD_SHUTDOWN;
import static se.kth.molguin.tracedemo.network.control.ControlConst.CMD_START_EXP;
import static se.kth.molguin.tracedemo.network.control.ControlConst.STATUS_ERROR;
import static se.kth.molguin.tracedemo.network.control.ControlConst.STATUS_SUCCESS;

public class ControlClient implements AutoCloseable {

    private final static String LOG_TAG = "ControlClient";

    private ExecutorService exec;
    private Socket socket;
    private DataInputStream data_in;
    private DataOutputStream data_out;
    private Context app_context;
    private ConnectionManager cm;
    private Experiment.Config config;

    private ReentrantLock lock;
    private boolean running;

    private String address;
    private int port;

    private byte[] readBytesFromSocket(int size) throws IOException {
        byte[] data = new byte[size];
        int total_read = 0;
        while (total_read < size) {
            int read = this.data_in.read(data, total_read, size - total_read);
            if (read <= 0)
                throw new IOException();
            total_read += read;
        }
        return data;
    }

    ControlClient(String address, int port, Context app_context, ConnectionManager cm) {
        this.address = address;
        this.port = port;
        this.app_context = app_context;
        this.config = null;
        this.cm = cm;

        this.exec = Executors.newSingleThreadExecutor();

        this.running = true;
        this.lock = new ReentrantLock();

        Log.i(LOG_TAG, "Initializing...");
        this.exec.submit(new Runnable() {
            @Override
            public void run() {
                try {
                    ControlClient.this.connectToControl();
                    ControlClient.this.waitForCommands();
                } catch (IOException e) {
                    Log.e(LOG_TAG, "Exception!", e);
                    exit(-1);
                }
            }
        });
    }

    public ControlClient(Context app_context, ConnectionManager cm) {
        this(ProtocolConst.SERVER, ControlConst.CONTROL_PORT, app_context, cm);
    }

    private void notifyCommandStatus(boolean success) {
        int status = success ? STATUS_SUCCESS : STATUS_ERROR;
        try {
            this.data_out.writeInt(status);
            this.data_out.flush();
        } catch (SocketException e) {
            Log.w(LOG_TAG, "Socket closed!");
            Log.e(LOG_TAG, "Exception!", e);
        } catch (IOException e) {
            Log.e(LOG_TAG, "Exception!", e);
            exit(-1);
        }
    }

    public void notifyExperimentFinish() {
        this.lock.lock();
        try {
            this.data_out.writeInt(ControlConst.MSG_EXPERIMENT_FINISH);
            this.data_out.flush();
        } catch (SocketException e) {
            Log.w(LOG_TAG, "Socket closed!");
            Log.e(LOG_TAG, "Exception!", e);
        } catch (IOException e) {
            Log.e(LOG_TAG, "Exception!", e);
            exit(-1);
        } finally {
            this.lock.unlock();
        }
    }

    private void connectToControl() throws IOException {
        Log.i(LOG_TAG, String.format("Connecting to Control Server at %s:%d",
                this.address, this.port));
        boolean connected = false;
        while (!connected) {
            this.lock.lock();
            try {
                if (!this.running) return;

                this.socket = new Socket();
                this.socket.setTcpNoDelay(true);
                this.socket.connect(new InetSocketAddress(this.address, this.port), 100);
                connected = true;

                this.data_in = new DataInputStream(this.socket.getInputStream());
                this.data_out = new DataOutputStream(this.socket.getOutputStream());
            } catch (SocketTimeoutException e) {
                Log.i(LOG_TAG, "Timeout - retrying...");
            } catch (ConnectException e) {
                Log.i(LOG_TAG, "Connection exception! Retrying...");
                Log.e(LOG_TAG, "Exception!", e);
            } finally {
                this.lock.unlock();
            }
        }
        Log.i(LOG_TAG, String.format("Connected to Control Server at %s:%d", address, port));
    }

    private void waitForCommands() {
        while (true) {

            this.lock.lock();
            try {
                if (!running) return;
            } finally {
                this.lock.unlock();
            }

            ConnectionManager.CMSTATE previous_state = this.cm.getState();
            try {
                this.cm.changeState(ConnectionManager.CMSTATE.LISTENINGCONTROL);

                int cmd_id = this.data_in.readInt();
                Log.i(LOG_TAG, "Got command with ID " + String.format("0x%08X", cmd_id));

                switch (cmd_id) {
                    case CMD_PUSH_CONFIG:
                        this.getConfigFromServer();
                        break;
                    case CMD_PULL_STATS:
                        this.uploadStats();
                        break;
                    case CMD_START_EXP:
                        this.startExperiment();
                        break;
                    case CMD_FETCH_TRACES:
                        this.downloadTraces();
                        break;
                    case CMD_NTP_SYNC:
                        this.ntpSync();
                        break;
                    case CMD_SHUTDOWN:
                        this.shutDownApp();
                        break;
                    default:
                        break;
                }
            } catch (IOException e) {
                this.cm.changeState(previous_state);
                Log.w(LOG_TAG, "Socket closed!");
                try {
                    this.close();
                } catch (Exception e1) {
                    Log.e(LOG_TAG, "Error while shutting down.");
                    e1.printStackTrace();
                    exit(-1);
                }
            } catch (ConnectionManager.ConnectionManagerException e) {
                Log.e(LOG_TAG, "Error when triggering NTP sync!", e);
                exit(-1);
            }
        }
    }

    private void shutDownApp() {
        Log.w(LOG_TAG, "Shutdown command from control!");
        this.cm.triggerAppShutDown();
    }

    private void ntpSync() throws ConnectionManager.ConnectionManagerException {
        this.cm.syncNTP();
        this.notifyCommandStatus(true);
    }

    private void getConfigFromServer() {
        Log.i(LOG_TAG, "Receiving experiment configuration...");
        this.cm.changeState(ConnectionManager.CMSTATE.CONFIGURING);

        try {
            int config_len = this.data_in.readInt();
            byte[] config_b = this.readBytesFromSocket(config_len);

            JSONObject config = new JSONObject(new String(config_b, "UTF-8"));
            this.config = new Experiment.Config(config);
            this.cm.setConfig(this.config);

            this.notifyCommandStatus(true);
        } catch (SocketException e) {
            Log.w(LOG_TAG, "Socket closed!");
            Log.e(LOG_TAG, "Exception!", e);
        } catch (UnsupportedEncodingException | JSONException e) {
            Log.e(LOG_TAG, "Could not parse incoming data!");
            Log.e(LOG_TAG, "Exception!", e);
            this.notifyCommandStatus(false);
        } catch (IOException e) {
            Log.e(LOG_TAG, "Exception!", e);
            exit(-1);
        }
    }

    private void uploadStats() {
        Log.i(LOG_TAG, "Uploading run metrics.");

        try {
            JSONObject payload = this.cm.getResults();

            Log.i(LOG_TAG, "Sending JSON data...");
            byte[] payload_b = payload.toString().getBytes("UTF-8");
            Log.i(LOG_TAG, String.format("Payload size: %d bytes", payload_b.length));

            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            DataOutputStream outStream = new DataOutputStream(baos);
            outStream.writeInt(payload_b.length);
            outStream.write(payload_b);

            this.data_out.write(baos.toByteArray());
            this.data_out.flush();
        } catch (SocketException e) {
            Log.w(LOG_TAG, "Socket closed!");
            Log.e(LOG_TAG, "Exception!", e);
        } catch (UnsupportedEncodingException e) {
            Log.e(LOG_TAG, "Error sending metrics!");
            Log.e(LOG_TAG, "Exception!", e);
            this.notifyCommandStatus(false);
            exit(-1);
        } catch (IOException e) {
            Log.e(LOG_TAG, "Exception!", e);
            exit(-1);
        }
    }

    private void downloadTraces() {
        this.cm.changeState(ConnectionManager.CMSTATE.FETCHINGTRACE);

        final File appDir = this.app_context.getFilesDir();
        for (File f : appDir.listFiles())
            if (!f.isDirectory())
                f.delete();

        final CountDownLatch latch = new CountDownLatch(this.config.num_steps);
        final RequestQueue requestQueue = Volley.newRequestQueue(this.app_context);

        for (int i = 0; i < this.config.num_steps; i++) {

            final String stepFilename = ControlConst.STEP_PREFIX + (i + 1) + ControlConst.STEP_SUFFIX;
            final String stepUrl = this.config.trace_url + stepFilename;

            Log.i(LOG_TAG, "Enqueuing request for " + stepFilename);

            final Response.Listener<byte[]> response_listener = new Response.Listener<byte[]>() {
                @Override
                public void onResponse(byte[] response) {
                    try {
                        if (response != null) {
                            Log.i(LOG_TAG, "Got trace " + stepFilename);
                            FileOutputStream file_out = ControlClient.this.app_context.openFileOutput(stepFilename, Context.MODE_PRIVATE);
                            file_out.write(response);
                            file_out.close();
                            latch.countDown();
                        }
                    } catch (IOException e) {
                        Log.e(LOG_TAG, "Exception!", e);
                        exit(-1);
                    }
                }
            };

            final Response.ErrorListener error_listener = new Response.ErrorListener() {
                @Override
                public void onErrorResponse(VolleyError error) {
                    Log.e(LOG_TAG, "Could not fetch " + stepUrl);
                    Log.e(LOG_TAG, "Retry " + stepFilename);

                    // re-enqueue request if it fails - repeat ad nauseam
                    InputStreamVolleyRequest req =
                            new InputStreamVolleyRequest(Request.Method.GET,
                                    stepUrl, response_listener, this, null);

                    requestQueue.add(req);
                }
            };


            InputStreamVolleyRequest req =
                    new InputStreamVolleyRequest(Request.Method.GET,
                            stepUrl, response_listener, error_listener, null);

            requestQueue.add(req);
        }

        try {
            latch.await();
        } catch (InterruptedException e) {
            Log.e(LOG_TAG, "Exception!", e);
        } finally {
            requestQueue.stop();
        }

        this.notifyCommandStatus(true);
    }

    private void startExperiment() {
        try {
            this.cm.runExperiment();
        } catch (ConnectionManager.ConnectionManagerException | IOException e) {
            Log.e(LOG_TAG, "Exception!", e);
            this.notifyCommandStatus(false);
            exit(-1);
        }
        this.notifyCommandStatus(true);
    }

    @Override
    public void close() throws Exception {

        this.lock.lock();
        try {
            this.running = false;

            this.exec.shutdownNow();

            if (null != this.socket)
                this.socket.close();

            if (null != this.data_in)
                this.data_in.close();

            if (null != this.data_out)
                this.data_out.close();

        } finally {
            this.lock.unlock();
        }
    }
}
