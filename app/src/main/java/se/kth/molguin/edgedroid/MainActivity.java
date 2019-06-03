/**
 * Copyright 2019 Manuel Olguín
<<<<<<< HEAD
 * <p>
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p>
=======
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
>>>>>>> good and bad latency bounds in config
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package se.kth.molguin.edgedroid;

import android.arch.lifecycle.Observer;
import android.arch.lifecycle.ViewModelProviders;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v7.app.AppCompatActivity;
import android.view.WindowManager;
import android.widget.ImageView;
import android.widget.TextView;

import java.util.Locale;

public class MainActivity extends AppCompatActivity {

    private static final String LOG_TAG = "EdgeDroidMainActivity";
    ImageView sent_frame_view;
    ImageView new_frame_view;

    TimestampLogTextView log_view;

    TextView rtt_view;
    TextView qoe_view;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // keep screen on while task running
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        this.log_view = this.findViewById(R.id.log_view);
        this.sent_frame_view = this.findViewById(R.id.sent_frame_view);
        this.new_frame_view = this.findViewById(R.id.new_frame_view);
        this.rtt_view = this.findViewById(R.id.avg_rtt_txt);
        this.qoe_view = this.findViewById(R.id.qoe_txt);

        // find the viewmodel
        AppViewModel viewModel = ViewModelProviders.of(this).get(AppViewModel.class);
        viewModel.getRealTimeFrameFeed().observe(this, new Observer<byte[]>() {
            @Override
            public void onChanged(@Nullable byte[] frame) {
                MainActivity.this.handleRealTimeFrameUpdate(frame);
            }
        });
        viewModel.getSentFrameFeed().observe(this, new Observer<byte[]>() {
            @Override
            public void onChanged(@Nullable byte[] frame) {
                MainActivity.this.handleSentFrameUpdate(frame);
            }
        });
        viewModel.getLogFeed().observe(this, new Observer<IntegratedAsyncLog.LogEntry>() {
            @Override
            public void onChanged(@Nullable IntegratedAsyncLog.LogEntry msg) {
                MainActivity.this.handleLogFeed(msg);
            }
        });
        viewModel.getShutdownEvent().observe(this, new Observer<ShutdownMessage>() {

            @Override
            public void onChanged(@Nullable ShutdownMessage shutdownMessage) {
                if (shutdownMessage != null)
                    MainActivity.this.handleShutdownMessage(shutdownMessage);
            }
        });
        viewModel.getRTTFeed().observe(this, new Observer<Double>() {
            @Override
            public void onChanged(@Nullable Double rtt) {
                MainActivity.this.handleRTTUpdate(rtt);
            }
        });
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
    }

    public void handleLogFeed(IntegratedAsyncLog.LogEntry logEntry) {

        // FIXME use timestamp from log entry
        this.log_view.log(logEntry.log);
    }

    public void handleRealTimeFrameUpdate(byte[] frame) {
        this.new_frame_view.setImageBitmap(BitmapFactory.decodeByteArray(frame, 0, frame.length));
    }

    public void handleSentFrameUpdate(byte[] frame) {
        this.sent_frame_view.setImageBitmap(BitmapFactory.decodeByteArray(frame, 0, frame.length));
    }

    public void handleShutdownMessage(@NonNull ShutdownMessage message) {

        Dialogs.ShutDown dialog = new Dialogs.ShutDown();
        dialog.setParams(message.success, message.completed_runs, message.msg);
        dialog.show(this.getFragmentManager(), "Shutdown");

    }

    public void handleRTTUpdate(double rtt) {
        // TODO: hardcoded ranges need to be fixed
        int color;
        String qoe = "";
        if (rtt < 200) {
            color = Color.GREEN;
            qoe = "GOOD";
        } else if (rtt < 600) {
            color = Color.YELLOW;
            qoe = "IMPAIRED";
        } else {
            color = Color.RED;
            qoe = "UNUSABLE";
        }

        this.rtt_view.setTextColor(color);
        this.qoe_view.setTextColor(color);
        this.rtt_view.setText(String.format(Locale.ENGLISH, "%.4f ms", rtt));
        this.qoe_view.setText(qoe);
    }
}
