package com.example.android.bluetoothlegatt;

import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattService;

import java.nio.ByteBuffer;
import java.util.UUID;

/**
 * Created by fred on 17/09/15.
 */
public class ArmbandController {

    private static String AMBIENT_BAND_UUID_SERVICE = "00002220-0000-1000-8000-00805f9b34fb";
    private static String AMBIENT_BAND_UUID_CHAR = "00002222-0000-1000-8000-00805f9b34fb";
    private BluetoothLeService mBluetoothLeService;

    public ArmbandController(BluetoothLeService mBluetoothLeService) {
        this.mBluetoothLeService = mBluetoothLeService;
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


    public void sendString(String s){
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


}
