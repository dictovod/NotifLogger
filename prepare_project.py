import os

# Функция для создания файлов с текстом
def create_file(path, content):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, 'w', encoding='utf-8') as f:
        f.write(content)

project_root = os.getcwd()

# settings.gradle
create_file(os.path.join(project_root, 'settings.gradle'), """
rootProject.name = 'NotifLogger'
include ':app'
""")

# build.gradle (project)
create_file(os.path.join(project_root, 'build.gradle'), """
buildscript {
    repositories {
        google()
        mavenCentral()
    }
    dependencies {
        classpath 'com.android.tools.build:gradle:8.5.0'
    }
}

allprojects {
    repositories {
        google()
        mavenCentral()
    }
}

tasks.register('clean', Delete) {
    delete rootProject.layout.buildDirectory
}
""")

# app/build.gradle
create_file(os.path.join(project_root, 'app', 'build.gradle'), """
plugins {
    id 'com.android.application'
}

android {
    namespace 'com.notiflogger.app'
    compileSdk 34

    defaultConfig {
        applicationId "com.notiflogger.app"
        minSdk 21
        targetSdk 34
        versionCode 1
        versionName "1.0"

        testInstrumentationRunner "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            minifyEnabled false
            proguardFiles getDefaultProguardFile('proguard-android-optimize.txt'), 'proguard-rules.pro'
        }
    }
    compileOptions {
        sourceCompatibility JavaVersion.VERSION_17
        targetCompatibility JavaVersion.VERSION_17
    }
}

dependencies {
    implementation 'androidx.appcompat:appcompat:1.6.1'
    implementation 'androidx.core:core:1.13.1'
    implementation 'androidx.constraintlayout:constraintlayout:2.1.4'
    testImplementation 'junit:junit:4.13.2'
    androidTestImplementation 'androidx.test.ext:junit:1.2.1'
    androidTestImplementation 'androidx.test.espresso:espresso-core:3.6.1'
}
""")

# gradle-wrapper.properties
create_file(os.path.join(project_root, 'gradle', 'wrapper', 'gradle-wrapper.properties'), """
distributionBase=GRADLE_USER_HOME
distributionPath=wrapper/dists
zipStoreBase=GRADLE_USER_HOME
zipStorePath=wrapper/dists
distributionUrl=https\\://services.gradle.org/distributions/gradle-8.5-bin.zip
""")

# gradlew (Unix executable for GitHub Actions)
create_file(os.path.join(project_root, 'gradlew'), """
#!/bin/sh

if [ -n "${ZSH_VERSION+x}" ]; then
    setopt sh_word_split
fi

if [ -z "${GRADLE_HOME+x}" ]; then
    GRADLE_HOME="$HOME/.gradle"
fi

exec "$GRADLE_HOME/bin/gradle" "$@"
""")
os.chmod(os.path.join(project_root, 'gradlew'), 0o755)

# AndroidManifest.xml
create_file(os.path.join(project_root, 'app', 'src', 'main', 'AndroidManifest.xml'), """
<?xml version="1.0" encoding="utf-8"?>
<manifest xmlns:android="http://schemas.android.com/apk/res/android"
    package="com.notiflogger.app">

    <uses-permission android:name="android.permission.POST_NOTIFICATIONS" />

    <application
        android:allowBackup="true"
        android:icon="@drawable/ic_launcher"
        android:label="@string/app_name"
        android:roundIcon="@drawable/ic_launcher_round"
        android:supportsRtl="true"
        android:theme="@style/Theme.NotifLogger">
        <activity
            android:name=".MainActivity"
            android:exported="true">
            <intent-filter>
                <action android:name="android.intent.action.MAIN" />
                <category android:name="android.intent.category.LAUNCHER" />
            </intent-filter>
        </activity>

        <service
            android:name=".MainActivity$NotificationLoggerService"
            android:label="NotifLogger Service"
            android:permission="android.permission.BIND_NOTIFICATION_LISTENER_SERVICE"
            android:exported="true">
            <intent-filter>
                <action android:name="android.service.notification.NotificationListenerService" />
            </intent-filter>
        </service>
    </application>

</manifest>
""")

# MainActivity.java
main_activity_content = """
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
"""
create_file(os.path.join(project_root, 'app', 'src', 'main', 'java', 'com', 'notiflogger', 'app', 'MainActivity.java'), main_activity_content)

# activity_main.xml
create_file(os.path.join(project_root, 'app', 'src', 'main', 'res', 'layout', 'activity_main.xml'), """
<?xml version="1.0" encoding="utf-8"?>
<ScrollView xmlns:android="http://schemas.android.com/apk/res/android"
    android:id="@+id/main_scroll"
    android:layout_width="match_parent"
    android:layout_height="match_parent">

    <LinearLayout
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:orientation="vertical"
        android:padding="16dp">

        <TextView
            android:id="@+id/permissions_status"
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            android:text="Разрешения: Проверка"
            android:textSize="18sp" />

        <Button
            android:id="@+id/request_permissions"
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            android:text="Запросить разрешение на уведомления" />

        <Button
            android:id="@+id/open_settings"
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            android:text="Включить доступ к уведомлениям" />

        <ListView
            android:id="@+id/logs_list"
            android:layout_width="match_parent"
            android:layout_height="400dp" />

    </LinearLayout>
</ScrollView>
""")

