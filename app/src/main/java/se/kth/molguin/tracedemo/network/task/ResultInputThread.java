package se.kth.molguin.tracedemo.network.task;

import android.support.annotation.NonNull;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.DataInputStream;
import java.io.IOException;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

import se.kth.molguin.tracedemo.IntegratedAsyncLog;
import se.kth.molguin.tracedemo.network.control.experiment.run.RunStats;
import se.kth.molguin.tracedemo.network.gabriel.ProtocolConst;
import se.kth.molguin.tracedemo.network.gabriel.TokenPool;

public class ResultInputThread {

    private static final String LOG_TAG = "ResultInput";

    private final TokenPool tokenPool;
    private final RunStats stats;
    private final IntegratedAsyncLog log;
    private final DataInputStream dataIn;
    private final Callable<Integer> changeStepCallback;
    private final ExecutorService execs;

    private final AtomicBoolean running_flag;

    public ResultInputThread(@NonNull RunStats stats,
                             @NonNull DataInputStream dataIn,
                             @NonNull TokenPool tokenPool,
                             @NonNull IntegratedAsyncLog log,
                             @NonNull Callable<Integer> changeStepCallback) {
        this.changeStepCallback = changeStepCallback;
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
            public Void call() throws Exception {

                try {

                    while (running_flag.get()) {
                        int total_read = 0;

                        // get incoming message size:
                        int len = dataIn.readInt();
                        total_read += 4;

                        byte[] msg_b = new byte[len];

                        // read the message into a string
                        int readSize = 0;
                        while (readSize < len) {
                            int ret = dataIn.read(msg_b, readSize, len - readSize);
                            if (ret <= 0) {
                                throw new IOException();
                            }
                            readSize += ret;
                        }
                        total_read += len;

                        String msg_s = new String(msg_b, "UTF-8");

                        // parse the string into a JSON
                        String status;
                        JSONObject result;
                        long frameID;
                        int state_index;
                        boolean feedback;

                        try {
                            JSONObject msg = new JSONObject(msg_s);
                            status = msg.getString(ProtocolConst.HEADER_MESSAGE_STATUS);
                            result = new JSONObject(msg.getString(ProtocolConst.HEADER_MESSAGE_RESULT));

                            feedback = status.equals(ProtocolConst.STATUS_SUCCESS);
                            if (feedback)
                                state_index = result.getInt("state_index");
                            else state_index = -1;
                            //String sensorType = msg.getString(ProtocolConst.SENSOR_TYPE_KEY);
                            frameID = msg.getLong(ProtocolConst.HEADER_MESSAGE_FRAME_ID);
                            //String engineID = msg.getString(ProtocolConst.HEADER_MESSAGE_ENGINE_ID);
                        } catch (JSONException e) {
                            log.w(LOG_TAG, "Received message is not valid Gabriel message.", e);
                            return null;
                        }

                        if (feedback) {
                            // differentiate different types of messages
                            if (state_index >= 0)
                                changeStepCallback.
                            else ;
                            //TODO: Do something in case of error ¯\_(ツ)_/¯
                        }

                        // we got a valid message, give back a token
                        this.tokenPool.putToken();
                        this.stats.registerReceivedFrame((int) frameID, feedback);
                        return total_read; // return number of read bytes
                    }

                } finally {

                }


                return null
            }
        });

    }
}
