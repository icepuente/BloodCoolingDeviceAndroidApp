package com.captstone.bloodcoolingmonitor;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.os.Handler;
import android.os.Message;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.util.SparseArray;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.TabHost;
import android.widget.TextView;
import android.widget.Toast;

import java.io.IOException;
import java.io.InputStream;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

public class MainActivity extends AppCompatActivity {

    Button startStopButton, resetButton;
    TextView timerText, statusText;
    TabHost tabHost;
    long savedTime;

    private CountDownTimer timer;
    private BluetoothAdapter adapter;
    private Set<BluetoothDevice> pairedDevices;
    private BluetoothConnectThread connect;
    private BluetoothConnectedThread connected;

    private SparseArray<TextView> resultFields;
    private int[] resultFieldIDs;

    private Menu menu;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        timerText = (TextView) findViewById(R.id.timerTextView);
        statusText = (TextView) findViewById(R.id.statusLabel);

        resultFieldIDs = new int[]{R.id.peltierTextField, R.id.pumpField, R.id.therm1TempField,
                R.id.therm2TempField, R.id.therm3TempField, R.id.irTempField};
        resultFields = new SparseArray<>();
        for (int resultFieldID : resultFieldIDs) {
            resultFields.put(resultFieldID, (TextView) findViewById(resultFieldID));
        }

