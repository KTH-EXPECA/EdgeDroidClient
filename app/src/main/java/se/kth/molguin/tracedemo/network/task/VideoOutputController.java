package se.kth.molguin.tracedemo.network.task;

import android.arch.lifecycle.MutableLiveData;
import android.content.Context;
import android.support.annotation.NonNull;

import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.Locale;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import se.kth.molguin.tracedemo.IntegratedAsyncLog;
import se.kth.molguin.tracedemo.network.control.ControlConst;
import se.kth.molguin.tracedemo.network.control.experiment.Config;
import se.kth.molguin.tracedemo.network.control.experiment.run.RunStats;
import se.kth.molguin.tracedemo.network.gabriel.ProtocolConst;
import se.kth.molguin.tracedemo.network.gabriel.TokenPool;

//import com.instacart.library.truetime.TrueTimeRx;

public class VideoOutputController {

    private final static String LOG_TAG = "VideoOutput";

    private final SynchronizedBuffer<byte[]> frame_buffer;

    private final AtomicBoolean running_flag;
    private final AtomicBoolean task_success;
    private final DataOutputStream dataOut;
    private final Config config;

    private final TokenPool tokenPool;
    private final RunStats stats;

    private final Context appContext;

    private final MutableLiveData<byte[]> sentframe_feed;
    private final MutableLiveData<byte[]> rtframe_feed;

    private final IntegratedAsyncLog log;

    // mutable state:
    private final AtomicInteger frame_counter;
    private final AtomicInteger current_step_idx;

    private final Lock step_lock;
    // locking primitives to notify end of stream!
    private final Lock runLock;
    private final Condition runCond;
    private final ExecutorService execs;
    private TaskStep current_step;

    //private ContentResolver contentResolver;

    public VideoOutputController(@NonNull Config config,
                                 @NonNull Context appContext,
                                 @NonNull RunStats stats,
                                 @NonNull DataOutputStream dataOut,
                                 @NonNull TokenPool tokenPool,
                                 @NonNull MutableLiveData<byte[]> sentframe_feed,
                                 @NonNull MutableLiveData<byte[]> rtframe_feed,
                                 @NonNull IntegratedAsyncLog log) throws FileNotFoundException {
        this.appContext = appContext;
        this.dataOut = dataOut;
        this.config = config;
        this.tokenPool = tokenPool;
        this.sentframe_feed = sentframe_feed;
        this.rtframe_feed = rtframe_feed;
        this.log = log;

        this.current_step_idx = new AtomicInteger(-1);
        this.frame_counter = new AtomicInteger(0);
        this.running_flag = new AtomicBoolean(false);
        this.task_success = new AtomicBoolean(false);
        this.step_lock = new ReentrantLock();

        this.current_step = null;
        this.stats = stats;

        this.frame_buffer = new SynchronizedBuffer<>();
        this.execs = Executors.newSingleThreadExecutor();

        this.runLock = new ReentrantLock();
        this.runCond = this.runLock.newCondition();

        this.goToStep(this.current_step_idx.get());
    }

    public void goToStep(final int step_idx) throws FileNotFoundException {

        if (step_idx >= this.config.num_steps) {
            // reached end of stream
            this.log.i(LOG_TAG, "Reached end of stream!");

            this.task_success.set(true);
            this.running_flag.set(false);

            try {
                this.execs.awaitTermination(100, TimeUnit.MILLISECONDS); // FIXME magic numbah
            } catch (InterruptedException ignored) {
            }
            this.execs.shutdownNow(); // force shutdown if stuck

        } else if (this.current_step_idx.get() != step_idx) {
            this.log.i(LOG_TAG, "Moving to step " + step_idx + " from step " + this.current_step_idx.get());

            this.step_lock.lock();
            try {
                if (this.current_step != null)
                    this.current_step.stop();

                this.current_step = new TaskStep(this.getDataInputStreamForStep(step_idx),
                        this.frame_buffer, this.rtframe_feed, this.log,
                        this.config.fps, this.config.rewind_seconds, this.config.max_replays);

                this.current_step_idx.set(step_idx);

                if (this.running_flag.get())
                    this.current_step.start();
            } finally {
                this.step_lock.unlock();
            }
        }
    }

    private DataInputStream getDataInputStreamForStep(int index) throws FileNotFoundException {
        return new DataInputStream(this.appContext.openFileInput(
                ControlConst.STEP_PREFIX + (index + 1) + ControlConst.STEP_SUFFIX
        ));
    }

    public void awaitFinish() throws InterruptedException {
        this.runLock.lock();
        try {
            while (this.running_flag.get())
                this.runCond.await();
        } finally {
            this.runLock.unlock();
        }
    }


    public void startStream() {
        this.execs.submit(new Callable<Void>() {
            @Override
            public Void call() {
                try {
                    log.i(LOG_TAG, "Starting stream...");
                    running_flag.set(true);

                    step_lock.lock();
                    try {
                        if (current_step == null) {
                            current_step_idx.set(-1);
                            goToStep(0);
                        }

                        current_step.start();
                    } finally {
                        step_lock.unlock();
                    }

                    while (running_flag.get()) {

                        // get a token
                        tokenPool.getToken();
                        // got a token
                        // now get a frame to send
                        final byte[] frame_data = frame_buffer.pop();
                        frame_counter.getAndIncrement();
                        sendFrame(frame_counter.get(), frame_data);
                    }

                } catch (InterruptedException ignored) {
                } catch (IOException e) {
                    log.e(LOG_TAG, "Exception in VideoOutput", e);
                } finally {
                    running_flag.set(false);
                    step_lock.lock();
                    try {
                        if (current_step != null)
                            current_step.stop();
                    } finally {
                        step_lock.unlock();
                    }

                    stats.finish(task_success.get());
                    final String status_msg = task_success.get() ? "Success" : "Failure";
                    log.i(LOG_TAG, "Stream finished. Status: " + status_msg);

                    runLock.lock();
                    try {
                        runCond.signalAll();
                    } finally {
                        runLock.unlock();
                    }

                }
                return null;
            }
        });
    }

    private void sendFrame(int id, byte[] data) throws IOException {
        byte[] header = String.format(Locale.ENGLISH, ProtocolConst.VIDEO_HEADER_FMT, id).getBytes();

        try (// use auxiliary output streams to write everything out at once
             ByteArrayOutputStream baos = new ByteArrayOutputStream();
             DataOutputStream daos = new DataOutputStream(baos)
        ) {
            daos.writeInt(header.length);
            daos.write(header);
            daos.writeInt(data.length);
            daos.write(data);

            byte[] out_data = baos.toByteArray();
            this.dataOut.write(out_data); // send!
            this.dataOut.flush();

            this.stats.registerSentFrame(id);
            this.sentframe_feed.postValue(data);
        }
    }
}
