package com.notiflogger.app;

import android.app.Service;
import android.content.Intent;
import android.os.IBinder;
import android.util.Base64;
import android.util.Log;
import org.json.JSONObject;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class NotificationLoggerService extends Service {
    private static final String TAG = "NotificationLoggerService";
    private ActivationManager activationManager;

    @Override
    public void onCreate() {
        super.onCreate();
        activationManager = new ActivationManager(this);
        Log.d(TAG, "NotificationLoggerService created");
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.d(TAG, "NotificationLoggerService started");
        if (intent != null) {
            // Assuming the intent contains uuid, startDate, and durationSeconds
            String uuid = intent.getStringExtra("uuid");
            String startDate = intent.getStringExtra("start_date");
            int durationSeconds = intent.getIntExtra("duration_seconds", -1);

            if (uuid != null && startDate != null && durationSeconds != -1) {
                try {
                    // Construct the token as per the new format
                    JSONObject tokenData = new JSONObject();
                    tokenData.put("device_id", uuid); // Assuming uuid is the device_id
                    tokenData.put("start_date", startDate);
                    tokenData.put("duration_seconds", durationSeconds);
                    String token = Base64.encodeToString(tokenData.toString().getBytes(), Base64.URL_SAFE | Base64.NO_WRAP);

                    // Activate using the new validateAndActivate method
                    boolean success = activationManager.validateAndActivate(token);
                    if (success) {
                        Log.i(TAG, "Activation successful in NotificationLoggerService");
                    } else {
                        Log.e(TAG, "Activation failed in NotificationLoggerService");
                    }
                } catch (Exception e) {
                    Log.e(TAG, "Error constructing token: " + e.getMessage(), e);
                }
            } else {
                Log.w(TAG, "Missing activation parameters in intent");
            }
        }
        // Continue with notification logging logic
        return START_STICKY;
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        Log.d(TAG, "NotificationLoggerService destroyed");
    }
}