package se.kth.molguin.tracedemo;

public class StatBackendConstants {

    public static final String EXP_CONTROL_ADDRESS = "192.168.0.100";
    public static final int EXP_CONTROL_PORT = 1337;
    public static final int EXP_CONTROL_SUCCESS = 0x00000001;
    public static final int EXP_CONTROL_GETSTATS = 0x00000003;

    public static final String FIELD_CLIENTID = "client_id";
    public static final String FIELD_TASKNAME = "experiment_id";
    public static final String FIELD_TASKBEGIN = "task_init";
    public static final String FIELD_TASKEND = "task_end";
    public static final String FIELD_TASKSUCCESS = "task_success";
    public static final String FIELD_FRAMELIST = "frames";

    public static final String FRAMEFIELD_ID = "frame_id";
    public static final String FRAMEFIELD_SENT = "sent";
    public static final String FRAMEFIELD_RECV = "recv";
    public static final String FRAMEFIELD_FEEDBACK = "feedback";
}
