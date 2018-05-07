package se.kth.molguin.tracedemo;

public class Constants {
    // public static final String INCORRECT_MSG_TXT = "incorrect";
    // public static final String TASK_END_MSG_TXT = "Congratulations!";

    public static final String STEP_PREFIX = "step_";
    public static final String STEP_SUFFIX = ".trace";

    public static final String EXPCONFIG_ID = "experiment_id";
    public static final String EXPCONFIG_CLIENTIDX = "client_id";
    public static final String EXPCONFIG_RUNS = "runs";
    public static final String EXPCONFIG_STEPS = "steps";
    public static final String EXPCONFIG_TRACE = "trace_root_url";
    public static final String EXPCONFIG_PORTS = "ports";
    public static final String EXPPORTS_VIDEO = "video";
    public static final String EXPPORTS_CONTROL = "control";
    public static final String EXPPORTS_RESULT = "result";

    public static final String CONNECT_TXT = "Connect";
    public static final String DISCONNECT_TXT = "Disconnect";
    public static final String STATUS_CONNECTINGCONTROL = "Connecting to Control server...";
    public static final String STATUS_CONNECTEDCONTROL = "Connected to Control server";
    public static final String STATUS_CONFIGURING = "Configuring experiment...";
    public static final String STATUS_FETCHTRACE = "Fetching traces from repository...";
    public static final String STATUS_WAITFOREXPERIMENT = "Waiting for experiment start...";
    public static final String STATUS_DISCONNECTED = "Disconnected";
    public static final String STATUS_INITEXPERIMENT = "Initializing experiment...";
    public static final String STATUS_CONNECTED = "Connected";
    public static final String STATUS_STREAMING = "Connected and streaming...";
    public static final String STATUS_STREAM_DONE = "Streaming done. Disconnecting...";
    public static final String STATUS_DISCONNECTING = "Closing connections...";
    public static final String STATUS_NTP_SYNC = "Syncing clocks...";
    public static final String STATUS_UPLOADING = "Uploading experiment data...";
    public static final String STATUS_LISTENING_CONTROL = "Listening for Control commands...";
    public static final String PREFS_ADDR = "GABRIEL_ADDR";
    public static final String PREFS_NAME = "TRACEDEMOPREFS";
    public static final String STATS_FMT = "Current RTT: %f ms";
    public static final int FPS = 15;
    public static final int REWIND_SECONDS = 5;

    public static final String RUN_STATUS_FMT = "Executing run number %d";

    public static final int MAX_REPLAY_COUNT = 3;
}
