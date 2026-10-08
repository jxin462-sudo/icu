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
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.text.TextUtils;


import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.ArrayDeque;
import android.util.ArrayMap;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.json.JSONException;
import org.json.JSONObject;

import androidx.annotation.NonNull;

@SuppressLint({"NewApi", "MissingPermission"})
@SuppressWarnings({"unused", "Java8ListSort"})
public class BleManager {
    public interface Listener {
        void onStateChanged();
    }

    public static final UUID ICU_SERVICE_UUID = UUID.fromString("0000a002-0000-1000-8000-00805f9b34fb");
    public static final UUID ICU_NOTIFY_UUID = UUID.fromString("0000c305-0000-1000-8000-00805f9b34fb");
    public static final UUID ICU_WRITE_UUID = UUID.fromString("0000c304-0000-1000-8000-00805f9b34fb");
    private static final UUID CLIENT_CONFIG_UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb");
    private static final String DEFAULT_ZONE = "right";
    private static final int ICU_MTU = 517;
    private static final int MAX_LOG_COUNT = 120;
    private static final long SCAN_TIMEOUT_MS = 8000L;
    // ★ 后台完整状态轮询 30s 一次（版本/全部开关等兜底）
    private static final long AUTO_UPDATE_MS = 30000L;
    // ★ 0904 问题2:温度/湿度/CO2(及左右舱氧)要持续刷新,不能等 30s。
    //   单独用 3s 轻量轮询只读环境数据,避免完整 readAllStatus 的高频负载。
    private static final long ENV_UPDATE_MS = 3000L;
    // ★ 定时控件(红外/蓝光/紫外/雾化/负离子)倒计时校时轮询。
    //   APP 本地倒计时只做秒级平滑显示,真正的剩余时间以主机回包为准,
    //   否则"下发瞬间起算"与"主机收到命令起算"的偏差会一直累积。
    //   只在至少有一个定时控件开启时才发,空闲时不占用 BLE。
    private static final long TIMED_SYNC_MS = 5000L;
    private static final long SERVICE_DISCOVERY_DELAY_MS = 350L;
    private static final long SERVICE_DISCOVERY_TIMEOUT_MS = 3500L;
    private static final long WRITE_CALLBACK_TIMEOUT_MS = 1200L;
    private static final long ENV_RESPONSE_TIMEOUT_MS = 2200L;
    private static final long ENV_LATE_RESPONSE_GUARD_MS = 350L;
    private static final long MTU_READY_TIMEOUT_MS = 1200L;
    private static final long NOTIFY_READY_TIMEOUT_MS = 2200L;
    private static final int MAX_SERVICE_DISCOVERY_ATTEMPTS = 3;
    private static final int MAX_NOTIFY_ENABLE_ATTEMPTS = 2;
    private static final int MAX_NOTIFY_BUFFER_CHARS = 32768;
    // ★ P1-15:意外掉线后自动重连(指数退避)。
    //   平板挂墙 + ICU 舱的场景下偶发掉线是常态,之前只把状态置成"已断开"就结束,
    //   每次都要人工重新扫描 + 点连接。
    //   退避序列 2s → 4s → 8s → 16s → 30s(封顶),最多 6 次,避免设备端异常时反复冲击。
    private static final long RECONNECT_BASE_DELAY_MS = 2000L;
    private static final long RECONNECT_MAX_DELAY_MS = 30000L;
    private static final int MAX_RECONNECT_ATTEMPTS = 6;
    // 定时控件直接开启时的本次运行值由 TimedControlProtocol 统一定义；
    // 用户在“设置”中保存的时长单独持久化，二者不得混用。
    //   紫外(12)/雾化(10)/负离子(11) = 120 分钟；
    //   红外(6)/蓝光(7) = 不限时,协议时长写哨兵值 65536。
    private static final int TIMED_MAX_MINUTES = TimedControlProtocol.MAX_MINUTES;

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
    private final ArrayList<DeviceItem> devices = new ArrayList<>();
    private final ArrayList<String> logs = new ArrayList<>();
    private final ArrayDeque<QueuedCommand> writeQueue = new ArrayDeque<>();
    private final StringBuilder notifyBuffer = new StringBuilder();
    private final Handler handler = new Handler(Looper.getMainLooper());
    // ★ P0-5:writeQueue / logs / devices 都被主线程(sendCommand/bleAction)与 BLE binder
    //   线程(onCharacteristicWrite→finishCurrentWrite、onScanResult)同时访问。
    //   ArrayDeque / ArrayList 都不是线程安全的,并发改动会破坏内部数组或抛 CME。
    //   与 Am4100Manager 里 frameBufferLock / waveLock 的做法保持一致。
    private final Object writeQueueLock = new Object();
    private final Object logsLock = new Object();
    private final Object devicesLock = new Object();
    private Listener listener;
    private BluetoothLeScanner scanner;
    private ScanCallback scanCallback;
    private volatile BluetoothGatt bluetoothGatt;
    private volatile BluetoothGattCharacteristic writeCharacteristic;
    private volatile BluetoothGattCharacteristic notifyCharacteristic;
    private volatile boolean scanning;
    private volatile boolean connected;
    private volatile boolean protocolReady;
    // ★ P0-5:writing 由 binder 线程(finishCurrentWrite)置 false,主线程(processWriteQueue)读。
    //   不加 volatile 时主线程可能永远看到 true,导致整个写队列永久卡死
    //   ——表现为"蓝牙显示已连接但点按钮完全没反应"。
    private volatile boolean writing;
    private volatile boolean autoUpdateEnabled;
    private volatile long ignoreWriteCallbacksUntilMs;
    private int serviceDiscoveryAttempts;
    private int notifyEnableAttempts;
    private String currentZone = DEFAULT_ZONE;
    private String connectionStateText = "未连接";
    private String connectedDeviceName = "";
    private String connectedDeviceId = "";
    private String lastSent = "";
    private String lastReceived = "";
    private String lastError = "";
    private String lastParsedStatus = "尚未收到响应";
    private static final class ZoneEnvironmentState {
        Float temperature;
        Float oxygen;
        Float humidity;
        Integer co2;
        long updatedAtMs;

        void clear() {
            temperature = null;
            oxygen = null;
            humidity = null;
            co2 = null;
            updatedAtMs = 0L;
        }
    }

    private static final class QueuedCommand {
        final String payload;
        final byte[] data;
        final String cmd;
        final String zone;
        final String dedupKey;
        final boolean environmentQuery;
        final long token;
        long sentAtMs;

        QueuedCommand(String payload, String cmd, String zone, long token) {
            this.payload = payload;
            this.data = payload.getBytes(StandardCharsets.UTF_8);
            this.cmd = cmd;
            this.zone = zone;
            this.dedupKey = EnvironmentProtocol.commandKey(cmd, zone);
            this.environmentQuery = EnvironmentProtocol.isEnvironmentResponseCommand(cmd);
            this.token = token;
        }
    }

    private static final class PendingEnvironmentRead {
        final String cmd;
        final String zone;
        final long token;
        final long generation;
        final long sentAtMs;
        final Runnable timeoutRunnable;

        PendingEnvironmentRead(String cmd, String zone, long token, long generation,
                               long sentAtMs, Runnable timeoutRunnable) {
            this.cmd = cmd;
            this.zone = zone;
            this.token = token;
            this.generation = generation;
            this.sentAtMs = sentAtMs;
            this.timeoutRunnable = timeoutRunnable;
        }
    }

    private final ZoneEnvironmentState leftEnvironment = new ZoneEnvironmentState();
    private final ZoneEnvironmentState rightEnvironment = new ZoneEnvironmentState();
    private final HostZoneState leftHostState = new HostZoneState("left");
    private final HostZoneState rightHostState = new HostZoneState("right");
    private final ArrayMap<String, PendingEnvironmentRead> pendingEnvironmentReads = new ArrayMap<>();
    private final ArrayMap<String, Long> environmentGuardUntil = new ArrayMap<>();
    private final Object environmentReadLock = new Object();
    private volatile QueuedCommand currentWrite;
    private long nextCommandToken;
    private long connectionGeneration;

    private Float lastSetCabinTemp;
    private Float lastSetHumidity;
    private Float lastSetOxygenLeft;
    private Float lastSetOxygenRight;
    private Float infraredTemp;
    // ★ 红外测温历史最大值（用户需求：显示最大值而非当前值）
    private float infraredTempMax = Float.NEGATIVE_INFINITY;
    // CO2 自动预警阀值：固定值，不可配置，与 CO2 目标浓度完全独立。
    // 实测值 > 该阀值时自动开启外循环换气。取值落在产品规定的 4000-6000 PPM 区间内。
    // 该功能常驻启用，无开关、无设置入口，因此不按舱区分、不持久化。
    private static final int CO2_AUTO_VENT_THRESHOLD = 5000;
    // 自动开启外循环的节流，避免同一次超阈反复下发 set_outer_cycle。
    private long lastCo2AutoVentAt = 0L;
    private static final long CO2_AUTO_VENT_INTERVAL_MS = 60000L;
    // CO2 目标浓度（set_co2 下发值）按舱保存，仅用于设置弹窗回填，不参与报警判定。
    private Integer lastSetCo2Left;
    private Integer lastSetCo2Right;
    private Integer lastSetCo2BeforeRollback;  // 设备拒绝 set_co2 时回滚目标值
    private long lastCo2AlarmAt = 0L;  // 上次报警时间,防止短时间内重复报警
    private long lastCo2RecoverAt = 0L;  // ★ P2-2:上次报警恢复时间,防止频繁"恢复"提示
    private boolean wasInAlarm = false;  // ★ P2-2:上一次是否在报警状态
    private static final long CO2_ALARM_INTERVAL_MS = 30000L;  // 30秒内不重复报警
    private static final long CO2_RECOVER_INTERVAL_MS = 60000L;  // ★ P2-2:60秒内不重复恢复提示
    private static final int CO2_VALUE_MIN = 0;
    private static final int CO2_VALUE_MAX = 100000;  // ★ P0-2:上限保护
    // ★ P2-1:报警历史记录,最多保存最近 50 条
    private final java.util.ArrayDeque<String> co2AlarmHistory = new java.util.ArrayDeque<>();
    private static final int CO2_ALARM_HISTORY_MAX = 50;
    private String statusLightColor = "";
    // ★ P1-2:通用 set_* 控制命令 pending 跟踪(覆盖全部14 项控制 + 监护等级)
    //   key = 命令名(如 "set_red_enable"),value = 期望值 + 超时定时器
    //   ArrayMap 非线程安全:主线程(recordSetPending)与 BLE binder 线程(verifyControlPending)
    //   会并发读写,所有访问必须持 pendingControlsLock。
    private final ArrayMap<String, PendingControlEntry> pendingControls = new ArrayMap<>();
    private final Object pendingControlsLock = new Object();
    private static final long CONTROL_CONFIRM_TIMEOUT_MS = 3000L;  // 3 秒未确认即主动 get 校验
    private String mainVersion = "";

    /**
     * ★ P1-2:单条 set_* 命令的 pending 跟踪条目。
     */
    private static class PendingControlEntry {
        final String cmdName;
        final String zone;
        final String expectedEnable;
        final String expectedValue;
        final Integer previousCo2Target;
        final Runnable timeoutRunnable;

        PendingControlEntry(String cmdName, String zone, String expectedEnable,
                            String expectedValue, Integer previousCo2Target, Runnable timeoutRunnable) {
            this.cmdName = cmdName;
            this.zone = zone;
            this.expectedEnable = expectedEnable;
            this.expectedValue = expectedValue;
            this.previousCo2Target = previousCo2Target;
            this.timeoutRunnable = timeoutRunnable;
        }
    }
    private String ctrlVersion = "";
    private String btVersion = "";
    private Integer treatmentMinutes;
    // ★ 治疗时长自动正计时（按舱隔离）：任一治疗项开启即"治疗中"。
    //   accumulated = 已完结段落的累计；runningSince = 本次连续治疗的起始墙钟时刻（0=未在计时）。
    //   切舱/重启用墙钟续算，不丢已走过的秒数。
    private long treatmentAccumulatedMs;
    private long treatmentRunningSinceMs;
    private boolean treatmentTickerScheduled;
    private Boolean tempEnabled;
    private Boolean o2Enabled;
    private Boolean coldLight;
    private Boolean warmLight;
    private Boolean redTherapy;
    private Boolean blueTherapy;
    private Boolean outerCycle;
    private Boolean innerCycle;
    private Boolean nebulizer;
    private Boolean anion;
    private Boolean uv;
    private Boolean co2Enabled;
    private Integer redTime;
    private Integer blueTime;
    private Integer nebulizerTime;
    private Integer anionTime;
    private Integer uvTime;
    private long redCountdownEndAtMs = 0L;
    private long blueCountdownEndAtMs = 0L;
    private long uvCountdownEndAtMs = 0L;
    private long nebulizerCountdownEndAtMs = 0L;
    private long anionCountdownEndAtMs = 0L;
    // ★ 不限时(65536)运行标记：这类控件没有倒计时终点，
    //   不能再用“终点时间是否在未来”来判断是否开启。
    private final java.util.HashSet<Integer> unlimitedRunning = new java.util.HashSet<>();
    // ★ 一次性运行时长(直接点开关下发)对应的 set_*_time 命令名。
    //   这类回包不能写回“用户配置时长”，否则默认 120/65536 会覆盖用户在“设置”里保存的值。
    private long treatmentCountdownEndAtMs = 0L;

    private final Runnable autoUpdateRunnable = new Runnable() {
        @Override
        public void run() {
            if (!autoUpdateEnabled) {
                return;
            }
            if (connected && protocolReady) {
                readAllStatus();
            }
            if (autoUpdateEnabled) {
                handler.postDelayed(this, AUTO_UPDATE_MS);
            }
        }
    };
    private final Runnable envUpdateRunnable = new Runnable() {
        @Override
        public void run() {
            if (!autoUpdateEnabled) {
                return;
            }
            if (connected && protocolReady) {
                readEnvironmentStatus();
            }
            if (autoUpdateEnabled) {
                handler.postDelayed(this, ENV_UPDATE_MS);
            }
        }
    };
    // ★ 定时控件倒计时校时：只在有控件开启时才向主机读一次状态，
    //   避免空闲时白白占用 BLE 带宽。
    private final Runnable timedSyncRunnable = new Runnable() {
        @Override
        public void run() {
            if (!autoUpdateEnabled) {
                return;
            }
            if (connected && protocolReady) {
                readTimedControlStatus();
            }
            if (autoUpdateEnabled) {
                handler.postDelayed(this, TIMED_SYNC_MS);
            }
        }
    };
    private final Runnable scanTimeoutRunnable = new Runnable() {
        @Override
        public void run() {
            if (!scanning) {
                return;
            }
            stopScan();
            addLog("扫描已自动停止");
            emit();
        }
    };
    private final Runnable statusRefreshRunnable = new Runnable() {
        @Override
        public void run() {
            if (connected && protocolReady) {
                readAllStatus();
            }
        }
    };
    private final Runnable serviceDiscoveryTimeoutRunnable = new Runnable() {
        @Override
        public void run() {
            if (protocolReady || connected || bluetoothGatt == null) {
                return;
            }
            if (serviceDiscoveryAttempts < MAX_SERVICE_DISCOVERY_ATTEMPTS) {
                addLog("发现服务超时，正在重试");
                startServiceDiscovery();
                return;
            }
            connectionStateText = "发现服务失败";
            setError("发现服务超时，请断开后重新连接，或确认设备支持 A002/C304/C305");
        }
    };
    private final Runnable mtuReadyTimeoutRunnable = new Runnable() {
        @Override
        public void run() {
            if (!protocolReady && bluetoothGatt != null && writeCharacteristic != null && notifyCharacteristic != null) {
                addLog("MTU 回调未返回，继续启用 Notify");
                enableNotifyChannel();
            }
        }
    };
    private final Runnable protocolReadyFallbackRunnable = new Runnable() {
        @Override
        public void run() {
            if (protocolReady || bluetoothGatt == null || writeCharacteristic == null || notifyCharacteristic == null) {
                return;
            }
            if (notifyEnableAttempts < MAX_NOTIFY_ENABLE_ATTEMPTS) {
                addLog("Notify descriptor 无回调，正在重试");
                enableNotifyChannel();
                return;
            }
            connectionStateText = "Notify失败";
            setError("Notify descriptor 写入无回调，请断开后重新连接");
        }
    };
    private final Runnable resumeWriteQueueRunnable = new Runnable() {
        @Override
        public void run() {
            processWriteQueue();
        }
    };
    private final Runnable writeTimeoutRunnable = new Runnable() {
        @Override
        public void run() {
            if (!writing) {
                return;
            }
            addLog("写入回调超时，继续发送下一条");
            ignoreWriteCallbacksUntilMs = SystemClock.uptimeMillis() + ENV_LATE_RESPONSE_GUARD_MS;
            finishCurrentWrite(false);
        }
    };
    // ★ P1-15:自动重连状态。userDisconnected 用来区分"用户主动断开"与"意外掉线",
    //   只有后者才自动重连。
    //   这三个字段由 BLE binder 线程(onConnectionStateChange→scheduleReconnect)与
    //   主线程(connect/disconnect)同时访问,需 volatile 保证可见性。
    private volatile boolean userDisconnected = true;
    private volatile int reconnectAttempts;
    private volatile String reconnectAddress = "";
    private final Runnable reconnectRunnable = new Runnable() {
        @Override
        public void run() {
            if (userDisconnected || connected || bluetoothGatt != null
                    || TextUtils.isEmpty(reconnectAddress)) {
                return;
            }
            if (bluetoothAdapter == null || !bluetoothAdapter.isEnabled()) {
                addLog("[P1-15] 蓝牙未开启，停止自动重连");
                return;
            }
            addLog("[P1-15] 自动重连 " + reconnectAttempts + "/" + MAX_RECONNECT_ATTEMPTS);
            connectionStateText = "自动重连中 " + reconnectAttempts + "/" + MAX_RECONNECT_ATTEMPTS;
            emit();
            attemptConnect(reconnectAddress, false);
        }
    };

    // ★ P1-2:statusLight 的独立定时器已合并到通用 recordSetPending / clearControlPending
    //   这里不再需要独立的 statusLightConfirmRunnable

    public BleManager(Context context) {
        appContext = context.getApplicationContext();
        android.content.SharedPreferences controlPreferences = appContext.getSharedPreferences("icu_ble_settings", Context.MODE_PRIVATE);
        lastSetCabinTemp = controlPreferences.contains("last_set_cabin_temp")
                ? controlPreferences.getFloat("last_set_cabin_temp", 0f) : null;
        lastSetHumidity = controlPreferences.contains("last_set_humidity")
                ? controlPreferences.getFloat("last_set_humidity", 0f) : null;
        lastSetOxygenLeft = controlPreferences.contains("last_set_oxygen_left")
                ? controlPreferences.getFloat("last_set_oxygen_left", 0f) : null;
        lastSetOxygenRight = controlPreferences.contains("last_set_oxygen_right")
                ? controlPreferences.getFloat("last_set_oxygen_right", 0f) : null;
        // 自动预警阀值现为固定常量（CO2_AUTO_VENT_THRESHOLD），不再读写偏好设置。
        // 旧版本残留的 co2_auto_vent_threshold_* 键不再使用，留在原地不影响行为。
        // CO2 目标浓度。旧版本把目标值存在 co2_alarm_threshold_* 里（那时目标与
        // 报警共用一个值），这里按其真实语义迁移为目标值，不迁移成报警阈值。
        lastSetCo2Left = controlPreferences.contains("last_set_co2_left")
                ? controlPreferences.getInt("last_set_co2_left", 0) : null;
        lastSetCo2Right = controlPreferences.contains("last_set_co2_right")
                ? controlPreferences.getInt("last_set_co2_right", 0) : null;
        int legacyLeft = controlPreferences.getInt("co2_alarm_threshold_left", -1);
        int legacyRight = controlPreferences.getInt("co2_alarm_threshold_right", -1);
        int legacyGlobal = controlPreferences.getInt("co2_alarm_threshold", -1);
        if (legacyLeft <= 0 && legacyGlobal > 0) {
            legacyLeft = legacyGlobal;
        }
        SharedPreferences.Editor migration = controlPreferences.edit();
        boolean migrated = false;
        if (lastSetCo2Left == null && legacyLeft > 0) {
            lastSetCo2Left = legacyLeft;
            migration.putInt("last_set_co2_left", legacyLeft);
            migrated = true;
        }
        if (lastSetCo2Right == null && legacyRight > 0) {
            lastSetCo2Right = legacyRight;
            migration.putInt("last_set_co2_right", legacyRight);
            migrated = true;
        }
        if (migrated) {
            migration.apply();
        }
        String savedZone = controlPreferences.getString("current_zone", DEFAULT_ZONE);
        currentZone = "left".equals(savedZone) ? "left" : DEFAULT_ZONE;
        migrateLegacyZoneTargets(controlPreferences);
        loadZoneProjection();
        BluetoothManager bluetoothManager = (BluetoothManager) appContext.getSystemService(Context.BLUETOOTH_SERVICE);
        bluetoothAdapter = bluetoothManager != null ? bluetoothManager.getAdapter() : null;
    }

    public void setListener(Listener listener) {
        this.listener = listener;
    }

    public List<DeviceItem> getDevices() {
        synchronized (devicesLock) {
            return new ArrayList<>(devices);
        }
    }

