package se.kth.molguin.tracedemo.network.gabriel;

import org.apache.commons.math3.stat.descriptive.DescriptiveStatistics;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.net.SocketException;
import java.net.UnknownHostException;
import java.util.HashSet;
import java.util.LinkedList;

import se.kth.molguin.tracedemo.Constants;
import se.kth.molguin.tracedemo.StatBackendConstants;
import se.kth.molguin.tracedemo.synchronization.NTPClient;
import se.kth.molguin.tracedemo.synchronization.NTPClientFactory;

import static java.lang.System.exit;

abstract class Experiment {
    private static final int STAT_WINDOW_SZ = 15;

    public static class Run {
        long init;
        long finish;
        HashSet<Integer> feedback_frames;
        LinkedList<Frame> frames;
        DescriptiveStatistics rtt;
        boolean success;

        NTPClient ntp;

        Run() {
            this.init = -1;
            this.finish = -1;
            this.success = false;

            this.feedback_frames = new HashSet<>();
            this.frames = new LinkedList<>();
            this.rtt = new DescriptiveStatistics(STAT_WINDOW_SZ);

            try {
                this.ntp = NTPClientFactory.getNTPClient(ProtocolConst.SERVER);
            } catch (SocketException | UnknownHostException e) {
                e.printStackTrace();
                exit(-1);
            }
        }

        public void init() {
            this.init = this.ntp.currentTimeMillis();
        }

        public void finish() {
            this.finish = this.ntp.currentTimeMillis();
        }

        public void setSuccess(boolean success) {
            this.success = success;
        }

        public void registerFrame(int frame_id, long sent, long recv, boolean feedback) {
            Frame f = new Frame(frame_id, sent, recv);
            this.frames.add(f);

            if (feedback)
                this.feedback_frames.add(frame_id);

            this.rtt.addValue(f.getRTT());
        }

        public double getRollingRTT() {
            return this.rtt.getMean();
        }

        long getInitTimestamp() {
//            Calendar c = Calendar.getInstance();
//            c.setTime(this.init);
//            return c.getTimeInMillis();

            return this.init;
        }

        long getFinishTimestamp() {
//            Calendar c = Calendar.getInstance();
//            c.setTime(this.finish);
//            return c.getTimeInMillis();

            return this.init;
        }

        public JSONObject toJSON() throws JSONException {
            JSONObject repr = new JSONObject();

            repr.put(StatBackendConstants.FIELD_RUNBEGIN, this.getInitTimestamp());
            repr.put(StatBackendConstants.FIELD_RUNEND, this.getFinishTimestamp());
            repr.put(StatBackendConstants.FIELD_RUNSUCCESS, this.success);

            JSONArray json_frames = new JSONArray();
            for (Frame f : this.frames) {
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
        long sent;
        long recv;

        Frame(int id, long sent, long recv) {
            this.id = id;
            this.sent = sent;
            this.recv = recv;
        }

        long getRTT() {
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
            repr.put(StatBackendConstants.FRAMEFIELD_ID, this.id);

//            Calendar c = Calendar.getInstance();
//
//            c.setTime(this.sent);
//            repr.put(StatBackendConstants.FRAMEFIELD_SENT, c.getTimeInMillis());
//
//            c.setTime(this.recv);
//            repr.put(StatBackendConstants.FRAMEFIELD_RECV, c.getTimeInMillis());

            repr.put(StatBackendConstants.FRAMEFIELD_SENT, this.sent);
            repr.put(StatBackendConstants.FRAMEFIELD_RECV, this.recv);

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

    }
}
