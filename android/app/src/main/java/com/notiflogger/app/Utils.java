package com.notiflogger.app;

import android.content.Context;
import android.content.pm.PackageManager;
import android.provider.Settings;
import android.widget.Toast;

import androidx.core.content.ContextCompat;

/**
 * Утилиты для работы с устройством и общие функции
 */
public class Utils {

    /**
     * Показывает короткое Toast сообщение
     */
    public static void showToast(Context context, String message) {
        Toast.makeText(context, message, Toast.LENGTH_SHORT).show();
    }

    /**
     * Показывает длинное Toast сообщение
     */
    public static void showLongToast(Context context, String message) {
        Toast.makeText(context, message, Toast.LENGTH_LONG).show();
    }

    /**
     * Форматирует размер файла в человекочитаемый формат
     */
    public static String formatFileSize(long bytes) {
        if (bytes < 1024) return bytes + " B";
        int exp = (int) (Math.log(bytes) / Math.log(1024));
        String pre = "KMGTPE".charAt(exp - 1) + "";
        return String.format("%.1f %sB", bytes / Math.pow(1024, exp), pre);
    }

    /**
     * Форматирует время в читаемый формат
     */
    public static String formatDuration(long millis) {
        long seconds = millis / 1000;
        long minutes = seconds / 60;
        long hours = minutes / 60;
        long days = hours / 24;

        if (days > 0) {
            return String.format("%d дн. %d ч.", days, hours % 24);
        } else if (hours > 0) {
            return String.format("%d ч. %d мин.", hours, minutes % 60);
        } else if (minutes > 0) {
            return String.format("%d мин. %d сек.", minutes, seconds % 60);
        } else {
            return String.format("%d сек.", seconds);
        }
    }

    /**
     * Получает серийный номер устройства для дополнительной идентификации
     */
    public static String getDeviceSerial() {
        try {
            return android.os.Build.SERIAL;
        } catch (Exception e) {
            return "unknown";
        }
    }

    /**
     * Получает информацию об устройстве для логирования
     */
    public static String getDeviceInfo() {
        return String.format(
                "Device: %s %s\nAndroid: %s (API %d)\nSerial: %s",
                android.os.Build.MANUFACTURER,
                android.os.Build.MODEL,
                android.os.Build.VERSION.RELEASE,
                android.os.Build.VERSION.SDK_INT,
                getDeviceSerial()
        );
    }

    /**
     * Получает версию Android API
     */
    public static int getAndroidApiLevel() {
        return android.os.Build.VERSION.SDK_INT;
    }

    /**
     * Получает человекочитаемое название версии Android
     */
    public static String getAndroidVersionName() {
        int apiLevel = android.os.Build.VERSION.SDK_INT;
        
        if (apiLevel >= 33) return "Android 13+ (API " + apiLevel + ")";
        else if (apiLevel >= 32) return "Android 12L (API " + apiLevel + ")";
        else if (apiLevel >= 31) return "Android 12 (API " + apiLevel + ")";
        else if (apiLevel >= 30) return "Android 11 (API " + apiLevel + ")";
        else if (apiLevel >= 29) return "Android 10 (API " + apiLevel + ")";
        else if (apiLevel >= 28) return "Android 9 (API " + apiLevel + ")";
        else if (apiLevel >= 26) return "Android 8+ (API " + apiLevel + ")";
        else if (apiLevel >= 24) return "Android 7+ (API " + apiLevel + ")";
        else return "Android Legacy (API " + apiLevel + ")";
    }

    /**
     * Проверяет, включен ли сервис доступа к уведомлениям
     */
    public static boolean isNotificationServiceEnabled(Context context) {
        String enabledListeners = android.provider.Settings.Secure.getString(
                context.getContentResolver(),
                "enabled_notification_listeners"
        );

        if (enabledListeners == null || enabledListeners.isEmpty()) {
            return false;
        }

        String packageName = context.getPackageName();
        return enabledListeners.contains(packageName);
    }
}