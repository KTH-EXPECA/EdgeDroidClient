package se.kth.molguin.tracedemo;

import android.content.Context;

import se.kth.molguin.tracedemo.network.control.ControlClient;

public class StateManager {

    final ControlClient client;
    final UILink uiLink;

    public StateManager(Context appContext, UILink uiLink)
    {
        this.uiLink
        this.client = new ControlClient(appContext);
    }

}
