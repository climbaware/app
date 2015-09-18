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
import android.view.View.OnLongClickListener;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.SimpleExpandableListAdapter;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
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

    private TextView mConnectionState;
    private String mDeviceName;
    private String mDeviceAddress;
    private BluetoothLeService mBluetoothLeService;
    private ArrayList<ArrayList<BluetoothGattCharacteristic>> mGattCharacteristics =
            new ArrayList<ArrayList<BluetoothGattCharacteristic>>();
    private boolean mConnected = false;

    private final String LIST_NAME = "NAME";
    private final String LIST_UUID = "UUID";


    private SoundPoolPlayer sound;
    private ArmbandController mArmbandController;


    /* Study */
    private LogFile mLogFile;
    private Spinner mParticipantGroupSpinner;
    private boolean timerIsRunning = false;
    private boolean isClimbing = false;
    private int sentCueIndex = 0;
    private int modalityIndex = 0;
    private int routeIndex = 0;
    private int[] intensitySequence;
    private Random rnd;
    private EditText log;
    static String[] CLIMBERANSWERS = new String[]{"LOW 1", "MEDIUM 2", "HIGH 3"};
    private Button noResponseButton;
    private Chronometer mChronometer;
    private Button nextbutton;
    private static final int CUES_PER_MODALITY = 3;
    private Button reachedTop;
    String participant_group;


    /**
     * Show Climber Response Dialog
     * <p/>
     * shows dialog to enter the climbers response about the
     * perceived intensity of the visual/tactile/audible cue
     */
    void showDialog() {
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
    private class ClimberResponseDialog extends DialogFragment {
        @Override
        public Dialog onCreateDialog(Bundle savedInstanceState) {
            AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
            builder.setTitle(R.string.climber_response_question)
                    .setItems(CLIMBERANSWERS, new DialogInterface.OnClickListener() {
                        public void onClick(DialogInterface dialog, int i) {

                            writeToLog("climber_response", CLIMBERANSWERS[i]);

                            // The 'which' argument contains the index position
                            // of the selected item
                        }
                    });
            return builder.create();
        }

    }

    /**
     * Random Array Shuffle
     * <p/>
     * Implementing Fisher–Yates shuffle
     * for randomly shffling an array
     *
     * @param ar array to be shuffeld
     */
    private void shuffleArray(int[] ar) {

        for (int i = ar.length - 1; i > 0; i--) {
            int index = rnd.nextInt(i + 1);
            // Simple swap
            int a = ar[index];
            ar[index] = ar[i];
            ar[i] = a;
        }
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
            mArmbandController = new ArmbandController(mBluetoothLeService);

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
            } else if (BluetoothLeService.ACTION_GATT_SERVICES_DISCOVERED.equals(action)) {
                // Show all the supported services and characteristics on the user interface.
                displayGattServices(mBluetoothLeService.getSupportedGattServices());
            }
        }
    };


    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.gatt_services_characteristics);

        final Intent intent = getIntent();
        mDeviceName = intent.getStringExtra(EXTRAS_DEVICE_NAME);
        mDeviceAddress = intent.getStringExtra(EXTRAS_DEVICE_ADDRESS);
        mLogFile = LogFile.getInstance(this);

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

        mParticipantGroupSpinner = (Spinner) findViewById(R.id.participant_group);
        participant_group = (String) mParticipantGroupSpinner.getSelectedItem();


        // initialize the sound player
        sound = new SoundPoolPlayer(this);

        /*
          Study Control
         */
        mChronometer = (Chronometer) findViewById(R.id.chronometer);
        nextbutton = (Button) findViewById(R.id.study_next);
        noResponseButton = (Button) findViewById(R.id.study_no_response);
        noResponseButton.setEnabled(false);
        reachedTop = (Button) findViewById(R.id.reached_top);
        reachedTop.setEnabled(false);
        rnd = new Random();
        resetStudy();

        /*
         * Participant group dropdown select
         */
        mParticipantGroupSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {

            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {

            }
        });

        /**
         * Button No Response
         *
         * study_no_response (the climber did not react to the the audo/tactile/light feedback)
         */
        noResponseButton.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                executeNextStudyAction(false);
            }
        });

        /**
         * Button Reached top
         *
         * mark the end of the climb
         */
        reachedTop.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {

                reachedTop.setEnabled(false);
                nextbutton.setEnabled(true);
                noResponseButton.setEnabled(true);

                AlertDialog.Builder alert = new AlertDialog.Builder(DeviceControlActivity.this);
                writeToLog("reached_top");

                if(modalityIndex < 2) {
                    alert.setTitle("Route is finished. Go to the next route.");
                } else {
                    nextbutton.setEnabled(false);
                    noResponseButton.setEnabled(false);

                    alert.setTitle("Participant finished. Enter name and group of next participant and long press RESET.");
                }

                nextbutton.setText("Start Climbing");
                noResponseButton.setEnabled(false);

                isClimbing = false;

                alert.show();
            }
        });



        /*
         *  NEXT Button (States: Start Study/Send Notification/Climber Responded)
         *
         * UI behavior for conducting the user study
         * Button that starts the study and let's the coordinator
         * send the next visual/tactile/audible cue
         */
        nextbutton.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                executeNextStudyAction(true);
            }
        });

        /*
         * RESET Button
         *
         * UI behavior for conducting the user study
         * resets the current user study state to the beginning
         * returns to first modality and first cue
         */
        findViewById(R.id.study_reset).setOnLongClickListener(new OnLongClickListener() {

            public boolean onLongClick(View v) {
                resetStudy();
                log.setText("");

                return true;

            }

        });



        /*
            Direct Device Control
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

    private void sendModalityCue(String participant_group) {
        String[] pdata = participant_group.split("-");
        String group_number = pdata[0];
        String climbing_grade = pdata[1];
        String notification_modalities = pdata[2];
        if (modalityIndex < notification_modalities.length()) {
            switch (notification_modalities.charAt(modalityIndex)) {
                case 'V':
                    tactileModality(intensitySequence[sentCueIndex]);
                    break;
                case 'S':
                    audibleModality(intensitySequence[sentCueIndex]);
                    break;
                case 'L':
                    visualModality(intensitySequence[sentCueIndex]);
                    break;
            }
        }
    }


    private void resetStudy() {
        mChronometer.reset();

        modalityIndex = 0;
        sentCueIndex = 0;
        routeIndex = 0;
        nextbutton.setText("Start Study");

        nextbutton.setEnabled(true);

        isClimbing = false;
        timerIsRunning = false;

        // END of the json file
        if(!mLogFile.isEmpty()) {

            mLogFile.log("]}");
            mLogFile.createFreshLogFile();
        }

        writeMetaDataToLogFile();

        mLogFile.log(",\"events\":[");

    }

    private String climbingGrade() {
        participant_group = (String) mParticipantGroupSpinner.getSelectedItem();
        return String.valueOf(participant_group.split("-")[1].charAt(routeIndex));
    }

    private void writeMetaDataToLogFile() {
        // start of the JSON file
        mLogFile.log("{\"metadata\":");

        try {
            JSONObject json = new JSONObject();
            json.put("participant_group", mParticipantGroupSpinner.getSelectedItem());
            json.put("participant_name", ((EditText) findViewById(R.id.participant_name)).getText());
            mLogFile.log(json.toString());

        } catch (JSONException e) {
            mLogFile.log("{}");
            throw new RuntimeException(e);
        }
    }


    private void executeNextStudyAction(boolean climberHasResponded) {

        participant_group = (String) mParticipantGroupSpinner.getSelectedItem();

        // STATE 1: Initial Button State: Start Study
        if (!isClimbing) {
            isClimbing = true;

            // was "Start Study Before"
            nextbutton.setText("Send Notification");
            noResponseButton.setEnabled(false);
            writeToLog("started_climbing", participant_group);
            writeToLog("grade", climbingGrade());


            // generate random intensity sequence
            intensitySequence = new int[]{1, 2, 3};
            shuffleArray(intensitySequence);
            writeToLog("generated_sequence: ", intensitySequence[0] + "," + intensitySequence[1] + "," + intensitySequence[2]);
            return;
        }

        // STATE 2: Initial Button State: Send Notification
        if (!timerIsRunning) { // timer was not running, study coordinator sent modality feedback to climber
            timerIsRunning = true;

            mChronometer.start();
            nextbutton.setText("Climber responded");
            noResponseButton.setEnabled(true);
            sendModalityCue(participant_group);


            // STATE 3: Initial Button State: Climber responded
        } else { // timer was running, climber reacted to feedback
            mChronometer.stop();
            String time = mChronometer.getText().toString();
            timerIsRunning = false;
            mChronometer.reset();
            writeToLog("response_time", time);
            noResponseButton.setEnabled(false);

            if (climberHasResponded) {
                // show climber response dialog
                showDialog();
            } else {
                writeToLog("no_response");
            }

            /*
             * determine next state
             */
            nextbutton.setText("Send Notification");

            // next state --> STATE 2: sent notification of less than 3 were send before
            if (sentCueIndex == CUES_PER_MODALITY - 1) {
                writeToLog("route_finished");
                reachedTop.setEnabled(true);
                nextbutton.setEnabled(false);

                sentCueIndex = 0;


                if (routeIndex == 1) { // there exists two routes (easy and hard)
                    nextbutton.setText("Next Modality");
                    nextbutton.setEnabled(false);
                    noResponseButton.setEnabled(false);
                    isClimbing = false;
                    routeIndex = 0;
                    writeToLog("modality_finished");
                    //shareStudyResults();
                    if (modalityIndex == 2) {
                        nextbutton.setText("Participant Finished");
                        writeToLog("participant_finished");


                        nextbutton.setEnabled(false);
                        noResponseButton.setEnabled(false);

                    } else {
                        modalityIndex++;
                    }
                } else {
                    routeIndex++;
                }
            } else {
                sentCueIndex++;
            }


            Toast.makeText(getBaseContext(), time,
                    Toast.LENGTH_SHORT).show();


        }


    }

    private void writeToLog(String event) {
        writeToLog(event, "", "");
    }

    private void writeToLog(String event, int data) {
        writeToLog(event, Integer.toString(data));
    }

    private void writeToLog(String event, int data, String text) {
        writeToLog(event, Integer.toString(data), text);
    }

    private void writeToLog(String event, String data) {
        writeToLog(event, data, "");
    }

        private void writeToLog(String event, String data, String text) {
        Long tsLong = System.currentTimeMillis()/1000;
        String ts = tsLong.toString();

        String v = event;
            if(!data.isEmpty())
                v += " (" + data + ")";

            log.append("\n" + v);


        try {
            JSONObject json = new JSONObject();
            json.put("timestamp", ts);
            json.put("event", event);
            json.put("description", text);
            json.put("data", data);

            mLogFile.log(json.toString()+",");

        } catch (JSONException e) {
            throw new RuntimeException(e);
        }


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

        writeToLog("visual_cue",intensity,"sent visual cue, intensity " + intensity);

        mArmbandController.sendString("L" + Integer.toString(intensity));

        Toast.makeText(getBaseContext(), "Sent VISUAL (" + intensity + ") cue.",
                Toast.LENGTH_SHORT).show();

    }

    /*
     * Used in the user study to notify the climber
     * using a tactile cue.
     */
    private void tactileModality(int intensity) {
        writeToLog("tactile_cue", intensity, "sent tactile cue, intensity " + intensity);
        mArmbandController.sendString("V" + Integer.toString(intensity));

        Toast.makeText(getBaseContext(), "Sent TACTILE (" + intensity + ") cue.",
                Toast.LENGTH_SHORT).show();

    }

    /*
      * Used in the user study to notify the climber
      * using a audible cue.
      */
    private void audibleModality(int intensity) {
        writeToLog("audible_cue", intensity, "sent audible cue, intensity " + intensity);
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


        
        Toast.makeText(getBaseContext(), "Sent AUDIBLE (" + intensity + ") cue.",
                Toast.LENGTH_SHORT).show();
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