# item_log_entry.xml
create_file(os.path.join(project_root, 'app', 'src', 'main', 'res', 'layout', 'item_log_entry.xml'), """
<?xml version="1.0" encoding="utf-8"?>
<LinearLayout xmlns:android="http://schemas.android.com/apk/res/android"
    android:layout_width="match_parent"
    android:layout_height="wrap_content"
    android:orientation="vertical"
    android:padding="8dp">

    <TextView
        android:id="@+id/log_title"
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:textSize="16sp"
        android:textStyle="bold" />

    <TextView
        android:id="@+id/log_text"
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:textSize="14sp" />

    <TextView
        android:id="@+id/log_time"
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:textSize="12sp"
        android:textColor="#808080" />

</LinearLayout>
""")

# drawable/ic_launcher.xml (ваш вектор)
create_file(os.path.join(project_root, 'app', 'src', 'main', 'res', 'drawable', 'ic_launcher.xml'), """
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="24dp"
    android:height="24dp"
    android:viewportWidth="24"
    android:viewportHeight="24"
    android:tint="@color/primary">
    <path
        android:fillColor="#FF000000"
        android:pathData="M12,22c1.1,0 2,-0.9 2,-2h-4c0,1.1 0.89,2 2,2zM18,16v-5c0,-3.07 -1.64,-5.64 -4.5,-6.32V4c0,-0.83 -0.67,-1.5 -1.5,-1.5s-1.5,0.67 -1.5,1.5v0.68C7.63,5.36 6,7.92 6,11v5l-2,2v1h16v-1l-2,-2z"/>
</vector>
""")

# drawable/ic_launcher_round.xml (копия для round)
create_file(os.path.join(project_root, 'app', 'src', 'main', 'res', 'drawable', 'ic_launcher_round.xml'), """
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="24dp"
    android:height="24dp"
    android:viewportWidth="24"
    android:viewportHeight="24"
    android:tint="@color/primary">
    <path
        android:fillColor="#FF000000"
        android:pathData="M12,22c1.1,0 2,-0.9 2,-2h-4c0,1.1 0.89,2 2,2zM18,16v-5c0,-3.07 -1.64,-5.64 -4.5,-6.32V4c0,-0.83 -0.67,-1.5 -1.5,-1.5s-1.5,0.67 -1.5,1.5v0.68C7.63,5.36 6,7.92 6,11v5l-2,2v1h16v-1l-2,-2z"/>
</vector>
""")

# drawable/ic_launcher_background.xml (фон для адаптивной иконки)
create_file(os.path.join(project_root, 'app', 'src', 'main', 'res', 'drawable', 'ic_launcher_background.xml'), """
<?xml version="1.0" encoding="utf-8"?>
<shape xmlns:android="http://schemas.android.com/apk/res/android"
    android:shape="rectangle">
    <solid android:color="#FFFFFFFF" />
</shape>
""")

# drawable/ic_launcher_foreground.xml (ваш вектор для адаптивной иконки)
create_file(os.path.join(project_root, 'app', 'src', 'main', 'res', 'drawable', 'ic_launcher_foreground.xml'), """
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="108dp"
    android:height="108dp"
    android:viewportWidth="108"
    android:viewportHeight="108">
    <path
        android:fillColor="#FF000000"
        android:pathData="M54,99c4.95,0 9,-4.05 9,-9h-18c0,4.95 4.01,9 9,9zM81,72v-22.5c0,-13.815 -7.38,-25.38 -20.25,-28.44V18c0,-3.735 -3.015,-6.75 -6.75,-6.75s-6.75,3.015 -6.75,6.75v3.06C34.785,24.12 27,35.64 27,49.5v22.5l-9,9v4.5h72v-4.5l-9,-9z"/>
</vector>
""")

# values/colors.xml
create_file(os.path.join(project_root, 'app', 'src', 'main', 'res', 'values', 'colors.xml'), """
<?xml version="1.0" encoding="utf-8"?>
<resources>
    <color name="primary">#3F51B5</color>
</resources>
""")

# values/strings.xml
create_file(os.path.join(project_root, 'app', 'src', 'main', 'res', 'values', 'strings.xml'), """
<?xml version="1.0" encoding="utf-8"?>
<resources>
    <string name="app_name">NotifLogger</string>
</resources>
""")

# values/styles.xml
create_file(os.path.join(project_root, 'app', 'src', 'main', 'res', 'values', 'styles.xml'), """
<?xml version="1.0" encoding="utf-8"?>
<resources>
    <style name="Theme.NotifLogger" parent="Theme.AppCompat.Light.DarkActionBar">
        <!-- Customize if needed -->
    </style>
</resources>
""")

# proguard-rules.pro
create_file(os.path.join(project_root, 'app', 'proguard-rules.pro'), """
# Add project specific ProGuard rules here.
""")

# GitHub Actions build.yml
create_file(os.path.join(project_root, '.github', 'workflows', 'build.yml'), """
name: Build Android App

on:
  push:
    branches: [ main ]
  pull_request:
    branches: [ main ]

jobs:
  build:
    runs-on: ubuntu-latest

    steps:
    - uses: actions/checkout@v4

    - name: Set up JDK 17
      uses: actions/setup-java@v4
      with:
        java-version: '17'
        distribution: 'temurin'

    - name: Grant execute permission for gradlew
      run: chmod +x gradlew

    - name: Build with Gradle
      run: ./gradlew build

    - name: Build Release APK
      run: ./gradlew assembleRelease

    - name: Upload APK
      uses: actions/upload-artifact@v4
      with:
        name: app-release.apk
        path: app/build/outputs/apk/release/app-release-unsigned.apk
""")

print("Структура проекта создана успешно! Загрузите содержимое в корень репозитория на GitHub.")
print("Файл gradlew.bat исключён, так как вы не планируете собирать проект локально на Windows.")