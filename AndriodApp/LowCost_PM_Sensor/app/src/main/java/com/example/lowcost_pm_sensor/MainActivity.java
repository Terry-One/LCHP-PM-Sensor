package com.example.lowcost_pm_sensor;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.AlertDialog;
import android.app.Dialog;
import android.app.ProgressDialog;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothProfile;
import android.bluetooth.le.BluetoothLeScanner;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanResult;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.text.InputType;
import android.text.method.ScrollingMovementMethod;
import android.util.Log;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.android.material.navigation.NavigationBarView;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.FirebaseDatabase;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.UUID;


public class MainActivity extends AppCompatActivity {
    BottomNavigationView bottomNavigationView;
    Button BleSwitch;
    Button start;
    Button ChangeFreq;
    TextView connection_state;
    TextView devices;


    private List<String> mPermissionList = new ArrayList<>();
    private String[] permissions = new String[]{Manifest.permission.ACCESS_FINE_LOCATION,Manifest.permission.ACCESS_COARSE_LOCATION};
    private final int mRequestCode = 200;


    private List<String> received_data = new ArrayList<>();
    private List<String> current_time = new ArrayList<>();

    private TextView receive_data;
    // for the bluetooth connection
    String TAG = "BleActivity";

    boolean USE_SPECIFIC_UUID = true;//Use specific UUID
    // 服务标识
    private final UUID mServiceUUID = UUID.fromString("ef12c126-80cf-11ec-a8a3-0242ac120002");
    // 特征标识（读取数据）PM value  7772e5db-3868-4112-a1a9-f2669d106bf3   76b0499a-80ea-11ec-a8a3-0242ac120002
    private final UUID mCharacteristicUUID_pm = UUID.fromString("76b0499a-80ea-11ec-a8a3-0242ac120002");
    // 特征标识（发送数据） Frequency
    private final UUID mCharacteristicUUID_freq = UUID.fromString("36612c92-80ea-11ec-a8a3-0242ac120002");
    // 描述标识 -- check with group
    private final UUID mCharacteristicUUID_RTC = UUID.fromString("f37e1b98-afdc-11ec-b909-0242ac120002");

    private final UUID mConfigUUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb");



    private BluetoothAdapter mBluetoothAdapter;
    private BluetoothLeScanner mBLEScanner;
    private boolean mScanning;//是否正在搜索
    private Handler mHandler;
    private final int SCAN_TIME = 3000;
    private ArrayList<BluetoothDevice> bluetoothDeviceArrayList = new ArrayList<>();
    int selIndex = 0;

    // 是否正在连接
    private boolean mConnectionState = false;
    private BluetoothGatt mBluetoothGatt = null;

    private ArrayList<BluetoothGattCharacteristic> writeCharacteristicArrayList ;
    private ArrayList<BluetoothGattCharacteristic> readCharacteristicArrayList ;
    private ArrayList<BluetoothGattCharacteristic> notifyCharacteristicArrayList ;

    ProgressDialog waitDialog;
    ProgressDialog cancelDialog;

    private FirebaseAuth authentication;
    private String uid;
    private String Mode;
    private String Freq;
    private String DatasetName = "Buffer";
    private String BLE_state = "Off";

    @SuppressLint("WrongViewCast")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        initPermission();
        initView();

        Mode = getIntent().getExtras().getString("Mode");
        if (Mode.equals("Offline")){
            getSupportActionBar().setTitle("LowCost_PM_Sensor (Offline)");
        }

        authentication = FirebaseAuth.getInstance();
        if (authentication.getCurrentUser() != null){
            uid = authentication.getCurrentUser().getUid();
            System.out.println("------------------->" + uid);
        }

        // 获取BluetoothAdapter
        mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        mHandler = new Handler();
        writeCharacteristicArrayList = new ArrayList<>();
        readCharacteristicArrayList = new ArrayList<>();
        notifyCharacteristicArrayList = new ArrayList<>();

        BleSwitch = findViewById(R.id.BLE_switch);
        start = findViewById(R.id.Start_btn);
        ChangeFreq = findViewById(R.id.Freq_btn);

