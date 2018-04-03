package se.kth.molguin.tracedemo;

import android.content.Intent;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;

import java.io.DataInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;

import se.kth.molguin.tracedemo.network.gabriel.ConnectionManager;
import se.kth.molguin.tracedemo.network.gabriel.ProtocolConst;

import static java.lang.System.exit;

public class MainActivity extends AppCompatActivity {

    private static final String CONNECT_TXT = "Connect";
    private static final String DISCONNECT_TXT = "Disconnect";

    private static final String STATUS_DISCONNECTED_FMT = "Disconnected";
    private static final String STATUS_CONNECTING_FMT = "Connecting to %s...";
    private static final String STATUS_CONNECTED_FMT = "Connected to %s";
    private static final String STATUS_STREAMING_FMT = "Connected and streaming to %s";
    private static final String STATUS_DISCONNECTING_FMT = "Closing connectiong to %s...";

    private static final int PICK_TRACE = 7;

    DataInputStream trace_inputstream;
    Uri selected_trace;

    Button fileSelect;
    Button connect;
    TextView status;
    TextView stats;
    TextView rtt_stats;
    EditText address;
    String addr;

    MonitoringThread monitoring;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        monitoring = new MonitoringThread(this);

        trace_inputstream = null;
        selected_trace = null;
        addr = "";

        fileSelect = this.findViewById(R.id.file_choose_button);

        connect = this.findViewById(R.id.connect_button);
        connect.setEnabled(false);

        status = this.findViewById(R.id.status_text);
        stats = this.findViewById(R.id.stats_text);
        rtt_stats = this.findViewById(R.id.rtt_stats);
        address = this.findViewById(R.id.address_ip);

        address.setText(ProtocolConst.SERVER);

        fileSelect.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
                intent.setType("*/*");
                startActivityForResult(intent, MainActivity.PICK_TRACE);
            }
        });

        monitoring.start();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (this.isFinishing()) // if we're closing the app, kill everything
        {
            try {
                ConnectionManager.getInstance().shutDown();
            } catch (InterruptedException | IOException e) {
                e.printStackTrace();
            }
        }
        // else/and close down monitoring, (we'll relaunch it later)
        monitoring.stopRunning();
    }

    public void stateDisconnected() {
        this.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                // called to setup the app when ConnectionManager is disconnected.
                MainActivity.this.status.setText(STATUS_DISCONNECTED_FMT);
                MainActivity.this.connect.setText(CONNECT_TXT);
                MainActivity.this.fileSelect.setEnabled(true);

                if (MainActivity.this.selected_trace == null)
                    MainActivity.this.connect.setEnabled(false);
                else {
                    MainActivity.this.setupTraceFromUri(selected_trace);
                }

                MainActivity.this.connect.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View view) {
                        MainActivity.this.addr = MainActivity.this.address.getText().toString();
                        try {
                            ConnectionManager.getInstance().setAddr(MainActivity.this.addr);
                        } catch (ConnectionManager.ConnectionManagerException e) {
                            // these errors should never happen here!
                            e.printStackTrace();
                            exit(-1);
                        }
                        MainActivity.this.connect.setEnabled(false);
                        new ConnectTask().execute();
                    }
                });
            }
        });
    }

    public void stateConnecting() {
        this.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                // called to setup the app when ConnectionManager is connecting
                MainActivity.this.connect.setEnabled(false);
                MainActivity.this.fileSelect.setEnabled(false);
                MainActivity.this.connect.setText(DISCONNECT_TXT);
                MainActivity.this.status.setText(String.format(STATUS_CONNECTING_FMT, addr));
            }
        });
    }

    public void stateConnected() {
        this.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                // called to setup the app when ConnectionManager is connected.
                MainActivity.this.connect.setText(DISCONNECT_TXT);
                MainActivity.this.fileSelect.setEnabled(false);
                MainActivity.this.connect.setEnabled(false);

                MainActivity.this.status.setText(String.format(STATUS_CONNECTED_FMT, addr));
            }
        });
    }

    public void stateStreaming() {
        this.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                // called to setup the app when ConnectionManager is streaming.
                MainActivity.this.status.setText(String.format(STATUS_STREAMING_FMT, addr));
                MainActivity.this.connect.setText(DISCONNECT_TXT);
                MainActivity.this.connect.setEnabled(true);
                MainActivity.this.fileSelect.setEnabled(false);

                // disconnect onclicklistener
                MainActivity.this.connect.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View view) {
                        connect.setEnabled(false);
                        new DisconnectTask().execute();
                    }
                });
            }
        });
    }

    public void stateDisconnecting() {
        this.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                // called to setup the app when ConnectionManager is disconnecting.
                MainActivity.this.status.setText(String.format(STATUS_DISCONNECTING_FMT, addr));
                MainActivity.this.connect.setText(DISCONNECT_TXT);
                MainActivity.this.connect.setEnabled(false);
                MainActivity.this.fileSelect.setEnabled(false);
            }
        });
    }


    private void setupTraceFromUri(Uri file) {
        this.selected_trace = file;
        try {
            this.trace_inputstream = new DataInputStream(getContentResolver().openInputStream(file));
        } catch (FileNotFoundException e) {
            e.printStackTrace();
            exit(-1);
        }

        try {
            ConnectionManager.getInstance().setTrace(this.trace_inputstream);
        } catch (ConnectionManager.ConnectionManagerException e) {
            // tried to set trace when system was already connected
            // notify that and set activity to "connected" mode
            this.status.setText("");
            e.printStackTrace();
        }
        this.fileSelect.setText(file.getPath());
        this.connect.setText(CONNECT_TXT);
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

    private static class ConnectTask extends AsyncTask<Void, Void, Void> {
        @Override
        protected Void doInBackground(Void... voids) {
            ConnectionManager cm = ConnectionManager.getInstance();
            try {
                cm.initConnections();
            } catch (ConnectionManager.ConnectionManagerException e) {
                // TODO: deal with issues connecting, which shouldn't happen??
                e.printStackTrace();
                exit(-1);
            }
            return null;
        }
    }


    private static class DisconnectTask extends AsyncTask<Void, Void, Void> {
        @Override
        protected Void doInBackground(Void... voids) {
            ConnectionManager cm = ConnectionManager.getInstance();
            try {
                cm.shutDown();
            } catch (InterruptedException | IOException e) {
                e.printStackTrace();
                exit(-1);
            }
            return null;
        }
    }
}
