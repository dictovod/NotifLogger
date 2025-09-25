package com.notiflogger.app;

import android.Manifest;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.TextView;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import android.util.Log;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * Активность для просмотра логов уведомлений
 */
public class LogsActivity extends AppCompatActivity {
    private static final String TAG = "LogsActivity";
    private static final int STORAGE_PERMISSION_CODE = 100;
    
    private RecyclerView recyclerView;
    private TextView emptyView;
    private TextView statsView;
    private LogsAdapter logsAdapter;
    private List<LogEntry> logEntries;
    private ActivationManager activationManager;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_logs);
        
        activationManager = new ActivationManager(this);
        
        // Проверяем разрешения на чтение/запись
        if (!checkStoragePermissions()) {
            requestStoragePermissions();
            return;
        }
        
        // Проверяем активацию
        if (!activationManager.isActivated()) {
            Log.w(TAG, "Application not activated, finishing activity");
            Utils.showToast(this, "Приложение не активировано");
            finish();
            return;
        }
        
        initViews();
        loadLogs();
    }

    private boolean checkStoragePermissions() {
        return ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED &&
               ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED;
    }

    private void requestStoragePermissions() {
        ActivityCompat.requestPermissions(this,
                new String[]{Manifest.permission.READ_EXTERNAL_STORAGE, Manifest.permission.WRITE_EXTERNAL_STORAGE},
                STORAGE_PERMISSION_CODE);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == STORAGE_PERMISSION_CODE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                Log.i(TAG, "Storage permissions granted, initializing views and loading logs");
                initViews();
                loadLogs();
            } else {
                Log.w(TAG, "Storage permissions denied, finishing activity");
                Utils.showToast(this, "Требуются разрешения для доступа к логам");
                finish();
            }
        }
    }

    private void initViews() {
        findViewById(R.id.btn_back).setOnClickListener(v -> finish());
        
        recyclerView = findViewById(R.id.recycler_logs);
        emptyView = findViewById(R.id.tv_empty);
        statsView = findViewById(R.id.tv_stats);
        
        logEntries = new ArrayList<>();
        logsAdapter = new LogsAdapter(logEntries);
        
        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        recyclerView.setAdapter(logsAdapter);
    }

    private void loadLogs() {
        new Thread(() -> {
            List<LogEntry> entries = readLogFiles();
            
            runOnUiThread(() -> {
                logEntries.clear();
                logEntries.addAll(entries);
                logsAdapter.notifyDataSetChanged();
                
                updateStats();
                updateEmptyState();
            });
        }).start();
    }

    private List<LogEntry> readLogFiles() {
        List<LogEntry> entries = new ArrayList<>();
        
        try {
            File logDir = new File(getExternalFilesDir(null), "notification_logs");
            if (!logDir.exists()) {
                logDir.mkdirs();
                Log.i(TAG, "Created log directory: " + logDir.getAbsolutePath());
                return entries;
            }
            
            File[] logFiles = logDir.listFiles((dir, name) -> name.endsWith(".json") && !name.equals("debug_activation.log"));
            if (logFiles == null || logFiles.length == 0) {
                Log.w(TAG, "No log files found in: " + logDir.getAbsolutePath());
                return entries;
            }
            
            // Читаем все JSON лог файлы
            for (File logFile : logFiles) {
                Log.d(TAG, "Reading log file: " + logFile.getName());
                readLogFile(logFile, entries);
            }
            
            // Сортируем по времени (новые сверху)
            entries.sort((a, b) -> b.timestamp.compareTo(a.timestamp));
            
            // Ограничиваем количество записей
            if (entries.size() > 1000) {
                entries = entries.subList(0, 1000);
            }
            
        } catch (Exception e) {
            Log.e(TAG, "Error reading log files: " + e.getMessage(), e);
        }
        
        return entries;
    }

    private void readLogFile(File logFile, List<LogEntry> entries) {
        try (BufferedReader reader = new BufferedReader(new FileReader(logFile))) {
            String line;
            
            while ((line = reader.readLine()) != null) {
                line = line.trim();
                
                // Пропускаем служебные строки
                if (line.startsWith("===") || line.isEmpty()) {
                    continue;
                }
                
                try {
                    LogEntry entry = LogEntry.fromJsonLine(line);
                    if (entry != null) {
                        entries.add(entry);
                    } else {
                        Log.w(TAG, "Skipping invalid JSON line in " + logFile.getName() + ": " + line);
                    }
                } catch (Exception e) {
                    Log.w(TAG, "Error parsing JSON line in " + logFile.getName() + ": " + e.getMessage());
                    continue;
                }
            }
            
        } catch (IOException e) {
            Log.e(TAG, "Error reading file " + logFile.getName() + ": " + e.getMessage(), e);
        }
    }

    private void updateStats() {
        int totalCount = logEntries.size();
        
        // Подсчитываем статистику
        long todayCount = logEntries.stream()
                .filter(entry -> isToday(entry.timestamp))
                .count();
        
        String stats = String.format(
                Locale.getDefault(),
                "Всего записей: %d • Сегодня: %d",
                totalCount, todayCount
        );
        
        statsView.setText(stats);
    }

    private boolean isToday(String timestamp) {
        try {
            SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault());
            Date logDate = sdf.parse(timestamp);
            Date today = new Date();
            
            SimpleDateFormat dayFormat = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());
            return dayFormat.format(logDate).equals(dayFormat.format(today));
            
        } catch (Exception e) {
            Log.w(TAG, "Error parsing timestamp: " + timestamp, e);
            return false;
        }
    }

    private void updateEmptyState() {
        if (logEntries.isEmpty()) {
            recyclerView.setVisibility(View.GONE);
            emptyView.setVisibility(View.VISIBLE);
        } else {
            recyclerView.setVisibility(View.VISIBLE);
            emptyView.setVisibility(View.GONE);
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.logs_menu, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();
        
        if (id == R.id.menu_refresh) {
            loadLogs();
            Utils.showToast(this, "Логи обновлены");
            return true;
            
        } else if (id == R.id.menu_clear) {
            showClearLogsDialog();
            return true;
            
        } else if (id == R.id.menu_export) {
            exportLogs();
            return true;
        }
        
        return super.onOptionsItemSelected(item);
    }

    private void showClearLogsDialog() {
        new AlertDialog.Builder(this)
                .setTitle("Очистить логи")
                .setMessage("Удалить все сохранённые логи уведомлений?")
                .setIcon(R.drawable.ic_warning)
                .setPositiveButton("Удалить", (dialog, which) -> clearLogs())
                .setNegativeButton("Отмена", null)
                .show();
    }

    private void clearLogs() {
        new Thread(() -> {
            try {
                File logDir = new File(getExternalFilesDir(null), "notification_logs");
                if (logDir.exists()) {
                    File[] files = logDir.listFiles((dir, name) -> name.endsWith(".json") && !name.equals("debug_activation.log"));
                    if (files != null) {
                        for (File file : files) {
                            file.delete();
                        }
                    }
                }
                
                runOnUiThread(() -> {
                    logEntries.clear();
                    logsAdapter.notifyDataSetChanged();
                    updateStats();
                    updateEmptyState();
                    Utils.showToast(this, "Логи очищены");
                });
                
            } catch (Exception e) {
                Log.e(TAG, "Error clearing logs: " + e.getMessage(), e);
                runOnUiThread(() -> {
                    Utils.showToast(this, "Ошибка при очистке логов");
                });
            }
        }).start();
    }

    private void exportLogs() {
        Utils.showToast(this, "Функция экспорта в разработке");
    }

    /**
     * Класс для представления записи лога
     */
    public static class LogEntry {
        public String timestamp;
        public String action;
        public String packageName;
        public String appName;
        public String title;
        public String text;
        public boolean ongoing;
        
        public static LogEntry fromJsonLine(String jsonLine) {
            try {
                org.json.JSONObject json = new org.json.JSONObject(jsonLine);
                
                LogEntry entry = new LogEntry();
                entry.timestamp = json.optString("timestamp", "");
                entry.action = json.optString("action", "");
                entry.packageName = json.optString("package_name", "");
                entry.appName = json.optString("app_name", "");
                entry.title = json.optString("title", "");
                entry.text = json.optString("text", "");
                entry.ongoing = json.optBoolean("ongoing", false);
                
                return entry;
                
            } catch (Exception e) {
                return null;
            }
        }
        
        public String getDisplayTitle() {
            if (!title.isEmpty()) {
                return title;
            } else if (!text.isEmpty()) {
                return text.length() > 50 ? text.substring(0, 50) + "..." : text;
            } else {
                return appName.isEmpty() ? packageName : appName;
            }
        }
        
        public String getDisplayTime() {
            try {
                SimpleDateFormat inputFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault());
                SimpleDateFormat outputFormat = new SimpleDateFormat("HH:mm", Locale.getDefault());
                Date date = inputFormat.parse(timestamp);
                return outputFormat.format(date);
            } catch (Exception e) {
                return timestamp;
            }
        }
    }
}