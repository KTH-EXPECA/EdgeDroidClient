package se.kth.molguin.tracedemo;

import java.util.concurrent.locks.Lock;

public final class Utils {

    /**
     * Runs an arbitrary Runnable within a synchronized try-finally block using a given Lock.
     *
     * @param lock     Lock to synchronize on.
     * @param runnable Code to execute.
     */
    public static void synchronizeOnLock(Lock lock, Runnable runnable) {
        lock.lock();
        try {
            runnable.run();
        } finally {
            lock.unlock();
        }
    }
}
