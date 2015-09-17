/*
 * Copyright (C) 2013 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.example.android.bluetoothlegatt;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.app.DialogFragment;
import android.app.Fragment;
import android.app.FragmentManager;
import android.app.FragmentTransaction;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattService;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.IBinder;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ExpandableListView;
import android.widget.SimpleExpandableListAdapter;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.UUID;
import java.util.Random;

/**
 * For a given BLE device, this Activity provides the user interface to connect, display data,
 * and display GATT services and characteristics supported by the device.  The Activity
 * communicates with {@code BluetoothLeService}, which in turn interacts with the
 * Bluetooth LE API.
 */
public class DeviceControlActivity extends Activity {
    private final static String TAG = DeviceControlActivity.class.getSimpleName();

    public static final String EXTRAS_DEVICE_NAME = "DEVICE_NAME";
    public static final String EXTRAS_DEVICE_ADDRESS = "DEVICE_ADDRESS";

    private LogFile mLogFile;
    private TextView mConnectionState;
    private String mDeviceName;
    private String mDeviceAddress;
    private BluetoothLeService mBluetoothLeService;
    private ArrayList<ArrayList<BluetoothGattCharacteristic>> mGattCharacteristics =
            new ArrayList<ArrayList<BluetoothGattCharacteristic>>();
    private boolean mConnected = false;
    private BluetoothGattCharacteristic mNotifyCharacteristic;

    private Spinner mGroupSpinner;
    private boolean timerIsRunning = false;
    private boolean studyIsStarted = false;
    private int sentCueIndex = 0;
    private int modalityIndex = 0;


    private int[] intensitySequence;
    private Random rnd;
    FragmentManager fragmentManager;


    private EditText log;


    private final String LIST_NAME = "NAME";
    private final String LIST_UUID = "UUID";

    public static String AMBIENT_BAND_UUID_SERVICE = "00002220-0000-1000-8000-00805f9b34fb";
    public static String AMBIENT_BAND_UUID_CHAR = "00002222-0000-1000-8000-00805f9b34fb";
    private SoundPoolPlayer sound;

    static String[] CLIMBERANSWERS = new String[] {"1", "2", "3"};

    void showDialog() {
        // DialogFragment.show() will take care of adding the fragment
        // in a transaction.  We also want to remove any currently showing
        // dialog, so make our own transaction and take care of that here.
        FragmentTransaction ft = getFragmentManager().beginTransaction();
        Fragment prev = getFragmentManager().findFragmentByTag("dialog");
        if (prev != null) {
            ft.remove(prev);
        }
        ft.addToBackStack(null);

        // Create and show the dialog.
        DialogFragment newFragment = new ClimberResponseDialog();
        newFragment.show(ft, "dialog");
    }

    /*
     * Climber Response Dialog
     */
    public class ClimberResponseDialog extends DialogFragment {
        @Override
        public Dialog onCreateDialog(Bundle savedInstanceState) {
            AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
            builder.setTitle("What did the climber respond?")
                    .setItems(CLIMBERANSWERS, new DialogInterface.OnClickListener() {
                        public void onClick(DialogInterface dialog, int i) {

                            writeToLog(CLIMBERANSWERS[i]);

                            // The 'which' argument contains the index position
                            // of the selected item
                        }
                    });
            return builder.create();
        }

    }

    // Implementing Fisher–Yates shuffle
    private void shuffleArray(int[] ar)
    {

        for (int i = ar.length - 1; i > 0; i--)
        {
            int index = rnd.nextInt(i + 1);
            // Simple swap
            int a = ar[index];
            ar[index] = ar[i];
            ar[i] = a;
        }
    }

    public void sendNotification(View v) {

        BluetoothGattService ambientBandService = mBluetoothLeService.getService(UUID.fromString(AMBIENT_BAND_UUID_SERVICE));
        if (ambientBandService == null) {
            System.out.println("service null"); return;
        }
        BluetoothGattCharacteristic ambiendBandCharacteristic = ambientBandService.getCharacteristic(UUID.fromString(AMBIENT_BAND_UUID_CHAR));
        if (ambiendBandCharacteristic == null) {
            System.out.println("characteristic null"); return;
        }
        //ambiendBandCharacteristic.setValue(new byte[] {0x00,0x00,0x00,0x01, 0x02, 0x0B , (byte) 0xB8, (byte) (byte) 0xff, (byte) (byte) 0xff, (byte) 0xff, (byte) 0xff, (byte) 0xfe});
        ambiendBandCharacteristic.setValue(fullOnOffString("110",1000,255));
        boolean status = mBluetoothLeService.writeCharacteristic(ambiendBandCharacteristic);
        System.out.println("Write Status: " + status);

        System.out.println(getOnOffString("010", 1000,255));
    }


