package com.example.niunantime;

import android.content.Context;
import android.content.Intent;

public class TimerManager {
    private static TimerManager instance;

    private boolean active = false;
    private String eventName;
    private String timerType;
    private long elapsedMillis;   // 计时已过毫秒数（用于stopwatch恢复）
    private int remainingSeconds; // 剩余秒数（用于countdown恢复）
    private long pauseTimestamp;  // 暂停时的时间戳（用于后台耗时计算）

    private TimerManager() {}

    public static synchronized TimerManager getInstance() {
        if (instance == null) {
            instance = new TimerManager();
        }
        return instance;
    }

    /** 暂停stopwatch：保存已过时间 */
    public void pauseStopwatch(String eventName, long elapsedMillis) {
        this.eventName = eventName;
        this.timerType = "stopwatch";
        this.elapsedMillis = elapsedMillis;
        this.pauseTimestamp = System.currentTimeMillis();
        this.active = true;
    }

    /** 暂停countdown：保存剩余秒数 */
    public void pauseCountdown(String eventName, int remainingSeconds) {
        this.eventName = eventName;
        this.timerType = "countdown";
        this.remainingSeconds = Math.max(remainingSeconds, 0);
        this.pauseTimestamp = System.currentTimeMillis();
        this.active = true;
    }

    public boolean isActive() {
        return active;
    }

    public String getEventName() { return eventName; }
    public String getTimerType() { return timerType; }

    public void stop() {
        active = false;
    }

    public int getRemainingSeconds() {
        return remainingSeconds;
    }

    public Intent createResumeIntent(Context context) {
        long now = System.currentTimeMillis();
        long bgElapsed = now - pauseTimestamp; // 后台经过的毫秒数

        Intent intent = new Intent(context, TimerActivity.class);
        intent.putExtra(TimerActivity.EXTRA_EVENT_NAME, eventName);
        intent.putExtra(TimerActivity.EXTRA_TIMER_TYPE, timerType);
        intent.putExtra(TimerActivity.EXTRA_IS_RESUME, true);
        intent.putExtra(TimerActivity.EXTRA_BG_ELAPSED, bgElapsed);

        if ("stopwatch".equals(timerType)) {
            // 包含后台时间的总已过时间
            long totalElapsed = elapsedMillis + bgElapsed;
            intent.putExtra(TimerActivity.EXTRA_START_TIME, now - totalElapsed);
            intent.putExtra(TimerActivity.EXTRA_DURATION_MINUTES, 0);
        } else {
            // 减去后台经过的时间
            int adjusted = remainingSeconds - (int)(bgElapsed / 1000);
            intent.putExtra(TimerActivity.EXTRA_START_TIME, now);
            intent.putExtra(TimerActivity.EXTRA_DURATION_MINUTES, 0);
            intent.putExtra(TimerActivity.EXTRA_REMAINING_SECONDS, Math.max(adjusted, 0));
        }
        return intent;
    }
}
