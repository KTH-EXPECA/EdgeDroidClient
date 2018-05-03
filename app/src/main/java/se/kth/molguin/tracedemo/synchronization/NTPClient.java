package se.kth.molguin.tracedemo.synchronization;

/*
Manuel Olguin, May 2 2018: Shamelessly pulled from
https://github.com/wshackle/ntpclient/blob/master/src/main/java/com/github/wshackle/ntpclient/NTPClient.java
Modified to suit the use cases of our application.

This is a modified version of example by Jason Mathews, MITRE Corp that was
published on https://commons.apache.org/proper/commons-net/index.html
with the Apache Commons Net software.
 */

import android.util.Log;

import org.apache.commons.net.ntp.NTPUDPClient;
import org.apache.commons.net.ntp.TimeInfo;

import java.io.IOException;
import java.net.InetAddress;
import java.net.SocketException;
import java.net.UnknownHostException;

import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

public class NTPClient implements AutoCloseable {

    private static final int NTP_TIMEOUT = 100;
    private static final String LOG_TAG = "NTPClient";
    private ReadWriteLock lock;

    private InetAddress hostAddr;
    NTPUDPClient ntpUdpClient;

    private TimeInfo timeInfo;

    public void pollNtpServer() {
        try {
            TimeInfo ti = ntpUdpClient.getTime(hostAddr);
            ti.computeDetails();

            this.setTimeInfo(ti);
            Log.i(LOG_TAG, "Polled " + this.hostAddr.toString());
            Log.i(LOG_TAG, "Local time: " + System.currentTimeMillis());
            Log.i(LOG_TAG, "Server time: " + this.currentTimeMillis());
            Log.i(LOG_TAG, String.format(
                    "Offset: %d\tDelay: %d",
                    ti.getOffset(),
                    ti.getDelay()
            ));
        } catch (SocketException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
            this.close();
        }
    }


    public NTPClient(String host) throws UnknownHostException, SocketException {
        this.hostAddr = InetAddress.getByName(host);
        this.ntpUdpClient = new NTPUDPClient();
        this.ntpUdpClient.setDefaultTimeout(10000);
        this.ntpUdpClient.open();
        this.ntpUdpClient.setSoTimeout(NTP_TIMEOUT);
        this.timeInfo = null;

        this.lock = new ReentrantReadWriteLock();

        this.pollNtpServer();
    }

    /**
     * Get the value of timeInfo
     *
     * @return the value of timeInfo
     */
    public TimeInfo getTimeInfo() {
        this.lock.readLock().lock();
        if (null == this.timeInfo) {
            this.lock.readLock().unlock();
            this.pollNtpServer();
            this.lock.readLock().lock();
        }

        TimeInfo result = this.timeInfo;
        this.lock.readLock().unlock();

        return result;
    }

    private void setTimeInfo(TimeInfo timeInfo) {
        this.lock.writeLock().lock();
        this.timeInfo = timeInfo;
        this.lock.writeLock().unlock();
    }

    /**
     * Returns milliseconds just as System.currentTimeMillis() but using the latest
     * estimate from the remote time server.
     *
     * @return the difference, measured in milliseconds, between the current time and midnight, January 1, 1970 UTC.
     */
    public long currentTimeMillis() {
        this.lock.readLock().lock();
        if (null == this.timeInfo) {
            this.lock.readLock().unlock();
            this.pollNtpServer();
            this.lock.readLock().lock();
        }

        //long diff = System.currentTimeMillis() - this.timeInfoSetLocalTime;
        //long result = timeInfo.getMessage().getReceiveTimeStamp().getTime() + diff;
        long result = System.currentTimeMillis() + timeInfo.getOffset();
        this.lock.readLock().unlock();

        return result;
    }

    @Override
    public void close() {
        this.lock.writeLock().lock();
        if (null != ntpUdpClient) {
            ntpUdpClient.close();
            ntpUdpClient = null;
        }
        this.lock.writeLock().unlock();
    }

}
