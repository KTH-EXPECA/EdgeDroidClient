package se.kth.molguin.edgedroid.network.task;

public class VideoFrame {
    private int id;
    private byte[] frame_data;
    private double timestamp;

    VideoFrame(int id, byte[] data, double timestamp) {
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

    public double getTimestamp() {
        return timestamp;
    }
}
