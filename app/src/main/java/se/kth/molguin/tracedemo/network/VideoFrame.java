package se.kth.molguin.tracedemo.network;

public class VideoFrame {
    private int id;
    private byte[] frame_data;
    private long timestamp;

    VideoFrame(int id, byte[] data, long timestamp) {
        this.id = id;
        this.frame_data = data;

        if (timestamp <= 0)
            this.timestamp = System.currentTimeMillis();
        else
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
