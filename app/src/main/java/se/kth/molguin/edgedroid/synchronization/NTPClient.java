package se.kth.molguin.edgedroid.synchronization;

/*
Manuel Olguin, May 2 2018: Shamelessly pulled from
https://github.com/wshackle/ntpclient/blob/master/src/main/java/com/github/wshackle/ntpclient/NTPClient.java
Modified to suit the use cases of our application.

This is a modified version of example by Jason Mathews, MITRE Corp that was
published on https://commons.apache.org/proper/commons-net/index.html
with the Apache Commons Net software.
 */

import org.apache.commons.math3.stat.descriptive.SummaryStatistics;
import org.apache.commons.net.ntp.NTPUDPClient;
import org.apache.commons.net.ntp.TimeInfo;

import java.io.IOException;
import java.net.InetAddress;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.util.Locale;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import se.kth.molguin.edgedroid.IntegratedAsyncLog;

public class NTPClient implements AutoCloseable {

    private static final int NTP_POLL_COUNT = 11;
    private static final int NTP_TIMEOUT = 100;
    private static final String LOG_TAG = "NTPClient";
    private final ReadWriteLock lock;
    private final String host;
    private final IntegratedAsyncLog log;
    private final NTPUDPClient ntp;
    private final InetAddress hostAddr;

    private INTPSync current_sync;

    public NTPClient(final String host, final IntegratedAsyncLog log) throws SocketException, UnknownHostException {
        this.host = host;
        this.lock = new ReentrantReadWriteLock();
        this.current_sync = new NullNTPSync();
        this.log = log;
        this.ntp = new NTPUDPClient();
        this.ntp.setDefaultTimeout(10000); // FIXME: magic number
        this.ntp.open();
        this.ntp.setSoTimeout(NTP_TIMEOUT);

        this.hostAddr = InetAddress.getByName(this.host);
    }

    public INTPSync sync() throws IOException {
        this.log.i(LOG_TAG, "Polling NTP host " + this.host);
        this.lock.writeLock().lock();
        try {
            final SummaryStatistics offsets = new SummaryStatistics();
            final SummaryStatistics delays = new SummaryStatistics();

            int poll_cnt = 0;
            while (poll_cnt < NTP_POLL_COUNT) {
                try {

                    final TimeInfo ti = ntp.getTime(this.hostAddr);
                    ti.computeDetails();
                    poll_cnt++;

                    offsets.addValue(ti.getOffset());
                    delays.addValue(ti.getDelay());
                } catch (SocketTimeoutException e) {
                    this.log.w(LOG_TAG, "NTP request timed out! Retry!");
                }
            }

            this.current_sync = new StaticNTPSync(
                    offsets.getMean(), delays.getMean(),
                    offsets.getStandardDeviation(), delays.getStandardDeviation()
            );

            this.log.i(LOG_TAG, "Polled " + this.host);
            this.log.i(LOG_TAG, "Local time: " + System.currentTimeMillis());
            this.log.i(LOG_TAG, "Server time: " + this.current_sync.currentTimeMillis());
            this.log.i(LOG_TAG, String.format(Locale.ENGLISH,
                    "Offset: %f (+- %f) ms\tDelay: %f (+- %f) ms",
                    this.current_sync.getOffset(),
                    this.current_sync.getOffsetError(),
                    this.current_sync.getDelay(),
                    this.current_sync.getDelayError()
            ));
        } finally {
            this.lock.writeLock().unlock();
        }

        return this.current_sync;
    }

    public double getOffset() {
        this.lock.readLock().lock();
        try {
            return this.current_sync.getOffset();
        } finally {
            this.lock.readLock().unlock();
        }
    }

    public double getDelay() {
        this.lock.readLock().lock();
        try {
            return this.current_sync.getDelay();
        } finally {
            this.lock.readLock().unlock();
        }
    }

    public double getOffsetError() {
        this.lock.readLock().lock();
        try {
            return this.current_sync.getOffsetError();
        } finally {
            this.lock.readLock().unlock();
        }
    }

    public double getDelayError() {
        this.lock.readLock().lock();
        try {
            return this.current_sync.getDelayError();
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
            return this.current_sync.currentTimeMillis();
        } finally {
            this.lock.readLock().unlock();
        }
    }

    @Override
    public void close() {
        this.ntp.close();
    }
}
