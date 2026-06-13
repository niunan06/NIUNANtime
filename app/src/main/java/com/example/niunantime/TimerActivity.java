package com.example.niunantime;

import android.app.AlertDialog;
import android.app.NotificationManager;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.NotificationCompat;

import com.example.niunantime.databinding.ActivityTimerBinding;
import com.example.niunantime.db.AppDatabase;
import com.example.niunantime.db.TimeEvent;

import java.util.Locale;
import java.util.concurrent.Executors;

public class TimerActivity extends AppCompatActivity {

    public static final String EXTRA_EVENT_NAME = "event_name";
    public static final String EXTRA_TIMER_TYPE = "timer_type";
    public static final String EXTRA_DURATION_MINUTES = "duration_minutes";
    public static final String EXTRA_IS_RESUME = "is_resume";
    public static final String EXTRA_START_TIME = "start_time";
    public static final String EXTRA_REMAINING_SECONDS = "remaining_seconds";
    public static final String EXTRA_BG_ELAPSED = "bg_elapsed";
    public static final String EXTRA_EVENT_ID = "event_id";

    private ActivityTimerBinding binding;

    private String eventName;
    private String timerType;
    private int totalSeconds;
    private int remainingSeconds;

    private boolean isRunning = true;
    private boolean isPaused = false;
    private Handler handler = new Handler(Looper.getMainLooper());
    private long startTime;

    private Runnable timerRunnable;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        applyTheme();
        super.onCreate(savedInstanceState);
        binding = ActivityTimerBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        eventName = getIntent().getStringExtra(EXTRA_EVENT_NAME);
        timerType = getIntent().getStringExtra(EXTRA_TIMER_TYPE);
        int durationMinutes = getIntent().getIntExtra(EXTRA_DURATION_MINUTES, 0);
        boolean isResume = getIntent().getBooleanExtra(EXTRA_IS_RESUME, false);

        binding.tvEventName.setText(eventName);

        binding.toolbar.setNavigationOnClickListener(v -> saveToTimerManagerAndFinish());

