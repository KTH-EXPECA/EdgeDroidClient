package se.kth.molguin.tracedemo.network.control;

import android.content.Context;
import android.support.annotation.NonNull;
import android.util.Log;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
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
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import se.kth.molguin.tracedemo.UILink;
import se.kth.molguin.tracedemo.network.control.experiment.Config;
import se.kth.molguin.tracedemo.synchronization.NTPClient;

import static java.lang.System.exit;
import static se.kth.molguin.tracedemo.network.control.ControlConst.CMD_NTP_SYNC;
import static se.kth.molguin.tracedemo.network.control.ControlConst.CMD_PULL_STATS;
import static se.kth.molguin.tracedemo.network.control.ControlConst.CMD_PUSH_CONFIG;
import static se.kth.molguin.tracedemo.network.control.ControlConst.CMD_PUSH_STEP;
import static se.kth.molguin.tracedemo.network.control.ControlConst.CMD_SHUTDOWN;
import static se.kth.molguin.tracedemo.network.control.ControlConst.CMD_START_EXP;
import static se.kth.molguin.tracedemo.network.control.ControlConst.STATUS_ERROR;
import static se.kth.molguin.tracedemo.network.control.ControlConst.STATUS_SUCCESS;

public class NewControlClient {
    private final static String LOG_TAG = "ControlClient";

    private final String address;
    private final int port;
    private final Context app_context;
    private final ExecutorService exec;
    private final Lock lock;
    private final UILink uiLink;

    private boolean running;

    /**
     * Constructs the Control client.
     *
     * @param address     Host address to connect to.
     * @param port        TCP port on the host to connect to.
     * @param app_context Context of the current app.
     */
    NewControlClient(final String address, final int port, final Context app_context, final UILink uiLink) {
        this.address = address;
        this.port = port;
        this.app_context = app_context;
        this.exec = Executors.newSingleThreadExecutor();
        this.lock = new ReentrantLock();
        this.uiLink = uiLink;

        this.running = false;
    }

    /**
     * Constructs a ControlClient using default parameters for host and port.
     *
     * @param app_context Context of the current app.
     */
    public NewControlClient(final Context app_context, final UILink uiLink) {
        this(ControlConst.SERVER, ControlConst.CONTROL_PORT, app_context, uiLink);
    }


