package se.kth.molguin.tracedemo;

public class StatBackendConstants {
    public static final int STATSERVERPORT = 5000;
    public static final String STATSERVERENDPOINT = "/experiment_results";

    public static final String FIELD_CLIENTID = "client_id";
    public static final String FIELD_TASKNAME = "task_name";
    public static final String FIELD_TASKBEGIN = "task_init";
    public static final String FIELD_TASKEND = "task_end";
    public static final String FIELD_TASKSUCCESS = "task_success";
    public static final String FIELD_FRAMELIST = "frames";

    public static final String FRAMEFIELD_ID = "frame_id";
    public static final String FRAMEFIELD_SENT = "sent";
    public static final String FRAMEFIELD_RECV = "recv";
    public static final String FRAMEFIELD_FEEDBACK = "feedback";

    public static final String TASKSUCCESS_STR = "success";
    public static final String TASKERROR_STR = "error";
}
