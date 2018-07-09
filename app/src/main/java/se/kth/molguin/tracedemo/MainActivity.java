package se.kth.molguin.tracedemo;

import android.app.DialogFragment;
import android.graphics.BitmapFactory;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.WindowManager;
import android.widget.ImageView;

import java.util.concurrent.locks.ReentrantLock;

public class MainActivity extends AppCompatActivity {

    private static final String LOG_TAG = "TraceDemoMainActivity";

    //    Button connect;
    // EditText address; TODO: add back in the future
    ImageView sent_frame_view;
    ImageView new_frame_view;

    TimestampLogTextView log_view;
    ReentrantLock stream_lock;
    double current_rtt;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Log.i(LOG_TAG, "Starting...");
        Thread.setDefaultUncaughtExceptionHandler(new CrashHandler());
        ApplicationStateUpdHandler.getInstance().setMainActivity(this);
        setContentView(R.layout.activity_main);

        // keep screen on while task running
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
//        // restore address from preferences or use default
//        prefs = getSharedPreferences(Constants.PREFS_NAME, Context.MODE_PRIVATE);
//        addr = prefs.getString(Constants.PREFS_ADDR, null);
//        if (addr == null)
//            addr = ProtocolConst.SERVER;

        // references to UI elements
//        connect = this.findViewById(R.id.connect_button);
        this.log_view = this.findViewById(R.id.log_view);
        this.sent_frame_view = this.findViewById(R.id.sent_frame_view);
        this.new_frame_view = this.findViewById(R.id.new_frame_view);

        this.stream_lock = new ReentrantLock();
        this.current_rtt = -1;
    }

    @Override
    protected void onDestroy() {
        Log.w(LOG_TAG, "onDestroy() called!");
        super.onDestroy();

        ApplicationStateUpdHandler.shutdown();
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
