package com.notiflogger.app;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Build;
import android.provider.Settings;
import android.util.Base64;
import android.util.Log;
import org.json.JSONObject;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class ActivationManager {
    private static final String PREFS_NAME = "activation_data";
    private static final String KEY_IS_ACTIVATED = "is_activated";
    private static final String KEY_ACTIVATION_TIME = "activation_time";
    private static final String KEY_EXPIRATION_TIME = "expiration_time";
    private static final String KEY_DEVICE_ID = "device_id";
    private static final String KEY_TOKEN_CREATION_DATE = "token_creation_date";
    private static final String TAG = "ActivationManager";
    private final SharedPreferences preferences;
    private final Context context;
    private static String cachedDeviceId = null;

    public ActivationManager(Context context) {
        this.context = context;
        this.preferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    public void deactivate() {
        clearActivation();
    }

    public boolean validateAndActivate(String token) {
        try {
            String deviceId = getDeviceUniqueId();
            if (deviceId == null) {
                Log.e(TAG, "Device unique ID is null");
                writeToDebugFile("Error: Device unique ID is null");
                return false;
            }
            if (token == null || token.trim().isEmpty()) {
                Log.e(TAG, "Token is empty or null");
                writeToDebugFile("Error: Token is empty or null");
                return false;
            }
            byte[] decodedBytes = Base64.decode(token.trim(), Base64.URL_SAFE);
            String decodedJson = new String(decodedBytes);
            Log.d(TAG, "Decoded JSON: " + decodedJson);
            writeToDebugFile("Decoded JSON: " + decodedJson);
            JSONObject tokenData = new JSONObject(decodedJson);
            String tokenDeviceId = tokenData.optString("device_id", null);
            String creationDateStr = tokenData.optString("start_date", null);
            long durationSeconds = tokenData.optLong("duration_seconds", -1);
            if (tokenDeviceId == null || creationDateStr == null || durationSeconds == -1) {
                Log.e(TAG, "Missing token data");
                writeToDebugFile("Error: Missing token data");
                return false;
            }
            if (!tokenDeviceId.equals(deviceId)) {
                Log.w(TAG, "Device IDs do not match: device=" + deviceId + ", token=" + tokenDeviceId);
                writeToDebugFile("Warning: Device IDs do not match: device=" + deviceId + ", token=" + tokenDeviceId);
                return false;
            }
            Date currentDate = new Date();
            Date expirationDate = new Date(currentDate.getTime() + (durationSeconds * 1000L));
            Log.d(TAG, "Current time: " + currentDate);
            Log.d(TAG, "Token creation time: " + creationDateStr);
            Log.d(TAG, "Expiration time: " + expirationDate);
            writeToDebugFile("Current time: " + currentDate + "\nToken creation time: " + creationDateStr + "\nExpiration time: " + expirationDate);
            saveActivation(deviceId, currentDate.getTime(), expirationDate.getTime(), creationDateStr);
            Log.i(TAG, "Activation successful: deviceId=" + deviceId);
            writeToDebugFile("Success: Activation successful: deviceId=" + deviceId);
            return true;
        } catch (Exception e) {
            Log.e(TAG, "Token validation error: " + e.getMessage(), e);
            writeToDebugFile("Token validation error: " + e.getMessage());
            return false;
        }
    }

    public boolean isActivated() {
        boolean activated = preferences.getBoolean(KEY_IS_ACTIVATED, false);
        if (activated && isActivationExpired()) {
            Log.w(TAG, "Activation expired, clearing data");
            writeToDebugFile("Warning: Activation expired, clearing data");
            clearActivation();
            return false;
        }
        return activated;
    }

    public boolean isActivationExpired() {
        long expirationTime = preferences.getLong(KEY_EXPIRATION_TIME, 0);
        if (expirationTime == 0) return false;
        boolean expired = System.currentTimeMillis() > expirationTime;
        if (expired) writeToDebugFile("Expiration check: Yes, expired (current time > " + expirationTime + ")");
        return expired;
    }

    public long getExpirationTime() {
        return preferences.getLong(KEY_EXPIRATION_TIME, 0);
    }

    public long getActivationTime() {
        return preferences.getLong(KEY_ACTIVATION_TIME, 0);
    }

    public String getTokenCreationDate() {
        return preferences.getString(KEY_TOKEN_CREATION_DATE, "");
    }

    public void clearActivation() {
        preferences.edit()
                .putBoolean(KEY_IS_ACTIVATED, false)
                .putLong(KEY_ACTIVATION_TIME, 0)
                .putLong(KEY_EXPIRATION_TIME, 0)
                .putString(KEY_DEVICE_ID, "")
                .putString(KEY_TOKEN_CREATION_DATE, "")
                .apply();
        Log.i(TAG, "Activation data cleared");
        writeToDebugFile("Info: Activation data cleared");
    }

    public String getDeviceUniqueId() {
        if (cachedDeviceId != null) {
            Log.d(TAG, "Returning cached Device unique ID: " + cachedDeviceId);
            writeToDebugFile("Info: Returning cached Device unique ID: " + cachedDeviceId + " (Android version: " + Build.VERSION.SDK_INT + ")");
            return cachedDeviceId;
        }
        String deviceId = Settings.Secure.getString(context.getContentResolver(), Settings.Secure.ANDROID_ID);
        cachedDeviceId = deviceId;
        Log.d(TAG, "Device unique ID: " + (deviceId != null ? deviceId : "null"));
        writeToDebugFile("Info: Device unique ID: " + (deviceId != null ? deviceId : "null") + " (Android version: " + Build.VERSION.SDK_INT + ")");
        return deviceId;
    }

    public String validateTokenDebug(String token) {
        StringBuilder debug = new StringBuilder();
        debug.append("=== TOKEN DEBUG ===\n");
        try {
            String deviceId = getDeviceUniqueId();
            debug.append("Device unique ID: ").append(deviceId != null ? deviceId : "NULL").append("\n");
            writeToDebugFile("Debug: Device unique ID: " + (deviceId != null ? deviceId : "NULL"));
            if (token == null || token.trim().isEmpty()) {
                debug.append("ERROR: Token is empty\n");
                writeToDebugFile("Debug error: Token is empty");
                return debug.toString();
            }
            token = token.trim();
            debug.append("Token length: ").append(token.length()).append("\n");
            byte[] decodedBytes;
            String decodedJson;
            try {
                decodedBytes = Base64.decode(token, Base64.URL_SAFE);
                decodedJson = new String(decodedBytes);
                debug.append("✓ Base64 decoding successful\n");
                debug.append("JSON: ").append(decodedJson).append("\n");
                writeToDebugFile("Debug: ✓ Base64 decoding successful, JSON: " + decodedJson);
            } catch (Exception e) {
                debug.append("❌ Base64 error: ").append(e.getMessage()).append("\n");
                writeToDebugFile("Debug Base64 error: " + e.getMessage());
                return debug.toString();
            }
            JSONObject tokenData;
            try {
                tokenData = new JSONObject(decodedJson);
                debug.append("✓ JSON is valid\n");
                writeToDebugFile("Debug: ✓ JSON is valid");
            } catch (Exception e) {
                debug.append("❌ JSON error: ").append(e.getMessage()).append("\n");
                writeToDebugFile("Debug JSON error: " + e.getMessage());
                return debug.toString();
            }
            String tokenDeviceId = tokenData.optString("device_id", "NONE");
            String creationDateStr = tokenData.optString("start_date", "NONE");
            long durationSeconds = tokenData.optLong("duration_seconds", -1);
            debug.append("Token Device ID: ").append(tokenDeviceId).append("\n");
            debug.append("Token creation date: ").append(creationDateStr).append("\n");
            debug.append("Duration: ").append(durationSeconds).append(" sec\n");
            if (deviceId != null && tokenDeviceId.equals(deviceId)) {
                debug.append("✓ Device IDs match\n");
                writeToDebugFile("Debug: ✓ Device IDs match");
            } else {
                debug.append("❌ Device IDs do not match\n");
                writeToDebugFile("Debug: ❌ Device IDs do not match");
            }
            debug.append("✓ Token valid (activation starts at current time)\n");
            Date currentDate = new Date();
            Date expirationDate = new Date(currentDate.getTime() + (durationSeconds * 1000L));
            debug.append("Current: ").append(currentDate).append("\n");
            debug.append("Token creation: ").append(creationDateStr).append("\n");
            debug.append("Expiration (if activated now): ").append(expirationDate).append("\n");
            writeToDebugFile("Debug: ✓ Token valid, Current: " + currentDate + ", Creation: " + creationDateStr + ", Expiration: " + expirationDate);
        } catch (Exception e) {
            debug.append("CRITICAL ERROR: ").append(e.getMessage()).append("\n");
            writeToDebugFile("Debug critical error: " + e.getMessage());
        }
        debug.append("=== END DEBUG ===\n");
        return debug.toString();
    }

    public String getActivationInfo() {
        if (!isActivated()) return "Application not activated";
        long activationTime = getActivationTime();
        long expirationTime = getExpirationTime();
        String deviceId = preferences.getString(KEY_DEVICE_ID, "");
        String creationDate = preferences.getString(KEY_TOKEN_CREATION_DATE, "");
        SimpleDateFormat sdf = new SimpleDateFormat("dd.MM.yyyy HH:mm:ss", Locale.getDefault());
        return String.format(
            "Activated: %s\nExpires: %s\nToken created: %s\nDevice ID: %s",
            sdf.format(new Date(activationTime)),
            sdf.format(new Date(expirationTime)),
            creationDate,
            deviceId
        );
    }

    private void saveActivation(String deviceId, long activationTime, long expirationTime, String creationDate) {
        preferences.edit()
                .putBoolean(KEY_IS_ACTIVATED, true)
                .putLong(KEY_ACTIVATION_TIME, activationTime)
                .putLong(KEY_EXPIRATION_TIME, expirationTime)
                .putString(KEY_DEVICE_ID, deviceId)
                .putString(KEY_TOKEN_CREATION_DATE, creationDate)
                .apply();
        Log.i(TAG, "Activation data saved: deviceId=" + deviceId + ", creationDate=" + creationDate);
        writeToDebugFile("Info: Activation data saved: deviceId=" + deviceId + ", creationDate=" + creationDate);
    }

    private void writeToDebugFile(String message) {
        File logFile = new File(context.getExternalFilesDir(null), "notification_logs/debug_activation.log");
        FileWriter writer = null;
        try {
            if (!logFile.getParentFile().exists()) logFile.getParentFile().mkdirs();
            if (!logFile.exists()) logFile.createNewFile();
            writer = new FileWriter(logFile, true);
            SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US);
            String timestamp = sdf.format(new Date());
            writer.write("[" + timestamp + "] " + message + "\n");
        } catch (IOException e) {
            Log.e(TAG, "Error writing to debug file: " + e.getMessage(), e);
        } finally {
            if (writer != null) {
                try {
                    writer.close();
                } catch (IOException e) {}
            }
        }
    }
}