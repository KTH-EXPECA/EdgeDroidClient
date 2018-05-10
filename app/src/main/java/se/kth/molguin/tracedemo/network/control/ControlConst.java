package se.kth.molguin.tracedemo.network.control;

final class ControlConst {

    // Protocol definition for control server commands

    final static int CONTROL_PORT = 1337;

    final static int STATUS_SUCCESS = 0x00000001;
    final static int STATUS_ERROR = 0xffffffff;

    final static int MSG_EXPERIMENT_FINISH = 0x000000b1;

    final static int CMD_PUSH_CONFIG = 0x000000a1;
    final static int CMD_PULL_STATS = 0x000000a2;
    final static int CMD_START_EXP = 0x000000a3;
    final static int CMD_FETCH_TRACES = 0x000000a4;
    final static int CMD_NTP_SYNC = 0x000000a5;

    final static int CMD_SHUTDOWN = 0x000000af;
}
