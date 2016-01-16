package joinstore.it.testhardness;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothManager;
import android.bluetooth.le.BluetoothLeScanner;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanResult;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.support.design.widget.FloatingActionButton;
import android.support.design.widget.Snackbar;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.Menu;
import android.view.MenuItem;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import butterknife.Bind;
import butterknife.ButterKnife;

public class MainActivity extends AppCompatActivity {

    //
    // View & View control
    //

    @Bind(R.id.container)
    LinearLayout container;
    @Bind(R.id.supported_text)
    TextView supportedText;
    @Bind(R.id.list)
    ListView listView;

    DeviceArrayAdapter devicesArrayAdapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        ButterKnife.bind(this);
        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

/*        FloatingActionButton fab = (FloatingActionButton) findViewById(R.id.fab);
        fab.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Snackbar.make(view, "Replace with your own action", Snackbar.LENGTH_LONG)
                        .setAction("Action", null).show();
            }
        });*/

        checkPhoneReq();
        setupLayout();

        //for classic scan
        // Register the BroadcastReceiver
        IntentFilter filter = new IntentFilter(BluetoothDevice.ACTION_FOUND);
        registerReceiver(mReceiver, filter); // Don't forget to unregister during onDestroy
    }

    void setupLayout() {
        devices = new ArrayList<>();
        devicesArrayAdapter = new DeviceArrayAdapter(this, R.layout.list_item);
        listView.setAdapter(devicesArrayAdapter);
        listView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                //start details activity
                BluetoothDevice device = devicesArrayAdapter.getItem(position);
                startDetailsActivity(device);
            }
        });
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        unregisterReceiver(mReceiver);
    }

    private class DeviceArrayAdapter extends ArrayAdapter<BluetoothDevice> {
        Context context;
        int res;

        public DeviceArrayAdapter(Context context, int resource) {
            super(context, resource, devices);
//            this.devices = devices;
            this.context = context;
            res = resource;
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            LayoutInflater inflater = LayoutInflater.from(context);
            View row = inflater.inflate(res, parent, false);
            BluetoothDevice device = devices.get(position);
            TextView tv = (TextView) row.findViewById(R.id.label1);

            tv.setText("TYPE: " + device.getType() + " CLASS: " + device.getBluetoothClass());
            tv = (TextView) row.findViewById(R.id.label2);

            tv.setText("NAME: " + device.getName() + " STATE: " + device.getBondState());
            //STATE 10 = none 11 = bounding 12= bounded
            tv = (TextView) row.findViewById(R.id.label3);
            tv.setText(device.getAddress());
            return row;
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();

        if (id == R.id.action_settings) {
            log("Not implemented");
            return true;
        }
        if (id == R.id.action_scan) {
            if (!mScanning) {
                log("Start scanning for devices");
                scanForLeDevices(true);
            } else {
                log("Stopping");
                scanForLeDevices(false);
            }
            return true;
        }
        if (id == R.id.action_scan_classic) {
            log("Start scanning for devices (Classic)");
            scanForDevicesClassic();
            return true;
        }
        if (id == R.id.action_query) {
            queryPaired();
        }
        if (id == R.id.action_clear) {
            clearList();
        }
        return super.onOptionsItemSelected(item);
    }

    void log(String s) {
        Snackbar.make(container, s, Snackbar.LENGTH_LONG)
                .setAction("Action", null).show();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == REQUEST_ENABLE_BT) {
            // Make sure the request was successful
            if (resultCode == RESULT_OK) {
                //...
                log("Bluetooth enabled.");
            }
            if (requestCode == RESULT_CANCELED) {
                log("Bluetooth must be enabled.");
            }
        }
    }

    //
    // Behaviour
    //

    BluetoothAdapter mBluetoothAdapter;
    BluetoothLeScanner mBluetoothScanner; //new
    private final int REQUEST_ENABLE_BT = 1;
    private boolean mScanning;
    private Handler mHandler;
    // Stops scanning after 10 seconds.
    private static final long SCAN_PERIOD = 10000;
    ArrayList<BluetoothDevice> devices;

    void clearList(){
        devices.removeAll(devices);
        devicesArrayAdapter.notifyDataSetChanged();
    }

    void checkPhoneReq() {
        // Use this check to determine whether BLE is supported on the device. Then
        // you can selectively disable BLE-related features.
        if (!getPackageManager().hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE)) {
            log("Bluetooth LE not supported");
            supportedText.setText("not supported");
        }

        // Initializes Bluetooth adapter.
        final BluetoothManager bluetoothManager =
                (BluetoothManager) getSystemService(Context.BLUETOOTH_SERVICE);
        mBluetoothAdapter = bluetoothManager.getAdapter(); //Req min API 18

        // Ensures Bluetooth is available on the device and it is enabled. If not,
        // displays a dialog requesting user permission to enable Bluetooth.
        if (mBluetoothAdapter == null || !mBluetoothAdapter.isEnabled()) {
            //error prompting the user to go to Settings to enable Bluetooth:
            Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            startActivityForResult(enableBtIntent, REQUEST_ENABLE_BT);
        }
    }

    //Battery-Intensive operation
    //Guidelines:
    // As soon as you find your device, stop.
    // Never in a loop
    // Set a time limit
    //Need 2 implementation if support is needed for < API 21
    private void scanForLeDevices(boolean enable) {
        int apiVersion = android.os.Build.VERSION.SDK_INT;
        mHandler = new Handler(Looper.getMainLooper());
        if (apiVersion > android.os.Build.VERSION_CODES.KITKAT) {
            useScanner(enable);
        } else {
            useScannerOld(enable);
        }
    }

    void useScanner(boolean enable) {
        if (enable) {
            mBluetoothScanner = mBluetoothAdapter.getBluetoothLeScanner();
            // scan for devices but only for a specified time
            mHandler.postDelayed(new Runnable() {
                @Override
                public void run() {
                    mScanning = false;
                    if (mBluetoothScanner != null) {
                        mBluetoothScanner.stopScan(mScanCallback);
                        log("Scan ended after 10s");
                    }
                }
            }, SCAN_PERIOD);

            mBluetoothScanner.startScan(mScanCallback);

        } else {
            mScanning = false;
            if (mBluetoothScanner != null)
                mBluetoothScanner.stopScan(mScanCallback);
        }
    }

    //for kitkat and below
    void useScannerOld(boolean enable) {
        // targetting kitkat or bellow
        if (enable) {
            mHandler.postDelayed(new Runnable() {
                @Override
                public void run() {
                    mScanning = false;
                    mBluetoothAdapter.stopLeScan(mLeScanCallback);
                }
            }, SCAN_PERIOD);

            mBluetoothAdapter.startLeScan(mLeScanCallback);
        } else {
            mScanning = false;
            mBluetoothAdapter.stopLeScan(mLeScanCallback);
        }
    }

    //DEVICE SCAN CALLBACK

    //FOR NEW DEVICES
    ScanCallback mScanCallback = new ScanCallback() {
        @Override
        public void onScanResult(int callbackType, ScanResult result) {
            BluetoothDevice device = result.getDevice();
            addDeviceToList(device);
        }

        @Override
        public void onScanFailed(int errorCode) {
            super.onScanFailed(errorCode);
            log("Scan failed with code: " + errorCode);
        }
    };

    //FOR OLD DEVICES
    BluetoothAdapter.LeScanCallback mLeScanCallback = new BluetoothAdapter.LeScanCallback() {
        @Override
        public void onLeScan(final BluetoothDevice device, int rssi, byte[] scanRecord) {
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    addDeviceToList(device);
                }
            });
        }
    };

    //Adds the device to a List Adapter
    void addDeviceToList(BluetoothDevice device) {
        Log.v("test", "Found something.");
        devices.add(device);
        devicesArrayAdapter.notifyDataSetChanged();
    }

    ///
    void queryPaired() {
        Set<BluetoothDevice> deviceSet = mBluetoothAdapter.getBondedDevices();
        if (deviceSet.size() > 0) {
            for (BluetoothDevice device : deviceSet) {
                addDeviceToList(device);
            }
        }
    }

    ////

    // Create a BroadcastReceiver for ACTION_FOUND
    private final BroadcastReceiver mReceiver = new BroadcastReceiver() {
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            // When discovery finds a device
            if (BluetoothDevice.ACTION_FOUND.equals(action)) {
                // Get the BluetoothDevice object from the Intent
                BluetoothDevice device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
                // Add the name and address to an array adapter to show in a ListView
                addDeviceToList(device);
            }
        }
    };

    void scanForDevicesClassic() {
        /*
        The process is asynchronous and the method will immediately return with
        a boolean indicating whether discovery has successfully started.
        The discovery process usually involves an inquiry scan of about 12 seconds,
        followed by a page scan of each found device to retrieve its Bluetooth name.
         */
        mBluetoothAdapter.startDiscovery();
    }


    public final static String EXTRA_BTDEVICE = "btdevice";

    //on list click
    void startDetailsActivity(BluetoothDevice device){
        Intent i = new Intent(this, DetailsActivity.class);
        i.putExtra(EXTRA_BTDEVICE, device);
        startActivity(i);
    }
}
