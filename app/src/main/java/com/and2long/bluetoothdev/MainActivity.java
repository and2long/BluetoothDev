package com.and2long.bluetoothdev;

import android.Manifest;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothProfile;
import android.content.DialogInterface;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.support.annotation.NonNull;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.DividerItemDecoration;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;

import com.zhy.adapter.recyclerview.MultiItemTypeAdapter;

import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

public class MainActivity extends AppCompatActivity {

    private static final String TAG = "MainActivity";

    private BluetoothAdapter mBluetoothAdapter;
    private ArrayList<BluetoothDevice> mData = new ArrayList<>();
    private MAdapter adapter;
    private static final int REQUEST_CODE_LOCATION = 100;
    private Handler mHandler = new Handler();
    final BluetoothAdapter.LeScanCallback callback = new BluetoothAdapter.LeScanCallback() {
        @Override
        public void onLeScan(final BluetoothDevice device, int rssi, byte[] scanRecord) {

            if (!mData.contains(device)) {
                String name = device.getName();
                String e = DateUtil.getCurrentDateFormat() + "\n" + name + " : " + device.getAddress();
                mData.add(device);
                log(e);
            }
            adapter.notifyDataSetChanged();

        }
    };
    private BluetoothGattCharacteristic characteristicRead;
    private BluetoothGattCharacteristic characteristicWrite;
    //    private UUID UUID_SERVER = UUID.fromString("8b624661-89ea-4ec9-97cc-eda0b52e96e6");
//    private UUID UUID_CHARREAD = UUID.fromString("8b624662-89ea-4ec9-97cc-eda0b52e96e6");
    private List<String> mLogData = new ArrayList<>();
    private LogAdapter logAdapter;
    private RecyclerView logList;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        EventBus.getDefault().register(this);

        initLogView();
        initDevideView();

        mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        //强制开启蓝牙，不予提示。
        if (!mBluetoothAdapter.isEnabled()) {
            mBluetoothAdapter.enable();
        }

    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        EventBus.getDefault().unregister(this);
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onMessageEvent(Event event) {
        switch (event.getType()) {
            case 0:
                logAdapter.notifyDataSetChanged();
                logList.scrollToPosition(logAdapter.getItemCount() == 0 ? 0 : logAdapter.getItemCount() - 1);
                break;
        }
    }

    private void initDevideView() {
        RecyclerView recyclerView = findViewById(R.id.recyclerView);
        adapter = new MAdapter(this, mData);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        recyclerView.setAdapter(adapter);
        recyclerView.addItemDecoration(new DividerItemDecoration(this, DividerItemDecoration.VERTICAL));
        adapter.setOnItemClickListener(new MultiItemTypeAdapter.OnItemClickListener() {
            @Override
            public void onItemClick(View view, RecyclerView.ViewHolder holder, int position) {
                stopScan();
                String e = DateUtil.getCurrentDateFormat() + "\n" + "尝试连接：" + mData.get(position).getName()
                        + "\n" + "*****稍等一会，等日志刷新后再次操作*****"
                        + "\n" + "####如果日志出现“error status：133”，再次点击设备尝试再次连接！####";
                log(e);
                //第二个参数为false时，尝试连接一次。
                mBluetoothGatt = mData.get(position).connectGatt(
                        MainActivity.this, false, mBluetoothGattCallback);
            }

            @Override
            public boolean onItemLongClick(View view, RecyclerView.ViewHolder holder, int position) {
                return false;
            }
        });
    }

    private void initLogView() {
        logList = findViewById(R.id.recyclerView1);
        logAdapter = new LogAdapter(this, mLogData);
        logList.setLayoutManager(new LinearLayoutManager(this));
        logList.setAdapter(logAdapter);
    }

