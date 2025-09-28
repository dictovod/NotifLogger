
package com.notiflogger.app;

import android.Manifest;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Context;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.app.NotificationCompat;
import androidx.core.content.ContextCompat;

import android.service.notification.NotificationListenerService;
import android.service.notification.StatusBarNotification;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class MainActivity extends AppCompatActivity {

    private static final int PERMISSION_REQUEST_CODE = 1;
    private ListView logsListView;
    private TextView permissionsText;
    private Button requestPermBtn, settingsBtn;
    private List<LogEntry> logEntries = new ArrayList<>();
    private LogsAdapter adapter;
    private SQLiteDatabase db;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        logsListView = findViewById(R.id.logs_list);
        permissionsText = findViewById(R.id.permissions_status);
        requestPermBtn = findViewById(R.id.request_permissions);
        settingsBtn = findViewById(R.id.open_settings);

        adapter = new LogsAdapter(this, logEntries);
        logsListView.setAdapter(adapter);

        setupDatabase();

        requestPermBtn.setOnClickListener(v -> requestNotificationPermission());

        settingsBtn.setOnClickListener(v -> openNotificationSettings());

        updatePermissionsStatus();
        loadLogs();
    }

    @Override
    protected void onResume() {
        super.onResume();
        updatePermissionsStatus();
        loadLogs();
    }

    private void setupDatabase() {
        db = openOrCreateDatabase("logs.db", MODE_PRIVATE, null);
        db.execSQL("CREATE TABLE IF NOT EXISTS logs (id INTEGER PRIMARY KEY, title TEXT, text TEXT, time TEXT)");
    }

    private void requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.POST_NOTIFICATIONS}, PERMISSION_REQUEST_CODE);
            } else {
                Toast.makeText(this, "Разрешение уже предоставлено", Toast.LENGTH_SHORT).show();
            }
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == PERMISSION_REQUEST_CODE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                Toast.makeText(this, "Разрешение предоставлено", Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(this, "Разрешение отклонено", Toast.LENGTH_SHORT).show();
            }
            updatePermissionsStatus();
        }
    }

    private boolean hasNotificationPermission() {
        boolean listenerEnabled = Settings.Secure.getString(getContentResolver(), "enabled_notification_listeners").contains(getPackageName());
        boolean postGranted = true;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            postGranted = ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED;
        }
        return listenerEnabled && postGranted;
    }

    private void openNotificationSettings() {
        Intent intent = new Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS);
        startActivity(intent);
    }

    private void updatePermissionsStatus() {
        if (hasNotificationPermission()) {
            permissionsText.setText("Разрешения: предоставлены - ведётся логирование");
            requestPermBtn.setVisibility(View.GONE);
            settingsBtn.setVisibility(View.GONE);
        } else {
            permissionsText.setText("Разрешения: не предоставлены - дайте доступ для начала работы");
            requestPermBtn.setVisibility(View.VISIBLE);
            settingsBtn.setVisibility(View.VISIBLE);
        }
    }

    private void loadLogs() {
        logEntries.clear();
        Cursor cursor = db.rawQuery("SELECT * FROM logs ORDER BY id DESC", null);
        if (cursor.moveToFirst()) {
            do {
                LogEntry entry = new LogEntry();
                entry.title = cursor.getString(cursor.getColumnIndex("title"));
                entry.text = cursor.getString(cursor.getColumnIndex("text"));
                entry.time = cursor.getString(cursor.getColumnIndex("time"));
                logEntries.add(entry);
            } while (cursor.moveToNext());
        }
        cursor.close();
        adapter.notifyDataSetChanged();
    }

    public static class LogEntry {
        String title;
        String text;
        String time;
    }

    public static class LogsAdapter extends ArrayAdapter<LogEntry> {
        public LogsAdapter(Context context, List<LogEntry> objects) {
            super(context, 0, objects);
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            if (convertView == null) {
                convertView = LayoutInflater.from(getContext()).inflate(R.layout.item_log_entry, parent, false);
            }
            LogEntry entry = getItem(position);
            TextView titleView = convertView.findViewById(R.id.log_title);
            TextView textView = convertView.findViewById(R.id.log_text);
            TextView timeView = convertView.findViewById(R.id.log_time);
            titleView.setText(entry.title);
            textView.setText(entry.text);
            timeView.setText(entry.time);
            return convertView;
        }
    }

    public static class Utils {
        public static String formatTime(long time) {
            return new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(new Date(time));
        }
    }

    public static class NotificationLoggerService extends NotificationListenerService {

        private static final String CHANNEL_ID = "notif_logger_channel";

        @Override
        public void onCreate() {
            super.onCreate();
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                NotificationChannel channel = new NotificationChannel(CHANNEL_ID, "NotifLogger Channel", NotificationManager.IMPORTANCE_DEFAULT);
                NotificationManager manager = getSystemService(NotificationManager.class);
                manager.createNotificationChannel(channel);
            }
        }

        @Override
        public void onListenerConnected() {
            super.onListenerConnected();
            NotificationCompat.Builder builder = new NotificationCompat.Builder(this, CHANNEL_ID)
                    .setContentTitle("NotifLogger")
                    .setContentText("Логирование уведомлений")
                    .setSmallIcon(R.drawable.ic_launcher)
                    .setPriority(NotificationCompat.PRIORITY_DEFAULT);

            startForeground(1, builder.build());
        }

        @Override
        public void onNotificationPosted(StatusBarNotification sbn) {
            if (sbn == null) return;
            Notification notification = sbn.getNotification();
            if (notification == null) return;
            String title = notification.extras.getString(NotificationCompat.EXTRA_TITLE, "Unknown");
            String text = notification.extras.getString(NotificationCompat.EXTRA_TEXT, "");
            String time = Utils.formatTime(System.currentTimeMillis());

            SQLiteDatabase db = openOrCreateDatabase("logs.db", MODE_PRIVATE, null);
            db.execSQL("INSERT INTO logs (title, text, time) VALUES (?, ?, ?)", new Object[]{title, text, time});
            db.close();
        }
    }
}
