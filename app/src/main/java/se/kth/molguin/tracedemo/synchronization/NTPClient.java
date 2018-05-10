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

import org.apache.commons.math3.stat.descriptive.SummaryStatistics;
import org.apache.commons.net.ntp.NTPUDPClient;
import org.apache.commons.net.ntp.TimeInfo;

import java.io.IOException;
import java.net.InetAddress;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import static java.lang.System.exit;

public class NTPClient implements AutoCloseable {

    private static final int NTP_POLL_COUNT = 11;
    private static final int NTP_TIMEOUT = 100;
    private static final String LOG_TAG = "NTPClient";
    private ReadWriteLock lock;

    private InetAddress hostAddr;
    NTPUDPClient ntpUdpClient;

    private double mean_offset;
    private double mean_delay;
    private double offset_err;
    private double delay_err;

    private boolean sync;

    public NTPClient(String host) throws UnknownHostException, SocketException {
        Log.i(LOG_TAG, "Initializing with host: " + host);
        this.hostAddr = InetAddress.getByName(host);
        Log.i(LOG_TAG, "Resolved host address: " + this.hostAddr);
        this.ntpUdpClient = new NTPUDPClient();
        this.ntpUdpClient.setDefaultTimeout(10000);
        this.ntpUdpClient.open();
        this.ntpUdpClient.setSoTimeout(NTP_TIMEOUT);
        this.sync = false;
        this.lock = new ReentrantReadWriteLock();

        this.pollNtpServer();
    }

    public void pollNtpServer() {
        SummaryStatistics offsets = new SummaryStatistics();
        SummaryStatistics delays = new SummaryStatistics();

        int poll_cnt = 0;
        while (poll_cnt < NTP_POLL_COUNT) {
            try {
                TimeInfo ti = ntpUdpClient.getTime(hostAddr);
                ti.computeDetails();
                poll_cnt++;

                offsets.addValue(ti.getOffset());
                delays.addValue(ti.getDelay());
            } catch (SocketTimeoutException e) {
                Log.w(LOG_TAG, "NTP request timed out! Retry!");
            } catch (IOException e) {
                Log.e(LOG_TAG, "Exception!", e);
                this.close();
                exit(-1);
            }
        }

        this.lock.writeLock().lock();
        try {
            this.mean_offset = offsets.getMean();
            this.mean_delay = delays.getMean();
            this.offset_err = offsets.getStandardDeviation();
            this.delay_err = delays.getStandardDeviation();
            this.sync = true;
        } finally {
            this.lock.writeLock().unlock();
        }

        Log.i(LOG_TAG, "Polled " + this.hostAddr.toString());
        Log.i(LOG_TAG, "Local time: " + System.currentTimeMillis());
        Log.i(LOG_TAG, "Server time: " + this.currentTimeMillis());
        Log.i(LOG_TAG, String.format(
                "Offset: %f (+- %f) ms\tDelay: %f (+- %f) ms",
                offsets.getMean(),
                offsets.getStandardDeviation(),
                delays.getMax(),
                delays.getStandardDeviation()
        ));
    }

    public double getMeanOffset() {
        double result;
        this.lock.readLock().lock();
        try {
            result = this.mean_offset;
        } finally {
            this.lock.readLock().unlock();
        }

        return result;
    }

    public double getMeanDelay() {
        double result;
        this.lock.readLock().lock();
        try {
            result = this.mean_delay;
        } finally {
            this.lock.readLock().unlock();
        }

        return result;
    }

    public double getOffsetError() {
        double result;
        this.lock.readLock().lock();
        try {
            result = this.offset_err;
        } finally {
            this.lock.readLock().unlock();
        }

        return result;
    }

    public double getDelayError() {
        double result;
        this.lock.readLock().lock();
        try {
            result = this.delay_err;
        } finally {
            this.lock.readLock().unlock();
        }

        return result;
    }

    /**
     * Returns milliseconds just as System.currentTimeMillis() but using the latest
     * estimate from the remote time server.
     *
     * @return the difference, measured in milliseconds, between the current time and midnight, January 1, 1970 UTC.
     */
    public double currentTimeMillis() {
        double result;
        this.lock.readLock().lock();
        try {
            if (!this.sync) {
                this.lock.readLock().unlock();
                this.pollNtpServer();
                this.lock.readLock().lock();
            }

            //long diff = System.currentTimeMillis() - this.timeInfoSetLocalTime;
            //long result = timeInfo.getMessage().getReceiveTimeStamp().getTime() + diff;
            result = System.currentTimeMillis() + this.mean_offset;
        } finally {
            this.lock.readLock().unlock();
        }

        return result;
    }

    @Override
    public void close() {
        this.lock.writeLock().lock();
        try {
            if (null != ntpUdpClient) {
                ntpUdpClient.close();
                ntpUdpClient = null;
            }
            this.sync = false;
        } finally {
            this.lock.writeLock().unlock();
        }
    }

}