    private void scan() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (ContextCompat.checkSelfPermission(MainActivity.this,
                    Manifest.permission.ACCESS_COARSE_LOCATION)
                    == PackageManager.PERMISSION_GRANTED) {
                scanLeDevice(true);
            } else {
                requestPermissions(new String[]{Manifest.permission.ACCESS_COARSE_LOCATION}, REQUEST_CODE_LOCATION);
            }
        } else {
            scanLeDevice(true);
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.i_scan:
                scan();
                return true;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_CODE_LOCATION) {
            if (grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                scanLeDevice(true);
            } else {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    if (shouldShowRequestPermissionRationale(Manifest.permission.ACCESS_COARSE_LOCATION)) {
                        new AlertDialog.Builder(this)
                                .setMessage(R.string.alert_permission_location)
                                .setPositiveButton(R.string.ok, new DialogInterface.OnClickListener() {
                                    @Override
                                    public void onClick(DialogInterface dialog, int which) {
                                        //申请定位权限
                                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                                            requestPermissions(
                                                    new String[]{Manifest.permission.ACCESS_COARSE_LOCATION},
                                                    REQUEST_CODE_LOCATION);
                                        }
                                    }
                                }).show();
                    }
                }
            }
        }
    }

    boolean mScanning = false;
    int SCAN_PERIOD = 10000;

    /**
     * 定时扫描
     *
     * @param enable
     */
    private void scanLeDevice(final boolean enable) {
        if (enable) {
            mData.clear();
            adapter.notifyDataSetChanged();
            setTitle(R.string.scanning);
            // Stops scanning after a pre-defined scan period.
            // 预先定义停止蓝牙扫描的时间（因为蓝牙扫描需要消耗较多的电量）
            mHandler.postDelayed(new Runnable() {
                @Override
                public void run() {
                    stopScan();
                }
            }, SCAN_PERIOD);
            mScanning = true;

            // 定义一个回调接口供扫描结束处理
            mBluetoothAdapter.startLeScan(callback);
            String e = DateUtil.getCurrentDateFormat() + "\n" + "开始扫描...";
            log(e);
        } else {
            mScanning = false;
            mBluetoothAdapter.stopLeScan(callback);
        }
    }

    private void stopScan() {
        if (mScanning) {
            mScanning = false;
            mBluetoothAdapter.stopLeScan(callback);
            setTitle("扫描结束");
            String e = DateUtil.getCurrentDateFormat() + "\n" + "扫描结束";
            log(e);
        }
    }

    public BluetoothGatt mBluetoothGatt;

    //    状态改变
    BluetoothGattCallback mBluetoothGattCallback = new BluetoothGattCallback() {
        @Override
        public void onConnectionStateChange(BluetoothGatt gatt, int status, int newState) {
            super.onConnectionStateChange(gatt, status, newState);
            log(DateUtil.getCurrentDateFormat() + "\n" + "onConnectionStateChange() called with: " +
                    "gatt = [" + gatt + "], status = [" + status + "], newState = [" + newState + "]");

            if (status != BluetoothGatt.GATT_SUCCESS) {
                String err = "Cannot connect device with error status: " + status;
                log(err);
                // 当尝试连接失败的时候调用 disconnect 方法是不会引起这个方法回调的，所以这里
                //   直接回调就可以了。
                gatt.close();
                return;
            }

            if (newState == BluetoothProfile.STATE_CONNECTED) {
                boolean b = mBluetoothGatt.discoverServices();
                int size = gatt.getServices().size();
//                setState(ConnectionState.STATE_CONNECTING);
                log("Connect--->success  " + "\n" +
                        "Attempting to start service discovery");
            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                log("Connect--->failed" + "\n" +
                        " Disconnected from GATT server");
                //                setState(ConnectionState.STATE_NONE);
            }
        }

        @Override
        public void onServicesDiscovered(BluetoothGatt gatt, int status) {
            String e1 = DateUtil.getCurrentDateFormat() + "\n" + "onServicesDiscovered() called with: " +
                    "gatt = [" + gatt + "], status = [" + status + "]";
            log(e1);
            if (status == BluetoothGatt.GATT_SUCCESS) {
//                setState(ConnectionState.STATE_CONNECTED);
                log("成功");
                initCharacteristic();
                try {
                    Thread.sleep(200);//延迟发送，否则第一次消息会不成功
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            } else {
                log("失败");
                //                setState(ConnectionState.STATE_NONE);
            }

        }

        @Override
        public void onCharacteristicWrite(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, int status) {
            super.onCharacteristicWrite(gatt, characteristic, status);
            String e = DateUtil.getCurrentDateFormat() + "\n" + "onCharacteristicWrite() called with: " +
                    "gatt = [" + gatt + "], characteristic = [" + characteristic + "], status = [" + status + "]";
            log(e);
        }

        @Override
        public void onDescriptorWrite(BluetoothGatt gatt, BluetoothGattDescriptor descriptor, int status) {
            super.onDescriptorWrite(gatt, descriptor, status);
            String e = DateUtil.getCurrentDateFormat() + "\n" + "onDescriptorWrite() called with: " +
                    "gatt = [" + gatt + "], descriptor = [" + descriptor + "], status = [" + status + "]";
            log(e);
        }

        @Override
        public void onDescriptorRead(BluetoothGatt gatt, BluetoothGattDescriptor descriptor, int status) {
            super.onDescriptorRead(gatt, descriptor, status);
            String e = DateUtil.getCurrentDateFormat() + "\n" + "onDescriptorRead() called with: " +
                    "gatt = [" + gatt + "], descriptor = [" + descriptor + "], status = [" + status + "]";
            log(e);
        }

        @Override
        public void onCharacteristicRead(BluetoothGatt gatt,
                                         BluetoothGattCharacteristic characteristic,
                                         int status) {
            String s = DateUtil.getCurrentDateFormat() + "\n" + "onCharacteristicRead() called with: " +
                    "gatt = [" + gatt + "], characteristic = [" + characteristic + "], status = [" + status + "]";
            log(s);
        }

        @Override
        public void onCharacteristicChanged(BluetoothGatt gatt,
                                            BluetoothGattCharacteristic characteristic) {
            String s = DateUtil.getCurrentDateFormat() + "\n" + "onCharacteristicChanged() " +
                    "called with: gatt = [" + gatt + "], characteristic = [" + characteristic + "]";
            log(s);
            readCharacteristic(characteristic);
        }

    };

    private void log(String msg) {
        mLogData.add(msg);
        EventBus.getDefault().post(new Event(0));
        writeFile(msg);
        Log.i(TAG, "log: " + msg);
    }

    public synchronized void initCharacteristic() {
        if (mBluetoothGatt == null)
            throw new NullPointerException();
        List<BluetoothGattService> services = mBluetoothGatt.getServices();
        //因为不知道对方的UUID，这里遍历把所有的服务全部注册
        log("------开始收集设备信息------");
        log(DateUtil.getCurrentDateFormat());
        for (int i = 0; i < services.size(); i++) {
            BluetoothGattService service = services.get(i);
            log("service" + i + ":" + String.valueOf(service.getUuid()));
            List<BluetoothGattCharacteristic> characteristics = service.getCharacteristics();
            if (characteristics != null) {
                for (int y = 0; y < characteristics.size(); y++) {
                    BluetoothGattCharacteristic characteristic = characteristics.get(y);
                    UUID uuid = characteristic.getUuid();
                    log("characteristic" + y + ":Uuid:" + String.valueOf(uuid));
                    log("characteristic" + y + ":Permissions:" + characteristic.getPermissions());
                    log("characteristic" + y + ":WriteType:" + characteristic.getWriteType());
                    log("characteristic" + y + ":Discriptors size::" + characteristic.getDescriptors().size());
                    List<BluetoothGattDescriptor> descriptors = characteristic.getDescriptors();
                    if (descriptors.size() > 0) {
                        for (int x = 0; x < descriptors.size(); x++) {
                            BluetoothGattDescriptor descriptor = descriptors.get(x);
                            log("descriptor" + x + ": Uuid:" + descriptor.getUuid());
                            log("descriptor" + x + ": Value:" + Arrays.toString(descriptor.getValue()));
                            log("descriptor" + x + ": Permissions:" + descriptor.getPermissions());
                        }
                    }
                }
            }
        }
        log("------收集设备信息结束------");

        //设置监听所有服务
        for (BluetoothGattService service : services) {
            List<BluetoothGattCharacteristic> characteristics = service.getCharacteristics();
            if (characteristics != null) {
                for (BluetoothGattCharacteristic characteristic : characteristics) {
                    String e = DateUtil.getCurrentDateFormat() + "\n" + "监听Service："
                            + String.valueOf(service.getUuid() + "\n" + "监听characteristic："
                            + String.valueOf(characteristic.getUuid()));
                    log(e);
                    mBluetoothGatt.setCharacteristicNotification(characteristic, true);
                }
            }
        }
    }

    public void readCharacteristic(BluetoothGattCharacteristic characteristic) {
        if (mBluetoothAdapter == null || mBluetoothGatt == null) {
            String msg = "BluetoothAdapter not initialized";
            log(msg);
            return;
        }
        mBluetoothGatt.readCharacteristic(characteristic);
        byte[] bytes = characteristic.getValue();
        String str = new String(bytes);
        String msg = "## --------读取到数据-------- " + "\n"
                + "String:" + str + "\n"
                + "byte[]:" + Arrays.toString(bytes);
        log(msg);
    }

    public void write(byte[] cmd) {
        Log.i(TAG, "write:" + new String(cmd));
        if (cmd.length == 0)
            return;
        //        synchronized (LOCK) {
        characteristicWrite.setValue(cmd);
        characteristicWrite.setWriteType(BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT);
        mBluetoothGatt.writeCharacteristic(characteristicWrite);
        Log.i(TAG, "write:--->" + new String(cmd));
        //        }
    }

    /**
     * 关闭
     */
    public void close() {
        if (mBluetoothGatt == null) {
            return;
        }
        mBluetoothGatt.close();
        mBluetoothGatt = null;
//        setState(ConnectionState.STATE_NONE);
    }

    private void writeFile(String msg) {
        FileWriter fw = null;
        File file = new File(getExternalCacheDir(), "device_data.txt");
        try {
            File dir = file.getParentFile();
            if (!dir.exists()) {
                dir.mkdirs();
            }
            if (!file.exists()) {
                file.createNewFile();
            }
            fw = new FileWriter(file, true);
            fw.append(msg + "\n");
            fw.flush();
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            try {
                if (fw != null)
                    fw.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

}
