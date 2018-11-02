package se.kth.molguin.edgedroid.synchronization;

import android.util.Log;

public class NullNTPSync implements INTPSync {
    private double null_sync_result() {
        Log.w("NTP", "NTP not synced!");
        return 0.d;
    }

    @Override
    public double getOffset() {
        return null_sync_result();
    }

    @Override
    public double getDelay() {
        return null_sync_result();
    }

    @Override
    public double getOffsetError() {
        return null_sync_result();
    }

    @Override
    public double getDelayError() {
        return null_sync_result();
    }

    @Override
    public double currentTimeMillis() {
        return System.currentTimeMillis() + null_sync_result();
    }
}
