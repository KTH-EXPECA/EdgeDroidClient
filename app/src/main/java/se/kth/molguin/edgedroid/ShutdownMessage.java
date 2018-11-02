package se.kth.molguin.edgedroid;

public class ShutdownMessage {

    public final boolean success;
    public final int completed_runs;
    public final String msg;

    public ShutdownMessage(boolean success, int completed_runs, String msg) {
        this.success = success;
        this.completed_runs = completed_runs;
        this.msg = msg;
    }
}
