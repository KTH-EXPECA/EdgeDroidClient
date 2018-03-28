package se.kth.molguin.tracedemo;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.LinkedBlockingQueue;

/**
 * Created by molguin on 2018-02-19.
 */

public class StatCollector {

    private Map<Integer, Long> sent_records;
    private LinkedBlockingQueue<Long> rtts;
    private LinkedBlockingQueue<Long> recv_ts;
    private int last_recv_seq;
    private LinkedBlockingQueue<Integer> missed_seqs;

    public StatCollector() {
        this.sent_records = new ConcurrentHashMap<>(100);
        this.rtts = new LinkedBlockingQueue<Long>(10);
        this.recv_ts = new LinkedBlockingQueue<Long>(21);
        this.missed_seqs = new LinkedBlockingQueue<Integer>(10);
        last_recv_seq = -1;
    }

    public void recordSentTime(int seq) {
        sent_records.put(seq, System.currentTimeMillis());
    }

    public void recordRecvTime(int seq) {
        long timestamp = System.currentTimeMillis();
        if (!sent_records.containsKey(seq)) return;

        if (last_recv_seq != -1 && last_recv_seq != seq) {
            int missed = (seq - last_recv_seq) - 1;
            while (!missed_seqs.offer(missed))
                missed_seqs.poll();
        }

        last_recv_seq = seq;

        long dt = timestamp - sent_records.get(seq);
        while(!rtts.offer(dt))
            // keep popping elements until there's room available
            rtts.poll();

        while (!recv_ts.offer(timestamp))
            // again, pop until there's room
            recv_ts.poll();
    }

    public double getMovingAvgRecvDT() {
        double sum = 0.0d;
        int count = 0;

        Object[] timestamps = recv_ts.toArray();
        for (int i = 0; i < (timestamps.length - 1); i++) {
            sum += (((long) timestamps[i + 1]) - ((long) timestamps[i]));
            count++;
        }

        return sum / count;
    }

    public double getMovingAvgRTT() {
        double sum = 0.0d;

        Object[] dts = rtts.toArray();
        for (Object dt: dts)
            sum += (long) dt;

        return sum/dts.length;
    }

    public double getAvgMissedSeq() {
        double sum = 0.0d;

        Object[] misses = missed_seqs.toArray();
        for (Object miss : misses)
            sum += (int) miss;

        return sum / misses.length;
    }

}