        setupButtons();
        setupTabs();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        adapter = BluetoothAdapter.getDefaultAdapter();
        if (adapter == null) {
            Toast.makeText(getApplicationContext(), getString(R.string.bluetooth_missing),
                    Toast.LENGTH_LONG).show();
            return false;
        }
        pairedDevices = adapter.getBondedDevices();
        getMenuInflater().inflate(R.menu.activity_menu, menu);
        this.menu = menu;
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.menu_bluetooth_on:
                if (!adapter.isEnabled()) {
                    adapter.enable();
                    Toast.makeText(getApplicationContext(), getString(R.string.bluetooth_on_toast),
                            Toast.LENGTH_LONG).show();
                } else {
                    Toast.makeText(getApplicationContext(), getString(R.string.bluetooth_already_on_toast),
                            Toast.LENGTH_LONG).show();
                }
                return true;
            case R.id.menu_bluetooth_off:
                if (adapter.isEnabled()) {
                    adapter.disable();
                    Toast.makeText(getApplicationContext(), getString(R.string.bluetooth_off_toast),
                            Toast.LENGTH_LONG).show();
                } else {
                    Toast.makeText(getApplicationContext(), getString(R.string.bluetooth_already_off_toast),
                            Toast.LENGTH_LONG).show();
                }
                return true;
            case R.id.menu_connect_device:
                if (!adapter.isEnabled()) {
                    Toast.makeText(getApplicationContext(), getString(R.string.turn_on_bluetooth_request),
                            Toast.LENGTH_LONG).show();
                    return true;
                }
                if (item.getTitle().toString().equals(getString(R.string.disconnect_menu_title))) {
                    if (connected != null && connected.isAlive()) {
                        connected.cancel();
                        connected.interrupt();
                    }
                    return true;
                }
                pairedDevices = adapter.getBondedDevices();
                BluetoothDevice arduino = null;
                for (BluetoothDevice device : pairedDevices) {
                    if (device.getName().equals("HC-06")) {
                        arduino = device;
                        break;
                    }
                }
                if (arduino != null && item.getTitle().toString().equals(getString(R.string.connect_menu_title))) {
                    if (connect != null && connect.isAlive()) {
                        connect.cancel();
                        connect.interrupt();
                    }
                    connect = new BluetoothConnectThread(arduino);
                    connect.start();
                } else {
                    Toast.makeText(getApplicationContext(), getString(R.string.pair_device_message),
                            Toast.LENGTH_LONG).show();
                }
                return true;
            default:
                return super.onOptionsItemSelected(item);
        }
    }

    public void setupButtons() {
        startStopButton = (Button) findViewById(R.id.startStop);
        startStopButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (timer == null) {
                    timer = createCountdownTimer(180000);
                }
                Button b = (Button) view;
                if (b.getText().toString().contains(getString(R.string.start))) {
                    b.setText(getString(R.string.pause));
                    timer.start();
                } else if (b.getText().toString().contains(getString(R.string.resume))) {
                    b.setText(getString(R.string.pause));
                    timer = createCountdownTimer(savedTime);
                    timer.start();
                } else {
                    b.setText(getString(R.string.resume));
                    timer.cancel();
                }
            }
        });

        resetButton = (Button) findViewById(R.id.reset);
        resetButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (timer != null) {
                    timer.cancel();
                    timer = null;
                }
                timerText.setText(formatTime(180000));
                startStopButton.setText(getString(R.string.start));
            }
        });
    }

    public CountDownTimer createCountdownTimer(long time) {
        return new CountDownTimer(time, 1000) {
            @Override
            public void onTick(long l) {
                savedTime = l;
                timerText.setText(formatTime(l));
            }

            @Override
            public void onFinish() {
                timerText.setText(getString(R.string.done));
            }
        };
    }

    public void setupTabs() {
        tabHost = (TabHost) findViewById(R.id.tabHost);

        tabHost.setup();

        TabHost.TabSpec tabSpec = tabHost.newTabSpec("controls");
        tabSpec.setContent(R.id.Controls);
        tabSpec.setIndicator(getString(R.string.controls_tab));
        tabHost.addTab(tabSpec);

        tabSpec = tabHost.newTabSpec("sensors");
        tabSpec.setContent(R.id.Sensors);
        tabSpec.setIndicator(getString(R.string.sensors_tab));
        tabHost.addTab(tabSpec);
    }

    public String formatTime(long millis) {
        return String.format(Locale.getDefault(), "%02d:%02d:%02d",
                TimeUnit.MILLISECONDS.toHours(millis),
                TimeUnit.MILLISECONDS.toMinutes(millis) -
                        TimeUnit.HOURS.toMinutes(TimeUnit.MILLISECONDS.toHours(millis)),
                TimeUnit.MILLISECONDS.toSeconds(millis) -
                        TimeUnit.MINUTES.toSeconds(TimeUnit.MILLISECONDS.toMinutes(millis)));
    }

    private final Handler handler = new Handler() {
        @Override
        public void handleMessage(Message msg) {

            switch (msg.what) {
                case Constants.MESSAGE_READ:
                    byte[] writeBuffer = (byte[]) msg.obj;
                    int begin = msg.arg1;
                    int end = msg.arg2;

                    String message;
                    String[] statuses;
                    message = new String(writeBuffer);
                    message = message.substring(begin, end);
                    Log.d("Monitor", message);
                    statuses = message.split(",");
                    for (int i = 0; i < resultFieldIDs.length; i++) {
                        resultFields.get(resultFieldIDs[i]).setText(statuses[i]);
                    }
                    break;
                case Constants.MENU_TO_CONNECT:
                    MenuItem disconnectMenu = menu.findItem(R.id.menu_connect_device);
                    disconnectMenu.setTitle(R.string.connect_menu_title);
                    Toast.makeText(getApplicationContext(), getString(R.string.disconnected_device_string),
                            Toast.LENGTH_LONG).show();
                    statusText.setText(getString(R.string.status_disconnected));
                    break;
                case Constants.MENU_TO_DISCONNECT:
                    MenuItem connectMenu = menu.findItem(R.id.menu_connect_device);
                    connectMenu.setTitle(R.string.disconnect_menu_title);
                    Toast.makeText(getApplicationContext(), getString(R.string.connected_device_string),
                            Toast.LENGTH_LONG).show();
                    statusText.setText(getString(R.string.status_connected));
                    break;
            }
        }
    };

    private class BluetoothConnectThread extends Thread {

        private final BluetoothSocket btSocket;
        private final BluetoothDevice btDevice;

        BluetoothConnectThread(BluetoothDevice device) {
            BluetoothSocket temp = null;
            this.btDevice = device;
            try {
                temp = btDevice.createRfcommSocketToServiceRecord(UUID.fromString(
                        getString(R.string.uuid)));
            } catch (IOException e) {
                Log.d("Monitor", e.toString());
            }
            this.btSocket = temp;
        }

        public void run() {
            adapter.cancelDiscovery();
            try {
                btSocket.connect();
            } catch (IOException connectException) {
                try {
                    btSocket.close();
                } catch (IOException closeException) {
                    Log.d("Monitor", closeException.toString());
                }
                return;
            }
            connected = new BluetoothConnectedThread(btSocket);
            connected.start();
        }

        void cancel() {
            try {
                btSocket.close();
            } catch (IOException e) {
                Log.d("Monitor", e.toString());
            }
        }
    }

    private class BluetoothConnectedThread extends Thread {

        private final BluetoothSocket btSocket;
        private final InputStream inputStream;
        //private final OutputStream outputStream;

        BluetoothConnectedThread(BluetoothSocket socket) {
            this.btSocket = socket;
            InputStream tempInput = null;
            //OutputStream tempOutput = null;
            try {
                tempInput = socket.getInputStream();
                //tempOutput = socket.getOutputStream();
            } catch (IOException e) {
                Log.d("Monitor", e.toString());
            }
            this.inputStream = tempInput;
            //this.outputStream = tempOutput;
        }

        public void run() {
            try {
                handler.obtainMessage(Constants.MENU_TO_DISCONNECT).sendToTarget();
                byte[] buffer = new byte[1024];

                while (true) {
                    try {
                        int i = 0;
                        while (i < 26) {
                            buffer[i] = (byte) inputStream.read();
                            i++;
                        }
                        handler.obtainMessage(Constants.MESSAGE_READ, 0, 26, buffer).sendToTarget();
                    } catch (IOException e) {
                        Log.d("Monitor", e.toString());
                        break;
                    }
                }
            } catch (Exception e) {
                handler.obtainMessage(Constants.MENU_TO_CONNECT).sendToTarget();
            }
            handler.obtainMessage(Constants.MENU_TO_CONNECT).sendToTarget();
        }

        /**
         * public void write(byte[] bytes) {
         * try {
         * outputStream.write(bytes);
         * } catch (IOException e) {
         * Log.d("Monitor", e.toString());
         * }
         * }
         */

        void cancel() {
            try {
                btSocket.close();
            } catch (IOException e) {
                Log.d("Monitor", e.toString());
            }
        }
    }
}
