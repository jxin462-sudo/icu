package com.example.icuapk;

import android.annotation.SuppressLint;
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
import android.bluetooth.le.ScanFilter;
import android.bluetooth.le.ScanResult;
import android.bluetooth.le.ScanSettings;
import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.os.ParcelUuid;

import androidx.annotation.NonNull;

import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.UUID;

/** Receives 32x24 thermal frames advertised by the Windows serial-to-BLE bridge. */
@SuppressLint("MissingPermission")
public final class ThermalBleManager {
    public interface Listener {
        void onThermalFrame(short[] centiDegrees, int width, int height, long timestampMs);

        void onThermalConnectionChanged(String state);

        void onThermalDevicesChanged(ArrayList<DeviceItem> devices);
    }

    public static final class DeviceItem {
        public final String address;
        public final String name;
        public final int rssi;

        DeviceItem(String address, String name, int rssi) {
            this.address = address;
            this.name = name;
            this.rssi = rssi;
        }
    }

    public static final UUID SERVICE_UUID = UUID.fromString("7f0d0001-9b2e-4ecb-a1c1-4c2ae9b50101");
    public static final UUID FRAME_CHARACTERISTIC_UUID = UUID.fromString("7f0d0002-9b2e-4ecb-a1c1-4c2ae9b50101");
    // 反向 ACK 通道：BLE 接收完一帧后写 ACK 给 PC 端，告知可以发下一帧
    public static final UUID ACK_SERVICE_UUID = UUID.fromString("7f0d0003-9b2e-4ecb-a1c1-4c2ae9b50101");
    public static final UUID ACK_CHARACTERISTIC_UUID = UUID.fromString("7f0d0004-9b2e-4ecb-a1c1-4c2ae9b50101");
    private static final UUID CLIENT_CONFIGURATION_UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb");
    private static final int HEADER_SIZE = 16;
    private static final int MAX_PACKET_COUNT = 16;
    private static final int MAX_FRAME_BYTES = 4096;
    private static final long RESCAN_DELAY_MS = 1500L;
    private static final int MAX_SERVICE_DISCOVERY_ATTEMPTS = 3;
    private static final int MAX_NOTIFY_ENABLE_ATTEMPTS = 3;
    private static final long SERVICE_DISCOVERY_TIMEOUT_MS = 2500L;
    private static final long NOTIFY_READY_FALLBACK_MS = 1800L;

    private final Context appContext;
    private final BluetoothAdapter bluetoothAdapter;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final ArrayList<DeviceItem> devices = new ArrayList<>();
    private BluetoothLeScanner scanner;
    private BluetoothGatt gatt;
    // ackCharacteristic 由 onServicesDiscovered（主线程）写入，被 consumePacket（binder 线程）
    // 通过 writeAck 读取，必须 volatile 保证跨线程可见性。
    private volatile BluetoothGattCharacteristic ackCharacteristic;
    private BluetoothGattCharacteristic notifyCharacteristic;
    private boolean scanning;
    private boolean started;
    private boolean discoveringServices;
    private boolean protocolReady;
    private int serviceDiscoveryAttempts;
    private int notifyEnableAttempts;
    private Listener listener;
    private String pendingConnectAddress = "";
    private int assemblingFrameId = -1;
    private int expectedPacketCount;
    private int frameWidth;
    private int frameHeight;
    private long frameTimestampMs;
    private byte[][] packetPayloads;
    private boolean[] receivedPackets;

    private final Runnable rescanRunnable = new Runnable() {
        @Override
        public void run() {
            if (started && gatt == null && !scanning) {
                beginScan();
            }
        }
    };

    private final Runnable mtuFallbackRunnable = new Runnable() {
        @Override
        public void run() {
            if (started && gatt != null && !discoveringServices) {
                beginServiceDiscovery(gatt);
            }
        }
    };

