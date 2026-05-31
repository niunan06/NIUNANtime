package com.example.niunantime;

import android.content.Context;
import android.content.Intent;

public class TimerManager {
    private static TimerManager instance;

    private boolean active = false;
    private String eventName;
    private String timerType;
    private long elapsedMillis;   // 计时已过毫秒数（用于stpowatch恢复）
    private int remainingSeconds; // 剩余秒数（用于countdown恢复）

    private TimerManager() {}

    public static synchronized TimerManager getInstance() {
        if (instance == null) {
            instance = new TimerManager();
        }
        return instance;
    }

    /** 暂停stpowatch：保存已过时间 */
    public void pauseStopwatch(String eventName, long elapsedMillis) {
        this.eventName = eventName;
        this.timerType = "stopwatch";
        this.elapsedMillis = elapsedMillis;
        this.active = true;
    }

    /** 暂停countdown：保存剩余秒数 */
    public void pauseCountdown(String eventName, int remainingSeconds) {
        this.eventName = eventName;
        this.timerType = "countdown";
        this.remainingSeconds = Math.max(remainingSeconds, 0);
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

    public Intent createResumeIntent(Context context) {
        Intent intent = new Intent(context, TimerActivity.class);
        intent.putExtra(TimerActivity.EXTRA_EVENT_NAME, eventName);
        intent.putExtra(TimerActivity.EXTRA_TIMER_TYPE, timerType);
        intent.putExtra(TimerActivity.EXTRA_IS_RESUME, true);

        if ("stopwatch".equals(timerType)) {
            // 设置startTime使得 now - startTime = 已过时间
            intent.putExtra(TimerActivity.EXTRA_START_TIME,
                    System.currentTimeMillis() - elapsedMillis);
            intent.putExtra(TimerActivity.EXTRA_DURATION_MINUTES, 0);
        } else {
            // countdown：直接传剩余秒数和当前时间为起点
            intent.putExtra(TimerActivity.EXTRA_START_TIME, System.currentTimeMillis());
            intent.putExtra(TimerActivity.EXTRA_DURATION_MINUTES, 0);
            intent.putExtra(TimerActivity.EXTRA_REMAINING_SECONDS, remainingSeconds);
        }
        return intent;
    }
}
