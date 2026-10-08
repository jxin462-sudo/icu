package com.example.icuapk;

import android.annotation.SuppressLint;
import android.Manifest;
import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothManager;
import android.bluetooth.le.BluetoothLeScanner;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanResult;
import android.bluetooth.le.ScanSettings;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;


import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

import androidx.annotation.NonNull;

@SuppressLint({"NewApi", "MissingPermission"})
@SuppressWarnings({"unused", "Java8ListSort"})
public class Am4100Manager {
    public interface Listener {
        void onStateChanged();
    }

    public static final UUID AM4100_SERVICE_UUID = UUID.fromString("49535343-FE7D-4AE5-8FA9-9FAFD205E455");
    public static final UUID AM4100_NOTIFY_UUID = UUID.fromString("49535343-1E4D-4BD9-BA61-23C647249616");
    public static final UUID AM4100_WRITE_UUID = UUID.fromString("49535343-8841-43F4-A8D4-ECBE34729BB3");
    private static final UUID CLIENT_CONFIG_UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb");
    private static final long SCAN_TIMEOUT_MS = 10000L;
    private static final long WAVE_UI_EMIT_INTERVAL_MS = 90L;
    private static final long WAVE_LOG_INTERVAL_MS = 1500L;
    private static final int MAX_LOG_COUNT = 80;
    private static final int MAX_WAVE_SAMPLES = 720;

    public static final class DeviceItem {
        public final String deviceId;
        public final String deviceName;
        public final int rssi;

        public DeviceItem(String deviceId, String deviceName, int rssi) {
            this.deviceId = deviceId;
            this.deviceName = deviceName;
            this.rssi = rssi;
        }
    }

    private final Context appContext;
    private final BluetoothAdapter bluetoothAdapter;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final ArrayList<DeviceItem> devices = new ArrayList<>();
    private final ArrayList<String> logs = new ArrayList<>();
    // ★ 修复 P1-7:frameBuffer 跨线程访问(BLE 回调线程 + 主线程),需加锁防崩溃
    private final ArrayList<Integer> frameBuffer = new ArrayList<>();
    private final Object frameBufferLock = new Object();
    private final ArrayList<Integer> ecgWaveSamples = new ArrayList<>();
    private final ArrayList<Integer> spo2WaveSamples = new ArrayList<>();
    private final ArrayList<Integer> respWaveSamples = new ArrayList<>();
    private final ArrayList<Integer> heartRateHistory = new ArrayList<>();
    private final ArrayList<Integer> spo2History = new ArrayList<>();
    private final ArrayList<Integer> pulseRateHistory = new ArrayList<>();
    private final ArrayList<Integer> bloodPressureHistory = new ArrayList<>();
    private final ArrayList<Integer> temperatureHistory = new ArrayList<>();
    private final ArrayList<Integer> respHistory = new ArrayList<>();
    private final Object waveLock = new Object();
    private Listener listener;
    private BluetoothLeScanner scanner;
    private ScanCallback scanCallback;
    private BluetoothGatt bluetoothGatt;
    private BluetoothGattCharacteristic notifyCharacteristic;
    private BluetoothGattCharacteristic writeCharacteristic;
    private boolean scanning;
    private boolean connected;
    private boolean protocolReady;
    private String stateText = "未连接";
    private String connectedDeviceName = "";
    private String connectedDeviceId = "";
    private String lastError = "";
    private String lastFrameSummary = "尚未收到 AM4100 数据";
    private String lastRawFrame = "";
    private int spo2 = -1;
    private int pulseRate = -1;
    private int heartRate = -1;
    private int sys = -1;
    private double map = -1.0;
    private int dia = -1;
    private int resp = -1;
    private String earTemp = "--";
    private String bodyTemp = "--";
    private String ambientTemp = "--";
    private String objectTemp = "--";
    private String statusOne = "--";
    private String statusTwo = "--";
    private String statusThree = "--";
    private boolean waveEmitPending;
    private long lastWaveEmitMs;
    private long lastWaveLogMs;

