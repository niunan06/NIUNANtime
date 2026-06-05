package com.example.niunantime;

import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.example.niunantime.databinding.ActivityStatisticsBinding;
import com.example.niunantime.db.AppDatabase;
import com.example.niunantime.db.TimeEvent;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class StatisticsActivity extends AppCompatActivity {

    private ActivityStatisticsBinding binding;
    private boolean isToday = true;
    private long periodStartTime;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        applyTheme();
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        binding = ActivityStatisticsBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        ViewCompat.setOnApplyWindowInsetsListener(binding.getRoot(), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        binding.toolbar.setNavigationOnClickListener(v -> finish());

        binding.bottomNavigation.setOnNavigationItemSelectedListener(item -> {
            int id = item.getItemId();
            if (id == R.id.nav_today) {
                isToday = true;
                loadStats();
                return true;
            } else if (id == R.id.nav_week) {
                isToday = false;
                loadStats();
                return true;
            }
            return false;
        });

        // 监听 fragment 返回
        getSupportFragmentManager().addOnBackStackChangedListener(() -> {
            if (getSupportFragmentManager().getBackStackEntryCount() == 0) {
                binding.fragmentContainer.setVisibility(View.GONE);
                binding.scrollContent.setVisibility(View.VISIBLE);
                binding.bottomNavigation.setVisibility(View.VISIBLE);
            }
        });

        loadStats();
    }

    private void loadStats() {
        new Thread(() -> {
            AppDatabase db = AppDatabase.getInstance(this);

            Calendar cal = Calendar.getInstance();
            if (isToday) {
                cal.set(Calendar.HOUR_OF_DAY, 0);
                cal.set(Calendar.MINUTE, 0);
                cal.set(Calendar.SECOND, 0);
                cal.set(Calendar.MILLISECOND, 0);
            } else {
                cal.setFirstDayOfWeek(Calendar.MONDAY);
                cal.set(Calendar.DAY_OF_WEEK, Calendar.MONDAY);
                cal.set(Calendar.HOUR_OF_DAY, 0);
                cal.set(Calendar.MINUTE, 0);
                cal.set(Calendar.SECOND, 0);
                cal.set(Calendar.MILLISECOND, 0);
            }
            periodStartTime = cal.getTimeInMillis();

            List<TimeEvent> events = db.timeEventDao().getEventsSince(periodStartTime);

            runOnUiThread(() -> displayStats(events));
        }).start();
    }

    private void displayStats(List<TimeEvent> events) {
        if (events == null) events = Collections.emptyList();

        // 按事件名称分组
        Map<String, List<TimeEvent>> grouped = new HashMap<>();
        for (TimeEvent e : events) {
            grouped.computeIfAbsent(e.eventName, k -> new ArrayList<>()).add(e);
        }

        // 计算总时长
        long totalSeconds = 0;
        for (List<TimeEvent> list : grouped.values()) {
            for (TimeEvent e : list) {
                totalSeconds += e.durationSeconds;
            }
        }

        binding.tvTotalDuration.setText("总时长：" + formatDuration(totalSeconds));

        binding.eventListContainer.removeAllViews();

        if (grouped.isEmpty()) {
            TextView empty = new TextView(this);
            empty.setText(isToday ? "今日暂无记录" : "本周暂无记录");
            empty.setTextColor(0xFF999999);
            empty.setTextSize(16);
            empty.setPadding(0, 40, 0, 0);
            empty.setGravity(View.TEXT_ALIGNMENT_CENTER);
            binding.eventListContainer.addView(empty);
            return;
        }

        // 按总时长从大到小排序
        List<Map.Entry<String, List<TimeEvent>>> sortedGroups =
                new ArrayList<>(grouped.entrySet());
        Collections.sort(sortedGroups, (a, b) -> {
            long sumA = 0, sumB = 0;
            for (TimeEvent e : a.getValue()) sumA += e.durationSeconds;
            for (TimeEvent e : b.getValue()) sumB += e.durationSeconds;
            return Long.compare(sumB, sumA);
        });

        for (Map.Entry<String, List<TimeEvent>> entry : sortedGroups) {
            final String eventName = entry.getKey();
            List<TimeEvent> sessions = entry.getValue();

            // 计算该事件总时长
            long eventTotal = 0;
            for (TimeEvent e : sessions) {
                eventTotal += e.durationSeconds;
            }

            View itemView = LayoutInflater.from(this)
                    .inflate(R.layout.item_stat_event, binding.eventListContainer, false);

            TextView tvName = itemView.findViewById(R.id.tv_event_name);
            TextView tvDuration = itemView.findViewById(R.id.tv_event_duration);
            TextView tvPercent = itemView.findViewById(R.id.tv_event_percent);

            tvName.setText(eventName);
            tvDuration.setText(formatDuration(eventTotal));

            int percent = totalSeconds > 0
                    ? (int) (eventTotal * 100 / totalSeconds)
                    : 0;
            tvPercent.setText(percent + "%");

            // 点击查看详细记录
            itemView.setOnClickListener(v -> openEventDetail(eventName));

            binding.eventListContainer.addView(itemView);
        }
    }

    private void openEventDetail(String eventName) {
        // 隐藏主内容，显示 fragment
        binding.scrollContent.setVisibility(View.GONE);
        binding.bottomNavigation.setVisibility(View.GONE);
        binding.fragmentContainer.setVisibility(View.VISIBLE);

        getSupportFragmentManager().beginTransaction()
                .setCustomAnimations(
                        android.R.anim.fade_in,
                        android.R.anim.fade_out,
                        android.R.anim.fade_in,
                        android.R.anim.fade_out
                )
                .replace(R.id.fragment_container,
                        EventDetailFragment.newInstance(eventName, periodStartTime))
                .addToBackStack("event_detail")
                .commit();
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

    private String formatDuration(long totalSeconds) {
        long hours = totalSeconds / 3600;
        long minutes = (totalSeconds % 3600) / 60;
        long secs = totalSeconds % 60;
        if (hours > 0) {
            return String.format(Locale.getDefault(), "%d时%02d分%02d秒", hours, minutes, secs);
        } else if (minutes > 0) {
            return String.format(Locale.getDefault(), "%d分%02d秒", minutes, secs);
        } else {
            return String.format(Locale.getDefault(), "%d秒", secs);
        }
    }
}
