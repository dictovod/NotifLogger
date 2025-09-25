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
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;

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

    public void activate(String uuid, String startDate, int durationSeconds) {
        try {
            Date startDateParsed = parseISODateFixed(startDate);
            if (startDateParsed == null) {
                Log.e(TAG, "Failed to parse start date: " + startDate);
                writeToDebugFile("Error: Failed to parse start date: " + startDate);
                return;
            }
            Date currentDate = new Date();
            Date expirationDate = new Date(startDateParsed.getTime() + (durationSeconds * 1000L));
            if (currentDate.before(startDateParsed) || currentDate.after(expirationDate)) {
                Log.w(TAG, "Date out of range: current=" + currentDate + ", start=" + startDateParsed + ", end=" + expirationDate);
                writeToDebugFile("Warning: Date out of range: current=" + currentDate + ", start=" + startDateParsed + ", end=" + expirationDate);
                return;
            }
            String deviceId = getDeviceUniqueId();
            if (deviceId == null) {
                Log.e(TAG, "Failed to get device unique ID");
                writeToDebugFile("Error: Failed to get device unique ID");
                return;
            }
            saveActivation(deviceId, uuid, currentDate.getTime(), expirationDate.getTime());
        } catch (Exception e) {
            Log.e(TAG, "Activation error: " + e.getMessage(), e);
            writeToDebugFile("Activation error: " + e.getMessage());
        }
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
            String tokenDeviceId = tokenData.optString("imei", null);
            String tokenUuid = tokenData.optString("uuid", null);
            String startDateStr = tokenData.optString("start_date", null);
            long durationSeconds = tokenData.optLong("duration_seconds", -1);
            if (tokenDeviceId == null || tokenUuid == null || startDateStr == null || durationSeconds == -1) {
                Log.e(TAG, "Missing token data");
                writeToDebugFile("Error: Missing token data");
                return false;
            }
            if (!tokenDeviceId.equals(deviceId)) {
                Log.w(TAG, "Device IDs do not match: device=" + deviceId + ", token=" + tokenDeviceId);
                writeToDebugFile("Warning: Device IDs do not match: device=" + deviceId + ", token=" + tokenDeviceId);
                return false;
            }
            Date startDate = parseISODateFixed(startDateStr);
            if (startDate == null) {
                Log.e(TAG, "Failed to parse start date: " + startDateStr);
                writeToDebugFile("Error: Failed to parse start date: " + startDateStr);
                return false;
            }
            Date expirationDate = new Date(startDate.getTime() + (durationSeconds * 1000L));
            Date currentDate = new Date();
            Log.d(TAG, "Current time: " + currentDate);
            Log.d(TAG, "Start time: " + startDate);
            Log.d(TAG, "Expiration time: " + expirationDate);
            writeToDebugFile("Current time: " + currentDate + "\nStart time: " + startDate + "\nExpiration time: " + expirationDate);
            long timeBuffer = 30 * 1000L;
            Date startDateWithBuffer = new Date(startDate.getTime() - timeBuffer);
            if (currentDate.before(startDateWithBuffer)) {
                Log.w(TAG, "Token not yet valid");
                writeToDebugFile("Warning: Token not yet valid (current time before start with buffer)");
                return false;
            }
            if (currentDate.after(expirationDate)) {
                Log.w(TAG, "Token expired");
                writeToDebugFile("Warning: Token expired (current time after expiration)");
                return false;
            }
            saveActivation(deviceId, tokenUuid, currentDate.getTime(), expirationDate.getTime());
            Log.i(TAG, "Activation successful: deviceId=" + deviceId + ", uuid=" + tokenUuid);
            writeToDebugFile("Success: Activation successful: deviceId=" + deviceId + ", uuid=" + tokenUuid);
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

    public void clearActivation() {
        preferences.edit()
                .putBoolean(KEY_IS_ACTIVATED, false)
                .putLong(KEY_ACTIVATION_TIME, 0)
                .putLong(KEY_EXPIRATION_TIME, 0)
                .putString(KEY_DEVICE_IMEI, "")
                .putString(KEY_TOKEN_UUID, "")
                .apply();
        Log.i(TAG, "Activation data cleared");
        writeToDebugFile("Info: Activation data cleared");
    }

    private void saveActivation(String deviceId, String tokenUuid, long activationTime, long expirationTime) {
        preferences.edit()
                .putBoolean(KEY_IS_ACTIVATED, true)
                .putLong(KEY_ACTIVATION_TIME, activationTime)
                .putLong(KEY_EXPIRATION_TIME, expirationTime)
                .putString(KEY_DEVICE_IMEI, deviceId)
                .putString(KEY_TOKEN_UUID, tokenUuid)
                .apply();
        Log.i(TAG, "Activation data saved: deviceId=" + deviceId + ", uuid=" + tokenUuid);
        writeToDebugFile("Info: Activation data saved: deviceId=" + deviceId + ", uuid=" + tokenUuid);
    }

    private Date parseISODateFixed(String dateStr) {
        if (dateStr == null || dateStr.trim().isEmpty()) {
            Log.e(TAG, "Empty date string");
            writeToDebugFile("Error: Empty date string");
            return null;
        }
        Log.d(TAG, "Parsing date: " + dateStr);
        writeToDebugFile("Parsing date: " + dateStr);
        SimpleDateFormat[] formats = {
                new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US),
                new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US),
                new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSSSS'Z'", Locale.US),
                new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US),
                new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS", Locale.US),
                new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSSSS", Locale.US)
        };
        for (SimpleDateFormat format : formats) {
            try {
                format.setTimeZone(TimeZone.getTimeZone("UTC"));
                Date result = format.parse(dateStr);
                Log.d(TAG, "Date parsed successfully as UTC: " + result);
                writeToDebugFile("Success: Date parsed as UTC: " + result + " (format: " + format.toPattern() + ")");
                return result;
            } catch (ParseException e) {
                writeToDebugFile("Parse attempt failed for format: " + format.toPattern() + " - " + e.getMessage());
            }
        }
        Log.e(TAG, "Failed to parse date in any format: " + dateStr);
        writeToDebugFile("Error: Failed to parse date in any format: " + dateStr);
        return null;
    }

    public String getDeviceUniqueId() {
        String deviceId = Settings.Secure.getString(context.getContentResolver(), Settings.Secure.ANDROID_ID);
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
            String tokenDeviceId = tokenData.optString("imei", "NONE");
            String tokenUuid = tokenData.optString("uuid", "NONE");
            String startDateStr = tokenData.optString("start_date", "NONE");
            long durationSeconds = tokenData.optLong("duration_seconds", -1);
            debug.append("Token Device ID: ").append(tokenDeviceId).append("\n");
            debug.append("UUID: ").append(tokenUuid).append("\n");
            debug.append("Start date: ").append(startDateStr).append("\n");
            debug.append("Duration: ").append(durationSeconds).append(" sec\n");
            if (deviceId != null && tokenDeviceId.equals(deviceId)) {
                debug.append("✓ Device IDs match\n");
                writeToDebugFile("Debug: ✓ Device IDs match");
            } else {
                debug.append("❌ Device IDs do not match\n");
                writeToDebugFile("Debug: ❌ Device IDs do not match");
            }
            Date startDate = parseISODateFixed(startDateStr);
            if (startDate != null) {
                Date currentDate = new Date();
                Date expirationDate = new Date(startDate.getTime() + (durationSeconds * 1000L));
                debug.append("✓ Date parsed\n");
                debug.append("Current: ").append(currentDate).append("\n");
                debug.append("Start: ").append(startDate).append("\n");
                debug.append("End: ").append(expirationDate).append("\n");
                writeToDebugFile("Debug: ✓ Date parsed, Current: " + currentDate + ", Start: " + startDate + ", End: " + expirationDate);
                if (currentDate.before(startDate)) {
                    debug.append("❌ Token not yet active\n");
                    writeToDebugFile("Debug: ❌ Token not yet active");
                } else if (currentDate.after(expirationDate)) {
                    debug.append("❌ Token expired\n");
                    writeToDebugFile("Debug: ❌ Token expired");
                } else {
                    debug.append("✓ Token valid by time\n");
                    writeToDebugFile("Debug: ✓ Token valid by time");
                }
            } else {
                debug.append("❌ Failed to parse date\n");
                writeToDebugFile("Debug: ❌ Failed to parse date");
            }
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
        String deviceId = preferences.getString(KEY_DEVICE_IMEI, "");
        String uuid = preferences.getString(KEY_TOKEN_UUID, "");
        SimpleDateFormat sdf = new SimpleDateFormat("dd.MM.yyyy HH:mm:ss", Locale.getDefault());
        return String.format(
            "Activated: %s\nExpires: %s\nDevice ID: %s\nUUID: %s",
            sdf.format(new Date(activationTime)),
            sdf.format(new Date(expirationTime)),
            deviceId,
            uuid
        );
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