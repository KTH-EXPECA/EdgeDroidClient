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

package se.kth.molguin.edgedroid.network.control.experiment.run;

import android.arch.lifecycle.MutableLiveData;
import android.content.Context;
import android.support.annotation.NonNull;
import android.util.Log;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.util.Locale;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import se.kth.molguin.edgedroid.IntegratedAsyncLog;
import se.kth.molguin.edgedroid.network.control.ControlConst;
import se.kth.molguin.edgedroid.network.control.experiment.Config;
import se.kth.molguin.edgedroid.network.control.experiment.Sockets;
import se.kth.molguin.edgedroid.network.gabriel.ProtocolConst;
import se.kth.molguin.edgedroid.network.gabriel.TokenPool;
import se.kth.molguin.edgedroid.network.task.SynchronizedBuffer;
import se.kth.molguin.edgedroid.network.task.TaskStep;
import se.kth.molguin.edgedroid.synchronization.INTPSync;

public class Run {
    private static final String LOG_TAG = "ExperimentRun";
    private final IntegratedAsyncLog log;

    private final SynchronizedBuffer<byte[]> frame_buffer;
    private final AtomicBoolean running_flag;
    private final AtomicBoolean task_success;
    private final Config config;
    private final TokenPool tokenPool;
    private final RunStats stats;
    private final Context appContext;

    private final MutableLiveData<byte[]> sentframe_feed;
    private final MutableLiveData<byte[]> rtframe_feed;

    // mutable state:
    private final AtomicInteger frame_counter;
    private final AtomicInteger current_step_idx;

    private final Lock step_lock;
    // locking primitives to notify end of stream!
    private final ExecutorService execs;

    @NonNull // step should never be null
    private TaskStep current_step;

    public Run(@NonNull final Config config,
               @NonNull final INTPSync ntp,
               @NonNull final Context appContext,
               @NonNull final IntegratedAsyncLog log,
               @NonNull final MutableLiveData<byte[]> rtframe_feed,
               @NonNull final MutableLiveData<byte[]> sentframe_feed)
            throws InterruptedException, FileNotFoundException {

        this.log = log;
        this.appContext = appContext;
        this.config = config;

        this.sentframe_feed = sentframe_feed;
        this.rtframe_feed = rtframe_feed;

        this.log.i(LOG_TAG, "Initiating new Experiment Run");
        this.execs = Executors.newFixedThreadPool(2); // Magic number since it shouldn't ever need to change anyway
        this.stats = new RunStats(ntp);
        this.tokenPool = new TokenPool(this.log);

        this.frame_buffer = new SynchronizedBuffer<>();
        this.running_flag = new AtomicBoolean(false);
        this.task_success = new AtomicBoolean(false);
        this.frame_counter = new AtomicInteger(0);
        this.current_step_idx = new AtomicInteger(0);

        this.step_lock = new ReentrantLock();

        this.current_step = new TaskStep(this.getDataInputStreamForStep(this.current_step_idx.get()),
                this.frame_buffer, this.rtframe_feed, this.log,
                this.config.fps, this.config.rewind_seconds, this.config.max_replays);
    }

    private DataInputStream getDataInputStreamForStep(int index) throws FileNotFoundException {
        return new DataInputStream(this.appContext.openFileInput(
                ControlConst.STEP_PREFIX + (index + 1) + ControlConst.STEP_SUFFIX
        ));
    }

    public JSONObject getRunStats() throws RunStats.RunStatsException, JSONException {
        return this.stats.toJSON();
    }

    public boolean succeeded() {
        return this.stats.succeeded();
    }

    public void executeAndWait() throws ExecutionException {

        try (
                final Sockets sockets = new Sockets(this.config);
                final DataInputStream dataIn = new DataInputStream(sockets.result.getInputStream());
                final DataOutputStream dataOut = new DataOutputStream(sockets.video.getOutputStream());
        ) {

            this.running_flag.set(true);
            this.stats.init();

            final Future streamTask = this.execs.submit(new Runnable() {
                @Override
                public void run() {
                    stream(dataOut);
                }
            });

            final Future listenTask = this.execs.submit(new Runnable() {
                @Override
                public void run() {
                    listen(dataIn);
                }
            });


            // wait for task completion
            // listener thread will exit when it gets the final feedback from the backend
            // after that, we just interrupt the stream thread
            listenTask.get();
            streamTask.cancel(true);
            this.execs.awaitTermination(100, TimeUnit.MILLISECONDS);

            this.stats.finish(task_success.get());
        } catch (InterruptedException e) {
            // clean shutdown
        } catch (IOException e) {
            // socket error?
            this.log.e(LOG_TAG, "Error communicating with backend!!, e");
        } catch (RunStats.RunStatsException e) {
            this.log.e(LOG_TAG, "Error collecting stats!", e);
        } finally {
            this.execs.shutdownNow();
        }

        final String status_msg = task_success.get() ? "Success" : "Failure";
        this.log.i(LOG_TAG, "Stream finished. Status: " + status_msg);
    }