    private final Runnable subscribeTimeoutRunnable = new Runnable() {
        @Override
        public void run() {
            if (started && gatt != null && notifyCharacteristic != null && !protocolReady) {
                // 部分 Windows GATT Server 已接受 CCCD 写入却不回调 onDescriptorWrite。
                // 不能把这个 Android 回调缺失误报成电脑端无响应。
                markProtocolReady();
            }
        }
    };

    private final Runnable serviceDiscoveryTimeoutRunnable = new Runnable() {
        @Override
        public void run() {
            if (started && gatt != null && discoveringServices) {
                discoveringServices = false;
                beginServiceDiscovery(gatt);
            }
        }
    };

    // 兜底超时：connectGatt 调出去后 8 秒内 onConnectionStateChange 还没触发，
    // 说明连接卡死在 BLE 层（最常见：PC 端 winsdk 状态卡死）。
    // 此时主动 disconnect + close，UI 立刻报"连接超时，请重试"，
    // 用户再点一次就能连上——避免"点连接没反应"假死。
    private final Runnable connectTimeoutRunnable = new Runnable() {
        @Override
        public void run() {
            if (!started || gatt == null) {
                return;
            }
            BluetoothGatt stuck = gatt;
            gatt = null;
            try {
                stuck.disconnect();
                stuck.close();
            } catch (Exception ignored) {
            }
            resetAssembler();
            // 保留 pendingConnectAddress 让用户重试时不用重新选设备
            emitConnection("连接超时，请重试");
        }
    };

    private final Runnable connectRunnable = new Runnable() {
        @Override
        public void run() {
            if (!started || bluetoothAdapter == null || pendingConnectAddress.isEmpty() || gatt != null) {
                return;
            }
            try {
                BluetoothDevice device = bluetoothAdapter.getRemoteDevice(pendingConnectAddress);
                // 1500ms 延迟而不是 450ms：给 Android BLE 栈足够时间把上一次 gatt 的
                // 底层资源（pending connect、bonding 状态、descriptor 缓存）清理完，
                // 避免新旧 gatt 资源碰撞导致 connectGatt 静默失败。
                BluetoothGatt newGatt = device.connectGatt(appContext, false, gattCallback, BluetoothDevice.TRANSPORT_LE);
                if (newGatt == null) {
                    // connectGatt 偶发返回 null（资源碰撞或蓝牙栈饱和），必须显式提示
                    // ——否则用户永远卡在"正在连接..."无任何反馈
                    emitConnection("启动连接失败，请重试");
                    return;
                }
                gatt = newGatt;
                // 8 秒兜底：onConnectionStateChange 不触发就强制断开。
                // 成功路径（gattCallback onConnectionStateChange）会移除这个回调。
                handler.removeCallbacks(connectTimeoutRunnable);
                handler.postDelayed(connectTimeoutRunnable, 8000L);
            } catch (IllegalArgumentException error) {
                emitConnection("红外测温设备地址无效");
            }
        }
    };

    private final ScanCallback scanCallback = new ScanCallback() {
        @Override
        public void onScanResult(int callbackType, @NonNull ScanResult result) {
            if (!started || gatt != null || result.getDevice() == null) {
                return;
            }
            BluetoothDevice device = result.getDevice();
            updateDevice(device.getAddress(), device.getName(), result.getRssi());
        }

        @Override
        public void onScanFailed(int errorCode) {
            scanning = false;
            emitConnection("红外测温扫描失败: " + errorCode);
            scheduleRescan();
        }
    };

