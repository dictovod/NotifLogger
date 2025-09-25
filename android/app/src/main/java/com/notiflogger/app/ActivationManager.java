package com.notiflogger.app;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Base64;

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
    
    private final SharedPreferences preferences;
    private final Context context;

    public ActivationManager(Context context) {
        this.context = context;
        this.preferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    /**
     * Проверяет и активирует приложение с помощью токена
     * Реализует ту же логику, что и Python скрипт для генерации токенов
     */
    public boolean validateAndActivate(String imei, String token) {
        try {
            // Декодируем токен из Base64 (как в Python скрипте)
            byte[] decodedBytes = Base64.decode(token, Base64.URL_SAFE);
            String decodedJson = new String(decodedBytes);
            
            // Парсим JSON данные токена
            JSONObject tokenData = new JSONObject(decodedJson);
            
            // Извлекаем данные токена
            String tokenImei = tokenData.getString("imei");
            String tokenUuid = tokenData.getString("uuid");
            String startDateStr = tokenData.getString("start_date");
            long durationSeconds = tokenData.getLong("duration_seconds");
            
            // Проверяем соответствие IMEI
            if (!tokenImei.equals(imei)) {
                return false;
            }
            
            // Парсим дату начала действия токена
            Date startDate = parseISODate(startDateStr);
            if (startDate == null) {
                return false;
            }
            
            // Вычисляем дату окончания действия
            Date expirationDate = new Date(startDate.getTime() + (durationSeconds * 1000));
            Date currentDate = new Date();
            
            // Проверяем срок действия токена
            if (currentDate.before(startDate) || currentDate.after(expirationDate)) {
                return false;
            }
            
            // Токен валиден - сохраняем активацию
            saveActivation(imei, tokenUuid, currentDate.getTime(), expirationDate.getTime());
            return true;
            
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    /**
     * Проверяет, активировано ли приложение
     */
    public boolean isActivated() {
        boolean activated = preferences.getBoolean(KEY_IS_ACTIVATED, false);
        
        // Если активировано, проверяем срок действия
        if (activated && isActivationExpired()) {
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
    }

    /**
     * Парсит дату в формате ISO (как в Python скрипте)
     */
    private Date parseISODate(String dateStr) {
        try {
            // Поддержка полного формата ISO 8601 (например, "2025-09-25T10:29:03.477360")
            SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSSSS", Locale.US);
            sdf.setTimeZone(TimeZone.getTimeZone("UTC")); // Учитываем UTC, как в примере
            return sdf.parse(dateStr);
        } catch (ParseException e) {
            e.printStackTrace();
            // Попытка парсинга без микросекунд как запасной вариант
            try {
                SimpleDateFormat sdfFallback = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US);
                sdfFallback.setTimeZone(TimeZone.getTimeZone("UTC"));
                return sdfFallback.parse(dateStr);
            } catch (ParseException ex) {
                ex.printStackTrace();
                return null;
            }
        }
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
        
        java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat("dd.MM.yyyy HH:mm");
        
        return String.format(
            "Активировано: %s\nИстекает: %s\nIMEI: %s",
            sdf.format(new Date(activationTime)),
            sdf.format(new Date(expirationTime)),
            imei
        );
    }
}