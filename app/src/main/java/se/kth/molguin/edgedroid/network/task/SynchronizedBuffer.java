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

package se.kth.molguin.edgedroid.network.task;

import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

public class SynchronizedBuffer<T> {

    private final Lock lock;
    private final Condition upd_cond;

    private boolean updated;
    private T data;

    public SynchronizedBuffer() {
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

    public T pop() throws InterruptedException {
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
