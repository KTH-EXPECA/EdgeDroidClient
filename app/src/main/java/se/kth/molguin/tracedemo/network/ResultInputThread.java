package se.kth.molguin.tracedemo.network;

import java.io.DataInputStream;
import java.io.IOException;
import java.net.Socket;

public class ResultInputThread extends SocketInputThread {

    ResultInputThread(Socket socket) throws IOException {
        super(socket);
    }

    @Override
    protected int processIncoming(DataInputStream socket_in) throws IOException, InterruptedException {
        // TODO: implement gabriel protocol
        return 0;
    }


}