    public static byte[] hexStringToByteArray(String s) {
        int len = s.length();
        byte[] data = new byte[len / 2];
        for (int i = 0; i < len; i += 2) {
            data[i / 2] = (byte) ((Character.digit(s.charAt(i), 16) << 4)
                    + Character.digit(s.charAt(i+1), 16));
        }
        return data;
    }

    public byte[] getOnOffString(String ids, int duration, int intensity){
        byte[] result = new byte[4];

        byte b = (byte) Integer.parseInt(ids, 2);


        result[0] = b;


        ByteBuffer bb = ByteBuffer.allocate(4);
        bb.putInt(duration);
        byte[] duration_res = bb.array();
        result[1] = duration_res[2];
        result[2] = duration_res[3];

        bb = ByteBuffer.allocate(4);
        bb.putInt(intensity);
        byte[] intensity_ar = bb.array();
        result[3] = intensity_ar[3];





        return result;
    }

    public byte[] fullOnOffString( String ids, int duration, int intensity){

        byte[] onoff = getOnOffString(ids, duration, intensity);

        // build the string
        byte[] result = new byte[4*3];
        result[0] = (byte) 0x00;
        result[1] = (byte) 0x00;
        result[2] = (byte) 0x00;
        result[3] = (byte) 0x01;

        result[4] = onoff[0];
        result[5] = onoff[1];
        result[6] = onoff[2];
        result[7] = onoff[3];

        result[8] = (byte) 0xff;
        result[9] = (byte) 0xff;
        result[10] = (byte) 0xff;
        result[11] = (byte) 0xfe;

        return result;

    }





    // Code to manage Service lifecycle.
    private final ServiceConnection mServiceConnection = new ServiceConnection() {

        @Override
        public void onServiceConnected(ComponentName componentName, IBinder service) {
            mBluetoothLeService = ((BluetoothLeService.LocalBinder) service).getService();
            if (!mBluetoothLeService.initialize()) {
                Log.e(TAG, "Unable to initialize Bluetooth");
                finish();
            }
            // Automatically connects to the device upon successful start-up initialization.
            mBluetoothLeService.connect(mDeviceAddress);
        }

        @Override
        public void onServiceDisconnected(ComponentName componentName) {
            mBluetoothLeService = null;
        }
    };

