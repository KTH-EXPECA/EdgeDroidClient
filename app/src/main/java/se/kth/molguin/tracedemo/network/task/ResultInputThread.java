package se.kth.molguin.tracedemo.network.task;

import android.support.annotation.NonNull;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.DataInputStream;
import java.io.IOException;
import java.net.Socket;

import se.kth.molguin.tracedemo.IntegratedAsyncLog;
import se.kth.molguin.tracedemo.network.control.experiment.run.RunStats;
import se.kth.molguin.tracedemo.network.gabriel.ProtocolConst;
import se.kth.molguin.tracedemo.network.gabriel.TokenPool;

public class ResultInputThread extends SocketInputThread {

    private static final String LOG_TAG = "SocketInputThread";
    private final TokenPool tokenPool;
    private final RunStats stats;
    private final VideoOutputThread videout;
    private final IntegratedAsyncLog log;

    public ResultInputThread(@NonNull VideoOutputThread videout,
                             @NonNull RunStats stats,
                             @NonNull Socket socket,
                             @NonNull TokenPool tokenPool,
                             @NonNull IntegratedAsyncLog log) {
        super(socket);
        this.videout = videout;
        this.tokenPool = tokenPool;
        this.stats = stats;
        this.log = log;
    }

    @Override
    protected int processIncoming(DataInputStream socket_in) throws IOException {
        int total_read = 0;

        // get incoming message size:
        int len = socket_in.readInt();
        total_read += 4;

        byte[] msg_b = new byte[len];

        // read the message into a string
        int readSize = 0;
        while (readSize < len) {
            int ret = socket_in.read(msg_b, readSize, len - readSize);
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
            this.log.w(LOG_TAG, "Received message is not valid Gabriel message.", e);
            return total_read;
        }

        if (feedback) {
            // differentiate different types of messages
            if (state_index >= 0)
                this.videout.goToStep(state_index);
            else ;
            //TODO: Do something in case of error ¯\_(ツ)_/¯
        }

        // we got a valid message, give back a token
        this.tokenPool.putToken();
        this.stats.registerReceivedFrame((int) frameID, feedback);
        return total_read; // return number of read bytes
    }


}
