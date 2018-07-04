package se.kth.molguin.tracedemo.network.control.experiment;

import org.apache.commons.math3.stat.descriptive.DescriptiveStatistics;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.HashSet;
import java.util.LinkedList;

import se.kth.molguin.tracedemo.network.control.ControlConst;
import se.kth.molguin.tracedemo.synchronization.NTPClient;

public class RunStats {
    private static final int STAT_WINDOW_SZ = 15;
    private double init;
    private double finish;
    private double timestamp_error;
    private HashSet<Integer> feedback_frames;
    private LinkedList<RunStats.Frame> frames;
    private DescriptiveStatistics rtt;
    private boolean success;

    private NTPClient ntp;

    public RunStats(NTPClient ntpClient) {
        this.init = -1;
        this.finish = -1;
        this.timestamp_error = -1;
        this.success = false;

        this.feedback_frames = new HashSet<>();
        this.frames = new LinkedList<>();
        this.rtt = new DescriptiveStatistics(RunStats.STAT_WINDOW_SZ);
        this.ntp = ntpClient;
    }

    public void init() {
        this.init = this.ntp.currentTimeMillis();
    }

    public void finish() {
        this.finish = this.ntp.currentTimeMillis();
        this.timestamp_error = this.ntp.getOffsetError();
    }

    public void setSuccess(boolean success) {
        this.success = success;
    }

    public void registerFrame(int frame_id, double sent, double recv, boolean feedback) {
        RunStats.Frame f = new RunStats.Frame(frame_id, sent, recv);
        this.frames.add(f);

        if (feedback)
            this.feedback_frames.add(frame_id);

        this.rtt.addValue(f.getRTT());
    }

    public double getRollingRTT() {
        return this.rtt.getMean();
    }

    private double getInitTimestamp() {
//            Calendar c = Calendar.getInstance();
//            c.setTime(this.init);
//            return c.getTimeInMillis();

        return this.init;
    }

    private double getFinishTimestamp() {
//            Calendar c = Calendar.getInstance();
//            c.setTime(this.finish);
//            return c.getTimeInMillis();

        return this.finish;
    }

    public JSONObject toJSON() throws JSONException {
        JSONObject repr = new JSONObject();

        repr.put(ControlConst.Stats.FIELD_RUNBEGIN, this.getInitTimestamp());
        repr.put(ControlConst.Stats.FIELD_RUNEND, this.getFinishTimestamp());
        repr.put(ControlConst.Stats.FIELD_RUNTIMESTAMPERROR, this.timestamp_error);
        repr.put(ControlConst.Stats.FIELD_RUNSUCCESS, this.success);
        repr.put(ControlConst.Stats.FIELD_RUNNTPOFFSET, this.ntp.getMeanOffset());

        JSONArray json_frames = new JSONArray();
        for (RunStats.Frame f : this.frames) {
            JSONObject f_json = f.toJSON();
            if (this.feedback_frames.contains(f.id))
                f_json.put(ControlConst.Stats.FRAMEFIELD_FEEDBACK, true);
            else f_json.put(ControlConst.Stats.FRAMEFIELD_FEEDBACK, false);

            json_frames.put(f_json);
        }

        repr.put(ControlConst.Stats.FIELD_RUNFRAMELIST, json_frames);

        return repr;
    }

    private static class Frame {
        int id;
        double sent;
        double recv;

        Frame(int id, double sent, double recv) {
            this.id = id;
            this.sent = sent;
            this.recv = recv;
        }

        double getRTT() {
//            Calendar c = Calendar.getInstance();
//            c.setTime(this.sent);
//            long s = c.getTimeInMillis();
//
//            c.setTime(this.recv);
//            long r = c.getTimeInMillis();
//
//            return r - s;

            return this.recv - this.sent;
        }

        JSONObject toJSON() throws JSONException {
            JSONObject repr = new JSONObject();
            repr.put(ControlConst.Stats.FRAMEFIELD_ID, this.id);

//            Calendar c = Calendar.getInstance();
//
//            c.setTime(this.sent);
//            repr.put(StatBackendConstants.FRAMEFIELD_SENT, c.getTimeInMillis());
//
//            c.setTime(this.recv);
//            repr.put(StatBackendConstants.FRAMEFIELD_RECV, c.getTimeInMillis());

            repr.put(ControlConst.Stats.FRAMEFIELD_SENT, this.sent);
            repr.put(ControlConst.Stats.FRAMEFIELD_RECV, this.recv);

            return repr;
        }
    }

}
