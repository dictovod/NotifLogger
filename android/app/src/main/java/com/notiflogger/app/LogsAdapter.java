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
import java.util.ArrayList;
import java.util.List;

public class LogsAdapter extends RecyclerView.Adapter<LogsAdapter.LogViewHolder> {
    private final List<LogsActivity.LogEntry> logEntries;

    public LogsAdapter(List<LogsActivity.LogEntry> logEntries) {
        this.logEntries = logEntries != null ? logEntries : new ArrayList<>();
    }

    @NonNull
    @Override
    public LogViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_log_entry, parent, false);
        return new LogViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull LogViewHolder holder, int position) {
        holder.bind(logEntries.get(position));
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
        private final PackageManager packageManager;

        public LogViewHolder(@NonNull View itemView) {
            super(itemView);
            iconView = itemView.findViewById(R.id.icon_app);
            appNameView = itemView.findViewById(R.id.tv_app_name);
            titleView = itemView.findViewById(R.id.tv_title);
            timeView = itemView.findViewById(R.id.tv_time);
            actionView = itemView.findViewById(R.id.tv_action);
            ongoingIndicator = itemView.findViewById(R.id.indicator_ongoing);
            packageManager = itemView.getContext().getPackageManager();
        }

        public void bind(LogsActivity.LogEntry entry) {
            appNameView.setText(entry.appName.isEmpty() ? entry.packageName : entry.appName);
            titleView.setText(entry.getDisplayTitle());
            timeView.setText(entry.getDisplayTime());
            actionView.setText(entry.action);
            int actionColor;
            if ("POSTED".equals(entry.action)) {
                actionColor = itemView.getContext().getColor(R.color.success);
            } else if ("REMOVED".equals(entry.action)) {
                actionColor = itemView.getContext().getColor(R.color.error);
            } else {
                actionColor = itemView.getContext().getColor(R.color.text_secondary);
            }
            actionView.setTextColor(actionColor);
            ongoingIndicator.setVisibility(entry.ongoing ? View.VISIBLE : View.GONE);
            loadAppIcon(entry.packageName, iconView);
            itemView.setOnClickListener(v -> showNotificationDetails(entry));
        }

        private void loadAppIcon(String packageName, ImageView iconView) {
            if (packageManager != null) {
                try {
                    Drawable icon = packageManager.getApplicationIcon(packageName);
                    iconView.setImageDrawable(icon);
                } catch (PackageManager.NameNotFoundException e) {
                    iconView.setImageResource(R.drawable.ic_app_icon);
                }
            } else {
                iconView.setImageResource(R.drawable.ic_app_icon);
            }
        }

        private void showNotificationDetails(LogsActivity.LogEntry entry) {
            String details = String.format(
                "Приложение: %s\nПакет: %s\nВремя: %s\nДействие: %s\nЗаголовок: %s\nТекст: %s\nПостоянное: %s",
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