package se.kth.molguin.tracedemo;

import android.os.AsyncTask;

import java.io.IOException;
import java.lang.ref.WeakReference;

import se.kth.molguin.tracedemo.network.gabriel.ConnectionManager;

import static java.lang.System.exit;

public class MonitoringThread extends Thread {

    private static final Object lock = new Object();

    private WeakReference<MainActivity> mainActivity;
    private boolean running;

    MonitoringThread(MainActivity mainActivity) {
        this.mainActivity = new WeakReference<>(mainActivity);
        this.running = false;
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

    private void updateOnState(ConnectionManager.CMSTATE state) {
        synchronized (lock) {
            // setup based on state
            // trigger transitions in ConnectionManager
            MainActivity act = this.mainActivity.get();
            switch (state) {
                case DISCONNECTED:
                    if (act != null)
                        act.stateDisconnected();
                    break;
                case CONNECTED:
                    if (act != null)
                        act.stateConnected();
                    // start streaming
                    new StreamStartTask().execute();
                    break;
                case CONNECTING:
                    if (act != null)
                        act.stateConnecting();
                    break;
                case STREAMING:
                    if (act != null)
                        act.stateStreaming();
                    break;
                case DISCONNECTING:
                    if (act != null)
                        act.stateDisconnecting();
                    break;
                default:
                    break;
            }
        }
    }

    @Override
    public void run() {

        synchronized (lock) {
            this.running = true;
        }
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

        synchronized (lock) {
            this.running = false;
        }
    }

    private static class StreamStartTask extends AsyncTask<Void, Void, Void> {

        @Override
        protected Void doInBackground(Void... voids) {
            ConnectionManager cm = ConnectionManager.getInstance();
            try {
                cm.startStreaming();
            } catch (InterruptedException | IOException e) {
                e.printStackTrace();
                exit(-1);
            } catch (ConnectionManager.ConnectionManagerException e) {
                // TODO: deal with shit that shouldn't happen?
                e.printStackTrace();
            }
            return null;
        }
    }
}
