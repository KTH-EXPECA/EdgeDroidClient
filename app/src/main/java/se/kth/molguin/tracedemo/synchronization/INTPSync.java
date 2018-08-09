package se.kth.molguin.tracedemo.synchronization;

public interface INTPSync {
    double getOffset();
    double getDelay();
    double getOffsetError();
    double getDelayError();
    double currentTimeMillis();
}
