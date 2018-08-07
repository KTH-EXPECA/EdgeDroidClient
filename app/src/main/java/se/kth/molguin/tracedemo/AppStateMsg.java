package se.kth.molguin.tracedemo;

public class AppStateMsg {

    enum STATE {
        STOPPED,
        RUNNING,
        SUCCESS,
        ERROR
    }

    public final STATE state;
    public final String msg;
    public final int step;
    public final int run;

    public AppStateMsg(STATE state, String msg, int step, int run) {
        this.state = state;
        this.msg = msg;
        this.step = step;
        this.run = run;
    }
}
