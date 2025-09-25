package com.notiflogger.app;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

public class MainActivity extends AppCompatActivity {
    
    private FrameLayout statusContainer;
    private ImageView statusIcon;
    private TextView statusTitle;
    private TextView statusDescription;
    private TextView deviceIdTextView; // Новый TextView для отображения Device ID
    private Button activateButton;
    private ImageButton settingsButton;
    
    private ActivationManager activationManager;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        
        initViews();
        setupClickListeners();
        
        activationManager = new ActivationManager(this);
        updateUI();
    }

    @Override
    protected void onResume() {
        super.onResume();
        // Обновляем UI при возврате из настроек
        updateUI();
    }

    private void initViews() {
        statusContainer = findViewById(R.id.status_container);
        statusIcon = findViewById(R.id.status_icon);
        statusTitle = findViewById(R.id.tv_status_title);
        statusDescription = findViewById(R.id.tv_status_description);
        deviceIdTextView = findViewById(R.id.tv_device_id); // Предполагаем, что добавим этот элемент в layout
        activateButton = findViewById(R.id.btn_activate);
        settingsButton = findViewById(R.id.btn_settings);
        
        // Навигация
        findViewById(R.id.nav_home).setOnClickListener(v -> {
            // Уже на главной странице
        });
        
        findViewById(R.id.nav_permissions).setOnClickListener(v -> {
            startActivity(new Intent(this, PermissionsActivity.class));
        });
        
        findViewById(R.id.nav_logs).setOnClickListener(v -> {
            if (activationManager.isActivated()) {
                startActivity(new Intent(this, LogsActivity.class));
            } else {
                // Показываем сообщение о необходимости активации
                Utils.showToast(this, "Сначала активируйте приложение");
            }
        });
    }

    private void setupClickListeners() {
        settingsButton.setOnClickListener(v -> openSettings());
        activateButton.setOnClickListener(v -> openSettings());
    }

    private void openSettings() {
        Intent intent = new Intent(this, SettingsActivity.class);
        startActivity(intent);
    }

    private void updateUI() {
        boolean isActivated = activationManager.isActivated();
        String deviceId = activationManager.getDeviceUniqueId(); // Получаем Device ID
        
        if (isActivated) {
            // Активированное состояние - цветная иконка
            statusIcon.setImageResource(R.drawable.ic_bell_active);
            statusContainer.setBackgroundResource(R.drawable.bg_status_active);
            statusTitle.setText(R.string.status_activated);
            statusDescription.setText(R.string.status_description_activated);
            activateButton.setText(R.string.btn_go_to_settings);
            if (deviceIdTextView != null) {
                deviceIdTextView.setText(getString(R.string.device_id_label) + ": " + (deviceId != null ? deviceId : "Не удалось получить ID"));
                deviceIdTextView.setVisibility(View.VISIBLE); // Показываем Device ID
            }
            
            // Подсветка для активного состояния
            statusContainer.setElevation(12f);
            
        } else {
            // Неактивированное состояние - черно-белая иконка
            statusIcon.setImageResource(R.drawable.ic_bell_inactive);
            statusContainer.setBackgroundResource(R.drawable.bg_status_inactive);
            statusTitle.setText(R.string.status_not_activated);
            statusDescription.setText(R.string.status_description_not_activated);
            activateButton.setText(R.string.btn_activate);
            if (deviceIdTextView != null) {
                deviceIdTextView.setText(getString(R.string.device_id_label) + ": " + (deviceId != null ? deviceId : "Не удалось получить ID"));
                deviceIdTextView.setVisibility(View.VISIBLE); // Показываем Device ID
            }
            
            statusContainer.setElevation(4f);
        }
        
        // Проверяем истечение активации
        if (isActivated && activationManager.isActivationExpired()) {
            // Активация истекла - показываем как неактивированное
            activationManager.clearActivation();
            updateUI(); // Рекурсивно обновляем UI
        }
    }
}