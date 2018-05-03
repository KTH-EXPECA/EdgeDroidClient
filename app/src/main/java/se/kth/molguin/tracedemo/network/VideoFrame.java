package se.kth.molguin.tracedemo.network;

import java.util.Date;

public class VideoFrame {
    private int id;
    private byte[] frame_data;
    private long timestamp;

    VideoFrame(int id, byte[] data, long timestamp) {
        this.id = id;
        this.frame_data = data;
        this.timestamp = timestamp;
    }

    public int getId() {
        return id;
    }

    public byte[] getFrameData() {
        return frame_data;
    }

    public long getTimestamp() {
        return timestamp;
    }
}
