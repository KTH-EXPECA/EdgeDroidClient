package se.kth.molguin.tracedemo;

import java.lang.ref.WeakReference;

import se.kth.molguin.tracedemo.network.gabriel.ConnectionManager;

public class MonitoringThread extends Thread {

    private static MonitoringThread instance = null;
    private static final Object lock = new Object();

    public static MonitoringThread getInstance() {
        synchronized (lock) {
            if (instance == null)
                instance = new MonitoringThread();
            return instance;
        }
    }

    private WeakReference<MainActivity> mainActivity;
    private boolean running;

    private MonitoringThread() {
        this.mainActivity = null;
        this.running = false;
    }

    public void setMainActivity(MainActivity mainActivity) {
        synchronized (lock) {
            this.mainActivity = new WeakReference<>(mainActivity);
        }
    }

    public boolean isRunning() {
        synchronized (lock) {
            return this.running;
        }

    }

    public void stopRunning() {
        synchronized (lock) {
            this.running = false;
            this.interrupt();
        }
    }

    private void updateOnState(ConnectionManager.CMSTATE state)
    {
        // setup based on state
        switch (state)
        {
            case DISCONNECTED:
                // TODO: Set up as disconnected.
                break;
            case CONNECTED:
                // TODO: Set up as connected.
                break;
            case CONNECTING:
                // TODO: set up as connecting
                break;
            case STREAMING:
                // TODO: set up as streaming.
                break;
            case DISCONNECTING:
                // TODO: set up as disconnecting.
                break;
            default:
                break;
        }
    }

    @Override
    public void run() {

        this.running = true;
        ConnectionManager cm = ConnectionManager.getInstance();

        // initial state
        this.updateOnState(cm.getState());

        while (true) {
            synchronized (lock) {
                if (!running)
                    break;
            }

            try {
                cm.waitForStateChange(); // wait until something happens
            } catch (InterruptedException ignored) {
                //e.printStackTrace();
                continue;
            }

            updateOnState(cm.getState());
        }
    }
}
