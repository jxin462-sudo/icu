# ICU 动物监护 App（icu-app）

兽用 ICU 监护舱 Android 应用（包名 `com.example.icuapk`）。
UI 为 WebView 内嵌 H5（`app/src/main/assets/lanhu/v2/`），经 `IcuNative` JS 桥与原生层交互。

## 本分支说明

- 本项目放在仓库的 `icu-app` 分支、`icu-app/` 子目录，**与 main 分支的 AML 站点互不干扰**。
- 多人协作：合并前请先确认 main 的最新状态，不要直接动 main。

## 构建

```bat
.\gradlew.bat assembleDebug
```

产物：`app\build\outputs\apk\debug\app-debug.apk`

## 关键约束（CC任务书_ICU_UI对齐.md）

- 只改 UI 层（`lanhu/v2`）与必要的 Java 数据桥；**绝不修改 BleManager.java / 蓝牙协议**。
- 不破坏原生桥契约：`IcuNative.action / firstPhaseState / syncTempPatient / login / logout / bleAction`。
- 不伪造业务数据：无数据显示 `--`。
- 改动 JS 后跑 `node --check`。
