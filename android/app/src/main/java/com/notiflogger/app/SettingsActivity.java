package com.notiflogger.app;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import android.view.ViewParent;
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
    private TextView deviceIdTextView;
    private TextInputEditText tokenEditText;
    private Button activateButton;
    private Button debugButton;
    private ImageButton copyButton;
    private LinearLayout errorLayout;
    private TextView errorTextView;
    private TextView activationStatusTextView;
    private LinearLayout expirationLayout;
    private TextView expirationDateTextView;
    private LinearLayout tokenCreationLayout;
    private TextView tokenCreationDateTextView;
    private ActivationManager activationManager;
    private String deviceId;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);
        initViews();
        setupClickListeners();
        activationManager = new ActivationManager(this);
        deviceId = activationManager.getDeviceUniqueId();
        updateUI();
    }

    private void initViews() {
        findViewById(R.id.btn_back).setOnClickListener(v -> finish());
        deviceIdTextView = findViewById(R.id.tv_imei);
        tokenEditText = findViewById(R.id.et_token);
        activateButton = findViewById(R.id.btn_activate);
        copyButton = findViewById(R.id.btn_copy_imei);
        errorLayout = findViewById(R.id.layout_error);
        errorTextView = findViewById(R.id.tv_error);
        activationStatusTextView = findViewById(R.id.tv_activation_status);
        expirationLayout = findViewById(R.id.layout_expiration);
        expirationDateTextView = findViewById(R.id.tv_expiration_date);
        tokenCreationLayout = findViewById(R.id.layout_token_creation);
        tokenCreationDateTextView = findViewById(R.id.tv_token_creation_date);
        createDebugButton();
    }

    private void setupClickListeners() {
        copyButton.setOnClickListener(v -> copyDeviceIdToClipboard());
        activateButton.setOnClickListener(v -> performActivation());
        if (debugButton != null) debugButton.setOnClickListener(v -> debugToken());
    }

    private void updateUI() {
        deviceIdTextView.setText(deviceId != null ? deviceId : "Не удалось получить Device ID");
        boolean isActivated = activationManager.isActivated();
        if (isActivated) {
            activationStatusTextView.setText(R.string.status_activated);
            activationStatusTextView.setTextColor(getColor(R.color.success));
            long expirationTime = activationManager.getExpirationTime();
            String tokenCreationDate = activationManager.getTokenCreationDate();
            if (expirationTime > 0) {
                expirationLayout.setVisibility(View.VISIBLE);
                SimpleDateFormat sdf = new SimpleDateFormat("dd MMMM yyyy, HH:mm", new Locale("ru"));
                expirationDateTextView.setText(sdf.format(new Date(expirationTime)));
            }
            if (!TextUtils.isEmpty(tokenCreationDate)) {
                tokenCreationLayout.setVisibility(View.VISIBLE);
                tokenCreationDateTextView.setText(tokenCreationDate);
            }
            activateButton.setText(R.string.status_activated);
            activateButton.setEnabled(false);
        } else {
            activationStatusTextView.setText(R.string.status_not_activated);
            activationStatusTextView.setTextColor(getColor(R.color.error));
            expirationLayout.setVisibility(View.GONE);
            tokenCreationLayout.setVisibility(View.GONE);
            activateButton.setText(R.string.btn_activate_app);
            activateButton.setEnabled(true);
        }
        if (isActivated && activationManager.isActivationExpired()) {
            activationManager.clearActivation();
            updateUI();
            showError("Срок активации истек. Получите новый токен.");
        }
    }

    private void copyDeviceIdToClipboard() {
        ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        ClipData clip = ClipData.newPlainText("Device ID", deviceId != null ? deviceId : "Не удалось получить ID");
        clipboard.setPrimaryClip(clip);
        copyButton.setImageResource(R.drawable.ic_check);
        copyButton.postDelayed(() -> copyButton.setImageResource(R.drawable.ic_copy), 2000);
        Utils.showToast(this, getString(R.string.device_id_copied));
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
        android.content.SharedPreferences prefs = getSharedPreferences("activation", MODE_PRIVATE);
        prefs.edit().putString("activation_token", token).apply();
        activateButton.setText(R.string.validating);
        activateButton.setEnabled(false);
        new Thread(() -> {
            boolean success = activationManager.validateAndActivate(token);
            runOnUiThread(() -> {
                if (success) {
                    Utils.showToast(this, getString(R.string.activation_success));
                    tokenEditText.setText("");
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
        new androidx.appcompat.app.AlertDialog.Builder(this)
                .setTitle("Активация успешна!")
                .setMessage("Приложение активировано и готово к использованию")
                .setIcon(R.drawable.ic_check)
                .setPositiveButton("Продолжить", (dialog, which) -> {
                    dialog.dismiss();
                    finish();
                })
                .setCancelable(false)
                .show();
    }

    private void createDebugButton() {
        try {
            ViewParent parent = findViewById(R.id.btn_activate).getParent();
            if (parent instanceof View) {
                ViewParent grandParent = ((View) parent).getParent();
                if (grandParent instanceof LinearLayout) {
                    LinearLayout container = (LinearLayout) grandParent;
                    debugButton = new Button(this);
                    debugButton.setText("🔍 Отладить токен");
                    debugButton.setTextSize(14);
                    debugButton.setBackgroundColor(getColor(android.R.color.holo_orange_light));
                    debugButton.setTextColor(getColor(android.R.color.white));
                    debugButton.setPadding(32, 24, 32, 24);
                    LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                    );
                    params.topMargin = 16;
                    debugButton.setLayoutParams(params);
                    container.addView(debugButton);
                    android.util.Log.d("SettingsActivity", "Кнопка отладки успешно создана");
                } else {
                    android.util.Log.e("SettingsActivity", "Родительский контейнер не является LinearLayout");
                }
            } else {
                android.util.Log.e("SettingsActivity", "Родитель btn_activate не является View");
            }
        } catch (Exception e) {
            android.util.Log.e("SettingsActivity", "Ошибка при создании кнопки отладки: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private void debugToken() {
        String token = tokenEditText.getText().toString().trim();
        if (TextUtils.isEmpty(token)) {
            showDebugDialog("Ошибка", "Введите токен для отладки");
            return;
        }
        if (debugButton != null) {
            debugButton.setText("🔍 Отладка...");
            debugButton.setEnabled(false);
        }
        new Thread(() -> {
            String debugResult = activationManager.validateTokenDebug(token);
            runOnUiThread(() -> {
                if (debugButton != null) {
                    debugButton.setText("🔍 Отладить токен");
                    debugButton.setEnabled(true);
                }
                showDebugDialog("Результат отладки токена", debugResult);
            });
        }).start();
    }

    private void showDebugDialog(String title, String debugText) {
        android.widget.ScrollView scrollView = new android.widget.ScrollView(this);
        TextView textView = new TextView(this);
        textView.setText(debugText);
        textView.setTextSize(12);
        textView.setPadding(32, 32, 32, 32);
        textView.setTypeface(android.graphics.Typeface.MONOSPACE);
        scrollView.addView(textView);
        new androidx.appcompat.app.AlertDialog.Builder(this)
                .setTitle(title)
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