package se.kth.molguin.tracedemo.network.control;

import android.content.Context;
import android.util.Log;

import com.android.volley.Request;
import com.android.volley.RequestQueue;
import com.android.volley.Response;
import com.android.volley.VolleyError;
import com.android.volley.toolbox.Volley;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import se.kth.molguin.tracedemo.Constants;
import se.kth.molguin.tracedemo.network.InputStreamVolleyRequest;
import se.kth.molguin.tracedemo.network.gabriel.ConnectionManager;
import se.kth.molguin.tracedemo.network.gabriel.Experiment;
import se.kth.molguin.tracedemo.network.gabriel.ProtocolConst;

import static java.lang.System.exit;
import static se.kth.molguin.tracedemo.network.control.ControlConst.CMD_FETCH_TRACES;
import static se.kth.molguin.tracedemo.network.control.ControlConst.CMD_PULL_STATS;
import static se.kth.molguin.tracedemo.network.control.ControlConst.CMD_PUSH_CONFIG;
import static se.kth.molguin.tracedemo.network.control.ControlConst.CMD_REPEAT_EXP;
import static se.kth.molguin.tracedemo.network.control.ControlConst.CMD_SHUTDOWN;
import static se.kth.molguin.tracedemo.network.control.ControlConst.STATUS_ERROR;
import static se.kth.molguin.tracedemo.network.control.ControlConst.STATUS_SUCCESS;

public class ControlClient implements AutoCloseable {

    private final static String LOG_TAG = "ControlClient";

    private ExecutorService exec;
    private Socket socket;
    private DataInputStream data_in;
    private DataOutputStream data_out;
    private Context app_context;
    private Experiment.Config config;

    ControlClient(String address, int port, Context app_context) {
        Log.i(LOG_TAG, String.format("Connecting to Control Server at %s:%d", address, port));
        this.app_context = app_context;
        this.config = null;

        boolean connected = false;
        while (!connected) {
            try {
                this.socket = new Socket();
                this.socket.setTcpNoDelay(true);
                this.socket.connect(new InetSocketAddress(address, port), 100);
                connected = true;

                this.data_in = new DataInputStream(this.socket.getInputStream());
                this.data_out = new DataOutputStream(this.socket.getOutputStream());
            } catch (SocketException e) {
                Log.i(LOG_TAG, "Could not connect, retrying...");
            } catch (IOException e) {
                e.printStackTrace();
                exit(-1);
            }
        }
        Log.i(LOG_TAG, String.format("Connected to Control Server at %s:%d", address, port));

        this.exec = Executors.newSingleThreadExecutor();

        Log.i(LOG_TAG, "Initializing command listening thread...");
        this.exec.submit(new Runnable() {
            @Override
            public void run() {
                try {
                    ControlClient.this.waitForCommands();
                } catch (IOException e) {
                    e.printStackTrace();
                    exit(-1);
                }
            }
        });
    }

    ControlClient(Context app_context) {
        this(ProtocolConst.SERVER, ControlConst.CONTROL_PORT, app_context);
    }

    private void notifyCommandStatus(boolean success) {
        int status = success ? STATUS_SUCCESS : STATUS_ERROR;
        try {
            this.data_out.writeInt(status);
            this.data_out.flush();
        } catch (SocketException e) {
            Log.w(LOG_TAG, "Socket closed!");
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
            exit(-1);
        }
    }

    private void waitForCommands() throws IOException {
        while (true) {
            try {
                int cmd_id = this.data_in.readInt();

                switch (cmd_id) {
                    case CMD_PUSH_CONFIG:
                        this.getConfigFromServer();
                        break;
                    case CMD_PULL_STATS:
                        this.uploadStats();
                        break;
                    case CMD_REPEAT_EXP:
                        // TODO: trigger experiment repeat
                        break;
                    case CMD_FETCH_TRACES:
                        this.downloadTraces();
                        break;
                    case CMD_SHUTDOWN:
                        // TODO: shut down!
                        break;
                    default:
                        break;
                }

            } catch (SocketException e) {
                Log.w(LOG_TAG, "Socket closed!");
                return;
            }
        }
    }

