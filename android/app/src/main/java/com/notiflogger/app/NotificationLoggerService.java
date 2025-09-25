package com.notiflogger.app;

import android.app.Notification;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.os.IBinder;
import android.service.notification.NotificationListenerService;
import android.service.notification.StatusBarNotification;
import android.util.Log;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * Сервис для перехвата и логирования системных уведомлений
 * Работает только при активированном приложении
 */
public class NotificationLoggerService extends NotificationListenerService {
    
    private static final String TAG = "NotifLoggerService";
    private ActivationManager activationManager;
    private File logFile;
    private SimpleDateFormat dateFormat;

    @Override
    public void onCreate() {
        super.onCreate();
        
        activationManager = new ActivationManager(this);
        dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault());
        
        // Создаем файл для логов
        createLogFile();
        
        Log.d(TAG, "NotificationLoggerService created");
    }

    @Override
    public void onNotificationPosted(StatusBarNotification sbn) {
        // Проверяем активацию перед логированием
        if (!activationManager.isActivated()) {
            return;
        }
        
        try {
            logNotification(sbn, "POSTED");
        } catch (Exception e) {
            Log.e(TAG, "Error logging notification", e);
        }
    }

    @Override
    public void onNotificationRemoved(StatusBarNotification sbn) {
        // Проверяем активацию перед логированием
        if (!activationManager.isActivated()) {
            return;
        }
        
        try {
            logNotification(sbn, "REMOVED");
        } catch (Exception e) {
            Log.e(TAG, "Error logging notification removal", e);
        }
    }

    /**
     * Логирует уведомление в JSON формате
     */
    private void logNotification(StatusBarNotification sbn, String action) {
        try {
            Notification notification = sbn.getNotification();
            String packageName = sbn.getPackageName();
            
            // Создаем JSON объект с данными уведомления
            JSONObject logEntry = new JSONObject();
            
            // Основная информация
            logEntry.put("timestamp", dateFormat.format(new Date()));
            logEntry.put("action", action);
            logEntry.put("package_name", packageName);
            logEntry.put("app_name", getAppName(packageName));
            logEntry.put("post_time", dateFormat.format(new Date(sbn.getPostTime())));
            logEntry.put("id", sbn.getId());
            logEntry.put("tag", sbn.getTag() != null ? sbn.getTag() : "");
            logEntry.put("key", sbn.getKey());
            
            // Данные уведомления
            if (notification != null) {
                logEntry.put("ticker", getNotificationText(notification.tickerText));
                
                // Извлекаем title и text из extras
                if (notification.extras != null) {
                    logEntry.put("title", getNotificationText(notification.extras.getCharSequence(Notification.EXTRA_TITLE)));
                    logEntry.put("text", getNotificationText(notification.extras.getCharSequence(Notification.EXTRA_TEXT)));
                    logEntry.put("big_text", getNotificationText(notification.extras.getCharSequence(Notification.EXTRA_BIG_TEXT)));
                    logEntry.put("sub_text", getNotificationText(notification.extras.getCharSequence(Notification.EXTRA_SUB_TEXT)));
                    logEntry.put("info_text", getNotificationText(notification.extras.getCharSequence(Notification.EXTRA_INFO_TEXT)));
                }
                
                // Приоритет и категория
                logEntry.put("priority", notification.priority);
                logEntry.put("category", notification.category != null ? notification.category : "");
                
                // Флаги
                logEntry.put("flags", notification.flags);
                logEntry.put("ongoing", (notification.flags & Notification.FLAG_ONGOING_EVENT) != 0);
                logEntry.put("auto_cancel", (notification.flags & Notification.FLAG_AUTO_CANCEL) != 0);
            }
            
            // Информация о пользователе (для многопользовательских устройств)
            logEntry.put("user_id", sbn.getUserId());
            
            // Записываем в файл
            writeLogEntry(logEntry.toString());
            
            Log.d(TAG, "Logged notification: " + packageName + " - " + action);
            
        } catch (JSONException e) {
            Log.e(TAG, "Error creating JSON log entry", e);
        }
    }

    /**
     * Получает название приложения по имени пакета
     */
    private String getAppName(String packageName) {
        try {
            PackageManager pm = getPackageManager();
            ApplicationInfo appInfo = pm.getApplicationInfo(packageName, 0);
            return pm.getApplicationLabel(appInfo).toString();
        } catch (PackageManager.NameNotFoundException e) {
            return packageName; // Возвращаем имя пакета, если не удалось получить название
        }
    }

    /**
     * Безопасно извлекает текст из CharSequence
     */
    private String getNotificationText(CharSequence text) {
        return text != null ? text.toString() : "";
    }

    /**
     * Создает файл для логов
     */
    private void createLogFile() {
        try {
            File logDir = new File(getExternalFilesDir(null), "notification_logs");
            if (!logDir.exists()) {
                logDir.mkdirs();
            }
            
            String fileName = "notifications_" + 
                new SimpleDateFormat("yyyy_MM_dd", Locale.getDefault()).format(new Date()) + ".json";
            
            logFile = new File(logDir, fileName);
            
            if (!logFile.exists()) {
                logFile.createNewFile();
                
                // Записываем заголовок файла
                JSONObject header = new JSONObject();
                header.put("log_start", dateFormat.format(new Date()));
                header.put("device_info", Utils.getDeviceInfo());
                header.put("app_version", "1.0.0");
                
                writeLogEntry("=== LOG START ===");
                writeLogEntry(header.toString());
                writeLogEntry("=== NOTIFICATIONS ===");
            }
            
        } catch (Exception e) {
            Log.e(TAG, "Error creating log file", e);
        }
    }

    /**
     * Записывает запись в лог файл
     */
    private synchronized void writeLogEntry(String entry) {
        try {
            if (logFile != null && logFile.exists()) {
                FileWriter writer = new FileWriter(logFile, true);
                writer.write(entry + "\n");
                writer.flush();
                writer.close();
            }
        } catch (IOException e) {
            Log.e(TAG, "Error writing to log file", e);
        }
    }

    @Override
    public IBinder onBind(Intent intent) {
        return super.onBind(intent);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        
        try {
            // Записываем окончание сессии
            JSONObject footer = new JSONObject();
            footer.put("log_end", dateFormat.format(new Date()));
            
            writeLogEntry("=== LOG END ===");
            writeLogEntry(footer.toString());
            
        } catch (JSONException e) {
            Log.e(TAG, "Error writing log footer", e);
        }
        
        Log.d(TAG, "NotificationLoggerService destroyed");
    }

    /**
     * Получает статистику логирования
     */
    public String getLoggingStats() {
        if (logFile == null || !logFile.exists()) {
            return "Лог файл не найден";
        }
        
        long fileSize = logFile.length();
        String lastModified = dateFormat.format(new Date(logFile.lastModified()));
        
        return String.format(
            "Файл лога: %s\nРазмер: %s\nОбновлен: %s",
            logFile.getName(),
            Utils.formatFileSize(fileSize),
            lastModified
        );
    }
}