package se.kth.molguin.tracedemo;

import android.graphics.Color;

public class Constants {

    // TODO: dynamically get these values from config
    public static final int FPS = 15;
    public static final int REWIND_SECONDS = 5;
    public static final int MAX_REPLAY_COUNT = 3;
    //

    // TODO: move to resources
    public static final String RUN_STATUS_FMT = "Executing run number %d";
    public static final String STATS_FMT = "Current RTT: %f ms";
    //

    public static final int COLOR_GOOD = Color.GREEN;
    public static final int COLOR_MEDIUM = Color.YELLOW;
    public static final int COLOR_BAD = Color.RED;

}
