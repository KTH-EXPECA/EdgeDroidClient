package se.kth.molguin.tracedemo;

import android.os.AsyncTask;

import java.io.IOException;

import se.kth.molguin.tracedemo.network.gabriel.ConnectionManager;
import se.kth.molguin.tracedemo.network.gabriel.TokenManager;

import static java.lang.System.exit;

class Tasks {
    public static class ConnectTask extends AsyncTask<Void, Void, Void> {
        @Override
        protected Void doInBackground(Void... voids) {
            ConnectionManager cm = ConnectionManager.getInstance();
            try {
                cm.initConnections();
            } catch (ConnectionManager.ConnectionManagerException e) {
                // TODO: deal with issues connecting, which shouldn't happen??
                e.printStackTrace();
                exit(-1);
            }
            return null;
        }
    }

    public static class DisconnectTask extends AsyncTask<Void, Void, Void> {
        @Override
        protected Void doInBackground(Void... voids) {
            ConnectionManager cm = ConnectionManager.getInstance();
            try {
                cm.shutDown();
            } catch (InterruptedException | IOException e) {
                e.printStackTrace();
                exit(-1);
            }
            return null;
        }
    }

    public static class StreamStartTask extends AsyncTask<Void, Void, Void> {

        @Override
        protected Void doInBackground(Void... voids) {
            ConnectionManager cm = ConnectionManager.getInstance();
            try {
                TokenManager.getInstance().putToken(); // to avoid hangs
                cm.startStreaming();
            } catch (IOException e) {
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
