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
     * Возвращает null если нет разрешения или не удается получить IMEI
     * Использует разные методы в зависимости от версии Android
     */
    public static String getDeviceIMEI(Context context) {
        // Проверяем разрешение на чтение состояния телефона
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_PHONE_STATE)
                != PackageManager.PERMISSION_GRANTED) {
            return null;
        }

        try {
            TelephonyManager telephonyManager =
                    (TelephonyManager) context.getSystemService(Context.TELEPHONY_SERVICE);

            if (telephonyManager == null) {
                return null;
            }

            String imei = null;
            int apiLevel = android.os.Build.VERSION.SDK_INT;

            // Android 13+ (API 33+) - требует особого подхода
            if (apiLevel >= 33) {
                // Android 13 и выше - сложности с получением IMEI
                imei = getImeiForAndroid13Plus(telephonyManager);
            }
            // Android 10-12 (API 29-32)
            else if (apiLevel >= 29) {
                imei = getImeiForAndroid10Plus(telephonyManager);
            }
            // Android 8-9 (API 26-28)
            else if (apiLevel >= 26) {
                imei = getImeiForAndroid8Plus(telephonyManager);
            }
            // Android 7 (API 24-25)
            else if (apiLevel >= 24) {
                imei = getImeiForAndroid7(telephonyManager);
            }
            // Android 6 и ниже (API 23 и ниже) - legacy метод
            else {
                imei = getImeiLegacy(telephonyManager);
            }

            return imei;

        } catch (SecurityException e) {
            // Нет разрешения
            return null;
        }
    }

    /**
     * Получает IMEI для Android 13+ (API 33+)
     */
    private static String getImeiForAndroid13Plus(TelephonyManager telephonyManager) {
        try {
            // В Android 13+ getImei() может быть ограничен
            // Пробуем различные способы
            String imei = telephonyManager.getImei();
            if (imei != null && !imei.isEmpty() && !imei.equals("000000000000000")) {
                return imei;
            }

            // Пробуем с индексом слота
            imei = telephonyManager.getImei(0);
            if (imei != null && !imei.isEmpty() && !imei.equals("000000000000000")) {
                return imei;
            }

            // Если есть второй слот
            if (telephonyManager.getPhoneCount() > 1) {
                imei = telephonyManager.getImei(1);
                if (imei != null && !imei.isEmpty() && !imei.equals("000000000000000")) {
                    return imei;
                }
            }

            // Fallback к getDeviceId для совместимости
            imei = telephonyManager.getDeviceId();
            if (imei != null && !imei.isEmpty() && !imei.equals("000000000000000")) {
                return imei;
            }

        } catch (Exception e) {
            // Игнорируем исключения
        }
        return null;
    }

    /**
     * Получает IMEI для Android 10-12 (API 29-32)
     */
    private static String getImeiForAndroid10Plus(TelephonyManager telephonyManager) {
        try {
            // Основной метод для Android 10+
            String imei = telephonyManager.getImei();
            if (imei != null && !imei.isEmpty()) {
                return imei;
            }

            // Пробуем с индексом слота (0 - первый SIM)
            imei = telephonyManager.getImei(0);
            if (imei != null && !imei.isEmpty()) {
                return imei;
            }

            // Если есть второй слот
            if (telephonyManager.getPhoneCount() > 1) {
                imei = telephonyManager.getImei(1);
                if (imei != null && !imei.isEmpty()) {
                    return imei;
                }
            }

            // Fallback метод
            imei = telephonyManager.getDeviceId();
            if (imei != null && !imei.isEmpty()) {
                return imei;
            }

        } catch (Exception e) {
            // Игнорируем исключения
        }
        return null;
    }

    /**
     * Получает IMEI для Android 8-9 (API 26-28)
     */
    private static String getImeiForAndroid8Plus(TelephonyManager telephonyManager) {
        try {
            // В Android 8+ появился метод getImei()
            String imei = telephonyManager.getImei();
            if (imei != null && !imei.isEmpty()) {
                return imei;
            }

            // Пробуем с индексом слота
            imei = telephonyManager.getImei(0);
            if (imei != null && !imei.isEmpty()) {
                return imei;
            }

            // Для dual SIM устройств
            if (telephonyManager.getPhoneCount() > 1) {
                imei = telephonyManager.getImei(1);
                if (imei != null && !imei.isEmpty()) {
                    return imei;
                }
            }

            // Fallback к старому методу
            imei = telephonyManager.getDeviceId();
            if (imei != null && !imei.isEmpty()) {
                return imei;
            }

        } catch (Exception e) {
            // Игнорируем исключения
        }
        return null;
    }

    /**
     * Получает IMEI для Android 7 (API 24-25)
     */
    private static String getImeiForAndroid7(TelephonyManager telephonyManager) {
        try {
            // В Android 7 используем getDeviceId()
            String imei = telephonyManager.getDeviceId();
            if (imei != null && !imei.isEmpty()) {
                return imei;
            }

            // Пробуем с индексом слота для dual SIM
            if (telephonyManager.getPhoneCount() > 1) {
                imei = telephonyManager.getDeviceId(1);
                if (imei != null && !imei.isEmpty()) {
                    return imei;
                }
            }

        } catch (Exception e) {
            // Игнорируем исключения
        }
        return null;
    }

    /**
     * Получает IMEI для Android 6 и ниже (legacy метод)
     */
    private static String getImeiLegacy(TelephonyManager telephonyManager) {
        try {
            // Для старых версий Android используем getDeviceId()
            String imei = telephonyManager.getDeviceId();
            if (imei != null && !imei.isEmpty()) {
                return imei;
            }
        } catch (Exception e) {
            // Игнорируем исключения
        }
        return null;
    }

    /**
     * Генерирует или получает сохраненный мок IMEI для демо
     * ИСПРАВЛЕНО: Теперь использует алгоритм Luhn для валидного IMEI
     */
    private static String getMockIMEI(Context context) {
        android.content.SharedPreferences prefs =
                context.getSharedPreferences("device_info", Context.MODE_PRIVATE);

        String mockImei = prefs.getString("mock_imei", null);

        if (mockImei == null) {
            // Генерируем валидный IMEI с алгоритмом Luhn
            mockImei = generateValidIMEI();

            // Сохраняем для постоянного использования
            prefs.edit().putString("mock_imei", mockImei).apply();
        }

        return mockImei;
    }

    /**
     * Генерирует валидный IMEI с алгоритмом Luhn
     */
    private static String generateValidIMEI() {
        SecureRandom random = new SecureRandom();
        StringBuilder imei = new StringBuilder();

        // Генерируем первые 14 цифр
        for (int i = 0; i < 14; i++) {
            imei.append(random.nextInt(10));
        }

        // Вычисляем контрольную сумму по алгоритму Luhn
        int sum = 0;
        String imei14 = imei.toString();
        
        for (int i = 0; i < 14; i++) {
            int digit = Character.getNumericValue(imei14.charAt(i));
            
            // Удваиваем каждую вторую цифру справа (позиции 1, 3, 5, ...)
            if ((14 - i) % 2 == 0) {
                digit *= 2;
                if (digit > 9) {
                    digit = digit / 10 + digit % 10;
                }
            }
            sum += digit;
        }

        // Контрольная цифра
        int checkDigit = (10 - (sum % 10)) % 10;
        imei.append(checkDigit);

        return imei.toString();
    }

    /**
     * Валидирует IMEI с помощью алгоритма Luhn
     */
    public static boolean validateIMEI(String imei) {
        if (imei == null || imei.length() != 15) {
            return false;
        }

        // Проверяем что все символы - цифры
        if (!imei.matches("\\d{15}")) {
            return false;
        }

        // Проверка алгоритмом Luhn
        int sum = 0;
        for (int i = 0; i < 15; i++) {
            int digit = Character.getNumericValue(imei.charAt(i));
            
            // Удваиваем каждую вторую цифру справа (позиции 1, 3, 5, ...)
            if ((15 - i) % 2 == 0) {
                digit *= 2;
                if (digit > 9) {
                    digit = digit / 10 + digit % 10;
                }
            }
            sum += digit;
        }

        return sum % 10 == 0;
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
     * ИСПРАВЛЕНО: Теперь использует алгоритм Luhn
     */
    public static boolean isValidIMEI(String imei) {
        return validateIMEI(imei);
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
     * Проверяет поддержку метода getImei() в текущей версии Android
     */
    public static boolean supportsGetImeiMethod() {
        return android.os.Build.VERSION.SDK_INT >= 26; // Android 8.0+
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
     * Проверяет, есть ли разрешение на чтение состояния телефона
     */
    public static boolean hasPhoneStatePermission(Context context) {
        return ContextCompat.checkSelfPermission(context, Manifest.permission.READ_PHONE_STATE)
                == PackageManager.PERMISSION_GRANTED;
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