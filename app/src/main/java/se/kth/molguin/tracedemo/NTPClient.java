package se.kth.molguin.tracedemo;

/*
Manuel Olguin, May 2 2018: Shamelessly pulled from
https://github.com/wshackle/ntpclient/blob/master/src/main/java/com/github/wshackle/ntpclient/NTPClient.java
Modified to suit the use cases of our application.

This is a modified version of example by Jason Mathews, MITRE Corp that was
published on https://commons.apache.org/proper/commons-net/index.html
with the Apache Commons Net software.
 */

import java.io.IOException;
import java.net.InetAddress;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.text.NumberFormat;
import java.util.Date;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.apache.commons.net.ntp.NTPUDPClient;
import org.apache.commons.net.ntp.NtpUtils;
import org.apache.commons.net.ntp.NtpV3Packet;
import org.apache.commons.net.ntp.TimeInfo;
import org.apache.commons.net.ntp.TimeStamp;

/**
 * NTPClient polls an NTP server with UDP  and returns milli seconds with
 * currentTimeMillis() intended as drop in replacement for System.currentTimeMillis()
 *
 * @author Will Shackleford
 */
public final class NTPClient implements AutoCloseable {

    final InetAddress hostAddr;
    NTPUDPClient ntpUdpClient;
    Thread pollThread = null;
    final long poll_ms;

    private void pollNtpServer() {
        try {
            while (!Thread.currentThread().isInterrupted()) {
                try {
                    Thread.sleep(poll_ms);
                    TimeInfo ti = ntpUdpClient.getTime(hostAddr);
//                    long diff0 = ti.getMessage().getReceiveTimeStamp().getTime() - System.currentTimeMillis();
//                    System.out.println("diff0 = " + diff0);
                    this.setTimeInfo(ti);
                } catch (SocketTimeoutException ste) {
                }
            }
        } catch (InterruptedException interruptedException) {
        } catch (IOException ex) {
            Logger.getLogger(NTPClient.class.getName()).log(Level.SEVERE, null, ex);
        }
    }

    /**
     * Connect to host and poll the host every poll_ms milliseconds.
     * Thread is started in the constructor.
     *
     * @param host
     * @param poll_ms
     * @throws UnknownHostException
     * @throws SocketException
     */
    public NTPClient(String host, int poll_ms) throws UnknownHostException, SocketException {
        this.poll_ms = poll_ms;
        hostAddr = InetAddress.getByName(host);
        ntpUdpClient = new NTPUDPClient();
        ntpUdpClient.setDefaultTimeout(10000);
        ntpUdpClient.open();
        ntpUdpClient.setSoTimeout(poll_ms * 2 + 20);
        pollThread = new Thread(new Runnable() {
            @Override
            public void run() {
                NTPClient.this.pollNtpServer();
            }
        }, "pollNtpServer(" + host + "," + poll_ms + ")");
        pollThread.start();
    }

    private TimeInfo timeInfo;
    private long timeInfoSetLocalTime;

    /**
     * Get the value of timeInfo
     *
     * @return the value of timeInfo
     */
    public synchronized TimeInfo getTimeInfo() {
        return timeInfo;
    }

    private synchronized void setTimeInfo(TimeInfo timeInfo) {
        this.timeInfo = timeInfo;
        timeInfoSetLocalTime = System.currentTimeMillis();
    }

    /**
     * Returns milliseconds just as System.currentTimeMillis() but using the latest
     * estimate from the remote time server.
     *
     * @return the difference, measured in milliseconds, between the current time and midnight, January 1, 1970 UTC.
     */
    public long currentTimeMillis() {
        long diff = System.currentTimeMillis() - timeInfoSetLocalTime;
//        System.out.println("diff = " + diff);
        return timeInfo.getMessage().getReceiveTimeStamp().getTime() + diff;
    }

    /**
     * Polls an NTP server printing the current Date as recieved from it an the difference
     * between that and System.currentTimeMillis()
     *
     * @param args host name of ntp server in first element
     * @throws UnknownHostException
     * @throws SocketException
     * @throws InterruptedException
     * @throws IOException
     * @throws Exception
     */
    public static void main(String[] args) throws UnknownHostException, SocketException, InterruptedException, IOException, Exception {
        if (args.length < 1) {
            args = new String[]{"time-a.nist.gov"};
        }

        try (NTPClient ntp = new NTPClient(args[0], 100)) {

            for (int i = 0; i < 10; i++) {
                Thread.sleep(1000);
                long t1 = System.currentTimeMillis();
                long t2 = ntp.currentTimeMillis();
                long t3 = System.currentTimeMillis();

                Date d = new Date(t2);
                System.out.println(d + " :  diff = " + (t3 - t2) + " ms");
            }
        }
    }

    private boolean closed = false;

    @Override
    public void close() throws Exception {
        if (null != pollThread) {
            pollThread.interrupt();
            pollThread.join(200);
            pollThread = null;
        }
        if (null != ntpUdpClient) {
            ntpUdpClient.close();
            ntpUdpClient = null;
        }

    }

    protected void finalizer() {
        try {
            this.close();
        } catch (Exception ex) {
            Logger.getLogger(NTPClient.class.getName()).log(Level.SEVERE, null, ex);
        }
    }
}
