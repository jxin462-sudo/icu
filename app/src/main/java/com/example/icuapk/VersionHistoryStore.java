package com.example.icuapk;

import android.content.Context;
import android.text.TextUtils;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * ★ 2026-10-15 V50：版本更新记录存储（关于页-版本信息）。
 * 落盘 filesDir/version_history.json：
 * { "last":  {"app":"V50-20261015","main":"1.2","ctrl":"2.0","bt":"3.1"},
 *   "items": [ {"time":"yyyy-MM-dd HH:mm:ss","module":"APP|主控|监护|蓝牙",
 *               "from":"旧版本","to":"新版本","content":"更新内容(仅APP有)"}, ... ] }
 * items 最新在前，封顶 200 条。
 * 记录规则：APP 首次运行含本功能的版本也会记一条（从 V50 起有内容可看）；
 * 设备版本（主控/监护/蓝牙）首次读取只落基线不产生记录，值变化才记"更新"——
 * 避免把"首次读到版本"误报成"固件更新"（数据准确）。
 */
public class VersionHistoryStore {
    private static final String FILE_NAME = "version_history.json";
    private static final int MAX_ITEMS = 200;

    private final File file;
    private JSONObject root;

    public VersionHistoryStore(Context context) {
        file = new File(context.getFilesDir(), FILE_NAME);
        load();
    }

    private synchronized void load() {
        root = new JSONObject();
        if (!file.exists()) {
            return;
        }
        StringBuilder sb = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(new FileInputStream(file), "UTF-8"))) {
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line);
            }
            root = new JSONObject(sb.toString());
        } catch (Exception ignored) {
            root = new JSONObject();
        }
    }

    private synchronized void save() {
        try (OutputStreamWriter w = new OutputStreamWriter(new FileOutputStream(file), "UTF-8")) {
            w.write(root.toString());
        } catch (Exception ignored) {
        }
    }

    private static String now() {
        return new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(new Date());
    }

    /**
     * 记录某模块版本。与已存值相同直接返回；空版本忽略。
     * content 仅 APP 更新时从 assets changelog.json 带过来。
     */
    public synchronized void noteVersion(String key, String module, String version, String content) {
        if (TextUtils.isEmpty(version)) {
            return;
        }
        try {
            JSONObject last = root.optJSONObject("last");
            if (last == null) {
                last = new JSONObject();
                root.put("last", last);
            }
            String prev = last.optString(key, "");
            if (version.equals(prev)) {
                return;
            }
            last.put(key, version);
            boolean record = !TextUtils.isEmpty(prev) || "app".equals(key);
            if (record) {
                JSONObject item = new JSONObject();
                item.put("time", now());
                item.put("module", module);
                item.put("from", prev);
                item.put("to", version);
                item.put("content", content == null ? "" : content);
                JSONArray items = root.optJSONArray("items");
                if (items == null) {
                    items = new JSONArray();
                    root.put("items", items);
                }
                JSONArray next = new JSONArray();
                next.put(item);
                for (int i = 0; i < items.length() && next.length() < MAX_ITEMS; i++) {
                    next.put(items.getJSONObject(i));
                }
                root.put("items", next);
            }
            save();
        } catch (JSONException ignored) {
        }
    }

    /** 已落盘的模块当前版本（无则空串）。 */
    public synchronized String lastVersion(String key) {
        JSONObject last = root.optJSONObject("last");
        return last == null ? "" : last.optString(key, "");
    }

    /** 全量记录（最新在前），随状态每秒下发；H5 负责时间筛选/分页（与日志页同模式）。 */
    public synchronized JSONArray itemsJson() {
        JSONArray items = root.optJSONArray("items");
        return items == null ? new JSONArray() : items;
    }
}