    // Handles various events fired by the Service.
    // ACTION_GATT_CONNECTED: connected to a GATT server.
    // ACTION_GATT_DISCONNECTED: disconnected from a GATT server.
    // ACTION_GATT_SERVICES_DISCOVERED: discovered GATT services.
    // ACTION_DATA_AVAILABLE: received data from the device.  This can be a result of read
    //                        or notification operations.
    private final BroadcastReceiver mGattUpdateReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            final String action = intent.getAction();
            if (BluetoothLeService.ACTION_GATT_CONNECTED.equals(action)) {
                mConnected = true;
                updateConnectionState(R.string.connected);
                invalidateOptionsMenu();
            } else if (BluetoothLeService.ACTION_GATT_DISCONNECTED.equals(action)) {
                mConnected = false;
                updateConnectionState(R.string.disconnected);
                invalidateOptionsMenu();
                clearUI();
            } else if (BluetoothLeService.ACTION_GATT_SERVICES_DISCOVERED.equals(action)) {
                // Show all the supported services and characteristics on the user interface.
                displayGattServices(mBluetoothLeService.getSupportedGattServices());
            }
        }
    };

    // If a given GATT characteristic is selected, check for supported features.  This sample
    // demonstrates 'Read' and 'Notify' features.  See
    // http://d.android.com/reference/android/bluetooth/BluetoothGatt.html for the complete
    // list of supported characteristic features.
    private final ExpandableListView.OnChildClickListener servicesListClickListner =
            new ExpandableListView.OnChildClickListener() {
                @Override
                public boolean onChildClick(ExpandableListView parent, View v, int groupPosition,
                                            int childPosition, long id) {
                    if (mGattCharacteristics != null) {
                        final BluetoothGattCharacteristic characteristic =
                                mGattCharacteristics.get(groupPosition).get(childPosition);
                        final int charaProp = characteristic.getProperties();
                        if ((charaProp | BluetoothGattCharacteristic.PROPERTY_READ) > 0) {
                            // If there is an active notification on a characteristic, clear
                            // it first so it doesn't update the data field on the user interface.
                            if (mNotifyCharacteristic != null) {
                                mBluetoothLeService.setCharacteristicNotification(
                                        mNotifyCharacteristic, false);
                                mNotifyCharacteristic = null;
                            }
                            mBluetoothLeService.readCharacteristic(characteristic);
                        }
                        if ((charaProp | BluetoothGattCharacteristic.PROPERTY_NOTIFY) > 0) {
                            mNotifyCharacteristic = characteristic;
                            mBluetoothLeService.setCharacteristicNotification(
                                    characteristic, true);
                        }
                        return true;
                    }
                    return false;
                }
    };

    private void clearUI() {
    }

    private void sendString(String s){
        byte[] bs = s.getBytes();

        BluetoothGattService ambientBandService = mBluetoothLeService.getService(UUID.fromString(AMBIENT_BAND_UUID_SERVICE));
        if (ambientBandService == null) {
            System.out.println("service null"); return;
        }
        BluetoothGattCharacteristic ambiendBandCharacteristic = ambientBandService.getCharacteristic(UUID.fromString(AMBIENT_BAND_UUID_CHAR));
        if (ambiendBandCharacteristic == null) {
            System.out.println("characteristic null"); return;
        }
        //ambiendBandCharacteristic.setValue(new byte[] {0x00,0x00,0x00,0x01, 0x02, 0x0B , (byte) 0xB8, (byte) (byte) 0xff, (byte) (byte) 0xff, (byte) 0xff, (byte) 0xff, (byte) 0xfe});



        ambiendBandCharacteristic.setValue(bs);
        boolean status = mBluetoothLeService.writeCharacteristic(ambiendBandCharacteristic);
        System.out.println("Write Status: " + status);

    }


    private void sendByteString(byte[] bs){
        BluetoothGattService ambientBandService = mBluetoothLeService.getService(UUID.fromString(AMBIENT_BAND_UUID_SERVICE));
        if (ambientBandService == null) {
            System.out.println("service null"); return;
        }
        BluetoothGattCharacteristic ambiendBandCharacteristic = ambientBandService.getCharacteristic(UUID.fromString(AMBIENT_BAND_UUID_CHAR));
        if (ambiendBandCharacteristic == null) {
            System.out.println("characteristic null"); return;
        }
        //ambiendBandCharacteristic.setValue(new byte[] {0x00,0x00,0x00,0x01, 0x02, 0x0B , (byte) 0xB8, (byte) (byte) 0xff, (byte) (byte) 0xff, (byte) 0xff, (byte) 0xff, (byte) 0xfe});



        ambiendBandCharacteristic.setValue(bs);
        boolean status = mBluetoothLeService.writeCharacteristic(ambiendBandCharacteristic);
        System.out.println("Write Status: " + status);

    }

    private void sendBlink(String ids, int duration, int intensity){
        sendByteString(fullOnOffString(ids, duration, intensity));

    }


    public class BlinkSequenceItem{
        public String getIds() {
            return ids;
        }

        public int getDuration() {
            return duration;
        }

        public int getIntensity() {
            return intensity;
        }

        private final String ids;
        private final int duration;
        private final int intensity;

        public BlinkSequenceItem(String ids, int duration, int intensity){
            this.ids = ids;
            this.duration = duration;
            this.intensity = intensity;
        }
    }


    public void sendBlinkSquence(BlinkSequenceItem[] sequence){

        // build the string
        byte[] result = new byte[4*2 + 4*sequence.length];
        result[0] = (byte) 0x00;
        result[1] = (byte) 0x00;
        result[2] = (byte) 0x00;
        result[3] = (byte) 0x01;

        int current_index = 4;

        for(BlinkSequenceItem bsi : sequence){
            byte[] onoff = getOnOffString(bsi.getIds(), bsi.getDuration(), bsi.getIntensity());

            result[current_index] = onoff[0];
            current_index++;
            result[current_index] = onoff[1];
            current_index++;
            result[current_index] = onoff[2];
            current_index++;
            result[current_index] = onoff[3];
            current_index++;

        }


        result[current_index] = (byte) 0xff;
        current_index++;

        result[current_index] = (byte) 0xff;
        current_index++;

        result[current_index] = (byte) 0xff;
        current_index++;

        result[current_index] = (byte) 0xfe;


        sendByteString(result);


    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.gatt_services_characteristics);

        final Intent intent = getIntent();
        mDeviceName = intent.getStringExtra(EXTRAS_DEVICE_NAME);
        mDeviceAddress = intent.getStringExtra(EXTRAS_DEVICE_ADDRESS);

        // Sets up UI references.
        ((TextView) findViewById(R.id.device_address)).setText(mDeviceAddress);
        mConnectionState = (TextView) findViewById(R.id.connection_state);

        getActionBar().setTitle(mDeviceName);
        getActionBar().setDisplayHomeAsUpEnabled(true);
        Intent gattServiceIntent = new Intent(this, BluetoothLeService.class);
        bindService(gattServiceIntent, mServiceConnection, BIND_AUTO_CREATE);

        // participant group dropdown select
        Spinner spinner = (Spinner) findViewById(R.id.participant_group);
        ArrayAdapter<CharSequence> adapter = ArrayAdapter.createFromResource(this,
                R.array.participant_groups, android.R.layout.simple_spinner_item);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinner.setAdapter(adapter);
        log = (EditText) findViewById(R.id.timelog);

        log.setFocusable(false);

        mGroupSpinner = (Spinner) findViewById(R.id.participant_group);



        // initialize the sound player
        sound = new SoundPoolPlayer(this);

        /*
          Study Control
         */
        final Chronometer mChronometer = (Chronometer) findViewById(R.id.chronometer);
        final Button nextbutton = (Button) findViewById(R.id.study_next);
        final Button noResponseButton = (Button) findViewById(R.id.study_no_response);
        noResponseButton.setEnabled(false);
        rnd = new Random();

        mGroupSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                mChronometer.reset();
                nextbutton.setText("Start Study");
                nextbutton.setEnabled(true);
                log.setText("");
                writeToLog("changed group");
                studyIsStarted = false;
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {

            }
        });

        // study_no_response (the climber did not react to the the audo/tactile/light feedback)
        noResponseButton.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {

                timerIsRunning = false;
                mChronometer.reset();

                nextbutton.setText("Send Notification");

                if(sentCueIndex == 3) {
                    writeToLog("route finished");
                }

                if(sentCueIndex == 6) {
                    nextbutton.setText("Next Modality");
                    nextbutton.setEnabled(true);
                    noResponseButton.setEnabled(false);
                    studyIsStarted = false;
                    writeToLog("modality finished");
                    sentCueIndex = 0;

                    //shareStudyResults();
                }

                sentCueIndex++;

                writeToLog("no_response");


            }





            //LogFile file = LogFile.getInstance(getApplicationContext());






        });




        // next
        nextbutton.setOnClickListener(new View.OnClickListener() {

            public void onClick(View v) {
                String participant_group = (String) mGroupSpinner.getSelectedItem();

                if(!studyIsStarted) {
                    nextbutton.setText("Send Notification");
                    studyIsStarted = true;
                    writeToLog("study_started for: " + participant_group);

                    // generate random intensity sequence
                    intensitySequence = new int[] {1,2,3};
                    shuffleArray(intensitySequence);
                    writeToLog("generated new feedback intensity sequence: " + intensitySequence[0] + "," + intensitySequence[1] + "," + intensitySequence[2]);

                    return;
                }



                if(!timerIsRunning) { // timer was not running, study coordinator sent modality feedback to climber

                    mChronometer.start();
                    timerIsRunning = true;


                    nextbutton.setText("Climber responded");
                    noResponseButton.setEnabled(true);
                    sendNextFeedBack(participant_group);



                } else { // timer was running, climber reacted to feedback
                    mChronometer.stop();
                    String time = mChronometer.getText().toString();
                    timerIsRunning = false;
                    mChronometer.reset();

                    // show climber response dialog

                    nextbutton.setText("Send Notification");

                    writeToLog(time);
                    showDialog();

                    if (sentCueIndex == 3) {
                        writeToLog("route finished");
                    }

                    if (sentCueIndex == 6) {
                        nextbutton.setText("Next Modality");
                        nextbutton.setEnabled(true);
                        noResponseButton.setEnabled(false);
                        studyIsStarted = false;
                        writeToLog("modality finihsed");
                        //shareStudyResults();
                        sentCueIndex = 0;
                        modalityIndex++;

                    } else {

                    }

                    sentCueIndex++;


                    Toast.makeText(getBaseContext(), time,
                            Toast.LENGTH_LONG).show();


                }


            }
        });

        // reset
        findViewById(R.id.study_reset).setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                mChronometer.reset();

                modalityIndex = 0;
                sentCueIndex = 0;
                nextbutton.setText("Start Study");

                nextbutton.setEnabled(true);
                log.setText("");

                writeToLog("reset initiated by study coordinator");
                studyIsStarted = false;

            }
        });












        /*
            Device Control
         */

        final Button button_vibrate_short = (Button) findViewById(R.id.bt_vibrate_short);
        button_vibrate_short.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                tactileModality(1);

            }
        });

        final Button button_vibrate_normal = (Button) findViewById(R.id.bt_vibrate_normal);
        button_vibrate_normal.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                tactileModality(2);
            }
        });

        final Button button_vibrate_long = (Button) findViewById(R.id.bt_vibrate_long);
        button_vibrate_long.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                tactileModality(3);
            }
        });


        final Button button_sound_1 = (Button) findViewById(R.id.bt_sound_1);
        button_sound_1.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                sound.playShortResource(R.raw.one);
            }
        });

        final Button button_sound_2 = (Button) findViewById(R.id.bt_sound_2);
        button_sound_2.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                sound.playShortResource(R.raw.two);
            }
        });

        final Button button_sound_3 = (Button) findViewById(R.id.bt_sound_3);
        button_sound_3.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                sound.playShortResource(R.raw.three);
            }
        });



        final Button button_on_short = (Button) findViewById(R.id.bt_on_short);
        button_on_short.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                visualModality(1);
            }
        });

        final Button button_on_normal = (Button) findViewById(R.id.bt_on_normal);
        button_on_normal.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                visualModality(2);
            }
        });

        final Button button_on_long = (Button) findViewById(R.id.bt_on_long);
        button_on_long.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                visualModality(3);
            }
        });



    }

    private void sendNextFeedBack(String participant_group) {
        String[] pdata = participant_group.split("-");
        String group_number = pdata[0];
        String climbing_grade = pdata[1];
        String notification_modalities = pdata[2];
        if (modalityIndex < notification_modalities.length()) {
            switch (notification_modalities.charAt(modalityIndex)) {
                case 'V':
                    tactileModality(intensitySequence[modalityIndex]);
                    break;
                case 'S':
                    audibleModality(intensitySequence[modalityIndex]);
                    break;
                case 'L':
                    visualModality(intensitySequence[modalityIndex]);
                    break;
            }
            sentCueIndex++;
        }
    }

    private void writeToLog(String text) {



        log.append("\n" + text);
    }

    // share the study results
    public void shareStudyResults(View v) {
        Intent shareIntent = new Intent(Intent.ACTION_SEND);
        shareIntent.setType("text/plain");
        shareIntent.putExtra(Intent.EXTRA_TEXT, log.getText());
        shareIntent.putExtra(Intent.EXTRA_SUBJECT, "ClimbAware Study Results");
        startActivity(shareIntent);
    }

    /*
     * Used in the user study to notify the climber
     * using a visual cue.
     */
    private void visualModality(int intensity) {
        writeToLog("sent visual cue, intensity " + intensity);

        sendString("L" + Integer.toString(intensity));

        Toast.makeText(getBaseContext(), "Sent VISUAL cue.",
                Toast.LENGTH_SHORT).show();

    }

    /*
     * Used in the user study to notify the climber
     * using a tactile cue.
     */
    private void tactileModality(int intensity) {
        writeToLog("sent tactile cue, intensity " + intensity);
        sendString("V" + Integer.toString(intensity));

        Toast.makeText(getBaseContext(), "Sent TACTILE cue.",
                Toast.LENGTH_SHORT).show();

    }

    /*
      * Used in the user study to notify the climber
      * using a audible cue.
      */
    private void audibleModality(int intensity) {
        writeToLog("sent audible cue, intensity " + intensity);
        switch (intensity){
            case 1:
                sound.playShortResource(R.raw.one);
                break;
            case 2:
                sound.playShortResource(R.raw.two);
                break;
            case 3:
                sound.playShortResource(R.raw.three);
                break;

            default:
                break;
        }


        
        Toast.makeText(getBaseContext(), "Sent AUDITIVE cue.",
                Toast.LENGTH_LONG).show();
    }


    @Override
    protected void onResume() {
        super.onResume();
        registerReceiver(mGattUpdateReceiver, makeGattUpdateIntentFilter());
        if (mBluetoothLeService != null) {
            final boolean result = mBluetoothLeService.connect(mDeviceAddress);
            Log.d(TAG, "Connect request result=" + result);
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        unregisterReceiver(mGattUpdateReceiver);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        unbindService(mServiceConnection);
        mBluetoothLeService = null;
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.gatt_services, menu);
        if (mConnected) {
            menu.findItem(R.id.menu_connect).setVisible(false);
            menu.findItem(R.id.menu_disconnect).setVisible(true);
        } else {
            menu.findItem(R.id.menu_connect).setVisible(true);
            menu.findItem(R.id.menu_disconnect).setVisible(false);
        }
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch(item.getItemId()) {
            case R.id.menu_connect:
                mBluetoothLeService.connect(mDeviceAddress);
                return true;
            case R.id.menu_disconnect:
                mBluetoothLeService.disconnect();
                return true;
            case android.R.id.home:
                onBackPressed();
                return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private void updateConnectionState(final int resourceId) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                mConnectionState.setText(resourceId);
            }
        });
    }



    // Demonstrates how to iterate through the supported GATT Services/Characteristics.
    // In this sample, we populate the data structure that is bound to the ExpandableListView
    // on the UI.
    private void displayGattServices(List<BluetoothGattService> gattServices) {
        if (gattServices == null) return;
        String uuid = null;
        String unknownServiceString = getResources().getString(R.string.unknown_service);
        String unknownCharaString = getResources().getString(R.string.unknown_characteristic);
        ArrayList<HashMap<String, String>> gattServiceData = new ArrayList<HashMap<String, String>>();
        ArrayList<ArrayList<HashMap<String, String>>> gattCharacteristicData
                = new ArrayList<ArrayList<HashMap<String, String>>>();
        mGattCharacteristics = new ArrayList<ArrayList<BluetoothGattCharacteristic>>();

        // Loops through available GATT Services.
        for (BluetoothGattService gattService : gattServices) {
            HashMap<String, String> currentServiceData = new HashMap<String, String>();
            uuid = gattService.getUuid().toString();
            currentServiceData.put(
                    LIST_NAME, SampleGattAttributes.lookup(uuid, unknownServiceString));
            currentServiceData.put(LIST_UUID, uuid);
            gattServiceData.add(currentServiceData);

            ArrayList<HashMap<String, String>> gattCharacteristicGroupData =
                    new ArrayList<HashMap<String, String>>();
            List<BluetoothGattCharacteristic> gattCharacteristics =
                    gattService.getCharacteristics();
            ArrayList<BluetoothGattCharacteristic> charas =
                    new ArrayList<BluetoothGattCharacteristic>();

            // Loops through available Characteristics.
            for (BluetoothGattCharacteristic gattCharacteristic : gattCharacteristics) {
                charas.add(gattCharacteristic);
                HashMap<String, String> currentCharaData = new HashMap<String, String>();
                uuid = gattCharacteristic.getUuid().toString();
                currentCharaData.put(
                        LIST_NAME, SampleGattAttributes.lookup(uuid, unknownCharaString));
                currentCharaData.put(LIST_UUID, uuid);
                gattCharacteristicGroupData.add(currentCharaData);
            }
            mGattCharacteristics.add(charas);
            gattCharacteristicData.add(gattCharacteristicGroupData);
        }

        SimpleExpandableListAdapter gattServiceAdapter = new SimpleExpandableListAdapter(
                this,
                gattServiceData,
                android.R.layout.simple_expandable_list_item_2,
                new String[] {LIST_NAME, LIST_UUID},
                new int[] { android.R.id.text1, android.R.id.text2 },
                gattCharacteristicData,
                android.R.layout.simple_expandable_list_item_2,
                new String[] {LIST_NAME, LIST_UUID},
                new int[] { android.R.id.text1, android.R.id.text2 }
        );
    }

    private static IntentFilter makeGattUpdateIntentFilter() {
        final IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(BluetoothLeService.ACTION_GATT_CONNECTED);
        intentFilter.addAction(BluetoothLeService.ACTION_GATT_DISCONNECTED);
        intentFilter.addAction(BluetoothLeService.ACTION_GATT_SERVICES_DISCOVERED);
        intentFilter.addAction(BluetoothLeService.ACTION_DATA_AVAILABLE);
        return intentFilter;
    }
}
