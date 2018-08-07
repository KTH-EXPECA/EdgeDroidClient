package se.kth.molguin.tracedemo;

import android.app.DialogFragment;
import android.arch.lifecycle.Observer;
import android.arch.lifecycle.ViewModelProviders;
import android.graphics.BitmapFactory;
import android.os.Bundle;
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
        viewModel.getLatestRealTimeFrame().observe(this, new Observer<byte[]>() {
            @Override
            public void onChanged(@Nullable byte[] frame) {
                MainActivity.this.handleRealTimeFrameUpdate(frame);
            }
        });
        viewModel.getLatestSentFrame().observe(this, new Observer<byte[]>() {
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
        viewModel.getLatestAppStateMsg().observe(this, new Observer<AppStateMsg>() {
            @Override
            public void onChanged(@Nullable AppStateMsg appStateMsg) {
                if (appStateMsg != null) {
                    switch (appStateMsg.state) {
                        case SUCCESS:
                            MainActivity.this.handleSuccess(appStateMsg.run);
                            break;
                        case ERROR:
                            MainActivity.this.handleError(appStateMsg.step, appStateMsg.msg);
                            break;
                        case STOPPED:
                        case RUNNING:
                        default:
                            // TODO
                            break;
                    }
                }
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

    public void handleSuccess(int run_count) {
        this.getWindow().clearFlags(
                WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
        );

        Bundle b = new Bundle();
        b.putInt("run_count", run_count);
        DialogFragment dialog = new Dialogs.Finished();
        dialog.setArguments(b);
        dialog.show(this.getFragmentManager(), "Done");
    }

    public void handleError(int step, String error) {
        this.getWindow().clearFlags(
                WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
        );

        Bundle b = new Bundle();
        b.putInt("step", step);
        b.putString("error", error);
        DialogFragment dialog = new Dialogs.Error();
        dialog.setArguments(b);
        dialog.show(this.getFragmentManager(), "Error");
    }
}
