package se.kth.molguin.edgedroid.synchronization;

import android.support.annotation.NonNull;

import com.github.molguin92.minisync.algorithm.MiniSyncAlgorithm;
import com.github.molguin92.minisync.algorithm.TimeSyncAlgorithm;
import com.github.molguin92.minisync.algorithm.TimeSyncAlgorithmException;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetSocketAddress;
import java.net.SocketException;
import java.util.Locale;
import java.util.Random;
import java.util.concurrent.BrokenBarrierException;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.atomic.AtomicInteger;

import se.kth.molguin.edgedroid.IntegratedAsyncLog;
import se.kth.molguin.edgedroid.network.DataIOStreams;
import se.kth.molguin.edgedroid.network.control.ControlClient;
import se.kth.molguin.edgedroid.network.control.ControlConst;
import se.kth.molguin.edgedroid.network.control.experiment.Config;
import se.kth.molguin.edgedroid.utils.AtomicDouble;

import static se.kth.molguin.edgedroid.network.control.ControlConst.Commands.SHUTDOWN;

public class TimeKeeper {
    private final static String LOG_TAG = "TimeKeeper";
    private TimeSyncAlgorithm algorithm;
    private Double T_init;
    private final IntegratedAsyncLog log;
    private double min_local_delay;

    public TimeKeeper(@NonNull final IntegratedAsyncLog log) {
        this.algorithm = new MiniSyncAlgorithm();
        this.log = log;
        this.min_local_delay = 0d;
    }

    public void estimateMinimumLocalDelay() throws InterruptedException, IOException, ControlClient.ControlException {
        // estimate minimum local delay
        // set up two local sockets and send data back and forth
        final int local_port = 5000;
        final int packet_sz = 32;
        final AtomicDouble send_time = new AtomicDouble(0);
        final AtomicDouble min_delay = new AtomicDouble(Double.MAX_VALUE);
        final AtomicInteger num_loops = new AtomicInteger(500); // TODO: magic numbah?
        final CyclicBarrier barrier = new CyclicBarrier(2);

        Thread server = new Thread(() -> {
            double current_min = Double.MAX_VALUE;
            DatagramSocket serverSocket;
            try {
                serverSocket = new DatagramSocket(local_port);
            } catch (SocketException e) {
                return;
            }

            DatagramPacket packet = new DatagramPacket(new byte[packet_sz], packet_sz);
            while (num_loops.get() > 0) {
                try {
                    serverSocket.receive(packet);
                    double recv_time = System.nanoTime() / 1000.0;
                    double delay = (recv_time - send_time.doubleValue()) / 2.0;
                    current_min = delay < current_min ? delay : current_min;

                    num_loops.decrementAndGet();
                    barrier.await();
                } catch (IOException ignored) {
                    break;
                } catch (BrokenBarrierException e) {
                    break;
                } catch (InterruptedException e) {
                    break;
                }
            }
            min_delay.set(current_min);
            serverSocket.close();
        });

        server.start();
        this.log.i(LOG_TAG, "Estimating minimum local delay...");

        // server is waiting, now we connect to it and send mock packets
        Random r = new Random(System.currentTimeMillis());
        final DatagramSocket clientSocket = new DatagramSocket();
        clientSocket.connect(new InetSocketAddress("0.0.0.0", local_port));
        byte[] data = new byte[packet_sz];
        DatagramPacket packet = new DatagramPacket(new byte[packet_sz], packet_sz);
        while (num_loops.get() > 0) {
            try {
                r.nextBytes(data);
                packet.setData(data);
                send_time.set(System.nanoTime() / 1000.0);
                clientSocket.send(packet);
                barrier.await();
            } catch (BrokenBarrierException e) {
                this.log.e(LOG_TAG, "Broken barrier when estimating minimum local delay: ", e);
                throw new ControlClient.ControlException("Could not estimate minimum delay.");
            }
        }
        clientSocket.close();
        server.join();
        // minimum delay is now stored
        this.min_local_delay = min_delay.doubleValue();
        this.algorithm.setMinimumLocalDelay(this.min_local_delay);
        this.log.i(LOG_TAG, String.format(Locale.ENGLISH,
                "Estimated minimum local delay: %f microseconds", min_delay.doubleValue()));
    }

    public void init() {
        this.T_init = System.nanoTime() / 1000.0d;
    }

    public double currentAdjustedSimTime() {
        assert T_init != null;
        return ((System.nanoTime() / 1000.0) - T_init) * algorithm.getDrift() + algorithm.getOffset();
    }

    public double currentAdjustedTimeMilliseconds() {
        return this.currentAdjustedSimTime() / 1000.0;
    }

    // TODO: USE SYSTEM.CURRENTTIMEMILLISECONDS
    public void syncClocks(@NonNull final DataIOStreams ioStreams, @NonNull Config config) throws IOException, ControlClient.ShutdownCommandException, ControlClient.ControlException, InterruptedException {
        // notify start of sync
        this.log.i(LOG_TAG, "Synchronizing clocks...");
        ioStreams.write(ControlConst.Commands.TimeSync.SYNC_START);
        ioStreams.flush();
        final double T0 = System.nanoTime() / 1000.0; // reference T = 0
        // iterations
        for (int i = 0; i < 10 || algorithm.getOffsetError() >= config.target_offset_error; ++i) {
            ioStreams.write(ControlConst.Commands.TimeSync.SYNC_BEACON);
            ioStreams.flush();
            double To = (System.nanoTime() / 1000.0) - T0;

            // wait for reply
            switch (ioStreams.readInt()) {
                case ControlConst.Commands.TimeSync.SYNC_BEACON_REPLY:
                    break;
                case SHUTDOWN:
                    throw new ControlClient.ShutdownCommandException(); // shut down gracefully
                default:
                    // got an invalid command
                    throw new ControlClient.ControlException("Unexpected command from Control!");
            }
            double Tbr = ioStreams.readDouble();
            double Tbt = ioStreams.readDouble();

            double Tr = (System.nanoTime() / 1000.0) - T0;

            // update algorithm
            try {
                algorithm.addDataPoint(To, Tbr, Tr);
                algorithm.addDataPoint(To, Tbt, Tr);
            } catch (TimeSyncAlgorithmException e) {
                this.log.e(LOG_TAG, "Time was not monotonically increasing! Retrying...");
                i = 0;
                this.algorithm = new MiniSyncAlgorithm();
                this.algorithm.setMinimumLocalDelay(this.min_local_delay);
                // this.init(); // reset the count
                continue;
            }
            Thread.sleep(5);
        }

        ioStreams.write(ControlConst.Commands.TimeSync.SYNC_END);
        ioStreams.flush();

        this.log.i(LOG_TAG, "Synchronized clocks with server.");
        this.log.i(LOG_TAG, String.format(Locale.ENGLISH,
                "Drift: %f (Error: %f)", algorithm.getDrift(), algorithm.getDriftError()));
        this.log.i(LOG_TAG, String.format(Locale.ENGLISH,
                "Offset: %f µs (Error: %f µs)", algorithm.getOffset(), algorithm.getOffsetError()));
        // sync done
    }

    public Parameters getParameters() {
        return new Parameters(algorithm.getDrift(), algorithm.getDriftError(),
                algorithm.getOffset(), algorithm.getOffsetError());
    }

    public class Parameters {
        public final double drift;
        public final double drift_error;
        public final double offset;
        public final double offset_error;

        private Parameters(double drift, double drift_error, double offset, double offset_error) {
            this.drift = drift;
            this.drift_error = drift_error;
            this.offset = offset;
            this.offset_error = offset_error;
        }
    }
}
