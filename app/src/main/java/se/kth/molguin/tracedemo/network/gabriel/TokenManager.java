package se.kth.molguin.tracedemo.network.gabriel;

import java.util.concurrent.Semaphore;

public class TokenManager {

    private static TokenManager instance = null;
    private final static int DEFAULT_TOKEN_COUNT = 1;

    //    private static final Object lock = new Object();
    private Semaphore token_pool;
//    private boolean hasToken;

    public synchronized static TokenManager getInstance() {
        if (instance == null)
            instance = new TokenManager();
        return instance;
    }

    private TokenManager() {
        // hasToken = true;
        this.token_pool = new Semaphore(DEFAULT_TOKEN_COUNT, true);
    }

    public void getToken() throws InterruptedException {
//        synchronized (lock) {
//            while (!hasToken) {
//                lock.wait();
//            }
//            hasToken = false;
//        }
        this.token_pool.acquire();
    }

    public void putToken() {
//        synchronized (lock) {
//            if (!hasToken) hasToken = true;
//            lock.notify();
//        }
        this.token_pool.release();
    }

}
