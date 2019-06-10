package se.kth.molguin.edgedroid.synchronization;

import android.os.SystemClock;
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

import static se.kth.molguin.edgedroid.network.control.ControlConst.Commands.SHUTDOWN;

public class TimeKeeper {
    private final static String LOG_TAG = "TimeKeeper";
    private TimeSyncAlgorithm algorithm;
    //private Double T_init_nanos;
    private final IntegratedAsyncLog log;
    private double min_local_delay_ms;
    //private Double abs_time_offset_ms;

    public TimeKeeper(@NonNull final IntegratedAsyncLog log) {
        this.algorithm = new MiniSyncAlgorithm();
        this.log = log;
        this.min_local_delay_ms = 0d;
        //this.T_init_nanos = null;
        //this.abs_time_offset_ms = null;
    }

    public void estimateMinimumLocalDelay() throws InterruptedException, IOException, ControlClient.ControlException {
        // estimate minimum local delay
        // set up two local sockets and send data back and forth
        final int local_port = 5000;
        final int packet_sz = 32;
        final AtomicInteger num_loops = new AtomicInteger(500); // TODO: magic numbah?
        final CyclicBarrier barrier = new CyclicBarrier(2);

        Thread server = new Thread(() -> {
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
                    serverSocket.send(packet);
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
            serverSocket.close();
        });

        server.start();
        this.log.i(LOG_TAG, "Estimating minimum local delay...");

        // server is waiting, now we connect to it and send mock packets
        Random r = new Random(System.currentTimeMillis());
        final DatagramSocket clientSocket = new DatagramSocket();
        clientSocket.connect(new InetSocketAddress("0.0.0.0", local_port));
        byte[] data = new byte[packet_sz];
        double send_time, delay;
        double min_delay_ns = Double.MAX_VALUE;
        while (num_loops.get() > 0) {
            try {
                DatagramPacket packet = new DatagramPacket(new byte[packet_sz], packet_sz);
                r.nextBytes(data);
                packet.setData(data);
                send_time = SystemClock.elapsedRealtimeNanos();
                clientSocket.send(packet);
                clientSocket.receive(packet);
                delay = SystemClock.elapsedRealtimeNanos() - send_time;
                min_delay_ns = delay < min_delay_ns ? delay : min_delay_ns;

                barrier.await();
            } catch (BrokenBarrierException e) {
                this.log.e(LOG_TAG, "Broken barrier when estimating minimum local delay: ", e);
                throw new ControlClient.ControlException("Could not estimate minimum delay.");
            }
        }
        clientSocket.close();
        server.join();
        // minimum delay is now stored
        this.min_local_delay_ms = (min_delay_ns / 2.0) / 1000000.0; // min_delay_ns includes roundtrip
        this.algorithm.setMinimumLocalDelay(this.min_local_delay_ms);
        this.log.i(LOG_TAG, String.format(Locale.ENGLISH,
                "Estimated minimum local delay: %f milliseconds", this.min_local_delay_ms));
    }

    // public void init() {
    //     this.abs_time_offset_ms = (System.currentTimeMillis() * 1000000.0) - SystemClock.elapsedRealtimeNanos();
    //    this.T_init_nanos = SystemClock.elapsedRealtimeNanos() + this.abs_time_offset_ms;
    //}

    //    public double currentAdjustedSimTimeNanos() {
//        //assert T_init_nanos != null;
//        return (this.getAdjustedBaseTimeNanos() - T_init_nanos) * algorithm.getDrift() + algorithm.getOffset();
//    }
//
    public double currentAdjustedTimeMilliseconds() {
        return (System.currentTimeMillis() * algorithm.getDrift()) + algorithm.getOffset();
    }
//
//    private double getAdjustedBaseTimeNanos() {
//        assert T_init_nanos != null;
//        return SystemClock.elapsedRealtimeNanos() + this.abs_time_offset_ms;
//    }

    public void syncClocks(@NonNull final DataIOStreams ioStreams, @NonNull Config config) throws IOException, ControlClient.ShutdownCommandException, ControlClient.ControlException, InterruptedException {
        // notify start of sync
        // note: the client uses a relative clock approach whereas the server uses an absolute clock
        // this is because Java 8 has no way of obtaining absolute time with microsecond precision,
        // so what we do instead is set an arbitrary point in time as T = 0, and then synchronize
        // around that point. Since we're synchronizing with respect to the server clocks, which is
        // absolute, the algorithm will correct for this and give us absolute time here as well.

        //assert T_init_nanos != null;
        this.log.i(LOG_TAG, "Synchronizing clocks...");
        ioStreams.writeInt(ControlConst.Commands.TimeSync.SYNC_START);
        ioStreams.flush();
        // iterations
        for (int i = 0; i < 10 || Math.abs(algorithm.getOffsetError()) >= config.target_offset_error_ms; ++i) {
            this.log.i(LOG_TAG, "Sending time sync beacon...");
            ioStreams.writeInt(ControlConst.Commands.TimeSync.SYNC_BEACON);
            ioStreams.flush();
            double To = (double) System.currentTimeMillis();

            // wait for reply
            switch (ioStreams.readInt()) {
                case ControlConst.Commands.TimeSync.SYNC_BEACON_REPLY:
                    this.log.i(LOG_TAG, "Got a beacon reply.");
                    break;
                case SHUTDOWN:
                    throw new ControlClient.ShutdownCommandException(); // shut down gracefully
                default:
                    // got an invalid command
                    throw new ControlClient.ControlException("Unexpected command from Control!");
            }
            double Tbr = ioStreams.readDouble();
            double Tbt = ioStreams.readDouble();

            double Tr = (double) System.currentTimeMillis();

            // update algorithm
            try {
                algorithm.addDataPoint(To, Tbr, Tr);
                algorithm.addDataPoint(To, Tbt, Tr);
            } catch (TimeSyncAlgorithmException e) {
                this.log.e(LOG_TAG, "Time was not monotonically increasing! Retrying...");
                i = 0;
                this.algorithm = new MiniSyncAlgorithm();
                this.algorithm.setMinimumLocalDelay(this.min_local_delay_ms);
                // this.init(); // reset the count
                continue;
            }

            if (i % 20 == 0) {
                this.log.i(LOG_TAG, "Synchronizing...");
                this.log.i(LOG_TAG, String.format(Locale.ENGLISH,
                        "Drift: %f (Error: %f)", algorithm.getDrift(), algorithm.getDriftError()));
                this.log.i(LOG_TAG, String.format(Locale.ENGLISH,
                        "Offset: %f ms (Error: %f µs)", algorithm.getOffset(), algorithm.getOffsetError()));
            }

            Thread.sleep(5);
        }

        ioStreams.writeInt(ControlConst.Commands.TimeSync.SYNC_END);
        ioStreams.flush();

        this.log.i(LOG_TAG, "Synchronized clocks with server.");
        this.log.i(LOG_TAG, String.format(Locale.ENGLISH,
                "Drift: %f (Error: %f)", algorithm.getDrift(), algorithm.getDriftError()));
        this.log.i(LOG_TAG, String.format(Locale.ENGLISH,
                "Offset: %f ms (Error: %f ms)", algorithm.getOffset(), algorithm.getOffsetError()));
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
