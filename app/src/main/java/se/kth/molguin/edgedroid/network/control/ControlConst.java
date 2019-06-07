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
    public static final class ConfigFields {
        public static final String ID = "experiment_id";
        public static final String CLIENTIDX = "client_id";
        public static final String STEPS = "steps";
        public static final String NTP = "ntp_server";
        public static final String PORTS = "ports";
        public static final String FPS = "fps";
        public static final String REWIND_SECONDS = "rewind_seconds";
        public static final String MAX_REPLAYS = "max_replays";
        public static final String GOOD_LATENCY = "good_latency_bound";
        public static final String BAD_LATENCY = "bad_latency_bound";
        public static final String TARGET_OFFSET_ERROR = "target_offset_error";

        public static final class Ports {
            public static final String VIDEO = "video";
            public static final String CONTROL = "control";
            public static final String RESULT = "result";
        }
    }

    public static final class Commands {
        public final static int PUSH_CONFIG = 0x000000a1;
        public final static int PULL_STATS = 0x000000a2;
        public final static int START_EXP = 0x000000a3;
        public final static int PUSH_STEP = 0x000000a4;
        public final static int TIME_SYNC = 0x000000a5;
        public final static int SHUTDOWN = 0x000000af;

        public static final class TimeSync {
            public final static int SYNC_START = 0xa0000001;
            public final static int SYNC_END = 0xa0000002;
            public final static int SYNC_BEACON = 0xa0000010;
            public final static int SYNC_BEACON_REPLY = 0xa0000011;
        }
    }

    public static final class Status {
        public final static int SUCCESS = 0x00000001;
        public final static int ERROR = 0xffffffff;
    }

    public static final class StepMetadata {
        public static final String INDEX = "index";
        public static final String SIZE = "size";
        public static final String CHECKSUM = "md5checksum";
    }

    public static final class ServerConstants {
        public final static int PORT = 1337;
        public static final String IPv4_ADDRESS = "192.168.0.100";  // Cloudlet
    }

    public static final class Defaults {
        public static final int GOOD_LATENCY = 600;
        public static final int BAD_LATENCY = 2700;
        public static final int TARGET_OFFSET_ERROR = 750;
    }

    public final static int MSG_EXPERIMENT_FINISH = 0x000000b1;
    public static final String STEP_PREFIX = "step_";
    public static final String STEP_SUFFIX = ".trace";

    public static final class StatFields {
        // Stat results fields
        public static final String CLIENT_ID = "client_id";
        public static final String TASK_NAME = "experiment_id";
        public static final String PORTS = "ports";
        public static final String RUN_RESULTS = "run_results";

        public static final class Run {
            public static final String INIT = "init";
            public static final String END = "end";
            public static final String SUCCESS = "success";
            public static final String FRAME_LIST = "frames";
            public static final String TIMESTAMP_DRIFT_ERROR = "clock_drift_error";
            public static final String TIMESTAMP_OFFSET_ERROR = "clock_offset_error";
        }

        public final class FrameFields {
            public static final String ID = "frame_id";
            public static final String SENT = "sent";
            public static final String RECV = "recv";
            public static final String FEEDBACK = "feedback";
            public static final String SERVER_SENT = "server_sent";
            public static final String SERVER_RECV = "server_recv";
            public static final String STATE_IDX = "state_index";
        }
    }
}