    private final BluetoothGattCallback gattCallback = new BluetoothGattCallback() {
        @Override
        public void onConnectionStateChange(BluetoothGatt connection, int status, int newState) {
            // 任何状态变化都取消 connect 超时——成功取消避免误触发，失败也取消（已主动关闭）
            handler.removeCallbacks(connectTimeoutRunnable);
            if (newState == BluetoothGatt.STATE_CONNECTED && status == BluetoothGatt.GATT_SUCCESS) {
                emitConnection("正在订阅红外测温数据...");
                serviceDiscoveryAttempts = 0;
                notifyEnableAttempts = 0;
                protocolReady = false;
                if (connection.requestMtu(517)) {
                    handler.postDelayed(mtuFallbackRunnable, 1200L);
                } else {
                    beginServiceDiscovery(connection);
                }
                return;
            }
            closeConnection(connection);
            emitConnection("红外测温桥已断开");
            scheduleRescan();
        }

        @Override
        public void onMtuChanged(BluetoothGatt connection, int mtu, int status) {
            if (status == BluetoothGatt.GATT_SUCCESS && connection == gatt) {
                beginServiceDiscovery(connection);
            }
        }

        @Override
        public void onServicesDiscovered(BluetoothGatt connection, int status) {
            discoveringServices = false;
            handler.removeCallbacks(mtuFallbackRunnable);
            handler.removeCallbacks(serviceDiscoveryTimeoutRunnable);
            if (status != BluetoothGatt.GATT_SUCCESS) {
                retryServiceDiscovery(connection, "红外测温服务发现失败");
                return;
            }
            BluetoothGattService service = connection.getService(SERVICE_UUID);
            BluetoothGattCharacteristic characteristic = service == null
                    ? null : service.getCharacteristic(FRAME_CHARACTERISTIC_UUID);
            if (characteristic == null || !connection.setCharacteristicNotification(characteristic, true)) {
                closeConnection(connection);
                emitConnection("红外测温 Notify 通道不可用");
                scheduleRescan();
                return;
            }
            notifyCharacteristic = characteristic;
            // 查找反向 ACK Write 特征（用于告诉 PC 端可以发下一帧）
            BluetoothGattService ackService = connection.getService(ACK_SERVICE_UUID);
            if (ackService != null) {
                ackCharacteristic = ackService.getCharacteristic(ACK_CHARACTERISTIC_UUID);
            }
            if (ackCharacteristic == null && service != null) {
                // 兼容：ACK 特征可能在同一 Notify 服务下
                ackCharacteristic = service.getCharacteristic(ACK_CHARACTERISTIC_UUID);
            }
            enableNotifyChannel(connection);
        }

        @Override
        public void onDescriptorWrite(BluetoothGatt connection, BluetoothGattDescriptor descriptor, int status) {
            if (!FRAME_CHARACTERISTIC_UUID.equals(descriptor.getCharacteristic().getUuid())) {
                return;
            }
            if (status == BluetoothGatt.GATT_SUCCESS) {
                markProtocolReady();
            } else {
                enableNotifyChannel(connection);
            }
        }

        @Override
        public void onCharacteristicChanged(BluetoothGatt connection, BluetoothGattCharacteristic characteristic) {
            if (FRAME_CHARACTERISTIC_UUID.equals(characteristic.getUuid())) {
                markProtocolReady();
                consumePacket(characteristic.getValue());
            }
        }

        @Override
        public void onCharacteristicChanged(BluetoothGatt connection, BluetoothGattCharacteristic characteristic,
                                            byte[] value) {
            if (FRAME_CHARACTERISTIC_UUID.equals(characteristic.getUuid())) {
                markProtocolReady();
                consumePacket(value);
            }
        }

        @Override
        public void onCharacteristicRead(BluetoothGatt connection, BluetoothGattCharacteristic characteristic,
                                         int status) {
            // ACK 特征 Read 回调。winsdk 自动返回 static_value（PC 端写入的当前 frame_id）。
            // 仅做诊断用：校验 PC 端发帧与 Android 端读取是否同步。
            if (ACK_CHARACTERISTIC_UUID.equals(characteristic.getUuid())
                    && status == BluetoothGatt.GATT_SUCCESS) {
                byte[] value = characteristic.getValue();
                if (value != null && value.length >= 4
                        && value[0] == 'A' && value[1] == 'C' && value[2] == 'K') {
                    int ackedFrameId = value[3] & 0xFF;
                    int currentFrameId = assemblingFrameId >= 0 ? assemblingFrameId : -1;
                    if (currentFrameId >= 0 && ackedFrameId != currentFrameId) {
                        android.util.Log.w("ThermalBle",
                                "ACK frame_id mismatch: PC=" + ackedFrameId
                                        + " Android=" + currentFrameId
                                        + " (PC 发帧过快或平板丢帧)");
                    }
                }
            }
        }
    };

