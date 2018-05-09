package com.example.nicholasnguyen.smh;

import android.annotation.SuppressLint;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.UUID;
import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.content.Intent;
import android.media.MediaPlayer;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

public class MainActivity extends Activity {
    TextView bpm_input;
    Handler bluetoothIn;
    Button buttonPlayPause;



    final int handlerState = 0;                        //used to identify handler message
    private BluetoothAdapter btAdapter = null;
    private BluetoothSocket btSocket = null;
    private StringBuilder recDataString = new StringBuilder();

    private ConnectedThread mConnectedThread;

    // SPP UUID service - this should work for most devices
    private static final UUID BTMODULEUUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB");

    // String for MAC address
    private static String address;

    private MediaPlayer player;
    private String sensor0;

    @SuppressLint("HandlerLeak")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        //Link the buttons and textViews to respective views
        //txtStringLength = (TextView) findViewById(R.id.testView1);
        //sensorView0 = (TextView) findViewById(R.id.sensorView0);

        bpm_input = (TextView) findViewById(R.id.bpm_input);

        bluetoothIn = new Handler() {
            public void handleMessage(android.os.Message msg) {
                if (msg.what == handlerState) {                                         //if message is what we want
                    String readMessage = (String) msg.obj;                              // msg.arg1 = bytes from connect thread
                    recDataString.append(readMessage);                                  //keep appending to string until ~
                    int endOfLineIndex = recDataString.indexOf("~");                    // determine the end-of-line
                    if (endOfLineIndex > 0) {                                           // make sure there data before ~
                        String dataInPrint = recDataString.substring(0, endOfLineIndex);// extract string
                        //int dataLength = dataInPrint.length();                        //get length of data received
                        if (recDataString.charAt(0) == '#')                             //if it starts with # we know it is what we are looking for
                        {
                           // String sensor0 = recDataString.substring(1, 2);           //get sensor value from string between indices 1-5
                            sensor0 = recDataString.substring(1, 3);
                            //sensorView0.setText(" Sensor 0 Voltage = " + sensor0 + "V");    //update the textviews with sensor values
                            bpm_input.setText(sensor0);
                        }
                        recDataString.delete(0, recDataString.length());                //clear all string data
                        // strIncom =" ";
                        //dataInPrint = " ";
                    }
                }
            }
        };

        btAdapter = BluetoothAdapter.getDefaultAdapter();                               // get Bluetooth adapter
        checkBTState();

