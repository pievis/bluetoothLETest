package joinstore.it.testhardness;

import android.app.ActionBar;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.os.Parcelable;
import android.support.design.widget.FloatingActionButton;
import android.support.design.widget.Snackbar;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.io.BufferedReader;
import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutionException;

import butterknife.Bind;
import butterknife.ButterKnife;

public class DetailsActivity extends AppCompatActivity {

    @Bind(R.id.container)
    View container;
    @Bind(R.id.device_text)
    TextView deviceText;
    @Bind(R.id.info_container)
    LinearLayout infoContainer;
    @Bind(R.id.container_services)
    LinearLayout servicesContainer;
    @Bind(R.id.status)
    TextView statusText;
    @Bind(R.id.cancel_btn)
    Button cancelBtn;

    BluetoothDevice device;

    ArrayList<UUID> uuids;

    final static String TAG = "test";
    boolean connectionStarted = false;

    final static int STATUS_CONNECTING = 1;
    final static int STATUS_DISCOVERY = 0;
    final static int STATUS_TRANSFER = 2;

    void setStatusText(int status) {
        final String str;
        switch (status) {
            case STATUS_CONNECTING:
                str = "Connecting";
                break;
            case STATUS_DISCOVERY:
                str = "Discovery";
                break;
            case STATUS_TRANSFER:
                str = "Discovery";
                break;
            default:
                str = "Nothing";
        }
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                statusText.setText(str);
            }
        });

    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_details);
        ButterKnife.bind(this);
        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        toolbar.setTitle("Details");
        fetchInfo();
        if (device != null) {
            fillView();
            doServiceDiscovery();
            //startConnectionThread();
        }
    }

    void fillView() {
        deviceText.setText(device.getName() + " " + device.getAddress());
        uuids = new ArrayList<>();
        cancelBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (cth != null) {
                    cth.cancel();
                    log("Transfer aborted");
                }
            }
        });
    }

    void startConnectionThread(UUID uuid) {
        if (cth == null || !cth.isAlive()) {
//            connectionStarted = true;
            setStatusText(STATUS_CONNECTING);
            ConnectThread ct = new ConnectThread(device, uuid);
            ct.start();
        } else {
            log("Connection already started");
        }
    }

    void doServiceDiscovery() {
        IntentFilter filter = new IntentFilter(BluetoothDevice.ACTION_UUID);
        registerReceiver(mReceiver, filter); // Don't forget to unregister during onDestroy
        if (device != null) {
            setStatusText(STATUS_DISCOVERY);
            device.fetchUuidsWithSdp();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        unregisterReceiver(mReceiver);
    }

    // Create a BroadcastReceiver for ACTION_FOUND
    private final BroadcastReceiver mReceiver = new BroadcastReceiver() {
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            // When discovery finds a new list of UUIDs
            if (BluetoothDevice.ACTION_UUID.equals(action)) {
                BluetoothDevice btd = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
                Log.i(TAG, "Received uuids for " + btd.getName());
                Parcelable[] uuidExtra = intent.getParcelableArrayExtra(BluetoothDevice.EXTRA_UUID);
                StringBuilder sb = new StringBuilder();
                uuids = new ArrayList<UUID>(uuidExtra.length);
                if (uuidExtra != null) {
                    for (int i = 0; i < uuidExtra.length; i++) {
                        sb.append(uuidExtra[i].toString()).append(',');
                        uuids.add(UUID.fromString(uuidExtra[i].toString()));
                    }
                }
                Log.i(TAG, "ACTION_UUID received for " + btd.getName() + " uuids: " + sb.toString());
                //update view with uuids
                updateViewUUIDS();
            }
        }
    };

    void updateViewUUIDS() {
        log("Service discovery ended.");
        if (uuids != null) {
            for (final UUID uuid : uuids) {
                Button btn = new Button(this);
                btn.setLayoutParams(new ViewGroup.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
                btn.setText(uuid.toString());

                btn.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        startConnectionThread(uuid);
                    }
                });

                servicesContainer.addView(btn);
            }
        }
    }

    void fetchInfo() {
        Bundle b = getIntent().getExtras();
        if (b != null) {
            device = b.getParcelable(MainActivity.EXTRA_BTDEVICE);
        } else {
            log("Error - no device found");
        }
    }


    void log(String s) {
        Snackbar.make(container, s, Snackbar.LENGTH_LONG)
                .setAction("Action", null).show();
    }


    //private final static UUID DISCOVERY_UUID = UUID.fromString("00030000-0000-1000-8000-00805F9B34FB");

    //Connection thread
    private class ConnectThread extends Thread {
        private final BluetoothSocket mmSocket;
        private final BluetoothDevice mmDevice;

        public ConnectThread(BluetoothDevice device, UUID uuid) {
            // Use a temporary object that is later assigned to mmSocket,
            // because mmSocket is final
            BluetoothSocket tmp = null;
            mmDevice = device;

            // Get a BluetoothSocket to connect with the given BluetoothDevice
            try {
                // MY_UUID is the app's UUID string, also used by the server code
                tmp = device.createRfcommSocketToServiceRecord(uuid);
//                tmp = device.createInsecureRfcommSocketToServiceRecord(uuid);
            } catch (IOException e) {
            }
            mmSocket = tmp;
        }

        public void run() {
            try {
                // Connect the device through the socket. This will block
                // until it succeeds or throws an exception
                mmSocket.connect();
            } catch (IOException connectException) {
                connectionStarted = false;
                // Unable to connect; close the socket and get out
                try {
                    mmSocket.close();
                } catch (IOException closeException) {
                }
                log("Error during connection");
                return;
            }

            // Do work to manage the connection (in a separate thread)
            manageConnectedSocket(mmSocket);
        }

        /**
         * Will cancel an in-progress connection, and close the socket
         */
        public void cancel() {
            try {
                mmSocket.close();
            } catch (IOException e) {
            }
        }
    }

    //After connection, handle data transfer
    private class ConnectedThread extends Thread {
        private final BluetoothSocket mmSocket;
        private final InputStream mmInStream;
        private final OutputStream mmOutStream;

        public ConnectedThread(BluetoothSocket socket) {
            mmSocket = socket;
            InputStream tmpIn = null;
            OutputStream tmpOut = null;

            // Get the input and output streams, using temp objects because
            // member streams are final
            try {
                tmpIn = socket.getInputStream();
                tmpOut = socket.getOutputStream();
            } catch (IOException e) {
            }

            mmInStream = tmpIn;
            mmOutStream = tmpOut;
        }

        public void run() {
//            readAndPublishFloat();
            readAndPublishRaw();
//            readAndPublishString();
            Log.v("result", "Reading data ended.");
            setStatusText(-1);
        }

        void readAndPublishRaw() {
            byte[] buffer = new byte[1024];  // buffer store for the stream
            int bytes; // bytes returned from read()
            Log.v("result", "Start reading...");
            // Keep listening to the InputStream until an exception occurs
            try {
                while ((bytes = mmInStream.read(buffer)) != -1) {

                    // Read from the InputStream
//                    bytes = mmInStream.read(buffer);
                    // Send the obtained bytes to the UI activity
                    Log.v("result", bytes + "");
                    StringBuilder sb = new StringBuilder();
                    for (int i = 0; i < 1024; i++) {
                        sb.append(Integer.toHexString((int) buffer[i] & 0xff));
                    }
                    Log.v("result", "hex: " +  sb.toString());
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        void readAndPublishFloat() {
            DataInputStream din = new DataInputStream(mmInStream);
            Log.v("result", "Start reading...");
            try {
//                float total = 0;
                while (din.available() > 0) {
//                float value = din.readFloat();
//                    double value += din.readDouble();
//                    total+=value;
                    String value = din.readUTF();
                    Log.v("result", value + "");
                }
//                Log.v("result", "Ended with value " +  total);
            } catch (Exception e) {
                Log.v(TAG, "exception " + e.getMessage());
                e.printStackTrace();
            }
        }

        void readAndPublishString() {
            //String method, not useful in this case?
            try {
                BufferedReader r = new BufferedReader(new InputStreamReader(mmInStream));
                StringBuilder total = new StringBuilder();
                String line;
                Log.v("result", "Start reading...");
                while ((line = r.readLine()) != null) {
                    total.append(line);
                    Log.v("result", line);
                }
                Log.v("result", total.toString());
                //TODO publish read string to the view
            } catch (Exception e) {
                //
                try {
                    mmSocket.close();
                } catch (Exception ex) {
                }
                Log.v(TAG, "exception reading data from service");
            }
        }

        /* Call this from the main activity to send data to the remote device */
        public void write(byte[] bytes) {
            try {
                mmOutStream.write(bytes);
            } catch (IOException e) {
            }
        }

        /* Call this from the main activity to shutdown the connection */
        public void cancel() {
            try {
                mmSocket.close();
                setStatusText(-1);
            } catch (IOException e) {
            }
        }
    }


    ConnectedThread cth = null;

    void manageConnectedSocket(BluetoothSocket socket) {
        log("Starting a new connection");
        if (cth != null) {
            cth.cancel();
        }

        setStatusText(STATUS_TRANSFER);
        cth = new ConnectedThread(socket);
        cth.start();
    }

}