    private void stream(DataOutputStream dataOut) {
        try {
            if (!running_flag.get())
                return;

            log.i(LOG_TAG, "Starting stream...");
            this.current_step.start();

            while (running_flag.get()) {

                // get a token
                tokenPool.getToken();
                // got a token
                // now get a frame to send
                final byte[] frame_data = frame_buffer.pop();
                final int current_frame_id = frame_counter.incrementAndGet();

                sendFrame(dataOut, current_frame_id, frame_data);

                this.stats.registerSentFrame(current_frame_id);
                this.sentframe_feed.postValue(frame_data);
            }

        } catch (InterruptedException ignored) {
        } catch (IOException e) {
            this.log.e(LOG_TAG, "Exception in VideoOutput", e);
        } catch (RunStats.RunStatsException e) {
            this.log.e(LOG_TAG, "Error collecting stats!", e);
        } finally {
            current_step.stop();
        }
    }

    private static void sendFrame(DataOutputStream dataOut, int id, byte[] data) throws IOException {
        byte[] header = String.format(Locale.ENGLISH, ProtocolConst.VIDEO_HEADER_FMT, id).getBytes();

        try (// use auxiliary output streams to write everything out at once
             ByteArrayOutputStream baos = new ByteArrayOutputStream();
             DataOutputStream daos = new DataOutputStream(baos)
        ) {
            daos.writeInt(header.length);
            daos.write(header);
            daos.writeInt(data.length);
            daos.write(data);

            byte[] out_data = baos.toByteArray();
            dataOut.write(out_data); // send!
            dataOut.flush();
        }
    }

    private void listen(DataInputStream dataIn) {
        try {
            while (running_flag.get()) {

                // get incoming message size:
                final int len = dataIn.readInt();
                final byte[] msg_b = new byte[len];
                dataIn.readFully(msg_b);

                // read the message into a string
//                        int readSize = 0;
//                        while (readSize < len) {
//                            int ret = dataIn.read(msg_b, readSize, len - readSize);
//                            if (ret <= 0) {
//                                throw new IOException();
//                            }
//                            readSize += ret;
//                        }

                final String msg_s = new String(msg_b, "UTF-8");

                try {
                    // parse the string into a JSON
                    final JSONObject msg = new JSONObject(msg_s);
                    final String status = msg.getString(ProtocolConst.HEADER_MESSAGE_STATUS);
                    final JSONObject result = new JSONObject(msg.getString(ProtocolConst.HEADER_MESSAGE_RESULT));

                    final boolean feedback = status.equals(ProtocolConst.STATUS_SUCCESS);
                    final int state_index = feedback ? result.getInt(ProtocolConst.HEADER_MESSAGE_STATE_IDX) : -1;
                    final long frameID = msg.getLong(ProtocolConst.HEADER_MESSAGE_FRAME_ID);


                    if (feedback && state_index >= 0) {
                        // differentiate different types of messages
                        this.changeStep(state_index);
                        //TODO: Do something in case of error (state index < 0) ¯\_(ツ)_/¯
                    }

                    // we got a valid message, give back a token
                    tokenPool.putToken();

                    try {
                        final double server_sent = result.getDouble(ProtocolConst.HEADER_MESSAGE_SERVER_SEND);
                        final double server_recv = result.getDouble(ProtocolConst.HEADER_MESSAGE_SERVER_RECV);

                        stats.registerReceivedFrame((int) frameID, feedback, server_recv, server_sent, state_index);
                    } catch (JSONException e) {
                        log.submitLog(Log.WARN, LOG_TAG, "Server send/recv timestamps not found in incoming message.", false);
                        stats.registerReceivedFrame((int) frameID, feedback, state_index);
                    }


                } catch (JSONException e) {
                    log.w(LOG_TAG, "Received message is not valid Gabriel message.", e);
                } catch (RunStats.RunStatsException e) {
                    this.log.e(LOG_TAG, "Error collecting stats!", e);
                }
            }
        } catch (InterruptedException ignored) {
            // shutdown smoothly
        } catch (UnsupportedEncodingException e) {
            // This should never happen??
            this.log.e(LOG_TAG, "Impossible exception!", e);
        } catch (IOException e) {
            // Socket closed...
            this.log.e(LOG_TAG, "Input socket prematurely closed!", e);
        }
    }

    private void changeStep(int new_step_idx) {
        // change step, set appropiate flags

        if (new_step_idx >= this.config.num_steps) {
            // reached the end of the stream!
            // cleanly shut down
            this.running_flag.set(false);
            this.task_success.set(true);
            this.current_step.stop();
        } else if (this.current_step_idx.get() != new_step_idx) {
            // need to change step

            this.log.i(LOG_TAG, "Moving to step " + new_step_idx + " from step " + this.current_step_idx.get());

            this.current_step.stop();

            this.step_lock.lock();
            try {
                this.current_step = new TaskStep(this.getDataInputStreamForStep(new_step_idx),
                        this.frame_buffer, this.rtframe_feed, this.log, this.config.fps,
                        this.config.rewind_seconds, this.config.max_replays);
            } catch (FileNotFoundException e) {
                this.log.e(LOG_TAG, "Could not find find trace file for step " + new_step_idx + "!!");
                this.log.e(LOG_TAG, "FATAL ERROR (Should never happen!!)");
                this.running_flag.set(false);
                return;
            } finally {
                this.step_lock.unlock();
            }

            this.current_step_idx.set(new_step_idx);
            if (this.running_flag.get())
                this.current_step.start();

        }

    }
}
