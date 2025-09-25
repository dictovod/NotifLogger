package com.notiflogger.app;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.textfield.TextInputEditText;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class SettingsActivity extends AppCompatActivity {
    
    private TextView imeiTextView;
    private TextInputEditText tokenEditText;
    private Button activateButton;
    private Button debugButton; // Новая кнопка для отладки токена
    private ImageButton copyButton;
    private LinearLayout errorLayout;
    private TextView errorTextView;
    private TextView activationStatusTextView;
    private LinearLayout expirationLayout;
    private TextView expirationDateTextView;
    
    private ActivationManager activationManager;
    private String deviceImei;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);
        
        initViews();
        setupClickListeners();
        
        activationManager = new ActivationManager(this);
        deviceImei = Utils.getDeviceIMEI(this);
        
        updateUI();
    }

    private void initViews() {
        findViewById(R.id.btn_back).setOnClickListener(v -> finish());
        
        imeiTextView = findViewById(R.id.tv_imei);
        tokenEditText = findViewById(R.id.et_token);
        activateButton = findViewById(R.id.btn_activate);
        copyButton = findViewById(R.id.btn_copy_imei);
        errorLayout = findViewById(R.id.layout_error);
        errorTextView = findViewById(R.id.tv_error);
        activationStatusTextView = findViewById(R.id.tv_activation_status);
        expirationLayout = findViewById(R.id.layout_expiration);
        expirationDateTextView = findViewById(R.id.tv_expiration_date);
        
        // Создаем кнопку отладки токена программно
        createDebugButton();
    }

    private void setupClickListeners() {
        copyButton.setOnClickListener(v -> copyImeiToClipboard());
        activateButton.setOnClickListener(v -> performActivation());
        if (debugButton != null) {
            debugButton.setOnClickListener(v -> debugToken());
        }
    }

    private void updateUI() {
        // Отображаем IMEI
        imeiTextView.setText(deviceImei);
        
        // Обновляем статус активации
        boolean isActivated = activationManager.isActivated();
        
        if (isActivated) {
            activationStatusTextView.setText("Активировано");
            activationStatusTextView.setTextColor(getColor(R.color.success));
            
            // Показываем дату истечения
            long expirationTime = activationManager.getExpirationTime();
            if (expirationTime > 0) {
                expirationLayout.setVisibility(View.VISIBLE);
                SimpleDateFormat sdf = new SimpleDateFormat("dd MMMM yyyy, HH:mm", new Locale("ru"));
                expirationDateTextView.setText(sdf.format(new Date(expirationTime)));
            }
            
            activateButton.setText("Активировано");
            activateButton.setEnabled(false);
            
        } else {
            activationStatusTextView.setText("Не активировано");
            activationStatusTextView.setTextColor(getColor(R.color.error));
            expirationLayout.setVisibility(View.GONE);
            
            activateButton.setText(R.string.btn_activate_app);
            activateButton.setEnabled(true);
        }
        
        // Проверяем истечение активации
        if (isActivated && activationManager.isActivationExpired()) {
            activationManager.clearActivation();
            updateUI(); // Рекурсивно обновляем UI
            showError("Срок активации истек. Получите новый токен.");
        }
    }

    private void copyImeiToClipboard() {
        ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        ClipData clip = ClipData.newPlainText("IMEI", deviceImei);
        clipboard.setPrimaryClip(clip);
        
        // Показываем анимацию копирования
        copyButton.setImageResource(R.drawable.ic_check);
        copyButton.postDelayed(() -> {
            copyButton.setImageResource(R.drawable.ic_copy);
        }, 2000);
        
        Utils.showToast(this, getString(R.string.imei_copied));
    }

    private void performActivation() {
        hideError();
        
        String token = tokenEditText.getText().toString().trim();
        
        if (TextUtils.isEmpty(token)) {
            showError(getString(R.string.token_required));
            return;
        }
        
        if (token.length() < 10) {
            showError(getString(R.string.token_too_short));
            return;
        }
        
        // Сохраняем токен перед валидацией
        android.content.SharedPreferences prefs = getSharedPreferences("activation", MODE_PRIVATE);
        prefs.edit().putString("activation_token", token).apply();
        
        activateButton.setText(R.string.validating);
        activateButton.setEnabled(false);
        
        // Выполняем активацию в фоновом потоке
        new Thread(() -> {
            boolean success = activationManager.validateAndActivate(deviceImei, token);
            
            runOnUiThread(() -> {
                if (success) {
                    Utils.showToast(this, getString(R.string.activation_success));
                    tokenEditText.setText(""); // Очищаем поле токена
                    updateUI();
                    showSuccessDialog();
                } else {
                    showError(getString(R.string.invalid_token));
                    activateButton.setText(R.string.btn_activate_app);
                    activateButton.setEnabled(true);
                }
            });
        }).start();
    }

    private void showError(String message) {
        errorTextView.setText(message);
        errorLayout.setVisibility(View.VISIBLE);
    }

    private void hideError() {
        errorLayout.setVisibility(View.GONE);
    }

    private void showSuccessDialog() {
        androidx.appcompat.app.AlertDialog.Builder builder = 
            new androidx.appcompat.app.AlertDialog.Builder(this);
        
        builder.setTitle("Активация успешна!")
               .setMessage("Приложение активировано и готово к использованию")
               .setIcon(R.drawable.ic_check)
               .setPositiveButton("Продолжить", (dialog, which) -> {
                   dialog.dismiss();
                   finish(); // Возвращаемся на главную
               })
               .setCancelable(false)
               .show();
    }
    
    /**
     * Создает кнопку отладки токена программно и добавляет её в layout
     */
    private void createDebugButton() {
        try {
            // Находим родительский контейнер с кнопкой активации
            View activateButtonParent = findViewById(R.id.btn_activate).getParent().getParent();
            if (activateButtonParent instanceof LinearLayout) {
                LinearLayout container = (LinearLayout) activateButtonParent;
                
                // Создаем кнопку отладки
                debugButton = new Button(this);
                debugButton.setText("🔍 Отладить токен");
                debugButton.setTextSize(14);
                debugButton.setBackgroundColor(getColor(android.R.color.holo_orange_light));
                debugButton.setTextColor(getColor(android.R.color.white));
                debugButton.setPadding(32, 24, 32, 24);
                
                // Параметры размещения
                LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                );
                params.topMargin = 16;
                debugButton.setLayoutParams(params);
                
                // Добавляем кнопку в контейнер после кнопки активации
                container.addView(debugButton);
                
                android.util.Log.d("SettingsActivity", "Кнопка отладки успешно создана");
            } else {
                android.util.Log.e("SettingsActivity", "Не удалось найти контейнер для кнопки отладки");
            }
        } catch (Exception e) {
            android.util.Log.e("SettingsActivity", "Ошибка при создании кнопки отладки: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    /**
     * Отладка токена - показывает подробную информацию о проблемах с токеном
     */
    private void debugToken() {
        String token = tokenEditText.getText().toString().trim();
        
        if (TextUtils.isEmpty(token)) {
            showDebugDialog("Ошибка", "Введите токен для отладки");
            return;
        }
        
        // Показываем индикатор загрузки
        if (debugButton != null) {
            debugButton.setText("🔍 Отладка...");
            debugButton.setEnabled(false);
        }
        
        // Выполняем отладку в фоновом потоке
        new Thread(() -> {
            String debugResult = activationManager.validateTokenDebug(token);
            
            runOnUiThread(() -> {
                // Восстанавливаем кнопку
                if (debugButton != null) {
                    debugButton.setText("🔍 Отладить токен");
                    debugButton.setEnabled(true);
                }
                
                // Показываем результат отладки
                showDebugDialog("Результат отладки токена", debugResult);
            });
        }).start();
    }
    
    /**
     * Показывает диалог с результатами отладки
     */
    private void showDebugDialog(String title, String debugText) {
        androidx.appcompat.app.AlertDialog.Builder builder = 
            new androidx.appcompat.app.AlertDialog.Builder(this);
        
        // Создаем ScrollView для длинного текста
        android.widget.ScrollView scrollView = new android.widget.ScrollView(this);
        TextView textView = new TextView(this);
        textView.setText(debugText);
        textView.setTextSize(12);
        textView.setPadding(32, 32, 32, 32);
        textView.setTypeface(android.graphics.Typeface.MONOSPACE);
        
        scrollView.addView(textView);
        
        builder.setTitle(title)
               .setView(scrollView)
               .setPositiveButton("Закрыть", (dialog, which) -> dialog.dismiss())
               .setNeutralButton("Скопировать", (dialog, which) -> {
                   ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
                   ClipData clip = ClipData.newPlainText("Отладка токена", debugText);
                   clipboard.setPrimaryClip(clip);
                   Utils.showToast(this, "Результат скопирован в буфер обмена");
               })
               .show();
    }
}