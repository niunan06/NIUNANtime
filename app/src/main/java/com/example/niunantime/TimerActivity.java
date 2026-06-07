package com.example.niunantime;

import android.app.AlertDialog;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;

import androidx.appcompat.app.AppCompatActivity;

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

        binding.toolbar.setNavigationOnClickListener(v -> showBackDialog());

        if ("countdown".equals(timerType)) {
            binding.toolbar.setTitle("定时");

            if (isResume) {
                startTime = getIntent().getLongExtra(EXTRA_START_TIME, System.currentTimeMillis());
                remainingSeconds = getIntent().getIntExtra(EXTRA_REMAINING_SECONDS, 0);
            } else {
                startTime = System.currentTimeMillis();
                totalSeconds = durationMinutes * 60;
                remainingSeconds = totalSeconds;
            }

            // 暂停/继续按钮（红色）
            binding.btnStop.setText("暂停");
            binding.btnStop.setOnClickListener(v -> toggleCountdownPause());
            startCountdown();
        } else {
            binding.toolbar.setTitle("计时");

            if (isResume) {
                startTime = getIntent().getLongExtra(EXTRA_START_TIME, System.currentTimeMillis());
            } else {
                startTime = System.currentTimeMillis();
            }

            binding.btnStop.setOnClickListener(v -> stopStopwatch());
            startStopwatch();
        }
    }

    private void showBackDialog() {
        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("计时进行中")
                .setMessage("返回继续后台计时，退出将不记录本次计时")
                .setPositiveButton("返回", (d, w) -> {
                    isRunning = false;
                    handler.removeCallbacks(timerRunnable);

                    if ("stopwatch".equals(timerType)) {
                        long elapsed = System.currentTimeMillis() - startTime;
                        TimerManager.getInstance().pauseStopwatch(eventName, elapsed);
                    } else {
                        TimerManager.getInstance().pauseCountdown(eventName, remainingSeconds);
                    }
                    finish();
                })
                .setNegativeButton("退出", (d, w) -> {
                    TimerManager.getInstance().stop();
                    isRunning = false;
                    handler.removeCallbacks(timerRunnable);
                    finish();
                })
                .create();

        dialog.setOnShowListener(d -> {
            dialog.getButton(AlertDialog.BUTTON_NEGATIVE).setTextColor(Color.RED);
        });

        dialog.show();
    }

    // ---- 暂停/继续（仅倒计时） ----

    private void toggleCountdownPause() {
        if (isPaused) {
            // 继续
            isPaused = false;
            isRunning = true;
            binding.btnStop.setText("暂停");
            startCountdown();
        } else {
            // 暂停
            isPaused = true;
            isRunning = false;
            handler.removeCallbacks(timerRunnable);
            binding.btnStop.setText("继续");
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
        showBackDialog();
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
        }
    }
}