    public ThermalBleManager(Context context) {
        appContext = context.getApplicationContext();
        BluetoothManager manager = (BluetoothManager) appContext.getSystemService(Context.BLUETOOTH_SERVICE);
        bluetoothAdapter = manager == null ? null : manager.getAdapter();
    }

    public void setListener(Listener value) {
        listener = value;
    }

    public void start() {
        started = true;
        if (bluetoothAdapter == null || !bluetoothAdapter.isEnabled()) {
            emitConnection("请开启蓝牙以连接红外测温桥");
            return;
        }
        if (gatt == null && !scanning) {
            devices.clear();
            emitDevices();
            beginScan();
        }
    }

    public void connect(String address) {
        if (bluetoothAdapter == null || !bluetoothAdapter.isEnabled() || address == null || address.isEmpty()) {
            emitConnection("无法连接红外测温桥");
            return;
        }
        stop();
        started = true;
        pendingConnectAddress = address;
        emitConnection("正在连接红外测温桥...");
        // 1500ms 而非 450ms：BLE 栈在 gatt.close() 之后仍可能有挂起操作，
        // 给够时间清完再发起新 connectGatt，避免与上一次的资源碰撞。
        handler.postDelayed(connectRunnable, 1500L);
    }

    public ArrayList<DeviceItem> getDevices() {
        return new ArrayList<>(devices);
    }

    public void stop() {
        started = false;
        pendingConnectAddress = "";
        handler.removeCallbacks(rescanRunnable);
        handler.removeCallbacks(mtuFallbackRunnable);
        handler.removeCallbacks(subscribeTimeoutRunnable);
        handler.removeCallbacks(serviceDiscoveryTimeoutRunnable);
        handler.removeCallbacks(connectTimeoutRunnable);
        handler.removeCallbacks(connectRunnable);
        stopScan();
        if (gatt != null) {
            try {
                gatt.disconnect();
                gatt.close();
            } catch (Exception ignored) {
            }
            gatt = null;
        }
        ackCharacteristic = null;
        notifyCharacteristic = null;
        protocolReady = false;
        resetAssembler();
    }

    private void beginScan() {
        if (!started || bluetoothAdapter == null || !bluetoothAdapter.isEnabled()) {
            return;
        }
        scanner = bluetoothAdapter.getBluetoothLeScanner();
        if (scanner == null) {
            emitConnection("当前设备不支持 BLE 扫描");
            return;
        }
        ArrayList<ScanFilter> filters = new ArrayList<>();
        filters.add(new ScanFilter.Builder().setServiceUuid(new ParcelUuid(SERVICE_UUID)).build());
        ScanSettings settings = new ScanSettings.Builder()
                .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
                .build();
        scanning = true;
        emitConnection("正在搜索红外测温桥...");
        scanner.startScan(filters, settings, scanCallback);
    }

    private void stopScan() {
        if (scanner != null && scanning) {
            try {
                scanner.stopScan(scanCallback);
            } catch (Exception ignored) {
            }
        }
        scanning = false;
    }

    private void scheduleRescan() {
        handler.removeCallbacks(rescanRunnable);
        if (started) {
            handler.postDelayed(rescanRunnable, RESCAN_DELAY_MS);
        }
    }

    private void updateDevice(String address, String name, int rssi) {
        for (int i = 0; i < devices.size(); i++) {
            if (devices.get(i).address.equals(address)) {
                devices.set(i, new DeviceItem(address, name, rssi));
                emitDevices();
                return;
            }
        }
        devices.add(new DeviceItem(address, name == null || name.trim().isEmpty() ? "红外测温桥" : name, rssi));
        emitDevices();
    }

    private void emitDevices() {
        final Listener target = listener;
        if (target != null) {
            final ArrayList<DeviceItem> snapshot = getDevices();
            handler.post(new Runnable() {
                @Override
                public void run() {
                    target.onThermalDevicesChanged(snapshot);
                }
            });
        }
    }

