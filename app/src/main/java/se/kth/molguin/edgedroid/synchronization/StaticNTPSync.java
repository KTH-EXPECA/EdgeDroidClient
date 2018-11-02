package se.kth.molguin.edgedroid.synchronization;

public class StaticNTPSync implements INTPSync {

    private final double offset;
    private final double delay;
    private final double offset_error;
    private final double delay_error;

    protected StaticNTPSync(final double offset, final double delay,
                            final double offset_error, final double delay_error) {
        this.offset = offset;
        this.delay = delay;
        this.offset_error = offset_error;
        this.delay_error = delay_error;
    }

    @Override
    public double getOffset() {
        return this.offset;
    }

    @Override
    public double getDelay() {
        return this.delay;
    }

    @Override
    public double getOffsetError() {
        return this.offset_error;
    }

    @Override
    public double getDelayError() {
        return this.delay_error;
    }

    @Override
    public double currentTimeMillis() {
        return System.currentTimeMillis() + this.offset;
    }
}
