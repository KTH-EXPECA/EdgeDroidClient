package se.kth.molguin.tracedemo.network.control.experiment;

import android.util.Log;

import org.apache.commons.math3.stat.descriptive.SynchronizedDescriptiveStatistics;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import se.kth.molguin.tracedemo.network.control.ControlConst;
import se.kth.molguin.tracedemo.synchronization.NTPClient;

public class RunStats {
    private static final String LOG_TAG = "RunStats";
    private static final int STAT_WINDOW_SZ = 15;
    private static final int DEFAULT_INIT_MAP_SIZE = 5;

    private final ReadWriteLock lock;

    private final List<Frame> frames;
    private final ConcurrentHashMap<Integer, Double> outgoing_timestamps;
    private SynchronizedDescriptiveStatistics rtt;

    private boolean success;
    private double init;
    private double finish;

    private final NTPClient ntp;

    public RunStats(NTPClient ntpSyncer) {
        this.init = -1;
        this.finish = -1;
        this.success = false;

        this.lock = new ReentrantReadWriteLock();

        // initial size of 5 is ok since we'll constantly be removing frames as we get back confirmations
        this.outgoing_timestamps = new ConcurrentHashMap<>(DEFAULT_INIT_MAP_SIZE);
        this.frames = Collections.synchronizedList(new LinkedList<Frame>());
        this.rtt = new SynchronizedDescriptiveStatistics(RunStats.STAT_WINDOW_SZ);
        this.ntp = ntpSyncer;
    }

    public void init() {
        this.lock.writeLock();
        try {
            if (this.init < 0 && this.finish < 0)
                this.init = this.ntp.currentTimeMillis();
        } finally {
            this.lock.writeLock().unlock();
        }
    }

    public void finish(boolean success) {
        this.lock.writeLock();
        try {
            if (this.init > 0 && this.finish < 0) {
                this.finish = this.ntp.currentTimeMillis();
                this.success = success;
            }
        } finally {
            this.lock.writeLock().unlock();
        }
    }

    public void registerSentFrame(int frame_id) {
        this.outgoing_timestamps.put(frame_id, this.ntp.currentTimeMillis());
    }

    public void registerReceivedFrame(int frame_id, boolean feedback) {
        double in_time = this.ntp.currentTimeMillis();
        Double out_time = this.outgoing_timestamps.get(frame_id);

        if (out_time != null) {
            Frame f = new Frame(frame_id, out_time, in_time, feedback);
            this.frames.add(f);
            this.rtt.addValue(f.getRTT());
        } else
            Log.w(LOG_TAG, "Got reply for frame "
                    + frame_id + " but couldn't find it in the list of sent frames!");
    }

    public double getRollingRTT() {
        return this.rtt.getMean();
    }

    public JSONObject toJSON() throws JSONException, RunStatsException {

        this.lock.readLock();
        try {
            if (this.init < 0 || this.finish < 0) {
                String msg = "Stats not ready!";
                Log.e(LOG_TAG, msg);
                throw new RunStatsException(msg);
            }

            JSONObject repr = new JSONObject();

            repr.put(ControlConst.Stats.FIELD_RUNBEGIN, this.init);
            repr.put(ControlConst.Stats.FIELD_RUNEND, this.finish);
            repr.put(ControlConst.Stats.FIELD_RUNTIMESTAMPERROR, this.ntp.getOffsetError());
            repr.put(ControlConst.Stats.FIELD_RUNSUCCESS, this.success);
            repr.put(ControlConst.Stats.FIELD_RUNNTPOFFSET, this.ntp.getMeanOffset());

            JSONArray json_frames = new JSONArray();
            for (Frame f : this.frames) {
                json_frames.put(f.toJSON());
            }

            repr.put(ControlConst.Stats.FIELD_RUNFRAMELIST, json_frames);

            return repr;
        } finally {
            this.lock.readLock().unlock();
        }
    }

    private static class Frame {
        final int id;
        final double sent;
        final double recv;
        final boolean feedback;

        Frame(int id, double sent, double recv, boolean feedback) {
            this.id = id;
            this.sent = sent;
            this.recv = recv;
            this.feedback = feedback;
        }

        double getRTT() {
            return this.recv - this.sent;
        }

        JSONObject toJSON() throws JSONException {
            JSONObject repr = new JSONObject();
            repr.put(ControlConst.Stats.FRAMEFIELD_ID, this.id);
            repr.put(ControlConst.Stats.FRAMEFIELD_SENT, this.sent);
            repr.put(ControlConst.Stats.FRAMEFIELD_RECV, this.recv);
            repr.put(ControlConst.Stats.FRAMEFIELD_FEEDBACK, this.feedback);

            return repr;
        }
    }

    static class RunStatsException extends Exception {
        RunStatsException(String msg) {
            super(msg);
        }
    }
}
