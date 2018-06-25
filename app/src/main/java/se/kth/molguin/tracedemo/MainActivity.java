package se.kth.molguin.tracedemo;

import android.app.DialogFragment;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.View;
import android.view.WindowManager;
import android.widget.ImageView;
import android.widget.TextView;

import java.lang.ref.WeakReference;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.locks.ReentrantLock;

import se.kth.molguin.tracedemo.network.VideoFrame;
import se.kth.molguin.tracedemo.network.control.ControlConst;
import se.kth.molguin.tracedemo.network.gabriel.ConnectionManager;

public class MainActivity extends AppCompatActivity {

    private static final String LOG_TAG = "TraceDemoMainActivity";

    //    Button connect;
    // EditText address; TODO: add back in the future
    ImageView sent_frame_view;
    ImageView new_frame_view;

    TimestampLogTextView log_view;

//    String addr;
//    SharedPreferences prefs;

    ExecutorService stream_upd_exec;
    ReentrantLock stream_lock;

    Bitmap current_frame;
    double current_rtt;

    View.OnClickListener connect_listener;
    View.OnClickListener disconnect_listener;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Log.i(LOG_TAG, "Starting...");
        Thread.setDefaultUncaughtExceptionHandler(new CrashHandler());

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
        //address = this.findViewById(R.id.address_ip);

        // frame
        if (current_frame != null)
            sent_frame_view.setImageBitmap(current_frame);

        // frame update
        this.stream_upd_exec = Executors.newSingleThreadExecutor();
        this.stream_lock = new ReentrantLock();
        this.current_rtt = -1;

