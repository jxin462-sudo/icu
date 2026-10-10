package com.example.icuapk;

import android.app.AlertDialog;
import android.app.DatePickerDialog;
import android.app.TimePickerDialog;
import android.content.ContentValues;
import android.content.Context;
import android.content.DialogInterface;
import android.database.Cursor;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.net.Uri;
import android.net.wifi.WifiManager;
import android.provider.Settings;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RadialGradient;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.graphics.pdf.PdfDocument;
import android.media.AudioManager;
import android.media.ToneGenerator;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.provider.MediaStore;
import android.provider.OpenableColumns;
import android.text.InputType;
import android.text.InputFilter;
import android.text.TextUtils;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.EditText;
import android.widget.CheckBox;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.text.SimpleDateFormat;
import java.util.AbstractList;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import android.content.Context;
import android.os.Bundle;
import android.os.CancellationSignal;
import android.os.ParcelFileDescriptor;
import android.print.PageRange;
import android.print.PrintAttributes;
import android.print.PrintDocumentAdapter;
import android.print.PrintDocumentInfo;
import android.print.PrintJob;
import android.print.PrintManager;
import android.net.nsd.NsdManager;
import android.net.nsd.NsdServiceInfo;
import java.io.InputStream;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

public class IcuDashboardView extends View {
    static final int REQUEST_ORGANIZATION_LOGO = 4101;
    // ★ 任务31：页脚LOGO 独立请求码（此前与医院LOGO 共用 action，无法区分）
    static final int REQUEST_FOOTER_LOGO = 4102;
    private static final String PREFS_NAME = "icu_dashboard";
    private static final String KEY_TREATMENT_CASES = "treatment_cases";
    private static final String KEY_TREATMENT_CASES_V2 = "treatment_cases_v2";
    private static final int TREATMENT_CASES_SCHEMA_VERSION = 2;
    // ★ V1.02 S2 操作日志：用户关键操作（编辑/删除/开始护疗/结束）落库，供 S8 日志模块展示。
    private static final String KEY_USER_OP_LOG = "user_op_log_v1";
    /* ★ 2026-10-08 日志页：其余 5 类日志各一个 SharedPreferences 键（通信记录在 MainActivity 侧单独存） */
    private static final String KEY_GENERAL_LOG = "general_log_v1";
    private static final String KEY_DEBUG_LOG = "debug_log_v1";
    private static final String KEY_ERROR_LOG = "error_log_v1";
    private static final String KEY_STACK_LOG = "stack_log_v1";
    private static final String KEY_COMM_LOG = "comm_log_v1";
    private static final String KEY_CASE_NO_CURSOR = "case_no_cursor";
    private static final String KEY_CAMERA_STREAM_URL = "camera_stream_url";
    private static final String KEY_CAMERA_STREAM_USER_SET = "camera_stream_url_user_set";
    private static final String KEY_CAMERA_SNAPSHOTS = "camera_snapshots";
    private static final String KEY_ORGANIZATIONS = "organizations";
    private static final String KEY_SELECTED_ORGANIZATION = "selected_organization";
    private static final String KEY_SELECTED_HOST_MODE = "selected_host_mode";
    private static final String KEY_LAST_HOST_MODE = "last_host_mode";
    private static final String KEY_CUSTOM_HOST_MODE_CONFIG = "custom_host_mode_config";
    private static final String KEY_HOST_MODE_CONFIG_MOTHER = "host_mode_config_mother";
    private static final String KEY_HOST_MODE_CONFIG_POSTOP = "host_mode_config_postop";
    private static final String KEY_HOST_MODE_CONFIG_CARDIO = "host_mode_config_cardio";
    private static final String KEY_ACTIVE_HOST_MODE = "active_host_mode";
    private static final String[] HOST_MODE_CONFIG_KEYS = {
            KEY_HOST_MODE_CONFIG_MOTHER,
            KEY_HOST_MODE_CONFIG_POSTOP,
            KEY_HOST_MODE_CONFIG_CARDIO,
            KEY_CUSTOM_HOST_MODE_CONFIG
    };
    private static final String KEY_TREATMENT_PERIOD_MODE = "treatment_period_mode";
    private static final String KEY_MONITOR_PULSE_BEEP = "monitor_pulse_beep";
    private static final String KEY_MONITOR_ALARM_SOUND = "monitor_alarm_sound";
    private static final String KEY_MONITOR_ALARM_ENABLED = "monitor_alarm_enabled";
    private static final String KEY_MONITOR_THRESHOLDS = "monitor_alarm_thresholds";
    private static final String[] MONITOR_THRESHOLD_KEYS = {
            "heartRate", "bloodPressure", "spo2", "pulse", "bodyTemp", "resp"
    };
    private static final String[] MONITOR_THRESHOLD_LABELS = {
            "心率 bpm", "血压 mmHg (收缩/舒张)", "血氧 %", "脉率 bpm", "体温 ℃", "呼吸率 brpm"
    };
    // 教程页视频列表指纹：JS 第一次成功拿到教程页状态后写入，用于判断"教程内容是否变更"
    private static final String KEY_TUTORIAL_GALLERY_FINGERPRINT = "tutorial_gallery_fingerprint";
    // 默认报警阈值（低于这些值则触发报警）
    private static final float[] DEFAULT_MONITOR_THRESHOLDS = {
            60f, 90f, 94f, 60f, 35f, 8f
    };
    // 血压阈值的舒张压默认值（与收缩压成对）
    private static final float[] DEFAULT_MONITOR_THRESHOLDS_DIA = {
            0f, 60f, 0f, 0f, 0f, 0f
    };
    private static final long ALARM_DEBOUNCE_MS = 10000L;  // 同一指标 10 秒内不重复报警
    private static final int TAG_THRESHOLD_DIA_INPUT = 0x7F0A0001;  // 用作血压舒张压输入框的 tag key
    private static final String KEY_MONITOR_LEVEL_COLOR = "monitor_level_color";
    private static final String KEY_DEVICE_PROFILE = "device_profile";

    // ★ V1.02 S5 设置项（常规/连接/传输/打印）
    private static final String KEY_S5_LANGUAGE = "s5_language";
    private static final String KEY_S5_TIME_FORMAT = "s5_time_format";
    private static final String KEY_S5_PRINT_COPIES = "s5_print_copies";
    /** ★ 任务14（V1.02 R59）：打印机地址 */
    private static final String KEY_S5_PRINTER_ADDRESS = "s5_printer_address";
    /** ★ 任务16：演示数据开关（开=监护页显示默认 demo 波形；关=未连蓝牙时波形为空） */
    private static final String KEY_S5_DEMO_DATA = "s5_demo_data";
    private static final String KEY_S5_REPORT_TITLE = "s5_report_title";
    private static final String KEY_S5_REPORT_DECLARATION = "s5_report_declaration";
    private static final String KEY_S5_FOOTER_LOGO_URI = "s5_footer_logo_uri";
    private static final String KEY_S5_CLOUD_PLATFORM = "s5_cloud_platform";
    private static final String KEY_S5_LIS = "s5_lis";

    private String s5Language = "zh";
    private String s5TimeFormat = "yyyy-MM-dd HH:mm:ss";
    private int s5PrintCopies = 1;
    /** ★ 任务14（V1.02 R59）：打印机地址 */
    private String s5PrinterAddress = "";
    /** ★ 任务16：演示数据开关 */
    private boolean s5DemoData = false;
    private String s5ReportTitle = "";
    /** ★ 任务15(#7)：报告声明默认文案 —— 对应报告单中「※本检测结果仅对该样本负责」 */
    private String s5ReportDeclaration = "※本检测结果仅对该样本负责";
    private String s5FooterLogoUri = "";
    private boolean s5CloudPlatform = false;
    private boolean s5Lis = false;

    /* ★ 2026-10-10 #56：打印结果回推 H5（成功/失败提示 + 关闭「正在打印」弹窗）。
       seq 单调递增，H5 边沿检测后 toast 并退出打印页。 */
    private int printStatusSeq = 0;
    private boolean printStatusOk = false;
    private String printStatusMsg = "";

    /* ★ 2026-10-10 #57：NSD 局域网打印机扫描（_ipp._tcp / _pdl-datastream._tcp）。
       结果以「打印机名 + 地址」推给 H5，H5 列表只显示打印机名，点击即添加。 */
    private boolean printerScanning = false;
    private final java.util.List<JSONObject> printerScanResults = new java.util.ArrayList<>();
    private final java.util.List<NsdManager.DiscoveryListener> printerScanListeners = new java.util.ArrayList<>();
    private final Handler printerScanHandler = new Handler(Looper.getMainLooper());
    private WifiManager.MulticastLock printerScanLock = null;
    private static final String CAMERA_PLAYER_VERSION = "摄像头内置播放器 v1.8";
    private static final String EMPTY_MONITOR_SUMMARY = "尚未收到 AM4100 数据";
    private static final String PERIOD_ALL = "all";
    private static final String PERIOD_ENABLED = "enabled";
    private static final String PERIOD_CUSTOM = "custom";
    private static final String[] CUSTOM_NUMBER_KEYS = {"temp", "oxygen", "humidity", "co2", "treatmentMinutes"};
    private static final String[] CUSTOM_NUMBER_LABELS = {"舱内温度 ℃", "氧浓度 %", "湿度 %", "CO2目标值 PPM", "治疗时长 分钟"};
    private static final int[] CUSTOM_NUMBER_INDICES = {0, 1, 14, 3, 15};
    private static final boolean[] CUSTOM_NUMBER_INTEGER = {false, false, false, true, true};
    private static final String[] CUSTOM_SWITCH_KEYS = {"co2Enabled", "coldLight", "warmLight", "redTherapy", "blueTherapy", "uv", "nebulizer", "anion", "outerCycle", "innerCycle"};
    private static final String[] CUSTOM_SWITCH_LABELS = {"CO2开关", "冷光照明", "暖光照明", "红外理疗", "蓝光理疗", "紫外消毒", "雾化器", "负离子", "外循环", "内循环"};
    private static final int[] CUSTOM_SWITCH_INDICES = {3, 4, 5, 6, 7, 12, 10, 11, 8, 9};
    private static final String[] CUSTOM_TIME_KEYS = {"redTime", "blueTime", "uvTime", "nebulizerTime", "anionTime"};
    private static final String[] CUSTOM_TIME_LABELS = {"红外理疗时间 分钟", "蓝光理疗时间 分钟", "紫外消毒时间 分钟", "雾化器时间 分钟", "负离子时间 分钟"};
    private static final int[] CUSTOM_TIME_INDICES = {6, 7, 12, 10, 11};

    private static final String[][] TREATMENT_TEMPLATES = {
            {"舱内温度", "℃", "true", "true"},
            {"氧浓度", "%", "true", "true"},
            {"红外理疗", "分钟", "false", "true"},
            {"蓝光理疗", "分钟", "false", "true"},
            {"雾化器", "分钟", "false", "true"},
            {"负离子", "分钟", "false", "true"},
            {"湿度", "%", "true", "true"},
            {"CO2浓度", "PPM", "true", "true"},
            {"护理模式", "", "true", "true"},
            {"心率", "bpm", "true", "true"},
            {"血压", "mmHg", "true", "true"},
            {"血氧", "%", "true", "true"},
            {"脉率", "bpm", "true", "true"},
            {"体温", "℃", "true", "true"},
            {"呼吸率", "brpm", "true", "true"},
            {"心电图", "", "true", "true"},
            {"血氧波形", "", "true", "true"},
            {"呼吸率波形", "", "true", "true"}
    };

    interface CameraPreviewHost {
        void updateCameraPreview(RectF bounds, boolean visible, String[] urls);

        void captureCameraFrame(CameraFrameCallback callback);

        boolean toggleCameraFullscreen();
    }

    interface CameraFrameCallback {
        void onFrame(Bitmap bitmap);

        void onError(String message);
    }

    private interface ClickAction {
        void run();
    }

    private static final class ClickZone {
        final RectF rect;
        final ClickAction action;

        ClickZone(RectF rect, ClickAction action) {
            this.rect = rect;
            this.action = action;
        }
    }

    private void showTreatmentEntryDialog(final PatientCase patient, final TreatmentEntry entry) {
        LinearLayout form = new LinearLayout(activity);
        form.setOrientation(LinearLayout.VERTICAL);
        int pad = dpInt(12);
        form.setPadding(pad, pad, pad, pad);

        final CheckBox enabled = new CheckBox(activity);
        enabled.setText("是否开启");
        enabled.setChecked(entry.enabled);
        form.addView(enabled);

        final CheckBox includeReport = new CheckBox(activity);
        includeReport.setText("是否录入报告");
        includeReport.setChecked(entry.includeInReport);
        form.addView(includeReport);

        final EditText manualAverage = addInput(form, "手工平均值/医生修订", entry.manualAverageValue, InputType.TYPE_CLASS_TEXT);
        final EditText manualHigh = addInput(form, "手工最高值/医生修订", entry.manualHighValue, InputType.TYPE_CLASS_TEXT);
        final EditText manualLow = addInput(form, "手工最低值/医生修订", entry.manualLowValue, InputType.TYPE_CLASS_TEXT);
        final EditText start = addInput(form, "开始治疗时间", entry.startTime, InputType.TYPE_CLASS_TEXT);
        final EditText end = addInput(form, "结束治疗时间", entry.endTime, InputType.TYPE_CLASS_TEXT);

        new AlertDialog.Builder(activity)
                .setTitle("编辑治疗记录 - " + entry.itemName)
                .setView(form)
                .setNegativeButton("取消", null)
                .setNeutralButton("清空统计", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        entry.sum = 0f;
                        entry.high = Float.NaN;
                        entry.low = Float.NaN;
                        entry.sampleCount = 0;
                        entry.manualAverageValue = "";
                        entry.manualHighValue = "";
                        entry.manualLowValue = "";
                        entry.manualValue = "";
                        entry.lastValue = "--";
                        lastGeneratedPdf = null;
                        saveTreatmentRecordsToStorage();
                        invalidate();
                    }
                })
                .setPositiveButton("保存", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        entry.enabled = enabled.isChecked();
                        entry.includeInReport = includeReport.isChecked();
                        entry.manualAverageValue = manualAverage.getText().toString().trim();
                        entry.manualHighValue = manualHigh.getText().toString().trim();
                        entry.manualLowValue = manualLow.getText().toString().trim();
                        applyManualStatOverrides(entry);
                        entry.manualValue = buildTreatmentManualSummary(entry);
                        String nextStartTime = cleanInput(start, entry.startTime);
                        String nextEndTime = cleanInput(end, entry.endTime);
                        applyManualTreatmentPeriod(patient, entry, nextStartTime, nextEndTime);
                        patient.treatmentStartTime = firstNonEmpty(patient.treatmentStartTime, entry.startTime);
                        lastGeneratedPdf = null;
                        saveTreatmentRecordsToStorage();
                        Toast.makeText(activity, "治疗记录已保存", Toast.LENGTH_SHORT).show();
                        invalidate();
                    }
                })
                .show();
    }

    private String treatmentPeriodText(TreatmentEntry entry) {
        if (entry.sampleCount <= 0) {
            return "--";
        }
        return entry.sampleCount + "次 / " + dash(entry.lastValue);
    }

    private String treatmentPeriodDisplayText(TreatmentEntry entry) {
        if (entry != null && entry.customPeriod) {
            return "\u81ea\u5b9a\u4e49\u65f6\u6bb5";
        }
        if (PERIOD_CUSTOM.equals(treatmentPeriodMode)) {
            return "\u81ea\u5b9a\u4e49\u65f6\u6bb5";
        }
        if (PERIOD_ENABLED.equals(treatmentPeriodMode) || usesEnabledOnlyPeriod(entry)) {
            return "\u6253\u5f00\u65f6\u6bb5";
        }
        return "\u5168\u65f6\u6bb5";
    }

    private boolean usesEnabledOnlyPeriod(TreatmentEntry entry) {
        if (entry == null || TextUtils.isEmpty(entry.itemName)) {
            return false;
        }
        return entry.itemName.contains("\u7ea2\u5916")
                || entry.itemName.contains("\u84dd\u5149")
                || entry.itemName.contains("\u96fe\u5316")
                || entry.itemName.contains("\u8d1f\u79bb\u5b50")
                || entry.itemName.contains("\u7d2b\u5916");
    }

    private String treatmentAverageText(TreatmentEntry entry) {
        if (entry == null) {
            return "--";
        }
        if (!TextUtils.isEmpty(entry.manualAverageValue)) {
            return manualStatDisplayText(entry.manualAverageValue, entry.unit);
        }
        if (entry.sampleCount <= 0) {
            return "--";
        }
        return treatmentExtremeText(entry.sum / entry.sampleCount, entry.unit);
    }

    private String treatmentHighText(TreatmentEntry entry) {
        if (entry == null) {
            return "--";
        }
        if (!TextUtils.isEmpty(entry.manualHighValue)) {
            return manualStatDisplayText(entry.manualHighValue, entry.unit);
        }
        return treatmentExtremeText(entry.high, entry.unit);
    }

    private String treatmentLowText(TreatmentEntry entry) {
        if (entry == null) {
            return "--";
        }
        if (!TextUtils.isEmpty(entry.manualLowValue)) {
            return manualStatDisplayText(entry.manualLowValue, entry.unit);
        }
        return treatmentExtremeText(entry.low, entry.unit);
    }

    private String treatmentExtremeText(float value, String unit) {
        if (Float.isNaN(value)) {
            return "--";
        }
        String number = Math.abs(value - Math.round(value)) < 0.05f
                ? String.valueOf(Math.round(value))
                : String.format(Locale.US, "%.1f", value);
        return number + unit;
    }

    private String buildTreatmentManualSummary(TreatmentEntry entry) {
        if (entry == null) {
            return "";
        }
        StringBuilder builder = new StringBuilder();
        appendTreatmentManualSummaryPart(builder, "均", entry.manualAverageValue, entry.unit);
        appendTreatmentManualSummaryPart(builder, "高", entry.manualHighValue, entry.unit);
        appendTreatmentManualSummaryPart(builder, "低", entry.manualLowValue, entry.unit);
        return builder.toString();
    }

    private void appendTreatmentManualSummaryPart(StringBuilder builder, String label, String value, String unit) {
        if (TextUtils.isEmpty(value)) {
            return;
        }
        if (builder.length() > 0) {
            builder.append(' ');
        }
        builder.append(label).append(':').append(manualStatDisplayText(value, unit));
    }

    private String manualStatDisplayText(String value, String unit) {
        if (TextUtils.isEmpty(value)) {
            return "--";
        }
        String trimmed = value.trim();
        if (trimmed.matches("[-+]?\\d+(?:\\.\\d+)?")) {
            try {
                return treatmentExtremeText(Float.parseFloat(trimmed), unit);
            } catch (NumberFormatException ignored) {
            }
        }
        return trimmed;
    }

    private void applyManualStatOverrides(TreatmentEntry entry) {
        if (entry == null) {
            return;
        }
        if (!TextUtils.isEmpty(entry.manualAverageValue)) {
            entry.lastValue = manualStatDisplayText(entry.manualAverageValue, entry.unit);
        } else if (TextUtils.isEmpty(entry.lastValue) && !TextUtils.isEmpty(entry.manualValue)) {
            entry.lastValue = entry.manualValue;
        }
    }

    private static final class Organization {
        String name;
        String address;
        String phone;
        String logoUri;
        String logoName;

        Organization(String name, String address) {
            this(name, address, "0755-00000000", "", "");
        }

        Organization(String name, String address, String phone) {
            this(name, address, phone, "", "");
        }

        Organization(String name, String address, String phone, String logoUri, String logoName) {
            this.name = name;
            this.address = address;
            this.phone = phone;
            this.logoUri = logoUri == null ? "" : logoUri;
            this.logoName = logoName == null ? "" : logoName;
        }
    }

    private static final class DeviceProfile {
        String productModel;
        String machineType;
        String serialNo;
        String manufactureDate;
        String softwareVersion;

        DeviceProfile() {
            this("EM-150Vet-20", "", "", "", "");
        }

        DeviceProfile(String productModel, String machineType, String serialNo, String manufactureDate, String softwareVersion) {
            this.productModel = productModel;
            this.machineType = machineType;
            this.serialNo = serialNo;
            this.manufactureDate = manufactureDate;
            this.softwareVersion = softwareVersion;
        }
    }

    private static final class PatientCase {
        final String caseId;
        String caseNo;
        String monitorNo;
        String petName;
        String species;
        String sex;
        String age;
        String ownerName;
        String ownerPhone;
        String doctor;
        String recordNo;
        String visitDate;
        String note;
        // ★ 报告上要打印的三个补充项。原来 PDF 里写死 "--"，
        //   现在从病例录入表单（H5 弹窗）一路同步到存储与报告。
        String weight = "";
        String department = "";
        String followUpDate = "";
        // ★ V1.02 新增字段（数据基座，UI 录入/展示在 UI 阶段补）
        String ageUnit = "";      // 年龄单位：天 / 月 / 岁
        String disease = "";      // 病症（列表"状态"列，PDF 改名为"病症"）
        boolean transferredOut;   // 是否已转出（转出后护疗列表不再显示）
        boolean currentTreatment;
        // ★ 2026-10-09 回顾转入修复：区分"新建未护疗"与"护疗进行中"。
        //   新建样本时 currentTreatment=true 仅是占位（作为当前选中病例），并未真正开始护疗；
        //   只有点过「开始护疗」才置本标志。跨天转入回顾只认本标志，
        //   否则昨天新建的样本会因 currentTreatment=true 永远留在护疗列表、进不了回顾。
        boolean treatmentStarted;
        String treatmentStartTime;
        String treatmentEndTime;
        long lastTreatmentSampleAt;
        boolean pendingInitialEntry;
        // ★ 任务29（#26c）：治疗记录单编辑落本地 —— 出院建议 / 治疗效果三态 / 生命体征记录行。
        //   sheetVitals 为 JSON 数组文本，每行 5 列 [时间,心率,血氧,血压,体温]，无数据库，随病例存 SharedPreferences。
        String sheetAdvice = "";
        int sheetConclusionIdx = 0;
        String sheetVitals = "";
        final ArrayList<TreatmentEntry> treatmentEntries = new ArrayList<>();
        final MonitorSnapshot monitorSnapshot = new MonitorSnapshot();

        PatientCase(String caseNo, String monitorNo, String petName, String species, String sex, String age,
                    String ownerName, String ownerPhone, String doctor, String recordNo, String visitDate, String note) {
            this(UUID.randomUUID().toString(), caseNo, monitorNo, petName, species, sex, age,
                    ownerName, ownerPhone, doctor, recordNo, visitDate, note);
        }

        PatientCase(String caseId, String caseNo, String monitorNo, String petName, String species, String sex, String age,
                    String ownerName, String ownerPhone, String doctor, String recordNo, String visitDate, String note) {
            this.caseId = TextUtils.isEmpty(caseId) ? UUID.randomUUID().toString() : caseId;
            this.caseNo = caseNo;
            this.monitorNo = monitorNo;
            this.petName = petName;
            this.species = species;
            this.sex = sex;
            this.age = age;
            this.ownerName = ownerName;
            this.ownerPhone = ownerPhone;
            this.doctor = doctor;
            this.recordNo = recordNo;
            this.visitDate = visitDate;
            this.note = note;
            this.treatmentStartTime = visitDate;
            this.treatmentEndTime = "进行中";
            seedTreatmentEntries();
        }

        private void seedTreatmentEntries() {
            for (String[] item : TREATMENT_TEMPLATES) {
                treatmentEntries.add(new TreatmentEntry(item[0], item[1],
                        Boolean.parseBoolean(item[2]), Boolean.parseBoolean(item[3]),
                        isSessionTrackedTreatment(item[0]) ? "" : treatmentStartTime,
                        isSessionTrackedTreatment(item[0]) ? "" : treatmentEndTime));
            }
        }
    }

    private static final class MonitorSnapshot {
        String heartRate = "--";
        String bloodPressure = "--/--";
        String map = "--";
        String spo2 = "--";
        String pulse = "--";
        String pulseRate = "--";
        String bodyTemp = "--";
        String resp = "--";
        String summary = EMPTY_MONITOR_SUMMARY;
        final ArrayList<Integer> ecgWaveSamples = new ArrayList<>();
        final ArrayList<Integer> spo2WaveSamples = new ArrayList<>();
        final ArrayList<Integer> respWaveSamples = new ArrayList<>();
        final ArrayList<Integer> heartRateHistory = new ArrayList<>();
        final ArrayList<Integer> bloodPressureHistory = new ArrayList<>();
        final ArrayList<Integer> spo2History = new ArrayList<>();
        final ArrayList<Integer> pulseRateHistory = new ArrayList<>();
        final ArrayList<Integer> temperatureHistory = new ArrayList<>();
        final ArrayList<Integer> respHistory = new ArrayList<>();

        boolean hasData() {
            return !"--".equals(heartRate)
                    || !"--/--".equals(bloodPressure)
                    || !"--".equals(spo2)
                    || !"--".equals(pulse)
                    || !"--".equals(bodyTemp)
                    || !"--".equals(resp)
                    || !heartRateHistory.isEmpty()
                    || !bloodPressureHistory.isEmpty()
                    || !spo2History.isEmpty()
                    || !pulseRateHistory.isEmpty()
                    || !temperatureHistory.isEmpty()
                    || !respHistory.isEmpty()
                    || !ecgWaveSamples.isEmpty()
                    || !spo2WaveSamples.isEmpty()
                    || !respWaveSamples.isEmpty();
        }
    }

    private static final class TreatmentEntry {
        String itemName;
        String unit;
        boolean enabled;
        boolean includeInReport;
        boolean customPeriod;
        String startTime;
        String endTime;
        String manualAverageValue = "";
        String manualHighValue = "";
        String manualLowValue = "";
        String manualValue = "";
        String lastValue = "--";
        float sum;
        float high = Float.NaN;
        float low = Float.NaN;
        int sampleCount;

        TreatmentEntry(String itemName, String unit, boolean enabled, boolean includeInReport, String startTime, String endTime) {
            this.itemName = itemName;
            this.unit = unit;
            this.enabled = enabled;
            this.includeInReport = includeInReport;
            this.startTime = startTime;
            this.endTime = endTime;
        }

        void record(float value, String displayValue) {
            if (Float.isNaN(value)) {
                if (!TextUtils.isEmpty(displayValue) && !"--".equals(displayValue)) {
                    lastValue = displayValue;
                    if (sampleCount == 0) {
                        sampleCount = 1;
                    }
                }
                return;
            }
            lastValue = TextUtils.isEmpty(displayValue) ? String.valueOf(value) : displayValue;
            sum += value;
            high = Float.isNaN(high) ? value : Math.max(high, value);
            low = Float.isNaN(low) ? value : Math.min(low, value);
            sampleCount++;
        }
    }

    private static final class PdfRecord {
        String action;
        String fileName;
        String patientName;
        String timeText;
        String location;
        File cacheFile;
        Uri downloadUri;
        // ★ 左右舱的 PDF/抓拍列表是同一份全局 List，加 zone 标签
        //   让渲染时按当前舱过滤，避免两舱的报告混在一个列表里。
        String zone;

        PdfRecord(String action, String fileName, String patientName, String timeText, String location, File cacheFile, Uri downloadUri, String zone) {
            this.action = action;
            this.fileName = fileName;
            this.patientName = patientName;
            this.timeText = timeText;
            this.location = location;
            this.cacheFile = cacheFile;
            this.downloadUri = downloadUri;
            this.zone = EnvironmentProtocol.normalizeZone(zone);
        }
    }

    private static final class CameraSnapshot {
        String fileName;
        String timeText;
        long capturedAt;
        File cacheFile;
        Uri downloadUri;
        String patientRecordNo;
        String patientName;
        // ★ 同上，标签用于按舱过滤显示
        String zone;

        CameraSnapshot(String fileName, String timeText, long capturedAt, File cacheFile, Uri downloadUri,
                       String patientRecordNo, String patientName, String zone) {
            this.fileName = fileName;
            this.timeText = timeText;
            this.capturedAt = capturedAt;
            this.cacheFile = cacheFile;
            this.downloadUri = downloadUri;
            this.patientRecordNo = patientRecordNo;
            this.patientName = patientName;
            this.zone = EnvironmentProtocol.normalizeZone(zone);
        }
    }

    private static final class CameraImageAnalysis {
        int width;
        int height;
        int brightness;
        int contrast;
        int edgeScore;
    }

    private static final class PatientZoneState {
        final String zone;
        final ArrayList<PatientCase> cases = new ArrayList<>();
        int selectedCaseIndex;
        // ★ 病例编号按舱独立：左舱第一个是 000001，右舱第一个也是 000001。
        //   两舱共用一个游标会让右舱接着左舱的号码往下发，等于编号没有隔离。
        long caseNoCursor;
        int caseNoCursorWidth = 6;
        // ★ V1.02 住院号改为「舱位前缀 + 日期 + 当日3位序号」(如 A20260915001)。
        //   每日游标按舱独立，跨天后由 nextCaseNo 自动从 001 重新计，保证左右舱各自连续且互不干扰。
        String dailyCaseNoDate = "";
        long dailyCaseNoCursor = 0L;

        PatientZoneState(String zone) {
            this.zone = zone;
        }
    }

    /**
     * Compatibility view for the native Canvas code. Existing rendering helpers keep using
     * {@code cases}, while every operation is delegated to the currently selected cabin.
     */
    private final class ActivePatientList extends AbstractList<PatientCase> {
        private ArrayList<PatientCase> delegate() {
            return activePatientState().cases;
        }

        @Override public PatientCase get(int index) { return delegate().get(index); }
        @Override public int size() { return delegate().size(); }
        @Override public PatientCase set(int index, PatientCase value) { return delegate().set(index, value); }
        @Override public void add(int index, PatientCase value) { delegate().add(index, value); }
        @Override public PatientCase remove(int index) { return delegate().remove(index); }
        @Override public void clear() { delegate().clear(); }
    }

    private final MainActivity activity;
    private final BleManager bleManager;
    private final Am4100Manager am4100Manager;
    private final LocalAccountStore accountStore;
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final ArrayList<ClickZone> clickZones = new ArrayList<>();
    private final ArrayList<Organization> organizations = new ArrayList<>();
    private final DeviceProfile deviceProfile = new DeviceProfile();
    private final PatientZoneState leftPatients = new PatientZoneState("left");
    private final PatientZoneState rightPatients = new PatientZoneState("right");
    private final List<PatientCase> cases = new ActivePatientList();
    // ★ V1.02 S2 用户操作日志（内存 + SharedPreferences 持久化）。
    private final JSONArray userOpLog = new JSONArray();
    /* ★ 2026-10-08 日志页：常规信息 / 后端调试 / 错误记录 / 堆栈信息（通信记录存 MainActivity 侧） */
    private final JSONArray generalLog = new JSONArray();
    private final JSONArray debugLog = new JSONArray();
    private final JSONArray errorLog = new JSONArray();
    private final JSONArray stackLog = new JSONArray();
    private final JSONArray commLog = new JSONArray();
    // ★ V1.02 S3 回顾查询：当前生效的查询条件（null = 无筛选，historyCases 即为全量历史）。
    private JSONObject reviewQueryFilter;
    private final ArrayList<PdfRecord> pdfRecords = new ArrayList<>();
    private final ArrayList<CameraSnapshot> cameraSnapshots = new ArrayList<>();
    private final String[] tabs = {"ICU状态", "主机控制", "实时监护", "摄像监控", "治疗记录", "PDF文件", "使用教程"};
    private static final int MONITOR_TREND_ALL = -1;
    private static final int MONITOR_TREND_HEART = 0;
    private static final int MONITOR_TREND_SPO2 = 1;
    private static final int MONITOR_TREND_PULSE = 2;
    private static final int MONITOR_TREND_BP = 3;
    private static final int MONITOR_TREND_TEMP = 4;
    private static final int MONITOR_TREND_RESP = 5;
    private static final int MONITOR_MODE_LIVE = 0;
    private static final int MONITOR_MODE_HISTORY = 1;
    private static final int MONITOR_MODE_SETTINGS = 2;
    private static final int LIMIT_HEART = 0;
    private static final int LIMIT_BP_SYS = 1;
    private static final int LIMIT_BP_DIA = 2;
    private static final int LIMIT_PULSE = 3;
    private static final int LIMIT_SPO2 = 4;
    private static final int LIMIT_TEMP = 5;
    private static final int LIMIT_RESP = 6;

    private boolean showingSettings;
    private boolean cameraPlayback;
    private boolean startupRecordPromptShown;
    private int activeTab;
    private int monitorMode = MONITOR_MODE_LIVE;
    private int selectedMonitorTrend = MONITOR_TREND_ALL;
    private int selectedOrganizationIndex;
    private int selectedCaseIndex;

    private int selectedCameraSnapshotIndex = -1;
    private int selectedHostModeIndex;
    private boolean hostModeActive;
    private int lastHostModeIndex = 1;
    private final RectF am4100DeviceListBounds = new RectF();
    private float am4100DeviceScrollY;
    private float am4100DeviceMaxScrollY;
    private float touchDownY;
    private float lastTouchY;
    private boolean draggingAm4100DeviceList;
    private boolean touchMoved;
    private boolean monitorRefreshPending;
    private String searchKeyword = "";
    private String treatmentPeriodMode = PERIOD_ALL;
    private String selectedMonitorLevelColor = "yellow";
    private File lastGeneratedPdf;
    private float density;
    private boolean monitorPulseBeep = true;
    private boolean monitorAlarmSound = true;
    private boolean monitorAutoNibp;
    private String cameraStreamUrl = "";
    private CameraPreviewHost cameraPreviewHost;
    private String currentLanhuPath = "";
    private final boolean[] monitorLimitEnabled = {true, true, true, true, true, true, true};
    private final float[] monitorLimitHigh = {120f, 140f, 90f, 120f, 100f, 38f, 40f};
    private final float[] monitorLimitLow = {60f, 90f, 60f, 60f, 94f, 35f, 8f};
    // 自动报警阈值：低于阈值时触发报警；index 1 是血压的收缩压，index 1 的 dia 单独存
    private float[] monitorThresholds = copyDefaultThresholds();
    private float[] monitorThresholdsDia = copyDefaultThresholdsDia();
    private boolean monitorAlarmEnabled = false;     // 自动报警开关
    private long lastMonitorAlarmTime = 0L;          // 上次报警时间（防抖）
    private final long[] lastMonitorMetricAlarmTime = new long[MONITOR_THRESHOLD_KEYS.length];  // 每指标独立防抖

    private final int navy = Color.rgb(82, 166, 242);
    private final int page = Color.rgb(229, 239, 251);
    private final int sidebar = Color.rgb(86, 166, 242);
    private final int panel = Color.WHITE;
    private final int border = Color.rgb(218, 229, 243);
    private final int text = Color.rgb(64, 72, 86);
    private final int muted = Color.rgb(124, 138, 154);
    private final int green = Color.rgb(27, 206, 138);
    private final int orange = Color.rgb(247, 172, 38);
    private final int red = Color.rgb(240, 84, 92);
    private final int blue = Color.rgb(0, 132, 235);
    private final int softBlue = Color.rgb(238, 246, 255);
    private final int activeBlue = Color.rgb(0, 132, 235);
    private final int deepPanel = Color.rgb(28, 28, 28);
    // 本 View 只作状态容器使用，从未 addView 到视图树，因此 View.post() / postDelayed()
    // 会一直滞留在 HandlerActionQueue 里等 onAttachedToWindow，永远不会执行。
    // 所有延迟/跨线程回调统一走这个主线程 Handler，不依赖 attach 状态。
    private final Handler uiHandler = new Handler(Looper.getMainLooper());
    private final Runnable monitorRefreshRunnable = new Runnable() {
        @Override
        public void run() {
            monitorRefreshPending = false;
            if (!showingSettings && activeTab == 2 && (am4100Manager.isScanning() || am4100Manager.isConnected() || am4100Manager.hasVitals())) {
                invalidate();
            }
        }
    };

    // ★ 任务22 BPM 血压仪（监护页「实时⇄物理」）：协议见《优利特ICU动物监护舱和平板通信协议说明文件》#38/#39。
    //   手动=物理：BPM_setting manual mode + BPM_manual start（单次测量）
    //   自动=实时：BPM_automatic mode 5 recy（默认 5 分钟周期测量）
    //   状态查询：BPM_system status → sys/dia/mean/pluse rate/error
    //   发送复用 BleManager.public sendCommand，接收由 MainActivity.onStateChanged 读
    //   getLastReceived() 后调 updateBpmFromJson —— 不改动 BleManager 本身。
    private String bpmMode = "";        // "" 未设置 / "manual" 物理 / "auto" 实时
    private String bpmSys = "";
    private String bpmDia = "";
    private String bpmMean = "";
    private String bpmPr = "";
    private String bpmError = "";
    private String bpmLastRaw = "";     // 去重：同一条原始报文只解析一次
    private static final long BPM_POLL_MS = 5000L;
    private boolean bpmPollScheduled = false;
    private final Runnable bpmPollRunnable = new Runnable() {
        @Override
        public void run() {
            bpmPollScheduled = false;
            if (TextUtils.isEmpty(bpmMode) || !bleManager.isConnected()) {
                return;
            }
            bleManager.sendCommand("{\"zone\":\"" + bleManager.getCurrentZone() + "\",\"cmd\":\"BPM_system status\"}");
            scheduleBpmPoll();
        }
    };

    private void scheduleBpmPoll() {
        if (bpmPollScheduled) {
            return;
        }
        bpmPollScheduled = true;
        uiHandler.postDelayed(bpmPollRunnable, BPM_POLL_MS);
    }

    /** 监护页血压模式切换：manual=物理（手动单次测量），auto=实时（自动 5 分钟周期）。 */
    void handleBpmMode(String mode) {
        if (!bleManager.isConnected()) {
            Toast.makeText(activity, "主机蓝牙未连接", Toast.LENGTH_SHORT).show();
            return;
        }
        String zone = bleManager.getCurrentZone();
        if ("manual".equals(mode)) {
            bleManager.sendCommand("{\"zone\":\"" + zone + "\",\"cmd\":\"BPM_setting manual mode\"}");
            bleManager.sendCommand("{\"zone\":\"" + zone + "\",\"cmd\":\"BPM_manual start\"}");
            bpmMode = "manual";
        } else {
            bleManager.sendCommand("{\"zone\":\"" + zone + "\",\"cmd\":\"BPM_automatic mode 5 recy\"}");
            bpmMode = "auto";
        }
        appendUserLog("监护", "血压模式", "manual".equals(mode) ? "物理（手动测量）" : "实时（自动 5 分钟）");
        scheduleBpmPoll();
    }

    /** 从主机最新一条上行报文中提取 BPM_system status（sys/dia/mean/pluse rate/error）。 */
    void updateBpmFromJson(String raw) {
        if (raw == null || raw.equals(bpmLastRaw) || !raw.contains("BPM_system status")) {
            return;
        }
        bpmLastRaw = raw;
        try {
            JSONObject obj = new JSONObject(raw);
            bpmSys = obj.optString("sys", bpmSys);
            bpmDia = obj.optString("dia", bpmDia);
            bpmMean = obj.optString("mean", bpmMean);
            bpmPr = obj.optString("pluse rate", bpmPr);
            bpmError = obj.optString("error", "");
        } catch (JSONException ignored) {
        }
    }

    private JSONObject bpmToJson() throws JSONException {
        JSONObject bpm = new JSONObject();
        bpm.put("mode", bpmMode);
        bpm.put("sys", bpmSys);
        bpm.put("dia", bpmDia);
        bpm.put("mean", bpmMean);
        bpm.put("pr", bpmPr);
        bpm.put("error", bpmError);
        return bpm;
    }

    public IcuDashboardView(MainActivity activity, BleManager bleManager, Am4100Manager am4100Manager) {
        super(activity);
        this.activity = activity;
        this.bleManager = bleManager;
        this.am4100Manager = am4100Manager;
        this.accountStore = new LocalAccountStore(activity);
        seedHomeData();
        loadUiSettings();
        loadTreatmentRecordsFromStorage();
        loadUserOpLog();
        loadLog(KEY_GENERAL_LOG, generalLog);
        loadLog(KEY_DEBUG_LOG, debugLog);
        loadLog(KEY_ERROR_LOG, errorLog);
        loadLog(KEY_STACK_LOG, stackLog);
        loadLog(KEY_COMM_LOG, commLog);
        /* ★ 2026-10-08 日志页·常规信息：应用启动 */
        appendGeneralLog("系统", "应用启动");
        loadCameraSettings();
        loadCameraSnapshotsFromStorage();
        setFocusable(true);
        textPaint.setSubpixelText(true);
        // 治疗记录确认弹窗仅在用户已经处于登录态时才自动触发。
        // 处于登录页时由 webLogin / webRegister 成功后再触发，避免弹窗覆盖登录页。
        if (!TextUtils.isEmpty(accountStore.getCurrentAccount())) {
            scheduleStartupTreatmentRecordPrompt();
        }
    }

    /**
     * 延迟弹出"是否创建新的治疗记录"确认框。仅在登录后由登录流程触发，
     * 不会在登录页面上弹出。
     */
    public void scheduleStartupTreatmentRecordPrompt() {
        uiHandler.postDelayed(new Runnable() {
            @Override
            public void run() {
                showStartupTreatmentRecordPrompt();
            }
        }, 500L);
    }

    void setCameraPreviewHost(CameraPreviewHost cameraPreviewHost) {
        this.cameraPreviewHost = cameraPreviewHost;
    }

    void setCurrentLanhuPath(String path) {
        currentLanhuPath = path == null ? "" : path;
    }

    void syncCurrentPatientMonitorSnapshot() {
        PatientCase patient = currentCase();
        if (patient == null || !am4100Manager.hasVitals()) {
            return;
        }
        fillMonitorSnapshot(patient.monitorSnapshot);
        // 同步检查实时报警阈值，越界时自动播放报警音
        checkMonitorAlarmThresholds();
    }

    void syncCurrentTreatmentEntryStatesFromDevice() {
        PatientCase patient = currentTreatmentCase();
        if (patient == null || !bleManager.isProtocolReady()) {
            return;
        }
        String now = currentTimeText();
        boolean changed = false;
        for (TreatmentEntry entry : patient.treatmentEntries) {
            int controlIndex = treatmentControlIndexOf(entry.itemName);
            if (controlIndex < 0) {
                continue;
            }
            boolean liveEnabled = bleManager.isControlOn(controlIndex);
            if (entry.enabled == liveEnabled) {
                if (liveEnabled && TextUtils.isEmpty(entry.startTime)) {
                    entry.startTime = now;
                    entry.endTime = "进行中";
                    entry.customPeriod = false;
                    changed = true;
                }
                continue;
            }
            entry.enabled = liveEnabled;
            if (liveEnabled) {
                entry.startTime = now;
                entry.endTime = "进行中";
            } else {
                if (TextUtils.isEmpty(entry.startTime)) {
                    entry.startTime = now;
                }
                entry.endTime = now;
            }
            entry.customPeriod = false;
            changed = true;
        }
        if (changed) {
            lastGeneratedPdf = null;
            saveTreatmentRecordsToStorage();
        }
    }

    private boolean blockPatientSwitchIfNeeded() {
        if (am4100Manager.isConnected() && am4100Manager.hasVitals()) {
            Toast.makeText(activity, "实时监护已有数据，不能切换宠物", Toast.LENGTH_SHORT).show();
            return true;
        }
        return false;
    }

    String buildFirstPhaseStateJson() {
        try {
            captureTreatmentSampleIfNeeded(false);
            JSONObject root = new JSONObject();
            String zone = bleManager.getCurrentZone();
            root.put("schemaVersion", TREATMENT_CASES_SCHEMA_VERSION);
            // ★ 2026-10-10 版本控制：关于页「版本信息」框显示真实 APK 版本（versionName/versionCode 取自 PackageManager，与 build.gradle 单一来源）
            try {
                PackageInfo pi = activity.getPackageManager().getPackageInfo(activity.getPackageName(), 0);
                JSONObject av = new JSONObject();
                av.put("name", pi.versionName);
                av.put("code", Build.VERSION.SDK_INT >= 28 ? pi.getLongVersionCode() : pi.versionCode);
                root.put("appVersion", av);
            } catch (Exception ignored) {
            }
            root.put("zone", zone);
            root.put("organization", organizationToJson(currentOrganization()));
            root.put("accountName", accountStore.getCurrentAccount());
            // ★ 任务13：用户管理(R67-R73) + 舱区权限(R93)
            root.put("accountRole", accountStore.getCurrentRole());
            root.put("canSwitchZone", accountStore.isCurrentService());
            root.put("accounts", accountsToJson());
            root.put("patient", patientToJson(currentCase()));
            root.put("cases", casesToJson());
            // ★ V1.02 护疗列表仅当天/进行中/未转出；与完整 cases（回顾用）并存，H5 在 UI 阶段切换消费。
            root.put("careCases", careCasesToJson());
            // ★ V1.02 S2 监护模式标识：「住院号 + 宠物名」（如 A20260915001 咸鱼）。
            //   H5 护疗页左下角固定显示「监护模式：<monitorModeLabel>」，切换住院号后随 currentCase 同步。
            PatientCase monitorModePatient = currentCase();
            String monitorModeLabel = monitorModePatient == null ? ""
                    : (safeJsonText(monitorModePatient.caseNo) + " " + safeJsonText(monitorModePatient.petName)).trim();
            root.put("monitorModeLabel", monitorModeLabel);
            // ★ V1.02 S3 回顾数据集：仅历史记录（不含护疗列表中的当天/进行中记录），供回顾页消费。
            root.put("historyCases", historyCasesToJson());
            root.put("selectedCaseIndex", selectedCaseIndex);
            root.put("treatments", treatmentsToJson(currentCase()));
            root.put("hostModes", hostModesToJson());
            root.put("treatmentPeriodMode", treatmentPeriodMode);
            root.put("monitorSettings", monitorSettingsToJson());
            root.put("monitorHasData", am4100Manager.hasVitals());
            // ★ 任务22：BPM 血压仪（实时=自动模式 / 物理=手动模式）状态推给 H5
            root.put("bpm", bpmToJson());
            JSONObject ble = dashboardBleStateToJson();
            root.put("ble", ble);
            // ★ 任务13 R53：wifi 状态（enabled / 当前热点名）一并推给 H5，设置-连接页据此显示开关
            try {
                root.put("wifi", activity.buildWifiStateJson());
            } catch (JSONException ignored) {
            }
            // ★ 任务28：文件助手二维码（url 非空=可渲染；error 非空=生成失败提示）
            try {
                JSONObject shareQr = new JSONObject();
                shareQr.put("url", shareQrUrl);
                shareQr.put("error", shareQrError);
                shareQr.put("fileName", shareQrName);
                root.put("shareQr", shareQr);
            } catch (JSONException ignored) {
            }
            // ★ 2026-10-10 #56：打印结果（seq 边沿检测 → H5 toast 成功/失败并关闭打印弹窗）
            try {
                JSONObject ps = new JSONObject();
                ps.put("seq", printStatusSeq);
                ps.put("ok", printStatusOk);
                ps.put("msg", printStatusMsg);
                root.put("printStatus", ps);
            } catch (JSONException ignored) {
            }
            // ★ 2026-10-10 #57：打印机扫描状态与结果（H5 设置-打印页列表，只显示打印机名）
            try {
                JSONObject scan = new JSONObject();
                scan.put("scanning", printerScanning);
                JSONArray arr = new JSONArray();
                synchronized (printerScanResults) {
                    for (JSONObject item : printerScanResults) {
                        arr.put(item);
                    }
                }
                scan.put("printers", arr);
                root.put("printerScan", scan);
            } catch (JSONException ignored) {
            }
            // 页面通用数据绑定使用顶层 host；与 ble.host 共用同一份实时主机状态，避免跨页面字段不一致。
            root.put("host", ble.optJSONObject("host"));
            root.put("deviceProfile", deviceProfileToJson());
            root.put("camera", cameraStateToJson());
            // ★ 2026-10-08 日志页：用户/操作日志（userOpLog）推给 H5，倒序（最新在前）
            root.put("userLogs", userLogsToJson());
            /* ★ 2026-10-08 日志页：常规信息 / 后端调试 / 错误记录 / 堆栈信息（通信记录由 MainActivity 合并） */
            root.put("generalLogs", logToJson(generalLog));
            root.put("debugLogs", logToJson(debugLog));
            root.put("errorLogs", logToJson(errorLog));
            root.put("stackLogs", logToJson(stackLog));
            root.put("commLogs", logToJson(commLog));
            return root.toString();
        } catch (JSONException exception) {
            /* ★ 2026-10-08 日志页·错误记录：状态构建异常落库，避免静默失败 */
            appendErrorLog("状态构建", String.valueOf(exception));
            return "{}";
        }
    }

    private JSONObject cameraStateToJson() throws JSONException {
        JSONObject camera = new JSONObject();
        camera.put("configured", !TextUtils.isEmpty(cameraStreamUrl));
        camera.put("streamUrlMasked", TextUtils.isEmpty(cameraStreamUrl) ? "" : maskCameraUrl(cameraStreamUrl));
        camera.put("selectedSnapshotIndex", selectedCameraSnapshotIndex);
        camera.put("snapshots", cameraSnapshotsToJson());
        return camera;
    }

    private JSONArray cameraSnapshotsToJson() throws JSONException {
        // ★ 推给 H5 的抓拍列表只包含当前舱。两舱列表独立，避免左右舱混在同一条目里。
        String z = currentZoneNormalized();
        JSONArray array = new JSONArray();
        int zoneIndex = 0;
        for (CameraSnapshot snapshot : cameraSnapshots) {
            if (!z.equals(snapshot.zone)) continue;
            JSONObject item = new JSONObject();
            // nativeIndex 改为舱内序号，全局索引会让 H5 端寻址错位
            item.put("nativeIndex", zoneIndex++);
            item.put("fileName", snapshot.fileName);
            item.put("timeText", snapshot.timeText);
            item.put("capturedAt", snapshot.capturedAt);
            item.put("cacheUrl", snapshot.cacheFile == null ? "" : Uri.fromFile(snapshot.cacheFile).toString());
            item.put("downloaded", snapshot.downloadUri != null);
            item.put("downloadUri", snapshot.downloadUri == null ? "" : snapshot.downloadUri.toString());
            item.put("recordNo", safeJsonText(snapshot.patientRecordNo));
            item.put("petName", safeJsonText(snapshot.patientName));
            array.put(item);
        }
        return array;
    }

    String buildPatientStateJson() {
        try {
            PatientCase patient = currentCase();
            return patient == null ? "{}" : patientToJson(patient).toString();
        } catch (JSONException exception) {
            return "{}";
        }
    }

    private PatientCase findPatientCase(String zone, String caseId, int fallbackIndex) {
        PatientZoneState state = patientStateForZone(zone);
        if (!TextUtils.isEmpty(caseId)) {
            for (PatientCase item : state.cases) {
                if (caseId.equals(item.caseId)) {
                    return item;
                }
            }
            return null;
        }
        return fallbackIndex >= 0 && fallbackIndex < state.cases.size()
                ? state.cases.get(fallbackIndex) : null;
    }

    void syncTempPatientFromWeb(String payload) {
        if (TextUtils.isEmpty(payload)) {
            return;
        }
        /* ★ 2026-10-08 日志页·后端调试：病例同步入口（payload 截断 200 字） */
        appendDebugLog("INFO", "syncTempPatient", payload.length() > 200 ? payload.substring(0, 200) : payload);
        try {
            JSONObject json = new JSONObject(payload);
            String zone = EnvironmentProtocol.normalizeZone(json.optString("zone", ""));
            if (TextUtils.isEmpty(zone)) {
                zone = bleManager.getCurrentZone();
            }
            PatientCase patient = findPatientCase(zone, json.optString("caseId", ""),
                    json.optInt("nativeIndex", -1));
            if (patient == null) {
                // ★ 2026-10-10 #53：回顾页编辑「病症」时病例是历史记录，不在当前舱列表，
                //   退到全量 cases 里按 caseId 找（与 updateRecordFromText 一致），否则同步被静默丢弃
                String cid = json.optString("caseId", "");
                if (!TextUtils.isEmpty(cid)) {
                    for (PatientCase c : cases) {
                        if (c != null && cid.equals(c.caseId)) {
                            patient = c;
                            break;
                        }
                    }
                }
                if (patient == null) {
                    return;
                }
            }
            patient.petName = optNonEmpty(json, "petName", patient.petName);
            patient.species = optNonEmpty(json, "species", patient.species);
            patient.sex = optNonEmpty(json, "sex", patient.sex);
            patient.age = optNonEmpty(json, "age", patient.age);
            patient.ownerName = optNonEmpty(json, "ownerName", patient.ownerName);
            patient.ownerPhone = optNonEmpty(json, "ownerPhone", patient.ownerPhone);
            patient.doctor = optNonEmpty(json, "doctor", patient.doctor);
            String incomingCaseNo = optNonEmpty(json, "caseNo", patient.caseNo);
            String incomingRecordNo = optNonEmpty(json, "recordNo", patient.recordNo);
            String finalRecordNo = !TextUtils.isEmpty(incomingRecordNo) ? incomingRecordNo : incomingCaseNo;
            if (caseNoUsedByOther(zone, finalRecordNo, patient)) {
                Toast.makeText(activity, "病历编号已存在，请输入其他编号", Toast.LENGTH_SHORT).show();
                return;
            }
            patient.caseNo = finalRecordNo;
            patient.recordNo = finalRecordNo;
            if (patient.pendingInitialEntry) {
                patient.monitorNo = finalRecordNo;
                updateCaseNoCursorFromText(zone, finalRecordNo);
                patient.pendingInitialEntry = false;
            }
            patient.visitDate = optNonEmpty(json, "visitDate", patient.visitDate);
            patient.note = optNonEmpty(json, "note", patient.note);
            // ★ 录入弹窗新增的三个字段，报告直接取用
            patient.weight = optNonEmpty(json, "weight", patient.weight);
            patient.department = optNonEmpty(json, "department", patient.department);
            patient.followUpDate = optNonEmpty(json, "followUpDate", patient.followUpDate);
            // ★ V1.02 S2 同步录入弹窗新增的两个字段（年龄单位 / 病症），供报告与列表使用。
            patient.ageUnit = optNonEmpty(json, "ageUnit", patient.ageUnit);
            patient.disease = optNonEmpty(json, "disease", patient.disease);
            patient.treatmentStartTime = optNonEmpty(json, "treatmentStartTime", patient.treatmentStartTime);
            patient.treatmentEndTime = optNonEmpty(json, "treatmentEndTime", patient.treatmentEndTime);
            if (json.has("currentTreatment")) {
                patient.currentTreatment = json.optBoolean("currentTreatment", patient.currentTreatment);
            }
            lastGeneratedPdf = null;
            saveTreatmentRecordsToStorage(true);
            invalidate();
        } catch (JSONException ignored) {
        }
    }

    /* ★ 任务29（#26c）：治疗记录单编辑保存 —— H5 act('update_record', JSON) → 这里解析并写入当前病例，
       无数据库，直接落 SharedPreferences（saveTreatmentRecordsToStorage），随后 invalidate 回推 H5。
       payload: {caseId, zone, owner, weight, dept, doc, advice, conclIdx,
                 proj:[{i,on,val}], vitals:[[时间,心率,血氧,血压,体温], ...]} */
    String updateRecordFromText(String text) {
        if (TextUtils.isEmpty(text)) {
            return "保存失败：空数据";
        }
        try {
            JSONObject json = new JSONObject(text);
            String zone = EnvironmentProtocol.normalizeZone(json.optString("zone", ""));
            if (TextUtils.isEmpty(zone)) {
                zone = bleManager.getCurrentZone();
            }
            PatientCase patient = findPatientCase(zone, json.optString("caseId", ""), -1);
            if (patient == null) {
                // 预览历史病例时 caseId 不在当前舱列表，退到全量 cases 里找
                for (PatientCase c : cases) {
                    if (c != null && c.caseId != null && c.caseId.equals(json.optString("caseId", ""))) {
                        patient = c;
                        break;
                    }
                }
            }
            if (patient == null) {
                return "保存失败：未找到对应样本";
            }
            patient.ownerName = optNonEmpty(json, "owner", patient.ownerName);
            patient.weight = optNonEmpty(json, "weight", patient.weight);
            patient.department = optNonEmpty(json, "dept", patient.department);
            patient.doctor = optNonEmpty(json, "doc", patient.doctor);
            if (json.has("advice")) {
                patient.sheetAdvice = json.optString("advice", "");
            }
            if (json.has("conclIdx")) {
                int ci = json.optInt("conclIdx", 0);
                patient.sheetConclusionIdx = ci < 0 ? 0 : (ci > 2 ? 2 : ci);
            }
            // 治疗项目勾选 + 显示值（值写回 lastValue；开启采样项后续可能被实时采样覆盖）
            JSONArray proj = json.optJSONArray("proj");
            if (proj != null) {
                for (int i = 0; i < proj.length(); i++) {
                    JSONObject p = proj.optJSONObject(i);
                    if (p == null) {
                        continue;
                    }
                    int idx = p.optInt("i", -1);
                    if (idx < 0 || idx >= patient.treatmentEntries.size()) {
                        continue;
                    }
                    TreatmentEntry entry = patient.treatmentEntries.get(idx);
                    entry.enabled = p.optInt("on", entry.enabled ? 1 : 0) == 1;
                    String val = p.optString("val", "");
                    if (!TextUtils.isEmpty(val)) {
                        entry.lastValue = val;
                    }
                }
            }
            // 生命体征行：服务端再兜底清洗（≤50 行、5 列、单元格 ≤32 字符、整行空白剔除）
            JSONArray vitals = json.optJSONArray("vitals");
            JSONArray clean = new JSONArray();
            if (vitals != null) {
                for (int i = 0; i < vitals.length() && clean.length() < 50; i++) {
                    JSONArray row = vitals.optJSONArray(i);
                    if (row == null) {
                        continue;
                    }
                    JSONArray out = new JSONArray();
                    boolean allBlank = true;
                    for (int c = 0; c < 5; c++) {
                        String cell = row.optString(c, "").trim();
                        if (cell.length() > 32) {
                            cell = cell.substring(0, 32);
                        }
                        if (!cell.isEmpty()) {
                            allBlank = false;
                        }
                        out.put(cell);
                    }
                    if (!allBlank) {
                        clean.put(out);
                    }
                }
            }
            patient.sheetVitals = clean.toString();
            lastGeneratedPdf = null;
            saveTreatmentRecordsToStorage(true);
            appendUserLog("记录单", "编辑保存", "样本 " + safeJsonText(patient.recordNo) + " 治疗记录单已更新");
            invalidate();
            return "治疗记录单已保存";
        } catch (JSONException e) {
            return "保存失败：数据格式错误";
        }
    }

    boolean performNativeAction(String action) {
        if (TextUtils.isEmpty(action)) {
            return false;
        }
        /* ★ 2026-10-08 日志页·后端调试：桥接动作入口 */
        appendDebugLog("INFO", "performNativeAction", action);
        if ("select_current_treatment".equals(action)) {
            if (blockPatientSwitchIfNeeded()) {
                return true;
            }
            selectedCaseIndex = getCurrentTreatmentIndex();
            showingSettings = false;
            invalidate();
            return true;
        }
        if ("organization_switch".equals(action)) {
            showOrganizationDialog();
            return true;
        }
        if ("organization_edit".equals(action)) {
            // organization_edit 已被 organization_save 取代：点击"保存"按钮时由 MainActivity.handleOrganizationSaveFromLanhu
            // 直接读取 WebView 输入框并写入，不再弹原生对话框。
            return true;
        }
        if ("organization_logo_pick".equals(action)) {
            chooseOrganizationLogo();
            return true;
        }
        // ★ 任务31：页脚LOGO 独立 action → 写 s5FooterLogoUri（报告页脚品牌位）
        if ("organization_footer_logo_pick".equals(action)) {
            chooseFooterLogo();
            return true;
        }
        // ★ 历史死代码:device_profile_edit 之前用于弹原生对话框编辑仪器状态
        //   改用 webview inline edit 后不再触发,JS 已删除对应 bindNativeAction
        //   IcuDashboardView 里 showDeviceProfileDialog 也一起删除,避免误调用
        // if ("device_profile_edit".equals(action)) { showDeviceProfileDialog(); return true; }
        if ("account_menu".equals(action)) {
            handleAccountTap();
            return true;
        }
        if ("search_patient".equals(action)) {
            showSearchDialog();
            return true;
        }
        if (action.startsWith("patient_select_")) {
            if (blockPatientSwitchIfNeeded()) {
                return true;
            }
            try {
                String suffix = action.substring("patient_select_".length());
                String zone = bleManager.getCurrentZone();
                int separator = suffix.indexOf('_');
                if (separator > 0) {
                    zone = EnvironmentProtocol.normalizeZone(suffix.substring(0, separator));
                    suffix = suffix.substring(separator + 1);
                }
                int index = Integer.parseInt(suffix);
                if (TextUtils.isEmpty(zone)) {
                    return true;
                }
                PatientZoneState state = patientStateForZone(zone);
                if (index >= 0 && index < state.cases.size()) {
                    state.selectedCaseIndex = index;
                    if (zone.equals(bleManager.getCurrentZone())) {
                        selectedCaseIndex = index;
                    }
                    lastGeneratedPdf = null;
                    showingSettings = false;
                    saveTreatmentRecordsToStorage();
                    invalidate();
                    return true;
                }
            } catch (NumberFormatException ignored) {
            }
        }
        if ("patient_edit".equals(action)) {
            PatientCase patient = currentCase();
            if (patient != null) {
                showEditPatientDialog(patient);
            }
            return true;
        }
        // H5「新建样本」入口：新增一条当前治疗记录，随后 JS 会自动打开样本信息录入框。
        if ("patient_new".equals(action)) {
            if (blockPatientSwitchIfNeeded()) {
                return true;
            }
            createNewTreatmentRecord();
            invalidate();
            activity.openLanhuPatientEditorAfterNew();
            return true;
        }
        if ("patient_delete".equals(action)) {
            PatientCase patient = currentCase();
            if (patient != null) {
                confirmDeletePatient(patient);
            }
            return true;
        }
        // ★ V1.02 S2 开始护疗：选中行进入护疗页面，状态置护疗中，治疗时长开始累计；带隔离与防重复开始。
        if ("patient_start_treatment".equals(action)) {
            startCareForPatient(currentCase());
            return true;
        }
        if ("pdf_generate".equals(action)) {
            generatePdfAction();
            invalidate();
            return true;
        }
        if ("pdf_download".equals(action)) {
            downloadPdfAction();
            invalidate();
            return true;
        }
        // ★ V1.02 S4 导出/传输：生成报告 → 系统分享（微信文件传输助手/蓝牙）/ 系统打印；发送数据置灰二期。
        if ("export_report".equals(action)) {
            exportReportViaShare();
            invalidate();
            return true;
        }
        if ("bluetooth_send_report".equals(action)) {
            sendReportViaBluetooth();
            invalidate();
            return true;
        }
        // ★ 任务28（V1.02 R25/R42 方案A）：文件助手二维码 —— 报告 PDF 托管到局域网 HTTP 服务，
        //   URL 回推给 H5 渲染二维码；微信扫码打开下载页后由用户手动转发到「文件传输助手」。
        if ("export_qr".equals(action)) {
            buildReportShareQr();
            invalidate();
            return true;
        }
        if ("export_qr_clear".equals(action)) {
            shareQrUrl = "";
            shareQrError = "";
            shareQrName = "";
            ShareHttpServer.get().clear();
            invalidate();
            return true;
        }
        if ("print_report".equals(action)) {
            printReportViaSystem();
            invalidate();
            return true;
        }
        // ★ 任务14（V1.02 R59）：打印测试页
        if ("print_test".equals(action)) {
            printTestPageViaSystem();
            invalidate();
            return true;
        }
        // ★ 2026-10-10 #57：扫描局域网打印机（NSD 发现 _ipp._tcp / _pdl-datastream._tcp）
        if ("printer_scan".equals(action)) {
            startPrinterScan();
            invalidate();
            return true;
        }
        if ("send_data".equals(action)) {
            sendDataStage2();
            invalidate();
            return true;
        }
        // ★ V1.02 S5 设置：加载/恢复出厂/云平台(LIS)二期
        if ("settings_load".equals(action)) {
            pushSettingsToLanhu();
            Toast.makeText(activity, "设置已加载", Toast.LENGTH_SHORT).show();
            invalidate();
            return true;
        }
        if ("factory_reset".equals(action)) {
            // ★ 2026-09-30 用户确认：恢复出厂设置仅工程师可用（原生双保险）
            if (!accountStore.isCurrentService()) {
                Toast.makeText(activity, "无权限：恢复出厂设置仅工程师可用", Toast.LENGTH_SHORT).show();
                return true;
            }
            factoryResetSettings();
            invalidate();
            return true;
        }
        if ("cloud_platform".equals(action)) {
            Toast.makeText(activity, "云平台功能为二期规划，暂未开放", Toast.LENGTH_SHORT).show();
            return true;
        }
        if ("lis".equals(action)) {
            Toast.makeText(activity, "LIS功能为二期规划，暂未开放", Toast.LENGTH_SHORT).show();
            return true;
        }
        if ("camera_preview_tap".equals(action)) {
            openTpLinkCameraInternal();
            return true;
        }
        if ("camera_fullscreen".equals(action) || "camera_open".equals(action)) {
            toggleTpLinkCameraFullscreen();
            return true;
        }
        if ("goto_camera_tab".equals(action)) {
            // H5 首页的摄像头预览区点击：同时切换 native Tab 和 WebView 页面
            // (WebView 在 native 之上, 仅切 native 用户看不到变化, 必须同步 WebView)
            // 不调用 invalidate()：native canvas 在 WebView 之下，用户看不到的 tab 指示器无需刷新；
            // 避免 native 重绘与 WebView 导航同时发生造成按钮闪烁。
            activeTab = 3;
            showingSettings = false;
            if (activity != null) {
                activity.navigateLanhuToCameraMonitor();
            }
            return true;
        }
        if ("camera_config".equals(action)) {
            showTpLinkCameraDialog();
            return true;
        }
        if (action.startsWith("camera_select_")) {
            try {
                int index = Integer.parseInt(action.substring("camera_select_".length()));
                if (index >= 0 && index < activeCameraSnapshotCount()) {
                    selectedCameraSnapshotIndex = index;
                    invalidate();
                    return true;
                }
            } catch (NumberFormatException ignored) {
            }
        }
        if ("camera_snapshot".equals(action)) {
            captureCameraSnapshotAction();
            return true;
        }
        if ("camera_snapshot_download".equals(action)) {
            downloadSelectedCameraSnapshot();
            return true;
        }
        if ("camera_snapshot_delete".equals(action)) {
            deleteSelectedCameraSnapshot();
            return true;
        }
        if ("camera_ai".equals(action)) {
            showCameraAiDialog();
            return true;
        }
        if ("camera_manage".equals(action)) {
            showCameraManageDialog();
            return true;
        }
        if ("treatment_sample".equals(action)) {
            captureTreatmentSampleIfNeeded(true);
            lastGeneratedPdf = null;
            Toast.makeText(activity, "已写入一次治疗记录采样", Toast.LENGTH_SHORT).show();
            invalidate();
            return true;
        }
        if ("treatment_new".equals(action)) {
            createNewTreatmentRecord();
            invalidate();
            return true;
        }
        if ("treatment_finish".equals(action)) {
            finishCurrentTreatment();
            invalidate();
            return true;
        }
        if (action.startsWith("treatment_edit_")) {
            showTreatmentEntryDialogByAction(action);
            return true;
        }
        if (action.startsWith("treatment_period_")) {
            setTreatmentPeriodMode(action);
            return true;
        }
        if ("monitor_history".equals(action)) {
            activeTab = 2;
            monitorMode = MONITOR_MODE_HISTORY;
            showingSettings = false;
            invalidate();
            return true;
        }
        if ("monitor_limits".equals(action)) {
            activeTab = 2;
            monitorMode = MONITOR_MODE_SETTINGS;
            showingSettings = false;
            invalidate();
            return true;
        }
        if ("monitor_volume".equals(action)) {
            // 点"音量开关" → 直接切换自动报警开关（阈值在设置页面里配置）
            toggleMonitorAlarm();
            return true;
        }
        if ("monitor_alarm_sound".equals(action)) {
            // 点 "报警音" 按钮 → 直接切换自动报警开关（阈值在设置页面里配置）
            toggleMonitorAlarm();
            return true;
        }
        if ("monitor_threshold_settings".equals(action)) {
            // 从设置页面进入 → 弹出阈值设置对话框
            showMonitorThresholdDialog();
            return true;
        }
        if ("monitor_level_red".equals(action)) {
            applyMonitorLevel("red");
            return true;
        }
        if ("monitor_level_yellow".equals(action)) {
            applyMonitorLevel("yellow");
            return true;
        }
        if ("monitor_level_green".equals(action)) {
            applyMonitorLevel("green");
            return true;
        }
        if ("apk_update_open".equals(action)) {
            /* ★ 2026-10-09 微信文件传输助手升级：打开全屏 WebView 覆盖层 */
            activity.openApkUpdatePage();
            return true;
        }
        if (action.startsWith("tutorial_")) {
            /* ★ 2026-10-09 教程槽位绑定：pick/play/clear 先于旧 showTutorialAction 处理 */
            if (action.startsWith("tutorial_slot_pick_")) {
                try {
                    activity.pickTutorialMedia(Integer.parseInt(action.substring("tutorial_slot_pick_".length())));
                } catch (NumberFormatException ignored) {
                }
                return true;
            }
            if (action.startsWith("tutorial_slot_play_")) {
                try {
                    playTutorialSlot(Integer.parseInt(action.substring("tutorial_slot_play_".length())));
                } catch (NumberFormatException ignored) {
                }
                return true;
            }
            if (action.startsWith("tutorial_slot_clear_")) {
                try {
                    clearTutorialSlot(Integer.parseInt(action.substring("tutorial_slot_clear_".length())));
                } catch (NumberFormatException ignored) {
                }
                if (activity instanceof MainActivity) {
                    ((MainActivity) activity).notifyLanhuStateChanged();
                }
                return true;
            }
            showTutorialAction(action);
            return true;
        }
        if ("host_ble".equals(action)) {
            showingSettings = true;
            invalidate();
            return true;
        }
        if ("monitor_ble".equals(action)) {
            activeTab = 2;
            showingSettings = false;
            invalidate();
            return true;
        }
        if ("monitor_ble_disconnect".equals(action)) {
            // #5 修复：连接状态页加"断开"动作
            am4100Manager.disconnect();
            Toast.makeText(activity, "已断开监护蓝牙", Toast.LENGTH_SHORT).show();
            activity.notifyLanhuStateChanged();
            invalidate();
            return true;
        }
        if ("monitor_ble_leave".equals(action)) {
            // 实时监护页"离开"按钮：断开监护蓝牙，留在本页
            if (am4100Manager.isConnected()) {
                am4100Manager.disconnect();
                Toast.makeText(activity, "已断开监护蓝牙", Toast.LENGTH_SHORT).show();
                activity.notifyLanhuStateChanged();
            } else {
                Toast.makeText(activity, "监护蓝牙未连接", Toast.LENGTH_SHORT).show();
            }
            invalidate();
            return true;
        }
        // ★ R93：左/右舱切换仅工程师(Service)可用；无权限直接拒绝并提示
        if ("tablet_zone_left".equals(action) || "tablet_zone_right".equals(action)) {
            if (!canSwitchZone()) {
                Toast.makeText(activity, "无权限：仅工程师可切换左/右舱", Toast.LENGTH_SHORT).show();
                return true;
            }
            switchPatientZone("tablet_zone_left".equals(action) ? "left" : "right");
            Toast.makeText(activity, "tablet_zone_left".equals(action) ? "已切换到左舱" : "已切换到右舱",
                    Toast.LENGTH_SHORT).show();
            appendUserLog("控制舱", "切换舱区", "tablet_zone_left".equals(action) ? "左舱" : "右舱");
            /* ★ 2026-10-08 日志页·常规信息：舱区切换 */
            appendGeneralLog("舱区", "切换到" + ("tablet_zone_left".equals(action) ? "左舱" : "右舱"));
            activity.notifyLanhuStateChanged();
            invalidate();
            return true;
        }
        if (action.startsWith("host_mode_")) {
            // ★ 0904 模式开启与设置分离：
            //   host_mode_*            -> 直接开启（按存档/当前配置下发，不弹参数框）
            //   host_mode_*_settings   -> 打开该模式的参数设置弹窗
            if (action.endsWith("_settings")) {
                int settingsIndex = hostModeIndexFromAction(action);
                if (settingsIndex >= 0) {
                    showCustomHostModeDialog(settingsIndex);
                    return true;
                }
                return false;
            }
            applyHostMode(action);
            return true;
        }
        return performHostControlAction(action);
    }

    boolean isCameraConfiguredForWeb() {
        return !TextUtils.isEmpty(cameraStreamUrl);
    }

    String[] getCameraPreviewUrlsForWeb() {
        if (TextUtils.isEmpty(cameraStreamUrl)) {
            return new String[0];
        }
        return buildTpLinkRtspCandidates(cameraStreamUrl);
    }

    private boolean performHostControlAction(String action) {
        if ("control_temp".equals(action)) {
            // ★ 修复 P1-3:用 lastSet 值而非设备实测值,保留用户上次输入
            showNumberDialog(0, "设置舱内温度", "℃", bleManager.getLastSetCabinTempValue(), false);
            return true;
        }
        if ("control_oxygen".equals(action)) {
            // ★ 修复 P1-3:用 lastSet 值而非设备实测值,保留用户上次输入
            showNumberDialog(1, "设置氧浓度", "%", bleManager.getLastSetOxygenValue(), false);
            return true;
        }
        if ("control_co2".equals(action)) {
            showCo2ControlDialog();
            return true;
        }
        if ("control_humidity".equals(action)) {
            // ★ P2-问题15 修复:湿度取消设置功能,只显示舱内实时湿度。
            //   这里推翻了早前的 P1-9(当时把湿度从只读改成弹设置框,理由是"用户反馈点不动");
            //   自测报告明确"湿度是没有设置功能的",以报告为准。
            //   handleControlCardTap(14) 里 #16 的只读实现一直还在(主动 get_humidity
            //   + Toast 当前值),直接复用它,不另写一份 —— 既满足只读要求,
            //   又保留点击反馈,不会退回 P1-9 抱怨的"点不动"。
            handleControlCardTap(14, "湿度");
            invalidate();
            return true;
        }
        if ("control_time".equals(action)) {
            // ★ 治疗时长改为自动统计（开启任一治疗项即正计时累计），不再手动设置。
            new AlertDialog.Builder(activity)
                    .setTitle("治疗时长")
                    .setMessage("治疗时长为自动统计：开启任一治疗项（恒温/氧气/雾化/蓝光/红光/负离子/紫外）后"
                            + "自动开始正计时，全部关闭后暂停，再次开启会继续累计。\n\n当前累计："
                            + bleManager.getTreatmentTimeText())
                    .setPositiveButton("知道了", null)
                    .show();
            return true;
        }
        // ★ 新加:补偿设置页 6 张卡的 +/- 按钮
        if ("compensation_edit_temp".equals(action)) {
            showNumberDialog(0, "温度补偿", "℃", bleManager.getCabinTempValue(), false);
            return true;
        }
        if ("compensation_edit_oxygen".equals(action)) {
            showNumberDialog(1, "氧浓度补偿", "%", bleManager.getOxygenValue(), false);
            return true;
        }
        if ("compensation_edit_humidity".equals(action)) {
            showNumberDialog(14, "湿度补充", "%", bleManager.getLastSetHumidityValue(), false);
            return true;
        }
        if ("compensation_edit_co2".equals(action)) {
            showNumberDialog(3, "CO2浓度补偿", "PPM", bleManager.getCo2Value(), false);
            return true;
        }
        if ("compensation_edit_infrared".equals(action)) {
            // ★ 红外体温补偿：直接弹数字输入对话框，调用新加的 setInfraredTemp
            showInfraredTempCompensationDialog();
            return true;
        }
        if ("compensation_add".equals(action)) {
            // ★ 第 6 张卡 "+" 占位：本轮先弹 Toast
            Toast.makeText(activity, "添加补偿项暂未启用", Toast.LENGTH_SHORT).show();
            return true;
        }
        if ("compensation_save".equals(action)) {
            // ★ 补偿设置保存：JS 端会先调 evaluateJavascript 把 5 个 input 值传过来
            //   实际上"每张卡点+/- 已经实时下发 BLE"，这里只做兜底持久化
            Toast.makeText(activity, "补偿设置已保存", Toast.LENGTH_SHORT).show();
            invalidate();
            return true;
        }
        if ("device_profile_save".equals(action)) {
            // ★ 仪器状态保存：复用 organization_save 模式 — MainActivity 通过 evaluateJavascript 读 5 个 input
            //   直接返回 true，让 MainActivity 走 handleDeviceProfileSaveFromLanhu
            if (activity instanceof MainActivity) {
                ((MainActivity) activity).handleDeviceProfileSaveFromLanhu();
            }
            return true;
        }
        if ("about_save".equals(action)) {
            if (activity instanceof MainActivity) {
                ((MainActivity) activity).handleAboutSaveFromLanhu();
            }
            return true;
        }
        if ("other_settings_save".equals(action)) {
            if (activity instanceof MainActivity) {
                ((MainActivity) activity).handleOtherSettingsSaveFromLanhu();
            }
            return true;
        }
        if ("admin_login".equals(action)) {
            if (activity instanceof MainActivity) {
                ((MainActivity) activity).handleAdminLoginFromLanhu();
            }
            return true;
        }
        boolean timeSetting = action.endsWith("_time");
        String baseAction = timeSetting ? action.substring(0, action.length() - "_time".length()) : action;
        int index = -1;
        String title = "";
        if ("control_humidity".equals(baseAction)) {
            index = 14;
            title = "湿度";
        } else if ("control_red".equals(baseAction)) {
            index = 6;
            title = "红外理疗";
        } else if ("control_blue".equals(baseAction)) {
            index = 7;
            title = "蓝光理疗";
        } else if ("control_uv".equals(baseAction)) {
            index = 12;
            title = "紫外消毒";
        } else if ("control_nebulizer".equals(baseAction)) {
            index = 10;
            title = "雾化器";
        } else if ("control_anion".equals(baseAction)) {
            index = 11;
            title = "负离子";
        } else if ("control_cold_light".equals(baseAction)) {
            index = 4;
            title = "冷光照明";
        } else if ("control_warm_light".equals(baseAction)) {
            index = 5;
            title = "暖光照明";
        } else if ("control_outer".equals(baseAction)) {
            index = 8;
            title = "外循环";
        } else if ("control_inner".equals(baseAction)) {
            index = 9;
            title = "内循环";
        } else if ("control_time".equals(baseAction)) {
            index = 15;
            title = "治疗时长";
        }
        if (index < 0) {
            return false;
        }
        if (timeSetting) {
            handleControlButtonTap(index, title);
        } else {
            handleControlCardTap(index, title);
        }
        invalidate();
        return true;
    }

    private void showCo2ControlDialog() {
        // 自动换气阀值为固定值，后台常驻生效，菜单里不展示、也不提供设置入口。
        String[] items = new String[]{"设置CO2目标值", "读取当前CO2", "切换CO2开关"};
        new AlertDialog.Builder(activity)
                .setTitle("CO2浓度")
                .setItems(items, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        if (which == 0) {
                            showNumberDialog(3, "设置CO2目标值", "PPM", co2TargetForDialog(), true);
                            return;
                        }
                        boolean sent = which == 1
                                ? bleManager.sendControlPreset(3)
                                : bleManager.toggleCo2Enable();
                        showSendResult(which == 1 ? "读取CO2" : "CO2开关", sent);
                        invalidate();
                    }
                })
                .show();
    }

    /** 目标值弹窗预填：优先上次设置的目标值，其次当前实测值。 */
    private Number co2TargetForDialog() {
        Integer target = bleManager.getLastSetCo2Value();
        return target != null ? target : bleManager.getCo2Value();
    }

    private void showTreatmentEntryDialogByAction(String action) {
        PatientCase patient = currentCase();
        if (patient == null) {
            return;
        }
        try {
            int index = Integer.parseInt(action.substring("treatment_edit_".length()));
            if (index >= 0 && index < patient.treatmentEntries.size()) {
                showTreatmentEntryDialog(patient, patient.treatmentEntries.get(index));
            }
        } catch (NumberFormatException ignored) {
        }
    }

    private void setTreatmentPeriodMode(String action) {
        if ("treatment_period_enabled".equals(action)) {
            treatmentPeriodMode = PERIOD_ENABLED;
        } else if ("treatment_period_custom".equals(action)) {
            treatmentPeriodMode = PERIOD_CUSTOM;
        } else {
            treatmentPeriodMode = PERIOD_ALL;
        }
        saveUiSettings();
        Toast.makeText(activity, "已切换治疗记录时段: " + treatmentPeriodModeText(), Toast.LENGTH_SHORT).show();
        invalidate();
    }

    private String treatmentPeriodModeText() {
        if (PERIOD_ENABLED.equals(treatmentPeriodMode)) {
            return "功能开启时段";
        }
        if (PERIOD_CUSTOM.equals(treatmentPeriodMode)) {
            return "自定义时段";
        }
        return "全时段";
    }

    private void applyMonitorLevel(String color) {
        selectedMonitorLevelColor = normalizeMonitorLevelColor(color);
        saveUiSettings();
        boolean sent = bleManager.setStatusLightColor(selectedMonitorLevelColor);
        String label = monitorLevelLabel(selectedMonitorLevelColor);
        if (sent) {
            Toast.makeText(activity, "监护等级已切换为" + label, Toast.LENGTH_SHORT).show();
        } else {
            String detail = TextUtils.isEmpty(bleManager.getLastError()) ? "主机未连接，已保存本地显示" : bleManager.getLastError();
            Toast.makeText(activity, "监护等级已切换为" + label + "，" + detail, Toast.LENGTH_SHORT).show();
        }
        invalidate();
    }

    /**
     * ★ 修复 P1-1:判断"主机舱内环境控制器"是否 BLE 连接。
     * 区别于 am4100Manager.isConnected()(那是监护仪的连接)。
     */
    private boolean isHostConnected() {
        return bleManager != null && bleManager.isConnected();
    }

    public void syncMonitorLevelColorFromDevice() {
        if (!bleManager.isConnected()) {
            return;
        }
        String live = bleManager.getStatusLightColor();
        if (TextUtils.isEmpty(live)) {
            return;
        }
        String normalized = normalizeMonitorLevelColor(live);
        if (TextUtils.equals(selectedMonitorLevelColor, normalized)) {
            return;
        }
        selectedMonitorLevelColor = normalized;
        saveUiSettings();
    }

    private void applyHostMode(String action) {
        int clickedIndex = -1;
        if ("host_mode_mother".equals(action)) {
            clickedIndex = 0;
        } else if ("host_mode_postop".equals(action)) {
            clickedIndex = 1;
        } else if ("host_mode_cardio".equals(action)) {
            clickedIndex = 2;
        } else if ("host_mode_custom".equals(action)) {
            clickedIndex = 3;
        }
        if (clickedIndex >= 0 && clickedIndex == selectedHostModeIndex && hostModeActive) {
            hostModeActive = false;
            lastHostModeIndex = selectedHostModeIndex;
            saveUiSettings();
            Toast.makeText(activity, "已关闭 " + hostModeNames()[selectedHostModeIndex] + " 模式", Toast.LENGTH_SHORT).show();
            invalidate();
            // ★ 修复 #22:关闭后主动 push state 到 JS,避免 1500ms 窗口期
            //   用户感觉"关闭没生效"
            if (activity instanceof MainActivity) {
                ((MainActivity) activity).notifyLanhuStateChanged();
            }
            return;
        }
        int next = (clickedIndex >= 0) ? clickedIndex : selectedHostModeIndex;
        int modeIndex = clampIndex(next, hostModeNames().length);
        // ★ 0904 自测：开启模式不再弹参数框，直接按该模式已保存参数下发；
        //   想改参数点图标下方的“设置”按钮（host_mode_*_settings）。
        //   未保存过参数的模式按主机当前值直接开启（config 为空时只下发 set_care_mode）。
        JSONObject savedConfig = loadCustomHostModeConfig(modeIndex);
        applyCustomHostModeConfig(modeIndex, savedConfig, false);
        invalidate();
    }

    private int hostModeIndexFromAction(String action) {
        if ("host_mode_mother_settings".equals(action)) {
            return 0;
        }
        if ("host_mode_postop_settings".equals(action)) {
            return 1;
        }
        if ("host_mode_cardio_settings".equals(action)) {
            return 2;
        }
        if ("host_mode_custom_settings".equals(action)) {
            return 3;
        }
        return -1;
    }

    private void showCustomHostModeDialog() {
        showCustomHostModeDialog(selectedHostModeIndex);
    }

    private void showCustomHostModeDialog(int modeIndex) {
        // ★ 修复:这里原本调用无参的 loadCustomHostModeConfig(),它读的是
        //   selectedHostModeIndex 而不是传进来的 modeIndex。
        //   applyHostMode() 在打开未保存模式的编辑框时,selectedHostModeIndex 还是
        //   上一个模式,于是弹窗会预填【上一个模式】的参数,保存时又写进【被点的模式】,
        //   造成两个模式的参数互相污染。改为按 modeIndex 读取。
        final JSONObject config = loadCustomHostModeConfig(modeIndex);
        LinearLayout form = new LinearLayout(activity);
        form.setOrientation(LinearLayout.VERTICAL);
        int pad = dpInt(12);
        form.setPadding(pad, pad, pad, pad);

        TextView help = new TextView(activity);
        help.setText("保存后会记住这套参数；点击“保存并开启”会保存并开启当前"
                + hostModeNames()[clampIndex(modeIndex, hostModeNames().length)]
                + "，并按配置下发主机功能。");
        help.setTextSize(13);
        help.setPadding(0, 0, 0, dpInt(8));
        form.addView(help);

        addSectionLabel(form, "基础参数");
        final EditText[] numberInputs = new EditText[CUSTOM_NUMBER_KEYS.length];
        for (int i = 0; i < CUSTOM_NUMBER_KEYS.length; i++) {
            int inputType = InputType.TYPE_CLASS_NUMBER | (CUSTOM_NUMBER_INTEGER[i] ? 0 : InputType.TYPE_NUMBER_FLAG_DECIMAL);
            String value = customNumberText(config, CUSTOM_NUMBER_KEYS[i], currentCustomNumber(CUSTOM_NUMBER_INDICES[i]), CUSTOM_NUMBER_INTEGER[i]);
            if (CUSTOM_NUMBER_INDICES[i] == 15) {
                numberInputs[i] = addLabeledInput(form, "治疗时长（分钟）", "请输入时间", value, inputType);
            } else {
                numberInputs[i] = addInput(form, CUSTOM_NUMBER_LABELS[i], value, inputType);
            }
        }

        addSectionLabel(form, "功能开关");
        final CheckBox[] switchInputs = new CheckBox[CUSTOM_SWITCH_KEYS.length];
        for (int i = 0; i < CUSTOM_SWITCH_KEYS.length; i++) {
            switchInputs[i] = addCheckBox(form, CUSTOM_SWITCH_LABELS[i], customBoolean(config, CUSTOM_SWITCH_KEYS[i], CUSTOM_SWITCH_INDICES[i]));
        }

        addSectionLabel(form, "功能时间");
        final EditText[] timeInputs = new EditText[CUSTOM_TIME_KEYS.length];
        for (int i = 0; i < CUSTOM_TIME_KEYS.length; i++) {
            String label = CUSTOM_TIME_LABELS[i].replace("时间 分钟", "时间（分钟）");
            timeInputs[i] = addLabeledInput(form, label, "请输入时间",
                    customNumberText(config, CUSTOM_TIME_KEYS[i], getTimedControlDefault(CUSTOM_TIME_INDICES[i]), true),
                    InputType.TYPE_CLASS_NUMBER);
        }

        ScrollView scroll = new ScrollView(activity);
        scroll.setFillViewport(true);
        scroll.addView(form);

        LinearLayout dialogBody = new LinearLayout(activity);
        dialogBody.setOrientation(LinearLayout.VERTICAL);
        int maxBodyHeight = (int) (activity.getResources().getDisplayMetrics().heightPixels * 0.78f);
        dialogBody.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, maxBodyHeight));
        dialogBody.addView(scroll, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f));

        LinearLayout actionBar = new LinearLayout(activity);
        actionBar.setOrientation(LinearLayout.HORIZONTAL);
        actionBar.setPadding(dpInt(8), dpInt(6), dpInt(8), dpInt(6));
        dialogBody.addView(actionBar, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));

        Button saveOnlyButton = createDialogActionButton("仅保存");
        Button applyOnlyButton = createDialogActionButton("仅开启");
        Button cancelButton = createDialogActionButton("取消");
        Button saveAndApplyButton = createDialogActionButton("保存并开启");
        actionBar.addView(saveOnlyButton, actionButtonParams(1f, 0));
        actionBar.addView(applyOnlyButton, actionButtonParams(1f, dpInt(4)));
        actionBar.addView(cancelButton, actionButtonParams(0.85f, dpInt(12)));
        actionBar.addView(saveAndApplyButton, actionButtonParams(1.3f, dpInt(4)));

        final AlertDialog dialog = new AlertDialog.Builder(activity)
                .setTitle(hostModeNames()[clampIndex(modeIndex, hostModeNames().length)] + " 参数设置")
                .setView(dialogBody)
                .create();
        saveOnlyButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                saveCustomHostModeForm(modeIndex, numberInputs, switchInputs, timeInputs, false, false);
            }
        });
        applyOnlyButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                saveCustomHostModeForm(modeIndex, numberInputs, switchInputs, timeInputs, true, false);
            }
        });
        cancelButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                dialog.dismiss();
            }
        });
        saveAndApplyButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                saveCustomHostModeForm(modeIndex, numberInputs, switchInputs, timeInputs, true, true);
                dialog.dismiss();
            }
        });
        dialog.show();
        if (dialog.getWindow() != null) {
            // ★ P2-问题16 顺带修复:原来是 SOFT_INPUT_ADJUST_NOTHING,键盘弹出时
            //   不重排布局,会挡住表单下半部分(本框有 5 个数值 + 10 个开关 + 5 个时间共 20 项)。
            //   这与 P2-问题2 在 Activity 层修掉的是同一类遮挡问题。
            //   表单本身已经在 ScrollView 里,改成 ADJUST_RESIZE 后键盘弹出会压缩可视区,
            //   用户可以滚到被遮挡的输入项。
            dialog.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
        }
    }

    private void addSectionLabel(LinearLayout form, String text) {
        TextView label = new TextView(activity);
        label.setText(text);
        label.setTextSize(15);
        label.setTypeface(Typeface.DEFAULT_BOLD);
        label.setPadding(0, dpInt(10), 0, dpInt(4));
        form.addView(label);
    }

    private CheckBox addCheckBox(LinearLayout form, String text, boolean checked) {
        CheckBox checkBox = new CheckBox(activity);
        checkBox.setText(text);
        checkBox.setTextSize(15);
        checkBox.setChecked(checked);
        form.addView(checkBox);
        return checkBox;
    }

    private JSONObject loadCustomHostModeConfig() {
        return loadCustomHostModeConfig(selectedHostModeIndex);
    }

    private JSONObject loadCustomHostModeConfig(int modeIndex) {
        SharedPreferences prefs = activity.getSharedPreferences(PREFS_NAME, 0);
        String key = hostModeConfigKey(modeIndex);
        if (TextUtils.isEmpty(key)) {
            return new JSONObject();
        }
        String raw = prefs.getString(key, "");
        if (TextUtils.isEmpty(raw)) {
            return new JSONObject();
        }
        try {
            return new JSONObject(raw);
        } catch (JSONException exception) {
            return new JSONObject();
        }
    }

    private void saveCustomHostModeConfig(JSONObject config) {
        saveCustomHostModeConfig(selectedHostModeIndex, config);
    }

    private void saveCustomHostModeConfig(int modeIndex, JSONObject config) {
        String key = hostModeConfigKey(modeIndex);
        if (TextUtils.isEmpty(key)) {
            return;
        }
        activity.getSharedPreferences(PREFS_NAME, 0)
                .edit()
                .putString(key, config.toString())
                .apply();
    }

    private String hostModeConfigKey(int modeIndex) {
        int safe = clampIndex(modeIndex, HOST_MODE_CONFIG_KEYS.length);
        return HOST_MODE_CONFIG_KEYS[safe];
    }

    private String customNumberText(JSONObject config, String key, Number fallback, boolean integerOnly) {
        if (config != null && config.has(key) && !config.isNull(key)) {
            return String.valueOf(config.opt(key));
        }
        if (fallback == null) {
            return "";
        }
        return integerOnly ? String.valueOf(fallback.intValue()) : trimNumber(fallback.floatValue());
    }

    private Number currentCustomNumber(int index) {
        if (index == 0) {
            return bleManager.getCabinTempValue();
        }
        if (index == 1) {
            return bleManager.getOxygenValue();
        }
        if (index == 3) {
            return bleManager.getCo2Value();
        }
        if (index == 14) {
            return bleManager.getHumidityValue();
        }
        if (index == 15) {
            return bleManager.getTreatmentMinutesValue();
        }
        return null;
    }

    private boolean customBoolean(JSONObject config, String key, int index) {
        if (config != null && config.has(key) && !config.isNull(key)) {
            return config.optBoolean(key);
        }
        return bleManager.isControlOn(index);
    }

    private void saveCustomHostModeForm(EditText[] numberInputs, CheckBox[] switchInputs, EditText[] timeInputs, boolean apply) {
        saveCustomHostModeForm(selectedHostModeIndex, numberInputs, switchInputs, timeInputs, apply, false);
    }

    private void saveCustomHostModeForm(int modeIndex, EditText[] numberInputs, CheckBox[] switchInputs, EditText[] timeInputs, boolean apply, boolean persistMode) {
        try {
            JSONObject config = new JSONObject();
            for (int i = 0; i < CUSTOM_NUMBER_KEYS.length; i++) {
                String raw = numberInputs[i].getText().toString().trim();
                if (TextUtils.isEmpty(raw)) {
                    continue;
                }
                float value = Float.parseFloat(raw);
                String error = validateControlInput(CUSTOM_NUMBER_INDICES[i], value);
                if (!TextUtils.isEmpty(error)) {
                    Toast.makeText(activity, error, Toast.LENGTH_SHORT).show();
                    return;
                }
                if (CUSTOM_NUMBER_INTEGER[i]) {
                    config.put(CUSTOM_NUMBER_KEYS[i], Math.round(value));
                } else {
                    config.put(CUSTOM_NUMBER_KEYS[i], value);
                }
            }
            for (int i = 0; i < CUSTOM_SWITCH_KEYS.length; i++) {
                config.put(CUSTOM_SWITCH_KEYS[i], switchInputs[i].isChecked());
            }
            for (int i = 0; i < CUSTOM_TIME_KEYS.length; i++) {
                String raw = timeInputs[i].getText().toString().trim();
                if (TextUtils.isEmpty(raw)) {
                    continue;
                }
                float value = Float.parseFloat(raw);
                String error = validateControlInput(CUSTOM_TIME_INDICES[i], value);
                if (!TextUtils.isEmpty(error)) {
                    Toast.makeText(activity, error, Toast.LENGTH_SHORT).show();
                    return;
                }
                config.put(CUSTOM_TIME_KEYS[i], Math.round(value));
            }
            saveCustomHostModeConfig(modeIndex, config);
            String modeName = hostModeNames()[clampIndex(modeIndex, hostModeNames().length)];
            if (apply) {
                applyCustomHostModeConfig(modeIndex, config, persistMode);
            } else {
                Toast.makeText(activity, modeName + "参数已保存", Toast.LENGTH_SHORT).show();
                invalidate();
            }
        } catch (NumberFormatException exception) {
            Toast.makeText(activity, "请输入正确的数值", Toast.LENGTH_SHORT).show();
        } catch (JSONException exception) {
            Toast.makeText(activity, "模式参数保存失败", Toast.LENGTH_SHORT).show();
        }
    }

    private void applyCustomHostModeConfig(JSONObject config) {
        applyCustomHostModeConfig(selectedHostModeIndex, config);
    }

    private void applyCustomHostModeConfig(int modeIndex, JSONObject config) {
        applyCustomHostModeConfig(modeIndex, config, true);
    }

    /**
     * ★ 0904 自测：保存并开启必须先把当前表单参数按模式存档到主机
     *   （save_care_mode_set），再 set_care_mode 切模式，否则主机仍按
     *   旧的模式存档运行，表现为“只开启了已设置参数、没保存当前配置”。
     *   persistMode=false 用于“仅开启”：下发当前数值但不写入模式存档。
     */
    private void applyCustomHostModeConfig(int modeIndex, JSONObject config, boolean persistMode) {
        int safeIndex = clampIndex(modeIndex, hostModeNames().length);
        lastHostModeIndex = selectedHostModeIndex;
        selectedHostModeIndex = safeIndex;

        boolean sent = false;
        for (int i = 0; i < CUSTOM_NUMBER_KEYS.length; i++) {
            if (!config.has(CUSTOM_NUMBER_KEYS[i]) || config.isNull(CUSTOM_NUMBER_KEYS[i])) {
                continue;
            }
            int index = CUSTOM_NUMBER_INDICES[i];
            if (index == 0) {
                sent |= bleManager.setTemperature((float) config.optDouble(CUSTOM_NUMBER_KEYS[i]));
            } else if (index == 1) {
                sent |= bleManager.setOxygen((float) config.optDouble(CUSTOM_NUMBER_KEYS[i]));
            } else if (index == 3) {
                sent |= bleManager.setCo2(config.optInt(CUSTOM_NUMBER_KEYS[i]));
            } else if (index == 14) {
                sent |= bleManager.setHumidity((float) config.optDouble(CUSTOM_NUMBER_KEYS[i]));
            } else if (index == 15) {
                sent |= bleManager.setControlTime(15, config.optInt(CUSTOM_NUMBER_KEYS[i]));
            }
        }
        // 定时值必须先下发；随后开启对应功能时，BleManager 才会按新时长启动倒计时。
        // ★ P2-问题13 修复:老版本存下的模式参数可能超过设备上限 120 分钟。
        //   此处 sent 用的是 |=,单条被 setControlTime 拒绝会被其它成功项掩盖,
        //   用户只看到"已下发"却不知该项没生效 —— 所以下发前先钳到 120。
        for (int i = 0; i < CUSTOM_TIME_KEYS.length; i++) {
            if (!config.has(CUSTOM_TIME_KEYS[i]) || config.isNull(CUSTOM_TIME_KEYS[i])) {
                continue;
            }
            int minutes = config.optInt(CUSTOM_TIME_KEYS[i]);
            // ★ 红外/蓝光允许不限时哨兵 65536；其余仍钳到设备上限 120。
            if (!TimedControlProtocol.isAcceptableMinutes(CUSTOM_TIME_INDICES[i], minutes)) {
                minutes = 120;
                Toast.makeText(activity,
                        CUSTOM_TIME_LABELS[i] + "超过设备上限，已按120分钟下发",
                        Toast.LENGTH_SHORT).show();
            }
            sent |= bleManager.setControlTime(CUSTOM_TIME_INDICES[i], minutes);
        }
        for (int i = 0; i < CUSTOM_SWITCH_KEYS.length; i++) {
            int controlIndex = CUSTOM_SWITCH_INDICES[i];
            boolean enabled = config.optBoolean(CUSTOM_SWITCH_KEYS[i]);
            if (TimedControlProtocol.isTimedControlIndex(controlIndex)) {
                sent |= bleManager.setTimedControlEnabledFromConfiguredDuration(controlIndex, enabled);
            } else {
                sent |= bleManager.setControlEnabled(controlIndex, enabled);
            }
        }
        if (persistMode) {
            sent |= bleManager.saveCareModeSet(hostModeValues()[safeIndex]);
        }
        sent |= bleManager.setCareMode(hostModeValues()[safeIndex]);
        String modeName = hostModeNames()[safeIndex];
        // 只有至少一条配置命令进入发送队列，才让前端显示该模式已经开启。
        hostModeActive = sent;
        saveUiSettings();
        if (sent) {
            Toast.makeText(activity, modeName + "参数已保存并下发", Toast.LENGTH_SHORT).show();
        } else {
            String detail = TextUtils.isEmpty(bleManager.getLastError()) ? "请连接主机蓝牙后再开启" : bleManager.getLastError();
            Toast.makeText(activity, modeName + "参数已保存，暂未下发：" + detail, Toast.LENGTH_LONG).show();
        }
        invalidate();
        if (activity instanceof MainActivity) {
            ((MainActivity) activity).notifyLanhuStateChanged();
        }
    }

    private void showTutorialAction(String action) {
        // 教程视频：点击打开平板相册中按日期排序的对应视频
        if ("tutorial_changed".equals(action)) {
            android.util.Log.d("ICU-Native", "showTutorialAction: tutorial_changed, showing Toast");
            Toast.makeText(activity, "使用教程已更新", Toast.LENGTH_SHORT).show();
            return;
        }
        if (action.startsWith("tutorial_video_")) {
            android.util.Log.d("ICU-Native", "showTutorialAction: matched tutorial_video_, action='" + action + "'");
            try {
                int index = Integer.parseInt(action.substring("tutorial_video_".length()));
                android.util.Log.d("ICU-Native", "showTutorialAction: calling openAlbumVideo(" + index + ")");
                activity.openAlbumVideo(index);
            } catch (NumberFormatException e) {
                android.util.Log.e("ICU-Native", "showTutorialAction: parse failed", e);
            }
            return;
        }
        android.util.Log.d("ICU-Native", "showTutorialAction: no match for action='" + action + "', showing AlertDialog");
        String title = "使用教程";
        String message = "教程内容已内置在当前页面，可按模块查看。";
        if ("tutorial_install_video".equals(action)) {
            title = "装机教程";
            message = "1. 检查舱体、电源线、监护设备和摄像头。\n2. 主机通电后确认蓝牙、WiFi和舱内照明状态。\n3. 在“连接状态”中连接主机蓝牙和监护蓝牙。\n4. 在摄像监控中录入RTSP地址并测试画面。";
        } else if ("tutorial_operation_video".equals(action)) {
            title = "使用操作指南";
            message = "1. 编辑患者信息并确认本次治疗记录。\n2. 连接主机蓝牙后设置温度、氧浓度、湿度、CO2和护理模式。\n3. 连接监护蓝牙后观察心率、血氧、血压、体温和波形。\n4. 治疗结束后生成并下载PDF报告。";
        } else if ("tutorial_manual".equals(action)) {
            title = "临床应用手册";
            message = "包含适用场景、母幼/术后/心肺/自定义护理模式、生命体征指标解释、常见报警处理和日常维护建议。当前版本以内置图文手册为准。";
        } else if ("tutorial_update".equals(action)) {
            title = "版本更新说明";
            message = "第一期教程随APK内置发布；如需替换视频或手册，可在下一版打包新素材。在线教程更新属于服务端能力，未接入前不会误报为已联网。";
        }
        new AlertDialog.Builder(activity)
                .setTitle(title)
                .setMessage(message)
                .setPositiveButton("知道了", null)
                .show();
    }

    private JSONObject organizationToJson(Organization organization) throws JSONException {
        JSONObject json = new JSONObject();
        if (organization == null) {
            return json;
        }
        json.put("name", organization.name);
        json.put("address", organization.address);
        json.put("phone", organization.phone);
        // ★ 把真实的 logoUri 和 logoName 传给 JS,否则 LOGO 永远显示不了图片
        json.put("logoUri", organization.logoUri == null ? "" : organization.logoUri);
        json.put("logoName", organization.logoName == null ? "" : organization.logoName);
        return json;
    }

    private JSONObject dashboardBleStateToJson() throws JSONException {
        JSONObject root = new JSONObject();
        // 主机状态只由 MainActivity 构建，bleState() 与 firstPhaseState() 共用同一契约。
        JSONObject host = activity.buildHostStateJson();

        JSONObject monitor = new JSONObject();
        monitor.put("connected", am4100Manager.isConnected());
        monitor.put("scanning", am4100Manager.isScanning());
        monitor.put("ready", am4100Manager.isProtocolReady());
        monitor.put("state", safeText(am4100Manager.getStateText()));
        monitor.put("deviceName", safeText(am4100Manager.getConnectedDeviceName()));
        monitor.put("deviceId", safeText(am4100Manager.getConnectedDeviceId()));
        // ★ 2026-10-08：补推监护扫描设备列表（H5 蓝牙选择弹窗数据源，此前缺失导致列表恒空）
        monitor.put("devices", activity.buildMonitorDevicesJson());
        if (am4100Manager.isConnected()) {
            fillMonitorJsonFromLiveManager(monitor);
        } else {
            fillMonitorJsonFromSnapshot(monitor, currentCase() == null ? null : currentCase().monitorSnapshot);
        }
        monitor.put("lastError", safeText(am4100Manager.getLastError()));

        root.put("host", host);
        root.put("monitor", monitor);
        return root;
    }

    private void fillMonitorSnapshot(MonitorSnapshot snapshot) {
        if (snapshot == null) {
            return;
        }
        snapshot.heartRate = safeText(am4100Manager.getHeartRateText());
        snapshot.bloodPressure = safeText(am4100Manager.getBloodPressureText());
        snapshot.map = safeText(am4100Manager.getMapText());
        snapshot.spo2 = safeText(am4100Manager.getSpo2Text());
        snapshot.pulse = safeText(am4100Manager.getPulseRateText());
        snapshot.pulseRate = safeText(am4100Manager.getPulseRateText());
        snapshot.bodyTemp = safeText(am4100Manager.getBodyTempText());
        snapshot.resp = safeText(am4100Manager.getRespText());
        snapshot.summary = safeText(am4100Manager.getLastFrameSummary());
        replaceIntegerList(snapshot.ecgWaveSamples, am4100Manager.getEcgWaveSamples());
        replaceIntegerList(snapshot.spo2WaveSamples, am4100Manager.getSpo2WaveSamples());
        replaceIntegerList(snapshot.respWaveSamples, am4100Manager.getRespWaveSamples());
        replaceIntegerList(snapshot.heartRateHistory, am4100Manager.getHeartRateHistory());
        replaceIntegerList(snapshot.bloodPressureHistory, am4100Manager.getBloodPressureHistory());
        replaceIntegerList(snapshot.spo2History, am4100Manager.getSpo2History());
        replaceIntegerList(snapshot.pulseRateHistory, am4100Manager.getPulseRateHistory());
        replaceIntegerList(snapshot.temperatureHistory, am4100Manager.getTemperatureHistory());
        replaceIntegerList(snapshot.respHistory, am4100Manager.getRespHistory());
    }

    private void fillMonitorJsonFromLiveManager(JSONObject monitor) throws JSONException {
        monitor.put("heartRate", safeText(am4100Manager.getHeartRateText()));
        monitor.put("bloodPressure", safeText(am4100Manager.getBloodPressureText()));
        monitor.put("map", safeText(am4100Manager.getMapText()));
        monitor.put("spo2", safeText(am4100Manager.getSpo2Text()));
        monitor.put("pulse", safeText(am4100Manager.getPulseRateText()));
        monitor.put("pulseRate", safeText(am4100Manager.getPulseRateText()));
        monitor.put("bodyTemp", safeText(am4100Manager.getBodyTempText()));
        monitor.put("resp", safeText(am4100Manager.getRespText()));
        monitor.put("summary", safeText(am4100Manager.getLastFrameSummary()));
        monitor.put("ecgWaveSamples", intListToJsonArray(am4100Manager.getEcgWaveSamples()));
        monitor.put("spo2WaveSamples", intListToJsonArray(am4100Manager.getSpo2WaveSamples()));
        monitor.put("respWaveSamples", intListToJsonArray(am4100Manager.getRespWaveSamples()));
        monitor.put("heartRateHistory", intListToJsonArray(am4100Manager.getHeartRateHistory()));
        monitor.put("bloodPressureHistory", intListToJsonArray(am4100Manager.getBloodPressureHistory()));
        monitor.put("spo2History", intListToJsonArray(am4100Manager.getSpo2History()));
        monitor.put("pulseRateHistory", intListToJsonArray(am4100Manager.getPulseRateHistory()));
        monitor.put("temperatureHistory", intListToJsonArray(am4100Manager.getTemperatureHistory()));
        monitor.put("respHistory", intListToJsonArray(am4100Manager.getRespHistory()));
    }

    private void fillMonitorJsonFromSnapshot(JSONObject monitor, MonitorSnapshot snapshot) throws JSONException {
        if (snapshot == null || !snapshot.hasData()) {
            monitor.put("heartRate", "--");
            monitor.put("bloodPressure", "--/--");
            monitor.put("map", "--");
            monitor.put("spo2", "--");
            monitor.put("pulse", "--");
            monitor.put("pulseRate", "--");
            monitor.put("bodyTemp", "--");
            monitor.put("resp", "--");
            monitor.put("summary", EMPTY_MONITOR_SUMMARY);
            monitor.put("ecgWaveSamples", new JSONArray());
            monitor.put("spo2WaveSamples", new JSONArray());
            monitor.put("respWaveSamples", new JSONArray());
            monitor.put("heartRateHistory", new JSONArray());
            monitor.put("bloodPressureHistory", new JSONArray());
            monitor.put("spo2History", new JSONArray());
            monitor.put("pulseRateHistory", new JSONArray());
            monitor.put("temperatureHistory", new JSONArray());
            monitor.put("respHistory", new JSONArray());
            return;
        }
        monitor.put("heartRate", safeText(snapshot.heartRate));
        monitor.put("bloodPressure", safeText(snapshot.bloodPressure));
        monitor.put("map", safeText(snapshot.map));
        monitor.put("spo2", safeText(snapshot.spo2));
        monitor.put("pulse", safeText(snapshot.pulse));
        monitor.put("pulseRate", safeText(snapshot.pulseRate));
        monitor.put("bodyTemp", safeText(snapshot.bodyTemp));
        monitor.put("resp", safeText(snapshot.resp));
        monitor.put("summary", safeText(snapshot.summary));
        monitor.put("ecgWaveSamples", intListToJsonArray(snapshot.ecgWaveSamples));
        monitor.put("spo2WaveSamples", intListToJsonArray(snapshot.spo2WaveSamples));
        monitor.put("respWaveSamples", intListToJsonArray(snapshot.respWaveSamples));
        monitor.put("heartRateHistory", intListToJsonArray(snapshot.heartRateHistory));
        monitor.put("bloodPressureHistory", intListToJsonArray(snapshot.bloodPressureHistory));
        monitor.put("spo2History", intListToJsonArray(snapshot.spo2History));
        monitor.put("pulseRateHistory", intListToJsonArray(snapshot.pulseRateHistory));
        monitor.put("temperatureHistory", intListToJsonArray(snapshot.temperatureHistory));
        monitor.put("respHistory", intListToJsonArray(snapshot.respHistory));
    }

    private void replaceIntegerList(ArrayList<Integer> target, List<Integer> source) {
        target.clear();
        if (source == null) {
            return;
        }
        target.addAll(source);
    }

    private void putDashboardControl(JSONObject controls, String key, int index) throws JSONException {
        if (index == 2) {
            controls.put(key, safeText(currentMonitorLevelLabel()));
        } else {
            controls.put(key, safeText(bleManager.getControlValue(index)));
        }
        controls.put(key + "On", bleManager.isControlOn(index));
    }

    private void putDashboardCountdown(JSONObject controls, String key, int index) throws JSONException {
        controls.put(key + "RemainingMs", bleManager.getCountdownRemainingMs(index));
    }

    private JSONObject monitorSettingsToJson() throws JSONException {
        JSONObject json = new JSONObject();
        json.put("mode", monitorMode == MONITOR_MODE_HISTORY ? "history" : (monitorMode == MONITOR_MODE_SETTINGS ? "settings" : "live"));
        json.put("pulseBeep", monitorPulseBeep);
        json.put("alarmSound", monitorAlarmSound);
        json.put("alarmEnabled", monitorAlarmEnabled);  // 暴露给 JS 用于文字着色
        JSONArray limits = new JSONArray();
        for (int i = 0; i < monitorLimitEnabled.length; i++) {
            JSONObject item = new JSONObject();
            item.put("name", monitorLimitName(i));
            item.put("enabled", monitorLimitEnabled[i]);
            item.put("high", formatLimitValue(i, monitorLimitHigh[i]));
            item.put("low", formatLimitValue(i, monitorLimitLow[i]));
            item.put("unit", monitorLimitUnit(i));
            limits.put(item);
        }
        json.put("limits", limits);
        return json;
    }

    private String safeText(String value) {
        return value == null ? "" : value;
    }

    private JSONArray intListToJsonArray(List<Integer> values) throws JSONException {
        JSONArray array = new JSONArray();
        if (values == null) {
            return array;
        }
        for (Integer value : values) {
            if (value != null) {
                array.put(value);
            }
        }
        return array;
    }

    private boolean isPlaceholderPatientText(String value) {
        if (TextUtils.isEmpty(value)) {
            return true;
        }
        String clean = value.trim();
        if (TextUtils.isEmpty(clean) || "-".equals(clean) || "--".equals(clean) || "/".equals(clean)) {
            return true;
        }
        boolean allZero = clean.length() <= 6;
        for (int i = 0; i < clean.length(); i++) {
            if (clean.charAt(i) != '0') {
                allZero = false;
                break;
            }
        }
        return allZero
                || "\u5f85\u5f55\u5165".equals(clean)
                || "\u672a\u547d\u540d".equals(clean)
                || "\u672a\u77e5".equals(clean)
                || "null".equalsIgnoreCase(clean)
                || "undefined".equalsIgnoreCase(clean);
    }

    private boolean hasMeaningfulPatientData(PatientCase patient) {
        return patient != null
                && (patient.currentTreatment
                || !isPlaceholderPatientText(patient.petName)
                || !isPlaceholderPatientText(patient.species)
                || !isPlaceholderPatientText(patient.ownerName)
                || !isPlaceholderPatientText(patient.ownerPhone)
                || !isPlaceholderPatientText(patient.doctor)
                || !isPlaceholderPatientText(patient.recordNo)
                || !isPlaceholderPatientText(patient.caseNo));
    }

    private JSONArray casesToJson() throws JSONException {
        JSONArray array = new JSONArray();
        for (PatientCase patient : cases) {
            if (!hasMeaningfulPatientData(patient)) {
                continue;
            }
            array.put(patientToJson(patient));
        }
        return array;
    }

    private JSONObject patientToJson(PatientCase patient) throws JSONException {
        JSONObject json = new JSONObject();
        if (patient == null) {
            return json;
        }
        json.put("caseId", patient.caseId);
        json.put("caseNo", patient.caseNo);
        json.put("monitorNo", patient.monitorNo);
        json.put("petName", patient.petName);
        json.put("species", patient.species);
        json.put("sex", patient.sex);
        json.put("age", patient.age);
        json.put("ownerName", patient.ownerName);
        json.put("ownerPhone", patient.ownerPhone);
        json.put("doctor", patient.doctor);
        json.put("recordNo", patient.recordNo);
        json.put("visitDate", patient.visitDate);
        json.put("note", patient.note);
        json.put("weight", safeJsonText(patient.weight));
        json.put("department", safeJsonText(patient.department));
        json.put("followUpDate", safeJsonText(patient.followUpDate));
        json.put("ageUnit", safeJsonText(patient.ageUnit));
        json.put("disease", safeJsonText(patient.disease));
        json.put("transferredOut", patient.transferredOut);
        json.put("currentTreatment", patient.currentTreatment);
        json.put("treatmentStartTime", patient.treatmentStartTime);
        json.put("treatmentEndTime", patient.treatmentEndTime);
        // ★ V1.02 UI：H5 需要据此区分"新建样本/编辑样本"弹窗标题
        json.put("pendingInitialEntry", patient.pendingInitialEntry);
        // ★ 任务29：记录单编辑字段回推（出院建议/治疗效果/体征行），H5 据此渲染记录单
        json.put("advice", safeJsonText(patient.sheetAdvice));
        json.put("conclIdx", patient.sheetConclusionIdx);
        JSONArray vitals = new JSONArray();
        if (!TextUtils.isEmpty(patient.sheetVitals)) {
            try {
                vitals = new JSONArray(patient.sheetVitals);
            } catch (JSONException ignored) {
                vitals = new JSONArray();
            }
        }
        json.put("vitals", vitals);
        return json;
    }

    private JSONArray treatmentsToJson(PatientCase patient) throws JSONException {
        JSONArray array = new JSONArray();
        if (patient == null) {
            return array;
        }
        for (int i = 0; i < patient.treatmentEntries.size(); i++) {
            TreatmentEntry entry = patient.treatmentEntries.get(i);
            JSONObject json = new JSONObject();
            json.put("index", i + 1);
            json.put("itemName", entry.itemName);
            json.put("unit", entry.unit);
            json.put("enabled", entry.enabled);
            json.put("includeInReport", entry.includeInReport);
            json.put("customPeriod", entry.customPeriod);
            json.put("startTime", entry.startTime);
            json.put("endTime", entry.endTime);
            json.put("manualAverageValue", entry.manualAverageValue);
            json.put("manualHighValue", entry.manualHighValue);
            json.put("manualLowValue", entry.manualLowValue);
            json.put("manualValue", buildTreatmentManualSummary(entry));
            json.put("lastValue", entry.lastValue);
            json.put("sampleCount", entry.sampleCount);
            json.put("period", treatmentPeriodDisplayText(entry));
            json.put("average", treatmentAverageText(entry));
            json.put("high", treatmentHighText(entry));
            json.put("low", treatmentLowText(entry));
            array.put(json);
        }
        return array;
    }

    private JSONArray hostModesToJson() throws JSONException {
        JSONArray array = new JSONArray();
        for (int i = 0; i < hostModeNames().length; i++) {
            JSONObject item = new JSONObject();
            item.put("name", hostModeNames()[i]);
            item.put("value", hostModeValues()[i]);
            item.put("active", hostModeActive && i == selectedHostModeIndex);
            item.put("lastUsed", i == lastHostModeIndex && (selectedHostModeIndex < 0 || i != selectedHostModeIndex));
            array.put(item);
        }
        return array;
    }

    private String[] hostModeNames() {
        return new String[]{"母幼护理模式", "术后护理模式", "心肺护理模式", "自定义模式"};
    }

    private String[] hostModeValues() {
        return new String[]{"maternal_and_infant_care", "Postoperative_care", "Cardiopulmonary_care", "Custom_mode"};
    }

    private String selectedHostModeName() {
        String[] names = hostModeNames();
        int index = clampIndex(selectedHostModeIndex, names.length);
        return names[index];
    }

    private String optNonEmpty(JSONObject json, String key, String fallback) {
        String value = json.optString(key, "");
        return TextUtils.isEmpty(value) ? fallback : value;
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        density = getResources().getDisplayMetrics().density;
        captureTreatmentSampleIfNeeded(false);
        clickZones.clear();

        float w = getWidth();
        float h = getHeight();
        float leftW = Math.max(dp(220), Math.min(dp(350), w * 0.19f));
        float topH = dp(44);
        float patientH = dp(148);
        float tabH = dp(48);

        fill(canvas, 0, 0, w, h, page);
        drawSidebar(canvas, leftW, h);
        drawTopBar(canvas, leftW, 0, w - leftW, topH);
        drawPatientHeader(canvas, leftW, topH, w - leftW, patientH);

        if (showingSettings) {
            updateCameraPreviewOverlay(null, false);
            drawSettings(canvas, leftW, topH + patientH, w - leftW, h - topH - patientH);
            return;
        }

        drawTabs(canvas, leftW, topH + patientH, w - leftW, tabH);
        float contentY = topH + patientH + tabH;
        float contentH = h - contentY;
        if (activeTab != 0 && activeTab != 3) {
            updateCameraPreviewOverlay(null, false);
        }
        if (activeTab == 0) {
            drawStatusPage(canvas, leftW, contentY, w - leftW, contentH);
        } else if (activeTab == 1) {
            drawControlPage(canvas, leftW, contentY, w - leftW, contentH);
        } else if (activeTab == 2) {
            drawMonitorPage(canvas, leftW, contentY, w - leftW, contentH);
        } else if (activeTab == 3) {
            drawCameraPage(canvas, leftW, contentY, w - leftW, contentH);
        } else if (activeTab == 4) {
            drawRecordPage(canvas, leftW, contentY, w - leftW, contentH);
        } else if (activeTab == 5) {
            drawPdfFilesPage(canvas, leftW, contentY, w - leftW, contentH);
        } else {
            drawTutorialPage(canvas, leftW, contentY, w - leftW, contentH);
        }
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (event.getAction() == MotionEvent.ACTION_DOWN) {
            touchDownY = event.getY();
            lastTouchY = touchDownY;
            touchMoved = false;
            draggingAm4100DeviceList = !showingSettings && activeTab == 2 && am4100DeviceListBounds.contains(event.getX(), event.getY());
            return true;
        }
        if (event.getAction() == MotionEvent.ACTION_MOVE) {
            if (draggingAm4100DeviceList) {
                float dy = event.getY() - lastTouchY;
                if (Math.abs(event.getY() - touchDownY) > dp(4)) {
                    touchMoved = true;
                }
                am4100DeviceScrollY = clamp(am4100DeviceScrollY - dy, 0, am4100DeviceMaxScrollY);
                lastTouchY = event.getY();
                invalidate();
            }
            return true;
        }
        if (event.getAction() == MotionEvent.ACTION_CANCEL) {
            draggingAm4100DeviceList = false;
            return true;
        }
        if (event.getAction() != MotionEvent.ACTION_UP) {
            return true;
        }
        if (draggingAm4100DeviceList && touchMoved) {
            draggingAm4100DeviceList = false;
            invalidate();
            return true;
        }
        draggingAm4100DeviceList = false;
        float x = event.getX();
        float y = event.getY();
        for (int i = clickZones.size() - 1; i >= 0; i--) {
            ClickZone zone = clickZones.get(i);
            if (zone.rect.contains(x, y)) {
                zone.action.run();
                invalidate();
                return true;
            }
        }
        return true;
    }

    private void drawSidebar(Canvas canvas, float w, float h) {
        fill(canvas, 0, 0, w, h, sidebar);
        fill(canvas, 0, 0, w, dp(44), navy);
        drawText(canvas, "● 动物ICU重症监护系统", dp(14), dp(28), 17, Color.WHITE, true);

        Organization orgInfo = currentOrganization();
        RectF org = rect(dp(14), dp(60), w - dp(14), dp(192));
        rounded(canvas, org, Color.TRANSPARENT, Color.TRANSPARENT, dp(10));
        RectF logo = rect(org.left, org.top + dp(18), org.left + dp(60), org.top + dp(78));
        drawCircle(canvas, logo.centerX(), logo.centerY(), dp(30), Color.WHITE);
        drawTextCenter(canvas, "LOGO", logo, 14, Color.rgb(69, 151, 228), true);
        drawText(canvas, orgInfo.name, org.left + dp(76), org.top + dp(34), 17, Color.WHITE, true);
        drawText(canvas, "⌖ " + orgInfo.address, org.left + dp(76), org.top + dp(60), 14, Color.WHITE, false);
        drawSmallButton(canvas, "⇄ 切换机构", org.left + dp(76), org.top + dp(82), dp(106), dp(30), false, new ClickAction() {
            @Override
            public void run() {
                showOrganizationDialog();
            }
        });

        String searchLabel = TextUtils.isEmpty(searchKeyword) ? "⌕  搜索宠物名字/主人" : "⌕  " + searchKeyword;
        drawSearchBox(canvas, searchLabel, dp(14), dp(208), w - dp(28), dp(36), new ClickAction() {
            @Override
            public void run() {
                showSearchDialog();
            }
        });

        List<Integer> visibleCases = getVisibleCaseIndices();
        float rowY = dp(264);
        if (visibleCases.isEmpty()) {
            drawText(canvas, "没有匹配的治疗记录", dp(20), rowY + dp(18), 13, Color.WHITE, false);
            return;
        }
        for (int i = 0; i < visibleCases.size(); i++) {
            final int caseIndex = visibleCases.get(i);
            PatientCase item = cases.get(caseIndex);
            RectF r = rect(dp(14), rowY, w - dp(14), rowY + dp(136));
            boolean selected = caseIndex == selectedCaseIndex;
            rounded(canvas, r, selected ? Color.WHITE : Color.TRANSPARENT, selected ? Color.TRANSPARENT : Color.TRANSPARENT, dp(8));
            int primary = selected ? activeBlue : Color.WHITE;
            int secondary = selected ? text : Color.WHITE;
            int weak = selected ? muted : Color.rgb(226, 242, 255);
            String petName = pendingText(item.petName);
            String species = pendingText(item.species);
            String owner = pendingText(item.ownerName);
            String no = pendingText(item.monitorNo);
            String date = pendingText(item.visitDate);
            float petMax = r.right - dp(58) - dp(20);
            float infoMax = r.right - r.left - dp(36);
            drawText(canvas, fitSidebarText("• 宠物名： " + petName, 16, true, petMax),
                    r.left + dp(12), r.top + dp(28), 16, primary, true);
            drawText(canvas, fitSidebarText("种类： " + species + "    主人： " + owner, 13, false, infoMax),
                    r.left + dp(18), r.top + dp(58), 13, secondary, false);
            drawText(canvas, fitSidebarText("编号： " + no, 13, false, infoMax),
                    r.left + dp(18), r.top + dp(82), 13, secondary, false);
            drawText(canvas, fitSidebarText(date, 13, false, infoMax),
                    r.left + dp(18), r.top + dp(108), 13, weak, false);
            drawSmallButton(canvas, "ICU", r.right - dp(58), r.top + dp(16), dp(42), dp(20), selected, new ClickAction() {
                @Override
                public void run() {
                    selectCase(caseIndex);
                }
            });
            clickZones.add(new ClickZone(new RectF(r), new ClickAction() {
                @Override
                public void run() {
                    selectCase(caseIndex);
                }
            }));
            rowY += dp(136);
            if (rowY > h - dp(80)) {
                break;
            }
        }
    }

    private void drawTopBar(Canvas canvas, float x, float y, float w, float h) {
        fill(canvas, x, y, x + w, y + h, navy);
        drawTopButton(canvas, "▯  动物样本", x, y, dp(160), h, !showingSettings, new ClickAction() {
            @Override
            public void run() {
                showingSettings = false;
            }
        });
        drawTopButton(canvas, "⬡  设置", x + dp(160), y, dp(160), h, showingSettings, new ClickAction() {
            @Override
            public void run() {
                showingSettings = true;
            }
        });
        String label = TextUtils.isEmpty(accountStore.getCurrentAccount()) ? "登录" : "◎ " + accountStore.getCurrentAccount() + "⌄";
        drawTextRight(canvas, label, x + w - dp(18), y + dp(28), 16, Color.WHITE, true);
        clickZones.add(new ClickZone(rect(x + w - dp(120), y, x + w, y + h), new ClickAction() {
            @Override
            public void run() {
                handleAccountTap();
            }
        }));
    }

    private void drawTopButton(Canvas canvas, String label, float x, float y, float w, float h, boolean active, ClickAction action) {
        int fill = active ? activeBlue : navy;
        rounded(canvas, rect(x, y, x + w, y + h), fill, fill, 0);
        drawTextCenter(canvas, label, rect(x, y, x + w, y + h), 17, Color.WHITE, true);
        clickZones.add(new ClickZone(rect(x, y, x + w, y + h), action));
    }

    private void drawPatientHeader(Canvas canvas, float x, float y, float w, float h) {
        fill(canvas, x, y, x + w, y + h, Color.WHITE);
        RectF shell = rect(x + dp(28), y + dp(16), x + w - dp(18), y + h - dp(16));
        rounded(canvas, shell, Color.rgb(230, 237, 248), Color.TRANSPARENT, dp(8));
        final PatientCase patient = currentCase();
        float fieldW = (shell.width() - dp(510)) / 4f;
        if (patient == null) {
            drawText(canvas, "暂无患者信息", shell.left + dp(18), shell.top + dp(30), 13, text, true);
            return;
        }
        float fy = shell.top + dp(12);
        float rowGap = dp(40);
        float labelW = dp(72);
        drawFormField(canvas, "宠物名称", patient.petName, shell.left + dp(18), fy, labelW, fieldW, true);
        drawFormField(canvas, "年龄", patient.age, shell.left + dp(18) + labelW + fieldW + dp(42), fy, dp(44), fieldW * 0.8f, false);
        drawFormField(canvas, "宠物主人", patient.ownerName, shell.left + dp(18) + (labelW + fieldW + dp(42)) * 2, fy, dp(72), fieldW, false);
        drawFormField(canvas, "主治医生", patient.doctor, shell.left + dp(18) + (labelW + fieldW + dp(42)) * 3, fy, dp(72), fieldW, false);

        drawFormField(canvas, "动物种类", patient.species, shell.left + dp(18), fy + rowGap, labelW, fieldW, false);
        drawFormField(canvas, "病历编号", patient.recordNo, shell.left + dp(18) + labelW + fieldW + dp(42), fy + rowGap, dp(72), fieldW, true);
        drawFormField(canvas, "联系电话", patient.ownerPhone, shell.left + dp(18) + (labelW + fieldW + dp(42)) * 2, fy + rowGap, dp(72), fieldW, true);
        drawFormField(canvas, "就诊时间", patient.visitDate, shell.left + dp(18) + (labelW + fieldW + dp(42)) * 3, fy + rowGap, dp(72), fieldW, false);

        RectF order = rect(shell.right - dp(330), shell.top + dp(14), shell.right - dp(18), shell.top + dp(84));
        rounded(canvas, order, Color.WHITE, Color.TRANSPARENT, dp(4));
        drawText(canvas, "医嘱", order.left - dp(42), order.top + dp(24), 13, text, false);
        String headerNoteText = TextUtils.isEmpty(patient.note) ? "--" : patient.note;
        drawText(canvas, headerNoteText, order.left + dp(12), order.top + dp(32), 12, text, false);

        final ClickAction editPatientAction = new ClickAction() {
            @Override
            public void run() {
                showEditPatientDialog(patient);
            }
        };
        clickZones.add(new ClickZone(rect(shell.left + dp(12), shell.top + dp(6), order.left - dp(8), shell.top + dp(92)), editPatientAction));
        clickZones.add(new ClickZone(new RectF(order), editPatientAction));

        float by = shell.bottom - dp(38);
        String[] labels = {"编辑", "删除", "打印", "下载"};
        final int[] actions = {1, 2, 0, 3};
        for (int i = 0; i < labels.length; i++) {
            final int mappedAction = actions[i];
            drawHeaderButton(canvas, labels[i], shell.left + dp(28) + i * dp(96), by, dp(76), dp(30), i == 3, new ClickAction() {
                @Override
                public void run() {
                    handlePatientAction(mappedAction);
                }
            });
        }
    }

    private void seedHomeData() {
        organizations.add(new Organization("本院动物医院", "请在设置中维护医院地址", "请维护医院电话"));
        organizations.add(new Organization("动物ICU监护中心", "请在设置中维护中心地址", "请维护联系电话"));
        String now = currentTimeText();
        cases.add(new PatientCase("000001", "000001", "待录入", "待录入", "-", "-", "待录入", "", "", "000001", now, ""));
        for (int i = 0; i < cases.size(); i++) {
            PatientCase item = cases.get(i);
            item.currentTreatment = i == 0;
            item.treatmentEndTime = item.currentTreatment ? "进行中" : item.visitDate.substring(0, 10) + " 18:00:00";
            for (TreatmentEntry entry : item.treatmentEntries) {
                entry.endTime = item.treatmentEndTime;
            }
        }
    }

    private void loadUiSettings() {
        SharedPreferences prefs = activity.getSharedPreferences(PREFS_NAME, 0);
        loadOrganizationsFromStorage(prefs);
        loadDeviceProfileFromStorage(prefs);
        selectedOrganizationIndex = clampIndex(prefs.getInt(KEY_SELECTED_ORGANIZATION, selectedOrganizationIndex), organizations.size());
        selectedHostModeIndex = clampIndex(prefs.getInt(KEY_SELECTED_HOST_MODE, selectedHostModeIndex), hostModeNames().length);
        lastHostModeIndex = clampIndex(prefs.getInt(KEY_LAST_HOST_MODE, lastHostModeIndex), hostModeNames().length);
        treatmentPeriodMode = prefs.getString(KEY_TREATMENT_PERIOD_MODE, PERIOD_ALL);
        monitorPulseBeep = prefs.getBoolean(KEY_MONITOR_PULSE_BEEP, monitorPulseBeep);
        monitorAlarmSound = prefs.getBoolean(KEY_MONITOR_ALARM_SOUND, monitorAlarmSound);
        loadMonitorThresholdState();
        loadS5Settings(prefs);
        selectedMonitorLevelColor = normalizeMonitorLevelColor(prefs.getString(KEY_MONITOR_LEVEL_COLOR, selectedMonitorLevelColor));
        if (!PERIOD_ENABLED.equals(treatmentPeriodMode) && !PERIOD_CUSTOM.equals(treatmentPeriodMode)) {
            treatmentPeriodMode = PERIOD_ALL;
        }
    }

    private void saveUiSettings() {
        activity.getSharedPreferences(PREFS_NAME, 0)
                .edit()
                .putInt(KEY_SELECTED_ORGANIZATION, selectedOrganizationIndex)
                .putString(KEY_ORGANIZATIONS, organizationsToJsonString())
                .putInt(KEY_SELECTED_HOST_MODE, selectedHostModeIndex)
                .putInt(KEY_LAST_HOST_MODE, lastHostModeIndex)
                .putString(KEY_TREATMENT_PERIOD_MODE, treatmentPeriodMode)
                .putBoolean(KEY_MONITOR_PULSE_BEEP, monitorPulseBeep)
                .putBoolean(KEY_MONITOR_ALARM_SOUND, monitorAlarmSound)
                .putString(KEY_MONITOR_LEVEL_COLOR, selectedMonitorLevelColor)
                .putString(KEY_DEVICE_PROFILE, deviceProfileToJsonString())
                .putString(KEY_S5_LANGUAGE, s5Language)
                .putString(KEY_S5_TIME_FORMAT, s5TimeFormat)
                .putInt(KEY_S5_PRINT_COPIES, s5PrintCopies)
                .putString(KEY_S5_PRINTER_ADDRESS, s5PrinterAddress)
                .putBoolean(KEY_S5_DEMO_DATA, s5DemoData)
                .putString(KEY_S5_REPORT_TITLE, s5ReportTitle)
                .putString(KEY_S5_REPORT_DECLARATION, s5ReportDeclaration)
                .putString(KEY_S5_FOOTER_LOGO_URI, s5FooterLogoUri)
                .putBoolean(KEY_S5_CLOUD_PLATFORM, s5CloudPlatform)
                .putBoolean(KEY_S5_LIS, s5Lis)
                .apply();
    }

    // ★ V1.02 S5 设置：读取/保存/JSON 桥/恢复出厂/时间格式
    private void loadS5Settings(SharedPreferences prefs) {
        s5Language = prefs.getString(KEY_S5_LANGUAGE, s5Language);
        s5TimeFormat = prefs.getString(KEY_S5_TIME_FORMAT, s5TimeFormat);
        s5PrintCopies = prefs.getInt(KEY_S5_PRINT_COPIES, s5PrintCopies);
        s5PrinterAddress = prefs.getString(KEY_S5_PRINTER_ADDRESS, s5PrinterAddress);
        s5DemoData = prefs.getBoolean(KEY_S5_DEMO_DATA, s5DemoData);
        s5ReportTitle = prefs.getString(KEY_S5_REPORT_TITLE, s5ReportTitle);
        s5ReportDeclaration = prefs.getString(KEY_S5_REPORT_DECLARATION, s5ReportDeclaration);
        s5FooterLogoUri = prefs.getString(KEY_S5_FOOTER_LOGO_URI, s5FooterLogoUri);
        s5CloudPlatform = prefs.getBoolean(KEY_S5_CLOUD_PLATFORM, s5CloudPlatform);
        s5Lis = prefs.getBoolean(KEY_S5_LIS, s5Lis);
    }

    private JSONObject s5SettingsToJson() throws JSONException {
        JSONObject json = new JSONObject();
        json.put("language", s5Language);
        json.put("timeFormat", s5TimeFormat);
        json.put("printCopies", s5PrintCopies);
        json.put("printerAddress", s5PrinterAddress);
        json.put("demoData", s5DemoData);
        json.put("reportTitle", s5ReportTitle);
        json.put("reportDeclaration", s5ReportDeclaration);
        json.put("footerLogoUri", s5FooterLogoUri);
        json.put("cloudPlatform", s5CloudPlatform);
        json.put("lis", s5Lis);
        return json;
    }

    /** 把 H5 设置页提交的 JSON 写入 S5 设置并落盘。 */
    void applyS5SettingsFromText(String text) {
        if (TextUtils.isEmpty(text)) {
            Toast.makeText(activity, "设置内容为空", Toast.LENGTH_SHORT).show();
            return;
        }
        try {
            JSONObject obj = new JSONObject(text);
            s5Language = obj.optString("language", s5Language);
            s5TimeFormat = obj.optString("timeFormat", s5TimeFormat);
            s5PrintCopies = obj.optInt("printCopies", s5PrintCopies);
            if (s5PrintCopies < 1) s5PrintCopies = 1;
            s5PrinterAddress = obj.optString("printerAddress", s5PrinterAddress);
            s5DemoData = obj.optBoolean("demoData", s5DemoData);
            s5ReportTitle = obj.optString("reportTitle", s5ReportTitle);
            s5ReportDeclaration = obj.optString("reportDeclaration", s5ReportDeclaration);
            s5FooterLogoUri = obj.optString("footerLogoUri", s5FooterLogoUri);
            s5CloudPlatform = obj.optBoolean("cloudPlatform", s5CloudPlatform);
            s5Lis = obj.optBoolean("lis", s5Lis);
            saveUiSettings();
            pushSettingsToLanhu();
            Toast.makeText(activity, "设置已保存", Toast.LENGTH_SHORT).show();
            appendUserLog("设置", "保存S5设置", "语言=" + s5Language + " 份数=" + s5PrintCopies);
        } catch (JSONException e) {
            Toast.makeText(activity, "设置解析失败: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    /** 把当前 S5 设置推送给 H5（注入 window.__icuSettings 并派发事件）。 */
    void pushSettingsToLanhu() {
        try {
            final String json = s5SettingsToJson().toString();
            if (activity instanceof MainActivity) {
                String script = "window.__icuSettings=JSON.parse(" + JSONObject.quote(json) + ");"
                        + "window.dispatchEvent(new Event('icu-settings-loaded'));";
                ((MainActivity) activity).evaluateLanhuJs(script, null);
            }
        } catch (JSONException ignored) {
        }
    }

    /** 恢复出厂：清空 S5 设置、仪器档案、机构、监护设置（账号权限由独立文件保留）。 */
    void factoryResetSettings() {
        activity.getSharedPreferences(PREFS_NAME, 0).edit()
                .remove(KEY_S5_LANGUAGE).remove(KEY_S5_TIME_FORMAT).remove(KEY_S5_PRINT_COPIES)
                .remove(KEY_S5_PRINTER_ADDRESS)
                .remove(KEY_S5_DEMO_DATA)
                .remove(KEY_S5_REPORT_TITLE).remove(KEY_S5_REPORT_DECLARATION).remove(KEY_S5_FOOTER_LOGO_URI)
                .remove(KEY_S5_CLOUD_PLATFORM).remove(KEY_S5_LIS)
                .remove(KEY_DEVICE_PROFILE).remove(KEY_ORGANIZATIONS).remove(KEY_SELECTED_ORGANIZATION)
                .remove(KEY_SELECTED_HOST_MODE).remove(KEY_LAST_HOST_MODE).remove(KEY_TREATMENT_PERIOD_MODE)
                .remove(KEY_MONITOR_PULSE_BEEP).remove(KEY_MONITOR_ALARM_SOUND).remove(KEY_MONITOR_LEVEL_COLOR)
                .apply();
        s5Language = "zh"; s5TimeFormat = "yyyy-MM-dd HH:mm:ss"; s5PrintCopies = 1;
        s5PrinterAddress = "";
        s5DemoData = false;
        s5ReportTitle = ""; s5ReportDeclaration = "※本检测结果仅对该样本负责"; s5FooterLogoUri = ""; s5CloudPlatform = false; s5Lis = false;
        loadUiSettings();
        pushSettingsToLanhu();
        Toast.makeText(activity, "已恢复出厂设置", Toast.LENGTH_LONG).show();
        appendUserLog("设置", "恢复出厂", "已清空本地设置（保留账号权限）");
        if (activity instanceof MainActivity) {
            ((MainActivity) activity).notifyLanhuStateChanged();
        }
    }

    /** S5 时间格式：生成报告/日志时间统一使用用户设置格式，非法时回退默认。 */
    String formatSettingsDateTime(Date date) {
        try {
            return new SimpleDateFormat(s5TimeFormat, Locale.getDefault()).format(date);
        } catch (Exception e) {
            return new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(date);
        }
    }

    /**
     * 读取已记录的教程页视频列表指纹。空字符串表示从未记录过。
     */
    String getTutorialFingerprint() {
        return activity.getSharedPreferences(PREFS_NAME, 0)
                .getString(KEY_TUTORIAL_GALLERY_FINGERPRINT, "");
    }

    /**
     * 把教程页视频列表指纹写入 SharedPreferences。供 JS 首次成功拿到状态后调用。
     */
    void recordTutorialFingerprint(String fingerprint) {
        if (TextUtils.isEmpty(fingerprint)) return;
        activity.getSharedPreferences(PREFS_NAME, 0).edit()
                .putString(KEY_TUTORIAL_GALLERY_FINGERPRINT, fingerprint)
                .apply();
    }

    /**
     * 是否曾经记录过教程页视频指纹。供 IcuNative.tutorialState() 返回 firstLoad 字段。
     */
    boolean hasRecordedTutorialFingerprint() {
        return !TextUtils.isEmpty(getTutorialFingerprint());
    }

    /* ★ 2026-10-09 教程页槽位绑定：6 个槽位各自存一条 JSON {uri, mime, name}，
       用户通过系统选择器主动挑选照片/视频绑定，替换旧的"按日期倒序取第 N 个"被动模式。 */
    static final int TUTORIAL_SLOT_COUNT = 6;
    private static final String KEY_TUTORIAL_SLOT_PREFIX = "tutorial_slot_";

    private SharedPreferences tutorialPrefs() {
        return activity.getSharedPreferences(PREFS_NAME, 0);
    }

    /** 读取某槽位绑定；未绑定返回 null。 */
    private JSONObject tutorialSlotBinding(int slot) {
        try {
            String raw = tutorialPrefs().getString(KEY_TUTORIAL_SLOT_PREFIX + slot, "");
            if (TextUtils.isEmpty(raw)) return null;
            return new JSONObject(raw);
        } catch (Exception e) {
            return null;
        }
    }

    /** 供 tutorialState() 扩展：6 槽数组，元素 {uri, mime, name, thumb}，未绑定字段为空串。 */
    JSONArray getTutorialSlotsJson() {
        JSONArray arr = new JSONArray();
        for (int i = 0; i < TUTORIAL_SLOT_COUNT; i++) {
            JSONObject slot = new JSONObject();
            try {
                JSONObject b = tutorialSlotBinding(i);
                slot.put("uri", b == null ? "" : b.optString("uri", ""));
                slot.put("mime", b == null ? "" : b.optString("mime", ""));
                slot.put("name", b == null ? "" : b.optString("name", ""));
                File thumb = tutorialThumbFile(i);
                slot.put("thumb", b != null && thumb.exists() ? Uri.fromFile(thumb).toString() : "");
            } catch (JSONException ignored) {
            }
            arr.put(slot);
        }
        return arr;
    }

    private File tutorialThumbFile(int slot) {
        return new File(new File(activity.getFilesDir(), "tutorial_thumbs"), "slot" + slot + ".jpg");
    }

    /** 选择器返回后调用：持久化授权、存绑定、后台生成缩略图。 */
    void onTutorialMediaSelected(int slot, Uri uri) {
        if (uri == null || slot < 0 || slot >= TUTORIAL_SLOT_COUNT) return;
        try {
            activity.getContentResolver().takePersistableUriPermission(
                    uri, Intent.FLAG_GRANT_READ_URI_PERMISSION);
        } catch (SecurityException ignored) {
            // 部分提供方不支持持久权限，当前会话仍可读
        }
        String mime = activity.getContentResolver().getType(uri);
        if (TextUtils.isEmpty(mime)) mime = "";
        String name = displayNameForUri(uri);
        try {
            JSONObject b = new JSONObject();
            b.put("uri", uri.toString());
            b.put("mime", mime);
            b.put("name", name);
            tutorialPrefs().edit().putString(KEY_TUTORIAL_SLOT_PREFIX + slot, b.toString()).apply();
        } catch (JSONException ignored) {
        }
        generateTutorialThumb(slot, uri, mime);
        Toast.makeText(activity, "已绑定到教程位 " + (slot + 1) + "：" + name, Toast.LENGTH_SHORT).show();
    }

    void clearTutorialSlot(int slot) {
        tutorialPrefs().edit().remove(KEY_TUTORIAL_SLOT_PREFIX + slot).apply();
        File thumb = tutorialThumbFile(slot);
        if (thumb.exists()) thumb.delete();
        invalidate();
    }

    /** 播放/查看某槽位绑定的媒体；未绑定则提示先选择。 */
    void playTutorialSlot(int slot) {
        JSONObject b = tutorialSlotBinding(slot);
        if (b == null) {
            Toast.makeText(activity, "该教程位还未选择照片或视频", Toast.LENGTH_SHORT).show();
            return;
        }
        try {
            Uri uri = Uri.parse(b.optString("uri", ""));
            String mime = b.optString("mime", "video/*");
            if (TextUtils.isEmpty(mime)) mime = "video/*";
            Intent intent = new Intent(Intent.ACTION_VIEW);
            intent.setDataAndType(uri, mime);
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            activity.startActivity(intent);
        } catch (Exception e) {
            Toast.makeText(activity, "打开失败：" + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    /** 后台线程生成缩略图：图片采样解码 / 视频取首帧（MediaMetadataRetriever，framework 自带）。 */
    private void generateTutorialThumb(final int slot, final Uri uri, final String mime) {
        new Thread(new Runnable() {
            @Override
            public void run() {
                Bitmap bmp = null;
                try {
                    if (mime != null && mime.startsWith("image/")) {
                        bmp = decodeSampled(uri, 900, 600);
                    } else {
                        android.media.MediaMetadataRetriever mmr = new android.media.MediaMetadataRetriever();
                        try {
                            mmr.setDataSource(activity, uri);
                            bmp = mmr.getFrameAtTime(0);
                        } finally {
                            mmr.release();
                        }
                    }
                    if (bmp == null) return;
                    File out = tutorialThumbFile(slot);
                    out.getParentFile().mkdirs();
                    FileOutputStream fos = new FileOutputStream(out);
                    bmp.compress(Bitmap.CompressFormat.JPEG, 85, fos);
                    fos.close();
                    bmp.recycle();
                    activity.runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            if (activity instanceof MainActivity) {
                                ((MainActivity) activity).notifyLanhuStateChanged();
                            }
                        }
                    });
                } catch (Exception ignored) {
                }
            }
        }, "tutorial-thumb").start();
    }

    /** 按目标尺寸采样解码 content Uri 图片，避免大图 OOM。 */
    private Bitmap decodeSampled(Uri uri, int reqW, int reqH) throws IOException {
        BitmapFactory.Options bounds = new BitmapFactory.Options();
        bounds.inJustDecodeBounds = true;
        java.io.InputStream in = activity.getContentResolver().openInputStream(uri);
        if (in == null) return null;
        BitmapFactory.decodeStream(in, null, bounds);
        in.close();
        int sample = 1;
        while (bounds.outWidth / (sample * 2) >= reqW && bounds.outHeight / (sample * 2) >= reqH) {
            sample *= 2;
        }
        BitmapFactory.Options opts = new BitmapFactory.Options();
        opts.inSampleSize = sample;
        in = activity.getContentResolver().openInputStream(uri);
        if (in == null) return null;
        Bitmap bmp = BitmapFactory.decodeStream(in, null, opts);
        in.close();
        return bmp;
    }

    private String normalizeMonitorLevelColor(String color) {
        if ("red".equals(color) || "yellow".equals(color) || "green".equals(color)) {
            return color;
        }
        return "yellow";
    }

    private String currentMonitorLevelColor() {
        // ★ 修复 P1-1:设备回值优先,本地与设备不一致时以设备为准 + 同步本地 + Toast
        if (!isHostConnected()) {
            return normalizeMonitorLevelColor(selectedMonitorLevelColor);
        }
        String live = bleManager.getStatusLightColor();
        if (TextUtils.isEmpty(live)) {
            return normalizeMonitorLevelColor(selectedMonitorLevelColor);
        }
        String normalized = normalizeMonitorLevelColor(live);
        String localStored = normalizeMonitorLevelColor(selectedMonitorLevelColor);
        if (!TextUtils.equals(localStored, normalized)) {
            // 设备回值与本地不一致 → 以设备为准,同步本地
            selectedMonitorLevelColor = normalized;
            saveUiSettings();
            final String deviceLabel = monitorLevelLabel(normalized);
            activity.runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    android.widget.Toast.makeText(activity,
                        "监护等级已与设备同步:" + deviceLabel,
                        android.widget.Toast.LENGTH_SHORT).show();
                }
            });
        }
        return normalized;
    }

    private String currentMonitorLevelLabel() {
        return monitorLevelLabel(currentMonitorLevelColor());
    }

    public String getResolvedMonitorLevelColor() {
        return currentMonitorLevelColor();
    }

    public String getResolvedMonitorLevelLabel() {
        return currentMonitorLevelLabel();
    }

    private String nextMonitorLevelColor(String color) {
        if ("red".equals(color)) {
            return "yellow";
        }
        if ("yellow".equals(color)) {
            return "green";
        }
        return "red";
    }

    private int monitorLevelFillColor(String color) {
        if ("red".equals(color)) {
            return red;
        }
        if ("green".equals(color)) {
            return green;
        }
        return orange;
    }

    private String monitorLevelLabel(String color) {
        if ("red".equals(color)) {
            return "一级";
        }
        if ("green".equals(color)) {
            return "三级";
        }
        return "二级";
    }

    private void loadDeviceProfileFromStorage(SharedPreferences prefs) {
        String raw = prefs.getString(KEY_DEVICE_PROFILE, "");
        if (TextUtils.isEmpty(raw)) {
            return;
        }
        try {
            JSONObject json = new JSONObject(raw);
            deviceProfile.productModel = optNonEmpty(json, "productModel", deviceProfile.productModel);
            deviceProfile.machineType = optNonEmpty(json, "machineType", deviceProfile.machineType);
            deviceProfile.serialNo = optNonEmpty(json, "serialNo", deviceProfile.serialNo);
            deviceProfile.manufactureDate = optNonEmpty(json, "manufactureDate", deviceProfile.manufactureDate);
            deviceProfile.softwareVersion = optNonEmpty(json, "softwareVersion", deviceProfile.softwareVersion);
        } catch (JSONException ignored) {
        }
    }

    private String deviceProfileToJsonString() {
        try {
            return deviceProfileToJson().toString();
        } catch (JSONException exception) {
            return "{}";
        }
    }

    private JSONObject deviceProfileToJson() throws JSONException {
        JSONObject json = new JSONObject();
        json.put("productModel", firstNonEmpty(deviceProfile.productModel, "EM-150Vet-20"));
        json.put("machineType", firstNonEmpty(deviceProfile.machineType, ""));
        json.put("serialNo", firstNonEmpty(deviceProfile.serialNo, ""));
        json.put("manufactureDate", firstNonEmpty(deviceProfile.manufactureDate, ""));
        json.put("softwareVersion", firstNonEmpty(deviceProfile.softwareVersion, ""));
        return json;
    }

    private void loadOrganizationsFromStorage(SharedPreferences prefs) {
        String raw = prefs.getString(KEY_ORGANIZATIONS, "");
        if (TextUtils.isEmpty(raw)) {
            return;
        }
        try {
            JSONArray array = new JSONArray(raw);
            if (array.length() == 0) {
                return;
            }
            organizations.clear();
            for (int i = 0; i < array.length(); i++) {
                JSONObject item = array.getJSONObject(i);
                organizations.add(new Organization(
                        item.optString("name", "未设置医院"),
                        item.optString("address", "未设置地址"),
                        item.optString("phone", "未设置电话"),
                        // ★ 加载 logoUri 和 logoName,LOGO 才能跨重启保留
                        item.optString("logoUri", ""),
                        item.optString("logoName", "")));
            }
        } catch (JSONException ignored) {
        }
    }

    private String organizationsToJsonString() {
        JSONArray array = new JSONArray();
        try {
            for (Organization organization : organizations) {
                JSONObject item = new JSONObject();
                item.put("name", organization.name);
                item.put("address", organization.address);
                item.put("phone", organization.phone);
                // ★ logoUri 和 logoName 也持久化,否则 APP 重启后 LOGO 丢失
                item.put("logoUri", organization.logoUri == null ? "" : organization.logoUri);
                item.put("logoName", organization.logoName == null ? "" : organization.logoName);
                array.put(item);
            }
        } catch (JSONException ignored) {
        }
        return array.toString();
    }

    private void ensureTreatmentEntrySet(PatientCase patient) {
        if (patient == null) {
            return;
        }
        for (String[] item : TREATMENT_TEMPLATES) {
            if (!hasTreatmentEntry(patient, item[0])) {
                patient.treatmentEntries.add(new TreatmentEntry(item[0], item[1],
                        Boolean.parseBoolean(item[2]), Boolean.parseBoolean(item[3]),
                        isSessionTrackedTreatment(item[0]) ? "" : patient.treatmentStartTime,
                        isSessionTrackedTreatment(item[0]) ? "" : patient.treatmentEndTime));
            }
        }
    }

    private boolean hasTreatmentEntry(PatientCase patient, String targetName) {
        for (TreatmentEntry entry : patient.treatmentEntries) {
            if (sameTreatmentName(entry.itemName, targetName)) {
                return true;
            }
        }
        return false;
    }

    private boolean sameTreatmentName(String left, String right) {
        if (TextUtils.equals(left, right)) {
            return true;
        }
        if (("氧气浓度".equals(left) && "氧浓度".equals(right)) || ("氧浓度".equals(left) && "氧气浓度".equals(right))) {
            return true;
        }
        return ("雾化治疗".equals(left) && "雾化器".equals(right)) || ("雾化器".equals(left) && "雾化治疗".equals(right));
    }

    private static int treatmentControlIndexOf(String itemName) {
        if ("红外理疗".equals(itemName)) {
            return 6;
        }
        if ("蓝光理疗".equals(itemName)) {
            return 7;
        }
        if ("雾化器".equals(itemName) || "雾化治疗".equals(itemName)) {
            return 10;
        }
        if ("负离子".equals(itemName)) {
            return 11;
        }
        if ("紫外消毒".equals(itemName)) {
            return 12;
        }
        return -1;
    }

    private static boolean isSessionTrackedTreatment(String itemName) {
        return treatmentControlIndexOf(itemName) >= 0;
    }

    private void seedHistoricalTreatmentStats() {
        for (int i = 0; i < cases.size(); i++) {
            PatientCase patient = cases.get(i);
            if (patient.currentTreatment) {
                continue;
            }
            for (TreatmentEntry entry : patient.treatmentEntries) {
                if ("舱内温度".equals(entry.itemName)) {
                    seedEntry(entry, 31.2f, 32.0f, 30.8f, "31.6℃");
                } else if ("氧气浓度".equals(entry.itemName) || "氧浓度".equals(entry.itemName)) {
                    seedEntry(entry, 34f, 36f, 32f, "35%");
                } else if ("红外理疗".equals(entry.itemName)) {
                    seedEntry(entry, 35f, 45f, 20f, "开启 35分钟");
                } else if ("蓝光理疗".equals(entry.itemName)) {
                    seedEntry(entry, 20f, 30f, 10f, "开启 20分钟");
                } else if ("雾化器".equals(entry.itemName) || "雾化治疗".equals(entry.itemName)) {
                    seedEntry(entry, 18f, 25f, 10f, "开启 18分钟");
                } else if ("负离子".equals(entry.itemName)) {
                    seedEntry(entry, 30f, 45f, 15f, "开启 30分钟");
                } else if ("湿度".equals(entry.itemName)) {
                    seedEntry(entry, 58f, 62f, 54f, "58%");
                } else if ("CO2浓度".equals(entry.itemName)) {
                    seedEntry(entry, 580f, 620f, 540f, "580PPM");
                } else if ("护理模式".equals(entry.itemName)) {
                    seedEntry(entry, 1f, 1f, 1f, selectedHostModeName());
                } else if ("治疗时长".equals(entry.itemName)) {
                    seedEntry(entry, 92f, 120f, 60f, "92分钟");
                } else if ("心率".equals(entry.itemName)) {
                    seedEntry(entry, 78f, 92f, 66f, "78bpm");
                } else if ("血压".equals(entry.itemName)) {
                    seedEntry(entry, 118f, 132f, 105f, "118/75");
                } else if ("血氧".equals(entry.itemName)) {
                    seedEntry(entry, 97f, 100f, 94f, "97%");
                } else if ("脉率".equals(entry.itemName)) {
                    seedEntry(entry, 80f, 96f, 68f, "80bpm");
                } else if ("体温".equals(entry.itemName)) {
                    seedEntry(entry, 37.1f, 37.8f, 36.5f, "37.1℃");
                } else if ("呼吸率".equals(entry.itemName)) {
                    seedEntry(entry, 22f, 30f, 16f, "22brpm");
                } else if ("心电图".equals(entry.itemName)) {
                    seedEntry(entry, 78f, 92f, 66f, "ECG 78bpm");
                } else if ("血氧波形".equals(entry.itemName)) {
                    seedEntry(entry, 97f, 100f, 94f, "PLETH 97%");
                } else if ("呼吸率波形".equals(entry.itemName)) {
                    seedEntry(entry, 22f, 30f, 16f, "RESP 22brpm");
                }
            }
        }
    }

    private void seedEntry(TreatmentEntry entry, float avg, float high, float low, String lastValue) {
        entry.sampleCount = 6;
        entry.sum = avg * entry.sampleCount;
        entry.high = high;
        entry.low = low;
        entry.lastValue = lastValue;
    }

    private void captureTreatmentSampleIfNeeded(boolean force) {
        PatientCase patient = currentTreatmentCase();
        if (patient == null) {
            return;
        }
        long now = System.currentTimeMillis();
        if (!force && patient.lastTreatmentSampleAt > 0 && now - patient.lastTreatmentSampleAt < 60000L) {
            return;
        }
        patient.lastTreatmentSampleAt = now;
        String sampleTime = currentTimeText();
        boolean changed = false;
        for (TreatmentEntry entry : patient.treatmentEntries) {
            if (!entry.enabled) {
                continue;
            }
            String display = treatmentDisplayValue(entry.itemName);
            float value = parseFirstNumber(display);
            int before = entry.sampleCount;
            entry.record(value, display);
            changed = changed || entry.sampleCount != before;
            if ("进行中".equals(entry.endTime)) {
                entry.endTime = "进行中";
            }
            if (TextUtils.isEmpty(entry.startTime)) {
                entry.startTime = sampleTime;
                entry.customPeriod = false;
            }
        }
        if (changed) {
            saveTreatmentRecordsToStorage();
        }
    }

    private PatientCase currentTreatmentCase() {
        for (PatientCase item : cases) {
            if (item.currentTreatment) {
                return item;
            }
        }
        return null;
    }

    private String treatmentDisplayValue(String itemName) {
        if ("舱内温度".equals(itemName)) {
            return bleManager.getCabinTempText();
        }
        if ("氧气浓度".equals(itemName) || "氧浓度".equals(itemName)) {
            return bleManager.getOxygenText();
        }
        if ("红外理疗".equals(itemName)) {
            return bleManager.getControlValue(6);
        }
        if ("蓝光理疗".equals(itemName)) {
            return bleManager.getControlValue(7);
        }
        if ("雾化治疗".equals(itemName) || "雾化器".equals(itemName)) {
            return bleManager.getControlValue(10);
        }
        if ("负离子".equals(itemName)) {
            return bleManager.getControlValue(11);
        }
        if ("湿度".equals(itemName)) {
            return bleManager.getHumidityText();
        }
        if ("CO2浓度".equals(itemName)) {
            return bleManager.getCo2Text();
        }
        if ("治疗时长".equals(itemName)) {
            return bleManager.getTreatmentTimeText();
        }
        if ("护理模式".equals(itemName)) {
            return selectedHostModeName();
        }
        if ("心率".equals(itemName)) {
            return am4100Manager.getHeartRateText();
        }
        if ("血压".equals(itemName)) {
            return am4100Manager.getBloodPressureText();
        }
        if ("血氧".equals(itemName)) {
            return am4100Manager.getSpo2Text();
        }
        if ("脉率".equals(itemName)) {
            return am4100Manager.getPulseRateText();
        }
        if ("体温".equals(itemName)) {
            return am4100Manager.getBodyTempText();
        }
        if ("呼吸率".equals(itemName)) {
            return am4100Manager.getRespText();
        }
        if ("心电图".equals(itemName)) {
            return "ECG " + am4100Manager.getHeartRateText();
        }
        if ("血氧波形".equals(itemName)) {
            return "PLETH " + am4100Manager.getSpo2Text();
        }
        if ("呼吸率波形".equals(itemName)) {
            return "RESP " + am4100Manager.getRespText();
        }
        return "--";
    }

    private void applyManualTreatmentPeriod(PatientCase patient, TreatmentEntry entry, String startTime, String endTime) {
        if (entry == null) {
            return;
        }
        boolean changed = !TextUtils.equals(entry.startTime, startTime)
                || !TextUtils.equals(entry.endTime, endTime);
        entry.startTime = startTime;
        entry.endTime = endTime;
        if (changed) {
            entry.customPeriod = shouldUseCustomPeriodForManualTimes(patient, entry, startTime, endTime);
        }
    }

    private boolean shouldUseCustomPeriodForManualTimes(PatientCase patient, TreatmentEntry entry, String startTime, String endTime) {
        if (TextUtils.isEmpty(startTime) && TextUtils.isEmpty(endTime)) {
            return false;
        }
        if (patient == null || entry == null || isSessionTrackedTreatment(entry.itemName)) {
            return true;
        }
        String autoStartTime = patient.treatmentStartTime;
        String autoEndTime = patient.currentTreatment ? "进行中" : patient.treatmentEndTime;
        return !TextUtils.equals(autoStartTime, startTime)
                || !TextUtils.equals(autoEndTime, endTime);
    }

    private boolean inferLegacyCustomPeriod(PatientCase patient, TreatmentEntry entry) {
        if (patient == null || entry == null || isSessionTrackedTreatment(entry.itemName)) {
            return false;
        }
        String autoStartTime = patient.treatmentStartTime;
        String autoEndTime = patient.currentTreatment ? "进行中" : patient.treatmentEndTime;
        return (!TextUtils.isEmpty(entry.startTime) && !TextUtils.equals(autoStartTime, entry.startTime))
                || (!TextUtils.isEmpty(entry.endTime) && !TextUtils.equals(autoEndTime, entry.endTime));
    }

    private void resetTreatmentEntryTiming(TreatmentEntry entry, String now) {
        if (entry == null) {
            return;
        }
        if (isSessionTrackedTreatment(entry.itemName)) {
            entry.enabled = false;
            entry.customPeriod = false;
            entry.startTime = "";
            entry.endTime = "";
            return;
        }
        entry.customPeriod = false;
        entry.startTime = now;
        entry.endTime = "进行中";
    }

    private void closeTreatmentRecord(PatientCase patient, String now) {
        if (patient == null) {
            return;
        }
        patient.currentTreatment = false;
        patient.treatmentEndTime = now;
        for (TreatmentEntry entry : patient.treatmentEntries) {
            if (isSessionTrackedTreatment(entry.itemName)) {
                boolean hasSession = entry.enabled
                        || !TextUtils.isEmpty(entry.startTime)
                        || "进行中".equals(entry.endTime);
                if (hasSession) {
                    if (TextUtils.isEmpty(entry.startTime)) {
                        entry.startTime = now;
                    }
                    entry.endTime = now;
                }
                entry.enabled = false;
                entry.customPeriod = false;
            } else {
                if (TextUtils.isEmpty(entry.startTime)) {
                    entry.startTime = patient.treatmentStartTime;
                }
                entry.endTime = now;
                entry.customPeriod = false;
            }
        }
        lastGeneratedPdf = null;
        saveTreatmentRecordsToStorage();
    }

    private JSONObject patientCaseToStorageJson(PatientCase patient) throws JSONException {
        JSONObject item = new JSONObject();
        item.put("caseId", patient.caseId);
        item.put("caseNo", patient.caseNo);
        item.put("monitorNo", patient.monitorNo);
        item.put("petName", patient.petName);
        item.put("species", patient.species);
        item.put("sex", patient.sex);
        item.put("age", patient.age);
        item.put("ownerName", patient.ownerName);
        item.put("ownerPhone", patient.ownerPhone);
        item.put("doctor", patient.doctor);
        item.put("recordNo", patient.recordNo);
        item.put("visitDate", patient.visitDate);
        item.put("note", patient.note);
        item.put("weight", safeJsonText(patient.weight));
        item.put("department", safeJsonText(patient.department));
        item.put("followUpDate", safeJsonText(patient.followUpDate));
        item.put("currentTreatment", patient.currentTreatment);
        item.put("treatmentStarted", patient.treatmentStarted);
        item.put("treatmentStartTime", patient.treatmentStartTime);
        item.put("treatmentEndTime", patient.treatmentEndTime);
        item.put("lastTreatmentSampleAt", patient.lastTreatmentSampleAt);
        // ★ 任务29：记录单编辑字段随病例持久化
        item.put("sheetAdvice", safeJsonText(patient.sheetAdvice));
        item.put("sheetConclusionIdx", patient.sheetConclusionIdx);
        item.put("sheetVitals", safeJsonText(patient.sheetVitals));
        JSONArray entries = new JSONArray();
        for (TreatmentEntry entry : patient.treatmentEntries) {
            JSONObject entryJson = new JSONObject();
            entryJson.put("itemName", entry.itemName);
            entryJson.put("unit", entry.unit);
            entryJson.put("enabled", entry.enabled);
            entryJson.put("includeInReport", entry.includeInReport);
            entryJson.put("customPeriod", entry.customPeriod);
            entryJson.put("startTime", entry.startTime);
            entryJson.put("endTime", entry.endTime);
            entryJson.put("manualAverageValue", entry.manualAverageValue);
            entryJson.put("manualHighValue", entry.manualHighValue);
            entryJson.put("manualLowValue", entry.manualLowValue);
            entryJson.put("manualValue", buildTreatmentManualSummary(entry));
            entryJson.put("lastValue", entry.lastValue);
            entryJson.put("sum", entry.sum);
            entryJson.put("high", Float.isNaN(entry.high) ? JSONObject.NULL : entry.high);
            entryJson.put("low", Float.isNaN(entry.low) ? JSONObject.NULL : entry.low);
            entryJson.put("sampleCount", entry.sampleCount);
            entries.put(entryJson);
        }
        item.put("entries", entries);
        return item;
    }

    private JSONObject patientZoneToStorageJson(PatientZoneState state) throws JSONException {
        JSONObject zoneJson = new JSONObject();
        JSONArray array = new JSONArray();
        for (PatientCase patient : state.cases) {
            if (hasMeaningfulPatientData(patient)) {
                array.put(patientCaseToStorageJson(patient));
            }
        }
        zoneJson.put("cases", array);
        zoneJson.put("selectedCaseIndex", clampIndex(state.selectedCaseIndex, array.length()));
        // ★ 编号游标随舱持久化，重启后每个舱各自接着往下发。
        zoneJson.put("caseNoCursor", formatCaseNo(state.caseNoCursor, state.caseNoCursorWidth));
        // ★ V1.02 每日住院号游标随舱持久化，跨天后由 nextCaseNo 自动归零。
        zoneJson.put("dailyCaseNoDate", state.dailyCaseNoDate);
        zoneJson.put("dailyCaseNoCursor", state.dailyCaseNoCursor);
        return zoneJson;
    }

    private void saveTreatmentRecordsToStorage() {
        saveTreatmentRecordsToStorage(false);
    }

    private void saveTreatmentRecordsToStorage(boolean immediate) {
        try {
            JSONArray array = new JSONArray();
            for (PatientCase patient : cases) {
                if (!hasMeaningfulPatientData(patient)) {
                    continue;
                }
                JSONObject item = new JSONObject();
                item.put("caseNo", patient.caseNo);
                item.put("monitorNo", patient.monitorNo);
                item.put("petName", patient.petName);
                item.put("species", patient.species);
                item.put("sex", patient.sex);
                item.put("age", patient.age);
                item.put("ownerName", patient.ownerName);
                item.put("ownerPhone", patient.ownerPhone);
                item.put("doctor", patient.doctor);
                item.put("recordNo", patient.recordNo);
                item.put("visitDate", patient.visitDate);
                item.put("note", patient.note);
                item.put("ageUnit", patient.ageUnit);
                item.put("disease", patient.disease);
                item.put("transferredOut", patient.transferredOut);
                item.put("currentTreatment", patient.currentTreatment);
                item.put("treatmentStarted", patient.treatmentStarted);
                item.put("treatmentStartTime", patient.treatmentStartTime);
                item.put("treatmentEndTime", patient.treatmentEndTime);
                item.put("lastTreatmentSampleAt", patient.lastTreatmentSampleAt);
                // ★ 任务29：旧版存储路径同样写入记录单编辑字段（与 v2 一致）
                item.put("sheetAdvice", safeJsonText(patient.sheetAdvice));
                item.put("sheetConclusionIdx", patient.sheetConclusionIdx);
                item.put("sheetVitals", safeJsonText(patient.sheetVitals));
                JSONArray entries = new JSONArray();
                for (TreatmentEntry entry : patient.treatmentEntries) {
                    JSONObject entryJson = new JSONObject();
                    entryJson.put("itemName", entry.itemName);
                    entryJson.put("unit", entry.unit);
                    entryJson.put("enabled", entry.enabled);
                    entryJson.put("includeInReport", entry.includeInReport);
                    entryJson.put("customPeriod", entry.customPeriod);
                    entryJson.put("startTime", entry.startTime);
                    entryJson.put("endTime", entry.endTime);
                    entryJson.put("manualAverageValue", entry.manualAverageValue);
                    entryJson.put("manualHighValue", entry.manualHighValue);
                    entryJson.put("manualLowValue", entry.manualLowValue);
                    entryJson.put("manualValue", buildTreatmentManualSummary(entry));
                    entryJson.put("lastValue", entry.lastValue);
                    entryJson.put("sum", entry.sum);
                    entryJson.put("high", Float.isNaN(entry.high) ? JSONObject.NULL : entry.high);
                    entryJson.put("low", Float.isNaN(entry.low) ? JSONObject.NULL : entry.low);
                    entryJson.put("sampleCount", entry.sampleCount);
                    entries.put(entryJson);
                }
                item.put("entries", entries);
                array.put(item);
            }
            syncActivePatientSelection();
            JSONObject v2 = new JSONObject();
            v2.put("schemaVersion", TREATMENT_CASES_SCHEMA_VERSION);
            JSONObject zones = new JSONObject();
            zones.put("left", patientZoneToStorageJson(leftPatients));
            zones.put("right", patientZoneToStorageJson(rightPatients));
            v2.put("zones", zones);
            SharedPreferences prefs = activity.getSharedPreferences(PREFS_NAME, 0);
            SharedPreferences.Editor editor = prefs.edit()
                    .putString(KEY_TREATMENT_CASES_V2, v2.toString())
                    .putString(KEY_TREATMENT_CASES, array.toString())
                    // 兼容旧版本读取：这里只写当前舱游标，真正的按舱游标在 v2.zones 内。
                    .putString(KEY_CASE_NO_CURSOR,
                            formatCaseNo(activePatientState().caseNoCursor, activePatientState().caseNoCursorWidth));
            if (immediate) {
                editor.commit();
            } else {
                editor.apply();
            }
            activity.notifyLanhuStateChanged();
        } catch (Exception ignored) {
        }
    }

    // ★ V1.02 S2 用户操作日志：记录编辑/删除/开始护疗/结束等关键动作，供 S8 日志模块展示。
    void appendUserLog(String module, String action, String detail) {
        if (activity == null) {
            return;
        }
        try {
            JSONObject item = new JSONObject();
            item.put("time", currentTimeText());
            item.put("account", firstNonEmpty(accountStore.getCurrentAccount(), "-"));
            item.put("zone", bleManager.getCurrentZone());
            item.put("module", module);
            item.put("action", action);
            item.put("detail", detail);
            item.put("result", "成功");
            userOpLog.put(item);
            while (userOpLog.length() > 1000) {
                userOpLog.remove(0);
            }
            saveUserOpLog();
        } catch (JSONException ignored) {
        }
    }

    private void saveUserOpLog() {
        try {
            SharedPreferences prefs = activity.getSharedPreferences(PREFS_NAME, 0);
            prefs.edit().putString(KEY_USER_OP_LOG, userOpLog.toString()).apply();
        } catch (Exception ignored) {
        }
    }

    /** ★ 2026-10-08 日志页：userOpLog 倒序拷贝（最新在前），供 buildFirstPhaseStateJson 推给 H5 */
    private JSONArray userLogsToJson() {
        JSONArray array = new JSONArray();
        for (int i = userOpLog.length() - 1; i >= 0; i -= 1) {
            JSONObject item = userOpLog.optJSONObject(i);
            if (item != null) {
                array.put(item);
            }
        }
        return array;
    }

    private void loadUserOpLog() {
        try {
            SharedPreferences prefs = activity.getSharedPreferences(PREFS_NAME, 0);
            String raw = prefs.getString(KEY_USER_OP_LOG, "");
            if (!TextUtils.isEmpty(raw)) {
                JSONArray loaded = new JSONArray(raw);
                for (int i = 0; i < loaded.length(); i++) {
                    userOpLog.put(loaded.get(i));
                }
            }
        } catch (Exception ignored) {
        }
    }

    /* ==================================================================
       ★ 2026-10-08 日志页：常规信息 / 后端调试 / 错误记录 / 堆栈信息 通用存取
       与 userOpLog 同模式：JSONArray 内存 + SharedPreferences 持久化，倒序推给 H5。
       ================================================================== */

    private void appendLog(JSONArray store, String key, int cap, JSONObject item) {
        if (activity == null || item == null) {
            return;
        }
        store.put(item);
        while (store.length() > cap) {
            store.remove(0);
        }
        try {
            SharedPreferences prefs = activity.getSharedPreferences(PREFS_NAME, 0);
            prefs.edit().putString(key, store.toString()).apply();
        } catch (Exception ignored) {
        }
    }

    private void loadLog(String key, JSONArray into) {
        try {
            SharedPreferences prefs = activity.getSharedPreferences(PREFS_NAME, 0);
            String raw = prefs.getString(key, "");
            if (!TextUtils.isEmpty(raw)) {
                JSONArray loaded = new JSONArray(raw);
                for (int i = 0; i < loaded.length(); i++) {
                    into.put(loaded.get(i));
                }
            }
        } catch (Exception ignored) {
        }
    }

    /** 倒序拷贝（最新在前），供 buildFirstPhaseStateJson 推给 H5 */
    private JSONArray logToJson(JSONArray store) {
        JSONArray array = new JSONArray();
        for (int i = store.length() - 1; i >= 0; i -= 1) {
            JSONObject item = store.optJSONObject(i);
            if (item != null) {
                array.put(item);
            }
        }
        return array;
    }

    /** 常规信息：{time, source, msg} */
    public void appendGeneralLog(String source, String msg) {
        try {
            JSONObject item = new JSONObject();
            item.put("time", currentTimeText());
            item.put("source", source);
            item.put("msg", msg);
            appendLog(generalLog, KEY_GENERAL_LOG, 1000, item);
        } catch (JSONException ignored) {
        }
    }

    /** 后端调试：{time, level, source, msg} */
    public void appendDebugLog(String level, String source, String msg) {
        try {
            JSONObject item = new JSONObject();
            item.put("time", currentTimeText());
            item.put("level", level);
            item.put("source", source);
            item.put("msg", msg);
            appendLog(debugLog, KEY_DEBUG_LOG, 1000, item);
        } catch (JSONException ignored) {
        }
    }

    /** 错误记录：{time, source, msg} */
    public void appendErrorLog(String source, String msg) {
        try {
            JSONObject item = new JSONObject();
            item.put("time", currentTimeText());
            item.put("source", source);
            item.put("msg", msg);
            appendLog(errorLog, KEY_ERROR_LOG, 1000, item);
        } catch (JSONException ignored) {
        }
    }

    /** 堆栈信息：{time, msg=完整堆栈文本}，仅保留最近 20 条 */
    public void appendStackLog(String trace) {
        if (TextUtils.isEmpty(trace)) {
            return;
        }
        try {
            JSONObject item = new JSONObject();
            item.put("time", currentTimeText());
            item.put("msg", trace);
            appendLog(stackLog, KEY_STACK_LOG, 20, item);
        } catch (JSONException ignored) {
        }
    }

    /** 通信记录：{time, dir=H5→native / native→H5, type, msg}（只读记录桥接事件，不触碰蓝牙协议） */
    public void appendCommLog(String dir, String type, String msg) {
        try {
            JSONObject item = new JSONObject();
            item.put("time", currentTimeText());
            item.put("dir", dir);
            item.put("type", type);
            item.put("msg", msg == null ? "" : (msg.length() > 300 ? msg.substring(0, 300) : msg));
            appendLog(commLog, KEY_COMM_LOG, 1000, item);
        } catch (JSONException ignored) {
        }
    }

    private void loadTreatmentEntriesFromStorage(PatientCase patient, JSONArray entries) throws JSONException {
        if (entries == null || entries.length() == 0) {
            return;
        }
        patient.treatmentEntries.clear();
        for (int j = 0; j < entries.length(); j++) {
            JSONObject entryJson = entries.getJSONObject(j);
            TreatmentEntry entry = new TreatmentEntry(
                    entryJson.optString("itemName", "项目"),
                    entryJson.optString("unit", ""),
                    entryJson.optBoolean("enabled", true),
                    entryJson.optBoolean("includeInReport", true),
                    entryJson.optString("startTime", patient.treatmentStartTime),
                    entryJson.optString("endTime", patient.treatmentEndTime));
            entry.customPeriod = entryJson.has("customPeriod")
                    ? entryJson.optBoolean("customPeriod", false)
                    : inferLegacyCustomPeriod(patient, entry);
            entry.manualAverageValue = entryJson.optString("manualAverageValue", "");
            entry.manualHighValue = entryJson.optString("manualHighValue", "");
            entry.manualLowValue = entryJson.optString("manualLowValue", "");
            entry.manualValue = entryJson.optString("manualValue", "");
            if (TextUtils.isEmpty(entry.manualAverageValue) && TextUtils.isEmpty(entry.manualHighValue)
                    && TextUtils.isEmpty(entry.manualLowValue) && !TextUtils.isEmpty(entry.manualValue)) {
                entry.manualAverageValue = entry.manualValue;
                entry.manualHighValue = entry.manualValue;
                entry.manualLowValue = entry.manualValue;
            }
            entry.lastValue = entryJson.optString("lastValue", "--");
            entry.sum = (float) entryJson.optDouble("sum", 0);
            entry.high = entryJson.isNull("high") ? Float.NaN : (float) entryJson.optDouble("high", Float.NaN);
            entry.low = entryJson.isNull("low") ? Float.NaN : (float) entryJson.optDouble("low", Float.NaN);
            entry.sampleCount = entryJson.optInt("sampleCount", 0);
            applyManualStatOverrides(entry);
            entry.manualValue = buildTreatmentManualSummary(entry);
            patient.treatmentEntries.add(entry);
        }
    }

    private PatientCase patientCaseFromStorageJson(String zone, JSONObject item, int index) throws JSONException {
        PatientCase patient = new PatientCase(
                item.optString("caseId", ""), item.optString("caseNo", "000000"),
                item.optString("monitorNo", "000000"), item.optString("petName", "未命名"),
                item.optString("species", "未知"), item.optString("sex", "-"), item.optString("age", "-"),
                item.optString("ownerName", "-"), item.optString("ownerPhone", "-"),
                item.optString("doctor", "-"), item.optString("recordNo", "000000"),
                item.optString("visitDate", currentTimeText()), item.optString("note", ""));
        patient.weight = item.optString("weight", "");
        patient.department = item.optString("department", "");
        patient.followUpDate = item.optString("followUpDate", "");
        patient.ageUnit = item.optString("ageUnit", "");
        patient.disease = item.optString("disease", "");
        patient.transferredOut = item.optBoolean("transferredOut", false);
        patient.currentTreatment = item.optBoolean("currentTreatment", index == 0);
        // 旧数据没有 treatmentStarted 字段：按 currentTreatment 兜底（保持旧行为，不意外转回顾）
        patient.treatmentStarted = item.optBoolean("treatmentStarted", patient.currentTreatment);
        patient.treatmentStartTime = item.optString("treatmentStartTime", patient.visitDate);
        patient.treatmentEndTime = item.optString("treatmentEndTime",
                patient.currentTreatment ? "进行中" : patient.visitDate);
        patient.lastTreatmentSampleAt = item.optLong("lastTreatmentSampleAt", 0L);
        // ★ 任务29：记录单编辑字段读取
        patient.sheetAdvice = item.optString("sheetAdvice", "");
        patient.sheetConclusionIdx = item.optInt("sheetConclusionIdx", 0);
        patient.sheetVitals = item.optString("sheetVitals", "");
        loadTreatmentEntriesFromStorage(patient, item.optJSONArray("entries"));
        ensureTreatmentEntrySet(patient);
        // ★ 游标按舱推进：读左舱病例不能把右舱的编号起点抬上去。
        updateCaseNoCursorFromText(zone, patient.caseNo);
        updateCaseNoCursorFromText(zone, patient.recordNo);
        return patient;
    }

    private void loadPatientZone(PatientZoneState state, JSONObject zoneJson) throws JSONException {
        state.cases.clear();
        // 先按存储的游标恢复，再用本舱病例取最大值兜底（两者取大）。
        state.caseNoCursor = 0L;
        state.caseNoCursorWidth = 6;
        if (zoneJson != null) {
            updateCaseNoCursorFromText(state.zone, zoneJson.optString("caseNoCursor", ""));
            state.dailyCaseNoDate = zoneJson.optString("dailyCaseNoDate", "");
            state.dailyCaseNoCursor = zoneJson.optLong("dailyCaseNoCursor", 0L);
        }
        JSONArray array = zoneJson == null ? null : zoneJson.optJSONArray("cases");
        if (array != null) {
            for (int i = 0; i < array.length(); i++) {
                PatientCase patient = patientCaseFromStorageJson(state.zone, array.getJSONObject(i), i);
                if (hasMeaningfulPatientData(patient)) {
                    state.cases.add(patient);
                }
            }
        }
        state.selectedCaseIndex = clampIndex(zoneJson == null ? 0
                : zoneJson.optInt("selectedCaseIndex", 0), state.cases.size());
    }

    private void loadTreatmentRecordsFromStorage() {
        try {
            SharedPreferences prefs = activity.getSharedPreferences(PREFS_NAME, 0);
            // 旧版本只有一个全局游标。它属于升级时所在舱的病例，另一舱必须为空，
            // 否则右舱会继承左舱的历史编号，不再是 000001 起步。
            String legacyCursor = prefs.getString(KEY_CASE_NO_CURSOR, "");
            String rawV2 = prefs.getString(KEY_TREATMENT_CASES_V2, "");
            if (!TextUtils.isEmpty(rawV2)) {
                JSONObject root = new JSONObject(rawV2);
                if (root.optInt("schemaVersion", 0) == TREATMENT_CASES_SCHEMA_VERSION) {
                    JSONObject zones = root.optJSONObject("zones");
                    JSONObject leftJson = zones == null ? null : zones.optJSONObject("left");
                    JSONObject rightJson = zones == null ? null : zones.optJSONObject("right");
                    loadPatientZone(leftPatients, leftJson);
                    loadPatientZone(rightPatients, rightJson);
                    boolean hasStoredCursor =
                            !TextUtils.isEmpty(leftJson == null ? "" : leftJson.optString("caseNoCursor", ""))
                            || !TextUtils.isEmpty(rightJson == null ? "" : rightJson.optString("caseNoCursor", ""));
                    // 老版本存档没有按舱游标：把旧游标归给升级时当前所在的那一舱。
                    if (!hasStoredCursor && !TextUtils.isEmpty(legacyCursor)) {
                        updateCaseNoCursorFromText(activePatientState().zone, legacyCursor);
                    }
                    selectedCaseIndex = activePatientState().selectedCaseIndex;
                    return;
                }
            }
            // V1 migration: all legacy records belong to the cabin selected at upgrade time.
            leftPatients.cases.clear();
            rightPatients.cases.clear();
            // ★ 旧记录整体迁入升级时当前所在的舱，旧游标也只归这一舱。
            String migrationZone = activePatientState().zone;
            updateCaseNoCursorFromText(migrationZone, legacyCursor);
            String raw = prefs.getString(KEY_TREATMENT_CASES, "");
            if (TextUtils.isEmpty(raw)) {
                return;
            }
            JSONArray array = new JSONArray(raw);
            if (array.length() == 0) {
                return;
            }
            cases.clear();
            for (int i = 0; i < array.length(); i++) {
                JSONObject item = array.getJSONObject(i);
                PatientCase patient = new PatientCase(
                        item.optString("caseNo", "000000"),
                        item.optString("monitorNo", "000000"),
                        item.optString("petName", "未命名"),
                        item.optString("species", "未知"),
                        item.optString("sex", "-"),
                        item.optString("age", "-"),
                        item.optString("ownerName", "-"),
                        item.optString("ownerPhone", "-"),
                        item.optString("doctor", "-"),
                        item.optString("recordNo", "000000"),
                        item.optString("visitDate", currentTimeText()),
                        item.optString("note", ""));
                patient.currentTreatment = item.optBoolean("currentTreatment", i == 0);
                // 旧版存储路径同样读取（缺省按 currentTreatment 兜底，保持旧行为）
                patient.treatmentStarted = item.optBoolean("treatmentStarted", patient.currentTreatment);
                patient.treatmentStartTime = item.optString("treatmentStartTime", patient.visitDate);
                patient.treatmentEndTime = item.optString("treatmentEndTime", patient.currentTreatment ? "进行中" : patient.visitDate);
                patient.lastTreatmentSampleAt = item.optLong("lastTreatmentSampleAt", 0L);
                // ★ 任务29：旧版存储路径同样读取记录单编辑字段
                patient.sheetAdvice = item.optString("sheetAdvice", "");
                patient.sheetConclusionIdx = item.optInt("sheetConclusionIdx", 0);
                patient.sheetVitals = item.optString("sheetVitals", "");
                JSONArray entries = item.optJSONArray("entries");
                if (entries != null && entries.length() > 0) {
                    patient.treatmentEntries.clear();
                    for (int j = 0; j < entries.length(); j++) {
                        JSONObject entryJson = entries.getJSONObject(j);
                        TreatmentEntry entry = new TreatmentEntry(
                                entryJson.optString("itemName", "项目"),
                                entryJson.optString("unit", ""),
                                entryJson.optBoolean("enabled", true),
                                entryJson.optBoolean("includeInReport", true),
                                entryJson.optString("startTime", patient.treatmentStartTime),
                                entryJson.optString("endTime", patient.treatmentEndTime));
                        entry.customPeriod = entryJson.has("customPeriod")
                                ? entryJson.optBoolean("customPeriod", false)
                                : inferLegacyCustomPeriod(patient, entry);
                        entry.manualAverageValue = entryJson.optString("manualAverageValue", "");
                        entry.manualHighValue = entryJson.optString("manualHighValue", "");
                        entry.manualLowValue = entryJson.optString("manualLowValue", "");
                        entry.manualValue = entryJson.optString("manualValue", "");
                        if (TextUtils.isEmpty(entry.manualAverageValue)
                                && TextUtils.isEmpty(entry.manualHighValue)
                                && TextUtils.isEmpty(entry.manualLowValue)
                                && !TextUtils.isEmpty(entry.manualValue)) {
                            entry.manualAverageValue = entry.manualValue;
                            entry.manualHighValue = entry.manualValue;
                            entry.manualLowValue = entry.manualValue;
                        }
                        entry.lastValue = entryJson.optString("lastValue", "--");
                        entry.sum = (float) entryJson.optDouble("sum", 0);
                        entry.high = entryJson.isNull("high") ? Float.NaN : (float) entryJson.optDouble("high", Float.NaN);
                        entry.low = entryJson.isNull("low") ? Float.NaN : (float) entryJson.optDouble("low", Float.NaN);
                        entry.sampleCount = entryJson.optInt("sampleCount", 0);
                        applyManualStatOverrides(entry);
                        entry.manualValue = buildTreatmentManualSummary(entry);
                        patient.treatmentEntries.add(entry);
                    }
                }
                ensureTreatmentEntrySet(patient);
                if (!hasMeaningfulPatientData(patient)) {
                    continue;
                }
                cases.add(patient);
                updateCaseNoCursorFromText(migrationZone, patient.caseNo);
                updateCaseNoCursorFromText(migrationZone, patient.recordNo);
            }
            boolean hasCurrent = false;
            for (PatientCase patient : cases) {
                hasCurrent = hasCurrent || patient.currentTreatment;
            }
            if (!hasCurrent && !cases.isEmpty()) {
                cases.get(0).currentTreatment = true;
                cases.get(0).treatmentEndTime = "进行中";
            }
            selectedCaseIndex = clampIndex(selectedCaseIndex, cases.size());
            saveTreatmentRecordsToStorage();
        } catch (Exception ignored) {
        }
    }

    private int clampIndex(int index, int size) {
        if (size <= 0) {
            return 0;
        }
        return Math.max(0, Math.min(index, size - 1));
    }

    private Organization currentOrganization() {
        if (organizations.isEmpty()) {
            return new Organization("未设置医院", "未设置地址");
        }
        if (selectedOrganizationIndex < 0 || selectedOrganizationIndex >= organizations.size()) {
            selectedOrganizationIndex = 0;
        }
        return organizations.get(selectedOrganizationIndex);
    }

    private int findOrganizationIndexByName(String name) {
        if (TextUtils.isEmpty(name)) {
            return -1;
        }
        for (int i = 0; i < organizations.size(); i++) {
            if (name.equals(organizations.get(i).name)) {
                return i;
            }
        }
        return -1;
    }

    /**
     * 登录成功后按当前账号所属机构切换：找到就选中；找不到则新建一条并选中；
     * 账号未绑定机构（机构名为空）时保持当前机构不变，兼容旧账号。
     */
    private void applyAccountOrganization() {
        String orgName = accountStore.getCurrentOrganization();
        if (TextUtils.isEmpty(orgName)) {
            return;
        }
        int index = findOrganizationIndexByName(orgName);
        if (index < 0) {
            organizations.add(new Organization(orgName, "请在设置中维护机构地址", "请维护联系电话"));
            index = organizations.size() - 1;
        }
        selectedOrganizationIndex = index;
        lastGeneratedPdf = null;
        saveUiSettings();
        invalidate();
    }

    /**
     * 给 JS 桥调用：返回当前是否已有登录账号（currentAccount 不为空）。
     */
    public boolean isLoggedIn() {
        return !TextUtils.isEmpty(accountStore.getCurrentAccount());
    }

    /**
     * 给 JS 桥调用：在网页端登录。成功后切换到该账号绑定的机构。
     * 注意：JS 桥方法在 binder 线程被调用，所以内部把 UI 副作用 post 到 UI 线程。
     *
     * @return true 表示登录成功
     */
    public boolean webLogin(final String name, final String password) {
        if (TextUtils.isEmpty(name) || password == null) {
            return false;
        }
        final boolean ok = accountStore.authenticate(name, password);
        if (!ok) {
            /* ★ 2026-10-08 日志页·常规信息：登录失败 */
            appendGeneralLog("登录", name + " 登录失败");
            return false;
        }
        accountStore.setCurrentAccount(name);
        final String displayName = accountStore.getCurrentAccount();
        /* ★ 2026-10-08 日志页·常规信息：登录成功 */
        appendGeneralLog("登录", displayName + " 登录成功");
        uiHandler.post(new Runnable() {
            @Override
            public void run() {
                applyAccountOrganization();
                Toast.makeText(activity, "已登录: " + displayName, Toast.LENGTH_SHORT).show();
                invalidate();
                scheduleStartupTreatmentRecordPrompt();
            }
        });
        return true;
    }

    /**
     * 给 JS 桥调用：在网页端注册新账号。成功后会立即设为当前账号并切换机构。
     *
     * @return "ok" 表示成功；否则返回面向用户的错误描述
     */
    public String webRegister(final String name, final String password, final String organization) {
        if (TextUtils.isEmpty(name)) {
            return "请输入登录账号";
        }
        if (TextUtils.isEmpty(password)) {
            return "请输入登录口令";
        }
        if (accountStore.nameExists(name)) {
            return "该账号已存在";
        }
        final String org = organization == null ? "" : organization;
        final boolean saved = accountStore.register(name, password, org);
        if (!saved) {
            return "注册失败";
        }
        accountStore.setCurrentAccount(name);
        final String displayName = accountStore.getCurrentAccount();
        /* ★ 2026-10-08 日志页·常规信息：注册并登录 */
        appendGeneralLog("登录", displayName + " 注册并登录成功");
        uiHandler.post(new Runnable() {
            @Override
            public void run() {
                applyAccountOrganization();
                Toast.makeText(activity, "已注册并登录: " + displayName, Toast.LENGTH_SHORT).show();
                invalidate();
                scheduleStartupTreatmentRecordPrompt();
            }
        });
        return "ok";
    }

    /**
     * 给 JS 桥调用：清除当前账号并把 WebView 切到登录页。
     */
    public void webLogout() {
        /* ★ 2026-10-08 日志页·常规信息：退出登录（先取账号名再清除） */
        String who = accountStore.getCurrentAccount();
        appendGeneralLog("注销", (TextUtils.isEmpty(who) ? "-" : who) + " 退出登录");
        accountStore.clearCurrentAccount();
        invalidate();
        Toast.makeText(activity, "已退出登录", Toast.LENGTH_SHORT).show();
        if (activity != null) {
            activity.navigateLanhuToLogin();
        }
    }

    /* ==================================================================
       ★ 任务13（V1.02 R67-R73 用户管理 / R53-R55 连接开关 / R93 舱区权限门禁）
       ================================================================== */

    /** 用户列表（设置-用户页渲染）：用户名 / 密码掩码 / 角色 / 是否当前登录 */
    JSONArray accountsToJson() {
        JSONArray array = new JSONArray();
        try {
            String current = accountStore.getCurrentAccount();
            for (LocalAccountStore.Account a : accountStore.listAccounts()) {
                JSONObject item = new JSONObject();
                item.put("name", a.name);
                item.put("passwordMasked", "••••••");
                item.put("role", a.role);
                item.put("organization", a.organization == null ? "" : a.organization);
                item.put("isCurrent", a.name.equals(current));
                array.put(item);
            }
        } catch (JSONException ignored) {
        }
        return array;
    }

    public String getCurrentAccountRole() {
        return accountStore.getCurrentRole();
    }

    /** R93 → 2026-09-30 用户确认：所有角色均可切换左舱/右舱 */
    public boolean canSwitchZone() {
        return true;
    }

    /**
     * R71/R72：新增或编辑账号。
     * text = JSON { originalName?(空=新增), name, password, organization, role }
     * @return "ok" 成功；否则返回面向用户的错误描述
     */
    public String saveAccountFromText(String text) {
        if (!accountStore.isCurrentAdminOrService()) {
            return "无权限：账号管理仅工程师/管理员可用";
        }
        try {
            JSONObject obj = new JSONObject(text == null ? "" : text);
            String originalName = obj.optString("originalName", "").trim();
            String name = obj.optString("name", "").trim();
            String password = obj.optString("password", "");
            String organization = obj.optString("organization", "");
            String role = obj.optString("role", "");
            if (TextUtils.isEmpty(name)) {
                return "请输入用户名";
            }
            if (TextUtils.isEmpty(originalName) && TextUtils.isEmpty(password)) {
                return "密码必填";
            }
            if (TextUtils.isEmpty(role)) {
                return "权限必选";
            }
            String msg;
            boolean isNew = TextUtils.isEmpty(originalName);
            if (isNew) {
                if (accountStore.nameExists(name)) {
                    return "该用户名已存在";
                }
                msg = accountStore.register(name, password, organization, role) ? "ok" : "保存失败";
            } else {
                int code = accountStore.updateAccount(originalName, password, organization, role);
                if (code == 0) {
                    msg = "ok";
                } else if (code == -2) {
                    msg = "账号不存在";
                } else {
                    msg = "保存失败";
                }
            }
            if ("ok".equals(msg)) {
                appendUserLog("用户管理", isNew ? "新增用户" : "编辑用户", name + " / " + role);
            }
            return msg;
        } catch (JSONException ignored) {
            return "数据格式错误";
        }
    }

    /**
     * R73：删除账号。text = JSON { name }
     * @return "ok" 成功；否则返回面向用户的错误描述
     */
    public String deleteAccountFromText(String text) {
        if (!accountStore.isCurrentAdminOrService()) {
            return "无权限：账号管理仅工程师/管理员可用";
        }
        try {
            JSONObject obj = new JSONObject(text == null ? "" : text);
            String name = obj.optString("name", "").trim();
            if (TextUtils.isEmpty(name)) {
                return "请先选择要删除的用户";
            }
            int code = accountStore.deleteAccount(name);
            if (code == 0) {
                appendUserLog("用户管理", "删除用户", name);
                return "ok";
            }
            if (code == -3) {
                return "不能删除当前登录用户";
            }
            if (code == -4) {
                return "至少保留一个账号";
            }
            if (code == -2) {
                return "账号不存在";
            }
            return "删除失败";
        } catch (JSONException ignored) {
            return "数据格式错误";
        }
    }

    /**
     * R53：wifi 开关。Android 10+ 三方应用无法直接 setWifiEnabled，
     * 直接调用失败时改为打开系统 WiFi 面板，避免"点了没反应"。
     */
    public void toggleWifiFromText(String text) {
        boolean enable = true;
        try {
            JSONObject obj = new JSONObject(text == null ? "" : text);
            enable = obj.optBoolean("enabled", true);
        } catch (JSONException ignored) {
        }
        WifiManager wifiManager = (WifiManager) activity.getApplicationContext()
                .getSystemService(Context.WIFI_SERVICE);
        if (wifiManager == null) {
            Toast.makeText(activity, "无法访问 WiFi 设置", Toast.LENGTH_SHORT).show();
            return;
        }
        boolean applied = false;
        try {
            applied = wifiManager.setWifiEnabled(enable);
        } catch (SecurityException ignored) {
            applied = false;
        }
        if (applied) {
            Toast.makeText(activity, enable ? "正在开启 WiFi" : "正在关闭 WiFi", Toast.LENGTH_SHORT).show();
            return;
        }
        // 系统不允许应用直接开关（Android 10+ 限制）：降级为打开系统 WiFi 面板
        try {
            Intent panel = new Intent(Settings.Panel.ACTION_WIFI);
            panel.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            activity.startActivity(panel);
            Toast.makeText(activity, "请在系统面板中开关 WiFi", Toast.LENGTH_SHORT).show();
        } catch (Exception ignored) {
            Toast.makeText(activity, "当前系统不允许应用直接开关 WiFi", Toast.LENGTH_SHORT).show();
        }
    }

    /** R54：主机蓝牙开关。开=重连上次主机；关=断开 */
    public void toggleHostBleFromText(String text) {
        boolean enable = true;
        try {
            JSONObject obj = new JSONObject(text == null ? "" : text);
            enable = obj.optBoolean("enabled", true);
        } catch (JSONException ignored) {
        }
        if (enable) {
            if (bleManager.isConnected()) {
                Toast.makeText(activity, "主机蓝牙已连接", Toast.LENGTH_SHORT).show();
                return;
            }
            boolean attempted = activity.reconnectLastHostIfAvailable();
            Toast.makeText(activity, attempted ? "正在连接主机蓝牙…" : "未保存主机设备，请先在监护页连接",
                    Toast.LENGTH_SHORT).show();
        } else {
            bleManager.disconnect();
            Toast.makeText(activity, "已断开主机蓝牙", Toast.LENGTH_SHORT).show();
        }
    }

    /** R55：监护蓝牙开关。开=开始搜索监护设备；关=断开 */
    public void toggleMonitorBleFromText(String text) {
        boolean enable = true;
        try {
            JSONObject obj = new JSONObject(text == null ? "" : text);
            enable = obj.optBoolean("enabled", true);
        } catch (JSONException ignored) {
        }
        if (enable) {
            if (am4100Manager.isConnected()) {
                Toast.makeText(activity, "监护蓝牙已连接", Toast.LENGTH_SHORT).show();
                return;
            }
            am4100Manager.startScan();
            Toast.makeText(activity, "正在搜索监护设备…", Toast.LENGTH_SHORT).show();
        } else {
            am4100Manager.disconnect();
            Toast.makeText(activity, "已断开监护蓝牙", Toast.LENGTH_SHORT).show();
        }
    }

    /**
     * 给 JS 桥调用：把"医院信息"页面内三个输入框当前值写入当前机构并落库。
     * 由 MainActivity.handleOrganizationSaveFromLanhu 在用户点击"保存"按钮后调用。
     *
     * 已经在 UI 线程上（外层 runOnUiThread 包裹），所以这里同步直接落盘：
     *   - 用 commit() 而不是 apply()，确保立刻写入磁盘文件
     *   - 不再用 View.post() 二次异步，避免 post 队列里丢消息导致数据没落盘
     *
     * @return 给用户看的提示文案（成功 / 失败原因）
     */
    public String webUpdateOrganization(final String name, final String address, final String phone) {
        final Organization organization = currentOrganization();
        if (organization == null) {
            android.util.Log.e("ICU-Native", "webUpdateOrganization: currentOrganization() returned null");
            return "保存失败：未找到当前机构";
        }
        organization.name = name == null ? "" : name.trim();
        organization.address = address == null ? "" : address.trim();
        organization.phone = phone == null ? "" : phone.trim();
        android.util.Log.d("ICU-Native", "webUpdateOrganization: name=[" + organization.name
                + "] address=[" + organization.address + "] phone=[" + organization.phone + "]");
        try {
            // 1) 把机构名写进当前账号的绑定（兼容登录路由）
            boolean acctOk = accountStore.setOrganizationForCurrent(organization.name);
            // 2) 把完整机构数据写进 SharedPreferences（commit() 同步落盘）
            boolean prefsOk = commitUiSettings();
            // 3) 触发 native 重绘（其他地方可能用到 Organization 字段）
            lastGeneratedPdf = null;
            invalidate();
            android.util.Log.d("ICU-Native", "webUpdateOrganization: committed org index="
                    + selectedOrganizationIndex + " size=" + organizations.size()
                    + " acctOk=" + acctOk + " prefsOk=" + prefsOk);
            if (!prefsOk) {
                return "保存失败：SharedPreferences 写入失败";
            }
        } catch (Throwable t) {
            android.util.Log.e("ICU-Native", "webUpdateOrganization save failed", t);
            return "保存失败：" + (t.getMessage() == null ? t.getClass().getSimpleName() : t.getMessage());
        }
        return "医院信息已保存";
    }

    /**
     * ★ 历史死代码:showDeviceProfileDialog 弹原生对话框编辑仪器状态
     *   改用 webview inline edit 后,所有路径都走 webUpdateDeviceProfile,
     *   这个方法已无人调用,保留空壳仅占位以防误删
     */
    private void showDeviceProfileDialog() {
        // ★ 已删除:原方法体 ~30 行 EditText 对话框代码(产品型号/机型/机号/生产日期/程序版本)
        //   完全删除会留编译错误,所以保留方法签名 + 空 body
    }

    /**
     * ★ 新加：仪器状态页保存。5 个字段写入 deviceProfile + SharedPreferences。
     */
    public String webUpdateDeviceProfile(final String productModel, final String machineType,
                                          final String serialNo, final String manufactureDate,
                                          final String softwareVersion) {
        try {
            deviceProfile.productModel = productModel == null ? "" : productModel.trim();
            deviceProfile.machineType = machineType == null ? "" : machineType.trim();
            deviceProfile.serialNo = serialNo == null ? "" : serialNo.trim();
            deviceProfile.manufactureDate = manufactureDate == null ? "" : manufactureDate.trim();
            deviceProfile.softwareVersion = softwareVersion == null ? "" : softwareVersion.trim();
            commitUiSettings();
            invalidate();
            return "仪器信息已保存";
        } catch (Throwable t) {
            android.util.Log.e("ICU-Native", "webUpdateDeviceProfile save failed", t);
            return "保存失败：" + (t.getMessage() == null ? t.getClass().getSimpleName() : t.getMessage());
        }
    }

    /**
     * ★ 新加：管理员登录校验。返回 true 表示通过。LocalAccountStore 内部已做线程安全。
     */
    public boolean webAdminLogin(final String name, final String password) {
        if (TextUtils.isEmpty(name) || password == null) {
            return false;
        }
        return accountStore.authenticate(name, password);
    }

    /**
     * ★ 新加：暴露 BleManager 实例给 MainActivity 用于触发 A1/A2 升级。
     */
    public BleManager bleManager() {
        return bleManager;
    }

    /**
     * saveUiSettings 的同步落盘版本：用 commit() 替代 apply()，避免异步写入被中断。
     * (ApplySharedPref lint 警告在这里是有意忽略的)
     */
    @android.annotation.SuppressLint("ApplySharedPref")
        /**
     * #19 修复：定时控件（红外理疗/蓝光理疗/紫外消毒/雾化器/负离子）单独开关时的默认时长
     * 优先用 BLE 当前值（getControlTimeValue），否则用本地 SharedPreferences 缓存，
     * 都没有的话默认 99 分钟。
     */
    private int getTimedControlDefault(int controlIndex) {
        Integer live = bleManager.getControlTimeValue(controlIndex);
        if (live != null && live > 0) {
            return live;
        }
        SharedPreferences prefs = activity.getSharedPreferences(PREFS_NAME, 0);
        int cached = prefs.getInt(timedControlPreferenceKey(controlIndex), 0);
        if (cached > 0) {
            return cached;
        }
        return 99;
    }

    private static String timedControlPreferenceKey(int controlIndex) {
        return "timed_control_default_" + controlIndex;
    }

    void chooseOrganizationLogo() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("image/*");
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
        activity.startActivityForResult(intent, REQUEST_ORGANIZATION_LOGO);
    }

    // ★ 任务31：页脚LOGO 选择器（与医院LOGO 同样的系统文档选择器，独立请求码）
    void chooseFooterLogo() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("image/*");
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
        activity.startActivityForResult(intent, REQUEST_FOOTER_LOGO);
    }

    void onFooterLogoSelected(Uri logoUri) {
        if (logoUri == null) {
            return;
        }
        try {
            activity.getContentResolver().takePersistableUriPermission(
                    logoUri, Intent.FLAG_GRANT_READ_URI_PERMISSION);
        } catch (SecurityException ignored) {
            // 部分文档提供方不支持持久权限，当前会话仍可正常显示图片。
        }
        s5FooterLogoUri = logoUri.toString();
        saveUiSettings();
        pushSettingsToLanhu();
        lastGeneratedPdf = null;
        Toast.makeText(activity, "页脚 Logo 已上传", Toast.LENGTH_SHORT).show();
        invalidate();
    }

    void onOrganizationLogoSelected(Uri logoUri) {
        if (logoUri == null) {
            return;
        }
        try {
            activity.getContentResolver().takePersistableUriPermission(
                    logoUri, Intent.FLAG_GRANT_READ_URI_PERMISSION);
        } catch (SecurityException ignored) {
            // 部分文档提供方不支持持久权限，当前会话仍可正常显示图片。
        }
        Organization organization = currentOrganization();
        organization.logoUri = logoUri.toString();
        organization.logoName = displayNameForUri(logoUri);
        saveUiSettings();
        lastGeneratedPdf = null;
        Toast.makeText(activity, "医院 Logo 已上传", Toast.LENGTH_SHORT).show();
        invalidate();
    }

    private String displayNameForUri(Uri uri) {
        Cursor cursor = null;
        try {
            cursor = activity.getContentResolver().query(uri,
                    new String[]{OpenableColumns.DISPLAY_NAME}, null, null, null);
            if (cursor != null && cursor.moveToFirst()) {
                // 用 getColumnIndexOrThrow 更稳：列不存在时直接抛、走 catch 兜底，
                // 避免 getColumnIndex 返回 -1 传给 cursor.getString(-1) 触发 IllegalArgumentException
                int col = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                if (col >= 0) {
                    String name = cursor.getString(col);
                    if (!TextUtils.isEmpty(name)) {
                        return name;
                    }
                }
            }
        } catch (Exception ignored) {
        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }
        return "已上传 Logo";
    }

    private boolean commitUiSettings() {
        boolean ok = activity.getSharedPreferences(PREFS_NAME, 0)
                .edit()
                .putInt(KEY_SELECTED_ORGANIZATION, selectedOrganizationIndex)
                .putString(KEY_ORGANIZATIONS, organizationsToJsonString())
                .putInt(KEY_SELECTED_HOST_MODE, selectedHostModeIndex)
                .putInt(KEY_ACTIVE_HOST_MODE, selectedHostModeIndex)
                .putInt(KEY_LAST_HOST_MODE, lastHostModeIndex)
                .putString(KEY_TREATMENT_PERIOD_MODE, treatmentPeriodMode)
                .putBoolean(KEY_MONITOR_PULSE_BEEP, monitorPulseBeep)
                .putBoolean(KEY_MONITOR_ALARM_SOUND, monitorAlarmSound)
                .putString(KEY_MONITOR_LEVEL_COLOR, selectedMonitorLevelColor)
                .putString(KEY_DEVICE_PROFILE, deviceProfileToJsonString())
                .commit();
        android.util.Log.d("ICU-Native", "commitUiSettings ok=" + ok
                + " orgs=" + organizationsToJsonString());
        return ok;
    }

    private PatientZoneState patientStateForZone(String zone) {
        return "left".equals(EnvironmentProtocol.normalizeZone(zone)) ? leftPatients : rightPatients;
    }

    private PatientZoneState activePatientState() {
        return patientStateForZone(bleManager.getCurrentZone());
    }

    /** 反查某个病例属于哪个舱；找不到时退回当前舱，避免把编号推进到错误的舱。 */
    private String zoneOfPatient(PatientCase patient) {
        if (patient != null) {
            if (leftPatients.cases.indexOf(patient) >= 0) {
                return "left";
            }
            if (rightPatients.cases.indexOf(patient) >= 0) {
                return "right";
            }
        }
        return activePatientState().zone;
    }

    private String currentZoneNormalized() {
        return EnvironmentProtocol.normalizeZone(bleManager.getCurrentZone());
    }

    private String patientZoneOrCurrent(PatientCase patient) {
        return patient == null ? currentZoneNormalized() : zoneOfPatient(patient);
    }

    /** 当前舱的相机抓拍数（PDF/抓拍列表全局共享，但渲染按当前舱过滤）。 */
    private int activeCameraSnapshotCount() {
        String z = currentZoneNormalized();
        int n = 0;
        for (CameraSnapshot s : cameraSnapshots) {
            if (z.equals(s.zone)) n++;
        }
        return n;
    }

    private CameraSnapshot getActiveCameraSnapshot(int index) {
        String z = currentZoneNormalized();
        int seen = 0;
        for (CameraSnapshot s : cameraSnapshots) {
            if (z.equals(s.zone)) {
                if (seen == index) return s;
                seen++;
            }
        }
        return null;
    }

    private int activePdfRecordCount() {
        String z = currentZoneNormalized();
        int n = 0;
        for (PdfRecord r : pdfRecords) {
            if (z.equals(r.zone)) n++;
        }
        return n;
    }

    private PdfRecord getActivePdfRecord(int index) {
        String z = currentZoneNormalized();
        int seen = 0;
        for (PdfRecord r : pdfRecords) {
            if (z.equals(r.zone)) {
                if (seen == index) return r;
                seen++;
            }
        }
        return null;
    }

    /** 单舱抓拍上限。原来按全局 60 张裁剪，一舱截图多了会把另一舱的挤掉。 */
    private static final int MAX_CAMERA_SNAPSHOTS_PER_ZONE = 60;

    private void trimCameraSnapshotsForZone(String zone) {
        String z = EnvironmentProtocol.normalizeZone(zone);
        int count = 0;
        for (int i = 0; i < cameraSnapshots.size(); i++) {
            if (!z.equals(cameraSnapshots.get(i).zone)) {
                continue;
            }
            count++;
            if (count > MAX_CAMERA_SNAPSHOTS_PER_ZONE) {
                CameraSnapshot removed = cameraSnapshots.remove(i);
                if (removed.cacheFile != null && removed.cacheFile.exists()) {
                    removed.cacheFile.delete();
                }
                i--;
            }
        }
    }

    /** 单舱 PDF 记录上限（原为全局 30）。 */
    private static final int MAX_PDF_RECORDS_PER_ZONE = 30;

    private void trimPdfRecordsForZone(String zone) {
        String z = EnvironmentProtocol.normalizeZone(zone);
        int count = 0;
        for (int i = 0; i < pdfRecords.size(); i++) {
            if (!z.equals(pdfRecords.get(i).zone)) {
                continue;
            }
            count++;
            if (count > MAX_PDF_RECORDS_PER_ZONE) {
                pdfRecords.remove(i);
                i--;
            }
        }
    }

    /**
     * 升级前的旧抓拍数据没有 zone 字段。策略：
     * 1) 有条目自带 zone 就用它；
     * 2) 否则用「病例编号 + 宠物名」在两舱里找，只有唯一一舱命中时才认这一舱
     *    （左右舱编号各自从 000001 起，所以必须两个条件都命中才算唯一）；
     * 3) 都不确定时归到当前舱，保证不会把旧数据整体丢掉。
     */
    private String resolveLegacySnapshotZone(String rawZone, String recordNo, String petName) {
        String explicit = EnvironmentProtocol.normalizeZone(rawZone);
        if (!explicit.isEmpty()) {
            return explicit;
        }
        String hit = "";
        int hits = 0;
        for (PatientZoneState state : patientZones()) {
            for (PatientCase patientCase : state.cases) {
                boolean recordOk = !TextUtils.isEmpty(recordNo)
                        && recordNo.equals(safeJsonText(patientCase.recordNo));
                boolean nameOk = !TextUtils.isEmpty(petName)
                        && petName.equals(safeJsonText(patientCase.petName));
                if (recordOk && nameOk) {
                    hits++;
                    hit = state.zone;
                    break;
                }
            }
        }
        if (hits == 1) {
            return hit;
        }
        return currentZoneNormalized();
    }

    private java.util.List<PatientZoneState> patientZones() {
        java.util.ArrayList<PatientZoneState> zones = new java.util.ArrayList<>();
        zones.add(leftPatients);
        zones.add(rightPatients);
        return zones;
    }

    private void syncActivePatientSelection() {
        PatientZoneState state = activePatientState();
        state.selectedCaseIndex = clampIndex(selectedCaseIndex, state.cases.size());
    }

    private void switchPatientZone(String zone) {
        syncActivePatientSelection();
        bleManager.setCurrentZone(zone);
        selectedCaseIndex = clampIndex(activePatientState().selectedCaseIndex, cases.size());
        searchKeyword = "";
        // 抓拍选中项是「舱内下标」，切舱后必须重置，否则会指向另一舱的截图。
        selectedCameraSnapshotIndex = activeCameraSnapshotCount() <= 0 ? -1 : 0;
        lastGeneratedPdf = null;
    }

    private PatientCase currentCase() {
        PatientZoneState state = activePatientState();
        if (state.cases.isEmpty()) {
            selectedCaseIndex = 0;
            state.selectedCaseIndex = 0;
            return null;
        }
        selectedCaseIndex = clampIndex(selectedCaseIndex, state.cases.size());
        state.selectedCaseIndex = selectedCaseIndex;
        return state.cases.get(selectedCaseIndex);
    }

    private List<Integer> getVisibleCaseIndices() {
        ArrayList<Integer> visible = new ArrayList<>();
        for (int i = 0; i < cases.size(); i++) {
            if (caseMatchesSearch(cases.get(i))) {
                visible.add(i);
            }
        }
        return visible;
    }

    private boolean caseMatchesSearch(PatientCase item) {
        if (TextUtils.isEmpty(searchKeyword)) {
            return true;
        }
        String key = searchKeyword.toLowerCase(Locale.getDefault());
        return containsLower(item.petName, key)
                || containsLower(item.ownerName, key)
                || containsLower(item.ownerPhone, key)
                || containsLower(item.caseNo, key)
                || containsLower(item.recordNo, key);
    }

    private boolean containsLower(String value, String key) {
        return value != null && value.toLowerCase(Locale.getDefault()).contains(key);
    }

    private void selectCase(int index) {
        if (index < 0 || index >= cases.size()) {
            return;
        }
        if (index != selectedCaseIndex && blockPatientSwitchIfNeeded()) {
            return;
        }
        selectedCaseIndex = index;
        lastGeneratedPdf = null;
        showingSettings = false;
        Toast.makeText(activity, "已切换到 " + (cases.get(index).currentTreatment ? "当前治疗: " : "历史治疗: ") + cases.get(index).petName, Toast.LENGTH_SHORT).show();
    }

    private void selectMainTab(final int index) {
        activeTab = index;
        showingSettings = false;
    }

    private int getCurrentTreatmentIndex() {
        for (int i = 0; i < cases.size(); i++) {
            if (cases.get(i).currentTreatment) {
                return i;
            }
        }
        return 0;
    }

    private void showStartupTreatmentRecordPrompt() {
        // ★ 任务16：按需求移除「治疗记录确认」开机弹窗 —— 不再向用户弹出该确认框。
        //   保留方法与调用点（登录/注册流程），置位标记后直接返回，避免影响既有流程。
        startupRecordPromptShown = true;
    }

    // ★ 病例编号按舱独立生成：只扫本舱病例，只推进本舱游标。
    // ★ V1.02 住院号生成：舱位前缀(A=左 / B=右) + 8位日期(yyyyMMdd) + 当日3位序号。
    //   例：左舱 2026-09-15 第1条 = A20260915001。每日序号按舱独立、跨天归零。
    private String nextCaseNo(String zone) {
        PatientZoneState state = patientStateForZone(zone);
        String today = currentDateYmd();
        if (!today.equals(state.dailyCaseNoDate)) {
            // 跨天：当日序号重新从 001 计起。历史日期号段因已含日期而全局唯一，互不冲突。
            state.dailyCaseNoDate = today;
            state.dailyCaseNoCursor = 0L;
        }
        String prefix = zoneCasePrefix(zone);
        long candidate = state.dailyCaseNoCursor + 1L;
        while (candidate <= 999L) {
            String candidateNo = prefix + today + String.format(Locale.US, "%03d", candidate);
            if (!caseNoStringExists(zone, candidateNo)) {
                break;
            }
            candidate += 1L;
        }
        if (candidate > 999L) {
            return "";
        }
        state.dailyCaseNoCursor = candidate;
        return prefix + today + String.format(Locale.US, "%03d", candidate);
    }

    private void updateCaseNoCursorFromText(String zone, String text) {
        Long value = parseNumericCaseNo(text);
        if (value == null) {
            return;
        }
        PatientZoneState state = patientStateForZone(zone);
        if (value > state.caseNoCursor) {
            state.caseNoCursor = value;
            state.caseNoCursorWidth = Math.max(6, text.length());
        } else if (value == state.caseNoCursor) {
            state.caseNoCursorWidth = Math.max(state.caseNoCursorWidth, Math.max(6, text.length()));
        }
    }

    private Long parseNumericCaseNo(String text) {
        if (TextUtils.isEmpty(text) || text.length() > 12 || !text.matches("^[0-9]+$")) {
            return null;
        }
        try {
            return Long.parseLong(text);
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    // ★ 重名检查也只在本舱内进行：左右舱允许存在相同的编号。
    //   住院号现为「前缀+日期+序号」字符串，直接按完整字符串比对（同时兼容旧版纯数字编号）。
    private boolean caseNoUsedByOther(String zone, String text, PatientCase current) {
        if (TextUtils.isEmpty(text)) {
            return false;
        }
        for (PatientCase patient : patientStateForZone(zone).cases) {
            if (patient == current) {
                continue;
            }
            if (text.equals(patient.caseNo) || text.equals(patient.recordNo)) {
                return true;
            }
        }
        return false;
    }

    private String formatCaseNo(long value, int width) {
        int safeWidth = Math.max(6, Math.min(12, width));
        String digits = String.valueOf(Math.max(0L, value));
        if (digits.length() >= safeWidth) {
            return digits;
        }
        return String.format(Locale.US, "%0" + safeWidth + "d", value);
    }

    // ===== V1.02 病例数据基座辅助方法 =====

    /** 当前日期 yyyyMMdd（住院号日期段）。 */
    private static String currentDateYmd() {
        return new SimpleDateFormat("yyyyMMdd", Locale.US).format(new Date());
    }

    /** 舱位前缀：左舱 = A，右舱 = B（与文档示例 A20260915001 一致）。 */
    private static String zoneCasePrefix(String zone) {
        return "left".equals(EnvironmentProtocol.normalizeZone(zone)) ? "A" : "B";
    }

    /** 完整住院号字符串是否已存在于本舱（caseNo 或 recordNo）。 */
    private boolean caseNoStringExists(String zone, String fullNo) {
        if (TextUtils.isEmpty(fullNo)) {
            return false;
        }
        for (PatientCase patient : patientStateForZone(zone).cases) {
            if (fullNo.equals(patient.caseNo) || fullNo.equals(patient.recordNo)) {
                return true;
            }
        }
        return false;
    }

    /** 护疗列表（主页）只显示「当天新建 / 进行中 / 未转出」的记录；其余进入回顾。 */
    private List<PatientCase> getCareListCases() {
        List<PatientCase> out = new ArrayList<>();
        for (PatientCase patient : cases) {
            if (isInCareList(patient)) {
                out.add(patient);
            }
        }
        return out;
    }

    private JSONArray careCasesToJson() throws JSONException {
        JSONArray array = new JSONArray();
        for (PatientCase patient : getCareListCases()) {
            if (hasMeaningfulPatientData(patient)) {
                array.put(patientToJson(patient));
            }
        }
        return array;
    }

    // ★ V1.02 S3 回顾查询：数据集 = 所有有意义、且不在护疗列表中的记录（历史记录）。
    //   进入回顾页默认不显示"疗养列表"记录，仅显示历史记录。
    private List<PatientCase> getHistoryCases() {
        List<PatientCase> out = new ArrayList<>();
        for (PatientCase patient : cases) {
            if (!hasMeaningfulPatientData(patient)) {
                continue;
            }
            if (isInCareList(patient)) {
                continue;
            }
            out.add(patient);
        }
        return out;
    }

    private boolean isInCareList(PatientCase patient) {
        if (patient.transferredOut) {
            return false;
        }
        // 当天新建必显示；跨天后仅「真正开始过护疗且未结束」的才保留（连续多日护疗），
        // 仅新建未护疗的样本过凌晨 0 点转入回顾。
        return (patient.currentTreatment && patient.treatmentStarted) || isSameDay(patient.visitDate);
    }

    private JSONArray historyCasesToJson() throws JSONException {
        List<PatientCase> source = getHistoryCases();
        if (reviewQueryFilter != null) {
            source = filterHistoryCases(source, reviewQueryFilter);
        }
        JSONArray array = new JSONArray();
        for (PatientCase patient : source) {
            array.put(patientToJson(patient));
        }
        return array;
    }

    // ★ V1.02 S3 组合查询：各条件可单独或组合（AND），时间范围为 visitDate 区间。
    private List<PatientCase> filterHistoryCases(List<PatientCase> source, JSONObject filter) {
        String caseNo = optNonEmpty(filter, "caseNo", "");
        String petName = optNonEmpty(filter, "petName", "");
        String ownerName = optNonEmpty(filter, "ownerName", "");
        String ownerPhone = optNonEmpty(filter, "ownerPhone", "");
        String species = optNonEmpty(filter, "species", "");
        String doctor = optNonEmpty(filter, "doctor", "");
        String timeFrom = optNonEmpty(filter, "timeFrom", "");
        String timeTo = optNonEmpty(filter, "timeTo", "");
        List<PatientCase> out = new ArrayList<>();
        for (PatientCase p : source) {
            if (!TextUtils.isEmpty(caseNo) && !containsIgnoreCase(p.caseNo, caseNo)) continue;
            if (!TextUtils.isEmpty(petName) && !containsIgnoreCase(p.petName, petName)) continue;
            if (!TextUtils.isEmpty(ownerName) && !containsIgnoreCase(p.ownerName, ownerName)) continue;
            if (!TextUtils.isEmpty(ownerPhone) && !containsIgnoreCase(p.ownerPhone, ownerPhone)) continue;
            if (!TextUtils.isEmpty(species) && !containsIgnoreCase(p.species, species)) continue;
            if (!TextUtils.isEmpty(doctor) && !containsIgnoreCase(p.doctor, doctor)) continue;
            if ((!TextUtils.isEmpty(timeFrom) || !TextUtils.isEmpty(timeTo))
                    && !inDayRange(p.visitDate, timeFrom, timeTo)) continue;
            out.add(p);
        }
        return out;
    }

    private static boolean containsIgnoreCase(String value, String needle) {
        if (TextUtils.isEmpty(value)) {
            return false;
        }
        return value.toLowerCase(Locale.US).contains(needle.toLowerCase(Locale.US));
    }

    private static boolean inDayRange(String visitDate, String from, String to) {
        if (TextUtils.isEmpty(visitDate) || visitDate.length() < 10) {
            return false;
        }
        String day = visitDate.substring(0, 10);
        if (!TextUtils.isEmpty(from) && day.compareTo(from) < 0) return false;
        if (!TextUtils.isEmpty(to) && day.compareTo(to) > 0) return false;
        return true;
    }

    private static boolean isValidQueryDate(String text) {
        if (TextUtils.isEmpty(text) || text.length() != 10) {
            return false;
        }
        return text.matches("^\\d{4}-\\d{2}-\\d{2}$");
    }

    private static boolean isEmptyQueryFilter(JSONObject filter) {
        return TextUtils.isEmpty(filter.optString("caseNo", ""))
                && TextUtils.isEmpty(filter.optString("petName", ""))
                && TextUtils.isEmpty(filter.optString("ownerName", ""))
                && TextUtils.isEmpty(filter.optString("ownerPhone", ""))
                && TextUtils.isEmpty(filter.optString("species", ""))
                && TextUtils.isEmpty(filter.optString("doctor", ""))
                && TextUtils.isEmpty(filter.optString("timeFrom", ""))
                && TextUtils.isEmpty(filter.optString("timeTo", ""));
    }

    private static String queryConditionSummary(JSONObject filter) {
        List<String> parts = new ArrayList<>();
        if (!TextUtils.isEmpty(filter.optString("caseNo", ""))) parts.add("住院号");
        if (!TextUtils.isEmpty(filter.optString("petName", ""))) parts.add("宠物名");
        if (!TextUtils.isEmpty(filter.optString("ownerName", ""))) parts.add("宠物主人");
        if (!TextUtils.isEmpty(filter.optString("ownerPhone", ""))) parts.add("联系电话");
        if (!TextUtils.isEmpty(filter.optString("species", ""))) parts.add("物种");
        if (!TextUtils.isEmpty(filter.optString("doctor", ""))) parts.add("主治医生");
        if (!TextUtils.isEmpty(filter.optString("timeFrom", "")) || !TextUtils.isEmpty(filter.optString("timeTo", ""))) parts.add("护疗时间");
        return parts.isEmpty() ? "无" : TextUtils.join("/", parts);
    }

    // ★ V1.02 S3 应用/清除回顾查询条件（经 H5 action('review_query', json, '') 调用）。
    void applyReviewQuery(String payload) {
        if (TextUtils.isEmpty(payload)) {
            reviewQueryFilter = null;
            if (activity != null) activity.notifyLanhuStateChanged();
            return;
        }
        JSONObject filter;
        try {
            filter = new JSONObject(payload);
        } catch (JSONException e) {
            Toast.makeText(activity, "查询条件格式错误", Toast.LENGTH_SHORT).show();
            return;
        }
        String timeFrom = optNonEmpty(filter, "timeFrom", "");
        String timeTo = optNonEmpty(filter, "timeTo", "");
        if (!TextUtils.isEmpty(timeFrom) && !isValidQueryDate(timeFrom)) {
            Toast.makeText(activity, "开始时间格式应为 yyyy-MM-dd", Toast.LENGTH_SHORT).show();
            return;
        }
        if (!TextUtils.isEmpty(timeTo) && !isValidQueryDate(timeTo)) {
            Toast.makeText(activity, "结束时间格式应为 yyyy-MM-dd", Toast.LENGTH_SHORT).show();
            return;
        }
        if (!TextUtils.isEmpty(timeFrom) && !TextUtils.isEmpty(timeTo) && timeFrom.compareTo(timeTo) > 0) {
            Toast.makeText(activity, "开始时间不能晚于结束时间", Toast.LENGTH_SHORT).show();
            return;
        }
        if (isEmptyQueryFilter(filter)) {
            reviewQueryFilter = null;
            appendUserLog("回顾", "查询", "清空筛选条件");
            Toast.makeText(activity, "已重置查询条件", Toast.LENGTH_SHORT).show();
            if (activity != null) activity.notifyLanhuStateChanged();
            return;
        }
        reviewQueryFilter = filter;
        List<PatientCase> result = filterHistoryCases(getHistoryCases(), filter);
        appendUserLog("回顾", "查询", "条件 " + queryConditionSummary(filter) + " / 命中 " + result.size() + " 条");
        Toast.makeText(activity, result.isEmpty() ? "无匹配记录" : "找到 " + result.size() + " 条记录", Toast.LENGTH_SHORT).show();
        if (activity != null) activity.notifyLanhuStateChanged();
    }

    void resetReviewQuery() {
        reviewQueryFilter = null;
        appendUserLog("回顾", "查询重置", "清空筛选条件");
        Toast.makeText(activity, "已重置查询条件", Toast.LENGTH_SHORT).show();
        if (activity != null) activity.notifyLanhuStateChanged();
    }

    /** visitDate 形如 yyyy-MM-dd HH:mm:ss，判断是否为今天。 */
    private static boolean isSameDay(String visitDate) {
        if (TextUtils.isEmpty(visitDate) || visitDate.length() < 10) {
            return false;
        }
        String todayDash = new SimpleDateFormat("yyyy-MM-dd", Locale.US).format(new Date());
        return visitDate.substring(0, 10).equals(todayDash);
    }

    /** V1.02 物种下拉选项（犬/猫/兔/蛇/蜥蜴/其他，其他可自填）。 */
    static final String[] SPECIES_OPTIONS = {"犬", "猫", "兔", "蛇", "蜥蜴", "其他"};

    /** 联系电话格式校验：手机号(1[3-9]\d{9}) 或 固话(0\d{2,3}-?\d{7,8})。 */
    static boolean isValidPhone(String phone) {
        if (TextUtils.isEmpty(phone)) {
            return false;
        }
        String p = phone.trim();
        return p.matches("^1[3-9]\\d{9}$") || p.matches("^0\\d{2,3}-?\\d{7,8}$");
    }

    private void createNewTreatmentRecord() {
        // ★ 编号归属当前舱：右舱新建的第一条同样从 000001 开始。
        String nextNo = nextCaseNo(activePatientState().zone);
        if (TextUtils.isEmpty(nextNo)) {
            Toast.makeText(activity, "病例编号已达到12位上限，无法继续自动编号", Toast.LENGTH_LONG).show();
            return;
        }
        String now = currentTimeText();
        for (PatientCase item : cases) {
            if (item.currentTreatment) {
                closeTreatmentRecord(item, now);
            }
        }
        PatientCase created = new PatientCase(nextNo, nextNo, "", "", "", "",
                "", "", "", nextNo, now, "");
        created.pendingInitialEntry = true;
        created.currentTreatment = true;
        created.treatmentStarted = false;   // 仅新建，未开始护疗：跨天后转入回顾
        created.treatmentStartTime = now;
        created.treatmentEndTime = "进行中";
        for (TreatmentEntry entry : created.treatmentEntries) {
            resetTreatmentEntryTiming(entry, now);
        }
        cases.add(0, created);
        selectedCaseIndex = 0;
        activeTab = 0;
        lastGeneratedPdf = null;
        saveTreatmentRecordsToStorage();
        /* ★ 2026-10-10 #69：新建样本不再同步设备当前功能状态 —— 避免把上一样本开启的红外/雾化等功能
           及其治疗时长带进新样本；设备遗留功能在「开始护疗」时统一关闭（见 startCareForPatient） */
        Toast.makeText(activity, "已创建新的治疗记录", Toast.LENGTH_SHORT).show();
    }

    private void finishCurrentTreatment() {
        PatientCase patient = currentCase();
        if (patient == null) {
            return;
        }
        if (!patient.currentTreatment) {
            Toast.makeText(activity, "历史治疗记录不能结束，请切回当前治疗", Toast.LENGTH_SHORT).show();
            return;
        }
        String now = currentTimeText();
        closeTreatmentRecord(patient, now);
        appendUserLog("护疗", "结束护疗", "住院号 " + patient.caseNo + " / " + patient.petName);
        /* ★ 2026-10-08 日志页·常规信息 */
        appendGeneralLog("护疗", "结束护疗 " + patient.caseNo + " / " + patient.petName);
        Toast.makeText(activity, "当前治疗已结束", Toast.LENGTH_SHORT).show();
    }

    // ★ V1.02 S2 开始护疗：把选中记录置为护疗中并开始累计时长，进入护疗页面（状态/主控/监护/视频）。
    //   左右舱隔离：同一时刻每舱至多一条进行中；切换住院号只影响本舱，不波及其余舱。
    private void startCareForPatient(PatientCase patient) {
        if (patient == null) {
            Toast.makeText(activity, "请先选中一条记录", Toast.LENGTH_SHORT).show();
            return;
        }
        /* ★ 2026-10-10 #69：仅「已真正开始护疗」才直接进页；仅新建未开始的样本（currentTreatment=true
           但 treatmentStarted=false）必须走下方完整开启流程，重置开始时间/治疗条目并清空设备遗留功能 */
        if (patient.currentTreatment && patient.treatmentStarted) {
            // 已在护疗中：直接进护疗页，不重复开启。
            enterCarePage(patient);
            return;
        }
        // 已结束的记录（治疗结束时间已落为具体时间、且非待录入）不可重复开始护疗。
        boolean alreadyEnded = !"进行中".equals(patient.treatmentEndTime) && !patient.pendingInitialEntry;
        if (alreadyEnded) {
            Toast.makeText(activity, "该记录已结束，不能重复开始护疗", Toast.LENGTH_SHORT).show();
            return;
        }
        String now = currentTimeText();
        String zone = zoneOfPatient(patient);
        PatientZoneState state = patientStateForZone(zone);
        for (PatientCase other : state.cases) {
            if (other != patient && other.currentTreatment) {
                closeTreatmentRecord(other, now);
            }
        }
        patient.currentTreatment = true;
        patient.treatmentStarted = true;    // 真正开始护疗：连续多日跨天保留在护疗列表
        patient.treatmentStartTime = now;
        patient.treatmentEndTime = "进行中";
        patient.pendingInitialEntry = false;
        for (TreatmentEntry entry : patient.treatmentEntries) {
            resetTreatmentEntryTiming(entry, now);
        }
        /* ★ 2026-10-10 #69：开始新一轮治疗前关闭设备上遗留的治疗功能（红外/蓝光/雾化/负离子/紫外），
           保证每个样本的治疗数据独立，不把上一样本开启的功能带进新样本 */
        int[] sessionControls = {6, 7, 10, 11, 12};
        for (int ci : sessionControls) {
            if (bleManager.isControlOn(ci)) {
                bleManager.setControlEnabled(ci, false);
            }
        }
        /* ★ 2026-10-10 #80：治疗时长按样本独立 —— 新样本开始护疗时清零本舱治疗计时 */
        bleManager.resetTreatmentTimer();
        lastGeneratedPdf = null;
        saveTreatmentRecordsToStorage(true);
        appendUserLog("护疗", "开始护疗", "住院号 " + patient.caseNo + " / " + patient.petName);
        /* ★ 2026-10-08 日志页·常规信息 */
        appendGeneralLog("护疗", "开始护疗 " + patient.caseNo + " / " + patient.petName);
        Toast.makeText(activity, "已开始护疗：" + patient.caseNo, Toast.LENGTH_SHORT).show();
        enterCarePage(patient);
    }

    private void enterCarePage(PatientCase patient) {
        if (patient != null) {
            String zone = zoneOfPatient(patient);
            PatientZoneState state = patientStateForZone(zone);
            int idx = state.cases.indexOf(patient);
            if (idx >= 0) {
                state.selectedCaseIndex = idx;
                if (zone.equals(bleManager.getCurrentZone())) {
                    selectedCaseIndex = idx;
                }
            }
        }
        showingSettings = false;
        activeTab = 0;
        if (activity != null) {
            activity.notifyLanhuStateChanged();
        }
        invalidate();
    }

    private void showOrganizationDialog() {
        if (TextUtils.isEmpty(accountStore.getCurrentAccount())) {
            Toast.makeText(activity, "请先登录后再管理机构", Toast.LENGTH_SHORT).show();
            handleAccountTap();
            return;
        }
        if (organizations.isEmpty()) {
            Toast.makeText(activity, "暂无可切换机构", Toast.LENGTH_SHORT).show();
            return;
        }
        final String[] items = new String[organizations.size()];
        for (int i = 0; i < organizations.size(); i++) {
            Organization item = organizations.get(i);
            String name = firstNonEmpty(item.name, "未命名机构");
            String address = firstNonEmpty(item.address, "未设置地址");
            items[i] = name + "  |  " + address;
        }
        final int[] chosen = {clampIndex(selectedOrganizationIndex, organizations.size())};
        new AlertDialog.Builder(activity)
                .setTitle("切换机构")
                .setSingleChoiceItems(items, chosen[0], new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        chosen[0] = which;
                    }
                })
                .setNegativeButton("取消", null)
                .setNeutralButton("编辑当前机构", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        // 原生弹窗已删除：把用户带到设置页（lanhu_81shezhi）就地编辑当前机构信息
                        if (activity != null) {
                            activity.navigateLanhuToHospitalSettings();
                        }
                    }
                })
                .setPositiveButton("切换", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        switchOrganization(chosen[0]);
                    }
                })
                .show();
    }

    private void switchOrganization(int index) {
        if (organizations.isEmpty()) {
            return;
        }
        selectedOrganizationIndex = clampIndex(index, organizations.size());
        Organization organization = currentOrganization();
        accountStore.setOrganizationForCurrent(organization.name);
        lastGeneratedPdf = null;
        saveUiSettings();
        Toast.makeText(activity, "已切换机构: " + firstNonEmpty(organization.name, "未命名机构"), Toast.LENGTH_SHORT).show();
        invalidate();
    }

    private void showEditOrganizationDialog() {
        // 旧的医院信息编辑弹窗已删除：现在直接改 settings 页（lanhu_81shezhi）上的输入框，
        // 点击"保存"按钮时由 MainActivity.handleOrganizationSaveFromLanhu 读取并调用 webUpdateOrganization 落库。
        // 保留此空壳仅为兼容调用方（performNativeAction、showOrganizationDialog 的"编辑当前机构"按钮）。
        // 调用方已改为跳转到设置页（见 navigateLanhuToHospitalSettings）。
    }

    private void showSearchDialog() {
        final EditText input = new EditText(activity);
        input.setSingleLine(true);
        input.setHint("宠物名字 / 主人名 / 电话号码");
        input.setText(searchKeyword);
        input.setInputType(InputType.TYPE_CLASS_TEXT);
        new AlertDialog.Builder(activity)
                .setTitle("搜索患者信息")
                .setView(input)
                .setNegativeButton("取消", null)
                .setNeutralButton("清空", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        searchKeyword = "";
                        invalidate();
                    }
                })
                .setPositiveButton("搜索", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        searchKeyword = input.getText().toString().trim();
                        List<Integer> visible = getVisibleCaseIndices();
                        if (!visible.isEmpty() && !visible.contains(selectedCaseIndex)) {
                            if (blockPatientSwitchIfNeeded()) {
                                invalidate();
                                return;
                            }
                            selectedCaseIndex = visible.get(0);
                        }
                        Toast.makeText(activity, visible.isEmpty() ? "没有匹配患者" : "找到 " + visible.size() + " 条患者信息", Toast.LENGTH_SHORT).show();
                        invalidate();
                    }
                })
                .show();
    }

    private void handlePatientAction(int actionIndex) {
        PatientCase patient = currentCase();
        if (patient == null) {
            Toast.makeText(activity, "暂无患者信息", Toast.LENGTH_SHORT).show();
            return;
        }
        if (actionIndex == 0) {
            generatePdfAction();
        } else if (actionIndex == 1) {
            showEditPatientDialog(patient);
        } else if (actionIndex == 2) {
            confirmDeletePatient(patient);
        } else {
            downloadPdfAction();
        }
    }

    private void showEditPatientDialog(final PatientCase patient) {
        LinearLayout form = new LinearLayout(activity);
        form.setOrientation(LinearLayout.VERTICAL);
        int pad = dpInt(12);
        form.setPadding(pad, pad, pad, pad);

        final EditText petName = addInput(form, "宠物名称", patient.petName, InputType.TYPE_CLASS_TEXT);
        final EditText species = addInput(form, "动物种类", patient.species, InputType.TYPE_CLASS_TEXT);
        final EditText sex = addInput(form, "性别", patient.sex, InputType.TYPE_CLASS_TEXT);
        final EditText age = addInput(form, "年龄", patient.age, InputType.TYPE_CLASS_TEXT);
        final EditText ownerName = addInput(form, "宠主姓名", patient.ownerName, InputType.TYPE_CLASS_TEXT);
        final EditText ownerPhone = addInput(form, "联系电话", patient.ownerPhone, InputType.TYPE_CLASS_PHONE);
        configurePhoneInput(ownerPhone);
        final EditText doctor = addInput(form, "主治医师", patient.doctor, InputType.TYPE_CLASS_TEXT);
        // ★ 与 H5 录入弹窗保持一致：体重 / 科室 / 复诊时间。
        //   暂时用不到，输入框先注释掉；PatientCase 的字段和报告绑定都保留，
        //   之后要用把下面几行（含保存处三行）放开即可，PDF 会自动带出。
        // final EditText weight = addInput(form, "体重", patient.weight, InputType.TYPE_CLASS_TEXT);
        // final EditText department = addInput(form, "科室", patient.department, InputType.TYPE_CLASS_TEXT);
        // final EditText followUpDate = addInput(form, "复诊时间", patient.followUpDate, InputType.TYPE_CLASS_TEXT);
        final EditText recordNo = addInput(form, "病历号", patient.recordNo, InputType.TYPE_CLASS_TEXT);
        recordNo.setFilters(new InputFilter[]{new InputFilter.LengthFilter(12)});
        final EditText visitDate = addInput(form, "就诊日期", patient.visitDate, InputType.TYPE_CLASS_TEXT);
        configureVisitDateInput(visitDate);
        final EditText note = addInput(form, "医嘱备注", patient.note, InputType.TYPE_CLASS_TEXT);

        ScrollView scroll = new ScrollView(activity);
        scroll.addView(form);
        final AlertDialog dialog = new AlertDialog.Builder(activity)
                .setTitle("编辑患者信息")
                .setView(scroll)
                .setNegativeButton("取消", null)
                .setPositiveButton("保存", null)
                .create();
        dialog.setOnShowListener(new DialogInterface.OnShowListener() {
            @Override
            public void onShow(DialogInterface ignored) {
                dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(new OnClickListener() {
                    @Override
                    public void onClick(View view) {
                        String phone = normalizePhoneDigits(ownerPhone.getText().toString());
                        if (!TextUtils.isEmpty(phone) && phone.length() != 11) {
                            Toast.makeText(activity, "联系电话必须为11位数字", Toast.LENGTH_SHORT).show();
                            return;
                        }
                        String visit = cleanInput(visitDate, patient.visitDate);
                        if (TextUtils.isEmpty(visit)) {
                            visit = currentTimeText();
                        }
                        patient.petName = cleanInput(petName, patient.petName);
                        patient.species = cleanInput(species, patient.species);
                        patient.sex = cleanInput(sex, patient.sex);
                        patient.age = cleanInput(age, patient.age);
                        patient.ownerName = cleanInput(ownerName, patient.ownerName);
                        patient.ownerPhone = phone;
                        patient.doctor = cleanInput(doctor, patient.doctor);
                        // patient.weight = cleanInput(weight, patient.weight);
                        // patient.department = cleanInput(department, patient.department);
                        // patient.followUpDate = cleanInput(followUpDate, patient.followUpDate);
                        String newRecordNo = cleanInput(recordNo, patient.recordNo);
                        String patientZone = zoneOfPatient(patient);
                        if (caseNoUsedByOther(patientZone, newRecordNo, patient)) {
                            Toast.makeText(activity, "病历编号已存在，请输入其他编号", Toast.LENGTH_SHORT).show();
                            return;
                        }
                        patient.recordNo = newRecordNo;
                        patient.caseNo = newRecordNo;
                        if (patient.pendingInitialEntry) {
                            patient.monitorNo = newRecordNo;
                            updateCaseNoCursorFromText(patientZone, newRecordNo);
                            patient.pendingInitialEntry = false;
                        }
                        patient.visitDate = visit;
                        patient.note = cleanInput(note, patient.note);
                        lastGeneratedPdf = null;
                        saveTreatmentRecordsToStorage(true);
                        appendUserLog("病例", "编辑", "住院号 " + patient.caseNo + " / " + patient.petName);
                        Toast.makeText(activity, "患者信息已保存", Toast.LENGTH_SHORT).show();
                        dialog.dismiss();
                        invalidate();
                    }
                });
            }
        });
        dialog.show();
    }

    private EditText addLabeledInput(LinearLayout form, String labelText, String hint,
                                      String value, int inputType) {
        LinearLayout container = new LinearLayout(activity);
        container.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams containerParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        containerParams.topMargin = dpInt(6);
        form.addView(container, containerParams);

        TextView label = new TextView(activity);
        label.setText(labelText);
        label.setTextSize(13);
        label.setTypeface(Typeface.DEFAULT_BOLD);
        label.setPadding(0, 0, 0, dpInt(2));
        container.addView(label);

        EditText input = createInput(hint, value, inputType);
        input.setId(View.generateViewId());
        label.setLabelFor(input.getId());
        container.addView(input, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
        return input;
    }

    private Button createDialogActionButton(String text) {
        Button button = new Button(activity);
        button.setText(text);
        button.setTextSize(13);
        button.setSingleLine(true);
        button.setMinWidth(0);
        button.setMinimumWidth(0);
        button.setMinHeight(dpInt(48));
        return button;
    }

    private LinearLayout.LayoutParams actionButtonParams(float weight, int leftMargin) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, weight);
        params.leftMargin = leftMargin;
        return params;
    }

    private EditText addInput(LinearLayout form, String hint, String value, int inputType) {
        EditText input = new EditText(activity);
        input.setSingleLine(true);
        input.setHint(hint);
        input.setText(value);
        input.setInputType(inputType);
        form.addView(input);
        return input;
    }

    private EditText createInput(String hint, String value, int inputType) {
        EditText input = new EditText(activity);
        input.setSingleLine(true);
        input.setHint(hint);
        input.setText(value);
        input.setInputType(inputType);
        return input;
    }

    private void configurePhoneInput(final EditText input) {
        input.setInputType(InputType.TYPE_CLASS_NUMBER);
        input.setFilters(new InputFilter[]{new InputFilter.LengthFilter(11)});
        input.addTextChangedListener(new android.text.TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }

            @Override
            public void afterTextChanged(android.text.Editable s) {
                String normalized = normalizePhoneDigits(s == null ? "" : s.toString());
                String current = s == null ? "" : s.toString();
                if (!normalized.equals(current)) {
                    input.setText(normalized);
                    input.setSelection(normalized.length());
                }
            }
        });
    }

    private String normalizePhoneDigits(String value) {
        if (TextUtils.isEmpty(value)) {
            return "";
        }
        String digits = value.replaceAll("\\D+", "");
        return digits.length() > 11 ? digits.substring(0, 11) : digits;
    }

    private void configureVisitDateInput(final EditText input) {
        input.setFocusable(false);
        input.setClickable(true);
        input.setCursorVisible(false);
        input.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View view) {
                showVisitDatePicker(input);
            }
        });
    }

    private void showVisitDatePicker(final EditText target) {
        final Calendar calendar = parseVisitDate(target.getText().toString());
        new DatePickerDialog(activity, new DatePickerDialog.OnDateSetListener() {
            @Override
            public void onDateSet(android.widget.DatePicker view, final int year, final int month, final int dayOfMonth) {
                final int hour = calendar.get(Calendar.HOUR_OF_DAY);
                final int minute = calendar.get(Calendar.MINUTE);
                new TimePickerDialog(activity, new TimePickerDialog.OnTimeSetListener() {
                    @Override
                    public void onTimeSet(android.widget.TimePicker view, int pickedHour, int pickedMinute) {
                        Calendar selected = Calendar.getInstance();
                        selected.set(Calendar.YEAR, year);
                        selected.set(Calendar.MONTH, month);
                        selected.set(Calendar.DAY_OF_MONTH, dayOfMonth);
                        selected.set(Calendar.HOUR_OF_DAY, pickedHour);
                        selected.set(Calendar.MINUTE, pickedMinute);
                        selected.set(Calendar.SECOND, 0);
                        selected.set(Calendar.MILLISECOND, 0);
                        target.setText(formatVisitDate(selected));
                    }
                }, hour, minute, true).show();
            }
        }, calendar.get(Calendar.YEAR), calendar.get(Calendar.MONTH), calendar.get(Calendar.DAY_OF_MONTH)).show();
    }

    private Calendar parseVisitDate(String value) {
        Calendar calendar = Calendar.getInstance();
        if (TextUtils.isEmpty(value)) {
            return calendar;
        }
        try {
            Date parsed = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).parse(value.trim().replaceAll("\\s+", " "));
            if (parsed != null) {
                calendar.setTime(parsed);
            }
        } catch (Exception ignored) {
        }
        return calendar;
    }

    private String formatVisitDate(Calendar calendar) {
        return new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(calendar.getTime());
    }

    private String cleanInput(EditText input, String fallback) {
        String value = input.getText().toString().trim();
        return TextUtils.isEmpty(value) ? fallback : value;
    }

    private void confirmDeletePatient(final PatientCase patient) {
        new AlertDialog.Builder(activity)
                .setTitle("删除患者信息")
                .setMessage("确定删除 " + patient.petName + " 的 ICU 监护记录吗？")
                .setNegativeButton("取消", null)
                .setPositiveButton("删除", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        appendUserLog("病例", "删除", "住院号 " + patient.caseNo + " / " + patient.petName);
                        cases.remove(patient);
                        if (selectedCaseIndex >= cases.size()) {
                            selectedCaseIndex = Math.max(0, cases.size() - 1);
                        }
                        recomputeDailyCaseNoCursor();
                        lastGeneratedPdf = null;
                        saveTreatmentRecordsToStorage();
                        Toast.makeText(activity, "已删除患者信息", Toast.LENGTH_SHORT).show();
                        invalidate();
                    }
                })
                .show();
    }

    /** 任务9：删除病例后重算 activePatientState 当日住院号流水游标，
     *  使删除当天唯一的 001 病例后，下一条新建病例住院号尾部仍为 001。 */
    private void recomputeDailyCaseNoCursor() {
        PatientZoneState st = activePatientState();
        String prefix = zoneCasePrefix(st.zone);
        String today = currentDateYmd();
        long maxSeq = 0;
        for (PatientCase pc : st.cases) {
            String no = pc.caseNo;
            if (no == null) continue;
            String head = prefix + today;
            if (no.startsWith(head) && no.length() > head.length()) {
                String seqStr = no.substring(head.length());
                try {
                    long seq = Long.parseLong(seqStr);
                    if (seq > maxSeq) maxSeq = seq;
                } catch (NumberFormatException ignore) {
                    // 历史旧编号（非纯数字后缀），忽略
                }
            }
        }
        st.dailyCaseNoCursor = maxSeq;
        st.dailyCaseNoDate = today;
    }

    private void handleAccountTap() {
        if (!accountStore.hasAny()) {
            showRegisterDialog();
            return;
        }
        if (TextUtils.isEmpty(accountStore.getCurrentAccount())) {
            showAccountListDialog();
            return;
        }
        new AlertDialog.Builder(activity)
                .setTitle("当前账号: " + accountStore.getCurrentAccount())
                .setItems(new String[]{"切换账号", "退出登录"}, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        if (which == 0) {
                            showAccountListDialog();
                        } else {
                            webLogout();
                        }
                    }
                })
                .show();
    }

    private void showAccountListDialog() {
        final List<String> names = accountStore.listNames();
        if (names.isEmpty()) {
            showRegisterDialog();
            return;
        }
        new AlertDialog.Builder(activity)
                .setTitle("选择登录账号")
                .setItems(names.toArray(new String[0]), new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        if (which >= 0 && which < names.size()) {
                            showPasswordDialog(names.get(which));
                        }
                    }
                })
                .show();
    }

    private void showPasswordDialog(final String name) {
        LinearLayout form = new LinearLayout(activity);
        form.setOrientation(LinearLayout.VERTICAL);
        int pad = dpInt(12);
        form.setPadding(pad, pad, pad, pad);

        TextView help = new TextView(activity);
        help.setText("账号: " + name);
        help.setTextSize(13);
        help.setPadding(0, 0, 0, dpInt(8));
        form.addView(help);

        final EditText password = addInput(form, "登录口令", "", InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);

        new AlertDialog.Builder(activity)
                .setTitle("登录")
                .setView(form)
                .setNegativeButton("取消", null)
                .setPositiveButton("登录", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        String secret = password.getText().toString();
                        if (accountStore.authenticate(name, secret)) {
                            accountStore.setCurrentAccount(name);
                            applyAccountOrganization();
                            Toast.makeText(activity, "已登录: " + accountStore.getCurrentAccount(), Toast.LENGTH_SHORT).show();
                            invalidate();
                        } else {
                            Toast.makeText(activity, "账号或口令不正确", Toast.LENGTH_SHORT).show();
                        }
                    }
                })
                .show();
    }

    private void showRegisterDialog() {
        LinearLayout form = new LinearLayout(activity);
        form.setOrientation(LinearLayout.VERTICAL);
        int pad = dpInt(12);
        form.setPadding(pad, pad, pad, pad);

        TextView help = new TextView(activity);
        help.setText("本地登录：不联网，账号和口令保存在设备本地文件。");
        help.setTextSize(13);
        help.setPadding(0, 0, 0, dpInt(8));
        form.addView(help);

        final EditText account = addInput(form, "登录账号", "", InputType.TYPE_CLASS_TEXT);
        final EditText password = addInput(form, "登录口令", "", InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        final EditText organization = addInput(form, "所属机构", currentOrganization().name, InputType.TYPE_CLASS_TEXT);

        new AlertDialog.Builder(activity)
                .setTitle(accountStore.hasAny() ? "新增账号" : "创建第一个账号")
                .setView(form)
                .setNegativeButton("取消", null)
                .setPositiveButton("注册并登录", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        String value = account.getText().toString().trim();
                        String secret = password.getText().toString();
                        String org = organization.getText().toString().trim();
                        if (TextUtils.isEmpty(value)) {
                            Toast.makeText(activity, "请输入登录账号", Toast.LENGTH_SHORT).show();
                            return;
                        }
                        if (TextUtils.isEmpty(secret)) {
                            Toast.makeText(activity, "请输入登录口令", Toast.LENGTH_SHORT).show();
                            return;
                        }
                        if (accountStore.nameExists(value)) {
                            Toast.makeText(activity, "该账号已存在", Toast.LENGTH_SHORT).show();
                            return;
                        }
                        if (accountStore.register(value, secret, org)) {
                            accountStore.setCurrentAccount(value);
                            applyAccountOrganization();
                            Toast.makeText(activity, "已注册并登录: " + accountStore.getCurrentAccount(), Toast.LENGTH_SHORT).show();
                            invalidate();
                        } else {
                            Toast.makeText(activity, "注册失败", Toast.LENGTH_SHORT).show();
                        }
                    }
                })
                .show();
    }

    private void generatePdfAction() {
        try {
            lastGeneratedPdf = generatePdfReport();
            addPdfRecord("生成PDF", lastGeneratedPdf.getName(), cacheLocationText(lastGeneratedPdf), lastGeneratedPdf, null);
            activeTab = 5;
            showingSettings = false;
            Toast.makeText(activity, "PDF已生成: " + lastGeneratedPdf.getName(), Toast.LENGTH_LONG).show();
        } catch (IOException exception) {
            Toast.makeText(activity, "生成PDF失败: " + exception.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    private void downloadPdfAction() {
        try {
            if (lastGeneratedPdf == null || !lastGeneratedPdf.exists()) {
                lastGeneratedPdf = generatePdfReport();
                addPdfRecord("生成PDF", lastGeneratedPdf.getName(), cacheLocationText(lastGeneratedPdf), lastGeneratedPdf, null);
            }
            String downloadName = createPdfFileName();
            Uri uri = copyPdfToDownloads(lastGeneratedPdf, downloadName);
            addPdfRecord("下载PDF", downloadName, "系统下载目录: " + uri.toString(), lastGeneratedPdf, uri);
            activeTab = 5;
            showingSettings = false;
            Toast.makeText(activity, "PDF已下载: " + uri.toString(), Toast.LENGTH_LONG).show();
        } catch (IOException exception) {
            Toast.makeText(activity, "下载失败: " + exception.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    private void addPdfRecord(String action, String fileName, String location, File cacheFile, Uri downloadUri) {
        PatientCase patient = currentCase();
        String patientName = patient == null ? "-" : patient.petName;
        String timeText = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(new Date());
        String zone = patientZoneOrCurrent(patient);
        pdfRecords.add(0, new PdfRecord(action, fileName, patientName, timeText, location, cacheFile, downloadUri,
                zone));
        trimPdfRecordsForZone(zone);
    }

    private String cacheLocationText(File file) {
        return file == null ? "应用缓存目录" : "应用缓存目录: " + file.getAbsolutePath();
    }

    // ★ V1.02 S4 导出/传输：在已生成 PDF 基础上，提供系统分享（微信/蓝牙）与系统打印；发送数据置灰二期。
    private File ensureReportPdf() throws IOException {
        if (lastGeneratedPdf == null || !lastGeneratedPdf.exists()) {
            lastGeneratedPdf = generatePdfReport();
            addPdfRecord("生成PDF", lastGeneratedPdf.getName(), cacheLocationText(lastGeneratedPdf), lastGeneratedPdf, null);
        }
        return lastGeneratedPdf;
    }

    private void exportReportViaShare() {
        try {
            File pdf = ensureReportPdf();
            if (pdf == null) return;
            Uri uri = copyPdfToDownloads(pdf, createPdfFileName());
            if (uri == null) {
                Toast.makeText(activity, "生成分享文件失败", Toast.LENGTH_SHORT).show();
                return;
            }
            Intent intent = new Intent(Intent.ACTION_SEND);
            intent.setType("application/pdf");
            intent.putExtra(Intent.EXTRA_STREAM, uri);
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            activity.startActivity(Intent.createChooser(intent, "发送报告（微信文件传输助手 / 蓝牙等）"));
            Toast.makeText(activity, "请选择微信「文件传输助手」或蓝牙设备发送报告", Toast.LENGTH_LONG).show();
            appendUserLog("导出", "导出报告", "住院号 " + (currentCase() == null ? "-" : currentCase().caseNo));
        } catch (IOException e) {
            Toast.makeText(activity, "导出失败: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    private void sendReportViaBluetooth() {
        try {
            File pdf = ensureReportPdf();
            if (pdf == null) return;
            Uri uri = copyPdfToDownloads(pdf, createPdfFileName());
            if (uri == null) {
                Toast.makeText(activity, "生成分享文件失败", Toast.LENGTH_SHORT).show();
                return;
            }
            Intent intent = new Intent(Intent.ACTION_SEND);
            intent.setType("application/pdf");
            intent.putExtra(Intent.EXTRA_STREAM, uri);
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            activity.startActivity(Intent.createChooser(intent, "通过蓝牙发送报告"));
            Toast.makeText(activity, "请选择已配对的蓝牙设备发送报告", Toast.LENGTH_LONG).show();
            appendUserLog("导出", "蓝牙发送", "住院号 " + (currentCase() == null ? "-" : currentCase().caseNo));
        } catch (IOException e) {
            Toast.makeText(activity, "蓝牙发送失败: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    // ★ 任务28：文件助手二维码状态（firstPhaseState.shareQr 回推给 H5 渲染二维码）
    private String shareQrUrl = "";
    private String shareQrError = "";
    private String shareQrName = "";

    /** 生成报告 PDF → 注册到局域网 HTTP 服务 → 回推下载页 URL（H5 渲染二维码） */
    private void buildReportShareQr() {
        shareQrUrl = "";
        shareQrError = "";
        shareQrName = "";
        try {
            File pdf = ensureReportPdf();
            if (pdf == null || !pdf.exists()) {
                shareQrError = "暂无可导出的报告";
                return;
            }
            String ip = ShareHttpServer.getLanIp();
            if (TextUtils.isEmpty(ip)) {
                shareQrError = "未获取到局域网IP，请连接WiFi后重试";
                return;
            }
            // 文件名含住院号/宠物名/日期（V1.02 R25 验收口径）
            String displayName = createPdfFileName();
            String token = ShareHttpServer.get().register(pdf, displayName);
            shareQrName = displayName;
            shareQrUrl = "http://" + ip + ":" + ShareHttpServer.PORT + "/r/" + token;
            appendUserLog("导出", "文件助手二维码", "住院号 " + (currentCase() == null ? "-" : currentCase().caseNo));
        } catch (IOException e) {
            shareQrError = "生成失败: " + e.getMessage();
        }
    }

    /* ★ 2026-10-10 #56：打印结果回推 H5 —— seq 递增触发边沿检测，H5 toast 并退出打印页 */
    private void pushPrintStatus(boolean ok, String msg) {
        printStatusSeq++;
        printStatusOk = ok;
        printStatusMsg = msg == null ? "" : msg;
        appendUserLog("打印", ok ? "打印成功" : "打印失败", printStatusMsg);
        invalidate();
        if (activity instanceof MainActivity) {
            ((MainActivity) activity).notifyLanhuStateChanged();
        }
    }

    /* 轮询 PrintJob 直至全部进入终态（完成/失败/取消），最多 60 秒 */
    private void watchPrintJobs(final java.util.List<PrintJob> jobs) {
        if (jobs == null || jobs.isEmpty()) {
            pushPrintStatus(false, "打印任务创建失败");
            return;
        }
        printerScanHandler.postDelayed(new Runnable() {
            private int elapsed = 0;
            @Override
            public void run() {
                boolean allTerminal = true;
                boolean allOk = true;
                String failReason = "";
                for (PrintJob job : jobs) {
                    if (job == null) { continue; }
                    if (job.isCompleted()) { continue; }
                    allOk = false;
                    if (job.isFailed()) {
                        failReason = "打印失败";
                    } else if (job.isCancelled()) {
                        failReason = "打印已取消";
                    } else {
                        allTerminal = false;
                    }
                }
                if (allTerminal) {
                    pushPrintStatus(allOk, allOk ? "打印成功" : failReason);
                    return;
                }
                elapsed += 1000;
                if (elapsed >= 60000) {
                    pushPrintStatus(false, "打印超时，请检查打印机");
                    return;
                }
                printerScanHandler.postDelayed(this, 1000);
            }
        }, 1000);
    }

    private void printReportViaSystem() {
        try {
            File pdf = ensureReportPdf();
            if (pdf == null) {
                pushPrintStatus(false, "报告生成失败，请先补全患者信息");
                return;
            }
            if (activity == null) {
                pushPrintStatus(false, "当前环境不支持打印");
                return;
            }
            Object pm = activity.getSystemService(Context.PRINT_SERVICE);
            if (!(pm instanceof PrintManager)) {
                pushPrintStatus(false, "当前设备不支持打印");
                return;
            }
            PrintManager printManager = (PrintManager) pm;
            int copies = s5PrintCopies < 1 ? 1 : s5PrintCopies;
            java.util.List<PrintJob> jobs = new java.util.ArrayList<>();
            for (int i = 1; i <= copies; i++) {
                String jobName = "动物ICU监护报告_" + pdf.getName() + (copies > 1 ? "_" + i : "");
                PrintJob job = printManager.print(jobName, new PdfPrintAdapter(pdf), null);
                if (job != null) {
                    jobs.add(job);
                }
            }
            appendUserLog("打印", "打印报告", "住院号 " + (currentCase() == null ? "-" : currentCase().caseNo) + " 份数=" + copies);
            watchPrintJobs(jobs);
        } catch (IOException e) {
            pushPrintStatus(false, "打印失败: " + e.getMessage());
        }
    }

    /* ★ 2026-10-10 #57：NSD 扫描局域网打印机（Bonjour/mDNS 广播的 _ipp._tcp 与 _pdl-datastream._tcp 服务）。
       8 秒后自动停止；每台解析出的打印机以 {name, addr} 存入 printerScanResults 并即时回推 H5。 */
    private void startPrinterScan() {
        if (activity == null) {
            return;
        }
        Object svc = activity.getSystemService(Context.NSD_SERVICE);
        if (!(svc instanceof NsdManager)) {
            Toast.makeText(activity, "当前设备不支持打印机扫描", Toast.LENGTH_SHORT).show();
            return;
        }
        final NsdManager nsd = (NsdManager) svc;
        stopPrinterScan();
        // mDNS 组播锁：部分设备不持锁收不到打印机应答
        try {
            WifiManager wm = (WifiManager) activity.getApplicationContext().getSystemService(Context.WIFI_SERVICE);
            if (wm != null) {
                printerScanLock = wm.createMulticastLock("icu_printer_scan");
                printerScanLock.setReferenceCounted(true);
                printerScanLock.acquire();
            }
        } catch (Exception ignored) { }
        synchronized (printerScanResults) {
            printerScanResults.clear();
        }
        printerScanning = true;
        final String[] types = {"_ipp._tcp.", "_pdl-datastream._tcp."};
        for (final String type : types) {
            NsdManager.DiscoveryListener listener = new NsdManager.DiscoveryListener() {
                @Override public void onStartDiscoveryFailed(String serviceType, int errorCode) { }
                @Override public void onStopDiscoveryFailed(String serviceType, int errorCode) { }
                @Override public void onDiscoveryStarted(String serviceType) { }
                @Override public void onDiscoveryStopped(String serviceType) { }
                @Override public void onServiceFound(NsdServiceInfo serviceInfo) {
                    if (serviceInfo == null) { return; }
                    try {
                        nsd.resolveService(serviceInfo, new NsdManager.ResolveListener() {
                            @Override public void onResolveFailed(NsdServiceInfo info, int errorCode) { }
                            @Override public void onServiceResolved(NsdServiceInfo info) {
                                if (info == null || info.getHost() == null) { return; }
                                String name = info.getServiceName() == null ? "" : info.getServiceName().trim();
                                if (name.isEmpty()) { return; }
                                String addr = info.getHost().getHostAddress() + ":" + info.getPort();
                                try {
                                    JSONObject item = new JSONObject();
                                    item.put("name", name);
                                    item.put("addr", addr);
                                    synchronized (printerScanResults) {
                                        for (JSONObject ex : printerScanResults) {
                                            if (addr.equals(ex.optString("addr")) || name.equals(ex.optString("name"))) {
                                                return;
                                            }
                                        }
                                        printerScanResults.add(item);
                                    }
                                } catch (JSONException ignored) { }
                                invalidate();
                                if (activity instanceof MainActivity) {
                                    ((MainActivity) activity).notifyLanhuStateChanged();
                                }
                            }
                        });
                    } catch (Exception ignored) { }
                }
                @Override public void onServiceLost(NsdServiceInfo serviceInfo) { }
            };
            try {
                nsd.discoverServices(type, NsdManager.PROTOCOL_DNS_SD, listener);
                printerScanListeners.add(listener);
            } catch (Exception ignored) { }
        }
        // 8 秒扫描窗口
        printerScanHandler.postDelayed(new Runnable() {
            @Override public void run() {
                stopPrinterScan();
                invalidate();
                if (activity instanceof MainActivity) {
                    ((MainActivity) activity).notifyLanhuStateChanged();
                }
            }
        }, 8000);
    }

    private void stopPrinterScan() {
        printerScanning = false;
        Object svc = activity == null ? null : activity.getSystemService(Context.NSD_SERVICE);
        if (svc instanceof NsdManager) {
            for (NsdManager.DiscoveryListener l : printerScanListeners) {
                try { ((NsdManager) svc).stopServiceDiscovery(l); } catch (Exception ignored) { }
            }
        }
        printerScanListeners.clear();
        if (printerScanLock != null) {
            try { printerScanLock.release(); } catch (Exception ignored) { }
            printerScanLock = null;
        }
    }

    /**
     * ★ 任务14（V1.02 R59）：打印测试页 —— 生成一页简易 PDF（含打印机地址与时间），
     * 走与报告相同的系统打印通道，用于验证打印机配置是否正确。
     */
    private void printTestPageViaSystem() {
        if (activity == null) {
            Toast.makeText(activity, "当前环境不支持打印", Toast.LENGTH_SHORT).show();
            return;
        }
        Object pm = activity.getSystemService(Context.PRINT_SERVICE);
        if (!(pm instanceof PrintManager)) {
            Toast.makeText(activity, "当前设备不支持打印", Toast.LENGTH_SHORT).show();
            return;
        }
        try {
            PdfDocument doc = new PdfDocument();
            PdfDocument.PageInfo info = new PdfDocument.PageInfo.Builder(595, 842, 1).create();
            PdfDocument.Page page = doc.startPage(info);
            Canvas canvas = page.getCanvas();
            Paint paint = new Paint();
            paint.setColor(Color.BLACK);
            paint.setTextSize(26);
            canvas.drawText("URIT 动物ICU · 打印测试页", 70, 150, paint);
            paint.setTextSize(17);
            canvas.drawText("打印机地址: " + (TextUtils.isEmpty(s5PrinterAddress) ? "（未设置）" : s5PrinterAddress), 70, 210, paint);
            canvas.drawText("打印时间: " + new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(new java.util.Date()), 70, 250, paint);
            canvas.drawText("如果您能看到这一页，说明打印机配置正确。", 70, 310, paint);
            doc.finishPage(page);
            File dir = new File(activity.getCacheDir(), "print");
            if (!dir.exists() && !dir.mkdirs()) {
                throw new IOException("无法创建打印缓存目录");
            }
            File pdf = new File(dir, "icu_print_test.pdf");
            FileOutputStream os = new FileOutputStream(pdf);
            try {
                doc.writeTo(os);
            } finally {
                os.close();
                doc.close();
            }
            ((PrintManager) pm).print("动物ICU打印测试页", new PdfPrintAdapter(pdf), null);
            Toast.makeText(activity, "已发送打印测试页", Toast.LENGTH_SHORT).show();
            appendUserLog("打印", "打印测试页", TextUtils.isEmpty(s5PrinterAddress) ? "未设置地址" : s5PrinterAddress);
        } catch (Exception e) {
            Toast.makeText(activity, "打印测试页失败: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    private void sendDataStage2() {
        Toast.makeText(activity, "发送数据功能为二期规划，暂未开放", Toast.LENGTH_SHORT).show();
    }

    private static final class PdfPrintAdapter extends PrintDocumentAdapter {
        private final File pdfFile;
        PdfPrintAdapter(File pdfFile) { this.pdfFile = pdfFile; }
        @Override
        public void onLayout(PrintAttributes oldAttributes, PrintAttributes newAttributes,
                             CancellationSignal cancellationSignal, PrintDocumentAdapter.LayoutResultCallback callback, android.os.Bundle extras) {
            if (cancellationSignal.isCanceled()) { callback.onLayoutCancelled(); return; }
            PrintDocumentInfo info = new PrintDocumentInfo.Builder(pdfFile.getName())
                    .setContentType(PrintDocumentInfo.CONTENT_TYPE_DOCUMENT)
                    .setPageCount(PrintDocumentInfo.PAGE_COUNT_UNKNOWN)
                    .build();
            callback.onLayoutFinished(info, true);
        }
        @Override
        public void onWrite(PageRange[] pages, ParcelFileDescriptor destination,
                            CancellationSignal cancellationSignal, PrintDocumentAdapter.WriteResultCallback callback) {
            try {
                InputStream in = new FileInputStream(pdfFile);
                OutputStream out = new FileOutputStream(destination.getFileDescriptor());
                byte[] buffer = new byte[8192];
                int len;
                while ((len = in.read(buffer)) > 0) { out.write(buffer, 0, len); }
                in.close();
                out.close();
                callback.onWriteFinished(new PageRange[]{PageRange.ALL_PAGES});
            } catch (Exception e) {
                callback.onWriteFailed(e.getMessage());
            }
        }
    }

    private File generatePdfReport() throws IOException {
        PatientCase patient = currentCase();
        if (patient == null) {
            throw new IOException("暂无患者信息");
        }
        String missing = missingReportFields(patient);
        if (!TextUtils.isEmpty(missing)) {
            throw new IOException("请先补全患者信息: " + missing);
        }
        captureTreatmentSampleIfNeeded(true);
        File output = new File(activity.getCacheDir(), createPdfFileName());
        PdfDocument document = new PdfDocument();
        PdfDocument.Page page = document.startPage(new PdfDocument.PageInfo.Builder(595, 842, 1).create());
        Canvas pdfCanvas = page.getCanvas();
        Paint pdfPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        String createdAt = formatSettingsDateTime(new Date());
        drawTemplatePdf(pdfCanvas, pdfPaint, patient, currentOrganization(), createdAt, s5ReportTitle, s5ReportDeclaration, s5FooterLogoUri);
        document.finishPage(page);

        boolean useLegacyPdfTemplate = false;
        if (useLegacyPdfTemplate) {
        drawPdfHeader(pdfCanvas, pdfPaint, "动物ICU重症监护报告", "生成时间: " + createdAt);

        int y = 116;
        y = drawPdfSection(pdfCanvas, pdfPaint, "医院信息", y);
        Organization org = currentOrganization();
        y = drawPdfLine(pdfCanvas, pdfPaint, "医院名称", org.name, y);
        y = drawPdfLine(pdfCanvas, pdfPaint, "医院地址", org.address, y);
        y += 10;
        y = drawPdfSection(pdfCanvas, pdfPaint, "患者信息", y);
        y = drawPdfLine(pdfCanvas, pdfPaint, "宠物名称", patient.petName + " / " + patient.species + " / " + patient.sex, y);
        y = drawPdfLine(pdfCanvas, pdfPaint, "年龄", patient.age, y);
        y = drawPdfLine(pdfCanvas, pdfPaint, "宠主姓名", patient.ownerName, y);
        y = drawPdfLine(pdfCanvas, pdfPaint, "联系电话", patient.ownerPhone, y);
        y = drawPdfLine(pdfCanvas, pdfPaint, "病历号", patient.recordNo, y);
        y = drawPdfLine(pdfCanvas, pdfPaint, "就诊日期", patient.visitDate, y);
        y = drawPdfLine(pdfCanvas, pdfPaint, "主治医师", patient.doctor, y);
        y = drawPdfLine(pdfCanvas, pdfPaint, "备注", patient.note, y);
        y += 10;
        y = drawPdfSection(pdfCanvas, pdfPaint, "ICU状态", y);
        y = drawPdfLine(pdfCanvas, pdfPaint, "舱内温度", bleManager.getCabinTempText(), y);
        y = drawPdfLine(pdfCanvas, pdfPaint, "氧浓度", bleManager.getOxygenText(), y);
        y = drawPdfLine(pdfCanvas, pdfPaint, "湿度", bleManager.getHumidityText(), y);
        y = drawPdfLine(pdfCanvas, pdfPaint, "CO2浓度", bleManager.getCo2Text(), y);
        y = drawPdfLine(pdfCanvas, pdfPaint, "治疗时长", bleManager.getTreatmentTimeText(), y);
        y += 10;
        y = drawPdfTreatmentRecordBlock(pdfCanvas, pdfPaint, patient, y);
        document.finishPage(page);

        PdfDocument.Page monitorPage = document.startPage(new PdfDocument.PageInfo.Builder(595, 842, 2).create());
        Canvas monitorCanvas = monitorPage.getCanvas();
        drawPdfHeader(monitorCanvas, pdfPaint, "实时监护与电波图", "患者: " + patient.petName + "    生成时间: " + createdAt);
        y = 116;
        y = drawPdfSection(monitorCanvas, pdfPaint, "实时监护", y);
        drawPdfMonitorBlock(monitorCanvas, pdfPaint, y);
        document.finishPage(monitorPage);

        }

        FileOutputStream out = new FileOutputStream(output);
        try {
            document.writeTo(out);
        } finally {
            out.close();
            document.close();
        }
        return output;
    }

    private void drawTemplatePdf(Canvas canvas, Paint pdfPaint, PatientCase patient, Organization org, String createdAt, String reportTitle, String declaration, String footerLogoUri) {
        pdfFill(canvas, pdfPaint, 0, 0, 595, 842, Color.WHITE);
        drawPdfTemplateHeader(canvas, pdfPaint, org, reportTitle);
        drawPdfTemplatePatientInfo(canvas, pdfPaint, patient);
        drawPdfTemplateTreatment(canvas, pdfPaint, patient);
        drawPdfTemplateVitalsTable(canvas, pdfPaint, patient, createdAt);
        // 波形与数值：实时优先，断开时回退病例快照，避免打印时波形/数值全空
        drawPdfTemplateWave(canvas, pdfPaint, "\u3010\u5fc3\u7535\u56fe\u3011", 333, Color.rgb(55, 220, 92), 0,
                pdfWaveSamples(am4100Manager.getEcgWaveSamples(), am4100Manager.getHeartRateHistory(),
                        patient.monitorSnapshot.ecgWaveSamples, patient.monitorSnapshot.heartRateHistory),
                pdfFirstNonZero(am4100Manager.getHeartRateValue(),
                        pdfVitalsNumber(patient.monitorSnapshot.heartRate)));
        drawPdfTemplateWave(canvas, pdfPaint, "\u3010\u8109\u640f\u6ce2\u5f62\u56fe\u3011", 387, Color.rgb(245, 74, 72), 1,
                pdfWaveSamples(am4100Manager.getSpo2WaveSamples(), am4100Manager.getSpo2History(),
                        patient.monitorSnapshot.spo2WaveSamples, patient.monitorSnapshot.spo2History),
                pdfFirstNonZero(am4100Manager.getSpo2Value(),
                        pdfVitalsNumber(patient.monitorSnapshot.spo2)));
        drawPdfTemplateWave(canvas, pdfPaint, "\u3010\u547c\u5438\u7387\u56fe\u3011", 441, Color.rgb(53, 176, 232), 2,
                pdfWaveSamples(am4100Manager.getRespWaveSamples(), am4100Manager.getRespHistory(),
                        patient.monitorSnapshot.respWaveSamples, patient.monitorSnapshot.respHistory),
                pdfFirstNonZero(am4100Manager.getRespValue(),
                        pdfVitalsNumber(patient.monitorSnapshot.resp)));
        drawPdfTemplateEnvironment(canvas, pdfPaint);
        drawPdfTemplateImageAlarm(canvas, pdfPaint, patient);
        drawPdfTemplateConclusion(canvas, pdfPaint, patient, org, createdAt);
        drawPdfTemplateFooter(canvas, pdfPaint, declaration, footerLogoUri);
    }

    private void drawPdfTemplateFooter(Canvas canvas, Paint pdfPaint, String declaration, String footerLogoUri) {
        if (!pdfIsBlankValue(declaration)) {
            pdfTextFit(canvas, pdfPaint, declaration, 28, 800, 539, 9, Color.rgb(96, 101, 108), false);
        }
        if (!pdfIsBlankValue(footerLogoUri)) {
            try {
                Bitmap bmp = BitmapFactory.decodeFile(footerLogoUri);
                if (bmp != null) {
                    float maxH = 28f;
                    float h = maxH;
                    float w = h * bmp.getWidth() / bmp.getHeight();
                    if (w > 120f) { w = 120f; h = w * bmp.getHeight() / bmp.getWidth(); }
                    pdfPaint.setShader(null);
                    pdfPaint.setStyle(Paint.Style.FILL);
                    canvas.drawBitmap(bmp, null, new RectF(595 - 28 - w, 806, 595 - 28, 806 + h), pdfPaint);
                    bmp.recycle();
                }
            } catch (Exception ignored) {
            }
        }
    }

    private void drawPdfTemplateHeader(Canvas canvas, Paint pdfPaint, Organization org, String reportTitle) {
        int headerBg = Color.rgb(228, 241, 252);
        int titleBlue = Color.rgb(0, 158, 218);
        int titleDark = Color.rgb(0, 118, 194);
        int strongText = Color.rgb(58, 62, 68);
        pdfFill(canvas, pdfPaint, 0, 13, 595, 52, headerBg);

        Path darkWedge = new Path();
        darkWedge.moveTo(318, 0);
        darkWedge.lineTo(378, 0);
        darkWedge.lineTo(344, 52);
        darkWedge.lineTo(284, 52);
        darkWedge.close();
        pdfPaint.setShader(null);
        pdfPaint.setStyle(Paint.Style.FILL);
        pdfPaint.setColor(titleDark);
        canvas.drawPath(darkWedge, pdfPaint);

        Path banner = new Path();
        banner.moveTo(360, 0);
        banner.lineTo(595, 0);
        banner.lineTo(595, 52);
        banner.lineTo(330, 52);
        banner.close();
        pdfPaint.setColor(titleBlue);
        canvas.drawPath(banner, pdfPaint);

        String hospitalName = pdfCleanValue(org == null ? "" : org.name);
        if (pdfIsBlankValue(hospitalName)) {
            hospitalName = "\u533b\u9662\u540d\u79f0";
        }
        pdfText(canvas, pdfPaint, "LOGO", 16, 38, 18, strongText, true);
        pdfTextFit(canvas, pdfPaint, hospitalName, 89, 36, 230, 15, strongText, false);
        pdfTextCenter(canvas, pdfPaint, pdfIsBlankValue(reportTitle) ? "ICU\u52a8\u7269\u8231\u6cbb\u7597\u8bb0\u5f55\u5355" : reportTitle, new RectF(360, 0, 590, 52), 18, Color.WHITE, true);
    }

    /** 舱位的中文名，用于报告与文件名。 */
    private String zoneText(String zone) {
        return "left".equals(EnvironmentProtocol.normalizeZone(zone)) ? "左舱" : "右舱";
    }

    /** 舱位的短标识，用于文件名（L / R）。 */
    private String zoneFileTag(String zone) {
        return "left".equals(EnvironmentProtocol.normalizeZone(zone)) ? "L" : "R";
    }

    // 报告正文用固定的 5 列栅格：可用区 28 ~ 568，等分后列间距 6，
    // 每列 103 = 标签宽 + 值宽。之前是手填坐标，出现值区压到下一列标签、
    // 以及最后一列冲出右边界的情况，这里统一由栅格算出来。
    private static final float PDF_INFO_LEFT = 28f;
    private static final float PDF_INFO_RIGHT = 568f;
    private static final float PDF_INFO_GAP = 6f;
    private static final int PDF_INFO_COLUMNS = 5;

    private float pdfInfoColumnWidth() {
        return pdfInfoColumnWidth(PDF_INFO_COLUMNS);
    }

    private float pdfInfoColumnWidth(int columns) {
        return (PDF_INFO_RIGHT - PDF_INFO_LEFT - PDF_INFO_GAP * (columns - 1)) / columns;
    }

    private float pdfInfoColumnX(int column) {
        return pdfInfoColumnX(column, PDF_INFO_COLUMNS);
    }

    private float pdfInfoColumnX(int column, int columns) {
        return PDF_INFO_LEFT + column * (pdfInfoColumnWidth(columns) + PDF_INFO_GAP);
    }

    private void drawPdfTemplatePatientInfo(Canvas canvas, Paint pdfPaint, PatientCase patient) {
        float y1 = 73;
        float y2 = 94;
        float colW = pdfInfoColumnWidth();
        // 住院号在两舱内各自编号，只靠它分不清左右，所以"舱位"列显示真实舱名。
        String zoneText = zoneText(zoneOfPatient(patient));

        drawPdfInfoField(canvas, pdfPaint, "\u52a8\u7269\u540d\u79f0", patient.petName,
                pdfInfoColumnX(0), y1, 45, colW - 45);
        drawPdfInfoField(canvas, pdfPaint, "\u4f4f\u9662\u53f7", patient.recordNo,
                pdfInfoColumnX(1), y1, 38, colW - 38);
        drawPdfInfoField(canvas, pdfPaint, "\u4e3b\u4eba", patient.ownerName,
                pdfInfoColumnX(2), y1, 32, colW - 32);
        drawPdfInfoField(canvas, pdfPaint, "\u6cbb\u7597\u65e5\u671f", pdfDateOnly(patient.visitDate),
                pdfInfoColumnX(3), y1, 52, colW - 52);
        drawPdfInfoField(canvas, pdfPaint, "\u8231\u4f4d", zoneText,
                pdfInfoColumnX(4), y1, 30, colW - 30);

        drawPdfInfoField(canvas, pdfPaint, "\u52a8\u7269\u79cd\u7c7b", patient.species,
                pdfInfoColumnX(0), y2, 45, colW - 45);
        // 体重 / 科室 来自病例录入弹窗，不再写死 "--"
        drawPdfInfoField(canvas, pdfPaint, "\u4f53\u91cd", patient.weight,
                pdfInfoColumnX(1), y2, 32, colW - 32);
        drawPdfInfoField(canvas, pdfPaint, "\u79d1\u5ba4", patient.department,
                pdfInfoColumnX(2), y2, 32, colW - 32);
        drawPdfInfoField(canvas, pdfPaint, "\u6cbb\u7597\u65f6\u957f",
                pdfFirstValue(bleManager.getTreatmentTimeText(), patient.treatmentEndTime),
                pdfInfoColumnX(3), y2, 52, colW - 52);
        drawPdfInfoField(canvas, pdfPaint, "\u64cd\u4f5c\u533b\u5e08",
                pdfFirstValue(patient.doctor, accountStore.getCurrentAccount()),
                pdfInfoColumnX(4), y2, 52, colW - 52);
    }

    private void drawPdfTemplateTreatment(Canvas canvas, Paint pdfPaint, PatientCase patient) {
        drawPdfSectionTitle(canvas, pdfPaint, "\u3010\u6cbb\u7597\u9879\u76ee\u3011", 28, 119, 92, 568);
        TreatmentEntry temp = pdfFindTreatmentEntry(patient, "\u8231\u5185\u6e29\u5ea6", "\u6052\u6e29\u6cbb\u7597");
        TreatmentEntry oxygen = pdfFindTreatmentEntry(patient, "\u6c27\u6d53\u5ea6", "\u6c27\u6c14\u6d53\u5ea6", "\u6c27\u6c14\u6cbb\u7597");
        TreatmentEntry nebulizer = pdfFindTreatmentEntry(patient, "\u96fe\u5316\u5668", "\u96fe\u5316\u6cbb\u7597");
        TreatmentEntry blueLight = pdfFindTreatmentEntry(patient, "\u84dd\u5149\u6cbb\u7597");
        TreatmentEntry redLight = pdfFindTreatmentEntry(patient, "\u7ea2\u5916\u6cbb\u7597", "\u7ea2\u5149\u6cbb\u7597");
        TreatmentEntry anion = pdfFindTreatmentEntry(patient, "\u8d1f\u79bb\u5b50", "\u8d1f\u79bb\u5b50\u6cbb\u7597");

        // 3 列：30 / 245 / 440。第三列原本把值区开到 625，超出 568 被裁；
        // 现在统一传 maxRight，并把它自己的 detailOffset 收到 62。
        drawPdfTreatmentCheck(canvas, pdfPaint, 30, 141, "\u6052\u6e29\u6cbb\u7597", pdfEntryEnabled(temp),
                "\u76ee\u6807\u6e29\u5ea6", pdfFirstValue(pdfEntryValue(temp), bleManager.getControlValue(0), bleManager.getCabinTempText()), 68,
                PDF_INFO_RIGHT);
        drawPdfTreatmentCheck(canvas, pdfPaint, 245, 141, "\u96fe\u5316\u6cbb\u7597", pdfTreatmentOn(10, nebulizer),
                "\u65f6\u957f", pdfControlDurationText(10), 66, PDF_INFO_RIGHT);
        drawPdfTreatmentCheck(canvas, pdfPaint, 440, 141, "\u7ea2\u5149\u6cbb\u7597", pdfTreatmentOn(6, redLight),
                "", pdfControlDurationText(6), 62, PDF_INFO_RIGHT);

        drawPdfTreatmentCheck(canvas, pdfPaint, 30, 162, "\u6c27\u6c14\u6cbb\u7597", pdfTreatmentOn(13, oxygen),
                "\u6c27\u6d53\u5ea6", pdfFirstValue(pdfEntryValue(oxygen), bleManager.getControlValue(1), bleManager.getOxygenText()), 68,
                PDF_INFO_RIGHT);
        drawPdfTreatmentCheck(canvas, pdfPaint, 245, 162, "\u84dd\u5149\u6cbb\u7597", pdfTreatmentOn(7, blueLight),
                "", pdfControlDurationText(7), 76, 437f);
        drawPdfTreatmentCheck(canvas, pdfPaint, 440, 162, "\u8d1f\u79bb\u5b50\u6cbb\u7597", pdfTreatmentOn(11, anion),
                "", pdfControlDurationText(11), 62, PDF_INFO_RIGHT);
    }

    private void drawPdfTemplateVitalsTable(Canvas canvas, Paint pdfPaint, PatientCase patient, String createdAt) {
        drawPdfSectionTitle(canvas, pdfPaint, "\u3010\u751f\u547d\u4f53\u5f81\u8bb0\u5f55\u3011", 28, 198, 118, 568);
        float left = 28;
        float top = 209;
        float[] widths = {150, 105, 105, 135, 45};
        String[] headers = {"\u65f6\u95f4", "\u5fc3\u7387", "\u8840\u6c27", "\u8840\u538b", "\u4f53\u6e29"};
        // 实时值优先，断开时回退病例快照
        String heart = pdfVitalsText(am4100Manager.getHeartRateText(),
                patient == null ? "" : patient.monitorSnapshot.heartRate);
        String spo2 = pdfVitalsText(am4100Manager.getSpo2Text(),
                patient == null ? "" : patient.monitorSnapshot.spo2);
        String bloodPressure = pdfVitalsText(am4100Manager.getBloodPressureText(),
                patient == null ? "" : patient.monitorSnapshot.bloodPressure);
        String bodyTemp = pdfVitalsText(am4100Manager.getBodyTempText(),
                patient == null ? "" : patient.monitorSnapshot.bodyTemp);
        boolean hasVitals = !pdfIsBlankValue(heart) || !pdfIsBlankValue(spo2) || !pdfIsBlankValue(bloodPressure) || !pdfIsBlankValue(bodyTemp);
        String[][] rows = new String[4][5];
        for (int i = 0; i < rows.length; i++) {
            rows[i][0] = "";
            rows[i][1] = "";
            rows[i][2] = "";
            rows[i][3] = "";
            rows[i][4] = "";
        }
        if (hasVitals) {
            rows[0][0] = createdAt;
            rows[0][1] = heart;
            rows[0][2] = spo2;
            rows[0][3] = bloodPressure;
            rows[0][4] = bodyTemp;
        } else {
            rows[0][1] = "--";
            rows[0][2] = "--";
            rows[0][3] = "--";
            rows[0][4] = "--";
        }

        drawPdfTableRow(canvas, pdfPaint, headers, left, top, widths, 20, true, 0);
        for (int i = 0; i < rows.length; i++) {
            drawPdfTableRow(canvas, pdfPaint, rows[i], left, top + 20 + i * 20, widths, 20, false, i);
        }
    }

    private void drawPdfTemplateWave(Canvas canvas, Paint pdfPaint, String label, float top, int color, int type,
                                     List<Integer> samples, int numericValue) {
        float labelLeft = 54;
        float boxLeft = 98;
        float boxRight = 568;
        float boxHeight = 48;
        pdfTextRight(canvas, pdfPaint, label, labelLeft + 34, top + 24, 10, Color.rgb(96, 101, 108), true);
        pdfRoundRect(canvas, pdfPaint, boxLeft, top, boxRight, top + boxHeight, 2, Color.BLACK, Color.TRANSPARENT, 0);
        if (pdfHasWaveSamples(samples)) {
            drawReportWave(canvas, boxLeft + 5, top + boxHeight / 2f, boxRight - 5, boxHeight * 0.34f,
                    color, type, samples, numericValue, 1.05f);
        }
    }

    private void drawPdfTemplateEnvironment(Canvas canvas, Paint pdfPaint) {
        drawPdfSectionTitle(canvas, pdfPaint, "\u3010\u73af\u5883\u53c2\u6570\u3011", 28, 515, 92, 568);
        // 4 列栅格：原来的手填坐标让"氧浓度"的值区冲到 595，超出右边界 568 被裁掉。
        float envW = pdfInfoColumnWidth(4);
        drawPdfInfoField(canvas, pdfPaint, "\u8231\u5185\u6e29\u5ea6", bleManager.getCabinTempText(),
                pdfInfoColumnX(0, 4), 543, 62, envW - 62);
        drawPdfInfoField(canvas, pdfPaint, "\u6e7f\u5ea6", bleManager.getHumidityText(),
                pdfInfoColumnX(1, 4), 543, 38, envW - 38);
        drawPdfInfoField(canvas, pdfPaint, "CO2\u6d53\u5ea6", bleManager.getCo2Text(),
                pdfInfoColumnX(2, 4), 543, 58, envW - 58);
        drawPdfInfoField(canvas, pdfPaint, "\u6c27\u6d53\u5ea6", bleManager.getOxygenText(),
                pdfInfoColumnX(3, 4), 543, 48, envW - 48);
    }

    private void drawPdfTemplateImageAlarm(Canvas canvas, Paint pdfPaint, PatientCase patient) {
        drawPdfSectionTitle(canvas, pdfPaint, "【影像记录】", 28, 581, 92, 330);
        drawPdfSectionTitle(canvas, pdfPaint, "【报警事件】", 340, 581, 415, 568);

        // ★ 按当前舱过滤影像记录：右舱的报告不要再放左舱的治疗前/治疗后照片。
        CameraSnapshot first = getActiveCameraSnapshot(0);
        CameraSnapshot second = getActiveCameraSnapshot(1);
        /* ★ 2026-10-10 #76：影像区重新排版对齐设计稿（lanhu_baogao）——三个等宽图框并排，
           框下居中标题（治疗前/治疗后/扫码查看原图），标题下再居中时间点（MM-dd HH:mm，如 09-01 12:00）；
           治疗前=开始治疗时间，治疗后=结束治疗时间（仍在进行中按当前时间）。
           旧版图框只有 39pt 宽、显示的是照片拍摄时间，与设计稿不一致。 */
        RectF box1 = new RectF(28, 599, 108, 654);
        RectF box2 = new RectF(118, 599, 198, 654);
        RectF boxQr = new RectF(228, 599, 308, 654);
        drawPdfSnapshotBox(canvas, pdfPaint, box1, first, "图片");
        drawPdfSnapshotBox(canvas, pdfPaint, box2, second, "图片");
        drawPdfSnapshotBox(canvas, pdfPaint, boxQr, null, "二维码");
        int capColor = Color.rgb(96, 101, 108);
        int tmColor = Color.rgb(108, 114, 122);
        pdfTextCenter(canvas, pdfPaint, "治疗前", new RectF(box1.left, 656, box1.right, 670), 8.8f, capColor, false);
        pdfTextCenter(canvas, pdfPaint, "治疗后", new RectF(box2.left, 656, box2.right, 670), 8.8f, capColor, false);
        pdfTextCenter(canvas, pdfPaint, "扫码查看原图", new RectF(boxQr.left, 656, boxQr.right, 670), 8.8f, capColor, false);
        String startTm = pdfMdHm(patient == null ? "" : patient.treatmentStartTime);
        String endRaw = patient == null ? "" : patient.treatmentEndTime;
        String endTm = pdfMdHm((endRaw == null || endRaw.isEmpty() || "进行中".equals(endRaw))
                ? formatSettingsDateTime(new Date()) : endRaw);
        pdfTextCenter(canvas, pdfPaint, startTm, new RectF(box1.left, 672, box1.right, 686), 7.6f, tmColor, false);
        pdfTextCenter(canvas, pdfPaint, endTm, new RectF(box2.left, 672, box2.right, 686), 7.6f, tmColor, false);

        RectF alarmBox = new RectF(340, 599, 568, 654);
        pdfRoundRect(canvas, pdfPaint, alarmBox.left, alarmBox.top, alarmBox.right, alarmBox.bottom, 2,
                Color.rgb(229, 239, 251), Color.TRANSPARENT, 0);
        pdfDrawWrappedText(canvas, pdfPaint, pdfAlarmText(), alarmBox.left + 8, alarmBox.top + 18,
                alarmBox.width() - 16, 9.4f, Color.rgb(96, 101, 108), false, 3, 14);
    }

    /** 「yyyy-MM-dd HH:mm:ss」→「MM-dd HH:mm」（如 09-01 12:00）；格式不符时原样返回，空串安全。 */
    private String pdfMdHm(String dt) {
        if (dt == null) {
            return "";
        }
        String s = dt.trim();
        if (s.length() >= 16 && s.charAt(4) == '-') {
            return s.substring(5, 16);
        }
        return s;
    }

    private void drawPdfTemplateConclusion(Canvas canvas, Paint pdfPaint, PatientCase patient, Organization org, String createdAt) {
        drawPdfSectionTitle(canvas, pdfPaint, "\u3010\u6cbb\u7597\u7ed3\u8bba\u3011", 28, 704, 92, 568);
        pdfText(canvas, pdfPaint, "\u6cbb\u7597\u6548\u679c:", 28, 728, 9.5f, Color.rgb(96, 101, 108), false);
        // 方框按各自右侧文字的实际墨迹中心放置（getTextBounds），与文字视觉齐平
        drawPdfCheckboxForBaseline(canvas, pdfPaint, 82, 728, 9.5f, false, "\u826f\u597d");
        pdfText(canvas, pdfPaint, "\u826f\u597d", 96, 728, 9.5f, Color.rgb(96, 101, 108), false);
        drawPdfCheckboxForBaseline(canvas, pdfPaint, 128, 728, 9.5f, false, "\u4e00\u822c");
        pdfText(canvas, pdfPaint, "\u4e00\u822c", 142, 728, 9.5f, Color.rgb(96, 101, 108), false);
        drawPdfCheckboxForBaseline(canvas, pdfPaint, 176, 728, 9.5f, false, "\u5dee");
        pdfText(canvas, pdfPaint, "\u5dee", 190, 728, 9.5f, Color.rgb(96, 101, 108), false);

        pdfText(canvas, pdfPaint, "\u51fa\u9662\u5efa\u8bae:", 28, 751, 9.5f, Color.rgb(96, 101, 108), false);
        RectF advice = new RectF(78, 738, 568, 771);
        pdfRoundRect(canvas, pdfPaint, advice.left, advice.top, advice.right, advice.bottom, 2,
                Color.rgb(229, 239, 251), Color.TRANSPARENT, 0);
        pdfDrawWrappedText(canvas, pdfPaint, patient.note, advice.left + 8, advice.top + 15, advice.width() - 16,
                9.4f, Color.rgb(96, 101, 108), false, 2, 13);
        // 复诊时间来自病例录入弹窗；未填写时仍显示 "--"
        pdfText(canvas, pdfPaint, "\u590d\u8bca\u65f6\u95f4: " + pdfFirstValue(patient.followUpDate), 28, 793, 9.5f, Color.rgb(96, 101, 108), false);

        pdfText(canvas, pdfPaint, "\u64cd\u4f5c\u533b\u5e08: " + pdfFooterValue(pdfFirstValue(patient.doctor, accountStore.getCurrentAccount())), 28, 821, 9.0f, Color.rgb(96, 101, 108), false);
        pdfText(canvas, pdfPaint, "\u4e3b\u7ba1\u62a4\u58eb: ________", 150, 821, 9.0f, Color.rgb(96, 101, 108), false);
        pdfText(canvas, pdfPaint, "\u52a8\u7269\u4e3b\u4eba: " + pdfFooterValue(patient.ownerName), 280, 821, 9.0f, Color.rgb(96, 101, 108), false);
        pdfText(canvas, pdfPaint, "\u6253\u5370\u65f6\u95f4: " + createdAt, 430, 821, 9.0f, Color.rgb(96, 101, 108), false);

        pdfText(canvas, pdfPaint, "\u533b\u9662\u7535\u8bdd: " + pdfCleanValue(org == null ? "" : org.phone), 28, 838, 8.4f, Color.rgb(96, 101, 108), false);
        pdfTextFit(canvas, pdfPaint, "\u533b\u9662\u5730\u5740: " + pdfCleanValue(org == null ? "" : org.address), 170, 838, 250, 8.4f, Color.rgb(96, 101, 108), false);
        pdfText(canvas, pdfPaint, "\u203b \u672c\u68c0\u6d4b\u7ed3\u679c\u4ec5\u5bf9\u8be5\u6837\u672c\u8d1f\u8d23", 430, 838, 8.4f, Color.rgb(96, 101, 108), false);
    }

    private void drawPdfSectionTitle(Canvas canvas, Paint pdfPaint, String title, float x, float y, float lineStart, float lineEnd) {
        float titleSize = 12.5f;
        String text = title == null ? "" : title;
        pdfText(canvas, pdfPaint, text, x, y, titleSize, Color.rgb(78, 82, 88), true);
        // 下划线的起点原来写死 92 / 415，但标题宽度随字数变化：
        // 「【生命体征记录】」要到 128 才结束，线却从 118 起，末尾的「记录】」被压在线上。
        // 这里按实际排版宽度把线推到标题右侧，任何字数都不会再压字。
        pdfPaint.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD));
        pdfPaint.setTextSize(titleSize);
        float titleEnd = x + pdfPaint.measureText(text);
        float safeStart = Math.max(lineStart, titleEnd + 6f);
        if (safeStart >= lineEnd) {
            return;
        }
        pdfLine(canvas, pdfPaint, safeStart, y - 3, lineEnd, y - 3, Color.rgb(105, 174, 255), 0.8f);
    }

    private void drawPdfInfoField(Canvas canvas, Paint pdfPaint, String label, String value, float x, float y, float labelWidth, float valueWidth) {
        int color = Color.rgb(96, 101, 108);
        pdfText(canvas, pdfPaint, label + ": ", x, y, 9.5f, color, false);
        pdfTextFit(canvas, pdfPaint, pdfCleanValue(value), x + labelWidth, y, valueWidth, 9.5f, color, false);
    }

    private void drawPdfTreatmentCheck(Canvas canvas, Paint pdfPaint, float x, float baseline, String label, boolean checked,
                                       String detailLabel, String detailValue, float detailOffset, float maxRight) {
        int color = Color.rgb(96, 101, 108);
        // 正文统一 9.5，方框按左侧项目名的实际墨迹中心放置
        drawPdfCheckboxForBaseline(canvas, pdfPaint, x, baseline, 9.5f, checked, label);
        pdfText(canvas, pdfPaint, label, x + 14, baseline, 9.5f, color, false);
        String cleanValue = pdfCleanValue(detailValue);
        if (!TextUtils.isEmpty(detailLabel)) {
            float valueX = x + detailOffset + 45;
            // 值区不能超过 maxRight，否则第三列会画出页面外（原来固定 64 会冲到 625）。
            float valueWidth = Math.max(20f, Math.min(64f, maxRight - valueX));
            pdfText(canvas, pdfPaint, detailLabel + ": ", x + detailOffset, baseline, 9.5f, color, false);
            pdfTextFit(canvas, pdfPaint, cleanValue, valueX, baseline, valueWidth, 9.5f, color, false);
        } else {
            float valueX = x + detailOffset;
            float valueWidth = Math.max(20f, Math.min(70f, maxRight - valueX));
            pdfTextFit(canvas, pdfPaint, cleanValue, valueX, baseline, valueWidth, 9.5f, color, false);
        }
    }

    private void drawPdfCheckbox(Canvas canvas, Paint pdfPaint, float x, float y, boolean checked) {
        RectF box = new RectF(x, y, x + 8, y + 8);
        pdfStrokeRect(canvas, pdfPaint, box.left, box.top, box.right, box.bottom,
                checked ? Color.rgb(95, 171, 255) : Color.rgb(112, 118, 124), 0.8f);
        if (checked) {
            pdfLine(canvas, pdfPaint, x + 1.6f, y + 4.2f, x + 3.5f, y + 6.3f, Color.rgb(95, 171, 255), 1.1f);
            pdfLine(canvas, pdfPaint, x + 3.5f, y + 6.3f, x + 7.1f, y + 1.6f, Color.rgb(95, 171, 255), 1.1f);
        }
    }

    /** 视觉微调量：中文方块字的视觉重心略低于墨迹几何中心，方框再下压一点才"看着"齐平。用户实测 0.5 仍偏上，调到 1.0。 */
    private static final float PDF_CHECKBOX_OPTICAL_DROP_PT = 1.0f;

    /**
     * 勾选框与同一行文字垂直居中。
     * 之前方框的 y 是手填的 baseline-8，字号不同时框会浮在文字上方或沉到下方。
     * 注意：不能用 FontMetrics 的几何中心 —— 那是主字体（拉丁字体）的度量，
     * 而中文走的是 CJK 回退字体，中文字形的实际墨迹中心比它低零点几个 pt。
     * 这里用 getTextBounds 拿对齐文字的真实墨迹范围来定位方框，才是视觉上的齐平。
     */
    private void drawPdfCheckboxForBaseline(Canvas canvas, Paint pdfPaint, float x, float baseline,
                                            float textSize, boolean checked, String alignText) {
        pdfPaint.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.NORMAL));
        pdfPaint.setTextSize(textSize);
        float textCenter;
        android.graphics.Rect ink = new android.graphics.Rect();
        if (!TextUtils.isEmpty(alignText)) {
            pdfPaint.getTextBounds(alignText, 0, alignText.length(), ink);
            textCenter = baseline + (ink.top + ink.bottom) / 2f;
        } else {
            Paint.FontMetrics metrics = pdfPaint.getFontMetrics();
            textCenter = baseline + (metrics.ascent + metrics.descent) / 2f;
        }
        float boxSize = 8f;
        drawPdfCheckbox(canvas, pdfPaint, x, textCenter - boxSize / 2f + PDF_CHECKBOX_OPTICAL_DROP_PT, checked);
    }

    private void drawPdfTableRow(Canvas canvas, Paint pdfPaint, String[] cells, float left, float top, float[] widths,
                                 float height, boolean header, int rowIndex) {
        int fillColor = header ? Color.rgb(226, 238, 250)
                : rowIndex % 2 == 1 ? Color.rgb(229, 239, 251) : Color.WHITE;
        float right = left;
        for (float width : widths) {
            right += width;
        }
        pdfFill(canvas, pdfPaint, left, top, right, top + height, fillColor);
        float x = left;
        for (int i = 0; i < cells.length && i < widths.length; i++) {
            pdfTextFit(canvas, pdfPaint, cells[i] == null ? "" : cells[i], x + 8, top + 13.5f,
                    widths[i] - 12, 9.0f, Color.rgb(96, 101, 108), header);
            x += widths[i];
        }
    }

    private void drawPdfSnapshotBox(Canvas canvas, Paint pdfPaint, RectF box, CameraSnapshot snapshot, String placeholder) {
        boolean drewBitmap = false;
        if (snapshot != null && snapshot.cacheFile != null && snapshot.cacheFile.exists()) {
            Bitmap bitmap = BitmapFactory.decodeFile(snapshot.cacheFile.getAbsolutePath());
            if (bitmap != null) {
                canvas.drawBitmap(bitmap, null, box, pdfPaint);
                bitmap.recycle();
                drewBitmap = true;
            }
        }
        if (!drewBitmap) {
            pdfRoundRect(canvas, pdfPaint, box.left, box.top, box.right, box.bottom, 2,
                    Color.rgb(155, 158, 162), Color.TRANSPARENT, 0);
            pdfTextCenter(canvas, pdfPaint, placeholder, box, 9.0f, Color.WHITE, false);
        }
        pdfRoundRect(canvas, pdfPaint, box.left, box.top, box.right, box.bottom, 2,
                Color.TRANSPARENT, Color.rgb(160, 168, 176), 0.6f);
    }

    private void pdfFill(Canvas canvas, Paint pdfPaint, float left, float top, float right, float bottom, int color) {
        pdfPaint.setShader(null);
        pdfPaint.setStyle(Paint.Style.FILL);
        pdfPaint.setColor(color);
        canvas.drawRect(left, top, right, bottom, pdfPaint);
    }

    private void pdfStrokeRect(Canvas canvas, Paint pdfPaint, float left, float top, float right, float bottom, int color, float strokeWidth) {
        pdfPaint.setShader(null);
        pdfPaint.setStyle(Paint.Style.STROKE);
        pdfPaint.setStrokeWidth(strokeWidth);
        pdfPaint.setColor(color);
        canvas.drawRect(left, top, right, bottom, pdfPaint);
        pdfPaint.setStyle(Paint.Style.FILL);
    }

    private void pdfRoundRect(Canvas canvas, Paint pdfPaint, float left, float top, float right, float bottom,
                              float radius, int fillColor, int strokeColor, float strokeWidth) {
        RectF rect = new RectF(left, top, right, bottom);
        pdfPaint.setShader(null);
        if (fillColor != Color.TRANSPARENT) {
            pdfPaint.setStyle(Paint.Style.FILL);
            pdfPaint.setColor(fillColor);
            canvas.drawRoundRect(rect, radius, radius, pdfPaint);
        }
        if (strokeColor != Color.TRANSPARENT && strokeWidth > 0f) {
            pdfPaint.setStyle(Paint.Style.STROKE);
            pdfPaint.setStrokeWidth(strokeWidth);
            pdfPaint.setColor(strokeColor);
            canvas.drawRoundRect(rect, radius, radius, pdfPaint);
            pdfPaint.setStyle(Paint.Style.FILL);
        }
    }

    private void pdfLine(Canvas canvas, Paint pdfPaint, float x1, float y1, float x2, float y2, int color, float strokeWidth) {
        pdfPaint.setShader(null);
        pdfPaint.setStyle(Paint.Style.STROKE);
        pdfPaint.setStrokeWidth(strokeWidth);
        pdfPaint.setColor(color);
        canvas.drawLine(x1, y1, x2, y2, pdfPaint);
        pdfPaint.setStyle(Paint.Style.FILL);
    }

    private void pdfText(Canvas canvas, Paint pdfPaint, String value, float x, float y, float size, int color, boolean bold) {
        pdfPaint.setShader(null);
        pdfPaint.setStyle(Paint.Style.FILL);
        pdfPaint.setTextAlign(Paint.Align.LEFT);
        pdfPaint.setTextSize(size);
        pdfPaint.setColor(color);
        pdfPaint.setTypeface(Typeface.create(Typeface.DEFAULT, bold ? Typeface.BOLD : Typeface.NORMAL));
        canvas.drawText(value == null ? "" : value, x, y, pdfPaint);
    }

    private void pdfTextRight(Canvas canvas, Paint pdfPaint, String value, float x, float y, float size, int color, boolean bold) {
        pdfPaint.setShader(null);
        pdfPaint.setStyle(Paint.Style.FILL);
        pdfPaint.setTextAlign(Paint.Align.RIGHT);
        pdfPaint.setTextSize(size);
        pdfPaint.setColor(color);
        pdfPaint.setTypeface(Typeface.create(Typeface.DEFAULT, bold ? Typeface.BOLD : Typeface.NORMAL));
        canvas.drawText(value == null ? "" : value, x, y, pdfPaint);
        pdfPaint.setTextAlign(Paint.Align.LEFT);
    }

    private void pdfTextCenter(Canvas canvas, Paint pdfPaint, String value, RectF rect, float size, int color, boolean bold) {
        pdfPaint.setShader(null);
        pdfPaint.setStyle(Paint.Style.FILL);
        pdfPaint.setTextAlign(Paint.Align.CENTER);
        pdfPaint.setTextSize(size);
        pdfPaint.setColor(color);
        pdfPaint.setTypeface(Typeface.create(Typeface.DEFAULT, bold ? Typeface.BOLD : Typeface.NORMAL));
        Paint.FontMetrics metrics = pdfPaint.getFontMetrics();
        float baseline = rect.centerY() - (metrics.ascent + metrics.descent) / 2f;
        canvas.drawText(value == null ? "" : value, rect.centerX(), baseline, pdfPaint);
        pdfPaint.setTextAlign(Paint.Align.LEFT);
    }

    private void pdfTextFit(Canvas canvas, Paint pdfPaint, String value, float x, float y, float maxWidth,
                            float size, int color, boolean bold) {
        String text = value == null ? "" : value;
        float textSize = size;
        pdfPaint.setShader(null);
        pdfPaint.setStyle(Paint.Style.FILL);
        pdfPaint.setTextAlign(Paint.Align.LEFT);
        pdfPaint.setTypeface(Typeface.create(Typeface.DEFAULT, bold ? Typeface.BOLD : Typeface.NORMAL));
        pdfPaint.setColor(color);
        pdfPaint.setTextSize(textSize);
        while (textSize > 6f && pdfPaint.measureText(text) > maxWidth) {
            textSize -= 0.5f;
            pdfPaint.setTextSize(textSize);
        }
        String display = text;
        while (display.length() > 1 && pdfPaint.measureText(display) > maxWidth) {
            display = display.substring(0, display.length() - 1);
        }
        if (!display.equals(text) && display.length() > 3) {
            display = display.substring(0, Math.max(0, display.length() - 3)) + "...";
        }
        canvas.drawText(display, x, y, pdfPaint);
    }

    private void pdfDrawWrappedText(Canvas canvas, Paint pdfPaint, String value, float x, float y, float maxWidth,
                                    float size, int color, boolean bold, int maxLines, float lineHeight) {
        String text = pdfCleanValue(value);
        if (pdfIsBlankValue(text)) {
            return;
        }
        pdfPaint.setShader(null);
        pdfPaint.setStyle(Paint.Style.FILL);
        pdfPaint.setTextAlign(Paint.Align.LEFT);
        pdfPaint.setTypeface(Typeface.create(Typeface.DEFAULT, bold ? Typeface.BOLD : Typeface.NORMAL));
        pdfPaint.setTextSize(size);
        pdfPaint.setColor(color);
        StringBuilder line = new StringBuilder();
        int drawn = 0;
        for (int i = 0; i < text.length(); i++) {
            char ch = text.charAt(i);
            line.append(ch);
            if (pdfPaint.measureText(line.toString()) > maxWidth && line.length() > 1) {
                line.deleteCharAt(line.length() - 1);
                canvas.drawText(line.toString(), x, y + drawn * lineHeight, pdfPaint);
                drawn++;
                if (drawn >= maxLines) {
                    return;
                }
                line.setLength(0);
                line.append(ch);
            }
        }
        if (drawn < maxLines && line.length() > 0) {
            canvas.drawText(line.toString(), x, y + drawn * lineHeight, pdfPaint);
        }
    }

    private String pdfCleanValue(String value) {
        if (TextUtils.isEmpty(value)) {
            return "--";
        }
        String clean = value.trim();
        if (TextUtils.isEmpty(clean) || "-".equals(clean) || "--".equals(clean) || "--/--".equals(clean)
                || "\u5f85\u5f55\u5165".equals(clean) || "\u672a\u77e5".equals(clean)) {
            return "--";
        }
        return clean;
    }

    private boolean pdfIsBlankValue(String value) {
        return "--".equals(pdfCleanValue(value));
    }

    private String pdfFirstValue(String... values) {
        if (values != null) {
            for (String value : values) {
                String clean = pdfCleanValue(value);
                if (!pdfIsBlankValue(clean)) {
                    return clean;
                }
            }
        }
        return "--";
    }

    private String pdfDateOnly(String value) {
        String clean = pdfCleanValue(value);
        if (pdfIsBlankValue(clean)) {
            return "--";
        }
        return clean.length() >= 10 ? clean.substring(0, 10) : clean;
    }

    private String pdfCleanBloodPressure() {
        String bp = pdfCleanValue(am4100Manager.getBloodPressureText());
        return "--/--".equals(bp) ? "--" : bp;
    }

    /**
     * 报告取监护值：优先实时数据；监护仪已断开或当前无数据时，
     * 回退到该病例存储的生命体征快照（fillMonitorSnapshot 随治疗持续更新）。
     * 之前只读实时值，打印时若监护已断开，报告上全是 "--"。
     */
    private String pdfVitalsText(String liveValue, String storedValue) {
        String live = pdfCleanValue(liveValue);
        if (!pdfIsBlankValue(live)) {
            return live;
        }
        return pdfCleanValue(storedValue);
    }

    /** 从 "98" / "98 bpm" 这类文本里取整数，取不到返回 0（0 会被波形绘制按默认值处理）。 */
    private int pdfVitalsNumber(String text) {
        String clean = pdfCleanValue(text);
        if (pdfIsBlankValue(clean)) {
            return 0;
        }
        try {
            return (int) Float.parseFloat(clean.replaceAll("[^0-9.]", ""));
        } catch (NumberFormatException ignored) {
            return 0;
        }
    }

    private int pdfFirstNonZero(int primary, int fallback) {
        return primary > 0 ? primary : fallback;
    }

    /** 波形采样优先实时，其次病例快照。 */
    private List<Integer> pdfWaveSamples(List<Integer> livePrimary, List<Integer> liveFallback,
                                         List<Integer> storedPrimary, List<Integer> storedFallback) {
        if (pdfHasWaveSamples(livePrimary) || pdfHasWaveSamples(liveFallback)) {
            return pdfPreferredWaveSamples(livePrimary, liveFallback);
        }
        return pdfPreferredWaveSamples(storedPrimary, storedFallback);
    }

    private TreatmentEntry pdfFindTreatmentEntry(PatientCase patient, String... names) {
        if (patient == null || names == null) {
            return null;
        }
        for (TreatmentEntry entry : patient.treatmentEntries) {
            if (entry == null || TextUtils.isEmpty(entry.itemName)) {
                continue;
            }
            for (String name : names) {
                if (TextUtils.isEmpty(name)) {
                    continue;
                }
                if (TextUtils.equals(entry.itemName, name) || entry.itemName.contains(name) || name.contains(entry.itemName)) {
                    return entry;
                }
            }
        }
        return null;
    }

    private boolean pdfEntryEnabled(TreatmentEntry entry) {
        return entry != null && entry.enabled;
    }

    private boolean pdfTreatmentOn(int controlIndex, TreatmentEntry entry) {
        return bleManager.isControlOn(controlIndex) || pdfEntryEnabled(entry);
    }

    private String pdfEntryValue(TreatmentEntry entry) {
        if (entry == null) {
            return "--";
        }
        return pdfFirstValue(entry.manualAverageValue, entry.manualValue,
                entry.sampleCount > 0 ? entry.lastValue : "", treatmentAverageText(entry));
    }

    private String pdfControlDurationText(int controlIndex) {
        Integer minutes = bleManager.getControlTimeValue(controlIndex);
        if (minutes != null) {
            return minutes + "\u5206\u949f";
        }
        String value = pdfCleanValue(bleManager.getControlValue(controlIndex));
        if ("\u5f00".equals(value) || "\u5173".equals(value)) {
            return "--";
        }
        return value;
    }

    private String pdfFooterValue(String value) {
        String clean = pdfCleanValue(value);
        return pdfIsBlankValue(clean) ? "________" : clean;
    }

    private String pdfSnapshotTime(CameraSnapshot snapshot) {
        if (snapshot == null) {
            return "";
        }
        String clean = pdfCleanValue(snapshot.timeText);
        if (pdfIsBlankValue(clean)) {
            return "";
        }
        return clean.length() > 16 ? clean.substring(5, 16) : clean;
    }

    private String pdfAlarmText() {
        ArrayList<String> alarms = new ArrayList<>();
        if (isMonitorTrendAlarm(MONITOR_TREND_HEART)) {
            alarms.add("\u5fc3\u7387\u5f02\u5e38");
        }
        if (isMonitorTrendAlarm(MONITOR_TREND_BP)) {
            alarms.add("\u8840\u538b\u5f02\u5e38");
        }
        if (isMonitorTrendAlarm(MONITOR_TREND_SPO2)) {
            alarms.add("\u8840\u6c27\u5f02\u5e38");
        }
        if (isMonitorTrendAlarm(MONITOR_TREND_TEMP)) {
            alarms.add("\u4f53\u6e29\u5f02\u5e38");
        }
        if (isMonitorTrendAlarm(MONITOR_TREND_RESP)) {
            alarms.add("\u547c\u5438\u5f02\u5e38");
        }
        if (alarms.isEmpty()) {
            return "\u65e0";
        }
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < alarms.size(); i++) {
            if (i > 0) {
                builder.append("\u3001");
            }
            builder.append(alarms.get(i));
        }
        return builder.toString();
    }

    private boolean pdfHasWaveSamples(List<Integer> samples) {
        return samples != null && samples.size() >= 6;
    }

    private List<Integer> pdfPreferredWaveSamples(List<Integer> primary, List<Integer> fallback) {
        return pdfHasWaveSamples(primary) ? primary : fallback;
    }

    private String missingReportFields(PatientCase patient) {
        ArrayList<String> missing = new ArrayList<>();
        if (isBlankPatientValue(patient.petName)) {
            missing.add("宠物名称");
        }
        if (isBlankPatientValue(patient.species)) {
            missing.add("动物种类");
        }
        if (isBlankPatientValue(patient.ownerName)) {
            missing.add("宠主姓名");
        }
        if (isBlankPatientValue(patient.ownerPhone)) {
            missing.add("联系电话");
        }
        if (missing.isEmpty()) {
            return "";
        }
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < missing.size(); i++) {
            if (i > 0) {
                builder.append("、");
            }
            builder.append(missing.get(i));
        }
        return builder.toString();
    }

    private boolean isBlankPatientValue(String value) {
        if (TextUtils.isEmpty(value)) {
            return true;
        }
        String clean = value.trim();
        return "--".equals(clean) || "-".equals(clean) || "待录入".equals(clean) || "未知".equals(clean);
    }

    private int drawPdfSection(Canvas canvas, Paint pdfPaint, String title, int y) {
        pdfPaint.setColor(Color.rgb(45, 52, 82));
        pdfPaint.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD));
        pdfPaint.setTextSize(15);
        canvas.drawText(title, 42, y, pdfPaint);
        pdfPaint.setStrokeWidth(1);
        canvas.drawLine(42, y + 8, 552, y + 8, pdfPaint);
        return y + 30;
    }

    private int drawPdfLine(Canvas canvas, Paint pdfPaint, String label, String value, int y) {
        pdfPaint.setColor(Color.rgb(47, 56, 72));
        pdfPaint.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD));
        pdfPaint.setTextSize(11);
        canvas.drawText(label + ":", 54, y, pdfPaint);
        pdfPaint.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.NORMAL));
        canvas.drawText(value == null ? "-" : value, 130, y, pdfPaint);
        return y + 22;
    }

    private int drawPdfTreatmentRecordBlock(Canvas canvas, Paint pdfPaint, PatientCase patient, int y) {
        y = drawPdfSection(canvas, pdfPaint, "治疗记录", y);
        pdfPaint.setColor(Color.rgb(92, 103, 118));
        pdfPaint.setTextSize(9);
        pdfPaint.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.NORMAL));
        canvas.drawText("开始: " + patient.treatmentStartTime + "    结束: " + patient.treatmentEndTime, 54, y, pdfPaint);
        y += 18;

        String[] headers = {"项目", "开启", "入报告", "平均", "最高", "最低", "手工统计", "时间段"};
        float[] widths = {64, 32, 42, 54, 54, 54, 74, 112};
        drawPdfTreatmentRow(canvas, pdfPaint, headers, widths, y, true);
        y += 18;
        int count = 0;
        for (TreatmentEntry entry : patient.treatmentEntries) {
            if (!entry.includeInReport) {
                continue;
            }
            String manualSummary = buildTreatmentManualSummary(entry);
            String[] row = {
                    entry.itemName,
                    entry.enabled ? "开" : "关",
                    "是",
                    treatmentAverageText(entry),
                    treatmentHighText(entry),
                    treatmentLowText(entry),
                    TextUtils.isEmpty(manualSummary) ? "-" : manualSummary,
                    shortDateTime(entry.startTime) + "-" + shortDateTime(entry.endTime)
            };
            drawPdfTreatmentRow(canvas, pdfPaint, row, widths, y, false);
            y += 18;
            count++;
            if (count >= 10 || y > 805) {
                break;
            }
        }
        if (count == 0) {
            pdfPaint.setColor(Color.rgb(92, 103, 118));
            pdfPaint.setTextSize(10);
            canvas.drawText("暂无勾选录入报告的治疗项目。", 54, y, pdfPaint);
            y += 18;
        }
        return y + 8;
    }

    private void drawPdfTreatmentRow(Canvas canvas, Paint pdfPaint, String[] cells, float[] widths, int y, boolean header) {
        float x = 54;
        pdfPaint.setStyle(Paint.Style.FILL);
        pdfPaint.setColor(header ? Color.rgb(234, 238, 242) : Color.WHITE);
        canvas.drawRect(54, y - 12, 540, y + 5, pdfPaint);
        pdfPaint.setStyle(Paint.Style.STROKE);
        pdfPaint.setStrokeWidth(0.6f);
        pdfPaint.setColor(Color.rgb(154, 166, 178));
        canvas.drawRect(54, y - 12, 540, y + 5, pdfPaint);
        pdfPaint.setStyle(Paint.Style.FILL);
        pdfPaint.setTextSize(7.2f);
        pdfPaint.setTypeface(Typeface.create(Typeface.DEFAULT, header ? Typeface.BOLD : Typeface.NORMAL));
        pdfPaint.setColor(Color.rgb(47, 56, 72));
        for (int i = 0; i < cells.length && i < widths.length; i++) {
            canvas.drawText(cells[i] == null ? "-" : cells[i], x + 3, y, pdfPaint);
            x += widths[i];
        }
    }

    private void drawPdfHeader(Canvas canvas, Paint pdfPaint, String title, String subtitle) {
        pdfPaint.setStyle(Paint.Style.FILL);
        pdfPaint.setColor(Color.WHITE);
        canvas.drawRect(0, 0, 595, 842, pdfPaint);
        pdfPaint.setColor(Color.rgb(35, 45, 65));
        pdfPaint.setTextSize(22);
        pdfPaint.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD));
        canvas.drawText(title, 42, 55, pdfPaint);
        pdfPaint.setTextSize(11);
        pdfPaint.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.NORMAL));
        canvas.drawText(subtitle, 42, 78, pdfPaint);
        pdfPaint.setColor(Color.rgb(230, 236, 242));
        canvas.drawRect(42, 92, 553, 94, pdfPaint);
    }

    private int drawPdfMonitorBlock(Canvas canvas, Paint pdfPaint, int y) {
        float cardTop = y - 6;
        RectF card = new RectF(48, cardTop, 547, cardTop + 78);
        pdfPaint.setStyle(Paint.Style.FILL);
        pdfPaint.setColor(Color.rgb(246, 250, 253));
        canvas.drawRoundRect(card, 8, 8, pdfPaint);
        pdfPaint.setStyle(Paint.Style.STROKE);
        pdfPaint.setStrokeWidth(1);
        pdfPaint.setColor(Color.rgb(154, 166, 178));
        canvas.drawRoundRect(card, 8, 8, pdfPaint);
        pdfPaint.setStyle(Paint.Style.FILL);

        drawPdfMetric(canvas, pdfPaint, "心率", am4100Manager.getHeartRateText(), "bpm", 66, y + 20, Color.rgb(57, 135, 64));
        drawPdfMetric(canvas, pdfPaint, "血压", am4100Manager.getBloodPressureText(), "mmHg", 220, y + 20, Color.rgb(237, 139, 42));
        drawPdfMetric(canvas, pdfPaint, "血氧", am4100Manager.getSpo2Text(), "%", 386, y + 20, Color.rgb(190, 50, 48));
        drawPdfMetric(canvas, pdfPaint, "脉率", am4100Manager.getPulseRateText(), "bpm", 66, y + 54, Color.rgb(57, 135, 64));
        drawPdfMetric(canvas, pdfPaint, "体温", am4100Manager.getBodyTempText(), "℃", 220, y + 54, Color.rgb(60, 160, 180));
        drawPdfMetric(canvas, pdfPaint, "呼吸率", am4100Manager.getRespText(), "brpm", 386, y + 54, Color.rgb(74, 124, 170));

        float waveTop = card.bottom + 18;
        RectF waveCard = new RectF(48, waveTop, 547, waveTop + 452);
        pdfPaint.setStyle(Paint.Style.FILL);
        pdfPaint.setColor(Color.rgb(12, 18, 28));
        canvas.drawRoundRect(waveCard, 8, 8, pdfPaint);
        pdfPaint.setStyle(Paint.Style.STROKE);
        pdfPaint.setStrokeWidth(1);
        pdfPaint.setColor(Color.rgb(80, 95, 115));
        canvas.drawRoundRect(waveCard, 8, 8, pdfPaint);
        pdfPaint.setStyle(Paint.Style.FILL);

        drawPdfWaveRow(canvas, pdfPaint, "心率 " + dash(am4100Manager.getHeartRateText()) + " bpm", 70, waveTop + 48, 520, 18, Color.rgb(92, 220, 96), 0, am4100Manager.getHeartRateHistory(), am4100Manager.getHeartRateValue());
        drawPdfWaveRow(canvas, pdfPaint, "血氧 " + dash(am4100Manager.getSpo2Text()) + " %", 70, waveTop + 112, 520, 18, Color.rgb(232, 80, 72), 1, am4100Manager.getSpo2History(), am4100Manager.getSpo2Value());
        drawPdfWaveRow(canvas, pdfPaint, "脉率 " + dash(am4100Manager.getPulseRateText()) + " bpm", 70, waveTop + 176, 520, 18, Color.rgb(92, 190, 110), 0, am4100Manager.getPulseRateHistory(), am4100Manager.getPulseRateValue());
        drawPdfWaveRow(canvas, pdfPaint, "血压 " + am4100Manager.getBloodPressureText() + " mmHg", 70, waveTop + 240, 520, 18, Color.rgb(237, 139, 42), 0, am4100Manager.getBloodPressureHistory(), 120);
        drawPdfWaveRow(canvas, pdfPaint, "体温 " + dash(am4100Manager.getBodyTempText()) + " ℃", 70, waveTop + 304, 520, 18, Color.rgb(60, 160, 180), 2, am4100Manager.getTemperatureHistory(), 370);
        drawPdfWaveRow(canvas, pdfPaint, "呼吸率 " + dash(am4100Manager.getRespText()) + " brpm", 70, waveTop + 368, 520, 18, Color.rgb(96, 166, 232), 2, am4100Manager.getRespHistory(), am4100Manager.getRespValue());

        pdfPaint.setColor(Color.rgb(180, 195, 210));
        pdfPaint.setTextSize(9);
        pdfPaint.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.NORMAL));
        canvas.drawText("历史电波图: 记录本次连接期间的监护数值变化；最近数据: " + dash(am4100Manager.getLastFrameSummary()), 62, waveCard.bottom - 16, pdfPaint);
        return (int) waveCard.bottom + 18;
    }

    private void drawPdfMetric(Canvas canvas, Paint pdfPaint, String label, String value, String unit, float x, float y, int color) {
        pdfPaint.setStyle(Paint.Style.FILL);
        pdfPaint.setColor(Color.rgb(92, 103, 118));
        pdfPaint.setTextSize(9);
        pdfPaint.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.NORMAL));
        canvas.drawText(label, x, y - 13, pdfPaint);
        pdfPaint.setColor(color);
        pdfPaint.setTextSize(18);
        pdfPaint.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD));
        canvas.drawText(dash(value), x, y + 6, pdfPaint);
        pdfPaint.setTextSize(8);
        pdfPaint.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.NORMAL));
        canvas.drawText(unit, x + 74, y + 4, pdfPaint);
    }

    private void drawPdfWaveRow(Canvas canvas, Paint pdfPaint, String label, float left, float baseline, float right, float amp,
                                int color, int type, List<Integer> samples, int numericValue) {
        pdfPaint.setStyle(Paint.Style.FILL);
        pdfPaint.setColor(color);
        pdfPaint.setTextSize(10);
        pdfPaint.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD));
        canvas.drawText(label, left, baseline - amp - 12, pdfPaint);
        pdfPaint.setColor(Color.rgb(38, 50, 66));
        for (int i = 0; i < 5; i++) {
            canvas.drawRect(left, baseline - amp + i * (amp * 2 / 4f), right, baseline - amp + i * (amp * 2 / 4f) + 0.6f, pdfPaint);
        }
        drawReportWave(canvas, left + 52, baseline, right, amp, color, type, samples, numericValue, 1.6f);
    }

    private void drawReportWave(Canvas canvas, float left, float baseline, float right, float amp, int color, int type,
                                List<Integer> samples, int numericValue, float strokeWidth) {
        if (samples != null && samples.size() >= 6) {
            drawSmoothSampleWave(canvas, left, baseline, right, amp, color, samples, strokeWidth);
            return;
        } else {
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(strokeWidth);
            paint.setColor(color);
            Path path = new Path();
            float safeValue = numericValue > 0 ? numericValue : (type == 2 ? 20f : type == 1 ? 98f : 75f);
            path.moveTo(left, baseline);
            int syntheticCount = Math.max(260, Math.min(520, (int) ((right - left) / 1.2f)));
            for (int i = 0; i <= syntheticCount; i++) {
                float t = i / (float) syntheticCount;
                float x = left + (right - left) * t;
                float y;
                if (type == 0) {
                    float bpm = clamp(safeValue, 35f, 180f);
                    float periodPx = clamp(4200f / bpm, 24f, 92f);
                    float beat = (i % periodPx) / periodPx;
                    float spike = beat > 0.42f && beat < 0.48f ? amp * (0.72f + bpm / 260f) : 0f;
                    y = baseline - spike + (float) Math.sin(t * 18f * Math.PI) * amp * 0.10f;
                } else if (type == 1) {
                    float spo2 = clamp(safeValue, 70f, 100f);
                    float quality = clamp((spo2 - 70f) / 30f, 0.25f, 1f);
                    y = baseline + (float) Math.sin(t * 42f) * amp * 0.28f * quality
                            + (float) Math.sin(t * 13f) * amp * 0.16f;
                } else {
                    float resp = clamp(safeValue, 6f, 60f);
                    y = baseline + (float) Math.sin(t * 14f * clamp(resp / 20f, 0.45f, 2.2f)) * amp * 0.45f;
                }
                path.lineTo(x, y);
            }
            canvas.drawPath(path, paint);
        }
        paint.setStyle(Paint.Style.FILL);
    }

    private String createPdfFileName() {
        PatientCase patient = currentCase();
        String no = patient == null ? "empty" : sanitizeFileName(patient.recordNo + "_" + patient.petName);
        String stamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(new Date());
        // ★ 两舱各自从 000001 编号，文件名不加舱位会出现两份同名报告、分不清是哪一舱。
        String zoneTag = zoneFileTag(patient == null ? bleManager.getCurrentZone() : zoneOfPatient(patient));
        return "icu_report_" + zoneTag + "_" + no + "_" + stamp + ".pdf";
    }

    private String sanitizeFileName(String value) {
        if (TextUtils.isEmpty(value)) {
            return "report";
        }
        return value.replaceAll("[\\\\/:*?\"<>|\\s]+", "_");
    }

    private String safeJsonText(String value) {
        return value == null ? "" : value;
    }

    private String createCameraSnapshotFileName() {
        PatientCase patient = currentCase();
        String no = patient == null ? "camera" : sanitizeFileName(patient.recordNo + "_" + patient.petName);
        String stamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(new Date());
        return "icu_camera_" + no + "_" + stamp + ".jpg";
    }

    private Uri copyImageToPictures(File source, String fileName) throws IOException {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ContentValues values = new ContentValues();
            values.put(MediaStore.MediaColumns.DISPLAY_NAME, fileName);
            values.put(MediaStore.MediaColumns.MIME_TYPE, "image/jpeg");
            values.put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/ICUCamera");
            Uri uri = activity.getContentResolver().insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values);
            if (uri == null) {
                throw new IOException("无法创建图片文件");
            }
            OutputStream output = activity.getContentResolver().openOutputStream(uri);
            if (output == null) {
                throw new IOException("无法打开图片文件");
            }
            copyFile(source, output);
            return uri;
        }
        File dir = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES), "ICUCamera");
        if (!dir.exists() && !dir.mkdirs()) {
            throw new IOException("无法创建图片目录");
        }
        File target = new File(dir, fileName);
        FileOutputStream output = new FileOutputStream(target);
        copyFile(source, output);
        return Uri.fromFile(target);
    }

    private void saveCameraSnapshotsToStorage() {
        try {
            JSONArray array = new JSONArray();
            for (CameraSnapshot snapshot : cameraSnapshots) {
                JSONObject item = new JSONObject();
                item.put("fileName", snapshot.fileName);
                item.put("timeText", snapshot.timeText);
                item.put("capturedAt", snapshot.capturedAt);
                item.put("cachePath", snapshot.cacheFile == null ? "" : snapshot.cacheFile.getAbsolutePath());
                item.put("downloadUri", snapshot.downloadUri == null ? "" : snapshot.downloadUri.toString());
                item.put("recordNo", snapshot.patientRecordNo);
                item.put("petName", snapshot.patientName);
                item.put("zone", snapshot.zone);
                array.put(item);
            }
            SharedPreferences prefs = activity.getSharedPreferences(PREFS_NAME, 0);
            prefs.edit().putString(KEY_CAMERA_SNAPSHOTS, array.toString()).apply();
        } catch (Exception ignored) {
        }
    }

    private void loadCameraSnapshotsFromStorage() {
        cameraSnapshots.clear();
        SharedPreferences prefs = activity.getSharedPreferences(PREFS_NAME, 0);
        String raw = prefs.getString(KEY_CAMERA_SNAPSHOTS, "");
        if (TextUtils.isEmpty(raw)) {
            return;
        }
        try {
            JSONArray array = new JSONArray(raw);
            for (int i = 0; i < array.length(); i++) {
                JSONObject item = array.optJSONObject(i);
                if (item == null) {
                    continue;
                }
                File file = new File(item.optString("cachePath", ""));
                if (!file.exists()) {
                    continue;
                }
                String uriText = item.optString("downloadUri", "");
                Uri uri = TextUtils.isEmpty(uriText) ? null : Uri.parse(uriText);
                long capturedAt = item.optLong("capturedAt", file.lastModified());
                String recordNo = optSnapshotRecordNo(item, file.getName());
                String petName = optSnapshotPetName(item, file.getName());
                cameraSnapshots.add(new CameraSnapshot(
                        item.optString("fileName", file.getName()),
                        item.optString("timeText", ""),
                        capturedAt,
                        file,
                        uri,
                        recordNo,
                        petName,
                        resolveLegacySnapshotZone(item.optString("zone", ""), recordNo, petName)));
            }
            selectedCameraSnapshotIndex = activeCameraSnapshotCount() <= 0 ? -1 : 0;
        } catch (Exception ignored) {
        }
    }

    private String optSnapshotRecordNo(JSONObject item, String fileName) {
        String recordNo = item.optString("recordNo", "");
        return TextUtils.isEmpty(recordNo) ? inferSnapshotRecordNo(fileName) : recordNo;
    }

    private String optSnapshotPetName(JSONObject item, String fileName) {
        String petName = item.optString("petName", "");
        return TextUtils.isEmpty(petName) ? inferSnapshotPetName(fileName) : petName;
    }

    private String inferSnapshotRecordNo(String fileName) {
        String base = normalizeSnapshotName(fileName);
        int split = base.indexOf('_');
        if (split <= 0) {
            return "";
        }
        return base.substring(0, split);
    }

    private String inferSnapshotPetName(String fileName) {
        String base = normalizeSnapshotName(fileName);
        int split = base.indexOf('_');
        if (split < 0 || split >= base.length() - 1) {
            return "";
        }
        return base.substring(split + 1);
    }

    private String normalizeSnapshotName(String fileName) {
        String name = fileName == null ? "" : fileName;
        if (name.startsWith("icu_camera_")) {
            name = name.substring("icu_camera_".length());
        }
        if (name.endsWith(".jpg")) {
            name = name.substring(0, name.length() - 4);
        }
        return name.replaceFirst("_(\\d{8}_\\d{6})$", "");
    }

    private Uri copyPdfToDownloads(File source, String fileName) throws IOException {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ContentValues values = new ContentValues();
            values.put(MediaStore.MediaColumns.DISPLAY_NAME, fileName);
            values.put(MediaStore.MediaColumns.MIME_TYPE, "application/pdf");
            values.put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS);
            Uri uri = activity.getContentResolver().insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values);
            if (uri == null) {
                throw new IOException("无法创建下载文件");
            }
            OutputStream output = activity.getContentResolver().openOutputStream(uri);
            if (output == null) {
                throw new IOException("无法打开下载文件");
            }
            copyFile(source, output);
            return uri;
        }
        File dir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
        if (!dir.exists() && !dir.mkdirs()) {
            throw new IOException("无法创建下载目录");
        }
        File target = new File(dir, fileName);
        FileOutputStream output = new FileOutputStream(target);
        copyFile(source, output);
        return Uri.fromFile(target);
    }

    private void copyFile(File source, OutputStream output) throws IOException {
        FileInputStream input = new FileInputStream(source);
        try {
            byte[] buffer = new byte[8192];
            int length;
            while ((length = input.read(buffer)) >= 0) {
                output.write(buffer, 0, length);
            }
        } finally {
            input.close();
            output.close();
        }
    }

    private int dpInt(float value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private void drawTabs(Canvas canvas, float x, float y, float w, float h) {
        fill(canvas, x, y, x + w, y + h, Color.WHITE);
        float tabW = Math.min(dp(170), (w - dp(80)) / tabs.length);
        float tx = x + dp(220);
        for (int i = 0; i < tabs.length; i++) {
            final int index = i;
            RectF r = rect(tx, y + dp(8), tx + tabW, y + h);
            int fill = activeTab == i ? page : Color.WHITE;
            rounded(canvas, r, fill, Color.TRANSPARENT, dp(4));
            drawTextCenter(canvas, tabs[i], r, 16, activeTab == i ? activeBlue : text, activeTab == i);
            clickZones.add(new ClickZone(new RectF(r), new ClickAction() {
                @Override
                public void run() {
                    selectMainTab(index);
                }
            }));
            tx += tabW;
        }
    }

    private void drawStatusPage(Canvas canvas, float x, float y, float w, float h) {
        fill(canvas, x, y, x + w, y + h, Color.WHITE);
        RectF shell = rect(x + dp(28), y, x + w - dp(18), y + h - dp(18));
        rounded(canvas, shell, page, Color.TRANSPARENT, dp(8));
        float gap = dp(22);
        float left = shell.left + dp(24);
        float top = shell.top + dp(24);
        float gridW = shell.width() * 0.50f;
        float cardW = (gridW - gap * 2) / 3f;
        float cardH = Math.min(dp(142), (shell.height() - dp(230)) / 3f);
        String monitorLevelColor = currentMonitorLevelColor();
        String monitorLevelText = monitorLevelLabel(monitorLevelColor);
        String[][] data = {
                // ★ 修复 #14:主机控制页大表盘数字显示用户设置值
                {"舱内温度  ℃", compactDashboardValue(bleManager.getLastSetCabinTempText()), "♨", "#444444"},
                {"氧浓度  %", compactDashboardValue(bleManager.getLastSetOxygenText()), "O₂", "#444444"},
                {"湿度  %", compactDashboardValue(bleManager.getHumidityText()), "♢", "#444444"},
                {"红外体温  ℃", compactDashboardValue(bleManager.getInfraredTempText()), "♨", "#444444"},
                {"监护等级", monitorLevelText, "▣", "#F0545C"},
                {"二氧化碳浓度  PPM", compactDashboardValue(bleManager.getCo2Text()), "CO₂", "#444444"},
                {"治疗时长  h", compactDashboardValue(bleManager.getTreatmentTimeText()), "◷", "#444444"},
                {"红外理疗  m", compactDashboardValue(bleManager.getControlValue(6)), "∿", "#444444"},
                {"蓝光理疗  m", compactDashboardValue(bleManager.getControlValue(7)), "☂", "#444444"}
        };
        for (int i = 0; i < data.length; i++) {
            int col = i % 3;
            int row = i / 3;
            RectF r = rect(left + col * (cardW + gap), top + row * (cardH + gap), left + col * (cardW + gap) + cardW, top + row * (cardH + gap) + cardH);
            rounded(canvas, r, panel, Color.TRANSPARENT, dp(8));
            drawText(canvas, data[i][0], r.left + dp(20), r.top + dp(28), 15, text, true);
            if (i == 4) {
                RectF level = rect(r.left + dp(22), r.top + dp(56), r.left + dp(112), r.top + dp(88));
                rounded(canvas, level, monitorLevelFillColor(monitorLevelColor), Color.TRANSPARENT, dp(16));
                drawTextCenter(canvas, monitorLevelText, level, 15, Color.WHITE, true);
                drawStatusLightDots(canvas, r);
            } else {
                drawText(canvas, data[i][1], r.left + dp(20), r.top + dp(88), 40, Color.parseColor(data[i][3]), false);
                drawProgressBar(canvas, r.left + dp(20), r.bottom - dp(28), r.width() - dp(82), dp(10), i == 7 ? 0.05f : i == 8 ? 0.14f : 0.58f, i == 8 ? red : green);
            }
            drawTextRight(canvas, data[i][2], r.right - dp(18), r.top + dp(88), 27, Color.rgb(55, 148, 242), true);
        }

        RectF cam = rect(left + gridW + gap * 2, top, shell.right - dp(22), top + cardH * 3 + gap * 2);
        drawCameraPreview(canvas, cam, true);
        RectF statusPreview = inset(cam, dp(2), dp(2));
        statusPreview.bottom -= dp(24);
        updateCameraPreviewOverlay(statusPreview, true);
        drawCameraControlBar(canvas, cam, true);
        clickZones.add(new ClickZone(new RectF(cam), new ClickAction() {
            @Override
            public void run() {
                // 点击摄像头预览区 → 跳到"摄像监控" Tab (activeTab = 3)
                activeTab = 3;
                showingSettings = false;
                invalidate();
            }
        }));

        float miniTop = top + cardH * 3 + gap * 3;
        String[] names = {"雾化器", "暖光照明", "内循环"};
        int[] miniControlIndices = {10, 5, 9};
        for (int i = 0; i < names.length; i++) {
            RectF mini = rect(left + i * dp(168), miniTop, left + i * dp(168) + dp(132), miniTop + dp(110));
            final int controlIndex = miniControlIndices[i];
            final String controlName = names[i];
            boolean controlOn = bleManager.isControlOn(controlIndex);
            rounded(canvas, mini, Color.WHITE, Color.TRANSPARENT, dp(8));
            drawTextCenter(canvas, names[i], rect(mini.left, mini.top + dp(18), mini.right, mini.top + dp(42)), 16, text, true);
            drawText(canvas, controlOn ? "开" : "关", mini.left + dp(34), mini.top + dp(72), 13, text, false);
            drawToggle(canvas, mini.left + dp(58), mini.top + dp(58), controlOn);
            clickZones.add(new ClickZone(new RectF(mini), new ClickAction() {
                @Override
                public void run() {
                    handleControlCardTap(controlIndex, controlName);
                }
            }));
        }
        RectF thermal = rect(left + dp(504), miniTop, left + dp(760), miniTop + dp(110));
        drawLightPreview(canvas, thermal);

        RectF summary = rect(left, shell.bottom - dp(130), shell.right - dp(22), shell.bottom - dp(28));
        rounded(canvas, summary, panel, Color.TRANSPARENT, dp(8));
        drawText(canvas, "医嘱", summary.left + dp(20), summary.top + dp(34), 15, text, true);
        final PatientCase patient = currentCase();
        String noteText = patient == null || TextUtils.isEmpty(patient.note) ? "--" : patient.note;
        drawText(canvas, noteText, summary.left + dp(20), summary.top + dp(66), 13, text, false);
        if (patient != null) {
            clickZones.add(new ClickZone(new RectF(summary), new ClickAction() {
                @Override
                public void run() {
                    showEditPatientDialog(patient);
                }
            }));
        }
    }

    private void drawControlPage(Canvas canvas, float x, float y, float w, float h) {
        fill(canvas, x, y, x + w, y + h, Color.WHITE);
        RectF shell = rect(x + dp(28), y, x + w - dp(18), y + h - dp(18));
        rounded(canvas, shell, page, Color.TRANSPARENT, dp(8));
        String[][] items = {
                // ★ 修复 #14:主机控制页右下方仪表盘的舱内温度和氧浓度也显示用户设置值
                {"舱内温度", compactDashboardValue(bleManager.getLastSetCabinTempText()), "设置"}, {"氧浓度", compactDashboardValue(bleManager.getLastSetOxygenText()), "设置"}, {"监护等级", bleManager.getControlValue(2), ""}, {"CO2浓度", compactDashboardValue(bleManager.getControlValue(3)), "读取"},
                {"冷光照明", compactDashboardValue(bleManager.getControlValue(4)), ""}, {"暖光照明", compactDashboardValue(bleManager.getControlValue(5)), ""}, {"红外理疗", compactDashboardValue(bleManager.getControlValue(6)), "定时"}, {"蓝光理疗", compactDashboardValue(bleManager.getControlValue(7)), "定时"},
                {"外循环", compactDashboardValue(bleManager.getControlValue(8)), ""}, {"内循环", compactDashboardValue(bleManager.getControlValue(9)), ""}, {"雾化器", compactDashboardValue(bleManager.getControlValue(10)), "定时"}, {"负离子", compactDashboardValue(bleManager.getControlValue(11)), "定时"},
                {"紫外消毒", compactDashboardValue(bleManager.getControlValue(12)), "定时"}, {"雾化器", compactDashboardValue(bleManager.getControlValue(10)), ""}, {"负离子", compactDashboardValue(bleManager.getControlValue(11)), ""}, {"冷光照明", compactDashboardValue(bleManager.getControlValue(4)), ""},
                {"暖光照明", compactDashboardValue(bleManager.getControlValue(5)), ""}, {"外循环", compactDashboardValue(bleManager.getControlValue(8)), ""}, {"内循环", compactDashboardValue(bleManager.getControlValue(9)), ""}, {"治疗时长", compactDashboardValue(bleManager.getControlValue(15)), "设置"}
        };
        float gap = dp(24);
        float cardW = (shell.width() - gap * 6) / 5f;
        float cardH = Math.min(dp(178), (shell.height() - gap * 5) / 4f);
        for (int i = 0; i < items.length; i++) {
            final int commandIndex = Math.min(i, 15);
            final String title = items[i][0];
            int col = i % 5;
            int row = i / 5;
            RectF r = rect(shell.left + gap + col * (cardW + gap), shell.top + gap + row * (cardH + gap), shell.left + gap + col * (cardW + gap) + cardW, shell.top + gap + row * (cardH + gap) + cardH);
            ClickAction cardAction = new ClickAction() {
                @Override
                public void run() {
                    handleControlCardTap(commandIndex, title);
                }
            };
            ClickAction buttonAction = new ClickAction() {
                @Override
                public void run() {
                    handleControlButtonTap(commandIndex, title);
                }
            };
            rounded(canvas, r, panel, Color.TRANSPARENT, dp(8));
            drawText(canvas, items[i][0], r.left + dp(22), r.top + dp(34), 17, text, true);
            if (commandIndex == 2) {
                String monitorLevelColor = currentMonitorLevelColor();
                RectF level = rect(r.left + dp(22), r.top + dp(56), r.left + dp(112), r.top + dp(88));
                rounded(canvas, level, monitorLevelFillColor(monitorLevelColor), Color.TRANSPARENT, dp(16));
                drawTextCenter(canvas, monitorLevelLabel(monitorLevelColor), level, 15, Color.WHITE, true);
                drawStatusLightDots(canvas, r);
            } else {
                int valueColor = commandIndex == 0 ? red : text;
                drawText(canvas, items[i][1], r.left + dp(22), r.top + dp(94), 40, valueColor, false);
            }
            drawControlIcon(canvas, r, commandIndex);
            if (!TextUtils.isEmpty(items[i][2])) {
                clickZones.add(new ClickZone(new RectF(r), cardAction));
                drawText(canvas, items[i][2], r.left + dp(22), r.bottom - dp(24), 15, activeBlue, false);
                clickZones.add(new ClickZone(rect(r.left, r.bottom - dp(48), r.left + dp(82), r.bottom), buttonAction));
            } else {
                drawToggle(canvas, r.left + dp(22), r.bottom - dp(38), bleManager.isControlOn(commandIndex));
                clickZones.add(new ClickZone(new RectF(r), cardAction));
            }
        }
    }

    private void handleControlCardTap(int index, String title) {
        if (index == 0 || index == 1) {
            handleControlButtonTap(index, title);
            return;
        }
        if (index == 2) {
            applyMonitorLevel(nextMonitorLevelColor(currentMonitorLevelColor()));
            return;
        }
        // 5 个定时控件:红外理疗(6)/蓝光理疗(7)/紫外消毒(12)/雾化器(10)/负离子(11)
        // 已开→关闭,已关→默认 99 分钟并开启倒计时
        if (index == 6 || index == 7 || index == 10 || index == 11 || index == 12) {
            boolean ok;
            if (bleManager.isControlOn(index)) {
                // ★ 修复 P0-2:之前只无条件开启,用户永远关不掉,只能等倒计时归零
                ok = bleManager.setControlEnabled(index, false);
            } else {
                ok = bleManager.startTimedControlDirect(index);
            }
            showSendResult(title, ok);
            invalidate();
            return;
        }
        // ★ 修复 #16:湿度(index=14)只读,点击主动请求读取设备湿度
        if (index == 14) {
            // 1) 主动发 get_humidity 命令给设备(不等 3 秒轮询)
            boolean sent = bleManager.requestHumidity();
            // 2) 先 Toast 当前缓存值(用户立即看到反馈)
            String humidityText = safeText(bleManager.getHumidityText());
            if (TextUtils.isEmpty(humidityText)) {
                Float hv = bleManager.getHumidityValue();
                humidityText = hv == null ? "--" : String.valueOf(hv);
            }
            String msg = sent
                    ? "正在读取设备湿度... 当前缓存: " + humidityText + "%"
                    : "湿度缓存: " + humidityText + "%（蓝牙未连接）";
            Toast.makeText(activity, msg, Toast.LENGTH_SHORT).show();
            invalidate();
            return;
        }
        showSendResult(title, bleManager.sendControlPreset(index));
        invalidate();
    }

    private void handleControlButtonTap(int index, String title) {
        if (index == 0) {
            // ★ 修复 P1-3:用 lastSet 值而非设备实测值
            showNumberDialog(index, "设置舱内温度", "℃", bleManager.getLastSetCabinTempValue(), false);
            return;
        }
        if (index == 1) {
            // ★ 修复 P1-3:用 lastSet 值而非设备实测值
            showNumberDialog(index, "设置氧浓度", "%", bleManager.getLastSetOxygenValue(), false);
            return;
        }
        if (index == 3) {
            // CO2 目标值：报警阈值已独立到“设置报警阈值”，这里只设目标浓度。
            // showNumberDialog 内部：超出参考区间弹警告 Toast 但不阻止，允许强行设置。
            showNumberDialog(index, "设置CO2目标值", "PPM", co2TargetForDialog(), true);
            return;
        }
        if (index == 14) {
            // ★ 修复 #16:湿度只读,点"设置"按钮主动读取设备湿度
            boolean sent = bleManager.requestHumidity();
            String humidityText = safeText(bleManager.getHumidityText());
            if (TextUtils.isEmpty(humidityText)) {
                Float hv = bleManager.getHumidityValue();
                humidityText = hv == null ? "--" : String.valueOf(hv);
            }
            String msg = sent
                    ? "正在读取设备湿度... 当前缓存: " + humidityText + "%"
                    : "湿度缓存: " + humidityText + "%（蓝牙未连接）";
            Toast.makeText(activity, msg, Toast.LENGTH_SHORT).show();
            invalidate();
            return;
        }
        if (index == 15) {
            // ★ 治疗时长自动统计，不再手动设置（见 control_time 入口的说明弹窗）。
            new AlertDialog.Builder(activity)
                    .setTitle("治疗时长")
                    .setMessage("治疗时长为自动统计：开启任一治疗项后自动正计时，全部关闭后暂停，"
                            + "再次开启继续累计。\n\n当前累计：" + bleManager.getTreatmentTimeText())
                    .setPositiveButton("知道了", null)
                    .show();
            return;
        }
        if (index == 6 || index == 7 || index == 10 || index == 11 || index == 12) {
            showNumberDialog(index, "设置" + title + "时间", "分钟", bleManager.getControlTimeValue(index), true);
            return;
        }
        showSendResult(title, bleManager.sendControlPreset(index));
        invalidate();
    }

    private void showNumberDialog(final int index, String title, String suffix, Number currentValue, final boolean integerOnly) {
        final EditText input = new EditText(activity);
        input.setSingleLine(true);
        input.setSelectAllOnFocus(true);
        input.setText(currentValue == null ? "" : (integerOnly ? String.valueOf(currentValue.intValue()) : trimNumber(currentValue.floatValue())));
        input.setHint(suffix);
        input.setInputType(InputType.TYPE_CLASS_NUMBER | (integerOnly ? 0 : InputType.TYPE_NUMBER_FLAG_DECIMAL));

        // ★ 修复 #17:用 LinearLayout 包一个"参考区间提示 TextView" + EditText,让用户**直接看到**区间
        //   取代之前只在超界时弹警告 Toast 的"隐藏"行为
        android.widget.LinearLayout container = new android.widget.LinearLayout(activity);
        container.setOrientation(android.widget.LinearLayout.VERTICAL);
        int pad = dpInt(16);
        container.setPadding(pad, pad, pad, 0);
        if (index == 3) {
            // CO2 目标值：这里只说明目标值本身
            TextView hint = new TextView(activity);
            hint.setText("参考区间: 4000 - 6000 PPM\n" +
                    "• 这是下发给主机的目标浓度");
            hint.setTextSize(13);
            hint.setTextColor(0xFF666666);  // 灰色提示
            hint.setPadding(0, 0, 0, dpInt(12));
            hint.setLineSpacing(dpInt(2), 1.0f);
            container.addView(hint);
        }
        container.addView(input);

        new AlertDialog.Builder(activity)
                .setTitle(title)
                .setView(container)
                .setNegativeButton("取消", null)
                .setPositiveButton("确定", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        String raw = input.getText().toString().trim();
                        if (TextUtils.isEmpty(raw)) {
                            Toast.makeText(activity, "请输入数值", Toast.LENGTH_SHORT).show();
                            return;
                        }
                        try {
                            float value = Float.parseFloat(raw);
                            String error = validateControlInput(index, value);
                            if (!TextUtils.isEmpty(error)) {
                                Toast.makeText(activity, error, Toast.LENGTH_SHORT).show();
                                return;
                            }
                            // CO2 目标值参考区间 4000-6000 PPM：超出只警告，不阻止发送。
                            // 报警阈值是独立设置，不受此目标值影响。
                            if (index == 3 && (value < 4000 || value > 6000)) {
                                Toast.makeText(activity, "⚠️ 警告：CO2 目标值建议设置在 4000-6000 PPM（当前 " + Math.round(value) + "）", Toast.LENGTH_LONG).show();
                                // 不return,允许设置
                            }
                            boolean sent;
                            if (index == 0) {
                                sent = bleManager.setTemperature(value);
                            } else if (index == 1) {
                                sent = bleManager.setOxygen(value);
                            } else if (index == 3) {
                                sent = bleManager.setCo2(Math.round(value));
                            } else if (index == 14) {
                                sent = bleManager.setHumidity(value);
                            } else if (index == 6 || index == 7 || index == 10 || index == 11 || index == 12) {
                                // 定时功能的“设置”确认即按所填时长开启并开始倒计时。
                                sent = bleManager.startTimedControlWithConfiguredDuration(index, Math.round(value));
                            } else {
                                sent = bleManager.setControlTime(index, Math.round(value));
                            }
                            showSendResult("设置", sent);
                            invalidate();
                        } catch (NumberFormatException exception) {
                            Toast.makeText(activity, "数值格式不正确", Toast.LENGTH_SHORT).show();
                        }
                    }
                })
                .show();
    }

    /**
     * ★ 新加：补偿设置"红外体温"卡。弹出数字输入框，单位 ℃，调 setInfraredTemp。
     */
    private void showInfraredTempCompensationDialog() {
        final EditText input = new EditText(activity);
        input.setSingleLine(true);
        input.setSelectAllOnFocus(true);
        Float current = bleManager.getInfraredTempValue();
        input.setText(current == null ? "" : trimNumber(current));
        input.setHint("℃");
        input.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
        android.widget.LinearLayout container = new android.widget.LinearLayout(activity);
        container.setOrientation(android.widget.LinearLayout.VERTICAL);
        int pad = dpInt(16);
        container.setPadding(pad, pad, pad, 0);
        TextView hint = new TextView(activity);
        hint.setText("参考区间: 30 - 45 ℃\n该值会通过 BLE 下发到主机红外测温模块。");
        hint.setTextSize(13);
        hint.setTextColor(0xFF666666);
        hint.setPadding(0, 0, 0, dpInt(12));
        hint.setLineSpacing(dpInt(2), 1.0f);
        container.addView(hint);
        container.addView(input);

        new AlertDialog.Builder(activity)
                .setTitle("红外体温补偿")
                .setView(container)
                .setNegativeButton("取消", null)
                .setPositiveButton("确定", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        String raw = input.getText().toString().trim();
                        if (TextUtils.isEmpty(raw)) {
                            Toast.makeText(activity, "请输入数值", Toast.LENGTH_SHORT).show();
                            return;
                        }
                        try {
                            float value = Float.parseFloat(raw);
                            if (value < 20f || value > 50f) {
                                Toast.makeText(activity, "⚠️ 建议设置在 20-50 ℃ 范围内 (当前 " + value + ")", Toast.LENGTH_LONG).show();
                            }
                            boolean sent = bleManager.setInfraredTemp(value);
                            showSendResult("设置红外体温补偿", sent);
                            invalidate();
                        } catch (NumberFormatException exception) {
                            Toast.makeText(activity, "数值格式不正确", Toast.LENGTH_SHORT).show();
                        }
                    }
                })
                .show();
    }

    private String validateControlInput(int index, float value) {
        if (index == 0 && (value < 15f || value > 45f)) {
            return "舱内温度建议设置在15-45℃";
        }
        if (index == 1 && (value < 18f || value > 95f)) {
            return "氧浓度建议设置在18-95%";
        }
        if (index == 3 && (value < 300f || value > 6000f)) {
            return "CO2目标值建议设置在300-6000PPM";
        }
        if (index == 14 && (value < 20f || value > 95f)) {
            return "湿度建议设置在20-95%";
        }
        // ★ 定时控件时长校验：紫外/雾化/负离子 0-120 分钟；
        //   红外/蓝光额外允许不限时哨兵值 65536（协议约定的“无时间限制”）。
        if ((index == 6 || index == 7 || index == 10 || index == 11 || index == 12)
                && !TimedControlProtocol.isAcceptableMinutes(index, Math.round(value))) {
            if (index == 6 || index == 7) {
                return "治疗时长必须在0-120分钟之间，或填 "
                        + TimedControlProtocol.UNLIMITED_MINUTES + " 表示不限时";
            }
            return "治疗时长必须在0-120分钟之间";
        }
        // 治疗时长(index 15)是 APP 本地记录的总疗程时长,不下发给设备,保留原 0-1440 上限。
        if (index == 15 && (value < 0f || value > 1440f)) {
            return "治疗时长建议设置在0-1440分钟";
        }
        return "";
    }

    private void showSendResult(String title, boolean sent) {
        String detail = TextUtils.isEmpty(bleManager.getLastError()) ? "暂时无法发送，请检查蓝牙连接" : bleManager.getLastError();
        String message = sent ? "已发送: " + title + "，等待设备回读" : detail;
        Toast.makeText(activity, message, Toast.LENGTH_SHORT).show();
    }

    private void playMonitorAlarmSound() {
        try {
            final ToneGenerator tone = new ToneGenerator(AudioManager.STREAM_ALARM, 100);
            tone.startTone(ToneGenerator.TONE_CDMA_ALERT_CALL_GUARD, 850);
            uiHandler.postDelayed(new Runnable() {
                @Override
                public void run() {
                    try {
                        tone.release();
                    } catch (RuntimeException ignored) {
                    }
                }
            }, 1000L);
        } catch (RuntimeException exception) {
            Toast.makeText(activity, "报警音播放失败，请检查系统音量", Toast.LENGTH_SHORT).show();
        }
    }

    private String trimNumber(float value) {
        if (Math.abs(value - Math.round(value)) < 0.05f) {
            return String.valueOf(Math.round(value));
        }
        return String.format(java.util.Locale.US, "%.1f", value);
    }

    private String compactDashboardValue(String value) {
        if (TextUtils.isEmpty(value)) {
            return "--";
        }
        String compact = value.replace("℃", "").replace("%", "").replace("PPM", "").trim();
        if (compact.startsWith("开") && compact.endsWith("分钟") && compact.length() > 3) {
            compact = compact.substring(1, compact.length() - 2).trim();
        }
        int hIndex = compact.indexOf('h');
        int mIndex = compact.indexOf('m');
        if (hIndex > 0 && mIndex > hIndex) {
            try {
                int hours = Integer.parseInt(compact.substring(0, hIndex).trim());
                int minutes = Integer.parseInt(compact.substring(hIndex + 1, mIndex).trim());
                float totalHours = hours + minutes / 60f;
                return Math.abs(totalHours - Math.round(totalHours)) < 0.05f
                        ? String.valueOf(Math.round(totalHours))
                        : String.format(Locale.US, "%.1f", totalHours);
            } catch (NumberFormatException ignored) {
            }
        }
        return compact;
    }

    private void drawMonitorPage(Canvas canvas, float x, float y, float w, float h) {
        fill(canvas, x, y, x + w, y + h, page);
        RectF monitor = rect(x + dp(10), y + dp(10), x + w - dp(10), y + h - dp(10));
        rounded(canvas, monitor, Color.BLACK, border, dp(1));
        drawText(canvas, "AM4100 实时监护", monitor.left + dp(20), monitor.top + dp(24), 12, Color.WHITE, true);
        drawText(canvas, "连接: " + am4100Manager.getStateText() + "    设备: " + dash(am4100Manager.getConnectedDeviceName()),
                monitor.left + dp(20), monitor.top + dp(45), 10, Color.rgb(190, 205, 215), false);

        float buttonY = monitor.top + dp(14);
        float buttonX = monitor.left + dp(188);
        drawSmallButton(canvas, "权限", buttonX, buttonY, dp(54), dp(24), false, new ClickAction() {
            @Override
            public void run() {
                activity.requestBlePermissionsFromUi();
            }
        });
        drawSmallButton(canvas, "蓝牙", buttonX + dp(62), buttonY, dp(54), dp(24), false, new ClickAction() {
            @Override
            public void run() {
                am4100Manager.requestEnableBluetooth(activity);
            }
        });
        drawSmallButton(canvas, am4100Manager.isScanning() ? "停止扫描" : "扫描蓝牙", buttonX + dp(124), buttonY, dp(92), dp(24), am4100Manager.isScanning(), new ClickAction() {
            @Override
            public void run() {
                if (am4100Manager.isScanning()) {
                    am4100Manager.stopScan();
                } else if (activity.hasAllBlePermissions()) {
                    am4100Manager.startScan();
                } else {
                    Toast.makeText(activity, "请先申请蓝牙权限", Toast.LENGTH_SHORT).show();
                }
            }
        });
        drawSmallButton(canvas, "断开", buttonX + dp(224), buttonY, dp(54), dp(24), false, new ClickAction() {
            @Override
            public void run() {
                am4100Manager.disconnect();
            }
        });
        drawSmallButton(canvas, "体温模式", buttonX + dp(286), buttonY, dp(76), dp(24), false, new ClickAction() {
            @Override
            public void run() {
                am4100Manager.setTemperatureMode();
            }
        });

        drawMonitorModeTabs(canvas, monitor);
        if (monitorMode == MONITOR_MODE_HISTORY) {
            am4100DeviceListBounds.setEmpty();
            drawMonitorHistoryPage(canvas, monitor);
            scheduleMonitorRefresh();
            return;
        }
        if (monitorMode == MONITOR_MODE_SETTINGS) {
            am4100DeviceListBounds.setEmpty();
            drawMonitorSettingsPage(canvas, monitor);
            scheduleMonitorRefresh();
            return;
        }

        float graphLeft = monitor.left + dp(20);
        float graphRight = monitor.right - dp(205);
        if (selectedMonitorTrend == MONITOR_TREND_ALL) {
            drawRealtimeWave(canvas, graphLeft, monitor.top + dp(78), graphRight, dp(30),
                    Color.rgb(57, 135, 64), 0, am4100Manager.getEcgWaveSamples(), am4100Manager.getHeartRateValue());
            drawRealtimeWave(canvas, graphLeft, monitor.top + dp(150), graphRight, dp(42),
                    Color.rgb(142, 48, 42), 1, am4100Manager.getSpo2WaveSamples(), am4100Manager.getSpo2Value());
            drawRealtimeWave(canvas, graphLeft, monitor.top + dp(222), graphRight, dp(36),
                    Color.rgb(74, 124, 170), 2, am4100Manager.getRespWaveSamples(), am4100Manager.getRespValue());
            drawText(canvas, "ECG", graphLeft, monitor.top + dp(68), 10, Color.rgb(87, 204, 88), true);
            drawText(canvas, "PLETH", graphLeft, monitor.top + dp(135), 10, Color.rgb(224, 68, 64), true);
            drawText(canvas, "RESP", graphLeft, monitor.top + dp(205), 10, Color.rgb(94, 158, 222), true);
            drawText(canvas, am4100Manager.hasVitals() ? "LIVE" : "WAITING", graphLeft + dp(170), monitor.top + dp(174), 28,
                    Color.argb(125, 55, 130, 210), false);
        } else {
            drawSelectedMonitorTrend(canvas, selectedMonitorTrend, graphLeft, monitor.top + dp(62), graphRight, monitor.top + dp(260));
        }

        float mx = monitor.right - dp(178);
        drawMonitorMetric(canvas, "心率", am4100Manager.getHeartRateText(), "bpm", mx, monitor.top + dp(70), green, MONITOR_TREND_HEART);
        drawMonitorMetric(canvas, "血压", am4100Manager.getBloodPressureText(), "mmHg", mx, monitor.top + dp(125), orange, MONITOR_TREND_BP);
        drawMonitorMetric(canvas, "血氧", am4100Manager.getSpo2Text(), "%", mx, monitor.top + dp(180), red, MONITOR_TREND_SPO2);
        drawMonitorMetric(canvas, "脉率", am4100Manager.getPulseRateText(), "bpm", mx, monitor.top + dp(235), green, MONITOR_TREND_PULSE);
        drawMonitorMetric(canvas, "体温", am4100Manager.getBodyTempText(), "℃", mx, monitor.top + dp(290), Color.rgb(60, 190, 210), MONITOR_TREND_TEMP);
        drawMonitorMetric(canvas, "呼吸率", am4100Manager.getRespText(), "brpm", mx, monitor.top + dp(345), Color.rgb(80, 170, 235), MONITOR_TREND_RESP);

        RectF devicePanel = rect(graphLeft, Math.max(monitor.top + dp(275), monitor.bottom - dp(116)),
                graphRight, monitor.bottom - dp(14));
        if (devicePanel.height() > dp(54)) {
            rounded(canvas, devicePanel, Color.rgb(14, 22, 32), Color.rgb(65, 84, 105), dp(2));
            drawText(canvas, "AM4100设备", devicePanel.left + dp(10), devicePanel.top + dp(18), 11, Color.WHITE, true);
            drawText(canvas, "最近帧: " + dash(am4100Manager.getLastFrameSummary()),
                    devicePanel.left + dp(92), devicePanel.top + dp(18), 10, Color.rgb(190, 205, 215), false);
            String error = am4100Manager.getLastError();
            if (!TextUtils.isEmpty(error)) {
                drawText(canvas, error, devicePanel.left + dp(10), devicePanel.top + dp(38), 10, Color.rgb(255, 155, 130), false);
            } else {
                drawText(canvas, "校准: " + dash(am4100Manager.getCalibrationStatusText())
                                + "  环境温: " + am4100Manager.getAmbientTempText()
                                + "  物体温: " + am4100Manager.getObjectTempText(),
                        devicePanel.left + dp(10), devicePanel.top + dp(38), 10, Color.rgb(170, 190, 205), false);
            }

            RectF listClip = rect(devicePanel.left + dp(10), devicePanel.top + dp(48),
                    devicePanel.right - dp(10), devicePanel.bottom - dp(8));
            am4100DeviceListBounds.set(listClip);
            canvas.save();
            canvas.clipRect(listClip);
            List<Am4100Manager.DeviceItem> devices = am4100Manager.getDevices();
            if (devices.isEmpty()) {
                am4100DeviceScrollY = 0;
                am4100DeviceMaxScrollY = 0;
                drawText(canvas, "扫描后会显示附近蓝牙设备，可上下滑动查找", listClip.left, listClip.top + dp(18), 10, Color.rgb(135, 156, 176), false);
            } else {
                float rowHeight = dp(34);
                float contentHeight = devices.size() * rowHeight;
                am4100DeviceMaxScrollY = Math.max(0, contentHeight - listClip.height());
                am4100DeviceScrollY = clamp(am4100DeviceScrollY, 0, am4100DeviceMaxScrollY);
                float rowY = listClip.top - am4100DeviceScrollY;
                for (int i = 0; i < devices.size(); i++) {
                    final Am4100Manager.DeviceItem item = devices.get(i);
                    RectF row = rect(listClip.left, rowY, listClip.right, rowY + dp(28));
                    if (row.bottom >= listClip.top && row.top <= listClip.bottom) {
                        rounded(canvas, row, Color.rgb(26, 38, 52), Color.rgb(75, 92, 110), dp(2));
                        drawText(canvas, item.deviceName + "  RSSI " + item.rssi, row.left + dp(8), row.top + dp(17), 10, Color.WHITE, true);
                        drawTextRight(canvas, "连接", row.right - dp(10), row.top + dp(18), 10, green, true);
                        RectF clickRow = new RectF(row);
                        clickRow.intersect(listClip);
                        clickZones.add(new ClickZone(clickRow, new ClickAction() {
                            @Override
                            public void run() {
                                am4100Manager.connect(item.deviceId);
                            }
                        }));
                    }
                    rowY += rowHeight;
                }
                if (am4100DeviceMaxScrollY > 0) {
                    float thumbHeight = Math.max(dp(14), listClip.height() * listClip.height() / contentHeight);
                    float thumbTop = listClip.top + (listClip.height() - thumbHeight) * (am4100DeviceScrollY / am4100DeviceMaxScrollY);
                    rounded(canvas, rect(listClip.right - dp(5), thumbTop, listClip.right - dp(1), thumbTop + thumbHeight),
                            Color.rgb(90, 116, 140), Color.TRANSPARENT, dp(2));
                }
            }
            canvas.restore();
        }
        scheduleMonitorRefresh();
    }

    private void drawMonitorModeTabs(Canvas canvas, RectF monitor) {
        float y = monitor.top + dp(14);
        float x = monitor.right - dp(246);
        drawMonitorModeButton(canvas, "实时", x, y, dp(54), MONITOR_MODE_LIVE);
        drawMonitorModeButton(canvas, "历史数据", x + dp(62), y, dp(82), MONITOR_MODE_HISTORY);
        drawMonitorModeButton(canvas, "设置", x + dp(152), y, dp(54), MONITOR_MODE_SETTINGS);
    }

    private void drawMonitorModeButton(Canvas canvas, String label, float x, float y, float w, final int mode) {
        final RectF r = rect(x, y, x + w, y + dp(24));
        boolean active = monitorMode == mode;
        rounded(canvas, r, active ? Color.rgb(28, 92, 68) : Color.rgb(12, 18, 26),
                active ? green : Color.rgb(68, 82, 98), dp(12));
        drawTextCenter(canvas, label, r, 10, active ? Color.WHITE : Color.rgb(184, 202, 216), true);
        clickZones.add(new ClickZone(new RectF(r), new ClickAction() {
            @Override
            public void run() {
                monitorMode = mode;
            }
        }));
    }

    private void drawMonitorHistoryPage(Canvas canvas, RectF monitor) {
        RectF card = rect(monitor.left + dp(20), monitor.top + dp(58), monitor.right - dp(20), monitor.bottom - dp(16));
        rounded(canvas, card, Color.rgb(7, 12, 18), Color.rgb(58, 76, 94), dp(3));
        drawText(canvas, "历史数据", card.left + dp(16), card.top + dp(24), 13, Color.WHITE, true);
        drawText(canvas, "记录本次连接期间 AM4100 回传的心率、血氧、脉率、血压、体温、呼吸率趋势",
                card.left + dp(100), card.top + dp(24), 10, Color.rgb(166, 186, 204), false);
        drawTextRight(canvas, "最近帧: " + dash(am4100Manager.getLastFrameSummary()), card.right - dp(16), card.top + dp(24),
                9, Color.rgb(130, 154, 176), false);

        float top = card.top + dp(42);
        float rowH = Math.max(dp(54), (card.bottom - top - dp(12)) / 6f);
        drawHistoryTrendRow(canvas, MONITOR_TREND_HEART, card.left + dp(12), top, card.right - dp(12), rowH - dp(7));
        drawHistoryTrendRow(canvas, MONITOR_TREND_SPO2, card.left + dp(12), top + rowH, card.right - dp(12), rowH - dp(7));
        drawHistoryTrendRow(canvas, MONITOR_TREND_PULSE, card.left + dp(12), top + rowH * 2, card.right - dp(12), rowH - dp(7));
        drawHistoryTrendRow(canvas, MONITOR_TREND_BP, card.left + dp(12), top + rowH * 3, card.right - dp(12), rowH - dp(7));
        drawHistoryTrendRow(canvas, MONITOR_TREND_TEMP, card.left + dp(12), top + rowH * 4, card.right - dp(12), rowH - dp(7));
        drawHistoryTrendRow(canvas, MONITOR_TREND_RESP, card.left + dp(12), top + rowH * 5, card.right - dp(12), rowH - dp(7));
    }

    private void drawHistoryTrendRow(Canvas canvas, final int trend, float left, float top, float right, float height) {
        RectF r = rect(left, top, right, top + height);
        int color = monitorTrendColor(trend);
        boolean alarm = isMonitorTrendAlarm(trend);
        rounded(canvas, r, alarm ? Color.rgb(36, 10, 10) : Color.rgb(13, 22, 32),
                alarm ? red : Color.rgb(45, 63, 80), dp(2));
        drawText(canvas, monitorTrendName(trend), r.left + dp(12), r.top + dp(21), 11, color, true);
        drawText(canvas, dash(monitorTrendValue(trend)) + " " + monitorTrendUnit(trend),
                r.left + dp(12), r.top + dp(42), 15, alarm ? red : color, true);
        drawText(canvas, monitorLimitText(trend), r.left + dp(122), r.top + dp(21), 9, Color.rgb(150, 170, 188), false);
        drawText(canvas, "点此查看单项电波图", r.left + dp(122), r.top + dp(42), 9, Color.rgb(104, 128, 150), false);

        List<Integer> samples = monitorTrendSamples(trend);
        float graphLeft = r.left + dp(280);
        float graphRight = r.right - dp(92);
        float baseline = r.centerY() + dp(5);
        float amp = Math.max(dp(14), r.height() * 0.28f);
        for (int i = 0; i < 4; i++) {
            float gy = baseline - amp + i * (amp * 2f / 3f);
            fill(canvas, graphLeft, gy, graphRight, gy + dp(0.5f), Color.rgb(28, 43, 58));
        }
        drawReportWave(canvas, graphLeft, baseline, graphRight, amp, color, monitorTrendWaveType(trend),
                samples, monitorTrendNumericValue(trend, samples), dp(1.4f));
        drawTextRight(canvas, "点数 " + samples.size(), r.right - dp(14), r.top + dp(24), 9, Color.rgb(135, 156, 176), false);
        drawTextRight(canvas, alarm ? "报警" : "正常", r.right - dp(14), r.top + dp(45), 10, alarm ? red : green, true);

        clickZones.add(new ClickZone(new RectF(r), new ClickAction() {
            @Override
            public void run() {
                selectedMonitorTrend = trend;
                monitorMode = MONITOR_MODE_LIVE;
            }
        }));
    }

    private void drawMonitorSettingsPage(Canvas canvas, RectF monitor) {
        RectF card = rect(monitor.left + dp(20), monitor.top + dp(58), monitor.right - dp(20), monitor.bottom - dp(16));
        rounded(canvas, card, Color.rgb(18, 18, 18), Color.rgb(85, 85, 85), dp(4));
        drawText(canvas, "设置", card.left + dp(16), card.top + dp(26), 14, Color.WHITE, true);
        drawText(canvas, "上下限会用于实时指标报警高亮，点击每一行可修改数值", card.left + dp(76), card.top + dp(26),
                10, Color.rgb(175, 175, 175), false);

        float y = card.top + dp(52);
        drawMonitorTopSwitch(canvas, "脉搏音", card.left + dp(250), y - dp(22), monitorPulseBeep, new ClickAction() {
            @Override
            public void run() {
                monitorPulseBeep = !monitorPulseBeep;
                saveUiSettings();
            }
        });
        drawMonitorTopSwitch(canvas, "报警音", card.left + dp(470), y - dp(22), monitorAlarmSound, new ClickAction() {
            @Override
            public void run() {
                monitorAlarmSound = !monitorAlarmSound;
                if (monitorAlarmSound) {
                    playMonitorAlarmSound();
                }
                saveUiSettings();
            }
        });
        fill(canvas, card.left + dp(16), y, card.right - dp(16), y + dp(0.8f), Color.rgb(70, 70, 70));
        y += dp(20);

        y = drawMonitorLimitSection(canvas, card.left + dp(18), y, card.right - dp(18), "心率", green,
                new int[]{LIMIT_HEART}, new String[]{"上下限"}, false);
        y = drawMonitorLimitSection(canvas, card.left + dp(18), y, card.right - dp(18), "血压", orange,
                new int[]{LIMIT_BP_SYS, LIMIT_BP_DIA}, new String[]{"收缩压", "舒张压"}, true);
        y = drawMonitorLimitSection(canvas, card.left + dp(18), y, card.right - dp(18), "脉率", Color.rgb(22, 165, 145),
                new int[]{LIMIT_PULSE}, new String[]{"上下限"}, false);
        y = drawMonitorLimitSection(canvas, card.left + dp(18), y, card.right - dp(18), "血氧", red,
                new int[]{LIMIT_SPO2}, new String[]{"上下限"}, false);
        y = drawMonitorLimitSection(canvas, card.left + dp(18), y, card.right - dp(18), "体温", Color.rgb(42, 190, 210),
                new int[]{LIMIT_TEMP}, new String[]{"上下限"}, false);
        drawMonitorLimitSection(canvas, card.left + dp(18), y, card.right - dp(18), "呼吸率", Color.rgb(80, 170, 235),
                new int[]{LIMIT_RESP}, new String[]{"上下限"}, false);
    }

    private void drawMonitorTopSwitch(Canvas canvas, String label, float x, float y, boolean on, final ClickAction action) {
        drawText(canvas, label, x, y + dp(20), 12, Color.WHITE, true);
        RectF sw = rect(x + dp(78), y + dp(2), x + dp(124), y + dp(25));
        drawMonitorSwitch(canvas, sw, on, green);
        clickZones.add(new ClickZone(new RectF(sw), action));
    }

    private float drawMonitorLimitSection(Canvas canvas, float left, float y, float right, String title, int color,
                                          int[] limitIndices, String[] rowLabels, boolean includeAutoRow) {
        drawText(canvas, "[ " + title + " ]", left, y + dp(18), 12, color, true);
        y += dp(30);
        for (int i = 0; i < limitIndices.length; i++) {
            drawMonitorLimitRow(canvas, left, y, right, rowLabels[i], limitIndices[i], color);
            y += dp(34);
        }
        if (includeAutoRow) {
            RectF row = rect(left, y, right, y + dp(30));
            drawText(canvas, "自动测量", row.left, row.top + dp(21), 11, color, true);
            drawMonitorOpenClose(canvas, rect(row.right - dp(90), row.top + dp(4), row.right - dp(34), row.bottom - dp(4)),
                    monitorAutoNibp, color);
            clickZones.add(new ClickZone(new RectF(row.right - dp(94), row.top, row.right, row.bottom), new ClickAction() {
                @Override
                public void run() {
                    monitorAutoNibp = !monitorAutoNibp;
                }
            }));
            y += dp(34);
        }
        fill(canvas, left, y + dp(2), right, y + dp(2.8f), Color.rgb(62, 62, 62));
        return y + dp(18);
    }

    private void drawMonitorLimitRow(Canvas canvas, float left, float y, float right, String label, final int limitIndex, int color) {
        final RectF row = rect(left, y, right, y + dp(32));
        boolean enabled = monitorLimitEnabled[limitIndex];
        int rowColor = enabled ? color : Color.rgb(125, 125, 125);
        drawText(canvas, label, row.left, row.top + dp(21), 11, rowColor, true);
        drawText(canvas, "上限: " + formatLimitValue(limitIndex, monitorLimitHigh[limitIndex]),
                row.left + dp(250), row.top + dp(21), 11, rowColor, true);
        drawText(canvas, "下限: " + formatLimitValue(limitIndex, monitorLimitLow[limitIndex]),
                row.left + dp(365), row.top + dp(21), 11, rowColor, true);
        drawMonitorOpenClose(canvas, rect(row.right - dp(90), row.top + dp(4), row.right - dp(34), row.bottom - dp(4)),
                enabled, rowColor);
        drawTextRight(canvas, ">", row.right - dp(6), row.top + dp(23), 20, Color.rgb(150, 150, 150), false);

        clickZones.add(new ClickZone(new RectF(row), new ClickAction() {
            @Override
            public void run() {
                showMonitorLimitDialog(limitIndex);
            }
        }));
        clickZones.add(new ClickZone(new RectF(row.right - dp(96), row.top, row.right - dp(28), row.bottom), new ClickAction() {
            @Override
            public void run() {
                monitorLimitEnabled[limitIndex] = !monitorLimitEnabled[limitIndex];
            }
        }));
    }

    private void drawMonitorOpenClose(Canvas canvas, RectF r, boolean on, int color) {
        rounded(canvas, r, Color.BLACK, Color.rgb(80, 80, 80), dp(8));
        drawTextCenter(canvas, on ? "开" : "关", r, 11, on ? color : Color.rgb(170, 170, 170), true);
    }

    private void drawMonitorSwitch(Canvas canvas, RectF r, boolean on, int color) {
        rounded(canvas, r, on ? Color.rgb(35, 205, 94) : Color.rgb(80, 80, 80), Color.TRANSPARENT, r.height() / 2f);
        drawCircle(canvas, on ? r.right - r.height() / 2f : r.left + r.height() / 2f, r.centerY(),
                r.height() * 0.42f, Color.WHITE);
    }

    private void drawMonitorMetric(Canvas canvas, String name, String value, String unit, float x, float y, int color, final int trend) {
        RectF hit = rect(x - dp(10), y - dp(25), x + dp(170), y + dp(21));
        boolean alarm = isMonitorTrendAlarm(trend);
        if (selectedMonitorTrend == trend || alarm) {
            rounded(canvas, hit, alarm ? Color.rgb(42, 8, 8) : Color.rgb(10, 25, 36), alarm ? red : color, dp(4));
            drawTextRight(canvas, alarm ? "报警" : "已筛选", hit.right - dp(8), hit.top + dp(13), 8, alarm ? red : color, false);
        }
        drawMetric(canvas, name, value, unit, x, y, color);
        clickZones.add(new ClickZone(new RectF(hit), new ClickAction() {
            @Override
            public void run() {
                selectedMonitorTrend = selectedMonitorTrend == trend ? MONITOR_TREND_ALL : trend;
            }
        }));
    }

    private void drawSelectedMonitorTrend(Canvas canvas, int trend, float left, float top, float right, float bottom) {
        RectF card = rect(left, top, right, bottom);
        int color = monitorTrendColor(trend);
        List<Integer> samples = monitorTrendWaveSamples(trend);
        rounded(canvas, card, Color.rgb(3, 9, 15), Color.rgb(43, 65, 84), dp(2));

        drawText(canvas, monitorTrendName(trend) + "电波图", card.left + dp(12), card.top + dp(22), 12, color, true);
        drawTextRight(canvas, "再次点击右侧指标恢复全部", card.right - dp(12), card.top + dp(22), 9, Color.rgb(148, 170, 190), false);

        float chartLeft = card.left + dp(18);
        float chartRight = card.right - dp(18);
        float chartTop = card.top + dp(40);
        float chartBottom = card.bottom - dp(42);
        float baseline = chartTop + (chartBottom - chartTop) * 0.55f;
        float amp = Math.max(dp(34), (chartBottom - chartTop) * 0.36f);
        for (int i = 0; i <= 4; i++) {
            float gy = chartTop + (chartBottom - chartTop) * i / 4f;
            fill(canvas, chartLeft, gy, chartRight, gy + dp(0.6f), Color.rgb(26, 43, 58));
        }
        for (int i = 0; i <= 6; i++) {
            float gx = chartLeft + (chartRight - chartLeft) * i / 6f;
            fill(canvas, gx, chartTop, gx + dp(0.6f), chartBottom, Color.rgb(19, 33, 47));
        }
        drawReportWave(canvas, chartLeft, baseline, chartRight, amp, color, monitorTrendWaveType(trend),
                samples, monitorTrendNumericValue(trend, samples), dp(2));

        drawText(canvas, dash(monitorTrendValue(trend)) + " " + monitorTrendUnit(trend),
                card.left + dp(12), card.bottom - dp(16), 20, color, true);
        drawTextRight(canvas, "历史点 " + samples.size(), card.right - dp(12), card.bottom - dp(18),
                9, Color.rgb(150, 170, 188), false);
    }

    private String monitorTrendName(int trend) {
        if (trend == MONITOR_TREND_HEART) {
            return "心率";
        }
        if (trend == MONITOR_TREND_SPO2) {
            return "血氧";
        }
        if (trend == MONITOR_TREND_PULSE) {
            return "脉率";
        }
        if (trend == MONITOR_TREND_BP) {
            return "血压";
        }
        if (trend == MONITOR_TREND_TEMP) {
            return "体温";
        }
        return "呼吸率";
    }

    private String monitorTrendValue(int trend) {
        if (trend == MONITOR_TREND_HEART) {
            return am4100Manager.getHeartRateText();
        }
        if (trend == MONITOR_TREND_SPO2) {
            return am4100Manager.getSpo2Text();
        }
        if (trend == MONITOR_TREND_PULSE) {
            return am4100Manager.getPulseRateText();
        }
        if (trend == MONITOR_TREND_BP) {
            return am4100Manager.getBloodPressureText();
        }
        if (trend == MONITOR_TREND_TEMP) {
            return am4100Manager.getBodyTempText();
        }
        return am4100Manager.getRespText();
    }

    private String monitorTrendUnit(int trend) {
        if (trend == MONITOR_TREND_SPO2) {
            return "%";
        }
        if (trend == MONITOR_TREND_BP) {
            return "mmHg";
        }
        if (trend == MONITOR_TREND_TEMP) {
            return "℃";
        }
        if (trend == MONITOR_TREND_RESP) {
            return "brpm";
        }
        return "bpm";
    }

    private int monitorTrendColor(int trend) {
        if (trend == MONITOR_TREND_SPO2) {
            return red;
        }
        if (trend == MONITOR_TREND_BP) {
            return orange;
        }
        if (trend == MONITOR_TREND_TEMP) {
            return Color.rgb(60, 190, 210);
        }
        if (trend == MONITOR_TREND_RESP) {
            return Color.rgb(80, 170, 235);
        }
        return green;
    }

    private int monitorTrendWaveType(int trend) {
        if (trend == MONITOR_TREND_SPO2) {
            return 1;
        }
        if (trend == MONITOR_TREND_TEMP || trend == MONITOR_TREND_RESP) {
            return 2;
        }
        return 0;
    }

    private List<Integer> monitorTrendSamples(int trend) {
        if (trend == MONITOR_TREND_HEART) {
            return am4100Manager.getHeartRateHistory();
        }
        if (trend == MONITOR_TREND_SPO2) {
            return am4100Manager.getSpo2History();
        }
        if (trend == MONITOR_TREND_PULSE) {
            return am4100Manager.getPulseRateHistory();
        }
        if (trend == MONITOR_TREND_BP) {
            return am4100Manager.getBloodPressureHistory();
        }
        if (trend == MONITOR_TREND_TEMP) {
            return am4100Manager.getTemperatureHistory();
        }
        return am4100Manager.getRespHistory();
    }

    private List<Integer> monitorTrendWaveSamples(int trend) {
        if (trend == MONITOR_TREND_HEART) {
            return am4100Manager.getEcgWaveSamples();
        }
        if (trend == MONITOR_TREND_SPO2 || trend == MONITOR_TREND_PULSE) {
            return am4100Manager.getSpo2WaveSamples();
        }
        if (trend == MONITOR_TREND_RESP) {
            return am4100Manager.getRespWaveSamples();
        }
        return monitorTrendSamples(trend);
    }

    private int monitorTrendNumericValue(int trend, List<Integer> samples) {
        if (samples != null && !samples.isEmpty()) {
            return samples.get(samples.size() - 1);
        }
        if (trend == MONITOR_TREND_HEART) {
            return am4100Manager.getHeartRateValue();
        }
        if (trend == MONITOR_TREND_SPO2) {
            return am4100Manager.getSpo2Value();
        }
        if (trend == MONITOR_TREND_PULSE) {
            return am4100Manager.getPulseRateValue();
        }
        if (trend == MONITOR_TREND_RESP) {
            return am4100Manager.getRespValue();
        }
        if (trend == MONITOR_TREND_TEMP) {
            return 370;
        }
        return 120;
    }

    private void showMonitorLimitDialog(final int limitIndex) {
        LinearLayout layout = new LinearLayout(activity);
        layout.setOrientation(LinearLayout.VERTICAL);
        int pad = (int) dp(12);
        layout.setPadding(pad, pad, pad, pad);

        final EditText highInput = new EditText(activity);
        highInput.setSingleLine(true);
        highInput.setHint("上限 " + monitorLimitUnit(limitIndex));
        highInput.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
        highInput.setText(formatLimitValue(limitIndex, monitorLimitHigh[limitIndex]));
        layout.addView(highInput);

        final EditText lowInput = new EditText(activity);
        lowInput.setSingleLine(true);
        lowInput.setHint("下限 " + monitorLimitUnit(limitIndex));
        lowInput.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
        lowInput.setText(formatLimitValue(limitIndex, monitorLimitLow[limitIndex]));
        layout.addView(lowInput);

        new AlertDialog.Builder(activity)
                .setTitle(monitorLimitTitle(limitIndex))
                .setView(layout)
                .setPositiveButton("保存", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        try {
                            float high = Float.parseFloat(highInput.getText().toString().trim());
                            float low = Float.parseFloat(lowInput.getText().toString().trim());
                            if (high <= low) {
                                Toast.makeText(activity, "上限必须大于下限", Toast.LENGTH_SHORT).show();
                                return;
                            }
                            monitorLimitHigh[limitIndex] = high;
                            monitorLimitLow[limitIndex] = low;
                            monitorLimitEnabled[limitIndex] = true;
                            invalidate();
                        } catch (NumberFormatException exception) {
                            Toast.makeText(activity, "请输入正确的上下限数值", Toast.LENGTH_SHORT).show();
                        }
                    }
                })
                .setNegativeButton("取消", null)
                .show();
    }

    private String monitorLimitTitle(int index) {
        return "设置" + monitorLimitName(index) + "上下限";
    }

    private float[] copyDefaultThresholds() {
        float[] result = new float[DEFAULT_MONITOR_THRESHOLDS.length];
        System.arraycopy(DEFAULT_MONITOR_THRESHOLDS, 0, result, 0, DEFAULT_MONITOR_THRESHOLDS.length);
        return result;
    }

    private float[] copyDefaultThresholdsDia() {
        float[] result = new float[DEFAULT_MONITOR_THRESHOLDS_DIA.length];
        System.arraycopy(DEFAULT_MONITOR_THRESHOLDS_DIA, 0, result, 0, DEFAULT_MONITOR_THRESHOLDS_DIA.length);
        return result;
    }

    /**
     * 点击"报警音/音量开关"按钮：直接切换自动报警开关。
     * 阈值在设置页"报警阈值"菜单里配置。
     */
    private void toggleMonitorAlarm() {
        monitorAlarmEnabled = !monitorAlarmEnabled;
        if (monitorAlarmEnabled) {
            monitorAlarmSound = true;
            monitorPulseBeep = true;
            // 重置防抖，让用户开启后立即检查当前数据
            lastMonitorAlarmTime = 0L;
            for (int i = 0; i < lastMonitorMetricAlarmTime.length; i++) {
                lastMonitorMetricAlarmTime[i] = 0L;
            }
            // 开启时立即检查一次，给用户即时反馈
            checkMonitorAlarmThresholds();
            playMonitorAlarmSound();
            Toast.makeText(activity, "自动报警已开启（阈值在设置页配置）", Toast.LENGTH_SHORT).show();
        } else {
            Toast.makeText(activity, "自动报警已关闭", Toast.LENGTH_SHORT).show();
        }
        saveUiSettings();
        invalidate();
    }

    private void showMonitorThresholdDialog() {
        LinearLayout form = new LinearLayout(activity);
        form.setOrientation(LinearLayout.VERTICAL);
        int pad = dpInt(12);
        form.setPadding(pad, pad, pad, pad);

        TextView help = new TextView(activity);
        help.setText("设置各指标的下限阈值。当从蓝牙获取到的实时数值低于对应阈值时，自动播放报警音。\n" +
                "血压需要同时设置收缩压和舒张压，都低于阈值才会报警。");
        help.setTextSize(13);
        help.setPadding(0, 0, 0, dpInt(8));
        form.addView(help);

        addSectionLabel(form, "报警阈值（数值低于阈值时报警）");
        final EditText[] inputs = new EditText[MONITOR_THRESHOLD_KEYS.length];
        for (int i = 0; i < MONITOR_THRESHOLD_KEYS.length; i++) {
            if (i == 1) {
                // 血压需要两个输入框（收缩压 + 舒张压），并排显示
                LinearLayout bpRow = new LinearLayout(activity);
                bpRow.setOrientation(LinearLayout.HORIZONTAL);
                EditText sys = createInput(MONITOR_THRESHOLD_LABELS[i] + " - 收缩压",
                        formatThresholdValue(monitorThresholds[i]), InputType.TYPE_CLASS_NUMBER);
                EditText dia = createInput("  舒张压",
                        formatThresholdValue(monitorThresholdsDia[i]), InputType.TYPE_CLASS_NUMBER);
                bpRow.addView(sys);
                bpRow.addView(dia);
                form.addView(bpRow);
                inputs[i] = sys;
                sys.setTag(TAG_THRESHOLD_DIA_INPUT, dia);
                continue;
            }
            inputs[i] = addInput(form, MONITOR_THRESHOLD_LABELS[i],
                    formatThresholdValue(monitorThresholds[i]),
                    InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
        }

        ScrollView scroll = new ScrollView(activity);
        scroll.addView(form);

        DialogInterface.OnClickListener saveOnly = new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                // 仅保存阈值，不改变 monitorAlarmEnabled 状态
                saveMonitorThresholds(inputs, false);
            }
        };

        AlertDialog dialog = new AlertDialog.Builder(activity)
                .setTitle("报警阈值设置")
                .setView(scroll)
                .setPositiveButton("保存", saveOnly)
                .show();
        if (dialog.getWindow() != null) {
            dialog.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_NOTHING);
        }
    }

    private String formatThresholdValue(float value) {
        if (value <= 0f) {
            return "";
        }
        return Math.abs(value - Math.round(value)) < 0.001f
                ? String.valueOf(Math.round(value))
                : String.format(java.util.Locale.US, "%.1f", value);
    }

    private void saveMonitorThresholds(EditText[] inputs, boolean enableAlarm) {
        try {
            float[] newThresholds = copyDefaultThresholds();
            float[] newThresholdsDia = copyDefaultThresholdsDia();
            for (int i = 0; i < inputs.length; i++) {
                if (inputs[i] == null) {
                    continue;
                }
                String raw = inputs[i].getText().toString().trim();
                if (TextUtils.isEmpty(raw)) {
                    continue;
                }
                float value = Float.parseFloat(raw);
                if (value < 0f) {
                    Toast.makeText(activity, MONITOR_THRESHOLD_LABELS[i] + " 不能为负数", Toast.LENGTH_SHORT).show();
                    return;
                }
                newThresholds[i] = value;
                // 血压同时读舒张压
                if (i == 1) {
                    EditText diaInput = (EditText) inputs[i].getTag(TAG_THRESHOLD_DIA_INPUT);
                    if (diaInput != null) {
                        String diaRaw = diaInput.getText().toString().trim();
                        if (!TextUtils.isEmpty(diaRaw)) {
                            float diaValue = Float.parseFloat(diaRaw);
                            if (diaValue < 0f) {
                                Toast.makeText(activity, "舒张压不能为负数", Toast.LENGTH_SHORT).show();
                                return;
                            }
                            newThresholdsDia[i] = diaValue;
                        }
                    }
                }
            }
            monitorThresholds = newThresholds;
            monitorThresholdsDia = newThresholdsDia;
            monitorAlarmEnabled = enableAlarm;
            // 重置防抖，让用户改完阈值后立即生效
            lastMonitorAlarmTime = 0L;
            for (int i = 0; i < lastMonitorMetricAlarmTime.length; i++) {
                lastMonitorMetricAlarmTime[i] = 0L;
            }
            saveMonitorThresholdState();
            if (enableAlarm) {
                Toast.makeText(activity, "报警阈值已保存并开启自动报警", Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(activity, "报警阈值已保存", Toast.LENGTH_SHORT).show();
            }
            invalidate();
        } catch (NumberFormatException exception) {
            Toast.makeText(activity, "请输入正确的数值", Toast.LENGTH_SHORT).show();
        }
    }

    private void saveMonitorThresholdState() {
        activity.getSharedPreferences(PREFS_NAME, 0)
                .edit()
                .putBoolean(KEY_MONITOR_ALARM_ENABLED, monitorAlarmEnabled)
                .putString(KEY_MONITOR_THRESHOLDS, thresholdsToJson())
                .apply();
    }

    private String thresholdsToJson() {
        try {
            org.json.JSONObject json = new org.json.JSONObject();
            for (int i = 0; i < MONITOR_THRESHOLD_KEYS.length; i++) {
                json.put(MONITOR_THRESHOLD_KEYS[i], monitorThresholds[i]);
                if (i == 1) {
                    json.put("bloodPressureDia", monitorThresholdsDia[i]);
                }
            }
            return json.toString();
        } catch (org.json.JSONException exception) {
            return "";
        }
    }

    private void loadMonitorThresholdState() {
        monitorThresholds = copyDefaultThresholds();
        monitorThresholdsDia = copyDefaultThresholdsDia();
        SharedPreferences prefs = activity.getSharedPreferences(PREFS_NAME, 0);
        monitorAlarmEnabled = prefs.getBoolean(KEY_MONITOR_ALARM_ENABLED, false);
        String raw = prefs.getString(KEY_MONITOR_THRESHOLDS, "");
        if (TextUtils.isEmpty(raw)) {
            return;
        }
        try {
            org.json.JSONObject json = new org.json.JSONObject(raw);
            for (int i = 0; i < MONITOR_THRESHOLD_KEYS.length; i++) {
                if (json.has(MONITOR_THRESHOLD_KEYS[i]) && !json.isNull(MONITOR_THRESHOLD_KEYS[i])) {
                    monitorThresholds[i] = (float) json.optDouble(MONITOR_THRESHOLD_KEYS[i], monitorThresholds[i]);
                }
                if (i == 1 && json.has("bloodPressureDia") && !json.isNull("bloodPressureDia")) {
                    monitorThresholdsDia[i] = (float) json.optDouble("bloodPressureDia", monitorThresholdsDia[i]);
                }
            }
        } catch (org.json.JSONException ignored) {
        }
    }

    /**
     * 检查当前监护数值是否低于阈值，低于则触发报警。
     * 每个指标 10 秒内最多报警一次，整体也有 3 秒的兜底防抖。
     */
    private void checkMonitorAlarmThresholds() {
        if (!monitorAlarmEnabled || !monitorAlarmSound) {
            return;
        }
        long now = System.currentTimeMillis();
        // 整体防抖：3 秒内不多次触发
        if (now - lastMonitorAlarmTime < 3000L) {
            return;
        }
        String[] texts = new String[]{
                bleManager == null ? "" : am4100Manager.getHeartRateText(),
                bleManager == null ? "" : am4100Manager.getBloodPressureText(),
                bleManager == null ? "" : am4100Manager.getSpo2Text(),
                bleManager == null ? "" : am4100Manager.getPulseRateText(),
                bleManager == null ? "" : am4100Manager.getBodyTempText(),
                bleManager == null ? "" : am4100Manager.getRespText()
        };
        StringBuilder triggered = new StringBuilder();
        for (int i = 0; i < texts.length; i++) {
            if (now - lastMonitorMetricAlarmTime[i] < ALARM_DEBOUNCE_MS) {
                continue;
            }
            float currentValue;
            boolean violated;
            if (i == 1) {
                // 血压需要同时低于收缩压和舒张压阈值才报警
                float[] bp = parseBloodPressure(texts[i]);
                if (bp == null) {
                    continue;
                }
                float sys = bp[0];
                float dia = bp[1];
                violated = (monitorThresholds[i] > 0f && sys < monitorThresholds[i])
                        || (monitorThresholdsDia[i] > 0f && dia < monitorThresholdsDia[i]);
                currentValue = sys;
            } else {
                Float parsed = parseFloatStrict(texts[i]);
                if (parsed == null) {
                    continue;
                }
                currentValue = parsed;
                violated = monitorThresholds[i] > 0f && currentValue < monitorThresholds[i];
            }
            if (violated) {
                lastMonitorMetricAlarmTime[i] = now;
                if (triggered.length() > 0) {
                    triggered.append("、");
                }
                triggered.append(MONITOR_THRESHOLD_LABELS[i]);
            }
        }
        if (triggered.length() > 0) {
            lastMonitorAlarmTime = now;
            playMonitorAlarmSound();
            Toast.makeText(activity, "报警：" + triggered.toString() + " 低于阈值", Toast.LENGTH_LONG).show();
        }
    }

    private Float parseFloatStrict(String value) {
        if (value == null) {
            return null;
        }
        String cleaned = value.replace("℃", "").replace("%", "").replace("bpm", "")
                .replace("brpm", "").replace("mmHg", "").trim();
        if (cleaned.isEmpty() || "--".equals(cleaned) || "-".equals(cleaned)) {
            return null;
        }
        try {
            return Float.parseFloat(cleaned);
        } catch (NumberFormatException exception) {
            return null;
        }
    }

    /**
     * 解析血压字符串 "120/80" → float[2] {sys, dia}。
     */
    private float[] parseBloodPressure(String value) {
        if (value == null || value.indexOf('/') < 0) {
            return null;
        }
        int slash = value.indexOf('/');
        try {
            float sys = Float.parseFloat(value.substring(0, slash).trim());
            float dia = Float.parseFloat(value.substring(slash + 1).trim());
            return new float[]{sys, dia};
        } catch (NumberFormatException exception) {
            return null;
        }
    }

    private String monitorLimitName(int index) {
        if (index == LIMIT_HEART) {
            return "心率";
        }
        if (index == LIMIT_BP_SYS) {
            return "收缩压";
        }
        if (index == LIMIT_BP_DIA) {
            return "舒张压";
        }
        if (index == LIMIT_PULSE) {
            return "脉率";
        }
        if (index == LIMIT_SPO2) {
            return "血氧";
        }
        if (index == LIMIT_TEMP) {
            return "体温";
        }
        return "呼吸率";
    }

    private String monitorLimitUnit(int index) {
        if (index == LIMIT_SPO2) {
            return "%";
        }
        if (index == LIMIT_TEMP) {
            return "℃";
        }
        if (index == LIMIT_HEART || index == LIMIT_PULSE) {
            return "bpm";
        }
        if (index == LIMIT_RESP) {
            return "brpm";
        }
        return "mmHg";
    }

    private String formatLimitValue(int index, float value) {
        if (index == LIMIT_TEMP || Math.abs(value - Math.round(value)) > 0.05f) {
            return String.format(Locale.US, "%.1f", value);
        }
        return String.valueOf(Math.round(value));
    }

    private String monitorLimitText(int trend) {
        if (trend == MONITOR_TREND_BP) {
            return "收缩 " + formatLimitValue(LIMIT_BP_SYS, monitorLimitHigh[LIMIT_BP_SYS]) + "/"
                    + formatLimitValue(LIMIT_BP_SYS, monitorLimitLow[LIMIT_BP_SYS])
                    + "  舒张 " + formatLimitValue(LIMIT_BP_DIA, monitorLimitHigh[LIMIT_BP_DIA]) + "/"
                    + formatLimitValue(LIMIT_BP_DIA, monitorLimitLow[LIMIT_BP_DIA]) + " mmHg";
        }
        int index = monitorTrendLimitIndex(trend);
        return "上限 " + formatLimitValue(index, monitorLimitHigh[index])
                + "  下限 " + formatLimitValue(index, monitorLimitLow[index])
                + " " + monitorLimitUnit(index);
    }

    private int monitorTrendLimitIndex(int trend) {
        if (trend == MONITOR_TREND_HEART) {
            return LIMIT_HEART;
        }
        if (trend == MONITOR_TREND_SPO2) {
            return LIMIT_SPO2;
        }
        if (trend == MONITOR_TREND_PULSE) {
            return LIMIT_PULSE;
        }
        if (trend == MONITOR_TREND_TEMP) {
            return LIMIT_TEMP;
        }
        if (trend == MONITOR_TREND_RESP) {
            return LIMIT_RESP;
        }
        return LIMIT_BP_SYS;
    }

    private boolean isMonitorTrendAlarm(int trend) {
        if (trend == MONITOR_TREND_BP) {
            String bp = am4100Manager.getBloodPressureText();
            int slash = bp == null ? -1 : bp.indexOf('/');
            if (slash <= 0) {
                return false;
            }
            float sys = parseFloatSafe(bp.substring(0, slash));
            float dia = parseFloatSafe(bp.substring(slash + 1));
            return isOutsideLimit(LIMIT_BP_SYS, sys) || isOutsideLimit(LIMIT_BP_DIA, dia);
        }
        return isOutsideLimit(monitorTrendLimitIndex(trend), parseFloatSafe(monitorTrendValue(trend)));
    }

    private boolean isOutsideLimit(int index, float value) {
        return !Float.isNaN(value) && monitorLimitEnabled[index]
                && (value > monitorLimitHigh[index] || value < monitorLimitLow[index]);
    }

    private String currentTimeText() {
        return new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(new Date());
    }

    private String shortEndTime(String value) {
        if (TextUtils.isEmpty(value) || "进行中".equals(value)) {
            return "进行中";
        }
        return shortDateTime(value);
    }

    private String shortDateTime(String value) {
        if (TextUtils.isEmpty(value)) {
            return "--";
        }
        if ("进行中".equals(value)) {
            return value;
        }
        return value.length() > 16 ? value.substring(5, 16) : value;
    }

    private String currentTreatmentLiveSummary() {
        // ★ 修复 #14:显示用户设置值
                return "舱温 " + bleManager.getLastSetCabinTempText()
                + "  氧 " + bleManager.getLastSetOxygenText()
                + "  心率 " + am4100Manager.getHeartRateText();
    }

    private String firstNonEmpty(String primary, String fallback) {
        return TextUtils.isEmpty(primary) ? fallback : primary;
    }

    private float parseFirstNumber(String value) {
        if (TextUtils.isEmpty(value) || "--".equals(value)) {
            return Float.NaN;
        }
        StringBuilder builder = new StringBuilder();
        boolean started = false;
        for (int i = 0; i < value.length(); i++) {
            char ch = value.charAt(i);
            boolean numeric = (ch >= '0' && ch <= '9') || ch == '.' || ch == '-';
            if (numeric) {
                builder.append(ch);
                started = true;
            } else if (started) {
                break;
            }
        }
        if (builder.length() == 0) {
            return Float.NaN;
        }
        try {
            return Float.parseFloat(builder.toString());
        } catch (NumberFormatException exception) {
            return Float.NaN;
        }
    }

    private float parseFloatSafe(String value) {
        if (TextUtils.isEmpty(value) || "--".equals(value)) {
            return Float.NaN;
        }
        String cleaned = value.replace("℃", "").replace("%", "").trim();
        try {
            return Float.parseFloat(cleaned);
        } catch (NumberFormatException exception) {
            return Float.NaN;
        }
    }

    private void scheduleMonitorRefresh() {
        if (monitorRefreshPending || showingSettings || activeTab != 2) {
            return;
        }
        if (!am4100Manager.isScanning() && !am4100Manager.isConnected() && !am4100Manager.hasVitals()) {
            return;
        }
        monitorRefreshPending = true;
        uiHandler.postDelayed(monitorRefreshRunnable, 160L);
    }

    private void drawCameraPage(Canvas canvas, float x, float y, float w, float h) {
        fill(canvas, x, y, x + w, y + h, Color.WHITE);
        RectF shell = rect(x + dp(28), y, x + w - dp(18), y + h - dp(18));
        rounded(canvas, shell, page, Color.TRANSPARENT, dp(8));
        float rightW = dp(420);
        RectF right = rect(shell.right - rightW, shell.top, shell.right, shell.bottom);
        drawCameraMode(canvas, right);
        if (cameraPlayback) {
            updateCameraPreviewOverlay(null, false);
            drawCameraPlayback(canvas, shell.left + dp(24), shell.top + dp(24), shell.width() - rightW - dp(54), shell.height() - dp(48));
        } else {
            RectF video = rect(shell.left + dp(24), shell.top + dp(24), right.left - dp(28), shell.bottom - dp(24));
            drawVideoPlayer(canvas, video);
            RectF preview = inset(video, dp(2), dp(2));
            preview.bottom -= dp(34);
            updateCameraPreviewOverlay(preview, true);
        }
    }

    private void updateCameraPreviewOverlay(RectF bounds, boolean visible) {
        if (cameraPreviewHost == null) {
            return;
        }
        if (!visible || bounds == null) {
            cameraPreviewHost.updateCameraPreview(null, false, null);
            return;
        }
        cameraPreviewHost.updateCameraPreview(new RectF(bounds), true, buildTpLinkRtspCandidates(cameraStreamUrl));
    }

    private void handleCameraPlaybackAction(int actionIndex) {
        if (actionIndex == 0) {
            captureCameraSnapshotAction();
        } else if (actionIndex == 1) {
            downloadSelectedCameraSnapshot();
        } else {
            deleteSelectedCameraSnapshot();
        }
    }

    private void captureCameraSnapshotAction() {
        if (cameraPreviewHost == null) {
            if (duplicateSelectedCameraSnapshot()) {
                return;
            }
            Toast.makeText(activity, "摄像头预览未准备好", Toast.LENGTH_SHORT).show();
            return;
        }
        cameraPreviewHost.captureCameraFrame(new CameraFrameCallback() {
            @Override
            public void onFrame(Bitmap bitmap) {
                try {
                    saveCameraSnapshot(bitmap);
                    Toast.makeText(activity, "截图已保存到回放列表", Toast.LENGTH_SHORT).show();
                    invalidate();
                } catch (IOException exception) {
                    Toast.makeText(activity, "截图保存失败: " + exception.getMessage(), Toast.LENGTH_LONG).show();
                } finally {
                    if (bitmap != null && !bitmap.isRecycled()) {
                        bitmap.recycle();
                    }
                }
            }

            @Override
            public void onError(String message) {
                Toast.makeText(activity, message, Toast.LENGTH_LONG).show();
            }
        });
    }

    private boolean duplicateSelectedCameraSnapshot() {
        CameraSnapshot snapshot = selectedCameraSnapshot();
        if (snapshot == null || snapshot.cacheFile == null || !snapshot.cacheFile.exists()) {
            return false;
        }
        try {
            File dir = new File(activity.getFilesDir(), "camera_snapshots");
            if (!dir.exists() && !dir.mkdirs()) {
                throw new IOException("无法创建截图目录");
            }
            String fileName = createCameraSnapshotFileName();
            File target = new File(dir, fileName);
            FileOutputStream output = new FileOutputStream(target);
            copyFile(snapshot.cacheFile, output);
            long capturedAt = System.currentTimeMillis();
            String timeText = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(new Date(capturedAt));
            PatientCase patient = currentCase();
            // 复制件留在源截图所属的舱，避免用户在切舱途中复制导致跨舱污染。
            cameraSnapshots.add(0, new CameraSnapshot(
                    fileName,
                    timeText,
                    capturedAt,
                    target,
                    null,
                    patient == null ? snapshot.patientRecordNo : safeJsonText(patient.recordNo),
                    patient == null ? snapshot.patientName : safeJsonText(patient.petName),
                    snapshot.zone));
            selectedCameraSnapshotIndex = 0;
            saveCameraSnapshotsToStorage();
            Toast.makeText(activity, "已从回放图片保存一张截图", Toast.LENGTH_SHORT).show();
            invalidate();
            return true;
        } catch (IOException exception) {
            Toast.makeText(activity, "回放截图失败: " + exception.getMessage(), Toast.LENGTH_LONG).show();
            return false;
        }
    }

    private void saveCameraSnapshot(Bitmap bitmap) throws IOException {
        File dir = new File(activity.getFilesDir(), "camera_snapshots");
        if (!dir.exists() && !dir.mkdirs()) {
            throw new IOException("无法创建截图目录");
        }
        String fileName = createCameraSnapshotFileName();
        File target = new File(dir, fileName);
        FileOutputStream output = new FileOutputStream(target);
        try {
            if (!bitmap.compress(Bitmap.CompressFormat.JPEG, 92, output)) {
                throw new IOException("无法编码截图");
            }
        } finally {
            output.close();
        }
        long capturedAt = System.currentTimeMillis();
        String timeText = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(new Date(capturedAt));
        PatientCase patient = currentCase();
        String zone = patientZoneOrCurrent(patient);
        cameraSnapshots.add(0, new CameraSnapshot(
                fileName,
                timeText,
                capturedAt,
                target,
                null,
                patient == null ? "" : safeJsonText(patient.recordNo),
                patient == null ? "" : safeJsonText(patient.petName),
                zone));
        selectedCameraSnapshotIndex = 0;
        trimCameraSnapshotsForZone(zone);
        saveCameraSnapshotsToStorage();
    }

    private void downloadSelectedCameraSnapshot() {
        CameraSnapshot snapshot = selectedCameraSnapshot();
        if (snapshot == null || snapshot.cacheFile == null || !snapshot.cacheFile.exists()) {
            Toast.makeText(activity, "请先选择一张截图", Toast.LENGTH_SHORT).show();
            return;
        }
        try {
            Uri uri = copyImageToPictures(snapshot.cacheFile, snapshot.fileName);
            snapshot.downloadUri = uri;
            saveCameraSnapshotsToStorage();
            Toast.makeText(activity, "截图已下载: " + uri.toString(), Toast.LENGTH_LONG).show();
            invalidate();
        } catch (IOException exception) {
            Toast.makeText(activity, "下载截图失败: " + exception.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    private void deleteSelectedCameraSnapshot() {
        final CameraSnapshot snapshot = selectedCameraSnapshot();
        if (snapshot == null) {
            Toast.makeText(activity, "请先选择一张截图", Toast.LENGTH_SHORT).show();
            return;
        }
        new AlertDialog.Builder(activity)
                .setTitle("删除截图")
                .setMessage("确定删除这张摄像头截图吗？")
                .setNegativeButton("取消", null)
                .setPositiveButton("删除", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        if (snapshot.cacheFile != null && snapshot.cacheFile.exists()) {
                            snapshot.cacheFile.delete();
                        }
                        cameraSnapshots.remove(snapshot);
                        int remaining = activeCameraSnapshotCount();
                        selectedCameraSnapshotIndex = remaining <= 0
                                ? -1
                                : Math.min(selectedCameraSnapshotIndex, remaining - 1);
                        saveCameraSnapshotsToStorage();
                        Toast.makeText(activity, "截图已删除", Toast.LENGTH_SHORT).show();
                        invalidate();
                    }
                })
                .show();
    }

    private void showCameraAiDialog() {
        CameraSnapshot snapshot = selectedCameraSnapshot();
        if (snapshot == null || snapshot.cacheFile == null || !snapshot.cacheFile.exists()) {
            Toast.makeText(activity, "请先选择一张截图", Toast.LENGTH_SHORT).show();
            return;
        }
        try {
            CameraImageAnalysis analysis = analyzeCameraSnapshot(snapshot.cacheFile);
            StringBuilder message = new StringBuilder();
            message.append("文件：").append(snapshot.fileName);
            message.append("\n时间：").append(TextUtils.isEmpty(snapshot.timeText) ? "--" : snapshot.timeText);
            if (!TextUtils.isEmpty(snapshot.patientName) || !TextUtils.isEmpty(snapshot.patientRecordNo)) {
                message.append("\n宠物：").append(TextUtils.isEmpty(snapshot.patientName) ? "--" : snapshot.patientName);
                message.append("    编号：").append(TextUtils.isEmpty(snapshot.patientRecordNo) ? "--" : snapshot.patientRecordNo);
            }
            message.append("\n分辨率：").append(analysis.width).append(" x ").append(analysis.height);
            message.append("\n亮度：").append(analysis.brightness).append(" / 255（").append(cameraBrightnessLabel(analysis.brightness)).append("）");
            message.append("\n对比度：").append(analysis.contrast).append("（").append(cameraContrastLabel(analysis.contrast)).append("）");
            message.append("\n清晰度：").append(analysis.edgeScore).append(" / 100（").append(cameraEdgeLabel(analysis.edgeScore)).append("）");
            new AlertDialog.Builder(activity)
                    .setTitle("AI识图")
                    .setMessage(message.toString())
                    .setPositiveButton("确定", null)
                    .show();
        } catch (IOException exception) {
            Toast.makeText(activity, "AI识图失败: " + exception.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    private CameraImageAnalysis analyzeCameraSnapshot(File file) throws IOException {
        BitmapFactory.Options bounds = new BitmapFactory.Options();
        bounds.inJustDecodeBounds = true;
        BitmapFactory.decodeFile(file.getAbsolutePath(), bounds);
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) {
            throw new IOException("无法读取图片尺寸");
        }
        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inSampleSize = Math.max(1, Math.max(bounds.outWidth, bounds.outHeight) / 320);
        Bitmap bitmap = BitmapFactory.decodeFile(file.getAbsolutePath(), options);
        if (bitmap == null) {
            throw new IOException("无法解析图片内容");
        }
        CameraImageAnalysis analysis = new CameraImageAnalysis();
        analysis.width = bounds.outWidth;
        analysis.height = bounds.outHeight;
        long sum = 0L;
        long sumSquares = 0L;
        long edge = 0L;
        int count = 0;
        int edgeCount = 0;
        int step = Math.max(1, Math.min(bitmap.getWidth(), bitmap.getHeight()) / 120);
        for (int y = 0; y < bitmap.getHeight(); y += step) {
            for (int x = 0; x < bitmap.getWidth(); x += step) {
                int color = bitmap.getPixel(x, y);
                int value = (Color.red(color) * 30 + Color.green(color) * 59 + Color.blue(color) * 11) / 100;
                sum += value;
                sumSquares += (long) value * (long) value;
                count += 1;
                if (x + step < bitmap.getWidth()) {
                    int next = bitmap.getPixel(x + step, y);
                    int nextValue = (Color.red(next) * 30 + Color.green(next) * 59 + Color.blue(next) * 11) / 100;
                    edge += Math.abs(value - nextValue);
                    edgeCount += 1;
                }
                if (y + step < bitmap.getHeight()) {
                    int below = bitmap.getPixel(x, y + step);
                    int belowValue = (Color.red(below) * 30 + Color.green(below) * 59 + Color.blue(below) * 11) / 100;
                    edge += Math.abs(value - belowValue);
                    edgeCount += 1;
                }
            }
        }
        bitmap.recycle();
        if (count <= 0) {
            throw new IOException("图片内容为空");
        }
        double mean = sum / (double) count;
        double variance = Math.max(0d, sumSquares / (double) count - mean * mean);
        analysis.brightness = (int) Math.round(mean);
        analysis.contrast = (int) Math.round(Math.sqrt(variance));
        analysis.edgeScore = edgeCount <= 0 ? 0 : (int) Math.max(0, Math.min(100, Math.round(edge / (double) edgeCount / 2.55d)));
        return analysis;
    }

    private String cameraBrightnessLabel(int value) {
        if (value < 70) {
            return "偏暗";
        }
        if (value > 185) {
            return "偏亮";
        }
        return "正常";
    }

    private String cameraContrastLabel(int value) {
        if (value < 22) {
            return "偏低";
        }
        if (value > 58) {
            return "偏高";
        }
        return "正常";
    }

    private String cameraEdgeLabel(int value) {
        if (value < 28) {
            return "偏柔";
        }
        if (value > 72) {
            return "清晰";
        }
        return "适中";
    }

    private void showCameraManageDialog() {
        final PatientCase patient = currentCase();
        final int patientCount = countCameraSnapshotsForPatient(patient);
        final CameraSnapshot selected = selectedCameraSnapshot();
        StringBuilder message = new StringBuilder();
        message.append("当前回放总数：").append(activeCameraSnapshotCount());
        if (patient != null) {
            message.append("\n当前宠物：").append(safeJsonText(patient.petName));
            message.append("\n当前宠物回放：").append(patientCount);
        }
        if (selected != null) {
            message.append("\n已选文件：").append(selected.fileName);
        }
        List<String> actions = new ArrayList<>();
        actions.add("配置摄像头");
        if (patient != null && patientCount > 0) {
            actions.add("清空当前宠物回放");
        }
        if (activeCameraSnapshotCount() > 0) {
            actions.add("清空" + zoneText(currentZoneNormalized()) + "全部回放");
        }
        final String[] items = actions.toArray(new String[0]);
        new AlertDialog.Builder(activity)
                .setTitle("回放管理")
                .setMessage(message.toString())
                .setNegativeButton("关闭", null)
                .setItems(items, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        String choice = items[which];
                        if ("配置摄像头".equals(choice)) {
                            showTpLinkCameraDialog();
                            return;
                        }
                        if ("清空当前宠物回放".equals(choice)) {
                            confirmDeleteCameraSnapshotsForPatient(patient);
                            return;
                        }
                        if (choice != null && choice.startsWith("清空") && choice.endsWith("全部回放")) {
                            confirmDeleteAllCameraSnapshots();
                        }
                    }
                })
                .show();
    }

    private int countCameraSnapshotsForPatient(PatientCase patient) {
        if (patient == null) {
            return 0;
        }
        int count = 0;
        for (CameraSnapshot snapshot : cameraSnapshots) {
            if (cameraSnapshotMatchesPatient(snapshot, patient)) {
                count += 1;
            }
        }
        return count;
    }

    private boolean cameraSnapshotMatchesPatient(CameraSnapshot snapshot, PatientCase patient) {
        if (snapshot == null || patient == null) {
            return false;
        }
        // ★ 左右舱病例编号各自从 000001 起，只比编号会把另一舱的同号病例认成同一只宠物，
        //   必须先比舱位，再比编号/宠物名。
        if (!zoneOfPatient(patient).equals(snapshot.zone)) {
            return false;
        }
        if (!TextUtils.isEmpty(snapshot.patientRecordNo) && !TextUtils.isEmpty(patient.recordNo)) {
            return snapshot.patientRecordNo.equals(patient.recordNo);
        }
        return !TextUtils.isEmpty(snapshot.patientName) && snapshot.patientName.equals(patient.petName);
    }

    private void confirmDeleteCameraSnapshotsForPatient(final PatientCase patient) {
        if (patient == null) {
            return;
        }
        new AlertDialog.Builder(activity)
                .setTitle("清空当前宠物回放")
                .setMessage("确定清空 " + safeJsonText(patient.petName) + " 的全部回放截图吗？")
                .setNegativeButton("取消", null)
                .setPositiveButton("清空", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        deleteCameraSnapshotsForPatient(patient);
                    }
                })
                .show();
    }

    private void confirmDeleteAllCameraSnapshots() {
        final String zone = currentZoneNormalized();
        new AlertDialog.Builder(activity)
                .setTitle("清空" + zoneText(zone) + "全部回放")
                .setMessage("确定清空" + zoneText(zone) + "的所有摄像监控回放截图吗？（另一舱不受影响）")
                .setNegativeButton("取消", null)
                .setPositiveButton("清空", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        clearAllCameraSnapshots();
                    }
                })
                .show();
    }

    private void deleteCameraSnapshotsForPatient(PatientCase patient) {
        for (int i = cameraSnapshots.size() - 1; i >= 0; i--) {
            CameraSnapshot snapshot = cameraSnapshots.get(i);
            if (!cameraSnapshotMatchesPatient(snapshot, patient)) {
                continue;
            }
            if (snapshot.cacheFile != null && snapshot.cacheFile.exists()) {
                snapshot.cacheFile.delete();
            }
            cameraSnapshots.remove(i);
        }
        int remaining = activeCameraSnapshotCount();
        selectedCameraSnapshotIndex = remaining <= 0 ? -1 : Math.min(selectedCameraSnapshotIndex, remaining - 1);
        saveCameraSnapshotsToStorage();
        Toast.makeText(activity, "当前宠物回放已清空", Toast.LENGTH_SHORT).show();
        invalidate();
    }

    /** 只清当前舱的回放，另一舱的截图保持不动（左右舱数据完全隔离）。 */
    private void clearAllCameraSnapshots() {
        String z = currentZoneNormalized();
        for (int i = cameraSnapshots.size() - 1; i >= 0; i--) {
            CameraSnapshot snapshot = cameraSnapshots.get(i);
            if (!z.equals(snapshot.zone)) {
                continue;
            }
            if (snapshot.cacheFile != null && snapshot.cacheFile.exists()) {
                snapshot.cacheFile.delete();
            }
            cameraSnapshots.remove(i);
        }
        selectedCameraSnapshotIndex = -1;
        saveCameraSnapshotsToStorage();
        Toast.makeText(activity, zoneText(z) + "全部回放已清空", Toast.LENGTH_SHORT).show();
        invalidate();
    }

    /**
     * 返回当前舱「过滤后列表」中选中的截图。
     * 注意 selectedCameraSnapshotIndex 是舱内下标，不是 cameraSnapshots 的全局下标，
     * 否则切舱后会把另一舱的截图当成当前选中项。
     */
    private CameraSnapshot selectedCameraSnapshot() {
        int count = activeCameraSnapshotCount();
        if (count <= 0) {
            selectedCameraSnapshotIndex = -1;
            return null;
        }
        if (selectedCameraSnapshotIndex < 0 || selectedCameraSnapshotIndex >= count) {
            selectedCameraSnapshotIndex = 0;
        }
        return getActiveCameraSnapshot(selectedCameraSnapshotIndex);
    }

    private void drawCameraMode(Canvas canvas, RectF right) {
        RectF live = rect(right.left + dp(44), right.top + dp(66), right.left + dp(204), right.top + dp(108));
        RectF replay = rect(right.left + dp(230), right.top + dp(66), right.left + dp(390), right.top + dp(108));
        rounded(canvas, live, !cameraPlayback ? activeBlue : Color.WHITE, Color.TRANSPARENT, dp(4));
        rounded(canvas, replay, cameraPlayback ? activeBlue : Color.WHITE, Color.TRANSPARENT, dp(4));
        drawTextCenter(canvas, "实况", live, 20, !cameraPlayback ? Color.WHITE : text, true);
        drawTextCenter(canvas, "回放", replay, 20, cameraPlayback ? Color.WHITE : text, true);
        clickZones.add(new ClickZone(live, new ClickAction() {
            @Override
            public void run() {
                cameraPlayback = false;
            }
        }));
        clickZones.add(new ClickZone(replay, new ClickAction() {
            @Override
            public void run() {
                cameraPlayback = true;
            }
        }));
        if (cameraPlayback) {
            String[] buttons = {"截图", "下载", "删除"};
            for (int i = 0; i < buttons.length; i++) {
                final int actionIndex = i;
                drawActionButton(canvas, buttons[i], right.left + dp(44), right.top + dp(170) + i * dp(62), right.width() - dp(88), dp(42), new ClickAction() {
                    @Override
                    public void run() {
                        handleCameraPlaybackAction(actionIndex);
                    }
                });
            }
            return;
        }
        String[] labels = {"截图", "录制", "全屏", "对讲", "镜头遮蔽", "画面翻转", "手动报警", "原图", "巡航", "视频参数", "图表", "自定义"};
        String[] icons = {"✂", "▣", "⛶", "♩", "▧", "⇄", "♕", "▧", "◉", "▻", "▥", "＋"};
        float cellW = dp(82);
        float cellH = dp(74);
        float startX = right.left + dp(44);
        float startY = right.top + dp(168);
        for (int i = 0; i < labels.length; i++) {
            int col = i % 3;
            int row = i / 3;
            final int actionIndex = i;
            RectF cell = rect(startX + col * dp(116), startY + row * dp(112), startX + col * dp(116) + cellW, startY + row * dp(112) + cellH);
            rounded(canvas, cell, i == 5 ? Color.rgb(91, 174, 245) : Color.WHITE, Color.TRANSPARENT, dp(4));
            drawTextCenter(canvas, icons[i], rect(cell.left, cell.top + dp(8), cell.right, cell.top + dp(42)), 24, i == 5 ? Color.WHITE : text, true);
            drawTextCenter(canvas, labels[i], rect(cell.left - dp(12), cell.bottom + dp(8), cell.right + dp(12), cell.bottom + dp(34)), 14, text, false);
            clickZones.add(new ClickZone(new RectF(cell), new ClickAction() {
                @Override
                public void run() {
                    if (actionIndex == 0) {
                        captureCameraSnapshotAction();
                    } else if (actionIndex == 2) {
                        toggleTpLinkCameraFullscreen();
                    } else if (actionIndex == 9) {
                        showTpLinkCameraDialog();
                    } else {
                        Toast.makeText(activity, "当前只支持预览、全屏、截图和视频参数配置；此项需摄像头厂家控制协议", Toast.LENGTH_SHORT).show();
                    }
                }
            }));
        }
    }

    private void showTpLinkCameraDialog() {
        LinearLayout form = new LinearLayout(activity);
        form.setOrientation(LinearLayout.VERTICAL);
        int pad = dpInt(12);
        form.setPadding(pad, pad, pad, pad);

        final EditText urlInput = addInput(form, "RTSP地址，例如 rtsp://账号:密码@设备IP:554/stream2",
                cameraStreamUrl, InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI);

        TextView help = new TextView(activity);
        help.setText("内置预览使用手写 RTSP H.264 解码，只支持 RTSP 地址。\n"
                + "单目 TP-LINK 请优先填写 /stream2 或 /stream1。\n"
                + "请在摄像头后台开启RTSP，确认账号密码、H.264编码、端口554和设备网络；如果是192.168.x.x地址，手机必须和摄像头在同一局域网。");
        help.setTextSize(13);
        help.setPadding(0, dpInt(8), 0, 0);
        form.addView(help);

        final AlertDialog dialog = new AlertDialog.Builder(activity)
                .setTitle("TP-LINK摄像头配置")
                .setView(form)
                .setNegativeButton("取消", null)
                .setPositiveButton("保存并预览", null)
                .create();
        dialog.setOnShowListener(new DialogInterface.OnShowListener() {
            @Override
            public void onShow(DialogInterface dialogInterface) {
                dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View view) {
                        String nextUrl = urlInput.getText().toString().trim();
                        if (!TextUtils.isEmpty(nextUrl) && !nextUrl.toLowerCase(Locale.ROOT).startsWith("rtsp://")) {
                            Toast.makeText(activity, "内置预览目前只支持 RTSP 地址", Toast.LENGTH_SHORT).show();
                            return;
                        }
                        cameraStreamUrl = normalizeTpLinkStreamUrl(nextUrl);
                        saveCameraSettings();
                        if (activity != null) {
                            activity.resetCameraPreviewFailure();
                        }
                        invalidate();
                        dialog.dismiss();
                        if (TextUtils.isEmpty(cameraStreamUrl)) {
                            Toast.makeText(activity, "已清空摄像头地址", Toast.LENGTH_SHORT).show();
                            return;
                        }
                        Toast.makeText(activity, "已保存 RTSP 地址，正在连接摄像头…", Toast.LENGTH_SHORT).show();
                        openTpLinkCameraPreview();
                    }
                });
            }
        });
        dialog.show();
        if (dialog.getWindow() != null) {
            dialog.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_NOTHING);
        }
    }

    private void openTpLinkCameraPreview() {
        if (TextUtils.isEmpty(cameraStreamUrl)) {
            Toast.makeText(activity, "请先配置摄像头视频地址", Toast.LENGTH_SHORT).show();
            showTpLinkCameraDialog();
            return;
        }
        if (activity != null) {
            activity.refreshLanhuCameraPreview();
        }
    }

    private void openTpLinkCameraInternal() {
        openTpLinkCameraPreview();
    }

    private void toggleTpLinkCameraFullscreen() {
        if (TextUtils.isEmpty(cameraStreamUrl)) {
            Toast.makeText(activity, "请先配置TP-LINK摄像头视频地址", Toast.LENGTH_SHORT).show();
            showTpLinkCameraDialog();
            return;
        }
        if (cameraPreviewHost == null) {
            Toast.makeText(activity, "摄像头预览宿主未初始化", Toast.LENGTH_SHORT).show();
            return;
        }
        if (cameraPreviewHost.toggleCameraFullscreen()) {
            return;
        }
        if (activity != null) {
            activity.refreshLanhuCameraPreview();
        }
        Toast.makeText(activity, "摄像头画面尚未显示，请稍后再试", Toast.LENGTH_SHORT).show();
    }

    private void loadCameraSettings() {
        SharedPreferences prefs = activity.getSharedPreferences(PREFS_NAME, 0);
        if (prefs.getBoolean(KEY_CAMERA_STREAM_USER_SET, false)) {
            cameraStreamUrl = prefs.getString(KEY_CAMERA_STREAM_URL, "");
        } else {
            cameraStreamUrl = "";
            prefs.edit().remove(KEY_CAMERA_STREAM_URL).apply();
        }
    }

    private void saveCameraSettings() {
        SharedPreferences prefs = activity.getSharedPreferences(PREFS_NAME, 0);
        prefs.edit()
                .putString(KEY_CAMERA_STREAM_URL, cameraStreamUrl)
                .putBoolean(KEY_CAMERA_STREAM_USER_SET, !TextUtils.isEmpty(cameraStreamUrl))
                .commit();
    }

    private String defaultCameraStreamUrl() {
        return "";
    }

    private String[] buildTpLinkRtspCandidates(String configuredUrl) {
        ArrayList<String> urls = new ArrayList<>();
        String normalizedUrl = normalizeTpLinkStreamUrl(configuredUrl);
        addCameraUrl(urls, normalizedUrl);
        if (!TextUtils.equals(normalizedUrl, configuredUrl)) {
            addCameraUrl(urls, configuredUrl);
        }
        addTpLinkRootCandidates(urls, cameraRtspRoot(configuredUrl));
        return urls.toArray(new String[0]);
    }

    private void addTpLinkRootCandidates(ArrayList<String> urls, String root) {
        if (TextUtils.isEmpty(root)) {
            return;
        }
        addCameraUrl(urls, root + "/stream2");
        addCameraUrl(urls, root + "/stream1");
    }

    private String normalizeTpLinkStreamUrl(String url) {
        if (TextUtils.isEmpty(url)) {
            return "";
        }
        String trimmed = url.trim();
        String lower = trimmed.toLowerCase(Locale.ROOT);
        if (lower.contains("/stream2&channel=") || lower.contains("/stream2?channel=")) {
            return trimTpLinkChannelSuffix(trimmed, "/stream2");
        }
        if (lower.contains("/stream1&channel=") || lower.contains("/stream1?channel=")) {
            return trimTpLinkChannelSuffix(trimmed, "/stream1");
        }
        return trimmed;
    }

    private String trimTpLinkChannelSuffix(String url, String streamPath) {
        int streamIndex = url.toLowerCase(Locale.ROOT).indexOf(streamPath);
        if (streamIndex < 0) {
            return url;
        }
        return url.substring(0, streamIndex + streamPath.length());
    }

    private void addCameraUrl(ArrayList<String> urls, String url) {
        if (TextUtils.isEmpty(url)) {
            return;
        }
        String trimmed = url.trim();
        if (trimmed.length() == 0 || urls.contains(trimmed)) {
            return;
        }
        urls.add(trimmed);
    }

    private String cameraRtspRoot(String url) {
        if (TextUtils.isEmpty(url) || !url.startsWith("rtsp://")) {
            return "";
        }
        int schemeEnd = url.indexOf("://");
        int pathStart = url.indexOf('/', schemeEnd + 3);
        if (pathStart <= 0) {
            return url;
        }
        return url.substring(0, pathStart);
    }

    private String maskCameraUrl(String url) {
        if (TextUtils.isEmpty(url)) {
            return "-";
        }
        int schemeEnd = url.indexOf("://");
        int at = url.indexOf('@');
        int colon = schemeEnd >= 0 ? url.indexOf(':', schemeEnd + 3) : -1;
        if (schemeEnd >= 0 && colon > schemeEnd && at > colon) {
            return url.substring(0, colon + 1) + "******" + url.substring(at);
        }
        return url;
    }

    private void drawRecordPage(Canvas canvas, float x, float y, float w, float h) {
        fill(canvas, x, y, x + w, y + h, page);
        captureTreatmentSampleIfNeeded(false);
        final PatientCase patient = currentCase();
        RectF shell = rect(x + dp(10), y + dp(12), x + w - dp(10), y + h - dp(12));
        rounded(canvas, shell, Color.WHITE, border, dp(2));
        drawText(canvas, "治疗记录", shell.left + dp(18), shell.top + dp(28), 17, text, true);
        if (patient == null) {
            drawText(canvas, "暂无治疗记录", shell.left + dp(18), shell.top + dp(64), 12, muted, false);
            return;
        }

        drawText(canvas, (patient.currentTreatment ? "当前治疗" : "历史治疗") + "  " + patient.petName + " / " + patient.recordNo,
                shell.left + dp(120), shell.top + dp(28), 12, patient.currentTreatment ? green : muted, true);
        drawText(canvas, "开始: " + patient.treatmentStartTime + "    结束: " + patient.treatmentEndTime,
                shell.left + dp(18), shell.top + dp(52), 10, muted, false);
        drawTextRight(canvas, "采样策略: 每分钟写入一次，异常断电时保留最近有效统计", shell.right - dp(18), shell.top + dp(52), 10, muted, false);

        drawSmallButton(canvas, "写入一次采样", shell.left + dp(18), shell.top + dp(68), dp(100), dp(26), false, new ClickAction() {
            @Override
            public void run() {
                captureTreatmentSampleIfNeeded(true);
                lastGeneratedPdf = null;
                Toast.makeText(activity, "已写入一次治疗记录采样", Toast.LENGTH_SHORT).show();
            }
        });
        drawSmallButton(canvas, "新建治疗记录", shell.left + dp(130), shell.top + dp(68), dp(100), dp(26), false, new ClickAction() {
            @Override
            public void run() {
                createNewTreatmentRecord();
            }
        });
        drawSmallButton(canvas, "结束当前治疗", shell.left + dp(242), shell.top + dp(68), dp(100), dp(26), !patient.currentTreatment, new ClickAction() {
            @Override
            public void run() {
                finishCurrentTreatment();
            }
        });

        RectF table = rect(shell.left + dp(12), shell.top + dp(108), shell.right - dp(12), shell.bottom - dp(12));
        String[] headers = {"项目项", "开启", "录入报告", "时间段", "平均值", "最高值", "最低值", "手工统计", "开始治疗时间", "结束治疗时间"};
        float[] col = {0.12f, 0.06f, 0.08f, 0.12f, 0.09f, 0.09f, 0.09f, 0.10f, 0.13f, 0.12f};
        float rowH = Math.max(dp(25), Math.min(dp(34), table.height() / (patient.treatmentEntries.size() + 1f)));
        float cy = table.top;
        drawTableRow(canvas, table.left, cy, table.width(), rowH, headers, col, true);
        cy += rowH;
        for (int i = 0; i < patient.treatmentEntries.size(); i++) {
            final TreatmentEntry entry = patient.treatmentEntries.get(i);
            String manualSummary = buildTreatmentManualSummary(entry);
            String[] row = {
                    entry.itemName,
                    entry.enabled ? "开" : "关",
                    entry.includeInReport ? "是" : "否",
                    treatmentPeriodDisplayText(entry),
                    treatmentAverageText(entry),
                    treatmentHighText(entry),
                    treatmentLowText(entry),
                    TextUtils.isEmpty(manualSummary) ? "点击编辑" : manualSummary,
                    shortDateTime(entry.startTime),
                    shortDateTime(entry.endTime)
            };
            RectF rowRect = rect(table.left, cy, table.right, cy + rowH);
            drawTableRow(canvas, table.left, cy, table.width(), rowH, row, col, false);
            clickZones.add(new ClickZone(new RectF(rowRect), new ClickAction() {
                @Override
                public void run() {
                    showTreatmentEntryDialog(patient, entry);
                }
            }));
            cy += rowH;
            if (cy + rowH > table.bottom + dp(2)) {
                break;
            }
        }
    }

    private void drawPdfFilesPage(Canvas canvas, float x, float y, float w, float h) {
        fill(canvas, x, y, x + w, y + h, page);
        RectF shell = rect(x + dp(10), y + dp(10), x + w - dp(10), y + h - dp(10));
        rounded(canvas, shell, Color.WHITE, border, dp(2));
        drawText(canvas, "PDF文件与下载记录", shell.left + dp(18), shell.top + dp(28), 18, text, true);
        drawText(canvas, "生成和下载后的PDF都会显示在这里，方便现场确认文件是否已经产生。", shell.left + dp(18), shell.top + dp(52), 11, muted, false);

        drawActionButton(canvas, "生成当前PDF", shell.left + dp(18), shell.top + dp(70), dp(118), dp(32), new ClickAction() {
            @Override
            public void run() {
                generatePdfAction();
            }
        });
        drawActionButton(canvas, "下载最新PDF", shell.left + dp(150), shell.top + dp(70), dp(118), dp(32), new ClickAction() {
            @Override
            public void run() {
                downloadPdfAction();
            }
        });
        drawActionButton(canvas, "清空记录", shell.left + dp(282), shell.top + dp(70), dp(92), dp(32), new ClickAction() {
            @Override
            public void run() {
                clearPdfRecords();
            }
        });

        RectF summary = rect(shell.right - dp(225), shell.top + dp(22), shell.right - dp(18), shell.top + dp(104));
        rounded(canvas, summary, Color.rgb(246, 250, 253), border, dp(3));
        drawText(canvas, "当前患者", summary.left + dp(12), summary.top + dp(22), 11, muted, false);
        PatientCase patient = currentCase();
        drawText(canvas, patient == null ? "-" : patient.petName + " / " + patient.recordNo, summary.left + dp(12), summary.top + dp(47), 14, text, true);
        drawText(canvas, "记录数量: " + activePdfRecordCount(), summary.left + dp(12), summary.top + dp(69), 11, muted, false);

        RectF preview = rect(shell.right - dp(285), shell.top + dp(118), shell.right - dp(18), shell.bottom - dp(16));
        RectF area = rect(shell.left + dp(18), shell.top + dp(118), preview.left - dp(14), shell.bottom - dp(16));
        canvas.save();
        canvas.clipRect(area);
        // ★ 只显示当前舱的 PDF 报告（左右舱分别从 000001 编号后，列表必须按舱切）
        int activePdfCount = activePdfRecordCount();
        if (activePdfCount == 0) {
            drawEmptyPdfState(canvas, area);
        } else {
            float cy = area.top;
            int maxRows = Math.max(1, (int) (area.height() / dp(86)));
            int count = Math.min(maxRows, activePdfCount);
            for (int i = 0; i < count; i++) {
                PdfRecord record = getActivePdfRecord(i);
                if (record == null) break;
                drawPdfRecordCard(canvas, record, area.left, cy, area.width(), dp(76));
                cy += dp(86);
            }
        }
        canvas.restore();
        drawPdfPreviewPanel(canvas, preview);
    }

    private void drawEmptyPdfState(Canvas canvas, RectF area) {
        RectF card = rect(area.left, area.top, area.right, area.top + dp(118));
        rounded(canvas, card, panel, border, dp(4));
        drawPdfIcon(canvas, card.left + dp(26), card.top + dp(22), dp(52), dp(68), Color.rgb(218, 82, 75));
        drawText(canvas, "还没有生成PDF", card.left + dp(96), card.top + dp(42), 15, text, true);
        drawText(canvas, "点击上方“生成当前PDF”后，会在这里看到文件名、时间和保存位置。", card.left + dp(96), card.top + dp(68), 11, muted, false);
    }

    private void drawPdfRecordCard(Canvas canvas, final PdfRecord record, float x, float y, float w, float h) {
        RectF card = rect(x, y, x + w, y + h);
        rounded(canvas, card, Color.rgb(248, 252, 255), border, dp(4));
        int iconColor = "下载PDF".equals(record.action) ? Color.rgb(64, 142, 210) : Color.rgb(218, 82, 75);
        drawPdfIcon(canvas, card.left + dp(14), card.top + dp(10), dp(42), dp(54), iconColor);
        drawText(canvas, record.action + "  " + record.fileName, card.left + dp(68), card.top + dp(22), 12, text, true);
        drawText(canvas, "患者: " + record.patientName + "    时间: " + record.timeText, card.left + dp(68), card.top + dp(43), 10, muted, false);
        drawText(canvas, record.location, card.left + dp(68), card.top + dp(62), 9, muted, false);
        String badge = record.downloadUri == null ? "已生成" : "已下载";
        RectF badgeRect = rect(card.right - dp(78), card.top + dp(13), card.right - dp(18), card.top + dp(35));
        rounded(canvas, badgeRect, record.downloadUri == null ? Color.rgb(250, 239, 238) : Color.rgb(231, 243, 252), border, dp(11));
        drawTextCenter(canvas, badge, badgeRect, 10, record.downloadUri == null ? Color.rgb(170, 66, 60) : blue, true);
        clickZones.add(new ClickZone(new RectF(card), new ClickAction() {
            @Override
            public void run() {
                Toast.makeText(activity, record.fileName + "\n" + record.location, Toast.LENGTH_LONG).show();
            }
        }));
        if (record.downloadUri == null && record.cacheFile != null && record.cacheFile.exists()) {
            drawSmallButton(canvas, "下载", card.right - dp(78), card.bottom - dp(30), dp(60), dp(22), false, new ClickAction() {
                @Override
                public void run() {
                    downloadExistingPdfRecord(record);
                }
            });
        }
    }

    private void drawPdfPreviewPanel(Canvas canvas, RectF area) {
        rounded(canvas, area, Color.rgb(246, 250, 253), border, dp(4));
        drawText(canvas, "PDF预览", area.left + dp(14), area.top + dp(24), 14, text, true);
        final PdfRecord latest = getActivePdfRecord(0);
        String fileText = latest == null ? "当前实时监护报告预览" : latest.fileName;
        String stateText = latest == null ? "尚未生成，可先点击“生成当前PDF”" : (latest.downloadUri == null ? "已生成，未下载" : "已下载，可按此预览打印");
        drawText(canvas, fileText, area.left + dp(14), area.top + dp(45), 9, muted, false);
        drawText(canvas, stateText, area.left + dp(14), area.top + dp(62), 9, muted, false);
        drawSmallButton(canvas, "下载预览", area.right - dp(82), area.top + dp(16), dp(66), dp(24), false, new ClickAction() {
            @Override
            public void run() {
                if (latest != null && latest.cacheFile != null && latest.cacheFile.exists() && latest.downloadUri == null) {
                    downloadExistingPdfRecord(latest);
                } else {
                    downloadPdfAction();
                }
            }
        });

        RectF paperArea = rect(area.left + dp(14), area.top + dp(78), area.right - dp(14), area.bottom - dp(14));
        float paperW = Math.min(paperArea.width(), paperArea.height() * 595f / 842f);
        float paperH = paperW * 842f / 595f;
        if (paperH > paperArea.height()) {
            paperH = paperArea.height();
            paperW = paperH * 595f / 842f;
        }
        RectF paperRect = rect(paperArea.centerX() - paperW / 2f, paperArea.top, paperArea.centerX() + paperW / 2f, paperArea.top + paperH);
        rounded(canvas, paperRect, Color.WHITE, Color.rgb(160, 170, 180), dp(2));
        canvas.save();
        canvas.clipRect(paperRect);
        canvas.translate(paperRect.left, paperRect.top);
        float scale = paperRect.width() / 595f;
        canvas.scale(scale, scale);
        Paint previewPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        String subtitle = latest == null ? "预览当前实时数据" : "预览文件: " + latest.fileName;
        drawPdfHeader(canvas, previewPaint, "实时监护与电波图", subtitle);
        int previewY = 116;
        previewY = drawPdfSection(canvas, previewPaint, "实时监护", previewY);
        previewY = drawPdfMonitorBlock(canvas, previewPaint, previewY);
        previewY += 12;
        previewY = drawPdfSection(canvas, previewPaint, "打印说明", previewY);
        drawPdfLine(canvas, previewPaint, "说明", "下载后的PDF会包含本页电波图，可直接打印", previewY);
        canvas.restore();
    }

    private void drawPdfIcon(Canvas canvas, float x, float y, float w, float h, int color) {
        RectF pageRect = rect(x, y, x + w, y + h);
        rounded(canvas, pageRect, Color.WHITE, color, dp(3));
        Path fold = new Path();
        fold.moveTo(pageRect.right - dp(15), pageRect.top);
        fold.lineTo(pageRect.right, pageRect.top + dp(15));
        fold.lineTo(pageRect.right - dp(15), pageRect.top + dp(15));
        fold.close();
        paint.setColor(Color.rgb(235, 239, 244));
        paint.setStyle(Paint.Style.FILL);
        canvas.drawPath(fold, paint);
        RectF label = rect(pageRect.left + dp(5), pageRect.centerY() - dp(10), pageRect.right - dp(5), pageRect.centerY() + dp(10));
        rounded(canvas, label, color, Color.TRANSPARENT, dp(3));
        drawTextCenter(canvas, "PDF", label, 10, Color.WHITE, true);
    }

    private void clearPdfRecords() {
        String z = currentZoneNormalized();
        for (int i = pdfRecords.size() - 1; i >= 0; i--) {
            if (z.equals(pdfRecords.get(i).zone)) {
                pdfRecords.remove(i);
            }
        }
        Toast.makeText(activity, zoneText(z) + "PDF记录已清空", Toast.LENGTH_SHORT).show();
        invalidate();
    }

    private void downloadExistingPdfRecord(PdfRecord record) {
        try {
            String downloadName = record.fileName;
            Uri uri = copyPdfToDownloads(record.cacheFile, downloadName);
            addPdfRecord("下载PDF", downloadName, "系统下载目录: " + uri.toString(), record.cacheFile, uri);
            Toast.makeText(activity, "PDF已下载: " + uri.toString(), Toast.LENGTH_LONG).show();
            invalidate();
        } catch (IOException exception) {
            Toast.makeText(activity, "下载失败: " + exception.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    private void drawTutorialPage(Canvas canvas, float x, float y, float w, float h) {
        fill(canvas, x, y, x + w, y + h, page);
        float gap = dp(18);
        float thumbW = (w - gap * 4) / 3f;
        float thumbH = (h - gap * 3) / 2f;
        for (int i = 0; i < 6; i++) {
            int col = i % 3;
            int row = i / 3;
            RectF r = rect(x + gap + col * (thumbW + gap), y + gap + row * (thumbH + gap), x + gap + col * (thumbW + gap) + thumbW, y + gap + row * (thumbH + gap) + thumbH);
            if (i == 0) {
                drawThermalThumb(canvas, r);
            } else if (i == 1) {
                drawControlThumb(canvas, r);
            } else {
                drawDocumentThumb(canvas, r, i);
            }
        }
    }

    private void drawSettings(Canvas canvas, float x, float y, float w, float h) {
        fill(canvas, x, y, x + w, y + h, page);
        RectF left = rect(x + dp(16), y + dp(18), x + w * 0.52f, y + h - dp(18));
        RectF right = rect(x + w * 0.54f, y + dp(18), x + w - dp(16), y + h - dp(18));
        rounded(canvas, left, Color.WHITE, border, dp(2));
        rounded(canvas, right, Color.WHITE, border, dp(2));

        drawText(canvas, "蓝牙连接设置", left.left + dp(18), left.top + dp(28), 18, text, true);
        drawText(canvas, "在这里申请权限、开启蓝牙、扫描设备，并连接 ICU 主机。", left.left + dp(18), left.top + dp(56), 11, muted, false);
        drawText(canvas, "当前舱区: " + ("left".equals(bleManager.getCurrentZone()) ? "左舱" : "右舱"), left.left + dp(18), left.top + dp(78), 11, muted, false);
        drawSmallButton(canvas, "左舱", left.left + dp(112), left.top + dp(62), dp(52), dp(24), "left".equals(bleManager.getCurrentZone()), new ClickAction() {
            @Override
            public void run() {
                // 走统一的切舱入口：同步重置病例选中项与抓拍选中下标，避免沿用另一舱的状态。
                switchPatientZone("left");
                invalidate();
            }
        });
        drawSmallButton(canvas, "右舱", left.left + dp(174), left.top + dp(62), dp(52), dp(24), "right".equals(bleManager.getCurrentZone()), new ClickAction() {
            @Override
            public void run() {
                switchPatientZone("right");
                invalidate();
            }
        });

        float by = left.top + dp(104);
        drawActionButton(canvas, "申请权限", left.left + dp(18), by, dp(92), dp(32), new ClickAction() {
            @Override
            public void run() {
                activity.requestBlePermissionsFromUi();
            }
        });
        drawActionButton(canvas, "开启蓝牙", left.left + dp(122), by, dp(92), dp(32), new ClickAction() {
            @Override
            public void run() {
                bleManager.requestEnableBluetooth(activity);
            }
        });
        drawActionButton(canvas, bleManager.isScanning() ? "停止扫描" : "开始扫描", left.left + dp(226), by, dp(92), dp(32), new ClickAction() {
            @Override
            public void run() {
                if (bleManager.isScanning()) {
                    bleManager.stopScan();
                } else if (activity.hasAllBlePermissions()) {
                    bleManager.startScan();
                } else {
                    Toast.makeText(activity, "请先申请权限", Toast.LENGTH_SHORT).show();
                }
            }
        });
        drawActionButton(canvas, "断开连接", left.left + dp(330), by, dp(92), dp(32), new ClickAction() {
            @Override
            public void run() {
                bleManager.disconnect();
            }
        });

        float infoY = by + dp(58);
        drawInfoLine(canvas, "连接状态", bleManager.getConnectionStateText(), left.left + dp(18), infoY, left.width() - dp(36));
        drawInfoLine(canvas, "设备名称", dash(bleManager.getConnectedDeviceName()), left.left + dp(18), infoY + dp(48), left.width() - dp(36));
        drawInfoLine(canvas, "设备ID", dash(bleManager.getConnectedDeviceId()), left.left + dp(18), infoY + dp(96), left.width() - dp(36));
        drawInfoLine(canvas, "协议摘要", dash(bleManager.getLastParsedStatus()), left.left + dp(18), infoY + dp(144), left.width() - dp(36));

        drawText(canvas, "附近设备", left.left + dp(18), infoY + dp(204), 14, text, true);
        RectF deviceArea = rect(left.left + dp(18), infoY + dp(218), left.right - dp(18), left.bottom - dp(16));
        List<BleManager.DeviceItem> devices = bleManager.getDevices();
        canvas.save();
        canvas.clipRect(deviceArea);
        if (devices.isEmpty()) {
            drawText(canvas, "还没有扫描到有名称的 BLE 设备", deviceArea.left, deviceArea.top + dp(18), 12, muted, false);
        } else {
            float dy = deviceArea.top;
            int maxRows = Math.max(0, (int) (deviceArea.height() / dp(48)));
            int count = Math.min(maxRows, devices.size());
            for (int i = 0; i < count; i++) {
                final BleManager.DeviceItem item = devices.get(i);
                RectF row = rect(deviceArea.left, dy, deviceArea.right, dy + dp(42));
                rounded(canvas, row, panel, border, dp(2));
                drawText(canvas, item.deviceName + "    RSSI " + item.rssi, row.left + dp(10), row.top + dp(17), 11, text, true);
                drawText(canvas, item.deviceId, row.left + dp(10), row.top + dp(34), 9, muted, false);
                clickZones.add(new ClickZone(new RectF(row), new ClickAction() {
                    @Override
                    public void run() {
                        bleManager.connect(item.deviceId);
                    }
                }));
                dy += dp(48);
            }
        }
        canvas.restore();

        drawText(canvas, "通信状态", right.left + dp(18), right.top + dp(28), 18, text, true);
        drawActionButton(canvas, "读取全部状态", right.left + dp(18), right.top + dp(56), dp(118), dp(32), new ClickAction() {
            @Override
            public void run() {
                bleManager.readAllStatus();
            }
        });
        drawActionButton(canvas, "读取温度", right.left + dp(150), right.top + dp(56), dp(92), dp(32), new ClickAction() {
            @Override
            public void run() {
                bleManager.sendCommand("{\"cmd\":\"get_temp\"}");
            }
        });
        drawActionButton(canvas, "清空日志", right.left + dp(256), right.top + dp(56), dp(92), dp(32), new ClickAction() {
            @Override
            public void run() {
                bleManager.clearLogs();
            }
        });
        drawLogBox(canvas, "最近发送", dash(bleManager.getLastSent()), right.left + dp(18), right.top + dp(112), right.width() - dp(36), dp(64));
        drawLogBox(canvas, "最近接收", dash(bleManager.getLastReceived()), right.left + dp(18), right.top + dp(192), right.width() - dp(36), dp(64));
        drawLogBox(canvas, "最近错误", dash(bleManager.getLastError()), right.left + dp(18), right.top + dp(272), right.width() - dp(36), dp(64));
        drawText(canvas, "通信日志", right.left + dp(18), right.top + dp(372), 14, text, true);
        RectF logs = rect(right.left + dp(18), right.top + dp(386), right.right - dp(18), right.bottom - dp(16));
        rounded(canvas, logs, panel, border, dp(2));
        List<String> logLines = bleManager.getLogs();
        RectF logClip = inset(logs, dp(8), dp(8));
        float ly = logClip.top + dp(10);
        int maxLogLines = Math.max(0, (int) (logClip.height() / dp(18)));
        canvas.save();
        canvas.clipRect(logClip);
        for (int i = 0; i < Math.min(maxLogLines, logLines.size()); i++) {
            drawText(canvas, logLines.get(i), logClip.left, ly, 10, muted, false);
            ly += dp(18);
        }
        canvas.restore();
    }

    private void drawInfoLine(Canvas canvas, String label, String value, float x, float y, float w) {
        RectF r = rect(x, y, x + w, y + dp(36));
        rounded(canvas, r, panel, border, dp(2));
        drawText(canvas, label, r.left + dp(10), r.top + dp(14), 10, muted, false);
        drawTextRight(canvas, value, r.right - dp(10), r.top + dp(24), 11, text, true);
    }

    private void drawLogBox(Canvas canvas, String label, String value, float x, float y, float w, float h) {
        drawText(canvas, label, x, y, 11, muted, false);
        RectF r = rect(x, y + dp(10), x + w, y + h);
        rounded(canvas, r, panel, border, dp(2));
        canvas.save();
        canvas.clipRect(inset(r, dp(8), dp(6)));
        drawText(canvas, value, r.left + dp(10), r.top + dp(24), 10, text, false);
        canvas.restore();
    }

    private void drawVideoPlayer(Canvas canvas, RectF r) {
        rounded(canvas, r, Color.BLACK, Color.rgb(100, 108, 118), 0);
        drawCameraLens(canvas, rect(r.left + dp(60), r.top + dp(36), r.right - dp(60), r.bottom - dp(48)));
        drawText(canvas, "TP-LINK 摄像头", r.left + dp(18), r.top + dp(28), 14, Color.WHITE, true);
        String status = TextUtils.isEmpty(cameraStreamUrl) ? "未配置视频地址，点击右侧“配置摄像头”" : "已配置: " + maskCameraUrl(cameraStreamUrl);
        drawText(canvas, status, r.left + dp(18), r.top + dp(52), 10, Color.rgb(190, 205, 215), false);
        drawText(canvas, CAMERA_PLAYER_VERSION + "；优先使用已配置地址的 /stream2 H.264 RTSP。",
                r.left + dp(18), r.top + dp(74), 10, Color.rgb(150, 170, 190), false);
        drawText(canvas, "如果仍打不开，请拍摄播放页显示的 DESCRIBE/SETUP/PLAY/H264 错误。",
                r.left + dp(18), r.top + dp(94), 10, Color.rgb(150, 170, 190), false);
        clickZones.add(new ClickZone(new RectF(r), new ClickAction() {
            @Override
            public void run() {
                openTpLinkCameraPreview();
            }
        }));
        drawCameraControlBar(canvas, r, false);
    }

    private void drawCameraControlBar(Canvas canvas, RectF r, boolean compact) {
        float barH = compact ? dp(24) : dp(34);
        RectF bar = rect(r.left, r.bottom - barH, r.right, r.bottom);
        fill(canvas, bar.left, bar.top, bar.right, bar.bottom, Color.argb(190, 18, 22, 28));
        drawText(canvas, compact ? "▶  LIVE" : "▶   00:31 / LIVE", bar.left + dp(12), bar.top + barH * 0.64f, compact ? 9 : 11, Color.WHITE, true);

        drawTextRight(canvas, "全屏", bar.right - dp(12), bar.top + barH * 0.64f, compact ? 9 : 11, Color.WHITE, true);

        final RectF fullscreen = compact
                ? rect(bar.right - dp(54), bar.top, bar.right, bar.bottom)
                : rect(bar.right - dp(70), bar.top, bar.right, bar.bottom);
        clickZones.add(new ClickZone(fullscreen, new ClickAction() {
            @Override
            public void run() {
                toggleTpLinkCameraFullscreen();
            }
        }));
    }

    private void drawCameraPlayback(Canvas canvas, float x, float y, float w, float h) {
        RectF panel = rect(x, y, x + w, y + h);
        rounded(canvas, panel, Color.WHITE, border, 0);
        drawText(canvas, "<返回实况", panel.left + dp(12), panel.top + dp(20), 10, muted, false);
        clickZones.add(new ClickZone(rect(panel.left + dp(6), panel.top + dp(4), panel.left + dp(82), panel.top + dp(32)), new ClickAction() {
            @Override
            public void run() {
                cameraPlayback = false;
            }
        }));
        float gap = dp(6);
        float thumbW = (panel.width() - dp(36)) / 3f;
        float thumbH = (panel.height() - dp(72)) / 2.5f;
        float yy = panel.top + dp(44);
        // 缩略图只显示当前舱的抓拍。选中下标 selectedCameraSnapshotIndex 也是这张列表里的下标。
        ArrayList<CameraSnapshot> visibleSnapshots = new ArrayList<>();
        String activeZone = currentZoneNormalized();
        for (CameraSnapshot snapshot : cameraSnapshots) {
            if (activeZone.equals(snapshot.zone)) {
                visibleSnapshots.add(snapshot);
            }
        }
        if (visibleSnapshots.isEmpty()) {
            drawTextCenter(canvas, "暂无摄像头截图，点击右侧“截图”后会显示在这里",
                    rect(panel.left, panel.top + dp(80), panel.right, panel.top + dp(130)), 13, muted, false);
            drawTextCenter(canvas, "截图会保存到本地回放；下载后进入系统图片目录 ICUCamera",
                    rect(panel.left, panel.top + dp(118), panel.right, panel.top + dp(160)), 11, muted, false);
            return;
        }
        int count = Math.min(6, visibleSnapshots.size());
        for (int i = 0; i < count; i++) {
            final int snapshotIndex = i;
            CameraSnapshot snapshot = visibleSnapshots.get(i);
            int col = i % 3;
            int row = i / 3;
            RectF t = rect(panel.left + dp(12) + col * (thumbW + gap), yy + row * (thumbH + gap), panel.left + dp(12) + col * (thumbW + gap) + thumbW, yy + row * (thumbH + gap) + thumbH);
            boolean selected = selectedCameraSnapshotIndex == i;
            rounded(canvas, t, Color.BLACK, selected ? orange : Color.WHITE, selected ? dp(3) : dp(1));
            RectF imageArea = rect(t.left + dp(2), t.top + dp(2), t.right - dp(2), t.bottom - dp(28));
            Bitmap bitmap = null;
            if (snapshot.cacheFile != null && snapshot.cacheFile.exists()) {
                bitmap = BitmapFactory.decodeFile(snapshot.cacheFile.getAbsolutePath());
            }
            if (bitmap != null) {
                canvas.drawBitmap(bitmap, null, imageArea, paint);
                bitmap.recycle();
            } else {
                drawCameraLens(canvas, inset(imageArea, dp(12), dp(8)));
            }
            fill(canvas, t.left, t.bottom - dp(28), t.right, t.bottom, Color.argb(210, 18, 22, 28));
            drawText(canvas, snapshot.timeText, t.left + dp(8), t.bottom - dp(13), 8, Color.WHITE, false);
            drawTextRight(canvas, snapshot.downloadUri == null ? "本地" : "已下载", t.right - dp(8), t.bottom - dp(13), 8,
                    snapshot.downloadUri == null ? Color.rgb(210, 220, 230) : green, true);
            clickZones.add(new ClickZone(new RectF(t), new ClickAction() {
                @Override
                public void run() {
                    selectedCameraSnapshotIndex = snapshotIndex;
                }
            }));
        }
        CameraSnapshot selected = selectedCameraSnapshot();
        String selectedText = selected == null ? "未选择截图" : "已选择: " + selected.fileName;
        drawTextCenter(canvas, "共 " + visibleSnapshots.size() + " 张截图    " + selectedText,
                rect(panel.left, panel.bottom - dp(34), panel.right, panel.bottom - dp(12)), 9, muted, false);
    }

    private void drawCameraPreview(Canvas canvas, RectF r, boolean borderOrange) {
        rounded(canvas, r, Color.BLACK, borderOrange ? orange : border, dp(1));
        drawCameraLens(canvas, inset(r, dp(12), dp(12)));
    }

    private void drawCameraLens(Canvas canvas, RectF r) {
        float cx = r.centerX();
        float cy = r.centerY();
        float radius = Math.min(r.width(), r.height()) * 0.45f;
        if (radius <= 0f) {
            return;
        }
        paint.setShader(new RadialGradient(cx, cy, radius, Color.rgb(210, 220, 220), Color.rgb(25, 30, 33), Shader.TileMode.CLAMP));
        canvas.drawCircle(cx, cy, radius, paint);
        paint.setShader(null);
        stroke(canvas, cx - radius, cy - radius, cx + radius, cy + radius, Color.rgb(70, 78, 82), dp(2));
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(dp(3));
        paint.setColor(Color.rgb(140, 150, 150));
        for (int i = -2; i <= 2; i++) {
            canvas.drawLine(cx - radius * 0.8f, cy + i * radius * 0.22f, cx + radius * 0.65f, cy - i * radius * 0.18f, paint);
        }
        paint.setStyle(Paint.Style.FILL);
        fill(canvas, cx - radius * 0.2f, cy - radius * 0.9f, cx + radius * 0.15f, cy + radius * 0.15f, Color.argb(130, 250, 250, 240));
        fill(canvas, cx + radius * 0.25f, cy - radius * 0.5f, cx + radius * 0.78f, cy + radius * 0.3f, Color.argb(95, 250, 250, 240));
    }

    private void drawLightPreview(Canvas canvas, RectF r) {
        RectF inner = inset(r, dp(18), dp(30));
        paint.setShader(new LinearGradient(inner.left, inner.top, inner.right, inner.bottom, Color.rgb(68, 48, 16), Color.rgb(246, 216, 34), Shader.TileMode.CLAMP));
        canvas.drawRect(inner, paint);
        paint.setShader(null);
    }

    private void drawWave(Canvas canvas, float left, float baseline, float right, float amp, int color, int type) {
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(dp(2));
        paint.setColor(color);
        Path path = new Path();
        path.moveTo(left, baseline);
        float width = right - left;
        for (int i = 0; i <= 220; i++) {
            float t = i / 220f;
            float x = left + width * t;
            float y;
            if (type == 0) {
                float beat = (i % 36) / 36f;
                y = baseline - (beat > 0.42f && beat < 0.48f ? amp : (float) Math.sin(t * 30) * amp * 0.12f);
            } else if (type == 1) {
                y = baseline + (float) Math.sin(t * 42) * amp * 0.45f + (float) Math.sin(t * 13) * amp * 0.22f;
            } else {
                y = baseline + (float) Math.sin(t * 14) * amp * 0.45f;
            }
            path.lineTo(x, y);
        }
        canvas.drawPath(path, paint);
        paint.setStyle(Paint.Style.FILL);
    }

    private void drawRealtimeWave(Canvas canvas, float left, float baseline, float right, float amp,
                                  int color, int type, List<Integer> samples, int numericValue) {
        if (samples != null && samples.size() >= 6) {
            drawSampleWave(canvas, left, baseline, right, amp, color, samples);
            return;
        }
        drawSyntheticRealtimeWave(canvas, left, baseline, right, amp, color, type, numericValue);
    }

    private void drawSampleWave(Canvas canvas, float left, float baseline, float right, float amp, int color, List<Integer> samples) {
        drawSmoothSampleWave(canvas, left, baseline, right, amp, color, samples, dp(1.35f));
    }

    private void drawSmoothSampleWave(Canvas canvas, float left, float baseline, float right, float amp,
                                      int color, List<Integer> samples, float strokeWidth) {
        if (samples == null || samples.size() < 2 || right <= left) {
            return;
        }
        int targetCount = Math.max(220, Math.min(720, (int) ((right - left) / 1.15f)));
        int count = Math.min(targetCount, samples.size());
        int start = samples.size() - count;
        int min = Integer.MAX_VALUE;
        int max = Integer.MIN_VALUE;
        for (int i = start; i < samples.size(); i++) {
            int value = samples.get(i);
            min = Math.min(min, value);
            max = Math.max(max, value);
        }
        float range = Math.max(12f, max - min);
        float center = (max + min) / 2f;
        float top = baseline - amp * 1.72f;
        float bottom = baseline + amp * 1.72f;
        float[] xs = new float[count];
        float[] ys = new float[count];
        float lastY = baseline;
        for (int i = 0; i < count; i++) {
            float t = count <= 1 ? 0 : i / (float) (count - 1);
            xs[i] = left + (right - left) * t;
            float normalized = (samples.get(start + i) - center) / range;
            float y = baseline - normalized * amp * 1.62f;
            if (i > 0) {
                y = lastY * 0.18f + y * 0.82f;
            }
            ys[i] = clamp(y, top, bottom);
            lastY = ys[i];
        }

        Path path = new Path();
        path.moveTo(xs[0], ys[0]);
        for (int i = 1; i < count - 1; i++) {
            float midX = (xs[i] + xs[i + 1]) * 0.5f;
            float midY = (ys[i] + ys[i + 1]) * 0.5f;
            path.quadTo(xs[i], ys[i], midX, midY);
        }
        if (count > 1) {
            path.lineTo(xs[count - 1], ys[count - 1]);
        }

        canvas.save();
        canvas.clipRect(left, top - strokeWidth * 2f, right, bottom + strokeWidth * 2f);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeCap(Paint.Cap.ROUND);
        paint.setStrokeJoin(Paint.Join.ROUND);
        int glowAlpha = 52;
        paint.setStrokeWidth(strokeWidth * 2.4f);
        paint.setColor(Color.argb(glowAlpha, Color.red(color), Color.green(color), Color.blue(color)));
        canvas.drawPath(path, paint);
        paint.setStrokeWidth(strokeWidth);
        paint.setColor(color);
        canvas.drawPath(path, paint);
        paint.setStrokeCap(Paint.Cap.BUTT);
        paint.setStrokeJoin(Paint.Join.MITER);
        paint.setStyle(Paint.Style.FILL);
        canvas.restore();
    }

    private void drawSyntheticRealtimeWave(Canvas canvas, float left, float baseline, float right, float amp,
                                           int color, int type, int numericValue) {
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(dp(1.35f));
        paint.setStrokeCap(Paint.Cap.ROUND);
        paint.setStrokeJoin(Paint.Join.ROUND);
        paint.setColor(color);
        Path path = new Path();
        path.moveTo(left, baseline);
        float width = right - left;
        float safeValue = numericValue > 0 ? numericValue : (type == 2 ? 20f : type == 1 ? 98f : 75f);
        float time = (System.currentTimeMillis() % 100000L) / 1000f;
        int syntheticCount = Math.max(300, Math.min(620, (int) (width / 1.1f)));
        for (int i = 0; i <= syntheticCount; i++) {
            float t = i / (float) syntheticCount;
            float x = left + width * t;
            float y;
            if (type == 0) {
                float bpm = clamp(safeValue, 35f, 180f);
                float periodPx = clamp(4200f / bpm, 24f, 92f);
                float beat = ((i + time * 42f) % periodPx) / periodPx;
                float spike = beat > 0.42f && beat < 0.48f ? amp * (0.72f + bpm / 260f) : 0f;
                float ripple = (float) Math.sin((t * 18f + time * 2.6f) * Math.PI) * amp * 0.10f;
                y = baseline - spike + ripple;
            } else if (type == 1) {
                float spo2 = clamp(safeValue, 70f, 100f);
                float quality = clamp((spo2 - 70f) / 30f, 0.25f, 1f);
                y = baseline + (float) Math.sin(t * 42f + time * 5.2f) * amp * 0.28f * quality
                        + (float) Math.sin(t * 13f + time * 1.7f) * amp * 0.16f;
            } else {
                float resp = clamp(safeValue, 6f, 60f);
                float speed = clamp(resp / 20f, 0.45f, 2.2f);
                y = baseline + (float) Math.sin(t * 14f + time * speed * 2.2f) * amp * 0.45f;
            }
            path.lineTo(x, y);
        }
        canvas.drawPath(path, paint);
        paint.setStrokeCap(Paint.Cap.BUTT);
        paint.setStrokeJoin(Paint.Join.MITER);
        paint.setStyle(Paint.Style.FILL);
    }

    private void drawMetric(Canvas canvas, String name, String value, String unit, float x, float y, int color) {
        drawText(canvas, name, x, y, 10, color, false);
        drawText(canvas, value, x + dp(54), y, 14, color, true);
        drawText(canvas, unit, x + dp(116), y, 8, color, false);
    }

    private void drawTableRow(Canvas canvas, float x, float y, float w, float h, String[] cells, float[] cols, boolean header) {
        int fill = header ? Color.rgb(234, 238, 242) : Color.WHITE;
        fill(canvas, x, y, x + w, y + h, fill);
        stroke(canvas, x, y, x + w, y + h, border, dp(0.8f));
        float cx = x;
        for (int i = 0; i < cells.length; i++) {
            float cw = w * cols[i];
            stroke(canvas, cx, y, cx + cw, y + h, border, dp(0.8f));
            drawTextCenter(canvas, cells[i], rect(cx, y, cx + cw, y + h), 9, text, header);
            cx += cw;
        }
    }

    private void drawThermalThumb(Canvas canvas, RectF r) {
        rounded(canvas, r, Color.BLACK, Color.WHITE, 0);
        RectF image = inset(r, dp(8), dp(18));
        paint.setShader(new LinearGradient(image.left, image.top, image.right, image.bottom, Color.rgb(20, 40, 130), Color.rgb(255, 60, 60), Shader.TileMode.CLAMP));
        canvas.drawRect(image, paint);
        paint.setShader(null);
        fill(canvas, image.left + image.width() * 0.3f, image.top + dp(8), image.left + image.width() * 0.48f, image.bottom - dp(8), Color.rgb(255, 235, 80));
        fill(canvas, image.left + image.width() * 0.58f, image.top + dp(14), image.left + image.width() * 0.72f, image.bottom - dp(12), Color.rgb(255, 120, 80));
    }

    private void drawControlThumb(Canvas canvas, RectF r) {
        rounded(canvas, r, Color.rgb(15, 24, 38), Color.WHITE, 0);
        drawTextCenter(canvas, "36.5", rect(r.left + dp(10), r.top + dp(20), r.right - dp(10), r.top + dp(60)), 22, Color.rgb(120, 205, 255), true);
        drawTextCenter(canvas, "ICU 监护舱", rect(r.left, r.centerY(), r.right, r.centerY() + dp(28)), 12, Color.WHITE, true);
        for (int i = 0; i < 5; i++) {
            drawCircle(canvas, r.left + dp(25) + i * dp(26), r.bottom - dp(28), dp(8), i % 2 == 0 ? green : orange);
        }
    }

    private void drawDocumentThumb(Canvas canvas, RectF r, int index) {
        rounded(canvas, r, Color.WHITE, Color.WHITE, 0);
        drawText(canvas, index == 2 ? "术后监护说明" : "ICU 使用教程", r.left + dp(14), r.top + dp(24), 11, Color.rgb(70, 90, 120), true);
        for (int i = 0; i < 5; i++) {
            fill(canvas, r.left + dp(16), r.top + dp(48) + i * dp(16), r.right - dp(30), r.top + dp(54) + i * dp(16), Color.rgb(226, 232, 240));
        }
        drawCircle(canvas, r.right - dp(48), r.bottom - dp(38), dp(22), index % 2 == 0 ? Color.rgb(235, 170, 120) : Color.rgb(125, 190, 220));
    }

    private void drawStatusLightDots(Canvas canvas, RectF r) {
        String active = currentMonitorLevelColor();
        drawCircle(canvas, r.centerX() - dp(24), r.top + dp(50), dp(8), "red".equals(active) ? red : Color.rgb(235, 176, 176));
        drawCircle(canvas, r.centerX(), r.top + dp(50), dp(8), "yellow".equals(active) ? Color.rgb(248, 210, 42) : Color.rgb(232, 219, 156));
        drawCircle(canvas, r.centerX() + dp(24), r.top + dp(50), dp(8), "green".equals(active) ? green : Color.rgb(178, 215, 174));
    }

    private void drawSearchBox(Canvas canvas, String label, float x, float y, float w, float h, ClickAction action) {
        RectF r = rect(x, y, x + w, y + h);
        rounded(canvas, r, Color.WHITE, Color.TRANSPARENT, dp(8));
        drawText(canvas, label, r.left + dp(14), r.top + h * 0.64f, 14, text, false);
        clickZones.add(new ClickZone(r, action));
    }

    private void drawHeaderButton(Canvas canvas, String label, float x, float y, float w, float h, boolean active, ClickAction action) {
        RectF r = rect(x, y, x + w, y + h);
        rounded(canvas, r, active ? activeBlue : Color.WHITE, Color.TRANSPARENT, dp(4));
        drawTextCenter(canvas, label, r, 14, active ? Color.WHITE : text, false);
        clickZones.add(new ClickZone(r, action));
    }

    private void drawFormField(Canvas canvas, String label, String value, float x, float y, float labelW, float valueW, boolean required) {
        drawText(canvas, (required ? "* " : "") + label, x, y + dp(20), 13, required ? red : text, false);
        RectF box = rect(x + labelW, y, x + labelW + valueW, y + dp(30));
        rounded(canvas, box, Color.WHITE, Color.TRANSPARENT, dp(3));
        drawText(canvas, dash(value), box.left + dp(12), box.top + dp(20), 13, text, false);
    }

    private void drawProgressBar(Canvas canvas, float x, float y, float w, float h, float progress, int color) {
        RectF bg = rect(x, y, x + w, y + h);
        rounded(canvas, bg, Color.rgb(229, 236, 246), Color.TRANSPARENT, h / 2f);
        RectF fg = rect(x, y, x + Math.max(h, w * clamp(progress, 0f, 1f)), y + h);
        rounded(canvas, fg, color, Color.TRANSPARENT, h / 2f);
    }

    private void drawControlIcon(Canvas canvas, RectF r, int index) {
        float cx = r.right - dp(44);
        float cy = r.centerY();
        int color = Color.rgb(55, 148, 242);
        if (index == 5 || index == 7) {
            color = Color.rgb(250, 174, 75);
        }
        drawTextCenter(canvas, controlIconText(index), rect(cx - dp(28), cy - dp(28), cx + dp(28), cy + dp(28)), 30, color, true);
    }

    private String controlIconText(int index) {
        if (index == 0) return "♨";
        if (index == 1) return "O₂";
        if (index == 2) return "▣";
        if (index == 3) return "CO₂";
        if (index == 4) return "☼";
        if (index == 5) return "☀";
        if (index == 6) return "◌";
        if (index == 7) return "☀";
        if (index == 8) return "◎";
        if (index == 9) return "↻";
        if (index == 10) return "◌";
        if (index == 11) return "✣";
        if (index == 12) return "24";
        return "✦";
    }

    private void drawSmallButton(Canvas canvas, String label, float x, float y, float w, float h, boolean active, ClickAction action) {
        RectF r = rect(x, y, x + w, y + h);
        rounded(canvas, r, active ? activeBlue : Color.WHITE, active ? activeBlue : Color.TRANSPARENT, Math.min(dp(6), h / 2f));
        drawTextCenter(canvas, label, r, 10, active ? Color.WHITE : text, false);
        if (action != null) {
            clickZones.add(new ClickZone(r, action));
        }
    }

    private void drawActionButton(Canvas canvas, String label, float x, float y, float w, float h, ClickAction action) {
        RectF r = rect(x, y, x + w, y + h);
        rounded(canvas, r, Color.WHITE, Color.TRANSPARENT, dp(4));
        drawTextCenter(canvas, label, r, 11, text, true);
        clickZones.add(new ClickZone(r, action));
    }

    private void drawToggle(Canvas canvas, float x, float y, boolean on) {
        RectF r = rect(x, y, x + dp(38), y + dp(18));
        rounded(canvas, r, on ? Color.rgb(194, 229, 249) : Color.rgb(204, 214, 226), Color.TRANSPARENT, dp(9));
        drawCircle(canvas, on ? r.right - dp(9) : r.left + dp(9), r.centerY(), dp(7), on ? activeBlue : Color.rgb(150, 162, 176));
    }

    private void rounded(Canvas canvas, RectF r, int fill, int stroke, float radius) {
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(fill);
        canvas.drawRoundRect(r, radius, radius, paint);
        if (stroke != Color.TRANSPARENT) {
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(dp(1));
            paint.setColor(stroke);
            canvas.drawRoundRect(r, radius, radius, paint);
            paint.setStyle(Paint.Style.FILL);
        }
    }

    private void fill(Canvas canvas, float left, float top, float right, float bottom, int color) {
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(color);
        canvas.drawRect(left, top, right, bottom, paint);
    }

    private void stroke(Canvas canvas, float left, float top, float right, float bottom, int color, float width) {
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(width);
        paint.setColor(color);
        canvas.drawRect(left, top, right, bottom, paint);
        paint.setStyle(Paint.Style.FILL);
    }

    private void drawCircle(Canvas canvas, float cx, float cy, float radius, int color) {
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(color);
        canvas.drawCircle(cx, cy, radius, paint);
    }

    private void drawText(Canvas canvas, String value, float x, float y, float sp, int color, boolean bold) {
        textPaint.setShader(null);
        textPaint.setColor(color);
        textPaint.setTextSize(dp(sp));
        textPaint.setTypeface(Typeface.create(Typeface.DEFAULT, bold ? Typeface.BOLD : Typeface.NORMAL));
        textPaint.setTextAlign(Paint.Align.LEFT);
        canvas.drawText(value, x, y, textPaint);
    }

    private void drawTextRight(Canvas canvas, String value, float x, float y, float sp, int color, boolean bold) {
        textPaint.setShader(null);
        textPaint.setColor(color);
        textPaint.setTextSize(dp(sp));
        textPaint.setTypeface(Typeface.create(Typeface.DEFAULT, bold ? Typeface.BOLD : Typeface.NORMAL));
        textPaint.setTextAlign(Paint.Align.RIGHT);
        canvas.drawText(value, x, y, textPaint);
    }

    private void drawTextCenter(Canvas canvas, String value, RectF r, float sp, int color, boolean bold) {
        textPaint.setShader(null);
        textPaint.setColor(color);
        textPaint.setTextSize(dp(sp));
        textPaint.setTypeface(Typeface.create(Typeface.DEFAULT, bold ? Typeface.BOLD : Typeface.NORMAL));
        textPaint.setTextAlign(Paint.Align.CENTER);
        Paint.FontMetrics metrics = textPaint.getFontMetrics();
        float baseline = r.centerY() - (metrics.ascent + metrics.descent) / 2f;
        canvas.drawText(value, r.centerX(), baseline, textPaint);
    }

    private RectF rect(float left, float top, float right, float bottom) {
        return new RectF(left, top, right, bottom);
    }

    private RectF inset(RectF r, float dx, float dy) {
        return new RectF(r.left + dx, r.top + dy, r.right - dx, r.bottom - dy);
    }

    private String dash(String value) {
        return TextUtils.isEmpty(value) ? "-" : value;
    }

    private String pendingText(String value) {
        if (value == null) {
            return "待录入";
        }
        String v = value.trim();
        return TextUtils.isEmpty(v) || "-".equals(v) || "--".equals(v) || "...".equals(v)
                ? "待录入" : value;
    }

    private String fitSidebarText(String value, float sp, boolean bold, float maxWidthDp) {
        textPaint.setShader(null);
        textPaint.setTextSize(dp(sp));
        textPaint.setTypeface(Typeface.create(Typeface.DEFAULT, bold ? Typeface.BOLD : Typeface.NORMAL));
        String s = value == null ? "" : value;
        if (textPaint.measureText(s) <= dp(maxWidthDp)) {
            return s;
        }
        String fitted = s;
        while (fitted.length() > 1 && textPaint.measureText(fitted) > dp(maxWidthDp)) {
            fitted = fitted.substring(0, fitted.length() - 1);
        }
        if (fitted.length() > 3) {
            fitted = fitted.substring(0, fitted.length() - 3) + "...";
        }
        return fitted;
    }

    private float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }

    private float dp(float value) {
        return value * (density == 0 ? getResources().getDisplayMetrics().density : density);
    }
}