    private final Runnable scanTimeoutRunnable = new Runnable() {
        @Override
        public void run() {
            if (!scanning) {
                return;
            }
            stopScan();
            addLog("AM4100 扫描已自动停止");
            emit();
        }
    };
    private final Runnable waveEmitRunnable = new Runnable() {
        @Override
        public void run() {
            waveEmitPending = false;
            lastWaveEmitMs = System.currentTimeMillis();
            emit();
        }
    };

    public Am4100Manager(Context context) {
        appContext = context.getApplicationContext();
        BluetoothManager bluetoothManager = (BluetoothManager) appContext.getSystemService(Context.BLUETOOTH_SERVICE);
        bluetoothAdapter = bluetoothManager == null ? null : bluetoothManager.getAdapter();
    }

    public void setListener(Listener listener) {
        this.listener = listener;
    }

    public List<DeviceItem> getDevices() {
        synchronized (devices) {
            return new ArrayList<>(devices);
        }
    }

    public List<String> getLogs() {
        synchronized (logs) {
            return new ArrayList<>(logs);
        }
    }

    public boolean isScanning() {
        return scanning;
    }

    public boolean isConnected() {
        return connected;
    }

    public boolean isProtocolReady() {
        return protocolReady;
    }

    public String getStateText() {
        return stateText;
    }

    public String getConnectedDeviceName() {
        return connectedDeviceName;
    }

    public String getConnectedDeviceId() {
        return connectedDeviceId;
    }

    public String getLastError() {
        return lastError;
    }

    public String getLastFrameSummary() {
        return lastFrameSummary;
    }

    public String getLastRawFrame() {
        return lastRawFrame;
    }

    public String getHeartRateText() {
        return intText(heartRate);
    }

    public int getHeartRateValue() {
        return heartRate;
    }

    public String getPulseRateText() {
        return intText(pulseRate);
    }

    public int getPulseRateValue() {
        return pulseRate;
    }

    public String getSpo2Text() {
        return intText(spo2);
    }

    public int getSpo2Value() {
        return spo2;
    }

    public String getBloodPressureText() {
        if (sys < 0 || dia < 0) {
            return "--/--";
        }
        return sys + "/" + dia;
    }

    public String getMapText() {
        return map < 0 ? "--" : String.format(Locale.US, "%.0f", map);
    }

    public String getRespText() {
        return intText(resp);
    }

    public int getRespValue() {
        return resp;
    }

    public List<Integer> getEcgWaveSamples() {
        synchronized (waveLock) {
            return new ArrayList<>(ecgWaveSamples);
        }
    }

    public List<Integer> getSpo2WaveSamples() {
        synchronized (waveLock) {
            return new ArrayList<>(spo2WaveSamples);
        }
    }

    public List<Integer> getRespWaveSamples() {
        synchronized (waveLock) {
            return new ArrayList<>(respWaveSamples);
        }
    }

    public List<Integer> getHeartRateHistory() {
        synchronized (waveLock) {
            return new ArrayList<>(heartRateHistory);
        }
    }

    public List<Integer> getSpo2History() {
        synchronized (waveLock) {
            return new ArrayList<>(spo2History);
        }
    }

    public List<Integer> getPulseRateHistory() {
        synchronized (waveLock) {
            return new ArrayList<>(pulseRateHistory);
        }
    }

    public List<Integer> getBloodPressureHistory() {
        synchronized (waveLock) {
            return new ArrayList<>(bloodPressureHistory);
        }
    }

    public List<Integer> getTemperatureHistory() {
        synchronized (waveLock) {
            return new ArrayList<>(temperatureHistory);
        }
    }

    public List<Integer> getRespHistory() {
        synchronized (waveLock) {
            return new ArrayList<>(respHistory);
        }
    }

    public String getBodyTempText() {
        if (!"--".equals(bodyTemp)) {
            return bodyTemp;
        }
        if (!"--".equals(earTemp)) {
            return earTemp;
        }
        if (!"--".equals(objectTemp)) {
            return objectTemp;
        }
        return "--";
    }

    public String getEarTempText() {
        return earTemp;
    }

    public String getAmbientTempText() {
        return ambientTemp;
    }

    public String getObjectTempText() {
        return objectTemp;
    }

    public String getCalibrationStatusText() {
        if (!"--".equals(statusThree)) {
            return statusThree;
        }
        if (!"--".equals(statusTwo)) {
            return statusTwo;
        }
        return statusOne;
    }

