package com.example.icuapk;

/**
 * Pure helpers for the five timed controls (red/blue/UV/nebulizer/anion).
 *
 * 口径：
 * - 紫外(12) / 雾化(10) / 负离子(11) 默认 120 分钟；
 * - 红外(6) / 蓝光(7) 不限时，协议时长值 65536；
 * - 不限时控件不产生本地倒计时终点，避免到点误发关闭命令。
 */
final class TimedControlProtocol {
    static final int UNLIMITED_MINUTES = 65536;
    static final int DEFAULT_MINUTES = 120;
    static final int MAX_MINUTES = 120;

    private TimedControlProtocol() {
    }

    static boolean isTimedControlIndex(int index) {
        return index == 6 || index == 7 || index == 10 || index == 11 || index == 12;
    }

    /**
     * 用户直接点击开关时，本次运行应发送的协议时长。
     * 该值不是用户在“设置”中保存的配置值，也不是护理模式默认值。
     */
    static int directStartMinutes(int index) {
        if (index == 6 || index == 7) {
            return UNLIMITED_MINUTES;
        }
        return DEFAULT_MINUTES;
    }

    static boolean isUnlimitedMinutes(int minutes) {
        return minutes == UNLIMITED_MINUTES;
    }

    /** 只有红外/蓝光可以下发 65536，其余仍限制 0-120。 */
    static boolean isAcceptableMinutes(int index, int minutes) {
        if (!isTimedControlIndex(index)) {
            return true;
        }
        if (minutes < 0) {
            return false;
        }
        if (isUnlimitedMinutes(minutes)) {
            return index == 6 || index == 7;
        }
        return minutes <= MAX_MINUTES;
    }

    /**
     * 主机回包的 time_remaining 换算成本地倒计时终点。
     * 返回 0 表示不应该有倒计时（已关闭、或不限时运行）。
     */
    static long countdownEndAtMs(long nowMs, boolean enabled, int remainingMinutes) {
        if (!enabled || remainingMinutes <= 0 || isUnlimitedMinutes(remainingMinutes)) {
            return 0L;
        }
        return nowMs + remainingMinutes * 60000L;
    }

    /** 主机回包是否表示"该控件正在不限时运行"。 */
    static boolean isUnlimitedRunning(boolean enabled, int remainingMinutes) {
        return enabled && isUnlimitedMinutes(remainingMinutes);
    }
}