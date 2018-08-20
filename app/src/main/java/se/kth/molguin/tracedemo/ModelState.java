package se.kth.molguin.tracedemo;

import android.arch.lifecycle.MutableLiveData;
import android.content.Context;
import android.support.annotation.NonNull;

public class ModelState {

    private final Context appContext;
    private final MutableLiveData<byte[]> rt_frame;
    private final MutableLiveData<byte[]> sent_frame;
    private final MutableLiveData<String> log;
    private final MutableLiveData<ShutdownMessage> shutdownMsg;

    public ModelState(@NonNull final Context appContext,
                      @NonNull final MutableLiveData<byte[]> rt_frame,
                      @NonNull final MutableLiveData<byte[]> sent_frame,
                      @NonNull final MutableLiveData<String> log,
                      @NonNull final MutableLiveData<ShutdownMessage> shutdownMsg) {
        this.appContext = appContext;
        this.rt_frame = rt_frame;
        this.sent_frame = sent_frame;
        this.log = log;
        this.shutdownMsg = shutdownMsg;
    }

    public Context getAppContext() {
        return appContext;
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

    public void postAppStateMsg(final ShutdownMessage msg) {
        this.shutdownMsg.postValue(msg);
    }

}
