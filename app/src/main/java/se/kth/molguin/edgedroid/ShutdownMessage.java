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

package se.kth.molguin.edgedroid;

public class ShutdownMessage {

    public final boolean success;
    public final int completed_runs;
    public final String msg;

    public ShutdownMessage(boolean success, int completed_runs, String msg) {
        this.success = success;
        this.completed_runs = completed_runs;
        this.msg = msg;
    }
}
