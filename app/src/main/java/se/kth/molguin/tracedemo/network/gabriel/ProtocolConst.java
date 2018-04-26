package se.kth.molguin.tracedemo.network.gabriel;

public class ProtocolConst {
    // port protocol to the server
    public static final int VIDEO_STREAM_PORT = 9098;
    // not used (yet):
    // public static final int ACC_STREAM_PORT = 9099;
    // public static final int AUDIO_STREAM_PORT = 9100;
    public static final int RESULT_RECEIVING_PORT = 9111;
    public static final int CONTROL_PORT = 22222;

    // server IP
    public static final String SERVER = "192.168.0.100";  // Cloudlet
    // token size
    //public static final int TOKEN_SIZE = 1;

    public static final int NETWORK_RET_FAILED = 1;
    public static final int NETWORK_RET_SPEECH = 2;
    public static final int NETWORK_RET_CONFIG = 3;
    public static final int NETWORK_RET_TOKEN = 4;
    public static final int NETWORK_RET_IMAGE = 5;
    public static final int NETWORK_RET_VIDEO = 6;
    public static final int NETWORK_RET_ANIMATION = 7;
    public static final int NETWORK_RET_MESSAGE = 8;
    public static final int NETWORK_RET_DONE = 9;
    public static final int NETWORK_RET_SYNC = 10;

    public static final String HEADER_MESSAGE_STATUS = "status";
    public static final String HEADER_MESSAGE_CONTROL = "control";
    public static final String HEADER_MESSAGE_RESULT = "result";
    public static final String HEADER_MESSAGE_INJECT_TOKEN = "token_inject";
    public static final String HEADER_MESSAGE_FRAME_ID = "frame_id";
    public static final String HEADER_MESSAGE_ENGINE_ID = "engine_id";

    public static final String STATUS_SUCCESS = "success";

    public static final String SENSOR_TYPE_KEY = "sensor_type";
    public static final String SENSOR_JPEG = "mjpeg";
    public static final String SENSOR_ACC = "acc";
    public static final String SENSOR_GPS = "gps";
    public static final String SENSOR_AUDIO = "audio";

    public static final String SERVER_CONTROL_SENSOR_TYPE_IMAGE = SENSOR_JPEG;
    public static final String SERVER_CONTROL_SENSOR_TYPE_ACC = SENSOR_ACC;
    public static final String SERVER_CONTROL_SENSOR_TYPE_AUDIO = SENSOR_AUDIO;
    public static final String SERVER_CONTROL_FPS = "fps";
    public static final String SERVER_CONTROL_IMG_WIDTH = "img_width";
    public static final String SERVER_CONTROL_IMG_HEIGHT = "img_height";

    public static final String VIDEO_HEADER_FMT = "{\"frame_id\":%d}";
}