        this.connect_listener = new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                ConnectionManager.reset(MainActivity.this);
            }
        };

        this.disconnect_listener = new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                ConnectionManager.shutdownAndDelete();
                MainActivity.this.stateDisconnected();
            }
        };

        ConnectionManager.reset(this).pushStateToActivity();
        // TODO: Add way to set address in CM
    }

    @Override
    protected void onDestroy() {
        Log.w(LOG_TAG, "onDestroy() called!");
        super.onDestroy();
        if (this.isFinishing()) // if we're closing the app, kill everything
        {
            Log.w(LOG_TAG, "isFinishing() TRUE!");
            ConnectionManager.shutdownAndDelete();
        }

        // frame update
        if (this.stream_upd_exec != null)
            this.stream_upd_exec.shutdownNow();
    }

    public void stateFinished(final int run_count) {
        this.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                ConnectionManager.shutdownAndDelete();

                if (MainActivity.this.stream_upd_exec != null)
                    MainActivity.this.stream_upd_exec.shutdownNow();

                MainActivity.this.getWindow().clearFlags(
                        WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
                );

                Bundle b = new Bundle();
                b.putInt("run_count", run_count);
                DialogFragment dialog = new FinishedDialog();
                dialog.setArguments(b);
                dialog.show(MainActivity.this.getFragmentManager(), "Done");
            }
        });
    }

    public void updateRunStatus(final int run_number) {
        this.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                MainActivity.this.log_view.log(String.format(Locale.ENGLISH, Constants.RUN_STATUS_FMT, run_number));
                // MainActivity.this.run_status.setText(String.format(Locale.ENGLISH, Constants.RUN_STATUS_FMT, run_number));
            }
        });
    }

    public void pushSentFrameAndStatsToPreview(final VideoFrame vf, final double rtt) {
        // updates frame preview and run stats

        final WeakReference<MainActivity> ref = new WeakReference<>(this);
        Runnable update = new Runnable() {
            @Override
            public void run() {

                final MainActivity act = ref.get();
                if (act == null) return;

                act.stream_lock.lock();
                try {
                    act.current_frame = BitmapFactory.decodeByteArray(vf.getFrameData(),
                            0, vf.getFrameData().length);
                    act.current_rtt = rtt;

                    act.runOnUiThread(new Runnable() {
                        @Override
                        public void run() {

                            act.stream_lock.lock();
                            try {
                                act.sent_frame_view.setImageBitmap(act.current_frame);
                                // TODO: SHOW RTT SOMEWHEEEERE
//                                act.rtt_stats.setText(String.format(Locale.ENGLISH,
//                                        Constants.STATS_FMT,
//                                        act.current_rtt));
//
//                                if (act.current_rtt < ControlConst.DEFAULT_GOOD_LATENCY_MS)
//                                    act.rtt_stats.setTextColor(Constants.COLOR_GOOD);
//                                else if (act.current_rtt > ControlConst.DEFAULT_BAD_LATENCY_MS)
//                                    act.rtt_stats.setTextColor(Constants.COLOR_BAD);
//                                else
//                                    act.rtt_stats.setTextColor(Constants.COLOR_MEDIUM);

                            } finally {
                                act.stream_lock.unlock();
                            }
                        }
                    });
                } finally {
                    act.stream_lock.unlock();
                }
            }
        };

        this.stream_upd_exec.execute(update);
    }

    public void pushNewFrameToPreview(final byte[] raw_frame) {
        final WeakReference<MainActivity> ref = new WeakReference<>(this);
        Runnable update = new Runnable() {
            @Override
            public void run() {

                final MainActivity act = ref.get();
                if (act == null) return;

                act.stream_lock.lock();
                try {
                    final Bitmap new_frame = BitmapFactory.decodeByteArray(raw_frame,
                            0, raw_frame.length);

                    act.runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            act.stream_lock.lock();
                            try {
                                act.new_frame_view.setImageBitmap(new_frame);
                            } finally {
                                act.stream_lock.unlock();
                            }
                        }
                    });
                } finally {
                    act.stream_lock.unlock();
                }
            }
        };

        this.stream_upd_exec.execute(update);
    }

    public void stateConnectingControl() {
        this.runOnUiThread(new Runnable() {
            @Override
            public void run() {
//                MainActivity.this.connect.setEnabled(true);
//                MainActivity.this.connect.setText(Constants.DISCONNECT_TXT);
//                MainActivity.this.connect.setOnClickListener(MainActivity.this.disconnect_listener);
                //MainActivity.this.address.setEnabled(false);
                MainActivity.this.log_view.log(MainActivity.this.getString(R.string.status_connecting_control));
                // MainActivity.this.status.setText(Constants.STATUS_CONNECTINGCONTROL);
            }
        });
    }

    public void stateConfig() {
        this.runOnUiThread(new Runnable() {
            @Override
            public void run() {
//                MainActivity.this.connect.setEnabled(true);
//                MainActivity.this.connect.setText(Constants.DISCONNECT_TXT);
//                MainActivity.this.connect.setOnClickListener(MainActivity.this.disconnect_listener);
                //MainActivity.this.address.setEnabled(false);
                MainActivity.this.log_view.log(MainActivity.this.getString(R.string.status_configuring));
                //MainActivity.this.status.setText(Constants.STATUS_CONFIGURING);
            }
        });
    }

    public void stateFetchTraces() {
        this.runOnUiThread(new Runnable() {
            @Override
            public void run() {
//                MainActivity.this.connect.setEnabled(false);
//                MainActivity.this.connect.setText(Constants.DISCONNECT_TXT);
                //MainActivity.this.address.setEnabled(false);
                MainActivity.this.log_view.log(MainActivity.this.getString(R.string.status_fetch_trace));
                //MainActivity.this.status.setText(Constants.STATUS_FETCHTRACE);
            }
        });
    }

    /**
     * Called to setup the app when ConnectionManager is connecting
     */
    public void stateInitExperiment() {
        this.runOnUiThread(new Runnable() {
            @Override
            public void run() {
//                MainActivity.this.connect.setEnabled(false);
//                //MainActivity.this.address.setEnabled(false);
//                MainActivity.this.connect.setText(Constants.DISCONNECT_TXT);
                MainActivity.this.log_view.log(MainActivity.this.getString(R.string.status_init_experiment));
                //MainActivity.this.status.setText(Constants.STATUS_INITEXPERIMENT);
            }
        });
    }


    /**
     * Called to setup the app when ConnectionManager is streaming.
     */
    public void stateStreaming() {
        this.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                MainActivity.this.log_view.log(MainActivity.this.getString(R.string.status_streaming));
                //MainActivity.this.status.setText(Constants.STATUS_STREAMING);
//                MainActivity.this.connect.setText(Constants.DISCONNECT_TXT);
//                MainActivity.this.connect.setEnabled(true);
                //MainActivity.this.address.setEnabled(false);

                // disconnect onclicklistener
//                MainActivity.this.connect.setOnClickListener(MainActivity.this.disconnect_listener);
            }
        });
    }

    /**
     * Called to setup the app when ConnectionManager is done streaming but still connected.
     */
    public void stateStreamingEnd() {
        this.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                MainActivity.this.log_view.log(MainActivity.this.getString(R.string.status_stream_done));
                //MainActivity.this.status.setText(Constants.STATUS_STREAM_DONE);
//                MainActivity.this.connect.setText(Constants.DISCONNECT_TXT);
//                MainActivity.this.connect.setEnabled(false);
                //MainActivity.this.address.setEnabled(false);
            }
        });
    }

    /**
     * Called to setup the app when ConnectionManager is disconnected.
     */
    public void stateDisconnected() {
        this.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                MainActivity.this.log_view.log(MainActivity.this.getString(R.string.status_disconnected));
                //MainActivity.this.status.setText(Constants.STATUS_DISCONNECTED);
//                MainActivity.this.connect.setText(Constants.CONNECT_TXT);
//                MainActivity.this.connect.setOnClickListener(MainActivity.this.connect_listener);
                //MainActivity.this.address.setEnabled(true);
            }
        });
    }

    public void stateNTPSync() {
        this.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                //MainActivity.this.address.setEnabled(false);
//                MainActivity.this.connect.setEnabled(false);
//                MainActivity.this.connect.setText(Constants.DISCONNECT_TXT);
                MainActivity.this.log_view.log(MainActivity.this.getString(R.string.status_ntp_sync));
                //MainActivity.this.status.setText(Constants.STATUS_NTP_SYNC);
            }
        });
    }

    /**
     * Called to setup the app when ConnectionManager is disconnecting.
     */
    public void stateDisconnecting() {
        this.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                MainActivity.this.log_view.log(MainActivity.this.getString(R.string.status_disconnecting));
                //MainActivity.this.status.setText(Constants.STATUS_DISCONNECTING);
//                MainActivity.this.connect.setText(Constants.DISCONNECT_TXT);
//                MainActivity.this.connect.setEnabled(false);
                //MainActivity.this.address.setEnabled(false);
            }
        });
    }

    public void stateUploading() {
        this.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                MainActivity.this.log_view.log(MainActivity.this.getString(R.string.status_uploading));
                //MainActivity.this.status.setText(Constants.STATUS_UPLOADING);
//                MainActivity.this.connect.setText(Constants.DISCONNECT_TXT);
//                MainActivity.this.connect.setEnabled(false);
                //MainActivity.this.address.setEnabled(false);
            }
        });
    }

    public void stateListeningControl() {
        this.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                MainActivity.this.log_view.log(MainActivity.this.getString(R.string.status_listening_control));
                //MainActivity.this.status.setText(Constants.STATUS_LISTENING_CONTROL);
//                MainActivity.this.connect.setText(Constants.DISCONNECT_TXT);
//                MainActivity.this.connect.setEnabled(false);
                //MainActivity.this.address.setEnabled(false);
            }
        });
    }
}
