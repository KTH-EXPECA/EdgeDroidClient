package se.kth.molguin.tracedemo;

import java.lang.ref.WeakReference;

import se.kth.molguin.tracedemo.network.gabriel.ConnectionManager;

public class MonitoringThread extends Thread {

    private static final Object lock = new Object();

    private WeakReference<MainActivity> mainActivity;
    private boolean running;
    private ConnectionManager.CMSTATE previous_state;

    MonitoringThread(MainActivity mainActivity) {
        this.mainActivity = new WeakReference<>(mainActivity);
        this.running = false;
        this.previous_state = null;
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
        this.previous_state = state;
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
                    new Tasks.StreamStartTask().execute();
                    break;
                case CONNECTING:
                    if (act != null)
                        act.stateConnecting();
                    break;
                case STREAMING:
                    if (act != null)
                        act.stateStreaming();
                    break;
                case STREAMING_DONE:
                    if (act != null)
                        act.stateStreamingEnd();
                    new Tasks.DisconnectTask().execute();
                    break;
                case DISCONNECTING:
                    if (act != null)
                        act.stateDisconnecting();
                    break;
                case NTPSYNC:
                    if (act != null)
                        act.stateNTPSync();
                    break;
                case UPLOADINGRESULTS:
                    if (act != null)
                        act.stateUploading();
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
        //this.updateOnState(cm.getState());

        while (true) {
            synchronized (lock) {
                if (!running)
                    break;
            }

            ConnectionManager.CMSTATE new_state = cm.getState();
            if (this.previous_state != new_state)
                this.updateOnState(new_state);
            else {
                try {
                    cm.waitForStateChange(); // wait until something happens
                } catch (InterruptedException ignored) {
                    //e.printStackTrace();
                    continue;
                }
                this.updateOnState(cm.getState());
            }
        }

        synchronized (lock) {
            this.running = false;
        }
    }

}
