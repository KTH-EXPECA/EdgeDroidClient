package se.kth.molguin.tracedemo.network.task;

import android.arch.core.util.Function;
import android.support.annotation.NonNull;
import android.util.Log;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.DataInputStream;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

import se.kth.molguin.tracedemo.IntegratedAsyncLog;
import se.kth.molguin.tracedemo.network.control.experiment.run.RunStats;
import se.kth.molguin.tracedemo.network.gabriel.ProtocolConst;
import se.kth.molguin.tracedemo.network.gabriel.TokenPool;

public class ResultInput {

    private static final String LOG_TAG = "ResultInput";

    private final TokenPool tokenPool;
    private final RunStats stats;
    private final IntegratedAsyncLog log;
    private final DataInputStream dataIn;
    private final ExecutorService execs;
    private final Function<Integer, Void> callback;

    private final AtomicBoolean running_flag;

    public ResultInput(@NonNull RunStats stats,
                       @NonNull DataInputStream dataIn,
                       @NonNull TokenPool tokenPool,
                       @NonNull IntegratedAsyncLog log,
                       @NonNull Function<Integer, Void> changeStepCallback) {
        this.callback = changeStepCallback;
        this.tokenPool = tokenPool;
        this.stats = stats;
        this.log = log;
        this.dataIn = dataIn;

        this.running_flag = new AtomicBoolean(false);

        this.execs = Executors.newSingleThreadExecutor();
    }

    public void startListening() {

        this.execs.submit(new Callable<Void>() {
            @Override
            public Void call() {

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

                            if (feedback && state_index >= 0) {
                                // differentiate different types of messages
                                callback.apply(state_index);
                                //TODO: Do something in case of error (state index < 0) ¯\_(ツ)_/¯
                            }

                            // we got a valid message, give back a token
                            tokenPool.putToken();
                            stats.registerReceivedFrame((int) frameID, feedback);

                        } catch (JSONException e) {
                            log.w(LOG_TAG, "Received message is not valid Gabriel message.", e);
                        }
                    }
                } catch (InterruptedException ignored) {
                    // finish smoothly
                } catch (UnsupportedEncodingException e) {
                    // This should never happen??
                    Log.e(LOG_TAG, "Impossible exception!", e);
                } catch (IOException e) {
                    // Socket closed...
                    // if running flag is set, this means an error.
                    // otherwise it's just part of the shutdown operation
                    if (running_flag.get())
                        log.e(LOG_TAG, "Socket prematurely closed?", e);
                }
                return null;
            }
        });

    }
}
