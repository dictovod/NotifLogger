package com.notiflogger.app;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Build;
import android.provider.Settings;
import android.telephony.TelephonyManager;
import android.util.Base64;
import android.util.Log;

import org.json.JSONObject;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;

/**
 * ИСПРАВЛЕННЫЙ Менеджер активации приложения
 * Исправлены проблемы с часовыми поясами, парсингом дат, получением IMEI на Android 10+ и добавлено подробное логирование в файл.
 */
public class ActivationManager {
    
    private static final String PREFS_NAME = "activation_data";
    private static final String KEY_IS_ACTIVATED = "is_activated";
    private static final String KEY_ACTIVATION_TIME = "activation_time";
    private static final String KEY_EXPIRATION_TIME = "expiration_time";
    private static final String KEY_DEVICE_IMEI = "device_imei";
    private static final String KEY_TOKEN_UUID = "token_uuid";
    private static final String TAG = "ActivationManager";
    private static final String DEBUG_LOG_PATH = "/storage/emulated/0/Android/data/com.notiflogger.app/files/notification_logs/debug_activation.log";
    
    private final SharedPreferences preferences;
    private final Context context;

    public ActivationManager(Context context) {
        this.context = context;
        this.preferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    /**
     * Активирует приложение с указанными параметрами
     */
    public void activate(String uuid, String startDate, int durationSeconds) {
        try {
            Date startDateParsed = parseISODateFixed(startDate);
            if (startDateParsed == null) {
                Log.e(TAG, "Не удалось распарсить дату начала: " + startDate);
                writeToDebugFile("Ошибка: Не удалось распарсить дату начала: " + startDate);
                return;
            }

            Date currentDate = new Date();
            Date expirationDate = new Date(startDateParsed.getTime() + (durationSeconds * 1000L));

            if (currentDate.before(startDateParsed) || currentDate.after(expirationDate)) {
                Log.w(TAG, "Дата вне диапазона: текущая=" + currentDate + 
                      ", начало=" + startDateParsed + ", конец=" + expirationDate);
                writeToDebugFile("Предупреждение: Дата вне диапазона: текущая=" + currentDate + 
                                 ", начало=" + startDateParsed + ", конец=" + expirationDate);
                return;
            }

            String deviceId = getDeviceUniqueId();
            if (deviceId == null) {
                Log.e(TAG, "Не удалось получить уникальный ID устройства");
                writeToDebugFile("Ошибка: Не удалось получить уникальный ID устройства");
                return;
            }

            saveActivation(deviceId, uuid, currentDate.getTime(), expirationDate.getTime());
        } catch (Exception e) {
            Log.e(TAG, "Ошибка активации: " + e.getMessage(), e);
            writeToDebugFile("Ошибка активации: " + e.getMessage());
        }
    }

    /**
     * Деактивирует приложение
     */
    public void deactivate() {
        clearActivation();
    }

    /**
     * ИСПРАВЛЕННАЯ версия проверки и активации с помощью токена
     */
    public boolean validateAndActivate(String token) {
        try {
            String deviceId = getDeviceUniqueId();
            if (deviceId == null) {
                Log.e(TAG, "Уникальный ID устройства равен null");
                writeToDebugFile("Ошибка: Уникальный ID устройства равен null");
                return false;
            }

            if (token == null || token.trim().isEmpty()) {
                Log.e(TAG, "Токен пустой или null");
                writeToDebugFile("Ошибка: Токен пустой или null");
                return false;
            }

            // Декодируем токен
            byte[] decodedBytes = Base64.decode(token.trim(), Base64.URL_SAFE);
            String decodedJson = new String(decodedBytes);
            Log.d(TAG, "Декодированный JSON: " + decodedJson);
            writeToDebugFile("Декодированный JSON: " + decodedJson);
            
            JSONObject tokenData = new JSONObject(decodedJson);
            String tokenDeviceId = tokenData.optString("imei", null);
            String tokenUuid = tokenData.optString("uuid", null);
            String startDateStr = tokenData.optString("start_date", null);
            long durationSeconds = tokenData.optLong("duration_seconds", -1);

            // Проверяем наличие всех необходимых данных
            if (tokenDeviceId == null || tokenUuid == null || startDateStr == null || durationSeconds == -1) {
                Log.e(TAG, "Недостаточно данных в токене");
                writeToDebugFile("Ошибка: Недостаточно данных в токене");
                return false;
            }
            
            // Проверяем Device ID (IMEI или ANDROID_ID)
            if (!tokenDeviceId.equals(deviceId)) {
                Log.w(TAG, "Device ID не совпадают: устройство=" + deviceId + ", токен=" + tokenDeviceId);
                writeToDebugFile("Предупреждение: Device ID не совпадают: устройство=" + deviceId + ", токен=" + tokenDeviceId);
                return false;
            }
            
            // ИСПРАВЛЕНИЕ: Используем новый метод парсинга дат
            Date startDate = parseISODateFixed(startDateStr);
            if (startDate == null) {
                Log.e(TAG, "Не удалось распарсить дату начала: " + startDateStr);
                writeToDebugFile("Ошибка: Не удалось распарсить дату начала: " + startDateStr);
                return false;
            }
            
            Date expirationDate = new Date(startDate.getTime() + (durationSeconds * 1000L));
            Date currentDate = new Date();
            
            Log.d(TAG, "Текущее время: " + currentDate);
            Log.d(TAG, "Время начала: " + startDate);
            Log.d(TAG, "Время окончания: " + expirationDate);
            writeToDebugFile("Текущее время: " + currentDate + "\nВремя начала: " + startDate + "\nВремя окончания: " + expirationDate);
            
            // ИСПРАВЛЕНИЕ: Добавляем небольшой буфер времени (30 секунд) для компенсации сетевых задержек
            long timeBuffer = 30 * 1000L; // 30 секунд
            Date startDateWithBuffer = new Date(startDate.getTime() - timeBuffer);
            
            if (currentDate.before(startDateWithBuffer)) {
                Log.w(TAG, "Токен еще не действителен");
                writeToDebugFile("Предупреждение: Токен еще не действителен (текущее время раньше start с буфером)");
                return false;
            }
            
            if (currentDate.after(expirationDate)) {
                Log.w(TAG, "Токен истек");
                writeToDebugFile("Предупреждение: Токен истек (текущее время позже expiration)");
                return false;
            }
            
            saveActivation(deviceId, tokenUuid, currentDate.getTime(), expirationDate.getTime());
            Log.i(TAG, "Активация успешна: deviceId=" + deviceId + ", uuid=" + tokenUuid);
            writeToDebugFile("Успех: Активация успешна: deviceId=" + deviceId + ", uuid=" + tokenUuid);
            return true;
            
        } catch (Exception e) {
            Log.e(TAG, "Ошибка валидации токена: " + e.getMessage(), e);
            writeToDebugFile("Ошибка валидации токена: " + e.getMessage());
            return false;
        }
    }

    /**
     * Проверяет, активировано ли приложение
     */
    public boolean isActivated() {
        boolean activated = preferences.getBoolean(KEY_IS_ACTIVATED, false);
        
        if (activated && isActivationExpired()) {
            Log.w(TAG, "Активация истекла, очищаем данные");
            writeToDebugFile("Предупреждение: Активация истекла, очищаем данные");
            clearActivation();
            return false;
        }
        
        return activated;
    }

    /**
     * Проверяет, истек ли срок активации
     */
    public boolean isActivationExpired() {
        long expirationTime = preferences.getLong(KEY_EXPIRATION_TIME, 0);
        if (expirationTime == 0) {
            return false;
        }
        
        boolean expired = System.currentTimeMillis() > expirationTime;
        if (expired) {
            writeToDebugFile("Проверка истечения: Да, истек (текущее время > " + expirationTime + ")");
        }
        return expired;
    }

    /**
     * Получает время истечения активации
     */
    public long getExpirationTime() {
        return preferences.getLong(KEY_EXPIRATION_TIME, 0);
    }

    /**
     * Получает время активации
     */
    public long getActivationTime() {
        return preferences.getLong(KEY_ACTIVATION_TIME, 0);
    }

    /**
     * Очищает данные активации
     */
    public void clearActivation() {
        preferences.edit()
                .putBoolean(KEY_IS_ACTIVATED, false)
                .putLong(KEY_ACTIVATION_TIME, 0)
                .putLong(KEY_EXPIRATION_TIME, 0)
                .putString(KEY_DEVICE_IMEI, "")
                .putString(KEY_TOKEN_UUID, "")
                .apply();
        Log.i(TAG, "Данные активации очищены");
        writeToDebugFile("Инфо: Данные активации очищены");
    }

    /**
     * Сохраняет данные активации
     */
    private void saveActivation(String deviceId, String tokenUuid, long activationTime, long expirationTime) {
        preferences.edit()
                .putBoolean(KEY_IS_ACTIVATED, true)
                .putLong(KEY_ACTIVATION_TIME, activationTime)
                .putLong(KEY_EXPIRATION_TIME, expirationTime)
                .putString(KEY_DEVICE_IMEI, deviceId)
                .putString(KEY_TOKEN_UUID, tokenUuid)
                .apply();
        Log.i(TAG, "Данные активации сохранены: deviceId=" + deviceId + ", uuid=" + tokenUuid);
        writeToDebugFile("Инфо: Данные активации сохранены: deviceId=" + deviceId + ", uuid=" + tokenUuid);
    }

    /**
     * ИСПРАВЛЕННЫЙ метод парсинга дат с правильной обработкой UTC
     */
    private Date parseISODateFixed(String dateStr) {
        if (dateStr == null || dateStr.trim().isEmpty()) {
            Log.e(TAG, "Пустая строка даты");
            writeToDebugFile("Ошибка: Пустая строка даты");
            return null;
        }

        Log.d(TAG, "Парсинг даты: " + dateStr);
        writeToDebugFile("Парсинг даты: " + dateStr);
        
        // Список форматов с приоритетом UTC форматов
        SimpleDateFormat[] formats = {
            // UTC форматы (с Z на конце)
            new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US),
            new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US),
            new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSSSS'Z'", Locale.US),
            // Форматы без явного указания часового пояса (интерпретируем как UTC)
            new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US),
            new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS", Locale.US),
            new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSSSS", Locale.US)
        };
        
        for (SimpleDateFormat format : formats) {
            try {
                // ИСПРАВЛЕНИЕ: Всегда интерпретируем как UTC
                format.setTimeZone(TimeZone.getTimeZone("UTC"));
                Date result = format.parse(dateStr);
                Log.d(TAG, "Дата успешно распарсена как UTC: " + result);
                writeToDebugFile("Успех: Дата успешно распарсена как UTC: " + result + " (формат: " + format.toPattern() + ")");
                return result;
            } catch (ParseException e) {
                writeToDebugFile("Попытка парсинга неудачна для формата: " + format.toPattern() + " - " + e.getMessage());
                // Продолжаем со следующим форматом
            }
        }
        
        Log.e(TAG, "Не удалось распарсить дату ни в одном из форматов: " + dateStr);
        writeToDebugFile("Ошибка: Не удалось распарсить дату ни в одном из форматов: " + dateStr);
        return null;
    }

    /**
     * Получает уникальный ID устройства (IMEI для старых Android, ANDROID_ID для новых)
     */
    public String getDeviceUniqueId() {
        String deviceId = null;
        try {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
                // Для Android <10: Пытаемся получить IMEI (требует READ_PHONE_STATE)
                TelephonyManager telephonyManager = (TelephonyManager) context.getSystemService(Context.TELEPHONY_SERVICE);
                if (telephonyManager != null) {
                    deviceId = telephonyManager.getImei();
                }
                if (deviceId == null) {
                    Log.w(TAG, "IMEI null на старом Android, fallback на ANDROID_ID");
                    writeToDebugFile("Предупреждение: IMEI null на старом Android, fallback на ANDROID_ID");
                    deviceId = Settings.Secure.getString(context.getContentResolver(), Settings.Secure.ANDROID_ID);
                }
            } else {
                // Для Android 10+: Используем ANDROID_ID (без разрешений)
                deviceId = Settings.Secure.getString(context.getContentResolver(), Settings.Secure.ANDROID_ID);
            }
            Log.d(TAG, "Получен уникальный ID устройства: " + (deviceId != null ? deviceId : "null"));
            writeToDebugFile("Инфо: Получен уникальный ID устройства: " + (deviceId != null ? deviceId : "null") + " (версия Android: " + Build.VERSION.SDK_INT + ")");
        } catch (SecurityException e) {
            Log.e(TAG, "SecurityException при получении ID: " + e.getMessage());
            writeToDebugFile("Ошибка: SecurityException при получении ID: " + e.getMessage() + " - fallback на ANDROID_ID");
            deviceId = Settings.Secure.getString(context.getContentResolver(), Settings.Secure.ANDROID_ID);
        } catch (Exception e) {
            Log.e(TAG, "Ошибка получения ID: " + e.getMessage());
            writeToDebugFile("Ошибка получения ID: " + e.getMessage());
        }
        return deviceId;
    }

    /**
     * УЛУЧШЕННАЯ версия отладки токена
     */
    public String validateTokenDebug(String token) {
        StringBuilder debug = new StringBuilder();
        debug.append("=== ОТЛАДКА ТОКЕНА ===\n");
        
        try {
            String deviceId = getDeviceUniqueId();
            debug.append("Уникальный ID устройства: ").append(deviceId != null ? deviceId : "NULL").append("\n");
            writeToDebugFile("Отладка: Уникальный ID устройства: " + (deviceId != null ? deviceId : "NULL"));
            
            if (token == null || token.trim().isEmpty()) {
                debug.append("ОШИБКА: Токен пустой\n");
                writeToDebugFile("Отладка ошибка: Токен пустой");
                return debug.toString();
            }
            
            // Очищаем токен от лишних пробелов
            token = token.trim();
            debug.append("Длина токена: ").append(token.length()).append("\n");
            
            // Декодируем Base64
            byte[] decodedBytes;
            String decodedJson;
            try {
                decodedBytes = Base64.decode(token, Base64.URL_SAFE);
                decodedJson = new String(decodedBytes);
                debug.append("✓ Base64 декодирование успешно\n");
                debug.append("JSON: ").append(decodedJson).append("\n");
                writeToDebugFile("Отладка: ✓ Base64 декодирование успешно, JSON: " + decodedJson);
            } catch (Exception e) {
                debug.append("❌ Ошибка Base64: ").append(e.getMessage()).append("\n");
                writeToDebugFile("Отладка ошибка Base64: " + e.getMessage());
                return debug.toString();
            }
            
            // Парсим JSON
            JSONObject tokenData;
            try {
                tokenData = new JSONObject(decodedJson);
                debug.append("✓ JSON валиден\n");
                writeToDebugFile("Отладка: ✓ JSON валиден");
            } catch (Exception e) {
                debug.append("❌ Ошибка JSON: ").append(e.getMessage()).append("\n");
                writeToDebugFile("Отладка ошибка JSON: " + e.getMessage());
                return debug.toString();
            }
            
            // Извлекаем данные
            String tokenDeviceId = tokenData.optString("imei", "НЕТ");
            String tokenUuid = tokenData.optString("uuid", "НЕТ");
            String startDateStr = tokenData.optString("start_date", "НЕТ");
            long durationSeconds = tokenData.optLong("duration_seconds", -1);
            
            debug.append("Токен Device ID: ").append(tokenDeviceId).append("\n");
            debug.append("UUID: ").append(tokenUuid).append("\n");
            debug.append("Дата начала: ").append(startDateStr).append("\n");
            debug.append("Длительность: ").append(durationSeconds).append(" сек\n");
            
            // Проверяем Device ID
            if (deviceId != null && tokenDeviceId.equals(deviceId)) {
                debug.append("✓ Device ID совпадают\n");
                writeToDebugFile("Отладка: ✓ Device ID совпадают");
            } else {
                debug.append("❌ Device ID НЕ совпадают\n");
                writeToDebugFile("Отладка: ❌ Device ID НЕ совпадают");
            }
            
            // Парсим и проверяем даты
            Date startDate = parseISODateFixed(startDateStr);
            if (startDate != null) {
                Date currentDate = new Date();
                Date expirationDate = new Date(startDate.getTime() + (durationSeconds * 1000L));
                
                debug.append("✓ Дата распарсена\n");
                debug.append("Сейчас: ").append(currentDate).append("\n");
                debug.append("Начало: ").append(startDate).append("\n");
                debug.append("Конец: ").append(expirationDate).append("\n");
                writeToDebugFile("Отладка: ✓ Дата распарсена, Сейчас: " + currentDate + ", Начало: " + startDate + ", Конец: " + expirationDate);
                
                if (currentDate.before(startDate)) {
                    debug.append("❌ Токен еще не активен\n");
                    writeToDebugFile("Отладка: ❌ Токен еще не активен");
                } else if (currentDate.after(expirationDate)) {
                    debug.append("❌ Токен истек\n");
                    writeToDebugFile("Отладка: ❌ Токен истек");
                } else {
                    debug.append("✓ Токен действителен по времени\n");
                    writeToDebugFile("Отладка: ✓ Токен действителен по времени");
                }
            } else {
                debug.append("❌ Не удалось распарсить дату\n");
                writeToDebugFile("Отладка: ❌ Не удалось распарсить дату");
            }
            
        } catch (Exception e) {
            debug.append("КРИТИЧЕСКАЯ ОШИБКА: ").append(e.getMessage()).append("\n");
            writeToDebugFile("Отладка критическая ошибка: " + e.getMessage());
        }
        
        debug.append("=== КОНЕЦ ОТЛАДКИ ===");
        return debug.toString();
    }

    /**
     * Информация о текущей активации
     */
    public String getActivationInfo() {
        if (!isActivated()) {
            return "Приложение не активировано";
        }
        
        long activationTime = getActivationTime();
        long expirationTime = getExpirationTime();
        String deviceId = preferences.getString(KEY_DEVICE_IMEI, "");
        String uuid = preferences.getString(KEY_TOKEN_UUID, "");
        
        SimpleDateFormat sdf = new SimpleDateFormat("dd.MM.yyyy HH:mm:ss", Locale.getDefault());
        
        return String.format(
            "Активировано: %s\nИстекает: %s\nDevice ID: %s\nUUID: %s",
            sdf.format(new Date(activationTime)),
            sdf.format(new Date(expirationTime)),
            deviceId,
            uuid
        );
    }

    /**
     * Метод для записи в debug-файл с timestamp
     */
    private void writeToDebugFile(String message) {
        File logFile = new File(DEBUG_LOG_PATH);
        FileWriter writer = null;
        try {
            if (!logFile.getParentFile().exists()) {
                logFile.getParentFile().mkdirs();
            }
            if (!logFile.exists()) {
                logFile.createNewFile();
            }
            writer = new FileWriter(logFile, true); // Append mode
            SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US);
            String timestamp = sdf.format(new Date());
            writer.write("[" + timestamp + "] " + message + "\n");
        } catch (IOException e) {
            Log.e(TAG, "Ошибка записи в debug файл: " + e.getMessage(), e);
        } finally {
            if (writer != null) {
                try {
                    writer.close();
                } catch (IOException e) {
                    // Ignore
                }
            }
        }
    }
}