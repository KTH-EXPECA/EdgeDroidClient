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


package se.kth.molguin.edgedroid.network.control;

@SuppressWarnings("WeakerAccess")
public final class ControlConst {
    public static final String EXPCONFIG_ID = "experiment_id";
    public static final String EXPCONFIG_CLIENTIDX = "client_id";
    public static final String EXPCONFIG_STEPS = "steps";
    public static final String EXPCONFIG_NTP = "ntp_server";
    public static final String EXPCONFIG_PORTS = "ports";
    public static final String EXPPORTS_VIDEO = "video";
    public static final String EXPPORTS_CONTROL = "control";
    public static final String EXPPORTS_RESULT = "result";
    public static final String EXPCONFIG_FPS = "fps";
    public static final String EXPCONFIG_REWIND_SECONDS = "rewind_seconds";
    public static final String EXPCONFIG_MAX_REPLAYS = "max_replays";
    public static final String EXPCONFIG_GOOD_LATENCY = "good_latency_bound";
    public static final String EXPCONFIG_BAD_LATENCY = "bad_latency_bound";

    public static final String STEP_METADATA_INDEX = "index";
    public static final String STEP_METADATA_SIZE = "size";
    public static final String STEP_METADATA_CHKSUM = "md5checksum";

    // Protocol definition for control server commands

    public final static int CONTROL_PORT = 1337;

    public final static int STATUS_SUCCESS = 0x00000001;
    public final static int STATUS_ERROR = 0xffffffff;

    public final static int MSG_EXPERIMENT_FINISH = 0x000000b1;

    public final static int CMD_PUSH_CONFIG = 0x000000a1;
    public final static int CMD_PULL_STATS = 0x000000a2;
    public final static int CMD_START_EXP = 0x000000a3;
    // public final static int CMD_FETCH_TRACES = 0x000000a4;
    public final static int CMD_PUSH_STEP = 0x000000a4;
    public final static int CMD_NTP_SYNC = 0x000000a5;

    public final static int CMD_SHUTDOWN = 0x000000af;
    public static final String STEP_PREFIX = "step_";
    public static final String STEP_SUFFIX = ".trace";

    public static final int DEFAULT_GOOD_LATENCY_MS = 600;
    public static final int DEFAULT_BAD_LATENCY_MS = 2700;
    // server IP
    public static final String SERVER = "192.168.0.100";  // Cloudlet
    //public static final String SERVER = "130.237.43.83";  // Cloudlet

    public static final class Stats {

        // Stat results fields
        public static final String FIELD_CLIENTID = "client_id";
        public static final String FIELD_TASKNAME = "experiment_id";
        public static final String FIELD_PORTS = "ports";
        public static final String FIELD_RUNRESULTS = "run_results";
        public static final String FIELD_RUNBEGIN = "init";
        public static final String FIELD_RUNEND = "end";
        public static final String FIELD_RUNSUCCESS = "success";
        public static final String FIELD_RUNTIMESTAMPERROR = "timestamp_error";
        public static final String FIELD_RUNNTPOFFSET = "ntp_offset";
        public static final String FIELD_RUNFRAMELIST = "frames";
        public static final String FRAMEFIELD_ID = "frame_id";
        public static final String FRAMEFIELD_SENT = "sent";
        public static final String FRAMEFIELD_RECV = "recv";
        public static final String FRAMEFIELD_FEEDBACK = "feedback";
        public static final String FRAMEFIELD_SERVERSENT = "server_sent";
        public static final String FRAMEFIELD_SERVERRECV = "server_recv";
        public static final String FRAMEFIELD_STATEIDX = "state_index";
    }
}