    public boolean hasVitals() {
        return heartRate >= 0 || spo2 >= 0 || pulseRate >= 0 || sys >= 0 || resp >= 0
                || !"--".equals(bodyTemp) || !"--".equals(earTemp);
    }

    @SuppressLint("MissingPermission")
    public void requestEnableBluetooth(Activity activity) {
        if (bluetoothAdapter == null) {
            setError("设备不支持蓝牙");
            return;
        }
        if (missingBluetoothConnectPermission()) {
            setError("缺少蓝牙连接权限，请先申请权限");
            return;
        }
        try {
            if (bluetoothAdapter.isEnabled()) {
                addLog("Bluetooth already enabled");
                emit();
                return;
            }
            Intent intent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            activity.startActivity(intent);
            addLog("Request enable bluetooth");
        } catch (SecurityException exception) {
            setError("缺少蓝牙连接权限，请先申请权限");
        }
    }

    @SuppressLint("MissingPermission")
    public void startScan() {
        if (bluetoothAdapter == null) {
            setError("设备不支持蓝牙");
            return;
        }
        if (missingBluetoothScanPermission() || missingBluetoothConnectPermission()) {
            setError("缺少蓝牙扫描权限，请先申请权限");
            return;
        }
        if (!bluetoothAdapter.isEnabled()) {
            setError("蓝牙未开启");
            return;
        }
        scanner = bluetoothAdapter.getBluetoothLeScanner();
        if (scanner == null) {
            setError("BLE scanner is unavailable");
            return;
        }

        stopScan();
        devices.clear();
        scanning = true;
        if (!connected) {
            stateText = "扫描中";
        }

        if (scanCallback == null) {
            scanCallback = new ScanCallback() {
                @Override
                public void onScanResult(int callbackType, @NonNull ScanResult result) {
                    onDeviceFound(result);
                }

                @Override
                public void onBatchScanResults(@NonNull List<ScanResult> results) {
                    for (ScanResult result : results) {
                        onDeviceFound(result);
                    }
                }

                @Override
                public void onScanFailed(int errorCode) {
                    scanning = false;
                    setError("AM4100 scan failed: " + errorCode);
                }
            };
        }

        ScanSettings settings = new ScanSettings.Builder()
                .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
                .build();
        scanner.startScan(null, settings, scanCallback);
        lastError = "";
        addLog("扫描附近 BLE 设备");
        handler.removeCallbacks(scanTimeoutRunnable);
        handler.postDelayed(scanTimeoutRunnable, SCAN_TIMEOUT_MS);
        emit();
    }

    @SuppressLint("MissingPermission")
    public void stopScan() {
        handler.removeCallbacks(scanTimeoutRunnable);
        if (missingBluetoothScanPermission()) {
            scanning = false;
            emit();
            return;
        }
        if (scanner != null && scanCallback != null) {
            try {
                scanner.stopScan(scanCallback);
            } catch (Exception ignored) {
            }
        }
        scanning = false;
        if (!connected && "扫描中".equals(stateText)) {
            stateText = "未连接";
        }
        emit();
    }

    @SuppressLint("MissingPermission")
    public void connect(String address) {
        if (bluetoothAdapter == null) {
            setError("设备不支持蓝牙");
            return;
        }
        if (missingBluetoothConnectPermission()) {
            setError("缺少蓝牙连接权限，请先申请权限");
            return;
        }
        stopScan();
        disconnect();
        try {
            BluetoothDevice device = bluetoothAdapter.getRemoteDevice(address);
            connectedDeviceId = address;
            connectedDeviceName = resolveDeviceName(address, device);
            stateText = "连接中";
            lastError = "";
            clearVitals();
            bluetoothGatt = device.connectGatt(appContext, false, gattCallback);
            addLog("Connect AM4100 " + (TextUtils.isEmpty(connectedDeviceName) ? address : connectedDeviceName));
            emit();
        } catch (Exception exception) {
            setError("AM4100 connect failed: " + exception.getMessage());
        }
    }

