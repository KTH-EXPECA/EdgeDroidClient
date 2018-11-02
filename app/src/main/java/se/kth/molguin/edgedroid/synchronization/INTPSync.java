package se.kth.molguin.edgedroid.synchronization;

public interface INTPSync {
    double getOffset();
    double getDelay();
    double getOffsetError();
    double getDelayError();
    double currentTimeMillis();
}
