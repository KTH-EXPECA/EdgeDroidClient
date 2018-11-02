package se.kth.molguin.edgedroid;

import android.arch.lifecycle.LiveData;
import android.arch.lifecycle.MutableLiveData;
import android.util.Log;

import java.util.LinkedList;
import java.util.Queue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicBoolean;

public class IntegratedAsyncLog {

    public static class LogEntry {
        final long timestamp;
        final String tag;
        final int level;
        final String log;

        private LogEntry(int level, String tag, String log) {

            this.timestamp = System.currentTimeMillis();

            this.tag = tag;
            this.level = level;
            this.log = log;
        }
    }

    private final BlockingQueue<LogEntry> backlog;
    private final MutableLiveData<LogEntry> log_feed; // TODO: single live event?
    private final ExecutorService execs;
    private final Future internal_task;

    private final AtomicBoolean run_flag;

    public IntegratedAsyncLog() {

        this.backlog = new LinkedBlockingQueue<>();
        this.log_feed = new MutableLiveData<>();
        this.run_flag = new AtomicBoolean(true);

        this.execs = Executors.newSingleThreadExecutor();
        this.internal_task = this.execs.submit(new Runnable() {
            @Override
            public void run() {
                processLog();
            }
        });

    }

    public LiveData<LogEntry> getLogFeed() {
        return log_feed;
    }

    public void submitLog(int level, String tag, String log, boolean postToUI) {
        final LogEntry entry = new LogEntry(level, tag, log);
        this.backlog.offer(entry);
        if (postToUI)
            this.log_feed.postValue(entry);
    }

    public void d(String tag, String msg) {
        this.submitLog(Log.DEBUG, tag, msg, true);
    }

    public void d(String tag, String msg, Throwable tr) {
        final String exception_msg = Log.getStackTraceString(tr);
        final String log = msg + "\n" + exception_msg;
        this.submitLog(Log.DEBUG, tag, log, true);
    }

    public void e(String tag, String msg) {
        this.submitLog(Log.ERROR, tag, msg, true);
    }

    public void e(String tag, String msg, Throwable tr) {
        final String exception_msg = Log.getStackTraceString(tr);
        final String log = msg + "\n" + exception_msg;
        this.submitLog(Log.ERROR, tag, log, true);
    }

    public void i(String tag, String msg) {
        this.submitLog(Log.INFO, tag, msg, true);
    }

    public void i(String tag, String msg, Throwable tr) {
        final String exception_msg = Log.getStackTraceString(tr);
        final String log = msg + "\n" + exception_msg;
        this.submitLog(Log.INFO, tag, log, true);
    }

    public void v(String tag, String msg) {
        this.submitLog(Log.VERBOSE, tag, msg, true);
    }

    public void v(String tag, String msg, Throwable tr) {
        final String exception_msg = Log.getStackTraceString(tr);
        final String log = msg + "\n" + exception_msg;
        this.submitLog(Log.VERBOSE, tag, log, true);
    }

    public void w(String tag, String msg) {
        this.submitLog(Log.WARN, tag, msg, true);
    }

    public void w(String tag, String msg, Throwable tr) {
        final String exception_msg = Log.getStackTraceString(tr);
        final String log = msg + "\n" + exception_msg;
        this.submitLog(Log.WARN, tag, log, true);
    }

    public void cancel() {
        this.run_flag.set(false);
        this.internal_task.cancel(true);
        this.execs.shutdown();
    }

    private void processLog() {
        try {
            while (run_flag.get()) {
                final LogEntry entry = this.backlog.take();
                Log.println(entry.level, entry.tag, entry.log);
            }
        } catch (InterruptedException ignored) {
            // Interrupted means we should calmly shutdown
        }

        // handle leftovers
        final Queue<LogEntry> leftovers = new LinkedList<>();
        this.backlog.drainTo(leftovers);

        while (!leftovers.isEmpty()) {
            final LogEntry entry = leftovers.poll();
            Log.println(entry.level, entry.tag, entry.log);
        }
    }
}
