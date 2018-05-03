package se.kth.molguin.tracedemo.synchronization;

/*
Manuel Olguin, May 2 2018: Shamelessly pulled from
https://github.com/wshackle/ntpclient/blob/master/src/main/java/com/github/wshackle/ntpclient/NTPClient.java
Modified to suit the use cases of our application.

This is a modified version of example by Jason Mathews, MITRE Corp that was
published on https://commons.apache.org/proper/commons-net/index.html
with the Apache Commons Net software.
 */

import org.apache.commons.net.ntp.NTPUDPClient;
import org.apache.commons.net.ntp.TimeInfo;

import java.io.IOException;
import java.net.InetAddress;
import java.net.SocketException;
import java.net.UnknownHostException;

public class NTPClient implements AutoCloseable {

    private static final int NTP_TIMEOUT = 100;

    InetAddress hostAddr;
    NTPUDPClient ntpUdpClient;

    boolean sync;

    private TimeInfo timeInfo;
    private long timeInfoSetLocalTime;

    void pollNtpServer() {
        try {
            TimeInfo ti = ntpUdpClient.getTime(hostAddr);
            this.setTimeInfo(ti);
            this.sync = true;
        } catch (SocketException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
            this.close();
        }
    }


    NTPClient(String host) throws UnknownHostException, SocketException {
        this.hostAddr = InetAddress.getByName(host);
        this.ntpUdpClient = new NTPUDPClient();
        this.ntpUdpClient.setDefaultTimeout(10000);
        this.ntpUdpClient.open();
        this.ntpUdpClient.setSoTimeout(NTP_TIMEOUT);

        this.sync = false;
    }

    /**
     * Get the value of timeInfo
     *
     * @return the value of timeInfo
     */
    public TimeInfo getTimeInfo() {
        return timeInfo;
    }

    synchronized void setTimeInfo(TimeInfo timeInfo) {
        this.timeInfo = timeInfo;
        this.timeInfoSetLocalTime = System.currentTimeMillis();
    }

    /**
     * Returns milliseconds just as System.currentTimeMillis() but using the latest
     * estimate from the remote time server.
     *
     * @return the difference, measured in milliseconds, between the current time and midnight, January 1, 1970 UTC.
     */
    public long currentTimeMillis() {
        if (!this.sync)
            this.pollNtpServer();

        long diff = System.currentTimeMillis() - timeInfoSetLocalTime;
        return timeInfo.getMessage().getReceiveTimeStamp().getTime() + diff;
    }

    @Override
    public void close() {
        if (null != ntpUdpClient) {
            ntpUdpClient.close();
            ntpUdpClient = null;
        }

    }

}
