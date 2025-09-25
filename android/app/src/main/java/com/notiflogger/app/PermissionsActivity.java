package com.notiflogger.app;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.Settings;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

/**
 * Активность для управления разрешениями приложения
 */
public class PermissionsActivity extends AppCompatActivity {
    
    private static final int REQUEST_NOTIFICATION_PERMISSION = 1001;
    private static final int REQUEST_STORAGE_PERMISSION = 1002;
    
    private View notificationCard;
    private View storageCard;
    private View phoneCard;
    
    private ImageView notificationIcon;
    private ImageView storageIcon;
    private ImageView phoneIcon;
    
    private TextView notificationStatus;
    private TextView storageStatus;
    private TextView phoneStatus;
    
    private Button notificationButton;
    private Button storageButton;
    private Button phoneButton;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_permissions);
        
        initViews();
        setupClickListeners();
    }

    @Override
    protected void onResume() {
        super.onResume();
        updatePermissionStates();
    }

    private void initViews() {
        findViewById(R.id.btn_back).setOnClickListener(v -> finish());
        
        notificationCard = findViewById(R.id.card_notification);
        storageCard = findViewById(R.id.card_storage);
        phoneCard = findViewById(R.id.card_phone);
        
        notificationIcon = findViewById(R.id.icon_notification);
        storageIcon = findViewById(R.id.icon_storage);
        phoneIcon = findViewById(R.id.icon_phone);
        
        notificationStatus = findViewById(R.id.status_notification);
        storageStatus = findViewById(R.id.status_storage);
        phoneStatus = findViewById(R.id.status_phone);
        
        notificationButton = findViewById(R.id.btn_notification);
        storageButton = findViewById(R.id.btn_storage);
        phoneButton = findViewById(R.id.btn_phone);
    }

    private void setupClickListeners() {
        notificationButton.setOnClickListener(v -> requestNotificationPermission());
        storageButton.setOnClickListener(v -> requestStoragePermission());
        phoneButton.setOnClickListener(v -> showPhoneInfo()); // Обновлено для информации
    }

    private void updatePermissionStates() {
        updateNotificationPermission();
        updateStoragePermission();
        updatePhoneInfo(); // Обновлено для информации
    }

    private void updateNotificationPermission() {
        boolean hasPermission = Utils.isNotificationServiceEnabled(this);
        
        if (hasPermission) {
            notificationIcon.setImageResource(R.drawable.ic_check);
            notificationIcon.setColorFilter(getColor(R.color.success));
            notificationStatus.setText("Предоставлено");
            notificationStatus.setTextColor(getColor(R.color.success));
            notificationButton.setText("Настроить");
            notificationButton.setEnabled(true);
        } else {
            notificationIcon.setImageResource(R.drawable.ic_error);
            notificationIcon.setColorFilter(getColor(R.color.error));
            notificationStatus.setText("Не предоставлено");
            notificationStatus.setTextColor(getColor(R.color.error));
            notificationButton.setText("Предоставить");
            notificationButton.setEnabled(true);
        }
    }

    private void updateStoragePermission() {
        boolean hasPermission;
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            hasPermission = Environment.isExternalStorageManager();
        } else {
            hasPermission = ContextCompat.checkSelfPermission(this, 
                Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED;
        }
        
        if (hasPermission) {
            storageIcon.setImageResource(R.drawable.ic_check);
            storageIcon.setColorFilter(getColor(R.color.success));
            storageStatus.setText("Предоставлено");
            storageStatus.setTextColor(getColor(R.color.success));
            storageButton.setText("Настроить");
            storageButton.setEnabled(true);
        } else {
            storageIcon.setImageResource(R.drawable.ic_error);
            storageIcon.setColorFilter(getColor(R.color.error));
            storageStatus.setText("Не предоставлено");
            storageStatus.setTextColor(getColor(R.color.error));
            storageButton.setText("Предоставить");
            storageButton.setEnabled(true);
        }
    }

    private void updatePhoneInfo() {
        // Теперь это информационное поле, так как READ_PHONE_STATE не требуется
        phoneIcon.setImageResource(R.drawable.ic_info);
        phoneIcon.setColorFilter(getColor(R.color.text_secondary));
        phoneStatus.setText("Не требуется");
        phoneStatus.setTextColor(getColor(R.color.text_secondary));
        phoneButton.setText("Подробнее");
        phoneButton.setEnabled(true);
    }

    private void requestNotificationPermission() {
        if (Utils.isNotificationServiceEnabled(this)) {
            // Разрешение уже есть, открываем настройки
            Intent intent = new Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS);
            startActivity(intent);
        } else {
            // Показываем диалог с объяснением
            showPermissionDialog(
                "Доступ к уведомлениям",
                "Приложению необходим доступ к уведомлениям для их логирования. " +
                "Это основная функция приложения.\n\n" +
                "В настройках найдите \"" + getString(R.string.app_name) + "\" и включите доступ.",
                () -> {
                    Intent intent = new Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS);
                    startActivityForResult(intent, REQUEST_NOTIFICATION_PERMISSION);
                }
            );
        }
    }

    private void requestStoragePermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            if (Environment.isExternalStorageManager()) {
                // Разрешение уже есть
                Utils.showToast(this, "Разрешение уже предоставлено");
            } else {
                showPermissionDialog(
                    "Доступ к файлам",
                    "Приложению необходим доступ к файлам для сохранения логов уведомлений.",
                    () -> {
                        Intent intent = new Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION);
                        intent.setData(Uri.parse("package:" + getPackageName()));
                        startActivityForResult(intent, REQUEST_STORAGE_PERMISSION);
                    }
                );
            }
        } else {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) 
                    != PackageManager.PERMISSION_GRANTED) {
                
                showPermissionDialog(
                    "Доступ к хранилищу",
                    "Приложению необходим доступ к хранилищу для сохранения файлов логов.",
                    () -> {
                        ActivityCompat.requestPermissions(this, 
                            new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE}, 
                            REQUEST_STORAGE_PERMISSION);
                    }
                );
            } else {
                Utils.showToast(this, "Разрешение уже предоставлено");
            }
        }
    }

    private void showPhoneInfo() {
        showPermissionDialog(
            "Информация об идентификаторе",
            "Приложение больше не требует разрешения на доступ к состоянию телефона (IMEI). " +
            "Теперь используется уникальный идентификатор устройства (Device ID), который " +
            "получается безопасно и не требует специальных разрешений.\n\n" +
            "Если у вас есть вопросы, обратитесь в поддержку.",
            null // Нет действия, только информация
        );
    }

    private void showPermissionDialog(String title, String message, Runnable onConfirm) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this)
                .setTitle(title)
                .setMessage(message)
                .setIcon(R.drawable.ic_permissions)
                .setCancelable(true);

        if (onConfirm != null) {
            builder.setPositiveButton("Предоставить", (dialog, which) -> onConfirm.run())
                   .setNegativeButton("Отмена", null);
        } else {
            builder.setPositiveButton("Закрыть", (dialog, which) -> dialog.dismiss());
        }

        builder.show();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        
        // Обновляем состояния разрешений после возврата из настроек
        updatePermissionStates();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, 
                                         @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        
        if (requestCode == REQUEST_STORAGE_PERMISSION) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                Utils.showToast(this, "Разрешение предоставлено");
            } else {
                Utils.showToast(this, "Разрешение отклонено");
            }
        }
        
        updatePermissionStates();
    }
}