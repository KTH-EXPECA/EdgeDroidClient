package se.kth.molguin.tracedemo.network.control;

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
    public static final String SERVER = "130.237.43.83";  // Cloudlet
}
