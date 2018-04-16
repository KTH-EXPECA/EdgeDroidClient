package se.kth.molguin.tracedemo;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Bundle;
import android.support.v4.provider.DocumentFile;
import android.support.v7.app.AppCompatActivity;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;

import java.io.IOException;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Timer;
import java.util.TimerTask;

import se.kth.molguin.tracedemo.network.VideoFrame;
import se.kth.molguin.tracedemo.network.gabriel.ConnectionManager;
import se.kth.molguin.tracedemo.network.gabriel.ProtocolConst;

import static java.lang.System.exit;

public class MainActivity extends AppCompatActivity {

    private static final int PICK_TRACE = 7;
    private static final Object frame_lock = new Object();

    DocumentFile selected_trace_dir;
    Uri[] step_traces;

    Button fileSelect;
    Button connect;
    TextView status;
    TextView stats;
    TextView rtt_stats;
    EditText address;
    ImageView imgview;

    String addr;
    MonitoringThread monitoring;
    SharedPreferences prefs;

    Timer stream_timer;
    TimerTask frame_upd_task;

    Bitmap current_frame;
    double current_rtt;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // keep screen on while on activity
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        // restore address from preferences or use default
        prefs = getPreferences(Context.MODE_PRIVATE);
        addr = prefs.getString(Constants.PREFS_ADDR, null);
        if (addr == null)
            addr = ProtocolConst.SERVER;

        // references to UI elements
        fileSelect = this.findViewById(R.id.file_choose_button);
        connect = this.findViewById(R.id.connect_button);
        status = this.findViewById(R.id.status_text);
        stats = this.findViewById(R.id.stats_text);
        rtt_stats = this.findViewById(R.id.rtt_stats);
        imgview = this.findViewById(R.id.frame_view);
        address = this.findViewById(R.id.address_ip);

        // setup the UI temporarily
        connect.setEnabled(false);
        address.setText(addr);
        rtt_stats.setText("");

        // frame
        if (current_frame != null)
            synchronized (frame_lock) {
                imgview.setImageBitmap(current_frame);
            }

