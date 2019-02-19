/**
 * Copyright 2019 Manuel Olguín
 * 
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *     http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package se.kth.molguin.edgedroid.network.gabriel;

import android.util.Log;

import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

import se.kth.molguin.edgedroid.IntegratedAsyncLog;

public class TokenPool {

    private final static String LOG_TAG = "TokenPool";
    public final static int DEFAULT_MAX_TOKEN_COUNT = 1;

    private final ReentrantLock token_lock;
    private final Condition has_token;
    private final IntegratedAsyncLog log;
    private final int max_token_count;
    private int current_token_count;

    public TokenPool(IntegratedAsyncLog log) throws InterruptedException {
        this(DEFAULT_MAX_TOKEN_COUNT, log);
    }

    public TokenPool(int max_token_count, IntegratedAsyncLog log) throws InterruptedException {

        this.token_lock = new ReentrantLock();
        this.has_token = this.token_lock.newCondition();
        this.log = log;
        this.max_token_count = max_token_count;
        this.reset();
    }

    public void reset() throws InterruptedException {
        log.submitLog(Log.DEBUG, LOG_TAG, "Resetting token count", false);
        this.token_lock.lockInterruptibly();
        try {
            this.current_token_count = this.max_token_count;
            Log.d(LOG_TAG, "New token count: " + this.current_token_count);
            this.has_token.signalAll();
        } finally {
            this.token_lock.unlock();
        }
    }

    public void getToken() throws InterruptedException {
        log.submitLog(Log.DEBUG, LOG_TAG, "Requesting token...", false);
        this.token_lock.lockInterruptibly();
        try {
            while (this.current_token_count <= 0) {
                log.submitLog(Log.DEBUG, LOG_TAG, "No tokens available, wait...", false);
                this.has_token.await();
            }
            this.current_token_count--;
            log.submitLog(Log.DEBUG, LOG_TAG, "Got token!", false);
            log.submitLog(Log.DEBUG, LOG_TAG, "New count: " + this.current_token_count, false);
        } finally {
            this.token_lock.unlock();
        }
    }

    public void putToken() throws InterruptedException {
        log.submitLog(Log.DEBUG, LOG_TAG, "Returning token...", false);
        this.token_lock.lockInterruptibly();
        try {
            this.current_token_count = Math.min(this.current_token_count + 1, this.max_token_count);
            log.submitLog(Log.DEBUG, LOG_TAG, "New count: " + this.current_token_count, false);
            this.has_token.signalAll();
        } finally {
            this.token_lock.unlock();
        }
    }

}
