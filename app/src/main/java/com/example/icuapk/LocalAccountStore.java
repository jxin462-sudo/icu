package com.example.icuapk;

import android.content.Context;
import android.text.TextUtils;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 本地账号存储。文件:filesDir/accounts.json
 * 格式:
 * {
 *   "accounts": [ {"name":"x","password":"y","organization":"z","role":"service"}, ... ],
 *   "currentAccount": "x"
 * }
 *
 * ★ 任务13（V1.02 R67-R73/R93）：增加角色(role)与增删改查。
 *   role: "service"(工程师·所有权限) | "admin"(管理员) | "user"(用户)
 *   旧文件无 role 字段时，为保证既有账号功能不回退，默认按 service 处理。
 *
 * 仅本地、纯明文,不做加密或联网校验。
 */
public class LocalAccountStore {
    private static final String FILE_NAME = "accounts.json";

    /** 工程师：所有权限（含调试、关闭、左右舱切换） */
    public static final String ROLE_SERVICE = "service";
    /** 管理员：除调试/关闭/左右舱切换外的权限，可新建账号 */
    public static final String ROLE_ADMIN = "admin";
    /** 用户：除调试/关闭/左右舱切换外的权限 */
    public static final String ROLE_USER = "user";

    public static final class Account {
        public final String name;
        public String password;
        public String organization;
        public String role;

        public Account(String name, String password, String organization) {
            this(name, password, organization, ROLE_SERVICE);
        }

        public Account(String name, String password, String organization, String role) {
            this.name = name;
            this.password = password;
            this.organization = organization == null ? "" : organization;
            this.role = normalizeRole(role);
        }
    }

    private static String normalizeRole(String role) {
        if (ROLE_ADMIN.equals(role) || ROLE_USER.equals(role) || ROLE_SERVICE.equals(role)) {
            return role;
        }
        return ROLE_SERVICE;
    }

    private final File file;
    private final List<Account> accounts = new ArrayList<>();
    private String currentAccount = "";

    public LocalAccountStore(Context context) {
        file = new File(context.getFilesDir(), FILE_NAME);
        load();
        // ★ 2026-10-08：全新安装（无 accounts.json 或没有任何账号）时播种默认账号，
        //   否则 H5 登录页没有注册入口（任务#20 已移除），用户将永远无法登录。
        //   通过 UI 删除账号有"最后一个账号不可删"保护，不会走到这里。
        if (accounts.isEmpty()) {
            accounts.add(new Account("admin", "123456", "", ROLE_SERVICE));
            save();
        }
    }

    public synchronized List<String> listNames() {
        List<String> names = new ArrayList<>();
        for (Account a : accounts) {
            names.add(a.name);
        }
        return names;
    }

    public synchronized boolean hasAny() {
        return !accounts.isEmpty();
    }

    public synchronized String getCurrentAccount() {
        return currentAccount == null ? "" : currentAccount;
    }

    public synchronized void setCurrentAccount(String name) {
        currentAccount = TextUtils.isEmpty(name) ? "" : name;
        save();
    }

    public synchronized void clearCurrentAccount() {
        currentAccount = "";
        save();
    }

    public synchronized String getOrganizationFor(String name) {
        if (TextUtils.isEmpty(name)) {
            return "";
        }
        for (Account a : accounts) {
            if (a.name.equals(name)) {
                return a.organization == null ? "" : a.organization;
            }
        }
        return "";
    }

    public synchronized String getCurrentOrganization() {
        return getOrganizationFor(currentAccount);
    }

    public synchronized boolean setOrganizationForCurrent(String organization) {
        if (TextUtils.isEmpty(currentAccount)) {
            return false;
        }
        for (Account a : accounts) {
            if (a.name.equals(currentAccount)) {
                a.organization = organization == null ? "" : organization;
                return save();
            }
        }
        return false;
    }

    public synchronized boolean nameExists(String name) {
        if (TextUtils.isEmpty(name)) {
            return false;
        }
        for (Account a : accounts) {
            if (a.name.equals(name)) {
                return true;
            }
        }
        return false;
    }

