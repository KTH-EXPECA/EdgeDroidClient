package se.kth.molguin.tracedemo;

import android.arch.lifecycle.Observer;
import android.arch.lifecycle.ViewModelProviders;
import android.graphics.BitmapFactory;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.WindowManager;
import android.widget.ImageView;

import java.util.concurrent.locks.ReentrantLock;

public class MainActivity extends AppCompatActivity {

    private static final String LOG_TAG = "TraceDemoMainActivity";
    ImageView sent_frame_view;
    ImageView new_frame_view;

    TimestampLogTextView log_view;
    ReentrantLock stream_lock;
    double current_rtt;

    AppViewModel viewModel;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Log.i(LOG_TAG, "Starting...");
        Thread.setDefaultUncaughtExceptionHandler(new CrashHandler());
        setContentView(R.layout.activity_main);

        // keep screen on while task running
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        this.log_view = this.findViewById(R.id.log_view);
        this.sent_frame_view = this.findViewById(R.id.sent_frame_view);
        this.new_frame_view = this.findViewById(R.id.new_frame_view);

        this.stream_lock = new ReentrantLock();
        this.current_rtt = -1;

        // find the viewmodel
        viewModel = ViewModelProviders.of(this).get(AppViewModel.class);
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
        viewModel.getLatestLogMsg().observe(this, new Observer<String>() {
            @Override
            public void onChanged(@Nullable String msg) {
                MainActivity.this.handleInfoUpdate(msg);
            }
        });
        viewModel.getShutdownMessage().observe(this, new Observer<ShutdownMessage>() {

            @Override
            public void onChanged(@Nullable ShutdownMessage shutdownMessage) {
                if (shutdownMessage != null)
                    MainActivity.this.handleShutdownMessage(shutdownMessage);
            }
        });
    }

    @Override
    protected void onDestroy() {
        Log.w(LOG_TAG, "onDestroy() called!");
        super.onDestroy();
    }

    public void handleInfoUpdate(String info) {
        this.log_view.log(info);
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
}
