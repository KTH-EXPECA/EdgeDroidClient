package se.kth.molguin.tracedemo.network;

import android.util.Log;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.DataInputStream;
import java.io.IOException;
import java.net.Socket;

import se.kth.molguin.tracedemo.network.gabriel.ProtocolConst;
import se.kth.molguin.tracedemo.network.gabriel.TokenManager;

public class ResultInputThread extends SocketInputThread {

    TokenManager tkman;

    public ResultInputThread(Socket socket, TokenManager tkman) throws IOException {
        super(socket);
        this.tkman = tkman;
    }

    @Override
    protected int processIncoming(DataInputStream socket_in) throws IOException, InterruptedException {
        // TODO: TOKENS TOKENS token
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
        String status = null;
        String result = null;
        String sensorType = null;
        long frameID = -1;
        String engineID = "";
        try {
            JSONObject msg = new JSONObject(msg_s);
            status = msg.getString(ProtocolConst.HEADER_MESSAGE_STATUS);
            result = msg.getString(ProtocolConst.HEADER_MESSAGE_RESULT);
            sensorType = msg.getString(ProtocolConst.SENSOR_TYPE_KEY);
            frameID = msg.getLong(ProtocolConst.HEADER_MESSAGE_FRAME_ID);
            engineID = msg.getString(ProtocolConst.HEADER_MESSAGE_ENGINE_ID);
        } catch (JSONException e) {
            Log.w(this.getClass().getSimpleName(), "Received message is not valid Gabriel message.");
            return total_read;
        }

        // we got a valid message, give back a token
        tkman.putToken();

        if (!status.equals(ProtocolConst.STATUS_SUCCESS))
            return total_read;

        // TODO notify success

        return total_read; // return number of read bytes
    }


}