    @SuppressLint("MissingPermission")
    public void disconnect() {
        stopScan();
        connected = false;
        protocolReady = false;
        notifyCharacteristic = null;
        writeCharacteristic = null;
        stateText = "未连接";
        synchronized (frameBufferLock) {
            frameBuffer.clear();
        }
        handler.removeCallbacks(waveEmitRunnable);
        waveEmitPending = false;
        if (bluetoothGatt != null) {
            try {
                if (!missingBluetoothConnectPermission()) {
                    bluetoothGatt.disconnect();
                }
            } catch (Exception ignored) {
            }
            try {
                if (!missingBluetoothConnectPermission()) {
                    bluetoothGatt.close();
                }
            } catch (Exception ignored) {
            }
            bluetoothGatt = null;
        }
        emit();
    }

    public void clearLogs() {
        logs.clear();
        emit();
    }

    public void setTemperatureMode() {
        writeHex(new byte[]{0x55, (byte) 0xAA, 0x04, 0x60, 0x01, (byte) 0x9A});
    }

    public void setEarTemperatureMode() {
        writeHex(new byte[]{0x55, (byte) 0xAA, 0x04, 0x60, 0x00, (byte) 0x9B});
    }

    public void startEarTemperatureCalibration() {
        writeHex(new byte[]{0x55, (byte) 0xAA, 0x04, 0x60, 0x10, (byte) 0x8B});
    }

    @SuppressLint("MissingPermission")
    public void writeHex(byte[] command) {
        if (missingBluetoothConnectPermission()) {
            setError("缺少蓝牙连接权限，请先申请权限");
            return;
        }
        if (!protocolReady || bluetoothGatt == null || writeCharacteristic == null) {
            setError("请先连接 AM4100 监护设备");
            return;
        }
        try {
            writeCharacteristic.setWriteType(BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE);
            writeCharacteristic.setValue(command);
            boolean started = bluetoothGatt.writeCharacteristic(writeCharacteristic);
            if (!started) {
                setError("AM4100 writeCharacteristic returned false");
                return;
            }
            lastError = "";
            addLog("AM4100 发送: " + toHex(command));
            emit();
        } catch (Exception exception) {
            setError("AM4100 write failed: " + exception.getMessage());
        }
    }

