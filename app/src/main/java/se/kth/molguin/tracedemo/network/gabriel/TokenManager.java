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

    public void getToken() {
        synchronized (lock) {
            while (!hasToken) {
                try {
                    lock.wait();
                } catch (InterruptedException ignored) {
                }
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