    private void closeConnection(BluetoothGatt connection) {
        handler.removeCallbacks(mtuFallbackRunnable);
        handler.removeCallbacks(subscribeTimeoutRunnable);
        handler.removeCallbacks(serviceDiscoveryTimeoutRunnable);
        handler.removeCallbacks(connectTimeoutRunnable);
        discoveringServices = false;
        if (connection != null) {
            try {
                connection.close();
            } catch (Exception ignored) {
            }
        }
        if (gatt == connection) {
            gatt = null;
        }
        ackCharacteristic = null;
        notifyCharacteristic = null;
        protocolReady = false;
        resetAssembler();
    }

    private void beginServiceDiscovery(BluetoothGatt connection) {
        if (connection == null || connection != gatt || discoveringServices) {
            return;
        }
        handler.removeCallbacks(mtuFallbackRunnable);
        if (serviceDiscoveryAttempts >= MAX_SERVICE_DISCOVERY_ATTEMPTS) {
            closeConnection(connection);
            emitConnection("红外测温服务发现多次失败，正在重新扫描");
            scheduleRescan();
            return;
        }
        discoveringServices = true;
        serviceDiscoveryAttempts++;
        if (!connection.discoverServices()) {
            discoveringServices = false;
            retryServiceDiscovery(connection, "红外测温服务发现未启动");
            return;
        }
        handler.removeCallbacks(serviceDiscoveryTimeoutRunnable);
        handler.postDelayed(serviceDiscoveryTimeoutRunnable, SERVICE_DISCOVERY_TIMEOUT_MS);
    }

    private void retryServiceDiscovery(final BluetoothGatt connection, String reason) {
        if (connection == null || connection != gatt) {
            return;
        }
        if (serviceDiscoveryAttempts >= MAX_SERVICE_DISCOVERY_ATTEMPTS) {
            closeConnection(connection);
            emitConnection(reason + "，正在重新扫描");
            scheduleRescan();
            return;
        }
        emitConnection(reason + "，正在重试");
        handler.postDelayed(new Runnable() {
            @Override
            public void run() {
                beginServiceDiscovery(connection);
            }
        }, 350L);
    }

    private void enableNotifyChannel(final BluetoothGatt connection) {
        if (connection == null || connection != gatt || notifyCharacteristic == null || protocolReady) {
            return;
        }
        if (notifyEnableAttempts >= MAX_NOTIFY_ENABLE_ATTEMPTS) {
            closeConnection(connection);
            emitConnection("红外测温通知开启多次失败，正在重新扫描");
            scheduleRescan();
            return;
        }
        BluetoothGattDescriptor descriptor = notifyCharacteristic.getDescriptor(CLIENT_CONFIGURATION_UUID);
        if (descriptor == null) {
            closeConnection(connection);
            emitConnection("红外测温通知描述符缺失");
            scheduleRescan();
            return;
        }
        notifyEnableAttempts++;
        descriptor.setValue(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE);
        if (connection.writeDescriptor(descriptor)) {
            handler.removeCallbacks(subscribeTimeoutRunnable);
            handler.postDelayed(subscribeTimeoutRunnable, NOTIFY_READY_FALLBACK_MS);
            return;
        }
        handler.postDelayed(new Runnable() {
            @Override
            public void run() {
                enableNotifyChannel(connection);
            }
        }, 350L);
    }

    private void markProtocolReady() {
        if (protocolReady) {
            return;
        }
        protocolReady = true;
        handler.removeCallbacks(subscribeTimeoutRunnable);
        emitConnection("红外热图已连接");
    }

