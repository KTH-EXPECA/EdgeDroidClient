package se.kth.molguin.tracedemo.network.gabriel;

import com.instacart.library.truetime.TrueTimeRx;

import org.apache.commons.math3.stat.descriptive.DescriptiveStatistics;
import org.apache.commons.math3.stat.descriptive.SummaryStatistics;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.Calendar;
import java.util.Date;
import java.util.HashSet;
import java.util.LinkedList;

import se.kth.molguin.tracedemo.Constants;
import se.kth.molguin.tracedemo.StatBackendConstants;

public abstract class Experiment {
    private static final int STAT_WINDOW_SZ = 15;

    public static class Run {
        Date init;
        Date finish;
        HashSet<Integer> feedback_frames;
        LinkedList<Frame> frames;
        DescriptiveStatistics rtt;
        boolean success;

        public Run()
        {
            this.init = null;
            this.finish = null;
            this.success = false;

            this.feedback_frames = new HashSet<>();
            this.frames = new LinkedList<>();
            this.rtt = new DescriptiveStatistics(STAT_WINDOW_SZ);
        }

        public void init()
        {
            this.init = TrueTimeRx.now();
        }

        public void finish()
        {
            this.finish = TrueTimeRx.now();
        }

        public void setSuccess(boolean success) {
            this.success = success;
        }

        public void registerFrame(int frame_id, Date sent, Date recv, boolean feedback)
        {
            Frame f = new Frame(frame_id, sent, recv);
            this.frames.add(f);

            if (feedback)
                this.feedback_frames.add(frame_id);

            this.rtt.addValue(f.getRTT());
        }

        public double getRollingRTT()
        {
            return this.rtt.getMean();
        }

        long getInitTimestamp()
        {
            Calendar c = Calendar.getInstance();
            c.setTime(this.init);
            return c.getTimeInMillis();
        }

        long getFinishTimestamp()
        {
            Calendar c = Calendar.getInstance();
            c.setTime(this.finish);
            return c.getTimeInMillis();
        }

        public JSONObject toJSON() throws JSONException
        {
            JSONObject repr = new JSONObject();

            repr.put(StatBackendConstants.FIELD_RUNBEGIN, this.getInitTimestamp());
            repr.put(StatBackendConstants.FIELD_RUNEND, this.getFinishTimestamp());
            repr.put(StatBackendConstants.FIELD_RUNSUCCESS, this.success);

            JSONArray json_frames = new JSONArray();
            for (Frame f: this.frames)
            {
                JSONObject f_json = f.toJSON();
                if (this.feedback_frames.contains(f.id))
                    f_json.put(StatBackendConstants.FRAMEFIELD_FEEDBACK, true);
                else f_json.put(StatBackendConstants.FRAMEFIELD_FEEDBACK, false);

                json_frames.put(f_json);
            }

            repr.put(StatBackendConstants.FIELD_FRAMELIST, json_frames);

            return repr;
        }
    }

    private static class Frame {
        int id;
        Date sent;
        Date recv;

        Frame(int id, Date sent, Date recv)
        {
            this.id = id;
            this.sent = sent;
            this.recv = recv;
        }

        long getRTT()
        {
            Calendar c = Calendar.getInstance();
            c.setTime(this.sent);
            long s = c.getTimeInMillis();

            c.setTime(this.recv);
            long r = c.getTimeInMillis();

            return r - s;
        }

        JSONObject toJSON() throws JSONException {
            JSONObject repr = new JSONObject();
            repr.put(StatBackendConstants.FRAMEFIELD_ID, this.id);

            Calendar c = Calendar.getInstance();

            c.setTime(this.sent);
            repr.put(StatBackendConstants.FRAMEFIELD_SENT, c.getTimeInMillis());

            c.setTime(this.recv);
            repr.put(StatBackendConstants.FRAMEFIELD_RECV, c.getTimeInMillis());

            return repr;
        }
    }

    public static class Config {
        String experiment_id;
        int client_id;
        int runs;
        int steps;
        String trace_url;

        int video_port;
        int control_port;
        int result_port;

        Config(JSONObject json) throws JSONException {
            this.experiment_id = json.getString(Constants.EXPCONFIG_ID);
            this.client_id = json.getInt(Constants.EXPCONFIG_CLIENTIDX);
            this.runs = json.getInt(Constants.EXPCONFIG_RUNS);
            this.steps = json.getInt(Constants.EXPCONFIG_STEPS);
            this.trace_url = json.getString(Constants.EXPCONFIG_TRACE);

            JSONObject ports = json.getJSONObject(Constants.EXPCONFIG_PORTS);
            this.video_port = ports.getInt(Constants.EXPPORTS_VIDEO);
            this.control_port = ports.getInt(Constants.EXPPORTS_CONTROL);
            this.result_port = ports.getInt(Constants.EXPPORTS_RESULT);
        }

        public JSONObject toJSON() throws JSONException {
            JSONObject ports = new JSONObject();
            ports.put(Constants.EXPPORTS_VIDEO, this.video_port);
            ports.put(Constants.EXPPORTS_CONTROL, this.control_port);
            ports.put(Constants.EXPPORTS_RESULT, this.result_port);

            JSONObject config = new JSONObject();
            config.put(Constants.EXPCONFIG_ID, this.experiment_id);
            config.put(Constants.EXPCONFIG_CLIENTIDX, this.client_id);
            config.put(Constants.EXPCONFIG_RUNS, this.runs);
            config.put(Constants.EXPCONFIG_STEPS, this.steps);
            config.put(Constants.EXPCONFIG_TRACE, this.trace_url);
            config.put(Constants.EXPCONFIG_PORTS, ports);

            return config;
        }
    }
}