    private final BluetoothGattCallback gattCallback = new BluetoothGattCallback() {
        @SuppressLint("MissingPermission")
        @Override
        public void onConnectionStateChange(@NonNull BluetoothGatt gatt, int status, int newState) {
            if (newState == BluetoothGatt.STATE_CONNECTED) {
                connected = false;
                protocolReady = false;
                stateText = "发现服务中";
                addLog("AM4100 GATT connected");
                emit();
                try {
                    if (missingBluetoothConnectPermission()) {
                        setError("缺少蓝牙连接权限，请先申请权限");
                        return;
                    }
                    gatt.discoverServices();
                } catch (Exception exception) {
                    setError("AM4100 discover failed: " + exception.getMessage());
                }
                return;
            }
            if (newState == BluetoothGatt.STATE_CONNECTING) {
                stateText = "连接中";
                emit();
                return;
            }
            if (newState == BluetoothGatt.STATE_DISCONNECTING) {
                stateText = "断开中";
                emit();
                return;
            }
            connected = false;
            protocolReady = false;
            notifyCharacteristic = null;
            writeCharacteristic = null;
            stateText = "已断开";
            addLog("AM4100 GATT disconnected");
            if (bluetoothGatt == gatt) {
                try {
                    gatt.close();
                } catch (Exception ignored) {
                }
                bluetoothGatt = null;
            }
            emit();
        }

        @Override
        public void onServicesDiscovered(@NonNull BluetoothGatt gatt, int status) {
            if (status != BluetoothGatt.GATT_SUCCESS) {
                stateText = "发现服务失败";
                setError("AM4100 discover status: " + status);
                return;
            }
            BluetoothGattService service = gatt.getService(AM4100_SERVICE_UUID);
            if (service == null) {
                stateText = "协议不匹配";
                setError("未找到 AM4100 服务: " + AM4100_SERVICE_UUID);
                return;
            }
            notifyCharacteristic = service.getCharacteristic(AM4100_NOTIFY_UUID);
            writeCharacteristic = service.getCharacteristic(AM4100_WRITE_UUID);
            if (notifyCharacteristic == null || writeCharacteristic == null) {
                stateText = "协议不匹配";
                setError("未找到 AM4100 Notify/Write 特征");
                return;
            }
            enableNotify(gatt);
        }

        @Override
        public void onDescriptorWrite(@NonNull BluetoothGatt gatt, @NonNull BluetoothGattDescriptor descriptor, int status) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                markReady();
            } else {
                stateText = "Notify失败";
                setError("AM4100 Notify descriptor failed: " + status);
            }
        }

        @Override
        public void onCharacteristicChanged(@NonNull BluetoothGatt gatt, @NonNull BluetoothGattCharacteristic characteristic) {
            handleCharacteristicChanged(characteristic, characteristic.getValue());
        }

        @Override
        public void onCharacteristicChanged(@NonNull BluetoothGatt gatt, @NonNull BluetoothGattCharacteristic characteristic, @NonNull byte[] value) {
            handleCharacteristicChanged(characteristic, value);
        }
    };

    @SuppressLint("MissingPermission")
    private void enableNotify(BluetoothGatt gatt) {
        if (missingBluetoothConnectPermission()) {
            setError("缺少蓝牙连接权限，请先申请权限");
            return;
        }
        stateText = "启用Notify中";
        try {
            boolean enabled = gatt.setCharacteristicNotification(notifyCharacteristic, true);
            if (!enabled) {
                stateText = "Notify失败";
                setError("AM4100 本地 Notify 开关失败");
                return;
            }
            BluetoothGattDescriptor descriptor = notifyCharacteristic.getDescriptor(CLIENT_CONFIG_UUID);
            if (descriptor == null) {
                addLog("AM4100 无 2902 descriptor，仅启用本地通知");
                markReady();
                return;
            }
            int properties = notifyCharacteristic.getProperties();
            if ((properties & BluetoothGattCharacteristic.PROPERTY_INDICATE) != 0
                    && (properties & BluetoothGattCharacteristic.PROPERTY_NOTIFY) == 0) {
                descriptor.setValue(BluetoothGattDescriptor.ENABLE_INDICATION_VALUE);
            } else {
                descriptor.setValue(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE);
            }
            boolean started = gatt.writeDescriptor(descriptor);
            if (!started) {
                stateText = "Notify失败";
                setError("AM4100 descriptor write returned false");
            }
        } catch (Exception exception) {
            stateText = "Notify失败";
            setError("AM4100 Notify failed: " + exception.getMessage());
        }
    }

    private void markReady() {
        connected = true;
        protocolReady = true;
        stateText = "已连接";
        lastError = "";
        addLog("AM4100 协议通道已就绪");
        emit();
    }

    private void handleCharacteristicChanged(BluetoothGattCharacteristic characteristic, byte[] data) {
        if (characteristic == null || !AM4100_NOTIFY_UUID.equals(characteristic.getUuid()) || data == null || data.length == 0) {
            return;
        }
        lastError = "";
        boolean shouldEmitNow = parseFrames(data);
        long now = System.currentTimeMillis();
        if (shouldEmitNow || now - lastWaveLogMs >= WAVE_LOG_INTERVAL_MS) {
            lastRawFrame = toHex(data, 48);
            addLog("AM4100 收到 " + data.length + " bytes: " + lastRawFrame);
            lastWaveLogMs = now;
        }
        if (shouldEmitNow) {
            emit();
        } else {
            scheduleWaveEmit();
        }
    }

    private boolean parseFrames(byte[] data) {
        boolean shouldEmitNow = false;
        // ★ 修复 P1-7:整个解析过程都在锁内,防止 BLE 回调线程与主线程 clearVitals/disconnect 冲突
        synchronized (frameBufferLock) {
            for (byte item : data) {
                frameBuffer.add(item & 0xFF);
            }
            int index = 0;
            int validIndex = 0;
            int maxIndex = frameBuffer.size() - 7;
            while (index <= maxIndex) {
                if (frameBuffer.get(index) != 0x55 || frameBuffer.get(index + 1) != 0xAA) {
                    index++;
                    validIndex = index;
                    continue;
                }
                int length = frameBuffer.get(index + 2);
                int type = frameBuffer.get(index + 3);
                int dataCount = length - 3;
                int packetLength = length + 2;
                if (dataCount <= 0) {
                    index += 2;
                    validIndex = index;
                    continue;
                }
                if (index + packetLength > frameBuffer.size()) {
                    validIndex = index;
                    break;
                }
                int checksum = frameBuffer.get(index + 4 + dataCount) & 0xFF;
                int sum = 0;
                int[] payload = new int[dataCount];
                for (int i = 0; i < dataCount; i++) {
                    int value = frameBuffer.get(index + 4 + i) & 0xFF;
                    payload[i] = value;
                    sum += value;
                }
                int realSum = (~(length + type + sum)) & 0xFF;
                if (checksum != realSum) {
                    index += 2;
                    validIndex = index;
                    continue;
                }
                shouldEmitNow = readData(type, payload) || shouldEmitNow;
                index += packetLength;
                validIndex = index;
            }
            if (validIndex > 0 && validIndex <= frameBuffer.size()) {
                frameBuffer.subList(0, validIndex).clear();
            }
            if (frameBuffer.size() > 2048) {
                frameBuffer.clear();
            }
        }
        return shouldEmitNow;
    }

    private boolean readData(int type, int[] data) {
        if (data.length == 0) {
            return false;
        }
        switch (type) {
            case 0x02:
                readHeartResp(data);
                lastFrameSummary = "HR/RESP 心率 " + getHeartRateText() + " 呼吸 " + getRespText();
                return true;
            case 0x03:
                readNonInvasiveBloodPressure(data);
                lastFrameSummary = "无创血压 " + getBloodPressureText() + " 平均压 " + getMapText();
                return true;
            case 0x04:
                readSpo2Pulse(data);
                lastFrameSummary = "SpO2/PR 血氧 " + getSpo2Text() + "% 脉率 " + getPulseRateText();
                return true;
            case 0x05:
                readTemp(data);
                lastFrameSummary = "TEMP 体温 " + getBodyTempText();
                return true;
            case 0x06:
                readStatus(data);
                lastFrameSummary = "STATUS " + getCalibrationStatusText();
                return true;
            case 0x01:
                appendWaveSamples(ecgWaveSamples, data);
                lastFrameSummary = "ECG 波形数据";
                return false;
            case 0xFE:
                appendWaveSamples(spo2WaveSamples, data);
                lastFrameSummary = "SpO2 波形数据";
                return false;
            case 0xFF:
                appendWaveSamples(respWaveSamples, data);
                lastFrameSummary = "RESP 波形数据";
                return false;
            default:
                lastFrameSummary = "AM4100 frame type 0x" + Integer.toHexString(type).toUpperCase(Locale.US);
                return true;
        }
    }

    private void readHeartResp(int[] data) {
        int status = data[0] & 0x02;
        int nextHr = -1;
        int nextResp = -1;
        if (data.length == 5) {
            nextHr = status == 0 ? data[1] : -1;
            nextResp = status == 0 ? data[2] : -1;
        } else if (data.length == 6) {
            nextHr = status == 0 ? (data[5] << 8) | data[1] : -1;
            nextResp = status == 0 ? data[2] : -1;
        }
        heartRate = nextHr;
        resp = nextResp;
        recordTrendSample(heartRateHistory, heartRate);
        recordTrendSample(respHistory, resp);
    }

    private void appendWaveSamples(ArrayList<Integer> target, int[] data) {
        synchronized (waveLock) {
            for (int value : data) {
                target.add(value & 0xFF);
            }
            if (target.size() > MAX_WAVE_SAMPLES) {
                target.subList(0, target.size() - MAX_WAVE_SAMPLES).clear();
            }
        }
    }

    private void recordTrendSample(ArrayList<Integer> target, int value) {
        if (value < 0) {
            return;
        }
        synchronized (waveLock) {
            target.add(value);
            if (target.size() > MAX_WAVE_SAMPLES) {
                target.subList(0, target.size() - MAX_WAVE_SAMPLES).clear();
            }
        }
    }

    private void readNonInvasiveBloodPressure(int[] data) {
        if (data.length < 5) {
            return;
        }
        int status = (data[0] & (32 | 16 | 8 | 4)) >> 2;
        if (status == 0 || status == 7) {
            sys = data[2];
            map = data[3];
            dia = data[4];
            recordTrendSample(bloodPressureHistory, sys);
        } else {
            sys = -1;
            map = -1.0;
            dia = -1;
        }
    }

    private void readSpo2Pulse(int[] data) {
        if (data.length < 3) {
            return;
        }
        int nextSpo2 = data[0] == 0 ? data[1] : -1;
        if (nextSpo2 == 127) {
            nextSpo2 = -1;
        }
        int nextPr;
        if (data.length == 3) {
            nextPr = data[2] != 255 ? data[2] : -1;
        } else {
            nextPr = data[2] | (data[3] << 8);
            if (nextPr == 0xFF00) {
                nextPr = -1;
            }
        }
        spo2 = nextSpo2;
        pulseRate = nextPr;
        recordTrendSample(spo2History, spo2);
        recordTrendSample(pulseRateHistory, pulseRate);
    }

    private void readTemp(int[] data) {
        if (data.length < 3) {
            return;
        }
        if (data[0] == 0x40) {
            earTemp = data[1] + "." + data[2];
            recordTrendSample(temperatureHistory, data[1] * 10 + data[2]);
        } else if (data[0] == 0x00) {
            bodyTemp = data[1] + "." + data[2];
            recordTrendSample(temperatureHistory, data[1] * 10 + data[2]);
        }
    }

    private void readStatus(int[] data) {
        if (data.length < 1) {
            return;
        }
        if (data[0] == 0x40 && data.length >= 3) {
            ambientTemp = data[1] + "." + data[2];
        } else if (data[0] == 0x20 && data.length >= 3) {
            objectTemp = data[1] + "." + data[2];
        } else if (data[0] == 0xDD) {
            statusOne = "等待37℃校准";
        } else if (data[0] == 0xEE) {
            statusOne = "37℃校准完成";
            statusTwo = "等待42℃校准";
        } else if (data[0] == 0xFF) {
            statusTwo = "42℃校准完成";
            statusThree = "校准完成";
        }
    }

    private void clearVitals() {
        synchronized (frameBufferLock) {
            frameBuffer.clear();
        }
        synchronized (waveLock) {
            ecgWaveSamples.clear();
            spo2WaveSamples.clear();
            respWaveSamples.clear();
            heartRateHistory.clear();
            spo2History.clear();
            pulseRateHistory.clear();
            bloodPressureHistory.clear();
            temperatureHistory.clear();
            respHistory.clear();
        }
        spo2 = -1;
        pulseRate = -1;
        heartRate = -1;
        sys = -1;
        map = -1.0;
        dia = -1;
        resp = -1;
        earTemp = "--";
        bodyTemp = "--";
        ambientTemp = "--";
        objectTemp = "--";
        statusOne = "--";
        statusTwo = "--";
        statusThree = "--";
        lastFrameSummary = "尚未收到 AM4100 数据";
        lastRawFrame = "";
    }

    private void onDeviceFound(ScanResult result) {
        if (result == null || result.getDevice() == null) {
            return;
        }
        String name;
        String address;
        try {
            name = result.getDevice().getName();
            if (TextUtils.isEmpty(name) && result.getScanRecord() != null) {
                name = result.getScanRecord().getDeviceName();
            }
            address = result.getDevice().getAddress();
        } catch (SecurityException exception) {
            setError("缺少蓝牙扫描权限，请先申请权限");
            return;
        }
        if (TextUtils.isEmpty(address)) {
            return;
        }
        if (TextUtils.isEmpty(name)) {
            name = "未知设备 " + addressSuffix(address);
        }
        DeviceItem item = new DeviceItem(address, name, result.getRssi());
        synchronized (devices) {
            int index = findDeviceIndex(address);
            if (index >= 0) {
                devices.set(index, item);
            } else {
                devices.add(item);
            }
            Collections.sort(devices, (left, right) -> Integer.compare(right.rssi, left.rssi));
        }
        emit();
    }

    private int findDeviceIndex(String address) {
        for (int i = 0; i < devices.size(); i++) {
            if (address.equals(devices.get(i).deviceId)) {
                return i;
            }
        }
        return -1;
    }

    private String findDeviceName(String address) {
        synchronized (devices) {
            for (DeviceItem item : devices) {
                if (address.equals(item.deviceId)) {
                    return item.deviceName;
                }
            }
        }
        return "";
    }

    /**
     * ★ 0908：连接状态页“第一次显示名称、之后只剩物理地址”的修复（监护蓝牙）。
     * 与主机蓝牙同一口径：扫描结果 → 系统/已配对缓存名称 → 本地记住的名称。
     */
    @SuppressLint("MissingPermission")
    private String resolveDeviceName(String address, BluetoothDevice device) {
        String name = findDeviceName(address);
        if (!TextUtils.isEmpty(name)) {
            rememberDeviceName(address, name);
            return name;
        }
        if (device != null && !missingBluetoothConnectPermission()) {
            try {
                name = device.getName();
            } catch (Exception ignored) {
                name = "";
            }
            if (!TextUtils.isEmpty(name)) {
                rememberDeviceName(address, name);
                return name;
            }
        }
        return loadRememberedDeviceName(address);
    }

    private String deviceNameKey(String address) {
        return "monitor_device_name_" + address;
    }

    private void rememberDeviceName(String address, String name) {
        if (TextUtils.isEmpty(address) || TextUtils.isEmpty(name)) {
            return;
        }
        appContext.getSharedPreferences("icu_ble_settings", Context.MODE_PRIVATE)
                .edit()
                .putString(deviceNameKey(address), name)
                .apply();
    }

    private String loadRememberedDeviceName(String address) {
        if (TextUtils.isEmpty(address)) {
            return "";
        }
        return appContext.getSharedPreferences("icu_ble_settings", Context.MODE_PRIVATE)
                .getString(deviceNameKey(address), "");
    }

    private String addressSuffix(String address) {
        if (TextUtils.isEmpty(address) || address.length() < 5) {
            return "";
        }
        return address.substring(Math.max(0, address.length() - 5));
    }

    private boolean missingBluetoothScanPermission() {
        if (Build.VERSION.SDK_INT >= 31) {
            return permissionDenied(Manifest.permission.BLUETOOTH_SCAN);
        }
        return permissionDenied(Manifest.permission.ACCESS_FINE_LOCATION)
                && permissionDenied(Manifest.permission.ACCESS_COARSE_LOCATION);
    }

    private boolean missingBluetoothConnectPermission() {
        return Build.VERSION.SDK_INT >= 31
                && permissionDenied(Manifest.permission.BLUETOOTH_CONNECT);
    }

    private boolean permissionDenied(String permission) {
        return appContext.checkSelfPermission(permission) != PackageManager.PERMISSION_GRANTED;
    }

    private void addLog(String message) {
        String time = new SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(new Date());
        synchronized (logs) {
            logs.add(0, time + "  " + message);
            while (logs.size() > MAX_LOG_COUNT) {
                logs.remove(logs.size() - 1);
            }
        }
    }

    private void setError(String message) {
        lastError = message;
        addLog("ERROR " + message);
        emit();
    }

    private void emit() {
        if (listener != null) {
            listener.onStateChanged();
        }
    }

    private void scheduleWaveEmit() {
        long now = System.currentTimeMillis();
        long elapsed = now - lastWaveEmitMs;
        if (elapsed >= WAVE_UI_EMIT_INTERVAL_MS) {
            lastWaveEmitMs = now;
            emit();
            return;
        }
        if (!waveEmitPending) {
            waveEmitPending = true;
            handler.postDelayed(waveEmitRunnable, WAVE_UI_EMIT_INTERVAL_MS - elapsed);
        }
    }

    private String intText(int value) {
        return value < 0 ? "--" : String.valueOf(value);
    }

    private String toHex(byte[] data) {
        return toHex(data, data == null ? 0 : data.length);
    }

    private String toHex(byte[] data, int maxBytes) {
        if (data == null || data.length == 0) {
            return "";
        }
        StringBuilder builder = new StringBuilder();
        int count = Math.min(data.length, Math.max(0, maxBytes));
        for (int i = 0; i < count; i++) {
            byte item = data[i];
            if (builder.length() > 0) {
                builder.append(' ');
            }
            builder.append(String.format(Locale.US, "%02X", item & 0xFF));
        }
        if (data.length > count) {
            builder.append(" ...");
        }
        return builder.toString();
    }
}
