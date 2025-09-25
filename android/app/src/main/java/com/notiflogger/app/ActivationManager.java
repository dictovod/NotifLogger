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
 * Менеджер активации приложения
 * Реализует логику проверки токенов на основе Python скрипта генерации
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
            Date startDateParsed = parseISODate(startDate);
            if (startDateParsed == null) {
                Log.e(TAG, "Не удалось распарсить дату начала: " + startDate);
                return;
            }

            Date currentDate = new Date();
            Date expirationDate = new Date(startDateParsed.getTime() + (durationSeconds * 1000L));

            if (currentDate.before(startDateParsed) || currentDate.after(expirationDate)) {
                Log.w(TAG, "Дата вне диапазона: текущая=" + currentDate + ", начало=" + startDateParsed + ", конец=" + expirationDate);
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
     * Проверяет и активирует приложение с помощью токена
     * Реализует ту же логику, что и Python скрипт для генерации токенов
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

            byte[] decodedBytes = Base64.decode(token, Base64.URL_SAFE);
            String decodedJson = new String(decodedBytes);
            Log.d(TAG, "Декодированный JSON: " + decodedJson);
            
            JSONObject tokenData = new JSONObject(decodedJson);
            String tokenImei = tokenData.optString("imei", null);
            String tokenUuid = tokenData.optString("uuid", null);
            String startDateStr = tokenData.optString("start_date", null);
            long durationSeconds = tokenData.optLong("duration_seconds", -1);

            if (tokenImei == null || tokenUuid == null || startDateStr == null || durationSeconds == -1) {
                Log.e(TAG, "Недостаточно данных в токене: imei=" + tokenImei + ", uuid=" + tokenUuid + 
                      ", startDate=" + startDateStr + ", duration=" + durationSeconds);
                return false;
            }
            
            if (!tokenImei.equals(imei)) {
                Log.w(TAG, "IMEI не совпадают: устройство=" + imei + ", токен=" + tokenImei);
                return false;
            }
            
            Date startDate = parseISODate(startDateStr);
            if (startDate == null) {
                Log.e(TAG, "Не удалось распарсить дату начала: " + startDateStr);
                return false;
            }
            
            Date expirationDate = new Date(startDate.getTime() + (durationSeconds * 1000L));
            Date currentDate = new Date();
            
            if (currentDate.before(startDate) || currentDate.after(expirationDate)) {
                Log.w(TAG, "Токен вне временного диапазона: текущая=" + currentDate + 
                      ", начало=" + startDate + ", конец=" + expirationDate);
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
     * Парсит дату в формате ISO (как в Python скрипте)
     * Улучшенная версия с поддержкой разных форматов времени
     */
    private Date parseISODate(String dateStr) {
        Log.d(TAG, "Парсинг даты: " + dateStr);
        
        // Список различных форматов для парсинга
        SimpleDateFormat[] formats = {
            new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSSSS", Locale.US),
            new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US),
            new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS", Locale.US),
            new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US),
            new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US)
        };
        
        for (SimpleDateFormat format : formats) {
            try {
                format.setTimeZone(TimeZone.getTimeZone("UTC"));
                Date result = format.parse(dateStr);
                Log.d(TAG, "Дата успешно распарсена как UTC: " + result);
                return result;
            } catch (ParseException e) {
                try {
                    format.setTimeZone(TimeZone.getDefault());
                    Date result = format.parse(dateStr);
                    Log.d(TAG, "Дата успешно распарсена как локальное время: " + result);
                    return result;
                } catch (ParseException ex) {
                    // Продолжаем со следующим форматом
                }
            }
        }
        
        Log.e(TAG, "Не удалось распарсить дату: " + dateStr);
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
     * Валидирует токен с подробной отладочной информацией (БЕЗ активации)
     * Используйте этот метод для диагностики проблем с токенами
     */
    public String validateTokenDebug(String token) {
        StringBuilder debug = new StringBuilder();
        debug.append("=== ОТЛАДКА ТОКЕНА ===\n");
        
        try {
            String deviceImei = Utils.getDeviceIMEI(context);
            debug.append("IMEI устройства: ").append(deviceImei != null ? deviceImei : "NULL (нет разрешения?)").append("\n");
            
            if (token == null || token.trim().isEmpty()) {
                debug.append("ОШИБКА: Токен пустой или null\n");
                return debug.toString();
            }
            
            debug.append("Длина токена: ").append(token.length()).append("\n");
            debug.append("Токен (первые 50 символов): ").append(token.length() > 50 ? token.substring(0, 50) + "..." : token).append("\n");
            
            // Декодируем Base64
            byte[] decodedBytes;
            try {
                decodedBytes = Base64.decode(token, Base64.URL_SAFE);
                debug.append("✓ Base64 декодирование успешно\n");
            } catch (Exception e) {
                debug.append("ОШИБКА: Некорректный Base64: ").append(e.getMessage()).append("\n");
                return debug.toString();
            }
            
            String decodedJson = new String(decodedBytes);
            debug.append("Декодированный JSON: ").append(decodedJson).append("\n");
            
            // Парсим JSON
            JSONObject tokenData;
            try {
                tokenData = new JSONObject(decodedJson);
                debug.append("✓ JSON парсинг успешен\n");
            } catch (Exception e) {
                debug.append("ОШИБКА: Некорректный JSON: ").append(e.getMessage()).append("\n");
                return debug.toString();
            }
            
            // Получаем данные токена
            String tokenImei = tokenData.optString("imei", "НЕ НАЙДЕН");
            String tokenUuid = tokenData.optString("uuid", "НЕ НАЙДЕН");
            String startDateStr = tokenData.optString("start_date", "НЕ НАЙДЕН");
            long durationSeconds = tokenData.optLong("duration_seconds", -1);
            
            debug.append("Токен IMEI: ").append(tokenImei).append("\n");
            debug.append("Токен UUID: ").append(tokenUuid).append("\n");
            debug.append("Дата начала: ").append(startDateStr).append("\n");
            debug.append("Длительность (сек): ").append(durationSeconds).append("\n");
            
            // Сравниваем IMEI
            if (deviceImei != null) {
                if (tokenImei.equals(deviceImei)) {
                    debug.append("✓ IMEI совпадают\n");
                } else {
                    debug.append("❌ IMEI НЕ совпадают!\n");
                    debug.append("  Устройство: '").append(deviceImei).append("'\n");
                    debug.append("  Токен:     '").append(tokenImei).append("'\n");
                }
            } else {
                debug.append("❌ Не удалось получить IMEI устройства (нет разрешения READ_PHONE_STATE?)\n");
            }
            
            // Парсим и проверяем даты
            Date startDate = parseISODate(startDateStr);
            if (startDate != null) {
                debug.append("✓ Дата начала распарсена: ").append(startDate).append("\n");
                
                Date currentDate = new Date();
                Date expirationDate = new Date(startDate.getTime() + (durationSeconds * 1000L));
                
                debug.append("Текущее время: ").append(currentDate).append("\n");
                debug.append("Время окончания: ").append(expirationDate).append("\n");
                
                if (currentDate.before(startDate)) {
                    long diffMin = (startDate.getTime() - currentDate.getTime()) / (1000 * 60);
                    debug.append("❌ Токен еще не действителен (начнет действовать через ").append(diffMin).append(" мин)\n");
                } else if (currentDate.after(expirationDate)) {
                    long diffMin = (currentDate.getTime() - expirationDate.getTime()) / (1000 * 60);
                    debug.append("❌ Токен истек (").append(diffMin).append(" мин назад)\n");
                } else {
                    debug.append("✓ Временные рамки корректны\n");
                }
                
                // Часовые пояса
                debug.append("Текущий часовой пояс: ").append(TimeZone.getDefault().getID()).append("\n");
                debug.append("Смещение часового пояса: ").append(TimeZone.getDefault().getRawOffset() / (1000 * 60 * 60)).append(" часов\n");
                
            } else {
                debug.append("❌ Не удалось распарсить дату начала: ").append(startDateStr).append("\n");
            }
            
        } catch (Exception e) {
            debug.append("КРИТИЧЕСКАЯ ОШИБКА: ").append(e.getMessage()).append("\n");
            Log.e(TAG, "Критическая ошибка отладки токена: " + e.getMessage(), e);
        }
        
        debug.append("=== КОНЕЦ ОТЛАДКИ ===\n");
        return debug.toString();
    }

    /**
     * Получает информацию о текущей активации для отладки
     */
    public String getActivationInfo() {
        if (!isActivated()) {
            return "Приложение не активировано";
        }
        
        long activationTime = getActivationTime();
        long expirationTime = getExpirationTime();
        String imei = preferences.getString(KEY_DEVICE_IMEI, "");
        
        SimpleDateFormat sdf = new SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.getDefault());
        
        return String.format(
            "Активировано: %s\nИстекает: %s\nIMEI: %s",
            sdf.format(new Date(activationTime)),
            sdf.format(new Date(expirationTime)),
            imei
        );
    }
}