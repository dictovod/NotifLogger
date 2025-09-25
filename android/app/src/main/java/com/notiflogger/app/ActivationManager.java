package com.notiflogger.app;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Base64;
import android.util.Log;
import org.json.JSONObject;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;

/**
 * ИСПРАВЛЕННЫЙ Менеджер активации приложения
 * Исправлены проблемы с часовыми поясами и парсингом дат
 */
public class ActivationManager {
    
    private static final String PREFS_NAME = "activation_data";
    private static final String KEY_IS_ACTIVATED = "is_activated";
    private static final String KEY_ACTIVATION_TIME = "activation_time";
    private static final String KEY_EXPIRATION_TIME = "expiration_time";
    private static final String KEY_DEVICE_IMEI = "device_imei";
    private static final String KEY_TOKEN_UUID = "token_uuid";
    private static final String TAG = "ActivationManager";
    
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
                return;
            }

            Date currentDate = new Date();
            Date expirationDate = new Date(startDateParsed.getTime() + (durationSeconds * 1000L));

            if (currentDate.before(startDateParsed) || currentDate.after(expirationDate)) {
                Log.w(TAG, "Дата вне диапазона: текущая=" + currentDate + 
                      ", начало=" + startDateParsed + ", конец=" + expirationDate);
                return;
            }

            String imei = Utils.getDeviceIMEI(context);
            if (imei == null) {
                Log.e(TAG, "Не удалось получить IMEI устройства");
                return;
            }

            saveActivation(imei, uuid, currentDate.getTime(), expirationDate.getTime());
        } catch (Exception e) {
            Log.e(TAG, "Ошибка активации: " + e.getMessage(), e);
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
    public boolean validateAndActivate(String imei, String token) {
        try {
            if (imei == null) {
                Log.e(TAG, "IMEI устройства равен null");
                return false;
            }

            if (token == null || token.trim().isEmpty()) {
                Log.e(TAG, "Токен пустой или null");
                return false;
            }

            // Декодируем токен
            byte[] decodedBytes = Base64.decode(token.trim(), Base64.URL_SAFE);
            String decodedJson = new String(decodedBytes);
            Log.d(TAG, "Декодированный JSON: " + decodedJson);
            
            JSONObject tokenData = new JSONObject(decodedJson);
            String tokenImei = tokenData.optString("imei", null);
            String tokenUuid = tokenData.optString("uuid", null);
            String startDateStr = tokenData.optString("start_date", null);
            long durationSeconds = tokenData.optLong("duration_seconds", -1);

            // Проверяем наличие всех необходимых данных
            if (tokenImei == null || tokenUuid == null || startDateStr == null || durationSeconds == -1) {
                Log.e(TAG, "Недостаточно данных в токене");
                return false;
            }
            
            // Проверяем IMEI
            if (!tokenImei.equals(imei)) {
                Log.w(TAG, "IMEI не совпадают: устройство=" + imei + ", токен=" + tokenImei);
                return false;
            }
            
            // ИСПРАВЛЕНИЕ: Используем новый метод парсинга дат
            Date startDate = parseISODateFixed(startDateStr);
            if (startDate == null) {
                Log.e(TAG, "Не удалось распарсить дату начала: " + startDateStr);
                return false;
            }
            
            Date expirationDate = new Date(startDate.getTime() + (durationSeconds * 1000L));
            Date currentDate = new Date();
            
            Log.d(TAG, "Текущее время: " + currentDate);
            Log.d(TAG, "Время начала: " + startDate);
            Log.d(TAG, "Время окончания: " + expirationDate);
            
            // ИСПРАВЛЕНИЕ: Добавляем небольшой буфер времени (30 секунд) для компенсации сетевых задержек
            long timeBuffer = 30 * 1000L; // 30 секунд
            Date startDateWithBuffer = new Date(startDate.getTime() - timeBuffer);
            
            if (currentDate.before(startDateWithBuffer)) {
                Log.w(TAG, "Токен еще не действителен");
                return false;
            }
            
            if (currentDate.after(expirationDate)) {
                Log.w(TAG, "Токен истек");
                return false;
            }
            
            saveActivation(imei, tokenUuid, currentDate.getTime(), expirationDate.getTime());
            Log.i(TAG, "Активация успешна: imei=" + imei + ", uuid=" + tokenUuid);
            return true;
            
        } catch (Exception e) {
            Log.e(TAG, "Ошибка валидации токена: " + e.getMessage(), e);
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
        
        return System.currentTimeMillis() > expirationTime;
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
    }

    /**
     * Сохраняет данные активации
     */
    private void saveActivation(String imei, String tokenUuid, long activationTime, long expirationTime) {
        preferences.edit()
                .putBoolean(KEY_IS_ACTIVATED, true)
                .putLong(KEY_ACTIVATION_TIME, activationTime)
                .putLong(KEY_EXPIRATION_TIME, expirationTime)
                .putString(KEY_DEVICE_IMEI, imei)
                .putString(KEY_TOKEN_UUID, tokenUuid)
                .apply();
        Log.i(TAG, "Данные активации сохранены: imei=" + imei + ", uuid=" + tokenUuid);
    }

    /**
     * ИСПРАВЛЕННЫЙ метод парсинга дат с правильной обработкой UTC
     */
    private Date parseISODateFixed(String dateStr) {
        if (dateStr == null || dateStr.trim().isEmpty()) {
            Log.e(TAG, "Пустая строка даты");
            return null;
        }

        Log.d(TAG, "Парсинг даты: " + dateStr);
        
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
                return result;
            } catch (ParseException e) {
                // Продолжаем со следующим форматом
                continue;
            }
        }
        
        Log.e(TAG, "Не удалось распарсить дату ни в одном из форматов: " + dateStr);
        return null;
    }

    /**
     * Получает IMEI устройства для отладки
     */
    public String getDebugDeviceImei() {
        String imei = Utils.getDeviceIMEI(context);
        Log.d(TAG, "Получен IMEI для отладки: " + (imei != null ? imei : "null"));
        return imei;
    }

    /**
     * УЛУЧШЕННАЯ версия отладки токена
     */
    public String validateTokenDebug(String token) {
        StringBuilder debug = new StringBuilder();
        debug.append("=== ОТЛАДКА ТОКЕНА ===\n");
        
        try {
            String deviceImei = Utils.getDeviceIMEI(context);
            debug.append("IMEI устройства: ").append(deviceImei != null ? deviceImei : "NULL").append("\n");
            
            if (token == null || token.trim().isEmpty()) {
                debug.append("ОШИБКА: Токен пустой\n");
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
            } catch (Exception e) {
                debug.append("❌ Ошибка Base64: ").append(e.getMessage()).append("\n");
                return debug.toString();
            }
            
            // Парсим JSON
            JSONObject tokenData;
            try {
                tokenData = new JSONObject(decodedJson);
                debug.append("✓ JSON валиден\n");
            } catch (Exception e) {
                debug.append("❌ Ошибка JSON: ").append(e.getMessage()).append("\n");
                return debug.toString();
            }
            
            // Извлекаем данные
            String tokenImei = tokenData.optString("imei", "НЕТ");
            String tokenUuid = tokenData.optString("uuid", "НЕТ");
            String startDateStr = tokenData.optString("start_date", "НЕТ");
            long durationSeconds = tokenData.optLong("duration_seconds", -1);
            
            debug.append("Токен IMEI: ").append(tokenImei).append("\n");
            debug.append("UUID: ").append(tokenUuid).append("\n");
            debug.append("Дата начала: ").append(startDateStr).append("\n");
            debug.append("Длительность: ").append(durationSeconds).append(" сек\n");
            
            // Проверяем IMEI
            if (deviceImei != null && tokenImei.equals(deviceImei)) {
                debug.append("✓ IMEI совпадают\n");
            } else {
                debug.append("❌ IMEI НЕ совпадают\n");
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
                
                if (currentDate.before(startDate)) {
                    debug.append("❌ Токен еще не активен\n");
                } else if (currentDate.after(expirationDate)) {
                    debug.append("❌ Токен истек\n");
                } else {
                    debug.append("✓ Токен действителен по времени\n");
                }
            } else {
                debug.append("❌ Не удалось распарсить дату\n");
            }
            
        } catch (Exception e) {
            debug.append("КРИТИЧЕСКАЯ ОШИБКА: ").append(e.getMessage()).append("\n");
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
        String imei = preferences.getString(KEY_DEVICE_IMEI, "");
        String uuid = preferences.getString(KEY_TOKEN_UUID, "");
        
        SimpleDateFormat sdf = new SimpleDateFormat("dd.MM.yyyy HH:mm:ss", Locale.getDefault());
        
        return String.format(
            "Активировано: %s\nИстекает: %s\nIMEI: %s\nUUID: %s",
            sdf.format(new Date(activationTime)),
            sdf.format(new Date(expirationTime)),
            imei,
            uuid
        );
    }
}