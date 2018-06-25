package se.kth.molguin.tracedemo;

import android.graphics.Color;

public class Constants {
    // public static final String INCORRECT_MSG_TXT = "incorrect";
    // public static final String TASK_END_MSG_TXT = "Congratulations!";

    // TODO: MOVE TO RESOURCE FILE

    public static final String STATUS_CONNECTINGCONTROL = "Connecting to Control server...";
    public static final String STATUS_CONFIGURING = "Configuring experiment...";
    public static final String STATUS_FETCHTRACE = "Fetching traces from repository...";
    public static final String STATUS_DISCONNECTED = "Disconnected";
    public static final String STATUS_INITEXPERIMENT = "Initializing experiment...";
    public static final String STATUS_STREAMING = "Connected and streaming...";
    public static final String STATUS_STREAM_DONE = "Streaming done. Disconnecting...";
    public static final String STATUS_DISCONNECTING = "Closing connections...";
    public static final String STATUS_NTP_SYNC = "Syncing clocks...";
    public static final String STATUS_UPLOADING = "Uploading experiment data...";
    public static final String STATUS_LISTENING_CONTROL = "Listening for Control commands...";
//    public static final String PREFS_ADDR = "GABRIEL_ADDR";
//    public static final String PREFS_NAME = "TRACEDEMOPREFS";
    public static final String STATS_FMT = "Current RTT: %f ms";

    // TODO: dynamically get these values from config
    public static final int FPS = 15;
    public static final int REWIND_SECONDS = 5;

    public static final String RUN_STATUS_FMT = "Executing run number %d";

    public static final int MAX_REPLAY_COUNT = 3;

    public static final int COLOR_GOOD = Color.GREEN;
    public static final int COLOR_MEDIUM = Color.YELLOW;
    public static final int COLOR_BAD = Color.RED;

}