    public synchronized boolean authenticate(String name, String password) {
        if (TextUtils.isEmpty(name) || password == null) {
            return false;
        }
        for (Account a : accounts) {
            if (a.name.equals(name) && a.password.equals(password)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 注册新账号,同名会失败。
     * @return true 表示注册成功;false 表示名称已存在或参数非法
     */
    public synchronized boolean register(String name, String password, String organization) {
        return register(name, password, organization, ROLE_SERVICE);
    }

    /** ★ 任务13：注册时指定角色 */
    public synchronized boolean register(String name, String password, String organization, String role) {
        if (TextUtils.isEmpty(name) || TextUtils.isEmpty(password)) {
            return false;
        }
        if (nameExists(name)) {
            return false;
        }
        accounts.add(new Account(name, password, organization, role));
        return save();
    }

    /* ---------- ★ 任务13：用户管理（R67-R73）所需接口 ---------- */

    /** 返回账号快照列表（用户名/密码/机构/角色），供设置-用户页渲染 */
    public synchronized List<Account> listAccounts() {
        List<Account> snapshot = new ArrayList<>(accounts);
        Collections.sort(snapshot, (left, right) -> left.name.compareToIgnoreCase(right.name));
        return snapshot;
    }

    public synchronized String getRoleFor(String name) {
        if (TextUtils.isEmpty(name)) {
            return "";
        }
        for (Account a : accounts) {
            if (a.name.equals(name)) {
                return a.role;
            }
        }
        return "";
    }

    /** 当前登录账号的角色；未登录返回 "" */
    public synchronized String getCurrentRole() {
        return getRoleFor(currentAccount);
    }

    /** ★ R93：仅工程师(Service)可切换左右舱、使用调试与关闭 */
    public synchronized boolean isCurrentService() {
        return ROLE_SERVICE.equals(getCurrentRole());
    }

    /** ★ 2026-09-30：账号管理（增/删/改）仅工程师/管理员可做 */
    public synchronized boolean isCurrentAdminOrService() {
        String r = getCurrentRole();
        return ROLE_SERVICE.equals(r) || ROLE_ADMIN.equals(r);
    }

    /**
     * 修改账号（用户名不允许改，靠 originalName 定位）。
     * @return 0 成功；-1 参数非法；-2 账号不存在；-3 新用户名已被占用
     */
    public synchronized int updateAccount(String originalName, String password,
                                          String organization, String role) {
        if (TextUtils.isEmpty(originalName)) {
            return -1;
        }
        for (Account a : accounts) {
            if (a.name.equals(originalName)) {
                if (password != null && password.length() > 0) {
                    a.password = password;
                }
                if (organization != null) {
                    a.organization = organization;
                }
                if (!TextUtils.isEmpty(role)) {
                    a.role = normalizeRole(role);
                }
                return save() ? 0 : -1;
            }
        }
        return -2;
    }

    /**
     * 删除账号。
     * 规则（R72/R73）：不能删除当前登录账号；不能删除最后一个账号。
     * @return 0 成功；-1 参数非法；-2 账号不存在；-3 是当前登录账号；-4 是最后一个账号
     */
    public synchronized int deleteAccount(String name) {
        if (TextUtils.isEmpty(name)) {
            return -1;
        }
        if (name.equals(currentAccount)) {
            return -3;
        }
        if (accounts.size() <= 1) {
            return -4;
        }
        for (int i = 0; i < accounts.size(); i += 1) {
            if (accounts.get(i).name.equals(name)) {
                accounts.remove(i);
                return save() ? 0 : -1;
            }
        }
        return -2;
    }

    private void load() {
        accounts.clear();
        currentAccount = "";
        if (file == null || !file.exists()) {
            return;
        }
        FileInputStream is = null;
        try {
            is = new FileInputStream(file);
            byte[] data = new byte[(int) file.length()];
            int read = is.read(data);
            String content = new String(data, 0, Math.max(read, 0), StandardCharsets.UTF_8);
            JSONObject root = new JSONObject(content);
            JSONArray arr = root.optJSONArray("accounts");
            if (arr != null) {
                for (int i = 0; i < arr.length(); i += 1) {
                    JSONObject obj = arr.optJSONObject(i);
                    if (obj == null) {
                        continue;
                    }
                    String name = obj.optString("name", "");
                    String password = obj.optString("password", "");
                    String organization = obj.optString("organization", "");
                    // ★ 任务13：旧文件无 role 时默认 service，保证既有账号权限不回退
                    String role = obj.has("role") ? obj.optString("role", ROLE_SERVICE) : ROLE_SERVICE;
                    if (!TextUtils.isEmpty(name) && !TextUtils.isEmpty(password)) {
                        accounts.add(new Account(name, password, organization, role));
                    }
                }
            }
            currentAccount = root.optString("currentAccount", "");
            if (!nameExists(currentAccount)) {
                currentAccount = "";
            }
        } catch (IOException | JSONException ignored) {
            accounts.clear();
            currentAccount = "";
        } finally {
            if (is != null) {
                try {
                    is.close();
                } catch (IOException ignored) {
                }
            }
        }
    }

    private synchronized boolean save() {
        if (file == null) {
            return false;
        }
        File tmp = new File(file.getParentFile(), FILE_NAME + ".tmp");
        FileOutputStream os = null;
        try {
            JSONObject root = new JSONObject();
            JSONArray arr = new JSONArray();
            List<Account> snapshot = new ArrayList<>(accounts);
            Collections.sort(snapshot, (left, right) -> left.name.compareToIgnoreCase(right.name));
            for (Account a : snapshot) {
                JSONObject obj = new JSONObject();
                obj.put("name", a.name);
                obj.put("password", a.password);
                obj.put("organization", a.organization == null ? "" : a.organization);
                obj.put("role", a.role);
                arr.put(obj);
            }
            root.put("accounts", arr);
            root.put("currentAccount", currentAccount == null ? "" : currentAccount);
            os = new FileOutputStream(tmp);
            os.write(root.toString().getBytes(StandardCharsets.UTF_8));
            os.flush();
        } catch (JSONException | IOException e) {
            if (os != null) {
                try {
                    os.close();
                } catch (IOException ignored) {
                }
            }
            return false;
        } finally {
            if (os != null) {
                try {
                    os.close();
                } catch (IOException ignored) {
                }
            }
        }
        if (file.exists() && !file.delete()) {
            return false;
        }
        return tmp.renameTo(file);
    }
}
