package se.kth.molguin.tracedemo.network.gabriel;

public class TokenManager {

    private static TokenManager instance = null;

    private static final Object lock = new Object();
    private boolean hasToken;

    public static TokenManager getInstance() {
        synchronized (lock){
            if (instance == null)
                instance = new TokenManager();
            return instance;
        }
    }

    private TokenManager() {
        hasToken = true;
    }

    public void getToken() throws InterruptedException {
        synchronized (lock) {
            while (!hasToken) {
                lock.wait();
            }
            hasToken = false;
        }
    }

    public void putToken() {
        synchronized (lock) {
            if (!hasToken) hasToken = true;
            lock.notify();
        }
    }

}
