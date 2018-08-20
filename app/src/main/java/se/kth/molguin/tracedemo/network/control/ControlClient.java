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
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.FutureTask;
import java.util.concurrent.locks.ReentrantLock;

import se.kth.molguin.tracedemo.ModelState;
import se.kth.molguin.tracedemo.ShutdownMessage;
import se.kth.molguin.tracedemo.network.control.experiment.Config;
import se.kth.molguin.tracedemo.network.control.experiment.run.Run;
import se.kth.molguin.tracedemo.network.control.experiment.run.RunStats;
import se.kth.molguin.tracedemo.synchronization.INTPSync;
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

/**
 * ControlClient connects to the control server and parses commands, effectively controlling
 * the execution of experiments on the client device.
 */
@SuppressWarnings("WeakerAccess")
public class ControlClient {
    private final static String LOG_TAG = "ControlClient";

    private final ExecutorService exec;
    private final Socket socket;
    private final String address;
    private final int port;
    private final ModelState modelState;
    private final ReentrantLock lock;
    private final NTPClient ntp;

    private boolean running;
    private Future internal_task;

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
     * @param address Host address to connect to.
     * @param port    TCP port on the host to connect to.
     */
    ControlClient(final String address, final int port, final ModelState modelState) {
        this.address = address;
        this.port = port;
        this.socket = new Socket();
        this.ntp = new NTPClient(address);
        this.exec = Executors.newSingleThreadExecutor();
        this.lock = new ReentrantLock();
        this.modelState = modelState;

        this.running = false;

        // initialize internal task as a "null" callable to avoid null checks
        this.internal_task = new FutureTask<>(new Callable<Void>() {
            @Override
            public Void call() {
                return null;
            }
        });
    }

    /**
     * Constructs a ControlClient using default parameters for host and port.
     */
    public ControlClient(final ModelState modelState) {
        this(ControlConst.SERVER, ControlConst.CONTROL_PORT, modelState);
    }