    public List<String> getLogs() {
        synchronized (logsLock) {
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

    public String getConnectionStateText() {
        return connectionStateText;
    }

    public String getConnectedDeviceName() {
        return connectedDeviceName;
    }

    public String getConnectedDeviceId() {
        return connectedDeviceId;
    }

    public String getLastSent() {
        return lastSent;
    }

    public String getLastReceived() {
        return lastReceived;
    }

    public String getLastError() {
        return lastError;
    }

    public String getLastParsedStatus() {
        return lastParsedStatus;
    }

    public String getCabinTempText() {
        return formatValue(getCurrentCabinTemp(), "℃");
    }

    public String getCabinTempText(String zone) {
        return formatValue(environmentForZone(zone).temperature, "℃");
    }

    public String getLastSetCabinTempText() {
        return formatValue(lastSetCabinTemp, "℃");
    }

    public String getOxygenText() {
        return formatValue(getCurrentOxygen(), "%");
    }

    public String getOxygenText(String zone) {
        return formatValue(environmentForZone(zone).oxygen, "%");
    }

    public String getLastSetOxygenText() {
        return formatValue(getLastSetOxygen(), "%");
    }

    public String getHumidityText() {
        return formatValue(getCurrentHumidity(), "%");
    }

    public String getHumidityText(String zone) {
        return formatValue(environmentForZone(zone).humidity, "%");
    }

    public Float getHumidityValue() {
        return getCurrentHumidity();
    }

    public Float getLastSetHumidityValue() {
        android.content.SharedPreferences prefs = appContext.getSharedPreferences(
                "icu_ble_settings", Context.MODE_PRIVATE);
        return prefs.contains("last_set_humidity")
                ? prefs.getFloat("last_set_humidity", 0f) : null;
    }

    public String getCo2Text() {
        Integer value = getCurrentCo2();
        return value == null ? "--" : value + "PPM";
    }

    public String getCo2Text(String zone) {
        Integer value = environmentForZone(zone).co2;
        return value == null ? "--" : value + "PPM";
    }

    public Integer getCo2Value() {
        return getCurrentCo2();
    }

    public long getEnvironmentUpdatedAt(String zone) {
        return environmentForZone(zone).updatedAtMs;
    }

    public String getInfraredTempText() {
        // ★ 用户需求：返回红外测温的最大值，而非当前瞬时值
        if (infraredTemp == null) {
            return "--";
        }
        float displayed = infraredTemp;
        if (infraredTempMax > Float.NEGATIVE_INFINITY && infraredTempMax > displayed) {
            displayed = infraredTempMax;
        }
        return formatValue(displayed, "℃");
    }

    /**
     * 设置红外体温值（同时记录历史最大值）。返回 true 表示 BLE 已连接并入队。
     * ★ 改为 boolean 返回值，跟 set_temp / set_o2 等其它 set_* 方法一致。
     */
    public boolean setInfraredTemp(float value) {
        if (!ensureReady()) {
            // 设备未连接也保存本地值，保留之前的行为
            this.infraredTemp = value;
            if (value > infraredTempMax) {
                infraredTempMax = value;
            }
            return false;
        }
        this.infraredTemp = value;
        if (value > infraredTempMax) {
            infraredTempMax = value;
        }
        // BLE 协议字段待定：先发个 payload，等设备协议确认后改 cmd/value 名
        sendCommand("{\"cmd\":\"set_infrared_temp\",\"value\":" + formatCommandNumber(value) + "}");
        recordSetPending("set_infrared_temp", null, formatCommandNumber(value));
        return true;
    }

    /**
     * 重置红外体温最大值（用于新测量周期）
     */
    public void resetInfraredTempMax() {
        this.infraredTempMax = Float.NEGATIVE_INFINITY;
    }

    /**
     * ★ 新加：返回当前红外体温值（Float），给补偿设置弹窗预填用。
     *   协议字段 set_infrared_temp 如设备端不支持，setInfraredTemp 会失败但本地值仍保留。
     */
    public Float getInfraredTempValue() {
        return infraredTemp;
    }

    /** 任一治疗项（恒温/氧气/雾化/蓝光/红光/负离子/紫外）开启即视为"治疗中"。 */
    private boolean isAnyTreatmentControlOn() {
        return Boolean.TRUE.equals(tempEnabled)
                || Boolean.TRUE.equals(o2Enabled)
                || Boolean.TRUE.equals(nebulizer)
                || Boolean.TRUE.equals(blueTherapy)
                || Boolean.TRUE.equals(redTherapy)
                || Boolean.TRUE.equals(anion)
                || Boolean.TRUE.equals(uv);
    }

    /** 治疗开关状态变化时结算/启动正计时：开→记起点；关→把这段耗时累进累计值并持久化。 */
    private void updateTreatmentAccumulator() {
        boolean on = isAnyTreatmentControlOn();
        long now = System.currentTimeMillis();
        if (on) {
            if (treatmentRunningSinceMs == 0L) {
                treatmentRunningSinceMs = now;
                saveZoneTreatmentRunningSinceMs(now);
            }
        } else if (treatmentRunningSinceMs != 0L) {
            treatmentAccumulatedMs += Math.max(0L, now - treatmentRunningSinceMs);
            treatmentRunningSinceMs = 0L;
            saveZoneTreatmentAccumulatedMs();
            saveZoneTreatmentRunningSinceMs(0L);
        }
    }

    private void ensureTreatmentTicker() {
        if (treatmentRunningSinceMs <= 0L || treatmentTickerScheduled) {
            return;
        }
        treatmentTickerScheduled = true;
        handler.postDelayed(new Runnable() {
            @Override
            public void run() {
                treatmentTickerScheduled = false;
                if (treatmentRunningSinceMs > 0L) {
                    emit();
                }
            }
        }, 1000L);
    }

    /** 当前舱累计治疗毫秒数（含正在进行的这一段）。 */
    public long getCurrentTreatmentElapsedMs() {
        long total = treatmentAccumulatedMs;
        if (treatmentRunningSinceMs > 0L) {
            total += Math.max(0L, System.currentTimeMillis() - treatmentRunningSinceMs);
        }
        return total;
    }

    public String getTreatmentTimeText() {
        long totalMs = getCurrentTreatmentElapsedMs();
        if (totalMs <= 0L) {
            return "--";
        }
        int totalMinutes = (int) (totalMs / 60000L);
        int hours = totalMinutes / 60;
        int minutes = totalMinutes % 60;
        return hours + "h" + minutes + "m";
    }

    public String getTreatmentTimeClockText() {
        long totalMs = getCurrentTreatmentElapsedMs();
        if (totalMs <= 0L) {
            return "--";
        }
        int totalSeconds = (int) (totalMs / 1000L);
        int hours = totalSeconds / 3600;
        int minutes = (totalSeconds % 3600) / 60;
        int seconds = totalSeconds % 60;
        return String.format(Locale.US, "%02d:%02d:%02d", hours, minutes, seconds);
    }

    public Integer getTreatmentMinutesValue() {
        return treatmentMinutes;
    }

    public String getStatusLightColor() {
        return statusLightColor;
    }

    public String getMainVersion() {
        return mainVersion;
    }

    public String getCtrlVersion() {
        return ctrlVersion;
    }

    public String getBtVersion() {
        return btVersion;
    }

    public Boolean getTemperatureEnabledValue() {
        return tempEnabled;
    }

    public Boolean getOxygenEnabledValue() {
        return o2Enabled;
    }

    // ★ P1-2:通用 set_* 控制命令 pending 跟踪辅助方法(覆盖全部 14 项)
    /**
     * 记录一条 set_* 命令的 pending 状态。
     * 同名 cmd 会取消旧 entry 并启动新定时器。
     *
     * @param cmdName       BLE 命令名(如 "set_red_enable")
     * @param expectedEnable 期望 enable 字段值("on"/"off"),可空
     * @param expectedValue  期望 value 字段值(如 "250"),可空
     */
    private String pendingControlKey(String cmdName, String zone) {
        return cmdName + ":" + normalizeZone(zone);
    }

    private PendingControlEntry findPendingControl(String cmdName, String responseZone) {
        synchronized (pendingControlsLock) {
            String zone = normalizeZone(responseZone);
            if (!TextUtils.isEmpty(zone)) {
                return pendingControls.get(pendingControlKey(cmdName, zone));
            }
            PendingControlEntry only = null;
            for (PendingControlEntry entry : pendingControls.values()) {
                if (!cmdName.equals(entry.cmdName)) {
                    continue;
                }
                if (only != null) {
                    return null;
                }
                only = entry;
            }
            return only;
        }
    }

    private void recordSetPending(String cmdName, String expectedEnable, String expectedValue) {
        recordSetPending(cmdName, currentZone, expectedEnable, expectedValue);
    }

    private void recordSetPending(String cmdName, String zone, String expectedEnable, String expectedValue) {
        final String normalizedZone = normalizeZone(zone);
        final String pendingKey = pendingControlKey(cmdName, normalizedZone);
        PendingControlEntry existing;
        synchronized (pendingControlsLock) {
            existing = pendingControls.remove(pendingKey);
        }
        if (existing != null && existing.timeoutRunnable != null) {
            handler.removeCallbacks(existing.timeoutRunnable);
        }
        final String finalCmdName = cmdName;
        Runnable timeout = new Runnable() {
            @Override
            public void run() {
                boolean stillPending;
                synchronized (pendingControlsLock) {
                    stillPending = pendingControls.containsKey(pendingKey);
                }
                if (!stillPending) {
                    return;
                }
                addLog("[P1-2] " + finalCmdName + "[" + normalizedZone + "] 3s 未收到响应,主动 get 校验");
                setError("设备 3 秒未确认 " + finalCmdName + "(" + normalizedZone + ")命令,可能未生效");
                handler.postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        boolean unconfirmed;
                        synchronized (pendingControlsLock) {
                            unconfirmed = pendingControls.containsKey(pendingKey);
                        }
                        if (unconfirmed) {
                            addLog("[P1-4] " + finalCmdName + "[" + normalizedZone + "] 主动校验仍无响应");
                            setError("设备长时间无响应,请检查连接");
                        }
                    }
                }, 2000L);

                String getCmd = mapSetToGetCmd(finalCmdName);
                if (getCmd != null && connected && protocolReady) {
                    sendCommand("{\"cmd\":\"" + getCmd + "\",\"zone\":\"" + normalizedZone + "\"}");
                }
            }
        };
        Integer previousCo2 = "set_co2".equals(cmdName)
                ? hostStateForZone(normalizedZone).previousTargetCo2 : null;
        PendingControlEntry entry = new PendingControlEntry(cmdName, normalizedZone,
                expectedEnable, expectedValue, previousCo2, timeout);
        synchronized (pendingControlsLock) {
            pendingControls.put(pendingKey, entry);
        }
        handler.postDelayed(timeout, CONTROL_CONFIRM_TIMEOUT_MS);
    }

    private void clearControlPending(String cmdName) {
        clearControlPending(cmdName, currentZone);
    }

    private void clearControlPending(String cmdName, String zone) {
        String pendingKey = pendingControlKey(cmdName, zone);
        PendingControlEntry existing;
        synchronized (pendingControlsLock) {
            existing = pendingControls.remove(pendingKey);
        }
        if (existing != null && existing.timeoutRunnable != null) {
            handler.removeCallbacks(existing.timeoutRunnable);
        }
    }

    private void verifyControlPending(String cmdName, org.json.JSONObject parsed) {
        String responseZone = normalizeZone(parsed.optString("zone", ""));
        PendingControlEntry pending = findPendingControl(cmdName, responseZone);
        if (pending == null) {
            return;
        }
        String respEnable = parsed.optString("enable", "");
        String respValue = parsed.optString("value", "");
        String respColor = parsed.optString("color", "");
        boolean mismatch = false;
        String detail = null;
        if (!TextUtils.isEmpty(pending.expectedEnable) && !TextUtils.isEmpty(respEnable)
                && !pending.expectedEnable.equalsIgnoreCase(respEnable)) {
            mismatch = true;
            detail = "期望=" + pending.expectedEnable + ",响应=" + respEnable;
        } else if (!TextUtils.isEmpty(pending.expectedValue) && !TextUtils.isEmpty(respValue)
                && !pending.expectedValue.equals(respValue)) {
            mismatch = true;
            detail = "期望=" + pending.expectedValue + ",响应=" + respValue;
        } else if (!TextUtils.isEmpty(pending.expectedValue) && !TextUtils.isEmpty(respColor)
                && !pending.expectedValue.equalsIgnoreCase(respColor)) {
            mismatch = true;
            detail = "期望=" + pending.expectedValue + ",响应=" + respColor;
        }
        if (mismatch) {
            addLog("[P1-2] " + cmdName + " 设备回执与期望不符," + detail);
            setError(cmdName + " 设备拒绝(" + detail + ")");
            // ★ P0-3:set_co2 设备拒绝时,回滚报警阈值
            if ("set_co2".equals(cmdName)) {
                HostZoneState state = hostStateForZone(pending.zone);
                state.targetCo2 = pending.previousCo2Target;
                if (pending.zone.equals(currentZone)) {
                    if ("left".equals(pending.zone)) {
                        lastSetCo2Left = pending.previousCo2Target;
                    } else {
                        lastSetCo2Right = pending.previousCo2Target;
                    }
                }
            }
        }
        clearControlPending(cmdName, pending.zone);
    }

    /**
     * set_* 命令名 → 对应的 get_* 命令名(用于 3s 超时主动校验)。
     */
    private String mapSetToGetCmd(String setCmd) {
        if (setCmd == null) {
            return null;
        }
        // 直接对应
        if (setCmd.equals("set_cold_light")) return "get_cold_light";
        if (setCmd.equals("set_warm_light")) return "get_warm_light";
        if (setCmd.equals("set_outer_cycle")) return "get_outer_cycle";
        if (setCmd.equals("set_inner_cycle")) return "get_inner_cycle";
        if (setCmd.equals("set_temp_enable")) return "get_temp";
        if (setCmd.equals("set_o2_enable")) return "get_o2";
        if (setCmd.equals("set_co2_enable")) return "get_co2";
        // 时间类与 enable 类共用 get_xxx
        if (setCmd.startsWith("set_red_")) return "get_red_light";
        if (setCmd.startsWith("set_blue_")) return "get_blue_light";
        if (setCmd.startsWith("set_uv_")) return "get_uv";
        if (setCmd.startsWith("set_nebulizer_")) return "get_nebulizer";
        if (setCmd.startsWith("set_anion_")) return "get_anion";
        // 数值类
        if (setCmd.equals("set_temp")) return "get_temp";
        if (setCmd.equals("set_o2")) return "get_o2";
        if (setCmd.equals("set_humidity")) return "get_humidity";
        if (setCmd.equals("set_co2")) return "get_co2";
        // 监护等级
        if (setCmd.equals("status_light") || setCmd.equals("set_status_light")) return "get_status_light";
        return null;
    }

    /**
     * 公开方法:获取当前是否还有 pending 的控制命令(给 JS / UI 用)。
     */
    public boolean hasPendingControl(String cmdName) {
        synchronized (pendingControlsLock) {
            return pendingControls.containsKey(pendingControlKey(cmdName, currentZone));
        }
    }

    /**
     * 公开方法:获取所有 pending 命令名(给 JS 端批量查询用)。
     */
    public String getPendingControlNames() {
        StringBuilder sb = new StringBuilder();
        synchronized (pendingControlsLock) {
            for (int i = 0; i < pendingControls.size(); i += 1) {
                PendingControlEntry entry = pendingControls.valueAt(i);
                if (!entry.zone.equals(currentZone)) {
                    continue;
                }
                if (sb.length() > 0) sb.append(",");
                sb.append(entry.cmdName);
            }
        }
        return sb.toString();
    }

    /**
     * ★ P1-2:获取 status_light 的 pending 期望值(给 UI 显示"确认中..."用)。
     */
    public String getPendingStatusLightColor() {
        PendingControlEntry entry;
        synchronized (pendingControlsLock) {
            entry = pendingControls.get(pendingControlKey("status_light", currentZone));
        }
        return (entry != null) ? entry.expectedValue : "";
    }

    /**
     * ★ P1-2:获取某个控制命令的 pending 期望值(给 UI 显示"确认中..."用)。
     * 开关类返回 "on"/"off",数值类返回字符串数字。
     */
    public String getPendingControlExpected(String cmdName) {
        PendingControlEntry entry;
        synchronized (pendingControlsLock) {
            entry = pendingControls.get(pendingControlKey(cmdName, currentZone));
        }
        if (entry == null) return "";
        if (!TextUtils.isEmpty(entry.expectedEnable)) return entry.expectedEnable;
        return entry.expectedValue;
    }

    public boolean setStatusLightColor(String color) {
        String next = normalizeStatusLightColor(color);
        if (TextUtils.isEmpty(next) || !ensureReady()) {
            return false;
        }
        sendCommand("{\"cmd\":\"status_light\",\"zone\":\"" + currentZone + "\",\"color\":\"" + next + "\"}");
        statusLightColor = next;
        // ★ P1-2:用通用 pending 机制记录(与 12 项控制统一)
        recordSetPending("status_light", null, next);
        scheduleStatusRefresh();
        emit();
        return true;
    }

    public Float getCabinTempValue() {
        return getCurrentCabinTemp();
    }

    /**
     * ★ 修复 P1-3:获取用户上次设置的目标温度(用于设置对话框显示默认值)。
     * 区别于 getCabinTempValue()(设备实测值,加热过程中会偏低)。
     */
    public Float getLastSetCabinTempValue() {
        return lastSetCabinTemp;
    }

    public Float getOxygenValue() {
        return getCurrentOxygen();
    }

    /**
     * ★ 修复 P1-3:获取用户上次设置的氧浓度目标值(按当前 zone 区分)。
     */
    public Float getLastSetOxygenValue() {
        return getLastSetOxygen();
    }

    public Integer getControlTimeValue(int index) {
        switch (index) {
            case 6:
                return redTime;
            case 7:
                return blueTime;
            case 10:
                return nebulizerTime;
            case 11:
                return anionTime;
            case 12:
                return uvTime;
            default:
                return null;
        }
    }

    public String getControlValue(int index) {
        switch (index) {
            case 0:
                return getCabinTempText();
            case 1:
                return getOxygenText();
            case 2:
                return formatStatusLight();
            case 3:
                return getCo2Text();
            case 4:
                return formatSwitch(coldLight);
            case 5:
                return formatSwitch(warmLight);
            case 6:
                return formatTimedValue(redTime);
            case 7:
                return formatTimedValue(blueTime);
            case 8:
                return formatSwitch(outerCycle);
            case 9:
                return formatSwitch(innerCycle);
            case 10:
                return formatTimedValue(nebulizerTime);
            case 11:
                return formatTimedValue(anionTime);
            case 12:
                return formatTimedValue(uvTime);
            case 13:
                return formatSwitch(o2Enabled);
            case 14:
                return getHumidityText();
            case 15:
                return getTreatmentTimeClockText();
            default:
                return "-";
        }
    }

    public boolean isControlOn(int index) {
        switch (index) {
            case 3:
                return Boolean.TRUE.equals(co2Enabled);
            case 4:
                return Boolean.TRUE.equals(coldLight);
            case 5:
                return Boolean.TRUE.equals(warmLight);
            case 6:
                return Boolean.TRUE.equals(redTherapy);
            case 7:
                return Boolean.TRUE.equals(blueTherapy);
            case 8:
                return Boolean.TRUE.equals(outerCycle);
            case 9:
                return Boolean.TRUE.equals(innerCycle);
            case 10:
                return Boolean.TRUE.equals(nebulizer);
            case 11:
                return Boolean.TRUE.equals(anion);
            case 12:
                return Boolean.TRUE.equals(uv);
            case 13:
                return Boolean.TRUE.equals(o2Enabled);
            default:
                return false;
        }
    }

    public String getOtherTreatmentSummary() {
        return "红外理疗（" + formatTimedShort(redTherapy, redTime) + "）、雾化器（" + formatSwitch(nebulizer) + "）、蓝光理疗（" + formatTimedShort(blueTherapy, blueTime) + "）、";
    }

    public String getOtherTreatmentSummaryLine2() {
        return "暖光照明（" + formatSwitch(warmLight) + "）、内循环（" + formatSwitch(innerCycle) + "）";
    }

    public String getCurrentZone() {
        return currentZone;
    }

    private HostZoneState hostStateForZone(String zone) {
        return "left".equals(normalizeZone(zone)) ? leftHostState : rightHostState;
    }

    private HostZoneState currentHostState() {
        return hostStateForZone(currentZone);
    }

    private void saveCurrentZoneProjection() {
        HostZoneState state = currentHostState();
        state.initialized = true;
        state.targetTemperature = lastSetCabinTemp;
        state.targetOxygen = getLastSetOxygen();
        state.targetHumidity = lastSetHumidity;
        state.targetCo2 = getCurrentLastSetCo2();
        state.previousTargetCo2 = lastSetCo2BeforeRollback;
        state.statusLightColor = statusLightColor;
        state.treatmentMinutes = treatmentMinutes;
        state.treatmentAccumulatedMs = treatmentAccumulatedMs;
        state.treatmentRunningSinceMs = treatmentRunningSinceMs;
        state.tempEnabled = tempEnabled;
        state.o2Enabled = o2Enabled;
        state.coldLight = coldLight;
        state.warmLight = warmLight;
        state.redTherapy = redTherapy;
        state.blueTherapy = blueTherapy;
        state.outerCycle = outerCycle;
        state.innerCycle = innerCycle;
        state.nebulizer = nebulizer;
        state.anion = anion;
        state.uv = uv;
        state.co2Enabled = co2Enabled;
        saveCurrentZoneTimingProjection(state);
        saveCurrentZoneAlarmProjection(state);
    }

    public void setCurrentZone(String zone) {
        String normalized = normalizeZone(zone);
        if (TextUtils.isEmpty(normalized)) {
            return;
        }
        if (normalized.equals(currentZone)) {
            enqueueEnvironmentRead(currentZone);
            return;
        }
        saveCurrentZoneProjection();
        currentZone = normalized;
        loadZoneProjection();
        appContext.getSharedPreferences("icu_ble_settings", Context.MODE_PRIVATE)
                .edit()
                .putString("current_zone", currentZone)
                .apply();
        // 先通知 UI 切换到目标舱缓存（无缓存则显示 --），再优先补读完整环境状态。
        emit();
        enqueueEnvironmentRead(currentZone);
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
                addLog("Bluetooth is ready");
                lastError = "";
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
            setError("Bluetooth disabled");
            return;
        }

        scanner = bluetoothAdapter.getBluetoothLeScanner();
        if (scanner == null) {
            setError("BLE scanner is unavailable");
            return;
        }

        stopScan();
        synchronized (devicesLock) {
            devices.clear();
        }
        scanning = true;
        if (!connected) {
            connectionStateText = "扫描中";
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
                    setError("Scan failed: " + errorCode);
                }
            };
        }

        ScanSettings settings = new ScanSettings.Builder()
                .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
                .build();
        scanner.startScan(null, settings, scanCallback);
        lastError = "";
        addLog("扫描有名称的 BLE 设备");
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
        if (scanning) {
            addLog("Stop BLE scan");
        }
        scanning = false;
        if (!connected) {
            connectionStateText = "未连接";
        }
        emit();
    }

    @SuppressLint("MissingPermission")
    public void connect(String address) {
        // ★ P1-15:用户主动连接 → 记住地址并允许后续自动重连,重置退避计数
        reconnectAddress = address;
        reconnectAttempts = 0;
        attemptConnect(address, true);
    }

    /**
     * ★ P1-15:实际发起 GATT 连接。
     *
     * @param fromUser true = 用户主动点连接(会先 disconnect 清场);
     *                 false = 自动重连(掉线时已清场,不再重复 disconnect,
     *                 避免把 reconnectAddress 和退避计数清掉)。
     */
    @SuppressLint("MissingPermission")
    private void attemptConnect(String address, boolean fromUser) {
        if (bluetoothAdapter == null) {
            setError("设备不支持蓝牙");
            return;
        }
        if (missingBluetoothConnectPermission()) {
            setError("缺少蓝牙连接权限，请先申请权限");
            return;
        }
        stopScan();
        if (fromUser) {
            disconnect();
        }
        // 进入连接流程即视为"期望保持连接",后续掉线应自动重连
        userDisconnected = false;
        try {
            BluetoothDevice device = bluetoothAdapter.getRemoteDevice(address);
            connectedDeviceId = address;
            connectedDeviceName = resolveDeviceName(address, device);
            connectionStateText = "连接中";
            lastError = "";
            serviceDiscoveryAttempts = 0;
            notifyEnableAttempts = 0;
            addLog("C304=Write, C305=Notify");
            bluetoothGatt = device.connectGatt(appContext, false, gattCallback);
            if (bluetoothGatt == null) {
                // connectGatt 偶发返回 null(资源碰撞/蓝牙栈饱和),必须显式提示并安排重连,
                // 否则用户永远卡在"连接中"。
                setError("启动连接失败，请重试");
                scheduleReconnect();
                return;
            }
            addLog("Connect " + (TextUtils.isEmpty(connectedDeviceName) ? address : connectedDeviceName));
            emit();
        } catch (Exception exception) {
            setError("Connect failed: " + exception.getMessage());
            scheduleReconnect();
        }
    }

    /**
     * ★ P1-15:安排一次带指数退避的自动重连。
     * 用户主动断开、已连接、或超过最大次数时不再重连。
     */
    private void scheduleReconnect() {
        handler.removeCallbacks(reconnectRunnable);
        if (userDisconnected || connected || TextUtils.isEmpty(reconnectAddress)) {
            return;
        }
        if (reconnectAttempts >= MAX_RECONNECT_ATTEMPTS) {
            connectionStateText = "已断开";
            setError("自动重连 " + MAX_RECONNECT_ATTEMPTS + " 次仍失败，请手动重新连接");
            return;
        }
        reconnectAttempts++;
        long delay = Math.min(RECONNECT_MAX_DELAY_MS,
                RECONNECT_BASE_DELAY_MS * (1L << (reconnectAttempts - 1)));
        addLog("[P1-15] " + (delay / 1000L) + " 秒后自动重连(第 " + reconnectAttempts + " 次)");
        handler.postDelayed(reconnectRunnable, delay);
    }

    @SuppressLint("MissingPermission")
    public void disconnect() {
        // ★ P1-15:用户主动断开 → 禁止自动重连并取消已排队的重连
        userDisconnected = true;
        reconnectAttempts = 0;
        handler.removeCallbacks(reconnectRunnable);
        stopAutoUpdate();
        writeCharacteristic = null;
        notifyCharacteristic = null;
        connected = false;
        protocolReady = false;
        serviceDiscoveryAttempts = 0;
        notifyEnableAttempts = 0;
        int droppedCommands = resetTransportState(true);
        // ★ P1-13:排队中的控制命令被丢弃时必须告知用户,
        //   否则用户以为指令已下发,实际设备从未收到。
        if (droppedCommands > 0) {
            addLog("[P1-13] 断开时丢弃了 " + droppedCommands + " 条未发送的命令");
        }
        // ★ P1-14:倒计时清零必须走 setCountdownField(),它会同步写 SharedPreferences。
        //   之前直接赋值绕过持久化,导致下次启动 loadZoneTimedControlState() 把旧的
        //   endAt 读回来,UI 复活出"紫外消毒还剩 XX 分钟"的假状态。
        final int[] timedIndices = {6, 7, 10, 11, 12};
        for (final String zone : new String[]{"left", "right"}) {
            runWithZoneProjection(zone, new Runnable() {
                @Override
                public void run() {
                    for (int index : timedIndices) {
                        clearCountdown(index);
                    }
                }
            });
        }
        cancelTimedControlCountdowns();
        connectionStateText = "未连接";
        handler.removeCallbacks(writeTimeoutRunnable);
        handler.removeCallbacks(mtuReadyTimeoutRunnable);
        handler.removeCallbacks(serviceDiscoveryTimeoutRunnable);
        handler.removeCallbacks(protocolReadyFallbackRunnable);
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
        // addLog 会从 BLE binder 线程写入,getLogs 从主线程拷贝,清空同样要持锁。
        synchronized (logsLock) {
            logs.clear();
        }
        emit();
    }

    public void readAllStatus() {
        if (isWriteQueueOverloaded()) {
            return;
        }
        enqueueEnvironmentSweep();
        sendCommand("{\"cmd\":\"get_status_light\",\"zone\":\"" + currentZone + "\"}");
        sendCommand("{\"cmd\":\"get_main_version\"}");
        sendCommand("{\"cmd\":\"get_ctrl_version\"}");
        sendCommand("{\"cmd\":\"get_bt_ver\"}");
        addLog("正在读取所有设备状态");
        emit();
    }

    /** 每 3 秒执行一次；当前舱优先，另一舱随后。 */
    public void readEnvironmentStatus() {
        if (!isWriteQueueOverloaded()) {
            enqueueEnvironmentSweep();
        }
    }

    /**
     * ★ 定时控件倒计时校时：读取主机当前剩余时间。
     * 只在至少有一个定时控件处于开启/不限时状态时才发，空闲时不占用 BLE。
     */
    public void readTimedControlStatus() {
        if (isWriteQueueOverloaded() || !hasActiveTimedControl()) {
            return;
        }
        sendCommand("{\"cmd\":\"get_red_light\",\"zone\":\"" + currentZone + "\"}");
        sendCommand("{\"cmd\":\"get_blue_light\",\"zone\":\"" + currentZone + "\"}");
        sendCommand("{\"cmd\":\"get_uv\",\"zone\":\"" + currentZone + "\"}");
        sendCommand("{\"cmd\":\"get_nebulizer\",\"zone\":\"" + currentZone + "\"}");
        sendCommand("{\"cmd\":\"get_anion\",\"zone\":\"" + currentZone + "\"}");
    }

    private boolean hasActiveTimedControl() {
        return Boolean.TRUE.equals(redTherapy) || Boolean.TRUE.equals(blueTherapy)
                || Boolean.TRUE.equals(uv) || Boolean.TRUE.equals(nebulizer)
                || Boolean.TRUE.equals(anion);
    }

    private boolean isWriteQueueOverloaded() {
        synchronized (writeQueueLock) {
            return writeQueue.size() + (currentWrite == null ? 0 : 1) > 20;
        }
    }

    private void enqueueEnvironmentSweep() {
        enqueueEnvironmentRead(currentZone);
        enqueueEnvironmentRead("left".equals(currentZone) ? "right" : "left");
    }

    private void enqueueEnvironmentRead(String zone) {
        String normalized = normalizeZone(zone);
        if (TextUtils.isEmpty(normalized)) {
            return;
        }
        // get_all_status 是连接时已验证的完整环境回包路径；每舱一条查询
        // 替代四条格式不一致的单项查询，降低 BLE 队列压力。
        sendCommand("{\"cmd\":\"get_all_status\",\"zone\":\"" + normalized + "\"}");
    }

    /**
     * ★ #16 修复:用户主动请求读取湿度。
     * 发送 get_humidity 命令给设备,设备响应后湿度值会自动更新到 humidity 字段。
     * UI 端订阅 state.host.humidity 即可看到最新值。
     */
    public boolean requestHumidity() {
        if (!ensureReady()) {
            return false;
        }
        sendCommand("{\"cmd\":\"get_humidity\",\"zone\":\"" + currentZone + "\"}");
        addLog("主动请求读取湿度");
        return true;
    }

    /**
     * ★ #17 修复:用户主动请求读取 CO2。
     * 发送 get_co2 命令给设备,设备响应后 CO2 值会自动更新到 co2 字段。
     * 同时检查是否超过报警阈值,超过则返回告警信息。
     */
    public boolean requestCo2() {
        if (!ensureReady()) {
            return false;
        }
        sendCommand("{\"cmd\":\"get_co2\",\"zone\":\"" + currentZone + "\"}");
        addLog("主动请求读取 CO2");
        return true;
    }

    public boolean sendControlPreset(int index) {
        if (TimedControlProtocol.isTimedControlIndex(index)) {
            return isControlOn(index)
                    ? setControlEnabled(index, false)
                    : startTimedControlDirect(index);
        }
        if (!ensureReady()) {
            return false;
        }
        switch (index) {
            case 0:
                sendCommand("{\"cmd\":\"get_temp\",\"zone\":\"" + currentZone + "\"}");
                break;
            case 1:
                sendCommand("{\"cmd\":\"get_o2\",\"zone\":\"" + currentZone + "\"}");
                break;
            case 2:
                sendCommand("{\"cmd\":\"status_light\",\"zone\":\"" + currentZone + "\",\"color\":\"" + nextStatusLightColor() + "\"}");
                break;
            case 3:
                sendCommand("{\"cmd\":\"get_co2\",\"zone\":\"" + currentZone + "\"}");
                break;
            case 4:
                boolean nextCold = nextSwitchValue(coldLight);
                sendCommand("{\"cmd\":\"set_cold_light\",\"zone\":\"" + currentZone + "\",\"enable\":\"" + onOff(nextCold) + "\"}");
                coldLight = nextCold;
                break;
            case 5:
                boolean nextWarm = nextSwitchValue(warmLight);
                sendCommand("{\"cmd\":\"set_warm_light\",\"zone\":\"" + currentZone + "\",\"enable\":\"" + onOff(nextWarm) + "\"}");
                warmLight = nextWarm;
                break;
            case 8:
                boolean nextOuter = nextSwitchValue(outerCycle);
                sendCommand("{\"cmd\":\"set_outer_cycle\",\"enable\":\"" + onOff(nextOuter) + "\"}");
                outerCycle = nextOuter;
                break;
            case 9:
                boolean nextInner = nextSwitchValue(innerCycle);
                sendCommand("{\"cmd\":\"set_inner_cycle\",\"zone\":\"" + currentZone + "\",\"enable\":\"" + onOff(nextInner) + "\"}");
                innerCycle = nextInner;
                break;
            case 13:
                boolean nextOxygen = nextSwitchValue(o2Enabled);
                sendCommand("{\"cmd\":\"set_o2_enable\",\"zone\":\"" + currentZone + "\",\"enable\":\"" + onOff(nextOxygen) + "\"}");
                o2Enabled = nextOxygen;
                break;
            case 14:
                sendCommand("{\"cmd\":\"get_humidity\",\"zone\":\"" + currentZone + "\"}");
                break;
            case 15:
                sendCommand("{\"cmd\":\"get_run_time\"}");
                break;
            default:
                return false;
        }
        scheduleStatusRefresh();
        emit();
        return true;
    }

    public boolean setTemperature(float value) {
        if (!ensureReady()) {
            addLog("[P1-11] setTemperature:设备未连接,本地保存温度 " + value + "℃");
            // ★ P1-11 修复:设备未连接也保存本地目标温度,对话框默认值用最近设置
            lastSetCabinTemp = value;
            appContext.getSharedPreferences("icu_ble_settings", Context.MODE_PRIVATE)
                    .edit()
                    .putFloat("last_set_cabin_temp", value)
                    .apply();
            emit();
            return true;
        }
        lastSetCabinTemp = value;
        appContext.getSharedPreferences("icu_ble_settings", Context.MODE_PRIVATE)
                .edit()
                .putFloat("last_set_cabin_temp", value)
                .apply();
        String valueStr = formatCommandNumber(value);
        sendCommand("{\"cmd\":\"set_temp\",\"value\":" + valueStr + "}");
        // ★ P1-2:记录 pending
        recordSetPending("set_temp", null, valueStr);
        scheduleStatusRefresh();
        return true;
    }

    public boolean setTemperatureEnabled(boolean enabled) {
        if (!ensureReady()) {
            return false;
        }
        String enableStr = onOff(enabled);
        sendCommand("{\"cmd\":\"set_temp_enable\",\"enable\":\"" + enableStr + "\"}");
        tempEnabled = enabled;
        // ★ P1-2:记录 pending
        recordSetPending("set_temp_enable", enableStr, null);
        scheduleStatusRefresh();
        emit();
        return true;
    }

    public boolean setOxygen(float value) {
        if (!ensureReady()) {
            addLog("[P1-11] setOxygen:设备未连接,本地保存氧浓度 " + value + "%");
            // ★ P1-11 修复:设备未连接也保存本地目标氧浓度
            setLastSetOxygen(value);
            emit();
            return true;
        }
        setLastSetOxygen(value);
        String valueStr = formatCommandNumber(value);
        sendCommand("{\"cmd\":\"set_o2\",\"value\":" + valueStr + ",\"zone\":\"" + currentZone + "\"}");
        // ★ P1-2:记录 pending(value 字段以整数字符串形式)
        recordSetPending("set_o2", null, valueStr);
        scheduleStatusRefresh();
        return true;
    }

    public boolean setOxygenEnabled(boolean enabled) {
        if (!ensureReady()) {
            return false;
        }
        String enableStr = onOff(enabled);
        sendCommand("{\"cmd\":\"set_o2_enable\",\"zone\":\"" + currentZone + "\",\"enable\":\"" + enableStr + "\"}");
        o2Enabled = enabled;
        // ★ P1-2:记录 pending
        recordSetPending("set_o2_enable", enableStr, null);
        scheduleStatusRefresh();
        emit();
        return true;
    }

    public boolean setHumidity(float value) {
        // ★ P1-9 修复:设备未连接也保存本地湿度(之前 return false 用户反馈点不动)
        if (!ensureReady()) {
            addLog("[P1-9] setHumidity:设备未连接,本地保存湿度目标 " + value + "%");
            // 只持久化设置值，不能把目标值写进实时传感器缓存。
            appContext.getSharedPreferences("icu_ble_settings", Context.MODE_PRIVATE)
                    .edit()
                    .putFloat("last_set_humidity", value)
                    .apply();
            emit();
            return true;
        }
        // ★ 协议对齐(优利特 V1.00)：湿度为只读，设备无 set_humidity 命令，下发会被固件忽略。
        //   不再发送无效命令；仅本地保存目标值供对话框回填，并记日志便于审计。
        addLog("[协议] setHumidity: 湿度只读，设备不支持设置，已跳过下发仅本地保存 " + value + "%");
        appContext.getSharedPreferences("icu_ble_settings", Context.MODE_PRIVATE)
                .edit()
                .putFloat("last_set_humidity", value)
                .apply();
        emit();
        return true;
    }

    public boolean setCo2(int value) {
        if (!ensureReady()) {
            return false;
        }
        // ★ P0-2:校验 value 范围,拒绝非法值
        if (value < CO2_VALUE_MIN || value > CO2_VALUE_MAX) {
            addLog("setCo2 拒绝非法值: " + value + " (允许范围 " + CO2_VALUE_MIN + "-" + CO2_VALUE_MAX + ")");
            setError("CO2 值超出允许范围 (0-" + CO2_VALUE_MAX + ")");
            return false;
        }
        String valueStr = String.valueOf(value);
        sendCommand("{\"cmd\":\"set_co2\",\"value\":" + valueStr + ",\"zone\":\"" + currentZone + "\"}");
        // 目标浓度回滚备份（设备拒绝时用），与报警阈值无关
        lastSetCo2BeforeRollback = getCurrentLastSetCo2();
        // 只保存目标浓度；自动预警阀值是固定常量，与此处无关
        if ("left".equals(currentZone)) {
            lastSetCo2Left = value;
        } else {
            lastSetCo2Right = value;
        }
        String key = "left".equals(currentZone) ? "last_set_co2_left" : "last_set_co2_right";
        appContext.getSharedPreferences("icu_ble_settings", Context.MODE_PRIVATE)
                .edit()
                .putInt(key, value)
                .apply();
        // ★ P1-2:记录 pending
        recordSetPending("set_co2", null, valueStr);
        scheduleStatusRefresh();
        return true;
    }

    /** 按当前舱取 CO2 目标浓度（仅用于弹窗回填）。 */
    private Integer getCurrentLastSetCo2() {
        return "left".equals(currentZone) ? lastSetCo2Left : lastSetCo2Right;
    }

    /**
     * 设备拒绝 set_co2 时回滚 CO2 目标浓度到上一有效值。
     * 自动预警阀值是固定常量，不参与回滚。
     */
    private void rollbackLastSetCo2() {
        Integer rollbackTo = lastSetCo2BeforeRollback;
        if ("left".equals(currentZone)) {
            lastSetCo2Left = rollbackTo;
        } else {
            lastSetCo2Right = rollbackTo;
        }
        String key = "left".equals(currentZone) ? "last_set_co2_left" : "last_set_co2_right";
        SharedPreferences.Editor editor =
                appContext.getSharedPreferences("icu_ble_settings", Context.MODE_PRIVATE).edit();
        if (rollbackTo == null) {
            editor.remove(key);
        } else {
            editor.putInt(key, rollbackTo);
        }
        editor.apply();
        addLog("CO2 目标浓度已回滚到 " + (rollbackTo == null ? "未设置" : rollbackTo + " PPM"));
    }

    /**
     * 检查 CO2 是否超过固定的自动预警阀值，由 MainActivity 在收到 BLE 状态时调用。
     * 超阈时自动开启外循环换气，并返回事件文本用于日志与历史记录。
     * 阀值为常量 CO2_AUTO_VENT_THRESHOLD，与 CO2 目标浓度完全独立。
     * @return null=未触发,否则返回换气/恢复信息
     */
    public String checkCo2Alarm() {
        return checkCo2Alarm(currentZone);
    }

    public String checkCo2Alarm(final String zone) {
        final String normalized = normalizeZone(zone);
        if (TextUtils.isEmpty(normalized)) {
            return null;
        }
        if (normalized.equals(currentZone)) {
            String event = checkCurrentZoneCo2Alarm();
            saveCurrentZoneProjection();
            return event;
        }
        final String[] result = new String[1];
        runWithZoneProjection(normalized, new Runnable() {
            @Override
            public void run() {
                result[0] = checkCurrentZoneCo2Alarm();
            }
        });
        return result[0];
    }

    private String checkCurrentZoneCo2Alarm() {
        final int threshold = CO2_AUTO_VENT_THRESHOLD;
        Integer currentCo2 = getCurrentCo2();
        if (currentCo2 == null) {
            // 读数丢失时必须复位 wasInAlarm，否则 isInCo2Alarm() 会一直返回
            // true，UI 的报警态(.icu-alarm)永久卡住。
            wasInAlarm = false;
            return null;
        }
        long now = SystemClock.uptimeMillis();
        if (currentCo2 > threshold) {
            // 超阈即触发换气；节流避免同一次超阈反复下发 set_outer_cycle。
            boolean vented = false;
            if (now - lastCo2AutoVentAt >= CO2_AUTO_VENT_INTERVAL_MS) {
                vented = openOuterCycleForVentilation();
                if (vented) {
                    lastCo2AutoVentAt = now;
                }
            }
            // 30 秒内不重复报警
            if (now - lastCo2AlarmAt < CO2_ALARM_INTERVAL_MS) {
                wasInAlarm = true;
                return null;
            }
            lastCo2AlarmAt = now;
            wasInAlarm = true;
            String event = "[" + alarmTimestamp() + "] 当前 " + currentCo2
                    + " PPM,超过自动换气阀值 " + threshold + " PPM"
                    + (vented ? "，已自动开启外循环换气" : "，外循环已在运行或未连接");
            addAlarmHistory(event);
            return event;
        }
        // 回到阀值以内时，60 秒内只报一次恢复
        if (wasInAlarm && now - lastCo2RecoverAt > CO2_RECOVER_INTERVAL_MS) {
            lastCo2RecoverAt = now;
            wasInAlarm = false;
            lastCo2AutoVentAt = 0L;
            String event = "[" + alarmTimestamp() + "] 当前 " + currentCo2
                    + " PPM,已回到自动换气阀值 " + threshold + " PPM 以内";
            addAlarmHistory(event);
            return event;
        }
        wasInAlarm = false;
        return null;
    }

    /**
     * CO2 超阈时自动开启外循环换气。已开启或未连接时返回 false，不重复下发。
     */
    private boolean openOuterCycleForVentilation() {
        if (Boolean.TRUE.equals(outerCycle)) {
            return false;
        }
        if (!ensureReady()) {
            addLog("[CO2预警] 未连接主机，无法自动开启外循环");
            return false;
        }
        sendCommand("{\"cmd\":\"set_outer_cycle\",\"enable\":\"on\"}");
        outerCycle = true;
        recordSetPending("set_outer_cycle", "on", null);
        addLog("[CO2预警] CO2 超过预警阀值，已自动开启外循环换气");
        scheduleStatusRefresh();
        return true;
    }

    private String alarmTimestamp() {
        java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.US);
        return sdf.format(new java.util.Date());
    }

    /**
     * ★ P2-1:添加报警历史(最多保留 50 条,最新的在末尾)
     */
    private void addAlarmHistory(String event) {
        synchronized (co2AlarmHistory) {
            if (co2AlarmHistory.size() >= CO2_ALARM_HISTORY_MAX) {
                co2AlarmHistory.pollFirst();
            }
            co2AlarmHistory.offerLast(event);
        }
    }

    /**
     * ★ P2-1:公开方法:获取报警历史(JSON 数组)
     */
    public String getCo2AlarmHistory() {
        synchronized (co2AlarmHistory) {
            org.json.JSONArray arr = new org.json.JSONArray();
            for (String s : co2AlarmHistory) {
                arr.put(s);
            }
            return arr.toString();
        }
    }

    /** 兼容旧调用：返回固定的自动换气阀值。 */
    public int getCo2AlarmThreshold() {
        return CO2_AUTO_VENT_THRESHOLD;
    }

    /** CO2 自动换气阀值（固定常量，不可配置）。 */
    public int getCo2AutoVentThreshold() {
        return CO2_AUTO_VENT_THRESHOLD;
    }

    /** CO2 目标浓度（用户上次设置值），未设置时返回 null。 */
    public Integer getLastSetCo2Value() {
        return getCurrentLastSetCo2();
    }

    /**
     * ★ P1-1:判断当前是否处于 CO2 报警状态(给 JS UI 用,驱动 .icu-alarm class)
     */
    public boolean isInCo2Alarm() {
        return wasInAlarm;
    }

    /**
     * 判断 CO2 当前是否超过固定的自动换气阀值。
     */
    public boolean isCo2OverThreshold() {
        Integer currentCo2 = getCurrentCo2();
        return currentCo2 != null && currentCo2 > CO2_AUTO_VENT_THRESHOLD;
    }

    public boolean toggleCo2Enable() {
        if (!ensureReady()) {
            return false;
        }
        boolean next = nextSwitchValue(co2Enabled);
        sendCommand("{\"cmd\":\"set_co2_enable\",\"enable\":\"" + onOff(next) + "\"}");
        co2Enabled = next;
        // ★ P1-2:记录 pending,3s 超时主动 get_co2 校验
        recordSetPending("set_co2_enable", onOff(next), null);
        return true;
    }

    public boolean setControlEnabled(int index, boolean enabled) {
        if (!ensureReady()) {
            return false;
        }
        String enableStr = onOff(enabled);
        switch (index) {
            case 3:
                sendCommand("{\"cmd\":\"set_co2_enable\",\"enable\":\"" + enableStr + "\"}");
                co2Enabled = enabled;
                recordSetPending("set_co2_enable", enableStr, null);
                break;
            case 4:
                sendCommand("{\"cmd\":\"set_cold_light\",\"zone\":\"" + currentZone + "\",\"enable\":\"" + enableStr + "\"}");
                coldLight = enabled;
                recordSetPending("set_cold_light", enableStr, null);
                break;
            case 5:
                sendCommand("{\"cmd\":\"set_warm_light\",\"zone\":\"" + currentZone + "\",\"enable\":\"" + enableStr + "\"}");
                warmLight = enabled;
                recordSetPending("set_warm_light", enableStr, null);
                break;
            case 6:
                sendCommand("{\"cmd\":\"set_red_enable\",\"zone\":\"" + currentZone + "\",\"enable\":\"" + enableStr + "\"}");
                redTherapy = enabled;
                recordSetPending("set_red_enable", enableStr, null);
                break;
            case 7:
                sendCommand("{\"cmd\":\"set_blue_enable\",\"zone\":\"" + currentZone + "\",\"enable\":\"" + enableStr + "\"}");
                blueTherapy = enabled;
                recordSetPending("set_blue_enable", enableStr, null);
                break;
            case 8:
                sendCommand("{\"cmd\":\"set_outer_cycle\",\"enable\":\"" + enableStr + "\"}");
                outerCycle = enabled;
                recordSetPending("set_outer_cycle", enableStr, null);
                break;
            case 9:
                sendCommand("{\"cmd\":\"set_inner_cycle\",\"zone\":\"" + currentZone + "\",\"enable\":\"" + enableStr + "\"}");
                innerCycle = enabled;
                recordSetPending("set_inner_cycle", enableStr, null);
                break;
            case 10:
                sendCommand("{\"cmd\":\"set_nebulizer_enable\",\"zone\":\"" + currentZone + "\",\"enable\":\"" + enableStr + "\"}");
                nebulizer = enabled;
                recordSetPending("set_nebulizer_enable", enableStr, null);
                break;
            case 11:
                sendCommand("{\"cmd\":\"set_anion_enable\",\"zone\":\"" + currentZone + "\",\"enable\":\"" + enableStr + "\"}");
                anion = enabled;
                recordSetPending("set_anion_enable", enableStr, null);
                break;
            case 12:
                sendCommand("{\"cmd\":\"set_uv_enable\",\"zone\":\"" + currentZone + "\",\"enable\":\"" + enableStr + "\"}");
                uv = enabled;
                recordSetPending("set_uv_enable", enableStr, null);
                break;
            case 13:
                sendCommand("{\"cmd\":\"set_o2_enable\",\"zone\":\"" + currentZone + "\",\"enable\":\"" + enableStr + "\"}");
                o2Enabled = enabled;
                recordSetPending("set_o2_enable", enableStr, null);
                break;
            default:
                return false;
        }
        if (!enabled) {
            clearCountdown(index);
        }
        scheduleStatusRefresh();
        emit();
        return true;
    }

    /**
     * 5 个定时控件直接点击开关：按协议固定本次运行时长开启。
     * - 紫外/雾化/负离子：120 分钟并启动倒计时；
     * - 红外/蓝光：不限时（协议 65536），不启动倒计时；
     * - 本次运行值不覆盖用户在“设置”中保存的时长。
     */
    public boolean startTimedControlDirect(int index) {
        if (!TimedControlProtocol.isTimedControlIndex(index)) {
            return false;
        }
        int minutes = TimedControlProtocol.directStartMinutes(index);
        boolean unlimited = TimedControlProtocol.isUnlimitedMinutes(minutes);
        // ★ P1-3:设备未连接也先更新本地状态，连接后由主机状态校正
        if (!ensureReady()) {
            addLog("[定时] 未连接，本地按" + (unlimited ? "不限时" : minutes + " 分钟") + "开启 index=" + index);
            applyLocalTimedStart(index, minutes);
            emit();
            return true;
        }
        // 1. 先下发本次运行时长（不写入“设置”保存值）
        if (!setTimedControlRunDuration(index, minutes)) {
            return false;
        }
        // 2. 本地先起算，随后由主机 time_remaining 校时
        applyLocalTimedStart(index, minutes);
        // 3. 再发 set_xxx_enable on
        return setControlEnabled(index, true);
    }

    /** 本地起始状态：不限时只置标记，不产生倒计时终点。 */
    private void applyLocalTimedStart(int index, int minutes) {
        setTimedControlEnabledLocally(index, true);
        if (TimedControlProtocol.isUnlimitedMinutes(minutes)) {
            clearCountdown(index);
            setUnlimitedRunning(index, true);
        } else {
            setUnlimitedRunning(index, false);
            armCountdown(index, minutes);
        }
    }

    /**
     * “设置”确认后的启动路径：使用用户刚输入的时长，并立即开启对应功能。
     */
    public boolean startTimedControlWithConfiguredDuration(int index, int minutes) {
        if (!TimedControlProtocol.isTimedControlIndex(index) || minutes <= 0) {
            return false;
        }
        if (!setControlTime(index, minutes)) {
            return false;
        }
        if (TimedControlProtocol.isUnlimitedMinutes(minutes)) {
            clearCountdown(index);
            setUnlimitedRunning(index, true);
        } else {
            setUnlimitedRunning(index, false);
            armCountdown(index, minutes);
        }
        if (!ensureReady()) {
            setTimedControlEnabledLocally(index, true);
            emit();
            return true;
        }
        return setControlEnabled(index, true);
    }

    /**
     * 护理模式使用刚保存的配置时长启用；与卡片“直接开启”的固定运行值分离。
     */
    public boolean setTimedControlEnabledFromConfiguredDuration(int index, boolean enabled) {
        if (!TimedControlProtocol.isTimedControlIndex(index)) {
            return setControlEnabled(index, enabled);
        }
        if (!enabled) {
            return setControlEnabled(index, false);
        }
        Integer minutes = getControlTimeValue(index);
        if (minutes == null || minutes <= 0
                || !TimedControlProtocol.isAcceptableMinutes(index, minutes)) {
            lastError = "护理模式时长无效 index=" + index;
            addLog(lastError);
            emit();
            return false;
        }
        if (!ensureReady()) {
            return false;
        }
        applyLocalTimedStart(index, minutes);
        return setControlEnabled(index, true);
    }

    private void setTimedControlEnabledLocally(int index, boolean enabled) {
        switch (index) {
            case 6: redTherapy = enabled; break;
            case 7: blueTherapy = enabled; break;
            case 10: nebulizer = enabled; break;
            case 11: anion = enabled; break;
            case 12: uv = enabled; break;
            default: break;
        }
    }

    private long getCountdownField(int index) {
        switch (index) {
            case 6:
                return redCountdownEndAtMs;
            case 7:
                return blueCountdownEndAtMs;
            case 10:
                return nebulizerCountdownEndAtMs;
            case 11:
                return anionCountdownEndAtMs;
            case 12:
                return uvCountdownEndAtMs;
            case 15:
                return treatmentCountdownEndAtMs;
            default:
                return 0L;
        }
    }

    private void setCountdownField(int index, long endAtMs) {
        switch (index) {
            case 6:
                redCountdownEndAtMs = endAtMs;
                break;
            case 7:
                blueCountdownEndAtMs = endAtMs;
                break;
            case 10:
                nebulizerCountdownEndAtMs = endAtMs;
                break;
            case 11:
                anionCountdownEndAtMs = endAtMs;
                break;
            case 12:
                uvCountdownEndAtMs = endAtMs;
                break;
            case 15:
                treatmentCountdownEndAtMs = endAtMs;
                break;
        }
        if (index == 15) {
            saveZoneTreatmentCountdownEndAt(endAtMs);
        } else {
            saveZoneCountdownEndAt(index, endAtMs);
        }
    }

    private void clearCountdown(int index) {
        setCountdownField(index, 0L);
    }

    /** 不限时运行标记：按舱持久化，切舱/重启后仍能恢复“常开”状态。 */
    private void setUnlimitedRunning(int index, boolean running) {
        if (!TimedControlProtocol.isTimedControlIndex(index)) {
            return;
        }
        if (running) {
            unlimitedRunning.add(index);
        } else {
            unlimitedRunning.remove(index);
        }
        appContext.getSharedPreferences("icu_ble_settings", Context.MODE_PRIVATE)
                .edit()
                .putBoolean(zoneUnlimitedKey(index), running)
                .apply();
    }

    public boolean isTimedControlUnlimited(int index) {
        return unlimitedRunning.contains(index);
    }

    private String zoneUnlimitedKey(int index) {
        return "zone_timed_control_unlimited_" + index + "_" + currentZone;
    }

    private Integer getConfiguredMinutes(int index) {
        switch (index) {
            case 6:
                return redTime;
            case 7:
                return blueTime;
            case 10:
                return nebulizerTime;
            case 11:
                return anionTime;
            case 12:
                return uvTime;
            case 15:
                return treatmentMinutes;
            default:
                return null;
        }
    }

    private void armCountdown(int index) {
        Integer minutes = getConfiguredMinutes(index);
        if (minutes == null || minutes <= 0) {
            clearCountdown(index);
            return;
        }
        armCountdown(index, minutes);
    }

    private void armCountdown(int index, int minutes) {
        if (TimedControlProtocol.isUnlimitedMinutes(minutes)) {
            // 不限时：不设终点，避免 handleCountdownTick 到点误发关闭命令。
            clearCountdown(index);
            setUnlimitedRunning(index, true);
            return;
        }
        if (minutes <= 0) {
            clearCountdown(index);
            return;
        }
        setUnlimitedRunning(index, false);
        setCountdownField(index, System.currentTimeMillis() + minutes * 60000L);
        scheduleCountdownCheck(index);
    }

    private boolean setTimedControlRunDuration(int index, int minutes) {
        String cmd = timedTimeCmdName(index);
        if (cmd == null) {
            return false;
        }
        // 一次性运行时长：回包不写回“用户配置时长”。
        sendCommand("{\"cmd\":\"" + cmd + "\",\"zone\":\"" + currentZone + "\",\"value\":" + minutes + "}");
        recordSetPending(cmd, null, String.valueOf(minutes));
        return true;
    }

    private String timedTimeCmdName(int index) {
        switch (index) {
            case 6:
                return "set_red_time";
            case 7:
                return "set_blue_time";
            case 10:
                return "set_nebulizer_time";
            case 11:
                return "set_anion_time";
            case 12:
                return "set_uv_time";
            default:
                return null;
        }
    }

    private boolean isTimedTimeCmd(String cmd) {
        return "set_red_time".equals(cmd) || "set_blue_time".equals(cmd)
                || "set_uv_time".equals(cmd) || "set_nebulizer_time".equals(cmd)
                || "set_anion_time".equals(cmd);
    }

    /** 单条定时控件状态查询命令 → 控件索引；非定时命令返回 -1。 */
    private int timedIndexForGetCmd(String cmd) {
        if ("get_red_light".equals(cmd)) return 6;
        if ("get_blue_light".equals(cmd)) return 7;
        if ("get_nebulizer".equals(cmd)) return 10;
        if ("get_anion".equals(cmd)) return 11;
        if ("get_uv".equals(cmd)) return 12;
        return -1;
    }

    /** 兼容 status / enable 两种字段命名。 */
    private boolean timedEnabledFromResponse(JSONObject object) {
        String value = object.optString("status", "");
        if (TextUtils.isEmpty(value)) {
            value = object.optString("enable", "");
        }
        return "on".equalsIgnoreCase(value) || "true".equalsIgnoreCase(value) || "1".equals(value);
    }

    /** 兼容 time_remaining / remaining / time / value 几种剩余时间字段。 */
    private int timedRemainingFromResponse(JSONObject object) {
        if (object.has("time_remaining")) {
            return object.optInt("time_remaining", 0);
        }
        if (object.has("remaining")) {
            return object.optInt("remaining", 0);
        }
        if (object.has("time")) {
            return object.optInt("time", 0);
        }
        return object.optInt("value", 0);
    }

    public long getCountdownRemainingMs(int index) {
        long endAt = getCountdownField(index);
        if (endAt <= 0L) {
            return 0L;
        }
        long remaining = endAt - System.currentTimeMillis();
        if (remaining < 0L) {
            return 0L;
        }
        return remaining;
    }

    private void scheduleCountdownCheck(final int index) {
        final String zone = currentZone;
        String key = countdownRunnableKey(zone, index);
        Runnable existing = countdownRunnables.get(key);
        if (existing != null) {
            handler.removeCallbacks(existing);
        }
        Runnable runnable = new Runnable() {
            @Override
            public void run() {
                runWithZoneProjection(zone, new Runnable() {
                    @Override
                    public void run() {
                        handleCountdownTick(index);
                    }
                });
                emit();
            }
        };
        setCountdownRunnable(zone, index, runnable);
        long remaining = getCountdownRemainingMs(index);
        long delay = remaining > 0L ? Math.min(1000L, remaining) : 0L;
        handler.postDelayed(runnable, delay);
    }

    private void handleCountdownTick(int index) {
        long remaining = getCountdownRemainingMs(index);
        if (remaining > 0L) {
            emit();
            scheduleCountdownCheck(index);
            return;
        }
        clearCountdown(index);
        if (index == 15) {
            treatmentMinutes = 0;
            saveZoneTreatmentMinutes(0);
        } else {
            setControlEnabled(index, false);
        }
        emit();
    }

    private final java.util.HashMap<String, Runnable> countdownRunnables = new java.util.HashMap<>();

    private String countdownRunnableKey(String zone, int index) {
        return normalizeZone(zone) + ":" + index;
    }

    private Runnable countdownCheckRunnable(int index) {
        return countdownRunnables.get(countdownRunnableKey(currentZone, index));
    }

    private void setCountdownRunnable(String zone, int index, Runnable runnable) {
        String key = countdownRunnableKey(zone, index);
        Runnable previous = countdownRunnables.get(key);
        if (previous != null) {
            handler.removeCallbacks(previous);
        }
        countdownRunnables.put(key, runnable);
    }

    public boolean setControlTime(int index, int minutes) {
        if (minutes < 0) {
            minutes = 0;
        }
        // ★ 治疗时长范围验证：紫外/雾化/负离子 0-120 分钟；
        //   红外/蓝光额外允许不限时哨兵值 65536。
        if (TimedControlProtocol.isTimedControlIndex(index)
                && !TimedControlProtocol.isAcceptableMinutes(index, minutes)) {
            lastError = "治疗时长必须在0-" + TIMED_MAX_MINUTES + "分钟之间，当前输入：" + minutes + "分钟";
            addLog("setControlTime 范围错误: index=" + index + " minutes=" + minutes);
            emit();
            return false;
        }
        if (index == 15) {
            treatmentMinutes = minutes;
            saveZoneTreatmentMinutes(minutes);
            refreshTreatmentCountdown();
            emit();
            return true;
        }
        if (!ensureReady()) {
            addLog("[P1-6] setControlTime:设备未连接,本地保存 " + minutes + " 分钟");
            // 设备未连接时仍保存用户配置，供下次打开“设置”时预填；
            // 直接点击开关始终使用协议固定的 120/65536，不读取此值。
            switch (index) {
                case 6: redTime = minutes; break;
                case 7: blueTime = minutes; break;
                case 10: nebulizerTime = minutes; break;
                case 11: anionTime = minutes; break;
                case 12: uvTime = minutes; break;
                default: return false;
            }
            saveZoneTimedControlValue(index, minutes);
            emit();
            return true;
        }
        switch (index) {
            case 6:
                sendCommand("{\"cmd\":\"set_red_time\",\"zone\":\"" + currentZone + "\",\"value\":" + minutes + "}");
                redTime = minutes;
                recordSetPending("set_red_time", null, String.valueOf(minutes));
                break;
            case 7:
                sendCommand("{\"cmd\":\"set_blue_time\",\"zone\":\"" + currentZone + "\",\"value\":" + minutes + "}");
                blueTime = minutes;
                recordSetPending("set_blue_time", null, String.valueOf(minutes));
                break;
            case 10:
                sendCommand("{\"cmd\":\"set_nebulizer_time\",\"zone\":\"" + currentZone + "\",\"value\":" + minutes + "}");
                nebulizerTime = minutes;
                recordSetPending("set_nebulizer_time", null, String.valueOf(minutes));
                break;
            case 11:
                sendCommand("{\"cmd\":\"set_anion_time\",\"zone\":\"" + currentZone + "\",\"value\":" + minutes + "}");
                anionTime = minutes;
                recordSetPending("set_anion_time", null, String.valueOf(minutes));
                break;
            case 12:
                sendCommand("{\"cmd\":\"set_uv_time\",\"zone\":\"" + currentZone + "\",\"value\":" + minutes + "}");
                uvTime = minutes;
                recordSetPending("set_uv_time", null, String.valueOf(minutes));
                break;
            default:
                return false;
        }
        saveZoneTimedControlValue(index, minutes);
        scheduleStatusRefresh();
        emit();
        return true;
    }

    private void refreshTreatmentCountdown() {
        if (treatmentMinutes == null || treatmentMinutes <= 0) {
            clearCountdown(15);
            return;
        }
        armCountdown(15);
    }

    public boolean setCareMode(String value) {
        if (!ensureReady()) {
            return false;
        }
        if (TextUtils.isEmpty(value)) {
            value = "Custom_mode";
        }
        sendCommand("{\"cmd\":\"set_care_mode\",\"zone\":\"" + currentZone + "\",\"value\":\"" + escapeJson(value) + "\"}");
        scheduleStatusRefresh();
        return true;
    }

    public boolean saveCareModeSet(String value) {
        if (!ensureReady()) {
            return false;
        }
        if (TextUtils.isEmpty(value)) {
            value = "Custom_mode";
        }
        sendCommand("{\"cmd\":\"save_care_mode_set\",\"zone\":\"" + currentZone + "\",\"value\":\"" + escapeJson(value) + "\"}");
        scheduleStatusRefresh();
        return true;
    }

    public boolean upgradeMainBoard() {
        if (!ensureReady()) {
            return false;
        }
        sendCommand("{\"cmd\":\"upgrade_main\"}");
        emit();
        return true;
    }

    public boolean upgradeControlBoard() {
        if (!ensureReady()) {
            return false;
        }
        sendCommand("{\"cmd\":\"upgrade_ctrl\"}");
        emit();
        return true;
    }

    /**
     * ★ 新加：A3/A4 升级占位。BLE 协议字段 upgrade_other 待设备端确认，先发个空 payload。
     *   native action 里只对 A1/A2 真下发，A3/A4 走 Toast 提示，本方法实际不被调用。
     *   保留是为了对称性，未来协议明确后只要替换 sendCommand 里的 cmd/字段即可。
     */
    public boolean upgradeOtherBoard(String upgradeFile) {
        if (!ensureReady()) {
            return false;
        }
        String value = upgradeFile == null ? "" : upgradeFile.trim();
        sendCommand("{\"cmd\":\"upgrade_other\",\"value\":\"" + escapeJson(value) + "\"}");
        emit();
        return true;
    }

    public boolean setBluetoothName(String name) {
        if (!ensureReady()) {
            return false;
        }
        String cleaned = name == null ? "" : name.trim();
        if (TextUtils.isEmpty(cleaned)) {
            setError("蓝牙名称不能为空");
            return false;
        }
        sendCommand("{\"cmd\":\"set_bt_name\",\"value\":\"" + escapeJson(cleaned) + "\"}");
        emit();
        return true;
    }

    private boolean isCabinScopedCommand(String cmd) {
        return !TextUtils.isEmpty(cmd)
                && (cmd.startsWith("set_") || cmd.startsWith("get_"))
                && !"set_bt_name".equals(cmd)
                && !"get_main_version".equals(cmd)
                && !"get_ctrl_version".equals(cmd)
                && !"get_display_version".equals(cmd)
                && !"get_other_version".equals(cmd)
                && !"get_bt_version".equals(cmd)
                && !cmd.startsWith("set_infrared_")
                && !cmd.startsWith("get_infrared_");
    }

    static String addCommandZone(String payload, String zone) {
        if (TextUtils.isEmpty(payload) || payload.indexOf("\"zone\"") >= 0) {
            return payload;
        }
        String cmd = extractJsonStringStatic(payload, "cmd");
        String normalized = EnvironmentProtocol.normalizeZone(zone);
        if (TextUtils.isEmpty(cmd) || TextUtils.isEmpty(normalized)) {
            return payload;
        }
        int end = payload.lastIndexOf('}');
        return end > 0 ? payload.substring(0, end) + ",\"zone\":\"" + normalized + "\"}" : payload;
    }

    private static String extractJsonStringStatic(String json, String key) {
        if (TextUtils.isEmpty(json)) {
            return "";
        }
        try {
            return new JSONObject(json).optString(key, "");
        } catch (Exception ignored) {
            return "";
        }
    }

    private String prepareOutboundCommand(String payload, String zone) {
        String cmd = extractJsonStringStatic(payload, "cmd");
        return isCabinScopedCommand(cmd) ? addCommandZone(payload, zone) : payload;
    }

    public void sendCommand(String payload) {
        payload = prepareOutboundCommand(payload, currentZone);
        if (missingBluetoothConnectPermission()) {
            setError("缺少蓝牙连接权限，请先申请权限");
            return;
        }
        if (bluetoothGatt == null) {
            setError("请先连接蓝牙设备");
            return;
        }
        if (!connected || writeCharacteristic == null) {
            setError("蓝牙已连接，协议通道初始化中，请稍后再试");
            return;
        }

        QueuedCommand command;
        boolean enqueued = true;
        synchronized (writeQueueLock) {
            command = buildQueuedCommand(payload);
            if (payload.contains("\"cmd\":\"get_")) {
                enqueued = !hasQueuedOrActiveDuplicate(command);
                if (enqueued) {
                    writeQueue.addLast(command);
                    addLog("[GET→队尾] " + payload);
                } else {
                    addLog("[GET合并] " + command.dedupKey);
                }
            } else if (isUserControlCommand(payload)) {
                dedupControlCommandInQueue(command);
                insertControlCommandLocked(command);
                addLog("[CTL→优先] " + payload);
            } else {
                writeQueue.addLast(command);
                addLog("[RAW→队尾] " + payload);
            }
        }
        if (!enqueued) {
            return;
        }
        lastError = "";
        processWriteQueue();
        emit();
    }

    /**
     * 用户控制命令仍然优先于后台 get_* 轮询，但控制命令之间保持先来后到。
     *
     * ★ 之前统一 addFirst：先入队的 set_*_time 会被后入队的 set_*_enable 顶到后面，
     *   主机可能先收到"开启"再收到"时长"，导致按旧时长运行 —— 这正是
     *   APP 与主机倒计时对不上的原因之一。
     * 调用方必须持有 writeQueueLock。
     */
    private void insertControlCommandLocked(QueuedCommand command) {
        java.util.ArrayList<QueuedCommand> tail = new java.util.ArrayList<>();
        // 把队首连续的控制命令原样保留，新命令排在它们之后、所有 get_* 之前。
        while (!writeQueue.isEmpty() && isUserControlCommand(writeQueue.peek().payload)) {
            tail.add(writeQueue.poll());
        }
        tail.add(command);
        for (int i = tail.size() - 1; i >= 0; i--) {
            writeQueue.addFirst(tail.get(i));
        }
    }

    private QueuedCommand buildQueuedCommand(String payload) {
        String cmd = "";
        String zone = "";
        try {
            JSONObject object = new JSONObject(payload);
            cmd = object.optString("cmd", "");
            zone = normalizeZone(object.optString("zone", ""));
        } catch (JSONException ignored) {
            cmd = extractControlCmdName(payload);
        }
        return new QueuedCommand(payload, cmd == null ? "" : cmd, zone, ++nextCommandToken);
    }

    /** 调用方必须持有 writeQueueLock。 */
    private boolean hasQueuedOrActiveDuplicate(QueuedCommand command) {
        if (command == null || TextUtils.isEmpty(command.dedupKey)) {
            return false;
        }
        if (currentWrite != null && command.dedupKey.equals(currentWrite.dedupKey)) {
            return true;
        }
        synchronized (environmentReadLock) {
            PendingEnvironmentRead pending = pendingEnvironmentReads.get(command.cmd);
            if (pending != null && command.zone.equals(pending.zone)) {
                return true;
            }
        }
        for (QueuedCommand queued : writeQueue) {
            if (command.dedupKey.equals(queued.dedupKey)) {
                return true;
            }
        }
        return false;
    }

    /**
     * ★ 新增 P0-2:识别用户主动控制命令。
     * 覆盖所有14 项主机控制 + 监护等级 + 升级/配网等用户操作。
     * 返回 true 表示走队首 + 去重。
     */
    private boolean isUserControlCommand(String payload) {
        if (payload == null) {
            return false;
        }
        return payload.contains("\"cmd\":\"set_")          // 全部 set_*(温度/氧/湿/CO2/所有理疗和照明和循环)
                || payload.contains("\"cmd\":\"status_light\"")  // ★ 监护等级(命名例外,不以 set_ 开头)
                || payload.contains("\"cmd\":\"save_")    // 保存护理模式配置
                || payload.contains("\"cmd\":\"upgrade_") // 升级主控/控制板
                || payload.contains("\"cmd\":\"set_bt_name\"");  // 改蓝牙名
    }

    /**
     * ★ 新增 P0-2:用户控制命令同名去重(覆盖 set_* + status_light + save_* 等)。
     * 解决用户连点同一开关多次时,队列里堆积多条相同命令的问题。
     * 保留最新的那条(用户最终意图)。
     *
     * ★ P0-5:调用方必须已持有 writeQueueLock(迭代器 remove 期间不能有其他线程 poll)。
     */
    private void dedupControlCommandInQueue(QueuedCommand command) {
        if (command == null || TextUtils.isEmpty(command.dedupKey)) {
            return;
        }
        java.util.Iterator<QueuedCommand> it = writeQueue.iterator();
        while (it.hasNext()) {
            QueuedCommand queued = it.next();
            if (command.dedupKey.equals(queued.dedupKey)) {
                it.remove();
            }
        }
    }

    /**
     * ★ 新增:从 JSON payload 里提取用户控制命令名(set_xxx 或 status_light 等)。
     * 找不到返回 null。
     */
    private String extractControlCmdName(String payload) {
        if (payload == null) {
            return null;
        }
        // 优先匹配 set_ 前缀
        int idx = payload.indexOf("\"cmd\":\"set_");
        if (idx >= 0) {
            int start = idx + 7;
            int end = payload.indexOf("\"", start);
            return (end > start) ? payload.substring(start, end) : null;
        }
        // 监护等级例外
        idx = payload.indexOf("\"cmd\":\"status_light\"");
        if (idx >= 0) {
            return "status_light";
        }
        // save_/upgrade_/set_bt_name
        int cmdIdx = payload.indexOf("\"cmd\":\"");
        if (cmdIdx >= 0) {
            int start = cmdIdx + 7;
            int end = payload.indexOf("\"", start);
            return (end > start) ? payload.substring(start, end) : null;
        }
        return null;
    }

    public void sendPayload(String payload) {
        sendCommand(payload);
    }

    @SuppressLint("MissingPermission")
    private void processWriteQueue() {
        if (bluetoothGatt == null || writeCharacteristic == null) {
            return;
        }
        long now = SystemClock.uptimeMillis();
        if (ignoreWriteCallbacksUntilMs > now) {
            handler.removeCallbacks(resumeWriteQueueRunnable);
            handler.postDelayed(resumeWriteQueueRunnable, ignoreWriteCallbacksUntilMs - now);
            return;
        }
        QueuedCommand command;
        synchronized (writeQueueLock) {
            if (writing) {
                return;
            }
            command = takeNextWritableCommandLocked();
            if (command == null) {
                return;
            }
            currentWrite = command;
            writing = true;
        }
        command.sentAtMs = SystemClock.uptimeMillis();
        lastSent = command.payload;
        if (command.environmentQuery && !TextUtils.isEmpty(command.zone)) {
            registerPendingEnvironmentRead(command);
        }
        try {
            int preferredType = getPreferredWriteType();
            boolean started = tryWrite(command.data, preferredType);
            if (!started) {
                int fallbackType = preferredType == BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE
                        ? BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
                        : BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE;
                addLog("首选写入方式启动失败，切换为 " + writeTypeName(fallbackType));
                started = tryWrite(command.data, fallbackType);
            }
            if (!started) {
                clearPendingEnvironmentRead(command, false);
                setError("TX failed: writeCharacteristic returned false");
                finishCurrentWrite(false);
            }
        } catch (Exception exception) {
            clearPendingEnvironmentRead(command, false);
            setError("TX failed: " + exception.getMessage());
            finishCurrentWrite(false);
        }
    }

    /** 调用方持有 writeQueueLock；跳过正在等待同指标设备响应的环境查询。 */
    private QueuedCommand takeNextWritableCommandLocked() {
        long now = SystemClock.uptimeMillis();
        java.util.Iterator<QueuedCommand> it = writeQueue.iterator();
        while (it.hasNext()) {
            QueuedCommand command = it.next();
            if (command.environmentQuery && !TextUtils.isEmpty(command.zone)
                    && isEnvironmentCommandBlocked(command.cmd, now)) {
                continue;
            }
            it.remove();
            return command;
        }
        return null;
    }

    private boolean isEnvironmentCommandBlocked(String cmd, long now) {
        synchronized (environmentReadLock) {
            if (pendingEnvironmentReads.containsKey(cmd)) {
                return true;
            }
            Long guardUntil = environmentGuardUntil.get(cmd);
            if (guardUntil != null) {
                if (guardUntil > now) {
                    return true;
                }
                environmentGuardUntil.remove(cmd);
            }
            return false;
        }
    }

    @SuppressLint("MissingPermission")
    private boolean tryWrite(byte[] data, int writeType) {
        if (missingBluetoothConnectPermission()) {
            return false;
        }
        writeCharacteristic.setWriteType(writeType);
        writeCharacteristic.setValue(data);
        boolean started = bluetoothGatt.writeCharacteristic(writeCharacteristic);
        if (started) {
            addLog("写入方式: " + writeTypeName(writeType));
            handler.removeCallbacks(writeTimeoutRunnable);
            handler.postDelayed(writeTimeoutRunnable, WRITE_CALLBACK_TIMEOUT_MS);
        }
        return started;
    }

    private void finishCurrentWrite(boolean success) {
        handler.removeCallbacks(writeTimeoutRunnable);
        QueuedCommand finished;
        synchronized (writeQueueLock) {
            finished = currentWrite;
            currentWrite = null;
            writing = false;
        }
        // 写回调超时并不代表设备未收到命令；环境查询上下文继续保留到设备响应超时。
        if (!success && finished != null && finished.sentAtMs <= 0L) {
            clearPendingEnvironmentRead(finished, false);
        }
        if (success) {
            addLog("写入完成" + (finished == null ? "" : " " + finished.dedupKey));
        }
        processWriteQueue();
        emit();
    }

    private void registerPendingEnvironmentRead(final QueuedCommand command) {
        if (command == null || !command.environmentQuery || TextUtils.isEmpty(command.zone)) {
            return;
        }
        final long generation = connectionGeneration;
        final long sentAt = SystemClock.uptimeMillis();
        final Runnable timeout = new Runnable() {
            @Override
            public void run() {
                boolean expired = false;
                synchronized (environmentReadLock) {
                    PendingEnvironmentRead current = pendingEnvironmentReads.get(command.cmd);
                    if (current != null && current.token == command.token
                            && current.generation == generation) {
                        pendingEnvironmentReads.remove(command.cmd);
                        environmentGuardUntil.put(command.cmd,
                                SystemClock.uptimeMillis() + ENV_LATE_RESPONSE_GUARD_MS);
                        expired = true;
                    }
                }
                if (!expired) {
                    return;
                }
                addLog("[ENV超时] " + command.cmd + " zone=" + command.zone);
                handler.postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        synchronized (environmentReadLock) {
                            Long until = environmentGuardUntil.get(command.cmd);
                            if (until != null && until <= SystemClock.uptimeMillis()) {
                                environmentGuardUntil.remove(command.cmd);
                            }
                        }
                        processWriteQueue();
                    }
                }, ENV_LATE_RESPONSE_GUARD_MS);
            }
        };
        PendingEnvironmentRead entry = new PendingEnvironmentRead(command.cmd, command.zone,
                command.token, generation, sentAt, timeout);
        synchronized (environmentReadLock) {
            pendingEnvironmentReads.put(command.cmd, entry);
        }
        handler.postDelayed(timeout, ENV_RESPONSE_TIMEOUT_MS);
        addLog("[ENV发送] " + command.cmd + " zone=" + command.zone + " #" + command.token);
    }

    private void clearPendingEnvironmentRead(QueuedCommand command, boolean lateGuard) {
        if (command == null || !command.environmentQuery) {
            return;
        }
        PendingEnvironmentRead removed = null;
        synchronized (environmentReadLock) {
            PendingEnvironmentRead current = pendingEnvironmentReads.get(command.cmd);
            if (current != null && current.token == command.token) {
                removed = pendingEnvironmentReads.remove(command.cmd);
                if (lateGuard) {
                    environmentGuardUntil.put(command.cmd,
                            SystemClock.uptimeMillis() + ENV_LATE_RESPONSE_GUARD_MS);
                }
            }
        }
        if (removed != null) {
            handler.removeCallbacks(removed.timeoutRunnable);
        }
        if (removed != null && lateGuard) {
            final String cmd = command.cmd;
            handler.postDelayed(new Runnable() {
                @Override
                public void run() {
                    synchronized (environmentReadLock) {
                        Long until = environmentGuardUntil.get(cmd);
                        if (until != null && until <= SystemClock.uptimeMillis()) {
                            environmentGuardUntil.remove(cmd);
                        }
                    }
                    processWriteQueue();
                }
            }, ENV_LATE_RESPONSE_GUARD_MS);
        }
    }

    /**
     * 优先使用回包 zone；缺失时按已实际发送且仍未超时的同指标查询归属。
     * 返回空串表示无法安全归属，调用方不得写实时缓存。
     */
    private String resolveEnvironmentResponseZone(JSONObject parsed, String cmd) {
        JSONObject data = parsed.optJSONObject("data");
        String rawZone = parsed.optString("zone", "");
        if (TextUtils.isEmpty(rawZone) && data != null) {
            rawZone = data.optString("zone", "");
        }
        String explicitZone = normalizeZone(rawZone);
        boolean invalidExplicitZone = !TextUtils.isEmpty(rawZone) && TextUtils.isEmpty(explicitZone);
        PendingEnvironmentRead pending;
        Long guardUntil;
        synchronized (environmentReadLock) {
            pending = pendingEnvironmentReads.remove(cmd);
            guardUntil = environmentGuardUntil.get(cmd);
        }
        if (pending != null) {
            handler.removeCallbacks(pending.timeoutRunnable);
        }
        boolean guardActive = guardUntil != null && guardUntil > SystemClock.uptimeMillis();
        String resolved = EnvironmentProtocol.resolveResponseZone(rawZone,
                pending == null ? "" : pending.zone,
                pending != null && pending.generation == connectionGeneration,
                TextUtils.isEmpty(rawZone) && guardActive);
        String source = !TextUtils.isEmpty(explicitZone) ? "payload" : "request";
        if (invalidExplicitZone) {
            addLog("[ENV拒绝] " + cmd + " 非法 zone=" + rawZone);
        } else if (TextUtils.isEmpty(resolved)) {
            if (guardActive) {
                addLog("[ENV拒绝] " + cmd + " 无 zone 的迟到回包");
            } else {
                addLog("[ENV拒绝] " + cmd + " 无 zone 且无匹配请求");
            }
        } else if (!TextUtils.isEmpty(explicitZone) && pending != null
                && !explicitZone.equals(pending.zone)) {
            addLog("[ENV舱位不一致] " + cmd + " request=" + pending.zone
                    + " response=" + explicitZone);
        }
        if (!TextUtils.isEmpty(resolved)) {
            long latency = pending == null ? -1L : SystemClock.uptimeMillis() - pending.sentAtMs;
            addLog("[ENV归属] " + cmd + " zone=" + resolved + " via=" + source
                    + (latency < 0L ? "" : " " + latency + "ms"));
        }
        processWriteQueue();
        return resolved;
    }

    private int getPreferredWriteType() {
        if (writeCharacteristic == null) {
            return BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT;
        }
        int properties = writeCharacteristic.getProperties();
        boolean canWriteNoResponse = (properties & BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE) != 0;
        boolean canWriteWithResponse = (properties & BluetoothGattCharacteristic.PROPERTY_WRITE) != 0;
        if (canWriteNoResponse) {
            return BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE;
        }
        if (canWriteWithResponse) {
            return BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT;
        }
        return BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT;
    }

    private String writeTypeName(int writeType) {
        if (writeType == BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE) {
            return "WriteWithoutResponse";
        }
        if (writeType == BluetoothGattCharacteristic.WRITE_TYPE_SIGNED) {
            return "SignedWrite";
        }
        return "WriteWithResponse";
    }

    private String describeProperties(BluetoothGattCharacteristic characteristic) {
        if (characteristic == null) {
            return "--";
        }
        int properties = characteristic.getProperties();
        ArrayList<String> names = new ArrayList<>();
        if ((properties & BluetoothGattCharacteristic.PROPERTY_READ) != 0) {
            names.add("read");
        }
        if ((properties & BluetoothGattCharacteristic.PROPERTY_WRITE) != 0) {
            names.add("write");
        }
        if ((properties & BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE) != 0) {
            names.add("writeWithoutResponse");
        }
        if ((properties & BluetoothGattCharacteristic.PROPERTY_NOTIFY) != 0) {
            names.add("notify");
        }
        if ((properties & BluetoothGattCharacteristic.PROPERTY_INDICATE) != 0) {
            names.add("indicate");
        }
        return names.isEmpty() ? "0x" + Integer.toHexString(properties) : TextUtils.join(", ", names);
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
        if (TextUtils.isEmpty(name)) {
            return;
        }
        if (TextUtils.isEmpty(address)) {
            return;
        }

        DeviceItem item = new DeviceItem(address, name, result.getRssi());
        // ★ 0908：扫描到的名称落盘，之后按 MAC 直连（自动重连/记忆设备）时仍能显示名称。
        rememberDeviceName(address, name);
        // ★ P0-5:onDeviceFound 跑在 BLE binder 线程,getDevices() 在主线程,
        //   并发 add/sort 与拷贝会抛 CME。
        boolean isNew;
        synchronized (devicesLock) {
            int index = indexOfDevice(item.deviceId);
            isNew = index < 0;
            if (isNew) {
                devices.add(item);
            } else {
                devices.set(index, item);
            }
            Collections.sort(devices, (left, right) -> Integer.compare(right.rssi, left.rssi));
        }
        if (isNew) {
            addLog("发现: " + name);
        }
        emit();
    }

    private String findDeviceName(String address) {
        synchronized (devicesLock) {
            for (DeviceItem item : devices) {
                if (item.deviceId.equals(address)) {
                    return item.deviceName;
                }
            }
        }
        return "";
    }

    /**
     * ★ 0908：连接状态页“第一次能显示名称、之后只剩物理地址”的修复。
     *
     * 原因：只从本次扫描结果 devices 里找名称。首次进入是先扫描再连接，能找到；
     * 之后走自动重连/记忆设备直接按 MAC 连接，没有扫描列表，名称就成了空字符串，
     * 面板只好显示物理地址。
     *
     * 现在按优先级取名：本次扫描结果 → 已配对/系统缓存名称 → 本地记住的名称。
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
        return "device_name_" + address;
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

    /** ★ P0-5:调用方必须已持有 devicesLock。 */
    private int indexOfDevice(String address) {
        for (int i = 0; i < devices.size(); i++) {
            if (devices.get(i).deviceId.equals(address)) {
                return i;
            }
        }
        return -1;
    }

    @SuppressLint("MissingPermission")
    private void startServiceDiscovery() {
        if (bluetoothGatt == null || protocolReady || connected) {
            return;
        }
        if (serviceDiscoveryAttempts >= MAX_SERVICE_DISCOVERY_ATTEMPTS) {
            connectionStateText = "发现服务失败";
            setError("发现服务超时，请断开后重新连接，或确认设备支持 A002/C304/C305");
            return;
        }
        serviceDiscoveryAttempts++;
        connectionStateText = "发现服务中 " + serviceDiscoveryAttempts + "/" + MAX_SERVICE_DISCOVERY_ATTEMPTS;
        addLog("开始发现服务 " + serviceDiscoveryAttempts + "/" + MAX_SERVICE_DISCOVERY_ATTEMPTS);
        boolean started;
        try {
            started = bluetoothGatt.discoverServices();
        } catch (Exception exception) {
            started = false;
            addLog("发现服务异常: " + exception.getMessage());
        }
        if (!started) {
            if (serviceDiscoveryAttempts < MAX_SERVICE_DISCOVERY_ATTEMPTS) {
                handler.postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        startServiceDiscovery();
                    }
                }, SERVICE_DISCOVERY_DELAY_MS);
            } else {
                connectionStateText = "发现服务失败";
                setError("发现服务启动失败，请断开后重新连接");
            }
            emit();
            return;
        }
        handler.removeCallbacks(serviceDiscoveryTimeoutRunnable);
        handler.postDelayed(serviceDiscoveryTimeoutRunnable, SERVICE_DISCOVERY_TIMEOUT_MS);
        emit();
    }

    private final BluetoothGattCallback gattCallback = new BluetoothGattCallback() {
        @Override
        public void onConnectionStateChange(@NonNull BluetoothGatt gatt, int status, int newState) {
            if (gatt != bluetoothGatt) {
                addLog("忽略旧 GATT 连接状态回调 state=" + newState);
                if (newState == BluetoothGatt.STATE_DISCONNECTED) {
                    try {
                        gatt.close();
                    } catch (Throwable ignored) {
                    }
                }
                return;
            }
            if (newState == BluetoothGatt.STATE_CONNECTED) {
                connected = false;
                protocolReady = false;
                serviceDiscoveryAttempts = 0;
                notifyEnableAttempts = 0;
                connectionStateText = "发现服务中";
                addLog("GATT 连接成功");
                emit();
                handler.postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        startServiceDiscovery();
                    }
                }, SERVICE_DISCOVERY_DELAY_MS);
                return;
            }
            if (newState == BluetoothGatt.STATE_CONNECTING) {
                connectionStateText = "连接中";
                emit();
                return;
            }
            if (newState == BluetoothGatt.STATE_DISCONNECTING) {
                connectionStateText = "断开中";
                emit();
                return;
            }
            connected = false;
            protocolReady = false;
            stopAutoUpdate();
            handler.removeCallbacks(serviceDiscoveryTimeoutRunnable);
            handler.removeCallbacks(mtuReadyTimeoutRunnable);
            handler.removeCallbacks(protocolReadyFallbackRunnable);
            serviceDiscoveryAttempts = 0;
            notifyEnableAttempts = 0;
            connectionStateText = "已断开";
            addLog("GATT 已断开");
            writeCharacteristic = null;
            notifyCharacteristic = null;
            int dropped = resetTransportState(true);
            // ★ P1-13:意外掉线时丢弃的命令同样要提示,不能静默
            if (dropped > 0) {
                addLog("[P1-13] 掉线时丢弃了 " + dropped + " 条未发送的命令");
            }
            handler.removeCallbacks(writeTimeoutRunnable);
            // ★ 修复 P0-4:断开时 close GATT 并置空,避免 GATT 句柄泄漏导致连接数耗尽
            try {
                if (gatt != null) {
                    gatt.close();
                }
            } catch (Throwable ignored) {}
            bluetoothGatt = null;
            // ★ P1-15:意外掉线(非用户主动断开)时安排自动重连
            if (!userDisconnected) {
                addLog("[P1-15] 意外掉线，准备自动重连");
                scheduleReconnect();
            }
            emit();
        }

        @Override
        public void onMtuChanged(@NonNull BluetoothGatt gatt, int mtu, int status) {
            if (gatt != bluetoothGatt) {
                return;
            }
            handler.removeCallbacks(mtuReadyTimeoutRunnable);
            addLog("MTU " + mtu + " status " + status);
            if (!protocolReady && writeCharacteristic != null && notifyCharacteristic != null) {
                enableNotifyChannel();
            }
            emit();
        }

        @SuppressLint("MissingPermission")
        @Override
        public void onServicesDiscovered(@NonNull BluetoothGatt gatt, int status) {
            if (gatt != bluetoothGatt) {
                return;
            }
            handler.removeCallbacks(serviceDiscoveryTimeoutRunnable);
            if (status != BluetoothGatt.GATT_SUCCESS) {
                if (serviceDiscoveryAttempts < MAX_SERVICE_DISCOVERY_ATTEMPTS) {
                    addLog("发现服务失败 " + status + "，正在重试");
                    startServiceDiscovery();
                } else {
                    connectionStateText = "发现服务失败";
                    setError("发现服务失败: " + status);
                }
                return;
            }
            try {
                if (missingBluetoothConnectPermission()) {
                    setError("缺少蓝牙连接权限，请先申请权限");
                    return;
                }
                gatt.requestMtu(ICU_MTU);
            } catch (Exception ignored) {
            }
            BluetoothGattService service = gatt.getService(ICU_SERVICE_UUID);
            if (service == null) {
                connectionStateText = "协议不匹配";
                setError("未找到协议服务: " + ICU_SERVICE_UUID);
                return;
            }
            notifyCharacteristic = service.getCharacteristic(ICU_NOTIFY_UUID);
            writeCharacteristic = service.getCharacteristic(ICU_WRITE_UUID);
            if (notifyCharacteristic == null) {
                connectionStateText = "协议不匹配";
                setError("未找到 TX/Notify 特征: " + ICU_NOTIFY_UUID);
                return;
            }
            if (writeCharacteristic == null) {
                connectionStateText = "协议不匹配";
                setError("未找到 RX/Write 特征: " + ICU_WRITE_UUID);
                return;
            }
            addLog("RX Write 已保存: " + ICU_WRITE_UUID);
            addLog("C305 属性: " + describeProperties(notifyCharacteristic));
            addLog("C304 属性: " + describeProperties(writeCharacteristic));
            try {
                notifyEnableAttempts = 0;
                boolean mtuStarted = gatt.requestMtu(ICU_MTU);
                if (mtuStarted) {
                    addLog("请求 MTU " + ICU_MTU);
                    handler.removeCallbacks(mtuReadyTimeoutRunnable);
                    handler.postDelayed(mtuReadyTimeoutRunnable, MTU_READY_TIMEOUT_MS);
                } else {
                    addLog("MTU 请求未启动，直接启用 Notify");
                    enableNotifyChannel();
                }
            } catch (Exception exception) {
                addLog("MTU 请求异常: " + exception.getMessage());
                enableNotifyChannel();
            }
        }

        @Override
        public void onDescriptorWrite(@NonNull BluetoothGatt gatt, @NonNull BluetoothGattDescriptor descriptor, int status) {
            if (gatt != bluetoothGatt) {
                return;
            }
            handler.removeCallbacks(protocolReadyFallbackRunnable);
            if (status == BluetoothGatt.GATT_SUCCESS) {
                markProtocolReady();
                return;
            }
            if (notifyEnableAttempts < MAX_NOTIFY_ENABLE_ATTEMPTS) {
                addLog("Notify descriptor 写入失败 " + status + "，正在重试");
                enableNotifyChannel();
                return;
            }
            connectionStateText = "Notify失败";
            setError("Notify descriptor 写入失败: " + status);
        }

        @Override
        public void onCharacteristicWrite(@NonNull BluetoothGatt gatt, @NonNull BluetoothGattCharacteristic characteristic, int status) {
            if (gatt != bluetoothGatt) {
                addLog("忽略旧连接的迟到写回调");
                return;
            }
            if (SystemClock.uptimeMillis() < ignoreWriteCallbacksUntilMs) {
                addLog("忽略上一条命令的迟到写回调");
                return;
            }
            if (status == BluetoothGatt.GATT_SUCCESS) {
                finishCurrentWrite(true);
            } else {
                setError("写入失败: " + status);
                finishCurrentWrite(false);
            }
        }

        @Override
        public void onCharacteristicChanged(@NonNull BluetoothGatt gatt, @NonNull BluetoothGattCharacteristic characteristic) {
            handleCharacteristicChanged(gatt, characteristic, characteristic.getValue());
        }

        @Override
        public void onCharacteristicChanged(@NonNull BluetoothGatt gatt, @NonNull BluetoothGattCharacteristic characteristic, @NonNull byte[] value) {
            handleCharacteristicChanged(gatt, characteristic, value);
        }
    };

    private void markProtocolReady() {
        if (protocolReady) {
            return;
        }
        connected = true;
        protocolReady = true;
        // ★ P1-15:协议通道就绪即视为重连成功,清空退避计数,
        //   下次掉线重新从 2 秒开始退避。
        reconnectAttempts = 0;
        handler.removeCallbacks(reconnectRunnable);
        handler.removeCallbacks(serviceDiscoveryTimeoutRunnable);
        handler.removeCallbacks(mtuReadyTimeoutRunnable);
        handler.removeCallbacks(protocolReadyFallbackRunnable);
        connectionStateText = "已连接";
        lastError = "";
        addLog("协议通道已就绪");
        // ★ 0908：连接就绪后再补一次名称。按 MAC 直连（自动重连/记忆设备）时
        //   连接瞬间往往还拿不到名称，此时 GATT 已建立，getName() 通常可用。
        if (TextUtils.isEmpty(connectedDeviceName) && bluetoothGatt != null) {
            String resolved = resolveDeviceName(connectedDeviceId, bluetoothGatt.getDevice());
            if (!TextUtils.isEmpty(resolved)) {
                connectedDeviceName = resolved;
                addLog("已补全设备名称: " + resolved);
            }
        }
        handler.postDelayed(new Runnable() {
            @Override
            public void run() {
                readAllStatus();
                startAutoUpdate();
            }
        }, 500L);
        emit();
    }

    @SuppressLint("MissingPermission")
    private void enableNotifyChannel() {
        if (protocolReady || bluetoothGatt == null || notifyCharacteristic == null) {
            return;
        }
        if (missingBluetoothConnectPermission()) {
            setError("缺少蓝牙连接权限，请先申请权限");
            return;
        }
        notifyEnableAttempts++;
        connectionStateText = "启用Notify中 " + notifyEnableAttempts + "/" + MAX_NOTIFY_ENABLE_ATTEMPTS;
        addLog("启用 C305 Notify " + notifyEnableAttempts + "/" + MAX_NOTIFY_ENABLE_ATTEMPTS);
        try {
            boolean notificationStarted = bluetoothGatt.setCharacteristicNotification(notifyCharacteristic, true);
            if (!notificationStarted) {
                connectionStateText = "Notify失败";
                setError("Notify 本地开关启动失败");
                return;
            }
            BluetoothGattDescriptor descriptor = notifyCharacteristic.getDescriptor(CLIENT_CONFIG_UUID);
            if (descriptor == null) {
                addLog("Notify 无 2902 descriptor，仅开启本地通知");
                markProtocolReady();
                return;
            }
            int properties = notifyCharacteristic.getProperties();
            if ((properties & BluetoothGattCharacteristic.PROPERTY_INDICATE) != 0
                    && (properties & BluetoothGattCharacteristic.PROPERTY_NOTIFY) == 0) {
                descriptor.setValue(BluetoothGattDescriptor.ENABLE_INDICATION_VALUE);
                addLog("C305 使用 Indicate descriptor");
            } else {
                descriptor.setValue(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE);
                addLog("C305 使用 Notify descriptor");
            }
            boolean descriptorWriteStarted = bluetoothGatt.writeDescriptor(descriptor);
            if (descriptorWriteStarted) {
                handler.removeCallbacks(protocolReadyFallbackRunnable);
                handler.postDelayed(protocolReadyFallbackRunnable, NOTIFY_READY_TIMEOUT_MS);
                emit();
                return;
            }
            if (notifyEnableAttempts < MAX_NOTIFY_ENABLE_ATTEMPTS) {
                addLog("Notify descriptor 写入未启动，正在重试");
                enableNotifyChannel();
                return;
            }
            connectionStateText = "Notify失败";
            setError("Notify descriptor 写入未启动");
        } catch (Exception exception) {
            connectionStateText = "Notify失败";
            setError("Notify 启用异常: " + exception.getMessage());
        }
    }

    private void handleCharacteristicChanged(BluetoothGatt sourceGatt,
                                             BluetoothGattCharacteristic characteristic, byte[] data) {
        if (sourceGatt == null || sourceGatt != bluetoothGatt) {
            addLog("忽略旧连接的迟到 Notify");
            return;
        }
        if (characteristic == null || !ICU_NOTIFY_UUID.equals(characteristic.getUuid()) || data == null) {
            return;
        }
        String chunk = new String(data, StandardCharsets.UTF_8);
        ArrayList<String> messages = appendNotifyChunk(chunk);
        if (messages.isEmpty()) {
            addLog("收到分片 " + data.length + " bytes，等待完整 JSON");
        } else {
            for (String message : messages) {
                lastReceived = message;
                lastError = "";
                lastParsedStatus = parseStatus(message);
                addLog("收到完整消息 " + message.getBytes(StandardCharsets.UTF_8).length + " bytes: " + message);
            }
        }
        emit();
    }

    private ArrayList<String> appendNotifyChunk(String chunk) {
        ArrayList<String> messages = new ArrayList<>();
        synchronized (notifyBuffer) {
            notifyBuffer.append(chunk);
            while (notifyBuffer.length() > 0) {
                int start = notifyBuffer.indexOf("{");
                if (start < 0) {
                    if (notifyBuffer.length() > MAX_NOTIFY_BUFFER_CHARS) {
                        notifyBuffer.setLength(0);
                        addLog("Notify 缓冲区没有 JSON 起始符，已清空");
                    }
                    break;
                }
                if (start > 0) {
                    notifyBuffer.delete(0, start);
                }

                int depth = 0;
                boolean inString = false;
                boolean escaped = false;
                int end = -1;
                for (int i = 0; i < notifyBuffer.length(); i++) {
                    char value = notifyBuffer.charAt(i);
                    if (inString) {
                        if (escaped) {
                            escaped = false;
                        } else if (value == '\\') {
                            escaped = true;
                        } else if (value == '"') {
                            inString = false;
                        }
                        continue;
                    }
                    if (value == '"') {
                        inString = true;
                    } else if (value == '{') {
                        depth++;
                    } else if (value == '}') {
                        depth--;
                        if (depth == 0) {
                            end = i;
                            break;
                        }
                    }
                }
                if (end < 0) {
                    if (notifyBuffer.length() > MAX_NOTIFY_BUFFER_CHARS) {
                        notifyBuffer.setLength(0);
                        addLog("Notify JSON 超过最大长度，已清空");
                    }
                    break;
                }
                messages.add(notifyBuffer.substring(0, end + 1));
                notifyBuffer.delete(0, end + 1);
            }
        }
        return messages;
    }

    private void startAutoUpdate() {
        stopAutoUpdate();
        autoUpdateEnabled = true;
        handler.postDelayed(autoUpdateRunnable, AUTO_UPDATE_MS);
        // ★ 0904 问题2:环境数据 3s 轻量轮询 + 完整状态 30s 兜底
        handler.postDelayed(envUpdateRunnable, ENV_UPDATE_MS);
        // ★ 定时控件倒计时以主机剩余时间为准，5s 校时一次
        handler.postDelayed(timedSyncRunnable, TIMED_SYNC_MS);
        addLog("状态自动更新已开启 (环境数据每 " + (ENV_UPDATE_MS / 1000L)
                + " 秒,完整状态每 " + (AUTO_UPDATE_MS / 1000L) + " 秒)");
    }

    private void stopAutoUpdate() {
        autoUpdateEnabled = false;
        handler.removeCallbacks(autoUpdateRunnable);
        handler.removeCallbacks(timedSyncRunnable);
        handler.removeCallbacks(envUpdateRunnable);
    }

    private String parseStatus(String message) {
        updateStatusFromJson(message);
        String cmd = extractJsonString(message, "cmd");
        String ret = extractJsonString(message, "ret");
        if ("get_all_status".equals(cmd)) {
            if (!"ok".equals(ret)) {
                return cmd + (TextUtils.isEmpty(ret) ? "" : " -> " + ret);
            }
            String temp = extractNestedInt(message, "temp", "current");
            String o2 = extractNestedInt(message, "o2", "current");
            String humidity = extractJsonNumber(message, "humidity");
            String co2 = extractJsonNumber(message, "co2");
            StringBuilder builder = new StringBuilder("get_all_status");
            if (!TextUtils.isEmpty(ret)) {
                builder.append(" -> ").append(ret);
            }
            if (!TextUtils.isEmpty(temp)) {
                builder.append(" 温度").append(formatDiv10(temp));
            }
            if (!TextUtils.isEmpty(o2)) {
                builder.append(" 氧").append(formatDiv10(o2));
            }
            if (!TextUtils.isEmpty(humidity)) {
                builder.append(" 湿度").append(formatDiv10(humidity));
            }
            if (!TextUtils.isEmpty(co2)) {
                builder.append(" CO2 ").append(co2);
            }
            return builder.toString();
        }
        String value = extractJsonNumber(message, "value");
        if (!TextUtils.isEmpty(cmd)) {
            if ("get_temp".equals(cmd) || "get_o2".equals(cmd) || "get_humidity".equals(cmd)) {
                return cmd + " -> " + formatMaybeDiv10(value);
            }
            if (("set_temp".equals(cmd) || "set_o2".equals(cmd) || "set_humidity".equals(cmd)) && "ok".equals(ret)) {
                return cmd + " -> " + formatMaybeDiv10(value);
            }
            if ("get_bt_ver".equals(cmd)) {
                String version = parsedVersionValue(message);
                if (!TextUtils.isEmpty(version)) {
                    return cmd + " -> " + version;
                }
            }
            if (!TextUtils.isEmpty(value)) {
                return cmd + " -> " + value;
            }
            return cmd + (TextUtils.isEmpty(ret) ? "" : " -> " + ret);
        }
        return message;
    }

    private String resolveCabinResponseZone(JSONObject parsed, String cmd) {
        String explicit = normalizeZone(parsed.optString("zone", ""));
        if (!TextUtils.isEmpty(explicit)) {
            return explicit;
        }
        PendingControlEntry pending = findPendingControl(cmd, "");
        if (pending != null) {
            return pending.zone;
        }
        synchronized (writeQueueLock) {
            if (currentWrite != null && cmd.equals(currentWrite.cmd)
                    && !TextUtils.isEmpty(currentWrite.zone)) {
                return currentWrite.zone;
            }
        }
        return "";
    }

    private void runWithZoneProjection(String zone, Runnable update) {
        String normalized = normalizeZone(zone);
        if (TextUtils.isEmpty(normalized)) {
            addLog("[分舱] 回包缺少可验证 zone，拒绝更新");
            return;
        }
        String visibleZone = currentZone;
        if (visibleZone.equals(normalized)) {
            update.run();
            saveCurrentZoneProjection();
            return;
        }
        saveCurrentZoneProjection();
        currentZone = normalized;
        loadZoneProjection();
        try {
            update.run();
            saveCurrentZoneProjection();
        } finally {
            currentZone = visibleZone;
            loadZoneProjection();
        }
    }

    private void applyControlResponseToZone(final String zone, final Runnable update) {
        runWithZoneProjection(zone, update);
    }

    private String requireCabinResponseZone(JSONObject parsed, String cmd) {
        String zone = resolveCabinResponseZone(parsed, cmd);
        if (TextUtils.isEmpty(zone)) {
            addLog("[分舱] " + cmd + " 回包无法确定舱位，忽略");
        }
        return zone;
    }

    private void applyAllStatusData(JSONObject data, String responseZone) {
        JSONObject temp = data.optJSONObject("temp");
        if (temp != null) {
            updateEnvironmentTemperature(responseZone,
                    valueMaybeDiv10(temp, "current", environmentForZone(responseZone).temperature));
            // ★ 协议修复：文档 get_all_status 温度目标字段为 "target"（非 "set"），兼容两者。
            String tempTargetKey = temp.has("set") ? "set" : (temp.has("target") ? "target" : null);
            if (tempTargetKey != null) {
                lastSetCabinTemp = valueMaybeDiv10(temp, tempTargetKey, lastSetCabinTemp);
            }
            if (temp.has("status")) {
                tempEnabled = "on".equals(temp.optString("status", ""));
            }
        }
        // ★ 协议修复(对照优利特 V1.00)：湿度为 decimal(%)，原 valueDiv10 会 ÷10 显示成 1/10。
        //   改用 valueMaybeDiv10（>100 才 ÷10），同时兼容 decimal(55.2) 与 ×10(552) 两种约定。
        if (data.has("humidity")) {
            updateEnvironmentHumidity(responseZone,
                    valueMaybeDiv10(data, "humidity", environmentForZone(responseZone).humidity));
        }
        // ★ 协议修复：文档 get_all_status 返回键为 "oxygen"（非 "o2"），浓度字段为 "concentration"（非 "current"）。
        //   同时兼容两种键名/字段名，并用 valueMaybeDiv10 避免 ÷10 错误。
        JSONObject o2 = data.optJSONObject("o2");
        if (o2 == null) {
            o2 = data.optJSONObject("oxygen");
        }
        if (o2 != null) {
            Float o2Value = valueMaybeDiv10(o2, "current", null);
            if (o2Value == null) {
                o2Value = valueMaybeDiv10(o2, "concentration", null);
            }
            if (o2Value != null) {
                updateEnvironmentOxygen(responseZone, o2Value);
            }
            String o2TargetKey = o2.has("set") ? "set" : (o2.has("target") ? "target" : null);
            if (o2TargetKey != null) {
                Float targetOxygen = valueMaybeDiv10(o2, o2TargetKey, getLastSetOxygen());
                if ("left".equals(responseZone)) {
                    lastSetOxygenLeft = targetOxygen;
                } else {
                    lastSetOxygenRight = targetOxygen;
                }
            }
            if (o2.has("status")) {
                o2Enabled = "on".equals(o2.optString("status", ""));
            }
        }
        JSONObject co2 = data.optJSONObject("co2");
        if (co2 != null) {
            updateEnvironmentCo2(responseZone, co2.optInt("current", environmentForZone(responseZone).co2 == null
                    ? 0 : environmentForZone(responseZone).co2));
            checkCo2Alarm(responseZone);
            if (co2.has("set")) {
                if ("left".equals(responseZone)) {
                    lastSetCo2Left = co2.optInt("set", lastSetCo2Left == null ? 0 : lastSetCo2Left);
                } else {
                    lastSetCo2Right = co2.optInt("set", lastSetCo2Right == null ? 0 : lastSetCo2Right);
                }
            }
            if (co2.has("status")) {
                co2Enabled = "on".equals(co2.optString("status", ""));
            }
        } else if (data.has("co2")) {
            updateEnvironmentCo2(responseZone, data.optInt("co2"));
            checkCo2Alarm(responseZone);
        }
        if (data.has("status_light")) {
            statusLightColor = normalizeStatusLightColor(data.optString("status_light", statusLightColor));
        }
        updateTimedSwitch(data.optJSONObject("uv"), "uv");
        updateTimedSwitch(data.optJSONObject("blue_light"), "blue");
        updateTimedSwitch(data.optJSONObject("red_light"), "red");
        updateTimedSwitch(data.optJSONObject("nebulizer"), "nebulizer");
        updateTimedSwitch(data.optJSONObject("anion"), "anion");
        if (data.has("inner_cycle")) innerCycle = "on".equals(data.optString("inner_cycle", ""));
        if (data.has("outer_cycle")) outerCycle = "on".equals(data.optString("outer_cycle", ""));
        if (data.has("cold_light")) coldLight = "on".equals(data.optString("cold_light", ""));
        if (data.has("warm_light")) warmLight = "on".equals(data.optString("warm_light", ""));
        saveCurrentZoneProjection();
    }

    private boolean isCabinResponseCommand(String cmd) {
        return (cmd.startsWith("set_") || cmd.startsWith("get_") || "status_light".equals(cmd))
                && !"set_bt_name".equals(cmd)
                && !"get_main_version".equals(cmd)
                && !"get_ctrl_version".equals(cmd)
                && !"get_display_version".equals(cmd)
                && !"get_other_version".equals(cmd)
                && !"get_bt_ver".equals(cmd)
                && !"get_bt_version".equals(cmd)
                && !cmd.startsWith("set_infrared_")
                && !cmd.startsWith("get_infrared_");
    }

    private void updateStatusFromJson(String message) {
        try {
            final JSONObject parsed = new JSONObject(message);
            final String cmd = parsed.optString("cmd", "");
            if ("get_all_status".equals(cmd)) {
                updateStatusFromJsonInCurrentZone(message);
                return;
            }
            if (isCabinResponseCommand(cmd)) {
                final String zone = EnvironmentProtocol.isEnvironmentResponseCommand(cmd)
                        ? resolveEnvironmentResponseZone(parsed, cmd)
                        : requireCabinResponseZone(parsed, cmd);
                if (TextUtils.isEmpty(zone)) {
                    return;
                }
                parsed.put("zone", zone);
                if (EnvironmentProtocol.isEnvironmentResponseCommand(cmd)) {
                    updateStatusFromJsonInCurrentZone(parsed.toString());
                    return;
                }
                runWithZoneProjection(zone, new Runnable() {
                    @Override
                    public void run() {
                        updateStatusFromJsonInCurrentZone(parsed.toString());
                    }
                });
                return;
            }
        } catch (JSONException ignored) {
        }
        updateStatusFromJsonInCurrentZone(message);
    }

    private void updateStatusFromJsonInCurrentZone(String message) {
        try {
            JSONObject parsed = new JSONObject(message);
            String cmd = parsed.optString("cmd", "");
            if ("get_all_status".equals(cmd) && "ok".equals(parsed.optString("ret", "")) && parsed.has("data")) {
                final String responseZone = resolveEnvironmentResponseZone(parsed, cmd);
                final JSONObject data = parsed.optJSONObject("data");
                if (data == null || TextUtils.isEmpty(responseZone)) {
                    addLog("[分舱] get_all_status 无法确定舱位，忽略");
                    return;
                }
                runWithZoneProjection(responseZone, new Runnable() {
                    @Override
                    public void run() {
                        applyAllStatusData(data, responseZone);
                    }
                });
                emit();
                return;
            }

            if ("get_temp".equals(cmd) && parsed.has("value")) {
                String zone = resolveEnvironmentResponseZone(parsed, cmd);
                if (!TextUtils.isEmpty(zone)) {
                    updateEnvironmentTemperature(zone,
                            valueMaybeDiv10(parsed, "value", environmentForZone(zone).temperature));
                }
                return;
            }
            if ("set_temp".equals(cmd) && "ok".equals(parsed.optString("ret", ""))) {
                verifyControlPending("set_temp", parsed);
                sendCommand("{\"cmd\":\"get_temp\",\"zone\":\"" + currentZone + "\"}");
                return;
            }
            if ("set_temp_enable".equals(cmd) && "ok".equals(parsed.optString("ret", ""))) {
                tempEnabled = "on".equals(parsed.optString("enable", onOff(Boolean.TRUE.equals(tempEnabled))));
                verifyControlPending("set_temp_enable", parsed);
                return;
            }
            if ("get_o2".equals(cmd) && parsed.has("value")) {
                String zone = resolveEnvironmentResponseZone(parsed, cmd);
                if (!TextUtils.isEmpty(zone)) {
                    updateEnvironmentOxygen(zone,
                            valueMaybeDiv10(parsed, "value", environmentForZone(zone).oxygen));
                }
                return;
            }
            if ("set_o2".equals(cmd) && "ok".equals(parsed.optString("ret", ""))) {
                verifyControlPending("set_o2", parsed);
                sendCommand("{\"cmd\":\"get_o2\",\"zone\":\"" + currentZone + "\"}");
                return;
            }
            if ("set_o2_enable".equals(cmd) && "ok".equals(parsed.optString("ret", ""))) {
                o2Enabled = "on".equals(parsed.optString("enable", onOff(Boolean.TRUE.equals(o2Enabled))));
                verifyControlPending("set_o2_enable", parsed);
                return;
            }
            if ("get_humidity".equals(cmd) && parsed.has("value")) {
                String zone = resolveEnvironmentResponseZone(parsed, cmd);
                if (!TextUtils.isEmpty(zone)) {
                    updateEnvironmentHumidity(zone,
                            valueMaybeDiv10(parsed, "value", environmentForZone(zone).humidity));
                }
                return;
            }
            if ("set_humidity".equals(cmd) && "ok".equals(parsed.optString("ret", ""))) {
                verifyControlPending("set_humidity", parsed);
                sendCommand("{\"cmd\":\"get_humidity\",\"zone\":\"" + currentZone + "\"}");
                return;
            }
            if ("get_co2".equals(cmd) && parsed.has("value")) {
                String zone = resolveEnvironmentResponseZone(parsed, cmd);
                if (!TextUtils.isEmpty(zone)) {
                    updateEnvironmentCo2(zone, parsed.optInt("value"));
                    checkCo2Alarm(zone);
                }
                return;
            }
            if ("set_co2".equals(cmd) && "ok".equals(parsed.optString("ret", ""))) {
                verifyControlPending("set_co2", parsed);
                sendCommand("{\"cmd\":\"get_co2\",\"zone\":\"" + currentZone + "\"}");
                return;
            }
            if ("set_co2_enable".equals(cmd) && "ok".equals(parsed.optString("ret", ""))) {
                co2Enabled = switchValueFromResponse(parsed, co2Enabled);
                verifyControlPending("set_co2_enable", parsed);
                return;
            }
            if ("ok".equals(parsed.optString("ret", ""))) {
                if ("set_cold_light".equals(cmd)) {
                    coldLight = switchValueFromResponse(parsed, coldLight);
                    verifyControlPending("set_cold_light", parsed);
                    return;
                }
                if ("set_warm_light".equals(cmd)) {
                    warmLight = switchValueFromResponse(parsed, warmLight);
                    verifyControlPending("set_warm_light", parsed);
                    return;
                }
                if ("set_red_enable".equals(cmd)) {
                    redTherapy = switchValueFromResponse(parsed, redTherapy);
                    verifyControlPending("set_red_enable", parsed);
                    return;
                }
                if ("set_blue_enable".equals(cmd)) {
                    blueTherapy = switchValueFromResponse(parsed, blueTherapy);
                    verifyControlPending("set_blue_enable", parsed);
                    return;
                }
                if ("set_outer_cycle".equals(cmd)) {
                    outerCycle = switchValueFromResponse(parsed, outerCycle);
                    verifyControlPending("set_outer_cycle", parsed);
                    return;
                }
                if ("set_inner_cycle".equals(cmd)) {
                    innerCycle = switchValueFromResponse(parsed, innerCycle);
                    verifyControlPending("set_inner_cycle", parsed);
                    return;
                }
                if ("set_nebulizer_enable".equals(cmd)) {
                    nebulizer = switchValueFromResponse(parsed, nebulizer);
                    verifyControlPending("set_nebulizer_enable", parsed);
                    return;
                }
                if ("set_anion_enable".equals(cmd)) {
                    anion = switchValueFromResponse(parsed, anion);
                    verifyControlPending("set_anion_enable", parsed);
                    return;
                }
                if ("set_uv_enable".equals(cmd)) {
                    uv = switchValueFromResponse(parsed, uv);
                    verifyControlPending("set_uv_enable", parsed);
                    return;
                }
            }
            if ("set_care_mode".equals(cmd) && "ok".equals(parsed.optString("ret", ""))) {
                return;
            }
            if ("save_care_mode_set".equals(cmd) && "ok".equals(parsed.optString("ret", ""))) {
                return;
            }
            if ("get_status_light".equals(cmd) && parsed.has("color")) {
                // ★ P1-2:get_status_light 响应处理(可能由主动校验或后台轮询触发)
                String responseColor = parsed.optString("color", "");
                String responseZone = parsed.optString("zone", "");
                // zone 校验:防止响应是其他舱的
                if (!TextUtils.isEmpty(responseZone) && !TextUtils.isEmpty(currentZone)
                        && !currentZone.equals(responseZone)) {
                    return;
                }
                String normalized = normalizeStatusLightColor(responseColor);
                statusLightColor = normalized;
                // 校验 pending(如果有):设备实际值 ≠ 我们发出的 → 设备否决
                verifyControlPending("status_light", parsed);
                return;
            }
            if (("status_light".equals(cmd) || "set_status_light".equals(cmd))
                    && "ok".equals(parsed.optString("ret", ""))) {
                // ★ P1-2:status_light 响应处理(走通用 pending 校验)
                String responseColor = parsed.optString("color", "");
                String responseZone = parsed.optString("zone", "");
                // zone 校验
                if (!TextUtils.isEmpty(responseZone) && !TextUtils.isEmpty(currentZone)
                        && !currentZone.equals(responseZone)) {
                    addLog("status_light 响应 zone 不匹配(响应=" + responseZone + "),忽略");
                    return;
                }
                if (TextUtils.isEmpty(responseColor)) {
                    // 设备没回 color 字段 → 主动 get_status_light 校验
                    addLog("status_light 响应缺少 color 字段,主动 get_status_light 校验");
                    sendCommand("{\"cmd\":\"get_status_light\",\"zone\":\"" + currentZone + "\"}");
                    return;
                }
                String normalized = normalizeStatusLightColor(responseColor);
                statusLightColor = normalized;
                // 校验 pending 并清除
                verifyControlPending("status_light", parsed);
                return;
            }
            // ★ 定时控件单条状态回包（5s 校时轮询用）：主机剩余时间是权威值。
            //   之前只在 get_all_status 里解析，单条 get_* 回来后被直接丢弃。
            int timedIndex = timedIndexForGetCmd(cmd);
            if (timedIndex >= 0) {
                String zone = normalizeZone(parsed.optString("zone", ""));
                if (!TextUtils.isEmpty(zone) && !currentZone.equals(zone)) {
                    addLog("[定时校时] 忽略非当前舱回包 " + cmd + " zone=" + zone);
                    return;
                }
                JSONObject data = parsed.optJSONObject("data");
                JSONObject source = data != null ? data : parsed;
                boolean enabled = timedEnabledFromResponse(source);
                int remaining = timedRemainingFromResponse(source);
                applyHostTimedRemaining(timedIndex, enabled, remaining);
                setTimedControlEnabledLocally(timedIndex, enabled);
                return;
            }
            if ("get_main_version".equals(cmd)) {
                mainVersion = validVersion(parsed.optString("value", "")) ? parsed.optString("value", "") : mainVersion;
                return;
            }
            if ("get_ctrl_version".equals(cmd)) {
                ctrlVersion = validVersion(parsed.optString("value", "")) ? parsed.optString("value", "") : ctrlVersion;
                return;
            }
            if ("get_bt_ver".equals(cmd)) {
                String bt = parsed.optString("value", "");
                if (!validVersion(bt)) {
                    bt = parsed.optString("version", "");
                }
                btVersion = validVersion(bt) ? bt : btVersion;
                return;
            }
            // ★ P0-6:5 个 set_*_time 与 set_infrared_temp 之前只 recordSetPending 却从不
            //   verifyControlPending,pending 永不清除。后果:每次"直接开启"理疗都必定在
            //   3 秒后弹"设备 3 秒未确认",2 秒后再弹"设备长时间无响应",而功能其实正常。
            //   同时 hasPendingControl() 永久为 true,UI 的"确认中…"永不消失。
            //   这里只补接收侧解析,不改任何发出去的报文。
            if ("ok".equals(parsed.optString("ret", ""))) {
                if (isTimedTimeCmd(cmd)) {
                    // 配置值在 setControlTime() 发命令前已经保存；ACK 只清 pending。
                    // 直接开启使用同名命令发送 120/65536，绝不能借 ACK 覆盖用户配置。
                    verifyControlPending(cmd, parsed);
                    return;
                }
                if ("set_infrared_temp".equals(cmd)) {
                    // 协议字段设备端未最终确认,这里只清 pending,不覆盖本地值
                    verifyControlPending("set_infrared_temp", parsed);
                    return;
                }
            }
            if ("get_run_time".equals(cmd) && parsed.has("value")) {
                treatmentMinutes = parsed.optInt("value");
                refreshTreatmentCountdown();
                return;
            }
            if ("set_run_time".equals(cmd) && "ok".equals(parsed.optString("ret", "")) && parsed.has("value")) {
                treatmentMinutes = parsed.optInt("value");
                refreshTreatmentCountdown();
                return;
            }
            // 设备已回同名环境命令但缺值/失败，也要消费请求上下文，避免另一舱一直等到超时。
            if (isEnvironmentResponseCommand(cmd)) {
                resolveEnvironmentResponseZone(parsed, cmd);
                addLog("[ENV无有效值] " + cmd + " ret=" + parsed.optString("ret", ""));
            }
        } catch (JSONException exception) {
            addLog("环境/状态 JSON 解析失败: " + exception.getMessage() + " raw=" + message);
        }
    }

    private Boolean switchValueFromResponse(JSONObject object, Boolean fallback) {
        String value = object.optString("enable", "");
        if (TextUtils.isEmpty(value)) {
            value = object.optString("status", "");
        }
        if ("on".equalsIgnoreCase(value) || "true".equalsIgnoreCase(value) || "1".equals(value)) {
            return true;
        }
        if ("off".equalsIgnoreCase(value) || "false".equalsIgnoreCase(value) || "0".equals(value)) {
            return false;
        }
        return fallback;
    }

    /**
     * 主机状态回包 → 本地定时控件状态。
     *
     * ★ 校时口径：time_remaining 是主机权威剩余时间，只用来重设本地倒计时终点，
     *   不再写入/持久化“用户配置时长”，否则每轮轮询都会把配置值改小。
     * ★ time_remaining == 65536 视为不限时运行：清倒计时、置常开标记。
     */
    private void updateTimedSwitch(JSONObject object, String type) {
        if (object == null) {
            return;
        }
        boolean enabled = "on".equals(object.optString("status", "off"));
        int time = object.optInt("time_remaining", 0);
        int index = -1;
        if ("uv".equals(type)) {
            uv = enabled;
            index = 12;
        } else if ("blue".equals(type)) {
            blueTherapy = enabled;
            index = 7;
        } else if ("red".equals(type)) {
            redTherapy = enabled;
            index = 6;
        } else if ("nebulizer".equals(type)) {
            nebulizer = enabled;
            index = 10;
        } else if ("anion".equals(type)) {
            anion = enabled;
            index = 11;
        }
        if (index < 0) {
            return;
        }
        applyHostTimedRemaining(index, enabled, time);
    }

    /** 用主机剩余时间校准本地倒计时（所有 get_* / get_all_status 回包共用）。 */
    private void applyHostTimedRemaining(int index, boolean enabled, int remainingMinutes) {
        boolean unlimited = TimedControlProtocol.isUnlimitedRunning(enabled, remainingMinutes);
        setUnlimitedRunning(index, unlimited);
        long endAt = TimedControlProtocol.countdownEndAtMs(
                System.currentTimeMillis(), enabled, remainingMinutes);
        setCountdownField(index, endAt);
        if (endAt > 0L) {
            // ★ 主机每次回包都重排 tick，让本地显示跟随主机收敛，不再各算各的。
            scheduleCountdownCheck(index);
        } else {
            cancelCountdownRunnable(index);
        }
        addLog("[定时校时] index=" + index + " on=" + enabled
                + (unlimited ? " 不限时" : " 剩余=" + remainingMinutes + "分钟"));
    }

    private void cancelCountdownRunnable(int index) {
        Runnable runnable = countdownRunnables.remove(countdownRunnableKey(currentZone, index));
        if (runnable != null) {
            handler.removeCallbacks(runnable);
        }
    }

    /**
     * ★ 修改 P0-2:set_* 操作后不再触发批量轮询。
     * 设备在 set_* 响应里已经回执当前状态(例如 set_red_enable ret:ok + enable),
     * updateStatusFromJson() 会自动更新本地状态。
     * 完整状态由后台 30s 一次的 readAllStatus() 兜底。
     */
    private void scheduleStatusRefresh() {
        // 空实现,保留方法签名避免大范围修改调用点
    }

    private ZoneEnvironmentState environmentForZone(String zone) {
        return "left".equals(normalizeZone(zone)) ? leftEnvironment : rightEnvironment;
    }

    private Float getCurrentCabinTemp() {
        return environmentForZone(currentZone).temperature;
    }

    private Float getCurrentOxygen() {
        return environmentForZone(currentZone).oxygen;
    }

    private Float getCurrentHumidity() {
        return environmentForZone(currentZone).humidity;
    }

    private Integer getCurrentCo2() {
        return environmentForZone(currentZone).co2;
    }

    private static String normalizeZone(String zone) {
        return EnvironmentProtocol.normalizeZone(zone);
    }

    private static boolean isEnvironmentResponseCommand(String cmd) {
        return EnvironmentProtocol.isEnvironmentResponseCommand(cmd);
    }

    private void updateEnvironmentTemperature(String zone, Float value) {
        if (value == null || TextUtils.isEmpty(normalizeZone(zone))) return;
        ZoneEnvironmentState state = environmentForZone(zone);
        state.temperature = value;
        state.updatedAtMs = System.currentTimeMillis();
    }

    private void updateEnvironmentOxygen(String zone, Float value) {
        if (value == null || TextUtils.isEmpty(normalizeZone(zone))) return;
        ZoneEnvironmentState state = environmentForZone(zone);
        state.oxygen = value;
        state.updatedAtMs = System.currentTimeMillis();
    }

    private void updateEnvironmentHumidity(String zone, Float value) {
        if (value == null || TextUtils.isEmpty(normalizeZone(zone))) return;
        ZoneEnvironmentState state = environmentForZone(zone);
        state.humidity = value;
        state.updatedAtMs = System.currentTimeMillis();
    }

    private void updateEnvironmentCo2(String zone, Integer value) {
        if (value == null || TextUtils.isEmpty(normalizeZone(zone))) return;
        ZoneEnvironmentState state = environmentForZone(zone);
        state.co2 = value;
        state.updatedAtMs = System.currentTimeMillis();
    }

    private String zoneTimedControlKey(int index) {
        return "zone_timed_control_" + index + "_" + currentZone;
    }

    private Integer loadZoneTimedControlValue(int index) {
        android.content.SharedPreferences preferences = appContext.getSharedPreferences("icu_ble_settings", Context.MODE_PRIVATE);
        String key = zoneTimedControlKey(index);
        return preferences.contains(key) ? preferences.getInt(key, 0) : null;
    }

    private void saveZoneTimedControlValue(int index, int minutes) {
        appContext.getSharedPreferences("icu_ble_settings", Context.MODE_PRIVATE)
                .edit()
                .putInt(zoneTimedControlKey(index), minutes)
                .apply();
    }

    private String zoneCountdownEndAtKey(int index) {
        return "zone_timed_control_end_at_" + index + "_" + currentZone;
    }

    private void saveZoneCountdownEndAt(int index, long endAtMs) {
        appContext.getSharedPreferences("icu_ble_settings", Context.MODE_PRIVATE)
                .edit()
                .putLong(zoneCountdownEndAtKey(index), endAtMs)
                .apply();
    }

    private long loadZoneCountdownEndAt(int index) {
        return appContext.getSharedPreferences("icu_ble_settings", Context.MODE_PRIVATE)
                .getLong(zoneCountdownEndAtKey(index), 0L);
    }

    private String zoneTreatmentMinutesKey() {
        return "zone_treatment_minutes_" + currentZone;
    }

    private String zoneTreatmentCountdownEndAtKey() {
        return "zone_treatment_end_at_" + currentZone;
    }

    private void saveZoneTreatmentMinutes(int minutes) {
        appContext.getSharedPreferences("icu_ble_settings", Context.MODE_PRIVATE)
                .edit()
                .putInt(zoneTreatmentMinutesKey(), minutes)
                .apply();
    }

    private String zoneTreatmentAccumulatedKey() {
        return "zone_treatment_accum_ms_" + currentZone;
    }

    private String zoneTreatmentRunningSinceKey() {
        return "zone_treatment_running_since_" + currentZone;
    }

    private void saveZoneTreatmentAccumulatedMs() {
        appContext.getSharedPreferences("icu_ble_settings", Context.MODE_PRIVATE)
                .edit()
                .putLong(zoneTreatmentAccumulatedKey(), treatmentAccumulatedMs)
                .apply();
    }

    private void saveZoneTreatmentRunningSinceMs(long sinceMs) {
        appContext.getSharedPreferences("icu_ble_settings", Context.MODE_PRIVATE)
                .edit()
                .putLong(zoneTreatmentRunningSinceKey(), sinceMs)
                .apply();
    }

    private void saveZoneTreatmentCountdownEndAt(long endAtMs) {
        appContext.getSharedPreferences("icu_ble_settings", Context.MODE_PRIVATE)
                .edit()
                .putLong(zoneTreatmentCountdownEndAtKey(), endAtMs)
                .apply();
    }

    private String zoneTargetKey(String name, String zone) {
        return "zone_target_" + name + "_" + normalizeZone(zone);
    }

    private void migrateLegacyZoneTargets(SharedPreferences preferences) {
        if (preferences.getBoolean("zone_targets_v2_migrated", false)) {
            return;
        }
        SharedPreferences.Editor editor = preferences.edit();
        if (lastSetCabinTemp != null) {
            editor.putFloat(zoneTargetKey("temp", currentZone), lastSetCabinTemp);
        }
        if (lastSetHumidity != null) {
            editor.putFloat(zoneTargetKey("humidity", currentZone), lastSetHumidity);
        }
        editor.putBoolean("zone_targets_v2_migrated", true).commit();
    }

    private Float loadZoneFloatTarget(String name) {
        SharedPreferences preferences = appContext.getSharedPreferences("icu_ble_settings", Context.MODE_PRIVATE);
        String key = zoneTargetKey(name, currentZone);
        return preferences.contains(key) ? preferences.getFloat(key, 0f) : null;
    }

    private void saveZoneFloatTarget(String name, Float value) {
        SharedPreferences.Editor editor = appContext.getSharedPreferences(
                "icu_ble_settings", Context.MODE_PRIVATE).edit();
        String key = zoneTargetKey(name, currentZone);
        if (value == null) editor.remove(key); else editor.putFloat(key, value);
        editor.apply();
    }

    private void saveCurrentZoneTimingProjection(HostZoneState state) {
        state.redTime = redTime;
        state.blueTime = blueTime;
        state.nebulizerTime = nebulizerTime;
        state.anionTime = anionTime;
        state.uvTime = uvTime;
        state.redCountdownEndAtMs = redCountdownEndAtMs;
        state.blueCountdownEndAtMs = blueCountdownEndAtMs;
        state.nebulizerCountdownEndAtMs = nebulizerCountdownEndAtMs;
        state.anionCountdownEndAtMs = anionCountdownEndAtMs;
        state.uvCountdownEndAtMs = uvCountdownEndAtMs;
        state.treatmentCountdownEndAtMs = treatmentCountdownEndAtMs;
        state.unlimitedRunning.clear();
        state.unlimitedRunning.addAll(unlimitedRunning);
    }

    private void saveCurrentZoneAlarmProjection(HostZoneState state) {
        state.lastCo2AutoVentAt = lastCo2AutoVentAt;
        state.lastCo2AlarmAt = lastCo2AlarmAt;
        state.lastCo2RecoverAt = lastCo2RecoverAt;
        state.co2AlarmActive = wasInAlarm;
        synchronized (co2AlarmHistory) {
            state.co2AlarmHistory.clear();
            state.co2AlarmHistory.addAll(co2AlarmHistory);
        }
    }

    private void loadZoneProjection() {
        HostZoneState state = currentHostState();
        lastSetCabinTemp = loadZoneFloatTarget("temp");
        lastSetHumidity = loadZoneFloatTarget("humidity");
        statusLightColor = "";
        tempEnabled = null;
        o2Enabled = null;
        coldLight = null;
        warmLight = null;
        redTherapy = null;
        blueTherapy = null;
        outerCycle = null;
        innerCycle = null;
        nebulizer = null;
        anion = null;
        uv = null;
        co2Enabled = null;
        lastSetCo2BeforeRollback = null;
        lastCo2AutoVentAt = 0L;
        lastCo2AlarmAt = 0L;
        lastCo2RecoverAt = 0L;
        wasInAlarm = false;
        synchronized (co2AlarmHistory) {
            co2AlarmHistory.clear();
        }
        loadZoneTimedControlState();
        if (state.initialized) {
            lastSetCabinTemp = state.targetTemperature;
            lastSetHumidity = state.targetHumidity;
            lastSetCo2BeforeRollback = state.previousTargetCo2;
            statusLightColor = state.statusLightColor;
            treatmentMinutes = state.treatmentMinutes;
            treatmentAccumulatedMs = state.treatmentAccumulatedMs;
            treatmentRunningSinceMs = state.treatmentRunningSinceMs;
            tempEnabled = state.tempEnabled;
            o2Enabled = state.o2Enabled;
            coldLight = state.coldLight;
            warmLight = state.warmLight;
            redTherapy = state.redTherapy;
            blueTherapy = state.blueTherapy;
            outerCycle = state.outerCycle;
            innerCycle = state.innerCycle;
            nebulizer = state.nebulizer;
            anion = state.anion;
            uv = state.uv;
            co2Enabled = state.co2Enabled;
            redTime = state.redTime;
            blueTime = state.blueTime;
            nebulizerTime = state.nebulizerTime;
            anionTime = state.anionTime;
            uvTime = state.uvTime;
            redCountdownEndAtMs = state.redCountdownEndAtMs;
            blueCountdownEndAtMs = state.blueCountdownEndAtMs;
            nebulizerCountdownEndAtMs = state.nebulizerCountdownEndAtMs;
            anionCountdownEndAtMs = state.anionCountdownEndAtMs;
            uvCountdownEndAtMs = state.uvCountdownEndAtMs;
            treatmentCountdownEndAtMs = state.treatmentCountdownEndAtMs;
            unlimitedRunning.clear();
            unlimitedRunning.addAll(state.unlimitedRunning);
            lastCo2AutoVentAt = state.lastCo2AutoVentAt;
            lastCo2AlarmAt = state.lastCo2AlarmAt;
            lastCo2RecoverAt = state.lastCo2RecoverAt;
            wasInAlarm = state.co2AlarmActive;
            synchronized (co2AlarmHistory) {
                co2AlarmHistory.addAll(state.co2AlarmHistory);
            }
        }
        saveCurrentZoneProjection();
    }

    private void loadZoneTimedControlState() {
        cancelTimedControlCountdowns();
        redTime = loadZoneTimedControlValue(6);
        blueTime = loadZoneTimedControlValue(7);
        nebulizerTime = loadZoneTimedControlValue(10);
        anionTime = loadZoneTimedControlValue(11);
        uvTime = loadZoneTimedControlValue(12);
        redCountdownEndAtMs = loadZoneCountdownEndAt(6);
        blueCountdownEndAtMs = loadZoneCountdownEndAt(7);
        nebulizerCountdownEndAtMs = loadZoneCountdownEndAt(10);
        anionCountdownEndAtMs = loadZoneCountdownEndAt(11);
        uvCountdownEndAtMs = loadZoneCountdownEndAt(12);
        android.content.SharedPreferences preferences = appContext.getSharedPreferences("icu_ble_settings", Context.MODE_PRIVATE);
        treatmentMinutes = preferences.contains(zoneTreatmentMinutesKey())
                ? preferences.getInt(zoneTreatmentMinutesKey(), 0) : null;
        treatmentCountdownEndAtMs = preferences.getLong(zoneTreatmentCountdownEndAtKey(), 0L);
        // ★ 恢复治疗时长正计时：累计值 + 本次起点（墙钟），断电重启也能续算。
        treatmentAccumulatedMs = preferences.getLong(zoneTreatmentAccumulatedKey(), 0L);
        treatmentRunningSinceMs = preferences.getLong(zoneTreatmentRunningSinceKey(), 0L);
        long now = System.currentTimeMillis();
        // ★ 不限时(65536)控件没有倒计时终点，必须单独恢复常开标记，
        //   否则切舱/重启后会被误判成“已关闭”。
        unlimitedRunning.clear();
        int[] timedIndices = {6, 7, 10, 11, 12};
        for (int index : timedIndices) {
            if (preferences.getBoolean(zoneUnlimitedKey(index), false)) {
                unlimitedRunning.add(index);
            }
        }
        redTherapy = (redCountdownEndAtMs > now || unlimitedRunning.contains(6)) ? Boolean.TRUE : null;
        blueTherapy = (blueCountdownEndAtMs > now || unlimitedRunning.contains(7)) ? Boolean.TRUE : null;
        nebulizer = (nebulizerCountdownEndAtMs > now || unlimitedRunning.contains(10)) ? Boolean.TRUE : null;
        anion = (anionCountdownEndAtMs > now || unlimitedRunning.contains(11)) ? Boolean.TRUE : null;
        uv = (uvCountdownEndAtMs > now || unlimitedRunning.contains(12)) ? Boolean.TRUE : null;
        // ★ P1-14:恢复出来的倒计时必须重新挂上 tick,否则只是显示在走,
        //   归零时不会触发 setControlEnabled(index, false) 自动关闭。
        //   已过期的直接清掉(连带清持久化),避免复活假状态。
        for (int index : timedIndices) {
            long endAt = getCountdownField(index);
            if (endAt > now) {
                scheduleCountdownCheck(index);
            } else if (endAt > 0L) {
                clearCountdown(index);
            }
        }
        if (treatmentCountdownEndAtMs > now) {
            scheduleCountdownCheck(15);
        } else if (treatmentCountdownEndAtMs > 0L) {
            treatmentMinutes = 0;
            saveZoneTreatmentMinutes(0);
            saveZoneTreatmentCountdownEndAt(0L);
            treatmentCountdownEndAtMs = 0L;
        }
    }

    private void cancelTimedControlCountdowns() {
        for (Runnable runnable : countdownRunnables.values()) {
            if (runnable != null) {
                handler.removeCallbacks(runnable);
            }
        }
        countdownRunnables.clear();
    }

    private Float getLastSetOxygen() {
        return "left".equals(currentZone) ? lastSetOxygenLeft : lastSetOxygenRight;
    }

    private void setLastSetOxygen(float value) {
        String key = "left".equals(currentZone) ? "last_set_oxygen_left" : "last_set_oxygen_right";
        if ("left".equals(currentZone)) {
            lastSetOxygenLeft = value;
        } else {
            lastSetOxygenRight = value;
        }
        appContext.getSharedPreferences("icu_ble_settings", Context.MODE_PRIVATE)
                .edit()
                .putFloat(key, value)
                .apply();
    }

    private boolean ensureReady() {
        if (missingBluetoothConnectPermission()) {
            setError("缺少蓝牙连接权限，请先申请权限");
            return false;
        }
        if (bluetoothGatt == null) {
            setError("请先连接蓝牙设备");
            return false;
        }
        if (!connected || writeCharacteristic == null) {
            // ★ 修复 P0-4:明确告知用户设备已断开,而不是误导"初始化中"
            setError("设备已断开,请重新连接蓝牙");
            return false;
        }
        return true;
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

    private String formatCommandNumber(float value) {
        if (Math.abs(value - Math.round(value)) < 0.001f) {
            return String.valueOf(Math.round(value));
        }
        return String.format(Locale.US, "%.1f", value);
    }

    private String escapeJson(String value) {
        return value == null ? "" : value.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private void updateOxygenFromZone(String zone, Float value) {
        updateEnvironmentOxygen(zone, value);
    }

    private boolean validVersion(String value) {
        return !TextUtils.isEmpty(value)
                && !"--".equals(value)
                && !"undefined".equals(value)
                && !"null".equals(value);
    }

    private String onOff(boolean value) {
        return value ? "on" : "off";
    }

    private boolean nextSwitchValue(Boolean current) {
        return !Boolean.TRUE.equals(current);
    }

    private String nextStatusLightColor() {
        if ("red".equals(statusLightColor)) {
            return "yellow";
        }
        if ("yellow".equals(statusLightColor)) {
            return "green";
        }
        return "red";
    }

    private String normalizeStatusLightColor(String color) {
        if ("red".equals(color) || "yellow".equals(color) || "green".equals(color)) {
            return color;
        }
        return "";
    }

    private Float valueDiv10(JSONObject object, String key, Float fallback) {
        if (object == null || !object.has(key)) {
            return fallback;
        }
        return (float) object.optDouble(key) / 10f;
    }

    private Float valueMaybeDiv10(JSONObject object, String key, Float fallback) {
        if (object == null || !object.has(key)) {
            return fallback;
        }
        double raw = object.optDouble(key, Double.NaN);
        if (Double.isNaN(raw)) {
            return fallback;
        }
        if (Math.abs(raw) > 100d) {
            raw = raw / 10d;
        }
        return (float) raw;
    }

    private String formatValue(Float value, String suffix) {
        return value == null ? "--" : formatNumber(value) + suffix;
    }

    private String formatSwitch(Boolean value) {
        if (value == null) {
            return "--";
        }
        return value ? "开" : "关";
    }

    private String formatTimedValue(Integer minutes) {
        return minutes == null ? "--" : minutes + "分钟";
    }

    private String formatTimedSwitch(Boolean enabled, Integer minutes) {
        if (enabled == null) {
            return "--";
        }
        if (!enabled) {
            return "关";
        }
        return "开" + (minutes == null ? "--" : minutes) + "分钟";
    }

    private String formatTimedShort(Boolean enabled, Integer minutes) {
        if (enabled == null) {
            return "--";
        }
        if (!enabled) {
            return "关";
        }
        return "开" + (minutes == null ? "--" : minutes) + "m";
    }

    private String formatStatusLight() {
        if ("red".equals(statusLightColor)) {
            return "红";
        }
        if ("yellow".equals(statusLightColor)) {
            return "黄";
        }
        if ("green".equals(statusLightColor)) {
            return "绿";
        }
        return "--";
    }

    private String formatNumber(float value) {
        if (Math.abs(value - Math.round(value)) < 0.05f) {
            return String.valueOf(Math.round(value));
        }
        return String.format(Locale.US, "%.1f", value);
    }

    private String extractJsonString(String message, String key) {
        Pattern pattern = Pattern.compile("\"" + key + "\"\\s*:\\s*\"([^\"]*)\"");
        Matcher matcher = pattern.matcher(message);
        return matcher.find() ? matcher.group(1) : "";
    }

    private String extractJsonNumber(String message, String key) {
        Pattern pattern = Pattern.compile("\"" + key + "\"\\s*:\\s*(-?\\d+(?:\\.\\d+)?)");
        Matcher matcher = pattern.matcher(message);
        return matcher.find() ? matcher.group(1) : "";
    }

    private String parsedVersionValue(String message) {
        String version = extractJsonString(message, "value");
        if (TextUtils.isEmpty(version)) {
            version = extractJsonString(message, "version");
        }
        return version;
    }

    private String extractNestedInt(String message, String objectKey, String valueKey) {
        Pattern objectPattern = Pattern.compile("\"" + objectKey + "\"\\s*:\\s*\\{([^}]*)\\}");
        Matcher objectMatcher = objectPattern.matcher(message);
        if (!objectMatcher.find()) {
            return "";
        }
        return extractJsonNumber(objectMatcher.group(1), valueKey);
    }

    private String formatDiv10(String raw) {
        if (TextUtils.isEmpty(raw)) {
            return "";
        }
        try {
            return String.format(Locale.US, "%.1f", Float.parseFloat(raw) / 10f);
        } catch (Exception ignored) {
            return raw;
        }
    }

    private String formatMaybeDiv10(String raw) {
        if (TextUtils.isEmpty(raw)) {
            return "";
        }
        try {
            float value = Float.parseFloat(raw);
            if (Math.abs(value) > 100f) {
                value = value / 10f;
            }
            return formatNumber(value);
        } catch (Exception ignored) {
            return raw;
        }
    }

    private int resetTransportState(boolean clearLiveEnvironment) {
        handler.removeCallbacks(writeTimeoutRunnable);
        handler.removeCallbacks(resumeWriteQueueRunnable);
        ignoreWriteCallbacksUntilMs = 0L;
        int dropped;
        synchronized (writeQueueLock) {
            dropped = writeQueue.size() + (currentWrite == null ? 0 : 1);
            writeQueue.clear();
            currentWrite = null;
            writing = false;
        }
        synchronized (environmentReadLock) {
            for (int i = 0; i < pendingEnvironmentReads.size(); i++) {
                handler.removeCallbacks(pendingEnvironmentReads.valueAt(i).timeoutRunnable);
            }
            pendingEnvironmentReads.clear();
            environmentGuardUntil.clear();
        }
        synchronized (notifyBuffer) {
            notifyBuffer.setLength(0);
        }
        connectionGeneration++;
        if (clearLiveEnvironment) {
            leftEnvironment.clear();
            rightEnvironment.clear();
            wasInAlarm = false;
        }
        return dropped;
    }

    private void setError(String message) {
        lastError = message;
        addLog(message);
        emit();
    }

    private void addLog(String message) {
        String timestamp = new SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(new Date());
        // ★ P0-5:addLog 被主线程与 BLE binder 线程同时调用,
        //   而 getLogs() 在主线程做拷贝,不加锁必然出 ConcurrentModificationException。
        synchronized (logsLock) {
            logs.add(0, "[" + timestamp + "] " + message);
            while (logs.size() > MAX_LOG_COUNT) {
                logs.remove(logs.size() - 1);
            }
        }
    }

    private void emit() {
        // 治疗时长正计时：每次状态变化先结算/启动计时器，治疗中再挂 1 秒心跳让显示走字。
        updateTreatmentAccumulator();
        ensureTreatmentTicker();
        if (listener != null) {
            listener.onStateChanged();
        }
    }
}
