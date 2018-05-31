package se.kth.molguin.tracedemo.network.control;

import android.content.Context;
import android.support.annotation.NonNull;
import android.util.Log;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.math.BigInteger;
import java.net.ConnectException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Locale;
import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.locks.ReentrantLock;

import se.kth.molguin.tracedemo.network.gabriel.ConnectionManager;
import se.kth.molguin.tracedemo.network.gabriel.Experiment;
import se.kth.molguin.tracedemo.network.gabriel.ProtocolConst;

import static java.lang.System.exit;
import static se.kth.molguin.tracedemo.network.control.ControlConst.CMD_NTP_SYNC;
import static se.kth.molguin.tracedemo.network.control.ControlConst.CMD_PULL_STATS;
import static se.kth.molguin.tracedemo.network.control.ControlConst.CMD_PUSH_CONFIG;
import static se.kth.molguin.tracedemo.network.control.ControlConst.CMD_PUSH_STEP;
import static se.kth.molguin.tracedemo.network.control.ControlConst.CMD_SHUTDOWN;
import static se.kth.molguin.tracedemo.network.control.ControlConst.CMD_START_EXP;
import static se.kth.molguin.tracedemo.network.control.ControlConst.STATUS_ERROR;
import static se.kth.molguin.tracedemo.network.control.ControlConst.STATUS_SUCCESS;

//import com.android.volley.Request;
//import com.android.volley.RequestQueue;
//import com.android.volley.Response;
//import com.android.volley.VolleyError;
//import com.android.volley.toolbox.Volley;
//import java.util.concurrent.CountDownLatch;
//import se.kth.molguin.tracedemo.network.InputStreamVolleyRequest;
// import static se.kth.molguin.tracedemo.network.control.ControlConst.CMD_FETCH_TRACES;

/**
 * ControlClient connects to the control server and parses commands, effectively controlling
 * the execution of experiments on the client device.
 */
