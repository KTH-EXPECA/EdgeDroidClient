package se.kth.molguin.tracedemo.network.control.experiment.run;

import android.arch.lifecycle.MutableLiveData;
import android.content.Context;
import android.support.annotation.NonNull;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.util.Locale;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import se.kth.molguin.tracedemo.IntegratedAsyncLog;
import se.kth.molguin.tracedemo.network.control.experiment.Config;
import se.kth.molguin.tracedemo.network.control.experiment.Sockets;
import se.kth.molguin.tracedemo.network.gabriel.ProtocolConst;
import se.kth.molguin.tracedemo.network.gabriel.TokenPool;
import se.kth.molguin.tracedemo.network.task.SynchronizedBuffer;
import se.kth.molguin.tracedemo.network.task.TaskStep;
import se.kth.molguin.tracedemo.synchronization.INTPSync;

public class Run {
    private static final String LOG_TAG = "ExperimentRun";
    private final IntegratedAsyncLog log;

    private final SynchronizedBuffer<byte[]> frame_buffer;
    private final AtomicBoolean running_flag;
    private final AtomicBoolean task_success;
    private final Config config;
    private final TokenPool tokenPool;
    private final RunStats stats;
    private final Context appContext;

    private final MutableLiveData<byte[]> sentframe_feed;
    private final MutableLiveData<byte[]> rtframe_feed;

    // mutable state:
    private final AtomicInteger frame_counter;
    private final AtomicInteger current_step_idx;

    private final Lock step_lock;
    // locking primitives to notify end of stream!
    private final ExecutorService execs;

    private TaskStep current_step;

    public Run(@NonNull final Config config,
               @NonNull final INTPSync ntp,
               @NonNull final Context appContext,
               @NonNull final IntegratedAsyncLog log,
               @NonNull final MutableLiveData<byte[]> rtframe_feed,
               @NonNull final MutableLiveData<byte[]> sentframe_feed)
            throws InterruptedException {

        this.log = log;
        this.appContext = appContext;
        this.config = config;

        this.sentframe_feed = sentframe_feed;
        this.rtframe_feed = rtframe_feed;

        this.log.i(LOG_TAG, "Initiating new Experiment Run");
        this.execs = Executors.newCachedThreadPool();
        this.stats = new RunStats(ntp);
        this.tokenPool = new TokenPool(this.log);

        this.frame_buffer = new SynchronizedBuffer<>();
        this.running_flag = new AtomicBoolean(false);
        this.task_success = new AtomicBoolean(false);
        this.frame_counter = new AtomicInteger(0);
        this.current_step_idx = new AtomicInteger(-1);

        this.step_lock = new ReentrantLock();
    }

    public void executeAndWait() {

        try (
                final Sockets sockets = new Sockets(this.config);
                final DataInputStream dataIn = new DataInputStream(sockets.result.getInputStream());
                final DataOutputStream dataOut = new DataOutputStream(sockets.video.getOutputStream());
        ) {

            this.running_flag.set(true);

            final Future streamTask = this.execs.submit(new Runnable() {
                @Override
                public void run() {
                    stream(dataOut);
                }
            });

            final Future listenTask = this.execs.submit(new Runnable() {
                @Override
                public void run() {
                    listen(dataIn);
                }
            });


            // wait for task completion...
            streamTask.get();
            listenTask.get();

        } catch (InterruptedException e) {
            e.printStackTrace();
        } catch (ExecutionException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }


    }

    public JSONObject getRunStats() throws RunStats.RunStatsException, JSONException {
        return this.stats.toJSON();
    }

    public boolean succeeded() {
        return this.stats.succeeded();
    }

