package se.kth.molguin.tracedemo.network.control;

import android.arch.lifecycle.LiveData;
import android.arch.lifecycle.MutableLiveData;
import android.content.Context;
import android.support.annotation.NonNull;

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
import java.net.UnknownHostException;
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
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.ReentrantLock;

import se.kth.molguin.tracedemo.IntegratedAsyncLog;
import se.kth.molguin.tracedemo.ShutdownMessage;
import se.kth.molguin.tracedemo.SingleLiveEvent;
import se.kth.molguin.tracedemo.network.DataIOStreams;
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

    private static class ControlException extends Exception {
        ControlException(String msg) {
            super(msg);
        }
    }

    private static class ShutdownCommandException extends Exception {
    }

    private final static String LOG_TAG = "ControlClient";

    private final ExecutorService exec;
    private final String address;
    private final int port;
    private final Context appContext;
    private final IntegratedAsyncLog log;
    private final ReentrantLock lock;

    private final MutableLiveData<byte[]> realTimeFrameFeed;
    private final MutableLiveData<byte[]> sentFrameFeed;
    private final SingleLiveEvent<ShutdownMessage> shutdownEvent;

    private final AtomicBoolean running_flag;
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
        } catch (NoSuchAlgorithmException ignored) {
        }
        return null;
    }

    /**
     * Constructs the Control client.
     *
     * @param address Host address to connect to.
     * @param port    TCP port on the host to connect to.
     */
    ControlClient(final String address, final int port, final Context appContext, final IntegratedAsyncLog log) {
        this.address = address;
        this.port = port;
        this.exec = Executors.newSingleThreadExecutor();
        this.lock = new ReentrantLock();
        this.appContext = appContext;
        this.log = log;

        this.running_flag = new AtomicBoolean(false);

        this.realTimeFrameFeed = new MutableLiveData<>();
        this.sentFrameFeed = new MutableLiveData<>();
        this.shutdownEvent = new SingleLiveEvent<>();

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
    public ControlClient(final Context appContext, final IntegratedAsyncLog log) {
        this(ControlConst.SERVER, ControlConst.CONTROL_PORT, appContext, log);
    }

    public LiveData<byte[]> getRealTimeFrameFeed() {
        return this.realTimeFrameFeed;
    }

    public LiveData<byte[]> getSentFrameFeed() {
        return this.sentFrameFeed;
    }

    public LiveData<ShutdownMessage> getShutdownEvent() {
        return this.shutdownEvent;
    }

    /**
     * Initializes the internal listener thread.
     */
    public void init() {
        this.running_flag.set(true);
        this.log.i(LOG_TAG, "Initializing...");
        this.internal_task = this.exec.submit(new Runnable() {
            @Override
            public void run() {
                int total_runs = 0;
                int successful_runs = 0; // TODO: use this value in the future

                // preset failure values to use in case of an exception
                boolean success = false;
                String msg = "";

                // try-with-resources to automagically close the socket and the streams
                // and yes, automagically IS a word...
                try (
                        final Socket socket = connectToControl();
                        final DataIOStreams ioStreams = new DataIOStreams(
                                socket.getInputStream(), socket.getOutputStream())
                ) {
                    try {
                        // first, configure the experiment
                        final Config config = configure(ioStreams);

                        // initialize the ntp client
                        try (final NTPClient ntp = new NTPClient(config.ntp_host, log)) {
                            // actual experiment loop here
                            while (running_flag.get()) {
                                try {
                                    // wait for NTP sync
                                    final INTPSync sync = ntpSync(ioStreams, ntp);
                                    // wait for experiment start
                                    if (runExperiment(config, sync, ioStreams))
                                        successful_runs++;
                                    total_runs++;
                                } catch (ShutdownCommandException e) {
                                    // if we get here we got a shutdown command from control
                                    // here, it is a clean and expected shutdown command
                                    // so we just exit the loop cleanly
                                    log.i(LOG_TAG, "Got shutdown command!");
                                    success = true;
                                    msg = "";

                                    // exit the loop:
                                    running_flag.set(false);
                                    break;
                                }
                            }
                        } catch (SocketException e) {
                            msg = "Error polling time server!";
                            success = false;
                            notifyCommandStatus(ioStreams, false);
                        } catch (UnknownHostException e) {
                            msg = "Could not resolve NTP host address!";
                            success = false;
                            notifyCommandStatus(ioStreams, false);
                        }
                    } catch (JSONException e) {
                        // error receiving data from control
                        msg = "Error while parsing data from Control Server!";
                        log.e(LOG_TAG, msg, e);
                        notifyCommandStatus(ioStreams, false);
                    } catch (ExecutionException e) {
                        // socket connection error (backend)
                        msg = "Error while trying to connect to the application backend!";
                        log.e(LOG_TAG, msg, e);
                        notifyCommandStatus(ioStreams, false);
                    } catch (ControlException e) {
                        msg = "Unexpected control command!";
                        log.e(LOG_TAG, msg, e);
                        notifyCommandStatus(ioStreams, false);
                    } catch (InterruptedException ignored) {
                        msg = "Interrupted";
                        log.w(LOG_TAG, msg);
                    } catch (RunStats.RunStatsException e) {
                        // error while executing run
                        msg = "Error while recording stats for experiment!";
                        log.e(LOG_TAG, msg, e);
                        notifyCommandStatus(ioStreams, false);
                    } catch (ShutdownCommandException e) {
                        // not an error per se, just a premature shutdown request
                        msg = "Got premature shutdown command from Control!";
                        log.e(LOG_TAG, msg);
                        notifyCommandStatus(ioStreams, true); // true because we shut down
                    }
                } catch (IOException e) {
                    msg = "Error trying to connect to Control Server!";
                    log.e(LOG_TAG, msg, e);
                } catch (Exception e) {
                    msg = "Unexpected, unhandled exception!";
                    log.e(LOG_TAG, msg, e);
                    throw e;
                } finally {
                    log.w(LOG_TAG, "Shutting down...");
                    // shut down
                    running_flag.set(false);
                    // done, now notify UI!
                    shutdownEvent.postValue(new ShutdownMessage(success, total_runs, msg));
                }
            }
        });
    }

    private static byte[] readPayloadFromRemote(DataInputStream dataIn) throws IOException {
        final int length = dataIn.readInt();
        final byte[] payload = new byte[length];
        dataIn.readFully(payload);
        return payload;
    }

    private static JSONObject readJSONFromRemote(DataInputStream dataIn) throws IOException, JSONException {
        return new JSONObject(new String(readPayloadFromRemote(dataIn), "UTF-8"));
    }

    /**
     * Connects to the Control server.
     *
     * @throws IOException In case something goes wrong connecting.
     */
    private Socket connectToControl() throws IOException {
        this.log.i(LOG_TAG, String.format(Locale.ENGLISH, "Connecting to Control Server at %s:%d",
                this.address, this.port));

        final Socket socket = new Socket();
        while (this.running_flag.get()) {
            try {
                socket.setTcpNoDelay(true);
                socket.connect(new InetSocketAddress(this.address, this.port), 100);
                break;
            } catch (SocketTimeoutException e) {
                this.log.i(LOG_TAG, "Timeout - retrying...");
            } catch (ConnectException e) {
                this.log.w(LOG_TAG, "Connection exception! Retrying...", e);
            }
        }
        this.log.i(LOG_TAG, String.format(Locale.ENGLISH, "Connected to Control Server at %s:%d", address, port));

        // return the new, connected socket
        return socket;
    }

    private Config configure(@NonNull DataIOStreams ioStreams) throws IOException, JSONException, ControlException, ShutdownCommandException {

        // wait for config message
        switch (ioStreams.readInt()) {
            case CMD_PUSH_CONFIG:
                break;
            case CMD_SHUTDOWN:
                throw new ShutdownCommandException();
            default:
                throw new ControlException("Unexpected command from Control!");
        }

        this.log.i(LOG_TAG, "Receiving experiment configuration...");

        final Config config = new Config(readJSONFromRemote(ioStreams.getDataInputStream()));
        this.notifyCommandStatus(ioStreams, true);

        // wait for steps
        for (int i = 1; i <= config.num_steps; i++) {
            switch (ioStreams.readInt()) {
                case CMD_PUSH_STEP:
                    break;
                case CMD_SHUTDOWN:
                    throw new ShutdownCommandException();
                default:
                    throw new ControlException("Unexpected command from Control!");
            }

            this.log.i(LOG_TAG, "Checking step " + i + "...");

            final JSONObject step_metadata = readJSONFromRemote(ioStreams.getDataInputStream());
            final int index = step_metadata.getInt(ControlConst.STEP_METADATA_INDEX);
            final int size = step_metadata.getInt(ControlConst.STEP_METADATA_SIZE);
            final String checksum = step_metadata.getString(ControlConst.STEP_METADATA_CHKSUM);

            if (index != i) {
                // step in wrong order?
                this.log.e(LOG_TAG, "Step push in wrong order. Expected " + i + ", got " + index + "!");
                throw new ControlException("Received step in wrong order!");
            }

            final boolean found = this.checkStep(index, checksum);
            this.notifyCommandStatus(ioStreams, found);
            if (!found)
                // step was not found, download it
                this.receiveStep(index, size, checksum, ioStreams);
        }

        this.log.i(LOG_TAG, "Got all steps -- fully configured for experiment!");
        return config;
    }

    private INTPSync ntpSync(@NonNull DataIOStreams ioStreams,
                             @NonNull NTPClient ntp) throws IOException, ShutdownCommandException, ControlException {
        // wait for initial NTP synchronization command
        this.log.i(LOG_TAG, "Waiting for NTP sync command...");
        switch (ioStreams.readInt()) {
            case CMD_NTP_SYNC:
                break;
            case CMD_SHUTDOWN:
                throw new ShutdownCommandException(); // shut down gracefully
            default:
                // got an invalid command
                throw new ControlException("Unexpected command from Control!");
        }

        this.log.i(LOG_TAG, "Synchronizing clocks...");
        final INTPSync ntpSync = ntp.sync();
        this.notifyCommandStatus(ioStreams, true);

        return ntpSync;
    }


    private boolean runExperiment(@NonNull Config config, @NonNull INTPSync ntpsync,
                                  @NonNull DataIOStreams ioStreams)
            throws ShutdownCommandException, ControlException, InterruptedException, ExecutionException, IOException, RunStats.RunStatsException, JSONException {
        // wait for experiment start
        // listen for commands
        // only valid commands at this stage are start experiment or shutdown
        this.log.i(LOG_TAG, "Waiting for experiment start...");
        switch (ioStreams.readInt()) {
            case CMD_START_EXP:
                this.log.i(LOG_TAG, "Starting experiment...");
                break;
            case CMD_SHUTDOWN:
                throw new ShutdownCommandException(); // smooth shutdown
            default:
                throw new ControlException("Unexpected command from Control!");
        }

        // notify that we are going to start before executing
        this.notifyCommandStatus(ioStreams, true);

        // run experiment here
        final Run current_run = new Run(config, ntpsync, this.appContext,
                this.log, this.realTimeFrameFeed, this.sentFrameFeed);
        current_run.executeAndWait();

        // wait for run to finish, then notify
        ioStreams.writeInt(ControlConst.MSG_EXPERIMENT_FINISH);
        ioStreams.flush();

        // get stats and wait to upload them
        final JSONObject run_stats = current_run.getRunStats();
        switch (ioStreams.readInt()) {
            // only valid commands are "fetch stats" and shutdown
            case CMD_PULL_STATS:
                break;
            case CMD_SHUTDOWN:
                throw new ShutdownCommandException(); // shut down gracefully
            default:
                throw new ControlException("Unexpected command from Control!");
        }

        // upload stats and return
        this.log.i(LOG_TAG, "Sending JSON data...");
        final byte[] payload = run_stats.toString().getBytes("UTF-8");
        this.log.i(LOG_TAG, String.format(Locale.ENGLISH, "Payload size: %d bytes", payload.length));

        final ByteArrayOutputStream baos = new ByteArrayOutputStream();
        final DataOutputStream outStream = new DataOutputStream(baos);
        outStream.writeInt(payload.length);
        outStream.write(payload);

        ioStreams.write(baos.toByteArray());
        ioStreams.flush();

        return current_run.succeeded();

    }


    /**
     * Notifies the ControlServer of the status of a recent command.
     *
     * @param success Success status of the command.
     */
    private void notifyCommandStatus(@NonNull DataIOStreams ioStreams, boolean success) {
        int status = success ? STATUS_SUCCESS : STATUS_ERROR;
        try {
            ioStreams.writeInt(status);
            ioStreams.flush();
        } catch (SocketException e) {
            this.log.w(LOG_TAG, "Socket closed!");
            this.log.e(LOG_TAG, "Exception!", e);
        } catch (IOException e) {
            this.log.e(LOG_TAG, "Exception!", e);
            exit(-1);
        }
    }

    private boolean checkStep(final int index, @NonNull final String checksum) {
        String filename = ControlConst.STEP_PREFIX + index + ControlConst.STEP_SUFFIX;
        this.log.i(LOG_TAG,
                String.format(Locale.ENGLISH, "Checking if %s already exists locally...", filename));
        try {
            final File step_file = this.appContext.getFileStreamPath(filename);
            final byte[] data = new byte[(int) step_file.length()];
            try (FileInputStream f_in = new FileInputStream(step_file)) {
                if (step_file.length() != f_in.read(data)) throw new IOException();
            }

            String local_chksum = getMD5Hex(data);
            String remote_chksum = checksum.toUpperCase(Locale.ENGLISH);

            if (!Objects.equals(local_chksum, remote_chksum)) {
                this.log.w(LOG_TAG, String.format(
                        Locale.ENGLISH,
                        "%s found but MD5 checksums do not match.",
                        filename));
                this.log.w(LOG_TAG,
                        String.format(
                                Locale.ENGLISH,
                                "Remote: %s\tLocal: %s",
                                remote_chksum, local_chksum
                        ));
                return false;
            }

            this.log.i(LOG_TAG, String.format(Locale.ENGLISH, "%s found locally!", filename));
            return true;

        } catch (FileNotFoundException e) {
            this.log.w(LOG_TAG,
                    String.format(Locale.ENGLISH, "%s was not found locally!", filename));
            return false;
        } catch (IOException e) {
            this.log.w(LOG_TAG,
                    String.format(Locale.ENGLISH, "Error trying to read %s.", filename));
            return false;
        }
    }

    private void receiveStep(int index, int size, @NonNull String checksum, @NonNull DataIOStreams ioStreams) throws IOException, ControlException {
        // step not found locally
        this.log.i(LOG_TAG, "Step " + index + " not found locally, downloading copy from server...");
        final String filename = ControlConst.STEP_PREFIX + index + ControlConst.STEP_SUFFIX;
        // receive step from Control

        this.log.i(LOG_TAG, String.format(Locale.ENGLISH, "Receiving step %s from Control. Total size: %d bytes", filename, size));
        byte[] data = new byte[size];
        ioStreams.readFully(data);

        this.log.i(LOG_TAG, String.format(Locale.ENGLISH, "Received %s from Control.", filename));

        // verify checksums match before saving it
        final String recv_md5 = getMD5Hex(data);
        final String prev_checksum = checksum.toUpperCase(Locale.ENGLISH);
        // this.log.i(LOG_TAG, String.format(Locale.ENGLISH, "Checksums - remote: %s\tlocal: %s", prev_checksum, recv_md5));

        if (!Objects.equals(recv_md5, prev_checksum)) {
            this.log.e(LOG_TAG, String.format(Locale.ENGLISH, "Received step %s correctly, but MD5 checksums do not match!", filename));
            this.log.e(LOG_TAG, String.format(Locale.ENGLISH, "Expected: %s\nReceived: %s", prev_checksum, recv_md5));
            throw new ControlException("Checksum for step " + index + " does not match!");
        }

        // checksums match, so save it
        try (FileOutputStream f_out = this.appContext.openFileOutput(filename, Context.MODE_PRIVATE)) {
            // this.log.i(LOG_TAG, String.format(Locale.ENGLISH, "Saving %s locally", filename));
            f_out.write(data);
        }
        this.log.i(LOG_TAG, "Successfully received step " + index + ".");
        this.notifyCommandStatus(ioStreams, true);

    }

    public void cancel() {
        // forcibly aborts execution
        this.log.w(LOG_TAG, "cancel() called!");
        this.lock.lock();
        try {
            this.running_flag.set(false);
            this.internal_task.cancel(true);
            this.exec.shutdownNow();
        } finally {
            this.lock.unlock();
        }
    }
}