        if ("countdown".equals(timerType)) {
            binding.toolbar.setTitle("定时");

            if (isResume) {
                startTime = getIntent().getLongExtra(EXTRA_START_TIME, System.currentTimeMillis());
                remainingSeconds = getIntent().getIntExtra(EXTRA_REMAINING_SECONDS, 0);
                // 后台已走完，直接结算
                if (remainingSeconds <= 0) {
                    onCountdownFinished();
                    return;
                }
            } else {
                startTime = System.currentTimeMillis();
                totalSeconds = durationMinutes * 60;
                remainingSeconds = totalSeconds;
            }

            // 暂停/继续按钮
            binding.btnPause.setOnClickListener(v -> toggleCountdownPause());
            // 结束按钮
            binding.btnEnd.setOnClickListener(v -> {
                TimerManager.getInstance().stop();
                int elapsedActual = countdownInitialSeconds - remainingSeconds;
                saveEvent(elapsedActual > 0 ? elapsedActual : 0);
                finish();
            });
            startCountdown();
        } else {
            binding.toolbar.setTitle("计时");

            if (isResume) {
                startTime = getIntent().getLongExtra(EXTRA_START_TIME, System.currentTimeMillis());
            } else {
                startTime = System.currentTimeMillis();
            }

            binding.btnPause.setOnClickListener(v -> toggleStopwatchPause());
            binding.btnEnd.setOnClickListener(v -> stopStopwatch());
            startStopwatch();
        }
    }

    private void saveToTimerManagerAndFinish() {
        isRunning = false;
        handler.removeCallbacks(timerRunnable);

        if ("stopwatch".equals(timerType)) {
            long elapsed = System.currentTimeMillis() - startTime;
            TimerManager.getInstance().pauseStopwatch(eventName, elapsed);
        } else {
            TimerManager.getInstance().pauseCountdown(eventName, remainingSeconds);
        }
        finish();
    }

    // ---- 暂停/继续（计时 & 倒计时通用） ----

    private long pauseElapsed; // 秒表暂停时累积的毫秒数

    private void toggleStopwatchPause() {
        if (isPaused) {
            // 继续
            isPaused = false;
            isRunning = true;
            startTime = System.currentTimeMillis() - pauseElapsed;
            binding.btnPause.setText("暂停");
            startStopwatch();
        } else {
            // 暂停
            isPaused = true;
            isRunning = false;
            handler.removeCallbacks(timerRunnable);
            pauseElapsed = System.currentTimeMillis() - startTime;
            binding.btnPause.setText("继续");
        }
    }

    private void toggleCountdownPause() {
        if (isPaused) {
            // 继续
            isPaused = false;
            isRunning = true;
            binding.btnPause.setText("暂停");
            startCountdown();
        } else {
            // 暂停
            isPaused = true;
            isRunning = false;
            handler.removeCallbacks(timerRunnable);
            binding.btnPause.setText("继续");
        }
    }

    // ---- Stopwatch ----

    private void startStopwatch() {
        timerRunnable = new Runnable() {
            @Override
            public void run() {
                if (!isRunning) return;
                long elapsed = System.currentTimeMillis() - startTime;
                int sec = (int) (elapsed / 1000);
                int hours = sec / 3600;
                int minutes = (sec % 3600) / 60;
                int seconds = sec % 60;
                binding.tvTimeDisplay.setText(String.format(Locale.getDefault(),
                        "%02d:%02d:%02d", hours, minutes, seconds));
                handler.postDelayed(this, 200);
            }
        };
        handler.post(timerRunnable);
    }

    private void stopStopwatch() {
        isRunning = false;
        handler.removeCallbacks(timerRunnable);

        long elapsed = System.currentTimeMillis() - startTime;
        int sec = (int) (elapsed / 1000);

        TimerManager.getInstance().stop();
        saveEvent(sec);
        finish();
    }

    // ---- 倒计时 ----

    private int countdownInitialSeconds; // 保存本次倒计时的总秒数（用于最终保存）

    private void startCountdown() {
        countdownInitialSeconds = remainingSeconds;

        timerRunnable = new Runnable() {
            @Override
            public void run() {
                if (!isRunning) return;
                if (remainingSeconds <= 0) {
                    binding.tvTimeDisplay.setText("00:00:00");
                    onCountdownFinished();
                    return;
                }
                int hours = remainingSeconds / 3600;
                int minutes = (remainingSeconds % 3600) / 60;
                int sec = remainingSeconds % 60;
                binding.tvTimeDisplay.setText(String.format(Locale.getDefault(),
                        "%02d:%02d:%02d", hours, minutes, sec));
                remainingSeconds--;
                handler.postDelayed(this, 1000);
            }
        };
        handler.post(timerRunnable);
    }

    private void onCountdownFinished() {
        isRunning = false;
        handler.removeCallbacks(timerRunnable);

        TimerManager.getInstance().stop();
        saveEvent(countdownInitialSeconds);

        // 发送通知
        NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        if (nm != null) {
            NotificationCompat.Builder builder = new NotificationCompat.Builder(this, "todo_reminder")
                    .setSmallIcon(android.R.drawable.ic_dialog_info)
                    .setContentTitle("时间到！")
                    .setContentText(eventName + " 事件时间到！！")
                    .setAutoCancel(true)
                    .setPriority(NotificationCompat.PRIORITY_HIGH);
            nm.notify(1001, builder.build());
        }

        new AlertDialog.Builder(this)
                .setTitle("时间到！")
                .setMessage("事件「" + eventName + "」已完成")
                .setCancelable(false)
                .setPositiveButton("确定", (dialog, which) -> finish())
                .show();
    }

    // ---- 数据库保存 ----

    private void saveEvent(int durationSeconds) {
        long endTime = startTime + durationSeconds * 1000L;

        Executors.newSingleThreadExecutor().execute(() -> {
            TimeEvent event = new TimeEvent();
            event.eventName = eventName;
            event.durationSeconds = durationSeconds;
            event.type = timerType;
            event.timestamp = endTime;
            AppDatabase.getInstance(this).timeEventDao().insert(event);
        });
    }

    @Override
    public void onBackPressed() {
        saveToTimerManagerAndFinish();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        isRunning = false;
        handler.removeCallbacks(timerRunnable);
    }

    private void applyTheme() {
        SharedPreferences prefs = getSharedPreferences("app_prefs", MODE_PRIVATE);
        String themeColor = prefs.getString("theme_color", "purple");
        switch (themeColor) {
            case "blue": setTheme(R.style.Theme_NIUNANtime_Blue); break;
            case "green": setTheme(R.style.Theme_NIUNANtime_Green); break;
            case "red": setTheme(R.style.Theme_NIUNANtime_Red); break;
            case "orange": setTheme(R.style.Theme_NIUNANtime_Orange); break;
            case "teal": setTheme(R.style.Theme_NIUNANtime_Teal); break;
            case "pink": setTheme(R.style.Theme_NIUNANtime_Pink); break;
            case "indigo": setTheme(R.style.Theme_NIUNANtime_Indigo); break;
            case "cyan": setTheme(R.style.Theme_NIUNANtime_Cyan); break;
            case "lime": setTheme(R.style.Theme_NIUNANtime_Lime); break;
            case "deep_orange": setTheme(R.style.Theme_NIUNANtime_DeepOrange); break;
            case "deep_purple": setTheme(R.style.Theme_NIUNANtime_DeepPurple); break;
            case "brown": setTheme(R.style.Theme_NIUNANtime_Brown); break;
            case "blue_grey": setTheme(R.style.Theme_NIUNANtime_BlueGrey); break;
            case "light_green": setTheme(R.style.Theme_NIUNANtime_LightGreen); break;
            case "white": setTheme(R.style.Theme_NIUNANtime_White); break;
            case "black": setTheme(R.style.Theme_NIUNANtime_Black); break;
        }
    }
}
