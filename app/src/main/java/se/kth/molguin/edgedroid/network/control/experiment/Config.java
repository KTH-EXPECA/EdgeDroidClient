/**
 * Copyright 2019 Manuel Olguín
 * <p>
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package se.kth.molguin.edgedroid.network.control.experiment;

import org.json.JSONException;
import org.json.JSONObject;

import se.kth.molguin.edgedroid.network.control.ControlConst;

public class Config {
    public final String experiment_id;
    public final int client_id;
    //public int runs;
    public final int num_steps;
    // public String trace_url;
    public final String ntp_host;

    public final String server;

    public final int video_port;
    public final int control_port;
    public final int result_port;

    public final int fps;
    public final int rewind_seconds;
    public final int max_replays;

    // TODO: USE THESE!
    public final int good_latency_bound;
    public final int bad_latency_bound;

    public final int target_offset_error_ms;

    public Config(JSONObject json) throws JSONException {
        this.experiment_id = json.getString(ControlConst.ConfigFields.ID);
        this.client_id = json.getInt(ControlConst.ConfigFields.CLIENTIDX);
        //this.runs = json.getInt(Constants.EXPCONFIG_RUNS);
        this.num_steps = json.getInt(ControlConst.ConfigFields.STEPS);
        this.fps = json.getInt(ControlConst.ConfigFields.FPS);
        this.rewind_seconds = json.getInt(ControlConst.ConfigFields.REWIND_SECONDS);
        this.max_replays = json.getInt(ControlConst.ConfigFields.MAX_REPLAYS);
        // this.trace_url = json.getString(ControlConst.EXPCONFIG_TRACE);
        this.ntp_host = json.getString(ControlConst.ConfigFields.NTP);
        this.bad_latency_bound = json.optInt(ControlConst.ConfigFields.BAD_LATENCY,
                ControlConst.Defaults.BAD_LATENCY);
        this.good_latency_bound = json.optInt(ControlConst.ConfigFields.GOOD_LATENCY,
                ControlConst.Defaults.GOOD_LATENCY);

        this.target_offset_error_ms = json.optInt(ControlConst.ConfigFields.TARGET_OFFSET_ERROR_MS,
                ControlConst.Defaults.TARGET_OFFSET_ERROR_MS);

        JSONObject ports = json.getJSONObject(ControlConst.ConfigFields.PORTS);
        this.video_port = ports.getInt(ControlConst.ConfigFields.Ports.VIDEO);
        this.control_port = ports.getInt(ControlConst.ConfigFields.Ports.CONTROL);
        this.result_port = ports.getInt(ControlConst.ConfigFields.Ports.RESULT);

        this.server = ControlConst.ServerConstants.IPv4_ADDRESS; // TODO: For now
    }

}
