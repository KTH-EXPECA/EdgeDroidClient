package se.kth.molguin.tracedemo;

import android.arch.lifecycle.MutableLiveData;

public class UILink {

    private final MutableLiveData<byte[]> rt_frame;
    private final MutableLiveData<byte[]> sent_frame;
    private final MutableLiveData<String> log;
    private final MutableLiveData<AppStateMsg> appstatemsg;

    public UILink(final MutableLiveData<byte[]> rt_frame, final MutableLiveData<byte[]> sent_frame,
                  final MutableLiveData<String> log, final MutableLiveData<AppStateMsg> appstatemsg) {
        this.rt_frame = rt_frame;
        this.sent_frame = sent_frame;
        this.log = log;
        this.appstatemsg = appstatemsg;
    }

    public void postRTFrame(final byte[] frame) {
        this.rt_frame.postValue(frame);
    }

    public void postSentFrame(final byte[] frame) {
        this.sent_frame.postValue(frame);
    }

    public void postLogMessage(final String msg) {
        this.log.postValue(msg);
    }

    public void postAppStateMsg(final AppStateMsg msg) {
        this.appstatemsg.postValue(msg);
    }

}