    private void consumePacket(byte[] packet) {
        if (packet == null || packet.length < HEADER_SIZE || packet[0] != 'T' || packet[1] != 'H' || packet[2] != 1) {
            return;
        }
        int frameId = unsignedShort(packet, 4);
        int packetIndex = unsignedByte(packet[6]);
        int packetCount = unsignedByte(packet[7]);
        int payloadLength = unsignedShort(packet, 8);
        int width = unsignedByte(packet[10]);
        int height = unsignedByte(packet[11]);
        long timestamp = unsignedInt(packet, 12);
        if (packetCount < 1 || packetCount > MAX_PACKET_COUNT || packetIndex >= packetCount
                || payloadLength > packet.length - HEADER_SIZE || width < 1 || height < 1
                || width * height * 2 > MAX_FRAME_BYTES) {
            return;
        }
        if (frameId != assemblingFrameId || packetCount != expectedPacketCount
                || width != frameWidth || height != frameHeight) {
            beginFrame(frameId, packetCount, width, height, timestamp);
        }
        if (!receivedPackets[packetIndex]) {
            packetPayloads[packetIndex] = Arrays.copyOfRange(packet, HEADER_SIZE, HEADER_SIZE + payloadLength);
            receivedPackets[packetIndex] = true;
        }
        for (boolean received : receivedPackets) {
            if (!received) {
                return;
            }
        }
        ByteArrayOutputStream output = new ByteArrayOutputStream(frameWidth * frameHeight * 2);
        for (byte[] payload : packetPayloads) {
            output.write(payload, 0, payload.length);
        }
        byte[] frame = output.toByteArray();
        int expectedBytes = frameWidth * frameHeight * 2;
        if (frame.length == expectedBytes) {
            short[] centiDegrees = new short[frameWidth * frameHeight];
            for (int i = 0; i < centiDegrees.length; i++) {
                int offset = i * 2;
                centiDegrees[i] = (short) ((frame[offset] & 0xff) | ((frame[offset + 1] & 0xff) << 8));
            }
            // 帧重组完成：Read 反向 ACK（PC 端 static_value 自动返回当前 frame_id）
            readAck();
            Listener target = listener;
            if (target != null) {
                target.onThermalFrame(centiDegrees, frameWidth, frameHeight, frameTimestampMs);
            }
        }
        resetAssembler();
    }

    /**
     * Read 反向 ACK：BLE 接收完一帧后调用 gatt.readCharacteristic(ackCharacteristic)。
     * winsdk 会自动返回 PC 端写入的 static_value（当前 frame_id）。
     * 结果通过 onCharacteristicRead 回调接收（仅做诊断）。
     * 失败时静默（PC 端不依赖此 ACK，靠 FPS 节流保证稳定）。
     */
    @SuppressLint("MissingPermission")
    private void readAck() {
        if (ackCharacteristic == null || gatt == null) {
            return;
        }
        try {
            // 不关心返回值：失败时不影响主流程
            gatt.readCharacteristic(ackCharacteristic);
        } catch (Exception ignored) {
        }
    }

    private void beginFrame(int frameId, int packetCount, int width, int height, long timestamp) {
        assemblingFrameId = frameId;
        expectedPacketCount = packetCount;
        frameWidth = width;
        frameHeight = height;
        frameTimestampMs = timestamp;
        packetPayloads = new byte[packetCount][];
        receivedPackets = new boolean[packetCount];
    }

    private void resetAssembler() {
        assemblingFrameId = -1;
        expectedPacketCount = 0;
        frameWidth = 0;
        frameHeight = 0;
        packetPayloads = null;
        receivedPackets = null;
    }

    private void emitConnection(final String state) {
        final Listener target = listener;
        if (target != null) {
            handler.post(new Runnable() {
                @Override
                public void run() {
                    target.onThermalConnectionChanged(state);
                }
            });
        }
    }

    private static int unsignedByte(byte value) {
        return value & 0xff;
    }

    private static int unsignedShort(byte[] value, int offset) {
        return unsignedByte(value[offset]) | (unsignedByte(value[offset + 1]) << 8);
    }

    private static long unsignedInt(byte[] value, int offset) {
        return (long) unsignedByte(value[offset])
                | ((long) unsignedByte(value[offset + 1]) << 8)
                | ((long) unsignedByte(value[offset + 2]) << 16)
                | ((long) unsignedByte(value[offset + 3]) << 24);
    }
}
