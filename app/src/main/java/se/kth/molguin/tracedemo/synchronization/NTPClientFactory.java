package se.kth.molguin.tracedemo.synchronization;

import java.net.SocketException;
import java.net.UnknownHostException;
import java.util.HashMap;

public class NTPClientFactory {
    private static HashMap<String, NTPClient> ntp_clients = null;

    public static NTPClient getNTPClient(String host) throws SocketException, UnknownHostException {
        if (null == ntp_clients)
            ntp_clients = new HashMap<>();

        NTPClient client = ntp_clients.get(host);
        if (null == client)
        {
            client = new NTPClient(host);
            ntp_clients.put(host, client);
        }

        return client;
    }
}
