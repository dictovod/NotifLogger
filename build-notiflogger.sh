#!/usr/bin/env bash
set -e

# === ПАРАМЕТРЫ ===
APP_ID="com.example.notificationlogger"
APP_NAME="NotificationLogger"
WORKDIR="$PWD/android_build"
JAVA_VERSION="17"
GRADLE_VERSION="8.2.1"

# === ПОДГОТОВКА ===
rm -rf "$WORKDIR"
mkdir -p "$WORKDIR"
cd "$WORKDIR"

# === СКАЧАТЬ GRADLE ===
if [ ! -f gradle.zip ]; then
  wget -q https://services.gradle.org/distributions/gradle-${GRADLE_VERSION}-bin.zip -O gradle.zip
fi
unzip -q gradle.zip
export PATH="$WORKDIR/gradle-${GRADLE_VERSION}/bin:$PATH"

# === СОЗДАТЬ ПРОЕКТ ===
mkdir -p app/src/main/java/com/example/notificationlogger
mkdir -p app/src/main/res/layout

# build.gradle проекта
cat > build.gradle <<'EOF'
plugins {
    id 'com.android.application'
}

android {
    namespace 'com.example.notificationlogger'
    compileSdk 34

    defaultConfig {
        applicationId "com.example.notificationlogger"
        minSdk 24
        targetSdk 34
        versionCode 1
        versionName "1.0"
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
    implementation 'androidx.core:core:1.12.0'
}
EOF

# settings.gradle
echo "rootProject.name = 'NotificationLogger'
include ':app'" > settings.gradle

# gradle-wrapper.properties (минимально)
mkdir -p gradle/wrapper
cat > gradle/wrapper/gradle-wrapper.properties <<EOF
distributionBase=GRADLE_USER_HOME
distributionPath=wrapper/dists
distributionUrl=https\://services.gradle.org/distributions/gradle-${GRADLE_VERSION}-bin.zip
zipStoreBase=GRADLE_USER_HOME
zipStorePath=wrapper/dists
EOF

# proguard
echo "" > proguard-rules.pro

# AndroidManifest.xml
cat > app/src/main/AndroidManifest.xml <<'EOF'
<?xml version="1.0" encoding="utf-8"?>
<manifest xmlns:android="http://schemas.android.com/apk/res/android"
    package="com.example.notificationlogger">

    <uses-permission android:name="android.permission.BIND_NOTIFICATION_LISTENER_SERVICE" tools:ignore="ProtectedPermissions" />
    <uses-permission android:name="android.permission.WRITE_EXTERNAL_STORAGE" />

    <application
        android:allowBackup="true"
        android:label="Notification Logger"
        android:supportsRtl="true">
        <activity android:name=".MainActivity">
            <intent-filter>
                <action android:name="android.intent.action.MAIN" />
                <category android:name="android.intent.category.LAUNCHER" />
            </intent-filter>
        </activity>

        <service
            android:name=".MainActivity\$MyNotificationListener"
            android:label="Notification Logger Service"
            android:permission="android.permission.BIND_NOTIFICATION_LISTENER_SERVICE">
            <intent-filter>
                <action android:name="android.service.notification.NotificationListenerService" />
            </intent-filter>
        </service>
    </application>
</manifest>
EOF

# Java-файл
cat > app/src/main/java/com/example/notificationlogger/MainActivity.java <<'EOF'
package com.example.notificationlogger;

import android.app.Activity;
import android.app.Notification;
import android.content.ContentValues;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.os.Bundle;
import android.service.notification.NotificationListenerService;
import android.service.notification.StatusBarNotification;
import android.util.Log;
import android.widget.TextView;
import android.content.Context;

import java.io.FileWriter;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class MainActivity extends Activity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        TextView tv = new TextView(this);
        tv.setText("Notification logger установлен.\nРазрешите доступ к уведомлениям.\nБаза: " +
            getDatabasePath("notifications.db"));
        setContentView(tv);
    }

    public static class DBHelper extends SQLiteOpenHelper {
        public DBHelper(Context context) {
            super(context, "notifications.db", null, 1);
        }
        @Override
        public void onCreate(SQLiteDatabase db) {
            db.execSQL("CREATE TABLE IF NOT EXISTS notifications (" +
                    "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                    "timestamp TEXT," +
                    "package TEXT," +
                    "title TEXT," +
                    "text TEXT)");
        }
        @Override
        public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
            db.execSQL("DROP TABLE IF EXISTS notifications");
            onCreate(db);
        }
    }

    public static class MyNotificationListener extends NotificationListenerService {
        private static final String TAG = "NotifLogger";
        private DBHelper dbHelper;
        private FileWriter logFile;
        @Override
        public void onCreate() {
            super.onCreate();
            dbHelper = new DBHelper(this);
            try {
                logFile = new FileWriter(getFilesDir() + "/notifications.log", true);
            } catch (IOException e) {
                Log.e(TAG, "Ошибка открытия файла", e);
            }
        }
        @Override
        public void onDestroy() {
            super.onDestroy();
            try {
                if (logFile != null) logFile.close();
            } catch (IOException e) {
                Log.e(TAG, "Ошибка закрытия файла", e);
            }
        }
        @Override
        public void onNotificationPosted(StatusBarNotification sbn) {
            Notification n = sbn.getNotification();
            if (n == null) return;
            CharSequence titleCs = n.extras.getCharSequence(Notification.EXTRA_TITLE);
            CharSequence textCs = n.extras.getCharSequence(Notification.EXTRA_TEXT);
            String pkg = sbn.getPackageName();
            String title = titleCs != null ? titleCs.toString() : "";
            String text = textCs != null ? textCs.toString() : "";
            String timestamp = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(new Date());
            SQLiteDatabase db = dbHelper.getWritableDatabase();
            ContentValues cv = new ContentValues();
            cv.put("timestamp", timestamp);
            cv.put("package", pkg);
            cv.put("title", title);
            cv.put("text", text);
            db.insert("notifications", null, cv);
            String logLine = timestamp + " | " + pkg + " | " + title + " | " + text + "\n";
            Log.i(TAG, logLine);
            try {
                if (logFile != null) {
                    logFile.write(logLine);
                    logFile.flush();
                }
            } catch (IOException e) {
                Log.e(TAG, "Ошибка записи", e);
            }
        }
        @Override
        public void onNotificationRemoved(StatusBarNotification sbn) {}
    }
}
EOF

# === СБОРКА ===
gradle assembleDebug

echo "=== APK готов ==="
find app/build/outputs/apk/debug -name "*.apk"
