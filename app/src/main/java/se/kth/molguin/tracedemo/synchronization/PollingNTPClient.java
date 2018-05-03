package se.kth.molguin.tracedemo.synchronization;

import org.apache.commons.net.ntp.TimeInfo;

import java.net.SocketException;
import java.net.UnknownHostException;
import java.util.Timer;
import java.util.TimerTask;

/**
 * PollingNTPClient polls an NTP server with UDP  and returns milliseconds with
 * currentTimeMillis() intended as drop in replacement for System.currentTimeMillis()
 *
 * @author Will Shackleford (original)
 * @author Manuel Olguin (modified)
 */

public class PollingNTPClient extends NTPClient {
    private final static Object lock = new Object();

    private Timer pollTimer;
    private TimerTask pollTask;

    public PollingNTPClient(String host, int poll_ms) throws UnknownHostException, SocketException {
        super(host);
        this.ntpUdpClient.setSoTimeout(poll_ms * 2 + 20);

        this.pollTimer = new Timer();
        this.pollTask = new TimerTask() {
            @Override
            public void run() {
                synchronized (lock) {
                    PollingNTPClient.this.pollNtpServer();
                }
            }
        };

        this.pollTimer.scheduleAtFixedRate(this.pollTask, 0, poll_ms);
    }

    @Override
    public void close() {
        if (this.pollTask != null) {
            this.pollTask.cancel();
            this.pollTask = null;
        }

        if (this.pollTimer != null) {
            this.pollTimer.cancel();
            this.pollTimer.purge();
            this.pollTimer = null;
        }

        synchronized (lock){
            super.close();
        }
    }

    @Override
    public TimeInfo getTimeInfo() {
        synchronized (lock) {
            return super.getTimeInfo();
        }
    }

    @Override
    synchronized void setTimeInfo(TimeInfo timeInfo) {
        synchronized (lock) {
            super.setTimeInfo(timeInfo);
        }
    }

    @Override
    public long currentTimeMillis() {
        synchronized (lock)
        {
            return super.currentTimeMillis();
        }
    }
}
