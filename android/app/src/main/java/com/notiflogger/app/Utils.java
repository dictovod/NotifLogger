package com.notiflogger.app;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.telephony.TelephonyManager;
import android.widget.Toast;

import androidx.core.content.ContextCompat;

import java.security.SecureRandom;

/**
 * Утилиты для работы с устройством и общие функции
 */
public class Utils {

    /**
     * Получает IMEI устройства
     * Если нет разрешения - генерирует мок IMEI для демо
     */
    public static String getDeviceIMEI(Context context) {
        // Проверяем разрешение на чтение состояния телефона
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_PHONE_STATE) 
                == PackageManager.PERMISSION_GRANTED) {
            
            try {
                TelephonyManager telephonyManager = 
                    (TelephonyManager) context.getSystemService(Context.TELEPHONY_SERVICE);
                
                if (telephonyManager != null) {
                    String imei = telephonyManager.getImei();
                    if (imei != null && !imei.isEmpty()) {
                        return imei;
                    }
                }
            } catch (SecurityException e) {
                // Fallback к генерации мок IMEI
            }
        }
        
        // Генерируем или получаем сохраненный мок IMEI
        return getMockIMEI(context);
    }

    /**
     * Генерирует или получает сохраненный мок IMEI для демо
     */
    private static String getMockIMEI(Context context) {
        android.content.SharedPreferences prefs = 
            context.getSharedPreferences("device_info", Context.MODE_PRIVATE);
        
        String mockImei = prefs.getString("mock_imei", null);
        
        if (mockImei == null) {
            // Генерируем 15-значный IMEI
            StringBuilder imei = new StringBuilder();
            SecureRandom random = new SecureRandom();
            
            for (int i = 0; i < 15; i++) {
                imei.append(random.nextInt(10));
            }
            
            mockImei = imei.toString();
            
            // Сохраняем для постоянного использования
            prefs.edit().putString("mock_imei", mockImei).apply();
        }
        
        return mockImei;
    }

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
     * Проверяет, является ли строка валидным IMEI (15 цифр)
     */
    public static boolean isValidIMEI(String imei) {
        if (imei == null || imei.isEmpty()) {
            return false;
        }
        
        // IMEI должен содержать ровно 15 цифр
        return imei.matches("\\d{15}");
    }

    /**
     * Форматирует IMEI для отображения (добавляет пробелы)
     */
    public static String formatIMEI(String imei) {
        if (imei == null || imei.length() != 15) {
            return imei;
        }
        
        // Форматируем как XXX XXX XXX XXX XXX
        StringBuilder formatted = new StringBuilder();
        for (int i = 0; i < imei.length(); i++) {
            if (i > 0 && i % 3 == 0) {
                formatted.append(" ");
            }
            formatted.append(imei.charAt(i));
        }
        
        return formatted.toString();
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
}