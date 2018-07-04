package se.kth.molguin.tracedemo.network.control.experiment;

import org.json.JSONException;
import org.json.JSONObject;

import se.kth.molguin.tracedemo.network.control.ControlConst;

public class Config {
    public String experiment_id;
    public int client_id;
    //public int runs;
    public int num_steps;
    // public String trace_url;
    public String ntp_host;

    public String server;

    public int video_port;
    public int control_port;
    public int result_port;

    public int fps;
    public int rewind_seconds;
    public int max_replays;

    public Config(JSONObject json) throws JSONException {
        this.experiment_id = json.getString(ControlConst.EXPCONFIG_ID);
        this.client_id = json.getInt(ControlConst.EXPCONFIG_CLIENTIDX);
        //this.runs = json.getInt(Constants.EXPCONFIG_RUNS);
        this.num_steps = json.getInt(ControlConst.EXPCONFIG_STEPS);
        this.fps = json.getInt(ControlConst.EXPCONFIG_FPS);
        this.rewind_seconds = json.getInt(ControlConst.EXPCONFIG_REWIND_SECONDS);
        this.max_replays = json.getInt(ControlConst.EXPCONFIG_MAX_REPLAYS);
        // this.trace_url = json.getString(ControlConst.EXPCONFIG_TRACE);
        this.ntp_host = json.getString(ControlConst.EXPCONFIG_NTP);

        JSONObject ports = json.getJSONObject(ControlConst.EXPCONFIG_PORTS);
        this.video_port = ports.getInt(ControlConst.EXPPORTS_VIDEO);
        this.control_port = ports.getInt(ControlConst.EXPPORTS_CONTROL);
        this.result_port = ports.getInt(ControlConst.EXPPORTS_RESULT);

        this.server = ControlConst.SERVER; // TODO: For now
    }

}
