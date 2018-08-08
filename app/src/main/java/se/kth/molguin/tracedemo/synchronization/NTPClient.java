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

import org.apache.commons.math3.stat.descriptive.SynchronizedSummaryStatistics;
import org.apache.commons.net.ntp.NTPUDPClient;
import org.apache.commons.net.ntp.TimeInfo;

import java.io.IOException;
import java.net.InetAddress;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

public class NTPClient {

    private static final int NTP_POLL_COUNT = 11;
    private static final int NTP_TIMEOUT = 100;
    private static final String LOG_TAG = "NTPClient";
    private final ReadWriteLock lock;

    private final InetAddress hostAddr;
    private NTPUDPClient ntpUdpClient;

    private final SynchronizedSummaryStatistics offsets;
    private final SynchronizedSummaryStatistics delays;

    private double mean_offset;
    private double mean_delay;
    private double offset_err;
    private double delay_err;
    private boolean synced;

    public NTPClient(final String host) throws UnknownHostException, SocketException {
        this.lock = new ReentrantReadWriteLock();
        this.offsets = new SynchronizedSummaryStatistics();
        this.delays = new SynchronizedSummaryStatistics();

        this.mean_offset = 0;
        this.mean_delay = 0;
        this.offset_err = 0;
        this.delay_err = 0;
        this.synced = false;

        Log.i(LOG_TAG, "Initializing with host: " + host);
        this.hostAddr = InetAddress.getByName(host);
        Log.i(LOG_TAG, "Resolved host address: " + this.hostAddr);
        this.ntpUdpClient = new NTPUDPClient();
        this.ntpUdpClient.setDefaultTimeout(10000);
        this.ntpUdpClient.open();
        this.ntpUdpClient.setSoTimeout(NTP_TIMEOUT);
    }

    public void sync() throws IOException {
        this.lock.writeLock().lock();
        try {

            this.offsets.clear();
            this.delays.clear();

            int poll_cnt = 0;
            while (poll_cnt < NTP_POLL_COUNT) {
                try {

                    TimeInfo ti = ntpUdpClient.getTime(this.hostAddr);
                    ti.computeDetails();
                    poll_cnt++;

                    this.offsets.addValue(ti.getOffset());
                    this.delays.addValue(ti.getDelay());
                } catch (SocketTimeoutException e) {
                    Log.w(LOG_TAG, "NTP request timed out! Retry!");
                }
            }

            this.mean_offset = offsets.getMean();
            this.mean_delay = delays.getMean();
            this.offset_err = offsets.getStandardDeviation();
            this.delay_err = delays.getStandardDeviation();

            Log.i(LOG_TAG, "Polled " + this.hostAddr.toString());
            Log.i(LOG_TAG, "Local time: " + System.currentTimeMillis());
            Log.i(LOG_TAG, "Server time: " + this.currentTimeMillis());
            Log.i(LOG_TAG, String.format(
                    "Offset: %f (+- %f) ms\tDelay: %f (+- %f) ms",
                    this.mean_offset,
                    this.offset_err,
                    this.mean_delay,
                    this.delay_err
            ));
        } finally {
            this.lock.writeLock().unlock();
        }
    }

    public double getMeanOffset() {
        this.lock.readLock().lock();
        try {
            if (!this.isSynced())
                Log.w(LOG_TAG, "NPT is not synced!");
            return this.mean_offset;
        } finally {
            this.lock.readLock().unlock();
        }
    }

    public double getMeanDelay() {
        this.lock.readLock().lock();
        try {
            if (!this.isSynced())
                Log.w(LOG_TAG, "NPT is not synced!");
            return this.mean_delay;
        } finally {
            this.lock.readLock().unlock();
        }
    }

    public double getOffsetError() {
        this.lock.readLock().lock();
        try {
            if (!this.isSynced())
                Log.w(LOG_TAG, "NPT is not synced!");
            return this.offset_err;
        } finally {
            this.lock.readLock().unlock();
        }
    }

    public double getDelayError() {
        this.lock.readLock().lock();
        try {
            if (!this.isSynced())
                Log.w(LOG_TAG, "NPT is not synced!");
            return this.delay_err;
        } finally {
            this.lock.readLock().unlock();
        }
    }

    /**
     * Returns milliseconds just as System.currentTimeMillis() but using the latest
     * estimate from the remote time server.
     *
     * @return the difference, measured in milliseconds, between the current time and midnight, January 1, 1970 UTC.
     */
    public double currentTimeMillis() {
        this.lock.readLock().lock();
        try {
            if (!this.isSynced())
                Log.w(LOG_TAG, "NPT is not synced!");
            return System.currentTimeMillis() + this.mean_offset;
        } finally {
            this.lock.readLock().unlock();
        }
    }

    public void close() {
        this.ntpUdpClient.close();
    }

    public boolean isSynced() {
        this.lock.readLock().lock();
        try {
            return this.synced;
        } finally {
            this.lock.readLock().unlock();
        }
    }
}