    public void initMainLoop() {
        Runnable mainLoop = new Runnable() {
            @Override
            public void run() {
                try {
                    // 1. Connect
                    Log.i(LOG_TAG, String.format("Connecting to Control Server at %s:%d",
                            NewControlClient.this.address, NewControlClient.this.port));

                    final Socket socket = new Socket();
                    final NTPClient ntp = new NTPClient(NewControlClient.this.address);

                    socket.setTcpNoDelay(true);
                    try {
                        socket.connect(
                                new InetSocketAddress(
                                        NewControlClient.this.address,
                                        NewControlClient.this.port), 100);
                    } catch (SocketTimeoutException e) {
                        Log.i(LOG_TAG, "Timeout - retrying...");
                    } catch (ConnectException e) {
                        Log.i(LOG_TAG, "Connection exception! Retrying...");
                        Log.e(LOG_TAG, "Exception!", e);
                    }

                    final DataInputStream data_in = new DataInputStream(socket.getInputStream());
                    final DataOutputStream data_out = new DataOutputStream(socket.getOutputStream());

                    Log.i(LOG_TAG, String.format("Connected to Control Server at %s:%d",
                            NewControlClient.this.address, NewControlClient.this.port));

                    // 2. listen for config
                    if (data_in.readInt() != CMD_PUSH_CONFIG) {
                        // got unexpected command
                        // TODO: New exception?
                    }

                    final Config config = new Config(new JSONObject(new String(
                            NewControlClient.readMessageFromInputStream(data_in), "UTF-8")));

                    // 3. listen for other commands
                    while (NewControlClient.this.isRunning()) {
                        final int cmd_id = data_in.readInt();
                        Log.i(LOG_TAG, "Got command with ID " + String.format("0x%08X", cmd_id));

                        boolean success = false;
                        try {
                            switch (cmd_id) {
                                case CMD_PULL_STATS:
                                    this.uploadStats();
                                    break;
                                case CMD_START_EXP:
                                    this.startExperiment();
                                    break;
                                case CMD_PUSH_STEP:
                                    receiveStep(data_in, data_out);
                                    success = true;
                                    break;
                                case CMD_NTP_SYNC: {
                                    uiLink.postLogMessage("Synchronizing NTP...");
                                    ntp.sync();
                                    success = true;
                                }
                                case CMD_SHUTDOWN:
                                    this.shutDownApp();
                                    break;
                                case CMD_PUSH_CONFIG:
                                default:
                                    break;
                            }
                        } finally {
                            NewControlClient.notifyCommandStatus(data_out, success);
                        }
                    }


                } catch (InterruptedException e) {
                    return;
                } catch (SocketException e) {
                    e.printStackTrace();
                } catch (IOException e) {
                    e.printStackTrace();
                } catch (JSONException e) {
                    e.printStackTrace();
                }
            }
        };

        this.lock.lock();
        try {
            this.running = true;
            this.exec.submit(mainLoop);
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

    private void receiveStep(final DataInputStream data_in, final DataOutputStream data_out) throws IOException, JSONException {
        Log.i(LOG_TAG, "Getting step metadata from Control Server.");
        final byte[] b_metadata = readMessageFromInputStream(data_in);
        final JSONObject metadata = new JSONObject(new String(b_metadata, "UTF-8"));

        final int index = metadata.getInt(ControlConst.STEP_METADATA_INDEX);
        final int size = metadata.getInt(ControlConst.STEP_METADATA_SIZE);
        final String checksum = metadata.getString(ControlConst.STEP_METADATA_CHKSUM).toUpperCase(Locale.ENGLISH);

        if (this.checkStep(index, checksum)) {
            this.uiLink.postLogMessage("Step " + index + " found locally!");
            return;
        }

        // didn't find it locally
        this.uiLink.postLogMessage("Step " + index + " not found locally, downloading copy from server...");
        notifyCommandStatus(data_out, false);
        final String filename = ControlConst.STEP_PREFIX + index + ControlConst.STEP_SUFFIX;

        Log.i(LOG_TAG, String.format(Locale.ENGLISH,
                "Receiving step %s from Control. Total size: %d bytes",
                filename, size));
        final byte[] data = new byte[size];
        data_in.readFully(data);

        Log.i(LOG_TAG, String.format(Locale.ENGLISH, "Received %s from Control.", filename));

        // verify checksums match before saving it
        String recv_md5 = getMD5Hex(data);
        Log.i(LOG_TAG, String.format(Locale.ENGLISH, "Checksums - remote: %s\tlocal: %s",
                checksum, recv_md5));

        if (!Objects.equals(recv_md5, checksum)) {
            Log.e(LOG_TAG, String.format(Locale.ENGLISH,
                    "Received step %s correctly, but MD5 checksums do not match!",
                    filename));
            throw new IOException();
        }

        // checksums match, so save it
        Log.i(LOG_TAG, String.format(Locale.ENGLISH, "Saving %s locally", filename));
        try (final FileOutputStream f_out = this.app_context.openFileOutput(filename, Context.MODE_PRIVATE)) {
            f_out.write(data);
        }
        this.uiLink.postLogMessage("Successfully received step " + index + ".");
    }

    private boolean checkStep(final int index, @NonNull final String remote_chksum) {
        final String filename = ControlConst.STEP_PREFIX + index + ControlConst.STEP_SUFFIX;
        Log.i(LOG_TAG,
                String.format(Locale.ENGLISH, "Checking if %s already exists locally...", filename));
        try {
            File step_file = this.app_context.getFileStreamPath(filename);
            byte[] data = new byte[(int) step_file.length()];
            try (FileInputStream f_in = new FileInputStream(step_file)) {
                if (step_file.length() != f_in.read(data)) throw new IOException();
            }

            final String local_chksum = getMD5Hex(data);

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

    private static byte[] readMessageFromInputStream(final DataInputStream data_in) throws IOException {
        final int length = data_in.readInt();
        final byte[] payload = new byte[length];
        data_in.readFully(payload);
        return payload;
    }

    private static void notifyCommandStatus(final DataOutputStream data_out, final boolean success) throws IOException {
        final int status = success ? STATUS_SUCCESS : STATUS_ERROR;
        data_out.writeInt(status);
        data_out.flush();
    }


}