        // onclicklistener for the trace select button
        fileSelect.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);
                //intent.setType("application/json");
                startActivityForResult(intent, MainActivity.PICK_TRACE);
            }
        });

        selected_trace_dir = null;
        step_traces = null;

        // frame update
        stream_timer = null;
        frame_upd_task = null;

        current_rtt = -1;

        // start the monitoring thread
        monitoring = new MonitoringThread(this);
        monitoring.start();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (this.isFinishing()) // if we're closing the app, kill everything
        {
            try {
                ConnectionManager.shutDownAndDelete();
            } catch (InterruptedException | IOException e) {
                e.printStackTrace();
            }
        }
        // else/and close down monitoring, (we'll relaunch it later)
        monitoring.stopRunning();
        // frame update
        if (this.frame_upd_task != null)
            this.frame_upd_task.cancel();
        if (this.stream_timer != null)
            this.stream_timer.cancel();
    }

    private void streamingUpdate() {
        // updates frame preview and stats

        ConnectionManager cm = ConnectionManager.getInstance();
        if (cm.getState() != ConnectionManager.CMSTATE.STREAMING)
            return;

        VideoFrame vf;
        try {
            vf = cm.getLastFrame();
            if (vf == null)
                return;

            synchronized (frame_lock) {
                this.current_frame = BitmapFactory.decodeByteArray(vf.getFrameData(),
                        0, vf.getFrameData().length);
                this.current_rtt = cm.getRollingRTT();
            }
        } catch (InterruptedException e) {
            e.printStackTrace();
            return;
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

    /**
     * Called to setup the app when ConnectionManager is connecting
     */
    public void stateConnecting() {
        this.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                MainActivity.this.connect.setEnabled(false);
                MainActivity.this.fileSelect.setEnabled(false);
                MainActivity.this.connect.setText(Constants.DISCONNECT_TXT);
                MainActivity.this.status.setText(String.format(Constants.STATUS_CONNECTING_FMT, addr));
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
                MainActivity.this.fileSelect.setEnabled(false);
                MainActivity.this.connect.setEnabled(false);

                MainActivity.this.status.setText(String.format(Constants.STATUS_CONNECTED_FMT, addr));
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
                MainActivity.this.status.setText(String.format(Constants.STATUS_STREAMING_FMT, addr));
                MainActivity.this.connect.setText(Constants.DISCONNECT_TXT);
                MainActivity.this.connect.setEnabled(true);
                MainActivity.this.fileSelect.setEnabled(false);

                // disconnect onclicklistener
                MainActivity.this.connect.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View view) {
                        connect.setEnabled(false);
                        new Tasks.DisconnectTask().execute();
                    }
                });
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
                MainActivity.this.status.setText(Constants.STATUS_STREAM_DONE_FMT);
                MainActivity.this.connect.setText(Constants.DISCONNECT_TXT);
                MainActivity.this.connect.setEnabled(false);
                MainActivity.this.fileSelect.setEnabled(false);
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
                MainActivity.this.status.setText(Constants.STATUS_DISCONNECTED_FMT);
                MainActivity.this.connect.setText(Constants.CONNECT_TXT);
                MainActivity.this.fileSelect.setEnabled(true);

                if (MainActivity.this.selected_trace_dir == null)
                    MainActivity.this.connect.setEnabled(false);
                else {
                    MainActivity.this.setupFromTrace();
                }

                MainActivity.this.connect.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View view) {
                        // get address from address field
                        // also store it in the preferences
                        MainActivity.this.addr = MainActivity.this.address.getText().toString();
                        SharedPreferences.Editor edit = prefs.edit();
                        edit.putString(Constants.PREFS_ADDR, MainActivity.this.addr);
                        edit.apply();

                        ConnectionManager cm = ConnectionManager.getInstance();
                        try {
                            cm.setAddr(MainActivity.this.addr);
                            cm.setTrace(MainActivity.this.step_traces);
                            cm.setContext(MainActivity.this.getApplicationContext());
                        } catch (ConnectionManager.ConnectionManagerException e) {
                            // tried to set trace when system was already connected
                            // notify that and set activity to "connected" mode
                            MainActivity.this.status.setText("Error");
                            e.printStackTrace();
                            exit(-1);
                        }

                        MainActivity.this.connect.setEnabled(false);
                        new Tasks.ConnectTask().execute();
                    }
                });
            }
        });
    }


    /**
     * Sets up the UI after selecting a trace file
     */
    private void setupFromTrace() {
        DocumentFile[] df = this.selected_trace_dir.listFiles();
        List<Uri> uris = new ArrayList<>(df.length);
        for (DocumentFile d : df) {
            if (d.isFile()) {
                //in_streams.add(new DataInputStream(getContentResolver().openInputStream(d.getUri())));
                uris.add(d.getUri());
            }
        }
        //this.step_traces = in_streams.toArray(new DataInputStream[0]);
        this.step_traces = uris.toArray(new Uri[0]);
        this.fileSelect.setText(this.selected_trace_dir.getUri().getPath());
        this.connect.setText(Constants.CONNECT_TXT);
        this.connect.setEnabled(true);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        switch (requestCode) {
            case MainActivity.PICK_TRACE:
                if (resultCode == RESULT_OK) {
                    DocumentFile d = DocumentFile.fromTreeUri(this, data.getData());
                    if (d.isDirectory()) {
                        this.selected_trace_dir = d;
                        this.setupFromTrace();
                    } else {
                        this.selected_trace_dir = null;
                        this.fileSelect.setText(Constants.TRACE_ERROR_TXT);
                    }
                }
                break;
        }
    }

    /**
     * Called to setup the app when ConnectionManager is disconnecting.
     */
    public void stateDisconnecting() {
        this.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                MainActivity.this.status.setText(String.format(Constants.STATUS_DISCONNECTING_FMT, addr));
                MainActivity.this.connect.setText(Constants.DISCONNECT_TXT);
                MainActivity.this.connect.setEnabled(false);
                MainActivity.this.fileSelect.setEnabled(false);
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
