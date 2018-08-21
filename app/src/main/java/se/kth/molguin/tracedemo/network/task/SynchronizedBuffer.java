package se.kth.molguin.tracedemo.network.task;

import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

class SynchronizedBuffer<T> {

    private final Lock lock;
    private final Condition upd_cond;

    private boolean updated;
    private T data;

    SynchronizedBuffer(){
        this.updated = false;
        this.data = null;
        this.lock = new ReentrantLock();
        this.upd_cond = this.lock.newCondition();
    }

    void push(T new_data) {
        this.lock.lock();
        try {
            // overwrite old data
            this.data = new_data;
            this.updated = true;
            this.upd_cond.signalAll();
        } finally {
            this.lock.unlock();
        }
    }

    T pop() throws InterruptedException {
        this.lock.lock();
        try {
            while (!this.updated)
                this.upd_cond.await();
            this.updated = false;
            return data;
        } finally {
            this.lock.unlock();
        }
    }

}