        start.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {

                if (BLE_state.equals("On")){

                    AlertDialog.Builder builder = new AlertDialog.Builder(MainActivity.this);
                    builder.setTitle("Input the DataSetName: ");

                    // Set up the input
                    final EditText input = new EditText(MainActivity.this);
                    // Specify the type of input expected; this, for example, sets the input as a password, and will mask the text
                    input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
                    builder.setView(input);

                    // Set up the buttons
                    builder.setPositiveButton("OK", new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            DatasetName = input.getText().toString();
                        }
                    });
                    builder.setNegativeButton("Cancel", new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            dialog.cancel();
                        }
                    });

                    builder.show();
                }
            }
        });

        ChangeFreq.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {

                if (BLE_state.equals("On")){

                    AlertDialog.Builder builder = new AlertDialog.Builder(MainActivity.this);
                    builder.setTitle("Input the Frequency you want: ");

                    // Set up the input
                    final EditText input = new EditText(MainActivity.this);
                    // Specify the type of input expected; this, for example, sets the input as a password, and will mask the text
                    input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
                    builder.setView(input);

                    // Set up the buttons
                    builder.setPositiveButton("OK", new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            Freq = input.getText().toString();


                        }
                    });
                    builder.setNegativeButton("Cancel", new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            dialog.cancel();
                        }
                    });

                    builder.show();
                }
            }
        });

        BleSwitch.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Log.d(TAG, "click received");
                if(view.getId() == R.id.BLE_switch){
                    // If connect, then perform disconnecting
                    if (mConnectionState){
                        cancelDialog.show();
                        mHandler.postDelayed(new Runnable() {
                            @Override
                            public void run() {
                                cancelDialog.dismiss();
                            }
                        },1000);
                        mConnectionState = false;
                        connection_state.setText("Disconnected");
                        BLE_state = "Off";
                        //BleSwitch.setBackgroundResource(R.drawable.ic_circle_grey);
                        BleSwitch.setBackgroundResource(R.mipmap.freshair);



                        if(mBluetoothGatt!=null){
                            mBluetoothGatt.disconnect();
                        }
                    }else {
                        if (!checkBleDevice(getApplicationContext())){
                            return;
                        }
                        bluetoothDeviceArrayList.clear();
                        Log.d(TAG, "scanDevice begin");
                        scanLeDevice(true);
                    }
                }
            }
        });
        // Process Navigation Bar
        bottomNavigationView = findViewById(R.id.bottom_navigation_event);
        bottomNavigationView.setSelectedItemId(R.id.navigation_Main);


        bottomNavigationView.setOnItemSelectedListener(new NavigationBarView.OnItemSelectedListener() {
            @Override
            public boolean onNavigationItemSelected(@NonNull MenuItem item) {
                switch (item.getItemId()) {
                    case R.id.navigation_Data:
                        Intent intent1 = new Intent(MainActivity.this, ViewDataActivity.class);
                        if (Mode.equals("Offline")){
                            intent1.putExtra("Mode","Offline");
                        }else{
                            intent1.putExtra("Mode","Online");
                        }
                        startActivity(intent1);
                        return true;
                    case R.id.navigation_Main:
                        return true;
                    case R.id.navigation_Alert:
                        Intent intent2 = new Intent(MainActivity.this, ProfileActivity.class);
                        if (Mode.equals("Offline")){
                            intent2.putExtra("Mode","Offline");
                        }else{
                            intent2.putExtra("Mode","Online");
                        }
                        startActivity(intent2);
                        return true;
                }
                return false;
            }
        });

    }



    /**
     * Permission check and request
     */
    private void initPermission() {
        mPermissionList.clear();//Clear waiting Permission
        //Check if permission is given
        for (int i = 0; i < permissions.length; i++) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && checkSelfPermission(permissions[i])!= PackageManager.PERMISSION_GRANTED) {
                mPermissionList.add(permissions[i]);//Add permission
            }
        }
        //Request for permission
        if (mPermissionList.size() > 0 && Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {//有权限没有通过，需要申请
            requestPermissions(permissions, mRequestCode);
        }
    }


    /**
     * Initialization
     *    Ble button：R.id.btn_ble
     *    Device name：R.id.tv_device_name
     *    Data receving：R.id.edit_receive_data
     *    Show: Connecting...
     *    Inform: No Device find
     */
    private void initView(){

        devices = findViewById(R.id.device);
        connection_state = findViewById(R.id.connection);

        receive_data = findViewById(R.id.edit_receive_data);
        receive_data.setMovementMethod(ScrollingMovementMethod.getInstance());


        waitDialog = new ProgressDialog(this);
        waitDialog.setProgressStyle(ProgressDialog.STYLE_SPINNER);
        waitDialog.setCancelable(false);
        waitDialog.setCanceledOnTouchOutside(false);

        waitDialog.setTitle("Please Wait");
        waitDialog.setMessage("Searching for Device...");

        cancelDialog = new ProgressDialog(this);
        cancelDialog.setProgressStyle(ProgressDialog.STYLE_SPINNER);
        cancelDialog.setCancelable(false);
        cancelDialog.setCanceledOnTouchOutside(false);

        cancelDialog.setTitle("Please Wait");
        cancelDialog.setMessage("Disconnecting");
    }

    /**
     * Search for Device
     * mBluetoothAdapter.startLeScan(mLeScanCallback);
     */
    @SuppressLint("MissingPermission")
    private void scanLeDevice(final boolean enable) {
        if (enable) {//true
            waitDialog.setMessage("Searching...");
            waitDialog.show();
            mScanning = true;   // mark current state are scanning

            if (mBLEScanner == null){
                mBLEScanner = mBluetoothAdapter.getBluetoothLeScanner();
            }

            //Start Scanning
            mBLEScanner.startScan(mScanCallback);
            //Stop scanning to safe energy
            mHandler.postDelayed(new Runnable() {
                @Override
                public void run() {
                    //stop scanning and mark current state as not scanning
                    mScanning = false;
                    mBLEScanner.stopScan(mScanCallback);
                    waitDialog.dismiss();
                    if (bluetoothDeviceArrayList.size()>0){
                        showScanDeviceList();
                    }
                }
            }, SCAN_TIME);
        } else {//false
            //mark current state as not scanning
            mScanning = false;
            mBLEScanner.stopScan(mScanCallback);
            waitDialog.dismiss();
        }
    }

    /**
     * Result of scanning
     */
    private ScanCallback mScanCallback = new ScanCallback() {
        @SuppressLint("MissingPermission")
        @Override
        public void onScanResult(int callbackType, ScanResult result) {
            super.onScanResult(callbackType, result);
            //get result from scanned device
            BluetoothDevice device = result.getDevice();
            if (device.getName() == null) {
                return;
            }

            for (int i = 0; i < bluetoothDeviceArrayList.size(); i++) {
                if (device.getAddress().equals(bluetoothDeviceArrayList.get(i).getAddress())) {
                    return;
                }
            }
            bluetoothDeviceArrayList.add(device);
        }

        //return numbers of result
        @Override
        public void onBatchScanResults(List<ScanResult> results) {
            super.onBatchScanResults(results);
        }

        @Override
        public void onScanFailed(int errorCode) {
            super.onScanFailed(errorCode);
        }
    };

    @SuppressLint("MissingPermission")
    private void showScanDeviceList(){
        final String[] deviceNames = new String[bluetoothDeviceArrayList.size()];
        for (int i = 0; i < bluetoothDeviceArrayList.size(); i++) {
            if (bluetoothDeviceArrayList.get(i).getName() == null) {
                deviceNames[i] = "Unknow";
            } else {
                deviceNames[i] = bluetoothDeviceArrayList.get(i).getName();
            }
        }

        new AlertDialog.Builder(this).setTitle("Select Device")
                .setSingleChoiceItems(deviceNames, -1, new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int item) {
                        selIndex = item;
                    }
                }).setPositiveButton("Connect", new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int which) {

                waitDialog.setMessage("Connecting...");
                waitDialog.show();
                connectLeDevice(bluetoothDeviceArrayList.get(selIndex).getAddress());
            }
        }).setNegativeButton("Cancel", new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int which) {
            }
        }).show();
    }



    /**
     *We can use Mac address to connect
     * @param address
     * @return
     */

    @SuppressLint("MissingPermission")
    public boolean connectLeDevice(final String address) {
        Log.d(TAG, "连接" + address);
        if (mBluetoothAdapter == null || address == null) {
            Log.d(TAG,"BluetoothAdapter不能初始化 or 未知 address.");
            return false;
        }

        final BluetoothDevice device = mBluetoothAdapter.getRemoteDevice(address);
        if (device == null) {
            Log.d(TAG, "设备没找到，不能连接");
            return false;
        }

        if(mBluetoothGatt!=null){
            mBluetoothGatt.close();
        }
        readCharacteristicArrayList.clear();
        writeCharacteristicArrayList.clear();
        notifyCharacteristicArrayList.clear();
        mBluetoothGatt = device.connectGatt(this, false, mGattCallback);//真正的连接
        return true;
    }

    /**
     * BluetoothGattCallback can be used to pass connecting status and result
     * here it is used to handle connection status
     */
    private final BluetoothGattCallback mGattCallback = new BluetoothGattCallback() {
        @SuppressLint("MissingPermission")
        @Override
        public void onConnectionStateChange(BluetoothGatt gatt, int status,
                                            int newState) {
            Log.d(TAG, "status" + status+",newSatate"+newState);
            if (newState == BluetoothProfile.STATE_CONNECTED) {//当连接状态发生改变
                if (mBluetoothGatt == gatt){
                    mConnectionState = true;
                    // after connection, try discover service
                    gatt.discoverServices();
                    mHandler.post(new Runnable() {
                        @Override
                        public void run() {
                            connection_state.setText("Connected");
                            BLE_state = "On";
                            String device_name = mBluetoothGatt.getDevice().getName();
                            //BleSwitch.setBackgroundResource(R.drawable.ic_circle_green);
                            BleSwitch.setBackgroundResource(R.mipmap.bledisconnect);
                            devices.setText("" + device_name);
                            waitDialog.dismiss();
                        }
                    });
                }else {
                    if (mBluetoothGatt == gatt){
                        mConnectionState = true;
                        gatt.discoverServices();
                        String device_name = mBluetoothGatt.getDevice().getName();
                    }
                }

            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {//while not able to connect
                if (mBluetoothGatt == gatt){
                    mConnectionState = false;
                    if(mBluetoothGatt!=null){
                        mBluetoothGatt.close();
                    }
                    mHandler.post(new Runnable() {
                        @Override
                        public void run() {
                            //未连接
                            waitDialog.dismiss();
                            String device_name = "";
                            devices.setText("");
                        }
                    });
                }else {
                    if (mBluetoothGatt == gatt){
                        mConnectionState = false;
                        if(mBluetoothGatt!=null){
                            mBluetoothGatt.close();
                        }
                        String device_name = mBluetoothGatt.getDevice().getName();
                    }

                }

            }
        }


        @Override
        // find new service，the return of mBluetoothGatt.discoverServices()
        // here it handles the pm values that send from device
        public void onServicesDiscovered(BluetoothGatt gatt, int status) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                if (mBluetoothGatt == gatt) {
                    if (USE_SPECIFIC_UUID) {
                        //obtain specific service uuid
                        BluetoothGattService gattService = mBluetoothGatt.getService(mServiceUUID);
                        //specifiv service cannot be null
                        if (gattService != null) {
                            //obtain characteristic
                            BluetoothGattCharacteristic gattCharacteristic = gattService.getCharacteristic(mCharacteristicUUID_pm);
                            setBluetoothGattNotification(mBluetoothGatt, gattCharacteristic, true);


                            BluetoothGattCharacteristic gattFreq = gattService.getCharacteristic(mCharacteristicUUID_freq);
                            setBluetoothGattNotification(mBluetoothGatt, gattFreq, true);


                            //获取特定特征成功
                            if (gattCharacteristic != null) {
                                readCharacteristicArrayList.add(gattCharacteristic);
                                notifyCharacteristicArrayList.add(gattCharacteristic);
                            }
                            if (gattFreq != null){
                                writeCharacteristicArrayList.add(gattFreq);
                            }
                        }
                    }

                }

            } else {
                Log.i(TAG, "onServicesDiscovered received: " + status);
            }
        }

        // Read Char
        @Override
        public void onCharacteristicRead(BluetoothGatt gatt,
                                         BluetoothGattCharacteristic characteristic, int status) {
            final byte[] desData = characteristic.getValue();
            Log.i(TAG,"onCharacteristicRead:"+desData.toString());
            if (status == BluetoothGatt.GATT_SUCCESS) {
                //final byte[] desData = characteristic.getValue();
                Log.i(TAG,"onCharacteristicRead:"+desData.toString());
            }
        }

        @Override // TODO this is going to be write process for freq
        public void onDescriptorWrite(BluetoothGatt gatt,
                                      BluetoothGattDescriptor descriptor, int status) {
            Log.i(TAG,"onDescriptorWrite");

        }

        @Override
        public void onCharacteristicChanged(BluetoothGatt gatt,
                                            BluetoothGattCharacteristic characteristic) {
            final byte[] String = characteristic.getValue();
            String desData = new String(String);
            Log.i(TAG,"onCharacteristicChanged:"+desData.toString());
            if (mBluetoothGatt == gatt){
                mHandler.post(new Runnable() {
                    @Override
                    public void run() {

                        receive_data.setText(desData);
                        received_data.add(desData.toString());
                        String currentDate = new SimpleDateFormat("dd-MM-yyyy", Locale.getDefault()).format(new Date());
                        String currentTime = new SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(new Date());
                        current_time.add( currentDate + " , "  + currentTime);
                        if(Mode.equals("Online")){
                            HashMap<String,Object> map = new HashMap<>();
                            DataSet dataS = new DataSet(received_data, current_time,DatasetName, "3" );
//                            map.put("DatasetName",DatasetName);
//                            map.put("DataTime",current_time);
//                            map.put("Frequency",3);
//                            map.put("Data",received_data);
                            map.put(DatasetName,dataS);

                            FirebaseDatabase.getInstance().getReference().child(uid).child("Datasets").child(currentDate).child(DatasetName).updateChildren(map);
                        }
                    }
                });
            }

        }

        @Override
        public void onReadRemoteRssi(BluetoothGatt gatt, int rssi, int status) {
            //mBluetoothGatt.readRemoteRssi()调用得到，rssi即信号强度，做防丢器时可以不断使用此方法得到最新的信号强度，从而得到距离。


        }

        public void onCharacteristicWrite(BluetoothGatt gatt,BluetoothGattCharacteristic gattFreq, int status) {

            gattFreq.setValue(Freq);
            mBluetoothGatt.writeCharacteristic(gattFreq);
            System.out.println("--------write success----- status:" + status);
        }
    };

    /**
     * Set BLE notification if data are received
     */
    @SuppressLint("MissingPermission")
    private boolean setBluetoothGattNotification(BluetoothGatt bluetoothGatt, BluetoothGattCharacteristic characteristic, boolean enable){
        //Logger.d("setCharacteristicNotification");
        System.out.println("set---------test");
        bluetoothGatt.setCharacteristicNotification(characteristic, enable);
        BluetoothGattDescriptor descriptor = characteristic.getDescriptor(mConfigUUID);
        descriptor.setValue(enable ? BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE : new byte[]{0x00, 0x00});
        return bluetoothGatt.writeDescriptor(descriptor); //descriptor write operation successfully started?
    }

    /**
     * Check if phone support BLE
     */
    public boolean checkBleDevice(Context context) {
        if (mBluetoothAdapter != null) {
            if (!mBluetoothAdapter.isEnabled()) {
                @SuppressLint("MissingPermission") boolean enable = mBluetoothAdapter.enable();
                if (enable) {
                    Toast.makeText(context, "Successfully Open Bluetooth", Toast.LENGTH_SHORT).show();
                    return true;
                } else {
                    Toast.makeText(context, "Failed to open Bluetooth, please open Bluetooth from System setting", Toast.LENGTH_SHORT).show();
                    return false;
                }
            } else {
                return true;
            }
        } else {
            Toast.makeText(context, "Your phone doesn't support Bluetoooth", Toast.LENGTH_SHORT).show();
            return false;

        }
    }

    public static String bytesToHex(byte[] bytes) {
        char[] hexChars = new char[bytes.length * 2];
        char[] hexArray = "0123456789ABCDEF".toCharArray();
        for (int j = 0; j < bytes.length; j++) {
            int v = bytes[j] & 0xFF;
            hexChars[j * 2] = hexArray[v >>> 4];
            hexChars[j * 2 + 1] = hexArray[v & 0x0F];
        }
        return new String(hexChars);
    }


}