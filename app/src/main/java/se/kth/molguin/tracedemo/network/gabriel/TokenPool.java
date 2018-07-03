package se.kth.molguin.tracedemo.network.gabriel;

import android.util.Log;

import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

public class TokenPool {

    private final static String LOG_TAG = "TokenPool";
    public final static int DEFAULT_MAX_TOKEN_COUNT = 1;

    private ReentrantLock token_lock;
    private Condition has_token;

    private int max_token_count;
    private int current_token_count;

    public TokenPool() {
        this(DEFAULT_MAX_TOKEN_COUNT);
    }

    public TokenPool(int max_token_count) {

        this.token_lock = new ReentrantLock();
        this.has_token = this.token_lock.newCondition();

        this.max_token_count = max_token_count;
        this.reset();
    }

    public void getToken() throws InterruptedException {
        Log.d(LOG_TAG, "Requesting token...");
        this.token_lock.lock();
        try {
            while (this.current_token_count <= 0) {
                Log.d(LOG_TAG, "No tokens available, wait.");
                this.has_token.await();
            }
            this.current_token_count--;
            Log.d(LOG_TAG, "Got token!");
            Log.d(LOG_TAG, "New token count: " + this.current_token_count);
        } finally {
            this.token_lock.unlock();
        }
    }

    public void putToken() {
        Log.d(LOG_TAG, "Returning token...");
        this.token_lock.lock();
        try {
            this.current_token_count = Math.min(this.current_token_count + 1, this.max_token_count);
            Log.d(LOG_TAG, "New token count: " + this.current_token_count);
            this.has_token.signalAll();
        } finally {
            this.token_lock.unlock();
        }
    }

    public void reset() {
        Log.d(LOG_TAG, "Resetting token count...");
        this.token_lock.lock();
        try {
            this.current_token_count = this.max_token_count;
            Log.d(LOG_TAG, "New token count: " + this.current_token_count);
            this.has_token.signalAll();
        } finally {
            this.token_lock.unlock();
        }
    }

}