@SuppressWarnings("WeakerAccess")
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

    /**
     * Helper static method.
     * Calculates the MD5 hash of a byte array and returns its hexadecimal string representation.
     *
     * @param data Byte array of data to hash.
     * @return Hexadecimal string representation of the MD5 hash.
     */
    private static String getMD5Hex(byte[] data) {
        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            md.reset();
            md.update(data);
            byte[] hash = md.digest();

            return String.format("%032x", new BigInteger(1, hash)).toUpperCase(Locale.ENGLISH);
        } catch (NoSuchAlgorithmException e) {
            Log.e(LOG_TAG, "Exception!", e);
            exit(-1);
        }
        return null;
    }

    /**
     * Constructs the Control client.
     *
     * @param address     Host address to connect to.
     * @param port        TCP port on the host to connect to.
     * @param app_context Context of the current app.
     * @param cm          Reference to the ConnectionManager.
     */
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

    /**
     * Constructs a ControlClient using default parameters for host and port.
     *
     * @param app_context Context of the current app.
     * @param cm          Reference to the ConnectionManager.
     */
    public ControlClient(Context app_context, ConnectionManager cm) {
        this(ProtocolConst.SERVER, ControlConst.CONTROL_PORT, app_context, cm);
    }

    /**
     * Notifies the ControlServer of the status of a recent command.
     *
     * @param success Success status of the command.
     */
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

    /**
     * Notifies the backend of the conclusion of the current experiment repetition.
     */
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

    /**
     * Connects to the Control server.
     *
     * @throws IOException In case something goes wrong connecting.
     */
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
//                    case CMD_FETCH_TRACES:
//                        this.downloadTraces();
//                        break;
                    case CMD_PUSH_STEP:
                        this.receiveStep();
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
            byte[] config_b = new byte[config_len];
            this.data_in.readFully(config_b);

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
        } catch (EOFException e) {
            Log.e(LOG_TAG, "Unexpected end of stream from socket.");
            exit(-1);
        } catch (IOException e) {
            Log.e(LOG_TAG, "Exception!", e);
            exit(-1);
        }
    }

    private boolean checkStep(int index, @NonNull String checksum) {
        String filename = ControlConst.STEP_PREFIX + index + ControlConst.STEP_SUFFIX;
        Log.i(LOG_TAG,
                String.format(Locale.ENGLISH, "Checking if %s already exists locally...", filename));
        try {
            File step_file = this.app_context.getFileStreamPath(filename);
            byte[] data = new byte[(int) step_file.length()];
            try (FileInputStream f_in = new FileInputStream(step_file)) {
                if (step_file.length() != f_in.read(data)) throw new IOException();
            }

            String local_chksum = ControlClient.getMD5Hex(data);
            String remote_chksum = checksum.toUpperCase(Locale.ENGLISH);

            if (!Objects.equals(local_chksum, remote_chksum)) {
                Log.w(LOG_TAG, String.format(
                        Locale.ENGLISH,
                        "%s found but MD5 checksums do not match.",
                        filename));
                Log.w(LOG_TAG,
                        String.format(
                                Locale.ENGLISH,
                                "Remote: %s\tLocal: %s",
                                remote_chksum, local_chksum
                        ));
                return false;
            }

            Log.i(LOG_TAG, String.format(Locale.ENGLISH, "%s found locally!", filename));
            return true;

        } catch (FileNotFoundException e) {
            Log.w(LOG_TAG,
                    String.format(Locale.ENGLISH, "%s was not found locally!", filename));
            return false;
        } catch (IOException e) {
            Log.w(LOG_TAG,
                    String.format(Locale.ENGLISH, "Error trying to read %s.", filename));
            return false;
        }
    }

    private void receiveStep() {
        // TODO: connectionmanager state

        // first get initial step metadata message
        JSONObject metadata;
        int index = -1;
        int size = 0;
        String checksum = "";

        try {
            Log.i(LOG_TAG, "Getting step metadata from Control server.");
            byte[] metadata_b = new byte[this.data_in.readInt()];
            this.data_in.readFully(metadata_b);
            metadata = new JSONObject(new String(metadata_b, "utf-8"));

            index = metadata.getInt(ControlConst.STEP_METADATA_INDEX);
            size = metadata.getInt(ControlConst.STEP_METADATA_SIZE);
            checksum = metadata.getString(ControlConst.STEP_METADATA_CHKSUM);

        } catch (JSONException e) {
            Log.e(LOG_TAG, "Could not parse step metadata!");
            exit(-1);
        } catch (EOFException e) {
            Log.e(LOG_TAG, "Unexpected end of stream from socket.");
            exit(-1);
        } catch (IOException e) {
            Log.e(LOG_TAG, "Error receiving step metadata!");
            exit(-1);
        }

        if (this.checkStep(index, checksum)) {
            // step found locally
            this.notifyCommandStatus(true);
            return;
        }

        // step not found locally
        this.notifyCommandStatus(false);
        String filename = ControlConst.STEP_PREFIX + index + ControlConst.STEP_SUFFIX;
        // receive step from Control

        try {
            Log.i(LOG_TAG,
                    String.format(Locale.ENGLISH,
                            "Receiving step %s from Control. Total size: %d bytes",
                            filename, size));
            byte[] data = new byte[size];
            this.data_in.readFully(data);

            Log.i(LOG_TAG, String.format(Locale.ENGLISH,
                    "Received %s from Control.", filename));

            // verify checksums match before saving it

            // reverse for testing
            String recv_md5 = ControlClient.getMD5Hex(data);
            checksum = checksum.toUpperCase(Locale.ENGLISH);
            Log.i(LOG_TAG,
                    String.format(Locale.ENGLISH, "Checksums - remote: %s\tlocal: %s",
                            checksum, recv_md5));

            if (!Objects.equals(recv_md5, checksum)) {
                Log.e(LOG_TAG,
                        String.format(Locale.ENGLISH,
                                "Received step %s correctly, but MD5 checksums do not match!",
                                filename));
                this.notifyCommandStatus(false);
                exit(-1);
            }

            // checksums match, so save it
            try (FileOutputStream f_out = this.app_context.openFileOutput(filename, Context.MODE_PRIVATE)) {
                Log.i(LOG_TAG, String.format(Locale.ENGLISH, "Saving %s locally", filename));
                f_out.write(data);
            }
            this.notifyCommandStatus(true);
        } catch (EOFException e) {
            Log.e(LOG_TAG, "Unexpected end of stream from socket.");
            exit(-1);
        } catch (IOException e) {
            Log.e(LOG_TAG,
                    String.format(Locale.ENGLISH,
                            "Error while receiving step %s from Control...",
                            filename), e);
            this.notifyCommandStatus(false);
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

/*    private void downloadTraces() {
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
    }*/

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
