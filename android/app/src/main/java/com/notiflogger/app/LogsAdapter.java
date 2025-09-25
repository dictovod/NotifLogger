package com.notiflogger.app;

import android.content.pm.PackageManager;
import android.graphics.drawable.Drawable;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.recyclerview.widget.RecyclerView;
import java.util.ArrayList;
import java.util.List;

public class LogsAdapter extends RecyclerView.Adapter<LogsAdapter.LogViewHolder> {
    private static final String TAG = "LogsAdapter";
    private final List<LogsActivity.LogEntry> logEntries;

    public LogsAdapter(List<LogsActivity.LogEntry> logEntries) {
        this.logEntries = logEntries != null ? logEntries : new ArrayList<>();
        Log.d(TAG, "LogsAdapter initialized with " + this.logEntries.size() + " entries");
    }

    @NonNull
    @Override
    public LogViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        try {
            View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_log_entry, parent, false);
            return new LogViewHolder(view);
        } catch (Exception e) {
            Log.e(TAG, "Error inflating item_log_entry: " + e.getMessage(), e);
            throw new RuntimeException("Failed to inflate item_log_entry", e);
        }
    }

    @Override
    public void onBindViewHolder(@NonNull LogViewHolder holder, int position) {
        try {
            holder.bind(logEntries.get(position));
        } catch (Exception e) {
            Log.e(TAG, "Error binding log entry at position " + position + ": " + e.getMessage(), e);
        }
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
            if (iconView == null || appNameView == null || titleView == null || timeView == null || actionView == null || ongoingIndicator == null) {
                Log.e(TAG, "One or more views not found in item_log_entry");
            }
        }

        public void bind(LogsActivity.LogEntry entry) {
            if (entry == null) {
                Log.w(TAG, "LogEntry is null");
                return;
            }
            
            appNameView.setText(entry.appName.isEmpty() ? entry.packageName : entry.appName);
            titleView.setText(entry.getDisplayTitle());
            timeView.setText(entry.getDisplayTime());
            actionView.setText(entry.action);
            
            int actionColor;
            try {
                if ("POSTED".equals(entry.action)) {
                    actionColor = itemView.getContext().getColor(R.color.success);
                } else if ("REMOVED".equals(entry.action)) {
                    actionColor = itemView.getContext().getColor(R.color.error);
                } else {
                    actionColor = itemView.getContext().getColor(R.color.text_secondary);
                }
                actionView.setTextColor(actionColor);
            } catch (Exception e) {
                Log.e(TAG, "Error setting action color: " + e.getMessage(), e);
            }
            
            ongoingIndicator.setVisibility(entry.ongoing ? View.VISIBLE : View.GONE);
            loadAppIcon(entry.packageName, iconView);
            
            itemView.setOnClickListener(v -> showNotificationDetails(entry));
        }

        private void loadAppIcon(String packageName, ImageView iconView) {
            if (packageManager == null || packageName == null || packageName.isEmpty()) {
                iconView.setImageResource(R.drawable.ic_app_icon);
                Log.w(TAG, "PackageManager or packageName is null/empty, using default icon");
                return;
            }
            try {
                Drawable icon = packageManager.getApplicationIcon(packageName);
                iconView.setImageDrawable(icon);
            } catch (PackageManager.NameNotFoundException e) {
                iconView.setImageResource(R.drawable.ic_app_icon);
                Log.w(TAG, "App icon not found for package: " + packageName);
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
            try {
                new AlertDialog.Builder(itemView.getContext())
                        .setTitle("Детали уведомления")
                        .setMessage(details)
                        .setIcon(R.drawable.ic_logs)
                        .setPositiveButton("Закрыть", null)
                        .show();
            } catch (Exception e) {
                Log.e(TAG, "Error showing notification details: " + e.getMessage(), e);
            }
        }
    }
}