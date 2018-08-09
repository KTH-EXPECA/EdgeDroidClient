package se.kth.molguin.tracedemo.network.control;

import android.content.Context;
import android.support.annotation.NonNull;
import android.util.Log;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
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

import se.kth.molguin.tracedemo.UILink;
import se.kth.molguin.tracedemo.network.control.experiment.Config;
import se.kth.molguin.tracedemo.synchronization.INTPSync;
import se.kth.molguin.tracedemo.synchronization.NTPClient;
import se.kth.molguin.tracedemo.synchronization.NullNTPSync;

import static java.lang.System.exit;
import static se.kth.molguin.tracedemo.network.control.ControlConst.CMD_NTP_SYNC;
import static se.kth.molguin.tracedemo.network.control.ControlConst.CMD_PULL_STATS;
import static se.kth.molguin.tracedemo.network.control.ControlConst.CMD_PUSH_CONFIG;
import static se.kth.molguin.tracedemo.network.control.ControlConst.CMD_PUSH_STEP;
import static se.kth.molguin.tracedemo.network.control.ControlConst.CMD_SHUTDOWN;
import static se.kth.molguin.tracedemo.network.control.ControlConst.CMD_START_EXP;
import static se.kth.molguin.tracedemo.network.control.ControlConst.STATUS_ERROR;
import static se.kth.molguin.tracedemo.network.control.ControlConst.STATUS_SUCCESS;

/**
 * ControlClient connects to the control server and parses commands, effectively controlling
 * the execution of experiments on the client device.
 */
@SuppressWarnings("WeakerAccess")
public class ControlClient implements AutoCloseable {
    private final static String LOG_TAG = "ControlClient";

    private final ExecutorService exec;
    private final Socket socket;
    private final String address;
    private final int port;
    private final UILink uiLink;
    private final Context app_context;
    private final ReentrantLock lock;
    private final NTPClient ntp;

    private boolean running;

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
     */
    ControlClient(final String address, final int port, final Context app_context, final UILink uiLink) {
        this.address = address;
        this.port = port;
        this.socket = new Socket();
        this.app_context = app_context;
        this.ntp = new NTPClient(address);
        this.exec = Executors.newSingleThreadExecutor();
        this.lock = new ReentrantLock();
        this.uiLink = uiLink;
    }

    /**
     * Constructs a ControlClient using default parameters for host and port.
     *
     * @param app_context Context of the current app.
     */
    public ControlClient(final Context app_context, final UILink uiLink) {
        this(ControlConst.SERVER, ControlConst.CONTROL_PORT, app_context, uiLink);
    }

    public void init() {
        this.running = true;
        Log.i(LOG_TAG, "Initializing...");
        this.exec.submit(new Runnable() {
            @Override
            public void run() {
                ControlClient.this.connectToControl();
                ControlClient.this.waitForCommands();
            }
        });
    }

    private byte[] getPayloadFromSocket() throws IOException {
        DataInputStream data_in = new DataInputStream(this.socket.getInputStream());
        final int length = data_in.readInt();
        final byte[] payload = new byte[length];
        data_in.readFully(payload);
        return payload;
    }

    private JSONObject getJSONFromSocket() throws IOException, JSONException {
        return new JSONObject(new String(this.getPayloadFromSocket(), "UTF-8"));
    }

    /**
     * Connects to the Control server.
     *
     * @throws IOException In case something goes wrong connecting.
     */
    private void connectToControl() throws IOException {
        Log.i(LOG_TAG, String.format("Connecting to Control Server at %s:%d",
                this.address, this.port));
        while (this.isRunning()) {
            try {
                this.socket.setTcpNoDelay(true);
                this.socket.connect(new InetSocketAddress(this.address, this.port), 100);
                // this.data_in = new DataInputStream(this.socket.getInputStream());
                // this.data_out = new DataOutputStream(this.socket.getOutputStream());

                break;
            } catch (SocketTimeoutException e) {
                Log.i(LOG_TAG, "Timeout - retrying...");
            } catch (ConnectException e) {
                Log.i(LOG_TAG, "Connection exception! Retrying...");
                Log.e(LOG_TAG, "Exception!", e);
            }
        }
        Log.i(LOG_TAG, String.format("Connected to Control Server at %s:%d", address, port));
    }

