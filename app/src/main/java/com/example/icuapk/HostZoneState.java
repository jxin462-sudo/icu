package com.example.icuapk;

import java.util.ArrayDeque;
import java.util.HashSet;

/** Mutable ICU-host state owned by one logical cabin on the shared BLE link. */
final class HostZoneState {
    final String zone;
    boolean initialized;

    Float targetTemperature;
    Float targetOxygen;
    Float targetHumidity;
    Integer targetCo2;
    Integer previousTargetCo2;
    String statusLightColor = "";

    Integer treatmentMinutes;
    Boolean tempEnabled;
    Boolean o2Enabled;
    Boolean coldLight;
    Boolean warmLight;
    Boolean redTherapy;
    Boolean blueTherapy;
    Boolean outerCycle;
    Boolean innerCycle;
    Boolean nebulizer;
    Boolean anion;
    Boolean uv;
    Boolean co2Enabled;

    Integer redTime;
    Integer blueTime;
    Integer nebulizerTime;
    Integer anionTime;
    Integer uvTime;
    long redCountdownEndAtMs;
    long blueCountdownEndAtMs;
    long nebulizerCountdownEndAtMs;
    long anionCountdownEndAtMs;
    long uvCountdownEndAtMs;
    long treatmentCountdownEndAtMs;
    // ★ 治疗时长自动正计时（按舱隔离）：任一治疗项开启即"治疗中"，
    //   accumulated 是已完成段落的累计，runningSince 是本次连续治疗的起始时刻（0 表示未在计时）。
    long treatmentAccumulatedMs;
    long treatmentRunningSinceMs;
    final HashSet<Integer> unlimitedRunning = new HashSet<>();

    long lastCo2AutoVentAt;
    long lastCo2AlarmAt;
    long lastCo2RecoverAt;
    boolean co2AlarmActive;
    final ArrayDeque<String> co2AlarmHistory = new ArrayDeque<>();

    HostZoneState(String zone) {
        this.zone = zone;
    }
}
