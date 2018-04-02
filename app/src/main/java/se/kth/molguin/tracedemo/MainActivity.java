package se.kth.molguin.tracedemo;

import android.app.Activity;
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
import java.lang.ref.WeakReference;
import java.net.Socket;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import se.kth.molguin.tracedemo.network.ResultInputThread;
import se.kth.molguin.tracedemo.network.VideoOutputThread;
import se.kth.molguin.tracedemo.network.gabriel.ConnectionManager;

import static java.lang.System.exit;

public class MainActivity extends AppCompatActivity {

    private static final String CONNECT_TXT = "Connect";
    private static final String DISCONNECT_TXT = "Disconnect";

    private static final int PICK_TRACE = 7;
    private static final int N_THREADS = 2;

    DataInputStream trace_inputstream;
    Button fileSelect;
    Button connect;
    TextView status;
    TextView stats;
    TextView rtt_stats;
    //ConnectionManager connectionManager;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        fileSelect = this.findViewById(R.id.file_choose_button);
        connect = this.findViewById(R.id.connect_button);
        status = this.findViewById(R.id.status_text);
        stats = this.findViewById(R.id.stats_text);
        rtt_stats = this.findViewById(R.id.rtt_stats);
        final EditText address = this.findViewById(R.id.address_ip);
        


        connect.setText(CONNECT_TXT);
        connect.setEnabled(false);

        fileSelect.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
                intent.setType("*/*");
                startActivityForResult(intent, MainActivity.PICK_TRACE);
            }
        });

        connect.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                connect.setEnabled(false);
                ConnectionManager connectionManager = ConnectionManager.getInstance();

                try {
                    connectionManager.setAddr(address.getText().toString());
                } catch (ConnectionManager.ConnectionManagerException e) {
                    // handle errors if connectionmanager is already connected or streaming
                }

                if (connectionManager.isConnected() || connectionManager.isStreaming()) {
                    try {
                        connectionManager.shutDown();
                    } catch (InterruptedException | IOException e) {
                        e.printStackTrace();
                        exit(-1);
                    }
                }

                status.setText("");
                stats.setText("");
                rtt_stats.setText("");

                new ConnectTask(MainActivity.this, connectionManager).execute();
            }
        });

        // TODO: Check if connectionmanager is already connected and/or streaming
        // if (connectionmanager.connected()) ...
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        switch (requestCode) {
            case MainActivity.PICK_TRACE:
                if (resultCode == RESULT_OK) {
                    Uri uri = data.getData();
                    try {
                        trace_inputstream = new DataInputStream(getContentResolver().openInputStream(uri));
                    } catch (FileNotFoundException e) {
                        e.printStackTrace();
                        exit(-1);
                    }
                    ConnectionManager.getInstance().setTrace(trace_inputstream);
                    fileSelect.setText(uri.getPath());
                    connect.setEnabled(true);
                }
                break;
        }
    }

    void startStreaming(){
        ConnectionManager cm = ConnectionManager.getInstance();
        try {
            cm.startStreaming();
        } catch (IOException | InterruptedException e) {
            e.printStackTrace();
            exit(-1);
        } catch (ConnectionManager.ConnectionManagerException e) {
            // already streaming or not connected or no trace...
            switch (e.getState())
            {
                case NOTRACE:
                    // notify user no trace is set?
                    break;
                case NOADDRESS:
                    // notify no address
                    break;
                case NOTCONNECTED:
                    // notify not connected
                case ALREADYCONNECTED:
                    // shouldn't happen??
                    e.printStackTrace();
                    exit(-1);
                    break;
                case ALREADYSTREAMING:
                    // disconnect?
                    break;
            }
        }
    }

    private static class ConnectTask extends AsyncTask<Void, Void, Void>
    {
        private ConnectionManager cm;
        private WeakReference<MainActivity> context;
        ConnectTask(MainActivity context, ConnectionManager cm)
        {
            this.context = new WeakReference<>(context);
            this.cm = cm;
        }

        @Override
        protected Void doInBackground(Void... voids) {
            try {
                cm.initConnections();
            } catch (ConnectionManager.ConnectionManagerException e) {
                e.printStackTrace();
                exit(-1);
            }
            return null;
        }

        @Override
        protected void onPostExecute(Void result)
        {
            MainActivity parent = this.context.get();
            if (parent != null)
                parent.startStreaming();
            // if we lost the activity we do nothing
        }
    }

//    protected void startConnection() throws IOException {
//        videooutput = new SocketOutputThread(clientsocket, trace_inputstream, statCollector);
//        resultinput = new SocketInputThread(clientsocket, statCollector);
//
//        TimerTask progressTask = new TimerTask() {
//            @Override
//            public void run() {
//                runOnUiThread(new Runnable() {
//                    @Override
//                    public void run() {
//                        int sent = videooutput.getSent();
//                        int recv = resultinput.getRead();
//
//                        String statstext = String.format("%d bytes sent, %d bytes received.", sent, recv);
//                        stats.setText(statstext);
//                    }
//                });
//                new StatTask().execute();
//            }
//        };
//
//        stat_timer = new Timer();
//        stat_timer.scheduleAtFixedRate(progressTask, 0, 500);
//
//        execs.execute(videooutput);
//        execs.execute(resultinput);
//    }
//
//    private class StatTask extends AsyncTask<Void, Void, Tuple<Double, Double, Double>> {
//        @Override
//        protected Tuple<Double, Double, Double> doInBackground(Void... voids) {
//            double avg_rtt = statCollector.getMovingAvgRTT();
//            double avg_dt = statCollector.getMovingAvgRecvDT();
//            double avg_misses = statCollector.getAvgMissedSeq();
//
//            return new Tuple<>(avg_rtt, avg_dt, avg_misses);
//        }
//
//        @Override
//        protected void onPostExecute(final Tuple<Double, Double, Double> result) {
//            runOnUiThread(new Runnable() {
//                @Override
//                public void run() {
//                    String text = String.format("Current average response time: %.2f ms\n" +
//                            "Current average deltaT: %.2f ms\n" +
//                            "Current average missed seqs: %.2f seqs.", result.a, result.b, result.c);
//                    rtt_stats.setText(text);
//                }
//            });
//        }
//    }
//
//    private class ConnectTask extends AsyncTask<Void, Void, Socket> {
//
//        private InetSocketAddress addr;
//
//        ConnectTask(InetSocketAddress addr) {
//            this.addr = addr;
//        }
//
//        @Override
//        protected Socket doInBackground(Void... params) {
//            Socket socket = null;
//            try {
//                publishProgress();
//                socket = new Socket();
//                socket.connect(addr, 10 * 1000);
//            } catch (IOException e) {
//                e.printStackTrace();
//                return null;
//            }
//            return socket;
//        }
//
//        @Override
//        protected void onProgressUpdate(Void... progress) {
//            status.setText("Trying to connect to " + addr.toString());
//        }
//
//        @Override
//        protected void onPostExecute(Socket socket) {
//            if (socket == null) {
//                status.setText("Error trying to connect to " + addr.toString());
//                connect.setEnabled(true);
//            } else {
//                status.setText("Connected to " + addr.toString());
//                clientsocket = socket;
//                try {
//                    startConnection();
//                } catch (IOException e) {
//                    e.printStackTrace();
//                    exit(-1);
//                }
//            }
//        }
//    }

    private class Tuple<T1, T2, T3> {
        T1 a;
        T2 b;
        T3 c;

        Tuple(T1 a, T2 b, T3 c) {
            this.a = a;
            this.b = b;
            this.c = c;
        }
    }
}
