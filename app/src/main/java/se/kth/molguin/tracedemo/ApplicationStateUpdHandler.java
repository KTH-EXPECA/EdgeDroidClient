package se.kth.molguin.tracedemo;

import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.util.Log;

import java.lang.ref.WeakReference;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

public class ApplicationStateUpdHandler extends Handler {

    public static final int APP_STATE_MSG = 1;

    private static final String LOG_TAG = "ApplicationStateHandler";
    private static ApplicationStateUpdHandler instance = null;
    private static final ReentrantLock instanceLock = new ReentrantLock();

    public static ApplicationStateUpdHandler getInstance() {
        instanceLock.lock();
        try {
            if (instance == null)
                instance = new ApplicationStateUpdHandler();
            return instance;
        } finally {
            instanceLock.unlock();
        }
    }

    public static void shutdown() {
        instanceLock.lock();
        try {
            instance = null;
        } finally {
            instanceLock.unlock();
        }
    }


    private WeakReference<MainActivity> mAct;
    private final ReadWriteLock locks;

    private ApplicationStateUpdHandler() {
        super(Looper.getMainLooper());
        this.mAct = new WeakReference<>(null);
        this.locks = new ReentrantReadWriteLock();
    }

    public void setMainActivity(MainActivity activity) {
        this.locks.writeLock().lock();
        try {
            this.mAct = new WeakReference<>(activity);
        } finally {
            this.locks.writeLock().unlock();
        }
    }

    @Override
    public void handleMessage(Message msg) {
        switch (msg.what) {
            case APP_STATE_MSG: {
                this.locks.readLock().lock();
                try {
                    MainActivity mainActivity = this.mAct.get();
                    if (mainActivity != null)
                        mainActivity.handleStateUpdate((MainActivity.APPSTATE) msg.obj);
                    else
                        Log.w(LOG_TAG, "No MainActivity?");
                } finally {
                    this.locks.readLock().unlock();
                }
            }
            default:
                Log.w(LOG_TAG, "Unrecognized message type - skipping.");
        }
    }


}
