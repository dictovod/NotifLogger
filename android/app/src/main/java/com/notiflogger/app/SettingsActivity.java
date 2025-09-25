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
    }

    private void setupClickListeners() {
        copyButton.setOnClickListener(v -> copyImeiToClipboard());
        activateButton.setOnClickListener(v -> performActivation());
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
}