        //media player stuff
        player = MediaPlayer.create(this, R.raw.song);
        setPlayPauseButton();
        //changePlayerSpeed();
        //setSpeedOptions();
    }



    private BluetoothSocket createBluetoothSocket(BluetoothDevice device) throws IOException {

        return  device.createRfcommSocketToServiceRecord(BTMODULEUUID);
        //creates secure outgoing connection with BT device using UUID
    }

    @Override
    public void onResume() {
        super.onResume();

        //Get MAC address from DeviceListActivity via intent
        Intent intent = getIntent();

        //Get the MAC address from the DeviceListActivty via EXTRA
        address = intent.getStringExtra(DeviceListActivity.EXTRA_DEVICE_ADDRESS);

        //create device and set the MAC address
        BluetoothDevice device = btAdapter.getRemoteDevice(address);

        try {
            btSocket = createBluetoothSocket(device);
        } catch (IOException e) {
            Toast.makeText(getBaseContext(), "Socket creation failed", Toast.LENGTH_LONG).show();
        }
        // Establish the Bluetooth socket connection.
        try
        {
            btSocket.connect();
        } catch (IOException e) {
            try
            {
                btSocket.close();
            } catch (IOException e2)
            {
                //insert code to deal with this
            }
        }
        mConnectedThread = new ConnectedThread(btSocket);
        mConnectedThread.start();

        //I send a character when resuming.beginning transmission to check device is connected
        //If it is not an exception will be thrown in the write method and finish() will be called
        mConnectedThread.write("x");
    }

    @Override
    public void onPause()
    {
        super.onPause();
        try
        {
            //Don't leave Bluetooth sockets open when leaving activity
            btSocket.close();
        } catch (IOException e2) {
            //insert code to deal with this
        }
    }

    //Checks that the Android device Bluetooth is available and prompts to be turned on if off
    private void checkBTState() {

        if(btAdapter==null) {
            Toast.makeText(getBaseContext(), "Device does not support bluetooth", Toast.LENGTH_LONG).show();
        } else {
            if (btAdapter.isEnabled()) {
            } else {
                Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
                startActivityForResult(enableBtIntent, 1);
            }
        }
    }

    //create new class for connect thread
    private class ConnectedThread extends Thread {
        private final InputStream mmInStream;
        private final OutputStream mmOutStream;

        //creation of the connect thread
        public ConnectedThread(BluetoothSocket socket) {
            InputStream tmpIn = null;
            OutputStream tmpOut = null;

            try {
                //Create I/O streams for connection
                tmpIn = socket.getInputStream();
                tmpOut = socket.getOutputStream();
            } catch (IOException e) { }

            mmInStream = tmpIn;
            mmOutStream = tmpOut;
        }

        public void run() {
            byte[] buffer = new byte[256];
            int bytes;

            // Keep looping to listen for received messages
            while (true) {
                try {
                    bytes = mmInStream.read(buffer);            //read bytes from input buffer
                    String readMessage = new String(buffer, 0, bytes);
                    // Send the obtained bytes to the UI Activity via handler
                    bluetoothIn.obtainMessage(handlerState, bytes, -1, readMessage).sendToTarget();
                } catch (IOException e) {
                    break;
                }
            }
        }
        //write method
        public void write(String input) {
            byte[] msgBuffer = input.getBytes();           //converts entered String into bytes
            try {
                mmOutStream.write(msgBuffer);                //write bytes over BT connection via outstream
            } catch (IOException e) {
                //if you cannot write, close the application
                Toast.makeText(getBaseContext(), "Connection Failure", Toast.LENGTH_LONG).show();
                finish();

            }
        }
    }


    //Music stuff below
    private void setPlayPauseButton() {
        final Button playPauseButton = (Button) findViewById(R.id.buttonPlayPause);
        playPauseButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (player.isPlaying()) {
                    changePlayerSpeed();
                    player.pause();
                } else {
                    player.start();
                }
            }
            /*
            @Override
            public void onResume() {
                while (player.isPlaying()) {
                    if (sensor0.equals("1")) {
                        player.setPlaybackParams(player.getPlaybackParams().setSpeed(0.33f));
                    }
                    if (sensor0.equals("2")) {
                        player.setPlaybackParams(player.getPlaybackParams().setSpeed(0.66f));
                    }
                    if (sensor0.equals("3")) {
                        player.setPlaybackParams(player.getPlaybackParams().setSpeed(1.0f));
                    }
                    if (sensor0.equals("4")) {
                        player.setPlaybackParams(player.getPlaybackParams().setSpeed(1.0f));
                    }
                    if (sensor0.equals("5")) {
                        player.setPlaybackParams(player.getPlaybackParams().setSpeed(1.2f));
                    }
                    if (sensor0.equals("6")) {
                        player.setPlaybackParams(player.getPlaybackParams().setSpeed(1.5f));
                    }
                }
            }*/
        });
    }

    private void changePlayerSpeed() {
        //player.start();
        if (player.isPlaying()) {
            if (sensor0.equals("50") || sensor0.equals("51") || sensor0.equals("52") || sensor0.equals("53") || sensor0.equals("54") || sensor0.equals("55")) {
                player.setPlaybackParams(player.getPlaybackParams().setSpeed(0.70f));
            }
            else if (sensor0.equals("56") || sensor0.equals("57") || sensor0.equals("58") || sensor0.equals("59") || sensor0.equals("60")) {
                player.setPlaybackParams(player.getPlaybackParams().setSpeed(0.80f));
            }
            else if (sensor0.equals("61") || sensor0.equals("62") || sensor0.equals("63") || sensor0.equals("64") || sensor0.equals("65")) {
                player.setPlaybackParams(player.getPlaybackParams().setSpeed(0.90f));
            }
            else if (sensor0.equals("66") || sensor0.equals("67") || sensor0.equals("68") || sensor0.equals("69") || sensor0.equals("70")) {
                player.setPlaybackParams(player.getPlaybackParams().setSpeed(0.95f));
            }
            else if (sensor0.equals("71") || sensor0.equals("72") || sensor0.equals("73") || sensor0.equals("74") || sensor0.equals("75")) {
                player.setPlaybackParams(player.getPlaybackParams().setSpeed(1.0f));
            }
            else if (sensor0.equals("76") || sensor0.equals("77") || sensor0.equals("78") || sensor0.equals("79") || sensor0.equals("80")) {
                player.setPlaybackParams(player.getPlaybackParams().setSpeed(1.05f));
            }
            else if (sensor0.equals("81") || sensor0.equals("82") || sensor0.equals("83") || sensor0.equals("84") || sensor0.equals("85")) {
                player.setPlaybackParams(player.getPlaybackParams().setSpeed(1.20f));
            }
            else if (sensor0.equals("86") || sensor0.equals("87") || sensor0.equals("88") || sensor0.equals("89") || sensor0.equals("90")) {
                player.setPlaybackParams(player.getPlaybackParams().setSpeed(1.30f));
            }
            else if (sensor0.equals("91") || sensor0.equals("92") || sensor0.equals("93") || sensor0.equals("94") || sensor0.equals("95")) {
                player.setPlaybackParams(player.getPlaybackParams().setSpeed(1.35f));
            }
            else if (sensor0.equals("96") || sensor0.equals("97") || sensor0.equals("98") || sensor0.equals("99") || sensor0.equals("100")) {
                player.setPlaybackParams(player.getPlaybackParams().setSpeed(1.40f));
            }
            else if (sensor0.equals("101") || sensor0.equals("102") || sensor0.equals("103") || sensor0.equals("104") || sensor0.equals("105")) {
                player.setPlaybackParams(player.getPlaybackParams().setSpeed(1.50f));
            }
            else {
                player.setPlaybackParams(player.getPlaybackParams().setSpeed(1.0f));
            }
        }
    }
}