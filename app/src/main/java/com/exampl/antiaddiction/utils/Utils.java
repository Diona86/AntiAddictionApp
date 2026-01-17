package com.exampl.antiaddiction.utils;

public class Utils {
    static public String formatTime(long millis) {
        long seconds = millis / 1000;
        long minutes = seconds / 60;
        long hours = minutes / 60;

        if (hours > 0) {
            return String.format("%02dh %02dm", hours, minutes % 60);
        } else {
            return String.format("%02dm %02ds", minutes, seconds % 60);
        }
    }
}
