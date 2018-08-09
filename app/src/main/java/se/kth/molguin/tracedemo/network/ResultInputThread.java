package se.kth.molguin.tracedemo.network;

import android.support.annotation.NonNull;
import android.util.Log;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.DataInputStream;
import java.io.IOException;
import java.net.Socket;

import se.kth.molguin.tracedemo.network.control.experiment.run.Run;
import se.kth.molguin.tracedemo.network.control.experiment.run.RunStats;
import se.kth.molguin.tracedemo.network.gabriel.ProtocolConst;
import se.kth.molguin.tracedemo.network.gabriel.TokenPool;

public class ResultInputThread extends SocketInputThread {

    private static final String LOG_TAG = "SocketInputThread";
    private final TokenPool tokenPool;
    private final RunStats stats;
    private final Run run;

    public ResultInputThread(@NonNull Run run, @NonNull RunStats stats, @NonNull Socket socket,
                             @NonNull TokenPool tokenPool) {
        super(socket);
        this.run = run;
        this.tokenPool = tokenPool;
        this.stats = stats;
    }

    @Override
    protected int processIncoming(DataInputStream socket_in) throws IOException {
        int total_read = 0;

        Log.d(LOG_TAG, "Wait for incoming messages...");

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
        Log.d(LOG_TAG, "Got incoming message, size: " + len);

        // parse the string into a JSON
        String status;
        JSONObject result;
        long frameID;
        int state_index;

        try {
            JSONObject msg = new JSONObject(msg_s);
            status = msg.getString(ProtocolConst.HEADER_MESSAGE_STATUS);
            result = msg.getJSONObject(ProtocolConst.HEADER_MESSAGE_RESULT);
            state_index = result.getInt("state_index");
            //String sensorType = msg.getString(ProtocolConst.SENSOR_TYPE_KEY);
            frameID = msg.getLong(ProtocolConst.HEADER_MESSAGE_FRAME_ID);
            //String engineID = msg.getString(ProtocolConst.HEADER_MESSAGE_ENGINE_ID);
        } catch (JSONException e) {
            Log.w(LOG_TAG, "Received message is not valid Gabriel message.");
            return total_read;
        }

        boolean feedback = status.equals(ProtocolConst.STATUS_SUCCESS);

        if (feedback) {
            // differentiate different types of messages
            if (state_index >= 0)
                this.run.stepUpdate(state_index); // success
            else
                this.run.incrementErrorCount(); // error
        }

        // we got a valid message, give back a token
        this.tokenPool.putToken();
        this.stats.registerReceivedFrame((int) frameID, feedback);
        return total_read; // return number of read bytes
    }


}