    public void init() {
        this.running = true;
        Log.i(LOG_TAG, "Initializing...");
        this.internal_task = this.exec.submit(new Runnable() {
            @Override
            public void run() {
                int run_count = 0;
                try {
                    connectToControl();
                    run_count = stateUnconfigured();

                    // if we get here we got a shutdown command from control
                    Log.i(LOG_TAG, "Got shutdown command!");
                    modelState.postLogMessage("Got shutdown command!");

                    // shut down
                    lock.lock();
                    try {
                        running = false;
                    } finally {
                        lock.unlock();
                    }
                    socket.close();

                    // done, now notify UI!
                    modelState.postAppStateMsg(new ShutdownMessage(true, run_count, ""));
                    // TODO: are we done here?

                } catch (IOException e) {
                    e.printStackTrace();
                } catch (Run.RunException e) {
                    e.printStackTrace();
                } catch (ExecutionException e) {
                    e.printStackTrace();
                } catch (JSONException e) {
                    e.printStackTrace();
                } catch (InterruptedException e) {
                    e.printStackTrace();
                } catch (RunStats.RunStatsException e) {
                    e.printStackTrace();
                }

                // TODO EXCEPTIONS
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

    private int stateUnconfigured() throws IOException, JSONException, Run.RunException, ExecutionException, InterruptedException, RunStats.RunStatsException {
        // wait for config message
        final DataInputStream data_in = new DataInputStream(this.socket.getInputStream());

        switch (data_in.readInt()) {
            case CMD_PUSH_CONFIG:
                break;
            case CMD_SHUTDOWN:
                return 0;
            default:
                throw new IOException(); // TODO: specific exception
        }

        Log.i(LOG_TAG, "Receiving experiment configuration...");
        this.modelState.postLogMessage("Configuring experiment...");

        final Config config = new Config(this.getJSONFromSocket());
        this.notifyCommandStatus(true);

        // wait for steps
        for (int i = 1; i <= config.num_steps; i++) {
            switch (data_in.readInt()) {
                case CMD_PUSH_STEP:
                    break;
                case CMD_SHUTDOWN:
                    return 0;
                default:
                    throw new IOException(); // TODO: specific exception
            }

            Log.i(LOG_TAG, "Checking step " + i + "...");
            this.modelState.postLogMessage("Receiving step " + i + "...");

            final JSONObject step_metadata = this.getJSONFromSocket();
            final int index = step_metadata.getInt(ControlConst.STEP_METADATA_INDEX);
            final int size = step_metadata.getInt(ControlConst.STEP_METADATA_SIZE);
            final String checksum = step_metadata.getString(ControlConst.STEP_METADATA_CHKSUM);

            if (index != i) {
                // step in wrong order?
                Log.e(LOG_TAG, "Step push in wrong order. Expected " + i + ", got " + index + "!");
                throw new IOException(); // TODO: specific exception
            }

            final boolean found = this.checkStep(index, checksum);
            this.notifyCommandStatus(found);
            if (!found)
                // step was not found, download it
                this.receiveStep(index, size, checksum);
        }

        Log.i(LOG_TAG, "Got all steps.");
        this.modelState.postLogMessage("All steps ready!");

        // wait for initial NTP synchronization command
        Log.i(LOG_TAG, "Waiting for initial NTP sync command...");
        switch (data_in.readInt()) {
            case CMD_NTP_SYNC:
                break;
            case CMD_SHUTDOWN:
                return 0; // shut down gracefully
            default:
                // got an invalid command
                throw new IOException(); // TODO: specific exception
        }

        // fully configured, change state:
        Log.i(LOG_TAG, "Synchronizing clocks...");
        this.modelState.postLogMessage("Synchronizing clocks...");
        return this.stateConfiguredAndReady(config, this.ntp.sync());
    }

    private int stateConfiguredAndReady(final Config config, @NonNull INTPSync ntpsync) throws IOException, Run.RunException, ExecutionException, InterruptedException, RunStats.RunStatsException, JSONException {
        // wait for experiment start
        final DataInputStream data_in = new DataInputStream(this.socket.getInputStream());
        int run_count = 0;
        while (this.isRunning()) {
            // listen for commands
            // only valid commands at this stage are (re)sync NTP, start experiment or shutdown
            Log.i(LOG_TAG, "Waiting for experiment start...");
            this.modelState.postLogMessage("Waiting for experiment start...");
            switch (data_in.readInt()) {
                case CMD_NTP_SYNC:
                    Log.i(LOG_TAG, "Got NTP re-sync command!");
                    this.modelState.postLogMessage("Resynchronizing clocks...");
                    ntpsync = this.ntp.sync();
                    break;
                case CMD_START_EXP:
                    Log.i(LOG_TAG, "Starting experiment, run number: " + (run_count + 1));
                    this.modelState.postLogMessage("Starting experiment, run number: " + (run_count + 1));
                    if (this.runExperiment(config, ntpsync))
                        run_count++;
                    // fixme: stop
                    break;
                case CMD_SHUTDOWN:
                    break; // smooth shutdown
                default:
                    throw new IOException(); // FIXME
            }
        }

        return run_count;
    }

    private boolean runExperiment(@NonNull final Config config, @NonNull final INTPSync ntp) throws Run.RunException, InterruptedException, ExecutionException, IOException, RunStats.RunStatsException, JSONException {
        final Run current_run = new Run(config, ntp, this.modelState); // fixme: run
        current_run.execute();
        // wait for run to finish, then notify
        final DataOutputStream data_out = new DataOutputStream(this.socket.getOutputStream());
        data_out.writeInt(ControlConst.MSG_EXPERIMENT_FINISH);
        data_out.flush();

        // get stats and wait to upload them
        final JSONObject run_stats = current_run.getRunStats();
        final DataInputStream data_in = new DataInputStream(this.socket.getInputStream());
        switch (data_in.readInt()) {
            // only valid commands are "fetch stats" and shutdown
            case CMD_PULL_STATS:
                break;
            case CMD_SHUTDOWN:
                return false; // shut down gracefully
            default:
                throw new IOException(); // fixme
        }

        // upload stats and return
        Log.i(LOG_TAG, "Sending JSON data...");
        final byte[] payload = run_stats.toString().getBytes("UTF-8");
        Log.i(LOG_TAG, String.format("Payload size: %d bytes", payload.length));

        final ByteArrayOutputStream baos = new ByteArrayOutputStream();
        final DataOutputStream outStream = new DataOutputStream(baos);
        outStream.writeInt(payload.length);
        outStream.write(payload);

        data_out.write(baos.toByteArray());
        data_out.flush();

        return true;
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

    public boolean isRunning() {
        this.lock.lock();
        try {
            return this.running;
        } finally {
            this.lock.unlock();
        }
    }

    private boolean checkStep(final int index, @NonNull final String checksum) {
        String filename = ControlConst.STEP_PREFIX + index + ControlConst.STEP_SUFFIX;
        Log.i(LOG_TAG,
                String.format(Locale.ENGLISH, "Checking if %s already exists locally...", filename));
        try {
            final File step_file = this.modelState.getAppContext().getFileStreamPath(filename);
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
        this.modelState.postLogMessage("Step " + index + " not found locally, downloading copy from server...");
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
        try (FileOutputStream f_out = this.modelState.getAppContext().openFileOutput(filename, Context.MODE_PRIVATE)) {
            Log.i(LOG_TAG, String.format(Locale.ENGLISH, "Saving %s locally", filename));
            f_out.write(data);
        }
        modelState.postLogMessage("Successfully received step " + index + ".");
        this.notifyCommandStatus(true);

    }

    public void cancel() {
        // forcibly aborts execution
        this.lock.lock();
        try {
            this.running = false;
            try {
                this.socket.close();
            } catch (IOException e) {
                Log.e(LOG_TAG, "Error when closing socket!", e);
            }

            this.internal_task.cancel(true);
            this.exec.shutdownNow();
        } finally {
            this.lock.unlock();
        }
    }
}
