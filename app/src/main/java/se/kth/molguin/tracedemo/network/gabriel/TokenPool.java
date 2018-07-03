package se.kth.molguin.tracedemo.network.gabriel;

import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

public class TokenPool {

    private static final ReentrantLock instance_lock = new ReentrantLock();

    private static TokenPool instance = null;
    private final static int MAX_TOKEN_COUNT = 1;

    private ReentrantLock token_lock;
    private Condition has_token;

    private int token_count;

    public static TokenPool getInstance() {
        instance_lock.lock();
        try {
            if (instance == null)
                instance = new TokenPool();
        } finally {
            instance_lock.unlock();
        }
        return instance;
    }

    private TokenPool() {

        this.token_lock = new ReentrantLock();
        this.has_token = this.token_lock.newCondition();

        this.token_count = MAX_TOKEN_COUNT;
    }

    public void getToken() throws InterruptedException {
        this.token_lock.lock();
        try {
            while (this.token_count <= 0)
                    this.has_token.await();
            this.token_count--;
        } finally {
            this.token_lock.unlock();
        }
    }

    public void putToken() {
        this.token_lock.lock();
        try {
            this.token_count = Math.min(this.token_count + 1, MAX_TOKEN_COUNT);
            this.has_token.signalAll();
        } finally {
            this.token_lock.unlock();
        }
    }

    public void reset() {
        this.token_lock.lock();
        try {
            this.token_count = MAX_TOKEN_COUNT;
            this.has_token.signalAll();
        } finally {
            this.token_lock.unlock();
        }
    }

}
