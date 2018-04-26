package se.kth.molguin.tracedemo;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;

import java.lang.ref.WeakReference;
import java.util.Locale;
import java.util.Timer;
import java.util.TimerTask;

import se.kth.molguin.tracedemo.network.VideoFrame;
import se.kth.molguin.tracedemo.network.gabriel.ConnectionManager;
import se.kth.molguin.tracedemo.network.gabriel.ProtocolConst;

import static java.lang.System.exit;

public class MainActivity extends AppCompatActivity {

    private static final Object frame_lock = new Object();

    Button connect;
    TextView status;
    TextView stats;
    TextView rtt_stats;
    // EditText address; TODO: add back in the future
    ImageView imgview;

    String addr;
    SharedPreferences prefs;

    Timer stream_timer;
    TimerTask frame_upd_task;

    Bitmap current_frame;
    double current_rtt;

    View.OnClickListener connect_listener;
    View.OnClickListener disconnect_listener;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // keep screen on while on activity
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        // restore address from preferences or use default
        prefs = getSharedPreferences(Constants.PREFS_NAME, Context.MODE_PRIVATE);
        addr = prefs.getString(Constants.PREFS_ADDR, null);
        if (addr == null)
            addr = ProtocolConst.SERVER;

        // references to UI elements
        connect = this.findViewById(R.id.connect_button);
        status = this.findViewById(R.id.status_text);
        stats = this.findViewById(R.id.stats_text);
        rtt_stats = this.findViewById(R.id.rtt_stats);
        imgview = this.findViewById(R.id.frame_view);
        //address = this.findViewById(R.id.address_ip);

        // frame
        if (current_frame != null)
            synchronized (frame_lock) {
                imgview.setImageBitmap(current_frame);
            }

        // frame update
        stream_timer = null;
        frame_upd_task = null;
        current_rtt = -1;