    private void stateUnconfigured() throws IOException, JSONException {
        // wait for config message
        DataInputStream data_in = new DataInputStream(this.socket.getInputStream());
        if (data_in.readInt() != CMD_PUSH_CONFIG)
            throw new IOException(); // TODO: specific exception

        Log.i(LOG_TAG, "Receiving experiment configuration...");
        this.uiLink.postLogMessage("Configuring experiment...");

        final Config config = new Config(this.getJSONFromSocket());
        this.notifyCommandStatus(true);

        // wait for steps
        for (int i = 1; i <= config.num_steps; i++) {
            if (data_in.readInt() != CMD_PUSH_STEP)
                throw new IOException(); // TODO: specific exception

            final JSONObject step_metadata = this.getJSONFromSocket();
            final int index = step_metadata.getInt(ControlConst.STEP_METADATA_INDEX);
            final int size = step_metadata.getInt(ControlConst.STEP_METADATA_SIZE);
            final String checksum = step_metadata.getString(ControlConst.STEP_METADATA_CHKSUM);

            if (index != i)
                // step in wrong order?
                throw new IOException(); // TODO: specific exception

            final boolean found = this.checkStep(index, checksum);
            this.notifyCommandStatus(found);
            if (!found)
                // step was not found, download it
                this.receiveStep(index, size, checksum);
        }

        // fully configured, change state:
        this.stateConfigured(config);
    }

    private void stateConfigured(final Config config) throws IOException {
        // wait for NTP sync message
        final DataInputStream data_in = new DataInputStream(this.socket.getInputStream());
        while (this.isRunning()) {

            // listen for commands
            // only valid commands at this stage are sync and shutdown
            switch (data_in.readInt()) {
                case CMD_NTP_SYNC:
                                        
                    break;
                case CMD_SHUTDOWN:
                    return; // shut down gracefully
                default:
                    // got an invalid command
                    throw new IOException(); // TODO: specific exception
            }


        }
    }

    /**
     * Notifies the ControlServer of the status of a recent command.
     *
     * @param success Success status of the command.
     */
    private void notifyCommandStatus(boolean success) {
        int status = success ? STATUS_SUCCESS : STATUS_ERROR;
        try {
            DataOutputStream data_out = new DataOutputStream(this.socket.getOutputStream());
            data_out.writeInt(status);
            data_out.flush();
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
            DataOutputStream data_out = new DataOutputStream(this.socket.getOutputStream());
            data_out.writeInt(ControlConst.MSG_EXPERIMENT_FINISH);
            data_out.flush();
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

    public boolean isRunning() {
        this.lock.lock();
        try {
            return this.running;
        } finally {
            this.lock.unlock();
        }
    }

    private void shutDownApp() {
        Log.w(LOG_TAG, "Shutdown command from control!");
        this.ntp.close();

    }

    private boolean checkStep(final int index, @NonNull final String checksum) {
        String filename = ControlConst.STEP_PREFIX + index + ControlConst.STEP_SUFFIX;
        Log.i(LOG_TAG,
                String.format(Locale.ENGLISH, "Checking if %s already exists locally...", filename));
        try {
            final File step_file = this.app_context.getFileStreamPath(filename);
            final byte[] data = new byte[(int) step_file.length()];
            try (FileInputStream f_in = new FileInputStream(step_file)) {
                if (step_file.length() != f_in.read(data)) throw new IOException();
            }

            String local_chksum = getMD5Hex(data);
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

    private void receiveStep(final int index, final int size, @NonNull final String checksum) throws IOException {
        // step not found locally
        this.uiLink.postLogMessage("Step " + index + " not found locally, downloading copy from server...");
        final String filename = ControlConst.STEP_PREFIX + index + ControlConst.STEP_SUFFIX;
        // receive step from Control
        final DataInputStream data_in = new DataInputStream(this.socket.getInputStream());
        Log.i(LOG_TAG, String.format(Locale.ENGLISH, "Receiving step %s from Control. Total size: %d bytes", filename, size));
        byte[] data = new byte[size];
        data_in.readFully(data);

        Log.i(LOG_TAG, String.format(Locale.ENGLISH, "Received %s from Control.", filename));

        // verify checksums match before saving it
        final String recv_md5 = getMD5Hex(data);
        final String prev_checksum = checksum.toUpperCase(Locale.ENGLISH);
        Log.i(LOG_TAG, String.format(Locale.ENGLISH, "Checksums - remote: %s\tlocal: %s", prev_checksum, recv_md5));

        if (!Objects.equals(recv_md5, prev_checksum)) {
            Log.e(LOG_TAG, String.format(Locale.ENGLISH, "Received step %s correctly, but MD5 checksums do not match!", filename));
            throw new IOException(); // TODO: exception
        }

        // checksums match, so save it
        try (FileOutputStream f_out = this.app_context.openFileOutput(filename, Context.MODE_PRIVATE)) {
            Log.i(LOG_TAG, String.format(Locale.ENGLISH, "Saving %s locally", filename));
            f_out.write(data);
        }
        uiLink.postLogMessage("Successfully received step " + index + ".");
        this.notifyCommandStatus(true);

    }

    private void uploadStats() {
        Log.i(LOG_TAG, "Uploading run metrics.");

        try {
            // TODO: get stats from run
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

    private void startExperiment() {
        try {
            // TODO: execute experiment inside run
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
