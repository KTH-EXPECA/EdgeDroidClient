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
import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static java.lang.System.exit;

public class MainActivity extends AppCompatActivity {

    DataInputStream trace_inputstream;
    Button fileSelect;
    Button connect;
    TextView status;
    TextView stats;
    TextView rtt_stats;
    Socket clientsocket;

    Timer stat_timer;
    StatCollector statCollector;

    ExecutorService execs;
    SocketOutputThread outputHandler;
    SocketInputThread inputHandler;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        fileSelect = (Button) this.findViewById(R.id.file_choose_button);
        connect = (Button) this.findViewById(R.id.connect_button);
        status = (TextView) this.findViewById(R.id.status_text);
        stats = (TextView) this.findViewById(R.id.stats_text);
        rtt_stats = (TextView) this.findViewById(R.id.rtt_stats);
        stat_timer = null;

        final EditText address = (EditText) this.findViewById(R.id.address_ip);
        final EditText port = (EditText) this.findViewById(R.id.address_port);

        execs = Executors.newFixedThreadPool(2);

        clientsocket = null;
        outputHandler = null;
        inputHandler = null;

        statCollector = null;

        fileSelect.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
                intent.setType("*/*");
                startActivityForResult(intent, 7);
            }
        });

        connect.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                connect.setEnabled(false);

                statCollector = new StatCollector();

                if (outputHandler != null)
                    outputHandler.stop();
                if (inputHandler != null)
                    inputHandler.stop();

                try {
                    execs.awaitTermination(100, TimeUnit.MILLISECONDS);

                    if (clientsocket != null) {
                        clientsocket.close();
                        clientsocket = null;
                    }
                } catch (InterruptedException | IOException e) {
                    e.printStackTrace();
                    exit(-1);
                }

                if (stat_timer != null)
                    stat_timer.cancel();

                status.setText("");
                stats.setText("");
                rtt_stats.setText("");

                InetSocketAddress addr = new InetSocketAddress(address.getText().toString(), Integer.parseInt(port.getText().toString()));
                new ConnectTask(addr).execute();
            }
        });
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        switch (requestCode) {
            case 7:
                if (resultCode == RESULT_OK) {
                    Uri uri = data.getData();
                    try {
                        trace_inputstream = new DataInputStream(getContentResolver().openInputStream(uri));
                    } catch (FileNotFoundException e) {
                        e.printStackTrace();
                        exit(-1);
                    }
                    fileSelect.setText(uri.getPath());
                    connect.setEnabled(true);
                }
                break;
        }
    }

    protected void startConnection() throws IOException {
        outputHandler = new SocketOutputThread(clientsocket, trace_inputstream, statCollector);
        inputHandler = new SocketInputThread(clientsocket, statCollector);

        TimerTask progressTask = new TimerTask() {
            @Override
            public void run() {
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        int sent = outputHandler.getSent();
                        int recv = inputHandler.getRead();

                        String statstext = String.format("%d bytes sent, %d bytes received.", sent, recv);
                        stats.setText(statstext);
                    }
                });
                new StatTask().execute();
            }
        };

        stat_timer = new Timer();
        stat_timer.scheduleAtFixedRate(progressTask, 0, 500);

        execs.execute(outputHandler);
        execs.execute(inputHandler);
    }

    private class StatTask extends AsyncTask<Void, Void, Tuple<Double, Double, Double>> {
        @Override
        protected Tuple<Double, Double, Double> doInBackground(Void... voids) {
            double avg_rtt = statCollector.getMovingAvgRTT();
            double avg_dt = statCollector.getMovingAvgRecvDT();
            double avg_misses = statCollector.getAvgMissedSeq();

            return new Tuple<>(avg_rtt, avg_dt, avg_misses);
        }

        @Override
        protected void onPostExecute(final Tuple<Double, Double, Double> result) {
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    String text = String.format("Current average response time: %.2f ms\n" +
                            "Current average deltaT: %.2f ms\n" +
                            "Current average missed seqs: %.2f seqs.", result.a, result.b, result.c);
                    rtt_stats.setText(text);
                }
            });
        }
    }

    private class ConnectTask extends AsyncTask<Void, Void, Socket> {

        private InetSocketAddress addr;

        ConnectTask(InetSocketAddress addr) {
            this.addr = addr;
        }

        @Override
        protected Socket doInBackground(Void... params) {
            Socket socket = null;
            try {
                publishProgress();
                socket = new Socket();
                socket.connect(addr, 10 * 1000);
            } catch (IOException e) {
                e.printStackTrace();
                return null;
            }
            return socket;
        }

        @Override
        protected void onProgressUpdate(Void... progress) {
            status.setText("Trying to connect to " + addr.toString());
        }

        @Override
        protected void onPostExecute(Socket socket) {
            if (socket == null) {
                status.setText("Error trying to connect to " + addr.toString());
                connect.setEnabled(true);
            } else {
                status.setText("Connected to " + addr.toString());
                clientsocket = socket;
                try {
                    startConnection();
                } catch (IOException e) {
                    e.printStackTrace();
                    exit(-1);
                }
            }
        }
    }

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
