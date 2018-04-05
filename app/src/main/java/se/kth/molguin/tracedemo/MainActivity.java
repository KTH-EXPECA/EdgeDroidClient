package se.kth.molguin.tracedemo;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;

import java.io.DataInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.lang.ref.WeakReference;

import se.kth.molguin.tracedemo.network.gabriel.ConnectionManager;
import se.kth.molguin.tracedemo.network.gabriel.ProtocolConst;

import static java.lang.System.exit;

public class MainActivity extends AppCompatActivity {

    private static final int PICK_TRACE = 7;

    Uri selected_trace;

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
    Thread img_update_thread;
    UpdateFrameRunnable img_update_runnable;


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

        // onclicklistener for the trace select button
        fileSelect.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
                intent.setType("*/*");
                startActivityForResult(intent, MainActivity.PICK_TRACE);
            }
        });

        img_update_runnable = null;
        img_update_thread = null;
        selected_trace = null;

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
        // img update
        if (img_update_runnable != null) img_update_runnable.stop();
        if (img_update_thread != null) img_update_thread.interrupt();
    }


    /**
     * Sets the image in the ImageView for previewing the last sent frame.
     *
     * @param bitmap New frame to display.
     */
    protected void setImage(final Bitmap bitmap) {
        this.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                MainActivity.this.imgview.setImageBitmap(bitmap);
            }
        });
    }


    /**
     * Called to setup the app when ConnectionManager is disconnected.
     */
    public void stateDisconnected() {
        this.img_update_runnable = new UpdateFrameRunnable(MainActivity.this);
        this.img_update_thread = new Thread(MainActivity.this.img_update_runnable);
        this.img_update_thread.start();

        this.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                MainActivity.this.status.setText(Constants.STATUS_DISCONNECTED_FMT);
                MainActivity.this.connect.setText(Constants.CONNECT_TXT);
                MainActivity.this.fileSelect.setEnabled(true);

                if (MainActivity.this.selected_trace == null)
                    MainActivity.this.connect.setEnabled(false);
                else {
                    MainActivity.this.setupTraceFromUri(selected_trace);
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

                        DataInputStream trace_inputstream;

                        try {
                            trace_inputstream = new DataInputStream(getContentResolver().openInputStream(selected_trace));
                            ConnectionManager.getInstance().setAddr(MainActivity.this.addr);
                            ConnectionManager.getInstance().setTrace(trace_inputstream);
                        } catch (ConnectionManager.ConnectionManagerException e) {
                            // tried to set trace when system was already connected
                            // notify that and set activity to "connected" mode
                            MainActivity.this.status.setText("Error");
                            e.printStackTrace();
                            exit(-1);
                        } catch (FileNotFoundException e) {
                            // shouldn't happen, yet here we are
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

    /**
     * Called to setup the app when ConnectionManager is done streaming but still connected.
     */
    public void stateStreamingEnd() {
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
     * Called to setup the app when ConnectionManager is disconnecting.
     */
    public void stateDisconnecting() {
        // stop image feed
        if (this.img_update_runnable != null)
            this.img_update_runnable.stop();
        if (this.img_update_thread != null)
            this.img_update_thread.interrupt();

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


    /**
     * Sets up the UI after selecting a trace file
     * @param file trace file.
     */
    private void setupTraceFromUri(Uri file) {
        this.selected_trace = file;
        this.fileSelect.setText(file.getPath());
        this.connect.setText(Constants.CONNECT_TXT);
        this.connect.setEnabled(true);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        switch (requestCode) {
            case MainActivity.PICK_TRACE:
                if (resultCode == RESULT_OK) {
                    this.setupTraceFromUri(data.getData());
                }
                break;
        }
    }


    /**
     * Thread that handles the frame feed to the ImageView
     */
    private static class UpdateFrameRunnable implements Runnable {

        WeakReference<MainActivity> mainAct;
        private static final Object lock = new Object();
        boolean running;

        UpdateFrameRunnable(MainActivity mainAct) {
            this.mainAct = new WeakReference<>(mainAct);
            this.running = false;
        }

        void stop() {
            synchronized (lock) {
                this.running = false;
            }
        }

        @Override
        public void run() {
            byte[] frame;
            Bitmap img;
            this.running = true;
            ConnectionManager cm = ConnectionManager.getInstance();

            try {
                while (true) {
                    synchronized (lock) {
                        if (!running) break;
                    }

                    if (cm.getState() != ConnectionManager.CMSTATE.STREAMING)
                        Thread.sleep(5);
                    else {
                        frame = cm.getLastFrame();
                        img = BitmapFactory.decodeByteArray(frame, 0, frame.length);
                        MainActivity act = mainAct.get();
                        if (act != null)
                            act.setImage(img);
                    }
                }
            } catch (InterruptedException ignored) {
            }
            this.running = false;
        }
    }
}
