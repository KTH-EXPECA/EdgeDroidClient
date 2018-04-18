package se.kth.molguin.tracedemo;

public class Constants {
    // public static final String INCORRECT_MSG_TXT = "incorrect";
    // public static final String TASK_END_MSG_TXT = "Congratulations!";

    // state (step) information
    private static final int BACKEND_ERROR_STATE = -1;
    private static final int NUM_STEPS = 7;


    public static final String TRACE_ERROR_TXT = "Invalid selection";
    public static final String CONNECT_TXT = "Connect";
    public static final String DISCONNECT_TXT = "Disconnect";
    public static final String STATUS_DISCONNECTED_FMT = "Disconnected";
    public static final String STATUS_CONNECTING_FMT = "Connecting to %s...";
    public static final String STATUS_CONNECTED_FMT = "Connected to %s";
    public static final String STATUS_STREAMING_FMT = "Connected and streaming to %s";
    public static final String STATUS_STREAM_DONE_FMT = "Streaming done. Disconnecting...";
    public static final String STATUS_DISCONNECTING_FMT = "Closing connections to %s...";
    public static final String STATUS_NTP_SYNC_FMT = "Sync clock with %s...";
    public static final String STATUS_UPLOADING_FMT = "Uploading experiment data...";
    public static final String STATUS_ERROR_FMT = "ERROR: PLEASE RESTART THE APPLICATION";
    public static final String PREFS_ADDR = "GABRIEL_ADDR";
    public static final String PREFS_CLIENTID = "CLIENT_ID";
    public static final String PREFS_NAME = "TRACEDEMOPREFS";
    public static final String STATS_FMT = "Current RTT: %f ms";
    public static final int FPS = 15;
    public static final int REWIND_SECONDS = 5;
    public static final int MIN_MISTAKE_COUNT = 10;
    public static final String TASKNAME = "GabrielLEGO";
    public static final float MAX_NTP_DISPERSION = 10.0f;
}
