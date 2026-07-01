package com.exampl.antiaddiction.data.common;

public final class UserIdNormalizer {

    private UserIdNormalizer() {}

    public static String normalize(String rawUserId) {
        if (rawUserId == null) {
            return "";
        }
        String trimmed = rawUserId.trim();
        if (trimmed.endsWith(".0")) {
            trimmed = trimmed.substring(0, trimmed.length() - 2);
        }
        return trimmed;
    }

    public static String normalizeForCloudQuery(String rawUserId) {
        String normalized = normalize(rawUserId);
        if (normalized.contains(".")) {
            normalized = normalized.split("\\.")[0];
        }
        return normalized;
    }
}
