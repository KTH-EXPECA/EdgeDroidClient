/**
 * Copyright 2019 Manuel Olguín
 * <p>
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package se.kth.molguin.edgedroid;

import android.app.Application;
import android.arch.lifecycle.AndroidViewModel;
import android.arch.lifecycle.LiveData;
import android.util.Log;

import se.kth.molguin.edgedroid.network.control.ControlClient;

public class AppViewModel extends AndroidViewModel {
    /*
    Interface layer between MainActivity and application logic.
     */

    private final ControlClient client;
    private final IntegratedAsyncLog log;

    public AppViewModel(Application app) {
        // initialize everything here

        super(app);

        this.log = new IntegratedAsyncLog();
        this.client = new ControlClient(app.getApplicationContext(), this.log);
        this.client.init();
    }

    public LiveData<Double> getRTTFeed() {
        return this.client.getRTTFeed();
    }

    public LiveData<byte[]> getRealTimeFrameFeed() {
        return this.client.getRealTimeFrameFeed();
    }

    public LiveData<byte[]> getSentFrameFeed() {
        return this.client.getSentFrameFeed();
    }

    public LiveData<IntegratedAsyncLog.LogEntry> getLogFeed() {
        return this.log.getLogFeed();
    }

    public LiveData<ShutdownMessage> getShutdownEvent() {
        return this.client.getShutdownEvent();
    }

    @Override
    protected void onCleared() {
        try {
            this.client.cancel();
            this.log.cancel();
        } catch (Exception e) {
            Log.e("ViewModel", "Exception", e);
        }
    }
}
