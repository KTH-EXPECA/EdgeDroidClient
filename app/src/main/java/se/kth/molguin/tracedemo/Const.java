package se.kth.molguin.tracedemo;

public final class Const {
    // port protocol to the server
    public static final int VIDEO_STREAM_PORT = 9098;
    // not used (yet):
    // public static final int ACC_STREAM_PORT = 9099;
    // public static final int AUDIO_STREAM_PORT = 9100;
    public static final int RESULT_RECEIVING_PORT = 9111;
    public static final int CONTROL_PORT = 22222;

    // server IP
    public static final String SERVER = "kth-8604.ee.kth.se";  // Cloudlet
    // token size
    public static final int TOKEN_SIZE = 1;

    public static final String VIDEO_HEADER_FMT = "{\"frame_id\":%d}";
}