    private void stream(DataOutputStream dataOut) {
        try {
            if (!running_flag.get())
                return;

            log.i(LOG_TAG, "Starting stream...");
            step_lock.lock();
            try {
                if (current_step == null) {
                    current_step_idx.set(-1);
                    goToStep(0);
                }

                current_step.start();
            } finally {
                step_lock.unlock();
            }

            while (running_flag.get()) {

                // get a token
                tokenPool.getToken();
                // got a token
                // now get a frame to send
                final byte[] frame_data = frame_buffer.pop();
                final int current_frame_id = frame_counter.incrementAndGet();

                sendFrame(dataOut, current_frame_id, frame_data);

                this.stats.registerSentFrame(current_frame_id);
                this.sentframe_feed.postValue(frame_data);
            }

        } catch (InterruptedException ignored) {
        } catch (IOException e) {
            log.e(LOG_TAG, "Exception in VideoOutput", e);
        } finally {
            step_lock.lock();
            try {
                if (current_step != null)
                    current_step.stop();
            } finally {
                step_lock.unlock();
            }

            stats.finish(task_success.get());
            final String status_msg = task_success.get() ? "Success" : "Failure";
            log.i(LOG_TAG, "Stream finished. Status: " + status_msg);
        }
    }

    private void listen(DataInputStream dataIn) {
        try {
            while (running_flag.get()) {

                // get incoming message size:
                final int len = dataIn.readInt();
                final byte[] msg_b = new byte[len];
                dataIn.readFully(msg_b);

                // read the message into a string
//                        int readSize = 0;
//                        while (readSize < len) {
//                            int ret = dataIn.read(msg_b, readSize, len - readSize);
//                            if (ret <= 0) {
//                                throw new IOException();
//                            }
//                            readSize += ret;
//                        }

                final String msg_s = new String(msg_b, "UTF-8");

                try {
                    // parse the string into a JSON
                    final JSONObject msg = new JSONObject(msg_s);
                    final String status = msg.getString(ProtocolConst.HEADER_MESSAGE_STATUS);
                    final JSONObject result = new JSONObject(msg.getString(ProtocolConst.HEADER_MESSAGE_RESULT));

                    final boolean feedback = status.equals(ProtocolConst.STATUS_SUCCESS);
                    final int state_index = feedback ? result.getInt(ProtocolConst.HEADER_MESSAGE_STATE_IDX) : -1;
                    final long frameID = msg.getLong(ProtocolConst.HEADER_MESSAGE_FRAME_ID);

                    try {
                        if (feedback && state_index >= 0) {
                            // differentiate different types of messages
                            this.changeStep(state_index);
                            //TODO: Do something in case of error (state index < 0) ¯\_(ツ)_/¯
                        }
                    } finally {
                        // we got a valid message, give back a token
                        tokenPool.putToken();
                        stats.registerReceivedFrame((int) frameID, feedback);
                    }

                } catch (JSONException e) {
                    log.w(LOG_TAG, "Received message is not valid Gabriel message.", e);
                }
            }
        } catch (ShutdownException e) {
            // shutdown smoothly
        } catch (InterruptedException ignored) {
            // finish smoothly
        } catch (UnsupportedEncodingException e) {
            // This should never happen??
            this.log.e(LOG_TAG, "Impossible exception!", e);
        } catch (IOException e) {
            // Socket closed...
            this.log.e(LOG_TAG, "Input socket prematurely closed!", e);
        }
    }

    private static void sendFrame(DataOutputStream dataOut, int id, byte[] data) throws IOException {
        byte[] header = String.format(Locale.ENGLISH, ProtocolConst.VIDEO_HEADER_FMT, id).getBytes();

        try (// use auxiliary output streams to write everything out at once
             ByteArrayOutputStream baos = new ByteArrayOutputStream();
             DataOutputStream daos = new DataOutputStream(baos)
        ) {
            daos.writeInt(header.length);
            daos.write(header);
            daos.writeInt(data.length);
            daos.write(data);

            byte[] out_data = baos.toByteArray();
            dataOut.write(out_data); // send!
            dataOut.flush();
        }
    }

    private void changeStep(int new_step_idx) throws ShutdownException {

    }

    private static class ShutdownException extends Exception {
    }
}