    private void getConfigFromServer() {
        Log.i(LOG_TAG, "Receiving experiment configuration...");

        try {
            int config_len = this.data_in.readInt();
            byte[] config_b = new byte[config_len];

            int readSize = 0;
            while (readSize < config_len) {
                int ret = this.data_in.read(config_b, readSize, config_len - readSize);
                if (ret <= 0) {
                    throw new IOException();
                }
                readSize += ret;
            }

            JSONObject config = new JSONObject(new String(config_b, "UTF-8"));
            this.config = new Experiment.Config(config);

            // TODO: push config to ConnectionManager

            this.notifyCommandStatus(true);
        } catch (SocketException e) {
            Log.w(LOG_TAG, "Socket closed!");
            e.printStackTrace();
        } catch (UnsupportedEncodingException | JSONException e) {
            Log.e(LOG_TAG, "Could not parse incoming data!");
            e.printStackTrace();
            this.notifyCommandStatus(false);
        } catch (IOException e) {
            e.printStackTrace();
            exit(-1);
        }
    }

    private void uploadStats() {
        Log.i(LOG_TAG, "Uploading experiment metrics.");
        // TODO: get JSON from ConnectionManager

        try {
            JSONObject payload = new JSONObject();

            Log.i(LOG_TAG, "Sending JSON data...");
            byte[] payload_b = payload.toString().getBytes("UTF-8");
            Log.i(LOG_TAG, String.format("Payload size: %d bytes", payload_b.length));

            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            DataOutputStream outStream = new DataOutputStream(baos);
            outStream.writeInt(payload_b.length);
            outStream.write(payload_b);

            this.data_out.write(baos.toByteArray());
            this.data_out.flush();
        } catch (SocketException e) {
            Log.w(LOG_TAG, "Socket closed!");
            e.printStackTrace();
        } catch (UnsupportedEncodingException e) {
            Log.e(LOG_TAG, "Error sending metrics!");
            e.printStackTrace();
            this.notifyCommandStatus(false);
            exit(-1);
        } catch (IOException e) {
            e.printStackTrace();
            exit(-1);
        }
    }

    private void downloadTraces() {
        final File appDir = this.app_context.getFilesDir();
        for (File f : appDir.listFiles())
            if (!f.isDirectory())
                f.delete();

        final CountDownLatch latch = new CountDownLatch(this.config.steps);
        RequestQueue requestQueue = Volley.newRequestQueue(this.app_context);

        for (int i = 0; i < this.config.steps; i++) {
            // fetch all the steps using Volley

            final String stepFilename = Constants.STEP_PREFIX + (i + 1) + Constants.STEP_SUFFIX;
            final String stepUrl = this.config.trace_url + stepFilename;

            InputStreamVolleyRequest req =
                    new InputStreamVolleyRequest(Request.Method.GET, stepUrl,
                            new Response.Listener<byte[]>() {
                                @Override
                                public void onResponse(byte[] response) {
                                    try {
                                        if (response != null) {
                                            Log.i(LOG_TAG, "Got trace " + stepFilename);
                                            FileOutputStream file_out = ControlClient.this.app_context.openFileOutput(stepFilename, Context.MODE_PRIVATE);
                                            file_out.write(response);
                                            file_out.close();
                                            latch.countDown();
                                        }
                                    } catch (IOException e) {
                                        e.printStackTrace();
                                        exit(-1);
                                    }
                                }
                            },
                            new Response.ErrorListener() {
                                @Override
                                public void onErrorResponse(VolleyError error) {
                                    Log.e(LOG_TAG, "Could not fetch " + stepUrl);
                                    ControlClient.this.notifyCommandStatus(false);
                                    error.printStackTrace();
                                    exit(-1); // for now
                                }
                            }, null);

            requestQueue.add(req);
        }

        try {
            latch.await();
        } catch (InterruptedException e) {
            e.printStackTrace();
        } finally {
            requestQueue.stop();
        }

        this.notifyCommandStatus(true);
    }

    @Override
    public void close() throws Exception {

        this.exec.shutdownNow();
        this.socket.close();
        this.data_in.close();
        this.data_out.close();
    }
}
