package com.example.niunantime;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

import androidx.core.app.NotificationCompat;

import com.example.niunantime.db.AppDatabase;
import com.example.niunantime.db.Todo;
import com.example.niunantime.db.TodoDao;

import java.util.concurrent.Executors;

public class ReminderReceiver extends BroadcastReceiver {

    private static final String CHANNEL_ID = "todo_reminder";

    @Override
    public void onReceive(Context context, Intent intent) {
        int todoId = intent.getIntExtra("todo_id", 0);
        String todoName = intent.getStringExtra("todo_name");

        if (todoName == null) return;

        ensureChannel(context);

        Intent launchIntent = new Intent(context, MainActivity.class);
        launchIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent pendingIntent = PendingIntent.getActivity(
                context, todoId, launchIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        NotificationCompat.Builder builder = new NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setContentTitle("今日待办")
                .setContentText(todoName)
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setAutoCancel(true)
                .setContentIntent(pendingIntent);

        NotificationManager manager =
                (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        if (manager != null) {
            manager.notify(todoId, builder.build());
        }

        // Mark the reminder as delivered in the database
        Executors.newSingleThreadExecutor().execute(() -> {
            TodoDao dao = AppDatabase.getInstance(context).todoDao();
            Todo todo = dao.getById(todoId);
            if (todo != null) {
                todo.reminded = true;
                todo.reminderTime = 0L;
                dao.update(todo);
            }
        });
    }

    public static void ensureChannel(Context context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationManager manager =
                    context.getSystemService(NotificationManager.class);
            if (manager != null) {
                manager.createNotificationChannel(new NotificationChannel(
                        CHANNEL_ID, "待办提醒", NotificationManager.IMPORTANCE_DEFAULT));
            }
        }
    }
}