        this.connect_listener = new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                ConnectionManager.init(MainActivity.this);
            }
        };

        this.disconnect_listener = new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                ConnectionManager.shutdownAndDelete();
            }
        };

        ConnectionManager.init(this).pushStateToActivity();
        // TODO: Add way to set address in CM
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (this.isFinishing()) // if we're closing the app, kill everything
            ConnectionManager.shutdownAndDelete();

        // frame update
        if (this.frame_upd_task != null)
            this.frame_upd_task.cancel();
        if (this.stream_timer != null)
            this.stream_timer.cancel();
    }

    private void streamingUpdate() {
        // updates frame preview and stats

        ConnectionManager cm = null;
        try {
            cm = ConnectionManager.getInstance();
        } catch (ConnectionManager.ConnectionManagerException e) {
            e.printStackTrace();
            exit(-1);
        }
        if (cm.getState() != ConnectionManager.CMSTATE.STREAMING)
            return;

        VideoFrame vf;
        vf = cm.getLastFrame();
        if (vf == null)
            return;

        synchronized (frame_lock) {
            this.current_frame = BitmapFactory.decodeByteArray(vf.getFrameData(),
                    0, vf.getFrameData().length);
            this.current_rtt = cm.getRollingRTT();
        }


        this.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                synchronized (MainActivity.frame_lock) {
                    MainActivity.this.imgview.setImageBitmap(MainActivity.this.current_frame);
                    MainActivity.this.rtt_stats.setText(String.format(Locale.ENGLISH,
                            Constants.STATS_FMT,
                            MainActivity.this.current_rtt));
                }
            }
        });
    }

    public void stateConnectingControl() {
        this.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                MainActivity.this.connect.setEnabled(true);
                MainActivity.this.connect.setText(Constants.DISCONNECT_TXT);
                MainActivity.this.connect.setOnClickListener(MainActivity.this.disconnect_listener);
                //MainActivity.this.address.setEnabled(false);
                MainActivity.this.status.setText(Constants.STATUS_CONNECTINGCONTROL);
            }
        });
    }

    public void stateConnectedControl() {
        this.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                MainActivity.this.connect.setEnabled(true);
                MainActivity.this.connect.setText(Constants.DISCONNECT_TXT);
                MainActivity.this.connect.setOnClickListener(MainActivity.this.disconnect_listener);
                //MainActivity.this.address.setEnabled(false);
                MainActivity.this.status.setText(Constants.STATUS_CONNECTEDCONTROL);
            }
        });
    }

    public void stateConfig() {
        this.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                MainActivity.this.connect.setEnabled(true);
                MainActivity.this.connect.setText(Constants.DISCONNECT_TXT);
                MainActivity.this.connect.setOnClickListener(MainActivity.this.disconnect_listener);
                //MainActivity.this.address.setEnabled(false);
                MainActivity.this.status.setText(Constants.STATUS_CONFIGURING);
            }
        });
    }

    public void stateFetchTraces() {
        this.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                MainActivity.this.connect.setEnabled(false);
                MainActivity.this.connect.setText(Constants.DISCONNECT_TXT);
                //MainActivity.this.address.setEnabled(false);
                MainActivity.this.status.setText(Constants.STATUS_FETCHTRACE);
            }
        });
    }

    public void stateWaitForStart() {
        this.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                MainActivity.this.connect.setEnabled(true);
                MainActivity.this.connect.setText(Constants.DISCONNECT_TXT);
                MainActivity.this.connect.setOnClickListener(MainActivity.this.disconnect_listener);
                //MainActivity.this.address.setEnabled(false);
                MainActivity.this.status.setText(Constants.STATUS_WAITFOREXPERIMENT);
            }
        });
    }

    /**
     * Called to setup the app when ConnectionManager is connecting
     */
    public void stateConnecting() {
        this.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                MainActivity.this.connect.setEnabled(false);
                //MainActivity.this.address.setEnabled(false);
                MainActivity.this.connect.setText(Constants.DISCONNECT_TXT);
                MainActivity.this.status.setText(String.format(Constants.STATUS_CONNECTING, addr));
            }
        });
    }

    /**
     * Called to setup the app when ConnectionManager is connected.
     */
    public void stateConnected() {
        this.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                MainActivity.this.connect.setText(Constants.DISCONNECT_TXT);
                MainActivity.this.connect.setEnabled(true);
                MainActivity.this.connect.setOnClickListener(MainActivity.this.disconnect_listener);
                //MainActivity.this.address.setEnabled(false);
                MainActivity.this.status.setText(Constants.STATUS_CONNECTED);
            }
        });
    }


    /**
     * Called to setup the app when ConnectionManager is streaming.
     */
    public void stateStreaming() {
        startPeriodicStreamUpdate();

        this.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                MainActivity.this.status.setText(Constants.STATUS_STREAMING);
                MainActivity.this.connect.setText(Constants.DISCONNECT_TXT);
                MainActivity.this.connect.setEnabled(true);
                //MainActivity.this.address.setEnabled(false);

                // disconnect onclicklistener
                MainActivity.this.connect.setOnClickListener(MainActivity.this.disconnect_listener);
            }
        });
    }

    private void startPeriodicStreamUpdate() {
        if (this.frame_upd_task != null)
            this.frame_upd_task.cancel();
        if (this.stream_timer != null)
            this.stream_timer.cancel();

        this.stream_timer = new Timer();
        this.frame_upd_task = new StreamUpdateTask(this);

        this.stream_timer.scheduleAtFixedRate(this.frame_upd_task, 0,
                (long) Math.ceil(1000.0 / Constants.FPS)); // 15 fps
    }

    /**
     * Called to setup the app when ConnectionManager is done streaming but still connected.
     */
    public void stateStreamingEnd() {
        if (this.frame_upd_task != null)
            this.frame_upd_task.cancel();
        if (this.stream_timer != null)
            this.stream_timer.cancel();

        this.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                MainActivity.this.status.setText(Constants.STATUS_STREAM_DONE);
                MainActivity.this.connect.setText(Constants.DISCONNECT_TXT);
                MainActivity.this.connect.setEnabled(false);
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
                MainActivity.this.status.setText(Constants.STATUS_DISCONNECTED);
                MainActivity.this.connect.setText(Constants.CONNECT_TXT);
                MainActivity.this.connect.setOnClickListener(MainActivity.this.connect_listener);
                //MainActivity.this.address.setEnabled(true);
            }
        });
    }

    public void stateNTPSync() {
        this.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                //MainActivity.this.address.setEnabled(false);
                MainActivity.this.connect.setEnabled(false);
                MainActivity.this.connect.setText(Constants.DISCONNECT_TXT);
                MainActivity.this.status.setText(Constants.STATUS_NTP_SYNC);
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
                MainActivity.this.status.setText(Constants.STATUS_DISCONNECTING);
                MainActivity.this.connect.setText(Constants.DISCONNECT_TXT);
                MainActivity.this.connect.setEnabled(false);
                //MainActivity.this.address.setEnabled(false);
            }
        });
    }

    public void stateUploading() {
        this.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                MainActivity.this.status.setText(Constants.STATUS_UPLOADING);
                MainActivity.this.connect.setText(Constants.DISCONNECT_TXT);
                MainActivity.this.connect.setEnabled(false);
                //MainActivity.this.address.setEnabled(false);
            }
        });
    }


    private static class StreamUpdateTask extends TimerTask {

        WeakReference<MainActivity> mainAct;

        StreamUpdateTask(MainActivity mainAct) {
            this.mainAct = new WeakReference<>(mainAct);
        }

        @Override
        public void run() {
            MainActivity act = this.mainAct.get();
            if (act != null) act.streamingUpdate();
            else this.cancel();
        }
    }
}
