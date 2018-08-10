package se.kth.molguin.tracedemo;

import android.os.Bundle;

public class AppStateMsg {

    public enum STATE {
        STOPPED,
        RUNNING,
        SUCCESS,
        ERROR
    }

    public final STATE state;
    public final Bundle payload;

    public AppStateMsg(STATE state, Bundle payload) {
        this.state = state;
        this.payload = payload;
    }
}
