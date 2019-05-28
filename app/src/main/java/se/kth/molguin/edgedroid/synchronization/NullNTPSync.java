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

package se.kth.molguin.edgedroid.synchronization;

import android.util.Log;

public class NullNTPSync implements INTPSync {
    private double null_sync_result() {
        Log.w("NTP", "NTP not synced!");
        return 0.d;
    }

    @Override
    public double getOffset() {
        return null_sync_result();
    }

    @Override
    public double getDelay() {
        return null_sync_result();
    }

    @Override
    public double getOffsetError() {
        return null_sync_result();
    }

    @Override
    public double getDelayError() {
        return null_sync_result();
    }

    @Override
    public double currentTimeMillis() {
        return System.currentTimeMillis() + null_sync_result();
    }
}
