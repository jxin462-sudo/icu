package com.example.icuapk;

import java.util.Locale;

/** Pure helpers for zone-aware ICU environment queries. */
final class EnvironmentProtocol {
    private EnvironmentProtocol() {
    }

    static String normalizeZone(String zone) {
        String value = zone == null ? "" : zone.trim().toLowerCase(Locale.US);
        return "left".equals(value) || "right".equals(value) ? value : "";
    }

    static String commandKey(String command, String zone) {
        String cmd = command == null ? "" : command.trim();
        String normalizedZone = normalizeZone(zone);
        return normalizedZone.isEmpty() ? cmd : cmd + ":" + normalizedZone;
    }

    static boolean isEnvironmentResponseCommand(String command) {
        return "get_temp".equals(command) || "get_o2".equals(command)
                || "get_humidity".equals(command) || "get_co2".equals(command)
                || "get_all_status".equals(command);
    }

    /** Empty means the response cannot be attributed safely. */
    static String resolveResponseZone(String payloadZone, String requestedZone,
                                      boolean hasMatchingRequest, boolean lateGuardActive) {
        String raw = payloadZone == null ? "" : payloadZone.trim();
        String explicit = normalizeZone(raw);
        if (!raw.isEmpty()) {
            return explicit; // malformed nonempty zones are rejected
        }
        if (lateGuardActive || !hasMatchingRequest) {
            return "";
        }
        return normalizeZone(requestedZone);
    }
}
