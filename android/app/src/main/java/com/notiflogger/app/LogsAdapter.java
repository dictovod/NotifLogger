package com.notiflogger.app;

import android.content.pm.PackageManager;
import android.graphics.drawable.Drawable;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.List;

/**
 * Адаптер для отображения списка логов уведомлений
 */
public class LogsAdapter extends RecyclerView.Adapter<LogsAdapter.LogViewHolder> {
    
    private final List<LogsActivity.LogEntry> logEntries;

    public LogsAdapter(List<LogsActivity.LogEntry> logEntries) {
        this.logEntries = logEntries != null ? logEntries : new ArrayList<>();
    }

    @NonNull
    @Override
    public LogViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_log_entry, parent, false);
        return new LogViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull LogViewHolder holder, int position) {
        LogsActivity.LogEntry entry = logEntries.get(position);
        holder.bind(entry);
    }

    @Override
    public int getItemCount() {
        return logEntries.size();
    }

    static class LogViewHolder extends RecyclerView.ViewHolder {
        private final ImageView iconView;
        private final TextView appNameView;
        private final TextView titleView;
        private final TextView timeView;
        private final TextView actionView;
        private final View ongoingIndicator;
        private final PackageManager packageManager; // Инициализируем для каждого ViewHolder

        public LogViewHolder(@NonNull View itemView) {
            super(itemView);
            
            iconView = itemView.findViewById(R.id.icon_app);
            appNameView = itemView.findViewById(R.id.tv_app_name);
            titleView = itemView.findViewById(R.id.tv_title);
            timeView = itemView.findViewById(R.id.tv_time);
            actionView = itemView.findViewById(R.id.tv_action);
            ongoingIndicator = itemView.findViewById(R.id.indicator_ongoing);
            packageManager = itemView.getContext().getPackageManager(); // Инициализация здесь
        }

        public void bind(LogsActivity.LogEntry entry) {
            // Устанавливаем название приложения
            appNameView.setText(entry.appName.isEmpty() ? entry.packageName : entry.appName);
            
            // Устанавливаем заголовок уведомления
            titleView.setText(entry.getDisplayTitle());
            
            // Устанавливаем время
            timeView.setText(entry.getDisplayTime());
            
            // Устанавливаем действие
            actionView.setText(entry.action);
            
            // Цвет действия
            int actionColor;
            if ("POSTED".equals(entry.action)) {
                actionColor = itemView.getContext().getColor(R.color.success);
            } else if ("REMOVED".equals(entry.action)) {
                actionColor = itemView.getContext().getColor(R.color.error);
            } else {
                actionColor = itemView.getContext().getColor(R.color.text_secondary);
            }
            actionView.setTextColor(actionColor);
            
            // Показываем индикатор для постоянных уведомлений
            ongoingIndicator.setVisibility(entry.ongoing ? View.VISIBLE : View.GONE);
            
            // Загружаем иконку приложения (попытка получить реальную иконку)
            loadAppIcon(entry.packageName, iconView);
            
            // Обработка нажатия
            itemView.setOnClickListener(v -> showNotificationDetails(entry));
        }
        
        private void loadAppIcon(String packageName, ImageView iconView) {
            if (packageManager != null) {
                try {
                    Drawable icon = packageManager.getApplicationIcon(packageName);
                    iconView.setImageDrawable(icon);
                } catch (PackageManager.NameNotFoundException e) {
                    iconView.setImageResource(R.drawable.ic_app_icon); // Фallback на дефолтную иконку
                }
            } else {
                iconView.setImageResource(R.drawable.ic_app_icon); // Фallback, если packageManager недоступен
            }
        }
        
        private void showNotificationDetails(LogsActivity.LogEntry entry) {
            // Создаем диалог с деталями уведомления
            String details = String.format(
                "Приложение: %s\n" +
                "Пакет: %s\n" +
                "Время: %s\n" +
                "Действие: %s\n" +
                "Заголовок: %s\n" +
                "Текст: %s\n" +
                "Постоянное: %s",
                entry.appName.isEmpty() ? "—" : entry.appName,
                entry.packageName,
                entry.timestamp,
                entry.action,
                entry.title.isEmpty() ? "—" : entry.title,
                entry.text.isEmpty() ? "—" : entry.text,
                entry.ongoing ? "Да" : "Нет"
            );
            
            new androidx.appcompat.app.AlertDialog.Builder(itemView.getContext())
                    .setTitle("Детали уведомления")
                    .setMessage(details)
                    .setIcon(R.drawable.ic_logs)
                    .setPositiveButton("Закрыть", null)
                    .show();
        }
    }
}